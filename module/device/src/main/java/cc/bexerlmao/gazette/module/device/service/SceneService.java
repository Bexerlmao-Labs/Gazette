package cc.bexerlmao.gazette.module.device.service;

import cc.bexerlmao.gazette.common.web.BizException;
import cc.bexerlmao.gazette.module.device.dto.SceneSaveRequest;
import cc.bexerlmao.gazette.module.device.entity.Scene;
import cc.bexerlmao.gazette.module.device.mapper.DeviceMapper;
import cc.bexerlmao.gazette.module.device.mapper.SceneMapper;
import cc.bexerlmao.gazette.module.device.render.SceneRenderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 场景（原始画面 + 动态 DSL）管理 */
@Service
@RequiredArgsConstructor
public class SceneService {

    private final SceneMapper sceneMapper;
    private final DeviceMapper deviceMapper;
    private final SceneRenderService renderService;

    public List<Scene> list() {
        return sceneMapper.selectList(new LambdaQueryWrapper<Scene>()
                .eq(Scene::getStatus, 1)
                .orderByDesc(Scene::getUpdatedAt));
    }

    public Scene get(Long id) {
        Scene scene = sceneMapper.selectById(id);
        if (scene == null || scene.getStatus() == 0) {
            throw new BizException(404, "场景不存在");
        }
        return scene;
    }

    /** 不抛异常的查询：设备拉取画面等场景缺失时应降级而不是报错 */
    public Scene safeGet(Long id) {
        if (id == null) {
            return null;
        }
        Scene scene = sceneMapper.selectById(id);
        return scene != null && scene.getStatus() == 1 ? scene : null;
    }

    @Transactional
    public Scene save(SceneSaveRequest req) {
        Scene scene;
        if (req.getId() != null) {
            scene = get(req.getId());
        } else {
            scene = new Scene();
            scene.setStatus(1);
            scene.setCreatedAt(LocalDateTime.now());
        }
        scene.setName(req.getName());
        scene.setWidth(req.getWidth() == null ? 0 : req.getWidth());
        scene.setHeight(req.getHeight() == null ? 0 : req.getHeight());
        scene.setOverlayDsl(req.getOverlayDsl());
        if (req.getBaseImageBase64() != null && !req.getBaseImageBase64().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(stripDataUrl(req.getBaseImageBase64()));
            scene.setBaseImage(bytes);
            scene.setBaseHash(sha256Hex(bytes));
        }
        scene.setUpdatedAt(LocalDateTime.now());
        if (scene.getId() == null) {
            sceneMapper.insert(scene);
        } else {
            sceneMapper.updateById(scene);
        }
        return scene;
    }

    /** 软删除，同时解绑引用该场景的设备 */
    @Transactional
    public void delete(Long id) {
        Scene scene = get(id);
        scene.setStatus(0);
        scene.setUpdatedAt(LocalDateTime.now());
        sceneMapper.updateById(scene);
        // 解绑引用该场景的设备
        var unbind = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<cc.bexerlmao.gazette.module.device.entity.Device>()
                .eq(cc.bexerlmao.gazette.module.device.entity.Device::getCurrentSceneId, id)
                .set(cc.bexerlmao.gazette.module.device.entity.Device::getCurrentSceneId, null);
        deviceMapper.update(null, unbind);
    }

    /** 渲染预览：DSL + 数据集 → PNG 字节 */
    public byte[] preview(Long id, Map<String, Object> data) {
        return renderService.render(get(id), data).png();
    }

    private String stripDataUrl(String base64) {
        int comma = base64.indexOf(',');
        return comma >= 0 && base64.startsWith("data:") ? base64.substring(comma + 1) : base64;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append("%02x".formatted(b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed", e);
        }
    }
}
