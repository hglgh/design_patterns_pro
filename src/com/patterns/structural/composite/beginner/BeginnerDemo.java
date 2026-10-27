package com.patterns.structural.composite.beginner;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合入门演示：文件夹里既有文件，也有子文件夹，还可能出现快捷方式。
 *
 * <p>改造前：文件夹用 {@code List<Object>} 装子项，每个统计/遍历方法都要 instanceof；
 * 改造后：文件和文件夹实现同一个 Node 接口，文件夹递归调用子节点，客户端统一处理。
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
        System.out.println("========== 改造前：文件夹里装 Object，靠 instanceof 分辨 ==========");

        OldFolder reportDir = new OldFolder("报表");
        reportDir.add(new OldFile("日报.csv", 80));
        reportDir.add(new OldFile("月报.csv", 200));

        OldFolder root = new OldFolder("根目录");
        root.add(new OldFile("订单.txt", 120));
        root.add(reportDir);
        root.add(new OldFile("说明.md", 5));

        // 新需求：加一个快捷方式
        root.add(new OldShortcut("报表快捷方式", reportDir));

        root.print("");
        System.out.println("  总大小：" + root.sizeKb() + "KB");
        System.out.println("  ↑ 快捷方式既没被统计，也没被打印：两个方法都不认识它。");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 每个遍历方法都要写一遍 instanceof，新增一种节点要改所有方法；");
        System.out.println("  2. 客户端拿到的是 Object，必须强转，类型错误运行时才发现；");
        System.out.println("  3. 「文件」和「文件夹」没有共同接口，无法统一递归。");
    }

    static class OldFile {
        final String name;
        final int sizeKb;

        OldFile(String name, int sizeKb) {
            this.name = name;
            this.sizeKb = sizeKb;
        }
    }

    static class OldShortcut {
        final String name;
        final OldFolder target;

        OldShortcut(String name, OldFolder target) {
            this.name = name;
            this.target = target;
        }
    }

    static class OldFolder {

        final String name;
        final List<Object> items = new ArrayList<>();

        OldFolder(String name) {
            this.name = name;
        }

        void add(Object item) {
            items.add(item);
        }

        int sizeKb() {
            int total = 0;
            for (Object item : items) {
                if (item instanceof OldFile file) {
                    total += file.sizeKb;
                } else if (item instanceof OldFolder folder) {
                    total += folder.sizeKb();
                }
                // OldShortcut 没有被处理 —— 新增类型时这里就会漏
            }
            return total;
        }

        void print(String indent) {
            System.out.println(indent + name + "/");
            for (Object item : items) {
                if (item instanceof OldFile file) {
                    System.out.println(indent + "  " + file.name + " (" + file.sizeKb + "KB)");
                } else if (item instanceof OldFolder folder) {
                    folder.print(indent + "  ");
                }
                // 同样漏掉了快捷方式
            }
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：文件和文件夹都是 Node ==========");

        FolderNode reportDir = new FolderNode("报表");
        reportDir.add(new FileNode("日报.csv", 80));
        reportDir.add(new FileNode("月报.csv", 200));

        FolderNode root = new FolderNode("根目录");
        root.add(new FileNode("订单.txt", 120));
        root.add(reportDir);
        root.add(new FileNode("说明.md", 5));

        System.out.println("  —— 新增快捷方式：只加一个类，FolderNode 的统计和打印都不改 ——");
        root.add(new ShortcutNode("报表快捷方式", reportDir));

        root.print("");
        System.out.println("  总大小：" + root.sizeKb() + "KB");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每个节点都要实现同一套接口，简单文件系统会显得重；");
        System.out.println("  2. 想给所有节点新增一种「操作」（例如导出 JSON），仍要改接口和所有节点；");
        System.out.println("     这正是组合模式与访问者模式的分界。");
    }

    /** 叶子节点和容器节点的共同接口：客户端只认它。 */
    interface Node {
        int sizeKb();

        void print(String indent);
    }

    static class FileNode implements Node {

        private final String name;
        private final int sizeKb;

        FileNode(String name, int sizeKb) {
            this.name = name;
            this.sizeKb = sizeKb;
        }

        @Override
        public int sizeKb() {
            return sizeKb;
        }

        @Override
        public void print(String indent) {
            System.out.println(indent + name + " (" + sizeKb + "KB)");
        }
    }

    /** 容器节点：它不关心子节点是文件、文件夹还是快捷方式，统一调 Node 接口。 */
    static class FolderNode implements Node {

        private final String name;
        private final List<Node> children = new ArrayList<>();

        FolderNode(String name) {
            this.name = name;
        }

        void add(Node child) {
            children.add(child);
        }

        @Override
        public int sizeKb() {
            int total = 0;
            for (Node child : children) {
                total += child.sizeKb();       // 递归由多态完成
            }
            return total;
        }

        @Override
        public void print(String indent) {
            System.out.println(indent + name + "/");
            for (Node child : children) {
                child.print(indent + "  ");
            }
        }
    }

    // ---- 新节点类型：只新增一个类 ----

    static class ShortcutNode implements Node {

        private final String name;
        private final Node target;

        ShortcutNode(String name, Node target) {
            this.name = name;
            this.target = target;
        }

        @Override
        public int sizeKb() {
            return 1;      // 快捷方式本身占 1KB，不重复计算目标大小
        }

        @Override
        public void print(String indent) {
            System.out.println(indent + name + " -> 快捷方式 (1KB)");
        }
    }
}
