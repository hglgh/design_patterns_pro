package com.patterns.behavioral.interpreter;

import java.util.List;

/**
 * 语法分析器：把 {@link Lexer} 切出的记号流组装成 {@link Expression} 树。
 *
 * <p><b>它为什么必须存在？</b>
 * 因为解释器模式的"表达式类"只回答「怎么算」，
 * 不回答「先算谁」。{@code 10 + 5 * 2} 的树应该是
 * {@code (10 + (5 * 2))} 还是 {@code ((10 + 5) * 2)}，
 * 这个决定权在语法层。原项目把这一步交给了手工
 * {@code new GreaterThanExpression(new Context(...), 18)}，
 * 于是"规则文本"这个最重要的输入被完全绕过了（探针 I3/I7）。</p>
 *
 * <p><b>递归下降的核心技巧：每个非终结符一个方法，
 * 优先级从低到高排列。</b></p>
 *
 * <pre>{@code
 * expression     := or
 * or             := and ( "||" and )*
 * and            := equality ( "&&" equality )*
 * equality       := comparison ( ("==" | "!=") comparison )*
 * comparison     := additive ( (">" | ">=" | "<" | "<=") additive )*
 * additive       := multiplicative ( ("+" | "-") multiplicative )*
 * multiplicative := unary ( ("*" | "/") unary )*
 * unary          := ("!" | "-") unary | primary
 * primary        := NUMBER | IDENTIFIER | "(" expression ")"
 * }</pre>
 *
 * <p>调用链本身就是优先级：{@code or} 在最外层，所以它最后被结合；
 * {@code primary} 在最内层，所以它最先成型。
 * 要加一种"比 {@code ||} 更低"的运算符（比如 {@code ?:}），
 * 只需要在 {@code or} 外面再套一层，其余方法一行不改。</p>
 *
 * <p><b>三个必须讲清的实现细节：</b></p>
 *
 * <ol>
 *   <li><b>左结合用循环，不用递归。</b>
 *       {@code 10 - 3 - 2} 必须是 {@code (10 - 3) - 2 = 5}。
 *       如果写成 {@code additive() { left(); if ("-") right = additive(); }}
 *       就变成右结合，算出 9，而且每多一个减号就多一层
 *       方法递归 —— 项数一多就爆栈。
 *       这里统一用 {@code while (操作符) { left = new Xxx(op, left, right()); }}，
 *       既保证左结合，又让"同一层的项"只占一层栈。</li>
 *   <li><b>末尾必须检查是否还有记号。</b>
 *       原项目 {@code "10abc"} 会安静地算出 10（探针 I7）。
 *       这里 {@code parse} 结束时若不是 {@code END_OF_INPUT}
 *       就报"多余的内容"，并且指出位置。</li>
 *   <li><b>嵌套层数设上限。</b>
 *       语法分析器本身是递归的，{@code (((((...)))))} 这种
 *       病态输入会让分析器自己爆栈。给出 {@link #MAX_NESTING}
 *       层的上限，超过时抛可读的异常，而不是 StackOverflowError。</li>
 * </ol>
 *
 * <p><b>括号信息会被丢掉，这是有意的。</b>
 * 语法树只保留"结合结果"，不保留"用户写没写括号"。
 * {@code (10) + 5} 和 {@code 10 + 5} 得到同一棵树 ——
 * 求值不需要区别，但 {@code describe()} 出来的文本
 * 与原文不会逐字相同。这是所有语法分析器的常态：
 * 树表达语义，不表达排版。</p>
 */
public final class Parser {

    /**
     * 允许的最大嵌套层数（括号与一元运算符各算一层）。
     *
     * <p>真实规则不会嵌套上百层；设上限是为了让
     * 恶意或误生成的输入得到一条人类可读的错误，
     * 而不是一个不携带任何位置的 {@link StackOverflowError}。</p>
     */
    public static final int MAX_NESTING = 200;

    private final String source;
    private final List<Token> tokens;
    private int index;
    private int nesting;

    /**
     * 用一段规则文本创建分析器。
     *
     * <p>构造时立刻完成词法分析，语法分析由 {@link #parse()} 触发。
     * 这样"词法错误"和"语法错误"的抛出时机是分开的，
     * 便于在使用时分别说明。</p>
     *
     * @param source 规则文本，不能为 null
     * @throws IllegalArgumentException source 为 null
     * @throws ExpressionException      词法错误
     */
    public Parser(String source) {
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        this.source = source;
        this.tokens = new Lexer(source).tokenize();
        this.index = 0;
        this.nesting = 0;
    }

    /**
     * 一次性完成词法 + 语法分析。
     *
     * @param source 规则文本
     * @return 表达式树
     * @throws ExpressionException 词法或语法错误
     */
    public static Expression parse(String source) {
        return new Parser(source).parse();
    }

    /**
     * 解析出表达式树，并要求整段文本被完整消费。
     *
     * @return 表达式树
     * @throws ExpressionException 语法错误，或末尾还有多余内容
     */
    public Expression parse() {
        if (peek().isEnd()) {
            throw error(peek(), "表达式不能为空");
        }
        Expression expression = or();
        Token token = peek();
        if (!token.isEnd()) {
            throw error(token, "多余的内容 " + token.display() + "，表达式应在此结束");
        }
        return expression;
    }

    private Expression or() {
        Expression left = and();
        while (check(TokenType.OR)) {
            advance();
            left = new LogicalExpression(LogicalExpression.Operator.OR, left, and());
        }
        return left;
    }

    private Expression and() {
        Expression left = equality();
        while (check(TokenType.AND)) {
            advance();
            left = new LogicalExpression(LogicalExpression.Operator.AND, left, equality());
        }
        return left;
    }

    private Expression equality() {
        Expression left = comparison();
        while (check(TokenType.EQUAL) || check(TokenType.NOT_EQUAL)) {
            Token token = advance();
            ComparisonExpression.Operator operator = token.type() == TokenType.EQUAL
                    ? ComparisonExpression.Operator.EQUAL
                    : ComparisonExpression.Operator.NOT_EQUAL;
            left = new ComparisonExpression(operator, left, comparison());
        }
        return left;
    }

    private Expression comparison() {
        Expression left = additive();
        while (check(TokenType.GREATER) || check(TokenType.GREATER_EQUAL)
                || check(TokenType.LESS) || check(TokenType.LESS_EQUAL)) {
            Token token = advance();
            ComparisonExpression.Operator operator = switch (token.type()) {
                case GREATER -> ComparisonExpression.Operator.GREATER;
                case GREATER_EQUAL -> ComparisonExpression.Operator.GREATER_EQUAL;
                case LESS -> ComparisonExpression.Operator.LESS;
                case LESS_EQUAL -> ComparisonExpression.Operator.LESS_EQUAL;
                default -> throw new IllegalStateException("不可能到达: " + token);
            };
            left = new ComparisonExpression(operator, left, additive());
        }
        return left;
    }

    private Expression additive() {
        Expression left = multiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token token = advance();
            ArithmeticExpression.Operator operator = token.type() == TokenType.PLUS
                    ? ArithmeticExpression.Operator.ADD
                    : ArithmeticExpression.Operator.SUBTRACT;
            left = new ArithmeticExpression(operator, left, multiplicative());
        }
        return left;
    }

    private Expression multiplicative() {
        Expression left = unary();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            Token token = advance();
            ArithmeticExpression.Operator operator = token.type() == TokenType.STAR
                    ? ArithmeticExpression.Operator.MULTIPLY
                    : ArithmeticExpression.Operator.DIVIDE;
            left = new ArithmeticExpression(operator, left, unary());
        }
        return left;
    }

    private Expression unary() {
        if (check(TokenType.MINUS) || check(TokenType.NOT)) {
            Token token = advance();
            UnaryExpression.Operator operator = token.type() == TokenType.MINUS
                    ? UnaryExpression.Operator.NEGATE
                    : UnaryExpression.Operator.NOT;
            guardNesting(token);
            nesting++;
            try {
                return new UnaryExpression(operator, unary());
            } finally {
                nesting--;
            }
        }
        return primary();
    }

    private Expression primary() {
        Token token = peek();

        if (check(TokenType.NUMBER)) {
            advance();
            try {
                return new NumberExpression(Long.parseLong(token.text()));
            } catch (NumberFormatException e) {
                throw error(token, "数字超出 long 范围: " + token.text());
            }
        }

        if (check(TokenType.IDENTIFIER)) {
            advance();
            return new VariableExpression(token.text(), token.position());
        }

        if (check(TokenType.LEFT_PAREN)) {
            advance();
            guardNesting(token);
            nesting++;
            try {
                Expression inner = or();
                expect(TokenType.RIGHT_PAREN, "缺少右括号 ')'");
                return inner;
            } finally {
                nesting--;
            }
        }

        if (token.isEnd()) {
            throw error(token, "表达式在此处意外结束，缺少操作数");
        }

        throw error(token, "这里需要一个数字、变量名或左括号，实际是 " + token.display());
    }

    private void guardNesting(Token token) {
        if (nesting + 1 > MAX_NESTING) {
            throw error(token, "表达式嵌套层数超过上限 " + MAX_NESTING + "，请拆分规则");
        }
    }

    private Token peek() {
        return tokens.get(index);
    }

    private boolean check(TokenType type) {
        return peek().type() == type;
    }

    private Token advance() {
        Token token = tokens.get(index);
        if (!token.isEnd()) {
            index++;
        }
        return token;
    }

    private Token expect(TokenType type, String message) {
        if (!check(type)) {
            throw error(peek(), message + "，实际是 " + peek().display());
        }
        return advance();
    }

    private ExpressionException error(Token token, String message) {
        return new ExpressionException(message, source, token.position());
    }
}
