package com.patterns.behavioral.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 过滤迭代器：只让满足条件的节点通过。
 *
 * <p><b>一、为什么它是"装饰器"而不是"第四个遍历器"？</b>
 *
 * <p>如果照着 {@link DepthFirstIterator} 再写一个
 * {@code DepthFirstApprovalIterator}，问题立刻出现：
 *
 * <ul>
 *   <li>过滤条件一变（"只看审批节点" → "只看审批 + 通知节点"）
 *       就要改类或者写第二份；</li>
 *   <li>想同时支持"在广度优先里只取审批节点"，
 *       得再写第三个类 —— 组合数爆炸成
 *       {@code 遍历方式 × 过滤条件}。</li>
 * </ul>
 *
 * <p>本类的做法是：<b>它不生产节点，只转发节点</b>。
 * 构造时接收任意一个 {@code Iterator<WorkflowNode>}，
 * 对外表现得像同一个迭代器，只是把不满足条件的元素
 * 悄悄吃掉。
 *
 * <pre>
 *   new FilteringIterator(workflow.iterator(TraversalOrder.BREADTH_FIRST),
 *                         node -&gt; node instanceof StepNode s
 *                                 &amp;&amp; s.type() == StepType.APPROVAL,
 *                         "只取审批节点");
 * </pre>
 *
 * <p>换个被包装的迭代器，过滤就换到另一种遍历上，
 * 本类一个字符都不用改 —— 这就是"组合优于继承"在
 * 迭代器上的具体样子。
 *
 * <p>这也顺带回答了迭代器模式的一个常见疑问：
 * 迭代器能不能再套一层？能，只要它实现的还是同一个
 * {@code Iterator} 接口，它就是可组合的。
 *
 * <p><b>二、为什么必须预读一个元素？</b>
 *
 * <p>这是本类唯一复杂的地方，也是它最容易写错的地方。
 *
 * <p>假设不预读，直接转发：
 *
 * <pre>
 *   public boolean hasNext() { return delegate.hasNext(); }   // 错
 *   public WorkflowNode next() {
 *       WorkflowNode node = delegate.next();
 *       if (!predicate.test(node)) { ... }   // 这个不满意的节点去哪？
 *   }
 * </pre>
 *
 * <p>问题在于：{@code next()} 已经把被包装迭代器的元素
 * <b>取走了</b>，迭代器是单向的，没有"还回去"的方法。
 * 一旦取到不满足条件的元素，要么丢掉（那就得在这里
 * 循环继续取），要么违反契约直接返回它（那过滤就失效了）。
 *
 * <p>{@code hasNext()} 又必须能回答"到底还有没有一个
 * <b>满足条件的</b>元素" —— 这个答案只能靠提前取出来看。
 *
 * <p>所以本类的做法是：在 {@code hasNext()} 里
 * 持续从 delegate 取元素，直到取到一个满足条件的，
 * 把它存进 {@link #pending}，然后返回 {@code true}。
 * 真正被消费是在 {@code next()} 里。
 *
 * <p><b>三、代价：hasNext() 有了副作用</b>
 *
 * <p>{@link DepthFirstIterator#hasNext()} 是纯查询，
 * 本类的不是：它会推动 {@code delegate} 前进。
 *
 * <p>这不是缺陷，是<b>过滤语义的必然代价</b>。
 * 但调用方必须知道一点：{@code hasNext()} 之后如果不调用
 * {@code next()}，那个已经预读出来的节点会<b>留在
 * {@code pending} 里</b>，不会丢 —— 下一次 {@code hasNext()}
 * 会看到 {@code hasPending} 已为真，直接返回 {@code true}。
 * 也就是说重复调用 {@code hasNext()} 是安全的，
 * 只是第一次调用干了活。
 *
 * <p>这也是 JDK 里 {@code BufferedReader} 风格 API 的通病：
 * 为了回答"还有没有"，必须先读一点。区别只在于
 * 本类把"读过的东西"显式存了下来。
 *
 * <p><b>四、fail-fast 会被延迟</b>
 *
 * <p>如果 {@code delegate} 是带 fail-fast 的迭代器，
 * 结构被修改的异常会在 {@code delegate.next()} 里抛出。
 * 但本类在 {@link #pending} 已有元素时不会碰 delegate，
 * 所以异常会延迟到"这个预读元素被消费完之后的那一次
 * {@code next()} 才爆发"。
 *
 * <p>这是可接受的：延迟不代表丢失，异常终究会来，
 * 而且那时还没有返回任何"越过修改点"的节点。
 *
 * @author design-patterns-pro
 * @see DepthFirstIterator
 * @see BreadthFirstIterator
 */
public final class FilteringIterator implements Iterator<WorkflowNode> {

    /**
     * 被包装的迭代器。
     *
     * <p>它的类型是接口，而不是某个具体实现 —— 这一步
     * 是本类能套在任意遍历方式上的关键。
     */
    private final Iterator<WorkflowNode> delegate;

    /** 过滤条件。 */
    private final Predicate<WorkflowNode> predicate;

    /** 用于异常消息的说明文字，比如"只取审批节点"。 */
    private final String description;

    /**
     * 已经预读出来、尚未交给调用方的节点。
     *
     * <p>{@link #hasPending} 为 {@code false} 时它的值无意义，
     * 此时置为 {@code null}，避免持有已经被消费掉的节点引用。
     */
    private WorkflowNode pending;

    /** {@link #pending} 是否有效。 */
    private boolean hasPending;

    /**
     * 创建一个过滤迭代器。
     *
     * @param delegate    被包装的迭代器，不能为 {@code null}
     * @param predicate   过滤条件，返回 {@code true} 表示放行
     * @param description 说明文字，用于异常消息与调试
     * @throws NullPointerException 任一参数为 {@code null} 时
     */
    public FilteringIterator(Iterator<WorkflowNode> delegate,
                             Predicate<WorkflowNode> predicate,
                             String description) {
        this.delegate = Objects.requireNonNull(delegate, "被包装的迭代器不能为 null");
        this.predicate = Objects.requireNonNull(predicate, "过滤条件不能为 null");
        this.description = Objects.requireNonNull(description, "说明文字不能为 null");
    }

    /**
     * 往前推到下一个满足条件的节点。
     *
     * <p>三件事都要注意：
     *
     * <ol>
     *   <li>{@code !hasPending} 放在循环条件里：
     *       已经预读过就直接返回，不会把 delegate 多推一格；</li>
     *   <li>{@code delegate.hasNext()} 也放在条件里：
     *       不能先 {@code next()} 再判，否则到底时必炸；</li>
     *   <li>循环结束后 {@code hasPending} 就是最终答案：
     *       要么找到了，要么 delegate 真的空了。</li>
     * </ol>
     */
    private void advance() {
        while (!hasPending && delegate.hasNext()) {
            WorkflowNode candidate = delegate.next();
            if (predicate.test(candidate)) {
                pending = candidate;
                hasPending = true;
            }
        }
    }

    /**
     * 是否还有满足条件的节点。
     *
     * <p>有副作用（会推动 {@code delegate}），理由见类注释第二节。
     * 重复调用安全：第二次调用会发现 {@code hasPending} 已是
     * {@code true}，直接返回而不动 delegate。
     *
     * @return 还有满足条件的节点时返回 {@code true}
     */
    @Override
    public boolean hasNext() {
        advance();
        return hasPending;
    }

    /**
     * 取下并返回下一个满足条件的节点。
     *
     * @return 下一个满足条件的节点，不为 {@code null}
     * @throws NoSuchElementException 没有更多满足条件的节点时
     */
    @Override
    public WorkflowNode next() {
        if (!hasNext()) {
            throw new NoSuchElementException(
                    "在「" + description + "」条件下已经没有更多节点");
        }
        WorkflowNode result = pending;
        pending = null;
        hasPending = false;
        return result;
    }
}
