package com.patterns.structural.composite;

import java.util.Optional;

/**
 * 客户端（Client）：构建一棵目录树，并<b>用同一套代码</b>操作整棵树。
 *
 * <p>运行方式：
 * <pre>
 *   javac --release 25 -d out $(find src -name "*.java")
 *   java -cp out com.patterns.structural.composite.Client
 * </pre>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        Directory root = buildProjectTree();

        System.out.println("=== 1. 打印目录结构（递归遍历整棵树）===");
        root.print("");

        System.out.println();
        System.out.println("=== 2. 统一调用 size()：客户端不关心是文件还是目录 ===");
        // 关键演示：这两行调用形式完全相同，一个作用于叶子，一个作用于整棵树
        FileSystemNode leaf = new FileLeaf("独立文件.txt", 8);
        System.out.println("单个文件 '" + leaf.name() + "' 大小 = " + leaf.size() + " KB");
        System.out.println("整个项目 '" + root.name() + "' 大小 = " + root.size() + " KB");

        System.out.println();
        System.out.println("=== 3. 递归查找深层文件 ===");
        Optional<FileSystemNode> found = root.find("架构图.png");
        found.ifPresentOrElse(
                node -> System.out.println("找到了：" + node.name() + "，大小 " + node.size() + " KB"),
                () -> System.out.println("未找到 架构图.png"));
        System.out.println("查找不存在的文件 -> " + root.find("不存在.zip"));

        System.out.println();
        System.out.println("=== 4. 统计文件总数（sealed + switch 模式匹配）===");
        System.out.println("项目下共有 " + root.fileCount() + " 个文件");

        System.out.println();
        System.out.println("=== 5. 动态增删：树结构变化后，所有统计自动跟着变 ===");
        Directory tests = new Directory("tests");
        tests.add(new FileLeaf("PatternTest.java", 12));
        root.add(tests);
        System.out.println("新增 tests 目录后 -> 文件数 = " + root.fileCount()
                + "，总大小 = " + root.size() + " KB");
        root.remove(tests);
        System.out.println("移除 tests 目录后 -> 文件数 = " + root.fileCount()
                + "，总大小 = " + root.size() + " KB");

        System.out.println();
        System.out.println("=== 6. 防环校验：把父目录加到自己孩子里 ===");
        try {
            root.add(root);
        } catch (IllegalArgumentException e) {
            System.out.println("已拦截非法操作：" + e.getMessage());
        }
    }

    /**
     * 构建一棵模拟的项目目录树：
     *
     * <pre>
     * design-patterns/
     * ├─ src/
     * │  ├─ Main.java
     * │  └─ docs/
     * │     ├─ 需求说明书.docx
     * │     └─ 架构图.png
     * ├─ README.md
     * └─ pom.xml
     * </pre>
     *
     * @return 根目录
     */
    private static Directory buildProjectTree() {
        Directory docs = new Directory("docs");
        docs.add(new FileLeaf("需求说明书.docx", 120))
                .add(new FileLeaf("架构图.png", 340));

        Directory src = new Directory("src");
        src.add(new FileLeaf("Main.java", 15))
                .add(docs);

        Directory root = new Directory("design-patterns");
        root.add(src)
                .add(new FileLeaf("README.md", 6))
                .add(new FileLeaf("pom.xml", 3));
        return root;
    }
}
