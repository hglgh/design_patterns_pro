package com.patterns.behavioral.memento;

/**
 * 选区：一段半开区间 {@code [start, end)}。
 *
 * <p><b>为什么不用两个 {@code int}？</b>
 * 原项目的备忘录只存 {@code content}，所以根本没有选区这个概念（探针 P5）。
 * 一旦要把光标和选区也纳入快照，最直接的写法是给方法加两个参数：
 *
 * <pre>
 *   void select(int start, int end);
 *   Memento(String text, int cursor, int start, int end);
 * </pre>
 *
 * 于是 {@code start} 和 {@code end} 这两个 {@code int} 会一路穿过
 * 编辑器、快照、历史三层。这种"参数对"的问题是：
 * <ul>
 *   <li><b>顺序可以传反</b>：{@code select(8, 3)} 编译通过，运行时才可能炸；</li>
 *   <li><b>校验要写很多遍</b>：每一层都得重新检查 {@code start >= 0 && end >= start}；</li>
 *   <li><b>没法表达"不含选区"</b>：只能靠调用方约定 {@code start == end}。</li>
 * </ul>
 *
 * <p>做成 record 之后，这三种问题一次性消失：
 * 紧凑构造器把校验收在<b>一处</b>，任何拿到 {@code TextRegion} 的代码
 * 都可以假定它合法；{@link #caret(int)} 把"只是一个光标"这个常见情形
 * 明确命名出来，读代码的人不用再猜 {@code start == end} 是什么意思。
 *
 * <p><b>为什么是半开区间？</b>
 * 与 {@link String#substring(int, int)} 保持一致。{@code start} 指向第一个字符，
 * {@code end} 指向<b>最后一个字符的下一个位置</b>。这样 {@link #length()}
 * 就是 {@code end - start}，不需要 {@code +1}，也不会出现"空选区的 end 是多少"的歧义。
 *
 * @param start 起始下标（含）
 * @param end   结束下标（不含）
 * @author design-patterns-pro
 */
public record TextRegion(int start, int end) {

    /**
     * 紧凑构造器：下标必须非负且有序。
     *
     * @throws IllegalArgumentException {@code start < 0} 或 {@code end < start} 时
     */
    public TextRegion {
        if (start < 0) {
            throw new IllegalArgumentException("start 不能为负，实际 " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException(
                    "end(" + end + ") 不能小于 start(" + start + ")");
        }
    }

    /**
     * 一个"什么都没有选中"的选区，只有光标。
     *
     * @param position 光标位置，必须非负
     * @return 起止相同的选区
     * @throws IllegalArgumentException {@code position < 0} 时
     */
    public static TextRegion caret(int position) {
        return new TextRegion(position, position);
    }

    /**
     * 选区覆盖的字符数。
     *
     * @return {@code end - start}，非负
     */
    public int length() {
        return end - start;
    }

    /**
     * 是否只有光标、没有选中任何字符。
     *
     * @return {@code start == end} 时为 {@code true}
     */
    public boolean isEmpty() {
        return start == end;
    }

    /**
     * 把选区整体右移，用于"在选区前插入了一段文本之后"的重定位。
     *
     * <p>返回新对象而不是原地修改，因为 record 不可变；
     * 调用方拿到的一定是一个合法的 {@code TextRegion}。
     *
     * @param delta 位移量，可为负
     * @return 平移后的选区
     * @throws IllegalArgumentException 平移后起点为负时
     */
    public TextRegion shift(int delta) {
        return new TextRegion(start + delta, end + delta);
    }

    /**
     * 紧凑格式：{@code [2,5)} 表示选中下标 2、3、4 三个字符。
     *
     * <p><b>为什么重写 record 的默认 {@code toString()}？</b>
     * 默认版本是 {@code TextRegion[start=2, end=5]}，长到无法在日志里
     * 横向对齐，而这个值在 Demo 里要出现几十次。换成区间写法之后，
     * 一眼就能看出"尽头是 5，但 5 不在里面"—— 这正是半开区间要传达的语义。
     *
     * @return 形如 {@code [2,5)}；无选区时形如 {@code [4,4)}
     */
    @Override
    public String toString() {
        return "[" + start + "," + end + ")";
    }
}
