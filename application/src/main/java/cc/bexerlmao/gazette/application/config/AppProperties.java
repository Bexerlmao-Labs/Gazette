package cc.bexerlmao.gazette.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级功能开关配置（由 application-dev.yaml / application-prod.yaml 控制）。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 是否启用蓝牙连接页：prod 开启，dev 关闭 */
    private boolean bluetoothEnabled = true;
}
