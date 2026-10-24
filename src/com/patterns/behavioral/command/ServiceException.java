package com.patterns.behavioral.command;

/**
 * 接收者（Receiver）抛出的异常：代表「这个业务操作本身没做成」。
 *
 * <p><b>为什么不直接用 {@link CommandException}，要多这一层？</b>
 * 因为两者属于不同的层次，混用会把耦合方向弄反：
 *
 * <pre>
 *   Invoker ──依赖──▶ Command ◀──实现在── 具体命令 ──依赖──▶ Receiver
 *      │                  │                                   │
 *      └──只认识──────────┘                                   └──只认识 ServiceException
 *      CommandException
 * </pre>
 *
 * <ul>
 *   <li><b>接收者</b>只知道自己会怎么失败（磁盘满、没数据、地址非法），
 *       它<b>不应该知道</b>「命令」这个概念的存在——它是被复用的业务能力，
 *       明天可能被一个定时任务、一个 HTTP 接口、一个单元测试直接调用。</li>
 *   <li><b>具体命令</b>负责翻译：把「备份失败：磁盘空间不足」翻译成
 *       「备份任务[orders_db] 失败：磁盘空间不足」。它补的是<b>业务上下文</b>。</li>
 *   <li><b>Invoker</b> 只认识 {@code CommandException}。
 *       于是新增一种接收者（比如「刷新 CDN」）时，<b>Invoker 一行都不用改</b>。</li>
 * </ul>
 *
 * <p>这个「统一异常体系」的做法不是本项目发明的，Spring 的
 * {@code DataAccessException} 就是同一件事：MyBatis、JPA、原生 JDBC 各自抛不同的异常，
 * Spring 统一翻译成一套，于是业务代码只 {@code catch (DataAccessException)} 就够了。
 *
 * <p><b>代价也要说清楚</b>：每个命令多了 3 行 {@code try/catch} 样板代码。
 * 这个代价是<b>故意</b>付的——它让「翻译」这件事在代码里<b>看得见</b>。
 * 如果嫌烦，可以用一个 {@code abstract class AbstractCommand} 把
 * {@code final execute()} 写成模板方法（正好是上一个模式），
 * 但那样读者就看不见翻译发生在哪了，对<b>教学代码</b>来说反而是损失。
 *
 * @author design-patterns-pro
 * @see CommandException
 */
public class ServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param message 失败原因，写清楚「哪个资源出了什么问题」，例如
     *                {@code "磁盘空间不足：需要 600MB，可用 500MB"}
     */
    public ServiceException(String message) {
        super(message);
    }

    /**
     * @param message 失败原因
     * @param cause   底层技术异常（如 {@code java.io.IOException}）
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
