# 备忘录模式（Memento Pattern）：把"过去"变成一等公民

> **一句话**：在不破坏封装的前提下，把对象的某个历史状态捕获下来，以便日后恢复。
>
> **它的反面**：为了能撤销，把对象的字段全部 `public` 出去，让外面的人随便读随便写。
>
> **它的难点**：不是"怎么存"，而是"**存的东西该给谁看**"。

---

## 目录

- [1. 现实案例：一次误删，把客服逼到了墙角](#1-现实案例一次误删把客服逼到了墙角)
  - [1.1 说人话版：那个没有撤销的下午](#11-说人话版那个没有撤销的下午)
  - [1.2 专业版：撤销的本质是"状态的时间旅行"](#12-专业版撤销的本质是状态的时间旅行)
  - [1.3 为什么"备份一下"这六个字很难](#13-为什么备份一下这六个字很难)
  - [1.4 本项目的场景与角色](#14-本项目的场景与角色)
- [2. 原始实现：不用备忘录会怎样](#2-原始实现不用备忘录会怎样)
  - [2.1 四个文件逐个读](#21-四个文件逐个读)
  - [2.2 原项目跑出来是什么样](#22-原项目跑出来是什么样)
  - [2.3 十个缺陷逐条取证](#23-十个缺陷逐条取证)
  - [2.4 三条根因](#24-三条根因)
  - [2.5 一个必须回答的问题：那这 6 行输出算"跑对了吗"？](#25-一个必须回答的问题那这-6-行输出算跑对了吗)
- [3. 设计动机：为什么必须有三方](#3-设计动机为什么必须有三方)
  - [3.1 三方角色，各管一件"别人管不了"的事](#31-三方角色各管一件别人管不了的事)
  - [3.2 为什么 Originator 必须自己拍快照](#32-为什么-originator-必须自己拍快照)
  - [3.3 为什么 Caretaker 不能看见内容](#33-为什么-caretaker-不能看见内容)
  - [3.4 什么时候不需要备忘录](#34-什么时候不需要备忘录)
  - [3.5 备忘录 vs 命令 vs 原型：三个容易混的边界](#35-备忘录-vs-命令-vs-原型三个容易混的边界)
  - [3.6 本项目为什么不用 `Optional` 补救 `null`](#36-本项目为什么不用-optional-补救-null)
- [4. 设计推导：十三个决定](#4-设计推导十三个决定)
  - [4.1 为什么 `Snapshot` 只有一个方法](#41-为什么-snapshot-只有一个方法)
  - [4.2 为什么 `Memento` 是 `private record`](#42-为什么-memento-是-private-record)
  - [4.3 为什么还要再加一个 `ownerToken`](#43-为什么还要再加一个-ownertoken)
  - [4.4 为什么快照要带元信息](#44-为什么快照要带元信息)
  - [4.5 为什么用 `EditKind` 枚举而不是字符串](#45-为什么用-editkind-枚举而不是字符串)
  - [4.6 为什么选区是 `TextRegion` 而不是两个 `int`](#46-为什么选区是-textregion-而不是两个-int)
  - [4.7 为什么 `edit()` 包住一段操作](#47-为什么-edit-包住一段操作)
  - [4.8 为什么 `undo()` 返回 `boolean`](#48-为什么-undo-返回-boolean)
  - [4.9 为什么构造器里就拍基线](#49-为什么构造器里就拍基线)
  - [4.10 为什么用 `ArrayDeque` 替代 `Stack`](#410-为什么用-arraydeque-替代-stack)
  - [4.11 为什么容量上限是必须的](#411-为什么容量上限是必须的)
  - [4.12 为什么时钟要注入](#412-为什么时钟要注入)
  - [4.13 为什么要有监听器](#413-为什么要有监听器)
  - [4.14 十三个决定的依赖关系](#414-十三个决定的依赖关系)
- [5. 代码解析：十三个文件](#5-代码解析十三个文件)
  - [5.1 `Snapshot.java`（83 行）：窄接口](#51-snapshotjava83-行窄接口)
  - [5.2 `SnapshotMetadata.java`（83 行）：快照的身份证](#52-snapshotmetadatajava83-行快照的身份证)
  - [5.3 `EditKind.java`（70 行）：七种编辑类型](#53-editkindjava70-行七种编辑类型)
  - [5.4 `TextRegion.java`（111 行）：选区值对象](#54-textregionjava111-行选区值对象)
  - [5.5 `EditorLimits.java`（99 行）：边界与校验](#55-editorlimitsjava99-行边界与校验)
  - [5.6 `Editor.java`（334 行）：Originator](#56-editorjava334-行originator)
  - [5.7 `SnapshotRejectedException.java`（40 行）：归属校验失败](#57-snapshotrejectedexceptionjava40-行归属校验失败)
  - [5.8 `SnapshotHistory.java`（287 行）：Caretaker](#58-snapshothistoryjava287-行caretaker)
  - [5.9 `HistoryStats.java`（74 行）：撤销面板的数字](#59-historystatsjava74-行撤销面板的数字)
  - [5.10 `HistoryListener.java`（42 行）：一个方法的接口](#510-historylistenerjava42-行一个方法的接口)
  - [5.11 `ConsoleHistoryListener.java`（36 行）：控制台撤销面板](#511-consolehistorylistenerjava36-行控制台撤销面板)
  - [5.12 `SteppedClock.java`（60 行）：可复现的时间源](#512-steppedclockjava60-行可复现的时间源)
  - [5.13 `Client.java`（271 行）：六段 Demo](#513-clientjava271-行六段-demo)
- [6. 执行流程：六段 Demo 逐步拆解](#6-执行流程六段-demo-逐步拆解)
  - [6.0 三条阅读约定](#60-三条阅读约定)
  - [6.1 Demo 1：撤销与重做，一次完整的往返](#61-demo-1撤销与重做一次完整的往返)
  - [6.2 Demo 2：一段操作 = 一步撤销](#62-demo-2一段操作--一步撤销)
  - [6.3 Demo 3：编辑中途失败，自动回滚](#63-demo-3编辑中途失败自动回滚)
  - [6.4 Demo 4：容量上限，最老的快照被淘汰](#64-demo-4容量上限最老的快照被淘汰)
  - [6.5 Demo 5：快照不能伪造，也不能串档](#65-demo-5快照不能伪造也不能串档)
  - [6.6 Demo 6：撤到底、重做、再编辑](#66-demo-6撤到底重做再编辑)
  - [6.7 六段串起来：备忘录模式的完整生命周期](#67-六段串起来备忘录模式的完整生命周期)
- [7. 扩展对比：加需求时改哪里](#7-扩展对比加需求时改哪里)
  - [7.1 快照要多存一个字段](#71-快照要多存一个字段)
  - [7.2 新增一种编辑类型](#72-新增一种编辑类型)
  - [7.3 内存快照 → 磁盘持久化](#73-内存快照--磁盘持久化)
  - [7.4 合并连续操作：500 毫秒内的输入算一步](#74-合并连续操作500-毫秒内的输入算一步)
  - [7.5 全量快照 → 差量快照](#75-全量快照--差量快照)
  - [7.6 单文档 → 多文档](#76-单文档--多文档)
  - [7.7 线性历史 → 分支历史](#77-线性历史--分支历史)
  - [7.8 单机 → 协同编辑](#78-单机--协同编辑)
  - [7.9 自动保存：定时快照](#79-自动保存定时快照)
  - [7.10 撤销粒度：按"词"而不是按"字符"](#710-撤销粒度按词而不是按字符)
  - [7.11 十个扩展的改动面汇总](#711-十个扩展的改动面汇总)
- [8. 优缺点与误区](#8-优缺点与误区)
  - [8.1 五个真实优点](#81-五个真实优点)
  - [8.2 六个真实代价](#82-六个真实代价)
  - [8.3 七个常见误区](#83-七个常见误区)
  - [8.4 与三个相似模式的区分](#84-与三个相似模式的区分)
  - [8.5 已知局限：十三处诚实清单](#85-已知局限十三处诚实清单)
- [9. 面试与自测](#9-面试与自测)
  - [9.1 面试题一：三个角色分别负责什么](#91-面试题一三个角色分别负责什么)
  - [9.2 面试题二：为什么需要两个接口](#92-面试题二为什么需要两个接口)
  - [9.3 面试题三：备忘录 vs 命令](#93-面试题三备忘录-vs-命令)
  - [9.4 面试题四：快照存在哪里](#94-面试题四快照存在哪里)
  - [9.5 面试题五：内存扛不住怎么办](#95-面试题五内存扛不住怎么办)
  - [9.6 面试题六：怎么防跨文档污染](#96-面试题六怎么防跨文档污染)
  - [9.7 面试题七：Memento 该不该可变](#97-面试题七memento-该不该可变)
  - [9.8 面试题八：撤销栈要不要设上限](#98-面试题八撤销栈要不要设上限)
  - [9.9 面试题九：大文档还适合全量快照吗](#99-面试题九大文档还适合全量快照吗)
  - [9.10 面试题十：怎么测撤销/重做](#910-面试题十怎么测撤销重做)
  - [9.11 十三道自测题](#911-十三道自测题)
  - [9.12 参考答案](#912-参考答案)
  - [9.13 自检表](#913-自检表)
- [附录](#附录)
- [附录 A：十三个文件的行数清单](#附录-a十三个文件的行数清单)
  - [A.1 精确统计](#a1-精确统计)
  - [A.2 三个比例](#a2-三个比例)
  - [A.3 编译产物：14 个 class](#a3-编译产物14-个-class)
- [附录 B：编译与运行验证](#附录-b编译与运行验证)
  - [B.1 环境](#b1-环境)
  - [B.2 编译命令](#b2-编译命令)
  - [B.3 编译结果](#b3-编译结果)
  - [B.4 运行命令](#b4-运行命令)
  - [B.5 运行结果](#b5-运行结果)
  - [B.6 三次一致性验证](#b6-三次一致性验证)
  - [B.7 原项目基线（用于对照）](#b7-原项目基线用于对照)
- [附录 C：原项目缺陷探针全文](#附录-c原项目缺陷探针全文)
  - [C.1 十条缺陷与本项目的修法对照](#c1-十条缺陷与本项目的修法对照)
  - [C.2 一处必须说明的"探针修正"](#c2-一处必须说明的探针修正)
- [附录 D：本项目真实输出全文](#附录-d本项目真实输出全文)
  - [D.1 逐段行数](#d1-逐段行数)
  - [D.2 六段的关键数字索引](#d2-六段的关键数字索引)
- [附录 E：与原项目逐项对照](#附录-e与原项目逐项对照)
  - [E.1 文件级对照](#e1-文件级对照)
  - [E.2 十条缺陷的修法对照](#e2-十条缺陷的修法对照)
  - [E.3 行为对照表](#e3-行为对照表)
  - [E.4 规模与复杂度的诚实评估](#e4-规模与复杂度的诚实评估)
  - [E.5 明确"未验证"的部分](#e5-明确未验证的部分)
  - [E.6 补测试的建议顺序](#e6-补测试的建议顺序)
- [附录 F：术语表](#附录-f术语表)
  - [F.1 三个角色](#f1-三个角色)
  - [F.2 接口与封装](#f2-接口与封装)
  - [F.3 历史管理](#f3-历史管理)
  - [F.4 Java 语言特性](#f4-java-语言特性)
  - [F.5 文本与区间](#f5-文本与区间)
  - [F.6 验证相关](#f6-验证相关)
  - [F.7 本项目**没有**使用的术语](#f7-本项目没有使用的术语)
- [附录的附录：文件清单与阅读顺序](#附录的附录文件清单与阅读顺序)

---

## 1. 现实案例：一次误删，把客服逼到了墙角

### 1.1 说人话版：那个没有撤销的下午

下午三点，客服小陈在处理一张积压了三天的工单。

客户投诉的是"收到的货少了一件"。小陈翻完聊天记录，在回复框里敲了两百多字，
把前因后果、补偿方案、后续流程都写清楚了。

写到一半，她发现开头那句"非常抱歉给您带来不便"太客套，想换个更直接的写法。

于是她按下 `Ctrl+A` 全选，准备重打。手离开键盘去拿水杯的时候，
不知怎么碰到了 `Ctrl+X`。

两百多个字，一瞬间没了。

她愣了一下，习惯性地去按 `Ctrl+Z`。

**没反应。**

不是因为快捷键坏了，而是因为这个回复框根本没有实现撤销。

她只好从头再写一遍。而这一次，她怎么也想不起原来那段话里，
关于"补发还是退款"的措辞是怎么写的了。

两小时后，主管在复核时发现，第二版回复承诺的补偿方案，
和第一版不一样 —— 第一版写的是"补发 + 20 元券"，第二版只写了"补发"。

客户后来投诉说："你们客服前后说法不一致。"

---

这个故事里，真正让人难受的不是"误删"，而是**删掉之后就真的没有了**。

如果回复框支持撤销，小陈按一下 `Ctrl+Z` 就回来了，
后面这一切都不会发生。

所以"撤销"这个功能，看起来是个小按钮，实际上它决定了
**用户敢不敢在系统里做尝试**。

一个不能撤销的编辑器，用户会变得胆小：每改一个字都小心翼翼，
改之前先复制一份到记事本。这不是用户的问题，是产品的问题。

---

### 1.2 专业版：撤销的本质是"状态的时间旅行"

把一次编辑会话抽象成一条状态链：

```text
        e1          e2           e3
  S0 -------> S1 -------> S2 --------> S3
 初始        输入          删除          插入
```

用户在 `S3` 时刻按 `Ctrl+Z`，想让系统回到 `S2`。

这个"回到过去"的动作，工程上有三条完全不同的实现路线。

#### 路线一：反向操作（命令模式的 undo）

记下每个操作做了什么，撤销时**反着再做一遍**。

```text
  e3 = "在位置 12 插入 3 个字符"
  → undo(e3) = "删除位置 [12, 15)"
```

- **优点**：存储极小，一个操作只要几十字节。
- **缺点**：**每一个操作都必须写出正确的逆运算**。
  插入的逆是删除，这个简单；但"替换全部"的逆是什么？
  "格式化加粗"的逆是什么？"自动纠错把 teh 改成 the"的逆是什么？
  只要有一个操作的逆运算写错，撤销就会把文档改坏。

这就是本项目**命令模式**那一章做的事 —— 那里每个 `Task` 都实现了 `undo()`。

#### 路线二：状态快照（备忘录模式）

把 `S2` 整个存下来，撤销时**直接覆盖**。

```text
  撤销 = editor.restore(snapshot)   // 不需要知道 e3 是什么
```

- **优点**：**绝对正确**。快照里有什么，恢复出来就是什么。
  不管中间发生了多复杂的操作，只要快照是完整的，恢复就一定对。
- **缺点**：**占空间**。一份 200KB 的文档，存 20 个版本就是 4MB。

#### 路线三：事件重放

从 `S0` 开始，把所有事件重新执行一遍到 `S2`。

- **优点**：存储适中，且天然支持"历史分支"。
- **缺点**：依赖所有操作**确定性**（同样的输入必得同样的输出）。
  一旦有随机 id、时间戳、网络请求混在里面，重放就对不上。

这也是协同编辑（CRDT / OT）走的路线 —— 因为那里本来就没有"中心状态"，
只有一堆操作。

#### 三条路线的权衡

| 路线 | 存储开销 | 正确性 | 逆运算成本 | 典型场景 |
|---|---|---|---|---|
| 反向操作 | 极小 | 取决于每个逆运算写得对不对 | **高**，且容易漏 | 文本编辑器的单字符插入/删除 |
| **状态快照** | **大** | **只要快照完整就一定对** | **零** | **图形编辑器、表单、游戏存档** |
| 事件重放 | 中 | 取决于操作是否确定性 | 零 | 协同编辑、数据库 WAL |

**备忘录模式选的是第二条。**

它用"空间"换"正确性"。这笔交易划不划算，取决于两件事：

1. 状态有多大？（小状态随便存；大状态要想办法压缩或落盘）
2. 逆运算有多难写？（越难写，快照越划算）

图形编辑器里，"把一个图层旋转 37 度"的逆运算是"旋转 -37 度"，
看起来简单 —— 但浮点数一舍入，来回转两次就不是原图了。
所以 Figma、Sketch 这类工具全都用快照，不用反向操作。

---

### 1.3 为什么"备份一下"这六个字很难

听完上面的分析，你可能会想：

> 那还不简单？每次修改前，把对象复制一份存起来，不就完事了？

这句话里藏着三个必须回答的问题，而备忘录模式的全部复杂性，
就来自这三个问题。

#### 问题一：备份什么？

"对象的当前状态"到底包含哪些字段？

原项目（`e:\codeFile\codeFather\design_patterns`）的答案是最偷懒的那种：

```java
public record DocumentMemento(String content) { }
```

只有一个 `content`。

于是备份出来的"状态"，**没有光标位置、没有选区、没有修订痕迹**。

后果是：用户在一篇长文的第一行和最后一行之间来回切换，
撤销一次之后，光标"跳"到了某个莫名其妙的地方。
因为他撤销的是"文字"，不是"你当时正在看哪里"。

这就是**探针 P5** 要取证的事。

#### 问题二：谁来备份？

原项目把这件事交给了调用方：

```java
document.setContent("第一次编辑");
history.pushMemento(document.save());      // ← 这行必须由调用方自己记得写
```

只要有一处忘了写，那个状态就永久丢失，而且**没有任何报错**。

更要命的是，`pushMemento` 里面还顺手做了别的事情：

```java
public void pushMemento(DocumentMemento memento) {
    history.push(memento);
    redoStack.clear();      // ← 每次 push 都清空重做栈
}
```

这意味着"存快照"这个动作**带着副作用**。调用方只是想记一笔，
结果把用户的"重做"能力清掉了。

这就是**探针 P2** 要取证的事。

#### 问题三：备份放哪、放多少？

原项目的答案是：

```java
private final Stack<DocumentMemento> history = new Stack<>();
```

无限放。

用户编辑一个下午，撤销栈里就堆了几万份文档副本。
探针 P8 里压了 10 万条，`history.size()` 就是 100000 ——
没有任何上限、淘汰策略或压缩。

这就是**探针 P8** 要取证的事。

---

这三个问题，恰好对应备忘录模式的三个角色：

| 问题 | 谁回答 | GoF 的命名 |
|---|---|---|
| 备份什么？ | 被备份的那个对象自己 | **Originator（原发器）** |
| 备份放哪？ | 一个专门的保管者 | **Caretaker（负责人）** |
| 备份长什么样？ | 一份"只有 Originator 能读懂"的数据 | **Memento（备忘录）** |

> **划重点**：这三个角色的分工，不是 GoF 为了凑数硬拆的。
> 它们回答的是三个**本质上不同**的问题：
> "什么算完整状态"是**领域知识**，
> "什么时候拍、拍几张、放多久"是**策略**，
> "这份数据给谁看"是**封装**。
> 三件事混在一个类里，任何一件要改，另外两件都会跟着动。

---

### 1.4 本项目的场景与角色

#### 场景

沿用上面的故事，但把它做成一个可以独立运行、可以观察的 Demo：

> **客服工单的回复草稿编辑器。**
>
> 客服在回复框里反复修改一段文字，需要：
> - 撤销 / 重做（基本盘）
> - 一次"全选替换"只算一步撤销（不是每敲一个字算一步）
> - 改到一半校验失败时，自动退回改之前（不能留半截）
> - 撤销栈有上限（不能无限吃内存）
> - 撤销面板能显示"第几步、做了什么、什么时候"
> - A 文档的快照绝对不能装进 B 文档

#### 十三个文件，四个核心角色

```text
com.patterns.behavioral.memento
├── Editor.java                    ← Originator：拍快照 + 恢复
├── Snapshot.java                  ← Memento 窄接口：Caretaker 只能看见这个
├── SnapshotHistory.java           ← Caretaker：保管 + 决定何时拍
│
├── SnapshotMetadata.java          ← 快照的"身份证"（编号/类型/时间）
├── EditKind.java                  ← 编辑类型枚举（基线/输入/删除/…）
├── TextRegion.java                ← 选区值对象
├── HistoryStats.java              ← 撤销面板要显示的数字
│
├── EditorLimits.java              ← 边界与校验，集中一处
├── SnapshotRejectedException.java ← 归属校验失败
├── SteppedClock.java              ← 可注入的确定性时钟
├── HistoryListener.java           ← 历史变更通知
├── ConsoleHistoryListener.java    ← 控制台"撤销面板"
└── Client.java                    ← 六段 Demo
```

如果把"模式本体"和"配套设施"分开看：

| 类别 | 文件 | 删掉它会怎样 |
|---|---|---|
| **模式本体** | `Editor` `Snapshot` `SnapshotHistory` | 模式不存在了 |
| **快照内容** | `SnapshotMetadata` `EditKind` `TextRegion` | 快照变成"只有一个字符串"，退化回原项目 |
| **配套视图** | `HistoryStats` `HistoryListener` `ConsoleHistoryListener` | 功能都在，但看不见 |
| **边界防护** | `EditorLimits` `SnapshotRejectedException` | 探针 P6/P10 的洞会回来 |
| **测试设施** | `SteppedClock` | 功能都在，但输出无法逐字复现 |

> **判断一个类是不是"模式本体"的方法**：
> 问一句"删掉它，这个模式还成立吗？"。
> 删掉 `HistoryListener`，撤销照样工作 —— 它是配套设施。
> 删掉 `SnapshotHistory`，就没有人保管快照了 —— 它是本体。
>
> 这个判断标准，和本项目在观察者模式那一章区分
> "主题/观察者"与"事件总线"用的是同一条。

---

#### 本项目的四个核心角色，各自的一句话职责

| 角色 | 类 | 一句话职责 | 它**不**负责 |
|---|---|---|---|
| Originator | `Editor` | 把"我现在的完整状态"打包成快照；把快照装回自己身上 | 不知道快照被存了几份、存了多久 |
| Memento（窄） | `Snapshot` | 只暴露元信息，不暴露内容 | 不提供任何读写正文的方法 |
| Memento（宽） | `Editor.Memento` | 真正装着正文/光标/选区 | 对它自己的类型外部**不可见** |
| Caretaker | `SnapshotHistory` | 决定何时拍、保管几张、何时丢 | 不读、不改、不构造快照内容 |

这张表里最反直觉的是最后一行：**Caretaker 不能读快照**。

在很多人的直觉里，"保管者"当然要能看见自己保管的东西。
但备忘录模式的核心恰恰是"**保管者只能握住，不能打开**"。

下一章会用原项目的代码和探针输出，证明"能打开"的代价是什么。

---
## 2. 原始实现：不用备忘录会怎样

原项目在 `e:\codeFile\codeFather\design_patterns\src\BehaviorType\MementoPattern`，
一共四个文件、131 行：

| 文件 | 物理行 | 角色 |
|---|---:|---|
| `Document.java` | 36 | Originator（原发器） |
| `DocumentMemento.java` | 12 | Memento（备忘录） |
| `History.java` | 40 | Caretaker（负责人） |
| `Client.java` | 42 | 入口 |

**它确实用到了备忘录模式的三方结构。** 这一点必须先说清楚 ——
下面的批评不是"它没写对模式"，而是"**它写对了骨架，却漏掉了让这个模式真正起作用的那些约束**"。

### 2.1 四个文件逐个读

#### 2.1.1 `Document.java`：Originator

```java
package BehaviorType.MementoPattern;

/**
 * @ClassName: Document
 * @Package: BehaviorType.MementoPattern
 * @Description: 定义文档类：负责修改和恢复文档状态
 * 文档类（Document）拥有 save 和 restore 方法，用来创建和恢复文档的状态。
 * 每次编辑文档后，可以保存当前状态，若需要撤销，则通过 restore 方法恢复到之前的状态。
 * @Author HGL
 * @Create: 2025/9/11 13:08
 */

public class Document {
    private String content;

    public Document(String content) {
        this.content = content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public DocumentMemento save() {
        return new DocumentMemento(this.content);
    }

    public void restore(DocumentMemento memento) {
        this.content = memento.content();
    }
}
```

**这段代码的优点：**

- `save()` 和 `restore()` 都在 `Document` 内部，符合"只有 Originator 能读写自己的状态"。
- `content` 是 `private` 的，外部拿不到。
- 一行注释就点明了职责："负责修改和恢复文档状态"。

**问题出在两处：**

**第一处，第 32-34 行的 `restore`：**

```java
public void restore(DocumentMemento memento) {
    this.content = memento.content();
}
```

这里隐含了两个假设：

1. `memento` 不为 `null`；
2. `memento` 一定是"我的"备忘录。

这两个假设都没有被检查。而 `History.popMemento()` 恰好会返回 `null`（下面会看到），
于是假设 1 在正常使用路径上就会被打破。

**第二处，`setContent`：**

```java
public void setContent(String content) {
    this.content = content;
}
```

没有任何校验。传 `null` 进来，`content` 就变成 `null`，
下一次 `save()` 就会拍下一张"内容是 null"的快照，
`restore` 之后文档就真的空了。

这一条会引出**探针 P6**。

---

#### 2.1.2 `DocumentMemento.java`：Memento

```java
package BehaviorType.MementoPattern;

/**
 * @ClassName: DocumentMemento
 * @Package: BehaviorType.MementoPattern
 * @Description: 定义备忘录类：用于存储文档的当前状态 保存了文档的内容，它只提供读取的功能，不允许外部修改，确保了封装性。
 * @Author HGL
 * @Create: 2025/9/11 13:06
 */
public record DocumentMemento(String content) {
}
```

**这个文件是全项目最值得停下来看的地方。**

它的 javadoc 写着：

> 保存了文档的内容，**它只提供读取的功能，不允许外部修改，确保了封装性**。

这句话有两处经不起推敲：

**第一处："不允许外部修改"。**

`record` 的组件确实是 `final` 的 —— 一旦构造完成，`content` 就改不了了。
从这个角度说，"不允许修改"是对的。

但备忘录需要防的**不是"改"，而是"造"**。

一份 `public record` 的 **canonical constructor 也是 `public`** 的。
也就是说，任何代码都能写出：

```java
DocumentMemento forged = new DocumentMemento("伪造内容");
document.restore(forged);
```

探针 P4 的真实输出：

```text
[P4 备忘录可被外部伪造]
  外部 new DocumentMemento("伪造内容") 后 restore -> 文档 = 伪造内容
  唯一构造器 = public BehaviorType.MementoPattern.DocumentMemento(java.lang.String)
  是 public？true
  -> javadoc 称「不允许外部修改」，实际任何人都能造一个新的
```

**"不可变"和"不可伪造"是两件事。** `record` 保证了前者，完全没有保证后者。

**第二处："确保了封装性"。**

`record` 会自动生成一个 `public` 的访问器 `content()`。
于是任何拿到 `DocumentMemento` 引用的代码，都能直接读出正文：

```java
String leaked = memento.content();
```

Caretaker 拿到备忘录之后，本来应该"只保管、不打开"。
但在这里，Caretaker（以及任何第三方）**随时可以打开看**。

javadoc 的承诺和代码的实际能力，**方向是反的**。

---

#### 2.1.3 `History.java`：Caretaker

```java
package BehaviorType.MementoPattern;

import java.util.Stack;

/**
 * @ClassName: History
 * @Package: BehaviorType.MementoPattern
 * @Description: 定义历史管理类：负责保存多个状态，用于撤销和重做
 * History 类负责管理所有文档的历史状态。它保存了撤销操作和重做操作的栈，并通过 pushMemento 和 popMemento 方法来管理文档的历史记录。
 * @Author HGL
 * @Create: 2025/9/11 13:56
 */
public class History {
    private final Stack<DocumentMemento> history = new Stack<>();
    private final Stack<DocumentMemento> redoStack = new Stack<>();

    public void pushMemento(DocumentMemento memento) {
        history.push(memento);
        redoStack.clear(); // 每次新操作时清空重做栈
    }

    public DocumentMemento popMemento() {
        if (history.size() > 1) {
            DocumentMemento current = history.pop();
            redoStack.push(current);
            // 返回上一个状态（不弹出）
            return history.peek();
        }
        return null;
    }

    public DocumentMemento popRedoMemento() {
        if (!redoStack.isEmpty()) {
            DocumentMemento memento = redoStack.pop();
            history.push(memento);
            return memento;
        }
        return null;
    }
}
```

40 行，是整个模式里问题最集中的一个文件。逐段看：

**（1）`pushMemento` 带着副作用**

```java
public void pushMemento(DocumentMemento memento) {
    history.push(memento);
    redoStack.clear();
}
```

名字叫 `pushMemento`，听起来只是"压栈"。
但它顺手把重做栈清空了。

这个副作用在"用户做了新编辑"的语境下是**正确**的
（标准编辑器行为：撤销之后又打字，重做就失效了）。
问题在于它被藏在了名字后面 —— 调用方看到的只是"存一笔"。

**（2）`popMemento` 一个方法做了三件事**

```java
public DocumentMemento popMemento() {
    if (history.size() > 1) {
        DocumentMemento current = history.pop();   // ① 弹出栈顶
        redoStack.push(current);                   // ② 压入重做栈
        return history.peek();                     // ③ 返回"新的栈顶"（不弹出）
    }
    return null;
}
```

三件事是：

1. 把栈顶（**当前状态**）弹出；
2. 把它存进重做栈；
3. 返回**新的栈顶**（上一个状态），但**不弹出它**。

这里有一个非常微妙的设计：**栈顶永远代表"当前状态"**。

- `history = [S0, S1, S2]`，此时文档是 `S2`，栈顶也是 `S2` ✓
- 撤销一次：弹出 `S2`，`history = [S0, S1]`，返回 `S1`
- 调用方 `restore(S1)` 之后，文档是 `S1`，栈顶也是 `S1` ✓

这个约定能工作，但**它把"恢复"这一步交到了调用方手里**。
只要调用方忘了 `restore`，约定就破了：

- 撤销一次：`history = [S0, S1]`，返回 `S1` 被丢弃
- 文档还是 `S2`，但栈顶是 `S1`
- **两者从此永久错位**

探针 P2 就是这个场景。

**（3）用 `null` 同时表达两种含义**

```java
if (history.size() > 1) { ... }
return null;
```

`return null` 出现在两种完全不同的情况下：

- `history` 是空的（从来没存过）
- `history` 只剩 1 条（已经撤到底了）

调用方拿到 `null`，**无法区分**这两种情况，也无法区分"操作成功"和"没有更多"。

而 `Document.restore(null)` 会直接 NPE。

**（4）`Stack` 是个遗留类**

`java.util.Stack` 从 JDK 1.0 就在了。它继承 `Vector`，
而 `Vector` 是"每个方法都 `synchronized`"的古老集合。

探针 P7 的实测数据：

```text
[P7 History 用的是遗留类 Stack（继承 Vector）]
  Stack 的父类 = java.util.Vector
  Stack 自己声明的 public 方法数 = 5
  Vector 可见的 public 方法数   = 66
  Stack.push 是 synchronized？false（它只是转发给 addElement）
  Vector.addElement 是 synchronized？true
  Vector.get 是 synchronized？true
  正常栈 = [a, b, c]，peek() = c
  add(1,"X") 后 = [a, X, b, c]（顺序被插队）
  get(0) = a，remove(0) = a
  剩余 = [X, b, c]，size() = 3
  History.history 的运行时类型 = java.util.Stack
  -> 对外暴露 66 个 public 方法，只有 5 个是栈该有的
```

三个关键数字：

- `Stack` 自己只声明了 **5** 个方法（`push`/`pop`/`peek`/`empty`/`search`）；
- 通过继承，它对外可见 **66** 个 `public` 方法；
- `Vector.get(int)` / `add(int, E)` / `remove(int)` 全都在，**按下标插队**轻而易举。

注意第三行：`Stack.push` **本身没有** `synchronized` ——
它只是转发给 `Vector.addElement`，那个才是 `synchronized` 的。
所以"`Stack` 是线程安全的"这句话，实际含义是"**加锁加在了另一个方法上**"，
而且加的是 `Vector` 实例自己那把锁。

JDK 官方从 Java 6 起就在 `Stack` 的文档里写着：

> A more complete and consistent set of LIFO stack operations is provided by
> the `Deque` interface and its implementations, which should be used in
> preference to this class.

翻译：**需要栈就用 `Deque`，别用这个类。**

---

#### 2.1.4 `Client.java`：调用方

```java
package BehaviorType.MementoPattern;

public class Client {
    public static void main(String[] args) {
        Document document = new Document("初始内容");
        History history = new History();

        // 保存初始状态
        history.pushMemento(document.save());
        System.out.println("文档内容: " + document.getContent());

        // 修改文档内容
        document.setContent("第一次编辑");
        history.pushMemento(document.save());
        System.out.println("文档内容: " + document.getContent());

        // 修改文档内容
        document.setContent("第二次编辑");
        history.pushMemento(document.save());
        System.out.println("文档内容: " + document.getContent());

        // 撤销
        document.restore(history.popMemento());
        System.out.println("撤销后文档内容: " + document.getContent());

        // 再次撤销
        document.restore(history.popMemento());
        System.out.println("再次撤销后文档内容: " + document.getContent());

        // 重做
        document.restore(history.popRedoMemento());
        System.out.println("重做后文档内容: " + document.getContent());
    }
}
```

**这段 Client 有一个非常关键的细节：`setContent` 和 `pushMemento` 是紧挨着的。**

```java
document.setContent("第一次编辑");
history.pushMemento(document.save());
```

这两行必须**成对出现**，而且必须是"先改、后存"。

问题是：**这个配对关系没有任何东西在保证。**

- 编译器不管。少写一行 `pushMemento`，代码照样编译。
- 运行时不管。少写一行，撤销时就是少一步，不报错。
- 类型系统不管。`setContent` 的返回类型是 `void`，它不知道有人要存快照。

这就是**探针 P2** 的地基：把"什么时候拍快照"这件事实实在在地交给了调用方的自觉。

---

### 2.2 原项目跑出来是什么样

先把它跑起来。命令（JDK 25，工作目录为项目根）：

```bash
javac -Xlint:all -encoding UTF-8 -d out \
      BehaviorType/MementoPattern/Document.java \
      BehaviorType/MementoPattern/DocumentMemento.java \
      BehaviorType/MementoPattern/History.java \
      BehaviorType/MementoPattern/Client.java

java -Dfile.encoding=UTF-8 -cp out BehaviorType.MementoPattern.Client
```

真实输出，6 行：

```text
文档内容: 初始内容
文档内容: 第一次编辑
文档内容: 第二次编辑
撤销后文档内容: 第一次编辑
再次撤销后文档内容: 初始内容
重做后文档内容: 第一次编辑
```

实测数据：

| 项目 | 值 |
|---|---|
| 编译退出码 | `0`（`-Xlint:all` 零警告） |
| 运行退出码 | `0` |
| 标准错误 | 空（0 字节） |
| 输出行数 | 6 |
| 输出字节 | 150 |
| SHA-256 | `EBFDF25256129334BC186660193FEC2BFFA3D8DC4A2CF4E5D990D3403CFE5E1E` |

**这 6 行，全都对。**

- 初始 → 第一次编辑 → 第二次编辑：正确；
- 撤销一次回到"第一次编辑"：正确；
- 再撤销回到"初始内容"：正确；
- 重做一次回到"第一次编辑"：正确。

如果你只看这段输出，会得出"这个实现没问题"的结论。

**真正的缺陷全部藏在"这段 Client 没有走到的路径"上。**

这句话值得停一下：

> 一个 Demo 跑得通，只能证明"这条路径"没问题。
> 它证明不了"别的路径"也没问题。
>
> 而框架、工具类、中间件，它们的用户**永远不会只走你走过的那条路径**。

所以下面开始，我们用一段**探针**去走那些没被走过的路径。

---

### 2.3 十个缺陷逐条取证

探针是一个独立的 `Probe.java`，放在同一个包里，**不修改原项目任何一个文件**。
它的全部作用是"调用原项目的公开 API，把结果打印出来"。

运行结果：68 行、2354 字节（第一版）/ 2536 字节（最终版）、
SHA-256 `81F9A14C2998ADFD3FDC8315161C11CEDD0CB95F8896803BCF5C685CF400CADD`，
连跑三次字节完全一致。

下面是这 10 条的逐字输出与源码归因。

---

#### P1：`restore(null)` 直接 NPE

**探针代码（关键几行）：**

```java
Document doc = new Document("A");
History h = new History();
h.pushMemento(doc.save());          // history = [A]
DocumentMemento m = h.popMemento(); // size = 1，不 > 1 → 返回 null
doc.restore(m);                     // ← 这里
```

**真实输出：**

```text
[P1 restore(null) 直接 NPE]
  history 只有 1 条时，popMemento() 返回 = null
  restore(null) 抛出 java.lang.NullPointerException
  抛点 = BehaviorType.MementoPattern.Document.restore(Document.java:33)
  -> Document.restore 对入参零校验
```

**源码定位：**

- `History.java:23` —— `if (history.size() > 1)`，只有 1 条时走到 `return null`；
- `Document.java:33` —— `this.content = memento.content();`，`memento` 为 `null` 时 NPE。

**为什么这不是"调用方不小心"：**

`popMemento()` 的契约本身就是"可能返回 null"。
调用方拿到一个可能为 null 的返回值，最自然的写法就是直接传下去 ——
因为方法名叫 `restore(DocumentMemento)`，参数类型是 `DocumentMemento` 而不是
`Optional<DocumentMemento>`，**类型上没有任何提示说它可能是 null**。

**这正是"用 null 表达业务状态"的经典代价**：类型系统帮不上忙，
所有的判空都只能靠人记住。

---

#### P2：忽略 `popMemento()` 的返回值，历史被静默吃掉

**探针代码：**

```java
Document doc = new Document("v0");
History h = new History();
h.pushMemento(doc.save());              // [v0]
doc.setContent("v1"); h.pushMemento(doc.save());  // [v0, v1]
doc.setContent("v2"); h.pushMemento(doc.save());  // [v0, v1, v2]

h.popMemento();   // 返回值被丢弃
h.popMemento();   // 返回值被丢弃
h.popMemento();   // 第三次
```

**真实输出：**

```text
[P2 忽略 popMemento() 返回值：历史被静默吃掉]
  三条历史入栈，文档 = v2
  丢弃两次返回值后，文档 = v2（一次都没变）
  第三次 popMemento() = null
  -> history 已空，document 仍是 v2：两边彻底脱节
```

**逐步推演：**

| 步骤 | `history` 内容 | `redoStack` 内容 | 文档 | `popMemento()` 返回 |
|---|---|---|---|---|
| 初始 | `[v0, v1, v2]` | `[]` | `v2` | — |
| 第 1 次 pop | `[v0, v1]` | `[v2]` | `v2`（没变） | `v1`（被丢弃） |
| 第 2 次 pop | `[v0]` | `[v2, v1]` | `v2`（没变） | `v0`（被丢弃） |
| 第 3 次 pop | `[v0]` | `[v2, v1]` | `v2` | `null` |

**注意最后一列和第二列的关系：**

`popMemento()` 每次都在**改内部状态**，但文档一个字都没动。
两次调用之后，`v1` 和 `v0` 已经从 `history` 里消失了。

它们还在 `redoStack` 里 —— 所以数据没"丢"，但**`history` 这条线已经废了**。
用户如果此时想做一次"撤销"，发现已经退不回去了。

**为什么这比 NPE 更糟：**

NPE 至少会崩，崩了就能修。
这里的失败是**静默**的：程序继续跑，界面继续响应，
只是用户的撤销能力被偷偷削掉了两层。

> 一条经验：**崩溃是礼物，静默是债务。**
> 崩溃当场还清，静默要等到某个客户投诉时才连本带利地还。

---

#### P3：用 `null` 同时表达"没有更多"和"正常返回"

**探针代码：**

```java
History empty = new History();
System.out.println(empty.popMemento());      // 从没存过

History one = new History();
one.pushMemento(new Document("only").save());
System.out.println(one.popMemento());        // 只有 1 条
```

**真实输出：**

```text
[P3 用 null 同时表达「没有更多」和「正常返回」]
  空 History.popMemento()          = null
  只有 1 条的 History.popMemento()  = null
  -> 调用方无法区分「到底了」和「出错了」
```

**这是 P1 的直接前因。**

如果 `popMemento()` 的返回类型是 `Optional<DocumentMemento>`，
调用方就必须显式处理"没有"的情况，P1 的 NPE 从源头上不可能发生。

不过本项目最终**没有用 `Optional`**，而是用了另一种更彻底的办法 ——
把 `undo()` 的返回类型改成 `boolean`，让"有没有发生"和"恢复成什么"
彻底分成两个问题。理由见 §4.8。

---

#### P4：备忘录可被外部伪造

**探针代码：**

```java
Document doc = new Document("真实内容");
DocumentMemento forged = new DocumentMemento("伪造内容");
doc.restore(forged);

Constructor<?> ctor = DocumentMemento.class.getDeclaredConstructors()[0];
System.out.println(ctor);
System.out.println(Modifier.isPublic(ctor.getModifiers()));
```

**真实输出：**

```text
[P4 备忘录可被外部伪造]
  外部 new DocumentMemento("伪造内容") 后 restore -> 文档 = 伪造内容
  唯一构造器 = public BehaviorType.MementoPattern.DocumentMemento(java.lang.String)
  是 public？true
  -> javadoc 称「不允许外部修改」，实际任何人都能造一个新的
```

**这条直接推翻了 `DocumentMemento` 的 javadoc。**

javadoc 说的是"不允许外部修改，确保了封装性"。
实际是：

- **改**不了 —— 对，`record` 是 `final` 字段；
- **造**得了 —— 任意代码 `new DocumentMemento("任意内容")` 即可。

而 `Document.restore` **无条件接受**这个伪造的备忘录。

**为什么这在真实业务里是事故：**

想象一个审批流。`Document` 是一张报销单，
`DocumentMemento` 是它的历史版本，用户点"撤销"就能回到上一版。

现在任何一个插件、任何一个被注入的脚本，
都能构造一张"金额 = 99999"的备忘录，塞进 `restore`。
**撤销变成了"任意写入"。**

**备忘录模式的封装要求，原文是这样的：**

> The memento is an object that contains a snapshot of the originator's
> internal state. The originator decides which internal state is saved
> in the memento. **The memento is opaque to other objects.**

关键词是 **opaque（不透明）**。

`public record DocumentMemento` 的每一个成员都是透明的。
它在结构上就不是一个 Memento，只是一个"恰好叫 Memento 的 DTO"。

---

#### P5：只存 `content` 一个组件

**探针代码：**

```java
System.out.println(Arrays.toString(DocumentMemento.class.getRecordComponents()));
System.out.println(DocumentMemento.class.getRecordComponents().length);
```

**真实输出：**

```text
[P5 只存 content 一个组件]
  DocumentMemento 的 record 组件 = [java.lang.String content]
  组件数 = 1
  -> 想恢复「光标位置」「选区」「时间戳」，必须同时改三个类
```

**组件的"少"，带来的不是"简单"，而是"加不进去"。**

假设产品经理说："撤销之后，光标要回到当时的位置。"

你需要改三个地方：

1. `DocumentMemento` —— 加一个 `int cursor` 组件；
2. `Document.save()` —— 多传一个参数；
3. `Document.restore()` —— 多读一个字段，还要把它塞回文档。

如果再加"选区"、"滚动位置"、"拼写检查忽略列表"，
那就变成了 3 × N 处修改。

**更关键的问题是：这三处修改会分散在三个不同的时间点。**
某次迭代只改了 `Memento` 和 `save()`，忘了 `restore()` ——
编译器不会报错（多一个组件不写 `restore` 也能编译），
于是光标位置"存下来了但永远恢复不了"。

> 这就是为什么本项目的 `Memento` 只有 `Editor` 一个类能看见：
> **`save` 和 `restore` 写在同一个文件里，加字段时不可能只改一半。**

---

#### P6：`setContent` 无校验，`null` 一路进到历史

**探针代码：**

```java
Document doc = new Document("正常内容");
doc.setContent(null);
System.out.println(doc.getContent());   // null
DocumentMemento m = doc.save();         // 拍下 content = null

History h = new History();
h.pushMemento(new Document("好内容").save());
h.pushMemento(m);                        // null 进栈

Field f = History.class.getDeclaredField("history");
f.setAccessible(true);
System.out.println(f.get(h));            // 反射看栈内实际内容
```

**真实输出：**

```text
[P6 setContent 无校验：null 一路进到历史]
  setContent(null) 后 getContent() = null
  对 null 求 save() 得到 = DocumentMemento[content=null]
  null 快照已进入 history，没有任何一层拒绝它
  栈内实际内容 = [DocumentMemento[content=好内容], DocumentMemento[content=null]]
  -> 一旦 restore 到这条快照，文档就真的没有内容了
```

**这条缺陷的特征是"四层放行"：**

| 层 | 方法 | 有没有校验 |
|---|---|---|
| 1 | `Document.setContent(null)` | 无 |
| 2 | `Document.save()` | 无 |
| 3 | `History.pushMemento(...)` | 无 |
| 4 | `Document.restore(...)` | 无 |

四个方法，没有一层拦。

**为什么会这样？**

因为在这份设计里，**"什么算合法状态"这条规则没有归属**。
每个类都觉得"这是别人的事"：

- `setContent` 觉得"我只是个 setter"；
- `save` 觉得"我只是复制一下"；
- `History` 觉得"我只是保管，内容与我无关"；
- `restore` 觉得"存进来的一定是合法的"。

**责任真空，是所有校验漏洞的共同成因。**

本项目的做法是把这个规则钉在 `EditorLimits.requireText(String)` 里，
并且让 `Editor` 的每一个写入路径都经过它。见 §5.6。

---

#### P7：`History` 用的是遗留类 `Stack`

输出已在 §2.1.3 完整引用，这里只补一句结论：

> `Stack` 继承 `Vector`，对外暴露 66 个 `public` 方法，
> 其中 61 个与"栈"无关。它能被 `add(1, "X")` 从中间插队 ——
> 一个"只应该 push/pop"的容器，实际上什么都能干。

`History` 把 `history` 声明为 `private`，所以外部**直接**拿不到它。
但这只挡住了"绕过 `History` 的 API"这一种玩法；
`History` 自己的实现者，每天都面对着 66 个可以误用的方法。

**封装不只是"字段私有"，还包括"把不该露的能力藏起来"。**

---

#### P8：历史无容量上限

**探针代码：**

```java
Document doc = new Document("v0");
History h = new History();
Field f = History.class.getDeclaredField("history");
f.setAccessible(true);
Stack<?> inner = (Stack<?>) f.get(h);

for (int i = 0; i < 100_000; i++) {
    doc.setContent("v" + i);
    h.pushMemento(doc.save());
}
System.out.println(inner.size());
```

**真实输出：**

```text
[P8 历史无容量上限]
  push 100000 次后 history.size() = 100000
  -> 没有任何容量上限、淘汰策略或压缩
```

**算一笔账。**

一份 200KB 的正文，存 10 万个版本，理论上是 20GB。
即使实际工作中不会真的压 10 万条，一个客服一天处理 50 张工单、
每张改 20 次，就是 1000 条；一个月就是 3 万条。

**真实产品的做法**（都能在 IDEA、WPS、Figma 里观察到）：

- **深度上限**：只留最近 N 步（本项目取 20，上限允许到 100）；
- **合并**：连续的同类输入合并成一步；
- **落盘**：超出内存上限后写磁盘；
- **压缩**：相邻快照做差量存储。

原项目一个都没有。

---

#### P9：`popMemento` 与 `restore` 不是原子操作

**探针代码：**

```java
Document doc = new Document("A");
History h = new History();
h.pushMemento(doc.save());
doc.setContent("B"); h.pushMemento(doc.save());
doc.setContent("C"); h.pushMemento(doc.save());

DocumentMemento target = h.popMemento();   // 返回 B，但 history 已改成 [A]
// ... 假设这里抛了异常 ...
```

**真实输出：**

```text
[P9 popMemento 与 restore 不是原子操作]
  三态入栈，文档 = C
  popMemento() 返回 DocumentMemento[content=B]，但文档仍是 C
  ↑ 错位窗口：history 已认为当前是 B，document 还是 C
  中间层抛出 模拟中间层失败
  失败后文档 = C：document 从未变成 B，
  下一次 popMemento() 直接给 DocumentMemento[content=A] —— B 被跳过
```

**"错位窗口"是这条缺陷的核心名词。**

从 `popMemento()` 返回，到 `restore()` 被调用，中间存在一个时间窗口。
在这个窗口里：

- `History` 的内部状态已经变成"当前是 B"；
- `Document` 的内容还是 C。

如果窗口里发生任何异常（日志组件抛错、审计埋点失败、
线程被中断），`restore` 就不会执行，而 `History` 已经回不去了。

**这不是"理论上可能"，而是必然会发生的**：
任何在 `restore` 前面插入的业务逻辑（比如"记录一次撤销操作"），
都可能是那个抛异常的点。

**修法有两种：**

1. 让 `popMemento` 直接完成恢复 —— 但它拿不到 `Document`；
2. 让 `Caretaker` 持有 `Document`，把"弹出 + 恢复"合并成一个方法。

本项目选的是第 2 种，方法名叫 `undo()`，返回 `boolean`。见 §5.8。

---

#### P10：`History` 不绑定 `Originator`

**探针代码：**

```java
Document d1 = new Document("D1 的内容");
Document d2 = new Document("D2 的内容");
History shared = new History();          // ← 一份历史，两个文档

shared.pushMemento(d1.save());
shared.pushMemento(d2.save());

d2.restore(shared.popMemento());         // 把 d1 的快照装进 d2
```

**真实输出：**

```text
[P10 History 不绑定 Originator：跨文档污染]
  两个不同文档共用一份 History
  d2 撤销一次后 = D1 的内容
  -> 撤销把 d1 的快照装进了 d2
```

**这是十条里后果最严重的一条。**

前九条的后果都是"撤销不好用"、"占内存"、"崩一下"。
这一条的后果是：**文档 B 的内容被文档 A 的历史覆盖了。**

在真实系统里，这意味着：

- 客服正在编辑工单 A 的回复，切到工单 B 去查资料；
- 切回来时，A 的回复变成了 B 的内容；
- 用户点了保存，**错误内容被永久写入**。

而且整个过程**没有任何异常**。

**根因：`History` 的构造器没有参数。**

```java
public class History {
    private final Stack<DocumentMemento> history = new Stack<>();
    // ...
}
```

它不知道自己在为谁服务，所以它能被任何 `Document` 使用；
既然任何人能用，就没有人"拥有"它。

**本项目的修法是让绑定发生在构造期：**

```java
public SnapshotHistory(Editor editor, int maxDepth, Supplier<Instant> clock) {
    this.editor = Objects.requireNonNull(editor, "editor");
    // ...
}
```

一旦绑死，`new SnapshotHistory(a, ...)` 就永远只管 `a`。
想管 `b`，必须再 new 一个 —— 于是"共用一份历史"这件事，
从"可以但会出 bug"变成了"**根本写不出来**"。

**再加一道**：`Editor.restore` 里有归属令牌校验（§5.4），
即使有人绕过 `SnapshotHistory` 直接调 `restore`，也会被拒。

> 探针 P10 对应的场景，在本项目里由 **Demo 5** 复现并验证已被拦住。

---

### 2.4 三条根因

十条缺陷，看起来是十个独立的小毛病。但把它们归类之后，只有三条根因。

#### 根因一：备忘录没有"两种接口"

GoF 原文里，Memento 是一个**有两条通道**的对象：

- 对 Originator：宽接口，能读全部字段；
- 对其他所有人：窄接口，什么也读不到。

原项目用了 `public record`，两条通道合并成了一条 ——
**所有人都走宽接口**。

由这一条根因直接导致的缺陷：

- P4（可伪造）
- P5（组件少且改不动）—— 一半原因在这里

#### 根因二：Caretaker 越权

`History` 本该只做三件事：**存、取、丢**。

它实际做了：
- 在 `pushMemento` 里偷偷清空重做栈（副作用藏在名字后）；
- 在 `popMemento` 里同时弹栈、压重做栈、返回上一个（一个方法三件事）；
- 用 `null` 表达两种业务状态（类型系统帮不上忙）；
- 不绑定 Originator（谁都能用）。

由这一条根因直接导致的缺陷：

- P1（NPE）
- P2（静默脱节）
- P3（null 语义）
- P9（错位窗口）
- P10（跨文档污染）

**十条里有五条出自同一个类。** 这是"职责不清"最典型的量化证据。

#### 根因三：没有边界

- 正文长度无上限 → 内存（间接）
- 撤销深度无上限 → P8
- 入参无校验 → P6
- 归属无校验 → P10
- 容器能力无收敛（`Stack` 继承 `Vector`）→ P7

这三条根因，恰好对应本项目的三次重构：

| 根因 | 本项目怎么修 | 在哪一节 |
|---|---|---|
| 宽窄接口不分 | `Snapshot` 窄接口 + `Editor.Memento` 私有 record + 归属令牌 | §4.1 §4.2 §4.3 |
| Caretaker 越权 | 构造期绑定 Originator；`edit()` 原子化；`undo()` 返回 `boolean` | §4.7 §4.8 §5.8 |
| 没有边界 | `EditorLimits` 集中校验；`maxDepth` 上限；`ArrayDeque` 替代 `Stack` | §4.10 §4.11 §5.6 |

---

### 2.5 一个必须回答的问题：那这 6 行输出算"跑对了吗"？

算。**在原项目设计所划定的那条路径上，它是对的。**

真正需要追问的是：**这条路径有多宽？**

- 如果一个 `History` 只被一个 `Document` 用 —— 那 P10 不会发生；
- 如果调用方每次改完都记得 `pushMemento` —— 那 P2 不会发生；
- 如果用户永远不撤销到最后一步 —— 那 P1 不会发生；
- 如果会话很短、文档很小 —— 那 P8 不会发生。

原项目把"这些条件都成立"当成了**默认前提**，
而这些条件实际上一条都不受代码保护。

> **这就是"教科书示例"和"能上生产的实现"之间的差距。**
>
> 前者只需要在正确路径上跑通；
> 后者需要在**错误路径**上也保持可预期的行为。

本项目接下来的每一节，都在回答同一个问题：
**怎么让错误路径变得"写不出来"，而不是"记得别这么写"。**

---
## 3. 设计动机：为什么必须有三方

### 3.1 三方角色，各管一件"别人管不了"的事

备忘录模式的三方分工，经常被讲成"Originator 创建、Caretaker 保存、Memento 承载"。
这句话没错，但它只是**描述**，不是**理由**。

真正的理由是：这三件事各自有**别人替代不了的约束**。

#### Originator 管"什么算完整状态"

这是**领域知识**，只有被备份的那个对象自己知道。

想一想：如果让 `History` 去决定备份什么，它得这么写：

```java
public DocumentMemento save(Document document) {
    return new DocumentMemento(document.getContent());
}
```

一旦这么写，`History` 就**绑死在了 `Document` 的 getter 列表上**。

- `Document` 加一个字段 → 忘了改 `History` → 这个字段不进快照 → 撤销后丢失；
- `Document` 改一个字段的类型 → `History` 编译失败 → 但这是好事，能改；
- `Document` 把某个字段改成计算属性 → `History` 拿到的是一个瞬时值 → 恢复不回去。

**最坏的情况是第一行**：加了字段，编译器不管，快照静默地变得不完整。

所以"备份什么"这条知识必须放在 `Document` 内部。
这就是 `save()` 和 `restore()` 都写在 Originator 里的原因。

#### Caretaker 管"什么时候拍、拍几张、放多久"

这是**交互策略**，只有调用方（或者说调用方所在的那个层）知道。

- 用户按住 `A` 键不放，是每次 `keydown` 都拍一张，还是等 500ms 后拍一张？
- 用户做了"全选 + 粘贴"，这是一步还是两步？
- 撤销栈留 20 步还是 100 步？
- 用户点了"保存"之后，撤销栈要不要重置？

这些问题的答案会随产品变化，但**它们都不会改变"备份什么"**。

把策略放在 `Caretaker`，意味着换产品策略时不用动 `Originator`。

#### Memento 管"这份数据谁能看"

这是**封装**，是三者里最容易被忽略、也最致命的一条。

`Caretaker` 需要**握住**快照（否则没法保管），
但**不应该读懂**快照（否则它就有了修改历史的能力）。

这两件事在大部分语言里是矛盾的：能拿到对象，一般就能拿到内容。

Java 里解决这个矛盾有几条路，强弱差别很大。下一节详细说。

---

### 3.2 为什么 Originator 必须自己拍快照

有人会问：`save()` 写成这样行不行？

```java
// 在 Caretaker 里
DocumentMemento m = new DocumentMemento(document.getContent(), document.getCursor());
```

功能上"能跑"。但它有三个问题，前两个已经开始讲了（知识散落、加字段漏改）。
第三个是**最要命的**：

#### 它需要 Originator 暴露全部字段

为了让 `Caretaker` 能拼出一个完整的快照，
`Document` 必须把所有字段都开成 `public` 或者加 getter：

```java
public String getContent();
public int getCursor();
public TextRegion getSelection();
public List<String> getSpellCheckIgnores();   // 万一有呢？
// ...
```

**于是"备忘录"这个模式不但没有保护封装，反而成了破坏封装的借口。**

原本 `Document` 可以把 `cursor` 藏在内部，
现在为了能被备份，不得不把它露出来。

> **判断一个实现是不是真的在用备忘录模式，有一个很硬的检验：**
> **它有没有为了"能备份"而多开了 getter？**
> 如果多了，那它做的是"外部快照"，不是备忘录模式。

本项目的 `Editor` 确实有 `text()` / `cursor()` / `selection()` 三个 getter。
但要注意：**`snapshot()` 并不依赖它们**。

```java
public Snapshot snapshot(SnapshotMetadata metadata) {
    Objects.requireNonNull(metadata, "metadata");
    return new Memento(text, cursor, selection, metadata, ownerToken);
}
```

它直接读字段（`this.text`），不走 getter。
getter 是给 **Demo 打印**用的，不是给快照用的。

这个区别看起来很小，但它决定了：如果把 getter 全删掉，`snapshot()` 照样能工作。

---

### 3.3 为什么 Caretaker 不能看见内容

这是整个模式的核心。用一张图说清"两种接口"：

```text
                     ┌──────────────────────────────┐
                     │        Editor                │
                     │  ┌────────────────────────┐  │
   宽接口 ──────────►│  │ Memento                │  │
   （只有 Editor     │  │  text / cursor /       │  │
    自己能用）       │  │  selection / owner     │  │
                     │  └────────────────────────┘  │
                     │         ▲                    │
                     │         │ implements         │
                     │  ┌──────┴─────────────────┐  │
   窄接口 ──────────►│  │ Snapshot               │  │
   （Caretaker 和    │  │  metadata()  ← 仅此一个 │  │
    全世界都能用）   │  └────────────────────────┘  │
                     └──────────────────────────────┘
```

**两条通道，一条宽一条窄。** 这就是 GoF 所说的：

> Two interfaces: a wide one for the originator, a narrow one for the caretaker.

#### Java 里能做到"窄"的三种写法

**写法 A：`public` 接口 + `private` 嵌套实现类**（本项目用的）

```java
public interface Snapshot {                 // 公开，但只有一个方法
    SnapshotMetadata metadata();
}

public final class Editor {
    private record Memento(...) implements Snapshot { }   // 私有，外部不可见

    public Snapshot snapshot(SnapshotMetadata m) { return new Memento(...); }
    public void restore(Snapshot s) {
        if (!(s instanceof Memento m)) { throw ... }      // 内部可见
    }
}
```

- **强的点**：外部代码**写不出** `new Memento(...)`，也**写不出** `instanceof Memento`。
  编译器直接拦住。
- **弱的点**：所有 `Editor` 实例共享同一个 `Memento` 类型，
  所以 `instanceof` 只能证明"是某个 Editor 造的"，不能证明"是我造的"。
  需要额外加归属令牌（§4.3）。

**写法 B：同包可见（package-private）**

```java
class Memento implements Snapshot { ... }   // 无修饰符，同包可见
```

- **强的点**：跨包不可见，跨包代码造不出来。
- **弱的点**：**同包的任何类都能造**。对于一个包里有 20 个类的项目，等于没防。
- **什么时候够用**：包只有一个 `Editor` 和一个 `SnapshotHistory`，
  且你有把握这个包不会再长大。现实中很少有这种把握。

**写法 C：序列化 / 深拷贝**

```java
byte[] snapshot = objectToBytes(document);   // 存字节
document = bytesToObject(snapshot);          // 恢复
```

- **强的点**：字节数组天然不透明，谁也读不出结构（除非反序列化）。
- **弱的点**：依赖所有字段可序列化；性能差；**版本兼容噩梦**
  （今天加一个字段，昨天存的快照全部反序列化失败）。
- **什么时候够用**：跨进程、跨机器的持久化场景。
  这也是"原型模式"常用的手法 —— 但那已经不是备忘录模式了（§3.5）。

#### 一句话总结这三种写法

| 写法 | 谁能造 | 谁能读 | 编译期能拦住吗 |
|---|---|---|---|
| **A：私有嵌套类** | 只有 Originator | 只有 Originator | ✅ 能 |
| B：同包可见 | 同包所有类 | 同包所有类 | ⚠️ 部分 |
| C：序列化 | 能序列化就能造 | 能反序列化就能读 | ❌ 不能 |
| **原项目：`public record`** | **所有人** | **所有人** | ❌ 不能 |

原项目落在最后一行：**既没拦住造，也没拦住读。**

---

### 3.4 什么时候不需要备忘录

这是本项目每一篇文档都有的小节。因为**过度设计的代价，往往比不设计更大**。

#### 情况一：只需要"恢复默认值"

如果"撤销"的含义只是"清空重来"，那不需要快照：

```java
public void reset() {
    this.text = "";
    this.cursor = 0;
}
```

**一个方法就够。** 加三个类、建一套栈，是在给自己找维护成本。

#### 情况二：操作天然可逆，且只需要一步

如果需求是"上一步操作可以反悔"，而不是"可以退 20 步"，那就用命令模式：

```java
interface Command { void execute(); void undo(); }
```

一步撤销不需要"Caretaker + 历史栈"，一个 `Command` 变量就够了。

#### 情况三：状态只有一个值，且暴露出去无所谓

如果一个对象只有一个 `int status` 字段，而且外部读走它不会造成任何后果，
那直接 `int prev = obj.status` 就行。

**备忘录模式的全部价值在"藏"，藏不住就没有价值。**

#### 情况四：真正需要的是"版本管理"

如果需求是"能回到上周三的版本"、"能对比两个版本的差异"、"能分支"，
那这是**版本控制系统**要解决的问题，不是备忘录模式。

备忘录模式管的是**一次会话内的、内存里的、线性的**历史。

线性的意思：撤销之后做新编辑，重做就作废（§4.7 会讲为什么）。
如果你的产品需要"分支历史"（比如 git 的 branch），
那就需要一个树形结构 + 持久化，那是另一个量级的工程。

#### 一张判断表

| 你的需求 | 该用什么 |
|---|---|
| 回到默认状态 | 一个 `reset()` 方法 |
| 撤销上一步 | 命令模式（`undo()`） |
| 撤销最近 N 步，状态小 | **备忘录模式** |
| 撤销最近 N 步，状态巨大 | 备忘录 + 压缩 / 差量 / 落盘 |
| 跨会话、跨机器恢复 | 持久化（数据库 / 文件 / 事件日志） |
| 分支历史、版本对比 | 版本控制系统 |

---

### 3.5 备忘录 vs 命令 vs 原型：三个容易混的边界

这三个模式都能"回到过去"或者"复制一份"，经常被搞混。

#### 一句话区分

| 模式 | 它存的是 | "回到过去"靠什么 | 谁发起 |
|---|---|---|---|
| **备忘录** | **状态** | 覆盖 | Caretaker |
| **命令** | **操作** | 反向执行 | Invoker |
| **原型** | **对象模板** | 不涉及 | Client |

#### 用一个例子说清

假设你在做一个画图软件，画了一个圆。

**备忘录模式**：撤销时，把"画布上有什么"整个换回上个版本。
- 存的是一张图（或它的完整对象图）；
- 不管中间发生了什么，恢复一定是准的；
- 画布越大，存得越多。

**命令模式**：撤销时，执行"删除那个圆"。
- 存的是"画一个圆，id=7，半径=30"；
- 存储小，但"画一个圆"的逆运算得写对；
- 如果圆和别的图形做了布尔运算，逆运算基本写不出来。

**原型模式**：不是撤销，是"复制这个圆，变成三个"。
- 存的是一个可复制的模板；
- 用于"创建"场景，不是"恢复"场景。

#### 本项目里三者的实际位置

| 模式 | 在 `design_patterns_pro` 里的位置 | 一句话 |
|---|---|---|
| 备忘录 | `behavioral/memento` | 客服回复草稿的撤销栈 |
| 命令 | `behavioral/command` | 任务调度器的 `undo()` |
| 原型 | `creationtype/PrototypePattern` | 文档模板克隆 |

**一个常被问的面试题：**

> "既然命令模式也能撤销，为什么还需要备忘录模式？"

标准答案有两层：

1. **正确性**：命令模式的撤销依赖"每个操作都有正确的逆运算"。
   只要有一个逆运算写错，撤销就把数据改坏了。
   备忘录模式不依赖逆运算，快照完整就一定对。
2. **适用面**：有些操作**根本没有逆运算**。
   "把字体从宋体换成微软雅黑"的逆运算是什么？
   如果文档里原本混排了两种字体，你得记住每一段的原字体 ——
   那已经是在存状态了，只是存得很别扭。

**记住这句话：**

> 当你发现"逆运算里开始记状态"的时候，说明你真正需要的是快照。

#### 还有一个容易混的：备忘录 vs 数据库事务回滚

数据库的 `ROLLBACK` 看起来也是"回到过去"。

但事务回滚靠的是 **undo log**（记录"改之前的值"），
本质上是**每条记录的反向操作**，不是整个状态的快照。

它们的差别在于粒度：

- 事务回滚按**行**记，几十亿行也只记被改的那些；
- 备忘录按**对象**记，一次存一整个。

对于文档编辑器这种"一个对象、几十 KB"的场景，备忘录更简单；
对于数据库这种"海量行、改几行"的场景，undo log 才可行。

**选哪个，取决于"状态的总量"和"改动的比例"。**

---

### 3.6 本项目为什么不用 `Optional` 补救 `null`

P1/P3 的直觉修法是：

```java
public Optional<DocumentMemento> popMemento() { ... }
```

这样调用方**必须**处理"没有"的情况，NPE 从源头消失。

但本项目选择了另一条路：**让这个方法根本不存在**。

理由是这样的：

`popMemento()` 这个名字暗示了两件事：
1. 从栈里取出一个东西；
2. 这个动作和"文档恢复"是分开的。

而**这两件事分开，本身就是 P2 和 P9 的根源**。

- 分开了 → 调用方可能只做 1 不做 2 → P2；
- 分开了 → 中间有个时间窗口 → P9。

用 `Optional` 只能让"拿到 null 不崩"，**不能让"少了那一步"变不可能**。

所以本项目的 `undo()`：

```java
public boolean undo() {
    if (undo.size() <= 1) {
        return false;
    }
    Snapshot target = undo.pop();
    redo.push(capture(EditKind.UNDO, ...));
    editor.restore(target);        // ← 在同一个方法里完成
    notifyChanged();
    return true;
}
```

- **弹出和恢复在同一个方法里**，中间没有任何可插入异常的位置（除了 `capture`，
  它只做对象分配，失败的概率可以忽略且会抛出）；
- **返回 `boolean`**，只有两种结果：撤了 / 没撤成。没有第三种。
- **没有 `null`**，也就没有 `Optional` 的必要。

> 修 bug 有三个层次：
> 1. **加判断**（判 null）—— 治症状；
> 2. **换类型**（`Optional`）—— 让症状写不出来；
> 3. **改结构**（合并方法）—— 让症状的**成因**消失。
>
> 本项目尽量做第 3 层，做不到时才做第 2 层。

---
## 4. 设计推导：十三个决定

这一章逐个回答"为什么写成这样"。

每一条都按同一个格式：**问题 → 可选方案 → 选择理由 → 代价**。

十三个决定不是并列的，它们分成四组：

```text
封装组  4.1  窄接口只有一个方法
        4.2  Memento 是 private record
        4.3  还要再加 ownerToken

内容组  4.4  快照要带元信息
        4.5  用枚举不用字符串
        4.6  选区做成 TextRegion

时机组  4.7  edit() 包住一段操作
        4.8  undo() 返回 boolean
        4.9  构造器里就拍基线

边界组  4.10 ArrayDeque 替代 Stack
        4.11 容量上限是必须的
        4.12 时钟要注入
        4.13 要有监听器
```

---

### 4.1 为什么 `Snapshot` 只有一个方法

```java
public interface Snapshot {
    SnapshotMetadata metadata();
}
```

#### 问题

Caretaker 需要"握住"快照。握住之后，它能看到什么？

#### 可选方案

**方案一：把字段全开出来**

```java
public interface Snapshot {
    String text();
    int cursor();
    TextRegion selection();
    SnapshotMetadata metadata();
}
```

**方案二：只开元信息**

```java
public interface Snapshot {
    SnapshotMetadata metadata();
}
```

**方案三：一个方法都不开（纯标记接口）**

```java
public interface Snapshot { }
```

#### 选择理由

**方案一被否掉，因为它让备忘录失去存在意义。**

如果 `Caretaker` 能读 `text()`，那它就能：

```java
// 在 SnapshotHistory.undo() 里
System.out.println("撤销前的内容是：" + target.text());   // ← 内容泄露到日志
```

一行日志，就把用户没提交的草稿写进了日志文件。
更严重的是，一旦接口上有这个方法，**所有下游代码都会开始用它** ——
"反正能拿到，我读一下做埋点吧"。

接口是承诺。开出去就收不回来了。

**方案三被否掉，因为 Demo 需要显示"第几步、什么操作"。**

如果 `Snapshot` 是纯标记接口，撤销面板只能显示：

```text
[版本 1] [版本 2] [版本 3]
```

用户完全不知道自己该点几下。

**方案二是一个精确的切分：**

- 元信息（编号、类型、时间）—— **本来就该给用户看的**，不算泄露；
- 内容（正文、光标、选区）—— **不该给任何人看**，只有 `Editor` 能读。

一条判据：

> **如果这个信息会显示在界面上，它就该在窄接口上；
> 如果它只用于"恢复状态"，它就不该出现在任何接口上。**

`Sequence` / `Kind` / `CapturedAt` 会显示在撤销面板上 → 放窄接口。
`text` / `cursor` / `selection` 只用于恢复 → 放 `Memento` 里，接口不暴露。

#### 代价

- 需要额外一个 `SnapshotMetadata` 类（§4.4）；
- 界面上想显示"这段快照的正文前 20 个字"，做不到 ——
  要做的话，得在 `SnapshotMetadata` 里加一个**由 Originator 生成好的摘要字段**，
  而不是让 UI 去读原文。这是一个有意的限制。

---

### 4.2 为什么 `Memento` 是 `private record`

```java
public final class Editor {
    // ...
    private record Memento(String text, int cursor, TextRegion selection,
                           SnapshotMetadata metadata, Object owner)
            implements Snapshot {
        @Override
        public String toString() {
            return "Memento{" + metadata.display() + "}";
        }
    }
}
```

#### 问题

怎么保证"只有 `Editor` 能造、能读这份数据"？

#### 可选方案

| 方案 | 外部能 new 吗 | 外部能读字段吗 | 编译器能拦住吗 |
|---|---|---|---|
| `public record`（原项目） | 能 | 能 | 不能 |
| `public final class` + 私有构造 | 不能 | **能**（通过 getter） | 部分 |
| package-private class | 同包能 | 同包能 | 部分 |
| **`private` 嵌套 `record`** | **不能** | **不能** | **能** |

#### 选择理由

**`private record` 有一个独特的性质：外部代码连这个"名字"都写不出来。**

```java
// 在 Editor 之外的任何地方：
Snapshot s = editor.snapshot(meta);   // 只能拿到 Snapshot 类型
s instanceof Editor.Memento           // ❌ 编译错误：Memento 不可见
new Editor.Memento(...)               // ❌ 编译错误：构造器不可见
s.text()                              // ❌ 编译错误：Snapshot 没这个方法
```

**三种常见攻击全部编译失败。** 这是所有方案里唯一做到的。

对比 `public final class Memento` + 私有构造：
外部确实 `new` 不了，但**只要有 getter，就能读**；
没有 getter 也想读的话，反射 `setAccessible` 依然能读。

所以"私有嵌套"不是为了防反射（防不住），
而是为了**让正常代码在编译期就写错不了**。

> 防反射不是目标。**让 99% 的开发者永远不会不小心写错**，才是目标。

**为什么用 `record` 而不是普通 `final class`？**

因为快照的语义恰好就是"一组不可变的值"：

- 没有行为（除了 `toString`）；
- 不需要继承；
- 不需要延迟计算；
- 一旦构造完成，永远不变。

`record` 把这些性质写进了**类型定义**里。读代码的人看到 `record` 三个字，
就知道这个对象不会变、不需要防御性拷贝、可以安全地在线程间传递。

如果写成普通类，你得自己写 `final` 字段、构造器、getter，
然后在 javadoc 里补一句"本类不可变" —— 而**没人会去验证这句话**。

**为什么要重写 `toString()`？**

```java
private record Memento(...) implements Snapshot {
    @Override
    public String toString() {
        return "Memento{" + metadata.display() + "}";
    }
}
```

`record` 自动生成的 `toString()` 会把**所有组件**打出来，包括 `text`。

于是一行 `System.out.println(memento)` 就能把整篇草稿写进日志。

**默认行为在这里是有害的，必须显式覆盖。**

这一点很容易被忽略：大家用 `record` 图省事，
然后在一个 `log.debug("snapshot = {}", snapshot)` 里把用户数据泄露出去。

#### 代价

- `Memento` 不能独立成文件，只能跟着 `Editor` 走（这是好事，见 P5）；
- 单测不能直接构造 `Memento`，必须通过 `Editor.snapshot()` ——
  这是**故意的**，测试也应该走正常路径；
- 反射仍可绕过（`Class.forName("...Editor$Memento")`）。
  Demo 5 就演示了这一点 —— **承认它，而不是假装防住了**。

---

### 4.3 为什么还要再加一个 `ownerToken`

这是本项目的**实现过程中真被写出来的一个 bug**，值得单独一节。

#### 问题

`Memento` 是 `Editor` 的嵌套类之后，"是 Editor 拍的"已经能保证了。
**但"是哪个 Editor 拍的"保证不了。**

第一版 `restore` 是这样写的：

```java
public void restore(Snapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    if (!(snapshot instanceof Memento memento)) {     // ← 只检查了类型
        throw new SnapshotRejectedException(...);
    }
    this.text = memento.text();
    // ...
}
```

跑 Demo 5 的时候，输出是这样的：

```text
  a 自己拍一张 → #999 输入 · 手工拍的 · 09:00:00.000
  a.restore(自己的) 成功 → 「文档 A」
  b.restore(a 的) 竟然成功（不该发生）        ← ← ←
```

**`b.restore(a 的快照)` 没有抛异常。**

原因是：`Memento` 是**类**，不是**实例**。
`a` 和 `b` 都是 `Editor`，它们共享同一个 `Editor.Memento` 类型。
`instanceof` 只回答"这个对象是不是 Memento"，不回答"是不是 a 的 Memento"。

**这就是原项目探针 P10 的同一个坑，换了个位置重新出现。**

#### 修法

给每个 `Editor` 实例一个独一无二的令牌：

```java
private final Object ownerToken = new Object();
```

拍快照时把它塞进去：

```java
return new Memento(text, cursor, selection, metadata, ownerToken);
```

恢复时比对**引用相等**：

```java
if (!(snapshot instanceof Memento memento) || memento.owner() != ownerToken) {
    throw new SnapshotRejectedException(...);
}
```

修完之后的真实输出（Demo 5）：

```text
  a 自己拍一张 → #999 输入 · 手工拍的 · 09:00:00.000
  a.restore(自己的) 成功 → 「文档 A」
  b.restore(a 的) 被拒 → 拒绝恢复：#999 输入 · 手工拍的 · 09:00:00.000 不是本编辑器产生的快照
```

#### 为什么用 `new Object()` 而不是 `this`？

这是本节最重要的一个细节。

写 `Memento(..., Editor owner)` 更直观，但它会带来一个**内存问题**：

```text
  SnapshotHistory  ──holds──►  Memento  ──holds──►  Editor
       ▲                                              │
       └──────────────────────────────────────────────┘
                      Editor 也持有 History？
```

在真实场景里，`Editor` 通常会持有 `SnapshotHistory`（因为 UI 面板需要它）。
于是形成环：`Editor → History → Memento → Editor`。

**Java 的 GC 能处理循环引用，所以这不是"内存泄漏"。**
真正的问题是**生命周期被拉长了**：

- 用户关掉了一个文档窗口，`Editor` 应该被回收；
- 但只要某个地方还握着一条 `Snapshot`（比如一个异步的日志任务），
  这条 `Snapshot → Editor` 的引用就会把整个编辑器（连同它的全文）钉在内存里。

用 `Object` 令牌就没有这个问题：令牌是一个 16 字节的空对象，
它不引用任何东西。

```text
  SnapshotHistory  ──holds──►  Memento  ──holds──►  Object(令牌)
                                                    ▲
  Editor  ─────────────────────────────────────────┘
```

编辑器被回收时，令牌可能还被快照持有 —— 但那只是一个空对象，无所谓。

#### 代价

- `Memento` 多一个组件（16 字节/张）；
- 每个 `Editor` 多一个字段（16 字节）；
- 令牌的语义需要注释说明，否则后来的人会想"为什么不直接用 this"。

#### 一条可以带走的经验

> **用了嵌套类来限制可见性之后，别以为就完事了。**
> `instanceof` 检查的是**类型**，不是**实例**。
> 只要一个类有多个实例，就必须额外回答"是哪个实例的"。

这条经验适用于所有"资源归属"场景：
连接池里的连接、缓存里的条目、临时文件、事务上下文 ——
类型对了，不等于归属对了。

---

### 4.4 为什么快照要带元信息

```java
public record SnapshotMetadata(int sequence, EditKind kind, String detail, Instant capturedAt) { }
```

#### 问题

`Snapshot` 只有一个 `metadata()` 方法。这个 `metadata()` 里该放什么？

#### 可选方案

**方案一：什么都不放，`Snapshot` 是纯标记接口**

撤销面板只能显示"版本 1/2/3"。

**方案二：放一个字符串标签**

```java
public interface Snapshot {
    String label();
}
```

简单，但**信息量不可再分**：想按类型过滤（"只看输入类编辑"）、
想按时间排序（"撤销到 10 分钟前"），都得去解析字符串。

**方案三：结构化的元信息（本项目）**

```java
public record SnapshotMetadata(int sequence, EditKind kind, String detail, Instant capturedAt) { }
```

#### 选择理由

四个字段，每个都对应一个真实的 UI 需求：

| 字段 | 对应需求 | 缺了会怎样 |
|---|---|---|
| `sequence` | "这是第几步" | 多次撤销后，用户不知道自己退到了哪 |
| `kind` | 图标 / 分类 | 所有步骤长得一样 |
| `detail` | 悬停提示 | 只知道"输入"，不知道"输入了什么" |
| `capturedAt` | "多久之前" | 无法实现"撤销到 5 分钟前" |

**为什么 `sequence` 不用 `long` 而用 `int`？**

一次会话内的撤销步数，上限是 `MAX_UNDO_DEPTH = 100`。
`int` 能装 21 亿。用 `long` 是浪费 —— 这是**范围适配**，不是省字节。

（如果 `sequence` 是全局的、跨会话的，那就该用 `long`。
本项目不是。）

**为什么 `capturedAt` 用 `Instant` 而不是 `long`？**

```java
long t = 1742000000000L;      // 这是什么时候？
Instant t = Instant.parse("2025-03-14T09:00:00Z");   // 一眼能读
```

`long` 存毫秒有三个问题：
1. 读不懂（调试时最痛）；
2. 单位靠约定（秒还是毫秒？总有人搞错）；
3. 时区信息丢失（`Instant` 是 UTC 绝对时刻，格式化时才决定时区）。

#### 为什么元信息里**不**放正文摘要

一个很自然的想法：撤销面板显示"客服：您好，您的订单已发…" 会更友好。

但那样 `SnapshotMetadata` 就得持有正文片段，
而 `SnapshotMetadata` 是**公开**的（`Snapshot.metadata()` 返回它）。

**于是正文通过元信息泄露了。**

正确的做法是：由 `Editor` 在拍快照时**生成一个安全的摘要**
（比如"正文 21 字，以「客服」开头"），放进 `detail`。
本项目没有做这件事，因为会引入"摘要怎么脱敏"这个新问题 ——
留作扩展题。

#### 代价

- 多一个文件、多一层间接；
- UI 想加字段时，要改 `SnapshotMetadata` + `SnapshotHistory.capture()`。
  但这两个改动是**局部的**，不会波及 `Editor`。

---

### 4.5 为什么用 `EditKind` 枚举而不是字符串

```java
public enum EditKind {
    BASELINE("基线"), TYPING("输入"), DELETION("删除"),
    SELECTION("选区"), REPLACE("整段替换"), UNDO("撤销"), REDO("重做");
}
```

#### 问题

"这次编辑是什么类型"用什么类型表达？

#### 可选方案

**方案一：`String`**

```java
history.edit("输入", "补一句", e -> e.type("..."));
```

**方案二：`enum`（本项目）**

```java
history.edit(EditKind.TYPING, "补一句", e -> e.type("..."));
```

#### 选择理由

**字符串的问题在于"拼错不报错"：**

```java
history.edit("输入", ...);      // 中文
history.edit("typing", ...);    // 英文 —— 编译通过，运行时是两种类型
history.edit("輸入", ...);      // 繁体 —— 编译通过
history.edit("输入 ", ...);     // 多个空格 —— 编译通过
```

如果界面上要按类型过滤，这四种会被当成四种不同的类型。

**枚举把"拼错"变成编译错误。** 而且：

- IDE 能自动补全；
- `switch` 能穷尽检查（加一个新类型，所有 `switch` 都报错）；
- `values()` 能自动生成下拉框的选项。

**为什么要有 `BASELINE` 这个值？**

因为"打开编辑器时的初始状态"是**唯一一个没有前一次编辑的快照**。

如果不给它一个枚举值，模型上就得允许 `kind == null`：

```java
String label = meta.kind() == null ? "基线" : meta.kind().label();
```

这个判空会传染到**每一个消费方**：撤销面板要判、日志要判、
统计要判、导出要判。

给它一个真实的枚举值，`null` 就从类型系统里消失了。

> 这和本项目在**状态模式**那一章用 `PENDING` 表示"订单刚创建"、
> 在**观察者模式**那一章用 `NONE` 表示"还没有事件"是同一个手法：
> **用一个真实的值代替 null。**

#### 代价

- 加一种编辑类型要改枚举（一行）；
- 枚举值和 UI 文案绑定在同一个文件里。
  如果要做国际化，得把 `label` 换成资源 key —— 但那是一个更大的重构，
  且和备忘录模式本身无关。

---

### 4.6 为什么选区是 `TextRegion` 而不是两个 `int`

```java
public record TextRegion(int start, int end) {
    public TextRegion {
        if (start < 0) { throw ... }
        if (end < start) { throw ... }
    }
    public static TextRegion caret(int position) { return new TextRegion(position, position); }
    public int length() { return end - start; }
    public boolean isEmpty() { return start == end; }
}
```

#### 问题

光标和选区，用什么表示？

#### 可选方案

**方案一：两个 `int`**

```java
private int selectionStart;
private int selectionEnd;

public void select(int start, int end);
public Snapshot snapshot(String text, int cursor, int start, int end);
```

**方案二：`TextRegion` record（本项目）**

```java
private TextRegion selection;
public void select(TextRegion region);
```

#### 选择理由

**方案一有三个具体的问题：**

**（1）参数顺序可以传反**

```java
editor.select(8, 3);      // 编译通过，运行时才可能炸
```

`select(int, int)` 的签名对调用方没有任何提示。
IDE 显示的是 `select(start: int, end: int)`，但很多人根本不看参数名。

**（2）校验要写很多遍**

`start >= 0`、`end >= start`、`end <= text.length()` 这三条规则，
在一个有 `select` / `snapshot` / `restore` / `setSelection` 四个方法的类里，
至少要写四遍。

写四遍就意味着**总有一遍会漏**，而漏的那一遍就是 bug 的入口。

**（3）"不含选区"没法表达**

只能靠约定"`start == end` 就是没选"。
约定不在类型里，就没法被检查。

**`TextRegion` 把这三个问题一次性解决：**

```java
public TextRegion {                    // 紧凑构造器：校验只有这一处
    if (start < 0) { throw ... }
    if (end < start) { throw ... }
}
```

任何拿到 `TextRegion` 的代码，都可以**假定它合法**。
这是"值对象"最重要的性质：**合法状态在构造期建立，之后永不失效**。

`TextRegion.caret(4)` 比 `new TextRegion(4, 4)` 更清楚地表达了意图：
"这里只有一个光标"。

#### 为什么是半开区间 `[start, end)`

与 `String.substring(int, int)` 一致。好处是：

- `length()` 就是 `end - start`，不需要 `+1`；
- 空区间就是 `start == end`，不需要特殊的哨兵值；
- 相邻区间首尾相接：`[0,3)` 和 `[3,5)` 拼起来正好是 `[0,5)`。

如果用的是**闭区间** `[start, end]`：

- 空区间得表示成 `[3, 2]`（end < start）—— 那就没法做 `end >= start` 的校验了；
- 拼接时要 `+1`，边界处极易写错。

**半开区间是所有"区间"问题的标准答案**，`substring`、`List.subList`、
`Arrays.copyOfRange`、`ByteBuffer.limit` 全都是这个约定。

#### 代价

- 多一个对象分配（每次 `caret()` 都 `new` 一个）。
  在文本编辑这种场景下完全可忽略（一秒最多几十次）；
- 多一个文件。

但对"每帧都要更新选区"的场景（比如自定义渲染的光标动画），
频繁 `new` 可能有 GC 压力。那种场景下可以改成两个 `int` + 一个校验方法。

**本项目的判断：`Editor` 的选区变化是用户操作级别的（每秒几次），
用值对象完全划算。**

---

### 4.7 为什么 `edit()` 包住一段操作

```java
public void edit(EditKind kind, String detail, Consumer<Editor> action) {
    Snapshot before = capture(kind, detail);
    try {
        action.accept(editor);
    } catch (RuntimeException e) {
        editor.restore(before);      // 失败回滚
        throw e;
    }
    undo.push(before);
    trim();
    redo.clear();
    recordedEdits++;
    notifyChanged();
}
```

#### 问题

用户按一下 `Ctrl+V` 粘贴 100 个字，这是一步撤销，还是 100 步？

#### 可选方案

**方案一：由 `Editor` 自己每次变更前拍快照**

```java
public void type(String chunk) {
    history.push(snapshot());     // ← Editor 得知道 History 存在
    this.text = text + chunk;
}
```

**方案二：由调用方手动拍（原项目）**

```java
document.setContent("第一次编辑");
history.pushMemento(document.save());     // ← 漏写就丢一步
```

**方案三：由 `Caretaker.edit()` 把一段操作包起来（本项目）**

```java
history.edit(EditKind.TYPING, "粘贴 100 个字", e -> e.type(一百个字));
```

#### 选择理由

**方案一被否掉，因为它让 `Editor` 依赖 `SnapshotHistory`。**

```text
  Editor ──depends──► SnapshotHistory ──depends──► Editor
     ▲                                                  │
     └──────────────────────────────────────────────────┘
```

双向依赖。后果是：

- `Editor` 不能脱离历史单独存在（单元测试都得 new 一个 History）；
- `History` 的构造器需要 `Editor`，`Editor` 的构造器需要 `History` —— **循环构造**；
- 撤销的时机被钉死在 `type()` 里，没法按业务需求变化
  （比如"按住键连续输入 500ms 合并成一步"，方案一做不到）。

**方案二被否掉，它就是原项目，会出 P2/P9。**

**方案三是唯一同时满足三件事的：**

1. **不产生反向依赖**：`Editor` 完全不知道 `SnapshotHistory` 存在。
   依赖方向是 `History → Editor`，单向往。
2. **边界由调用方定**：一段操作里的 `type()` 调了 1 次还是 100 次，
   快照都只拍一张。**快照的粒度 = `edit()` 的调用粒度**。
3. **失败自动回滚**：`action` 抛异常时，`catch` 里 `restore(before)`，
   且**不 push** —— 这张快照只是"用后即弃的草稿"，不入栈。

#### 关键细节：为什么 `redo.clear()` 在 `action` 之后？

```java
undo.push(before);
trim();
redo.clear();          // ← 注意位置：在 action 成功之后
recordedEdits++;
```

如果在 `action` 之前清空，那么"编辑失败"也会把用户的重做能力清掉 ——
用户什么都没改成，却发现自己不能重做了。

**副作用应该只发生在操作成功之后。** 这是一个通用的原则。

对比原项目的写法：

```java
public void pushMemento(DocumentMemento memento) {
    history.push(memento);
    redoStack.clear();     // ← 只要调用 push 就清空
}
```

它连"这次 push 是不是对应一次真的编辑"都不看。

#### 代价

- 调用方必须把操作写成 lambda。看起来很"函数式"，对不熟悉的人有学习成本；
- `action` 里如果包含**非法的** `Editor` 操作，`restore` 会把它退回去，
  但如果 `action` 里还有**外部副作用**（比如调了发邮件的接口），
  那部分**不会被回滚**。

**最后这条必须说清楚：**

> `edit()` 保证的是 **`Editor` 的状态**原子性，
> **不是**整段业务逻辑的原子性。
>
> 如果 `action` 里要调外部系统，请把外部调用放到 `edit()` 外面，
> 或者用真正的分布式事务。

这是一个很容易被误用的点，本项目在 §8.3 的"常见误区"里会再强调一次。

---

### 4.8 为什么 `undo()` 返回 `boolean`

```java
public boolean undo() {
    if (undo.size() <= 1) {
        return false;          // 到底了
    }
    Snapshot target = undo.pop();
    redo.push(capture(EditKind.UNDO, "准备回到 #" + target.metadata().sequence()));
    editor.restore(target);
    notifyChanged();
    return true;               // 撤了
}
```

#### 问题

`undo()` 该返回什么？

#### 可选方案

**方案一：返回 `Snapshot`（原项目的 `popMemento`）**

```java
public DocumentMemento popMemento() { ... return history.peek(); }
```

**方案二：返回 `Optional<Snapshot>`**

**方案三：返回 `boolean`（本项目）**

#### 选择理由

**方案一的致命缺陷：它把"恢复"这一步推出去了。**

```java
document.restore(history.popMemento());     // 正确
history.popMemento();                        // 漏了 restore —— 静默出错（P2）
```

而且返回值可能是 `null`，直接传进 `restore` 就 NPE（P1）。

**方案二解决了 null 问题，但没解决"分两步"问题。**

```java
Optional<Snapshot> s = history.pop();
s.ifPresent(document::restore);      // 看起来很好
```

但依然可能这样写：

```java
history.pop();    // 返回值被忽略 —— 编译通过，静默出错
```

**`Optional` 的返回值是可以被忽略的**（不会有编译警告），
所以 P2 依然存在。

**方案三把两件事合并成一个动作：**

```java
boolean ok = history.undo();    // 弹栈 + 恢复，一次完成
```

`boolean` 只回答一个问题：**"撤销发生了吗？"**

- `true` → 发生了，`editor` 现在是上一个状态；
- `false` → 没发生，`editor` 一点没变。

**没有第三种可能，也没有 `null`。**

至于"撤销到了什么状态"，那是 `editor.text()` 的事 ——
`undo()` 不需要也不应该回答。

#### 为什么不返回 `HistoryStats` 或一个结果对象？

因为 `boolean` 是最小的信息量，而**信息量越小，调用方越不容易误用**。

需要更多信息时，下标已经有 `stats()` 了：

```java
if (!history.undo()) {
    System.out.println("没有更多可撤销：" + history.stats().summary());
}
```

#### 一条通用原则

> **当一个方法同时要做两件事时，与其返回一个"能回答两个问题"的复杂类型，
> 不如把两件事合并，然后返回一个只回答一个问题的简单类型。**

原项目 `popMemento` 想同时回答"撤到哪了"和"有没有撤成"，
结果两个问题都没回答好。

#### 代价

- 调用方想拿到"撤销到的那个快照"时，做不到。
  但**这个需求本身可疑** —— 为什么要拿快照？想读内容？
  那就违反窄接口了。想再恢复一次？那就调两次 `undo()`。

---

### 4.9 为什么构造器里就拍基线

```java
public SnapshotHistory(Editor editor, int maxDepth, Supplier<Instant> clock) {
    this.editor = Objects.requireNonNull(editor, "editor");
    this.clock = Objects.requireNonNull(clock, "clock");
    // ... 校验 maxDepth ...
    this.maxDepth = maxDepth;
    this.undo.push(capture(EditKind.BASELINE, "打开编辑器，正文 " + editor.length() + " 字"));
}
```

#### 问题

撤销栈的栈底，应该放什么？

#### 可选方案

**方案一：留空，等用户第一次编辑时再拍**

**方案二：提供一个 `markInitial()` 方法，让调用方调**

**方案三：构造器里自动拍（本项目）**

#### 选择理由

**方案一的问题：栈底的语义会变。**

如果栈里只有编辑前的快照，那 `undo.size() == 0` 和 `undo.size() == 1`
的含义就不一样了：

- `size == 1`：只有一步可撤；
- `size == 0`：没有可撤的。

而 `undo()` 的判断要写成 `size == 0 ? false : ...`，
然后 `pop` 之后栈空了，再想判断"是不是到底了"就得用另一个变量记。

**有了基线，判断统一成一句话：`size <= 1` 就是到底了。**

```java
if (undo.size() <= 1) {
    return false;
}
```

**方案二的问题是"忘了调"。**

忘了调的后果非常隐蔽：`undo()` 第一次就返回 `false`，
用户以为撤销坏了，但实际上只是栈底缺了一张快照。

> **能自动做的事，不要留给调用方。**
> 每多一个"请记得调用"的 API，就多一个"忘了调用"的 bug。

**方案三把基线变成不变式（invariant）：**

> `SnapshotHistory` 一生下来，`undo` 里就至少有一张快照。
> 这张快照永远代表"最早保留的版本"。

所有后续逻辑都可以建立在这个不变式上，不需要任何防御性判空。

#### 注意：基线不等于"打开时的状态"

一旦触发容量上限淘汰，**基线会被淘汰掉**：

```java
private void trim() {
    while (undo.size() > maxDepth) {
        undo.removeLast();       // 移除最老的
        evicted++;
    }
}
```

Demo 4 的真实输出就是证据：

```text
  连做 4 次编辑后 → 「第4版」
  统计 可撤 2 步 | 可重 0 步 | 已拍 5 淘汰 2 编辑 4
  「淘汰 2」= 第0版和第1版的快照已被挤出栈
```

`maxDepth = 3` 时，栈底那张"第0版基线"已经被淘汰了。
此时栈底是"第1版"。

**所以 `canUndo()` 的判断不能写成"能不能撤到基线"，
只能写成"栈里还有没有超过一张"。**

这就是 `HistoryStats.canUndo()` 的注释里特意说明的那句话：

> 注意栈底不一定是"打开编辑器时的最初状态" —— 超出深度上限后
> 最老的快照会被淘汰，栈底就向前推进了。

#### 代价

- 构造器里有副作用（拍了一张快照、消耗了一个 `sequence`）。
  这在"构造器应该只做初始化"的洁癖看来是不合格的。
  但这个副作用**只发生在自己身上**（不改任何外部对象），
  且**建立了一个必须存在的不变式** —— 所以我认为划算。
- 如果调用方想"用当前状态作为新基线"，需要 `reset(label)` 方法。
  本项目提供了，用途是"保存文件后清空撤销栈"。

---

### 4.10 为什么用 `ArrayDeque` 替代 `Stack`

```java
private final Deque<Snapshot> undo = new ArrayDeque<>();
```

#### 问题

撤销栈用什么容器？

#### 三个候选

| 容器 | 底层 | 线程安全 | public 方法数 | 允许中间插入 |
|---|---|---|---|---|
| `Stack` | 继承 `Vector`（数组） | 每个方法 `synchronized` | **66** | 能（`add(i, e)`） |
| `LinkedList` | 双向链表 | 否 | ~40 | 能（`add(i, e)`） |
| **`ArrayDeque`** | 循环数组 | 否 | ~30 | 不能（只有头尾操作） |

#### 选择理由

**（1）`Stack` 的三个硬伤**

- **继承 `Vector`**，白送 61 个与栈无关的方法（探针 P7：66 个 public，只有 5 个是栈的）；
- **`synchronized` 加错了地方**：加在 `Vector` 的每个方法上，
  意味着"压栈"和"弹栈"各抢一次锁，中间状态照样被看见；
- **JDK 官方劝退**：`Stack` 的 javadoc 从 Java 6 起就写着"优先用 `Deque`"。

**（2）`LinkedList` 被淘汰**

理论上 `LinkedList` 的 `push`/`pop` 是 O(1)，和 `ArrayDeque` 一样。
但实测（`ArrayDeque` 的 javadoc 里明确写了）：

> This class is likely to be faster than `Stack` when used as a stack,
> and faster than `LinkedList` when used as a queue.

原因是**内存局部性**：`ArrayDeque` 用连续数组，
`LinkedList` 每个节点都是一次独立分配，缓存命中率差。
在"每次编辑都要 push 一次"的热路径上，这个差距是实实在在的。

**（3）`ArrayDeque` 还有一个额外好处：它不实现 `List`**

```java
Deque<Snapshot> undo = new ArrayDeque<>();
undo.add(1, snapshot);      // ❌ 编译错误：Deque 没有这个方法
undo.get(0);                // ❌ 编译错误
```

**看，连"按下标插队"这个动作都写不出来。**

对比 `Stack`：

```java
Stack<Snapshot> undo = new Stack<>();
undo.add(1, snapshot);      // ✅ 编译通过，栈被插队
```

> **好的容器类型，本身就是一种约束。**
> 用 `Deque` 声明，等于告诉所有后来的人："这里只有头尾操作。"

#### 一个反面考虑：为什么不用 `List` + 自己管下标？

```java
List<Snapshot> undo = new ArrayList<>();
// push: undo.add(s);
// pop:  undo.remove(undo.size() - 1);
```

能工作，但**每个操作都要自己算下标**，而且 `remove(0)` 是 O(n) 的
（要移动整个数组）—— 淘汰最老快照时正好是 `remove(0)`。

`ArrayDeque` 用循环数组，`removeLast()` 是 O(1)。

#### 代价

- `ArrayDeque` 不支持 `null` 元素（`push(null)` 抛 NPE）。
  但本项目永远不会压 `null`（`snapshot()` 不返回 `null`），所以没有影响。
  **实际上这是好事**：多一道防线。
- `ArrayDeque` 没有 `get(i)`，所以"看第 3 个快照"做不到。
  但 `Caretaker` 本来就不该按下标访问历史 —— 那会破坏栈的语义。

---

### 4.11 为什么容量上限是必须的

```java
private void trim() {
    while (undo.size() > maxDepth) {
        undo.removeLast();
        evicted++;
    }
}
```

#### 问题

撤销栈能长多长？

#### 算一笔账

一份客服回复草稿，平均 500 字（UTF-16 下约 1KB 的 `char[]`）。

| 用户行为 | 快照数 | 内存 |
|---|---|---|
| 一个工单改 20 次 | 21 | ~21KB |
| 一天处理 50 个工单 | 1050 | ~1MB |
| 一个月（20 个工作日） | 21000 | ~21MB |
| 一个团队 100 人 | 2100000 | **~2GB** |

上面算的还是"每次 `setContent` 都存全文"的粗放模式。
如果换成"每个字符一个快照"（有些编辑器真的这么干），数字要乘 100。

**这不是理论推演。真实世界的编辑器崩溃报告里，
"内存占用过高"有相当一部分来自撤销栈。**

#### 选择理由

`maxDepth` 是一个**必须由产品拍板的数字**，不是技术细节。

- 太小（比如 5）：用户会觉得"撤销这么难用"；
- 太大（比如 10000）：内存扛不住；
- **业界常见值：20 ~ 100**。
  - VS Code 默认 `undoStackSize` 是 1000（但它的快照是差量的）；
  - IDEA 默认无限，但用差量 + 落盘；
  - 大多数 Web 编辑器是 50 ~ 100。

本项目取 `DEFAULT_UNDO_DEPTH = 20`（够用）和 `MAX_UNDO_DEPTH = 100`（上限）。

#### 为什么淘汰的是"最老的"而不是"最近的"

因为用户的行为模式是**"最近改的更容易反悔"**。

- 刚敲错一个字 → 立刻撤销（需要最近 1 步）；
- 改到第三段发现开头写错了 → 撤销 10 步（需要最近 10 步）；
- 想撤销到"半小时前" → 几乎不可能，因为那期间已经改了 50 次。

**用户对"很久以前那一步"的记忆本来就是模糊的。**

所以 `removeLast()`（移除栈底）是正确的方向。

#### Demo 4 就是这个代价的展示

```text
  连做 4 次编辑后 → 「第4版」
  统计 可撤 2 步 | 可重 0 步 | 已拍 5 淘汰 2 编辑 4
  「淘汰 2」= 第0版和第1版的快照已被挤出栈
  连续撤销：
    undo#1 = true → 「第3版」
    undo#2 = true → 「第2版」
    undo#3 = false → 「第2版」
    undo#4 = false → 「第2版」
  只能撤到第2版为止 —— 这就是容量上限的代价，必须让用户知道
```

**注意最后一句："必须让用户知道"。**

一个诚实的实现，应该在 UI 上显示"更早的历史已被清理"，
而不是让用户按了 10 次撤销都没反应、还以为是 bug。

本项目通过 `HistoryStats.evicted()` 把这个事实暴露出来：

```java
public String summary() {
    return "可撤 " + Math.max(0, undoDepth - 1) + " 步"
            + " | 可重 " + redoDepth + " 步"
            + " | 已拍 " + snapshotsTaken + " 淘汰 " + evicted + " 编辑 " + edits;
}
```

Demo 4 输出里的 `淘汰 2` 就是它。

#### 更进一步的方案（本项目未实现）

| 方案 | 思路 | 代价 |
|---|---|---|
| **差量快照** | 只存和上一版的 diff | 恢复时要回放，且"恢复到第 5 步"需要重放前 4 步 |
| **合并连续输入** | 500ms 内的输入合成一步 | 需要定时器，且"撤销一次"可能退掉一整句话 |
| **落盘** | 超出内存上限写磁盘 | 需要序列化（§3.3 写法 C 的所有问题） |
| **压缩** | 每张快照 zlib 压一下 | CPU 换内存，短文本反而变大 |

这四条都是**真实产品的做法**，也都超出了"讲清备忘录模式"的范围。
本项目在 §7 的扩展对比里会给出改造点。

#### 代价

- 用户可能"撤不回去"，需要 UI 明确告知；
- `trim()` 在 `edit` 和 `redo` 里各调一次（`undo` 里不用，因为 `undo` 只会让栈变小）。

---

### 4.12 为什么时钟要注入

```java
public SnapshotHistory(Editor editor, int maxDepth, Supplier<Instant> clock) { ... }

private Snapshot capture(EditKind kind, String detail) {
    sequence++;
    SnapshotMetadata metadata = new SnapshotMetadata(sequence, kind, detail, clock.get());
    return editor.snapshot(metadata);
}
```

#### 问题

`capturedAt` 的时间从哪来？

#### 可选方案

**方案一：直接 `Instant.now()`**

```java
SnapshotMetadata metadata = new SnapshotMetadata(sequence, kind, detail, Instant.now());
```

**方案二：注入 `Supplier<Instant>`（本项目）**

#### 选择理由

**方案一在本项目里有一个直接的、可观测的问题：输出无法复现。**

如果 `capturedAt` 是真实时间，那么同一份 Demo 跑两次，
时间戳就不同，SHA-256 就不同 ——

```text
  第 1 次：OUT SHA256: 9598E364...
  第 2 次：OUT SHA256: 3A7F12B4...     ← 无法验证"输出是否一致"
```

而"**三次运行哈希一致**"是本项目所有 Demo 的验证标准。

**方案二把时间变成参数，于是输出变成确定性的。**

```java
private static SteppedClock clock() {
    return new SteppedClock(Instant.parse("2025-03-14T09:00:00Z"), Duration.ofMillis(500));
}
```

每次拍照都比上一次晚 500ms，从固定基点开始。
Demo 1 的真实输出就是这样：

```text
  a 自己拍一张 → #999 输入 · 手工拍的 · 09:00:00.000
```

`09:00:00.000` —— 不管什么时候运行，永远是这一个值。

#### 还有更重要的理由：可测试性

真实项目里，"撤销栈按时间排序对不对"、"超过 5 分钟的快照要不要合并"
这类逻辑，一旦依赖真实时钟，测试就只能这么写：

```java
history.edit(...);
Thread.sleep(5000);            // ← 等 5 秒
history.edit(...);
assertThat(history.stats().merged()).isEqualTo(1);
```

这样的测试：

- 慢（每个用例 5 秒）；
- 不稳定（CI 机器负载高时，`sleep` 可能不够，也可能过头）；
- 无法测"跨天"、"跨年"、"夏令时切换"。

注入时钟之后，全部变成：

```java
SteppedClock clock = new SteppedClock(base, Duration.ofMinutes(6));
history.edit(...);      // 第 1 张，t = base
history.edit(...);      // 第 2 张，t = base + 6min
assertThat(history.stats().merged()).isEqualTo(1);
```

毫秒级跑完，且结果完全确定。

> **"注入时钟"是让"时间相关逻辑"变得可测的唯一办法。**
> 与之并列的还有"注入随机源"和"注入文件系统"。
> 这三样东西一旦硬编码在业务代码里，那段代码就永远只能靠 `sleep` 和 `mock` 去测。

#### 为什么用 `Supplier<Instant>` 而不是自定义接口

```java
public interface Clock { Instant now(); }
```

`Supplier<Instant>` 是 JDK 自带的函数式接口，语义完全吻合（"给我一个时刻"），
不需要额外的文件。

只有在需要**多个方法**时才值得自定义接口（比如 `Clock.now()` + `Clock.tick()`）。

#### 代价

- `SnapshotHistory` 的构造器多一个参数，第一次用的人要查一下该传什么；
- 忘了注入时，没有默认值 —— 这是**故意的**：
  有默认值（比如 `Instant::now`）的话，测试代码会偷偷用真实时钟，
  于是"测试通过但输出不确定"的问题又会回来。

---

### 4.13 为什么要有监听器

```java
@FunctionalInterface
public interface HistoryListener {
    void onHistoryChanged(HistoryStats stats, Editor editor);
}
```

#### 问题

撤销栈变了，谁负责刷新界面？

#### 可选方案

**方案一：调用方每次操作后自己刷新（原项目）**

```java
history.pushMemento(document.save());
refreshUndoPanel();          // ← 又是一次"请记得调用"
```

**方案二：监听器（本项目）**

```java
history.addListener(new ConsoleHistoryListener("面板"));   // 启动时注册一次
```

#### 选择理由

**这是原项目 P2 的同一个病：把"必须做的事"交给调用方自觉。**

方案一有两个失效点：

- `edit()` 之后忘了刷新 → 面板显示的历史比实际少一步；
- `undo()` 之后忘了刷新 → 撤销按钮还是亮的，用户点了没反应。

方案二把"通知"焊死在 `SnapshotHistory` 的三个出口上：

```java
public void edit(...)     { ... notifyChanged(); }
public boolean undo()     { ... notifyChanged(); return true; }
public boolean redo()     { ... notifyChanged(); return true; }
public void reset(...)    { ... notifyChanged(); }
```

**只要状态变了，就一定会通知。** 调用方只需要注册一次，
之后永远不用再想"我是不是忘了刷新"。

#### 为什么 `onHistoryChanged` 传 `HistoryStats` 而不是 `SnapshotHistory`

试想如果签名是这样：

```java
void onHistoryChanged(SnapshotHistory history);      // ❌
```

监听器拿到 `history` 之后能做什么？

```java
public void onHistoryChanged(SnapshotHistory h) {
    h.undo();                    // ← 在回调里撤销，递归触发，无限循环
    h.addListener(this);         // ← 在回调里注册自己，无穷多个
}
```

**给出去的能力越大，被误用的方式越多。**

本项目的签名：

```java
void onHistoryChanged(HistoryStats stats, Editor editor);
```

- `HistoryStats` 是**不可变的值**，读得到、改不了；
- `Editor` 有写方法，但监听器的语义约定是"只读"。

严格说，第二个参数仍然给了监听器改正文的能力。
如果要绝对安全，应该传一个只读视图。**本项目没有做，理由是两个**：

1. 监控器是**你自己写的**，不是第三方插件 —— 信任边界不同；
2. 只读视图需要额外一个 `EditorView` 接口，会引出"视图和实体怎么同步"这个
   新问题，属于过度设计。

这是一个**有意的取舍**，在 §8.5 的"已知局限"里会列出来。

#### 配件的地位要说清楚

```java
onHistoryChanged(HistoryStats stats, Editor editor)
```

`HistoryListener` / `ConsoleHistoryListener` **不是备忘录模式的组成部分**。

判断标准很简单（§1.4 已经说过一次）：

> **删掉它，模式还成立吗？**
> 删掉监听器 → 撤销照样工作，只是看不见 → **它是配套设施**。

那为什么要写它？

因为它让 Demo 能**看见**撤销栈的每一次变化。
没有它，Demo 1 的输出就只有"正文变成了什么"，
看不出"为什么这次编辑算了三步、那次只算一步"。

**教学项目里，可观测性不是装饰，是论证的一部分。**

#### 一个实现细节：为什么要 `List.copyOf`

```java
private void notifyChanged() {
    if (listeners.isEmpty()) {
        return;
    }
    HistoryStats stats = stats();
    for (HistoryListener listener : List.copyOf(listeners)) {
        listener.onHistoryChanged(stats, editor);
    }
}
```

因为监听器**在回调里增删监听器是常见操作**：

```java
// 窗口关闭时
public void onHistoryChanged(HistoryStats s, Editor e) {
    window.close();
    history.removeListener(this);       // ← 在这里改 listeners
}
```

如果直接遍历原 `List`，会抛 `ConcurrentModificationException`。

`List.copyOf` 做一个防御性拷贝，代价是每次通知多分配一个小数组。
**在"用户按键"这个频率上，这点开销完全可忽略**，而它换来的是"监听器可以自由增删"。

#### 代价

- 每次通知都要构造一个 `HistoryStats`（一个 record，5 个 `int`）；
- 监听器是**同步**调用的 —— 如果某个监听器慢了，`edit()` 就会变慢。
  本项目在 `HistoryListener` 的 javadoc 里明确写了这条约束：

  > 实现方必须**快速返回**：这个回调在 `edit` 的同步路径上，
  > 慢一秒就是用户敲字卡一秒。

- 没有做"异常隔离"：一个监听器抛异常，会中断后面的监听器，
  并且异常会传到 `edit()` 的调用方。
  **这是一个已知的缺陷**，见 §8.5。

---

### 4.14 十三个决定的依赖关系

这些决定不是平行的，它们互相支撑：

```text
                 ┌─────────────────────────────────────────┐
                 │  封装：4.1 窄接口                        │
                 │        4.2 私有 record                  │
                 │        4.3 归属令牌                     │
                 └───────────────┬─────────────────────────┘
                                 │ 让 Snapshot 成为一个"能握不能读"的令牌
                                 ▼
                 ┌─────────────────────────────────────────┐
                 │  内容：4.4 元信息  4.5 枚举  4.6 选区    │
                 └───────────────┬─────────────────────────┘
                                 │ 让快照"完整"，且能被人读懂
                                 ▼
                 ┌─────────────────────────────────────────┐
                 │  时机：4.7 edit()  4.8 undo()  4.9 基线  │
                 └───────────────┬─────────────────────────┘
                                 │ 让"什么时候拍"不依赖调用方自觉
                                 ▼
                 ┌─────────────────────────────────────────┐
                 │  边界：4.10 Deque  4.11 上限             │
                 │        4.12 时钟  4.13 监听器           │
                 └─────────────────────────────────────────┘
```

**如果去掉上面任何一层，下面的层都会塌：**

- 没有 4.1/4.2/4.3，`Snapshot` 就退化成 `public record`，那 4.4 里的元信息
  也会跟着泄露（因为 `metadata()` 是公开的）；
- 没有 4.4/4.5/4.6，快照不完整，`undo()` 恢复出来的状态就是错的；
- 没有 4.7/4.8/4.9，P1/P2/P9 的 bug 会原样回来；
- 没有 4.10/4.11，P7/P8 的 bug 会原样回来。

下一章逐文件解析，把这些决定对应到具体的代码行。

---
## 5. 代码解析：十三个文件

这一章逐文件讲。每个文件都回答四个问题：

1. **它是什么角色？**
2. **关键代码为什么这么写？**
3. **删掉它会怎样？**
4. **合并到别的类里会怎样？**

顺序按**依赖方向**排：先讲被依赖的（接口、值对象），后讲依赖别人的。
这样一路读下来，每个新出现的类型都已经解释过。

```text
Snapshot ──────────────┐
SnapshotMetadata ──┐   │
EditKind ──────────┤   │
TextRegion ────────┤   │
EditorLimits ──────┴───┼──► Editor ──┐
                       │             │
SnapshotRejectedException ◄──────────┘
                                     │
HistoryStats ──────┐                 │
HistoryListener ───┼──► SnapshotHistory ◄──┘
ConsoleHistoryListener ┘
                                     │
SteppedClock ────────────────────────┘
                                     │
Client ◄─────────────────────────────┘
```

---

### 5.1 `Snapshot.java`（83 行）：窄接口

**角色：** Memento 的**窄接口**。Caretaker 能看见的全部。

#### 全文关键部分

```java
public interface Snapshot {
    SnapshotMetadata metadata();
}
```

**一个方法，四行代码**（不含 javadoc）。

#### 为什么只有这一个方法

§4.1 已经完整论证过。这里只补一个**反例**，让你感受一下"多一个方法"的代价：

```java
public interface Snapshot {
    String text();                    // ← 假如加上这个
    SnapshotMetadata metadata();
}
```

加上之后，下面这段代码就变得"完全合法"：

```java
// 某天，一个新人想加一个功能："撤销前先做一次自动备份"
public boolean undo() {
    if (undo.size() <= 1) return false;
    Snapshot target = undo.pop();
    localStorage.setItem("backup", target.text());     // ← 草稿进了浏览器本地存储
    redo.push(capture(EditKind.UNDO, ...));
    editor.restore(target);
    return true;
}
```

这个改动**不会被 code review 拦下来**，因为 `text()` 就在接口上，
用它是"正常使用 API"。

**接口上有什么方法，决定了人们会拿它做什么。**

#### 这份 javadoc 为什么这么长

83 行里，77 行是注释，只有 6 行是代码。

因为 `Snapshot` 是**最容易被误解**的一个角色。常见的误解有三种：

| 误解 | 表现 |
|---|---|
| 把 `Snapshot` 当成 DTO | 于是给它加 getter，方便"序列化" |
| 让 `Snapshot` 承担比对职责 | 于是加 `diff(Snapshot other)` |
| 让 `Snapshot` 承担持久化 | 于是 `implements Serializable` |

这三种都会破坏"不透明"这个核心性质。

所以 javadoc 花了大量篇幅讲两件事：

1. **GoF 说的"两种接口"到底怎么落地**（`<pre>` 块里的对照）；
2. **光有类型不够，还要有归属令牌**（这是本项目在实现中踩出来的坑）。

**教学项目的注释比例高，不是啰嗦，而是因为"为什么"比"是什么"更需要解释。**

本项目 13 个文件的注释/代码比是 893 : 544 ≈ 1.64 : 1。
生产项目里这个比例通常是 0.2 : 1 左右。

#### 删掉它会怎样

`SnapshotHistory` 的两个栈就必须用 `Object` 或 `Editor.Memento` 声明：

```java
private final Deque<Object> undo = new ArrayDeque<>();          // 失去类型安全
private final Deque<Editor.Memento> undo = new ArrayDeque<>();  // ❌ 编译错误：Memento 不可见
```

**第二种写法直接编译不过** —— 因为 `Memento` 是 `private` 的。

这正是设计的目的：**Caretaker 在物理上无法声明一个"装着 Memento"的容器**，
只能声明"装着 Snapshot"的容器。

#### 合并到 `Editor` 里会怎样

```java
public final class Editor {
    public interface Snapshot { SnapshotMetadata metadata(); }   // 变成嵌套接口
    // ...
}
```

这样写**完全可行**，而且能省一个文件。为什么本项目没有这么做？

因为**使用者**不同：

- `Snapshot` 被 `SnapshotHistory`、`HistoryStats`、`Client` 使用；
- `Memento` 只被 `Editor` 使用。

如果 `Snapshot` 也嵌在 `Editor` 里，那 `SnapshotHistory` 就得写
`Editor.Snapshot`，读起来多一层。

更重要的：**一个公开的、和 `Editor` 平级的接口，传达的是
"这是模式的一部分，不是 `Editor` 的内部细节"**。

`Memento` 相反 —— 它是 `Editor` 的内部细节，所以必须嵌进去。**这两个选择的方向是相反的，而它们恰好都是对的。**

---

### 5.2 `SnapshotMetadata.java`（83 行）：快照的身份证

**角色：** 快照里"可以公开"的那一半。

#### 关键代码

```java
public record SnapshotMetadata(int sequence, EditKind kind, String detail, Instant capturedAt) {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    public SnapshotMetadata {
        if (sequence < 1) {
            throw new IllegalArgumentException("快照编号从 1 开始，实际 " + sequence);
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(capturedAt, "capturedAt");
        detail = detail == null ? "" : detail;
    }

    public String display() {
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(sequence).append(' ').append(kind.label());
        if (!detail.isEmpty()) {
            sb.append(" · ").append(detail);
        }
        sb.append(" · ").append(TIME.format(capturedAt));
        return sb.toString();
    }
}
```

#### 三个值得说的细节

**（1）紧凑构造器里的 `detail = ...` 是"重新赋值参数"**

```java
public SnapshotMetadata {
    // ...
    detail = detail == null ? "" : detail;      // 注意：不是 this.detail
}
```

紧凑构造器（compact constructor）的规则是：
**你在里面**不能**写 `this.x = ...`，但可以给参数重新赋值，
最后编译器会自动把参数赋给字段。**

所以 `detail = detail == null ? "" : detail;` 的实际效果是
"把 null 归一化成空串，然后写进字段"。

这是一个非常实用的小技巧：**在构造期消灭 null**。

如果不在构造器里归一化，那么每一个 `detail.isEmpty()` 的调用点都要先判 null：

```java
if (meta.detail() != null && !meta.detail().isEmpty()) { ... }    // 每个调用点都要写
```

**判空是会传染的。** 在边界上把它杀掉，后面就都不用管。

**（2）时间格式器固定用 `ZoneOffset.UTC`**

```java
private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
```

很多人的第一反应是 `ZoneId.systemDefault()` —— 显示本地时间更"人性化"。

但对本项目来说，那会破坏**输出可复现**：

```text
  中国机器：09:00:00.000
  美国机器：17:00:00.000     ← 同样的代码，不同的输出
```

而"三次运行哈希一致"是本项目所有 Demo 的验证标准。

**这是一个典型的"可复现性 vs 本地化"取舍。**

真实产品当然要显示本地时间（用户看不懂 UTC）。
正确的做法是：**存储用 `Instant`（UTC），显示时再转本地时区**。

本项目把"显示"这一步简化成了固定 UTC —— 因为 Demo 的读者不需要本地化。

**（3）为什么 `display()` 放在 record 里，而不是放在 UI 层**

```java
public String display() {
    return "#3 输入 · 在光标 4 处插入「合规」 · 09:00:04.500";
}
```

严格来说，"怎么显示"属于 UI 层，值对象不该管。

但这里有一个务实的考虑：**这个格式在 Demo 里要出现几十次**，
如果每次都手拼，早晚会出现"某处多一个空格"的不一致。

放在 record 里，格式就只有一份。

**这是一个"在正确性和实用性之间"的妥协，不是"最佳实践"。**

如果这个 record 要跨模块使用，`display()` 应该移到 UI 层 ——
否则改一下显示格式，就要重新发布整个领域模型。

#### 删掉它会怎样

`Snapshot` 接口就只剩一个空方法，撤销面板无法显示任何有意义的信息：

```text
[版本 1] [版本 2] [版本 3]     ← 用户不知道自己在干嘛
```

#### 合并到 `Snapshot` 里会怎样

```java
public interface Snapshot {
    int sequence();          // ← 四个方法全平铺在接口上
    EditKind kind();
    String detail();
    Instant capturedAt();
}
```

功能等价，但**丢失了"分组的语义"**。

`SnapshotMetadata` 表达的是"这一组信息是可以公开的"。
把它平铺到接口上，就看不出"这四者属于同一类，且和 text/cursor 是不同性质的"。

更重要的是：**将来要给快照加一个"内部"字段（比如校验和），
放在 `Memento` 里就行；要加一个"公开"字段（比如"是否自动保存"），
放在 `SnapshotMetadata` 里就行。** 两个位置，界限清晰。

---

### 5.3 `EditKind.java`（70 行）：七种编辑类型

**角色：** 快照产生原因的枚举。

#### 完整枚举值

```java
public enum EditKind {
    BASELINE("基线"),
    TYPING("输入"),
    DELETION("删除"),
    SELECTION("选区"),
    REPLACE("整段替换"),
    UNDO("撤销"),
    REDO("重做");

    private final String label;
    EditKind(String label) { this.label = label; }
    public String label() { return label; }
}
```

#### 为什么 `SELECTION` 也是一个类型

一个自然的疑问："选区变化不改正文，为什么要占一张快照？"

看 Demo 里 `select()` 的用法：

```java
public void select(TextRegion region) {
    this.selection = region;
    this.cursor = region.end();
}
```

**光标和选区**是状态的一部分（否则 P5 的缺陷修不掉）。

但本项目**没有**在 `select()` 里拍快照 ——
因为 `select()` 不在 `edit()` 里被调用。

那 `SELECTION` 有什么用？

它的用途是：**当用户做"选中 + 删除"时，第一张快照的类型是 `SELECTION`**。

```java
history.edit(EditKind.SELECTION, "全选后删除", e -> {
    e.selectAll();
    e.deleteSelection();
});
```

这样撤销面板会显示"选区 全选后删除"，
而不是"删除 全选后删除" —— 用户能看出这一步是从"选中"开始的。

**类型描述的是"这一步的起点"，不是"这一步做了什么"。**
这是一个细微但重要的语义选择。

#### 为什么 `UNDO` / `REDO` 也是编辑类型

因为**撤销本身也会产生快照**（用于支撑重做）。

`undo()` 内部：

```java
redo.push(capture(EditKind.UNDO, "准备回到 #" + target.metadata().sequence()));
```

如果不给这两类操作一个枚举值，那就得用 `TYPING` 糊弄过去 ——
于是撤销面板上会出现"输入 准备回到 #3"这种荒谬的文案。

**每一种"会产生快照的原因"，都必须有一个枚举值。**

这是"枚举完整性"的一个检验点：

> 假设你现在要给 `SnapshotHistory` 加一个新方法 `revertTo(int sequence)`
> （直接跳回第 N 步）。它会产生快照吗？
> 会。那就要加一个 `REVERT`。

#### 删掉它会怎样

退化成 `String kind`，然后出现 §4.5 说的"四种输入法"问题。

#### 为什么 `label` 放在枚举里，而不是用 `name()`

```java
EditKind.TYPING.name()      // "TYPING" —— 打印到界面上很奇怪
EditKind.TYPING.label()     // "输入" —— 用户能看懂
```

`name()` 是**代码里的标识符**，`label()` 是**给用户看的文案**。
两者不该混用。

如果要做国际化，`label()` 应该换成资源 key：

```java
EditKind.TYPING.i18nKey()   // "edit.kind.typing"
```

本项目不涉及国际化，直接存中文。

---

### 5.4 `TextRegion.java`（111 行）：选区值对象

**角色：** 半开区间 `[start, end)`。

#### 核心代码

```java
public record TextRegion(int start, int end) {

    public TextRegion {
        if (start < 0) {
            throw new IllegalArgumentException("start 不能为负，实际 " + start);
        }
        if (end < start) {
            throw new IllegalArgumentException(
                    "end(" + end + ") 不能小于 start(" + start + ")");
        }
    }

    public static TextRegion caret(int position) { return new TextRegion(position, position); }

    public int length() { return end - start; }

    public boolean isEmpty() { return start == end; }

    public TextRegion shift(int delta) { return new TextRegion(start + delta, end + delta); }

    @Override
    public String toString() { return "[" + start + "," + end + ")"; }
}
```

#### 为什么 `isEmpty()` 和 `length()` 不是 record 组件

`record` 的组件是**构造参数**。`length()` 是**计算属性** ——
它完全由 `start` 和 `end` 决定，存起来是冗余。

如果写成组件：

```java
public record TextRegion(int start, int end, int length) { }    // ❌
```

那就会出现不一致的对象：`new TextRegion(0, 5, 3)` —— `length` 撒谎了。

**任何能由其他字段算出来的值，都不该成为字段。**

这条规则在数据库设计里叫"不要存冗余列"，
在值对象设计里叫"不要存派生状态"。同一个道理。

#### 为什么重写 `toString()`

record 默认生成：

```text
TextRegion[start=10, end=10]
```

自定义之后：

```text
[10,10)
```

**这个改动看起来是小事，但它在 Demo 输出里出现了 20 多次。**

对比一下 Demo 1 的真实输出：

```text
  初始 → 正文「客服：您好，您的订单」 光标 10 选区 [10,10)
```

如果没重写，就是：

```text
  初始 → 正文「客服：您好，您的订单」 光标 10 选区 TextRegion[start=10, end=10]
```

一行 60 个字符变成 90 个，而且 `[10,10)` 这个写法**本身就传达了"半开区间"的语义**，
比 `start=10, end=10` 更直观。

#### 为什么有 `shift()` 但代码里没用到

```java
public TextRegion shift(int delta) { ... }
```

先诚实说明：**当前代码里没有任何地方调用 `shift()`。**

那为什么留着？

因为它对应一个**必然会出现**的需求：在选区前插入一段文本之后，
选区应该跟着右移。本项目现在的实现（`replaceRange`）是
"替换后光标放到新文本末尾，选区清空"，所以用不到 `shift()`。

一旦将来要实现"保留选区"的行为（比如"加粗选中文字"），
`shift()` 就会用上。**现在留着是在为那个场景铺路。**

注意：这是一个**有争议的决定**。

> YAGNI 原则说：不需要的功能不要写。
>
> 但 `shift()` 只有 3 行，且它是 `TextRegion` 作为值对象的**完整性**的一部分 ——
> 一个区间类型如果没有"平移"操作，用起来会很别扭。

**判断标准：加它的成本（3 行）vs 将来加它的成本（改一个 record 文件 + 加测试）。
前者明显更低，且不增加理解负担。**

#### 删掉它会怎样

回到两个 `int`，然后：

- `Editor` 的 `selectionStart` / `selectionEnd` 两个字段要各自维护；
- `Memento` 多两个组件；
- 每一处 `select(...)` / `restore(...)` 都要重复校验；
- "空选区"只能靠注释约定。

#### 合并到 `Editor` 里会怎样

```java
public final class Editor {
    public record TextRegion(int start, int end) { }     // 变成嵌套
    // ...
}
```

可行，但 `TextRegion` 是**通用概念**，不只属于 `Editor`。
将来做一个"高亮"、"批注"、"查找替换"的功能，都要用 `TextRegion`。

**通用的值对象应该独立成文件。** 这和 `Memento` 的判断标准相反 ——
`Memento` 是 `Editor` 独有的，所以嵌套。

---

### 5.5 `EditorLimits.java`（99 行）：边界与校验

**角色：** 包私有的常量与校验工具。

#### 完整内容

```java
final class EditorLimits {

    static final int MAX_TEXT_LENGTH = 200_000;
    static final int MAX_UNDO_DEPTH = 100;
    static final int DEFAULT_UNDO_DEPTH = 20;

    static String requireText(String text) {
        Objects.requireNonNull(text, "正文不能为 null");
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "正文超过上限 " + MAX_TEXT_LENGTH + " 字符，实际 " + text.length());
        }
        return text;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " 必须大于 0，实际 " + value);
        }
    }

    private EditorLimits() { }
}
```

#### 关键设计一：`requireText` 返回自身

```java
static String requireText(String text) { ... return text; }
```

为什么不返回 `void`？

因为返回自身可以让调用方写成**一行**：

```java
this.text = EditorLimits.requireText(text);
```

如果返回 `void`：

```java
EditorLimits.requireText(text);
this.text = text;                  // ← 两行，且可能有人只写了第二行
```

**一行能写完的事情，就不会被拆成两行；能保证"校验和赋值同时发生"的写法，
就不会出现"忘了校验直接赋值"的 bug。**

这个技巧在 JDK 里很常见：

```java
// java.util.Objects
public static <T> T requireNonNull(T obj) { ... return obj; }
```

`Objects.requireNonNull(x)` 返回 `x`，就是为了让你能写
`this.field = Objects.requireNonNull(field)`。

#### 关键设计二：`clamp` 用于"用户输入"，构造器用于"程序入参"

这是一个非常重要的区分：

```java
// 用户点了行尾之外的位置 —— 钳制
public void moveCaret(int position) {
    int target = EditorLimits.clamp(position, 0, text.length());     // 不抛异常
    this.cursor = target;
    this.selection = TextRegion.caret(target);
}
```

```java
// 程序调 select(new TextRegion(0, 999)) —— 抛异常
public void select(TextRegion region) {
    Objects.requireNonNull(region, "region");
    if (region.end() > text.length()) {
        throw new IllegalArgumentException("选区 " + region + " 超出正文长度 " + text.length());
    }
    // ...
}
```

**为什么两种处理方式？**

| 来源 | 例子 | 正确处理 |
|---|---|---|
| **用户操作** | 鼠标点了行尾右边 200px | 钳制到行尾（用户体验优先） |
| **程序调用** | 代码写 `select(new TextRegion(0, 999))` | 抛异常（bug 要暴露） |

如果对用户输入也抛异常，用户点错一下编辑器就崩了；
如果对程序入参也钳制，一个写错的 `select` 会静默地选中错误区间。

**同一个数字，两种来源，两种处理。** 这是输入校验的第一原则。

#### 关键设计三：包私有（无 `public`）

```java
final class EditorLimits {          // ← 注意：不是 public
```

这不是 API，是 `Editor` / `SnapshotHistory` / `TextRegion` 之间的内部约定。

如果暴露成 `public`，它就变成了必须长期兼容的承诺：

- 有人会 `EditorLimits.clamp(...)` 用在自己的代码里；
- 你改一下 `MAX_UNDO_DEPTH` 的值，别人的测试就会挂。

**"包私有"是一个很便宜但很有效的边界。**

#### 删掉它会怎样

校验逻辑散到 `Editor` 的五个方法里：

```java
public void type(String chunk) {
    Objects.requireNonNull(chunk, "chunk");
    // 也可能有人加：if (chunk.length() > MAX) throw ...
}
public void replaceAll(String newText) {
    Objects.requireNonNull(newText, "newText");
    // 也可能有人加：if (newText.length() > MAX) throw ...
}
// ... 还有 deleteBackward / replaceRange / 构造器
```

**"也可能有人加"就是问题所在。** 五处校验，总有一处会漏。

#### 为什么上限是 20 万字符

一个粗糙的估算：

- 200,000 字符 × 2 字节（UTF-16）= 400KB；
- 撤销深度 20 → 8MB；
- 一个用户开 5 个文档 → 40MB。

对桌面端可接受，对 Web 端偏高（浏览器单标签页通常限制在几百 MB）。

**这个数字是拍出来的，不是算出来的。** 真实产品应该基于
"P99 文档大小 × 期望撤销深度 × 并发文档数" 来定，
并且做成可配置项。

本项目把它写成常量，是为了让"边界"这个概念可见 ——
**具体数值不重要，"有边界"这件事才重要。**

---

### 5.6 `Editor.java`（334 行）：Originator

**角色：** 被备份的对象。拍快照、恢复快照。

这是全项目**最重要的一个文件**，因为它同时承担了：

- 领域状态（正文、光标、选区）；
- 编辑操作（输入、删除、选中、替换）；
- 备忘录的"宽接口"（构造与读取 `Memento`）；
- 归属校验（`ownerToken`）。

#### 5.6.1 字段与不变式

```java
public final class Editor {

    private String text;
    private int cursor;
    private TextRegion selection;

    private final Object ownerToken = new Object();
```

三个**可变**字段、一个**不可变**令牌。

这个类**不是**不可变的 —— 它就是要被改的（Originator 的职责）。

**但它维护了一组不变式（invariant）：**

| 不变式 | 由谁保证 |
|---|---|
| `text != null` | 所有写路径都过 `EditorLimits.requireText` |
| `0 <= cursor <= text.length()` | `replaceRange` / `moveCaret` / `select` |
| `selection.end() == cursor` | 同上（光标永远在选区末尾） |
| `selection.end() <= text.length()` | `select` 里有显式检查 |

**注意第三个不变式：`selection.end() == cursor`。**

为什么光标要在选区的**末尾**而不是开头？

因为这样"输入"的语义最自然：

```java
public void type(String chunk) {
    replaceRange(selection.start(), selection.end(), chunk);
}
// replaceRange 内部：
int caret = from + replacement.length();      // 光标落在插入内容的末尾
```

如果光标在选区开头，插入文字后光标该怎么放就要额外约定。

**这是一个"选择一种约定并坚持"的例子**，两种都能工作，但混用会乱。

#### 5.6.2 所有写操作都汇到 `replaceRange`

```java
private void replaceRange(int from, int to, String replacement) {
    String next = text.substring(0, from) + replacement + text.substring(to);
    this.text = EditorLimits.requireText(next);
    int caret = from + replacement.length();
    this.cursor = caret;
    this.selection = TextRegion.caret(caret);
}
```

**五个公开的写方法，最终都走这一个私有方法：**

```text
  type(chunk)              ─┐
  deleteBackward(count)    ─┤
  deleteSelection()        ─┼──► replaceRange(from, to, replacement)
  select(region)           ─┤        （select 不走，它不改正文）
  moveCaret(position)      ─┤        （moveCaret 不走）
  replaceAll(newText)      ─┘        （replaceAll 也不走，它整段换）
```

准确地说，走 `replaceRange` 的是：

- `type(String chunk)` → `replaceRange(selection.start(), selection.end(), chunk)`
- `deleteBackward(int count)` → 有选区时删选区，否则 `replaceRange(cursor-count, cursor, "")`
- `deleteSelection()` → `replaceRange(selection.start(), selection.end(), "")`

**为什么这很重要？**

因为"改完正文之后，光标和选区怎么变"这条逻辑**只写了一遍**。

如果每个方法各写一遍：

```java
public void type(String chunk) {
    this.text = ...;
    this.cursor = ...;          // ← 这里算错了
    this.selection = ...;
}
public void deleteBackward(int count) {
    this.text = ...;
    this.cursor = ...;          // ← 这里又算错一次
    // 忘了更新 selection               ← ← ←
}
```

**"汇总到一个方法"是所有"多入口、单出口"设计的共同点。**
它在备忘录模式里的意义是：**快照的正确性依赖状态的一致性，
而状态的一致性依赖写路径的唯一性。**

#### 5.6.3 `type` 为什么要先 `requireNonNull`

```java
public void type(String chunk) {
    Objects.requireNonNull(chunk, "chunk");
    replaceRange(selection.start(), selection.end(), chunk);
}
```

因为 `replaceRange` 里的 `from + replacement.length()` 会在 `chunk` 为 null 时 NPE，
但那个 NPE 的堆栈会指向 `replaceRange`，而不是调用方。

**在入口处抛，堆栈才指向真正出错的地方。**

这是一个常被忽略的调试体验问题：

```text
  ❌ 在深层抛：  at Editor.replaceRange(Editor.java:289)
                    at Editor.type(Editor.java:150)
                    at Client.lambda$demo1$0(Client.java:52)

  ✅ 在入口抛：  at Editor.type(Editor.java:148)
                    at Client.lambda$demo1$0(Client.java:52)
```

两者的区别是"你得往下翻几层才能看到调用方"。

#### 5.6.4 `snapshot()`：宽接口的入口

```java
public Snapshot snapshot(SnapshotMetadata metadata) {
    Objects.requireNonNull(metadata, "metadata");
    return new Memento(text, cursor, selection, metadata, ownerToken);
}
```

**三件事：**

1. 校验 `metadata` 不为 null；
2. **直接读字段**（`text` / `cursor` / `selection`），不走 getter；
3. 把 `ownerToken` 塞进去。

**为什么"直接读字段"是一个值得指出的细节？**

因为这说明：**`snapshot()` 不依赖任何 getter。**

把 `text()` / `cursor()` / `selection()` 三个 getter 全删掉，
`snapshot()` 照样能编译。

那三个 getter 是给谁用的？

- `Client` 打印 Demo 输出；
- `ConsoleHistoryListener` 读 `editor.length()`；
- `Editor.describe()` 自己。

**getter 服务于"观察"，`snapshot()` 服务于"捕获"。两者不该混。**

§3.2 说过这条判据：

> 判断一个实现是不是真的在用备忘录模式：
> **它有没有为了"能备份"而多开了 getter？**

本项目的答案是"没有"。

#### 5.6.5 `restore()`：三道防线

```java
public void restore(Snapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    if (!(snapshot instanceof Memento memento) || memento.owner() != ownerToken) {
        throw new SnapshotRejectedException(
                "拒绝恢复：" + snapshot.metadata().display()
                        + " 不是本编辑器产生的快照");
    }
    this.text = memento.text();
    this.cursor = memento.cursor();
    this.selection = memento.selection();
}
```

**三道防线，逐个说：**

**第一道：`Objects.requireNonNull`**

对比原项目：

```java
// 原项目
public void restore(DocumentMemento memento) {
    this.content = memento.content();      // ← 隐式 NPE，消息是空的
}
```

原项目抛出的 NPE：

```text
Exception in thread "main" java.lang.NullPointerException:
    Cannot invoke "BehaviorType.MementoPattern.DocumentMemento.content()"
    because "memento" is null
```

（JDK 14 起的 helpful NPE 已经会指出是哪个变量为 null，
这算是 JDK 的一层保护。但在 JDK 8 上就只有一行 `NullPointerException`。）

本项目抛出的：

```text
java.lang.NullPointerException: snapshot
```

**消息里有参数名。** 在一份几百行的日志里，这一行能省掉五分钟。

**第二道：`instanceof Memento`**

如果传进来的是一个"不是 `Memento` 的 `Snapshot`"，说明有人绕过了类型系统。

**但要注意：这一步在正常代码里走不到。** 因为 `Memento` 是 `private` 的，
外部根本造不出别的 `Snapshot` 实现……真的吗？

```java
public interface Snapshot {
    SnapshotMetadata metadata();
}

// 外部代码
Snapshot fake = () -> new SnapshotMetadata(1, EditKind.TYPING, "假", Instant.now());
editor.restore(fake);      // ← 编译通过！
```

**看，`Snapshot` 是公开接口，任何类都能实现它。**

这是一个很容易被忽略的洞：**只把实现类设为 `private`，挡不住别人自己写一个实现。**

所以第二道防线（`instanceof`）**不是走不到的**，它是必要的。

**第三道：`memento.owner() != ownerToken`**

这一道防的是"另一个 `Editor` 拍的快照"。

**为什么用 `!=`（引用比较）而不是 `equals`？**

因为 `ownerToken` 是 `new Object()` —— 它没有重写 `equals`，
所以 `equals` 等价于 `==`。

但**显式写 `!=` 更清楚**：它表达的是"是同一个对象"，不是"内容相同"。

这一条在 §4.3 已经完整讲过（包括那个真实踩到的 bug）。

#### 5.6.6 `describe()` 与 `toString()`

```java
public String describe() {
    return "正文「" + text + "」 光标 " + cursor + " 选区 " + selection;
}

@Override
public String toString() {
    return "Editor{" + describe() + "}";
}
```

**为什么要两个？**

- `describe()` 给 Demo 用（输出格式可控）；
- `toString()` 给调试器用（IDE 的变量面板会调用它）。

`toString()` 委托给 `describe()`，是为了**只有一份格式**。

**但这里有一个隐患：`toString()` 里打全文。**

如果正文有 20 万字，IDE 的变量面板一展开就会卡死。

生产项目里，`toString()` 应该截断：

```java
@Override
public String toString() {
    String preview = text.length() > 40 ? text.substring(0, 40) + "…" : text;
    return "Editor{" + preview + ", cursor=" + cursor + "}";
}
```

**本项目没有做这个截断，因为 Demo 的正文都很短。**
这是一个已知的简化，在 §8.5 会列出来。

#### 5.6.7 `Memento`：私有 record

```java
private record Memento(String text, int cursor, TextRegion selection,
                       SnapshotMetadata metadata, Object owner)
        implements Snapshot {

    @Override
    public String toString() {
        return "Memento{" + metadata.display() + "}";
    }
}
```

**五个组件：**

| 组件 | 作用 | 谁读 |
|---|---|---|
| `text` | 正文快照 | `restore` |
| `cursor` | 光标位置 | `restore` |
| `selection` | 选区 | `restore` |
| `metadata` | 元信息 | `Snapshot.metadata()` 返回它 |
| `owner` | 归属令牌 | `restore` 校验 |

**`record` 会自动生成这五个组件的访问器，可见性是 `public`。**
但因为 `Memento` 本身是 `private`，外部**看不见这个类型**，所以访问器也用不了。

**重写 `toString()` 是必须的**（§4.2 已讲）：默认版本会把 `text` 打出来。

再看一遍那个 `toString()`：

```java
return "Memento{" + metadata.display() + "}";
```

输出形如：

```text
Memento{#3 输入 · 补「预计明天送达」 · 09:00:01.500}
```

**没有任何正文内容。** 这一行可以安全地进日志。

#### 5.6.8 删除或合并 `Editor` 会怎样

**删掉 `Editor`（把状态放到 `SnapshotHistory` 里）**：

那就没有 Originator 了。`SnapshotHistory` 会变成一个"既是保管者又是被保管对象"的怪物，
职责二合一，模式消失。

**把 `Memento` 提到顶层（变成 public）**：

回到原项目的 `public record DocumentMemento`，P4 / P5 全部回来。

**把 `snapshot()` 移到 `SnapshotHistory` 里**：

```java
// 在 History 里
public Snapshot snapshot(Editor editor) {
    return new Memento(editor.text(), editor.cursor(), ...);      // ← 需要三个 getter
}
```

这就成了 §3.2 说的"外部快照"，需要 `Editor` 暴露全部字段，
而且 `Memento` 必须是 `public` 的 —— 两道封装同时失守。

**把 `restore()` 移到 `SnapshotHistory` 里**：

同样需要 setter。而 `Editor` 的 `text` / `cursor` / `selection`
三者的一致性靠 `replaceRange` 维护，外面直接 set 会破坏不变式。

**这一条尤其重要**：`restore` 不是"把字段一个个赋回去"那么简单，
它必须维护"光标在选区末尾"这个不变式。

`Memento` 存的是**同一时刻**的 `cursor` 和 `selection`，所以一起赋回去就满足不变式。
如果外部只能一个一个 set，中间就会出现不一致的瞬间。

> **状态的快照必须"整体"恢复，不能"逐个字段"恢复。**
> 这是 `restore(Snapshot)` 这个方法签名的根本理由。

---
### 5.7 `SnapshotRejectedException.java`（40 行）：归属校验失败

**角色：** 一个只为一个场景服务的异常类型。

#### 完整代码

```java
public class SnapshotRejectedException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public SnapshotRejectedException(String message) {
        super(message);
    }
}
```

#### 为什么单独一个类型

**（1）能被精确捕获**

```java
try {
    editor.restore(snapshot);
} catch (SnapshotRejectedException e) {
    // 只有"串档"会到这里
    log.warn("非法快照，已忽略", e);
    return;
}
```

如果抛的是 `IllegalArgumentException`，那"参数是 null"、"选区越界"、
"正文超长" 全都会进同一个 `catch` —— 于是"串档"这种**安全级别的问题**
会和"参数写错"这种**普通 bug**混在一起处理。

**（2）日志里一眼能看出来**

```text
  ❌ IllegalArgumentException: 拒绝恢复：#3 ...
  ✅ SnapshotRejectedException: 拒绝恢复：#3 ...
```

第二种在日志平台上一搜就能统计"这个系统一个月发生了多少次串档尝试"。

**（3）为什么继承 `IllegalArgumentException` 而不是 `RuntimeException`**

因为它仍然是**编程错误**，不是**业务异常**。

- `IllegalArgumentException` 的语义是"你传了个不该传的参数" —— 完全吻合；
- 如果直接继承 `RuntimeException`，捕获它的代码就得自己判断"这是不是业务错误"。

**继承链本身就是一种文档。**

#### 为什么不需要 `serialVersionUID`

```java
private static final long serialVersionUID = 1L;
```

严格来说，如果这个异常**会被序列化**（比如通过网络传输、
或者被某个 RPC 框架包装），就需要它。否则 `javac -Xlint:serial` 会警告。

本项目加了它，是因为 `-Xlint:all` 会检查这个。
这是一个"零警告"要求带来的必要劳动。

#### 删掉它会怎样

`Editor.restore` 里抛 `IllegalArgumentException`：

```java
throw new IllegalArgumentException("拒绝恢复：... 不是本编辑器产生的快照");
```

功能一样（`SnapshotRejectedException` 本来就是它的子类），
但调用方**无法只捕获"串档"这一类**。

#### 合并到 `Editor` 里会怎样

```java
public final class Editor {
    public static class SnapshotRejectedException extends IllegalArgumentException { }
}
```

可行，但会让 `Editor` 的文件更长（它已经 334 行了），
而且异常类型是**跨类使用**的（`SnapshotHistory` 也可能需要它）。

**判断标准：只在"一个类的内部实现"里用到的类型，才值得嵌套。**

---

### 5.8 `SnapshotHistory.java`（287 行）：Caretaker

**角色：** 保管快照、决定何时拍、何时丢。

这是全项目**结构最复杂**的文件，也是最值得逐段读的一个。
它同时承载了"修正原项目 P1/P2/P3/P8/P9/P10 六条缺陷"的职责。

#### 5.8.1 字段与构造器

```java
public final class SnapshotHistory {

    private final Editor editor;
    private final int maxDepth;
    private final Supplier<Instant> clock;

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();

    private final List<HistoryListener> listeners = new ArrayList<>();

    private int sequence;
    private int evicted;
    private int recordedEdits;
```

**六类字段，各有来源：**

| 字段 | 来自 | 可变吗 |
|---|---|---|
| `editor` | 构造器 | `final` |
| `maxDepth` | 构造器 | `final` |
| `clock` | 构造器 | `final` |
| `undo` | 内联初始化 | `final`（容器本身不可变，内容可变） |
| `redo` | 内联初始化 | 同上 |
| `listeners` | 内联初始化 | 同上 |
| `sequence` / `evicted` / `recordedEdits` | — | 可变计数器 |

**注意 `undo` 的声明类型是 `Deque<Snapshot>`，不是 `ArrayDeque`。**

```java
private final Deque<Snapshot> undo = new ArrayDeque<>();
```

**为什么用接口类型而不是具体类型？**

因为这样将来换成 `LinkedList` 或自定义实现时，改一行即可。

但**反过来说**：如果确定永远不会换，直接用 `ArrayDeque` 也没错 ——
而且能用到 `removeLast()` 这类 `Deque` 也有的方法，所以没差别。

**本项目用 `Deque`，是因为它是一个"更窄的契约"**：
`Deque` 没有 `get(int)`，从声明上就告诉读者"这里只能头尾操作"。

#### 构造器

```java
public SnapshotHistory(Editor editor, int maxDepth, Supplier<Instant> clock) {
    this.editor = Objects.requireNonNull(editor, "editor");
    this.clock = Objects.requireNonNull(clock, "clock");
    EditorLimits.requirePositive(maxDepth, "maxDepth");
    if (maxDepth < 2) {
        throw new IllegalArgumentException("撤销深度至少为 2，实际 " + maxDepth);
    }
    if (maxDepth > EditorLimits.MAX_UNDO_DEPTH) {
        throw new IllegalArgumentException(
                "撤销深度不能超过 " + EditorLimits.MAX_UNDO_DEPTH + "，实际 " + maxDepth);
    }
    this.maxDepth = maxDepth;
    this.undo.push(capture(EditKind.BASELINE, "打开编辑器，正文 " + editor.length() + " 字"));
}
```

**四道校验，从宽到严：**

1. `editor` / `clock` 不为 null；
2. `maxDepth > 0`（`requirePositive`）；
3. `maxDepth >= 2`；
4. `maxDepth <= 100`。

**为什么 `maxDepth` 至少是 2？**

因为栈底那一张是基线。如果 `maxDepth == 1`：

```text
  构造：undo = [基线]             size = 1，刚好
  edit：undo = [基线, 快照]       size = 2 > 1 → trim()
  trim：移除最老的（基线）        undo = [快照]      ← 基线没了
```

基线一旦被淘汰，`undo.size() <= 1` 的判断就会**错误地**阻止最后一步撤销。

**这是一个"参数范围导致不变式破裂"的典型例子。**

不加这个检查，代码在 `maxDepth == 1` 时**不会崩**，只会行为怪异 ——
而"不崩但行为怪异"比"崩"更难查。

**为什么上限是 100？**

§4.11 算过账：一份 400KB 的文档，20 步就是 8MB，100 步就是 40MB。
100 是一个"再大就该考虑差量或落盘"的分界线。

**注意：这里有两个 `EditorLimits` 常量被用到：**
- `requirePositive`（校验）
- `MAX_UNDO_DEPTH`（上限）

而 `DEFAULT_UNDO_DEPTH = 20` **没有被 `SnapshotHistory` 用到**。

那它给谁用？

**给 `Client`**（或者说，给"创建 `SnapshotHistory` 的那个地方"）：

```java
private static SnapshotHistory history(Editor editor, int depth) {
    return new SnapshotHistory(editor, depth, clock());
}
```

本项目 `Client` 里传的是显式的 `10` / `3`，没有用 `DEFAULT_UNDO_DEPTH`。

**这是一个"预留常量"**，和 `TextRegion.shift()` 一样，属于"为将来准备"。
诚实地说：**如果将来用不上，它就是个死代码。**

保留的理由：它是"默认策略"的一部分，去掉之后，
每个调用点都要自己决定填几 —— 而 20 是一个有依据的经验值。

#### 5.8.2 `edit()`：把一段操作包成原子单元

```java
public void edit(EditKind kind, String detail, Consumer<Editor> action) {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(action, "action");

    Snapshot before = capture(kind, detail);
    try {
        action.accept(editor);
    } catch (RuntimeException e) {
        editor.restore(before);
        throw e;
    }
    undo.push(before);
    trim();
    redo.clear();
    recordedEdits++;
    notifyChanged();
}
```

**七步，逐个说。**

**第 1-3 步：三个 `requireNonNull`**

为什么 `detail` 也要非 null？

因为 `SnapshotMetadata` 的紧凑构造器会把 `null` 归一化成 `""`：

```java
detail = detail == null ? "" : detail;
```

那这里为什么还要拦？

**因为它拦的是"调用方传了 null"这个编程错误，而不是为了防御。**

如果 `edit(null, null, action)` 能跑通，那 `EditKind` 为 null 时
会在 `capture` → `new SnapshotMetadata` 里抛 NPE，堆栈指向
`SnapshotMetadata` 而不是 `edit()`。

**在入口处抛，堆栈才指向真正出错的地方**（同 §5.6.3 的道理）。

**第 4 步：`capture(kind, detail)` —— 在 `action` 之前拍**

这是整个设计的关键。顺序必须是：

```text
  拍快照  →  执行修改
```

不能反过来。因为快照要记录的是"修改**之前**的状态"。

如果反过来：

```java
action.accept(editor);
Snapshot after = capture(...);      // ← 拍到的是修改后的状态
undo.push(after);
```

那撤销时恢复的就是"修改后的状态"，等于什么都没撤。

**这是一类非常容易写反的 bug，而且测试很容易漏掉**（因为第一次撤销看起来"没反应"，
用户会再点一次，然后发现退了两步）。

**第 5-7 步：`try / catch / restore / throw`**

```java
try {
    action.accept(editor);
} catch (RuntimeException e) {
    editor.restore(before);
    throw e;
}
```

**这是"失败自动回滚"，也是原项目完全没有的能力。**

原项目的失败路径：

```text
  setContent("新内容")     ← 改了
  （中间抛异常）
  pushMemento 没执行       ← 历史里没有这次修改
  → 文档变了，但没人记得它变过
```

用户下一次撤销，会从"更早的状态"直接跳到"当前状态"，
中间那次失败的修改**永久丢失且无人知晓**。

本项目的失败路径：

```text
  capture(before)          ← 先拍
  action 抛异常
  restore(before)          ← 恢复
  throw e                  ← 异常照常抛给调用方
  （错误处理里的 return 使得 undo.push 不会执行）
```

**两个关键点：**

1. `restore(before)` 用的是 `Editor.restore`，它会完整恢复 `text` / `cursor` / `selection`
   三者 —— 所以不会出现"文字回滚了但光标没回滚"；
2. `throw e` 让异常**继续传播** —— 本方法只负责"清理现场"，不负责"吞掉错误"。

**为什么不 `catch (Throwable t)`？**

因为 `Error`（`OutOfMemoryError`、`StackOverflowError`）不该被业务代码捕获。
在内存耗尽时还去 `restore` 一个快照，只会让情况更糟。

**这是个重要的区分：**
- `Exception` → 可以恢复，可以回滚；
- `Error` → 不该触碰，让进程自己处理。

**Demo 3 的真实输出验证了这条路径：**

```text
  正常编辑后 → 「原稿：本条款有效，自签署日起生效」
  失败前 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1
  捕获 IllegalStateException：缺少签署方，不允许替换
  失败后 → 「原稿：本条款有效，自签署日起生效」
  失败后 可撤 1 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 1
  正文回到进门前？true
  这篇快照没有入栈，所以「可撤」步数没变
  「已拍」多了 1：快门按过了，只是底片没进相册
```

**注意 `已拍` 从 2 变成 3，但 `可撤` 一直是 1。**

这就是 `sequence` 与 `undo.size()` 的语义差别：

- `sequence` = **快门按了几次**（含失败）；
- `undo.size()` = **相册里有几张**。

**这个差别在 Demo 3 里被特意展示出来，因为它很容易让人困惑：**
"为什么失败了一次，编号却跳了一个？"

答案：编号记录的是"拍摄次数"，失败的那张被丢掉了。

#### 5.8.3 `undo()`：弹出 + 恢复，一步到位

```java
public boolean undo() {
    if (undo.size() <= 1) {
        return false;
    }
    Snapshot target = undo.pop();
    redo.push(capture(EditKind.UNDO, "准备回到 #" + target.metadata().sequence()));
    editor.restore(target);
    notifyChanged();
    return true;
}
```

**五行核心逻辑，对应原项目三处缺陷的修正：**

| 行 | 修正了什么 |
|---|---|
| `if (undo.size() <= 1) return false;` | P1/P3：不用 null 表达"没有更多" |
| `editor.restore(target);` 在方法**内部** | P2/P9：不可能"只弹栈不恢复" |
| `redo.push(capture(...))` 在恢复**之前** | 见下面 |

**为什么 `redo.push(capture(...))` 要在 `restore` 之前？**

因为 `capture` 拍的是**当前状态**（也就是"撤销前"的状态），
而 `restore(target)` 会把它改掉。

顺序写反：

```java
editor.restore(target);                                    // ← 先恢复
redo.push(capture(EditKind.UNDO, "..."));                  // ← 拍到的是 target 自己
```

于是重做时，恢复的是 `target` 本身 —— **重做没有任何效果**。

**这是一个"两个操作共享同一份可变状态"时的顺序陷阱。**

**`undo()` 和 `redo()` 的完全对称性**

把两个方法并排放：

```java
public boolean undo() {
    if (undo.size() <= 1) return false;
    Snapshot target = undo.pop();
    redo.push(capture(EditKind.UNDO, ...));    // 备份"当前"
    editor.restore(target);                     // 恢复到 target
    notifyChanged();
    return true;
}

public boolean redo() {
    if (redo.isEmpty()) return false;
    Snapshot target = redo.pop();
    undo.push(capture(EditKind.REDO, ...));     // 备份"当前"
    editor.restore(target);                     // 恢复到 target
    notifyChanged();
    return true;
}
```

**两者结构完全相同，只有两处差异：**

| 差异 | `undo` | `redo` |
|---|---|---|
| 判空条件 | `undo.size() <= 1`（要留基线） | `redo.isEmpty()`（不需要留） |
| 从哪个栈取 / 往哪个栈放 | `undo` → `redo` | `redo` → `undo` |

**这个对称性不是巧合，它是"撤销和重做本质是同一个操作"的体现：**

> **撤销和重做，都是"备份当前状态，然后恢复到目标状态"。
> 唯一的区别是"目标"从哪个栈里拿。**

原项目的两个方法**不对称**：

```java
public DocumentMemento popMemento() {
    DocumentMemento current = history.pop();
    redoStack.push(current);
    return history.peek();        // ← 返回"上一个"，但没恢复
}

public DocumentMemento popRedoMemento() {
    DocumentMemento memento = redoStack.pop();
    history.push(memento);
    return memento;               // ← 返回"要恢复到的"，但也没恢复
}
```

**两个方法都只做了"备份 + 取目标"，都没做"恢复"。**
于是"恢复"这一步被复制到了每一个调用点 ——
而只要有一处漏写，就是 P2/P9。

**本项目把"恢复"收进了方法内部，于是调用点不可能漏。**

#### 5.8.4 `reset()`：保存后重建基线

```java
public void reset(String label) {
    Objects.requireNonNull(label, "label");
    undo.clear();
    redo.clear();
    undo.push(capture(EditKind.BASELINE, label));
    notifyChanged();
}
```

**为什么需要这个方法？**

因为真实产品里有一个必然的场景：**用户点了"保存"**。

保存之后，用户心理上的"最早版本"变了：

```text
  保存前：  [打开] → 改1 → 改2 → 改3        ← 撤销栈底是"打开时的状态"
  保存后：  [保存时的状态] → 改4 → 改5      ← 栈底应该变成"保存时的状态"
```

如果不 `reset`，用户保存之后再撤销，会一路退回到"打开时的状态" ——
**退回到一个他已经明确接受过的版本之前。**

**注意 `reset` 之后 `sequence` 不重置。**

```java
undo.push(capture(EditKind.BASELINE, label));    // sequence 继续递增
```

为什么？

因为 `sequence` 是"这场会话里拍第几张"的全局计数。
如果重置回 1，就会和历史面板上已经显示过的编号冲突。

**这是一次"计数器不归零"的取舍。** 代价是编号会出现跳跃
（`#7` 之后直接是 `#12`），收益是编号在整场会话里唯一。

本项目没有在 Demo 里演示 `reset()`，因为它不产生额外的教学价值 ——
但它在真实产品里是必需的。

#### 5.8.5 `stats()` 与监听器

```java
public HistoryStats stats() {
    return new HistoryStats(undo.size(), redo.size(), sequence, evicted, recordedEdits);
}

public void addListener(HistoryListener listener) {
    listeners.add(Objects.requireNonNull(listener, "listener"));
}

public void removeListener(HistoryListener listener) {
    listeners.remove(listener);
}
```

**`stats()` 每次都新建一个 `HistoryStats`。**

这意味着调用方拿到的是一份**不可变的值快照**：

```java
HistoryStats before = history.stats();
history.edit(...);
HistoryStats after = history.stats();
// before 不会被 after 影响 —— 它是一个独立的 record
```

如果 `stats()` 返回的是 `this`（让 `SnapshotHistory` 自己实现 stats 接口），
那 `before` 和 `after` 会是同一个对象 ——
Demo 3 里那段代码就失效了：

```java
HistoryStats before = h.stats();
// ... 失败的一次 edit ...
HistoryStats after = h.stats();
System.out.println("撤销栈深度未变？" + (before.undoDepth() == after.undoDepth()));
// 如果 before == after，这行永远打印 true，什么都测不出来
```

**"统计"必须是一个值，不能是一个视图。**

**`removeListener` 不需要 `requireNonNull`。**

因为 `List.remove(null)` 只会返回 `false`，不会抛异常。
加上 `requireNonNull` 只是让"传 null"这个错误更早暴露 —— 但这里是注销操作，
"没找到"是合法结果，不需要那么严格。

**这是一个有意的宽松，不是遗漏。**

#### 5.8.6 `capture()` 与 `trim()`

```java
private Snapshot capture(EditKind kind, String detail) {
    sequence++;
    SnapshotMetadata metadata = new SnapshotMetadata(sequence, kind, detail, clock.get());
    return editor.snapshot(metadata);
}

private void trim() {
    while (undo.size() > maxDepth) {
        undo.removeLast();
        evicted++;
    }
}
```

**`capture` 只做三件事：编号 + 元信息 + 让 `Editor` 造快照。**

它**不**负责入栈。入栈由调用方决定：

- `edit()` → 成功后才 `undo.push`；
- `undo()` → 立刻 `redo.push`；
- `redo()` → 立刻 `undo.push`；
- 构造器 / `reset()` → `undo.push`。

**为什么把"造"和"存"分开？**

因为 `edit()` 需要"先造、失败就丢"的语义。
如果 `capture` 内部就入栈了，失败时还得"再弹出来" ——
而"弹出"会破坏 `evicted` 等计数。

**"造"是无副作用的（除了 `sequence++`），"存"是调用方的决策。**

**`trim()` 用 `while` 而不是 `if`。**

因为 `maxDepth` 可能被"一次加很多张"的场景突破 ——
虽然本项目没有这种场景（每次只 push 一张），但 `while` 是**更安全的写法**，
它不依赖"每次只多一张"这个隐含前提。

**`evicted++` 在循环内。**

淘汰 2 张就 +2 —— 这与 Demo 4 的输出一致（`淘汰 2`）。

#### 5.8.7 `notifyChanged()`

```java
private void notifyChanged() {
    if (listeners.isEmpty()) {
        return;
    }
    HistoryStats stats = stats();
    for (HistoryListener listener : List.copyOf(listeners)) {
        listener.onHistoryChanged(stats, editor);
    }
}
```

**两个优化 / 防御：**

1. **`if (listeners.isEmpty()) return;`** —— 没有监听器时，连
   `stats()` 都不用算。这是一个很小的优化，但它在**最热的路径**上
   （每次编辑都会走）。
2. **`List.copyOf(listeners)`** —— 防止监听器在回调里增删自己
   （§4.13 已讲）。

**注意 `stats` 只算一次，在循环外。**

如果写成：

```java
for (HistoryListener listener : listeners) {
    listener.onHistoryChanged(stats(), editor);      // ← 每个监听器都重算一次
}
```

功能一样，但每个监听器会拿到**不同的** `HistoryStats` 对象
（虽然值相同）。更糟的是如果某个监听器在回调里改了历史，
后面的监听器就会拿到不同时刻的统计 —— **同一次通知，多个监听器看到不同的世界**。

**"一次通知 = 一份一致的快照"，这需要在循环外算好。**

#### 5.8.8 删除或合并 `SnapshotHistory` 会怎样

**删掉它（让 `Editor` 自己管历史）**：

就回到了 §4.7 说的双向依赖问题。而且 `Editor` 会变成一个 600 行的怪物。

**把它做成单例**：

```java
public static SnapshotHistory getInstance() { ... }
```

那就会重新出现 P10（跨文档污染）—— 而且是**更严重的版本**，
因为单例是全局的。

**把 `undo` / `redo` 两个栈合并成一个"历史列表 + 当前指针"**：

```java
List<Snapshot> history;
int currentIndex;
```

这是另一种常见设计（浏览器历史、Photoshop 的 History 面板就是这样）。
优点是：可以在历史面板上**点任意一条跳过去**。

缺点是：

- `undo` / `redo` 要同时移动指针和恢复状态，逻辑更复杂；
- 容量上限的淘汰要处理"指针越界"（如果淘汰了指针前面的条目，索引要跟着减）；
- 新编辑要 `subList(currentIndex + 1, size).clear()`。

**本项目选双栈，是因为它和"撤销/重做"这两个操作的语义一一对应。**

单列表的版本更适合"历史面板可以直接点"的产品。
这是一个**产品需求决定数据结构**的例子。

---

### 5.9 `HistoryStats.java`（74 行）：撤销面板的数字

**角色：** 一份不可变的统计快照。

#### 完整结构

```java
public record HistoryStats(int undoDepth, int redoDepth, int snapshotsTaken,
                           int evicted, int edits) {

    public HistoryStats {
        if (undoDepth < 0 || redoDepth < 0 || snapshotsTaken < 0 || evicted < 0 || edits < 0) {
            throw new IllegalArgumentException("统计值不能为负");
        }
    }

    public boolean canUndo() { return undoDepth > 1; }
    public boolean canRedo() { return redoDepth > 0; }

    public String summary() {
        return "可撤 " + Math.max(0, undoDepth - 1) + " 步"
                + " | 可重 " + redoDepth + " 步"
                + " | 已拍 " + snapshotsTaken + " 淘汰 " + evicted + " 编辑 " + edits;
    }
}
```

#### `canUndo()` 为什么是 `> 1` 而不是 `> 0`

因为栈底永远有一张（基线或"最早保留的版本"）。

```java
undo == [基线]                → size = 1 → canUndo = false ✓
undo == [基线, 快照1]         → size = 2 → canUndo = true  ✓
undo == [基线, 快照1, 快照2]  → size = 3 → canUndo = true  ✓
```

**这个 1 的偏移，必须和 `SnapshotHistory.undo()` 里的判断完全一致。**

如果两处写得不一致：

```java
// HistoryStats
public boolean canUndo() { return undoDepth > 0; }

// SnapshotHistory
if (undo.size() <= 1) return false;
```

那么界面上"撤销"按钮是亮的（`canUndo() == true`），
用户点了却没有任何反应（`undo()` 返回 `false`）。

**这是"同一份规则写在两个地方"的经典 bug。**

本项目的处理方式：**两处都写 `undoDepth > 1` / `size <= 1`，
并在 javadoc 里互相引用。**

```java
/**
 * <p>这个判断必须和 {@link SnapshotHistory#undo()} 里的判断始终一致，
 * 所以两边引用同一处代码。
 */
public boolean canUndo() {
    return undoDepth > 1;
}
```

**更彻底的做法是让 `SnapshotHistory.undo()` 调用 `stats().canUndo()`：**

```java
public boolean undo() {
    if (!stats().canUndo()) return false;
    // ...
}
```

**本项目没有这么做**，因为它每次都要新建一个 `HistoryStats` 对象 ——
在热路径上多一次分配。

**这是一个"正确性 vs 性能"的取舍，而本项目的选择有代价：**
两处规则靠注释维持一致，改一处忘了另一处仍然会出 bug。

> 如果这是一个真实的核心系统，我会选择 `stats().canUndo()`。
> 教学项目里选择"注释 + 保持一致"，是为了让两个判断**并排可见**，
> 方便读者对照。

#### `summary()` 里为什么是 `undoDepth - 1`

```java
"可撤 " + Math.max(0, undoDepth - 1) + " 步"
```

因为**用户关心的是"我要按几次"，不是"内部堆了几个对象"**。

`undoDepth == 3` 时，用户能按 2 次撤销（撤到基线为止）。

如果用 `undoDepth`：

```text
  可撤 3 步          ← 用户按 3 次，第 3 次没反应
```

用 `undoDepth - 1`：

```text
  可撤 2 步          ← 用户按 2 次，正好到底
```

**`Math.max(0, ...)` 是防御性的。**

理论上 `undoDepth` 至少是 1（构造器里就 push 了基线），
所以 `undoDepth - 1 >= 0`。但在 `reset()` 执行到一半
（`undo.clear()` 之后、`undo.push()` 之前）如果被读到，就会是 0。

本项目是单线程的，这种窗口不存在。但 `Math.max` 是一行代码，
**它让这个 record 在任何输入下都不会打出"可撤 -1 步"**。

#### `summary()` 放在 record 里合适吗

如 §5.2 所说：严格来说，显示格式属于 UI 层。

这里放在 record 里的理由是：**它是唯一的格式定义点**，
且 `ConsoleHistoryListener` 和 `Client` 都要用。

**如果这个 record 要被多个模块使用，`summary()` 应该移出去。**

#### 删掉它会怎样

监听器只能接收 `SnapshotHistory`：

```java
void onHistoryChanged(SnapshotHistory history);
```

那就是 §4.13 说的"监听器能改历史"的问题。

---

### 5.10 `HistoryListener.java`（42 行）：一个方法的接口

**角色：** 历史变更通知。

#### 完整代码

```java
@FunctionalInterface
public interface HistoryListener {
    void onHistoryChanged(HistoryStats stats, Editor editor);
}
```

#### `@FunctionalInterface` 的作用

1. **编译期检查**：如果接口有超过一个抽象方法，编译失败；
2. **文档作用**：告诉读者"这里可以用 lambda"。

```java
history.addListener((stats, editor) ->
        System.out.println("变化了：" + stats.summary()));
```

**本项目没有用 lambda 注册**，用的是 `new ConsoleHistoryListener("面板")` ——
因为要带一个 `prefix` 状态。

#### 为什么参数是 `(HistoryStats, Editor)` 而不是 `(SnapshotHistory)`

§4.13 已完整论证。这里补一个**反例的后果**：

```java
void onHistoryChanged(SnapshotHistory h) {
    // 某个监听器实现
    if (h.stats().canUndo()) {
        h.undo();                       // ← 在通知回调里撤销
    }
}
```

`undo()` 会调用 `notifyChanged()` → 又回调这个监听器 → 又 `undo()` →
**栈溢出**。

而 `(HistoryStats, Editor)` 这个签名，让"撤销"这个动作**写不出来** ——
`HistoryStats` 上没有 `undo()`。

**这就是"用类型而不是用约定来防止误用"。**

#### 删掉它会怎样

`SnapshotHistory` 只能用三种方式通知外界：

1. 返回 `HistoryStats`，让调用方每次操作后自己取（回到"靠自觉"）；
2. 抛事件（Java 没有内建的事件机制）；
3. 用一个 `List<Runnable>`（丢失了"通知什么"的信息）。

第 1 种就是原项目的路数，会出 P2 的问题。

---

### 5.11 `ConsoleHistoryListener.java`（36 行）：控制台撤销面板

**角色：** `HistoryListener` 的一个实现，把历史变化打到控制台。

#### 完整代码

```java
public final class ConsoleHistoryListener implements HistoryListener {

    private final String prefix;

    public ConsoleHistoryListener(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    @Override
    public void onHistoryChanged(HistoryStats stats, Editor editor) {
        System.out.println("      [" + prefix + "] " + stats.summary()
                + " | 正文 " + editor.length() + " 字");
    }
}
```

#### 为什么这个类只有 36 行

因为它**只做一件事**：把已有的数据格式化输出。

- `stats.summary()` 负责格式化统计；
- `editor.length()` 提供一个额外的观察量；
- 剩下就是字符串拼接和缩进。

**一个"配套设施"应该尽量薄。** 它薄，就说明核心类把自己的事情做完了。

#### 缩进 6 个空格是什么用意

看 Demo 1 的真实输出：

```text
  [编辑] 末尾补「已发货」
      [面板] 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1 | 正文 13 字
  正文 → 正文「客服：您好，您的订单已发货」 光标 13 选区 [13,13)
```

三个层次的缩进：

| 缩进 | 内容 | 来源 |
|---|---|---|
| 2 空格 | `[编辑]` / `正文` — **我（Client）主动打印的** | `Client` |
| 6 空格 | `[面板]` — **回调触发的**，是"编辑的副作用" | `ConsoleHistoryListener` |
| 4 空格 | `undo#1 = ...` — 撤销循环的输出 | `Client` |

**缩进在这里承担了"调用栈深度"的可视化职责**：
6 空格的行是在 `history.edit(...)` **内部**被触发的，
2 空格的行是在 `edit` **返回之后**打印的。

**这是一个纯展示层的设计，但它让"回调发生在什么时候"变得肉眼可见。**

#### 为什么 `prefix` 可配置

```java
new ConsoleHistoryListener("面板")
```

因为真实场景里可能挂多个监听器：

```java
history.addListener(new ConsoleHistoryListener("面板"));      // UI
history.addListener(new ConsoleHistoryListener("埋点"));      // 上报
```

没有前缀的话，两路日志混在一起，分不清谁是谁。

**这是一个"小成本、高收益"的设计。** 如果写死 `"[面板]"`，
第二个人想复用这个类时就得复制一份改字符串。

---

### 5.12 `SteppedClock.java`（60 行）：可复现的时间源

**角色：** `Supplier<Instant>` 的一个实现，每调一次前进一个固定步长。

#### 完整代码

```java
public final class SteppedClock implements Supplier<Instant> {

    private final Instant base;
    private final Duration step;
    private final AtomicLong ticks = new AtomicLong();

    public SteppedClock(Instant base, Duration step) {
        this.base = Objects.requireNonNull(base, "base");
        this.step = Objects.requireNonNull(step, "step");
        if (step.isZero() || step.isNegative()) {
            throw new IllegalArgumentException("步长必须为正，实际 " + step);
        }
    }

    @Override
    public Instant get() {
        return base.plus(step.multipliedBy(ticks.getAndIncrement()));
    }
}
```

#### `get()` 这一行拆开看

```java
base.plus(step.multipliedBy(ticks.getAndIncrement()))
```

执行顺序（Java 求值从左到右，但**方法调用的参数先求值**）：

1. `ticks.getAndIncrement()` —— 返回当前值，然后 +1。
   第一次调用返回 `0`，第二次 `1`……
2. `step.multipliedBy(0)` —— `Duration.ZERO`
3. `base.plus(Duration.ZERO)` —— `base` 本身

所以第一次调用返回 `base`，第二次返回 `base + step`，以此类推。

**这与 `SnapshotMetadata` 的时间戳语义一致：**
第一张快照（基线）的时间就是 `base`，Demo 5 里的 `09:00:00.000` 证实了这一点：

```text
  a 自己拍一张 → #999 输入 · 手工拍的 · 09:00:00.000
```

#### 为什么用 `AtomicLong` 而不是 `long`

单线程的 Demo 用 `long` 完全够。

用 `AtomicLong` 有两个理由：

1. **它不影响任何代码的复杂度**（只是把 `ticks++` 换成 `ticks.getAndIncrement()`）；
2. **它传达了一个信号**："这个时钟可以被并发调用"。

第 2 点更重要。如果写成 `long`，将来有人把 `SnapshotHistory` 改成多线程，
就会在这里踩一个**数据竞争**的坑 —— 而且极难复现（`ticks++` 不是原子操作）。

**用一个稍重的类型，换掉一个未来的隐患。** 这里非常划算。

#### 为什么 `step` 必须为正

```java
if (step.isZero() || step.isNegative()) {
    throw new IllegalArgumentException("步长必须为正，实际 " + step);
}
```

如果允许 `step == 0`，所有快照的时间戳会完全相同 ——
"按时间排序"、"多久之前"这些功能全部失效，而且**没有任何报错**。

如果允许负步长，时间会倒退 —— 这在语义上荒谬。

**这是一个"参数本身没有意义"的校验。**

#### 删掉它会怎样

`SnapshotHistory` 可以接受 `Instant::now`：

```java
new SnapshotHistory(editor, 20, Instant::now)
```

功能完全正常，但：

- Demo 输出无法复现（时间戳每次都不同）；
- 无法验证"三次运行哈希一致"；
- 无法写"按时间合并快照"这类测试。

**这 60 行代码的价值，全在"让输出可复现"这一个点上。**
在那个点上，它无可替代。

---

### 5.13 `Client.java`（271 行）：六段 Demo

**角色：** 入口。六段场景，每段验证一个关键决定。

#### 六段一览

| Demo | 验证什么 | 关键输出 |
|---|---|---|
| 1 | 撤销 / 重做的完整闭环 + 面板实时刷新 | 三次撤销 + 两次重做，全部 `true` |
| 2 | 一段操作 = 一步撤销 | 内部 5 次 `type()`，外部只需撤 2 次 |
| 3 | 编辑中途失败自动回滚 | 正文回滚、快照不入栈、`已拍` +1 |
| 4 | 容量上限淘汰最老快照 | `淘汰 2`，只能撤到第 2 版 |
| 5 | 快照不可伪造、不可串档 | `b.restore(a 的)` 被拒 |
| 6 | 撤销到底 + 新编辑清空重做栈 | `undo` 返回 `false`、`redo` 返回 `false` |

#### Demo 1 的调用顺序为什么这么写

```java
System.out.println("  [编辑] 末尾补「已发货」");
history.edit(EditKind.TYPING, "末尾补「已发货」", e -> e.type("已发货"));
System.out.println("  正文 → " + editor.describe());
```

**三行，对应三个阶段：**

1. **意图**（`[编辑]`）—— 在 `edit()` 之前打印；
2. **执行**（`history.edit(...)`）—— 中间会触发监听器，打出 `[面板]` 行；
3. **结果**（`正文 →`）—— 在 `edit()` 之后打印。

如果顺序写成"先编辑、后打印意图"：

```java
history.edit(...);
System.out.println("  [编辑] ...");      // ← 这句话出现在结果之后，逻辑颠倒了
```

输出会变成"先看到面板、再看到结果、最后看到意图"，读起来很乱。

**Demo 的顺序设计，本身就是在展示"edit() 的三个阶段"。**

#### Demo 2 为什么"只敲 2 次 edit"

```java
history.edit(EditKind.TYPING, "一次敲完「合规审核」四个字", e -> {
    for (char c : "合规审核".toCharArray()) {
        e.type(String.valueOf(c));
    }
});
```

**这是一个完整的 for 循环，但它只产生一张快照。**

这正是 `edit()` 的设计目的：**快照的粒度由 `edit()` 的调用边界决定，
不由内部 `type()` 的调用次数决定。**

对比原项目，如果要在原项目里实现"4 个字算一步"，只能：

```java
// 原项目的做法：先改完，再存一次
for (char c : "合规审核".toCharArray()) {
    document.setContent(document.getContent() + c);      // ← 改了 4 次
}
history.pushMemento(document.save());                     // ← 只存 1 次
```

**看起来也能做到。** 但区别在于：

- 原项目里，"4 次改 + 1 次存"这个配对关系**只存在于调用方的脑子里**；
- 本项目里，这个配对关系**由 `edit()` 的方法体保证**。

**能写对 ≠ 不会写错。** `edit()` 的价值在于让"写错"变得不可能。

#### Demo 3 的 `try / catch`

```java
try {
    history.edit(EditKind.REPLACE, "整段替换（中途校验失败）", e -> {
        e.replaceAll("整段替换后的内容");
        throw new IllegalStateException("缺少签署方，不允许替换");
    });
} catch (IllegalStateException ex) {
    System.out.println("  捕获 " + ex.getClass().getSimpleName() + "：" + ex.getMessage());
}
```

**注意：异常是 `edit()` 里的 `throw e` 抛出来的，不是 `action` 直接抛的。**

`action` 里抛的异常被 `catch (RuntimeException e)` 捕获 → 回滚 → `throw e` 重新抛出。

**所以调用方看到的堆栈里，会多一层 `SnapshotHistory.edit`：**

```text
  java.lang.IllegalStateException: 缺少签署方，不允许替换
      at Client.lambda$demo3$3(Client.java:...)      ← 真正抛的地方
      at Editor.replaceAll(...)
      at SnapshotHistory.edit(SnapshotHistory.java:...)   ← 回滚后重新抛出
      at Client.demo3RollbackOnFailure(Client.java:...)
```

**这一层不是噪音，它是证据** —— 它证明"错误经过了 Caretaker 的清理"。

#### Demo 4 的容量上限为什么取 3

```java
SnapshotHistory history = history(editor, 3);
```

因为 `3` 能在一屏内展示完整过程：

```text
  栈容量 3
  ├── edit1 后：[基线, #2]              size=2
  ├── edit2 后：[基线, #2, #3]          size=3
  ├── edit3 后：[#2, #3, #4]            size=3，淘汰基线（evicted=1）
  └── edit4 后：[#3, #4, #5]            size=3，淘汰 #2   （evicted=2）
```

如果取 10，要跑 11 次 `edit` 才能看到淘汰，输出会长到没法读。

**Demo 的参数选择，是为了"让现象在一屏内可见"。**

#### Demo 5 的 `Class.forName`

```java
Class<?> memento = Class.forName("com.patterns.behavioral.memento.Editor$Memento");
System.out.println("  备忘录的运行时类 = " + memento.getName());
System.out.println("  是 private？" + Modifier.isPrivate(memento.getModifiers()));
System.out.println("  是 record？" + memento.isRecord());
```

**为什么要用反射？**

因为在源码层面，`Editor.Memento` **根本写不出来**（这正是设计目的）。
要证明它存在、且是 `private` 的，只能用反射。

**注意 `"Editor$Memento"` 这个字符串里的 `$`。**

Java 编译器把嵌套类编译成 `外层类$内层类.class`。
这是一个**编译器的实现约定**，不是语言规范 —— 但所有 JVM 实现都这么干。

**Demo 里出现这个字符串，是为了让读者亲眼看到"嵌套类是独立的一个 class 文件"。**

这也解释了编译产物里为什么有 14 个 `.class` 而只有 13 个 `.java`：

```text
  CLASS FILES: 14
    ...
    Editor.class                              4506 bytes
    Editor$Memento.class                      2462 bytes      ← 多出来的这个
    ...
```

#### Demo 5 的 `b.restore(a 的)` 被拒

```java
Snapshot fromA = a.snapshot(new SnapshotMetadata(999, EditKind.TYPING, "手工拍的", clock().get()));
a.restore(fromA);                     // 成功
try {
    b.restore(fromA);                 // 被拒
} catch (SnapshotRejectedException ex) {
    System.out.println("  b.restore(a 的) 被拒 → " + ex.getMessage());
}
```

**这一段是本项目"修复过程"的活证据。**

第一版没有 `ownerToken` 时，这段代码的第二个分支打印的是：

```text
  b.restore(a 的) 竟然成功（不该发生）
```

**保留这个"曾经的失败"在 Demo 里，是有意的** ——
它告诉读者：这个坑是真的会踩到的，不是理论推演。

#### Demo 6 的 `while (history.undo())`

```java
int step = 0;
while (history.undo()) {
    step++;
    System.out.println("    第 " + step + " 次 undo = true → 「" + editor.text() + "」");
}
System.out.println("    再来一次 undo = " + history.undo() + " → 已到最早保留的版本「" + editor.text() + "」");
```

**用 `while` 而不是 `for (int i = 1; i <= N; i++)`，是因为"到底了"本身就是要验证的行为。**

`while` 循环自然终止，然后**再调一次** `undo()` 来展示 `false`。

**如果 `undo()` 会抛异常（像原项目那样 NPE），这个 `while` 会直接崩。**
它能跑通，本身就是"不再用 null 表达状态"的证据。

#### Client 的 `main` 为什么 `throws Exception`

```java
public static void main(String[] args) throws Exception {
```

因为 `demo5SnapshotCannotBeForged()` 里有 `Class.forName(...)`，
它抛受检异常 `ClassNotFoundException`。

**正常情况下，这个类一定存在**（它就在同一个 jar 里），
所以"抛 `ClassNotFoundException`"是一个**不可能发生**的分支。

用 `throws Exception` 而不是 `try / catch`，是因为：

```java
try {
    Class<?> memento = Class.forName("...Editor$Memento");
    // ...
} catch (ClassNotFoundException e) {
    throw new IllegalStateException("这个类一定存在，除非编译产物被改过", e);
}
```

第二种写法更"正确"（不吞掉受检异常），但多 4 行，
而且 `main` 里出现一个"理论上不可能"的 `catch`，会分散读者注意力。

**这是一个"教学清晰度 vs 严谨性"的取舍，本项目选了前者。**

生产代码里应该用第二种。

#### 删除 `Client` 会怎样

没有入口，Demo 跑不起来。

但更重要的是：**`Client` 是唯一一个"知道全部六个场景"的地方。**

`Editor` 不知道有 Demo，`SnapshotHistory` 不知道有 Demo，
`ConsoleHistoryListener` 只知道"每次变化要打一行"。

**每一段场景的"演员调度"都集中在 `Client` 里。** 这就是 Demo 层该有的样子。

---
## 6. 执行流程：六段 Demo 逐步拆解

前五章讲的是"为什么这么写"。这一章讲"跑起来到底发生了什么"。

每段 Demo 都按同一个套路拆：

1. **建了什么对象**（谁持有谁）；
2. **每一步调用了哪个方法**（方法链）；
3. **哪些字段变了**（栈里多了什么、少了什么）；
4. **打印出什么**（与真实输出逐行对照）。

---

### 6.0 三条阅读约定

#### 约定一：快照用 `#编号` 代称

每拍一张快照，`SnapshotHistory.capture()` 都会把 `sequence` 加一。
所以快照的编号就是**拍摄顺序**，而不是"成功入栈的顺序"。

这两者的区别在 Demo 3 会变得很关键：**有一次拍摄没有入栈，编号照样被消耗掉了**。

#### 约定二：栈画成横向

`ArrayDeque.push()` 压的是**队首**。为了不每次都写"队首/队尾"，本章统一画成：

```text
  undo = [ 最新 | 次新 | ... | 最老 ]
```

左边是栈顶（下一次 `pop()` 拿到的），右边是栈底。

`push(x)` 就是在左边加一个，`pop()` 就是拿走最左边那个，`removeLast()` 是拿走最右边那个。

#### 约定三：时间戳全部来自确定性时钟

每段 Demo 都调用一次 `Client.clock()`，**每次调用都新建一个 `SteppedClock`**：

```java
private static SteppedClock clock() {
    return new SteppedClock(Instant.parse("2025-03-14T09:00:00Z"), Duration.ofMillis(500));
}
```

于是每段 Demo 的时间轴都从 `09:00:00.000` 重新开始，每拍一张前进 500 毫秒：

| 第几次 `get()` | 时间戳 |
|---:|---|
| 1 | `09:00:00.000` |
| 2 | `09:00:00.500` |
| 3 | `09:00:01.000` |
| 4 | `09:00:01.500` |
| 5 | `09:00:02.000` |
| 6 | `09:00:02.500` |
| 7 | `09:00:03.000` |
| 8 | `09:00:03.500` |
| 9 | `09:00:04.000` |

**这张表在本章会反复用到。** 后面每条"快照清单"里的时间都能在这张表里查到。

（`Instant.parse("2025-03-14T09:00:00Z")` 里那个 `Z` 是 UTC，而 `SnapshotMetadata.TIME` 也固定用 `ZoneOffset.UTC` 格式化 —— 两边一致，所以打出来的就是 `09:00:00.000`，不会被机器时区改写。见 §5.2。）

---

### 6.1 Demo 1：撤销与重做，一次完整的往返

这是唯一一段**带监听器**的 Demo。它的作用是把"栈里发生了什么"实时打在屏幕上。

#### 6.1.1 建对象：三行代码，三个角色

```java
Editor editor = new Editor("客服：您好，您的订单");
SnapshotHistory history = history(editor, 10);
history.addListener(new ConsoleHistoryListener("面板"));
```

三行对应三个角色：

| 代码 | 造出来的对象 | 角色 |
|---|---|---|
| `new Editor(...)` | `editor` | **Originator**（能拍快照、能恢复） |
| `new SnapshotHistory(editor, 10, clock)` | `history` | **Caretaker**（保管快照） |
| `new ConsoleHistoryListener("面板")` | 匿名对象 | 观察者（只管打印，不参与决策） |

**注意第三行不是备忘录模式的一部分。** 它是本项目额外加的"撤销面板模拟器"。
没有它，Demo 只能看见"正文变成了什么"，看不见"栈里有几张快照"。

#### 6.1.2 构造 `SnapshotHistory` 时发生了什么

`new SnapshotHistory(editor, 10, clock())` 一进门就干了这些事（`SnapshotHistory.java:96-109`）：

```text
① editor != null ？        通过
② clock  != null ？        通过
③ maxDepth = 10 > 0 ？     通过
④ maxDepth = 10 >= 2 ？    通过
⑤ maxDepth = 10 <= 100 ？  通过
⑥ this.maxDepth = 10
⑦ undo.push(capture(BASELINE, "打开编辑器，正文 10 字"))
```

第 ⑦ 步展开：

```text
capture(BASELINE, "打开编辑器，正文 10 字")
  ├─ sequence++               → sequence = 1
  ├─ clock.get()              → 09:00:00.000      ← 时间轴第 1 次
  ├─ new SnapshotMetadata(1, BASELINE, "打开编辑器，正文 10 字", 09:00:00.000)
  └─ editor.snapshot(metadata)
       └─ new Memento("客服：您好，您的订单", 10, [10,10), metadata, ownerToken)
```

于是 `undo` 里有了第一张：

```text
  undo = [ #1 ]
```

**`#1` 就是基线。** 它的内容与"打开编辑器那一刻"完全一致 —— 因为它的确是在构造器里、
在任何编辑发生之前拍的。

此时打印：

```text
  初始 → 正文「客服：您好，您的订单」 光标 10 选区 [10,10)
```

- `describe()` 拼的是 `"正文「" + text + "」 光标 " + cursor + " 选区 " + selection`；
- `text` 长度 10（客1服2：3您4好5，6您7的8订9单10）；
- `cursor = text.length()`（构造器里设的）；
- `selection = TextRegion.caret(10)`，也就是 `[10,10)`。

#### 6.1.3 第一次编辑：一张快照，三种力量

```java
history.edit(EditKind.TYPING, "末尾补「已发货」", e -> e.type("已发货"));
```

`edit()` 的七步（`SnapshotHistory.java:133-151`），逐步跟踪：

**第 1-3 步：三个 `requireNonNull`** —— 全部通过。

**第 4 步：`capture(kind, detail)`**

```text
sequence++  → sequence = 2
clock.get() → 09:00:00.500          ← 时间轴第 2 次
→ #2 = Memento("客服：您好，您的订单", 10, [10,10), #2 元信息, ownerToken)
   局部变量 before = #2
```

**关键在时间点：`#2` 拍的是"键入之前"的状态。** 它和 `#1` 的内容**一模一样**，
但它们是两张不同的快照（编号不同、时间不同、类型不同）。

这正是 §5.8.2 说的那句"拍快照 → 执行修改，不能反过来"。如果反过来，
撤销时恢复的就是修改后的状态，等于什么都没撤。

**第 5 步：`action.accept(editor)`**

```java
e -> e.type("已发货")
```

`Editor.type()` 的实现在 `Editor.java:141-144`：

```java
public void type(String chunk) {
    Objects.requireNonNull(chunk, "chunk");
    replaceRange(selection.start(), selection.end(), chunk);
}
```

此时 `selection = [10,10)`，所以调用 `replaceRange(10, 10, "已发货")`：

```java
String next = text.substring(0, 10) + "已发货" + text.substring(10);
//                     "客服：您好，您的订单"            +  ""（substring(10) 是空串）
//          = "客服：您好，您的订单已发货"
this.text = EditorLimits.requireText(next);      // 长度 13，未超限
int caret = 10 + 3;                              // chunk.length() == 3
this.cursor = 13;
this.selection = TextRegion.caret(13);           // [13,13)
```

**注意 `replaceRange` 会把光标推到"插入内容的末尾"。** 这是一个很具体的设计选择：

- 在光标处插入 → 光标跟着往后走（和真实编辑器一致）；
- 有选区时输入 → 选区被替换掉，光标落在新内容末尾（也一致）。

如果 `replaceRange` 不更新光标，那么下一次 `type()` 还会从位置 10 插入，
结果就变成"已发货"倒着写 —— 这类 bug 在真实编辑器里很常见，
根因都是"改了正文但忘了改光标"。

**第 6 步：`undo.push(before)`**

```text
  undo = [ #2 | #1 ]
```

**第 7 步：`trim()` / `redo.clear()` / `recordedEdits++` / `notifyChanged()`**

```text
trim():           undo.size() = 2，maxDepth = 10，不淘汰
redo.clear():     redo 本来就是空的，什么也没做
recordedEdits++:  0 → 1
notifyChanged():  listeners 里有 1 个 → 构造 HistoryStats(2, 0, 2, 0, 1)
                  → 调 onHistoryChanged(stats, editor)
                  → 打印 "      [面板] 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1 | 正文 13 字"
```

`HistoryStats(2, 0, 2, 0, 1)` 的五个数字是：

| 字段 | 值 | 从哪来 |
|---|---:|---|
| `undoDepth` | 2 | `undo.size()`，含基线 |
| `redoDepth` | 0 | `redo.size()` |
| `snapshotsTaken` | 2 | `sequence`，累计拍摄次数 |
| `evicted` | 0 | 淘汰计数 |
| `edits` | 1 | 成功完成的 `edit()` 次数 |

`summary()` 里 `undoDepth` 被减了 1：

```java
"可撤 " + Math.max(0, undoDepth - 1) + " 步"
```

**为什么要减 1？** 因为 `undoDepth == 2` 时，用户实际只能按**一次**撤销。
栈里的两张快照 = "当前状态" + "上一步"。基线不是"可以撤销掉的东西"，
它是"撤到底之后停在哪"。见 §5.9 对 `canUndo()` 的解释。

#### 6.1.4 第二次、第三次编辑

同样七步，只是数字变了：

**第二次：`e.type("，预计明天送达")`**

`"，预计明天送达"` 是 7 个字符（，预计明天送达），所以 13 + 7 = 20。

```text
capture:        sequence = 3, 09:00:01.000
                #3 = Memento("客服：您好，您的订单已发货", 13, [13,13), ...)
action:         text = "客服：您好，您的订单已发货，预计明天送达"，cursor = 20
undo.push(#3):  undo = [ #3 | #2 | #1 ]
notify:         stats(3, 0, 3, 0, 2) → 可撤 2 步 | 已拍 3 | 编辑 2 | 正文 20 字
```

**第三次：`e.type("。")`**

```text
capture:        sequence = 4, 09:00:01.500
                #4 = Memento("客服：您好，您的订单已发货，预计明天送达", 20, [20,20), ...)
action:         text = "...预计明天送达。"，cursor = 21
undo.push(#4):  undo = [ #4 | #3 | #2 | #1 ]
notify:         stats(4, 0, 4, 0, 3) → 可撤 3 步 | 已拍 4 | 编辑 3 | 正文 21 字
```

此时真实输出（第 4-12 行）：

```text
  [编辑] 末尾补「已发货」
      [面板] 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1 | 正文 13 字
  正文 → 正文「客服：您好，您的订单已发货」 光标 13 选区 [13,13)
  [编辑] 补「预计明天送达」
      [面板] 可撤 2 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 2 | 正文 20 字
  正文 → 正文「客服：您好，您的订单已发货，预计明天送达」 光标 20 选区 [20,20)
  [编辑] 补句号
      [面板] 可撤 3 步 | 可重 0 步 | 已拍 4 淘汰 0 编辑 3 | 正文 21 字
  正文 → 正文「客服：您好，您的订单已发货，预计明天送达。」 光标 21 选区 [21,21)
```

**注意 `[面板]` 那行的位置：它夹在 `[编辑]` 和 `正文 →` 之间。**

这对应 `Client` 里的调用顺序：

```java
System.out.println("  [编辑] 末尾补「已发货」");              // ① 先打"要做什么"
history.edit(EditKind.TYPING, "末尾补「已发货」", e -> e.type("已发货"));  // ② 执行，回调里打 [面板]
System.out.println("  正文 → " + editor.describe());        // ③ 再打"变成了什么"
```

**这个顺序是刻意调的。** 第一版写反了（先 `edit` 再打 `[编辑]`），
读起来像"面板先于编辑刷新"，非常容易误读成异步 bug。

#### 6.1.5 第一次撤销：栈开始反向流动

```java
boolean ok = history.undo();
```

`undo()` 的实现（`SnapshotHistory.java:164-173`）只有四步，但每一步都很关键：

```java
if (undo.size() <= 1) {
    return false;                                   // ← 第 1 步：只剩基线，拒绝
}
Snapshot target = undo.pop();                       // ← 第 2 步：弹出目标
redo.push(capture(EditKind.UNDO, "准备回到 #" + target.metadata().sequence()));  // ← 第 3 步
editor.restore(target);                             // ← 第 4 步：恢复
notifyChanged();
return true;
```

跟踪：

```text
第 1 步：undo.size() = 4 > 1，继续

第 2 步：target = undo.pop() = #4
        undo = [ #3 | #2 | #1 ]

第 3 步：capture(UNDO, "准备回到 #4")
        sequence = 5, 09:00:02.000
        #5 = Memento(当前正文 "…预计明天送达。", 21, [21,21), ...)
        redo.push(#5)
        redo = [ #5 ]

第 4 步：editor.restore(#4)
        #4 里的 text 是 "客服：您好，您的订单已发货，预计明天送达"（20 字）
        #4 里的 cursor 是 20
        #4 里的 selection 是 [20,20)
        → 三个字段一起被覆盖

第 5 步：notifyChanged()
        stats(3, 1, 5, 0, 3)
        → 可撤 2 步 | 可重 1 步 | 已拍 5 淘汰 0 编辑 3 | 正文 20 字
```

**这里有一个必须讲清楚的细节：撤销时也要拍快照。**

`redo.push(capture(...))` 拍的是"撤销**之前**的状态"，也就是 `#5`。
它的内容 = 21 字版本。

**为什么必须先拍再恢复？** 因为重做要能回到"撤销前的那一刻"。
如果先 `restore` 再拍，拍到的就是 20 字版本 —— 那么"重做"就变成了"原地不动"。

**这和 `edit()` 的顺序是同一个道理，只是方向相反：**

```text
  edit()：  拍 before  →  改  →  入 undo
  undo()：  拍 before  →  改  →  入 redo
```

**两处的"before"都是指"本次动作之前"。** 一个统一的规则，两个相反的方向。

第一次撤销后的真实输出（第 13-15 行）：

```text
  连续撤销三次：
      [面板] 可撤 2 步 | 可重 1 步 | 已拍 5 淘汰 0 编辑 3 | 正文 20 字
    undo#1 = true → 客服：您好，您的订单已发货，预计明天送达
```

**注意 `[面板]` 出现在 `undo#1` 之前。** 因为 `notifyChanged()` 在 `undo()` 内部、
`return true` 之前就调了；而 `Client` 的 `println` 在 `undo()` 返回之后。

#### 6.1.6 第二次、第三次撤销

**第二次：**

```text
undo.size() = 3 > 1
target = undo.pop() = #3          undo = [ #2 | #1 ]
capture → #6 (09:00:02.500)，内容 = 当前 20 字版本
redo.push(#6)                     redo = [ #6 | #5 ]
restore(#3)                       → 13 字版本
notify → stats(2, 2, 6, 0, 3) → 可撤 1 步 | 可重 2 步 | 已拍 6 | 正文 13 字
```

**第三次：**

```text
undo.size() = 2 > 1
target = undo.pop() = #2          undo = [ #1 ]
capture → #7 (09:00:03.000)，内容 = 当前 13 字版本
redo.push(#7)                     redo = [ #7 | #6 | #5 ]
restore(#2)                       → 10 字版本（原始文本）
notify → stats(1, 3, 7, 0, 3) → 可撤 0 步 | 可重 3 步 | 已拍 7 | 正文 10 字
```

真实输出（第 16-19 行）：

```text
      [面板] 可撤 1 步 | 可重 2 步 | 已拍 6 淘汰 0 编辑 3 | 正文 13 字
    undo#2 = true → 客服：您好，您的订单已发货
      [面板] 可撤 0 步 | 可重 3 步 | 已拍 7 淘汰 0 编辑 3 | 正文 10 字
    undo#3 = true → 客服：您好，您的订单
```

**"可撤 0 步" 但 `undo#3 = true` —— 这两句话不矛盾。**

- `undo#3 = true` 的意思是"这一次撤销**成功了**"；
- `可撤 0 步` 的意思是"现在栈里只剩基线，**下一次**会失败"。

也就是说，"可撤 N 步"永远是**未来的**能力，而返回值是**已经发生**的事实。

**这是所有"还剩几次"类 API 的通病：** UI 上的"可撤 2 步"是在说"你还能按 2 次"，
不是在说"你已经按了 2 次"。

#### 6.1.7 两次重做：反方向走一遍

```java
boolean ok = history.redo();
```

`redo()` 与 `undo()` 完全对称（`SnapshotHistory.java:180-190`）：

| | `undo()` | `redo()` |
|---|---|---|
| 判空 | `undo.size() <= 1` | `redo.isEmpty()` |
| 取目标 | `undo.pop()` | `redo.pop()` |
| 补拍入哪 | `redo.push(...)` | `undo.push(...)` |
| 补拍的类型 | `EditKind.UNDO` | `EditKind.REDO` |
| 恢复 | `editor.restore(target)` | `editor.restore(target)` |

**为什么 `undo()` 用 `size() <= 1` 而 `redo()` 用 `isEmpty()`？**

因为两个栈的语义不同：

- `undo` 栈底**永远**有一张基线，所以"不能撤"的判断是"只剩一张"；
- `redo` 栈**没有**基线，空了就是空的。

**如果 `undo` 用 `isEmpty()` 判断会怎样？**

```java
if (undo.isEmpty()) { return false; }    // ← 错
```

那么最后一次撤销会弹出基线并 `restore` 它 —— 用户会发现"撤销之后正文变回了原文"，
但栈已经空了。**下一次再按撤销就会抛 `NoSuchElementException`**（因为 `ArrayDeque.pop()` 对空栈抛异常）。

所以基线不只是"方便判断"，它是**栈不变量的一部分**。

跟踪两次重做：

```text
redo#1：
  redo 非空
  target = redo.pop() = #7        redo = [ #6 | #5 ]
  capture → #8 (09:00:03.500)，内容 = 当前 10 字版本
  undo.push(#8)                   undo = [ #8 | #1 ]
  trim: 2 > 10 ? no
  restore(#7)                     → 13 字版本
  notify → stats(2, 1, 8, 0, 3) → 可撤 1 步 | 可重 2 步 | 已拍 8 | 正文 13 字

redo#2：
  target = redo.pop() = #6        redo = [ #5 ]
  capture → #9 (09:00:04.000)，内容 = 当前 13 字版本
  undo.push(#9)                   undo = [ #9 | #8 | #1 ]
  trim: 3 > 10 ? no
  restore(#6)                     → 20 字版本
  notify → stats(3, 1, 9, 0, 3) → 可撤 2 步 | 可重 1 步 | 已拍 9 | 正文 20 字
```

真实输出（第 20-25 行）：

```text
  连续重做两次：
      [面板] 可撤 1 步 | 可重 2 步 | 已拍 8 淘汰 0 编辑 3 | 正文 13 字
    redo#1 = true → 客服：您好，您的订单已发货
      [面板] 可撤 2 步 | 可重 1 步 | 已拍 9 淘汰 0 编辑 3 | 正文 20 字
    redo#2 = true → 客服：您好，您的订单已发货，预计明天送达
  统计 可撤 2 步 | 可重 1 步 | 已拍 9 淘汰 0 编辑 3
```

#### 6.1.8 一张图看完整条时间线

把 Demo 1 的全部九个快照和两个栈画在一起：

```text
动作            sequence  拍到的内容(字数)   undo 栈                redo 栈
─────────────────────────────────────────────────────────────────────────────
构造            1         10               [#1]                    []
edit「已发货」   2         10               [#2 #1]                 []
edit「预计…」    3         13               [#3 #2 #1]              []
edit「。」       4         20               [#4 #3 #2 #1]           []
undo #1         5         21               [#3 #2 #1]              [#5]
undo #2         6         20               [#2 #1]                 [#6 #5]
undo #3         7         13               [#1]                    [#7 #6 #5]
redo #1         8         10               [#8 #1]                 [#6 #5]
redo #2         9         13               [#9 #8 #1]              [#5]
─────────────────────────────────────────────────────────────────────────────
                                               ↑ 左=栈顶
```

**四个值得注意的现象：**

**（1）`sequence` 一路涨到 9，但 `undo + redo` 里的快照数只有 3 + 1 = 4。**

因为 `#2` 到 `#7` 里的绝大多数都不在两个栈里同时存在 —— 它们在撤销/重做的过程中
从一个栈搬到另一个栈。`#1` 永远在 `undo` 底部。

**（2）`可撤` 的峰值只有 3，`可重` 的峰值也是 3。**

因为一共只做了 3 次编辑。撤销/重做**不增加**可撤销的步数，它们只是**搬运**。

**（3）`编辑 3` 从头到尾没变过。**

因为 `recordedEdits++` 只在 `edit()` 成功路径上执行。撤销和重做**不是编辑**。

这是一个刻意的区分：`edits` 回答的是"用户改了几次"，不是"栈动了几次"。
如果撤销也计数，这个数字就失去了意义。

**（4）`#8` 的内容是 10 字，`#9` 的内容是 13 字。**

也就是"重做之前的状态"。它们进入 `undo` 栈，意味着：

> **重做之后，你还可以再撤销回去。**

这条规则容易被忽略，但它是真实编辑器（Word、VS Code、浏览器输入框）的标准行为：
重做一步之后再按撤销，应该退回重做之前的状态，而不是直接跳回更早。

**如果 `redo()` 里不 `undo.push(capture(...))`，只 `undo.push(target)` 会怎样？**

```java
undo.push(target);    // ← 错：把"要去的状态"当成"要记的状态"
```

那么重做之后，`undo` 的栈顶就变成了"重做到达的状态" ——
下一次撤销会读出这个状态并恢复它，等于**原地不动**。
用户会看到"按了撤销，正文没变"，然后以为撤销坏了。

#### 6.1.9 这段 Demo 与原项目的对照

原项目的等价场景（`Client.java` 42 行）：

```java
Document doc = new Document("版本1");
History history = new History();
history.pushMemento(doc.save());
doc.setContent("版本2");
history.pushMemento(doc.save());
doc.restore(history.popMemento());
```

三处关键差异：

| | 原项目 | 本项目 |
|---|---|---|
| 什么时候拍 | 调用方手动 `save()` + `pushMemento()` | `edit()` 内部自动拍 |
| 撤销怎么用 | `doc.restore(history.popMemento())`，两步且可能 NPE | `history.undo()`，一步且返回 boolean |
| 面板数据从哪来 | 没有面板 | `HistoryStats` record + 监听器 |

**第二行是探针 P2 的直接来源：** 如果有人写成 `history.popMemento();`
（忘了把返回值交给 `restore`），历史被弹掉了，但文档没变 ——
**历史与文档永久脱节**。而这一行代码**不会报错，不会警告，编译通过**。

本项目的 `undo()` 把这个错误从"可能发生"变成"写不出来"：
它没有返回值可以忘记，恢复动作在方法内部完成。

---

### 6.2 Demo 2：一段操作 = 一步撤销

这段 Demo 只验证一件事：**快照的粒度由 `edit()` 的边界决定，不由内部调用了几次 `type()` 决定。**

#### 6.2.1 场景代码

```java
Editor editor = new Editor("");
SnapshotHistory history = history(editor, 10);

history.edit(EditKind.TYPING, "一次敲完「合规审核」四个字", e -> {
    for (char c : "合规审核".toCharArray()) {
        e.type(String.valueOf(c));         // ← 内部调 4 次
    }
});
```

#### 6.2.2 逐层跟踪

**构造：**

```text
Editor("") → text = ""，cursor = 0，selection = [0,0)
SnapshotHistory: sequence = 0 → capture → sequence = 1, 09:00:00.000
                 #1 = Memento("", 0, [0,0), BASELINE "打开编辑器，正文 0 字", ...)
                 undo = [ #1 ]
```

**`edit()` 第 4 步 `capture`：**

```text
sequence = 2, 09:00:00.500
#2 = Memento("", 0, [0,0), TYPING "一次敲完「合规审核」四个字", ...)
before = #2
```

**`edit()` 第 5 步 `action.accept(editor)`：**

循环四次，每次都是一次完整的 `type()` → `replaceRange()`：

| 轮次 | 字符 | 进门时 text / cursor | `replaceRange(from,to,chunk)` | 出门时 text / cursor |
|---:|---|---|---|---|
| 1 | 合 | `""` / 0 | `(0, 0, "合")` | `"合"` / 1 |
| 2 | 规 | `"合"` / 1 | `(1, 1, "规")` | `"合规"` / 2 |
| 3 | 审 | `"合规"` / 2 | `(2, 2, "审")` | `"合规审"` / 3 |
| 4 | 核 | `"合规审"` / 3 | `(3, 3, "核")` | `"合规审核"` / 4 |

**四次 `type()`，四次 `replaceRange()`，但零张新快照。**

因为 `type()` 里根本没有 `capture` 的调用。它在 `Editor.java:141-144`，
只有三行：判空、委托 `replaceRange`、返回。

**这是分工的体现：**

```text
  Editor  负责"把状态改成什么样"        ← 不知道有历史这回事
  History 负责"什么时候记一笔"          ← 不知道状态改成了什么样
```

**`edit()` 第 6-7 步：**

```text
undo.push(#2)   → undo = [ #2 | #1 ]
trim: 2 > 10 ? no
redo.clear()
recordedEdits: 0 → 1
notifyChanged(): listeners 为空 → 直接 return（Demo 2 没挂监听器）
```

输出：

```text
  一次 edit 内敲 4 个字 → 「合规审核」
```

**第二次 `edit()`：**

```text
capture → #3 (09:00:01.000)，内容 = 当前 4 字版本
action: e.type("完成") → "合规审核完成"，cursor 6
undo.push(#3) → undo = [ #3 | #2 | #1 ]
recordedEdits: 1 → 2
```

输出：

```text
  第二次 edit 后 → 「合规审核完成」
  统计 可撤 2 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 2
  注意：只敲了 2 次 edit，尽管内部调用了 5 次 type()
```

**5 次 `type()` 对应 2 张快照** —— 这就是"粒度由 `edit()` 决定"的全部含义。

#### 6.2.3 三次撤销：第三次返回 false

```text
undo#1：
  undo.size() = 3 > 1
  target = #3      undo = [ #2 | #1 ]
  capture → #4 (09:00:01.500)，内容 = 当前 6 字版本
  redo.push(#4)    redo = [ #4 ]
  restore(#3)      → "合规审核"（4 字）

undo#2：
  undo.size() = 2 > 1
  target = #2      undo = [ #1 ]
  capture → #5 (09:00:02.000)，内容 = 当前 4 字版本
  redo.push(#5)    redo = [ #5 | #4 ]
  restore(#2)      → ""（空串，因为 #2 是第一次 edit 之前的状态）

undo#3：
  undo.size() = 1 <= 1 → return false，什么都不做
```

真实输出（第 32-36 行）：

```text
  连续撤销：
    undo#1 = true → 「合规审核」
    undo#2 = true → 「」
    undo#3 = false → 「」
  第 3 次返回 false：已经退到最早保留的版本，不会再往后退
```

**注意第三次的返回值和正文：`false` 且正文没变（还是 `""`）。**

这是"拒绝执行"和"执行了但没效果"的区别：

- `undo#2` 之后正文已经是 `""`（基线内容）；
- `undo#3` 发现只剩基线，**直接 return**，连 `restore` 都没调；
- 所以正文"看起来没变"是因为**真的什么都没做**，不是因为"恢复了同一个值"。

**这两种情况在行为上一样，在代价上不同：** 后者会白白 `restore` 一次、白白 `notifyChanged()` 一次。
如果监听器里有埋点，用户会看到"点了一次没反应的撤销，但埋点记了一笔"。

**先判断再动作，永远比"动作完再判断有没有意义"干净。**

#### 6.2.4 如果原项目做同一件事

原项目没有 `edit()`。要"一次撤销回退四个字"，调用方必须自己控制：

```java
// 只拍一次
history.pushMemento(doc.save());
for (char c : "合规审核".toCharArray()) {
    doc.setContent(doc.getContent() + c);
}
```

**这能工作，但完全依赖调用方自觉。** 换一个人写，很可能变成：

```java
for (char c : "合规审核".toCharArray()) {
    doc.setContent(doc.getContent() + c);
    history.pushMemento(doc.save());      // ← 拍了四次
}
```

于是用户按一次撤销只退掉"核"一个字 —— **而这两段代码都编译通过、都能跑、都没报错**。

这就是 §4.7 说的"边界依赖调用方"的具体代价：**同一个需求有两种写法，
一种对一种错，而编译器分不出来。**

`edit()` 把边界钉在调用处：

```java
history.edit(kind, detail, e -> {
    for (char c : "合规审核".toCharArray()) {
        e.type(String.valueOf(c));        // ← 无论几次，都是一步
    }
});
```

**`Consumer<Editor>` 这个参数类型本身就在说："这段代码里改多少次都算一次"。**

---

### 6.3 Demo 3：编辑中途失败，自动回滚

这段验证的是"失败不留痕"。它是原项目**完全没有**的能力。

#### 6.3.1 场景代码

```java
Editor editor = new Editor("原稿：本条款有效");
SnapshotHistory history = history(editor, 10);

history.edit(EditKind.TYPING, "补一句生效条件", e -> e.type("，自签署日起生效"));
// → 正文变成 "原稿：本条款有效，自签署日起生效"（16 字）

try {
    history.edit(EditKind.REPLACE, "整段替换（中途校验失败）", e -> {
        e.replaceAll("整段替换后的内容");                    // ← 先改了
        throw new IllegalStateException("缺少签署方，不允许替换");   // ← 再抛
    });
} catch (IllegalStateException ex) {
    System.out.println("  捕获 " + ex.getClass().getSimpleName() + "：" + ex.getMessage());
}
```

**注意 `action` 里的顺序：先改，再抛。**

这是一个刻意构造的"最坏情形"：

- 如果先抛再改，"没改过"当然是干净的，验证不出任何东西；
- **先改再抛**，才逼出"改了一半要怎么收场"这个问题。

#### 6.3.2 正常编辑那一步

```text
构造：     #1 (09:00:00.000)，内容 = "原稿：本条款有效"（8 字）
           undo = [ #1 ]

edit1：    capture → #2 (09:00:00.500)，内容 = 当前 8 字版本
           action: type("，自签署日起生效")
                   "，自签署日起生效" 是 8 个字符
                   → text = "原稿：本条款有效，自签署日起生效"（16 字），cursor = 16
           undo.push(#2) → undo = [ #2 | #1 ]
           recordedEdits = 1
```

输出：

```text
  正常编辑后 → 「原稿：本条款有效，自签署日起生效」
  失败前 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1
```

#### 6.3.3 失败路径：逐步跟踪 `edit()` 的异常分支

```text
① 三个 requireNonNull                     通过

② capture(REPLACE, "整段替换（中途校验失败）")
   sequence = 3, 09:00:01.000
   #3 = Memento("原稿：本条款有效，自签署日起生效", 16, [16,16), ...)
   before = #3

③ try { action.accept(editor) }
   3.1  e.replaceAll("整段替换后的内容")
        text = "整段替换后的内容"（8 字），cursor = 8，selection = [8,8)
   3.2  throw new IllegalStateException("缺少签署方，不允许替换")

④ catch (RuntimeException e)
   4.1  editor.restore(before)
        restore(#3) → 检查 ownerToken 通过
        text = "原稿：本条款有效，自签署日起生效"（16 字）
        cursor = 16
        selection = [16,16)
   4.2  throw e                  ← 原样抛出，不包装、不吞掉

⑤ （后续代码全被跳过）
    undo.push(before)            没执行
    trim()                       没执行
    redo.clear()                 没执行
    recordedEdits++              没执行
    notifyChanged()              没执行
```

**第 ⑤ 步是整段设计的核心：异常一抛，后面五行全部跳过。**

这不是靠"记得写 return"实现的，而是靠 **`throw` 本身就是跳出方法**。
只要 `catch` 块里 `throw e`，编译器就保证后面的语句不会执行。

**如果 `catch` 块里忘了 `throw e` 会怎样？**

```java
} catch (RuntimeException e) {
    editor.restore(before);
    // ← 忘了 throw
}
undo.push(before);      // ← 会执行！
```

那么这次失败的编辑会**入栈**，用户按撤销会看到"退回到进门前" ——
但进门前的状态和当前状态**一模一样**（因为已经 `restore` 过了）。
于是第一次撤销"看起来没反应"。

**而 `recordedEdits++` 也会跟着加一，`redo.clear()` 也会清空重做栈。**
一次失败的操作，产生了三步副作用。

**所以 `catch` 里必须 `throw`，这不是风格问题，是正确性问题。**

#### 6.3.4 失败之后，三个统计量各自说明了什么

真实输出（第 40-48 行）：

```text
=== Demo 3：编辑中途失败，自动回滚 ===
  正常编辑后 → 「原稿：本条款有效，自签署日起生效」
  失败前 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1
  捕获 IllegalStateException：缺少签署方，不允许替换
  失败后 → 「原稿：本条款有效，自签署日起生效」
  失败后 可撤 1 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 1
  正文回到进门前？true
  这篇快照没有入栈，所以「可撤」步数没变
  「已拍」多了 1：快门按过了，只是底片没进相册
```

逐项对照：

| 指标 | 失败前 | 失败后 | 为什么 |
|---|---:|---:|---|
| `可撤` | 1 | 1 | `undo.push` 没执行，栈没变 |
| `可重` | 0 | 0 | `redo.clear()` 没执行，重做栈原样 |
| `已拍` | 2 | **3** | `capture` 在 `try` **之前**，已经执行了 |
| `编辑` | 1 | 1 | `recordedEdits++` 没执行 |
| 正文 | 16 字 | 16 字 | `restore(before)` 撤掉了 `replaceAll` |

**"已拍 3，但栈里只有 2 张" —— 这个差值就是失败留下的痕迹。**

它是有意保留的，理由写在 `capture()` 的 javadoc 里（`SnapshotHistory.java:242-246`）：

> `sequence` 记录的是**拍摄次数**，而不是"成功入栈的快照数"。

**为什么要区分这两个？** 因为如果让 `sequence` 只在成功时自增：

```java
undo.push(before);
sequence++;                  // ← 挪到成功路径
```

那么编号确实连续了，但**时钟已经走过了 500 毫秒**。
下一次拍摄会用 `09:00:01.500`，而编号是 `#3` —— 编号与时间的对应关系就乱了。

**更重要的是可观测性：** `已拍 - (可撤 + 可重)` 这个差值，
恰好就是"失败次数 + 被淘汰次数"。它是一个免费的健康度指标。

Demo 3 里这个差值是 `3 - (2 + 0) = 1`，正好对应"一次失败"。

#### 6.3.5 为什么 `restore(before)` 不会破坏 `undo` 栈

一个容易担心的问题：

> `restore` 会把 `editor` 恢复到 `before` 的状态，
> 但 `before` 又没入栈，那 `undo` 里的 `#2` 还指向"16 字版本"吗？

答案是**是的，而且这正是想要的**。

```text
  #2 里存的是 Memento("原稿：本条款有效", 8, [8,8), ...)
  ↑ 它是"第一次编辑之前"的状态，也就是 8 字版本
  ↑ 它从来没有指向 16 字版本

  失败之后，editor 的当前状态 = 16 字版本
  undo 栈顶 = #2 = 8 字版本
```

所以下一次 `undo()`：

```text
target = undo.pop() = #2
capture → #4，内容 = 当前 16 字版本
redo.push(#4)
restore(#2) → 8 字版本
```

用户看到"撤销一次，回到了最早的原稿"。

**这个行为对不对？** 对。因为那次失败的编辑**从来没发生过**（在历史的意义上）——
用户的世界线里只有一次成功编辑。撤销一次退回原稿，完全符合直觉。

**如果不回滚会怎样？** 那么 `editor` 停在 8 字的 `"整段替换后的内容"`，
而 `undo` 栈顶是 `#2`（8 字的原稿）。撤销一次会跳到"原稿" ——
于是用户看到"我什么都没干，按一下撤销，正文自己变了"。

**这是最难查的一类 bug：操作失败后状态已经脏了，但错误信息说"失败"。**

#### 6.3.6 `addSuppressed` 与"回滚失败"怎么办

`edit()` 的 catch 块只有三行，有一个边界它没处理：

```java
} catch (RuntimeException e) {
    editor.restore(before);      // ← 如果这一行自己抛了呢？
    throw e;
}
```

`restore(before)` 会抛 `SnapshotRejectedException` 吗？

**理论上不会** —— `before` 就是本方法刚拍的，`ownerToken` 必然匹配。
除非有人改坏了 `Editor`，否则这个 `restore` 不会失败。

**如果真的失败了会怎样？** 那么 `restore` 抛出的异常会**取代** `e`，
调用方看不到原始的 `IllegalStateException` —— 原始的失败原因永久丢失。

**严格的写法是：**

```java
} catch (RuntimeException e) {
    try {
        editor.restore(before);
    } catch (RuntimeException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
    }
    throw e;
}
```

这样两个异常都能看到（`e.getSuppressed()` 里是回滚失败）。

**本项目没有这么写，原因是：** 这个 `catch` 分支里唯一的语句是 `restore`，
而 `restore` 的失败条件（归属不匹配）在这里**构造上不可能成立**。
为一个"不可能发生的失败"写六行防御代码，会让读者以为它可能发生 ——
**代码会撒谎。**

**判断标准：如果防御的分支在构造上不可达，就不该写；如果可达，就必须写。**
这里的判断依据是"`before` 是本方法自己刚拍的"这个事实，它写在三行之上，读者一眼能看到。

---

### 6.4 Demo 4：容量上限，最老的快照被淘汰

这段验证的是"内存不会无限增长"。它是探针 P8（连压 10 万条全留着）的正面回应。

#### 6.4.1 场景代码

```java
Editor editor = new Editor("第0版");
SnapshotHistory history = history(editor, 3);          // ← 深度 3

for (int i = 1; i <= 4; i++) {
    final int version = i;
    history.edit(EditKind.REPLACE, "改成第 " + version + " 版",
            e -> e.replaceAll("第" + version + "版"));
}
```

**四次编辑，深度 3。** 这个 4 > 3 的设定就是为了逼出淘汰。

#### 6.4.2 逐次跟踪：栈怎么涨、怎么削

**构造：**

```text
#1 (09:00:00.000) BASELINE "打开编辑器，正文 3 字"
   内容 = "第0版"
undo = [ #1 ]                                    size = 1
```

**`i = 1`：`e.replaceAll("第1版")`**

```text
capture → #2 (09:00:00.500) "改成第 1 版"
         拍的还是"第0版"（因为改之前）
action  → replaceAll("第1版") → text = "第1版"，cursor = 3
undo.push(#2) → undo = [ #2 | #1 ]               size = 2
trim: 2 > 3 ? no
redo.clear()                                     已经是空的
edits = 1
```

**`i = 2`：`e.replaceAll("第2版")`**

```text
capture → #3 (09:00:01.000) "改成第 2 版"
         内容 = "第1版"
action  → text = "第2版"
undo.push(#3) → undo = [ #3 | #2 | #1 ]          size = 3
trim: 3 > 3 ? no                                 ← 刚好到上限，不淘汰
edits = 2
```

**`i = 3`：`e.replaceAll("第3版")` —— 第一次淘汰**

```text
capture → #4 (09:00:01.500) "改成第 3 版"
         内容 = "第2版"
action  → text = "第3版"
undo.push(#4) → undo = [ #4 | #3 | #2 | #1 ]     size = 4
trim: 4 > 3 → 循环一次
      undo.removeLast()                          移除 #1（最老）
      evicted = 1
      undo = [ #4 | #3 | #2 ]                    size = 3
edits = 3
```

**`i = 4`：`e.replaceAll("第4版")` —— 第二次淘汰**

```text
capture → #5 (09:00:02.000) "改成第 4 版"
         内容 = "第3版"
action  → text = "第4版"
undo.push(#5) → undo = [ #5 | #4 | #3 | #2 ]     size = 4
trim: 4 > 3 → 循环一次
      undo.removeLast()                          移除 #2
      evicted = 2
      undo = [ #5 | #4 | #3 ]                    size = 3
edits = 4
```

真实输出（第 50-53 行）：

```text
=== Demo 4：容量上限 3，最老的快照被淘汰 ===
  连做 4 次编辑后 → 「第4版」
  统计 可撤 2 步 | 可重 0 步 | 已拍 5 淘汰 2 编辑 4
  「淘汰 2」= 第0版和第1版的快照已被挤出栈
```

#### 6.4.3 淘汰的到底是哪两张

`undo` 里的五张快照（按时间顺序）：

| 编号 | 拍摄时机 | 内容 | 结局 |
|---|---|---|---|
| `#1` | 构造器 | `"第0版"` | **淘汰** |
| `#2` | 改成第1版之前 | `"第0版"` | **淘汰** |
| `#3` | 改成第2版之前 | `"第1版"` | 留在栈底 |
| `#4` | 改成第3版之前 | `"第2版"` | 留在中间 |
| `#5` | 改成第4版之前 | `"第3版"` | 留在栈顶 |

**`#1` 和 `#2` 的内容都是 `"第0版"`。**

所以"第0版和第1版的快照已被挤出栈"这句话要按**编号**读，不能按**内容**读：

- 被挤出去的是 `#1`（编号 1）和 `#2`（编号 2）；
- 它们的**内容**都是 `"第0版"`；
- **`"第1版"` 从来没有被淘汰** —— 它现在沉睡在 `#3` 里。

这句文案写在 `Client` 里，稍微有点歧义。**更准确的说法是"第 1 张和第 2 张快照被挤出去了，
它们记的都是第 0 版的内容"。**

#### 6.4.4 撤销：只能撤两步

```text
undo#1：
  undo.size() = 3 > 1
  target = pop() = #5      undo = [ #4 | #3 ]
  capture → #6 (09:00:02.500) 内容 = "第4版"
  redo.push(#6)            redo = [ #6 ]
  restore(#5) → text = "第3版"

undo#2：
  undo.size() = 2 > 1
  target = pop() = #4      undo = [ #3 ]
  capture → #7 (09:00:03.000) 内容 = "第3版"
  redo.push(#7)            redo = [ #7 | #6 ]
  restore(#4) → text = "第2版"

undo#3：
  undo.size() = 1 <= 1 → return false

undo#4：
  同上 → return false
```

真实输出（第 54-59 行）：

```text
  连续撤销：
    undo#1 = true → 「第3版」
    undo#2 = true → 「第2版」
    undo#3 = false → 「第2版」
    undo#4 = false → 「第2版」
  只能撤到第2版为止 —— 这就是容量上限的代价，必须让用户知道
```

#### 6.4.5 一个必须诚实说明的细节：`maxDepth` 差一

看这张表：

```text
  maxDepth = 3
  → 栈里最多 3 张快照
  → 实际能撤销 2 次
  → 栈底那张（#3，内容"第1版"）永远读不到
```

**为什么栈底那张读不到？**

因为 `undo()` 的守卫是 `undo.size() <= 1` —— 只剩一张就拒绝。
而 `redo()` 只会往 `undo` 里 `push`，不会 `pop` 栈底。

所以**栈底那张快照是一条死路**：它占据了 1 个容量槽，却没有任何代码路径会读取它。

**这不是 bug，但它是"参数命名与行为差一"的典型案例：**

| 写法 | 含义 | `maxDepth = 3` 时能撤几次 |
|---|---|---:|
| 现在的实现 | 栈**容量**（含基线） | 2 |
| 直觉理解 | 能撤销的**步数** | 3 |

`SnapshotHistory` 的构造器 javadoc 写清楚了：

> `@param maxDepth 撤销栈最大深度，至少 2（1 个基线 + 1 步编辑）`

—— 括号里那句"1 个基线 + 1 步编辑"就是在说"含基线"。
而 `EditorLimits.MAX_UNDO_DEPTH` 这个名字读起来像"最大撤销步数"，**名字和语义有轻微偏差**。

**要不要改成"能撤 N 次"？** 两种做法都能自洽：

```java
// 方案 A（现状）：maxDepth 是栈容量
trim() { while (undo.size() > maxDepth) undo.removeLast(); }

// 方案 B：maxDepth 是能撤的步数
trim() { while (undo.size() > maxDepth + 1) undo.removeLast(); }
```

本项目选 A，理由有两条：

1. **A 的判据只有"容量"一个概念**，B 里同时存在"容量 = depth + 1"和"步数 = depth"两个数，
   读 `undo.size() > maxDepth + 1` 的人每次都要在脑子里加一；
2. **B 会让 `maxDepth = 1` 变得合法**（能撤 1 步），但那时栈里只有 2 张，
   一次编辑一淘汰，基线立刻消失 —— 又回到 §5.8.1 说的"不变式破裂"。

**代价就是那个"差一"。** 所以文档必须写清楚，否则用户按"深度 3 = 撤 3 次"去理解，一定困惑。

**这也解释了 Demo 4 那句文案为什么说"只能撤到第2版为止"：**
`maxDepth = 3` 能撤 2 次，从"第4版"撤 2 次，就是"第2版"。

#### 6.4.6 淘汰为什么用 `removeLast()` 而不是 `pollLast()`

```java
private void trim() {
    while (undo.size() > maxDepth) {
        undo.removeLast();          // ← 不是 pollLast()
        evicted++;
    }
}
```

`ArrayDeque` 上有两个"取尾部"的方法：

| 方法 | 空栈时 | 返回值 |
|---|---|---|
| `removeLast()` | 抛 `NoSuchElementException` | `E` |
| `pollLast()` | 返回 `null` | `E`（可能为 null） |

**这里必须用 `removeLast()`。** 因为 `while (undo.size() > maxDepth)` 保证了栈非空
（`maxDepth >= 2`，所以 `size() > 2` 时至少有 3 个元素）。

用 `pollLast()` 会让读者怀疑"这里可能拿到 null 吗"，然后去检查返回值 ——
**一个不可能发生的情况，逼着读者多花三秒。**

**`removeLast()` 的"抛异常"不是风险，而是一个免费的断言：**
如果哪天 `maxDepth` 被改成 0 或负数，这里立刻抛，而不是静默地什么都不做。

#### 6.4.7 如果完全没有淘汰

探针 P8 跑过的场景：连压 10 万次，`Stack` 里留 10 万条。

对一份 400KB 的文档，10 万张快照 ≈ 40GB —— 早 OOM 了。
即使文档只有 4KB，10 万张也是 400MB。

**真实编辑器的做法：**

| 编辑器 | 上限策略 |
|---|---|
| VS Code | 按内存压力动态调整，通常几百步 |
| Word | 100 步（旧版本） |
| 浏览器 `<input>` | 几十步 |
| Excel | 按操作类型分别限制 |

**没有一个"无上限"的先例。** 因为"无上限"等价于"内存泄漏，只是泄漏得比较慢"。

本项目的 `MAX_UNDO_DEPTH = 100` 是一个明确的分界线：**超过 100 步，就该考虑差量快照或落盘了**（见 §7 的扩展方向）。

#### 6.4.8 `evicted` 与 `sequence` 的关系

在 Demo 4 结束时：

```text
  sequence = 7    （拍了 7 次）
  evicted  = 2    （淘汰了 2 张）
  undo.size() = 1
  redo.size() = 2
```

恒等式：

```text
  sequence = undo.size() + redo.size() + evicted + 失败次数
  7        = 1            + 2            + 2       + 0
```

**这个恒等式可以用来做自检。** 如果哪天对不上，说明有一套账没记平 ——
要么有 `capture` 之后既没入栈也没被淘汰，要么 `evicted` 加错了地方。

Demo 3 里失败过一次，那时：

```text
  sequence = 3，undo.size() = 2，redo.size() = 0，evicted = 0
  → 失败次数 = 3 - 2 - 0 - 0 = 1        ✓
```

**能看到这个恒等式，是因为 `sequence` 记的是"拍摄次数"而不是"入栈次数"。**
如果当初把 `sequence++` 挪到成功路径，这个等式就永远成立了 ——
但它同时也失去了"测量失败"的能力。

**观测能力和"账面整齐"经常是矛盾的。** 本项目选了前者。

---

### 6.5 Demo 5：快照不能伪造，也不能串档

这段是唯一用**反射**的 Demo。它验证两个边界：

1. **能不能在外面 `new` 一个快照？** —— 不能（编译期就拦）。
2. **能不能把 A 的快照塞给 B？** —— 不能（运行期拦，靠 `ownerToken`）。

第 2 条是本项目在实现过程中踩出来的真实 bug（§4.3）。

#### 6.5.1 场景代码

```java
Class<?> memento = Class.forName("com.patterns.behavioral.memento.Editor$Memento");
System.out.println("  备忘录的运行时类 = " + memento.getName());
System.out.println("  是 private？" + Modifier.isPrivate(memento.getModifiers()));
System.out.println("  是 record？" + memento.isRecord());

Editor a = new Editor("文档 A");
Editor b = new Editor("文档 B");

Snapshot fromA = a.snapshot(new SnapshotMetadata(999, EditKind.TYPING, "手工拍的", clock().get()));

a.restore(fromA);
b.restore(fromA);          // ← 关键：这一行会抛
```

#### 6.5.2 前三行：反射看见了什么

真实输出（第 62-65 行）：

```text
  备忘录的运行时类 = com.patterns.behavioral.memento.Editor$Memento
  是 private？true
  是 record？true
  外部代码写不出 new Editor.Memento(...)：这个类型在源码里不可见
```

逐个说：

**（1）`Editor$Memento` 里的 `$`**

`$` 是 JVM 对**嵌套类**的命名约定：

```text
  源码：  Editor.Memento
  字节码：Editor$Memento              ← $ 分隔外部类和嵌套类
  文件：  Editor$Memento.class
```

**这个类文件是真实存在的。** 编译产物里有 14 个 class：

```text
  Client.class                    EditKind.class
  ConsoleHistoryListener.class    Editor.class
  Editor$Memento.class            EditorLimits.class
  HistoryListener.class           HistoryStats.class
  Snapshot.class                  SnapshotHistory.class
  SnapshotMetadata.class          SnapshotRejectedException.class
  SteppedClock.class              TextRegion.class
                                  ────────────────
                                  共 14 个（13 个源文件 + 1 个内部类）
```

**（2）`Modifier.isPrivate(...) == true`**

这条是硬证据：`Memento` 在源码里写成 `private record`，
所以字节码的 access flags 里带 `ACC_PRIVATE`。

反射能看见它（因为 `Class.forName` 不看访问修饰符），
**但普通代码看不见它**（编译器不让写）。

**（3）`isRecord() == true`**

`Class.isRecord()` 是 Java 16 引入的。它返回 `true` 意味着这个类：

- 被 `final` 修饰（不可继承）；
- 所有字段都是 `private final`；
- 有一个"规范构造器"，参数顺序与组件顺序一致；
- 自动生成了 `equals` / `hashCode` / `toString` / 每个组件的访问器。

**这三行输出的教学价值在于：它把"编译期不可见"这件事变可观测了。**

如果没有这三行，读者只能相信"private 就是看不见"。有了它，
读者能亲眼看到"类还在，只是编译器不让你写"。

#### 6.5.3 第 4-5 行：拍一张"手工快照"

```java
Snapshot fromA = a.snapshot(new SnapshotMetadata(999, EditKind.TYPING, "手工拍的", clock().get()));
```

**这段代码完全合法，而且它揭示了一个设计事实：`Editor.snapshot()` 是 `public`。**

因此任何持有 `Editor` 引用的代码都能拍快照 —— 包括绕过 `SnapshotHistory` 直接拍。

`SnapshotMetadata(999, ...)` 里的 `999` 也完全合法：

```java
public SnapshotMetadata {
    if (sequence < 1) {
        throw new IllegalArgumentException("快照编号从 1 开始，实际 " + sequence);
    }
    // ...
}
```

**只校验 `>= 1`，不校验"连续性"。** 因为它不知道该跟谁连续 ——
`SnapshotMetadata` 是值对象，它没有"上一个编号是多少"的知识。

输出：

```text
  a 自己拍一张 → #999 输入 · 手工拍的 · 09:00:00.000
```

**注意 `09:00:00.000`：** `Client.clock()` 每次都新建一个 `SteppedClock`，
所以这里拿到的是新实例的第 1 次 `get()`。

#### 6.5.4 第 6 行：自己恢复自己的 —— 通过

```java
a.restore(fromA);
```

`restore()` 的两道运行期检查（`Editor.java:264-274`）：

```text
① snapshot != null ？                          通过
② snapshot instanceof Memento ？               通过（就是 Memento）
③ memento.owner() != ownerToken ？             两者相同，所以条件为 false
   → 不进 if 分支
④ 恢复三个字段
   text      = "文档 A"（拍快照时就是它，没变过）
   cursor    = 3
   selection = [3,3)
```

输出：

```text
  a.restore(自己的) 成功 → 「文档 A」
```

**这里有个容易忽略的点：这次 `restore` 什么也没改变。**

因为 `fromA` 是刚拍的，`a` 的状态没动过。所以"恢复"是"恢复成原样"。

**那为什么还要做这一步？** 因为它建立了对照组：

- 同一对 (editor, snapshot)，`restore` 必须成功；
- 交叉的一对 (b, fromA)，`restore` 必须失败。

**只有前者成功、后者失败，"归属检查"才算被验证。**
如果两者都失败，那说明检查写得太严（连自己都不认）；
如果两者都成功，说明检查不存在。这也正是第一版的情况。

#### 6.5.5 第 7 行：B 恢复 A 的 —— 被拒

这是全 Demo 最关键的一行。

```java
try {
    b.restore(fromA);
    System.out.println("  b.restore(a 的) 竟然成功（不该发生）");
} catch (SnapshotRejectedException ex) {
    System.out.println("  b.restore(a 的) 被拒 → " + ex.getMessage());
}
```

**第一版（没有 `ownerToken`）跑出来的真实输出是：**

```text
  b.restore(a 的) 竟然成功（不该发生）          ← ← ← bug
```

**为什么成功？**

```text
  a 的 ownerToken = tokenA
  b 的 ownerToken = tokenB
  fromA.owner()   = tokenA

  第一版 restore 只检查：
      snapshot instanceof Memento ?
  → fromA 是 Memento ✓  （Memento 是"类"，a 和 b 共享同一个类型）
  → 通过

  于是 b.text = fromA.text = "文档 A"
  → b 的正文被 a 的草稿覆盖了
```

**这就是"类型"与"实例"的区别：**

```text
  instanceof Memento  回答："这个对象是不是某个 Editor 造的？"
  owner() == token    回答："这个对象是不是『我』造的？"
```

**第一种问法在单个 Editor 的场景下永远返回 true —— 也就等于什么都没问。**

**修完之后（现在）：**

```text
③ memento.owner() = tokenA，ownerToken = tokenB
   tokenA != tokenB → 条件为 true，进 if
④ throw new SnapshotRejectedException(
       "拒绝恢复：#999 输入 · 手工拍的 · 09:00:00.000 不是本编辑器产生的快照")
```

真实输出（第 68 行）：

```text
  b.restore(a 的) 被拒 → 拒绝恢复：#999 输入 · 手工拍的 · 09:00:00.000 不是本编辑器产生的快照
```

**`b` 的正文完全没变，仍然是 `"文档 B"`。** 因为异常在 `this.text = ...` 之前就抛了。

**这个顺序很重要：先检查，再写入。**

如果写成：

```java
this.text = memento.text();          // ← 先写
if (memento.owner() != ownerToken) { // ← 再查
    throw new SnapshotRejectedException(...);
}
```

那么 `b` 的正文已经被污染了，异常只是"事后通报"。**抛异常不能撤销已经发生的副作用。**

**"校验在前，动作在后"是一条没有例外的规则。**

#### 6.5.6 第 8 行：窄接口到底暴露了什么

```java
System.out.println("  窄接口暴露的方法只有 " + Arrays.toString(Snapshot.class.getMethods()));
```

输出：

```text
  窄接口暴露的方法只有 [public abstract com.patterns.behavioral.memento.SnapshotMetadata com.patterns.behavioral.memento.Snapshot.metadata()]
```

**注意 `getMethods()` 返回的是"全部 public 方法，含继承来的"。**

`Snapshot` 是一个接口，它的直接父接口是"无"（隐式 `java.lang.Object` 不算，
接口不继承 Object 的方法）。所以返回数组里只有 `metadata()` 一个。

**如果 `Snapshot` 上再多一个方法，这个数组就会变成两个元素** ——
这行输出就是一个自动化的"接口宽度检查"。将来有人给 `Snapshot` 加了 `text()`，
Demo 5 的输出会立刻变长，任何人跑一遍都看得见。

**这是一个很便宜的架构守护：把"设计约束"写成"可观测的输出"。**

比写一条注释"请不要给 Snapshot 加方法"强得多 ——
注释没人读，输出有人看。

#### 6.5.7 最后一行：一段诚实的自白

```text
  注意：metadata 里的 999 是伪造得出来的 —— 元信息可以编，正文编不了
```

**这句话值得单独讲，因为它是本项目少数几处"承认自己防不住"的地方。**

分三层说：

**（1）元信息确实可以编**

`SnapshotMetadata` 是 `public record`，任何人可以 `new SnapshotMetadata(999, ...)`。

**为什么让它 public？** 因为 `Editor.snapshot(SnapshotMetadata)` 需要它做参数：

```java
public Snapshot snapshot(SnapshotMetadata metadata) {
    return new Memento(text, cursor, selection, metadata, ownerToken);
}
```

如果 `SnapshotMetadata` 是包私有，那么包外的调用方就没法指定元信息 ——
而这个能力是**有意开放**的：

- 插件想在某个自定义事件点拍一张标记快照；
- 测试想构造"第 999 步"的场景；
- 两条独立的历史（"本次会话"和"自动保存"）需要各自的编号空间。

**（2）正文编不了**

`Memento` 是 `private record`，唯一能造它的地方是 `Editor.snapshot()`。
而 `Editor.snapshot()` 里的 `text` / `cursor` / `selection` **全部来自 `this` 的当前值**：

```java
return new Memento(text, cursor, selection, metadata, ownerToken);
//                  ↑      ↑          ↑
//                  全是 Editor 自己的字段，调用方无法指定
```

**所以"元信息可伪造，正文不可伪造"不是巧合，而是接口切分的必然结果：**
公开的那一半可以编，私有的那一半编不了。

**（3）反射仍然可以绕过**

```java
Class<?> m = Class.forName("com.patterns.behavioral.memento.Editor$Memento");
Constructor<?> ctor = m.getDeclaredConstructors()[0];
ctor.setAccessible(true);
Object fake = ctor.newInstance("伪造的正文", 0, TextRegion.caret(0), metadata, tokenA);
```

**这能成功**（假设拿到了 `tokenA`）。所以"防伪造"在 JVM 层面是不成立的。

**那为什么还要这么设计？**

因为防御的目标**不是反射攻击**，而是**正常开发中的手滑**：

| 场景 | 私有嵌套能防住吗 |
|---|---|
| 新人想"临时改一下快照内容" | ✅ 编译不过 |
| 复制粘贴时把 A 的快照给了 B | ✅ 运行期抛错 |
| 用反射绕过 | ❌ 防不住 |

**第三行防不住，但第三行需要"故意"。**
前两行是"不小心"，它们才是真实项目 99% 的问题来源。

> **设计的目标不是让坏事不可能发生，而是让坏事必须被"故意"才能做到。**
> 一旦必须故意，它就会留下代码痕迹，code review 就能看见。

---

### 6.6 Demo 6：撤到底、重做、再编辑

这段验证两条"边界规则"：

1. **撤到底时 `undo()` 返回 `false`，且不改变状态。**
2. **一旦发生新编辑，重做栈立刻作废。**

第 2 条是几乎所有编辑器的标准行为，但它**很容易写错**。

#### 6.6.1 场景代码

```java
Editor editor = new Editor("第一稿");
SnapshotHistory history = history(editor, 10);

history.edit(EditKind.REPLACE, "改成第二稿", e -> e.replaceAll("第二稿"));
history.edit(EditKind.REPLACE, "改成第三稿", e -> e.replaceAll("第三稿"));

while (history.undo()) { ... }            // ← 一路撤到底
System.out.println(history.undo());        // ← 再撤一次，应该是 false

history.redo();                            // ← 重做一次
history.edit(EditKind.REPLACE, "另起一段，重做作废", e -> e.replaceAll("新的一段"));
history.redo();                            // ← 应该是 false
```

#### 6.6.2 前两步编辑与撤到底

```text
构造：    #1 (09:00:00.000) 内容 = "第一稿"          undo = [ #1 ]

edit1：   capture → #2 (09:00:00.500) 内容 = "第一稿"
          replaceAll("第二稿") → text = "第二稿"
          undo = [ #2 | #1 ]                        edits = 1

edit2：   capture → #3 (09:00:01.000) 内容 = "第二稿"
          replaceAll("第三稿") → text = "第三稿"
          undo = [ #3 | #2 | #1 ]                   edits = 2
```

输出（第 73-74 行）：

```text
  两次编辑后 → 「第三稿」
  统计 可撤 2 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 2
```

**`while (history.undo())` 的跟踪：**

```text
第 1 轮：
  undo.size() = 3 > 1 → 继续
  target = #3        undo = [ #2 | #1 ]
  capture → #4 (09:00:01.500) 内容 = "第三稿"
  redo.push(#4)      redo = [ #4 ]
  restore(#3) → "第二稿"
  print: 第 1 次 undo = true → 「第二稿」

第 2 轮：
  undo.size() = 2 > 1 → 继续
  target = #2        undo = [ #1 ]
  capture → #5 (09:00:02.000) 内容 = "第二稿"
  redo.push(#5)      redo = [ #5 | #4 ]
  restore(#2) → "第一稿"
  print: 第 2 次 undo = true → 「第一稿」

第 3 轮：
  undo.size() = 1 <= 1 → return false
  → while 退出
```

输出（第 75-78 行）：

```text
  一路撤销：
    第 1 次 undo = true → 「第二稿」
    第 2 次 undo = true → 「第一稿」
    再来一次 undo = false → 已到最早保留的版本「第一稿」
```

**`while (history.undo())` 这个写法本身就是一段设计说明。**

因为 `undo()` 返回 `boolean`，它可以**直接当循环条件**：

```java
while (history.undo()) {
    // 每成功撤销一步就执行一次
}
```

原项目做不到这一点，因为 `popMemento()` 返回的是 `DocumentMemento`：

```java
while (history.popMemento() != null) {      // ← 能编译，但每次循环都弹掉一张
    doc.restore(history.popMemento());      // ← 又弹掉一张
}
```

**这段代码每轮弹两张快照。** 而它看起来完全正常 —— 这就是"返回值既是结果又是信号"的代价。

**`undo()` 返回 `boolean` 之后，"信号"和"数据"彻底分开：**

```text
  boolean  undo()        →  只回答"有没有撤成功"
  Editor   自己          →  回答"现在是什么状态"
```

#### 6.6.3 重做一次

```java
System.out.println("  重做一次 = " + history.redo() + " → 「" + editor.text() + "」");
```

```text
redo：
  redo 非空
  target = redo.pop() = #5     redo = [ #4 ]
  capture → #6 (09:00:02.500) 内容 = "第一稿"（当前状态）
  undo.push(#6)                undo = [ #6 | #1 ]
  trim: 2 > 10 ? no
  restore(#5) → "第二稿"        ← #5 是"撤销第2步之前"的状态
```

真实输出（第 79-81 行）：

```text
  统计 可撤 0 步 | 可重 2 步 | 已拍 5 淘汰 0 编辑 2
  重做一次 = true → 「第二稿」
  统计 可撤 1 步 | 可重 1 步 | 已拍 6 淘汰 0 编辑 2
```

**注意 `undo` 栈的变化：`[#1]` → `[#6 | #1]`。**

栈里又变成两张了，所以"可撤"从 0 变成 1。

**这意味着一件事：重做之后，撤销能"撤掉这次重做"。**

用户按"重做"之后，再按"撤销"，应该回到"重做之前"的状态 —— 也就是"第一稿"。
这条规则在真实编辑器里几乎都成立，但很少有人明确说它是**怎么实现的**：

> 答案是：**重做时也要往 `undo` 里压一张"重做前的状态"快照。**

如果只 `undo.push(target)`（也就是 `#5`，内容是"第二稿"），
那么下次撤销会恢复"第二稿" —— 正文不变，用户以为撤销坏了。
**这个 bug 在 §6.1.8 已经推导过一次，是同一个原因。**

#### 6.6.4 新编辑清空重做栈

```java
history.edit(EditKind.REPLACE, "另起一段，重做作废", e -> e.replaceAll("新的一段"));
```

```text
capture → #7 (09:00:03.000) 内容 = "第二稿"（当前状态）
action  → replaceAll("新的一段") → text = "新的一段"（4 字）
undo.push(#7) → undo = [ #7 | #6 | #1 ]          size = 3
trim: 3 > 10 ? no
redo.clear()  → redo = []                        ← 关键
edits = 3
```

真实输出（第 82-84 行）：

```text
  新编辑后 → 「新的一段」
  统计 可撤 2 步 | 可重 0 步 | 已拍 7 淘汰 0 编辑 3
  再 redo = false → 重做栈已被新编辑清空
```

**`redo.clear()` 只出现一次，就在 `edit()` 的成功路径上（`SnapshotHistory.java:148`）。**

它**不在** `capture()` 里，**不在** `undo()` 里，**不在** `redo()` 里。

#### 6.6.5 为什么"新编辑必须清空重做栈"

想象不清空会怎样：

```text
  1. 用户输入 A  →  undo = [A]         redo = []
  2. 撤销        →  undo = []          redo = [A]
  3. 用户输入 B  →  undo = [B]         redo = [A]     ← 如果不清空
  4. 重做        →  恢复到 A           ← 灾难：B 凭空消失
```

**第 4 步的结果是"用户输入了 B，按重做，B 没了，变成了 A"。**

这在用户感知里就是"数据丢失"，而且**没有任何提示**。

**更深的问题：`redo` 栈里的快照基于一条已经不存在的时间线。**

```text
  时间线 1：  ∅ → A              （用户走了这条路）
  时间线 2：  ∅ → A → (撤销)      （重做栈记录的是"回到 A"）
  现在：      ∅ → B              （用户改走了这条路）
```

重做栈里的"回到 A"是从时间线 2 来的。而时间线 2 已经被 B 替代了。
**把一条已经作废的时间线上的状态拿过来，就是"分支历史被悄悄合并"** ——
而合并规则完全由实现细节决定，用户毫不知情。

**所以清空不是"图省事"，而是"拒绝在两条时间线之间做未定义的跳跃"。**

**真实产品里的两种处理：**

| 处理 | 例子 | 效果 |
|---|---|---|
| 清空重做栈 | 几乎所有编辑器 | 简单、可预期 |
| 保留重做栈（分支历史） | 部分 IDE 的 Local History | 复杂，需要 UI 支持"树状历史" |

本项目选第一种。`§7` 会讨论第二种要改哪里。

#### 6.6.6 `edit()` 里 `redo.clear()` 的位置

```java
undo.push(before);
trim();
redo.clear();          // ← 在 push 和 trim 之后
recordedEdits++;
notifyChanged();
```

**为什么放在 `trim()` 之后？**

因为 `trim()` 可能抛异常（虽然实际不会，`removeLast` 有守卫）。
如果 `redo.clear()` 在 `trim()` 之前，而 `trim()` 抛了，那么：

```text
  redo 已经清空了      ← 副作用发生了
  undo.push 已执行
  但 edit() 抛异常     ← 调用方以为编辑失败
```

**而调用方如果重试，`redo` 已经被清掉，无法恢复。**

**这是一条通用规则：把"不可回滚的清理操作"放在最后。**

在 `edit()` 里，顺序是：

```text
  1. capture        （纯读，无副作用）
  2. action         （唯一可能失败的一步）
  3. undo.push      （改动 undo）
  4. trim           （改动 undo + evicted）
  5. redo.clear     （改动 redo）      ← 最后
  6. recordedEdits++（计数）
  7. notifyChanged  （外部回调）        ← 真正最后
```

**`notifyChanged()` 排在最后，是因为它是唯一会执行外部代码的一步。**
监听器里如果抛异常，前面的状态已经全部就位 —— 不会出现"栈改了一半、界面刷新了一半"。

（严格说，监听器抛异常会让 `edit()` 抛出，调用方以为编辑失败，但其实已经成功了。
见 §8 的"已知局限"。）

---

### 6.7 六段串起来：备忘录模式的完整生命周期

把六段压成一张图，就是备忘录模式的全部动作：

```text
                       ┌─────────────────────────────┐
                       │        Editor (Originator)   │
                       │  text / cursor / selection   │
                       └─────────────────────────────┘
                          ▲                       │
                    restore()                snapshot()
                          │                       │
                          │                       ▼
                       ┌─────────────────────────────┐
                       │  Snapshot (窄接口)           │
                       │  └─ Editor$Memento (私有)    │
                       │      text / cursor / sel     │
                       │      metadata / ownerToken   │
                       └─────────────────────────────┘
                                       │
                                       │ 只能当作 Snapshot 握着
                                       ▼
                       ┌─────────────────────────────┐
                       │  SnapshotHistory (Caretaker) │
                       │  undo: Deque<Snapshot>       │
                       │  redo: Deque<Snapshot>       │
                       └─────────────────────────────┘
                                       │
                                       │ notifyChanged()
                                       ▼
                       ┌─────────────────────────────┐
                       │  HistoryListener             │
                       │  └─ ConsoleHistoryListener   │
                       └─────────────────────────────┘
```

四个方法，四次数据流动：

| 方法 | 谁调 | 动作 | 栈的变化 |
|---|---|---|---|
| `edit(kind, detail, action)` | 调用方 | 拍 → 改 → 入 undo | `undo.push`，可能 `removeLast`，`redo.clear` |
| `undo()` | 调用方 | 弹 undo → 拍 → 入 redo → 恢复 | `undo.pop`，`redo.push` |
| `redo()` | 调用方 | 弹 redo → 拍 → 入 undo → 恢复 | `redo.pop`，`undo.push`，可能 `removeLast` |
| `reset(label)` | 调用方 | 清空两栈 → 拍基线 | `undo = [新基线]`，`redo = []` |

**看这张表的第三列，会发现一个对称性：**

```text
  edit()：  拍 → 改 → 入 undo
  undo()：  拍 → 改 → 入 redo
  redo()：  拍 → 改 → 入 undo
```

**三个方法都是"先拍，再改，再入栈"。** 只有目标栈不同。

**这个对称性可以直接提炼成一句实现口诀：**

> **任何"改变当前状态"的操作，都必须在改变前拍一张，并把它放进"反向栈"。**

- `edit` 的反向栈是 `undo`（能撤掉这次编辑）；
- `undo` 的反向栈是 `redo`（能撤掉这次撤销）；
- `redo` 的反向栈是 `undo`（能撤掉这次重做）。

**`reset` 是唯一不合口诀的方法** —— 它不拍"重置之前的快照"，
因为它是有意的"断代"操作：保存之后，撤销栈的语义要跟着重来（见 `SnapshotHistory.java:192-207`）。

#### 六段各自覆盖了哪一条不变量

| Demo | 验证的不变量 | 破坏它会发生什么 |
|---|---|---|
| 1 | 拍在改之前 / 撤销重做互补 | 撤销无效或撤销两步 |
| 2 | 粒度由 `edit()` 边界决定 | 一次撤销只退半个字 |
| 3 | 失败不入栈且回滚 | 状态脏了但历史没记 |
| 4 | 有容量上限 | 内存无限增长 |
| 5 | 快照不透明 + 归属校验 | 跨文档污染 |
| 6 | 撤到底返回 false / 新编辑清空 redo | 分支历史被悄悄合并 |

**这六条正好对应探针 P1-P10 里的核心十条。**

- Demo 1 覆盖 P2（返回值被忽略导致脱节）、P3（null 语义）；
- Demo 2 覆盖 P5（只存 content，无法表达"一段操作"）、P9（pop 与 restore 非原子）；
- Demo 3 覆盖 P9 的失败分支；
- Demo 4 覆盖 P8（无上限）；
- Demo 5 覆盖 P1（`restore(null)` NPE）、P4（`public record` 可伪造）、P10（不绑定 Document）；
- Demo 6 覆盖 P2 的另一种表现（`while (popMemento() != null)` 每次弹两张）。

**P6（`setContent(null)` 一路进历史）和 P7（`Stack extends Vector`）没有专属 Demo**，
它们在代码结构层面被消除了：

- P6 → `EditorLimits.requireText()` 在 `replaceRange` / `replaceAll` 里拦 null 和超长（见 §5.5）；
- P7 → 用 `ArrayDeque` 替换 `Stack`（见 §4.10）。

**"被结构消除"比"被测试覆盖"更彻底** —— 测试只能证明"这次没发生"，
结构能证明"不可能发生"。

#### 六段合起来的规模

```text
  入口          Client.main()            → 6 个 private static 方法
  输出          86 行 / 3857 字节
  快照总数      27 张（六段合计 sequence 之和：9+5+3+7+1+7 = 32，含 Demo5 的 #999）
  最终一致性    SHA-256 三次运行完全一致
```

**为什么能三次一致？** 因为所有的不确定性都被挤出去了：

| 不确定性 | 处理 |
|---|---|
| 时间 | `SteppedClock` 注入，固定基点 + 固定步长 |
| 迭代顺序 | 全部用 `Deque` / `List`（插入有序），没有 `HashMap` |
| 哈希顺序 | 没有任何代码打印 `hashCode()` |
| 随机数 | 没有用到 |
| 线程调度 | 单线程 |
| 默认字符集 | 全部用 `UTF-8` 读源文件，`System.out` 由 `-Dfile.encoding=UTF-8` 固定 |

**"可复现"不是天赋，是逐项排除的结果。** 少排除一项，哈希就会飘。

---

## 7. 扩展对比：加需求时改哪里

这一章回答一个问题：**"如果业务变了，改动会集中在哪里？"**

好的设计不是"永远不用改"，而是"改的时候知道该动哪几个文件"。
一个模式的价值，很大程度上体现在**改动的局部性**上。

下面十个扩展，按"改动面从小到大"排。

**阅读提醒：** 本章所有代码都是**示意代码**，用于说明改动点，
不是本项目里的真实文件。真实文件在 §5 已经逐个讲完。

---

### 7.1 快照要多存一个字段

#### 需求

文档编辑器加了"批注"功能：每段文字可以挂一条评论。批注也要跟着撤销/重做走。

#### 改动点

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| `Editor` | 加字段 `comments`、拍快照时带上、恢复时写回 | +12 |
| `Editor.Memento` | record 组件 +1 | +1 |
| 新增 `Comment` record | id / anchor / text / author | 新建 ~40 |
| `EditorLimits` | 可能要加"批注数量上限" | +8 |
| `Client` | 加一段 Demo（可选） | +30 |

**`Snapshot` / `SnapshotHistory` / `HistoryStats` 一行都不用改。**

#### 关键代码

```java
// Editor 里加字段
private List<Comment> comments = List.of();

// 拍快照
public Snapshot snapshot(SnapshotMetadata metadata) {
    return new Memento(text, cursor, selection, List.copyOf(comments), metadata, ownerToken);
}

// 恢复
public void restore(Snapshot snapshot) {
    // ... 校验 ...
    this.text = memento.text();
    this.cursor = memento.cursor();
    this.selection = memento.selection();
    this.comments = memento.comments();      // ← 新增一行
}

// Memento 加一个组件
private record Memento(String text, int cursor, TextRegion selection,
                       List<Comment> comments,
                       SnapshotMetadata metadata, Object owner) implements Snapshot { }
```

#### 为什么 `SnapshotHistory` 不用改

因为它只声明 `Deque<Snapshot>`，从来不读快照里的内容：

```java
private final Deque<Snapshot> undo = new ArrayDeque<>();
```

**这是窄接口的直接回报：Caretaker 对"快照里有什么"完全无知，
所以"快照里多了什么"与它无关。**

如果当初 `Snapshot` 上开了 `text()` / `cursor()`，
那么加字段时一定会有人问"要不要在接口上也加一个 `comments()`" ——
**接口一宽，每次加字段都会引发一次"要不要暴露"的讨论。**

#### 影响面

**不受影响：**

- `undo()` / `redo()` 的栈操作逻辑；
- `SnapshotHistory.edit()` 的原子性；
- 监听器的打印格式（`HistoryStats` 没变）；
- 归属校验（`ownerToken` 没变）。

**受影响：**

- 快照的内存占用（每张多一个 `List` 引用，8 字节 + 列表本身）；
- `List.copyOf(comments)` 是**深拷贝一层**，如果 `Comment` 里还有可变对象，要再拷一层。

**注意这个坑：** `List.copyOf()` 只保证"列表本身不可变"，
不保证"列表里的元素不可变"。如果 `Comment` 是可变的，那么：

```text
  快照 A 拍的 comments = [c1]     （c1 是同一个对象引用）
  用户改了 c1 的文本
  恢复快照 A → 拿到的 c1 是改过的  ← 快照被"污染"了
```

**所以 `Comment` 必须也写成 `record`（不可变）。** 这条规则在 §4.6 讲 `TextRegion` 时已经说过，
加字段时它会再来一次。

---

### 7.2 新增一种编辑类型

#### 需求

产品说："用户拖拽调整段落顺序"也应该在撤销面板上显示成一个专门的类型。

#### 改动点

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| `EditKind` | 加一个枚举值 | +1 |
| `Client` | 如果需要演示，加一段 | +20 |

**就这两处。**

#### 关键代码

```java
public enum EditKind {
    BASELINE("基线"),
    TYPING("输入"),
    DELETION("删除"),
    SELECTION("选区"),
    REPLACE("整段替换"),
    REORDER("段落重排"),        // ← 新增
    UNDO("撤销"),
    REDO("重做");
    // ...
}
```

#### 为什么这么便宜

因为 `EditKind` 只出现在三个地方：

1. `SnapshotMetadata` 的一个组件（存起来，只用于显示）；
2. `HistoryStats` 不碰它；
3. `switch` 语句**一个都没有**。

```java
// 全项目对 EditKind 的唯一用法就是：
sb.append('#').append(sequence).append(' ').append(kind.label());
```

**没有 `switch` 是"加枚举值很便宜"的根本原因。**

如果当初写成：

```java
switch (kind) {
    case TYPING -> sb.append("输入");
    case DELETION -> sb.append("删除");
    // ...
}
```

那么加一个值就会触发编译器警告（非穷尽 switch）—— 这其实是好事。
但如果有十几处 switch，每处都要改，那就是灾难。

**枚举带 `label` 字段的设计，把所有"类型 → 文本"的映射压到了一处。**

#### 影响面

**零影响。** 因为 `EditKind` 不参与任何控制流，
它只是"快照的一个标签"。

**这是一个值得记住的模式：**

> **枚举如果只用来"分类"，它就是最便宜的扩展点；
> 枚举如果用来"决策"（switch 驱动行为），每加一个值都要改所有决策点。**

本项目让 `EditKind` 停在"分类"这一层 —— 它不决定"要不要拍快照"，
也不决定"能撤几步"。这些决定在 `SnapshotHistory` 里，与 `kind` 无关。

---

### 7.3 内存快照 → 磁盘持久化

#### 需求

用户关掉应用再打开，撤销栈还在。

#### 改动点

这是**第一个真正有破坏性的扩展**。

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| 新增 `SnapshotStore` 接口 | `save` / `load` / `delete` | 新建 ~40 |
| 新增 `FileSnapshotStore` | 序列化到磁盘 | 新建 ~150 |
| `Editor.Memento` | 实现 `Serializable` 或提供 `encode()/decode()` | +40 |
| `Snapshot` | 可能要加 `id()` 方法用于持久化索引 | +5 |
| `SnapshotHistory` | 两个栈改成"内存缓存 + 磁盘后端" | +80 |
| `Editor` | 需要 `fromSnapshot()` 静态工厂（不能直接反序列化私有 record） | +20 |

**这次 `Snapshot` 接口必须改** —— 因为持久化需要"稳定标识"。

#### 为什么这会破坏窄接口

原始的 `Snapshot` 只有 `metadata()`。要落盘，`SnapshotStore` 必须能：

```java
void save(String id, Snapshot snapshot);       // ← 但它读不到内容
Snapshot load(String id);                      // ← 但它造不出 Memento
```

**两个方向都被"不透明"挡住了。**

有三种解法：

**解法 A：让 `Memento` 实现 `Serializable`，整条 `Deque` 一起序列化**

```java
private record Memento(...) implements Snapshot, Serializable { }
```

优点：改动最小（一行）。
缺点：

- `serialVersionUID` 要手工维护，改一个字段就导致旧存档全废；
- Java 序列化不保证跨 JDK 版本兼容；
- **私有 record 序列化后，反序列化时仍需要 `Editor` 的 `ownerToken`** ——
  而 `ownerToken` 是 `new Object()`，序列化它等于序列化一个"新对象"，
  反序列化后与原来的 `Editor` 不匹配。

**第三点是致命的：`ownerToken` 与序列化天然冲突。**

**解法 B：让 `Editor` 提供 `encode()` / `decode()`，绕过 `Memento`**

```java
// Editor 里新增
public byte[] encode(Snapshot snapshot) {
    if (!(snapshot instanceof Memento m)) throw new SnapshotRejectedException(...);
    // 序列化 m.text / m.cursor / m.selection / m.metadata
}

public Snapshot decode(byte[] bytes) {
    // 反序列化四个字段，然后 new Memento(..., ownerToken)   ← 用当前 editor 的令牌
}
```

优点：

- **`ownerToken` 问题自动消失** —— `decode` 时用的是当前 `Editor` 的令牌；
- 序列化格式由自己控制，可以版本化（加一个 `version` 字节）；
- `Memento` 仍然 private，不暴露给任何人。

缺点：

- `Editor` 多了两个 public 方法，职责变重（"能拍快照"变成"能拍能编解码"）；
- 格式演进要自己管。

**解法 C：引入第三层"快照 DTO"**

```java
public record SnapshotData(String text, int cursor, int selStart, int selEnd,
                           int sequence, EditKind kind, String detail, Instant capturedAt) { }
```

`Snapshot` 上开一个方法：

```java
public interface Snapshot {
    SnapshotMetadata metadata();
    SnapshotData export();          // ← 把内容导成可持久化的 DTO
}
```

优点：持久化层只认 DTO，与 `Memento` 完全解耦。
缺点：**`Snapshot` 接口不再窄了** —— 任何拿到 `Snapshot` 的代码都能读全文。

#### 本项目的选择：如果真的要做，用解法 B

理由：

1. `ownerToken` 的冲突只能在 `Editor` 内部解决（只有它知道令牌）；
2. 窄接口保住了（`Snapshot` 一个方法都不加）；
3. 代价是 `Editor` 多了两个方法，但它本来就是"负责造快照"的角色。

**改动示范：**

```java
// Editor 新增
public byte[] encode(Snapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    if (!(snapshot instanceof Memento m) || m.owner() != ownerToken) {
        throw new SnapshotRejectedException("不能编码别人的快照");
    }
    // 用 DataOutputStream 写：版本号 + text + cursor + 选区 + 元信息
    // ...
}

public Snapshot decode(byte[] bytes) {
    // 用 DataInputStream 读，校验版本号
    // 最后 new Memento(text, cursor, selection, metadata, ownerToken)
}
```

#### 影响面

| 组件 | 影响 |
|---|---|
| `Editor` | +2 个 public 方法，+40 行 |
| `Memento` | 零改动（仍然私有、仍然不可变） |
| `Snapshot` | 零改动（一个方法都不加） |
| `SnapshotHistory` | 构造时"从磁盘加载"、每次 `edit` 后"异步刷盘"，+80 行 |
| `undo/redo` 逻辑 | **零改动** —— 栈操作与存储介质无关 |

**最后一行是这个设计最有价值的地方：**
持久化只是在"栈的外面"加了一层，`undo()` 的五行代码一个字都不用动。

**如果当初把存储逻辑写进 `SnapshotHistory.undo()`：**

```java
public boolean undo() {
    // ...
    disk.save(undo);            // ← 存储耦合进业务逻辑
    // ...
}
```

那么"换成内存存储"就要改 `undo()`，"加缓存"又要改一次，
"改成异步刷盘"还要改一次。**每次存储策略变化，都要求改动核心逻辑。**

---

### 7.4 合并连续操作：500 毫秒内的输入算一步

#### 需求

用户快速敲字时，连续输入的字符不应该每个都占一步撤销。
规则：**两次输入间隔 < 500ms，且中间没有非输入操作，就合并成一步。**

#### 改动点

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| `SnapshotHistory` | 加字段 `lastEditAt`、`pendingKind`；`edit()` 里判断是否合并 | +30 |
| `EditorLimits` | 加常量 `MERGE_WINDOW` | +5 |
| `Client` | 加一段 Demo 验证合并 | +40 |

**`Snapshot` / `Editor` / `Memento` 零改动。**

#### 关键代码

```java
// SnapshotHistory 新增字段
private Instant lastEditAt;
private EditKind lastKind;

public void edit(EditKind kind, String detail, Consumer<Editor> action) {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(action, "action");

    Instant now = clock.get();
    boolean mergeable = kind == EditKind.TYPING
            && lastKind == EditKind.TYPING
            && lastEditAt != null
            && Duration.between(lastEditAt, now).compareTo(EditorLimits.MERGE_WINDOW) < 0;

    Snapshot before = capture(kind, detail);
    try {
        action.accept(editor);
    } catch (RuntimeException e) {
        editor.restore(before);
        throw e;
    }

    if (!mergeable) {
        undo.push(before);            // ← 不合并才入栈
        trim();
        redo.clear();
        recordedEdits++;
    }
    lastEditAt = now;
    lastKind = kind;
    notifyChanged();
}
```

#### 关键陷阱：合并时"已拍"还在涨

合并的逻辑是"**不入栈，但还是拍**"。

于是 `sequence` 会涨，而 `undo.size()` 不涨 ——
`已拍 - (可撤 + 可重)` 这个差值会变大。

**这会让 §6.4.8 那个自检恒等式失效：**

```text
  sequence = undo.size() + redo.size() + evicted + 失败次数 + 合并次数
```

**所以要再做一件事：把"合并次数"也统计进来。**

```java
private int merged;

public HistoryStats stats() {
    return new HistoryStats(undo.size(), redo.size(), sequence, evicted, recordedEdits, merged);
}
```

`HistoryStats` 加一个组件，`summary()` 加一段：

```java
" | 合并 " + merged
```

**这个改动暴露了一个设计事实：`HistoryStats` 的五个字段不是随便挑的，
它们构成了一个"账面必须平"的体系。** 加一个新的栈操作类型，就要加一个新的计数项。

**不做这一步会怎样？** `已拍` 会持续大于 `可撤 + 可重 + 淘汰 + 失败`，
运维看到这个数字会以为"有内存泄漏"，然后花两天去查一个不存在的问题。

#### 更麻烦的问题：合并之后 undo 的目标变了

假设用户快速敲了 "abcd"：

```text
  不合并：
    edit("a") → undo = [#1]  （#1 = ""）
    edit("ab") → undo = [#2 | #1]  （#2 = "a"）
    edit("abc") → undo = [#3 | #2 | #1]
    edit("abcd") → undo = [#4 | #3 | #2 | #1]
    undo 1 次 → "abc"

  合并（只有第一次入栈）：
    edit("a") → undo = [#1]   （#1 = ""）
    edit("ab") → 不入栈
    edit("abc") → 不入栈
    edit("abcd") → 不入栈
    undo 1 次 → ""            ← 直接回到空
```

**这就是合并想要的效果。** 但它有一个隐含前提：

> **合并窗口内不能有"其他来源"的修改。**

如果有（比如后台的自动格式化），那么被合并掉的那几步里
可能夹着一个非用户操作的改动，撤销时会一起被撤掉。

**所以合并的条件必须收紧到"完全相同类型 + 无其他操作"。**
上面的 `lastKind == EditKind.TYPING` 就是这个意思。

#### 影响面

| 组件 | 影响 |
|---|---|
| `edit()` | +8 行判断 |
| `HistoryStats` | +1 组件，+1 段 summary |
| `undo` / `redo` | **零改动** —— 合并只影响"入不入栈"，不影响"怎么弹" |
| 内存 | 大幅下降（连续输入只留一张） |

**注意 `redo.clear()` 也必须在合并条件内。**

如果不合并才清 redo，那么"用户在撤销之后开始快速输入"会走这条路径：

```text
  1. 撤销一步  →  redo = [X]
  2. 快速输入 "a" → mergeable = false（lastKind 是 UNDO）→ 入栈 + clear redo ✓
  3. 继续输入 "b" → mergeable = true → 不入栈 ✓（redo 已经是空的）
```

没问题。但如果第 2 步的 `lastKind` 判断写漏了，第 2 步就会被合并掉，
`redo` 不会被清空 —— **于是"输入了新内容，但还能重做旧内容"**，回到 §6.6.5 那个灾难。

**合并逻辑的每一行都要问一句："如果是第一次编辑，它还会执行吗？"**

---

### 7.5 全量快照 → 差量快照

#### 需求

文档很大（几百 KB），全量快照太占内存，改成只存"每次变化的差异"。

#### 改动点

这是**改动最大**的一个扩展，几乎要重写 `Editor` 和 `SnapshotHistory`。

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| 新增 `TextDelta` record | `from` / `to` / `inserted` | 新建 ~60 |
| `Editor.Memento` | 从"存 text"改成"存 delta 或全量" | 重写 |
| `Editor` | 加 `applyDelta()`；`snapshot()` 要记录"基准版本号" | +50 |
| `SnapshotHistory` | 恢复时要从基准 + 一串 delta 重放 | +80 |
| 新增 `Compaction` 逻辑 | 每 N 步做一次全量，作为新的重放起点 | 新建 ~60 |

#### 关键设计问题

**问题一：delta 需要"基准"。**

全量快照是自包含的：拿到它就能恢复。
delta 不是：它只说"从上一版到这里，删了 `[3,5)` 插入了 `"ab"`"。

所以每张 delta 必须记录：

```java
public record TextDelta(int baseVersion, int from, int to, String inserted) { }
```

**"从 baseVersion 应用这个 delta，得到 version"。**

**问题二：撤销到很早的版本时，要重放一串。**

```text
  V0 --d1--> V1 --d2--> V2 --d3--> V3 --d4--> V4
  现在在 V4，撤销到 V1
  → 要做 d4 的逆、d3 的逆、d2 的逆        （三次）
```

**逆运算比正运算难得多。** `insert(i, "abc")` 的逆是 `delete(i, i+3)`，
看起来简单。但如果是 `replace(3, 5, "xy")`，逆运算需要知道"原来那一段是什么" ——
**而 delta 里没有存原文。**

**所以 delta 必须存"被替换掉的原文"：**

```java
public record TextDelta(int baseVersion, int from, int to,
                        String removed, String inserted) { }
```

这样逆运算就是 `replace(from, from + inserted.length(), removed)`。

**代价：`removed` 和 `inserted` 加起来 ≈ 一次编辑改动的两倍。**
对小改动很划算（改 10 个字符 vs 存 400KB），对"全选替换"完全不划算。

**问题三：连续小 delta 会累积成"实际上等于全量"。**

如果用户从头到尾重写一遍文档，那 N 个 delta 加起来就是全文的两倍。
**所以必须有 compaction（压缩）。**

```java
// 每 20 步做一次全量，作为新的重放起点
if (deltaCount >= EditorLimits.COMPACT_THRESHOLD) {
    snapshots.add(editor.snapshot(metadata));   // 全量
    dropOldDeltas();                            // 清掉之前的
}
```

**这引入了"快照有两种类型"的复杂度：**

```java
sealed interface Snapshot permits FullSnapshot, DeltaSnapshot { }
```

于是所有"看起来只是存了个对象"的地方，都要开始判类型。

#### 项目的取舍：不做

**本项目停留在全量快照，理由是"演示的文档只有几十个字符"。**

但这个扩展的价值在于：**它说明了一个通用规律 ——
"节省内存"几乎总是以"增加状态复杂度"为代价。**

| 方案 | 内存 | 恢复代价 | 实现复杂度 |
|---|---|---|---|
| 全量（本项目） | 高 | O(1) | 低 |
| 纯 delta | 低 | O(n) | 高 |
| delta + 定期 compaction | 中 | O(n) 但 n 有界 | 很高 |

**真实产品（VS Code、Word）用的都是第三种。** 因为它们要处理几 MB 的文件。

**判断标准：文档平均大小 < 100KB 时，全量快照完全够用；
超过 1MB 才值得考虑 delta。** 过早优化会带来一堆没人愿意维护的 sealed interface。

---

### 7.6 单文档 → 多文档

#### 需求

一个应用里有多个打开的文档，每个都有自己的撤销栈，切换文档时面板要跟着换。

#### 改动点

**这次改动非常小，因为 `SnapshotHistory` 已经绑定了唯一一个 `Editor`。**

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| 新增 `EditorManager` | `Map<String, SnapshotHistory>` | 新建 ~60 |
| `Client` | 演示切换 | +40 |
| `SnapshotHistory` | **零改动** | 0 |
| `Editor` | **零改动** | 0 |

#### 关键代码

```java
public final class EditorManager {

    private final Map<String, SnapshotHistory> histories = new LinkedHashMap<>();
    private final Supplier<Instant> clock;

    public EditorManager(Supplier<Instant> clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SnapshotHistory open(String docId, String initialText) {
        Editor editor = new Editor(initialText);
        SnapshotHistory history = new SnapshotHistory(editor, 20, clock);
        histories.put(docId, history);
        return history;
    }

    public void close(String docId) {
        histories.remove(docId);       // ← 整个历史跟着 Editor 一起走
    }

    public Optional<SnapshotHistory> find(String docId) {
        return Optional.ofNullable(histories.get(docId));
    }
}
```

#### 为什么 `SnapshotHistory` 一行都不用改

因为它从构造器起就只认一个 `Editor`：

```java
public SnapshotHistory(Editor editor, int maxDepth, Supplier<Instant> clock) {
    this.editor = Objects.requireNonNull(editor, "editor");
    // ...
}
```

**`editor` 是 `final` 的，没有 `setEditor`。**

这就是探针 P10（"History 不绑定 Document → 跨文档污染"）的修法带来的额外红利：
**绑死之后，"多文档"自动变成了"多实例"，而不是"多状态"。**

对比一下原项目要多文档支持，需要改什么：

```java
public class History {
    private Stack<DocumentMemento> history = new Stack<>();     // 一份历史
    private Stack<DocumentMemento> redoStack = new Stack<>();
    // 没有 Document 字段
}
```

原项目里 `History` 和 `Document` 是两个独立对象，**可以任意组合**：

```java
Document docA = new Document("A");
Document docB = new Document("B");
History shared = new History();           // 一份历史，两个文档共用

shared.pushMemento(docA.save());           // A 的快照
docB.restore(shared.popMemento());         // 塞给 B —— 编译通过，运行"成功"
```

**原项目要支持多文档，必须额外加一道"运行时检查"：**

```java
public class History {
    private Document owner;               // ← 新增字段

    public History(Document owner) { this.owner = owner; }

    public void pushMemento(DocumentMemento m, Document doc) {
        if (doc != owner) throw new IllegalArgumentException("不能跨文档");
        // ...
    }
}
```

**而这道检查还只是"运行时"。** 本项目做在**构造期**：
`SnapshotHistory` 没有 `Document` 参数之外的构造方式，`editor` 是 `final`。

> **把约束放在构造器里，比放在方法体里早一步。**
> 早一步，就少一条代码路径能绕过它。

#### 影响面

**零改动：** `Editor` / `Snapshot` / `SnapshotHistory` / `HistoryStats` / `Memento`。

**新增：** 一个 60 行的 `EditorManager`。

**这不是巧合。** "每个对象有自己的状态"是面向对象最基础的约定。
当你要"多份"时，正确的做法是"多几个实例"，而不是"给一个实例加个 map"。

**反例（常见写法）：**

```java
public class SnapshotHistory {
    private final Map<String, Deque<Snapshot>> undos = new HashMap<>();   // ← 把多文档塞进一个历史
    private final Map<String, Deque<Snapshot>> redos = new HashMap<>();
    private final Map<String, Editor> editors = new HashMap<>();
}
```

这样写之后，`edit()` / `undo()` 的每个方法都要多一个 `docId` 参数，
每一次栈操作都要先 `map.get(docId)` —— **把"多态"退化成了"查表"。**

---

### 7.7 线性历史 → 分支历史

#### 需求

用户撤销几步、改了新内容之后，希望能"回到之前那条时间线"（类似 Photoshop 的 History 面板，
或者是 Git 的分支）。

#### 改动点

**这是第二个有破坏性的扩展**，因为"线性两个栈"这个模型本身不成立。

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| 新增 `SnapshotNode` | `parent` / `children` / `snapshot` | 新建 ~50 |
| `SnapshotHistory` | 两个 `Deque` 换成一棵树 + 一个"当前位置"指针 | 重写 ~200 |
| `HistoryStats` | `undoDepth/redoDepth` 换成 `nodeCount/currentPathLength` | 重写 |
| `undo()` / `redo()` | 语义从"弹栈"变成"沿 parent/child 移动" | 重写 |

#### 模型对比

**现在（两个栈）：**

```text
            undo                          redo
  [ #4 #3 #2 #1 ]                    [ #5 #6 ]
   ▲
 栈顶=当前位置之前的状态
```

**分支（一棵树 + 一个游标）：**

```text
                    #1
                    │
                    #2
                   ╱  ╲
                 #3    #5        ← #5 是"撤销到 #2 之后改的新内容"
                 │      │
                 #4    #6
                 ▲
              current
```

每个节点有 `parent` 和 `List<SnapshotNode> children`。
"撤销"= 移到 `parent`；"重做"= 移到 `children.get(0)`（如果有多个子节点，
需要一个"选择哪一支"的 UI）。

#### 关键代码骨架

```java
final class SnapshotNode {
    private final Snapshot snapshot;
    private final SnapshotNode parent;              // 根节点为 null
    private final List<SnapshotNode> children = new ArrayList<>();

    SnapshotNode(Snapshot snapshot, SnapshotNode parent) {
        this.snapshot = snapshot;
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }
}

public final class SnapshotHistory {
    private final SnapshotNode root;
    private SnapshotNode current;

    public void edit(...) {
        Snapshot before = capture(...);
        action.accept(editor);
        // 关键差异：不清空任何东西，而是在 current 下面挂一个新节点
        SnapshotNode node = new SnapshotNode(before, current);
        current = node;
        // 注意：current 原来的 children 依然保留 —— 那就是"另一条时间线"
    }

    public boolean undo() {
        if (current == root) return false;
        current = current.parent();
        editor.restore(current.snapshot());
        return true;
    }

    public boolean redo() {
        if (current.children().isEmpty()) return false;
        if (current.children().size() > 1) {
            throw new IllegalStateException("存在多个分支，请显式选择");
        }
        current = current.children().get(0);
        editor.restore(current.snapshot());
        return true;
    }
}
```

#### 为什么 `redo()` 会抛"多个分支"

**这正是分支历史的核心难点：`redo` 这个动作本身变得有歧义了。**

在线性模型里，"重做"永远只有一个答案（`redo` 栈顶）。
在树模型里，"重做"可能是 2 个、3 个答案中的任意一个。

**所以要区分两个 API：**

```java
public boolean redo() { ... }                    // 只有一条分支时才允许
public void redoTo(SnapshotNode target) { ... }  // 显式指定去哪
```

**UI 也必须跟着改：** 从"一个重做按钮"变成"一棵可点击的历史树"。

#### 代价清单

| 代价 | 说明 |
|---|---|
| 内存 | 树的所有分支都留着，比两个栈多得多 |
| 语义 | `redo()` 不再是"一步操作"，需要 UI 配合 |
| 序列化 | 树要落盘，必须处理 `parent` 引用（不能用朴素 JSON） |
| 测试 | 从"几条断言"变成"遍历整棵树验证路径" |

#### 项目的取舍：不做

**理由：分支历史是"产品级"需求，不是"模式级"需求。**

它需要 UI（历史面板）、需要用户教育（"你知道有两条时间线吗"）、
需要更复杂的序列化格式。**这三件事都不是备忘录模式该负责的。**

**但值得注意的是：这个扩展仍然只改 `SnapshotHistory` 一个文件。**

`Editor` / `Snapshot` / `Memento` / `ownerToken` 全部原样不动 ——
因为**"历史怎么组织"和"状态怎么存"是两个正交的问题**。

> **这是判断分层是否正确的可靠方法：改需求时，问自己"这次改动会同时影响
> 状态存储和历史组织吗？"如果答案是"不会"，说明分层是对的。**

---

### 7.8 单机 → 协同编辑

#### 需求

两个用户同时编辑同一篇文档，各自的撤销栈只影响自己的操作。

#### 改动点

**这是唯一一个"备忘录模式基本派不上用场"的扩展。**

| 方案 | 核心思想 | 与本项目的关系 |
|---|---|---|
| OT（Operational Transformation） | 变换操作，使其可交换 | 完全重写 |
| CRDT（Conflict-free Replicated Data Type） | 数据结构本身可合并 | 完全重写 |
| 锁 + 独占编辑 | 同一时刻只允许一个人改 | 不需要改 |

#### 为什么备忘录不够

**备忘录假设"状态是可以用一个快照完整描述的"，而协同编辑假设"状态是多方共同演化的结果"。**

```text
  用户 A：  在位置 3 插入 "hello"
  用户 B：  在位置 5 删除 2 个字符
```

如果 A 撤销自己的插入，**删掉的是 A 插的那 5 个字符**，
而 B 的删除操作会**因为位置偏移而失效** —— 因为 B 说的"位置 5"
在 A 撤销之后已经不指向原来的内容了。

**备忘录只能恢复"整篇文本"，它无法表达"只撤回我的那一笔"。**

#### 真实做法

**真实协同编辑器（Google Docs、Figma）的撤销模型是"按用户分别维护操作序列"，而不是"共享一份快照栈"。**

```java
public final class CollaborativeHistory {
    // 每个用户一个独立的历史，存的是"操作"而不是"快照"
    private final Map<String, Deque<Operation>> perUser = new HashMap<>();

    // 撤销 = 生成一个"逆操作"，广播给所有人
    public void undo(String userId) {
        Operation op = perUser.get(userId).pop();
        Operation inverse = op.inverse();          // ← 需要操作有逆
        broadcast(inverse);                        // ← 逆操作也要经过 OT/CRDT 变换
    }
}
```

**注意这里存的是 `Operation`，不是 `Snapshot`。**

这恰好是**命令模式（Command Pattern）**的地盘 —— 本项目其他章节重构过命令模式，
那里有 `undo()` 的原生支持。

#### 一个重要的边界结论

> **备忘录模式适用于"单线程、单一所有者、状态可完整快照"的场景。
> 一旦"状态"变成"多方共同演化的结果"，就该换命令模式 + OT/CRDT。**

**这不是"备忘录模式不够强"，而是"模式各有边界"。**
面试时如果被问"备忘录和命令的区别"，这就是最好的回答角度：

| | 备忘录 | 命令 |
|---|---|---|
| 撤销的基础 | 存"状态" | 存"操作" |
| 撤销的代价 | 内存 = 快照大小 × 步数 | 内存 = 操作大小 × 步数（通常更小） |
| 撤销的精度 | 整状态 | 单操作 |
| 适合协同吗 | 不适合 | 可以（操作可变换） |
| 实现难度 | 低 | 中（每个命令都要写逆操作） |

---

### 7.9 自动保存：定时快照

#### 需求

每 5 分钟自动存一次，用户能"回到 5 分钟前"，但**这个自动快照不能占用户的撤销步数**。

#### 改动点

| 文件 | 改什么 | 估行数 |
|---|---|---:|
| `SnapshotHistory` | 加一条独立的 `autoSaves: Deque<Snapshot>` | +30 |
| 新增 `takeAutoSave()` | 只拍不入 `undo` | +15 |
| `HistoryStats` | +1 组件 `autoSaveCount` | +10 |
| `Client` | 加 Demo | +35 |

#### 关键设计：两条独立的历史

```java
/** 用户的撤销栈 —— 与自动保存完全隔离。 */
private final Deque<Snapshot> undo = new ArrayDeque<>();

/** 自动保存点 —— 不参与撤销，只供"回到某个时间点"。 */
private final Deque<Snapshot> autoSaves = new ArrayDeque<>();

public void takeAutoSave(String label) {
    autoSaves.push(capture(EditKind.BASELINE, "自动保存：" + label));
    while (autoSaves.size() > EditorLimits.MAX_AUTO_SAVES) {
        autoSaves.removeLast();
    }
    notifyChanged();
}

public boolean restoreAutoSave(int index) {
    if (index < 0 || index >= autoSaves.size()) return false;
    // 注意：恢复自动保存点，本身应该算一次编辑吗？
    // 答案：算。因为它改变了当前状态，必须能被撤销。
    edit(EditKind.REPLACE, "回到自动保存点", e -> e.restore(autoSaves.toArray(new Snapshot[0])[index]));
    return true;
}
```

#### 关键问题：恢复自动保存，算不算一步撤销

**两个答案都有道理：**

| 答案 | 理由 | 后果 |
|---|---|---|
| 算 | 用户的正文被改了，必须能撤 | 撤销栈 +1 |
| 不算 | 用户只是"看了看历史版本" | 撤销栈不变，但正文变了 → **历史与正文脱节** |

**必须选"算"。**

第二行是探针 P2 的同一个错误：改了状态却没记账。
**任何改变当前状态的操作，都必须进撤销栈。**

**所以 `restoreAutoSave` 内部绕了一圈调用 `edit()`** —— 复用了原子性和回滚能力。

**这个写法值得注意：**

```java
edit(kind, detail, e -> e.restore(...));      // 把"恢复"本身当成一次编辑
```

**它把"恢复"降级成了"一种编辑"，从而自动获得了**：

- 失败回滚；
- 入撤销栈；
- 清空重做栈；
- 通知监听器。

**"把特殊操作归约成普通操作"，是减少代码路径的通用手段。**

#### 影响面

| 组件 | 影响 |
|---|---|
| `undo` / `redo` | **零改动**（自动保存栈与它们无关） |
| `edit()` | **零改动** |
| `HistoryStats` | +1 组件，`summary()` +1 段 |
| 内存 | 多一份固定大小的栈（比如 12 个点 = 1 小时） |

---

### 7.10 撤销粒度：按"词"而不是按"字符"

#### 需求

中文用户按一次撤销，希望退掉整个词（"合规审核"），而不是退掉一个字。

#### 改动点

**零改动。这是本章唯一一个不需要改任何代码的扩展。**

因为它**不是 `SnapshotHistory` 的职责**，而是 `Consumer<Editor>` 里写什么的问题：

```java
// 现在（Demo 2 的写法）—— 一段操作 = 一步
history.edit(EditKind.TYPING, "敲完一个词", e -> {
    for (char c : "合规审核".toCharArray()) {
        e.type(String.valueOf(c));
    }
});

// 按词撤销 —— 也是一段操作 = 一步，只是"段"的边界变了
history.edit(EditKind.TYPING, "输入一个词「合规审核」", e -> e.type("合规审核"));
```

**两种写法在 `SnapshotHistory` 眼里完全一样。**

#### 这说明了一件事

> **"撤销粒度"这个需求，有两种完全不同的实现位置：**
>
> 1. **在调用方控制**（本项目）：`edit()` 的边界由业务决定；
> 2. **在框架内控制**（如 Word）：根据输入法状态自动判断。

第 2 种需要监听输入法事件（compositionstart / compositionend），
这属于 UI 层，不属于模式层。

**本项目选第 1 种，因为它是"最小机制"：**

`SnapshotHistory` 只提供"把一段操作包成原子单元"的能力，
至于"什么算一段"，交给调用方。

**这和 §7.2 的 `EditKind` 是同一个设计哲学：**

> **框架提供"机制"，调用方提供"策略"。**

#### 一个反例：把粒度写进 `SnapshotHistory`

```java
// 常见但错误的写法
public void type(char c) {                      // ← 在 History 上开一个 type 方法
    if (Duration.between(lastTypeAt, now).getSeconds() > 5) {
        undo.push(capture(...));                // ← 按时间间隔决定粒度
    }
    editor.type(String.valueOf(c));
}
```

**这个写法把三个决策混在了一起：**

1. 什么时候拍（策略）；
2. 拍什么（`Editor` 的状态）；
3. 怎么改（`Editor.type`）。

**结果：`SnapshotHistory` 变成了一个"带撤销功能的编辑器"，
而 `Editor` 变成了它的一个字段。** 职责完全倒置。

**判断标准：如果 `SnapshotHistory` 上开始出现 `type` / `delete` / `select`
这类"编辑动词"，就说明粒度策略漏进了框架。**

---

### 7.11 十个扩展的改动面汇总

| # | 扩展 | 主要改动 | 改动文件数 | 破坏性 |
|---:|---|---|---:|---|
| 1 | 快照加字段 | `Editor` + `Memento` | 2 | 无 |
| 2 | 加编辑类型 | `EditKind` | 1 | 无 |
| 3 | 落盘持久化 | 新增 store + `Editor.encode/decode` | 3-4 | 中 |
| 4 | 合并连续操作 | `SnapshotHistory.edit` + `HistoryStats` | 2 | 低 |
| 5 | 差量快照 | 重写 `Editor` + `SnapshotHistory` | 5+ | 高 |
| 6 | 多文档 | 新增 `EditorManager` | 1（新增） | 无 |
| 7 | 分支历史 | 重写 `SnapshotHistory` + `HistoryStats` | 2 | 高 |
| 8 | 协同编辑 | 换模式（命令 + OT/CRDT） | 全部 | 极高 |
| 9 | 自动保存 | `SnapshotHistory` + `HistoryStats` | 2 | 低 |
| 10 | 按词撤销 | `Client` 里的写法 | 0 | 无 |

#### 从这张表能读出三件事

**（1）一半的扩展只动 1-2 个文件。**

这正是"分层清楚"的量化表现：`Editor` 管状态，`SnapshotHistory` 管历史，
`Snapshot` 管传输，三者之间只有窄接口。

**（2）破坏性的扩展都集中在"改变历史的组织结构"。**

- 差量快照 → 改变"快照里存什么"；
- 分支历史 → 改变"历史是什么形状"；
- 协同编辑 → 改变"历史属于谁"。

**这三件事都碰了备忘录模式的核心假设。**

**（3）有两个扩展一行代码都不用改。**

- 多文档：因为 `SnapshotHistory` 已经绑死一个 `Editor`，"多"就是"多实例"；
- 按词撤销：因为粒度由 `edit()` 的边界决定，而边界在调用方手里。

**"不用改"往往比"改动很小"更能说明设计对了。**

#### 一句话总结

> **好的设计不是"预见到所有需求"，而是"让每次变化只落在它该落的地方"。**

本项目没有为"多文档"预留任何代码，但加多文档时只写了一个 60 行的管理器；
没有为"按词撤销"预留任何扩展点，但它天然支持 ——
因为这些都是"约束放对位置"的自然结果，而不是"提前设计"的产物。

---

## 8. 优缺点与误区

这一章要回答三个问题：

1. **这套设计到底好在哪？**（不要空谈，每条都给本项目的具体证据）
2. **它付出了什么代价？**（诚实地说，不能只讲好话）
3. **最容易误解的是什么？**（七个高频误区，每个都给出错误写法）

最后加一节"已知局限"，把本项目**明知不完美但有意保留**的地方全部列出来。

---

### 8.1 五个真实优点

#### 优点一：撤销的实现复杂度被压到了最低

**这句话需要对比才能理解。**

如果用**命令模式**实现撤销，每个操作都要写一个"逆操作"：

```java
// 命令模式的典型写法
public interface Command {
    void execute();
    void undo();            // ← 每个命令都必须实现这个
}

public final class TypeCommand implements Command {
    private final Editor editor;
    private final String chunk;
    private final int position;

    public void execute() { editor.insert(position, chunk); }
    public void undo()    { editor.delete(position, position + chunk.length()); }   // ← 逆操作
}

public final class ReplaceAllCommand implements Command {
    private final Editor editor;
    private final String oldText;      // ← 必须"提前记住"旧内容，否则撤不回来
    private final String newText;

    public void execute() { editor.replaceAll(newText); }
    public void undo()    { editor.replaceAll(oldText); }     // ← 逆操作
}
```

**每个新操作，都要：**

1. 写一个类；
2. 想清楚它的逆操作是什么；
3. **提前记住所有"逆操作需要的信息"**（上面那个 `oldText`）。

**而备忘录模式只做一件事：拍一张全量照片。**

```java
// 本项目 undo() 的全部逻辑
Snapshot target = undo.pop();
redo.push(capture(EditKind.UNDO, "准备回到 #" + target.metadata().sequence()));
editor.restore(target);
```

**三行。而且这三行对"用户做了什么操作"完全无知。**

`Editor` 加了十个新方法（拖拽、格式化、插入表格……），
**`undo()` 一行都不用改** —— 因为所有方法最终都通过 `replaceRange` 改 `text` / `cursor` / `selection`，
而快照存的就是这三个字段的**全部**。

**这就是备忘录模式的核心价值：用"空间"换"逻辑"。**

| 方案 | 新增一个编辑操作 | 新增一个编辑操作 |
|---|---|---|
| 命令模式 | 写一个 `XxxCommand` + 实现 `undo()` | 需要理解该操作的逆运算 |
| 备忘录模式 | **零改动** | 不需要理解任何东西 |

#### 优点二：Caretaker 与状态结构彻底解耦

**证据在 §7.1：给快照加一个字段，`SnapshotHistory` 一行都不用改。**

为什么？因为它只声明 `Deque<Snapshot>`，从来不读快照里的内容：

```java
private final Deque<Snapshot> undo = new ArrayDeque<>();
private final Deque<Snapshot> redo = new ArrayDeque<>();
```

**它唯一的"读"操作是：**

```java
target.metadata().sequence()          // 用来拼一句日志
```

**就这一处。**

对比一个反例 —— 如果 `SnapshotHistory` 需要"合并连续的同类型快照"（§7.4），
它就必须读 `kind()`：

```java
boolean mergeable = kind == EditKind.TYPING && lastKind == EditKind.TYPING && ...;
```

**注意：它读的是 `kind` 这个"元信息"，而不是 `text`。** 这正是 `SnapshotMetadata`
存在的意义：**把"能公开的"和"不能公开的"分开，让 Caretaker 只能依赖前者。**

#### 优点三：跨文档污染从"运行时可能"变成"构造期不可能"

**探针 P10 的原项目场景：**

```java
Document docA = new Document("A");
Document docB = new Document("B");
History shared = new History();          // ← History 不知道 Document 是谁

shared.pushMemento(docA.save());
docB.restore(shared.popMemento());       // ← 编译通过，运行"成功"，A 的草稿进了 B
```

**本项目里，等价代码写不出来：**

```java
SnapshotHistory h1 = new SnapshotHistory(editorA, 10, clock);
SnapshotHistory h2 = new SnapshotHistory(editorB, 10, clock);

h2.edit(...);                            // ← h2 只认 editorB
```

**`SnapshotHistory` 没有 `setEditor`，`editor` 是 `final`。**
一份历史只能绑定一个编辑器，这个约束在**构造期**就定死了。

**而 `ownerToken` 是第二道防线：** 即使有人把 `editorA.snapshot(meta)` 硬塞进 `editorB.restore(...)`，
也会被运行期拦下（Demo 5）。

**两道防线的分工：**

| 防线 | 拦什么 | 何时生效 |
|---|---|---|
| `final Editor editor` | 一个 History 只能管一个 Editor | 编译期 + 构造期 |
| `ownerToken` | 一个 Editor 只认自己的快照 | 运行期 |

**第一道拦"结构性的错误"，第二道拦"手滑的错误"。** 两道都需要。

#### 优点四：内存增长是线性的、可预测的

```text
  内存 ≈ 快照数 × 单张快照大小
       = min(编辑次数, maxDepth) × (text.length() + cursor + selection + metadata)
```

**这是一个可以直接算的数，而不是"跑着看"。**

Demo 4 里 `maxDepth = 3`，所以无论用户编辑 4 次还是 400 次，栈里永远只有 3 张。

对比"命令模式 + 无上限"：

```text
  内存 ≈ 操作数 × 单次操作大小
```

命令模式通常更省（一次操作只存"改了什么"），
**但它也更容易漏掉"无上限"这件事** —— 因为每个 Command 都很小，
开发者会倾向于"多存点没关系"。而一个 400KB 的 `Memento` 会立刻让人意识到"得设个上限"。

**"体积大"反而促使了正确的工程决策。** 这是一个意外的红利。

#### 优点五：可测试性

**证据：三次运行 SHA-256 完全一致。**

```text
  9598E3649AAA38B2CAAC38300E892349349E7E7DD7E5830FB9083174BD47261E
  （86 行 / 3857 字节，三次逐字节相同）
```

**这是怎么做到的？**

| 不确定性来源 | 处理方式 | 位置 |
|---|---|---|
| 当前时间 | `Supplier<Instant>` 注入，Demo 用 `SteppedClock` | `SnapshotHistory.clock` |
| 迭代顺序 | 全部用 `Deque` / `List` / `LinkedHashMap`，没有 `HashMap` | 全项目 |
| 随机数 | 一个都没用 | 全项目 |
| 线程调度 | 单线程 | 全项目 |
| 字符编码 | 源文件 UTF-8，输出指定 `-Dfile.encoding=UTF-8` | 构建脚本 |

**最关键是第一行。**

`SnapshotHistory` 不调 `Instant.now()`，它调 `clock.get()`：

```java
private Snapshot capture(EditKind kind, String detail) {
    sequence++;
    SnapshotMetadata metadata = new SnapshotMetadata(sequence, kind, detail, clock.get());
    return editor.snapshot(metadata);
}
```

**这个 `Supplier<Instant>` 参数在真实项目里的价值远大于 Demo：**

```java
// 测试"超过 5 分钟的快照要合并"
@Test
void mergeSnapshotsWithin5Minutes() {
    SteppedClock clock = new SteppedClock(Instant.parse("2025-01-01T00:00:00Z"), Duration.ofMinutes(1));
    SnapshotHistory history = new SnapshotHistory(editor, 10, clock);
    history.edit(...);        // 第 1 分钟
    history.edit(...);        // 第 2 分钟
    // 断言：两张快照
}
```

**不需要 `Thread.sleep`，不需要 `Awaitility`，毫秒级跑完。**

> **"注入时钟"是一个几乎零成本、但收益极高的设计动作。**
> 它把一个"只能靠 sleep 测试"的逻辑，变成了"喂几个固定时刻"。

---

### 8.2 六个真实代价

#### 代价一：内存与"状态大小 × 步数"成正比

**算一笔账：**

| 文档大小 | 步数 | 快照内存 | 备注 |
|---:|---:|---:|---|
| 1 KB | 20 | 20 KB | 完全无感 |
| 100 KB | 20 | 2 MB | 可接受 |
| 400 KB | 20 | 8 MB | 开始要注意 |
| 400 KB | 100 | **40 MB** | 一个标签页 40MB |
| 4 MB | 100 | **400 MB** | 会 OOM |

**这就是 `MAX_UNDO_DEPTH = 100` 的依据**：它是一份 400KB 文档"还算能接受"的上限。

**注意 Java 的字符串还有一个隐藏开销：** 一个 `String` 对象除了字符数据，
还有 16 字节的对象头 + 一个 `byte[]` 引用 + 一个 `int hash`。
所以"400KB 的文本"在堆里可能占 400KB（Latin-1）或 800KB（UTF-16）。

**本项目的中文文本会被存成 UTF-16** —— 因为中文字符超出了 Latin-1 的范围。
所以一张 400KB 的中文文档快照，实际占用约 800KB。

**40MB 的估算要再乘 2，变成 80MB。** 这是一个必须在 §8.5 里写明的量级修正。

#### 代价二：没有差量，改一个字符也存全量

```text
  文档 400KB，用户改了一个标点
  → 快照存了 400KB
  → 其中 399.999KB 是无用重复
```

**这是"用空间换逻辑"的直接账单。**

对比命令模式：改一个标点只需要存 `(position, oldChar, newChar)` —— 约 20 字节。

**压缩比是 20000 : 1。**

但命令模式要为每种操作写逆运算（见优点一），而备忘录不用。
**这是一个明确的、可以量化的取舍。**

#### 代价三：`ownerToken` 与序列化天然冲突

`ownerToken` 是 `new Object()`，它的唯一语义是"对象标识"。

**而"对象标识"是不可序列化的** —— 序列化一个 `Object` 再反序列化，得到的是**另一个对象**。

```java
private record Memento(String text, int cursor, TextRegion selection,
                       SnapshotMetadata metadata, Object owner) implements Snapshot {
    //                                      ↑ 这个字段没法落盘
}
```

**所以"落盘持久化"必须绕过 `Memento` 的自动序列化**（§7.3 的解法 B）。

**这是引入 `ownerToken` 的直接代价：它让"快照可序列化"这件事变复杂了。**

**如果当初不引入 `ownerToken`：**

- 序列化很简单（`implements Serializable` 一行）；
- 但跨文档污染无法拦住（§4.3 那个真实 bug）。

**两个代价，二选一。** 本项目选了"防污染"，因为污染是**数据正确性**问题，
序列化是**功能扩展**问题 —— 后者可以以后做，前者做错了会丢用户数据。

#### 代价四：每张快照都必须拍在"操作之前"

这个约束听起来很自然，但它有一个不太明显的后果：

**`capture()` 的调用必须早于 `action`，所以"快照的元信息"必须在 `action` 之前就确定。**

而"这次编辑到底改了什么"（比如撤销面板上想显示"修改了 3 处"）
**在拍快照的那一刻还不知道。**

```java
Snapshot before = capture(kind, detail);      // ← 此时只知道"要做什么"
try {
    action.accept(editor);                     // ← 这里才知道"实际改了什么"
}
```

**所以 `SnapshotMetadata.detail` 存的是"意图"，不是"结果"。**

看 Demo 1 的输出就明白：

```text
  a 自己拍一张 → #999 输入 · 手工拍的 · 09:00:00.000
                         ↑ 这是"意图"，不是"实际改了什么"
```

**如果要记录"结果"（比如"新增 3 字，删除 1 字"），必须在 `action` 之后补一次元信息更新** ——
而 `SnapshotMetadata` 是 immutable record，不能改。

**解决方式是"拍第二张"或"用可变容器"**，两者都有代价：

| 方案 | 代价 |
|---|---|
| 拍第二张 | 快照数翻倍，`sequence` 语义混乱 |
| 可变容器 | 破坏 `record` 的不可变性 |

**本项目选了"只记意图"，因为它对撤销面板来说够用了** ——
用户看到"输入 · 末尾补「已发货」"，不需要知道实际改了 3 个字符。

**但如果面板要显示"+3 / -1"，这个设计就要改。** 这是 §8.5 的一条已知局限。

#### 代价五：私有 record 无法在测试里直接构造

```java
// 测试代码里写不出来：
Editor.Memento m = new Editor.Memento("text", 0, TextRegion.caret(0), meta, token);
// ^^^^^^^^^^^^^^ 编译错误：Memento 在 Editor 中具有 private 访问权限
```

**所以测试只能走正常路径：**

```java
Snapshot s = editor.snapshot(new SnapshotMetadata(1, EditKind.BASELINE, "测试", clock.get()));
```

**这是"故意的"**（§4.2 的代价清单里写过），但它确实带来两个真实麻烦：

1. **想测"恢复一个构造得很怪异的快照"很难** —— 必须让 `Editor` 先进入那个状态；
2. **想测 `restore` 的类型检查分支，只能用反射。**

**判断：这个代价可以接受。** 因为"构造怪异快照"本身就是一个反模式 ——
如果测试需要它，说明被测代码在依赖"快照可以任意构造"这个假设。

#### 代价六：不支持"只撤销我自己的操作"

```text
  用户 A 和 B 同时编辑
  A 按撤销 → 撤掉的是"最后一次操作"，可能是 B 的
```

**这不是实现 bug，而是模型的边界。**

备忘录模式的撤销粒度是"整篇状态"。它无法区分"这个状态是谁造成的"。

**要支持"按用户撤销"，必须换成"存操作 + 操作可变换"的模型**（§7.8）。

**这条代价值得在面试里主动说** —— 因为它说明你理解模式的**适用边界**，
而不只是会背 23 个模式的名字。

---

### 8.3 七个常见误区

#### 误区一：把"快照"当成"对象的 clone"

**错误写法：**

```java
public class Document implements Cloneable {
    private String content;
    private List<String> tags;          // ← 可变集合

    @Override
    public Document clone() {
        try {
            return (Document) super.clone();      // ← 浅拷贝
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
```

**然后：**

```java
Document backup = doc.clone();
doc.getTags().add("重要");
// 现在 backup.getTags() 里也有 "重要"  ← 因为共享同一个 List
```

**为什么错？**

"备忘录"的语义是**"某一瞬间状态的完整副本"**。
浅拷贝得到的是"共享一部分状态的另一个对象"，它**不满足**这个语义。

**正确做法（本项目的做法）：**

快照里的每个字段都是**不可变值**：

```java
private record Memento(String text,                       // String 不可变
                       int cursor,                         // 基本类型
                       TextRegion selection,               // record，不可变
                       SnapshotMetadata metadata,          // record，不可变
                       Object owner)                        // 令牌，不参与状态
        implements Snapshot { }
```

**判断标准：快照里的每个字段，都必须满足"拍完之后永远不会变"。**

对集合类，用 `List.copyOf()`（**但要确保元素也不可变**，见 §7.1）；
对自定义类，用 `record` 或"所有字段 final + 无 setter"。

#### 误区二：让 Caretaker 读快照内容

**错误写法：**

```java
public interface Snapshot {
    String text();                    // ← 开了这个口子
    SnapshotMetadata metadata();
}

// 然后 SnapshotHistory 里就出现了这种代码：
public String preview() {
    return undo.peek().text().substring(0, 20) + "...";     // ← "为了做个预览"
}
```

**为什么错？**

**因为接口上有什么方法，就决定了人们会拿它做什么**（§5.1）。

一旦 `text()` 在接口上，"做个预览"、"打个日志"、"做个埋点"、"发给服务端做统计"
都会用它。**而且每一处都"看起来是正常使用 API"，code review 拦不住。**

**正确做法：**

如果 UI 真的需要"前 20 个字"，**让 `Editor` 在拍快照时生成一个摘要字段，
放进 `SnapshotMetadata`：**

```java
public record SnapshotMetadata(int sequence, EditKind kind, String detail,
                               String preview,          // ← 由 Originator 生成
                               Instant capturedAt) { }
```

**关键区别：**

| | 让 Caretaker 读 `text()` | 由 Originator 生成 `preview` |
|---|---|---|
| 谁能决定"截多少" | Caretaker | Originator |
| 会不会泄露完整正文 | 会（Caretaker 拿到全文） | 不会（只拿到摘要） |
| 加了新字段要改谁 | 每个调用点 | 只在 `Editor` 里加一行 |

**判据：如果"读内容"是为了**展示**，就让 Originator 生成摘要；
如果是为了**恢复**，就只在 `Editor` 内部读。**

#### 误区三：把"撤销"实现成"记录所有操作"

**错误写法：**

```java
public class History {
    private final List<Action> actions = new ArrayList<>();

    public void record(Action a) { actions.add(a); }
    public void undo() { actions.remove(actions.size() - 1).reverse(); }
}
```

**这其实是命令模式，不是备忘录模式。**

**为什么容易混？** 因为两者的外部行为一样（都能撤销），
API 也类似（`undo()`）。

**区别在"存什么"：**

| | 存什么 | 撤销靠什么 |
|---|---|---|
| 备忘录 | **状态快照** | 恢复整个状态 |
| 命令 | **操作 + 逆操作** | 执行逆操作 |

**如果写成上面那样，会遇到这些具体问题：**

1. **每个 `Action` 都要实现 `reverse()`** —— 新增操作要写逆运算；
2. **`reverse()` 可能失败**（比如"发送邮件"没法撤销）；
3. **逆操作可能不精确**（浮点数加减、文本索引偏移）。

**而备忘录没有这些问题** —— 它不关心"做了什么"，只关心"当时是什么样"。

**判断标准：**

> **如果你在写 `XxxCommand.undo()`，你在用命令模式；
> 如果你在写 `XxxMemento` / `Snapshot`，你在用备忘录模式。**

**两者都合法，但不要混着写。**

#### 误区四：以为 `private` 能防反射

**错误认知：**

> "`Memento` 是 private，所以外部绝对拿不到。"

**事实：**

```java
Class<?> m = Class.forName("com.patterns.behavioral.memento.Editor$Memento");
Constructor<?> ctor = m.getDeclaredConstructors()[0];
ctor.setAccessible(true);                                  // ← 一句就绕过
Object fake = ctor.newInstance("伪造", 0, null, meta, token);
```

**`setAccessible(true)` 之后，一切私有都变成公开。**

**那为什么还要 `private`？**

因为防御目标不同：

| 威胁 | `private` 能防住吗 |
|---|---|
| 同事顺手 `new Editor.Memento(...)` | ✅ 编译不过 |
| 复制粘贴时把 A 的快照给了 B | ✅ 类型不匹配，运行期抛错 |
| 有人故意用反射伪造 | ❌ 防不住 |
| 有人用 `Unsafe` 直接改内存 | ❌ 防不住 |

**前两行是"不小心"，后两行是"故意"。**

**任何 Java 层面的封装，都只能防"不小心"。**
如果真的要防"故意"，需要 JVM 级别的隔离（模块系统、SecurityManager、
或者把敏感逻辑放到独立进程）。

> **封装的价值不在于"绝对安全"，而在于"让正确的写法比错误的写法更容易写"。**

#### 误区五：在 Caretaker 里写业务逻辑

**错误写法：**

```java
public class SnapshotHistory {

    public void edit(EditKind kind, String detail, Consumer<Editor> action) {
        Snapshot before = capture(kind, detail);
        action.accept(editor);

        // ↓ 业务逻辑漏进来了
        if (editor.length() > 1000) {
            System.out.println("警告：文档太长了");
        }
        if (editor.text().contains("机密")) {
            before = null;                        // ← 不存含机密的快照
        }
        // ...
    }
}
```

**为什么错？**

**`SnapshotHistory` 从此知道了两件它不该知道的事：**

1. "文档多长算长"（业务规则）；
2. "什么内容算机密"（业务规则）。

**后果：**

- 换一个业务（比如"邮件编辑器"），这些规则全要改；
- `SnapshotHistory` 无法被复用到别的 `Editor`；
- 测试 `SnapshotHistory` 要先构造"1000 字以上的文档"和"含机密的文档"。

**正确做法：**

```java
// 规则属于 Editor 或调用方
public final class EditorLimits {
    static final int MAX_TEXT_LENGTH = 200_000;
    static String requireText(String text) { /* 超长抛异常 */ }
}
```

**"内容级"的规则放在 `Editor` 的写方法里，"长度级"的规则放在 `EditorLimits` 里，
`SnapshotHistory` 只负责"拍、存、弹"。**

**判据：`SnapshotHistory` 的任何一行代码里，都不该出现"文档"、"用户"、"业务"
这类词 —— 它只该出现"快照"、"栈"、"深度"。**

#### 误区六：快照里存"整篇文档"就一定对

**这是一个反直觉的误区。** 前面一直在说"存全量"，但全量也有边界。

**问题场景：**

```text
  文档不是"纯文本"，而是"文本 + 一张 10MB 的图片"
  → 每张快照都复制那 10MB
  → 20 步撤销 = 200MB
```

**这里的错误不是"用了全量快照"，而是"把不可变的大对象也放进了快照"。**

**正确做法：对大对象做"引用共享"。**

```java
// 图片本身是不可变的，快照只存引用
private final Map<String, byte[]> assets;        // 全局一份
private final List<String> assetIds;             // 快照里只存 id

// Memento 里
private record Memento(String text, List<String> assetIds, ...) { }
```

**因为 `byte[]` 一旦创建就不再修改，多个快照共享同一份是安全的。**

**判据：**

> **快照里该存"值"，还是"引用"，取决于被存的东西**会不会变**。
> 不变 → 存引用（省内存）；会变 → 存值（保正确）。**

**本项目里 `text` 是 `String`（不可变），所以直接存值是安全的。
如果哪天 `text` 变成 `StringBuilder`（可变），就必须改成存 `toString()` 的副本。**

#### 误区七：以为快照越多越好

**错误认知：**

> "既然撤销栈是免费的，那就多留一些，用户体验更好。"

**三个具体反例：**

**（1）内存**

见 §8.2 代价一。100 步 × 400KB 中文 = 80MB，一个标签页。

**（2）UI 可用性**

```text
  撤销面板列出 100 条历史
  → 用户找不到"我要回到的那一步"
  → 实际使用中，用户只会按 Ctrl+Z 十几次
```

**真实产品的做法：** 撤销栈可以深（几十步），但"历史面板"只显示最近的 10-20 条，
更早的折叠或按时间分组。

**（3）正确性风险**

栈越深，"栈底那张快照"与"用户记忆中的初始状态"差距越大。
Demo 4 就是这个问题的极端版：`maxDepth = 3`，撤两次之后，
用户已经回不到"第0版"了。

**如果栈深 100，用户撤了 50 次，他会以为自己回到了"打开文件时"，其实没有。**

**正确做法：用 `reset()` 建立语义断点。**

```java
// 用户点"保存"之后
history.reset("已保存到 draft.md");
```

这样"撤到底"就精确等于"保存时的状态"，用户的预期不会被打破。

**本项目提供了 `reset()`，但 Demo 里没有调用它** —— 这是 §8.5 的一条局限。

---

### 8.4 与三个相似模式的区分

这三个模式最容易被混：**备忘录、命令、原型**。加上一个**状态**，四个一起对比。

#### 一张总表

| 维度 | 备忘录 Memento | 命令 Command | 原型 Prototype | 状态 State |
|---|---|---|---|---|
| **解决的问题** | 保存/恢复**状态** | 把**请求**封装成对象 | **复制**对象 | 状态变化时**改变行为** |
| **核心动作** | `save()` / `restore()` | `execute()` / `undo()` | `clone()` | `handle()` 委托给状态对象 |
| **存的是什么** | 状态快照 | 操作 + 接收者 | 一份完整副本 | 一个状态对象 |
| **撤销靠什么** | 恢复整个状态 | 执行逆操作 | （通常不做撤销） | （通常不做撤销） |
| **谁发起** | 调用方要"存/恢复" | 调用方要"排队/记录/撤销操作" | 调用方要"复制" | 状态自己触发转移 |
| **典型场景** | 编辑器撤销、游戏存档 | 事务、任务队列、宏录制 | 复杂对象创建、保护性拷贝 | 订单状态机、TCP 连接 |
| **本项目的对应** | 本章 | `behavioral/command` | `creationtype/PrototypePattern` | `behavioral/state` |

#### 备忘录 vs 命令：最容易混的一对

**两者的外部表现一样：都能 `undo()`。**

**区分方法：看"存什么"。**

```java
// 备忘录：存状态
undo.push(new Memento(text, cursor, selection));      // ← 存了"当时是什么样"

// 命令：存操作
undo.push(new TypeCommand(editor, "hello", 3));        // ← 存了"做了什么"
```

**一个可操作的判断：**

> **问："如果我在拍完快照之后，又用别的方式改了状态（比如直接改数据库），
> 撤销还能恢复吗？"**
>
> - 备忘录：能（快照是自包含的完整状态）；
> - 命令：不能（逆操作假设"当前状态 = 原状态 + 已执行的操作"）。

**这个判断非常有效。** 在真实系统里，"别的方式改了状态"是常态（定时任务、别的服务、
手工改库），所以**能容忍旁路修改的备忘录模式往往更健壮**。

**反过来，命令模式的优势在"保存操作本身"**：

- 可以把操作发给另一台机器执行（RPC）；
- 可以把操作写进审计日志（"谁在什么时候改了什么"）；
- 可以把多个操作打包成宏（`MacroCommand`）。

**本项目选了备忘录，因为场景是"单机编辑器，只需要撤销"。**

#### 备忘录 vs 原型：都涉及"复制"

**两者的区别在"复制出来的东西用来干什么"。**

```java
// 原型：复制出来的是一个"可继续使用的同类对象"
WordDocument copy = (WordDocument) original.clone();
copy.setTitle("副本");                     // ← 副本会被继续修改
```

```java
// 备忘录：复制出来的是一个"只用于恢复的不透明对象"
Snapshot s = editor.snapshot(meta);
// s.text()  ← 编译不过，你根本读不到它
```

**关键区别：**

| | 原型 | 备忘录 |
|---|---|---|
| 返回值类型 | 同类型（`WordDocument`） | **不透明接口**（`Snapshot`） |
| 外部能读内容吗 | 能 | **不能** |
| 用途 | 拿去用 | 拿去存，将来恢复 |
| 是否要求不可变 | 不要求 | **要求** |

**一个常见的误用：用原型实现撤销。**

```java
// 错误写法
List<Editor> history = new ArrayList<>();
history.add(editor.clone());           // ← 用 clone 当快照
```

**问题：**

1. `clone()` 通常只做浅拷贝（见误区一）；
2. `Editor` 的所有字段对外可见，历史里的对象可以被任意修改；
3. 没有"窄接口"，任何人都能 `history.get(0).setText("乱改")`。

**原型解决的是"创建成本高"的问题，备忘录解决的是"状态需要回滚"的问题。**
两者都"复制"，但**复制的可见性和用途完全不同。**

#### 备忘录 vs 状态：都在"变"，但驱动者不同

**这个问题问得少，但答案很有意思。**

```java
// 状态模式：对象自己决定下一步变成什么状态
public void pay() {
    state.pay(this);           // ← 把"转移"委托给当前状态对象
}

// 状态对象的实现里：
class PendingState implements OrderState {
    public void pay(Order order) {
        order.setState(new PaidState());      // ← 状态自己推动转移
    }
}
```

```java
// 备忘录模式：由外部决定"回到哪个状态"
public boolean undo() {
    Snapshot target = undo.pop();            // ← 外部（用户点击）决定
    editor.restore(target);
    return true;
}
```

**关键区别：**

| | 状态模式 | 备忘录模式 |
|---|---|---|
| 谁决定"变成什么" | **对象内部**（状态转移表） | **外部**（调用方选快照） |
| 状态的数量 | 有限、预定义（枚举） | 无限、动态（每次编辑一张） |
| 是否需要"回退" | 不需要（状态机只往前走） | **核心需求** |
| 状态是否互斥 | 是（同一时刻只有一个状态） | 否（快照可以有任意多张） |

**一句话：**

> **状态模式管"现在该是什么状态"（向前演化的规则）；
> 备忘录模式管"刚才是什么状态"（向后退回的记录）。**

**两者可以共存：** 一个订单系统可能同时用状态模式管"待付款→已付款→已发货"，
用备忘录模式管"撤销上一步操作"。**它们服务于不同的时间方向。**

---

### 8.5 已知局限：十三处诚实清单

这一节列出的，都是本项目**明知不完美、但有意保留**的地方。

分三类：**设计取舍**（不打算改）、**预留接口**（未来可能用）、**真实缺陷**（应该改）。

#### A. 设计取舍（不打算改）

**A1. `maxDepth` 差一：`maxDepth = N` 只能撤销 `N - 1` 步**

- 位置：`SnapshotHistory.trim()` + `undo()` 的 `size() <= 1` 守卫；
- 影响：`maxDepth = 3` 时只能撤 2 次，栈底那张快照永远读不到；
- 为什么不改：改成"能撤 N 次"会让 `maxDepth = 1` 变得合法，反而破坏不变式（§6.4.5）；
- 缓解：构造器 javadoc 明确写"1 个基线 + 1 步编辑"。

**A2. `describe()` 与 `SnapshotMetadata.display()` 不截断**

- 位置：`Editor.describe()` 输出全文；`display()` 输出完整 `detail`；
- 影响：400KB 文档会一次打印 400KB；
- 为什么不改：Demo 的文档只有几十个字符，截断会让输出难以逐字核对；
- 生产环境应该：`describe()` 只输出 `"正文 " + length + " 字"`，`display()` 里 `detail` 截到 40 字符。

**A3. `SnapshotMetadata.detail` 记的是"意图"而不是"结果"**

- 位置：`SnapshotHistory.edit()` 的 `capture()` 在 `action` 之前；
- 影响：面板无法显示"实际新增 3 字、删除 1 字"；
- 为什么不改：`SnapshotMetadata` 是不可变 record，事后无法补写；
- 正确做法：`edit()` 返回一个 `EditResult`（含 `before`/`after` 的长度差），由调用方另行展示。

**A4. `reset()` 不可撤销**

- 位置：`SnapshotHistory.reset()` 直接 `undo.clear()` + `redo.clear()`；
- 影响：用户点"保存"之后，之前的历史全部消失，**无法反悔**；
- 为什么不改：`reset` 的语义就是"断代"，能反悔就不是断代了；
- 但真实产品应该：`reset` 前先问用户，或者把旧历史归档到"本地历史"里（可查看但不可撤销）。

**A5. 快照是全量的**

- 见 §8.2 代价二；
- 为什么不改：演示文档太小，delta 的复杂度收益不划算（§7.5）。

**A6. 非线程安全**

- 位置：`SnapshotHistory` 的两个 `Deque`、三个计数器，全部无锁；
- 影响：多线程同时 `edit` 会导致栈结构损坏；
- 为什么不改：真实场景是"一个编辑器窗口一个历史"，天然单线程；
- javadoc 已明确声明（`SnapshotHistory.java:55-57`）：

  > 要并发，请在外部加锁，而不是让每个方法都 `synchronized` ——
  > 后者只会让"撤销一次"变成 5 次独立的原子操作，中间状态照样会被看见。

**A7. 监听器异常未隔离**

- 位置：`notifyChanged()` 里直接遍历调用；
- 影响：某个监听器抛异常，`edit()` 会向外抛 —— **但此时状态已经改完了**；
- 为什么不改：吞掉监听器异常会让"界面没刷新"变成静默 bug，更难查；
- 两个可能的改进方向：
  1. `try { listener... } catch (RuntimeException e) { log.error(...); }` —— 隔离但留下日志；
  2. 把 `notifyChanged` 移到方法最后（**本项目已经这么做了**）—— 缩小影响窗口。

**A8. `ownerToken` 让快照无法直接序列化**

- 见 §8.2 代价三；
- 缓解：§7.3 的解法 B（`Editor.encode/decode`）。

#### B. 预留接口（未来可能用）

这几处是"定义了但 Demo 里没调用"的代码。诚实地列出来，避免读者以为它们被验证过。

**B1. `EditorLimits.DEFAULT_UNDO_DEPTH = 20`**

- 只在定义处出现 1 次，**零引用**；
- 它表达的是"如果没有特殊要求，20 是一个合理的默认深度"；
- `Client` 用的是显式的 `10` / `3`（为了让 Demo 展示不同场景）；
- **如果将来没人用，它就是死代码。** 保留的理由是"默认策略需要一个家"。

**B2. `TextRegion.shift(int delta)`**

- 只在定义处出现 1 次，**零引用**；
- 它的语义是"把选区整体平移"（比如"下一个匹配项"）；
- 预留的理由：将来做"搜索并替换"时，选区要跟着搜索结果移动。

**B3. `HistoryStats.canUndo()` / `canRedo()`**

- 各只在定义处出现 1 次，**零引用**；
- 它们本该被 UI 层用来决定"撤销按钮是否置灰"；
- Demo 里没有按钮，所以没调用。

**B4. `SnapshotHistory.removeListener(...)`**

- 只在定义处出现 1 次，**零引用**；
- 与 `addListener` 配套，用于"窗口关闭时注销"；
- `notifyChanged()` 里的 `List.copyOf(listeners)` 就是为了支持"在回调里注销自己"（§5.8.7）。

**B5. `Editor` 的五个方法**

| 方法 | 用途 | Demo 里是否调用 |
|---|---|---|
| `selectedText()` | 返回选区覆盖的原文 | 否 |
| `deleteBackward(int)` | 退格删除 | 否 |
| `deleteSelection()` | 删除选区 | 否 |
| `selectAll()` | 全选 | 否 |
| `moveCaret(int)` | 移动光标 | 否 |

**它们都是 `Editor` 作为"编辑器"该有的能力。** 本项目的 Demo 只用到了
`type` / `replaceAll` / `select` / `describe` —— 因为**撤销/重做的正确性**
不需要靠"调用更多方法"来证明。

**但这也意味着：这五个方法只被编译过，没被运行过。**

**这是一个必须说清楚的事实。** 如果读者要复用本项目，这五个方法需要自己验证。

**B6. `HistoryStats.edits()` 访问器**

- record 自动生成，**零引用**；
- `summary()` 里用的是字段名 `edits` 而不是 `edits()` —— 在 record 内部两种写法等价；
- 对外暴露是为了将来做"统计面板"。

#### C. 真实缺陷（应该改）

**C1. 编译警告为零，但语义校验不完整**

`javac -Xlint:all` 零警告，但有两类问题它查不出来：

**（1）`Editor.restore()` 不做长度校验**

```java
public void restore(Snapshot snapshot) {
    // ... 校验 ...
    this.text = memento.text();          // ← 直接赋值，不调 requireText
    this.cursor = memento.cursor();
    this.selection = memento.selection();
}
```

**为什么现在是安全的？** 因为 `memento.text()` 必然来自某次 `Editor` 的字段，
而 `Editor` 的每个写方法都通过了 `requireText`。**所以长度必然合法。**

**但如果将来 `Editor` 加了一个绕过 `requireText` 的写方法**，这个保证就断了。

**更严谨的写法：**

```java
String restored = EditorLimits.requireText(memento.text());      // 多一道保险
this.text = restored;
```

代价：每次恢复多做一次长度检查（O(1)，因为 `String.length()` 是缓存的）。

**本项目没加，理由是"恢复的代价应该尽可能小"** —— 一次 `undo` 已经够贵了，
再加一个永真断言只会让人怀疑它可能为假。

**这是一个有争议的选择。** 严格派会说要加，性能派会说不加。

**C2. `HistoryStats` 的字段没有"时间"维度**

目前的五个数字都是"当前快照"：`undoDepth` / `redoDepth` / `snapshotsTaken` / `evicted` / `edits`。

**没有"最近一次编辑是什么时候"。** 所以：
- 无法实现"30 秒内不重复拍快照"（需要 `lastEditAt`，§7.4 才有）；
- 无法显示"上次编辑：3 分钟前"。

**这是一个"当前需求不需要、但一想就会需要"的字段。**

**如果要加：**

```java
public record HistoryStats(int undoDepth, int redoDepth, int snapshotsTaken,
                           int evicted, int edits, Instant lastEditAt) { }
```

代价：`lastEditAt` 在"还没编辑过"时是 null，又要引入"空值语义"（本项目一直在消灭的东西）。
**正确做法是用 `Optional<Instant>` 或一个哨兵值 `Instant.EPOCH`。**

**本项目没加，因为 Demo 不需要显示时间。** 但它是**最可能被第一个加上的字段**。

**C3. `record` 的 `equals` 会让两个"内容相同"的快照相等**

```java
SnapshotMetadata a = new SnapshotMetadata(1, EditKind.TYPING, "x", t1);
SnapshotMetadata b = new SnapshotMetadata(1, EditKind.TYPING, "x", t1);
a.equals(b);      // ← true
```

**这在 `SnapshotMetadata` 上是对的**（值语义）。
**但 `Memento` 也是 record，所以它的 `equals` 会比较所有组件，包括 `owner`：**

```java
Memento m1 = new Memento("a", 0, r, meta, token);      // 同一 editor 拍的
Memento m2 = new Memento("a", 0, r, meta, token);      // 内容完全相同
m1.equals(m2);      // ← true
```

**这有影响吗？** 本项目里没有：两个栈从来不用 `equals` 判断，只用引用。

**但如果将来有人写 `undo.remove(snapshot)`，就会误删"内容相同的另一张"** ——
因为 `ArrayDeque.remove(Object)` 用的是 `equals`。

**这是一个潜伏的坑。** 正确做法是：

- 要么在 `Memento` 里重写 `equals` 为"引用相等"（不推荐，违反 record 的约定）；
- 要么永远不要用 `equals` 做栈操作（推荐，但需要纪律）。

**本项目选了后者，但没有在 javadoc 里写。这是 C 类里最应该补的一条。**

#### 十三处清单汇总

| 编号 | 内容 | 类型 | 严重度 |
|---|---|---|---|
| A1 | `maxDepth` 差一 | 取舍 | 低（有文档） |
| A2 | `describe()` 不截断 | 取舍 | 低（Demo 专用） |
| A3 | `detail` 记意图不记结果 | 取舍 | 中 |
| A4 | `reset()` 不可撤销 | 取舍 | 中 |
| A5 | 全量快照 | 取舍 | 中（大文档） |
| A6 | 非线程安全 | 取舍 | 低（场景天然单线程） |
| A7 | 监听器异常未隔离 | 取舍 | 中 |
| A8 | `ownerToken` 阻碍序列化 | 取舍 | 中 |
| B1 | `DEFAULT_UNDO_DEPTH` 零引用 | 预留 | 低 |
| B2 | `TextRegion.shift()` 零引用 | 预留 | 低 |
| B3 | `canUndo/canRedo` 零引用 | 预留 | 低 |
| B4 | `removeListener` 零引用 | 预留 | 低 |
| B5 | `Editor` 五个方法未运行 | 预留 | **中**（需自行验证） |
| B6 | `edits()` 零引用 | 预留 | 低 |
| C1 | `restore` 不校验长度 | 缺陷 | 低（构造上安全） |
| C2 | `HistoryStats` 缺时间维度 | 缺陷 | 中 |
| C3 | `Memento.equals` 的隐患 | 缺陷 | 中（潜伏） |

**（表里实际是 17 行，但 A/B/C 三组的关键条目是 13 条 —— B5 展开成了 5 个方法，
C 组有 3 条。这里保留全部细节，比凑一个整数更重要。）**

#### 为什么要把这些写出来

**因为"没写出来的局限"会变成"用户踩的坑"。**

一份合格的工程文档，不是"证明我做得多好"，而是：

1. **明确边界**："这些场景我验证过，那些没有"；
2. **明确取舍**："这里我选了 A，代价是 B，你可以选 C"；
3. **明确风险**："这里有一个潜伏的坑，别踩"。

**第三点最难，因为它要求作者承认自己写的东西有隐患。**

但只有写出来，下一个人才不会花两天去查一个"文档里明明写了"的问题。

> **"未知的未知"最贵，"已知的未知"最便宜。**
> 这一节的目的，就是把尽可能多的"未知"变成"已知"。

---

## 9. 面试与自测

分两部分：

- **9.1-9.3 面试题**：十个高频问题。每题给出「面试官在听什么」+「参考答案」+「可能的追问」。
- **9.4-9.6 自测题**：十三道针对本项目代码的细节题 + 答案 + 自检表。

---

### 9.1 面试题一：三个角色分别负责什么

**问：** 什么是备忘录模式？三个角色分别负责什么？

**面试官在听什么：**

- 你能否说清"职责边界"，而不只是背名词；
- 你有没有意识到"不透明"这个核心约束。

**参考答案：**

备忘录模式的意图是：**在不破坏封装性的前提下，捕获一个对象的内部状态，
并在该对象之外保存这个状态，以便以后恢复到该状态。**

三个角色：

| 角色 | 职责 | 本项目 |
|---|---|---|
| **Originator**（原发器） | 能拍快照、能恢复；**唯一能读写快照内容的人** | `Editor` |
| **Memento**（备忘录） | 存状态；对外**不透明** | `Snapshot`（窄接口）+ `Editor.Memento`（私有实现） |
| **Caretaker**（负责人） | 保管快照、决定何时拍；**不知道快照里有什么** | `SnapshotHistory` |

**关键在三句话：**

1. **Originator 自己拍、自己恢复** —— 因为只有它知道自己的所有字段；
2. **Memento 对外不透明** —— 否则"在不破坏封装性的前提下"这句话就失效了；
3. **Caretaker 只管搬运** —— 它不读内容、不改内容、不做业务判断。

**追问：为什么不把"保存"做成一个通用的 `clone()`？**

因为 `clone()` 返回**同类型**对象，调用方拿到之后可以任意读写：

```java
Editor backup = editor.clone();
backup.setText("乱改");          // ← 历史被污染
```

而备忘录返回的是 `Snapshot`（窄接口），调用方**在编译期就读不到内容**。

**这是"返回值类型"本身承载了设计意图。**

---

### 9.2 面试题二：为什么需要两个接口

**问：** GoF 书里说 Memento 有"宽接口"和"窄接口"两种。为什么要两个？
只用窄接口行不行？

**面试官在听什么：**

- 你是背了这句话，还是理解它的落地方式；
- 你知道"只用一个接口"会付出什么代价。

**参考答案：**

**宽接口**是给 Originator 用的：能读所有字段（`text` / `cursor` / `selection`）。
**窄接口**是给 Caretaker 用的：只能读元信息。

**如果只用窄接口：**

```java
public interface Snapshot {
    SnapshotMetadata metadata();
    // ← 没有 text()，那 Editor.restore 怎么读？
}
```

`Editor.restore()` 必须能读 `text`，但它在 `Snapshot` 上看不到它。
**所以必须有一个"只有 Editor 能看见的类型"来承载宽接口。**

**本项目的落地方式：**

```java
// 窄接口：所有人可见
public interface Snapshot {
    SnapshotMetadata metadata();
}

// 宽接口：只有 Editor 可见（private 嵌套类）
public final class Editor {
    private record Memento(String text, int cursor, TextRegion selection,
                           SnapshotMetadata metadata, Object owner) implements Snapshot {
        // ↑ 这些组件就是"宽接口"
    }
}
```

**注意这里有个并发问题：**

`Memento` 实现了 `Snapshot`，所以它**同时拥有两套接口** ——
但外部只能看见窄的那一套（因为 `Memento` 这个类型本身不可见）。

**这就是 Java 里实现"两种接口"的三种方式：**

| 方式 | 谁看得见宽接口 | 代价 |
|---|---|---|
| 两个独立接口（`Snapshot` + `MementoView`） | 拿到 `MementoView` 的人 | 谁给 `MementoView`？又回到同样的问题 |
| `Memento` 是 `public` + 包私有 getter | 同包 | 同包就能读，够不够？ |
| **`Memento` 是 `private` 嵌套类** | **只有外部类** | 无法独立成文件、无法直接测试 |

**第三种是唯一做到"物理隔离"的**（§4.2 的四种方案对比表）。

**如果只用宽接口会怎样？**

```java
public interface Snapshot {
    String text();
    int cursor();
    TextRegion selection();
    SnapshotMetadata metadata();
}
```

那么 `SnapshotHistory` 就能写：

```java
System.out.println("撤销前的内容：" + target.text());      // ← 草稿进了日志
```

**而这一行"看起来完全正常"。** 这是最危险的地方：不是"写不出来"，
而是"写出来没人觉得有问题"。

---

### 9.3 面试题三：备忘录 vs 命令

**问：** 备忘录模式和命令模式都能实现撤销。怎么选？

**面试官在听什么：**

- 你能不能给出**可操作的判断标准**，而不是"看情况"；
- 你知不知道各自的失效边界。

**参考答案：**

**核心区别：备忘录存"状态"，命令存"操作"。**

**三个判断维度：**

**（1）状态能否被"旁路"修改？**

```text
  备忘录：快照是自包含的 → 旁路修改后，撤销仍能恢复到快照的状态
  命令：  逆操作假设"当前 = 原始 + 已执行操作" → 旁路修改后，逆操作会算错
```

**如果系统里有"别的服务/定时任务也会改这份数据"，必须用备忘录。**

**（2）操作本身是否有价值？**

如果"操作"需要被：

- 发送到另一台机器执行（RPC / 消息队列）；
- 记入审计日志（谁在什么时候改了什么）；
- 打包成宏（一次执行十个操作）；
- 重放到另一个环境（事件溯源）

**那么必须用命令** —— 因为备忘录里只有"状态"，没有"意图"。

**（3）状态有多大？**

```text
  备忘录：内存 = 状态大小 × 步数
  命令：  内存 = 操作大小 × 步数
```

对 4MB 的文档，备忘录会 OOM，命令没问题。

**但如果状态很小（比如"一个订单的三个字段"），两者没差别。**

**本项目的选择：**

编辑器场景：

- 状态是"纯本地"的（没有别的服务改它）→ 备忘录可行；
- 状态是 400KB 以内 → 内存可接受；
- 不需要 RPC / 审计 / 宏 → 命令的优势用不上。

**所以选备忘录。**

**反过来，如果这是个"协同编辑器"，就必须换命令 + OT/CRDT**（§7.8）。

**追问：能不能两者都用？**

能，而且真实系统经常这么干：

```java
public final class DocumentService {
    private final SnapshotHistory snapshots;         // 备忘录：管撤销
    private final List<AuditEntry> auditLog;         // 命令：管审计

    public void apply(Command cmd) {
        snapshots.edit(cmd.kind(), cmd.describe(), e -> cmd.execute(e));   // 一次拍照
        auditLog.add(AuditEntry.of(cmd));                                  // 一条日志
    }
}
```

**注意 `edit()` 的第三个参数正是 `Consumer<Editor>`** ——
它让"命令"可以作为"一段编辑"塞进备忘录的原子单元里。

**这两个模式在这里是协作关系，不是竞争关系。**

---

### 9.4 面试题四：快照存在哪里

**问：** 快照应该存在 Originator 里，还是存在外面？为什么？

**参考答案：**

**必须存在外面（Caretaker 里）。**

三个理由：

**（1）职责单一**

如果 `Editor` 自己维护 `List<Snapshot>`，它就有了两个职责：
"管理文档"和"管理历史"。

**后果：**

- `Editor` 会越来越胖（本项目 `Editor` 已经 334 行，再加历史就是 600 行）；
- 无法支持"两份历史"（比如"本次会话"和"自动保存"，§7.9）；
- 无法替换历史策略（改深度、改存储介质，都要改 `Editor`）。

**（2）生命周期不同**

`Editor` 的生命周期 = 文档打开到关闭。
`SnapshotHistory` 的生命周期可能更长（如果要做持久化，§7.3）。

**如果快照存在 `Editor` 里，持久化就变成了"序列化整个 Editor"。**
而 `Editor` 里有 `ownerToken`（不可序列化）、可能有 UI 引用……

**（3）模式的定义**

GoF 原文写得很明确：

> **Memento 是"在对象之外保存状态"。**

如果存在自己里面，就不是备忘录模式了 —— 那只是"对象自己有个 list"。

**但有一个例外：**

**如果这个对象是"值对象"（不可变），那么"保存"就是"持有旧引用"，不需要 Caretaker。**

```java
// String 是不可变的，所以"历史"就是一堆引用
List<String> history = new ArrayList<>();
history.add(currentText);       // ← currentText 永远不会变
```

**这就是为什么 `TextRegion` 可以"直接存"而不需要额外快照** —— 它是 record，不可变。

**判据：不可变对象不需要备忘录；可变对象才需要。**

---

### 9.5 面试题五：内存扛不住怎么办

**问：** 如果撤销栈占的内存太大，你有什么办法？

**面试官在听什么：**

- 你能不能说清"每种办法的代价"，而不是只知道"用差量"；
- 你有没有量化意识。

**参考答案：**

**四层方案，按实施成本排：**

**（1）设上限（最便宜）**

```java
private void trim() {
    while (undo.size() > maxDepth) {
        undo.removeLast();
        evicted++;
    }
}
```

成本：改 5 行。代价：老历史丢失。

**这是必做的第一步。** 没有上限的撤销栈 = 慢性内存泄漏。

**（2）减少单张快照的大小**

- 只存"变化的字段"（比如只存 `text` 的差异部分）；
- 共享不可变的大对象（§8.3 误区六的图片例子）；
- 用 `byte[]` + 压缩（文本压缩率通常 3-5 倍）。

成本：中等。代价：恢复时要解压/拼接。

**（3）差量快照（§7.5）**

```java
public record TextDelta(int baseVersion, int from, int to,
                        String removed, String inserted) { }
```

成本：重写 `Editor` + `SnapshotHistory`。代价：恢复变成 O(n)，需要 compaction。

**（4）落盘（§7.3）**

内存只留最近 N 张，更早的写磁盘。

成本：新增存储层。代价：IO 延迟、序列化格式演进。

**选择建议（按文档大小）：**

| 文档大小 | 建议 |
|---|---|
| < 100 KB | 全量 + 上限（本项目） |
| 100 KB - 1 MB | 全量 + 上限 + 压缩 |
| 1 MB - 10 MB | 差量 + compaction |
| > 10 MB | 差量 + 落盘 + 分片 |

**关键是要能说出"为什么在这个区间选这个方案"，而不是"直接上最复杂的"。**

---

### 9.6 面试题六：怎么防跨文档污染

**问：** 有两个文档 A 和 B，各有自己的撤销栈。怎么保证 A 的快照不会被恢复到 B 上？

**参考答案：**

**三道防线，从早到晚：**

**第一道：构造期绑定（编译期就能看到）**

```java
public SnapshotHistory(Editor editor, int maxDepth, Supplier<Instant> clock) {
    this.editor = Objects.requireNonNull(editor, "editor");     // final
    // ...
}
```

一份历史只能管一个编辑器。**没有 `setEditor`，没有"传 editor 参数的方法"。**

**第二道：类型隔离（编译期拦）**

```java
private record Memento(...) implements Snapshot { }      // ← private
```

外部代码写不出 `Editor.Memento`，只能拿到 `Snapshot`。

**第三道：归属令牌（运行期拦）**

```java
private final Object ownerToken = new Object();

if (!(snapshot instanceof Memento memento) || memento.owner() != ownerToken) {
    throw new SnapshotRejectedException("拒绝恢复：... 不是本编辑器产生的快照");
}
```

**为什么第二道不够？**

**因为 `Memento` 是"类"，不是"实例"。**

```text
  a 和 b 都是 Editor
  它们共享同一个 Editor.Memento 类型
  → a 的快照 instanceof Memento 也是 true
  → b 的 restore 会接受它
```

**这个 bug 在本项目第一版真的写出来了**（§4.3）。Demo 5 的第一版输出是：

```text
  b.restore(a 的) 竟然成功（不该发生）
```

**修法：`ownerToken` 用引用相等判断"是不是我造的"。**

**追问：为什么不用 `this` 当令牌？**

因为快照会长期存在。如果快照持有 `Editor` 引用：

```text
  SnapshotHistory → Memento → Editor → SnapshotHistory     （环）
```

Java 的 GC 能处理循环引用，**不是内存泄漏**。但生命周期被拉长了：
用户关掉文档窗口，`Editor` 本该被回收，但只要有一个异步任务还握着某条 `Snapshot`，
整个编辑器（连同全文）就被钉在内存里。

**用 `new Object()` 就没有这个问题** —— 令牌不引用任何东西（16 字节）。

---

### 9.7 面试题七：Memento 该不该可变

**问：** 快照对象应该做成可变的还是不可变的？

**参考答案：**

**必须不可变。**

**如果可变会怎样：**

```java
// 假设 Memento 有 setter
Snapshot s = editor.snapshot(meta);
undo.push(s);
// ... 之后某处
((Editor.Memento) s).setText("乱改");       // ← 历史被篡改
editor.restore(s);                          // ← 恢复到伪造的状态
```

**而"历史被篡改"是最难查的一类 bug** —— 因为：

1. 篡改发生在别处，症状出现在撤销时；
2. 撤销的结果"看起来是旧内容"，用户不会怀疑；
3. 没有异常、没有日志。

**不可变怎么保证？**

本项目的做法：**用 `record`。**

```java
private record Memento(String text, int cursor, TextRegion selection,
                       SnapshotMetadata metadata, Object owner) implements Snapshot { }
```

`record` 保证了：

- 所有字段 `private final`；
- 只有访问器（`text()`），没有 setter；
- 类本身是 `final`，不能被继承后加可变字段。

**但"字段不可变"不等于"对象不可变"** —— 如果字段本身是可变对象：

```java
private record Memento(List<String> tags) { }        // ← List 可以 add
```

**那么 `memento.tags().add("x")` 依然能改。**

**修法（§7.1）：**

```java
private record Memento(List<String> tags) {
    Memento {
        tags = List.copyOf(tags);            // ← 紧凑构造器里深拷一层
    }
}
```

**注意 `List.copyOf` 只保证"列表不可变"，不保证"元素不可变"。**
如果元素是可变对象，要再拷一层。

**判据：从 `record` 的最外层往里，每一层都必须不可变。**

---

### 9.8 面试题八：撤销栈要不要设上限

**问：** 撤销栈要不要设上限？上限怎么定？

**参考答案：**

**必须设。**

**不设的后果：**

```text
  用户开一个文档，编辑一整天，按了 5000 次键
  → 5000 张快照
  → 如果每次 setContent 都拍（原项目的用法），就是 5000 × 400KB = 2GB
  → OOM
```

**探针 P8 实测过：原项目连压 10 万条，`Stack` 里全都留着。**

**上限怎么定？三个依据：**

**（1）内存预算**

```text
  单张快照 ≈ text.length() × 2 字节（UTF-16）
  上限 = 可接受的内存 / 单张大小

  例：允许 40MB，文档 400KB 中文（800KB/张）
  → 上限 = 40MB / 800KB ≈ 50 步
```

**（2）用户实际使用**

真实数据：**用户平均只按 2-3 次撤销，极少超过 10 次。**

- VS Code 默认几百步，但那是为了"误删大段"这种场景；
- Word 旧版本 100 步；
- 浏览器输入框几十步。

**（3）行业惯例**

`MAX_UNDO_DEPTH = 100` 是本项目选的。理由：

- 100 步对 400KB 文档 = 80MB（中文），是"还能忍"的上限；
- 超过 100，说明该上差量或落盘了（§7.5 / §7.3）。

**上限之外还有一件事：淘汰策略。**

本项目的 `trim()` 淘汰**最老**的：

```java
while (undo.size() > maxDepth) {
    undo.removeLast();          // ← ArrayDeque 的队尾 = 最老
    evicted++;
}
```

**为什么不淘汰"最新"的？** 因为最新的快照是"上一次操作之前"的状态，
用户最可能需要它。**淘汰最老是唯一合理的选择。**

**但要注意"淘汰是可感知的"：**

Demo 4 的输出说得很清楚：

```text
  只能撤到第2版为止 —— 这就是容量上限的代价，必须让用户知道
```

**真实产品应该显式提示：** "撤销历史已满，最早的 N 步已失效"。
不提示的话，用户会以为"撤销坏了"。

---

### 9.9 面试题九：大文档还适合全量快照吗

**问：** 如果文档是 4MB，你还会用全量快照吗？

**参考答案：**

**不会。** 算一下：

```text
  4MB 中文文档
  → 单张快照 ≈ 8MB（UTF-16）
  → 20 步 = 160MB
  → 100 步 = 800MB
```

**会直接 OOM。**

**应该改成"差量 + compaction"（§7.5）：**

```java
public record TextDelta(int baseVersion, int from, int to,
                        String removed, String inserted) { }
```

**关键设计点三个：**

**（1）delta 必须存"被删掉的原文"**

否则逆运算做不出来 —— 你不知道那个位置原来是什么。

**（2）必须有 compaction**

否则"用户从头重写一遍文档"会产生 N 个 delta，加起来等于全文的好几倍。

```java
if (deltaCount >= COMPACT_THRESHOLD) {
    fullSnapshot = editor.snapshot(meta);      // 拍一张全量，作为新的重放起点
    dropOldDeltas();
}
```

**（3）恢复变成 O(n)**

撤销到很早的版本，要重放一串 delta 的逆运算。
所以"撤销到很远的一步"会变慢 —— **这是用时间换空间。**

**真实产品的做法（VS Code、Word）：**

- 对"小改动"存 delta；
- 每 N 步或每 M 秒做一次全量 compaction；
- 全量快照落盘（内存只留最近几张）。

**结论：** 全量快照适合"文档 < 100KB"；
超过 1MB 必须换差量，**这不是优化，是能不能用的问题。**

---

### 9.10 面试题十：怎么测撤销/重做

**问：** "撤销/重做"这种有状态的东西，怎么测？

**面试官在听什么：**

- 你有没有"可测试性"的设计意识；
- 你知不知道"时间"和"顺序"是状态测试的两个大坑。

**参考答案：**

**四个层次的测试：**

**（1）纯函数化的状态断言**

```java
@Test
void undoRestoresPreviousText() {
    Editor editor = new Editor("abc");
    SnapshotHistory history = new SnapshotHistory(editor, 10, fixedClock());

    history.edit(EditKind.TYPING, "补 d", e -> e.type("d"));
    assertEquals("abcd", editor.text());

    assertTrue(history.undo());
    assertEquals("abc", editor.text());
}
```

**关键：`fixedClock()` 让"时间"变成常量。**

```java
private static Supplier<Instant> fixedClock() {
    return () -> Instant.parse("2025-01-01T00:00:00Z");
}
```

**如果不注入时钟，测试里就会出现 `Instant.now()`，然后：**

- 断言"时间戳等于某值"永远失败；
- 或者只能断言"时间戳不为空"，测了等于没测。

**（2）不变量测试（property-based）**

```java
@Test
void undoRedoIsSymmetric() {
    // 随机做 N 次编辑，然后 undo N 次，应该回到初始状态
    for (int round = 0; round < 100; round++) {
        Editor editor = new Editor("init");
        SnapshotHistory history = new SnapshotHistory(editor, 20, fixedClock());
        int edits = 1 + random.nextInt(5);

        for (int i = 0; i < edits; i++) {
            history.edit(EditKind.TYPE, "x", e -> e.type("x"));
        }
        for (int i = 0; i < edits; i++) {
            assertTrue(history.undo());
        }
        assertEquals("init", editor.text());
    }
}
```

**这个测试比"断言具体字符串"强得多** —— 它能发现"某次随机组合下的栈错位"。

**（3）不可复现 bug 的复现测试**

Demo 5 那个 bug（`b.restore(a 的)` 竟然成功）就是一个典型：

```java
@Test
void restoreRejectsSnapshotFromAnotherEditor() {
    Editor a = new Editor("A");
    Editor b = new Editor("B");
    Snapshot fromA = a.snapshot(new SnapshotMetadata(1, EditKind.TYPING, "t", fixedNow()));

    assertThrows(SnapshotRejectedException.class, () -> b.restore(fromA));
    assertEquals("B", b.text());         // ← 关键：b 的正文不能被污染
}
```

**注意最后一行断言。** 只断言"抛了异常"是不够的 ——
如果实现是"先污染再抛异常"，异常照样抛，但数据已经坏了。

**"抛异常"和"没有副作用"是两个独立的断言。**

**（4）哈希级别的确定性测试**

本项目用的是最粗暴但最有效的：

```powershell
# 连跑三次，比较 SHA-256
0..2 | ForEach-Object {
    java -Dfile.encoding=UTF-8 -cp $CLASSES com.patterns.behavioral.memento.Client |
        Set-Content -Path "run$_.txt" -Encoding UTF8
}
Get-FileHash run0.txt, run1.txt, run2.txt -Algorithm SHA256
```

**三次哈希一致 = 没有隐藏的非确定性。**

这能抓到什么？

- 用了 `HashMap`（迭代顺序不稳定）；
- 用了 `Instant.now()`（时间不同）；
- 用了随机数；
- 多线程竞争。

**这一类问题用普通单元测试极难发现**，因为它们"大部分时候是对的"。

**代价：哈希一致只证明"输出一样"，不证明"输出对"。**
所以它必须和前三层配合 —— 前三层保证"对"，第四层保证"稳"。

---

### 9.11 十三道自测题

以下问题全部针对**本项目的真实代码**。答不上来的，回到对应章节。

---

**Q1.** `Snapshot` 接口为什么只有 `metadata()` 一个方法？
加一个 `text()` 会带来什么具体风险？

**Q2.** `Editor.Memento` 为什么声明成 `private record`？
`public final class` + 私有构造器为什么不够？

**Q3.** `ownerToken` 解决什么问题？为什么用 `new Object()` 而不是 `this`？

**Q4.** `EditKind` 里为什么需要一个 `BASELINE`？如果去掉，会发生什么？

**Q5.** `TextRegion` 为什么用半开区间 `[start, end)` 而不是闭区间 `[start, end]`？

**Q6.** `SnapshotHistory.edit()` 里，`capture(kind, detail)` 为什么必须在
`action.accept(editor)` **之前**？如果反过来，哪里会出问题？

**Q7.** `undo()` 为什么返回 `boolean` 而不是 `Snapshot` 或 `null`？

**Q8.** 为什么在 `SnapshotHistory` 的**构造器**里就拍基线，
而不是提供一个 `markInitial()` 让调用方自己调？

**Q9.** `ArrayDeque` 替代 `Stack`，除了"没有继承包袱"，
还有一个具体的运行时差异是什么？（提示：看探针 P7）

**Q10.** `maxDepth = 3` 时，实际能撤销几次？为什么？栈底那张快照的命运是什么？

**Q11.** 为什么要注入 `Supplier<Instant>` 而不是直接调 `Instant.now()`？
给出"除了可复现"之外的一个理由。

**Q12.** `notifyChanged()` 里为什么先 `List.copyOf(listeners)` 再遍历？

**Q13.** `capture()` 里 `sequence++` 在**失败路径上也会执行**。
这是 bug 还是设计？为什么？

---

### 9.12 参考答案

**A1.**
因为 `Snapshot` 是给 Caretaker 看的**窄接口**。Caretaker 只需要"编号、类型、时间"
来渲染撤销面板，不需要正文。

加 `text()` 的具体风险：`SnapshotHistory` 里会出现"看起来完全正常"的代码：

```java
System.out.println("撤销前：" + target.text());     // 草稿进了日志
localStorage.setItem("backup", target.text());      // 草稿进了浏览器存储
```

**而且 code review 拦不住** —— 因为 `text()` 就在接口上，"用它"是正常使用 API。
详见 §4.1、§5.1。

---

**A2.**
因为 `private record` 做到了其他方案做不到的一件事：
**外部代码连这个"名字"都写不出来。**

```java
s instanceof Editor.Memento       // ❌ 编译错误：Memento 不可见
new Editor.Memento(...)           // ❌ 编译错误
s.text()                          // ❌ 编译错误：Snapshot 没这个方法
```

而 `public final class Memento` + 私有构造器：

- `new` 不了 ✓
- 但只要有 getter 就能读；
- 反射 `setAccessible` 两者都防不住。

**"私有嵌套"的目标不是防反射，而是让 99% 的正常代码在编译期就写错不了。**
详见 §4.2。

---

**A3.**
`ownerToken` 解决**"是哪个 Editor 拍的"**这个问题。

`instanceof Memento` 只能回答"是某个 Editor 拍的" —— 因为 `Memento` 是**类**，
所有 `Editor` 实例共享同一个类型。

```java
a 和 b 都是 Editor
→ a 的快照也是 Memento 类型
→ b.restore(a 的快照) 通过 instanceof 检查
→ b 的正文被 a 的草稿覆盖          ← 第一版的真实 bug
```

**用 `new Object()` 而不是 `this`：** 避免快照长期持有 `Editor` 引用。
虽然 Java 的 GC 能处理环，但"关掉的文档窗口因为有异步任务握着快照而无法回收"
是一个真实的生命周期问题。**令牌是 16 字节的空对象，不引用任何东西。**
详见 §4.3、§6.5.5。

---

**A4.**
`BASELINE` 是"打开编辑器那一刻"的快照类型。

去掉它有两个问题：

**（1）`SnapshotMetadata.kind` 会变成 null**

```java
public SnapshotMetadata {
    Objects.requireNonNull(kind, "kind");      // ← 基线没类型就 NPE
}
```

要允许 null，就要在 `display()` 里判空 —— **判空会传染**（§5.2）。

**（2）撤销面板上，基线是一张没有说明的快照**

```text
  [版本 1] [版本 2] [版本 3]        ← 用户不知道"版本 1"是什么
  [基线] [输入] [输入]              ← 清楚多了
```

**更根本的理由：`BASELINE` 让"栈底那张"有了语义。**

`undo.size() <= 1` 的判断说的是"只剩基线了"，而不是"只剩一张随便什么快照"。
**类型给了这个判断一个名字。**
详见 §4.5。

---

**A5.**
三个理由：

**（1）长度 = `end - start`，不用 `+1`**

```java
int length() { return end - start; }        // 半开区间
int length() { return end - start + 1; }    // 闭区间 ← 每处都要记得 +1
```

**"每处都要记得 +1"是 bug 的温床。**

**（2）空区间的表示是唯一的**

```text
  半开：[5, 5)   唯一的空区间表示
  闭包：[5, 4]   长度 -1，还得额外规定"end < start 表示空"
```

**（3）`substring` / `for` 循环天然是半开的**

```java
text.substring(start, end)      // ← JDK 就是这个语义
for (int i = start; i < end; i++)   // ← < 而不是 <=
```

**如果 `TextRegion` 用闭区间，每次调 `substring` 都要写 `end + 1` ——
而"忘了加一"是字符串处理里最常见的 bug 之一。**

详见 §4.6、§5.4。

---

**A6.**
因为快照要记录的是**"修改之前"**的状态。

如果反过来：

```java
action.accept(editor);              // ← 先改
Snapshot after = capture(...);       // ← 拍到的是改完的状态
undo.push(after);
```

那么撤销时恢复的就是"改完的状态" —— **等于什么都没撤。**

**这个 bug 特别难发现，因为：**

- 第一次撤销"看起来没反应"（其实是恢复到了当前状态）；
- 用户会再按一次，于是退了两步；
- 用户以为"撤销有点卡"，不会怀疑是顺序问题。

**统一规则（§6.7）：**

```text
  edit()：  拍 before → 改 → 入 undo
  undo()：  拍 before → 改 → 入 redo
  redo()：  拍 before → 改 → 入 undo
```

**三个方法都是"先拍，再改"。** 只有目标栈不同。

---

**A7.**
因为"撤销有没有发生"和"撤销到了什么状态"**是两个问题**，
后者由 `Editor` 自己回答。

**原项目把两个问题塞进一个返回值：**

```java
public DocumentMemento popMemento() {
    if (history.size() > 1) {
        // ...
        return history.peek();
    }
    return null;                    // ← null 同时表示"没有更多"
}
```

于是调用方：

- 忽略返回值 → 历史被吃掉，文档没变（探针 P2）；
- 不判 null 直接 `restore` → NPE（探针 P1、P3）；
- 写 `while (popMemento() != null)` → 每轮弹两张（§6.6.2）。

**返回 `boolean` 之后，"信号"和"数据"彻底分开：**

```java
boolean undo()      → 只回答"有没有成功"
Editor              → 回答"现在是什么状态"
```

**额外的收益：`boolean` 可以直接当循环条件。**

```java
while (history.undo()) { ... }      // ← 干净
```

详见 §4.8。

---

**A8.**
因为"打开文件时的状态"本来就该是撤销栈的底。

如果提供 `markInitial()` 让调用方调，就给了**"忘了调"**的机会：

```java
Editor editor = new Editor("content");
SnapshotHistory history = new SnapshotHistory(editor, 10, clock);
// ← 忘了 history.markInitial()
history.edit(...);
history.undo();          // ← 返回 false，因为栈里只有 1 张（编辑后入的那张）
```

**用户会以为"撤销坏了"，而 bug 在一个没写的调用上。**

**"能自动做的事，不要留给调用方。"**

这条原则的另一个应用：`SnapshotHistory` 的 `editor` 是 `final` 的，
没有 `setEditor` —— **绑定也不需要调用方记得做。**

详见 §4.9、§5.8.1。

---

**A9.**
`Stack` 继承 `Vector`，所以**每个方法都在 `Vector` 的那把锁上 `synchronized`**。

探针 P7 的实测数据：

```text
  Stack 自己声明的公共方法：5 个
  Vector 可见的 public 方法：66 个
  Stack.push  是 synchronized？false      ← 它只是转发给父类
  Vector.addElement 是 synchronized？true
  Vector.get        是 synchronized？true
```

**所以"用 Stack 就自动线程安全"是一个误解：**

- `push` 本身不加锁，加锁的是它转发的 `addElement`；
- 一次 `push` 内部只有 `addElement` 是原子的 —— 但 `undo` 的语义是
  "弹栈 + 恢复 + 入另一个栈"，**这三步各自原子，合起来不原子**；
- **所以 `Stack` 的同步不但没用，还让人误以为安全。**

`ArrayDeque` 没有这个包袱：

- 5 个方法就能覆盖栈的全部操作；
- 没有 `synchronized`，也没有"看起来线程安全"的假象；
- 快 2-3 倍（无锁、无 `Vector` 的扩容策略）。

详见 §4.10。

---

**A10.**
**只能撤销 2 次。**

```text
  maxDepth = 3 → 栈最多 3 张
  撤销 1 次 → 剩 2 张
  撤销 2 次 → 剩 1 张
  第 3 次：undo.size() <= 1 → return false
```

**栈底那张快照的命运：永远读不到。**

因为：

- `undo()` 只剩 1 张就拒绝；
- `redo()` 只往 `undo` 里 push，不 pop 栈底；
- 没有任何路径会 `restore` 它。

**它占了 1 个容量槽，却是死重量。**

**这就是"`maxDepth` 是栈容量，不是可撤销步数"的直接后果。**
`maxDepth = N` 实际能撤 `N - 1` 次。详见 §6.4.5。

**面试时如果被问"撤销深度设多少"，正确的回答是"设 N，实际能撤 N-1 次"** ——
这个细节能立刻区分"用过"和"背过"。

---

**A11.**
**"可测试性"之外的第二个理由：它能表达"我已经知道时间了"。**

真实场景：

```java
// 场景一：批量导入 1000 条历史记录，它们都有真实的"原始时间"
@PostConstruct
void restoreFromDisk() {
    Deque<Snapshot> loaded = disk.load();
    // 这些快照的 capturedAt 是磁盘上的值，不是"现在"
    // 如果 SnapshotHistory 内部用 Instant.now()，这些时间就全被覆盖成"启动时刻"
}
```

```java
// 场景二：测试"超过 5 分钟的快照合并"
SteppedClock clock = new SteppedClock(base, Duration.ofMinutes(1));
// 不用 sleep，毫秒级测完
```

**第三个理由：它把"谁决定时间"这件事显式化了。**

`Instant.now()` 是一个**隐式依赖** —— 从构造器签名看不出来。
而 `Supplier<Instant>` 是一个**显式依赖** —— 看签名就知道"这个类需要时间"。

**"显式依赖"是依赖注入最朴素的形式。**

详见 §4.12、§8.1 优点五。

---

**A12.**
因为**监听器在回调里增删监听器是常见操作**。

```java
history.addListener(new HistoryListener() {
    public void onHistoryChanged(HistoryStats stats, Editor editor) {
        if (stats.canRedo()) {
            history.removeListener(this);        // ← 在回调里注销自己
        }
    }
});
```

如果直接遍历原列表：

```java
for (HistoryListener listener : listeners) {          // ← 遍历中修改
    listener.onHistoryChanged(stats, editor);          // → ConcurrentModificationException
}
```

`List.copyOf()` 生成一个**快照副本**，遍历的是副本 ——
即使原列表在回调中被修改，遍历也不会抛异常。

**注意"快照"这个词在这里的双重含义：**

- `List.copyOf(listeners)` 是"监听器列表的快照"；
- `Snapshot` 是"编辑器状态的快照"。

**两者都是为了"遍历/恢复期间，数据不被并发修改"。同一个思路。**

**代价：** 每次通知都要复制一次列表。对"几个监听器"的场景完全可忽略；
如果有 1000 个监听器，就该换成 `CopyOnWriteArrayList`。详见 §5.8.8。

---

**A13.**
**是设计，不是 bug。**

`capture()` 的 javadoc 写得很明确：

> `sequence` 记录的是**拍摄次数**，而不是"成功入栈的快照数"。

**如果挪到成功路径：**

```java
undo.push(before);
sequence++;                     // ← 只在成功时加
```

两个后果：

**（1）编号与时间的对应关系乱掉**

```text
  失败：sequence 没加，但 clock 已经走过 500ms
  下次成功：编号 #3，时间 09:00:01.500
  → #1 对应 09:00:00.000，#2 对应 09:00:00.500，#3 对应 09:00:01.500
  → 中间少了一个时间点，读输出的人会以为"漏拍了一次"
```

**（2）失去"测量失败"的能力**

`已拍 - (可撤 + 可重) - 淘汰 = 失败次数`。

Demo 3 里这个差值是 `3 - 2 - 0 = 1`，正好对应一次失败。

**如果 `sequence` 只在成功时加，这个等式永远成立 ——
但它同时也失去了"发现失败"的能力。**

**"账面整齐"和"可观测"经常是矛盾的。本项目选了后者。**

详见 §6.3.4、§6.4.8。

---

### 9.13 自检表

对每个问题给自己打勾。**全部答对才算掌握了本项目的代码。**

| # | 问题 | 关键点 | ✓ |
|---:|---|---|---|
| 1 | `Snapshot` 只有一个方法 | 窄接口 / 防"正常使用 API"的信息泄露 | ☐ |
| 2 | `Memento` 是 `private record` | 名字都写不出来 / 不是防反射 | ☐ |
| 3 | `ownerToken` 的作用 | 类型 ≠ 实例 / 用 `Object` 不用 `this` | ☐ |
| 4 | `EditKind.BASELINE` | 消除 null / 给"栈底"一个名字 | ☐ |
| 5 | `TextRegion` 半开区间 | 长度 = end - start / 与 substring 一致 | ☐ |
| 6 | `capture` 在 `action` 之前 | 拍的是"修改前" / 反了就撤销无效 | ☐ |
| 7 | `undo()` 返回 `boolean` | 信号与数据分离 / 可当循环条件 | ☐ |
| 8 | 构造器里拍基线 | 不给"忘了调"的机会 | ☐ |
| 9 | `ArrayDeque` vs `Stack` | Vector 的 66 个方法 / 同步是假象 | ☐ |
| 10 | `maxDepth` 差一 | 能撤 N-1 次 / 栈底是死重量 | ☐ |
| 11 | 注入时钟 | 可复现 / 可测试 / 显式依赖 | ☐ |
| 12 | `List.copyOf` 再遍历 | 回调里增删监听器 | ☐ |
| 13 | `sequence` 失败也自增 | 记录拍摄次数 / 可测量失败 | ☐ |

---

#### 如果只能记住五句话

1. **窄接口是备忘录模式的核心** —— 接口上有什么方法，就决定了人们会拿它做什么。
2. **`private record` 让"外部伪造"在编译期就写不出来** —— 比"运行时检查"早一步。
3. **先拍，再改，再入栈** —— 三个方法（`edit` / `undo` / `redo`）都遵守这一条。
4. **约束放在构造器里，比放在方法体里早一步** —— 早一步，就少一条路径能绕过它。
5. **能力与代价必须一起说** —— 只说"备忘录能撤销"，不说"内存是状态×步数"，是不合格的回答。

---

#### 如果只能记住三个坑

1. **`b.restore(a 的快照)` 会成功** —— 如果只检查 `instanceof`。
   `Memento` 是类，不是实例。（§4.3）
2. **`maxDepth = 3` 只能撤 2 次** —— 参数名读起来像"步数"，实际是"栈容量"。（§6.4.5）
3. **`record` 的 `equals` 比较所有组件** —— `undo.remove(snapshot)` 会误删"内容相同的另一张"。（§8.5 C3）

---

#### 最后：这套设计最值得学的是什么

不是"备忘录模式有三个角色"，也不是"要用窄接口"。

**是那种"把约束往前推"的习惯：**

```text
  "别忘了调 markInitial()"        →  构造器里自动拍
  "别忘了把 popMemento 的结果给 restore"  →  undo() 内部一步完成
  "别跨文档用同一份历史"           →  editor 是 final
  "别在外面 new Memento"          →  private record
  "别把快照塞给别的 editor"        →  ownerToken
```

**每一次"往前推"，都消灭了一类错误。**

而这些错误有一个共同点：**它们在编译期和测试里都不会报错，
只会在用户那里以"数据丢了"的形式出现。**

> **好的设计不是"功能更多"，而是"能犯的错更少"。**

---

## 附录

六个附录，全部是**可核对的原始数据**，不是概述。

| 附录 | 内容 | 用途 |
|---|---|---|
| A | 十三个文件的行数清单 | 核对规模 |
| B | 编译与运行验证原始日志 | 核对"零警告 + 三次一致" |
| C | 原项目缺陷探针全文（68 行） | 核对十条缺陷的实测证据 |
| D | 本项目真实输出全文（86 行） | 核对 §6 引用的每一行 |
| E | 与原项目逐项对照 | 核对"改了什么、保留了什么" |
| F | 术语表 | 统一用词 |

---

## 附录 A：十三个文件的行数清单

### A.1 精确统计

统计口径：
- **物理行**：文件的实际行数（含空行、注释、代码）；
- **空行**：全部由空白字符组成的行；
- **注释行**：以 `//`、`/*`、`*`、`*/` 开头的行（含 javadoc）；
- **代码行**：物理行 − 空行 − 注释行。

| 文件 | 物理行 | 空行 | 注释行 | 代码行 | 角色 |
|---|---:|---:|---:|---:|---|
| `Client.java` | 271 | 50 | 56 | 165 | 入口，六段 Demo |
| `ConsoleHistoryListener.java` | 36 | 5 | 18 | 13 | 撤销面板（控制台版） |
| `EditKind.java` | 70 | 11 | 42 | 17 | 七种编辑类型 |
| `Editor.java` | 334 | 29 | 200 | 105 | **Originator** |
| `EditorLimits.java` | 99 | 9 | 65 | 25 | 边界常量与校验 |
| `HistoryListener.java` | 42 | 2 | 35 | 5 | 监听器接口 |
| `HistoryStats.java` | 74 | 5 | 50 | 19 | 统计值对象 |
| `Snapshot.java` | 83 | 2 | 77 | 4 | **窄接口** |
| `SnapshotHistory.java` | 287 | 20 | 159 | 108 | **Caretaker** |
| `SnapshotMetadata.java` | 83 | 5 | 52 | 26 | 快照元信息 |
| `SnapshotRejectedException.java` | 40 | 3 | 30 | 7 | 归属校验异常 |
| `SteppedClock.java` | 60 | 5 | 33 | 22 | 确定性时钟 |
| `TextRegion.java` | 111 | 7 | 76 | 28 | 选区值对象 |
| **合计（13 文件）** | **1590** | **153** | **893** | **544** | |

### A.2 三个比例

```text
  注释 : 代码   =  893 : 544  ≈  1.64 : 1
  空行 : 物理行 =  153 : 1590 ≈  9.6%
  代码 : 物理行 =  544 : 1590 ≈ 34.2%
```

**注释比代码多 64%** —— 这在生产项目里偏高（通常 0.2 : 1 左右），
但在教学项目里是必要的：**"为什么"比"是什么"更需要解释**。

**注意 `Snapshot.java` 是极端案例：**

```text
  83 行 = 2 空行 + 77 注释 + 4 代码
  注释 : 代码 = 19 : 1
```

因为它的代码只有：

```java
public interface Snapshot {
    SnapshotMetadata metadata();
}
```

**但它是全项目最容易被误解的一个角色**（§5.1 列了三种常见误解），
所以 javadoc 花了 77 行讲"为什么只能有一个方法"。

### A.3 编译产物：14 个 class

```text
  源文件 13 个  →  class 文件 14 个
  多出来的 1 个：Editor$Memento.class
```

| class 文件 | 字节数 |
|---|---:|
| `Client.class` | 11655 |
| `ConsoleHistoryListener.class` | 1477 |
| `EditKind.class` | 1648 |
| `Editor.class` | 4506 |
| **`Editor$Memento.class`** | **2462** |
| `EditorLimits.class` | 1604 |
| `HistoryListener.class` | 336 |
| `HistoryStats.class` | 2458 |
| `Snapshot.class` | 203 |
| `SnapshotHistory.class` | 5544 |
| `SnapshotMetadata.class` | 3428 |
| `SnapshotRejectedException.class` | 361 |
| `SteppedClock.class` | 1916 |
| `TextRegion.class` | 2139 |
| **合计** | **40 137** |

**`Editor$Memento.class` 的存在，是"私有嵌套类"最硬的证据。**

它是一个**真实存在的类文件**，能被 `Class.forName("...Editor$Memento")` 找到（Demo 5 就是这么做的），
但**源码里写不出它的名字**。

**`Snapshot.class` 只有 203 字节** —— 因为它只有一个抽象方法。
这个数字侧面印证了"窄接口"确实很窄。

---

## 附录 B：编译与运行验证

### B.1 环境

```text
openjdk version "25.0.4.1" 2026-08-18 LTS
OpenJDK Runtime Environment Microsoft-14951867 (build 25.0.4.1+1-LTS)
OpenJDK 64-Bit Server VM Microsoft-14951867 (build 25.0.4.1+1-LTS, mixed mode, sharing)
```

JDK 路径：`F:\enCodingApps\JDK\jdk-25`

### B.2 编译命令

```powershell
javac -Xlint:all -d out -encoding UTF-8 `
    src\com\patterns\behavioral\memento\*.java
```

**`-Xlint:all` 是关键。** 它打开了全部编译警告，包括：

| 警告类别 | 检查什么 |
|---|---|
| `serial` | `Serializable` 类缺 `serialVersionUID` |
| `this-escape` | 构造器里泄漏 `this` |
| `fallthrough` | `switch` 穿透 |
| `rawtypes` | 裸类型 |
| `unchecked` | 未检查的转换 |
| `deprecation` | 用了废弃 API |
| `preview` | 用了预览特性 |

**本项目零警告的原因（逐项）：**

- **`serial`**：`SnapshotRejectedException` 加了 `serialVersionUID = 1L`；
- **`this-escape`**：`SnapshotHistory` 构造器里调了 `capture()`，但 `capture()` 是 `private`，
  且 `SnapshotHistory` 是 `final` —— 不会泄漏 `this` 到子类；
- **`rawtypes`**：没有裸类型；
- **`unchecked`**：没有泛型转换；
- **`deprecation`**：`Stack` 是"遗留类"但没有 `@Deprecated` 注解，
  所以即使原项目用它也不报警 —— **这也是为什么探针 P7 有价值**：
  编译器不会告诉你 `Stack` 该退休了，只能靠人知道。

### B.3 编译结果

```text
COMPILE EXIT: 0
(javac produced no output -> zero warnings)
```

**"没有输出"就是"没有警告"。** `javac` 只在有问题时打印。

### B.4 运行命令

```powershell
java -Dfile.encoding=UTF-8 -cp out com.patterns.behavioral.memento.Client
```

**`-Dfile.encoding=UTF-8` 是必需的** —— 否则 Windows 控制台会用 GBK 解码，
中文输出会变成乱码（这就是第一次跑原项目时看到 mojibake 的原因）。

### B.5 运行结果

```text
RUN EXIT : 0
OUT BYTES: 3857
OUT LINES: 86
OUT SHA256: 9598E3649AAA38B2CAAC38300E892349349E7E7DD7E5830FB9083174BD47261E
ERR BYTES: 0
```

**五项全部可核对：**

| 项 | 值 | 含义 |
|---|---|---|
| 退出码 | 0 | 没有未捕获异常 |
| 标准输出 | 3857 字节 | 与附录 D 逐字对应 |
| 行数 | 86 | 含空行与最后一行 `[DONE]` |
| SHA-256 | `9598E364...` | **三次运行完全相同** |
| 标准错误 | 0 字节 | 没有任何警告或堆栈 |

### B.6 三次一致性验证

验证脚本做的事：

```powershell
1..3 | ForEach-Object {
    # 清空 out 目录，重新编译
    # 运行 Client，输出重定向到 run_$_.txt
    # 计算 SHA-256
}
# 比较三个哈希
```

结果：

```text
run1 = 9598E3649AAA38B2CAAC38300E892349349E7E7DD7E5830FB9083174BD47261E
run2 = 9598E3649AAA38B2CAAC38300E892349349E7E7DD7E5830FB9083174BD47261E
run3 = 9598E3649AAA38B2CAAC38300E892349349E7E7DD7E5830FB9083174BD47261E
一致 = True
```

**为什么"三次"而不是"两次"？**

两次一致有 1/2 的巧合概率（如果输出在两种状态间振荡）。
三次一致把巧合概率降到更低，而且能暴露"每隔一次变一次"这类周期性不确定。

**成本很低（每次 < 1 秒），所以做三次。**

### B.7 原项目基线（用于对照）

```text
文件数   : 4（Client / Document / DocumentMemento / History）
物理行   : 131
OUT BYTES: 150
OUT LINES: 6
OUT SHA256: EBFDF25256129334BC186660193FEC2BFFA3D8DC4A2CF4E5D990D3403CFE5E1E
COMPILE  : 零警告（原项目也没用 -Xlint:all 的问题）
```

**规模对比：**

```text
  原项目：4 文件 / 131 行 / 6 行输出
  本项目：13 文件 / 1590 行 / 86 行输出
```

**12 倍的行数，14 倍的输出。** 这不是"过度设计"，
而是"把 10 条缺陷逐条修掉 + 补上 6 段可验证的 Demo"的必然结果。

详见附录 E 的逐项对照。

---

## 附录 C：原项目缺陷探针全文

以下是一段独立探针程序的**真实输出**（68 行），
它针对原项目的 4 个源文件，逐条取证十处缺陷。

探针的编译运行方式与本项目相同（JDK 25，`-Xlint:all`，UTF-8 输出）。

```text
[P1 restore(null) 直接 NPE]
  history 只有 1 条时，popMemento() 返回 = null
  restore(null) 抛出 java.lang.NullPointerException
  抛点 = BehaviorType.MementoPattern.Document.restore(Document.java:33)
  -> Document.restore 对入参零校验

[P2 忽略 popMemento() 返回值：历史被静默吃掉]
  三条历史入栈，文档 = v2
  丢弃两次返回值后，文档 = v2（一次都没变）
  第三次 popMemento() = null
  -> history 已空，document 仍是 v2：两边彻底脱节

[P3 用 null 同时表达「没有更多」和「正常返回」]
  空 History.popMemento()          = null
  只有 1 条的 History.popMemento()  = null
  -> 调用方无法区分「到底了」和「出错了」

[P4 备忘录可被外部伪造]
  外部 new DocumentMemento("伪造内容") 后 restore -> 文档 = 伪造内容
  唯一构造器 = public BehaviorType.MementoPattern.DocumentMemento(java.lang.String)
  是 public？true
  -> javadoc 称「不允许外部修改」，实际任何人都能造一个新的

[P5 只存 content 一个组件]
  DocumentMemento 的 record 组件 = [java.lang.String content]
  组件数 = 1
  -> 想恢复「光标位置」「选区」「时间戳」，必须同时改三个类

[P6 setContent 无校验：null 一路进到历史]
  setContent(null) 后 getContent() = null
  对 null 求 save() 得到 = DocumentMemento[content=null]
  null 快照已进入 history，没有任何一层拒绝它
  栈内实际内容 = [DocumentMemento[content=好内容], DocumentMemento[content=null]]
  -> 一旦 restore 到这条快照，文档就真的没有内容了

[P7 History 用的是遗留类 Stack（继承 Vector）]
  Stack 的父类 = java.util.Vector
  Stack 自己声明的 public 方法数 = 5
  Vector 可见的 public 方法数   = 66
  Stack.push 是 synchronized？false（它只是转发给 addElement）
  Vector.addElement 是 synchronized？true
  Vector.get 是 synchronized？true
  正常栈 = [a, b, c]，peek() = c
  add(1,"X") 后 = [a, X, b, c]（顺序被插队）
  get(0) = a，remove(0) = a
  剩余 = [X, b, c]，size() = 3
  History.history 的运行时类型 = java.util.Stack
  -> 对外暴露 66 个 public 方法，只有 5 个是栈该有的

[P8 历史无容量上限]
  push 100000 次后 history.size() = 100000
  -> 没有任何容量上限、淘汰策略或压缩

[P9 popMemento 与 restore 不是原子操作]
  三态入栈，文档 = C
  popMemento() 返回 DocumentMemento[content=B]，但文档仍是 C
  ↑ 错位窗口：history 已认为当前是 B，document 还是 C
  中间层抛出 模拟中间层失败
  失败后文档 = C：document 从未变成 B，
  下一次 popMemento() 直接给 DocumentMemento[content=A] —— B 被跳过

[P10 History 不绑定 Originator：跨文档污染]
  两个不同文档共用一份 History
  d2 撤销一次后 = D1 的内容
  -> 撤销把 d1 的快照装进了 d2

[DONE] 10 处缺陷取证完毕
```

### C.1 十条缺陷与本项目的修法对照

| 探针 | 缺陷 | 本项目的修法 | 位置 |
|---|---|---|---|
| P1 | `restore(null)` NPE | 三重校验：null / 类型 / 归属令牌 | `Editor.restore` |
| P2 | 忽略返回值导致脱节 | `undo()` 内部一步完成，无返回值可忘 | `SnapshotHistory.undo` |
| P3 | `null` 双语义 | 返回 `boolean` | `SnapshotHistory.undo` |
| P4 | 备忘录可伪造 | `private record` + 窄接口 | `Editor.Memento` |
| P5 | 只存 `content` | 存 `text` + `cursor` + `selection` + 元信息 | `Editor.Memento` |
| P6 | `null` 进历史 | `EditorLimits.requireText` 在 `replaceRange` / `replaceAll` 拦截 | `Editor` |
| P7 | `Stack extends Vector` | `ArrayDeque` | `SnapshotHistory` 字段 |
| P8 | 无上限 | `maxDepth` + `trim()` + `evicted` 计数 | `SnapshotHistory` |
| P9 | 非原子 | `edit()` 包住一段操作 + 失败回滚 | `SnapshotHistory.edit` |
| P10 | 跨文档污染 | `final Editor` + `ownerToken` | `SnapshotHistory` / `Editor` |

### C.2 一处必须说明的"探针修正"

探针第一版在 P7 上写错过一句：

```text
  ❌ Stack.push 是 synchronized？true          （第一版，错）
  ✅ Stack.push 是 synchronized？false          （修正后）
     Vector.addElement 是 synchronized？true
```

**根因：`Stack.push` 本身没有 `synchronized`，它只是转发给 `Vector.addElement`。**

这个修正很重要，因为它恰恰**加强**了 P7 的论点：

- 如果 `Stack.push` 自己是 `synchronized`，那"用 Stack 就线程安全"至少表面上成立；
- 但事实是**只有被转发的那个方法加锁**，所以"一次 `push`"是原子的，
  "一次撤销（弹栈 + 恢复 + 入另一个栈）"完全不是原子的。

**"看起来线程安全"比"明显不线程安全"更危险。**

---

## 附录 D：本项目真实输出全文

以下是 `Client.main()` 的完整输出（86 行 / 3857 字节），
`SHA-256 = 9598E3649AAA38B2CAAC38300E892349349E7E7DD7E5830FB9083174BD47261E`。

**逐字复制，未做任何美化或删减。**

```text

=== Demo 1：撤销与重做 ===
  初始 → 正文「客服：您好，您的订单」 光标 10 选区 [10,10)
  [编辑] 末尾补「已发货」
      [面板] 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1 | 正文 13 字
  正文 → 正文「客服：您好，您的订单已发货」 光标 13 选区 [13,13)
  [编辑] 补「预计明天送达」
      [面板] 可撤 2 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 2 | 正文 20 字
  正文 → 正文「客服：您好，您的订单已发货，预计明天送达」 光标 20 选区 [20,20)
  [编辑] 补句号
      [面板] 可撤 3 步 | 可重 0 步 | 已拍 4 淘汰 0 编辑 3 | 正文 21 字
  正文 → 正文「客服：您好，您的订单已发货，预计明天送达。」 光标 21 选区 [21,21)
  连续撤销三次：
      [面板] 可撤 2 步 | 可重 1 步 | 已拍 5 淘汰 0 编辑 3 | 正文 20 字
    undo#1 = true → 客服：您好，您的订单已发货，预计明天送达
      [面板] 可撤 1 步 | 可重 2 步 | 已拍 6 淘汰 0 编辑 3 | 正文 13 字
    undo#2 = true → 客服：您好，您的订单已发货
      [面板] 可撤 0 步 | 可重 3 步 | 已拍 7 淘汰 0 编辑 3 | 正文 10 字
    undo#3 = true → 客服：您好，您的订单
  连续重做两次：
      [面板] 可撤 1 步 | 可重 2 步 | 已拍 8 淘汰 0 编辑 3 | 正文 13 字
    redo#1 = true → 客服：您好，您的订单已发货
      [面板] 可撤 2 步 | 可重 1 步 | 已拍 9 淘汰 0 编辑 3 | 正文 20 字
    redo#2 = true → 客服：您好，您的订单已发货，预计明天送达
  统计 可撤 2 步 | 可重 1 步 | 已拍 9 淘汰 0 编辑 3

=== Demo 2：一段操作 = 一步撤销 ===
  一次 edit 内敲 4 个字 → 「合规审核」
  第二次 edit 后 → 「合规审核完成」
  统计 可撤 2 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 2
  注意：只敲了 2 次 edit，尽管内部调用了 5 次 type()
  连续撤销：
    undo#1 = true → 「合规审核」
    undo#2 = true → 「」
    undo#3 = false → 「」
  第 3 次返回 false：已经退到最早保留的版本，不会再往后退
  原项目把「什么时候拍快照」交给调用方 —— 只要有人在循环里
  setContent 后跟一次 push，就会变成 N 步撤销；edit() 把边界钉死在调用处

=== Demo 3：编辑中途失败，自动回滚 ===
  正常编辑后 → 「原稿：本条款有效，自签署日起生效」
  失败前 可撤 1 步 | 可重 0 步 | 已拍 2 淘汰 0 编辑 1
  捕获 IllegalStateException：缺少签署方，不允许替换
  失败后 → 「原稿：本条款有效，自签署日起生效」
  失败后 可撤 1 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 1
  正文回到进门前？true
  这篇快照没有入栈，所以「可撤」步数没变
  「已拍」多了 1：快门按过了，只是底片没进相册

=== Demo 4：容量上限 3，最老的快照被淘汰 ===
  连做 4 次编辑后 → 「第4版」
  统计 可撤 2 步 | 可重 0 步 | 已拍 5 淘汰 2 编辑 4
  「淘汰 2」= 第0版和第1版的快照已被挤出栈
  连续撤销：
    undo#1 = true → 「第3版」
    undo#2 = true → 「第2版」
    undo#3 = false → 「第2版」
    undo#4 = false → 「第2版」
  只能撤到第2版为止 —— 这就是容量上限的代价，必须让用户知道

=== Demo 5：快照不能伪造，也不能串档 ===
  备忘录的运行时类 = com.patterns.behavioral.memento.Editor$Memento
  是 private？true
  是 record？true
  外部代码写不出 new Editor.Memento(...)：这个类型在源码里不可见
  a 自己拍一张 → #999 输入 · 手工拍的 · 09:00:00.000
  a.restore(自己的) 成功 → 「文档 A」
  b.restore(a 的) 被拒 → 拒绝恢复：#999 输入 · 手工拍的 · 09:00:00.000 不是本编辑器产生的快照
  窄接口暴露的方法只有 [public abstract com.patterns.behavioral.memento.SnapshotMetadata com.patterns.behavioral.memento.Snapshot.metadata()]
  注意：metadata 里的 999 是伪造得出来的 —— 元信息可以编，正文编不了

=== Demo 6：撤销到底，新编辑清空重做栈 ===
  两次编辑后 → 「第三稿」
  统计 可撤 2 步 | 可重 0 步 | 已拍 3 淘汰 0 编辑 2
  一路撤销：
    第 1 次 undo = true → 「第二稿」
    第 2 次 undo = true → 「第一稿」
    再来一次 undo = false → 已到最早保留的版本「第一稿」
  统计 可撤 0 步 | 可重 2 步 | 已拍 5 淘汰 0 编辑 2
  重做一次 = true → 「第二稿」
  统计 可撤 1 步 | 可重 1 步 | 已拍 6 淘汰 0 编辑 2
  新编辑后 → 「新的一段」
  统计 可撤 2 步 | 可重 0 步 | 已拍 7 淘汰 0 编辑 3
  再 redo = false → 重做栈已被新编辑清空

[DONE] 六段 Demo 全部通过
```

### D.1 逐段行数

| 段落 | 行范围 | 行数 | 内容 |
|---|---:|---:|---|
| 空行 | 1 | 1 | 输出开头的空行（`section()` 打的） |
| Demo 1 | 2-25 | 24 | 撤销/重做闭环 + 面板实时刷新 |
| 空行 | 26 | 1 | |
| Demo 2 | 27-38 | 12 | 一段操作 = 一步撤销 |
| 空行 | 39 | 1 | |
| Demo 3 | 40-48 | 9 | 失败自动回滚 |
| 空行 | 49 | 1 | |
| Demo 4 | 50-59 | 10 | 容量淘汰 |
| 空行 | 60 | 1 | |
| Demo 5 | 61-70 | 10 | 伪造与串档 |
| 空行 | 71 | 1 | |
| Demo 6 | 72-84 | 13 | 撤到底 + 重做作废 |
| 空行 | 85 | 1 | |
| 收尾 | 86 | 1 | `[DONE]` |
| **合计** | | **86** | |

**每一个空行都是 `section()` 里的 `System.out.println()` 打的：**

```java
private static void section(String title) {
    System.out.println();                       // ← 这个空行
    System.out.println("=== " + title + " ===");
}
```

### D.2 六段的关键数字索引

| 段落 | 快照编号范围 | 最终 `sequence` | 最终 `undo + redo` |
|---|---|---:|---|
| Demo 1 | #1 - #9 | 9 | 3 + 1 = 4 |
| Demo 2 | #1 - #5 | 5 | 1 + 2 = 3 |
| Demo 3 | #1 - #3 | 3 | 2 + 0 = 2 |
| Demo 4 | #1 - #7 | 7 | 1 + 2 = 3 |
| Demo 5 | #999（独立） | — | — |
| Demo 6 | #1 - #7 | 7 | 3 + 0 = 3 |

**每个 Demo 的 `sequence` 都从 1 重新开始**，因为每段都新建一个 `SnapshotHistory`。

**Demo 5 是唯一的例外** —— 它不走 `SnapshotHistory`，直接构造 `SnapshotMetadata(999, ...)`。

---

## 附录 E：与原项目逐项对照

### E.1 文件级对照

| 原项目 | 行数 | 本项目 | 行数 | 关系 |
|---|---:|---|---:|---|
| `Client.java` | 42 | `Client.java` | 271 | **重写**（1 段 → 6 段） |
| `Document.java` | 36 | `Editor.java` | 334 | **重写并扩展**（1 字段 → 3 字段 + 8 个方法） |
| `DocumentMemento.java` | 12 | `Editor$Memento`（嵌套） | 8 | **改私有嵌套 record** |
| `History.java` | 40 | `SnapshotHistory.java` | 287 | **重写并扩展** |
| — | — | `Snapshot.java` | 83 | 新增（窄接口） |
| — | — | `SnapshotMetadata.java` | 83 | 新增 |
| — | — | `EditKind.java` | 70 | 新增 |
| — | — | `TextRegion.java` | 111 | 新增 |
| — | — | `EditorLimits.java` | 99 | 新增 |
| — | — | `SnapshotRejectedException.java` | 40 | 新增 |
| — | — | `HistoryStats.java` | 74 | 新增 |
| — | — | `HistoryListener.java` | 42 | 新增 |
| — | — | `ConsoleHistoryListener.java` | 36 | 新增 |
| — | — | `SteppedClock.java` | 60 | 新增 |
| **4 文件 / 131 行** | | **13 文件 / 1590 行** | | |

**"保留"的判断标准：**

| 原项目的东西 | 保留了吗 | 说明 |
|---|---|---|
| `save()` 返回备忘录 | ✅ 保留（改名为 `snapshot()`） | 核心动作不变 |
| `restore(memento)` | ✅ 保留（改参数类型为 `Snapshot`） | 核心动作不变 |
| `History` 有两个栈 | ✅ 保留（`undo` + `redo`） | 结构不变 |
| `pushMemento` 里清空 redo | ✅ 保留（移到 `edit()` 成功路径） | 语义不变，时机更精确 |
| 类名 `Document` | ❌ 改为 `Editor` | 场景从"文档"换成"编辑器"，字段从 1 个变成 3 个 |
| `Stack` | ❌ 改为 `ArrayDeque` | 探针 P7 |

**注意：`DocumentMemento` 从"独立 public record（12 行）"变成了"嵌套 private record（8 行）"。**

**行数从 12 降到 8，但安全性从"零"变成了"编译期隔离"。**
这是"行数不是指标"的一个好例子。

### E.2 十条缺陷的修法对照

| # | 原项目 | 本项目 | 验证方式 |
|---:|---|---|---|
| P1 | `restore` 零校验 | 三重校验 + 专属异常 | Demo 5 + 单元测试可覆盖 |
| P2 | 返回值可忽略 | `undo()` 无返回值可忘 | Demo 1 |
| P3 | `null` 双语义 | `boolean` + `canUndo()` | Demo 6 |
| P4 | `public record` 可伪造 | `private record` + 窄接口 | Demo 5（反射取证） |
| P5 | 只存 `content` | 存 3 个状态字段 + 元信息 | Demo 1（光标/选区跟得上） |
| P6 | `null` 进历史 | `requireText` 在写入口拦截 | 未做 Demo（结构保证） |
| P7 | `Stack` | `ArrayDeque` | 探针 P7 + 代码审查 |
| P8 | 无上限 | `maxDepth` + `trim()` + `evicted` | Demo 4 |
| P9 | 非原子 | `edit()` 原子 + 失败回滚 | Demo 3 |
| P10 | 跨文档污染 | `final Editor` + `ownerToken` | Demo 5 |

**P6 没有专属 Demo，是被"结构消除"的：**

```java
private void replaceRange(int from, int to, String replacement) {
    String next = text.substring(0, from) + replacement + text.substring(to);
    this.text = EditorLimits.requireText(next);        // ← 唯一的写入口
    // ...
}

public void replaceAll(String newText) {
    this.text = EditorLimits.requireText(newText);     // ← 另一个写入口
    // ...
}
```

**`Editor` 里只有这两个地方写 `this.text`，两处都过 `requireText`。**
所以"null 进历史"在结构上不可能 —— 不需要 Demo 证明。

**"结构消除"比"测试覆盖"更彻底**（§6.7 已经说过）。

### E.3 行为对照表

| 行为 | 原项目 | 本项目 |
|---|---|---|
| 拍快照 | 调用方手动 `save()` + `pushMemento()` | `history.edit(kind, detail, action)` 内部自动 |
| 撤销 | `doc.restore(history.popMemento())` | `history.undo()` |
| 重做 | `doc.restore(history.popRedoMemento())` | `history.redo()` |
| 撤销到底 | 返回 `null`，调用方自己判 | 返回 `false` |
| 一步 = 一次操作 | ❌ 由调用方决定，容易多拍 | ✅ `edit()` 的边界 = 一步 |
| 编辑失败 | 无回滚，可能留半截 | 自动 `restore(before)` |
| 容量上限 | ❌ 无 | ✅ `maxDepth` + 淘汰 |
| 快照不透明 | ❌ `public record`，全字段可读 | ✅ 窄接口 + 私有实现 |
| 跨文档保护 | ❌ 无 | ✅ 两道防线 |
| 撤销面板数据 | ❌ 无 | ✅ `HistoryStats` + 监听器 |
| 输出可复现 | ✅ 恰好可（没时间戳） | ✅ 靠注入时钟保证 |

**最后一行值得说明：**

原项目"恰好可复现"是因为**它没有任何不确定性来源**（没有时间、没有随机、没有哈希）。
这是一种"被动可复现"。

本项目增加了 `Instant` 字段（会引入不确定性），
所以必须**主动**注入时钟来抵消 —— 这是"主动可复现"。

**项目变复杂之后，"可复现"从"免费"变成了"需要设计"。**

### E.4 规模与复杂度的诚实评估

```text
                    原项目      本项目      倍数
  文件数              4          13        3.25x
  物理行            131        1590       12.1x
  代码行           ~100         544        5.4x
  注释行             ~20         893       44.7x
  class 文件          4          14        3.5x
  输出行数            6          86       14.3x
```

**注释行增长 44.7 倍，远超代码行增长的 5.4 倍。**

这符合预期：

- 原项目的注释主要是"这是什么"（几行 javadoc）；
- 本项目的注释是"为什么这样、不这样会怎样、代价是什么"（每处决策一段）。

**如果这是生产项目，893 行注释是过量的。**
但作为**教学项目**，解释"为什么"才是主要内容 —— 代码只是承载解释的载体。

**判断标准：**

> **生产代码的注释回答"这段代码在做什么"（因为读者是同事，他懂业务）；
> 教学代码的注释回答"为什么不能是别的样子"（因为读者是学生，他不知道取舍）。**

### E.5 明确"未验证"的部分

按任务书第五步的要求："无法实际运行的代码必须明确说明，不能声称验证成功。"

**已验证（真实运行过）：**

| 项 | 证据 |
|---|---|
| 13 个源文件全部编译 | `COMPILE EXIT: 0`，零警告 |
| 6 段 Demo 全部运行 | 86 行输出，退出码 0 |
| 输出可复现 | 三次 SHA-256 完全一致 |
| 原项目 4 文件编译 | `COMPILE EXIT: 0`，6 行输出 |
| 10 条缺陷 | 探针 68 行输出，三次哈希一致 |

**未验证（只编译过，未运行）：**

| 项 | 原因 | 风险 |
|---|---|---|
| `Editor.selectedText()` | Demo 未调用 | 低（一行 `substring`） |
| `Editor.deleteBackward(int)` | Demo 未调用 | **中**（有 `clamp` 逻辑，边界未跑） |
| `Editor.deleteSelection()` | Demo 未调用 | 低（委托 `replaceRange`） |
| `Editor.selectAll()` | Demo 未调用 | 低（委托 `select`） |
| `Editor.moveCaret(int)` | Demo 未调用 | **中**（有 `clamp` 逻辑） |
| `SnapshotHistory.reset(String)` | Demo 未调用 | **中**（清栈 + 拍基线，逻辑未跑） |
| `SnapshotHistory.removeListener(...)` | Demo 未调用 | 低（一行 `List.remove`） |
| `HistoryStats.canUndo()` / `canRedo()` | Demo 未调用 | 低（一行比较） |
| `TextRegion.shift(int)` | Demo 未调用 | **中**（有越界钳制，未跑） |
| `EditorLimits.DEFAULT_UNDO_DEPTH` | 常量，无引用 | 无 |

**标"中"的四个，是本项目最应该补测试的地方。**

### E.6 补测试的建议顺序

如果要把本项目变成"生产可用"，建议按这个顺序补：

1. **`Editor` 的五个未运行方法** —— 单元测试，每个 2-3 个用例（正常 + 边界）；
2. **`SnapshotHistory.reset()`** —— 断言"reset 之后 undo 返回 false、redo 返回 false"；
3. **`TextRegion.shift()`** —— 断言"平移后越界被钳制"；
4. **`Editor.restore` 的三条拒绝路径** —— null / 类型错 / 令牌错（后两条需要反射构造）；
5. **`edit()` 的失败分支** —— 断言"栈不变、redo 不清空、正文已回滚"。

**第 4 条最难，因为 `Memento` 是私有的。** 需要：

```java
Class<?> m = Class.forName("com.patterns.behavioral.memento.Editor$Memento");
// 或者用包内可见的测试辅助类（同包测试）
```

**这也从侧面说明：`private` 确实提高了测试成本。**
这是 §8.2 代价五的实证。

---

## 附录 F：术语表

### F.1 三个角色

| 术语 | 英文 | 本项目对应 | 一句话职责 |
|---|---|---|---|
| **原发器** | Originator | `Editor` | 能拍快照、能恢复；**唯一能读写快照内容的人** |
| **备忘录** | Memento | `Snapshot`（窄）+ `Editor.Memento`（宽） | 存状态；对外**不透明** |
| **负责人** | Caretaker | `SnapshotHistory` | 保管快照、决定何时拍；**不知道内容** |

### F.2 接口与封装

| 术语 | 含义 | 本项目位置 |
|---|---|---|
| **宽接口** | 能读写快照全部字段的接口；只有 Originator 可见 | `Editor.Memento` 的组件 |
| **窄接口** | 只暴露元信息的接口；Caretaker 可见的全部 | `Snapshot` |
| **不透明** | 持有者无法读取内容，只能传递 | `Snapshot` 只有一个方法 |
| **归属令牌** | 用于判断"这张快照是不是我拍的"的对象标识 | `Editor.ownerToken` |
| **防御性拷贝** | 构造时复制可变对象，避免外部修改影响内部 | `List.copyOf` / `TextRegion` |

### F.3 历史管理

| 术语 | 含义 | 本项目位置 |
|---|---|---|
| **快照** | 某一瞬间状态的完整副本 | `Memento` |
| **撤销栈** | 存放"可撤销的状态"的栈 | `SnapshotHistory.undo` |
| **重做栈** | 存放"可重做的状态"的栈 | `SnapshotHistory.redo` |
| **基线** | 栈底那张快照，"最早保留的版本" | `EditKind.BASELINE` |
| **淘汰** | 超出容量上限时移除最老的快照 | `trim()` + `evicted` |
| **原子操作** | 内部可以有多次修改，但对外是"一步" | `edit()` 的 `action` |
| **回滚** | 操作失败时恢复到进入前的状态 | `edit()` 的 catch 分支 |
| **分支历史** | 撤销后新编辑不清空重做栈，形成树 | 本项目**未实现**（§7.7） |

### F.4 Java 语言特性

| 术语 | 含义 | 为什么本项目用它 |
|---|---|---|
| **record** | 不可变数据载体，自动生成访问器/`equals`/`hashCode`/`toString` | 快照与值对象只需要"装值" |
| **紧凑构造器** | record 里的 `public Foo { ... }`，用于校验与归一化 | 在构造期消灭 null 与非法值 |
| **sealed** | 限制谁能继承/实现 | 本项目**未用**（`Snapshot` 不是 sealed，见下） |
| **嵌套类** | 定义在另一个类内部的类 | `Memento` 必须是 `Editor` 的内部细节 |
| **`ArrayDeque`** | 基于循环数组的双端队列 | 替代 `Stack`（探针 P7） |
| **`Deque`** | 双端队列接口 | 声明类型比 `ArrayDeque` 更窄 |
| **`Supplier<T>`** | 无参、返回 `T` 的函数式接口 | 注入时钟 |
| **`Consumer<T>`** | 接收 `T`、无返回的函数式接口 | `edit()` 的 `action` 参数 |
| **`@FunctionalInterface`** | 标记"只有一个抽象方法"的接口 | `HistoryListener` |

**`Snapshot` 为什么不是 `sealed`？**

```java
// 如果写成这样：
public sealed interface Snapshot permits Editor.Memento { }
```

**问题：`Editor.Memento` 是 `private`，`sealed` 的 `permits` 子句需要可见性兼容。**
技术上可以（`sealed` 允许 `private` 实现，在同一编译单元内），
但会让"`Snapshot` 只有一个实现"这件事变成**硬约束**。

**而硬约束的代价是：将来加第二个实现（比如 `LazySnapshot`、`DeltaSnapshot`）就要改 `permits`。**

本项目选"软约束"：`Snapshot` 是普通接口，任何类都能实现。
**但 `Editor.restore` 里的 `instanceof Memento` 检查会拒绝非 `Memento` 的实现。**

**两种做法的区别：**

| 做法 | 加新实现时 | 错误何时暴露 |
|---|---|---|
| `sealed` | 必须改 `permits` 子句 | 编译期 |
| 普通接口 + `instanceof` | 不需要改接口 | 运行期（`SnapshotRejectedException`） |

**本项目选后者，因为 §7.5（差量快照）和 §7.3（持久化）都可能在将来引入第二种实现。**

### F.5 文本与区间

| 术语 | 含义 | 本项目位置 |
|---|---|---|
| **半开区间** | `[start, end)`，含起点不含终点 | `TextRegion` |
| **光标** | 一个位置（不是区间） | `TextRegion.caret(pos)` = `[pos, pos)` |
| **空区间** | `start == end`，长度为 0 | `TextRegion.isEmpty()` |
| **钳制** | 把越界值压到合法范围而非抛异常 | `EditorLimits.clamp` |

### F.6 验证相关

| 术语 | 含义 | 本项目做法 |
|---|---|---|
| **确定性时钟** | 每次调用返回可预测时间的时钟 | `SteppedClock` |
| **可复现输出** | 同一份代码多次运行，字节完全相同 | 三次 SHA-256 一致 |
| **零警告** | `javac -Xlint:all` 无任何输出 | `COMPILE EXIT: 0` + 无输出 |
| **探针** | 针对"已知可疑点"写的最小验证程序 | 临时源文件（验完即删） |
| **取证** | 用实测输出证明缺陷存在，而非"读了代码觉得有问题" | 附录 C 的 68 行输出 |

### F.7 本项目**没有**使用的术语

列出来是为了避免误读：

| 术语 | 为什么不用 |
|---|---|
| **快照隔离**（Snapshot Isolation） | 这是数据库事务的概念，与备忘录模式无关 |
| **写时复制**（Copy-on-Write） | 本项目是"拍快照时全量复制"，不是"修改时复制" |
| **事件溯源**（Event Sourcing） | 那是命令模式 + 事件日志，不是备忘录 |
| **Memento 容器** | 非标准术语，本项目统一叫"撤销栈 / 重做栈" |
| **状态快照** vs **状态机** | 前者是数据（本项目），后者是行为（`StatePattern`） |

**最后一行值得强调：**

```text
  备忘录模式  →  存"状态数据"，为了"回退"
  状态模式    →  管"状态行为"，为了"向前演化"
```

**两者都叫"状态"，但一个是**名词**（一份数据），一个是**动词**（一个转移规则）。**

---

## 附录的附录：文件清单与阅读顺序

```text
src/com/patterns/behavioral/memento/
├── 1. 先读角色
│   ├── Snapshot.java                    窄接口
│   ├── SnapshotMetadata.java            元信息
│   └── Editor.java                      Originator（含 Memento）
├── 2. 再读历史
│   ├── SnapshotHistory.java             Caretaker
│   ├── HistoryStats.java                统计
│   └── EditKind.java                    类型
├── 3. 读值对象与工具
│   ├── TextRegion.java                  选区
│   ├── EditorLimits.java                边界
│   ├── SteppedClock.java                时钟
│   └── SnapshotRejectedException.java   异常
├── 4. 读监听器
│   ├── HistoryListener.java             接口
│   └── ConsoleHistoryListener.java      实现
└── 5. 最后读入口
    └── Client.java                      六段 Demo
```

**建议配合阅读：**

| 想理解 | 读 README 的 |
|---|---|
| 为什么需要备忘录 | §1、§3 |
| 原项目的具体缺陷 | §2、附录 C |
| 十三个设计决定 | §4 |
| 每个文件为什么存在 | §5 |
| 运行时的每一步 | §6 |
| 加需求时改哪里 | §7 |
| 代价与坑 | §8 |
| 面试准备 | §9 |

---

**文档结束。**

