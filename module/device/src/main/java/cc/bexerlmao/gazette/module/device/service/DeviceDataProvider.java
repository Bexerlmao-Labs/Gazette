package cc.bexerlmao.gazette.module.device.service;

import cc.bexerlmao.gazette.module.device.entity.Device;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 设备数据源扩展点：为场景 DSL 提供变量数据集（weather.temp、indoor_temp 等）。
 * v1 内置实现返回空数据集，仅 datetime 等自渲染元素生效；
 * 后续接入天气/传感器数据源时实现本接口注册为 Bean 即可。
 */
public interface DeviceDataProvider {

    /** 返回本次渲染使用的变量数据集，键为点分命名空间，数组值用于 chart 序列 */
    Map<String, Object> provide(Device device);

    @Component
    class None implements DeviceDataProvider {
        @Override
        public Map<String, Object> provide(Device device) {
            return Map.of();
        }
    }
}
