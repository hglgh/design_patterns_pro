# 命令模式：通俗入门

> **这是第一层「入门」材料。** 目标只有一个：让你在不背定义的情况下，能说清
> 「为什么要把一次请求包成对象」，以及**什么时候根本不需要这么做**。
>
> 读完本文再看第二层：[深度文档 README.md](README.md)（五个角色的完整工程版、撤销/重做、
> 异常边界、宏命令与补偿）。两层读的是同一个模式，但入门层不要求你先吞下这些细节。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；`javac` 编译后直接 `java` 运行，输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**命令模式就是把「请你帮我做这件事」从一句口头吩咐，变成一张可以排队、可以留底、
可以转交、可以事后补做的工单。**

### 什么需求会让开发者想到它

一个电商系统每天凌晨 2 点要跑一批任务：

1. 备份 `orders_db`；
2. 备份 `logs_db`；
3. 生成每日销售报表；
4. 给运维发一封完成通知。

第一版代码很自然：写一个 `NightlyBatch.runAll()`，按顺序调用四个业务方法。
上线两周后，需求开始提出来：

- 磁盘紧张时，`logs_db` 会备份失败，**但不能连累报表和通知**；
- 失败的那条要能在运维清理磁盘后**单独重跑**；
- 三个月后要能查「哪天、跑了哪条、成功没有」；
- 以后还会加「清理临时文件」「同步用户画像」等任务。

这四个要求分别指向四件事：**失败隔离、重试、审计、可扩展**。
命令模式不是唯一解法，但它把这几件事变得很自然——因为它先把「一次请求」变成了对象。

---

## 2. 先看不使用设计模式的代码

下面是改造前的核心代码（完整可运行版本在
[BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()` 部分）：

```java
class NightlyBatch {

    private final BackupService backup;
    private final ReportService report;
    private final EmailService email;

    void runAll() {
        try {
            backup.backup("orders_db", 400);
            // ↓ 新需求：把 logs_db 也纳入。只能改这里，还要重新测试整个批处理。
            backup.backup("logs_db", 600);
            report.generate("每日销售报表", 1520);
            email.send("ops@shop.com", "夜间批处理完成", "共 4 项");
        } catch (IllegalStateException e) {
            System.out.println("[批处理] 失败：" + e.getMessage());
        }
    }
}
```

三个业务类都是普通的「接收者」，它们不知道批处理的存在：

```java
class BackupService {
    void backup(String database, int sizeMb) {
        if (sizeMb > freeDiskMb) {
            throw new IllegalStateException("磁盘只剩 " + freeDiskMb + "MB，放不下 " + sizeMb + "MB");
        }
        // ...
    }
}

class ReportService  { void generate(String title, int rows) { /* ... */ } }
class EmailService   { void send(String to, String subject, String body) { /* ... */ } }
```

运行结果是：

```text
今晚的任务：备份 orders_db → 备份 logs_db → 生成日报 → 发通知
    [备份] orders_db 完成，剩余磁盘 100MB
[批处理] 失败：磁盘只剩 100MB，放不下 600MB
```

**报表没生成，通知也没发出去。** 这不是编造的极端情况，而是「一个大 try 包住整批任务」
的必然结果。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
NightlyBatch ──依赖──▶ BackupService
             ──依赖──▶ ReportService
             ──依赖──▶ EmailService
```

`NightlyBatch` 同时知道**有哪些任务、按什么顺序、每个任务用什么参数、失败了怎么处理**。
四个职责长在一个类里。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前必须改的代码 | 代价 |
|---|---|---|
| 备份另一个库 `logs_db` | `runAll()` 里加一行，硬编码库名和大小 | 改核心类、重新回归整批任务 |
| 只重跑失败的那条 | 没有「失败的那条」这个对象，只能注释掉其他行再跑 | 靠人肉操作，无法自动化 |
| 记录每条任务的成败 | 在 `runAll()` 里逐条加变量和 `println` | 记录逻辑和业务调用继续缠绕 |
| 新增「清理临时文件」 | 改 `runAll()`，再加一个分支或一行调用 | 每加一种任务都改同一个类 |
| 调整执行顺序/延迟执行 | 改 `runAll()` 的方法体 | 顺序是写死的，无法运行时决定 |

### 3.3 为什么加个 if-else 不够

假设我们把任务类型做成字符串：

```java
void run(String type) {
    if (type.equals("backup-orders")) { ... }
    else if (type.equals("backup-logs")) { ... }
    else if (type.equals("report")) { ... }
    else if (type.equals("email")) { ... }
}
```

它解决了「用字符串选任务」，但没解决真正的问题：

- **参数还在方法体里**：换一个库名、换一个收件人，仍然要改这个类；
- **失败还是整批中断**：`if` 分支不改变「一个 try 包住全部」的结构；
- **失败的任务仍然不是对象**：catch 完只剩一条字符串，明天拿什么重跑？
- **每加一种任务，这个类都要重新编译、重新测试、重新发布**。

问题的根子不是「分支太多」，而是：**一次请求没有被表示成一个东西**。
它只是「调用栈上发生过的一件事」，发生完就消失了，所以没法排队、留底、转交、重试。

---

## 4. 从问题推导出设计

不要一上来就记五个角色。下面五步，每一步只解决前一步留下的一个具体问题。

### 第 1 步：把「一次调用」包成一个对象

先只包最常用的备份：

```java
class BackupCommand {
    private final BackupService backup;   // 谁来做
    private final String database;        // 做什么的参数
    private final int sizeMb;

    BackupCommand(BackupService backup, String database, int sizeMb) {
        this.backup = backup;
        this.database = database;
        this.sizeMb = sizeMb;
    }

    void execute() {
        backup.backup(database, sizeMb);
    }
}
```

**为什么需要它？** 因为「备份 logs_db 600MB」这件事现在是一个对象了。
对象可以放进 `List`、可以传给别人、可以留到明天、可以记录它叫什么。

**不加会怎样？** 请求只存在于 `runAll()` 的方法体里，方法一返回，请求就没了。

**它和 `BackupService` 有什么区别？** `BackupService` 是「会备份」的能力；
`BackupCommand` 是「这一次，备份这个库，这么大」的**具体请求**。同一个 Service
可以被无数个不同参数的 Command 复用。

### 第 2 步：抽出 `Command` 接口

```java
interface Command {
    String name();
    void execute();
}
```

三个命令都实现它。**为什么需要接口？** 因为调度器要能用一个循环处理所有任务：

```java
for (Command command : batch) {
    command.execute();
}
```

如果没有接口，调度器就得写 `if (task instanceof BackupCommand) ... else if ...`，
每加一种任务都要回来改调度器——这正是第 3 节里那个「改不完的类」。

**`name()` 为什么在接口里？** 因为排队、失败、历史记录都要能说清「是哪一条」。
让命令自己报名字，调度器才能写出通用日志；否则日志里只有「任务失败」四个字。

### 第 3 步：让调度器只持有 `Command`

```java
class Scheduler {
    private final List<Command> queue = new ArrayList<>();

    void submit(Command command) {
        queue.add(command);          // 只入队，不执行
    }

    void run() {
        List<Command> batch = new ArrayList<>(queue);
        queue.clear();
        for (Command command : batch) {
            command.execute();       // 调度器不知道背后是谁
        }
    }
}
```

**为什么需要调度器？** 因为「什么时候执行」和「执行什么」现在可以分开决定：
客户端现在提交，调度器凌晨 2 点再跑；也可以先提交 10 条，再一起跑。

**不加会怎样？** 请求对象只能立刻 `execute()`，排队、延迟、批量执行都无从谈起。

### 第 4 步：失败要留下对象

```java
try {
    command.execute();
    history.add(...成功...);
} catch (IllegalStateException e) {
    history.add(...失败：e.getMessage()...);
    failed.add(command);             // ← 关键：失败的命令留在手里
}
```

**为什么不能只记一条错误字符串？** 字符串没法重新执行。想重试，必须还持有那个
知道「备份哪个库、多大」的对象。

**为什么只 catch `IllegalStateException`？** 它在这里代表「业务上做不到」（磁盘不够）。
空指针、数组越界是程序 bug，应该当场抛出去，不能被记成「这条任务今天没跑成」。
命令模式解耦的是调用关系，不是让你把 bug 一起吞掉。

### 第 5 步：重新执行失败项

```java
void retryFailed() {
    queue.addAll(failed);
    failed.clear();
    run();
}
```

历史里会出现两条 `logs_db`：先失败、后成功。这不是重复记录，而是**两次真实尝试**。
审计要的正是这个。

最终结构：

```text
        Client
          │ 创建 Receiver、创建 Command、submit
          ▼
     ┌─────────┐  持有   ┌──────────────┐  调用   ┌──────────────┐
     │Scheduler│ ──────▶ │   Command    │ ──────▶ │   Receiver   │
     │(Invoker)│         │  (接口)      │         │ BackupService│
     └─────────┘         └──────────────┘         │ ReportService│
          │                    ▲                 │ EmailService │
          │ 记录历史/失败       │ 实现             └──────────────┘
          ▼                    │
     List<Command>       BackupCommand / ReportCommand / EmailCommand
```

五个角色在这里各有不可替代的原因：

| 角色 | 本项目 | 少了他会怎样 |
|---|---|---|
| Command | `Command` 接口 | 调度器只能用 `instanceof` 分支，新增任务要改调度器 |
| ConcreteCommand | `BackupCommand` 等 | 请求参数没有地方存，只能写死在调度器里 |
| Receiver | `BackupService` 等 | 业务逻辑会长进命令里，命令自己变成「厨师」 |
| Invoker | `Scheduler` | 没有统一的排队、执行、重试入口 |
| Client | `main` / `after()` | 没人负责把「谁来做」和「做什么」装配起来 |

---

## 5. 用具体数据走一遍

场景：磁盘初始 500MB，先备份 `orders_db`（400MB），再备份 `logs_db`（600MB），
然后生成 1520 行的报表、发通知。

### 5.1 对象关系

```text
scheduler ──queue──▶ [BackupCommand(backup,"orders_db",400),
                      BackupCommand(backup,"logs_db",600),
                      ReportCommand(report,"每日销售报表",1520),
                      EmailCommand(email,"ops@shop.com",...)]

BackupCommand ──持有──▶ BackupService（同一个 backup 实例）
ReportCommand ──持有──▶ ReportService
EmailCommand  ──持有──▶ EmailService
```

注意：命令对象里存的是**接收者的引用 + 参数**，不是执行结果。`submit()` 时什么业务都没发生。

### 5.2 真实执行过程（节选，完整输出见运行结果）

```text
[调度器] 入队：备份 orders_db（400MB）（待执行 1 条）
[调度器] 入队：备份 logs_db（600MB）（待执行 2 条）
[调度器] 入队：生成报表 每日销售报表（待执行 3 条）
[调度器] 入队：发邮件给 ops@shop.com（待执行 4 条）

—— 第一轮：一次交给调度器，按入队顺序执行 ——
[1] 备份 orders_db（400MB）
    [备份] orders_db 完成，剩余磁盘 100MB
    √ 成功
[2] 备份 logs_db（600MB）
    × 失败：磁盘只剩 100MB，放不下 600MB
[3] 生成报表 每日销售报表
    [报表] 每日销售报表 生成完毕，共 1520 行
    √ 成功
[4] 发邮件给 ops@shop.com
    [邮件] 已发送给 ops@shop.com，主题：夜间批处理完成
    √ 成功
[调度器] 本轮：共 4 条，成功 3 条，失败 1 条，待重试 1 条
```

第 2 条失败后，第 3、4 条继续执行——因为每条命令的 `execute()` 被单独包在 try 里。
**这是调度器的循环结构带来的，不是 Command 接口自动保证的。**

### 5.3 只重试失败的那条

```text
—— 运维清理磁盘后：只重试失败的那条 ——
    [运维] 清理出 600MB，可用磁盘 700MB
[调度器] 重试 1 条失败任务
[5] 备份 logs_db（600MB）
    [备份] logs_db 完成，剩余磁盘 100MB
    √ 成功
[调度器] 本轮：共 1 条，成功 1 条，失败 0 条，待重试 0 条

[调度器] 执行历史：
    1. 备份 orders_db（400MB） → 成功
    2. 备份 logs_db（600MB） → 失败：磁盘只剩 100MB，放不下 600MB
    3. 生成报表 每日销售报表 → 成功
    4. 发邮件给 ops@shop.com → 成功
    5. 备份 logs_db（600MB） → 成功
```

### 5.4 三处最容易写错的地方

**① 先快照再清空队列**

```java
List<Command> batch = new ArrayList<>(queue);
queue.clear();
```

如果直接 `while (!queue.isEmpty()) { queue.poll(); ... }`，命令执行时又提交了新命令，
这一轮就可能永远跑不完。快照的语义是明确的：**本次只跑进入时已在队列里的那批**。
这不是命令模式的规定，而是调度器自己的边界设计。

**② 只捕获业务失败**

```java
catch (IllegalStateException e) { ... }
```

如果写成 `catch (Exception e)`，一个空指针也会被记成「任务失败」并进入重试队列，
真正的 bug 就被埋在日志里了。

**③ 失败后保留的是对象，不是名字**

```java
failed.add(command);
```

只存 `command.name()` 的话，历史会好看，但重试时没有任何东西可以执行。
这是「记录」和「可重试」的分界线。

---

## 6. 改造前后对比

### 6.1 新增「清理临时文件」

| 改动点 | 改造前 | 改造后 |
|---|---|---|
| 业务动作 | 在 `NightlyBatch.runAll()` 里加一行调用 | 新增 `CleanTempCommand` 类 |
| 调度逻辑 | 可能还要改 try/catch 和顺序 | `Scheduler` **0 行修改** |
| 装配 | 无独立装配，写死在方法体 | `main` 里加一行 `scheduler.submit(...)` |
| 回归测试 | 整批任务重测 | 新命令单测 + 调度器既有测试不动 |

**要诚实的一点**：调度器不用改，但**调用方仍然要加一行 `submit`**。
没有任何模式能让你「新增功能时一行代码都不写」。命令模式买到的是：
**变化被关在一个新类里，而不是扩散进调度器和已有命令。**

### 6.2 换参数不再改代码

改造前，`runAll()` 里写死 `"orders_db", 400`。想备份 `logs_db` 600MB，必须改方法体。
改造后，只是多构造一个对象：

```java
scheduler.submit(new BackupCommand(backup, "logs_db", 600));
```

`BackupService`、`BackupCommand`、`Scheduler` 都不变。

### 6.3 代价

| 成本 | 具体表现 |
|---|---|
| 类变多 | 4 种任务 = 1 个接口 + 4 个命令类 + 1 个调度器 + 3 个服务 |
| 多一层间接 | 读代码要跳 `Scheduler → Command.execute → Receiver`，不再是一条直线 |
| 命名成本 | 每个命令都要起一个能出现在日志里的名字 |
| 生命周期约束 | 带撤销状态的命令不能随便重复执行；重试要考虑幂等 |
| 简单场景过度 | 只有一个任务、只在启动时跑一次、不需要审计/重试 → 直接调用更清楚 |

**判断标准**：如果你说不出「谁会排队、谁看历史、失败了谁重试」中的任何一个，
就不要引入命令模式。

---

## 7. 适用边界

### 7.1 适合

- 请求需要**排队/延迟/批量**执行（任务队列、调度器）；
- 请求需要**留痕**（审计日志、操作记录、WAL）；
- 请求需要**撤销/重做**（编辑器、配置中心）；
- 请求需要**跨进程传递**（RPC、消息队列的「命令消息」）；
- 请求需要**组合**（一个「发布」由四步组成，失败逆序补偿）。

### 7.2 不适合

- 只有一条固定调用链，没有排队、重试、审计需求；
- 业务方法本身就是唯一入口，包一层命令只是多写三个类；
- 团队还分不清「业务失败」和「程序 bug」，先引入命令只会把异常吞得更深。

### 7.3 容易混淆的模式

| 模式 | 它解决的问题 | 与命令的关键区别 |
|---|---|---|
| **策略 Strategy** | 同一件事有多个可替换算法 | 策略被**选中后立即计算**；命令是**一次请求**，可以被排队、记录、撤销 |
| **责任链 Chain** | 请求该由谁处理不确定 | 责任链让请求**沿链传递**直到有人处理；命令由调用者**明确指定**接收者 |
| **观察者 Observer** | 一件事发生后要通知多方 | 观察者传递的是「**已经发生**」的事件；命令是「**请去做**」，可以失败、可以拒绝 |
| **备忘录 Memento** | 保存/恢复对象内部状态 | 备忘录保存的是**状态快照**；命令保存的是**请求参数**（撤销时可能额外记录现场） |
| **状态 State** | 对象行为随状态改变 | 状态是对象**自己**切换行为；命令是**外部**把请求交给接收者 |

> **命令不是事件。** 命令是「请做某事」，有明确接收者，可以失败；事件是「某事已经发生」，
> 是既成事实，可以有多个监听者。把两者混在一起，就会出现「发出去的命令还能撤回」
> 这种自相矛盾的设计。

### 7.4 哪些能力是模式给的，哪些要自己搭

| 能力 | 谁提供 | 说明 |
|---|---|---|
| 统一执行入口 | `Command` 接口 | 模式直接提供 |
| 请求参数随对象保存 | 具体命令的字段 | 模式直接提供 |
| 排队 | Invoker 里的 `List`/`Queue` | 模式只给你「可被排队」的对象 |
| 失败隔离 | Invoker 的逐条 try/catch | 要自己写循环边界 |
| 重试 | Invoker 保留失败对象 + 业务可重入 | 命令模式不保证幂等 |
| 审计 | Invoker/客户端记录，命令提供 `name()` | 记录格式要自己设计 |
| 撤销 | 命令自己保存执行前状态（如 `UndoableCommand`） | **不是所有命令都能撤销**，不要硬塞进 `Command` |
| 异步 | 线程池、消息队列、虚拟线程 | 命令对象只是「可投递」的前提 |
| 持久化 | 序列化 + 数据库/文件/日志 | 命令对象只是「可存储」的前提 |
| 事务/补偿 | 业务机制 + 逆序 undo | `MacroCommand` 只负责循环，不负责保证一致性 |

---

## 8. 小练习

### 练习 1（场景判断）

后台管理页有三个按钮：导出 Excel、导出 PDF、刷新缓存。点击后分别直接调用
`reportService.exportExcel()`、`reportService.exportPdf()`、`cacheService.refresh()`。
产品经理说「用一下命令模式吧」。**现在需要吗？**

**参考答案**：不需要。当前没有排队、延迟、撤销、审计、统一调度的需求，
三个按钮直接调用各自服务最清楚。引入命令会多出接口、三个命令类和一个调用者，
却没有换来任何行为。**等出现「操作要记录到审计日志」「要支持批量执行/撤销」时再引入**，
那时命令对象才开始还债。

### 练习 2（解释设计原因）

`Scheduler` 为什么只 `catch (IllegalStateException)`，而不是 `catch (Exception)`？
请结合「失败的任务会进入重试队列」说明后果。

**参考答案**：`IllegalStateException` 在这里表示业务上做不到（磁盘不足）。
如果捕获 `Exception`，空指针、类型转换错误也会被当成业务失败：
历史里会留下一条「任务失败」，命令还会进入重试队列，明天再跑一次同样的 bug。
**bug 应该中断批处理、暴露给开发者；业务失败才应该被记录和重试。**
命令模式解决的是「谁来执行」，不是「所有异常都别抛出去」。

### 练习 3（推导）

现在要求「失败后自动重试 3 次，每次间隔 1 秒」。这需要改 `Command` 接口吗？
应该改哪里？有什么前提？

**参考答案**：不需要改 `Command`。重试是**调度策略**，属于 Invoker（或者一个专门的重试装饰器）：
在 `run()` 的 catch 里计数并重新入队，或用 `ScheduledExecutorService` 延迟投递。
前提是**命令可安全重复执行**（幂等）：备份覆盖同一个文件通常可以，发邮件、扣库存则不行。
这也说明：命令模式提供的是结构，**幂等性要由业务自己保证**。

### 练习 4（执行过程）

为什么重试成功后，历史里会有两条 `logs_db`，而不是把失败那条改成成功？

**参考答案**：重试是**第二次真实尝试**，不是对第一次的修正。审计记录的是事实：
第一次失败、第二次成功。如果把失败改成成功，就丢失了「曾经失败过」这个信息，
也就无法回答「这个任务最近失败了几次、为什么失败」。

---

## 9. 读完之后

你应该能用自己的话回答这五个问题（也是本项目的验收标准）：

1. 命令模式为什么存在？——把请求变成对象，从而能排队、留痕、转交、撤销。
2. 改造前的具体问题是什么？——硬编码、整批中断、无法重试、无法审计、加任务改核心类。
3. 每个关键对象为什么存在？——接口统一入口、命令绑定接收者与参数、调度器管何时、接收者管怎么做。
4. 对象之间怎么协作？——Client 装配 → submit 入队 → run 逐条 execute → 失败留对象 → retry 再执行。
5. 新场景要不要用？——看有没有排队/审计/撤销/组合需求；没有就别用。

接下来：

- [深度文档](README.md)：完整工程版、五段 Demo、撤销/重做、宏命令与补偿；
- [Client.java](Client.java)：可直接运行的五段演示；
- [现代场景](../../../../../docs/06-modern-scenarios.md)：`Runnable + ExecutorService`、WAL、消息队列里的命令；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)：策略 vs 状态 vs 命令 vs 模板方法。
