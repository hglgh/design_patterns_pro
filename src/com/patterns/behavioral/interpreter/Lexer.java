package com.patterns.behavioral.interpreter;

import java.util.ArrayList;
import java.util.List;

/**
 * 词法分析器：把一条规则字符串切成 {@link Token} 序列。
 *
 * <p><b>它替代了原项目里那些 {@code split} 和 {@code contains}。</b>
 * 原解析器的做法是：</p>
 * <pre>{@code
 * String[] tokens = rule.split("&&");
 * for (String token : tokens) {
 *     if (token.contains(">")) { ... token.split(">") ... }
 *     else if (token.contains("<")) { ... }
 * }
 * }</pre>
 * <p>这段代码有四个致命问题，全部被探针取证：</p>
 * <ol>
 *   <li>{@code contains(">")} 对 {@code >=} 也成立，切出 {@code "= 18"}
 *       然后 {@code parseInt} 崩（I3）；</li>
 *   <li>只用两个变量接住结果，第三个条件会覆盖前面的（I1）；</li>
 *   <li>只认识 {@code &&}，没有 {@code ||} 和 {@code !}；</li>
 *   <li>无法给出位置：切完只剩几段字符串，原始下标已经丢了（I4）。</li>
 * </ol>
 *
 * <p>本类按"最长匹配"原则切词：看到 {@code >} 先看下一个字符是不是
 * {@code =}，是就产出 {@code GREATER_EQUAL}，不是才产出 {@code GREATER}。
 * 这样语法层拿到的永远是完整运算符，不需要再猜。</p>
 *
 * <p><b>为什么 tokenize 返回不可变列表？</b>
 * 词法结果是解析器唯一的输入，一旦被外部改动，解析结果就无法复现。
 * {@code List.copyOf} 让这份"输入快照"不可篡改。</p>
 */
public final class Lexer {

    private final String source;
    private int position;

    /**
     * 构造词法分析器。
     *
     * @param source 规则文本，不能为 null
     */
    public Lexer(String source) {
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        this.source = source;
    }

    /**
     * 切出全部词法单元，末尾一定追加一个 {@link TokenType#END_OF_INPUT}。
     *
     * @return 不可变的 token 列表
     * @throws ExpressionException 遇到无法识别的字符
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (position < source.length()) {
            char current = source.charAt(position);
            if (Character.isWhitespace(current)) {
                position++;
                continue;
            }
            int start = position;
            if (Character.isDigit(current)) {
                tokens.add(readNumber(start));
            } else if (isIdentifierStart(current)) {
                tokens.add(readIdentifier(start));
            } else {
                tokens.add(readSymbol(start));
            }
        }
        tokens.add(new Token(TokenType.END_OF_INPUT, "", source.length()));
        return List.copyOf(tokens);
    }

    /**
     * 读一个整数。只接受 ASCII 数字，且不允许前导正负号
     * （{@code -5} 会被切成 {@code MINUS} + {@code NUMBER}，
     * 由语法层当作一元负号处理，这样 {@code 10-5} 和 {@code -5}
     * 走的是同一套规则，不需要在词法层做上下文判断）。
     *
     * @param start 起始下标
     * @return 数字 token
     */
    private Token readNumber(int start) {
        while (position < source.length() && Character.isDigit(source.charAt(position))) {
            position++;
        }
        return new Token(TokenType.NUMBER, source.substring(start, position), start);
    }

    /**
     * 读一个标识符（变量名）。
     *
     * <p>允许下划线开头、允许数字结尾，但不允许数字开头 ——
     * 否则 {@code 10abc} 会被读成一个"名字叫 10abc 的变量"，
     * 而它更可能是用户写错了（探针 I7 的同类问题）。</p>
     *
     * @param start 起始下标
     * @return 标识符 token
     */
    private Token readIdentifier(int start) {
        while (position < source.length() && isIdentifierPart(source.charAt(position))) {
            position++;
        }
        return new Token(TokenType.IDENTIFIER, source.substring(start, position), start);
    }

    /**
     * 读一个符号。双字符运算符优先匹配。
     *
     * @param start 起始下标
     * @return 符号 token
     * @throws ExpressionException 字符不在支持的符号集合内
     */
    private Token readSymbol(int start) {
        char current = source.charAt(position);
        switch (current) {
            case '+':
                position++;
                return new Token(TokenType.PLUS, "+", start);
            case '-':
                position++;
                return new Token(TokenType.MINUS, "-", start);
            case '*':
                position++;
                return new Token(TokenType.STAR, "*", start);
            case '/':
                position++;
                return new Token(TokenType.SLASH, "/", start);
            case '(':
                position++;
                return new Token(TokenType.LEFT_PAREN, "(", start);
            case ')':
                position++;
                return new Token(TokenType.RIGHT_PAREN, ")", start);
            case '>':
                position++;
                if (match('=')) {
                    return new Token(TokenType.GREATER_EQUAL, ">=", start);
                }
                return new Token(TokenType.GREATER, ">", start);
            case '<':
                position++;
                if (match('=')) {
                    return new Token(TokenType.LESS_EQUAL, "<=", start);
                }
                return new Token(TokenType.LESS, "<", start);
            case '=':
                position++;
                if (match('=')) {
                    return new Token(TokenType.EQUAL, "==", start);
                }
                throw new ExpressionException(
                        "单个 '=' 不是比较运算符，判断相等请写 '=='", source, start);
            case '!':
                position++;
                if (match('=')) {
                    return new Token(TokenType.NOT_EQUAL, "!=", start);
                }
                return new Token(TokenType.NOT, "!", start);
            case '&':
                position++;
                if (match('&')) {
                    return new Token(TokenType.AND, "&&", start);
                }
                throw new ExpressionException(
                        "单个 '&' 不是逻辑运算符，逻辑与请写 '&&'", source, start);
            case '|':
                position++;
                if (match('|')) {
                    return new Token(TokenType.OR, "||", start);
                }
                throw new ExpressionException(
                        "单个 '|' 不是逻辑运算符，逻辑或请写 '||'", source, start);
            default:
                throw new ExpressionException(
                        "无法识别的字符 '" + current + "'", source, start);
        }
    }

    /**
     * 如果下一个字符是期望的那个，就消费掉并返回 true。
     *
     * @param expected 期望字符
     * @return 是否匹配并消费
     */
    private boolean match(char expected) {
        if (position < source.length() && source.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private static boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }
}
