package cc.bexerlmao.gazette.module.device.service;

import cc.bexerlmao.gazette.module.device.entity.Device;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Token 用量数据源实现：把 GLM / DeepSeek 的用量数据集并入变量集合，
 * 与其他 DeviceDataProvider（weather/sensor 等）的输出合并。
 */
@Component
@Primary
public class TokenUsageDataProvider implements DeviceDataProvider {

    private final ObjectProvider<DeviceDataProvider> providers;
    private final TokenUsageService tokenUsageService;

    public TokenUsageDataProvider(ObjectProvider<DeviceDataProvider> providers,
                                  TokenUsageService tokenUsageService) {
        this.providers = providers;
        this.tokenUsageService = tokenUsageService;
    }

    @Override
    public Map<String, Object> provide(Device device) {
        Map<String, Object> data = new HashMap<>();
        // 合并其他数据源（None 等）
        for (DeviceDataProvider other : providers) {
            if (other == this) {
                continue;
            }
            data.putAll(other.provide(device));
        }
        // Token 用量命名空间：glm / deepseek；未配置或拉取失败时返回空，对应元素按缺失跳过
        for (String provider : TokenUsageService.PROVIDERS) {
            try {
                data.putAll(tokenUsageService.usage(provider));
            } catch (Exception e) {
                // 单个数据源故障不影响整屏渲染
            }
        }
        return data;
    }
}
