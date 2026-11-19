package cc.bexerlmao.gazette.module.device.controller;

import cc.bexerlmao.gazette.common.web.R;
import cc.bexerlmao.gazette.module.device.dto.SceneSaveRequest;
import cc.bexerlmao.gazette.module.device.entity.Scene;
import cc.bexerlmao.gazette.module.device.service.SceneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 场景管理接口 */
@RestController
@RequestMapping("/api/scene")
@RequiredArgsConstructor
public class SceneController {

    private final SceneService sceneService;

    @GetMapping("/list")
    public R<List<Scene>> list() {
        return R.ok(sceneService.list());
    }

    @GetMapping("/{id}")
    public R<Scene> detail(@PathVariable Long id) {
        return R.ok(sceneService.get(id));
    }

    @PostMapping
    public R<Scene> save(@Valid @RequestBody SceneSaveRequest req) {
        return R.ok(sceneService.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sceneService.delete(id);
        return R.ok();
    }

    /** 渲染预览：body 为变量数据集（可空），返回 PNG */
    @PostMapping(value = "/{id}/preview", consumes = MediaType.ALL_VALUE)
    public byte[] preview(@PathVariable Long id,
                          @RequestBody(required = false) Map<String, Object> data) {
        return sceneService.preview(id, data);
    }
}
