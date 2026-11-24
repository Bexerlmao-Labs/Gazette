import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 点阵字库生成工具（一次性运行，产物提交到 module/device/src/main/resources/font/pixel-font.json）。
 *
 * 输出 JSON：{ "size": 16, "glyphs": { "室": ["....", ...16 行], "A": [...10 宽 16 行] } }
 * - CJK / 全角：16×16 点阵
 * - ASCII 半角：10×16 点阵（等宽）
 *
 * 每个字形先用 96px 高清渲染（抗锯齿），再按块平均亮度阈值降采样为点阵。
 */
public final class FontGen {

    private static final int GRID = 16;      // 点阵行数
    private static final int CJK_W = 16;     // 全角宽
    private static final int ASCII_W = 10;   // 半角宽
    private static final int SS = 96;        // 超采样渲染尺寸

    public static void main(String[] args) throws IOException {
        String charset = """
                !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~°℃
                室内温度湿度晴阴雨雪多云雾霾风
                今天日期星期一二三四五六日年末初终
                年月时分秒毫微
                电池电量充放
                图表例最高低更
                上传下载导入
                蓝牙连接断开配对
                设置参数选项
                照片画布组件库创建预览
                位图文本时间数据源
                已存在云端模式离线推送
                屏幕尺寸消息提示成功失败
                历史未来周报告总计平平均值
                步骤当前目标系统网络状态
                新删除编辑保存取消确
                房间书房客厅卧室厨房阳台
                站外天气回复待办事项清单
                欢迎回来你好世界
                墨水屏电子纸像素
                树莓派开发板硬件
                入侵检测报警安全
                门铃提醒备忘录
                倒计时计划日程表
                勿扰专注
                喝水运动健身
                拿铁咖啡因摄入
                习惯打卡连续天数
                证机设备器名称
                湿噪声音量
                空气质量指数
                二氧化碳浓度
                甲醛TVOC
                气压海拔
                日出日落
                月相潮汐
                农历节节气
                生肖星座运势
                股票基金汇率
                比特币以太坊
                服务器机房监控
                访客计数器
                投票统计结果
                排行榜名次
                游戏得分等级
                生命值魔法攻击防御
                经验金币掉落
                地图导航方向北南东西
                距离速度里程
                卡路里脂肪蛋白质碳水
                血压心率血氧
                睡眠深浅快速眼动
                体重身高体质
                视力矫正度数
                背单词学习进度
                课程班级教师
                作业考试复习
                成绩分数排名
                图书借阅归还
                快递物流取件码
                优惠券折扣满减
                订单支付金额
                余额账单支出收入
                预算储蓄理财
                通勤班车地铁公交
                限行尾号加油
                停车位空余
                充电桩空闲
                排队叫号等待
                医院挂号诊室
                药品剂量服用
                宠物喂食铲屎
                植物浇水施肥
                鱼缸水温换
                天气预报降水概率
                紫外线指数防晒
                穿衣建议洗车
                钓鱼指数潮
                日程会议提醒开始结束
                生日纪念日祝福
                垃圾分类回收
                节能环保低碳
                智能家居开关灯空调
                传感器湿度温
                固件版本更新
                错误警告重试
                加载中请稍后
                欢迎使用
                感谢观看订阅
                扫码获取更多
                二维码内容链接地址
                网址邮箱电话
                地址邮编
                姓名职务部门
                签名盖章有效
                证书编号有效期
                会员等级积分
                生日快乐节日
                新年春节中秋端午国庆
                圣诞万圣节复活
                情人节玫瑰爱
                母亲父亲教师节
                儿童六一周
                青年妇女劳动
                建军建党史
                元旦除夕守岁
                正月初一到十五
                腊月廿三十
                甲乙丙丁戊己庚辛壬癸
                子丑寅卯辰巳午未申酉戌亥
                鼠牛虎兔龙蛇马羊猴鸡狗猪
                """;
        Set<Character> chars = new LinkedHashSet<>();
        charset.chars().filter(c -> !Character.isWhitespace(c)).forEach(c -> chars.add((char) c));

        // 字体：中文用微软雅黑，ASCII 用 Consolas（不可用则回退逻辑字体）
        Font cjkFont = pickFont("SimHei", "Microsoft YaHei");
        Font asciiFont = pickFont("Consolas", "Monospaced");

        // 按字体度量构造渲染包围盒：字符画在 box 内、基线对齐，再整体降采样
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();

        Font asciiSized = asciiFont.deriveFont(Font.BOLD, (float) SS);
        FontMetrics am = pg.getFontMetrics(asciiSized);
        int asciiBoxW = am.getMaxAdvance();
        int asciiBoxH = am.getAscent() + am.getDescent();
        int asciiBaseline = am.getAscent();

        Font cjkSized = cjkFont.deriveFont(Font.BOLD, (float) SS);
        FontMetrics cm = pg.getFontMetrics(cjkSized);
        int cjkBoxW = Math.max(cm.stringWidth("国"), cm.stringWidth("室"));
        int cjkBoxH = cm.getAscent() + cm.getDescent();
        int cjkBaseline = cm.getAscent();
        pg.dispose();

        StringBuilder json = new StringBuilder(1 << 20);
        json.append("{\"size\":").append(GRID).append(",\"glyphs\":{");
        boolean first = true;
        for (char ch : chars) {
            boolean ascii = ch < 128;
            String[] grid = rasterize(String.valueOf(ch), ascii ? asciiSized : cjkSized,
                    ascii ? ASCII_W : CJK_W, GRID,
                    ascii ? asciiBoxW : cjkBoxW, ascii ? asciiBoxH : cjkBoxH,
                    ascii ? asciiBaseline : cjkBaseline);
            if (grid == null) {
                System.err.println("跳过（无法渲染）: " + ch);
                continue;
            }
            if (!first) json.append(',');
            first = false;
            json.append(quote(String.valueOf(ch))).append(":[");
            for (int i = 0; i < grid.length; i++) {
                if (i > 0) json.append(',');
                json.append(quote(grid[i]));
            }
            json.append(']');
        }
        json.append("}}");

        Path out = Paths.get(args.length > 0 ? args[0] : "pixel-font.json");
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
        System.out.println("字库已生成: " + out.toAbsolutePath() + " (" + chars.size() + " 字符, "
                + Files.size(out) / 1024 + " KB)");
    }

    private static Font pickFont(String preferred, String fallback) {
        for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (name.equalsIgnoreCase(preferred)) {
                return new Font(name, Font.BOLD, 100);
            }
        }
        return new Font(fallback, Font.BOLD, 100);
    }

    /** 字符画在 boxW×boxH 包围盒内（基线 baseline），再降采样为 gw×gh 点阵；完全空白返回 null */
    private static String[] rasterize(String ch, Font font, int gw, int gh, int boxW, int boxH, int baseline) {
        BufferedImage img = new BufferedImage(boxW, boxH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.BLACK);
        g.drawString(ch, 0, baseline);
        g.dispose();

        String[] rows = new String[gh];
        boolean any = false;
        for (int gy = 0; gy < gh; gy++) {
            StringBuilder row = new StringBuilder(gw);
            for (int gx = 0; gx < gw; gx++) {
                long sum = 0;
                int x0 = gx * boxW / gw, x1 = (gx + 1) * boxW / gw;
                int y0 = gy * boxH / gh, y1 = (gy + 1) * boxH / gh;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        sum += (img.getRGB(x, y) >>> 24);
                    }
                }
                int avg = (int) (sum / ((x1 - x0) * (y1 - y0)));
                boolean dot = avg > 85;
                any |= dot;
                row.append(dot ? '#' : '.');
            }
            rows[gy] = row.toString();
        }
        return any ? rows : null;
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        return sb.append('"').toString();
    }
}
