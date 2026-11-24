package cc.bexerlmao.gazette.module.device.render;

import com.google.zxing.BarcodeFormat;
import cc.bexerlmao.gazette.module.device.entity.Scene;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景渲染器：静态底图 + DSL 动态元素 + 数据集 → 最终 1bpp 打包位图（墨水屏原始位图）。
 * 渲染只在服务端进行，设备端只消费最终位图与哈希。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneRenderService {

    private static final int[] FONT_SIZES = {12, 16, 24, 32, 48};
    private static final Color BLACK = Color.BLACK;
    private static final Color WHITE = Color.WHITE;

    /** 规范色彩索引（服务端统一存储格式，最多 6 色） */
    private static final int C_BLACK = 0, C_WHITE = 1, C_RED = 2, C_YELLOW = 3, C_BLUE = 4, C_GREEN = 5;

    /** 规范色彩对应的预览 RGB */
    private static final int[] PREVIEW_RGB = {0x000000, 0xFFFFFF, 0xD03A3A, 0xE0C020, 0x2864C8, 0x2E8B44};

    /** 渲染结果：按场景位图定义方案编码后的位图 + 实际尺寸 + 协议格式标识 + 内容哈希 */
    public record Rendered(byte[] bitmap, int width, int height, String format, String hash) {
    }

    /**
     * 渲染场景。data 为变量数据集（点分命名空间的键值对，数组用于 chart 序列）。
     */
    public Rendered render(Scene scene, Map<String, Object> data) {
        BufferedImage canvas = renderCanvas(scene, data);
        BitmapEncoding enc = parseEncoding(scene);
        enc.setBits(resolveBits(enc)); // 位宽确定后 format 与打包逻辑保持一致
        int[] idx = quantize(canvas, enc); // 规范色彩索引 0-5
        byte[] bitmap = packCodes(idx, canvas.getWidth(), canvas.getHeight(), enc);
        return new Rendered(bitmap, canvas.getWidth(), canvas.getHeight(),
                enc.format(), sha256Hex(bitmap));
    }

    /** 位宽：优先取配置（1-4）；未配置时按最大颜色编码自动推导 */
    private int resolveBits(BitmapEncoding enc) {
        if (enc.getBits() != null) {
            return enc.bitsOr1();
        }
        int max = Math.max(
                Math.max(BitmapEncoding.parseCode(enc.getBlack()), BitmapEncoding.parseCode(enc.getWhite())),
                Math.max(Math.max(BitmapEncoding.parseCode(enc.getRed()), BitmapEncoding.parseCode(enc.getYellow())),
                        Math.max(BitmapEncoding.parseCode(enc.getBlue()), BitmapEncoding.parseCode(enc.getGreen()))));
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(max, 0)));
    }

    /** 浏览器预览用：规范色彩直接映射 RGB（黑/白/红/黄/蓝/绿），仅给人看，设备端不下发 PNG */
    public byte[] renderPreviewPng(Scene scene, Map<String, Object> data) {
        BufferedImage canvas = renderCanvas(scene, data);
        int[] idx = quantize(canvas, parseEncoding(scene));
        int[] rgb = new int[idx.length];
        for (int i = 0; i < idx.length; i++) {
            rgb[i] = PREVIEW_RGB[idx[i]];
        }
        BufferedImage out = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, canvas.getWidth(), canvas.getHeight(), rgb, 0, canvas.getWidth());
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("png encode failed", e);
        }
    }

    /** 从场景拆列拼装位图定义方案；未配置时按默认（1bpp/msb，黑=1 白=0，GxEPD2 风格） */
    private BitmapEncoding parseEncoding(Scene scene) {
        BitmapEncoding e = new BitmapEncoding();
        e.setBits(scene.getBitmapBits());
        e.setBitOrder(scene.getBitmapBitOrder());
        e.setBlack(defaultCode(scene.getBitmapBlack(), "1"));
        e.setWhite(defaultCode(scene.getBitmapWhite(), "0"));
        e.setRed(scene.getBitmapRed());
        e.setYellow(scene.getBitmapYellow());
        e.setBlue(scene.getBitmapBlue());
        e.setGreen(scene.getBitmapGreen());
        return e;
    }

    private String defaultCode(String code, String def) {
        return BitmapEncoding.parseCode(code) >= 0 ? code.trim().substring(0, 1) : def;
    }

    /**
     * 画布 → 规范色彩索引（服务端统一存储格式，0-5）：
     * 黑/白按感知灰度阈值二值化；红/黄/蓝/绿按色相判定，且仅在该色已配置编码时参与匹配。
     */
    private int[] quantize(BufferedImage src, BitmapEncoding enc) {
        boolean hasRed = BitmapEncoding.parseCode(enc.getRed()) >= 0;
        boolean hasYellow = BitmapEncoding.parseCode(enc.getYellow()) >= 0;
        boolean hasBlue = BitmapEncoding.parseCode(enc.getBlue()) >= 0;
        boolean hasGreen = BitmapEncoding.parseCode(enc.getGreen()) >= 0;
        int w = src.getWidth(), h = src.getHeight();
        int[] out = new int[w * h];
        int[] buf = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, buf, 0, w);
            for (int x = 0; x < w; x++) {
                int rgb = buf[x];
                int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                if (hasRed && isRed(r, g, b)) {
                    out[y * w + x] = C_RED;
                } else if (hasYellow && isYellow(r, g, b)) {
                    out[y * w + x] = C_YELLOW;
                } else if (hasBlue && isBlue(r, g, b)) {
                    out[y * w + x] = C_BLUE;
                } else if (hasGreen && isGreen(r, g, b)) {
                    out[y * w + x] = C_GREEN;
                } else {
                    int gray = (r * 299 + g * 587 + b * 114) / 1000;
                    out[y * w + x] = gray < 128 ? C_BLACK : C_WHITE;
                }
            }
        }
        return out;
    }

    /** 红色：r 明显大于 g/b */
    private boolean isRed(int r, int g, int b) {
        return r > 150 && r - Math.max(g, b) > 60;
    }

    /** 黄色：r/g 都高且接近，b 明显低 */
    private boolean isYellow(int r, int g, int b) {
        return r > 150 && g > 140 && Math.abs(r - g) < 80 && Math.min(r, g) - b > 60;
    }

    /** 蓝色：b 明显大于 r/g */
    private boolean isBlue(int r, int g, int b) {
        return b > 130 && b - Math.max(r, g) > 40;
    }

    /** 绿色：g 明显大于 r/b */
    private boolean isGreen(int r, int g, int b) {
        return g > 110 && g - Math.max(r, b) > 40;
    }

    private BufferedImage renderCanvas(Scene scene, Map<String, Object> data) {
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
        return canvas;
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
     * chart x,y,w,h type=line series=a,b [title=] [x-label=] [y-label=] [x-ticks=变量]
     *   [y-min=] [y-max=] [legend=1] [points=24]
     *
     * - title/x-label/y-label 支持 {变量}；标题顶部居中，x-label 在底部（无刻度时）
     * - x-ticks 指向数据集字符串数组变量，作为横轴刻度（最多取 4 个均匀标注）
     * - points=N 时按 N 个槽位布局：数据不足 N 时左对齐、右侧留白（空缺月份等场景）
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

        String type = el.attr("type", "line");
        if ("pie".equals(type)) {
            drawPie(g, el, series);
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

        // 文字区域：标题（顶部）/ 横轴刻度与标签（底部）
        Font small = new Font("SansSerif", Font.PLAIN, 10);
        Font titleFont = new Font("SansSerif", Font.BOLD, 12);
        String title = resolveText(el.attr("title"), data);
        String xLabel = resolveText(el.attr("x-label"), data);
        String yLabel = resolveText(el.attr("y-label"), data);
        String[] xTicks = readLabels(data, el.attr("x-ticks"));
        int topBand = title != null ? 16 : 0;
        int bottomBand = xTicks.length > 0 || xLabel != null ? 13 : 0;

        g.drawRect(x, y, w, h); // 外框
        int plotTop = y + 1 + topBand;
        int plotBottom = y + h - 1 - bottomBand;
        int plotH = Math.max(2, plotBottom - plotTop);
        int plotW = w - 2;

        if (title != null) { // 标题：顶部居中
            g.setFont(titleFont);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, x + Math.max(0, (w - fm.stringWidth(title)) / 2), y + topBand - 4);
        }
        if (yLabel != null) { // 纵轴含义：绘图区左上角
            g.setFont(small);
            g.drawString(yLabel, x + 3, plotTop + 10);
        }

        int count = series.values().iterator().next().length;
        // points=N 槽位布局：数据不足时左对齐、右侧留白
        int slots = points > 0 ? Math.max(points, count) : count;
        int[] xs = new int[count];
        for (int i = 0; i < count; i++) {
            xs[i] = slots <= 1 ? x + 1 : x + 1 + Math.round(i * (float) (plotW - 1) / (slots - 1));
        }
        int[] ys = new int[count];
        for (Map.Entry<String, double[]> se : series.entrySet()) {
            double[] values = se.getValue();
            for (int i = 0; i < values.length; i++) {
                ys[i] = plotTop + (int) Math.round((yMax - values[i]) / (yMax - yMin) * (plotH - 1));
            }
            if ("bar".equals(type)) {
                int barW = Math.max(1, plotW / Math.max(slots, 1) - 1);
                for (int i = 0; i < values.length; i++) {
                    int bx = slots == 1 ? x + 1 : x + 1 + i * plotW / slots;
                    int bh = plotBottom - ys[i];
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

        // 纵轴刻度：右上 y-max、右下 y-min（紧凑数字）
        g.setFont(small);
        FontMetrics sfm = g.getFontMetrics();
        String yMaxS = compact(yMax);
        String yMinS = compact(yMin);
        g.drawString(yMaxS, x + w - 2 - sfm.stringWidth(yMaxS), plotTop + sfm.getAscent() + 1);
        g.drawString(yMinS, x + w - 2 - sfm.stringWidth(yMinS), plotBottom - 2);

        // 横轴：优先刻度标签（均匀取样），否则显示轴含义
        if (xTicks.length > 0) {
            int ticks = Math.min(4, xTicks.length);
            for (int t = 0; t < ticks; t++) {
                int idx = Math.round(t * (float) (Math.min(count, xTicks.length) - 1) / Math.max(ticks - 1, 1));
                if (idx >= count) {
                    continue;
                }
                String label = xTicks[idx];
                int lx = Math.max(x + 1, Math.min(xs[idx] - sfm.stringWidth(label) / 2, x + w - sfm.stringWidth(label) - 1));
                g.drawString(label, lx, y + h - 3);
            }
        } else if (xLabel != null) {
            g.drawString(xLabel, x + w - 2 - sfm.stringWidth(xLabel), y + h - 3);
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

    /**
     * 饼图（type=pie）：所有序列点按总和占比画扇区，1bpp 下用 2° 白色间隔区分相邻扇区。
     * 占比 ≥12% 的扇区标注百分比；数据全为 0/空缺时画外圈 + "无数据"提示。
     * chart x,y,w,h type=pie series=a,b
     */
    private void drawPie(Graphics2D g, DslElement el, Map<String, double[]> series) {
        double total = 0;
        List<Double> values = new ArrayList<>();
        for (double[] s : series.values()) {
            for (double v : s) {
                if (v > 0) {
                    values.add(v);
                    total += v;
                }
            }
        }
        int cx = el.x() + el.getW() / 2;
        int cy = el.y() + el.getH() / 2;
        int r = Math.max(2, Math.min(el.getW(), el.getH()) / 2 - 1);
        g.setColor(BLACK);
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        if (total <= 0 || values.isEmpty()) {
            // 空缺/全零：外圈 + 提示，而不是整块消失
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            FontMetrics fm = g.getFontMetrics();
            String tip = "无数据";
            g.drawString(tip, cx - fm.stringWidth(tip) / 2, cy + fm.getAscent() / 2);
            return;
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        FontMetrics fm = g.getFontMetrics();
        double angle = 0;
        for (double v : values) {
            double sweep = v / total * 360;
            int startDeg = (int) Math.round(angle);
            int sweepDeg = (int) Math.round(sweep);
            if (sweepDeg >= 3) {
                // 留间隔区分扇区
                g.fillArc(cx - r, cy - r, r * 2, r * 2, startDeg + 1, sweepDeg - 2);
            }
            double pct = v / total;
            if (pct >= 0.12) { // 大扇区标注百分比
                double mid = Math.toRadians(angle + sweep / 2);
                int px = cx + (int) Math.round(Math.cos(mid) * r * 0.55);
                int py = cy - (int) Math.round(Math.sin(mid) * r * 0.55);
                String label = Math.round(pct * 100) + "%";
                g.drawString(label, px - fm.stringWidth(label) / 2, py + fm.getAscent() / 2);
            }
            angle += sweep;
        }
    }

    /* ------------------------------------------------------------ 工具 */

    /** 读取数据集中的标签数组（x-ticks=变量路径 → ["2025-01", ...]） */
    private String[] readLabels(Map<String, Object> data, String path) {
        if (path == null || path.isBlank()) {
            return new String[0];
        }
        Object v = lookup(data, path.trim());
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toArray(String[]::new);
        }
        return new String[0];
    }

    /** 紧凑数字：1234567 → 1.2M，12345 → 12.3k，其余取整 */
    private String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) {
            return String.format("%.1fM", v / 1_000_000);
        }
        if (a >= 1_000) {
            return String.format("%.1fk", v / 1_000);
        }
        return String.valueOf(Math.round(v));
    }

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

    /* ------------------------------------------------------------ 位图定义方案编码 */

    /**
     * 规范色彩索引 → 目标单片机位图：
     * 每像素按其颜色的编码（code）写入 bits 位（1-4），行优先紧缩，行宽补齐到整字节；
     * bitOrder 决定字节内位序（msb/lsb），像素可能跨字节。
     */
    private byte[] packCodes(int[] idx, int w, int h, BitmapEncoding enc) {
        int[] codes = {
                BitmapEncoding.parseCode(enc.getBlack()),
                BitmapEncoding.parseCode(enc.getWhite()),
                BitmapEncoding.parseCode(enc.getRed()),
                BitmapEncoding.parseCode(enc.getYellow()),
                BitmapEncoding.parseCode(enc.getBlue()),
                BitmapEncoding.parseCode(enc.getGreen())
        };
        // 黑白编码兑底（parseEncoding 已保证非负，这里防御）
        if (codes[C_BLACK] < 0) codes[C_BLACK] = 1;
        if (codes[C_WHITE] < 0) codes[C_WHITE] = 0;

        // 位宽已由 resolveBits 确定并写入 enc
        int bits = enc.bitsOr1();

        int stride = (w * bits + 7) / 8;
        byte[] out = new byte[stride * h];
        boolean lsb = enc.isLsb();
        int mask = (1 << bits) - 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int code = codes[idx[y * w + x]] & mask;
                int off = (x * bits) % 8;             // 字节内位偏移
                int byteIdx = y * stride + (x * bits) / 8;
                if (lsb) {
                    if (off + bits <= 8) {
                        out[byteIdx] |= (byte) (code << off);
                    } else {
                        int first = 8 - off;
                        out[byteIdx] |= (byte) ((code & ((1 << first) - 1)) << off);
                        out[byteIdx + 1] |= (byte) (code >>> first);
                    }
                } else {
                    if (off + bits <= 8) {
                        out[byteIdx] |= (byte) (code << (8 - off - bits));
                    } else {
                        int first = 8 - off;
                        out[byteIdx] |= (byte) (code >>> (bits - first));
                        int rest = bits - first;
                        out[byteIdx + 1] |= (byte) ((code & ((1 << rest) - 1)) << (8 - rest));
                    }
                }
            }
        }
        return out;
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
