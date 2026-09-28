package com.fitpilot.util;

/** JSON 相关小工具 */
public final class JsonUtils {

    private JsonUtils() {}

    /** 去掉 LLM 回复中可能包裹的 ```json ... ``` 或 ``` ... ``` 代码块标记 */
    public static String stripCodeFences(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.startsWith("```")) {
            // 去掉第一行的 ```json / ```plan 等
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) {
                t = t.substring(firstNewline + 1);
            } else {
                return t.replaceAll("`", "").trim();
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        // 兜底：截取第一个 { 到最后一个 } 之间
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t.trim();
    }
}
