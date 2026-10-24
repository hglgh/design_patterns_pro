package com.patterns.structural.flyweight;

import java.util.List;

/**
 * 客户端：验证享元的共享效果。
 *
 * <p>运行方式：
 * <pre>
 *   java -cp out com.patterns.structural.flyweight.Client
 * </pre>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demo1SharedIdentity();
        demo2ExternalState();
        demo3InternalStateKey();
        demo4ScaleEffect();
    }

    /**
     * 演示 1：相同内部状态 → 同一个对象引用（共享的直接证据）。
     */
    private static void demo1SharedIdentity() {
        System.out.println("=== 1. 共享验证：相同内部状态返回同一对象 ===");

        GlyphFactory factory = new GlyphFactory();

        Glyph a1 = factory.getGlyph('a', "Arial");
        Glyph b1 = factory.getGlyph('b', "Arial");
        Glyph a2 = factory.getGlyph('a', "Arial");   // 第二次请求 'a'

        System.out.println("    a1 == a2 ? " + (a1 == a2) + "   ← true 说明是同一个对象，不是内容相等的副本");
        System.out.println("    a1 == b1 ? " + (a1 == b1) + "   ← 不同字符当然是不同对象");
        System.out.println("    a1.equals(a2) ? " + a1.equals(a2) + "  ← record 的 equals 也成立，但 == 才是共享的证据");
        System.out.println("    请求 " + factory.getRequestCount() + " 次，实际创建 "
                + factory.getCreationCount() + " 个，节省 " + factory.getSavedCount() + " 个对象");
        System.out.println();
    }

    /**
     * 演示 2：外部状态由调用方传入，同一个享元可服务于不同位置和颜色。
     */
    private static void demo2ExternalState() {
        System.out.println("=== 2. 外部状态：同一个 'a' 渲染在不同位置、不同颜色 ===");

        GlyphFactory factory = new GlyphFactory();

        // 保留原项目的示例：5 个字符视图，但只有 2 个享元
        CharacterView[] page = {
                new CharacterView(factory.getGlyph('a', "Arial"), 10, 10, "black"),
                new CharacterView(factory.getGlyph('b', "Arial"), 22, 10, "black"),
                new CharacterView(factory.getGlyph('a', "Arial"), 34, 10, "red"),
                new CharacterView(factory.getGlyph('a', "Arial"), 46, 10, "blue"),
                new CharacterView(factory.getGlyph('b', "Arial"), 58, 10, "black"),
        };

        System.out.println("    -- 逐个绘制（注意 3 个 'a' 位置颜色各不同，但用的是同一个享元对象）--");
        for (CharacterView cv : page) {
            cv.draw();
        }

        System.out.println("    -- 统计 --");
        System.out.println("    CharacterView 对象数 = " + page.length + "（不共享，每个位置一个）");
        System.out.println("    Glyph 享元对象数     = " + factory.getPoolSize() + "（共享，池内 key: "
                + factory.getPooledKeys() + "）");
        System.out.println();
    }

    /**
     * 演示 3：字体也是内部状态的一部分，所以 ('a', Arial) 与 ('a', 宋体) 是两个享元。
     *
     * <p>这是享元工厂 key 设计的要点：<b>key 必须覆盖全部内部状态</b>。
     */
    private static void demo3InternalStateKey() {
        System.out.println("=== 3. 内部状态决定池的 key：同字符不同字体是两个享元 ===");

        GlyphFactory factory = new GlyphFactory();

        Glyph aArial = factory.getGlyph('a', "Arial");
        Glyph aSong = factory.getGlyph('a', "SimSun");
        Glyph aArialAgain = factory.getGlyph('a', "Arial");

        System.out.println("    ('a',Arial) == ('a',SimSun) ? " + (aArial == aSong)
                + "   ← false：字体不同，不能共享");
        System.out.println("    ('a',Arial) == ('a',Arial)  ? " + (aArial == aArialAgain)
                + "    ← true：内部状态完全相同，共享");
        System.out.println("    池内容：" + factory.getPooledKeys());
        System.out.println();
        System.out.println("    若工厂只用 symbol 作 key（忽略 font），第二个调用会拿到 Arial 的字形，");
        System.out.println("    渲染出错误的字体——这是享元模式最典型的实现错误。");
        System.out.println();
    }

    /**
     * 演示 4：规模效应——长文本只产生少量享元。
     */
    private static void demo4ScaleEffect() {
        System.out.println("=== 4. 规模效应：渲染一整段文本 ===");

        GlyphFactory factory = new GlyphFactory();

        String paragraph = "the quick brown fox jumps over the lazy dog "
                + "the quick brown fox jumps over the lazy dog "
                + "the quick brown fox jumps over the lazy dog";
        TextDocument doc = new TextDocument(paragraph, "Arial", factory);

        List<CharacterView> views = doc.layout(0, 100, "black");

        System.out.println("    文本长度            = " + doc.length() + " 个字符");
        System.out.println("    生成的 View 对象数  = " + views.size() + "（空格不生成字形）");
        System.out.println("    实际创建的享元数    = " + factory.getCreationCount());
        System.out.println("    向工厂请求的次数    = " + factory.getRequestCount());
        System.out.println("    节省的对象数        = " + factory.getSavedCount());
        System.out.println("    池中共享的字形      = " + factory.getPooledKeys());

        double ratio = 100.0 * factory.getSavedCount() / factory.getRequestCount();
        System.out.printf("    共享命中率          = %.1f%%%n", ratio);
        System.out.println();
        System.out.println("    结论：一段 " + doc.length() + " 字的文本，只需要 "
                + factory.getPoolSize() + " 个字形对象。");
        System.out.println("    真实渲染引擎中每个字形含轮廓点集与位图缓存（数 KB 到数十 KB），");
        System.out.println("    这里的节省量会被放大成千上万倍——这才是享元模式存在的理由。");
        System.out.println();

        // 顺带验证：不同字体是另一批享元
        int poolSizeBefore = factory.getPoolSize();
        TextDocument songDoc = new TextDocument("你好世界你好", "SimSun", factory);
        List<CharacterView> songViews = songDoc.layout(0, 200, "red");
        System.out.println("    再渲染一段宋体中文：" + songDoc.getText());
        System.out.println("    View 数 = " + songViews.size() + "，池大小从 "
                + poolSizeBefore + " 增长到 " + factory.getPoolSize());
        System.out.println("    中文 6 个字里 '你' 和 '好' 各重复一次，所以只新建了 "
                + (factory.getPoolSize() - poolSizeBefore) + " 个享元");
    }
}
