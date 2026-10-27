package com.patterns.structural.flyweight.beginner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 享元入门演示：一屏 10000 个字符，只有 3 种字体样式。
 *
 * <p>改造前：每个字符都 new 一份样式，10000 个字符 = 10000 个重复的样式对象；
 * 改造后：样式按「字体+字号+颜色」共享，字符只保存坐标和对共享样式的引用。
 */
public class BeginnerDemo {

    private static final int GLYPH_COUNT = 10000;
    private static final String SAMPLE = "订单已发货";

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：每个字符一份样式 ==========");

        List<OldGlyph> glyphs = new ArrayList<>();
        for (int i = 0; i < GLYPH_COUNT; i++) {
            char ch = SAMPLE.charAt(i % SAMPLE.length());
            int x = i % 80;
            int y = i / 80;
            OldStyle style = createStyle(i % 3);      // 每次都 new
            glyphs.add(new OldGlyph(ch, style, x, y));
        }

        System.out.println("  字符数：" + glyphs.size());
        System.out.println("  样式对象数：" + OldStyle.created);
        System.out.println("  合计对象数：" + (glyphs.size() + OldStyle.created));
        System.out.println("  第 1 个和第 4 个字符的样式是同一个对象吗？"
                + (glyphs.get(0).style == glyphs.get(3).style));

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 10000 个字符创建了 10000 个样式对象，其中只有 3 种不同内容；");
        System.out.println("  2. 字符越多，重复样式越多，内存里全是同样的字体/字号/颜色；");
        System.out.println("  3. 样式信息跟着每个字符重复存了一份，改字体要遍历所有字符逐个改。");
    }

    private static OldStyle createStyle(int kind) {
        if (kind == 0) {
            return new OldStyle("宋体", 12, "黑");
        }
        if (kind == 1) {
            return new OldStyle("宋体", 14, "红");
        }
        return new OldStyle("黑体", 12, "蓝");
    }

    static class OldStyle {

        static int created;

        final String font;
        final int fontSize;
        final String color;

        OldStyle(String font, int fontSize, String color) {
            this.font = font;
            this.fontSize = fontSize;
            this.color = color;
            created++;
        }
    }

    static class OldGlyph {

        final char ch;
        final OldStyle style;
        final int x;
        final int y;

        OldGlyph(char ch, OldStyle style, int x, int y) {
            this.ch = ch;
            this.style = style;
            this.x = x;
            this.y = y;
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：样式按 key 共享 ==========");

        List<Glyph> glyphs = new ArrayList<>();
        for (int i = 0; i < GLYPH_COUNT; i++) {
            char ch = SAMPLE.charAt(i % SAMPLE.length());
            int x = i % 80;
            int y = i / 80;
            TextStyle style = StyleFactory.get(i % 3);   // 同 key 返回同一个对象
            glyphs.add(new Glyph(ch, style, x, y));
        }

        System.out.println("  字符数：" + glyphs.size());
        System.out.println("  样式对象数：" + StyleFactory.created);
        System.out.println("  合计对象数：" + (glyphs.size() + StyleFactory.created));
        System.out.println("  第 1 个和第 4 个字符的样式是同一个对象吗？"
                + (glyphs.get(0).style == glyphs.get(3).style));

        System.out.println();
        System.out.println("  共享的 3 个样式：");
        StyleFactory.printAll();

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 样式必须不可变，否则一个字符改样式会影响所有共享它的字符；");
        System.out.println("  2. 多了一个工厂和 Map 查找，字符很少时反而更慢；");
        System.out.println("  3. 缓存的生命周期要管理，长期不用的样式需要有清理策略。");
    }

    /** 享元：只保存「内在状态」——可以共享的字体/字号/颜色，全部 final。 */
    static class TextStyle {

        private final String font;
        private final int fontSize;
        private final String color;

        TextStyle(String font, int fontSize, String color) {
            this.font = font;
            this.fontSize = fontSize;
            this.color = color;
        }

        @Override
        public String toString() {
            return font + " " + fontSize + "px " + color;
        }
    }

    /**
     * 享元工厂：相同 key 只创建一次。
     *
     * <p>入门示例用静态工厂简化；真实项目里它通常是一个实例成员，
     * 生命周期由使用它的对象管理。
     */
    static class StyleFactory {

        static int created;
        private static final Map<String, TextStyle> CACHE = new HashMap<>();

        static TextStyle get(int kind) {
            String font;
            int size;
            String color;
            if (kind == 0) {
                font = "宋体";
                size = 12;
                color = "黑";
            } else if (kind == 1) {
                font = "宋体";
                size = 14;
                color = "红";
            } else {
                font = "黑体";
                size = 12;
                color = "蓝";
            }

            String key = font + "|" + size + "|" + color;
            TextStyle style = CACHE.get(key);
            if (style == null) {
                style = new TextStyle(font, size, color);
                CACHE.put(key, style);
                created++;
            }
            return style;
        }

        static void printAll() {
            for (Map.Entry<String, TextStyle> entry : CACHE.entrySet()) {
                System.out.println("    " + entry.getKey() + " -> " + entry.getValue());
            }
        }
    }

    /** 字符：保存「外在状态」坐标 + 对共享样式的引用。 */
    static class Glyph {

        private final char ch;
        private final TextStyle style;
        private final int x;
        private final int y;

        Glyph(char ch, TextStyle style, int x, int y) {
            this.ch = ch;
            this.style = style;
            this.x = x;
            this.y = y;
        }
    }
}
