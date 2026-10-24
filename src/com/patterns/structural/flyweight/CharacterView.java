package com.patterns.structural.flyweight;

/**
 * 上下文对象：持有<b>外部状态</b>，并把它与享元组合起来完成一次渲染。
 *
 * <p><b>为什么需要这个类？（这是享元模式最容易被忽略的角色）</b>
 * 享元本身只存内部状态（symbol、font），它是"无位置、无颜色"的抽象字形。
 * 但屏幕上每个具体的字符都必然有位置和颜色，这些数据总得有地方放。
 * {@code CharacterView} 就是那个地方——它是<b>不共享的</b>，每个位置一个实例，
 * 但它非常"轻"：只有一个享元引用（8 字节）+ 两个 int + 一个颜色引用。
 *
 * <p><b>共享与不共享的分工</b>：
 * <pre>
 *   重的部分（字体轮廓、度量数据）→ 放进享元，全屏幕共享 1 份
 *   轻的部分（坐标、颜色）        → 放进 CharacterView，每个字符 1 份
 * </pre>
 * 这就是享元模式的算术：假设字形数据 10KB、上下文数据 30 字节，
 * 渲染 10000 个字符：
 * <ul>
 *   <li>不用享元：10000 × (10KB + 30B) ≈ <b>100 MB</b></li>
 *   <li>用享元：26 × 10KB + 10000 × 30B ≈ <b>0.55 MB</b></li>
 * </ul>
 *
 * <p>GoF 把这种角色称为 <b>UnsharedConcreteFlyweight</b> 或"外部状态持有者"，
 * 它不属于享元池，也不实现享元接口（因为它带有不可共享的状态）。
 *
 * @author design-patterns-pro
 */
public class CharacterView {

    /** 指向共享的享元。多个 CharacterView 的这个字段可以指向同一个对象。 */
    private final Glyph glyph;

    /** 外部状态：横坐标 */
    private final int x;

    /** 外部状态：纵坐标 */
    private final int y;

    /** 外部状态：颜色 */
    private final String color;

    /**
     * @param glyph 从 {@link GlyphFactory} 取得的共享享元
     * @param x     横坐标
     * @param y     纵坐标
     * @param color 颜色
     */
    public CharacterView(Glyph glyph, int x, int y, String color) {
        this.glyph = glyph;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    /**
     * 绘制：把自己的外部状态<b>传给</b>享元，由享元结合内部状态完成渲染。
     *
     * <p>注意方向：外部状态是"从外向内传"，不是"存进享元"。
     * 一旦把 x/y/color 存进享元字段，共享就被破坏了。
     */
    public void draw() {
        glyph.render(x, y, color);
    }
}
