package cc.bexerlmao.gazette.module.device.service;

import cc.bexerlmao.gazette.common.web.BizException;
import cc.bexerlmao.gazette.module.device.dto.DeviceCreateRequest;
import cc.bexerlmao.gazette.module.device.dto.DeviceUpdateRequest;
import cc.bexerlmao.gazette.module.device.entity.Device;
import cc.bexerlmao.gazette.module.device.mapper.DeviceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** 墨水屏设备管理 */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final SceneService sceneService;
    private final SecureRandom random = new SecureRandom();

    public List<Device> list() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .orderByDesc(Device::getCreatedAt));
    }

    public Device get(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BizException(404, "设备不存在");
        }
        return device;
    }

    /** 仅持久化已有设备实体（在线状态/哈希上报等局部更新） */
    public void saveDevice(Device device) {
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
    }

    /** 按序列号 + 凭证校验设备（固件拉取画面用） */
    public Device verify(String sn, String key) {
        Device device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceSn, sn));
        if (device == null || key == null || !key.equals(device.getDeviceKey())) {
            throw new BizException(401, "设备校验失败");
        }
        return device;
    }

    @Transactional
    public Device create(DeviceCreateRequest req) {
        if (req.getCurrentSceneId() != null) {
            sceneService.get(req.getCurrentSceneId()); // 校验场景存在
        }
        Device device = new Device();
        device.setDeviceSn(generateSn());
        device.setDeviceKey(HexFormat.of().formatHex(UUID.randomUUID().toString().getBytes()));
        device.setName(req.getName());
        device.setModel(req.getModel());
        device.setWidth(req.getWidth() == null ? 0 : req.getWidth());
        device.setHeight(req.getHeight() == null ? 0 : req.getHeight());
        device.setRotate(0);
        device.setCurrentSceneId(req.getCurrentSceneId());
        device.setStatus(2); // 未激活
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.insert(device);
        return device;
    }

    @Transactional
    public Device update(Long id, DeviceUpdateRequest req) {
        Device device = get(id);
        if (req.getName() != null && !req.getName().isBlank()) {
            device.setName(req.getName());
        }
        if (Boolean.TRUE.equals(req.getBindScene())) {
            if (req.getCurrentSceneId() != null) {
                sceneService.get(req.getCurrentSceneId());
            }
            device.setCurrentSceneId(req.getCurrentSceneId());
            device.setCurrentHash(null); // 场景变化后强制下次轮询刷新
        }
        device.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(device);
        return device;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        deviceMapper.deleteById(id);
    }

    private String generateSn() {
        byte[] buf = new byte[6];
        random.nextBytes(buf);
        return "EP" + HexFormat.of().formatHex(buf).toUpperCase();
    }
}
