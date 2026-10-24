package com.patterns.structural.composite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 组合节点（Composite）：文件夹。持有子节点集合，把请求<b>递归委托</b>给孩子。
 *
 * <p><b>为什么这个类叫 Directory 而不叫 File？</b>
 * 原项目 demo2 中叶子节点命名为 {@code File}，与 JDK 的 {@link java.io.File}
 * 同名。一旦某个文件同时需要操作真实磁盘路径，就会出现
 * {@code import java.io.File} 与本项目 File 的命名冲突，只能写全限定名，可读性很差。
 * 因此这里把容器节点命名为 {@code Directory}、叶子节点命名为 {@code FileLeaf}，
 * 语义更准确，也彻底避开 JDK 类名。
 *
 * <p><b>核心机制：递归委托。</b>
 * {@link #size()} 的实现只有一句"遍历孩子，累加 child.size()"。
 * 它不关心孩子到底是文件还是子目录——因为 {@link FileSystemNode} 接口保证了
 * 两种节点都有 size() 方法。递归的深度由对象树自己决定，代码完全不用改。
 *
 * @author design-patterns-pro
 */
public final class Directory implements FileSystemNode {

    private final String name;

    /**
     * 子节点集合。<b>声明类型是 FileSystemNode 而不是 Directory</b>，
     * 这样才能同时容纳文件和子目录——这是组合模式的必要条件。
     */
    private final List<FileSystemNode> children = new ArrayList<>();

    /**
     * @param name 目录名，如 {@code "文档"}
     */
    public Directory(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("目录名不能为空");
        }
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * 添加子节点（文件或子目录均可）。
     *
     * <p>注意：<b>add 方法只存在于 Directory，不在 FileSystemNode 接口中</b>。
     * 这就是"安全式组合模式"——不允许对叶子节点调用 add，
     * 编译期即可拦截"往文件里塞子文件"这种无意义操作。
     *
     * @param child 子节点
     * @return this，支持链式调用 {@code dir.add(a).add(b)}
     */
    public Directory add(FileSystemNode child) {
        if (child == null) {
            throw new IllegalArgumentException("子节点不能为 null");
        }
        // 防环校验：把自己或自己的祖先加进来，会让 size()/print() 无限递归导致 StackOverflowError。
        // 教学 Demo 里这一步常被忽略，但真实项目（如组织架构、菜单树）必须有。
        if (child == this || (child instanceof Directory dir) && dir.contains(this)) {
            throw new IllegalArgumentException("不能将目录添加到自身或其子目录下，会形成循环引用");
        }
        children.add(child);
        return this;
    }

    /**
     * 移除子节点。
     *
     * @param child 待移除的节点
     * @return 是否移除成功
     */
    public boolean remove(FileSystemNode child) {
        return children.remove(child);
    }

    /**
     * 返回只读视图，防止外部绕过 {@link #add} 的防环校验直接修改集合。
     *
     * @return 不可修改的子节点列表
     */
    public List<FileSystemNode> children() {
        return Collections.unmodifiableList(children);
    }

    /**
     * 递归判断 target 是否为当前目录的子孙节点。
     */
    private boolean contains(FileSystemNode target) {
        for (FileSystemNode child : children) {
            if (child == target) {
                return true;
            }
            if (child instanceof Directory dir && dir.contains(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 递归累加所有子孙节点的大小。
     *
     * <p><b>这一行代码就是组合模式的精华</b>：
     * {@code child.size()} 对文件和子目录一视同仁，
     * 子目录会继续向下递归，客户端只需调用一次 {@code root.size()}。
     */
    @Override
    public long size() {
        long total = 0;
        for (FileSystemNode child : children) {
            total += child.size();
        }
        return total;
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + "└─ [目录] " + name + " (" + size() + " KB)");
        String childIndent = indent + "   ";
        for (FileSystemNode child : children) {
            child.print(childIndent);
        }
    }

    /**
     * 递归查找指定名称的节点，演示"统一接口"带来的便利：
     * 查找逻辑同样不需要区分文件与目录。
     *
     * @param targetName 目标名称
     * @return 第一个匹配的节点，不存在则返回 {@link Optional#empty()}
     */
    public Optional<FileSystemNode> find(String targetName) {
        for (FileSystemNode child : children) {
            if (child.name().equals(targetName)) {
                return Optional.of(child);
            }
            if (child instanceof Directory dir) {
                Optional<FileSystemNode> found = dir.find(targetName);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 统计整棵树的叶子（文件）数量。
     *
     * <p>这里用 switch 模式匹配 + sealed 接口：因为 FileSystemNode 是 sealed 的，
     * 编译器知道只有 FileLeaf 和 Directory 两种实现，两个 case 即穷尽，无需 default。
     *
     * @return 文件总数
     */
    public int fileCount() {
        int count = 0;
        for (FileSystemNode child : children) {
            count += switch (child) {
                case FileLeaf ignored -> 1;
                case Directory dir -> dir.fileCount();
            };
        }
        return count;
    }
}
