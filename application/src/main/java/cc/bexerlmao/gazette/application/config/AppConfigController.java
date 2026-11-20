package cc.bexerlmao.gazette.application.config;

import cc.bexerlmao.gazette.common.web.R;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前端功能开关下发接口：启动时前端据此决定是否启用蓝牙连接页。
 */
@RestController
@RequestMapping("/api/app-config")
@EnableConfigurationProperties(AppProperties.class)
@RequiredArgsConstructor
public class AppConfigController {

    private final AppProperties appProperties;

    @GetMapping
    public R<Map<String, Boolean>> get() {
        return R.ok(Map.of("bluetoothEnabled", appProperties.isBluetoothEnabled()));
    }
}
