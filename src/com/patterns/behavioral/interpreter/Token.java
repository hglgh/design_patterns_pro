package com.patterns.behavioral.interpreter;

/**
 * 一个词法单元：类型 + 原文 + 在源码中的起始下标。
 *
 * <p><b>{@code position} 是这一整套重构里最有价值的一个字段。</b>
 * 原项目的报错只有 {@code For input string: "eighteen"}
 * （探针 I4）—— 它甚至没说是哪条规则、哪个位置，
 * 规则一多就只能靠肉眼找。有了位置，报错可以画出</p>
 * <pre>
 * 规则: age &gt; eighteen &amp;&amp; score &lt; 500
 *                ^
 * </pre>
 * <p>这样的指针，读者一眼就能看到出错的那一段。</p>
 *
 * <p><b>为什么 position 是 int 而不是行/列？</b>
 * 表达式是单行文本，没有换行的概念。要去支持多行规则时，
 * 再引入"行号+列号"的换算即可，换算逻辑也只会在异常渲染处出现一次。
 * 现在就加行号只会增加没人用的字段。</p>
 *
 * @param type     词法单元类型
 * @param text     原文片段（{@code END_OF_INPUT} 为空串）
 * @param position 在源码中的起始下标（0 开始）
 */
public record Token(TokenType type, String text, int position) {

    /**
     * 紧凑构造器：类型不能为 null，位置不能为负。
     */
    public Token {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null");
        }
        if (text == null) {
            throw new IllegalArgumentException("text 不能为 null");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position 不能为负，实际 " + position);
        }
    }

    /**
     * 人类可读的描述，用于异常消息。
     *
     * <p>把 {@code END_OF_INPUT} 显示成"输入结束"而不是空字符串，
     * 是为了让"表达式不完整"这类错误读起来像一句人话：
     * {@code 期望数字，实际遇到 输入结束}。</p>
     *
     * @return 形如 "'>= " 或 "输入结束" 的描述
     */
    public String display() {
        if (type == TokenType.END_OF_INPUT) {
            return "输入结束";
        }
        return "'" + text + "'";
    }

    /**
     * 是否是输入结束标记。
     *
     * @return 类型为 {@link TokenType#END_OF_INPUT} 时返回 true
     */
    public boolean isEnd() {
        return type == TokenType.END_OF_INPUT;
    }
}
