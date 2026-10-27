package com.patterns.behavioral.visitor.beginner;

import java.util.ArrayList;
import java.util.List;

/**
 * 访问者入门演示：一份文档的导出、统计、敏感词检查。
 *
 * <p>改造前：每个元素类都把 export/check 写在身上，加一种操作就要改所有元素类。
 * 改造后：元素只负责 accept，操作住在访问者里，新增操作只加一个访问者。
 *
 * <p>代价：新增元素类型要改 Visitor 接口和所有访问者，方向正好相反。
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
        System.out.println("========== 改造前：每种操作都写在元素类里 ==========");

        OldText text = new OldText("内部季度总结：内部营收增长 12%");
        OldImage image = new OldImage("增长曲线", 240);
        OldTable table = new OldTable(4, 3);

        text.export();
        image.export();
        table.export();

        text.check();
        image.check();
        table.check();

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 新增「字数统计」要同时改 Text/Image/Table 三个类；");
        System.out.println("  2. 导出、检查、统计的代码分散在三个元素类里，重复且难统一；");
        System.out.println("  3. 元素类越来越像工具箱，和「文档节点」本身的职责无关。");
    }

    static class OldText {

        private final String content;

        OldText(String content) {
            this.content = content;
        }

        void export() {
            System.out.println("    [文本] " + content);
        }

        void check() {
            System.out.println("    [文本] 检查敏感词");
        }
    }

    static class OldImage {

        private final String name;
        private final int sizeKb;

        OldImage(String name, int sizeKb) {
            this.name = name;
            this.sizeKb = sizeKb;
        }

        void export() {
            System.out.println("    [图片] " + name + "（" + sizeKb + "KB）");
        }

        void check() {
            System.out.println("    [图片] 无需检查");
        }
    }

    static class OldTable {

        private final int rows;
        private final int cols;

        OldTable(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
        }

        void export() {
            System.out.println("    [表格] " + rows + " 行 × " + cols + " 列");
        }

        void check() {
            System.out.println("    [表格] 无需检查");
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：元素只 accept，操作住在访问者里 ==========");

        List<DocumentElement> document = new ArrayList<>();
        document.add(new TextElement("内部季度总结：内部营收增长 12%"));
        document.add(new ImageElement("增长曲线", 240));
        document.add(new TableElement(4, 3));

        System.out.println("  —— 导出 ——");
        visitAll(document, new ExportVisitor());

        System.out.println("  —— 统计 ——");
        StatsVisitor stats = new StatsVisitor();
        visitAll(document, stats);
        stats.report();

        System.out.println("  —— 敏感词「内部」——");
        SensitiveWordVisitor sensitive = new SensitiveWordVisitor("内部");
        visitAll(document, sensitive);
        System.out.println("    文本命中 " + sensitive.hits() + " 次");

        System.out.println("  —— 新增「字数统计」访问者，元素类不改 ——");
        WordCountVisitor wordCount = new WordCountVisitor();
        visitAll(document, wordCount);
        System.out.println("    总字数：" + wordCount.count());

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 新增元素类型（比如视频）要改 Visitor 接口和所有访问者；");
        System.out.println("  2. 访问者要读元素数据，元素得开 getter，封装会松一点；");
        System.out.println("  3. 元素类型稳定、操作经常增加时，访问者才划算。");
    }

    private static void visitAll(List<DocumentElement> document, Visitor visitor) {
        for (DocumentElement element : document) {
            element.accept(visitor);
        }
    }

    /** 元素接口：只负责把「自己」交给访问者。 */
    interface DocumentElement {
        void accept(Visitor visitor);
    }

    static class TextElement implements DocumentElement {

        private final String content;

        TextElement(String content) {
            this.content = content;
        }

        String content() {
            return content;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visit(this);
        }
    }

    static class ImageElement implements DocumentElement {

        private final String name;
        private final int sizeKb;

        ImageElement(String name, int sizeKb) {
            this.name = name;
            this.sizeKb = sizeKb;
        }

        String name() {
            return name;
        }

        int sizeKb() {
            return sizeKb;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visit(this);
        }
    }

    static class TableElement implements DocumentElement {

        private final int rows;
        private final int cols;

        TableElement(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
        }

        int rows() {
            return rows;
        }

        int cols() {
            return cols;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visit(this);
        }
    }

    /** 访问者接口：每种元素一个 visit 重载，调用时按元素真实类型分派。 */
    interface Visitor {

        void visit(TextElement element);

        void visit(ImageElement element);

        void visit(TableElement element);
    }

    static class ExportVisitor implements Visitor {

        @Override
        public void visit(TextElement element) {
            System.out.println("    [文本] " + element.content());
        }

        @Override
        public void visit(ImageElement element) {
            System.out.println("    [图片] " + element.name() + "（" + element.sizeKb() + "KB）");
        }

        @Override
        public void visit(TableElement element) {
            System.out.println("    [表格] " + element.rows() + " 行 × " + element.cols() + " 列");
        }
    }

    static class StatsVisitor implements Visitor {

        private int textCount;
        private int charCount;
        private int imageCount;
        private int imageKb;
        private int tableCount;
        private int cellCount;

        @Override
        public void visit(TextElement element) {
            textCount++;
            charCount += element.content().length();
        }

        @Override
        public void visit(ImageElement element) {
            imageCount++;
            imageKb += element.sizeKb();
        }

        @Override
        public void visit(TableElement element) {
            tableCount++;
            cellCount += element.rows() * element.cols();
        }

        void report() {
            System.out.println("    文本 " + textCount + " 段 / " + charCount + " 字；"
                    + "图片 " + imageCount + " 张 / " + imageKb + "KB；"
                    + "表格 " + tableCount + " 个 / " + cellCount + " 格");
        }
    }

    static class SensitiveWordVisitor implements Visitor {

        private final String word;
        private int hits;

        SensitiveWordVisitor(String word) {
            this.word = word;
        }

        @Override
        public void visit(TextElement element) {
            String content = element.content();
            int index = content.indexOf(word);
            while (index >= 0) {
                hits++;
                index = content.indexOf(word, index + word.length());
            }
        }

        @Override
        public void visit(ImageElement element) {
            // 图片没有文本，不做检查
        }

        @Override
        public void visit(TableElement element) {
            // 表格没有文本，不做检查
        }

        int hits() {
            return hits;
        }
    }

    /** 新操作：只新增一个访问者，三个元素类 0 修改。 */
    static class WordCountVisitor implements Visitor {

        private int count;

        @Override
        public void visit(TextElement element) {
            count += element.content().length();
        }

        @Override
        public void visit(ImageElement element) {
            // 图片不计字数
        }

        @Override
        public void visit(TableElement element) {
            // 表格不计字数
        }

        int count() {
            return count;
        }
    }
}
