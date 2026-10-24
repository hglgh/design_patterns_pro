package com.patterns.structural.flyweight;

/**
 * 享元接口（Flyweight）：定义"字符字形"的行为。
 *
 * <p><b>享元模式的命脉在这个方法签名上。</b>
 *
 * <pre>
 *   void render(int x, int y, String color);
 *                 ↑─────────────────────↑
 *                 这些是「外部状态」，必须由调用方每次传进来
 * </pre>
 *
 * 而字符本身是什么（{@code symbol}）、用什么字体（{@code font}）是「内部状态」，
 * 保存在享元对象里，<b>所有使用者共享同一份</b>。
 *
 * <p><b>为什么必须这样切分？</b>
 * 因为一个字符 'a' 可以在屏幕上的任意位置、以任意颜色出现无数次。
 * 如果把 x/y/color 存进对象，那每个位置每种颜色都要一个独立的 'a' 对象，
 * 共享就无从谈起。只有把"随场景变化的部分"挪到方法参数（外部状态），
 * "恒定不变的部分"留在对象里（内部状态），一个 'a' 对象才能服务所有位置。
 *
 * <p><b>判断内部/外部状态的口诀</b>：
 * 「这个东西会不会因为使用场景不同而不同？」
 * 会 → 外部状态（传参）；不会 → 内部状态（存字段）。
 *
 * @author design-patterns-pro
 * @see CharacterGlyph 具体享元
 * @see CharacterView  持有外部状态的上下文对象
 */
public interface Glyph {

    /**
     * 在指定位置以指定颜色渲染本字形。
     *
     * @param x     横坐标（外部状态）
     * @param y     纵坐标（外部状态）
     * @param color 颜色（外部状态）
     */
    void render(int x, int y, String color);
}
