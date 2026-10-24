package com.patterns.structural.flyweight;

/**
 * 具体享元（ConcreteFlyweight）：一个字符的字形。
 *
 * <p><b>内部状态</b>：{@code symbol}（哪个字符）+ {@code font}（什么字体）。
 * 这两个值一旦确定就永不改变，且与"渲染在哪里、什么颜色"完全无关，
 * 因此可以被所有使用场合共享。
 *
 * <p><b>为什么用 record？</b>
 * record 天生不可变（所有组件都是 final 私有字段，没有 setter）。
 * <b>不可变性是享元能被安全共享的前提</b>——如果某个调用方能修改享元的字段，
 * 所有共享它的调用方都会莫名其妙地看到数据变了，这种 bug 几乎无法排查。
 * 用 record 等于让编译器帮你保证"没人能改它"。
 *
 * <p><b>真实系统中这个对象有多重？</b>
 * 本 Demo 只存了 char + String 两个字段，看起来"共享了也没省多少"。
 * 但在真实的文字渲染引擎里，一个 Glyph 通常要持有：
 * <ul>
 *   <li>字体的贝塞尔曲线轮廓点集（几百到几千个坐标）</li>
 *   <li>字体度量信息（ascent / descent / advance width / kerning 表）</li>
 *   <li>预渲染的位图缓存（一个 32px 字符约 1KB）</li>
 * </ul>
 * 单个 Glyph 轻松达到几 KB 到几十 KB。一篇 10 万字的文章如果每个字符都建一个 Glyph，
 * 光字形数据就要几百 MB；共享之后只需几十种字符 × 几 KB = 几百 KB。
 * <b>这才是享元模式的真正价值所在。</b>
 *
 * @param symbol 字符（内部状态）
 * @param font   字体名（内部状态）
 * @author design-patterns-pro
 */
public record CharacterGlyph(char symbol, String font) implements Glyph {

    /**
     * 用享元的<b>内部状态</b>（symbol、font）+ 调用方传入的<b>外部状态</b>（x、y、color）
     * 共同完成一次渲染。
     *
     * <p><b>这一行代码就是享元模式的全部机制</b>：
     * 内部状态来自 this（共享），外部状态来自参数（每次不同）。
     * 方法本身不修改任何字段，因此天然线程安全、可并发调用。
     */
    @Override
    public void render(int x, int y, String color) {
        System.out.printf("    渲染 '%c' | 字体=%-6s | 位置=(%3d,%3d) | 颜色=%s%n",
                symbol, font, x, y, color);
    }
}
