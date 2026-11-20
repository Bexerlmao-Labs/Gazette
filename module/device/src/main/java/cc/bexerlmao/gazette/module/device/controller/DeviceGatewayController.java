package cc.bexerlmao.gazette.module.device.controller;

import cc.bexerlmao.gazette.common.web.R;
import cc.bexerlmao.gazette.module.device.entity.Device;
import cc.bexerlmao.gazette.module.device.entity.Scene;
import cc.bexerlmao.gazette.module.device.render.SceneRenderService;
import cc.bexerlmao.gazette.module.device.service.DeviceDataProvider;
import cc.bexerlmao.gazette.module.device.service.DeviceService;
import cc.bexerlmao.gazette.module.device.service.SceneService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 固件拉取接口（设备端调用，不走登录鉴权）：
 * GET /api/device/display?sn=xx&key=xx&hash=旧哈希[&battery=95]
 * 哈希一致返回 changed=false；不一致返回新位图（Base64 编码，按场景位图定义方案转换后的字节）。
 */
@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceGatewayController {

    private final DeviceService deviceService;
    private final SceneService sceneService;
    private final SceneRenderService renderService;
    private final DeviceDataProvider dataProvider;

    @Data
    public static class DisplayResponse {
        private boolean changed;
        private String hash;
        private Integer width;
        private Integer height;
        private String format; // 位图定义方案标识，如 bw-msb-b1 / bwr-plane-msb / bwr-2bpp-lsb
        private String image; // Base64 编码的位图，排布由 format 描述
    }

    @GetMapping("/display")
    public R<DisplayResponse> display(@RequestParam String sn,
                                      @RequestParam String key,
                                      @RequestParam(required = false) String hash,
                                      @RequestParam(required = false) Integer battery) {
        Device device = deviceService.verify(sn, key);

        // 上报在线状态与电量
        device.setStatus(1);
        device.setLastOnlineAt(LocalDateTime.now());
        if (battery != null) {
            device.setBattery(Math.max(0, Math.min(100, battery)));
        }
        deviceService.saveDevice(device);

        DisplayResponse resp = new DisplayResponse();
        Scene scene = device.getCurrentSceneId() == null
                ? null : sceneService.safeGet(device.getCurrentSceneId());
        if (scene == null) {
            resp.setChanged(false);
            resp.setHash(null);
            return R.ok(resp);
        }

        SceneRenderService.Rendered rendered = renderService.render(
                scene, dataProvider.provide(device));
        if (rendered.hash().equals(hash)) {
            resp.setChanged(false);
            resp.setHash(rendered.hash());
            return R.ok(resp);
        }

        device.setCurrentHash(rendered.hash());
        deviceService.saveDevice(device);

        resp.setChanged(true);
        resp.setHash(rendered.hash());
        resp.setWidth(rendered.width());
        resp.setHeight(rendered.height());
        resp.setFormat(rendered.format());
        resp.setImage(Base64.getEncoder().encodeToString(rendered.bitmap()));
        return R.ok(resp);
    }
}
