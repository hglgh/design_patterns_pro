package com.patterns.behavioral.command;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 撤销/重做历史（undo / redo 双栈）。
 *
 * <p><b>为什么这个类必须存在，而不是让调用方自己维护两个栈？</b>
 * 因为撤销功能有三条<b>不变量</b>，任何一条被破坏都会导致状态错乱，
 * 而"让每个调用点都记得维护"这种约定，迟早会被违反：
 *
 * <ol>
 *   <li><b>只有成功的命令才能进撤销栈。</b>
 *       失败的命令没有产生副作用，撤销它只会把系统改坏。
 *       → 所以 {@link #execute(UndoableCommand)} 先执行、成功了才入栈。</li>
 *   <li><b>执行新命令必须清空重做栈。</b>
 *       你撤销了 3 步，然后又做了一件新事——那 3 步"未来的分支"就<b>不存在了</b>。
 *       这和 Git 在历史中间提交会导致后面的 commit 离开当前分支是同一个道理。</li>
 *   <li><b>重做失败的命令必须放回重做栈。</b>
 *       否则它既不在撤销栈、也不在重做栈，从状态机里凭空消失了。</li>
 * </ol>
 *
 * <p><b>这个类也解释了为什么 {@code undo()} 不该由命令自己管理栈：</b>
 * 命令的职责是"知道怎么撤销自己"，历史的职责是"知道撤销的顺序"。
 * 把栈塞进命令里，等于让每个命令都依赖撤销栈这个全局结构——
 * 那就没法单测一个命令了。
 *
 * <p><b>为什么这里可以用 {@code ArrayDeque} 的 {@code push/pop}？</b>
 * 因为它们操作的是<b>栈顶</b>（{@code addFirst/removeFirst}），
 * 复杂度和语义都正好符合"最近执行的先撤销"（LIFO）。
 * 不要用 {@code ArrayList} 加 {@code remove(size()-1)} 硬凑——
 * 那是在手动实现一个栈，还容易写错边界。
 *
 * @author design-patterns-pro
 * @see UndoableCommand
 */
public class CommandHistory {

    /** 撤销栈：栈顶是"最近一次执行"的命令。 */
    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();

    /** 重做栈：栈顶是"最近一次撤销"的命令。 */
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();

    /**
     * 执行一条可撤销命令，并在成功后记入撤销栈。
     *
     * <p>这里是"执行"和"记录"的<b>唯一入口</b>，所以不可能出现
     * "执行了但忘了记录"这种情况——这正是把不变量收进一个类里的价值。
     * 可以对比模板方法那一章的教训：<b>把必须做对的事，变成结构中做不到错的事。</b>
     *
     * @param command 可撤销命令
     * @throws CommandException 命令执行失败；此时<b>不会</b>记入历史
     */
    public void execute(UndoableCommand command) throws CommandException {
        System.out.println("  [历史] 执行：" + command.getName());
        command.execute();
        undoStack.push(command);
        // 新分支产生，旧的重做路径全部作废
        redoStack.clear();
        System.out.println("  [历史] 已记入撤销栈（可撤销 " + undoStack.size()
                + " 步，可重做 " + redoStack.size() + " 步）");
    }

    /**
     * 撤销最近一次操作。
     *
     * @return 是否真的撤销了（栈空时返回 {@code false}）
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            System.out.println("  [历史] 没有可撤销的操作");
            return false;
        }
        UndoableCommand command = undoStack.pop();
        System.out.println("  [历史] 撤销：" + command.getName());
        command.undo();
        redoStack.push(command);
        System.out.println("  [历史] 撤销完成（可撤销 " + undoStack.size()
                + " 步，可重做 " + redoStack.size() + " 步）");
        return true;
    }

    /**
     * 重做最近一次被撤销的操作。
     *
     * <p><b>重做就是再执行一次</b>，不是"播放录像"。
     * 所以命令内部如果有"捕获现场"的逻辑（如 {@code UpdateConfigCommand}），
     * 它会重新捕获一次——语义差异见那个类的注释。
     *
     * @return 是否真的重做了（栈空时返回 {@code false}）
     * @throws CommandException 重做时执行失败
     */
    public boolean redo() throws CommandException {
        if (redoStack.isEmpty()) {
            System.out.println("  [历史] 没有可重做的操作");
            return false;
        }
        UndoableCommand command = redoStack.pop();
        System.out.println("  [历史] 重做：" + command.getName());
        try {
            command.execute();
        } catch (CommandException e) {
            // 【必须放回去】弹出来又失败，如果不还回去，
            // 这条命令就既不在 undoStack 也不在 redoStack —— 凭空消失了。
            redoStack.push(command);
            throw e;
        }
        undoStack.push(command);
        System.out.println("  [历史] 重做完成（可撤销 " + undoStack.size()
                + " 步，可重做 " + redoStack.size() + " 步）");
        return true;
    }

    /** @return 可撤销的步数 */
    public int undoCount() {
        return undoStack.size();
    }

    /** @return 可重做的步数 */
    public int redoCount() {
        return redoStack.size();
    }

    /**
     * 打印两个栈的内容（栈顶在左），用于观察状态机的形状。
     */
    public void printStacks() {
        System.out.println("  [历史] 撤销栈（栈顶→底）：" + undoStack.stream()
                .map(Command::getName).toList());
        System.out.println("  [历史] 重做栈（栈顶→底）：" + redoStack.stream()
                .map(Command::getName).toList());
    }
}
