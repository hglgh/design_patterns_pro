package com.patterns.structural.flyweight;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本文档：把一段字符串排版成一串 {@link CharacterView}。
 *
 * <p><b>这个类是"真实业务如何使用享元"的示范。</b>
 * 原项目只在 Client 里手写了 5 个 CharacterView，看不出享元的规模效应。
 * 这里改成"给一段文本，自动为每个字符取享元并排版"，
 * 于是可以直观看到：<b>一段 200 字的文本，池里只多了十几个字形对象</b>。
 *
 * <p>注意本类<b>不持有任何 Glyph 字段的副本</b>，它只在排版时向工厂索取，
 * 索取到的引用直接交给 CharacterView 保管。工厂才是享元的唯一权威来源。
 *
 * @author design-patterns-pro
 */
public class TextDocument {

    /** 每个字符的水平间距（像素） */
    private static final int CHAR_WIDTH = 12;

    private final String text;
    private final String font;
    private final GlyphFactory factory;

    /**
     * @param text    文档内容
     * @param font    全文使用的字体（内部状态的一部分）
     * @param factory 享元工厂，必须由外部注入以保证全局共享同一个池
     */
    public TextDocument(String text, String font, GlyphFactory factory) {
        this.text = text;
        this.font = font;
        this.factory = factory;
    }

    /**
     * 排版：为文本中的每个字符生成一个持有外部状态的视图对象。
     *
     * <p><b>关键在这一行</b>：{@code factory.getGlyph(ch, font)}。
     * 无论文本有多长、某个字符重复出现多少次，工厂对相同的 (字符, 字体)
     * 组合永远返回<b>同一个对象</b>。
     *
     * @param startX 起始横坐标
     * @param startY 纵坐标
     * @param color  颜色（外部状态，所有字符共用）
     * @return 排版结果，每个元素对应文本中的一个字符
     */
    public List<CharacterView> layout(int startX, int startY, String color) {
        List<CharacterView> views = new ArrayList<>(text.length());
        int x = startX;
        for (char ch : text.toCharArray()) {
            if (ch == ' ') {
                x += CHAR_WIDTH;      // 空格不生成字形，只推进光标
                continue;
            }
            Glyph glyph = factory.getGlyph(ch, font);
            views.add(new CharacterView(glyph, x, startY, color));
            x += CHAR_WIDTH;
        }
        return views;
    }

    /**
     * @return 文本内容
     */
    public String getText() {
        return text;
    }

    /**
     * @return 文本长度
     */
    public int length() {
        return text.length();
    }
}
