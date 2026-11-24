package cc.bexerlmao.gazette.module.device.service;

import cn.hutool.http.HttpRequest;
import cc.bexerlmao.gazette.common.web.BizException;
import cc.bexerlmao.gazette.module.device.entity.DataSource;
import cc.bexerlmao.gazette.module.device.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI 调用代理：把本服务变成 OpenAI 兼容的统一调用入口。
 *
 * POST /api/ai/{provider}/chat/completions
 *   - 上游 Key 取自数据源设置（t_data_source），调用方无需再带 Key；
 *   - 响应原样透传；本服务不落库、不记录任何用量/对话数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProxyService {

    private final DataSourceMapper dataSourceMapper;

    @Value("${gazette.ai-proxy.glm.url:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String glmChatUrl;

    @Value("${gazette.ai-proxy.deepseek.url:https://api.deepseek.com/chat/completions}")
    private String deepseekChatUrl;

    @Value("${gazette.security.secret:" + DataSourceCrypto.DEFAULT_SECRET + "}")
    private String cryptoSecret;

    /** 转发 chat/completions，返回上游原始 JSON 响应 */
    public String relay(String provider, String requestBody) {
        String key = apiKey(provider);
        if (key == null || key.isBlank()) {
            throw new BizException("未配置 " + provider + " 的 API Key，请先在数据源设置中配置");
        }
        String upstream = "glm".equals(provider) ? glmChatUrl : deepseekChatUrl;
        log.info("ai proxy relay: provider={}", provider);
        return HttpRequest.post(upstream)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .timeout(180_000)
                .execute()
                .body();
    }

    private String apiKey(String provider) {
        DataSource ds = dataSourceMapper.selectById(provider);
        return ds == null ? null : DataSourceCrypto.decrypt(ds.getApiKey(), cryptoSecret);
    }
}
