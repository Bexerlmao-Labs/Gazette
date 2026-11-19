package cc.bexerlmao.gazette.module.device.render;

import com.google.zxing.BarcodeFormat;
import cc.bexerlmao.gazette.module.device.entity.Scene;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景渲染器：静态底图 + DSL 动态元素 + 数据集 → 最终 1bpp 位图（PNG）。
 * 渲染只在服务端进行，设备端只消费最终位图与哈希。
 */
@Slf4j
@Service
public class SceneRenderService {

    private static final int[] FONT_SIZES = {12, 16, 24, 32, 48};
    private static final Color BLACK = Color.BLACK;
    private static final Color WHITE = Color.WHITE;

    /** 渲染结果：PNG 字节 + 内容哈希 */
    public record Rendered(byte[] png, String hash) {
    }

    /**
     * 渲染场景。data 为变量数据集（点分命名空间的键值对，数组用于 chart 序列）。
     */
    public Rendered render(Scene scene, Map<String, Object> data) {
        int w = scene.getWidth() == null || scene.getWidth() <= 0 ? 128 : scene.getWidth();
        int h = scene.getHeight() == null || scene.getHeight() <= 0 ? 296 : scene.getHeight();
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(WHITE);
            g.fillRect(0, 0, w, h);
            drawBaseImage(g, scene.getBaseImage(), w, h);
            Map<String, Object> ds = data == null ? Map.of() : data;
            for (DslElement el : DslParser.parse(scene.getOverlayDsl())) {
                try {
                    drawElement(g, el, ds, w, h);
                } catch (Exception e) {
                    // 单个元素渲染失败不影响整屏
                    log.warn("element render failed: {}", el.getRaw(), e);
                }
            }
        } finally {
            g.dispose();
        }
        byte[] png = to1bppPng(canvas);
        return new Rendered(png, sha256Hex(png));
    }

    /* ------------------------------------------------------------ 元素分发 */

    private void drawElement(Graphics2D g, DslElement el, Map<String, Object> data, int cw, int ch) {
        switch (el.getType()) {
            case "text" -> drawText(g, el, data);
            case "datetime" -> drawDatetime(g, el, data);
            case "line" -> drawLine(g, el);
            case "polyline" -> drawPolyline(g, el);
            case "rect" -> drawRect(g, el);
            case "icon" -> drawIcon(g, el, data);
            case "qr" -> drawQr(g, el, data);
            case "chart" -> drawChart(g, el, data);
            default -> log.debug("unknown dsl element type: {}", el.getType());
        }
    }

    /* ------------------------------------------------------------ 基础元素 */

    private void drawBaseImage(Graphics2D g, byte[] baseImage, int cw, int ch) {
        if (baseImage == null || baseImage.length == 0) {
            return;
        }
        try {
            Image img = ImageIO.read(new ByteArrayInputStream(baseImage));
            if (img != null) {
                g.drawImage(img, 0, 0, cw, ch, null);
            }
        } catch (Exception e) {
            log.warn("base image draw failed", e);
        }
    }

    private Font font(DslElement el, int style) {
        int size = FONT_SIZES[Math.min(Math.max(parseInt(el.attr("font", "1"), 1) - 1, 0), FONT_SIZES.length - 1)];
        return new Font("SansSerif", style, size);
    }

    private void drawText(Graphics2D g, DslElement el, Map<String, Object> data) {
        String resolved = resolveText(el.getText(), data);
        if (resolved == null) {
            return; // 变量缺失则跳过，避免一个数据源故障毁掉整屏
        }
        g.setColor(BLACK);
        g.setFont(font(el, Font.PLAIN));
        FontMetrics fm = g.getFontMetrics();
        int x = el.x(), y = el.y();
        int lineH = fm.getHeight();
        for (String line : resolved.split("\\\\n")) {
            int tx = x;
            if ("center".equals(el.attr("align")) && el.getW() != null) {
                tx = x + (el.getW() - fm.stringWidth(line)) / 2;
            } else if ("right".equals(el.attr("align")) && el.getW() != null) {
                tx = x + el.getW() - fm.stringWidth(line);
            }
            g.drawString(line, tx, y + fm.getAscent());
            y += lineH;
        }
    }

    private void drawDatetime(Graphics2D g, DslElement el, Map<String, Object> data) {
        ZoneId zone = ZoneId.of(el.attr("tz", ZoneId.systemDefault().getId()));
        ZonedDateTime now = ZonedDateTime.now(zone);
        g.setColor(BLACK);
        g.setFont(font(el, Font.PLAIN));
        String text = now.format(DateTimeFormatter.ofPattern(toJavaPattern(el.attr("format", "%H:%M"))));
        g.drawString(text, el.x(), el.y() + g.getFontMetrics().getAscent());
    }

    private void drawLine(Graphics2D g, DslElement el) {
        List<Integer> c = el.getCoords();
        if (c.size() < 4) {
            return;
        }
        g.setColor(BLACK);
        g.setStroke(new BasicStroke(parseInt(el.attr("thickness", "1"), 1)));
        g.drawLine(c.get(0), c.get(1), c.get(2), c.get(3));
    }

    private void drawPolyline(Graphics2D g, DslElement el) {
        List<Integer> c = el.getCoords();
        if (c.size() < 4) {
            return;
        }
        g.setColor(BLACK);
        g.setStroke(new BasicStroke(parseInt(el.attr("thickness", "1"), 1)));
        for (int i = 2; i + 1 < c.size(); i += 2) {
            g.drawLine(c.get(i - 2), c.get(i - 1), c.get(i), c.get(i + 1));
        }
    }

    private void drawRect(Graphics2D g, DslElement el) {
        if (el.getW() == null || el.getH() == null) {
            return;
        }
        g.setColor(BLACK);
        if ("1".equals(el.attr("fill", "0"))) {
            g.fillRect(el.x(), el.y(), el.getW(), el.getH());
        } else {
            g.setStroke(new BasicStroke(parseInt(el.attr("thickness", "1"), 1)));
            g.drawRect(el.x(), el.y(), el.getW(), el.getH());
        }
    }

    /* ------------------------------------------------------------ 复合元素 */

    private void drawIcon(Graphics2D g, DslElement el, Map<String, Object> data) {
        String name = resolveText(el.attr("name"), data);
        if (name == null) {
            return; // 图标值缺失则跳过
        }
        int size = el.getW() != null ? el.getW() : parseInt(el.attr("size", "16"), 16);
        int h = el.getH() != null ? el.getH() : size;
        // v1：图标集未内置，画占位框 + 名称缩写；接入图标集后替换为位图绘制
        g.setColor(BLACK);
        g.drawRect(el.x(), el.y(), size, h);
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.min(12, size)));
        g.drawString(name, el.x() + 2, el.y() + h - 3);
    }

    private void drawQr(Graphics2D g, DslElement el, Map<String, Object> data) {
        String payload = resolveText(el.getText(), data);
        if (payload == null) {
            return;
        }
        try {
            int size = el.getW() != null ? Math.min(el.getW(), el.getH() == null ? el.getW() : el.getH()) : 64;
            BitMatrix matrix = new QRCodeWriter()
                    .encode(payload, BarcodeFormat.QR_CODE, size, size);
            int cell = parseInt(el.attr("cell", "1"), 1);
            int x = el.x(), y = el.y();
            g.setColor(BLACK);
            for (int mx = 0; mx < matrix.getWidth(); mx++) {
                for (int my = 0; my < matrix.getHeight(); my++) {
                    if (matrix.get(mx, my)) {
                        g.fillRect(x + mx * cell, y + my * cell, cell, cell);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("qr render failed: {}", payload, e);
        }
    }

    /**
     * 图表（折线/柱状）。序列数据点来自数据集数组：
     * chart x,y,w,h type=line series=a,b [y-min=] [y-max=] [legend=1] [points=24]
     */
    private void drawChart(Graphics2D g, DslElement el, Map<String, Object> data) {
        if (el.getW() == null || el.getH() == null) {
            return;
        }
        String[] seriesNames = el.attr("series", "").split(",");
        if (seriesNames.length == 0 || seriesNames[0].isBlank()) {
            return;
        }
        int points = parseInt(el.attr("points", "0"), 0);
        Map<String, double[]> series = new HashMap<>();
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (String name : seriesNames) {
            double[] values = readSeries(data, name.trim());
            if (values == null) {
                return; // 任一序列缺失则跳过整个图表
            }
            if (points > 0 && values.length > points) {
                double[] tail = new double[points];
                System.arraycopy(values, values.length - points, tail, 0, points);
                values = tail;
            }
            for (double v : values) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            series.put(name.trim(), values);
        }
        if (min == Double.POSITIVE_INFINITY) {
            return;
        }
        double yMin = el.attr("y-min") != null ? Double.parseDouble(el.attr("y-min")) : min;
        double yMax = el.attr("y-max") != null ? Double.parseDouble(el.attr("y-max")) : max;
        if (yMax - yMin < 1e-9) {
            yMax = yMin + 1;
        }

        int x = el.x(), y = el.y(), w = el.getW(), h = el.getH();
        g.setColor(BLACK);
        g.setStroke(new BasicStroke(1));
        g.drawRect(x, y, w, h); // 外框

        String type = el.attr("type", "line");
        int count = series.values().iterator().next().length;
        int plotW = w - 2, plotH = h - 2;

        int[] xs = new int[count];
        for (int i = 0; i < count; i++) {
            xs[i] = count == 1 ? x + 1 : x + 1 + i * plotW / (count - 1);
        }
        int[] ys = new int[count];
        for (Map.Entry<String, double[]> se : series.entrySet()) {
            double[] values = se.getValue();
            for (int i = 0; i < values.length; i++) {
                ys[i] = y + 1 + (int) Math.round((yMax - values[i]) / (yMax - yMin) * (plotH - 1));
            }
            if ("bar".equals(type)) {
                int barW = Math.max(1, plotW / Math.max(count, 1) - 1);
                for (int i = 0; i < values.length; i++) {
                    int bx = count == 1 ? x + 1 : x + 1 + i * plotW / count;
                    int bh = y + h - 1 - ys[i];
                    g.fillRect(bx, ys[i], barW, Math.max(bh, 0));
                }
            } else {
                for (int i = 1; i < values.length; i++) {
                    g.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
                }
                for (int i = 0; i < values.length; i++) { // 数据点
                    g.fillRect(xs[i] - 1, ys[i] - 1, 2, 2);
                }
            }
        }

        if ("1".equals(el.attr("legend", "0"))) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            int lx = x + 2, ly = y + h + fm.getAscent() + 2;
            for (int si = 0; si < seriesNames.length; si++) {
                String name = seriesNames[si].trim();
                g.drawRect(lx, ly - fm.getAscent() + 2, 6, 6);
                if (si % 2 == 1) {
                    g.fillRect(lx + 1, ly - fm.getAscent() + 3, 4, 4);
                }
                g.drawString(name, lx + 10, ly);
                lx += 14 + fm.stringWidth(name) + 6;
            }
        }
    }

    /* ------------------------------------------------------------ 工具 */

    /** 点分路径取值：weather.temp → data.get("weather").get("temp") */
    @SuppressWarnings("unchecked")
    private Object lookup(Map<String, Object> data, String path) {
        Object cur = data;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m) || !m.containsKey(part)) {
                return null;
            }
            cur = m.get(part);
        }
        return cur;
    }

    /** 替换 {变量}；任一变量缺失返回 null（调用方跳过该元素） */
    private String resolveText(String template, Map<String, Object> data) {
        if (template == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (true) {
            int l = template.indexOf('{', i);
            if (l < 0) {
                sb.append(template, i, template.length());
                break;
            }
            int r = template.indexOf('}', l + 1);
            if (r < 0) {
                sb.append(template, i, template.length());
                break;
            }
            sb.append(template, i, l);
            Object v = lookup(data, template.substring(l + 1, r));
            if (v == null) {
                return null;
            }
            sb.append(v);
            i = r + 1;
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private double[] readSeries(Map<String, Object> data, String key) {
        Object v = lookup(data, key);
        if (v instanceof List<?> list) {
            return list.stream().mapToDouble(n -> ((Number) n).doubleValue()).toArray();
        }
        if (v instanceof Number n) {
            return new double[]{n.doubleValue()};
        }
        return null;
    }

    private String toJavaPattern(String fmt) {
        return fmt.replace("%Y", "yyyy").replace("%m", "MM").replace("%d", "dd")
                .replace("%H", "HH").replace("%M", "mm").replace("%S", "ss");
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** 彩色画布 → 1bpp（黑白）PNG，墨水屏友好 */
    private byte[] to1bppPng(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        IndexColorModel palette = new IndexColorModel(1, 2,
                new byte[]{0, (byte) 0xFF}, new byte[]{0, (byte) 0xFF}, new byte[]{0, (byte) 0xFF});
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY, palette);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int gray = (int) (((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000);
                out.setRGB(x, y, gray < 128 ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("png encode failed", e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append("%02x".formatted(b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed", e);
        }
    }
}
