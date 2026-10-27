package com.patterns.behavioral.memento.beginner;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 备忘录入门演示：文本编辑器的撤销与重做。
 *
 * <p>改造前：客户端为了撤销，自己记住 text 和 cursor，再手动写回；
 * 结果忘了 fontSize，而且 Editor 每加一个字段，备份代码都要跟着改。
 *
 * <p>改造后：快照由 Editor 自己拍，History 只负责存取，不关心里面有什么。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：客户端自己记字段 ==========");

        OldEditor editor = new OldEditor();
        editor.text = "设计模式";
        editor.cursor = 4;
        editor.fontSize = 14;
        System.out.println("  初始：" + editor);

        // 客户端自己决定备份哪两个字段
        String savedText = editor.text;
        int savedCursor = editor.cursor;

        editor.text = "设计模式真有趣";
        editor.cursor = 7;
        editor.fontSize = 18;
        System.out.println("  编辑后：" + editor);

        editor.text = savedText;
        editor.cursor = savedCursor;
        System.out.println("  撤销后：" + editor);

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 客户端必须知道 Editor 有哪些字段，还要自己决定备份哪几个；");
        System.out.println("  2. 这次忘了备份 fontSize，撤销后字号停在 18；");
        System.out.println("  3. Editor 以后每加一个字段，所有备份/恢复代码都要跟着改。");
    }

    static class OldEditor {

        String text = "";
        int cursor;
        int fontSize = 14;

        @Override
        public String toString() {
            return "text=「" + text + "」, cursor=" + cursor + ", fontSize=" + fontSize;
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：快照由编辑器自己拍 ==========");

        Editor editor = new Editor();
        History history = new History();
        history.save(editor.snapshot());

        editor.type("设计模式");
        history.save(editor.snapshot());
        System.out.println("  输入「设计模式」：" + editor);

        editor.setFontSize(18);
        history.save(editor.snapshot());
        System.out.println("  字号改为 18：" + editor);

        editor.type("真有趣");
        System.out.println("  输入「真有趣」：" + editor);
        System.out.println("  历史里的快照只能看到预览：" + history.latestPreview());

        undo(editor, history);
        undo(editor, history);
        redo(editor, history);

        System.out.println("  历史：可撤销 " + history.undoCount() + " 步，可重做 " + history.redoCount() + " 步");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每次编辑都存一份完整快照，文本很大时内存吃不消（可用增量/压缩）；");
        System.out.println("  2. 快照数量要有上限，否则历史会无限增长；");
        System.out.println("  3. 只有简单的「改了就回不去」场景，一个 boolean 标记就够，不必上三件套。");
    }

    private static void undo(Editor editor, History history) {
        Editor.Snapshot target = history.undo(editor.snapshot());
        if (target == null) {
            System.out.println("  撤销：没有历史了");
            return;
        }
        editor.restore(target);
        System.out.println("  撤销后：" + editor);
    }

    private static void redo(Editor editor, History history) {
        Editor.Snapshot target = history.redo(editor.snapshot());
        if (target == null) {
            System.out.println("  重做：没有可重做的步骤");
            return;
        }
        editor.restore(target);
        System.out.println("  重做后：" + editor);
    }

    /** Originator：只有它知道该把哪些字段装进快照。 */
    static class Editor {

        private String text = "";
        private int cursor;
        private int fontSize = 14;

        Snapshot snapshot() {
            return new Snapshot(text, cursor, fontSize);
        }

        void restore(Snapshot snapshot) {
            this.text = snapshot.text;
            this.cursor = snapshot.cursor;
            this.fontSize = snapshot.fontSize;
        }

        void type(String input) {
            text = text.substring(0, cursor) + input + text.substring(cursor);
            cursor += input.length();
        }

        void setFontSize(int fontSize) {
            this.fontSize = fontSize;
        }

        @Override
        public String toString() {
            return "text=「" + text + "」, cursor=" + cursor + ", fontSize=" + fontSize;
        }

        /** Memento：对外只给预览，不给完整内容和写入口。 */
        static final class Snapshot {

            private final String text;
            private final int cursor;
            private final int fontSize;

            private Snapshot(String text, int cursor, int fontSize) {
                this.text = text;
                this.cursor = cursor;
                this.fontSize = fontSize;
            }

            String preview() {
                return text.length() <= 6 ? text : text.substring(0, 6) + "…";
            }
        }
    }

    /** Caretaker：只保管快照，不读也不改快照里的内容。 */
    static class History {

        private final Deque<Editor.Snapshot> past = new ArrayDeque<>();
        private final Deque<Editor.Snapshot> future = new ArrayDeque<>();

        void save(Editor.Snapshot snapshot) {
            past.push(snapshot);
            future.clear();
        }

        Editor.Snapshot undo(Editor.Snapshot current) {
            if (past.isEmpty()) {
                return null;
            }
            future.push(current);
            return past.pop();
        }

        Editor.Snapshot redo(Editor.Snapshot current) {
            if (future.isEmpty()) {
                return null;
            }
            past.push(current);
            return future.pop();
        }

        String latestPreview() {
            return past.isEmpty() ? "（空）" : past.peek().preview();
        }

        int undoCount() {
            return past.size();
        }

        int redoCount() {
            return future.size();
        }
    }
}
