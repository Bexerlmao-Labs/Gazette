package cc.bexerlmao.gazette.module.device.render;

import lombok.Data;

/**
 * 位图定义方案（场景级转换模板，前端传入，拆列存储于 t_scene.bitmap_* 字段）。
 *
 * 服务端内部统一使用规范色彩（最多 6 色）：黑/白/红/黄/蓝/绿；
 * 下发前按本配置将每个像素转换为颜色编码 code（十六进制单字符 0-F），
 * 按 bits 位/像素紧缩打包，bitOrder 决定字节内位序，行宽补齐到整字节。
 * 某颜色未配置（null）表示不支持该色，渲染时该色像素回退为灰度黑白。
 */
@Data
public class BitmapEncoding {

    /** 每像素位宽 1/2/3/4；null 时按最大编码自动推导 */
    private Integer bits;

    /** 字节内位序：msb 高位在前（默认）/ lsb 低位在前 */
    private String bitOrder;

    /** 各颜色的编码（十六进制单字符 0-F），null = 不支持该色 */
    private String black;
    private String white;
    private String red;
    private String yellow;
    private String blue;
    private String green;

    public boolean isLsb() {
        return "lsb".equalsIgnoreCase(bitOrder);
    }

    public int bitsOr1() {
        Integer b = bits;
        return b == null || b < 1 ? 1 : Math.min(b, 4);
    }

    /** 解析十六进制编码，非法/空返回 -1 */
    public static int parseCode(String code) {
        if (code == null || code.isBlank()) {
            return -1;
        }
        return Character.digit(code.trim().charAt(0), 16);
    }

    /** 规范化编码：取首字符大写，非法返回 null */
    public static String normCode(String code) {
        int v = parseCode(code);
        return v < 0 ? null : "0123456789ABCDEF".charAt(v) + "";
    }

    /** 下发协议中的 format 标识，设备端据此解析 image 字段 */
    public String format() {
        return "bpp%d-%s".formatted(bitsOr1(), isLsb() ? "lsb" : "msb");
    }
}
