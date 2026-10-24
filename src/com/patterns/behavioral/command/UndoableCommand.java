package com.patterns.behavioral.command;

/**
 * 可撤销的命令：在 {@link Command} 的基础上增加 {@link #undo()}。
 *
 * <p><b>为什么单独建一个接口，而不是在 Command 里加一个 default 方法？</b>
 * 因为「能撤销」是<b>部分命令</b>的能力，不是命令的本质属性。
 *
 * <p>常见的反面写法是这样的：
 * <pre>
 *   public interface Command {
 *       void execute();
 *       default void undo() {
 *           throw new UnsupportedOperationException("本命令不支持撤销");
 *       }
 *   }
 * </pre>
 * 它的问题不在于「能不能跑」，而在于<b>把编译期错误推迟到了运行期</b>：
 * <ul>
 *   <li>所有命令<b>看起来</b>都能撤销（IDE 补全里人人都有 {@code undo()}），
 *       调用方必须先读实现、或者写 try-catch 兜底，才知道真相；</li>
 *   <li>「哪些命令能撤销」这个信息<b>丢掉了</b>。想给「只对可撤销命令显示撤销按钮」
 *       的界面写一段代码，只能靠运行期试探；</li>
 *   <li>用 {@code default} 抛异常其实是「用运行期异常表达类型系统的缺失」——
 *       这和一个方法返回 {@code null} 表示「没有」是同一类问题。</li>
 * </ul>
 *
 * <p>拆成两个接口后，「能不能撤销」变成了<b>编译期事实</b>：
 * <pre>
 *   if (cmd instanceof UndoableCommand undoable) {     // 类型即证据
 *       undoable.undo();
 *   }
 * </pre>
 * 这正是 <b>ISP（接口隔离原则）</b>的直接体现：
 * <b>不应该强迫使用者依赖它用不到的接口</b>。
 *
 * <p><b>哪些命令天生不可撤销？</b> 本项目的例子很有代表性：
 * {@code SendEmailCommand} <b>不实现</b>本接口，因为邮件<b>发出去了就收不回来</b>——
 * 这不是本项目没做，而是现实世界就是这样。
 * 记住这条推论：<b>把不可逆的操作放在流程的最后一步</b>，
 * 是「整个流程可回滚」的前提（见 {@code MacroCommand} 与 {@code Client} 的 demo5）。
 *
 * <p><b>undo 为什么不像 execute 那样声明受检异常？</b>
 * 因为补偿失败通常<b>没有自动化的补救手段</b>：撤销「改配置」时配置中心连不上，
 * 程序除了告警让人介入，做不了什么。让它抛受检异常，只会逼调用方写一个空的 catch。
 * 本项目的约定是：<b>undo 必须成功；做不到就说明状态机被破坏了，直接抛
 * {@link IllegalStateException}</b>——那是 bug，不是业务失败。
 *
 * <p><b>代价：命令实例变成有状态的了。</b>
 * 为了能撤销，命令必须在 {@code execute()} 时<b>记住</b>足够恢复现场的信息
 * （例如"改配置前的旧值"、"备份文件的编号"）。这带来两条硬约束：
 * <ol>
 *   <li><b>一个实例只能用一次</b>——重复执行会覆盖掉第一次记下的现场；</li>
 *   <li><b>不能跨线程共享</b>——两个线程同时执行同一个命令实例，字段会互相踩。</li>
 * </ol>
 * 所以在真实系统里，撤销栈里放的是<b>每次执行时新建的命令对象</b>，
 * 而不是复用一个单例命令。
 *
 * @author design-patterns-pro
 * @see Command
 */
public interface UndoableCommand extends Command {

    /**
     * 撤销本次 {@link #execute()} 造成的全部副作用，把系统恢复到执行前的状态。
     *
     * <p><b>契约</b>：
     * <ul>
     *   <li>必须在 {@code execute()} <b>成功之后</b>才能调用；
     *       执行都没成功就谈不上"恢复"，此时应抛 {@link IllegalStateException}。</li>
     *   <li><b>幂等性由实现者自行判断是否需要</b>：
     *       「把值改回旧值」天然幂等（做两次结果一样），不需要防护；
     *       「删除备份文件」不幂等（第二次删会失败），需要记录状态并拒绝重复撤销。
     *       本项目两种都给了例子——{@code UpdateConfigCommand} 与 {@code BackupDatabaseCommand}。</li>
     *   <li>撤销<b>不负责</b>把自己从撤销栈里移除——那是 {@code CommandHistory} 的职责，
     *       命令不该知道撤销栈的存在。</li>
     * </ul>
     *
     * @throws IllegalStateException 命令尚未成功执行，或重复撤销导致状态不一致
     */
    void undo();
}
