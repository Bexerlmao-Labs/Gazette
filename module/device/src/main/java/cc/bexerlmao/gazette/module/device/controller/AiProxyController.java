package cc.bexerlmao.gazette.module.device.controller;

import cc.bexerlmao.gazette.common.web.BizException;
import cc.bexerlmao.gazette.module.device.service.AiProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI 调用代理（OpenAI 兼容）：POST /api/ai/{provider}/chat/completions
 *
 * 调用方无需携带 Key（上游 Key 来自数据源设置）；响应为上游原始 JSON。
 * 用量在服务端逐笔累计，供 ai.* 变量（月度/每日序列）使用。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiProxyController {

    private final AiProxyService aiProxyService;

    @PostMapping(value = "/{provider}/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chatCompletions(@PathVariable String provider,
                                                  @RequestBody String body) {
        if (!"glm".equals(provider) && !"deepseek".equals(provider)) {
            throw new BizException("不支持的 AI 服务: " + provider);
        }
        String resp = aiProxyService.relay(provider, body);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
    }
}
