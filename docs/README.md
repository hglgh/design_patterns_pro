# docs 导航

> 项目根目录的 [README](../README.md) 是总入口。这一份只做一件事：**告诉你每条岔路通向哪里**，不重复内容。

---

## 按目的人找文档

| 你的处境 | 去哪 |
|---|---|
| 完全没学过设计模式 | [01-learning-path.md](01-learning-path.md) 的「从零开始」段 → 任选一个 `BEGINNER.md` |
| 能看懂模式，但不知道什么时候用哪个 | **[pattern-selection-guide.md](pattern-selection-guide.md)** ← 本项目的核心文档 |
| 想把两个相似模式分清楚 | [02-pattern-comparison.md](02-pattern-comparison.md)（定义差异）→ [选择指南 §4~§6](pattern-selection-guide.md)（同一需求下的取舍） |
| 拿到需求不知道怎么组织结构 | [practice/](practice/README.md) 先挑一题，做完第 4 节再看答案 |
| 想看清对象之间谁持有谁、谁调用谁 | [10-uml-atlas.md](10-uml-atlas.md)（25 类图 + 2 时序图 + 图-代码对照表） |
| 想知道「为什么不能用继承」 | [07-composition-vs-inheritance.md](07-composition-vs-inheritance.md) §1~§7 |
| 想为面试做准备 | [02-pattern-comparison.md](02-pattern-comparison.md) 的对比表 + 各模式 `README.md` 的「面试与自测」节 |
| 想知道这些模式在真实框架里长什么样 | [06-modern-scenarios.md](06-modern-scenarios.md) |
| 想了解项目本身怎么改的、有没有隐藏问题 | [00-source-review.md](00-source-review.md) · [03-optimization-log.md](03-optimization-log.md) · [05-verification.md](05-verification.md) |
| 想给某个模式写入门文档 | [08-beginner-teaching-template.md](08-beginner-teaching-template.md)（八段式模板 + 验收清单） |

## 按文档类型分

### 学设计（读这五份就够）

| 文档 | 讲什么 | 长度 |
|---|---|---|
| [pattern-selection-guide.md](pattern-selection-guide.md) | **从业务需求推导设计**：三误区 → 六步推导 → 何时抽象 → 接口/继承/组合 → 三类模式各怎么选 → 五版演进 → 五次过度设计 → 21 行决策卡片 | 858 行 |
| [02-pattern-comparison.md](02-pattern-comparison.md) | 相似模式的**定义级**差异，带代码对照 | 405 行 |
| [07-composition-vs-inheritance.md](07-composition-vs-inheritance.md) | 组合与继承的成本对照；§0 是 23 个模式的**严谨四分类**（子类型分派 3 = 类继承 1 + 接口实现 2 / 组合委托 10 / 用组合但意图不是组合 6 / 归不进去 4）；§1.4 把 extends / implements / 多态 / 组合拆开 | 563 行 |
| [10-uml-atlas.md](10-uml-atlas.md) | 25 张类图 + 2 张时序图。每张图固定回答四件事：谁创建、谁持有、谁控制流程、变化时改谁 | 1471 行 |
| [01-learning-path.md](01-learning-path.md) | 学习顺序的详细说明（三条路线：速成 / 系统 / 设计能力） | 253 行 |

### 练设计

| 文档 | 讲什么 |
|---|---|
| [practice/README.md](practice/README.md) | 五个练习的总览、使用方法、「做完」的判定标准 |
| [practice/01-order-discount.md](practice/01-order-discount.md) | 优惠计算：一个方法里的 if-else 怎么拆成三个正交维度 |
| [practice/02-order-state.md](practice/02-order-state.md) | 状态流转：**状态对象和查表什么时候该选哪个** |
| [practice/03-notification.md](practice/03-notification.md) | 消息通知：广播为什么不是责任链，也不是中介者 |
| [practice/04-file-export.md](practice/04-file-export.md) | 报表导出：模板方法和策略**不是二选一** |
| [practice/05-api-request.md](practice/05-api-request.md) | 请求管线：一个机制装不下所有事，剩下的该放哪 |

### 联系工程

| 文档 | 讲什么 |
|---|---|
| [06-modern-scenarios.md](06-modern-scenarios.md) | JDK（集合 / I/O / 动态代理 / Flow / 并发）、Spring（IoC / AOP / 事件 / MVC / Bean 生命周期）、MyBatis（插件 / Executor / Interceptor）、Netty（Pipeline / Handler / 事件传播）；以及 record / sealed / 虚拟线程吃掉了哪些模式 |

### 项目自身的记录（读代码前先看这些能省时间）

| 文档 | 讲什么 | 什么时候看 |
|---|---|---|
| [00-source-review.md](00-source-review.md) | 接手原项目时的源码审查：问题清单、优先级、已修/未修 | 想知道「为什么代码长这样」 |
| [03-optimization-log.md](03-optimization-log.md) | 四轮改造日志（§8 第三轮：删构建脚本、修理论错误、补 UML / 选择指南 / 练习；§9 第四轮收尾：只修准确性与一致性） | 想知道改了什么、为什么改 |
| [05-verification.md](05-verification.md) | 编译命令、25 个入口的逐条输出、文档链接审计、**已知未验证项** | 想自己重跑验证 |
| [08-beginner-teaching-template.md](08-beginner-teaching-template.md) | 入门文档的八段式模板与验收清单 | 想新增或改写 `BEGINNER.md` |
| [09-beginner-refactor-report.md](09-beginner-refactor-report.md) | 23 份 `BEGINNER.md` 的改造范围、逐类规模、已知问题 | 想了解入门层的质量基线 |

## 关于编号

`docs/` 里**没有 `04-*.md`**。这是历史遗留：原编号 04 是一份已被内容吸收进 [03-optimization-log.md](03-optimization-log.md) 的中间记录，删除后编号保留空位，以免所有已有引用失效。

新增文档从 `10-` 开始编号；不带编号的两份（`pattern-selection-guide.md`、`practice/`）是**主线文档**，故意不加编号 —— 它们不是"第 N 份记录"，而是学习路径上的正式一环。

## 和 `src/` 的关系

`docs/` 里的所有图、所有代码片段、所有类名，都对应 `src/com/patterns/` 下的真实文件。

- 类图对不上 → [10-uml-atlas.md 附录 A](10-uml-atlas.md) 有「图 ↔ 文件」对照表
- 想直接看某个模式的实现 → 根 [README 的 23 种模式导航](../README.md#23-种模式导航)
- 练习的参考实现 → `docs/practice/solutions/`（刻意**不**放 `src/`，因为含大量反例与对照实现，不是工程代码）

---

**返回**：[项目根 README](../README.md)
