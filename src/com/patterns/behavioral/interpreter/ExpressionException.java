package com.patterns.behavioral.interpreter;

/**
 * 表达式处理过程中的所有可预期错误，统一携带源码位置。
 *
 * <p><b>为什么必须自己定义异常？</b>
 * 原项目把错误全都交给了 JDK 的 {@code NumberFormatException}
 * 和 {@code NullPointerException}：</p>
 * <ul>
 *   <li>{@code "age >= 18 && score < 500"} → {@code For input string: "= 18"}
 *       （探针 I3）—— 用户写的是 {@code >=}，报的却是"字符串 '= 18' 不能转数字"；</li>
 *   <li>{@code "age > 18"} → {@code NullPointerException}（探针 I2）——
 *       完全看不出是"少了右边一半"；</li>
 *   <li>{@code ""} → {@code For input string: ""}（探针 I8）——
 *       空输入和"阈值是空字符串"报同一个错。</li>
 * </ul>
 * <p>这些异常的共同问题是：<b>它们描述的是"哪个 JDK 方法失败了"，
 * 而不是"用户写的规则哪里不对"</b>。自定义异常的价值在于
 * 把后者作为第一信息，位置作为第二信息。</p>
 *
 * <p><b>{@link #render()} 而不是 {@code getMessage()}。</b>
 * {@code getMessage()} 只返回一句话，适合日志；
 * {@code render()} 返回带源码和 {@code ^} 指针的三行，
 * 适合直接给终端用户看。两者分开，避免"为了好看而污染异常消息"。</p>
 */
public final class ExpressionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误信息里最多展示的规则字符数。 */
    private static final int MAX_SOURCE_CHARS = 100;

    /** 截断时在出错位置左侧保留的字符数。 */
    private static final int CARET_CONTEXT = 30;

    /** 出错的原始表达式，可能为 null（例如变量未定义时上下文已丢失源码）。 */
    private final String source;

    /** 出错位置在 {@code source} 中的下标，可能为 0。 */
    private final int position;

    /**
     * 构造异常。
     *
     * @param message  面向人的错误说明，应说明"期望什么、实际遇到什么"
     * @param source   原始表达式，可为 null
     * @param position 出错下标，负数会被当成 0
     */
    public ExpressionException(String message, String source, int position) {
        super(message);
        this.source = source;
        this.position = Math.max(position, 0);
    }

    /**
     * 原始表达式。
     *
     * @return 表达式文本，可能为 null
     */
    public String source() {
        return source;
    }

    /**
     * 出错位置。
     *
     * @return 下标（0 开始）
     */
    public int position() {
        return position;
    }

    /**
     * 渲染成三段式：错误说明、原始规则、指到出错位置的 {@code ^}。
     *
     * <p>换行符固定用 {@code "\n"} 而不是
     * {@code System.lineSeparator()}：输出需要逐字节可复现
     * （README 里的哈希校验依赖这一点），跨平台换行符会让哈希变化。</p>
     *
     * <p><b>超长规则会被截成窗口。</b>
     * 一条 4 百字符的病态规则如果把整段打印出来，
     * 错误信息本身就没人看得下去；而且控制台会折行，
     * {@code ^} 的位置也就对不上了。
     * 所以超过 {@link #MAX_SOURCE_CHARS} 字符时只保留
     * 出错位置附近的窗口，两侧用 {@code ...} 标记被省略的部分。
     * 标记故意用 3 个 ASCII 点而不是省略号字符，
     * 这样 {@code ^} 的空格数与显示宽度仍然一一对应。</p>
     *
     * @return 可直接打印的多行文本
     */
    public String render() {
        if (source == null || source.isEmpty()) {
            if (position > 0) {
                return getMessage() + "\n  位置: 第 " + position + " 个字符";
            }
            return getMessage();
        }
        int caretIndex = Math.min(position, source.length());
        String shownSource;
        int shownCaret;
        if (source.length() <= MAX_SOURCE_CHARS) {
            shownSource = source;
            shownCaret = caretIndex;
        } else {
            int start = Math.max(0, caretIndex - CARET_CONTEXT);
            int end = Math.min(source.length(), start + MAX_SOURCE_CHARS);
            start = Math.max(0, end - MAX_SOURCE_CHARS);
            String prefix = start > 0 ? "..." : "";
            String suffix = end < source.length() ? "..." : "";
            shownSource = prefix + source.substring(start, end) + suffix;
            shownCaret = prefix.length() + (caretIndex - start);
        }
        StringBuilder caret = new StringBuilder(shownCaret + 1);
        for (int index = 0; index < shownCaret; index++) {
            caret.append(' ');
        }
        caret.append('^');
        return getMessage() + "\n"
                + "  规则: " + shownSource + "\n"
                + "        " + caret;
    }
}
