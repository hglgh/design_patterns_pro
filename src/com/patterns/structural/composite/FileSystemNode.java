package com.patterns.structural.composite;

/**
 * 抽象组件（Component）：定义"文件"与"文件夹"的统一契约。
 *
 * <p><b>为什么需要这个接口？</b>
 * 组合模式的核心价值是：客户端调用 {@code node.size()} 时，
 * <b>不需要知道也不需要关心</b>眼前这个对象是一个文件还是一整个目录树。
 * 没有这个统一接口，客户端就必须写
 * {@code if (node instanceof FileLeaf) ... else ...} 到处判断类型，
 * 递归逻辑会散落在每一个调用点上。
 *
 * <p><b>为什么用 sealed？</b>
 * 文件系统节点在业务上只有两种：叶子（文件）和容器（目录）。
 * {@code sealed} 把这个约束写进类型系统，配合 switch 模式匹配可以获得
 * 编译期穷尽性检查——将来新增第三种节点，所有 switch 都会编译报错提醒你补全。
 *
 * <p><b>关于 add/remove 的取舍（安全式 vs 透明式）：</b>
 * 这里采用<b>安全式</b>组合模式——{@code add}/{@code remove} 只声明在
 * {@link Directory} 上，不放进本接口。因为"往一个文件里添加子文件"在业务上是
 * 无意义的操作，若放进接口，{@link FileLeaf} 只能抛
 * {@code UnsupportedOperationException}，把编译期能发现的错误推迟到运行期。
 * 代价是：客户端要往节点里添加子节点时，必须先确认它是 Directory。
 * 透明式的写法与取舍详见 README.md 第 8 节。
 *
 * @author design-patterns-pro
 * @see FileLeaf   叶子节点：文件
 * @see Directory  组合节点：文件夹
 */
public sealed interface FileSystemNode permits FileLeaf, Directory {

    /**
     * 节点名称（文件名或目录名）。
     *
     * @return 名称，永不为 null
     */
    String name();

    /**
     * 节点占用空间，单位 KB。
     *
     * <p>叶子节点返回自身大小；组合节点<b>递归累加</b>所有子孙节点的大小。
     * 客户端调用时完全不必关心底层是几层嵌套——这正是组合模式的意义。
     *
     * @return 占用空间（KB），非负数
     */
    long size();

    /**
     * 以树形缩进打印自身及所有子孙节点。
     *
     * @param indent 当前层级的缩进前缀，根节点传 {@code ""}
     */
    void print(String indent);
}
