# 编译运行验证记录

> 本文件只记录**实际执行过**的验证，所有数字均来自命令输出。
> 采集时间：2026-10-22；全量复核：2026-10-23（第四批文档补强与源码修复之后重新采集）。
> **最新复核：2026-10-24**（新增 `combined.apigateway` 六模式组合案例 24 个文件 + `docs/06-modern-scenarios.md` 之后，全量编译、25 个入口、文档审计全部重跑，**所有数字以本节为准**）。
> 任何未执行的验证都会在 §7 明确标注，不做「应该能通过」的推断。

## 1. 验证环境

| 项 | 值 |
|---|---|
| 操作系统 | Windows（x64） |
| JDK 路径 | `F:\enCodingApps\JDK\jdk-25` |
| `java -version` | `openjdk 25.0.4.1 2026-08-18 LTS`（Microsoft-14951867，build 25.0.4.1+1-LTS） |
| `javac -version` | `javac 25.0.4.1` |
| 构建依赖 | 无（Maven / Gradle / JUnit 均未引入） |
| 编码 | UTF-8 |

## 2. 全量编译

命令（等价于 `build.bat` 的编译段，额外开启全部告警）：

```bash
javac -Xlint:all --release 25 -encoding UTF-8 -d out $(find src -name "*.java")
```

| 指标 | 结果 |
|---|---|
| 源文件数 | **287** |
| 退出码 | **0** |
| 错误 | **0** |
| 告警（`-Xlint:all`） | **0** |
| 产出 class 文件 | **361** |
| 编译耗时 | 5.9 秒（`Stopwatch` 实测，含 287 文件的完整前端编译） |
| stderr 字节数 | **0**（错误与告警都走 stderr，0 字节即「一个字符都没输出」） |

> **为什么本轮 class 数是 361 而不是上一轮的 326**：上一轮输出目录用的是 `out`，里面混有 IDEA 的 `out\production\design_patterns_pro` 旧产物，`Get-ChildItem -Recurse` 会重复计数。本轮改为编译到独立目录 `_verify\classes`（`out` 目录被 IDE 占用、写权限受限），得到干净的 **361** 个 class。
> 两个数字的关系可以核对：**326（上一轮基线）+ 35（apigateway 的 24 个文件产生的嵌套类）= 361**。

### 2.1 287 个源文件的构成

| 部分 | 文件数 | 行数 |
|---|---|---|
| 23 种模式（23 个包） | 241 | 24420 |
| `combined.orderservice`（四模式组合示例） | 20 | 3547 |
| `combined.apigateway`（六模式组合示例，2026-10-24 新增） | 24 | 2927 |
| `com.patterns.util`（`Money` 91 行、`DisplayWidth` 138 行） | 2 | 229 |
| **合计** | **287** | **31123** |

### 2.2 逐模块规模（实测，`Get-ChildItem -Recurse`）

| 模块 | 源文件 | 代码行 | README 行 |
|---|---|---|---|
| 单例 | 6 | 263 | 1282 |
| 工厂方法 | 12 | 225 | 919 |
| 抽象工厂 | 14 | 300 | 870 |
| 建造者 | 6 | 351 | 1179 |
| 原型 | 4 | 242 | 1186 |
| 适配器 | 7 | 172 | 1223 |
| 桥接 | 10 | 214 | 1118 |
| 组合 | 4 | 365 | 1566 |
| 装饰器 | 8 | 408 | 1726 |
| 外观 | 8 | 635 | 1905 |
| 享元 | 6 | 469 | 2968 |
| 代理 | 8 | 737 | 2325 |
| 责任链 | 14 | 1743 | 2656 |
| 命令 | 16 | 1923 | 5343 |
| 解释器 | 16 | 1987 | 4471 |
| 迭代器 | 11 | 1807 | 10579 |
| 中介者 | 13 | 1623 | 10782 |
| 备忘录 | 13 | 1590 | 11673 |
| 观察者 | 11 | 1535 | 5184 |
| 状态 | 12 | 1951 | 3546 |
| 策略 | 10 | 1537 | 3577 |
| 模板方法 | 6 | 1780 | 5214 |
| 访问者 | 26 | 2563 | 2615 |
| 组合应用 · orderservice | 20 | 3547 | 6057 |
| 组合应用 · apigateway（新） | 24 | 2927 | 5092 |
| **合计** | **285** | **30894** | **95056** |

> 说明：合计的 285 / 30894 不含 `com.patterns.util` 的 2 个文件 / 229 行（与上一版口径一致），加上即 §2 的 287 / 31123。抽象工厂从 297 修正为 **300**（本轮重测，上一版少算 3 行）。

> 说明：2026-10-23 的复核批次里，适配器 / 桥接 / 工厂方法 / 建造者 / 原型的 README 从 188-285 行扩写到 870-1223 行（新增 §7 扩展对比、§8.3 已知瑕疵清单、§9 面试追问），单例 / 组合 / 装饰器 / 外观 / 享元也同步扩写到 1282-2968 行。本表是扩写后的实测值。

> 另一处订正：上一版此处的结论是「创建型与结构型 README（188-791 行）明显短于行为型」——**该结论已不成立**。补强后创建型与结构型为 870-2968 行，行为型为 2615-11673 行，两侧已有重叠。剩下的真实差距只在两份最深的文档（中介者 10782、备忘录 11673），差距如实保留，不做粉饰。

## 3. 25 个入口逐一运行

每个模块的主类单独启动，**显式固定 `-Dstdout.encoding=UTF-8`**，用 `Start-Process -RedirectStandardOutput` 做字节级重定向（不用 PowerShell 的 `>`，原因见 §4.1.1），再统计行数、字节数与 SHA256。字节数为文件长度（含 CRLF）。

| # | 入口类 | 退出码 | 输出行数 | 字节数 | SHA256（前 16 位） |
|---|---|---|---|---|---|
| 1 | `creational.singleton.SingletonClient` | 0 | 23 | 992 | `E94C01D6EF0DEA2B` |
| 2 | `creational.factorymethod.Client` | 0 | 8 | 392 | `B5F024D79033B191` |
| 3 | `creational.abstractfactory.Client` | 0 | 16 | 520 | `5356F49B361AEBF2` |
| 4 | `creational.builder.Client` | 0 | 13 | 1182 | `BE8BF185A09C825C` |
| 5 | `creational.prototype.Client` | 0 | 37 | 1073 | `7412EB2B5CA64B95` |
| 6 | `structural.adapter.Client` | 0 | 9 | 336 | `17E89E88B9DB6AF1` |
| 7 | `structural.bridge.Client` | 0 | 25 | 774 | `527F6F60F491634E` |
| 8 | `structural.composite.Client` | 0 | 27 | 1097 | `DF2F01838AA366E1` |
| 9 | `structural.decorator.Client` | 0 | 29 | 1391 | `551D0ADCFF282FC2` |
| 10 | `structural.facade.Client` | 0 | 74 | 3921 * | `14FC105045EE7654` * |
| 11 | `structural.flyweight.Client` | 0 | 77 | 4474 | `5818120EC6001FF7` |
| 12 | `structural.proxy.Client` | 0 | 113 | 5875 * | `049FC8F90BB52287` * |
| 13 | `behavioral.chainofresponsibility.Client` | 0 | 132 | 8282 | `403461BA3401A765` |
| 14 | `behavioral.command.Client` | 0 | 252 | 13517 | `967197016DA33BEE` |
| 15 | `behavioral.interpreter.Client` | 0 | 134 | 6501 | `FD30D6AE5E2C1026` |
| 16 | `behavioral.iterator.Client` | 0 | 120 | 5877 | `06D82FE51A106D5E` |
| 17 | `behavioral.mediator.Client` | 0 | 125 | 8632 | `D49210724EB114CE` |
| 18 | `behavioral.memento.Client` | 0 | 86 | 4945 | `61695C97ED24A51D` |
| 19 | `behavioral.observer.Client` | 0 | 83 | 4766 | `37F20A1E649D09B4` |
| 20 | `behavioral.state.Client` | 0 | 155 | 6856 | `AD11B4469BA736A2` |
| 21 | `behavioral.strategy.Client` | 0 | 139 | 8298 | `DADFB4B68F1BC468` |
| 22 | `behavioral.templatemethod.Client` | 0 | 275 | 16782 * | `F95CCF6E3261508E` * |
| 23 | `behavioral.visitor.Client` | 0 | 123 | 6948 | `07BD39FA17182442` |
| 24 | `combined.orderservice.Client` | 0 | 152 | 9797 | `DF1E29B1FCAAF0C6` |
| 25 | `combined.apigateway.Client` | 0 | 304 | 16085 | `E4888CC8F3ACB967` |

> `*` 该模块输出含运行时时间戳 / 耗时 / 业务流水号，字节数与哈希每次运行都不同，表中为一次样本；行数稳定。原因见 §4.1。
>
> 与上一版相比，**建造者（13 行 / 1226 → 13 行 / 1182）与原型（36 行 / 1018 → 37 行 / 1073）的字节数发生了变化**，原因是这一轮修掉了两个真实缺陷：`builder.Client` 原先复用同一个 `Director` 实例、`prototype.Client` 原先通过 `getImages()` 把内部集合泄漏给外部。代码改了，输出自然跟着变，旧哈希已作废。

**结果：25 / 25 全部退出码 0，无一个模块抛异常或崩溃。**

> 2026-10-24 本轮对全部 25 个入口重新跑了一遍（`javac --release 25` 全量重编后，用临时目录 `_verify\classes` 做 classpath；该目录已随临时产物清理，重跑时用 `build.bat` 生成 `out\` 或重新 `javac -d _verify\classes` 均可），结果：**退出码全部 0，stderr 全部 0 字节，stdout 总行数 2531 行**，逐模块行数与本表完全一致。
> 其中 apigateway 的 304 行 / 16085 字节 / SHA `E4888CC8…` 是**两次运行完全一致**的确定性输出，可用于回归。

### 3.1 入口完整性核对

用 `Select-String -Pattern "public static void main"` 扫全项目，得到 **25 个含 `main` 的类**，与 25 个模块一一对应，没有第二个入口：

```text
creational     singleton.SingletonClient / factorymethod.Client / abstractfactory.Client
               builder.Client / prototype.Client
structural     adapter.Client / bridge.Client / composite.Client / decorator.Client
               facade.Client / flyweight.Client / proxy.Client
behavioral     chainofresponsibility / command / interpreter / iterator / mediator
               memento / observer / state / strategy / templatemethod / visitor （各 Client）
combined       orderservice.Client / apigateway.Client
```

所以上表 **25 行即全部入口**，没有遗漏、也没有被排除的入口。

> 原项目建造者模块曾有 `Client` 与 `Client2` 两个入口（分别演示传统 Builder 与简化 Builder）。重构后两种用法合并进一个 `Client`，用 `--- 方式 1 ---` / `--- 方式 2 ---` 分段输出，`Client2` 已删除。本表数据采集于合并之后。

## 4. 可复现性

「能跑通」不等于「结果稳定」。判断输出里有没有不确定来源，靠的是**连跑两次逐行 diff**，不是肉眼观察。

### 4.1 确定性检测：21 个稳定，3 个必然不同

24 个入口各连跑 2 次，逐行 `Compare-Object`：**21 个完全相同，3 个每次都不同**。

| 模块 | 不确定来源 | 差异示例 |
|---|---|---|
| `structural.facade` | 审计日志打印 `LocalTime.now()` | `[Audit] 08:35:07  REGISTER ...` |
| `structural.proxy` | 动态代理打印方法耗时；快照名带 `nanoTime` | `耗时 745 μs`、`@338183835700` |
| `behavioral.templatemethod` | 支付 / 物流流水号带 `yyyyMMddHHmmss` | `PAY20261023083512-0001` |

这三处**行数稳定，字节数会随时间数字的位数浮动**（`745` 与 `1203` 都是 3~4 位），因此它们的 SHA256 不可复现。这是设计使然而非缺陷：审计时间、性能耗时、业务流水号本来就该是运行时值。其余 21 个模块的字节数与哈希可稳定复现。

> 组合示例（第 24 行）连续运行 5 次，均为 152 行 / 9797 字节 / `DF1E29B1…8DD7`，是稳定的。

### 4.1.1 统计口径：必须显式固定 stdout 编码

同一份字节码、同样的 152 行文本，stdout 编码不同，字节数与哈希就完全不同：

| 运行方式 | 行数 | 字节数 | SHA256（前 16 位） |
|---|---|---|---|
| `java -Dstdout.encoding=UTF-8 -cp out ...Client` | 152 | **9797** | `DF1E29B1FCAAF0C6` |
| `java -Dstdout.encoding=GBK -cp out ...Client` | 152 | **8102** | `53928A97720547FF` |

差异原因：同一个汉字 GBK 占 2 字节、UTF-8 占 3 字节。组合示例输出共有 1695 个汉字，`9797 - 8102 = 1695`，差值恰好对上。

**本文 §3 的表格统一采用 `-Dstdout.encoding=UTF-8`**，换机器、换默认代码页都不会变。

> 踩过的坑（本次真踩了）：**不要用 PowerShell 的 `>` 统计字节数**。PowerShell 会先按控制台代码页解码、再按自己的编码写出，代码页表示不了的字符被替换成 `?`，于是出现「行数完全对、字节数差几十」的假数据——本文件上一版有 9 个模块的字节数与哈希就是这样错的，已按 `-Dstdout.encoding=UTF-8` + `Start-Process -RedirectStandardOutput` 重新采集。
>
> 这条是真正的教学点：**任何「哈希一致」的断言，必须同时固定编码、换行符（CRLF/LF）、统计工具；少固定一个，断言就没有意义。**

### 4.2 其他模块

以下模块在开发阶段各做过 3 次哈希比对，均一致：

| 模块 | 需稳定的关键点 |
|---|---|
| 解释器 | 10 段 Demo 中所有数值结果、错误消息与列号 |
| 访问者 | 8 段 Demo 中购物车金额与统计汇总 |
| 迭代器 | DFS / BFS 的访问顺序 |
| 中介者 | 消息投递顺序与失败清单 |
| 备忘录 | 撤销/重做序列与元数据 |
| 观察者 | 通知顺序与投递结果 |
| 状态 | 155 行状态转移轨迹 |

其余模块只做了单次运行，但输出中不含任何非确定性来源（无时间、无随机、无并发）。

## 5. 文档审计

对全项目 33 个 Markdown 文件（25 份模块 README + 顶层 README + `docs/` 下 7 份）做统一机器审计，脚本为 `_probe/audit_docs.ps1`：

| 检查项 | 方法 | 结果 |
|---|---|---|
| 代码围栏成对 | 统计以 ` ``` ` 开头的行数（含列表内缩进围栏），必须为偶数 | **33/33 全部偶数** |
| 相对链接有效 | 先剥离围栏代码块与行内代码，再提取 `](path)`，`Join-Path` 后 `Test-Path` | **238 条，0 条失效** |
| 页内锚点存在 | 提取 `](#anchor)`，用「只保留字母与数字」的宽松键与各级标题比对 | **509 条，0 条失效** |
| 九大模块齐全 | 统计 `## ` 级标题数量 | 25 份模块 README 全部 ≥11 |

> **2026-10-27 新增 [07-composition-vs-inheritance.md](07-composition-vs-inheritance.md) 后**：表中的 238 条相对链接与 509 条锚点属于新增文档之前的全量审计值。新增文档已单独核对：468 行、5 对代码围栏（10 行）、28 条相对链接全部命中、0 条页内锚点引用。

> 2026-10-24 修了脚本的一个真 bug：原实现递归遍历整个项目目录，把 IDEA 编译产物 `out\production\design_patterns_pro\...` 里的 README **副本**当成第二份文档，一次误报了 28 条 BAD_LINK。加一句过滤后归零：
>
> ```powershell
> $mdFiles = Get-ChildItem -Path $pro -Recurse -Filter *.md |
>     Where-Object { $_.FullName -notmatch '\\(out|target|\.git|\.idea)\\' }
> ```
>
> **教训**：审计脚本的「扫描范围」和「检查规则」一样重要。规则错会漏报，范围错会误报；两者都会让审计结果不可信。

### 5.1 逐份数据

| README | 行数 | 围栏 | 偶数 | H2 | H3 | 失效链接 |
|---|---|---|---|---|---|---|
| chainofresponsibility | 2656 | 186 | Y | 11 | 61 | 0 |
| command | 5343 | 390 | Y | 11 | 64 | 0 |
| interpreter | 4471 | 384 | Y | 17 | 81 | 0 |
| iterator | 10579 | 874 | Y | 104 | 327 | 0 |
| mediator | 10782 | 972 | Y | 17 | 101 | 0 |
| memento | 11673 | 1076 | Y | 18 | 106 | 0 |
| observer | 5184 | 412 | Y | 17 | 76 | 0 |
| state | 3546 | 220 | Y | 17 | 64 | 0 |
| strategy | 3577 | 278 | Y | 11 | 53 | 0 |
| templatemethod | 5214 | 314 | Y | 11 | 60 | 0 |
| visitor | 2615 | 170 | Y | 17 | 87 | 0 |
| orderservice（组合） | 6057 | 490 | Y | 17 | 110 | 0 |
| apigateway（组合，新） | 5092 | 424 | Y | 18 | 78 | 0 |
| abstractfactory | 870 | 60 | Y | 11 | 47 | 0 |
| builder | 1179 | 80 | Y | 11 | 47 | 0 |
| factorymethod | 919 | 68 | Y | 11 | 47 | 0 |
| prototype | 1186 | 94 | Y | 11 | 43 | 0 |
| singleton | 1282 | 90 | Y | 11 | 50 | 0 |
| adapter | 1223 | 92 | Y | 11 | 52 | 0 |
| bridge | 1118 | 82 | Y | 11 | 53 | 0 |
| composite | 1566 | 98 | Y | 11 | 53 | 0 |
| decorator | 1726 | 112 | Y | 11 | 50 | 0 |
| facade | 1905 | 102 | Y | 11 | 51 | 0 |
| flyweight | 2968 | 276 | Y | 11 | 57 | 0 |
| proxy | 2325 | 164 | Y | 11 | 43 | 0 |
| **合计** | **95056** | **7508** | **Y** | — | — | **0** |

### 5.2 逐字输出核对（比链接检查更强的一层）

把这些 README 里的运行输出与真实输出**逐字比对**，而不是「看起来差不多」：

| README | 核对范围 | 结果 |
|---|---|---|
| state | §6 五段 Demo | 5/5 逐字匹配 |
| observer | §6 五段 Demo | 5/5 逐字匹配 |
| iterator | 全文输出块 | 双向逐字校验通过 |
| mediator | §6 六段 Demo | 6/6 匹配 |
| memento | §6 六段 Demo | 6/6 匹配 |
| visitor | §6 八段 Demo | 8/8 匹配 |
| interpreter | §6 十段 Demo | 10/10 匹配 |
| orderservice | §6 十段 Demo（152 行） | 逐段核对通过 |
| apigateway | §6 十段 Demo（304 行） | 逐段核对通过；两次运行 SHA 均 `E4888CC8…` |
| singleton | §6 输出（23 行 / 992 字节） | 行数、字节数与 SHA 均与实跑一致 |
| composite | §6 输出（27 行 / 1097 字节） | 同上 |
| decorator | §6 输出（29 行 / 1391 字节） | 同上 |
| flyweight | §6 输出（77 行 / 4474 字节） | 同上（两次运行 SHA 均为 `5818120EC6001FF7`） |
| facade | §6 输出（74 行 / 3921 字节） | 行数一致；字节数含审计时间戳，不可复现 |

其余 11 份（创建型 5 份中的 factorymethod / abstractfactory / builder / prototype + 结构型 adapter / bridge / proxy + 行为型 chainofresponsibility / command / state 等）的输出片段为**运行后直接粘贴**，只核对到「行数 / 字节数与实跑一致」这一层，未做逐字比对脚本；代码片段中的类名、方法名、行数均与源码核对过。

### 5.3 锚点审计口径：为什么不能用「精确 slug」校验

本轮审计踩到了一个值得记录的真坑。脚本第一版用「删除标点 → 空格转 `-`」推导每个标题的 slug，报告了 **18 条失效锚点**；逐一核对发现**全部是误报**，原因是各类渲染器对中文标点的处理并不一致：

| 字符 | 本文档体系的假设 | `github-slugger` 实际行为 |
|---|---|---|
| 全角 `：` `？` | 删除 | **保留**（不在删除集合里） |
| em dash `——` | 删除 | 按范围删除，但 `\u2014` 并不在 `\u2000-\u200A` 区间内，**实际保留** |
| 连续空格 | 合并成一个 `-` | 逐个空格转 `-`，**不合并** |

于是同一个标题在不同假设下会算出 2-3 个不同的 slug，无法做字节级校验。修正后的脚本把失败分两级：

- **BAD_ANCHOR（必须修）**：把锚点和标题都归一化成「只剩字母与数字」再比。能抓到「章节根本不存在」「多字少字」「缺前缀」这类真错误。本轮它实际抓出了 4 条真错：`#9-面试题与自测`（标题已改成「面试与自测」）、`#b-运行方式与验证记录`（标题带「附录 B」前缀）、`#43-第三步为什么必须引入-ordercontext`（章节已重写）、`#4-原始实现的if-推到`（标题改过），均已修好。
- **STRICT_MISS（仅记录）**：精确 slug 对不上但语义能对上，属渲染器差异，不计入错误。当前剩 **4 条**，全部落在中介者 / 策略两份文档的「含全角标点标题」上。

> 结论：**中文标题的页内锚点只能做语义校验，不能做字符串校验。**能保证的只有「链接指向的章节确实存在」，不能靠工具保证「点下去能在任意渲染器里精确跳转」。

## 6. 代码与文档一致性抽检

文档最容易出的错不是打错字，而是**写了不存在的 API**。本次对以下易错点逐一核对源码：

| 点 | 文档曾写 | 源码实际 | 状态 |
|---|---|---|---|
| 组合示例封顶作用域 | 整单封顶 | `PipelineFactory.Builder.cap()` 对**每个节点**各套一层 | 已修正 |
| 组合示例结果自洽 | 构造器强制校验「原价 − 共减 = 应付」 | 构造器只校验 null + `copyOf`；`totalReduction()` 现算 | 已修正 |
| 折扣注册表与帮助文本 | `helpText()` 随注册表变化 | `helpText()` 是硬编码 text block；受影响的是 `supportedCodes()` | 已修正 |
| 责任链标记方法 | `ChainState.markUsed()` | `ChainState.markGroup()` | 已修正 |
| 满减策略返回类型 | `FullReductionPolicy.decide()` 返回 `DiscountDecision` | `discount()` 返回 `BigDecimal`，无 `decide()` | 已修正 |
| 上下文周末判断 | `OrderContext.isWeekend()` | 无此方法；只有 `memberDay()` | 已修正 |
| 宏命令撤销 | `MacroCommand.undo()` | 无 `undo()`；是 `rollback()` 逆序补偿 | 已修正 |
| 模板方法步骤 | 五步「校验→计价→支付→发货→通知」 | 四步「库存检查→支付→发货→通知」 | 已修正 |
| 组合模式 `add` 归属 | 未说明 | 安全式：只在 `Directory` 上 | 已明确 |
| 责任链兜底位置 | 链尾节点 | 链对象 `ApprovalChain.submit()` | 已明确 |

## 7. 未验证 / 未覆盖项

如实列出，避免「全部验证通过」的误解：

| 项 | 状态 | 原因 |
|---|---|---|
| 单元测试 | **未编写** | 任务要求零依赖，不引入 JUnit。可复现性由「哈希比对」保证，不等价于覆盖率 |
| 并发正确性 | **部分验证** | 仅享元做过多线程竞争测试（`_probe/FlyweightProProbe.java`，16 线程 × 3000 轮），结果：`HashMap + computeIfAbsent` 有 **5/3000 轮**重复创建，`ConcurrentHashMap` 版为 0/3000。其余模块均为单线程 Demo，未压测 |
| `build.bat` / `build.sh` 端到端 | **未执行** | 两者调用的是同一组 `javac` / `java` 命令，本报告 §2 与 §3 已验证该命令组合；脚本本身只做路径拼接与遍历 |
| Linux / macOS | **未验证** | 当前环境为 Windows，`build.sh` 未经实机执行 |
| 创建型 / 结构型 README 的逐字输出比对 | **部分做** | 新补强的 5 份（singleton / composite / decorator / facade / flyweight）已核到行数与字节数；其余只做了链接与围栏审计 |
| apigateway 的并发正确性 | **未验证** | 全部 Demo 单线程。`breakers` 用普通 `LinkedHashMap`、`HalfOpen.tryPass()` 无条件放行等 5 处并发/逻辑风险已在 [apigateway/README.md](../src/com/patterns/combined/apigateway/README.md) §8.3 逐条列出，但**没有压测证明它们一定会出错** |
| 页内锚点在真实 GitHub 上的跳转 | **未验证** | 无网络环境实测，只验证了「目标章节存在」，见 §5.3 |

> 历史上此表曾有一行「`builder.Client2` 未纳入 24 入口矩阵」—— 该行已删除：`Client2` 已在建造者重构中合并进 `Client`（用 `--- 方式 1 ---` / `--- 方式 2 ---` 分段），项目里不再存在第二个入口。

## 8. 复现步骤

```bash
# 1. 编译（全量告警）
javac -Xlint:all --release 25 -encoding UTF-8 -d out $(find src -name "*.java")

# 2. 运行任意一个模式
java -cp out com.patterns.behavioral.strategy.Client

# 3. 验证组合示例的可复现性（显式固定 UTF-8，避开控制台代码页影响）
java -Dstdout.encoding=UTF-8 -cp out com.patterns.combined.orderservice.Client > run.txt
sha256sum run.txt
# 期望：DF1E29B1FCAAF0C65242EADDFE16D34D90BC8CECC81FAF498193AE9190518DD7（152 行 / 9797 字节）
# 若改用 -Dstdout.encoding=GBK：8102 字节 / 53928A97720547FF…，行数仍是 152。
```

Windows 下用 `build.bat` 一条命令完成编译 + 依次运行全部入口。

---

相关文档：[00 源码审查](00-source-review.md) · [01 学习路径](01-learning-path.md) · [02 模式对比](02-pattern-comparison.md) · [03 优化记录](03-optimization-log.md) · [07 组合与继承](07-composition-vs-inheritance.md)
