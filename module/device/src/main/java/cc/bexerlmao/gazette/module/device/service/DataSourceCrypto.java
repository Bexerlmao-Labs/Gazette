package cc.bexerlmao.gazette.module.device.service;

import cn.hutool.crypto.SecureUtil;

import java.nio.charset.StandardCharsets;

/**
 * 数据源敏感字段加解密（AES）。
 * 密钥来自 gazette.security.secret（SHA-256 截取 32 字节）。
 * 注意：密钥泄露即可解密，生产环境必须更换默认值并保护配置文件。
 */
public final class DataSourceCrypto {

    public static final String DEFAULT_SECRET = "gazette-dev-secret-change-me";

    private DataSourceCrypto() {
    }

    public static String encrypt(String plain, String secret) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        return SecureUtil.aes(key(secret)).encryptBase64(plain);
    }

    public static String decrypt(String enc, String secret) {
        if (enc == null || enc.isBlank()) {
            return null;
        }
        return SecureUtil.aes(key(secret)).decryptStr(enc);
    }

    private static byte[] key(String secret) {
        String hex = SecureUtil.sha256(secret == null || secret.isBlank() ? DEFAULT_SECRET : secret);
        return hex.substring(0, 32).getBytes(StandardCharsets.UTF_8);
    }
}
