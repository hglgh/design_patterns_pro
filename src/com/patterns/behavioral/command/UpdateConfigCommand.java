package com.patterns.behavioral.command;

import java.util.Objects;

/**
 * 具体命令：修改一项配置。<b>可撤销</b>，而且是本项目里最"标准"的撤销示例。
 *
 * <p><b>为什么它比备份/报表那两个更适合讲撤销？</b>
 * 因为配置的值是<b>可以精确对比</b>的：改之前是 {@code "10s"}，改之后是 {@code "30s"}，
 * 撤销后必须<b>一个字都不差</b>地回到 {@code "10s"}。
 * 这让"记住现场"这件事从抽象概念变成了可验证的事实。
 *
 * <p><b>本类最容易写错的一行是这句的顺序：</b>
 * <pre>
 *   previousValue = configService.get(key);   // ① 先捕获
 *   configService.set(key, newValue);         // ② 再修改
 * </pre>
 * 如果写反了（先 set 再 get），捕获到的就是<b>新值</b>，
 * 撤销时"把值改回新值"——看起来执行了，实际上什么都没变。
 * <b>这种 bug 不会报任何错</b>，只有对比变更前后的快照才会发现。
 * 这就是"捕获现场必须发生在产生副作用之前"。
 *
 * <p><b>{@code previousValue == null} 为什么不能直接 {@code set(key, null)}？</b>
 * 因为 {@code null} 在这里的含义是<b>"这个键原本不存在"</b>，而不是"值为空"。
 * 正确的补偿是 {@code remove(key)}：让系统回到"压根没这个配置"的状态。
 * 如果错写成 {@code set(key, "")}，配置里就会多出一个空串键，
 * 下游读到一个"显式配置为空"的 key，行为可能完全不同。
 *
 * <p><b>为什么这里不需要 {@code created = null} 那样的重复撤销防护？</b>
 * 因为"把值改回旧值"是<b>幂等</b>的：连做两次，结果一样。
 * 有状态防护只对<b>非幂等</b>的补偿（删除文件）才有必要。
 * 判断依据不是"习惯"，而是"这个操作重复执行会不会出问题"。
 *
 * <p><b>关于重做（redo）的语义，本项目做了一个明确选择：</b>
 * {@link #execute()} 每次执行都<b>重新捕获</b>当前值。于是：
 * <pre>
 *   execute() → previousValue = "10s"
 *   undo()    → 恢复 "10s"
 *   execute() → previousValue = "10s"（因为刚被恢复过，捕获到的还是它）
 * </pre>
 * 这是"<b>重新执行</b>"（re-execute）而不是"<b>重放</b>"（replay）。
 * 区别在两者不一致的时候才会显现：如果两次执行之间有人手工把配置改成了 {@code "20s"}，
 * 那么 redo 之后撤销会回到 {@code "20s"} 而不是最初的 {@code "10s"}。
 * 对配置变更来说这是<b>对的</b>——撤销应该回到"我刚才改之前的样子"，
 * 而不是强行抹掉别人在这期间做的修改。
 * 但对"转账"这类操作就必须用重放（用第一次记下的金额），语义完全不同。
 *
 * @author design-patterns-pro
 * @see ConfigService 接收者
 */
public class UpdateConfigCommand implements UndoableCommand {

    private final ConfigService configService;
    private final String key;
    private final String newValue;

    /** 是否已经成功执行过（用于阻止"执行前就撤销"这种用法错误）。 */
    private boolean executed;

    /** 执行前的旧值；{@code null} 表示<b>这个键原本不存在</b>。 */
    private String previousValue;

    /**
     * @param configService 接收者，不能为 {@code null}
     * @param key           配置键
     * @param newValue      新值
     */
    public UpdateConfigCommand(ConfigService configService, String key, String newValue) {
        this.configService = Objects.requireNonNull(configService, "configService 不能为 null");
        this.key = key;
        this.newValue = newValue;
    }

    @Override
    public String getName() {
        return "修改配置 " + key + " = " + newValue;
    }

    @Override
    public void execute() throws CommandException {
        // ① 先捕获现场（顺序不能反，见类注释）
        previousValue = configService.get(key);
        // ② 再修改
        // ConfigService.set 抛的是 IllegalArgumentException（key 为空），
        // 那是调用方传参错误，属于 bug，不该被包装成 CommandException 吞掉 ——
        // 所以这里不 catch。
        configService.set(key, newValue);
        // ③ 标记成功：放在 set 之后而不是之前。
        // 因为 set 抛异常时配置并没有被改动，此时不该允许 undo。
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new IllegalStateException(getName() + " 尚未执行，不能撤销");
        }
        if (previousValue == null) {
            // 原本不存在 → 删掉，而不是设成空串
            configService.remove(key);
        } else {
            configService.set(key, previousValue);
        }
        // 注意：这里不把 executed 置回 false。
        // 因为 undo 之后还可以 redo（= 再 execute 一次），而 execute 会重新置 true。
        // 置回 false 反而会让"undo → redo → undo"这条链断掉。
    }
}
