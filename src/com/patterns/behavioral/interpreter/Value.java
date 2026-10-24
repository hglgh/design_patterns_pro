package com.patterns.behavioral.interpreter;

/**
 * 表达式求值的结果：要么是整数，要么是布尔值。
 *
 * <p><b>为什么不干脆用 {@code long} 表示一切（true=1, false=0）？</b>
 * 因为那样 {@code 10 && 5} 会被算成 {@code 1 && 1 = true}，
 * 而用户显然写错了。原项目的解释器没有类型概念
 * （{@code Context} 只有两个 int，{@code Expression.interpret} 只返回 boolean），
 * 所以「字段不存在」和「值算出来是 0」这两种完全不同的情况
 * 被混成同一个 {@code false}（探针 I5）。</p>
 *
 * <p>引入 {@code Value} 之后，类型错误会在求值的第一时间暴露：
 * 逻辑运算拿到 {@code Num(10)} 就抛
 * 「<code>10 &amp;&amp; 5</code> 需要布尔值，实际是数字 10」，
 * 而不是安静地给出一个错误答案。</p>
 *
 * <p><b>为什么把两个 record 嵌在接口里？</b>
 * 它们各自只有一个字段、只有两三行逻辑，独立成文件会让读者
 * 在"值"和"值类型"之间来回跳。嵌进来之后，
 * {@code Value.Num} 和 {@code Value.Bool} 的完整定义
 * 和它们的公共契约在同一屏里，sealed 的 permits 也可以省略
 * （同一编译单元的实现类自动被允许）。</p>
 *
 * <p><b>两个 record 都叫 {@code value()}，会不会混淆？</b>
 * 不会 —— 调用方拿到的是 {@link Value}，必须先用
 * {@link #asNumber} 或 {@link #asBoolean} 声明自己要什么。
 * 这两个方法就是类型检查点：写错了会立刻抛出带说明的异常，
 * 而不是静默返回一个默认值。</p>
 */
public sealed interface Value {

    /**
     * 把整数包成 Value。
     *
     * @param value 整数值
     * @return 数字值
     */
    static Value of(long value) {
        return new Num(value);
    }

    /**
     * 把布尔包成 Value。
     *
     * @param value 布尔值
     * @return 布尔值
     */
    static Value of(boolean value) {
        return new Bool(value);
    }

    /**
     * 要求自己是一个数字。
     *
     * @param where 上下文描述，出现在异常消息里，便于定位是哪个子表达式出错
     * @return 整数值
     * @throws ExpressionException 实际是布尔值
     */
    long asNumber(String where);

    /**
     * 要求自己是一个布尔值。
     *
     * @param where 上下文描述，出现在异常消息里
     * @return 布尔值
     * @throws ExpressionException 实际是数字
     */
    boolean asBoolean(String where);

    /**
     * 用于输出的文本形式。
     *
     * @return 数字十进制文本，或 "true" / "false"
     */
    String display();

    /**
     * 数字值。
     *
     * @param value 整数值
     */
    record Num(long value) implements Value {

        @Override
        public long asNumber(String where) {
            return value;
        }

        @Override
        public boolean asBoolean(String where) {
            throw new ExpressionException(
                    where + " 需要布尔值，实际是数字 " + value, null, 0);
        }

        @Override
        public String display() {
            return Long.toString(value);
        }
    }

    /**
     * 布尔值。
     *
     * @param value 布尔值
     */
    record Bool(boolean value) implements Value {

        @Override
        public long asNumber(String where) {
            throw new ExpressionException(
                    where + " 需要数字，实际是布尔值 " + value, null, 0);
        }

        @Override
        public boolean asBoolean(String where) {
            return value;
        }

        @Override
        public String display() {
            return Boolean.toString(value);
        }
    }
}
