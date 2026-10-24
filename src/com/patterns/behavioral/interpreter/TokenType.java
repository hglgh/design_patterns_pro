package com.patterns.behavioral.interpreter;

/**
 * 词法单元类型。
 *
 * <p><b>为什么要有这一层枚举？</b>
 * 原项目的解析器用 {@code String.contains(">")} 和 {@code split("&&")}
 * 直接对原始字符串做判断（探针 I3：{@code >=} 被误判成 {@code >}，
 * 解析出 {@code "= 18"} 然后 {@code Integer.parseInt} 抛异常）。
 * 根本原因是「字符」和「单词」被混为一谈 —— 解析器必须一边看字符
 * 一边猜边界。一旦引入词法层，{@code >=} 在切词阶段就已经是一个
 * 完整的、不可再分的单元，语法层永远看不到"半个运算符"。</p>
 *
 * <p>这也解释了为什么 {@code END_OF_INPUT} 要作为一个真实存在的
 * token：有了它，"表达式末尾还有东西"（探针 I7 的
 * {@code "10*5" 返回 10}）就变成一个可以显式检查的条件，
 * 而不是靠 {@code pos < length} 这类容易写漏的边界判断。</p>
 */
public enum TokenType {

    /** 整数字面量，例如 18、500。 */
    NUMBER,

    /** 变量名，例如 age、score、weight。 */
    IDENTIFIER,

    /** 加号 +。 */
    PLUS,

    /** 减号 -。 */
    MINUS,

    /** 乘号 *。 */
    STAR,

    /** 除号 /。 */
    SLASH,

    /** 大于 >。 */
    GREATER,

    /** 大于等于 >=。 */
    GREATER_EQUAL,

    /** 小于 <。 */
    LESS,

    /** 小于等于 <=。 */
    LESS_EQUAL,

    /** 等于 ==。 */
    EQUAL,

    /** 不等于 !=。 */
    NOT_EQUAL,

    /** 逻辑与 &&。 */
    AND,

    /** 逻辑或 ||。 */
    OR,

    /** 逻辑非 !。 */
    NOT,

    /** 左括号 (。 */
    LEFT_PAREN,

    /** 右括号 )。 */
    RIGHT_PAREN,

    /** 输入结束。永远位于 token 列表最后，位置等于源码长度。 */
    END_OF_INPUT
}
