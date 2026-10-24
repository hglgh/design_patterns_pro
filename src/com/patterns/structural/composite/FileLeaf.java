package com.patterns.structural.composite;

/**
 * 叶子节点（Leaf）：文件。没有子节点，是递归的终点。
 *
 * <p><b>为什么用 record？</b>
 * 文件节点是纯数据载体——名字 + 大小，创建后不可变，不需要继承、不需要 setter。
 * record 自动生成 {@code name()} / {@code size()} 访问器，
 * <b>恰好与 {@link FileSystemNode} 接口声明的方法签名一致</b>，
 * 所以实现接口时一行代码都不用写，还免费获得 equals/hashCode/toString。
 *
 * <p><b>为什么叶子节点也必须实现 size()？</b>
 * 这是组合模式的关键设计：把"计算大小"这个能力下沉到统一接口，
 * {@link Directory#size()} 才能不加判断地写 {@code child.size()}。
 * 如果 size() 只在 Directory 上有，Directory 递归时就得
 * {@code if (child instanceof Directory d) ... else ...}，递归逻辑立刻变脏。
 *
 * @param name 文件名，如 {@code "readme.txt"}
 * @param size 文件大小（KB），必须非负
 * @author design-patterns-pro
 */
public record FileLeaf(String name, long size) implements FileSystemNode {

    /**
     * 紧凑构造器：在字段赋值前做参数校验。
     *
     * <p>record 的紧凑构造器不需要写参数列表和 {@code this.x = x}，
     * 编译器会在方法体结束后自动完成赋值——这是 JDK 16+ 的语法糖。
     */
    public FileLeaf {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (size < 0) {
            throw new IllegalArgumentException("文件大小不能为负数：" + size);
        }
    }

    @Override
    public void print(String indent) {
        // 叶子节点直接打印自己，递归到此结束
        System.out.println(indent + "├─ " + name + " (" + size + " KB)");
    }
}
