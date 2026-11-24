package cc.bexerlmao.gazette.module.device.controller;

import cc.bexerlmao.gazette.common.web.R;
import cc.bexerlmao.gazette.module.device.dto.DeviceCreateRequest;
import cc.bexerlmao.gazette.module.device.dto.DeviceRegisterRequest;
import cc.bexerlmao.gazette.module.device.dto.DeviceUpdateRequest;
import cc.bexerlmao.gazette.module.device.entity.Device;
import cc.bexerlmao.gazette.module.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 设备管理接口 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/list")
    public R<List<Device>> list() {
        return R.ok(deviceService.list());
    }

    @PostMapping
    public R<Device> create(@Valid @RequestBody DeviceCreateRequest req) {
        return R.ok(deviceService.create(req));
    }

    /** 蓝牙连接后自动注册（网页端调用）：SN 已存在时返回 409，前端提示只能离线模式 */
    @PostMapping("/register")
    public R<Device> register(@Valid @RequestBody DeviceRegisterRequest req) {
        return R.ok(deviceService.register(req));
    }

    @PutMapping("/{id}")
    public R<Device> update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest req) {
        return R.ok(deviceService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return R.ok();
    }
}
