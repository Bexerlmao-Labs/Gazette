package cc.bexerlmao.gazette.module.device.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 行式 DSL 解析器。
 * 一行一个元素，'#' 开头为注释，空行忽略；引号内可含空格与 {变量} 占位符。
 */
public final class DslParser {

    private DslParser() {
    }

    public static List<DslElement> parse(String dsl) {
        List<DslElement> elements = new ArrayList<>();
        if (dsl == null || dsl.isBlank()) {
            return elements;
        }
        for (String line : dsl.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            DslElement el = parseLine(trimmed);
            if (el != null) {
                elements.add(el);
            }
        }
        return elements;
    }

    private static DslElement parseLine(String line) {
        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            return null;
        }
        DslElement el = new DslElement();
        el.setRaw(line);
        el.setType(tokens.get(0).toLowerCase());

        boolean firstCoord = true;
        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("\"")) {
                el.setText(unescape(t.substring(1, t.length() - 1)));
            } else if (t.contains("=")) {
                int eq = t.indexOf('=');
                el.getAttrs().put(t.substring(0, eq), unescape(t.substring(eq + 1)));
            } else if (isCoord(t)) {
                for (String v : t.split(",")) {
                    el.getCoords().add(Integer.parseInt(v));
                }
                if (firstCoord && el.getCoords().size() == 4) {
                    // x,y,w,h 形式：后两位作为宽高
                    el.setW(el.getCoords().get(2));
                    el.setH(el.getCoords().get(3));
                }
                firstCoord = false;
            }
        }
        return el;
    }

    private static boolean isCoord(String t) {
        return t.matches("\\d+,\\d+(,\\d+(,\\d+)*)*");
    }

    /** 按空白切分，双引号内的空白不切分；等号后跟引号时合并为一个 token。 */
    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            StringBuilder sb = new StringBuilder();
            boolean quoted = false;
            boolean quotedAttr = false;
            if (c == '"') {
                quoted = true;
                sb.append('"');
                i++;
            }
            while (i < n) {
                c = line.charAt(i);
                if (c == '\\' && i + 1 < n) {
                    sb.append(line.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"' && (quoted || sb.indexOf("=") >= 0 && !quotedAttr)) {
                    if (sb.indexOf("=") >= 0) {
                        quotedAttr = true;
                    } else {
                        quoted = false;
                    }
                    sb.append('"');
                    i++;
                    if (!quotedAttr) {
                        break;
                    }
                    continue;
                }
                if (!quoted && !quotedAttr && Character.isWhitespace(c)) {
                    break;
                }
                sb.append(c);
                i++;
            }
            tokens.add(sb.toString());
        }
        return tokens;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
