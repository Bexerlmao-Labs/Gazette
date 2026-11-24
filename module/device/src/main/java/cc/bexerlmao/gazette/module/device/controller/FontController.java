package cc.bexerlmao.gazette.module.device.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 点阵字库接口：前端画布组件的文字渲染从后端调用字库，
 * 字库文件位于 module/device/src/main/resources/font/pixel-font.json
 * （由 tools/FontGen.java 离线栅格化生成：CJK 16×16、ASCII 10×16）。
 */
@RestController
@RequestMapping("/api")
public class FontController {

    private volatile String cached;

    @GetMapping("/font")
    public ResponseEntity<String> font() {
        String body = cached;
        if (body == null) {
            try (InputStream in = getClass().getResourceAsStream("/font/pixel-font.json")) {
                if (in == null) {
                    return ResponseEntity.notFound().build();
                }
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                cached = body;
            } catch (Exception e) {
                return ResponseEntity.internalServerError().build();
            }
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
