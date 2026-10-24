package com.patterns.behavioral.memento;

import java.util.Objects;

/**
 * 编辑器与历史的边界参数，集中一处。
 *
 * <p><b>为什么把"校验"单独抽出来？</b>
 * 探针 P6 证明了原项目的漏洞链：
 *
 * <pre>
 *   setContent(null)          // 不校验
 *     -> save()               // 不校验，拍下一张 content=null 的快照
 *       -> pushMemento()      // 不校验，null 进栈
 *         -> restore(它)      // 文档真的变空了
 * </pre>
 *
 * 三个类、四个方法，<b>没有一层拦</b>。原因不是谁写错了，而是"校验"
 * 在原始设计里<b>没有归属</b> —— 每个类都以为别人会管。
 *
 * <p>把边界值集中到这个类，等于给"什么算合法的正文"一个唯一答案：
 * 改上限改这里，改钳制规则改这里，写测试也只测这里。
 *
 * <p><b>为什么是包私有（没有 {@code public}）？</b>
 * 它不是 API，是三个类之间的内部约定。外部代码没有理由直接调用
 * {@code EditorLimits.clamp(...)}；把它暴露出去只会让"边界规则"
 * 从实现细节变成必须长期兼容的承诺。
 *
 * <p><b>为什么 {@code final} + 私有构造器？</b>
 * 纯静态工具的标配。不加的话，总有人会 {@code new EditorLimits()}，
 * 然后这个类的作者就得为"实例有没有状态"回答一辈子问题。
 *
 * @author design-patterns-pro
 */
final class EditorLimits {

    /** 正文长度上限。超过这个长度，一次快照就要复制几十 MB，撤销栈会变成内存黑洞。 */
    static final int MAX_TEXT_LENGTH = 200_000;

    /** 撤销栈的最大深度。超过就淘汰最老的一条 —— 探针 P8 里原项目 10 万条全留着。 */
    static final int MAX_UNDO_DEPTH = 100;

    /** 未显式指定时的默认撤销深度。 */
    static final int DEFAULT_UNDO_DEPTH = 20;

    /**
     * 校验并返回正文。
     *
     * <p>返回自身而不是 {@code void}，是为了让调用方写成
     * {@code this.text = EditorLimits.requireText(text)}，
     * 校准与赋值在同一行完成，没有"忘了校验"的缝。
     *
     * @param text 待校验正文
     * @return 同一个字符串
     * @throws NullPointerException     正文为 null 时
     * @throws IllegalArgumentException 超过 {@link #MAX_TEXT_LENGTH} 时
     */
    static String requireText(String text) {
        Objects.requireNonNull(text, "正文不能为 null");
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "正文超过上限 " + MAX_TEXT_LENGTH + " 字符，实际 " + text.length());
        }
        return text;
    }

    /**
     * 把下标钳制到 {@code [min, max]}。
     *
     * <p>光标位置用"钳制"而不是"抛异常"，因为这是<b>用户操作</b>：
     * 点了行尾之外的地方，编辑器应该把光标停在行尾，而不是弹一个错误框。
     * 而 {@link TextRegion} 的构造器用"抛异常"，因为它面对的是<b>程序入参</b>。
     * <b>同一个数字，两种来源，两种处理</b> —— 这是校验策略的关键区分。
     *
     * @param value 原始值
     * @param min   下界
     * @param max   上界
     * @return 钳制后的值
     */
    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 要求一个正整数。
     *
     * @param value 待校验值
     * @param name  参数名，用于异常消息
     * @throws IllegalArgumentException 小于等于 0 时
     */
    static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0，实际 " + value);
        }
    }

    private EditorLimits() {
    }
}
