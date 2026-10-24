package com.patterns.behavioral.command;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * 宏命令：把<b>多条命令打包成一条命令</b>。
 *
 * <p>这是命令模式 + 组合模式（Composite）的结合——正因为命令已经被抽象成了
 * 一个统一接口，一条命令和"一组命令"才能被同等对待。
 * Invoker 完全不知道它执行的是一条简单命令还是一个宏，代码一行都不用改。
 *
 * <p><b>它能解决什么现实问题？</b>
 * 发布一个版本需要：改配置 → 备份数据库 → 生成报表 → 发通知邮件。
 * 如果写成四行独立的命令调用，那么第三步失败时，
 * "已经做完的前两步怎么办"这个问题就只能由调用方自己处理，
 * 而且每多一个调用点，就要多写一遍这段回滚逻辑。
 * 宏命令把"要么都做、失败就把已做的撤掉"封在自己内部，调用方只看到一条命令。
 *
 * <p><b>为什么是"逆序"补偿？</b>
 * 因为步骤之间通常有依赖：报表是基于备份的数据生成的，
 * 配置变更又是整个流程的前提。回滚必须从最后一步往回走，
 * 否则会出现"备份已经删了，却还在用备份做报表回滚"的荒谬情况。
 * 这和后缀表达式求值、栈的弹出顺序是同一个道理。
 *
 * <p><b>为什么本类不实现 {@link UndoableCommand}？</b>
 * 因为它的步骤里包含 {@link SendEmailCommand}——<b>邮件发出去收不回来</b>。
 * 一条命令能不能撤销，取决于<b>它的每一步能不能撤销</b>；
 * 只要有一个不可逆的步骤，整体就不可撤销。
 * <b>承认这一点，比硬写一个会骗人的 undo() 诚实得多。</b>
 *
 * <p><b>于是引出一条非常重要的工程原则：</b>
 * <blockquote><b>把不可逆的操作放在流程的最后。</b></blockquote>
 * 本项目 demo5 的步骤顺序是「改配置 → 备份 → 报表 → 发邮件」，
 * 所以当报表失败时，前两步能完整回滚，而邮件<b>压根还没发</b>。
 * 如果把发邮件挪到第二步，那么报表失败时就会出现
 * "配置回滚了、备份删了，但客户已经收到『发布成功』的邮件"这种局面——
 * 这在真实事故复盘里非常常见。
 *
 * <p><b>一句诚实的提醒</b>：这叫<b>补偿（compensation）</b>，不叫事务回滚。
 * 两者有一个本质区别——<b>补偿没有隔离性</b>：
 * 在第 1 步做完、第 2 步失败的那段时间里，外部系统<b>看得见</b>中间状态。
 * 真正的分布式事务（2PC）会锁住资源直到全局提交，而 Saga 只能做到"最终一致"。
 * 所以补偿只适用于"中间状态短暂可见、且可以容忍"的场景。
 *
 * @author design-patterns-pro
 * @see Command 组合的基础
 * @see UndoableCommand 能被补偿的步骤
 */
public class MacroCommand implements Command {

    private final String name;
    private final List<Command> steps;

    /**
     * @param name  宏命令名称，例如 {@code "发布 v2.3.0"}
     * @param steps 步骤，按执行顺序排列；至少一个
     */
    public MacroCommand(String name, Command... steps) {
        if (steps == null || steps.length == 0) {
            // 空宏命令是调用方的编码错误（它什么也不做，却看起来像一个动作）
            throw new IllegalArgumentException("宏命令至少需要一个步骤");
        }
        this.name = name;
        this.steps = List.of(steps);
    }

    @Override
    public String getName() {
        return name + "（" + steps.size() + " 步）";
    }

    @Override
    public void execute() throws CommandException {
        List<Command> done = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Command step = steps.get(i);
            System.out.println("      步骤 " + (i + 1) + "/" + steps.size() + "：" + step.getName());
            try {
                step.execute();
                // 【关键】只有成功了才记入 done。
                // 失败的那一步不需要补偿——它自己就没改成任何东西（或者已自行回滚）。
                done.add(step);
            } catch (CommandException e) {
                // 先把失败原因打出来，再去回滚 ——
                // 否则读者会先看到一串「补偿」却不知道为什么补偿。
                System.out.println("      ! 第 " + (i + 1) + " 步失败：" + e.getDetail());
                int rolledBack = rollback(done);
                // 把"第几步失败、补偿到哪一步"写进异常信息：
                // 运维看到这条日志就知道该从哪里继续排查，而不是只知道"发布失败了"。
                throw new CommandException(getName(),
                        "第 " + (i + 1) + "/" + steps.size() + " 步（" + step.getName()
                                + "）失败，已补偿 " + rolledBack + " 步：" + e.getDetail(), e);
            }
        }
    }

    /**
     * 逆序补偿已完成的步骤。
     *
     * @param done 已成功执行的步骤（按执行顺序）
     * @return 实际补偿成功的步骤数
     */
    private int rollback(List<Command> done) {
        int count = 0;
        ListIterator<Command> it = done.listIterator(done.size());
        while (it.hasPrevious()) {
            Command step = it.previous();
            // 模式匹配的 instanceof（JDK 16+）：一次完成"判断 + 转型"。
            // 这也正是 UndoableCommand 单独成接口的价值 —— 能不能撤销是类型事实，不用猜。
            if (step instanceof UndoableCommand undoable) {
                System.out.println("      ← 补偿：" + step.getName());
                undoable.undo();
                count++;
            } else {
                // 不抛异常：一个步骤不可撤销，不应该让"其余还能撤销的步骤"也放弃补偿。
                // 但必须大声说出来——这就是 Saga 里所谓的"部分补偿"，需要人工介入。
                System.out.println("      ← 无法补偿：" + step.getName() + "（不可撤销）");
            }
        }
        return count;
    }
}
