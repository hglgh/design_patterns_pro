package com.patterns.util;

/**
 * 控制台文本对齐工具：解决<b>中日韩字符在等宽终端里占 2 列</b>导致的表格错位问题。
 *
 * <p><b>这不是设计模式的一部分</b>，纯粹是输出辅助工具。
 * 各模式的 Demo 依然互相独立，只是共用了这一个"打印对齐"的方法。
 *
 * <p><b>为什么需要它？</b>
 * Java 的 {@code System.out.printf("%-30s", s)} 是按<b>字符个数</b>补齐的，
 * 但一个汉字在等宽终端里占<b>两列</b>。看两个字符串：
 * <pre>
 *   String a = "满减（满 100 减 20）";      // 14 个字符，但显示宽度是 20 列
 *   String b = "新人首单立减 15";           //  9 个字符，但显示宽度是 15 列
 * </pre>
 * 用 {@code %-30s} 打印它们，printf 分别补 16 和 21 个空格，
 * 于是两行的<b>实际列数变成 36 和 36</b>——看似相等，但与后面拼接的金额列就对不上了：
 * <pre>
 *   未修正的实际输出（注意金额列参差不齐）：
 *     满减（满 100 减 20）                        ￥330.00     ￥20.00
 *     打折（9 折，折扣率 0.90）                      ￥315.00     ￥35.00
 *     VIP（8 折，最多优惠 200 元）                   ￥280.00     ￥70.00
 *     新人首单立减 15                             ￥335.00     ￥15.00
 * </pre>
 * 因为每行的汉字个数不同，printf 补的空格数也就不同，<b>金额列完全对不齐</b>。
 *
 * <p>本工具按<b>显示宽度</b>（而非字符数）补齐，修正后（金额列整齐）：
 * <pre>
 *   满减（满 100 减 20）          ￥330.00   ￥20.00
 *   打折（9 折，折扣率 0.90）      ￥315.00   ￥35.00
 *   VIP（8 折，最多优惠 200 元）   ￥280.00   ￥70.00
 *   新人首单立减 15              ￥335.00   ￥15.00
 * </pre>
 *
 * <p><b>宽度判定依据</b>：Unicode 的 East Asian Width 属性（UAX #11）中标记为
 * {@code W}（Wide）或 {@code F}（Fullwidth）的字符占 2 列，其余占 1 列。
 * 本实现用码点区间近似判定，覆盖了中文、日文假名、韩文、全角标点等常见情况。
 *
 * <p><b>已知局限</b>（诚实说明）：
 * <ul>
 *   <li>制表符、换行符、ANSI 颜色转义序列不做处理；</li>
 *   <li>Emoji（U+1F300 以上）在多数现代终端占 2 列，本实现<b>未覆盖</b>；</li>
 *   <li>{@code → ★ ◆ ◇} 这类"歧义宽度（Ambiguous）"字符，
 *       在中文终端里通常占 2 列、在西文终端占 1 列，本实现按 <b>1 列</b>处理
 *       （因为它们只用于行内标记，不参与表格对齐）；</li>
 *   <li>最终效果还取决于终端字体是否真正等宽。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public final class DisplayWidth {

    /** 工具类，禁止实例化。 */
    private DisplayWidth() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 计算字符串在等宽终端里的显示宽度（列数）。
     *
     * @param text 待计算的文本，{@code null} 视为空串
     * @return 显示宽度
     */
    public static int width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int total = 0;
        // 用 codePointAt + charCount 遍历，正确处理 BMP 之外的增补字符（如 Emoji、生僻字）
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            total += isFullWidth(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return total;
    }

    /**
     * 左对齐：在右侧补空格，使显示宽度达到 {@code targetWidth}。
     *
     * <p>如果文本已经超出目标宽度，<b>原样返回不截断</b>——
     * 截断中文会切出半个字符或者丢掉关键信息，宁可错位也不要丢数据。
     *
     * @param text        原始文本
     * @param targetWidth 目标显示宽度
     * @return 补齐后的文本
     */
    public static String padRight(String text, int targetWidth) {
        String safe = (text == null) ? "" : text;
        int gap = targetWidth - width(safe);
        return (gap <= 0) ? safe : safe + " ".repeat(gap);
    }

    /**
     * 右对齐：在左侧补空格，使显示宽度达到 {@code targetWidth}。
     * 金额、数量这类数字列应该右对齐，便于逐位比较大小。
     *
     * @param text        原始文本
     * @param targetWidth 目标显示宽度
     * @return 补齐后的文本
     */
    public static String padLeft(String text, int targetWidth) {
        String safe = (text == null) ? "" : text;
        int gap = targetWidth - width(safe);
        return (gap <= 0) ? safe : " ".repeat(gap) + safe;
    }

    /**
     * 生成一条分隔线，例如 {@code line('-', 40)} → {@code "----------------------------------------"}。
     *
     * @param ch     填充字符
     * @param length 长度
     * @return 分隔线
     */
    public static String line(char ch, int length) {
        return String.valueOf(ch).repeat(Math.max(0, length));
    }

    /**
     * 判断一个码点是否为全角（占 2 列）。
     *
     * <p>区间来自 Unicode East Asian Width 属性的 W/F 分类，做了合并简化。
     */
    private static boolean isFullWidth(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)     // 韩文字母 Jamo
                || (cp >= 0x2E80 && cp <= 0x303E)  // CJK 部首、康熙部首、CJK 符号标点（含 、。「」）
                || (cp >= 0x3041 && cp <= 0x33FF)  // 平假名、片假名、注音、CJK 兼容字符
                || (cp >= 0x3400 && cp <= 0x4DBF)  // CJK 扩展 A
                || (cp >= 0x4E00 && cp <= 0x9FFF)  // CJK 基本汉字
                || (cp >= 0xA000 && cp <= 0xA4CF)  // 彝文
                || (cp >= 0xAC00 && cp <= 0xD7A3)  // 韩文音节
                || (cp >= 0xF900 && cp <= 0xFAFF)  // CJK 兼容汉字
                || (cp >= 0xFE30 && cp <= 0xFE6F)  // CJK 兼容形式、小型变体
                || (cp >= 0xFF00 && cp <= 0xFF60)  // 全角 ASCII（含 ，（）：；！？）
                || (cp >= 0xFFE0 && cp <= 0xFFE6)  // 全角符号 ￥ ￠ ￡ ￥ ₩
                || (cp >= 0x20000 && cp <= 0x3FFFD); // CJK 扩展 B 及以上（生僻字）
    }
}
