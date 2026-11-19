package cc.bexerlmao.gazette.module.device.render;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个动态元素（DSL 中的一行）。
 * 语法：<type> <x>,<y>[,<w>,<h>] [attr=value ...] ["文本/载荷"]
 * 详见 docs/dynamic-content-design.md
 */
@Data
public class DslElement {

    private String type;

    /** 坐标序列（x,y 成对；line 为 x1,y1,x2,y2；polyline 为多点） */
    private List<Integer> coords = new ArrayList<>();

    /** 区域宽高（text/chart/icon/qr 等使用） */
    private Integer w;
    private Integer h;

    /** 属性键值对，如 font=2、series=a,b */
    private Map<String, String> attrs = new LinkedHashMap<>();

    /** 引号内的文本/载荷（含 {变量} 占位符） */
    private String text;

    /** 原始行（调试/报错用） */
    private String raw;

    public int x() {
        return coords.isEmpty() ? 0 : coords.get(0);
    }

    public int y() {
        return coords.size() < 2 ? 0 : coords.get(1);
    }

    public String attr(String key) {
        return attrs.get(key);
    }

    public String attr(String key, String def) {
        return attrs.getOrDefault(key, def);
    }
}
