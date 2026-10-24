package com.patterns.behavioral.iterator;

/**
 * 遍历顺序。
 *
 * <p><b>为什么遍历顺序是一个枚举，而不是两个方法？</b>
 *
 * <p>直觉写法是给 {@code Workflow} 加两个方法：
 *
 * <pre>
 *   Iterator&lt;WorkflowNode&gt; depthFirstIterator();
 *   Iterator&lt;WorkflowNode&gt; breadthFirstIterator();
 * </pre>
 *
 * <p>这在只有两种顺序时完全够用。但它把"顺序"这个<b>值</b>
 * 固定成了<b>方法名</b>，带来两个后果：
 *
 * <ul>
 *   <li>调用方拿到一个配置项（比如从 YAML 读来的
 *       {@code traversal: breadth-first}）时，只能自己写
 *       {@code if / else} 去二选一，这个 {@code if}
 *       会出现在每一个需要建迭代器的地方；</li>
 *   <li>将来加第三种顺序（比如「按节点类型分组遍历」），
 *       要加方法、改接口、改所有 {@code if}。</li>
 * </ul>
 *
 * <p>做成枚举之后，顺序变成一个可以传递、可以存库、
 * 可以配置的值，{@code Workflow} 内部用一次 {@code switch}
 * 把它翻译成具体实现 —— 那个 {@code switch} 只有一份。
 *
 * <p><b>注意：这个枚举不是模式的一部分。</b>
 *
 * <p>迭代器模式本身不关心有几种顺序。它只是本项目在
 * 「同一个容器可以有多种遍历器」这个事实之上，
 * 给调用方留的一个方便入口。如果只有一种遍历方式，
 * 这个枚举就该删掉 —— 多一层间接就是多一层理解成本。
 *
 * @author design-patterns-pro
 */
public enum TraversalOrder {

    /**
     * 深度优先：沿着一条路走到底，再回头走另一条。
     *
     * <p>输出顺序像"顺着流程往下读"：先看成立分支的整条链路，
     * 再看不成立分支。适合人工核对某一条业务路径。
     */
    DEPTH_FIRST("深度优先"),

    /**
     * 广度优先：一层一层往外扩。
     *
     * <p>输出顺序像"按审批层级展开"：先看第一层判断，
     * 再看所有第二层的步骤，最后才到叶子。
     * 适合看"这个流程一共有几层、每层有哪些节点"。
     */
    BREADTH_FIRST("广度优先");

    private final String label;

    TraversalOrder(String label) {
        this.label = label;
    }

    /**
     * 中文名，用于日志与界面文案。
     *
     * @return 遍历顺序的中文名
     */
    public String label() {
        return label;
    }
}
