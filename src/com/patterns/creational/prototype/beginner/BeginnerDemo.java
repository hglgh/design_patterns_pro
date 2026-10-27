package com.patterns.creational.prototype.beginner;

import java.util.ArrayList;
import java.util.List;

/**
 * 原型入门演示：从一个文档模板复制出多份草稿。
 *
 * <p>改造前：手动 new 再逐个字段拷贝，浅拷贝让两份草稿共用同一个章节列表和样式对象；
 * 改造后：模板自己提供 copy()，对可变字段做深拷贝，复制品和原件互不影响。
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
        System.out.println("========== 改造前：手动拷贝字段 ==========");

        OldDocument template = new OldDocument("周报模板", new ArrayList<>(List.of("本周完成", "下周计划")), new OldStyle(12));

        // 调用方照着自己记得的字段抄一遍
        OldDocument draft = new OldDocument(template.title, template.sections, template.style);

        draft.sections.add("风险与求助");
        draft.style.fontSize = 20;

        System.out.println("  模板章节：" + template.sections);
        System.out.println("  草稿章节：" + draft.sections);
        System.out.println("  模板字号：" + template.style.fontSize + "，草稿字号：" + draft.style.fontSize);
        System.out.println("  ↑ 改草稿把模板也改了：sections 和 style 指向同一份对象。");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 每加一个字段，所有拷贝代码都要跟着改，漏一个就丢数据；");
        System.out.println("  2. 复制规则（哪些字段要深拷贝）散落在调用方，而不是文档自己知道；");
        System.out.println("  3. 浅拷贝不会报错，问题要到「模板被改坏」时才暴露。");
    }

    static class OldStyle {
        int fontSize;

        OldStyle(int fontSize) {
            this.fontSize = fontSize;
        }
    }

    static class OldDocument {

        String title;
        List<String> sections;
        OldStyle style;

        OldDocument(String title, List<String> sections, OldStyle style) {
            this.title = title;
            this.sections = sections;
            this.style = style;
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：模板提供 copy() ==========");

        Document template = new Document("周报模板", new ArrayList<>(List.of("本周完成", "下周计划")), new Style(12));
        Document draft = template.copy();

        draft.addSection("风险与求助");
        draft.getStyle().setFontSize(20);
        draft.setTitle("第 42 周周报");

        System.out.println("  模板：" + template);
        System.out.println("  草稿：" + draft);
        System.out.println("  ↑ 草稿怎么改，模板都不动。");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. copy() 要自己决定每个字段是深拷贝还是共享，写错一个就出隐性 bug；");
        System.out.println("  2. 对象图很大或有循环引用时，深拷贝成本高、实现也更复杂；");
        System.out.println("  3. 如果对象本来就可以设计成不可变，直接共享最安全，根本不需要拷贝。");
    }

    static class Style {

        private int fontSize;

        Style(int fontSize) {
            this.fontSize = fontSize;
        }

        Style copy() {
            return new Style(fontSize);
        }

        int getFontSize() {
            return fontSize;
        }

        void setFontSize(int fontSize) {
            this.fontSize = fontSize;
        }
    }

    static class Document {

        private String title;
        private final List<String> sections;
        private final Style style;

        Document(String title, List<String> sections, Style style) {
            this.title = title;
            this.sections = sections;
            this.style = style;
        }

        /** 复制入口：调用方不需要知道 Document 里到底有几个字段。 */
        Document copy() {
            List<String> sectionsCopy = new ArrayList<>(sections);   // 深拷贝可变集合
            Style styleCopy = style.copy();                          // 深拷贝可变样式
            return new Document(title, sectionsCopy, styleCopy);
        }

        void addSection(String section) {
            sections.add(section);
        }

        void setTitle(String title) {
            this.title = title;
        }

        Style getStyle() {
            return style;
        }

        @Override
        public String toString() {
            return "Document{title=" + title + ", sections=" + sections
                    + ", fontSize=" + style.getFontSize() + "}";
        }
    }
}
