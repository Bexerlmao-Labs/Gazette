package cc.bexerlmao.gazette.module.device.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cc.bexerlmao.gazette.module.device.entity.DataSource;
import cc.bexerlmao.gazette.module.device.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 用量数据源（GLM 智谱开放平台 / DeepSeek），见 docs/dynamic-content-design.md §4.1。
 *
 * 融合命名空间 ai.*：GLM（账单用量）+ DeepSeek（平台用量 + 官方余额）统一汇总。
 *
 * 用量数据一律【不落库】：渲染时实时向平台拉取，仅 60s 内存缓存防频控。
 *
 * DeepSeek 用量来源（按 auth_type）：
 *   api_key           仅余额（官方无用量汇总接口，已实测 /dashboard/billing/usage 等均 404）
 *   platform_cookie   平台登录态 Cookie（推荐）：用户浏览器登录 platform.deepseek.com/usage
 *                     后复制 Cookie 粘贴，AES 加密存储，不碰账号密码
 *   platform_password 账号密码：⚠️ 加密存储在服务端数据库，自动登录可能被 WAF/验证码拦截
 *
 * 变量一览（点分路径，供 DSL {变量} 占位）见 VARIABLES。
 * 空缺处理：月度序列固定近 12 个月档位，未使用月份填 0，month_labels 标注月份。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    public record VariableInfo(String name, String desc, String type) {}

    /** 变量注册表：前端编辑器据此展示可用变量 */
    public static final List<VariableInfo> VARIABLES = List.of(
            /* ---- AI 融合 ---- */
            new VariableInfo("ai.total_tokens", "AI 累计 token（GLM 账单 + DeepSeek 平台）", "scalar"),
            new VariableInfo("ai.balance.total", "AI 账户余额（DeepSeek，元）", "scalar"),
            new VariableInfo("ai.usage.pie_seq", "AI 用量构成 [GLM, DeepSeek]（饼图）", "series"),
            /* ---- GLM ---- */
            new VariableInfo("glm.usage.total_tokens", "GLM 近 7 天累计 token 用量", "scalar"),
            new VariableInfo("glm.usage.requests", "GLM 近 7 天调用次数", "scalar"),
            new VariableInfo("glm.usage.seq", "GLM 近 7 天每日用量序列", "series"),
            new VariableInfo("glm.usage.seq_labels", "GLM 近 7 天日期标签", "labels"),
            new VariableInfo("glm.usage.month_seq", "GLM 近 12 个月用量（空缺月=0）", "series"),
            new VariableInfo("glm.usage.month_labels", "GLM 近 12 个月月份标签", "labels"),
            /* ---- DeepSeek ---- */
            new VariableInfo("deepseek.balance.total", "DeepSeek 账户总余额（元）", "scalar"),
            new VariableInfo("deepseek.balance.granted", "DeepSeek 赠送余额（元）", "scalar"),
            new VariableInfo("deepseek.balance.topped_up", "DeepSeek 充值余额（元）", "scalar"),
            new VariableInfo("deepseek.balance.pie_seq", "DeepSeek 余额构成 [赠送, 充值]（饼图）", "series"),
            new VariableInfo("deepseek.usage.total_tokens", "DeepSeek 累计 token（平台登录态实时查询）", "scalar"),
            new VariableInfo("deepseek.usage.month_seq", "DeepSeek 近 12 个月用量（空缺月=0）", "series"),
            new VariableInfo("deepseek.usage.month_labels", "DeepSeek 月份标签", "labels")
    );

    public static final List<String> PROVIDERS = List.of("glm", "deepseek", "ai");

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final DataSourceMapper dataSourceMapper;

    @Value("${gazette.token-usage.glm.url:https://open.bigmodel.cn/api/paas/v4/dashboard/billing/usage}")
    private String glmUrl;

    @Value("${gazette.token-usage.deepseek.url:https://api.deepseek.com/user/balance}")
    private String deepseekUrl;

    /** DeepSeek 平台用量接口：浏览器 F12 看 /usage 页面的 XHR 后可覆盖 */
    @Value("${gazette.token-usage.deepseek.platform-url:https://platform.deepseek.com/api/v0/users/current/usage}")
    private String dsPlatformUsageUrl;

    /** DeepSeek 平台登录接口（账号密码方式，尽力而为） */
    @Value("${gazette.token-usage.deepseek.platform-login-url:https://platform.deepseek.com/auth-api/v1/users/login}")
    private String dsPlatformLoginUrl;

    /** 敏感字段加密密钥（生产必须更换） */
    @Value("${gazette.security.secret:" + DataSourceCrypto.DEFAULT_SECRET + "}")
    private String cryptoSecret;

    /** 内存缓存：防止设备高频轮询打爆上游；仅内存，重启失效，非持久化 */
    private static final long CACHE_TTL_MS = 60_000L;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(long at, Map<String, Object> data) {}

    /* ============================================================ 公共入口 */

    /** 返回数据集片段 {"glm"/"deepseek"/"ai": {...}}；未配置/失败返回空 Map（元素按缺失跳过） */
    public Map<String, Object> usage(String provider) {
        Cached c = cache.get(provider);
        if (c != null && System.currentTimeMillis() - c.at < CACHE_TTL_MS) {
            return c.data();
        }
        try {
            Map<String, Object> data = switch (provider) {
                case "glm" -> Map.of("glm", glmDataset());
                case "deepseek" -> Map.of("deepseek", deepseekDataset());
                case "ai" -> Map.of("ai", aiDataset());
                default -> Map.of();
            };
            if (!data.isEmpty()) {
                cache.put(provider, new Cached(System.currentTimeMillis(), data));
            }
            return data;
        } catch (Exception e) {
            log.warn("token usage fetch failed: provider={} {}", provider, e.getMessage());
            return Map.of();
        }
    }

    /* ============================================================ 凭证管理 */

    public String apiKey(String provider) {
        DataSource ds = dataSourceMapper.selectById(provider);
        return ds == null ? null : DataSourceCrypto.decrypt(ds.getApiKey(), cryptoSecret);
    }

    /** 保存完整认证配置；未填写的字段保留原值（null=不修改，仅更新你配置的那部分） */
    public void saveAuth(String provider, String authType, String apiKey,
                         String account, String password, String cookie) {
        DataSource existing = dataSourceMapper.selectById(provider);
        boolean insert = existing == null;
        DataSource ds = insert ? new DataSource() : existing;
        ds.setProvider(provider);
        if (authType != null && !authType.isBlank()) {
            ds.setAuthType(authType);
        }
        if (ds.getAuthType() == null || ds.getAuthType().isBlank()) {
            ds.setAuthType("api_key");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            ds.setApiKey(DataSourceCrypto.encrypt(apiKey, cryptoSecret));
        }
        if (account != null) {
            ds.setAccount(account.isBlank() ? null : account.trim());
        }
        if (password != null) {
            ds.setSecretEnc(password.isBlank() ? null : DataSourceCrypto.encrypt(password, cryptoSecret));
        }
        if (cookie != null) {
            ds.setCookieEnc(cookie.isBlank() ? null : DataSourceCrypto.encrypt(cookie, cryptoSecret));
        }
        ds.setUpdatedAt(LocalDateTime.now());
        if (insert) {
            dataSourceMapper.insert(ds);
        } else {
            dataSourceMapper.updateById(ds);
        }
        cache.remove(provider);
    }

    public boolean isConfigured(String provider) {
        DataSource ds = dataSourceMapper.selectById(provider);
        return ds != null && ds.getApiKey() != null && !ds.getApiKey().isBlank();
    }

    /* ============================================================ GLM */

    /** GLM 数据集：一次拉 12 个月账单，本地聚合成近 7 天与近 12 个月两档序列 */
    private Map<String, Object> glmDataset() {
        String key = apiKey("glm");
        if (key == null || key.isBlank()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(11).withDayOfMonth(1);
        String body = HttpRequest.get(glmUrl)
                .header("Authorization", "Bearer " + key)
                .form("start_date", start.toString())
                .form("end_date", today.toString())
                .timeout(15_000)
                .execute()
                .body();
        log.debug("glm usage response: {}", body);

        // 日用量表（账单没有的日期 = 未使用 = 0）
        TreeMap<LocalDate, Double> daily = new TreeMap<>();
        long requests = 0;
        JSONObject json = JSONUtil.parseObj(body);
        JSONArray arr = json.getJSONArray("data");
        if (arr != null) {
            for (Object o : arr) {
                JSONObject day = (JSONObject) o;
                LocalDate d = parseFlexibleDate(day.getStr("date"));
                if (d == null) {
                    continue;
                }
                Double v = firstNumber(day, "aggregation_usage", "total_usage", "total_tokens", "usage");
                if (v != null) {
                    daily.merge(d, v, Double::sum);
                }
                Double cnt = firstNumber(day, "requests", "request_count", "aggregation_requests");
                if (cnt != null) {
                    requests += cnt.longValue();
                }
            }
        }

        List<Double> seq7 = new ArrayList<>();
        List<String> seq7Labels = new ArrayList<>();
        double total7 = 0;
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            double v = daily.getOrDefault(d, 0.0);
            seq7.add(v);
            seq7Labels.add(d.format(DateTimeFormatter.ofPattern("MM-dd")));
            total7 += v;
        }
        List<Double> monthSeq = new ArrayList<>();
        List<String> monthLabels = new ArrayList<>();
        YearMonth m0 = YearMonth.from(start);
        Map<YearMonth, Double> monthly = new HashMap<>();
        daily.forEach((d, v) -> monthly.merge(YearMonth.from(d), v, Double::sum));
        for (int i = 0; i < 12; i++) {
            YearMonth m = m0.plusMonths(i);
            monthSeq.add(monthly.getOrDefault(m, 0.0));
            monthLabels.add(m.toString());
        }

        Map<String, Object> usage = new HashMap<>();
        usage.put("total_tokens", Math.round(total7));
        usage.put("seq", seq7);
        usage.put("seq_labels", seq7Labels);
        usage.put("month_seq", monthSeq);
        usage.put("month_labels", monthLabels);
        if (requests > 0) {
            usage.put("requests", requests);
        }
        Map<String, Object> ns = new HashMap<>();
        ns.put("usage", usage);
        return ns;
    }

    /* ============================================================ DeepSeek */

    /** DeepSeek 数据集：余额走官方接口；用量按认证方式（Cookie/密码/代理累计降级） */
    private Map<String, Object> deepseekDataset() {
        DataSource ds = dataSourceMapper.selectById("deepseek");
        String apiKey = ds == null ? null : DataSourceCrypto.decrypt(ds.getApiKey(), cryptoSecret);

        Map<String, Object> ns = new HashMap<>();
        // 余额始终走官方接口（需要 API Key）
        if (apiKey != null && !apiKey.isBlank()) {
            ns.putAll(fetchDeepseekBalance(apiKey));
        }
        // 用量：仅平台登录态（Cookie / 账号密码）实时查询；未配置则变量缺失（元素跳过）
        Map<String, Object> usage = null;
        if (ds != null && "platform_cookie".equals(ds.getAuthType())) {
            usage = tryFetchDsPlatformUsage(DataSourceCrypto.decrypt(ds.getCookieEnc(), cryptoSecret));
        } else if (ds != null && "platform_password".equals(ds.getAuthType())) {
            String cookie = loginForCookie(ds);
            if (cookie != null) {
                usage = tryFetchDsPlatformUsage(cookie);
            }
        }
        if (usage != null) {
            ns.put("usage", usage);
        }
        return ns;
    }

    private Map<String, Object> fetchDeepseekBalance(String key) {
        String body = HttpRequest.get(deepseekUrl)
                .header("Authorization", "Bearer " + key)
                .timeout(10_000)
                .execute()
                .body();
        log.debug("deepseek balance response: {}", body);

        JSONObject json = JSONUtil.parseObj(body);
        Map<String, Object> balance = new HashMap<>();
        double granted = 0;
        double toppedUp = 0;
        JSONArray infos = json.getJSONArray("balance_infos");
        if (infos != null && !infos.isEmpty()) {
            JSONObject info = infos.getJSONObject(0);
            balance.put("currency", info.getStr("currency", "CNY"));
            balance.put("total", parseDecimal(info.getStr("total_balance")));
            granted = parseDecimal(info.getStr("granted_balance"));
            toppedUp = parseDecimal(info.getStr("topped_up_balance"));
            balance.put("granted", granted);
            balance.put("topped_up", toppedUp);
        }
        balance.put("pie_seq", List.of(granted, toppedUp));
        Map<String, Object> ns = new HashMap<>();
        ns.put("balance", balance);
        return ns;
    }

    /** 平台 Cookie 拉取用量；失败抛出，由调用方降级 */
    private Map<String, Object> tryFetchDsPlatformUsage(String cookie) {
        if (cookie == null || cookie.isBlank()) {
            return null;
        }
        try {
            String body = HttpRequest.get(dsPlatformUsageUrl)
                    .header("Cookie", cookie)
                    .header("User-Agent", BROWSER_UA)
                    .header("Referer", "https://platform.deepseek.com/usage")
                    .header("Accept", "application/json")
                    .timeout(15_000)
                    .execute()
                    .body();
            log.debug("deepseek platform usage response: {}", body);
            List<DayValue> points = extractDayValues(JSONUtil.parseObj(body));
            if (points.isEmpty()) {
                log.warn("deepseek platform usage: no recognizable usage entries, fallback to proxy stats");
                return null;
            }
            return buildUsageNs(points, null);
        } catch (Exception e) {
            log.warn("deepseek platform usage fetch failed: {}", e.getMessage());
            return null;
        }
    }

    /** 账号密码自动登录换取 Cookie（尽力而为：WAF/验证码会拦截，失败提示改用 Cookie 方式） */
    private String loginForCookie(DataSource ds) {
        String account = ds.getAccount();
        String password = DataSourceCrypto.decrypt(ds.getSecretEnc(), cryptoSecret);
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            log.warn("deepseek platform login skipped: account/password not set");
            return null;
        }
        try {
            HttpResponse resp = HttpRequest.post(dsPlatformLoginUrl)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", BROWSER_UA)
                    .body(JSONUtil.toJsonStr(Map.of("mobile", account, "username", account,
                            "email", account, "password", password)))
                    .timeout(15_000)
                    .execute();
            List<java.net.HttpCookie> cookies = resp.getCookies();
            if (cookies == null || cookies.isEmpty()) {
                log.warn("deepseek platform login: no cookie returned (WAF/captcha likely), use cookie mode");
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (java.net.HttpCookie c : cookies) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(c.getName()).append('=').append(c.getValue());
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("deepseek platform login failed: {}", e.getMessage());
            return null;
        }
    }

    /* ============================================================ AI 融合 */

    /** ai.* 融合数据集：GLM 账单 + DeepSeek（平台登录态）用量与余额，全部实时拉取 */
    private Map<String, Object> aiDataset() {
        Map<String, Object> glm = datasetCached("glm");
        Map<String, Object> ds = datasetCached("deepseek");

        double glmTokens = numberByPath(glm, "usage.total_tokens", 0.0);
        double dsTokens = numberByPath(ds, "usage.total_tokens", 0.0);
        double balance = numberByPath(ds, "balance.total", 0.0);

        Map<String, Object> ai = new HashMap<>();
        ai.put("total_tokens", Math.round(glmTokens + dsTokens));
        ai.put("balance", Map.of("total", balance));
        ai.put("usage", Map.of("pie_seq", List.of(glmTokens, dsTokens)));
        return ai;
    }

    /* ============================================================ 工具 */

    /** 带内层缓存的单一数据源数据集（aiDataset 复用，避免重复拉取） */
    private Map<String, Object> datasetCached(String provider) {
        try {
            if ("glm".equals(provider)) {
                return glmDataset();
            }
            if ("deepseek".equals(provider)) {
                return deepseekDataset();
            }
        } catch (Exception e) {
            log.warn("dataset fetch failed: provider={} {}", provider, e.getMessage());
        }
        return Map.of();
    }

    /** 通用解析：递归收集 (日期, 用量) 对，兼容平台接口的多种响应形态 */
    private List<DayValue> extractDayValues(Object node) {
        List<DayValue> out = new ArrayList<>();
        collectDayValues(node, out, 0);
        return out;
    }

    private void collectDayValues(Object node, List<DayValue> out, int depth) {
        if (depth > 6 || node == null) {
            return;
        }
        if (node instanceof JSONObject obj) {
            LocalDate date = null;
            for (String k : List.of("date", "day", "time", "created_at", "date_time")) {
                LocalDate d = parseFlexibleDate(obj.getStr(k));
                if (d != null) {
                    date = d;
                    break;
                }
            }
            if (date != null) {
                Double v = firstNumber(obj, "aggregation_usage", "total_tokens", "usage", "total", "amount");
                if (v != null) {
                    out.add(new DayValue(date, v));
                    return;
                }
            }
            for (String key : obj.keySet()) {
                collectDayValues(obj.get(key), out, depth + 1);
            }
        } else if (node instanceof JSONArray arr) {
            for (Object o : arr) {
                collectDayValues(o, out, depth + 1);
            }
        }
    }

    private record DayValue(LocalDate date, double value) {}

    /** 把 (日期, 值) 列表聚合成 usage 命名空间：total + 近 12 个月月度（空缺月=0） */
    private Map<String, Object> buildUsageNs(List<DayValue> points, Long totalOverride) {
        Map<YearMonth, Double> monthly = new HashMap<>();
        double total = 0;
        for (DayValue p : points) {
            monthly.merge(YearMonth.from(p.date()), p.value(), Double::sum);
            total += p.value();
        }
        List<Double> monthSeq = new ArrayList<>();
        List<String> monthLabels = new ArrayList<>();
        YearMonth m0 = YearMonth.from(LocalDate.now().minusMonths(11));
        for (int i = 0; i < 12; i++) {
            YearMonth m = m0.plusMonths(i);
            monthSeq.add(monthly.getOrDefault(m, 0.0));
            monthLabels.add(m.toString());
        }
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("total_tokens", totalOverride != null ? totalOverride : Math.round(total));
        usage.put("month_seq", monthSeq);
        usage.put("month_labels", monthLabels);
        return usage;
    }

    /** 宽松解析日期：yyyy-MM-dd / yyyyMMdd / yyyy-MM/dd 等 */
    private LocalDate parseFlexibleDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String digits = s.replaceAll("[^0-9]", "");
        try {
            if (digits.length() == 8) {
                return LocalDate.parse(digits, DateTimeFormatter.BASIC_ISO_DATE);
            }
            return LocalDate.parse(s.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private Double firstNumber(JSONObject json, String... keys) {
        for (String key : keys) {
            Object v = json.get(key);
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            if (v instanceof String s && !s.isBlank()) {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个键
                }
            }
        }
        return null;
    }

    /** 嵌套 Map 点分路径取数值，缺失返回 def */
    private Double numberByPath(Map<String, Object> data, String path, Double def) {
        if (data == null) {
            return def;
        }
        Object cur = data;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m) || !m.containsKey(part)) {
                return def;
            }
            cur = m.get(part);
        }
        return cur instanceof Number n ? n.doubleValue() : def;
    }

    private double parseDecimal(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
