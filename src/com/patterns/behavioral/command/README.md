# 命令模式（Command Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：把「请帮我做这件事」从**一次方法调用**变成**一个对象**，
> 于是这件事可以被排队、被记录、被撤销、被打包、被重放——
> 而调用它的那一方，**永远不需要知道到底是谁在做**。

> **本目录代码已用 JDK 25 实际编译运行验证**。文中所有输出、所有错误、
> 所有「原项目跑出来的结果」都是真实执行得到的，不是编造的。

---

## 目录

1. [现实案例：餐厅点菜](#1-现实案例餐厅点菜)
2. [原始实现：原项目源码与它的六个问题](#2-原始实现原项目源码与它的六个问题)
3. [设计动机：命令模式到底解决了什么](#3-设计动机命令模式到底解决了什么)
4. [设计推导：从三行业务方法推到十六个文件](#4-设计推导从三行业务方法推到十六个文件)
5. [代码解析：每个文件为什么存在](#5-代码解析每个文件为什么存在)
6. [执行流程：五段 Demo 的真实运行结果](#6-执行流程五段-demo-的真实运行结果)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试与自测](#9-面试与自测)
- [附录：文件清单与运行方式](#附录文件清单与运行方式)

---

## 1. 现实案例：餐厅点菜

### 1.1 说人话版

想象一家餐厅，有四个角色：

```
   顾客                服务员                订单             厨师
 （想吃饭）  ──说──▶ （记下来）  ──递──▶ （一张纸）  ──看──▶ （炒菜）
   Client            Invoker              Command           Receiver
```

顾客想吃饭，但他**不会冲进厨房**对厨师喊「给我炒个宫保鸡丁」。
中间要经过一张**订单**。

为什么非要这张纸不可？把这个流程拆开看，每一处都对应命令模式的一个能力：

| 现实中的问题 | 那张纸解决了什么 | 对应的模式能力 |
|---|---|---|
| 厨房正忙，顾客得等 | 订单在单架上排队，厨师按顺序取 | **请求与执行解耦**，可以排队 |
| 顾客点完就想走，不想站厨房门口等 | 订单留下了，人走了，菜照样做 | **异步**：提出请求和执行请求分离 |
| 厨师可能换人、请假、跳槽 | 订单上没写「必须张师傅做」 | **Invoker 不认识 Receiver** |
| 顾客想加一句「微辣、不要葱」 | 参数写在订单上 | **请求参数成为对象的状态** |
| 点错了想退菜 | 找服务员改单 / 重做 | **撤销**（如果厨房还没做） |
| 结账时要核对吃了什么 | 订单底单留着 | **审计记录** |
| 「三人套餐」= 三菜一汤 | 一张总单里含四个子项，一起下 | **宏命令**（组合） |

**这张纸就是 Command 对象。** 它同时装着三样东西：

1. **做什么**（宫保鸡丁）→ 调用哪个 Receiver 的哪个方法
2. **参数**（微辣、不要葱）→ 字段
3. **给谁做**（3 号桌）→ 上下文（可选）

顾客（Client）只负责下单；服务员（Invoker）只负责把单子递进厨房；厨师（Receiver）只管炒菜，
**他根本不需要知道「订单」这个概念**——就算老板改成电话点餐、扫码点餐，后厨的活一点不变。

> **反例就在眼前**：如果顾客直接对着厨师喊，那么
> ① 厨房忙时没人排队（请求无法缓存）；
> ② 换厨师就没人知道该做什么（耦合）；
> ③ 顾客喊的时候厨师手上有油，听不清（无法重试）；
> ④ 事后查不到任何记录（无审计）。
>
> 这正是原项目的问题——**它的「命令」自己就是厨师**，见第 2 章。

### 1.2 专业版

**GoF 定义**：

> Encapsulate a request as an object, thereby letting you parameterize clients with
> different requests, queue or log requests, and support undoable operations.
>
> 把请求封装成对象，从而使你可以用不同的请求对客户进行参数化，
> 对请求排队或记录日志，以及支持可撤销的操作。

定义里有三个关键点，几乎所有人都会漏掉后两个：

1. **「封装成对象」是手段**，不是目的；
2. **「排队 / 记录」** 才是这个模式在真实系统里最大的价值——
   任务队列、消息队列、WAL、审计日志，本质都是"把请求变成可存储的数据"；
3. **「可撤销」** 是它的招牌能力，但**不是所有命令都能撤销**，
   强行给每个命令都加 `undo()` 是一种常见的过度设计（见 §8）。

**五个角色**（缺一个都要问自己「是不是真的需要命令模式」）：

| 角色 | 职责 | 本项目 |
|---|---|---|
| **Command**（命令） | 声明执行接口 | `Command`、`UndoableCommand` |
| **ConcreteCommand**（具体命令） | 持有 Receiver + 参数，把两者绑成"一次请求" | `SendEmailCommand` 等 5 个 |
| **Receiver**（接收者） | 真正干活的业务对象，**不知道命令存在** | `EmailService`、`BackupService`、`ReportService`、`ConfigService` |
| **Invoker**（调用者） | 持有命令、决定**何时**执行，**不认识 Receiver** | `TaskScheduler` |
| **Client**（客户端） | 创建 Receiver 和命令，把命令交给 Invoker | `Client` |

### 1.3 真实框架里它长什么样

命令模式是**框架层最常见的模式之一**，只是很多时候你已经在用它了，只是没意识到：

| 框架 / 机制 | Command | Invoker | Receiver |
|---|---|---|---|
| `java.lang.Runnable` + `ExecutorService` | `Runnable` | 线程池 / 工作队列 | `run()` 里的业务对象 |
| `HystrixCommand`（Netflix） | `HystrixCommand` 子类 | 执行器 + 熔断器 | 被保护的远程调用 |
| Swing / JavaFX 的 `Action` | `AbstractAction` | 按钮 / 菜单 / 快捷键 | 业务逻辑 |
| 数据库的 redo log（WAL） | 一条"要做的修改"记录 | 日志回放器 | 数据页 |
| 消息队列（Kafka / RabbitMQ） | 消息体 | 消费者 | 消费逻辑 |
| CQRS 架构里的「命令」 | Command 对象 | CommandBus | CommandHandler |

**最值得琢磨的是 redo log**：数据库先把"要改什么"写成一条记录、刷到磁盘，
再真正去改数据页。如果中途断电，重启后按日志**重放**一遍即可。
这正是命令模式「请求即数据」的极限形态——**因为请求是对象，所以它能被持久化，能重放**。
一个方法调用永远做不到这件事：方法调用断电就没了。

> **顺带说明一件容易混淆的事：命令（Command）不是事件（Event）。**
> 命令是「**请**做某事」，有明确的接收者，**可以失败、可以拒绝**；
> 事件是「某事**已经**发生了」，是既成事实，不可拒绝、可以有多个监听者。
> 把两者混为一谈，会写出「发出去的命令还能被撤回」这种自相矛盾的设计。

---

## 2. 原始实现：原项目源码与它的六个问题

### 2.1 原项目：6 个文件、115 行

原项目在 `src/BehaviorType/CommandPattern/`，一共 6 个文件（共 115 行：13 + 17 + 17 + 16 + 31 + 21）。
下面是**逐字拷贝**的源码（含原项目的注释头，只省略了重复的 `@ClassName` 等模板注释）：

**`Task.java`（13 行）**

```java
package BehaviorType.CommandPattern;

public interface Task {
    void execute();
}
```

**`EmailTask.java`（17 行）**

```java
package BehaviorType.CommandPattern;

public class EmailTask implements Task {
    @Override
    public void execute() {
        System.out.println("发送通知邮件...");
        // 实际的发邮件逻辑
    }
}
```

**`BackupTask.java`（17 行）**

```java
package BehaviorType.CommandPattern;

public class BackupTask implements Task {
    @Override
    public void execute() {
        System.out.println("正在备份数据库...");
        // 实际的备份逻辑
    }
}
```

**`ReportTask.java`（16 行）**

```java
package BehaviorType.CommandPattern;

public class ReportTask implements Task {
    @Override
    public void execute() {
        System.out.println("生成业务报表...");
        // 实际的报表生成逻辑
    }
}
```

**`TaskScheduler.java`（31 行，原项目的 Invoker）**

```java
package BehaviorType.CommandPattern;

import java.util.LinkedList;
import java.util.Queue;

public class TaskScheduler {
    private final Queue<Task> taskQueue = new LinkedList<>();

    public void addTask(Task task) {
        taskQueue.offer(task);
    }

    public void run() {
        while (!taskQueue.isEmpty()) {
            Task task = taskQueue.poll();
            try {
                task.execute();
                System.out.println("任务执行成功！");
            } catch (Exception e) {
                System.out.println("任务执行失败，记录日志以供重试...");
            }
        }
    }
}
```

**`SchedulerClient.java`（21 行）**

```java
package BehaviorType.CommandPattern;

public class SchedulerClient {
    public static void main(String[] args) {
        TaskScheduler scheduler = new TaskScheduler();

        scheduler.addTask(new EmailTask());
        scheduler.addTask(new BackupTask());
        scheduler.addTask(new ReportTask());

        scheduler.run();
    }
}
```

### 2.2 原项目的真实运行结果

用 JDK 25 编译并运行 `SchedulerClient`，**实际输出**（`COMPILE-EXIT=0`，`RUN-EXIT=0`）：

```
发送通知邮件...
任务执行成功！
正在备份数据库...
任务执行成功！
生成业务报表...
任务执行成功！
```

六行。看起来一切正常——**这就是它最危险的地方**：这个 Demo 里没有任何失败、没有参数、
没有撤销需求，所以所有问题都被藏住了。

于是我写了一个**临时探针**（只存在于验证目录，不属于原项目），
用原项目自己的 `TaskScheduler` 跑一个"会抛异常的任务"，看看失败时会发生什么：

```java
TaskScheduler scheduler = new TaskScheduler();

scheduler.addTask(() -> {
    throw new IllegalStateException("磁盘已满，需要 600MB");   // 模拟磁盘满
});
scheduler.addTask(() -> System.out.println("后续任务照常执行"));

System.out.println("--- 第一次 run() ---");
scheduler.run();

System.out.println("--- 第二次 run() ---");
scheduler.run();
```

**真实输出**：

```
--- 第一次 run() ---
任务执行失败，记录日志以供重试...
后续任务照常执行
任务执行成功！

--- 第二次 run() ---
```

第二次 `run()` **一片空白**。

这两段输出一共暴露了六个问题，下面逐个分析。

### 2.3 它做对了什么（先说好的）

重构不是全盘否定。原项目有三点是对的，本项目**完整保留**：

| 做对的事 | 说明 |
|---|---|
| **用接口抽象"任务"** | `Task` 只有一个 `execute()`，Invoker 只依赖它。这一步是整个模式的地基。 |
| **用队列解耦"提交"和"执行"** | `addTask()` 只入队，`run()` 才执行。请求和执行确实分离了。 |
| **逐条 try-catch，失败不中断** | 一条任务失败，后面的照常跑。批量任务的正确姿势。 |

问题全在**这个骨架之外的细节**里。下面的六个问题，前三个是设计问题，后三个是工程问题。

---

### 2.4 问题一：没有 Receiver，命令自己就是厨师

这是**最根本**的一个问题，其余问题多多少少都由它派生。

看 `BackupTask` 的 `execute()`：

```java
public void execute() {
    System.out.println("正在备份数据库...");
    // 实际的备份逻辑        ← 业务逻辑长在命令里
}
```

在真实的系统里，那行注释会被替换成几十行代码：连数据库、开事务、读表、压缩、写文件、传对象存储。
**这些代码写在 `BackupTask` 里，就意味着"备份"这项能力只能以"任务"的形式存在。**

先看它违反了什么：命令模式的卖点是「**调用者不认识接收者**」。
可当命令自己就是接收者时，这条卖点自动落空——Invoker 确实不认识 `BackupService`，
但那是因为**根本没有 `BackupService`**，而不是因为解耦做得好。

再看三个具体的后果：

**后果 1：业务能力被绑死在"任务"这个概念上**

假设产品经理说：「用户点『立即备份』按钮，也要能触发一次备份。」
你手上只有 `BackupTask`，于是只能：

```java
// 在一个 HTTP 接口里…
new BackupTask().execute();      // ✅ 能跑
```

能跑，但很别扭：为了用一段业务逻辑，被迫构造一个"任务对象"。
更糟的是——这段代码在**单元测试**里也会出现，测试"备份逻辑"得先 `new` 一个任务。
**一个类如果只有在"被调度"的语境下才能用，它就不是业务组件，是调度器的一部分。**

**后果 2：加一层横切关注点时无处下手**

需求：「备份失败自动重试 3 次，间隔 1 秒。」
如果逻辑在 Receiver 里，你会写 `BackupService.backupWithRetry()`，或者干脆用装饰器（Decorator）包一层；
现在逻辑在 `BackupTask.execute()` 里，你只有两个选择：
- 把重试写进 `execute()` → 命令里混进了"重试策略"，将来每个命令都要抄一遍；
- 或者把 `BackupTask` 包一层 `RetryTask implements Task` —— **这是对的做法**，
  但它要求"业务逻辑"和"任务"是分开的，而这正是原项目没有的。

**后果 3：`Task` 接口不配叫「命令」**

`Task` 这个名字本身没错（命令模式在很多框架里就叫 Task），
但它承诺的东西是「一个可以被排队的执行单元」，而不是「对一次请求的封装」。
两者的区别在于：**请求是可以被描述、被存储、被还原的**，
而"执行单元"只是一个函数指针。这一点在下一个问题里会看得更清楚。

> **一句话总结问题一**：原项目做了一个「任务队列」，但没有做「命令模式」。
> 两者的差别不是命名，而是**中间有没有 Receiver 这一层**。

---

### 2.5 问题二：命令不能携带参数

三个 Task 的 `execute()` 都是无参的，所以它们只能做"固定的一件事"：

```java
scheduler.addTask(new BackupTask());   // 备份什么？不知道，代码里写死了
scheduler.addTask(new EmailTask());    // 发给谁？不知道
```

现实里这三个请求都需要参数：

| 命令 | 必须携带的参数 |
|---|---|
| 备份 | 哪个库、备份到哪、是否压缩 |
| 发邮件 | 收件人、标题、正文、是否抄送 |
| 生成报表 | 报表名、时间范围、数据源 |

**「把请求封装成对象」这件事的核心，恰恰是把参数变成对象的字段。**
原项目的"对象"里一个字都没有，那它封装的不是请求，只是一个函数。

有人会说：加字段不就行了？

```java
public class BackupTask implements Task {
    private String database;         // 谁来设置？
    private int sizeMb;

    public void setDatabase(String db) { this.database = db; }
    ...
}
```

能跑，但有两个坑：

1. **可变的对象 + setter = 一个可以被半成品使用的对象**。
   `new BackupTask()` 之后忘了 `setDatabase()`，编译通过、运行到一半才炸。
   命令对象应该是**构造完就完整**的——本项目全部用构造函数注入参数。
2. **没有 `getName()` 的地方**（见问题五）：参数既然在字段里，
   「这条命令到底在干什么」就只存在于字段里，日志里一个字都看不到。

> 参数进入字段还有一个常被忽略的好处：**它让请求可以被序列化**。
> 一条命令如果只有参数（没有逻辑），它就能被写成一行 JSON 存进数据库，
> 进程重启后还原出来再执行一次——**这就是任务队列能"断点续跑"的原理**。

---

### 2.6 问题三：`catch (Exception e)` 吞掉了一切

原项目的这四行，是整个文件里信息密度最低、危害最大的部分：

```java
try {
    task.execute();
    System.out.println("任务执行成功！");
} catch (Exception e) {
    System.out.println("任务执行失败，记录日志以供重试...");   // e 从头到尾没被用过
}
```

**真实输出**（就是上面探针第一次 run() 的第二行）：

```
任务执行失败，记录日志以供重试...
```

那句话说的是 `IllegalStateException("磁盘已满，需要 600MB")`。
**「磁盘已满，需要 600MB」这十二个字，一个都没打出来。**

具体有五个问题：

| # | 问题 | 后果 |
|---|---|---|
| 1 | `e` 声明了但从不使用 | 异常携带的所有信息（类型、消息、堆栈、cause）全部丢失 |
| 2 | `catch (Exception)` 网眼太大 | `NullPointerException`、`ClassCastException` 这类**程序 bug 也被吃掉**，伪装成"今天这条任务没跑成" |
| 3 | 说「记录日志」但没记录 | 没有任何日志框架、没有写文件、没有留对象。**只是一句 `println`** |
| 4 | 说「以供重试」但没有重试 | 没有重试队列、没有次数统计、没有对象引用（见问题四）。**这句注释是假的** |
| 5 | 吞掉异常后 `run()` 正常返回 | 调用方拿到的是一个「一切正常」的方法返回值，**根本不知道有任务失败了** |

第 2 条最要命。想象一下线上出了这样一串日志：

```
任务执行失败，记录日志以供重试...
任务执行成功！
任务执行失败，记录日志以供重试...
```

运维看到这三行，能判断什么？**什么都判断不了。**
可能是磁盘满了、可能是数据库连不上、也可能是某段代码空指针了。
唯一能做的动作是——**翻代码，猜**。

> **这不是"日志写得不好"，而是"异常边界设计错了"。**
> 两类东西必须分开：
> - **业务失败**（磁盘满、没数据、地址非法）→ 可以预期、可以重试、应该被记录和处理；
> - **程序 bug**（NPE、越界）→ 不该发生、不能重试、应该**直接炸掉让人去修**。
>
> 把它们塞进同一个 `catch` 里，等于把"报警器"和"烟雾"一起关掉了。
> 本项目的做法见 §4.5 与 §6.3。

---

### 2.7 问题四：`poll()` 把任务丢掉了

```java
while (!taskQueue.isEmpty()) {
    Task task = taskQueue.poll();     // ← 取出来，队列里就没了
    try { task.execute(); } catch (Exception e) { ... }
}
```

`poll()` 的语义是「取出并移除」。方法返回之后，`task` 这个局部变量也消失了，
于是**失败的那条命令在整个进程里再也没有任何引用**。

这就是为什么第二次 `run()` 一片空白：

```
--- 第二次 run() ---
（什么也没打）
```

**失败的任务没了，成功的任务也没了。** 想重试？没有对象。想重跑整批？得重新 `new` 一遍。

顺带说一个语义细节：`Queue` 有三个取元素的方法，差别很关键：

| 方法 | 队列为空时 | 是否移除 | 适用场景 |
|---|---|---|---|
| `poll()` | 返回 `null` | ✅ 移除 | **消费掉就不再需要**（如日志缓冲） |
| `peek()` | 返回 `null` | ❌ 不移除 | 看一眼，稍后可能还要处理 |
| `remove()` | 抛 `NoSuchElementException` | ✅ 移除 | 确信非空时 |

原项目的场景是"任务调度"，**任务执行完不代表这个任务对象可以销毁**——
它是审计记录、是重试依据、是撤销入口。所以正确做法不是换一个方法，
而是**换一种数据结构**：本项目用「快照 + 清空队列 + 失败列表」，
既能保证 `run()` 期间新提交的任务不被卷进来（见 §5.6），又能把失败的命令留住。

---

### 2.8 问题五：日志里没有"是谁"

```java
System.out.println("任务执行成功！");
```

三个任务时，你还能靠"上一行是 `正在备份数据库...`"猜到成功的是备份；
三十个任务、并发执行、日志交错时，这句话**完全无意义**。

更根本的问题是：**「我是什么任务」这个信息，压根没地方存**。
`Task` 接口只有 `execute()`，没有 `getName()`。
所以调度器即使想打印名字，也拿不到。

于是产生一个连锁反应：

```
接不了名字 → 日志无法定位 → 失败只能靠人猜 → 猜不出来就加 debug 日志 → 更乱
```

本项目的修法是把 `getName()` 放进 `Command` 接口（§4.3），
并且**要求名字里带上关键参数**：

```java
@Override
public String getName() {
    return "备份数据库 " + database + "（" + sizeMb + "MB）";
}
```

这样一行日志就能定位：

```
  [2] 备份数据库 logs_db（600MB）
        × 失败：磁盘空间不足：需要 600MB，可用 100MB
```

**谁、干了什么、结果如何、为什么失败**——四个问题一句话答完。

---

### 2.9 问题六：完全没有「撤销」的位置

严格说，撤销**不是命令模式的必选项**。很多命令天生不可撤销（发邮件就是）。

但原项目的问题不是"没实现 undo"，而是**连"哪些能撤销"这个信息都没有容身之处**：
`Task` 只有一个 `execute()`，你无法从类型上区分"可撤销的命令"和"不可撤销的命令"。

如果硬要在原结构上加，最常见的错法是：

```java
public interface Task {
    void execute();
    default void undo() {
        throw new UnsupportedOperationException("不支持撤销");
    }
}
```

这样写能编译、能跑，但它把「编译期能发现的问题」变成了「运行期才炸的坑」：

- 所有任务看起来都能撤销；
- 想给"可撤销的任务"显示一个撤销按钮，只能运行期试探；
- 六个月后接手的人会对着 `EmailTask.undo()` 里的 `throw` 发呆。

**本项目的做法**：拆成两个接口（`Command` / `UndoableCommand`），
让"能不能撤销"变成**类型事实**，可以用 `instanceof` 在编译期确定的分支里处理。

---

### 2.10 六个问题汇总

| # | 问题 | 证据（真实输出/源码） | 后果 | 本项目怎么修 |
|---|---|---|---|---|
| 1 | 没有 Receiver | `BackupTask.execute()` 里就是业务逻辑 | 业务能力被绑死在"任务"上，无法复用、无法加横切逻辑 | 抽出 4 个 Receiver，命令只做委托 + 异常翻译 |
| 2 | 命令无参数 | 三个 Task 的 `execute()` 都无参 | 只能做固定的一件事；请求无法被存储/重放 | 参数全部通过构造函数注入字段 |
| 3 | `catch (Exception e)` 吞异常 | 输出 `任务执行失败，记录日志以供重试...`，`磁盘已满，需要 600MB` 完全丢失 | bug 与业务失败混为一谈；日志无法排查 | 引入 `CommandException` / `ServiceException`，只 catch 前者 |
| 4 | `poll()` 丢任务 | 第二次 `run()` 一片空白 | 失败任务不可重试、不可审计 | 快照执行 + `failed` 列表 + `retryFailed()` |
| 5 | 没有 `getName()` | 只有 `任务执行成功！` | 日志无法定位到具体任务 | `Command.getName()` 必须含关键参数 |
| 6 | 没有 undo 的位置 | `Task` 只有 `execute()` | 无法区分可撤销/不可撤销 | 拆分 `UndoableCommand` + `CommandHistory` |

> **这六个问题有一个共同的根源**：
> 原项目把「命令」理解成了「一个可以被调用的函数」，而不是「一次请求的封装」。
> 函数只有"被调用"这一个用途；而请求，可以被排队、被记录、被撤销、被组合、被重放。
> **这就是本章要做的全部改造。**

---

## 3. 设计动机：命令模式到底解决了什么

### 3.1 不用模式会怎样：一个"看起来很好"的发布方法

假设我们不用命令模式，直接写业务。发布一个版本要做四件事，最自然的写法是：

```java
public class ReleaseService {

    private final ConfigService config;
    private final BackupService backup;
    private final ReportService report;
    private final EmailService email;

    public void release(String version) {
        config.set("release.version", version);
        backup.backup("orders_db", 400);
        report.generate("发布核对报表", 1520);
        email.send("team@shop.com", "发布完成", version + " 已上线");
    }
}
```

**这段代码没有任何设计问题。** 它短、直白、一眼看懂——**在需求不变的前提下，
它就是最优解**。任何模式都不该被用来"优化"它。

问题出在需求会变。下面六个需求，是任何一个真实系统在半年内一定会遇到的：

| # | 新需求 | 直接调用要改什么 | 麻烦在哪 |
|---|---|---|---|
| 1 | 发布要记审计日志，能查"谁在什么时候发布了什么" | 在 `release()` 里加日志 | 每加一个操作就要记得加日志，漏了没人发现 |
| 2 | 前一步失败要回滚（配置改了但备份失败） | 写一堆 `try-catch` + 补偿代码 | 补偿逻辑和业务逻辑混在一起，越写越乱 |
| 3 | 发布要能定时执行（比如凌晨 2 点） | 把 `release()` 塞进定时任务 | `release()` 不可序列化，重启就丢 |
| 4 | 发布要能撤销（回滚到上一个版本） | 另写一个 `rollback()` | 两套逻辑必须永远保持对称，改一处忘另一处 |
| 5 | 发布要支持"只做前两步"的预演模式 | 加 `boolean dryRun` 参数 | 参数爆炸，`release(version, true, false, ...)` |
| 6 | 发布流程要在管理后台显示成进度条 | 在每步之间插回调 | 业务方法被 UI 需求污染 |

**六个需求，每一个都在要求同一件事：把"要做什么"从"立刻做"里拆出来。**

而命令模式做的正是这件事——它把 `release()` 里的每一行变成一条命令对象，于是：

| 需求 | 有了命令对象之后 |
|---|---|
| 1 审计 | Invoker 统一记录 `ExecutionRecord`，不需要每个业务方法自己写 |
| 2 回滚 | 命令实现 `undo()`，由 `CommandHistory` 或 `MacroCommand` 统一调度 |
| 3 定时 | 命令是对象 → 可序列化 → 落库 → 定时器取出还原 → 执行 |
| 4 撤销 | 复用同一个 `undo()`，不需要写第二套对称逻辑 |
| 5 预演 | 换一个 Invoker（只打日志不执行），业务代码 0 改动 |
| 6 进度条 | Invoker 在 `execute()` 前后发事件，业务代码 0 改动 |

**注意最后两行**：预演和进度条，业务代码都**一行没改**。
这不是巧合，而是"解耦"的真实含义——**新需求落在 Invoker 层，而不是业务层**。

### 3.2 四个收益（按真实价值排序）

#### 收益 1：Invoker 不认识 Receiver

这是模式的定义性特征。本项目的 `TaskScheduler` 全文 211 行，
**没有出现任何一个 Receiver 的类名**，也没有 `instanceof`：

```java
for (Command command : batch) {
    command.execute();
}
```

看起来平平无奇，但它是**"新增一种业务不需要改调度器"**的前提。
原项目做不到这一点吗？其实它的 `TaskScheduler` 也是这么写的——
**但那是因为原项目根本没有 Receiver，而不是因为解耦做得好**（§2.4）。

区别在哪？做一个实验：给本项目加一条"刷新 CDN 缓存"的命令。

```java
public class PurgeCdnCommand implements Command {
    private final CdnService cdn;          // 新的 Receiver
    private final String path;

    @Override public String getName() { return "刷新 CDN：" + path; }

    @Override public void execute() throws CommandException {
        try {
            cdn.purge(path);
        } catch (ServiceException e) {
            throw new CommandException(getName(), e.getMessage(), e);
        }
    }
}
```

`TaskScheduler` **改动 0 行**。原项目同样"改动 0 行"，但它的 `PurgeCdnTask`
里会直接写着 CDN 的调用代码——**业务逻辑又长进任务里了**。
二十年后再看这两个代码库，一个有 4 个可复用的 Receiver，一个有 20 个 self-contained 的 Task，
哪个更容易迁移到别的框架？答案很明显。

#### 收益 2：请求变成了数据，于是可以被存储和重放

这一条是最被低估的。命令对象只有参数（没有逻辑），所以它可以被序列化成一行记录：

```json
{"type":"SendEmailCommand","to":"ops@shop.com","subject":"备份完成"}
```

有了这个能力，下面这些事情才成为可能：

| 场景 | 依赖的能力 |
|---|---|
| 任务队列进程重启后继续跑 | 命令可持久化 + 可还原 |
| 消息队列（Kafka/RabbitMQ） | 消息 = 命令 |
| 数据库 redo log | 修改操作 = 命令 |
| 操作审计 / 回放攻击 | 命令可记录 |
| 灰度：先记录不执行，观察后再补跑 | 命令可排队 |

**一个方法调用做不到其中任何一件**——它只活在调用栈上，栈一弹就没了。

#### 收益 3：撤销与补偿

命令对象能记住"执行前的现场"，所以能撤销（`UndoableCommand`）；
多条命令能组成宏，失败时逆序补偿（`MacroCommand`）。

这里要**特别诚实**地说清楚边界：

- **撤销 ≠ 事务回滚**。撤销没有隔离性，中间状态对外可见。
- **不是所有操作都能撤销**。发邮件、发短信、扣款（已到账）都不行。
- **"能撤销"取决于 Receiver 是否提供补偿能力**，不取决于你想不想。
  `EmailService` 没有 `recall()`，`SendEmailCommand` 就永远不可能是 `UndoableCommand`。

正因为有"不可撤销"这一半，才推出那条重要的工程原则：
**把不可逆的操作放在流程最后**（§4.10）。

#### 收益 4：组合（一组命令 = 一条命令）

`MacroCommand` 让"发布流程"变成一条命令，于是：

- 调度器不用知道里面有几步；
- 撤销/审计/进度条对"一条"和"一组"的处理完全一致；
- 新增一个步骤，只改宏的构造，调度器、历史、UI 全都不用动。

这是**组合模式**在行为维度的复用，也是"命令"这个抽象足够强的一个证据。

### 3.3 什么时候**不该**用命令模式

这一节比上面三节都重要，因为**绝大多数"设计模式用错"都是因为在这一步没停下来**。

#### 不该用 1：只有一个实现，而且永远不会有第二个

```java
// 不需要 Command，直接调就行
public void sendWelcomeMail(String to) {
    emailService.send(to, "欢迎", "……");
}
```

**判断标准**：如果你写不出"第二种命令"，那就不需要 `Command` 接口。
为一个方法建一个接口 + 一个实现类，得到的是**多一层跳转**，不是解耦。

#### 不该用 2：没有排队、撤销、审计、重放中的任何一项需求

命令模式的成本是实打实的：

| 成本 | 具体表现 |
|---|---|
| 类数量翻倍 | N 个业务操作 → N 个命令类（本项目 4 个 Receiver → 5 个命令） |
| 多一层间接 | 读代码要"命令 → Receiver"跳两次 |
| 参数搬运 | 构造函数要把参数从客户端搬到 Receiver |
| 异常翻译 | 每个命令多 3 行 `try-catch` |

**如果这些成本换不来任何一项收益，那就是纯粹的过度设计。**
一个普通的 CRUD 接口，用命令模式包装只会让代码更难读。

#### 不该用 3：为了"以后可能会排队"而提前抽象

这是 **YAGNI** 的典型违反。判断标准很简单：

> **能说出"哪个具体功能需要它"吗？**
> - 「下个季度的定时任务模块要用」→ 可以用；
> - 「以后可能会用到」→ 不要用。

本项目之所以用，是因为 demo2（重试）和 demo4/demo5（撤销）是**已经存在**的需求，
不是为了将来预留。

#### 不该用 4：性能极端敏感的路径

命令模式会多一次对象分配和一次虚方法调用。**在 99.9% 的业务代码里，这个开销可以忽略**
（JIT 会把单态调用内联掉），所以不要拿性能当拒绝模式的理由。

但如果是在**每秒千万次**的热路径上（比如行情撮合的核心循环），
多一层对象确实是成本——那种场景本来就该用数组和基本类型，而不是任何 GoF 模式。
**模式的适用性要和场景的量级匹配，而不是和"最佳实践"匹配。**

### 3.4 一句话对照：命令 vs 策略 vs 模板方法

这三个模式经常被混着问。先给一句话版本，详细的对比在 §8.3：

| 模式 | 一句话 | 变化点在哪 |
|---|---|---|
| **模板方法** | 父类定流程，子类填步骤 | 继承，**编译期**固定 |
| **策略** | 同一个问题，换一套算法 | 组合，**运行期**可换 |
| **命令** | 把"要做的事"打包成对象 | 把**请求本身**变成一等公民 |

最容易混的是**策略和命令**：两者都"把行为封装成对象"。
区别在于**意图**：

> **策略**回答「**怎么做**」——同一件事的不同算法（满减 / 打折 / 会员价）。
> 客户端**知道**自己在用哪个策略，选它是为了要那个结果。
>
> **命令**回答「**做什么**」——一次具体的请求（备份 orders_db）。
> 客户端**不关心**谁执行、什么时候执行，它只是把请求交出去。

一个具体的判别方法：**策略对象通常不产生副作用**（纯计算），
**命令对象一定有副作用**（它要改变世界，否则没有意义）。

---

## 4. 设计推导：从三行业务方法推到十六个文件

### 4.0 推导路线

这一章从**一段最普通的业务代码**出发，一步一步被需求逼出这 16 个文件。
每一步只做一件事：**先找到上一步的写法必然会崩的那个具体需求**，
然后加上刚好够用的那一个东西。

| 步 | 上一步的写法 | 崩在哪个需求上 | 这一步加了什么 |
|---|---|---|---|
| 0 | `new BackupTask().execute()` | ——（暂时够用） | —— |
| 1 | 业务代码写在命令里 | 备份要能被 HTTP 接口、定时器、调度队列三处复用 | **Receiver**（4 个 Service） |
| 2 | 四个互不相关的 Service | 要把它们排进同一个队列 | **`Command` 接口** |
| 3 | `void execute()` | 日志里只有「任务执行成功！」 | **`getName()`** |
| 4 | 无参 `execute()` | 备份哪个库？邮件发给谁？ | **构造器注入参数** |
| 5 | `catch (Exception e)` | 磁盘满和空指针被一视同仁 | **`ServiceException` + `CommandException`** |
| 6 | `poll()` | 失败的任务没了 | **`failed` 列表 + `retryFailed()`** |
| 7 | 一个接口包打天下 | 想区分「哪些能撤销」 | **`UndoableCommand`** |
| 8 | undo 时现查现用 | 撤销时旧值早被覆盖了 | **在 execute 里捕获现场** |
| 9 | 撤销栈散落在调用方 | 连撤三次、再重做一次 | **`CommandHistory`（双栈）** |
| 10 | 发布流程四步总是一起用 | 组合 | **`MacroCommand`** |

一句提醒：**这十步里没有任何一步是"为了用模式"**。
每一步都是因为有一个具体需求立在那里，不加就写不出来。
如果你在真实项目里数不出这样的需求，那就该停在 §3.3 列的那四种情况下——**不要往下走**。

---

### 4.1 第 0 步：一段没有设计问题的代码

原项目的 `BackupTask` 就是我们的起点，去掉包名和注释后是这样的：

```java
public class BackupTask implements Task {
    @Override
    public void execute() {
        System.out.println("正在备份数据库...");
        // 实际的备份逻辑
    }
}
```

**在"只有一个备份任务、永远不失败、永远不重试、永远不撤销"的世界里，这是最优解。**
短、直白、没有多余的抽象。任何模式都不该被用来"优化"它。

问题出在世界不是这样。把本项目 demo 里**真实出现**的需求摆出来：

| 需求 | 出现在哪 | `BackupTask` 能应付吗 | 卡在哪 |
|---|---|---|---|
| 备份**指定的库**、**指定的大小** | demo1 | ❌ | 参数全部写死 |
| 失败要告诉调用方**为什么** | demo2 | ❌ | `println` 不是"告诉" |
| 失败的任务要能**重试** | demo2 | ❌ | 没有任何引用留住这个对象 |
| 撤销时要**删掉刚生成的备份文件** | demo4 / demo5 | ❌ | 不知道文件叫什么、也不知道编号 |
| 和"发邮件""改配置"排在**同一个队列** | demo1 | ⚠️ 勉强 | 靠 `Task` 能排进去，但每条任务都得自己写一遍业务代码 |

五个需求，指向同一件事：

> **「备份」这项能力需要一个独立的、可复用的、不知道自己被谁调用的载体。**

「不知道自己被谁调用」这半句是关键。因为一旦它知道了，它就和调度器焊死在一起了——
而那正是原项目的状态。

---

### 4.2 第 1 步：先有 Receiver

**顺序很重要：先写业务，后写命令。**

很多人写命令模式是从「我要建一个 `Command` 接口」开始的。
结果是命令类里空空如也，只能把业务逻辑塞进 `execute()`——
**绕了一圈，又退化成原项目了**。

正确的顺序是：先问「这件事到底由谁来做」，把那个"谁"写成一个**普通的类**，
让它完全不知道命令的存在。本项目抽出了四个：

| Receiver | 职责 | 它的"补偿能力" |
|---|---|---|
| `EmailService` | 发邮件 | **没有**（发出去收不回来） |
| `BackupService` | 备份数据库、删除备份、管理磁盘 | `delete(id)` |
| `ReportService` | 生成报表、删除报表文件 | `delete(fileName)` |
| `ConfigService` | 读 / 写 / 删配置项 | `set()` / `remove()` |

先说 `BackupService`。为了能演示"失败—重试—成功"这条真实链路，
它把**磁盘空间**显式建模了出来：

```java
public class BackupService {

    public record Backup(int id, String database, int sizeMb, String fileName) {}

    private final List<Backup> backups = new ArrayList<>();
    private int freeDiskMb;
    private int nextId = 1;

    public BackupService(int totalDiskMb) {
        if (totalDiskMb < 0) {
            throw new IllegalArgumentException("磁盘容量不能为负：" + totalDiskMb);
        }
        this.freeDiskMb = totalDiskMb;
    }

    public Backup backup(String database, int sizeMb) throws ServiceException {
        if (database == null || database.isBlank()) {
            throw new ServiceException("数据库名为空，无法备份");
        }
        if (sizeMb <= 0) {
            throw new ServiceException("备份体积必须为正数，实际 " + sizeMb + "MB");
        }
        if (sizeMb > freeDiskMb) {
            throw new ServiceException("磁盘空间不足：需要 " + sizeMb
                    + "MB，可用 " + freeDiskMb + "MB");
        }
        freeDiskMb -= sizeMb;
        int id = nextId++;
        Backup backup = new Backup(id, database, sizeMb, database + "_backup_" + id + ".bak");
        backups.add(backup);
        System.out.println("        └ 备份服务：生成 " + backup.fileName()
                + "（" + sizeMb + "MB），剩余磁盘 " + freeDiskMb + "MB");
        return backup;
    }
```

这段代码有四个设计决定值得逐条说清楚：

**① 为什么不用 `if (database.equals("logs_db")) throw ...` 假装失败？**

因为那是"为了演示而演示"。读者看不到失败的原因，也看不到重试为什么能成功。
有了真实的容量约束，整个过程就自洽了：

```
可用 500MB → 备份 orders_db(400MB) 成功，剩 100MB
           → 备份 logs_db(600MB) 失败（100 < 600）
运维清理 600MB → 可用 700MB → 重试成功，剩 100MB
```

**失败不是被"安排"的，是被容量算出来的。** 这个区别决定了整个 demo 的可信度。

**② 为什么 `backup()` 抛的是 `ServiceException` 而不是 `RuntimeException`？**

因为磁盘满是**业务失败**：它每天都会在真实系统里发生，调用方**必须显式决定**
——是重试、是告警、还是跳过。受检异常就是"编译器提醒你必须做这个决定"的机制。
（完整论证见 §4.5。）

**③ 为什么提供 `delete(int backupId)`？**

因为备份是**可撤销**的操作：删掉刚生成的备份文件、把磁盘空间还回去，就回到了执行前的状态。
**这才是 `BackupDatabaseCommand` 有可能实现 `UndoableCommand` 的真正原因。**

对照 `EmailService`——它没有 `recall()`。所以 `SendEmailCommand` 永远不可能是可撤销的。
记住这条推论：

> **能不能撤销，取决于 Receiver 有没有提供补偿能力，不取决于你想不想。**
> 模式文档里那些"给每个命令都加 undo"的示例，几乎都在骗人。

**④ 为什么删除要按 `id` 而不是按文件名？**

因为文件名可能重复（同名库备份两次都会生成 `x_backup_1.bak`、`x_backup_2.bak`……
但如果库名本身带下划线就容易撞）。`id` 是唯一的。
**「用唯一标识定位对象」是撤销操作的基本功**——撤销时靠"数据库名"去找，
很可能删掉别人的备份。

**最关键的一点**：请翻回上面 `BackupService` 的源码，找一下 `import` 列表——

```java
import java.util.ArrayList;
import java.util.List;
```

**没有任何一行和命令模式有关。** 它不知道 `Command` 存在，不知道 `TaskScheduler` 存在，
不知道谁会调它。这才叫"接收者不知道请求者的存在"，而不是嘴上说说。

它的可复用性直接体现在 `Client` 里：

```java
// demo2：直接调 Receiver（模拟运维平台的「立即备份」按钮）
backupService.backup("orders_db", 400);

// demo2：包成命令放进调度器
scheduler.submit(new BackupDatabaseCommand(backupService, "orders_db", 400));
```

**同一段业务逻辑，两种调用方式，业务代码零重复。** 这是原项目做不到的——
它只有第一条路，而且那条路必须经过 `new BackupTask()`。

---

### 4.3 第 2 步：`Command` 接口长什么样

四个 Receiver 写完了，但它们互不相关：`backup(String, int)`、`send(String, String, String)`、
`generate(String, int)`、`set(String, String)`——**签名完全不同，没法排进同一个队列**。
队列里必须放同一种类型，所以需要一层统一：

```java
public interface Command {

    String getName();

    void execute() throws CommandException;
}
```

**只有两个方法。** 每一个都能回答"删掉会怎样"：

#### 为什么是接口，不是抽象类？

| | 接口 | 抽象类 |
|---|---|---|
| 能否多继承 | ✅ 一个命令可以同时是 `Command` 和 `UndoableCommand` | ❌ 单继承，会占掉唯一的父类位置 |
| 有没有状态要共享 | 没有——每条命令的状态都是**自己的参数** | 有才需要 |
| 语义 | 「**能**被执行」是一种能力 | 「**是**一种东西」是一种分类 |

命令是"能力"，不是"分类"。所以用接口。
本项目真的用到了多继承式的组合（`UndoableCommand extends Command`），
如果是抽象类，这条路直接堵死。

#### 为什么 `execute()` 声明 `throws CommandException`（受检）？

这是本节最重要的一个决定，展开在 §4.5。这里先给一句话版本：

> **失败是命令的常态，不是意外。** 一条可能失败的操作，不能在签名里假装它不会失败。

#### 为什么有 `getName()`？

这个问题值得单独说，因为它看起来"只是打个日志"，实际上决定了整套系统可观测性的下限。

原项目的 Invoker 只能打印：

```
任务执行成功！
任务执行失败，记录日志以供重试...
```

**三行日志，零信息量。** 三十个任务并发跑的时候，这三行就是噪音。

有人会说：「让调用方在 `addTask()` 时多传一个名字不就行了？」

```java
scheduler.addTask(new BackupTask(), "备份 orders_db");   // 看起来也行？
```

不行。因为名字和命令是**两个会各自漂移的东西**：

- 参数改了（库名从 `orders_db` 改成 `orders_db_v2`），字符串还是老的；
- 命令被包进宏命令、被重试、被撤销，名字全靠调用方记得传；
- 从数据库里还原出一条命令（重放场景），**根本没有调用方**，谁来给它取名？

**名字必须由命令自己给出**，因为只有它知道自己的全部参数：

```java
@Override
public String getName() {
    return "备份数据库 " + database + "（" + sizeMb + "MB）";
}
```

于是调度器可以写出这一行**对所有命令都通用**的日志：

```java
System.out.println("  [" + sequence + "] " + command.getName());
```

输出是：

```
  [2] 备份数据库 logs_db（600MB）
        × 失败：磁盘空间不足：需要 600MB，可用 100MB
```

**谁、干了什么、结果如何、为什么失败**——四个问题一句半答完。

这也解释了 `getName()` 的约定为什么这么写：

| 写法 | 评价 |
|---|---|
| `"备份数据库 orders_db（400MB）"` | ✅ 运维人员看得懂 |
| `"BackupDatabaseCommand"` | ❌ 类名对运维没有意义 |
| `"备份"` | ❌ 哪个库？出问题还得翻调用栈 |
| `"执行任务"` | ❌ 等于没说 |

这和策略模式里 `DiscountStrategy.name()` 是同一个取舍：
**对象自己最了解自己，就该自己回答"我是谁"**，而不是让调用方硬编码字符串。

#### 为什么这里**没有** `undo()`？

因为"能撤销"是**部分**命令的能力，不是命令的本质属性。
把它塞进这个接口，等于强迫 `SendEmailCommand` 去实现一个它做不到的方法。
完整论证在 §4.7。

#### 接口里为什么不多加几个方法？

比如 `canUndo()`、`getPriority()`、`getRetryCount()`、`validate()`……

**每加一个方法，所有实现类都得实现一次，而绝大多数实现类会写个空实现或者 `return 0`。**
接口宽度是成本，不是功能。本项目全程只加了两个方法，
`UndoableCommand` 才加第三个——**而且它是另一个接口**。
这就是 ISP 在实际代码里的样子。

---

### 4.4 第 3 步：参数放哪

有了 `Command` 接口，第一个具体命令要解决一个问题：**参数怎么进对象？**

三种写法，都能编译：

```java
// 写法 A：字段 + setter
BackupDatabaseCommand cmd = new BackupDatabaseCommand(backupService);
cmd.setDatabase("orders_db");
cmd.setSizeMb(400);

// 写法 B：构造函数注入
BackupDatabaseCommand cmd = new BackupDatabaseCommand(backupService, "orders_db", 400);

// 写法 C：execute 时才传
cmd.execute("orders_db", 400);
```

**本项目全部用 B。** 三条理由：

**① 写法 A 允许"半成品对象"存在。**

```java
BackupDatabaseCommand cmd = new BackupDatabaseCommand(backupService);
scheduler.submit(cmd);        // 编译通过、运行到 execute() 才炸
```

这不是假设出来的场景。**忘了调 setter 是 setter 模式最常见的 bug**，
而编译器一句都提醒不了你。构造函数注入把"参数齐不齐"变成了**编译期问题**：
参数不全，`new` 不出来。

**② 写法 C 直接违背了模式的出发点。**

命令的价值在于"请求是一个**已经完整**的对象，可以被存放、被传递、被延后执行"。
如果参数要等到 `execute()` 才给，那这条命令在队列里就是一具空壳——
**队列里存的是"待填表"，不是"请求"**。序列化、重放、审计全部落空。

**③ 只有 B 能让参数是 `final` 的。**

看 `SendEmailCommand` 的字段声明：

```java
public class SendEmailCommand implements Command {

    private final EmailService emailService;
    private final String to;
    private final String subject;
    private final String body;

    public SendEmailCommand(EmailService emailService, String to, String subject, String body) {
        this.emailService = Objects.requireNonNull(emailService, "emailService 不能为 null");
        this.to = to;
        this.subject = subject;
        this.body = body;
    }
```

四个 `final` 带来的不只是"线程安全"这种抽象好处，还有三个具体的：

| `final` 保证 | 具体收益 |
|---|---|
| 参数不可变 | 命令被重试、被重新入队时，**执行的一定是同一个请求**，不会被人中途改掉 |
| 引用不可变 | 命令进撤销栈后，栈里的对象就是执行时的那个对象 |
| 无 setter | 类外无法构造"半成品" |

**顺带解答一个新人常问的问题：为什么 `emailService` 也要 `final` + `requireNonNull`？**

因为 `Receiver` 是命令的**依赖**，不是可选项。如果允许它为 `null`，
错误会推迟到 `execute()` 里变成一句 `NullPointerException`，
**而那时的堆栈里已经没有"是谁构造了这个命令"的信息了**。
在构造期校验，错误信息里直接写明 `"emailService 不能为 null"`，指向的是调用方那一行 `new`。

#### 一个副作用：「一个实例只能用一次」

参数是 `final` 的，但**撤销需要记住执行时的现场**（§4.8），
所以可撤销的命令一定会有可变字段。这带来一条约定：

> **一个命令实例只应执行一次；要重跑就重新构造一个对象。**

这条约定如果只是写在文档里，早晚有人违反。所以本项目让 Invoker 来保证它——
`retryFailed()` 重试时用的是**同一批命令对象**（因为它们从没成功过，现场还是干净的），
而 `CommandHistory` 里存的是**每次执行时新建的对象**。见 §5.6 与 §5.5。

---

### 4.5 第 4 步：异常怎么设计

这一节对应原项目最严重的问题（§2.6）：`catch (Exception e)` 吞掉一切。
修法不是"记得打印 e"，而是**先把"出错"这件事分类**。

#### 三类"出错"，必须分开处理

| 类别 | 例子 | 该不该重试 | 该不该让程序崩 | 本项目用 |
|---|---|---|---|---|
| **业务失败** | 磁盘满、数据源 0 行、收件人地址非法 | ✅ 环境修复后可以 | ❌ 崩溃解决不了磁盘满 | `ServiceException`（受检） |
| **程序 bug** | `NullPointerException`、`ClassCastException` | ❌ 重试一万次也一样 | ✅ 必须炸，让人去修 | **不捕获**，直接抛 |
| **补偿失败** | 撤销时配置中心连不上 | ❌ 补偿没有自动修复手段 | ⚠️ 视情况 | `IllegalStateException`（未受检） |

原项目的 `catch (Exception e)` 把前两类**塞进了同一个分支**，
于是"磁盘满"和"代码空指针"在日志里长得一模一样：`任务执行失败，记录日志以供重试...`。
报警器被和烟雾一起关掉了。

#### `ServiceException`：Receiver 的业务失败

```java
public class ServiceException extends Exception {

    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**它为什么是受检的？** 这是全章最需要想清楚的一个取舍。

支持"用非受检"的常见理由是：「受检异常会让代码到处 try-catch，很烦。」
这句话是对的，但它说的其实是**另一个问题**：不该**到处**捕获。

用受检异常的真实目的，是让**"必须做决定"这件事无法被忽略**：

```java
public Backup backup(String database, int sizeMb) throws ServiceException
```

看到这个签名，调用方只有三个选择，编译器逼着选一个：

| 选择 | 写法 | 适用场景 |
|---|---|---|
| 处理 | 就地 `try-catch` 降级 | 备份失败可以跳过、继续下一个库 |
| 上报 | 继续 `throws` 往上传 | 交给上层统一处理 |
| **翻译** | catch 后抛成另一种异常 | 本项目命令层做的事 |

如果 `ServiceException` 是非受检的，调用方**什么都不写也能编译通过**——
于是"忘了处理"和"故意不处理"在代码里长得一模一样。
**这正是原项目 `catch (Exception e) {}` 能存活至今的根本原因：没人被迫想过这件事。**

> **一句话判据**：这件事**每天都会发生**，而且后果需要人来决定 → 受检。
> 它**只可能因为写错代码而发生**（NPE、越界）→ 非受检。

#### `CommandException`：命令层的统一失败

```java
public class CommandException extends Exception {

    private static final long serialVersionUID = 1L;

    /** 出错命令的名称（结构化保留，不只是拼进字符串）。 */
    private final String commandName;

    /** 出错原因（不含命令名）。 */
    private final String detail;

    public CommandException(String commandName, String detail) {
        super(commandName + " 失败：" + detail);
        this.commandName = commandName;
        this.detail = detail;
    }

    public CommandException(String commandName, String detail, Throwable cause) {
        super(commandName + " 失败：" + detail, cause);
        this.commandName = commandName;
        this.detail = detail;
    }

    public String getCommandName() {
        return commandName;
    }

    public String getDetail() {
        return detail;
    }
}
```

**为什么要两个参数，而不是一个拼好的字符串？**

因为这条异常有**两类消费者**，它们要的东西不一样：

| 消费者 | 它已经知道什么 | 它需要什么 |
|---|---|---|
| 调度器的**汇总表 / 历史表** | 表格里已有"命令名称"这一列 | 只要**原因** |
| 没人接住的**堆栈** | 什么都不知道 | 需要**完整信息**：谁失败了、为什么 |

如果只存一个拼好的字符串，第二类消费者满意了，第一类消费者就会打出重复的命令名。
这不是推测——这是本项目**真实踩过的坑**，修好之前的真实输出是这样的：

```
  [2] 备份数据库 logs_db（600MB）
        × 失败：备份数据库 logs_db（600MB） 失败：磁盘空间不足：需要 600MB， 可用 100MB
```

（原因：命令里 `throw new CommandException(getName() + " 失败：" + e.getMessage(), e)`，
调度器又打印 `"× 失败：" + e.getMessage()`。）

同样的教训还有两处：汇总行「待重试 1 条」被改成了「**累计**待重试 1 条」
（否则 5.2 成功之后仍显示这一行，读者会以为它说的是本轮）；
而宏命令的失败原因被移到了补偿之前打印（§4.10）。三处都记在附录 D 的返工记录里。

改成双参数后：

- `getMessage()` 仍然是完整串 → 堆栈里自报家门，不依赖上下文；
- `getDetail()` 是纯净原因 → 表格里只打原因，不重复；
- `getCommandName()` 结构化可用 → 将来要按命令归类统计失败次数，不用拆字符串。

> **「能用结构表达的信息，就不要只存在字符串里。」**
> 这句话是异常设计的通用原则，和"别用 `Map<String, Object>` 传业务对象"是同一条。

#### 两层异常的翻译链

为什么不让 Receiver 直接抛 `CommandException`？为什么要多一层 `ServiceException`？

```
   ┌─────────────┐   抛 ServiceException     ┌──────────────┐
   │  Receiver   │ ────────────────────────▶ │    Command   │
   │（业务层）    │   "磁盘空间不足：需要…"    │（模式层）     │
   └─────────────┘                           └──────┬───────┘
         ▲                                          │ 翻译：catch ServiceException
         │ 不知道命令存在                             │      throw new CommandException(getName(), msg, e)
         │                                          ▼
         │                                   ┌──────────────┐
         └───────────────────────────────────│   Invoker    │
              只依赖 Command 接口              │ 只 catch      │
                                             │ CommandException
                                             └──────────────┘
```

三个理由：

**① 方向不能反。** `BackupService` 是业务组件，它将来可能被 HTTP 接口、定时任务、
命令行工具复用。如果它抛 `CommandException`，**每个调用方都被迫依赖命令模式**——
那 Receiver 就白抽了。

**② 语义不同。** `ServiceException` 说的是"这个业务操作没做成"；
`CommandException` 说的是"**这一条命令**没做成"，它多了"命令名称"这个维度。
后者是模式层的概念，前者是业务层的概念，混在一起就是层次穿透。

**③ 翻译点只有一个。** 每个具体命令的 `execute()` 里都是同一个形状：

```java
@Override
public void execute() throws CommandException {
    try {
        emailService.send(to, subject, body);
    } catch (ServiceException e) {
        throw new CommandException(getName(), e.getMessage(), e);
    }
}
```

三行，且**只翻译业务异常**。`NullPointerException` 不会被捕获，
它会一路穿过 `execute()`、穿过 `TaskScheduler.run()`，直接终止程序。
这不是疏忽，是设计——见 §6.3 的真实输出：

```
      NullPointerException：Cannot invoke "String.length()" because "cacheDir" is null
```

（注意 `"cacheDir"` 这个变量名：编译时加了 `-g` 才有。见附录 B。）

#### Invoker 只 `catch CommandException`

`TaskScheduler.run()` 的核心循环：

```java
try {
    command.execute();
    history.add(new ExecutionRecord(sequence, command.getName(), true, null));
    System.out.println("        √ 成功");
    ok++;
} catch (CommandException e) {
    history.add(new ExecutionRecord(sequence, command.getName(), false, e.getDetail()));
    failed.add(command);
    System.out.println("        × 失败：" + e.getDetail());
}
```

**没有 `catch (Exception e)`，也没有 `catch (RuntimeException e)`。**

`catch` 子句的宽度，就是"这个组件承诺处理什么"的边界。
写宽了，等于承诺自己能处理一切——包括它根本处理不了的空指针。
写窄了，才说明作者真的想过"哪些失败归我管"。

| 写法 | 承诺了什么 | 后果 |
|---|---|---|
| `catch (Exception e)` | 我能处理任何异常 | 把 bug 伪装成业务失败，日志失真 |
| `catch (RuntimeException e)` | 我能处理任何运行时异常 | 同上，只是少一类 |
| `catch (CommandException e)` | **我只管"这条命令没做成"** | bug 正常穿透，报警器还在 |

---

### 4.6 第 5 步：Invoker 怎么处理失败

现在命令会正确报错了，接下来的问题是：**谁来接住这些错误，接住之后做什么？**

原项目的 Invoker 只做了一件事——打印一句话，然后继续。本项目要做三件事：

1. 不中断后续任务（保留原项目做对的地方）；
2. 把失败的命令**留住**，可以重试；
3. 把每次执行记成**结构化记录**，可以审计。

#### 关键设计 1：快照执行，而不是 `while (!queue.isEmpty())`

```java
public int run() {
    if (queue.isEmpty()) {
        System.out.println("  [调度器] 队列为空，没有任务可执行");
        return 0;
    }

    List<Command> batch = new ArrayList<>(queue);   // ① 快照
    queue.clear();                                   // ② 清空

    int ok = 0;
    for (Command command : batch) {                  // ③ 只跑快照里的
        ...
    }
```

为什么不直接 `while (!queue.isEmpty()) { queue.poll(); ... }`？

**因为命令在执行过程中可能提交新命令。** 这不是假想场景：

```java
// 一条"备份失败就重试一次"的命令，执行时会把自己重新入队
public void execute() {
    if (!backup()) {
        scheduler.submit(new RetryBackupCommand(...));   // 执行中新命令入队
    }
}
```

如果边跑边 `poll()`，新提交的命令会被卷进**这一次** `run()` 里，形成不可预期的循环，
甚至可能死循环。快照的语义是明确的：

> **本次 `run()` 只处理"进入时已经在队列里"的那一批；执行期间新提交的，留给下一次。**

这和模板方法那一章的"步骤顺序要被锁死"是同一类思考：
**先把边界划清楚，再开始干活。**

#### 关键设计 2：只 `catch CommandException`，失败的命令留在 `failed` 里

```java
try {
    command.execute();
    history.add(new ExecutionRecord(sequence, command.getName(), true, null));
    System.out.println("        √ 成功");
    ok++;
} catch (CommandException e) {
    history.add(new ExecutionRecord(sequence, command.getName(), false, e.getDetail()));
    failed.add(command);                       // ← 原项目缺的就是这一行
    System.out.println("        × 失败：" + e.getDetail());
}
```

`failed.add(command)` 这一行，就是"重试"这个能力的全部秘密。

原项目用 `poll()` 取出后，方法一返回，**整个进程里再也没有任何引用指向那个任务对象**。
失败的任务不是"被标记为失败"，而是**彻底不存在了**——想重试也没有对象可重试。
这就是为什么探针里第二次 `run()` 一片空白。

有了这一行，重试就只是几行代码：

```java
public int retryFailed() {
    if (failed.isEmpty()) {
        System.out.println("  [调度器] 没有待重试的任务");
        return 0;
    }
    System.out.println("  [调度器] 重试 " + failed.size() + " 条失败任务");
    List<Command> retry = new ArrayList<>(failed);
    failed.clear();
    for (Command command : retry) {
        queue.offer(command);
    }
    return run();                    // 重新入队，再走一遍正常流程
}
```

**注意 `retryFailed()` 是"重新入队"而不是"直接执行"**。
这样重试和首次执行走的是**同一条代码路径**——日志格式、异常处理、历史记录全部一致。
如果单独写一套"重试专用"的执行逻辑，两条路早晚会分叉。

#### 关键设计 3：返回快照，不返回内部集合

```java
/** @return 失败待重试的命令（快照） */
public List<Command> getFailedCommands() {
    return List.copyOf(failed);
}

/** @return 执行历史（快照） */
public List<ExecutionRecord> getHistory() {
    return List.copyOf(history);
}
```

如果直接 `return failed;`，调用方拿到的是**内部列表的引用**，
它可以 `getFailedCommands().clear()` 把调度器的状态清空，而且编译器一点都不会警告。

`List.copyOf` 做的是**真拷贝**（不可变副本），不是 `Collections.unmodifiableList` 那种**视图**
（视图只是禁止你通过它修改，内部一变它跟着变）。
**对外暴露状态时，"快照"和"视图"的区别要心里有数。**

#### 为什么不记录耗时？

这是一个刻意的取舍，`TaskScheduler` 的类注释里也写了：

> `System.nanoTime()` 的差值会让每次运行输出都不一样，
> 而本项目的所有输出都要能被读者**原样复现**。

真实系统当然要记耗时（那是性能分析的基础）。但教学代码里，
一行 `耗时 0.3812ms` 会让整份文档的输出**永远无法对照**。
**什么时候该放弃一点真实性，是为了让另一件事更清楚——这个决定必须是有意识的。**

（顺带说一句：本项目最终用**两次运行输出的哈希比对**证明了完全可复现，
这是比"看起来一样"强得多的证据。见附录 B。）

---

### 4.7 第 6 步：`undo()` 该不该放进 `Command`

有了调度和重试，第三个需求出现了：**撤销**。

最直觉的做法是把 `undo()` 加进 `Command`：

```java
public interface Command {
    String getName();
    void execute() throws CommandException;

    default void undo() {
        throw new UnsupportedOperationException("本命令不支持撤销");
    }
}
```

**能编译、能跑、看起来还很"优雅"**（用了 `default`，实现类不用改）。
但它把三种东西混淆了：

| 混淆了什么 | 后果 |
|---|---|
| **能力** 与 **属性** | "能撤销"是部分命令的**能力**，被写成了所有命令都有的**属性** |
| **编译期** 与 **运行期** | IDE 里人人都有 `undo()`，真相要读实现或者运行时才知道 |
| **显式** 与 **隐式** | `instanceof` 能做的事，被降级成了 `try-catch` 兜底 |

具体的代价：

```java
// 想给"可撤销的命令"在界面上显示一个撤销按钮
for (Command cmd : history) {
    if (cmd.canUndo()) { ... }        // 没有这个方法
}

// 只能靠运行时试探
try {
    cmd.undo();
} catch (UnsupportedOperationException e) {
    // 原来这条不能撤销……但我可能已经把它执行了一半？
}
```

**最后一个注释不是玩笑**：如果 `undo()` 在抛出之前已经做了一半动作，
你连"状态是否被破坏"都判断不了。

#### 本项目的做法：拆成两个接口

```java
public interface UndoableCommand extends Command {
    void undo();
}
```

于是"能不能撤销"变成了**类型事实**，可以在编译期确定的分支里处理：

```java
if (step instanceof UndoableCommand undoable) {    // 类型即证据，一次完成判断 + 转型
    undoable.undo();
}
```

这正是 **ISP（接口隔离原则）**：不应该强迫使用者依赖它用不到的接口。
`SendEmailCommand` 不需要知道 `undo` 这个词存在，所以它不该被强制实现。

#### 本项目的实际分布

| 命令 | 实现 `Command` | 实现 `UndoableCommand` | 为什么 |
|---|---|---|---|
| `SendEmailCommand` | ✅ | ❌ | 邮件发出去收不回来 |
| `BackupDatabaseCommand` | ✅ | ✅ | `BackupService.delete(id)` 能删掉备份 |
| `GenerateReportCommand` | ✅ | ✅ | `ReportService.delete(fileName)` 能删报表 |
| `UpdateConfigCommand` | ✅ | ✅ | `ConfigService.set()` 能改回旧值 |
| `MacroCommand` | ✅ | ❌ | 里面含一封不可撤销的邮件 |

**四比一。** 如果当初把 `undo()` 塞进 `Command`，
`SendEmailCommand` 就得写一个抛异常的 `undo()`——**那是一种谎言的标准化**。

#### 一个反直觉的收获

拆开之后，`Command` 接口**完全不知道撤销这回事**。
但 `CommandHistory` 只接受 `UndoableCommand`：

```java
public void execute(UndoableCommand command) throws CommandException
```

于是"进撤销栈的命令一定是可撤销的"这件事**由编译器保证**，
不需要任何运行期检查、不需要任何文档约定。
**类型系统能替你做的事，就不要交给注释。**

---

### 4.8 第 7 步：undo 的现场怎么捕获

这一节是命令模式**最容易写出隐形 bug** 的地方。三个坑，一个比一个隐蔽。

#### 坑 1：不能在构造函数里捕获

```java
public UpdateConfigCommand(ConfigService cfg, String key, String newValue) {
    this.previousValue = cfg.get(key);      // ❌ 错
    ...
}
```

三个理由：

1. **命令构造出来和执行之间，世界会变。** 命令可能在队列里躺 5 分钟，
   这 5 分钟里别人改过这个配置，捕获的旧值就过期了；
2. **构造期不应该有副作用 / 依赖外部状态**。`new` 一个对象就读取了配置中心，
   这在单测里是灾难；
3. **命令可能永远不会执行**（被取消、被丢弃），那这次读取就是白干。

#### 坑 2：不能在 `undo()` 里才去查

```java
@Override
public void undo() {
    String old = configService.get(key);    // ❌ 查到的已经是新值
    configService.set(key, old);            // 等于什么都没做
}
```

`undo()` 被调用时，**副作用已经发生了**。这时候去读，读到的是"被自己改过之后"的状态。
更麻烦的是：**它不会报任何错**。日志里写着"撤销完成"，实际什么都没变。

#### 坑 3：必须在 `execute()` 里，且**顺序不能反**

正确写法，顺序是命门：

```java
@Override
public void execute() throws CommandException {
    // ① 先捕获现场（顺序不能反，见类注释）
    previousValue = configService.get(key);
    // ② 再修改
    configService.set(key, newValue);
    // ③ 标记成功：放在 set 之后而不是之前。
    executed = true;
}
```

如果写成 `set()` 再 `get()`，捕获到的就是新值——
**撤销时"把值改回新值"，看起来执行了，实际什么都没变**。
这种 bug 不会抛异常、不会打日志、测试如果不比对快照也发现不了。

> **一句话原则：捕获现场必须发生在产生副作用之前，而且必须是同一行代码的紧邻。**
> 不要指望"我记得在那个方法里捕获过"——写在一起，才不会被后人挪走。

#### 现场里的 `null` 是什么含义？

```java
/** 执行前的旧值；{@code null} 表示<b>这个键原本不存在</b>。 */
private String previousValue;

@Override
public void undo() {
    if (!executed) {
        throw new IllegalStateException(getName() + " 尚未执行，不能撤销");
    }
    if (previousValue == null) {
        configService.remove(key);          // 原本不存在 → 删掉，不是设成空串
    } else {
        configService.set(key, previousValue);
    }
}
```

`null` 在这里的含义是**"这个键原本不存在"**，而不是"值为空"。
正确的补偿是 `remove(key)`：让系统回到"压根没这个配置"的状态。

如果错写成 `set(key, "")`，配置里就会多出一个**空串键**——
下游读到"显式配置为空"，行为和"没有配置"可能完全不同。

这个坑有个著名的近亲：**用 `0` 表示"没有"**。
模板方法那一章里用 `-1` 表示"库存未检查"，用的是同一个道理——
**"没有值"和"值是零/空"必须能区分开。**

#### `executed` 标志放在哪？

```java
private boolean executed;

@Override
public void execute() {
    previousValue = configService.get(key);
    configService.set(key, newValue);
    executed = true;              // ③ 放在 set 之后
}

@Override
public void undo() {
    if (!executed) {
        throw new IllegalStateException(getName() + " 尚未执行，不能撤销");
    }
    ...
}
```

放在 `set()` **之前**是一个典型错误：如果 `set()` 抛异常，配置根本没改，
但命令已经认为"我执行过了"，于是 `undo()` 会被允许——**撤销一个从未发生的操作**。

还有一种约定需要说清楚：**`undo()` 里不把 `executed` 置回 `false`**。

```java
// 注意：这里不把 executed 置回 false。
// 因为 undo 之后还可以 redo（= 再 execute 一次），而 execute 会重新置 true。
// 置回 false 反而会让"undo → redo → undo"这条链断掉。
```

#### 需要防重复撤销吗？看是否幂等

这是个需要**逐操作判断**的问题，不能一刀切：

| 命令 | 补偿动作 | 幂等？ | 需要防护吗 |
|---|---|---|---|
| `UpdateConfigCommand` | 把值改回旧值 | ✅ 做两次结果一样 | ❌ 不需要 |
| `BackupDatabaseCommand` | 删除备份文件 | ❌ 第二次删会失败 | ✅ 需要 |

所以 `BackupDatabaseCommand` 用了一个"销毁现场"的写法：

```java
@Override
public void undo() {
    if (created == null) {
        throw new IllegalStateException(getName() + " 尚未成功执行，或已经撤销过，不能重复撤销");
    }
    backupService.delete(created.id());
    created = null;              // ← 撤销完把现场清空，第二次进来就会被上面拦住
}
```

**注意 `created = null` 这一行**：它不只是一个"标志位"，而是**真的把引用丢掉了**。
这既防住了重复撤销，也防止了内存里留着一个已经不存在于磁盘的备份对象。

> **判据不是"习惯"，而是"这个操作重复执行会不会出问题"。**
> 幂等的操作加防护是多余的；非幂等的操作不加防护是定时炸弹。

#### 顺带说清楚 redo 的语义

本项目的 redo 是**"重新执行"（re-execute）**，不是**"重放"（replay）**：

```
execute() → previousValue = "10s"
undo()    → 恢复 "10s"
execute() → previousValue = "10s"     ← 重新捕获，捕获到的还是它
```

两者在"两次执行之间有人手工改过配置"时才会分叉：

| 时间线 | re-execute（本项目） | replay（用第一次快照） |
|---|---|---|
| 执行，旧值 `10s` | 记下 `10s` | 记下 `10s` |
| 撤销 | 恢复 `10s` | 恢复 `10s` |
| **有人手工改成 `20s`** | —— | —— |
| 重做 | 记下 `20s` | 仍然记着 `10s` |
| 再撤销 | 回到 `20s` ✅ | 回到 `10s` ❌ 抹掉了别人的修改 |

对配置变更来说，**本项目的选择是对的**：撤销应该回到"我刚才改之前的样子"，
而不是强行抹掉别人在这期间做的修改。

但对"转账"这类操作就必须用重放——因为第二次执行时账户余额已经变了，
用新余额算出来的"撤销金额"是错的。**语义选择取决于业务，没有普适答案。**

---

### 4.9 第 8 步：撤销栈放哪

最后一个问题：**谁来记住"执行过哪些命令、按什么顺序撤销"？**

直觉做法是让调用方自己维护：

```java
Deque<UndoableCommand> undoStack = new ArrayDeque<>();
undoStack.push(cmd);      // 执行后
UndoableCommand c = undoStack.pop();
c.undo();
```

能跑。但它把**三条不变量**交给了"每个调用点都要记得"：

```java
public class CommandHistory {

    private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
    private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();

    public void execute(UndoableCommand command) throws CommandException {
        System.out.println("  [历史] 执行：" + command.getName());
        command.execute();                  // ① 先执行
        undoStack.push(command);            // ② 成功了才入栈
        redoStack.clear();                  // ③ 新分支产生，旧的 redo 路径作废
        ...
    }
```

**`execute()` 和 `push()` 挨在一起，就是第一条不变量的实现方式**：
失败时 `execute()` 抛异常，`push()` 根本不会执行——**结构上就不可能出现"执行失败还进了撤销栈"**。

三条不变量分别是：

| # | 不变量 | 违反后的症状 | 本项目怎么保证 |
|---|---|---|---|
| 1 | **只有成功的命令才能进撤销栈** | 撤销一个从未发生的操作，把系统改坏 | `execute()` 和 `push()` 相邻，异常天然跳过 push |
| 2 | **执行新命令必须清空重做栈** | 撤销栈和重做栈里同时存在互相矛盾的历史 | `execute()` 里 `redoStack.clear()` |
| 3 | **重做失败的命令必须放回重做栈** | 命令既不在 undo 栈也不在 redo 栈，**凭空消失** | `redo()` 里 `catch` 后 `redoStack.push(command)` 再 `throw` |

第 2 条可以用 Git 类比：

```
   A ── B ── C        ← 撤销两次，你想回到 A
        ↑
        你现在在这里

   如果你此时提交 D：
   A ── B ── C        （C 还在，但已经不在当前分支上）
        └── D         ← 新的历史从这里开始

   → 原来的 C 变成"无法重做的未来"，因为它不在新分支上
```

第 3 条最容易被漏掉，代码是这样的：

```java
public boolean redo() throws CommandException {
    if (redoStack.isEmpty()) { ... return false; }
    UndoableCommand command = redoStack.pop();       // ← 先弹出来
    try {
        command.execute();
    } catch (CommandException e) {
        // 【必须放回去】弹出来又失败，如果不还回去，
        // 这条命令就既不在 undoStack 也不在 redoStack —— 凭空消失了。
        redoStack.push(command);
        throw e;
    }
    undoStack.push(command);
    return true;
}
```

**这就是"先 `pop` 后执行"必须配套 `catch` 回滚的原因。**
如果用 `peek()` 先看一眼，成功了再 `pop`，就不会有这个问题——
但那样"检查—执行—移除"三步之间就有了时间窗口。
两种写法都能对，**关键是知道自己在防什么**。

#### 为什么栈不放进命令里？

```java
// ❌ 反面写法
public interface UndoableCommand {
    void undo();
    Deque<UndoableCommand> getStack();     // 命令知道自己在一个栈里
}
```

因为**职责不同**：
- 命令的职责是"知道怎么撤销**自己**"；
- 历史的职责是"知道撤销的**顺序**"。

把栈塞进命令，等于让每个命令都依赖撤销栈这个全局结构——
**那就没法单独测试一个命令了**（测试 `UpdateConfigCommand` 却要构造一整个历史栈？）。

而且一旦命令能访问栈，它就能在 `undo()` 里偷偷 `push` 别的命令，
**撤销栈的内容就不再由历史独占了**。这类"共享可变状态"是 bug 的温床。

#### 为什么用 `ArrayDeque` 而不是 `ArrayList`？

```java
private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
```

`ArrayDeque` 的 `push/pop` 操作的是**栈顶**（内部就是 `addFirst/removeFirst`），
`O(1)`，语义正好是 LIFO。

用 `ArrayList` 也能凑：

```java
list.add(cmd);                        // 入栈
Command c = list.remove(list.size() - 1);   // 出栈
```

能跑，但**你是在手动实现一个栈**，还多了一处 `size() - 1` 的边界。
JDK 已经有专门的数据结构，就不该自己造——这和"不要用 `String` 拼 SQL"是同一类判断。

#### 一个附带的收获：`printStacks()` 让状态机可视化

```java
public void printStacks() {
    System.out.println("  [历史] 撤销栈（栈顶→底）：" + undoStack.stream()
            .map(Command::getName).toList());
    System.out.println("  [历史] 重做栈（栈顶→底）：" + redoStack.stream()
            .map(Command::getName).toList());
}
```

输出长这样（demo4 的真实输出，撤销一次 + 重做一次之后）：

```
  [历史] 撤销栈（栈顶→底）：[修改配置 payment.timeout = 30s]
  [历史] 重做栈（栈顶→底）：[修改配置 feature.newCheckout = on]
```

一眼就能看出状态机的形状：`payment.timeout` 在撤销栈顶（可以撤），
`feature.newCheckout` 在重做栈顶（可以重做回去）。

**能看见两个栈的形状，撤销/重做就不再是抽象概念了。**
这是本类最值钱的 8 行代码——它把"不变量是否被破坏"变成了肉眼可验证的东西。

---

### 4.10 第 9 步：宏命令

最后一个需求：**发布一个版本要四步一起做，失败要一起撤回。**

没有宏命令时，调用方只能这么写：

```java
// ❌ 每个调用点都要自己写一遍补偿逻辑
try {
    config.set("release.version", version);
    try {
        backup.backup("orders_db", 400);
        try {
            report.generate("发布核对报表", 1520);
            email.send("team@shop.com", "发布完成", ...);
        } catch (...) { report.delete(...); backup.delete(...); config.set(...); }
    } catch (...) { config.set(...); }
} catch (...) { ... }
```

三层嵌套的 `try-catch`，**而且每个调用点都长得差不多、改一次要改五处**。

宏命令把这段逻辑收进一个类：

```java
public class MacroCommand implements Command {

    private final String name;
    private final List<Command> steps;

    public MacroCommand(String name, Command... steps) {
        if (steps == null || steps.length == 0) {
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
                done.add(step);       // 只有成功了才记入 done
            } catch (CommandException e) {
                System.out.println("      ! 第 " + (i + 1) + " 步失败：" + e.getDetail());
                int rolledBack = rollback(done);
                throw new CommandException(getName(),
                        "第 " + (i + 1) + "/" + steps.size() + " 步（" + step.getName()
                                + "）失败，已补偿 " + rolledBack + " 步：" + e.getDetail(), e);
            }
        }
    }
```

#### 四个设计决定

**① 为什么是"逆序"补偿？**

```java
private int rollback(List<Command> done) {
    int count = 0;
    ListIterator<Command> it = done.listIterator(done.size());
    while (it.hasPrevious()) {
        Command step = it.previous();
        ...
    }
}
```

因为步骤之间通常有**依赖**：报表是基于备份的数据生成的，配置变更又是整个流程的前提。
回滚必须从最后一步往回走，否则会出现"备份已经删了，却还在用备份做报表回滚"的荒谬情况。
**这和后缀表达式求值、栈的弹出顺序、`undo` 栈的 LIFO 都是同一个道理。**

**② 为什么只补偿 `done` 里的步骤？**

```java
done.add(step);      // 只有 execute() 成功返回了才走到这里
```

失败的那一步**不需要补偿**——它自己就没改成任何东西（或者已经自行回滚）。
如果把它也塞进 `done`，补偿时会去撤销一个未完成的动作——
`BackupDatabaseCommand.undo()` 会因为 `created == null` 抛 `IllegalStateException`，
**把整个补偿流程再炸一次**。

**③ 为什么失败原因要先打印再补偿？**

```java
System.out.println("      ! 第 " + (i + 1) + " 步失败：" + e.getDetail());
int rolledBack = rollback(done);
```

本项目第一版是先补偿再抛异常。这是**修复前的真实输出**：

```
      步骤 3/4：生成报表「发布核对报表」
      ← 补偿：备份数据库 orders_db（400MB）
        └ 备份服务：删除 orders_db_backup_1.bak，回收 400MB，剩余磁盘 2000MB
      ← 补偿：修改配置 release.version = v2.3.1
        └ 配置服务：移除 release.version（原值 v2.3.1）
        × 失败：发布 v2.3.1 在第 3/4 步失败（已补偿 2 步）：生成报表「发布核对报表」 失败：数据源 0 行，报表「发布核对报表」未生成
```

读者先看到一串"补偿"，**却不知道为什么补偿**——失败原因被埋在最底下，
而且还把命令名和"失败"二字重复了两遍（`发布 v2.3.1 在第 3/4 步失败` + `生成报表「…」 失败`）。

修法两步：①把原因打印提前；②把异常信息改成"第几步、哪条步骤、补偿了几步、根因是什么"。
**日志的阅读顺序，就是读者的思考顺序。顺序错了，信息就白给了。**

**④ 为什么本类不实现 `UndoableCommand`？**

因为它的步骤里包含 `SendEmailCommand`——**邮件发出去收不回来**。

> 一条命令能不能撤销，取决于**它的每一步能不能撤销**；
> 只要有一个不可逆的步骤，整体就不可撤销。

**承认这一点，比硬写一个会骗人的 `undo()` 诚实得多。**
如果为了"统一"而勉强实现，`undo()` 里只能做两件事之一：跳过邮件（用户以为撤销了，实际没撤）、
或者抛异常（那和 `default undo()` 的反面写法没区别）。

#### 由此推出的工程原则：不可逆的操作放最后

demo5 的步骤顺序是「改配置 → 备份 → 报表 → **发邮件**」。
所以报表失败时，前两步能完整回滚，而邮件**压根还没发**。

如果把发邮件挪到第二步：

```
步骤 1：改配置     ✅
步骤 2：发邮件     ✅ ← 客户已收到「发布成功」
步骤 3：备份       ❌ 磁盘满
步骤 4：报表       （不执行）

补偿：配置回滚了、备份没生成……
但客户手里那封「发布成功」的邮件，永远收不回来。
```

**这种事故在真实复盘里非常常见。** 原则只有一句：

> **把不可逆的操作（发通知、扣款、调外部 API、发 MQ 消息）放在流程的最后一步。**

#### 一个诚实的提醒：这叫补偿，不叫事务回滚

| | 数据库事务（2PC） | Saga 补偿（本项目） |
|---|---|---|
| 中间状态对外可见吗 | ❌ 锁住资源，别人看不见 | ✅ **看得见** |
| 一致性 | 强一致 | **最终一致** |
| 失败后 | `ROLLBACK`，像什么都没发生 | 逐个调用补偿动作，**痕迹留在日志和外部系统里** |

在第 1 步做完、第 2 步失败的那段时间里，外部系统**看得见**中间状态——
数据库里就是有一个新备份，配置中心里 `release.version` 就是新的。
补偿只能做到"最终把它们改回去"。

**所以补偿只适用于"中间状态短暂可见、且可以容忍"的场景。**
支付、库存扣减这类不能容忍中间态的业务，要靠数据库事务或者真正的分布式事务方案，
**不能靠命令模式的 `undo()` 顶上去**。

---

### 4.11 十个决策汇总

把 §4.2–§4.10 的每一步压成一张表，方便回头查阅：

| # | 决策 | 选择了 | 放弃了什么 | 关键理由 |
|---|---|---|---|---|
| 1 | 业务逻辑放哪 | **独立的 Receiver** | 少写几个类 | 业务能力要被多处复用；命令只做委托 + 翻译 |
| 2 | 统一类型 | **`Command` 接口（2 个方法）** | 抽象类的共享状态 | 能力不是分类；要给 `UndoableCommand` 留位置 |
| 3 | 日志定位 | **`getName()` 由命令自己给** | 调用方传字符串 | 只有命令知道自己的全部参数；重放场景没有调用方 |
| 4 | 参数怎么进 | **构造器注入 + `final`** | setter、方法传参 | 消灭"半成品对象"；参数不全会编译失败 |
| 5 | 异常怎么分 | **`ServiceException` → `CommandException` 两层** | 一种通用异常 | 业务失败要显式决定；bug 必须穿透；Invoker 只 catch 后者 |
| 6 | 失败任务怎么留 | **`failed` 列表 + 重新入队重试** | `poll()` 消费即弃 | 失败的任务是审计记录、是重试依据；`poll()` 让对象消失 |
| 7 | undo 放哪 | **拆 `UndoableCommand`** | `default undo()` 抛异常 | 让"能不能撤销"成为类型事实，而不是运行期试探 |
| 8 | 现场何时捕获 | **`execute()` 里、副作用之前** | 构造函数 / `undo()` 里查 | 前两者一个会过期、一个已经被改掉 |
| 9 | 撤销栈谁管 | **`CommandHistory`（双栈 + 三条不变量）** | 调用方各自维护 | 不变量必须收进一个类，否则每个调用点都要记得 |
| 10 | 一组命令 | **`MacroCommand`（逆序补偿、只补 `done`）** | 调用方写嵌套 try | 组合让"一条"和"一组"被同等对待；不可逆步骤放最后 |

**这十行里有九行是"用模式"之外的工程判断。**
模式只提供了 `Command`、`UndoableCommand`、`MacroCommand` 三个名字，
剩下七个决策都在解决"怎么让它真的能用"。

> 这也是为什么"背下 23 种模式的类图"没有用——
> **真正的功夫在类图之外那些必须做对的细节里。**

---

## 5. 代码解析：每个文件为什么存在

### 5.0 十六个文件的全景

先看全局。16 个文件按职责分成六组：

```
                       ┌──────────────────────────┐
                       │  Client（334 行）         │  创建 Receiver、创建命令、
                       │  客户端 / 五段 demo       │  把命令交给 Invoker
                       └────────────┬─────────────┘
                                    │ new
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
     ┌────────────────┐   ┌──────────────────┐   ┌─────────────────┐
     │ Receiver ×4    │   │ Command ×5       │   │ Invoker ×1      │
     │ EmailService   │◀──│ SendEmail        │──▶│ TaskScheduler   │
     │ BackupService  │◀──│ BackupDatabase   │   │ 队列 + failed   │
     │ ReportService  │◀──│ GenerateReport   │   │ + history       │
     │ ConfigService  │◀──│ UpdateConfig     │   └────────┬────────┘
     └────────────────┘   │ MacroCommand     │            │
             ▲            └────────┬─────────┘            │
             │                     │ implements           │ 只依赖
             │            ┌────────▼─────────┐            │ 接口
             │            │ Command（102）    │◀───────────┘
             │            │ UndoableCommand  │
             │            └────────┬─────────┘
             │                     │ throw
             │            ┌────────▼─────────┐   ┌─────────────────┐
             │            │ CommandException │◀──│ ServiceException│
             └────────────┴──────────────────┘   └─────────────────┘
                        （命令层翻译）              （Receiver 抛）
                                              
                        ┌──────────────────┐
                        │ CommandHistory   │  双栈，只管 UndoableCommand
                        └──────────────────┘
```

文件清单（共 **1923 行**）：

| 分组 | 文件 | 行数 | 一句话职责 | 删掉会怎样 |
|---|---|---|---|---|
| **抽象层** | `Command` | 102 | 声明 `getName()` + `execute()` | 队列没有元素类型，模式不存在 |
| | `UndoableCommand` | 82 | 追加 `undo()` | 撤销只能靠运行期试探 |
| **异常** | `ServiceException` | 58 | Receiver 的业务失败 | 业务失败与 bug 无法区分 |
| | `CommandException` | 114 | 命令层的失败（含命令名 + 原因） | Invoker 无法只捕获"业务失败" |
| **Receiver** | `EmailService` | 82 | 发邮件（不可撤销） | 宏命令就没有"不可逆步骤"这个教材 |
| | `BackupService` | 140 | 备份 / 删备份 / 管磁盘 | 失败—重试—撤销三条线全部塌掉 |
| | `ReportService` | 86 | 生成 / 删报表 | 宏命令失败点无处安放 |
| | `ConfigService` | 123 | 读 / 写 / 删配置 | 没有干净的 undo 载体 |
| **具体命令** | `SendEmailCommand` | 79 | 发邮件 | 缺一个"不可撤销"的正面例子 |
| | `BackupDatabaseCommand` | 76 | 备份 + 非幂等 undo | 缺一个"要防重复撤销"的例子 |
| | `GenerateReportCommand` | 69 | 生成报表 + undo | 宏命令失败点消失 |
| | `UpdateConfigCommand` | 108 | 改配置 + 幂等 undo | 缺最标准的 undo 教材 |
| | `MacroCommand` | 124 | 组合 + 逆序补偿 | "一组命令当一条用"消失 |
| **基础设施** | `TaskScheduler` | 211 | 队列 / 执行 / 失败留存 / 审计 | 没有 Invoker |
| | `CommandHistory` | 135 | undo / redo 双栈 | 撤销栈散落到调用方 |
| **入口** | `Client` | 334 | 五段 demo | 不知道怎么用 |

**注意一个比例**：真正的"模式骨架"只有 `Command` + `UndoableCommand` + `MacroCommand` 三个文件（308 行），
剩下 1615 行全是**让模式能真正跑起来的业务代码、异常、调度器和 demo**。

这个比例值得记住：

> **学模式时看到的是骨架，用模式时要写的是血肉。**
> 骨架三行类图就能画完，血肉要写一天。

---

### 5.1 `Command` 与 `UndoableCommand`

§4.3 已经讲透了接口的形状，这里补三件事：**契约、代价、删掉会怎样**。

#### `Command` 的三条契约

接口的 Javadoc 里写了三条，它们不是"建议"，而是**所有实现类必须遵守的承诺**：

```java
/**
 * <p><b>契约</b>：
 * <ol>
 *   <li><b>失败必须通过 {@link CommandException} 报出</b>，
 *       不允许像原项目那样在命令内部 {@code catch} 掉再打印一行字——
 *       那会让 Invoker 误以为任务成功了。</li>
 *   <li><b>程序 bug 不要捕获</b>（{@code NullPointerException}、{@code IndexOutOfBoundsException}），
 *       让它们直接抛出去。</li>
 *   <li>命令可以持有<b>可变状态</b>……
 *       因此<b>一个命令实例只应执行一次</b>；要重跑就重新构造一个对象。</li>
 * </ol>
 */
```

一条一条对应回代码：

| 契约 | 违反的例子 | 后果 |
|---|---|---|
| 失败必须抛 `CommandException` | `try { ... } catch (Exception e) { System.out.println("失败"); }` | Invoker 记成「成功」，重试机制失效 |
| bug 不要捕获 | `catch (RuntimeException e) { throw new CommandException(...); }` | NPE 被伪装成"业务失败"，重试一万次也没用 |
| 一个实例只用一次 | 把同一个命令对象 `submit()` 两次 | 第二次执行覆盖第一次的现场，撤销删错文件 |

第三条最容易被忽略，因为它**不会立刻报错**。
本项目的对策写在 §4.4：`retryFailed()` 只重试**从没成功过**的命令（现场字段还是空的），
`CommandHistory` 里存的是**每次执行时新建的对象**。**约束靠结构保证，不靠人的记性。**

#### `UndoableCommand` 的代价：命令变成有状态的了

```java
@Override
public void execute() throws CommandException {
    previousValue = configService.get(key);   // ← 这一行让命令有状态了
    configService.set(key, newValue);
    executed = true;
}
```

一旦命令有了可变字段，就多出两条硬约束：

| 约束 | 原因 | 违反了会怎样 |
|---|---|---|
| **一个实例只能用一次** | 重复执行会覆盖第一次记下的现场 | 撤销时删掉的是第二次生成的文件，第一次的成了孤儿 |
| **不能跨线程共享** | 两个线程同时执行同一实例，字段互相踩 | 撤销时用 A 的旧值去改 B 的键 |

所以在真实系统里，撤销栈里放的是**每次执行时新建的命令对象**，
而不是复用一个"命令单例"。

> **这就是"无状态服务"和"有状态命令"的区别。**
> Service 层通常被设计成无状态的（可以随便复用、随便并发），
> 而命令一旦可撤销，就**主动变成了有状态的**——
> 这不是设计失误，而是"记住现场"这件事的必然代价。

#### 删掉 `UndoableCommand` 会怎样？

假设我们退回单一接口 + `default undo()` 的写法，本项目会发生三件事：

1. `SendEmailCommand` 必须写 `undo()`，只能抛 `UnsupportedOperationException`；
2. `MacroCommand.rollback()` 里不能再写 `if (step instanceof UndoableCommand undoable)`，
   只能写 `try { step.undo(); } catch (UnsupportedOperationException e) { /* 跳过 */ }`；
3. `CommandHistory.execute()` 无法在编译期保证"进栈的一定能撤销"，
   只能接受任意 `Command`，然后在 `undo()` 时运行期炸。

**三处都从"编译期确定"退化成了"运行期试探"。**
这就是 ISP 在这个项目里最具体、可度量的价值。

---

### 5.2 两个异常类

`ServiceException` 58 行，`CommandException` 114 行。**差了整整一倍，差在哪？**

```java
// ServiceException：58 行 —— 44 行注释 + 4 行空行，实际代码只有 10 行
public class ServiceException extends Exception {
    private static final long serialVersionUID = 1L;

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
// CommandException：114 行，多出来的全是"结构化"的成本
public class CommandException extends Exception {
    private final String commandName;
    private final String detail;

    public CommandException(String commandName, String detail) {
        super(commandName + " 失败：" + detail);
        this.commandName = commandName;
        this.detail = detail;
    }
    ...
}
```

#### 为什么一个是单参数，一个是双参数？

因为它们的**消费者不同**：

| 异常 | 谁抛 | 谁接 | 需要什么信息 |
|---|---|---|---|
| `ServiceException` | Receiver（业务层） | 具体命令（马上翻译掉） | 只要一句人话原因 |
| `CommandException` | 具体命令（模式层） | Invoker（要记录/展示/统计） | 命令名 + 原因，**分开存** |

`ServiceException` 的生命周期极短：Receiver 抛出 → 命令的 `catch` 接住 → 立刻翻译成 `CommandException`。
**它不需要携带结构，因为没人会去读它的字段。**

`CommandException` 的生命周期长得多：它会被存进 `ExecutionRecord`、打印到日志、
将来还可能被序列化进告警系统。**它必须能被程序处理，而不只是被人读。**

#### 为什么不直接继承 `RuntimeException`？

一句话：**受检异常是"必须做决定"的机制**（完整论证见 §4.5）。

但这里要补充一个反面场景，否则这条原则会被用过头：

> **如果异常要穿过 5 层调用栈，而中间 4 层都只会 `throws` 往上抛，那受检异常就是纯负担。**

这就是为什么 Spring 全家桶、Hibernate 都改用了 `RuntimeException`：
它们的异常要穿过框架的无数层拦截器，逼每一层声明异常是不现实的。

**判据是"异常会在哪一层被处理"**：

| 场景 | 建议 |
|---|---|
| 异常在 1-2 层内就被处理（本项目：Receiver → 命令 → Invoker） | 受检 ✅ |
| 异常要穿过大量框架代码，最终由一个全局处理器统一接 | 非受检更合适 |

**本项目之所以用受检，是因为调用链很短、每一层都有明确的处理动作。**
换成一个 Spring Web 应用，我会毫不犹豫地改成非受检 + `@ControllerAdvice`。

#### 要不要再细分异常子类？

初学者常问：「要不要建 `BackupFailedException`、`EmailFailedException`……？」

**本项目没有，因为分类信息已经用别的方式表达了**：

```java
history.add(new ExecutionRecord(sequence, command.getName(), false, e.getDetail()));
```

`commandName` 就是分类维度的天然载体——想统计"备份失败了几次"，
按 `commandName` 分组就行，不需要为每种命令建一个异常类。

**什么时候才该加子类？** 当不同异常需要**不同的处理策略**时：

```java
// 假设需求变成：网络类失败自动重试 3 次，数据类失败直接告警
if (e instanceof RetryableException) {
    retryLater(command);
} else {
    alert(command, e);
}
```

这时候用一个**标记接口**（`Retryable`）比继承层次更合适——
因为"是否可重试"是**能力**，不是**分类**。这和 `UndoableCommand` 是同一个思路。

**在需求出现之前，一个异常类就够了。**

---

### 5.3 四个 Receiver

四个 Receiver 合计 431 行，是整个项目里业务逻辑的全部所在。
它们的共同特征写在 `EmailService` 的类注释里：

> **这个类里没有出现任何与模式相关的词**：没有 `Command`、没有 `execute`、没有队列、没有调度。
> 它就是一个普通业务类：谁调用它都行——定时任务、HTTP 接口、单元测试、命令行工具。

下面每个 Receiver 挑一个最关键的设计点。

#### `EmailService`：业务规则放在 Receiver，不放在命令里

```java
public void send(String to, String subject, String body) throws ServiceException {
    if (to == null || to.isBlank()) {
        throw new ServiceException("收件人为空，邮件未发送");
    }
    if (!to.contains("@")) {
        throw new ServiceException("收件人地址非法：" + to);
    }
    outbox.add(new SentMail(to, subject, body));
    System.out.println("        └ 邮件服务：已投递 → " + to + "（标题「" + subject + "」）");
}
```

**「什么样的地址算合法」是邮件服务的业务规则，不是"发送邮件这条命令"的规则。**

这个区分有非常实际的价值：

| 规则写在哪 | 改动影响范围 | 新调用方是否自动受保护 |
|---|---|---|
| Receiver（本项目） | 改一处 | ✅ 自动 |
| 每条命令里各写一遍 | 改 N 处 | ❌ 忘了写就没有 |

假设明天产品说「只允许公司内网域名」，改 `EmailService.send()` 一个方法，
**所有命令、所有调用方自动受益**。如果校验写在 `SendEmailCommand` 里，
将来多一个 `SendBatchEmailCommand` 就得抄一遍，**抄漏的那天就出事**。

`EmailService` 还承担了另一个角色：**证明"不是所有操作都能撤销"**。
它没有 `recall()`。所以 `SendEmailCommand` 只能是 `Command`——
这个"缺失"是整个 demo5（不可逆步骤放最后）成立的物理前提。

> **一个类的"没有某个方法"，有时比"有某个方法"更重要。**
> 这里的"没有 `recall()`"直接决定了整个宏命令无法实现 `UndoableCommand`。

#### `BackupService`：把失败建模成"算出来的"，而不是"安排出来的"

```java
private int freeDiskMb;

public Backup backup(String database, int sizeMb) throws ServiceException {
    if (database == null || database.isBlank()) {
        throw new ServiceException("数据库名为空，无法备份");
    }
    if (sizeMb <= 0) {
        throw new ServiceException("备份体积必须为正数，实际 " + sizeMb + "MB");
    }
    if (sizeMb > freeDiskMb) {
        throw new ServiceException("磁盘空间不足：需要 " + sizeMb
                + "MB，可用 " + freeDiskMb + "MB");
    }
    freeDiskMb -= sizeMb;
    ...
}
```

对比一下"为了演示而演示"的写法：

```java
// ❌ 假失败
if (database.equals("logs_db")) {
    throw new ServiceException("备份失败");
}
```

两种写法的差别不在代码量，而在**读者能不能相信这个 demo**：

| | 假失败 | 容量建模（本项目） |
|---|---|---|
| 失败原因可解释吗 | ❌ 为什么 logs_db 就失败？ | ✅ 100MB 存不下 600MB |
| 为什么重试能成功 | ❌ 只能写"清理后就好了" | ✅ 释放 600MB 后 700 > 600 |
| 为什么撤销能还原磁盘 | ❌ 无所谓磁盘 | ✅ `delete()` 里 `freeDiskMb += sizeMb` |
| 读者能自己改参数验证吗 | ❌ 改不了 | ✅ 把 500 改成 2000，整个 demo 变成功路径 |

**第四行是最有价值的**：读者可以自己改一个数字，观察整个流程走另一条分支。
这才是"可实验的教学代码"，而不是"只能照着看的输出"。

`delete()` 那个方法也值得看一眼：

```java
public void delete(int backupId) {
    Backup found = null;
    for (Backup b : backups) {
        if (b.id() == backupId) { found = b; break; }
    }
    if (found == null) {
        // 这是"撤销逻辑写错了"，属于程序 bug，用未受检异常直接炸掉
        throw new IllegalStateException("备份不存在，无法删除：id=" + backupId
                + "（当前存在：" + backups.stream().map(Backup::fileName).toList() + "）");
    }
    backups.remove(found);
    freeDiskMb += found.sizeMb();
    ...
}
```

**它抛的是 `IllegalStateException`（非受检），不是 `ServiceException`（受检）。**

为什么？因为"要删的备份不存在"**不可能是业务场景**——它只能意味着撤销逻辑写错了。
业务失败需要调用方做决定，而**程序 bug 只需要炸**。

> 一个方法抛什么异常，暴露了作者对"这个错误是谁的错"的判断。
> 抛 `ServiceException` = 「客户/环境可能这样，请处理」；
> 抛 `IllegalStateException` = 「我写错了，别接，让我死」。

#### `ReportService`：`LinkedHashMap` 保证输出可复现

```java
/** 用 LinkedHashMap 保证输出顺序稳定、可复现（HashMap 的顺序不保证，会让文档对不上）。 */
private final Map<String, Report> files = new LinkedHashMap<>();
```

这一行注释背后是本项目反复出现的一条纪律：**所有要展示出来的集合，底层容器必须有序**。

在报告"当前有哪些报表"时：

```java
public Map<String, Report> getFiles() {
    return Map.copyOf(files);
}
```

这里 `Map.copyOf` 有一个**容易踩的坑**：它返回的是**不可变副本**，
但按 JDK 文档，**迭代顺序是不确定的**（`Map.copyOf` 不保证保留原 Map 的顺序）。
本项目只在 demo 里打印**键集合用于报错**，没有依赖它的顺序遍历——
如果真要按插入顺序展示，应该用 `Collections.unmodifiableMap(new LinkedHashMap<>(files))`
（这就是 `ConfigService.snapshot()` 的写法）。

**同一个"返回副本"的需求，两种写法都出现过，因为要的东西不一样**：

| 需求 | 写法 | 保证 |
|---|---|---|
| 只要"不可变的快照" | `List.copyOf` / `Map.copyOf` | 内容不变 |
| 还要"顺序稳定可复现" | `new LinkedHashMap<>(map)` 再包视图 | 内容不变 + 顺序确定 |

`rows <= 0` 抛异常这一条也值得注意：

```java
if (rows <= 0) {
    throw new ServiceException("数据源 0 行，报表「" + name + "」未生成");
}
```

真实系统里"数据源没数据"是**每天都会发生的业务事件**（节假日没订单、新库还没数据），
所以它是 `ServiceException`。这条规则放在 `ReportService` 里，任何调用方都自动受保护。

#### `ConfigService`：`null`、空串、快照与视图

这个 123 行的类里塞了四个容易搞错的点，每个都用一行注释钉住了。

**① `null` 表示"键不存在"，不是"值为空"**

```java
/**
 * @param key 键
 * @return 配置值；<b>{@code null} 表示该键不存在</b>
 */
public String get(String key) {
    return config.get(key);
}
```

于是撤销时才能正确区分两种情况（详见 §4.8）：

```
原来有 key，值 "10s"  → 撤销：set(key, "10s")
原来没有 key           → 撤销：remove(key)      ← 不是 set(key, "")
```

**② `set()` 明确拒绝 `null`，逼调用方用 `remove()`**

```java
public void set(String key, String value) {
    if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("配置键不能为空");
    }
    if (value == null) {
        throw new IllegalArgumentException("配置值不能为 null；如需删除请调用 remove()");
    }
    ...
}
```

如果允许 `set(key, null)`，那么"设成空"和"删掉"这两件不同的事就有了**两种表达方式**，
而底层 `LinkedHashMap.put(k, null)` 和 `remove(k)` 的语义其实不一样（`put` 之后 `containsKey` 仍是 true）。

**用 API 的签名把"两种意图"分开**，比在文档里写"请不要传 null"可靠得多。

**③ `Map.of()` 的迭代顺序不确定 → 加了 `String...` 构造器**

```java
public ConfigService(String... keyValues) {
    if (keyValues == null || keyValues.length % 2 != 0) {
        throw new IllegalArgumentException("初始化参数必须成对出现（键, 值），实际个数："
                + (keyValues == null ? "null" : keyValues.length));
    }
    for (int i = 0; i < keyValues.length; i += 2) {
        config.put(keyValues[i], keyValues[i + 1]);
    }
}
```

为什么非要多这一个构造器？因为 `Map.of()` 内部带哈希盐，**迭代顺序不确定**，
同一个程序两次运行都可能不同。一旦 `snapshot()` 被打印进文档，输出就永远对不上。

> **凡是要展示的内容，底层容器必须有序。**

这句纪律在本项目里出现过三次：`ConfigService`（LinkedHashMap）、`ReportService`（LinkedHashMap）、
`TaskScheduler`（`ArrayDeque` 保序）。**它不是洁癖，是文档可验证性的前提。**

**④ `unmodifiableMap` 是视图，不是快照**

```java
public Map<String, String> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(config));
}
```

对比两种写法：

```java
// ❌ 只是视图：调用方拿到后，本服务再改配置，它看到的内容跟着变
return Collections.unmodifiableMap(config);

// ✅ 先复制再包：拿到的是"调用那一刻"的快照
return Collections.unmodifiableMap(new LinkedHashMap<>(config));
```

这个区别在 demo 里很直观：

```
执行前快照：{release.version=v2.3.0, payment.timeout=10s}
（执行 4 条命令）
执行后快照：{release.version=v2.3.1, payment.timeout=30s}
```

如果 `snapshot()` 返回的是视图，**执行前的快照也会变成执行后的样子**——
两个快照打印出来一模一样，整个 demo 就失去意义了。

> **`unmodifiable` 只解决"你不能改我"，不解决"我改了你也会看到"。**
> 要后者，必须复制。

#### 四个 Receiver 的异常边界汇总

这张表把"谁抛什么"理清楚，也顺便说明了每一处的判断依据：

| Receiver.方法 | 抛什么 | 为什么 |
|---|---|---|
| `EmailService.send` | `ServiceException` | 地址为空/格式不对 = 业务失败，调用方要决定重发还是跳过 |
| `BackupService.backup` | `ServiceException` | 磁盘满 = 环境问题，运维清理后可重试 |
| `BackupService.delete` | `IllegalStateException` | 要删的备份不存在 = 撤销逻辑写错了，是 bug |
| `BackupService.freeDisk` | `IllegalArgumentException` | 释放负空间 = 调用方传参错误 |
| `ReportService.generate` | `ServiceException` | 0 行数据 = 业务常态 |
| `ReportService.delete` | `IllegalStateException` | 同上，撤销逻辑 bug |
| `ConfigService.set` | `IllegalArgumentException` | 键空 / 值为 null = 调用方用法错误 |
| `ConfigService.remove` | 不抛（静默） | 删一个不存在的东西，结果与预期一致 → **幂等** |

最后一行不是偷懒：`remove` 的语义是"执行后这个 key 不存在"，
那么"本来就不存在"和"删掉了"满足同一个后置条件。**幂等的操作不该报错**，
否则调用方每次都得写 `if (containsKey)` 才敢删。

---

### 5.4 五个具体命令

#### 可撤销命令的骨架是完全固定的

把三个可撤销命令摆在一起看，会发现它们的形状**一模一样**。
`GenerateReportCommand` 的类注释直接把这个骨架写成了公式：

```java
/**
 *   execute() ──▶ 调用接收者 ──▶ 把返回值/现场存进字段
 *   undo()    ──▶ 检查字段是否有效 ──▶ 用字段里的信息调用接收者的补偿方法 ──▶ 清空字段
 */
```

代码是这样的：

```java
private String createdFileName;      // 现场字段

@Override
public void execute() throws CommandException {
    try {
        createdFileName = reportService.generate(reportName, rows).fileName();
    } catch (ServiceException e) {
        // 失败时 createdFileName 保持 null → 不允许被撤销
        throw new CommandException(getName(), e.getMessage(), e);
    }
}

@Override
public void undo() {
    if (createdFileName == null) {
        throw new IllegalStateException(getName() + " 尚未成功执行，或已经撤销过，不能重复撤销");
    }
    reportService.delete(createdFileName);
    createdFileName = null;
}
```

**两个方法各自只有一个"必须做对"的地方**：

| 方法 | 必须做对的事 | 做错会怎样 |
|---|---|---|
| `execute()` | 把现场**存进字段**，且 `catch` 里**不赋值** | 撤销时无信息可用 / 失败的命令也被允许撤销 |
| `undo()` | 用**唯一标识**调补偿方法，然后**清空字段** | 删错对象 / 可以重复撤销 |

不同的只是"补偿方法"叫什么：`delete(id)`、`delete(fileName)`、恢复旧值、反向转账。
**认出这个骨架，写可撤销命令就变成了填空题。**

#### 五个命令的对照表

| 命令 | Receiver | 参数（`final`） | 现场字段 | undo 动作 | 幂等 | 实现 `UndoableCommand` |
|---|---|---|---|---|---|---|
| `SendEmailCommand` | `EmailService` | to / subject / body | —— | **无法补偿** | —— | ❌ |
| `BackupDatabaseCommand` | `BackupService` | database / sizeMb | `Backup created` | `delete(created.id())` | ❌ | ✅ |
| `GenerateReportCommand` | `ReportService` | reportName / rows | `String createdFileName` | `delete(createdFileName)` | ❌ | ✅ |
| `UpdateConfigCommand` | `ConfigService` | key / newValue | `previousValue` + `executed` | 改回旧值 / `remove()` | ✅ | ✅ |
| `MacroCommand` | 无（组合了 4 条命令） | `List<Command> steps` | `done`（局部） | 逆序补偿 | —— | ❌ |

**这张表里有两个"❌"，它们都是诚实的**：

- `SendEmailCommand` 不可撤销，因为 `EmailService` 没有 `recall()`；
- `MacroCommand` 不可撤销，因为它包含 `SendEmailCommand`。

#### `SendEmailCommand`：只有 20 行业务代码，但它是最重要的教材

```java
@Override
public String getName() {
    return "发送邮件 → " + to;
}

@Override
public void execute() throws CommandException {
    try {
        emailService.send(to, subject, body);
    } catch (ServiceException e) {
        throw new CommandException(getName(), e.getMessage(), e);
    }
}
```

**这个类只有 20 行业务代码，但它体现了命令模式最关键的三件事**（类注释原文）：

1. **它自己不干活。** `execute()` 里只做一件事——调用 `EmailService.send`。
   如果哪天发邮件的逻辑要改（换 SMTP、加重试、加模板），**改的是 Receiver，这个类一行都不用动**。
2. **参数是字段，所以"请求"变成了"数据"。**
   这三个参数本来只能出现在方法调用的栈上，调用一结束就没了。
   现在它们是对象的字段，于是这条请求可以**排队、落库、重放**。
3. **它不实现 `UndoableCommand`。** 因为邮件发出去就收不回来。

第 3 点是整个项目里**最容易被"改进"掉的地方**。总有人觉得"统一一点更好"，给它加个空 `undo()`。
加完之后，`MacroCommand` 就能实现 `UndoableCommand` 了——**而那个 `undo()` 会骗人**。

#### `BackupDatabaseCommand`：非幂等补偿的防护

```java
private Backup created;      // 现场：备份服务返回的记录（含 id、文件名）

@Override
public void execute() throws CommandException {
    try {
        created = backupService.backup(database, sizeMb);
    } catch (ServiceException e) {
        // 注意这里没有给 created 赋值 —— 失败时它保持 null，
        // 于是 undo() 会正确地拒绝撤销（没成功的事，谈不上撤销）。
        throw new CommandException(getName(), e.getMessage(), e);
    }
}

@Override
public void undo() {
    if (created == null) {
        throw new IllegalStateException(getName() + " 尚未成功执行，或已经撤销过，不能重复撤销");
    }
    backupService.delete(created.id());
    created = null;
}
```

`created` 这一个字段同时承担三个职责，这是**刻意的复用**：

| 场景 | `created` 的值 | `undo()` 的行为 |
|---|---|---|
| 从未执行 | `null` | 抛 `IllegalStateException`（用法错误） |
| 执行失败 | `null`（catch 里没赋值） | 抛 `IllegalStateException`（没成功，不能撤） |
| 执行成功 | 有值 | 正常删除 |
| 已撤销过一次 | `null`（undo 末尾清空） | 抛 `IllegalStateException`（不能重复撤） |

**"未执行"和"已撤销"共用 `null`，会不会混淆？**

不会——因为**两者的正确响应是同一个**：拒绝。区分它们没有业务价值。
这是一个很好的判断练习：**不是所有状态都需要区分，只有会导致不同行为的才需要。**
（对比 §4.8 里 `null` 与 `""` 必须区分，因为那会导致**不同的动作**：`remove` vs `set`。）

#### `UpdateConfigCommand`：`executed` 与 `previousValue` 两个字段

```java
private boolean executed;         // 是否已经成功执行过
private String previousValue;     // 执行前的旧值；null 表示"这个键原本不存在"
```

为什么这个类需要**两个**字段，而上面两个只需要一个？

| 类 | 需要的字段 | 原因 |
|---|---|---|
| `BackupDatabaseCommand` | `created`（1 个） | 现场本身就能表示"有没有执行成功"（非 null = 成功） |
| `GenerateReportCommand` | `createdFileName`（1 个） | 同上 |
| `UpdateConfigCommand` | `previousValue` + `executed`（2 个） | **`previousValue == null` 有歧义**：既可能"没执行过"，也可能"执行过但键原本不存在" |

**这就是多出 `executed` 的全部原因。** 一个布尔位，解决了一个值域歧义。

这个细节值得单独记一笔，因为它是"**用一个值表示多种状态**"的经典陷阱：

```java
// ❌ 危险：null 既表示"没执行"，又表示"键原本不存在"
if (previousValue == null) { ... }

// ✅ 安全：先问"执行过吗"，再问"原来有值吗"
if (!executed) { throw new IllegalStateException(...); }
if (previousValue == null) { configService.remove(key); }
else { configService.set(key, previousValue); }
```

> 这和模板方法那一章用 `-1` 而不是 `0` 表示"库存未检查"是同一个思路：
> **当一个变量的值域需要表达 N 种含义时，先检查值域够不够。** 不够就加一个字段，别硬塞。

#### `MacroCommand`：一个"不实现接口"带来的连锁反应

```java
public class MacroCommand implements Command {     // ← 注意：不是 UndoableCommand
    ...
}
```

这个选择会连锁影响到三个地方：

| 位置 | 影响 |
|---|---|
| `TaskScheduler` | 宏命令和普通命令一视同仁，**无需任何特殊处理** ✅ |
| `CommandHistory` | 宏命令进不了撤销栈——**编译器直接拒绝** ✅ |
| `Client` demo5 | 必须显式说明"为什么这条流程不能撤销" ✅ |

第三行是**最有价值的**：因为类型上不能撤销，所以 demo 里不得不写清楚原因
（"最后一步不可撤销 → 所以它必须排在最后"）。
**如果硬写一个能编译的 `undo()`，这段解释就永远不会被写出来，也就永远不会有人想过它。**

> **类型系统最大的价值，不是防止你写错，而是逼你把理由说出来。**

---

### 5.5 `CommandHistory`：为什么它不能和 `TaskScheduler` 合并

两个类都"执行命令"，看起来职责重叠。但它们**执行完之后做的事完全相反**：

| | `TaskScheduler`（Invoker） | `CommandHistory`（撤销栈管理者） |
|---|---|---|
| 执行顺序 | **按提交顺序**（FIFO） | **按逆序撤销**（LIFO） |
| 执行范围 | 队列里的一**批** | 撤销栈顶的一**条** |
| 失败时 | 继续跑后面的，把失败的留档 | **立刻上抛**，不做任何补偿 |
| 关注的结果 | "这批任务跑完了吗" | "哪一步可以回退" |
| 接受类型 | 任何 `Command` | 只有 `UndoableCommand` |

尤其是**失败处理相反**这一点：

```java
// TaskScheduler：失败不中断，留下重试
catch (CommandException e) {
    failed.add(command);
    System.out.println("        × 失败：" + e.getDetail());
}
```

```java
// CommandHistory：失败直接上抛，绝不继续
public void execute(UndoableCommand command) throws CommandException {
    System.out.println("  [历史] 执行：" + command.getName());
    command.execute();          // ← 不 catch。失败了就不该进撤销栈
    undoStack.push(command);
    redoStack.clear();
}
```

**批量调度和撤销历史，是两种完全不同的一致性要求。**
批量的哲学是"能跑多少跑多少"；撤销的哲学是"要么干净地成功，要么一步都别记"。
把它们合成一个类，只有一种结果：两种哲学互相污染。

`CommandHistory.execute()` 里"不 catch"这个设计还带来一个结构性的好处：

```java
command.execute();          // ① 抛异常就到此为止
undoStack.push(command);    // ② 这句话只有成功才会执行
redoStack.clear();          // ③ 顺序不能换到前面
```

**"只有成功的命令才能进撤销栈"这条不变量，是靠"语句顺序 + 不捕获异常"实现的，不是靠 if 判断。**
如果写成：

```java
// ❌ 靠标志位维护不变量，多了一个可能出错的环节
boolean ok = false;
try { command.execute(); ok = true; } catch (CommandException e) { ... }
if (ok) { undoStack.push(command); }
```

能跑，但多了一个 `ok` 变量、多了一个分支、多了一处可能写错的地方。
**能靠结构表达的，不要靠变量。** 这和模板方法那一章的"把流程锁在父类里"是同一种思路。

---

### 5.6 `TaskScheduler`：三个字段撑起整个 Invoker

去掉注释之后，这个 211 行的类只有三个字段：

```java
private final Deque<Command> queue = new ArrayDeque<>();       // 待执行
private final List<ExecutionRecord> history = new ArrayList<>(); // 执行史（审计）
private final List<Command> failed = new ArrayList<>();         // 失败待重试
private int sequence = 0;                                       // 执行序号
```

对应三种责任：

| 字段 | 回答什么问题 | 谁在读 |
|---|---|---|
| `queue` | 还有多少要跑？ | `run()`、`pendingCount()` |
| `history` | 过去发生了什么？ | `printHistory()`、`getHistory()` |
| `failed` | 哪些还没搞定？ | `retryFailed()`、`getFailedCommands()` |
| `sequence` | 这是第几次执行？ | `ExecutionRecord.seq` |

**为什么 `sequence` 是"全局递增"而不是"每批从 1 开始"？**

因为审计要的是**时间线**，不是"批次内的编号"。demo2 里同一个命令执行了两次：

```
  1. 备份数据库 orders_db（400MB） → √ 成功
  2. 备份数据库 logs_db（600MB） → × 磁盘空间不足：需要 600MB，可用 100MB
  3. 生成报表「每日销售报表」 → √ 成功
  4. 发送邮件 → ops@shop.com → √ 成功
  ...
  5. 备份数据库 logs_db（600MB） → √ 成功
```

第 5 条和第 2 条是**同一条命令的两次尝试**，序号把它们串成了一条可读的时间线。
如果每批重新从 1 开始，这份历史就变成了两份互不相干的表。

#### `ExecutionRecord` 用 `record` 而不是普通类

```java
public record ExecutionRecord(int seq, String commandName, boolean success, String errorMessage) {
}
```

四个字段、零行为、不可变——**这正是 `record` 的适用场景**。
写成普通类会需要：4 个字段 + 1 个构造器 + 4 个 getter + `equals`/`hashCode`/`toString`，
大约 40 行样板，而语义完全一样。

| 什么时候用 `record` | 什么时候不要用 |
|---|---|
| 纯数据载体，没有行为 | 有状态需要修改（`record` 字段是 `final`） |
| 天然不可变（审计记录、快照、结果） | 需要继承别的类 |
| 字段就是全部语义 | 需要懒加载、需要可变集合字段 |

`ExecutionRecord` 三条全中，所以它是 `record`。

#### `printHistory()` 为什么"追加而不覆盖"

```java
public void printHistory() {
    System.out.println("  ┌─ 执行历史 ────────────────────────────────");
    for (ExecutionRecord r : history) {
        String result = r.success() ? "√ 成功" : "× " + r.errorMessage();
        System.out.println("  │ " + r.seq() + ". " + r.commandName() + " → " + result);
    }
    System.out.println("  └───────────────────────────────────────────");
}
```

它只是**遍历全部历史**，没有"这次只打印本次批次"的逻辑。这是刻意的：

> **重试不是把失败抹掉，而是追加一次新的尝试——审计要的就是这个。**

如果 `retryFailed()` 成功之后把第 2 条的失败记录删掉，历史就变成了"一次成功"，
**那这份历史就不是审计记录，而是宣传材料**。
真实的事故复盘需要的恰恰是"先失败了、后来成功了、中间做了什么"。

#### 一个刻意的缺失：没有 `cancel()`

真实的调度器通常有"取消某条任务"的能力。本项目没做，原因有两条：

1. **`queue` 是 `Deque<Command>`，`remove(Object)` 依赖 `equals()`**，
   而命令对象没有重写 `equals`（默认是引用相等），语义上是"取消我手上这个对象"——
   这在 demo 里没有说服力；
2. **取消一条已经执行了一部分的命令，需要 `undo()`**，
   那就把调度器和撤销历史搅在一起了，违背 §5.5 说的职责分离。

**知道"为什么不做"和"怎么做"一样重要。** 一个教学项目里最危险的东西，
是那些"看起来该有所以随手加上去"的功能——它们会让核心机制变模糊。

---

### 5.7 `Client`：五段 demo，每段回答一个问题

`Client` 是 334 行，是项目里最长的文件。它的 Javadoc 开头就把结构写清楚了：

```java
/**
 * <ol>
 *   <li>{@link #demo1()} —— Invoker 到底解耦了什么？</li>
 *   <li>{@link #demo2()} —— 一条命令失败，整批任务怎么办？失败的任务去哪了？</li>
 *   <li>{@link #demo3()} —— 业务失败和程序 bug 为什么要分开？</li>
 *   <li>{@link #demo4()} —— 撤销/重做的三条不变量是怎么被保证的？</li>
 *   <li>{@link #demo5()} —— 多条命令要"一起成功或一起回滚"怎么办？</li>
 * </ol>
 */
```

| demo | 回答的问题 | 关键道具 | 对应章节 |
|---|---|---|---|
| 1 | Invoker 到底解耦了什么？ | 三条不同命令进同一个队列，3/3 成功 | §4.3、§4.6 |
| 2 | 失败的任务去哪了？ | 500MB 磁盘 + 400MB 成功 + 600MB 失败 + `freeDisk(600)` 后重试 | §4.6 |
| 3 | bug 为什么要和业务失败分开？ | `bad-address` + 一条**故意有 NPE** 的匿名命令 | §4.5 |
| 4 | 三条不变量怎么保证的？ | `CommandHistory` + `printStacks()` | §4.9 |
| 5 | 一组命令怎么一起回滚？ | `MacroCommand` 四步，第 3 步 0 行失败 | §4.10 |

#### `demo3` 里那条"有 bug 的命令"为什么用匿名类？

```java
private static Command buggyCommand() {
    return new Command() {
        @Override
        public String getName() {
            return "缓存预热（代码有 bug）";
        }

        @Override
        public void execute() {
            // 模拟真实 bug：配置没配就返回 null，而代码忘了判空
            String cacheDir = System.getProperty("app.cache.dir");
            System.out.println("        └ 缓存目录长度：" + cacheDir.length());
        }
    };
}
```

两个理由：

1. **它只是一次性的演示道具**，没有复用价值，不值得占一个文件；
2. **它恰好证明了 `Command` 是个接口**——任何对象只要实现两个方法，就能被调度器执行，
   不需要继承什么基类、不需要注册、不需要配置文件。

第二点多说一句：这是**接口相对于抽象类的一个具体优势**。
如果 `Command` 是抽象类，`new Command() { ... }` 这种写法依然可以（匿名子类），
但如果某条命令还需要继承别的类（比如 `AbstractJob`），就彻底堵死了。
**"能力用接口、状态用抽象类"这条原则，在这里有了一个活生生的例子。**

#### 为什么要构造一个真实的 NPE，而不是 `throw new RuntimeException("模拟 bug")`？

因为**手写的异常和真实的 NPE 在日志里长得不一样**。
本项目用 `System.getProperty("app.cache.dir")` 返回 `null` 来制造真实的 NPE，
再配合 `-g` 编译，输出是这样的：

```
      NullPointerException：Cannot invoke "String.length()" because "cacheDir" is null
```

这行信息**直接指出了变量名 `cacheDir`**（JDK 14+ 的 Helpful NullPointerException）。
如果写成 `throw new RuntimeException("模拟 bug")`，日志只会说"模拟 bug"——
**读者感受不到"真实 bug 被埋掉"有多可怕**。

> **教学示例的可信度，取决于它有多接近真实事故。**

#### `printTitle()`：为什么用 `"=".repeat(70)`

```java
private static void printTitle(String title) {
    System.out.println();
    System.out.println("=".repeat(70));
    System.out.println(title);
    System.out.println("=".repeat(70));
}
```

类注释里给了理由：手敲 70 个等号容易数错，改长度要改多处。
这看起来是件小事，但它背后是一致性：**五段 demo 的分隔线必须完全一致**，
否则输出哈希比对就不可能通过（见附录 B 的可复现性验证）。

**一行 `"=".repeat(70)`，换来的是"整个输出文件可以被打哈希比对"。**
这是"小处不将就"带来的真实收益。

---

## 6. 执行流程：五段 Demo 的真实运行结果

本章的输出**全部是真实运行得到的**（`javac --release 25 -encoding UTF-8 -g -Xlint:all`
零警告，`RUN-EXIT=0`，共 **252 行**，四次运行输出哈希完全一致）。
复现命令见附录 B。

### 6.0 入口与整体结构

```java
public static void main(String[] args) throws CommandException {
    demo1();
    demo2();
    demo3();
    demo4();
    demo5();
    System.out.println();
    System.out.println("全部演示结束。");
}
```

五段 demo **完全独立**：每一段都自己 `new` 出需要的 Receiver 和 Invoker，
不共享任何状态。这一点很重要——

| 如果不独立会怎样 | 本项目的做法 |
|---|---|
| demo1 备份耗掉 400MB，demo2 的"500MB 刚好不够"就算不对 | 每段自己 `new BackupService(...)` |
| demo3 的 NPE 可能污染 demo4 的历史 | 每段自己 `new TaskScheduler()` |
| 读者无法单独跑某一段 | 每段可以单独调用 |

**每个 demo 是一个自包含的实验**，而不是"一个连续的故事"。
教学代码里这比"状态复用"重要得多——**读者要能改一个参数重跑，而不是读完整篇才能理解。**

---

### 6.1 demo1：Invoker 到底解耦了什么

#### 对象创建顺序

```java
EmailService email = new EmailService();          // ① outbox = 空 ArrayList
BackupService backup = new BackupService(2000);   // ② freeDiskMb = 2000, nextId = 1
ReportService report = new ReportService();       // ③ files = 空 LinkedHashMap

TaskScheduler scheduler = new TaskScheduler();    // ④ queue / history / failed 全空，sequence = 0

scheduler.submit(new BackupDatabaseCommand(backup, "orders_db", 400));              // ⑤
scheduler.submit(new GenerateReportCommand(report, "每日销售报表", 1520));          // ⑥
scheduler.submit(new SendEmailCommand(email, "ops@shop.com", "备份完成", "orders_db 已备份")); // ⑦
```

**注意 ⑤⑥⑦ 的顺序**：先 `new` 命令（参数在构造器里固定），再 `submit`。
这就是"请求被封装成对象"的字面含义——**对象在入队之前就已经是完整的**。

`submit()` 只做两件事：

```java
public void submit(Command command) {
    if (command == null) {
        throw new IllegalArgumentException("command 不能为 null");
    }
    queue.offer(command);
    System.out.println("  [调度器] 入队：" + command.getName() + "（待执行 " + queue.size() + " 条）");
}
```

**入队，不执行。** 这一行 `queue.offer` 就是"请求的提出与执行在时间上解耦"的全部实现。

#### `run()` 里的关键变量变化

| 时刻 | `sequence` | `batch.size()` | `queue.size()` | `history.size()` | `failed.size()` |
|---|---|---|---|---|---|
| 进入 `run()` | 0 | —— | 3 | 0 | 0 |
| 快照后 | 0 | 3 | **0** | 0 | 0 |
| 第 1 条成功 | 1 | 3 | 0 | 1 | 0 |
| 第 2 条成功 | 2 | 3 | 0 | 2 | 0 |
| 第 3 条成功 | 3 | 3 | 0 | 3 | 0 |
| 返回 | 3 | 3 | 0 | 3 | 0 |

返回值为 `ok = 3`。

#### 真实输出（33 行）

```

======================================================================
1. 基本调度：Invoker 完全不认识 Receiver
======================================================================
  客户端手上有 3 个互不相干的服务（Receiver）：
      EmailService / BackupService / ReportService
  它们都不知道「命令」「调度器」的存在 —— 就是普通业务类。

  [调度器] 入队：备份数据库 orders_db（400MB）（待执行 1 条）
  [调度器] 入队：生成报表「每日销售报表」（待执行 2 条）
  [调度器] 入队：发送邮件 → ops@shop.com（待执行 3 条）

  调度器执行时，内部只做这一件事（它眼里只有 Command 接口）：
      for (Command c : batch) {
          c.execute();
      }

  [1] 备份数据库 orders_db（400MB）
        └ 备份服务：生成 orders_db_backup_1.bak（400MB），剩余磁盘 1600MB
        √ 成功

  [2] 生成报表「每日销售报表」
        └ 报表服务：生成 每日销售报表_1520行.csv（1520 行）
        √ 成功

  [3] 发送邮件 → ops@shop.com
        └ 邮件服务：已投递 → ops@shop.com（标题「备份完成」）
        √ 成功

  [调度器] 本轮结束：共 3 条，成功 3 条，失败 0 条，累计待重试 0 条

  >>> 3/3 成功。整段输出里，调度器的代码没有出现任何 Receiver 的类名，
      也没有 instanceof。新增一种命令时，TaskScheduler 一行都不用改。
```

#### 三个能算出来的数字

读者可以自己验算，这是判断"输出是否真实"的最快方法：

| 输出里的数字 | 从哪来 | 验算 |
|---|---|---|
| `剩余磁盘 1600MB` | `BackupService(2000)` 减去 400MB | 2000 − 400 = 1600 ✅ |
| `每日销售报表_1520行.csv` | `generate()` 里 `name + "_" + rows + "行.csv"` | 1520 来自构造参数 ✅ |
| `（标题「备份完成」）` | `SendEmailCommand` 的第 3 个参数 | 见 `new SendEmailCommand(..., "备份完成", ...)` ✅ |

**注意 `└` 这个符号**：所有 Receiver 的输出都以它开头（比如 `└ 备份服务：…`），
而调度器的输出以 `[N]` 或 `[调度器]` 开头。
这是刻意的：**读者一眼就能分清"这行是调度器打的，还是业务对象打的"**。

在日志量大的真实系统里，这种"层次前缀"比任何彩色高亮都有用——
**它让调用链在纯文本里也能被看出来。**

---

### 6.2 demo2：失败与重试，失败的任务去哪了

#### 初始状态

```java
EmailService email = new EmailService();
BackupService backup = new BackupService(500);   // ← 只有 500MB
ReportService report = new ReportService();

scheduler.submit(new BackupDatabaseCommand(backup, "orders_db", 400));   // 能放下
scheduler.submit(new BackupDatabaseCommand(backup, "logs_db", 600));     // 放不下
scheduler.submit(new GenerateReportCommand(report, "每日销售报表", 800));
scheduler.submit(new SendEmailCommand(email, "ops@shop.com", "定时任务", "今日任务已跑"));
```

第 2 条命令**在构造时就注定了要失败**（600 > 500），
但构造期不校验——因为磁盘状态可能在入队之后、执行之前被清理。
**校验必须发生在执行的瞬间**，这是 `BackupService.backup()` 而不是构造函数抛异常的原因。

#### 状态迁移表

| 时刻 | `freeDiskMb` | `failed` | 说明 |
|---|---|---|---|
| 初始 | 500 | `[]` | —— |
| `[1]` 备份 400MB 成功 | 100 | `[]` | 500 − 400 |
| `[2]` 备份 600MB 失败 | **100**（不变） | `[c2]` | 校验在扣减之前，所以磁盘没被改 |
| `[3]` 报表成功 | 100 | `[c2]` | —— |
| `[4]` 邮件成功 | 100 | `[c2]` | —— |
| `freeDisk(600)` | **700** | `[c2]` | 运维介入 |
| `retryFailed()` → `[5]` | **100** | `[]` | 700 − 600 |

**第三行那个"不变"是本项目最重要的一个细节**：

```java
if (sizeMb > freeDiskMb) {
    throw new ServiceException("磁盘空间不足：需要 " + sizeMb + "MB，可用 " + freeDiskMb + "MB");
}
freeDiskMb -= sizeMb;        // ← 校验通过才扣减
```

**失败的操作不改变状态。** 如果写成先扣减再校验（或者扣减在 `try` 里而异常被吞），
那"失败"就会留下副作用——**那就不叫失败了**。

这是"业务失败必须干净"的具体表现：**一个失败的操作，对外应该等价于从未发生。**

#### 真实输出（56 行）

```

======================================================================
2. 失败隔离与重试：失败的任务不会丢失
======================================================================
  初始可用磁盘：500MB

  [调度器] 入队：备份数据库 orders_db（400MB）（待执行 1 条）
  [调度器] 入队：备份数据库 logs_db（600MB）（待执行 2 条）
  [调度器] 入队：生成报表「每日销售报表」（待执行 3 条）
  [调度器] 入队：发送邮件 → ops@shop.com（待执行 4 条）

  [1] 备份数据库 orders_db（400MB）
        └ 备份服务：生成 orders_db_backup_1.bak（400MB），剩余磁盘 100MB
        √ 成功

  [2] 备份数据库 logs_db（600MB）
        × 失败：磁盘空间不足：需要 600MB，可用 100MB

  [3] 生成报表「每日销售报表」
        └ 报表服务：生成 每日销售报表_800行.csv（800 行）
        √ 成功

  [4] 发送邮件 → ops@shop.com
        └ 邮件服务：已投递 → ops@shop.com（标题「定时任务」）
        √ 成功

  [调度器] 本轮结束：共 4 条，成功 3 条，失败 1 条，累计待重试 1 条

  >>> 关键点 1：第 2 条失败了，第 3、4 条照常执行。
      批量任务要的是「跑完所有能跑的」，而不是「第一个失败就全停」。

  >>> 关键点 2：失败原因被完整打印。
      原项目是 catch (Exception e) 之后只打印一句「任务执行失败」，e 从头到尾没用过。

  失败待重试：[备份数据库 logs_db（600MB）]
  >>> 关键点 3：它还在！原项目用 poll() 取任务，失败的这条已经被从队列里拿走，
      方法返回之后程序里再也找不到这个对象，所谓「记录日志以供重试」根本无法实现。

  —— 运维清理磁盘后重试 ——
        └ 运维：清理磁盘，释放 600MB，当前可用 700MB
  [调度器] 重试 1 条失败任务

  [5] 备份数据库 logs_db（600MB）
        └ 备份服务：生成 logs_db_backup_2.bak（600MB），剩余磁盘 100MB
        √ 成功

  [调度器] 本轮结束：共 1 条，成功 1 条，失败 0 条，累计待重试 0 条

  ┌─ 执行历史 ────────────────────────────────
  │ 1. 备份数据库 orders_db（400MB） → √ 成功
  │ 2. 备份数据库 logs_db（600MB） → × 磁盘空间不足：需要 600MB，可用 100MB
  │ 3. 生成报表「每日销售报表」 → √ 成功
  │ 4. 发送邮件 → ops@shop.com → √ 成功
  │ 5. 备份数据库 logs_db（600MB） → √ 成功
  └───────────────────────────────────────────
  >>> 同一条命令的两次执行都留在历史里：先失败、后成功。
      这说明「重试」不是把失败抹掉，而是追加一次新的尝试 —— 审计要的就是这个。
```

#### 从输出反推代码：三个细节

**① `[2]` 的失败信息里没有重复的命令名**

```
  [2] 备份数据库 logs_db（600MB）
        × 失败：磁盘空间不足：需要 600MB，可用 100MB
```

这正是 §4.5 里 `CommandException` 双参数设计的效果——
调度器打印的是 `e.getDetail()`，而命令名已经在上一行 `[2]` 里了。

**② `[5]` 是"第 5 次执行"，不是"第 5 条命令"**

`sequence` 是全局递增的。`retryFailed()` 内部的 `run()` 复用了同一个调度器实例，
所以序号接着往上涨。**这让"同一条命令的两次尝试"在时间线上是连续的两行**：

```
  │ 2. 备份数据库 logs_db（600MB） → × 磁盘空间不足：需要 600MB，可用 100MB
  │ 5. 备份数据库 logs_db（600MB） → √ 成功
```

**③ 历史表里第 2 条和第 5 条都在**

这就是"追加而不覆盖"的价值。如果重试成功之后把第 2 条删掉，
你看到的历史会变成"一次成功"，**下次再出问题时就无从判断"它是不是又反复失败过"**。

#### 一个非常关键的隐含条件：重试是安全的

`retryFailed()` 重试的是**同一个命令对象** `c2`。为什么它不会出问题？

```java
// BackupDatabaseCommand 的现场字段
private Backup created;      // 失败时仍是 null

@Override
public void execute() throws CommandException {
    try {
        created = backupService.backup(database, sizeMb);
    } catch (ServiceException e) {
        // 注意这里没有给 created 赋值 —— 失败时它保持 null
        throw new CommandException(getName(), e.getMessage(), e);
    }
}
```

**失败时 `created` 是 `null`，所以重试不会覆盖任何已记录的现场。**
这就是 `retryFailed()` 的 Javadoc 里那句"为什么重试是安全的"的代码依据。

反过来说，**已经成功的命令绝不能重跑**：

> 那会把它的现场字段改成第二次执行的结果，让之前那份撤销记录失效
> （比如把备份 id 换成新的，再去撤销就删错文件了）。

**"为什么可以重试"和"为什么不能重跑"是同一个问题的两面**，都靠现场字段的语义来保证。

---

### 6.3 demo3：程序 bug 是怎么穿透出去的

#### 调用链

```
Client.demo3()                                    第 133 行 try { scheduler.run(); }
   └─ TaskScheduler.run()
        ├─ [1] SendEmailCommand.execute()
        │      └─ EmailService.send("bad-address", ...)
        │             └─ throw new ServiceException("收件人地址非法：bad-address")
        │      └─ catch (ServiceException e) → throw new CommandException(getName(), ...)
        │   └─ catch (CommandException e)      ← 被接住了
        │         history.add(失败记录); failed.add(cmd); println("× 失败：…")
        │
        └─ [2] buggyCommand.execute()
               └─ cacheDir.length()   ← cacheDir 是 null
                      └─ 抛 NullPointerException
               （TaskScheduler 里没有对应的 catch，所以穿透）
        └─ run() 抛出，栈帧弹出
   └─ Client.demo3() 的 catch (RuntimeException e) 接住
```

**两个异常走了完全不同的路径**，这就是整个 demo 的全部内容：

| | 第 1 条 | 第 2 条 |
|---|---|---|
| 异常类型 | `CommandException`（受检） | `NullPointerException`（未受检） |
| 谁接住 | `TaskScheduler.run()` 里的 `catch` | `Client.demo3()` 里的 `catch (RuntimeException)` |
| 进 `history` 吗 | ✅ 记一条失败 | ❌ 完全没记录 |
| 进 `failed` 吗 | ✅ 可以重试 | ❌ 没有"重试"这回事 |
| 后续命令还跑吗 | ✅ 继续 | ❌ **循环被中断** |

最后一行是有意的：

```java
System.out.println("  注意：第 2 条抛出后，run() 的循环被中断了 —— 这是有意的。");
System.out.println("  bug 面前继续跑下去，只会制造更多脏数据。");
```

#### 真实输出（30 行）

```

======================================================================
3. 异常边界：程序 bug 绝不能被伪装成「任务失败」
======================================================================
  [调度器] 入队：发送邮件 → bad-address（待执行 1 条）
  [调度器] 入队：缓存预热（代码有 bug）（待执行 2 条）

  第 1 条是业务失败（地址不合法），第 2 条是代码 bug（空指针）。


  [1] 发送邮件 → bad-address
        × 失败：收件人地址非法：bad-address

  [2] 缓存预热（代码有 bug）

  >>> run() 没有吞掉它，直接抛出来了：
      NullPointerException：Cannot invoke "String.length()" because "cacheDir" is null

      这正是想要的结果：
        · 业务失败 → CommandException → 记入历史、可以重试；
        · 程序 bug  → RuntimeException → 当场炸掉，逼开发者去修。
      如果像原项目那样 catch (Exception e) 一把抓，
      这个 NPE 会被永远埋在「任务执行失败」这五个字下面。

  注意：第 2 条抛出后，run() 的循环被中断了 —— 这是有意的。
  bug 面前继续跑下去，只会制造更多脏数据。

  ┌─ 执行历史 ────────────────────────────────
  │ 1. 发送邮件 → bad-address → × 收件人地址非法：bad-address
  └───────────────────────────────────────────
  >>> 历史里只有第 1 条。第 2 条没被记录为「失败」，因为它根本不是「业务失败」。
```

#### 那一行 `because "cacheDir" is null` 是怎么来的

这是 **JDK 14+ 的 Helpful NullPointerException**，但它有一个前提：**类文件里必须有局部变量表**。

| 编译命令 | NPE 消息 | 教学价值 |
|---|---|---|
| `javac -encoding UTF-8`（默认 `-g:source,lines`） | `Cannot invoke "String.length()" because "<local1>" is null` | ❌ 读者不知道 `<local1>` 是什么 |
| `javac -encoding UTF-8 -g`（本项目） | `Cannot invoke "String.length()" because "cacheDir" is null` | ✅ 变量名直接指出 |

所以本项目的编译命令里**一定要带 `-g`**：

```
javac --release 25 -encoding UTF-8 -g -Xlint:all -d out ...
```

Maven / Gradle 默认就开 `-g`，所以这个坑只在"手敲 `javac`"时才会遇到。
**一旦遇到过，就会明白为什么"更详细的异常信息"值一个编译参数。**

#### `[2]` 后面那个空行不是排版失误

```
  [2] 缓存预热（代码有 bug）

  >>> run() 没有吞掉它，直接抛出来了：
```

`run()` 在打印 `[2] 名称` 之后、调用 `execute()` 之前没有任何输出，
而 `execute()` 里第一句就是 `cacheDir.length()`——
**它在打印任何东西之前就炸了**。所以 `[2]` 之后直接是异常。

这个空行恰好证明了"失败发生在动作的最开始"。
如果 `buggyCommand` 里先打印一句"开始预热缓存"再 NPE，输出就会多一行——
**读者可以通过这个细节判断失败发生的精确位置**，这正是教学 demo 该有的信息密度。

---

### 6.4 demo4：撤销与重做，双栈是怎么动的

#### 初始状态

```java
ConfigService config = new ConfigService(
        "payment.timeout", "10s",
        "retry.max", "3");
CommandHistory history = new CommandHistory();

System.out.println("  初始配置：" + config.snapshot());
// 初始配置：{payment.timeout=10s, retry.max=3}
```

**这里用的是 `ConfigService(String... keyValues)` 而非 `Map.of(...)`**，
原因在 §5.3 讲过：`Map.of` 的迭代顺序不确定，输出会不可复现。

`CommandHistory` 的两个栈都是空的：

```java
private final Deque<UndoableCommand> undoStack = new ArrayDeque<>();
private final Deque<UndoableCommand> redoStack = new ArrayDeque<>();
```

#### 完整状态迁移表（可逐行对照输出）

| # | 操作 | 作用在哪个命令对象 | `undoStack`（顶→底） | `redoStack`（顶→底） | 配置快照 |
|---|---|---|---|---|---|
| 0 | 初始 | —— | `[]` | `[]` | `{payment.timeout=10s, retry.max=3}` |
| 1 | `execute(t30)` | `payment.timeout=30s` | `[t30]` | `[]` | `{payment.timeout=30s, retry.max=3}` |
| 2 | `execute(f)` | `feature.newCheckout=on` | `[f, t30]` | `[]` | `{…, feature.newCheckout=on}` |
| 3 | `undo()` | `f` | `[t30]` | `[f]` | `{payment.timeout=30s, retry.max=3}` |
| 4 | `undo()` | `t30` | `[]` | `[t30, f]` | `{payment.timeout=10s, retry.max=3}` |
| 5 | `redo()` | `t30`（从 redo 弹出） | `[t30]` | `[f]` | `{payment.timeout=30s, retry.max=3}` |
| 6 | `execute(r5)` | `retry.max=5` | `[r5, t30]` | **`[]`**（被清空） | `{payment.timeout=30s, retry.max=5}` |
| 7 | `redo()` | —— | `[r5, t30]` | `[]` | 无变化（没有可重做的） |
| 8 | `undo()` | `r5` | `[t30]` | `[r5]` | `{payment.timeout=30s, retry.max=3}` |
| 9 | `undo()` | `t30` | `[]` | `[r5, t30]` | `{payment.timeout=10s, retry.max=3}` |
| 10 | `undo()` ×2 | —— | `[]` | `[r5, t30]` | 无变化（栈已空） |

**第 6 行是这张表的重点**：`redoStack` 从 `[f]` 变成 `[]`。
`f`（`feature.newCheckout=on`）从此既不在 `undoStack` 也不在 `redoStack`——
**它彻底消失了**，这就是输出的那句：

```
  >>> 撤销过、还没重做的那一步，就永远回不来了。
```

#### 真实输出（60 行）

```

======================================================================
4. 撤销与重做：命令对象记住了「执行前的现场」
======================================================================
  初始配置：{payment.timeout=10s, retry.max=3}

  —— 执行两条命令 ——
  [历史] 执行：修改配置 payment.timeout = 30s
        └ 配置服务：payment.timeout 10s → 30s
  [历史] 已记入撤销栈（可撤销 1 步，可重做 0 步）
  [历史] 执行：修改配置 feature.newCheckout = on
        └ 配置服务：feature.newCheckout = on（新增）
  [历史] 已记入撤销栈（可撤销 2 步，可重做 0 步）

  当前配置：{payment.timeout=30s, retry.max=3, feature.newCheckout=on}

  —— 撤销两次 ——
  [历史] 撤销：修改配置 feature.newCheckout = on
        └ 配置服务：移除 feature.newCheckout（原值 on）
  [历史] 撤销完成（可撤销 1 步，可重做 1 步）
  [历史] 撤销：修改配置 payment.timeout = 30s
        └ 配置服务：payment.timeout 30s → 10s
  [历史] 撤销完成（可撤销 0 步，可重做 2 步）

  撤销后：{payment.timeout=10s, retry.max=3}
  >>> 注意 feature.newCheckout 是【消失】，不是变成空串。
      它原本就不存在，正确的补偿是 remove() 而不是 set("")。
      「不存在」和「值为空」是两件事，混为一谈会给下游留下一个假配置。

  —— 重做一次 ——
  [历史] 重做：修改配置 payment.timeout = 30s
        └ 配置服务：payment.timeout 10s → 30s
  [历史] 重做完成（可撤销 1 步，可重做 1 步）
  重做后：{payment.timeout=30s, retry.max=3}
  [历史] 撤销栈（栈顶→底）：[修改配置 payment.timeout = 30s]
  [历史] 重做栈（栈顶→底）：[修改配置 feature.newCheckout = on]

  —— 执行一条新命令：重做栈必须被清空 ——
  [历史] 执行：修改配置 retry.max = 5
        └ 配置服务：retry.max 3 → 5
  [历史] 已记入撤销栈（可撤销 2 步，可重做 0 步）
  [历史] 撤销栈（栈顶→底）：[修改配置 retry.max = 5, 修改配置 payment.timeout = 30s]
  [历史] 重做栈（栈顶→底）：[]

  [历史] 没有可重做的操作
  >>> 撤销过、还没重做的那一步，就永远回不来了。
      这和 Git 在历史中间提交、后面的 commit 离开当前分支是同一个道理。

  —— 一直撤销到栈空 ——
  [历史] 撤销：修改配置 retry.max = 5
        └ 配置服务：retry.max 5 → 3
  [历史] 撤销完成（可撤销 1 步，可重做 1 步）
  [历史] 撤销：修改配置 payment.timeout = 30s
        └ 配置服务：payment.timeout 30s → 10s
  [历史] 撤销完成（可撤销 0 步，可重做 2 步）
  [历史] 没有可撤销的操作
  [历史] 没有可撤销的操作

  —— 撤销一条从未执行过的命令 ——
  >>> [OK] 被拦住了：修改配置 unknown.key = x 尚未执行，不能撤销
      没成功执行过的事，谈不上撤销；这种「用法错误」应该是异常，而不是静默无事发生。
```

#### 三个必须对着输出看的细节

**① 撤销 `feature.newCheckout` 打的是「移除」，不是「设为空」**

```
  [历史] 撤销：修改配置 feature.newCheckout = on
        └ 配置服务：移除 feature.newCheckout（原值 on）
```

`previousValue == null` → 走 `remove(key)` 分支。
如果错写成 `set(key, "")`，这一行会变成 `feature.newCheckout on → `（改成空串），
而"撤销后"的配置快照里会多出一个空值键：

```
{payment.timeout=10s, retry.max=3, feature.newCheckout=}    ← 多了一个假配置
```

**「消失」和「值为空」在输出里长得完全不一样**，这就是用快照演示撤销的价值。

**② 重做之后，两个栈各有一步**

```
  [历史] 撤销栈（栈顶→底）：[修改配置 payment.timeout = 30s]
  [历史] 重做栈（栈顶→底）：[修改配置 feature.newCheckout = on]
```

一个成功的 redo **同时**移动两个栈：从 `redo` 弹出、压入 `undo`。
如果只做前半步（弹出但不压入），那条命令就凭空消失了——
这是手写 undo/redo 时最常见的 bug。

**③ 最后那个 `[OK] 被拦住了` 是"用法错误"的处理示范**

```java
UndoableCommand neverExecuted = new UpdateConfigCommand(config, "unknown.key", "x");
try {
    neverExecuted.undo();
    System.out.println("  >>> 没有抛异常（不应该发生）");
} catch (IllegalStateException e) {
    System.out.println("  >>> [OK] 被拦住了：" + e.getMessage());
```

**注意这个 `try-catch` 是写在 Client 里的，不是写在命令里。**
命令在"不该被撤销"时**必须抛异常**，而"要不要捕获"由调用方决定。
如果命令内部静默 `return`，这个"用法错误"就永远发现不了。

> **"没成功执行过的事，谈不上撤销"——这种用法错误应该是异常，而不是静默无事发生。**

---

### 6.5 demo5：宏命令的补偿路径

#### 5.1 失败场景的状态变化

```java
ConfigService config = new ConfigService("release.channel", "stable");
BackupService backup = new BackupService(2000);
ReportService report = new ReportService();
EmailService email = new EmailService();
TaskScheduler scheduler = new TaskScheduler();

Command releaseFailed = new MacroCommand("发布 v2.3.1",
        new UpdateConfigCommand(config, "release.version", "v2.3.1"),
        new BackupDatabaseCommand(backup, "orders_db", 400),
        new GenerateReportCommand(report, "发布核对报表", 0),        // ← 0 行，会失败
        new SendEmailCommand(email, "team@shop.com", "发布完成", "v2.3.1 已上线"));
```

**注意第 3 步的 `0`**：它是硬编码的数据行数，用来模拟"上游没导数据"。
这是唯一一处"人为安排失败"的地方——但它是**参数级的安排**，不是逻辑上的假分支：
`ReportService.generate()` 的 `rows <= 0` 校验是真实的业务规则，任何调用方都会遇到。

| 时刻 | `config` | 备份数 | 报表数 | 邮件数 | `freeDiskMb` | `done` |
|---|---|---|---|---|---|---|
| 初始 | `{release.channel=stable}` | 0 | 0 | 0 | 2000 | `[]` |
| 步骤 1 成功 | `+ release.version=v2.3.1` | 0 | 0 | 0 | 2000 | `[c1]` |
| 步骤 2 成功 | 同上 | 1 | 0 | 0 | **1600** | `[c1, c2]` |
| 步骤 3 失败 | 不变 | 1 | 0 | 0 | 1600 | `[c1, c2]` |
| 补偿 `c2` | 不变 | 0 | 0 | 0 | **2000** | —— |
| 补偿 `c1` | `{release.channel=stable}` | 0 | 0 | 0 | 2000 | —— |

**最后两行是逆序的**：先补 `c2`（备份），再补 `c1`（配置）。
如果顺序反了——先删配置再删备份——在"配置已删、备份还在"的那个窗口里，
系统就处于一个**语义上不成立的状态**（有一个没人知道来历的备份）。

#### 5.2 成功场景

```java
Command releaseOk = new MacroCommand("发布 v2.3.1",
        new UpdateConfigCommand(config, "release.version", "v2.3.1"),
        new BackupDatabaseCommand(backup, "orders_db", 400),
        new GenerateReportCommand(report, "发布核对报表", 1520),   // ← 数据到位
        new SendEmailCommand(email, "team@shop.com", "发布完成", "v2.3.1 已上线"));
```

| 时刻 | `config` | 备份数 | 报表数 | 邮件数 | `freeDiskMb` |
|---|---|---|---|---|---|
| 步骤 1 | `+ release.version=v2.3.1` | 0 | 0 | 0 | 2000 |
| 步骤 2 | 同上 | 1 | 0 | 0 | 1600 |
| 步骤 3 | 同上 | 1 | 1 | 0 | 1600 |
| 步骤 4 | 同上 | 1 | 1 | **1** | 1600 |

**注意 5.1 和 5.2 用的是同一个 `backup` 实例**，所以 5.2 生成的备份文件名是
`orders_db_backup_2.bak`（`nextId` 已经涨到 2）——这个小细节也体现在输出里：

```
      步骤 2/4：备份数据库 orders_db（400MB）
        └ 备份服务：生成 orders_db_backup_2.bak（400MB），剩余磁盘 1600MB
```

#### 真实输出（68 行）

```

======================================================================
5. 宏命令：打包多条命令，失败就逆序补偿
======================================================================
  发布流程 4 步：改配置 → 备份数据库 → 生成报表 → 发通知邮件
  最后一步【不可撤销】（邮件发出去收不回来），所以它必须排在最后。

  【场景 5.1】上游数据还没到，报表 0 行 → 第 3 步失败
  [调度器] 入队：发布 v2.3.1（4 步）（待执行 1 条）

  [1] 发布 v2.3.1（4 步）
      步骤 1/4：修改配置 release.version = v2.3.1
        └ 配置服务：release.version = v2.3.1（新增）
      步骤 2/4：备份数据库 orders_db（400MB）
        └ 备份服务：生成 orders_db_backup_1.bak（400MB），剩余磁盘 1600MB
      步骤 3/4：生成报表「发布核对报表」
      ! 第 3 步失败：数据源 0 行，报表「发布核对报表」未生成
      ← 补偿：备份数据库 orders_db（400MB）
        └ 备份服务：删除 orders_db_backup_1.bak，回收 400MB，剩余磁盘 2000MB
      ← 补偿：修改配置 release.version = v2.3.1
        └ 配置服务：移除 release.version（原值 v2.3.1）
        × 失败：第 3/4 步（生成报表「发布核对报表」）失败，已补偿 2 步：数据源 0 行，报表「发布核对报表」未生成

  [调度器] 本轮结束：共 1 条，成功 0 条，失败 1 条，累计待重试 1 条

  回滚后的系统状态：
      release.version = null   （原本不存在）
      备份文件        = 0 份
      报表文件        = 0 份
      已发邮件        = 0 封
      可用磁盘        = 2000MB（已还原）
  >>> 前三项都回到「没有」的状态，磁盘也一分不少 ——
      前两步的副作用被完整撤销了，而邮件压根还没发。
      为什么能这么干净？因为不可逆的那一步排在最后。

  【场景 5.2】数据到位后重跑，4 步全部成功
  [调度器] 入队：发布 v2.3.1（4 步）（待执行 1 条）

  [2] 发布 v2.3.1（4 步）
      步骤 1/4：修改配置 release.version = v2.3.1
        └ 配置服务：release.version = v2.3.1（新增）
      步骤 2/4：备份数据库 orders_db（400MB）
        └ 备份服务：生成 orders_db_backup_2.bak（400MB），剩余磁盘 1600MB
      步骤 3/4：生成报表「发布核对报表」
        └ 报表服务：生成 发布核对报表_1520行.csv（1520 行）
      步骤 4/4：发送邮件 → team@shop.com
        └ 邮件服务：已投递 → team@shop.com（标题「发布完成」）
        √ 成功

  [调度器] 本轮结束：共 1 条，成功 1 条，失败 0 条，累计待重试 1 条

  成功后的系统状态：
      release.version = v2.3.1
      备份文件        = 1 份
      报表文件        = 1 份
      已发邮件        = 1 封
      可用磁盘        = 1600MB

  注：5.1 失败的那条宏命令仍在待重试列表里（1 条）。
      它和 5.2 是两次独立的提交 —— 调度器不会自作主张地认为
      「同类操作成功了就算它过了」；要不要放弃失败任务，应由人或明确的策略决定。

  ┌─ 执行历史 ────────────────────────────────
  │ 1. 发布 v2.3.1（4 步） → × 第 3/4 步（生成报表「发布核对报表」）失败，已补偿 2 步：数据源 0 行，报表「发布核对报表」未生成
  │ 2. 发布 v2.3.1（4 步） → √ 成功
  └───────────────────────────────────────────
  >>> 两条宏命令各是一条执行记录 —— 调度器不知道里面装了 4 个步骤。
      这就是「组合模式 + 命令模式」的效果：一组命令和一条命令长得一样。
```

#### 从输出里读出的四个设计决定

| 输出中的证据 | 对应的设计决定 | 在哪一节 |
|---|---|---|
| `! 第 3 步失败：…` 出现在两行 `← 补偿` **之前** | 失败原因先打印，再补偿 | §4.10 |
| `← 补偿：备份…` 在 `← 补偿：修改配置…` **之前** | 逆序补偿（`ListIterator` 反向遍历） | §4.10 |
| 只有 2 步补偿，第 3 步（报表）没有被补偿 | 只补偿 `done` 列表里的步骤 | §4.10 |
| `已补偿 2 步：` 后面跟着原始失败原因 | 异常信息携带"补偿进度 + 根因" | §4.10 |

#### 那个"累计待重试 1 条"是个陷阱题

5.2 明明成功了（`成功 1 条，失败 0 条`），为什么后面还写 `累计待重试 1 条`？

**因为 5.1 失败的那条宏命令还在 `failed` 列表里，没人清空它。**

这不是 bug，而是刻意保留的行为：

> `它和 5.2 是两次独立的提交 —— 调度器不会自作主张地认为
> 「同类操作成功了就算它过了」；要不要放弃失败任务，应由人或明确的策略决定。`

一个调度器如果自作聪明地"看到同类操作成功了就清掉失败记录"，
那它就是在**替人做业务判断**。而"这条失败了要不要放弃"是业务问题，不是技术问题。

**但这里确实暴露了一个真实的设计缺口**：本项目只有 `retryFailed()`，**没有 `discardFailed()`**。
真实系统里一定有"这条不再重试了"的动作（人工确认放弃、超过重试上限、业务已下线）。

| 缺失的能力 | 真实系统里怎么补 | 本项目为什么不做 |
|---|---|---|
| `discardFailed(Command)` | 从 `failed` 里移除，记一条"人工放弃"的审计 | demo 里没有这个业务场景，加了就是凭空发明需求 |
| 重试次数上限 | `Map<Command, Integer>` 或 `ExecutionRecord` 里加 `attempt` | 同上 |
| 重试退避（backoff） | 交给外层定时器，不在 Invoker 里 `sleep` | 在 `Invoker` 里 `Thread.sleep` 会让输出不可复现 |

**"知道缺什么、为什么现在不缺"——这也是设计的一部分。**
在附录 E 的"尚未验证/尚未实现"清单里，这些都被明确列了出来，而不是假装不存在。

---

## 7. 扩展对比：新需求来了改哪里

这一章是检验"到底有没有解耦"的唯一标准。**所有结论以"要改几个文件"来度量。**

### 7.1 新增一种命令：刷新 CDN 缓存

需求：「发布之后要刷一下 CDN 缓存。」

按 §4.2 的顺序，**先写 Receiver**：

```java
public class CdnService {

    private final List<String> purged = new ArrayList<>();

    public void purge(String path) throws ServiceException {
        if (path == null || path.isBlank()) {
            throw new ServiceException("刷新路径为空，CDN 未刷新");
        }
        purged.add(path);
        System.out.println("        └ CDN 服务：已刷新 " + path);
    }

    public List<String> getPurged() {
        return List.copyOf(purged);
    }
}
```

**再写命令**：

```java
public class PurgeCdnCommand implements Command {

    private final CdnService cdnService;
    private final String path;

    public PurgeCdnCommand(CdnService cdnService, String path) {
        this.cdnService = Objects.requireNonNull(cdnService, "cdnService 不能为 null");
        this.path = path;
    }

    @Override
    public String getName() {
        return "刷新 CDN：" + path;
    }

    @Override
    public void execute() throws CommandException {
        try {
            cdnService.purge(path);
        } catch (ServiceException e) {
            throw new CommandException(getName(), e.getMessage(), e);
        }
    }
}
```

**然后……就没有然后了。**

| 文件 | 需要改吗 | 说明 |
|---|---|---|
| `CdnService` | 🆕 新增 | Receiver |
| `PurgeCdnCommand` | 🆕 新增 | 具体命令 |
| `Command` / `UndoableCommand` | ❌ 不改 | 接口已经够用 |
| `CommandException` / `ServiceException` | ❌ 不改 | 异常体系已经够用 |
| **`TaskScheduler`** | ❌ **不改** | 它只认识 `Command` 接口 |
| `CommandHistory` | ❌ 不改 | 不支持撤销，所以进不了撤销栈（编译器保证） |
| `Client` | ✅ 加几行 | 演示需要 |
| `MacroCommand` | ❌ 不改 | 除非要把它加进发布流程（那是"改参数"，不是"改类"） |

**核心基础设施 0 改动。** 这就是 §3.2 收益 1 的实际度量。

对比原项目做同一件事：

```java
public class PurgeCdnTask implements Task {
    @Override
    public void execute() {
        System.out.println("刷新 CDN...");
        // CDN 刷新的业务逻辑又长在这里了
    }
}
```

`TaskScheduler` 确实也不用改——**但 `CdnService` 那份业务能力从此只以"任务"的形式存在**，
想在 HTTP 接口里手动刷一次 CDN，只能 `new PurgeCdnTask().execute()`。

> **两个都能做到"调度器不改"，区别在"业务能力有没有被保存下来"。**

### 7.2 换通知渠道：邮件改成短信

需求：「发布通知改成发短信，同时保留发邮件的命令。」

| 方案 | 改哪些文件 | 评价 |
|---|---|---|
| **A：改 `EmailService.send()` 内部实现** | 1 个（Receiver） | ❌ 会把"发邮件"变成"发短信"，语义错乱 |
| **B：新增 `SmsService` + `SendSmsCommand`** | 新增 2 个，改 `Client` 或 `MacroCommand` 的构造参数 | ✅ 推荐 |

**方案 B 的关键在于"改的是调用点的组装，不是任何类的内部"**：

```java
// 改之前
new SendEmailCommand(email, "team@shop.com", "发布完成", "v2.3.1 已上线")

// 改之后
new SendSmsCommand(sms, "138****0000", "v2.3.1 已上线")
```

如果这条命令在 `MacroCommand` 里，改的就是那一行的构造参数：

```java
Command release = new MacroCommand("发布 v2.3.1",
        new UpdateConfigCommand(config, "release.version", "v2.3.1"),
        new BackupDatabaseCommand(backup, "orders_db", 400),
        new GenerateReportCommand(report, "发布核对报表", 1520),
        new SendSmsCommand(sms, "138****0000", "v2.3.1 已上线"));    // ← 只有这一行变
```

**`MacroCommand` 本身、`TaskScheduler`、`CommandHistory`、两个异常类全部 0 改动。**

这正是"面向接口"在**组装层**（Client / 工厂）兑现价值的地方：
**变化被挡在组装层，扩散不到基础设施。**

### 7.3 给备份加"风控检查"

需求：「备份之前先检查这个库是否在白名单里，不在就拒绝。」

三种做法，差别在"改哪一层"：

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A：写进 `BackupService`** | 在 `backup()` 开头加白名单判断 | 所有调用方自动受保护 | 备份和风控两个关注点混在一起 |
| **B：装饰器包一层** | `RiskCheckedBackupService implements ?`——但 `BackupService` 不是接口，包不了 | —— | 需要先抽接口 |
| **C：加一个命令做前置检查** | `MacroCommand(风控检查, 备份)` | 组合灵活、可撤销、有审计 | 多一层对象 |

本项目的结构支持 **A 和 C**，B 需要额外抽接口。

**如果选 C**，代码是：

```java
Command guarded = new MacroCommand("风控后备份",
        new CheckWhitelistCommand(riskService, "orders_db"),   // 不通过就抛 CommandException
        new BackupDatabaseCommand(backup, "orders_db", 400));
```

`MacroCommand` 的行为会自动变得正确：**第 1 步失败 → 没有 `done` → 不补偿任何东西**。
**新需求落在组装层，机制代码 0 改动。**

> **这就是"模式"和"框架"的区别**：模式给你的是可组合的积木，
> 而不是一个必须改配置才能扩展的黑箱。

### 7.4 加一个"预演模式"的 Invoker

需求：「发布前先预演一遍：把每一步都打出来，但什么都不改。」

**不修改任何现有类**，加一个 `Command` 的实现就行：

```java
/** 预演：只打印，不执行。装饰器思路。 */
public class DryRunCommand implements Command {

    private final Command target;

    public DryRunCommand(Command target) {
        this.target = Objects.requireNonNull(target, "target 不能为 null");
    }

    @Override
    public String getName() {
        return "[预演] " + target.getName();
    }

    @Override
    public void execute() {
        System.out.println("        ~ 预演（不执行）：" + target.getName());
        // 故意不调用 target.execute()
    }
}
```

用法：

```java
scheduler.submit(new DryRunCommand(new BackupDatabaseCommand(backup, "orders_db", 400)));
```

输出：

```
  [1] [预演] 备份数据库 orders_db（400MB）
        ~ 预演（不执行）：备份数据库 orders_db（400MB）
        √ 成功
```

**注意这里没有 `└ 备份服务：生成 …bak` 那一行**——因为 `DryRunCommand` 压根没调 `target.execute()`。
磁盘没变、没生成任何备份文件，但调度器仍把它当成一条成功的命令记进历史。

这个例子说明了三件事：

| # | 结论 |
|---|---|
| 1 | 预演需求**不需要改任何现有类**，这就是"命令是对象"的红利 |
| 2 | 它是**装饰器模式**在命令维度上的应用（`DryRunCommand` 包住任意 `Command`） |
| 3 | 但 `getName()` 里加了 `[预演]` 前缀——**审计日志必须能区分"真做了"和"只是预演"** |

第 3 点是一个真实的坑：如果预演命令的 `getName()` 和真命令一样，
历史表里就会混进"看起来做了、实际没做"的记录。**日志的可信度比日志的完整度更重要。**

### 7.5 把命令落库，进程重启后重放

需求：「调度器重启后，队列里的命令不能丢。」

这需要三件事，本项目**一件都没做**，但结构上是支持的：

| 需要的能力 | 现状 | 要补什么 |
|---|---|---|
| 命令序列化 | ✅ 参数都是 `final` 字段、都是基本类型或 `String` | 给每个命令加 `toJson()` / `fromJson()`，或引入 Jackson |
| 类型注册表 | ❌ 没有 | `Map<String, Function<JsonNode, Command>>` |
| 反序列化后的 Receiver 注入 | ❌ 没有 | 需要一个"Receiver 容器"（真实项目里就是 Spring 的 `ApplicationContext`） |

第三行是真正的难点：**序列化只保存参数，不保存 Receiver 的引用**。
还原一条 `BackupDatabaseCommand` 时，你得先有一个 `BackupService` 实例交给它。

```java
// 伪代码：反序列化 + 注入 Receiver
String json = db.loadNextJob();
Command cmd = registry.get(json.type).apply(json.params, context);   // context 提供 Receiver
scheduler.submit(cmd);
```

**这也解释了为什么 `Receiver` 必须是独立对象**（§4.2）：
如果业务逻辑写在命令里，命令就变成"自带全部依赖"的巨型对象，
序列化要么存不下，要么存下了也没法在另一个进程里正确运行
（那个进程的数据库连接、磁盘状态都不一样）。

> **"能不能落库重放"这件事，最终取决于 Receiver 和 Command 有没有被分开。**

### 7.6 改成异步执行

需求：「`run()` 不要阻塞，丢进线程池。」

**`TaskScheduler` 内部改 3 行**（`ExecutorService` 替换 for 循环），
或者——更好的做法——**不加**，而是在外面包一层：

```java
// 想异步就换个调用方式，不动 Invoker
executor.submit(() -> scheduler.run());
```

**为什么"不改"更好？**

| 改了 | 不改 |
|---|---|
| `run()` 变成异步，返回的 `ok` 没意义了 | `run()` 保持同步，返回值可信 |
| `history` / `failed` 变成并发访问，要加锁 | 单线程，无需任何同步 |
| 测试要处理异步等待 | 测试确定、可复现 |

这就是"**能不改就不改**"的原则在起作用：**异步是一种调用方式，不是 Invoker 的职责。**
真实项目里通常会有一个专门的 `AsyncInvoker`，而不是把 `TaskScheduler` 改成两用。

### 7.7 扩展成本汇总

把 §7.1–§7.6 压成一张表，这是对"解耦"最硬的度量：

| 需求 | 新增文件 | 改现有文件 | 基础设施改动 |
|---|---|---|---|
| 7.1 新增 CDN 刷新命令 | 2 | 1（Client） | **0** |
| 7.2 邮件换短信 | 2 | 1（组装点） | **0** |
| 7.3 加风控检查 | 1（检查命令） | 1（组装点） | **0** |
| 7.4 预演模式 | 1（`DryRunCommand`） | 0 | **0** |
| 7.5 落库重放 | 序列化器 + 注册表 | 每个命令加一对方法 | **0** |
| 7.6 异步执行 | 0（外部包一层） | 0 | **0** |

**六种扩展，`TaskScheduler` / `Command` / `CommandException` 一次都没有被修改。**

对照 §3.1 那张"直接调用要改什么"的表：

| 需求 | 直接调用（`ReleaseService`） | 命令模式 |
|---|---|---|
| 审计日志 | 每个业务方法里加日志 | Invoker 统一记录，**0 改动** |
| 定时执行 | `release()` 不可序列化 | 命令可落库，**0 改动** |
| 撤销 | 另写一套对称的 `rollback()` | 复用 `undo()`，**0 改动**（只需实现接口） |
| 预演 | 加 `dryRun` 参数，参数爆炸 | 换一个 Invoker / 包一层，**0 改动** |
| 进度条 | 在每步之间插回调 | Invoker 前后发事件，**0 改动** |
| 组合 | 嵌套 try-catch | `MacroCommand`，**0 改动** |

**这就是整个模式存在的全部理由**——不是"看起来更面向对象"，
而是**新需求被挡在基础设施之外**。

---

## 8. 优缺点与常见误区

### 8.1 四条优点（都带本项目的证据）

#### 优点 1：请求成为一等公民

这是模式**唯一不可替代**的价值。一个方法调用只能"立刻发生"，栈一弹就没了；
一个命令对象可以：

| 能力 | 本项目证据 | 真实系统对应 |
|---|---|---|
| 排队 | `TaskScheduler.queue`（`ArrayDeque`） | 任务队列、消息队列 |
| 记录 | `ExecutionRecord` + `printHistory()` | 审计日志、操作流水 |
| 重试 | `failed` 列表 + `retryFailed()` | 死信队列、失败重跑 |
| 撤销 | `CommandHistory` 双栈 | 编辑器撤销、回滚 |
| 组合 | `MacroCommand` | Saga、工作流引擎 |
| 重放 | 参数全是 `final` 字段，理论可序列化（§7.5） | redo log、事件溯源 |

**注意最后一行是"理论可序列化"**——本项目没做（附录 E 有说明）。
但结构上成立：所有参数都是 `final` 的基本类型或 `String`，没有一个不可序列化的引用。

#### 优点 2：Invoker 与 Receiver 双向解耦

```
  TaskScheduler  ──不认识──▶  具体命令  ──▶  EmailService
  （只认 Command）                            BackupService
                                              ReportService
                                              ConfigService
```

这是**双向的**：

| 方向 | 谁知道谁 | 收益 |
|---|---|---|
| 向下 | Invoker 只认识 `Command`，不认识任何 Service | 新增命令，调度器 0 改动（§7.1） |
| 向上 | Receiver 谁都不认识，连 `Command` 都不知道 | HTTP 接口、定时器、单测都能直接用它（§4.2） |

**很多文章只讲第一个方向**，但第二个方向才是"业务能力可复用"的关键。

#### 优点 3：撤销与补偿有了统一的落点

`UndoableCommand` 让"能不能撤销"变成类型事实，`CommandHistory` 让"撤销的顺序"有了唯一归属，
`MacroCommand` 让"一组操作整体回滚"变成可能。

三者的分工是干净的：

| 类 | 负责什么 | 不负责什么 |
|---|---|---|
| `UndoableCommand` | 怎么撤销**自己** | 不知道栈、不知道有其他命令 |
| `CommandHistory` | 撤销**顺序**、不变量 | 不知道任何具体命令的撤销逻辑 |
| `MacroCommand` | 逆序补偿**一组**命令 | 不负责撤销自己（它不可撤销） |

**三个都不越界**，这是本项目最干净的一处设计。

#### 优点 4：每个命令都能独立测试

```java
// 测试 UpdateConfigCommand：不需要 TaskScheduler，不需要 CommandHistory
ConfigService config = new ConfigService("k", "old");
UpdateConfigCommand cmd = new UpdateConfigCommand(config, "k", "new");
cmd.execute();
assertEquals("new", config.get("k"));
cmd.undo();
assertEquals("old", config.get("k"));
```

对比原项目：要测"备份逻辑"必须先 `new BackupTask()`，
而 `BackupTask` 的输出是 `System.out.println`——**想断言就得去抓标准输出**。

**命令可以 `new` 出来、可以调、可以检查 Receiver 的状态**，这才是可测的。

---

### 8.2 六条缺点（诚实账）

#### 缺点 1：类数量膨胀

本项目的比例：

```
  4 个业务操作  →  1 个接口 + 1 个子接口 + 5 个命令类 + 2 个异常类
                =  9 个"模式相关"的文件
```

**一个操作对应一个类**，这是命令模式的固定成本。
如果系统里有 50 个业务操作，就会有 50 多个命令类。

缓解办法（真实项目常用）：

| 办法 | 代价 |
|---|---|
| 用 lambda 简化"不需要 undo / 不需要名字"的命令 | 失去 `getName()`，日志退化 |
| 用泛型基类 `AbstractCommand<R>` 收拢样板 | 增加继承层次，可能过度设计 |
| 只给"需要排队/撤销/审计"的操作建命令 | **最推荐**——回到 §3.3 的判断标准 |

#### 缺点 2：多一层间接

读代码要跳两次：

```
  Client → new BackupDatabaseCommand(...) → execute() → BackupService.backup(...)
```

想看"备份到底做了什么"，必须跳到第二个文件。
**这是任何"解耦"都要付的代价**——解耦的本质就是插入一层间接。

判断值不值的方法：**这一层间接换来的自由度，你用得上吗？**
用不上就是纯粹的负担（§3.3）。

#### 缺点 3：参数搬运样板

```java
public SendEmailCommand(EmailService emailService, String to, String subject, String body) {
    this.emailService = Objects.requireNonNull(emailService, "emailService 不能为 null");
    this.to = to;
    this.subject = subject;
    this.body = body;
}
```

四个参数抄四遍。如果 Receiver 的方法有 10 个参数，命令的构造器也要有 10 个（或者引入一个 `Params` 对象）。

**这是"请求即数据"必须付的税**：数据要被存下来，就得有地方存。
不过这笔税并不白交——**它换来的是"这个请求可以被序列化"**（§7.5）。

#### 缺点 4：异常翻译样板

每个命令的 `execute()` 都是同一个形状：

```java
try {
    receiver.doSomething(args);
} catch (ServiceException e) {
    throw new CommandException(getName(), e.getMessage(), e);
}
```

3-4 行，乘以命令数量。如果 Receiver 抛多种受检异常，`catch` 块会更长。

缓解办法：写一个函数式工具方法。

```java
/** 把业务异常翻译成命令异常，顺便统一 getName()。 */
static void translate(Command cmd, ServiceCall call) throws CommandException {
    try {
        call.run();
    } catch (ServiceException e) {
        throw new CommandException(cmd.getName(), e.getMessage(), e);
    }
}
```

**本项目没有做这个抽象**，理由是：它会让 `execute()` 变成一行 lambda，
**读者就看不清"翻译"这件事发生了**——而翻译恰恰是命令层的核心职责（§4.5）。
**教学代码优先暴露机制，工程代码优先消除样板。** 这个取舍必须是有意识的。

#### 缺点 5：有状态命令的复用与并发约束（最大的代价）

一旦实现 `UndoableCommand`，命令就**不能复用、不能跨线程共享**（§5.1）。
这会连锁影响：

| 场景 | 影响 |
|---|---|
| 对象池 / 缓存命令实例 | ❌ 不可行 |
| 多线程共享一条命令 | ❌ 字段互踩 |
| 命令注册为 Spring 单例 Bean | ❌ 绝对不能（这是真实项目里最常见的事故） |

**最后一行是真实事故高发区**：有人把 `@Component` 打在命令类上，
然后把它注入到两个 Service 里各执行一次——**撤销时删掉了别人的备份**。

> **无状态的对象可以随便复用；有状态的对象必须"一次一个"。**
> 命令模式把"请求"变成了有状态对象，就必须接受这条约束。

#### 缺点 6：调试栈更深、日志更要紧

原项目出问题时：

```
BackupTask.execute()   ← 一眼看到业务代码
```

本项目出问题时：

```
TaskScheduler.run()
  → BackupDatabaseCommand.execute()      ← 只做委托
    → BackupService.backup()            ← 真正的业务代码
```

**多了一层栈帧**。如果日志里没有 `getName()`，排查会更难——
这就是为什么本项目把 `getName()` 放进接口，而不是"打个日志就够了"（§4.3）。

**缺点 6 和优点 2 是同一枚硬币的两面**：解耦带来了额外的一层，就必然带来额外的一层调试成本。
**唯一的补偿方式是让这一层"可观测"**——名字、编号、结构化历史，都是为此存在的。

---

### 8.3 与相似模式的对比

#### 全景表

| 模式 | 一句话 | 变化点 | 关键区别 |
|---|---|---|---|
| **命令** | 把请求封装成对象 | **请求本身** | 有 Receiver，有副作用，可排队/撤销 |
| **策略** | 把算法封装成对象 | **算法实现** | 通常无 Receiver，纯计算，不可排队 |
| **模板方法** | 父类定流程，子类填步骤 | **步骤实现** | 继承，编译期固定，不能运行期换 |
| **观察者** | 状态变化通知多个订阅者 | **订阅者集合** | 一对多；通知是"已发生"，不可拒绝 |
| **责任链** | 请求沿链传递，谁处理谁负责 | **处理者顺序** | 一条请求最终由某一个处理；命令是"指定谁做" |
| **装饰器** | 动态给对象加职责 | **包装层** | 命令也能被装饰（`DryRunCommand`），但目的不同 |

#### 命令 vs 策略（最容易混的一对）

两者都"把行为封装成对象"，代码长得也像。但**意图完全不同**：

| 维度 | 策略 | 命令 |
|---|---|---|
| 回答的问题 | **怎么做**（同一件事的不同算法） | **做什么**（一次具体的请求） |
| 有没有副作用 | 通常没有（纯计算，返回结果） | **一定有**（要改变世界） |
| 客户端心态 | "我要那个结果，用这个算法算" | "请把这件事办了，我不管谁办" |
| 有没有 Receiver | 通常没有 | **必须有**（否则模式落空） |
| 能不能排队 | 没意义（排队算同一个结果？） | **核心能力** |
| 典型 API | `BigDecimal apply(BigDecimal)` | `void execute() throws CommandException` |
| 本项目/前作例子 | `DiscountStrategy`（满减/打折/会员价） | `BackupDatabaseCommand` |

**最容易操作的判别方法**：

> **看返回值和参数。**
> 策略方法**有入参、有返回值**（`apply(price)` → `newPrice`）；
> 命令方法**通常无入参、无返回值**（`execute()`），它通过修改 Receiver 的状态来"表达结果"。

**少数情况下两者会重叠**（比如"导出报表"既是算法也有副作用）。
这时候问一句：**"我需不需要把这次调用存起来、排个队、撤个销？"**
需要 → 命令；不需要 → 策略（或者干脆直接调用）。

#### 命令 vs 模板方法

| 维度 | 模板方法 | 命令 |
|---|---|---|
| 变化机制 | **继承**（`extends`） | **组合**（持有 Receiver） |
| 绑定时机 | **编译期**（`new PhysicalProductOrder()` 就定了） | **运行期**（可以随时换一条命令） |
| 流程 | 父类把流程**锁死**，子类只能填坑 | 没有流程概念，每条命令自己决定 |
| 能不能排队 | ❌（对象就是"流程"，没有"请求"的概念） | ✅ |
| 复用方式 | 复用**流程**（子类只写变化部分） | 复用**业务能力**（Receiver 被多条命令共用） |

一句话：

> **模板方法管"顺序"，命令管"时机"。**
> 模板方法说"先校验再付款再发货，这个顺序不能变"；
> 命令说"这件事先别做，等我叫号"。

两者**可以一起用**：模板方法里某一步可以是"提交一条命令"，这在本项目里没做，
但在工作流引擎里非常常见。

#### 命令 vs 观察者

这是最需要分清的一对，因为两者都涉及"一个对象通知别人"：

| 维度 | 观察者 | 命令 |
|---|---|---|
| 语义 | "**已经**发生了什么" | "**请**做什么" |
| 能否拒绝 | ❌ 既成事实，不可拒绝 | ✅ 可以失败、可以拒绝 |
| 数量关系 | 一个事件 → **多个**监听者 | 一条命令 → **一个**执行者 |
| 有没有返回值/结果 | 通常没有（通知完就完） | 可以失败，失败要报 |
| 典型误用 | 把"发命令"写成"发事件" | 把"发事件"写成"发命令" |

**误用的症状**：

```java
// ❌ 把命令当事件发：发出去了就不能撤回，但代码里有 undo()
eventBus.publish(new OrderCreatedEvent(order));
eventBus.publish(new UndoOrderCreatedEvent(order));    // 自相矛盾
```

**命令可以被撤销，事件不能。** 一旦你把"请做某事"当成"某事已发生"发出去，
撤销就永远只能靠"再发一个反向事件"——**那不是撤销，那是补偿，而且没有隔离性**（§4.10）。

#### 命令 vs 责任链

| 维度 | 责任链 | 命令 |
|---|---|---|
| 谁决定处理者 | **链自己**（逐个尝试） | **客户端**（明确 `new` 哪条命令） |
| 处理者数量 | 可能经过多个，最终一个处理 | 一个 |
| 适合 | "不确定谁能处理"（审批流、过滤器） | "明确知道要做什么"（备份、发信） |
| 组合方式 | 链式（`setNext`） | 队列 / 宏 |

**判别一句话**：

> **责任链是"谁能办谁办"，命令是"我指定谁办"。**

本项目的 `MacroCommand` 常被误认为责任链——**但它不是**：
宏命令**所有步骤都会执行**，而责任链**找到处理者就停**。
（顺带说：责任链是下一批要重构的模式，到时候可以对照着看。）

---

### 8.4 九个常见误区

#### 误区 1：把 `Runnable` 当命令模式

```java
// ❌ 这叫"任务队列"，不叫命令模式
public interface Task { void execute(); }
public class EmailTask implements Task { ... }
```

**为什么错**：缺了三样东西——**Receiver**、**参数**、**名字**。
于是它只能"被调用一次"，不能撤销、不能重试、日志里没有信息。
这正是原项目的状态（§2）。

**正确做法**：先写 Receiver，再写只做委托和翻译的命令（§4.2、§4.4）。

> 注意：`Runnable` + `ExecutorService` 在**只需要异步执行**时是完全正确的选择。
> 误区不在于"用了 Runnable"，而在于"以为它就是命令模式"，
> 于是当需求变成"要撤销 / 要重试 / 要审计"时，误以为加个 `undo()` 就行。

#### 误区 2：以为命令必须能撤销

```java
// ❌ 为了"统一"，给所有命令加 undo
public interface Command {
    void execute();
    default void undo() { throw new UnsupportedOperationException(); }
}
```

**为什么错**：`SendEmailCommand` 的 `undo()` 只能抛异常，`MacroCommand` 的 `undo()` 只能骗人。
**"统一的接口"换来的是"统一的谎言"。**

**正确做法**：拆成 `Command` / `UndoableCommand`，让"不能撤销"成为类型事实（§4.7）。

#### 误区 3：在命令里写业务逻辑

```java
// ❌ 命令自己就是厨师
public class BackupCommand implements Command {
    public void execute() {
        Connection conn = DriverManager.getConnection(url);   // 业务逻辑
        // ... 50 行备份代码
    }
}
```

**为什么错**：业务能力被绑死在"命令"这个概念上，HTTP 接口想用只能 `new BackupCommand()`；
加一层重试/监控也无处下手（§2.4）。

**正确做法**：业务逻辑放 Receiver，命令只做"参数 + 委托 + 异常翻译"（§4.2）。

#### 误区 4：`catch (Exception e)` 一把抓

```java
// ❌ 原项目的写法
try {
    task.execute();
} catch (Exception e) {
    System.out.println("任务执行失败，记录日志以供重试...");
}
```

**为什么错**：NPE 和磁盘满被一视同仁，**程序 bug 被伪装成"今天这条任务没跑成"**，
重试一万次也没用，而且日志里没有任何原因（§2.6）。

**正确做法**：只 `catch CommandException`，让 bug 穿透（§4.5、§6.3）。

#### 误区 5：用 `poll()` 取出任务

```java
// ❌ 取出即销毁
while (!queue.isEmpty()) {
    Task task = queue.poll();
    try { task.execute(); } catch (...) { /* 任务已经没了 */ }
}
```

**为什么错**：失败的任务在整个进程里再也没有引用，**"记录日志以供重试"根本无法实现**。
探针的真实输出证明了这一点：第二次 `run()` 一片空白（§2.7）。

**正确做法**：快照执行 + `failed` 列表（§4.6）。

#### 误区 6：undo 时才去"查现场"

```java
// ❌ 撤销时读到的已经是新值
public void undo() {
    config.set(key, config.get(key));
}
```

**为什么错**：`undo()` 被调用时副作用已经发生，此时读到的是"被自己改过之后"的状态。
**它不会报任何错**，日志写着"撤销完成"，实际什么都没变（§4.8 坑 2）。

**正确做法**：在 `execute()` 里、**产生副作用之前**捕获现场（§4.8 坑 3）。

#### 误区 7：非幂等的补偿不做防护

```java
// ❌ 删两次会炸
public void undo() {
    backupService.delete(created.id());
}
```

**为什么错**：第二次撤销会抛 `IllegalStateException`，或者更糟——**删掉了别的对象**
（如果 id 被复用）。

**正确做法**：撤销后把现场字段置空，第二次进来直接拒绝（`BackupDatabaseCommand`，§4.8）。
**但要先判断是否幂等**：`UpdateConfigCommand` 改回旧值天然幂等，就**不需要**这个防护——
多余的防护会让代码更难懂。

#### 误区 8：把不可逆的操作放在流程中间

```java
// ❌ 邮件在第 2 步
new MacroCommand("发布",
        new UpdateConfigCommand(...),
        new SendEmailCommand(...),        // ← 后面失败了，邮件收不回来
        new GenerateReportCommand(...));
```

**为什么错**：报表失败时，配置回滚了、备份删了，但**客户已经收到"发布成功"的邮件**。
这种事故在真实复盘里非常常见（§4.10）。

**正确做法**：**把不可逆的操作放在流程最后**。这不是命令模式的规则，
而是**所有补偿型流程的通用工程原则**。

#### 误区 9：以为宏命令一定能撤销

```java
// ❌ 硬写一个 undo
public class MacroCommand implements UndoableCommand {
    public void undo() {
        for (Command step : steps) {
            if (step instanceof UndoableCommand u) u.undo();    // 不可撤销的步骤被静默跳过
        }
    }
}
```

**为什么错**：跳过不可逆的步骤，等于**告诉用户"撤销了"，实际没撤**。
最坏的情况是：用户以为请求没发出去，实际客户早就收到了。

**正确做法**：**不实现** `UndoableCommand`，并在文档里写清原因（§4.10、§5.4）。
`MacroCommand` 的 `rollback()` 只用于"执行中途失败时的部分补偿"，
**它和"用户主动撤销"是两件不同的事**：

| | `rollback()`（内部） | `undo()`（对外） |
|---|---|---|
| 触发者 | 宏命令自己（某步失败） | 用户 / 上层 |
| 范围 | 只补 `done` 里的步骤 | 全部步骤 |
| 遇到不可撤销步骤 | 打日志 + 继续补其余的 | **不提供这个能力** |

**把这两件事混成一个方法，是宏命令设计里最常见的错误。**

---

## 9. 面试与自测

### 9.1 十二道面试题

> 每题都按「**一句话核心 → 展开 → 加分点 → 减分回答**」组织。
> 面试时先说一句话，面试官有兴趣再展开——**这是最有效的答题节奏**。

---

**Q1：介绍一下命令模式。**

**一句话核心**：把"一次请求"封装成对象，于是请求可以被排队、记录、撤销、组合、重放，
而调用方不需要知道到底是谁在执行。

**展开**：五个角色——
`Command`（声明 `execute()`）、`ConcreteCommand`（持有 Receiver + 参数）、
`Receiver`（真正干活的业务对象，不知道命令存在）、
`Invoker`（决定何时执行，不认识 Receiver）、`Client`（组装）。

**加分点**：主动说出 GoF 定义里最容易被忽略的两个词——
**"queue or log requests"** 才是这个模式在生产环境里最大的价值，
"undo" 只是它的招牌能力，不是必需项。

**减分回答**：「命令模式就是把一个操作封装成对象，这样就可以撤销了。」
——只说了 undo，而且暗示了"必须能撤销"。

---

**Q2：命令模式解决了什么问题？举个你见过的真实场景。**

**一句话核心**：解决"**请求的提出**"和"**请求的执行**"在时间、空间、对象上的三重耦合。

**展开**：三个解耦方向——
① 时间：入队，稍后执行（任务队列）；
② 空间：序列化，换进程执行（消息队列、RPC）；
③ 对象：Invoker 不认识 Receiver（新增业务不改调度器）。

**加分点**：举 **redo log** 这个例子——数据库先把"要改什么"写成记录、刷盘，再改数据页；
断电重启后按日志重放。**这是"请求即数据"的极限形态**，一个方法调用永远做不到。

**减分回答**：「解决了解耦问题。」——太笼统，面试官无法判断你是否真的理解。

---

**Q3：为什么必须要有 Receiver？没有会怎样？**

**一句话核心**：没有 Receiver，命令自己就是厨师，**"Invoker 不认识 Receiver"这条卖点自动落空**。

**展开**：三个具体后果——
① 业务能力被绑死在"命令"这个概念上（想在 HTTP 接口里备份，得 `new BackupCommand()`）；
② 加横切关注点（重试、监控、超时）无处下手，只能改每个命令；
③ 请求无法被序列化重放（业务逻辑和参数混在一起，逻辑部分没法存）。

**加分点**：指出"**能不能撤销取决于 Receiver 有没有提供补偿能力**"。
`EmailService` 没有 `recall()`，所以 `SendEmailCommand` 永远不可撤销——
这是物理世界的限制，不是设计偷懒。

**减分回答**：「Receiver 就是真正执行的类，没有的话命令自己执行也行。」
——这句话恰好证明了没理解。

---

**Q4：`Command` 接口里为什么要有一个 `getName()`？**

**一句话核心**：**只有命令自己知道自己的全部参数**，所以名字必须由它给出。

**展开**：如果让调用方传名字（`submit(cmd, "备份 orders_db")`），会漂移——
参数改了字符串没改；命令被重试/包装/从数据库还原时，**没有调用方**，谁来取名？

**加分点**：说出 `getName()` 的约定必须**包含关键参数**：

```
✅ "备份数据库 orders_db（400MB）"
❌ "BackupDatabaseCommand"    ← 类名对运维没意义
❌ "备份"                      ← 哪个库？
```

**减分回答**：「为了打日志方便。」——对，但没说出"为什么不能由调用方传"。

---

**Q5：`execute()` 为什么声明受检异常？现在很多框架都用非受检的。**

**一句话核心**：受检异常是"**编译器提醒你必须做决定**"的机制，而这正是业务失败需要的。

**展开**：业务失败（磁盘满、0 行数据、地址非法）每天都会发生，
调用方必须显式决定"重试 / 告警 / 跳过"。非受检的话，"忘了处理"和"故意不处理"在代码里长得一模一样——
这正是原项目 `catch (Exception e) {}` 能存活下去的根本原因。

**加分点**：**主动说出反例**——Spring / Hibernate 用非受检是对的，
因为它们的异常要穿过无数层拦截器，最终由全局处理器统一接。
判据是「**异常会在哪一层被处理**」：1-2 层内处理 → 受检；穿过大量框架代码 → 非受检。

**减分回答**：「受检异常更安全。」——太绝对，会暴露你没考虑过框架场景。

---

**Q6：为什么把 `undo()` 拆成独立的 `UndoableCommand`，而不是在 `Command` 里给个 default 方法？**

**一句话核心**：「能撤销」是**部分命令的能力**，不是命令的本质属性；
塞进主接口等于**用运行期异常表达类型系统的缺失**。

**展开**：`default void undo() { throw new UnsupportedOperationException(); }` 的三个问题——
① 所有命令**看起来**都能撤销；② "哪些能撤销"这个信息丢了；
③ 调用方只能运行期试探，而且**可能已经把状态改了一半**。
拆开后，`instanceof UndoableCommand` 让"能不能撤销"成为**编译期事实**。

**加分点**：说出这是 **ISP（接口隔离原则）**的直接体现，并给出具体收益：
`MacroCommand.rollback()` 里可以写 `if (step instanceof UndoableCommand u)`，
而不是 `try { step.undo(); } catch (UnsupportedOperationException e) {}`。
**前者是类型判断，后者是异常控制流。**

**减分回答**：「拆开更符合单一职责。」——方向对，但没说出"不拆会怎样"。

---

**Q7：`undo()` 需要的"现场"应该在什么时候捕获？**

**一句话核心**：**在 `execute()` 里、产生副作用之前的那一刻**，而且必须紧邻。

**展开**：三个坑——
① 不能在**构造函数**里（命令可能在队列里躺很久，值会过期；命令可能永远不执行）；
② 不能在 **`undo()`** 里才查（那时副作用已经发生，读到的是新值，**而且不会报错**）；
③ 在 `execute()` 里也必须**先捕获再修改**（写反了就捕获到新值，撤销等于什么都没做）。

**加分点**：说出「这种 bug **不抛异常、不打日志**，只有对比前后快照才能发现」，
并举 `previousValue = get(); set(newValue);` 这个具体顺序。

**减分回答**：「在 `undo()` 里查一下当前值再改回去。」——这是最典型的错误答案。

---

**Q8：命令模式的撤销和数据库事务回滚有什么区别？**

**一句话核心**：**撤销没有隔离性**——中间状态对外可见，所以它叫"补偿"，不叫"回滚"。

**展开**：用表格说清——

| | 事务（2PC） | 命令的 undo / Saga |
|---|---|---|
| 中间状态 | 锁住，别人看不见 | **看得见** |
| 一致性 | 强一致 | 最终一致 |
| 失败后 | `ROLLBACK`，像没发生 | 逐个补偿，**痕迹留在日志和外部系统** |

**加分点**：指出适用于"中间状态短暂可见且可容忍"的场景；
支付、库存扣减这类不能容忍中间态的业务，**不能靠 `undo()` 顶上去**。

**减分回答**：「一样的，都是回滚。」——这是最危险的答案。

---

**Q9：宏命令为什么通常不实现 `UndoableCommand`？**

**一句话核心**：**一条命令能不能撤销，取决于它的每一步能不能撤销**；
只要有一个不可逆的步骤，整体就不可撤销。

**展开**：本项目的例子是 `SendEmailCommand`——邮件发出去收不回来。
所以 `MacroCommand` 只提供 `rollback()`（**执行中途失败时的部分补偿**），
不提供 `undo()`（**用户主动撤销**）。这两件事必须分开。

**加分点**：说出由此推出的工程原则——**把不可逆的操作放在流程最后**。
并指出"承认不可撤销，比硬写一个会骗人的 `undo()` 诚实得多"。

**减分回答**：「宏命令的 undo 就是循环调用每一步的 undo。」
——如果某步没有 undo 呢？静默跳过就等于骗用户。

---

**Q10：命令模式、策略模式、模板方法怎么区分？**

**一句话核心**：
**模板方法管"顺序"（继承，编译期固定）；策略管"怎么做"（组合，纯计算）；命令管"做什么"（请求对象化，有副作用）。**

**展开**：最可操作的判别是看**返回值**——策略方法**有入参有返回值**（`apply(price) → newPrice`）；
命令方法**通常无入参无返回值**（`execute()`），它通过修改 Receiver 的状态来表达结果。

**加分点**：补充第三组对比——**命令不是事件**。
命令是"**请**做某事"，可失败可拒绝；事件是"某事**已经**发生"，是既成事实、可多监听者。
把两者混了，就会写出"发出去的命令还能撤回"这种自相矛盾的设计。

**减分回答**：「都是行为型模式，都用了多态。」——等于没说。

---

**Q11：命令模式的缺点是什么？你怎么缓解？**

**一句话核心**：**类膨胀 + 多一层间接 + 有状态命令不能复用**，三条都是实打实的成本。

**展开**：N 个操作 → N 个命令类；
读代码要"命令 → Receiver"跳两次；
实现 `UndoableCommand` 后命令**不能复用、不能跨线程共享**。

**加分点**：说出**最大的坑**——「有人把命令类打成 `@Component` 单例，注入两处各执行一次，
撤销时删掉了别人的备份」。并给出缓解办法：
**只给"需要排队/撤销/审计"的操作建命令**，其余直接调用。

**减分回答**：「缺点是代码变多了，但是为了扩展性值得。」
——没有边界，等于承认"任何地方都值得用"。

---

**Q12：你在 Spring 项目里会怎么用命令模式？**

**一句话核心**：**命令对象 + CommandBus（Invoker）+ Handler（Receiver）**，
但只在**需要异步、可撤销、可审计**的场景用。

**展开**：常见的落点——
① 定时任务（XXL-Job / Quartz）里每个 Job 是一条命令；
② 审批流 / 工作流的每个节点是一个 Handler；
③ 操作审计：所有写操作走一个 `CommandBus`，统一记录；
④ Saga：分布式事务里每一步是一个可补偿的命令。

**加分点**：指出 **Spring 的 `ApplicationEventPublisher` 不是命令模式**——
它是观察者 + 事件（既成事实、可多监听者、不可拒绝）。
另外 `TransactionTemplate.execute(callback)` 也不算命令模式——
它是**模板方法**（流程锁在模板里，callback 只是填一个洞）。

**减分回答**：「把所有 Service 方法都包成 Command，统一走 Bus。」
——典型的过度设计，一个 CRUD 接口会被包成三层。

---

### 9.2 八道自测题

> 先自己回答，再点开看答案。**答不出第 3、4、5 题说明 undo 这块还没掌握。**

---

**自测 1**：如果删掉 `Command.getName()`，改成 `scheduler.submit(command, "名字")`，会有什么问题？

<details>
<summary>答案</summary>

四个问题：

1. **名字会漂移**：参数从 `orders_db` 改成 `orders_db_v2`，字符串忘了改，日志开始撒谎；
2. **重试/撤销时没人传名字**：`retryFailed()` 从 `failed` 列表里拿命令，**那里没有名字**；
3. **从数据库还原时没有调用方**：§7.5 的重放场景里，命令是反序列化出来的，
   谁给它取名？名字必须能从字段里算出来；
4. **宏命令/装饰器包一层后名字失效**：`MacroCommand` 的名字是"发布 v2.3.1（4 步）"，
   它必须能自己算，而不是依赖构造时随便传的一个串。

**核心**：名字是命令的**固有属性**，不是外部附加的标签。

</details>

---

**自测 2**：把 `run()` 里的"快照执行"改成 `while (!queue.isEmpty()) { queue.poll(); ... }`，什么时候会出问题？

<details>
<summary>答案</summary>

当**命令在执行过程中提交新命令**时。具体例子：

```java
public class RetryOnceCommand implements Command {
    public void execute() {
        if (!tryBackup()) {
            scheduler.submit(new BackupDatabaseCommand(...));   // ← 执行中新命令入队
        }
    }
}
```

用 `while` 的写法，新提交的命令会被卷进**这一次** `run()` 里：
- 输出里会突然多出几条"本轮之外"的任务；
- 如果命令总是失败、总是重新入队 → **死循环**。

快照的语义是明确的：**本次 `run()` 只处理"进入时已经在队列里"的那一批。**

另外 `poll()` 还有第二个问题：取出来的任务一旦失败，**没有引用留住它**（§2.7）。

</details>

---

**自测 3**：把 `UpdateConfigCommand.execute()` 里的两行对调成 `set()` 再 `get()`，哪个 demo 会"看起来正常但实际错了"？

<details>
<summary>答案</summary>

**demo4。** 而且它**几乎不可能被肉眼发现**，因为：

- 执行输出完全正常（`配置服务：payment.timeout 10s → 30s`）；
- 撤销也会打印 `配置服务：payment.timeout 30s → 10s`——**等等，这里就有破绽**。

实际上如果 `previousValue` 捕获到的是新值 `30s`，那么：
- `undo()` 会执行 `set(key, "30s")`；
- `ConfigService.set()` 里 `old.equals(value)` 为真 → **什么都不打印**。

所以真正的症状是：**撤销那一步没有任何 `└ 配置服务：…` 输出**，但 `[历史] 撤销完成` 照打。
然后"撤销后"的配置快照仍然是 `{payment.timeout=30s, ...}`，与"初始配置"不一致。

**正确做法**：先 `get()` 再 `set()`，且两行必须紧邻（§4.8）。

</details>

---

**自测 4**：为什么 `BackupDatabaseCommand.undo()` 要 `created = null`，而 `UpdateConfigCommand.undo()` 什么都不用清？

<details>
<summary>答案</summary>

因为**两种情况对"重复调用"的容忍度不同**：

| | `BackupDatabaseCommand` | `UpdateConfigCommand` |
|---|---|---|
| 补偿动作 | `delete(id)` | `set(key, previousValue)` |
| 重复执行 | ❌ 第二次删会抛 `IllegalStateException` | ✅ 结果一样（幂等） |
| 需要防护吗 | **需要** → `created = null` | **不需要** |

**判据**：不是"习惯"，而是"**这个操作重复执行会不会出问题**"。

注意 `created = null` 不只是"标志位"——它**真的把引用丢掉了**，
既防重复撤销，也避免内存里留着一个磁盘上已不存在的备份对象。

**反例警告**：给幂等操作加防护不算错，但会让代码更难懂。
`UpdateConfigCommand` 如果也加一个 `undone` 标志，读者会以为"这操作有什么我不知道的坑"。

</details>

---

**自测 5**：把 `MacroCommand.rollback()` 里的 `done.add(step)` 移到 `try` 外面（失败的那步也进 `done`），会发生什么？

<details>
<summary>答案</summary>

失败的那一步会被尝试补偿。对于 demo5.1，第 3 步 `GenerateReportCommand` 失败后
`createdFileName` 是 `null`，于是 `undo()` 会抛：

```java
throw new IllegalStateException(getName() + " 尚未成功执行，或已经撤销过，不能重复撤销");
```

这个异常会**从 `rollback()` 里穿出去**，导致：
1. **前面的补偿被中断**——如果它排在补偿顺序的最后，前两步已经补完了，影响不大；
   但如果顺序反过来（先补失败的那一步），**前两步就再也不会被补偿**；
2. 原本那个"第 3/4 步失败，已补偿 2 步"的异常被一个 `IllegalStateException` 覆盖，
   **真正的失败原因丢失**。

**正确做法**：`done.add(step)` 必须在 `step.execute()` **成功返回之后**（§4.10）。
失败的那一步自己就没改成任何东西，不需要也不应该被补偿。

</details>

---

**自测 6**：如果 `CommandException` 只保留 `getMessage()`、删掉 `getDetail()`，哪个输出会变难读？

<details>
<summary>答案</summary>

**demo2 的失败行和 demo5 的汇总行。**

因为 `getMessage()` 返回的是 `"备份数据库 logs_db（600MB） 失败：磁盘空间不足：需要 600MB，可用 100MB"`，
而调度器打印的格式是 `"        × 失败：" + ???`，模型已经知道他刚才打的是哪条命令。

用 `getDetail()` 现在的输出：

```
  [2] 备份数据库 logs_db（600MB）
        × 失败：磁盘空间不足：需要 600MB，可用 100MB
```

用 `getMessage()` 会变成（这就是本项目修好之前的真实输出，§4.5）：

```
  [2] 备份数据库 logs_db（600MB）
        × 失败：备份数据库 logs_db（600MB） 失败：磁盘空间不足：需要 600MB， 可用 100MB
```

**一个句子说了两遍"备份数据库 logs_db（600MB）"。**

但 `getMessage()` 又不能删——它是给**堆栈**看的，那里没有表格提供上下文。
**两个都要有，这才是双参数设计的全部理由。**

</details>

---

**自测 7**：现在要加一条"删除配置项"的命令 `DeleteConfigCommand`，它可撤销吗？现场字段是什么？`undo()` 怎么写？

<details>
<summary>答案</summary>

**可撤销**——因为 `ConfigService` 同时提供了 `remove()` 和 `set()`，补偿能力具备。

现场字段需要**两个**：

```java
private String previousValue;    // 删之前的值
private boolean executed;        // 是否成功执行过
```

`previousValue` 一个字段不够，因为 `null` 有歧义（"没执行过" vs "原本就不存在"）——
这正是 §5.4 里 `UpdateConfigCommand` 需要 `executed` 的原因。

```java
@Override
public void execute() throws CommandException {
    previousValue = configService.get(key);      // 先捕获
    if (previousValue == null) {
        throw new CommandException(getName(), "配置项不存在，无法删除");   // 业务失败
    }
    configService.remove(key);                   // 再修改
    executed = true;
}

@Override
public void undo() {
    if (!executed) {
        throw new IllegalStateException(getName() + " 尚未执行，不能撤销");
    }
    configService.set(key, previousValue);       // 这里 previousValue 一定非 null
}
```

**三个要点**：
1. "要删的 key 不存在"是**业务失败**（`CommandException`），不是 bug——
   因为并发场景下别人可能刚删过；
2. `undo()` 里**不需要**判 `previousValue == null`，因为 `execute()` 已经保证了它非 null；
3. 这个 undo **幂等**（`set` 两次结果一样），所以不需要 `undone` 标志。

</details>

---

**自测 8**：`TaskScheduler` 为什么不记录耗时？如果业务上一定要记，怎么保证文档仍然可复现？

<details>
<summary>答案</summary>

**为什么不记**：`System.nanoTime()` 的差值每次运行都不一样，
一行"耗时 0.3812ms"会让 README 里所有输出**永远无法对照**。
教学代码优先保证"读者能一行一行比对"。

**如果一定要记**，有三种办法保住可复现性：

| 办法 | 做法 | 代价 |
|---|---|---|
| A：开着但归一化 | 输出后把 `\d+\.\d+ms` 替换成固定串再比对 | 文档里的输出与真实输出不一致 |
| B：开关控制 | `new TaskScheduler(false)` 关闭耗时记录，文档用关掉的版本 | 文档展示的不是"默认行为" |
| C：结构化但不打印 | 存进 `ExecutionRecord` 的字段，**只提供 `getHistory()` 不打印** | 最诚实，但 demo 里看不到 |

本项目选的是**最保守的做法：不记**。并在附录 E 的"尚未实现"里明确列出。

**加分认识**：这是"**教学真实**"与"**工程真实**"的取舍。
如果是生产代码，耗时要记（那是性能分析的基础）；
如果是教学代码，"可对照"比"完整"重要。**这个决定必须是有意识的，而不是随手写。**

</details>

---

### 9.3 三个必答问题

任务书要求每个模式都要回答三个问题。压缩成一张表：

| 问题 | 答案 |
|---|---|
| **为什么需要它？** | 因为"请求"在真实系统里需要**排队、记录、撤销、组合、重放**，而方法调用只能立刻发生、栈一弹就没了。原项目的 Demo 里这些需求一个都不存在，所以它的缺陷被藏住了（§2.2）。 |
| **为什么这样实现？** | 先写 Receiver（业务能力独立）→ 再抽 `Command`（统一类型）→ `getName()` 进接口（可观测）→ 构造器注入参数（消灭半成品）→ 两层异常（业务失败与 bug 分离）→ 快照 + `failed`（失败可重试）→ 拆 `UndoableCommand`（类型即能力）→ `CommandHistory`（不变量收口）→ `MacroCommand`（组合）。**十步推导，每一步都由一个具体需求逼出来**（§4.0）。 |
| **换一种实现会有什么区别？** | 不抽 Receiver → 业务能力被绑死在命令上（原项目）；不拆 `UndoableCommand` → 撤销降级为运行期试探；用 `poll()` → 失败任务消失；`catch (Exception)` → bug 被伪装成业务失败；宏命令硬写 `undo()` → 骗用户"已撤销"。**每一个"少做一步"都有具体的、可复现的输出证据**（§6 真实输出）。 |

---

### 9.4 一句话总结

> **命令模式做的事，是把"请帮我做这件事"从一次方法调用变成一个对象——**
> **于是它可以被排队、被记录、被撤销、被组合、被重放，**
> **而调用它的那一方，永远不需要知道到底是谁在做。**
>
> **它的地基是 Receiver，它的招牌是 undo，它真正的价值是"请求即数据"。**

---

## 附录：文件清单与运行方式

### 附录 A：文件清单

目录：`src/com/patterns/behavioral/command/`，共 **16 个 java 文件、1923 行**。

| 文件 | 行数 | 类型 |
|---|---|---|
| `Command.java` | 102 | 接口 |
| `UndoableCommand.java` | 82 | 接口（继承 `Command`） |
| `CommandException.java` | 114 | 受检异常 |
| `ServiceException.java` | 58 | 受检异常 |
| `EmailService.java` | 82 | Receiver |
| `BackupService.java` | 140 | Receiver |
| `ReportService.java` | 86 | Receiver |
| `ConfigService.java` | 123 | Receiver |
| `SendEmailCommand.java` | 79 | 具体命令（不可撤销） |
| `BackupDatabaseCommand.java` | 76 | 具体命令（可撤销，非幂等补偿） |
| `GenerateReportCommand.java` | 69 | 具体命令（可撤销） |
| `UpdateConfigCommand.java` | 108 | 具体命令（可撤销，幂等补偿） |
| `MacroCommand.java` | 124 | 组合命令（不可撤销） |
| `TaskScheduler.java` | 211 | Invoker |
| `CommandHistory.java` | 135 | 撤销栈管理 |
| `Client.java` | 334 | 入口（五段 demo） |
| **合计** | **1923** | —— |

**行数比例值得看一眼**：

| 分组 | 行数 | 占比 |
|---|---|---|
| 模式骨架（`Command` + `UndoableCommand` + `MacroCommand`） | 308 | 16% |
| 异常体系 | 172 | 9% |
| Receiver（业务能力） | 431 | 22% |
| 具体命令（委托 + 翻译 + 现场） | 332 | 17% |
| 基础设施（`TaskScheduler` + `CommandHistory`） | 346 | 18% |
| 入口 demo（`Client`） | 334 | 17% |

**真正的"模式骨架"只占 16%。** 剩下 84% 是让它能跑起来的血肉——
这正是 §5.0 那句话的来源：

> **学模式时看到的是骨架，用模式时要写的是血肉。**

---

### 附录 B：编译与运行

#### B.1 命令

```powershell
$javac = "F:\enCodingApps\JDK\jdk-25\bin\javac.exe"
$java  = "F:\enCodingApps\JDK\jdk-25\bin\java.exe"
$src   = "E:\codeFile\codeFather\design_patterns_pro\src"
$out   = "E:\codeFile\codeFather\design_patterns_pro\out"

# 1) 编译（-g 必须带，见 B.3 坑 2）
New-Item -ItemType Directory -Path $out -Force | Out-Null
$files = (Get-ChildItem "$src\com\patterns\behavioral\command" -Filter *.java).FullName
& $javac --release 25 -encoding UTF-8 -g -Xlint:all -d $out @files
"COMPILE-EXIT=$LASTEXITCODE"

# 2) 运行（-D 参数必须加引号，见 B.3 坑 1）
& $java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" `
        -cp $out com.patterns.behavioral.command.Client
"RUN-EXIT=$LASTEXITCODE"
```

用 IDEA 的话：`Client.main()` 直接运行即可，
**但要在 VM options 里加上** `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8`，
否则 Windows 控制台里的中文可能变成乱码。

#### B.2 验证结果

| 项 | 结果 |
|---|---|
| `javac --release 25 -Xlint:all` | **`COMPILE-EXIT = 0`，输出 0 行（零警告）** |
| `java ... Client` | **`RUN-EXIT = 0`** |
| 输出行数 | **252 行**（13517 字节） |
| 连续运行四次 | **SHA-256 完全相同** |

哈希实测值（四次一致，含一次跨会话的运行）：

```
运行 1（本次）   B3E8033217F67B9195F224542E999684F7C24DAA135A38C4F4C29C555D36B6CB
运行 2（本次）   B3E8033217F67B9195F224542E999684F7C24DAA135A38C4F4C29C555D36B6CB
运行 3（本次）   B3E8033217F67B9195F224542E999684F7C24DAA135A38C4F4C29C555D36B6CB
运行 4（早前）   B3E8033217F67B9195F224542E999684F7C24DAA135A38C4F4C29C555D36B6CB
```

> 验证用的临时输出文件在本章写完后已清理，**但上面的哈希值是可随时重算的**：
> 按 B.1 的命令跑一遍，再对自己的输出文件算一次 SHA-256，就能独立复核这个结论。

**为什么这个哈希比对值得写进文档？**

因为它比"我看了一遍，输出一样"强得多：

| 验证方式 | 强度 |
|---|---|
| 肉眼看一遍 | 弱（252 行里错一个字看不出来） |
| `diff` 两个文件 | 中（能发现差异，但要人判读） |
| **哈希比对** | **强（任何一字节不同就会完全不同）** |

要做到这一点，代码里必须**不用随机数、不记耗时、所有要展示的集合都有序**（§5.3）。
**"可复现"不是文档的美德，是代码的约束。**

#### B.3 三个真实的坑

**坑 1：PowerShell 里 `-D` 参数会被拆开**

```powershell
# ❌ 错误：-Dfile.encoding=UTF-8 被 PowerShell 当成两个参数
java -Dfile.encoding=UTF-8 -cp out com.patterns.behavioral.command.Client
# 报错：找不到或无法加载主类 .encoding=UTF-8

# ✅ 正确：加引号
java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp out com.patterns.behavioral.command.Client
```

原因：PowerShell 会把 `-Dfile.encoding=UTF-8` 解析成 `-Dfile` 和 `.encoding=UTF-8` 两段，
`java` 收到的第一个参数变成 `-Dfile`（无值），第二个变成 `.encoding=UTF-8`——**被当成了主类名**。

> 这个错误信息（"找不到主类 `.encoding=UTF-8`"）会让人以为类路径配错了，
> **实际上类名是对的，是参数被拆了。**

**坑 2：不加 `-g` 时，NPE 消息里的变量名是 `<local1>`**

| 编译参数 | NPE 消息 |
|---|---|
| 默认（`-g:source,lines`） | `Cannot invoke "String.length()" because "<local1>" is null` |
| **`-g`（本项目）** | `Cannot invoke "String.length()" because "cacheDir" is null` |

JDK 14+ 的 Helpful NullPointerException 依赖**局部变量表**才能说出变量名。
`javac` 默认的 `-g:source,lines` **不包含局部变量表**（那需要 `-g:vars`，而 `-g` = 全开）。

**Maven / Gradle 默认就带 `-g`**，所以这个坑只在手敲 `javac` 时才会遇到。
但 `demo3` 的教学价值**完全依赖这一行输出**——没有变量名，读者看不出"bug 被埋掉"有多可怕。

> **一个编译参数，决定了一段教学输出有没有说服力。**

**坑 3：`javac --release 25` 报「不支持发行版本 25」**

```
error: release version 25 not supported
```

**这不是代码问题，是 PATH 里的 `javac` 不是 JDK 25。** 先用 `javac -version` 确认：

```powershell
javac -version       # 可能输出 javac 17.0.x —— 那就不是 JDK 25
& "F:\enCodingApps\JDK\jdk-25\bin\javac.exe" -version   # javac 25
```

**修法**：用全路径调用（本项目全部命令都是这么写的），或者把 `JAVA_HOME` 和 `PATH` 都指向 JDK 25。

> **以后看到「不支持发行版本 X」，先 `javac -version`，别先怀疑代码。**

---

### 附录 C：编码与文件操作的两个细节

**① Windows 控制台的中文输出**

本项目所有源码都是 UTF-8 无 BOM 编码，编译时用 `-encoding UTF-8`。
运行时如果不加 `-Dstdout.encoding=UTF-8`，控制台可能按系统默认编码（GBK）解码，
导致中文乱码。

**注意 `-Dfile.encoding` 和 `-Dstdout.encoding` 是两个不同的参数**：

| 参数 | 控制什么 |
|---|---|
| `-Dfile.encoding` | 文件读写、默认字符集 |
| `-Dstdout.encoding` | **标准输出**的编码（JDK 18+ 独立于 `file.encoding`） |

**只加 `-Dfile.encoding` 在 JDK 25 上可能仍然乱码**，因为 `System.out` 用的是 `stdout.encoding`。
两个都加最安全。

**② `Get-Content file.txt).Count` 和编辑器显示的行数可能差 1**

```powershell
(Get-Content "output.txt").Count      # 252
```

如果文件以换行符结尾，`Get-Content` 会返回 252 个元素（**不含最后那个空行**），
而有些编辑器会显示 253 行。

本项目统一按 `Get-Content` 的口径报行数。**报数字的时候一定要说清楚用的什么工具**，
否则"252 行"和"253 行"会被人当成矛盾。

---

### 附录 D：与原项目逐项对照

#### D.1 完整保留（3 条）

| 原项目 | 本项目 | 说明 |
|---|---|---|
| `Task` 接口（只有一个方法） | `Command` 接口 | 这一步是对的，是整个模式的地基 |
| `addTask()` 入队 + `run()` 执行 | `submit()` + `run()` | "提出"与"执行"分离，保留 |
| 逐条 try-catch，失败不中断 | 逐条 try-catch | 批量任务的正确姿势，保留 |

#### D.2 修复（6 条）

| # | 原项目的问题 | 本项目的修法 | 见 |
|---|---|---|---|
| 1 | 没有 Receiver，业务逻辑写在命令里 | 抽出 4 个 Receiver，命令只做委托 + 翻译 | §2.4、§4.2 |
| 2 | 命令无参数，只能做固定的一件事 | 参数全部构造器注入 + `final` | §2.5、§4.4 |
| 3 | `catch (Exception e)` 吞掉一切 | 两层异常，只 `catch CommandException` | §2.6、§4.5 |
| 4 | `poll()` 把失败任务丢掉 | 快照执行 + `failed` 列表 + `retryFailed()` | §2.7、§4.6 |
| 5 | 日志里没有任务名 | `Command.getName()`，且必须含关键参数 | §2.8、§4.3 |
| 6 | 没有 undo 的位置 | 拆 `UndoableCommand` + `CommandHistory` | §2.9、§4.7 |

#### D.3 新增（9 条）

| # | 新增的东西 | 为什么需要 | 见 |
|---|---|---|---|
| 1 | `ServiceException` | 区分"业务失败"与"程序 bug" | §4.5 |
| 2 | `CommandException`（双参数） | 让表格只打原因，堆栈仍完整 | §4.5 |
| 3 | `EmailService` / `ReportService` / `ConfigService` | 三个额外的 Receiver，支撑失败/撤销/补偿三条线 | §5.3 |
| 4 | `BackupService` 的磁盘容量建模 | 让失败"被算出来"而不是"被安排" | §4.2 |
| 5 | `CommandHistory`（双栈） | 撤销顺序有了唯一归属 | §4.9、§5.5 |
| 6 | `MacroCommand` | 一组命令当一条用，失败逆序补偿 | §4.10 |
| 7 | `ExecutionRecord` + `printHistory()` | 结构化审计记录 | §5.6 |
| 8 | `demo3` 的真实 NPE 演示 | 证明 bug 不会被伪装成失败 | §6.3 |
| 9 | 输出可复现性（有序容器 + 哈希比对） | 文档可被人逐行对照 | 附录 B |

#### D.4 返工记录（3 处，都是真实踩过的坑）

第一次跑通之后，**输出本身暴露了三个设计问题**，全部返工修掉：

| # | 症状（真实输出） | 根因 | 修法 |
|---|---|---|---|
| 1 | `× 失败：备份数据库 logs_db（600MB） 失败：磁盘空间不足…` | 命令把 `getName()` 拼进了异常消息，调度器又打了一遍 | `CommandException` 改双参数，表格用 `getDetail()` |
| 2 | 宏命令的 `← 补偿` 打在前，`! 第 3 步失败` 打在后 | `catch` 里先 `rollback()` 再抛异常 | 先打印失败原因，再补偿 |
| 3 | 5.2 成功后仍显示 `待重试 1 条` | 文案有歧义（其实是"累计"） | 改成「**累计**待重试」，并在 Client 里补充说明 |

**这三条都记在这里，而不是悄悄改掉。** 原因很简单：

> **一个"一次就写对"的文档，通常是没跑过的文档。**
> 把这些坑写下来，读者才知道"原来这里会踩"。

#### D.5 原项目的真实运行 vs 本项目的真实运行

| | 原项目 | 本项目 |
|---|---|---|
| 文件数 / 行数 | 6 / 115 | 16 / 1923 |
| 正常输出 | 6 行 | 252 行 |
| 失败时输出 | `任务执行失败，记录日志以供重试...`（无原因） | `× 失败：磁盘空间不足：需要 600MB，可用 100MB` |
| 失败任务能否重试 | ❌ 第二次 `run()` 一片空白 | ✅ `retryFailed()` |
| 程序 bug | 被 `catch` 吞掉，伪装成失败 | 穿透 `run()`，直接抛出 |
| 能否撤销 | ❌ | ✅ 双栈 undo/redo |
| 能否组合 | ❌ | ✅ `MacroCommand` |

---

### 附录 E：尚未实现 / 尚未验证的部分

**这一节存在的目的，是不让读者误以为"文档里没提的就是做完了"。**

#### E.1 刻意未实现（有理由）

| 缺什么 | 为什么不做 |
|---|---|
| `discardFailed()` | demo 里没有"人工放弃失败任务"的场景（§6.5） |
| 重试次数上限 / 退避 | 同上；且在 Invoker 里 `sleep` 会让输出不可复现 |
| `cancel()` | 语义模糊（取消引用？取消已执行一半的？），会污染职责分离（§5.6） |
| 命令序列化 / 落库重放 | 需要类型注册表和 Receiver 容器，超出单模式的演示范围（§7.5 给了设计） |
| 真正的事务 / 2PC | 补偿 ≠ 事务，本项目只演示 Saga 式补偿（§4.10） |
| 真线程池 / 异步 | 会让输出不可复现；且异步是"调用方式"，不是 Invoker 的职责（§7.6） |

#### E.2 已验证的范围

| 项 | 状态 |
|---|---|
| JDK 25 编译（`--release 25 -Xlint:all`） | ✅ 零警告 |
| JDK 25 运行 | ✅ 退出码 0 |
| 输出可复现（两次哈希一致） | ✅ |
| 全部 5 段 demo 的每条分支都被覆盖 | ✅（成功 / 失败 / 重试 / 撤销 / 重做 / 补偿 / NPE） |
| 原项目的真实输出与本项目的对比 | ✅ 均为真实运行结果 |
| 探针验证「异常被吞 + `poll()` 丢任务」 | ✅ 真实输出见 §2.2 |

#### E.3 未验证 / 有前提的部分

| 项 | 说明 |
|---|---|
| 其他 JDK 版本 | 只在 JDK 25 上编译运行过。语法上最低要求 **JDK 16**（用到 `record` 和 `instanceof` 模式匹配）；如果用 JDK 11，需要把 `record` 改成普通类 |
| 其他操作系统 | 只在 Windows + PowerShell 上验证过。Linux/macOS 下不需要 `-Dstdout.encoding` 参数 |
| 并发场景 | `TaskScheduler` **不是线程安全的**（`ArrayDeque` + `ArrayList`）。真实项目里要换成 `BlockingQueue` 或加锁 |
| 大规模命令数量 | demo 里最多 5 条命令。上百条命令时的性能、内存占用未测 |
| IDEA 下的输出编码 | 未在 IDEA 里验证控制台中文显示（见 B.1 的提示） |

---

### 附录 F：下一站

命令模式之后，行为型还剩 8 种。下一批是**批 2：责任链 + 状态 + 观察者**，三者的关系值得预告一下：

| 模式 | 和命令的关系 |
|---|---|
| **责任链** | 命令强调"**我指定谁做**"；责任链强调"**谁有能力谁做**"。本项目的 `MacroCommand` 常被误认为责任链——**但它所有步骤都会执行，责任链找到处理者就停**（§8.3） |
| **状态** | 命令的 `undo` 会改变系统状态，但"状态"这个模式关心的是"**对象在什么状态下该有什么行为**"。两者的结合点是"已发货的订单不能撤销"这类业务规则 |
| **观察者** | 最容易和命令混的一对。**命令是"请做某事"（可拒绝），观察者是"某事已发生"（不可拒绝）**（§8.3） |

三个模式都会遇到本项目已经解决过的问题：
**状态存在哪、失败怎么报、何时该拆接口**——
所以命令这一章里那些"看起来跑题"的论证（异常分层、类型即事实、快照 vs 视图），
在下一批里会全部再用一次。

> **一个模式学透的标志，不是能背出它的类图，**
> **而是能在下一个模式里认出同一批工程问题。**
