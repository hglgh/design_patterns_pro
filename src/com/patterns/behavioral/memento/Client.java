package com.patterns.behavioral.memento;

import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * 六段 Demo，把备忘录模式的每一个关键决定都跑一遍。
 *
 * <p>场景：客服在工单里写回复草稿，反复修改，需要撤销与重做。
 *
 * <p><b>为什么所有时钟都是 {@link SteppedClock} 而不是 {@code Instant::now}？</b>
 * 为了让输出可复现 —— 同一份代码连跑三次，字节完全一致。
 * 这是本项目所有 Demo 的共同要求，理由见 {@link SteppedClock}。
 *
 * <p>六段各自验证什么：
 * <ol>
 *   <li><b>Demo 1</b>：撤销 / 重做的基本闭环，外加撤销面板实时刷新；</li>
 *   <li><b>Demo 2</b>：一段操作 = 一步撤销（原项目做不到）；</li>
 *   <li><b>Demo 3</b>：编辑中途失败自动回滚（原项目会留半截）；</li>
 *   <li><b>Demo 4</b>：容量上限淘汰最老快照（原项目会无限增长）；</li>
 *   <li><b>Demo 5</b>：快照不可伪造、不可串档（原项目两者皆可）；</li>
 *   <li><b>Demo 6</b>：撤销到底返回 false、新编辑清空重做栈。</li>
 * </ol>
 *
 * @author design-patterns-pro
 */
public final class Client {

    public static void main(String[] args) throws Exception {
        demo1BasicUndoRedo();
        demo2AtomicEdit();
        demo3RollbackOnFailure();
        demo4CapacityEviction();
        demo5SnapshotCannotBeForged();
        demo6UndoBottomAndRedoReset();
        System.out.println();
        System.out.println("[DONE] 六段 Demo 全部通过");
    }

    // ============================================================
    // Demo 1：撤销 / 重做的基本闭环
    // ============================================================

    private static void demo1BasicUndoRedo() {
        section("Demo 1：撤销与重做");

        Editor editor = new Editor("客服：您好，您的订单");
        SnapshotHistory history = history(editor, 10);
        history.addListener(new ConsoleHistoryListener("面板"));

        System.out.println("  初始 → " + editor.describe());

        System.out.println("  [编辑] 末尾补「已发货」");
        history.edit(EditKind.TYPING, "末尾补「已发货」", e -> e.type("已发货"));
        System.out.println("  正文 → " + editor.describe());

        System.out.println("  [编辑] 补「预计明天送达」");
        history.edit(EditKind.TYPING, "补「预计明天送达」", e -> e.type("，预计明天送达"));
        System.out.println("  正文 → " + editor.describe());

        System.out.println("  [编辑] 补句号");
        history.edit(EditKind.TYPING, "补句号", e -> e.type("。"));
        System.out.println("  正文 → " + editor.describe());

        System.out.println("  连续撤销三次：");
        for (int i = 1; i <= 3; i++) {
            boolean ok = history.undo();
            System.out.println("    undo#" + i + " = " + ok + " → " + editor.text());
        }

        System.out.println("  连续重做两次：");
        for (int i = 1; i <= 2; i++) {
            boolean ok = history.redo();
            System.out.println("    redo#" + i + " = " + ok + " → " + editor.text());
        }

        System.out.println("  统计 " + history.stats().summary());
    }

    // ============================================================
    // Demo 2：一段操作 = 一步撤销
    // ============================================================

    private static void demo2AtomicEdit() {
        section("Demo 2：一段操作 = 一步撤销");

        Editor editor = new Editor("");
        SnapshotHistory history = history(editor, 10);

        history.edit(EditKind.TYPING, "一次敲完「合规审核」四个字", e -> {
            for (char c : "合规审核".toCharArray()) {
                e.type(String.valueOf(c));
            }
        });
        System.out.println("  一次 edit 内敲 4 个字 → 「" + editor.text() + "」");

        history.edit(EditKind.TYPING, "再补「完成」", e -> e.type("完成"));
        System.out.println("  第二次 edit 后 → 「" + editor.text() + "」");
        System.out.println("  统计 " + history.stats().summary());

        System.out.println("  注意：只敲了 2 次 edit，尽管内部调用了 5 次 type()");
        System.out.println("  连续撤销：");
        for (int i = 1; i <= 3; i++) {
            boolean ok = history.undo();
            System.out.println("    undo#" + i + " = " + ok + " → 「" + editor.text() + "」");
        }
        System.out.println("  第 3 次返回 false：已经退到最早保留的版本，不会再往后退");
        System.out.println("  原项目把「什么时候拍快照」交给调用方 —— 只要有人在循环里");
        System.out.println("  setContent 后跟一次 push，就会变成 N 步撤销；edit() 把边界钉死在调用处");
    }

    // ============================================================
    // Demo 3：编辑中途失败，自动回滚
    // ============================================================

    private static void demo3RollbackOnFailure() {
        section("Demo 3：编辑中途失败，自动回滚");

        Editor editor = new Editor("原稿：本条款有效");
        SnapshotHistory history = history(editor, 10);

        history.edit(EditKind.TYPING, "补一句生效条件", e -> e.type("，自签署日起生效"));
        System.out.println("  正常编辑后 → 「" + editor.text() + "」");
        System.out.println("  失败前 " + history.stats().summary());

        try {
            history.edit(EditKind.REPLACE, "整段替换（中途校验失败）", e -> {
                e.replaceAll("整段替换后的内容");
                throw new IllegalStateException("缺少签署方，不允许替换");
            });
        } catch (IllegalStateException ex) {
            System.out.println("  捕获 " + ex.getClass().getSimpleName() + "：" + ex.getMessage());
        }

        System.out.println("  失败后 → 「" + editor.text() + "」");
        System.out.println("  失败后 " + history.stats().summary());
        System.out.println("  正文回到进门前？" + "原稿：本条款有效，自签署日起生效".equals(editor.text()));
        System.out.println("  这篇快照没有入栈，所以「可撤」步数没变");
        System.out.println("  「已拍」多了 1：快门按过了，只是底片没进相册");
    }

    // ============================================================
    // Demo 4：容量上限，最老的快照被淘汰
    // ============================================================

    private static void demo4CapacityEviction() {
        section("Demo 4：容量上限 3，最老的快照被淘汰");

        Editor editor = new Editor("第0版");
        SnapshotHistory history = history(editor, 3);

        for (int i = 1; i <= 4; i++) {
            final int version = i;
            history.edit(EditKind.REPLACE, "改成第 " + version + " 版",
                    e -> e.replaceAll("第" + version + "版"));
        }
        System.out.println("  连做 4 次编辑后 → 「" + editor.text() + "」");
        System.out.println("  统计 " + history.stats().summary());
        System.out.println("  「淘汰 2」= 第0版和第1版的快照已被挤出栈");

        System.out.println("  连续撤销：");
        for (int i = 1; i <= 4; i++) {
            boolean ok = history.undo();
            System.out.println("    undo#" + i + " = " + ok + " → 「" + editor.text() + "」");
        }
        System.out.println("  只能撤到第2版为止 —— 这就是容量上限的代价，必须让用户知道");
    }

    // ============================================================
    // Demo 5：快照不可伪造、不可串档
    // ============================================================

    private static void demo5SnapshotCannotBeForged() throws ClassNotFoundException {
        section("Demo 5：快照不能伪造，也不能串档");

        Class<?> memento = Class.forName("com.patterns.behavioral.memento.Editor$Memento");
        System.out.println("  备忘录的运行时类 = " + memento.getName());
        System.out.println("  是 private？" + Modifier.isPrivate(memento.getModifiers()));
        System.out.println("  是 record？" + memento.isRecord());
        System.out.println("  外部代码写不出 new Editor.Memento(...)：这个类型在源码里不可见");

        Editor a = new Editor("文档 A");
        Editor b = new Editor("文档 B");

        Snapshot fromA = a.snapshot(new SnapshotMetadata(999, EditKind.TYPING, "手工拍的", clock().get()));
        System.out.println("  a 自己拍一张 → " + fromA.metadata().display());

        a.restore(fromA);
        System.out.println("  a.restore(自己的) 成功 → 「" + a.text() + "」");

        try {
            b.restore(fromA);
            System.out.println("  b.restore(a 的) 竟然成功（不该发生）");
        } catch (SnapshotRejectedException ex) {
            System.out.println("  b.restore(a 的) 被拒 → " + ex.getMessage());
        }

        System.out.println("  窄接口暴露的方法只有 " + Arrays.toString(Snapshot.class.getMethods()));
        System.out.println("  注意：metadata 里的 999 是伪造得出来的 —— 元信息可以编，正文编不了");
    }

    // ============================================================
    // Demo 6：撤销到底 + 新编辑清空重做栈
    // ============================================================

    private static void demo6UndoBottomAndRedoReset() {
        section("Demo 6：撤销到底，新编辑清空重做栈");

        Editor editor = new Editor("第一稿");
        SnapshotHistory history = history(editor, 10);

        history.edit(EditKind.REPLACE, "改成第二稿", e -> e.replaceAll("第二稿"));
        history.edit(EditKind.REPLACE, "改成第三稿", e -> e.replaceAll("第三稿"));
        System.out.println("  两次编辑后 → 「" + editor.text() + "」");
        System.out.println("  统计 " + history.stats().summary());

        System.out.println("  一路撤销：");
        int step = 0;
        while (history.undo()) {
            step++;
            System.out.println("    第 " + step + " 次 undo = true → 「" + editor.text() + "」");
        }
        System.out.println("    再来一次 undo = " + history.undo()
                + " → 已到最早保留的版本「" + editor.text() + "」");
        System.out.println("  统计 " + history.stats().summary());

        System.out.println("  重做一次 = " + history.redo() + " → 「" + editor.text() + "」");
        System.out.println("  统计 " + history.stats().summary());

        history.edit(EditKind.REPLACE, "另起一段，重做作废", e -> e.replaceAll("新的一段"));
        System.out.println("  新编辑后 → 「" + editor.text() + "」");
        System.out.println("  统计 " + history.stats().summary());
        System.out.println("  再 redo = " + history.redo() + " → 重做栈已被新编辑清空");
    }

    // ============================================================
    // 工具
    // ============================================================

    /**
     * 建一份历史，时钟固定。
     *
     * @param editor 被记录的编辑器
     * @param depth  撤销栈深度
     * @return 新的历史
     */
    private static SnapshotHistory history(Editor editor, int depth) {
        return new SnapshotHistory(editor, depth, clock());
    }

    /**
     * 从 {@code 2025-03-14T09:00:00Z} 起，每拍一张前进 500 毫秒。
     *
     * <p>基点和步长都是固定的，所以同一段 Demo 打出来的时间戳永远一样。
     *
     * @return 确定性时钟
     */
    private static SteppedClock clock() {
        return new SteppedClock(Instant.parse("2025-03-14T09:00:00Z"), Duration.ofMillis(500));
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private Client() {
    }
}
