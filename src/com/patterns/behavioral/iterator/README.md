# 迭代器模式（Iterator Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：让你能一个一个地访问聚合对象里的元素，而不暴露它的内部结构。
>
> **本项目的场景**：一张差旅报销单的审批流。它是一棵**树**，不是一条线 ——
> 于是"遍历"这件事第一次变得值得单独拿出来做。

| 项目 | 原项目 | 本项目 |
|---|---|---|
| 源文件 | 4 个 | 11 个 |
| 物理行 | 109 行 | 1807 行 |
| 代码行 | 61 行 | 490 行 |
| 容器结构 | 一维数组 | 树（顺序块 / 分支 / 步骤） |
| 遍历方式 | 1 种（写死在内部类里） | 2 种（DFS / BFS）+ 可再包装过滤 |
| 能否增强 for | **不能** | 能 |
| 编译告警 | 0 | 0（`-Xlint:all`） |
| 真实输出 | 4 行 | 120 行 |

---

## 目录

**第一部分：问题从哪来**

- [1. 现实案例：一张报销单，卡在了哪一步](#1-现实案例一张报销单卡在了哪一步)
  - [1.1 一个真实的上午](#11-一个真实的上午)
  - [1.2 主管的第一个问题：这张单子走到哪了](#12-主管的第一个问题这张单子走到哪了)
  - [1.3 第一版：一条线，一个 for 循环](#13-第一版一条线一个-for-循环)
  - [1.4 需求变了：金额超过 1000 要走总监](#14-需求变了金额超过-1000-要走总监)
  - [1.5 第二版：把分叉硬塞进数组](#15-第二版把分叉硬塞进数组)
  - [1.6 第三个需求：换一个角度看流程](#16-第三个需求换一个角度看流程)
  - [1.7 归纳：迭代器模式的三个承诺](#17-归纳迭代器模式的三个承诺)

- [2. 原项目：109 行与八个问题](#2-原项目109-行与八个问题)
  - [2.1 四个文件](#21-四个文件)
  - [2.2 原项目的真实输出](#22-原项目的真实输出)
  - [2.3 问题一：Workflow 没有实现 Iterable](#23-问题一workflow-没有实现-iterable)
  - [2.4 问题二：next() 返回 Object](#24-问题二next-返回-object)
  - [2.5 问题三：next() 到底返回 null](#25-问题三next-到底返回-null)
  - [2.6 问题四：容量满了静默丢弃](#26-问题四容量满了静默丢弃)
  - [2.7 问题五：没有快照，也没有 fail-fast](#27-问题五没有快照也没有-fail-fast)
  - [2.8 问题六：非静态内部类持有外部引用](#28-问题六非静态内部类持有外部引用)
  - [2.9 问题七：类型只能靠名字猜](#29-问题七类型只能靠名字猜)
  - [2.10 问题八：只有一种遍历顺序](#210-问题八只有一种遍历顺序)
  - [2.11 八个问题汇总](#211-八个问题汇总)

**第二部分：怎么设计出来的**

- [3. 设计动机：什么时候才真的需要迭代器](#3-设计动机什么时候才真的需要迭代器)
  - [3.1 迭代器到底解决什么问题](#31-迭代器到底解决什么问题)
  - [3.2 下标遍历的三个隐含假设](#32-下标遍历的三个隐含假设)
  - [3.3 什么时候不需要迭代器](#33-什么时候不需要迭代器)
  - [3.4 模式的四个角色在本项目的落点](#34-模式的四个角色在本项目的落点)
  - [3.5 为什么本项目选"树"当容器](#35-为什么本项目选树当容器)

- [4. 设计推导：十一个决定](#4-设计推导十一个决定)
  - [4.0 从 4 个文件到 11 个文件](#40-从-4-个文件到-11-个文件)
  - [4.1 决定一：为什么必须有 WorkflowNode 接口](#41-决定一为什么必须有-workflownode-接口)
  - [4.2 决定二：为什么是 sealed 而不是普通接口](#42-决定二为什么是-sealed-而不是普通接口)
  - [4.3 决定三：为什么 children() 放在节点上](#43-决定三为什么-children-放在节点上)
  - [4.4 决定四：为什么树需要三种节点](#44-决定四为什么树需要三种节点)
  - [4.5 决定五：为什么 Workflow 必须 implements Iterable](#45-决定五为什么-workflow-必须-implements-iterable)
  - [4.6 决定六：为什么用 TraversalOrder 枚举](#46-决定六为什么用-traversalorder-枚举)
  - [4.7 决定七：为什么 DFS 用显式栈而不是递归](#47-决定七为什么-dfs-用显式栈而不是递归)
  - [4.8 决定八：为什么压栈要反序](#48-决定八为什么压栈要反序)
  - [4.9 决定九：为什么 modCount 放在 Workflow 里](#49-决定九为什么-modcount-放在-workflow-里)
  - [4.10 决定十：为什么 FilteringIterator 必须预读](#410-决定十为什么-filteringiterator-必须预读)
  - [4.11 决定十一：为什么 Client 里保留递归打印](#411-决定十一为什么-client-里保留递归打印)
  - [4.12 十一个决定汇总](#412-十一个决定汇总)

**第三部分：代码是怎么写的**

- [5. 代码解析：11 个文件逐一](#5-代码解析11-个文件逐一)
  - [5.1 StepType：类型是数据，不是名字](#51-steptype类型是数据不是名字)
  - [5.2 WorkflowNode：三种节点共用的唯一契约](#52-workflownode三种节点共用的唯一契约)
  - [5.3 StepNode：叶子节点](#53-stepnode叶子节点)
  - [5.4 BranchNode：二选一](#54-branchnode二选一)
  - [5.5 SequenceNode：依次执行](#55-sequencenode依次执行)
  - [5.6 TraversalOrder：顺序是一个值](#56-traversalorder顺序是一个值)
  - [5.7 Workflow：容器只提供结构，不提供顺序](#57-workflow容器只提供结构不提供顺序)
  - [5.8 DepthFirstIterator：显式栈与反序压栈](#58-depthfirstiterator显式栈与反序压栈)
  - [5.9 BreadthFirstIterator：只换了一个数据结构](#59-breadthfirstiterator只换了一个数据结构)
  - [5.10 FilteringIterator：不生产节点，只转发节点](#510-filteringiterator不生产节点只转发节点)
  - [5.11 Client：六段 Demo 的组织方式](#511-client六段-demo-的组织方式)

**第四部分：跑起来是什么样**

- [6. 执行流程：真实输出逐行讲](#6-执行流程真实输出逐行讲)
  - [6.1 Demo 1：一棵树与两个游标](#61-demo-1一棵树与两个游标)
  - [6.2 Demo 2：同一棵树，两种顺序](#62-demo-2同一棵树两种顺序)
  - [6.3 Demo 3：printAll 只认 Iterable](#63-demo-3printall-只认-iterable)
  - [6.4 Demo 4：过滤迭代器](#64-demo-4过滤迭代器)
  - [6.5 Demo 5：遍历途中改结构](#65-demo-5遍历途中改结构)
  - [6.6 Demo 6：到底了还取](#66-demo-6到底了还取)
  - [6.7 一次 next() 的完整时序](#67-一次-next-的完整时序)

**第五部分：还能怎么改，以及别踩哪些坑**

- [7. 扩展对比：加需求时改哪几行](#7-扩展对比加需求时改哪几行)
  - [7.1 新增第四种节点类型：并行会签](#71-新增第四种节点类型并行会签)
  - [7.2 新增第三种遍历顺序：后序遍历](#72-新增第三种遍历顺序后序遍历)
  - [7.3 新增一个倒序迭代器](#73-新增一个倒序迭代器)
  - [7.4 让遍历支持并发](#74-让遍历支持并发)
  - [7.5 把树换成图](#75-把树换成图)
  - [7.6 用 Stream 代替手写遍历](#76-用-stream-代替手写遍历)
  - [7.7 递归汇总：这个需求不该用迭代器](#77-递归汇总这个需求不该用迭代器)
  - [7.8 与访问者模式的边界：加第六种导出格式](#78-与访问者模式的边界加第六种导出格式)

- [8. 优缺点与误区](#8-优缺点与误区)
  - [8.1 什么场景该用](#81-什么场景该用)
  - [8.2 设计代价](#82-设计代价)
  - [8.3 九个常见误解](#83-九个常见误解)
  - [8.4 与相似模式的对比](#84-与相似模式的对比)

- [9. 面试与自测](#9-面试与自测)
  - [9.1 十道面试题](#91-十道面试题)
  - [9.2 自测清单](#92-自测清单)
  - [9.3 动手题](#93-动手题)
  - [9.4 参考答案要点](#94-参考答案要点)

**附录**

- [附录 A：文件清单与行数](#附录-a文件清单与行数)
- [附录 B：编译与运行命令](#附录-b编译与运行命令)
- [附录 C：原项目 vs 本项目逐项对照](#附录-c原项目-vs-本项目逐项对照)
- [附录 D：探针证据全表](#附录-d探针证据全表)
- [附录 E：关键术语](#附录-e关键术语)
- [附录 F：参考资料](#附录-f参考资料)

---

# 1. 现实案例：一张报销单，卡在了哪一步

## 1.1 一个真实的上午

周一上午十点，小李提交了一张差旅报销单：出差三天，住宿 980 元，高铁 320 元，合计 1300 元。

十分钟后，财务主管在系统里看到一条待办。他点开、翻了翻发票、点了通过。系统自动给小李发了一条站内信：「您的报销单已通过」。

下午两点，小李又提交了一张：市内打车 46 元。

这次接单的是小李的直属主管，不是财务主管。

同样是报销，两个人的单子走了两条不同的路。区别只有一个数字：**金额**。

这就是几乎所有公司都真实存在的规则：

```
金额 ≤ 1000  →  直属主管审批 → 通知申请人
金额 > 1000  →  财务总监审批 → 通知财务 + 通知申请人
```

现在的问题是：这个流程在系统里，应该用一个什么数据结构来装？

## 1.2 主管的第一个问题：这张单子走到哪了

上线第一周，财务主管提了一个需求：

> 我想在单据详情页上看到一条时间线 —— 这个流程一共分几步、现在走到第几步、后面还有哪几步。

产品经理把它翻译成技术语言：

> 给我一个能"按顺序把流程里的所有节点列出来"的能力。

这个需求听起来平平无奇，它是所有"遍历"需求的最小形态。

## 1.3 第一版：一条线，一个 for 循环

假设流程真的只是一条线（不看金额，所有人走同一条路）：

```
提交 → 主管审批 → 通知申请人
```

那实现简单到不需要任何设计：

```java
String[] steps = {"提交", "主管审批", "通知申请人"};
for (int i = 0; i < steps.length; i++) {
    System.out.println("第 " + (i + 1) + " 步：" + steps[i]);
}
```

**请注意这一节的存在。** 它要说明一件非常重要、但经常被模式教材跳过的事：

> 当容器是一维数组、而调用方又拿着这个数组的引用时，
> **根本不需要迭代器模式。**

`for (int i = 0; i < array.length; i++)` 已经是最优解：零对象分配、零间接调用、编译器能做的优化全都能做。

引入迭代器只会让它变慢，并且多出两个类。

所以"为什么要用迭代器"这个问题，真正的答案从来不是"因为它是设计模式"，
而是：**下面这三个前提开始不成立了。**

## 1.4 需求变了：金额超过 1000 要走总监

一个月后，风控部门要求加一条规则：

> 单笔超过 1000 元的报销，必须走财务总监；否则走直属主管。

流程变成了：

```
提交
  └─ 金额 > 1000 ？
       ├─ 是 → 财务总监审批 → 通知财务 → 通知申请人
       └─ 否 → 直属主管审批 → 通知申请人
```

这条规则本身很好写：

```java
if (amount > 1000) {
    director.approve(order);
} else {
    manager.approve(order);
}
```

但"列出所有步骤"这个需求立刻崩了。

## 1.5 第二版：把分叉硬塞进数组

面对一棵树，最省事的做法是什么？

**把它拍平。**

```java
ProcessNode[] nodes = new ProcessNode[5];
nodes[0] = new ProcessNode("开始");
nodes[1] = new ProcessNode("审批");
nodes[2] = new ProcessNode("通知");
nodes[3] = new ProcessNode("结束");
```

这就是原项目的做法 —— 而且它还留了个 `capacity` 参数：

```java
public Workflow(int capacity) {
    nodes = new ProcessNode[capacity];
    size = 0;
}
```

于是"流程"这个词在这里名不副实了：数组里那一串节点，**没有父子关系、没有分支、没有先后之外的任何信息**。

它表达的不是一棵树，而是一张清单。

真实的时间线应该是这样：

```
开始
 └─ 金额 > 1000 ？
      ├─ 是 → 财务总监审批 → 通知财务
      └─ 否 → 直属主管审批 → 通知申请人
```

但数组只能吐出：

```
开始
审批
通知
结束
```

调用的那个 `if` 去哪了？在业务代码里。也就是说：**流程的结构一部分在数据里，一部分在 `if` 语句里。**
任何时候想知道"这个流程长什么样"，都得把代码读一遍。

这就是为什么"加一个节点"这种看起来最小的需求，在原项目里也要改 Java 代码、重新发布。

## 1.6 第三个需求：换一个角度看流程

上线三个月，又来了两个需求：

**需求 A（财务）**：「我要按层级看。第一层是判断，第二层是各个审批线，第三层才是具体的人。」
→ 这是**广度优先**。

**需求 B（审计）**：「我只要所有需要人签字的环节，别给我看通知、开始、结束。」
→ 这是**在遍历上加过滤**。

**需求 C（流程设计器）**：「我要在一张画布上把整棵树画出来，边画边算坐标。」
→ 这是**递归**（一次画完，不需要暂停）。

三个需求，三种完全不同的"走法"。而它们面对的是**同一棵树**。

到这一步，问题的形状终于清楚了：

> 问题不在容器，也不在数据。
> 问题是：**"怎么走"这件事，被焊死在了容器里。**

原项目的 `WorkflowIterator` 是一个 `private class`，写在 `Workflow` 内部，`index++` 写死在 `next()` 里。
想加一种走法，只能改容器；想加一个过滤条件，也只能改容器。

## 1.7 归纳：迭代器模式的三个承诺

把上面的故事压缩成三句话，就是迭代器模式要交付的三件事：

### 承诺一：容器只承诺"能被遍历"，不暴露"怎么存"

调用方拿到 `Iterable<WorkflowNode>` 时，它不知道：

- 元素存在数组里、链表里、还是树里；
- 有没有父子关系；
- 一共有几层。

它只知道一件事：**我可以一个一个地取。**

这条承诺的收益是反向的：**容器可以换实现而不影响调用方。**
今天用树，明天换成图、换成从数据库流式读取，`printAll` 那一行代码不用改。

### 承诺二：遍历算法独立于容器，因此可以有很多种

同一棵树，深度优先和广度优先的差别**只有数据结构不同**：

| | 深度优先 | 广度优先 |
|---|---|---|
| 结构 | 栈（后进先出） | 队列（先进先出） |
| 取出 | `pop()` | `pollFirst()` |
| 装孩子 | 反序 `push()` | 正序 `addLast()` |

容器一个字符都不用改。

### 承诺三：遍历进度是独立对象，因此可以有多个、可以暂停

```java
Iterator<WorkflowNode> first = flow.iterator();
Iterator<WorkflowNode> second = flow.iterator();
```

两个游标各走各的。想停就停、想再开一个就再开一个。

对比 `for (int i = 0; ...)`：`i` 是**方法栈里的局部变量**，
方法一返回它就没了，所以"暂停遍历、明天接着走"这件事根本做不到。

**这就是 1.3 节那个 `for` 循环和迭代器的本质区别：**
不是"能不能遍历"，而是"进度能不能被当成一个东西拿在手上"。

---

# 2. 原项目：109 行与八个问题

## 2.1 四个文件

原项目在 `src/BehaviorType/IteratorPattern/` 下，一共四个文件、109 物理行、61 代码行：

| 文件 | 物理行 | 代码行 | 职责 |
|---|---:|---:|---|
| `Iterator.java` | 15 | 5 | 迭代器接口：`hasNext()` / `next()` |
| `ProcessNode.java` | 16 | 7 | 节点：`record ProcessNode(String name)` |
| `Workflow.java` | 52 | 34 | 容器：数组 + 内部类迭代器 |
| `Client.java` | 26 | 15 | 演示入口 |
| **合计** | **109** | **61** | |

先看完整源码，后面每一条批评都会落在具体的行上。

### Iterator.java（15 行）

```java
package BehaviorType.IteratorPattern;

/**
 * @ClassName: Iterator
 * @Package: BehaviorType.IteratorPattern
 * @Description:
 * @Author HGL
 * @Create: 2025/9/11 17:16
 */
interface Iterator {
    boolean hasNext();  // 判断是否还有下一个元素

    Object next();      // 获取下一个元素
}
```

### ProcessNode.java（16 行）

```java
package BehaviorType.IteratorPattern;

/**
 * @ClassName: ProcessNode
 * @Package: BehaviorType.IteratorPattern
 * @Description: 定义节点类：表示流程中的一个节点
 * @Author HGL
 * @Create: 2025/9/11 17:15
 */
record ProcessNode(String name) {
    @Override
    public String toString() {
        return "ProcessNode{name='" + name + "'}";
    }
}
```

### Workflow.java（52 行）

```java
package BehaviorType.IteratorPattern;

/**
 * @ClassName: Workflow
 * @Package: BehaviorType.IteratorPattern
 * @Description: 定义具体的流程类：实现节点集合并返回迭代器
 * @Author HGL
 * @Create: 2025/9/11 17:17
 */
class Workflow {
    private final ProcessNode[] nodes;
    private int size;

    public Workflow(int capacity) {
        nodes = new ProcessNode[capacity];
        size = 0;
    }

    // 添加节点到流程中
    public void addNode(ProcessNode node) {
        if (size < nodes.length) {
            nodes[size++] = node;
        }
    }

    // 返回流程的迭代器
    public Iterator iterator() {
        return new WorkflowIterator();
    }

    // 具体的迭代器实现类
    private class WorkflowIterator implements Iterator {
        private int index;

        public WorkflowIterator() {
            index = 0;
        }

        @Override
        public boolean hasNext() {
            return index < size;
        }

        @Override
        public Object next() {
            if (hasNext()) {
                return nodes[index++];
            }
            return null;
        }
    }
}
```

### Client.java（26 行）

```java
package BehaviorType.IteratorPattern;

/**
 * @ClassName: Client
 * @Package: BehaviorType.IteratorPattern
 * @Description:
 * @Author HGL
 * @Create: 2025/9/11 17:18
 */
public class Client {
    public static void main(String[] args) {
        Workflow workflow = new Workflow(5);
        workflow.addNode(new ProcessNode("开始"));
        workflow.addNode(new ProcessNode("审批"));
        workflow.addNode(new ProcessNode("通知"));
        workflow.addNode(new ProcessNode("结束"));

        // 获取迭代器进行遍历
        Iterator iterator = workflow.iterator();
        while (iterator.hasNext()) {
            ProcessNode node = (ProcessNode) iterator.next();
            System.out.println("Executing node: " + node);
        }
    }
}
```

### 先说做得对的地方

在逐条挑问题之前，必须承认这四段代码**把迭代器模式的核心骨架写对了**：

| 做对的地方 | 体现在哪 |
|---|---|
| 有独立的迭代器接口 | `Iterator` 有 `hasNext()` / `next()` 两个方法 |
| 容器不暴露内部数组 | `nodes` 是 `private final`，外部拿不到 |
| 遍历逻辑搬出了容器主体 | `WorkflowIterator` 持有 `index`，容器自己不记"走到哪了" |
| 每次 `iterator()` 返回新实例 | 见 `Workflow.java:27-29`，所以两次遍历互不干扰 |

这四条是模式的骨架。骨架对了，剩下的都是骨架之外的东西：
**类型安全、契约、边界、可扩展性**。

而恰恰是这些东西，决定了这段代码在生产环境里能不能活下来。

## 2.2 原项目的真实输出

编译运行原项目（JDK 25，`-Xlint:all` 零告警）：

```
Executing node: ProcessNode{name='开始'}
Executing node: ProcessNode{name='审批'}
Executing node: ProcessNode{name='通知'}
Executing node: ProcessNode{name='结束'}
```

4 行，干干净净。

**这段输出什么都没暴露问题。** 这正是最危险的地方：
原项目的所有缺陷，在这 4 行里一个都看不出来。

原因很简单 —— `Client` 是一个"完美调用方"：

- 它永远先问 `hasNext()` 再 `next()`；
- 它强转的类型永远是对的；
- 它建的数组容量永远够大；
- 它从不在遍历途中改结构。

真实代码里，这四条几乎不可能同时成立。

下面八节，每一条都配一个**探针程序**（不属于交付代码，跑完即删），  
把原项目那 4 行输出照不到的角落一个个点亮。

## 2.3 问题一：Workflow 没有实现 Iterable

### 现象

打开 `Workflow.java`，第 10 行是：

```java
class Workflow {
```

没有 `implements Iterable<ProcessNode>`。

看起来只是"少继承一个接口"，实际后果是一条**编译错误**。

### 证据

写一段再普通不过的代码：

```java
Workflow wf = new Workflow(1);
wf.addNode(new ProcessNode("开始"));
for (ProcessNode node : wf) {          // ← 第 15 行
    System.out.println(node);
}
```

用 JDK 25 编译它，javac 直接拒绝：

```
错误: for-each 不适用于表达式类型
        for (ProcessNode node : wf) {
                                ^
  要求: 数组或 java.lang.Iterable
  找到:    Workflow
1 个错误
```

**javac 的原话就是最准确的解释**：增强 `for` 只接受两种东西 ——
数组，或者 `java.lang.Iterable`。

### 为什么这不是小事

#### 后果一：所有调用方都得手写 while

原项目的 `Client` 只能这样写：

```java
Iterator iterator = workflow.iterator();
while (iterator.hasNext()) {
    ProcessNode node = (ProcessNode) iterator.next();
    System.out.println("Executing node: " + node);
}
```

三行样板。业务代码里每遍历一次就要写一遍。

#### 后果二：拿不到 Iterable 自带的一整套默认方法

`java.lang.Iterable` 从 Java 8 起提供了：

| 方法 | 能干什么 |
|---|---|
| `forEach(Consumer)` | 一行写完遍历 |
| `spliterator()` | 变成 `Stream`、支持并行 |
| （JDK 之外的 Guava / StreamEx 等） | 大量以 `Iterable` 为入口的工具 |

没有实现 `Iterable`，这些全部与你无关。

具体说：**你不能把一棵审批流变成流。**

```java
// 有了 Iterable，这一行才成立
StreamSupport.stream(workflow.spliterator(), false)
        .filter(node -> node instanceof StepNode s && s.type() == StepType.APPROVAL)
        .map(WorkflowNode::name)
        .forEach(System.out::println);
```

而 `spliterator()` 是 `Iterable` 的**默认方法**，它内部就是拿 `iterator()` 实现的。
也就是说：**你明明已经写了 `iterator()`，却因为没声明一个 `implements`，白丢了整条生态。**

#### 后果三：库函数收不进去

任何签名是 `void foo(Iterable<T> source)` 的库方法都无法接受一个 `Workflow`。

本项目 `Client` 里的 `printAll` 就是这样一个方法：

```java
private static void printAll(String title, Iterable<WorkflowNode> source) {
    ...
}
```

它既接受一棵树，也接受一个 `List`：

```
  传入 Workflow（一棵树）：
    1. 顺序块「报销主流程」(2 步)
    ...
    共 9 个节点

  传入 List.of(...)（一个扁平列表）：
    1. 审批节点「临时加签」
    2. 通知节点「临时抄送」
    共 2 个节点
```

原项目的 `Workflow` 一个都传不进去。

### 一句话总结

> **`iterator()` 是你送给别人的能力，`implements Iterable` 是你对外的承诺。**
> 没有承诺，编译器就不认，别的库也不认。

## 2.4 问题二：next() 返回 Object

### 现象

`Iterator.java:13`：

```java
Object next();      // 获取下一个元素
```

`Workflow.java:45-50`：

```java
@Override
public Object next() {
    if (hasNext()) {
        return nodes[index++];
    }
    return null;
}
```

容器里明明存的是 `ProcessNode[]`，接口却声明成 `Object`。

### 后果：强转责任被推给了每一个调用方

`Client.java` 里必须写：

```java
ProcessNode node = (ProcessNode) iterator.next();
//                 ^^^^^^^^^^^^^^^^ 每个调用方都要写，写错了编译器不管
```

### 证据：类型错了，编译通过，运行时才炸

探针 1 干了一件"手滑"的事 —— 把节点转成了 `Integer`：

```java
Object raw = it.next();
Integer wrong = (Integer) raw;      // 编译通过，零警告
```

运行结果：

```
=== 探针 1：next() 声明返回 Object，转错类型要到运行时才爆 ===
  next() 的声明类型是 Object，实际装的是 BehaviorType.IteratorPattern.ProcessNode
  强转成 Integer -> java.lang.ClassCastException
  编译器一条警告都没给：它只知道返回 Object，不知道里面装的是什么
```

注意第一行：**实际装的是 `ProcessNode`**。
编译器和运行时的信息差，就在这里。

### 泛型化之后，这一行根本写不出来

把接口改成 `Iterator<T>` 之后：

```java
Iterator<WorkflowNode> it = flow.iterator();
Integer wrong = (Integer) it.next();     // ← 编译错误，无法通过
```

javac 会直接报"不兼容的类型"。**错误从运行时前移到了编译期。**

### 这不是"风格问题"，是 Java 5 就已经解决的老问题

`java.util.Iterator` 在 Java 1.2 时也是 `Object next()`。
Java 5 引入泛型后立刻改成 `E next()`。

原项目要么是照着 1999 年的教材抄的，要么是没用泛型。

### 补充：为什么原项目"看起来也没出过事"

因为 `Client` 里只有一处调用，而且转对了。

**一个强转不出错，不代表这个设计没问题 ——
它只是说明调用点还很少。**

等工作流引擎被十个业务模块使用，每个模块都写一遍 `(ProcessNode) next()`，
就一定会有人在某个地方写错，而那时错误会出现在离出错点很远的地方。

## 2.5 问题三：next() 到底返回 null

### 现象

`Workflow.java:45-50`：

```java
@Override
public Object next() {
    if (hasNext()) {
        return nodes[index++];
    }
    return null;                 // ← 到底了，返回 null
}
```

### 证据

探针 2 连续调了两次 `next()`（数组里只有一个节点）：

```
=== 探针 2：next() 到底返回 null，异常被推迟到使用处 ===
  第 1 次 next()：ProcessNode{name='结束'}
  hasNext() = false
  第 2 次 next()：null
  对返回值取 name() -> NullPointerException
  栈顶是「(ProcessNode) extra」那一行，而不是「多调了一次 next()」那一行
```

### 这个设计的三个层次的问题

#### 第一层：返回值有两种含义，调用方分不清

`next()` 返回 `null` 时，调用方无法区分：

- "已经到底了" → 这是迭代器的责任；
- "这里真的有个元素，它的值是 `null`" → 这是数据的责任。

一个返回值承载两种含义，这是最经典的 API 设计错误。

（虽然本项目的 `ProcessNode` 不会是 null，但接口 `Iterator` 是通用的，它没有这个保证。）

#### 第二层：错误被推迟了

真正出错的地方是**"多调了一次 next()"**。

但异常抛在**"用了这个 null"**的地方。

```java
Object extra = it.next();                  // ← 真正的错处，静悄悄地返回 null
ProcessNode node = (ProcessNode) extra;    // 还是静悄悄
node.name();                               // ← 这里才炸

// 栈顶显示：Probe.probe2NullAtEnd(...) 这一行
// 排查的人第一反应是"node 怎么会是 null"，而不是"我多取了"
```

这在生产环境里意味着：**你要沿着调用链往上找好几层，才能找到那个本该报错的 `next()`。**

#### 第三层：和 JDK 的契约不一致

`java.util.Iterator#next()` 的 javadoc 写得很清楚：

> **Throws:** `NoSuchElementException` - if the iteration has no more elements

原项目实现了一个叫 `Iterator` 的接口，却违反了 JDK 同名接口的契约。

后果是：**任何熟悉 JDK 的人接手这段代码，都会按 JDK 的约定去理解它，然后踩坑。**

### 正确做法：抛异常

本项目的做法：

```java
@Override
public WorkflowNode next() {
    workflow.checkForComodification(expectedModCount);

    if (stack.isEmpty()) {
        throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点");
    }
    ...
}
```

运行结果（Demo 6）：

```
  next() 抛出：NoSuchElementException
  消息：深度优先遍历已经到底，没有下一个节点
```

### 一个常见的反驳

> "抛异常多暴力，返回 null 更温和。"

**错。** 返回 null 不是温和，是**把崩溃挪到更难排查的地方**。

- 抛 `NoSuchElementException`：崩在 `next()` 那一行，异常类型本身就是答案；
- 返回 `null`：崩在十行之外，`NullPointerException` 只能说"有个东西是 null"。

**"温和"的代价，永远由排查问题的人支付。**

## 2.6 问题四：容量满了静默丢弃

### 现象

`Workflow.java:20-24`：

```java
public void addNode(ProcessNode node) {
    if (size < nodes.length) {
        nodes[size++] = node;
    }
}
```

`if` 没有 `else`。数组满了，**什么也不做，直接返回。**

### 证据

探针 3：`capacity = 2`，加 4 个节点：

```
=== 探针 3：容量满了静默丢弃 ===
  capacity = 2，addNode 一共调了 4 次
  实际遍历到 2 个节点
  第 3、4 个既没进去，也没有任何提示：不抛异常、不返回 false、不打日志
```

### 为什么这是最危险的一个问题

前面三个问题都发生在"用错"的时候。这个问题发生在**"用对了"**的时候。

调用方完全按文档使用：`new Workflow(5)` 然后 `addNode(...)`。
它不知道容量是 5，也不知道加第 6 个会怎样 ——
因为**方法签名是 `void`，没有任何信息能传回来**。

三种常见写法，一个都没抓到：

| 可能的防线 | 原项目的情况 |
|---|---|
| 返回值检查 | `void`，没得返回 |
| 异常 | 不抛 |
| 日志 | 不打 |

### 真实世界长什么样

假设运营配置了一个 8 步的审批流程，代码里写的是 `new Workflow(5)`：

- 测试环境：测试数据只有 3 步 → 通过；
- 生产环境：正式流程 8 步 → 后 3 步（大概率包含「结束」）消失。

**用户看到的现象是"流程走完了但没通知"，排查的人会去查消息队列、查网络、查数据库。**

没人会想到是构造 `Workflow` 时那个 `5`。

### 本项目的做法

不用数组，用 `ArrayList`：

```java
private final List<WorkflowNode> roots = new ArrayList<>();
```

`add` 永远成功，没有容量上限。

如果将来真的有容量限制（比如单流程最多 200 节点），那也必须**显式失败**：

```java
public Workflow add(WorkflowNode node) {
    if (roots.size() >= MAX_NODES) {
        throw new IllegalStateException("单个审批流最多 " + MAX_NODES + " 个节点");
    }
    ...
}
```

**核心原则：容量限制可以是业务规则，但不能是静默的意外。**

## 2.7 问题五：没有快照，也没有 fail-fast

### 现象

`WorkflowIterator` 是 `Workflow` 的**非静态内部类**（`Workflow.java:32`），
它读的 `size` 是外部类的**实时字段**：

```java
private class WorkflowIterator implements Iterator {
    private int index;

    @Override
    public boolean hasNext() {
        return index < size;        // ← size 是 Workflow 的字段，实时变化
    }

    @Override
    public Object next() {
        if (hasNext()) {
            return nodes[index++];  // ← nodes 也是外部类的字段
        }
        return null;
    }
}
```

于是遍历途中的任何修改，产生的结果取决于**巧合**。

### 证据

探针 4：取一个、加一个、再继续取：

```
=== 探针 4：遍历中加节点，既没快照也没 fail-fast ===
  先取一个：ProcessNode{name='A'}
  addNode(C) 之后继续遍历：
    ProcessNode{name='B'}
    ProcessNode{name='C'}
  没有 ConcurrentModificationException
  C 居然也出现了：内部类读的是外部类实时的 size 字段，不是快照
  反过来，如果数组刚好在遍历途中被填满，新增节点就是静默看不到
```

### 三种可能的设计，原项目一个都没选

| 设计 | 遍历中新增节点时 | 优点 | 缺点 |
|---|---|---|---|
| **快照** | 新增的看不到 | 结果确定、可复现 | 要拷贝，有内存开销 |
| **fail-fast** | 立刻抛 `ConcurrentModificationException` | 错误立刻暴露 | 需要 `modCount` |
| **原项目的做法**（实时读） | **看情况** | 无 | 结果依赖执行时序 |

第三种最糟，因为它**不可复现**：

- 数组还没满 → 新节点会被这次遍历取到（探针 4 的情况）；
- 数组刚好满了 → 新节点被 `addNode` 丢弃，遍历取不到，还不报错。

同一份代码，同一个循环，换一份数据就换一种行为。

### 为什么"不可复现"比"抛异常"更糟

生产事故排查的第一原则是**复现**。

- fail-fast 的方案：异常栈直接告诉你"遍历时有人改了结构"，改掉就行；
- 原项目：日志里遍历结果少了两个节点，你无法判断是
  "数据本来就只有这些" 还是 "遍历漏了" 还是 "有人中途加了节点但没进来"。

**一个确定的错误，远好过一个不确定的正确。**

### 本项目的做法：快照 + fail-fast，两个都要

```java
public DispatchReport publish(Event event) { ... }        // 观察者模式里的写法
```

迭代器模式这里的写法分两层：

**第一层：构造时快照**

```java
public DepthFirstIterator(Workflow workflow) {
    this.workflow = Objects.requireNonNull(workflow, "审批流不能为 null");
    this.expectedModCount = workflow.modCount();

    List<WorkflowNode> roots = workflow.roots();     // ← List.copyOf，真拷贝
    for (int i = roots.size() - 1; i >= 0; i--) {
        stack.push(roots.get(i));
    }
}
```

`roots()` 返回 `List.copyOf(roots)`，从这一刻起，迭代器手里的根节点列表和
`Workflow` 内部那个列表**没有任何关系**。

**第二层：每次 next() 对账**

```java
void checkForComodification(int expectedModCount) {
    if (modCount != expectedModCount) {
        throw new ConcurrentModificationException(
                "遍历期间审批流被修改：迭代器创建时修改了 "
                        + expectedModCount + " 次，现在是 " + modCount + " 次");
    }
}
```

运行结果（Demo 5）：

```
[Demo 5：遍历途中改结构，立刻抛 ConcurrentModificationException]
  取了第 1 个：开始节点「开始」
  hasNext() = true（栈里还压着「审批」）
  现在往审批流里追加一个根节点……
  hasNext() 仍然返回 true —— 它只查栈，不查 modCount
  next() 抛出：ConcurrentModificationException
  消息：遍历期间审批流被修改：迭代器创建时修改了 2 次，现在是 3 次
  新加的节点不会出现在这次遍历里 —— 遍历范围在创建迭代器那一刻就固定了
```

**注意异常消息里带了两个数字**：`2` 和 `3`。

这不是炫技。排查时这两个数字直接告诉你：**有几次修改、发生在哪里**。
而 JDK 的 `ConcurrentModificationException` 默认是不带消息的，你只能看到一个空异常。

### 为什么同时要两层

- 只有快照、没有 fail-fast：遍历会安静地漏掉新节点，你还是不知道为什么；
- 只有 fail-fast、没有快照：检查通过之后，`roots()` 里的内容仍可能被外部改动。

两层加起来才是完整的：**范围固定下来，改动立刻报错。**

## 2.8 问题六：非静态内部类持有外部引用

### 现象

`Workflow.java:32`：

```java
private class WorkflowIterator implements Iterator {
```

注意：**没有 `static`**。

### 证据

探针 6 用反射把迭代器的字段全列出来：

```
=== 探针 6：迭代器是非静态内部类，隐式持有外部 Workflow 引用 ===
  迭代器实际类型：BehaviorType.IteratorPattern.Workflow$WorkflowIterator
    字段 index : int
    字段 this$0 : BehaviorType.IteratorPattern.Workflow
  this$0 是编译器自动加的：迭代器只要还活着，整个 Workflow 和它的数组都回收不掉
```

源码里只声明了一个 `index`，反射却看到两个字段。

多出来的 `this$0` 是 javac 自动生成的 —— **非静态内部类的每一个实例，都强引用着它的外部类实例。**

### 什么情况下真的会出问题

绝大多数情况下，这没关系：迭代器和容器同生共死，一起被回收。

真正出问题的场景是这样的：

```java
// 某个缓存里存了一批"遍历进度"
Map<String, Iterator> pausedCursors = new HashMap<>();

// 开始遍历一个 100 万节点的流程
Workflow huge = loadHugeWorkflow();
Iterator it = huge.iterator();

// 存起来（比如为了做分页）
pausedCursors.put("job-1", it);

// huge 已经用不到了，但回收不掉：
// it.this$0 → huge → ProcessNode[1000000]
```

**只要还有一个"暂停的游标"活着，整个容器就活着。**

在分页、断点续跑、异步任务这些场景里，游标被长期持有是常态。

### 为什么静态内部类 / 顶层类就没事

因为静态内部类和顶层类都**不持有外部类实例**。

本项目的做法：把迭代器全部做成**顶层类**：

```
DepthFirstIterator.java     ← 顶层类，独立文件
BreadthFirstIterator.java   ← 顶层类，独立文件
FilteringIterator.java      ← 顶层类，独立文件
```

它们只持有自己需要的东西：

```java
public final class DepthFirstIterator implements Iterator<WorkflowNode> {
    private final Deque<WorkflowNode> stack = new ArrayDeque<>();
    private final Workflow workflow;          // ← 只为了调用 modCount()，不是隐式的
    private final int expectedModCount;
}
```

注意 `workflow` 这个字段是**显式声明的**。

这不只是"少了一个字段"的问题，而是**意图问题**：

| | 原项目 | 本项目 |
|---|---|---|
| 为什么持有容器 | javac 自动加的，作者可能都不知道 | 作者显式写的，因为要用 `modCount()` |
| 能不能去掉 | 不能（除非改成 static） | 能（把 `modCount` 通过构造器传进来就行） |

**显式的依赖可以被审视、被质疑、被替换；隐式的依赖只能被踩到。**

### 顺带一提：`private class` 的另一个副作用

`WorkflowIterator` 是 `private` 的，加上它是非静态内部类，
意味着它**只能被 `Workflow` 自己创建和使用**。

想给这个迭代器加个装饰器？做不到 —— 外部连这个类型都看不到。

本项目的 `FilteringIterator` 之所以能包装任意迭代器，前提就是：
**迭代器的类型是公开的，且它只依赖 `Iterator<WorkflowNode>` 这个接口。**

## 2.9 问题七：类型只能靠名字猜

### 现象

`ProcessNode.java:10`：

```java
record ProcessNode(String name) {
```

整个节点只有一个字段：名字。

于是"这是一个审批节点还是通知节点"，只能靠名字字符串判断。

### 证据

探针 5 用最自然的方式写了一次过滤：

```java
for (ProcessNode node : nodes) {
    if (node.name().contains("审批")) {
        approvals.add(node.name());
    }
}
```

三个节点：`总监审批`、`主管审批`、`总监确认`。

运行结果：

```
=== 探针 5：类型只能靠名字猜，改文案就静默失效 ===
  按 name().contains("审批") 过滤 -> [总监审批, 主管审批]
  「总监确认」明明也是审批节点，却被漏掉了
  改文案的人和写过滤的人，不是同一个人
```

### 为什么这个 bug 特别难发现

因为它**不会报错**。

- 没有异常；
- 没有告警；
- 测试大概率也是绿的（测试数据里没有「总监确认」）。

它的表现形式是：**审计报表里的审批环节少了一个。**

而发现它的路径通常是：某次审计抽查，人工数了一遍，发现对不上。

### 名字和类型是两种完全不同的东西

| | 名字 | 类型 |
|---|---|---|
| 谁维护 | 运营 / 产品 | 开发 |
| 什么时候变 | 改文案、做本地化、换个说法 | 极少数情况 |
| 变的时候谁知道 | 改动的人自己 | 编译器 + 所有测试 |
| 能不能枚举 | 不能（无限种） | 能（有限个） |
| 适合做什么 | 展示 | 判断 |

**把展示用的字段拿来做业务判断，等于把"文案改动"升级成"逻辑改动"。**

### 本项目的做法

```java
public enum StepType {
    START("开始"),
    APPROVAL("审批"),
    NOTIFY("通知"),
    END("结束");
    ...
}
```

节点带上类型：

```java
public record StepNode(String name, StepType type) implements WorkflowNode {
```

过滤变成：

```java
Predicate<WorkflowNode> onlyApproval = node ->
        node instanceof StepNode step && step.type() == StepType.APPROVAL;
```

三个好处：

1. **改名字不影响判断** —— `name` 随便改，`type` 不动；
2. **枚举值有限，可以 `switch` 穷尽检查** —— 少写一个分支编译器会提醒；
3. **编译期就能查错** —— `step.type() == StepType.APPROVAL` 拼错枚举常量会直接编译失败。

Demo 4 的运行结果：

```
[Demo 4：给迭代器再套一层，只取审批节点]
  在深度优先上过滤：
    1. 审批节点「总监审批」
    2. 审批节点「主管审批」
```

**注意：如果流程里有一个叫「总监确认」的节点，只要它的 `type` 是 `APPROVAL`，它就会被正确取出来。**

## 2.10 问题八：只有一种遍历顺序

### 现象

`Workflow.java:26-29`：

```java
public Iterator iterator() {
    return new WorkflowIterator();
}
```

`WorkflowIterator` 就是"从 0 扫到 size-1"。

**整个容器只有这一种走法，而且这种走法写死在容器内部。**

### 为什么这是"设计"层面的问题

前七节讲的都是**实现缺陷**：改几行就能修。

这一条不一样，它是**结构问题**：

```
Workflow.java
  ├─ ProcessNode[] nodes        ← 数据
  ├─ addNode()                  ← 修改
  ├─ iterator()                 ← 唯一的入口
  └─ private class WorkflowIterator
        └─ index++ 每次都 +1     ← 顺序焊死在这里
```

想加"广度优先"，必须：

1. 打开 `Workflow.java`；
2. 在里面再加一个 `private class WorkflowBfsIterator`；
3. 加一个 `iteratorBfs()` 方法。

而"广度优先"这个算法，和"审批流"这个业务概念，**没有任何关系**。
它属于算法，不属于业务。

### 本项目的做法

容器只回答"结构"，顺序交给迭代器：

```java
public Iterator<WorkflowNode> iterator(TraversalOrder order) {
    Objects.requireNonNull(order, "遍历顺序不能为 null");
    return switch (order) {
        case DEPTH_FIRST -> new DepthFirstIterator(this);
        case BREADTH_FIRST -> new BreadthFirstIterator(this);
    };
}
```

`Workflow.java` 里**没有一行遍历逻辑**。它只知道：
"有这两种顺序，对应的实现类分别是这两个"。

Demo 2 的输出证明了两者共存且互不干扰：

```
[Demo 2：同一棵树，两种顺序]
  深度优先 iterator(TraversalOrder.DEPTH_FIRST)：
    1. 顺序块「报销主流程」(2 步)
    2. 开始节点「提交报销单」
    3. 分支「金额是否超过 1000」条件：amount > 1000
    4. 顺序块「总监线」(2 步)
    5. 审批节点「总监审批」
    6. 通知节点「通知财务」
    7. 顺序块「主管线」(2 步)
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」

  广度优先 iterator(TraversalOrder.BREADTH_FIRST)：
    1. 顺序块「报销主流程」(2 步)
    2. 开始节点「提交报销单」
    3. 分支「金额是否超过 1000」条件：amount > 1000
    4. 顺序块「总监线」(2 步)
    5. 顺序块「主管线」(2 步)
    6. 审批节点「总监审批」
    7. 通知节点「通知财务」
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」
```

对比第 4、5 行：

- 深度优先：`总监线` 之后立刻进了 `总监审批`（一条路走到底）；
- 广度优先：`总监线` 之后先看同层的 `主管线`（一层一层展开）。

**同一次运行的同一棵树，两种完全不同的合法读法。**

### 一个容易忽略的细节

如果容器里有一维数组，那"遍历顺序"这件事**根本不存在选择** ——
数组天然只有"从前到后"。

所以"只有一种遍历顺序"往往不是"没需求"，而是
**"数据结构太平坦，压根没给第二种顺序留出想象空间"**。

一旦流程从数组变成了树，"按层看"和"按路径看"就立刻变成两个真实需求。

## 2.11 八个问题汇总

| # | 问题 | 类型 | 后果 | 证据 | 本项目修复 |
|---|---|---|---|---|---|
| 1 | 没实现 `Iterable` | 契约缺失 | 增强 for 编译失败；拿不到 `forEach` / `Stream` | javac 报错原文 | `implements Iterable<WorkflowNode>` |
| 2 | `next()` 返回 `Object` | 类型不安全 | 强转错误到运行时才爆 | 探针 1 | `Iterator<WorkflowNode>` |
| 3 | `next()` 到底返回 `null` | 违反契约 | 异常被推迟到使用处 | 探针 2 | 抛 `NoSuchElementException` |
| 4 | 容量满静默丢弃 | 静默失败 | 节点悄悄消失，无任何信号 | 探针 3 | `ArrayList`，无容量上限 |
| 5 | 无快照、无 fail-fast | 行为不确定 | 遍历结果随修改时机变化 | 探针 4 | `List.copyOf` + `modCount` |
| 6 | 非静态内部类 | 隐式依赖 | 游标持有容器，容器无法回收 | 探针 6 | 全部改成顶层类 |
| 7 | 类型靠名字猜 | 脆弱耦合 | 改名即静默失效 | 探针 5 | `StepType` 枚举 |
| 8 | 只有一种遍历顺序 | 结构僵化 | 加顺序要改容器 | Demo 2 对比 | `TraversalOrder` + 两个迭代器类 |

### 与六段 Demo 的对应

`Client` 里有六段 Demo，它们覆盖了上表中的 1、2（间接）、3、5、7、8：

| Demo | 主要证明 |
|---|---|
| Demo 1 | 增强 for 可用（问题 1）+ 两个独立游标 |
| Demo 2 | 两种遍历顺序共存（问题 8） |
| Demo 3 | `Iterable` 作为通用协议（问题 1 的延伸） |
| Demo 4 | 迭代器可再包装（问题 8 的延伸）+ 类型枚举（问题 7） |
| Demo 5 | fail-fast（问题 5） |
| Demo 6 | 到底抛异常（问题 3） |

问题 4 和问题 6 没有专门的 Demo，因为它们在新项目里**已经不存在了**：

- 没有容量参数，就没有"满了"这回事；
- 迭代器是顶层类，就没有 `this$0` 这回事。

**"某个问题没有对应演示"，有时是最好的结果。**

---

# 3. 设计动机：什么时候才真的需要迭代器

## 3.1 迭代器到底解决什么问题

把 1.7 节的三条承诺再抽象一层，迭代器模式解决的问题可以用一句话概括：

> **把"存"和"取"分成两件事，并且让"取"有无数种实现。**

这句话拆开是三个独立的收益，每一个都可以单独成立：

### 收益一：解耦容器与调用方

调用方不需要知道容器长什么样。

```java
void printAll(Iterable<WorkflowNode> source) {
    for (WorkflowNode node : source) {
        System.out.println(node);
    }
}
```

这个方法**完全不知道** `source` 是什么：

- 可能是 `Workflow`（一棵树）；
- 可能是 `List.of(...)`（一个扁平列表）；
- 可能是 `Set`、`Queue`、`Stream` 转出来的；
- 可能是别人写的别的什么类，只要实现了 `Iterable`。

Demo 3 的运行结果就是证据：

```
[Demo 3：遍历算法跟着 Iterable 走，不跟着 Workflow 走]
  传入 Workflow（一棵树）：
    1. 顺序块「报销主流程」(2 步)
    ...
    共 9 个节点

  传入 List.of(...)（一个扁平列表）：
    1. 审批节点「临时加签」
    2. 通知节点「临时抄送」
    共 2 个节点

  printAll 只声明了 Iterable<WorkflowNode>，
  它既不知道树有多深，也不知道元素存在数组还是链表里
```

### 收益二：让"遍历算法"成为一个可以被替换的东西

这是最容易讲漏的收益。

很多人以为迭代器的价值是"统一遍历写法"，其实它更大的价值在**算法**这一侧：

| 遍历算法 | 数据结构 | 适用场景 |
|---|---|---|
| 深度优先 | 栈 | 顺着一条业务路径往下看 |
| 广度优先 | 队列 | 按层级展开、找最短路径、分层渲染 |
| 过滤 | 预读一个 | 只看某类节点 |
| 倒序 | 栈 + 反序 | 从后往前核对 |
| 分页 | 游标 + 计数 | 大数据量、接口分页 |

**这些都是"遍历算法"，它们和数据长什么样无关。**

如果遍历写在容器里，上面每一行都要在容器里加一段代码。
如果遍历写在迭代器里，它们只是一个又一个独立的类。

### 收益三：遍历状态变成一等公民

"遍历到哪了"这件事，从**方法栈上的局部变量**变成了**堆上的对象**。

这个转变带来的能力：

| 能力 | `for (int i = 0; ...)` | 迭代器 |
|---|---|---|
| 遍历完 | 只能从头再来 | 可以复制游标、可以从中间继续 |
| 中途返回 | `i` 消失，进度丢失 | 把迭代器返回出去，进度还在 |
| 多个游标 | 要写两套 `i`、`j` | 两个迭代器对象，天然独立 |
| 传递给别人 | 做不到 | 作为参数、作为字段、存进 Map |
| 懒计算 | 做不到 | 每 `next()` 一次才算一个 |

Demo 1 的后半段演示了"多个游标"：

```
  同时开两个游标，各自记各自的进度：
    first  第 1 个：顺序块「报销主流程」(2 步)
    first  第 2 个：开始节点「提交报销单」
    second 第 1 个：顺序块「报销主流程」(2 步)
    first  第 3 个：分支「金额是否超过 1000」条件：amount > 1000
  如果 iterator() 返回同一个实例，上面的 second 会从 first 停下的地方继续
```

## 3.2 下标遍历的三个隐含假设

为什么 `for (int i = 0; i < nodes.length; i++)` 在树形结构上会失效？

因为它偷偷依赖了三个假设，而这三个假设**在树上全都不成立**。

### 假设一：元素可以用一个整数索引定位

```java
nodes[0], nodes[1], nodes[2], ...
```

数组的每个元素有一个**唯一且连续**的下标。

**树上不成立**：节点的"位置"是一条路径，不是一个下标。

```
报销主流程 → 分支 → 总监线 → 总监审批
```

你要说"第 5 个节点"，必须先确定走哪种顺序 —— 因为深度优先第 5 个是「总监审批」，
广度优先第 5 个是「主管线」。

**顺序不定，序号就没有意义。**

### 假设二：所有元素在同一层

```java
for (int i = 0; i < nodes.length; i++)
```

一遍 `i++` 扫完，默认所有元素平铺在一个水平面上。

**树上不成立**：节点有深度。`总监审批` 在第 3 层，`报销主流程` 在第 0 层。

要遍历一棵树，必须记住"我是从哪条路下来的"。

这正是 DFS 用**栈**的原因 —— 栈就是"我现在这条路"的备忘录。

### 假设三：遍历过程中容器不会变

```java
int size = nodes.length;
for (int i = 0; i < size; i++)
```

隐含假设是：`i` 和元素之间是稳定的对应关系。

**树在遍历中变化更难处理**：不是"多一个元素"这么简单，而是
"某个分支下面整棵子树被替换了"。

（本项目通过"树节点全部不可变 + 只允许往根列表加节点"来简化这件事，
所以 `modCount` 只需要记录根列表的修改次数。）

### 所以：结构越复杂，"遍历"就越值得单独抽象

| 结构 | 遍历难度 | 需要迭代器吗 |
|---|---|---|
| 一维数组 | 一个 `for` | 不需要 |
| `ArrayList` / `LinkedList` | 一个增强 `for` | JDK 已经有了，不需要自己写 |
| 树 / 图 | 要维护路径、要选顺序 | **需要** |
| 从数据库流式读 | 要维护连接、游标、批量 | **需要**（JDBC 的 `ResultSet` 就是迭代器） |

## 3.3 什么时候不需要迭代器

这一节比上一节更重要，因为**过度使用迭代器是真实存在的**。

### 情况一：容器是 `List` / `Set` / `Map` 且没有自定义遍历需求

```java
List<Order> orders = orderRepository.findByUser(userId);
for (Order order : orders) {
    total += order.amount();
}
```

**不要再包一层迭代器。** JDK 的 `ArrayList.Itr` 已经做过极致优化，
你自己写的任何迭代器都会更慢。

### 情况二：只有一种遍历方式，且永远不会变

如果容器是数组、只有一种顺序、没有过滤需求，那 `for` 循环就是最好的迭代器。

**模式是给变化留位置，没有变化就不需要位置。**

### 情况三：你只是想把 `for` 循环藏起来

```java
// 这不是迭代器模式，这只是给 for 循环起了个名字
public void printAll() {
    for (ProcessNode node : nodes) {
        System.out.println(node);
    }
}
```

**判断标准**：如果把这段遍历逻辑搬到一个独立的类里，它会持有和容器一样多的知识，
那它就不该被搬出去 —— 它本来就是容器的一部分。

### 情况四：用 Stream 更合适

```java
List<String> approvalNames = flow.stream()
        .filter(WorkflowNode::isLeaf)
        .map(WorkflowNode::name)
        .toList();
```

如果只需要"算一个结果"，`Stream` 比手写迭代器可读得多。

**本项目为什么还手写迭代器？**

因为这是**教学项目**，目的是把迭代器模式的机制摊开给你看。
真实项目里，如果 `Workflow` 实现了 `Iterable`，绝大多数调用方会直接用 `Stream`。

`Stream` 不会取代迭代器 —— **`Stream` 本身就是建立在 `spliterator()` 之上的，
而 `spliterator()` 又建立在 `iterator()` 之上的。**

## 3.4 模式的四个角色在本项目的落点

GoF 原文里的迭代器模式有四个角色。很多教材只讲名字不讲"为什么是四个"。

| 角色 | 职责 | 本项目对应 | 如果去掉会怎样 |
|---|---|---|---|
| **Iterator**（迭代器接口） | 定义"取下一个"的协议 | `java.util.Iterator<WorkflowNode>` | 每种迭代器各有一套方法名，`FilteringIterator` 无法包装任意实现 |
| **ConcreteIterator**（具体迭代器） | 实现某一种遍历算法 | `DepthFirstIterator`<br>`BreadthFirstIterator`<br>`FilteringIterator` | 没有实际遍历能力 |
| **Aggregate**（聚合接口） | 承诺"我能产出迭代器" | `java.lang.Iterable<WorkflowNode>` | 调用方必须知道具体容器类型 |
| **ConcreteAggregate**（具体聚合） | 实际的容器 | `Workflow` | 没有数据 |

### 注意：本项目的 `Iterator` 和 `Iterable` 用的是 JDK 的，不是自己写的

原项目自己写了一个 `interface Iterator`，本项目**没有**。

理由有三条：

**理由一：JDK 的接口已经是行业标准**

```java
public interface Iterator<E> {
    boolean hasNext();
    E next();
    default void remove() { throw new UnsupportedOperationException(); }
    default void forEachRemaining(Consumer<? super E> action) { ... }
}
```

JDK 版本还有 `remove()`、`forEachRemaining()` 两个方法，
以及一个明确的契约（`next()` 无元素时抛 `NoSuchElementException`）。

**理由二：自己写就会漏掉契约**

原项目自己写的 `Iterator` 只抄了两个方法名，把契约抄丢了 ——
于是 `next()` 返回了 `null`（问题 3）。

**理由三：用 JDK 的接口，自动兼容所有工具**

- `Collections`、`Stream`、Guava、Apache Commons 全都以 JDK 的 `Iterator` / `Iterable` 为准；
- 自定义接口写不出 `StreamSupport.stream(...)`。

**只有一种情况应该自己定义迭代器接口**：目标类型系统不是 Java（比如写 DSL、
或者要做跨语言的序列化协议）。在普通 Java 项目里，用 JDK 的就够了。

### 本项目的"四角色"映射图

```
        ┌──────────────────────────┐
        │  java.lang.Iterable<E>   │  ← Aggregate（JDK 提供）
        │  Iterator<E> iterator()  │
        └────────────▲─────────────┘
                     │ implements
        ┌────────────┴─────────────┐
        │        Workflow          │  ← ConcreteAggregate
        │  List<WorkflowNode> roots│
        │  int modCount            │
        │  iterator(TraversalOrder)│
        └────────────┬─────────────┘
                     │ 创建
        ┌────────────▼─────────────┐
        │ java.util.Iterator<E>    │  ← Iterator（JDK 提供）
        │  boolean hasNext()       │
        │  E next()                │
        └────────────▲─────────────┘
                     │ implements
        ┌────────────┴─────────────┐
        │  DepthFirstIterator      │  ← ConcreteIterator
        │  BreadthFirstIterator    │
        │  FilteringIterator       │
        └──────────────────────────┘
```

**注意图中的箭头方向**：具体迭代器指向 `Workflow` 的只有一条"创建"线，
而不是"继承"。迭代器**不是**容器的一部分，它是一个独立的对象。

## 3.5 为什么本项目选"树"当容器

原项目用的是数组。要修那八个问题，用 `List<ProcessNode>` + 泛型 + fail-fast
就够了，根本不需要动结构。

**为什么还要把容器改成树？**

因为如果容器是 `ArrayList`，那这个模式就变成了"重新实现 JDK 已有的东西"，
学员学到的只是"怎么写一个和 `ArrayList.Itr` 一样的东西"。

而树能带来三个数组给不了的东西：

### 1. 让"多种遍历顺序"变成真实需求

一维数组只有一种顺序，"深度优先"和"广度优先"这两个词在数组上没有意义。

树有父子关系，于是：

- 深度优先 = 先看完整条路径；
- 广度优先 = 先看完整层。

**这两个都是真实业务需求（1.6 节的财务和审计），不是硬造出来的。**

### 2. 让"迭代器不能靠下标"变成硬约束

数组的迭代器可以偷懒：`index++` 就够了。

树的迭代器必须自己维护一个**待访问集合**（栈或队列）——
这才是迭代器模式的完整形态：

```java
private final Deque<WorkflowNode> stack = new ArrayDeque<>();
```

**没有这个栈，你就永远学不到"迭代器的状态可以很复杂"这件事。**

### 3. 让"遍历算法与容器分离"有可验证的效果

如果遍历写在容器里，数组版本只会多几行 `if`。

树版本呢？容器里要同时放下：

- 深度优先的栈逻辑；
- 广度优先的队列逻辑；
- 过滤的预读逻辑；
- 倒序的反向逻辑。

`Workflow.java` 会变成一个 300 行的怪物，而且每加一种走法都要重新理解它。

本项目把这些全部搬出去之后，`Workflow` 只剩 **63 行代码**。

---

# 4. 设计推导：十一个决定

## 4.0 从 4 个文件到 11 个文件

先看整体形状，再逐个解释为什么是这 11 个。

| # | 文件 | 物理行 | 代码行 | 一句话职责 |
|---:|---|---:|---:|---|
| 1 | `StepType.java` | 59 | 14 | 节点类型的枚举 |
| 2 | `WorkflowNode.java` | 105 | 9 | 三种节点共用的 sealed 接口 |
| 3 | `StepNode.java` | 101 | 21 | 叶子：一个动作 |
| 4 | `BranchNode.java` | 112 | 30 | 分支：二选一 |
| 5 | `SequenceNode.java` | 130 | 24 | 顺序块：依次执行 |
| 6 | `TraversalOrder.java` | 73 | 12 | 遍历顺序的枚举 |
| 7 | `Workflow.java` | 280 | 63 | 容器：只提供结构 |
| 8 | `DepthFirstIterator.java` | 205 | 37 | 深度优先迭代器 |
| 9 | `BreadthFirstIterator.java` | 139 | 34 | 广度优先迭代器 |
| 10 | `FilteringIterator.java` | 202 | 44 | 过滤迭代器（装饰器） |
| 11 | `Client.java` | 401 | 202 | 六段 Demo |
| — | **合计** | **1807** | **490** | |

### 为什么文件数从 4 涨到 11

一句话：**把"编译单元"和"概念单元"对齐。**

原项目的 4 个文件里，`Workflow.java` 装了 3 个概念：

```java
class Workflow {                    // ① 容器
    private final ProcessNode[] nodes;
    public void addNode(...) { ... }
    public Iterator iterator() { ... }

    private class WorkflowIterator {  // ② 迭代器
        private int index;
        ...
    }
}
```

而 `ProcessNode.java` 只装了 1 个概念，却**同时承担了"结构"和"类型"两个职责**
（它只有一个 `name` 字段，既要表达名字，又要隐含类型 —— 参见问题 7）。

拆分之后，每个文件只回答一个问题：

```
StepType          → 有哪些类型？
WorkflowNode      → 节点共有哪些能力？
StepNode          → 一个动作长什么样？
BranchNode        → 一个分支长什么样？
SequenceNode      → 一串动作长什么样？
TraversalOrder    → 有哪几种走法？
Workflow          → 怎么存、怎么建？
DepthFirstIterator→ 深度优先怎么走？
BreadthFirstIterator → 广度优先怎么走？
FilteringIterator → 怎么在遍历上加过滤？
Client            → 怎么用？
```

### 但文件多不等于设计好

必须说清楚一件事：**11 个文件不是目标，是结果。**

如果业务只有一条直线流程，正确的文件数是 2 个（`ProcessNode` + `Client`），
多一个都是在增加理解成本。

本项目之所以是 11 个，是因为它要同时演示四件事：

1. 复杂结构（树）怎么遍历；
2. 多种算法（DFS/BFS）怎么共存；
3. 迭代器怎么被装饰（过滤）；
4. fail-fast 契约怎么实现。

**每一件事都需要至少一个独立类型来承载，这才是 11 个的来源。**

## 4.1 决定一：为什么必须有 WorkflowNode 接口

### 问题

树里的节点有三种形态：

- 一个步骤（`StepNode`）；
- 一个分支（`BranchNode`）；
- 一串步骤（`SequenceNode`）。

迭代器遍历时，拿到的东西是"一个节点"。

**如果不定义 `WorkflowNode` 接口，迭代器要返回什么类型？**

### 三个备选方案

**方案 A：返回 `Object`**

```java
public Object next() { ... }
```

这就是原项目的做法。后果已经在 2.4 节看过了：每个调用方都要强转。

**方案 B：返回一个公共父类**

```java
public abstract class WorkflowNode {
    protected String name;
}
```

可行，但 Java 是单继承。用掉唯一的继承位，`SequenceNode` 就没办法再继承别的了。
而且"节点"是一个纯粹的能力概念，没有共享状态要实现 —— **接口比父类更贴切。**

**方案 C：返回 sealed interface（本项目）**

```java
public sealed interface WorkflowNode permits StepNode, BranchNode, SequenceNode {
    String name();
    List<WorkflowNode> children();
    default boolean isLeaf() { return children().isEmpty(); }
}
```

### 为什么接口只放两个方法

`WorkflowNode` 只有 `name()` 和 `children()`，加上一个默认方法 `isLeaf()`。

**为什么不是把三种节点共有的所有东西都堆上来？**

因为三种节点**真正共有的东西很少**：

| 能力 | StepNode | BranchNode | SequenceNode | 该放接口吗 |
|---|:---:|:---:|:---:|---|
| 有名字 | ✓ | ✓ | ✓ | **该** |
| 有子节点 | ✓（空） | ✓（2 个） | ✓（n 个） | **该** |
| 有类型 `StepType` | ✓ | ✗ | ✗ | 不该 |
| 有条件表达式 | ✗ | ✓ | ✗ | 不该 |
| 有步骤数 | ✗ | ✗ | ✓ | 不该 |

如果把 `type()` 放上接口，`BranchNode` 就必须回答"我是什么类型" ——
但它**没有类型**。

硬加的话，只能返回 `null` 或者造一个 `BRANCH` 枚举值，两种都很别扭：

```java
// 强行放上接口会变成这样
public interface WorkflowNode {
    String name();
    StepType type();      // BranchNode 怎么办？
}
```

**接口应该只包含"所有实现都真的具备的能力"。**
缺一个就会有人返回 `null` 或者抛 `UnsupportedOperationException`。

### 一个反直觉的细节：`children()` 返回的列表必须不可变

接口 javadoc 里写了这条：

> 返回的列表必须不可变：迭代器会直接把它当快照使用，
> 若调用方能改动它，遍历顺序会在遍历途中莫名其妙地变。

为什么这是**接口层面**的约束，而不是实现层面的建议？

因为迭代器**直接持有**这个列表的引用（比如 DFS 会 `stack.push(children.get(i))`）。
如果某个实现返回了内部可变列表，调用方一改，遍历顺序就变了，而 `modCount` 毫无察觉。

**约束写在接口上，所有实现才有统一的行为。**

## 4.2 决定二：为什么是 sealed 而不是普通接口

### 问题

`WorkflowNode` 有且只有三种实现。要不要把这件事告诉编译器？

### 三个备选方案

**方案 A：普通接口**

```java
public interface WorkflowNode {
    String name();
    List<WorkflowNode> children();
}
```

好处是开放：任何人都能加第四种节点。

坏处是**你永远不知道还有没有第四种**。于是：

```java
switch (node) {
    case StepNode s -> ...;
    case BranchNode b -> ...;
    // 编译器要求写 default，因为你没承诺只有两种
    default -> ...;
}
```

`default` 分支会变成"垃圾桶"：新增节点类型时，它悄悄走进 `default`，
不报错、不告警，只是行为不对。

**方案 B：枚举 + 一个万能节点类**

```java
public enum NodeKind { STEP, BRANCH, SEQUENCE }

public class WorkflowNode {
    private NodeKind kind;
    private String name;
    private List<WorkflowNode> children;
    private StepType stepType;      // 只有 STEP 用
    private String condition;       // 只有 BRANCH 用
}
```

一个类装三种形态，用 `kind` 区分。

这是"反模式"的典型：**一半的字段在任意时刻都是 `null`**。
而且没有任何编译期检查能阻止你给 `STEP` 节点设置 `condition`。

**方案 C：sealed（本项目）**

```java
public sealed interface WorkflowNode permits StepNode, BranchNode, SequenceNode {
```

### sealed 换来的两个具体好处

#### 好处一：switch 不需要 default，漏一个就编译失败

```java
// 假设将来在 Client 里写这样的代码
String describe(WorkflowNode node) {
    return switch (node) {
        case StepNode s -> "步骤：" + s.name();
        case BranchNode b -> "分支：" + b.name();
        case SequenceNode q -> "顺序块：" + q.name();
    };
}
```

如果再加一个 `LoopNode` 到 `permits` 列表里，**这段代码立刻编译失败**，提示：

```
错误: 当前 switch 表达式不完整，缺少 LoopNode 的情况
```

**这是 sealed 最大的价值：把"你忘了一个分支"从运行时错误变成编译错误。**

#### 好处二：封闭的集合可以被穷尽推理

因为只有三种节点，所以可以确定地回答：

- 所有节点都有名字 ✓
- 所有节点都有 `children()` ✓
- 所有节点都是不可变的 ✓

如果接口是开放的，上面三条都无法保证 —— 别人可以在任何时候加一个
"有 `children()` 但会抛异常"的实现。

### 为什么"封闭"在这里是对的

sealed 听起来像"限制自由"，但它限制的是一种**本来就不该存在的自由**。

审批流的节点形态，是由"流程"这个概念本身决定的：

- 一个动作（步骤）
- 一个判断（分支）
- 一串动作（顺序）

**这是业务语言的封闭集合，不是技术上的偷懒。**

如果有人真的需要第四种形态（比如"循环"、"并行"、"子流程引用"），
那是一次**业务模型的扩展**，理应改 `permits` ——
改动应该显式地发生在类型定义处，而不是悄悄发生在某个下游 `switch` 的 `default` 里。

### 顺带一提：`permits` 允许省略

如果一个 sealed 接口的所有实现都在**同一个文件**里，`permits` 子句可以省略，
编译器自动推断。

本项目把它们拆成 4 个文件，是为了每个文件只讲一个概念。
所以 `permits` 必须显式写出来。

## 4.3 决定三：为什么 children() 放在节点上

### 问题

迭代器要展开一个节点，它需要知道"这个节点下面还有谁"。

**这个知识应该放在哪？**

### 三个备选方案

**方案 A：迭代器用 `switch` 判断节点类型**

```java
private void pushChildren(WorkflowNode node) {
    switch (node) {
        case BranchNode b -> {
            stack.push(b.elseBranch());
            stack.push(b.thenBranch());
        }
        case SequenceNode q -> {
            for (int i = q.steps().size() - 1; i >= 0; i--) {
                stack.push(q.steps().get(i));
            }
        }
        case StepNode s -> { /* 叶子，什么都不做 */ }
    }
}
```

这是**最直觉**的写法，也是**最糟**的写法。

**方案 B：节点自己提供 children()（本项目）**

```java
public sealed interface WorkflowNode {
    List<WorkflowNode> children();
}
```

**方案 C：用一个外部访问者**

```java
public interface NodeVisitor {
    void visitStep(StepNode node);
    void visitBranch(BranchNode node);
    void visitSequence(SequenceNode node);
}
```

这是访问者模式的思路。它可以工作，但**为了一件小事引入了两个新概念**
（`NodeVisitor` 接口 + 每个迭代器都要实现它）。

### 为什么方案 A 会出事

假设项目上线三个月后，业务要加第四种节点：`LoopNode`（循环子流程）。

用方案 A，你要改：

1. `DepthFirstIterator.pushChildren()`；
2. `BreadthFirstIterator.next()`；
3. 也许还有 `FilteringIterator`（如果它对节点类型有特殊处理）。

**三处改动，编译器一处都不会提醒你漏了。**

因为 `switch` 里如果写了 `default -> { }`，新类型会静默走进去；
如果没写 `default`，编译器**会**提醒 —— 但前提是你用了 sealed + 无 default 的 switch。

等等，那方案 A 配合 sealed，不也能有编译期保护吗？

**能。但还有一个更深的问题：**

方案 A 把"每种节点怎么展开"这个**结构知识**复制到了每一个迭代器里。

- `DepthFirstIterator` 要知道 `BranchNode` 有两个孩子，还要知道压栈要反序；
- `BreadthFirstIterator` 要知道同样的事，只是入队要正序。

**同一份结构知识，写了两遍。**

而方案 B 里，`BranchNode.children()` 只写一次，两个迭代器都调用它。

### 方案 B 的直接证据：SequenceNode 是后加的

这是本项目开发过程中的真实顺序：

1. 先写 `WorkflowNode`、`StepNode`、`BranchNode`；
2. 再写 `Workflow`、`DepthFirstIterator`、`BreadthFirstIterator`；
3. 然后发现"审批完再通知"这条链没地方放，**后加** `SequenceNode`。

加入 `SequenceNode` 时，两个迭代器的代码：

```diff
  // DepthFirstIterator.java
- 无任何改动
  // BreadthFirstIterator.java
- 无任何改动
```

因为它们的展开逻辑是这一行：

```java
List<WorkflowNode> children = node.children();
```

**它不认识 `BranchNode`，也不认识 `SequenceNode`。它只认识"孩子"这个概念。**

这就是抽象的收益：**新增一种实现，不需要改任何已有的使用方。**

### 代价：多了一个"每个节点都要回答的问题"

方案 B 的代价是：每个节点类型都必须实现 `children()`。

`StepNode` 的实现是：

```java
private static final List<WorkflowNode> NO_CHILDREN = List.of();

@Override
public List<WorkflowNode> children() {
    return NO_CHILDREN;
}
```

三行代码，换来两个迭代器一辈子不用改。**这笔交易非常划算。**

## 4.4 决定四：为什么树需要三种节点

### 问题

真实的审批流需要表达几种结构？

先看业务语言里的说法：

> "提交之后判断金额，超过 1000 走总监线，**总监审批完之后通知财务**；
> 不超过 1000 走主管线，**主管审批完之后通知申请人**。"

这句话里有两种连接词：

1. **"判断……走 A，否则走 B"** → 二选一
2. **"……之后……"** → 依次执行

### 只有分支节点会怎样

```java
new BranchNode("金额判断", "amount > 1000", 总监审批, 主管审批)
```

`thenBranch` 只能放**一个**节点。

于是「通知财务」无处可放。

### 三个错误的补救方案

**补救 A：给 StepNode 加子节点**

```java
record StepNode(String name, StepType type, List<WorkflowNode> children)
```

后果：`StepNode` 不再是叶子。

"这是不是叶子"这个判断消失之后，迭代器无法知道"要不要继续往下展开" ——
它只能检查 `children().isEmpty()`，那和 `isLeaf()` 就没区别了。

更糟的是：**语义崩了**。"一个动作"后面跟着一串节点，这在业务上叫什么？
叫"这个动作完成后继续执行"—— 那不就是顺序块吗？

**补救 B：把链拆成多个根节点**

```java
workflow.add(开始)
        .add(金额分支)
        .add(通知财务)
        .add(通知申请人);
```

后果：`通知财务` 和 `金额分支` 变成了**兄弟**。

树上的父子关系丢失了。遍历时无法知道「通知财务」属于哪条分支。

**补救 C：用 `List<WorkflowNode>` 当 thenBranch 的类型**

```java
record BranchNode(String name, String condition,
                  List<WorkflowNode> thenBranch,
                  List<WorkflowNode> elseBranch)
```

后果：列表可以空、可以有 1 个、可以有 10 个。

- 空列表 = 这条分支上什么都没有（业务上说不通）；
- 10 个 = 其实就是一个顺序块，但被展开成了列表。

**类型没说清语义。** "两个节点的列表"和"顺序执行的顺序块"是完全不同的概念，
用同一个类型表达就会丢失信息。

### 本项目的做法：三种节点，一一对应三种业务语义

| 业务语言 | 节点类型 | `children()` 返回 |
|---|---|---|
| "做这件事" | `StepNode` | 空列表（共享常量） |
| "判断，走 A 还是 B" | `BranchNode` | 固定两个 |
| "先做 A，再做 B，再做 C" | `SequenceNode` | 构造时传入的整个列表 |

**三种节点刚好覆盖全部组合需求，而且两两不重叠。**

### 为什么"刚好"很重要

如果用两种节点能表达三种语义，那是好事（更少的概念）。

但这里不行：

- `StepNode` + `BranchNode`：表达不了"依次"，因为分支只能二选一（4.4 节的补救 A/B/C 都失败）；
- `StepNode` + `SequenceNode`：表达不了"二选一"，因为顺序块是"全都要做"。

三种是**最小完备集合**。

### 顺便：为什么分支只有两条

三个以上分支可以嵌套表达：

```
金额 > 5000 ？
  ├─ 是 → 总裁审批
  └─ 否 → 金额 > 1000 ？
           ├─ 是 → 总监审批
           └─ 否 → 主管审批
```

二叉树已经是图灵完备的决策结构。多分支只是语法糖。

**收窄到两条的好处是具体可见的**：

```java
@Override
public List<WorkflowNode> children() {
    return List.of(thenBranch, elseBranch);
}
```

永远两个元素。迭代器不需要处理"0 个孩子"和"n 个孩子"两种边界。

## 4.5 决定五：为什么 Workflow 必须 implements Iterable

这一条在 2.3 节已经讲过后果，这里讲**设计上为什么绕不过去**。

### Java 的增强 for 是编译期语法糖

```java
for (WorkflowNode node : flow) {
    ...
}
```

javac 把它翻译成：

```java
for (Iterator<WorkflowNode> it = flow.iterator(); it.hasNext(); ) {
    WorkflowNode node = it.next();
    ...
}
```

**注意最后一行没有强制类型转换** —— 因为 `it.next()` 返回的是 `WorkflowNode`。

而 javac 决定"能不能这样翻译"的唯一依据是：

> `flow` 的类型是不是 `Iterable`（或者是不是数组）。

**没有第二种可能。** 你不能靠"我有一个叫 `iterator()` 的方法"骗过它。

### 为什么 Java 不设计成"有 iterator() 方法就行"

这是结构类型（structural typing）和名义类型（nominal typing）的区别。

Java 选择名义类型：**一个类必须显式声明它实现了某个接口。**

好处是：

- 编译器只需要看类型声明，不需要扫全部方法；
- 接口是一个**承诺**，作者签名确认过；
- 不会因为巧合（恰好有个同名方法）就被当成实现。

坏处是：**你必须记得写 `implements`。**

原项目就是忘了写（或者不知道要写）。

### 写了 implements 之后自动获得什么

| 能力 | 来源 |
|---|---|
| 增强 `for` | javac 语法糖 |
| `flow.forEach(node -> ...)` | `Iterable` 默认方法 |
| `flow.spliterator()` | `Iterable` 默认方法 |
| `StreamSupport.stream(flow.spliterator(), false)` | 把任意 `Iterable` 变成 `Stream` |
| 传给任何 `<T extends Iterable<X>>` 的库函数 | 类型系统 |

**一行 `implements`，换上面五条。**

### 一个常见的误判

> "我的 `Workflow` 有一个 `iterator()` 方法，为什么不能用增强 for？"

因为**方法名一样不等于实现了接口**。

Java 里只有两种"能被增强 for 遍历"的东西：

1. 数组；
2. `Iterable` 的实现。

`Iterator` **不是** `Iterable`。这一点经常被搞混：

```java
Iterator<WorkflowNode> it = flow.iterator();
for (WorkflowNode n : it) { }        // ← 编译错误

for (WorkflowNode n : flow) { }      // ← 正确，遍历的是容器
```

| | `Iterable` | `Iterator` |
|---|---|---|
| 语义 | "我能被遍历" | "我正在遍历" |
| 方法 | `iterator()` → 造一个新游标 | `hasNext()` / `next()` → 走一步 |
| 数量 | 一个容器一个 | 一个容器可以有无数个 |
| 增强 for | 接受 | 不接受 |

**`Iterable` 是容器，`Iterator` 是进度条。**

## 4.6 决定六：为什么用 TraversalOrder 枚举

### 问题

`Workflow` 要支持两种遍历顺序。用什么形式暴露给调用方？

### 方案 A：两个方法

```java
public Iterator<WorkflowNode> depthFirstIterator() { ... }
public Iterator<WorkflowNode> breadthFirstIterator() { ... }
```

**这完全可行**，而且是很多 JDK 类的风格。

### 方案 B：一个方法带枚举参数（本项目）

```java
public Iterator<WorkflowNode> iterator(TraversalOrder order) {
    return switch (order) {
        case DEPTH_FIRST -> new DepthFirstIterator(this);
        case BREADTH_FIRST -> new BreadthFirstIterator(this);
    };
}
```

同时保留一个无参的 `iterator()` 作为默认实现（`Iterable` 要求）。

### 为什么选 B

#### 理由一：顺序变成可传递的值

方案 A 里，"顺序"是**方法名**的一部分。方法名不能被存进变量、传进函数、写进配置。

于是调用方要处理"从配置读来的顺序"时，只能这样：

```java
// 方案 A 下，每个调用点都要写一遍这个 if
Iterator<WorkflowNode> it;
if ("breadth-first".equals(config.getTraversal())) {
    it = flow.breadthFirstIterator();
} else {
    it = flow.depthFirstIterator();
}
```

**这段 `if` 会出现在每一个需要建迭代器的地方。**

方案 B 里，这个 `if` 只出现一次（在 `switch` 里）：

```java
TraversalOrder order = TraversalOrder.valueOf(config.getTraversal());
Iterator<WorkflowNode> it = flow.iterator(order);
```

#### 理由二：加第三种顺序时，编译器帮你找调用点

方案 A 加第三种顺序：新增一个方法。**已有的 `if` 全部不会报错**，
它们会继续走 `else` 分支，静默地忽略新顺序。

方案 B 加第三种顺序：在 `TraversalOrder` 里加一个值。
`Workflow.iterator()` 里的 `switch` 是**穷尽表达式**（没有 `default`），
**立刻编译失败**，提示你少了一个 `case`。

```java
return switch (order) {
    case DEPTH_FIRST -> new DepthFirstIterator(this);
    case BREADTH_FIRST -> new BreadthFirstIterator(this);
    // 加了 POST_ORDER 之后，这里编译报错，必须显式处理
};
```

**这是 sealed / enum + 无 default switch 的又一次收益。**

#### 理由三：枚举本身有名字

```java
public enum TraversalOrder {
    DEPTH_FIRST("深度优先"),
    BREADTH_FIRST("广度优先");
    private final String label;
    public String label() { return label; }
}
```

日志里打印 `order.label()` 得到"深度优先"，而不是 `0` 或 `"dfs"`。

### 方案 A 什么时候更好

**只有一种顺序**的时候。

那时候连枚举都不需要，`iterator()` 一个方法就够了。

本项目之所以需要枚举，是因为**真的有两种**，而且将来可能更多。
如果只有深度优先，这个枚举就是多余的抽象。

### 一个容易误解的点

`TraversalOrder` **不是迭代器模式的一部分**。

GoF 的迭代器模式只要求"容器能产出迭代器"。

多种顺序是**这个业务场景**的需要，不是模式的组成。
如果对照 GoF 的类图，`TraversalOrder` 根本不会出现。

**所以它的 javadoc 里专门写了一句**：

> 注意：这个枚举不是模式的一部分。

**分清"模式的必要组成"和"这个项目的具体选择"，是读懂设计模式的关键。**

## 4.7 决定七：为什么 DFS 用显式栈而不是递归

### 递归版本有多简单

```java
void visit(WorkflowNode node) {
    System.out.println(node);
    for (WorkflowNode child : node.children()) {
        visit(child);
    }
}
```

**四行。** 而且是人类最容易理解的深度优先。

### 但它做不到迭代器要做的三件事

#### 做不到一：不能暂停

迭代器的核心契约是"一次取一个"：

```java
Iterator<WorkflowNode> it = flow.iterator();
it.next();       // 取一个
// ... 干别的 ...
it.next();       // 再取一个
```

递归函数一旦调用，就必须**走完整个树**才能返回。
想在中途停下，没有"进度"可以保存。

**强行实现只能这样**：

```java
// 用异常做流程控制，或者在递归里传播一个 boolean 标志
private boolean stopped;
void visit(WorkflowNode node) {
    if (stopped) return;
    System.out.println(node);
    ...
    if (shouldStop()) { stopped = true; return; }
    ...
}
```

代码立刻变得难读，而且**每次遍历都要新建一个对象来存 `stopped`**。

#### 做不到二：不能同时开两个游标

递归的"进度"在**方法调用栈**里。方法栈是线程私有的、不可复制的。

```java
Iterator a = flow.iterator();
Iterator b = flow.iterator();
```

如果 `iterator()` 内部是递归，这两个"迭代器"要么是同一个递归的不同阶段
（不可能同时进行），要么每次 `next()` 都从头递归一遍（找到第 N 个就停）——
**O(n²)**。

#### 做不到三：深度受方法栈限制

Java 默认的线程栈大小是 512KB ~ 1MB。每次递归调用占一个栈帧。

一棵几万节点的流程树，**直接 `StackOverflowError`**。

而且这个错误：

- 在测试数据（几个节点）上永远测不出来；
- 上生产遇到大流程才炸；
- `StackOverflowError` 是 `Error` 不是 `Exception`，`catch (Exception e)` 抓不住。

### 显式栈把"进度"从栈搬到堆

```java
private final Deque<WorkflowNode> stack = new ArrayDeque<>();
```

这个 `stack` 是一个**堆上的对象**。于是：

| | 递归 | 显式栈 |
|---|---|---|
| 进度存在哪 | 方法调用栈 | 堆上的 `ArrayDeque` |
| 能否暂停 | 不能 | 能（对象还在） |
| 能否有多个 | 不能 | 能（多个对象） |
| 深度上限 | 方法栈（约几万帧） | 堆（几百 MB） |
| 出错形式 | `StackOverflowError` | `OutOfMemoryError`（且更晚） |

### 代价：可读性下降

显式栈版本：

```java
@Override
public WorkflowNode next() {
    workflow.checkForComodification(expectedModCount);
    if (stack.isEmpty()) {
        throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点");
    }
    WorkflowNode node = stack.pop();
    List<WorkflowNode> children = node.children();
    for (int i = children.size() - 1; i >= 0; i--) {
        stack.push(children.get(i));
    }
    return node;
}
```

比四行递归长，而且需要理解"为什么反序"。

**这笔交易是值得的**：迭代器本来就是为了"可控的遍历"，
放弃可控性去换四行的简洁，等于买椟还珠。

### 那 Client 里的递归算什么

`Client.printTree()` **保留了递归**：

```java
private static void printTree(WorkflowNode node, String prefix, boolean last, boolean root) {
    String connector = root ? "" : (last ? "└─ " : "├─ ");
    System.out.println("    " + prefix + connector + node);

    List<WorkflowNode> children = node.children();
    for (int i = 0; i < children.size(); i++) {
        boolean childLast = i == children.size() - 1;
        String childPrefix = root ? "" : prefix + (last ? "   " : "│  ");
        printTree(children.get(i), childPrefix, childLast, false);
    }
}
```

**因为它要做的事本来就不需要可暂停**：

- 一次把整棵树画完；
- 每一层需要知道自己的缩进（这是递归参数天然携带的）；
- 不需要游标、不需要多个、不需要暂停。

**该用递归的地方用递归，该用迭代器的地方用迭代器。**

这一点在 4.11 节还会展开。

## 4.8 决定八：为什么压栈要反序

### 问题

`BranchNode.children()` 返回 `[thenBranch, elseBranch]`。

深度优先遍历时，怎么压栈才能保证「先成立、后不成立」？

### 栈是后进先出

```
push(A) → 栈: [A]
push(B) → 栈: [A, B]        (B 在顶)
pop()   → 返回 B             (先弹出的永远是最后压入的)
```

### 两组代码，两种结果

**错误写法（正序压）**：

```java
for (int i = 0; i < children.size(); i++) {
    stack.push(children.get(i));
}
// children = [then, else]
// push(then) → 栈: [then]
// push(else) → 栈: [then, else]
// pop() → else    ← 不成立的分支先出来了！
```

**正确写法（反序压）**：

```java
for (int i = children.size() - 1; i >= 0; i--) {
    stack.push(children.get(i));
}
// i=1: push(else) → 栈: [else]
// i=0: push(then) → 栈: [else, then]
// pop() → then    ← 成立的分支先出来 ✓
```

### 这个 bug 有多隐蔽

正序压栈**不会报错**，输出也仍然是"一个合法的深度优先遍历" ——
只是左右分支反了。

```
错误输出：
  1. 顺序块「报销主流程」
  2. 开始节点「提交报销单」
  3. 分支「金额是否超过 1000」
  4. 顺序块「主管线」        ← 先走了 else
  5. 审批节点「主管审批」
  6. 通知节点「通知申请人」
  7. 顺序块「总监线」
  8. 审批节点「总监审批」
  9. 通知节点「通知财务」
```

看起来完全正常。**只有把输出和树形结构对照，才能发现顺序反了。**

### 为什么"反序"这件事无法被类型系统捕获

因为栈的语义（后进先出）是**运行时行为**，不是类型约束。

`Deque<WorkflowNode>` 既能当栈也能当队列，类型系统不区分。

**唯一的防线是注释 + 对比测试**：

```java
// DepthFirstIterator.java
List<WorkflowNode> children = node.children();
for (int i = children.size() - 1; i >= 0; i--) {
    stack.push(children.get(i));
}
```

```java
// BreadthFirstIterator.java
WorkflowNode node = queue.pollFirst();

// 正序入队：先左后右，同一层按声明顺序展开
queue.addAll(node.children());
```

**两个类都写了注释说明为什么是这一头。**

`BreadthFirstIterator` 的类注释还专门警告了这件事：

> 比如本类里把 `pollFirst()` 写成 `pollLast()`，
> 代码照样编译、照样跑完、不抛任何异常 —— 只是遍历顺序
> 悄悄变成了"倒着的广度优先"。这类错误只能靠单元测试
> 逐字比对输出来发现，编译器帮不上忙。

### 一个可以验证的口诀

| 结构 | 取出方向 | 装入孩子 |
|---|---|---|
| 栈（DFS） | 同侧（`pop` / `push` 都是头部） | **必须反序** |
| 队列（BFS） | 异侧（尾部装入、头部取出） | **必须正序** |

**"同侧反序，异侧正序"。** 记住这八个字就不会写错。

## 4.9 决定九：为什么 modCount 放在 Workflow 里

### 问题

fail-fast 需要记录"结构被改了几次"。

这个计数器应该放在迭代器里，还是容器里？

### 迭代器里的写法（错误）

```java
public class DepthFirstIterator {
    private int seenChildren;      // 我见过多少个子节点
    private int expectedSize;      // 开始时的总数

    private void check() {
        if (workflow.rootCount() != expectedSize) { ... }
    }
}
```

问题很多：

- "总数"和"结构被改过"不是一回事（加一个又删一个，总数不变，但结构变了）；
- 每次检查都要求一遍容器（`rootCount()` 要 `List.copyOf`）；
- 两个迭代器各存一份，数字可能不一致。

### 容器里的写法（本项目）

```java
public final class Workflow implements Iterable<WorkflowNode> {
    private final List<WorkflowNode> roots = new ArrayList<>();
    private int modCount;

    public Workflow add(WorkflowNode node) {
        Objects.requireNonNull(node, "节点不能为 null");
        roots.add(node);
        modCount++;                 // ← 唯一修改结构的地方
        return this;
    }

    int modCount() {
        return modCount;
    }

    void checkForComodification(int expectedModCount) {
        if (modCount != expectedModCount) {
            throw new ConcurrentModificationException(
                    "遍历期间审批流被修改：迭代器创建时修改了 "
                            + expectedModCount + " 次，现在是 " + modCount + " 次");
        }
    }
}
```

### 为什么这样更好

#### 理由一：计数器是容器的属性

"这个容器被改过几次" —— 这是容器的事实，不是某个迭代器的事实。

放在容器里，**所有迭代器看到的是同一份真相**。

#### 理由二：检查逻辑只有一份

```java
void checkForComodification(int expectedModCount) { ... }
```

它是**包级可见**的（没有 `public`），因为它是容器和迭代器之间的**内部协议**。

`DepthFirstIterator` 和 `BreadthFirstIterator` 都调用它：

```java
// DepthFirstIterator
workflow.checkForComodification(expectedModCount);

// BreadthFirstIterator
workflow.checkForComodification(expectedModCount);
```

**为什么必须共用？** 因为如果各写一份，很容易出现：

- DFS 的检查写得对；
- BFS 的检查写漏了一个边界。

然后你遇到"深度优先会抛异常、广度优先不会"—— 这种分裂行为比"都不检查"更难查。

#### 理由三：不在容器上暴露成 public

如果 `modCount()` 是 `public` 的，会有人拿它做业务判断：

```java
// 错误用法：把 modCount 当成"版本号"
if (flow.modCount() > lastSyncVersion) {
    resync(flow);
}
```

这会导致两个问题：

1. 业务逻辑和内部实现细节耦合；
2. `modCount` 的语义是"修改次数"，不是"版本号"（它可能溢出，也可能因为无关操作变化）。

**包级可见 = 只给自己人用。这是最小暴露原则的具体应用。**

### 一个细节：为什么消息里要带两个数字

```java
"遍历期间审批流被修改：迭代器创建时修改了 " + expectedModCount + " 次，现在是 " + modCount + " 次"
```

JDK 的 `ConcurrentModificationException` 默认**不带消息**。你只能看到一个空异常。

本项目的消息直接告诉你：

- 迭代器创建时是第几次修改；
- 现在是多少次。

排查时这两个数字能立刻区分两种情况：

- 相差 1：一次 `add`，很可能是刚加的某个节点；
- 相差很大：可能是另一个线程在批量操作。

### 为什么 hasNext() 不检查

这是刻意与 JDK 保持一致：

```java
// JDK ArrayList.Itr
public boolean hasNext() {
    return cursor != size;      // 不检查 modCount
}

public E next() {
    checkForComodification();   // 这里才检查
    ...
}
```

**代价是真实存在的**（观察者模式那份文档里用探针挖过这个坑）：

遍历中删掉倒数第二个元素时，`hasNext()` 会直接返回 `false`，不抛异常，
剩下的元素被静默跳过。

本项目选择保留这个行为，因为：

> 一个**布尔查询**方法却抛异常，更违反直觉。

要防住静默漏发，靠的是 `next()` 里的检查，而不是让 `hasNext()` 变成会爆炸的查询。

Demo 5 演示了这个设计：

```
  hasNext() 仍然返回 true —— 它只查栈，不查 modCount
  next() 抛出：ConcurrentModificationException
```

**`hasNext()` 说"还有"，`next()` 说"不行"。**
这看起来矛盾，但它是 JDK 的标准行为 —— **熟悉 JDK 的人一看就懂。**

## 4.10 决定十：为什么 FilteringIterator 必须预读

### 问题

过滤迭代器要保证：`hasNext()` 返回 `true` 时，`next()` 一定能取到一个**满足条件的**节点。

**难点：怎么知道"还有没有满足条件的"？**

### 天真的写法（错的）

```java
public boolean hasNext() {
    return delegate.hasNext();          // ← 错
}

public WorkflowNode next() {
    WorkflowNode node = delegate.next();
    if (!predicate.test(node)) {
        // 这个不满意的节点去哪？
        return next();                  // 递归跳过 —— 会 StackOverflow
    }
    return node;
}
```

三个问题：

1. `hasNext()` 撒谎：它说"有"，但可能剩下来的全都不满足条件；
2. `next()` 递归跳过会栈溢出（如果连续 10 万个不满足）；
3. 更根本的：**被取走的元素还不回去了。**

### 为什么"还不回去"

`Iterator` 是**单向**的。它没有 `unread()` 或 `putBack()` 方法。

```
delegate.next() → 取走一个
                ↑
            没有反向操作
```

所以一旦取到一个不满足条件的节点，只有两条路：

- **丢掉**（那就要继续取，直到取到满足条件的）；
- **返回它**（那过滤就失效了）。

### 本项目的做法：预读一个，存起来

```java
private WorkflowNode pending;
private boolean hasPending;

private void advance() {
    while (!hasPending && delegate.hasNext()) {
        WorkflowNode candidate = delegate.next();
        if (predicate.test(candidate)) {
            pending = candidate;
            hasPending = true;
        }
    }
}

@Override
public boolean hasNext() {
    advance();
    return hasPending;
}

@Override
public WorkflowNode next() {
    if (!hasNext()) {
        throw new NoSuchElementException(
                "在「" + description + "」条件下已经没有更多节点");
    }
    WorkflowNode result = pending;
    pending = null;
    hasPending = false;
    return result;
}
```

### 三个关键点

#### 关键点一：`advance()` 用 while 而不是 if

因为可能连续好几个节点都不满足条件。

```java
while (!hasPending && delegate.hasNext()) {
    ...
}
```

这个 `while` 会一直吃到找到一个满足条件的，或者 delegate 空了。

#### 关键点二：`!hasPending` 放在循环条件里

这保证了**重复调用 `hasNext()` 不会多推 delegate**.

```java
it.hasNext();    // 第一次：预读一个，hasPending = true
it.hasNext();    // 第二次：hasPending 已经是 true，循环不进入，直接返回 true
it.hasNext();    // 第三次：同上
```

**重复调用是安全的**，这一点很重要 —— 因为很多调用方会这样写：

```java
while (it.hasNext()) {          // 调一次
    if (something) continue;    // 不调 next()
}

// 下一次循环又调 hasNext()
```

#### 关键点三：`next()` 里也要调用 `hasNext()`

```java
public WorkflowNode next() {
    if (!hasNext()) { ... }     // ← 这里
    ...
}
```

为什么？因为调用方可能**不先调 `hasNext()` 直接调 `next()`**（虽然不推荐，但合法）。

如果 `next()` 里不调 `advance()`，那 `pending` 可能是空的。

**这是"防御性"写法：让 `next()` 自己保证前置条件成立，而不是依赖调用方的调用顺序。**

### 代价：hasNext() 有了副作用

`DepthFirstIterator.hasNext()` 是纯查询（只看栈空不空）。

`FilteringIterator.hasNext()` **会推动 delegate 前进**。

这违反了一条常见的 API 设计原则："查询方法不应该修改状态"。

**但这个代价无法避免** —— 因为"还有没有满足条件的"这个问题，
**只能靠提前取出来看**才能回答。

JDK 里也有同类的东西：

| 类 | 有副作用的查询 |
|---|---|
| `Scanner.hasNext()` | 会读取并缓存下一个 token |
| `BufferedReader.ready()` | 会触发一次底层读取 |
| `Stream.iterator().hasNext()` | 会拉取上游一个元素（流是 lazy 的） |

**这不是缺陷，是"惰性 + 过滤"这个组合的必然结果。**

### 一个必须知道的边界：fail-fast 会被延迟

如果 `delegate` 是 fail-fast 的迭代器，结构被修改的异常会在
`delegate.next()` 里抛出。

但 `FilteringIterator` 在 `pending` 已有元素时**不会碰 delegate**：

```java
private void advance() {
    while (!hasPending && delegate.hasNext()) {    // ← hasPending 为 true 时不进入
        ...
    }
}
```

所以异常会延迟到"预读元素被消费完之后的那一次 `next()`"才爆发。

**这是可接受的**：

- 延迟不代表丢失，异常终究会来；
- 而且那时还没有返回任何"越过修改点"的节点。

本类的 javadoc 专门写了这一节：

> 这是可接受的：延迟不代表丢失，异常终究会来，
> 而且那时还没有返回任何"越过修改点"的节点。

## 4.11 决定十一：为什么 Client 里保留递归打印

### 问题

本项目的主要角色是"用迭代器替换一切手写遍历"。

那 `Client.printTree()` 为什么还用递归？

```java
private static void printTree(WorkflowNode node, String prefix, boolean last, boolean root) {
    ...
    printTree(children.get(i), childPrefix, childLast, false);   // ← 递归
}
```

### 因为这是两个不同的任务

| 任务 | 需要什么 | 合适工具 |
|---|---|---|
| "把整棵树一次画完" | 一次走完、每层带缩进 | **递归** |
| "一个一个处理节点" | 可暂停、可多个游标 | **迭代器** |

**递归天然携带"当前路径"** —— `prefix` 参数就是"我从根走到这里，经过的每一层"。

用迭代器打印树形，要自己维护一个"深度栈"，代码反而更长：

```java
// 用迭代器打印树形的样子（不推荐）
Deque<Integer> depths = new ArrayDeque<>();
for (WorkflowNode node : flow) {
    // 问题：迭代器不告诉你"这个节点在第几层"
    // 你只能靠 parent 关系反推 —— 但节点没有 parent 指针
}
```

**迭代器只回答"下一个是谁"，不回答"它在第几层"。**

### 这是一个设计上的取舍，不是疏漏

如果要让迭代器支持"打印树形"，有两个办法：

**办法 A：让迭代器返回 `(节点, 深度)` 对**

```java
record NodeAtDepth(WorkflowNode node, int depth) { }

public Iterator<NodeAtDepth> iterator() { ... }
```

**接口变复杂了。** 而且绝大多数调用方不关心深度。

**办法 B：给节点加 parent 指针**

```java
record StepNode(String name, StepType type, BranchNode parent) { ... }
```

**节点变成可变的**（parent 要在构造子节点之后才能设置），
而且引入循环引用（`parent` ↔ `children`），序列化会死循环。

**两个办法都是"为了一个场景，污染整个模型"。**

所以本项目的选择是：

> 迭代器保持最小（只回答"下一个是谁"）；
> 需要结构信息的场景，用递归。

### 这条原则的通用形式

**不要为了"统一"而强迫一个工具干它不擅长的事。**

迭代器擅长"线性推进"，递归擅长"分治 + 携带上下文"。
两者共存是**健康的**，不是**不彻底**。

如果一份代码里所有遍历都用迭代器，那大概率是过度设计 ——
有些地方 `for` 循环就是最佳选择。

---

## 4.12 十一个决定汇总

| # | 决定 | 备选方案 | 为什么选这个 |
|---:|---|---|---|
| 1 | `WorkflowNode` 接口 | `Object` / 抽象类 | 类型安全 + 不占继承位 |
| 2 | `sealed` | 普通接口 / 枚举 + 万能类 | `switch` 穷尽检查，漏分支编译失败 |
| 3 | `children()` 在节点上 | 迭代器 `switch` 判断类型 | 结构知识只写一次；加 `SequenceNode` 时迭代器零改动 |
| 4 | 三种节点 | 两种（步骤 + 分支） | 表达不了"依次"；三种是最小完备集合 |
| 5 | `implements Iterable` | 只提供 `iterator()` 方法 | 增强 for 只认 `Iterable`；白丢 `forEach` / `Stream` |
| 6 | `TraversalOrder` 枚举 | 两个方法 | 顺序可传值；加新顺序时编译器帮你找调用点 |
| 7 | 显式栈 | 递归 | 可暂停、可多游标、不受方法栈限制 |
| 8 | 反序压栈 | 正序压栈 | 栈后进先出，正序压会导致左右分支颠倒 |
| 9 | `modCount` 在容器里 | 在迭代器里 | 计数器是容器事实；检查逻辑只有一份 |
| 10 | `FilteringIterator` 预读 | 递归 / 返回不满足的元素 | 迭代器单向、还不回去；必须缓存 |
| 11 | `Client` 保留递归 | 全部改用迭代器 | 递归天然携带路径；迭代器不该管深度 |

---

# 5. 代码解析：11 个文件逐一

## 5.0 怎么读这一章

后面的十一节，每个文件都按同一个结构讲：

1. **它为什么存在**（删掉会怎样）；
2. **关键代码逐段拆**（不是贴全部源码，而是挑需要解释的部分）；
3. **和其他文件的关系**（依赖了谁、被谁依赖）。

如果你只想快速理解模式，读 5.7（`Workflow`）、5.8（DFS）、5.9（BFS）三节就够了。

如果你想理解"为什么一个记录类型的空列表要写成静态常量"这种细节，
那这一章是为准备的。

---

## 5.1 StepType：类型是数据，不是名字

### 它为什么存在

因为原项目只能靠 `node.name().contains("审批")` 判断节点类型（问题 7）。

**删掉它会怎样**：回到 2.9 节的探针 5 —— 运营把「总监审批」改成「总监确认」，
所有依赖名字的判断静默失效。

**合并到 `StepNode` 里会怎样**：

```java
// 反例：把类型写成 String 常量
public record StepNode(String name, String type) { }
```

`"APPROVAL"` 和 `"approval"` 和 `"Approve"` 会变成三种不同的类型，
而且没有任何编译期检查。

### 代码

```java
public enum StepType {

    /** 开始节点：流程入口，没有实际动作。 */
    START("开始"),

    /** 审批节点：需要人签字确认，是流程里最需要被单独筛出来的一类。 */
    APPROVAL("审批"),

    /** 通知节点：只发送消息，不阻塞流程。 */
    NOTIFY("通知"),

    /** 结束节点：流程出口。 */
    END("结束");

    private final String label;

    StepType(String label) {
        this.label = label;
    }

    /**
     * 中文名，用于日志与界面文案。
     */
    public String label() {
        return label;
    }
}
```

### 三个设计点

#### 设计点一：枚举值用英文，标签用中文

```java
APPROVAL("审批"),
```

**为什么不让枚举值直接叫「审批」？**

因为枚举常量名会出现在：

- 序列化结果里（`"APPROVAL"` vs `"审批"`）；
- 数据库存储里；
- 日志里；
- 前端接口里。

**英文名是给机器看的标识符，中文标签是给人看的文案。** 两者生命周期不同：

- `APPROVAL` 一旦发布就不能改（改了老数据读不出来）；
- `"审批"` 可以随时改成 `"审批环节"`。

#### 设计点二：为什么自带 label 而不是用 `name()`

`Enum.name()` 返回的是 `APPROVAL`。

如果直接用 `name()` 打印，日志里会出现：

```
审批节点「总监审批」   →    APPROVAL节点「总监审批」
```

很丑。

**为什么不写一个静态方法 `labelOf(StepType)`？**

因为那是把"类型自己的属性"放到了外面。`label` 是 `StepType` 的字段，
放在枚举里最自然。

#### 设计点三：只有四个值

```java
START, APPROVAL, NOTIFY, END
```

**为什么不是更多？** 比如 `CONDITION`（条件节点）、`PARALLEL`（并行节点）。

因为：

- 条件由 `BranchNode` 表达（它是另一种节点，不是一种步骤）；
- 并行还没有业务需求。

**枚举值应该跟着业务语言走，不是跟着"可能会有什么"走。**

它的 javadoc 里写得很清楚：

> 类型是**数据的属性**，不该由名字推断。这和状态模式里
> "能用数据描述的差异不要用类"、观察者模式里"实时标记做成字段"
> 是同一条原则：**把规则写成数据，让编译器帮你检查。**

### 被谁用

| 使用者 | 用途 |
|---|---|
| `StepNode` | 作为字段，决定节点的业务类型 |
| `StepNode.toString()` | 打印时带上类型的中文名 |
| `Client.demoFilter()` | 过滤条件 `step.type() == StepType.APPROVAL` |

---

## 5.2 WorkflowNode：三种节点共用的唯一契约

### 它为什么存在

因为迭代器要遍历的是一棵树，树上有三种不同类型的节点。

**迭代器不能返回 `Object`**（问题 2），所以必须有一个共同类型。

**删掉它会怎样**：三个迭代器都要声明 `Iterator<Object>`，调用方全部强转。

**合并到某个具体节点里会怎样**：那就是 4.4 节讲过的"给 `StepNode` 加子节点"，
语义会崩。

### 代码

```java
public sealed interface WorkflowNode permits StepNode, BranchNode, SequenceNode {

    /**
     * 节点名，用于展示与日志。
     */
    String name();

    /**
     * 直接子节点，按「先左后右」的顺序返回。
     */
    List<WorkflowNode> children();

    /**
     * 是否是叶子节点。
     */
    default boolean isLeaf() {
        return children().isEmpty();
    }
}
```

**只有两个抽象方法 + 一个默认方法。** 这是整份代码里最短的接口，也是最重要的。

### 三个设计点

#### 设计点一：为什么只有 name() 和 children()

见 4.1 节。核心一句话：

> 接口应该只包含"所有实现都真的具备的能力"。

三种节点都有的东西只有"名字"和"孩子"。`StepType` 只有 `StepNode` 有，
条件表达式只有 `BranchNode` 有，所以都不上接口。

#### 设计点二：为什么 children() 返回 List 而不是数组

```java
List<WorkflowNode> children();
```

理由：

1. **数组是可变的** —— `List.of()` 返回的不可变列表能保证迭代器拿到的快照不会被改；
2. **`StepNode` 可以返回共享的空列表** —— `List.of()` 有空实例优化；
3. **`SequenceNode` 需要 `List.copyOf`** —— 数组没有对应的不可变版本。

#### 设计点三：isLeaf() 为什么是默认方法

```java
default boolean isLeaf() {
    return children().isEmpty();
}
```

因为它的定义**完全由 `children()` 推导**，不需要每个实现各写一遍。

**如果写成抽象方法会怎样**：三个节点各写一遍同样的代码：

```java
// StepNode
@Override public boolean isLeaf() { return children().isEmpty(); }
// BranchNode
@Override public boolean isLeaf() { return children().isEmpty(); }   // 永远 false
// SequenceNode
@Override public boolean isLeaf() { return children().isEmpty(); }   // 永远 false
```

三份重复代码，而且改定义时要改三处。

### 一个刻意的"不"

接口的 javadoc 里写了这么一段：

> 这里刻意**不**定义"是不是审批节点"之类的业务判断 ——
> 那属于 `StepNode#type()`，只有步骤节点才有，
> 放到接口上会逼着分支节点也回答一个它没有的概念。

这是"接口膨胀"最常见的触发方式：

1. 有人需要判断"这个节点要不要人签字"；
2. 他顺手在 `WorkflowNode` 上加一个 `needsHuman()`；
3. `BranchNode` 被迫返回 `false`（因为它不是步骤）；
4. 于是"判断节点要不要人签字"这件事，在分支上永远返回 false —— 但它其实应该递归问两个孩子。

**一旦接口上出现了"某些实现只能返回默认值"的方法，就说明这个方法放错了层。**

正确的位置是：

```java
// 在 Client 里，而不是在 WorkflowNode 上
boolean needsHuman(WorkflowNode node) {
    return switch (node) {
        case StepNode s -> s.type() == StepType.APPROVAL;
        case BranchNode b -> needsHuman(b.thenBranch()) || needsHuman(b.elseBranch());
        case SequenceNode q -> q.steps().stream().anyMatch(this::needsHuman);
    };
}
```

**注意这个方法的 `switch` 没有 `default`** —— 因为 `WorkflowNode` 是 `sealed` 的。
这一行 `switch` 是 4.2 节"好处一"的直接应用。

### 被谁用

| 使用者 | 用途 |
|---|---|
| `StepNode` / `BranchNode` / `SequenceNode` | 实现它 |
| `Workflow` | `List<WorkflowNode> roots` |
| 三个迭代器 | `Iterator<WorkflowNode>`，`node.children()` |
| `Client` | `for (WorkflowNode node : flow)` |

---

## 5.3 StepNode：叶子节点

### 它为什么存在

表达"做一件事"。

**删掉它会怎样**：一个只有分支和顺序块的树，无法表达叶子 —— 遍历永远走不到头。

### 代码

```java
public record StepNode(String name, StepType type) implements WorkflowNode {

    private static final List<WorkflowNode> NO_CHILDREN = List.of();

    public StepNode {
        Objects.requireNonNull(name, "节点名不能为 null");
        Objects.requireNonNull(type, "节点类型不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("节点名不能为空白");
        }
    }

    @Override
    public List<WorkflowNode> children() {
        return NO_CHILDREN;
    }

    @Override
    public String toString() {
        return type.label() + "节点「" + name + "」";
    }
}
```

### 三个设计点

#### 设计点一：为什么 children() 返回静态常量

```java
private static final List<WorkflowNode> NO_CHILDREN = List.of();

@Override
public List<WorkflowNode> children() {
    return NO_CHILDREN;
}
```

而不是：

```java
@Override
public List<WorkflowNode> children() {
    return List.of();        // 每次调用都新建一个对象
}
```

**差别在哪？**

`List.of()` 在 JDK 里对空列表**确实**返回同一个共享实例
（`ImmutableCollections.EMPTY_LIST`），所以性能上几乎没差别。

真正的差别在**语义**：

| 写法 | 表达的意思 |
|---|---|
| `return List.of();` | "叶子节点没有孩子"（每次现算） |
| `return NO_CHILDREN;` | "叶子节点没有孩子，这是这个类的事实"（一次确定） |

后者还带来一个好处：**如果将来要改成返回null 或者别的哨兵，只需要改一个地方。**

**注意：这是一个"可写可不写"的优化。** 如果 `StepNode` 只有一个 `children()` 调用点，
两种写法没区别。写出来是为了让读者一眼看到"叶子"这个语义。

#### 设计点二：紧凑构造器做三件事

```java
public StepNode {
    Objects.requireNonNull(name, "节点名不能为 null");
    Objects.requireNonNull(type, "节点类型不能为 null");
    if (name.isBlank()) {
        throw new IllegalArgumentException("节点名不能为空白");
    }
}
```

`record` 的紧凑构造器不需要写参数列表，也不需要写 `this.name = name`
（编译器自动补）。

**为什么要把校验放在这里，而不是留给调用方？**

一个没有名字的节点，`toString()` 会输出：

```
审批节点「null」
```

排查的人会去查"谁把 null 传进来的"—— 而那可能是半个小时前、在另一个类里。

**校验前置到数据入口，错误就发生在犯错的那一行。**

**为什么 null 用 `Objects.requireNonNull`，空白用 `IllegalArgumentException`？**

因为它们是两种不同的错误：

- `null` 是 API 误用（调用方忘了传值）→ `NullPointerException` 是标准反应；
- `""` 或 `"   "` 是**业务规则违反**（传了个看起来是值但没有意义的东西）→ `IllegalArgumentException`。

**异常类型本身就是文档。**

#### 设计点三：toString() 里类型在前

```java
return type.label() + "节点「" + name + "」";
```

输出：

```
审批节点「总监审批」
```

**为什么类型放前面？**

因为遍历输出是一行一行的列表：

```
    4. 顺序块「总监线」(2 步)
    5. 审批节点「总监审批」
    6. 通知节点「通知财务」
```

类型在最左边，形成一列，一眼能看出"这一行是哪种节点"。

如果写成 `「总监审批」审批节点`，眼睛要先扫过名字才能看到类型。

**输出格式是给排查的人用的，不是给代码用的。**

### 被谁用

| 使用者 | 用途 |
|---|---|
| `Client.buildFlow()` | 创建全部 5 个步骤节点 |
| `Client.demoFilter()` | `node instanceof StepNode step && step.type() == StepType.APPROVAL` |
| 三个迭代器 | 通过 `children()` 拿到空列表，自动停止展开 |

---

## 5.4 BranchNode：二选一

### 它为什么存在

表达"判断，走 A 还是 B"。

**删掉它会怎样**：审批流变回一条直线（原项目就是这个状态），
"金额超过 1000"这种规则只能写在业务代码的 `if` 里。

### 代码

```java
public record BranchNode(
        String name,
        String condition,
        WorkflowNode thenBranch,
        WorkflowNode elseBranch
) implements WorkflowNode {

    public BranchNode {
        Objects.requireNonNull(name, "分支名不能为 null");
        Objects.requireNonNull(condition, "条件不能为 null");
        Objects.requireNonNull(thenBranch, "条件成立时走的节点不能为 null");
        Objects.requireNonNull(elseBranch, "条件不成立时走的节点不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("分支名不能为空白");
        }
        if (condition.isBlank()) {
            throw new IllegalArgumentException("条件不能为空白");
        }
    }

    @Override
    public List<WorkflowNode> children() {
        return List.of(thenBranch, elseBranch);
    }

    @Override
    public String toString() {
        return "分支「" + name + "」条件：" + condition;
    }
}
```

### 四个设计点

#### 设计点一：为什么是两分支而不是 `List<WorkflowNode>`

见 4.4 节。一句话：二叉树已经图灵完备，多分支只是语法糖，
而固定两个能让迭代器的边界处理最简单。

#### 设计点二：为什么 thenBranch / elseBranch 不能为 null

原项目里没有这个约束，因为它的节点是数组元素。

树里就不一样了：`children()` 返回 `List.of(thenBranch, elseBranch)`，
`ArrayDeque` **不允许 `null` 元素** —— 一旦有一个是 null，`push` 直接抛 `NullPointerException`。

而且：
- 这个异常会发生在**迭代器内部**，而不是**建树的地方**；
- 从异常栈看不出是哪个分支缺了东西。

**所以约束必须前置。**

"这条分支上暂时没有节点"应该怎么表达？

用一个显式的占位步骤：

```java
new StepNode("无需审批，自动通过", StepType.NOTIFY)
```

**这比 null 好在：它在遍历时会出现在输出里，一眼能看到。**

#### 设计点三：children() 每次新建列表的代价

```java
@Override
public List<WorkflowNode> children() {
    return List.of(thenBranch, elseBranch);
}
```

`record` 不允许额外实例字段，所以没法像 `StepNode` 那样缓存。

看起来每次调用都新建一个两元素列表 —— 但 `List.of(a, b)` 的开销极小：

- 分配一个对象头 + 两个引用（约 24 字节）；
- 年轻代分配，GC 成本低。

**为什么没有为此放弃 record 改成普通类？**

因为代价不对称：

| 方案 | 成本 | 收益 |
|---|---|---|
| record + 每次新建 | 每次遍历多分配 n 个对象（n = 分支数） | 不可变性、零样板代码 |
| 普通类 + 缓存列表 | 无额外分配 | 多 40 行样板代码，且要自己保证不可变 |

对于一个"一次遍历几百个节点"的审批流，**第一种的开销在总的打印/网络开销面前完全可以忽略**。

类的 javadoc 里写了这个取舍：

> 若将来出现"一次遍历几万个分支节点"的场景，再把
> `BranchNode` 改成手写类并缓存子节点列表即可，
> 接口 `WorkflowNode#children()` 不用动。

**这句话是设计的一部分：它告诉你这个决定的适用边界，以及超出边界时怎么改。**

#### 设计点四：condition 为什么是 String 不是 Predicate

```java
String condition,          // 而不是 Predicate<Order> condition
```

因为节点描述的是**流程长什么样**，不是**怎么算**。

如果把 `Predicate<Order>` 塞进节点：

| 后果 | 说明 |
|---|---|
| 节点无法被序列化 | `Predicate` 是个 lambda，序列化后是一串字节码 |
| 节点无法被打印 | `toString()` 输出 `$$Lambda$14/0x0000000800c01000@1b6d3586` |
| 节点绑死了业务类型 | `Order` 是业务对象，节点变成业务对象的附属品 |
| 节点无法跨进程传输 | 图形化流程编辑器要把流程发给前端 |

**真正的条件求值属于流程引擎**，条件表达式是它的输入。

类的 javadoc 里写到：

> 因为节点描述的是**流程长什么样**，不是**怎么算**。
> 把 `Predicate<Order>` 塞进节点，等于让数据结构
> 依赖业务参数类型，节点就无法被单独打印、序列化、传输。

### 被谁用

| 使用者 | 用途 |
|---|---|
| `Client.buildFlow()` | 创建「金额是否超过 1000」这一个分支 |
| 三个迭代器 | 通过 `children()` 拿到两个分支，展开成两条路 |

**注意：分支的两条路都会被遍历到**（见 5.2 节和 `WorkflowNode` 的 javadoc 第四节）。

---

## 5.5 SequenceNode：依次执行

### 它为什么存在

表达"先做 A，再做 B"。

**删掉它会怎样**：`BranchNode` 的两个分支上只能各放一个节点，
"审批完通知财务"这条链无处安放（4.4 节）。

**这个类是项目的转折点**：它是在两个迭代器写完之后才加入的，
而加入时**两个迭代器一行都没改**。这是 4.3 节论证的直接证据。

### 代码

```java
public record SequenceNode(String name, List<WorkflowNode> steps) implements WorkflowNode {

    public SequenceNode {
        Objects.requireNonNull(name, "顺序块名字不能为 null");
        Objects.requireNonNull(steps, "步骤列表不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("顺序块名字不能为空白");
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("顺序块至少要有一个节点：" + name);
        }

        // 真拷贝：挡住外部列表后续的修改，也让 null 元素在这里就暴露
        steps = List.copyOf(steps);
    }

    @Override
    public List<WorkflowNode> children() {
        return steps;
    }

    @Override
    public String toString() {
        return "顺序块「" + name + "」(" + steps.size() + " 步)";
    }
}
```

### 四个设计点

#### 设计点一：`steps = List.copyOf(steps)` 是紧凑构造器里唯一一次赋值

`record` 的紧凑构造器允许对参数重新赋值：

```java
public SequenceNode {
    ...
    steps = List.copyOf(steps);      // ← 这个值会写入最终的字段
}
```

**这一步非常关键。** 如果只写 `Objects.requireNonNull(steps)` 不拷贝：

```java
List<WorkflowNode> list = new ArrayList<>();
list.add(step1);
SequenceNode seq = new SequenceNode("总监线", list);

list.add(step2);      // ← 外部改动了列表
// seq.steps() 现在有两个元素了！
```

于是：

- 树的结构被外部代码改掉了；
- `Workflow.modCount` 完全不知情（它只统计 `add()`）；
- 迭代器的 fail-fast 检测不到；
- 遍历结果随外部列表变化而变化。

**`List.copyOf` 做的是真拷贝** —— 生成一个新的、不可变的列表。

**注意区分 `List.copyOf` 和 `Collections.unmodifiableList`**：

| 方法 | 是否拷贝 | 外部改动内部列表会怎样 |
|---|---|---|
| `List.copyOf(list)` | **是** | 不影响新列表 |
| `Collections.unmodifiableList(list)` | **否**（只是视图） | **新列表也跟着变** |

这是观察者模式那份文档里踩过的同一个坑。**"不可变"和"不可修改视图"是两回事。**

#### 设计点二：为什么不允许空的顺序块

```java
if (steps.isEmpty()) {
    throw new IllegalArgumentException("顺序块至少要有一个节点：" + name);
}
```

因为空顺序块会造成一个**误导性的输出**：

```
    4. 顺序块「总监线」(0 步)
```

看起来"这里有一个环节"，点进去什么都没有。

排查的人第一反应是"遍历器漏了节点"，而不是"这里本来就是空的"。

**如果业务上真的需要"这一步可以跳过"，那就用一个显式的步骤来表达：**

```java
new StepNode("无需审批，自动通过", StepType.NOTIFY)
```

**让"空"这件事在数据上可见，而不是用空集合让结构变得不可信。**

#### 设计点三：children() 直接返回字段

```java
@Override
public List<WorkflowNode> children() {
    return steps;
}
```

不需要再 `List.copyOf` —— 因为字段本身已经是 `List.copyOf` 的结果。

**对比 `BranchNode`：**

| 类 | children() | 原因 |
|---|---|---|
| `StepNode` | 返回共享的 `NO_CHILDREN` | 空列表，常量 |
| `BranchNode` | 每次 `List.of(a, b)` | record 不能加实例字段 |
| `SequenceNode` | 直接返回字段 | 字段已在构造时拷贝过 |

**三个类三种写法，但对外承诺完全一致：返回的列表都不可变。**

这就是为什么"必须不可变"这条约束要写在**接口**的 javadoc 上，而不是各个实现里。

#### 设计点四：为什么 toString 要带步数

```java
return "顺序块「" + name + "」(" + steps.size() + " 步)";
```

输出：

```
顺序块「总监线」(2 步)
```

**为什么不是简单地输出名字？**

因为在树形打印里，顺序块和步骤节点长得太像：

```
├─ 顺序块「总监线」
├─ 审批节点「总监审批」
```

一个读者可能以为「总监线」就是个普通步骤。

带上 `(2 步)` 之后：

```
├─ 顺序块「总监线」(2 步)
│  ├─ 审批节点「总监审批」
│  └─ 通知节点「通知财务」
```

**一眼能看出"它下面还有东西"。**

### 被谁用

| 使用者 | 用途 |
|---|---|
| `Client.buildFlow()` | 创建三个顺序块：`报销主流程`、`总监线`、`主管线` |
| 三个迭代器 | 通过 `children()` 拿到全部步骤，依次展开 |

---

## 5.6 TraversalOrder：顺序是一个值

### 它为什么存在

因为 `Workflow` 要暴露"两种遍历顺序"（4.6 节）。

**删掉它会怎样**：`Workflow` 变成两个方法 `depthFirstIterator()` / `breadthFirstIterator()`，
调用方在需要动态选择时得写 `if / else`。

**它在模式里的地位**：**不是模式的一部分**。GoF 的迭代器类图里没有这个东西。
它是这个业务场景的具体选择。

类的 javadoc 专门写了一句：

> 注意：这个枚举不是模式的一部分。

### 代码

```java
public enum TraversalOrder {

    DEPTH_FIRST("深度优先"),

    BREADTH_FIRST("广度优先");

    private final String label;

    TraversalOrder(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
```

### 两个设计点

#### 设计点一：只有两个值，为什么还要写枚举

因为**两个**就是"会变成更多"的起点。

如果只有一种顺序，连枚举都不需要。

而一旦有第二种，就意味着：

- 调用方可能需要在运行时选；
- 将来可能加第三种。

**枚举让"加第三种"变成一次显式的、编译器能检查的改动。**

#### 设计点二：枚举常量上的 javadoc 说明了业务语义

```java
/**
 * 深度优先：沿着一条路走到底，再回头走另一条。
 *
 * <p>输出顺序像"顺着流程往下读"：先看成立分支的整条链路，
 * 再看不成立分支。适合人工核对某一条业务路径。
 */
DEPTH_FIRST("深度优先"),
```

**为什么"深度优先"这四个字不足以作为文档？**

因为它只说了算法名，没说**什么场景该用它**。

真实的选择场景是：

| 谁 | 想干什么 | 该选哪个 |
|---|---|---|
| 财务 | "我要顺着一条路径核对下来" | 深度优先 |
| 财务 | "我要看这个流程分了几层" | 广度优先 |
| 审计 | "我要看所有审批环节" | 无所谓（后面还有过滤） |
| 流程设计器 | "我要画图，按层布局好看" | 广度优先 |

**文档应该回答"什么时候用它"，而不只是"它叫什么"。**

### 被谁用

| 使用者 | 用途 |
|---|---|
| `Workflow.iterator(TraversalOrder)` | `switch` 决定创建哪个迭代器 |
| `Workflow.iterator()` | 默认传 `DEPTH_FIRST` |
| `Client.demoTwoOrders()` | 显式传两种顺序做对比 |
| `Client.demoFilter()` | 用两种顺序各过滤一次 |

---

## 5.7 Workflow：容器只提供结构，不提供顺序

### 它为什么存在

它是 `ConcreteAggregate`（具体聚合）：存数据、建树、产出迭代器。

**删掉它会怎样**：没有容器，迭代器无从遍历。

**它的设计目标**：`Workflow.java` 里**不能有一行遍历逻辑**。

这一点做到了 —— 它的 63 行代码里，没有一个 `stack`、没有一个 `queue`、
没有一个 `index++`。

### 类的骨架

```java
public final class Workflow implements Iterable<WorkflowNode> {

    private final String name;
    private final List<WorkflowNode> roots = new ArrayList<>();
    private int modCount;

    public Workflow(String name) { ... }
    public Workflow add(WorkflowNode node) { ... }
    public Workflow addAll(List<WorkflowNode> nodes) { ... }
    public List<WorkflowNode> roots() { ... }
    public int rootCount() { ... }
    public String name() { ... }

    @Override
    public Iterator<WorkflowNode> iterator() { ... }
    public Iterator<WorkflowNode> iterator(TraversalOrder order) { ... }

    int modCount() { ... }
    void checkForComodification(int expectedModCount) { ... }

    @Override
    public String toString() { ... }
}
```

### 八个设计点

#### 设计点一：`implements Iterable<WorkflowNode>`

```java
public final class Workflow implements Iterable<WorkflowNode> {
```

这是整份代码里最重要的一个 `implements`。理由见 4.5 节。

**一个具体的量化收益**：这一行让 `Client` 里这两段代码成为可能：

```java
for (WorkflowNode node : flow) { ... }                      // 增强 for
printAll("传入 Workflow", flow);                            // 传给 Iterable 参数
```

**如果删掉它**，上面两行全部编译失败。

#### 设计点二：`final class`

```java
public final class Workflow implements Iterable<WorkflowNode> {
```

**为什么是 `final`？**

因为 `Workflow` 的构造器里没有调用任何可被重写的方法，加上 `final` 之后：

1. 编译器可以做**去虚化**优化（比如 `checkForComodification` 的调用可以直接静态绑定）；
2. **JIT 更容易内联**；
3. 并发的 `this-escape` warning（`-Xlint:this-escape`）彻底消失。

第 3 点很实际：JDK 21+ 的 `-Xlint:all` 会检查"构造器里是否把 `this` 泄露出去"。
一个非 `final` 的类，如果构造器调用了非 `private` 方法，就会报警 ——
因为子类可能重写它，从而在对象还没构造完时就被调用。

**`final` 是消除这类警告最干净的方式。**

#### 设计点三：roots 用 `ArrayList` 而不是数组

```java
private final List<WorkflowNode> roots = new ArrayList<>();
```

**这是问题 4（容量满静默丢弃）的修复。**

原项目：

```java
private final ProcessNode[] nodes;
private int size;

public Workflow(int capacity) {
    nodes = new ProcessNode[capacity];
}

public void addNode(ProcessNode node) {
    if (size < nodes.length) {          // ← 满了就什么也不做
        nodes[size++] = node;
    }
}
```

本项目：

```java
public Workflow add(WorkflowNode node) {
    Objects.requireNonNull(node, "节点不能为 null");
    roots.add(node);         // ← 永远成功
    modCount++;
    return this;
}
```

**`ArrayList` 自动扩容**，没有"满了"这回事。

如果一个流程真的有节点数上限，那也应该显式失败：

```java
if (roots.size() >= MAX_NODES) {
    throw new IllegalStateException("单个审批流最多 " + MAX_NODES + " 个节点");
}
```

**容量限制可以是业务规则，但绝不能是静默的意外。**

#### 设计点四：`add()` 返回 `this`

```java
public Workflow add(WorkflowNode node) {
    ...
    return this;
}
```

**为什么不是 `void`？**

为了链式调用：

```java
Workflow flow = new Workflow("小流程")
        .add(step("开始", StepType.START))
        .add(step("审批", StepType.APPROVAL));
```

和 `void` 版本对比：

```java
Workflow flow = new Workflow("小流程");
flow.add(step("开始", StepType.START));
flow.add(step("审批", StepType.APPROVAL));
```

**前者读起来像"一句话"，后者像"三条命令"。**

类的 javadoc 里明确说了这个方法的定位：

> 这是本项目在 API 层面唯一"额外加的东西"，它和迭代器
> 模式无关，纯粹是为了让 `Client` 里的建树代码短一点。

**说清"这个设计不是模式的一部分"，比假装它是模式的一部分更重要。**

#### 设计点五：`roots()` 返回 `List.copyOf`

```java
public List<WorkflowNode> roots() {
    return List.copyOf(roots);
}
```

**这一行是快照的来源。** `DepthFirstIterator` 的构造器里：

```java
List<WorkflowNode> roots = workflow.roots();      // ← 真拷贝
for (int i = roots.size() - 1; i >= 0; i--) {
    stack.push(roots.get(i));
}
```

从这一刻起，迭代器手里的根节点列表和 `Workflow` 内部那个**没有任何关系**。

**如果不拷贝会怎样？**

```java
// 反例
public List<WorkflowNode> roots() {
    return roots;                     // 直接交出内部列表
}

// 调用方
flow.roots().clear();                 // ← 一句话清空整棵树
// modCount 不知道，迭代器的 fail-fast 检测不到
```

类的 javadoc 里写了这个场景：

> 若直接把内部列表交出去，调用方一个 `clear()`
> 就能把迭代器脚下抽空，而 `modCount` 却毫不知情。

**注意：`List.copyOf` 是浅拷贝** —— 它只复制引用，不复制 `WorkflowNode` 对象。

这在这是**安全的**，因为 `WorkflowNode` 的三种实现都是 `record`（不可变）。
所以"共享同一个节点对象"和"复制节点"没有区别。

**如果节点是可变的，这里就必须深拷贝。** 这一点要记住。

#### 设计点六：`rootCount()` 而不是 `size()`

```java
public int rootCount() {
    return roots.size();
}
```

**为什么不叫 `size()`？**

因为 `size()` 通常暗示"元素总数"。但这里返回的是**根节点数**，不是树里的节点总数。

树里的节点总数要遍历才知道：

```java
// 想知道总节点数，只能遍历
int total = 0;
for (WorkflowNode node : flow) {
    total++;
}
```

**一个误导性的方法名，比一个没有方法更糟。**

类的 javadoc：

> 注意它**不是**节点总数：整棵树的节点数要遍历才知道，
> 因为分支下面的节点只有走过 `children()` 才数得清。

#### 设计点七：`iterator()` 每次返回新实例

```java
@Override
public Iterator<WorkflowNode> iterator() {
    return iterator(TraversalOrder.DEPTH_FIRST);
}

public Iterator<WorkflowNode> iterator(TraversalOrder order) {
    Objects.requireNonNull(order, "遍历顺序不能为 null");
    return switch (order) {
        case DEPTH_FIRST -> new DepthFirstIterator(this);
        case BREADTH_FIRST -> new BreadthFirstIterator(this);
    };
}
```

**`new` 是关键。** 每次调用都是全新对象，所以：

```java
Iterator<WorkflowNode> first = flow.iterator();
Iterator<WorkflowNode> second = flow.iterator();
// first 和 second 完全独立
```

**如果返回同一个实例会怎样？**

```java
// 反例：缓存一个迭代器
private final Iterator<WorkflowNode> cached = new DepthFirstIterator(this);

@Override
public Iterator<WorkflowNode> iterator() {
    return cached;                     // ← 灾难
}
```

后果：

```java
for (WorkflowNode n : flow) { }        // 第一次：走完，游标到底
for (WorkflowNode n : flow) { }        // 第二次：什么都不输出
```

**第二次循环一行都不会执行，而且不报错。**

这是 `Iterable` 契约里最重要的一条，但很少有人显式写出来：

> `iterator()` 必须返回**新的**迭代器实例。

#### 设计点八：`switch` 没有 `default`

```java
return switch (order) {
    case DEPTH_FIRST -> new DepthFirstIterator(this);
    case BREADTH_FIRST -> new BreadthFirstIterator(this);
};
```

`TraversalOrder` 是枚举，`switch` 覆盖了所有值，所以：

- 不需要 `default`；
- 在 `TraversalOrder` 里加第三个值时，**这一行立刻编译失败**。

这是 4.2 节和 4.6 节反复强调的同一个技巧：**用类型系统把"漏了一个分支"变成编译错误。**

### 两个"内部协议"方法

#### `int modCount()`：包级可见

```java
int modCount() {
    return modCount;
}
```

**没有 `public`。** 理由见 4.9 节：防止被当成业务版本号。

**为什么需要这个方法？** 因为迭代器在构造时要读一次初始值：

```java
this.expectedModCount = workflow.modCount();
```

如果 `DepthFirstIterator` 和 `Workflow` 不在同一个包里，这个方法就得是 `public`。

**包级可见是"最小暴露"的落点**：够用，但不给外部。

#### `void checkForComodification(int)`：包级可见

```java
void checkForComodification(int expectedModCount) {
    if (modCount != expectedModCount) {
        throw new ConcurrentModificationException(
                "遍历期间审批流被修改：迭代器创建时修改了 "
                        + expectedModCount + " 次，现在是 " + modCount + " 次");
    }
}
```

**为什么这段检查在容器里，而不是每个迭代器各写一份？**

因为两个迭代器的检查逻辑**必须完全一致**。各写一份很容易出现：

- DFS 检查了，BFS 忘了；
- 或者两边的异常消息不同。

类的 javadoc：

> 把这段检查放在容器里而不是每个迭代器里，是因为
> 两个迭代器的检查逻辑必须完全一致 —— 否则会出现
> "深度优先会抛异常、广度优先不会"这种更难查的分裂行为。

### 一张图看清职责边界

```
┌──────────────────────────────────────────┐
│ Workflow                                 │
│                                          │
│  ✅ 存根节点（roots）                     │
│  ✅ 记录修改次数（modCount）              │
│  ✅ 产出迭代器（按 TraversalOrder 分派）  │
│  ✅ 提供 fail-fast 检查                   │
│                                          │
│  ❌ 不记录遍历进度                        │
│  ❌ 不知道有栈 / 队列                     │
│  ❌ 不知道顺序长什么样                    │
└──────────────────────────────────────────┘
                 │ 创建
     ┌───────────┼───────────┐
     ▼           ▼           ▼
  DFS迭代器   BFS迭代器   （将来更多）
  ✅ 栈       ✅ 队列
  ✅ 反序压栈  ✅ 正序入队
```

**"❌"那三行，就是这个模式的核心价值。**

---

## 5.8 DepthFirstIterator：显式栈与反序压栈

### 它为什么存在

实现"一条路走到底"。

**删掉它会怎样**：`TraversalOrder.DEPTH_FIRST` 分支没有实现，`switch` 编译失败。

### 类的骨架

```java
public final class DepthFirstIterator implements Iterator<WorkflowNode> {

    private final Deque<WorkflowNode> stack = new ArrayDeque<>();
    private final Workflow workflow;
    private final int expectedModCount;

    public DepthFirstIterator(Workflow workflow) { ... }

    @Override
    public boolean hasNext() { ... }

    @Override
    public WorkflowNode next() { ... }
}
```

**三个字段，两个方法。** 整个深度优先就这么多。

### 七个设计点

#### 设计点一：`final class`

```java
public final class DepthFirstIterator implements Iterator<WorkflowNode> {
```

三个理由：

1. **没有继承的打算** —— 迭代器是最终产物，不是模板；
2. **`final` 让 JIT 能直接内联 `hasNext()` / `next()`** —— 在热循环里这很关键；
3. **防止有人继承之后重写 `next()` 破坏 fail-fast 契约**。

#### 设计点二：用 `ArrayDeque` 而不是 `Stack`

```java
private final Deque<WorkflowNode> stack = new ArrayDeque<>();
```

`java.util.Stack` 是一个**从 Java 1.0 留下来的类**，它有三个问题：

| 问题 | 说明 |
|---|---|
| 继承自 `Vector` | 所有方法都 `synchronized`，单线程下是纯负担 |
| 属于遗留 API | JDK 文档明确建议用 `Deque` 替代 |
| 允许 `null` 元素 | 而 `ArrayDeque` 不允许 —— 这正好帮我们多加一道防线 |

JDK 的 `ArrayDeque` javadoc 原话：

> This class is likely to be faster than `Stack` when used as a stack,
> and faster than `LinkedList` when used as a queue.

**用 `ArrayDeque` 的两个身份**：

- 在 DFS 里当栈（`push` / `pop`）；
- 在 BFS 里当队列（`addLast` / `pollFirst`）。

**同一个类型，两种用法** —— 这也是 5.9 节能只换三行代码的原因。

#### 设计点三：`workflow` 字段是显式声明的

```java
private final Workflow workflow;
```

对比原项目的 `this$0`（2.8 节）。

**为什么本项目还需要这个字段？**

因为 `next()` 要用它做 fail-fast 检查：

```java
workflow.checkForComodification(expectedModCount);
```

**它是显式的、有明确用途的、可被审查的。**

**能不能不要这个字段？**

能。把检查逻辑挪进迭代器，或者把 `modCount` 变化通过回调传进来。

但那样两个迭代器就要各写一份检查（4.9 节的反例）。

**所以这个字段的存在是"共用检查逻辑"的代价，不是"隐式依赖"的残留。**

#### 设计点四：`expectedModCount` 是快照，不是引用

```java
private final int expectedModCount;

public DepthFirstIterator(Workflow workflow) {
    ...
    this.expectedModCount = workflow.modCount();      // ← 存的是值
}
```

`int` 是基本类型，赋值就是**拷贝值**。

**如果写成对象引用会怎样？**

```java
// 反例
private final Workflow workflow;
// 然后用 workflow.modCount() 直接比较
if (workflow.modCount() != workflow.modCount()) { ... }    // 永远相等
```

**检查必须比较"创建时的值"和"现在的值"。**
如果两边都实时读，就永远对得上，检查形同虚设。

**这是一个非常容易犯的错**：

```java
// 看起来对，实际永远不报错
private int expectedModCount;

public DepthFirstIterator(Workflow workflow) {
    this.expectedModCount = workflow.modCount();
}

private void check() {
    if (workflow.modCount() != expectedModCount) { ... }     // ← 对
}
```

上面这段是**对的**，因为 `expectedModCount` 是构造时存下的值。

错的是这样：

```java
private void check() {
    if (workflow.modCount() != workflow.modCount()) { ... }   // ← 废话
}
```

**判断标准：等号两边，一边必须是"过去的快照"。**

#### 设计点五：构造器里反序压栈

```java
public DepthFirstIterator(Workflow workflow) {
    this.workflow = Objects.requireNonNull(workflow, "审批流不能为 null");
    this.expectedModCount = workflow.modCount();

    List<WorkflowNode> roots = workflow.roots();
    for (int i = roots.size() - 1; i >= 0; i--) {
        stack.push(roots.get(i));
    }
}
```

三件事，顺序不能变：

1. **先赋值 `workflow`** —— 后面两个语句可能用到它；
2. **再抓 `modCount` 快照** —— 必须在任何可能触发修改之前；
3. **最后压栈** —— 反序，保证第一个根节点在栈顶。

**为什么第 2 步必须在第 3 步之前？**

理论上 `roots()` 不会改 `modCount`（它是只读的）。
但**顺序写对了，就不会因为将来给 `roots()` 加缓存而引入 bug**。

这是一个"防御性顺序"：**先做快照，再做任何可能变化的操作。**

#### 设计点六：`hasNext()` 只有一行

```java
@Override
public boolean hasNext() {
    return !stack.isEmpty();
}
```

**没有 `modCount` 检查**（4.9 节的取舍）。

**关键：它也没有展开孩子。**

展开孩子放在 `next()` 里：

```java
public WorkflowNode next() {
    ...
    WorkflowNode node = stack.pop();

    List<WorkflowNode> children = node.children();
    for (int i = children.size() - 1; i >= 0; i--) {
        stack.push(children.get(i));
    }

    return node;
}
```

**为什么展开放在 `next()` 而不是 `hasNext()`？**

因为 `hasNext()` 必须是**无副作用**的：

```java
if (it.hasNext()) {          // 第一次：展开，栈变了
    if (it.hasNext()) {      // 第二次：又展开一次！栈里出现重复节点
        ...
    }
}
```

**如果把展开放进 `hasNext()`，重复调用会导致重复展开。**

对比 `FilteringIterator`（5.10 节）：它的 `hasNext()` **有**副作用，
因为它不得不预读。而 `DepthFirstIterator` 没有这个必要 ——
"栈空不空"这个答案不需要提前消费任何东西。

**这是一个很好的对比：副作用不是迭代器的必然属性，而是具体算法的需要。**

#### 设计点七：next() 的三步顺序

```java
@Override
public WorkflowNode next() {
    workflow.checkForComodification(expectedModCount);       // ① 对账

    if (stack.isEmpty()) {                                    // ② 判空
        throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点");
    }

    WorkflowNode node = stack.pop();                          // ③ 取出

    List<WorkflowNode> children = node.children();            // ④ 展开
    for (int i = children.size() - 1; i >= 0; i--) {
        stack.push(children.get(i));
    }

    return node;
}
```

**为什么 ① 必须在 ③ 之前？**

如果先弹栈再对账：

```java
// 反例
WorkflowNode node = stack.pop();          // ← 栈已经被改了
workflow.checkForComodification(...);     // ← 抛异常
```

后果：调用方 `catch` 之后如果继续用这个迭代器，**那个已经弹出的节点就丢了** ——
它既没被返回，也不在栈里了。

**"检查要在产生副作用之前"是一条通用原则**，不只在迭代器里。

### Demo 里的验证

Demo 1 的输出（第 4-12 行）就是这一节的运行证据：

```
    4. 顺序块「总监线」(2 步)
    5. 审批节点「总监审批」
    6. 通知节点「通知财务」
    7. 顺序块「主管线」(2 步)
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」
```

**注意第 4-6 行和第 7-9 行**：先走完整条「总监线」，再回头走「主管线」。

这就是"一条路走到底"。

如果压栈写反了（正序压），输出会变成：

```
    4. 顺序块「主管线」(2 步)
    5. 审批节点「主管审批」
    6. 通知节点「通知申请人」
    7. 顺序块「总监线」(2 步)
    8. 审批节点「总监审批」
    9. 通知节点「通知财务」
```

**看起来仍然是个合法的深度优先遍历，只是左右反了。** 这就是 4.8 节说的"最隐蔽的 bug"。

---

## 5.9 BreadthFirstIterator：只换了一个数据结构

### 它为什么存在

实现"一层一层往外扩"。

**它存在的最大价值**：证明"换一种遍历方式，容器一个字符都不用改"。

### 代码对比

把两个迭代器的核心代码并排放：

```java
// DepthFirstIterator
private final Deque<WorkflowNode> stack = new ArrayDeque<>();
```

```java
// BreadthFirstIterator
private final Deque<WorkflowNode> queue = new ArrayDeque<>();
```

**同一个类型。** 差别只在怎么用。

```java
// DepthFirstIterator 构造器
for (int i = roots.size() - 1; i >= 0; i--) {
    stack.push(roots.get(i));              // ← 反序 push
}
```

```java
// BreadthFirstIterator 构造器
for (WorkflowNode root : roots) {
    // 正序入队：队列先进先出，第一个根节点第一个被访问
    queue.addLast(root);
}
```

```java
// DepthFirstIterator.next()
WorkflowNode node = stack.pop();
List<WorkflowNode> children = node.children();
for (int i = children.size() - 1; i >= 0; i--) {
    stack.push(children.get(i));           // ← 反序 push
}
```

```java
// BreadthFirstIterator.next()
WorkflowNode node = queue.pollFirst();
// 正序入队：先左后右，同一层按声明顺序展开
queue.addAll(node.children());
```

### 三种差异的对照表

这个表在 `BreadthFirstIterator` 的类注释里也有一份：

| 环节 | DepthFirstIterator | BreadthFirstIterator |
|---|---|---|
| 数据结构 | `Deque` 当栈（后进先出） | `Deque` 当队列（先进先出） |
| 取出 | `pop()`（取栈顶） | `pollFirst()`（取队首） |
| 装入孩子 | 反序 `push()`（后压先出） | 正序 `addLast()`（先加先出） |

**三个环节，三种差异，加起来不到十行代码。**

### 五个设计点

#### 设计点一：`Deque` 的双重身份是便利，也是风险

`Deque` 同时支持两头进出，所以"当栈用"和"当队列用"是同一个类型。

**好处**：两个类的字段声明完全一样，一眼能看出"只换了用法"。

**风险**：**写错了不会报错。**

类的 javadoc 专门警告：

> 比如本类里把 `pollFirst()` 写成 `pollLast()`，
> 代码照样编译、照样跑完、不抛任何异常 —— 只是遍历顺序
> 悄悄变成了"倒着的广度优先"。这类错误只能靠单元测试
> 逐字比对输出来发现，编译器帮不上忙。

**所以我们写了两处显式注释**：

```java
for (WorkflowNode root : roots) {
    // 正序入队：队列先进先出，第一个根节点第一个被访问
    queue.addLast(root);
}
```

```java
// 正序入队：先左后右，同一层按声明顺序展开
queue.addAll(node.children());
```

**注释不是废话，是"这里有一个编译器管不到的坑"的标记。**

#### 设计点二：`pollFirst()` 和 `poll()` 的区别

`ArrayDeque` 有几个取出方法：

| 方法 | 队列空时 | 从哪头取 |
|---|---|---|
| `poll()` | 返回 `null` | **头部**（等价于 `pollFirst()`） |
| `pollFirst()` | 返回 `null` | 头部 |
| `pollLast()` | 返回 `null` | 尾部 |
| `remove()` | **抛 `NoSuchElementException`** | 头部 |
| `removeFirst()` | **抛异常** | 头部 |

本项目用 `pollFirst()`，因为：

1. **它明确说了从哪头取** —— `poll()` 虽然等价，但不够直白；
2. **不抛异常** —— 因为我们在前面已经 `if (queue.isEmpty())` 判过了，
   用 `remove()` 会让"防御性判空"变得多余。

```java
if (queue.isEmpty()) {
    throw new NoSuchElementException("广度优先遍历已经到底，没有下一个节点");
}
WorkflowNode node = queue.pollFirst();
```

**判空 + `pollFirst` 是清晰的组合**：
判空负责给出**带消息的异常**，`pollFirst` 负责**不抛异常地取出**。

#### 设计点三：`addAll` 而不是 `addLast` 循环

```java
queue.addAll(node.children());
```

和显式循环对比：

```java
for (WorkflowNode child : node.children()) {
    queue.addLast(child);
}
```

**两者完全等价。** `ArrayDeque.addAll` 内部就是遍历 + 追加。

用 `addAll` 是因为它更短，而且语义更清楚："把这些孩子按顺序排到队尾"。

**一个细节**：`addAll` 在 `ArrayDeque` 里会做一次批量扩容检查，
比逐个 `addLast` 略快（但在这个规模下可以忽略）。

#### 设计点四：为什么共用 `checkForComodification`

```java
@Override
public WorkflowNode next() {
    workflow.checkForComodification(expectedModCount);      // ← 和 DFS 完全相同
    ...
}
```

**两个迭代器的 fail-fast 行为必须一致。**

类的 javadoc：

> `Workflow#checkForComodification(int)` 是包级方法，
> 只给两个迭代器用。让两边共用同一份实现，是为了避免
> "深度优先会抛、广度优先不抛"这种分裂行为 ——
> 同一棵树的两种走法，对"结构被改了"这件事必须有同一个态度。

#### 设计点五：为什么 `hasNext()` 也不检查 `modCount`

和 `DepthFirstIterator` 一样：

```java
@Override
public boolean hasNext() {
    return !queue.isEmpty();
}
```

**两个迭代器的一致性，也体现在这里**：要么都检查，要么都不检查。
不能一个检查一个不检查 —— 那样调用方无法写出统一的 `try/catch`。

### Demo 里的验证

Demo 2 的广度优先部分：

```
  广度优先 iterator(TraversalOrder.BREADTH_FIRST)：
    1. 顺序块「报销主流程」(2 步)
    2. 开始节点「提交报销单」
    3. 分支「金额是否超过 1000」条件：amount > 1000
    4. 顺序块「总监线」(2 步)
    5. 顺序块「主管线」(2 步)
    6. 审批节点「总监审批」
    7. 通知节点「通知财务」
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」
```

按层拆一下：

| 层 | 输出行 | 节点 |
|---:|---|---|
| 0 | 1 | 报销主流程 |
| 1 | 2, 3 | 提交报销单、金额分支 |
| 2 | 4, 5 | 总监线、主管线 |
| 3 | 6, 7, 8, 9 | 总监审批、通知财务、主管审批、通知申请人 |

**每一层的节点连续出现，层与层之间严格递增。**

**这棵树其实不是完美的二叉树**（`SequenceNode` 有 2 个孩子，
所以「总监线」在第 2 层，它的孩子在第 3 层），
但 BFS 完全不关心这件事 —— 它只按"入队顺序"走。

### 一个容易忽略的事实

`BreadthFirstIterator` 和 `DepthFirstIterator` **互不知道对方存在**。

- 没有共同父类（除了 `Iterator` 接口）；
- 没有共享工具方法（除了 `Workflow.checkForComodification`）；
- 没有继承关系。

**它们是两个完全独立的实现，只是恰好都实现了同一个接口。**

这就是"面向接口编程"最实际的样子：**没有继承树，只有接口。**

---

## 5.10 FilteringIterator：不生产节点，只转发节点

### 它为什么存在

因为真实需求从来不是"遍历所有节点"，而是"遍历**我想要的那些**节点"。

**删掉它会怎样**：`Client` 里要自己写过滤循环：

```java
for (WorkflowNode node : flow) {
    if (node instanceof StepNode s && s.type() == StepType.APPROVAL) {
        System.out.println(node);
    }
}
```

这能工作。但**过滤逻辑散落在每一个调用点**，而且**没法复用**：

- 想"取前 3 个审批节点"？做不到（`for` 循环没有"剩下几个"的概念）；
- 想把"过滤后的结果"传给另一个方法？做不到。

**`FilteringIterator` 让"过滤后的结果"仍然是一个迭代器** ——
所以它还能被继续包装、传递、暂停。

### 类的骨架

```java
public final class FilteringIterator implements Iterator<WorkflowNode> {

    private final Iterator<WorkflowNode> delegate;
    private final Predicate<WorkflowNode> predicate;
    private final String description;

    private WorkflowNode pending;
    private boolean hasPending;

    public FilteringIterator(Iterator<WorkflowNode> delegate,
                             Predicate<WorkflowNode> predicate,
                             String description) { ... }

    private void advance() { ... }

    @Override
    public boolean hasNext() { ... }

    @Override
    public WorkflowNode next() { ... }
}
```

### 六个设计点

#### 设计点一：`delegate` 的类型是 `Iterator` 接口

```java
private final Iterator<WorkflowNode> delegate;
```

**这是整个类最重要的一行。**

因为它接受的是接口，所以：

```java
new FilteringIterator(flow.iterator(TraversalOrder.DEPTH_FIRST), ...)    // ✓
new FilteringIterator(flow.iterator(TraversalOrder.BREADTH_FIRST), ...)  // ✓
new FilteringIterator(someList.iterator(), ...)                          // ✓
new FilteringIterator(anotherFilter.iterator(), ...)                     // ✓（可以套娃）
```

**如果写成具体类型会怎样？**

```java
// 反例
private final DepthFirstIterator delegate;
```

那就只能过滤深度优先。想过滤广度优先，得再写一个类。

**组合数爆炸**：`遍历方式 × 过滤条件`。

类的 javadoc：

> 本类的做法是：**它不生产节点，只转发节点**。
> 构造时接收任意一个 `Iterator<WorkflowNode>`，
> 对外表现得像同一个迭代器，只是把不满足条件的元素
> 悄悄吃掉。

#### 设计点二：为什么需要 `description`

```java
private final String description;
```

它只用在异常消息里：

```java
throw new NoSuchElementException(
        "在「" + description + "」条件下已经没有更多节点");
```

**为什么不直接写死一句话？**

因为过滤迭代器可能被套娃。想象三层过滤：

```java
Iterator<WorkflowNode> it = new FilteringIterator(
        new FilteringIterator(
                flow.iterator(),
                node -> !node.isLeaf(),          "只看非叶子"),
        node -> node instanceof StepNode,        "只看步骤");
```

当最外层抛异常时，如果消息里没有描述，你只知道"没有了"。

带上描述之后：

```
在「只看步骤」条件下已经没有更多节点
```

**一眼知道是哪一层到底了。**

**这是"错误消息应该包含足够上下文"这条原则的一个小应用。**

#### 设计点三：`pending` + `hasPending` 两个字段

```java
private WorkflowNode pending;
private boolean hasPending;
```

**为什么不用 `pending != null` 判断？**

因为 `pending` 的类型是 `WorkflowNode`，理论上它不会是 null（构造器都做了非空校验）。
但**这是一个隐含假设**。

用显式的 `hasPending` 之后：

- 语义清楚："有没有预读的值"；
- 不依赖"节点永远不为 null"这个假设；
- 排查时 `hasPending` 这个名字比 `pending != null` 更容易读懂。

**"用一个布尔标志表达状态"，比"用 null 表达状态"更安全。**

顺便：`next()` 里消费之后会把 `pending` 置空：

```java
WorkflowNode result = pending;
pending = null;
hasPending = false;
return result;
```

**为什么要置 null？** 为了让 GC 能回收那个已经被消费掉的节点引用。

虽然在这个场景下影响很小（节点很快就会被整个树回收），
但**"用完就清空引用"是一个好习惯**，尤其是在迭代器可能被长期持有的场景里（比如分页）。

#### 设计点四：`advance()` 的三件事

```java
private void advance() {
    while (!hasPending && delegate.hasNext()) {
        WorkflowNode candidate = delegate.next();
        if (predicate.test(candidate)) {
            pending = candidate;
            hasPending = true;
        }
    }
}
```

三个细节，每一个都不能少：

**① `!hasPending` 在循环条件里**

```java
while (!hasPending && delegate.hasNext())
```

保证**已经预读过就直接返回**，不会多推一格 delegate。

**如果漏了这一个**：

```java
// 反例
while (delegate.hasNext()) {
    ...
}
```

后果：每次调用 `hasNext()` 都会继续往后吞节点，**把已经预读的覆盖掉**。

具体表现：

```java
it.hasNext();      // 预读到「总监审批」，pending = 总监审批
it.hasNext();      // 又往后吞，pending = 总监审批 被覆盖成「主管审批」
it.next();         // 返回「主管审批」——「总监审批」丢了
```

**这是一个非常隐蔽的 bug**：不报错，只是偶尔漏节点。

**② `delegate.hasNext()` 在循环条件里**

不能写成：

```java
// 反例
while (!hasPending) {
    WorkflowNode candidate = delegate.next();     // ← 到底时炸
    ...
}
```

因为 `delegate.next()` 在到底时会抛 `NoSuchElementException`（JDK 契约），
或者返回 null（原项目的做法）—— 两种都不对。

**必须先用 `hasNext()` 问一次。**

**③ `hasPending` 是最终答案**

循环结束后：

- 如果找到了满足条件的 → `hasPending = true`；
- 如果 delegate 空了 → `hasPending` 还是 `false`。

**不需要额外的返回值**：`advance()` 是 `void`，调用方直接读字段。

#### 设计点五：`next()` 里也调用 `hasNext()`

```java
@Override
public WorkflowNode next() {
    if (!hasNext()) {
        throw new NoSuchElementException(
                "在「" + description + "」条件下已经没有更多节点");
    }
    WorkflowNode result = pending;
    pending = null;
    hasPending = false;
    return result;
}
```

**为什么要调 `hasNext()`？**

因为调用方可能**不先问 `hasNext()` 直接调 `next()`**。

虽然不推荐（JDK 的 `for-each` 总是先问），但它是**合法的**：

```java
Iterator<WorkflowNode> it = ...;
try {
    while (true) {
        System.out.println(it.next());       // ← 不问 hasNext
    }
} catch (NoSuchElementException e) {
    // 遍历结束
}
```

**这是 `Iterator` 契约允许的用法。** 所以 `next()` 必须自己保证 `pending` 有效。

**我们的做法是让 `next()` 复用 `hasNext()` 的逻辑**：

```java
if (!hasNext()) { ... }        // ← advance() 在里面被调用
```

**这是"防御性编程"的一个典型例子：每个方法都自己保证前置条件，不依赖调用顺序。**

#### 设计点六：`hasNext()` 的副作用是不可避免的

```java
@Override
public boolean hasNext() {
    advance();                 // ← 这里推动了 delegate
    return hasPending;
}
```

对比 `DepthFirstIterator.hasNext()`：

```java
@Override
public boolean hasNext() {
    return !stack.isEmpty();   // ← 纯查询
}
```

**为什么这个可以有副作用，那个不能？**

因为**问题本身不同**：

| | 要回答的问题 | 能否不消费就回答 |
|---|---|---|
| DFS | "栈里还有东西吗？" | **能**（看一眼栈就行） |
| 过滤器 | "还有满足条件的节点吗？" | **不能**（必须往后找才知道） |

**"还有没有满足条件的"，这个问题的答案不在当前状态里，而在未来。**

类的 javadoc：

> 这不是缺陷，是**过滤语义的必然代价**。
> 但调用方必须知道一点：`hasNext()` 之后如果不调用
> `next()`，那个已经预读出来的节点会**留在 `pending` 里**，
> 不会丢 —— 下一次 `hasNext()` 会看到 `hasPending` 已为真，
> 直接返回 `true`。

### 和其他迭代器的关系

```
       ┌────────────────────────────┐
       │ Iterator<WorkflowNode>     │
       └─────────────▲──────────────┘
                     │ implements
        ┌────────────┼────────────┐
        │            │            │
   DepthFirst    BreadthFirst  Filtering
                                │ 包装
                                ▼
                        任意 Iterator<WorkflowNode>
```

**注意最下面那条箭头是"包装"，不是"继承"。**

`FilteringIterator` 自己不产生任何节点，它只是**改变了下游看到的东西**。

这是装饰器模式（Decorator）在迭代器上的应用。两者的边界在 8.4 节讨论。

---

## 5.11 Client：六段 Demo 的组织方式

### 它为什么存在

演示所有能力，并且**每一段都对应原项目的一个问题**（2.11 节的表）。

### 结构

```java
public final class Client {

    private Client() { }

    public static void main(String[] args) {
        demoOneTree();                    // 树 + 增强 for + 两个游标
        demoTwoOrders();                  // DFS vs BFS
        demoAlgorithmFollowsContainer();  // Iterable 作为通用协议
        demoFilter();                     // 过滤迭代器
        demoFailFast();                   // 遍历中改结构
        demoExhausted();                  // 到底抛异常
        summary();                        // 六段与六个问题对照
    }
}
```

### 五个设计点

#### 设计点一：`private` 构造器

```java
private Client() {
}
```

**为什么？**

因为 `Client` 是一个工具类，一个实例都不需要创建。

**这不只是"省一个对象"** —— 它向读代码的人传达了一个明确信息：
**这个类只有 `static` 方法，不要 `new` 它。**

而且 JDK 21+ 的 `-Xlint:missing-explicit-ctor` 会对"有隐式 public 构造器的
public 类"报警。加上 `private` 构造器，警告消失。

#### 设计点二：`printedSections` 计数器

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

**为什么需要这个计数器？**

因为最初 `section()` 是这样写的：

```java
private static void section(String title) {
    System.out.println();               // ← 总是先空一行
    System.out.println("[" + title + "]");
}
```

后果：**输出的第一行是一个空行**。

看起来无所谓，但：

1. 引用到文档里的输出块会多一个前导空行；
2. 逐字比对时要专门处理；
3. 更像"调试残留"。

加了计数器之后，第一段不空、后面每段之间空一行：

```
[Demo 1：一棵审批流树，一次增强 for 遍历]
  树形结构（这里用递归打印，只是为了看形状）：
  ...

[Demo 2：同一棵树，两种顺序]
  ...
```

**这是一个很小的细节，但"输出干净"是交付质量的一部分。**

#### 设计点三：`buildFlow()` 只建一次树形状

```java
private static Workflow buildFlow() {
    WorkflowNode directorLine = new SequenceNode("总监线", List.of(
            step("总监审批", StepType.APPROVAL),
            step("通知财务", StepType.NOTIFY)
    ));
    WorkflowNode managerLine = new SequenceNode("主管线", List.of(
            step("主管审批", StepType.APPROVAL),
            step("通知申请人", StepType.NOTIFY)
    ));
    WorkflowNode amountGate = new BranchNode(
            "金额是否超过 1000",
            "amount > 1000",
            directorLine,
            managerLine
    );
    WorkflowNode mainLine = new SequenceNode("报销主流程", List.of(
            step("提交报销单", StepType.START),
            amountGate
    ));
    return new Workflow("差旅报销").add(mainLine);
}
```

**关键点：自下而上构建。**

```java
// ① 先建叶子（两个顺序块）
directorLine, managerLine
// ② 再用叶子建分支
amountGate
// ③ 最后用分支建根
mainLine
```

**为什么不能自上而下？**

因为 Java 里创建对象必须先有参数。

```java
// 反例：想自上而下写
new SequenceNode("报销主流程", List.of(
    step("提交报销单", StepType.START),
    new BranchNode("金额是否超过 1000", "amount > 1000",
        new SequenceNode("总监线", ...),      // 越写越深
        new SequenceNode("主管线", ...)
    )
));
```

**行数差不多，但可读性差很多** —— 嵌套 4 层的括号，很难一眼看出树形。

**先提取局部变量，再用变量名组装**，等价于给每个子树"取了个名字"。

#### 设计点四：`printTree()` 用递归

```java
private static void printTree(WorkflowNode node, String prefix, boolean last, boolean root) {
    String connector = root ? "" : (last ? "└─ " : "├─ ");
    System.out.println("    " + prefix + connector + node);

    List<WorkflowNode> children = node.children();
    for (int i = 0; i < children.size(); i++) {
        boolean childLast = i == children.size() - 1;
        String childPrefix = root ? "" : prefix + (last ? "   " : "│  ");
        printTree(children.get(i), childPrefix, childLast, false);
    }
}
```

**为什么存留递归？** 见 4.11 节。

**这里要讲的是那三个布尔/字符串参数怎么协作：**

| 参数 | 作用 |
|---|---|
| `prefix` | 当前层的缩进（可能包含 `│  ` 竖线） |
| `last` | 当前节点是否是父节点的最后一个孩子（决定用 `└─` 还是 `├─`） |
| `root` | 是否是根（根不画连接线） |

**`last` 的作用**：决定这一行后面要不要画竖线。

```
├─ 顺序块「总监线」(2 步)      ← 不是最后一个，用 ├─
│  ├─ 审批节点「总监审批」      ← 父节点有后续兄弟，缩进里保留 │
│  └─ 通知节点「通知财务」      ← 最后一个，用 └─
└─ 顺序块「主管线」(2 步)      ← 最后一个
```

**`childPrefix` 的那一行是关键**：

```java
String childPrefix = root ? "" : prefix + (last ? "   " : "│  ");
```

- 如果当前是叶子路径的最后一个 → 子节点前面加三个空格（不画竖线）；
- 否则 → 子节点前面加 `│  `（画竖线，表示"上面还有兄弟"）。

**这是画树形结构的经典算法**，值得记住。

**验证一下实际输出：**

```
    顺序块「报销主流程」(2 步)
    ├─ 开始节点「提交报销单」
    └─ 分支「金额是否超过 1000」条件：amount > 1000
       ├─ 顺序块「总监线」(2 步)
       │  ├─ 审批节点「总监审批」
       │  └─ 通知节点「通知财务」
       └─ 顺序块「主管线」(2 步)
          ├─ 审批节点「主管审批」
          └─ 通知节点「通知申请人」
```

**完美对齐。** 注意 `│` 会一直延续到「主管线」之前（因为「总监线」不是最后一个孩子）。

#### 设计点五：`printAll()` 的参数是 `Iterable`

```java
private static void printAll(String title, Iterable<WorkflowNode> source) {
    System.out.println("  " + title + "：");
    int index = 0;
    for (WorkflowNode node : source) {
        index++;
        System.out.printf("    %d. %s%n", index, node);
    }
    System.out.println("    共 " + index + " 个节点");
}
```

**这一个方法签名，是整份文档里"解耦"最直观的证据。**

它不知道 `Workflow` 存在，也不知道 `List` 存在。

```java
printAll("传入 Workflow（一棵树）", flow);          // 一棵树
printAll("传入 List.of(...)（一个扁平列表）", adHoc);  // 一个列表
```

**两行都编译通过、都能跑。**

### 六段 Demo 与六个问题的对应

`summary()` 里的输出：

```
[六段 Demo 对应原项目的六个问题]
  1. 增强 for + 两个独立游标   —— 原项目没实现 Iterable，只能手写 while 加强制类型转换
  2. 同一棵树两种遍历顺序       —— 原项目把遍历写死在数组下标里，只有一种走法
  3. 遍历算法跟着 Iterable 走   —— 原项目的遍历只能用于 Workflow 自己
  4. 迭代器可以再包装           —— 原项目想加筛选只能改容器或复制一份遍历代码
  5. 遍历中改结构立刻报错       —— 原项目没有 modCount，行为未定义
  6. 取到底抛 NoSuchElementException —— 原项目返回 null，错误延后爆发
```

**为什么没有"问题 4 容量满静默丢弃"和"问题 6 非静态内部类"？**

因为这两个问题在新项目里**已经不存在了**：

- 没有容量参数 → 没有"满了"；
- 迭代器是顶层类 → 没有 `this$0`。

**"某个问题没有对应演示"，有时是最好的结果。**

---

# 6. 执行流程：真实输出逐行讲

## 6.0 怎么读这一章

前面五章都在回答"为什么这样设计"。这一章换个方向：**程序跑起来的那一秒，到底发生了什么。**

本章贴的每一行输出都不是手写的，是从 `Client.main()` 的真实运行结果里原样复制的。整份输出的 SHA256 是：

```text
06D82FE51A106D5E692FAF5CD5A21E88D8431EC3888466202C8679E643E74669
```

共 120 行，三次独立运行结果完全一致（命令见附录 B）。这意味着你可以拿本章的任意一个代码块去 diff，一个空格都不该差。

每个小节都是同一个三段式：

| 段 | 内容 | 你该做的事 |
|---|---|---|
| ① 输出 | 从真实输出里截出来的那一段 | 先自己跑一遍，对着看 |
| ② 路径 | 每一行输出由哪几行代码产生 | 在输出和代码之间来回看 |
| ③ 陷阱 | 如果这里写错会怎样 | 记住症状，不是记住结论 |

**建议先跑再读。** 让 120 行输出在屏幕上滚过去一遍，再回来读这一章，效率高得多。读代码和看运行结果是两件不同的事，缺一个都会理解偏。

本章七节的分工：

| 节 | 对应 Demo | 核心问题 |
|---|---|---|
| 6.1 | Demo 1 | 一棵树怎么被"一次增强 for"走完？两个游标为什么互不干扰？ |
| 6.2 | Demo 2 | 同一棵树，DFS 和 BFS 的输出差在哪 4 个位置？ |
| 6.3 | Demo 3 | `printAll` 凭什么同时接受树和列表？ |
| 6.4 | Demo 4 | 过滤器怎么"跳过"元素却不丢失它们？ |
| 6.5 | Demo 5 | 遍历途中改结构，异常在哪一刻爆？为什么 `hasNext()` 不报？ |
| 6.6 | Demo 6 | 空容器遍历为什么零次不抛异常，而手动 `next()` 抛？ |
| 6.7 | 全部 | 一次 `next()` 调用，对象里到底动了哪几个字段？ |

---

## 6.1 Demo 1：一棵树与两个游标

### 6.1.1 真实输出（第 1-30 行）

```text
[Demo 1：一棵审批流树，一次增强 for 遍历]
  树形结构（这里用递归打印，只是为了看形状）：
    顺序块「报销主流程」(2 步)
    ├─ 开始节点「提交报销单」
    └─ 分支「金额是否超过 1000」条件：amount > 1000
       ├─ 顺序块「总监线」(2 步)
       │  ├─ 审批节点「总监审批」
       │  └─ 通知节点「通知财务」
       └─ 顺序块「主管线」(2 步)
          ├─ 审批节点「主管审批」
          └─ 通知节点「通知申请人」

  增强 for 遍历（编译器把它翻成 iterator() + hasNext() + next()）：
    1. 顺序块「报销主流程」(2 步)
    2. 开始节点「提交报销单」
    3. 分支「金额是否超过 1000」条件：amount > 1000
    4. 顺序块「总监线」(2 步)
    5. 审批节点「总监审批」
    6. 通知节点「通知财务」
    7. 顺序块「主管线」(2 步)
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」
  共 9 个节点

  同时开两个游标，各自记各自的进度：
    first  第 1 个：顺序块「报销主流程」(2 步)
    first  第 2 个：开始节点「提交报销单」
    second 第 1 个：顺序块「报销主流程」(2 步)
    first  第 3 个：分支「金额是否超过 1000」条件：amount > 1000
  如果 iterator() 返回同一个实例，上面的 second 会从 first 停下的地方继续
```

这 30 行里有三件独立的事，必须拆开看，否则很容易误以为"树形打印也是迭代器干的"：

| 输出区间 | 干这件事的东西 | 用的是迭代器吗 |
|---|---|---|
| 第 2-11 行 | `printTree()` 递归 | **不是**。纯递归，和 `Iterator` 无任何关系 |
| 第 13-23 行 | 增强 for | 是。走 `Workflow.iterator()` 拿到的 `DepthFirstIterator` |
| 第 25-30 行 | 两次 `flow.iterator()` | 是。两个互相独立的 `DepthFirstIterator` |

### 6.1.2 第 2-11 行：递归打印，和模式无关

先看这段代码（`Client.java` 336-346 行）：

```java
private static void printTree(WorkflowNode node, String prefix, boolean last, boolean root) {
    String connector = root ? "" : (last ? "└─ " : "├─ ");
    System.out.println("    " + prefix + connector + node);

    List<WorkflowNode> children = node.children();
    for (int i = 0; i < children.size(); i++) {
        boolean childLast = i == children.size() - 1;
        String childPrefix = root ? "" : prefix + (last ? "   " : "│  ");
        printTree(children.get(i), childPrefix, childLast, false);
    }
}
```

四个参数各管一件事：

| 参数 | 作用 | 为什么不能用局部变量 |
|---|---|---|
| `node` | 当前要打印的节点 | — |
| `prefix` | 这一行前面已确定的缩进（含前几层留下的竖线） | 每一层都不一样，靠参数往下传 |
| `last` | 当前节点是不是父节点的最后一个孩子 | 决定画 `├─` 还是 `└─`，也决定往下传 `│  ` 还是 `   ` |
| `root` | 是不是根节点 | 根节点不画连接符、不缩进 |

真实输出的缩进是这样一层层算出来的：

| 输出行 | `prefix` 实参 | `last` | `connector` | 最终前缀 |
|---|---|---|---|---|
| 顺序块「报销主流程」 | `""` | true | `""`（root） | 4 空格 |
| ├─ 开始节点「提交报销单」 | `""` | false | `├─ ` | 4 空格 + `├─ ` |
| └─ 分支「金额是否超过 1000」 | `""` | true | `└─ ` | 4 空格 + `└─ ` |
| ├─ 顺序块「总监线」 | `"   "` | false | `├─ ` | 4 + 3 = 7 空格 + `├─ ` |
| │  ├─ 审批节点「总监审批」 | `"   │  "` | false | `├─ ` | 7 空格 + `│  ` + `├─ ` |
| │  └─ 通知节点「通知财务」 | `"   │  "` | true | `└─ ` | 7 空格 + `│  ` + `└─ ` |
| └─ 顺序块「主管线」 | `"   "` | true | `└─ ` | 7 空格 + `└─ ` |
| ├─ 审批节点「主管审批」 | `"      "` | false | `├─ ` | 10 空格 + `├─ ` |
| └─ 通知节点「通知申请人」 | `"      "` | true | `└─ ` | 10 空格 + `└─ ` |

注意第 4 行和第 7 行：`prefix` 都是 `"   "`（3 个空格），**不是** `"│  "`。原因是金额分支自己是父节点的最后一个孩子（`last = true`），它下面不会再有兄弟，所以不需要一条竖线延续下去 —— 这就是 `last ? "   " : "│  "` 这一句的全部含义。

> **这里为什么要专门讲一个和模式无关的递归？**
>
> 因为很多人学完迭代器，会产生一种"所有遍历都该换成迭代器"的错觉。
>
> 这两件事的适用条件完全不同：
>
> | | 递归 | 迭代器 |
> |---|---|---|
> | 适合 | 把整棵树**一次画完** | 一个一个**处理**，可能随时停 |
> | 能否暂停 | 不能（暂停点只能存在方法栈里） | 能（游标是对象） |
> | 能否同时开两个 | 不能（除非自己维护栈） | 能（`new` 两个就行） |
> | 是否暴露结构 | 代码直接碰 `children()` | 只碰 `hasNext()` / `next()` |
>
> 本项目**没有**把 `printTree` 改成迭代器版本，这是 §4.11 那个决定十一的直接体现。该用递归的地方用递归，才是对模式最诚实的态度。

### 6.1.3 第 13-23 行：增强 for 被翻成了什么

`Client.java` 68-74 行只写了一个增强 for：

```java
System.out.println("  增强 for 遍历（编译器把它翻成 iterator() + hasNext() + next()）：");
int count = 0;
for (WorkflowNode node : flow) {
    count++;
    System.out.printf("    %d. %s%n", count, node);
}
System.out.println("  共 " + count + " 个节点");
```

`javac` 看到数组以外的增强 for，会把它改写成语义等价的 `while` 循环。下面这段是**编译器实际生成的形状**（反编译后的等价写法，不是源码）：

```java
Iterator<WorkflowNode> it = flow.iterator();   // ① 只调一次 iterator()
int count = 0;
while (it.hasNext()) {                          // ② 每轮先问
    WorkflowNode node = it.next();              // ③ 再取
    count++;
    System.out.printf("    %d. %s%n", count, node);
}
```

三个必须知道的事实：

1. **`iterator()` 在循环开始前只被调用一次**。所以"每次 for 循环都靠 `iterator()` 造新游标"这件事，天然不会有两个循环共用一个游标的问题。
2. **`next()` 的返回值直接赋给 `WorkflowNode`，不需要强转**。因为 `flow.iterator()` 的返回类型是 `Iterator<WorkflowNode>`，泛型在编译期就把类型定死了。原项目没有泛型，所以必须 `(ProcessNode) iterator.next()`（见 §2.4 的探针 1）。
3. **编译器只认 `Iterable` 或数组**。`Workflow` 如果没有 `implements Iterable<WorkflowNode>`，这四行编译直接失败，报错原文见 §2.3。

输出的 9 行来自 `DepthFirstIterator` 的 9 次 `next()`。第 1 行是"顺序块「报销主流程」"而不是"提交报销单"，这一点值得解释：

> **"9 个节点"数的是什么？**
>
> 树上有 9 个节点，其中 3 个是容器（1 个根顺序块 + 2 个分支下的顺序块）、1 个是分支、5 个是步骤。**容器节点也被吐出来了。**
>
> 这不是 bug。`children()` 的可选实现让"叶子"和"容器"共用同一个类型，DFS 不需要区分它们 —— 它做的事永远是"弹出一个、把它的孩子压回去"。叶子节点 `children()` 返回空列表，压 0 个，自然就没了。
>
> 如果设计成"跳过容器、只吐 `StepNode`"，会发生什么？
>
> - `iterator()` 的返回类型得改成 `Iterator<StepNode>`，那么"打印整棵树"和"只打印步骤"就变成两个不同的 API；
> - `FilteringIterator` 就没用了 —— 因为筛选逻辑被硬编码进了迭代器；
> - `BranchNode` 的"条件"信息永远也遍历不到，而它恰恰是审批流里最重要的信息之一。
>
> 所以这一条可以写成一句判据：**迭代器负责"全部"，过滤负责"子集"。** 容器节点也是节点，先全吐出来，想要哪个子集再包一层。

### 6.1.4 9 个节点的出场顺序：栈的逐步快照

`DepthFirstIterator` 只有一个数据结构：`Deque<WorkflowNode> stack`。9 次 `next()` 就是 9 次"弹出 + 展开"，每次栈的内容如下（**左端是栈顶**）：

| 序号 | `pop()` 出来的 | 它的 `children()` | 压栈后的栈（左=栈顶） |
|---:|---|---|---|
| 构造 | — | — | `[报销主流程]` |
| 1 | 顺序块「报销主流程」 | `[提交报销单, 金额分支]` | `[提交报销单, 金额分支]` |
| 2 | 开始节点「提交报销单」 | `[]` | `[金额分支]` |
| 3 | 分支「金额是否超过 1000」 | `[总监线, 主管线]` | `[总监线, 主管线]` |
| 4 | 顺序块「总监线」 | `[总监审批, 通知财务]` | `[总监审批, 通知财务, 主管线]` |
| 5 | 审批节点「总监审批」 | `[]` | `[通知财务, 主管线]` |
| 6 | 通知节点「通知财务」 | `[]` | `[主管线]` |
| 7 | 顺序块「主管线」 | `[主管审批, 通知申请人]` | `[主管审批, 通知申请人]` |
| 8 | 审批节点「主管审批」 | `[]` | `[通知申请人]` |
| 9 | 通知节点「通知申请人」 | `[]` | `[]`（空，`hasNext()` 变 false） |

对照真实输出，第 1-9 行完全吻合。

**关键在第 4 行到第 5 行之间的那一步。** 第 4 步弹出"总监线"后，它的两个孩子 `[总监审批, 通知财务]` 要压回去。如果**正序**压（`for (int i = 0; i < size; i++)`），栈会变成 `[通知财务, 总监审批, 主管线]`，栈顶是"通知财务"，第 5 步就会先打出"通知财务"再打"总监审批"。

顺序反了，但**不是错**——它仍然是一个合法的深度优先遍历，只是变成了镜像版。真实输出第 5、6 行会是：

```text
    5. 通知节点「通知财务」
    6. 审批节点「总监审批」
```

这看起来也不离谱，但它和"文件系统里 `ls` 出来的顺序""目录树打印的顺序"都对不上，读起来别扭。所以 §4.8 那条"同侧反序、异侧正序"是一个**约定**，不是数学必然：

| 数据结构 | 取出端 | 装入端 | 关系 | 该怎么做 |
|---|---|---|---|---|
| `ArrayDeque` 当栈（`push` / `pop`） | 头部 | 头部 | 同侧 | **反序**装入，才能让第一个孩子先出 |
| `ArrayDeque` 当队列（`addLast` / `pollFirst`） | 头部 | 尾部 | 异侧 | **正序**装入，先进先出自然成立 |

写错不会报错，只是顺序变了。这也是为什么 §6.2 要用"层号单调性"来验证 BFS —— 顺序类的 bug 只能靠顺序类的判据抓。

### 6.1.5 第 25-30 行：两个游标，两份互不干扰的进度

代码只有六行（`Client.java` 76-84 行）：

```java
System.out.println();
System.out.println("  同时开两个游标，各自记各自的进度：");
Iterator<WorkflowNode> first = flow.iterator();
Iterator<WorkflowNode> second = flow.iterator();
System.out.println("    first  第 1 个：" + first.next());
System.out.println("    first  第 2 个：" + first.next());
System.out.println("    second 第 1 个：" + second.next());
System.out.println("    first  第 3 个：" + first.next());
System.out.println("  如果 iterator() 返回同一个实例，上面的 second 会从 first 停下的地方继续");
```

`flow.iterator()` 被调了两次，每次都走 `Workflow.java` 的这一行：

```java
@Override
public Iterator<WorkflowNode> iterator() {
    return iterator(TraversalOrder.DEPTH_FIRST);
}
```

再往下走到 `iterator(TraversalOrder)` 里的 `switch`，`DEPTH_FIRST` 分支返回 `new DepthFirstIterator(this)`。

**`new` 这个词就是这条契约的全部实现。** 两个 `new`，两个对象，两个 `stack`，两个 `expectedModCount`。

真实输出可以验证这一点：

| 调用 | 返回的节点 | 说明 |
|---|---|---|
| `first.next()` 第 1 次 | 报销主流程 | `first` 自己的栈走了 1 步 |
| `first.next()` 第 2 次 | 提交报销单 | `first` 走到第 2 个 |
| `second.next()` 第 1 次 | **报销主流程** | `second` 从自己栈顶开始，**不是**接着 `first` |
| `first.next()` 第 3 次 | 金额分支 | `first` 走到第 3 个，完全没被 `second` 影响 |

如果 `iterator()` 返回同一个缓存实例，`second.next()` 那一行会打出**金额分支**（因为缓存实例已经走完 2 个）—— 而且**不报任何错**。这是最坏的一类 bug：结果错了，程序还在跑。

> **`Iterable` 契约里最重要的一条，恰好是最少被写出来的一条。**
>
> `java.util.Iterable` 的 javadoc 对 `iterator()` 只写了一句 "Returns an iterator over elements of type T."，没说"必须每次返回新实例"。但整个 JDK 都建立在这个约定上：`ArrayList.iterator()` 返回 `new Itr()`，`HashMap.entrySet().iterator()` 返回 `new EntryIterator()`，无一例外。
>
> 这条约定之所以没写进 javadoc，是因为**写了也没法检查**。编译器管不了返回值是不是同一个对象，只能在运行时表现为"第二个 for 循环零输出"或者"输出莫名其妙"。
>
> 本项目用 `Workflow` 的 280 行代码里唯一一处 `new` 来兑现它。这不是巧合，是设计。

### 6.1.6 这一段的三条可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 递归打印和迭代器是两件不同的事，不能互相替代 | `printTree` 与 `for (WorkflowNode node : flow)` 并列存在 | 强行把递归改成迭代器，代码变长 3 倍，还没法画树 |
| 增强 for 是编译期语法糖，只认 `Iterable` / 数组 | §2.3 的 javac 报错原文 | 不实现 `Iterable`，四行 for 编译不过 |
| `iterator()` 每次必须返回新实例 | `first` / `second` 的真实输出 | 返回缓存实例 → 第二次遍历零输出，**且不报错** |

---

## 6.2 Demo 2：同一棵树，两种顺序

### 6.2.1 真实输出（第 32-55 行）

```text
[Demo 2：同一棵树，两种顺序]
  深度优先 iterator(TraversalOrder.DEPTH_FIRST)：
    1. 顺序块「报销主流程」(2 步)
    2. 开始节点「提交报销单」
    3. 分支「金额是否超过 1000」条件：amount > 1000
    4. 顺序块「总监线」(2 步)
    5. 审批节点「总监审批」
    6. 通知节点「通知财务」
    7. 顺序块「主管线」(2 步)
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」

  广度优先 iterator(TraversalOrder.BREADTH_FIRST)：
    1. 顺序块「报销主流程」(2 步)
    2. 开始节点「提交报销单」
    3. 分支「金额是否超过 1000」条件：amount > 1000
    4. 顺序块「总监线」(2 步)
    5. 顺序块「主管线」(2 步)
    6. 审批节点「总监审批」
    7. 通知节点「通知财务」
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」

  两次遍历的输出条数一样，顺序完全不同，而树本身没有被改过一个字段
```

代码只有两行有差别（`Client.java` 101-106 行）：

```java
System.out.println("  深度优先 iterator(TraversalOrder.DEPTH_FIRST)：");
printWithIndex(flow.iterator(TraversalOrder.DEPTH_FIRST));

System.out.println();
System.out.println("  广度优先 iterator(TraversalOrder.BREADTH_FIRST)：");
printWithIndex(flow.iterator(TraversalOrder.BREADTH_FIRST));
```

两行 `printWithIndex` 调用的参数只差一个枚举常量。**树、节点、容器一行没动。**

### 6.2.2 两列对齐：到底哪 4 个位置不同

把两份输出并排，逐行比对：

| 序号 | DFS | BFS | 是否相同 |
|---:|---|---|:---:|
| 1 | 顺序块「报销主流程」 | 顺序块「报销主流程」 | 同 |
| 2 | 开始节点「提交报销单」 | 开始节点「提交报销单」 | 同 |
| 3 | 分支「金额是否超过 1000」 | 分支「金额是否超过 1000」 | 同 |
| 4 | 顺序块「总监线」 | 顺序块「总监线」 | 同 |
| 5 | 审批节点「总监审批」 | **顺序块「主管线」** | 异 |
| 6 | 通知节点「通知财务」 | **审批节点「总监审批」** | 异 |
| 7 | 顺序块「主管线」 | **通知节点「通知财务」** | 异 |
| 8 | 审批节点「主管审批」 | 审批节点「主管审批」 | 同 |
| 9 | 通知节点「通知申请人」 | 通知节点「通知申请人」 | 同 |

9 行里 5 行相同、4 行不同。

**相同的那 5 行容易骗人。** 如果只随手输出看一眼，会误以为"两种顺序差不多"。真正需要解释的是：为什么差异只集中在第 5-7 行？

### 6.2.3 给每个节点标上层号

树只有 4 层：

```text
第 1 层：报销主流程
第 2 层：提交报销单、金额分支
第 3 层：总监线、主管线
第 4 层：总监审批、通知财务、主管审批、通知申请人
```

把层号标到两份输出上：

| 序号 | DFS | DFS 层号 | BFS | BFS 层号 |
|---:|---|:---:|---|:---:|
| 1 | 报销主流程 | 1 | 报销主流程 | 1 |
| 2 | 提交报销单 | 2 | 提交报销单 | 2 |
| 3 | 金额分支 | 2 | 金额分支 | 2 |
| 4 | 总监线 | 3 | 总监线 | 3 |
| 5 | 总监审批 | **4** | 主管线 | **3** |
| 6 | 通知财务 | **4** | 总监审批 | **4** |
| 7 | 主管线 | **3** | 通知财务 | **4** |
| 8 | 主管审批 | **4** | 主管审批 | **4** |
| 9 | 通知申请人 | **4** | 通知申请人 | **4** |

**BFS 的层号序列 `1,2,2,3,3,4,4,4,4` 是单调不减的。DFS 的层号序列 `1,2,2,3,4,4,3,4,4` 不是**（第 7 个是 3，比前面的 4 小）。

这一条差别不只是"看起来不同"，它可以写成一个单元测试断言：

```java
// BFS 的性质：层号永远不回头
int previousDepth = 0;
for (WorkflowNode node : flow.iterator(TraversalOrder.BREADTH_FIRST)) {
    int depth = depthOf(node);
    assert depth >= previousDepth : "BFS 出现了回退，层号 " + previousDepth + " → " + depth;
    previousDepth = depth;
}
```

这个断言的价值在于：**它验证的是"BFS 真的是 BFS"，而不是"输出恰好是这 9 行"。** 后者只能靠人眼比对，前者可以跑一万次。

### 6.2.4 BFS 的队列：9 次 `pollFirst` + `addAll`

`BreadthFirstIterator` 的数据结构也是 `ArrayDeque`，但用法完全不同：`pollFirst()` 取、`addAll()` 加到尾部。**左端是队头**：

| 序号 | `pollFirst()` 出来的 | 它的 `children()` | `addAll` 后的队列（左=队头） |
|---:|---|---|---|
| 构造 | — | — | `[报销主流程]` |
| 1 | 报销主流程 | `[提交报销单, 金额分支]` | `[提交报销单, 金额分支]` |
| 2 | 提交报销单 | `[]` | `[金额分支]` |
| 3 | 金额分支 | `[总监线, 主管线]` | `[总监线, 主管线]` |
| 4 | 总监线 | `[总监审批, 通知财务]` | `[主管线, 总监审批, 通知财务]` |
| 5 | **主管线** | `[主管审批, 通知申请人]` | `[总监审批, 通知财务, 主管审批, 通知申请人]` |
| 6 | 总监审批 | `[]` | `[通知财务, 主管审批, 通知申请人]` |
| 7 | 通知财务 | `[]` | `[主管审批, 通知申请人]` |
| 8 | 主管审批 | `[]` | `[通知申请人]` |
| 9 | 通知申请人 | `[]` | `[]` |

第 5 行是理解 BFS 的关键：**"主管线"排在"总监审批"前面被吐出来**，不是因为它更靠左，而是因为它先入队。

看第 4 步的队列：`[主管线, 总监审批, 通知财务]`。

- "主管线"在第 3 步就入了队（它是金额分支的第二个孩子）；
- "总监审批"和"通知财务"是第 4 步才入队的（它们是总监线的孩子）；
- 队列先进先出 → "主管线"必然在它们前面出来。

这就是"一层走完再走下一层"的全部机制。**没有任何"层号"变量，没有嵌套循环，没有递归** —— 只有一个 `Deque` 和 `addAll`。

### 6.2.5 为什么差异只集中在第 5-7 行

如果只对比"哪几行不同"，会得到一个反直觉的结论：**两种遍历，前 4 行和后 2 行居然完全一样。**

原因在树形：

```text
报销主流程               ← 两个迭代器都从这里开始
├─ 提交报销单            ← 只有一个孩子，顺序必然相同
└─ 金额分支              ← 深度优先会立刻钻进第一个分支
   ├─ 总监线             ← DFS 立刻钻进来；BFS 也排在这（它是第 3 层第一个）
   │  ├─ 总监审批         ← DFS 第 5 个；BFS 因主管线插队被推到第 6 个
   │  └─ 通知财务         ← DFS 第 6 个；BFS 第 7 个
   └─ 主管线             ← DFS 第 7 个；BFS 因先入队升到第 5 个
      ├─ 主管审批
      └─ 通知申请人
```

- **前 4 行相同**：因为前 3 个节点都是"只有一个孩子"或者"第一个孩子"，两种策略在没得选的时候必然一致。
- **第 5-7 行不同**：这是整棵树**唯一一个"两个分支都有后代"的地方**。DFS 钻进去走完再回来，BFS 把兄弟先排上。
- **后 2 行相同**：因为"主管审批""通知申请人"都在最右也最深，DFS 走完总监线后来到这里，BFS 也把它排在最后。

一句话：**差异的规模取决于树的分叉形态，不取决于模式。** 换一棵更宽的树，差异会拉得更大；换一棵链状的树（每个节点只有一个孩子），两种顺序的输出会一模一样。

> **这是一个容易被忽略的测试陷阱。**
>
> 如果拿一棵链状树去测 DFS 和 BFS，两者输出完全相同，会误以为"BFS 没生效"或者"枚举参数被忽略了"。
>
> 判断标准只能是结构：**只有存在"一个节点有 ≥2 个有后代的孩子"时，DFS 和 BFS 才会分出差异。** 本项目那棵树恰好只有一个这样的节点（金额分支），所以差异只有 4 行。

### 6.2.6 "树本身没有被改过一个字段"——这句话要能验证

输出最后一行是一句断言：**两次遍历的输出条数一样，顺序完全不同，而树本身没有被改过一个字段。**

这句话可以被静态验证。打开三个数据类和 `Workflow`，搜 `order`：

| 文件 | 有 `order` 字段吗 | 说明 |
|---|:---:|---|
| `WorkflowNode` | 没有 | 只有 `name()` / `children()` / `isLeaf()` |
| `StepNode` | 没有 | `record StepNode(String name, StepType type)` |
| `BranchNode` | 没有 | `record BranchNode(name, condition, thenBranch, elseBranch)` |
| `SequenceNode` | 没有 | `record SequenceNode(String name, List<WorkflowNode> steps)` |
| `Workflow` | 没有 | 只有 `name` / `roots` / `modCount` |
| `DepthFirstIterator` | **有**（`stack`） | 顺序在这里 |
| `BreadthFirstIterator` | **有**（`queue`） | 顺序在这里 |

顺序**只活在迭代器实例里**。这是 §5.7 那句"容器只提供结构，不提供顺序"的静态证据。

如果反过来，把 `order` 塞进 `Workflow`（比如 `flow.setOrder(BREADTH_FIRST)`），会出什么事？

1. 两次遍历必须**串行**执行，第二次设置的顺序会污染第一次 —— 如果两次遍历之间有别的代码也在用这个 `flow`，它拿到的顺序不可预测；
2. `Workflow` 变成有状态对象，不再是"只描述流程长什么样"的纯数据结构；
3. 两次遍历要同时跑，就得更复杂的"顺序快照"机制，等于把迭代器的活搬到容器里重做一遍。

所以"顺序属于迭代器"这个分界，本质是**把可变状态关进短生命周期的对象里**。迭代器用完就扔，状态跟着扔，永远不可能污染别人。

### 6.2.7 这一段的三条可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 顺序属于迭代器，不属于容器 | 上表 7 个文件里只有 2 个迭代器有顺序字段 | 顺序塞进容器 → 两次遍历互相污染 |
| 栈要反序压、队列要正序加 | §6.1.4 与 §6.2.4 两张表 | 写反不报错，遍历顺序变镜像 |
| 验证遍历顺序要靠结构判据，不能靠人眼比对 | BFS 层号单调不减这个断言 | 用链状树测试，两种顺序相同，误判为 bug |

---

## 6.3 Demo 3：`printAll` 只认 `Iterable`

### 6.3.1 真实输出（第 57-76 行）

```text
[Demo 3：遍历算法跟着 Iterable 走，不跟着 Workflow 走]
  传入 Workflow（一棵树）：
    1. 顺序块「报销主流程」(2 步)
    2. 开始节点「提交报销单」
    3. 分支「金额是否超过 1000」条件：amount > 1000
    4. 顺序块「总监线」(2 步)
    5. 审批节点「总监审批」
    6. 通知节点「通知财务」
    7. 顺序块「主管线」(2 步)
    8. 审批节点「主管审批」
    9. 通知节点「通知申请人」
    共 9 个节点

  传入 List.of(...)（一个扁平列表）：
    1. 审批节点「临时加签」
    2. 通知节点「临时抄送」
    共 2 个节点

  printAll 只声明了 Iterable<WorkflowNode>，
  它既不知道树有多深，也不知道元素存在数组还是链表里
```

同一个方法，同一个方法体，两次调用，输入一个是**树**、一个是**扁平列表**，都打出来了。

### 6.3.2 `printAll` 的方法体只出现了 4 个东西

`Client.java` 371-379 行：

```java
private static void printAll(String title, Iterable<WorkflowNode> source) {
    System.out.println("  " + title + "：");
    int index = 0;
    for (WorkflowNode node : source) {      // ← 唯一一处使用 source
        index++;
        System.out.printf("    %d. %s%n", index, node);
    }
    System.out.println("    共 " + index + " 个节点");
}
```

方法体里能出现的东西，只有这 4 个：

| 出现在方法体里的 | 是什么 |
|---|---|
| `title` | 一个 `String` 参数 |
| `source` | 类型是 `Iterable<WorkflowNode>` 的参数 |
| `index` | 一个 `int` 局部变量 |
| `System.out` | 标准输出 |

**没有 `Workflow`，没有 `DepthFirstIterator`，没有 `List`，没有 `BreadthFirstIterator`。**

`source` 在整个方法体里只被用了一次，就是那个增强 for。换句话说，`printAll` 对 `source` 唯一的要求就是"能被遍历"。

### 6.3.3 两次调用的字节码是一样的

这是本节最重要的一句话：**`printAll("...", flow)` 和 `printAll("...", adHoc)` 编译出来的调用指令是同一条。**

Java 是静态单分派（static single dispatch）：

- 方法的**参数类型**在编译期就被固定成 `Iterable<WorkflowNode>`；
- 运行时不会因为"实际对象是 `Workflow`"就去调用 `Workflow` 里的方法；
- `printAll` 的方法体里能调用的方法，在编译期就锁死为 `Iterable` 接口上声明的那几个（`iterator()`、`forEach()`、`spliterator()`）。

换句话说：

> **即使传进来的对象有 30 个 public 方法，`printAll` 也只有 `iterator()` 这一个入口能碰它。**

这就是接口和"工具箱"的根本区别：

| 视角 | 接口是什么 |
|---|---|
| 实现方视角 | "我承诺提供这几个方法" |
| 调用方视角 | **"我只碰这几个方法，别的我一概不知道也不需要知道"** |

第二种视角才是设计模式真正在用的那个。`printAll` 不关心 `Workflow` 内部是 `ArrayList` 还是 `LinkedList`、是树还是链、有没有 `modCount` —— 它只关心 `iterator()` 返回的东西能 `hasNext()` / `next()`。

### 6.3.4 `List.of(...)` 凭什么也能传

`Client.java` 128-133 行：

```java
List<WorkflowNode> adHoc = List.of(
        step("临时加签", StepType.APPROVAL),
        step("临时抄送", StepType.NOTIFY)
);
System.out.println();
printAll("传入 List.of(...)（一个扁平列表）", adHoc);
```

`List<WorkflowNode>` 能赋给 `Iterable<WorkflowNode>` 形参，是因为 JDK 的继承链：

```text
java.lang.Iterable<T>
    ↑  extends
java.util.Collection<E>
    ↑  extends
java.util.List<E>
    ↑  implements（不可变实现）
java.util.ImmutableCollections.ListN
```

`List.of(...)` 返回的是 `ImmutableCollections.ListN`（JDK 内部类），它实现了 `List`，`List` 继承了 `Collection`，`Collection` 继承了 `Iterable`。**三层继承之后，它"是"一个 `Iterable`。**

这里有一个值得注意的对称性：

| 角色 | 谁写的 `iterator()` | 谁调用的 |
|---|---|---|
| `Workflow`（树） | **我们**（`Workflow.java` 第 5.7 节的 280 行） | `printAll` |
| `List.of(...)`（列表） | **JDK**（`ImmutableCollections` 内部） | 同一个 `printAll` |

`printAll` 不认识任何一方的实现，却能把两方都遍历。因为**协议是 JDK 定的**，双方各自兑现，调用方只认协议。

这就是"面向接口编程"在这个模式里的具体含义 —— 不是"定义一个接口让两个类实现"这么空的话，而是：**一个方法只声明它真正会用到的那个能力，多一个都不声明。**

### 6.3.5 如果 `printAll` 的参数写成 `Workflow`

把签名从 `Iterable<WorkflowNode>` 改成 `Workflow`，会发生什么？

```java
private static void printAll(String title, Workflow source) {   // ← 改这里
    ...
    for (WorkflowNode node : source) { ... }                    // 增强 for 仍然能用
    ...
}
```

三件事立刻发生：

| 后果 | 具体表现 |
|---|---|
| 第 133 行编译失败 | `printAll("...", adHoc)` 报"不兼容的类型: List 无法转换为 Workflow" |
| 临时列表那段要新写方法 | `printList(List<WorkflowNode>)`，方法体和 `printAll` 一字不差 |
| 所有能遍历的东西都得是 `Workflow` | 想打印一个 `Set<WorkflowNode>`、一个数组、一个 `Stream` 收集出来的列表，全部要再加方法 |

这就是"抽象泄漏"（leaky abstraction）：**一个只用到 `iterator()` 的方法，被硬绑在了一个具体容器类型上。**

真实输出第 75-76 行那句"它既不知道树有多深，也不知道元素存在数组还是链表里"，说的就是这件事。它不是在自我表扬，而是在陈述一个**可以静态检查的事实**：打开方法体，找不到任何容器类型。

### 6.3.6 反过来：如果连 `Iterable` 都不实现

那正是原项目。`BehaviorType/IteratorPattern/Workflow.java` 里只有：

```java
public Iterator iterator() {            // ← 自己定义的 Iterator，不是 java.util.Iterator
    return new WorkflowIterator();
}
```

没有 `implements Iterable`，所以：

```java
for (ProcessNode node : wf) { ... }     // ← 编译直接失败
```

javac 的报错原文（§2.3 附录）：

```text
错误: for-each 不适用于表达式类型
  要求: 数组或 java.lang.Iterable
  找到:    Workflow
```

只能退化成手写循环：

```java
Iterator iterator = workflow.iterator();
while (iterator.hasNext()) {
    ProcessNode node = (ProcessNode) iterator.next();     // ← 还必须强转
    System.out.println("Executing node: " + node);
}
```

三处退化，每一处都能在 §2 找到对应的问题编号：

| 退化 | 对应原项目问题 |
|---|---|
| 手写 `while` 循环，每次都要重写一遍 | 问题一：没有实现 `Iterable` |
| 必须 `(ProcessNode)` 强制转换 | 问题二：`next()` 返回 `Object` |
| 不能传给任何带 `<T extends Iterable<X>>` 约束的库函数 | 问题一：拿不到 `forEach` / `StreamSupport` / 任何集合工具 |

第三条最隐蔽。`printAll` 这种"只吃 `Iterable`"的方法，在原项目里**根本写不出来** —— 因为没有任何东西能传进去。

### 6.3.7 这一段的三条可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 方法签名应该只声明真正用到的能力 | `printAll` 用 `Iterable` 而非 `Workflow` | 写成具体类型 → 别的东西传不进来，方法要复制多份 |
| `List` 能传给 `Iterable` 形参，靠的是继承链而非巧合 | `Iterable ← Collection ← List` | 以为要写适配器 → 多写一层无用的包装 |
| 接口对调用方的意义是"我只碰这些"，不是"你能做这些" | `printAll` 编译期就锁死只有 `iterator()` 可调 | 以为接口是功能清单 → 把一堆无关方法塞进同一个接口 |

---

## 6.4 Demo 4：过滤迭代器

### 6.4.1 真实输出（第 78-92 行）

```text
[Demo 4：给迭代器再套一层，只取审批节点]
  在深度优先上过滤：
    1. 审批节点「总监审批」
    2. 审批节点「主管审批」

  在广度优先上过滤（同一个 FilteringIterator 类，只换了被包装的迭代器）：
    1. 审批节点「总监审批」
    2. 审批节点「主管审批」

  两次结果恰好相同：两个审批节点都在最深层，左右顺序也没变。
  过滤条件和遍历顺序是两个正交的维度 —— 组合起来时，
  顺序差异可能被树结构「藏」起来，不一定每次都能看出来。
  另外，七个非审批节点被跳过了但并没有消失：
  FilteringIterator 在 hasNext() 里把它们预读出来再跳过，
  它们仍然在树的原处，换一个条件就能重新被取到
```

9 个节点进，2 个节点出。**7 个被跳过，但一个都没有被删掉。**

代码（`Client.java` 150-164 行）：

```java
Predicate<WorkflowNode> onlyApproval = node ->
        node instanceof StepNode step && step.type() == StepType.APPROVAL;

System.out.println("  在深度优先上过滤：");
printWithIndex(new FilteringIterator(
        flow.iterator(TraversalOrder.DEPTH_FIRST),
        onlyApproval,
        "只取审批节点"));

System.out.println();
System.out.println("  在广度优先上过滤（同一个 FilteringIterator 类，只换了被包装的迭代器）：");
printWithIndex(new FilteringIterator(
        flow.iterator(TraversalOrder.BREADTH_FIRST),
        onlyApproval,
        "只取审批节点"));
```

和 Demo 2 一样，两块代码唯一的差别是传给 `flow.iterator()` 的枚举常量。**`FilteringIterator` 一行没改。**

### 6.4.2 预读的逐步过程

`FilteringIterator` 的核心只有两个字段和一个方法：

```java
private WorkflowNode pending;          // 下一个"合格"的节点，还没交出去
private boolean hasPending;            // pending 里到底有没有东西

private void advance() {
    while (!hasPending && delegate.hasNext()) {
        WorkflowNode candidate = delegate.next();
        if (predicate.test(candidate)) {
            pending = candidate;
            hasPending = true;
        }
    }
}
```

`printWithIndex` 的循环是 `while (iterator.hasNext()) { ... iterator.next(); }`，所以对 `FilteringIterator` 的调用序列是：`hasNext()` → `next()` → `hasNext()` → `next()` → `hasNext()`（false）。

逐步展开（Demo 4 第一段，包装的是 DFS）：

| 调用 | `advance()` 内部读了哪些节点 | 判定结果 | `pending` | 返回 |
|---|---|---|---|:---:|
| `hasNext()` #1 | 报销主流程 | 容器，✗ | null | — |
| | 提交报销单 | START，✗ | null | — |
| | 金额分支 | 容器，✗ | null | — |
| | 总监线 | 容器，✗ | null | — |
| | **总监审批** | APPROVAL，✓ | **总监审批** | **true** |
| `next()` #1 | （不读 delegate，直接交出 pending） | — | null | 总监审批 |
| `hasNext()` #2 | 通知财务 | NOTIFY，✗ | null | — |
| | 主管线 | 容器，✗ | null | — |
| | **主管审批** | APPROVAL，✓ | **主管审批** | **true** |
| `next()` #2 | （不读 delegate） | — | null | 主管审批 |
| `hasNext()` #3 | 通知申请人 | NOTIFY，✗ | null | — |
| | `delegate.hasNext()` 返回 false，循环结束 | — | null | **false** |

内部一共读了 **5 + 3 + 1 = 9** 个节点，正好是整个 DFS 序列。跳过了 `9 - 2 = 7` 个 —— 和输出里那句"七个非审批节点"完全对上。

**`next()` 一次都没碰 `delegate`。** 因为 `hasNext()` 已经把下一个合格节点捧在手里了。这是"预读"这个设计的直接结果。

### 6.4.3 两次结果为什么恰好相同

DFS 序列（9 个）里，两个审批节点分别在第 **5**、第 **8** 位。
BFS 序列（9 个）里，它们分别在第 **6**、第 **8** 位。

位置不同，但**相对顺序相同**：都是"总监审批 → 主管审批"。

原因在树形：

```text
金额分支
├─ 总监线        ← 左（thenBranch）
│  └─ 总监审批
└─ 主管线        ← 右（elseBranch）
   └─ 主管审批
```

- 总监线在左，主管线在右；
- 两个审批节点都在第 4 层，深度相同，左右顺序固定；
- DFS 走"左全部 → 右全部"，BFS 走"第 3 层左 → 第 3 层右 → 第 4 层左 → 第 4 层右"。

**只要两个节点深度相同、左右顺序不变，两种遍历下的相对顺序就一定相同。** 这是树形结构的几何性质，和实现无关。

> **"两次结果相同"是巧合，不是保证。**
>
> 如果树形改成这样：
>
> ```text
> 金额分支
> ├─ 总监线
> │  └─ 复核
> │     └─ 总监审批      ← 第 5 层
> └─ 主管线
>    └─ 主管审批          ← 第 4 层
> ```
>
> DFS 会先出"总监审批"（钻进左子树一路到底），BFS 会先出"主管审批"（第 4 层浅于第 5 层）。**同样的过滤器，两种顺序输出就反了。**
>
> 所以输出里那句"过滤条件和遍历顺序是两个正交的维度 —— 组合起来时，顺序差异可能被树结构「藏」起来"，说的是这件事：**本例的树恰好把差异藏住了。** 不要拿这一例当成"过滤与顺序无关"的证据。

这正是输出第 87-89 行想表达的：**"不一定每次都能看出来"**，不是"永远看不出来"。

### 6.4.4 三个错误写法，以及各自的症状

`FilteringIterator` 一共 202 行，其中真正难写的只有 `advance()` 里那个 `while` 条件。把它改错的方式至少有三种，每种都有独特的症状。

#### 错法 A：不预读，在 `next()` 里循环找

```java
public boolean hasNext() {
    return delegate.hasNext();          // ← 只是转发
}

public WorkflowNode next() {
    while (delegate.hasNext()) {
        WorkflowNode candidate = delegate.next();
        if (predicate.test(candidate)) {
            return candidate;
        }
    }
    throw new NoSuchElementException();
}
```

这段看起来更"直观"：不就是一直取，直到取到合格的吗？

**症状**：`hasNext()` 返回 `true`，紧接着 `next()` 抛 `NoSuchElementException`。

推演一下：DFS 序列剩下 3 个节点 `[通知财务, 主管线, 通知申请人]`，全是非审批。

- `hasNext()` → `delegate.hasNext()` → `true`（还有 3 个）→ 调用方以为"有下一个"；
- `next()` → 循环读 3 个，全不合格 → 抛异常。

调用方看着 `hasNext() == true` 却收到了"没有更多元素"，这在逻辑上自相矛盾。**这是违反 `Iterator` 契约的写法**：契约要求"`hasNext()` 返回 true 时，`next()` 必须能返回一个元素"。

正确写法必须让 `hasNext()` 提前把"未来"看穿。这就是"预读"存在的唯一理由。

#### 错法 B：`advance()` 里漏了 `!hasPending`

```java
private void advance() {
    while (delegate.hasNext()) {        // ← 少了 !hasPending
        WorkflowNode candidate = delegate.next();
        if (predicate.test(candidate)) {
            pending = candidate;
            hasPending = true;
        }
    }
}
```

看起来"多读几个也没关系，反正 pending 会记下合格的"。

**症状**：输出从 2 行变成 1 行，而且丢掉的是**前面**那个。

推演：第一次 `hasNext()` 调用 `advance()`：

- 读到"总监审批" → `pending = 总监审批`，`hasPending = true`；
- 但 `while` 条件只看 `delegate.hasNext()`，栈里还有"通知财务""主管线""主管审批""通知申请人"，**继续读**；
- 读到"主管审批" → `pending` 被**覆盖**成"主管审批"；
- 读到"通知申请人" → 不合格，`pending` 保持"主管审批"；
- `delegate.hasNext()` 变 false，退出。

结果：`pending = 主管审批`，**"总监审批"被覆盖掉了，永远取不到**。输出只剩一行。

这就是 `!hasPending` 必须放在 `while` 条件**第一个位置**的原因 —— 它不是"顺便判断一下"，它是"一旦找到就立刻停手"的刹车。

#### 错法 C：`next()` 里忘记清空 `pending`

```java
public WorkflowNode next() {
    if (!hasNext()) {
        throw new NoSuchElementException();
    }
    return pending;             // ← 忘了 pending = null; hasPending = false;
}
```

**症状**：`next()` 永远返回同一个节点，循环变成死循环（如果调用方不用 `hasNext()` 控制）或者输出重复。

推演：`hasPending` 一直是 `true`，所以下次 `advance()` 一进 `while` 就因为 `!hasPending == false` 直接退出，`pending` 还是老值，`hasNext()` 返回 `true`，`next()` 又返回同一个节点。**无限循环，不报错，CPU 打满。**

真实实现的清空动作是这两行：

```java
WorkflowNode result = pending;
pending = null;             // ← 必须清
hasPending = false;         // ← 必须清
return result;
```

两个字段必须同时清。**只清 `pending` 不清 `hasPending`**，会在 `advance()` 里把 `hasPending` 当成"还有东西"而跳过读取；**只清 `hasPending` 不清 `pending`**，虽然 `advance()` 会重新读，但旧引用会多活一段时间（对 GC 不友好，对值类型无影响）。

### 6.4.5 `hasNext()` 有副作用 —— 这是过滤语义的代价

对比两个迭代器的 `hasNext()`：

| 迭代器 | `hasNext()` 的实现 | 有副作用吗 |
|---|---|:---:|
| `DepthFirstIterator` | `return !stack.isEmpty();` | **没有**。纯查询，看栈就知道 |
| `BreadthFirstIterator` | `return !queue.isEmpty();` | **没有**。同上 |
| `FilteringIterator` | `advance(); return hasPending;` | **有**。会消耗 `delegate`，甚至抛出 `delegate` 的异常 |

为什么过滤器的 `hasNext()` 必须有副作用？

因为它的答案在"未来"。

- DFS 的 `hasNext()` 只需要看"栈里还剩几个" —— 剩下的都在手里，不需要往后找；
- 过滤器的 `hasNext()` 要回答的是"**下一个满足条件的**还有没有" —— 而满足条件的那个可能在 5 个之后，也可能在 500 个之后，也可能根本不存在。不往前读，就不可能知道。

这是"过滤"这个语义本身带来的，不是实现偷懒。

**副作用带来的三个实际影响**：

| 影响 | 具体表现 | 是否可接受 |
|---|---|:---:|
| 重复调用 `hasNext()` 不重复消耗 | 第二次进来 `hasPending` 已为 `true`，`while` 第一个条件就 false，直接返回 | 可接受（且是必须的） |
| 异常提前到 `hasNext()` | 如果 `delegate` 是 fail-fast 的，且遍历途中结构被改，`ConcurrentModificationException` 会在 `hasNext()` 里抛出而不是 `next()` 里 | 可接受（fail-fast 的原则就是尽早失败） |
| 调用方不能"试探性"地问 | 不能写 `if (it.hasNext()) { log("还有"); }` 然后再决定要不要取 —— 因为这一问可能已经推进了 `delegate` | 可接受（问过一次就会记住答案，不会丢节点） |

第三条值得展开：很多人担心"调了 `hasNext()` 却没调 `next()`，预读的节点会不会丢"。

**不会丢。** 因为 `pending` 是实例字段，`hasNext()` 返回后它还在。下次调 `next()` 直接取 `pending`，不会重新读 `delegate`。这是"预读"和"丢弃"的根本区别：

```text
预读：把节点从 delegate 搬到自己的 pending 里，delegate 那边少了，但总数没变
丢弃：跳过节点且不保留，总数变少
```

§2.10 谈到原项目的"把分叉硬塞进数组"时，那种做法才是真的丢弃。`FilteringIterator` 只搬不丢。

### 6.4.6 一行代码证明"包装"而非"继承"

`FilteringIterator` 类的第一行：

```java
public final class FilteringIterator implements Iterator<WorkflowNode> {
```

**`implements Iterator`，不是 `extends DepthFirstIterator`。** 这个选择决定了整个 Demo 4 能成立：

| | 如果 `extends DepthFirstIterator` | 实际做法：`implements Iterator` + 持有 `delegate` |
|---|---|---|
| 能包 DFS 吗 | 能 | 能 |
| 能包 BFS 吗 | **不能**（`BreadthFirstIterator` 不是它的父类） | 能 |
| 能包另一个 `FilteringIterator` 吗 | 不能 | 能 |
| 换一种被包装的对象要改代码吗 | 要，得再写一个类 | 不要，构造器传进去就行 |
| 关系类型 | 继承（is-a，编译期定死） | 组合（has-a，运行期可换） |

这就是 Demo 4 那句"同一个 `FilteringIterator` 类，只换了被包装的迭代器"的技术基础。**换的是构造器实参，不是类。**

用一句话概括：

> **继承让你在编译期多写一个类，组合让调用方在运行期多传一个参数。**

### 6.4.7 这一段的三条可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 过滤器必须预读，否则 `hasNext()` 无法回答"下一个合格的" | §6.4.4 错法 A 的推演 | `hasNext()` 说 true、`next()` 抛异常，自相矛盾 |
| `!hasPending` 必须在 `while` 条件里，且是第一个 | §6.4.4 错法 B 的推演 | 找到第一个后继续读，前面的被覆盖，输出少几个 |
| 包装用组合不用继承，才能包住任意 `Iterator` | `implements Iterator` + 构造器接收 `Iterator` | 继承具体迭代器 → 只能包一种，换一种要新写类 |

---

## 6.5 Demo 5：遍历途中改结构

### 6.5.1 真实输出（第 94-101 行）

```text
[Demo 5：遍历途中改结构，立刻抛 ConcurrentModificationException]
  取了第 1 个：开始节点「开始」
  hasNext() = true（栈里还压着「审批」）
  现在往审批流里追加一个根节点……
  hasNext() 仍然返回 true —— 它只查栈，不查 modCount
  next() 抛出：ConcurrentModificationException
  消息：遍历期间审批流被修改：迭代器创建时修改了 2 次，现在是 3 次
  新加的节点不会出现在这次遍历里 —— 遍历范围在创建迭代器那一刻就固定了
```

这段输出里有**一个看似矛盾的地方**：第 4 行说 `hasNext()` 返回 `true`，第 6 行说 `next()` 抛异常。调用方刚刚被告知"还有下一个"，下一秒就被告知"没有下一个"。

这不是 bug，是刻意的。下面逐步拆。

### 6.5.2 每一刻 `modCount` 与 `expectedModCount` 的值

代码（`Client.java` 187-210 行）：

```java
Workflow flow = new Workflow("小流程")
        .add(step("开始", StepType.START))
        .add(step("审批", StepType.APPROVAL));

Iterator<WorkflowNode> cursor = flow.iterator();
System.out.println("  取了第 1 个：" + cursor.next());
System.out.println("  hasNext() = " + cursor.hasNext() + "（栈里还压着「审批」）");

System.out.println("  现在往审批流里追加一个根节点……");
flow.add(step("中途塞进来的结束节点", StepType.END));

System.out.println("  hasNext() 仍然返回 " + cursor.hasNext()
        + " —— 它只查栈，不查 modCount");
try {
    cursor.next();
    System.out.println("  没有抛异常，这是 bug");
} catch (ConcurrentModificationException e) {
    System.out.println("  next() 抛出：" + e.getClass().getSimpleName());
    System.out.println("  消息：" + e.getMessage());
}
```

九个时刻的字段快照：

| 时刻 | 发生的事 | `flow.modCount` | `cursor.expectedModCount` | 检查结果 |
|---:|---|---:|---:|---|
| ① | `new Workflow("小流程")` | 0 | — | — |
| ② | `.add(step("开始"))` | 1 | — | — |
| ③ | `.add(step("审批"))` | 2 | — | — |
| ④ | `flow.iterator()` 构造 | 2 | **2**（快照） | — |
| ⑤ | `cursor.next()` 第 1 次 | 2 | 2 | `2 == 2` 通过 |
| ⑥ | `cursor.hasNext()` | 2 | 2 | **不检查** |
| ⑦ | `flow.add(step("中途塞进来…"))` | **3** | 2 | — |
| ⑧ | `cursor.hasNext()` | 3 | 2 | **不检查**，返回 true |
| ⑨ | `cursor.next()` 第 2 次 | 3 | 2 | `3 != 2` → **抛 CME** |

第 ④ 行是整件事的起点：`DepthFirstIterator` 构造器里的这一句：

```java
this.expectedModCount = workflow.modCount();      // 快照，不是引用
```

**"快照"这两个字是关键。** 如果写成 `this.workflow = workflow;` 然后每次检查时读 `workflow.modCount()`，那等号两边永远是同一个值，检查形同虚设：

```java
// 错误写法：两边都实时读，永远相等
if (workflow.modCount() != workflow.modCount()) { ... }    // 恒为 false
```

所以 `expectedModCount` 必须是 `int` 字段，在构造器里赋一次值，此后再不改。**等号一边必须是"过去的值"**，这是 fail-fast 成立的唯一前提。

### 6.5.3 为什么 `hasNext()` 不检查，只有 `next()` 检查

`DepthFirstIterator` 的这两行：

```java
@Override
public boolean hasNext() {
    return !stack.isEmpty();               // ← 没有任何检查
}

@Override
public WorkflowNode next() {
    workflow.checkForComodification(expectedModCount);   // ← 只有这里检查
    ...
}
```

这不是本项目自创的规矩，是照着 JDK `ArrayList.Itr` 抄的：

```java
// JDK 21, java.util.ArrayList.Itr
public boolean hasNext() {
    return cursor != size;                 // ← 不检查 modCount
}

public E next() {
    checkForComodification();              // ← 只有这里检查
    ...
}
```

三个理由：

**理由一：`hasNext()` 的语义本来就不保证"一定有"。**

`Iterator` 的契约只规定了一件事：**`next()` 在没有元素时必须抛 `NoSuchElementException`。** 对 `hasNext()` 没有任何"必须准确"的要求 —— 它可以在任何时候返回任何值，只要 `next()` 在没元素时抛异常，就没有违反契约。

换句话说，`hasNext()` 的回答是"我猜还有"，不是"我保证有"。这个措辞听起来很虚，但它恰恰解释了为什么 `hasNext() == true` 后 `next()` 抛异常是合法的。

**理由二：性能。**

增强 for 的循环结构是 `while (it.hasNext()) { it.next(); ... }`，每轮调一次 `hasNext()`。如果要检查 `modCount`，那 `hasNext()` 的调用次数和 `next()` 一样多，检查成本翻倍。而 `next()` 只有一次检查就足够拦住所有问题。

**理由三：时机。**

`hasNext()` 常被用在"只想看一眼"的地方：

```java
if (it.hasNext()) {
    log.debug("还有元素，准备处理");       // ← 只是想打个日志
}
```

如果 `hasNext()` 抛异常，这种只读代码路径会被意外打断。而真正有风险的操作是 `next()` —— 它要返回一个具体对象，如果结构变了，返回什么都不对。

**理由一和理由三合起来，解释了那句"这是刻意与 JDK 保持一致"**（`Client.java` 181-182 行注释）。本项目没有为了"更好用"而偏离标准，因为偏离标准会让所有熟悉 Java 的人踩坑。

### 6.5.4 "新加的节点不会出现"其实有两层原因

输出最后一行说："新加的节点不会出现在这次遍历里 —— 遍历范围在创建迭代器那一刻就固定了。"

这句话包含两个**独立**的机制，很多人只看到第一个：

**第一层：`modCount` 检查会拦住。**

第 ⑨ 步的 `next()` 一进来就抛异常，遍历直接终止。后面不管还有几个节点，都不会再被吐出来。

**第二层：即使没有 `modCount`，新节点也不会出现。**

因为构造器拿的是**快照**：

```java
public DepthFirstIterator(Workflow workflow) {
    this.workflow = Objects.requireNonNull(workflow, "审批流不能为 null");
    this.expectedModCount = workflow.modCount();

    List<WorkflowNode> roots = workflow.roots();     // ← List.copyOf，真拷贝
    for (int i = roots.size() - 1; i >= 0; i--) {
        stack.push(roots.get(i));
    }
}
```

`workflow.roots()` 返回的是 `List.copyOf(roots)`（见 §5.7）。此时 `stack` 里只有"开始"和"审批"两个节点。后续 `flow.add(...)` 只改 `Workflow` 内部的 `roots` 列表，**碰不到 `stack`**。

所以就算把 `checkForComodification` 整个删掉，程序也不会崩 —— 遍历会正常走完"开始"和"审批"两个节点，新加的"中途塞进来的结束节点"不会出现。

> **这是一个非常容易搞混的点，值得单独强调。**
>
> 如果第二层已经保证了"新节点不会出现"，那第一层（fail-fast）的价值是什么？
>
> **它的价值不是"防止错误结果"，而是"告诉你错误发生了"。**
>
> "遍历途中修改结构"这件事本身，往往意味着调用方的逻辑有问题：可能是在循环里误加了元素、可能是两个线程同时操作、可能是回调里改了数据。这些情况下，即使遍历结果"看起来正常"，业务逻辑大概率也已经错了。
>
> fail-fast 的作用是**把这个静默的逻辑错误变成一个响亮的异常**。它不修正结果，它只是不让你在错误的数据上继续跑。
>
> 这也是为什么它叫 fail-**fast**（快速失败）而不是 fail-**safe**（安全失败）。JDK 里相反的做法是 `CopyOnWriteArrayList` 和 `ConcurrentHashMap` 的弱一致迭代器 —— 它们不抛异常，而是给你一份"某一时刻的快照"，代价是内存复制。

### 6.5.5 为什么 `modCount` 必须放在 `Workflow` 里

`Workflow.java` 里那个 `int modCount` 字段和两个方法（包级可见）：

```java
int modCount() {
    return modCount;
}

void checkForComodification(int expectedModCount) {
    if (modCount != expectedModCount) {
        throw new ConcurrentModificationException(
                "遍历期间审批流被修改：迭代器创建时修改了 "
                        + expectedModCount + " 次，现在是 " + modCount + " 次");
    }
}
```

如果把它放进迭代器，会有两个后果：

| 放迭代器里的后果 | 具体表现 |
|---|---|
| 每个迭代器各自加自己的计数 | `DepthFirstIterator` 加到 5，`BreadthFirstIterator` 还是 0 —— 谁都不代表容器的真实状态 |
| 检查逻辑要复制两份 | DFS 里的 `checkForComodification` 和 BFS 里的必须一字不差，否则会出现"DFS 抛异常、BFS 不抛"的分裂 |

而"改了几次"是**容器的事实**，不是迭代器的事实。放容器里，检查逻辑只有一份，两个迭代器共用；放在容器里，`Workflow.add()` 里那一行 `modCount++` 就是唯一的修改入口，不可能漏。

**这就是 §4.9 决定九的全部论证**，在运行时的表现就是第 ⑨ 步那个 `3 != 2`。

还有一个细节值得注意：`modCount()` 和 `checkForComodification()` 都是**包级可见**（没有修饰符），不是 `public`。因为：

- 它们是 `Workflow` 和它的迭代器之间的"内部协议"，外部调用方不需要知道；
- 如果写成 `public`，外部代码就能随意调 `checkForComodification` 制造异常，或者读 `modCount` 做不该做的事；
- 两个类在同一个包里，包级可见刚好够用。

这是 `private` 太紧（迭代器访问不到）、`public` 太松（外部能碰）、**包私有刚刚好**的典型例子。

### 6.5.6 一个反例：如果遍历前先记下 `size`

有些人的第一反应是"那我遍历前先记住 `size`，遍历时比对 `size` 不就行了"：

```java
int expectedSize = workflow.rootCount();        // ← 记住根节点个数
while (it.hasNext()) {
    if (workflow.rootCount() != expectedSize) {
        throw new ConcurrentModificationException();
    }
    ...
}
```

**这个方案有三个漏洞**：

| 漏洞 | 说明 |
|---|---|
| 加法 + 减法会抵消 | 加一个再删一个，`rootCount()` 回到原值，检查发现不了 |
| 只比较根节点个数 | 往某个 `SequenceNode` 里加子节点，`rootCount()` 完全不变 |
| 检查点太多 | 每轮都要读一次 `rootCount()`，性能和代码都难看 |

`modCount` 用的是"**单调递增的修改次数**"，不是"当前元素个数"。**它只增不减，永远不会抵消。** 这就是为什么 JDK 选 `modCount` 而不是 `size`。

> 一个反推：`ArrayList` 有 `size()` 方法，为什么它还用 `modCount`？
>
> 答案就是第一个漏洞。`list.add(x); list.remove(x);` 之后 `size()` 回到原值，但结构确实变了。JDK 用 `modCount` 记录"**改动次数**"而不是"**当前状态**"，才能捕捉到这种"加了又删"的变化。

### 6.5.7 这一段的三条可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| `expectedModCount` 必须是构造时的快照，不能实时读 | §6.5.2 第 ④ 行；错误写法 `modCount() != modCount()` 恒为 false | 实时读 → 检查永远通过，等于没写 |
| `hasNext()` 不检查、`next()` 检查，是标准行为 | JDK `ArrayList.Itr` 与 §6.5.3 三个理由 | 在 `hasNext()` 里检查 → 与 JDK 不一致，只读代码路径被打断 |
| `modCount` 记录"改动次数"，不是"当前元素数" | §6.5.6 三个漏洞 | 用 `size` 比对 → 加法减法抵消，检查失效 |

---

## 6.6 Demo 6：到底了还取

### 6.6.1 真实输出（第 103-112 行）

```text
[Demo 6：到底了还取，抛异常而不是返回 null]
  空审批流，直接遍历零次：
    for 循环体执行了 0 次，没有抛异常
  hasNext() = false
  next() 抛出：NoSuchElementException
  消息：深度优先遍历已经到底，没有下一个节点

  对照原项目：next() 到底返回 null，
  异常会推迟到 node.name() 那一行才爆成 NullPointerException，
  栈顶指向「谁用了 null」，而不是「谁多取了一次」
```

同一个空容器，**增强 for 零次不抛异常**，**手动 `next()` 抛异常**。这两件事必须同时成立，才是一个正确的迭代器。

### 6.6.2 空容器遍历为什么零次、且不抛异常

`Client.java` 222-230 行：

```java
Workflow empty = new Workflow("空流程");
Iterator<WorkflowNode> cursor = empty.iterator();

System.out.println("  空审批流，直接遍历零次：");
int visited = 0;
for (WorkflowNode ignored : empty) {
    visited++;
}
System.out.println("    for 循环体执行了 " + visited + " 次，没有抛异常");
```

增强 for 展开成：

```java
Iterator<WorkflowNode> it = empty.iterator();
int visited = 0;
while (it.hasNext()) {          // ← 第一次就 false，循环体零次
    WorkflowNode ignored = it.next();
    visited++;
}
```

`DepthFirstIterator` 的构造器执行完，`stack` 是空的（`roots` 为空列表，`for` 循环压 0 个）。所以 `hasNext()` 直接返回 `false`，`while` 体一次都不进，`next()` **从未被调用**。

**这是 `Iterator` 契约的标准用法：调用方必须先问再取。** 只要遵守这个规矩，"遍历空容器"永远不会碰到 `NoSuchElementException`。

反过来，`Iterator` 的契约也保证了这一点：`hasNext()` 说 `false` 之后，`next()` 才允许抛异常。**只要 `hasNext()` 是 `false`，调用方本就不该再调 `next()`。**

### 6.6.3 手动越界：抛异常 vs 返回 null

接下来三行是故意违反契约的：

```java
System.out.println("  hasNext() = " + cursor.hasNext());     // false
try {
    cursor.next();                                            // ← 明知 false 还取
    System.out.println("  没有抛异常，这是 bug");
} catch (NoSuchElementException e) {
    System.out.println("  next() 抛出：" + e.getClass().getSimpleName());
    System.out.println("  消息：" + e.getMessage());
}
```

`DepthFirstIterator.next()` 里的第 ② 步拦住了它：

```java
if (stack.isEmpty()) {
    throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点");
}
```

对比原项目的写法：

```java
@Override
public Object next() {
    if (hasNext()) {
        return nodes[index++];
    }
    return null;                       // ← 到底返回 null
}
```

两种做法的差别，不在"这一行怎么写"，而在"错误在什么时候、以什么形式暴露"：

| 维度 | 返回 `null`（原项目） | 抛 `NoSuchElementException`（本项目） |
|---|---|---|
| 错误爆在哪一行 | 调用方**使用返回值**的那一行 | **调用 `next()` 的那一行** |
| 栈顶指向 | "谁用了 null" | "谁多取了一次" |
| 异常类型 | `NullPointerException` | `NoSuchElementException` |
| 原因是否唯一 | 不唯一 —— NPE 可能是别的原因 | 唯一 —— 只有"没有更多元素"会抛它 |
| 能否 `catch` 区分 | 很难（NPE 太泛） | 容易（专门 catch 这一类） |
| 调用方能否分辨"到底了"和"元素是 null" | **不能** | 能 |

最后一行是关键，展开说一下。

**如果 `next()` 用 `null` 表示"没有更多元素"，那"元素本身就是 `null`"和"没有更多元素"就分不清了。** 调用方拿到 `null`，不知道是"该停了"还是"这里真的有个空值"，只能靠额外约定（比如"本容器的元素不允许为 null"）。

本项目的节点确实不允许为 null（`StepNode` / `BranchNode` / `SequenceNode` 的紧凑构造器都会 `Objects.requireNonNull`），但**这是本项目自己加的额外保证，不是 `Iterator` 契约的保证**。原项目没有任何这类检查，`Workflow` 的 `nodes` 数组里完全可以塞 `null`。

探针 2 的证据（§2.5）：

```text
  第 1 次 next()：ProcessNode{name='结束'}
  hasNext() = false
  第 2 次 next()：null
  对返回值取 name() -> NullPointerException
  栈顶是「(ProcessNode) extra」那一行，而不是「多调了一次 next()」那一行
```

`Workflow` 里明明有 4 个节点，栈顶却指向了 `(ProcessNode) extra` —— 这个变量名是探针里随手起的，恰恰说明了问题：**错误现场离出事的地方已经隔了一行。** 如果调用链更长（比如 `node.name()` 藏在某个工具方法里），栈顶会离得更远。

### 6.6.4 为什么 `NoSuchElementException` 是 `RuntimeException`

`NoSuchElementException` 的继承链：

```text
java.lang.Throwable
 └── java.lang.Exception
      └── java.lang.RuntimeException          ← 非受检
           └── java.util.NoSuchElementException
```

它是 `RuntimeException`，不是受检异常。原因是：

**"多取一次"是调用方的编程错误，不是可恢复的运行时状况。**

- 受检异常（`IOException`、`SQLException`）表示"外部环境可能出问题，你必须处理"；
- 非受检异常（`NullPointerException`、`IndexOutOfBoundsException`、`NoSuchElementException`）表示"代码写错了，改代码"。

和 `IndexOutOfBoundsException` 类比就很清楚：

```java
int[] arr = {1, 2, 3};
int x = arr[3];                    // ← ArrayIndexOutOfBoundsException
```

没人会给数组下标访问包 `try-catch` —— 因为正确的代码永远不该越界。`next()` 越界同理：**先 `hasNext()` 再 `next()`** 的代码永远不会触发它。

如果设计成受检异常，每个 `next()` 都要 `try-catch` 或者 `throws`，而 99.99% 的代码根本不会触发 —— 纯粹的形式负担，还会把真正的错误（逻辑写错）伪装成"需要处理的外在状况"。

JDK 的 `Iterator.next()` javadoc 写着 "Throws: `NoSuchElementException` - if the iteration has no more elements"，但你在业务代码里几乎见不到 `catch (NoSuchElementException)`。**这说明它本来就不是给你 `catch` 的。**

### 6.6.5 一个常见的错误补救：`if (next() != null)`

有人看到 `next()` 返回 `null`，会这样补救：

```java
WorkflowNode node = iterator.next();
if (node != null) {
    process(node);
}
```

**这相当于把"到底了"和"元素是 null"两种情况下都静默跳过。** 后果：

| 情况 | 应当的行为 | 实际行为 |
|---|---|---|
| 遍历到底了（不该再调） | 报错，暴露逻辑 bug | 静默跳过，bug 被藏起来 |
| 元素真的是 null（数据问题） | 报错或跳过并记录 | 静默跳过，数据问题被藏起来 |

两类问题都被同一行代码吃掉，日志里什么都看不到。**这就是"用 null 表示异常状态"的典型恶果** —— 它迫使调用方把"正常值"和"异常信号"混在一个 `if` 里处理。

### 6.6.6 这一段的三条可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| `hasNext()` 为 false 后不该再调 `next()` | 增强 for 展开后零次循环 | 明知 false 还取 → 异常（正确行为） |
| `next()` 越界必须抛异常，不能返回 `null` | §6.6.3 对照表最后一行 | 返回 null → 分不清"到底了"和"元素是 null"，错误延后爆发 |
| `NoSuchElementException` 是非受检异常，不该被 `catch` | §6.6.4 与 `arr[3]` 的类比 | 到处 `catch` → 把逻辑 bug 当成外部状况，掩盖问题 |

---

## 6.7 一次 `next()` 的完整时序

前面六节都是从"输出"往回看代码。这一节倒过来：**从一次 `next()` 调用往前推，看它到底动了哪几个字段。**

### 6.7.1 单层：`DepthFirstIterator.next()`

方法体六步（`DepthFirstIterator.java`）：

```java
@Override
public WorkflowNode next() {
    workflow.checkForComodification(expectedModCount);   // ① 对账
    if (stack.isEmpty()) {                                // ② 判空
        throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点");
    }
    WorkflowNode node = stack.pop();                      // ③ 取出
    List<WorkflowNode> children = node.children();        // ④ 展开
    for (int i = children.size() - 1; i >= 0; i--) {
        stack.push(children.get(i));                      // ⑤ 反序压回
    }
    return node;                                          // ⑥ 交出去
}
```

以 Demo 1 的**第 3 次** `next()`（弹出"金额分支"）为例，逐行跟踪字段：

| 步 | 代码 | 读/写了什么 | 值的变化 |
|---:|---|---|---|
| ① | `checkForComodification(2)` | 读 `workflow.modCount` = 2，读 `this.expectedModCount` = 2 | 相等，通过 |
| ② | `stack.isEmpty()` | 读 `stack` | `[金额分支]` 非空，不抛 |
| ③ | `stack.pop()` | 改 `stack` | `[金额分支]` → `[]`；`node` = 金额分支 |
| ④ | `node.children()` | 调 `BranchNode.children()` | 返回 `[总监线, 主管线]`（`List.of` 建的不可变列表） |
| ⑤ i=1 | `stack.push(主管线)` | 改 `stack` | `[]` → `[主管线]` |
| ⑤ i=0 | `stack.push(总监线)` | 改 `stack` | `[主管线]` → `[总监线, 主管线]` |
| ⑥ | `return node` | — | 返回"金额分支"，栈顶是"总监线" |

注意第 ⑤ 步的循环方向：`i` 从 `size - 1` 递减到 `0`。

- i=1 先压"主管线" → 栈底；
- i=0 后压"总监线" → 栈顶。

所以下一次 `next()` 弹出的是"总监线"，这就是"先左后右"。

**如果循环写成 `for (int i = 0; i < children.size(); i++)`**，压栈顺序反过来：先压"总监线"、后压"主管线"，栈顶是"主管线"，下一次就会先弹"主管线"。输出变成右子树优先。**不报错，只是镜像。**

整个方法体里被读写的字段只有三个：

| 字段 | 类型 | 作用 | 谁改它 |
|---|---|---|---|
| `workflow` | `final Workflow` | 用来做 `modCount` 对账 | 构造后不再改 |
| `expectedModCount` | `final int` | 构造时的 `modCount` 快照 | 构造后不再改 |
| `stack` | `final Deque<WorkflowNode>` | 待处理节点 | 构造器压根、`next()` 弹+压 |

**两个 `final`、一个 `final` 引用但内容可变。** 没有第四个字段。这就是 §5.8 说的"迭代器的全部状态就是它的进度"。

### 6.7.2 两层嵌套：`FilteringIterator.next()` 调 `DepthFirstIterator.next()`

Demo 4 的调用链：

```text
printWithIndex(filter)
  └─ filter.hasNext()      → FilteringIterator.advance()
       └─ delegate.hasNext() / delegate.next()      ← delegate 是 DepthFirstIterator
  └─ filter.next()
       └─ this.hasNext()   → advance()
```

一次 `filter.next()` 可能触发**多次** `DepthFirstIterator.next()`。以 Demo 4 的**第 1 次** `filter.next()` 为例：

| 序号 | 调用 | 结果 | `pending` / `hasPending` |
|---:|---|---|---|
| 1 | `filter.hasNext()` | 进入 `advance()` | null / false |
| 2 | `delegate.hasNext()` | true（栈非空） | null / false |
| 3 | `delegate.next()` | 报销主流程（SequenceNode，✗） | 不变 |
| 4 | `delegate.next()` | 提交报销单（START，✗） | 不变 |
| 5 | `delegate.next()` | 金额分支（BranchNode，✗） | 不变 |
| 6 | `delegate.next()` | 总监线（SequenceNode，✗） | 不变 |
| 7 | `delegate.next()` | **总监审批（APPROVAL，✓）** | **总监审批 / true** |
| 8 | `while` 条件 `!hasPending` = false，退出 | — | 总监审批 / true |
| 9 | `filter.hasNext()` 返回 | true | 总监审批 / true |
| 10 | `filter.next()` 里 `if (!hasNext())` → 直接返回 true | 不再推进 | 总监审批 / true |
| 11 | `result = pending`；`pending = null`；`hasPending = false` | — | null / false |
| 12 | `filter.next()` 返回 | **总监审批** | null / false |

**一共 5 次 `DepthFirstIterator.next()`，只向上交出 1 个节点。** 这就是"包装"的代价和收益：

| | 数值 |
|---|---|
| 调用方写了几行 | 1 行 `filter.next()` |
| 底层实际取了几次 | 5 次 |
| 调用方知道跳过了 4 个吗 | 不知道，也不需要知道 |

再算第二段过滤（包装 BFS）的数字：

- BFS 序列里"总监审批"在第 **6** 位（见 §6.2.2），所以第一次 `filter.next()` 会触发 **6** 次 `delegate.next()`；
- 第二次 `filter.next()` 从第 7 位开始，读到第 8 位"主管审批"，触发 **2** 次；
- 第三次 `hasNext()` 读第 9 位，不合格，`delegate` 空 → 1 次。

**同一个过滤器、同一棵树，DFS 版内部取了 9 次，BFS 版内部也取了 9 次，但"每次用户可见的 `next()` 对应几次内部 `next()`"完全不同。** 这说明"过滤开销"不能只按"结果个数"算，得按"被跳过的元素分布"算。

### 6.7.3 一次完整遍历的调用次数

Demo 1 的增强 for（9 个节点）：

| 方法 | 调用次数 | 说明 |
|---|---:|---|
| `Workflow.iterator()` | 1 | 增强 for 只调一次 |
| `DepthFirstIterator.hasNext()` | 10 | 9 次 true + 1 次 false |
| `DepthFirstIterator.next()` | 9 | 每个节点一次 |
| `Workflow.checkForComodification()` | 9 | 每次 `next()` 都调 |

`hasNext()` 是 `10 = 9 + 1`，因为 `while` 条件在最后一轮还要再问一次"还有吗"，得到的答案是 false。

Demo 4 的过滤遍历（2 个节点）：

| 方法 | 调用次数 | 说明 |
|---|---:|---|
| `Workflow.iterator()` | 1 | — |
| `FilteringIterator.hasNext()` | 3 | 2 次 true + 1 次 false |
| `FilteringIterator.next()` | 2 | — |
| `DepthFirstIterator.hasNext()` | 9 | `advance()` 每读一个都要先问 |
| `DepthFirstIterator.next()` | 9 | 整个 DFS 序列被读空 |
| `Workflow.checkForComodification()` | 9 | — |

**注意 `FilteringIterator.next()` 是 2 次，但 `DepthFirstIterator.next()` 是 9 次。** 两个数字的比值（2 : 9）就是"过滤率"的倒数 —— 本例 2 个合格、9 个总元素，过滤率 22%。

这也解释了一个常见的性能误区：

> **`FilteringIterator` 是懒过滤，不是零成本过滤。**
>
> 它不会一次性把 9 个都读完（`advance()` 找到就停），但也不会"只读 2 个"（必须读完 9 个才知道没有第 3 个合格的）。总读取次数**恰好等于 9**，一次不多一次不少。
>
> 如果换成"先收集成 `List` 再筛选"，读取次数也是 9，但会多分配一个 `ArrayList` 和 9 次 `add` 调用。所以懒过滤省的是**内存**，不是**CPU**。

### 6.7.4 三层嵌套还能不能套

`FilteringIterator` 的构造器参数类型是 `Iterator<WorkflowNode>`，不是 `DepthFirstIterator`：

```java
public FilteringIterator(Iterator<WorkflowNode> delegate,
                         Predicate<WorkflowNode> predicate,
                         String description) {
```

所以下面这行是合法的：

```java
Iterator<WorkflowNode> doubleFiltered = new FilteringIterator(
        new FilteringIterator(
                flow.iterator(TraversalOrder.DEPTH_FIRST),
                onlyApproval,
                "第一层：只取审批"),
        node -> node.name().contains("总监"),
        "第二层：只取名字含「总监」");
```

两层的效果叠加：先过滤出审批节点，再过滤出名字含"总监"。本例的结果只有 1 个（总监审批）。

**每多套一层，一次用户可见的 `next()` 触发的底层 `next()` 次数最多翻倍**（最坏情况：每层都要跳过大量元素）。这是嵌套过滤的成本上限。

如果把构造器参数写成 `DepthFirstIterator`，上面这行编译不过 —— 因为内层的 `FilteringIterator` 不是 `DepthFirstIterator` 的子类。**这一个类型选择，就决定了能不能嵌套。**

### 6.7.5 六段 Demo 的调用次数总表

| Demo | `iterator()` | 外层 `hasNext()` | 外层 `next()` | 底层 `next()` | 说明 |
|---|---:|---:|---:|---:|---|
| 1 | 3 | 10 | 13 | 13 | 增强 for（1+10+9）+ 两个游标（2+0+4） |
| 2 | 2 | 20 | 18 | 18 | 两次完整遍历，各 10 次 `hasNext()`、9 次 `next()` |
| 3 | 2 | 13 | 11 | 11 | 树 9 个（10/9）+ 列表 2 个（3/2） |
| 4 | 2 | 6 | 4 | 18 | 两个过滤器各 3 次 `hasNext()`、2 次 `next()`；底层各 9 次 |
| 5 | 1 | 2 | 2 | 2 | 第 2 次 `next()` 抛异常，算 2 次调用 |
| 6 | 2 | 2 | 1 | 1 | 增强 for 零次 + 手动 `hasNext()` 1 次 + `next()` 1 次（抛） |

Demo 5 那行值得核对：`cursor.next()` 被调了 2 次 —— 第 1 次成功、第 2 次抛。虽然第 2 次没返回，但**方法确实被调用了**，`checkForComodification` 也确实执行了。这是"调用次数"和"成功次数"必须分开统计的例子。

Demo 6 那行也值得核对：`iterator()` 被调了 2 次 —— 第 223 行 `empty.iterator()` 赋给 `cursor`，第 227 行增强 for 内部又调了一次。**两次都是 `new DepthFirstIterator`，两个对象，两个空栈。**

### 6.7.6 时序图：`filter.next()` 的完整调用栈

把 §6.7.2 的表画成调用栈（缩进表示嵌套深度）：

```text
Client.main
└─ printWithIndex(filter)
   └─ filter.hasNext()                          ← FilteringIterator
      └─ advance()
         ├─ delegate.hasNext()                  ← DepthFirstIterator
         ├─ delegate.next()                     ← 报销主流程（✗）
         ├─ delegate.hasNext()
         ├─ delegate.next()                     ← 提交报销单（✗）
         ├─ delegate.hasNext()
         ├─ delegate.next()                     ← 金额分支（✗）
         ├─ delegate.hasNext()
         ├─ delegate.next()                     ← 总监线（✗）
         ├─ delegate.hasNext()
         ├─ delegate.next()                     ← 总监审批（✓）→ pending
         └─ while 退出（!hasPending 为 false）
   └─ filter.next()
      ├─ this.hasNext()                         ← 直接返回 true（pending 已有）
      ├─ result = pending
      ├─ pending = null
      ├─ hasPending = false
      └─ return result                          ← 总监审批
```

每一层 `delegate.next()` 内部还会执行那六步（对账、判空、弹出、展开、反序压、返回）。**最深层的那次 `checkForComodification` 堆栈深度是 6 层**：`main` → `printWithIndex` → `filter.next()` → `filter.hasNext()` → `advance()` → `delegate.next()` → `checkForComodification()`。

如果这里抛出 `ConcurrentModificationException`，栈顶会指向 `checkForComodification` 的那一行 `throw`，而不是 `delegate.next()` —— 这就是为什么异常消息里要带上"创建时改了几次、现在改了几次"两个数字：**光看栈顶看不出是哪个对象出的事。**

### 6.7.7 这一章的三条可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 迭代器的全部状态就是"进度"（栈/队列 + 快照计数） | §6.7.1 三个字段 | 加额外状态 → 多个游标互相影响 |
| 包装层数越多，一次 `next()` 触发的底层调用越多 | §6.7.2 的 5 次、§6.7.4 的翻倍 | 套五层过滤器 → 一次 `next()` 可能读几百个元素 |
| 调用次数 ≠ 成功次数，统计要分开 | Demo 5 的 `next()` 2 次、Demo 6 的 `iterator()` 2 次 | 只数返回值 → 漏掉抛异常的那次调用 |

---

# 7. 扩展对比：加需求时改哪几行

前面六章讲的是"现在这份代码为什么长这样"。这一章换个问题：

> **如果明天来一个新需求，要改哪几个文件？改几行？哪些文件一行都不用动？**

这个问题比"模式有什么优点"有用得多。因为设计的好坏不体现在"现在能跑"，而体现在"改动会往哪里蔓延"。

八个小节，八个需求，八张改动清单。每一张只列**真实需要改的地方**，不写"理论上可以怎样"。

先给一张总表：

| 节 | 新需求 | 新增文件 | 改动文件 | 零改动文件 |
|---|---|---:|---:|---:|
| 7.1 | 并行会签节点 | 1 | 1（改 3 行） | 9 |
| 7.2 | 后序遍历（从底向上汇总） | 1 | 2（改 2 行） | 8 |
| 7.3 | 倒着走一遍 | 1 | 0 | 10 |
| 7.4 | 多线程同时遍历 | 0 | 2 | 9 |
| 7.5 | 把树换成图 | 0 | 2 | 9 |
| 7.6 | 用 Stream 链式处理 | 0 | 0 | 11 |
| 7.7 | 递归汇总（不用迭代器） | 0 | 1 | 10 |
| 7.8 | 加第六种导出格式 | 1 | 4 | 6 |

注意 7.1 和 7.8 的对比：同样是"加一个类"，一个只改 1 处、一个要改 4 处。差别不在"加了几个文件"，而在**加在了哪一类扩展点上**。

---

## 7.1 新增第四种节点类型：并行会签

### 7.1.1 需求

法务部提了一条：单笔超过 5 万元的采购报销，必须**财务总监和法务总监同时审批**，两边都通过才能继续。

"同时"这个词在流程里意味着：不是一个接一个，而是**并行两路，都完成才汇合**。

现有的三种节点表达不了：

| 节点 | 语义 | 能表达"并行"吗 |
|---|---|---|
| `StepNode` | 做一件事 | 不能（只有一件事） |
| `BranchNode` | 二选一 | 不能（必须选一个） |
| `SequenceNode` | 依次执行 | 不能（顺序执行，不是并行） |

所以需要第四种：`ParallelNode`。

### 7.1.2 改动清单

| 文件 | 改什么 | 行数 |
|---|---|---:|
| `WorkflowNode.java` | `permits` 加一个类型；javadoc 里"三种"改"四种" | 2-3 行 |
| `ParallelNode.java` | **新增** | 约 100 行 |
| `DepthFirstIterator.java` | **零改动** | 0 |
| `BreadthFirstIterator.java` | **零改动** | 0 |
| `FilteringIterator.java` | **零改动** | 0 |
| `Workflow.java` | **零改动** | 0 |
| `StepNode.java` | **零改动** | 0 |
| `BranchNode.java` | **零改动** | 0 |
| `SequenceNode.java` | **零改动** | 0 |
| `TraversalOrder.java` | **零改动** | 0 |
| `Client.java` | **零改动**（除非要加一段新 Demo） | 0 |

**9 个文件零改动，包括两个迭代器。**

### 7.1.3 新文件全文

```java
package com.patterns.behavioral.iterator;

import java.util.List;
import java.util.Objects;

/**
 * 并行块：所有分支同时执行，全部完成后才继续。
 *
 * <p>用于"会签"场景 —— 财务和法务同时审，两边都通过才算过。
 *
 * @param name     节点名
 * @param branches 并行分支列表，至少 1 个
 */
public record ParallelNode(String name, List<WorkflowNode> branches) implements WorkflowNode {

    /**
     * 紧凑构造器：校验并做防御性拷贝。
     */
    public ParallelNode {
        Objects.requireNonNull(name, "节点名不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("节点名不能为空白");
        }
        Objects.requireNonNull(branches, "并行分支不能为 null");
        if (branches.isEmpty()) {
            throw new IllegalArgumentException("并行块「" + name + "」至少要有一个分支");
        }
        branches = List.copyOf(branches);
    }

    /**
     * 并行块的孩子就是各路分支。
     *
     * <p>注意：这里直接返回 {@code branches} 而不是再拷一份 ——
     * 紧凑构造器已经做过 {@code List.copyOf}，字段本身就是不可变列表。
     *
     * @return 不可变的分支列表
     */
    @Override
    public List<WorkflowNode> children() {
        return branches;
    }

    @Override
    public String toString() {
        return "并行块「" + name + "」(" + branches.size() + " 路)";
    }
}
```

### 7.1.4 `WorkflowNode.java` 改哪三行

```java
// 改前
public sealed interface WorkflowNode permits StepNode, BranchNode, SequenceNode {

// 改后
public sealed interface WorkflowNode permits StepNode, BranchNode, SequenceNode, ParallelNode {
```

外加 javadoc 里两处"三种"改成"四种"（纯文字，不影响编译）。

### 7.1.5 为什么两个迭代器一行都不用改

看 `DepthFirstIterator.next()` 的核心四行：

```java
WorkflowNode node = stack.pop();                      // ③ 取出
List<WorkflowNode> children = node.children();        // ④ 展开
for (int i = children.size() - 1; i >= 0; i--) {
    stack.push(children.get(i));                      // ⑤ 反序压回
}
```

**它只调了 `node.children()`，从来没问过"你是什么类型"。** `ParallelNode.children()` 返回 2 路分支，对 DFS 来说就是"这个节点有 2 个孩子"，和 `BranchNode` 有 2 个孩子没有任何区别。

`BreadthFirstIterator` 更简单，只有一行：

```java
queue.addAll(node.children());      // 正序入队
```

同样不问类型。

`FilteringIterator` 连 `children()` 都不调 —— 它只调 `predicate.test(candidate)`。

### 7.1.6 对照：如果 `children()` 写在迭代器里

假设当初的设计是"节点只存数据，展开逻辑放在迭代器里"：

```java
// 假想的坏设计：结构知识散落在每个迭代器里
private static List<WorkflowNode> childrenOf(WorkflowNode node) {
    return switch (node) {
        case StepNode s -> List.of();
        case BranchNode b -> List.of(b.thenBranch(), b.elseBranch());
        case SequenceNode s -> s.steps();
        // ← 加 ParallelNode 时，这里必须补一行
    };
}
```

这个写法在加 `ParallelNode` 时会发生什么？

**编译失败。** 因为 `WorkflowNode` 是 `sealed`，`switch` 表达式的穷尽性检查会立刻报错。

这个错误本身是好事（安全网），但代价是：

| | `children()` 在节点上（实际做法） | `children()` 在迭代器里（假想做法） |
|---|---|---|
| 结构知识有几份 | **1 份**（每个节点自己知道自己的孩子） | **N 份**（每个需要展开节点的类一份） |
| 加 `ParallelNode` 改几处 | 1 处（`permits`） | N 处（DFS 一处、BFS 一处、`Client.printTree` 一处、后续任何新迭代器一处） |
| 漏改会怎样 | 编译失败（`permits` 必须列全） | 编译失败（switch 不穷尽）—— 但**只在用到 `childrenOf` 的地方**失败 |
| 加第 6 个迭代器时 | 零改动 | 又要加一份 `childrenOf` |

**关键区别不是"安全 vs 不安全"** —— 两种写法都有编译期安全网。关键区别是**"结构知识有几份"**。

一份知识只需要维护一次；N 份知识有 N 次写错的机会，而且它们会随着时间漂移 —— 某天有人给 DFS 版的 `childrenOf` 加了个"跳过禁用节点"的逻辑，BFS 版的没加，两个迭代器行为就不一致了。

这就是 §4.3 决定三的完整论证。在本节的运行时表现是：**加第四种节点，两个迭代器的源码一个字节都没变。**

### 7.1.7 一个不能省的细节：`List.copyOf` 在紧凑构造器里

`ParallelNode` 的紧凑构造器里有一行：

```java
branches = List.copyOf(branches);
```

紧凑构造器（compact constructor）的赋值会自动赋给字段 `branches`，不需要写 `this.branches = ...`。

这一行的作用：

| 不加会怎样 | 具体风险 |
|---|---|
| 外部持有 `branches` 的引用 | 调用方 `add` 一个新分支，`ParallelNode` 的内容悄悄变了 |
| 遍历中途被改 | `children()` 返回的列表还在被 DFS 迭代，另一个线程往里加 → `ConcurrentModificationException` 或更糟 |
| 节点不再不可变 | `record` 的 `equals` / `hashCode` 基于字段，字段被外部改了，放进 `HashSet` 就找不到了 |

加了这一行，`ParallelNode` 就和现有三种节点一样是**真不可变**的。这也是 §7.4 讨论并发时"一旦迭代器构造完成就完全安全"的前提。

---

## 7.2 新增第三种遍历顺序：后序遍历

### 7.2.1 需求

财务要一张"耗时汇总表"：每个节点的耗时 = 自己耗时 + 所有子节点耗时。父节点的数字必须等所有子节点算完才能出来。

**这正是后序遍历（左-右-根）的语义** —— 先处理孩子，再处理自己。

现有的 `TraversalOrder` 只有两个常量：

```java
public enum TraversalOrder {
    DEPTH_FIRST,
    BREADTH_FIRST
}
```

### 7.2.2 改动清单

| 文件 | 改什么 | 行数 |
|---|---|---:|
| `TraversalOrder.java` | 加一个 `POST_ORDER` 常量 | 1 行 |
| `Workflow.java` | `switch` 加一个 `case` | 1 行 |
| `PostOrderIterator.java` | **新增** | 约 190 行 |
| 其余 8 个文件 | **零改动** | 0 |

### 7.2.3 编译器会替你记住这个 case

`Workflow.iterator(TraversalOrder)` 的方法体：

```java
public Iterator<WorkflowNode> iterator(TraversalOrder order) {
    Objects.requireNonNull(order, "遍历顺序不能为 null");
    return switch (order) {                          // ← 注意：return switch，是表达式
        case DEPTH_FIRST -> new DepthFirstIterator(this);
        case BREADTH_FIRST -> new BreadthFirstIterator(this);
    };
}
```

**这是 `switch` 表达式（switch expression），不是 `switch` 语句（statement）。**

两者的穷尽性要求完全不同。下面这段是真实的编译结果（临时对照组目录 `tmp_enum_switch`，验完即删，JDK 25）：

```text
=== 1) switch 表达式不穷尽（期望编译失败） ===
E:\codeFile\codeFather\design_patterns_pro\tmp_enum_switch\EnumSwitchFail.java:12: 错误: switch 表达式不包含所有可能的输入值
        return switch (o) {
               ^
1 个错误

=== 2) switch 语句不穷尽（期望编译通过） ===
exit = 0

=== 3) 运行对照组 ===
switch 语句不覆盖全部常量，编译通过，运行时什么都没打印
```

三个结论：

| 写法 | 加枚举常量后 | 后果 |
|---|---|---|
| `switch` **表达式**（`return switch (o) {...}`） | **编译失败** | 逼你补 `case`，不可能忘 |
| `switch` **语句**（`switch (o) {...}`） | 编译通过 | **运行时静默什么都不做** |
| 有 `default` 的 `switch` 表达式 | 编译通过 | 新常量悄悄落进 `default`，行为错但没人知道 |

**本项目用的是第一种。** 这就是为什么 `iterator(TraversalOrder)` 里那个 `switch` 刻意不写 `default` —— 不是为了少写一行，是为了让编译器当"忘记加 case"的守卫。

第三行那种"有 `default` 的写法"最危险：它把"漏改"伪装成"能跑"。加了一个枚举值，代码不报错，行为悄悄走了默认分支。很多线上事故就是这么来的。

> **一条可以带走的原则：**
>
> 对**封闭集合**（`enum`、`sealed interface`）做分支时，**不要写 `default`**。
>
> 不写 `default`，编译器帮你检查穷尽性；写了 `default`，编译器就闭嘴了。
>
> "少写一个 `default`"和"运行时静默走错分支"之间，只隔一个 `default`。

### 7.2.4 后序迭代器的单栈实现

后序（左-右-根）比前序难，因为**一个节点要被"看见"两次**：第一次是"我还没处理完孩子"，第二次才是"孩子都完了，可以交出我了"。

三种实现方式：

| 方案 | 做法 | 空间复杂度 | 说明 |
|---|---|---|---|
| A | 两个栈，第一个栈生成"根-右-左"，倒进第二个栈 | O(n) | 最简单，但要存整棵树 |
| B | 先收集成 `List`，再反转 | O(n) | 也要先存整棵树 |
| C | 单栈 + 每个节点记录"处理到第几个孩子" | **O(深度)** | 稍难，但保持了懒遍历 |

本项目选 C。理由和 §4.7 选显式栈一样：**迭代器的核心优势是"O(深度) 空间"，用 A 或 B 就把它丢了。**

完整实现：

```java
package com.patterns.behavioral.iterator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 后序遍历迭代器（左 → 右 → 根）。
 *
 * <p>用于"孩子的结果先算完，父节点才能汇总"的场景，
 * 比如耗时统计、金额汇总、依赖解析。
 *
 * <p>单栈实现：每个节点包成 {@link Frame}，记录"孩子处理到第几个"。
 * 空间复杂度是 O(树高)，不是 O(节点数)。
 */
public final class PostOrderIterator implements Iterator<WorkflowNode> {

    /**
     * 栈帧：节点 + 下一个要处理的孩子下标。
     */
    private static final class Frame {
        private final WorkflowNode node;
        private int nextChild;

        private Frame(WorkflowNode node) {
            this.node = node;
        }
    }

    private final Deque<Frame> stack = new ArrayDeque<>();
    private final Workflow workflow;
    private final int expectedModCount;

    /**
     * 构造：把根节点反序压栈（和 DFS 一样，让第一个根先出）。
     *
     * @param workflow 待遍历的审批流
     */
    public PostOrderIterator(Workflow workflow) {
        this.workflow = Objects.requireNonNull(workflow, "审批流不能为 null");
        this.expectedModCount = workflow.modCount();

        List<WorkflowNode> roots = workflow.roots();
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.push(new Frame(roots.get(i)));
        }
    }

    @Override
    public boolean hasNext() {
        return !stack.isEmpty();
    }

    @Override
    public WorkflowNode next() {
        workflow.checkForComodification(expectedModCount);

        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            List<WorkflowNode> children = top.node.children();

            if (top.nextChild < children.size()) {
                // 还有孩子没处理：压一个进去，下一轮继续往下钻
                stack.push(new Frame(children.get(top.nextChild)));
                top.nextChild++;
            } else {
                // 孩子都处理完了：现在才交出自己
                stack.pop();
                return top.node;
            }
        }
        throw new NoSuchElementException("后序遍历已经到底，没有下一个节点");
    }
}
```

### 7.2.5 逐步展开：验证顺序真的是"左-右-根"

用 Demo 1 那棵树的后半部分当例子：

```text
金额分支
├─ 总监线
│  ├─ 总监审批
│  └─ 通知财务
└─ 主管线
   ├─ 主管审批
   └─ 通知申请人
```

（把它当成独立的根，忽略上层的"报销主流程"和"提交报销单"。）

| 轮次 | `peek()` 的 | `nextChild` | 动作 | 栈（左=栈顶） |
|---:|---|---:|---|---|
| 构造 | — | — | — | `[金额分支(0)]` |
| 1 | 金额分支 | 0 | 压总监线，`nextChild` → 1 | `[总监线(0), 金额分支(1)]` |
| 2 | 总监线 | 0 | 压总监审批，`nextChild` → 1 | `[总监审批(0), 总监线(1), 金额分支(1)]` |
| 3 | 总监审批 | 0 | 孩子为空 → **弹出并返回总监审批** | `[总监线(1), 金额分支(1)]` |
| 4 | 总监线 | 1 | 压通知财务，`nextChild` → 2 | `[通知财务(0), 总监线(2), 金额分支(1)]` |
| 5 | 通知财务 | 0 | 孩子为空 → **弹出并返回通知财务** | `[总监线(2), 金额分支(1)]` |
| 6 | 总监线 | 2 | 2 == 2，孩子处理完 → **弹出并返回总监线** | `[金额分支(1)]` |
| 7 | 金额分支 | 1 | 压主管线，`nextChild` → 2 | `[主管线(0), 金额分支(2)]` |
| 8 | 主管线 | 0 | 压主管审批，`nextChild` → 1 | `[主管审批(0), 主管线(1), 金额分支(2)]` |
| 9 | 主管审批 | 0 | 孩子为空 → **返回主管审批** | `[主管线(1), 金额分支(2)]` |
| 10 | 主管线 | 1 | 压通知申请人，`nextChild` → 2 | `[通知申请人(0), 主管线(2), 金额分支(2)]` |
| 11 | 通知申请人 | 0 | 孩子为空 → **返回通知申请人** | `[主管线(2), 金额分支(2)]` |
| 12 | 主管线 | 2 | 孩子处理完 → **返回主管线** | `[金额分支(2)]` |
| 13 | 金额分支 | 2 | 孩子处理完 → **返回金额分支** | `[]` |

输出序列：`总监审批, 通知财务, 总监线, 主管审批, 通知申请人, 主管线, 金额分支`

逐个子树看：

- 总监线这一支：`总监审批 → 通知财务 → 总监线`（左、右、根）✓
- 主管线这一支：`主管审批 → 通知申请人 → 主管线`（左、右、根）✓
- 合并：`总监线这一支 → 主管线这一支 → 金额分支`（左、右、根）✓

**三层递归都满足"左-右-根"。**

### 7.2.6 为什么 `hasNext()` 还是纯查询

```java
@Override
public boolean hasNext() {
    return !stack.isEmpty();
}
```

和 DFS / BFS 完全一样：**只看栈非空，不推进任何东西。**

这一点比 `FilteringIterator` 干净 —— 后序"往下钻"的动作放在 `next()` 的 `while` 循环里，因为 `while` 会一直钻到"某个节点的孩子都处理完"才 `return`。所以 `hasNext()` 不需要预读。

**四种迭代器的 `hasNext()` 对比：**

| 迭代器 | `hasNext()` | 有副作用吗 | 为什么 |
|---|---|:---:|---|
| `DepthFirstIterator` | `!stack.isEmpty()` | 无 | 栈里就是"待处理"的全部 |
| `BreadthFirstIterator` | `!queue.isEmpty()` | 无 | 队列里就是"待处理"的全部 |
| `PostOrderIterator` | `!stack.isEmpty()` | 无 | 栈非空就一定有下一个（最深处那个） |
| `FilteringIterator` | `advance(); return hasPending;` | **有** | 答案在"未来"，必须预读 |

**三个纯查询 + 一个必然有副作用。** 这不是实现风格差异，是语义决定的：只要"是否还有下一个"的答案在当前状态里就已经确定，就不需要副作用。

### 7.2.7 一个容易写错的地方：`nextChild++` 的位置

正确写法：

```java
if (top.nextChild < children.size()) {
    stack.push(new Frame(children.get(top.nextChild)));
    top.nextChild++;                      // ← 必须在 push 之后
}
```

如果写成先自增再取：

```java
top.nextChild++;
stack.push(new Frame(children.get(top.nextChild)));      // ← 跳过了第一个孩子！
```

这会**跳过每个节点的第一个孩子**。症状：后序遍历少输出一半节点，且剩下的顺序看起来"还算合理"，很难一眼看出。

**为什么 `top.nextChild++` 能改到栈里的对象？** 因为 `Frame` 是**可变对象**，`peek()` 返回的是引用，`top.nextChild++` 改的是堆上那个 `Frame` 实例的字段。这和 §2.8 讲的"非静态内部类持有外部引用"是同一个道理的另一面：**引用共享意味着改动可见。**

如果 `Frame` 写成 `record`（字段 `final`），`top.nextChild++` 就编译失败 —— 编译器会逼你用"弹出旧的、压入新的"的方式更新。哪种更好？

| 设计 | 优点 | 缺点 |
|---|---|---|
| `Frame` 可变（`class`） | 改一个 `int` 字段，零分配 | 栈里存的是可变对象，要考虑"谁还能改它" |
| `Frame` 不可变（`record`） | 无副作用，每次 `pop` + `push` 新的 | 每次下钻多一次对象分配 |

本项目选可变，因为后序遍历里"压栈 + 下钻"是高频操作，用不可变 `Frame` 会让每次下钻都多分配一个对象。**在一个明确 `private static`、生命周期只到 `next()` 返回的类里，可变字段是最简单正确的做法。**

### 7.2.8 这一节的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 对 `enum` / `sealed` 做分支时不要写 `default` | §7.2.3 的真实编译输出 | 写了 `default` → 新常量静默落进默认分支 |
| `switch` 表达式有穷尽性检查，`switch` 语句没有 | `EnumSwitchFail` 编译失败 / `EnumSwitchStatementOk` 静默通过 | 用语句 → 漏 case 也能编译，运行时什么都不做 |
| 后序遍历用单栈 + `nextChild` 下标，保持 O(深度) 空间 | §7.2.4 与 §7.2.5 的 13 轮推演 | 用双栈 → 空间变 O(n)，失去懒遍历优势 |

---

## 7.3 新增一个倒序迭代器

### 7.3.1 需求

审批详情页要显示"日志倒序"，最新的在最上面。

产品经理的原话是："把原来的顺序反过来。"

**这句话在树上是个陷阱。** "反过来"至少有两种完全不同的含义：

| 理解 | 具体含义 | 实现代价 |
|---|---|---|
| A | 把前序输出的 9 个节点倒着排 | **O(n) 空间**（先全读进 `List` 再倒着走） |
| B | 换一个方向走这棵树 | **O(深度) 空间**（改一个循环方向） |

A 的做法：

```java
List<WorkflowNode> all = new ArrayList<>();
for (WorkflowNode node : flow) {
    all.add(node);
}
Collections.reverse(all);
for (WorkflowNode node : all) { ... }
```

B 的做法：把 `DepthFirstIterator` 的两处循环方向反过来：

```java
// 构造器里
for (int i = 0; i < roots.size(); i++) {          // ← 原本是 i--，改成 i++
    stack.push(roots.get(i));
}

// next() 里
for (int i = 0; i < children.size(); i++) {        // ← 原本是 i--，改成 i++
    stack.push(children.get(i));
}
```

**两个循环的 `i` 方向翻转，就是一个镜像 DFS。**

### 7.3.2 改动清单

| 文件 | 改什么 | 行数 |
|---|---|---:|
| `ReverseDepthFirstIterator.java` | **新增**（把 DFS 复制一份，两处循环反向） | 约 200 行 |
| 其余 10 个文件 | **零改动** | 0 |

注意：**`TraversalOrder` 不用加常量。** 因为"镜像"不是"新的遍历策略"，而是"同一个策略换个方向"。

如果硬要加到枚举里，会变成：

```java
public enum TraversalOrder {
    DEPTH_FIRST,
    BREADTH_FIRST,
    POST_ORDER,
    REVERSE_DEPTH_FIRST,        // ← 排列组合开始膨胀
    REVERSE_BREADTH_FIRST,      // ← 反着走的 BFS 要不要也加？
    ...
}
```

这是"枚举爆炸"的味道。**枚举只该装"有本质区别的策略"，不该装"策略 × 方向"的笛卡尔积。**

### 7.3.3 镜像 DFS 的输出：根还在最前

用 Demo 1 那棵树的后半部分（同 §7.2.5）：

```text
金额分支
├─ 总监线
│  ├─ 总监审批
│  └─ 通知财务
└─ 主管线
   ├─ 主管审批
   └─ 通知申请人
```

四种顺序的真实序列：

| 编号 | 名称 | 序列 |
|---|---|---|
| ① | 前序 `DepthFirstIterator`（根-左-右） | 金额分支, 总监线, 总监审批, 通知财务, 主管线, 主管审批, 通知申请人 |
| ② | **镜像前序**（根-右-左） | 金额分支, 主管线, 主管审批, 通知申请人, 总监线, 总监审批, 通知财务 |
| ③ | 后序（左-右-根） | 总监审批, 通知财务, 总监线, 主管审批, 通知申请人, 主管线, 金额分支 |
| ④ | **递归右-左-根** | 通知申请人, 主管审批, 主管线, 通知财务, 总监审批, 总监线, 金额分支 |

四个序列两两不同。关键差别：

| 对比 | 差异 |
|---|---|
| ① vs ② | 根都在**最前**，左子树和右子树的先后换了 |
| ① vs ④ | **④ 正好是 ① 的完全逆序** |
| ② vs ④ | 不同（② 是根在前，④ 是根在最后） |
| ③ vs ④ | ③ 是左-右-根，④ 是右-左-根，互为左右镜像 |

**验证 ① 与 ④ 的逆序关系**：① 是 `金额分支, 总监线, 总监审批, 通知财务, 主管线, 主管审批, 通知申请人`；把它倒过来读：

```text
通知申请人, 主管审批, 主管线, 通知财务, 总监审批, 总监线, 金额分支
```

与 ④ 完全一致。✓

### 7.3.4 一个干净的数学结论

> **前序（根-左-右）的完全逆序 = 递归的"右-左-根"。**

证明（归纳）：

- 设树为"根 + 左子树 L + 右子树 R"；
- 前序 = `[根] + preorder(L) + preorder(R)`；
- 前序的逆序 = `reverse(preorder(R)) + reverse(preorder(L)) + [根]`；
- 由归纳假设，`reverse(preorder(X)) = RLN(X)`（右-左-根）；
- 所以前序的逆序 = `RLN(R) + RLN(L) + [根]` = **`RLN(根 + L + R)`**。

第一行是"根在前"，最后一行是"根在最后"，中间两段子树的顺序也刚好互换。这个结论不是巧合，是结构的对称性。

**推论**：如果要"完全逆序"，实现方式是"后序的镜像"（右-左-根），不是"前序的镜像"（根-右-左）。两者的区别正是"根在最前"还是"根在最后"。

### 7.3.5 实现"完全逆序"：只改后序的一行

基于 §7.2 的 `PostOrderIterator`，只改"取孩子的方向"：

```java
// 左-右-根（后序）
stack.push(new Frame(children.get(top.nextChild)));

// 右-左-根（前序的完全逆序）
int index = children.size() - 1 - top.nextChild;
stack.push(new Frame(children.get(index)));
```

**一行之差，输出就从前序的镜像变成了前序的完全逆序。**

### 7.3.6 三种"倒着走"的映射表

这张表可以直接拿去回答"倒序怎么实现"：

| 你真正想要的 | 实现方式 | 改哪一行 | 空间 |
|---|---|---|---|
| 先右后左地往下钻（视觉上的"从右开始"） | 镜像前序 | 前序的两个循环方向翻转 | O(深度) |
| **最新的在最前**（前序的完全逆序） | 递归右-左-根 | 后序的取孩子下标倒着算 | O(深度) |
| 从叶子往根算（汇总类需求） | 后序 | 新增 `PostOrderIterator` | O(深度) |
| 干脆全读出来再倒排 | 收集 + `reverse` | 零（但多一个 `List`） | **O(n)** |

只有第四行需要 O(n) 空间。前三行都是 O(深度)。

> **"倒序"这个词在树上必须先问清楚是哪一种。** 如果需求方说"倒过来"，而你不问就实现，十有八九会做成第四行那种 O(n) 版本 —— 因为它在 `List` 上最直观。但树不是 `List`，它的"反向"有三个不同含义。

### 7.3.7 这一节的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| "倒序"在树上至少有三种含义，必须先问清 | §7.3.3 四个序列的对比 | 直接用 `List` + `reverse` → O(n) 空间，丢失懒遍历 |
| 前序的完全逆序 = 递归右-左-根，不是前序镜像 | §7.3.4 的归纳证明 | 用前序镜像 → 根跑到最前，与"最新在最前"相反 |
| 枚举只装"本质不同的策略"，不装"策略 × 方向" | §7.3.2 的枚举爆炸推演 | 每个方向加一个常量 → 常量数指数增长 |

---

## 7.4 让遍历支持并发

### 7.4.1 先分清两种"并发"

| 场景 | 描述 | 频率 |
|---|---|---|
| **场景一**：多个线程各自遍历同一个 `Workflow`（都不修改） | 报表服务里 5 个线程同时生成 5 份不同视图 | 很高 |
| **场景二**：一个线程在遍历，另一个线程 `add` 节点 | 审批流支持"运行时动态加签" | 较低但真实存在 |

两者需要的保证完全不同，不能混为一谈。

### 7.4.2 场景一：现在其实已经基本安全

`DepthFirstIterator` 的构造器做了三件事：

```java
this.workflow = Objects.requireNonNull(workflow, "审批流不能为 null");
this.expectedModCount = workflow.modCount();      // 读一次 int
List<WorkflowNode> roots = workflow.roots();      // 返回 List.copyOf(roots) —— 真拷贝
```

三个关键点：

| 点 | 为什么安全 |
|---|---|
| `roots()` 返回 `List.copyOf` | 迭代器操作的是**自己的副本**，不是 `Workflow` 内部的 `ArrayList` |
| 节点全是 `record` + `List.copyOf` | 迭代器栈里的每个节点都是不可变的，读到什么就是什么 |
| 构造之后不再碰 `workflow` 除 `modCount` 之外的任何字段 | 五个线程各自 `new` 一个迭代器，栈、队列全在各自栈帧里 |

**所以：五个线程各自 `flow.iterator()` 然后各自遍历，是安全的。** 它们只共享"不可变的节点对象"和"`modCount` 的一次读"。

唯一的不确定性：如果五个线程构造迭代器的**同时**另一个线程在 `add`，那 `roots()` 的 `List.copyOf` 可能在遍历 `ArrayList` 的中途看到数组扩容 —— 这是场景二的问题，不是场景一。

### 7.4.3 场景二：现在是坏的，而且坏在两处

`Workflow.add()`：

```java
public Workflow add(WorkflowNode node) {
    Objects.requireNonNull(node, "节点不能为 null");
    roots.add(node);        // ← ① ArrayList.add 非线程安全
    modCount++;             // ← ② int 自增非原子
    return this;
}
```

| 问题 | 后果 |
|---|---|
| ① 两个线程同时 `roots.add` | `ArrayList` 的内部数组可能被写坏，甚至无限循环（JDK 7 之前的经典 bug）；好的情况是丢一个元素 |
| ② `modCount++` 非原子 | 两个线程各加一次，`modCount` 可能只加了 1，于是 fail-fast 检查漏掉一次修改 |
| ③ `modCount` 非 `volatile` | 一个线程的 `add` 对另一个线程的迭代器**可能永远不可见** —— 于是不抛异常，静默遍历旧数据 |

第 ③ 条最隐蔽：`ConcurrentModificationException` 是"尽力而为"的，不是保证。

### 7.4.4 三个方案

| 方案 | 改动 | 代价 | 适合 |
|---|---|---|---|
| A | `add` 和 `roots()` 都加 `synchronized` | 读写都串行化 | 写极少、读也少 |
| B | `CopyOnWriteArrayList` + `AtomicInteger` | 写时复制整个数组，O(n) | **写少读多**（本项目典型） |
| C | 干脆不可变：构造时传 `List`，不提供 `add` | 要改 API，调用方不能增量构建 | 配置式流程 |

本项目如果要做场景二，选 B。改动只有两处：

```java
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

private final List<WorkflowNode> roots = new CopyOnWriteArrayList<>();
private final AtomicInteger modCount = new AtomicInteger();

public Workflow add(WorkflowNode node) {
    Objects.requireNonNull(node, "节点不能为 null");
    roots.add(node);
    modCount.incrementAndGet();
    return this;
}

public List<WorkflowNode> roots() {
    return List.copyOf(roots);          // CopyOnWriteArrayList 的迭代器本身就是快照
}

int modCount() {
    return modCount.get();              // ← 返回 int，签名不变
}

void checkForComodification(int expectedModCount) {
    if (modCount.get() != expectedModCount) {
        throw new ConcurrentModificationException(...);
    }
}
```

**注意：`DepthFirstIterator` / `BreadthFirstIterator` / `FilteringIterator` / `PostOrderIterator` 零改动。** 因为：

- `modCount()` 的方法签名还是 `int`（内部从 `int` 变成 `AtomicInteger.get()`）；
- `checkForComodification(int)` 的签名和语义都不变；
- `roots()` 返回类型还是 `List<WorkflowNode>`。

**这就是"把状态封装在方法后面"的价值**：改了字段类型，只要方法签名不变，调用方就感知不到。

如果当初把 `roots` 字段直接写成 `public`，这次改动就会穿透到所有迭代器。`private` + 访问器方法在这一刻兑现了它的价值。

### 7.4.5 为什么不推荐"迭代器里加锁"

有人会想：既然问题出在并发，那在迭代器里加 `synchronized` 不就行了？

```java
@Override
public synchronized WorkflowNode next() { ... }
```

**没用，因为锁的是迭代器对象，不是 `Workflow` 对象。**

- 五个线程各自 `new` 一个迭代器 → 五把不同的锁 → 完全不互斥；
- 真正的共享资源是 `Workflow.roots`，锁必须加在 `Workflow` 上（或者加在 `add` 和 `roots()` 上）。

这条经验可以推广成一句：

> **锁必须加在"被共享的那个对象"上，不是加在"使用它的那个对象"上。**

### 7.4.6 一个不能靠加锁解决的问题

即使 `add` 加了锁，仍然有一个语义问题：

```java
// 线程 A
for (WorkflowNode node : flow) {
    process(node);              // 处理到第 3 个
}

// 线程 B 同时
flow.add(新节点);               // 加到末尾
```

线程 A 该不该看到新节点？

| 期望 | 实现 |
|---|---|
| 看到（"遍历时实时反映最新结构"） | 迭代器每次 `next()` 都要重新读 `roots`，且不能有 `modCount` 检查 |
| 看不到（"遍历的是某一时刻的快照"） | 现在的 `List.copyOf` + `modCount` 已经做到了 |

**这两种语义没有对错，只有选择。** 本项目选"快照"，因为审批流的业务语义是"这张单子按提交时的流程走完"，中途加签应该新建一个流程版本，而不是偷偷改正在走的这张单。

**这个选择体现在两行代码里**：`List.copyOf` 决定了"快照"，`modCount` 检查决定了"改了就报错"。两条合起来，语义就是"要么看完整快照，要么什么都不看"。

### 7.4.7 这一节的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 多线程各自遍历已经基本安全，因为 `roots()` 返回真拷贝 | §7.4.2 三个关键点 | 直接暴露内部 `ArrayList` → 遍历时被改，行为不定 |
| 锁要加在共享对象（`Workflow`）上，不是使用者（迭代器）上 | §7.4.5 | 迭代器加 `synchronized` → 五把锁各不相同，等于没锁 |
| 改字段类型时保持方法签名不变，调用方零改动 | §7.4.4 的 `AtomicInteger` 改动 | 字段 `public` → 改动穿透到所有调用方 |

---

## 7.5 把树换成图

### 7.5.1 需求

审批流要支持"退回重审"：某个节点被驳回时，流程回到前面某个节点，形成一条**回边**。

有了回边，结构就从"树"变成了"有向图"。树上不会有环，图上可能有。

### 7.5.2 现在的 DFS 在图上会怎样

`DepthFirstIterator.next()` 没有任何"已访问"的概念：

```java
WorkflowNode node = stack.pop();
List<WorkflowNode> children = node.children();
for (int i = children.size() - 1; i >= 0; i--) {
    stack.push(children.get(i));        // ← 压回去，不管它是不是来过
}
```

假设图是这样的：

```text
A → B → C
    ↑   │
    └───┘         C 的孩子是 B（回边）
```

推演：

| 轮次 | 弹出 | 压入 | 栈 |
|---:|---|---|---|
| 构造 | — | A | `[A]` |
| 1 | A | B | `[B]` |
| 2 | B | C | `[C]` |
| 3 | C | B | `[B]` |
| 4 | B | C | `[C]` |
| 5 | C | B | `[B]` |
| ... | ... | ... | **永不结束** |

`hasNext()` 永远返回 `true`，`next()` 永远有东西返回，内存持续增长（`stack` 不会变空）。**死循环，且不报错。**

这是最危险的一类 bug：程序不崩、CPU 打满、日志还在刷，看起来"在干活"。

### 7.5.3 改动清单

| 文件 | 改什么 | 行数 |
|---|---|---:|
| `DepthFirstIterator.java` | 加一个 `visited` 集合 + 跳过已访问 | 约 10 行 |
| `BreadthFirstIterator.java` | 同样加 `visited`（队列也会绕环） | 约 10 行 |
| 其余 9 个文件 | **零改动** | 0 |

**两个迭代器都要改。** 为什么 BFS 也不能幸免，见下一节。

具体改动：

```java
private final Set<WorkflowNode> visited =
        Collections.newSetFromMap(new IdentityHashMap<>());

@Override
public WorkflowNode next() {
    workflow.checkForComodification(expectedModCount);

    while (!stack.isEmpty()) {
        WorkflowNode node = stack.pop();
        if (!visited.add(node)) {
            continue;                       // ← 来过了，跳过
        }
        for (int i = node.children().size() - 1; i >= 0; i--) {
            stack.push(node.children().get(i));
        }
        return node;
    }
    throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点");
}
```

三个细节：

| 细节 | 为什么 |
|---|---|
| `IdentityHashMap` 而不是 `HashSet` | 节点是 `record`，`equals` 基于字段。两个同名字同类型的节点会被判"相等"，但它们在图上可能是两个不同位置。用**引用相等**才对 |
| `visited.add()` 放在 `pop` 之后、`push` 之前 | 只有这样，"第一次访问"才是真的第一次 |
| `while` 而不是递归 `return next()` | 递归会消耗 Java 方法栈；如果图上有很多已访问节点连续被跳过，递归深度可能上千 |

### 7.5.4 为什么 BFS 也必须一起改

BFS 同样会死循环，只是绕圈的方向不同：

```java
WorkflowNode node = queue.pollFirst();
queue.addAll(node.children());      // ← C 的孩子 B 又被加进队列
```

所以严格地说：**成了图之后，两个迭代器都要加 `visited`。改动清单就是上一节表格里的 2 个文件、各约 10 行。**

| 迭代器 | 加 `visited` | 加在哪 |
|---|---|---|
| `DepthFirstIterator` | 要 | `pop` 之后 `continue` 跳过 |
| `BreadthFirstIterator` | 要 | `pollFirst` 之后 `continue` 跳过 |
| `FilteringIterator` | **不要** | 它只包装，`delegate` 已经去过重了 |
| `PostOrderIterator` | **不要**（但也不对） | 后序在图上本身就有歧义：一个节点可能有多条路径到达，后序该交几次？ |

最后一行值得展开：**图上的"后序"没有唯一定义。** 树上一个节点只有一个父，所以"处理完所有孩子"是明确的；图上一条节点可能通过多条路径被到达，那"所有孩子处理完"该按哪条路径算？

这不是实现问题，是语义问题。**所以在图上，只有前序和 BFS 是良定义的，"后序"要先定义清楚才有意义。**

### 7.5.5 一个更根本的结论

把树换成图，需要改的不是"迭代器模式"，而是"遍历算法本身"。

| 结构 | 遍历算法需要什么额外信息 |
|---|---|
| 一维数组 | 只要下标 |
| 链表 | 只要当前节点 |
| 树 | 只要栈 / 队列 |
| **图** | **还要 `visited` 集合** |
| 带权图 | 还要 `dist` 数组（最短路） |

**迭代器模式的价值恰恰在于：这个"额外信息"被关在一个类里。** 加 `visited` 只改 `DepthFirstIterator` 一个文件，`Workflow`、节点类、`Client`、`FilteringIterator` 全都不动。

如果遍历逻辑是散落在 `Client` 里的手写 `while`，那"换成图"就要改所有用到遍历的地方。

### 7.5.6 这一节的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 树上的遍历算法搬到图上必须加 `visited` | §7.5.2 的 5 轮推演（永不结束） | 不加 → 死循环、CPU 打满、内存增长，**不报错** |
| `record` 当集合元素时要用 `IdentityHashMap` 建 `Set` | §7.5.3 第一个细节 | 用 `HashSet` → 同名字节点被误判为"已访问"，漏遍历 |
| 图上的"后序"没有唯一定义，是语义问题不是实现问题 | §7.5.4 最后一段 | 硬实现 → 一个节点被交出多次或零次 |

---

## 7.6 用 Stream 代替手写遍历

### 7.6.1 需求

报表要一行写完："取出所有审批节点的名字，拼成逗号分隔的字符串。"

用现在的 API 要写：

```java
List<String> names = new ArrayList<>();
for (WorkflowNode node : flow) {
    if (node instanceof StepNode step && step.type() == StepType.APPROVAL) {
        names.add(step.name());
    }
}
String result = String.join(", ", names);
```

### 7.6.2 改动清单

| 文件 | 改什么 | 行数 |
|---|---|---:|
| 全部 11 个文件 | **零改动** | 0 |

**为什么一行都不用改？** 因为 `Workflow` 已经 `implements Iterable`，而 `Iterable` 有 default 方法 `spliterator()`：

```java
// java.lang.Iterable 的 default 方法
default Spliterator<T> spliterator() {
    return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED);
}
```

`StreamSupport.stream()` 吃的就是这个 `Spliterator`。

### 7.6.3 一行写法

```java
String result = StreamSupport
        .stream(flow.spliterator(), false)
        .filter(node -> node instanceof StepNode step && step.type() == StepType.APPROVAL)
        .map(WorkflowNode::name)
        .collect(Collectors.joining(", "));
```

输出：`总监审批, 主管审批`

如果只想取第一个：

```java
Optional<String> first = StreamSupport
        .stream(flow.spliterator(), false)
        .filter(node -> node instanceof StepNode step && step.type() == StepType.NOTIFY)
        .map(WorkflowNode::name)
        .findFirst();
```

`findFirst()` 是**短路操作** —— 找到第一个就停，后面的不读。

**这一点值得验证**：如果 `findFirst()` 真的短路，那么底层 `DepthFirstIterator.next()` 应该只被调了 6 次（"通知财务"是 DFS 序列第 6 个），不是 9 次。

### 7.6.4 `spliteratorUnknownSize` 的代价

注意上面那一行的关键：`spliteratorUnknownSize`。

| 特性 | `ArrayList.spliterator()` | `Iterable.spliterator()`（default） |
|---|---|---|
| `SIZED` 特性 | 有（大小已知） | **无**（大小未知） |
| `SUBSIZED` | 有 | **无** |
| `trySplit()` 质量 | 对半切，真并行 | 把剩余元素攒成数组，然后切 |
| `stream.count()` | O(1) | **O(n)**（遍历一遍） |
| `parallelStream()` | 有效 | **几乎无收益**，甚至更慢 |

第三行最要命：`Iterable.spliterator()` 的 `trySplit()` 必须先把剩下的元素全读进数组，才能分给别人。**这等于把"懒遍历"变成了"先物化"** —— 所以对未知大小的 `Iterable` 用并行流，通常比顺序流还慢。

### 7.6.5 什么时候用 Stream，什么时候手写

| 场景 | 推荐 | 理由 |
|---|---|---|
| 过滤 + 映射 + 收集 | **Stream** | 一行写完，可读性高 |
| 需要"边遍历边改状态"（比如维护索引） | 手写 `while` + `hasNext` | Stream 里改外部变量需要用 `AtomicInteger` 之类的丑陋写法 |
| 需要"同时开两个游标" | 手写 | Stream 是一次性的 |
| 需要并行 | 手写或先 `toList()` 再 `parallelStream()` | `spliteratorUnknownSize` 并行没收益 |
| 需要精确控制"读几个" | 手写 | Stream 的短路行为受中间操作影响，不易推理 |

**最关键的结论：Stream 不是迭代器的替代品，它是立在迭代器上面的一层。**

看这句话：

```java
StreamSupport.stream(flow.spliterator(), false)
```

`flow.spliterator()` 内部还是调 `flow.iterator()`。所以：

> **`implements Iterable` 一行，换来的是"整条 Stream 生态"。**
>
> 没有它，`stream()` / `forEach()` / `spliterator()` / `StreamSupport.stream()` / 任何 `<T extends Iterable<X>>` 的库函数，**全都用不了**。

这就是 §4.5 决定五在"扩展"层面的价值：它不是为了让 `for` 循环好看一点，它是为了接入整个 JDK 的工具链。

### 7.6.6 这一节的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| `implements Iterable` 是接入 Stream 生态的门票 | `Iterable.spliterator()` 是 default 方法 | 不实现 → `StreamSupport.stream()` 编译失败 |
| 未知大小的 `Iterable` 不适合并行流 | `spliteratorUnknownSize` + `trySplit` 要物化 | 用 `parallelStream()` → 比顺序流还慢 |
| Stream 建立在迭代器之上，不是替代它 | `flow.spliterator()` 内部调 `iterator()` | 以为 Stream 能替代迭代器 → 丢失"两个游标""边遍历边改"能力 |

---

## 7.7 递归汇总：这个需求不该用迭代器

### 7.7.1 需求

计算整棵树的"总耗时"：每个 `StepNode` 有耗时，容器节点的耗时是孩子之和。

### 7.7.2 用迭代器写会怎样

```java
// 想用迭代器实现"汇总"—— 需要后序，还要一个 Map 记住每个节点的累计值
Map<WorkflowNode, Long> totals = new IdentityHashMap<>();
for (WorkflowNode node : postOrderFlow) {
    long own = ownCost(node);
    for (WorkflowNode child : node.children()) {
        own += totals.getOrDefault(child, 0L);
    }
    totals.put(node, own);
}
long total = totals.get(root);
```

能跑，但引入了：

- 一个 `Map`（额外 O(n) 空间）；
- "后序保证孩子先算完"这个隐含依赖（如果换成前序，结果全错，且**不报错**）；
- 一个从"遍历"到"汇总"的思维转换。

### 7.7.3 递归写会怎样

```java
private static long totalCost(WorkflowNode node) {
    long sum = ownCost(node);
    for (WorkflowNode child : node.children()) {
        sum += totalCost(child);
    }
    return sum;
}

long total = totalCost(flow.roots().get(0));
```

**六行，零额外空间（除了方法栈），顺序天然正确（后序由递归结构自动保证）。**

### 7.7.4 改动清单

| 文件 | 改什么 | 行数 |
|---|---|---:|
| `Client.java` 或新的工具类 | 加一个 `totalCost` 方法 | 约 8 行 |
| 其余 10 个文件 | **零改动** | 0 |

### 7.7.5 为什么这个需求不该用迭代器

| 维度 | 迭代器 | 递归 |
|---|---|---|
| 需要"暂停"吗 | 不需要（一次算完） | 不需要 |
| 需要"多个游标"吗 | 不需要 | 不需要 |
| 需要"多种顺序"吗 | 需要后序，但递归天然就是后序 | 天然 |
| 需要"额外状态"吗 | 需要一个 `Map` | 不需要 |
| 代码行数 | 约 12 行 | 6 行 |
| 出错风险 | 换成前序会静默算错 | 低 |

**三个"不需要"，加一个"递归天然满足"，结论就是：这个需求不需要迭代器。**

这正是 §3.3 那节"什么时候不需要迭代器"的实例。**模式的适用边界，比模式本身更重要。**

更准确地说，判断标准是：

> **如果"遍历"和"处理"必须同时发生在每一个节点上，而且不需要中途暂停 —— 用递归。**
>
> **如果"遍历"和"处理"是两件事，或者遍历需要暂停/多游标/多顺序 —— 用迭代器。**

递归的 `totalCost` 里，"遍历"和"处理"合在一起了（`sum += totalCost(child)` 既是遍历也是累加）。而 `FilteringIterator` 里，"遍历"（`delegate.next()`）和"处理"（`predicate.test()`）是分开的。

### 7.7.6 这一节的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| "遍历"和"处理"合一、不需暂停时，递归更简单 | §7.7.5 对比表 | 硬套迭代器 → 多一个 `Map`，多一个顺序依赖 |
| 递归天然是后序，迭代器要显式实现 | §7.7.3 六行代码 | 用前序迭代器做汇总 → 静默算错 |

---

## 7.8 与访问者模式的边界：加第六种导出格式

### 7.8.1 需求

审批流要能导出成多种格式，每种格式对每种节点类型的处理都不一样：

| 格式 | `StepNode` | `BranchNode` | `SequenceNode` |
|---|---|---|---|
| 纯文本 | `[审批] 总监审批` | `? 金额 > 1000` | `> 报销主流程` |
| Markdown | `- **总监审批**` | `> 条件：amount > 1000` | `## 报销主流程` |
| JSON | `{"type":"step",...}` | `{"type":"branch",...}` | `{"type":"seq",...}` |
| Excel | 一行三列 | 一行带缩进 | 合并单元格 |
| PDF | 带图标 | 带菱形 | 带边框 |
| 思维导图 | 叶子节点 | 分支节点 | 折叠节点 |

**格式种类会不断增加**，而**节点类型相对稳定**。

### 7.8.2 用 `instanceof` 写会怎样

```java
private static String toMarkdown(WorkflowNode node) {
    if (node instanceof StepNode step) {
        return "- **" + step.name() + "**";
    } else if (node instanceof BranchNode branch) {
        return "> 条件：" + branch.condition();
    } else if (node instanceof SequenceNode seq) {
        return "## " + seq.name();
    } else if (node instanceof ParallelNode par) {     // ← 加 ParallelNode 时，每个格式都要补
        return "|| " + par.name();
    }
    throw new IllegalStateException("未知节点类型");
}
```

### 7.8.3 改动清单

| 加一个新格式 | 改什么 | 行数 |
|---|---|---|
| 用 `instanceof` | 新增 1 个方法（4 个分支） | 约 20 行 |
| **加一个新节点类型** | **改 N 个方法**（N = 格式数） | 20 × N 行 |
| 用访问者 | 新增 1 个 `Visitor` 类；**节点类各加一个 `accept()`** | 1 个类 + M 个节点各 3 行 |

**这就是"表达式问题"（Expression Problem）的现实版本：**

| 变化方向 | `instanceof` / 多态方法 | 访问者 |
|---|---|---|
| 节点类型稳定，**格式增加** | 加方法，**不动旧代码** ✓ | 加 Visitor 类，**不动旧代码** ✓ |
| **节点类型增加**，格式稳定 | **改 N 个方法** ✗ | 加 `visit()` 到所有 Visitor ✗ |
| 两者都增加 | 无解 | 无解 |

**没有一种设计能同时让两个方向都"只加不改"。这是语言层面的限制，不是模式能解决的。**

### 7.8.4 本项目为什么不用访问者

本项目在 §7.1 里**加了一个新节点类型（`ParallelNode`）**。如果用了访问者，那次改动就要：

- `ParallelNode` 加一个 `accept(Visitor)` 方法；
- **所有已有的 `Visitor` 实现类都要加一个 `visit(ParallelNode)` 方法**。

如果当时已经有 5 个导出格式，就是 5 个类都要改。而实际用的 `instanceof` 方案，加 `ParallelNode` 只需要：

- `WorkflowNode.permits` 加一个；
- 需要在导出时处理 `ParallelNode` 的地方，编译器会因为 `if/else if` 链不完整而**不报错**（`instanceof` 链不是穷尽检查），需要自己记得加。

**这是一个真实的权衡：**

| | `instanceof`（本项目） | 访问者 |
|---|---|---|
| 加节点类型 | 改 `permits` + **手动**检查所有 `instanceof` 链 | 改 `permits` + 所有 Visitor（**编译器强制**） |
| 加格式 | 加一个方法 | 加一个 Visitor 类 |
| 编译期安全 | 弱（`instanceof` 链不穷尽检查） | 强（接口方法必须实现） |
| 代码量 | 少（一个方法 4 个分支） | 多（一个 Visitor 接口 + M 个节点各 `accept()` + N 个实现类） |

本项目选 `instanceof`，因为：

1. 目前只有 `Client` 里一处 `instanceof`（过滤审批节点），没有"6 种格式"的需求；
2. §7.1 刚加了 `ParallelNode`，如果用了访问者，改动面会从 1 处变成"1 + 所有 Visitor"；
3. 一个 Visitor 接口 + 4 个节点各 3 行 `accept()` = 至少多出 50 行，为了过滤一个 `APPROVAL` 不值得。

### 7.8.5 什么时候该换成访问者

三个信号：

| 信号 | 具体表现 |
|---|---|
| `instanceof` 链出现在 3 个以上方法里 | 同一个节点类型判断在多个地方重复 |
| 每加一个节点类型，要改 3 个以上方法 | "节点类型增加"成了高频变化 |
| 导出/处理逻辑开始需要"按格式分组" | 比如 `MarkdownExporter` 需要一个类来装载所有相关方法 |

**一句话判据：**

> **节点类型稳定、操作种类在长 → 访问者。**
> **操作种类稳定、节点类型在长 → 多态方法（`children()` 那种）。**

本项目在 §7.1 选择了"节点类型可以长"，所以不用访问者。这是**有意识的取舍**，不是"忘了用访问者"。

### 7.8.6 与迭代器的关系：两者是正交的

访问者和迭代器经常被放在一起讲，但它们解决的是完全不同的问题：

| | 迭代器 | 访问者 |
|---|---|---|
| 解决 | **怎么走到下一个节点** | **走到节点后做什么** |
| 关注 | 顺序、进度、暂停 | 类型分派、操作集合 |
| 会不会互相依赖 | 迭代器不关心访问者 | 访问者常配合迭代器使用（遍历 + 访问） |
| 本项目 | 有（4 个迭代器） | 没有 |

如果真的要用访问者，典型组合是：

```java
for (WorkflowNode node : flow) {          // ← 迭代器负责"走"
    node.accept(markdownVisitor);         // ← 访问者负责"做"
}
```

**一行 for + 一行 accept，两个模式各司其职。** 迭代器不知道 `accept` 的存在，访问者也不知道 `iterator()` 的存在。

### 7.8.7 这一节的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 表达式问题无解：节点类型和操作种类不可能都"只加不改" | §7.8.3 的 3 行表 | 硬找一个"两边都不改"的设计 → 引入反射或 `Map<String, Function>`，丢掉编译期检查 |
| 节点类型会长 → 用多态；操作种类会长 → 用访问者 | §7.8.5 的一句话判据 | 判反了 → 每次扩展都改一大片 |
| 迭代器管"怎么走"，访问者管"做什么"，两者正交 | §7.8.6 | 把遍历逻辑写进 Visitor → 访问者里出现栈和队列，两个模式都变形 |

---

# 8. 优缺点与误区

## 8.0 这一章要解决什么

前面七章讲的都是"应该怎样"。这一章讲"**什么情况下不该这样**"，以及"**看起来像这样其实是错的**"。

分成四节：

| 节 | 回答的问题 |
|---|---|
| 8.1 | 什么场景该用？什么场景一眼就不该用？ |
| 8.2 | 用了之后要付什么代价？这些代价能不能量化？ |
| 8.3 | 九个流传很广、但经不起推敲的说法 |
| 8.4 | 和六个相似模式的分界线分别在哪 |

---

## 8.1 什么场景该用

### 8.1.1 五条正向判据

不是"有集合就该用迭代器"。真正的判据只有五条，**满足两条以上才值得考虑**：

| 判据 | 具体表现 | 本项目对应 |
|---|---|:---:|
| ① 容器结构对调用方封闭 | 调用方拿不到内部数组 / 链表 / 树 | ✓ |
| ② 遍历方式不止一种 | 同一份数据要看 DFS、BFS、后序、倒序 | ✓ |
| ③ 遍历需要暂停 | 一次处理一部分，状态要保留 | ✓ |
| ④ 需要多个游标同时走 | 两个指针各自记进度 | ✓ |
| ⑤ 遍历算法要复用到多种容器 | 一个 `printAll` 同时吃树和列表 | ✓ |

本项目五条全中，所以迭代器是必需品，不是装饰品。

### 8.1.2 五条反向判据：出现任意一条，先别用

| 反向判据 | 为什么 | 该用什么 |
|---|---|---|
| 容器就是 `int[]` 或 `Object[]`，调用方拿着引用 | 下标遍历零分配、零间接调用 | `for (int i = 0; i < a.length; i++)` |
| 遍历和处理必须同时发生，且不需要暂停 | 递归天然表达"到每个节点做一件事" | 递归（§7.7） |
| 只需要"全部处理一遍"，不需要顺序控制 | `forEach` 一句话 | `flow.forEach(...)` |
| 容器的生命周期只在一个方法内 | 拷进 `List` 再遍历，代码更短 | `List.copyOf` + for |
| 一次性、不可复用的遍历 | 迭代器的"可复用"优势用不上 | 直接递归或 Stream |

### 8.1.3 一个具体的四步决策流程

```text
第 1 问：容器是一维数组，且调用方拿着引用吗？
         ├─ 是 → 用下标 for，不需要模式。结束。
         └─ 否 → 第 2 问

第 2 问：遍历和处理是不是必须同时发生？会不会需要暂停？
         ├─ 同时发生、不需要暂停 → 递归。结束。
         └─ 不 → 第 3 问

第 3 问：只有一种遍历方式，而且不需要"先问再取"的语义吗？
         ├─ 是 → for-each（前提是容器已实现 Iterable）。结束。
         └─ 否 → 第 4 问

第 4 问：需要多种顺序 / 多个游标 / 可包装的过滤吗？
         ├─ 是 → 迭代器模式。本项目在这里。
         └─ 否 → for-each 够用，别加抽象。
```

**注意第 3 问和第 4 问的区别**：`for-each` 已经用上了 `Iterable`（也就是用了迭代器模式），但它只用了"一种"顺序。**很多人以为"没自己写 Iterator 类就不算用了模式"，其实 `for-each` 每跑一次就是在用迭代器模式。** 区别只在于"迭代器是谁写的"。

### 8.1.4 本项目五条判据的逐条落地

| 判据 | 在代码里怎么体现 |
|---|---|
| ① 结构封闭 | `Workflow.roots` 是 `private`，`roots()` 返回 `List.copyOf`；`WorkflowNode.children()` 返回不可变列表 |
| ② 多种遍历 | `DepthFirstIterator` / `BreadthFirstIterator` / `PostOrderIterator`（§7.2）/ 镜像 DFS（§7.3） |
| ③ 可暂停 | `Iterator` 是对象，`stack` 字段保存进度，随时可以停 |
| ④ 多游标 | Demo 1 的 `first` / `second`，两个独立对象 |
| ⑤ 算法可复用 | `printAll(Iterable<WorkflowNode>)` 同时接受树和 `List` |

---

## 8.2 设计代价

### 8.2.1 代价一：类数量从 4 变成 11

| 项目 | 文件数 | 物理行 |
|---|---:|---:|
| 原项目 | 4 | 109 |
| 本项目 | 11 | 1807 |

**多出来的 7 个文件，每一个都能说出"删了会怎样"**：

| 文件 | 删了会怎样 |
|---|---|
| `WorkflowNode.java` | 三种节点没有共同类型，`Workflow` 里得写三个 `List`，迭代器得写三份 |
| `StepNode.java` | 步骤只能用 `String` 表达，类型信息丢失（§2.9 的老问题） |
| `BranchNode.java` | 二选一表达不了，只能用"两个步骤名拼字符串" |
| `SequenceNode.java` | 顺序块表达不了，DFS 会把"总监线"和"主管线"当成两个平行分支 |
| `TraversalOrder.java` | 遍历顺序只能靠 `boolean` 或魔法值，调用方看不懂 |
| `DepthFirstIterator.java` | 没法 DFS |
| `BreadthFirstIterator.java` | 没法 BFS |
| `FilteringIterator.java` | 想过滤只能改容器或复制遍历代码 |

**这张表是"11 个文件是否过度设计"的唯一诚实答案。** 如果某个文件说不出"删了会怎样"，它才真的是过度设计。

### 8.2.2 代价二：每次 `next()` 多了一层间接调用

| 做法 | 一次遍历的调用开销 |
|---|---|
| 下标 for（`array[i]`） | 1 次数组访问 |
| 增强 for + 迭代器 | 每元素 2 次虚方法调用（`hasNext` + `next`）+ 1 次栈操作 |
| 增强 for + 过滤迭代器 | 每元素 2 次外层 + N 次内层 |

具体数字（Demo 1）：

| 指标 | 数值 |
|---|---:|
| 遍历 9 个节点 | `hasNext()` 10 次 + `next()` 9 次 = **19 次方法调用** |
| 下标 for 同样 9 个元素 | 9 次数组访问 + 9 次 `length` 读取 |

**19 次虚方法调用 vs 9 次数组访问。** 这个差距在 JIT 之后会大幅缩小（`DepthFirstIterator` 是 `final` 类，方法可能被内联），但**永远不会变成零**。

这个代价该不该付？看数据量：

| 数据量 | 建议 |
|---|---|
| 几十 ~ 几千个节点 | 随便用，差距在微秒级 |
| 几十万节点，热点路径 | 考虑用下标或 `for-each` 直接遍历数组 |
| 千万级 + 每帧都要跑 | 别用迭代器，别用 Stream，直接操作数组 |

**本项目的审批流节点是几十个量级，性能代价可以完全忽略。** 但如果有人拿这个模式去做"百万级日志行遍历"，就是误用。

### 8.2.3 代价三：可变状态带来的测试负担

三个迭代器共有 4 个可变状态：

| 类 | 可变字段 | 数量 |
|---|---|---:|
| `DepthFirstIterator` | `stack`（内容可变） | 1 |
| `BreadthFirstIterator` | `queue`（内容可变） | 1 |
| `FilteringIterator` | `pending`、`hasPending` | 2 |
| `PostOrderIterator`（§7.2） | `stack` + 每个 `Frame.nextChild` | 2 |

**每一个可变字段都是一个需要测试的状态。** 具体要测：

| 测试点 | 要验证什么 |
|---|---|
| 空容器 | `hasNext()` 立刻 false，`next()` 抛 `NoSuchElementException` |
| 单节点 | `hasNext()` true → `next()` 返回它 → `hasNext()` false |
| 多层树 | 顺序与手工推演一致 |
| 连续调 `hasNext()` | 不重复消耗（FilteringIterator 的 `hasPending` 短路） |
| 到底再取 | 每次都抛，不是只抛一次 |
| 遍历中改结构 | `next()` 抛 CME，`hasNext()` 不抛 |
| 两个游标 | 互不干扰 |

**七类测试 × 四个迭代器 = 28 个测试点。** 这是"可暂停"和"多游标"能力的真实成本。

对比：递归打印树只需要测"输出对不对"，不需要测状态 —— 因为它没有状态。

### 8.2.4 代价四：抽象层数增加，新人上手变慢

一个新人接手这个项目，要理解的层次：

```text
Workflow（容器，实现 Iterable）
  └─ iterator(TraversalOrder)  ← 一个方法，两个分支
       ├─ DepthFirstIterator（栈）
       ├─ BreadthFirstIterator（队列）
       └─ PostOrderIterator（栈 + 帧）
WorkflowNode（sealed 接口）
  ├─ StepNode（叶子）
  ├─ BranchNode（二选一）
  ├─ SequenceNode（顺序）
  └─ ParallelNode（并行，§7.1 扩展）
FilteringIterator（包装任意 Iterator）
TraversalOrder（枚举，两种值）
StepType（枚举，五种值）
```

**11 个类型，5 层关系。** 原项目只有 4 个类型、1 层关系。

这个代价能不能降低？能，但只能靠文档 —— 也就是你现在读的这份。**代码本身不会自我解释"为什么 children() 要放在节点上"，只有文档能。**

这也是为什么任务书要求"每种模式一份 Markdown 学习文档"，而不是"每种模式一份代码"。

### 8.2.5 代价汇总：什么情况下这些代价是不值得的

| 场景 | 值不值得 |
|---|---|
| 一次性的数据转换（读 CSV 转 JSON） | **不值得**。递归或 Stream 就够 |
| 只在 `main` 里演示一下 | **不值得**。除非演示的就是这个模式 |
| 团队里没人维护，只跑一次 | **不值得**。11 个文件是维护负担 |
| 业务会持续加遍历需求 | **值得**。§7 的八张改动清单就是回报 |
| 需要教别人"为什么这样设计" | **值得**。因为它把每个决定都摆在明面上 |

---

## 8.3 九个常见误解

### 误解一："迭代器就是 for 循环的语法糖"

**为什么有人这么想**：`for (X x : list)` 看起来就是个循环，和 `for (int i = 0; ...)` 没什么区别。

**为什么错**：for-each 只是**语法**，迭代器是**协议**。区别在三个能力上：

| 能力 | 下标 for | 迭代器 |
|---|---|---|
| 遍历数组 | ✓ | ✓ |
| 遍历链表（无下标） | ✗ | ✓ |
| 遍历树（无线性顺序） | ✗ | ✓ |
| 两种以上遍历顺序 | ✗ | ✓ |
| 中途暂停、恢复 | ✗（除非把 `i` 提到外面） | ✓（游标就是对象） |
| 多个游标同时走 | 手动维护多个 `i` | ✓（`new` 两个） |
| 在遍历之上再包一层（过滤） | ✗ | ✓ |

**正确理解**：for-each 是迭代器的**一种用法**，不是它的等价物。把 `for (WorkflowNode node : flow)` 展开成 `while (it.hasNext())` 之后，你手上多了一个**对象** —— 这个对象可以被保存、传递、包装、复制。语法糖不会给你这些。

### 误解二："实现了 `Iterable` 就必须自己写一个 `Iterator` 类"

**为什么有人这么想**：接口要求实现方法，所以必须写一个实现类。

**为什么错**：`iterator()` 可以返回**任何** `Iterator`，包括：

| 做法 | 例子 |
|---|---|
| 自己写一个类 | 本项目的 `DepthFirstIterator` |
| 返回匿名类 | `return new Iterator<>() { ... }` |
| 委托给别的集合 | `return list.iterator();` |
| 用 `Stream` 的迭代器 | `return stream.iterator();` |

本项目三种都用了：

```java
// 自己写一个类
public Iterator<WorkflowNode> iterator() {
    return iterator(TraversalOrder.DEPTH_FIRST);
}

// 委托给别的集合（FilteringIterator 内部）
private final Iterator<WorkflowNode> delegate;

// 返回匿名类（JDK 的 List.of 干的事，我们没写但用了）
```

**正确理解**：`implements Iterable` 是承诺"我能被遍历"，不是承诺"我有一个专门的迭代器类"。

### 误解三："迭代器必须实现 `remove()`"

**为什么有人这么想**：`java.util.Iterator` 接口上有 `remove()` 方法。

**为什么错**：`remove()` 是 **default 方法**，默认实现直接抛 `UnsupportedOperationException`：

```java
// java.util.Iterator
default void remove() {
    throw new UnsupportedOperationException("remove");
}
```

所以**不实现它是完全合法的**。JDK 里大量迭代器都不支持 `remove()`（比如 `List.of(...)` 的迭代器、`Arrays.asList(...)` 的迭代器）。

本项目的四个迭代器都**没有**实现 `remove()`，理由：

| 理由 | 说明 |
|---|---|
| 节点的"删除"不是迭代器的职责 | 删除要改 `Workflow.roots`，那是容器的事 |
| 删除会让 `modCount` 检查立刻失效 | 迭代器删一个，`modCount` 加一，自己把自己搞成"非法状态" |
| `FilteringIterator` 根本不知道"上一个返回的是哪个" | 它只记 `pending`，`remove()` 无从下手 |

**正确理解**：`Iterator` 的最小契约只有 `hasNext()` + `next()`。`remove()`、`forEachRemaining()`、`default` 的 `forEach()` 都是可选的。

如果真要支持删除，正确做法是让容器提供 `remove(node)`，而不是迭代器去删 —— 因为"删哪个"是业务问题，不是遍历问题。

### 误解四："`hasNext()` 和 `next()` 必须严格配对调用"

**为什么有人这么想**：既然要先问再取，那每次 `next()` 之前必须有一次 `hasNext()`。

**为什么错**：契约只规定了两件事：

| 契约 | 内容 |
|---|---|
| ① | `hasNext()` 返回 `true` 时，`next()` **必须**能返回一个元素 |
| ② | `hasNext()` 返回 `false` 时，`next()` **必须**抛 `NoSuchElementException` |

**没有任何一条要求"每次 `next()` 前必须先调 `hasNext()`"。**

所以下面这段是合法的：

```java
Iterator<WorkflowNode> it = flow.iterator();
WorkflowNode a = it.next();      // ← 没调 hasNext()，合法（前提是知道非空）
WorkflowNode b = it.next();
WorkflowNode c = it.next();      // ← 如果只有 2 个，这里抛 NoSuchElementException
```

代价是：不调 `hasNext()` 就无法预先知道什么时候会抛异常。所以实践上几乎总是配对使用 —— 但这是**方便**，不是**必须**。

本项目 Demo 5 就是这么写的：

```java
Iterator<WorkflowNode> cursor = flow.iterator();
System.out.println("  取了第 1 个：" + cursor.next());      // ← 直接 next，没先问
```

因为代码知道 `flow` 里有 2 个节点。

**正确理解**：`hasNext()` 是"建议"，不是"许可证"。**唯一的硬约束是 ②：`hasNext()` 为 false 时 `next()` 必须抛异常，不能返回 null。**

### 误解五："遍历时修改容器一定抛 `ConcurrentModificationException`"

**为什么有人这么想**：`ArrayList` 的 javadoc 写着 "if the list is structurally modified at any time after the iterator is created ... the iterator will throw a ConcurrentModificationException"。

**为什么错**：javadoc 的原文后面还有一句关键的话 —— **"in the face of concurrent modification, the iterator fails quickly and cleanly, rather than risking arbitrary, non-deterministic behavior at an undetermined time in the future."**

`fail-fast` 是**尽力而为**（best-effort），不是保证：

| 情况 | 会不会抛 |
|---|---|
| 单线程，遍历中 `add` | 会（下次 `next()` 时） |
| 单线程，遍历中 `add` 然后 `remove` 抵消 | **`modCount` 还是会变，照样抛**（这是 `modCount` 比 `size` 好的地方） |
| 多线程，`modCount` 没被 `volatile` 刷新 | **可能不抛**，静默读到旧数据 |
| 遍历已经结束，才 `add` | 不会（不检查了） |
| 只改元素内容，不改结构（`set`） | 不会（`modCount` 不变） |

第三行是最危险的：**多线程场景下 fail-fast 可能失效**，因为 `modCount` 的写对另一个线程不可见。所以：

> **`ConcurrentModificationException` 是"帮你发现 bug 的哨兵"，不是"并发安全的保证"。**
>
> 真要做并发安全，得用 `java.util.concurrent` 里的容器（`CopyOnWriteArrayList`、`ConcurrentHashMap`），或者用 §7.4 的方案 B。

**正确理解**：CME 是**诊断工具**，不是**同步机制**。看到它说明代码有 bug；看不到它**不代表**没有 bug。

### 误解六："`FilteringIterator` 应该继承 `DepthFirstIterator`，代码可以复用"

**为什么有人这么想**：过滤迭代器和 DFS 迭代器有大量相同逻辑（`hasNext`、`next`、`modCount` 检查），继承能省代码。

**为什么错**：三个致命问题：

| 问题 | 具体后果 |
|---|---|
| 只能包 DFS | `new FilteringIterator(new BreadthFirstIterator(...))` 编译失败 —— BFS 不是 DFS 的子类 |
| 无法嵌套 | 包两层过滤器要 `FilteringIterator extends FilteringIterator`，不可能 |
| 继承的字段没用 | `stack` 在过滤器里根本不该存在，却被继承下来，还占内存 |

更根本的问题：**过滤不是"一种 DFS"，它是"对任意迭代器的装饰"。** 这两者是 `is-a` 和 `has-a` 的区别，不能互换。

真实对照：

```java
// 实际做法：组合
public final class FilteringIterator implements Iterator<WorkflowNode> {
    private final Iterator<WorkflowNode> delegate;      // ← has-a
}

// 错误做法：继承
public final class FilteringIterator extends DepthFirstIterator {   // ← is-a（错）
    // 想包 BFS 就没办法了
}
```

**正确理解**：**"代码看起来一样"不是继承的理由，"是不是同一种东西"才是。** 过滤器和 DFS 是"装饰者"与"被装饰者"的关系，用组合。

这也顺带解释了为什么 `FilteringIterator` 的构造器参数类型是 `Iterator<WorkflowNode>` 而不是 `DepthFirstIterator` —— **这个类型选择本身就是设计**。写成后者，§6.7.4 的三层嵌套就编译不过。

### 误解七："树的遍历顺序应该由容器决定"

**为什么有人这么想**：`List` 的顺序是容器决定的（`ArrayList` 按插入顺序，`TreeSet` 按比较器），所以树的顺序也该由 `Workflow` 决定。

**为什么错**：`List` 的"顺序"和"遍历顺序"是两回事：

| | `ArrayList` | `Workflow`（树） |
|---|---|---|
| 元素的固有顺序 | 有（插入顺序） | **没有**（树只有父子关系） |
| 遍历策略 | 只有一种（顺着数组） | 至少四种（前序、BFS、后序、镜像） |
| 顺序该放哪 | 容器（因为它只有一个） | **迭代器**（因为有多个） |

树没有"固有线性顺序"。前序、BFS、后序、镜像前序，都是**观察者选择的视角**，不是树的属性。

如果把 `order` 塞进 `Workflow`：

```java
flow.setOrder(TraversalOrder.BREADTH_FIRST);      // ← 不要这样做
for (WorkflowNode node : flow) { ... }
```

三个后果：

| 后果 | 说明 |
|---|---|
| 两次遍历会互相污染 | 第二个调用方设置的顺序会影响第一个 |
| 容器变成有状态 | 一个"数据"对象变成了"带配置的服务"对象 |
| 无法同时两种顺序 | 要同时 BFS 和 DFS，得复制整个 `Workflow` |

**正确理解**：**容器描述"是什么"，迭代器描述"怎么看"。** §6.2.6 那张表（7 个文件里只有 2 个迭代器有顺序字段）就是这条的静态证据。

### 误解八："有了 Stream，迭代器模式就过时了"

**为什么有人这么想**：`stream().filter().map().collect()` 一行顶十行，谁还手写 `while`？

**为什么错**：Stream 建立在迭代器**之上**，不是它的替代品：

```java
// StreamSupport 的第一件事就是拿 iterator()
StreamSupport.stream(flow.spliterator(), false)
//                          ↑ 内部就是 iterator()
```

四个"Stream 做不到"的场景：

| 场景 | 为什么 Stream 不行 |
|---|---|
| 两个游标同时走 | Stream 是一次性的，`forEach` 走完就废 |
| 中途暂停，过一会儿继续 | Stream 的中间状态不可保存（除非用 `iterator()` 硬拿） |
| 嵌套过滤（过滤器包过滤器） | Stream 的 `filter` 可以链式，但拿不到"每次跳过了几个"这种元信息 |
| 边遍历边改容器 | Stream 的懒执行会让"改"和"遍历"的时机完全错乱 —— 这是 CME 的高发区 |

还有一条更根本的：**Stream 没有"失败"这个语义。**

- `Iterator.next()` 到底会抛 `NoSuchElementException` —— 这是一个**明确的错误信号**；
- Stream 走完了就是走完了，不会告诉你"你其实想要第 10 个但只有 9 个"，除非你显式收集成 `List` 再判断。

**正确理解**：

> **`implements Iterable` 让 Stream 成为可能；Stream 让"简单遍历"变短；迭代器让"复杂遍历"成为可能。**
>
> 三者是层次关系，不是替代关系。

### 误解九："`next()` 到底返回 `null` 是合理的，调用方判断一下就行"

**为什么有人这么想**：`null` 表示"没有"，简单直接，还省一个异常的开销。

**为什么错**：四个具体问题：

| 问题 | 说明 |
|---|---|
| 分不清"到底了"和"元素是 null" | 调用方无法区分两种完全不同的情况 |
| 错误现场远离源头 | NPE 爆在"用返回值"那一行，栈顶指向使用者而不是多取者（§2.5 探针 2） |
| 异常类型不精确 | NPE 可能是任何原因，无法 `catch` 到"就是到底了" |
| 破坏了 `Iterator` 契约 | 契约第 ② 条明确要求 `hasNext() == false` 时 `next()` 抛 `NoSuchElementException` |

第四条最严重：**这不是风格问题，是违约。** 任何按 `Iterator` 契约编程的库函数（比如 `StreamSupport`、`Iterables.concat`）拿到一个"到底返回 null"的迭代器，行为都是未定义的。

本项目 `FilteringIterator.next()` 的写法是标准答案：

```java
@Override
public WorkflowNode next() {
    if (!hasNext()) {
        throw new NoSuchElementException("在「" + description + "」条件下已经没有更多节点");
    }
    ...
}
```

注意消息里带了**上下文**（`description`），这样日志里一眼就能看出来是哪个过滤器到底了。这是"异常消息要带足够信息"的实例。

**正确理解**：`null` 是一个**值**，不是一个**信号**。用值来表示信号，是所有 API 设计里最常见的错误之一。

---

## 8.4 与相似模式的对比

### 8.4.1 总表

| 对比对象 | 一句话区别 | 判据 |
|---|---|---|
| 组合模式 | 组合管"结构"，迭代器管"访问" | 需要统一对待叶子/容器 → 组合；需要多种顺序 → 迭代器 |
| 访问者模式 | 访问者管"做什么"，迭代器管"怎么走" | 节点类型稳定、操作增长 → 访问者；节点类型增长 → 多态 |
| 工厂方法 | `iterator()` 本身就是一个工厂方法 | 见 8.4.3 |
| 模板方法 | `AbstractList.iterator()` 是模板方法 | 见 8.4.4 |
| 策略模式 | 迭代器是"策略实现"，`TraversalOrder` 是"策略选择器" | 见 8.4.5 |
| 观察者模式 | 观察者是 push，迭代器是 pull | 见 8.4.6 |

### 8.4.2 与组合模式：结构 vs 访问

**组合模式的核心**：让"叶子"和"容器"实现同一个接口，调用方统一对待。

本项目的 `WorkflowNode` 确实是组合模式：

```java
public sealed interface WorkflowNode permits StepNode, BranchNode, SequenceNode {
    String name();
    List<WorkflowNode> children();       // ← 组合模式的核心方法
    default boolean isLeaf() { return children().isEmpty(); }
}
```

`StepNode` 是叶子（`children()` 返回空），`SequenceNode` / `BranchNode` 是容器。**调用方看不出区别。**

**两者的分工**：

| 职责 | 谁负责 |
|---|---|
| 定义"一个节点有哪些孩子" | 组合模式（`children()`） |
| 定义"按什么顺序访问所有节点" | 迭代器模式（`iterator()`） |

**如果只有组合模式，没有迭代器**，能做什么？

```java
// 递归打印：只用组合模式，不需要迭代器
private static void print(WorkflowNode node, int depth) {
    System.out.println("  ".repeat(depth) + node);
    for (WorkflowNode child : node.children()) {
        print(child, depth + 1);
    }
}
```

这段完全不需要迭代器。**组合模式自己就能完成"遍历"** —— 通过递归。

**那什么时候才需要迭代器？** 当"遍历"这件事需要**对象化**的时候：

| 需求 | 组合模式的递归 | 迭代器 |
|---|---|---|
| 打印整棵树 | ✓ 够 | 也行 |
| 计算总耗时 | ✓ 够 | 也行（但更繁，§7.7） |
| **中途暂停** | ✗ 做不到 | ✓ |
| **两个游标** | ✗ 做不到 | ✓ |
| **切换遍历顺序** | ✗ 顺序写死在递归里 | ✓ |
| **在遍历外面再包一层过滤** | ✗ 要改递归函数 | ✓ |

**结论**：组合模式和迭代器是**互补**的，不是竞争的。本项目两个都用了，各管一半。

### 8.4.3 与工厂方法：`iterator()` 就是一个工厂方法

这个关系最容易被忽略。

**工厂方法模式的核心**：定义一个创建对象的接口，让子类决定实例化哪个类。

看本项目的 `Workflow.iterator(TraversalOrder)`：

```java
public Iterator<WorkflowNode> iterator(TraversalOrder order) {
    Objects.requireNonNull(order, "遍历顺序不能为 null");
    return switch (order) {                              // ← 根据参数决定造哪个类
        case DEPTH_FIRST -> new DepthFirstIterator(this);
        case BREADTH_FIRST -> new BreadthFirstIterator(this);
    };
}
```

**这就是一个工厂方法**：

| 工厂方法的要素 | 在本项目里 |
|---|---|
| 返回一个接口类型 | `Iterator<WorkflowNode>` |
| 隐藏具体类 | 调用方不知道 `DepthFirstIterator` 的存在 |
| 集中创建逻辑 | 只有这一处 `new` |
| 调用方只关心"能用" | `for (node : flow)` 不问怎么造的 |

`java.util.Collection.iterator()` 本身就是教科书级的工厂方法 —— 接口声明 `Iterator<E> iterator()`，`ArrayList` 返回 `Itr`，`LinkedList` 返回 `ListItr`，调用方完全不知道。

**所以"迭代器模式"和"工厂方法模式"在这里是嵌套关系**：迭代器模式需要"创建迭代器"，创建这一步用的是工厂方法。

> **这解释了一个常见的困惑**："为什么 `iterator()` 要放在容器上，而不是让调用方 `new DepthFirstIterator(flow)`？"
>
> 因为那样调用方就**知道了具体类**，容器就失去了"换实现"的自由。如果哪天把 DFS 换成递归版，所有 `new DepthFirstIterator` 的地方都要改。
>
> 把 `new` 关进 `iterator()` 里，调用方永远只写 `flow.iterator()`。

### 8.4.4 与模板方法：`AbstractList.iterator()` 是模板方法

**模板方法的核心**：父类定义算法骨架，子类实现可变步骤。

JDK 的 `java.util.AbstractList` 就是这样实现 `iterator()` 的：

```java
// 骨架：AbstractList.Itr 定义了"怎么遍历"
private class Itr implements Iterator<E> {
    int cursor = 0;

    public boolean hasNext() {
        return cursor != size();           // ← 调子类的 size()
    }

    public E next() {
        checkForComodification();
        try {
            int i = cursor;
            E next = get(i);               // ← 调子类的 get(int)
            cursor = i + 1;
            return next;
        } catch (IndexOutOfBoundsException e) {
            checkForComodification();
            throw new NoSuchElementException();
        }
    }
}
```

**`Itr` 是模板方法，`get(int)` 和 `size()` 是可变步骤。** 所以 `ArrayList` 和 `LinkedList` 都能用同一个 `Itr` —— 只要实现 `get` 和 `size`。

| 角色 | JDK | 本项目 |
|---|---|---|
| 模板方法 | `AbstractList.Itr` | `DepthFirstIterator` / `BreadthFirstIterator` |
| 可变步骤 | `get(int)` / `size()` | **没有** —— 本项目直接写完整实现 |
| 为什么 | JDK 要支持任意 `List` 实现 | 本项目只有一种容器（`Workflow`），不需要模板 |

**这解释了本项目的设计选择**：没有 `AbstractWorkflowIterator` 抽象类，因为只有一种容器。如果将来有 `Workflow`、`SubWorkflow`、`TemplateWorkflow` 三种容器共用遍历逻辑，才值得抽出来。

> **过度设计的典型症状：为"将来可能的第二个实现"抽一个抽象类。** 本项目只有一种容器，所以四个迭代器都是 `final class`，没有共同的父类 —— 它们只在 `Iterator` 接口这一层"相遇"。

### 8.4.5 与策略模式：`TraversalOrder` 是策略选择器

**策略模式的核心**：把算法封装成对象，让它们可以互换。

本项目的"策略"就是迭代器：

```java
// 策略接口
Iterator<WorkflowNode> it = flow.iterator(TraversalOrder.DEPTH_FIRST);
Iterator<WorkflowNode> it2 = flow.iterator(TraversalOrder.BREADTH_FIRST);
```

**但这里有一个微妙之处：本项目没有 `TraversalStrategy` 接口。**

标准策略模式的写法是：

```java
interface TraversalStrategy {
    Iterator<WorkflowNode> create(Workflow flow);      // ← 策略接口
}
class DfsStrategy implements TraversalStrategy { ... }
class BfsStrategy implements TraversalStrategy { ... }

// 使用
Iterator<WorkflowNode> it = strategy.create(flow);
```

而本项目写的是：

```java
return switch (order) {                                // ← 用枚举 + switch 代替策略对象
    case DEPTH_FIRST -> new DepthFirstIterator(this);
    case BREADTH_FIRST -> new BreadthFirstIterator(this);
};
```

**两种做法的对比**：

| | 策略对象（标准做法） | 枚举 + switch（本项目） |
|---|---|---|
| 加一种策略 | 新增一个 `implements TraversalStrategy` 的类 | 枚举加常量 + switch 加 case |
| 编译期穷尽检查 | 无（接口可以任意实现） | **有**（§7.2.3 的 `switch` 表达式） |
| 运行期动态加载 | 可以（反射 / SPI） | 不行 |
| 代码量 | 1 个接口 + N 个类 | 1 个枚举 + 1 个 switch |
| 适合 | 策略由**第三方**提供 | 策略集合**封闭已知** |

**本项目选"枚举 + switch"，理由是"策略集合封闭已知"** —— 遍历顺序就那几种，不会由第三方扩展。这时候枚举的**编译期穷尽检查**比策略接口的"开放扩展"更有价值（§7.2.3 已经验证了这个检查的作用）。

**判据**：

| 策略集合 | 选什么 |
|---|---|
| 封闭、已知、数量少 | 枚举 + `switch` 表达式 |
| 开放、可能由外部扩展 | 接口 + 实现类 |

> **这是"策略模式"的一个变体，不是"没用策略模式"。** GoF 的书里策略模式要求"定义一系列算法，把它们封装起来，并使它们可以互相替换" —— 枚举 + switch 同样让"遍历算法可以互相替换"，只是用枚举值而不是对象来标识。

### 8.4.6 与观察者模式：pull vs push

这是最有意思的一组对比。

**观察者模式**：主题有变化时，**主动推**数据给观察者。

```java
// 观察者：主题 push
subject.addObserver(observer);
subject.notifyObservers(data);          // ← 主题推
```

**迭代器模式**：调用方有需要时，**主动拉**数据。

```java
// 迭代器：调用方 pull
while (it.hasNext()) {                  // ← 调用方拉
    WorkflowNode node = it.next();
}
```

| 维度 | 观察者（push） | 迭代器（pull） |
|---|---|---|
| 谁决定"什么时候给" | 主题 | 调用方 |
| 谁决定"给多少" | 主题（一次给全部观察者） | 调用方（一次取一个） |
| 数据流向 | 主题 → 观察者 | 消费者 ← 容器 |
| 调用关系 | 一对多 | 一对一（一个游标对一个消费者） |
| 典型陷阱 | 通知顺序、循环通知、内存泄漏 | 并发修改、游标失效、null |
| 本项目 | 无（观察者是另一个模式） | 四个迭代器 |

**两者可以组合**：一个容器既是 `Iterable`，又是 `Observable`：

```java
for (WorkflowNode node : flow) {         // ← pull：主动遍历
    flow.notifyObservers(node);          // ← push：通知别人
}
```

这一行里，同一个 `flow` 既被"拉"又被"推"。**两个模式在同一个对象上并存，因为它们关心的方向相反。**

> **一个记忆口诀**：
>
> - 迭代器是"**我**要一个"，观察者是"**它**给你一个"。
> - 迭代器怕"数据变了"，观察者怕"数据没变"。

### 8.4.7 六组对比的汇总判据

| 如果你在纠结…… | 问自己一个问题 |
|---|---|
| 用组合还是迭代器？ | 需要"统一对待叶子/容器"吗？→ 组合。需要"多种顺序/暂停"吗？→ 迭代器。**都需要就都上。** |
| 用访问者还是迭代器？ | 变化的是"节点类型"还是"操作种类"？前者 → 多态。后者 → 访问者。**迭代器两样都不管，它只管走。** |
| `iterator()` 算不算工厂方法？ | 算。而且这是"把 `new` 关进容器"的唯一方式。 |
| 要不要抽 `AbstractIterator` 模板类？ | 有第二个容器实现吗？没有就别抽。 |
| 用策略对象还是枚举 + switch？ | 策略会由第三方扩展吗？不会就用枚举（换来编译期穷尽检查）。 |
| 观察者和迭代器会不会冲突？ | 不会。方向相反，可以并存（pull + push）。 |

---

# 9. 面试与自测

## 9.0 这一章怎么用

三种用法：

| 用法 | 怎么做 |
|---|---|
| **面试前突击** | 只看 9.1 的十道题，每题先自己答一遍，再看参考答案 |
| **学完自检** | 做 9.2 的 40 条清单，勾不出来的回对应章节重读 |
| **动手巩固** | 做 9.3 的五道题，每道都有可执行的验收标准 |

9.1 的每道题都是"三段式"：**问题 → 参考答案 → 追问**。追问部分是面试官最爱接着问的，也是最容易露馅的地方。

---

## 9.1 十道面试题

### 题一：迭代器模式解决什么问题？为什么不能直接用 `for` 循环？

**参考答案**

`for (int i = 0; i < array.length; i++)` 能工作，依赖三个隐含假设（§3.2）：

1. 容器能用整数下标访问；
2. 下标是连续的（0、1、2……没有空洞）；
3. 调用方知道容器的大小和内部结构。

三棵树、链表、图、无限流，这三个假设都不成立。

迭代器模式把"**怎么走**"从"**走的人**"手里拿出来，交给容器自己：

```java
// 调用方只承诺三件事：能问、能取、取完为止
while (it.hasNext()) {
    WorkflowNode node = it.next();
}
```

**没有暴露的东西**：树的深度、节点的类型、用的是栈还是队列、有几个根。

**追问一：那 `for-each` 算不算迭代器模式？**

算。`for (X x : list)` 编译后就是 `iterator()` + `hasNext()` + `next()`。区别只在于"迭代器是谁写的" —— JDK 写的还是你写的。

**追问二：什么情况下不需要迭代器？**

三个都不要：容器是一维数组、调用方拿着引用、只需要一种遍历。这时下标 `for` 是最优解（零分配、零间接调用）。**§1.3 专门讲了这一节，因为大多数教材不讲"什么时候不用"。**

**本项目证据**：§1.3（不需要的场景）、§3.2（三个隐含假设）、§8.1（五条正反判据）。

---

### 题二：`Iterable` 和 `Iterator` 有什么区别？为什么 `Iterator` 不实现 `Iterable`？

**参考答案**

| | `Iterable` | `Iterator` |
|---|---|---|
| 一句话 | "我能被遍历" | "我正在遍历" |
| 方法 | `iterator()` | `hasNext()` / `next()` |
| 状态 | 无（容器本身） | 有（进度条） |
| 能否重复用 | 能（每次 `iterator()` 都是新的） | 不能（走到头就废了） |
| 类比 | 一本书 | 一枚书签 |

**为什么 `Iterator` 不实现 `Iterable`？**

因为语义矛盾。如果 `Iterator` 实现了 `Iterable`，那就可以写：

```java
for (X x : iterator) { ... }        // ← 假设 Iterator 是 Iterable
```

而这个 `for` 循环内部会调 `iterator.iterator()`，返回什么？返回自己？那第二个 `for` 循环就会从第一个停下的地方继续 —— 这就是 bug。

JDK 曾经考虑过让 `Iterator` 实现 `Iterable`（方便在 `for-each` 里直接用），但最终拒绝了，理由是"**这会让 `Iterator` 的语义变得模糊**"。

**追问一：那为什么 `Stream` 实现了 `Iterable`？**

`Stream<T>` 的 `iterator()` 返回 `(Iterator<T>) spliterator()`，而且**只能调一次**（第二次抛 `IllegalStateException`）。这是"流是一次性的"这个语义带来的例外，不是通例。

**追问二：`Iterable` 为什么有 `forEach()` 和 `spliterator()`？**

都是 `default` 方法，建立在 `iterator()` 之上：

```java
default void forEach(Consumer<? super T> action) {
    Objects.requireNonNull(action);
    for (T t : this) { action.accept(t); }
}

default Spliterator<T> spliterator() {
    return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED);
}
```

**这两个 default 方法是"实现 `Iterable` 一行，换来整个 JDK 工具链"的具体内容**（§7.6）。

**本项目证据**：§4.5（为什么必须 `implements Iterable`）、§6.3（`printAll` 只认 `Iterable`）、§7.6（Stream 建立在迭代器上）。

---

### 题三：`iterator()` 为什么必须每次返回新对象？

**参考答案**

因为 `Iterator` 是**有状态**的。它记着"走到哪了"。

如果 `iterator()` 返回缓存的同一个实例：

```java
Iterator<WorkflowNode> first = flow.iterator();
Iterator<WorkflowNode> second = flow.iterator();     // ← 拿到的是同一个对象

first.next();       // 走到第 1 个
first.next();       // 走到第 2 个
second.next();      // ← 从第 3 个开始！因为 second 就是 first
```

**而且不报错。** 这是最坏的一类 bug：结果错了，程序还在跑。

正确的实现只有一个字：`new`。

```java
public Iterator<WorkflowNode> iterator() {
    return iterator(TraversalOrder.DEPTH_FIRST);      // → new DepthFirstIterator(this)
}
```

**追问一：这条约定写在哪？**

**没写在 `Iterable` 的 javadoc 里。** javadoc 只说"Returns an iterator over elements of type T"，没说"必须是新的"。

但整个 JDK 都遵守它：`ArrayList.iterator()` 返回 `new Itr()`，`HashMap.entrySet().iterator()` 返回 `new EntryIterator()`，无一例外。

**为什么没写进 javadoc？因为写了也没法检查。** 编译器管不了"返回值是不是同一个对象"，只能在运行时表现为"第二个 `for` 循环零输出"。

**追问二：`Iterable` 契约还有哪些"没写出来但必须遵守"的约定？**

| 约定 | 违反了会怎样 |
|---|---|
| `iterator()` 每次返回新对象 | 第二个 `for` 循环从第一个停下的地方继续 |
| `hasNext()` 不推进游标 | 循环少遍历一半 |
| `next()` 在没元素时抛 `NoSuchElementException` | 调用方拿到 `null`，错误延后爆发 |
| `hasNext()` 为 `true` 时 `next()` 必须能返回元素 | 调用方看到 `hasNext() == true` 却收到异常 |

**本项目证据**：§6.1.5（两个游标的真实输出）、§5.7（`Workflow` 唯一一处 `new`）。

---

### 题四：为什么 `next()` 到底要抛异常而不是返回 `null`？

**参考答案**

四个理由：

1. **分不清"到底了"和"元素是 null"**。调用方拿到 `null`，不知道是该停还是数据有问题。
2. **错误现场远离源头**。NPE 爆在"用返回值"那一行，栈顶指向使用者而不是多取者（§2.5 探针 2）。
3. **异常类型不精确**。NPE 可能是任何原因，无法 `catch` 到"就是到底了"。
4. **违反 `Iterator` 契约**。契约明确要求 `hasNext() == false` 时 `next()` 抛 `NoSuchElementException`。

第 4 条最严重 —— 这不是风格问题，是**违约**。任何按契约编程的库函数（`StreamSupport`、`Iterables.concat`）拿到一个"到底返回 null"的迭代器，行为都是未定义的。

**追问一：为什么是 `RuntimeException` 而不是受检异常？**

因为"多取一次"是**调用方的编程错误**，不是可恢复的运行时状况。

正确的代码永远不该触发它（先 `hasNext()` 再 `next()`）。如果设计成受检异常，每个 `next()` 都要 `try-catch`，而 99.99% 的代码根本不会触发 —— 纯粹的形式负担。

类比 `arr[3]` 抛 `ArrayIndexOutOfBoundsException`：没人给数组下标访问包 `try-catch`，因为越界就是写错了。

**追问二：`FilteringIterator` 抛异常时消息里为什么要带 `description`？**

```java
throw new NoSuchElementException("在「" + description + "」条件下已经没有更多节点");
```

因为过滤器的 `next()` 可能被**多层包裹**调用（§6.7.6 的调用栈有 6 层）。光看栈顶看不出是哪个过滤器到底了。消息里带上 `description`，日志里一眼就能定位。

**异常消息必须带足够的上下文** —— 这是所有异常设计的通用原则。

**本项目证据**：§2.5（探针 2 的 NPE 现场）、§6.6（对照表）、§5.10（`FilteringIterator` 的消息设计）。

---

### 题五：fail-fast 是怎么实现的？为什么 `modCount` 要放在容器里？

**参考答案**

两个字段：

```java
// 容器里
private int modCount;                    // 结构被修改的次数，只增不减

// 迭代器里
private final int expectedModCount;      // 创建时的快照

// 检查
void checkForComodification(int expected) {
    if (modCount != expected) {
        throw new ConcurrentModificationException(...);
    }
}
```

**关键在"快照"两个字。** `expectedModCount` 是 `final int`，在构造器里赋一次值，此后再不改。

如果写成实时读：

```java
if (workflow.modCount() != workflow.modCount()) { ... }      // 恒为 false
```

等号两边都是同一次读，永远相等 —— 检查形同虚设。**等号一边必须是"过去的值"。**

**为什么 `modCount` 放在容器里？**

（1）**"改了几次"是容器的事实，不是迭代器的事实。** 放迭代器里，每个迭代器各自加自己的计数 —— `DepthFirstIterator` 加到 5，`BreadthFirstIterator` 还是 0，谁都不代表容器的真实状态。

（2）**检查逻辑只有一份。** `checkForComodification` 是包级可见的方法，两个迭代器共用。否则会出现"DFS 抛异常、BFS 不抛"的分裂。

**追问一：为什么不用 `size()` 比对？**

三个漏洞：

| 漏洞 | 说明 |
|---|---|
| 加法 + 减法会抵消 | `add` 一个再 `remove` 一个，`size` 回到原值，检查发现不了 |
| 只比较元素个数 | 往某个 `SequenceNode` 里加子节点，`rootCount()` 完全不变 |
| 检查点太多 | 每轮都要读一次 `size()` |

`modCount` 是"**单调递增的修改次数**"，只增不减，永远不会抵消。**这就是 `ArrayList` 明明有 `size()` 还用 `modCount` 的原因。**

**追问二：fail-fast 能保证并发安全吗？**

**不能。** `modCount` 是普通 `int`，不是 `volatile`。一个线程的写对另一个线程可能永远不可见 —— 于是不抛异常，静默读到旧数据。

**`ConcurrentModificationException` 是"帮你发现 bug 的哨兵"，不是"并发安全的保证"。** 真要做并发安全，得用 `java.util.concurrent` 的容器或者 `CopyOnWriteArrayList`（§7.4）。

**本项目证据**：§4.9（为什么放容器里）、§6.5.2（九个时刻的字段快照）、§6.5.6（三个漏洞）、§8.3 误解五。

---

### 题六：为什么 `hasNext()` 不检查 `modCount`？

**参考答案**

三个理由：

**（1）契约上不需要。** `Iterator` 只规定了 `next()` 在没元素时必须抛异常。对 `hasNext()` 没有任何"必须准确"的要求 —— 它可以在任何时候返回任何值。

**（2）性能。** 增强 for 的循环结构是 `while (it.hasNext())`，每轮调一次 `hasNext()`。如果要检查 `modCount`，检查成本翻倍，而 `next()` 一次检查就足够拦住所有问题。

**（3）时机。** `hasNext()` 常被用在"只想看一眼"的地方：

```java
if (it.hasNext()) {
    log.debug("还有元素，准备处理");       // ← 只是想打个日志
}
```

如果 `hasNext()` 抛异常，这种只读代码路径会被意外打断。

**这不是本项目自创的规矩，是照着 JDK 抄的：**

```java
// JDK 21, java.util.ArrayList.Itr
public boolean hasNext() {
    return cursor != size;                 // ← 不检查 modCount
}

public E next() {
    checkForComodification();              // ← 只有这里检查
    ...
}
```

**追问一：那"`hasNext()` 说 true、`next()` 抛异常"不矛盾吗？**

看起来矛盾，但完全合法。

`hasNext()` 的回答是"**我猜还有**"，不是"**我保证有**"。`Iterator` 契约里没有"`hasNext()` 必须准确"这一条，只有"`next()` 在没元素时必须抛异常"这一条。

Demo 5 的真实输出就是这个矛盾的现场：

```text
  hasNext() 仍然返回 true —— 它只查栈，不查 modCount
  next() 抛出：ConcurrentModificationException
```

**追问二：有没有模式是反过来的（`hasNext()` 检查，`next()` 不检查）？**

没有标准做法。但 `FilteringIterator` 的 `hasNext()` **有副作用** —— 它会推进 `delegate`：

```java
public boolean hasNext() {
    advance();             // ← 会消耗 delegate
    return hasPending;
}
```

这不是"检查"，是"预读"。为什么必须这样，见题八。

**本项目证据**：§6.5.3（三个理由）、§6.4.5（`hasNext()` 有副作用的三种迭代器对比）。

---

### 题七：DFS 为什么用显式栈，不用递归？

**参考答案**

三个理由，都是"递归做不到"的：

**（1）递归不能暂停。**

递归的进度存在**方法栈**里，你没法把它"存起来、过一会儿再继续"。要暂停只有两个办法：抛异常（丑陋）或者改成显式栈。

迭代器是对象，`stack` 是字段，随时可以停：

```java
Iterator<WorkflowNode> it = flow.iterator();
it.next();      // 走一步
// 干别的去……
it.next();      // 从上次停下的地方继续
```

**（2）递归不能同时开两个游标。**

递归只能有一个"当前执行点"。要同时开两个，得开两个线程（重量级）或者自己维护两个栈（那就是显式栈）。

迭代器 `new` 两个就行：

```java
Iterator<WorkflowNode> first = flow.iterator();
Iterator<WorkflowNode> second = flow.iterator();      // 两个独立栈
```

**（3）递归深度受方法栈限制。**

树的深度一旦超过几千层，`StackOverflowError`。而且 `StackOverflowError` 是 `Error` 不是 `Exception`：

```java
try {
    recursiveDfs(root);
} catch (Exception e) {          // ← 抓不住 StackOverflowError！
    // 这里不会执行
}
```

`catch (Exception)` 抓不住 `Error`。要抓得写 `catch (Throwable)`，而这是极不推荐的做法。

显式栈在堆上，只受内存限制。

**追问一：显式栈比递归慢吗？**

通常**差不多或更快**。递归每层都要压栈帧（保存返回地址、局部变量、操作数栈），显式栈每次只压一个对象引用。

JIT 对递归的优化有限（尾递归优化 Java 不支持），对 `ArrayDeque.push/pop` 的优化很充分。

**追问二：那什么时候该用递归？**

当"遍历"和"处理"必须同时发生，且不需要暂停时（§7.7）：

```java
private static long totalCost(WorkflowNode node) {
    long sum = ownCost(node);
    for (WorkflowNode child : node.children()) {
        sum += totalCost(child);          // ← 遍历和处理合一
    }
    return sum;
}
```

六行，零额外空间，顺序天然是后序（孩子先算完，父节点才汇总）。

**本项目证据**：§4.7（三个理由）、§6.1.2（递归打印为什么保留）、§7.7（该用递归的场景）。

---

### 题八：过滤迭代器为什么必须预读？

**参考答案**

因为 `Iterator` 是**单向**的，没有 `unread()`。

`hasNext()` 要回答的问题是"**下一个满足条件的**还有没有"，而满足条件的那个可能在 5 个之后，也可能在 500 个之后，也可能根本不存在。

不往前读，就不可能知道。读了之后如果不符合条件，也**还不回去**（没有 `unread()`）。

所以唯一的办法是：**读到一个合格的，先存起来（`pending`），下次 `next()` 直接交出去。**

```java
private void advance() {
    while (!hasPending && delegate.hasNext()) {
        WorkflowNode candidate = delegate.next();
        if (predicate.test(candidate)) {
            pending = candidate;
            hasPending = true;
        }
    }
}
```

**追问一：不预读会怎样？**

如果 `hasNext()` 直接转发给 `delegate`：

```java
public boolean hasNext() {
    return delegate.hasNext();          // ← 错
}

public WorkflowNode next() {
    while (delegate.hasNext()) {
        WorkflowNode candidate = delegate.next();
        if (predicate.test(candidate)) {
            return candidate;
        }
    }
    throw new NoSuchElementException();
}
```

**症状**：`hasNext()` 返回 `true`，紧接着 `next()` 抛异常。

推演：剩下 3 个节点全是非审批 → `delegate.hasNext()` 是 `true` → 调用方以为还有 → `next()` 读完 3 个都不合格 → 抛异常。

**这违反了 `Iterator` 契约的第 ① 条**：`hasNext()` 返回 `true` 时，`next()` 必须能返回一个元素。

**追问二：`!hasPending` 为什么必须放在 `while` 条件里，而且是第一个？**

如果漏了：

```java
while (delegate.hasNext()) {            // ← 少了 !hasPending
    ...
}
```

第一次 `hasNext()` 会**一路读到 `delegate` 末尾**，`pending` 被覆盖成"**最后一个**合格的"，前面的全丢了。

**症状**：输出从 2 行变成 1 行，而且丢掉的是前面的。

所以 `!hasPending` 不是"顺便判断一下"，它是"**一旦找到就立刻停手**"的刹车。

**追问三：`hasNext()` 有副作用，会不会丢节点？**

**不会。** 因为 `pending` 是实例字段，`hasNext()` 返回后它还在。下次 `next()` 直接取 `pending`，不会重新读 `delegate`。

**"预读"和"丢弃"的根本区别**：

| | 行为 | 总数 |
|---|---|---|
| 预读 | 从 `delegate` 搬到 `pending` | 不变（只是换了个地方放） |
| 丢弃 | 跳过且不保留 | 变少 |

**本项目证据**：§4.10（为什么必须预读）、§6.4.4（三个错误写法及症状）、§6.4.5（`hasNext()` 有副作用的代价）。

---

### 题九：`List.copyOf` 和 `Collections.unmodifiableList` 有什么区别？

**参考答案**

| | `List.copyOf(list)` | `Collections.unmodifiableList(list)` |
|---|---|---|
| 是不是拷贝 | **是**（新数组/新对象） | **不是**（视图） |
| 原列表后续被改，视图会变吗 | 不会 | **会** |
| 时间复杂度 | O(n) | O(1) |
| 空间 | O(n) | O(1) |
| 元素本身可变时 | 元素还是共享的 | 元素还是共享的 |

**关键区别**：`unmodifiableList` 是"**你不能改**"，`copyOf` 是"**我不管你改不改，我这份是当时的**"。

```java
List<String> original = new ArrayList<>(List.of("a", "b"));
List<String> view = Collections.unmodifiableList(original);
List<String> copy = List.copyOf(original);

original.add("c");

System.out.println(view);      // [a, b, c]   ← 跟着变了
System.out.println(copy);      // [a, b]      ← 没变
```

**`view.add("d")` 会抛 `UnsupportedOperationException`，但 `original.add("c")` 会让 `view` 变大** —— 这就是"不可修改"不等于"不可变"。

**追问一：本项目的 `roots()` 为什么用 `copyOf`？**

因为迭代器需要**快照**（§6.5.4 的第二层）：

```java
List<WorkflowNode> roots = workflow.roots();      // 返回 List.copyOf(roots)
```

如果返回 `unmodifiableList`，那么构造迭代器时拿到的是视图。后续 `flow.add(...)` 会让这个视图变大 —— 但 `stack` 已经压好了，视图变大也不会影响 `stack`。**结果碰巧也是对的，但依赖的是"视图 + 已压栈"的巧妙组合，而不是明确的快照语义。**

用 `copyOf` 是明确表达"我这一份是当时的"，读代码的人不用推演。

**追问二：为什么 `copyOf` 不能完全防止"数据变化"？**

因为**浅拷贝**。如果 `List<WorkflowNode>` 里的元素是可变对象，元素本身被改，`copyOf` 的副本也会看到变化。

本项目的节点全是 `record` + `List.copyOf`（紧凑构造器里），**元素本身也是不可变的**，所以浅拷贝就够。

如果节点是可变的（比如 `class SequenceNode { List<WorkflowNode> steps; }` 且没有 `copyOf`），那就需要深拷贝 —— 代价高得多。

**正确的不可变是靠"每一层都不可变"累积出来的，不是靠一次 `copyOf`。**

**本项目证据**：§5.7（`roots()` 的实现）、§6.5.4（第二层机制）、§7.1.7（`ParallelNode` 的 `List.copyOf`）。

---

### 题十：如果要支持多线程遍历，怎么改？

**参考答案**

先分清两种场景：

| 场景 | 现状 | 要改吗 |
|---|---|---|
| 多个线程各自遍历（只读） | **基本安全** | 不用 |
| 一个线程遍历，另一个 `add` | **不安全** | 要改 |

**场景一为什么基本安全？**

三个原因：

1. `roots()` 返回 `List.copyOf` —— 迭代器操作自己的副本；
2. 节点全是 `record` + `List.copyOf` —— 栈里的每个节点都不可变；
3. 构造之后只读 `modCount`，不再碰 `Workflow` 的其它字段。

**场景二为什么坏？** 三处问题：

| 问题 | 后果 |
|---|---|
| `ArrayList.add` 非线程安全 | 内部数组可能被写坏，或者丢元素 |
| `modCount++` 非原子 | 两个线程各加一次，可能只加了 1，fail-fast 漏检 |
| `modCount` 非 `volatile` | 一个线程的 `add` 对另一个线程的迭代器可能永远不可见 |

**三个方案**：

| 方案 | 改动 | 代价 | 适合 |
|---|---|---|---|
| A | `synchronized` 包住 `add` 和 `roots()` | 读写都串行化 | 写极少、读也少 |
| B | `CopyOnWriteArrayList` + `AtomicInteger` | 写时复制 O(n) | **写少读多**（本项目典型） |
| C | 干脆不可变，不提供 `add` | 要改 API | 配置式流程 |

**方案 B 的改动只有两处，迭代器零改动**：

```java
private final List<WorkflowNode> roots = new CopyOnWriteArrayList<>();
private final AtomicInteger modCount = new AtomicInteger();

public Workflow add(WorkflowNode node) {
    roots.add(node);
    modCount.incrementAndGet();
    return this;
}

int modCount() {
    return modCount.get();          // ← 返回类型还是 int，签名不变
}
```

**为什么迭代器不用改？** 因为 `modCount()` 和 `checkForComodification(int)` 的**方法签名**没变，只是内部从 `int` 变成了 `AtomicInteger.get()`。

**这就是"把状态封装在方法后面"的价值** —— 改了字段类型，只要签名不变，调用方感知不到（§7.4.4）。

**追问一：能不能在迭代器里加 `synchronized`？**

**没用。** 锁的是迭代器对象，不是 `Workflow` 对象。五个线程各自 `new` 一个迭代器 → 五把不同的锁 → 完全不互斥。

**锁必须加在"被共享的那个对象"上，不是加在"使用它的那个对象"上。**

**追问二：即使加了锁，还有一个语义问题？**

对。**遍历途中 `add` 的新节点，该不该被这次遍历看到？**

| 语义 | 实现 |
|---|---|
| 看到（实时反映最新结构） | 迭代器每次 `next()` 重新读 `roots`，且不能有 `modCount` 检查 |
| 看不到（某一时刻的快照） | 现在的 `List.copyOf` + `modCount` 已经做到了 |

**这两种没有对错，只有选择。** 本项目选"快照"，因为审批流的业务语义是"这张单子按提交时的流程走完" —— 中途加签应该新建一个流程版本，而不是偷偷改正在走的这张单。

**本项目证据**：§7.4（完整方案）、§6.5.4（快照的两层机制）。

---

## 9.2 自测清单

40 条，分四组。**能在 30 秒内说出"为什么"才算过。**

### A 组：概念（10 条）

- [ ] A1 我能说出迭代器模式的三个承诺（§1.7）
- [ ] A2 我能说出下标遍历的三个隐含假设（§3.2）
- [ ] A3 我能说出"什么时候不需要迭代器"（§3.3、§8.1.2）
- [ ] A4 我能区分 `Iterable` 和 `Iterator` 的语义（§4.5、§9.1 题二）
- [ ] A5 我能说出 `iterator()` 必须返回新实例的原因（§6.1.5）
- [ ] A6 我能说出 `hasNext()` 为什么不检查 `modCount`（§6.5.3）
- [ ] A7 我能区分 `List.copyOf` 和 `unmodifiableList`（§9.1 题九）
- [ ] A8 我能说出 `next()` 到底抛异常而不是返回 `null` 的四个理由（§6.6.3）
- [ ] A9 我能说出 `NoSuchElementException` 是非受检异常的原因（§6.6.4）
- [ ] A10 我能说出组合模式和迭代器的分工（§8.4.2）

### B 组：代码（10 条）

- [ ] B1 我能不看书写出 `DepthFirstIterator` 的构造器（反序压栈）
- [ ] B2 我能说出 `BreadthFirstIterator` 和 DFS 只差哪几行（§5.9）
- [ ] B3 我能写出 `FilteringIterator.advance()` 的 `while` 条件，并解释 `!hasPending`
- [ ] B4 我能说出 `Frame.nextChild++` 必须在 `push` 之后的原因（§7.2.7）
- [ ] B5 我能写出 `Workflow.iterator(TraversalOrder)` 的 `switch`，并解释为什么不写 `default`
- [ ] B6 我能说出 `record` 紧凑构造器里 `List.copyOf` 的必要性（§7.1.7）
- [ ] B7 我能解释 `sealed interface` 的两个收益（§4.2）
- [ ] B8 我能说出 `IdentityHashMap` 在图的 `visited` 里的必要性（§7.5.3）
- [ ] B9 我能说出 `ArrayDeque` 比 `Stack` 好的原因（`Stack` 继承 `Vector`，全同步）
- [ ] B10 我能解释枚举 `switch` 为什么生成一个 `Workflow$1.class`（synthetic class）

### C 组：设计判断（10 条）

- [ ] C1 给我一个新需求，我能判断该用递归还是迭代器（§7.7、§8.1.3）
- [ ] C2 我能说出 `children()` 放在节点上而不是迭代器里的理由（§4.3）
- [ ] C3 我能说出为什么加 `ParallelNode` 时两个迭代器零改动（§7.1.5）
- [ ] C4 我能说出加 `POST_ORDER` 时为什么编译器会报错（§7.2.3）
- [ ] C5 我能说出"倒序"在树上的三种含义（§7.3.6）
- [ ] C6 我能说出为什么 `FilteringIterator` 用组合不用继承（§6.4.6、§8.3 误解六）
- [ ] C7 我能说出什么时候该上访问者、什么时候用 `instanceof`（§7.8.5）
- [ ] C8 我能说出"枚举 + switch"和"策略对象"各自的适用场景（§8.4.5）
- [ ] C9 我能说出 `modCount` 放在容器而不是迭代器里的理由（§6.5.5）
- [ ] C10 我能说出 `printAll(Iterable)` 和 `printAll(Workflow)` 的实际差别（§6.3.5）

### D 组：陷阱（10 条）

- [ ] D1 我知道写反压栈顺序不会报错，只会变镜像（§6.1.4）
- [ ] D2 我知道 `hasNext()` 说 true、`next()` 抛异常是标准行为（§6.5.3）
- [ ] D3 我知道 fail-fast 是尽力而为，不是并发安全保证（§8.3 误解五）
- [ ] D4 我知道 `advance()` 漏 `!hasPending` 会丢掉前面的匹配项（§6.4.4 错法 B）
- [ ] D5 我知道 `next()` 忘记清 `pending` 会导致死循环（§6.4.4 错法 C）
- [ ] D6 我知道在图上不加 `visited` 会死循环且不报错（§7.5.2）
- [ ] D7 我知道链状树上 DFS 和 BFS 输出完全相同，不能用来测 BFS（§6.2.5）
- [ ] D8 我知道 `record` 当集合元素时用 `HashSet` 会误判"已访问"（§7.5.3）
- [ ] D9 我知道 `Get-Content` 读无 BOM 的 UTF-8 中文会乱码，要验证编码必须读字节（附录 B）
- [ ] D10 我知道 `catch (Exception)` 抓不住 `StackOverflowError`（§9.1 题七）

---

## 9.3 动手题

五道题，难度递增。**每道题都有可执行的验收标准，不要只写不验。**

### 题一：手写 `PostOrderIterator`

**要求**

实现后序遍历（左 → 右 → 根），要求：

1. 空间复杂度 O(树高)，**不能用**"先收集成 `List` 再反转"；
2. `hasNext()` 必须是纯查询（无副作用）；
3. `next()` 到底抛 `NoSuchElementException`；
4. 支持 `modCount` fail-fast。

**验收标准**

用 Demo 1 那棵树（9 个节点），输出必须是：

```text
提交报销单
总监审批
通知财务
总监线
主管审批
通知申请人
主管线
金额是否超过 1000
报销主流程
```

自己推一遍：9 个节点的后序，根（报销主流程）必须在最后。

**提示**：参考 §7.2.4，但**先把代码盖住自己写一遍**。

---

### 题二：手写镜像 DFS

**要求**

实现"先右后左"的深度优先遍历（根 → 右 → 左），要求：

1. 只改 `DepthFirstIterator` 的两处循环方向，**不要新写一个类**（先试试能不能只改一个字段控制）；
2. 输出必须是 §7.3.3 表里的序列 ②。

**进阶要求**

改造成"可以用一个 `boolean reverse` 参数控制方向"，要求：

- `reverse = false` 时输出和原 DFS 完全一致（逐字比对）；
- 加了这个参数后，`Workflow.iterator()` 的签名怎么改？会不会破坏 `Iterable` 契约？

**验收标准**

```text
金额分支
主管线
主管审批
通知申请人
总监线
总监审批
通知财务
```

**思考题**：如果 `iterator()` 带参数，还能不能 `implements Iterable`？`Iterable.iterator()` 是无参的，所以必须保留一个无参版本 —— 这就是 `Workflow` 里为什么有**两个** `iterator()` 方法（§5.7）。

---

### 题三：让 DFS 支持有环图

**要求**

1. 加 `visited` 集合，用 `IdentityHashMap` 建的 `Set`；
2. 构造一个带环的测试图：

```text
A → B → C
    ↑   │
    └───┘
```

3. 验证输出是 `A, B, C`（每个节点只出一次），不是死循环。

**验收标准**

| 检查项 | 期望 |
|---|---|
| 输出节点数 | 3 |
| 输出顺序 | `A, B, C` |
| 运行时间 | < 1 秒（死循环会跑到超时） |
| 内存 | 不持续增长 |

**注意**：如果你不小心用了 `HashSet` 而不是 `IdentityHashMap` 建的 `Set`，而 `A`、`B`、`C` 恰好是同名同类型的节点，测试会**通过** —— 因为 bug 没被触发。**一定要用"两个同名字但不同位置"的节点来测**（§7.5.3 第一个细节）。

---

### 题四：实现"只遍历第 N 层"的迭代器

**要求**

实现 `LevelIterator(Workflow flow, int level)`，只吐出第 `level` 层的节点（根是第 1 层）。

1. 不能改变原有 4 个迭代器；
2. 必须是懒遍历（不能先把整棵树读完再筛）；
3. `level <= 0` 或 `level` 超过树高时，输出为空，**不抛异常**。

**验收标准**

用 Demo 1 那棵树：

| `level` | 输出 |
|---:|---|
| 1 | 报销主流程 |
| 2 | 提交报销单、金额是否超过 1000 |
| 3 | 总监线、主管线 |
| 4 | 总监审批、通知财务、主管审批、通知申请人 |
| 5 | （空） |
| 0 | （空） |

**提示**：BFS 天然按层走。你可以基于 `BreadthFirstIterator` 包装（用 `FilteringIterator` 的思路），也可以自己维护一个"当前层节点数"的计数。

**进阶**：用一种"不数层数"的写法 —— 用两个队列（当前层 / 下一层）交替。

---

### 题五：实现"路径迭代器"

**要求**

实现 `PathIterator`，每次 `next()` 返回一条**从根到叶**的完整路径（`List<WorkflowNode>`）。

**验收标准**

用 Demo 1 那棵树，输出必须是 2 条路径：

```text
[报销主流程, 提交报销单]                          ← 这是叶子吗？不是！
```

**陷阱在这里**：`SequenceNode` 有两个孩子（提交报销单、金额分支），`StepNode` 没有孩子。**"叶子"的定义是 `children().isEmpty()`，不是"是不是 `StepNode`"。**

正确的叶子只有 2 个（总监审批、通知财务、主管审批、通知申请人中的……）。等等 —— 一共有 4 个叶子（4 个 `StepNode` 都在最深层），所以应该是 4 条路径：

```text
[报销主流程, 提交报销单]                    ← 提交报销单的 children() 是空 → 它也是叶子！
[报销主流程, 金额是否超过 1000, 总监线, 总监审批]
[报销主流程, 金额是否超过 1000, 总监线, 通知财务]
[报销主流程, 金额是否超过 1000, 主管线, 主管审批]
[报销主流程, 金额是否超过 1000, 主管线, 通知申请人]
```

**5 条路径**，因为"提交报销单"虽然是流程的第二步，但它在树上确实是叶子。

**这道题的价值**：它强迫你先想清楚"叶子"的定义，而不是想当然地以为"叶子 = 最深的那批"。**如果你的实现只输出了 4 条路径，说明你用了 `depth == maxDepth` 之类的判据，而不是 `children().isEmpty()`。**

**提示**：用显式栈存"当前路径"，DFS 到叶子时拷贝一份路径返回。每次返回路径的拷贝 —— 否则调用方拿到的会是同一条被后续遍历改掉的列表。

---

## 9.4 参考答案要点

只给要点，不给完整代码。完整实现见 §7 对应小节。

### 题一：`PostOrderIterator`

**关键点**

| 点 | 说明 |
|---|---|
| 栈帧 | `Frame { node, nextChild }`，`nextChild` 可变 |
| `next()` 循环 | `peek()` → 还有孩子就 `push` + `nextChild++`；没孩子就 `pop` 并 `return` |
| `hasNext()` | `!stack.isEmpty()`（纯查询） |
| 顺序验证 | 13 轮推演见 §7.2.5 |

**最容易错的地方**：`nextChild++` 的位置。必须在 `push` **之后**，否则跳过每个节点的第一个孩子（§7.2.7）。

---

### 题二：镜像 DFS

**关键点**

两处循环方向翻转：

```java
// 构造器
for (int i = 0; i < roots.size(); i++) {              // 原：i--
    stack.push(roots.get(i));
}

// next()
for (int i = 0; i < children.size(); i++) {            // 原：i--
    stack.push(children.get(i));
}
```

**进阶要求的关键点**：`Iterable.iterator()` 是无参的，所以"带参数"必须**新增重载**，不能替换原方法：

```java
@Override
public Iterator<WorkflowNode> iterator() {             // ← 无参版本，必须保留
    return iterator(TraversalOrder.DEPTH_FIRST);
}

public Iterator<WorkflowNode> iterator(boolean reverse) { ... }    // ← 新增重载
```

**这就是 `Workflow` 里为什么有两个 `iterator()` 方法的深层原因** —— 不是"为了好用"，是"`Iterable` 契约要求必须有无参版本"。

---

### 题三：图的 DFS

**关键点**

| 点 | 说明 |
|---|---|
| `visited` 容器 | `Collections.newSetFromMap(new IdentityHashMap<>())` |
| 加在哪 | `pop()` 之后、`push` 之前 |
| 跳过方式 | `continue`（`while` 循环），**不要**递归 `return next()` |
| 为什么用 `IdentityHashMap` | `record` 的 `equals` 基于字段，同名字节点会被误判为"已访问" |

**验证的关键**：一定要用"两个同名字但不同位置"的节点来测。否则 `HashSet` 的 bug 不会暴露。

---

### 题四：`LevelIterator`

**关键点**

两种实现：

**方案 A：基于 BFS 包装**

```java
new FilteringIterator(flow.iterator(BreadthFirstIterator), node -> depthOf(node) == level, "...")
```

缺点：`depthOf(node)` 需要从节点反推深度，而节点本身不知道自己多深 → 得额外维护一个 `Map<WorkflowNode, Integer>`。**这不是懒遍历**（要预先算所有深度）。

**方案 B：双队列交替（推荐）**

```text
current 队列：当前层的节点
next    队列：下一层的节点

第 i 层：
  把 current 全部 poll 出来（这些就是第 i 层的节点）
  同时把它们的 children 加进 next
  current = next; next = 新队列

   当 i == level 时开始往外吐
```

**优点**：真正的懒遍历，O(宽度) 空间，不需要 `Map`。

**边界**：`level <= 0` 时直接返回空迭代器（`hasNext()` 恒 false），`level` 超过树高时自然走空。

---

### 题五：`PathIterator`

**关键点**

| 点 | 说明 |
|---|---|
| "叶子"的定义 | `children().isEmpty()`，**不是**"StepNode"或"最深层" |
| 路径存储 | 显式栈 + 一个"当前路径"的 `Deque` |
| 返回时 | **必须拷贝** `List.copyOf(currentPath)`，否则下一次 `next()` 会改掉已返回的列表 |
| 预期条数 | Demo 1 那棵树有 **5** 条路径（4 个最深的步骤 + "提交报销单"） |

**这道题最值得记住的一点**：

> **"提交报销单"是流程的第 2 步，但它在树上确实是叶子。**
>
> 这个反直觉的结论说明：**"叶子"是结构概念（没有孩子），不是业务概念（流程的最后一步）。**
>
> 如果你按业务概念实现，会漏掉它；按结构概念实现，才对。这也是为什么 `WorkflowNode.isLeaf()` 的默认实现是 `children().isEmpty()` —— 它定义的是结构，不是语义。

---

# 附录 A：文件清单与行数

## A.1 本项目：11 个源文件

统计口径：

- **物理行** = 文件的实际行数（不含末尾换行符产生的空行）；
- **代码行** = 非空、且不在块注释 / 行注释里的行（用状态机判断块注释，不是只看行的开头）。

| # | 文件 | 物理行 | 代码行 | 一句话职责 |
|---:|---|---:|---:|---|
| 1 | `StepType.java` | 59 | 14 | 节点类型的枚举（START / APPROVAL / NOTIFY / END / CONDITION） |
| 2 | `WorkflowNode.java` | 105 | 9 | 三种节点共用的 `sealed` 接口 |
| 3 | `StepNode.java` | 101 | 21 | 叶子：一个动作 |
| 4 | `BranchNode.java` | 112 | 30 | 分支：二选一 |
| 5 | `SequenceNode.java` | 130 | 24 | 顺序块：依次执行 |
| 6 | `TraversalOrder.java` | 73 | 12 | 遍历顺序的枚举 |
| 7 | `Workflow.java` | 280 | 63 | 容器：只提供结构，不提供顺序 |
| 8 | `DepthFirstIterator.java` | 205 | 37 | 深度优先迭代器（显式栈 + 反序压栈） |
| 9 | `BreadthFirstIterator.java` | 139 | 34 | 广度优先迭代器（队列 + `pollFirst`） |
| 10 | `FilteringIterator.java` | 202 | 44 | 过滤迭代器（装饰器 + 预读） |
| 11 | `Client.java` | 401 | 202 | 六段 Demo + 树形打印 + 输出工具 |
| — | **合计** | **1807** | **490** | |

**代码行只占 27%**（490 / 1807）。剩下的 73% 是 javadoc 和注释 —— 这是刻意的：这个项目的目的是"教人读懂为什么这样设计"，不是"用最少的行数实现功能"。

如果只算可执行语句，还能再少一半（大量 javadoc 里的 `<pre>` 示例、`@param` / `@return` 说明都不算代码）。

## A.2 编译产物：12 个 class 文件

```text
BranchNode.class
BreadthFirstIterator.class
Client.class
DepthFirstIterator.class
FilteringIterator.class
SequenceNode.class
StepNode.class
StepType.class
TraversalOrder.class
Workflow.class
Workflow$1.class              ← javac 为 switch-on-enum 生成的 synthetic class
WorkflowNode.class
```

**11 个源文件 → 12 个 class。** 多出来的 `Workflow$1.class` 不是笔误，是 `javac` 为 `Workflow.iterator(TraversalOrder)` 里的 `switch (order)` 生成的合成类。

它做的是"枚举序号 → 分支下标"的映射（用 `switch` 表代替逐项比较）。这是编译器的实现细节，不影响源码语义，但会影响"为什么 class 文件比源文件多一个"这个问题。

**验证方法**：

```powershell
Get-ChildItem out\com\patterns\behavioral\iterator -Filter *.class | Measure-Object | Select-Object Count
# Count: 12
```

## A.3 原项目：4 个源文件

| 文件 | 物理行 | 代码行 | 职责 |
|---|---:|---:|---|
| `Client.java` | 26 | 15 | 手工 `while` 循环 + 强制类型转换 |
| `Iterator.java` | 15 | 5 | 自定义接口（`hasNext` / `next` 返回 `Object`） |
| `ProcessNode.java` | 16 | 7 | `record`，一个名字 |
| `Workflow.java` | 52 | 34 | 数组容器 + **非静态内部类**迭代器 |
| **合计** | **109** | **61** | |

## A.4 两边的对照

| 指标 | 原项目 | 本项目 | 倍数 |
|---|---:|---:|---:|
| 源文件 | 4 | 11 | 2.75× |
| 物理行 | 109 | 1807 | 16.6× |
| 代码行 | 61 | 490 | 8.0× |
| 容器结构 | 一维数组（定长） | 树（4 种节点：3 种已实现 + 1 种扩展演示） | — |
| 遍历方式 | 1 种 | 2 种（+ 后序、镜像、过滤可以叠加） | — |
| 能否增强 for | 不能 | 能 | — |
| 类型安全 | `next()` 返回 `Object` | `Iterator<WorkflowNode>` | — |
| fail-fast | 没有 | 有 | — |
| 到底行为 | 返回 `null` | 抛 `NoSuchElementException` | — |
| 编译告警 | 未检查（无 `-Xlint`） | 0（`-Xlint:all`） | — |
| 真实输出 | 4 行 | 120 行 | 30× |

**16.6 倍的物理行增长，是这一份文档存在的理由。** 如果只是"把 4 个文件改成 11 个文件"，那不值得；值得的是每一行都能回答"删了会怎样"（§8.2.1 那张表）。

---

# 附录 B：编译与运行命令

## B.1 环境

| 项 | 值 |
|---|---|
| JDK | `F:\enCodingApps\JDK\jdk-25` |
| 源文件目录 | `E:\codeFile\codeFather\design_patterns_pro\src\com\patterns\behavioral\iterator` |
| 输出目录 | `E:\codeFile\codeFather\design_patterns_pro\out` |
| 主类 | `com.patterns.behavioral.iterator.Client` |
| 源文件编码 | UTF-8（无 BOM） |

## B.2 编译

```powershell
$jdk = 'F:\enCodingApps\JDK\jdk-25'
$src = 'E:\codeFile\codeFather\design_patterns_pro\src\com\patterns\behavioral\iterator'
$out = 'E:\codeFile\codeFather\design_patterns_pro\out'

New-Item -ItemType Directory -Force -Path $out | Out-Null
$files = Get-ChildItem -Path $src -Filter *.java | Sort-Object Name | Select-Object -ExpandProperty FullName

& "$jdk\bin\javac.exe" -encoding UTF-8 -Xlint:all -d $out $files
```

三个参数都不能省：

| 参数 | 作用 | 省了会怎样 |
|---|---|---|
| `-encoding UTF-8` | 告诉 javac 源文件是 UTF-8 | Windows 默认 GBK，中文注释里出现 GBK 没有的字符就报错或乱码 |
| `-Xlint:all` | 打开全部编译警告 | 未检查的转换、过时 API 全部静默通过 |
| `-d $out` | 指定输出目录 | class 文件散落在源码旁边 |

**真实编译输出**：

```text
source files = 11
COMPILE: OK, zero warnings
class files = 12
```

**"zero warnings" 是交付标准，不是"看起来没报错"。** 具体做法是把 javac 的 stdout + stderr 都重定向到日志文件，然后判断日志是否为空：

```powershell
& "$jdk\bin\javac.exe" -encoding UTF-8 -Xlint:all -d $out $files 2>&1 |
    Out-File -FilePath $log -Encoding utf8

if ([string]::IsNullOrWhiteSpace((Get-Content $log -Raw))) {
    Write-Host "COMPILE: OK, zero warnings"
}
```

## B.3 运行

```powershell
& "$jdk\bin\java.exe" "-Dstdout.encoding=UTF-8" "-Dfile.encoding=UTF-8" `
    -cp $out com.patterns.behavioral.iterator.Client
```

两个 `-D` 参数的作用：

| 参数 | 作用 |
|---|---|
| `-Dstdout.encoding=UTF-8` | 让 `System.out` 用 UTF-8 输出（JDK 19+ 默认是 UTF-8，但显式指定更保险） |
| `-Dfile.encoding=UTF-8` | 让文件相关的默认编码是 UTF-8 |

**注意引号**：`"-Dstdout.encoding=UTF-8"` 必须用引号包起来。在 PowerShell 里不加引号会被拆坏，报 `错误: 无效的标记: .encoding=UTF-8`。

## B.4 验证输出可复现

**跑一次看起来对，不代表每次都对。** 验证方法是三次独立运行 + 哈希比对：

```powershell
$main = 'com.patterns.behavioral.iterator.Client'
$hashes = @()
for ($round = 1; $round -le 3; $round++) {
    $txt = Join-Path $base ("run" + $round + ".txt")
    & "$jdk\bin\java.exe" "-Dstdout.encoding=UTF-8" "-Dfile.encoding=UTF-8" -cp $out $main 2>&1 |
        Out-File -FilePath $txt -Encoding utf8
    $n = (Get-Content -LiteralPath $txt).Count
    $h = (Get-FileHash -LiteralPath $txt -Algorithm SHA256).Hash
    $hashes += $h
    Write-Host ("run" + $round + ": lines=" + $n + "  sha256=" + $h)
}
if (($hashes | Select-Object -Unique).Count -eq 1) {
    Write-Host "REPRODUCIBLE: three runs identical"
}
```

**真实结果**：

```text
COMPILE: OK, zero warnings
run1: lines=120  sha256=06D82FE51A106D5E692FAF5CD5A21E88D8431EC3888466202C8679E643E74669
run2: lines=120  sha256=06D82FE51A106D5E692FAF5CD5A21E88D8431EC3888466202C8679E643E74669
run3: lines=120  sha256=06D82FE51A106D5E692FAF5CD5A21E88D8431EC3888466202C8679E643E74669

REPRODUCIBLE: three runs identical
```

**为什么三次而不是一次？**

因为"输出里有 `HashMap` 的遍历"这类问题只会间歇性出现。三次一致不能证明永远一致，但能排除绝大多数顺序依赖问题（比如忘了用 `LinkedHashMap`、依赖了 `hashCode` 的值）。

本项目没有用 `HashMap` 或 `HashSet`，所以三次必然一致 —— 但**"必然"要靠验证证明，不能靠推理声明**。

## B.5 Windows + PowerShell 的四个坑

这四条都是本项目实际踩过的，记录下来省得下次再踩。

### 坑一：`-Dxxx=yyy` / `-J-Dxxx=yyy` 不加引号会被拆坏

**症状**：

```text
错误: 无效的标记: .encoding=UTF-8
```

（在 GBK 终端里会显示成 `����: ��Ч�ı��`，更容易误判成编码问题。）

**原因**：PowerShell 把 `-Dstdout.encoding=UTF-8` 里的 `.` 当成了参数分隔符。

**解决**：加引号。

```powershell
"-J-Dstdout.encoding=UTF-8" "-J-Dstderr.encoding=UTF-8"      # javac 用 -J 前缀
"-Dstdout.encoding=UTF-8"                                     # java 直接 -D
```

### 坑二：内联 `pwsh -Command` 带 `$_` 或长字符串会被吞

**症状**：

```text
ParserError: 一元运算符 '-' 后缺少表达式
```

或者：

```text
文件名或扩展名太长
```

**原因**：外层 shell 先做了一遍变量替换和转义，`$_`、反引号、引号嵌套全被打乱。

**解决**：**一律写成 `.ps1` 脚本文件，用 `pwsh -NoProfile -File xxx.ps1` 执行。** 不要内联。

这条教训在本项目里出现了三次，每次都是同样的症状。

### 坑三：`Get-Content` 读无 BOM 的 UTF-8 中文会乱码

**症状**：

```text
# 杩唬鍣ㄦā寮忥紙Iterator Pattern锛?
```

（应该是 `# 迭代器模式（Iterator Pattern）`）

**原因**：Windows 中文版 PowerShell 5.x 的 `Get-Content` 默认用系统 ANSI 代码页（GBK）解码。文件是 UTF-8 无 BOM，就被当成 GBK 解了。

**注意：这是显示问题，不是文件损坏。** 判断方法见坑四。

**解决**：

```powershell
# 读：显式指定编码
Get-Content -LiteralPath $p -Encoding utf8

# 或者更可靠：读字节再解码
$utf8 = New-Object System.Text.UTF8Encoding($false)
$text = $utf8.GetString([System.IO.File]::ReadAllBytes($p))
```

**PowerShell 7+ 的 `Get-Content` 默认是 UTF-8**，所以这个坑只在 5.x 上出现。但脚本要在两种版本上都能跑，所以显式指定编码最保险。

### 坑四：怎么确认"文件真的是 UTF-8 无 BOM"

**不要用 `Get-Content` 判断。** 它自己就可能解码错。要读字节：

```powershell
$b = [System.IO.File]::ReadAllBytes($p)
Write-Host ('bytes    = ' + $b.Length)
Write-Host ('first 6  = ' + ($b[0..5] -join ','))
Write-Host ('has BOM  = ' + ($b[0] -eq 239 -and $b[1] -eq 187 -and $b[2] -eq 191))

$utf8 = New-Object System.Text.UTF8Encoding($false)
$text = $utf8.GetString($b)
$lines = $text -split "`r?`n"
Write-Host ('lines    = ' + $lines.Count)
```

**真实结果**（README 文件）：

```text
first 6  = 35,32,232,191,173,228
has BOM  = False
```

`bytes` 那一行故意没有写：它每改一次文档就变一次，写进文档里永远对不上。真正要看的是这两行：

| 检查项 | 值 | 说明 |
|---|---|---|
| `first 6` | `35, 32, 232, 191, 173, 228` | `35` = `#`，`32` = 空格，`232,191,173` = 「迭」的 UTF-8 三字节，`228` = 「代」的第一字节 |
| `has BOM` | `False` | 没有 `EF BB BF` |
| `lines` | 与编辑器一致 | 说明解码正确（解码错的话行数会变，因为某些字节序列会被当成换行） |

**结论**：`#` + 空格 + 合法的 UTF-8 中文三字节序列 = 文件是对的，之前的乱码纯粹是显示问题。

## B.6 附录 B 的可迁移结论

| 结论 | 证据 | 用错时的症状 |
|---|---|---|
| 编译要加 `-encoding UTF-8` + `-Xlint:all` | §B.2 三个参数 | 省略 → 中文报错 / 警告静默通过 |
| `-D` 参数必须加引号（PowerShell） | §B.5 坑一 | 报"无效的标记" |
| 复杂 PowerShell 一律写 `.ps1` 文件 | §B.5 坑二 | 报 `ParserError` 或"文件名或扩展名太长" |
| 验证编码要读字节，不能用 `Get-Content` | §B.5 坑四 | 误判文件损坏，白白重写一遍 |

---

# 附录 C：原项目 vs 本项目逐项对照

## C.1 八个问题 × 八处修正

| # | 原项目的问题 | 原项目的代码 | 本项目的解法 | 证据 |
|---:|---|---|---|---|
| 1 | `Workflow` 没实现 `Iterable` | `public Iterator iterator()`（自定义接口） | `implements Iterable<WorkflowNode>` + `@Override iterator()` | §2.3、§4.5 |
| 2 | `next()` 返回 `Object` | `Object next();` | `WorkflowNode next();`（泛型） | §2.4、§5.8 |
| 3 | `next()` 到底返回 `null` | `return null;` | 抛 `NoSuchElementException` + 带上下文的消息 | §2.5、§6.6 |
| 4 | 容量满了静默丢弃 | `if (size < nodes.length) { nodes[size++] = node; }` | 没有容量参数，`List` 自动扩容 | §2.6 |
| 5 | 没有快照，也没有 fail-fast | 内部类直接读外部类的实时 `size` | `List.copyOf` 快照 + `modCount` / `expectedModCount` | §2.7、§6.5 |
| 6 | 非静态内部类持有外部引用 | `private class WorkflowIterator` | 顶层 `final class`，只需传 `Workflow` 引用（不持有） | §2.8 |
| 7 | 类型只能靠名字猜 | `name().contains("审批")` | `StepType` 枚举 + `instanceof` 模式匹配 | §2.9、§5.1 |
| 8 | 只有一种遍历顺序 | 遍历写死在数组下标里 | `TraversalOrder` + 两个（可扩展到四个）迭代器 | §2.10、§5.6 |

## C.2 行为逐项对比

| 行为 | 原项目 | 本项目 |
|---|---|---|
| `for (X x : container)` | **编译失败**（`要求: 数组或 java.lang.Iterable`） | 能用 |
| 遍历顺序种类 | 1（数组正序） | 2 内置 + 后序 / 镜像 / 过滤可叠加 |
| 两个游标同时走 | 可以（每次都 `new` 内部类实例），但没人保证 | 保证（`iterator()` 每次 `new`） |
| 类型安全 | 运行时 `ClassCastException` | 编译期检查 |
| 类型信息的来源 | 节点名字的字符串 | `StepType` 枚举 |
| 到底了再取 | 返回 `null` → 后续 NPE | 抛 `NoSuchElementException` |
| 遍历中改结构 | 行为未定义（新节点可能突然出现） | `next()` 抛 `ConcurrentModificationException` |
| 容量限制 | 有（`capacity`），满了丢 | 无 |
| 迭代器持有外部对象 | 是（`this$0`） | 否（只持有 `workflow` 引用 + 自己的栈） |
| 能否传给 `printAll(Iterable)` | 不能 | 能 |
| 能否用 Stream | 不能（没有 `spliterator`） | 能（`Iterable.spliterator()` 是 default） |
| 编译告警 | 未检查 | 0（`-Xlint:all`） |

## C.3 保留了原项目的什么

不是"全都推翻了"。以下原项目的做法被完整保留：

| 保留的东西 | 为什么保留 |
|---|---|
| `Iterator` 的两个方法 `hasNext()` / `next()` | GoF 定义的最小接口，`java.util.Iterator` 也是这样 |
| 容器负责创建迭代器（`workflow.iterator()`） | 这是 `Iterable` 协议的入口，也是工厂方法的体现（§8.4.3） |
| 迭代器作为**独立对象** | 这是"可暂停、可多游标"的根本前提 |
| 一个 `Client` 一段 Demo 的组织方式 | 任务书要求"保留独立 Demo 形式" |
| 用 `record` 表示节点 | 原项目的 `ProcessNode` 就是 `record`，本项目延续了这个选择并扩展到三种节点 |
| `toString()` 打印节点信息 | 原项目有，本项目在每个节点类里都实现了，输出更可读 |

## C.4 改了什么、为什么改

| 改动 | 原因 |
|---|---|
| 数组 → 树 | 数组表达不了"金额 > 1000 走总监"这种分支（§1.4、§1.5） |
| `Object` → `WorkflowNode` | 消除运行时强转（§2.4 探针 1） |
| `null` → 异常 | 让错误在现场爆发（§2.5 探针 2） |
| 静默丢弃 → 无容量限制 | 静默失败是最难查的 bug（§2.6 探针 3） |
| 实时读 `size` → `modCount` 快照 | 遍历范围必须在创建时固定（§2.7 探针 4） |
| 非静态内部类 → 顶层类 | 避免 `this$0` 让整个容器回收不掉（§2.8 探针 6） |
| 字符串匹配 → 枚举 + 类型 | 改文案不该导致逻辑静默失效（§2.9 探针 5） |
| 一种顺序 → 枚举 + 多迭代器 | 容器不该决定"怎么看"（§2.10、§6.2.6） |

---

# 附录 D：探针证据全表

"探针"（probe）是指为了**验证某个行为**而专门写的最小程序。它们不属于交付代码，但每个结论都靠它们取证。

## D.1 八个探针的清单

| # | 探针文件 | 验证什么 | 输出行数 | 对应章节 |
|---:|---|---|---:|---|
| 1-6 | `Probe.java`（138 行） | 原项目的六个问题 | 36 | §2.4 - §2.9 |
| 7 | `ForEachFail.java`（19 行） | 不实现 `Iterable` 时 for-each 编译失败 | 编译错误 5 行 | §2.3 |
| 8 | `EnumSwitchFail.java` + `EnumSwitchStatementOk.java` | switch 表达式的穷尽性检查 | 编译错误 4 行 + 1 行 | §7.2.3 |

## D.2 探针 1：`next()` 返回 `Object`，转错类型要到运行时才爆

```text
=== 探针 1：next() 声明返回 Object，转错类型要到运行时才爆 ===
  next() 的声明类型是 Object，实际装的是 BehaviorType.IteratorPattern.ProcessNode
  强转成 Integer -> java.lang.ClassCastException
  编译器一条警告都没给：它只知道返回 Object，不知道里面装的是什么
```

**结论**：`Object next()` 让编译器无法检查类型。错在运行期，离源码很远。

**本项目的修正**：`WorkflowNode next()`（泛型参数 `Iterator<WorkflowNode>`）→ 编译器能检查，`instanceof` 也能模式匹配。

## D.3 探针 2：`next()` 到底返回 `null`，异常被推迟到使用处

```text
=== 探针 2：next() 到底返回 null，异常被推迟到使用处 ===
  第 1 次 next()：ProcessNode{name='结束'}
  hasNext() = false
  第 2 次 next()：null
  对返回值取 name() -> NullPointerException
  栈顶是「(ProcessNode) extra」那一行，而不是「多调了一次 next()」那一行
```

**结论**：错误的**现场**（用 null 那一行）和错误的**源头**（多取一次那一行）分离了。

**本项目的修正**：`throw new NoSuchElementException("深度优先遍历已经到底，没有下一个节点")` —— 异常就在多取的那一行抛出，消息还带上下文。

## D.4 探针 3：容量满了静默丢弃

```text
=== 探针 3：容量满了静默丢弃 ===
  capacity = 2，addNode 一共调了 4 次
  实际遍历到 2 个节点
  第 3、4 个既没进去，也没有任何提示：不抛异常、不返回 false、不打日志
```

**结论**：`if (size < nodes.length)` 这个判断把"加不进去"变成了**无提示的失败**。调用方 `addNode` 四次，以为成功了四次。

**本项目的修正**：没有容量参数。`List` 自动扩容，`add` 永远不会静默失败。

## D.5 探针 4：遍历中加节点，既没快照也没 fail-fast

```text
=== 探针 4：遍历中加节点，既没快照也没 fail-fast ===
  先取一个：ProcessNode{name='A'}
  addNode(C) 之后继续遍历：
    ProcessNode{name='B'}
    ProcessNode{name='C'}
  没有 ConcurrentModificationException
  C 居然也出现了：内部类读的是外部类实时的 size 字段，不是快照
  反过来，如果数组刚好在遍历途中被填满，新增节点就是静默看不到
```

**结论**：这个探针的价值在于**它推翻了预期**。原本以为"新增节点不会被看到"，实际是"会被看到" —— 因为 `WorkflowIterator` 是非静态内部类，`hasNext()` 读的是外部类**实时**的 `size`。

所以原项目的行为是：

| 情况 | 行为 |
|---|---|
| 数组还没满，遍历中 add | 新节点**会**出现（读实时 `size`） |
| 数组刚好满了，遍历中 add | 新节点**不会**出现（静默丢弃） |

**同一种操作，两种不同的结果，取决于数组满没满。** 这是最难查的一类 bug。

**本项目的修正**：两层机制（§6.5.4）—— `List.copyOf` 快照保证"不会出现"，`modCount` 保证"改了就报错"。

## D.6 探针 5：类型只能靠名字猜，改文案就静默失效

```text
=== 探针 5：类型只能靠名字猜，改文案就静默失效 ===
  按 name().contains("审批") 过滤 -> [总监审批, 主管审批]
  「总监确认」明明也是审批节点，却被漏掉了
  改文案的人和写过滤的人，不是同一个人
```

**结论**：把"类型"编码进"显示文本"，就把两个正交的概念绑死了。改文案（显示）会破坏筛选（逻辑）。

**本项目的修正**：`StepType` 枚举独立于 `name`。改名字不影响 `type`：

```java
new StepNode("总监确认", StepType.APPROVAL)      // ← 名字变了，类型不变
```

## D.7 探针 6：非静态内部类持有外部引用

```text
=== 探针 6：迭代器是非静态内部类，隐式持有外部 Workflow 引用 ===
  迭代器实际类型：BehaviorType.IteratorPattern.Workflow$WorkflowIterator
    字段 index : int
    字段 this$0 : BehaviorType.IteratorPattern.Workflow
  this$0 是编译器自动加的：迭代器只要还活着，整个 Workflow 和它的数组都回收不掉
```

**结论**：`this$0` 是编译器自动加的字段。只要迭代器还被引用，整个 `Workflow` 就回收不掉。

**本项目的修正**：四个迭代器都是顶层 `final class`。`DepthFirstIterator` 确实持有 `Workflow` 引用，但那是**显式字段** `private final Workflow workflow`，用于 `checkForComodification`，不是编译器偷加的。显式字段能被看到、能被审查、能被优化。

**注意**：本项目**没有**消除"迭代器持有容器引用"这件事（`modCount` 检查需要它）。消除的是"隐式持有" —— 现在这个依赖是写在字段声明里的，一眼能看到。

## D.8 探针 7：不实现 `Iterable` 时 for-each 编译失败

**真实 javac 输出**：

```text
E:\codeFile\codeFather\design_patterns_pro\tmp_it_probe\BehaviorType\IteratorPattern\ForEachFail.java:15: 错误: for-each 不适用于表达式类型
        for (ProcessNode node : wf) {
                                ^
  要求: 数组或 java.lang.Iterable
  找到:    Workflow
1 个错误
```

**结论**：`for-each` 是**编译期**语法糖，编译器只认"数组"或"`Iterable`"两种类型。原项目的 `Workflow` 两者都不是，所以这四行代码根本编译不过。

**这不是"运行时不好用"，是"编译不过"。** 差别很大：编译不过会立刻发现，运行时不好用可能上线半年才发现。

## D.9 探针 8：switch 表达式的穷尽性检查

**真实编译 + 运行输出**：

```text
=== 1) switch 表达式不穷尽（期望编译失败） ===
E:\codeFile\codeFather\design_patterns_pro\tmp_enum_switch\EnumSwitchFail.java:12: 错误: switch 表达式不包含所有可能的输入值
        return switch (o) {
               ^
1 个错误
exit = 1

=== 2) switch 语句不穷尽（期望编译通过） ===
exit = 0

=== 3) 运行对照组 ===
switch 语句不覆盖全部常量，编译通过，运行时什么都没打印
exit = 0
```

**结论**：这是 §7.2.3 那条"不写 `default` 让编译器当守卫"的硬证据。

| 写法 | 漏一个常量 |
|---|---|
| `switch` 表达式（无 `default`） | **编译失败** |
| `switch` 语句（无 `default`） | 编译通过，运行时**静默什么都不做** |
| `switch` 表达式（有 `default`） | 编译通过，静默走默认分支 |

## D.10 探针的可信度说明

| 探针 | 是否可独立复现 | 怎么复现 |
|---|:---:|---|
| 1-6 | 是 | 编译原项目 4 个文件，运行 `Probe.main` |
| 7 | 是 | 单独编译 `ForEachFail.java`，看报错 |
| 8 | 是 | 分别编译 `EnumSwitchFail.java` 和 `EnumSwitchStatementOk.java` |

**所有探针输出的都是真实终端输出，不是手写的。** 如果某个数字对不上，以脚本重新跑的结果为准。

**关于探针源码的去向**：它们是临时文件（编译到临时目录、跑完即删），不属于交付内容。上面引用的路径（`tmp_it_probe/...`、`tmp_enum_switch/...`）只是当时真实报错信息里的一部分，照抄保留是为了不篡改原始输出；你按上表的方法自己重建一份，会得到除路径外完全一致的报错。

---

# 附录 E：关键术语

按字母顺序，只收本模式相关的。

| 术语 | 含义 | 本项目里的落点 |
|---|---|---|
| **BFS**（Breadth-First Search） | 广度优先：先访问同一层的所有节点，再下一层 | `BreadthFirstIterator` |
| **DFS**（Depth-First Search） | 深度优先：一条路走到底，再回溯 | `DepthFirstIterator` |
| **fail-fast** | 检测到并发修改就立刻抛异常，而不是继续用可能错的数据 | `Workflow.checkForComodification` |
| **Iterable** | "我能被遍历"，只有一个方法 `iterator()` | `Workflow implements Iterable<WorkflowNode>` |
| **Iterator** | "我正在遍历"，`hasNext()` + `next()` | 四个迭代器都实现它 |
| **modCount** | 容器结构被修改的次数，**只增不减** | `Workflow.modCount` |
| **expectedModCount** | 迭代器创建时 `modCount` 的**快照**（`final int`） | 四个迭代器的同名字段 |
| **spliterator** | 可分割迭代器，Stream 的底层 | `Iterable` 的 default 方法 |
| **sealed interface** | 封闭接口，`permits` 列出所有允许的实现 | `WorkflowNode` |
| **record** | 不可变数据类，字段 `final`，自动生成 `equals` / `hashCode` / `toString` | `StepNode` / `BranchNode` / `SequenceNode` |
| **紧凑构造器**（compact constructor） | `record` 的校验 + 规范化入口，不写参数列表 | 三个节点 record 都用 |
| **防御性拷贝** | 构造时拷贝传入的可变对象，避免外部后续改动影响内部 | `List.copyOf` |
| **浅拷贝 / 深拷贝** | 浅拷贝只复制引用，深拷贝复制到底 | `List.copyOf` 是浅拷贝 |
| **前序**（preorder） | 根 → 左 → 右 | `DepthFirstIterator` |
| **中序**（inorder） | 左 → 根 → 右 | 本项目**没有**（二叉树才有意义；多叉树没有"中间"） |
| **后序**（postorder） | 左 → 右 → 根 | §7.2 的 `PostOrderIterator` |
| **镜像前序** | 根 → 右 → 左 | §7.3 的 `ReverseDepthFirstIterator` |
| **栈**（stack） | 后进先出；`push` / `pop` 都在同一端（头部） | `DepthFirstIterator` 的 `ArrayDeque` |
| **队列**（queue） | 先进先出；`addLast` 进、`pollFirst` 出 | `BreadthFirstIterator` 的 `ArrayDeque` |
| **装饰器**（Decorator） | 实现同一接口 + 持有同一接口的引用，转发并增强 | `FilteringIterator` |
| **预读**（look-ahead） | 提前算好"下一个合格的"，存在字段里 | `FilteringIterator.pending` |
| **pull / push** | 调用方主动拉 / 主题主动推 | 迭代器是 pull；观察者是 push（§8.4.6） |
| **表达式问题**（Expression Problem） | "加类型"和"加操作"不可能都只加不改 | §7.8.3 |
| **synthetic class** | 编译器生成的、源码里不存在的类 | `Workflow$1.class` |
| **`this$0`** | 非静态内部类隐式持有的外部类引用字段 | 原项目的 `Workflow$WorkflowIterator` 有；本项目**没有** |
| **`IdentityHashMap`** | 用 `==` 比较键的 `Map`，不是 `equals` | §7.5.3 的 `visited` 集合 |
| **`CopyOnWriteArrayList`** | 写时复制整个数组，读零锁 | §7.4.4 的并发方案 B |

---

# 附录 F：参考资料

## F.1 模式原典

| 资料 | 相关内容 |
|---|---|
| 《设计模式：可复用面向对象软件的基础》（GoF，1994） | 迭代器模式（Iterator）一章。定义、结构图、四角色（Iterator / ConcreteIterator / Aggregate / ConcreteAggregate）都在这一章 |
| 《Head First 设计模式》 | 迭代器模式一章，用餐厅菜单举例。适合入门，但示例是"数组 + 列表"两种容器，不涉及树 |

## F.2 Java 官方文档

| 资料 | 为什么值得读 |
|---|---|
| `java.lang.Iterable` 的 javadoc | 看 `iterator()` 的契约描述有多简略（一句话），对比整个 JDK 有多依赖它 |
| `java.util.Iterator` 的 javadoc | 看 `remove()` 和 `forEachRemaining()` 的 default 实现 |
| `java.util.ConcurrentModificationException` 的 javadoc | 看"fail-fast 是尽力而为，不是保证"的原话 |
| `java.util.Spliterator` 的 javadoc | 看 `SIZED` / `SUBSIZED` / `ORDERED` 这些特性位各是什么意思 |
| `java.util.ArrayList` 源码（`Itr` 内部类） | 看 JDK 自己的 fail-fast 怎么写（`checkForComodification` / `hasNext` 不检查） |
| `java.util.AbstractList` 源码（`Itr`） | 看"模板方法 + 迭代器"怎么配合（§8.4.4） |
| `java.lang.Iterable#spliterator()` 的 default 实现 | 看 `spliteratorUnknownSize` 为什么让并行流没收益（§7.6.4） |

## F.3 语言规范

| 主题 | 在哪儿查 |
|---|---|
| `switch` 表达式的穷尽性要求 | JLS 第 14 章（Blocks, Statements, and Patterns）里关于 `switch` 表达式的部分 |
| `sealed` 类 / 接口 | JLS 第 8 章（Classes）、第 9 章（Interfaces）里 sealed 相关小节 |
| `record` 的紧凑构造器 | JLS 第 8.10 节（Record Classes） |
| 增强 for 的等价展开 | JLS 第 14.14.2 节（The enhanced for statement）明确写了展开形式 |

**注意**：JLS 的章节号会随版本变化。查的时候以官方最新版为准，不要记死具体编号。

## F.4 延伸阅读方向

按"读完这份文档之后该看什么"排序：

| 方向 | 为什么 |
|---|---|
| `java.util.stream` 包的 javadoc | 理解 Stream 和 Iterator 的层次关系（§7.6、§8.3 误解八） |
| `java.util.concurrent` 包的 javadoc | 理解 `CopyOnWriteArrayList` 的"写时复制"和弱一致迭代器（§7.4） |
| 组合模式（Composite） | 本项目用了组合模式打底（`children()`），值得单独读（§8.4.2） |
| 访问者模式（Visitor） | 和迭代器正交但常配合使用（§7.8、§8.4.1） |
| 图算法（DFS / BFS / 拓扑排序） | §7.5 只是开了个头，真正的图遍历还有 `visited`、`dist`、连通分量 |
| JVM 内存模型（JMM） | 理解为什么 `modCount` 不加 `volatile` 就不是并发安全的（§8.3 误解五） |

## F.5 一句话收尾

这份文档的每一节都在回答同一个问题：

> **"如果换一种写法，会有什么区别？"**

如果你读完只记住一句话，希望是这句：

> **设计模式不是"给代码加一层抽象"，而是"把会变的东西关进一个边界里"。**
>
> 本项目的边界是：
>
> - **结构**关进节点（`children()`）—— 加第四种节点，迭代器不动；
> - **顺序**关进迭代器（`stack` / `queue`）—— 加第三种顺序，节点不动；
> - **筛选**关进装饰器（`FilteringIterator`）—— 换条件，容器不动；
> - **一致性**关进容器（`modCount`）—— 检查逻辑只有一份。

四条边界，四个"加东西时谁不用动"的答案。这就是本项目的全部设计。
