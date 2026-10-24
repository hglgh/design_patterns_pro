# 中介者模式：把「谁跟谁说」从六个同事手里收走

> **说人话版**
>
> 六个人一起办一件事，如果每个人都要记住「这事该找谁」，最后每个人都得背一张关系表。
> 中介者模式做的事很简单：**把这六张关系表撕掉，换成一张总表，挂在一个叫「中枢」的人墙上。**
> 六个人从此只跟中枢说话，谁也不认识谁。
>
> 表面上看是"多了一个中间人"，实际上是**把 N×(N-1) 条关系压成了 N 条** ——
> 而且那 N 条全都长得一样，都是"我说一句，中枢决定给谁"。

> **专业版**
>
> 中介者模式（Mediator Pattern）用一个中介对象来封装一系列对象之间的交互。
> 中介者使各对象不需要显式地相互引用，从而使其耦合松散，而且可以独立地改变它们之间的交互。
> 它属于**行为型模式**，关注的是"对象之间怎么协作"，而不是"对象怎么创建"或"怎么组合"。
>
> GoF 原书里把这个参与协作的类叫 **Colleague（同事）**。
> 本项目的 `Participant`（参与者）就是 Colleague 的直译改名 —— 因为"同事"在中文语境里
> 太容易被理解成"打工的同事"，而这里的角色还包括客户、仓库、财务、甚至一个短信网关。

---

## 目录

- [1. 现实案例：一张售后工单，为什么会吵成一锅粥](#1-现实案例一张售后工单为什么会吵成一锅粥)
  - [1.1 三个每天都在发生的场景](#11-三个每天都在发生的场景)
  - [1.2 从「人盯人」到「中枢排班」](#12-从人盯人到中枢排班)
  - [1.3 中介者到底省掉了什么](#13-中介者到底省掉了什么)
  - [1.4 什么时候你其实不需要中介者](#14-什么时候你其实不需要中介者)
- [2. 原始实现：不用中介者会怎样](#2-原始实现不用中介者会怎样)
  - [2.1 先看原项目的五个文件](#21-先看原项目的五个文件)
  - [2.2 原项目跑出来是什么样](#22-原项目跑出来是什么样)
  - [2.3 七个问题](#23-七个问题)
  - [2.4 七个问题的共同根源](#24-七个问题的共同根源)
- [3. 设计动机：为什么必须有一个中枢](#3-设计动机为什么必须有一个中枢)
  - [3.1 从「谁能跟谁说话」到「谁该收到什么」](#31-从谁能跟谁说话到谁该收到什么)
  - [3.2 星形拓扑的三个具体收益](#32-星形拓扑的三个具体收益)
  - [3.3 中介者必须持有状态，不能写成工具类](#33-中介者必须持有状态不能写成工具类)
  - [3.4 怎么判断该不该上中介者](#34-怎么判断该不该上中介者)
  - [3.5 中介者不是消息队列](#35-中介者不是消息队列)
- [4. 设计推导：十三个决定](#4-设计推导十三个决定)
  - [4.1 决定一：场景选「售后工单」，不选「聊天室」](#41-决定一场景选售后工单不选聊天室)
  - [4.2 决定二：角色用枚举，不用字符串，也不用 instanceof](#42-决定二角色用枚举不用字符串也不用-instanceof)
  - [4.3 决定三：消息类型用枚举，不用 String 前缀](#43-决定三消息类型用枚举不用-string-前缀)
  - [4.4 决定四：消息用 record，不用可变对象](#44-决定四消息用-record不用可变对象)
  - [4.5 决定五：`Participant` 用接口 + 默认方法，不用抽象类](#45-决定五participant-用接口-默认方法不用抽象类)
  - [4.6 决定六：`hub()` 只读，构造时绑定](#46-决定六hub-只读构造时绑定)
  - [4.7 决定七：中枢接口里一条路由规则都不放](#47-决定七中枢接口里一条路由规则都不放)
  - [4.8 决定八：路由表用 `switch` 表达式，不留 `default`](#48-决定八路由表用-switch-表达式不留-default)
  - [4.9 决定九：用 `EnumMap` 保证同角色唯一](#49-决定九用-enummap-保证同角色唯一)
  - [4.10 决定十：`deliver()` 里对每个收件方独立 try-catch](#410-决定十deliver-里对每个收件方独立-try-catch)
  - [4.11 决定十一：升级改的是 `assignee`，不是改某个人的字段](#411-决定十一升级改的是-assignee不是改某个人的字段)
  - [4.12 决定十二：`close()` 是唯一一处真正的广播](#412-决定十二close-是唯一一处真正的广播)
  - [4.13 决定十三：三种 `default` 策略并存](#413-决定十三三种-default-策略并存)
- [5. 代码解析：十三个文件](#5-代码解析十三个文件)
  - [5.1 文件全景](#51-文件全景)
  - [5.2 `Role.java`：六个角色，带中文名](#52-rolejava六个角色带中文名)
  - [5.3 `MessageKind.java`：七种消息语义](#53-messagekindjava七种消息语义)
  - [5.4 `Message.java`：一条不可变消息](#54-messagejava一条不可变消息)
  - [5.5 `Participant.java`：参与者接口](#55-participantjava参与者接口)
  - [5.6 `CollaborationHub.java`：中枢契约](#56-collaborationhubjava中枢契约)
  - [5.7 `AfterSalesHub.java`（上）：六个字段与 `join()`](#57-aftersaleshubjava上六个字段与-join)
  - [5.8 `AfterSalesHub.java`（中）：`say()` 与 `route()`](#58-aftersaleshubjava中say-与-route)
  - [5.9 `AfterSalesHub.java`（下）：`escalate()` / `close()` / `deliver()`](#59-aftersaleshubjava下escalate-close-deliver)
  - [5.10 `Customer.java`：只关心三种消息](#510-customerjava只关心三种消息)
  - [5.11 `Agent.java`：唯一"什么都收"的角色](#511-agentjava唯一什么都收的角色)
  - [5.12 `Warehouse.java`：最薄的角色](#512-warehousejava最薄的角色)
  - [5.13 `Finance.java`：两个分支，一条因果链](#513-financejava两个分支一条因果链)
  - [5.14 `Supervisor.java`：升级之后的负责方](#514-supervisorjava升级之后的负责方)
  - [5.15 `Notifier.java`：对外渠道的安全边界](#515-notifierjava对外渠道的安全边界)
  - [5.16 `Client.java`：六段 Demo](#516-clientjava六段-demo)
- [6. 执行流程：六段 Demo 逐步拆解](#6-执行流程六段-demo-逐步拆解)
  - [6.1 为什么六段 Demo 都要先跑一遍 `build()`](#61-为什么六段-demo-都要先跑一遍-build)
  - [6.2 Demo 1：一句话，一个人收到](#62-demo-1一句话一个人收到)
  - [6.3 Demo 2：一句缺货，三方协同](#63-demo-2一句缺货三方协同)
  - [6.4 Demo 3：换个角色发言，换一批收件人](#64-demo-3换个角色发言换一批收件人)
  - [6.5 Demo 4：升级之后，同一句话换了一个收件人](#65-demo-4升级之后同一句话换了一个收件人)
  - [6.6 Demo 5：关闭工单，唯一一次真正的广播](#66-demo-5关闭工单唯一一次真正的广播)
  - [6.7 Demo 6：一家炸了，其他家照常](#67-demo-6一家炸了其他家照常)
  - [6.8 三种不同的输出顺序，来自三个不同的地方](#68-三种不同的输出顺序来自三个不同的地方)
  - [6.9 六段 Demo 与七个缺陷的对应关系](#69-六段-demo-与七个缺陷的对应关系)
  - [6.10 收尾：`summary()` 那八行](#610-收尾summary-那八行)
- [7. 扩展对比：需求变了改哪里](#7-扩展对比需求变了改哪里)
  - [7.1 先给结论：一张改动成本表](#71-先给结论一张改动成本表)
  - [7.2 加一个角色：质检](#72-加一个角色质检)
  - [7.3 加一种消息类型：退款驳回](#73-加一种消息类型退款驳回)
  - [7.4 换投递通道：从方法调用换成消息队列](#74-换投递通道从方法调用换成消息队列)
  - [7.5 加"撤回上一条消息"](#75-加撤回上一条消息)
  - [7.6 改成一单多个客服](#76-改成一单多个客服)
  - [7.7 如果改用观察者模式](#77-如果改用观察者模式)
  - [7.8 如果改用责任链模式](#78-如果改用责任链模式)
  - [7.9 如果改用外观模式](#79-如果改用外观模式)
  - [7.10 五个模式的边界对照表](#710-五个模式的边界对照表)
  - [7.11 扩展时必须同步的三处清单](#711-扩展时必须同步的三处清单)
- [8. 优缺点与误区](#8-优缺点与误区)
  - [8.1 适用场景：从代码形状反推](#81-适用场景从代码形状反推)
  - [8.2 设计代价：五个具体的](#82-设计代价五个具体的)
  - [8.3 七个常见误解](#83-七个常见误解)
  - [8.4 中介者最容易退化的三种形态](#84-中介者最容易退化的三种形态)
  - [8.5 本项目的已知局限（诚实清单）](#85-本项目的已知局限诚实清单)
- [9. 面试与自测](#9-面试与自测)
  - [9.1 十个高频问题](#91-十个高频问题)
  - [9.2 自测题](#92-自测题)
  - [9.3 参考答案](#93-参考答案)
  - [9.4 最后一张自检表](#94-最后一张自检表)
- [附录 A：文件与行数清单](#附录-a文件与行数清单)
  - [A.1 十三个文件逐项数据](#a1-十三个文件逐项数据)
  - [A.2 二十二个 class 文件](#a2-二十二个-class-文件)
  - [A.3 `Client$Team` 和 `Client$BrokenNotifier`](#a3-clientteam-和-clientbrokennotifier)
- [附录 B：编译与运行验证](#附录-b编译与运行验证)
  - [B.1 环境](#b1-环境)
  - [B.2 编译命令](#b2-编译命令)
  - [B.3 编译结果](#b3-编译结果)
  - [B.4 运行命令](#b4-运行命令)
  - [B.5 三次运行结果](#b5-三次运行结果)
  - [B.6 踩过的坑：`java.exe` 输出重定向成 mojibake](#b6-踩过的坑javaexe-输出重定向成-mojibake)
  - [B.7 静态检查](#b7-静态检查)
  - [B.8 编译器对照实验：`switch` 表达式 vs 语句](#b8-编译器对照实验switch-表达式-vs-语句)
- [附录 C：原项目缺陷探针全文](#附录-c原项目缺陷探针全文)
  - [C.1 探针怎么搭的](#c1-探针怎么搭的)
  - [C.2 `MediatorProbes.java` 的结构](#c2-mediatorprobesjava-的结构)
  - [C.3 七个探针分别做了什么](#c3-七个探针分别做了什么)
  - [C.4 完整输出（54 行）](#c4-完整输出54-行)
- [附录 D：真实输出全文](#附录-d真实输出全文)
  - [D.1 运行配置](#d1-运行配置)
  - [D.2 完整输出（125 行）](#d2-完整输出125-行)
  - [D.3 输出的结构](#d3-输出的结构)
- [附录 E：与原项目逐项对照](#附录-e与原项目逐项对照)
  - [E.1 规模对照](#e1-规模对照)
  - [E.2 概念对照](#e2-概念对照)
  - [E.3 七个缺陷的修复对照](#e3-七个缺陷的修复对照)
  - [E.4 保留了什么](#e4-保留了什么)
- [附录 F：术语表](#附录-f术语表)
  - [F.1 模式的角色](#f1-模式的角色)
  - [F.2 本项目特有的词](#f2-本项目特有的词)
  - [F.3 Java 语言相关](#f3-java-语言相关)
  - [F.4 两个容易混的异常](#f4-两个容易混的异常)
  - [F.5 两种失败策略](#f5-两种失败策略)
  - [F.6 三种"顺序来源"](#f6-三种顺序来源)
  - [F.7 三个"数量"](#f7-三个数量)
- [结语](#结语)
---

## 1. 现实案例：一张售后工单，为什么会吵成一锅粥

### 1.1 三个每天都在发生的场景

**场景一：客户问了一句话，五个人都收到了**

李女士买的一副无线耳机，左耳没声音。她在 App 里提交售后，写了一句话：

> 耳机左耳没声音，能退吗？

如果这个系统的消息层是"广播"式的，接下来会发生什么？

- 客服小王收到了 → 应该他来处理，正确。
- 仓库收到了 → 仓库只关心"有没有货、要不要出库"，客户问退不退货跟他没关系。
- 财务收到了 → 财务只关心"钱怎么走"，而且此时还没确定要退。
- 主管收到了 → 主管只该在异常时介入，每单都抄他就是噪音。
- 通知网关收到了 → 它可能把这条**内部提问**当成"要给客户发通知"，于是给客户发了一条"您有新消息"。

一句话，五个收件人，只有一个是对的人。**这就是广播的代价**。

真实系统里，这种"多收"不会立刻炸，但会慢慢长出三种病：

1. 每个角色都在自己的收件箱里过滤"这条跟我有关吗"，过滤逻辑写五遍；
2. 某天有人改了一个角色的过滤条件，另一个角色的行为跟着变；
3. 出了一次事故之后，大家在群里问"这条消息到底谁该收到"，没人能回答 ——
   因为规则散落在五个 `receive()` 方法里。

**场景二：仓库说了一句"缺货"，为什么要惊动三个人**

客服小王查完库存，发现华东仓缺货 37 件。这条事实至少有三方需要知道：

- **客服**：他要决定"是等补货还是直接退款"；
- **财务**：他要在退款真正发起之前冻结原发票，否则钱可能重复出；
- **客户**：他等了两天，有权知道"不是不给你退，是真没货"。

这是**一条事实，三个收件人**。而且这不是广播 —— 主管不需要知道（除非升级），
通知网关也不该把"华东仓缺货 37 件"这种内部库存数据发给客户看。

于是问题来了：**这条规则写在哪里？**

写法 A：写在 `Warehouse` 里。

```java
public class Warehouse {
    private Agent agent;
    private Finance finance;
    private Customer customer;

    public void reportShortage(String sku, int shortage) {
        String text = sku + " 缺货 " + shortage + " 件";
        agent.receive(text);
        finance.receive(text);
        customer.receive(text);   // ← 仓库现在认识三个人
    }
}
```

仓库从"我负责查库存"变成了"我负责查库存 + 知道公司有哪几个角色 + 知道谁该被通知"。
明天公司加一个"供应链"角色，仓库要改。后天客户不想被直接通知（要走客服话术），仓库还要改。

**一个只该回答"有没有货"的类，被迫懂了整个公司的组织结构。**

写法 B：写在 `Client` 里（谁调用谁负责通知）。

```java
warehouse.reportShortage(sku, shortage);
agent.receive(text);        // ← 调用方负责转发
finance.receive(text);
customer.receive(text);
```

这更糟。这条规则会散落在**每一个调用点**。第二个调用点忘了写 `finance.receive`，
就是一次"钱退了但发票没冻结"的财务事故，而且编译器不会提醒你。

**场景三：客户等了三天，要求升级**

小王手上压了 40 张单，李女士这张被淹了。李女士再次发消息：

> 等了 3 天没人处理，要求升级。

升级意味着什么？

1. 这张单子的**负责方从客服变成主管** —— 从此以后客户的提问直接到主管；
2. 主管要收到这条升级消息；
3. 客服要收到（他要停止跟进，避免两个人同时回复客户）；
4. 通知网关要给主管的手机发一条短信（主管可能半小时才看一次后台）。

这是一个**业务动作触发四个下游**的例子。如果这四条写在客户类里：

```java
public void escalate() {
    supervisor.receive("客户要求升级");
    agent.receive("客户要求升级");
    notifier.sendSms(supervisor.phone, "客户要求升级");
    // 还有：把负责方改成主管 —— 但"负责方"这个概念客户类根本不知道
}
```

客户类从此知道"公司里有主管"、"主管有手机号"、"升级之后负责方会变"。
而客户类可能是**已经发布到 App 里的客户端代码** —— 你改不动它。

> **三个场景的共同点**
>
> 每一个场景里，"谁该收到这条消息"都是一个**独立于收发双方的业务规则**。
> 它既不属于发送方（仓库不该懂财务流程），也不属于接收方（客户不该懂公司架构），
> 更不该散落在调用点（每个调用点都得抄一遍）。
>
> 它需要一个**专门的归属地**。这就是中介者。

### 1.2 从「人盯人」到「中枢排班」

想象一家小公司，五个人。一开始大家座位挨着，谁有事扭头喊一声就行。

公司长到五十人，问题来了：你喊一声，五十个人都听见；你想只告诉某一个人，
就得走过去找他 —— 而且你得先知道他在哪个工位。

两种解法：

**解法一：给每个人发一本通讯录。** 每个人手里都有全部 49 个人的工位号。
好处是找人方便；坏处是**每来一个新同事，49 本通讯录都要改**。
这就是网状拓扑：N 个节点，N×(N-1) 条边。

**解法二：设一个前台。** 所有人有事找前台，前台负责转达。
你不需要知道对方在哪个工位，甚至不需要知道对方**是谁** ——
你只要说"这是报销单"，前台知道该给财务。
这就是星形拓扑：N 个节点，N 条边，全部指向中心。

中介者模式就是解法二。用图表示：

```text
   网状（没有中介者）                    星形（有中介者）

  客户 ──── 客服                        客户      客服
   │  ╲    ╱  │                           ╲      ╱
   │   ╲  ╱   │                            ╲    ╱
   │    ╳     │                             ╲  ╱
   │   ╱  ╲   │                            ┌──────┐
   │  ╱    ╲  │              ────────▶     │ 中枢 │
  财务 ──── 仓库                            └──────┘
                                          ╱   │   ╲
                                        财务  仓库  通知器
```

左边每两条线都代表"这两个类互相持有引用"。5 个角色最多 20 条有向边。
右边每个角色只有 1 条边，指向中枢。

**关键不是"边变少了"，而是"边的语义变了"。**

- 左边的边是"我知道你存在"—— 这条边一旦建立，两个类就绑死了；
- 右边的边是"我知道中枢存在"—— 中枢对所有人都是同一个接口，
  里面**没有任何一个人的名字**。

所以加一个新角色时：

- 网状：新角色要跟已有的 5 个角色都谈一遍"我们之间怎么通信"；
- 星形：新角色调用 `hub.join(this)`，然后在中枢的规则表里加一行。

### 1.3 中介者到底省掉了什么

很多人对中介者的理解停留在"多了一个中间类，解耦了"。这句话没错，但太浅。
**解耦不是目的，解耦是为了让某一种特定的改动变得便宜。** 具体是哪一种？

来看这张表。假设业务需求按时间顺序演化：

| 时间 | 新需求 | 网状结构要改什么 | 星形结构要改什么 |
|---|---|---|---|
| T1 | 客户提问只给客服 | 客户持客服引用 | 规则表加一行 |
| T2 | 缺货要通知财务 | 仓库持财务引用 + 改上报方法 | 规则表那行加一个角色 |
| T3 | 财务退款要通知客户和客服 | 财务持两个引用 | 规则表加一行 |
| T4 | 升级后负责方变更 | 客户要改"发给谁" + 客服要清待办 + 主管要接管 | `assignee` 字段改一下 |
| T5 | 新增一个"供应链"角色 | 至少改 3 个类 | 加一个类 + 规则表加一行 |
| T6 | 通知网关只允许外发两类消息 | 每个发送方自己判断 | 网关自己的 `receive()` 里判断 |

**T4 是这张表里最关键的一行。**

网状的写法里，"客户提问发给谁"是**编译期决定的**（客户类里写着 `agent.receive(...)`）。
T4 要求它变成**运行期决定的**（有时候给客服，有时候给主管）。
这不是"改一行代码"能解决的 —— 这是**设计层面的缺陷**。

星形的写法里，`route()` 里写的是 `List.of(assignee)`，`assignee` 是个字段。
升级时把字段改一下，下一句提问自动改道。**客户类一个字都没改。**

> 这就是中介者真正的收益：
> **它把"路由"从一个编译期的常量，变成了一个运行期的状态。**

原项目（一个聊天室）根本不需要这个能力 —— 聊天室里所有人对所有人，路由永远是
"除我之外的所有人"，没有状态可言。所以它能用 30 行写完，也确实够用。

但售后协同不是聊天室。它的路由表有 7 种消息 × 6 种角色，还依赖"当前负责方"这个状态。
在这个场景下，原项目那 30 行就不够用了。

### 1.4 什么时候你其实不需要中介者

这是每一篇设计模式文档都该有、但大多数都没有的一节。
**中介者是有成本的，而且成本不低。**

**不需要的情况一：参与者只有两三个，而且关系稳定。**

如果系统里只有"A 调用 B"，那 B 就是 A 的依赖，这叫正常依赖，不叫耦合问题。
硬加一个中枢，你得到的是：

```java
// 本来的样子
orderService.submit(order);

// 加了"中介者"之后
orderService.submit(order);
hub.route(new OrderEvent(order), List.of(Role.ORDER_SERVICE));
```

多了一层，没有任何收益。**两个对象之间的直接调用不是耦合，是结构。**

**不需要的情况二：交互是单向的、一次性的。**

如果只有"下单 → 发邮件"这一条线，写个 `EmailSender` 就够了。
中介者的价值来自"多方、双向、规则会变"。三条都不占，就别上。

**不需要的情况三：真正的问题是"事件要广播给不确定的多个订阅者"。**

如果需求是"订单创建后，所有关心这件事的模块都要收到通知，而且以后会不断加模块"，
那你要的是**观察者模式**（或事件总线），不是中介者。区别在：

| | 中介者 | 观察者 |
|---|---|---|
| 谁知道谁 | 中枢知道所有参与者 | 主题只知道自己有一群订阅者 |
| 路由规则 | 集中的、有业务含义的 | 通常是"全部通知" |
| 参与者之间 | 通过中枢间接协作 | 彼此完全不知道 |
| 典型问题 | "这条消息该给谁" | "这件事发生了，通知所有关心的人" |

本项目的 `close()` 是唯一一处真正的广播 —— 正因为它是特例，
才值得在文档里单独说明（见 §5.9）。**如果一个系统里到处都在广播，那它需要的是观察者，不是中介者。**

**不需要的情况四：你在做的是一个"数据流管道"。**

如果需求是"输入 → 校验 → 转换 → 输出"，每一环的处理逻辑是固定的、线性的，
那你要的是**责任链**。责任链的每一环只认识下一环，也不需要中枢。

**不需要的情况五：这个"中枢"最终会变成上帝类。**

这是中介者模式**最常见的失败方式**，必须单独说。

中介者把规则集中了，这既是优点也是陷阱。如果业务规则持续增加，
`AfterSalesHub` 可能从 375 行长到 3000 行，里面有退款规则、库存规则、
通知规则、权限规则、超时规则…… 那时候它不再是"中介者"，而是**上帝类**。

判断标准很简单：

> **中枢里只该有"谁该收到什么"，不该有"收到之后怎么处理"。**

本项目的 `route()` 只有 18 行，一个 `switch`，每个分支返回一个角色列表。
退款金额怎么算在 `Finance` 里，库存怎么扣在 `Warehouse` 里，短信怎么发在 `Notifier` 里。
中枢对此一无所知 —— 它甚至不知道 `message.text()` 里面写的是什么意思。

一旦你发现自己在 `route()` 里写 `if (message.text().contains("退款"))`，
说明中枢开始理解业务内容了。**这是它变成上帝类的第一步。**

---

## 2. 原始实现：不用中介者会怎样

### 2.1 先看原项目的五个文件

原项目在 `design_patterns/src/BehaviorType/MediatorPattern/`，一共五个文件、
118 个物理行、63 个代码行。规模很小，正好适合逐行拆。

#### 2.1.1 `ChatRoomMediator`：中介者接口（15 行）

```java
package BehaviorType.MediatorPattern;

public interface ChatRoomMediator {
    void sendMessage(String message, User sender);

    void registerUser(User user);
}
```

两个方法，一进一出：

- `sendMessage(String message, User sender)` —— 发一条消息，参数是**正文**和**发送者**；
- `registerUser(User user)` —— 把一个人加进房间。

**这个接口奠定了后面所有问题的基调：它只有"广播"这一种语义。**

注意 `sendMessage` 的签名里**没有收件人**。这不是疏忽，而是聊天室场景的真实需求：
聊天室里说话，天然就是"所有人都听到"。接口忠实反映了这个场景。

问题在于，一旦把这个接口用到"不是所有人都在同一个房间、每个人的关注点不同"的场景，
它就再也表达不出"这句话只给某一个人"了。

#### 2.1.2 `ChatRoom`：具体中介者（30 行）

```java
public class ChatRoom implements ChatRoomMediator {
    private final List<User> users = new ArrayList<>();

    @Override
    public void sendMessage(String message, User sender) {
        for (User user : users) {
            if (user != sender) {
                user.receive(sender.name + "：" + message);
            }
        }
    }

    @Override
    public void registerUser(User user) {
        users.add(user);
    }
}
```

十行核心代码，但里面有五个值得注意的细节：

| 细节 | 代码 | 后果 |
|---|---|---|
| 广播语义 | `for (User user : users)` | 所有在册的人都收到，无法定向 |
| 排除自己 | `if (user != sender)` | 用**引用比较**判断"是不是我" |
| 读别人的字段 | `sender.name` | 中介者直接读参与者的 `protected` 字段 |
| 无去重 | `users.add(user)` | 同一个对象加两次，就收两条 |
| 无校验 | 同上 | 加 `null` 进去，广播时 NPE |

第三点特别值得说：`sender.name` 能编译，是因为 `User.name` 是 `protected`，
而 `ChatRoom` 和 `User` 在同一个包里。**中介者读了参与者的内部状态。**

这在聊天室里没什么问题（名字就是用来显示的），但在真实业务里，
"中介者要读参与者的什么"是一个必须控制的边界 —— 一旦它开始读
`participant.getPendingOrders().size()`，它就和参与者绑死了。

#### 2.1.3 `User`：抽象参与者（24 行）

```java
public abstract class User {
    protected String name;
    protected ChatRoomMediator mediator;

    public User(String name, ChatRoomMediator mediator) {
        this.name = name;
        this.mediator = mediator;
    }

    public abstract void receive(String message);

    public abstract void send(String message);
}
```

两个 `protected` 字段，都不是 `final`：

- `name` 可写 → 任何同包的类、任何子类都能改（探针 5 演示了冒名）；
- `mediator` 可写 → 子类可以在运行中途把发言通道换掉（探针 6 演示了跨房间发言）。

构造函数对 `name` 和 `mediator` **都不校验**。传 `null` 名字能构造成功，
传 `null` 中介者也能构造成功 —— 然后在第一次 `send()` 时 NPE。

#### 2.1.4 `ChatUser`：具体参与者（26 行）

```java
public class ChatUser extends User {
    public ChatUser(String name, ChatRoomMediator mediator) {
        super(name, mediator);
    }

    @Override
    public void receive(String message) {
        System.out.println(this.name + " 收到消息：" + message);
    }

    @Override
    public void send(String message) {
        System.out.println(this.name + " 发送消息：" + message);
        mediator.sendMessage(message, this);
    }
}
```

`send()` 里只有两句：打印 + 转发。这是**正确的中介者骨架** ——
参与者自己不决定消息去哪，交给 `mediator`。

`receive()` 里只有打印。这是**聊天室场景的必然结果**：聊天室里，"收到"就是"看见"，
没有"收到之后该做什么"可言。而真实业务里，"收到"往往意味着要做一个动作
（冻结发票、释放库存、发短信），行动逻辑的复杂度远高于打印。

#### 2.1.5 `ChatClient`：入口（27 行）

```java
public class ChatClient {
    public static void main(String[] args) {
        ChatRoom chatRoom = new ChatRoom();

        User yupi = new ChatUser("鱼皮", chatRoom);
        User yes = new ChatUser("Yes哥", chatRoom);
        User y = new ChatUser("小y", chatRoom);

        chatRoom.registerUser(yupi);
        chatRoom.registerUser(yes);
        chatRoom.registerUser(y);

        yupi.send("大家好！");
        yes.send("Hi 鱼皮！");
    }
}
```

注意这里的**注册是手写且重复的**：三个 `new`，三个 `registerUser`，一一对应。
如果手滑漏了一行，这个人就"能发言但收不到消息" —— 而且不会有任何报错。

### 2.2 原项目跑出来是什么样

用 JDK 25 编译（`-Xlint:all` 零告警），运行结果 6 行：

```text
鱼皮 发送消息：大家好！
Yes哥 收到消息：鱼皮：大家好！
小y 收到消息：鱼皮：大家好！
Yes哥 发送消息：Hi 鱼皮！
鱼皮 收到消息：Yes哥：Hi 鱼皮！
小y 收到消息：Yes哥：Hi 鱼皮！
```

三次运行 SHA256 完全一致：

```text
B85FC6A8856ED778D7EDDFC9887F65338FF1782C612D35DEEDA93D418F191C67
```

输出读起来很顺，对吧？**这个 Demo 作为"中介者模式的入门示例"是合格的** ——
它清楚地表达了"发送方不直接调用接收方，而是交给房间"。

但它是**一个足够简单的场景，简单到掩盖了模式的所有难点**。下面用七个探针，
把原项目放到真实业务的压力下，看它会在哪里裂开。

### 2.3 七个问题

以下七个探针（`MediatorProbes.java`）**直接使用原项目的源码，一个字节都不改**，
只额外写了几个调用者。所有输出都是真实运行结果，全文见[附录 C](#附录-c原项目缺陷探针全文)。

#### 2.3.1 问题一：名单和引用是两份状态，可以互相错配

**这是最隐蔽、也最危险的一个问题。**

`ChatRoom` 里有一个 `List<User> users`，这是"房间的名单"。
`User` 里有一个 `ChatRoomMediator mediator`，这是"这个人发言走哪个房间"。

**这两份状态是独立维护的，谁也不知道对方的存在。**

`ChatRoom.registerUser(user)` 做的事只有 `users.add(user)` ——
它把 user 加入自己的名单，但**完全不会修改 user.mediator**。

于是可以构造出这样的局面：

```java
ChatRoom roomA = new ChatRoom();
ChatRoom roomB = new ChatRoom();

ChatUser alice = new ChatUser("Alice", roomA);   // Alice 的 mediator 指向 roomA
ChatUser bob = new ChatUser("Bob", roomB);
ChatUser carol = new ChatUser("Carol", roomA);

roomA.registerUser(alice);
roomA.registerUser(carol);
roomB.registerUser(bob);
roomB.registerUser(alice);          // ← 把 Alice 也拉进 B 的名单
```

现在的状态是：

- `roomA.users` = [Alice, Carol]
- `roomB.users` = [Bob, Alice]
- `alice.mediator` = **roomA**

**Alice 出现在 B 的房间名单里，但她说话的时候走的是 A 房间。**

真实输出：

```text
[P1] 注册进哪个房间，和发言走哪个房间，是两件事
  roomA 名单：Alice、Carol
  roomB 名单：Bob、Alice
  Alice 的 mediator 字段仍然指向 roomA（registerUser 不会改它）
  Alice 在 B 房间里说一句话，Bob 应该能收到：
Alice 发送消息：B 房间的 Bob 你好
Carol 收到消息：Alice：B 房间的 Bob 你好
  -> Bob 一条都没收到；Carol 收到了。名单里有 Alice，不代表她的话从这个房间走
```

**这个 bug 在真实系统里长什么样？**

想象一个"项目群"功能：项目经理把 Alice 拉进了项目 B 的讨论组。
Alice 出现在成员列表里，但她的消息全发到了原来的项目 A 群。
群里的人看到成员列表里有 Alice，却永远等不到她说话。

更糟的是：**没有任何日志会提示这件事**。Alice 发言成功，A 群正常收到，
B 群的成员列表里也正常显示她在。三个现象都正常，只有一件事不对 ——
**她的话走错地方了。**

在一个 50 行的聊天室 Demo 里，这个 bug 永远不会出现，因为
"注册"和"构造"总是紧挨着写的。但一旦有"动态拉人进群"的功能，
这两步就会分开，bug 就出现了。

#### 2.3.2 问题二：注册不去重，同一条消息收两遍

`registerUser` 的全部实现就是 `users.add(user)`。没有 `contains` 检查，
没有 `Set` 去重。

```java
room.registerUser(a);
room.registerUser(b);
room.registerUser(b);               // 第二次注册，没人拦
a.send("只发了一次");
```

真实输出：

```text
[P2] 重复注册：网络重连、页面刷新都会导致同一个用户加两次
A 发送消息：只发了一次
B 收到消息：A：只发了一次
B 收到消息：A：只发了一次
  -> B 打印了两次「收到消息」；registerUser 里只有 users.add(user)
```

**这个 bug 在真实系统里怎么产生？**

- 用户断线重连，客户端重新调了一次"加入房间"；
- 页面刷新，前端重放了一次初始化请求；
- 后端做了重试，同一个"加入"请求被处理了两次。

这三种情况在分布式系统里都是**正常现象**，不是异常。所以"重复注册"不是
"调用方写错了"，而是**中介者必须自己处理的正常输入**。

重复注册的后果取决于 `receive()` 做了什么：

- 如果只是打印，就是两行重复日志（本项目探针的样子）；
- 如果是"扣一次库存"，就是**扣了两次**；
- 如果是"发一条短信"，客户就收到两条同样的短信，并开始怀疑这个 App。

**注意这个问题和问题一的关联**：它们都是"名单"没有被当作一份有约束的状态来管理。
一个合格的中介者，`join()` 里应该同时保证"不重复"和"名单与引用一致"。

#### 2.3.3 问题三：一个 null 让广播静默断链

`registerUser` 不校验参数。传 `null` 进去，`users` 里就多了一个 `null`。

```java
room.registerUser(a);
room.registerUser(b);
room.registerUser(null);            // 没有任何校验
room.registerUser(c);
room.registerUser(d);

try {
    a.send("有人能收到吗");
} catch (NullPointerException e) {
    System.out.println("  -> 抛 " + e);
}
```

真实输出：

```text
[P3] 一个 null 混进名单：广播断在那里，后面的人全部静默丢失
A 发送消息：有人能收到吗
B 收到消息：A：有人能收到吗
  -> 抛 java.lang.NullPointerException: Cannot invoke "BehaviorType.MediatorPattern.User.receive(String)" because "<local4>" is null
  -> B 收到了（排在 null 之前），C 和 D 一条都没收到（排在 null 之后）
  -> 而且异常一路抛到调用 send() 的那行代码，业务方无从补救
```

这里有三层问题，一层比一层严重：

**第一层：`null` 能进名单。** 这是入参校验缺失。

**第二层：广播中断在半路。** `users` 的顺序是 [A, null, B, C, D]。
遍历到 A 时跳过（A 是 sender），遍历到 `null` 时抛 NPE。
于是**排在 `null` 后面的 C 和 D 永远收不到**。

这意味着：**名单里任何一个位置的错误，会让它后面所有人的消息全部丢失。**
而且丢失是"静默"的 —— B 收到了，B 会认为"广播正常"；C 和 D 什么都没收到，
他们甚至不知道自己错过了什么。

**第三层：异常穿透到了调用方。** `NullPointerException` 一路抛到 `send()` 的
调用者那里。调用者收到的信号是"A 发消息失败了"，但事实是
**"A 发消息成功了一部分，B 收到了，C 和 D 没收到"**。

调用者拿到这个 NPE 能做什么？他没法知道"哪些人收到了、哪些没收到"，
也没法重试（重试会让 B 收两遍）。**这个异常携带的信息量为零，
但它终止了整个流程。**

对比本项目 `deliver()` 的处理（§5.9 会详细讲）：

```java
try {
    target.receive(message);
    delivered++;
} catch (RuntimeException e) {
    String record = target.name() + " 投递失败：" + ...;
    failures.add(record);
    System.out.println("      ! 已隔离：" + record);
}
```

**每个收件方独立 try-catch。** 一家炸了，其他家照常收到，失败进 `failures` 留证。
这是"投递"这件事在生产环境里的唯一正确写法。

> **一条可以带走的原则**
>
> 一个方法里只要有循环，就要问自己两个问题：
> 1. 循环体里的异常，该不该中断整个循环？
> 2. 中断了，调用方能不能知道"断在哪里"？
>
> 广播/投递/批处理，绝大多数情况下的答案是"不该中断"和"必须留证"。

#### 2.3.4 问题四：广播途中注册新人，直接 CME

`sendMessage` 用增强 for 遍历 `users`。如果**被遍历的集合在遍历过程中被修改**，
Java 的集合会抛 `ConcurrentModificationException`。

什么时候会改？最自然的一种：某个用户在 `receive()` 里邀请新人进群。

```java
private static final class SelfInvitingUser extends ChatUser {
    private ChatUser guest;

    @Override
    public void receive(String message) {
        super.receive(message);
        if (guest != null) {
            ChatUser pending = guest;
            guest = null;
            mediator.registerUser(pending);     // 在别人的广播循环里插队
        }
    }
}
```

真实输出：

```text
[P4] 收到消息时顺手拉个人进群：ConcurrentModificationException
A 发送消息：欢迎新人
Inviter 收到消息：A：欢迎新人
  -> 抛 java.util.ConcurrentModificationException
  -> 广播循环正在遍历 users，receive() 里又调 registerUser() 改了它
```

**注意 `Inviter` 已经收到了消息**（它的 `receive` 执行完了），
但 `A` 的 `send()` 抛异常了，所以排在 `Inviter` 后面的用户全部漏收。

这个场景在真实系统里非常常见：

- 客户在群里说"我把我们领导也拉进来了"→ 客服的自动回复逻辑里调用"拉人"；
- 工单关闭时广播通知，某个接收方在 `receive()` 里把工单转派给另一个部门；
- 通知网关收到消息后，往系统里注册了一个新的重试任务。

**根本原因是"遍历"和"修改"用了同一个集合。** 这个问题有两个方向：

1. **让集合不可变**：遍历前 `List.copyOf(users)`，这样中途 add 只影响下次广播；
2. **让修改排队**：`join()` 时如果正在广播，把请求放进待处理队列，广播结束后统一处理。

本项目选的是第三种，也是最彻底的：**`deliver()` 遍历的是 `targets`（一个方法内生成的
`List<Role>`），而不是 `participants` 本身。** 所以即使某个参与者在中枢回调里
再 `join` 一个人，也不会影响正在进行的这轮投递 —— 丢的是"新人这次收不到"，
而不是"所有人这次都收不到"。

#### 2.3.5 问题五：name 不校验也不 final

`User` 的两个字段都是 `protected` 且非 `final`。这带来两个后果。

**后果一：`null` 名字能构造成功。**

```java
ChatUser ghost = new ChatUser(null, room);
room.registerUser(ghost);
ghost.send("我是谁");
```

真实输出：

```text
null 发送消息：我是谁
A 收到消息：null：我是谁
```

发送方显示为 `null`，消息前缀也是 `null`。这不会崩，但会污染所有日志 ——
出事故时你在日志里看到 `null：我是谁`，完全无法定位是谁发的。

**后果二：同包或子类可以随时改名。**

```java
ghost.name = "冒名者";
ghost.send("现在我叫冒名者");
```

真实输出：

```text
冒名者 发送消息：现在我叫冒名者
A 收到消息：冒名者：现在我叫冒名者
```

在同一个包里，`protected` 字段对所有类是可见的。所以 `ChatClient`（同包）
可以直接改任何人的名字。**在真实系统里，这意味着"用户名"可能在任何地方被改掉，
而你无法通过阅读改动点来找到它的改写路径。**

本项目的做法：

```java
private final String name;

public Customer(String name, CollaborationHub hub) {
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("客户称呼不能为空白");
    }
    this.name = name.strip();
    this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
}
```

`private final` + 构造时校验 + `strip()` 规范化。三个动作，缺一不可：

- `private` 让外部无法改；
- `final` 让子类也无法改；
- 构造时校验让 `null` 和 `"   "` 在**创建的那一刻**就失败，而不是等到打印日志时才发现。

#### 2.3.6 问题六：mediator 可写，子类能中途换房间

`User.mediator` 是 `protected` 非 `final`。子类可以直接改：

```java
private static final class Hijacker extends ChatUser {
    void switchTo(ChatRoomMediator other) {
        this.mediator = other;          // 编译通过
    }
}
```

真实输出：

```text
[P6] mediator 是 protected 且非 final：子类随时能把发言通道换掉
Hijacker 发送消息：这句还在 A 房间
Watcher 收到消息：Hijacker：这句还在 A 房间
Hijacker 发送消息：这句已经跑到 B 房间，但我在 A 的名单里
Bystander 收到消息：Hijacker：这句已经跑到 B 房间，但我在 A 的名单里
  -> 同一个人，两句话走了两个不同的房间，没有任何日志提示
```

**这个探针和探针 1 是同一个病的两种表现**：

- 探针 1：`registerUser` 改名单，但改不了 `mediator` → 名单和引用错配；
- 探针 6：子类改 `mediator`，但改不了名单 → 引用和名单错配。

**两份状态，两条修改路径，两个方向都能错配。**

本项目的做法是**把两份状态合并成一份**：

```java
public interface Participant {
    CollaborationHub hub();     // 只读，且没有 setHub
}
```

- `hub()` 只有 getter，没有 setter；
- 每个实现类的 `hub` 字段都是 `private final`；
- 绑定发生在构造函数里，之后不可能改变。

更重要的是：**一个参与者只能属于一个中枢，这是由构造过程保证的，不是靠约定。**

#### 2.3.7 问题七：接口没有定向发送能力

这是七个问题里唯一一个**不是 bug，而是能力缺失**的。但在真实业务里，
能力缺失比 bug 更麻烦 —— bug 可以修，缺的能力只能改设计。

`ChatRoomMediator` 声明的方法只有两个。用反射打印出来：

```java
for (Method m : ChatRoomMediator.class.getDeclaredMethods()) {
    System.out.println("    " + m.getName() + " -> " + m);
}
```

真实输出：

```text
[P7] 接口的发送能力只有一种：发给除自己外的所有人
  ChatRoomMediator 声明的方法：
    registerUser -> public abstract void BehaviorType.MediatorPattern.ChatRoomMediator.registerUser(BehaviorType.MediatorPattern.User)
    sendMessage -> public abstract void BehaviorType.MediatorPattern.ChatRoomMediator.sendMessage(java.lang.String,BehaviorType.MediatorPattern.User)
  A 只想告诉 B 一件事，但 send() 没有收件人参数：
A 发送消息：这句其实只有 B 该看到
B 收到消息：A：这句其实只有 B 该看到
C 收到消息：A：这句其实只有 B 该看到
  -> C 也看到了。想只发给 B，只能 a 持有 b 的引用直接 b.receive(...)，
     那就不再经过中介者，模式也就名存实亡
```

> 补充说明：`getDeclaredMethods()` 的返回顺序在 JVM 规范里是**不保证**的。
> 上面 `registerUser` 排在 `sendMessage` 前面，和源码里的声明顺序相反 ——
> 这是真实运行结果，不是笔误。

**"想私聊，只能绕过中介者"** 这句话是本节的核心。

一个中介者如果不能满足"定向发送"，那么需要定向发送的代码就只能绕开它。
而一旦绕开，参与者就重新持有了别人的引用，中介者也就失去了存在的意义 ——
**它退化成了一个只剩广播功能的容器。**

本项目的 `CollaborationHub` 依然**没有**定向发送方法，但它提供了另一个东西：
**`say(from, kind, text)` —— 表达"消息的性质"，而不是"消息的收件人"。**

这个差别是整个模式的分水岭：

| | 原项目 | 本项目 |
|---|---|---|
| 发送方法的参数 | `(String, User)` | `(Participant, MessageKind, String)` |
| 谁决定收件人 | 中介者（但规则写死为"除我之外"） | 中介者（规则表按角色和性质查） |
| 发送方能否指定收件人 | 不能 | **也不能** |
| 那么"定向"怎么实现 | 做不到 | 通过"消息性质"间接表达 |

关键在最后一行：本项目的发送方**依然不能指定收件人**，但它可以说
"这是一条「缺货上报」"。中枢查表得知缺货要给客服、财务、客户。
**发送方表达的是"这是什么"，而不是"这给谁"。**

这不是文字游戏。它决定了改动成本：

- 如果发送方能指定收件人 → "缺货该通知谁"这个知识散落在每个调用点；
- 如果发送方只能表达性质 → 这个知识只有一份，在中枢的 `route()` 里。

**顺手补一个：原项目根本没有"消息类型"。**

严格来说这是第七个问题的延伸，但因为太重要，单独列出来。

`sendMessage(String message, User sender)` 里，`message` 是一个裸 `String`。
"这条消息是什么性质"这个信息，在进入中介者的那一刻就丢失了。

于是中枢永远无法做这样的事：

```java
if (kind == STOCK_SHORTAGE) {
    // 缺货只给客服 + 财务 + 客户
} else if (kind == REFUND_CONFIRMED) {
    // 退款只给客户 + 客服
}
```

它连"kind"这个变量都没有。**没有分类，就没有路由；没有路由，中介者就只剩广播。**

如果非要在原项目的结构上实现分类，唯一的技术路线是**解析字符串**：

```java
if (message.startsWith("【缺货】")) { ... }
else if (message.startsWith("【退款】")) { ... }
```

这就是"用文案当协议"。运维改一个字，路由就静默失效 —— 这正是迭代器模式文档
[§2.9](../iterator/README.md#29-问题七类型只能靠名字猜) 里那个坑的同一副面孔。

### 2.4 七个问题的共同根源

把七个问题排在一起看，会发现它们不是七个独立的 bug，而是**三个设计缺陷的七种表现**：

| 编号 | 问题 | 归到哪个根源 |
|---|---|---|
| P1 | 名单和引用错配 | 根源 A |
| P2 | 重复注册 | 根源 A |
| P3 | null 断链 | 根源 A + B |
| P4 | 遍历中修改 CME | 根源 B |
| P5 | name 可空可改 | 根源 C |
| P6 | mediator 可写 | 根源 A |
| P7 | 没有定向/分类能力 | 根源 B |

**根源 A：状态没有归属。**

"谁在这个房间"和"我发言走哪个房间"，是同一件事的两面，
却被放在 `ChatRoom.users` 和 `User.mediator` 两个地方，各自修改，互不感知。

**根源 B：职责没有划清。**

`ChatRoom` 只做了"遍历 + 转发"，它不知道"消息是什么性质"，
不知道"该给谁"，也没有"投递失败怎么办"。它把所有决策都推给了参与者，
而参与者又只认识 `mediator` 这个接口 —— **两边都不知道规则在哪。**

**根源 C：边界没有收紧。**

`protected` + 非 `final` + 无校验，让所有"内部状态"都变成了"公共可变状态"。

这三个根源，正好对应中介者模式在设计时必须回答的三个问题：

1. **状态归谁？** → 归中枢。中枢是唯一知道"有谁参加、谁是负责方、工单是否关闭"的地方。
2. **规则归谁？** → 归中枢。参与者只表达"我发了什么性质的"，不表达"给谁"。
3. **边界怎么收？** → 参与者只暴露只读身份（`role()` / `name()` / `hub()`），
   中枢只暴露动作（`join` / `say` / `escalate` / `close`），双方都不给对方留后门。

下一节就从这个"三个问题"出发，推导本项目的设计。

---

## 3. 设计动机：为什么必须有一个中枢

### 3.1 从「谁能跟谁说话」到「谁该收到什么」

上一节的七个问题，追到根上是三个：

1. 状态没有归属（P1、P2、P6）；
2. 职责没有划清（P3、P4、P7）；
3. 边界没有收紧（P5）。

现在换个问法：**如果把中介者写对了，它应该长成什么样？**

先把"中介者"这个词拆开。它在系统里其实回答两个完全不同的问题：

| 问题 | 谁关心 | 变不变 |
|---|---|---|
| 「**谁能跟谁说话**」 | 架构层面 | 相对稳定 |
| 「**这条消息该给谁**」 | 业务层面 | 经常变 |

原项目把这两个问题**合并成了一个答案**：`user != sender`。这个答案同时表达了两件事：

- 结构上：所有人在同一个房间，都能互相说话；
- 路由上：谁发的，就发给除他以外的所有人。

在聊天室场景里，这两个问题恰好有同一个答案，所以合并没有问题。
但在售后协同里，它们立刻分道扬镳：

- 结构上：六方都在同一张工单里（所以确实"都能说话"）；
- 路由上：客户的话只给负责方，仓库的话给三方，财务的话给两方 —— **每个人都不一样**。

**中介者模式的第一个设计动作，就是把这两个问题拆开：**

- 「谁能跟谁说话」交给**拓扑**：所有人只连中枢，这是编译期固定的；
- 「这条消息该给谁」交给**规则表**：`route(from, kind)`，这是运行期计算的。

这么一分，P7（没有定向能力）就自然解决了 —— 定向能力不是通过"发送方指定收件人"
获得的，而是通过"发送方表达消息性质 + 中枢查表"获得的。

### 3.2 星形拓扑的三个具体收益

"减少耦合"这句话太抽象。星形拓扑到底带来了什么，一条一条数：

**收益一：新增角色不影响任何已有角色。**

本项目的七个参与者类，没有一个持有另一个参与者类的引用。翻遍 `Customer.java`，
你找不到 `Agent` 这个词（除了 javadoc 里的说明）；翻遍 `Warehouse.java`，
你找不到 `Finance`。

所以新增一个"供应链"角色时：

```java
// 1. Role 里加一个枚举值
SUPPLY_CHAIN("供应链")

// 2. 写一个类
public final class SupplyChain implements Participant { ... }

// 3. 在需要的地方 join
hub.join(supplyChain)

// 4. 在 route() 的某几个分支里加上它
case STOCK_SHORTAGE -> ... List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER, Role.SUPPLY_CHAIN)
```

**前六个别家一个字都不用改。** 第 4 步甚至会被编译器强制你考虑（见 §4.8 的穷尽性）。

**收益二：路由规则可测试，而且测试成本极低。**

`route()` 是一个**纯函数**：

```java
private List<Role> route(Role from, MessageKind kind)
```

它不读字段、不写日志、不碰外部系统（除了读 `assignee` 这个字段）。
所以你可以这样测：

```java
assertEquals(List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER),
             hub.route(Role.WAREHOUSE, MessageKind.STOCK_SHORTAGE));
```

不需要真的 new 一个仓库，不需要 mock 一个财务系统，甚至不需要构造任何参与者。
**规则表和它的执行环境是解耦的。**

对比网状结构：`Warehouse.reportShortage()` 里写了三个 `xxx.receive(...)`，
要测它就得有三个替身对象。而且你测的是"仓库的行为"，
不是"缺货该通知谁"这条规则本身。

**收益三：路由可以是运行期状态。**

这是最重要的一条，也是 §1.3 那张表里 T4 行说的东西。

```java
private Role assignee = Role.AGENT;

case QUESTION -> from == Role.CUSTOMER ? List.of(assignee) : List.of();
```

`assignee` 是一个字段。`escalate()` 把它改成 `Role.SUPERVISOR` 之后，
**同一个客户的同一句 `say(QUESTION, ...)`，收件人自动变成主管。**

客户的代码：

```java
customer.say(MessageKind.QUESTION, "到底什么时候能退？");
```

升级前和升级后**完全一样**。这就是"路由从编译期常量变成运行期状态"的实际含义。

### 3.3 中介者必须持有状态，不能写成工具类

这是很多实现会踩的坑，值得单独说。

有一种"看起来像中介者"的写法：

```java
public final class MediatorUtils {
    private MediatorUtils() {}

    public static void send(User from, String message) {
        // 静态方法，无状态
    }
}
```

**这不是中介者，这是一个静态工具类。** 它无法表达中介者最关键的三件事：

| 中介者必须有的状态 | 为什么必须有 |
|---|---|
| 谁参加了这张工单 | 路由表按角色找收件人，找不到就是"未加入" |
| 当前负责方是谁 | 客户提问要路由到"当前"负责方，这是会变的 |
| 这张工单是否已关闭 | 关闭之后不允许再发言，这是一个生命周期状态 |

`AfterSalesHub` 里就有这四个可变字段：

```java
private final List<Participant> participants = new ArrayList<>();
private final Map<Role, Participant> byRole = new EnumMap<>(Role.class);
private final List<String> failures = new ArrayList<>();
private Role assignee = Role.AGENT;
private int delivered;
private boolean closed;
```

**中介者的本质是"一个知道当前协同状态的实例"**，不是一个"能转发消息的函数"。

反过来说，如果一个场景确实不需要任何状态（纯粹的固定转发），
那它也不需要中介者 —— 一个 `switch` 或一个 `Map<Role, Handler>` 就够。

### 3.4 怎么判断该不该上中介者

把 §1.4 的五条"不需要"反过来，就是五个"该上"的信号：

| 信号 | 具体表现 | 本项目的对应 |
|---|---|---|
| 参与方 ≥ 4 | 两三个对象之间的依赖是结构，不是耦合 | 6 个角色 |
| 交互是网状的 | 不是 A→B→C 的线性链路 | 客户↔客服↔仓库↔财务 |
| 路由规则会变 | 今天"发给客服"，明天"发给主管" | `assignee` 可变 |
| 一条动作触发多个下游 | 一次升级要通知主管 + 客服 + 通知器 | `escalate()` |
| 规则需要一个可审计的归属地 | 出事故时要回答"这条消息谁该收到" | `route()` 是唯一副本 |

**五条信号里至少要占三条**，否则大概率是过度设计。

再看反面：如果只占"参与方 ≥ 4"这一条，但规则从不变化、永远是"全部通知"，
那你需要的是**观察者**。如果只是"A 要调 B、B 要调 C"，你需要的是**责任链**。

### 3.5 中介者不是消息队列

这是最常见的概念混淆，必须澄清。

| | 中介者（本项目） | 消息队列（Kafka / RabbitMQ） |
|---|---|---|
| 位置 | **进程内**的对象协作 | **进程间**的通信基础设施 |
| 解决的问题 | 对象之间的耦合 | 服务之间的解耦、削峰、异步 |
| 路由规则 | 业务规则，写在中枢里 | 主题/队列配置，写在 broker 里 |
| 是否有业务状态 | **有**（负责方、是否关闭） | 通常无（broker 不理解业务） |
| 是否跨网络 | 否 | 是 |
| 失败处理 | try-catch 隔离 + 记录 | 重试、死信队列、ACK |

**它们不冲突，可以叠加。** 一个真实系统里，`Notifier` 这个参与者背后可能就是一个
真正的消息队列生产者 —— 它在 `receive()` 里把消息投到 MQ，由 MQ 负责投递到短信网关。

但**中介者不能因为有 MQ 就省掉**：

- MQ 不知道"这张工单的负责方是客服还是主管"；
- MQ 不知道"缺货要同时给客服、财务、客户，而升级只给主管、客服、通知器"；
- MQ 不会因为"工单已关闭"就拒绝发送。

**路由的业务规则在中介者里，投递的可靠性在 MQ 里。** 两者的边界是
"这条消息该给谁" vs "这条消息怎么送到"。

---

## 4. 设计推导：十三个决定

这一节逐个回答"为什么这样设计，换一种实现会有什么区别"。
十三个决定，每一个都对应一个具体的岔路口。

### 4.1 决定一：场景选「售后工单」，不选「聊天室」

**为什么不把原项目的聊天室改好？**

因为聊天室这个场景**天然不需要中介者**。

聊天室的核心需求是"所有人看到所有人的话"，也就是广播。
广播这件事，最好的抽象是**观察者**（发布-订阅）：一个主题，多个订阅者，
主题不知道订阅者是谁，订阅者也不知道彼此。

如果硬把聊天室做成中介者，你会得到：

```java
// 一个"中介者"，但它的规则表只有一行
case CHAT -> List.of(所有人);
```

**规则表只有一行，等于没有规则。** 这时候中介者就是一层多余的间接调用 ——
它既没有表达任何业务知识，也没有降低任何复杂度。

原项目之所以看起来"能用"，正是因为聊天室的极端简单掩盖了模式的难点。
**一个 Demo 如果简单到所有设计都恰好成立，它就没有教学价值。**

**售后工单好在哪？** 它天然具备中介者需要的全部要素：

| 要素 | 售后工单里的表现 |
|---|---|
| 多方参与 | 客户、客服、仓库、财务、主管、通知器 |
| 路由有差异 | 7 种消息类型，收件人各不相同 |
| 路由有状态 | 升级后负责方从客服变成主管 |
| 有生命周期 | 工单会关闭，关闭后不能再发言 |
| 有外部依赖 | 通知器要发短信/站内信，会失败 |
| 有异常路径 | 升级、缺货、退款都是"非正常流程" |

而且它离生活很近 —— 任何人退过一次货，都能立刻理解"为什么退款要通知财务"。

**这个决定的教学意义：**

> 选 Demo 场景时，不要选"这个模式刚好成立"的场景，
> 要选"**没有这个模式会很痛**"的场景。
>
> 判断标准：如果去掉模式之后代码还是很好写，那这个 Demo 选错了。

### 4.2 决定二：角色用枚举，不用字符串，也不用 instanceof

`Role` 是六个枚举值：`CUSTOMER` / `AGENT` / `WAREHOUSE` / `FINANCE` /
`SUPERVISOR` / `NOTIFIER`。

**方案 A：用字符串。**

```java
String role = "客服";

if (role.equals("客服")) { ... }
```

三个问题：

1. 改文案就失效（"客服" → "客户服务"）；
2. 拼写错误编译器不管（`"客复"`）；
3. 无法穷尽检查 —— 加了新角色，所有 `if` 链条静默走到 `else`。

**方案 B：用具体类 + instanceof。**

```java
if (participant instanceof Agent) { ... }
else if (participant instanceof Warehouse) { ... }
```

三个问题：

1. 加一个角色，所有 instanceof 链条都要改，且不会报错；
2. "同一个角色多个实例"难以表达（虽然本项目做了限制，但这是另一个话题）；
3. 用 instanceof 做路由，等于让中枢依赖所有具体类 —— 和"参与者互相依赖"
   只有一步之遥。

**方案 C：枚举。** 三个好处：

1. **封闭集合**。`switch` 对枚举是穷尽的，加一个常量编译器会提醒你（§4.8）；
2. **可比较**。`from == Role.CUSTOMER` 是引用比较，零成本且不会错；
3. **可以带数据**。`Role.CUSTOMER.displayName()` 返回 `"客户"`，
   中文名只有一份，不会在六个文件里各写一遍。

```java
public enum Role {
    CUSTOMER("客户"),
    AGENT("客服"),
    WAREHOUSE("仓库"),
    FINANCE("财务"),
    SUPERVISOR("主管"),
    NOTIFIER("通知器");

    private final String displayName;

    Role(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }
}
```

**为什么 `displayName` 要放在枚举里，而不是散落在日志代码里？**

因为"新增一个角色"这个动作，应该只改一处。如果中文名散落在 20 个
`System.out.println` 里，加角色时你会漏掉几个，然后日志里出现 `null` 或英文常量名。

放在枚举里，`Role` 的构造器强制你提供名字 —— **漏不掉**。

**`NOTIFIER` 为什么也算"角色"？**

因为它虽然不是人，但它具备参与者的全部特征：有身份、会收消息、会做反应。
在中介者眼里，**"人"和"系统"没有区别，都只是"能收消息的一方"。**

这个设计带来一个实际收益：明天公司要换成钉钉机器人，只要写一个
`DingTalkNotifier implements Participant`，在 `join()` 时替换掉 `Notifier`，
中枢和另外五个同事一行都不用改。

### 4.3 决定三：消息类型用枚举，不用 String 前缀

`MessageKind` 是七个枚举值：`QUESTION` / `ANSWER` / `STOCK_SHORTAGE` /
`REFUND_CONFIRMED` / `NOTICE` / `ESCALATION` / `ARCHIVE`。

**为什么必须有这个东西？**

原项目的接口是 `sendMessage(String message, User sender)`。这里只有正文和发送者，
**"这条消息是什么性质"这个信息不存在。**

但路由恰恰依赖性质。看本项目的规则表：

```java
case QUESTION -> ...          // 客户提问 → 负责方
case ANSWER -> ...            // 客服/主管答复 → 客户
case STOCK_SHORTAGE -> ...    // 仓库缺货 → 三方
case REFUND_CONFIRMED -> ...  // 财务退款 → 两方
case NOTICE -> ...            // 任意提示 → 客户
```

**五种性质，五条不同的路由。** 没有性质，这五条就无法区分。

**如果不加枚举，唯一的技术路线是解析字符串：**

```java
if (message.startsWith("【缺货】")) {
    targets = List.of(AGENT, FINANCE, CUSTOMER);
} else if (message.startsWith("【退款】")) {
    targets = List.of(CUSTOMER, AGENT);
}
```

这段代码的寿命取决于**运营会不会改写文案**。

真实场景：审核觉得"缺货"这个词太刺激，改成"暂无库存"。于是
`startsWith("【缺货】")` 永远为 false，缺货上报**静默地**不再通知财务 ——
退款流程从此少了冻结发票这一步，**而且没有任何报错**。

这就是迭代器模式文档 [§2.9](../iterator/README.md#29-问题七类型只能靠名字猜)
里那个坑的同一副面孔：**类型信息只能靠名字猜。**

**用枚举之后，这个错误在编译期就被挡住了：**

```java
case STOCK_SHORTAGE -> ...     // 改名？改的是枚举常量，编译器全项目追踪
```

**为什么 `ESCALATION` 和 `ARCHIVE` 也在枚举里？**

因为它们同样"要发给某些人"。中介者的职责是路由，不是限制"只能由 `say()` 触发"。

区别只在**入口**：

| 消息类型 | 谁能产生 |
|---|---|
| `QUESTION` / `ANSWER` / `STOCK_SHORTAGE` / `REFUND_CONFIRMED` / `NOTICE` | 参与者通过 `say(kind, text)` |
| `ESCALATION` | 只能由 `hub.escalate(from, reason)` 产生 |
| `ARCHIVE` | 只能由 `hub.close(from)` 产生 |

这个约束写在哪？写在 `route()` 里：

```java
case ESCALATION, ARCHIVE -> List.of();
```

**`route()` 对这两种类型返回空列表。** 如果某个参与者试图 `say(ESCALATION, ...)`，
`targets.isEmpty()` 为真，中枢会打印"路由未命中"并丢弃。

这是一种"**用数据表达限制**"的写法：不需要在 `say()` 里写
`if (kind == ESCALATION) throw ...`，只需要让规则表说"这条边不存在"。

### 4.4 决定四：消息用 record，不用可变对象

```java
public record Message(Role from, MessageKind kind, String text) {

    public Message {
        Objects.requireNonNull(from, "发送方角色不能为 null");
        Objects.requireNonNull(kind, "消息类型不能为 null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("消息正文不能为空白");
        }
        text = text.strip();
    }

    public String describe() {
        return from.displayName() + " 的" + kind.label() + "：" + text;
    }
}
```

**为什么要把三个字段包成一个对象？**

因为中枢的入参本来可以是三个散装参数，而三个参数里有两个是"枚举 + 字符串"的组合，
编译器很难帮你发现问题：

```java
// 写法 A：散装
void say(Participant from, MessageKind kind, String text);
void receive(Role from, MessageKind kind, String text);

// 写法 B：封装
void say(Participant from, MessageKind kind, String text);
void receive(Message message);
```

写法 A 的问题：`say` 和 `receive` 的参数列表**不对齐**（一个是 `Participant`，
一个是 `Role`）。只要有一个人写错了顺序，`from` 和 `kind` 就会互换 ——
而 `Participant` 和 `MessageKind` 都是引用类型，编译器不会报错（除非强转）。

封装成 `Message` 之后：

1. **中转不丢字段**。中枢收到的是 `Message`，转发的也是同一个 `Message`，不需拆开再拼；
2. **校验只写一次**。正文非空、类型非空都在紧凑构造器里检查，
   不会出现"某一条投递路径忘了校验"；
3. **以后加字段不用改签名**。比如加 `timestamp` 或 `messageId`，
   只改这个 record，投递链路上所有方法都不用动。

**为什么是 record 而不是普通类？**

消息是典型的**值对象**：创建之后不该被修改，两个字段相同的消息就是同一条消息。

`record` 自动给了 `equals` / `hashCode` / `toString`，而且所有字段隐式 `final` ——
这正是"消息一旦发出就不该被改写"的语义。

如果写成普通类：

```java
public class Message {
    private Role from;
    private MessageKind kind;
    private String text;

    public void setText(String text) { this.text = text; }   // ← 危险
}
```

那么"财务正在处理这条消息时，有人改写了它的正文"就成了可能。
这种 bug 极难排查，因为消息在传递过程中被悄悄改了。

**为什么 `from` 记的是角色，而不是参与者对象？**

```java
public record Message(Role from, ...)     // 角色，不是 Participant
```

两个理由：

1. **不延长生命周期**。如果 `Message` 持有 `Participant` 引用，那么一条被存档的
   消息会让那个参与者对象无法被回收；
2. **防止自我回环**。如果 `Message` 里有发送者对象，转发时很容易写出
   "把发送者自己又发回去"的逻辑。存角色就没有这个问题 —— 角色只是一个枚举值。

而 `from` 在路由里本来也只需要角色：

```java
private List<Role> route(Role from, MessageKind kind)
```

**注意 `Message.from` 和 `Participant.role()` 是同一个值。**
`say()` 里是这么构造的：

```java
Message message = new Message(from.role(), kind, text);
```

这里用 `from.role()` 而不是让调用方传角色，是因为**发送方不能冒充别人**。
如果签名是 `say(Role from, ...)`，那任何人都能说"我是财务" —— 这是权限问题。

### 4.5 决定五：`Participant` 用接口 + 默认方法，不用抽象类

这是本项目最值得讲的一处设计。

原项目用的是抽象类：

```java
public abstract class User {
    protected String name;
    protected ChatRoomMediator mediator;

    public abstract void receive(String message);
    public abstract void send(String message);
}
```

每个子类都要自己实现 `send()`：

```java
public class ChatUser extends User {
    @Override
    public void send(String message) {
        System.out.println(this.name + " 发送消息：" + message);
        mediator.sendMessage(message, this);      // ← 每个子类都得抄一遍
    }
}
```

如果原项目有 5 种用户类型（管理员、游客、机器人……），`send()` 就要抄 5 遍，
而且**任何一次改动都要改 5 个地方**。

本项目的做法：

```java
public interface Participant {

    Role role();
    String name();
    void receive(Message message);
    CollaborationHub hub();

    default void say(MessageKind kind, String text) {
        hub().say(this, kind, text);              // ← 只有这一句
    }

    default void escalate(String reason) {
        hub().escalate(this, reason);
    }

    default void closeTicket() {
        hub().close(this);
    }
}
```

**四个抽象方法 + 三个默认方法。** 六个参与者类，没有一个重写 `say` / `escalate` /
`closeTicket`，但它们全都有这三个能力。

**为什么不写成抽象类？**

抽象类的写法会是：

```java
public abstract class AbstractParticipant {
    private final String name;
    private final CollaborationHub hub;

    protected AbstractParticipant(String name, CollaborationHub hub) {
        this.name = name;
        this.hub = hub;
    }

    public final String name() { return name; }
    public final CollaborationHub hub() { return hub; }

    public final void say(MessageKind kind, String text) { hub.say(this, kind, text); }
    public final void escalate(String reason) { hub.escalate(this, reason); }
    public final void closeTicket() { hub.close(this); }

    public abstract Role role();
    public abstract void receive(Message message);
}
```

行数差不多，但有三个实质区别：

**区别一：继承名额。**

Java 一个类只能继承一个父类。如果 `Agent` 继承了 `AbstractParticipant`，
它就再也继承不了别的业务基类。

本项目的 `Agent` 是 `public final class Agent implements Participant` ——
**它没有占用继承名额**。明天要让它继承一个 `AuditableEntity`（审计基类），
只需要 `implements Participant` 保持不变即可。

**区别二：语义。**

抽象类表达的是"**是一种**"（is-a）。但"参与者"不是一种实体类型，
它是一种**能力**：能被中枢投递、能向中枢发言。

一个类"是一个参与者"这句话很奇怪；一个类"具备参与协同的能力"才自然。
**接口表达"具备能力"，抽象类表达"属于某类"。**

**区别三：默认方法让"转发"变成了框架能力。**

这是最关键的一点。看 `say()` 的实现：

```java
default void say(MessageKind kind, String text) {
    hub().say(this, kind, text);
}
```

它做了两件事：

1. 把 `this` 交给中枢（**发送方的身份由框架保证，不可能造假**）；
2. 把 `kind` 和 `text` 原样传下去（**参与者无法指定收件人**）。

**这两条约束是写在接口里的，不是写在文档里的。** 任何一个实现类，
哪怕它想"偷偷指定收件人"，也没有语法可用 —— 因为 `say()` 只有两个参数，
都不是 `Role` 或 `Participant`。

对比抽象类的写法：如果用 `protected final CollaborationHub hub` 字段，
子类至少能看到 `hub` 这个对象，然后调用 `hub.say(...)` 或别的什么。
而接口的 `hub()` 是 `public` 的，看起来更暴露 —— 但关键在于
**`CollaborationHub` 接口里根本没有"发给特定角色"的方法**（见 §4.7）。

所以这两层是配合的：

- `Participant` 不提供"指定收件人"的语法；
- `CollaborationHub` 不提供"指定收件人"的方法。

**两层都没有，才是真的没有。**

**为什么 `receive()` 是抽象的？**

因为它是参与者**唯一需要自己实现的行为**：这条消息到我这里，我该做什么。

- 客户：确认（"好，那我等结果"）；
- 客服：接单 / 收到缺货 / 抄送已阅 / 从待办移除；
- 仓库：释放库存锁定；
- 财务：冻结发票 / 归档凭证；
- 主管：接手 / 答复 / 确认关闭；
- 通知器：发短信 / 发站内信 / 拒绝外发。

六个完全不同的行为，没法默认实现，也不该默认实现。

**这个边界的意义：**

> `receive()` 只描述"我看到这条消息做了什么"，
> 不描述"这条消息该转给谁" —— 后者是中枢的职责。

这个边界一旦划清，新增一个角色就只是新增一个 `receive()` 实现，
不会牵动任何其他文件。

### 4.6 决定六：`hub()` 只读，构造时绑定

```java
public interface Participant {
    CollaborationHub hub();      // 只有 getter
}
```

每个实现类：

```java
public final class Customer implements Participant {
    private final String name;
    private final CollaborationHub hub;

    public Customer(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("客户称呼不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public CollaborationHub hub() {
        return hub;
    }
}
```

**三个动作，堵死三个后门：**

| 动作 | 堵死什么 |
|---|---|
| `private final` 字段 | 外部改不了，子类也改不了（`final`） |
| 构造时 `requireNonNull` | 不会出现"参与者没有中枢"的半成品对象 |
| 接口只有 `hub()` 没有 `setHub()` | 调用方没有"换中枢"的语法 |

**对照原项目**（探针 6）：`protected ChatRoomMediator mediator` 可写，
子类可以在任意时刻换掉。**同一句话可以走两个不同的房间，没有任何日志提示。**

**这里有争议：`hub()` 是 `public` 的，参与者不就能拿到中枢了吗？**

能拿到，但拿到之后能做的最多就是：

```java
hub.join(other);                    // 把别人加进来（可能抛 IllegalArgumentException）
hub.say(this, kind, text);          // 自己发言
hub.escalate(this, reason);         // 自己升级
hub.close(this);                    // 自己关闭
hub.roster();                       // 看名单
hub.assignee();                     // 看负责方
hub.deliveryFailures();             // 看失败记录
```

**没有任何一个方法能"以别人的身份发言"或"指定收件人"** ——
因为所有动作的第一个参数都是 `Participant`，而 `Participant` 是调用方传进来的。
如果客户对象调用 `hub.say(agent, ...)`，那它得先拿到 `agent` 的引用 ——
而客户的代码里根本没有这个引用（构造函数只接收 `CollaborationHub`）。

**这就是"接口窄"的威力：不是靠访问修饰符挡住，而是靠"没有这个方法"。**

如果想更严格，可以把 `hub()` 改成包私有（package-private）。但那样
`say()` 这个默认方法也必须是包私有的 —— 而它得是 `public` 才能被调用。
所以 `hub()` 必须是 `public`。这是 Java 接口的一个现实约束，可以接受。

### 4.7 决定七：中枢接口里一条路由规则都不放

`CollaborationHub` 有 7 个方法：

```java
void join(Participant participant);
void say(Participant from, MessageKind kind, String text);
void escalate(Participant from, String reason);
void close(Participant from);
List<Participant> roster();
Role assignee();
List<String> deliveryFailures();
```

前四个是动作，后三个是只读查询。**没有任何一个是"发给某个特定角色"。**

**这是最容易写错的地方。** 很多人会把中介者接口写成：

```java
void sendToAgent(String text);
void sendToFinance(String text);
void sendToCustomer(String text);
void broadcast(String text);
```

看起来"很方便"，实际上毁掉了整个模式。两个原因：

**原因一：把"谁该收"的决定权交给了参与者。**

如果接口有 `sendToFinance(text)`，那客户就能调用它。于是：

```java
// 客户类里
hub.sendToFinance("我要退款");     // ← 客户现在知道公司里有"财务"
```

客户又开始依赖具体角色了。耦合并没有消失，只是从"依赖对象"
变成了"依赖角色"——**而角色列表比对象列表更不稳定**（公司会重组部门，
但不会重组"客户"这个概念）。

更糟的是，"什么情况下该发财务"这个业务知识，会散落在每个调用点。

**原因二：接口会随业务规则一起膨胀。**

每加一条协同规则就要加一个方法。半年后：

```java
void sendToAgent(...);
void sendToFinance(...);
void sendToWarehouse(...);
void sendToSupervisor(...);
void sendToFinanceAndAgent(...);          // ← 开始出现组合
void sendToAgentFinanceCustomer(...);     // ← 越来越离谱
```

**接口应该稳定，规则应该集中一处。** 规则是最常变的东西，
把它放进接口，等于让最稳定的东西跟着最不稳定的东西一起变。

**所以本项目的接口只留四个"动作"，都是"表达意图"而不是"指定收件人"：**

| 方法 | 表达什么意图 | 收件人由谁决定 |
|---|---|---|
| `join(p)` | 有人要参加 | 中枢（检查角色唯一、工单未关闭） |
| `say(from, kind, text)` | 我要发一条某性质的消息 | `route()` |
| `escalate(from, reason)` | 我要升级 | `escalate()` 内部固定为"主管 + 客服 + 通知器" |
| `close(from)` | 我要关闭 | `close()` 内部固定为"广播给其余所有人" |

**注意 `escalate` 和 `close` 是"特例方法"。**

为什么不把它们也做成 `say(ESCALATION, ...)` / `say(ARCHIVE, ...)`？

因为它们的**副作用不一样**：

- `say()` 只是投递消息，不改状态；
- `escalate()` 要**改 `assignee`**（这是状态变更）；
- `close()` 要**置 `closed = true`**（这是生命周期变更）。

如果把它们塞进 `say()`，`say()` 就会变成"有时候改状态、有时候不改"的方法 ——
这是典型的**方法名与行为不符**。

**一个方法只做一件事，状态变更要有明确的方法名。**

**为什么把只读查询也放进接口？**

`roster()` / `assignee()` / `deliveryFailures()` 是给演示与运维看的。
它们不改变状态，放在接口里让"中枢现状"成为契约的一部分，
而不是靠反射或强转去偷看实现类。

这也是一个取舍：接口宽一点，但**宽在只读上**是安全的；
宽在写上（尤其是"指定收件人"这种写上）才是危险的。

---

### 4.8 决定八：路由表用 `switch` 表达式，不留 `default`

`route()` 的完整实现：

```java
private List<Role> route(Role from, MessageKind kind) {
    return switch (kind) {
        case QUESTION -> from == Role.CUSTOMER
                ? List.of(assignee)
                : List.of();
        case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
                ? List.of(Role.CUSTOMER)
                : List.of();
        case STOCK_SHORTAGE -> from == Role.WAREHOUSE
                ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
                : List.of();
        case REFUND_CONFIRMED -> from == Role.FINANCE
                ? List.of(Role.CUSTOMER, Role.AGENT)
                : List.of();
        case NOTICE -> List.of(Role.CUSTOMER);
        case ESCALATION, ARCHIVE -> List.of();
    };
}
```

**这是 `switch` 表达式（switch expression），不是 `switch` 语句（statement）。**
`return switch (...) { ... };` —— 整个 `switch` 是一个产生值的表达式。

这个区别决定了穷尽性要求，也决定了"忘了加 case"会不会被发现。

**真实编译器证据（JDK 25，临时对照组目录 `tmp_med_switch`，验完即删）：**

对照组一 —— `switch` **表达式**不穷尽：

```java
public class ExprFail {
    enum Kind { A, B, C }
    static int f(Kind k) {
        return switch (k) {
            case A -> 1;
            case B -> 2;
        };
    }
}
```

编译真实输出：

```text
=== 1) switch 表达式不穷尽（期望编译失败） ===
E:\codeFile\codeFather\design_patterns_pro\tmp_med_switch\ExprFail.java:4: 错误: switch 表达式不包含所有可能的输入值
        return switch (k) {
               ^
1 个错误
exit = 1
```

对照组二 —— `switch` **语句**不穷尽：

```java
public class StmtOk {
    enum Kind { A, B, C }
    static void g(Kind k) {
        switch (k) {
            case A -> System.out.println("A");
            case B -> System.out.println("B");
        }
    }
    public static void main(String[] a) {
        g(Kind.C);
        System.out.println("语句不穷尽：编译通过，C 什么都没打印");
    }
}
```

编译 + 运行真实输出：

```text
=== 2) switch 语句不穷尽（期望编译通过） ===
（无输出）
exit = 0

=== 3) 运行对照组 ===
语句不穷尽：编译通过，C 什么都没打印
exit = 0
```

**三行结论：**

| 写法 | 加一个枚举常量后 | 后果 |
|---|---|---|
| `switch` **表达式**（`return switch (...) {...}`） | **编译失败** | 逼你补 `case`，不可能忘 |
| `switch` **语句**（`switch (...) {...}`） | 编译通过 | **运行时静默什么都不做** |
| 有 `default` 的 `switch` 表达式 | 编译通过 | 新常量悄悄落进 `default` |

**`route()` 用的是第一种。** 这是刻意的 —— 它意味着：

> 明天产品说"再加一种消息类型：`PARTIAL_REFUND`（部分退款）"，
> 你只改 `MessageKind` 枚举，然后 `javac` 立刻在 `route()` 报错：
> `switch 表达式不包含所有可能的输入值`。
>
> **编译器逼你回答"这种消息该给谁"。** 你不可能忘，也不可能拖。

如果 `route()` 用了 `if-else` 链：

```java
if (kind == QUESTION) { ... }
else if (kind == ANSWER) { ... }
// ... 新增的 PARTIAL_REFUND 掉进这里
else { return List.of(); }              // ← 空列表 = 没人收到
```

新增的消息类型会**静默地没人收到**。而且不会有人发现，直到客户投诉
"我申请部分退款，一直没人理我"。

**那为什么 `ESCALATION, ARCHIVE -> List.of();` 要显式写出来？**

因为它俩是"只能由 `escalate()` / `close()` 产生"的类型。
写一个空的 `List.of()`，明确表达"**这条边不存在**"。

这比写 `default -> List.of()` 好：

- 显式列出 → 加新类型时编译器仍然会提醒（因为不是 `default`）；
- 写 `default` → 加新类型时编译器闭嘴，新类型静默走"没人收到"。

**"什么都不做"也要显式写出来。** 这是穷尽性思维的核心。

### 4.9 决定九：用 `EnumMap` 保证同角色唯一

```java
private final Map<Role, Participant> byRole = new EnumMap<>(Role.class);

@Override
public void join(Participant participant) {
    Objects.requireNonNull(participant, "参与者不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已关闭，不能加入新参与者");
    }
    Role role = participant.role();
    Participant existing = byRole.get(role);
    if (existing != null) {
        throw new IllegalArgumentException(
                "角色「" + role.displayName() + "」已经由「" + existing.name()
                        + "」担任，不能再加入「" + participant.name() + "」");
    }
    byRole.put(role, participant);
    participants.add(participant);
}
```

`join()` 做了五件事，顺序很重要：

1. **`requireNonNull`** —— 堵死探针 3 的 `null` 断链；
2. **检查 `closed`** —— 已关闭的工单不能加人（生命周期约束）；
3. **按角色查重** —— 堵死探针 2 的重复注册；
4. **`byRole.put`** —— 按角色索引，供路由查表；
5. **`participants.add`** —— 按加入顺序保存，供广播和 `roster()` 用。

**为什么用 `EnumMap` 而不是 `HashMap`？**

三个理由：

1. **枚举的 `hashCode` 是身份哈希，但 `EnumMap` 连哈希都不用算** ——
   它内部用一个 `Object[]` 数组，下标就是 `ordinal()`。查表是 O(1) 的数组访问。
2. **内存更小**。`HashMap` 每个 entry 是一个 `Node` 对象；`EnumMap` 只有一个数组。
3. **意图明确**。看到 `EnumMap<Role, ...>` 就知道"键一定是 `Role`，而且只有 6 个"。

**为什么"同角色唯一"要报错，而不是静默覆盖？**

如果允许两个客服加入，`byRole.put(AGENT, ...)` 会覆盖前一个：

```java
byRole.put(AGENT, agentA);      // 第一个客服
byRole.put(AGENT, agentB);      // 第二个客服，覆盖了 agentA
participants.add(agentA);       // agentA 还在名单里
participants.add(agentB);
```

于是 `agentA` 出现在 `participants` 名单里（`close()` 广播时会收到），
但**按角色路由的消息永远收不到**（因为 `byRole.get(AGENT)` 返回 `agentB`）。

**这是最坏的一种失败：部分功能正常，部分功能静默失效。**

报错虽然会让调用方不高兴，但至少它知道出了问题。
**静默失效比报错可怕得多** —— 报错会在第一次调用时暴露，静默失效可能潜伏半年。

**为什么"同角色两个实例"用"报错"来表达，而不是"队列"？**

如果业务上真的需要"两个客服同时负责一张工单"，正确的表达方式是：

- 加一个 `Role.AGENT_BACKUP`（备班客服），然后在路由表里明确"提问发给谁"；
- 或者引入"转派"动作（`reassign(from, to)`），显式改 `byRole` 的内容。

**用 `join` 两次来表达"两个人负责"是错的**，因为：
- 它让路由表的语义变得含糊（提问到底给哪一个？）；
- 它让"谁该处理"变成一个隐式的"最后加入的人"，这个规则没人写得出来。

`join` 的语义是"这个角色由我担任"，不是"多了个人"。**一个角色只有一个担任者**，
这是本项目的建模前提。

### 4.10 决定十：`deliver()` 里对每个收件方独立 try-catch

```java
private void deliver(Message message, List<Role> targets) {
    System.out.println("      路由 → " + describeTargets(targets));

    for (Role role : targets) {
        Participant target = byRole.get(role);
        if (target == null) {
            System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
            continue;
        }
        try {
            target.receive(message);
            delivered++;
        } catch (RuntimeException e) {
            String record = target.name() + " 投递失败：" + e.getClass().getSimpleName()
                    + "：" + e.getMessage();
            failures.add(record);
            System.out.println("      ! 已隔离：" + record);
        }
    }
}
```

四层处理，每一层都有明确的理由：

**第一层：`target == null` 不报错，只跳过。**

```java
if (target == null) {
    System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
    continue;
}
```

为什么"未加入"不是错误？

因为**路由表和实际参会是两件事**。`route()` 说"缺货该给客服、财务、客户"，
这是**规则**；但某张工单可能压根没有财务参与（比如金额很小，不需要财务审核）。

如果"未加入"就抛异常，那么 `route()` 就必须知道"哪些角色肯定在"——
那是把名单信息塞进规则表，耦合。

所以正确的处理是：**规则说"该给谁"，实际投递时"谁在就给谁"。**
**这正好覆盖了探针 3 的场景**：原项目里一个 `null` 会炸断整个广播，
而这里"这个人不在"只是一句跳过日志，不影响其他人。

**第二层：`try-catch RuntimeException` 而非 `Exception` 或 `Throwable`。**

- 不用 `Exception`：`receive()` 不声明受检异常，编译器不会让受检异常逃出来；
- 不用 `Throwable`：`Error`（`OutOfMemoryError`、`StackOverflowError`）不应该被业务代码吞掉 ——
  那是 JVM 层面的问题，吞了只会让事情更糟。

`RuntimeException` 正好覆盖"业务代码里可能抛的所有异常"：
`NullPointerException`、`IllegalStateException`、`IllegalArgumentException`、
以及任何自定义的运行时异常。

**第三层：失败进 `failures`，不抛给调用方。**

```java
failures.add(record);
```

为什么？见 §3.1 的分析：**"通知失败"和"业务失败"是两件事。**

仓库上报缺货成功了，只是财务的收件箱坏了 —— 这时候让整个上报回滚是错的。
仓库应该继续正常工作，财务的失败进 `deliveryFailures()` 被监控看到。

**第四层：`delivered++` 只在成功后执行。**

```java
target.receive(message);
delivered++;
```

顺序不能反。如果 `delivered++` 写在 `receive()` 之前，
那么 `receive()` 抛异常时它已经加过了 —— 计数就不准了。

**这个计数器的作用是"证明隔离真的起作用了"**。Demo 6 里：

```text
  已关闭 = true，成功投递 6 次
```

如果隔离没起作用（比如第一个异常就中断了循环），这个数字会明显偏小。

**`record` 的格式设计：**

```java
target.name() + " 投递失败：" + e.getClass().getSimpleName() + "：" + e.getMessage()
```

三段式：**谁、什么类型的错、错在哪里**。

- 用 `getSimpleName()` 而不是 `getClass().getName()`：日志里 `IllegalStateException`
  比 `java.lang.IllegalStateException` 好读；
- 用 `e.getMessage()` 而不是堆栈：这里要的是"一眼看懂"，堆栈留给日志框架。

Demo 6 的真实输出：

```text
      ! 已隔离：短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
```

一句话说清了"哪里坏了、坏成什么样"。

### 4.11 决定十一：升级改的是 `assignee`，不是改某个人的字段

```java
@Override
public void escalate(Participant from, String reason) {
    Objects.requireNonNull(from, "发起方不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已关闭，不能再升级");
    }
    Message message = new Message(from.role(), MessageKind.ESCALATION, reason);

    System.out.println("  [" + label(from) + "] 申请升级：" + message.text());
    System.out.println("      负责方：" + assignee.displayName() + " → " + Role.SUPERVISOR.displayName());
    assignee = Role.SUPERVISOR;
    deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));
}
```

三行核心逻辑，顺序也是刻意的：

```java
assignee = Role.SUPERVISOR;                                          // 1. 改状态
deliver(message, List.of(SUPERVISOR, AGENT, NOTIFIER));              // 2. 投递
```

**为什么先改状态，再投递？**

因为投递过程中，某个参与者的 `receive()` 可能触发新的动作。
如果 `assignee` 还没改，新动作会走旧路由 —— 状态和消息就不一致了。

反过来，如果"改状态"在"投递"之后，那么投递期间同一条工单上的新提问
仍然会去找客服，而升级消息已经发给主管了。**主管会看到一条他不知道前因的升级。**

先改状态，意味着**从这一刻起，所有新路由都用新状态** —— 这是"事实先于通知"。

**为什么升级要改 `assignee`？**

因为升级的意义就是"这张单子以后归主管管"。

如果只发一条通知而不改负责人：

```java
// 错误做法：只通知，不改状态
deliver(message, List.of(SUPERVISOR, AGENT, NOTIFIER));
// assignee 还是 AGENT
```

那么客户下一次提问，`route()` 里 `case QUESTION -> List.of(assignee)` 仍然返回客服。
**主管只是"围观"了一下升级事件，实际上什么都没接管。**

这是很多工单系统里真实存在的 bug：升级按钮点了，通知也发了，但用户发现
"我说的话还是那个不管我的人在看"。

**演示这一点的是 Demo 4。** 真实输出：

```text
[Demo 4：升级之后，同一句话换了一个收件人]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  当前负责方：客服
  [客户-李女士] 申请升级：等了 3 天没人处理，要求升级
      负责方：客服 → 主管
      路由 → 主管-张主管、客服-小王、通知器-站内信网关
        [张主管] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [张主管] 主管：接手，先翻一遍上下文再回客户。
        [小王] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [小王] 客服：抄送已阅，这单不再由我跟。
        [站内信网关] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [站内信网关] 外发短信给主管手机：「等了 3 天没人处理，要求升级」
  当前负责方：主管
  [客户-李女士] 提问：到底什么时候能退？
      路由 → 主管-张主管
        [张主管] 收到 客户 的提问：到底什么时候能退？
        [张主管] 主管：客户又追问了，直接答复。
  客户代码一个字没改，收件人从「客服」变成了「主管」
```

注意最后那句：**客户代码一个字没改。**

客户在 Demo 4 里调用了两次 `say(QUESTION, ...)`，两次的代码完全一样：

```java
team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
// ... 升级 ...
team.customer().say(MessageKind.QUESTION, "到底什么时候能退？");
```

第一次路由到客服，第二次路由到主管。**路由变了，但发送方的代码没变** ——
这就是"路由是运行期状态"的实际价值。

**为什么升级还要发给 `AGENT`？**

因为客服要停止跟进。看 `Agent.receive()` 里的分支：

```java
case ESCALATION -> System.out.println("        [" + name + "] 客服：抄送已阅，这单不再由我跟。");
```

如果不通知客服，他可能还在回复客户，造成"两个人同时回复，说法不一致"。

**为什么升级还要发给 `NOTIFIER`？**

因为"系统里的升级"和"主管真的知道"是两件事。

主管可能半小时才看一次后台。如果升级只是在他的工单列表里多了一条，
那客户"等了三天"的诉求实际上又晚了半小时。

所以升级事件要同时推一条短信：

```java
case ESCALATION -> System.out.println("        [" + name + "] 外发短信给主管手机：「"
        + message.text() + "」");
```

**这就是中介者存在的意义**：一条业务动作需要触发几个不同的下游，
这个知识只该写在一处。三行代码写清了"升级要通知三家公司"，而且只有一份。

### 4.12 决定十二：`close()` 是唯一一处真正的广播

```java
@Override
public void close(Participant from) {
    Objects.requireNonNull(from, "发起方不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已经关闭过了");
    }
    Message message = new Message(from.role(), MessageKind.ARCHIVE,
            "工单 " + ticketId + " 已由 " + from.role().displayName() + " 关闭");

    System.out.println("  [" + label(from) + "] 申请关闭工单 " + ticketId);

    List<Role> targets = new ArrayList<>();
    for (Participant participant : participants) {
        if (participant != from) {
            targets.add(participant.role());
        }
    }
    deliver(message, targets);
    closed = true;
    System.out.println("  工单 " + ticketId + " 状态：已关闭");
}
```

**这是全项目唯一一处 `for (Participant participant : participants)`。**

其他地方的路由都是"查表得到几个角色"，只有这里是"遍历所有参会的角色"。

**为什么关闭可以广播，而其他消息不行？**

因为关闭有**明确的业务理由**：工单结束了，所有人的待办里都该把它划掉。

- 客户：会话结束，可以评价；
- 客服：从我的待办里移除；
- 仓库：释放库存锁定；
- 财务：归档退款凭证；
- 主管：确认关闭；
- 通知网关：给客户发一条"服务已完成"的站内信。

**六个人，六件不同的事，但触发点是同一个事件。** 这时候广播是正确的。

对比"客户提问"如果广播：仓库、财务、主管、通知网关都收到，四家都无事可做，
只有一家该处理。**广播的代价是"每个收件人都得判断这条跟我有没有关系"**，
所以它必须有"所有人都有事做"作为前提。

> **一条可以带走的原则**
>
> 广播的前提是"**每个人都需要知道**"。
> 如果不是，那就是路由 —— 应该用规则表，不该用广播。

**为什么 `close()` 里要遍历 `participants` 而不是 `byRole`？**

两个集合的内容其实一样（`join` 里同时维护），但语义不同：

- `participants` 是 `List`，**有顺序** —— 广播顺序和加入顺序一致，
  演示输出可预期、可复现；
- `byRole` 是 `EnumMap`，顺序是**枚举声明顺序**（`CUSTOMER` 在前，`NOTIFIER` 在后），
  和加入顺序无关。

用 `participants` 遍历，输出顺序就是 `build()` 里的 join 顺序：

```java
hub.join(customer);      // 客户
hub.join(agent);         // 客服
hub.join(warehouse);     // 仓库
hub.join(finance);       // 财务
hub.join(supervisor);    // 主管
hub.join(notifier);      // 通知器
```

Demo 5 的真实输出的顺序正是：客户 → 仓库 → 财务 → 主管 → 通知器（跳过了发起的客服）。

**为什么 `closed = true` 在 `deliver()` 之后？**

因为归档消息本身要投递给所有人。如果先 `closed = true`，那么 `deliver()` 里
如果有任何逻辑检查 `closed`，就会拒绝投递。

更实际的原因：**关闭是一个"完成时"状态**。归档消息发完了，关闭才算完成。
如果投递中途出问题（虽然已隔离），至少状态还能反映出"关闭进行中"。

**为什么关闭之后再发言要抛异常？**

```java
if (closed) {
    throw new IllegalStateException(
            "工单 " + ticketId + " 已关闭，" + from.name() + " 不能再发言");
}
```

因为"已关闭"是一个**业务约束**，不是建议。

原项目没有生命周期概念 —— 任何时候都能给任何人发消息。这在聊天室里没问题
（聊天室不会"关闭"），但在工单系统里，"关了还能发消息"意味着：

- 数据可能被改到已归档的工单上；
- 通知可能发给已经离开会话的客户；
- 审计上说不清"这条消息是在工单存续期内产生的吗"。

抛 `IllegalStateException` 而不是静默忽略，是因为**静默忽略会让调用方以为成功了**。
Demo 5 里专门演示了这一点：

```text
  关闭之后再发言：
    抛出 IllegalStateException：工单 AS-2024-0917 已关闭，李女士 不能再发言
```

**注意这里用的是 `IllegalStateException` 而不是 `IllegalArgumentException`。**
区别是：

- `IllegalArgumentException` = "你给的数据不对"（比如 `null`、空白）；
- `IllegalStateException` = "你的数据没错，但当前状态不允许这个操作"。

关闭后发言属于后者：消息本身完全合法，只是**时机不对**。

### 4.13 决定十三：三种 `default` 策略并存

这是本项目最"反常识"的一处设计。六个参与者类，`receive()` 里的 `switch`
处理方式完全不同：

| 类 | 有 `default` 吗 | `default` 做什么 | 为什么 |
|---|---|---|---|
| `Customer` | **没有** | —— | 收到内部消息无所谓，安静即可 |
| `Agent` | **没有** | —— | 收到不该收的消息，静默忽略 |
| `Finance` | **没有** | —— | 同上 |
| `Warehouse` | **有** | **报警**：`!! 这是我不该收到的消息类型「...」，请检查中枢的路由表` | 仓库只该收归档，收到别的说明路由表写错了 |
| `Supervisor` | **有** | 静默记录：`（记录在案，无需动作）` | 主管什么都可能看到，噪声不必报警 |
| `Notifier` | **有** | **拒绝外发**：`不在外发白名单，丢弃（防止内部消息外泄）` | 外发渠道必须有安全边界 |

**先说技术前提：`switch` 语句不穷尽也能编译。**

这一点必须用编译器证据说清，否则"省略 default"看起来像遗漏。

**真实编译器证据（本节 §4.8 已引用过的同一组对照实验）：**

```text
=== 1) switch 表达式不穷尽（期望编译失败） ===
E:\codeFile\codeFather\design_patterns_pro\tmp_med_switch\ExprFail.java:4: 错误: switch 表达式不包含所有可能的输入值
        return switch (k) {
               ^
1 个错误
exit = 1

=== 2) switch 语句不穷尽（期望编译通过） ===
（无输出）
exit = 0
```

**`Customer.receive()` 里的那个 `switch` 是语句，不是表达式** —— 它的每个分支
都在执行 `System.out.println(...)`，整个 `switch` 不产生值。所以它可以不穷尽。

```java
@Override
public void receive(Message message) {
    System.out.println("        [" + name + "] 收到 " + message.describe());
    switch (message.kind()) {
        case ANSWER -> System.out.println("        [" + name + "] 客户：好，那我等结果。");
        case REFUND_CONFIRMED -> System.out.println("        [" + name + "] 客户：钱到账了，问题解决。");
        case ARCHIVE -> System.out.println("        [" + name + "] 客户：会话结束，给这次服务打 5 星。");
    }
}
```

客户只处理三种消息。收到 `STOCK_SHORTAGE` 时，`switch` 什么都不做 ——
但它已经打印了 `收到 ...` 那一行，所以日志里仍然有痕迹。

**这就是"省略 default"的真正语义：不是"不处理"，而是"处理方式是什么都不做"。**

**为什么客户选择"什么都不做"？**

因为客户不懂 `STOCK_SHORTAGE` / `ESCALATION` / `NOTICE` 是什么意思。

- `STOCK_SHORTAGE`（缺货上报）：客户看到"华东仓缺货 37 件"这种内部库存数据，
  只会困惑。这条消息确实会到客户这里（`route()` 里缺货发给三方），
  但客户的处理是"不额外做动作" —— 消息已经打印了，人能看到。
- `ESCALATION`（升级）：客户发起升级，但他不需要"处理"升级事件。
- `NOTICE`（提示）：客户是唯一收件人，但这个分支在 `Customer` 里没有 ——
  因为它没有额外动作要执行。

**为什么仓库选择"报警"？**

```java
default -> System.out.println("        [" + name + "] 仓库：!! 这是我不该收到的消息类型「"
        + message.kind().label() + "」，请检查中枢的路由表");
```

仓库在规则表里的收件箱**只有 `ARCHIVE`**：

```java
// route() 里所有分支的返回列表，只有 close() 的广播会包含 WAREHOUSE
// STOCK_SHORTAGE -> List.of(AGENT, FINANCE, CUSTOMER)  ← 不含 WAREHOUSE
```

所以仓库一旦收到非 `ARCHIVE` 的消息，**一定是路由表写错了**。

这个错误如果不喊出来，会以"仓库没有任何反应"的形式潜伏很久：
缺货上报本该通知仓库补货，结果仓库静默忽略了，货一直没补，客户一直等。

**"我不知道该怎么办"和"我知道我不该收到"是两种不同的处境。**
仓库属于后者，所以它必须喊。

**为什么通知器选择"拒绝外发"？**

```java
default -> System.out.println("        [" + name + "] 不在外发白名单，丢弃（防止内部消息外泄）");
```

通知器会把消息发到**公司外面**（客户手机、主管手机）。
所以它对"什么能外发"必须保守：

- `ESCALATION` → 外发短信给主管（他需要马上知道）；
- `ARCHIVE` → 外发站内信给客户（会话结束的通知）；
- 其余 → **一律不发**。

哪怕某天有人把内部消息误路由到通知器，它也不会把"财务正在冻结发票"
这种话发给客户。

**这是一条安全边界，不是一条业务规则。** 安全边界的原则是
"默认拒绝，白名单放行" —— 所以 `default` 必须是拒绝。

**为什么主管选择"静默记录"？**

```java
default -> System.out.println("        [" + name + "] 主管：（记录在案，无需动作）");
```

主管在规则表里的收件箱包含 `ESCALATION`（升级）、`QUESTION`（接手后的提问）、
`ARCHIVE`（归档）。但它也可能收到别的（比如开发时调试）。

主管的处理是"记一笔，但不报警" —— 因为主管这个角色天然要"什么都知道一点"，
收到意外消息不算异常。

**三种策略的对照表：**

| 场景 | 省略 `default` | `default` 报警 | `default` 安全拒绝 | `default` 记录 |
|---|---|---|---|---|
| 用在哪 | 客户 / 客服 / 财务 | 仓库 | 通知器 | 主管 |
| 判据 | "意外消息与我无关，不用管" | "意外消息说明上游有 bug" | "意外消息可能造成外部损害" | "意外消息是我该留意的，但不必惊动" |
| 代价 | 真出问题时没人知道 | 日志噪音（如果意外是常态） | 可能漏掉本该外发的消息 | 记录可能被忽略 |

> **一条可以带走的原则**
>
> "该不该写 `default`"没有统一答案。
> 判断标准是：**漏掉这个分支会造成什么后果？**
>
> - 后果无关紧要 → 省略（让代码更短，意图更清晰）；
> - 后果是上游 bug → 报警；
> - 后果是不可挽回的外部损害 → 安全拒绝；
> - 后果是"我该知道但不急" → 记录。

**这也是为什么 §4.8 的 `route()` 必须用 `switch` 表达式：**
`route()` 的漏分支后果是"这条消息没人收到" —— 属于"上游 bug"级别，
必须让编译器挡住。而 `receive()` 的漏分支后果是"某一方少做一个动作" ——
由各个参与者根据自己的处境决定要不要喊。

**同一个语法（`switch`），两种穷尽性要求，六个类三种 `default` 策略。**
这不是不一致，这是**每个类在为自己的失败模式负责**。

---

## 5. 代码解析：十三个文件

### 5.1 文件全景

十三个文件，**1623 个物理行、593 个代码行**（代码行指去掉空行和注释后的行数）。
按职责分四层：

| 层 | 文件 | 物理行 | 代码行 | 职责 |
|---|---|---|---|---|
| **值对象层** | `Role` | 71 | 16 | 六个角色，带中文名 |
| | `MessageKind` | 77 | 17 | 七种消息语义，带中文名 |
| | `Message` | 73 | 15 | 一条不可变消息（发送方 + 类型 + 正文） |
| **接口层** | `Participant` | 111 | 16 | 参与者能力：身份 + 收消息 + 三个默认动作 |
| | `CollaborationHub` | 106 | 11 | 中枢契约：四个动作 + 三个查询 |
| **实现层** | `AfterSalesHub` | 375 | 157 | 具体中介者：名单 + 路由表 + 投递 + 状态 |
| | `Customer` | 81 | 34 | 客户 |
| | `Agent` | 87 | 35 | 客服 |
| | `Warehouse` | 86 | 34 | 仓库 |
| | `Finance` | 82 | 33 | 财务 |
| | `Supervisor` | 83 | 35 | 主管 |
| | `Notifier` | 85 | 36 | 通知器 |
| **入口** | `Client` | 306 | 154 | 六段 Demo |
| | **合计** | **1623** | **593** | |

**依赖方向（只有这一个方向，没有环）：**

```text
                        ┌──────────┐
                        │  Client  │  只认识 AfterSalesHub 和六个参与者
                        └────┬─────┘
                             │ 创建、组装、调用
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
  ┌───────────┐      ┌──────────────┐      ┌────────────┐
  │ Customer  │ ...  │ Notifier     │      │AfterSalesHub│
  └─────┬─────┘      └──────┬───────┘      └──────┬─────┘
        │ implements        │ implements          │ implements
        ▼                   ▼                     ▼
  ┌──────────────────────────────────┐   ┌──────────────────┐
  │        Participant  (接口)        │   │CollaborationHub  │
  └──────────────────────────────────┘   └──────────────────┘
        │ uses                                     │ uses
        ├──────────────► Message ◄─────────────────┤
        │                  │                       │
        │                  ├──► Role               │
        │                  └──► MessageKind        │
        ▼                                          ▼
   （六个参与者都只依赖这五个类型）
```

**关键点：六个参与者类之间没有任何引用。**

你可以逐个验证：

```text
Customer.java  里不出现 Agent / Warehouse / Finance / Supervisor / Notifier
Agent.java     里不出现 Customer / Warehouse / Finance / Supervisor / Notifier
Warehouse.java 里不出现 Agent / Customer / Finance / Supervisor / Notifier
Finance.java   里不出现 Agent / Customer / Warehouse / Supervisor / Notifier
Supervisor.java里不出现 Agent / Customer / Warehouse / Finance / Notifier
Notifier.java  里不出现 Agent / Customer / Warehouse / Finance / Supervisor
```

唯一的交叉出现在 `Client.build()` 里 —— 那是**组装**，不是**协作**。
组装必须在某个地方发生，它属于"程序的配置"，不属于"参与者的逻辑"。

### 5.2 `Role.java`：六个角色，带中文名

完整代码（71 行）：

```java
public enum Role {

    CUSTOMER("客户"),
    AGENT("客服"),
    WAREHOUSE("仓库"),
    FINANCE("财务"),
    SUPERVISOR("主管"),
    NOTIFIER("通知器");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

（省略了 javadoc 与枚举常量的单行注释，完整代码见源文件。）

**它为什么存在？**

因为 `route()` 需要用"身份"作为路由的输入。没有 `Role`，路由只能靠：

- 字符串比较（`name.equals("客服")`）—— 改文案就失效；
- `instanceof`（`participant instanceof Agent`）—— 加角色要改所有判断。

三个好处，逐个说。

**好处一：`switch` 穷尽，加角色时编译器会提醒。**

`route()` 的 `switch` 只 switch `MessageKind`，不 switch `Role`。
但 `join()` 检查角色唯一性时用了 `byRole.get(role)` —— 如果 `Role` 是字符串，
这个 map 就是 `Map<String, Participant>`，拼写错误无法被发现。

**好处二：`Role` 可以携带数据（`displayName`）。**

```java
public String displayName() {
    return displayName;
}
```

如果中文名散落在各处：

```java
System.out.println("角色「客服」...");       // 这里硬编码
System.out.println("当前负责方：客服");      // 这里也硬编码
```

那么"客服"改成"客户服务专员"时，你要全项目搜索替换 ——
而搜索替换一定会漏掉拼接出来的字符串。

放在枚举里，改一处，全项目生效。

**好处三：`Role` 是路由表的"键"。**

```java
private final Map<Role, Participant> byRole = new EnumMap<>(Role.class);
```

`EnumMap` 要求键是枚举。如果 `Role` 是 `String`，就只能用 `HashMap`，
失去了"O(1) 数组访问"和"类型安全"。

**`NOTIFIER` 为什么在角色列表里？**

因为它虽然不是人，但它具备参与者的全部特征：

- 有身份（`Role.NOTIFIER`）；
- 会收消息（`receive(Message)`）；
- 会做反应（发短信 / 发站内信 / 拒绝外发）。

**在中介者眼里，"人"和"系统"没有区别。** 这个抽象的收益是：
接第三方渠道（钉钉、企业微信、Twilio）不需要改中枢，只需要写一个新的
`Participant` 实现，然后 `join` 进去。

**如果删掉 `Role` 会怎样？**

`route()` 的返回类型就只能是 `List<Participant>` 或 `List<String>`：

- `List<Participant>` → 路由表要持有参与者的引用，规则表和实例绑死，
  换一个人当客服就要改规则表；
- `List<String>` → 回到字符串地狱。

**`Role` 是"规则表只依赖身份，不依赖实例"这个设计的技术基础。**

**如果合并到 `Message` 里会怎样？**

有人可能想：`Message` 里已经有 `Role from` 了，还需要单独的 `Role` 文件吗？

需要。因为 `Role` 还用在三个别的地方：

- `Participant.role()` 的返回类型；
- `CollaborationHub.assignee()` 的返回类型；
- `AfterSalesHub.byRole` 的键类型。

**`Role` 是一个独立的领域概念，不是 `Message` 的内部结构。**

### 5.3 `MessageKind.java`：七种消息语义

完整代码（77 行）：

```java
public enum MessageKind {

    QUESTION("提问"),
    ANSWER("答复"),
    STOCK_SHORTAGE("缺货上报"),
    REFUND_CONFIRMED("退款确认"),
    NOTICE("提示"),
    ESCALATION("升级"),
    ARCHIVE("归档");

    private final String label;

    MessageKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
```

**为什么是七种？这七种是怎么选出来的？**

不是先想"要几个枚举值"，而是**从路由表倒推**：每一种消息类型，必须对应
一条（或一组）**不同的收件人规则**。如果两种消息的收件人完全一样，
那它们不该是两个枚举值。

验证一下：

| 类型 | 收件人 | 和其他类型的区别 |
|---|---|---|
| `QUESTION` | 当前负责方（动态） | 唯一依赖 `assignee` 的类型 |
| `ANSWER` | 客户 | 唯一"往回流"的类型 |
| `STOCK_SHORTAGE` | 客服 + 财务 + 客户 | 唯一三方协同 |
| `REFUND_CONFIRMED` | 客户 + 客服 | 两方，但和缺货的两方不同（缺货是客服+财务+客户） |
| `NOTICE` | 客户 | 和 `ANSWER` 收件人相同，但**发送方限制不同**（任意角色 vs 客服/主管） |
| `ESCALATION` | 主管 + 客服 + 通知器 | 只由 `escalate()` 产生 |
| `ARCHIVE` | 其余所有人（广播） | 只由 `close()` 产生 |

**`NOTICE` 和 `ANSWER` 收件人一样，为什么还要分开？**

因为**发送方不同**：

```java
case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
        ? List.of(Role.CUSTOMER)
        : List.of();
case NOTICE -> List.of(Role.CUSTOMER);
```

- `ANSWER` 只有客服和主管能发（客户不能"答复"自己）；
- `NOTICE` 任意角色都能发（仓库可以说"您的包裹已到驿站"）。

**收件人相同但发送权限不同，这就够构成两个类型了。** 因为规则表的一行
包含"谁发的 + 什么性质 → 给谁"，两个维度任何一个不同，就是不同的规则。

**为什么 `ESCALATION` / `ARCHIVE` 在枚举里，但 `route()` 对它们返回空列表？**

因为它们**不通过 `say()` 投递**，而是通过 `escalate()` / `close()`。
`route()` 返回空列表，是明确表达"这条边不存在"。

但它们的收件人逻辑还是要写 —— 写在 `escalate()` / `close()` 里：

```java
// escalate() 里
deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));

// close() 里
for (Participant participant : participants) {
    if (participant != from) {
        targets.add(participant.role());
    }
}
```

**为什么不把它们也从枚举里删掉？**

因为 `Message` 需要一个类型字段，而 `ESCALATION` / `ARCHIVE` 是真实的
消息类型 —— 参与者要靠它决定怎么反应：

```java
// Supervisor.receive()
case ESCALATION -> ...   // 接手，先翻上下文
case ARCHIVE -> ...      // 确认关闭，同意归档
```

如果枚举里没有这两个值，`Supervisor` 就分不清"升级消息"和"归档消息"，
只能用别的字段猜。

**`MessageKind` 是"消息的语义标签"，不是"投递路径"。** 两者是分开的：

- 语义：这条消息是升级；
- 路径：升级消息由 `escalate()` 投递，`say()` 不能发。

**为什么 `label` 和 `Role.displayName` 都叫"中文名"却不同名？**

这是一个刻意的命名细节：

- `Role.displayName()` —— 角色是"实体"，它的中文名是"显示名"；
- `MessageKind.label()` —— 消息类型是"标签"，它的中文名是"标签"。

如果两个都叫 `displayName` 或都叫 `label`，读代码时会混淆
"这是角色名还是消息类型名"。**同名不同义是维护成本的来源。**

**如果删掉 `MessageKind` 会怎样？**

`say()` 的签名会退化成：

```java
void say(Participant from, String text);          // 没有类型
```

那么 `route()` 只能：

1. 返回一个固定的收件人列表（回到"广播"）；
2. 或者解析 `text`（回到"用文案当协议"）。

两条路都通向 §2.3.7 那个坑。

### 5.4 `Message.java`：一条不可变消息

完整代码（73 行）：

```java
public record Message(Role from, MessageKind kind, String text) {

    public Message {
        Objects.requireNonNull(from, "发送方角色不能为 null");
        Objects.requireNonNull(kind, "消息类型不能为 null");
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("消息正文不能为空白");
        }
        text = text.strip();
    }

    public String describe() {
        return from.displayName() + " 的" + kind.label() + "：" + text;
    }
}
```

（省略了 javadoc，完整代码见源文件。）

**三行校验，各自防什么：**

| 校验 | 防什么 | 不做的后果 |
|---|---|---|
| `requireNonNull(from)` | 发送方为空 | `route(null, kind)` 里 `from == Role.CUSTOMER` 恒为 false，消息神秘消失 |
| `requireNonNull(kind)` | 类型为空 | `switch (kind)` 抛 NPE，且发生在路由之前，整个 `say()` 崩 |
| `text == null \|\| text.isBlank()` | 正文为空 | 日志里出现 `[客服-小王] 提问：` 后面什么都没有，无法排查 |

**为什么正文空白要用 `IllegalArgumentException` 而不是 `requireNonNull`？**

因为 `""` 和 `"   "` 都**不是 `null`**，但它们是无效的正文。
`requireNonNull` 只能挡住 `null`，挡不住空字符串。

所以需要两步：

```java
if (text == null || text.isBlank()) {
    throw new IllegalArgumentException("消息正文不能为空白");
}
```

注意这里用的是 `IllegalArgumentException` 而不是 `NullPointerException` ——
因为"传了空字符串"和"传了 null"是同类错误（都是参数不合法），
统一用 `IllegalArgumentException` 更好。

**为什么要 `text.strip()`？**

这是**规范化**，不是校验。

```java
text = text.strip();
```

如果不做，日志里会出现：

```text
  [客户-李女士] 提问：   耳机左耳没声音，能退吗？
```

前面三个空格。看起来无害，但当你要**按正文做统计**（比如"最常见的提问是什么"）时，
`"  耳机左耳没声音"` 和 `"耳机左耳没声音"` 会被算成两条。

**在数据进系统的边界上做规范化，比在每个消费方做要便宜得多。**

**注意紧凑构造器里的赋值：**

```java
text = text.strip();
```

在 `record` 的紧凑构造器里，**对参数赋值就是给字段赋值**。
所以这一行真的修改了 `this.text`。

这是 `record` 的一个特性：紧凑构造器没有参数列表，参数名就是字段名。
**赋值即生效，不需要写 `this.text = text`。**

**为什么 `describe()` 要把 `from` 和 `kind` 拼在一起？**

```java
return from.displayName() + " 的" + kind.label() + "：" + text;
```

输出效果：

```text
客户 的提问：耳机左耳没声音，能退吗？
仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
```

**这一段是给所有参与者共用的日志格式。** 如果每个参与者的 `receive()` 各自拼：

```java
// Customer.java
System.out.println("收到 " + message.from() + " 的" + message.kind() + "：" + message.text());

// Agent.java
System.out.println("收到来自" + message.from() + "的消息（" + message.kind() + "）：" + message.text());
```

就会出现六种日志格式。出事故时 grep 日志会非常痛苦。

**`describe()` 把格式统一在值的定义处**，这是值对象的典型收益。

**为什么不用 `toString()`？**

`record` 自动生成的 `toString()` 是：

```text
Message[from=CUSTOMER, kind=QUESTION, text=耳机左耳没声音，能退吗？]
```

带类名、字段名、英文枚举常量。这在调试时有用，但不适合给人看的日志。

所以有两个方法：

- `toString()` —— 调试用，由编译器生成；
- `describe()` —— 给人看，手写。

**如果合并成一个会怎样？** 你要么损失调试信息，要么损失可读性。

**如果不用 `record` 而用普通类会怎样？**

需要手写：

```java
public final class Message {
    private final Role from;
    private final MessageKind kind;
    private final String text;

    public Message(Role from, MessageKind kind, String text) { ... }
    public Role from() { return from; }
    public MessageKind kind() { return kind; }
    public String text() { return text; }

    @Override public boolean equals(Object o) { ... }      // 10 行
    @Override public int hashCode() { ... }                 // 5 行
    @Override public String toString() { ... }              // 5 行
}
```

多出 40 行样板代码，而且每一处都可能写错（尤其是 `equals` 和 `hashCode` 的一致性）。

**`record` 的价值不是"少写代码"，而是"不可能写错"。**

### 5.5 `Participant.java`：参与者接口

完整代码（111 行，其中 95 行是 javadoc）：

```java
public interface Participant {

    Role role();

    String name();

    void receive(Message message);

    CollaborationHub hub();

    default void say(MessageKind kind, String text) {
        hub().say(this, kind, text);
    }

    default void escalate(String reason) {
        hub().escalate(this, reason);
    }

    default void closeTicket() {
        hub().close(this);
    }
}
```

**四个抽象方法 + 三个默认方法。这是整个模式最关键的设计。**

逐个说四个抽象方法存在的理由：

| 方法 | 谁调用 | 为什么必须是抽象的 |
|---|---|---|
| `role()` | 中枢（路由、查重、日志） | 只有参与者自己知道自己的身份 |
| `name()` | 中枢（日志、失败记录） | 只有参与者自己知道自己的名字 |
| `receive(Message)` | 中枢（投递） | 六个角色的反应完全不同，没法默认实现 |
| `hub()` | 默认方法（`say` / `escalate` / `closeTicket`） | 默认方法需要它才能把 `this` 交给中枢 |

**为什么 `receive()` 不能有默认实现？**

假如给它一个默认实现：

```java
default void receive(Message message) {
    System.out.println("收到 " + message.describe());
}
```

那么六个类都可以不写 `receive()` —— 编译通过，程序能跑，但**所有参与者都只会打印**。

这就是"默认实现诱惑"的典型危险：它让"忘记实现"变得无声无息。
一个本该"冻结发票"的财务，变成了"打印一行日志"，而且编译器不会提醒。

**抽象方法的价值不是"强制你写代码"，而是"强制你做决定"。**
哪怕你的决定是写一个空方法体，那也是显式写出来的。

三个默认方法逐个说：

**`say(kind, text)` —— 只有一行，但有三层意义。**

```java
default void say(MessageKind kind, String text) {
    hub().say(this, kind, text);
}
```

第一层：**参与者不可能伪造身份。**
参数是 `this`，不是 `from`。调用 `customer.say(...)` 时，传进去的一定是
`customer` 这个对象。没有语法能让客户说"我是财务"。

第二层：**参与者不可能指定收件人。**
参数只有 `kind` 和 `text`。没有 `to`、没有 `targets`、没有 `Role`。

第三层：**新增参与者自动获得这个能力。**
六个类没有一个重写 `say()`，但它们全都能 `say()`。这是接口默认方法的核心价值：
**把"框架能力"和"业务实现"分开。**

对比抽象类的写法（§4.5 讨论过）：抽象类会占用继承名额，
而且需要 `protected` 字段或 `protected` 方法，暴露面更大。

**`escalate(reason)` —— 没有"升级给谁"的参数。**

```java
default void escalate(String reason) {
    hub().escalate(this, reason);
}
```

这是刻意省略的。谁来接手，由中枢的规则决定。

客户不需要知道公司里有"主管"这个角色，更不需要知道主管叫什么。
**如果参数是 `escalate(Role to, String reason)`，客户就得知道 `Role.SUPERVISOR` 存在。**

**`closeTicket()` —— 没有参数。**

```java
default void closeTicket() {
    hub().close(this);
}
```

关闭不需要原因，不需要指定谁接收，只需要表达"我要关"。
至于"关了之后通知谁"，是中枢的 `close()` 决定的。

**为什么这三个方法不合并成一个 `send(Action, String)`？**

```java
// 假设的合并方案
default void send(Action action, String text) {
    hub().handle(this, action, text);
}
```

合并之后：

- `Action` 又要分成 `SAY` / `ESCALATE` / `CLOSE` 三种，而它们的参数含义不同
  （`SAY` 的 text 是正文，`ESCALATE` 的 text 是原因，`CLOSE` 没有 text）；
- 中枢的 `handle()` 里要 switch `Action`，而这三个动作的副作用完全不同
  （`SAY` 不改状态，`ESCALATE` 改 `assignee`，`CLOSE` 改 `closed`）；
- 参数校验变复杂（`CLOSE` 时传了 text 怎么办？忽略还是报错？）。

**三个动作的语义、参数、副作用都不同，合并只会让每一处都变复杂。**

**如果删掉 `hub()` 会怎样？**

三个默认方法都需要它。如果删掉，默认方法必须自己想办法拿到中枢 ——
要么变成抽象方法（六个类各写一遍），要么通过别的方式注入。

更糟的是：如果 `hub()` 变成 `protected CollaborationHub hub` 字段
（像原项目那样），子类就能改它（探针 6 的问题）。

**`hub()` 是只读方法，这是"参与者只能属于一个中枢"这个约束的技术实现。**

**如果合并 `Participant` 和 `CollaborationHub` 会怎样？**

有人可能觉得："既然参与者要调中枢、中枢要调参与者，为什么不合成一个接口？"

合成就意味着参与者知道 `join` / `close` / `escalate` 的全部实现细节。
而且更糟：**参与者能调 `hub.join(other)` 把别人加进来**，
或者 `hub.close(this)` 关掉工单。

现在这些能力其实也有（因为 `hub()` 返回的是完整的 `CollaborationHub`），
但**分离成两个接口让"谁能做什么"变得可读**：

- 看 `Participant` → 参与者的能力边界；
- 看 `CollaborationHub` → 中枢的能力边界。

**两个接口各自表达一个角色，合起来就表达不清了。**

### 5.6 `CollaborationHub.java`：中枢契约

完整代码（106 行）：

```java
public interface CollaborationHub {

    void join(Participant participant);

    void say(Participant from, MessageKind kind, String text);

    void escalate(Participant from, String reason);

    void close(Participant from);

    List<Participant> roster();

    Role assignee();

    List<String> deliveryFailures();
}
```

（省略了 javadoc 与 `import java.util.List;`，完整代码见源文件。）

**七个方法，分成两组：**

| 组 | 方法 | 特征 |
|---|---|---|
| 动作 | `join` / `say` / `escalate` / `close` | 改变状态，可能抛异常 |
| 查询 | `roster` / `assignee` / `deliveryFailures` | 只读，返回不可变集合 |

**为什么没有"发给某个角色"的方法？**

§4.7 详细论证过。一句话总结：**有那个方法，参与者就能指定收件人，
规则就会散落到每个调用点。**

**为什么所有动作的第一个参数都是 `Participant`？**

因为**发出动作的必须是"真实存在的一方"**。

如果签名是 `say(Role from, ...)`：

```java
hub.say(Role.FINANCE, REFUND_CONFIRMED, "已退 299 元");   // 谁都能冒充财务
```

这就成了权限漏洞。而且可追溯性也没了 —— 日志里只有"财务发了一条消息"，
不知道是财务的哪个人（虽然本项目每个角色只有一个实例，但这是设计原则）。

传 `Participant` 之后：

```java
hub.say(finance, REFUND_CONFIRMED, "已退 299 元");        // 一定是这个对象
```

而且中枢可以在日志里打印 `label(from)`，得到"财务-小赵"这种可定位的信息。

**`join` 的 javadoc 为什么强调"同角色只能有一个"？**

```java
/**
 * 让一个参与者加入协同。
 *
 * <p>同一角色只能有一个参与者：售后协同里，"两个客服同时负责一张工单"
 * 不是靠"加入两次"表达的，而是靠"升级 / 转派"这类显式动作。
 * 这个约束放在中枢里，就永远不会出现"同一个人收到两条"的问题
 * （原项目允许重复注册，探针 2 就是它的后果）。
 */
void join(Participant participant);
```

**接口的 javadoc 在描述"约束"，而不仅是"功能"。**

这是接口设计的一个重要习惯：功能可以从签名读出来，但**约束读不出来**。
"同角色只能有一个"这条约束如果不写在 javadoc 里，实现者可能写成"允许重复"，
调用者也可能写成"join 两次"。

**为什么 `roster()` 和 `deliveryFailures()` 返回 `List` 而不是 `Collection` 或 `Set`？**

`List` 有顺序。`roster()` 的顺序是加入顺序（Demo 输出依赖它），
`deliveryFailures()` 的顺序是失败发生顺序（排查时按时间读）。

如果返回 `Set`，顺序就丢了 —— 而这两个方法的价值恰恰在于"可预期的顺序"。

**为什么返回 `List.copyOf(...)` 而不是内部集合？**

```java
@Override
public List<Participant> roster() {
    return List.copyOf(participants);
}
```

三个理由：

1. **防篡改**。调用方拿到的列表是不可变的，`add` / `remove` 会抛
   `UnsupportedOperationException`。如果返回内部的 `ArrayList`，调用方一个
   `remove` 就能偷走一个参与者 —— 而且中枢完全不知道。
2. **防"看着看着变了"**。如果返回内部集合，调用方遍历时中枢可能同时 `join`，
   这就是探针 4 的 CME。`copyOf` 给了一个快照。
3. **表达意图**。"这是当前名单的一个副本"，读代码的人一看就懂。

**这三个查询方法为什么放在接口里，而不是只在实现类里？**

因为它们是**中枢现状的一部分**，是契约。

如果只在 `AfterSalesHub` 里，调用方就得：

```java
AfterSalesHub hub = (AfterSalesHub) collaborationHub;   // 强转
```

强转意味着"调用方知道具体实现"，那接口就没有隔离作用了。

而且演示代码 `Client.printRoster(AfterSalesHub hub)` 正好需要这个能力。
如果 `CollaborationHub` 没有 `roster()`，`Client` 就必须依赖 `AfterSalesHub` ——
**一个演示类依赖具体实现是可以的，但接口能提供为什么不提供？**

**注意 `deliveredCount()`、`closed()`、`ticketId()` 不在接口里。**

```java
// AfterSalesHub 里有，CollaborationHub 里没有
public int deliveredCount() { ... }
public boolean closed() { ... }
public String ticketId() { ... }
```

为什么？

- `deliveredCount()` 是**演示用的计数器**，业务上不需要；
- `closed()` 是 `close()` 之后的必然结果，业务方不需要"查"；
- `ticketId()` 是实现类的构造参数，不同实现可能有不同的标识方式。

**接口只放"所有中介者都该有"的能力。** 具体实现独有的东西留在实现类里 ——
这是接口最小化的原则。

反过来说，如果哪天发现三个不同的中介者实现都需要 `closed()`，
那就该把它提到接口里。**接口是随着实现收敛出来的，不是一开始设计全的。**

---

### 5.7 `AfterSalesHub.java`（上）：六个字段与 `join()`

`AfterSalesHub` 是 375 行、157 个代码行，全项目最大的类。
拆成三节读：字段与名单（本节）、发言与路由（§5.8）、升级关闭与投递（§5.9）。

#### 5.7.1 六个字段

```java
public final class AfterSalesHub implements CollaborationHub {

    private final String ticketId;

    private final List<Participant> participants = new ArrayList<>();

    private final Map<Role, Participant> byRole = new EnumMap<>(Role.class);

    private final List<String> failures = new ArrayList<>();

    private Role assignee = Role.AGENT;

    private int delivered;

    private boolean closed;
}
```

六个字段，四组角色：

| 字段 | 类型 | 可变 | 作用 |
|---|---|---|---|
| `ticketId` | `String` | 否 | 工单号，只用于日志与消息正文 |
| `participants` | `List<Participant>` | 内容可变 | 按加入顺序保存，供广播与 `roster()` |
| `byRole` | `Map<Role, Participant>` | 内容可变 | 角色 → 参与者，路由查表入口 |
| `failures` | `List<String>` | 内容可变 | 投递失败记录 |
| `assignee` | `Role` | **是** | 当前负责方，升级时改 |
| `delivered` | `int` | **是** | 成功投递计数，演示用 |
| `closed` | `boolean` | **是** | 生命周期状态 |

**为什么 `participants` 和 `byRole` 要同时存在？**

因为它们回答两个不同的问题：

- `participants` → "**有哪些人**"（需要顺序、需要遍历）；
- `byRole` → "**客服是谁**"（需要按角色查找）。

如果只留 `participants`，路由时要遍历找人：

```java
for (Participant p : participants) {
    if (p.role() == role) { ... }        // O(n)
}
```

如果只留 `byRole`，广播时要遍历 `EnumMap`：

```java
for (Participant p : byRole.values()) { ... }   // 顺序是按枚举声明，不是加入顺序
```

**两个索引，两个用途。这是典型的"用空间换清晰"** —— 只有 6 个元素，
占不了多少内存，但代码意图清楚得多。

**这两个集合的一致性怎么保证？**

**只在 `join()` 里同时修改，别处都不改。** 这是本类最重要的一条纪律：

```java
private final List<Participant> participants = new ArrayList<>();
private final Map<Role, Participant> byRole = new EnumMap<>(Role.class);
```

两个字段都是 `final`，但内容可变 —— Java 没有"只在这个方法里改"的语法。
所以只能靠**代码组织**来保证：全类搜 `participants.` 和 `byRole.`，
写入操作只出现在 `join()` 里。

```text
participants 的写入点：join() 里的 participants.add(...)      ← 唯一
byRole 的写入点：      join() 里的 byRole.put(...)            ← 唯一
participants 的读取点：close() 遍历、roster() 复制
byRole 的读取点：      join() 查重、deliver() 查表、describeTargets() 查表
```

**如果哪天需要"移除参与者"，必须写一个 `leave()` 方法，同时改两个集合。**
如果只改一个，就重现了原项目探针 1 的错配 —— 只不过方向相反。

**`assignee` 为什么初始值是 `Role.AGENT`？**

因为工单创建的那一刻，默认由客服接单。这是业务默认值，不是技术默认值。

如果初始值是 `null`，那么第一句客户提问就会：

```java
case QUESTION -> from == Role.CUSTOMER ? List.of(assignee) : List.of();
// List.of(null) → NullPointerException
```

`List.of(...)` 不接受 `null` 元素。所以 `assignee` 必须有初始值，
否则第一句话就崩。

**这个初始值也可以放在构造函数里：**

```java
public AfterSalesHub(String ticketId) {
    ...
    this.assignee = Role.AGENT;      // 和字段初始化等价
}
```

两种写法都行。字段初始化更靠近声明，读代码时更早看到"默认负责方是客服"。

**为什么 `delivered` 和 `closed` 不是 `final`？**

`delivered` 每次成功投递加一，`closed` 在 `close()` 里置真。

但更重要的是：**它们的可变性告诉读者"这个类是有状态的"。**
一个全是 `final` 字段的类是无状态的（线程安全、可复用）；
一个有可变字段的类是有状态的（每次实例都不同）。

**`AfterSalesHub` 是有状态的** —— 这是它区别于"工具类"的根本（见 §3.3）。

#### 5.7.2 构造函数

```java
public AfterSalesHub(String ticketId) {
    if (ticketId == null || ticketId.isBlank()) {
        throw new IllegalArgumentException("工单号不能为空白");
    }
    this.ticketId = ticketId.strip();
}
```

三行，和 `Customer` 的构造器同构：

```java
// Customer
if (name == null || name.isBlank()) {
    throw new IllegalArgumentException("客户称呼不能为空白");
}
this.name = name.strip();
```

**为什么每个类都重复这三行，不抽一个工具方法？**

可以抽：

```java
public final class Validators {
    public static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + "不能为空白");
        }
        return value.strip();
    }
}
```

但本项目**没有抽**，理由是：

1. 三行代码，抽出去的调用不会更短：
   `this.name = Validators.requireText(name, "客户称呼");` —— 一样长；
2. **错误信息的粒度**。每个类的错误信息都针对自己（"客户称呼" / "工单号" /
   "仓库名称"），抽出去之后要传一个 `what` 参数，反而容易写错；
3. **依赖方向**。抽一个 `Validators` 会让 13 个文件都依赖它。
   为了省 3 行，引入一个公共依赖，不划算。

**这是一个"不抽象"的决定。** 设计模式文档里经常强调"要抽象"，
但抽象是有成本的：多一个类、多一层间接、多一个需要理解的名字。

> **三行以内的重复，通常不值得抽象。**
> 判断标准不是"重复了几次"，而是"改动时会不会漏"。
> 校验逻辑几乎不会变，所以漏的风险很低。

#### 5.7.3 `join()`：五步，顺序不能换

```java
@Override
public void join(Participant participant) {
    Objects.requireNonNull(participant, "参与者不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已关闭，不能加入新参与者");
    }
    Role role = participant.role();
    Participant existing = byRole.get(role);
    if (existing != null) {
        throw new IllegalArgumentException(
                "角色「" + role.displayName() + "」已经由「" + existing.name()
                        + "」担任，不能再加入「" + participant.name() + "」");
    }
    byRole.put(role, participant);
    participants.add(participant);
}
```

五步，每一步都对应一个具体的失败模式：

| 步骤 | 代码 | 不做会怎样 |
|---|---|---|
| 1 | `requireNonNull` | 探针 3：`null` 进名单，广播时 NPE 断链 |
| 2 | `if (closed)` | 已关闭的工单还能加人，归档消息发不给他（他没在广播名单里） |
| 3 | `role()` + 查重 | 探针 2：重复注册，同一条消息收两遍 |
| 4 | `byRole.put` | 路由查不到人，所有消息都"跳过" |
| 5 | `participants.add` | `roster()` 空、`close()` 广播漏人 |

**为什么第 4、5 步是 `put` 然后 `add`，顺序有讲究吗？**

严格来说没有 —— 它们在同一个方法里、没有异常抛出的可能，顺序不影响结果。

但**先 `put` 后 `add` 有一个微弱的好处**：如果 `add` 抛异常（比如 `OutOfMemoryError`），
`byRole` 里已经有这个人了，至少按角色路由还能找到他。

反过来（先 `add` 后 `put`），如果 `put` 失败，`participants` 里有一个查不到角色的人，
`close()` 广播时会给他发消息，但按角色路由永远找不到他 —— 这是更糟的半失效状态。

**在"两个集合都要改"的场景里，先改"查表用"的那个，再改"遍历用"的那个。**
理由：查表失败是"看不见"，遍历失败是"看得见但不对"。**先保证核心功能可用。**

#### 5.7.4 异常类型的选择：`IllegalStateException` vs `IllegalArgumentException`

`join()` 里抛了两种异常：

```java
if (closed) {
    throw new IllegalStateException(...);        // 状态不允许
}
if (existing != null) {
    throw new IllegalArgumentException(...);     // 参数不合法
}
```

**为什么区分？**

| 异常类型 | 语义 | 调用方该怎么办 |
|---|---|---|
| `IllegalStateException` | "现在不行" | 等状态变化，或者走别的流程 |
| `IllegalArgumentException` | "这个参数永远不行" | 换个参数 |

- 工单已关闭 → 换一个工单，或者不 join（**换时机**）；
- 角色已被占用 → 换一个人，或者先让原负责人离开（**换参数**）。

**错误类型选对了，调用方才能写出正确的补救逻辑。**
全都抛 `RuntimeException` 的话，调用方只能 catch 住然后打印 —— 什么也做不了。

**注意 `Objects.requireNonNull` 抛的是 `NullPointerException`，不是
`IllegalArgumentException`。**

```java
Objects.requireNonNull(participant, "参与者不能为 null");
```

这是一个约定：`null` 永远用 NPE 表达。因为 NPE 的语义就是"本该有东西，结果是 null"，
而 `IllegalArgumentException` 更适合"值存在但不合法"（比如角色重复）。

#### 5.7.5 `join()` 的错误信息为什么这么长

```java
throw new IllegalArgumentException(
        "角色「" + role.displayName() + "」已经由「" + existing.name()
                + "」担任，不能再加入「" + participant.name() + "」");
```

真实输出（如果把 `Notifier` join 两次）：

```text
角色「通知器」已经由「站内信网关」担任，不能再加入「短信网关」
```

四个信息：

1. **哪个角色**冲突了（「通知器」）；
2. **现在是谁**在担任（「站内信网关」）；
3. **谁想加入**（「短信网关」）；
4. **能不能加**（"不能再加入"）。

**这四点是排查问题时最需要的。** 如果只写 `"角色重复"`，运维还得自己去查名单。

> **错误信息的标准：不看代码就能定位问题。**
> 达到这个标准的写法，通常是"把相关的三个值都打出来"。

### 5.8 `AfterSalesHub.java`（中）：`say()` 与 `route()`

#### 5.8.1 `say()`：四步，顺序也是刻意的

```java
@Override
public void say(Participant from, MessageKind kind, String text) {
    Objects.requireNonNull(from, "发送方不能为 null");
    Objects.requireNonNull(kind, "消息类型不能为 null");
    if (closed) {
        throw new IllegalStateException(
                "工单 " + ticketId + " 已关闭，" + from.name() + " 不能再发言");
    }

    Message message = new Message(from.role(), kind, text);
    List<Role> targets = route(from.role(), kind);

    System.out.println("  [" + label(from) + "] " + kind.label() + "：" + message.text());

    if (targets.isEmpty()) {
        System.out.println("      路由未命中：角色「" + from.role().displayName()
                + "」不能发送「" + kind.label() + "」，消息丢弃");
        return;
    }
    deliver(message, targets);
}
```

执行顺序（javadoc 里也写了）：

| 步骤 | 做什么 | 为什么在这个位置 |
|---|---|---|
| 1 | 校验 `from` / `kind` 非空 | 非法参数不该产生任何日志 |
| 2 | 校验 `closed` | 关闭后的调用不该打印"某某说了一句话" |
| 3 | 构造 `Message` | 它自己还会校验正文，双保险 |
| 4 | `route()` 算收件人 | **纯函数，无副作用，先算完** |
| 5 | 打印"谁说了什么" | 到这里所有校验都过了，这行日志一定有意义 |
| 6 | 空路由 → 提示 + 返回 | 未命中的消息不该进 `deliver()` |
| 7 | `deliver()` | 投递 |

**为什么第 4 步（算路由）在第 5 步（打印）之前？**

因为 `route()` 是纯函数：不打印、不改状态、不抛异常（`kind` 已校验非空）。
先算完，可以保证"打印出去的每一行日志，都对应一个真实的动作"。

如果先打印再算路由：

```java
System.out.println("  [" + label(from) + "] " + kind.label() + "：" + text);
List<Role> targets = route(from.role(), kind);
```

那么当 `route()` 抛异常时，日志里已经有"某某说了一句话"，但消息实际没发出去。
**日志和事实不一致，排查时会误判。**

**为什么"路由未命中"要打印，而不是静默丢弃？**

因为**未命中一定意味着调用方写错了代码**：

- 仓库调 `say(QUESTION, ...)` —— 规则表里没有这条边；
- 客户调 `say(STOCK_SHORTAGE, ...)` —— 客户不该上报缺货；
- 任何人调 `say(ESCALATION, ...)` —— 应该用 `escalate()`。

这三种都是**编程错误**，不是运行时数据问题。所以必须让开发者看到。

对照 §4.13 的三种 `default` 策略：这里是**中枢层面**的"报警"，
和 `Warehouse` 层面的"报警"是同一个思路 —— **上游有 bug，就必须喊出来。**

真实输出（Demo 6）：

```text
  [仓库-华南仓] 提问：有人能告诉我这单寄回哪里吗？
      路由未命中：角色「仓库」不能发送「提问」，消息丢弃
```

**注意提示语里同时有"角色是什么"和"想发什么"。** 这两个信息缺一不可 ——
只说"未命中"，开发者还得自己回代码里找是哪一行。

#### 5.8.2 `route()`：整张规则表只有一个副本

完整代码（18 行）：

```java
private List<Role> route(Role from, MessageKind kind) {
    return switch (kind) {
        case QUESTION -> from == Role.CUSTOMER
                ? List.of(assignee)
                : List.of();
        case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
                ? List.of(Role.CUSTOMER)
                : List.of();
        case STOCK_SHORTAGE -> from == Role.WAREHOUSE
                ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
                : List.of();
        case REFUND_CONFIRMED -> from == Role.FINANCE
                ? List.of(Role.CUSTOMER, Role.AGENT)
                : List.of();
        case NOTICE -> List.of(Role.CUSTOMER);
        case ESCALATION, ARCHIVE -> List.of();
    };
}
```

**这张表的七个分支，逐个追业务来源：**

**`QUESTION` —— 客户提问，给当前负责方。**

```java
case QUESTION -> from == Role.CUSTOMER ? List.of(assignee) : List.of();
```

- 发送方限制：只有 `CUSTOMER`。客服不能说"我有个提问"（他要说话应该用 `ANSWER`）。
- 收件人：`assignee`（**不是硬编码的 `AGENT`**）。

这一行是整个模式的核心价值所在：`assignee` 是字段，升级后自动变成
`SUPERVISOR`，**客户代码不用改**。

**`ANSWER` —— 答复，回流给客户。**

```java
case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR) ? List.of(Role.CUSTOMER) : List.of();
```

- 发送方限制：`AGENT` 或 `SUPERVISOR`。这两个角色都可能成为负责方，
  都可能答复客户。
- 收件人：`CUSTOMER`。

**为什么 `ANSWER` 不需要考虑 `assignee`？**

因为"谁在答复"已经由 `from` 决定了。不管是客服还是主管在答复，
收件人都是客户。**答复永远是"往回流"的。**

**`STOCK_SHORTAGE` —— 缺货，三方协同。**

```java
case STOCK_SHORTAGE -> from == Role.WAREHOUSE
        ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
        : List.of();
```

- 发送方限制：只有 `WAREHOUSE`。仓库是唯一能回答"有没有货"的角色。
- 收件人：客服（决定怎么办）+ 财务（冻结发票）+ 客户（知情）。

**这是唯一一条三方规则。** 它证明了"一条消息多个收件人"的必要性：
如果用广播实现，主管和通知器也会收到，那就是过度通知。

**`REFUND_CONFIRMED` —— 退款，两方。**

```java
case REFUND_CONFIRMED -> from == Role.FINANCE
        ? List.of(Role.CUSTOMER, Role.AGENT)
        : List.of();
```

- 发送方限制：只有 `FINANCE`。财务是唯一能回答"钱退不退"的角色。
- 收件人：客户（钱到账）+ 客服（留痕，客户可能追问）。

**为什么仓库收不到这条？**

因为仓库不需要知道钱的事。**这是"路由"相对于"广播"的最大优势：
不收，就不需要判断"这条跟我有关吗"。**

**`NOTICE` —— 提示，只推客户。**

```java
case NOTICE -> List.of(Role.CUSTOMER);
```

- 发送方限制：**没有**（任意角色都能发）。
- 收件人：只有客户。

**为什么任意角色都能发，但只给客户？**

因为 `NOTICE` 的语义是"给客户的补充说明"：

- 仓库：您的包裹已到驿站；
- 客服：我们下午 6 点前会处理；
- 财务：退款将在 3 个工作日内到账。

这些都是**对客户的告知**，不是内部协同。所以收件人只有客户，
内部角色不需要被这些"客套话"打扰。

**这一行没有 `from` 判断，是唯一一个"任意发送方"的分支。**
它证明规则表可以按需灵活 —— 有的规则限制发送方，有的不限制。

**`ESCALATION, ARCHIVE` —— 空列表。**

```java
case ESCALATION, ARCHIVE -> List.of();
```

- 收件人：空。
- 含义：**这条边不存在。**

因为这两种消息由 `escalate()` / `close()` 产生，不通过 `say()`。
`route()` 返回空，`say()` 里的 `targets.isEmpty()` 就会打印"路由未命中"并丢弃。

**为什么合成一个 `case` 而不是写两行？**

因为它们的处理完全一样（都返回空列表）。
合成一个 `case ESCALATION, ARCHIVE ->` 更简洁，也表示了"这两种类型在
`say()` 这条路径上地位相同"。

**如果以后需求变了（比如允许客服手动发一条归档通知），只要把
`ARCHIVE` 从这个 case 里拆出来，单独写一个分支。** 这个改动很小，
而且编译器不会阻止（因为类型已经被处理了）。

#### 5.8.3 `route()` 的三个性质

javadoc 里列了三点，值得展开：

**性质一：只依赖枚举，不依赖实例。**

```java
private List<Role> route(Role from, MessageKind kind)
```

参数是两个枚举，没有 `Participant`。这意味着：

- 换一个人当客服，规则不用改；
- 可以脱离任何参与者单独测试规则。

**如果参数是 `Participant from`，会发生什么？**

```java
// 假设的写法
private List<Role> route(Participant from, MessageKind kind) {
    if (from instanceof Warehouse) { ... }     // 需要 instanceof
}
```

`instanceof` 让中枢依赖所有具体类。加一个角色就要加一个分支，
而且分支之间可能互相影响。**枚举是"身份"的抽象，实例是"人"。路由只该关心前者。**

**性质二：纯函数。**

"纯函数"的定义：

1. 同样的输入永远同样的输出；
2. 没有副作用（不打印、不改状态、不抛异常）。

`route()` 满足吗？**基本满足，有一个例外：它读了 `assignee` 字段。**

```java
case QUESTION -> from == Role.CUSTOMER ? List.of(assignee) : List.of();
```

`assignee` 是字段，所以 `route()` 不是一个严格的纯函数 —— 它的输出依赖对象状态。

**但这个"不纯"是刻意的。** 因为"当前负责方"本来就是路由的一部分。
如果把 `assignee` 做成参数：

```java
private List<Role> route(Role from, MessageKind kind, Role assignee)
```

调用方每次都要传，而且容易传错（传了旧值）。**字段是更好的封装。**

所以准确的说法是：**`route()` 对"同一状态下的同样输入"是纯的。**
它不打印、不改状态、不抛异常 —— 这已经足够让它容易测试了。

**性质三：穷尽。**

`switch` 表达式没有 `default`，新增 `MessageKind` 时编译器会在这里报错。

这一点 §4.8 有完整的编译器证据。这里补一个**具体的场景**：

假设产品说"要加一种 `PARTIAL_REFUND`（部分退款）"。

1. 你在 `MessageKind` 里加 `PARTIAL_REFUND("部分退款")`；
2. 编译，`javac` 报错：

   ```text
   AfterSalesHub.java:189: 错误: switch 表达式不包含所有可能的输入值
           return switch (kind) {
                  ^
   ```

3. 你被迫回到 `route()`，思考"部分退款该给谁"；
4. 你写下 `case PARTIAL_REFUND -> List.of(Role.CUSTOMER, Role.AGENT, Role.FINANCE);`
   （部分退款多一个财务，因为要算差额）。

**如果没有穷尽性检查，第 2 步不会有任何报错。** 你会以为改完了，
上线之后客户申请部分退款，**消息静默地谁都没收到**。

这就是"用编译器替代测试"的典型场景 —— **一个永远不可能忘的检查点。**

### 5.9 `AfterSalesHub.java`（下）：`escalate()` / `close()` / `deliver()`

#### 5.9.1 `escalate()`：改状态 + 三路投递

```java
@Override
public void escalate(Participant from, String reason) {
    Objects.requireNonNull(from, "发起方不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已关闭，不能再升级");
    }
    Message message = new Message(from.role(), MessageKind.ESCALATION, reason);

    System.out.println("  [" + label(from) + "] 申请升级：" + message.text());
    System.out.println("      负责方：" + assignee.displayName() + " → " + Role.SUPERVISOR.displayName());
    assignee = Role.SUPERVISOR;
    deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));
}
```

**为什么不是 `say(from, ESCALATION, reason)`？**

因为升级有两个副作用，`say()` 一个都做不了：

1. **改 `assignee`**；
2. **固定三路投递**（不经过 `route()`，因为 `route()` 对 `ESCALATION` 返回空）。

**方法名要表达"做了什么"，不是"发了什么"。**
`escalate` 表达的是业务动作，`say(ESCALATION, ...)` 表达的是消息传递。
前者是意图，后者是手段。

**为什么打印两行（申请 + 负责方变化）？**

```text
  [客户-李女士] 申请升级：等了 3 天没人处理，要求升级
      负责方：客服 → 主管
      路由 → 主管-张主管、客服-小王、通知器-站内信网关
```

第二行是**状态变更的审计线索**。如果只打印"申请升级"，日志里看不到
"负责方变了"这件事 —— 而这是升级最重要的效果。

出事故时（"为什么主管没收到客户的新提问"），这一行能立刻回答：
"因为 12:03 升级了，负责方是主管"。

**先打印再改，还是先改再打印？**

```java
System.out.println("      负责方：" + assignee.displayName() + " → " + Role.SUPERVISOR.displayName());
assignee = Role.SUPERVISOR;
```

先打印（用的是旧值 + 新值），再改。顺序不能反 —— 反了就打印成
"主管 → 主管"。

**这是"日志要记录变化，不是记录结果"的一个实例。**
记录结果（只打印"负责方：主管"）在事后看是没有信息量的 ——
读者不知道之前是谁。

**为什么 `deliver()` 的目标列表是硬编码的三个角色？**

```java
deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));
```

因为它们代表三种不同的"知道方式"：

| 角色 | 为什么要收到 |
|---|---|
| `SUPERVISOR` | 他要接手，必须知道前因 |
| `AGENT` | 他要停止跟进，避免两个人同时回复 |
| `NOTIFIER` | 他要给主管手机发短信（主管可能不看后台） |

**注意 `CUSTOMER` 不在列表里。** 因为客户是发起方，他当然知道自己升级了 ——
再给他发一条"您已升级"是废话。

**这是 `close()` 之外唯一一处"多个固定收件人"的写法。**
它没有走 `route()`，因为 `route()` 是给 `say()` 用的规则表，
`escalate()` 是另一个入口，有自己的规则。

**如果以后要"升级也通知仓库"呢？**

改这一行：

```java
deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER, Role.WAREHOUSE));
```

**一处改动。** 而且因为用了 `List.of(...)` 字面量，改动点非常显眼。

#### 5.9.2 `close()`：唯一一处真正的广播

```java
@Override
public void close(Participant from) {
    Objects.requireNonNull(from, "发起方不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已经关闭过了");
    }
    Message message = new Message(from.role(), MessageKind.ARCHIVE,
            "工单 " + ticketId + " 已由 " + from.role().displayName() + " 关闭");

    System.out.println("  [" + label(from) + "] 申请关闭工单 " + ticketId);

    List<Role> targets = new ArrayList<>();
    for (Participant participant : participants) {
        if (participant != from) {
            targets.add(participant.role());
        }
    }
    deliver(message, targets);
    closed = true;
    System.out.println("  工单 " + ticketId + " 状态：已关闭");
}
```

**逐段说：**

**① 正文是中枢生成的，不是调用方传的。**

```java
Message message = new Message(from.role(), MessageKind.ARCHIVE,
        "工单 " + ticketId + " 已由 " + from.role().displayName() + " 关闭");
```

`closeTicket()` 的签名没有正文参数：

```java
default void closeTicket() {
    hub().close(this);
}
```

**为什么？**

因为归档消息的正文是**格式化的系统通知**，不是用户输入。
如果让调用方传，那么：

- 有人传"关了吧"，有人传"工单已关闭"，有人传"closed"；
- 每个人都可能忘记带工单号；
- 恶意或误用的调用方可以传"工单已退款 10000 元"这种假消息。

**系统生成的正文，由系统保证格式。** 这是"客户不能伪造归档内容"的边界。

**② 排除发起方。**

```java
if (participant != from) {
    targets.add(participant.role());
}
```

用 `!=` 引用比较，而不是 `equals`。

**为什么引用比较是对的？**

因为参与者是**唯一实例**（`join` 保证同角色唯一）。所以：

- 同一个参与者对象 → 引用相同；
- 不同角色 → 引用不同。

如果用 `equals`：`Participant` 接口没有定义 `equals`，所以默认是引用比较。
**两种写法效果一样，但 `!=` 更明确地表达了"是不是同一个对象"。**

**注意这和原项目的 `user != sender` 长得一样，但语义完全不同：**

| | 原项目 | 本项目 |
|---|---|---|
| 比较什么 | 遍历 `users` 时排除发送者 | 遍历 `participants` 时排除发起方 |
| 结果 | 发给其他所有人 | 发给其他所有人 |
| 区别 | **这是全部逻辑** | **这是例外，规则表才是常态** |

原项目里 `user != sender` 是**唯一的规则**；本项目里它是**唯一的广播**。
同一个表达式，一个在"只有广播"的系统里，一个在"只有一处广播"的系统里。

**③ 先 `deliver()` 再 `closed = true`。**

```java
deliver(message, targets);
closed = true;
```

理由见 §4.12：归档消息本身要投递，如果先置 `closed`，
任何检查 `closed` 的逻辑都会拒绝投递（虽然 `deliver()` 本身不检查）。

更实际的解释：**"关闭"是一个完成时状态 —— 消息发完了，才算关闭完成。**

**④ 关闭后打印状态。**

```java
System.out.println("  工单 " + ticketId + " 状态：已关闭");
```

这一行在所有参与者的反应之后，形成一个清晰的"结束标记"。
输出里能看到：

```text
        [站内信网关] 外发站内信给客户：「工单 AS-2024-0917 已由 客服 关闭」
  工单 AS-2024-0917 状态：已关闭
```

**为什么重复关闭要抛异常？**

```java
if (closed) {
    throw new IllegalStateException("工单 " + ticketId + " 已经关闭过了");
}
```

因为"关闭已关闭的工单"是**逻辑错误**，不是正常流程：

- 如果静默返回，调用方以为关闭成功，但其实什么也没发生（消息发了两遍？还是没发？）；
- 如果允许重复关闭，`participants` 会收到两遍归档，仓库会"解除库存锁定"两次。

**抛异常的成本是"调用方必须处理"，收益是"状态永远清晰"。**

#### 5.9.3 `deliver()`：四层防护

```java
private void deliver(Message message, List<Role> targets) {
    System.out.println("      路由 → " + describeTargets(targets));

    for (Role role : targets) {
        Participant target = byRole.get(role);
        if (target == null) {
            System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
            continue;
        }
        try {
            target.receive(message);
            delivered++;
        } catch (RuntimeException e) {
            String record = target.name() + " 投递失败：" + e.getClass().getSimpleName()
                    + "：" + e.getMessage();
            failures.add(record);
            System.out.println("      ! 已隔离：" + record);
        }
    }
}
```

**第 0 层：打印路由结果。**

```java
System.out.println("      路由 → " + describeTargets(targets));
```

在投递**之前**打印，这样即使第一个收件方就炸了，日志里也有"本来要发给谁"。

真实输出：

```text
      路由 → 客服-小王、财务（未加入）、客户-王先生
```

**注意 `财务（未加入）` —— `describeTargets()` 会把未加入的角色标注出来。**
这让"规则说该给财务，但财务没参加"这件事在日志里可见。

**第 1 层：`null` 检查（未加入）。**

```java
if (target == null) {
    System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
    continue;
}
```

`byRole.get(role)` 返回 `null`，说明这个角色没有参加。这是正常情况，
不是错误 —— 所以 `continue` 而不是抛异常。

**第 2 层：`try` 包住 `receive()` 和 `delivered++`。**

```java
try {
    target.receive(message);
    delivered++;
} catch (RuntimeException e) { ... }
```

注意 `delivered++` 在 `try` **里面**、`receive()` **之后**。
如果它在 `catch` 里或者外面，计数就会错。

**第 3 层：`catch RuntimeException`，记录三段式信息。**

```java
String record = target.name() + " 投递失败：" + e.getClass().getSimpleName()
        + "：" + e.getMessage();
```

真实输出：

```text
      ! 已隔离：短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
```

**第 4 层：不 rethrow。**

`catch` 块里没有 `throw e`。异常被彻底吞掉（但留了记录）。

**这是"隔离"的定义：一个参与者的失败，不影响其他参与者，也不影响调用方。**

**为什么 `describeTargets()` 要用实例方法而不是静态方法？**

```java
private String describeTargets(List<Role> targets) {
    List<String> parts = new ArrayList<>();
    for (Role role : targets) {
        Participant participant = byRole.get(role);
        parts.add(participant == null
                ? role.displayName() + "（未加入）"
                : label(participant));
    }
    return String.join("、", parts);
}
```

因为它要读 `byRole` 字段。`label()` 是静态的（不读字段），
`describeTargets()` 是实例的（读字段）—— **这个区别直接反映了"需要什么上下文"。**

**为什么用 `String.join("、", parts)` 而不是 `StringBuilder` 拼接？**

因为要处理"最后一个不加顿号"的问题：

```java
// StringBuilder 版本
for (int i = 0; i < parts.size(); i++) {
    sb.append(parts.get(i));
    if (i < parts.size() - 1) {
        sb.append("、");              // ← 这个判断容易写错
    }
}
```

`String.join` 一行搞定，而且空列表时返回 `""`（不会多一个顿号）。

**`List.of(...)` 和 `new ArrayList<>()` 的选择：**

`close()` 里用 `new ArrayList<>()`，因为要逐个 `add`；
`escalate()` 里用 `List.of(...)`，因为元素是固定的三个。

**能不可变就不可变。** `List.of(...)` 返回的列表不能被修改，
如果 `deliver()` 里不小心写了 `targets.add(...)`，会立刻抛
`UnsupportedOperationException` —— 而不是悄悄改掉调用方的列表。

---

### 5.10 `Customer.java`：只关心三种消息

完整代码（81 行，34 个代码行）：

```java
public final class Customer implements Participant {

    private final String name;
    private final CollaborationHub hub;

    public Customer(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("客户称呼不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.CUSTOMER;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CollaborationHub hub() {
        return hub;
    }

    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case ANSWER -> System.out.println("        [" + name + "] 客户：好，那我等结果。");
            case REFUND_CONFIRMED -> System.out.println("        [" + name + "] 客户：钱到账了，问题解决。");
            case ARCHIVE -> System.out.println("        [" + name + "] 客户：会话结束，给这次服务打 5 星。");
        }
    }
}
```

（省略了 javadoc，完整代码见源文件。）

**三个分支，对应客户在售后流程里的三个关键时刻：**

| 消息 | 客户的反应 | 业务含义 |
|---|---|---|
| `ANSWER` | "好，那我等结果" | 客服给了承诺，客户进入等待 |
| `REFUND_CONFIRMED` | "钱到账了，问题解决" | 钱的事解决了，客户满意 |
| `ARCHIVE` | "会话结束，打 5 星" | 流程闭环，客户评价 |

**注意这里没有 `STOCK_SHORTAGE` 分支。**

但 `route()` 里缺货是发给客户的：

```java
case STOCK_SHORTAGE -> List.from(Role.AGENT, Role.FINANCE, Role.CUSTOMER);
```

所以客户**会收到**缺货消息。收到的表现是什么？

```text
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
```

**只打印"收到"，没有额外动作。** 这正是 `switch` 里没有那个分支的效果。

**为什么这样设计？**

因为客户收到缺货消息后，**该做的事已经由系统做完了** —— 客服会决定退款，
财务会准备退款。客户不需要"处理"这条消息，他只需要"知道"。

**如果硬加一个分支：**

```java
case STOCK_SHORTAGE -> System.out.println("        [" + name + "] 客户：那就退款吧。");
```

这句话是**多余的**，而且会误导 —— 客户说"那就退款吧"，但退款流程并不是
由这句话触发的（是客服在 `Agent.receive()` 里决定走退款的）。

**代码里每一行输出都是"事实"。如果一行输出不对应任何状态变化，它就是噪音。**

**为什么 `switch` 没有 `default`？**

§4.13 详细讲了。这里补一个具体的验证方法：**如果你想确认"省略 default 不等于遗漏"，
可以数一下 7 种类型里有几个分支。**

7 种 `MessageKind`，`Customer` 有 3 个分支 → 4 种类型走"什么都不做"。
这 4 种是：`QUESTION`（自己发的，不会收到）、`STOCK_SHORTAGE`（收到但无动作）、
`ESCALATION`（自己发起的）、`NOTICE`（收到但无动作）。

**"什么都不做"是一个有效的决定，只要它是显式做出的。**

**`name` 和 `hub` 都是 `private final`。**

这一点 §4.6 讲过，这里补一个对比：

```java
// 原项目 User：两个 protected 非 final 字段
protected String name;
protected ChatRoomMediator mediator;

// 本项目 Customer：两个 private final 字段
private final String name;
private final CollaborationHub hub;
```

四个修饰符的差别：

| | `protected` 非 final | `private final` |
|---|---|---|
| 外部能改吗 | 同包能改 | 不能 |
| 子类能改吗 | 能 | 不能（而且类是 `final`，没有子类） |
| 初始化后能变吗 | 能 | 不能 |

**注意类声明是 `public final class Customer`。**

`final` 意味着**不能被继承**。这是有意的：

- 如果允许继承，子类可以重写 `role()` 返回别的角色 —— 冒充；
- 如果允许继承，子类可以重写 `receive()` 做别的事 —— 而中枢不知道。

**`final` 类的代价是"不能扩展"。但这里扩展的正确方式是"写一个新的
`Participant` 实现"，不是"继承 `Customer` 改几个方法"。**

### 5.11 `Agent.java`：唯一"什么都收"的角色

完整代码（87 行，35 个代码行），核心是 `receive()`：

```java
@Override
public void receive(Message message) {
    System.out.println("        [" + name + "] 收到 " + message.describe());
    switch (message.kind()) {
        case QUESTION -> System.out.println("        [" + name + "] 客服：接单，先看一眼历史记录。");
        case STOCK_SHORTAGE -> System.out.println("        [" + name + "] 客服：收到缺货，这单走退款。");
        case ESCALATION -> System.out.println("        [" + name + "] 客服：抄送已阅，这单不再由我跟。");
        case ARCHIVE -> System.out.println("        [" + name + "] 客服：从我的待办里移除。");
    }
}
```

**四个分支，是六个角色里最多的。**

逐个说业务来源：

| 消息 | 客服的反应 | 为什么客服会收到 |
|---|---|---|
| `QUESTION` | 接单 | 默认负责方 |
| `STOCK_SHORTAGE` | 收到缺货，走退款 | 缺货路由的三方之一 |
| `ESCALATION` | 抄送已阅，不再跟 | 升级时被抄送（避免重复回复） |
| `ARCHIVE` | 从待办移除 | 关闭时广播 |

**客服为什么是"枢纽"？**

因为客服在业务上就是**那个"出了问题要背"的人**，所以每一条相关事实
都得让他知道。这是业务决定的，不是设计决定的。

**但"知道"不等于"由他转发"。这是两个容易混掉的概念。**

- 如果仓库上报缺货时，由**仓库**去调客服、调财务、调客户 —— 仓库就变成了
  半个中介者；
- 如果由**客服**去转发 —— 客服就成了上帝对象，而且客户直接提问时不经过它。

**本项目的做法：客服只做自己的反应，转发交给中枢。**

所以 `Agent.java` 里没有一行"通知财务"的代码，但缺货上报时财务确实收到了 ——
因为规则写在中枢的 `route()` 里。

**注意 `Agent` 没有 `ANSWER` 分支。**

但客服明明会发 `ANSWER`：

```java
team.agent().say(MessageKind.ANSWER, "我查一下库存，稍等两分钟。");
```

为什么不给自己发？因为 `route()` 里 `ANSWER` 的收件人是 `CUSTOMER`：

```java
case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
        ? List.of(Role.CUSTOMER) : List.of();
```

**发送方不会收到自己发的消息。** 这是路由表决定的，不是 `receive()` 里过滤的。

**为什么不在 `deliver()` 里统一过滤"发送方不收自己的消息"？**

因为规则表已经能做到这件事了。加一层"统一过滤"会让规则表失去部分表达能力 ——
万一某天需要"自己也收到一份留痕"（比如存档副本），统一过滤就成了障碍。

**把规则放在规则表里，不要在代码里加"通用但可能不需要"的逻辑。**

**`Agent` 的四个分支覆盖了 7 种类型里的 4 种，剩下 3 种是：**

- `ANSWER`：自己发的；
- `REFUND_CONFIRMED`：财务发给客户和客服 —— **等等，客服会收到这个！**

让我核对 `route()`：

```java
case REFUND_CONFIRMED -> from == Role.FINANCE
        ? List.of(Role.CUSTOMER, Role.AGENT) : List.of();
```

是的，客服会收到 `REFUND_CONFIRMED`。但 `Agent.receive()` 里**没有这个分支**。

**所以客服收到退款确认时，只打印"收到"，没有额外动作。** 真实输出（Demo 3）：

```text
        [小王] 收到 财务 的退款确认：已退 299 元，1-3 个工作日到账
```

**这是一个"收到但无动作"的例子，和客户收到缺货是一样的模式。**

为什么客服对退款确认无动作？

因为退款已经完成了。客服需要的是"留痕"（知道这单结了），
而不是"再做一个动作"。**日志里的"收到"就是留痕。**

**`NOTICE` 呢？**

`route()` 里 `NOTICE` 只发给 `CUSTOMER`，所以客服不会收到。这个分支也就没有。

**这解释了为什么 `Agent` 的 javadoc 说"剩下三种（答复、退款确认、提示）本来就不该到这里"** ——
严格说，退款确认**会**到这里，只是没有专门的动作。这个措辞在源码里已经修正为
"剩下三种（答复、退款确认、提示）本来就不该到这里：答复是客服自己发出的，
退款确认是财务直接给客户的，提示只推给客户"。

**嗯，这个说法对退款确认不够准确。** 让我重新核对一下。

实际上 `route()` 明确把 `REFUND_CONFIRMED` 发给 `AGENT`。所以"退款确认
不该到客服这里"这个说法是**错的**。

这是一个需要修正的 javadoc。让我记下来，在最后统一修正。

正确的说法应该是：

> 七种消息类型里有四种轮到客服处理，所以这里写了四个分支；
> 剩下三种里，`ANSWER` 是自己发出的、`NOTICE` 只推给客户，
> 而 `REFUND_CONFIRMED` 虽然会到客服这里（用于留痕），但不需要额外动作 ——
> 所以也没有分支。

**这说明"读代码时核对 javadoc"这一步不能省。** 我在正文里保留了这个发现过程，
因为它是真实的 —— 写文档时重读代码，发现了一处措辞不准确。

### 5.12 `Warehouse.java`：最薄的角色

完整代码（86 行，34 个代码行），核心是：

```java
@Override
public void receive(Message message) {
    System.out.println("        [" + name + "] 收到 " + message.describe());
    switch (message.kind()) {
        case ARCHIVE -> System.out.println("        [" + name + "] 仓库：解除库存锁定，从待办移除。");
        default -> System.out.println("        [" + name + "] 仓库：!! 这是我不该收到的消息类型「"
                + message.kind().label() + "」，请检查中枢的路由表");
    }
}
```

**只有两个分支：一个正常，一个报警。**

**为什么仓库的收件箱只有 `ARCHIVE`？**

核对 `route()` 的所有分支，找出哪些包含 `WAREHOUSE`：

| 分支 | 收件人 | 含仓库？ |
|---|---|---|
| `QUESTION` | `assignee`（客服/主管） | 否 |
| `ANSWER` | 客户 | 否 |
| `STOCK_SHORTAGE` | 客服 + 财务 + 客户 | **否** |
| `REFUND_CONFIRMED` | 客户 + 客服 | 否 |
| `NOTICE` | 客户 | 否 |
| `ESCALATION` | 主管 + 客服 + 通知器（在 `escalate()` 里） | 否 |
| `ARCHIVE` | 广播给其余所有人（在 `close()` 里） | **是** |

**注意 `STOCK_SHORTAGE` 不含仓库！**

看起来奇怪：仓库上报缺货，自己收不到？但这完全合理 —— **发送方不需要收到自己发的消息。**

所以仓库在整张工单里，唯一会收到的就是"关闭"那一刻的归档。

**为什么仓库收到归档要做"解除库存锁定"？**

因为缺货上报之后，系统会为这张单**锁定一部分库存**（防止卖给别的客户）。
工单关闭了，锁定要释放，否则这部分库存永远卖不出去。

**这是"广播"的必要性证明：六个人，六件不同的事，触发点是同一个关闭事件。**

**为什么 `default` 要报警而不是静默？**

§4.13 讲过。这里补一个具体的推理链：

假设某天有人改了 `route()`：

```java
// 错误的改动：把仓库也加进缺货的收件人
case STOCK_SHORTAGE -> from == Role.WAREHOUSE
        ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER, Role.WAREHOUSE)
        : List.of();
```

如果没有 `default` 报警，运行时会：

```text
        [华东仓] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
```

**就这一行。** 看起来正常（仓库收到了一条消息），没有任何异常提示。

有了 `default` 报警：

```text
        [华东仓] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [华东仓] 仓库：!! 这是我不该收到的消息类型「缺货上报」，请检查中枢的路由表
```

**第二行立刻指出"路由表写错了"。**

**这就是"报警"的价值：把静默的逻辑错误变成显式的运行时提示。**

**为什么报警信息里要带 `message.kind().label()`？**

```java
"!! 这是我不该收到的消息类型「" + message.kind().label() + "」，请检查中枢的路由表"
```

因为"我不该收到某个类型"这个问题，第一个要查的就是"**哪个类型**"。

如果只写"收到不该收的消息"，开发者还得去看日志的上一行才知道是什么类型。
**把关键变量打出来，能省一次翻日志。**

**仓库类的其余部分和其他参与者完全一样**：三个 getter、一个构造函数。
34 个代码行里，真正有业务含义的只有 4 行（两个 `case`）。

**这是"同事应该有多薄"的最好例子。**

仓库系统对接的是 WMS，它连"财务"这个词都不该出现。所以：

- 它没有"缺货要通知谁"的知识（在中枢里）；
- 它没有"退款怎么算"的知识（在财务里）；
- 它只有"我有货/没货"和"工单关了要解锁库存"这两件事。

### 5.13 `Finance.java`：两个分支，一条因果链

完整代码（82 行，33 个代码行）：

```java
@Override
public void receive(Message message) {
    System.out.println("        [" + name + "] 收到 " + message.describe());
    switch (message.kind()) {
        case STOCK_SHORTAGE -> System.out.println("        [" + name + "] 财务：冻结原发票，准备全额退款。");
        case ARCHIVE -> System.out.println("        [" + name + "] 财务：退款凭证已归档。");
    }
}
```

**两个分支，对应两条业务因果：**

| 消息 | 财务的反应 | 因果链 |
|---|---|---|
| `STOCK_SHORTAGE` | 冻结原发票，准备退款 | 没货 → 发不出去 → 要退款 → 冻结发票 |
| `ARCHIVE` | 退款凭证归档 | 工单结束 → 凭证归档 |

**为什么财务要关心"缺货"？**

因为"仓库没货"直接意味着"这单发不出去"，而发不出去就要退款，退款要走财务。

**这条因果关系是业务规则，不是技术细节 —— 它必须写在某个地方。**

三种写法的对比（源码 javadoc 里有完整版）：

| 写法 | 后果 |
|---|---|
| 写在仓库里 | 仓库要知道财务存在。仓库从"查库存"变成"懂退款流程" |
| 写在财务里 | 财务要知道"缺货"这件事。但财务怎么知道客户提问、怎么知道客服答复？最终它得把整条链路听一遍 |
| **写在中枢里** | 仓库只管上报缺货，中枢负责让财务知道 |

**三种写法在代码行数上差不多，差别在改动成本：**

半年后公司决定"缺货也要通知供应商"，改动点分别是：

- 改仓库 → 在 `Warehouse.reportShortage()` 里加一行；
- 改财务 + 加一条链路 → 财务要监听新事件；
- **在中枢里 → `case STOCK_SHORTAGE` 分支里加一个角色。**

只有最后一种是"改一行"。

**为什么财务对退款确认（`REFUND_CONFIRMED`）没有分支？**

因为那条消息是财务自己发的：

```java
case REFUND_CONFIRMED -> from == Role.FINANCE
        ? List.of(Role.CUSTOMER, Role.AGENT) : List.of();
```

**发送方不收到自己发的消息。**

**为什么财务对升级（`ESCALATION`）没有分支？**

因为 `escalate()` 的投递列表是：

```java
deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));
```

**没有 `FINANCE`。** 财务不需要知道工单升级了 —— 除非升级导致退款金额变化，
但那是一个新需求，需要新的消息类型。

**这体现了"窄路由"的纪律：不是"可能相关"就发给谁，而是"确定需要动作"才发给谁。**

**财务的 `switch` 也没有 `default`。**

为什么财务和客户、客服一样省略 `default`，而不是像仓库那样报警？

因为财务会收到的类型（`STOCK_SHORTAGE`、`ARCHIVE`）都已经处理了，
剩下 5 种类型本来就不该到财务这里。

**那和仓库有什么区别？**

| | 仓库 | 财务 |
|---|---|---|
| 会收到几种 | 1 种（`ARCHIVE`） | 2 种（`STOCK_SHORTAGE`、`ARCHIVE`） |
| 收到意外的概率 | 高（只有 1 种，规则表稍改就会溢出） | 中 |
| 收到意外的后果 | 库存没解锁 / 该补货没补 | 可能有资金风险 |
| 选择 | **报警** | 静默忽略 |

**这个选择是主观的，但它是有理由的。** 仓库只有 1 种消息，边界最窄，
所以"越界"的信号最强 —— 一旦收到别的，几乎肯定是路由表错了。
财务有 2 种，边界稍宽，"越界"的判定没那么确定。

> **这也是"三个前提相同、两个选择不同"的例子。**
> 设计模式不是"每个类都写成一样"，而是"每个类为自己的失败模式负责"。

### 5.14 `Supervisor.java`：升级之后的负责方

完整代码（83 行，35 个代码行）：

```java
@Override
public void receive(Message message) {
    System.out.println("        [" + name + "] 收到 " + message.describe());
    switch (message.kind()) {
        case ESCALATION -> System.out.println("        [" + name + "] 主管：接手，先翻一遍上下文再回客户。");
        case QUESTION -> System.out.println("        [" + name + "] 主管：客户又追问了，直接答复。");
        case ARCHIVE -> System.out.println("        [" + name + "] 主管：确认关闭，同意归档。");
        default -> System.out.println("        [" + name + "] 主管：（记录在案，无需动作）");
    }
}
```

**这个类证明了一件事：中介者的规则一变，参与者的行为跟着变，但代码不用改。**

**升级前：**

```text
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
```

主管什么都没收到。

**升级后：**

```text
  [客户-李女士] 提问：到底什么时候能退？
      路由 → 主管-张主管
        [张主管] 收到 客户 的提问：到底什么时候能退？
        [张主管] 主管：客户又追问了，直接答复。
```

**同一个客户、同一句 `say(QUESTION, ...)`，收件人变成了主管。**

主管的代码里本来就写着"收到提问就回答"，所以它不需要知道
"自己是从什么时候开始接单的"。

**这就是中介者模式最容易被忽略的收益：路由是运行时的、可变的。**

如果把"客户提问发给客服"写死在客户类里：

```java
public void ask(String text) {
    agent.receive(text);      // ← 写死
}
```

升级之后就必须改客户类的代码 —— 而客户类可能已经发布到 App 里了。

**主管的三个分支对应三种真实场景：**

| 消息 | 主管的反应 | 场景 |
|---|---|---|
| `ESCALATION` | 接手，先翻上下文 | 刚接手，要看前因 |
| `QUESTION` | 直接答复 | 接手之后客户继续追问 |
| `ARCHIVE` | 确认关闭，同意归档 | 关闭确认 |

**为什么有 `default`？**

```java
default -> System.out.println("        [" + name + "] 主管：（记录在案，无需动作）");
```

因为主管这个角色天然要"什么都知道一点" —— 收到意外消息不算异常，
记一笔就行。

**为什么是"记录"而不是"报警"？**

报警的前提是"这一定是错的"。但主管可能因为业务调整收到新类型的消息
（比如"客户投诉"这个新类型先发给主管观察一段时间）。
这时候报警就是噪音。

**"记录"的语义是：我知道这件事，但我判断不需要动作。**

**注意 `default` 的输出用了括号 `（记录在案，无需动作）`。**

这是一个视觉信号：括号表示"补充说明"，不是"动作"。
对比仓库的 `!!` 前缀 —— 那是"警报"的视觉信号。

**输出格式也是 API。** 一个看日志的人应该能通过前缀判断严重程度：

| 前缀 | 含义 | 出现位置 |
|---|---|---|
| （无） | 正常动作 | 所有参与者 |
| `!!` | 上游有 bug | `Warehouse` 的 default |
| `（...）` | 记录但无需动作 | `Supervisor` 的 default |
| `!` | 已隔离的失败 | `deliver()` 的 catch |
| `>` 或 `→` | 路由/流向 | `deliver()` 的路由行 |

### 5.15 `Notifier.java`：对外渠道的安全边界

完整代码（85 行，36 个代码行）：

```java
@Override
public void receive(Message message) {
    System.out.println("        [" + name + "] 收到 " + message.describe());
    switch (message.kind()) {
        case ESCALATION -> System.out.println("        [" + name + "] 外发短信给主管手机：「"
                + message.text() + "」");
        case ARCHIVE -> System.out.println("        [" + name + "] 外发站内信给客户：「"
                + message.text() + "」");
        default -> System.out.println("        [" + name + "] 不在外发白名单，丢弃（防止内部消息外泄）");
    }
}
```

**两个外发分支 + 一个安全拒绝。**

| 消息 | 外发方式 | 为什么 |
|---|---|---|
| `ESCALATION` | 短信给主管手机 | 主管需要马上知道（他可能不看后台） |
| `ARCHIVE` | 站内信给客户 | 会话结束的通知 |
| 其余 | **一律不发** | 安全边界 |

**为什么通知器也算"参与者"？**

因为它具备参与者的全部特征：有身份、会收消息、会做反应。

**至于它背后是人、是部门、还是另一个系统，对中枢来说没有区别** ——
中介者只关心"谁能收什么"。

**这带来一个实际好处：接第三方渠道不需要改业务代码。**

明天公司要换成钉钉机器人：

```java
public final class DingTalkNotifier implements Participant {
    @Override public Role role() { return Role.NOTIFIER; }
    @Override public void receive(Message message) { /* 调钉钉 API */ }
    // ...
}
```

然后在 `build()` 里把 `new Notifier(...)` 换成 `new DingTalkNotifier(...)`，
**中枢和另外五个同事一行都不用改。**

**为什么 `default` 是"拒绝"？**

因为通知器会把消息发到**公司外面**（客户手机、主管手机）。

所以它对"什么能外发"必须保守。哪怕某天有人把内部消息误路由到通知器，
它也不会把"财务正在冻结发票"这种话发给客户。

**这是一条安全边界，不是一条业务规则。安全边界的原则是"默认拒绝，白名单放行"。**

**对比仓库的 `default`：**

| | 仓库 | 通知器 |
|---|---|---|
| default 做什么 | 报警 | 拒绝执行 |
| 为什么 | 想知道出了什么错 | 就算出错也不敢往外发 |
| 失败方向 | **fail loud**（喊出来） | **fail safe**（什么都不做） |

**同样是兜底分支，兜的东西不一样。**

- 仓库的失败是"我不知道该怎么办"—— 所以喊出来让人来管；
- 通知器的失败是"我可能造成不可挽回的损害"—— 所以宁可什么都不做。

> **一条可以带走的原则**
>
> 兜底分支该做什么，取决于"这个分支执行错的代价"。
>
> - 代价是"少做一件事" → 可以忽略；
> - 代价是"上游有 bug 但我不知道" → 报警；
> - 代价是"对外造成损害" → 拒绝。

**注意外发的内容直接用了 `message.text()`：**

```java
case ESCALATION -> System.out.println("        [" + name + "] 外发短信给主管手机：「"
        + message.text() + "」");
```

**这里没有做任何"脱敏"或"改写"。**

在真实系统里，这一步应该做：

```java
// 真实系统里应该有的处理
String safeText = TextSanitizer.forExternal(message.text());   // 脱敏、截断、转义
```

本项目省略了，因为它是演示。但**这个位置是必须记住的边界**：

> 内部消息的正文，和对外发送的正文，可能不是同一个东西。

比如内部消息"客户李女士（手机 138****5678）要求退款 299 元"，
外发给客户时应该变成"您的退款申请已受理"。

**把外发逻辑集中在一个 `Notifier` 里，这个改写就只有一个位置要做。**
如果外发散落在六个参与者的 `receive()` 里，你要改六处 —— 而且一定会漏。

### 5.16 `Client.java`：六段 Demo

306 行，154 个代码行。全项目最长的文件，但**没有一行业务逻辑** ——
它只是"组装 + 调用 + 打印说明"。

#### 5.16.1 结构

```java
public static void main(String[] args) {
    demoOneRoutingToOne();
    demoTwoStockShortage();
    demoThreeRefund();
    demoFourEscalation();
    demoFiveClose();
    demoSixIsolation();
    summary();
}
```

六段 Demo 的对应关系：

| Demo | 演示什么 | 对应原项目哪个问题 |
|---|---|---|
| 1 | 客户提问只到负责方 | P7（没有定向能力）+ 广播之痛 |
| 2 | 缺货触发三方协同 | P7（没有"一条消息多个收件人"的规则） |
| 3 | 换个角色发言换批收件人 | P7（收发两端永远是"除我之外"） |
| 4 | 升级后负责方变更 | P7（没有"负责方"这个概念）+ 路由不可变 |
| 5 | 关闭是唯一广播 + 关闭后不能发言 | 生命周期缺失 |
| 6 | 一家炸了不影响其他家 + 未定义路由被拦下 | P3（null 断链）+ P4（CME） |

**六段 Demo 的顺序不是随意的：**

1 → 2 → 3 是"路由从窄到宽"（一方 → 三方 → 按角色变化）；
4 是"路由随状态变化"；
5 是"生命周期";
6 是"失败处理"。

**这是"从正常到异常"的教学顺序。**

#### 5.16.2 `Team` record：为什么用 record 打包

```java
private record Team(AfterSalesHub hub, Customer customer, Agent agent,
                    Warehouse warehouse, Finance finance, Supervisor supervisor,
                    Notifier notifier) {
}
```

七个组件打包成一个 record。

**为什么？**

因为每段 Demo 都需要这七个东西：

```java
// 不用 record
AfterSalesHub hub = new AfterSalesHub("AS-2024-0917");
Customer customer = new Customer("李女士", hub);
Agent agent = new Agent("小王", hub);
// ... 六行 new

// 用了 record
Team team = build();
```

**演示代码越短，"中介者到底省了什么"越明显。**

如果每段 Demo 都花 10 行在 `new` 上，读者会以为"中介者模式就是一堆对象"。
用 `build()` 把组装收起来，每段 Demo 就只剩真正要演示的两三行。

**为什么 `Team` 里 `hub` 的类型是 `AfterSalesHub` 而不是 `CollaborationHub`？**

因为 `Client` 要用 `hub.deliveredCount()` 和 `hub.closed()` ——
这两个方法不在接口里（§5.6 讨论过）。

**演示代码依赖具体实现是可以接受的。** 如果连演示都要面向接口，
反而会增加噪音（要额外做类型转换）。

#### 5.16.3 `build()`：唯一一处"谁认识谁"

```java
private static Team build() {
    AfterSalesHub hub = new AfterSalesHub("AS-2024-0917");
    Customer customer = new Customer("李女士", hub);
    Agent agent = new Agent("小王", hub);
    Warehouse warehouse = new Warehouse("华东仓", hub);
    Finance finance = new Finance("小赵", hub);
    Supervisor supervisor = new Supervisor("张主管", hub);
    Notifier notifier = new Notifier("站内信网关", hub);

    hub.join(customer);
    hub.join(agent);
    hub.join(warehouse);
    hub.join(finance);
    hub.join(supervisor);
    hub.join(notifier);
    return new Team(hub, customer, agent, warehouse, finance, supervisor, notifier);
}
```

**十三行，是全项目唯一一处"六个参与者同时出现"的地方。**

**注意这里没有"谁认识谁"的代码。** 六个参与者都只认识 `hub`，
而 `hub` 认识所有人 —— 这是一个星形结构，不是网状结构。

**为什么 join 顺序重要？**

因为它决定了 `close()` 广播的顺序：

```java
hub.join(customer);      // 1
hub.join(agent);         // 2
hub.join(warehouse);     // 3
hub.join(finance);       // 4
hub.join(supervisor);    // 5
hub.join(notifier);      // 6
```

Demo 5 的广播输出顺序就是：客户 → 仓库 → 财务 → 主管 → 通知器（客服是发起方）。

**如果 join 顺序变了，输出就变了，哈希也变了。**
这就是为什么三次运行的哈希能一致 —— 顺序是确定性的。

**为什么工单号是固定的 `"AS-2024-0917"`？**

因为 Demo 要可复现。如果用 `UUID.randomUUID()`，每次输出都不同，哈希就没意义了。

**演示代码的"确定性"比"真实感"重要。** 一个不能复现的 Demo，
在教学上是不合格的 —— 读者无法对照。

#### 5.16.4 `demoFourEscalation()`：本项目的"高光时刻"

```java
private static void demoFourEscalation() {
    section("Demo 4：升级之后，同一句话换了一个收件人");
    Team team = build();

    team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
    System.out.println("  当前负责方：" + team.hub().assignee().displayName());

    team.customer().escalate("等了 3 天没人处理，要求升级");
    System.out.println("  当前负责方：" + team.hub().assignee().displayName());

    team.customer().say(MessageKind.QUESTION, "到底什么时候能退？");
    System.out.println("  客户代码一个字没改，收件人从「客服」变成了「主管」");
}
```

**十行，演示了中介者模式最有价值的能力。**

三次调用：

1. `say(QUESTION, ...)` → 路由到客服；
2. `escalate(...)` → 改 `assignee`；
3. `say(QUESTION, ...)` → 路由到主管。

**第 1 次和第 3 次的代码完全一样：**

```java
team.customer().say(MessageKind.QUESTION, "...");
```

**区别只在中间那次 `escalate()`。** 而这个区别是**中枢的状态变化**，
不是客户代码的变化。

**这就是"路由从编译期常量变成运行期状态"的可执行证明。**

**为什么在两处打印 `assignee()`？**

```java
System.out.println("  当前负责方：" + team.hub().assignee().displayName());
```

因为要**让状态变化可见**。如果不打印，读者只能从"路由 → 客服-小王"和
"路由 → 主管-张主管"这两行推断状态变了。

**显式打印状态，比让读者推断要好。** 这是演示代码的一条准则：
**关键变量要主动展示，不要指望读者从输出反推。**

#### 5.16.5 `demoSixIsolation()`：用内部类造一个"坏网关"

```java
private static final class BrokenNotifier implements Participant {

    private final CollaborationHub hub;

    BrokenNotifier(CollaborationHub hub) {
        this.hub = hub;
    }

    @Override
    public Role role() {
        return Role.NOTIFIER;
    }

    @Override
    public String name() {
        return "短信网关（故障）";
    }

    @Override
    public CollaborationHub hub() {
        return hub;
    }

    @Override
    public void receive(Message message) {
        throw new IllegalStateException("短信通道超时，" + message.kind().label() + " 未送达");
    }
}
```

**这个类存在的意义：证明中枢的 try-catch 真的有用，而不是"看起来写了 try-catch"。**

`receive()` 直接抛异常，而且抛的是 `IllegalStateException`（一个 `RuntimeException`）。

真实输出（Demo 6）：

```text
  —— 关闭工单：短信网关故障，其他人照常收到 ——
  [客服-小李] 申请关闭工单 AS-2024-0918
      路由 → 客户-王先生、仓库-华南仓、通知器-短信网关（故障）
        [王先生] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
        [王先生] 客户：会话结束，给这次服务打 5 星。
        [华南仓] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
        [华南仓] 仓库：解除库存锁定，从待办移除。
      ! 已隔离：短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
  工单 AS-2024-0918 状态：已关闭
  投递失败记录（1 条）：
    · 短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
```

**关键观察：**

1. 客户和仓库都收到了归档（在通知器**之前**）；
2. 通知器抛异常，被隔离，打印 `! 已隔离：...`；
3. 工单状态仍然是"已关闭"（异常没有中断 `close()`）；
4. 失败记录进了 `deliveryFailures()`。

**如果通知器排在客户前面会怎样？**

那客户和仓库都收不到（因为异常中断了循环）—— 这正是原项目探针 3 的行为。

**本项目不会，因为每个收件方独立 try-catch。**

**注意 `BrokenNotifier` 的 `hub` 字段从未被使用。**

```java
private final CollaborationHub hub;

@Override
public CollaborationHub hub() {
    return hub;
}
```

`hub()` 被接口要求，但 `BrokenNotifier` 自己从不调用它（它只会抛异常）。

**这是"接口的代价"：有些实现类不需要某个方法，但必须提供它。**

为什么不让 `hub()` 变成默认方法返回 `null`？

```java
// 不要这么做
default CollaborationHub hub() {
    return null;
}
```

因为那会让 `say()` / `escalate()` / `closeTicket()` 在调用时 NPE ——
而且是在"参与者主动发言"这个看似正常的操作上崩。

**显式实现一个没用的 getter，比留一个会 NPE 的默认方法好。**

#### 5.16.6 `section()` 和 `summary()`：输出的可读性

```java
private static int printedSections;

private static void section(String title) {
    if (printedSections > 0) {
        System.out.println();
    }
    printedSections++;
    System.out.println("[" + title + "]");
}
```

**为什么用静态计数器控制空行，而不是每处都写 `System.out.println()`？**

如果每段 Demo 开头都写：

```java
System.out.println();
System.out.println("[Demo 1：...]");
```

那么**第一段前面会多一个空行**（因为前面没有内容）。

用计数器：

```java
if (printedSections > 0) {
    System.out.println();      // 只有不是第一段才打印空行
}
```

**这才是"每段之间有一个空行，但开头没有"的正确写法。**

**这是一个小细节，但它决定了输出的"第一行是什么"。**
如果输出第一行是空行，复制到文档里会多一个空行，哈希也会不同。

**`summary()` 用表格对齐：**

```java
System.out.println("  1. 客户提问只到负责方       —— 原项目只能广播给所有人");
System.out.println("  2. 缺货触发三方协同         —— 原项目没有「一条消息多个收件人」的规则");
```

**为什么用空格对齐而不是 Markdown 表格？**

因为这是控制台输出，不是文档。空格对齐在等宽字体里能对齐，
在终端里能对齐，而且复制到任何地方都是纯文本。

**但从代码维护角度，空格对齐是脆弱的** —— 如果第 3 条的文字变长，
前面的空格要重新数。

**演示代码可以接受这种脆弱性**，因为它只跑一次、输出固定、有哈希校验。
**生产代码的日志不要这么做** —— 用结构化日志（JSON）或者制表符。

#### 5.16.7 `printRoster()`：为什么用 Stream

```java
private static void printRoster(AfterSalesHub hub) {
    List<String> labels = hub.roster().stream()
            .map(p -> p.role().displayName() + "-" + p.name())
            .toList();
    System.out.println("  参与方（" + labels.size() + "）：" + String.join("、", labels));
}
```

**三行 Stream，做了四件事：**

1. `roster()` 拿到不可变副本；
2. `map` 把每个参与者转成 `客服-小王` 这种标签；
3. `toList()` 收集（返回不可变列表）；
4. `String.join("、", ...)` 拼接。

**为什么不用 for 循环？**

可以：

```java
List<String> labels = new ArrayList<>();
for (Participant p : hub.roster()) {
    labels.add(p.role().displayName() + "-" + p.name());
}
```

两种写法行数差不多。选 Stream 的理由是**意图更集中**：
"把每个元素映射成一个字符串，然后收集" —— 这个语义 Stream 表达得更直接。

**但这不是"Stream 一定更好"。** 如果 `map` 里需要 `if-else` 或者提前 `break`，
for 循环会更清楚。

> **选择标准：这段代码的意图是"变换一个集合"还是"走一遍流程"？**
> 前者用 Stream，后者用循环。

---

## 6. 执行流程：六段 Demo 逐步拆解

六段 Demo 一共打印 125 行。本节不是把 125 行复制一遍就完事，而是**每一段都回答三个问题**：

1. 入口那一行 `say(...)` 进去之后，依次经过了哪几个方法；
2. 每一次经过，哪个字段的值变了；
3. 输出的行序，是被什么决定的。

> **读这一节的正确方式：** 把 `Client.java` 和 `AfterSalesHub.java` 打开放在旁边，
> 每读一段就对照一次源码。本节出现的每一个行号、每一个变量取值，
> 都能在源码里找到对应位置。

### 6.1 为什么六段 Demo 都要先跑一遍 `build()`

`main()` 只有七行：

```java
public static void main(String[] args) {
    demoOneRoutingToOne();
    demoTwoStockShortage();
    demoThreeRefund();
    demoFourEscalation();
    demoFiveClose();
    demoSixIsolation();
    summary();
}
```

前五段各自调用一次 `build()`，第六段自己手工搭建（因为它需要一个会炸的通知器）。

**为什么不共用一个 `Team`？**

因为**每段 Demo 都要从"一张全新的工单"开始**。如果共用，第一段关闭了工单，后面五段全都发不出消息（`say()` 会抛 `IllegalStateException`）。

**Demo 之间不共享状态，是演示代码的基本纪律。** 否则读者看到的输出依赖于"上一段做了什么"，无法单独理解任何一段。

`build()` 八行：

```java
private static Team build() {
    AfterSalesHub hub = new AfterSalesHub("AS-2024-0917");
    Customer customer = new Customer("李女士", hub);
    Agent agent = new Agent("小王", hub);
    Warehouse warehouse = new Warehouse("华东仓", hub);
    Finance finance = new Finance("小赵", hub);
    Supervisor supervisor = new Supervisor("张主管", hub);
    Notifier notifier = new Notifier("站内信网关", hub);

    hub.join(customer);
    hub.join(agent);
    hub.join(warehouse);
    hub.join(finance);
    hub.join(supervisor);
    hub.join(notifier);
    return new Team(hub, customer, agent, warehouse, finance, supervisor, notifier);
}
```

**拆成三步看。**

**第一步：造中枢。**

```java
AfterSalesHub hub = new AfterSalesHub("AS-2024-0917");
```

此刻中枢的六个字段取值：

| 字段 | 取值 | 说明 |
|---|---|---|
| `ticketId` | `"AS-2024-0917"` | 构造参数，`strip()` 过 |
| `participants` | `[]`（空 `ArrayList`） | 还没人加入 |
| `byRole` | `{}`（空 `EnumMap`） | 还没人加入 |
| `failures` | `[]`（空 `ArrayList`） | 还没失败过 |
| `assignee` | `Role.AGENT` | 字段初始值 |
| `delivered` | `0` | 默认值 |
| `closed` | `false` | 默认值 |

**注意第四个字段 `assignee` 已经有值了，但 `byRole` 是空的。**

这意味着：如果此刻有客户发言，`route()` 会返回 `List.of(Role.AGENT)`，然后 `deliver()` 里 `byRole.get(Role.AGENT)` 返回 `null`，打印"角色「客服」尚未加入，跳过"。

**中枢不检查"负责方在不在场"，它只负责"把人没来这件事说出来"。** 这是刻意的：如果 `route()` 里判断"客服不在就报错"，那么"客户先于客服加入"这个完全正常的初始化顺序就会变成异常。

**第二步：造六个参与者，每个都传入同一个 `hub`。**

```java
Customer customer = new Customer("李女士", hub);
```

六个构造函数长得一模一样，都是这三件事：

```java
if (name == null || name.isBlank()) {
    throw new IllegalArgumentException("...不能为空白");
}
this.name = name.strip();
this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
```

**这一行 `this.hub = Objects.requireNonNull(hub, ...)` 是整个模式的地基。**

因为 `Participant` 接口的三个 default 方法都要读 `hub()`：

```java
default void say(MessageKind kind, String text) {
    hub().say(this, kind, text);
}
```

如果 `hub` 是 `null`，`say()` 就会 NPE —— 而且是**在第一句业务代码执行时**才 NPE，离构造点很远。`requireNonNull` 把这个错误提前到了构造点，堆栈里能直接看到是哪一行 `new` 出的问题。

**为什么不在 `Participant` 里加一个 `setHub(...)`？**

原项目就是这么做的（`protected ChatRoomMediator mediator` + 可写），探针 6 演示了后果：子类随时能把发言通道换掉，于是"我在这张名单里，我的话却跑到另一个房间"。

**"必须传 hub 才能构造"这个约束，把"参与者没有中枢"这个状态从类型系统里彻底删掉了。**

**第三步：六次 `join()`，顺序是刻意的。**

```java
hub.join(customer);      // 1
hub.join(agent);         // 2
hub.join(warehouse);     // 3
hub.join(finance);       // 4
hub.join(supervisor);    // 5
hub.join(notifier);      // 6
```

**这个顺序决定了 `participants` 的顺序，也就决定了 Demo 5 广播时的输出顺序。**

验证一下。Demo 5 的 `close()` 由客服发起，广播名单是"遍历 `participants` 排除发起方"：

```text
participants = [客户-李女士, 客服-小王, 仓库-华东仓, 财务-小赵, 主管-张主管, 通知器-站内信网关]
排除 客服-小王
→ [客户-李女士, 仓库-华东仓, 财务-小赵, 主管-张主管, 通知器-站内信网关]
```

对照 Demo 5 的真实输出：

```text
        [李女士] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [华东仓] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [小赵] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [张主管] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [站内信网关] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
```

**五个收件人，顺序完全一致。**

**但 `join()` 的顺序不影响 `route()` 的收件人顺序。** 因为 `route()` 返回的是 `List.of(...)`，顺序由 `List.of` 的参数顺序决定：

```java
case STOCK_SHORTAGE -> from == Role.WAREHOUSE
        ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)     // 顺序：客服 → 财务 → 客户
        : List.of();
```

对照 Demo 2 的缺货输出：

```text
        [小王] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小赵] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
```

**顺序是 小王 → 小赵 → 李女士，即 客服 → 财务 → 客户，和 `List.of` 的参数顺序一致，和 `join` 顺序无关。**

这条区别很重要，后面 §6.8 会专门总结：**同一份代码里存在三种不同的"顺序来源"**。

**`Team` 这个 record 是干什么的？**

```java
private record Team(AfterSalesHub hub, Customer customer, Agent agent,
                    Warehouse warehouse, Finance finance, Supervisor supervisor,
                    Notifier notifier) {
}
```

它只是一个"打包箱"，让 `demoTwoStockShortage()` 里能写：

```java
team.customer().say(MessageKind.QUESTION, "...");
team.agent().say(MessageKind.ANSWER, "...");
```

而不必写：

```java
Customer customer = new Customer(...);
Agent agent = new Agent(...);
// ... 六个局部变量
customer.say(...);
agent.say(...);
```

**少掉的不是六行声明，而是"每段 Demo 开头都有六行样板代码"这个视觉噪音。**

> `record` 在这里是纯数据结构，不是设计模式的一部分。
> **不要因为用了 `record` 就以为中介者模式"必须"有它** —— 换成六个局部变量，
> 或者换成一个 `List<Participant>`，模式一点也不会变。

### 6.2 Demo 1：一句话，一个人收到

完整输出（含抬头一行）：

```text
[Demo 1：客户的一句话，只该到一个人手里]
  参与方（6）：客户-李女士、客服-小王、仓库-华东仓、财务-小赵、主管-张主管、通知器-站内信网关
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  当前负责方：客服
  只有 1 个人收到。原项目的 sendMessage 会发给除发送者外的所有人（5 个）
```

前两行是 `section(...)` 和 `printRoster(...)` 打的，最后两行是 Demo 自己的总结。

**真正属于"中介者跑了一遍"的，只有中间五行。**

**逐步追踪这一句：**

```java
team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
```

**第 1 步：进入 `Participant.say` 这个 default 方法。**

```java
default void say(MessageKind kind, String text) {
    hub().say(this, kind, text);
}
```

进参：`this` = 李女士这个 `Customer` 对象，`kind` = `QUESTION`，`text` = `"耳机左耳没声音，能退吗？"`。

出参：无。**这一行就是一次转发，没有任何判断。**

**第 2 步：进入 `AfterSalesHub.say`。**

```java
@Override
public void say(Participant from, MessageKind kind, String text) {
    Objects.requireNonNull(from, "发送方不能为 null");
    Objects.requireNonNull(kind, "消息类型不能为 null");
    if (closed) {
        throw new IllegalStateException(
                "工单 " + ticketId + " 已关闭，" + from.name() + " 不能再发言");
    }

    Message message = new Message(from.role(), kind, text);
    List<Role> targets = route(from.role(), kind);

    System.out.println("  [" + label(from) + "] " + kind.label() + "：" + message.text());

    if (targets.isEmpty()) {
        System.out.println("      路由未命中：角色「" + from.role().displayName()
                + "」不能发送「" + kind.label() + "」，消息丢弃");
        return;
    }
    deliver(message, targets);
}
```

**四步，顺序不可换：**

| 序 | 做什么 | 此刻的值 |
|---|---|---|
| 1 | `requireNonNull` ×2 | `from` = 李女士，`kind` = `QUESTION`，都非 null |
| 2 | `if (closed)` | `closed` = `false`，放行 |
| 3 | `new Message(...)` | `from` = `Role.CUSTOMER`，`kind` = `QUESTION`，`text` = `"耳机左耳没声音，能退吗？"` |
| 4 | `route(Role.CUSTOMER, QUESTION)` | 返回 `List.of(Role.AGENT)` |

**为什么校验要放在 `new Message` 之前？**

因为 `Message` 的构造函数会校验 `text`（非空白）。如果工单已关闭，先构造 `Message` 就等于"为一个注定失败的消息做了一次对象分配 + 一次校验"，纯浪费。

更实际的理由：**如果校验在构造之后，那么"已关闭"和"正文空白"同时成立时，抛出的异常是"正文空白"。** 用户明明是在一个已关闭的工单上发言，却收到"正文不能为空白"的报错 —— 这个报错是错的。

> **校验顺序决定报错顺序。报错顺序决定用户能不能一次改对。**
> 规则：**先检查"完全无法挽回"的条件**（工单关闭了，怎么改都没用），
> **再检查"改一下就能过"的条件**（正文空白，补上就行）。

**第 3 步：`route()` 怎么算出 `List.of(Role.AGENT)`。**

```java
private List<Role> route(Role from, MessageKind kind) {
    return switch (kind) {
        case QUESTION -> from == Role.CUSTOMER
                ? List.of(assignee)
                : List.of();
        ...
    };
}
```

进参 `from = Role.CUSTOMER`、`kind = QUESTION`，命中第一行。

内层三元：`from == Role.CUSTOMER` 为 `true`，所以返回 `List.of(assignee)`。

此刻 `assignee` = `Role.AGENT`（初始值），所以返回 `List.of(Role.AGENT)` —— **一个只有一个元素的不可变列表**。

**这里是全项目最关键的一行代码：**

```java
case QUESTION -> from == Role.CUSTOMER ? List.of(assignee) : List.of();
```

**它读了一个字段。** 所以 `route()` 不是纯函数 —— 它的输出依赖于中枢的当前状态。

**"客户提问发给谁"这个问题，答案不是一个常量，而是一个变量。** 这就是 Demo 4（升级）能成立的全部技术基础。

**第 4 步：打印发送方那一行。**

```java
System.out.println("  [" + label(from) + "] " + kind.label() + "：" + message.text());
```

分解：

| 片段 | 取值 | 结果 |
|---|---|---|
| `label(from)` | `role().displayName() + "-" + name()` | `"客户-李女士"` |
| `kind.label()` | `QUESTION` 的 `label` 字段 | `"提问"` |
| `message.text()` | 正文 | `"耳机左耳没声音，能退吗？"` |

拼出来：

```text
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
```

**注意：这一行是"发送成功"的日志，不是"投递成功"的日志。** 它打在 `deliver()` 之前，意味着即使后面所有收件人都失败，这一行也会出现。

**为什么先打发送方？**

因为读日志的人需要先知道"谁说了什么"，再知道"谁收到了"。反过来读会很别扭：

```text
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
```

**日志的顺序应该和人的思维顺序一致。**

**第 5 步：进入 `deliver()`。**

```java
private void deliver(Message message, List<Role> targets) {
    System.out.println("      路由 → " + describeTargets(targets));

    for (Role role : targets) {
        Participant target = byRole.get(role);
        if (target == null) {
            System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
            continue;
        }
        try {
            target.receive(message);
            delivered++;
        } catch (RuntimeException e) {
            String record = target.name() + " 投递失败：" + e.getClass().getSimpleName()
                    + "：" + e.getMessage();
            failures.add(record);
            System.out.println("      ! 已隔离：" + record);
        }
    }
}
```

**第一件事是打印路由，不是投递。**

`describeTargets(List.of(Role.AGENT))` 的实现：

```java
private String describeTargets(List<Role> targets) {
    List<String> parts = new ArrayList<>();
    for (Role role : targets) {
        Participant participant = byRole.get(role);
        parts.add(participant == null
                ? role.displayName() + "（未加入）"
                : label(participant));
    }
    return String.join("、", parts);
}
```

`byRole.get(Role.AGENT)` = 小王，所以 `parts = ["客服-小王"]`，`join` 结果 `"客服-小王"`。

打印：

```text
      路由 → 客服-小王
```

**注意：`describeTargets` 也查了一次 `byRole`，紧接着循环里 `byRole.get(role)` 又查了一次。**

这是重复劳动吗？是。为什么不合并？

因为 `describeTargets` 的职责是"把角色列表翻译成人类可读的字符串"，它需要知道"谁没加入"才能标 `（未加入）`。如果把它合并进循环，代码会变成：

```java
// 反例：把打印拆进循环
System.out.print("      路由 → ");
for (Role role : targets) { ... }        // 边打印边投递
System.out.println();
```

**问题在于：一旦"打印路由"和"投递"混在一起，投递给第一个人的输出就会插在"路由 →"这一行的中间。**

真实输出里，路由行是完整的一行，**后面**才是每个收件人的"收到"。这种"先声明去向，再逐个执行"的日志结构，是刻意保持的。

> **一次循环做一件事。** 想让"声明"和"执行"分开，就写两个循环。
> 多查一次 `EnumMap` 的成本是纳秒级，多读一遍嵌套循环的成本是分钟级。

**第 6 步：循环体第一次（也是唯一一次）执行。**

`role = Role.AGENT`，`target = byRole.get(Role.AGENT)` = 小王。

`target` 非 null，进入 `try`：

```java
target.receive(message);
```

**第 7 步：进入 `Agent.receive`。**

```java
@Override
public void receive(Message message) {
    System.out.println("        [" + name + "] 收到 " + message.describe());
    switch (message.kind()) {
        case QUESTION -> System.out.println("        [" + name + "] 客服：接单，先看一眼历史记录。");
        case STOCK_SHORTAGE -> System.out.println("        [" + name + "] 客服：收到缺货，这单走退款。");
        case ESCALATION -> System.out.println("        [" + name + "] 客服：抄送已阅，这单不再由我跟。");
        case ARCHIVE -> System.out.println("        [" + name + "] 从我的待办里移除。");
    }
}
```

第一行 `message.describe()`：

```java
public String describe() {
    return from.displayName() + " 的" + kind.label() + "：" + text;
}
```

拼出 `"客户 的提问：耳机左耳没声音，能退吗？"`。加上前缀：

```text
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
```

然后 `switch (message.kind())` 命中 `case QUESTION`：

```text
        [小王] 客服：接单，先看一眼历史记录。
```

**两行输出，一行是"收到什么"，一行是"我要做什么"。**

**这个两行结构在六个角色里完全一致。** 不是巧合 —— 它对应 `receive()` 的两个固定职责：

1. **留痕**：记录我收到了什么（这一行谁都会打）；
2. **反应**：根据消息类型做对应动作（这一行每个角色不同）。

`Notifier` 稍微特殊，它的第二行是"外发短信/站内信"，但结构一样。

**第 8 步：回到 `deliver()`，`delivered++`。**

`delivered` 从 `0` 变成 `1`。

**注意 `delivered++` 在 `target.receive(...)` 之后，不在之前。**

如果写成：

```java
// 反例
delivered++;
target.receive(message);
```

那么 `received()` 抛异常时，计数已经加了 —— 但它其实没收到。**计数器的位置决定了它是"投递尝试"还是"投递成功"。**

**第 9 步：循环结束，回到 `say()`，回到 `Participant.say`，回到 Demo 1。**

Demo 1 剩下的两行：

```java
System.out.println("  当前负责方：" + team.hub().assignee().displayName());
System.out.println("  只有 1 个人收到。原项目的 sendMessage 会发给除发送者外的所有人（5 个）");
```

第一行读 `assignee()`，返回 `Role.AGENT`，`displayName()` = `"客服"`：

```text
  当前负责方：客服
```

第二行是**手工写的总结文案**，不是程序算出来的。

**这里有一个细节值得警惕：** 这个"1"是常量字符串，如果哪天 `route()` 改成给两个人，输出会撒谎。

**为什么不做成自动统计？**

因为 Demo 的目的是"让读者一眼看到对比"，而自动统计会让代码多出五行：

```java
// 不写的原因：为了这五行，Demo 1 会从 10 行变成 15 行
int count = 0;
for (Participant p : team.hub().roster()) { ... }
```

**但代价是这个数字可能失真。**

> **演示代码里的硬编码数字，必须在文档里标明"这是手写的"。**
> 一旦读者以为它是程序算出来的，文档就失真了。

Demo 1 里这个 `1` 是可以手算验证的：`route()` 返回一个元素的 list，`deliver()` 循环一次。**只要读者自己验算过，硬编码就不算欺骗。**

**Demo 1 的完整状态变化：**

| 时刻 | `assignee` | `delivered` | `closed` | `failures` |
|---|---|---|---|---|
| `build()` 之后 | `AGENT` | 0 | `false` | 空 |
| `say()` 返回后 | `AGENT`（没变） | **1** | `false` | 空 |

**只有 `delivered` 变了。** 这正是"客户提个问"应该产生的影响：一条消息，一次投递，没有状态迁移。

### 6.3 Demo 2：一句缺货，三方协同

完整输出：

```text
[Demo 2：仓库一句「缺货」，中枢同时通知三方]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  [客服-小王] 答复：我查一下库存，稍等两分钟。
      路由 → 客户-李女士
        [李女士] 收到 客服 的答复：我查一下库存，稍等两分钟。
        [李女士] 客户：好，那我等结果。
  [仓库-华东仓] 缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
      路由 → 客服-小王、财务-小赵、客户-李女士
        [小王] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小王] 客服：收到缺货，这单走退款。
        [小赵] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小赵] 财务：冻结原发票，准备全额退款。
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
  这条消息由仓库发出，但客服、财务、客户都收到了
  三个收件人不是仓库决定的，是中枢的 route() 决定的
  通知器没有收到：它只外发升级与归档，缺货这种内部事实不外发
```

**三次 `say()`，三次独立的"发送—路由—投递"。**

**三句话，三种收件人规模：**

| 序 | 发送方 | 类型 | `route()` 返回 | 投递次数 |
|---|---|---|---|---|
| 1 | 客户 | `QUESTION` | `[AGENT]` | 1 |
| 2 | 客服 | `ANSWER` | `[CUSTOMER]` | 1 |
| 3 | 仓库 | `STOCK_SHORTAGE` | `[AGENT, FINANCE, CUSTOMER]` | 3 |

累计 `delivered` = 1 + 1 + 3 = **5**。

**第 1 句和第 2 句是"一问一答"，和第 1 段 Demo 里的逻辑一样，只是方向反过来。**

重点看第 2 句的路由：

```java
case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
        ? List.of(Role.CUSTOMER)
        : List.of();
```

`from = Role.AGENT`，命中 `true` 分支，返回 `List.of(Role.CUSTOMER)`。

**这个 `||` 是必须的。** 如果只写 `from == Role.AGENT`，那么主管在 Demo 4 里接手之后回复客户（`Supervisor` 发 `ANSWER`）就会被丢弃 —— 客户永远收不到主管的答复。

**而 `from == Role.CUSTOMER` 时必须返回空**：客户不能"答复"自己。

验证一下反过来的情况：如果客户调用 `customer.say(MessageKind.ANSWER, "...")`，`route()` 返回 `List.of()`，`say()` 里：

```java
if (targets.isEmpty()) {
    System.out.println("      路由未命中：角色「客户」不能发送「答复」，消息丢弃");
    return;
}
```

**客户根本没法"回答"别人。这是规则表强制的，不是 `Customer` 类里写了什么判断。**

**第 3 句是本段的重点。**

```java
case STOCK_SHORTAGE -> from == Role.WAREHOUSE
        ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
        : List.of();
```

三个元素，顺序：`AGENT` → `FINANCE` → `CUSTOMER`。

`deliver()` 循环三次：

**第一次：** `role = AGENT`，`target` = 小王，`receive()` 命中 `case STOCK_SHORTAGE`：

```text
        [小王] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小王] 客服：收到缺货，这单走退款。
```

**第二次：** `role = FINANCE`，`target` = 小赵，`receive()` 命中 `case STOCK_SHORTAGE`：

```text
        [小赵] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小赵] 财务：冻结原发票，准备全额退款。
```

**第三次：** `role = CUSTOMER`，`target` = 李女士，`receive()` 命中 `case STOCK_SHORTAGE` —— **等等，`Customer.receive()` 里没有这个分支。**

再看一遍：

```java
switch (message.kind()) {
    case ANSWER -> System.out.println("        [" + name + "] 客户：好，那我等结果。");
    case REFUND_CONFIRMED -> System.out.println("        [" + name + "] 客户：钱到账了，问题解决。");
    case ARCHIVE -> System.out.println("        [" + name + "] 客户：会话结束，给这次服务打 5 星。");
}
```

三个分支，没有 `STOCK_SHORTAGE`。所以第三次循环只打印了 `receive()` 的第一行：

```text
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
```

**就这一行，没有第二行。**

**这就是"收到但无动作"在输出上的样子：一行而不是两行。**

对照真实输出，第三次确实只有一行 —— 而前两次各有两行。**这个差异不是 bug，是设计。**

**为什么客户不该有动作？**

因为客户对"缺货"这件事**没有处置权**。他能做的只有等：客服会决定退款，财务会执行退款。如果给客户加一句"那就退款吧"，那句话就是假的 —— 退款不是他这句话触发的。

**"收到但无动作"是六种 `receive()` 里最重要的一种，因为它证明了一件事：**

> **中介者只保证"消息送到"，不保证"收件方会做什么"。**
> 收件方的反应完全由自己的 `switch` 决定，中枢不参与。

**如果中枢参与会怎样？**

反例：让中枢在投递后检查"该角色是否处理了"。

```java
// 反例：中枢越界
target.receive(message);
if (!target.hasHandled(kind)) {
    throw new IllegalStateException("客服没处理缺货消息");
}
```

**这会让中枢知道"每个角色的业务规则"，它就从"路由器"变成了"流程引擎"。** 而流程引擎的复杂度是另一个数量级 —— 一旦走了这条路，`AfterSalesHub` 会膨胀到几千行。

**`route()` 只回答"该给谁"，绝不回答"给完之后会怎样"。这条边界是中介者能保持 375 行的根本原因。**

**最后三行输出是本段的阅读提示：**

```text
  这条消息由仓库发出，但客服、财务、客户都收到了
  三个收件人不是仓库决定的，是中枢的 route() 决定的
  通知器没有收到：它只外发升级与归档，缺货这种内部事实不外发
```

第三行特别值得说：**通知器没有收到，不是因为它被排除了，而是因为 `STOCK_SHORTAGE` 的收件人列表里根本没写它。**

```java
List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)      // 三个，没有 NOTIFIER
```

**"不被通知"这件事，在代码里没有任何一行。它是"列表里没有那一项"的沉默结果。**

> **这是中介者模式最容易被忽略的好处：**
> "谁不该收到"这个知识，不需要写任何代码 —— 它自动地由"谁该收到"这个列表定义。
> 而在原项目里（广播给除我之外的所有人），"谁不该收到"是没法表达的。

**Demo 2 的完整状态变化：**

| 时刻 | `assignee` | `delivered` | `closed` | `failures` |
|---|---|---|---|---|
| `build()` 之后 | `AGENT` | 0 | `false` | 空 |
| 客户提问后 | `AGENT` | 1 | `false` | 空 |
| 客服答复后 | `AGENT` | 2 | `false` | 空 |
| 仓库上报后 | `AGENT` | **5** | `false` | 空 |

**`assignee` 全程没变。** 缺货上报不会改变"谁负责这张单" —— 该客服管还是客服管。

**这是业务判断，不是技术判断。** 如果业务规则是"缺货必须由主管介入"，那 `STOCK_SHORTAGE` 分支就该同时改 `assignee`：

```java
// 假设业务要求：缺货自动升级给主管
case STOCK_SHORTAGE -> {
    List<Role> target = List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER);
    if (from == Role.WAREHOUSE) {
        assignee = Role.SUPERVISOR;      // 副作用
    }
    yield from == Role.WAREHOUSE ? target : List.of();
}
```

**但这样一来 `route()` 就不再是"只看输入、不改状态"的方法了。** 打印的路由行会显示旧收件人，而实际投递是按新的算 —— 读者会看不懂输出。

> **如果某个业务动作既要"改状态"又要"投递"，就把它写成 `escalate()` / `close()` 那种独立方法，
> 不要塞进 `route()`。** `route()` 的唯一职责是"给定发送方和类型，返回收件人列表"。

### 6.4 Demo 3：换个角色发言，换一批收件人

完整输出：

```text
[Demo 3：同一张工单，换个角色发言就换一批收件人]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  [仓库-华东仓] 缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
      路由 → 客服-小王、财务-小赵、客户-李女士
        [小王] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小王] 客服：收到缺货，这单走退款。
        [小赵] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小赵] 财务：冻结原发票，准备全额退款。
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
  [财务-小赵] 退款确认：已退 299 元，1-3 个工作日到账
      路由 → 客户-李女士、客服-小王
        [李女士] 收到 财务 的退款确认：已退 299 元，1-3 个工作日到账
        [李女士] 客户：钱到账了，问题解决。
        [小王] 收到 财务 的退款确认：已退 299 元，1-3 个工作日到账
  退款确认只到 客户 + 客服。仓库收不到这条 —— 它不需要知道钱的事
```

**本段标题说的是"换个角色发言换一批收件人"，但其实前两句和 Demo 2 完全一样。**

真正的对比在第三句：

| | Demo 2 第三句 | Demo 3 第三句 |
|---|---|---|
| 发送方 | 仓库 | 财务 |
| 类型 | `STOCK_SHORTAGE` | `REFUND_CONFIRMED` |
| 收件人 | 客服 + 财务 + 客户 | 客户 + 客服 |
| 仓库收到吗 | 不适用（自己发的） | **不收到** |

**两句话的收件人差一个人：仓库。**

而"仓库不收到"这件事，代码里同样没有写 "exclude warehouse"。它是：

```java
case REFUND_CONFIRMED -> from == Role.FINANCE
        ? List.of(Role.CUSTOMER, Role.AGENT)      // 两个，没有 WAREHOUSE
        : List.of();
```

**列表里少写一项，就是这个效果。**

**注意路由行的顺序：`客户-李女士、客服-小王`。**

`List.of(Role.CUSTOMER, Role.AGENT)` —— 客户在前，客服在后。

所以输出顺序是：

```text
        [李女士] 收到 财务 的退款确认：已退 299 元，1-3 个工作日到账
        [李女士] 客户：钱到账了，问题解决。
        [小王] 收到 财务 的退款确认：已退 299 元，1-3 个工作日到账
```

**李女士的两行在前，小王的一行在后。** 而 `join()` 顺序是客户、客服 —— 恰好也是这个顺序，所以这次体现不出差异。

对照 Demo 2 的缺货：`List.of(AGENT, FINANCE, CUSTOMER)` 是客服在前，而 `join()` 顺序是客户在前。**输出是客服在前** —— 这就证明了顺序来源是 `List.of` 而不是 `join`。

> **这个差异是被刻意保留的，因为它是最直接的证据。**
> 如果想让输出顺序和 `join()` 一致，`route()` 就得先把 `List<Role>` 按 `participants` 顺序排序 ——
> 多五六行代码，换来"顺序更自然"。
> **不值。** 因为路由表的顺序本身就是有信息量的：`List.of(AGENT, FINANCE, CUSTOMER)` 读起来就是"先找客服，再找财务，最后告知客户"，
> 这是业务的优先级。打乱它反而丢信息。

**为什么客户收到退款确认会有动作，而收到缺货没有？**

对比 `Customer.receive()` 的两个分支：

```java
case REFUND_CONFIRMED -> System.out.println("        [" + name + "] 客户：钱到账了，问题解决。");
```

vs 缺货（无分支）。

**区别在于"客户能不能自己消化这条消息"。**

- 缺货：客户知道了也没用，他得等客服安排 —— **无动作**；
- 退款确认：客户知道了，这件事对他而言就结束了 —— **有动作（确认收到）**。

**判断标准：这条消息是否意味着"客户这边的一件待办结束了"。**

如果结束了 → 有动作（说出来）；
如果只是"知道了" → 无动作。

**这个标准可以直接套到其他角色上：**

| 角色 | 有动作的消息 | 无动作的消息 | 判断依据 |
|---|---|---|---|
| 客户 | `ANSWER` / `REFUND_CONFIRMED` / `ARCHIVE` | `STOCK_SHORTAGE` / `NOTICE` | 是否结束了一件客户的待办 |
| 客服 | `QUESTION` / `STOCK_SHORTAGE` / `ESCALATION` / `ARCHIVE` | `REFUND_CONFIRMED` / `NOTICE` | 是否需要客服再做一个动作 |
| 仓库 | `ARCHIVE` | 其它 | 仓库只在"关单"时需要解锁库存 |
| 财务 | `STOCK_SHORTAGE` / `ARCHIVE` | 其它 | 财务只在"要退款"和"关单"时需要动作 |

**注意客服对 `REFUND_CONFIRMED` 无动作**，而客户有动作。同一条消息，两个收件人，一个做事一个不做。

**这恰好证明了"角色决定反应"，而不是"消息决定反应"。** 中枢只负责把同一份 `Message` 对象发给两个人，两个人各自 `switch` 出不同结果。

**Demo 3 的完整状态变化：**

| 时刻 | `assignee` | `delivered` | `closed` | `failures` |
|---|---|---|---|---|
| `build()` 之后 | `AGENT` | 0 | `false` | 空 |
| 客户提问后 | `AGENT` | 1 | `false` | 空 |
| 仓库上报后 | `AGENT` | 4 | `false` | 空 |
| 财务确认后 | `AGENT` | **6** | `false` | 空 |

**6 = 1 + 3 + 2。** 每一句的投递次数等于 `route()` 返回列表的长度。

**这个等式的条件是"所有收件人都已加入、都没有抛异常"。** 只要有一个人没加入（Demo 6 的财务）或者抛异常（Demo 6 的短信网关），等号就不成立。

### 6.5 Demo 4：升级之后，同一句话换了一个收件人

**这是六段 Demo 里最重要的一段。** 它演示了中介者模式的"有状态"特性 ——
这是"中介者"和"消息转发工具类"的唯一实质区别。

完整输出：

```text
[Demo 4：升级之后，同一句话换了一个收件人]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  当前负责方：客服
  [客户-李女士] 申请升级：等了 3 天没人处理，要求升级
      负责方：客服 → 主管
      路由 → 主管-张主管、客服-小王、通知器-站内信网关
        [张主管] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [张主管] 主管：接手，先翻一遍上下文再回客户。
        [小王] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [小王] 客服：抄送已阅，这单不再由我跟。
        [站内信网关] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [站内信网关] 外发短信给主管手机：「等了 3 天没人处理，要求升级」
  当前负责方：主管
  [客户-李女士] 提问：到底什么时候能退？
      路由 → 主管-张主管
        [张主管] 收到 客户 的提问：到底什么时候能退？
        [张主管] 主管：客户又追问了，直接答复。
  客户代码一个字没改，收件人从「客服」变成了「主管」
```

**Demo 的代码只有六行：**

```java
Team team = build();

team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
System.out.println("  当前负责方：" + team.hub().assignee().displayName());

team.customer().escalate("等了 3 天没人处理，要求升级");
System.out.println("  当前负责方：" + team.hub().assignee().displayName());

team.customer().say(MessageKind.QUESTION, "到底什么时候能退？");
System.out.println("  客户代码一个字没改，收件人从「客服」变成了「主管」");
```

**两句 `say(QUESTION, ...)` 一字不差，同一行代码，收件人不同。**

**第 1 句：`assignee = AGENT`。**

`route(CUSTOMER, QUESTION)` → `List.of(assignee)` → `List.of(Role.AGENT)`。

输出：

```text
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
```

**第 2 步：`escalate()`。**

```java
default void escalate(String reason) {
    hub().escalate(this, reason);
}
```

进入 `AfterSalesHub.escalate`：

```java
@Override
public void escalate(Participant from, String reason) {
    Objects.requireNonNull(from, "发起方不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已关闭，不能再升级");
    }
    Message message = new Message(from.role(), MessageKind.ESCALATION, reason);

    System.out.println("  [" + label(from) + "] 申请升级：" + message.text());
    System.out.println("      负责方：" + assignee.displayName() + " → " + Role.SUPERVISOR.displayName());
    assignee = Role.SUPERVISOR;
    deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));
}
```

**六步，顺序极其重要：**

| 序 | 代码 | 此刻发生什么 |
|---|---|---|
| 1 | `requireNonNull(from, ...)` | 校验 |
| 2 | `if (closed)` | `closed = false`，放行 |
| 3 | `new Message(CUSTOMER, ESCALATION, reason)` | 造消息 |
| 4 | 打印"申请升级" | **第一行输出** |
| 5 | 打印"负责方：客服 → 主管"，然后 `assignee = SUPERVISOR` | **第二行输出**，然后改状态 |
| 6 | `deliver(message, List.of(SUPERVISOR, AGENT, NOTIFIER))` | 三路投递 |

**第 5 步是核心。** 它先打印**当前值**和**目标值**，再赋值：

```java
System.out.println("      负责方：" + assignee.displayName() + " → " + Role.SUPERVISOR.displayName());
assignee = Role.SUPERVISOR;
```

输出：

```text
      负责方：客服 → 主管
```

**如果先赋值再打印，这一行会变成 "负责方：主管 → 主管"。**

因为 `assignee` 已经被改成了 `SUPERVISOR`，`assignee.displayName()` 就是 `"主管"`。

**这是一个非常容易被写错的顺序问题。** 更隐蔽的写法是把打印放进 `assignee` 的 setter 里 —— 但这里没有 setter，`assignee` 是直接赋值的字段。

> **"打印旧值 → 赋值 → 用新值"这个三步模式，在状态迁移的日志里必须手写。**
> 没有语法能替你保证这个顺序，只能靠"赋值永远在打印之后"这条规则。

**第 6 步：`deliver()` 三路投递。**

`List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER)` → 三个元素。

`describeTargets` 拼出 `"主管-张主管、客服-小王、通知器-站内信网关"`：

```text
      路由 → 主管-张主管、客服-小王、通知器-站内信网关
```

**注意：`assignee` 已经改成 `SUPERVISOR` 了，但这条路由列表是硬编码的 `List.of(...)`，不依赖 `assignee`。**

**为什么这次不用 `assignee`？**

因为升级消息的收件人是固定的三方：主管（接手）、客服（抄送）、通知器（外发）。如果写成 `List.of(assignee, ...)`，由于 `assignee` 此刻已经是 `SUPERVISOR`，结果一样 —— 但语义完全不同。

**硬编码表达的是"升级这条消息永远给这三方"；用 `assignee` 表达的是"给当前负责方" —— 后者在升级场景里是自指的（改完之后自己指自己），会让读者困惑。**

> **一个字段能被用来算路由，不代表每次路由都该用它。**
> `assignee` 只在 `QUESTION` 分支里有意义，因为只有"提问"才需要一个"当前负责方"。

**三路投递的具体过程：**

**第一路：`role = SUPERVISOR`，`target` = 张主管。**

`Supervisor.receive()`：

```java
switch (message.kind()) {
    case ESCALATION -> System.out.println("        [" + name + "] 主管：接手，先翻一遍上下文再回客户。");
    ...
}
```

输出两行：

```text
        [张主管] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [张主管] 主管：接手，先翻一遍上下文再回客户。
```

**第二路：`role = AGENT`，`target` = 小王。**

`Agent.receive()` 命中 `case ESCALATION`：

```text
        [小王] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [小王] 客服：抄送已阅，这单不再由我跟。
```

**"抄送已阅，这单不再由我跟"这句话，是 `ESCALATION` 分支存在的唯一理由。**

如果客服没有这个分支，他就只会打印"收到" —— 那么读者无法从输出看出"客服已经知道这单不归他了"。**而这正是升级时抄送客服的目的：避免他继续处理一张已经转手的单子。**

> **抄送的价值不在"知道"，而在"知道之后停手"。**
> 如果收件方不会因为这条消息改变行为，那这条抄送就是纯噪音。

**第三路：`role = NOTIFIER`，`target` = 站内信网关。**

`Notifier.receive()` 的两个外发分支之一：

```java
case ESCALATION -> System.out.println("        [" + name + "] 外发短信给主管手机：「" + message.text() + "」");
```

输出：

```text
        [站内信网关] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [站内信网关] 外发短信给主管手机：「等了 3 天没人处理，要求升级」
```

**注意第二行里出现了 `主管手机` 这个具体渠道 —— 它是硬编码在 `Notifier.receive()` 里的。**

```java
case ESCALATION -> System.out.println("        [" + name + "] 外发短信给主管手机：「" + message.text() + "」");
case ARCHIVE -> System.out.println("        [" + name + "] 外发站内信给客户：「" + message.text() + "」");
```

**"升级发短信、归档发站内信"这个渠道选择，是通知器自己的知识，不是中枢的知识。**

中枢只知道"升级要给通知器一份"，至于通知器用什么渠道送达，中枢不关心。**这是职责边界的又一次体现：中枢管"谁收到"，通知器管"怎么送达"。**

**第 3 句：`assignee = SUPERVISOR` 之后的同一个 `say(QUESTION, ...)`。**

```java
team.customer().say(MessageKind.QUESTION, "到底什么时候能退？");
```

`route(CUSTOMER, QUESTION)` → `List.of(assignee)` → `List.of(Role.SUPERVISOR)`。

输出：

```text
  [客户-李女士] 提问：到底什么时候能退？
      路由 → 主管-张主管
        [张主管] 收到 客户 的提问：到底什么时候能退？
        [张主管] 主管：客户又追问了，直接答复。
```

**和第一句对比：**

| | 第一句 | 第三句 |
|---|---|---|
| 调用代码 | `say(MessageKind.QUESTION, "...")` | `say(MessageKind.QUESTION, "...")` |
| 发送方 | 客户 | 客户 |
| `assignee` | `AGENT` | `SUPERVISOR` |
| `route()` 返回 | `[AGENT]` | `[SUPERVISOR]` |
| 收件人 | 小王 | 张主管 |

**调用方一行都没改。** 变化的只有中枢内部的一个字段。

**这就是中介者模式最核心的价值，用一句话说：**

> **"这条消息该给谁"这个决策，从"发送方"转移到了"中枢"。**
> 所以当决策依据（`assignee`）变化时，**所有发送方的代码都不需要改**。

**如果不用中介者，这段逻辑会怎么写？**

客户代码得自己判断：

```java
// 反例：客户自己决定发给谁
if (ticket.isEscalated()) {
    supervisor.receive(new Message(...));
} else {
    agent.receive(new Message(...));
}
```

**这一改，客户就依赖了 `Agent` 和 `Supervisor` 两个具体类。**

更进一步：如果业务新增"VIP 客户的提问直接给主管"，客户代码就得再加一个判断。**判断会不断堆积，因为"该给谁"这件事的所有变化都会砸到发送方头上。**

**而中介者把所有这些判断收在一处。** 加规则 = 改 `route()` 的一个 case；加角色 = 加一个枚举值 + 一个 `case`。

**Demo 4 的完整状态变化：**

| 时刻 | `assignee` | `delivered` | `closed` | `failures` |
|---|---|---|---|---|
| `build()` 之后 | `AGENT` | 0 | `false` | 空 |
| 第 1 句提问后 | `AGENT` | 1 | `false` | 空 |
| `escalate()` 之后 | **`SUPERVISOR`** | 4 | `false` | 空 |
| 第 3 句提问后 | `SUPERVISOR` | **5** | `false` | 空 |

**`assignee` 是唯一一个在 Demo 4 里发生迁移的字段。**

而这个迁移只发生在一个地方：

```java
assignee = Role.SUPERVISOR;
```

**全项目搜 `assignee =`，只有这一处。** 这是"状态迁移点唯一"的具体体现 —— 和状态模式里"每个状态自己决定下一个状态"不同，中介者的状态迁移是**中枢自己做的**。

> **为什么不让 `Supervisor` 在 `receive()` 里调 `hub.assignToMe()`？**
> 因为那样"升级改负责方"这个决策就分散到了 `Supervisor` 类里。
> 一旦将来出现第二个"能改负责方"的场景（比如"客户直接指定主管"），两个地方各改一次，很容易漏。
> **把状态迁移收在中枢，同一条业务规则就只有一处实现。**

---

### 6.6 Demo 5：关闭工单，唯一一次真正的广播

完整输出：

```text
[Demo 5：关闭工单 —— 唯一一处真正的广播]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  [客服-小王] 申请关闭工单 AS-2024-0917
      路由 → 客户-李女士、仓库-华东仓、财务-小赵、主管-张主管、通知器-站内信网关
        [李女士] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [李女士] 客户：会话结束，给这次服务打 5 星。
        [华东仓] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [华东仓] 仓库：解除库存锁定，从待办移除。
        [小赵] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [小赵] 财务：退款凭证已归档。
        [张主管] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [张主管] 主管：确认关闭，同意归档。
        [站内信网关] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [站内信网关] 外发站内信给客户：「工单 AS-2024-0917 已由 客服 关闭」
  工单 AS-2024-0917 状态：已关闭
  关闭之后再发言：
    抛出 IllegalStateException：工单 AS-2024-0917 已关闭，李女士 不能再发言
  已关闭 = true，成功投递 6 次
```

**这段输出有 20 行，但 Demo 只有三行代码：**

```java
team.customer().say(MessageKind.QUESTION, "耳机左耳没声音，能退吗？");
team.agent().closeTicket();
// 加一段 try-catch 验证关闭后不能发言
```

第二行 `closeTicket()` 一个调用，产生了 15 行输出（1 行申请 + 1 行路由 + 10 行投递 + 1 行状态 + ...）。

**这是"一次业务动作触发多个下游"的典型形态。** 而它只写在一处：

```java
default void closeTicket() {
    hub().close(this);
}
```

**`close()` 的六步：**

```java
@Override
public void close(Participant from) {
    Objects.requireNonNull(from, "发起方不能为 null");
    if (closed) {
        throw new IllegalStateException("工单 " + ticketId + " 已经关闭过了");
    }
    Message message = new Message(from.role(), MessageKind.ARCHIVE,
            "工单 " + ticketId + " 已由 " + from.role().displayName() + " 关闭");

    System.out.println("  [" + label(from) + "] 申请关闭工单 " + ticketId);

    List<Role> targets = new ArrayList<>();
    for (Participant participant : participants) {
        if (participant != from) {
            targets.add(participant.role());
        }
    }
    deliver(message, targets);
    closed = true;
    System.out.println("  工单 " + ticketId + " 状态：已关闭");
}
```

| 序 | 代码 | 作用 |
|---|---|---|
| 1 | `requireNonNull` | 校验 |
| 2 | `if (closed)` | 已关闭不能再关（幂等保护） |
| 3 | `new Message(..., ARCHIVE, "工单 ... 已由 客服 关闭")` | 正文里拼好了工单号和关闭人 |
| 4 | 打印"申请关闭工单 AS-2024-0917" | 第一行输出 |
| 5 | 遍历 `participants` 排除 `from`，收集 `targets` | **广播名单** |
| 6 | `deliver(message, targets)` → `closed = true` → 打印状态 | 投递，然后置状态 |

**第 3 步的正文为什么在构造时拼好？**

```java
"工单 " + ticketId + " 已由 " + from.role().displayName() + " 关闭"
```

因为它要出现在 10 行输出里（5 个收件人 × 2 行，通知器那两行里也有）。如果每个收件人自己拼，那就得让 `Participant` 知道 `ticketId` —— 但参与者不该知道工单号，那是中枢的内部状态。

> **把"所有收件人都需要的信息"预先放进 `Message`，是避免收件方反查中枢的唯一办法。**
> 一旦某个 `receive()` 里去 `hub().ticketId()`，那个角色就和"具体的中介者实现"绑死了 ——
> 因为 `CollaborationHub` 接口里没有 `ticketId()`。

**第 5 步是本节的重点。** 广播名单是**算出来的**，不是写死的：

```java
List<Role> targets = new ArrayList<>();
for (Participant participant : participants) {
    if (participant != from) {
        targets.add(participant.role());
    }
}
```

`participants` 的顺序 = `join()` 顺序：

```text
[客户-李女士, 客服-小王, 仓库-华东仓, 财务-小赵, 主管-张主管, 通知器-站内信网关]
```

`from` = 小王（`Agent` 对象），排除他：

```text
[客户-李女士, 仓库-华东仓, 财务-小赵, 主管-张主管, 通知器-站内信网关]
```

**对照路由行：**

```text
      路由 → 客户-李女士、仓库-华东仓、财务-小赵、主管-张主管、通知器-站内信网关
```

**五个，顺序完全一致。**

**注意这里是 `participant != from`，用的是引用比较。**

为什么不用 `participant.role() != from.role()`？

因为引用比较更严格。如果传进来的 `from` 是一个**没有 `join` 过的**、但角色相同的 `Agent` 实例：

```java
Agent 外来的 = new Agent("路人", hub);
外来的.closeTicket();
```

- `participant != from` → 名单里没有人等于"外来的"，所以**所有 6 个人都收到**（包括小王）；
- `participant.role() != from.role()` → 小王会被排除，因为角色都是 `AGENT`。

**哪一种对？** 严格说是第一种"更安全"（不误伤），但它暴露了另一个问题：**一个没加入的人也能调 `close()`。**

**这个洞是真的存在。** `close()` 只校验 `from` 非 null 和工单未关，没校验"你在名单里"。

**要不要补？** 本项目**没补**，理由是：

1. `close()` 是内部方法，只通过 `Participant.closeTicket()` 暴露，调用方一定是参与者自己；
2. 如果补校验，就要写：
   ```java
   if (!participants.contains(from)) {
       throw new IllegalArgumentException("发起方不在参与者名单里");
   }
   ```
   这是第 7 行代码，而它挡住的是一个**只有演示代码才可能犯的错**（真实系统里 `from` 永远是 `this`）。

> **防御性校验的边界：挡住"真实调用方会犯的错"，不挡"理论上可能但实际不会发生"的错。**
> 后者会让每个方法都长出五六行前置检查，最终没人愿意读。

**但如果这个类要给别人用（发布成库），这个校验就该加上。** 判断标准是"调用方是不是你能控制的代码"。

**第 6 步的顺序：`deliver()` 在前，`closed = true` 在后。**

```java
deliver(message, targets);
closed = true;
```

**如果反过来写：**

```java
// 反例
closed = true;
deliver(message, targets);
```

会怎样？

`deliver()` 内部对每个收件人调 `receive()`。如果某个收件人在处理归档时**反过来调用了中枢的 `say()`**（比如 `Agent.receive()` 里想"确认一下"，发一条 `NOTICE`），此时 `closed` 已经是 `true`，那个 `say()` 会抛 `IllegalStateException` —— **而这个异常会被 `deliver()` 的 try-catch 捕获，记成"投递失败"。**

**结果就是：一个正常的"关单时的附带动作"被记成了投递故障。**

而按现在的顺序（先投递再置状态），关单过程中中枢还是"活的"，收件人可以安全地做附带动作；只有 `close()` 返回之后，中枢才变成只读。

> **规则：状态标记要放在"它会阻止的操作"全部做完之后。**
> 这是"先干活，再关门"。

**第 7 步：关闭之后再发言。**

```java
try {
    team.customer().say(MessageKind.QUESTION, "等等，我还有个问题");
} catch (IllegalStateException e) {
    System.out.println("    抛出 IllegalStateException：" + e.getMessage());
}
```

`say()` 的第 3 步：

```java
if (closed) {
    throw new IllegalStateException(
            "工单 " + ticketId + " 已关闭，" + from.name() + " 不能再发言");
}
```

输出：

```text
    抛出 IllegalStateException：工单 AS-2024-0917 已关闭，李女士 不能再发言
```

**注意消息里的两个信息：工单号 + 谁在发言。**

为什么要带"李女士"？

因为 `say()` 是被 `Participant.say()` 转发的，堆栈里看不出是哪个参与者。**如果只写"工单已关闭，不能再发言"，运维得猜是谁在重试。**

> **异常消息里要包含"能定位到具体对象"的信息。**
> 这里的定位信息是"李女士"（人名）而不是 `Customer@1a2b3c`（对象地址）——
> 因为运维看得懂人名，看不懂哈希。

**第 8 步：核对投递计数。**

```text
  已关闭 = true，成功投递 6 次
```

**6 是怎么来的？**

| 动作 | 收件人数 | 累计 |
|---|---|---|
| 客户提问（`QUESTION`） | 1（客服） | 1 |
| 关单广播（`ARCHIVE`） | 5（除客服外全部） | **6** |

**验证：1 + 5 = 6 ✓**

**注意"关闭后那次失败的发言"没有增加计数。** 因为它在 `say()` 的校验阶段就抛异常了，根本没走到 `deliver()`。

**这是计数器可信的前提：它只在 `receive()` 成功返回之后加一。**

**如果 Demo 5 里有一个参与者抛异常会怎样？**

假设通知器的 `receive()` 抛了 `IllegalStateException`，那么：

- `deliver()` 捕获 → 记进 `failures` → 打印 `! 已隔离：...`；
- `delivered` 不加；
- 循环继续，后面的收件人照常收到。

那么最后一行会变成 `成功投递 5 次`（而不是 6）。**数字会自动反映现实 —— 因为它不是硬编码的（和 Demo 1 里那个手写的"1"不同）。**

> **对比 Demo 1 的 `"只有 1 个人收到"` 和 Demo 5 的 `deliveredCount()`：**
> 前者是手写文案，后者是真实计数。
> 演示代码里两者都可以有，但**文档必须说清哪个是哪个**。

**Demo 5 的完整状态变化：**

| 时刻 | `assignee` | `delivered` | `closed` | `failures` |
|---|---|---|---|---|
| `build()` 之后 | `AGENT` | 0 | `false` | 空 |
| 客户提问后 | `AGENT` | 1 | `false` | 空 |
| `close()` 投递中 | `AGENT` | 1 → 6 | `false` | 空 |
| `close()` 返回后 | `AGENT` | 6 | **`true`** | 空 |
| 关闭后发言（异常） | `AGENT` | 6（不变） | `true` | 空 |

**`assignee` 没变 —— 关单不改负责方。** 这也是业务判断：一张已关闭的工单，"谁负责"已经没有意义了，但保留它能让"谁最后处理的"这个信息可查。

### 6.7 Demo 6：一家炸了，其他家照常

**这是六段里唯一一段不调用 `build()` 的。** 它需要：

1. 财务不加入（演示"角色未到场"）；
2. 通知器换成会抛异常的 `BrokenNotifier`（演示"异常隔离"）。

完整输出：

```text
[Demo 6：一家炸了，其他家照常；没定义路由的消息被拦下]
  参与方（4）：客户-王先生、客服-小李、仓库-华南仓、通知器-短信网关（故障）
  —— 仓库试图发一条「提问」（路由表里没有这条边）——
  [仓库-华南仓] 提问：有人能告诉我这单寄回哪里吗？
      路由未命中：角色「仓库」不能发送「提问」，消息丢弃
  —— 缺货上报：财务没加入，跳过；其余照常 ——
  [仓库-华南仓] 缺货上报：EP-2024 无线耳机，华南仓缺货
      路由 → 客服-小李、财务（未加入）、客户-王先生
        [小李] 收到 仓库 的缺货上报：EP-2024 无线耳机，华南仓缺货
        [小李] 客服：收到缺货，这单走退款。
      角色「财务」尚未加入，跳过
        [王先生] 收到 仓库 的缺货上报：EP-2024 无线耳机，华南仓缺货
  —— 关闭工单：短信网关故障，其他人照常收到 ——
  [客服-小李] 申请关闭工单 AS-2024-0918
      路由 → 客户-王先生、仓库-华南仓、通知器-短信网关（故障）
        [王先生] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
        [王先生] 客户：会话结束，给这次服务打 5 星。
        [华南仓] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
        [华南仓] 仓库：解除库存锁定，从待办移除。
      ! 已隔离：短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
  工单 AS-2024-0918 状态：已关闭
  投递失败记录（1 条）：
    · 短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
```

**搭建过程（和 `build()` 的唯一区别）：**

```java
AfterSalesHub hub = new AfterSalesHub("AS-2024-0918");
Customer customer = new Customer("王先生", hub);
Agent agent = new Agent("小李", hub);
Warehouse warehouse = new Warehouse("华南仓", hub);
BrokenNotifier broken = new BrokenNotifier(hub);

hub.join(customer);
hub.join(agent);
hub.join(warehouse);
hub.join(broken);      // 没有 finance，没有 supervisor
```

**四个人，不是六个。**

`BrokenNotifier` 是 `Client` 的私有内部类：

```java
private static final class BrokenNotifier implements Participant {

    private final CollaborationHub hub;

    BrokenNotifier(CollaborationHub hub) {
        this.hub = hub;
    }

    @Override
    public Role role() {
        return Role.NOTIFIER;
    }

    @Override
    public String name() {
        return "短信网关（故障）";
    }

    @Override
    public CollaborationHub hub() {
        return hub;
    }

    @Override
    public void receive(Message message) {
        throw new IllegalStateException("短信通道超时，" + message.kind().label() + " 未送达");
    }
}
```

**它只实现了 `receive()`，四个方法，三个 getter 一个 throw。**

**注意它没有重写 `say()` / `escalate()` / `closeTicket()`。** 那三个 default 方法它自动继承 —— 但它永远不会调用它们（一个网关不会主动发言）。

**`private static final class` 的三个修饰符各有理由：**

| 修饰符 | 理由 |
|---|---|
| `private` | 只有 `Client` 用，不该暴露给别的类 |
| `static` | 不需要访问 `Client` 的实例字段（`Client` 也没有实例字段） |
| `final` | 不打算被继承 |

**为什么它写在 `Client` 里，而不是单独一个文件？**

因为它**只在这段 Demo 里存在**，是一个"故意坏的"测试替身。单独建文件会让读者以为它是项目的一部分，实际上它是演示道具。

> **测试替身（test double）应该靠近使用它的地方。**
> 判断标准：这个类如果被删掉，是否还有别的地方会编译失败？
> 这里答案是否（只有 `demoSixIsolation()` 用它），所以它可以放在内部类里。

**第 1 件事：仓库试图发「提问」。**

```java
warehouse.say(MessageKind.QUESTION, "有人能告诉我这单寄回哪里吗？");
```

`route(Role.WAREHOUSE, MessageKind.QUESTION)`：

```java
case QUESTION -> from == Role.CUSTOMER ? List.of(assignee) : List.of();
```

`from = WAREHOUSE`，不等于 `CUSTOMER`，返回 `List.of()`。

回到 `say()`：

```java
System.out.println("  [" + label(from) + "] " + kind.label() + "：" + message.text());

if (targets.isEmpty()) {
    System.out.println("      路由未命中：角色「" + from.role().displayName()
            + "」不能发送「" + kind.label() + "」，消息丢弃");
    return;
}
deliver(message, targets);
```

输出：

```text
  [仓库-华南仓] 提问：有人能告诉我这单寄回哪里吗？
      路由未命中：角色「仓库」不能发送「提问」，消息丢弃
```

**注意：这里只有 2 行，没有"路由 →"这一行。**

因为 `deliver()` **根本没被调用**。`describeTargets()` 是 `deliver()` 的第一行，所以它也没执行。

**这个输出结构本身就是一个诊断信号：**

| 输出形态 | 含义 |
|---|---|
| `发送行` + `路由 → ...` + 收件人行 | 正常投递 |
| `发送行` + `路由 → `（后面空） | `targets` 非空但元素全是未加入的？不可能 —— 空列表走 else |
| `发送行` + `路由未命中：...` | `route()` 返回了空列表，消息被拒绝 |

**读者看日志就能区分"没人收到"和"被拒绝"。**

**如果 `say()` 里不做这个 `isEmpty` 判断，直接 `deliver(message, List.of())`，会打成什么样？**

```text
  [仓库-华南仓] 提问：有人能告诉我这单寄回哪里吗？
      路由 →
```

**一行"路由 →"后面什么都没有。** 读者会以为这是 bug（路由计算失败打出了空字符串），而不是"设计上就不允许这条消息"。

> **空集合必须被显式处理，否则它在日志里长得像故障。**
> 这是"零值不是一个值"的具体体现 —— `List.of()` 和"计算失败的 list"在打印上不可区分，
> 所以必须用不同的文案把它们分开。

**为什么不允许仓库发"提问"？**

因为 `QUESTION` 的语义是"客户向负责方提问"。仓库有问题应该发 `STOCK_SHORTAGE`（缺货上报）或者其他专门类型。

**这不是权限控制，是语义约束。** 如果允许仓库发 `QUESTION`，`route()` 该怎么算收件人？发给 `assignee`？那主管会收到一条"仓库问客户的话"。

> **枚举值不只是"分类"，它携带语义契约。**
> `QUESTION` 的契约是"发送方一定是客户"，所以 `route()` 里可以直接写 `List.of(assignee)` ——
> 不用判断"如果是仓库发的怎么办"，因为那种情况被规则表挡在门外了。

**第 2 件事：缺货上报，财务没加入。**

```java
warehouse.say(MessageKind.STOCK_SHORTAGE, "EP-2024 无线耳机，华南仓缺货");
```

`route(WAREHOUSE, STOCK_SHORTAGE)` → `List.of(AGENT, FINANCE, CUSTOMER)`。

`describeTargets` 遍历三个角色：

| 角色 | `byRole.get(...)` | 输出片段 |
|---|---|---|
| `AGENT` | 小李 | `"客服-小李"` |
| `FINANCE` | `null` | `"财务（未加入）"` |
| `CUSTOMER` | 王先生 | `"客户-王先生"` |

拼出：

```text
      路由 → 客服-小李、财务（未加入）、客户-王先生
```

**注意这是"三个收件人"的列表，其中一个标注了未加入。** 路由行如实反映了 "`route()` 算了三个，但只有两个在场"。

`deliver()` 循环：

**第一次：`AGENT` → 小李 → 两行输出。**

**第二次：`FINANCE` → `byRole.get(FINANCE)` 返回 `null`：**

```java
if (target == null) {
    System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
    continue;
}
```

输出：

```text
      角色「财务」尚未加入，跳过
```

**`continue` 而不是 `break`。** 这是关键 —— 财务不在，客户还得收到。

**如果写成 `break`，输出会变成：**

```text
      路由 → 客服-小李、财务（未加入）、客户-王先生
        [小李] 收到 ...
        [小李] 客服：...
      角色「财务」尚未加入，跳过
      （王先生什么都没收到）
```

**"跳过一个人"和"放弃整条消息"是完全不同的语义。** `continue` 表达前者，`break` 表达后者。

> **在投递循环里，`continue` 的意思是"这个收件人不在，下一个"，`break` 的意思是"这条消息送不出去了"。**
> 后者只应该在"整条消息都失效"时使用（比如消息本身过期了）。

**第三次：`CUSTOMER` → 王先生 → 一行输出。**

```text
        [王先生] 收到 仓库 的缺货上报：EP-2024 无线耳机，华南仓缺货
```

**只有一行**，因为 `Customer.receive()` 里没有 `STOCK_SHORTAGE` 分支（和 §6.3 的情况一样）。

**这里有一个很容易被误读的细节：`delivered` 加了 2，不是 3。**

财务那次 `continue` 之前没走到 `delivered++`，所以不加。**计数器如实反映"实际送达 2 条"。**

**第 3 件事：关单，通知器炸了。**

```java
agent.closeTicket();
```

`close(from = 小李)`，广播名单 = `participants` 排除小李：

```text
participants = [客户-王先生, 客服-小李, 仓库-华南仓, 通知器-短信网关（故障）]
排除 客服-小李
→ [客户-王先生, 仓库-华南仓, 通知器-短信网关（故障）]
```

`describeTargets` 拼出：

```text
      路由 → 客户-王先生、仓库-华南仓、通知器-短信网关（故障）
```

**注意：`describeTargets` 只对 `byRole.get(role) == null` 才标「（未加入）」。** `BrokenNotifier` 已经在 `byRole` 里了，所以它显示的是 `label(participant)` = `role().displayName() + "-" + name()` = `"通知器-短信网关（故障）"`。

**这里"（故障）"是 `name()` 返回值的一部分，不是 `describeTargets` 加的。**

> 两种括号长得像，来源完全不同：
> - `（未加入）` 是 `describeTargets` 在渲染时加的，**只在路由日志里出现**；
> - `（故障）` 是 `name()` 的内容，**在所有出现"短信网关（故障）"的地方都有**。

**循环三次：**

**第一次：王先生 → 两行。**

**第二次：华南仓 → 两行。**

**第三次：`BrokenNotifier.receive()` 抛出：**

```java
throw new IllegalStateException("短信通道超时，" + message.kind().label() + " 未送达");
```

`message.kind()` = `ARCHIVE`，`label()` = `"归档"`，异常消息 = `"短信通道超时，归档 未送达"`。

`catch (RuntimeException e)` 捕获：

```java
String record = target.name() + " 投递失败：" + e.getClass().getSimpleName()
        + "：" + e.getMessage();
failures.add(record);
System.out.println("      ! 已隔离：" + record);
```

拼出：

```text
短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
```

打印时加了 `! 已隔离：` 前缀，缩进 6 空格：

```text
      ! 已隔离：短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
```

**四个信息：谁失败了 + 失败动作 + 异常类型 + 异常消息。**

**为什么不用 `e.toString()`？**

`e.toString()` 输出 `"java.lang.IllegalStateException: 短信通道超时，归档 未送达"` —— 多了包名，少了"谁失败了"。

| 写法 | 结果 |
|---|---|
| `e.toString()` | `java.lang.IllegalStateException: 短信通道超时，归档 未送达` |
| `e.getClass().getSimpleName()` | `IllegalStateException` |
| `target.name() + " 投递失败：" + simpleName + "：" + getMessage()` | `短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达` |

**后者多了"谁"和"什么动作"，少了包名。** 包名在 `java.lang` 里是噪音，而"谁失败了"是排查时第一需要的。

> **日志里的异常信息，优先要"定位"，其次要"分类"。**
> `getSimpleName()` 保留了分类信息（`IllegalStateException` 表示状态不对），
> 而包名（`java.lang.`）在这个场景里不携带任何决策信息。

**注意 `catch (RuntimeException e)` 而不是 `catch (Exception e)`。**

为什么只抓 `RuntimeException`？

因为 `receive()` 的签名里没有 `throws`，它只能抛非受检异常（`RuntimeException` 及其子类）。`Exception` 里的受检分支（`IOException` 等）在语法上不可能从这个调用点抛出 —— **写 `catch (Exception e)` 会让编译器以为你在处理一些实际上不存在的情况。**

**更重要的：`catch (Throwable)` 是绝对不该写的。**

因为 `Throwable` 包含 `Error`（`OutOfMemoryError`、`StackOverflowError`、`AssertionError`）。**这些是 JVM 层面的故障，不是"某个收件方的问题"。** 捕获它们会让程序在已经不可信的状态下继续运行。

| 捕获类型 | 该不该 | 理由 |
|---|---|---|
| `RuntimeException` | 该 | 收件方的业务异常、超时异常都在这 |
| `Exception` | 不该 | 有一部分是受检异常，这个调用点抛不出来 |
| `Throwable` | 绝不该 | 会吞掉 `OutOfMemoryError` 这类致命错误 |

**第 4 件事：核对失败记录。**

```java
System.out.println("  投递失败记录（" + hub.deliveryFailures().size() + " 条）：");
for (String failure : hub.deliveryFailures()) {
    System.out.println("    · " + failure);
}
```

输出：

```text
  投递失败记录（1 条）：
    · 短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
```

**`deliveryFailures()` 返回 `List.copyOf(failures)`。**

```java
@Override
public List<String> deliveryFailures() {
    return List.copyOf(failures);
}
```

**不是 `return failures;`。** 因为那样调用方可以：

```java
// 反例：如果返回的是内部 list
hub.deliveryFailures().clear();      // 失败记录被清空
hub.deliveryFailures().add("假的");  // 伪造记录
```

**`List.copyOf` 返回一个不可变副本**，调用方 `add` / `clear` 会抛 `UnsupportedOperationException`。

> **返回内部集合时，永远返回副本或不可变视图。**
> `List.copyOf` 比 `Collections.unmodifiableList` 更好，因为它**真的复制了一份** ——
> 后者只是包装，如果内部 list 后续被修改，视图也会变，会出现 `ConcurrentModificationException`。

**对比 `roster()` 也是同样的处理：**

```java
@Override
public List<Participant> roster() {
    return List.copyOf(participants);
}
```

**三个查询方法里，两个返回 copy，一个返回 `assignee`（枚举，不可变），一个是基本类型。**

**没有任何一个返回内部可变集合的引用。** 这是"封装"在 Java 里最容易被忽略的一条：把 `final` 加在字段上，不等于把集合的内容保护住了。

**Demo 6 的完整状态变化：**

| 时刻 | `assignee` | `delivered` | `closed` | `failures` |
|---|---|---|---|---|
| 搭建之后 | `AGENT` | 0 | `false` | 空 |
| 仓库发「提问」（被拒） | `AGENT` | 0 | `false` | 空 |
| 缺货上报后 | `AGENT` | 2 | `false` | 空 |
| `close()` 投递中 | `AGENT` | 4 | `false` | **1 条** |
| `close()` 返回后 | `AGENT` | 4 | **`true`** | 1 条 |

**注意第 2 行：被拒绝的消息不改变任何字段。** `delivered` 还是 0，`failures` 还是空。

**"消息被拒绝"不是一种失败，它是正常的路由结果。** 如果把它也记进 `failures`，那么 `failures` 里会混着"这条边不存在"和"网关超时"两类完全不同的东西，监控就没法用了。

> **区分"业务拒绝"和"技术故障"：**
> 前者是设计好的一等公民（返回空列表 + 明确文案），后者是要报警的意外（进 `failures`）。
> **把两者混在一起，会让告警变成噪音。**

### 6.8 三种不同的输出顺序，来自三个不同的地方

六段输出读完，会发现收件人的打印顺序有时和 `join()` 顺序一致，有时不一致。这不是随机 —— 有**三个独立的顺序来源**：

| 来源 | 代码位置 | 决定的输出 | 顺序由什么决定 |
|---|---|---|---|
| **A. 路由列表** | `route()` 返回的 `List.of(...)` | `deliver()` 循环的投递顺序 | `List.of` 的参数顺序 |
| **B. 名单顺序** | `close()` 遍历 `participants` | 广播时的投递顺序 | `join()` 的调用顺序 |
| **C. 名单顺序** | `roster()` → `printRoster()` | `参与方（6）：...` 那一行 | `join()` 的调用顺序 |

**验证一下：**

**Demo 2 的缺货（来源 A）：**

```text
route: List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
join:  [客户, 客服, 仓库, 财务, 主管, 通知器]
```

输出顺序：

```text
        [小王] ...   ← AGENT，第 1
        [小赵] ...   ← FINANCE，第 2
        [李女士] ... ← CUSTOMER，第 3
```

**是 A 的顺序（客服→财务→客户），不是 `join` 顺序（客户在前）。** ✓

**Demo 5 的广播（来源 B）：**

```text
join:    [客户, 客服, 仓库, 财务, 主管, 通知器]
排除客服 → [客户, 仓库, 财务, 主管, 通知器]
```

输出顺序：

```text
        [李女士] ...
        [华东仓] ...
        [小赵] ...
        [张主管] ...
        [站内信网关] ...
```

**是 B 的顺序。** ✓

**Demo 3 的退款确认（来源 A）：**

```text
route: List.of(Role.CUSTOMER, Role.AGENT)
```

输出：

```text
        [李女士] ...   ← CUSTOMER 在前
        [小王] ...     ← AGENT 在后
```

**是 A 的顺序。** 这次恰好和 `join` 顺序一致（客户在 `join` 里也排第一），所以看不出差异 —— **但 Demo 2 已经证明了这个顺序是 A 决定的。**

**为什么这个区分重要？**

因为当你想"调整输出顺序"时，**改的地方完全不同**：

| 想调整 | 改哪里 | 副作用 |
|---|---|---|
| 缺货消息先通知财务 | `route()` 里的 `List.of` 参数顺序 | 无（`delivered` 不变） |
| 广播先通知主管 | 改 `join()` 顺序 | 影响 `roster()` 显示、影响所有广播顺序 |
| `参与方（6）` 那一行换个顺序 | 改 `join()` 顺序 | 同上 |

**"改 `route()` 只影响一类消息，改 `join()` 影响所有广播。"**

> **一个可维护性判断标准：**
> 如果"调整某条消息的收件人顺序"需要改 `join()`，那这个设计是有缺陷的 ——
> 因为它把"一条消息的规则"和"全局的成员顺序"绑在了一起。
> 本项目把这两者分开，所以改一个不影响另一个。

**但分开的代价是：观众第一次读输出时会觉得"顺序有点怪"。**

这是必须付的代价。**如果为了让输出看起来整齐，去把 `route()` 的结果按 `join()` 顺序排序，就多了一个"排序"步骤 —— 而这一步不携带任何业务信息，纯粹为了好看。**

> **不要为了输出的美观，在业务代码里加一步无意义的变换。**
> 输出顺序"怪"是有信息量的：它告诉你"这条消息的收件人是一个显式列表"。

### 6.9 六段 Demo 与七个缺陷的对应关系

README §2.3 列了原项目的七个缺陷（探针取证），§6 的六段 Demo 里，每一段都在堵其中一个（或几个）：

| Demo | 演示的东西 | 堵住的原项目缺陷 | 关键代码 |
|---|---|---|---|
| 1 | 一句话只到一个人 | 缺陷 7：只能广播，无法定向 | `case QUESTION -> List.of(assignee)` |
| 2 | 一句缺货三方协同 | 缺陷 7 + "谁不该收到"无法表达 | `List.of(AGENT, FINANCE, CUSTOMER)` |
| 3 | 换角色换收件人 | 同上（发送方参与路由计算） | `case REFUND_CONFIRMED -> ...` |
| 4 | 升级后改负责方 | 缺陷 7 + 没有"负责方"概念 | `assignee = Role.SUPERVISOR` |
| 5 | 关闭是唯一广播 + 关闭后禁言 | 缺陷 5（`name` 可改）+ 无生命周期 | `closed = true` |
| 6 | 故障隔离 + 未定义路由被拒 | **缺陷 3（null 断链）** + 缺陷 4（CME） | `try-catch` + `continue` |

**几个对应关系的细节：**

**Demo 5 对应缺陷 5（`name` 可空可改）？**

不完全是。Demo 5 演示的是"关闭后不能再发言"，这堵的是"没有生命周期"这个更根本的问题。**缺陷 5 是被 `private final String name` + 构造校验堵住的**，而这一点在六段 Demo 里没有专门演示 —— 它在 §5.10 的代码里。

> **不是每个修复都需要一段 Demo。**
> 六段 Demo 覆盖的是"行为层面的差异"（路由、广播、隔离、生命周期）。
> 修饰符层面的修复（`private final`、`final class`）在代码里，用 §5 逐文件解析来讲。

**Demo 6 对应缺陷 4（CME）？**

间接对应。原项目的 CME 是"收到消息时顺手注册一个人"导致的（探针 4）。本项目里 `join()` 只在搭建时调用，且 `close()` 遍历的是 `participants`，如果有人在中途 `join()`，也会 CME。

**这一点本项目没修，也没演示。** 严格说 `join()` 在 `close()` 进行中调用会出问题 —— 但 `join()` 会先检查 `closed`（此时还是 `false`），所以能加进去，然后 `participants.add()` 触发 `ConcurrentModificationException`。

**这个洞是真的，但触发条件极窄**（需要在 `deliver()` 的某个 `receive()` 里 `join` 一个新人）。本项目选择不修，理由是修它的成本（`CopyOnWriteArrayList` 或迭代前复制）会掩盖"成员列表是稳定的"这个更重要的认知。

> **明确知道一个洞存在，比假装它不存在要好。**
> 但也要明确：**这个洞的存在不影响模式本身** —— 它和"中介者持有名单"这个设计无关，
> 是"用 `ArrayList` 边遍历边改"这个通用问题。

**Demo 1-4 都没有演示 `NOTICE`（提示）类型。**

`NOTICE` 的 `route()` 是 `List.of(Role.CUSTOMER)` —— 任意角色发，只给客户。六段 Demo 里没有任何一处调用 `say(NOTICE, ...)`。

**为什么留着这个分支？**

因为它是"任意角色 → 客户"这条规则的具体实现，**而这条规则在真实系统里极常用**（"您的包裹已到驿站"、"退款已受理"、"工单已转交"）。

**它的存在还有一个教学价值：`NOTICE` 和 `ANSWER` 的收件人完全相同（都是客户），但发送方限制不同。**

```java
case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR) ? List.of(Role.CUSTOMER) : List.of();
case NOTICE -> List.of(Role.CUSTOMER);
```

**这两行的对比，正好回答了一个常见疑问："为什么不能合并成一个类型？"**

因为 `ANSWER` 是"客服对客户的应答"，`NOTICE` 是"任何人对客户的单向告知"。**如果合并，`route()` 就只能取更宽松的那个（`List.of(Role.CUSTOMER)`），于是客户也能"答复"自己。**

> **两个消息类型是否该合并，看的不是"收件人是否相同"，而是"规则表能否用同一行表达"。**
> 只要有一处判断不同，就该是两个类型。

### 6.10 收尾：`summary()` 那八行

第六段之后还有一次调用，它不属于任何一段 Demo：

```java
summary();
```

完整输出：

```text
[六段 Demo 对应原项目的六个问题]
  1. 客户提问只到负责方       —— 原项目只能广播给所有人
  2. 缺货触发三方协同         —— 原项目没有「一条消息多个收件人」的规则
  3. 换个角色发言换批收件人   —— 原项目的收发两端永远是「除我之外」
  4. 升级后负责方变更         —— 原项目没有「负责方」这个概念
  5. 关闭是唯一一次广播       —— 原项目全程都在广播
  6. 一家炸了不影响其他家     —— 原项目裸循环，一个 null 就断链

  六个同事的代码里没有一行「通知某某」——协同规则只写在中枢的 route() 里
```

**这八行全是硬编码字符串。** 没有任何一行是程序算出来的。

```java
private static void summary() {
    section("六段 Demo 对应原项目的六个问题");
    System.out.println("  1. 客户提问只到负责方       —— 原项目只能广播给所有人");
    // ... 共六条
    System.out.println();
    System.out.println("  六个同事的代码里没有一行「通知某某」——协同规则只写在中枢的 route() 里");
}
```

**为什么不用程序算？**

因为「原项目会怎样」这件事，本项目里根本没有原项目的代码 —— 它算不出来。

**但这八行不是装饰。它们是整份文档的索引：** 每一条都指向 §2.3 里一个带真实探针输出的小节。

| 这八行 | 对应 §2.3 的缺陷 | 证据 |
|---|---|---|
| 1. 只能广播给所有人 | 2.3.7 只能全员广播 | P7 |
| 2. 没有多收件人规则 | 2.3.7 只能全员广播 | P7 |
| 3. 收发两端永远「除我之外」 | 2.3.7 + 2.3.1 名单错配 | P7 + P1 |
| 4. 没有「负责方」概念 | 2.3.7 | P7 |
| 5. 全程都在广播 | 2.3.7 | P7 |
| 6. 裸循环，一个 null 就断链 | **2.3.3 null 断链** | **P3** |

**第 6 条最值得注意：** 它对应的是探针 3 里那句真实的 NPE：

```text
java.lang.NullPointerException: Cannot invoke "BehaviorType.MediatorPattern.User.receive(String)" because "<local4>" is null
```

**一个 `null` 进名单，排在它后面的人全部静默丢失。** 这不是"偶发 bug"，是裸循环的必然结果。

> **演示代码最后打出来的总结，最好一条一条能对上前面取证过的证据。**
> 否则它只是漂亮话 —— 而漂亮话在技术文档里是有害的，因为它会让读者以为
> "已经验证过了"。

**`summary()` 里那个空行也是刻意的。**

六条对比讲的是"有什么不同"，最后这句讲的是"为什么不同"。用一个空行把"现象"和"原因"分开。

---

## 7. 扩展对比：需求变了改哪里

这一节不谈"模式多好"，只算一件事：**需求变的时候，要改几个文件、几行代码、编译器会不会帮你兜底。**

七种需求变化，每一种都按同一套流程走：

1. 先说清楚需求；
2. 列出改动点；
3. 用真实代码写出来（不是伪代码）；
4. 说清楚"如果不改会怎样"；
5. 说清楚"编译器帮不帮得上忙"。

### 7.1 先给结论：一张改动成本表

| 需求 | 改动文件数 | 改哪些 | 编译器兜底 |
|---|---|---|---|
| 加一个角色（质检） | **2** | `Role` 加枚举 + 新建 `QualityInspector` + `route()` 加一条 + `build()` join | **不兜底** |
| 加一种消息类型（退款驳回） | **1 必修 + 3 选改** | `MessageKind` 加枚举；`route()` **必然编译失败**；三个无 `default` 的 `receive()` 静默无动作 | **只兜 route()** |
| 换消息投递通道（MQ） | **1** | `deliver()` 内部 | 不兜底 |
| 加"撤回上一条消息" | **3** | `Message` 加 ID + `AfterSalesHub` 加撤回 + 参与者接口加默认方法 | 不兜底 |
| 一单多个客服 | **1** | `byRole` 从 `Map<Role, Participant>` 改成 `Map<Role, List<Participant>>` | **兜底**（类型变了，所有 `get` 处编译失败） |
| 改成观察者模式 | **删除 3 个文件 + 重写 2 个** | `CollaborationHub` / `AfterSalesHub` 消失 | — |
| 改成责任链模式 | **删除 4 个文件 + 重写 6 个** | 六个参与者全部要改 | — |

**这张表的读法：**

- 前两行是"这个模式该有的扩展成本"——**加角色 2 个文件、加消息类型 1 个必修文件**，很便宜；
- 第五行是"类型系统帮你兜底"的正面案例——**改一个字段类型，编译器会把所有需要改的地方指出来**；
- 最后两行是"换模式"的成本——**不是重构，是重写**。

> **判断一个设计好不好，看的是"第五行那种改动有多少"，而不是"第一行有多便宜"。**
> 因为"加角色"这类需求是计划内的，而"改数据结构"是计划外的 ——
> 计划外的改动如果有编译器兜底，成本就从"三天"降到"三小时"。

下面六节逐个展开。

### 7.2 加一个角色：质检

**需求：** 工单关闭之前，质检要检查一遍客服的回复质量。所以 `ARCHIVE` 广播时质检要收到；另外质检还要能对客服发一条"整改意见"。

#### 7.2.1 第一处：`Role` 加枚举值

```java
public enum Role {

    CUSTOMER("客户"),
    AGENT("客服"),
    WAREHOUSE("仓库"),
    FINANCE("财务"),
    SUPERVISOR("主管"),
    NOTIFIER("通知器"),
    QUALITY("质检");        // 新增
    ...
}
```

**改完这一行，编译器报错了吗？**

**没有。一个错都没有。**

因为全项目没有任何一处 `switch` 的是 `Role`：

```java
// AfterSalesHub.route() 里 switch 的是 MessageKind，不是 Role
return switch (kind) {
    case QUESTION -> from == Role.CUSTOMER ? ... : ...;
    ...
};
```

**这是本节最需要记住的一件事：**

> **`Role` 不参与任何 `switch`，所以加角色时编译器完全帮不上忙。**
> 你会得到一个"编译通过、运行永远收不到消息"的新角色。

**为什么 `Role` 不参与 `switch`？**

因为 `route()` 的决策依据是"消息类型"，`Role` 只是它的**输入参数**（`from`）和**输出**（`List<Role>`）。它出现在 `==` 比较里，不出现在 `case` 里。

**`==` 比较不会被编译器检查穷尽性。** 漏掉一个枚举值，`==` 只是返回 `false`。

#### 7.2.2 第二处：新建 `QualityInspector`

```java
public final class QualityInspector implements Participant {

    private final String name;
    private final CollaborationHub hub;

    public QualityInspector(String name, CollaborationHub hub) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("质检名称不能为空白");
        }
        this.name = name.strip();
        this.hub = Objects.requireNonNull(hub, "中枢不能为 null");
    }

    @Override
    public Role role() {
        return Role.QUALITY;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public CollaborationHub hub() {
        return hub;
    }

    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case ARCHIVE -> System.out.println("        [" + name + "] 质检：抽查这单的回复记录。");
            default -> System.out.println("        [" + name + "] 质检：（记录，不介入）");
        }
    }
}
```

**85 行左右，和 `Supervisor.java`（83 行）几乎一模一样。**

**这不是巧合，也不是"应该抽个基类"的信号。**

六个参与者类的构造器、`role()`、`name()`、`hub()` 都相同，只有 `receive()` 不同。看起来非常适合抽一个 `AbstractParticipant`。

**本项目的决定是不抽**，理由在 §4.5 讲过：`Participant` 用接口 + default 方法，让"共享能力"通过 `say()` / `escalate()` / `closeTicket()` 三个 default 方法实现；**剩下四个方法（构造器 + 三个 getter）虽然重复，但它们是"数据持有"而不是"行为"。**

**抽基类的代价：**

```java
// 假设这么抽
public abstract class AbstractParticipant implements Participant {
    private final String name;
    private final CollaborationHub hub;
    protected AbstractParticipant(String name, CollaborationHub hub) { ... }
    // role() 还是得子类实现
    // receive() 还是得子类实现
}
```

**抽完发现：`role()` 和 `receive()` 两个方法还是每个子类都得写，省掉的只有两个 getter。**

而代价是所有参与者类从"零继承"变成"必须继承 `AbstractParticipant`"。**一旦某天需要一个"不是人的参与者"（比如 `BrokenNotifier` 那种连 `name` 都不想的），继承就成了负担。**

> **"重复的 getter"和"重复的逻辑"是两件事。**
> 前者是数据结构的形状，后者是行为的耦合。
> **只应该为后者抽基类。**

#### 7.2.3 第三处：`route()` 让质检收到归档 —— 但这里有个陷阱

**需求说"`ARCHIVE` 广播时质检要收到"。**

`ARCHIVE` 走的是 `close()`，不是 `route()`：

```java
case ESCALATION, ARCHIVE -> List.of();
```

所以**只需在 `build()` 里 `join` 一个质检，它自动就会收到归档** —— 因为 `close()` 的广播名单是遍历 `participants`：

```java
for (Participant participant : participants) {
    if (participant != from) {
        targets.add(participant.role());
    }
}
```

**质检 join 了，就自动在名单里，就自动收到归档。零改动。**

**这是 `close()` 用"遍历名单"而不是"写死列表"的直接收益。**

对比一下：如果 `close()` 写成：

```java
// 反例：写死广播名单
deliver(message, List.of(Role.CUSTOMER, Role.AGENT, Role.WAREHOUSE,
        Role.FINANCE, Role.SUPERVISOR, Role.NOTIFIER));
```

那么加质检时，`close()` 也得改 —— **而这里是很容易漏的，因为"关单广播"这个方法名字里没有"名单"两个字。**

> **广播名单应该从"当前成员"算出来，而不是"罗列所有角色"。**
> 前者加角色时自动生效，后者加角色时必须记得改。

**但"质检能收归档"和"质检能发整改意见"是两件事。**

后者需要一个新的 `MessageKind`（比如 `RECTIFICATION`），因为：

```java
case NOTICE -> List.of(Role.CUSTOMER);      // NOTICE 只给客户
```

质检想发给客服，`NOTICE` 走不通。

**这就引出 7.3。**

#### 7.2.4 第四处：`build()` 里 join

```java
private static Team build() {
    AfterSalesHub hub = new AfterSalesHub("AS-2024-0917");
    Customer customer = new Customer("李女士", hub);
    Agent agent = new Agent("小王", hub);
    Warehouse warehouse = new Warehouse("华东仓", hub);
    Finance finance = new Finance("小赵", hub);
    Supervisor supervisor = new Supervisor("张主管", hub);
    Notifier notifier = new Notifier("站内信网关", hub);
    QualityInspector quality = new QualityInspector("质检-周工", hub);   // 新增

    hub.join(customer);
    hub.join(agent);
    hub.join(warehouse);
    hub.join(finance);
    hub.join(supervisor);
    hub.join(notifier);
    hub.join(quality);      // 新增
    return new Team(hub, customer, agent, warehouse, finance, supervisor, notifier,
            quality);       // ← 这一行编译失败（Team 多了个字段）
}
```

**注意最后一行会编译失败** —— `Team` record 只有 7 个字段，现在传了 8 个。

**这是整个"加角色"流程里唯一会被编译器抓住的地方。**

**而它抓住的是"`Team` 的字段数不对"，不是"你漏了 `route()` 的分支"。**

> **这就是"编译器兜底"的真实水平：它能抓住结构性的不一致（字段数、类型），
> 抓不住语义性的遗漏（忘了给新角色配路由规则）。**

#### 7.2.5 加角色的完整清单

| 步骤 | 文件 | 是否会被编译器发现 |
|---|---|---|
| 1. `Role` 加枚举值 | `Role.java` | 不会 |
| 2. 写 `QualityInspector` | 新文件 | 不会（新文件不写也能编译） |
| 3. `route()` 加规则 | `AfterSalesHub.java` | **不会** |
| 4. `join` | `Client.java` | **会**（`Team` 字段数变了） |

**四个步骤，编译器只兜住最后一个。**

**所以加角色时，真正的风险在第 3 步：** 如果需求是"质检能收到客服的答复"，你得改：

```java
case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
        ? List.of(Role.CUSTOMER)
        : List.of();
```

改成：

```java
case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR)
        ? List.of(Role.CUSTOMER, Role.QUALITY)      // 客服答复也抄送质检
        : List.of();
```

**改完，质检就能收到客服的答复了。**

**这一步漏了会怎样？**

**编译通过，运行正常，质检永远收不到答复。** 而且没有任何报错 —— 因为 `deliver()` 只投递 `route()` 给出的列表，列表里没有质检，它就不会被调用。

**这是中介者模式最大的运维风险：路由遗漏是静默的。**

> **对策不是"更小心"，而是"让遗漏变得可见"。**
> 三种做法：
> 1. **写一个路由表快照测试**：断言 `route(每个Role, 每个MessageKind)` 的返回值，加角色时必须更新快照；
> 2. **启动时打印路由表**：把 6 个角色 × 7 种消息的 42 个格子全打出来，人眼扫一遍；
> 3. **在 `join()` 里检查"这个角色的收件箱是否为空"**：如果某个角色在所有 `route()` 分支里都没出现过，打一条警告。
>
> 本项目**没有实现这三种**，因为 6 个角色规模太小。**但在 20 个角色的系统里，第 1 种是必须的。**

### 7.3 加一种消息类型：退款驳回

**需求：** 财务发现不符合退款条件，要驳回。这条消息要同时给客户（说明原因）和客服（更新工单状态）。

#### 7.3.1 第一步：`MessageKind` 加枚举值

```java
public enum MessageKind {

    QUESTION("提问"),
    ANSWER("答复"),
    STOCK_SHORTAGE("缺货上报"),
    REFUND_CONFIRMED("退款确认"),
    REFUND_REJECTED("退款驳回"),      // 新增
    NOTICE("提示"),
    ESCALATION("升级"),
    ARCHIVE("归档");
    ...
}
```

**加完这一行，立刻编译，会报什么？**

**一个错误，在 `route()`：**

```text
AfterSalesHub.java:189: 错误: switch 表达式不包含所有可能的输入值
        return switch (kind) {
               ^
1 个错误
```

**这是 §4.8 那个决定（switch 表达式无 `default`）的直接回报：**

> **加一种消息类型，编译器立刻告诉你"路由表少了一格"。**

**对照一下：如果 `route()` 写了 `default`：**

```java
// 反例
return switch (kind) {
    case QUESTION -> ...;
    ...
    default -> List.of();       // 兜底
};
```

**那么加 `REFUND_REJECTED` 会编译通过，然后这条消息永远走 `default` → 返回空列表 → 被拒绝。**

**而"被拒绝"的日志是：**

```text
      路由未命中：角色「财务」不能发送「退款驳回」，消息丢弃
```

**这条日志看起来像是"规则就是这么定的"，实际上是你忘了配规则。** 一个设计错误被伪装成了一条正常的业务拒绝。

> **`default` 在规则表里是有害的，因为它把"遗漏"和"有意为之"变成了同一种东西。**
> 这是本项目全项目唯一一个 `switch` 表达式不写 `default` 的地方 —— 而它必须是唯一一个。

#### 7.3.2 第二步：补 `route()` 的一个 case

```java
case REFUND_REJECTED -> from == Role.FINANCE
        ? List.of(Role.CUSTOMER, Role.AGENT)
        : List.of();
```

**写完，编译通过。**

#### 7.3.3 第三步：谁需要处理这条消息？

**这是最容易被忽略的一步。**

编译已经通过了，运行也不报错。但如果六个 `receive()` 都不加分支，会发生什么？

逐个看：

| 文件 | `switch` 形式 | 有 `default` 吗 | 加枚举后的实际行为 |
|---|---|---|---|
| `AfterSalesHub.route` | **表达式** | 无 | **编译失败**（必须补） |
| `Customer.receive` | 语句 | **无** | 编译通过，**只打印"收到"，无动作** |
| `Agent.receive` | 语句 | **无** | 编译通过，**只打印"收到"，无动作** |
| `Finance.receive` | 语句 | **无** | 编译通过，**只打印"收到"，无动作** |
| `Warehouse.receive` | 语句 | 有（报警） | 编译通过，打印 **`!! 这是我不该收到的消息类型`** |
| `Supervisor.receive` | 语句 | 有（记录） | 编译通过，打印 `（记录在案，无需动作）` |
| `Notifier.receive` | 语句 | 有（拒绝） | 编译通过，打印 `不在外发白名单，丢弃` |

**这张表是本节的核心。它说明了一件事：**

> **七处 `switch`，编译器只守住一处（`route()`）。**
> 剩下六处，靠的是每个类自己的 `default` 策略。

**为什么不全都不写 `default`？**

因为这样六个 `receive()` 会在加枚举时全部编译失败，逼迫你逐个确认。

**这是一种可行的策略，但本项目没选**，理由是：

1. **`receive()` 的"什么都不做"是常态。** 六个角色里，任何一个角色对大多数消息都是无动作的（`Customer` 对 7 种里 4 种无动作，`Warehouse` 对 7 种里 6 种无动作）。如果强制穷尽，每个 `receive()` 都会有五六行 `case X -> {}`，**读代码时根本看不出哪些是"真的有反应"**。
2. **`default` 让"意外消息"可见。** `Warehouse` 的 `default` 打印报警，是因为"仓库收到缺货以外的业务消息"意味着路由表有问题 —— **这个报警是运行时的一等公民**。

**两种策略的对比：**

| 策略 | 加枚举时 | 运行时可观测性 | 代码噪音 |
|---|---|---|---|
| 全部穷尽（无 `default`） | **六处编译失败**，被迫逐个确认 | 低（静默无动作，没有日志） | 高（大量空 case） |
| **本项目：部分有 `default`** | 只 `route()` 编译失败 | **高**（报警/记录/拒绝三种策略） | 低 |

> **`switch` 语句加 `default`、`switch` 表达式不加 —— 这个不对称是刻意的。**
> 表达式必须给出一个值，`default` 会伪造一个"看起来合法"的答案；
> 语句只是执行动作，`default` 可以是一个**有意义的动作**（报警、记录、拒绝）。

**这句话是全项目最值得抄走的一条规则。**

#### 7.3.4 第四步：补客户和客服的分支

```java
// Customer.receive() 里加一行
case REFUND_REJECTED -> System.out.println("        [" + name + "] 客户：为什么不给退？我要投诉。");
```

```java
// Agent.receive() 里加一行
case REFUND_REJECTED -> System.out.println("        [" + name + "] 客服：转投诉流程，重新核对政策。");
```

**这两行加不加，编译都能过、运行都不报错。区别只在于输出里有没有那句话。**

**这就是 §7.1 表格里"1 必修 + 3 选改"的含义：**

- **1 必修** = `route()`（编译器强制）；
- **3 选改** = `Customer` / `Agent` / `Finance`（业务需要才改）。

#### 7.3.5 一个真实的风险：`Notifier` 会把它当"不该外发"而丢弃

补完 `route()` 之后，`REFUND_REJECTED` 只发给 `CUSTOMER` 和 `AGENT`，不会到 `Notifier`。所以它的 `default` 不会触发。

**但如果哪天有人把 `NOTIFIER` 加进了 `REFUND_REJECTED` 的收件人：**

```java
case REFUND_REJECTED -> from == Role.FINANCE
        ? List.of(Role.CUSTOMER, Role.AGENT, Role.NOTIFIER)
        : List.of();
```

**运行时输出会是：**

```text
        [站内信网关] 收到 财务 的退款驳回：不符合七天无理由，已驳回
        [站内信网关] 不在外发白名单，丢弃（防止内部消息外泄）
```

**这条消息被拒绝了，但整条投递没有失败** —— `delivered` 照样加一，`failures` 里没有记录。

**这是"fail safe"和"fail loud"的区别：**

| 策略 | 表现 | 适合 |
|---|---|---|
| **fail safe**（`Notifier` 的 `default`） | 丢弃 + 打印一句 | 安全边界（不能外发的东西，扔了比发出去好） |
| **fail loud**（`Warehouse` 的 `default`） | 打印 `!!` 警告 | 内部路由错误（必须被发现） |

**为什么 `Notifier` 选 fail safe？**

因为它连着外部世界。**"多发一条短信"的代价（客户投诉、合规风险）远高于"少发一条"**（客户多等一天）。

**为什么 `Warehouse` 选 fail loud？**

因为它的收件箱极窄（只有 `ARCHIVE`）。**一旦收到别的，几乎必然是路由表配错了** —— 这时候静默丢弃会让 bug 藏起来。

> **同一个 `default`，在两类类里应该有两种写法。**
> 判断标准：**"漏掉"的代价落在谁身上？**
> 落在外部世界（客户、合规、第三方）→ fail safe；
> 落在自己人（运维、开发）→ fail loud。

### 7.4 换投递通道：从方法调用换成消息队列

**需求：** 客服团队要和另一个系统对接，消息得走 Kafka。但业务代码一行都不想动。

#### 7.4.1 需要改的地方：只有一个方法

```java
private void deliver(Message message, List<Role> targets) {
    System.out.println("      路由 → " + describeTargets(targets));

    for (Role role : targets) {
        Participant target = byRole.get(role);
        if (target == null) {
            System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
            continue;
        }
        try {
            target.receive(message);
            delivered++;
        } catch (RuntimeException e) {
            ...
        }
    }
}
```

**`deliver()` 是整个项目里唯一一个"调用参与者方法"的地方。**

验证一下：全项目搜 `.receive(`，只有一处：

```text
AfterSalesHub.java:  target.receive(message);
```

**六个参与者从不互相调用 `receive()`。** 这是中介者模式的硬指标。

> **判断一个中介者是不是真的中介者，一行就够了：**
> **搜 `receive(` 的调用点，如果出现在多个类里，那就不是中介者，是网状调用。**

#### 7.4.2 换成 MQ 的写法（设计推演，未落地）

```java
// 设计推演：未编译、未运行
private void deliver(Message message, List<Role> targets) {
    System.out.println("      路由 → " + describeTargets(targets));

    for (Role role : targets) {
        Participant target = byRole.get(role);
        if (target == null) {
            System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
            continue;
        }
        try {
            mq.publish("aftersales." + role.name().toLowerCase(),
                    message.kind().name(),
                    message.text());
            delivered++;
        } catch (RuntimeException e) {
            ...
        }
    }
}
```

**六个参与者类一行都不用改。** 因为 `route()` 没变，`Message` 没变，`Participant` 接口没变。

**但有三件事会变，而且都不是好事：**

**第一件：`receive()` 不再被调用，参与者收不到消息了。**

因为走 MQ 之后，是 MQ 的消费者去调 `receive()`，不是 `deliver()` 直接调。所以还得写消费者：

```java
// 设计推演：未编译
@KafkaListener(topics = "aftersales.agent")
public void onMessage(String kind, String text) {
    Role role = Role.AGENT;
    byRole.get(role).receive(new Message(Role.FINANCE, MessageKind.valueOf(kind), text));
}
```

**注意这里的 `new Message(Role.FINANCE, ...)` 是错的** —— 发送方信息丢了。

**要修，就得把 `from` 也放进消息。**

```java
mq.publish(topic, message.from().name() + "|" + message.kind().name(), message.text());
```

**于是一个新的、丑陋的序列化格式诞生了。** 而它本该是 `Message` 的职责：

```java
// 正确做法：Message 自己负责序列化
public record Message(Role from, MessageKind kind, String text) {
    public String toWire() {
        return from.name() + "|" + kind.name() + "|" + text;
    }
    public static Message fromWire(String wire) { ... }
}
```

**这是"换通道"暴露出的第一个设计缺口：`Message` 是一个纯内存对象，没有线上表示。**

**第二件：`delivered` 计数不再准确。**

现在 `delivered++` 在 `receive()` 成功返回之后执行 —— 它统计的是"真的送达了"。

走 MQ 之后，`mq.publish(...)` 返回只意味着"进了 broker"，不意味着"消费者收到了"。**`delivered` 变成了"发布成功的条数"。**

**这两个数字的语义完全不同。** 一个是端到端的送达，一个是异步投递的入队。

**第三件：异常隔离失效。**

现在的 `try-catch` 捕获的是 `receive()` 抛出的异常 —— 也就是收件方的业务异常。

走 MQ 之后，`mq.publish()` 的异常是"broker 连不上"，不是"收件方处理失败"。**收件方处理失败的异常发生在消费者进程里，`AfterSalesHub` 永远看不到。**

**于是 `failures` 列表里记录的从"业务失败"变成了"网络故障"，两件事混在了一起。**

#### 7.4.3 三件事的共同原因

三件事都指向同一个判断：

> **`AfterSalesHub` 的名字里有 "Hub"，但它做的不是"消息中间件"。**
> 它做的是"**同步的、进程内的、有确定送达语义的**路由"。

**一旦换成 MQ，你换掉的不只是一个方法，是三件事的语义：**

| 现在的语义 | MQ 后的语义 |
|---|---|
| 同步：`say()` 返回时所有人已收到 | 异步：`say()` 返回时消息还在队列里 |
| 送达确定：`delivered` 是真实的 | 送达不确定：要等消费者 ACK |
| 异常可见：收件方异常进 `failures` | 异常不可见：在消费者进程里 |

**如果业务真的需要 MQ，正确做法不是"改 `deliver()`"，而是：**

1. 保留 `AfterSalesHub` 做**规则计算**（`route()` 纯粹、可测）；
2. 把 `deliver()` 换成一个 `MessageDispatcher` 接口的两个实现：`InMemoryDispatcher` 和 `KafkaDispatcher`；
3. 参与者不再是"直接被调用"，而是"订阅某个主题"。

**第 2 步的关键是：`deliver()` 抽成接口之前，先接受"语义变了"这个事实。**

> **不要把"换实现"说成"改一行"。**
> `deliver()` 抽成接口是容易的（5 分钟），保证语义不变是困难的（可能要重新设计幂等、重试、死信）。

**本项目不做这一步，因为它超出了设计模式演示的范围。** 但 §7.4.2 那三件事必须写出来 —— 否则读者会以为"中介者 = 可换成 MQ"，这是错的。

### 7.5 加"撤回上一条消息"

**需求：** 客服发现自己答错了，要撤回上一句。

**这个需求非常能说明中介者的边界。**

#### 7.5.1 为什么"撤回"难

因为消息已经投递出去了。`Customer.receive()` 已经打印了"好，那我等结果。"

**"撤回"要么是"发一条新的（更正）消息"，要么是"在投递前留一个时间窗"。**

**第一种（更正消息）很简单：**

```java
// MessageKind 加一个值
CORRECTION("更正"),
```

```java
// route() 加一条
case CORRECTION -> from == Role.AGENT ? List.of(Role.CUSTOMER) : List.of();
```

**两个文件，五行。** 这是"业务上的撤回"。

**第二种（真撤回）很难，因为需要三样东西：**

1. **每条消息有 ID** —— `Message` 现在是 `record Message(Role from, MessageKind kind, String text)`，没有 ID；
2. **中枢保留"已投递"记录** —— 现在只有计数 `delivered`，没有明细；
3. **收件方能"撤销一个动作"** —— 而 `receive()` 现在只是打印，没有可撤销的状态。

**三样缺任何一个，"真撤回"都做不成。**

#### 7.5.2 加 ID 是容易的

```java
public record Message(Role from, MessageKind kind, String text, long id) {
    private static final AtomicLong SEQ = new AtomicLong();

    public static Message of(Role from, MessageKind kind, String text) {
        return new Message(from, kind, text, SEQ.incrementAndGet());
    }
}
```

**但这一改，`AfterSalesHub` 里三处 `new Message(...)` 全部编译失败。** 这是好事 —— 编译器帮你找全了。

#### 7.5.3 保留投递明细是中等难度

```java
// 现在是
private int delivered;

// 改成
private final List<Delivery> delivered = new ArrayList<>();

private record Delivery(long messageId, Role to, boolean success) {
}
```

**这一改，`deliveredCount()` 的实现从 `return delivered;` 变成 `return (int) delivered.stream().filter(Delivery::success).count();`。**

**注意：这个改动会让"计数"从 O(1) 变成 O(n)。** 6 个收件人时无所谓，6 万个时就是性能问题。

> **"加一个明细列表"看起来只是多存点东西，实际上改的是一个字段的时间复杂度。**
> 这也是为什么"演示用的计数器"（`private int delivered`）和"生产用的投递日志"（`List<Delivery>`）
> 应该是两个不同的东西 —— 本项目只需要前者。

#### 7.5.4 撤销动作是真正难的部分

**这一部分本项目完全没有实现，因为它需要重新设计 `Participant`。**

现在的 `Participant` 只有 `receive(Message)`，语义是"通知"。要支持撤销，需要：

```java
// 设计推演：未编译
public interface Participant {
    ...
    default void revoke(long messageId) {
        // 默认什么都不做
    }
}
```

**加一个 default 方法，六个参与者一行都不用改（编译通过）—— 但"什么都没做"意味着撤销是假的。**

**要实现，每个参与者得自己维护"我处理过哪些 messageId"：**

```java
// 设计推演：未编译
private final Map<Long, Runnable> undoActions = new HashMap<>();

@Override
public void receive(Message message) {
    ...
    case ANSWER -> {
        System.out.println(...);
        undoActions.put(message.id(), () -> System.out.println("（撤回：客户不再等待）"));
    }
}
```

**这段代码会让每个参与者类膨胀 20~30 行，而它服务的是一个"客服答错了"的罕见场景。**

> **这是"中介者模式解决不了的问题"的典型例子。**
> 中介者能把"消息送给谁"集中管理，但**"收到之后能不能反悔"是收件方自己的事**。
> 硬要中枢管，中枢就得持有"每个收件方的可撤销动作" —— 那就变成了分布式事务协调者。

**结论：撤回需求如果出现，正确的做法是"发一条更正消息"，不是"真的撤回"。**

**而这恰好又落回中介者最擅长的事：加一种 `MessageKind` + 加一条 `route()` 规则。两个文件。**

### 7.6 改成一单多个客服

**需求：** 高峰期一个客服忙不过来，同一张工单要有 3 个客服协同。

**这个需求会打破一个核心假设：`byRole` 的"一个角色一个人"。**

#### 7.6.1 现在的实现和它的假设

```java
private final Map<Role, Participant> byRole = new EnumMap<>(Role.class);
```

`EnumMap` 的键是唯一的，所以**每个角色最多一个人**。

`join()` 里靠这个唯一性做去重：

```java
Participant existing = byRole.get(role);
if (existing != null) {
    throw new IllegalArgumentException(
            "角色「" + role.displayName() + "」已经由「" + existing.name()
                    + "」担任，不能再加入「" + participant.name() + "」");
}
```

**这个假设在售后场景里是对的**（一个工单一个客服）。**但它是一个假设，不是一个必然。**

#### 7.6.2 改法：从"一人"到"一组人"

```java
private final Map<Role, List<Participant>> byRole = new EnumMap<>(Role.class);
```

**改完这一行，编译器会报几个错？**

`byRole.get(role)` 现在返回 `List<Participant>`，所以：

| 位置 | 现在的代码 | 编译器说什么 |
|---|---|---|
| `join()` 查重 | `Participant existing = byRole.get(role);` | **类型不兼容**：`List<Participant>` 不能赋给 `Participant` |
| `deliver()` 取人 | `Participant target = byRole.get(role);` | **类型不兼容** |
| `describeTargets()` 取名 | `Participant participant = byRole.get(role);` | **类型不兼容** |

**三处全部编译失败。** 这就是 §7.1 表格里说的"编译器兜底"。

**为什么这次编译器管用，而加角色时不管用？**

因为**改的是类型**，不是"加一个值"。类型一变，所有使用点都失效。

> **"加值"编译器不管，"改类型"编译器全管。**
> 所以设计时如果预判某个维度会扩展，**把它做成类型（而不是枚举值），
> 就能把未来的改动成本转嫁给编译器。**

#### 7.6.3 三处的改法

**`join()` 从"拒绝重复"改成"追加"，但要加一个上限：**

```java
@Override
public void join(Participant participant) {
    Objects.requireNonNull(participant, "参与者不能为 null");
    if (closed) {
        throw new IllegalStateException(...);
    }
    Role role = participant.role();
    List<Participant> group = byRole.computeIfAbsent(role, r -> new ArrayList<>());
    if (group.size() >= MAX_PER_ROLE) {
        throw new IllegalArgumentException(
                "角色「" + role.displayName() + "」最多 " + MAX_PER_ROLE + " 人");
    }
    group.add(participant);
    participants.add(participant);
}
```

**`deliver()` 从"取一个人"变成"取一组人，两层循环"：**

```java
for (Role role : targets) {
    List<Participant> group = byRole.get(role);
    if (group == null || group.isEmpty()) {
        System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
        continue;
    }
    for (Participant target : group) {
        try {
            target.receive(message);
            delivered++;
        } catch (RuntimeException e) {
            ...
        }
    }
}
```

**`describeTargets()` 要拼接一组人的名字：**

```java
private String describeTargets(List<Role> targets) {
    List<String> parts = new ArrayList<>();
    for (Role role : targets) {
        List<Participant> group = byRole.get(role);
        if (group == null || group.isEmpty()) {
            parts.add(role.displayName() + "（未加入）");
        } else {
            parts.add(group.stream().map(AfterSalesHub::label).collect(Collectors.joining("/")));
        }
    }
    return String.join("、", parts);
}
```

**路由行会变成：**

```text
      路由 → 客服-小王/小李/小陈、客户-李女士
```

#### 7.6.4 一个被打破的语义

**`close()` 里的 `participant != from` 会出问题吗？**

不会 —— 它遍历的是 `participants`（所有加入的人），排除发起方。**三个客服里的人发起关闭，另外两个照常收到。** 这是对的。

**但 `escalate()` 会出问题：**

```java
deliver(message, List.of(Role.SUPERVISOR, Role.AGENT, Role.NOTIFIER));
```

**`Role.AGENT` 现在对应三个人。** 升级消息会发给三个客服。

**这可能是对的**（三个客服都该知道这单被升级了），**也可能是错的**（只有"主客服"该知道，另外两个只是协助）。

**这个歧义说明：`route()` 返回 `List<Role>` 这个设计，在"一个角色多个人"的场景里丢掉了"给哪一个"的信息。**

**要修，`route()` 的返回类型就得从 `List<Role>` 变成 `List<Participant>` 或 `List<Address>`（角色 + 序号）。**

> **"一个角色一个人"这个假设，藏在一个字段的类型里（`Map<Role, Participant>`）。**
> 它不是一个写下来的决定 —— 它是 `EnumMap` 的类型推出来的。
> **这就是为什么"选类型"比"写注释"更重要：类型会逼你面对假设，注释不会。**

### 7.7 如果改用观察者模式

**这是最常见的问题："中介者和观察者到底有什么区别？"**

**最短的回答：观察者是"通知所有订阅者"，中介者是"通知规则指定的人"。**

**用代码对比最清楚。**

#### 7.7.1 观察者版的"售后工单"

```java
// 设计推演：观察者版本的伪代码，不是本项目代码
public interface TicketObserver {
    void onTicketEvent(TicketEvent event);
}

public final class TicketSubject {
    private final List<TicketObserver> observers = new ArrayList<>();

    public void subscribe(TicketObserver observer) {
        observers.add(observer);
    }

    public void publish(TicketEvent event) {
        for (TicketObserver observer : observers) {
            observer.onTicketEvent(event);       // 注意：全部通知，没有筛选
        }
    }
}
```

**客户提个问，会发生什么？**

```java
subject.publish(new TicketEvent("提问", "耳机左耳没声音，能退吗？"));
```

**六个订阅者全部收到。** 仓库收到了一条"客户提问"，财务也收到了一条。

**这正是原项目的 `sendMessage` 干的事**（发给除自己外的所有人）。

**所以观察者模式不是"中介者的升级版"，它是"另一种拓扑"。**

#### 7.7.2 三个决定性差异

| | 观察者 | 中介者（本项目） |
|---|---|---|
| **收件人** | 所有订阅者 | `route()` 计算出的角色列表 |
| **筛选发生在哪** | **订阅者自己**（`onTicketEvent` 里 `if (event.type() != ...) return;`） | **中枢里**（`route()`） |
| **发送方知道收件人吗** | 不知道（它只知道"有人会收到"） | 不知道（但它知道"中枢会算"） |
| **谁承担"该不该处理"** | 每个订阅者重复判断 | 中枢集中判断一次 |
| **双向通信** | 难（订阅者要反向通知，得自己也变成被观察者） | 天然（每个人都是 `Participant`，都能 `say`） |

**第三行是最容易被忽略的：观察者是单向广播，中介者是双向星形。**

在售后场景里，客服要能回客户 —— 观察者模式下，客服得：

1. 持有 `TicketSubject` 的引用，或者
2. 自己被别人订阅，然后 `publish` 一条"答复"事件。

**第 2 条会导致所有角色互相订阅 —— 退回网状结构。**

#### 7.7.3 如果硬用观察者实现"只给客服"

```java
// 设计推演：反例
public final class AgentObserver implements TicketObserver {
    @Override
    public void onTicketEvent(TicketEvent event) {
        if (event.type() != Type.QUESTION) {
            return;                     // 客户提问才处理
        }
        ...
    }
}
```

**这段 `if` 就是"路由规则"，只不过它被复制到了每个订阅者里。**

| 角色 | 需要的 `if` |
|---|---|
| 客服 | `QUESTION` / `STOCK_SHORTAGE` / `ESCALATION` / `ARCHIVE` |
| 财务 | `STOCK_SHORTAGE` / `ARCHIVE` |
| 仓库 | `ARCHIVE` |
| 客户 | `ANSWER` / `REFUND_CONFIRMED` / `ARCHIVE` |

**16 个判断，散在 6 个类里。**

**而中介者把它们收成 7 行 `case`：**

```java
return switch (kind) {
    case QUESTION -> from == Role.CUSTOMER ? List.of(assignee) : List.of();
    case ANSWER -> (from == Role.AGENT || from == Role.SUPERVISOR) ? List.of(Role.CUSTOMER) : List.of();
    case STOCK_SHORTAGE -> from == Role.WAREHOUSE ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER) : List.of();
    case REFUND_CONFIRMED -> from == Role.FINANCE ? List.of(Role.CUSTOMER, Role.AGENT) : List.of();
    case NOTICE -> List.of(Role.CUSTOMER);
    case ESCALATION, ARCHIVE -> List.of();
};
```

**七行，一处。**

#### 7.7.4 什么时候观察者更合适

**三个信号：**

1. **收件人不需要筛选**（所有订阅者都该处理，比如"配置变更通知"）；
2. **发送方不该知道有哪些人**（比如框架级的生命周期事件）；
3. **一对多、单向、无回传**（比如日志、指标、UI 刷新）。

**本项目三个信号全不满足：** 收件人要筛、发送方需要知道"谁会回我"、必须双向。

> **选择标准不是"哪个模式更高级"，而是"规则该写在哪"。**
> 规则天然集中（售后流程是一张表）→ 中介者；
> 规则天然分散（每个订阅者自己的业务）→ 观察者。

### 7.8 如果改用责任链模式

**需求换成："客服解决不了 → 转主管 → 主管解决不了 → 转经理"。**

**这个需求看起来像中介者，实际上完全不同：它是线性的，不是星形的。**

#### 7.8.1 责任链版的实现

```java
// 设计推演：责任链版本，不是本项目代码
public abstract class Handler {
    private Handler next;

    public Handler setNext(Handler next) {
        this.next = next;
        return next;
    }

    public void handle(Ticket ticket) {
        if (canHandle(ticket)) {
            doHandle(ticket);
        } else if (next != null) {
            next.handle(ticket);
        }
    }

    protected abstract boolean canHandle(Ticket ticket);

    protected abstract void doHandle(Ticket ticket);
}
```

**一条请求，沿着链走，直到有人处理。**

#### 7.8.2 关键差异：一个走了，一个没走

| | 责任链 | 中介者 |
|---|---|---|
| 收件人数量 | **1**（第一个能处理的） | **N**（`route()` 指定的全部） |
| 是否继续传递 | 处理完通常停 | 每个收件人独立处理，互不影响 |
| 谁决定"给下一个" | `Handler` 自己 | 中枢的 `route()` |
| 加一个新的处理者 | 改链的组装代码 | 改 `route()` 的一个 case |
| 适合的场景 | 审批、过滤、异常处理 | 协同、广播、多方通知 |

**最根本的差异是"消息会不会被消费掉"：**

- 责任链里，`handle()` 走到某一个节点就停了 —— 只有一个人做事；
- 中介者里，`deliver()` 循环遍历所有人 —— **每个人都做事**。

**本项目的缺货上报：客服要退款、财务要冻结发票、客户要知道。三件事同时发生。**

**用责任链实现会变成：**

```java
// 反例：责任链版的缺货上报
warehouseHandler.setNext(financeHandler).setNext(customerHandler);
warehouseHandler.handle(shortageTicket);
```

**第一个 `canHandle` 返回 `true` 的处理器会处理，然后 `next` 不会被调用。**

**要让它继续传递，就得写成"处理完继续调 next"：**

```java
// 反例：处理完继续传
protected void doHandle(Ticket ticket) {
    System.out.println("客服：收到缺货，这单走退款。");
    if (next != null) {
        next.handle(ticket);        // 手动继续
    }
}
```

**这样写能跑，但代价是：**

1. **每个处理者都得记得调 `next`** —— 漏一个，后面的都收不到；
2. **"谁能处理"的判断从 `canHandle` 变成了"调不调 next"** —— 语义变模糊了；
3. **链的顺序成了硬编码** —— 想改成"财务先处理"，得重新组装链。

**而中介者的版本里，顺序是一行 `List.of(...)`，改顺序不需要动任何处理者的代码。**

> **责任链的"链"是结构（谁指向谁），中介者的"路由表"是数据（一个 List）。**
> 结构要改代码，数据只改字面量。

### 7.9 如果改用外观模式

**外观模式（Facade）和中介者在代码形状上很像：都有一个"中心的类"。**

**区别在于调用方向。**

| | 外观 | 中介者 |
|---|---|---|
| 调用方 | **外部系统**（客户端代码） | **内部同事**（参与者） |
| 被调方 | 子系统（多个类） | 中枢（一个） |
| 有没有回传 | **没有**（外观调用完就结束） | **有**（中枢反过来调参与者） |
| 子系统知道外观吗 | **不知道**（单向依赖） | **知道**（`Participant` 持有 `hub`） |

**最关键的一行：外观的子系统不认识外观，中介者的同事认识中枢。**

```java
// 外观：子系统完全不知道 Facade 存在
public final class UserServiceFacade {
    private final UserDAO dao = new UserDAOImpl();

    public void register(String name) {
        dao.insert(name);          // 单向：Facade → DAO
    }
}
```

```java
// 中介者：参与者持有 hub 引用，并且会回调它
public final class Customer implements Participant {
    private final CollaborationHub hub;      // ← 认识中枢

    @Override
    public void receive(Message message) {
        ...
        // 可以反过来调 hub().say(...) —— 双向
    }
}
```

**这个"认不认识"的差异决定了：**

- 外观**不能**用来实现"同事之间互相通知"（因为子系统不认识外观，没法回调）；
- 中介者**必须**让参与者认识中枢（否则参与者没法主动发言）。

**如果让参与者不认识中枢会怎样？**

那就得在构造完之后注入：

```java
// 反例：两段式构造
Customer customer = new Customer("李女士");
customer.setHub(hub);            // ← 忘了这一步，say() 就 NPE
```

**这正是原项目 `User.setMediator()` 的写法，也是探针 6 演示的问题。**

> **外观是"我给外部一个简单入口"，中介者是"我给内部一个统一出口"。**
> 方向相反，不能互相替代。

### 7.10 五个模式的边界对照表

| 维度 | 中介者 | 观察者 | 责任链 | 外观 | 命令 |
|---|---|---|---|---|---|
| **谁发起** | 同事 | 主题 | 调用方 | 外部客户端 | 调用方 |
| **谁接收** | `route()` 指定 | 所有订阅者 | 链上第一个能处理的 | 子系统 | 接收者 |
| **收件人数量** | N | N（全部） | 1 | N（但无回传） | 1 |
| **方向** | 双向 | 单向 | 单向 | 单向 | 单向 |
| **规则写在哪** | 中枢 | 订阅者各自 if | 链的组装 | 无需规则 | 命令对象 |
| **典型场景** | 多方协同 | 事件广播 | 审批/过滤 | 简化子系统 | 撤销/队列 |
| **加一个参与者** | 加枚举 + 写类 + 加 `join` | 加一个订阅者 | 改链 | 改外观 | 无需改 |
| **加一种事件** | 加枚举 + 加 `case`（编译器强制） | 加事件类型 | 加处理器 | 无 | 加命令类 |

**最后两行是"扩展成本"的具体数字。** 注意中介者的"加一种事件"是**编译期强制**的，而观察者是**运行期隐式**的（新事件发出去，没人订阅也不报错）。

> **同样是"加一种事件"，中介者会逼你面对"谁该收到"，观察者不会。**
> 这是中介者最大的优势，也是它最大的负担 —— 因为它真的要求你想清楚。

### 7.11 扩展时必须同步的三处清单

**任何时候改动这个项目，先扫一眼这张表：**

| 你改了什么 | 必须同步检查 | 编译器会提醒吗 |
|---|---|---|
| `Role` 加枚举值 | `route()` 的所有 `==` 比较、`Client.build()` | **不会** |
| `MessageKind` 加枚举值 | `route()`（强制）、六个 `receive()`（业务判断） | **只提醒 `route()`** |
| 改 `byRole` 的类型 | `join()` / `deliver()` / `describeTargets()` | **会，三处全报** |
| 改 `Message` 的字段 | 所有 `new Message(...)`（3 处）+ 所有 `message.xxx()` | **会** |
| 加一个 `Participant` 方法 | 六个实现类（如果不用 `default`） | **会** |
| 加一条 `route()` 规则 | 无需同步 | — |
| 改 `close()` 的广播逻辑 | `participants` 的所有写入点 | **不会** |

**这张表里"不会"的三行，就是本项目最危险的三处。**

**它们危险的原因是同一个：改动的是一个"值的集合"，而不是"一个类型"。**

> **凡是"加一个值"的改动，编译器都帮不上忙；凡是"改一个类型"的改动，编译器全包。**
> 所以设计时能选类型的，就不要选枚举 —— **除非你需要"穷尽性检查"这个回报。**
> 本项目的 `MessageKind` 用枚举，正是为了拿到 `route()` 那个编译错误。

---

## 8. 优缺点与误区

### 8.1 适用场景：从代码形状反推

§3.4 给的是**业务判断**（"你的系统满不满足这五条"）。本节给的是**代码判断** ——
不需要懂业务，只要看现有代码的形状，就能判断"该不该上中介者"。

#### 信号一：出现"广播 + 收件人自己 if 过滤"

```java
// 典型症状
for (User user : users) {
    user.receive(message);          // 全都发
}

// 然后在每个 User 子类里
public void receive(Message m) {
    if (m.type() != Type.QUESTION) return;     // ← 这里有 if
    ...
}
```

**这是最强烈的信号。** 因为它说明：

- 路由规则**已经存在**，只是被复制到了每个收件人里；
- 加一种消息类型时，要改 N 个 `if`（N = 收件人数量）；
- **没有一个地方能回答"这条消息谁会处理"。**

**中介者做的事就是把那些 `if` 收进 `route()`。**

#### 信号二：出现"a 调用 b、b 调用 c"的链式协调

```java
// 典型症状
public void reportShortage(String sku) {
    agent.receive("缺货：" + sku);       // 仓库调客服
    finance.receive("缺货：" + sku);     // 仓库也调财务
    customer.receive("缺货：" + sku);    // 仓库还调客户
}
```

**症状是"一个类里出现了三个别的类的字段"。**

`Warehouse` 里同时有 `Agent`、`Finance`、`Customer` 三个引用 —— **它就变成了半个中介者。**

**为什么是"半个"？** 因为 `Customer` 提问时不经过它。**于是同一套路由规则被拆成了两半：仓库这边的三条线，和客户那边的另一套。**

**中介者做的事就是把两半合并成一个。**

#### 信号三：出现"某两个类互相持有对方的引用"

```java
// 典型症状
public class Agent {
    private Customer customer;      // 客服认识客户
}

public class Customer {
    private Agent agent;            // 客户认识客服
}
```

**双向引用是网状结构的标志。**

两两互持在 2 个类时还能忍，到 6 个类就是 15 对（`C(6,2) = 15`）。

**改成星形之后，引用数从 15 对降到 6 条（每个同事 → 中枢）。**

#### 信号四：出现"新加一个角色要改很多文件"

**测试方法：** 数一下"加一个第七个角色"要动几个文件。

- 如果是 2 个（枚举 + 新类 + `join`），成本可控；
- 如果是 8 个（每个已有角色都要加一个"认识新人"的字段），那就该上中介者了。

**本项目这个数字是 2（`Role.java` + 新角色类），加 `join` 那一行算第 3 个。**

#### 信号五：出现"同一件事需要在多处记录"

```java
// 典型症状：同样的日志出现在三个地方
public class Agent {
    public void receive(Message m) {
        log.info("收到消息：{}", m);
    }
}
public class Finance {
    public void receive(Message m) {
        log.info("收到消息：{}", m);
    }
}
```

**这不是重复代码，是"投递这件事本身没有被建模"。**

中介者里，这一行出现在 `deliver()` 里一次：

```java
target.receive(message);
```

**想加"投递审计"，只改一处。**

#### 五个信号的自查表

| 信号 | 本项目怎么解决的 | 代码位置 |
|---|---|---|
| ① 广播 + 收件人 if 过滤 | `route()` 集中判断 | `AfterSalesHub.java:188-205` |
| ② 链式协调 | 仓库只 `say()`，不 `receive()` 别人 | `Warehouse.java` 全文无其他角色引用 |
| ③ 双向引用 | 六个参与者只持有 `hub` | `Participant.hub()` |
| ④ 加角色成本 | 2 个文件 | `Role` + `QualityInspector` 推演 |
| ⑤ 重复的投递日志 | `deliver()` 一处 | `AfterSalesHub.java:292-311` |

**三条以上成立 → 该上中介者。**
**一条都不成立 → 别上。**（否则就是 §4.5 说的"为了模式而模式"）

### 8.2 设计代价：五个具体的

**中介者不是免费的。它买到了"规则集中"，付出的代价是下面五条。**

#### 代价一：中枢会成为"最大的类"

**本项目：`AfterSalesHub` 375 行 / 157 个代码行，是全项目最大的类。**

对照其他 12 个文件：

| 文件 | 物理行 | 代码行 |
|---|---|---|
| `AfterSalesHub` | **375** | **157** |
| `Client` | 306 | 154 |
| `Participant` | 111 | 16 |
| `CollaborationHub` | 106 | 11 |
| `Agent` | 87 | 35 |
| 其余 8 个 | 71~86 | 15~36 |

**中枢的代码行数是第二大的（`Client` 是演示入口，不算业务类）的 4.5 倍。**

**这是模式的必然，不是实现的缺陷。**

因为"所有交互规则"都住在这里。**规则有 7 种消息 × 6 个角色 = 42 个格子，其中 7 行是有内容的。**

**如果规则继续膨胀（比如加到 20 种消息），中枢会失控。**

**三种拆法：**

1. **按消息类型拆成多个 `Route` 实现**（`QuestionRoute` / `StockShortageRoute` ...），中枢只做分发；
2. **把 `route()` 抽成一个独立的 `RoutingTable` 类**，中枢只持有它；
3. **引入规则引擎**（如果能接受外部配置）。

**本项目一个都没做**，因为 157 行还在"一个人能读完"的范围内。

> **拆分的时机不是"类超过 N 行"，而是"你开始需要滚动才能找到某个 `case`"。**

#### 代价二：所有交互都经过一个点，它挂了全都挂

**本项目里 `AfterSalesHub` 是无状态的还是单例？**

**都不是 —— 每个工单一个实例。**

```java
AfterSalesHub hub = new AfterSalesHub("AS-2024-0917");
```

**这是有意的：中枢持有 `assignee` / `closed` / `delivered`，这些是"每张工单独立"的状态。**

**如果做成单例（`AfterSalesHub.INSTANCE`），会发生什么？**

- 所有工单共用同一个 `assignee` —— 工单 A 升级到主管，工单 B 也跟着变成主管；
- 所有工单共用同一个 `closed` —— 关一张单，全部关闭；
- 所有工单共用同一个 `participants` —— 所有工单的客户都在同一张名单里。

**这是一场灾难。** 而且灾难的起点是一行看起来很无辜的代码：

```java
private static final AfterSalesHub INSTANCE = new AfterSalesHub("GLOBAL");
```

> **"中介者"和"单例"是两个正交的决定。**
> 中介者是"结构"（谁和谁说话），单例是"生命周期"（有几个实例）。
> **把两者绑定在一起，是把结构问题误当成生命周期问题。**

**但在某些场景里，单例是对的：** 全局的聊天室（`ChatRoom`）、全局的事件总线。
判断标准：**"这个中介者管的规则，是全局的还是某一张单子的？"**

#### 代价三：加角色的路由遗漏是静默的

**这一点 §7.2.5 详细讲过，这里只补一句结论：**

> **中介者把"分散的 if"换成了"集中的表"，代价是"表里漏一行"这件事没有语法能发现。**
> 观察者模式反而不会漏 —— 因为它是"全都发"，漏了也是多发。

**这是一种"用可维护性换正确性保证"的交换。** 缓解手段是路由表快照测试（本项目未实现）。

#### 代价四：参与者与中枢绑定，不能脱离它单独测试

**想单独测 `Agent` 呢？**

```java
Agent agent = new Agent("小王", hub);      // 必须给一个 hub
```

**不能 `new Agent("小王")`。**

**要测 `Agent.receive()`，得先造一个 `hub`（哪怕是假的）。**

**本项目的 `BrokenNotifier` 就是一个例子：它必须接收 `hub`，尽管它永远不调用 `hub()`。**

```java
BrokenNotifier(CollaborationHub hub) {
    this.hub = hub;
}
```

**这是"接口 + 构造注入"的一个小代价：所有实现类都得接受一个可能用不到的依赖。**

**缓解手段：** 造一个 `NoOpHub`（所有方法空实现），或者用 `null`（但那违反了 §5.10 的 `requireNonNull`）。

**本项目选择"老实传入真实 hub"。** 因为 6 个角色里只有 1 个（`Notifier`）真的用不到 `hub`，不值得为它造一个 `NoOpHub`。

> **如果 6 个里有 4 个用不到 `hub`，那说明 `Participant` 接口设计有问题** ——
> 它把"发言能力"强加给了不需要发言的参与者。

#### 代价五：`deliver()` 是同步的，慢收件人会拖住所有人

**本项目里 `target.receive(message)` 是同步调用。六个角色的 `receive()` 都只是打印，所以"很快"。**

**但如果某个收件人真的做 IO：**

```java
// 假设 Notifier 真的发短信
public void receive(Message message) {
    smsClient.send(message.text());      // 假设耗时 2 秒
}
```

**那么 `customer.say(QUESTION, "...")` 会阻塞 2 秒**（如果这条消息也发给通知器的话）。

而 Demo 4 的升级，`deliver()` 发给三个人 —— **如果三个都有 IO，就是 6 秒。**

**这是"同步投递"的固有代价。**

**三种解法：**

1. **异步化 `deliver()`**（线程池 / CompletableFuture）—— 但 §7.4.3 那三个语义问题会立刻出现；
2. **只把"慢收件人"异步化**（`Notifier` 单独一个线程）—— 折中方案；
3. **接受它** —— 本项目选这个，因为演示代码不需要真发短信。

> **"同步"不是缺陷，"同步"是选择。**
> 它的回报是"`say()` 返回时，投递结果已经确定" ——
> `delivered` 计数是真实的，`failures` 是完整的。
> **异步化会同时拿走这两个回报。**

### 8.3 七个常见误解

#### 误解一："中介者就是消息队列"

**错。** 消息队列是**基础设施**（进程间、持久化、异步），中介者是**代码结构**（进程内、方法调用、同步）。

**本项目的 `AfterSalesHub` 和 Kafka 没有一行关系。** 它甚至不是线程安全的。

**判断标准：**

| | 中介者 | 消息队列 |
|---|---|---|
| 消息发给谁 | `route()` 当场算 | 订阅关系 |
| 送达保证 | 同步方法调用 | broker 的持久化 + ACK |
| 消息存多久 | 不存（打印完就没了） | 按配置保留 |
| 挂了会怎样 | 抛异常 | 消息留在队列里 |

**两者能配合（§7.4 讨论了怎么配合），但不能互相替代。**

#### 误解二："中介者一定能降低耦合"

**错。它降低的是"同事之间的耦合"，增加的是"同事对中枢的耦合"。**

**量化一下本项目的引用关系：**

| 引用 | 数量 |
|---|---|
| 六个参与者之间的引用 | **0** |
| 六个参与者 → `CollaborationHub` | 6 |
| `AfterSalesHub` → 六个参与者 | 1（`List<Participant>`） |
| `AfterSalesHub` → 五个值类型 | 5 |

**总数：6 + 1 + 5 = 12 条。**

**如果不用中介者，六个角色两两互持：**

```text
C(6, 2) = 15 条
```

**12 < 15，但差距不大。**

**真正的收益不在"条数"，在"形状"：**

- 网状：15 条边，每条边都要维护"谁能调谁"；
- 星形：6 条边指向同一个接口，**加第 7 个角色只需加 1 条边**（网状要加 6 条）。

> **中介者的收益是"边际成本恒定"，不是"总边数更少"。**
> 角色越多，优势越大；只有 2 个角色时，中介者是纯负担。

#### 误解三："中介者必须是一个大而全的 God Object"

**不是必须，是很容易变成。**

**本项目的 `AfterSalesHub` 做了五件事：**

1. 存名单（`participants` / `byRole`）；
2. 算路由（`route()`）；
3. 投递（`deliver()`）；
4. 管生命周期（`closed` / `escalate()` / `close()`）；
5. 统计（`delivered` / `failures`）。

**五件事，157 个代码行。**

**如果它开始做第六件事（比如"生成工单报表"），它就越界了。**

**越界的信号：**

- 出现和"消息投递"无关的方法（报表、导出、权限校验）；
- 出现对某个具体参与者的类型判断（`if (target instanceof Agent)`）；
- 出现业务规则之外的逻辑（`if (message.text().contains("投诉"))`）。

**本项目一条都没违反。** 特别是第二条：`AfterSalesHub` 里没有任何 `instanceof`，只有 `Role` 枚举比较。

#### 误解四："有了中介者，参与者之间就完全不认识了"

**不完全对。**

**参与者之间确实没有字段引用，但它们通过 `Message` 间接"认识"彼此 —— 因为 `Message.from` 是 `Role`。**

```java
public String describe() {
    return from.displayName() + " 的" + kind.label() + "：" + text;
}
```

**`Customer.receive()` 打印出了 `"仓库 的缺货上报"` —— 它知道这条消息来自仓库。**

**这不是耦合，这是"消息元数据"。** 区别在于：

- 耦合 = 持有引用，能调用对方的方法；
- 元数据 = 知道对方是谁，但**不能调用**。

**`Customer` 里拿不到 `Warehouse` 对象，只能拿到一个 `Role.WAREHOUSE` 枚举值。**

> **"知道对方是谁"和"能调用对方"是两件事。**
> 中介者切断的是后者，保留前者 —— 因为很多业务反应需要知道"谁说的"。

#### 误解五："`Participant` 应该是抽象类"

**这是一个非常常见的直觉，而且有道理 —— 但本项目选了接口。**

**抽象类版的写法：**

```java
// 反例：抽象类版
public abstract class AbstractParticipant {
    private final String name;
    private final CollaborationHub hub;

    protected AbstractParticipant(String name, CollaborationHub hub) { ... }

    public final String name() { return name; }
    public final CollaborationHub hub() { return hub; }

    public abstract Role role();
    public abstract void receive(Message message);

    public void say(MessageKind kind, String text) { hub.say(this, kind, text); }
    public void escalate(String reason) { hub.escalate(this, reason); }
    public void closeTicket() { hub.close(this); }
}
```

**能省掉什么？** `name()` 和 `hub()` 两个 getter 的重复，每个子类省 10 行。

**代价是什么？**

1. **`BrokenNotifier` 必须继承它** —— 但它连 `name` 都不想规范（它叫"短信网关（故障）"），也不想有 `say()` 能力；
2. **不能再继承别的类**（Java 单继承）；
3. **`final` 方法不能重写** —— 如果某个参与者想改写 `say()` 的行为（比如加节流），做不到。

**接口 + default 的方法保住了什么？**

```java
public interface Participant {
    Role role();
    String name();
    CollaborationHub hub();
    void receive(Message message);

    default void say(MessageKind kind, String text) { hub().say(this, kind, text); }
    default void escalate(String reason) { hub().escalate(this, reason); }
    default void closeTicket() { hub().close(this); }
}
```

**`say()` 是 `default` 的，所以任何实现类都可以重写它。** 这一点在 `BrokenNotifier` 上就有用 —— 虽然它没重写，但"能不重写"是自由度。

> **抽象类适合"共享状态"，接口适合"共享契约"。**
> `name` / `hub` 是状态（两个字段），`say` / `escalate` / `closeTicket` 是契约（三个动作）。
> **本项目选择让"状态"由每个类自己持有，"契约"由接口统一提供。**

**代价是每个类多写 10 行 getter；回报是零继承、完全自由。**

#### 误解六："`route()` 应该返回 `List<Participant>`，不该返回 `List<Role>`"

**这个想法很自然：既然要投递，为什么不直接给出收件人对象？**

**本项目的选择是 `List<Role>`，理由三条：**

**理由一：规则表不该依赖实例。**

```java
List<Role> route(Role from, MessageKind kind)
```

**签名里没有 `Participant`。** 所以 `route()` 可以脱离"当前有哪些人"单独测试 —— 哪怕名单是空的，它照样能算出该给哪些角色。

**如果返回 `List<Participant>`，`route()` 就必须持有 `byRole`，且"收件人没来"这种情况无法表达。**

**理由二：能表达"该给财务，但财务没来"。**

```java
List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
```

**这是三个角色的意图，不管财务在不在。** 到了 `deliver()` 才变成"财务未加入，跳过"。

**如果 `route()` 返回 `List<Participant>`，"财务没来"就变成了"列表里只有两个元素" —— 意图丢了。**

**证据就在输出里：**

```text
      路由 → 客服-小李、财务（未加入）、客户-王先生
```

**这一行能打出"财务（未加入）"，正是因为 `route()` 给的是角色，`describeTargets()` 才知道该去查谁。**

**理由三：`List<Role>` 是不可变的枚举引用，没有生命周期问题。**

**如果返回 `List<Participant>`，那么 `route()` 返回的列表里持有的是活对象。一旦某人在投递过程中退出（本项目不支持 `leave()`），这个列表就会指向"已经不在名单里的人"。**

> **规则和实例分离，是中介者能保持"纯函数式规则表"的前提。**
> 一旦 `route()` 里出现 `Participant`，它就和运行时的名单绑死了。

#### 误解七："中介者模式必须有一个 `Mediator` 接口"

**本项目的 `CollaborationHub` 不是 `Mediator`，`Participant` 也不是 `Colleague`。**

**命名不重要。** 但如果**每个模式都叫 `Mediator` / `ConcreteMediator` / `Colleague` / `ConcreteColleague`，读代码的人会以为在背 GoF。**

**更重要的是：本项目只有一个具体中介者（`AfterSalesHub`），为什么还要接口？**

**三个真实理由：**

1. **`Participant` 需要它。** 六个参与者字段的类型是 `CollaborationHub`，不是 `AfterSalesHub` —— **否则参与者就依赖了具体实现**；
2. **`BrokenNotifier` 需要它。** 它只认识接口，所以它不知道自己加入的是"售后中枢"还是别的什么；
3. **测试需要它。** 可以造一个假的 `CollaborationHub` 来测 `Agent`。

**如果只有一个实现，接口是不是过度设计？**

**在这个项目里不是。** 因为 `Participant` 是一个**要被第三方实现的接口**（`BrokenNotifier` 就是第三方实现）。**当一个接口的"实现方"多于"调用方"时，接口是必须的。**

> **判断"要不要接口"的标准不是"有几个实现"，而是"有没有人会从外面实现它"。**
> 只有一个实现 + 没有外部实现者 → 不需要接口（YAGNI）；
> 只有一个实现 + 参与者必须依赖抽象 → **需要接口**。

### 8.4 中介者最容易退化的三种形态

**"退化"指的是：代码里还有 `Mediator` 这个名字，但模式的核心已经没了。**

#### 退化一：变成"转发器"（只转发，不决策）

```java
// 反例
public class ChatRoomMediatorImpl implements ChatRoomMediator {

    @Override
    public void sendMessage(String message, User user) {
        for (User u : users) {
            if (u != user) {
                u.receive(message);          // ← 没有任何筛选
            }
        }
    }
}
```

**这是原项目的实现。** 它有 `Mediator` 接口、有 `registerUser`、有 `sendMessage` —— **但它只是一个 for 循环。**

**判断标准：`route()` 里有没有"依赖消息内容的判断"。**

本项目的 `route()` 有 6 种 `case` + 3 处 `from == Role.X` 判断 —— **有决策。**

**如果哪天所有 `case` 都返回 `List.of(所有人)`，那这个中介者就该删掉了，用观察者更合适。**

#### 退化二：变成"上帝对象"（什么都管）

```java
// 反例
public class OrderMediator {
    public void placeOrder(...) { ... }
    public void sendEmail(...) { ... }
    public void calculateTax(...) { ... }      // ← 和"协同"无关
    public void generatePdf(...) { ... }       // ← 和"协同"无关
    public void checkPermission(...) { ... }   // ← 和"协同"无关
}
```

**症状：类名是 `XxxMediator`，但里面有一半方法和"谁跟谁说"无关。**

**本项目的 `AfterSalesHub` 有 15 个成员（1 个构造器 + 14 个方法），全部围绕"名单 / 路由 / 投递 / 生命周期"：**

```text
1 个构造器                              ← 校验工单号
join / say / escalate / close          ← 4 个动作（接口里）
roster / assignee / deliveryFailures   ← 3 个查询（接口里）
deliveredCount / closed / ticketId     ← 3 个查询（实现类里）
route / deliver / describeTargets      ← 3 个私有方法
label                                   ← 1 个私有静态工具
```

**没有一个方法在做"业务计算"。** 退款金额算多少、库存够不够 —— 这些都不在中枢里。

> **中介者管"消息该去哪"，不管"消息里的事怎么办"。**
> 一旦它开始计算业务结果，它就不再是中介者了。

#### 退化三：变成"状态机"（把所有状态迁移都塞进来）

**这个退化最难识别，因为它的代码看起来很"规整"。**

```java
// 反例
public void transition(Event event) {
    switch (state) {
        case CREATED -> {
            if (event == PAY) { state = PAID; notifyFinance(); }
        }
        case PAID -> {
            if (event == SHIP) { state = SHIPPED; notifyWarehouse(); }
        }
        ...
    }
}
```

**这段代码里，`state` 是"工单状态"，`transition` 是"状态迁移" —— 它其实是状态模式的活。**

**中介者里可以有状态（本项目的 `assignee` / `closed`），但状态的迁移必须是"由协同动作触发的"，不能是"中枢自己判断的"。**

**本项目的两个状态迁移：**

```java
assignee = Role.SUPERVISOR;      // 在 escalate() 里
closed = true;                   // 在 close() 里
```

**都是"某个参与者发起了动作，中枢执行"的模式。** 中枢没有"自己判断该不该升级"的能力 —— 它只在被调用时迁移。

> **中介者是"被动响应"，状态机是"主动判断"。**
> 本项目里没有一行 `if (该升级了)` —— 升级永远是参与者 `escalate()` 出来的。

### 8.5 本项目的已知局限（诚实清单）

**这份文档一直在说"本项目怎么解决的"，本节说"本项目没解决什么"。**

| 局限 | 具体表现 | 为什么不修 |
|---|---|---|
| **① 非线程安全** | `participants` / `byRole` / `delivered` / `assignee` / `closed` 全是普通字段 | 演示代码；加锁会掩盖"每次 Demo 一张新工单"这个结构 |
| **② `join()` 无"在场"校验** | 任何 `Participant` 都能 `join`，只要角色没被占 | 挡住的是"演示代码不会犯的错"（§6.6 讨论过） |
| **③ 投递中没有防"中途 join"** | `close()` 遍历 `participants` 时若有 `join`，会 CME | 触发条件极窄（§6.9 讨论过） |
| **④ 没有路由表测试** | `route()` 的 7 个分支没有单元测试 | 项目整体不含测试框架（原项目也没有） |
| **⑤ `delivered` 只是计数** | 无法回答"哪条消息送到了谁" | 需要 ID + 明细列表，属于投递日志而非模式演示 |
| **⑥ `Notifier` 的渠道是硬编码的** | `"外发短信给主管手机"` 写死在字符串里 | 真实系统里这是配置项；这里为了输出可读 |
| **⑦ 没有 `leave()`** | 参与者只能加入，不能退出 | 售后工单的成员是稳定的；加 `leave()` 会让 `participants` / `byRole` 的双写一致性变复杂 |
| **⑧ 消息正文不脱敏** | `Notifier` 直接把 `message.text()` 拼进"外发短信" | `Notifier` 的 `default` 分支已经挡住了"不该外发的类型"，但没挡"正文里含手机号" |

**第 ⑧ 条值得单独说。**

现在 `Notifier` 的安全边界是**按消息类型**的：

```java
case ESCALATION -> ...外发短信...
case ARCHIVE -> ...外发站内信...
default -> ...不在外发白名单，丢弃...
```

**它保证"只有升级和归档会外发"。但它不保证"外发的内容里没有敏感信息"。**

假设客户在提问里写了手机号，然后申请升级：

```text
[客户-李女士] 申请升级：我手机 138xxxx1234，等了 3 天没人处理
[站内信网关] 外发短信给主管手机：「我手机 138xxxx1234，等了 3 天没人处理」
```

**手机号被原样外发了。**

**真实系统需要在 `Notifier` 里加脱敏：**

```java
// 真实系统需要，本项目未实现
private static String mask(String text) {
    return text.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
}
```

**为什么本项目不加？**

因为演示代码的输出需要**可读、可对照**。加了脱敏，输出里就是 `138****1234`，读者得多花一秒确认"这是我输入的那句话"。

> **演示代码允许"为了让输出可读"而省略生产必需的处理。**
> **但省略的必须在文档里点名。** 否则读者会以为"这样就能上线了"。

**这张表就是这份文档的"免责声明"。** 八条局限，每一条都写清了"为什么不修" ——
而不是假装它们不存在。

---

## 9. 面试与自测

### 9.1 十个高频问题

**每个问题都按"面试官在考什么 → 答法骨架 → 追问"三段写。**

用本项目的真实代码作为依据 —— 不要背模式定义，说代码。

#### 问题一：中介者模式和观察者模式的区别？

**面试官在考什么：** 你知不知道两者都是"一对多"，区别在哪。

**答法骨架（三句话）：**

1. **拓扑相同，规则位置不同。** 都是星形：同事/订阅者都只认识中枢/主题。但观察者的主题**不筛选**收件人（全都通知），中介者的中枢**筛选**（`route()` 算出该给谁）。
2. **双向 vs 单向。** 观察者是单向广播（主题 → 订阅者，订阅者不反向通知主题）。中介者是双向的：同事既能收（`receive()`），也能发（`say()`）。
3. **"该不该处理"写在哪。** 观察者里写在每个订阅者的回调里（一堆 `if`）；中介者里写在中枢的规则表里（一处 `switch`）。

**用代码说：**

```java
// 观察者：全都通知
for (TicketObserver observer : observers) {
    observer.onTicketEvent(event);
}

// 中介者：先算，再投递
List<Role> targets = route(from.role(), kind);
for (Role role : targets) { ... }
```

**追问："那观察者能不能替代中介者？"**

不能。硬用观察者实现"只给客服"，就把路由规则复制到了每个订阅者里 —— 本项目 6 个角色需要 16 个 `if`，而中介者只要 7 行 `case`。

#### 问题二：中介者模式和外观模式的区别？

**面试官在考什么：** 你会不会把"中心化的类"一律当中介者。

**答法骨架：**

1. **调用方向相反。** 外观是"外部客户端 → 外观 → 子系统"，单向；中介者是"同事 → 中枢 → 同事"，双向。
2. **子系统认不认识外观。** 外观的子系统**完全不知道**外观存在；中介者的同事**必须持有**中枢的引用。
3. **动机不同。** 外观是为了"给外部一个简单入口"，中介者是为了"让内部不互相认识"。

**关键判据：**

> **外观的收件方不会回调外观；中介者的收件方一定会。**

**追问："如果同事不持有中枢引用，还能叫中介者吗？"**

不能。因为他就没法主动发言了 —— 那就退化成"外观 + 回调"。

#### 问题三：中介者模式和责任链模式的区别？

**面试官在考什么：** 你分不分得清"扇出"和"传递"。

**答法骨架：**

1. **收件人数量。** 中介者是 N（`route()` 给几个就是几个）；责任链是 1（第一个能处理的）。
2. **消息会不会被消费。** 责任链里 `handle()` 走到某个节点就停；中介者里每个人独立处理，互不影响。
3. **谁决定下一个。** 责任链由 `Handler` 自己 `next.handle()`；中介者由中枢的 `route()` 决定。

**用本项目的缺货上报说明：**

```text
客服：收到缺货，走退款
财务：冻结发票，准备退款
客户：知道了
```

**三件事同时发生** —— 责任链做不到（它只能有一个人处理）。

**追问："那审批流程该用哪个？"**

**责任链。** 因为审批是"一级批完不够，再往上一级"，天然线性且互斥。**如果审批的每一级都要"同时通知所有人"，那就该中介者。**

#### 问题四：中介者会不会变成上帝对象？怎么避免？

**面试官在考什么：** 你知道模式的代价吗。

**答法骨架：**

1. **会，而且很容易。** 因为"所有交互规则"天然都想往里塞。
2. **本项目的具体代价：** `AfterSalesHub` 375 行 / 157 个代码行，是最大的业务类（第二大的 `Agent` 只有 35 个代码行，4.5 倍）。
3. **三条边界线：**

| 边界 | 该做 | 不该做 |
|---|---|---|
| 管什么 | "消息该去谁那" | "消息里的事怎么办"（算钱、查库存） |
| 类型依赖 | `Role` 枚举比较 | `instanceof Agent` 这种具体类型判断 |
| 状态迁移 | 由参与者发起的动作触发 | 中枢自己 `if (该升级了)` 判断 |

**本项目的自律证据：**

```java
// 全项目只有两处状态迁移，都在被调用时发生
assignee = Role.SUPERVISOR;      // 在 escalate() 里
closed = true;                   // 在 close() 里
```

**追问："157 行还算可控，2000 行怎么办？"**

三种拆法（§8.2）：按消息类型拆成多个 `Route` 实现、把 `route()` 抽成独立的 `RoutingTable`、或者上规则引擎。**但拆分时机不是行数，而是"你需要滚动才能找到某个 case"。**

#### 问题五：路由规则应该放在中介者还是同事里？

**面试官在考什么：** 你理解"规则集中"的价值吗。

**答法骨架：**

1. **放中介者。** 这是模式的全部意义。
2. **判断方法：** 问"加一种消息类型，要改几个文件？"
   - 放中介者 → 1 处（`route()` 的一个 `case`），编译器还会强制你改；
   - 放同事里 → N 处（每个收件人的 `if`），没有任何编译期保护。
3. **但有一种例外：** 如果规则是"这个角色自己的业务判断"（比如"财务只在金额 > 100 时才处理"），那它属于收件人，不属于路由表。

**用本项目的例子区分：**

```java
// 属于路由表：谁该收到（跨角色的知识）
case STOCK_SHORTAGE -> from == Role.WAREHOUSE
        ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)
        : List.of();

// 属于收件人：收到之后做什么（本角色的知识）
case STOCK_SHORTAGE -> System.out.println("财务：冻结原发票，准备全额退款。");
```

**追问："如果'金额 > 100 才通知财务'这种规则呢？"**

**那要看金额从哪来。** 如果 `Message` 里没有金额字段，`route()` 就算不出来 —— 这时候要么给 `Message` 加字段，要么把这个判断放进 `Finance.receive()`。

**本项目的选择是后者**（`Message` 只有 `from` / `kind` / `text` 三个字段，没有业务数据）。**代价是"财务会收到每条缺货消息"，收益是"路由表保持纯粹"。**

#### 问题六：`route()` 为什么返回 `List<Role>` 而不是 `List<Participant>`？

**面试官在考什么：** 你会不会把"规则"和"实例"混在一起。

**答法骨架（三个理由）：**

1. **规则表不该依赖实例。** 返回 `Role` 时，`route()` 可以脱离"当前有哪些人"单独测试 —— 名单空着也能算。
2. **能表达"该给财务，但财务没来"。** 输出里那行 `路由 → 客服-小李、财务（未加入）、客户-王先生` 就是证据。
3. **避免活对象泄漏。** 如果返回 `List<Participant>`，列表里持有的是活引用；一旦支持 `leave()`，列表就会指向已退出的人。

**追问："那投递时还得再查一次 `byRole`，不是多一次开销？"**

是。**但这个开销是 `EnumMap` 的一次数组访问（O(1)），代价可以忽略。** 而"规则和实例分离"带来的是可测试性和可读性 —— 这笔交易很划算。

**如果真要优化，** 可以让 `route()` 返回 `List<Role>`，`deliver()` 里转成 `List<Participant>` 再循环 —— 但本项目没做，因为 6 个元素不值得。

#### 问题七：中介者的状态该由谁管？

**面试官在考什么：** 你知不知道"有状态"是中介者和"转发工具类"的分水岭。

**答法骨架：**

1. **必须由中介者管。** 因为状态的读取方和写入方都是中枢：`route()` 读 `assignee`，`escalate()` 写 `assignee`。
2. **如果让同事管，** 那每个同事都要知道"当前负责方是谁" —— 6 个类各存一份副本，同步就是灾难。
3. **如果让工具类管（无状态），** 就没人能改 —— 因为工具类是静态的。

**本项目的两个状态：**

| 字段 | 谁写 | 谁读 |
|---|---|---|
| `assignee` | 只有 `escalate()` | `route()` 的 `case QUESTION` |
| `closed` | 只有 `close()` | `say()` / `escalate()` / `close()` / `join()` |

**"谁写"这一列是重点：每个字段只有一个写入点。**

```java
assignee = Role.SUPERVISOR;      // 全项目搜 "assignee =" 只有这一处
closed = true;                   // 全项目搜 "closed =" 只有这一处
```

**追问："如果要做'撤销升级'呢？"**

那就得加第二个写入点（`assignee = Role.AGENT`）。**而这时候"写入点唯一"的纪律就破了 —— 应该抽一个 `assign(Role)` 的私有方法，两个调用点都走它。**

> **状态迁移点应该只有一个入口，即使它有多个触发条件。**
> 这是状态模式的核心纪律，中介者里的少量状态也该遵守。

#### 问题八：收件方抛异常怎么办？该不该让整个操作失败？

**面试官在考什么：** 你分得清"业务失败"和"通知失败"吗。

**答法骨架：**

1. **不该。** 一条消息发给 3 个人，第 2 个人炸了，第 3 个人照样该收到。
2. **本项目的做法：** `deliver()` 里逐人 `try-catch`，捕获 `RuntimeException`，记进 `failures`，然后 `continue`。
3. **关键：失败不抛给调用方。** 因为"通知失败"和"业务失败"是两件事 —— 仓库上报缺货已经成功了，只是通知器超时。

**真实输出（Demo 6）：**

```text
        [王先生] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
        [华南仓] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
      ! 已隔离：短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
  工单 AS-2024-0918 状态：已关闭
```

**注意：短信网关是最后一个收件人，所以看不出"隔离"的效果。**

**但代码是对的 —— 如果它在中间，后面的照常收到（`continue` 而不是 `break`）。**

**追问："那 `catch (Throwable)` 行不行？"**

**绝对不行。** `Throwable` 包含 `Error`（`OutOfMemoryError`、`StackOverflowError`）。**这些是 JVM 层面的故障，捕获它们等于在不可信的状态下继续跑。**

| 捕获 | 该不该 |
|---|---|
| `RuntimeException` | 该 |
| `Exception` | 不该（受检异常在这个调用点抛不出来） |
| `Throwable` | 绝不该 |

#### 问题九：中介者能不能做成单例？

**面试官在考什么：** 你分得清"结构"和"生命周期"吗。

**答法骨架：**

1. **能，但要看它管的是什么。**
2. **本项目的 `AfterSalesHub` 是"每个工单一个实例"，不能单例。** 因为它持有 `assignee` / `closed` / `delivered` —— 这些都是"每张工单独立"的状态。
3. **做成单例的后果：** 工单 A 升级到主管，工单 B 也跟着变；关一张单，全部关闭；所有工单的客户在同一张名单里。

**什么时候该单例：**

| 场景 | 该不该单例 | 理由 |
|---|---|---|
| 全局聊天室 | 该 | 只有一间房，规则是全局的 |
| 全局事件总线 | 该 | 事件是全局的 |
| 每张工单的协同 | **不该** | 状态是"每张单"独立的 |
| 每个游戏的房间 | 不该 | 每个房间一套成员 |

**追问："那把状态提到外面（`Map<String, TicketState>`），中枢做成无状态单例呢？"**

**可以，但那已经是一次重构了。** 无状态单例 + 外部状态存储，本质上就是"中介者 + 仓储"。**想法没问题，但如果状态存储也在进程内，只是把字段挪到了另一个类里，没解决任何问题。**

#### 问题十：什么情况下不该用中介者？

**面试官在考什么：** 你会不会过度设计。

**答法骨架（四种情况，都可以用本项目反推）：**

1. **只有两个同事。** 两个类的交互，直接互持比走中介更简单。中介者的收益是"加第 N 个角色时边际成本恒定"，N=2 时这个收益不存在。
2. **交互是单向的。** 如果 A 永远只调 B，B 从不回调 A，那这就是"外观"或"分层"，不需要中介者。
3. **规则几乎不变且极简单。** 如果"所有消息发给所有人"，那就是观察者，中介者只会多一层。
4. **需要强一致的事务。** 中介者是同步投递，但它不做事务。如果"三方都处理成功才算成功"，那是分布式事务，不是中介者。

**本项目 §1.4 还列了第五条：** 如果团队里没人能维护那张规则表，那中介者会变成一个"谁都不敢改"的类 —— **这种情况不如分成几个小的直接调用。**

**追问："那你觉得本项目有必要用中介者吗？"**

**有。** 因为 6 个角色、7 种消息、规则会变（升级、关闭、缺货三方协同），而且原项目的失败模式（广播 + 全收）恰好证明了"没有中枢"的代价。

**但如果砍掉 `Supervisor` 和 `Notifier`（只剩 4 个角色、4 种消息），我就会认真考虑"直连 + 少量 if"是不是更简单。**

> **这个回答的要点不是"要不要用"，而是"我会算一下再决定"。**
> 面试官想听的是判断过程，不是结论。

### 9.2 自测题

**分三档。第一档"看懂"，第二档"解释"，第三档"设计"。**

#### 第一档：看懂（不查代码就能答）

**Q1.** 本项目一共几个文件、几个代码行？其中最大的是哪个类？

**Q2.** `Participant` 接口有几个方法？哪几个是 `default`？

**Q3.** `CollaborationHub` 有几个方法？哪些是动作、哪些是查询？

**Q4.** `MessageKind` 有几种？哪两种在 `route()` 里返回空列表，为什么？

**Q5.** `AfterSalesHub` 有哪三个可变字段？各自的写入点在哪？

#### 第二档：解释（要能说出"为什么"）

**Q6.** 为什么 `Customer.receive()` 里没有 `STOCK_SHORTAGE` 分支，但客户确实会收到缺货消息？

**Q7.** 为什么 `close()` 里 `closed = true` 要放在 `deliver()` 之后？

**Q8.** 为什么 `deliver()` 里 `continue` 不能换成 `break`？

**Q9.** 为什么 `Notifier` 的 `default` 是"丢弃"，而 `Warehouse` 的 `default` 是"报警"？

**Q10.** 为什么 `route()` 的 `switch` 不写 `default`，而六个 `receive()` 里四个写了 `default`？

#### 第三档：设计（要能给出方案）

**Q11.** 现在要加一个"供应商"角色：仓库缺货时，供应商也要收到。请列出所有要改的地方，并说明编译器会提醒哪几处。

**Q12.** 现在要实现"工单暂停 / 恢复"：暂停期间所有 `say()` 都抛异常，恢复后正常。请说明加什么字段、改哪些方法，以及和 `closed` 的关系。

**Q13.** 如果要把"投递"从同步改成异步（每个收件人一个线程），会出现哪些新的问题？至少说三个。

### 9.3 参考答案

#### Q1

**13 个文件，1623 个物理行 / 593 个代码行。最大的是 `AfterSalesHub`（375 物理行 / 157 代码行）。**

注意"最大"要区分物理行和代码行 —— `Client` 物理行 306 但代码行 154，和 `AfterSalesHub` 的 157 很接近。**如果按"业务类"算，`AfterSalesHub` 是最大；如果按"所有文件"算，它也是最大。**

#### Q2

**四个方法 + 三个 `default`：**

| 方法 | 是否 default | 作用 |
|---|---|---|
| `role()` | 否 | 我是谁 |
| `name()` | 否 | 我叫什么 |
| `hub()` | 否 | 我的中枢 |
| `receive(Message)` | 否 | 收到消息后做什么 |
| `say(MessageKind, String)` | **是** | 发一条消息 |
| `escalate(String)` | **是** | 申请升级 |
| `closeTicket()` | **是** | 申请关闭 |

**四个必须实现（身份 + 收消息），三个默认提供（主动动作）。**

**为什么这样分？** 因为"身份"和"收消息"是每个参与者必须自己定义的；"发消息 / 升级 / 关单"都是"转给中枢"，逻辑完全一样，一行代码。

#### Q3

**七个方法，4 个动作 + 3 个查询：**

```java
// 动作
void join(Participant participant);
void say(Participant from, MessageKind kind, String text);
void escalate(Participant from, String reason);
void close(Participant from);

// 查询
List<Participant> roster();
Role assignee();
List<String> deliveryFailures();
```

**注意 `deliveredCount()` / `closed()` / `ticketId()` 不在接口里** —— 它们是 `AfterSalesHub` 的公有方法。

**为什么不放进接口？** 因为它们是"演示用的统计"，不是协同契约。**参与者不需要知道"一共投递了几次"。**

#### Q4

**七种。`ESCALATION` 和 `ARCHIVE` 在 `route()` 里返回空列表：**

```java
case ESCALATION, ARCHIVE -> List.of();
```

**因为它们不通过 `say()` 投递：**

- `ESCALATION` 由 `escalate()` 投递（发给主管 + 客服 + 通知器）；
- `ARCHIVE` 由 `close()` 投递（广播给其余所有人）。

**返回空列表是"明确表达这条边不存在"** —— 如果有人试图 `say(ESCALATION, ...)`，会得到：

```text
      路由未命中：角色「客户」不能发送「升级」，消息丢弃
```

**而不是静默走到某个 `default`。**

#### Q5

**三个可变字段：**

| 字段 | 写入点 | 代码 |
|---|---|---|
| `assignee` | `escalate()` | `assignee = Role.SUPERVISOR;` |
| `delivered` | `deliver()` | `delivered++;` |
| `closed` | `close()` | `closed = true;` |

**每个都只有一个写入点。**

**另外三个字段是 `final` 的容器：** `participants` / `byRole` / `failures` —— 容器不可变，内容可变，写入点也各只有一处（`join()` 和 `deliver()` 的 catch）。

**`ticketId` 也是 `final`，永不改变。**

#### Q6

**因为路由和反应是两件事。**

`route()` 负责"谁该收到"：

```java
case STOCK_SHORTAGE -> from == Role.WAREHOUSE
        ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER)      // 客户在列表里
        : List.of();
```

`Customer.receive()` 负责"收到之后做什么"：

```java
switch (message.kind()) {
    case ANSWER -> ...
    case REFUND_CONFIRMED -> ...
    case ARCHIVE -> ...
    // 没有 STOCK_SHORTAGE
}
```

**缺货消息到了客户这里，`switch` 什么都不匹配，所以只打印第一行"收到"，没有第二行"客户：..."。**

**真实输出：**

```text
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
```

**一行，不是两行。**

**这是有效的设计决定：** 客户收到缺货消息只需要"知道"，不需要"处置"（退款是客服决定的）。

#### Q7

**因为 `deliver()` 期间中枢还是"活的"，收件人可以安全地做附带动作。**

如果先 `closed = true` 再 `deliver()`：

```java
// 反例
closed = true;
deliver(message, targets);
```

那么任何收件人在 `receive()` 里回调 `hub().say(...)` 都会抛 `IllegalStateException` —— **而这个异常会被 `deliver()` 的 `try-catch` 捕获，记成"投递失败"。**

**结果：一个正常的关单附带动作，被记成了故障。**

**规则：状态标记要放在"它会阻止的操作"全部做完之后。**

**`close()` 的实际顺序：**

```java
deliver(message, targets);       // 1. 先投递
closed = true;                   // 2. 再关门
System.out.println("  工单 " + ticketId + " 状态：已关闭");    // 3. 最后打印
```

**第 3 步在第 2 步之后，是因为"状态：已关闭"这一行必须反映新状态。**

**注意这里和 `escalate()` 的区别：**

```java
// escalate()：先打印旧值和新值，再赋值
System.out.println("      负责方：" + assignee.displayName() + " → " + Role.SUPERVISOR.displayName());
assignee = Role.SUPERVISOR;
deliver(message, List.of(...));
```

**`escalate()` 是"先打印 → 再改 → 再投递"，`close()` 是"先投递 → 再改 → 再打印"。**

**为什么不一样？**

- `escalate()` 的投递列表是**硬编码的**（`List.of(SUPERVISOR, AGENT, NOTIFIER)`），不依赖 `assignee`。所以先改后改都不影响投递；
- `close()` 的投递列表**依赖 `participants`**，而 `participants` 不受 `closed` 影响。但 `deliver()` 里有 `receive()` 回调，所以必须先投递。

**一句话：投递列表依赖什么，就什么时候改。**

#### Q8

**因为 `continue` 是"这个人不在，下一个"，`break` 是"这条消息不发了"。**

```java
if (target == null) {
    System.out.println("      角色「" + role.displayName() + "」尚未加入，跳过");
    continue;        // ← 不能换成 break
}
```

**Demo 6 的真实场景：财务没加入，但客户必须收到。**

```text
      路由 → 客服-小李、财务（未加入）、客户-王先生
        [小李] 收到 ...
      角色「财务」尚未加入，跳过
        [王先生] 收到 ...          ← break 的话这一行会消失
```

**如果换成 `break`：**

```text
      路由 → 客服-小李、财务（未加入）、客户-王先生
        [小李] 收到 ...
      角色「财务」尚未加入，跳过
```

**王先生什么都没收到，但路由行里明明写着"客户-王先生"。** 日志和现实矛盾了。

> **`break` 只应该在"整条消息都失效"时用**（比如消息过期、工单已关闭）。
> **"某个收件人不可用"永远用 `continue`。**

#### Q9

**因为"漏掉"的代价落在不同的人身上。**

| | `Notifier` | `Warehouse` |
|---|---|---|
| 收件箱宽度 | 2 种（`ESCALATION` / `ARCHIVE`） | 1 种（`ARCHIVE`） |
| 收到意外消息意味着 | 可能有人配错了路由，也可能是"新消息值得外发" | **几乎必然是路由表错了** |
| 错的代价 | 外发一条不该发的短信 → **客户投诉、合规风险** | 内部多打一行日志 → **运维看一眼** |
| 选择 | **fail safe**（丢弃） | **fail loud**（报警） |

**判断标准：**

> **"漏掉"的代价落在外部世界 → fail safe；落在自己人 → fail loud。**

**`Notifier` 的 `default`：**

```java
default -> System.out.println("        [" + name + "] 不在外发白名单，丢弃（防止内部消息外泄）");
```

**`Warehouse` 的 `default`：**

```java
default -> System.out.println("        [" + name + "] 仓库：!! 这是我不该收到的消息类型「"
        + message.kind().label() + "」，请检查中枢的路由表");
```

**注意 `Warehouse` 那句里有"请检查中枢的路由表"** —— 它直接把排查方向写在了日志里。

#### Q10

**因为表达式必须给一个值，`default` 会伪造一个假答案；语句只是执行动作，`default` 可以是一个真动作。**

**`route()` 是表达式：**

```java
private List<Role> route(Role from, MessageKind kind) {
    return switch (kind) {
        case QUESTION -> ...;
        ...
        // 没有 default
    };
}
```

**加一个 `MessageKind` 立刻编译失败：**

```text
错误: switch 表达式不包含所有可能的输入值
```

**如果写了 `default -> List.of()`，加枚举时编译通过，新消息永远被丢弃 —— 而日志会显示"路由未命中"，看起来像是规则，实际是遗漏。**

**六个 `receive()` 是语句，四种有 `default`：**

| 类 | 有 default | default 做什么 |
|---|---|---|
| `Customer` | 无 | — |
| `Agent` | 无 | — |
| `Finance` | 无 | — |
| `Warehouse` | **有** | 报警 `!!` |
| `Supervisor` | **有** | 记录 `（...）` |
| `Notifier` | **有** | 拒绝（安全边界） |

**为什么 `Customer` / `Agent` / `Finance` 没有？**

因为它们的"无动作"是**常态**。`Customer` 对 7 种消息里 4 种无动作 —— 如果写 `default -> {}`，代码里会有大量空 case，读者看不出哪些是"真的有反应"。

**一句话总结：**

> **`switch` 表达式不加 `default`，`switch` 语句按需加 `default`。**
> **表达式要的是"穷尽"，语句要的是"兜底动作"。**

#### Q11

**要改 4 处，编译器只提醒 1 处。**

**第一处：`Role.java` 加枚举值**

```java
SUPPLIER("供应商"),
```

**编译器：不提醒。** 因为没有任何 `switch` 在 switch `Role`。

**第二处：新建 `Supplier.java`**

```java
public final class Supplier implements Participant {
    ...
    @Override
    public Role role() { return Role.SUPPLIER; }

    @Override
    public void receive(Message message) {
        System.out.println("        [" + name + "] 收到 " + message.describe());
        switch (message.kind()) {
            case STOCK_SHORTAGE -> System.out.println("        [" + name + "] 供应商：确认缺货，安排补货。");
            default -> System.out.println("        [" + name + "] 供应商：（记录）");
        }
    }
}
```

**编译器：不提醒。** 新文件不写也能编译，写了也没人检查它是否合理。

**第三处：`route()` 里 `STOCK_SHORTAGE` 加供应商**

```java
case STOCK_SHORTAGE -> from == Role.WAREHOUSE
        ? List.of(Role.AGENT, Role.FINANCE, Role.CUSTOMER, Role.SUPPLIER)     // ← 加一个
        : List.of();
```

**编译器：不提醒。** 这一步漏了，编译通过、运行正常，供应商永远收不到缺货。

**第四处：`Client.build()` 里 join**

```java
Supplier supplier = new Supplier("华东供应商", hub);
hub.join(supplier);
return new Team(hub, customer, agent, warehouse, finance, supervisor, notifier,
        supplier);        // ← 这一行编译失败：Team 只有 7 个字段
```

**编译器：提醒。** 但提醒的是"`Team` 字段数不对"，不是"你漏了路由"。

**结论：**

> **加角色是一次"编译器只能兜住最后一环"的改动。**
> 真正的风险在第三处（`route()`），而它恰恰是编译器管不到的地方。

**缓解手段（本项目未实现）：** 路由表快照测试 —— 断言 `route()` 在 6×7 = 42 个格子上的返回值，加角色时必须更新快照，否则测试失败。

#### Q12

**加一个字段，改四处判断。**

**第一步：加字段**

```java
private boolean paused;        // 默认 false
```

**注意它不是 `final`，且和 `closed` 是两个独立的状态。**

**第二步：`say()` 加检查**

```java
if (closed) {
    throw new IllegalStateException(...);
}
if (paused) {
    throw new IllegalStateException("工单 " + ticketId + " 已暂停，" + from.name() + " 暂时不能发言");
}
```

**第三步：`escalate()` / `close()` 也要加检查**

因为"暂停期间不能发言"不该只限制 `say()` —— 升级和关闭也是一种发言。

**第四步：加两个方法**

```java
@Override
public void pause(Participant from) {
    if (closed) {
        throw new IllegalStateException("工单已关闭，不能暂停");
    }
    paused = true;
}

@Override
public void resume(Participant from) {
    if (closed) {
        throw new IllegalStateException("工单已关闭，不能恢复");
    }
    paused = false;
}
```

**`CollaborationHub` 接口要加这两个方法，然后 `AfterSalesHub` 实现。**（如果给它们 `default` 空实现，就是"静默不生效" —— 不好。）

**和 `closed` 的关系，有三条：**

| 关系 | 说明 |
|---|---|
| **`closed` 优先** | 已关闭的工单不能暂停、不能恢复 —— 两个方法都要先检查 `closed` |
| **`paused` 不阻止关闭** | 暂停的工单**可以**关闭（否则永远关不掉）—— 所以 `close()` **不**检查 `paused` |
| **`paused` 不阻止 `join`** | 暂停期间可以加人，因为加人不算"发言" |

**关键判断：哪些方法检查 `paused`？**

| 方法 | 检查 `paused` | 理由 |
|---|---|---|
| `say()` | **是** | 发言 |
| `escalate()` | **是** | 升级是一种发言 |
| `close()` | **否** | 必须能关掉暂停的单子 |
| `join()` | **否** | 加人不是发言 |
| `receive()` | **否** | 收消息不受影响（已在途的消息照常送达） |

> **每加一个状态，都要重新过一遍"所有方法 × 这个新状态"的矩阵。**
> 这正是状态模式要解决的问题 —— 如果状态多于两个（`closed` / `paused` / `locked` / `archived`），
> 就该考虑把这些布尔字段换成一个 `TicketState` 枚举 + 状态对象。

**本项目只有 `closed` 一个布尔，所以用字段 + `if` 是合适的。**
**一旦出现第二个，就该警惕了** —— 因为两个布尔有 4 种组合，其中一种（`closed && paused`）是无意义的。

#### Q13

**至少五个新问题：**

**问题一：`delivered` 计数不再准确。**

现在是：

```java
target.receive(message);
delivered++;
```

异步化之后，`delivered++` 发生在提交任务时，而不是任务完成时 —— **它变成了"提交成功的条数"。**

**问题二：`failures` 收不到异常。**

`receive()` 的异常发生在另一个线程里，`deliver()` 的 `try-catch` 抓不到 —— 需要用 `Future.get()` 或 `CompletableFuture.exceptionally()` 收集。

**而 `Future.get()` 会阻塞** —— 那就等于没异步。

**问题三：`Message` 需要线程安全。**

`Message` 是 `record`，不可变（三个字段都是 `final`），所以本身安全。**但 `failures` / `delivered` 不是** —— 多线程写同一个 `ArrayList` 会出问题，得换成 `CopyOnWriteArrayList` 或 `AtomicInteger`。

**问题四：投递顺序不再确定。**

现在是严格的 `List.of` 顺序输出（客服 → 财务 → 客户）。异步之后，三个线程的输出顺序随机 —— **Demo 的输出就无法哈希校验了。**

**问题五：异常隔离的语义变了。**

现在的"隔离"是"这个人炸了，下一个照常" —— 因为循环是串行的。

异步之后，所有任务同时提交，"隔离"变成了"这个 future 失败了，其他 future 照常" —— **逻辑上还是隔离，但错误收集的时机从"投递中"变成了"投递后"。**

**问题六（额外）：`close()` 的 `closed = true` 时机变得微妙。**

现在是"投递完 → 置 closed"。异步之后，"投递完"不再是一个明确的时刻 —— **`closed = true` 会在所有任务完成之前执行。**

**于是"关单后立刻发言"这条检查，和"关单前发出的消息还在投递中"会同时存在。**

> **异步化不是"加个线程池"，它改的是"投递完成"这个概念的定义。**
> 一旦没有"完成"这个时刻，`delivered` / `failures` / `closed` 三个字段的语义全部要重新定义。

**这就是 §7.4 讨论 MQ 时说的同一件事：换通道，换的是语义。**

---

### 9.4 最后一张自检表

**读完这份文档，如果能不查代码回答下面 10 个问题，就说明真的懂了：**

| # | 问题 | 答案在哪 |
|---|---|---|
| 1 | 六个参与者的代码里，为什么找不到一行"通知某某"？ | §5.10-§5.16 |
| 2 | `route()` 为什么返回角色而不是对象？ | §8.3 误解六 |
| 3 | 为什么 `ESCALATION` 在 `route()` 里返回空列表，却仍然要留在这个枚举里？ | §5.3 |
| 4 | `close()` 为什么是唯一一处真正的广播？ | §5.9 / §6.6 |
| 5 | 为什么 `deliver()` 用 `continue` 不用 `break`？ | §6.7 / Q8 |
| 6 | 为什么 `Notifier` 的 `default` 和 `Warehouse` 的 `default` 策略相反？ | Q9 |
| 7 | 加一种消息类型，编译器会报几个错、报在哪？ | §7.3 / Q10 |
| 8 | 加一个角色，编译器会报错吗？ | §7.2 / Q11 |
| 9 | 中介者和观察者最本质的区别是哪一句？ | §7.7 / Q1 |
| 10 | 这套设计有哪八个已知局限？ | §8.5 |

**第 7 题和第 8 题的答案不对称，是这份文档最想传递的一件事：**

> **同一个模式，对不同维度的扩展有不同的保护强度。**
> 知道哪一维有编译器兜底、哪一维没有 —— 比记住"中介者降低耦合"有用得多。

---

## 附录 A：文件与行数清单

### A.1 十三个文件逐项数据

**数据来源：真实文件系统统计，不是估算。**

| # | 文件 | 字节 | 物理行 | 代码行 | 层 |
|---|---|---|---|---|---|
| 1 | `Role.java` | 2698 | 71 | 16 | 值对象 |
| 2 | `MessageKind.java` | 2712 | 77 | 17 | 值对象 |
| 3 | `Message.java` | 3372 | 73 | 15 | 值对象 |
| 4 | `Participant.java` | 4100 | 111 | 16 | 接口 |
| 5 | `CollaborationHub.java` | 4090 | 106 | 11 | 接口 |
| 6 | `AfterSalesHub.java` | 16218 | 375 | 157 | 实现（中枢） |
| 7 | `Customer.java` | 3269 | 81 | 34 | 实现（同事） |
| 8 | `Agent.java` | 3734 | 87 | 35 | 实现（同事） |
| 9 | `Warehouse.java` | 3126 | 86 | 34 | 实现（同事） |
| 10 | `Finance.java` | 3034 | 82 | 33 | 实现（同事） |
| 11 | `Supervisor.java` | 3188 | 83 | 35 | 实现（同事） |
| 12 | `Notifier.java` | 3324 | 85 | 36 | 实现（同事） |
| 13 | `Client.java` | 13031 | 306 | 154 | 入口 |
| | **合计** | **67989** | **1623** | **593** | |

**"代码行"的定义：去掉空行、去掉纯注释行（含 `/** ... */` 与 `//`）之后的行数。**

**两个数字的差距说明什么？**

`Participant.java`：111 物理行 / 16 代码行 —— **86.5% 是注释**。

`CollaborationHub.java`：106 物理行 / 11 代码行 —— **89.6% 是注释**。

**这不是"注释写多了"，而是"接口的定义很短，但契约说明很长"。**

对比 `AfterSalesHub.java`：375 物理行 / 157 代码行 —— **58% 是注释。**

> **接口的文件几乎全是注释，实现类的文件一半是代码。
> 这个比例本身就是一份设计说明：抽象的代价是"要把它讲清楚"。**

### A.2 二十二个 class 文件

**13 个源文件编译出 22 个 `.class`。**

| 源文件 | 生成的 class | 数量 |
|---|---|---|
| `AfterSalesHub.java` | `AfterSalesHub.class`、`AfterSalesHub$1.class` | 2 |
| `Agent.java` | `Agent.class`、`Agent$1.class` | 2 |
| `Client.java` | `Client.class`、`Client$BrokenNotifier.class`、`Client$Team.class` | 3 |
| `CollaborationHub.java` | `CollaborationHub.class` | 1 |
| `Customer.java` | `Customer.class`、`Customer$1.class` | 2 |
| `Finance.java` | `Finance.class`、`Finance$1.class` | 2 |
| `Message.java` | `Message.class` | 1 |
| `MessageKind.java` | `MessageKind.class` | 1 |
| `Notifier.java` | `Notifier.class`、`Notifier$1.class` | 2 |
| `Participant.java` | `Participant.class` | 1 |
| `Role.java` | `Role.class` | 1 |
| `Supervisor.java` | `Supervisor.class`、`Supervisor$1.class` | 2 |
| `Warehouse.java` | `Warehouse.class`、`Warehouse$1.class` | 2 |
| | **合计** | **22** |

**两种 `$` 后缀含义完全不同：**

| 形式 | 来源 | 例子 |
|---|---|---|
| `Xxx$1.class` | **编译器合成**（switch 表达式的映射表） | `Agent$1.class` |
| `Xxx$Yyy.class` | **源码里的嵌套类** | `Client$Team.class` |

**七个 `$1` 分别是七个 `switch` 的合成类：**

```text
AfterSalesHub$1    ← route() 里 switch MessageKind
Customer$1         ← receive() 里 switch MessageKind
Agent$1            ← receive() 里 switch MessageKind
Warehouse$1        ← receive() 里 switch MessageKind
Finance$1          ← receive() 里 switch MessageKind
Supervisor$1       ← receive() 里 switch MessageKind
Notifier$1         ← receive() 里 switch MessageKind
```

**验证方法（真实命令）：**

```powershell
javap -p -c "E:\codeFile\codeFather\design_patterns_pro\out\com\patterns\behavioral\mediator\Agent$1.class"
```

**输出（关键片段）：**

```text
final class com.patterns.behavioral.mediator.Agent$1 {
  static final int[] $SwitchMap$com$patterns$behavioral$mediator$MessageKind;
  static {};
}
```

**这个类的唯一职责是存一张 `int[]` 映射表，把枚举的 `ordinal()` 映射成 `switch` 的跳转索引。**

**为什么 Java 要生成它？**

因为 `switch` 枚举在字节码层面其实是 `switch (int)`。编译器先查表把枚举转成 int，再跳转。**这张表需要一个地方存 —— 就是 `$1`。**

**它在什么情况下会多一个条目？**

加一个 `MessageKind` 时。**七个 `$1` 都会重新生成，每个多一个数组元素。**

> **`$1` 类的存在，是"switch 枚举"和"switch 字符串"（Java 7+）共同的实现代价。**
> 它不影响设计，但 `22` 这个数字会让第一次看 `out/` 目录的人困惑 ——
> 所以它必须被解释一次。

### A.3 `Client$Team` 和 `Client$BrokenNotifier`

**这两个是源码里的嵌套类，不是编译器合成的。**

| 类 | 声明 | 修饰符含义 |
|---|---|---|
| `Client$Team` | `private record Team(...)` | `private`（只给 `Client` 用） |
| `Client$BrokenNotifier` | `private static final class BrokenNotifier implements Participant` | `private` + `static` + `final` |

**`record` 是隐式 `final` 的**，所以 `Team` 的字节码里也是 `final class`（继承 `java.lang.Record`）。

**`BrokenNotifier` 的三个修饰符逐个说：**

| 修饰符 | 不加会怎样 |
|---|---|
| `private` | 变成包级可见，`Client` 的"演示道具"泄漏到包里 |
| `static` | 会隐式持有 `Client.this` 引用（但 `Client` 没有实例，构造不出来） |
| `final` | 可以被继承 —— 一个"故意会炸"的类不该被继承 |

**`Client` 的构造器是 `private`：**

```java
private Client() {
}
```

**`static` 嵌套类不需要它，但工具类惯例如此** —— 防止 `new Client()`。

---

## 附录 B：编译与运行验证

### B.1 环境

| 项 | 值 |
|---|---|
| JDK | `F:\enCodingApps\JDK\jdk-25` |
| `java -version` | `openjdk version "25.0.4.1" 2026-08-18 LTS` |
| 运行时 | `OpenJDK Runtime Environment Microsoft-14951867 (build 25.0.4.1+1-LTS)` |
| VM | `OpenJDK 64-Bit Server VM Microsoft-14951867 (build 25.0.4.1+1-LTS, mixed mode, sharing)` |
| class 文件 major version | **69**（Java 25） |
| 操作系统 | Windows（shell 为 PowerShell 7） |
| 源文件编码 | **UTF-8 无 BOM** |
| 行尾 | **CRLF**（13 个文件全部） |

### B.2 编译命令

```powershell
$jdk = "F:\enCodingApps\JDK\jdk-25"
$src = "E:\codeFile\codeFather\design_patterns_pro\src\com\patterns\behavioral\mediator"
$out = "E:\codeFile\codeFather\design_patterns_pro\out"

& "$jdk\bin\javac.exe" -Xlint:all -encoding UTF-8 -d $out `
    "$src\Role.java" "$src\MessageKind.java" "$src\Message.java" `
    "$src\Participant.java" "$src\CollaborationHub.java" `
    "$src\AfterSalesHub.java" "$src\Customer.java" "$src\Agent.java" `
    "$src\Warehouse.java" "$src\Finance.java" "$src\Supervisor.java" `
    "$src\Notifier.java" "$src\Client.java"
```

**`-Xlint:all` 是刻意的。**

它会把所有可选的警告都打开：未检查的转换、缺失的 `serialVersionUID`、`switch` 落空、弃用 API……**本项目零警告。**

**为什么"零警告"值得写进文档？**

因为 `-Xlint:all` 零警告证明了两件事：

1. **没有用 `@SuppressWarnings` 掩盖问题**（本项目全项目搜不到这个注解）；
2. **`switch` 表达式确实穷尽**（每个 `MessageKind` 都被覆盖，否则 `-Xlint` 会报 `[fallthrough]` 或编译失败）。

### B.3 编译结果

```text
FILES: 13
COMPILE: OK, zero warnings
CLASSES: 22
```

**BOM 与行尾检查（每个文件）：**

```text
AfterSalesHub.java        16218 bytes  bom=False  crlf=375
Agent.java                 3734 bytes  bom=False  crlf=87
Client.java               13031 bytes  bom=False  crlf=306
CollaborationHub.java      4090 bytes  bom=False  crlf=106
Customer.java              3269 bytes  bom=False  crlf=81
Finance.java               3034 bytes  bom=False  crlf=82
Message.java               3372 bytes  bom=False  crlf=73
MessageKind.java           2712 bytes  bom=False  crlf=77
Notifier.java              3324 bytes  bom=False  crlf=85
Participant.java           4100 bytes  bom=False  crlf=111
Role.java                  2698 bytes  bom=False  crlf=71
Supervisor.java            3188 bytes  bom=False  crlf=83
Warehouse.java             3126 bytes  bom=False  crlf=86
```

**`crlf=375` 的意思是"有 375 个 CRLF 行尾"** —— 和物理行数一致。

**`bom=False` 重要吗？**

重要。**带 BOM 的 UTF-8 源文件在某些工具链里会导致编译失败**（`javac` 本身能容忍，但部分构建工具不能）。本项目全部无 BOM。

### B.4 运行命令

```powershell
& "$jdk\bin\java.exe" -Dfile.encoding=UTF-8 `
    -cp "E:\codeFile\codeFather\design_patterns_pro\out" `
    com.patterns.behavioral.mediator.Client
```

**`-Dfile.encoding=UTF-8` 是必要的。**

Windows 的默认编码是 GBK。不加这个参数，`System.out` 会用 GBK 编码输出，而中文 Windows 控制台能显示 GBK —— **看起来正常，但如果把输出重定向到文件再用 UTF-8 读，就是乱码。**

**本项目在验证过程中踩过这个坑（见 B.6）。**

### B.5 三次运行结果

| 次 | exit code | 行数 | SHA-256 |
|---|---|---|---|
| 1 | 0 | 125 | `203A78A7A3D77CE8735B9623D4E02C1A91124ACBC4E8A77B3D94C566CDD8319B` |
| 2 | 0 | 125 | `203A78A7A3D77CE8735B9623D4E02C1A91124ACBC4E8A77B3D94C566CDD8319B` |
| 3 | 0 | 125 | `203A78A7A3D77CE8735B9623D4E02C1A91124ACBC4E8A77B3D94C566CDD8319B` |

**三次哈希完全一致 → 输出完全确定。**

**为什么能确定？**

因为本项目里**没有任何不确定性来源**：

| 可能的来源 | 本项目的情况 |
|---|---|
| `HashMap` 遍历顺序 | **没有 `HashMap`**（只有 `EnumMap` 和 `ArrayList`，两者都是插入/声明顺序） |
| 时间戳 | 没有任何时间输出 |
| 随机数 | 没有 `Random` / `Math.random()` |
| 线程 | 单线程，无 `Thread` / 并发集合 |
| 对象哈希地址 | 没有打印 `toString()` 的默认实现（所有输出都是 `name` / `displayName()` / `message.describe()`） |
| 文件系统顺序 | 没有遍历目录 |

**六条全满足，输出才可哈希。**

> **"三次运行哈希一致"不是一句装饰，它是一个可验证的断言：**
> **这段程序是纯函数式的（在输出层面）。**
> 一旦哪天有人加了一个时间戳，这个哈希立刻会变 —— 于是这个数字本身成了回归测试。

### B.6 踩过的坑：`java.exe` 输出重定向成 mojibake

**这是本次验证过程中最严重的技术错误，值得写进附录。**

**错误写法：**

```powershell
& "$jdk\bin\java.exe" -cp $out com.patterns.behavioral.mediator.Client 2>&1 | Out-String | Set-Content $file -Encoding utf8
```

**结果：文件里的中文全是：**

```text
[Demo 1锛氬鎴风殑涓€鍙ヨ瘽]
```

**根因：**

1. `java.exe` 按 `-Dfile.encoding=UTF-8` 输出 UTF-8 字节到管道；
2. **PowerShell 按系统默认编码（GBK）解码这些字节** → 得到 mojibake 字符串；
3. `Set-Content -Encoding utf8` 把这个 mojibake 字符串**按 UTF-8 重新编码**写进文件。

**一进一出，两次错误编码转换 —— 文件内容已经不可逆转地损坏了。**

**正确写法：**

```powershell
Start-Process -FilePath "$jdk\bin\java.exe" `
    -ArgumentList "-Dfile.encoding=UTF-8", "-cp", $out, "com.patterns.behavioral.mediator.Client" `
    -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput $runFile `
    -RedirectStandardError $errFile
```

**`Start-Process -RedirectStandardOutput` 让子进程的字节直接落到文件，不经过 PowerShell 的字符串解码。**

**验证方法：**

```powershell
[System.IO.File]::ReadAllText($file, [System.Text.UTF8Encoding]::new($false))
```

**如果这段代码读出来是正常中文，说明文件是对的；如果 Program Files 是乱码，说明文件已经坏了。**

> **教训：任何"跨进程的文本传递"，都不要用管道 + `Out-String`。**
> **管道会做编码转换，而转换的方向由 `$OutputEncoding` 和 `[Console]::OutputEncoding` 决定 —— 这两个变量的默认值在中文 Windows 上都是 GBK。**

### B.7 静态检查

**除了编译，还做了四项文本检查：**

| 检查 | 方法 | 结果 |
|---|---|---|
| 行尾空白 | 正则 `[ \t]+$` | **0 处** |
| Tab 字符 | 正则 `\t` | **0 处** |
| 超长行 | 统计最大行长 | **61 字符**（上限 120） |
| 中英文之间多余空格 | 正则 `[\u4e00-\u9fff] +[\u4e00-\u9fff]` | **8 处，全部合法** |

**那 8 处"合法"的是什么？**

| 位置 | 内容 | 为什么合法 |
|---|---|---|
| 4 处 | 输出字符串里的表格对齐（如 `"  参与方（6）：..."`） | 对齐需要空格 |
| 2 处 | 日志文案里的分隔（如 `"客服：收到缺货，这单走退款。"` 前后） | 刻意的可读性 |
| 2 处 | javadoc 里的示例文本 | 引用真实输出 |

**这四项检查的意义：**

> **"能编译"不代表"符合规范"。**
> 行尾空白会让 `git diff` 出现无意义的改动；tab 和空格的混用会让不同编辑器里对齐错乱；
> 超长行会让 code review 时横向滚动。
> **这些都不是"设计模式"的问题，但它们是一份可交付代码的一部分。**

### B.8 编译器对照实验：`switch` 表达式 vs 语句

**这个实验是为了给 §4.13 和 §7.3 提供真实证据。**

**实验一：`switch` 表达式不穷尽**

```java
// tmp_med_switch/ExprFail.java
public class ExprFail {
    static String f(MessageKind k) {
        return switch (k) {       // 缺少一个枚举值
            case QUESTION -> "a";
        };
    }
}
```

**真实输出：**

```text
ExprFail.java:4: 错误: switch 表达式不包含所有可能的输入值
        return switch (k) {
               ^
1 个错误
exit = 1
```

**实验二：`switch` 语句不穷尽**

```java
// tmp_med_switch/StmtOk.java
public class StmtOk {
    static void f(MessageKind k) {
        switch (k) {          // 同样缺少一个枚举值
            case QUESTION -> System.out.println("a");
        }
    }
}
```

**真实输出：**

```text
（无任何输出）
exit = 0
```

**实验三：运行实验二的类**

```text
语句不穷尽：编译通过，C 什么都没打印
exit = 0
```

**三组结果放在一起：**

| 写法 | 不穷尽时 | 运行到未覆盖的值 |
|---|---|---|
| `switch` **表达式**（`return switch`） | **编译失败** | 不可能发生 |
| `switch` **语句** | 编译通过 | **静默什么都不做** |
| `switch` 语句 + `default` | 编译通过 | 走 `default` |

**这就是本项目"表达式不写 `default`、语句按需写 `default`"这条规则的编译器依据。**

> **这条规则不是风格偏好，是 JDK 25 的语义事实。**
> 任何人可以用上面 20 行代码重新验证一遍。

---

## 附录 C：原项目缺陷探针全文

### C.1 探针怎么搭的

> 下文出现的 `tmp_med_probe` / `tmp_med_switch` 是当时用过的临时目录名，验完已删。
> 你按本节步骤重建，会得到除路径外完全一致的输出。

**探针不是"重新实现一遍原项目"，而是直接用原项目的五个源文件。**

```powershell
# 把原项目的 5 个文件复制到临时目录，再在同一目录下写 MediatorProbes.java
$orig = "E:\codeFile\codeFather\design_patterns\src\BehaviorType\MediatorPattern"
$work = Join-Path (Get-Location) "probe"
Copy-Item "$orig\*.java" "$work\BehaviorType\MediatorPattern\"
```

**为什么要复制而不是直接引用？**

因为探针要写**同一个包**（`BehaviorType.MediatorPattern`）—— 只有这样，才能访问 `User` 的 `protected` 字段（`name` / `mediator`）。**而探针本身不应该污染原项目目录。**

**复制结果（6 个文件）：**

```text
COPIED: 5 original files + MediatorProbes.java
  ChatClient.java
  ChatRoom.java
  ChatRoomMediator.java
  ChatUser.java
  MediatorProbes.java
  User.java
```

**注意 `MediatorProbes.java` 和原项目的 5 个文件**在同一个包里、同一个目录下。

> **探针必须和被测代码同包，才能验证"同包能改 protected 字段"这一类问题。**
> 如果探针放在另一个包，探针 5 和探针 6 就验证不出来 —— 但"另一个包的子类能改"依然是成立的。

### C.2 `MediatorProbes.java` 的结构

**234 行，1 个 `main` + 7 个探针方法 + 2 个探针专用内部类。**

```java
public final class MediatorProbes {

    private MediatorProbes() {
    }

    public static void main(String[] args) {
        probe1RosterAndReferenceCanDiverge();
        probe2DuplicateRegistration();
        probe3NullBreaksBroadcast();
        probe4RegisterWhileIterating();
        probe5NullName();
        probe6RewritableMediator();
        probe7NoTargetedSend();
    }

    private static void probe1RosterAndReferenceCanDiverge() { ... }
    private static void probe2DuplicateRegistration() { ... }
    private static void probe3NullBreaksBroadcast() { ... }
    private static void probe4RegisterWhileIterating() { ... }

    /** 探针 4 专用：收到消息时顺手注册一个新人。 */
    private static final class SelfInvitingUser extends ChatUser {
        @Override
        public void receive(String message) {
            super.receive(message);
            room.registerUser(new ChatUser("新人", room));
        }
    }

    private static void probe5NullName() { ... }
    private static void probe6RewritableMediator() { ... }

    /** 探针 6 专用：构造时把 mediator 换成另一个房间。 */
    private static final class Hijacker extends ChatUser {
        Hijacker(String name, ChatRoomMediator room, ChatRoomMediator otherRoom) {
            super(name, room);
            this.mediator = otherRoom;
        }
    }

    private static void probe7NoTargetedSend() { ... }
}
```

**两个内部类都是"故意做坏事"的子类：**

| 内部类 | 干什么坏事 | 验证什么 |
|---|---|---|
| `SelfInvitingUser` | `receive()` 里调 `registerUser()` | 边遍历边改 → `ConcurrentModificationException` |
| `Hijacker` | 构造时把 `mediator` 指向别的房间 | 子类能改 `protected` 字段 → 发言跑错房间 |

**它们能编译通过，恰恰是问题所在。**

`Hijacker` 的构造器里 `this.mediator = otherRoom;` 这一行**没有任何警告** —— 因为 `mediator` 是 `protected` 且非 `final`。

> **"能写出这样的子类"本身就是缺陷的证据。**
> 如果 `mediator` 是 `private final`，`Hijacker` 根本无法编译 —— 而这个缺陷也就不存在了。

### C.3 七个探针分别做了什么

| 探针 | 动作 | 触发的问题 |
|---|---|---|
| **P1** | 把 `Alice` 注册进 `roomA`，再把她的 `mediator` 指向 `roomB`；然后让她在 `roomB` 发言 | 名单和引用是两份状态 |
| **P2** | 同一个 `User` 对象 `registerUser` 两次 | 无去重 → 收两遍 |
| **P3** | 往 `users` 里塞一个 `null`，再广播 | 裸循环 → NPE 断链 |
| **P4** | 用一个"收到消息就注册新人"的 `User` | 边遍历边改 → CME |
| **P5** | 用 `null` 名字构造 `User`；再用同包权限改 `name` | 无校验 + 非 `final` |
| **P6** | 用 `Hijacker` 换掉 `mediator` | `protected` 非 `final` |
| **P7** | 反射列出 `ChatRoomMediator` 的方法，观察发送能力 | 接口只有"群发" |

**P7 用了一个特殊手段：反射。**

```java
ChatRoomMediator.class.getDeclaredMethods()
```

**为什么要反射？**

因为 P7 想证明的不是"某个方法不存在"，而是"**这个接口一共就只有两个方法，没有第三个**"。

**如果只写代码调用，读者只能看到"我调了这个方法"，看不到"没有别的方法"。反射列出来，才能证明"能力表"本身有多窄。**

**真实输出的方法顺序是 `registerUser` → `sendMessage`。**

**而原项目源码里的声明顺序正好相反：**

```java
public interface ChatRoomMediator {
    void sendMessage(String message, User sender);      // 源码第 11 行

    void registerUser(User user);                        // 源码第 13 行
}
```

> **`getDeclaredMethods()` 不保证返回顺序。**
> Java 规范明确说"不保证任何特定顺序"。本次运行恰好是 `registerUser` 在前，
> **和源码声明顺序相反** —— 换一个 JDK 版本可能又变。
> **所以文档里引用这个输出时必须注明"顺序不保证"** —— 否则读者复现时会以为输出错了。

### C.4 完整输出（54 行）

```text
[P1] 注册进哪个房间，和发言走哪个房间，是两件事
  roomA 名单：Alice、Carol
  roomB 名单：Bob、Alice
  Alice 的 mediator 字段仍然指向 roomA（registerUser 不会改它）
  Alice 在 B 房间里说一句话，Bob 应该能收到：
Alice 发送消息：B 房间的 Bob 你好
Carol 收到消息：Alice：B 房间的 Bob 你好
  -> Bob 一条都没收到；Carol 收到了。名单里有 Alice，不代表她的话从这个房间走

[P2] 重复注册：网络重连、页面刷新都会导致同一个用户加两次
A 发送消息：只发了一次
B 收到消息：A：只发了一次
B 收到消息：A：只发了一次
  -> B 打印了两次「收到消息」；registerUser 里只有 users.add(user)

[P3] 一个 null 混进名单：广播断在那里，后面的人全部静默丢失
A 发送消息：有人能收到吗
B 收到消息：A：有人能收到吗
  -> 抛 java.lang.NullPointerException: Cannot invoke "BehaviorType.MediatorPattern.User.receive(String)" because "<local4>" is null
  -> B 收到了（排在 null 之前），C 和 D 一条都没收到（排在 null 之后）
  -> 而且异常一路抛到调用 send() 的那行代码，业务方无从补救

[P4] 收到消息时顺手拉个人进群：ConcurrentModificationException
A 发送消息：欢迎新人
Inviter 收到消息：A：欢迎新人
  -> 抛 java.util.ConcurrentModificationException
  -> 广播循环正在遍历 users，receive() 里又调 registerUser() 改了它

[P5] name 为 null 也能构造，而且外部能直接改
null 发送消息：我是谁
A 收到消息：null：我是谁
  -> 发送方和消息前缀都变成了字面量 null
  -> 同包直接改 protected 字段后，再发一次：
冒名者 发送消息：现在我叫冒名者
A 收到消息：冒名者：现在我叫冒名者

[P6] mediator 是 protected 且非 final：子类随时能把发言通道换掉
Hijacker 发送消息：这句还在 A 房间
Watcher 收到消息：Hijacker：这句还在 A 房间
Hijacker 发送消息：这句已经跑到 B 房间，但我在 A 的名单里
Bystander 收到消息：Hijacker：这句已经跑到 B 房间，但我在 A 的名单里
  -> 同一个人，两句话走了两个不同的房间，没有任何日志提示

[P7] 接口的发送能力只有一种：发给除自己外的所有人
  ChatRoomMediator 声明的方法：
    registerUser -> public abstract void BehaviorType.MediatorPattern.ChatRoomMediator.registerUser(BehaviorType.MediatorPattern.User)
    sendMessage -> public abstract void BehaviorType.MediatorPattern.ChatRoomMediator.sendMessage(java.lang.String,BehaviorType.MediatorPattern.User)
  A 只想告诉 B 一件事，但 send() 没有收件人参数：
A 发送消息：这句其实只有 B 该看到
B 收到消息：A：这句其实只有 B 该看到
C 收到消息：A：这句其实只有 B 该看到
  -> C 也看到了。想只发给 B，只能 a 持有 b 的引用直接 b.receive(...)，
     那就不再经过中介者，模式也就名存实亡

```

**`SHA-256`：**

```text
1A271487670C31B7F2865AE193C1049CC27C94BB0009A6EBFA894079E9BE5FB3
```

**54 行的构成：**

| 探针 | 行数 | 关键证据 |
|---|---|---|
| P1 | 7 | `Carol 收到消息` 而 `Bob` 一条没收到 |
| P2 | 5 | `B 收到消息` 出现两次 |
| P3 | 7 | 真实 NPE 消息 + `C 和 D 一条都没收到` |
| P4 | 5 | `java.util.ConcurrentModificationException` |
| P5 | 7 | `null 发送消息：我是谁` + `冒名者 发送消息` |
| P6 | 6 | 同一个人两句话走两个房间 |
| P7 | 10 | 只有两个方法 + `C 也看到了` |

**每一条输出都是可复现的** —— 因为原项目没有任何不确定性来源（无时间、无随机、无并发）。

---

## 附录 D：真实输出全文

### D.1 运行配置

```powershell
& "F:\enCodingApps\JDK\jdk-25\bin\java.exe" `
    -Dfile.encoding=UTF-8 `
    -cp "E:\codeFile\codeFather\design_patterns_pro\out" `
    com.patterns.behavioral.mediator.Client
```

**125 行，`SHA-256`：**

```text
203A78A7A3D77CE8735B9623D4E02C1A91124ACBC4E8A77B3D94C566CDD8319B
```

### D.2 完整输出（125 行）

```text
[Demo 1：客户的一句话，只该到一个人手里]
  参与方（6）：客户-李女士、客服-小王、仓库-华东仓、财务-小赵、主管-张主管、通知器-站内信网关
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  当前负责方：客服
  只有 1 个人收到。原项目的 sendMessage 会发给除发送者外的所有人（5 个）

[Demo 2：仓库一句「缺货」，中枢同时通知三方]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  [客服-小王] 答复：我查一下库存，稍等两分钟。
      路由 → 客户-李女士
        [李女士] 收到 客服 的答复：我查一下库存，稍等两分钟。
        [李女士] 客户：好，那我等结果。
  [仓库-华东仓] 缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
      路由 → 客服-小王、财务-小赵、客户-李女士
        [小王] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小王] 客服：收到缺货，这单走退款。
        [小赵] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小赵] 财务：冻结原发票，准备全额退款。
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
  这条消息由仓库发出，但客服、财务、客户都收到了
  三个收件人不是仓库决定的，是中枢的 route() 决定的
  通知器没有收到：它只外发升级与归档，缺货这种内部事实不外发

[Demo 3：同一张工单，换个角色发言就换一批收件人]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  [仓库-华东仓] 缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
      路由 → 客服-小王、财务-小赵、客户-李女士
        [小王] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小王] 客服：收到缺货，这单走退款。
        [小赵] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
        [小赵] 财务：冻结原发票，准备全额退款。
        [李女士] 收到 仓库 的缺货上报：EP-2024 无线耳机，华东仓缺货 37 件
  [财务-小赵] 退款确认：已退 299 元，1-3 个工作日到账
      路由 → 客户-李女士、客服-小王
        [李女士] 收到 财务 的退款确认：已退 299 元，1-3 个工作日到账
        [李女士] 客户：钱到账了，问题解决。
        [小王] 收到 财务 的退款确认：已退 299 元，1-3 个工作日到账
  退款确认只到 客户 + 客服。仓库收不到这条 —— 它不需要知道钱的事

[Demo 4：升级之后，同一句话换了一个收件人]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  当前负责方：客服
  [客户-李女士] 申请升级：等了 3 天没人处理，要求升级
      负责方：客服 → 主管
      路由 → 主管-张主管、客服-小王、通知器-站内信网关
        [张主管] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [张主管] 主管：接手，先翻一遍上下文再回客户。
        [小王] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [小王] 客服：抄送已阅，这单不再由我跟。
        [站内信网关] 收到 客户 的升级：等了 3 天没人处理，要求升级
        [站内信网关] 外发短信给主管手机：「等了 3 天没人处理，要求升级」
  当前负责方：主管
  [客户-李女士] 提问：到底什么时候能退？
      路由 → 主管-张主管
        [张主管] 收到 客户 的提问：到底什么时候能退？
        [张主管] 主管：客户又追问了，直接答复。
  客户代码一个字没改，收件人从「客服」变成了「主管」

[Demo 5：关闭工单 —— 唯一一处真正的广播]
  [客户-李女士] 提问：耳机左耳没声音，能退吗？
      路由 → 客服-小王
        [小王] 收到 客户 的提问：耳机左耳没声音，能退吗？
        [小王] 客服：接单，先看一眼历史记录。
  [客服-小王] 申请关闭工单 AS-2024-0917
      路由 → 客户-李女士、仓库-华东仓、财务-小赵、主管-张主管、通知器-站内信网关
        [李女士] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [李女士] 客户：会话结束，给这次服务打 5 星。
        [华东仓] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [华东仓] 仓库：解除库存锁定，从待办移除。
        [小赵] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [小赵] 财务：退款凭证已归档。
        [张主管] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [张主管] 主管：确认关闭，同意归档。
        [站内信网关] 收到 客服 的归档：工单 AS-2024-0917 已由 客服 关闭
        [站内信网关] 外发站内信给客户：「工单 AS-2024-0917 已由 客服 关闭」
  工单 AS-2024-0917 状态：已关闭
  关闭之后再发言：
    抛出 IllegalStateException：工单 AS-2024-0917 已关闭，李女士 不能再发言
  已关闭 = true，成功投递 6 次

[Demo 6：一家炸了，其他家照常；没定义路由的消息被拦下]
  参与方（4）：客户-王先生、客服-小李、仓库-华南仓、通知器-短信网关（故障）
  —— 仓库试图发一条「提问」（路由表里没有这条边）——
  [仓库-华南仓] 提问：有人能告诉我这单寄回哪里吗？
      路由未命中：角色「仓库」不能发送「提问」，消息丢弃
  —— 缺货上报：财务没加入，跳过；其余照常 ——
  [仓库-华南仓] 缺货上报：EP-2024 无线耳机，华南仓缺货
      路由 → 客服-小李、财务（未加入）、客户-王先生
        [小李] 收到 仓库 的缺货上报：EP-2024 无线耳机，华南仓缺货
        [小李] 客服：收到缺货，这单走退款。
      角色「财务」尚未加入，跳过
        [王先生] 收到 仓库 的缺货上报：EP-2024 无线耳机，华南仓缺货
  —— 关闭工单：短信网关故障，其他人照常收到 ——
  [客服-小李] 申请关闭工单 AS-2024-0918
      路由 → 客户-王先生、仓库-华南仓、通知器-短信网关（故障）
        [王先生] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
        [王先生] 客户：会话结束，给这次服务打 5 星。
        [华南仓] 收到 客服 的归档：工单 AS-2024-0918 已由 客服 关闭
        [华南仓] 仓库：解除库存锁定，从待办移除。
      ! 已隔离：短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达
  工单 AS-2024-0918 状态：已关闭
  投递失败记录（1 条）：
    · 短信网关（故障） 投递失败：IllegalStateException：短信通道超时，归档 未送达

[六段 Demo 对应原项目的六个问题]
  1. 客户提问只到负责方       —— 原项目只能广播给所有人
  2. 缺货触发三方协同         —— 原项目没有「一条消息多个收件人」的规则
  3. 换个角色发言换批收件人   —— 原项目的收发两端永远是「除我之外」
  4. 升级后负责方变更         —— 原项目没有「负责方」这个概念
  5. 关闭是唯一一次广播       —— 原项目全程都在广播
  6. 一家炸了不影响其他家     —— 原项目裸循环，一个 null 就断链

  六个同事的代码里没有一行「通知某某」——协同规则只写在中枢的 route() 里
```

### D.3 输出的结构

| 段 | 行数 | 行号区间 | 内容 |
|---|---|---|---|
| Demo 1 | 8 | 1-8 | 一句话只到一个人 |
| Demo 2 | 19 | 10-28 | 缺货三方协同 |
| Demo 3 | 18 | 30-47 | 换角色换收件人 |
| Demo 4 | 21 | 49-69 | 升级改负责方 |
| Demo 5 | 21 | 71-91 | 关闭广播 + 关闭后禁言 |
| Demo 6 | 23 | 93-115 | 故障隔离 + 未定义路由 |
| summary | 9 | 117-125 | 六条对照 |
| **小计** | **119** | | |
| 段间空行 | 6 | 9/29/48/70/92/116 | 每两段之间一行 |
| **合计** | **125** | | |

**119 + 6 = 125。** 段间那 6 个空行是 `section()` 打的：

```java
private static void section(String title) {
    if (printedSections > 0) {
        System.out.println();        // 除了第一段，每段之前空一行
    }
    printedSections++;
    System.out.println("[" + title + "]");
}
```

**`printedSections` 这个计数器只服务一个目的："第一段之前不要空行"。**

它是 `Client` 里唯一的静态可变字段 —— 因为 `section()` 是静态方法，没有实例可挂。

> **一个静态计数器 + 一个 `if`，换来的是"输出第一行不是空白"。**
> 如果不在乎这个，`section()` 可以只有一行（`println("[" + title + "]")`），
> 但那样输出的开头会是一个空行，`SHA-256` 也会变。

**输出里没有一个字是"示例"** —— 全部是程序真实打印的。本文档 §6 逐段引用的每一行，都是从这份输出里原样复制的。

> **校验方法：**
> 把上面这段输出存成 `run.txt`，然后比对
> `(Get-FileHash run.txt -Algorithm SHA256).Hash`
> 是否等于 `203A78A7...`。
> **如果不相等，说明本项目被改过了** —— 这是最直接的一致性检查。

---

## 附录 E：与原项目逐项对照

### E.1 规模对照

| 项 | 原项目 | 本项目 | 变化 |
|---|---|---|---|
| 源文件数 | **5** | **13** | +8 |
| 总行数 | 118 | 1623 | **+13.8 倍** |
| 参与者类 | 2（`ChatUser` + `User`） | 6（`Customer` 等） | +4 |
| 中枢类 | 1（`ChatRoom`，29 行） | 1（`AfterSalesHub`，375 行） | +346 行 |
| 接口 | 1（`ChatRoomMediator`，15 行） | 2（`Participant` + `CollaborationHub`） | +1 |
| 枚举 | **0** | **2**（`Role` / `MessageKind`） | +2 |
| 值对象 | 0 | 1（`Message`） | +1 |
| 演示入口 | `ChatClient`（26 行） | `Client`（306 行，六段 Demo） | +280 行 |
| 单元测试 | 0 | **0**（但输出可哈希校验） | — |

**"+13.8 倍"是夸张的吗？**

不夸张，但要拆开看：

| 增长来源 | 行数 | 占比 |
|---|---|---|
| 注释与 javadoc | 1030 | 68.8% |
| 演示代码（`Client`） | 306 | 20.4% |
| 业务代码（12 个文件） | 287 | 19.2% |
| | 1623 | |

**具体算一下：**

- 代码行总计 593，其中 `Client` 154 → **业务代码 439 个代码行**；
- 原项目 118 行（含注释），去掉注释估计 70 左右代码行。

**真实倍数是 439 / 70 ≈ 6.3 倍，其中 4 倍来自"从 2 个角色扩到 6 个角色、从 2 种消息扩到 7 种"。**

> **文档里写"13.8 倍"必须同时给出拆解，否则读者会以为是"模式带来的膨胀"。**
> **膨胀的来源是"业务变复杂了"，不是"用了模式"。**

### E.2 概念对照

| 原项目 | 本项目 | 变化理由 |
|---|---|---|
| `ChatRoomMediator`（接口） | `CollaborationHub`（接口） | 名字从"聊天室"改成"协同中枢"，因为场景从聊天变成工单 |
| `User`（抽象类） | `Participant`（**接口**） | 见 §8.3 误解五：共享契约不共享状态 |
| `ChatUser extends User` | 6 个 `implements Participant` | 零继承 |
| `ChatRoom implements ChatRoomMediator` | `AfterSalesHub implements CollaborationHub` | 同上 |
| `protected String name` | `private final String name`（每个类一份） | 修缺陷 5 |
| `protected ChatRoomMediator mediator` | `private final CollaborationHub hub` | 修缺陷 6 |
| `users: List<User>`（只有一份） | `participants: List<Participant>` + `byRole: EnumMap` | 修缺陷 1（两份索引，但**只在 `join()` 里同改**） |
| `registerUser(User)`（无去重） | `join(Participant)`（**同角色唯一**） | 修缺陷 2 |
| `sendMessage(String, User)`（广播） | `say(Participant, MessageKind, String)`（路由） | 修缺陷 7 |
| 无 | `escalate(Participant, String)` | 新增：升级 |
| 无 | `close(Participant)` | 新增：生命周期 |
| 无 | `roster()` / `assignee()` / `deliveryFailures()` | 新增：三个查询 |
| 无 | `Message`（record） | 新增：消息从"一个字符串"变成"结构化的三元组" |
| 无 | `Role` / `MessageKind` | 新增：路由的输入从"猜"变成"枚举" |

### E.3 七个缺陷的修复对照

| # | 原项目缺陷 | 探针 | 本项目的修法 | 代码位置 |
|---|---|---|---|---|
| 1 | 名单与引用是两份状态 | P1 | **合并**：`hub` 在构造时传入且只读，没有 `setHub()` | `Participant.hub()` + 6 个构造器 |
| 2 | 重复注册收两条 | P2 | `join()` 里按角色查重，重复抛 `IllegalArgumentException` | `AfterSalesHub.join()` |
| 3 | `null` 让广播断链 | P3 | `join()` 里 `requireNonNull`；`deliver()` 里 `target == null` 时 `continue` | `AfterSalesHub.join()` / `deliver()` |
| 4 | 遍历中注册 → CME | P4 | **未修**（§6.9 / §8.5 局限 ③） | — |
| 5 | `name` 可空可改 | P5 | `private final` + 构造器校验 `isBlank()` | 6 个参与者类 |
| 6 | `mediator` 可写 | P6 | `private final` + 构造时 `requireNonNull` + 无 setter | `Participant` 的六个实现 |
| 7 | 接口只能群发 | P7 | `say()` 带 `MessageKind`；`route()` 按类型和发送方算收件人 | `route()` |

**六修一留。**

**第 4 条（CME）为什么不修？**

因为修它的成本（`CopyOnWriteArrayList` 或迭代前复制）会掩盖一个更重要的认知：**成员列表在 `close()` 期间应该是稳定的。**

**而且本项目里 `join()` 只在搭建时调用（`build()` 里六次），永远不会在 `deliver()` 中途发生。**

> **"知道洞在哪，并说清楚为什么留着" 比 "假装没有洞" 有价值。**
> 这也是 §2.3 和附录 C 要把探针输出全文放出来的原因 ——
> **缺陷是可以被复现的，不是靠文字描述的。**

### E.4 保留了什么

**任务书要求"保留合理实现"，以下是明确保留的部分：**

| 保留项 | 说明 |
|---|---|
| **独立 Demo 形式** | 没有整合进一个大系统；`Client.main()` 就是全部入口 |
| **原生 Java** | 零第三方依赖，只用 `java.util` / `java.lang` |
| **`Mediator` 模式的核心结构** | 一个中枢 + N 个同事 + 同事只认识中枢 |
| **原项目的场景内核** | 从"聊天室"改成"售后工单"，但"多方消息协同"这个内核没变 |
| **单文件可运行** | 每个模式目录独立，`javac` + `java` 就能跑 |

**改掉了什么：**

| 改动 | 原因 |
|---|---|
| 场景从"聊天室"改成"售后工单" | 聊天室的规则是"所有人看得到"，**正好是中介者最不需要的场景**（那是观察者）。换成工单才能体现"路由" |
| `User` 抽象类 → `Participant` 接口 | 零继承；能力靠 `default` 方法共享 |
| 广播 → 路由表 | 模式的核心价值 |
| 加 `Role` / `MessageKind` 枚举 | 让路由可表达、可穷尽 |
| 加 `Message` record | 让"消息"从字符串变成有结构的值对象 |

---

## 附录 F：术语表

**按拼音/字母顺序，只收录本文档反复用到的词。**

### F.1 模式的角色

| 术语 | 英文 | 在本项目里 | 一句话解释 |
|---|---|---|---|
| **中介者** | Mediator | `CollaborationHub`（接口）/ `AfterSalesHub`（实现） | 决定"谁跟谁说话"的那个中心对象 |
| **同事** | Colleague | `Participant`（接口）/ 六个角色类 | 只和中介者说话、彼此不认识的参与者 |
| **具体中介者** | ConcreteMediator | `AfterSalesHub` | 持有名单、路由表、状态的那个类 |
| **中枢** | Hub | 同上 | 本文档对"中介者"的口语称呼 |

### F.2 本项目特有的词

| 术语 | 在本项目里 | 含义 |
|---|---|---|
| **路由表** | `route()` 方法 | "给定发送方 + 消息类型，返回收件角色列表"的规则表 |
| **投递** | `deliver()` 方法 | 把一条消息逐个送给收件人，并隔离异常 |
| **扇出** | `deliver()` 的循环 | 一条消息 → N 个收件人（对比"链式传递"的 1 → 1） |
| **广播** | `close()` 的遍历 | 一条消息 → **除发送方外全部**。本项目只有这一处 |
| **负责方** | `assignee` 字段 | 客户提问的路由目标，默认客服，升级后是主管 |
| **参与方** | `participants` 列表 | 按 `join()` 顺序保存的所有人 |
| **名单** | 同上 | 本文档对 `participants` 的口语称呼 |
| **在场 / 未加入** | `byRole.get(role) == null` | 角色在路由表里，但没人在这个角色上 |

### F.3 Java 语言相关

| 术语 | 说明 |
|---|---|
| **`record`** | Java 16 的不可变数据载体。`Message` / `Team` 用它。自动生成 `equals` / `hashCode` / `toString` / 访问器 |
| **`EnumMap`** | 键为枚举的 `Map`，内部用数组实现，O(1) 查找，遍历顺序 = 枚举声明顺序 |
| **`List.of(...)`** | 创建**不可变**列表。不接受 `null` 元素（`List.of(null)` 抛 NPE） |
| **`List.copyOf(...)`** | 创建**不可变副本**（真的复制）。用于返回内部集合 |
| **`default` 方法** | 接口里的方法默认实现（Java 8+）。`say()` / `escalate()` / `closeTicket()` 用它 |
| **`switch` 表达式** | `return switch (x) { ... };` 形式。**必须穷尽**（否则编译失败） |
| **`switch` 语句** | `switch (x) { ... }` 形式。**不要求穷尽**（不匹配就什么都不做） |
| **穷尽性** | exhaustiveness。`switch` 表达式覆盖所有枚举值 / 所有 sealed 子类 |
| **合成类** | synthetic class。编译器生成的 `Xxx$1.class`，用于存 `switch` 枚举的映射表 |
| **`ACC_SYNTHETIC`** | class 文件里的标志位，标记"这个类不是源码写的" |
| **`requireNonNull`** | `Objects.requireNonNull(x, "msg")`，`x` 为 `null` 时抛 `NullPointerException`（带自定义消息） |
| **`isBlank()`** | Java 11+。判断字符串是否为空或只含空白（比 `trim().isEmpty()` 更准） |

### F.4 两个容易混的异常

| 异常 | 语义 | 本项目的例子 |
|---|---|---|
| **`IllegalArgumentException`** | **参数**永远不行 | 角色已被占用（换个人就行）、正文为空白 |
| **`IllegalStateException`** | **现在**不行（状态不对） | 工单已关闭（换个时机就行） |
| **`NullPointerException`** | 本该有东西，结果是 `null` | `requireNonNull` 抛的 |

**三者不能混用** —— 因为调用方的补救逻辑不同（换参数 / 换时机 / 补 null）。

### F.5 两种失败策略

| 术语 | 含义 | 本项目的例子 |
|---|---|---|
| **fail safe** | 出错时"少做"而不是"多做" | `Notifier` 的 `default`：不在白名单就丢弃（防止外泄） |
| **fail loud** | 出错时"大声报"而不是"静默吞" | `Warehouse` 的 `default`：收到意外消息打 `!!` 并提示检查路由表 |

**判断标准：**

> **"漏掉"的代价落在外部世界（客户、合规、第三方）→ fail safe；
> 落在自己人（运维、开发）→ fail loud。**

### F.6 三种"顺序来源"

**这是本项目最容易混淆的一点，单独列出来：**

| 来源 | 决定什么 | 由什么决定 |
|---|---|---|
| **路由列表** | `deliver()` 的循环顺序 | `route()` 里 `List.of(...)` 的参数顺序 |
| **名单顺序** | `close()` 广播的顺序 | `join()` 的调用顺序 |
| **`roster()`** | `参与方（6）：...` 那一行的顺序 | 同上 |

**证据：**

- Demo 2 的缺货输出是"客服 → 财务 → 客户"（**路由列表**顺序），而 `join` 顺序是"客户 → 客服"；
- Demo 5 的广播输出是"客户 → 仓库 → 财务 → 主管 → 通知器"（**名单**顺序）。

### F.7 三个"数量"

| 数量 | 值 | 含义 |
|---|---|---|
| **文件数** | 13 | 源文件 |
| **物理行** | 1623 | 含空行和注释 |
| **代码行** | 593 | 去掉空行和纯注释 |
| **class 文件** | 22 | 13 个源类 + 7 个 `$1` 合成类 + 2 个嵌套类 |
| **输出行** | 125 | 一次运行的完整输出 |
| **探针行** | 54 | 七个缺陷的取证输出 |

---

## 结语

**这份文档写了 9 节正文 + 6 个附录，核心只有一句话：**

> **中介者模式做的事，是把"谁跟谁说话"从每个同事的代码里拿走，放进一个能被单独阅读、单独测试、单独修改的地方。**

**它的收益不在"减少耦合"（§8.3 误解二算过，只从 15 条边降到 12 条），**
**而在"加一个角色时，边际成本恒定"和"改一条规则时，只需要改一处"。**

**它的代价也不在"类变多"，而在"规则的遗漏是静默的"（§7.2.5 / §8.2 代价三）。**

**如果读完这份文档只记住一件事，那应该是：**

> **`route()` 是纯的（不碰实例），`deliver()` 是隔离的（一人炸不连累别人），
> `assignee` 是唯一的可变路由输入 —— 这三条撑起了整个模式。**

**验证这三条的方法，本文档都给了：**

| 要验证 | 怎么验 |
|---|---|
| `route()` 是纯的 | 空名单下调 `route()`，仍能算出角色列表（附录 C 的 P7 反证了原项目做不到） |
| `deliver()` 是隔离的 | Demo 6 的 `BrokenNotifier`（§6.7） |
| `assignee` 是唯一可变路由输入 | Demo 4 的两次 `say(QUESTION)`（§6.5） |

**最后一句：**

> **别背模式。去读那段 `route()` —— 七行代码，比任何定义都清楚。**
