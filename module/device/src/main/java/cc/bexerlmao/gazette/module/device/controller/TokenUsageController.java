package cc.bexerlmao.gazette.module.device.controller;

import cc.bexerlmao.gazette.common.web.BizException;
import cc.bexerlmao.gazette.common.web.R;
import cc.bexerlmao.gazette.module.device.entity.DataSource;
import cc.bexerlmao.gazette.module.device.mapper.DataSourceMapper;
import cc.bexerlmao.gazette.module.device.service.TokenUsageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量数据源接口（前端编辑器用）：
 * GET  /api/token-usage/variables      变量注册表（编辑器展示可用 {变量}）
 * GET  /api/token-usage/keys           各数据源配置状态（是否已配置 + 认证方式）
 * POST /api/token-usage/keys           保存认证配置（只保存填写的字段，其余保留原值）
 * GET  /api/token-usage/usage?provider 实时拉取数据集片段（联调/测试用）
 */
@RestController
@RequestMapping("/api/token-usage")
@RequiredArgsConstructor
public class TokenUsageController {

    private final TokenUsageService tokenUsageService;
    private final DataSourceMapper dataSourceMapper;

    @Data
    public static class SaveAuthRequest {
        private String provider;
        /** api_key / platform_cookie / platform_password */
        private String authType;
        private String apiKey;
        private String account;
        private String password;
        private String cookie;
    }

    @GetMapping("/variables")
    public R<List<TokenUsageService.VariableInfo>> variables() {
        return R.ok(TokenUsageService.VARIABLES);
    }

    @GetMapping("/keys")
    public R<Map<String, Map<String, Object>>> keys() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (String provider : TokenUsageService.PROVIDERS) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("configured", tokenUsageService.isConfigured(provider));
            DataSource ds = dataSourceMapper.selectById(provider);
            status.put("authType", ds == null ? null : ds.getAuthType());
            status.put("hasAccount", ds != null && ds.getAccount() != null && !ds.getAccount().isBlank());
            status.put("hasCookie", ds != null && ds.getCookieEnc() != null && !ds.getCookieEnc().isBlank());
            if (!"ai".equals(provider)) {
                out.put(provider, status);
            }
        }
        return R.ok(out);
    }

    @PostMapping("/keys")
    public R<Void> saveKey(@RequestBody SaveAuthRequest req) {
        if (req.getProvider() == null || !List.of("glm", "deepseek").contains(req.getProvider())) {
            throw new BizException("不支持的数据源: " + req.getProvider());
        }
        if (req.getApiKey() == null && req.getAccount() == null
                && req.getPassword() == null && req.getCookie() == null && req.getAuthType() == null) {
            throw new BizException("没有要保存的配置");
        }
        if ("platform_password".equals(req.getAuthType())
                && (req.getAccount() == null || req.getPassword() == null)) {
            throw new BizException("账号密码方式需要同时填写账号与密码");
        }
        tokenUsageService.saveAuth(req.getProvider(), req.getAuthType(), req.getApiKey(),
                req.getAccount(), req.getPassword(), req.getCookie());
        return R.ok();
    }

    @GetMapping("/usage")
    public R<Map<String, Object>> usage(@RequestParam String provider) {
        if (!TokenUsageService.PROVIDERS.contains(provider)) {
            throw new BizException("不支持的数据源: " + provider);
        }
        return R.ok(tokenUsageService.usage(provider));
    }
}
