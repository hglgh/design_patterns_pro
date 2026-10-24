package com.patterns.behavioral.memento;

import java.util.Objects;

/**
 * 原文编辑器 —— 备忘录模式里的 <b>Originator（原发器）</b>。
 *
 * <p><b>为什么"拍快照"和"恢复快照"都必须由它自己做？</b>
 * 因为只有它知道"一份完整的状态"到底由哪些字段构成。
 * 原项目的 {@code Document} 只存了 {@code content} 一个字段，
 * 于是 {@code save()} 也就只存得下一个字段（探针 P5）。将来加上光标、
 * 选区、修订痕迹、拼写检查缓存……每一次加字段，都要回到 {@code save()}
 * 和 {@code restore()} 各改一遍。
 *
 * <p>这不是"多写两行"的问题，而是<b>知识的归属</b>问题：
 * "什么算完整状态"这条知识一旦散到 {@code Document} 之外
 * （比如让 {@code History} 去 {@code getContent()} 再自己拼），
 * 那么 {@code History} 就绑死在了 {@code Document} 的字段列表上，
 * 而 {@code Document} 迟早会违背它的承诺。
 *
 * <p><b>为什么 {@link #restore(Snapshot)} 里要做 {@code instanceof} 校验？</b>
 * 原项目的 {@code restore(DocumentMemento)} 是无条件的：
 *
 * <pre>
 *   public void restore(DocumentMemento memento) { this.content = memento.content(); }
 * </pre>
 *
 * 它隐含着两个假设：入参不为 {@code null}，且一定是"我的"备忘录。
 * 探针 P1 用 {@code null} 打破了第一个，探针 P10 用"另一份 History"
 * 打破了第二个 —— <b>两次都没有任何提示</b>，只在很久以后表现为
 * "撤销之后内容变成了别的文件"。
 *
 * <p>本项目把这两个假设都变成了<b>编译器或运行时的硬约束</b>：
 * <ul>
 *   <li>{@code null} → {@link Objects#requireNonNull} 立刻抛，且消息明确；</li>
 *   <li>不是本编辑器的快照 → {@link SnapshotRejectedException}。</li>
 * </ul>
 *
 * <p><b>为什么 {@link Memento} 是 {@code private record}？</b>
 * 因为"只有 Originator 能造、能读"这件事，在 Java 里唯一可靠的表达方式
 * 就是<b>让它对外不可见</b>。{@code public record DocumentMemento} 的
 * 构造器和访问器都是 public 的，等于把"造"和"读"两扇门都敞开（探针 P4）。
 *
 * <p>而 {@code private record Memento} 意味着：外部写不出
 * {@code new Editor.Memento(...)}，也写不出 {@code instanceof Memento}，
 * 只能把它当作 {@link Snapshot} 握着 —— 这正是 GoF 要的"窄接口"。
 * 见 {@link Snapshot} 的类注释。
 *
 * <p><b>为什么编辑器本身是可变的？</b>
 * Originator 的职责就是"被改"，把它做成不可变对象反而要每次返回新实例，
 * 调用方就得自己接住返回值，一不小心就丢了修改。
 * <b>可变的是编辑器，不可变的是快照</b> —— 这两件事不要混。
 *
 * @author design-patterns-pro
 * @see Snapshot
 * @see SnapshotHistory
 */
public final class Editor {

    private String text;
    private int cursor;
    private TextRegion selection;

    /**
     * 归属令牌：每个编辑器实例独一份，用来认领"这张快照是不是我拍的"。
     *
     * <p><b>为什么光靠 {@code instanceof Memento} 不够？</b>
     * 因为 {@code Memento} 是 {@code Editor} 的嵌套类，<b>所有 Editor 实例
     * 共享同一个 Memento 类型</b>。于是 {@code b.restore(a.snapshot(...))}
     * 会通过 {@code instanceof} 检查 —— 快照明明是 a 拍的，却能被装进 b。
     * 这是多实例场景下备忘录最隐蔽的坑：类型只能证明"这是 Editor 拍的"，
     * 证明不了"是<b>这个</b> Editor 拍的"。
     *
     * <p>用一个空 Object 当令牌而不是直接用 {@code this}，是因为快照
     * 会长期存在；如果它持有 {@code Editor} 引用，编辑器就永远无法被回收。
     */
    private final Object ownerToken = new Object();

    /**
     * 以一段初始正文建立编辑器。光标停在末尾，不选中任何字符。
     *
     * @param initialText 初始正文，不能为 null
     * @throws NullPointerException     正文为 null 时
     * @throws IllegalArgumentException 正文超过 {@link EditorLimits#MAX_TEXT_LENGTH} 时
     */
    public Editor(String initialText) {
        this.text = EditorLimits.requireText(initialText);
        this.cursor = text.length();
        this.selection = TextRegion.caret(cursor);
    }

    // ==================== 读 ====================

    /**
     * @return 当前正文，永不为 null
     */
    public String text() {
        return text;
    }

    /**
     * @return 光标位置，范围 {@code [0, text().length()]}
     */
    public int cursor() {
        return cursor;
    }

    /**
     * @return 当前选区，永不为 null
     */
    public TextRegion selection() {
        return selection;
    }

    /**
     * @return 正文长度
     */
    public int length() {
        return text.length();
    }

    /**
     * @return 选区覆盖的那一段原文，无选区时为空串
     */
    public String selectedText() {
        return text.substring(selection.start(), selection.end());
    }

    // ==================== 写 ====================

    /**
     * 在光标处插入文本；若有选区，先删掉选区再插入。
     *
     * <p>注意：这里<b>不</b>负责拍快照。什么时候拍由
     * {@link SnapshotHistory#edit} 决定 —— 因为"几个字符算一步"是
     * 交互层的决策，不是编辑器的决策。见 {@link SnapshotHistory#edit}。
     *
     * @param chunk 要插入的文本，可为空串（等价于删除选区）
     * @throws NullPointerException 为 null 时
     */
    public void type(String chunk) {
        Objects.requireNonNull(chunk, "chunk");
        replaceRange(selection.start(), selection.end(), chunk);
    }

    /**
     * 退格删除：有选区时删选区，否则删光标前 {@code count} 个字符。
     *
     * <p>光标在开头时 {@code count} 会被钳制到 0，即"删不动"，
     * 而不是抛异常 —— 用户连按退格到行首是常态。
     *
     * @param count 要删除的字符数，必须大于 0
     * @throws IllegalArgumentException {@code count <= 0} 时
     */
    public void deleteBackward(int count) {
        EditorLimits.requirePositive(count, "count");
        if (!selection.isEmpty()) {
            replaceRange(selection.start(), selection.end(), "");
            return;
        }
        int from = EditorLimits.clamp(cursor - count, 0, text.length());
        replaceRange(from, cursor, "");
    }

    /**
     * 删除当前选区；没有选区时什么也不做。
     */
    public void deleteSelection() {
        if (selection.isEmpty()) {
            return;
        }
        replaceRange(selection.start(), selection.end(), "");
    }

    /**
     * 选中一段区间，并把光标停到区间末尾。
     *
     * @param region 选区
     * @throws NullPointerException     选区为 null 时
     * @throws IllegalArgumentException 区间越界时
     */
    public void select(TextRegion region) {
        Objects.requireNonNull(region, "region");
        if (region.end() > text.length()) {
            throw new IllegalArgumentException(
                    "选区 " + region + " 超出正文长度 " + text.length());
        }
        this.selection = region;
        this.cursor = region.end();
    }

    /**
     * 全选。
     */
    public void selectAll() {
        select(new TextRegion(0, text.length()));
    }

    /**
     * 移动光标并取消选区。越界的位置会被钳制到 {@code [0, length()]}。
     *
     * @param position 目标位置
     */
    public void moveCaret(int position) {
        int target = EditorLimits.clamp(position, 0, text.length());
        this.cursor = target;
        this.selection = TextRegion.caret(target);
    }

    /**
     * 整段替换正文。
     *
     * @param newText 新正文
     * @throws NullPointerException     为 null 时
     * @throws IllegalArgumentException 超过长度上限时
     */
    public void replaceAll(String newText) {
        this.text = EditorLimits.requireText(newText);
        this.cursor = text.length();
        this.selection = TextRegion.caret(cursor);
    }

    // ==================== 备忘录 ====================

    /**
     * 拍一张快照。
     *
     * <p>快照是<b>值拷贝</b>：{@code text} 是不可变字符串，
     * {@link TextRegion} 是不可变 record，{@code cursor} 是基本类型。
     * 所以拍完之后无论编辑器怎么改，这张快照都不会跟着变。
     *
     * <p><b>为什么元信息要由调用方传进来？</b>
     * 因为"第几号、什么时候拍的、因为什么拍的"是<b>历史</b>的知识，
     * 不是<b>文档</b>的知识。让 {@code Editor} 自己维护一个计数器，
     * 等于让它偷偷记住了"我被拍过几次" —— 而同一个编辑器完全可以
     * 被两条互不相干的历史记录（比如"本次会话"和"自动保存"）。
     *
     * @param metadata 元信息，由 {@link SnapshotHistory} 生成
     * @return 一份不透明的快照
     * @throws NullPointerException 元信息为 null 时
     */
    public Snapshot snapshot(SnapshotMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        return new Memento(text, cursor, selection, metadata, ownerToken);
    }

    /**
     * 恢复到某个快照。
     *
     * <p>三道防线：
     * <ol>
     *   <li>{@code null} 直接抛，消息是"snapshot"而不是一句无头 NPE；</li>
     *   <li>不是 {@code Memento} 类型 → 抛 {@link SnapshotRejectedException}。
     *       因为 {@code Memento} 是私有嵌套类，正常的业务代码<b>走不到</b>
     *       这个分支 —— 走到了就说明有人在绕过类型系统；</li>
     *   <li>是 {@code Memento} 但归属令牌不是自己的 → 同上抛错。
     *       这一道专门防"另一篇文档的快照被装进来"（原项目探针 P10）。</li>
     * </ol>
     *
     * @param snapshot 要恢复到的快照
     * @throws NullPointerException     快照为 null 时
     * @throws SnapshotRejectedException 快照不属于本编辑器时
     */
    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!(snapshot instanceof Memento memento) || memento.owner() != ownerToken) {
            throw new SnapshotRejectedException(
                    "拒绝恢复：" + snapshot.metadata().display()
                            + " 不是本编辑器产生的快照");
        }
        this.text = memento.text();
        this.cursor = memento.cursor();
        this.selection = memento.selection();
    }

    // ==================== 展示 ====================

    /**
     * 一行状态摘要，供 Demo 打印。
     *
     * @return 形如 {@code 正文「客服回复」 光标 4 选区 [2,4)}
     */
    public String describe() {
        return "正文「" + text + "」 光标 " + cursor + " 选区 " + selection;
    }

    @Override
    public String toString() {
        return "Editor{" + describe() + "}";
    }

    // ==================== 内部 ====================

    /**
     * 把 {@code [from, to)} 换成 {@code replacement}，并把光标推到新文本末尾。
     *
     * <p>所有写操作最终都汇到这一个方法，所以"改完正文之后光标和选区怎么变"
     * 只需要在这一处想清楚。原项目的 {@code setContent} 也有类似的机会，
     * 可惜它只有 {@code content} 一个字段，无从谈起。
     *
     * @param from        替换起点（含）
     * @param to          替换终点（不含）
     * @param replacement 替换内容
     */
    private void replaceRange(int from, int to, String replacement) {
        String next = text.substring(0, from) + replacement + text.substring(to);
        this.text = EditorLimits.requireText(next);
        int caret = from + replacement.length();
        this.cursor = caret;
        this.selection = TextRegion.caret(caret);
    }

    /**
     * 备忘录本体：不可变、私有、只能由 {@link Editor} 制造与读取。
     *
     * <p><b>为什么用 {@code record} 而不是普通类？</b>
     * 因为快照的全部语义就是"持有一组不可变的值"：
     * 没有行为、不需要继承、不需要延迟计算。{@code record} 恰好把这个语义
     * 写进了类型定义里 —— 任何读代码的人一眼就知道它<b>不会变</b>。
     *
     * <p><b>为什么要重写 {@code toString()}？</b>
     * record 自动生成的版本会把 {@code text} 全文打出来。
     * 快照一旦进入日志，正文就跟着进去了 —— 这正是"窄接口"要防的事。
     * 这里只打元信息。
     */
    private record Memento(String text, int cursor, TextRegion selection, SnapshotMetadata metadata,
                           Object owner) implements Snapshot {

        @Override
        public String toString() {
            return "Memento{" + metadata.display() + "}";
        }
    }
}
