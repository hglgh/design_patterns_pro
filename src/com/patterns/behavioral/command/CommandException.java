package com.patterns.behavioral.command;

/**
 * 命令层统一的<b>受检</b>异常：代表「这条命令没能完成它承诺的事」。
 *
 * <p><b>为什么是受检异常（checked），而不是 RuntimeException？</b>
 *
 * <p>因为它要逼着每一个 {@link Command} 实现去处理接收者的失败。
 * 看原项目的问题：
 * <pre>
 *   public interface Task {
 *       void execute();          // 原项目：不声明任何异常
 *   }
 *
 *   public void run() {
 *       try {
 *           task.execute();
 *       } catch (Exception e) {              // 只能 catch 最宽的 Exception
 *           System.out.println("任务执行失败，记录日志以供重试...");   // e 连用都没用
 *       }
 *   }
 * </pre>
 * 因为 {@code execute()} 不声明异常，接收者一旦抛出检查异常，
 * 命令实现除了「就地吞掉」没有别的选择；
 * 又因为调用方只知道「可能抛 Exception」，它也只能 {@code catch (Exception e)}——
 * 于是 <b>NullPointerException 这种程序 bug 和「磁盘满了」这种运营故障被混为一谈</b>。
 *
 * <p>声明 {@code throws CommandException} 之后，链路上每一环都被编译器盯着：
 * <ol>
 *   <li>接收者抛 {@link ServiceException} → <b>编译器强制</b>命令去处理，不能视而不见；</li>
 *   <li>命令把它翻译成 {@code CommandException} 并补上「哪条命令失败」的上下文；</li>
 *   <li>Invoker 只 {@code catch (CommandException)}，一个精确的网眼——
 *       {@code NullPointerException} 会直接穿过去，让 bug 暴露，而不是被伪装成「任务失败」。</li>
 * </ol>
 *
 * <p><b>为什么不干脆用 RuntimeException？</b>
 * 本类表达的是「预期内的运营故障」：磁盘空间不足、数据源 0 行、收件人地址填错。
 * 这类失败每天都会发生，<b>调用方必须显式决定怎么处理</b>（重试 / 跳过 / 告警 / 中止整个批处理），
 * 而受检异常正是「编译器提醒你必须做这个决定」的机制。
 * 与之相对，程序 bug（{@code null}、下标越界）应该以未受检异常的形式直接崩掉——见 {@code Client} 的 demo3。
 *
 * @author design-patterns-pro
 * @see ServiceException 接收者抛出的异常，由命令翻译成本异常
 * @see Command#execute()
 */
public class CommandException extends Exception {

    private static final long serialVersionUID = 1L;

    /** 出错命令的名称（结构化保留，不只是拼进字符串）。 */
    private final String commandName;

    /** 出错原因（不含命令名）。 */
    private final String detail;

    /**
     * 构造一条命令失败信息。
     *
     * <p><b>为什么把「命令名」和「原因」拆成两个参数，而不是直接拼成一串？</b>
     * 因为不同调用方要显示的东西不一样：
     * <ul>
     *   <li><b>Invoker 的执行表格</b>里已经有一列是命令名，
     *       再拼一遍就变成「2. 备份 logs_db → × 备份 logs_db 失败：……」，重复且难读；</li>
     *   <li><b>没人处理的异常堆栈</b>里没有表格，必须能自报家门。</li>
     * </ul>
     * 两个参数同时满足：{@link #getMessage()} 返回拼接后的完整描述（给堆栈用），
     * {@link #getDetail()} 返回纯净的原因（给表格用）。
     * <b>能用结构表达的信息，就不要只存在字符串里。</b>
     *
     * <p><b>写 detail 的规矩</b>：必须让读到日志的人能定位到具体对象。
     * 反例就是原项目的「任务执行失败」——没说是哪个任务、因为什么失败，
     * 这样的日志在生产上等于没写。
     *
     * @param commandName 命令名称，通常直接传 {@code getName()}
     * @param detail      失败原因（不含命令名）
     */
    public CommandException(String commandName, String detail) {
        super(commandName + " 失败：" + detail);
        this.commandName = commandName;
        this.detail = detail;
    }

    /**
     * 构造一条命令失败信息，并保留原始异常。
     *
     * <p><b>永远不要把 {@code cause} 丢掉。</b>
     * 命令层补的是「业务上下文」（哪条命令、操作什么），
     * 接收者保留的是「技术细节」（哪个文件、哪个 SQL、哪个 IO 错误）。
     * 两者缺一不可，这也是 {@code e.printStackTrace()} 能打出完整链条的前提。
     *
     * @param commandName 命令名称，通常直接传 {@code getName()}
     * @param detail      失败原因（不含命令名）
     * @param cause       接收者抛出的原始异常
     */
    public CommandException(String commandName, String detail, Throwable cause) {
        super(commandName + " 失败：" + detail, cause);
        this.commandName = commandName;
        this.detail = detail;
    }

    /**
     * @return 出错的命令名称
     */
    public String getCommandName() {
        return commandName;
    }

    /**
     * @return 纯净的失败原因（不含命令名），供已经知道命令名的调用方展示
     */
    public String getDetail() {
        return detail;
    }
}
