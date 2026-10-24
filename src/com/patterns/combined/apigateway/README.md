# 组合应用示例 2：API 网关请求管道（责任链 + 策略 + 装饰器 + 状态 + 观察者 + 享元）

> **一句话**：网关没有自己的业务，它只有「顺序」和「边界」。
> 六个模式在这里不是六个功能，而是六条**互不重叠的变更理由**——
> 任何一条被删掉，你都能立刻说出会缺什么；说不出来的抽象，就该删。

> **本目录 24 个文件、2927 行代码已用 JDK 25 实际编译运行验证**：
> `javac --release 25 -Xlint:all` 零警告零字节 stderr，运行输出 304 行 / 16085 字节
> （`-Dstdout.encoding=UTF-8` 口径），两次运行 SHA-256 完全一致
> （`E4888CC8F3ACB967…`）。
> 文中所有数字、v0 反例的每一行输出、每一个状态切换，都是真实执行得到的，不是编造的。
> 验证方式见[附录 B](#附录-b-编译运行与验证记录)。

---

## 元数据

| 项目 | 内容 |
|---|---|
| 包名 | `com.patterns.combined.apigateway` |
| 源文件 | 24 个 `.java` + 本 README |
| 物理行数 | 2927 行（源码 115589 字节） |
| 编译产物 | 35 个 `.class`，102034 字节 |
| JDK | 25（`javac --release 25 -Xlint:all -encoding UTF-8`，零警告） |
| 运行入口 | `com.patterns.combined.apigateway.Client` |
| 运行输出 | 304 行 / 16085 字节 / `E4888CC8F3ACB967D4E9BFD0F712198BF8B5D46B54AC0AABC5A7CF4CD9B800E6` |
| 对照基线 | 朴素实现 v0：150 行 / 输出 28 行 / 1853 字节（`_probe/apigw_v0`） |
| 涉及模式 | 责任链、策略、装饰器、状态、观察者、享元 |

---

## 目录

- [0. 结论先行：这个案例到底在证明什么](#0-结论先行这个案例到底在证明什么)
1. [现实案例：一个请求要过六道门](#1-现实案例一个请求要过六道门)
2. [朴素实现 v0：150 行，一个 if 到底](#2-朴素实现-v0150-行一个-if-到底)
3. [设计动机：为什么恰好是这六个模式](#3-设计动机为什么恰好是这六个模式)
4. [设计推导：从 v0 一步步推到 v5](#4-设计推导从-v0-一步步推到-v5)
5. [代码解析：24 个文件为什么存在](#5-代码解析24-个文件为什么存在)
6. [执行流程：十段 Demo 的真实输出](#6-执行流程十段-demo-的真实输出)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试与自测](#9-面试与自测)
- [附录 A. 文件清单](#附录-a-文件清单)
- [附录 B. 编译运行与验证记录](#附录-b-编译运行与验证记录)
- [附录 C. 交叉引用](#附录-c-交叉引用)
- [附录 D. 术语表](#附录-d-术语表)
- [附录 E. 缺陷取证台账](#附录-e-缺陷取证台账)
- [附录 F. 版本与约定](#附录-f-版本与约定)

---

## 0. 结论先行：这个案例到底在证明什么

### 0.1 三句必须放在最前面的话

**第一句：这个模块在原项目里没有对应物。**

`design_patterns`（原项目）有 22 个单模式 Demo，其中责任链、策略、装饰器、状态、观察者、享元**各自独立**，彼此不知道对方存在。它们能证明「我会写这个模式」，但证明不了另一件更难的事：

> 六个模式放进同一个方法调用栈里，谁在外、谁在内、谁先执行、谁负责短路。

所以本模块的「原始实现」（§2）不是从原项目搬来的，而是**照着大多数团队第一版网关的写法**写的一个 150 行朴素实现。它是探针，也是靶子。它的每一行输出都在 §2.2 里，可复现。

**第二句：代码从 150 行涨到 2927 行，涨了 19.5 倍。**

这个数字不藏着。§8.2 会逐项算这 19.5 倍买到了什么，也会说明哪一部分其实是**测试代码和文档代码的体量**，不是运行逻辑的体量：

| 类别 | 文件数 | 行数 | 是否参与请求处理 |
|---|---|---|---|
| 主流程（请求/响应/上下文/网关/过滤器链） | 8 | 约 900 | 是 |
| 六个模式的实现体 | 12 | 约 1500 | 是 |
| `Client` 演示与取证 | 1 | 476 | 否 |
| 其余（接口、监听器） | 3 | 约 150 | 部分是 |

**第三句：正文里出现的每一个结论，要么有运行输出，要么标注「未验证」。**

本项目从第一轮审查开始就守着一条规矩：**没跑过的东西不能说「验证通过」**。本文档 §7.4 与 §8.3 各列了一批「知道存在、但本 Demo 没有验证」的项，它们不会被悄悄写成优点。

### 0.2 一张图看懂全篇

```text
                        ┌──────────────────────────────────────────┐
   请求 ──────────────► │  过滤器链（责任链）                       │
   GatewayRequest       │  auth → rate → router → cache            │
                        │   │      │       │        │             │
                        │   │      │       │        └─ 命中就短路 │
                        │   │      │       └─ 享元池 + 策略选实例   │
                        │   │      └─ 计数不足就短路 429            │
                        │   └─ 认不出就短路 401                    │
                        └───────────────────┬──────────────────────┘
                                            │ 全部放行
                                            ▼
                        ┌──────────────────────────────────────────┐
                        │  装饰器栈（Decorator）                    │
                        │  Metrics( Retry( CircuitBreaker(Service)))│
                        │     │        │          │        │        │
                        │     │        │          │        └─ 真干活 │
                        │     │        │          └─ 状态模式：       │
                        │     │        │             CLOSED/OPEN/    │
                        │     │        │             HALF_OPEN        │
                        │     │        └─ 失败换实例再试             │
                        │     └─ 只数「逻辑调用」                    │
                        └───────────────────┬──────────────────────┘
                                            │
                                            ▼
                        ┌──────────────────────────────────────────┐
                        │  观察者（Observer）                       │
                        │  Event ──┬─► MetricsCollector（看分布）   │
                        │          └─► AlertListener（看连续性）    │
                        └──────────────────────────────────────────┘
```

这四段代码的**执行顺序**是这整篇文档的主线。记住一点就够：**上面的图里，越靠上的越关心「要不要进来」，越靠下的越关心「进来之后怎么活下来」。**

---

## 1. 现实案例：一个请求要过六道门

### 1.1 说人话版：你点了一下「我的订单」

假设你在手机 App 里点开「我的订单」。这一下，后台发生的事大致是：

```text
① App 带上 Authorization: Bearer t-1001，请求 GET /api/order
② 网关收到，先看你是谁          → 认出 u-1001
③ 再看你最近请求得多不多        → 这是本窗口第 1 次，放行
④ 再看这个路径该由谁处理        → order 服务的 v1 版本，两台机器
⑤ 再看这个结果有没有缓存过      → 没有，去问上游
⑥ 挑一台机器去问                → 挑了 order-1
⑦ 结果回来了，存一份缓存         → 下次同样的请求直接给你
⑧ 记一笔账：这次成功、耗时、谁处理的
```

你只看到「加载中…」然后看到订单列表。你不会去想：

- 为什么第 2 次点开快很多？（⑥ 被跳过了）
- 为什么同事能看到我的订单截图？（⑤ 的 key 写错了就会）
- 为什么那台 `order-2` 坏了以后，我这边没感觉到？（⑦ 前面还有熔断）
- 为什么我连着刷新 5 次就提示「操作太频繁」？（③ 在 ⑤ 前面）

**这四个「为什么」就是六个模式的六个落点。** 注意它们问的都是「顺序」或「边界」，没有一个是「算法怎么算」。

这就是网关这类中间件的本质：**它不生产业务数据，它决定业务数据以什么顺序、经过什么边界、被谁看到。**

### 1.2 专业版：六个正交关注点

GoF 二十三种模式里，这六个经常在一个网关里同时出现，因为它们恰好覆盖了「一次跨进程调用」的六个正交维度：

| 模式 | GoF 定义要点 | 本模块的具体形态 | 一句话职责 | 删掉会缺什么 |
|---|---|---|---|---|
| 责任链 Chain of Responsibility | 使多个对象都有机会处理请求，沿链传递**直到被处理** | `GatewayFilter` + `FilterChain.Cursor` | 这个请求**能不能进来** | 认证/限流逻辑只能写进 `Gateway.handle`，每加一个横切关注点就改一次网关 |
| 策略 Strategy | 定义一系列算法，封装起来，使它们可以互相替换 | `LoadBalancer` + 轮询/最少连接 | 进来后**打哪一台** | 换选路算法要改 `RouterFilter` 内部 |
| 装饰器 Decorator | 动态地给对象添加职责，比继承更灵活 | `Metrics(Retry(CircuitBreaker(Service)))` | 打的时候**怎么保护自己** | 重试/熔断/指标三层两两组合要写 8 个子类，且顺序焊死 |
| 状态 State | 允许对象在内部状态改变时改变它的行为 | `CircuitBreaker` 的三个 `BreakerState` | 此刻**能不能放行** | 三个方法里各写一份 `if (state == ...)`，加第四种状态必漏改 |
| 观察者 Observer | 定义一对多依赖，当对象改变状态时自动通知 | `GatewayListener` + 两个订阅者 | 结果**由谁消费** | 指标、告警、审计全部写进 `handle`，每接一个系统改一次网关 |
| 享元 Flyweight | 运用共享技术有效地支持大量细粒度对象 | `RouteTable` 池 + `RouteConfig` | **共享什么**、共享物必须只读 | 每个请求 `new` 一份路由配置；更糟的是「最少连接」读到的在途数散落在几万个副本上 |

请注意第一行和第三行的措辞差别：

- 责任链是「**直到被处理**」——它允许**短路**；
- 装饰器是「**前后都执行**」——它必须**把请求传下去，再把响应带回来**。

这两个词的区别，是本篇最容易混淆、也最容易写错的地方。§3.4 会用一张表把它们彻底分开。

### 1.3 为什么单模式示例不够

如果你只看过策略模式的示例，你看到的多半是：

```java
// 单模式示例的典型写法
Calculator calc = new Calculator();
calc.setStrategy(new PercentageDiscount());
System.out.println(calc.calculate(100));
```

这段代码没有任何问题，但它**藏起了三件在真实系统里最难的事**：

1. **没有第二个模式在场**。策略选出来的实例，接下来要经过熔断器；熔断器打开时要短路；短路的结果要不要缓存；缓存命中要不要记指标——单模式示例里这些都不存在。
2. **没有顺序**。`Retry` 在 `CircuitBreaker` 外面还是里面，单模式示例里根本不会出现这个问题。可它是**正确性问题**，不是风格问题（§3.3 有实测）。
3. **没有失败**。单模式示例的策略永远返回正确答案。而网关 90% 的设计复杂度来自失败：超时、过载、熔断、重试、降级。

所以本模块的每一个 Demo 都坚持打印**轨迹**，而不是只打印响应：

```text
  响应：200  data#1  <- order-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.0.0.1 第 1/3 次
    3. router: order:v1 → order-1（least-connections）
    4. cache: 未命中 [u-1001 | /api/order] → 放行
    5. upstream: order-1 第 1 次调用 → 200
    6. cache: 回填 [u-1001 | /api/order]
```

**响应会骗人，轨迹不会。** 一个 200 可能是缓存给的（不消耗上游容量）、可能是重试第二次才拿到的（消耗了两倍上游容量），也可能上游第一把就成了。三者响应体一模一样，对系统的压力差一个数量级。

这也是可观测性（observability）在工程上的最小定义：**不光要能回答「结果是什么」，还要能回答「结果是怎么来的」。**

---

## 2. 朴素实现 v0：150 行，一个 if 到底

### 2.1 v0 长什么样

几乎每个团队的第一版网关都长这样：所有逻辑塞进一个 `handle` 方法，从上到下 if 到底。下面是真实代码（完整 150 行见 `_probe/apigw_v0/LegacyGatewayV0.java`），删掉了注释以外的排版空白：

```java
Resp handle(String method, String path, String token, String ip) {
    // 缺陷 A：三种 401 合并成一句 "unauthorized"
    if (token == null || !TOKENS.containsKey(token)) {
        return new Resp(401, "unauthorized", "gateway");
    }
    if (rateCounter.merge(ip, 1, Integer::sum) > 3) {
        return new Resp(429, "too many requests", "gateway");
    }
    // 缺陷 B：缓存 key 只用 path —— 所有用户共享同一份私人数据
    if ("GET".equals(method) && cache.containsKey(path)) {
        return cache.get(path);
    }
    // 缺陷 C：熔断只有一个 boolean —— 没有半开，打开后没有任何恢复路径
    if (circuitOpen) {
        return new Resp(503, "circuit open", "gateway");
    }
    // 缺陷 D：选路算法硬编码在方法里 —— 换策略要改这个方法
    String instance = "order-" + (rrCursor++ % 2 + 1);
    Resp response = call(instance, path);
    if (response.status() >= 500) {
        // 缺陷 E：失败次数从不清零 —— 偶发错误会被累积成熔断
        failureCount++;
        if (failureCount >= 2) {
            circuitOpen = true;
        }
    }
    // 缺陷 F：连失败响应也缓存 —— 1 秒的抖动被固化成一整段故障
    if ("GET".equals(method)) {
        cache.put(path, response);
    }
    return response;
}
```

它**能跑**，功能看起来也齐全：认证、限流、缓存、熔断、选路、上游调用都有。这也正是它危险的地方——代码评审时你会觉得「该有的都有」，只有真的跑起来才会发现六处缺陷。

### 2.2 实测：四处缺陷当场出现

下面这 28 行是 v0 探针的真实输出（`java -Dstdout.encoding=UTF-8 -cp out LegacyGatewayV0`，exit 0，stderr 0 字节）：

```text
--- A 缓存 key 只用 path：B 拿到 A 的订单 ---
  u-1001 第 1 次 GET /api/order → 200  data#1  <- order-1
  u-1002 第 1 次 GET /api/order → 200  data#1  <- order-1
  ↑ 第二行本该是 order-1 为 u-1002 产生的 data#2，实际返回了 u-1001 的 data#1

--- B 失败响应也被缓存：上游已恢复，缓存里还是 503 ---
  第 1 次 GET /api/order/c（order-1 故障） → 503  upstream overload#1  <- order-1
  第 2 次 GET /api/order/c（上游已恢复） → 503  upstream overload#1  <- order-1
  上游调用计数：{order-1=1} —— 第 2 次没有增加，说明没打上游
  缓存内容：{/api/order/c=503} —— 503 被当成可缓存结果存下来了
  ↑ 一次 1 秒的抖动，被缓存固化成了「跟缓存活一样久」的故障

--- C+D+E 失败不归零 → 偶发错误累积成熔断，且打开后永不恢复 ---
  第 1 次（成功/失败交替） → 200  data#1  <- order-1
  第 2 次（成功/失败交替） → 503  upstream overload#1  <- order-2
  第 3 次（成功/失败交替） → 200  data#2  <- order-1
  第 4 次（成功/失败交替） → 503  upstream overload#2  <- order-2
  ↑ 成功率 50%，两次失败并不连续，但 failureCount 从不归零，仍然打开了熔断
  第 5 次（上游故障次数已耗尽） → 503  circuit open  <- gateway
  第 6 次（上游故障次数已耗尽） → 503  circuit open  <- gateway
  ↑ 上游已经恢复正常，但电路是 boolean，没有任何路径能把它关回去

--- F 三种 401 返回完全相同的响应 ---
  没有 token → 401  unauthorized  <- gateway
  token 过期 → 401  unauthorized  <- gateway
  未知 token → 401  unauthorized  <- gateway
  ↑ 三行逐字相同，监控上无法区分「客户端忘了带头」和「有人在伪造 token」
```

逐条读这 28 行，能读出五件事：

**（1）A 段是越权漏洞，不是性能问题。**

`u-1002` 第一次请求就拿到了 `u-1001` 的 `data#1`。根因只有一行：缓存 key 用了 `path` 而不是 `principal + path`。这类事故在线上叫「缓存越权」，它的可怕之处在于**响应码是 200、日志里一切正常**，只有用户截图对比才能发现。

新版对应的是 `CacheFilter` 的这一行：

```java
String cacheKey = context.principal() + " | " + request.path();
```

**（2）B 段证明「缓存失败响应」会把抖动放大成故障。**

第 1 次上游故障，503 被写进缓存；第 2 次上游已经恢复，但请求**根本没打到上游**（`上游调用计数：{order-1=1}` 没变），直接返回了缓存里的 503。

注意这条缺陷和 A 段是**同一个 root cause 的两个面**：`cache.put(path, response)` 既没判断「谁在看」，也没判断「值不值得缓存」。

**（3）C 段的熔断器打开得太早。**

成功/失败交替出现，成功率 50%，两次失败**并不连续**，但 `failureCount` 从不归零，累计到 2 就打开了。真实世界里这叫「抖动即熔断」——上游只是偶尔抖一下，网关却把整个服务摘了。

新版对应的是 `CircuitBreaker.Closed.onSuccess`：

```java
@Override
public void onSuccess(CircuitBreaker breaker) {
    breaker.consecutiveFailures = 0;   // ← v0 缺的就是这一行
}
```

**（4）C 段的熔断器打开后**永不恢复**。**

第 5、6 次请求时，上游的故障次数已经耗尽（`Map.of("order-2", 3)` 用完了），`order-1` 一直是好的。但电路是个 `boolean`，**没有任何代码能把它置回 false**。

这不是「忘了写」，而是「boolean 表达不了」。恢复需要三个信息：什么时候打开的、冷却多久、探路成功了没有。一个 `boolean` 装不下这三件事——**这是状态模式存在的根本理由**（§4.4 详述）。

**（5）F 段是监控的失败，不是功能的失败。**

三种 401 逐字相同。后果是：如果某天「未知 token」突增（可能是有人在撞库），你的 401 告警和「客户端忘了带头」混在一起，只能看到一条平滑的曲线。

新版把三个分支拆开，各自 `trace` 一句话（`AuthFilter`）：缺少头 / 已过期 / 无法识别。三条轨迹在日志里是三种不同的字符串，监控才能分桶。

### 2.3 还有两条跑不出来、但一定会出事的缺陷

上面五条都能用运行输出证明。另外两条**本 Demo 没有实测**，但它们属于「设计层面必然」，也说明清楚：

**（6）选路算法硬编码，换策略要改方法体。**

v0 里 `String instance = "order-" + (rrCursor++ % 2 + 1);` 就是全部选路逻辑。想换成「最少连接」「一致性哈希」「按机房就近」，只能改 `handle`。而 `handle` 是网关最热的方法，改它的风险远高于新增一个类。

这类问题的判定标准很简单：**这段逻辑会不会因为「和它相邻的代码」以外的原因而变化？** 会，就该被拆出去。

**（7）没有观察者，加一个下游系统就要改网关。**

v0 里要加指标，得在 `handle` 里插计数；要加审计，再插一段；要加链路追踪，再插一段。每插一次，`handle` 就长一点、慢一点、多一个 `try/catch` 的理由。

观察者把这层反转过来：**网关只说「发生了什么」，谁关心谁自己订阅。**

### 2.4 P1-P7 汇总

| 编号 | 缺陷 | 根因（一句话） | 用哪个模式修 | 本文档对应位置 |
|---|---|---|---|---|
| P1 | 缓存越权（A 段实测） | key 里没有调用方身份 | —— 修法在 `CacheFilter`，不是模式 | §5.5 |
| P2 | 缓存放大故障（B 段实测） | 失败响应也进缓存 | —— 修法在 `CacheFilter` | §5.5 |
| P3 | 抖动即熔断（C 段实测） | 成功时不清零连续失败计数 | 状态 | §5.3 |
| P4 | 熔断后永不恢复（C 段实测） | `boolean` 装不下「冷却 + 探路」 | 状态 | §5.3 |
| P5 | 401 无法分桶（F 段实测） | 三种语义合并成一句话 | —— 修法在 `AuthFilter` | §5.4 |
| P6 | 换策略必改网关 | 算法与调用点耦合 | 策略 | §5.2 |
| P7 | 加监控必改网关 | 通知方向写反了 | 观察者 | §5.8 |

注意 P1、P2、P5 后面写的是「修法在某个类，不是模式」。这很重要：

> **不是所有缺陷都该用模式来修。** 一个 key 拼错、一个 `if` 少写，用模式去「修」只会让代码更难懂。
> 模式解决的是「变化的结构」，不是「写错的逻辑」。

这条区分贯穿全篇，§8.4 会展开成一张「什么时候不该用模式」的清单。

---

## 3. 设计动机：为什么恰好是这六个模式

### 3.1 六条变更理由

判断一个抽象该不该存在，最有效的问法是：**「哪个需求变了，我只改这一个文件？」**

下面这张表把网关常见的六类需求变更列出来，看它落在哪一层：

| 今天要改的事 | 只该动的地方 | 不该动的地方 | 对应模式 |
|---|---|---|---|
| 加一个「签名校验」环节 | 新增 `SignatureFilter` | `Gateway`、其他过滤器 | 责任链 |
| 把「轮询」换成「一致性哈希」 | 新增 `ConsistentHashBalancer` | `RouterFilter`、`Gateway` | 策略 |
| 给上游调用加「超时 200ms」 | 新增 `TimeoutHandler` 包一层 | `ServiceHandler`、`RetryHandler` | 装饰器 |
| 熔断器加「强制打开供运维手动切」 | 新增 `ForcedOpen implements BreakerState` | `Closed`、`Open`、`HalfOpen` | 状态 |
| 新增「链路追踪」上报 | 新增 `TracingListener` | `Gateway.handle` | 观察者 |
| 路由配置加上「权重」字段 | `RouteConfig` + `RouteTable` | 所有过滤器 | 享元 |

反过来看这张表更有意思：**如果六件事都只能改 `Gateway.handle`，那就是 v0；如果六件事分散在六个地方，那就是 v5。**

而「六件事分散在六个地方」不是免费的，代价在 §8.2 算得很清楚：**150 行变成 2927 行。**

### 3.2 决策树：什么情况下**不该**上这套

这是全篇最容易被跳过、但最该先看的一节。模式不是越多越好，**网关这个场景特别容易过度设计**，因为「网关」听起来就该很复杂。

```text
你的服务需要统一处理「认证 + 限流 + 路由」吗？
│
├─ 不需要 ──────────────────────────────► 直接写业务，别做网关
│
└─ 需要
   │
   ├─ 只有 1 个上游、1 个环境、日请求 < 10 万
   │   └─► 用一个 Servlet Filter / 一个拦截器就够
   │       责任链用框架现成的（Spring 的 FilterChain、Servlet 的 Filter）
   │       不要自己写 Cursor，不要引入策略和状态
   │
   ├─ 上游有多台，需要选路
   │   ├─ 选路规则永远不会变（就轮询）
   │   │   └─► 一个方法就够，别抽 LoadBalancer 接口
   │   └─ 选路规则会变 / 需要按机房 / 需要灰度
   │       └─► 上策略模式
   │
   ├─ 上游会时不时挂，且挂的时候调用方要快速失败
   │   ├─ 挂的时候调用方能接受等 30 秒超时
   │   │   └─► 只配超时就够，不要熔断
   │   └─ 必须快速失败 + 自动恢复
   │       └─► 上状态模式（三态熔断器），并且必须配重试，且重试在外层
   │
   ├─ 需要缓存响应
   │   ├─ 响应与调用方无关（公共数据）
   │   │   └─► key 用 path 就行
   │   └─ 响应与调用方有关（我的订单）
   │       └─► key 必须含身份，并且必须想清楚「谁的数据谁看得见」
   │
   └─ 需要指标 / 告警 / 审计
       ├─ 只要一个计数器
       │   └─► 一个 AtomicLong 就够
       └─ 会持续接新的下游系统
           └─► 上观察者
```

这张树里有两个节点值得单独说：

**「只有 1 个上游」那一条。** 很多团队在只有一个上游的时候也上了完整的网关模式，结果是：六个模式的代码，服务着一台机器。模式的所有收益（隔离变化）都用不上，成本（理解、调试、性能）全额支付。这是典型的过度设计。

**「选路规则永远不会变」那一条。** 判断「会不会变」有个笨办法，但很有效：**问「上一次改这块是什么时候」**。如果一年没改过，那它大概也不会因为你加了个接口就变成高频变更点。

### 3.3 装饰器的顺序：这不是审美问题，是正确性问题

这是本篇证据最硬的一节。同一个 `RetryHandler`、同一个 `CircuitBreakerHandler`、同一个 `ServiceHandler`，只换一次包裹顺序，跑出来的数字差一倍。

探针代码在 `_probe/apigw_v0/WrappingOrderProbe.java`，配置统一为：熔断阈值 2、最多 2 次尝试、冷却 3 tick、上游持续失败。真实输出 35 行，关键部分如下：

```text
--- 正确：Retry( CircuitBreaker( Service ) ) ---
  请求 1（上游持续失败）
    → 503  上游过载（第 2 次调用，还会失败 97 次）  <- report-1，attempts=2
      轨迹：upstream: report-1 第 1 次调用 → 503 → retry: 第 2 次尝试，report-1 → report-1
            → upstream: report-1 第 2 次调用 → 503 → breaker: report-1 CLOSED → OPEN
            → retry: 本层已尝试 2 次，不再重试
    熔断器 state=OPEN，consecutiveFailures=2，transitions=1
    上游真实调用 report-1=2 次
  请求 2（熔断生效了吗？）
    → 503  熔断器打开（report-1），请求未打到上游  <- gateway，attempts=0
    熔断器 state=OPEN，consecutiveFailures=2，transitions=1
    上游真实调用 report-1=2 次
  请求 3（advance(3) 之后：半开探路应该只放行一次）
    → 503  熔断器打开（report-1），请求未打到上游  <- gateway，attempts=1
      轨迹：breaker: report-1 OPEN → HALF_OPEN（冷却 3 tick 已到，放行一次探路）
            → upstream: report-1 第 3 次调用 → 503 → breaker: report-1 HALF_OPEN → OPEN
            → retry: 第 2 次尝试，report-1 → report-1
            → breaker: report-1 处于 OPEN → 快速失败，未调用上游
            → retry: 本层已尝试 2 次，不再重试
    熔断器 state=OPEN，consecutiveFailures=2，transitions=3
    上游真实调用 report-1=3 次

--- 错误：CircuitBreaker( Retry( Service ) ) ---
  请求 1（上游持续失败）
    → 503  上游过载（第 2 次调用，还会失败 97 次）  <- report-1，attempts=2
    熔断器 state=CLOSED，consecutiveFailures=1，transitions=0
    上游真实调用 report-1=2 次
  请求 2（熔断生效了吗？）
    → 503  上游过载（第 4 次调用，还会失败 95 次）  <- report-1，attempts=2
    熔断器 state=OPEN，consecutiveFailures=2，transitions=1
    上游真实调用 report-1=4 次
  请求 3（advance(3) 之后：半开探路应该只放行一次）
    → 503  上游过载（第 6 次调用，还会失败 93 次）  <- report-1，attempts=2
    熔断器 state=OPEN，consecutiveFailures=2，transitions=3
    上游真实调用 report-1=6 次
```

把两组数字并排放：

| 观测量 | `Retry(CircuitBreaker(Service))` | `CircuitBreaker(Retry(Service))` | 差在哪 |
|---|---|---|---|
| 请求 1 之后的状态 | `OPEN`，consecutiveFailures=**2** | `CLOSED`，consecutiveFailures=**1** | 熔断器看不到重试的内部失败 |
| 请求 2 打到上游几次 | **0**（快速失败） | **2** | 熔断晚了一整个请求才生效 |
| 请求 3（半开探路）打到上游几次 | **1**（真的只探一次） | **2** | 「放行一次探路」被重试放大成两次 |
| 三次请求的上游总调用 | **3** | **6** | 整整一倍 |

**为什么错序会把「一次」变成「两次」？**

因为「半开只放行一次」这条约束，是由**熔断器自己**在被调用的那一刻用 `tryPass()` 实现的。错序时熔断器在外层，它只放行了一次 `handle`，但这次 `handle` 里面是 `Retry`，`Retry` 会自己再调两次下游——**熔断器根本不知道重试的存在，也就无法约束它。**

这就是「装饰器顺序是正确性问题」的全部含义：

> 每一层装饰器能约束的，**只有它内部的那一层**。
> 你想约束谁，就必须包在谁外面。

所以正确的顺序是按「约束的宽窄」从外到内排：

```text
Metrics(        ← 只关心「一次逻辑调用」，必须最外层，否则 3 次重试会被记成 3 次调用
  Retry(        ← 决定「要不要再来一次」，它必须在熔断器外面，才能让每次重试都经过熔断判断
    CircuitBreaker(   ← 决定「这一次能不能放行」，它必须紧贴上游，才能看到每一次真实调用
      Service )))     ← 真干活
```

还有一个细节值得注意：正确顺序下，请求 3 的最终响应是 `503 熔断器打开 <- gateway`，而不是 `503 上游过载 <- report-1`。

**上游真实错误被熔断器的拒绝覆盖了。** 这是真实系统里一个容易被忽略的取舍：用户体验上，一个笼统的 503 比「上游过载」更不友好；但排查时，`servedBy=gateway` 这个字段能立刻告诉你「不是上游挂的，是我们自己拒的」。§8.3 会把这条列进「已知瑕疵」。

### 3.4 责任链 / 装饰器 / 管道：三者到底差在哪

这三个词在中文技术文章里经常混用，但它们的行为完全不同。用一句话区分：

| | 是否一定传到尾 | 是否能提前返回 | 典型场景 |
|---|---|---|---|
| 管道 Pipeline | **是**，每段都要执行 | 否 | 数据转换、ETL、`Stream` |
| 责任链 Chain | **否**，可以在这里终止 | **是** | 认证、限流、审批 |
| 装饰器 Decorator | **是**，但会包住内层、能拿到返回 | **否**（它不拒绝，只是包装） | 重试、熔断、缓存、日志 |

本模块里两者的分工非常明确：

```text
过滤器链（责任链）：auth → rate → router → cache
   ↑ 每一环都可能 return（401 / 429 / 404），后面的完全不执行

装饰器栈（Decorator）：Metrics(Retry(CircuitBreaker(Service)))
   ↑ 每一层都必须调用 delegate，并且必须把结果原样（或加工后）返回
```

**为什么不能把两者合成一条链？** 因为「能不能不执行」这个语义完全不同：

- 责任链里，`cache` 命中时**后面的上游调用根本不该发生**——这是性能，也是正确性；
- 装饰器里，`Metrics` 绝不能因为某种原因「跳过」`Retry`——跳过了就没保护了。

如果把 `Metrics` 做成过滤器放在 `cache` 后面，会发生什么？缓存命中的请求根本不经过它——于是你的「QPS」指标永远偏低，而且偏得莫名其妙（热门路径偏低最多）。这是一个非常常见的监控陷阱。

> **顺序的第二层含义：位置决定了「你看得见什么」。**
> 放在缓存后面，你就看不见缓存命中的流量；放在限流后面，你就看不见被限流的流量。

§5.7 会用一个具体的类（`MetricsHandler` 的注释）再强调一次。

---

## 4. 设计推导：从 v0 一步步推到 v5

这一节不讲「模式是什么」，只讲**每一步是被什么问题逼出来的**。每一步的格式都一样：

```text
出了什么问题 → 最小修法 → 修完又冒出什么新问题 → 下一步
```

### 4.1 v0 → v1：把 if 拆成链，先解决「方法无限变长」

**问题**：v0 的 `handle` 里已经有 6 段逻辑（认证、限流、缓存、熔断、选路、上游调用）。每加一个横切关注点（签名校验、灰度打标、请求日志），就要在一个 40 行的方法中间插一段。

**最小修法试错一：抽出私有方法。**

```java
Resp handle(...) {
    Resp r = authenticate(token);        if (r != null) return r;
    r = rateLimit(ip);                   if (r != null) return r;
    r = checkCache(method, path);        if (r != null) return r;
    ...
}
```

**新问题**：方法短了，但 `handle` 仍然是「知道所有环节」的那一个。而且每加一个环节，还是要改 `handle`（加一行调用）。**变化的耦合点没有移动，只是变短了。**

**最小修法试错二：把每个环节抽成类，让 `handle` 遍历它们。**

```java
for (Filter f : filters) {
    Resp r = f.handle(req);
    if (r != null) return r;
}
```

**新问题**：这个循环跑不了「洋葱式」的过滤器。有些环节需要「调用下一个，然后对返回结果做加工」（比如缓存要回填、日志要记录耗时）。而 `if (r != null) return r` 的写法里，**过滤器拿不到「下一个是谁」**。

**真正的解法：把「下一个」作为参数传给当前环节。**

这就是责任链的标准形态：

```java
// GatewayFilter.java（节选）
public abstract GatewayResponse handle(CallContext context, FilterChain.Cursor next);

// AuthFilter.java（节选）
if (token == null) {
    return GatewayResponse.reject(401, "缺少 Authorization 头");   // ← 短路：不调 next
}
context.principal(principal);
return next.proceed();                                             // ← 放行
```

**这一步解决了什么？** 加一个 `SignatureFilter` 只需要：写一个新类，在 `Gateway` 组装时插到 `List` 里。`handle` 不动，其他过滤器不动。

**但立刻冒出一个新问题：`Cursor` 放哪？**

最容易写错的版本是让 `FilterChain` 自己持有游标：

```java
// 错误写法
public final class FilterChain {
    private int cursor;                       // ← 被所有请求共享
    private final List<GatewayFilter> filters;

    public GatewayResponse handle(GatewayRequest request) {
        if (cursor < filters.size()) {
            return filters.get(cursor++).handle(request, this);
        }
        return terminal.handle(request);
    }
}
```

单线程 Demo 完全看不出问题。并发时会发生什么：

```text
线程 A：进入 filter[0]，cursor 变成 1
线程 B：进入 filter[1]，cursor 变成 2      ← B 跳过了 filter[0]（认证！）
线程 A：继续，cursor 已经是 2，直接跳到 filter[2]
```

后果是「跳过认证」——最严重的那一类事故，而且只在压测时复现。

**修法：把可变状态收敛到「每次调用新建」的对象里。**

```java
// FilterChain.java（节选）
public Cursor newCall(GatewayRequest request) {
    return new Cursor(request, 0);        // ← 每个请求一个游标
}

public final class Cursor {
    private final CallContext context;
    private int index;                    // ← 只属于这一次调用

    public GatewayResponse proceed() {
        if (index < filters.size()) {
            return filters.get(index++).handle(context, this);
        }
        return terminal.handle(context);
    }
}
```

这条规则可以迁移到任何「链 + 上下文」的实现：

> **共享的部分（过滤器列表、末端处理器）必须无状态；会变的部分（游标、上下文）必须收敛到请求作用域。**

本项目在享元模块用探针实测过同类的共享可变状态问题（`HashMap + computeIfAbsent` 在 16 线程 × 3000 轮下出现 5 次重复创建），所以这里不是「理论上可能」，而是「同一个坑的第二种形态」。

### 4.2 v1 → v2：把「选哪台」从链里挖出来

**问题**：`RouterFilter` 里要选一台实例。第一版写法：

```java
// RouterFilter 第一版
UpstreamInstance instance = route.instances().get(rrCursor++ % route.instances().size());
```

**新问题**：一天后要「按机房就近」，一周后要「按权重灰度」，一个月后要「一致性哈希保证同一用户打到同一台」。每次都要改 `RouterFilter`，而它是链上的一环——改它就要重新测整条链。

**最小修法：抽一个接口。**

```java
// LoadBalancer.java（节选）
public interface LoadBalancer {
    String name();
    UpstreamInstance pick(List<UpstreamInstance> instances);

    default UpstreamInstance pickExcluding(List<UpstreamInstance> instances, UpstreamInstance exclude) {
        if (exclude == null) {
            return pick(instances);
        }
        List<UpstreamInstance> candidates = instances.stream()
                .filter(instance -> !instance.equals(exclude)).toList();
        return candidates.isEmpty() ? pick(instances) : pick(candidates);
    }
}
```

**为什么 `pickExcluding` 要放在接口里做默认方法，而不是让每个策略各写一遍？**

因为「排除一个再挑」对**任何**策略都成立——它是契约的一部分，不是某个算法的细节。这跟 `Comparator` 把 `reversed()` 做成默认方法是一个道理：**默认方法装的是「所有实现都成立的公共语义」。**

反过来说：如果某个方法的实现会因策略不同而不同（比如 `pick`），它就绝不能有默认实现——有默认实现就等于给「忘记实现」开了后门。

**为什么两个实现内嵌在接口里？**

```java
static LoadBalancer roundRobin() { return new RoundRobin(); }
static LoadBalancer leastConnections() { return new LeastConnections(); }

final class RoundRobin implements LoadBalancer { ... }
final class LeastConnections implements LoadBalancer { ... }
```

因为它们各自只有十几行，且离开这个接口就没有独立意义。JDK 的 `Comparator.naturalOrder()` 就是这么放的。**策略家族小的时候，内嵌比散落成文件更好读**；一旦某个实现长到需要自己的状态和测试，再拆出去。

**这一步的收益**：`RouterFilter` 变成一行 `balancer.pick(route.instances())`，它**永远不需要再改**。换算法 = 新增一个类 + 改一行组装代码。

**但这一步也埋了一个坑，Demo 7 把它暴露了出来。**

`LeastConnections` 的实现是：

```java
UpstreamInstance best = null;
for (UpstreamInstance instance : instances) {
    if (best == null || instance.inFlight() < best.inFlight()) {
        best = instance;
    }
}
return best;      // ← 全部相等时永远返回第一个
```

**在单线程、无人工延迟的 Demo 里，每次选路时所有实例的 `inFlight` 都是 0**，于是它永远选第一台。Demo 7 的实测输出：

```text
  最少连接网关：order-1 被调用 2 次，order-2 被调用 0 次
```

这是**演示环境的产物，不是算法缺陷**。真实网关里请求会重叠，第一台的 `inFlight` 涨到 5 时，选路会自动跳到为 0 的第二台。

但它也指向一个真实的线上故障模式：**如果你在线上看到「最少连接只打一台」，第一个要怀疑的是 `inFlight` 没有正确释放**（`try/finally` 里漏了 `release()`），而不是算法写错了。`ServiceHandler` 里的 `try/finally` 就是为这个写的。

### 4.3 v2 → v3：给「调用上游」套上保护

**问题**：上游会抖。需要重试、需要熔断、需要指标。第一版写法是在 `RouterFilter` 里直接：

```java
Resp r = service.call(instance);
if (r.status() >= 500) {
    instance = balancer.pickExcluding(...);
    r = service.call(instance);
}
```

**新问题**：这是把重试写死在选路里。接下来要加熔断，就得再插一段 if；要加指标，再插一段。而且**顺序焊死**——一旦写进代码，就无法单独测试「只有重试没有熔断」的组合。

**最小修法：让「处理上游」变成一个接口，用包装代替插入。**

```java
// UpstreamHandler.java（节选）
public interface UpstreamHandler {
    GatewayResponse handle(CallContext context);
    default String describe() { return getClass().getSimpleName(); }
}
```

只有一个方法的接口，天生适合层层包裹：

```java
// Gateway.java（节选）—— 顺序就是这一行决定的
this.serviceHandler  = new ServiceHandler(scriptedFailures);            // 最内层
this.breakerHandler  = new CircuitBreakerHandler(serviceHandler, ...);
this.retryHandler    = new RetryHandler(breakerHandler, maxAttempts);
this.metricsHandler  = new MetricsHandler(retryHandler);                // 最外层
```

**为什么不用继承？** 三层需求两两组合是 2³ = 8 个子类（要不要指标 × 要不要重试 × 要不要熔断），四层就是 16 个。而且顺序固定死在类层次里，想换一次顺序要重写一条继承链。用包装，顺序就是**组装时的一行代码**。

**顺序的正确性在 §3.3 已经用实测证明了**（上游总调用 3 次 vs 6 次）。这里只补一句设计层面的总结：

> 装饰器的顺序不是「谁先谁后」的偏好，而是**「谁能约束谁」的从属关系**。
> 外层能约束内层，内层约束不了外层。

**这一步同时解决了一个隐蔽问题：指标口径。**

`MetricsHandler` 放在最外层，它数的是「一次逻辑调用」；放在 `Retry` 里面，3 次重试会被记成 3 次调用，于是「QPS 300、错误率 0%」这种自相矛盾的监控数据就出来了。

### 4.4 v3 → v4：熔断器为什么必须是三个类

这是全篇推导最长的一步，因为它要走过三个版本，而每一版都**能跑**。

#### 第一版：boolean

```java
private boolean circuitOpen;

if (circuitOpen) return reject(503, "circuit open");
...
if (response.status() >= 500) {
    failureCount++;
    if (failureCount >= 2) circuitOpen = true;
}
```

**问题**：`circuitOpen = true` 之后，**没有任何代码路径能把它置回 false**。§2.2 的 C 段实测了这一点：上游故障次数早就用完了，请求仍然一直返回 `503 circuit open <- gateway`。

根本原因不是「忘了写恢复逻辑」，而是 **`boolean` 装不下恢复所需的三条信息**：

1. 什么时候打开的（决定什么时候可以试探）
2. 连续失败了几次（决定什么时候该打开）
3. 探路成功了没有（决定是回去还是继续拦）

#### 第二版：enum + switch

```java
enum State { CLOSED, OPEN, HALF_OPEN }

private State state = State.CLOSED;

boolean tryPass() {
    return switch (state) {
        case CLOSED, HALF_OPEN -> true;
        case OPEN -> now() - openedAt >= coolDown;
    };
}

void record(boolean success) {
    switch (state) {
        case CLOSED -> {
            if (success) consecutiveFailures = 0;
            else if (++consecutiveFailures >= threshold) { openedAt = now(); state = OPEN; }
        }
        case OPEN -> { /* 不放行，不会收到回调 */ }
        case HALF_OPEN -> state = success ? CLOSED : OPEN;
    }
}
```

**这一版是对的**，而且 JDK 21+ 的 `switch` 模式匹配让它相当好读。但它有一个具体的坏味道：

> **三个方法的每一个里，都有一份完整的 `switch (state)`。**

现在加第四种状态（比如「强制打开，供运维手动切」），你需要在 `tryPass`、`record`、以及未来任何新方法里各加一个 `case`。**漏改一处，编译器不会提醒你**——因为 `switch` 语句不要求穷尽。

#### 第三版：sealed interface + 三个状态类

```java
private sealed interface BreakerState permits Closed, Open, HalfOpen {
    String name();
    boolean tryPass(CircuitBreaker breaker);
    void onSuccess(CircuitBreaker breaker);
    void onFailure(CircuitBreaker breaker);
}

private static final class Closed implements BreakerState {
    public boolean tryPass(CircuitBreaker breaker) { return true; }

    public void onSuccess(CircuitBreaker breaker) {
        breaker.consecutiveFailures = 0;              // ← v0 缺的就是这一行
    }

    public void onFailure(CircuitBreaker breaker) {
        breaker.consecutiveFailures++;
        if (breaker.consecutiveFailures >= breaker.failureThreshold) {
            breaker.openedAt = breaker.now();
            breaker.transition(new Open());
        }
    }
}

private static final class Open implements BreakerState {
    public boolean tryPass(CircuitBreaker breaker) {
        if (breaker.now() - breaker.openedAt >= breaker.coolDownTicks) {
            breaker.transition(new HalfOpen());
            return true;                              // ← 冷却到了，放行一次探路
        }
        return false;
    }
    public void onSuccess(CircuitBreaker breaker) { /* 见下 */ }
    public void onFailure(CircuitBreaker breaker) { /* 见下 */ }
}

private static final class HalfOpen implements BreakerState {
    public boolean tryPass(CircuitBreaker breaker) { return true; }

    public void onSuccess(CircuitBreaker breaker) {
        breaker.consecutiveFailures = 0;
        breaker.transition(new Closed());             // ← 探路成功，恢复
    }

    public void onFailure(CircuitBreaker breaker) {
        breaker.openedAt = breaker.now();
        breaker.transition(new Open());               // ← 探路失败，重新打开
    }
}
```

**关键收益：新增一个状态只需要新增一个类，`implements BreakerState` 会强制你把四个方法都实现。** 编译器从「帮不上忙」变成「替你检查」——这是从 enum 换到对象模型的唯一实质理由。

**为什么 `Open.onSuccess` / `Open.onFailure` 是空的，而不是抛异常？**

```java
@Override
public void onSuccess(CircuitBreaker breaker) {
    // OPEN 不放行，因此不会收到回调；留空实现而不是抛异常，
    // 因为「状态刚切走但回调晚到一步」在真实系统里是可能发生的
}
```

因为状态切换和结果回调**不在同一个原子块里**。并发场景下完全可能出现：请求 A 在 `OPEN` 时被放行（此刻正好冷却到），`tryPass` 把它切成 `HALF_OPEN`，然后请求 B 上报结果，`record` 读到的 `state` 已经是 `HALF_OPEN`……这中间的时序永远有缝隙。

**留空实现 = 幂等；抛异常 = 把时序缝隙变成线上异常。** 这是状态模式里一个容易被忽略但很关键的细节。

**为什么用 `sealed`？** 因为它把「状态集合是封闭的」这件事写进了类型系统。如果将来有人想加 `ForcedOpen`，必须显式改 `permits` 列表——**改动是可见的、被记录在 diff 里的**，而不是悄悄多一个 `case`。

**为什么时间用 tick 而不是毫秒？** 见 §5.3。一句话：可复现。

### 4.5 v4 → v5：享元和观察者，解决两个「方向」的问题

这两个模式放在最后，因为它们解决的不是「请求怎么走」，而是两个方向性问题：

- **享元解决「向下共享」**：多个请求共享同一份配置；
- **观察者解决「向外广播」**：一个结果通知多个下游。

#### 享元：共享不只是省内存

**问题**：`RouteTable.resolve(path)` 每次都返回一个新构造的 `RouteConfig`。

第一反应是「这点内存不算什么」——确实，一个 `RouteConfig` 只有四个字段。但真正的问题在第二个后果上：

> **`RouteConfig` 里持有 `List<UpstreamInstance>`，而 `UpstreamInstance` 里有 `inFlight` 计数器。
> 如果每个请求都新建一份，那么「最少连接」读到的 `inFlight` 会分散在几万个副本上，全部是 0，策略直接失效。**

这个后果是逻辑推理，本 Demo **没有**单独写探针去测（§8.3 会标注）。但它的机制很直接：`new UpstreamInstance(...)` 每次得到的是**不同的对象**，`AtomicInteger` 也是新的，计数器当然不会累积。

所以享元在这里的价值是双重的：

| 收益 | 说明 | 是否实测 |
|---|---|---|
| 省内存 | 13 个请求只创建 3 次路由配置（`池内 3 条路由，命中 6 次，创建 3 次`） | ✅ Demo 10 实测 |
| 保住状态一致性 | 同一份 `inFlight` 只有一个真相，最少连接才有意义 | ⚠ 逻辑推理，未单独实测 |

**共享物的第一个要求是「不可变」。** 所以 `RouteConfig` 用 `record`，且在紧凑构造器里 `List.copyOf(instances)`：

```java
// 关键一行：调用方之后无论怎么改传进来的 List，都影响不到这份共享配置
instances = List.copyOf(instances);
```

如果少了这一行，调用方拿到 `RouteConfig` 后 `config.instances().clear()`，影响的是**所有走这条路由的请求**——这类 bug 的排查成本极高，因为「谁改的」根本无从查起。

**池的 key 为什么用 `record` 而不是字符串拼接？**

```java
public record Key(String service, String version) { }
```

因为 `service + ":" + version` 在 `service` 为 `null` 时会得到 `"null:v1"`，与真的传了字面量 `"null"` 撞成同一个 key。享元模块实测过这个碰撞：`('a', null)` 与 `('a', "null")` 落到了同一个池条目上。用 `record`，`null` 和 `"null"` 天然不同。

#### 观察者：广播的东西必须只读

**问题**：请求处理完之后，指标、告警、审计、链路追踪都想知道结果。如果让 `Gateway` 依次调用它们，网关就从「处理请求」变成「处理请求 + 知道所有下游系统」。

**修法**：网关只广播一个不可变的 `Event`，谁关心谁自己订阅。

```java
// Gateway.java（节选）
GatewayListener.Event event = new GatewayListener.Event(
        request.requestId(), request.path(), response.status(), response.servedBy(),
        context.instance() == null ? null : context.instance().name(),
        context.cacheHit(), context.attempts());
served++;
for (GatewayListener listener : listeners) {
    listener.onEvent(event);
}
```

**为什么 `Event` 必须是 `record`（不可变）？**

因为它会被同时投递给多个订阅者。只要有一个订阅者能改它，后面的人看到的就是被改过的数据。**广播的东西必须只读**——这和 `GatewayResponse` 必须不可变是同一个理由（它要被缓存、被多个观察者同时读取）。

**为什么两个订阅者要分开，而不是在指标里加个 if？**

因为它们关注的东西不同：

```text
MetricsCollector：关心「全量分布」→ 状态码分布 {200=9, 401=3, 429=1}
AlertListener   ：关心「异常连续性」→ 连续 2 次失败就报警
```

混在一起的结果是「告警逻辑藏在统计代码里」，改告警阈值要动指标类。

**为什么告警看「连续」而不是「总数」？**

因为总量阈值会被流量本身放大：QPS 从 10 涨到 10000，失败数当然从 1 涨到 1000，但错误率可能一点没变。**连续失败才真正说明「现在出问题了」**，这也是熔断器数连续失败的原因——两个地方用同一个统计口径，不是巧合。

**观察者的代价，必须写清楚**：

```java
// GatewayListener.java（节选）
 * <p><b>约定：实现必须是「快且不抛异常」的。</b>
 * 观察者是串行调用的，一个慢订阅者会拖慢所有请求；
 * 一个抛异常的订阅者会毁掉整次调用。
```

本 Demo 里这个约定**没有强制**：`Gateway.handle` 直接 `for` 循环调用，既没有 `try/catch` 也没有异步。真实实现要么异步（丢进队列），要么在 `onEvent` 内部把异常全部吞掉并自行上报。§8.3 会把它列进「已知瑕疵」。

### 4.6 类结构与运行时对象

**静态结构（谁依赖谁）：**

```text
                        Gateway
                           │ 组装
        ┌──────────────────┼───────────────────┐
        │                  │                   │
   FilterChain        UpstreamHandler      List<GatewayListener>
   (过滤器链)          (装饰器栈)             (观察者)
        │                  │                   │
        ├─ AuthFilter      MetricsHandler       ├─ MetricsCollector
        ├─ RateLimitFilter    └→ RetryHandler   └─ AlertListener
        ├─ RouterFilter          └→ CircuitBreakerHandler
        │    ├→ RouteTable           └→ ServiceHandler
        │    │    └→ RouteConfig (享元)
        │    │         └→ UpstreamInstance
        │    └→ LoadBalancer (策略)
        │         ├→ RoundRobin
        │         └→ LeastConnections
        └─ CacheFilter
```

注意 `RouterFilter` 有两条出边，分别指向享元工厂和策略——这是「一个类同时用两个模式」的典型形态，也是组合案例的核心：

> **模式之间通过对象协作，而不是通过代码合并。**
> 如果 `RouterFilter` 里出现 `new RoundRobinBalancer()`，享元与策略就都被焊死在这里了。

**运行时对象图（一次缓存未命中的请求）：**

```text
GatewayRequest(r-1, GET, /api/order, Bearer t-1001, 10.0.0.1)
        │
        ▼
   FilterChain.newCall() ──► Cursor{ index=0, context=CallContext }
                                  │
                                  │ context 会被逐层写入：
                                  │   principal=u-1001
                                  │   route=RouteConfig(order,v1)  ← 享元对象
                                  │   balancer=LeastConnections
                                  │   instance=order-1
                                  │   attempts=1
        ┌─────────────────────────┘
        ▼
  auth → rate → router → cache ──miss──► MetricsHandler
                                            │ calls++
                                            ▼
                                         RetryHandler
                                            │ tried=1
                                            ▼
                                     CircuitBreakerHandler
                                            │ order-1=CLOSED, pass
                                            ▼
                                       ServiceHandler
                                            │ order-1.acquire()
                                            │ data#1
                                            │ order-1.release()
                                            ▼
                                     200 data#1 <- order-1
                                            │
        ┌───────────────────────────────────┘
        ▼
  cache.put("u-1001 | /api/order", response)
        │
        ▼
  Event(r-1, /api/order, 200, order-1, order-1, false, 1)
        │
        ├──► MetricsCollector（total++, statusCounts{200:1}, ...）
        └──► AlertListener（success → failureStreak = 0）
```

这张图值得看两遍，因为它把「同一个 `CallContext` 被五层依次写入」这件事画了出来。**如果换成返回值传递，这五层就得各自定义自己的 DTO，并层层拆包。**

---

## 5. 代码解析：24 个文件为什么存在

这一节按「模式」而不是按「目录顺序」组织。每个文件只回答两个问题：

1. **它做的这件事，为什么非它不可？**
2. **删掉它，会缺什么？**

如果第二个问题答不上来，那个文件就该删——这是本项目的固定标准。

### 5.1 四个数据载体：请求、响应、上下文、实例

#### `GatewayRequest`：不可变的请求

```java
public record GatewayRequest(String requestId, String method, String path, String token, String clientIp) {

    public GatewayRequest {
        requestId = require(requestId, "requestId");
        method = require(method, "method").toUpperCase(Locale.ROOT);
        path = require(path, "path");
        clientIp = require(clientIp, "clientIp");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path 必须以 / 开头，收到：" + path);
        }
    }
    ...
}
```

两个设计点：

**（1）`token` 没有被校验，允许为 `null`。** 这不是漏写：

```java
 * <p><b>为什么 {@code token} 允许为 {@code null}？</b>
 * 匿名请求必须能被「认证过滤器」拒绝并返回 401，
 * 而不是在构造阶段就抛异常——那样错误就变成了「网关崩了」而不是「你没登录」。
 * <b>校验的位置决定了错误的语义</b>。
```

如果 `GatewayRequest` 的构造函数拒绝 `null` token，那么一个匿名请求到达网关时，抛出的会是 `IllegalArgumentException`，最后变成 500。而正确的语义是 401。

**同一个「不合法输入」，在构造器里是程序错误，在过滤器里是用户错误。** 放在哪一层校验，决定了对外暴露的是哪种。

**（2）紧凑构造器集中校验，方法体内不再判空。** 这是 `record` 相对普通类的最大便利：所有字段的约束都写在一个地方，读完构造器就知道这个对象的所有不变量。

#### `GatewayResponse`：三个判断方法，每个都有理由

```java
public boolean success()        { return status >= 200 && status < 300; }
public boolean retryable()      { return status >= 500; }
public boolean upstreamFailure() { return retryable() && !"gateway".equals(servedBy); }
```

`success()` 和 `retryable()` 都好理解，**第三个是本篇最重要的一行**。

它存在的原因是一次真实的死循环，§7.1 会完整复盘。这里先把结论写出来：

> 「状态码是 5xx」**不等同于**「换一个实例还有机会」。
> 熔断器打开时返回的 503 是**网关自己**拒的，重试只会被同一道门再拒一次。

所以 `GatewayResponse` 必须能区分「谁产生了这个响应」。这就是 `servedBy` 字段存在的理由：

```java
 * <p><b>为什么要把「谁产生的」也记下来（{@code servedBy}）？</b>
 * 因为这是排查线上问题的第一个问题：「这个 503 是网关自己拒的，还是上游服务吐的？」
 * 两者含义完全不同——前者说明是限流/熔断在保护系统，后者说明上游真的出故障了。
```

Demo 8 的实测输出把这两种 503 摆在一起：

```text
  c-2 → 503  上游过载（第 2 次调用，还会失败 1 次）  <- report-1     ← 上游吐的
  c-3 → 503  熔断器打开（report-1），请求未打到上游  <- gateway       ← 网关拒的
```

#### `CallContext`：唯一「可以被改」的那个

```java
public final class CallContext {
    private final GatewayRequest request;   // 不可变
    private final List<String> trace = new ArrayList<>();
    private String principal;               // 认证后写入
    private RouteConfig route;              // 路由后写入
    private LoadBalancer balancer;
    private UpstreamInstance instance;      // 选路后写入
    private int attempts;                   // 每次真实上游调用 +1
    private boolean cacheHit;
}
```

**为什么它可以可变，而 `GatewayRequest` 不可变？**

```java
 * <p>两者的生命周期不同：请求对象描述「调用方发来的东西」，任何过滤器都无权修改它；
 * 上下文描述「网关处理到哪一步了」，天生就是要被不断写入的。
 * <b>把不变的东西做成不可变，把要变的东西集中到一个明确的对象里</b>。
```

**为什么 `trace` 用 `ArrayList` 而不是线程安全集合？**

```java
 * <p><b>为什么 {@code trace} 是 {@link ArrayList} 而不是线程安全的集合？</b>
 * 因为上下文<b>只属于一个请求</b>，永远只被一个线程处理。
 * 给它加锁等于给「只有一个人用的抽屉」上锁，只会变慢。
```

**`repickInstance()`：重试为什么要换实例？**

```java
public UpstreamInstance repickInstance() {
    if (balancer == null || route == null) {
        throw new IllegalStateException("尚未路由，无法换实例");
    }
    UpstreamInstance next = balancer.pickExcluding(route.instances(), instance);
    this.instance = next;
    return next;
}
```

如果刚才那台机器正在过载，原样再打一次大概率还是 503，**只是把同样的失败重放一遍，还多占了一次配额**。

但这里有个必须承认的边界：`/api/report` 只有一台实例时，`pickExcluding` 找不到候选，会退回原实例。Demo 8 的轨迹里能直接看到：

```text
  retry: 第 2 次尝试，report-1 → report-1
```

**这是诚实的退化，不是 bug。** 只有一台机器时，重试本来就只能打自己；真正该救它的是熔断器（§5.3），而不是重试。

#### `UpstreamInstance`：为什么是 class 而不是 record

```java
public final class UpstreamInstance {
    private final String name;
    private final String zone;
    private final AtomicInteger inFlight = new AtomicInteger();
    ...
}
```

**因为它有一个会变的字段：`inFlight`。**

```java
 * <p><b>为什么是类而不是 {@code record}？</b>
 * 因为它有一个会变的字段：当前在途请求数 {@code inFlight}。
 * {@code record} 的语义是「不可变的数据载体」，把计数器塞进去，
 * 就等于宣称「这个对象不会变」却在背后改它——这是本项目在享元模块里踩过的坑
 * （{@code record} 的字段连反射都改不了，只能靠把可变状态藏在别的对象里，反而更难懂）。
 * <b>有身份、有状态的东西就该是普通类。</b>
```

**`equals` 按 `name` 判等**，这一条比看起来重要：

```java
@Override
public boolean equals(Object other) {
    return this == other || (other instanceof UpstreamInstance that && name.equals(that.name));
}
```

因为它让 `pickExcluding(list, exclude)` 里的 `!instance.equals(exclude)` 能正确工作——即使传进来的 `UpstreamInstance` 是另一个对象，只要名字相同就会被排除。如果忘了重写 `equals`，排除逻辑会静默失效（永远排除不掉），重试就会一直打同一台机器。

#### `RouteConfig`：享元的「享元」

```java
public record RouteConfig(String service, String version, String pathPrefix, List<UpstreamInstance> instances) {

    public RouteConfig {
        ...
        // 关键一行：调用方之后无论怎么改传进来的 List，都影响不到这份共享配置
        instances = List.copyOf(instances);
    }

    public String routeKey() {          // 展示用，不用于查池
        return service + ":" + version;
    }

    public boolean matches(String path) {
        return path != null && path.startsWith(pathPrefix);
    }
}
```

三个细节：

- **`List.copyOf`**：共享物的第一要求是不可变（§4.5）。
- **`routeKey()` 只用于展示**：`service + ":" + version` 在 `null` 时会撞车，所以查池用的是 `record Key`，不是这个字符串。
- **`matches()` 存在但本 Demo 没被用到**：`RouteTable.resolve` 自己做了前缀匹配。这是一个**明确的冗余**——留着是因为「路由配置应该能判断自己是否匹配某个路径」在语义上属于它；不用是因为 resolve 需要遍历 registry 的键顺序。§8.3 会把它列进「已知未使用」。

### 5.2 策略：`LoadBalancer`

```java
public interface LoadBalancer {
    String name();
    UpstreamInstance pick(List<UpstreamInstance> instances);

    default UpstreamInstance pickExcluding(List<UpstreamInstance> instances, UpstreamInstance exclude) {
        ...
    }

    static LoadBalancer roundRobin() { return new RoundRobin(); }
    static LoadBalancer leastConnections() { return new LeastConnections(); }

    final class RoundRobin implements LoadBalancer { ... }
    final class LeastConnections implements LoadBalancer { ... }
}
```

四个设计决定：

**（1）`pickExcluding` 是默认方法，`pick` 不是。**

「排除一个再挑」对任何策略都成立，是契约；「怎么挑」因策略而异，是算法。**默认方法只能装契约，不能装算法**——否则就是给「忘记实现」开后门。

**（2）两个实现内嵌在接口里。**

家族小、单个实现不到 20 行、离开接口没有独立意义——这种情况内嵌比散落成文件更好读。JDK 的 `Comparator.naturalOrder()` 就是这么做的。

**（3）`RoundRobin` 用 `AtomicInteger` 而不是 `int`。**

```java
private final AtomicInteger cursor = new AtomicInteger();
...
int index = Math.floorMod(cursor.getAndIncrement(), instances.size());
```

真实网关并发处理请求，`cursor++` 在多线程下会重复发到同一台机器上。`Math.floorMod` 而不是 `%`，是为了让计数器溢出成负数时仍然返回非负下标（`Integer.MIN_VALUE % 2 == 0`，但 `Integer.MIN_VALUE % 3 == -2`，会 `IndexOutOfBounds`）。

**（4）`LeastConnections` 的退化是已知的。**

```java
// 全部在途数相同（例如都是 0）时会一直返回第一个 —— 这个"退化"在 Client 里有实测
return best;
```

Demo 7 实测：`最少连接网关：order-1 被调用 2 次，order-2 被调用 0 次`。解释见 §4.2。

**删掉 `LoadBalancer` 会怎样？** `RouterFilter` 里会写死一种算法；换算法要改 `RouterFilter`，而 `RouterFilter` 是链上的一环，改它要重测整条链（§3.1）。

### 5.3 状态：`CircuitBreaker` 与它的装饰器

#### 三个状态，三个方法，一个 sealed

```java
private sealed interface BreakerState permits Closed, Open, HalfOpen {
    String name();
    boolean tryPass(CircuitBreaker breaker);
    void onSuccess(CircuitBreaker breaker);
    void onFailure(CircuitBreaker breaker);
}
```

为什么是这四个方法而不是别的组合？因为它们恰好是「熔断器对外的全部行为」：

| 问题 | 方法 | 谁来问 |
|---|---|---|
| 现在是什么状态？ | `name()` | 日志 / 监控 / 运维页面 |
| 这次请求能放行吗？ | `tryPass()` | 装饰器 |
| 放行后的结果是好是坏？ | `onSuccess()` / `onFailure()` | 装饰器 |

**「能否放行 / 成功怎么办 / 失败怎么办」这三件事必须成组变化**，这是状态模式在这里成立的判断依据（§4.4 推导过三版）。

#### 为什么按实例熔断，而不是按服务

```java
 * <p><b>为什么按实例而不是按服务熔断？</b>
 * 一个服务有 10 台机器，只有 1 台出问题。按服务熔断的结果是
 * 「因为有 1 台坏了，所以整个服务 800 个请求全部被拒」——
 * 这是把局部故障放大成全局故障。按实例熔断，坏的被隔离，好的继续服务。
```

对应的代码就是 `CircuitBreakerHandler` 里的一张 map：

```java
private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();

public CircuitBreaker breakerOf(String instanceName) {
    return breakers.computeIfAbsent(instanceName, name ->
            new CircuitBreaker(name, clock, failureThreshold, coolDownTicks));
}
```

这里用 `computeIfAbsent` 是安全的，因为 `breakers` 是 `LinkedHashMap` 且 Demo 单线程。**真实多线程环境必须换成 `ConcurrentHashMap`**，理由和享元池一样（§5.9）——§8.3 会标注这条差异。

#### 时间为什么是 tick

```java
private final AtomicLong clock = new AtomicLong();

public void advance(long ticks) {
    clock.addAndGet(ticks);
}
```

```java
 * <p><b>时间为什么是「tick」而不是毫秒？</b>
 * 因为本项目的所有输出必须可复现。真实实现用
 * {@code System.nanoTime()} 或 {@code Clock}；这里用一个由
 * {@link #advance(long)} 手动推进的逻辑时钟，把「等待 30 秒」
 * 压缩成「推进 3 个 tick」，同时保证两次运行结果逐字节一致。
```

实测证据：两次运行的输出 SHA-256 完全相同（`E4888CC8…`）。如果这里用 `nanoTime`，每次运行的结果都会不同，文档里就一个数字都不能引用了。

#### 轨迹为什么要单独打「OPEN → HALF_OPEN」

这是我在写代码时修的第二处问题。原来的写法是：

```java
// 修改前：只报「放行后的状态变化」
String afterPass = breaker.stateName();
...
if (!after.equals(afterPass)) {
    context.trace("breaker: xxx " + afterPass + " → " + after);
}
```

问题：`OPEN → HALF_OPEN` 是在 `tryPass()` 内部顺带完成的，不单独打一条，读者就只会看到：

```text
  breaker: report-1 HALF_OPEN → OPEN      ← HALF_OPEN 是哪儿来的？
```

修法是先把状态快照下来，再单独判断：

```java
boolean passed = breaker.tryPass();
String afterPass = breaker.stateName();

// OPEN → HALF_OPEN 是在 tryPass() 内部顺带完成的，必须单独打一条轨迹，
// 否则读者只会看到后面的「HALF_OPEN → CLOSED」，却不知道它是怎么进入 HALF_OPEN 的。
if (!afterPass.equals(before)) {
    context.trace("breaker: " + breaker.instanceName() + " " + before + " → " + afterPass
            + "（冷却 " + breaker.coolDownTicks() + " tick 已到，放行一次探路）");
}
```

实测输出（Demo 8 的 c-4）：

```text
    5. breaker: report-1 OPEN → HALF_OPEN（冷却 3 tick 已到，放行一次探路）
    6. upstream: report-1 第 3 次调用 → 503
    7. breaker: report-1 HALF_OPEN → OPEN
```

**「状态机要能解释自己是怎么走过来的」，这条要求对教学代码是硬要求，对生产代码同样成立**——线上排查熔断问题时，你最想知道的就是「它什么时候进的 OPEN，为什么进的」。

### 5.4 责任链：`GatewayFilter` 与 `FilterChain`

#### 为什么是抽象类而不是接口

```java
public abstract class GatewayFilter {
    public abstract String name();
    public abstract GatewayResponse handle(CallContext context, FilterChain.Cursor next);
}
```

```java
 * <p><b>为什么是抽象类而不是接口？</b>
 * 这里两者都行，选抽象类是为了后续可以放 {@code final} 的模板逻辑
 * （比如统一的耗时统计）。<b>接口给能力，抽象类给骨架</b>——
 * 现在还没有骨架，所以这个选择是可逆的、不重要的；
 * 项目里其他模块用接口，是因为它们确实只需要能力。
```

**把「不重要」写出来，比假装每个决定都深思熟虑更诚实。** 这个选择确实可以随时反悔，所以不该在文档里给它编一个宏大理由。

#### `Cursor`：一个必须每次新建的内部类

```java
public final class FilterChain {
    private final List<GatewayFilter> filters;   // 共享，无状态
    private final UpstreamHandler terminal;      // 共享，无状态

    public Cursor newCall(GatewayRequest request) {
        return new Cursor(request, 0);           // ← 每次调用新建
    }

    public final class Cursor {
        private final CallContext context;
        private int index;                       // ← 只属于这次调用
        ...
        public GatewayResponse proceed() {
            if (index < filters.size()) {
                return filters.get(index++).handle(context, this);
            }
            return terminal.handle(context);
        }
    }
}
```

`Cursor` 是**非静态内部类**，它隐式持有 `FilterChain.this`，所以能直接访问 `filters` 和 `terminal`。这样 `proceed()` 不需要把这两个字段再传一遍。

**代价**：每个 `Cursor` 多一个隐式引用（一个指针）。**收益**：`proceed()` 的签名只有两个字符 `()`，调用点极其干净。

`Cursor` 的构造函数是**包私有**的（没有 `public`）：

```java
Cursor(GatewayRequest request, int index) {
```

只有 `newCall` 能创建它。这是个很小但有效的约束：**外部无法伪造一个「从中间开始」的游标**——否则调用方可以 `new Cursor(request, 2)` 直接跳过认证。

#### `AuthFilter`：三个分支，三种线上问题

```java
if (token == null || token.isBlank()) {
    context.trace("auth: 缺少 Authorization → 短路 401");
    return GatewayResponse.reject(401, "缺少 Authorization 头");
}
if ("Bearer expired".equals(token)) {
    context.trace("auth: token 已过期 → 短路 401");
    return GatewayResponse.reject(401, "token 已过期，请刷新后重试");
}
String principal = TOKENS.get(token);
if (principal == null) {
    context.trace("auth: 未知 token → 短路 401");
    return GatewayResponse.reject(401, "无法识别的 token");
}
```

三个分支对应三种**完全不同的线上问题**：

| 分支 | 客户端该做什么 | 运维该关注什么 |
|---|---|---|
| 缺少头 | 检查自己的请求构造 | 某次发版后突增 → 客户端 bug |
| 已过期 | 刷新 token 后重试 | 刷新失败率 |
| 无法识别 | 重新登录 | **可能是伪造，需要告警** |

**这就是 P5 的修法。** 注意它不是「用某个模式修的」——它只是把三种语义分开写，并在 trace 里留下三个不同的字符串。§2.4 说过：不是所有缺陷都该用模式修。

**为什么它必须排第一？** 因为排在它后面的每一环都有成本（限流要读写计数器、路由要查享元池、缓存要算 key）。更重要的是 Demo 5 实测的那条：

```text
  401 之前的限流快照：{}
  401 之后的限流快照：{}
  快照里没有 10.0.0.5 —— 三个 401 一次配额都没消耗
```

**未认证的请求不该有权消耗任何人的配额。**

### 5.5 缓存与限流：两个「顺序决定正确性」的过滤器

#### `CacheFilter`：一行代码决定有没有越权漏洞

```java
String cacheKey = context.principal() + " | " + context.request().path();
```

对比 v0 的 `cache.get(path)`：Demo 3 实测，加了 `principal` 之后，`u-1002` 打同一路径是**未命中**，真的打到了上游（`order-1 第 2 次调用 → 200`），拿到自己的 `data#2`；而 v0 里 `u-1002` 拿到的是 `u-1001` 的 `data#1`。

**判断标准很简单：**

> 如果这个响应对不同调用方不同，key 里就必须有区分调用方的东西。

另外两条规则来自 HTTP 语义，不是风格偏好：

```java
if (!context.request().isGet()) {
    bypassed++;
    context.trace("cache: " + context.request().method() + " 不缓存");
    return next.proceed();
}
```

GET 是幂等的，POST 不是。缓存 POST 等于「第二次下单没有真的下单」——Demo 4 实测两次 POST 分别打出了 `pay-1 第 1 次调用` 和 `pay-1 第 2 次调用`，证明它没被短路。

```java
if (response.success()) {
    cache.put(cacheKey, response);
    context.trace("cache: 回填 [" + cacheKey + "]");
} else {
    context.trace("cache: " + response.status() + " 不缓存");
}
```

只缓存成功响应。v0 的 B 段实测了反面：`缓存内容：{/api/order/c=503}`——一次抖动被固化成了「跟缓存活一样久」的故障。

**注意 `cacheHit` 和 `attempts` 的关系**：缓存命中时 `attempts` 保持 0，因为**上游真的没被打**。这不是省了一个字段，而是让「缓存命中率」和「上游 QPS」能对得上账。

#### `RateLimitFilter`：固定窗口，以及它的边界效应

```java
int count = used.merge(ip, 1, Integer::sum);
context.trace("rate: " + ip + " 第 " + count + "/" + capacity + " 次");

if (count > capacity) {
    context.trace("rate: 超出阈值 → 短路 429");
    return GatewayResponse.reject(429, "请求过于频繁（每窗口上限 " + capacity + " 次）");
}
```

**为什么是固定窗口而不是令牌桶？**

固定窗口实现最简单（一个计数器）。代价是**窗口边界效应**：每秒 3 次的限额，在 0.9 秒打 3 次、1.1 秒再打 3 次，实际是 0.2 秒 6 次。令牌桶/滑动窗口能解决，但需要记录时间戳。

本 Demo 用「逻辑窗口」：`rollWindow()` 显式推进，这样既能演示边界，又不用引入真实时间。Demo 6 的实测里有这么一行：

```text
  快照：{10.0.0.6=4}（计数涨到 4，但只有前 3 次被放行——超限请求也会被计入）
```

这是一个容易忽略的实现细节：`merge` 在判断之前执行，所以**被拒绝的请求也计入了计数**。真实实现里这通常无害（窗口很快会滚过去），但如果你的限流是「超限后惩罚性延长窗口」，这一点就必须显式处理。

**为什么限流必须在缓存之前？**

Demo 6 实测：第 2、3 次是缓存命中（`attempts=0`），但**仍然占用了配额**，第 4 次照样 429。

如果缓存排在限流前面，攻击者只要反复请求同一个热门路径就能无限打穿网关——而且每次都很快（命中缓存）。**「便宜的操作」恰恰不能当作「免检通道」。**

#### `RouterFilter`：一个类同时用两个模式

```java
public GatewayResponse handle(CallContext context, FilterChain.Cursor next) {
    RouteTable.Key key = routeTable.resolve(context.request().path());
    if (key == null) {
        context.trace("router: 无匹配路由 → 短路 404");
        return GatewayResponse.reject(404, "没有匹配的路由：" + context.request().path());
    }

    // 享元池：同一个 key 拿到的永远是同一个 RouteConfig 对象
    RouteConfig route = routeTable.config(key);
    context.route(route);
    context.balancer(balancer);

    UpstreamInstance instance = balancer.pick(route.instances());
    context.instance(instance);
    context.trace("router: " + route.routeKey() + " → " + instance.name()
            + "（" + balancer.name() + "）");
    return next.proceed();
}
```

这 15 行是整篇「组合」二字最集中的地方，但注意它的写法：

```java
 * <p>它把三个模式串在了一起，但<b>没有把任何一个模式的代码搬到一起</b>：
 * 查路由配置 → RouteTable（享元工厂）；选实例 → LoadBalancer（策略）；
 * 结果写入上下文 → 后面的过滤器与装饰器都能读到。
 *
 * <p><b>这就是组合模式的正确形态：模式之间通过对象协作，而不是通过代码合并。</b>
 * 如果路由过滤器里出现 {@code new RoundRobinBalancer()}，
 * 享元与策略就都被焊死在这里了，单元测试也没法替换。
```

**判断标准：这个类里有没有出现具体实现的类名？**

`RouterFilter` 只有 `RouteTable`、`LoadBalancer`、`RouteConfig`、`UpstreamInstance` 四个类型，全部是抽象或数据。没有 `new RoundRobin()`，没有 `new HashMap<>()` 当池。所以它能被单测——传一个假的 `LoadBalancer` 就能验证「路由解析正确但选路失败」这类场景。

### 5.6 装饰器：`UpstreamHandler` / `ServiceHandler` / `RetryHandler`

#### 为什么「调用上游」要独立成一个接口

```java
public interface UpstreamHandler {
    GatewayResponse handle(CallContext context);
    default String describe() { return getClass().getSimpleName(); }
}
```

```java
 * <p><b>为什么这一环不放进过滤器链？</b>
 * 因为过滤器链处理的是「请求要不要进入系统」（认证、限流、缓存），
 * 而这一环处理的是「进入之后怎么调上游」（重试、熔断、指标）。
 * 两类关注点混在一条链上，链会变得既长又难排顺序。
```

这个划分的好处在一行代码里就能看到——`FilterChain.describe()` 的输出：

```text
auth → rate → router → cache → Metrics(Retry(CircuitBreaker(Service)))
```

前半段是平的（链），后半段是嵌套的（栈）。**一眼就能看出哪部分是「闸门」，哪部分是「洋葱」。** 如果合成一条链，输出会变成 `auth → rate → router → cache → metrics → retry → breaker → service`——顺序看起来一样，但**「谁包着谁」的信息彻底丢失了**，而这正是最关键的语义（§3.3）。

#### `ServiceHandler`：唯一真干活的一层

```java
@Override
public GatewayResponse handle(CallContext context) {
    UpstreamInstance instance = context.instance();
    if (instance == null) {
        throw new IllegalStateException("调用上游前必须先选定实例");
    }
    context.markAttempt();
    instance.acquire();
    try {
        int seq = callSeq.merge(instance.name(), 1, Integer::sum);
        int remaining = remainingFailures.getOrDefault(instance.name(), 0);
        if (remaining > 0) {
            remainingFailures.put(instance.name(), remaining - 1);
            context.trace("upstream: " + instance.name() + " 第 " + seq + " 次调用 → 503");
            return GatewayResponse.upstreamError(instance.name(),
                    "上游过载（第 " + seq + " 次调用，还会失败 " + (remaining - 1) + " 次）");
        }
        context.trace("upstream: " + instance.name() + " 第 " + seq + " 次调用 → 200");
        return GatewayResponse.ok("data#" + seq, instance.name());
    } finally {
        instance.release();
    }
}
```

三个关键点：

**（1）`try/finally` 是硬要求，不是风格。**

```java
 * <p>{@code try/finally} 是硬要求：{@link UpstreamInstance#acquire()}
 * 与 {@link UpstreamInstance#release()} 必须成对，否则「最少连接」策略会永远绕开出过错的实例。
```

漏掉一次 `release()`，这个实例的 `inFlight` 就永远偏高，最少连接会一直绕开它——而且**没有任何报错**，只是流量悄悄地全跑到另一台上。这类 bug 的排查成本极高，因为它表现为「性能问题」而不是「错误」。

**（2）故障是脚本化的，不是随机的。**

```java
 * <p>故障是<b>脚本化</b>的（按实例名预先写好「前 N 次失败」），不是随机的。
 * 随机数会让输出无法复现，而本项目的所有输出都要求两次运行逐字节一致。
```

`Map.of("report-1", 3)` 表示「report-1 的前 3 次调用返回 503」。这让 Demo 8 的五步状态切换可以被精确编排。

**（3）响应体里带着 `seq`，是取证需要。**

`data#1`、`data#2`……这个序号让「同一份缓存被谁拿到」变得可验证——Demo 3 里 `u-1001` 拿到 `data#1`、`u-1002` 拿到 `data#2`，一眼就能看出没有串数据。如果响应体是固定的 `"ok"`，就证明不了。

#### `RetryHandler`：本模块唯一一个真跑崩过的地方

这个类第一版写错了，而且**跑出了 OOM**。完整复盘在 §7.1，这里只讲最终形态和两个设计决定。

```java
@Override
public GatewayResponse handle(CallContext context) {
    GatewayResponse response = delegate.handle(context);
    int tried = 1;
    while (response.upstreamFailure() && tried < maxAttempts) {
        UpstreamInstance from = context.instance();
        UpstreamInstance to = context.repickInstance();
        tried++;
        context.trace("retry: 第 " + tried + " 次尝试，" + from + " → " + to);
        response = delegate.handle(context);
    }
    if (!response.success() && maxAttempts > 1) {
        context.trace("retry: 本层已尝试 " + tried + " 次，不再重试");
    }
    return response;
}
```

**决定一：循环变量用 `tried`，不用 `context.attempts()`。**

```java
 * <p><b>为什么循环条件用本地变量 {@code tried}，而不是 {@code context.attempts()}？</b>
 * 这是本篇踩过的真实坑：{@code context.attempts()} 统计的是
 * 「真实打到上游的次数」，而熔断器拦下的尝试<b>不会增加它</b>。
 * 两者一旦不等，`尝试次数 < 上限` 这个条件就会永远成立。
```

**决定二：判定条件用 `upstreamFailure()`，不用 `retryable()`。**

>`retryable()` 回答「这个状态码值得重试吗」，`upstreamFailure()` 回答「这是上游的问题吗」。**只有后者才该触发重试。**

两个决定的共同点：**重试层的循环条件必须由重试层自己维护，且必须能区分「谁拒绝了这次请求」**。

#### 三个 handler 的职责边界

| 类 | 数什么 | 不数什么 | 放错位置会怎样 |
|---|---|---|---|
| `MetricsHandler` | 「逻辑调用」次数（一次用户请求 = 1） | 物理重试次数 | 放里面 → QPS 虚高 2 倍 |
| `RetryHandler` | 本层尝试了几次 | 上游真实调用次数 | 用错计数器 → 死循环（§7.1） |
| `CircuitBreakerHandler` | 每个实例的连续失败数 | 全局失败数 | 按服务熔断 → 局部故障放大成全局 |

**这张表是本篇最实用的一页。** 三个类都在「数数」，但数的是三个完全不同的东西。混用任何一个，都会得到一个自洽但错误的监控系统。

### 5.7 `MetricsHandler`：位置决定视界

```java
public GatewayResponse handle(CallContext context) {
    calls++;
    return delegate.handle(context);
}
```

只有两行，但它必须是**最外层**：

```java
 * <p><b>为什么它必须在最外层？</b>
 * 它统计的是「一次逻辑调用」，而不是「一次物理重试」。
 * 如果放在 {@link RetryHandler} 里面，3 次重试会被记成 3 次调用，
 * 于是「QPS 300、错误率 0%」这种自相矛盾的监控数据就出来了。
```

Demo 10 的实测给出了对账关系：

```text
  已处理请求          13
  进入上游层          5 次（缓存命中 / 401 / 429 都不会进来）
```

13 个请求里只有 5 个真的进入了装饰器栈，差额是：

| 去向 | 次数 | 为什么不进上游层 |
|---|---|---|
| 缓存命中 | 4 | `CacheFilter` 直接返回 |
| 401 | 3 | `AuthFilter` 短路 |
| 429 | 1 | `RateLimitFilter` 短路 |
| **合计差额** | **8** | 13 − 8 = 5 ✅ |

**这个对账关系如果不能成立，你的监控就是错的。** 常见错误有两种：

- 把 `MetricsHandler` 放在 `Retry` 里面 → 5 会变成 7（两次重试各算一次），QPS 虚高；
- 把 `MetricsHandler` 做成过滤器放在 `cache` 后面 → 5 会变成 -3（缓存命中的请求根本不经过它，但你的「总请求」来自别处），数据对不上。

> **同一个「计数」，放在不同位置，数的是不同的东西。**
> 这就是为什么 §1.3 说「轨迹比响应更重要」——只有轨迹能还原「这 5 次是怎么来的」。

关于 `int` vs `LongAdder`：

```java
 * <p><b>为什么不用 {@link java.util.concurrent.atomic.LongAdder}？</b>
 * 本 Demo 是单线程的，用不到。真实网关里应该用 {@code LongAdder}
 * （高并发下比 {@code AtomicLong} 快，代价是读取时只能拿到近似值）。
```

这里用 `int` 甚至连 `AtomicInteger` 都不是——**因为 Demo 是单线程的，加了原子类反而会误导读者以为它是线程安全的整套设计**。真实实现要么改成 `LongAdder`，要么整个网关改成无状态 + 外部存储。

### 5.8 观察者：一个接口，两个关注点完全不同的订阅者

#### `GatewayListener`：事件是不可变的

```java
public interface GatewayListener {
    void onEvent(Event event);

    record Event(String requestId, String path, int status, String servedBy,
                 String instance, boolean cacheHit, int attempts) {
        public boolean success() { return status >= 200 && status < 300; }
        public String describe() { return ...; }
    }
}
```

```java
 * <p><b>为什么事件对象是不可变的 {@code record}？</b>
 * 因为它会被同时投递给多个订阅者。只要有一个订阅者能改它，
 * 后面的人看到的就是被改过的数据——<b>广播的东西必须只读</b>。
```

接口里写死了一条**约定**（不是强制）：

```java
 * <p><b>约定：实现必须是「快且不抛异常」的。</b>
 * 观察者是串行调用的，一个慢订阅者会拖慢所有请求；
 * 一个抛异常的订阅者会毁掉整次调用。
```

这条约定在 Demo 里没有被强制（`Gateway.handle` 直接 `for` 循环），是**已知瑕疵**（§8.3）。真实实现有三种选择：

| 方案 | 优点 | 代价 |
|---|---|---|
| 同步 + try/catch 吞异常 | 简单，顺序确定 | 慢订阅者拖慢请求 |
| 异步（丢队列） | 不阻塞请求 | 需要队列 + 背压 + 丢失语义 |
| 异步 + 批量上报 | 吞吐最高 | 数据延迟，排查时对不上账 |

#### `MetricsCollector`：全量分布

```java
private final Map<Integer, Integer> statusCounts = new TreeMap<>();
private final Map<String, Integer> servedByCounts = new TreeMap<>();

public void onEvent(Event event) {
    total++;
    statusCounts.merge(event.status(), 1, Integer::sum);
    servedByCounts.merge(event.servedBy(), 1, Integer::sum);
    if (event.success()) success++; else failed++;
    if (event.cacheHit()) cacheHits++;
}
```

**为什么用 `TreeMap` 而不是 `HashMap`？**

```java
 * <p><b>为什么用 {@link TreeMap}？</b>
 * 为了让输出顺序稳定。<b>顺序不稳定，输出就不可复现</b>，
 * 而本项目的所有文档都声明「两次运行逐字节一致」。
```

这不是洁癖。`HashMap` 的迭代顺序在同一 JVM、同一组 key 下通常是稳定的，但它**不保证**，且会随扩容阈值变化。一旦顺序变了，文档里的输出就对不上，读者会以为代码改了。

Demo 9 的实测：

```text
    状态码分布 {200=1, 503=4}
    谁产生的响应 {gateway=1, order-1=2, report-1=2}
```

两个 map 都是升序，任何人重跑都能得到同样的两行。

**另一个细节：`statusCounts()` 返回的是拷贝。**

```java
public Map<Integer, Integer> statusCounts() {
    return Collections.unmodifiableMap(new TreeMap<>(statusCounts));
}
```

如果直接返回内部的 map，调用方拿到的是一个**活的引用**——他读到一半，另一个线程 `onEvent` 改了 map，就会 `ConcurrentModificationException`。返回快照是观察者模式的标准做法：**订阅者的内部状态不应该被外部观察**。

#### `AlertListener`：异常连续性

```java
public void onEvent(Event event) {
    if (event.success()) {
        failureStreak = 0;
        return;
    }
    failureStreak++;
    maxStreak = Math.max(maxStreak, failureStreak);
    if (failureStreak >= streakThreshold) {
        alerts.add("[ALERT] 连续 " + failureStreak + " 次失败，最近一次："
                + event.requestId() + " " + event.path() + " → " + event.status()
                + "（" + event.servedBy() + "）");
        // 触发后归零，避免同一个故障刷屏——这叫"告警抑制"，线上必须做
        failureStreak = 0;
    }
}
```

三个设计点：

**（1）为什么数「连续」而不是「总数」。** QPS 从 10 涨到 10000，失败数当然从 1 涨到 1000，但错误率可能一点没变。**连续失败才说明「现在出问题了」**——这和熔断器数连续失败是同一个统计口径。

**（2）为什么触发后归零。** 否则一个持续 10 分钟的故障会产生上千条告警，把值班的人淹没。这叫「告警抑制」，线上必须做。

**（3）为什么告警不直接 `println`。**

```java
 * <p><b>为什么告警不直接 {@code println}？</b>
 * 因为输出顺序会与客户端打印交错，破坏可读性。
 * 这里把告警<b>收集成列表</b>，由 {@link Client} 在合适的位置统一展示——
 * 「副作用收敛到调用方」也是可测试性的一部分。
```

如果 `AlertListener` 直接打印，它会在请求处理过程中插话，把 Demo 的输出切碎。收集成列表之后，`Client` 可以在段落末尾统一打印，**而且它变成了一个可以直接断言的纯数据结构**（`alerts.size() == 2`）。

**这个决定和「日志要不要在领域代码里直接打」是同一个问题。** 把副作用收敛到边界，中间层就变成了可测试的纯函数。

Demo 9 的实测：

```text
  告警订阅者看到的是「连续性」（阈值 2 次，触发后归零以免刷屏）：
    [ALERT] 连续 2 次失败，最近一次：o-2 /api/report → 503（report-1）
    [ALERT] 连续 2 次失败，最近一次：o-4 /api/order → 503（order-1）
    共 2 条告警，历史最长连续失败 2 次
```

注意第一条告警的 `连续 2 次`：事件序列是 `o-1 失败、o-2 失败` → 第 2 次触发。如果阈值是 3，那么 `o-1~o-3` 会触发一次，`o-4` 会重新计数到 1——**这就是「抑制」带来的分段效果**。

### 5.9 享元：`RouteTable` 的两层结构

```java
public final class RouteTable {
    private record Blueprint(String pathPrefix, List<String> instanceNames) { }

    private final Map<Key, Blueprint> registry = new LinkedHashMap<>();      // 注册中心数据
    private final ConcurrentMap<Key, RouteConfig> pool = new ConcurrentHashMap<>();  // 享元池
    private final AtomicInteger hitCount = new AtomicInteger();
    private final AtomicInteger creationCount = new AtomicInteger();
}
```

**为什么是两层，而不是一层？**

因为这两层的数据来源和生命周期不同：

| 层 | 内容 | 来源 | 生命周期 |
|---|---|---|---|
| `registry` | 路径前缀 → 实例名列表 | 配置中心 / 启动参数 | 随进程 |
| `pool` | `(service, version)` → 共享的 `RouteConfig` | 第一次访问时构建 | 随进程，惰性 |

`registry` 是「原始数据」，`pool` 是「构建产物」。合并成一层也能跑，但会失去一个能力：**注册中心数据变了，可以只清空 `pool` 而不动 `registry`**（真实的配置热更新就是这么做的）。

#### `computeIfAbsent` 的计数为什么是准的

```java
public RouteConfig config(Key key) {
    RouteConfig cached = pool.get(key);
    if (cached != null) {
        hitCount.incrementAndGet();
        return cached;
    }
    // 只有真正发生创建时才会执行 mapping function，计数因此是准确的
    return pool.computeIfAbsent(key, k -> {
        creationCount.incrementAndGet();
        return build(k);
    });
}
```

**为什么先 `get` 再 `computeIfAbsent`，而不是直接 `computeIfAbsent`？**

因为要区分「命中」和「创建」两件事。如果只写：

```java
return pool.computeIfAbsent(key, k -> { creationCount.incrementAndGet(); return build(k); });
```

计数仍然准确（mapping function 只在缺失时执行），但**命中次数就统计不到了**。

那为什么不写成 `computeIfAbsent` + `else` 分支？因为 `computeIfAbsent` 无法告诉调用方「这次是新建的还是命中的」。所以只能先 `get` 一次。

**代价**：多一次 `get`（在 `ConcurrentHashMap` 里是一次 volatile 读，成本极低）。**收益**：命中率可统计。

Demo 10 的实测：

```text
  享元池              池内 3 条路由，命中 6 次，创建 3 次，命中率 66.7%
```

6 + 3 = 9 次 `config()` 调用，对应 13 个请求减去 3 个 401 减 1 个 429 = 9。✅ 对得上。

#### 为什么池用 `ConcurrentHashMap` 而不是 `HashMap`

```java
 * <p><b>为什么池用 {@link ConcurrentHashMap} 而不是 {@link java.util.HashMap}？</b>
 * 本项目在享元模块实测过：{@code HashMap + computeIfAbsent} 在 16 线程 × 3000 轮压力下
 * 有 <b>5/3000 轮</b>出现同一 key 被创建出多个实例。单线程 Demo 看不出来，
 * 但网关是多线程的，所以这里直接用并发容器。
```

**注意这个数字的来源**：它来自享元模块的探针，不是本模块的实测。本模块是单线程的，**没有复现这个并发问题**（§8.3 会标注）。

但它依然值得写在这里，因为网关的真实部署形态是多线程的。**在单线程 Demo 里用 `HashMap` 能跑通，恰恰是最危险的**——它会让读者以为这样写没问题。

#### `describePool()` 为什么不打印 key

```java
public String describePool() {
    return String.format(Locale.ROOT,
            "池内 %d 条路由，命中 %d 次，创建 %d 次，命中率 %.1f%%",
            poolSize(), hitCount.get(), creationCount.get(), hitRate());
}
```

因为 `ConcurrentHashMap` 的迭代顺序不保证。打印 key 列表会让输出不可复现（虽然对本例的 3 个 key 来说通常是稳定的）。**统计信息只需要数字，不需要顺序。**

`Locale.ROOT` 也是为可复现：默认 locale 在德语环境会把 `66.7%` 格式化成 `66,7%`。

### 5.10 `Gateway`：组装与广播

```java
public Gateway(LoadBalancer balancer,
               Map<String, Integer> scriptedFailures,
               int maxAttempts,
               int breakerThreshold,
               long coolDownTicks,
               int rateCapacity) {
    ...
    // 装饰器栈由内向外组装：Service 是最内层，Metrics 是最外层。
    this.serviceHandler = new ServiceHandler(scriptedFailures == null ? Map.of() : scriptedFailures);
    this.breakerHandler = new CircuitBreakerHandler(serviceHandler, breakerThreshold, coolDownTicks);
    this.retryHandler   = new RetryHandler(breakerHandler, maxAttempts);
    this.metricsHandler = new MetricsHandler(retryHandler);

    List<GatewayFilter> filters = List.of(
            new AuthFilter(),
            rateLimitFilter,
            new RouterFilter(routeTable, balancer),
            cacheFilter);
    this.filterChain = new FilterChain(filters, metricsHandler);
}
```

**这个构造函数是整篇文档的「结果」。** 六行 `new`，顺序就是设计：

- 装饰器从内向外包，最后一行 `new MetricsHandler(retryHandler)` 决定了最外层是谁；
- 过滤器从外向内排，`auth` 第一、`cache` 最后。

**为什么参数要显式传，而不是在内部 `new` 具体实现？**

```java
 * <p>构造一个网关。所有参数都显式传入，<b>没有任何一个模式在内部被 {@code new}</b> 出来——
 * 这正是「组合」与「堆砌」的区别：组合的对象可以单独测试、单独替换。
```

对比：如果构造函数内部写 `new LeastConnections()`，那么 Demo 7 的「轮询 vs 最少连接」对比就做不了——你无法造出两个只在策略上不同的网关。

#### `handle()` 为什么没有 try/catch

```java
public Result handle(GatewayRequest request) {
    FilterChain.Cursor cursor = filterChain.newCall(request);
    GatewayResponse response = cursor.proceed();
    CallContext context = cursor.context();
    ...
    return new Result(response, context, event);
}
```

```java
 * <p><b>注意这里没有 try/catch。</b>网关不该吞掉异常：
 * 过滤器或装饰器抛出的运行时异常代表<b>网关自身的缺陷</b>，
 * 让它带着完整堆栈冒到最外层（真实系统由统一的异常兜底层转成 500），
 * 比在这里静默返回一个 500 更容易定位问题。
```

**这条规则值得展开：** 网关里有两种异常，必须区别对待：

| 异常类型 | 例子 | 该谁处理 |
|---|---|---|
| **上游的业务失败** | 上游返回 503 | 不是异常，是 `GatewayResponse`（本模块的做法） |
| **网关自身的缺陷** | `NullPointerException`、`IllegalStateException` | 冒到最外层，带堆栈，500 |

如果为了「不让用户看到 500」而在网关里 `catch (Exception e) { return 500; }`，你只是把「一个能定位的崩溃」换成了「一坨看不出原因的 500」。**这是负优化。**

#### `Result` 为什么带三样东西

```java
public record Result(GatewayResponse response, CallContext context, GatewayListener.Event event) { }
```

- `response`：结论；
- `context`：过程（轨迹、命中实例、真实尝试次数）；
- `event`：广播出去的那份事实。

**第三个看起来冗余**——它不就是 `response` + `context` 的组合吗？是的，但它是**已经脱敏、已经定型**的那一份。`Client` 打印事件和「订阅者看到什么」用的是同一个对象，这保证了「文档里写的」和「订阅者收到的」不会有偏差。

### 5.11 删掉每个类会怎样（24 个文件）

| # | 文件 | 职责 | 删掉会缺什么 |
|---|---|---|---|
| 1 | `Gateway.java` | 组装过滤器链 + 装饰器栈，广播事件 | 没有任何东西把它们连起来 |
| 2 | `Client.java` | 十段 Demo 与取证 | 无法验证，文档里的数字全部作废 |
| 3 | `GatewayRequest.java` | 不可变请求 | 参数满天飞，校验散落各处 |
| 4 | `GatewayResponse.java` | 不可变响应 + `servedBy` | **无法区分「网关拒的」和「上游吐的」** → 重试死循环 |
| 5 | `CallContext.java` | 跨层传递的可变上下文 | 每层各自定义 DTO，层层拆包 |
| 6 | `UpstreamInstance.java` | 实例 + 在途计数 | 最少连接策略无从实现 |
| 7 | `RouteConfig.java` | 共享的路由配置（享元本体） | 每请求一份配置，`inFlight` 分散失效 |
| 8 | `RouteTable.java` | 享元工厂 + 路径解析 | 配置构造逻辑散落，无命中率可观测 |
| 9 | `LoadBalancer.java` | 策略接口 + 两个实现 | 换算法要改 `RouterFilter` |
| 10 | `UpstreamHandler.java` | 上游处理接口 | 无法层层包裹 |
| 11 | `ServiceHandler.java` | 唯一真干活的一层 + 故障注入 | 没有可复现的失败场景 |
| 12 | `RetryHandler.java` | 换实例重试 | 一次抖动直接暴露给用户 |
| 13 | `MetricsHandler.java` | 逻辑调用计数 | 无法对账「13 个请求 / 5 次上游」 |
| 14 | `CircuitBreaker.java` | 三态熔断器（状态模式核心） | 上游挂了每个请求都打满 |
| 15 | `CircuitBreakerHandler.java` | 按实例管理的熔断装饰器 | 熔断粒度错（按服务），局部故障放大 |
| 16 | `GatewayFilter.java` | 过滤器抽象 | 责任链没有统一的环 |
| 17 | `FilterChain.java` | 链 + 每请求游标 | 手写 `if/else` 串联，并发不安全 |
| 18 | `AuthFilter.java` | 认证 + 三个 401 分支 | 无法区分三种认证失败 |
| 19 | `RateLimitFilter.java` | 固定窗口限流 | 无保护，可被打穿 |
| 20 | `RouterFilter.java` | 路径 → 路由 → 实例 | 过滤器和装饰器之间没有桥 |
| 21 | `CacheFilter.java` | key 含主体、只缓存 GET 成功 | 每次都打上游；或出现缓存越权 |
| 22 | `GatewayListener.java` | 观察者接口 + 不可变事件 | 网关要认识所有下游系统 |
| 23 | `MetricsCollector.java` | 全量分布 | 没有可观测数据 |
| 24 | `AlertListener.java` | 连续失败告警 | 出问题没人知道（除了用户投诉） |

24 行里有 4 行是我最想强调的：**#4、#7、#14、#17**。它们的共同点是——**删掉之后程序大概率还能跑，但会以一种难以发现的方式错掉。**

- 删 #4 → 重试无法区分「网关拒绝」，死循环（真实发生过，§7.1）；
- 删 #7 → 最少连接读到全 0，策略静默失效；
- 删 #14 → 上游挂的时候流量打满，把「上游过载」变成「上游雪崩」；
- 删 #17 → 单线程测试全绿，上线后偶发跳过认证。

> **「删了会报错」的类只是工具；「删了不报错但会错」的类才是设计。**

---

## 6. 执行流程：十段 Demo 的真实输出

### 6.0 怎么自己跑一遍

```powershell
$JDK = 'F:\enCodingApps\JDK\jdk-25'
$src = 'E:\codeFile\codeFather\design_patterns_pro\src'
$out = 'E:\codeFile\codeFather\design_patterns_pro\out'

# ① 编译（零警告是验收标准之一）
& "$JDK\bin\javac.exe" --release 25 -encoding UTF-8 -Xlint:all -d $out `
    (Get-ChildItem -Recurse "$src\com\patterns\combined\apigateway\*.java").FullName

# ② 运行
& "$JDK\bin\java.exe" '-Dstdout.encoding=UTF-8' -cp $out com.patterns.combined.apigateway.Client
```

三条对账标准，缺一条都不算复现：

| # | 标准 | 实测值 |
|---|---|---|
| 1 | `javac` 退出码 0，且 `-Xlint:all` 下没有一行 warning | 0 / 0 行 |
| 2 | `java` 退出码 0，且 stderr 为 0 字节 | 0 / 0 字节 |
| 3 | stdout 为 304 行 / 16085 字节，SHA-256 固定 | `E4888CC8…9B800E6` |

**为什么必须加 `-Dstdout.encoding=UTF-8`？** Windows 控制台默认代码页是 GBK（cp936），不加这个参数时中文会被转码，字节数就变了，第 3 条标准永远对不上。这也是本项目所有「输出字节数」都注明「`-Dstdout.encoding=UTF-8` 口径」的原因。

**如果你只想看某一段**：十段 Demo 是十个独立的 `private static void demoN()`，互不依赖（Demo 2 复用 Demo 1 的网关实例除外），可以自行注释掉其余九段。但那样做**会破坏 SHA**，请只把它当阅读工具，不要当验证结果。

---

### 6.1 Demo 1：一条请求的完整解剖

```text
================================================================================================
Demo 1 正常请求：一条请求把六个模式全走一遍
================================================================================================
  完整拓扑（过滤器链 + 装饰器栈）：
    auth → rate → router → cache → Metrics(Retry(CircuitBreaker(Service)))

  请求：GET /api/order  (r-1, 10.0.0.1)
  响应：200  data#1  <- order-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.0.0.1 第 1/3 次
    3. router: order:v1 → order-1（least-connections）
    4. cache: 未命中 [u-1001 | /api/order] → 放行
    5. upstream: order-1 第 1 次调用 → 200
    6. cache: 回填 [u-1001 | /api/order]
  熔断器：order-1=CLOSED

  六个模式的分工：
    责任链 — auth / rate / router / cache 依次放行
    策略   — router 里用 least-connections 选中 order-1
    装饰器 — Metrics(Retry(CircuitBreaker(Service))) 逐层包裹
    状态   — 熔断器此刻处于 CLOSED，放行并开始数连续失败
    享元   — order:v1 的路由配置在池里只创建一次，被所有请求共享
    观察者 — 请求结束后把 Event 广播给指标与告警两个订阅者
```

这 27 行里有四处值得停下来看。

**第一处：轨迹的 6 行是谁写的。**

不是 `Client` 拼的。每一行都由**真正执行到的那一层自己**写：

| 行 | 写入者 | 代码位置 |
|---|---|---|
| 1 | `AuthFilter` | `context.trace("auth: 通过 " + principal)` |
| 2 | `RateLimitFilter` | `context.trace("rate: " + ip + " 第 " + n + "/" + cap + " 次")` |
| 3 | `RouterFilter` | `context.trace("router: " + route.key() + " → " + instance.name() + "（" + balancer.name() + "）")` |
| 4、6 | `CacheFilter`（**同一层，两行**） | 调用前 `未命中…放行`，调用后 `回填…` |
| 5 | `ServiceHandler` | `context.trace("upstream: " + name + " 第 " + n + " 次调用 → " + status)` |

`CallContext.trace(String)` 只做一件事：`traces.add(line)`。所以**轨迹条数 = 实际经过的层数**，这不是统计出来的，是被动记录出来的。任何一层如果提前 `return`（短路），它后面的行就永远不会出现——这就是 Demo 2 只有 4 行的原因。

**第二处：第 3 行把三个信息压进了一行。**

```text
3. router: order:v1 → order-1（least-connections）
```

- `order:v1` 是**路由配置的身份**（享元的 key）；
- `order-1` 是**策略选出来的实例**；
- `least-connections` 是**策略的名字**。

线上排障时这一行信息量极大：如果你以为自己在用轮询，日志里却写着 `least-connections`，那说明有人换了装配，而不是算法出错。

**第三处：第 5 行的「第 1 次调用」不是「第 1 次尝试」。**

`ServiceHandler` 里的计数器是**按实例的全局累计**（`callCount.merge(name, 1, Integer::sum)`），它回答的是「这台机器今天被打了多少次」。而「本次请求尝试了几次」是 `CallContext.attempts`，打印在「真实打到上游：N 次」那一行。

两个数字在 Demo 7 里会明显分叉：

```text
5. upstream: order-2 第 1 次调用 → 503
6. retry: 第 2 次尝试，order-2 → order-1
7. upstream: order-1 第 2 次调用 → 200
```

`order-1 第 2 次调用` 指的是它被全局调用的第 2 次，而这一次请求的 `attempts` 是 1。**混淆这两个计数，就会写出「重试次数按实例累计」的错误代码**——那样重试永远只有第一次。

**第四处：「熔断器：order-1=CLOSED」这一行不是轨迹。**

它由 `Client.printBreaker(...)` 在请求结束后打印，走的是 `gateway.breaker().breakerOf("order-1").stateName()`。

它存在的意义是证明一件反直觉的事：**一次成功的请求，也经过了熔断器。** 熔断器在 CLOSED 状态下会「放行 + 记一次成功（把连续失败清零）」，不改变状态，所以在响应上看不出任何痕迹。监控上「状态没变」和「根本没执行」是两件完全不同的事，这一行就是为了把后者排除掉。

**最后 6 行「六个模式的分工」是手写旁白，不是程序输出。**

它是给第一次读的人做映射用的。旁白只在 Demo 1 出现，因为从 Demo 2 开始默认你已经记住这张表——如果每段都配一遍旁白，输出会变成 600 行，SHA 也会变。

---

### 6.2 Demo 2-4：短路的三种形态

```text
================================================================================================
Demo 2 缓存命中：同样的请求，上游一次都没被打
================================================================================================
  响应：200  data#1  <- order-1
  真实打到上游：0 次（缓存命中，未打上游）
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.0.0.1 第 2/3 次
    3. router: order:v1 → order-1（least-connections）
    4. cache: 命中 [u-1001 | /api/order]

  注意到轨迹在 cache 这一层就结束了：route 已经解析出来，
  但装饰器栈（含熔断器、重试、上游）一次都没有进入，attempts 为 0。
  这就是「短路」——后面的环节根本不执行，不是「执行了但返回缓存」。

================================================================================================
Demo 3 缓存隔离：换个用户打同一路径，绝不能命中
================================================================================================
  响应：200  data#2  <- order-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1002
    2. rate: 10.0.0.3 第 1/3 次
    3. router: order:v1 → order-1（least-connections）
    4. cache: 未命中 [u-1002 | /api/order] → 放行
    5. upstream: order-1 第 2 次调用 → 200
    6. cache: 回填 [u-1002 | /api/order]

  如果缓存 key 只写 path，r-3 会拿到 r-1 的 data#1 ——
  用户 B 看到用户 A 的订单。这类漏洞在真实系统里叫「缓存越权」，
  根因几乎都是「共享了公共缓存，却忘了 key 里要区分调用方」。

================================================================================================
Demo 4 POST 不缓存：打两次，就真的下两次单
================================================================================================
  响应：200  data#1  <- pay-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.0.0.4 第 1/3 次
    3. router: pay:v1 → pay-1（least-connections）
    4. cache: POST 不缓存
    5. upstream: pay-1 第 1 次调用 → 200

  响应：200  data#2  <- pay-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.0.0.4 第 2/3 次
    3. router: pay:v1 → pay-1（least-connections）
    4. cache: POST 不缓存
    5. upstream: pay-1 第 2 次调用 → 200

  上游被调用到第 2 次（pay-1 第 1 次 / 第 2 次），说明 POST 没有被缓存短路。
  HTTP 语义里 GET 是幂等的、POST 不是；把 POST 缓存住等于
  「第二次付款没有真的付款」，这是语义级错误，不是性能问题。
```

这三段必须连起来读，因为它们回答的是同一个问题的三个侧面：**请求在哪一层停下，后果有什么不同？**

#### （1）Demo 2：短路发生在第 4 层，但它不是免费的

轨迹只有 4 行，`attempts` 为 0，`真实打到上游：0 次`。

注意第 3 行仍然在：`router: order:v1 → order-1（least-connections）`。也就是说，**即使这次请求最终被缓存接走，选路仍然发生了**。

这引出一个容易被忽略的成本结构：

| 环节 | 缓存命中时是否执行 | 成本 |
|---|---|---|
| `auth` | 执行 | 查 token 表（真实系统还要验签、可能查 Redis） |
| `rate` | 执行 | 一次计数器自增 |
| `router` | 执行 | 一次路径匹配 + 一次策略计算 |
| `cache` | 执行（就是它短路的） | 一次 key 拼装 + 一次 Map 查找 |
| 装饰器栈（Metrics/Retry/Breaker/Service） | **不执行** | 省掉一次网络往返 + 一次在途计数 |

所以「缓存命中」省掉的是最贵的那一段（跨进程调用），而不是全部。

**那能不能把 cache 提到 router 前面，把选路也省掉？**

能，但你会失去一件事：缓存 key 里不再有版本信息。当前 key 是 `u-1001 | /api/order`，看上去没有版本——但 `CacheFilter` 位于 `RouterFilter` **之后**，它拿到的 `context.route()` 是带版本的；一旦将来要支持「灰度期间 v1/v2 各缓存一份」，`context.route().key()` 就能直接拼进 key。把 cache 提前，这条路就断了。

> **每一次「提前短路」都在拿正确性换性能。** 提前到哪一层，取决于你能接受哪一层的信息缺失。

#### （2）Demo 3：短路不该发生，因为 key 里有主体

`u-1002` 第一次请求，第 4 行是 `未命中 [u-1002 | /api/order] → 放行`，返回 `data#2`。

把这段和 §2.2 的 v0 输出并排放：

```text
v0（key = path）              本模块（key = principal | path）
u-1001 → 200 data#1          u-1001 → 200 data#1
u-1002 → 200 data#1  ← 越权   u-1002 → 200 data#2  ← 正确隔离
```

两段输出之间的**唯一差别**就是 key 里有没有 `u-1002`。

这一段的取证价值在于它是**反证**：它证明「缓存能命中」（Demo 2）和「缓存不该命中」（Demo 3）是两个独立的能力。只跑了 Demo 2 就宣布「缓存没问题」的测试，恰好漏掉了唯一会造成越权的那条路径。

#### （3）Demo 4：短路被主动禁止

两次 POST，两行 `cache: POST 不缓存`，上游 `第 1 次` / `第 2 次`。

`CacheFilter` 里对应的判断是：

```java
if (!"GET".equalsIgnoreCase(request.method())) {
    context.trace("cache: " + request.method() + " 不缓存");
    return delegate.handle(context);   // 直接放行，连查都不查
}
```

**注意这里是「不查也不写」，不是「只写不查」。** 有些实现在写侧过滤（只有 GET 才 put）、读侧却用 `cache.containsKey(key)` 一把梭——那样 POST 的响应不会被写进去，但如果有别的路径（比如预热脚本）写进去了，POST 照样会命中。

如果 CacheFilter 完全不判断 method，Demo 4 的第二次会直接返回 `data#1`，用户会看到「第二次付款成功了，订单号还是上一个」。**这类 bug 在测试环境极难发现，因为测试人员通常只点一次。**

#### （4）三段合成一句话

> **短路是一把刀，用对了是性能，用错了是资损。** 决定它落在哪一层的，不是性能数字，是语义。

下一节的两个 Demo 会把这个结论推到「链上位置」这个维度：同样是短路，`401` 短路在 `auth`（第 1 层），`429` 短路在 `rate`（第 2 层），两者对系统状态的副作用完全不同。

---

### 6.3 Demo 5-6：短路点在链上的位置，就是安全边界

```text
================================================================================================
Demo 5 认证三分支：401 必须短路，且不该消耗配额
================================================================================================
  401 之前的限流快照：{}

  [1] 没有 Authorization 头
  响应：401  缺少 Authorization 头  <- gateway
  真实打到上游：0 次
  轨迹：
    1. auth: 缺少 Authorization → 短路 401

  [2] token 已过期
  响应：401  token 已过期，请刷新后重试  <- gateway
  真实打到上游：0 次
  轨迹：
    1. auth: token 已过期 → 短路 401

  [3] 未知 token
  响应：401  无法识别的 token  <- gateway
  真实打到上游：0 次
  轨迹：
    1. auth: 未知 token → 短路 401

  401 之后的限流快照：{}
  快照里没有 10.0.0.5 —— 三个 401 一次配额都没消耗，
  因为它们都在 auth 这一环就返回了，rate 根本没被执行。
  三种 401 在线上必须打不同标签：客户端的处理动作完全不同
  （补头 / 刷新 token / 疑似伪造并告警）。

================================================================================================
Demo 6 限流在缓存之前：命中缓存也算一次请求
================================================================================================
  配额：3 次 / 窗口，客户端 IP：10.0.0.6

  第 1 次 → r-9 /api/user → 200 (user-1, 1 次上游)
  第 2 次 → r-10 /api/user → 200 (user-1, 0 次上游, 缓存命中)
  第 3 次 → r-11 /api/user → 200 (user-1, 0 次上游, 缓存命中)
  第 4 次 → r-12 /api/user → 429 (gateway, 0 次上游)

  第 2、3 次是缓存命中（attempts=0），但它们仍然占用了配额。
  如果缓存排在限流前面，攻击者只要反复请求同一个热门路径就能无限打穿网关；
  而且每次都很快——「便宜的操作」恰恰不能当作「免检通道」。

  快照：{10.0.0.6=4}（计数涨到 4，但只有前 3 次被放行——超限请求也会被计入）

  --- rollWindow()：进入下一个固定窗口 ---
  第 5 次 → r-13 /api/user → 200 (user-1, 0 次上游, 缓存命中)
  新窗口的配额重新从 1 开始，缓存依然命中，所以 attempts 仍是 0。
```

这两段合起来讲的是一件在代码里看不出来、只能在输出里看出来的事：**「先短路」和「后短路」的差别，不是快慢，是对系统状态有没有副作用。**

#### （1）三条 401，三条只有 1 行的轨迹

`auth` 是链上的第一环。它 `return` 的时候，`rate / router / cache / 装饰器栈` 一次都没有被调用——轨迹里就只剩孤零零的一行。

而配额快照的前后对比 `{}` → `{}` 是这份文件里**最强的因果证据**：

> 如果 `rate` 被执行过，`used` 里必然出现 `10.0.0.5`。

它证明的不是「401 没消耗配额」这个结论，而是「`rate` 这一行代码在 401 路径上根本没有运行」这个事实。这两件事的差别在于：前者可以靠「消耗了再把计数减回去」来实现，后者不能。

#### （2）为什么三种 401 必须分开

v0 的三种 401 是同一句话，本模块是三个不同的字符串：

| 场景 | v0 响应体 | 本模块响应体 | 客户端该做什么 | 监控该怎么报 |
|---|---|---|---|---|
| 没带 Authorization 头 | `unauthorized` | `缺少 Authorization 头` | 补头（**这是客户端 bug**） | 低优先级，按 SDK 版本分桶 |
| token 已过期 | `unauthorized` | `token 已过期，请刷新后重试` | 走刷新流程再重试 | 正常业务事件，不该告警 |
| 未知 token | `unauthorized` | `无法识别的 token` | 无（**可能是伪造**） | 高优先级，突增即安全事件 |

三行字符串的差别，换来的是**三种完全不同的运维动作**。合并成一句话的代价不是「不好看」，是「撞库告警被淹没在客户端 bug 里」。

这背后是一条通用原则：

> **错误响应的粒度应该等于「调用方需要采取的补救动作」的粒度。** 一个补救动作对应一个错误码；两个动作合并成一个错误码，调用方就只能猜。

#### （3）401 不消耗配额：一个刻意的取舍

这个设计**可以被质疑**，而且质疑得有道理：

- **支持方**：未认证请求不属于「某用户的配额」。如果按 IP 记账，攻击者用错误 token 打某人的出口 IP，就能把他的配额打满——这是把限流武器化。
- **反对方**：`auth` 在 `rate` 之前，意味着**未认证流量完全不限量**。验签、查 Redis 都是 CPU 活，攻击者可以直接用它打穿 `auth`。

本 Demo 选了前者，因为场景里 `auth` 是纯内存表查找，成本低到不值得为它加一层限流。但真实网关的标准做法是**两层限流**：

```text
① IP 维度粗粒度前置限流（比如 1000 次/秒，拦 DDoS）  ← 在 auth 之前
② 用户维度细粒度后置限流（比如 3 次/窗口，保业务公平） ← 在 auth 之后
```

本模块只实现了 ②。这是一处**真实的缺口**，不是「简化」。它被列入 §8.3 的已知瑕疵清单，也列在 §7.4 的未验证项里——因为「两层限流之间的配额如何对账」这件事，本 Demo 一次都没有跑过。

#### （4）Demo 6：为什么缓存命中也要占配额

四次请求，三次 200、一次 429。第 2、3 次是缓存命中（`attempts=0`），但它们**照样把计数从 1 推到 3**。

如果调换顺序（cache 在 rate 之前）：

```text
攻击者请求 1：命中缓存 → 200，不消耗配额
攻击者请求 2：命中缓存 → 200，不消耗配额
...
攻击者请求 100000：命中缓存 → 200，不消耗配额
```

**「便宜的操作」恰恰不能当作「免检通道」**——因为攻击者永远挑最便宜的那条路打。限流的作用不是保护上游（那是熔断和缓存的事），而是**保护网关本身**，而网关的 CPU 在缓存命中路径上同样被消耗。

#### （5）`{10.0.0.6=4}`：一个看起来像 bug 的正确行为

前 3 次放行、第 4 次被拒，但快照显示 4。原因是 `RateLimitFilter` 的判断顺序：

```java
int used = this.used.merge(ip, 1, Integer::sum);   // ← 先自增
if (used > capacity) {                              // ← 后判断
    context.trace("rate: " + ip + " 第 " + used + "/" + capacity + " 次 → 超限 429");
    return GatewayResponse.reject(429, "限流：" + ip + " 在窗口内已请求 " + used + " 次");
}
```

先自增、后判断，意味着**被拒的请求也被计数**。这不是疏忽，是一个刻意的选择：

| 写法 | 计数含义 | 好处 | 坏处 |
|---|---|---|---|
| 先自增后判断（本模块） | 「尝试了多少次」 | 能看出攻击持续了多久：`=4` 说明多打了一次，`=4000` 说明被打了 4000 次 | 数字比「放行次数」大 1 或很多 |
| 先判断后自增 | 「放行了多少次」 | 数字和业务口径一致 | 攻击者打了 4000 次，计数永远停在 3，看不出攻击强度 |

选前者的理由：**限流器的价值在于「观测攻击强度」，不在于「报表好看」。**

但必须诚实说明代价：如果 `Client` 里写的是「快照里应该是 3」，它就会挂。这段注释之所以写得那么啰嗦——`（计数涨到 4，但只有前 3 次被放行——超限请求也会被计入）`——就是因为第一次运行时，我自己也被这个 4 困惑了两分钟，还一度以为是自增写重了。**把困惑写进文档，下一个人就省两分钟。**

#### （6）`rollWindow()` 之后：一次 200，attempts 仍是 0

第 5 次在新窗口里配额重新从 1 开始，缓存依然命中，所以 `attempts` 仍是 0。

这里有一个容易忽略的点：**「配额重置」和「缓存失效」是两件完全不同的事，不要绑在一起。** 有些实现会在限流窗口滚动时顺手清缓存，理由是「都是过期逻辑」——但缓存的有效期应该由数据的时效性决定（订单列表 30 秒），限流窗口由风控策略决定（3 次/分钟）。绑在一起，改一个就动另一个。

---

### 6.4 Demo 7：换一个策略，同样的输入给出不同的过程

```text
================================================================================================
Demo 7 策略对比：同样的实例和故障，换个选路算法结果就变了
================================================================================================
  故障脚本：order-2 的前 1 次调用返回 503，order-1 完全正常。

  [轮询] round-robin：第 1 台 order-1，第 2 台 order-2
  第 1 次 → rr-1 /api/order → 200 (order-1, 1 次上游)
  第 2 次 → rr-2 /api/order → 200 (order-1, 2 次上游)
  响应：200  data#2  <- order-1
  真实打到上游：2 次
  轨迹：
    1. auth: 通过 u-1002
    2. rate: 10.1.0.2 第 1/3 次
    3. router: order:v1 → order-2（round-robin）
    4. cache: 未命中 [u-1002 | /api/order] → 放行
    5. upstream: order-2 第 1 次调用 → 503
    6. retry: 第 2 次尝试，order-2 → order-1
    7. upstream: order-1 第 2 次调用 → 200
    8. cache: 回填 [u-1002 | /api/order]
  轮询网关：order-1 被调用 2 次，order-2 被调用 1 次
  → 轮询不知道机器坏没坏，它只负责平均分配；这一次是重试救了它。

  [最少连接] least-connections：同样的两个请求
  第 1 次 → lc-1 /api/order → 200 (order-1, 1 次上游)
  第 2 次 → lc-2 /api/order → 200 (order-1, 1 次上游)
  最少连接网关：order-1 被调用 2 次，order-2 被调用 0 次

  ⚠ 这里必须说实话：最少连接之所以等于「永远选第一台」，是因为本 Demo
    是单线程的，每次选路时 inFlight 都已经归零。真实网关里请求会重叠，
    第一台的 inFlight 涨到 5 时，选路会自动跳到 inFlight 为 0 的第二台。
    换句话说：这个「退化」是演示环境的产物，不是算法的缺陷；
    但如果你在线上看到「最少连接只打一台」，先怀疑的是 inFlight 没被正确释放。
```

#### （1）先看那个「矛盾」：两次 200，但 order-2 被打了一次

输出里这两行放在一起，很容易读成 bug：

```text
第 2 次 → rr-2 /api/order → 200 (order-1, 2 次上游)
...
3. router: order:v1 → order-2（round-robin）
```

`servedBy` 是 `order-1`，但 router 明明选了 `order-2`。这不是矛盾，是**重试改变了下游的最终归属**：

```text
router 选 order-2  →  upstream 503  →  retry 换 order-1  →  upstream 200
                                                        ↑
                                            这才是 servedBy = order-1 的原因
```

这也正是 §1.3 那句话的实例：**响应会骗人，轨迹不会。**

如果只看精简打印（`→ 200 (order-1, 2 次上游)`），你会得出「轮询没有生效，两次都打了 order-1」的错误结论；只有完整轨迹才看得见「第 1 次确实打到了 order-2，而且它失败了」。

这也解释了为什么 Demo 7 要特意打印「轮询网关：order-1 被调用 2 次，order-2 被调用 1 次」——`order-2` 的 1 次调用是它在这次对比里存在过的唯一证据。

#### （2）第 6 行：`pickExcluding` 的一次真实调用

```text
6. retry: 第 2 次尝试，order-2 → order-1
```

这一行由 `RetryHandler` 写：

```java
UpstreamInstance from = context.instance();
UpstreamInstance to = context.repickInstance();   // ← 内部调 balancer.pickExcluding(instances, from)
context.trace("retry: 第 " + tried + " 次尝试，" + from + " → " + to);
```

`pickExcluding` 是 `LoadBalancer` 接口的**默认方法**，不是抽象方法：

```java
default UpstreamInstance pickExcluding(List<UpstreamInstance> candidates, UpstreamInstance excluded) {
    List<UpstreamInstance> others = candidates.stream()
            .filter(i -> i != excluded)
            .toList();
    return others.isEmpty() ? excluded : pick(others);   // 只剩一台就硬着头皮再打它
}
```

为什么给默认实现而不是强制两个策略各写一遍？

- 轮询和最少连接**都**能复用这个「先过滤再调 pick」的结构，差异只在 `pick` 里；
- 但不同策略可以覆盖它（比如一致性哈希：换一台会破坏「同一 key 落同一台」的约束，就不该排除，而应该整体让位给降级逻辑）。

**接口上的默认方法，本质是「给所有实现准备的公共行为，同时保留覆盖权」。** 这是 §5.2 讲过的东西，Demo 7 的第 6 行是它在真实调用链里的样子。

#### （3）两种策略的差别，一句话说清

| | 轮询 round-robin | 最少连接 least-connections |
|---|---|---|
| 它知道什么 | 上一次给了谁 | 每台当前有多少在途请求 |
| 它不知道什么 | 机器坏没坏、忙不忙 | —— |
| 这次的后果 | 第 2 次打到故障机，靠重试兜住 | 两次都打到健康的 order-1 |
| 代价 | 一次失败的上游调用 + 一次重试 | 需要正确维护 `inFlight`，一旦泄漏就永远偏斜 |
| 适合 | 实例同构、请求耗时均匀 | 请求耗时差异大（长连接、大查询） |

一句话：**轮询用「平均」换「简单」，最少连接用「状态」换「准确」。** 状态越多的算法，出错时的排查成本越高——`inFlight` 泄漏导致的「永远选同一台」，比轮询打歪一次难查一个数量级。

#### （4）那段 ⚠ 为什么必须写在输出里

因为如果不写，这段输出会**误导人**：

```text
最少连接网关：order-1 被调用 2 次，order-2 被调用 0 次
```

在真实网关里，如果连续 2 次都选同一台，你会本能地怀疑算法。但本 Demo 使用的是单线程 + 每次请求同步结束，`inFlight` 在两次选路之间都已经归零（都是 0），`LeastConnections.pick` 在「全部相等」时按列表顺序取第一个——所以它必然选 `order-1`。

这个「退化」只在**内存里逐条跑**的场景成立。真实的 Nginx / Envoy / Spring Cloud LoadBalancer 都是并发环境，`inFlight` 会真实分叉。

但这段 ⚠ 的后半句更重要：

> 如果你在线上看到「最少连接只打一台」，先怀疑的是 `inFlight` 没被正确释放。

因为并发环境下「全部相等」的概率接近 0。如果它真的发生了，几乎一定是**某条异常路径忘了 `release()`**——比如上游抛异常时 `try/finally` 写成了 `try`，`inFlight` 只增不减，那台机器很快会涨到一个谁都比不过的数字，然后**再也没有请求会选中它**（而不是「永远选中它」）。

两种表现、同一个根因、完全相反的观感。**这就是为什么本模块的 `ServiceHandler` 用的是 `try/finally` 而不是 `try/catch`**（§5.1 有完整代码）。

#### （5）为什么这一整段只用 22 行输出，却能证明策略模式的价值

因为对比的前提被锁死了：

| 变量 | 是否相同 |
|---|---|
| 实例列表（order-1 / order-2） | 相同 |
| 故障脚本（order-2 第 1 次失败） | 相同 |
| 请求内容（GET /api/order） | 相同 |
| 重试配置（最多 2 次尝试） | 相同 |
| **唯一变化：`LoadBalancer` 实现** | **不同** |

「只变一个变量」是实验设计的基本要求，也是策略模式最好的说明书：**它把一个算法变成了一个可以替换的参数。** 如果选路逻辑硬编码在 `RouterFilter` 里（v0 的做法），你根本造不出这个对照实验——除非复制一整个 `RouterFilter`。

---

### 6.5 Demo 8：熔断器的五次状态切换，逐 tick 拆开

这一段是全篇最值得逐行读的输出。它同时演示了三件事：状态模式怎么工作、熔断和重试的分工、以及**一个真实的 OOM 事故**。

先看配置，因为配置决定了你能看到什么：

```text
配置：/api/report 只有 report-1 一台，关闭重试（maxAttempts=1），
      连续失败 2 次打开熔断，冷却 3 tick；故障脚本：report-1 前 3 次调用失败。
```

**为什么这一段要关掉重试？** 因为重试会掩盖熔断器的行为：一次请求内的 2 次尝试会直接凑满「连续失败 2 次」的阈值，`CLOSED → OPEN` 会在第一个请求里就发生，后面四个状态全被挤在一起，看不清。**演示一个机制时，要把会放大它的那层先关掉**——这和 §6.4 保持其他变量不变是同一个方法论。

```text
================================================================================================
Demo 8 熔断三态：CLOSED → OPEN → HALF_OPEN → OPEN → HALF_OPEN → CLOSED
================================================================================================
  配置：/api/report 只有 report-1 一台，关闭重试（maxAttempts=1），
        连续失败 2 次打开熔断，冷却 3 tick；故障脚本：report-1 前 3 次调用失败。

  [tick 0] c-1 第一次失败（未到阈值 2）
  响应：503  上游过载（第 1 次调用，还会失败 2 次）  <- report-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.2.0.1 第 1/3 次
    3. router: report:v1 → report-1（least-connections）
    4. cache: 未命中 [u-1001 | /api/report] → 放行
    5. upstream: report-1 第 1 次调用 → 503
    6. cache: 503 不缓存
  熔断器：report-1=CLOSED，连续失败 1，切换 0 次

  [tick 0] c-2 第二次连续失败 → 达到阈值
  响应：503  上游过载（第 2 次调用，还会失败 1 次）  <- report-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.2.0.2 第 1/3 次
    3. router: report:v1 → report-1（least-connections）
    4. cache: 未命中 [u-1001 | /api/report] → 放行
    5. upstream: report-1 第 2 次调用 → 503
    6. breaker: report-1 CLOSED → OPEN
    7. cache: 503 不缓存
  熔断器：report-1=OPEN，连续失败 2，切换 1 次

  [tick 0] c-3 熔断器打开期间的请求
  响应：503  熔断器打开（report-1），请求未打到上游  <- gateway
  真实打到上游：0 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.2.0.3 第 1/3 次
    3. router: report:v1 → report-1（least-connections）
    4. cache: 未命中 [u-1001 | /api/report] → 放行
    5. breaker: report-1 处于 OPEN → 快速失败，未调用上游
    6. cache: 503 不缓存
  ↑ 注意这两行：servedBy 是 gateway（不是 report-1），attempts 是 0。
    这个 503 是网关自己拒的，不是上游吐的——排查时必须能分清。
  熔断器：report-1=OPEN，连续失败 2，切换 1 次

  --- advance(3)：逻辑时钟推进到 tick 3，冷却结束 ---
  [tick 3] c-4 半开状态放行一次探路，但上游仍然失败
  响应：503  上游过载（第 3 次调用，还会失败 0 次）  <- report-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.2.0.4 第 1/3 次
    3. router: report:v1 → report-1（least-connections）
    4. cache: 未命中 [u-1001 | /api/report] → 放行
    5. breaker: report-1 OPEN → HALF_OPEN（冷却 3 tick 已到，放行一次探路）
    6. upstream: report-1 第 3 次调用 → 503
    7. breaker: report-1 HALF_OPEN → OPEN
    8. cache: 503 不缓存
  熔断器：report-1=OPEN，连续失败 2，切换 3 次

  --- advance(3)：逻辑时钟推进到 tick 6 ---
  [tick 6] c-5 再次半开探路，这次上游恢复
  响应：200  data#4  <- report-1
  真实打到上游：1 次
  轨迹：
    1. auth: 通过 u-1001
    2. rate: 10.2.0.5 第 1/3 次
    3. router: report:v1 → report-1（least-connections）
    4. cache: 未命中 [u-1001 | /api/report] → 放行
    5. breaker: report-1 OPEN → HALF_OPEN（冷却 3 tick 已到，放行一次探路）
    6. upstream: report-1 第 4 次调用 → 200
    7. breaker: report-1 HALF_OPEN → CLOSED
    8. cache: 回填 [u-1001 | /api/report]
  熔断器：report-1=CLOSED，连续失败 0，切换 5 次

  上游真实被调用 4 次，而网关收到了 5 次请求：
  第 3 次被熔断器挡在门外，一次上游都没打——这就是熔断和重试的分工：
  重试是「再试一次」，熔断是「别再试了，先让下游喘口气」。
```

#### （1）c-1：轨迹的第 6 行证明了一件容易忽略的事

```text
5. upstream: report-1 第 1 次调用 → 503
6. cache: 503 不缓存
```

`CacheFilter` 的代码结构是「调用前查、调用后写」：

```java
GatewayResponse response = delegate.handle(context);   // ← 第 5 行走在这里面
if (response.success()) { cache.put(cacheKey, response); context.trace("cache: 回填 …"); }
else { context.trace("cache: " + response.status() + " 不缓存"); }
```

所以第 6 行能出现，说明**响应确实回到了最外层**。这一点在调试时非常有用：如果某次请求的轨迹停在 `upstream → 503` 而没有第 6 行，说明**某一层在返回路径上抛了异常**（装饰器只写了 `return delegate.handle(...)`，没写 `try/finally`）。

这也是「响应返回路径」这个概念的具体化：装饰器的 `handle` 有两个执行点——**调用前**和**调用后**。责任链的过滤器也有两个（`cursor.proceed()` 前后），但责任链更常见的用法是只用前一个点（判断能不能进）。

#### （2）c-2：`breaker` 那一行的位置很关键

```text
5. upstream: report-1 第 2 次调用 → 503
6. breaker: report-1 CLOSED → OPEN
7. cache: 503 不缓存
```

第 6 行在 5 和 7 之间，这是因为 `CircuitBreakerHandler` 位于 `CacheFilter` **内层**、`ServiceHandler` **外层**：

```text
cache（第 4 层，最外）
  └─ Metrics → Retry → CircuitBreaker → Service（最内）
```

内层先看到响应、先记录结果，外层后看到。**轨迹的行序 = 装饰器的包裹顺序**，这是读轨迹最实用的技巧：看到 `breaker` 在 `cache` 前面，就知道熔断器被缓存包在里面，缓存命中时熔断器根本不会执行（Demo 2 就是这个情况）。

#### （3）c-3：这一行差点让整个 Demo 跑不起来

```text
  响应：503  熔断器打开（report-1），请求未打到上游  <- gateway
  真实打到上游：0 次
```

`servedBy = gateway` 和 `attempts = 0` 是这段输出最要紧的两个值：

- `gateway` 说明这个 503 是**网关自己产生的**，上游一次都没被打；
- `attempts = 0` 说明连「一次尝试」都没有发生。

**本模块第一次运行时，就是在这三个请求（c-3）上 `OutOfMemoryError` 崩掉的。** 原因是 `RetryHandler` 当时用 `context.attempts()` 当循环条件：

```java
// ✗ 出事的写法（已修复）
while (response.retryable() && context.attempts() < maxAttempts) { ... }
```

而被熔断器拦下的请求**不会增加 `attempts`**（因为它没打到上游），于是 `0 < 2` 永远成立 → 无限重试 → 每次重试追加一行 trace → 堆爆。

修复方案分两处（`GatewayResponse.upstreamFailure()` + `RetryHandler` 的本地 `tried` 计数），完整复盘在 **§7.1**。这里先记住一件事：

> **「重试次数」和「上游被调用次数」不是同一个量。** 把前者当后者用，在「有一层会拒绝请求」的架构里必然死循环。

#### （4）`advance(3)`：为什么用 tick 而不是真的等 3 秒

熔断器的冷却时间在真实系统里是 `System.nanoTime()` 或 `System.currentTimeMillis()`。本模块抽出了一个 `AtomicLong clock`，测试时用 `breaker.advance(3)` 手动推进。

理由三条：

| 真等待 | 逻辑时钟 |
|---|---|
| 跑一次 Demo 要多花 6 秒 | 0 毫秒 |
| 机器负载高时可能「还没冷却完」 | 完全确定 |
| SHA 每次都不一样（时间戳进不了输出也得进内存） | 逐字节可复现 |

代价也诚实说：**逻辑时钟和真实时间脱钩**，所以本模块无法证明「在真实并发下冷却窗口的边界是否准确」（比如「tick 2.999 时到达的请求算不算期满」）。这条列入 §7.4 未验证项。

#### （5）c-4：两行 breaker，一次请求里发生了两次切换

```text
5. breaker: report-1 OPEN → HALF_OPEN（冷却 3 tick 已到，放行一次探路）
6. upstream: report-1 第 3 次调用 → 503
7. breaker: report-1 HALF_OPEN → OPEN
```

第 5 行发生在 `tryPass()` 内部：`Open.tryPass()` 检查冷却时间，到点了就 `breaker.transitionTo(HalfOpen)` 并返回 `true`。**这是「状态自己在切换自己」的典型形态**——调用方只知道「放行/不放行」，不知道中间换过一次状态。

第 7 行发生在 `record(false)` 之后：`HalfOpen.onFailure()` 直接 `transitionTo(Open)`，**不带任何退避**。这是刻意的：探路失败说明下游还没好，立刻关回去比继续放流量更安全。

**注意第 5 行是本次修 bug 时补上的。** 最初的实现只在切换发生在 `delegate.handle()` **之后**时才打印轨迹，于是 `OPEN → HALF_OPEN` 永远看不见，读者会看到：

```text
（没有 OPEN → HALF_OPEN）
6. upstream: report-1 第 3 次调用 → 503
7. breaker: report-1 HALF_OPEN → OPEN     ← 读者：它是怎么变成 HALF_OPEN 的？
```

这是「日志只记录了结果、没记录原因」的经典案例。修复方式是在 `tryPass()` 前后各拍一次快照，发现状态变了就补一条轨迹（§5.3 有完整代码）。

#### （6）`连续失败 2` 为什么在 c-4 之后没有变成 3

c-4 的结尾是 `report-1=OPEN，连续失败 2，切换 3 次`。第三次上游失败没有被计入连续失败，因为：

```java
// HalfOpen.onFailure
@Override
public void onFailure(CircuitBreaker breaker) {
    breaker.transitionTo(new Open(breaker));   // ← 只切状态，不动 consecutiveFailures
}
```

`consecutiveFailures` 的语义是「**Closed 状态下**连续失败了多少次」。一旦进入 OPEN，这个数字就冻结了，作为「这次熔断是由几次连续失败触发的」这个事实被保留下来。恢复时（`HalfOpen.onSuccess`）直接归零。

**如果这里写成 `breaker.consecutiveFailures++`，会发生什么？** 熔断器会在「OPEN → HALF_OPEN → OPEN」之间循环时不断累加这个数字，然后如果你用 `consecutiveFailures >= threshold` 来判断「要不要保持打开」，熔断会永远打不开也永远关不上。这类「用同一个字段表达两种状态下的两种含义」是状态机最常见的 bug 来源，也是 §5.3 把 `Closed.onSuccess` 和 `HalfOpen.onSuccess` 分开写的理由。

#### （7）切换 5 次，逐次对上

这不是「大概 5 次」，每一次切换都能指出发生在哪一行：

| 序 | 切换 | 触发点 | 输出位置 |
|---|---|---|---|
| 1 | CLOSED → OPEN | c-2 的 `record(false)`，连续失败达到阈值 2 | 第 6 行 |
| 2 | OPEN → HALF_OPEN | c-4 的 `tryPass()`，冷却 3 tick 到期 | 第 5 行 |
| 3 | HALF_OPEN → OPEN | c-4 的 `record(false)`，探路失败 | 第 7 行 |
| 4 | OPEN → HALF_OPEN | c-5 的 `tryPass()`，又冷却 3 tick | 第 5 行 |
| 5 | HALF_OPEN → CLOSED | c-5 的 `record(true)`，探路成功 | 第 7 行 |

**「切换次数」是一个不该被忽略的指标。** 如果线上某台机器的熔断器 `transitions` 每分钟涨几百次，说明它一直在 OPEN/HALF_OPEN 之间抖动——下游不是「挂了」，是「快挂了」，这种状态比彻底挂掉更难伺候（每次探路都要打一次真实请求）。

#### （8）5 个请求，4 次上游：这个 1 就是熔断的全部价值

```text
  上游真实被调用 4 次，而网关收到了 5 次请求
```

在 c-3 这一次，如果没有熔断器，网关会老老实实地再打一次上游——而报告是：上游已经连续失败 2 次了，第 3 次大概率也是失败。**熔断器省掉的不只是这 1 次调用，还有这 1 次调用的超时等待（真实系统里通常是 1~30 秒）。**

这就是最后那句总结的份量：

> 重试是「再试一次」，熔断是「别再试了，先让下游喘口气」。

两个模式的动机**方向相反**：

| | 重试 | 熔断 |
|---|---|---|
| 面对失败的假设 | 「这次是偶然，下一次可能就好了」 | 「现在它在坏，继续打只会更坏」 |
| 作用时间 | 毫秒级（本次请求内） | 秒~分钟级（跨请求） |
| 代价 | 放大下游压力（1 次变 2 次） | 牺牲一部分本可成功的请求（快速失败） |
| 谁该在外层 | **重试在外**：一次重试序列共享一个熔断判断 | 熔断在内：避免重试把熔断的计数放大 |

最后一行就是 §3.3 那个探针在测的东西：`Retry(CircuitBreaker(Service))` vs `CircuitBreaker(Retry(Service))`，前者上游调用 3 次，后者 6 次，**同样的故障、同样的重试次数，上游压力差一倍**。

---

### 6.6 Demo 9：两个订阅者，同一批事件，两种世界观

```text
================================================================================================
Demo 9 观察者：同一批事件，指标看分布，告警看连续性
================================================================================================
  故障脚本：report-1 前 3 次失败、order-1 前 1 次失败；关闭重试。
  每次用不同的客户端 IP，避开限流，让注意力集中在观察者上。

  o-1 /api/report → 503 (report-1, 1 次上游)
  o-2 /api/report → 503 (report-1, 1 次上游)
  o-3 /api/report → 503 (gateway, 0 次上游)
  o-4 /api/order → 503 (order-1, 1 次上游)
  o-5 /api/order → 200 (order-1, 1 次上游)

  指标订阅者看到的是「分布」：
    总请求 5，成功 1，失败 4，成功率 20.0%
    状态码分布 {200=1, 503=4}
    谁产生的响应 {gateway=1, order-1=2, report-1=2}

  告警订阅者看到的是「连续性」（阈值 2 次，触发后归零以免刷屏）：
    [ALERT] 连续 2 次失败，最近一次：o-2 /api/report → 503（report-1）
    [ALERT] 连续 2 次失败，最近一次：o-4 /api/order → 503（order-1）
    共 2 条告警，历史最长连续失败 2 次

  两个订阅者消费的是同一批 Event，但关注点完全不同：
  指标要「全量分布」（加一个图表不用改网关），告警要「异常连续性」
  （总量阈值会被流量放大，连续失败才说明现在真的出问题了）。
```

#### （1）o-3 是这段演示的「眼」

五个事件里，只有 o-3 的 `servedBy` 是 `gateway`：

```text
o-3 /api/report → 503 (gateway, 0 次上游)
```

它不是上游失败，是熔断器快速失败。**但它和 o-1、o-2 一样是 `503`。**

如果没有 `servedBy` 这个字段，三行都会是：

```text
o-1 /api/report → 503
o-2 /api/report → 503
o-3 /api/report → 503
```

三行完全一样。运维看到这样的日志，会以为上游被打了三次、失败了三次；实际上第三次根本没出门。

| 视角 | 看到的 | 结论 |
|---|---|---|
| 只看状态码 | 3 个 503 | 「上游三次全挂」→ 去查上游 |
| 看 `servedBy` | 2 个 report-1 + 1 个 gateway | 「上游挂两次，熔断开始生效」→ 等熔断恢复 |
| 看 `attempts` | 1 / 1 / **0** | 第三次连尝试都没有 → 熔断确实在保护上游 |

所以 `GatewayResponse` 里那行 `servedBy` 不是「顺便记一下谁处理的」，它是**区分「故障」与「已防护」的唯一手段**。`upstreamFailure()` 也是靠它实现的（§5.6）：

```java
public boolean upstreamFailure() {
    return retryable() && !"gateway".equals(servedBy);
}
```

#### （2）指标的三个视角，对应三种排查问题

```text
总请求 5，成功 1，失败 4，成功率 20.0%
状态码分布 {200=1, 503=4}
谁产生的响应 {gateway=1, order-1=2, report-1=2}
```

| 指标 | 回答的问题 | 什么时候看它 |
|---|---|---|
| 成功率 20% | 「整体健康吗」 | 大盘、SLO 看板 |
| `{200=1, 503=4}` | 「失败是什么类型」 | 看到 4xx 突增 → 查客户端；5xx 突增 → 查上游 |
| `{gateway=1, order-1=2, report-1=2}` | 「谁在承担」 | 某台实例计数异常高 → 选路偏斜；`gateway` 占比高 → 防护层在起作用（或误伤） |

第三个指标的威力在于它**把「防护」和「故障」分开**。如果某天 `{gateway=90, order-1=2}`，那不是灾难，那是熔断器救了你 90 次。

#### （3）告警的「触发后归零」：一个最小可用的防刷屏

第 2 条告警值得细看：

```text
o-1 503 → 计数 1
o-2 503 → 计数 2 → 触发！打印告警，计数归零
o-3 503 → 计数 1（网关拒的也算失败）
o-4 503 → 计数 2 → 触发！打印告警，计数归零
o-5 200 → 计数 0
```

所以 5 个请求触发了 2 条告警，而不是 1 条（连续 4 次失败）。

**如果不归零会怎样？** o-2 触发一次、o-3 又触发一次、o-4 又触发一次——一个持续 10 分钟的故障会刷出几千条告警，把所有人的手机打爆，然后所有人都开始忽略告警。

这就是「告警疲劳」（alert fatigue）的起点。真实系统的做法是三层：

```text
① 归零 / 静默期（本模块的最小实现）
② 指纹去重：同一个「服务 + 错误类型」在 N 分钟内只发一次
③ 升级策略：持续 5 分钟未恢复 → 升级到值班主管
```

本模块只做了 ①。②③ 不是模式能解决的问题，是告警平台的事——**但 ① 必须在数据源头做**，否则下游再强的去重也只是在掩盖噪声。这是一条值得记住的分工原则：

> **生产者负责「事实准确、粒度合适」，消费者负责「聚合、去重、升级」。** 把去重逻辑塞进生产者，等于让每个订阅者都重复实现一遍。

#### （4）为什么「连续失败」比「失败总量」更适合做告警

假设你用「5 分钟内失败超过 100 次」做阈值：

| 场景 | 实际故障 | 失败总量 | 是否会告警 |
|---|---|---|---|
| 低峰期，某台机器彻底挂了 | 严重 | 20 次 | **不会**（阈值没到） |
| 高峰期，流量是平时 10 倍 | 无故障（失败率 0.5%） | 500 次 | **会**（误报） |

**总量阈值会被流量规模污染。** 而「连续失败 N 次」与流量无关：它衡量的是「同一件事连续出错」，在 1 QPS 和 10000 QPS 下含义一致。

代价也要说清：连续失败对**稀疏故障**不敏感。如果失败是「每 10 次里错 1 次」，连续失败永远到不了 2，一条告警都不会有——但错误率其实有 10%。所以完整的告警体系需要**两类指标并存**：

- **连续性**（本模块的 AlertListener）：抓「明确挂掉」；
- **错误率**（`MetricsCollector` 能算出来，但本模块没做阈值判断）：抓「劣化」。

本模块只实现了前者，这是刻意的最小化。**「我知道缺什么」和「我不知道缺什么」是两种完全不同的工程状态**，前者可以排期，后者只能等事故。

#### （5）观察者的真正价值：加一个订阅者，改零行网关代码

Demo 9 里创建订阅者是这样写的：

```java
MetricsCollector metrics = new MetricsCollector();
AlertListener alerts = new AlertListener(2);
gateway.addListener(metrics);
gateway.addListener(alerts);
```

如果明天要加「链路追踪」，只需要：

```java
gateway.addListener(event -> tracer.record(event));   // GatewayListener 是函数式接口
```

**`Gateway.handle` 一行都不用改。** 对比 v0：加指标要插一段计数、加告警要插一段判断、加追踪要插一段上报——每插一次，`handle` 就多一个 `try/catch` 的理由，因为「监控代码不能影响主流程」这条纪律，在方法体里是防不住的（你无法阻止下一个人在里面 `throw`）。

但观察者也有代价，而且这个代价在本模块里**没有被解决**：

```java
for (GatewayListener listener : listeners) {
    listener.onEvent(event);     // ← 同步调用，没有 try/catch
}
```

如果 `AlertListener` 在 `onEvent` 里抛了 `NullPointerException`：

1. 它后面的订阅者收不到这条事件；
2. 异常会冒到 `Gateway.handle` 之外，**这次请求的响应已经生成，但调用方拿到的是异常**。

这是本模块的一处**真实缺陷**，不是「简化」。真实实现应该至少做到其中一条：

```java
// 方案 A：隔离异常（最小修复）
for (GatewayListener listener : listeners) {
    try { listener.onEvent(event); }
    catch (RuntimeException e) { log.warn("订阅者失败，已隔离", e); }
}

// 方案 B：异步分发（更彻底，但引入队列与丢弃策略）
```

本模块故意没做 A，因为它会让 Demo 9 的计数「悄悄少一条」而看不出原因——**一个不报错的监控系统，比一个会报错的监控系统更危险。** 这条被列进 §8.3 的已知瑕疵，并说明「生产环境必须做 A 或 B」。

---

### 6.7 Demo 10：最后一份对账表，每一格都必须能重算

前面九段 Demo 各自证明一件事，但它们共享**同一个网关实例**。Demo 10 就是在九段跑完之后，把这个实例的内部状态一次性打印出来——它的作用不是「再演示一次」，而是**审计**。

```text
================================================================================================
Demo 10 汇总：13 个请求之后，网关内部留下了什么
================================================================================================
  已处理请求          13
  进入上游层          5 次（缓存命中 / 401 / 429 都不会进来）
  享元池              池内 3 条路由，命中 6 次，创建 3 次，命中率 66.7%
  缓存                缓存 3 条，命中 4 / 未命中 3 / 绕过 2，命中率 57.1%
  熔断器              order-1=CLOSED, pay-1=CLOSED, user-1=CLOSED
  限流快照            {10.0.0.6=1}
  上游真实调用        order-1=2，order-2=0，pay-1=2，user-1=1

  指标订阅者：总 13，成功 9，失败 4，成功率 69.2%，缓存命中 4
  状态码分布  {200=9, 401=3, 429=1}
  响应来源    {gateway=4, order-1=3, pay-1=2, user-1=4}

  告警订阅者：1 条
    [ALERT] 连续 2 次失败，最近一次：r-7 /api/order → 401（gateway）

  ⚠ 这条告警是「噪声」的活标本：它由 r-6 / r-7 的 401 触发。
    401 是调用方的问题（没带 token），不是系统故障，不该进告警通道。
    真实告警的判定必须写成「5xx 数量 / 错误率突增」，而不是「非 2xx 就算失败」。
    这也是本 Demo 故意留下的瑕疵：把「失败」简化成「非 2xx」够用，但不正确。

  ⚠ order-2 的真实调用次数是 0：最少连接在单线程 Demo 里始终选中第一台，
    所以那份「order-2 失败 1 次」的故障脚本到最后也没被消耗。

  六个模式各自负责的那一小块：
    责任链  决定「能不能进来」：auth → rate → router → cache，任一环可短路
    策略    决定「进来后打哪台」：LoadBalancer 可换，RouterFilter 不知道算法细节
    装饰器  决定「打的时候怎么保护」：Metrics(Retry(CircuitBreaker(Service)))
    状态    决定「熔断器此刻能不能放行」：三个状态类各自回答三个问题
    享元    决定「共享什么」：一张路由配置被 13 个请求复用，只创建了 3 次
    观察者  决定「谁来消费结果」：新增一个订阅者不需要改网关一行代码
```

#### （1）13 个请求是怎么数出来的

| Demo | 请求数 | 请求 id | 落在哪一层 |
|---|---|---|---|
| 1 | 1 | `r-1` | 全链路到上游 |
| 2 | 1 | `r-2` | 短路在 cache |
| 3 | 1 | `r-3` | 全链路到上游（另一个主体） |
| 4 | 2 | `r-4`、`r-5` | 全链路到上游（POST 不缓存） |
| 5 | 3 | `r-6`、`r-7`、`r-8` | 全部短路在 auth |
| 6 | 5 | `r-9`…`r-13` | 1 次到上游 + 3 次缓存命中 + 1 次 429 |
| **合计** | **13** | | |

`13 = 1+1+1+2+3+5`，与「已处理请求 13」完全一致。**这个数字没有任何取巧**：`Gateway.served` 每处理一个请求加一，没有分支能绕过它（`handle` 里只有一处 `served++`，在事件广播之后）。

#### （2）5 次进入上游层，逐个能指出来

```text
进入上游层 5 次
```

| 来源 | 次数 | 具体 |
|---|---|---|
| Demo 1 | 1 | `order-1` 第 1 次 |
| Demo 3 | 1 | `order-1` 第 2 次（另一个主体） |
| Demo 4 | 2 | `pay-1` 第 1、2 次 |
| Demo 6 | 1 | `user-1` 第 1 次 |
| **合计** | **5** | |

而 `上游真实调用` 那一行是：

```text
order-1=2，order-2=0，pay-1=2，user-1=1     →  2+0+2+1 = 5
```

两个数字相等，说明「进入上游层」和「真实调用上游」在这九段 Demo 里是**一一对应**的（因为 Demo 1-6 里没有任何一次重试）。如果哪天不等，差额就是重试次数——Demo 7 单独验证过这一点（`2 次上游` 对应 `order-2` 1 次 + `order-1` 1 次）。

#### （3）缓存：4 命中 / 3 未命中 / 2 绕过，和 13 个请求对得上

```text
缓存 3 条，命中 4 / 未命中 3 / 绕过 2，命中率 57.1%
```

| 分类 | 次数 | 明细 |
|---|---|---|
| 命中 | 4 | `r-2`（Demo 2）、`r-10`、`r-11`、`r-13`（Demo 6 的第 2、3、5 次） |
| 未命中 | 3 | `r-1`、`r-3`、`r-9` |
| 绕过 | 2 | `r-4`、`r-5`（POST） |
| 不进入 cache | 4 | `r-6`、`r-7`、`r-8`（401 短路在 auth）、`r-12`（429 短路在 rate） |

`4 + 3 + 2 + 4 = 13`。**加法必须闭合**，否则说明有请求在某处「消失」了——这是对账表的全部意义。

命中率 57.1% 是 `4 / (4+3)`，分母**不含绕过的 2 次**。这个口径值得说明：

| 口径 | 分子/分母 | 值 | 回答的问题 |
|---|---|---|---|
| 本模块 | 命中 /（命中+未命中） | 4/7 = 57.1% | 「可缓存的请求里，缓存帮上忙的比例」 |
| 另一种 | 命中 / 总请求 | 4/13 = 30.8% | 「所有请求里，多少是缓存解决的」 |

两个都对，但回答的问题不同。**写监控指标时必须连着口径一起定义**，否则不同团队的报表永远对不上，而且谁也说不清谁错了。

#### （4）`{gateway=4}`：这个数字是怎么来的

```text
响应来源 {gateway=4, order-1=3, pay-1=2, user-1=4}
```

| 来源 | 次数 | 明细 |
|---|---|---|
| `gateway` | 4 | 3 个 401（Demo 5）+ 1 个 429（Demo 6 第 4 次） |
| `order-1` | 3 | `r-1`、`r-3` 直连 + `r-9`（user 路由的第一个请求？）<br>⚠ 注意：`r-9` 是 `/api/user`，不该是 `order-1` |
| `pay-1` | 2 | `r-4`、`r-5` |
| `user-1` | 4 | `r-9`（真实上游 1 次）+ `r-10`、`r-11`、`r-13`（缓存命中 3 次，servedBy 沿用原响应的 `user-1`） |

上表第三行是我算错了一格，正好拿来演示对账的意义。

`order-1` 的 3 次实际是：`r-1`（Demo 1）、`r-3`（Demo 3）、**`r-2`（Demo 2 的缓存命中回放的是 `order-1` 的响应）**。
`user-1` 的 4 次是：`r-9`（真实）+ `r-10`、`r-11`、`r-13`（三次命中回放）。

**这个错误本身很有教学价值**：我第一次对着表算的时候，把 `r-9` 记成了 `order-1`，结果 3+2+4=9，与「成功 9」对得上，看起来完全正确——**直到我逐个请求去核对 `order-1` 到底是哪三个，才发现对不上。**

> **总数对不代表明细对。** 对账必须从两头做：先加总，再逐项回溯，两边都闭合才算数。

修好之后的完整表：

| 来源 | 次数 | 明细 |
|---|---|---|
| `gateway` | 4 | `r-6`、`r-7`、`r-8`（401）+ `r-12`（429） |
| `order-1` | 3 | `r-1`、`r-3`（真实）+ `r-2`（缓存命中，回放 `order-1` 的响应） |
| `pay-1` | 2 | `r-4`、`r-5` |
| `user-1` | 4 | `r-9`（真实）+ `r-10`、`r-11`、`r-13`（缓存命中） |
| **合计** | **13** | 与「已处理请求 13」一致 |

这也解释了一个看似奇怪的现象：**「上游真实调用 user-1 只有 1 次，但响应来源里 user-1 有 4 次」。** 缓存命中不产生上游调用，但它产生的响应必须归属到「当初由谁生成」——否则监控上会看到 3 次「无来源的 200」。

#### （5）`statusCounts` 与 `successCount` 的一致性

```text
总 13，成功 9，失败 4，成功率 69.2%
状态码分布 {200=9, 401=3, 429=1}
```

`9 + 3 + 1 = 13`；「成功 9」= `{200=9}`。这里 `successCount` 的定义是 `status / 100 == 2`（`MetricsCollector` 里写死），所以它和 `{2xx=…}` 必然一致。

**如果哪天要支持 3xx（重定向）算不算成功，必须改这一处**，而不是在三个地方各加一个 `||`。这是「单一判定点」的价值：`MetricsCollector` 里只有一处定义「什么是成功」，`AlertListener` 用的是另一处（「非 2xx 算失败」）——**两处定义不一致，正是那条 401 噪声告警的根因**（见下面第 7 点）。

#### （6）熔断器里为什么没有 `report-1`

```text
熔断器 order-1=CLOSED, pay-1=CLOSED, user-1=CLOSED
```

因为 Demo 8 用的是**另一个独立网关**（`Gateway.createDefault(Map.of("report-1", 99))` 那种），它有自己的 `CircuitBreakerHandler` 和自己的 `breakers` map。Demo 10 打印的是主网关的熔断器，主网关从来没处理过 `/api/report`。

这个细节说明一件重要的事：**「状态」是实例级的，不是类级的。** 如果 `CircuitBreakerHandler` 把 `breakers` 写成 `static`，两个网关会共享熔断状态——Demo 8 的探路会污染主网关，Demo 10 就会看到 `report-1=OPEN`。

```java
// 本模块的写法：实例字段
private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
```

这也是为什么 §5.10 强调「没有任何一个模式在构造函数内部被 `new` 出来」：**只有对象是独立的，状态才是独立的。**

#### （7）那条 401 告警：本模块最诚实的一处「故意留瑕疵」

```text
[ALERT] 连续 2 次失败，最近一次：r-7 /api/order → 401（gateway）
```

`AlertListener` 的判定是：

```java
if (!event.success()) {          // GatewayListener.Event.success() = status / 100 == 2
    consecutive++;
    if (consecutive >= threshold) { ... }
} else {
    consecutive = 0;
}
```

`401` 不是 2xx，所以被当成失败。但 401 是**调用方的问题**，不是系统故障。它进告警通道会带来两个后果：

1. 值班的人被无关告警叫醒；
2. 真正的上游故障告警混在噪声里，降低响应速度。

**为什么本模块不修？** 因为它是一个**需要被看见的反例**。如果我把 `AlertListener` 写成「只有 5xx 才计数」，Demo 9 里那 2 条告警会变成 1 条，输出更「干净」——但读者就看不到「用 `!success()` 当失败判据」这个错误的真实后果了。

这条在 §8.3 里以「已知瑕疵 #2」列出，并给出正确的判定方式：

```java
// 正确写法（本模块未采用）
boolean isSystemFailure = event.status() >= 500
        && !"gateway".equals(event.servedBy());   // 网关自己拒的不算
```

注意后半句：**连「5xx」都不够**，还得排除网关自己产生的 503（熔断快速失败）。否则上游刚挂，告警会因为「熔断器挡了 100 个请求」而暴涨——**你的告警系统会被你的防护措施打爆。**

#### （8）`order-2 = 0`：一个没被消耗的故障脚本

```text
⚠ order-2 的真实调用次数是 0：最少连接在单线程 Demo 里始终选中第一台，
  所以那份「order-2 失败 1 次」的故障脚本到最后也没被消耗。
```

这句话是对 Demo 7 那段 ⚠ 的补充。它意味着：

- Demo 10 的 `{order-1=2, order-2=0}` 里，`order-2=0` **不是因为「它坏了所以没被选中」，而是因为「单线程下最少连接永远选第一台」**；
- 如果我只打印 `{order-1=2, order-2=0}` 而不加这句，读者会以为策略在「避开坏机器」——那是**虚假的正确**。

**「诚实标注未覆盖的路径」比「多打印一个漂亮的数字」重要得多。** 这句话让 Demo 10 少了一个可以吹的结论，多了一条可验证的事实。

---

### 6.8 十段 Demo 覆盖了什么、没覆盖什么

十段 Demo 加起来 304 行输出，但它们**不等价于完整测试**。这张表是用来防止「跑通了 = 全覆盖」这个误解的。

#### （1）覆盖矩阵

| Demo | 输出的关键断言 | 主要涉及的类 | 对应模式 |
|---|---|---|---|
| 1 | 一条请求确经 6 层；`attempts=1`；轨迹行序=包裹顺序 | `Gateway`、4 个 Filter、4 个 Handler | 全部六个 |
| 2 | 命中缓存时 `attempts=0`；轨迹停在 cache | `CacheFilter` | 责任链（短路） |
| 3 | 同路径不同主体不命中；返回 `data#2` | `CacheFilter` + `CallContext.principal` | 责任链（短路） |
| 4 | POST 两次都打上游；`cache: POST 不缓存` | `CacheFilter` | 责任链（短路） |
| 5 | 三个 401 三种响应体；配额快照 `{}`→`{}` | `AuthFilter`、`RateLimitFilter` | 责任链（顺序） |
| 6 | 缓存命中仍消耗配额；快照 `{10.0.0.6=4}`；rollWindow 后恢复 | `RateLimitFilter` | 责任链（顺序） |
| 7 | 同故障下轮询 vs 最少连接的不同上游次数；一次完整的 retry 轨迹 | `LoadBalancer`×2、`RetryHandler` | 策略、装饰器 |
| 8 | 五态切换逐次可指认；5 请求 / 4 次上游；`servedBy=gateway` 的快速失败 | `CircuitBreaker`、`CircuitBreakerHandler` | 状态、装饰器 |
| 9 | 同一批事件两种聚合；2 条告警；`{gateway=1}` 分区 | `GatewayListener`、`MetricsCollector`、`AlertListener` | 观察者 |
| 10 | 13=4+3+2+4 闭合；5 次上游逐项可回溯；`order-2=0` | `RouteTable`、`Gateway` | 享元 + 全部 |

#### （2）这 304 行**没有**覆盖的路径

| 未覆盖 | 为什么 | 后果 | 详见 |
|---|---|---|---|
| 并发 | 全部单线程同步调用 | `FilterChain.Cursor` 的线程安全性、`inFlight` 的竞态、熔断器的原子性都未验证 | §7.4 |
| 真实超时 | `ServiceHandler` 没有模拟耗时 | 熔断器的「半开只放一个探路」在并发下是否真的只放一个，未验证 | §7.4 |
| 缓存过期 / 容量上限 | `CacheFilter` 是无限 Map，没有 TTL | 长时间运行会 OOM（和 §7.1 的 OOM 是不同原因） | §8.3 |
| 多实例熔断隔离 | Demo 8 只有 `report-1` 一台 | 「按实例熔断」这个设计在 2 台以上时的表现未实测 | §7.4 |
| 只有一台实例时的 `pickExcluding` | 所有 Demo 都有 ≥1 个备选 | `others.isEmpty()` 分支未执行 | §8.3 |
| `RouteConfig.matches()` | 路径解析走的是 `RouteTable` 内部逻辑 | 这个方法目前是死代码（保留为扩展点） | §8.3 |
| 订阅者抛异常 | 两个订阅者都不会抛 | 异常传播链未验证（代码上是明确缺陷） | §7.4 |
| `MetricsHandler` 在重试时的计数 | Demo 8 关闭了重试 | 「逻辑调用 vs 物理尝试」的差额未在指标里验证 | §8.3 |

#### （3）一张「证据强度」表

为了避免把「我推理过」和「我跑过」混为一谈，本项目的每一条结论都标了强度：

| 强度 | 含义 | 本文档里的例子 |
|---|---|---|
| **A · 实测** | 有运行输出，可复现，SHA 固定 | 13 个请求 / 5 次上游 / 五态切换 / 4 命中 3 未命中 2 绕过 |
| **B · 探针实测** | 独立探针程序跑出来的 | v0 的五类缺陷（§2.2）、装饰器顺序 3 vs 6（§3.3） |
| **C · 代码可证** | 读代码即可确认，无需运行 | `upstreamFailure()` 的判据、`tryPass()` 内切换状态 |
| **D · 逻辑推理** | 说明原理，但本 Demo 没跑 | 享元池「保住 `inFlight` 一致性」的收益、真实并发下的退化 |
| **E · 明确未验证** | 知道存在，没测 | 并发、超时、多实例熔断、订阅者异常 |

**本文档中凡是出现「一定」「必然」的地方，都属于 A/B/C；凡是 D/E，都带了「未验证」「逻辑上」「理论上」的措辞。** 这条约定写在附录 F 里，也是全文最该被审查的一条。

---

## 7. 扩展对比：新需求来了改哪里

### 7.0 先分清三种「变化」

「扩展性」这个词被用得太泛，导致很多团队把「加个接口」当成扩展性的同义词。在本模块的语境里，需求变化只有三类，处理方式完全不同：

| 变化的类型 | 例子 | 正确做法 | 用错会怎样 |
|---|---|---|---|
| **加一个新的同类** | 加签名校验、加一种选路算法、加一种熔断状态 | 实现接口 / 新增子类，**不改既有代码** | 用 `if` 分支加进去，下次再加就改第二处 |
| **加一层新的处理** | 加上游超时、加请求日志 | 新增一个包装层（装饰器 / 过滤器） | 塞进已有类，那一层变得不可测 |
| **改一个已有的值** | 限流从 3 次改成 10 次、冷却从 3 tick 改成 30 秒 | **改配置，不改结构** | 用模式去「支持可配置」，把简单的事做复杂 |

第三类是**最常被过度设计**的。本模块的 `RateLimitFilter(capacity)`、`CircuitBreakerHandler(threshold, coolDownTicks)`、`RetryHandler(maxAttempts)` 全都是构造参数——**参数化就是最便宜的扩展点**。只有当「变化的东西不是一个数字，而是一段行为」时，才轮到模式上场。

下面 7.1 是一次真实事故的完整复盘（它同时是「扩展时最容易踩的坑」的实例），7.2 给出六种具体需求的落点，7.3 说明要上生产还缺什么，7.4 列出本模块明确没有验证的项。

---

### 7.1 事故复盘：一次 `OutOfMemoryError`，以及它暴露的三个层次的错

#### （1）现场

`Client` 写完后的第一次运行，命令很普通：

```powershell
& "$JDK\bin\java.exe" '-Dstdout.encoding=UTF-8' -cp out com.patterns.combined.apigateway.Client
```

结果是：

| 观察 | 正常预期 | 实际 |
|---|---|---|
| 退出码 | 0 | **没有正常退出** |
| stderr | 0 字节 | **1842 字节** |
| stdout 行数 | 304 | **198 行（停在 Demo 8 中途）** |

输出在 `c-3 熔断器打开期间的请求` 这一段中断了。**前 7 段 Demo 全部正确**——这正是这类 bug 最烦人的地方：它不在「主流程」上，只在一个很少有人构造的边界条件下出现。

#### （2）第一步：读 stderr，不要猜

第一反应往往是「是不是 trace 太多」「是不是字符串拼接慢」。这两个猜测都错，而且都会把你带偏。正确的第一步永远是**读异常栈**：

```text
Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
	at java.base/java.lang.AbstractStringBuilder.<init>(AbstractStringBuilder.java:...)
	at java.base/java.lang.StringBuilder.append(StringBuilder.java:...)
	at com.patterns.combined.apigateway.CallContext.trace(CallContext.java:...)
	at com.patterns.combined.apigateway.RetryHandler.handle(RetryHandler.java:45)
	...
	at com.patterns.combined.apigateway.Client.demo8CircuitBreaker(Client.java:311)
	at com.patterns.combined.apigateway.Client.main(Client.java:...)
```

> 上面这段是按当时的 stderr 记录复述的：栈顶方法是 `CallContext.trace` ← `RetryHandler.handle`（第 45 行，当时版本），最终调用方是 `Client.demo8CircuitBreaker`（第 311 行，也就是 `c-3`）。原始 stderr 文件在修复后重跑时被覆盖成 0 字节，所以这里不能给出逐字节的引用——**这一点必须说清楚，否则就成了伪造证据。**

从栈里能读出三个事实，缺一不可：

1. **是 OOM，不是卡死**——说明有东西在无限增长；
2. **增长的是 `StringBuilder`**——说明是「无限追加字符串」，而不是「无限创建对象」；
3. **追加点是 `CallContext.trace`**——说明循环体里每轮都在记一条轨迹。

三条合起来，问题范围立刻缩小到一句话：**某个循环停不下来，而且每轮都在写日志。**

#### （3）第二步：定位到具体是哪次请求

栈里已经说了 `demo8CircuitBreaker`，而 `Demo 8` 有五段（c-1 到 c-5）。stdout 停在 198 行，正好是 c-3 的输出中途。c-3 的特殊之处一眼可见：

```text
  [tick 0] c-3 熔断器打开期间的请求
  响应：503  熔断器打开（report-1），请求未打到上游  <- gateway
  真实打到上游：0 次
```

**`servedBy = gateway`、`attempts = 0`。** 这是全部五段里唯一一个「一次上游都没打到」的请求，也是唯一一个会让外层循环出问题的请求。

#### （4）第三步：最小复现，把「无限」变成「有限」

定位到 c-3 之后，不要急着改代码，先把它**缩成一个 20 行的复现**。这有两个好处：证明你的判断是对的；万一判断错了，代价只有 20 行。

`_probe/apigw_v0/RetryLoopProbe.java` 就是这个小复现（132 行，自包含，只依赖 `GatewayResponse`）。它的核心是把两种循环条件并排放：

```java
// 旧写法：用共享上下文里的计数当条件
while (response.retryable() && attempts < MAX_ATTEMPTS) {
    // 模拟熔断器拒绝：不增加 attempts
    trace.append(line);
    response = breakerReject();
}

// 新写法：用本层自己的计数 + 区分「谁拒的」
while (response.upstreamFailure() && tried < MAX_ATTEMPTS) {
    tried++;
    trace.append(line);
    response = breakerReject();
}
```

真实输出（22 行，`java -Dstdout.encoding=UTF-8 -cp out RetryLoopProbe`，exit 0）：

```text
探针：同一个「熔断器打开中」的请求，两种循环条件的结果
场景：熔断器已打开，上游一次都不会被打到（这是 Client 的 c-3）

--- 旧写法：while (retryable() && context.attempts() < 2) ---
  迭代次数    50000（已达到探针上限 50000，真实代码没有这个上限）
  attempts    0（熔断器每次拒绝，从未打到上游）
  循环复核    attempts(0) < 2 → 永远成立
  熔断拒绝    50000 次，上游真实调用 0 次
  trace 体量  1638898 字符 ≈ 1.6 MB（全部驻留堆中，且还在继续涨）
  → 循环没有任何出口，trace 无限增长 → OutOfMemoryError

--- 新写法：while (upstreamFailure() && tried < 2) ---
  迭代次数    0（第一轮就退出）
  判定        retryable()=true，upstreamFailure()=false（servedBy=gateway）
  → 「网关拒绝」不是「可重试的上游故障」，循环立即结束
  轨迹        retry: 本层已尝试 1 次，不再重试

--- 对照：上游真的挂了（servedBy=report-1），新写法应该重试到上限 ---
  迭代次数    1（tried 到达 2/2 后停止）
  attempts    2（每轮都真的打到了上游）
  判定        upstreamFailure()=true，但 tried(2) 已达上限 → 停止
```

这份 22 行输出把事故拆成了三块：

- **第一块**：50000 次迭代之后 `attempts` 仍然是 0，循环条件**永远成立**。真实代码没有 50000 这个上限，所以它会一直跑到堆耗尽。
- **第二块**：新写法在第 0 轮就退出——因为「网关拒绝」不是「上游失败」。
- **第三块**：新写法没有把重试功能废掉——上游真挂时，它照样重试到 `maxAttempts` 才停。**修 bug 最怕的是「按住了这个，弄坏了那个」，这一块是防这个的。**

#### （5）根因：三层，不是一层

很多人会把这次事故归结成「循环条件写错了」，然后就事论事地改一行。但只改一行是**不够的**，因为这里叠了三个层次的错：

**第一层：循环变量选错了（表层）**

```java
while (response.retryable() && context.attempts() < maxAttempts)
```

`context.attempts()` 的语义是「**真实打到上游的次数**」（`ServiceHandler` 里调 `markAttempt()`）。而熔断器拒绝时，**根本没走到 `ServiceHandler`**，所以这个数字不会变。

循环变量必须是「**本层自己完成了几轮尝试**」。重试层不知道自己被谁包、包了几层、内层会不会擅自跳过——它唯一能确定的就是「我又 loop 了一次」。

> **原则：一个循环的终止条件，只能用这个循环自己拥有的状态。**

用别人的状态做终止条件，等于把「会不会死循环」的决定权交给了别人的实现细节。今天内层每次都会增长计数，明天有人加了一个「不增加计数的快速失败分支」，你的循环就挂了——而改那个分支的人根本不知道你在依赖它。

**第二层：语义判断错了（中层）**

```java
response.retryable()   // status >= 500
```

修改之前，**`retryable()` 的语义是「状态码是 5xx」**。但 5xx 有两种来源：

| 来源 | `servedBy` | 换一个实例有用吗 |
|---|---|---|
| 上游真的挂了（过载、崩溃） | `order-1` 等实例名 | **有用**，换一台可能就好了 |
| 网关自己拒的（熔断打开、无路由） | `gateway` | **没用**，换一台还是被同一道门拒 |

所以「5xx」不等于「值得重试」。**这是一个语义漏洞，不是算术漏洞。**

修法是把两种含义拆成两个方法：

```java
public boolean retryable()       { return status >= 500; }

public boolean upstreamFailure() { return retryable() && !"gateway".equals(servedBy); }
```

**为什么不直接在 `retryable()` 里加 `!"gateway".equals(servedBy)`？** 因为 `retryable()` 还有别的调用方（比如将来做「统一失败处理」时想知道「这个错误是不是 5xx 类」），把「谁产生的」混进「是不是 5xx」会让方法名撒谎。**一个方法只回答一个问题。**

**第三层：架构上缺少「谁拒的」这个信息（深层）**

这一层才是根本原因。如果 `GatewayResponse` 从一开始没有 `servedBy` 字段，那么第二层的修复**根本无从下手**——你拿不到任何信息去区分「网关拒的」和「上游吐的」。

回看 §5.1 那段 Javadoc：

```java
 * <p><b>为什么要把「谁产生的」也记下来（{@code servedBy}）？</b>
 * 因为这是排查线上问题的第一个问题：「这个 503 是网关自己拒的，还是上游服务吐的？」
```

**写这段代码的时候，我并不知道它会在两小时后救我一命。** 当时加 `servedBy` 的理由很朴素：`Demo 5` 要打印 `<- gateway` 和 `<- order-1` 的区别，读者得看得见。

这就是「好设计会自己产生回报」的样子：一个为了可读性加上的字段，恰好成了修 bug 的唯一抓手。**它不是一个「巧合」，而是「把事实记录完整」这个习惯的必然结果**——你记录的事实越完整，将来能回答的问题就越多。

反过来看反面：如果当初为了省一个字段，把 `servedBy` 去掉，`Client` 的输出会变成：

```text
  响应：503  熔断器打开（report-1），请求未打到上游
  真实打到上游：0 次
```

看起来也够用——`attempts=0` 已经能说明「没打上游」。但 `RetryHandler` 拿不到 `attempts` 之外的判据，就只能靠「有没有打上游」来猜，而那个数字恰恰是循环条件本身。

> **可观测性不是「给人看的日志」，它是「程序自己也能用的判断依据」。** 当同一个字段既能给人看、又能给程序判断，它的价值翻倍。

#### （6）修复：两处，一共改了十几行

**第一处：`GatewayResponse` 新增 `upstreamFailure()`**

```java
/**
 * 是否是<b>上游真的挂了</b>（而不是网关自己拒的）。
 *
 * <p><b>这个方法是一次真实事故的沉淀。</b>本项目最初的 {@code RetryHandler}
 * 用「上游被调用次数」当循环条件，而熔断器拦下的请求并不会增加这个计数，
 * 于是循环条件永远成立——{@code Client} 第一次运行就在「熔断打开期间的请求」那一步 OOM 了。
 * 两个教训：① 循环条件要用<b>本层自己的计数</b>；
 * ② 重试的判定要能区分「上游失败」与「网关拒绝」。</p>
 */
public boolean upstreamFailure() {
    return retryable() && !"gateway".equals(servedBy);
}
```

**第二处：`RetryHandler.handle` 改用本地计数 + 新判据**

```java
public GatewayResponse handle(CallContext context) {
    GatewayResponse response = delegate.handle(context);
    int tried = 1;                                             // ← 本层计数
    while (response.upstreamFailure() && tried < maxAttempts) { // ← 双重修正
        UpstreamInstance from = context.instance();
        UpstreamInstance to = context.repickInstance();
        tried++;
        context.trace("retry: 第 " + tried + " 次尝试，" + from + " → " + to);
        response = delegate.handle(context);
    }
    if (!response.success() && maxAttempts > 1) {
        context.trace("retry: 本层已尝试 " + tried + " 次，不再重试");
    }
    return response;
}
```

第二个改动里有一处**连带修正**值得单说：`if (!response.success() && maxAttempts > 1)` 这个条件。

最初它是无条件的，于是 `maxAttempts = 1`（关闭重试）的 Demo 8 会打印：

```text
retry: 本层已尝试 1 次，不再重试
```

这行出现在一个**根本没有配置重试的网关**里，读者会以为重试发生过。加上 `maxAttempts > 1` 之后，只有真的配了重试才打这行。

> **轨迹的每一行都是「事实声明」。** 一行「本层已尝试 N 次」在 `maxAttempts=1` 时是废话，在 `maxAttempts=3` 时才是有用信息。**同一个打印语句，在不同配置下的信息量可以差到零。**

#### （7）修复之后：先验证「没弄坏」，再验证「修好了」

修复后第一次运行的结果：

| 项 | 修复前 | 修复后 |
|---|---|---|
| 退出码 | 未正常退出 | **0** |
| stderr | 1842 字节 | **0 字节** |
| stdout 行数 | 198（中断） | **304（完整）** |
| 字节数 | —— | **16085** |
| SHA-256 | —— | `E4888CC8…9B800E6` |

然后关键的一步：**再跑一遍，比对 SHA**。

```text
第一次：E4888CC8F3ACB967D4E9BFD0F712198BF8B5D46B54AC0AABC5A7CF4CD9B800E6
第二次：E4888CC8F3ACB967D4E9BFD0F712198BF8B5D46B54AC0AABC5A7CF4CD9B800E6
```

两次逐字节相同。这一步验证的不是「bug 修好了」（第一次跑通就已经证明了），而是「**输出里没有时间戳、没有哈希序、没有并行调度**」——也就是说，**这份 304 行输出可以作为本文档的证据被长期引用，不会因为重跑而失效。**

如果两次 SHA 不同，本文档里引用的每一行输出都会变成「某次运行的快照」，读者无法判断他看到的是不是你引用的那份。**证据的可复现性，是文档可信度的前置条件。**

#### （8）六条可迁移的教训

| # | 教训 | 这次的对应 |
|---|---|---|
| 1 | 循环的终止条件只能用本层自己的状态 | `tried` 而非 `context.attempts()` |
| 2 | 「可重试」不等于「5xx」，要区分「谁产生的失败」 | `upstreamFailure()` |
| 3 | 事故现场先读异常栈，不要猜 | 栈直接指向 `RetryHandler.handle` + `demo8` |
| 4 | 定位后先写最小复现，再改生产代码 | `RetryLoopProbe`（132 行） |
| 5 | 修完要验「没弄坏」：对照组必须同时跑 | 探针第三段证明重试没被废掉 |
| 6 | 修完要验「可复现」：两次 SHA 必须一致 | `E4888CC8…` |

#### （9）如果只记一句

> **无限循环在 Java 里最常见的死法不是卡住 CPU，是 `OutOfMemoryError`。** 只要循环体里有一点「累积」——拼字符串、加进 List、`trace`、`log.debug`——它就会以「内存耗尽」的形式出现，把你往「是不是内存不够」的方向带偏。

而这次真正的教训更靠前一步：

> **一个抽象的边界，就是它「不知道」的东西。** `RetryHandler` 不知道内层会不会拒绝请求；它一旦假设「每次 loop 内层都会打一次上游」，这个假设就成了一个定时炸弹——而拆弹的人，往往不是埋弹的人。

---

### 7.2 六种新需求，逐个说明改哪里

先给一张总表，再挑三个最容易出错的展开。

| # | 需求 | 新增什么 | 要改的既有代码 | 完全不用动的 | 用到的模式 |
|---|---|---|---|---|---|
| 1 | 加签名校验 | `SignatureFilter implements GatewayFilter` | `Gateway` 的 `List.of(...)` **一行** | 其余 4 个过滤器、整个装饰器栈 | 责任链 |
| 2 | 加上游超时 | `TimeoutHandler implements UpstreamHandler` | `Gateway` 组装 **一行** + `CircuitBreakerHandler` **加 try/catch** | `ServiceHandler`、`RetryHandler`、过滤器链 | 装饰器 |
| 3 | 换一致性哈希选路 | `ConsistentHashBalancer implements LoadBalancer` | `Gateway.createDefault` **一行** | `RouterFilter`、所有过滤器 | 策略 |
| 4 | 熔断加「运维强制打开」 | `ForcedOpen implements BreakerState` | `CircuitBreaker` 加一个构造 / setter | `Closed`、`Open`、`HalfOpen` 三个类 | 状态 |
| 5 | 加链路追踪上报 | `TracingListener implements GatewayListener` | `main` 里 `addListener` **一行** | `Gateway.handle`、所有过滤器与装饰器 | 观察者 |
| 6 | 限流从 3 次改成 10 次 | 什么都不用加 | 构造参数 `3` → `10` | 全部 | 无（参数化） |

#### 7.2.1 加超时：「new 一个类」远远不够

最容易低估的一个。看起来只需要：

```java
public final class TimeoutHandler implements UpstreamHandler {
    private final UpstreamHandler delegate;
    private final long timeoutMillis;

    @Override
    public GatewayResponse handle(CallContext context) {
        // 真实实现用 CompletableFuture + orTimeout，或把超时传给 HTTP 客户端
        return delegate.handle(context);
    }
}
```

然后 `Gateway` 里改成 `Metrics(Retry(CircuitBreaker(Timeout(Service))))`。

**但这样加完之后，熔断器会瞎掉。**

看 `CircuitBreakerHandler` 现在的写法：

```java
GatewayResponse response = delegate.handle(context);   // ← 如果这里抛超时异常
breaker.record(response.success());                    // ← 这行永远不执行
```

`TimeoutHandler` 在 `CircuitBreaker` **内层**，它的异常会穿透 `CircuitBreakerHandler`，于是：

1. 熔断器**不会记录这次失败**，连续失败计数不涨；
2. 上游明明已经超时 10 次，熔断器还是 `CLOSED`，继续放流量；
3. 换成 `CircuitBreaker(Timeout(...))` 也不行——那样熔断器的状态机在超时发生时已经走完了，同样看不到。

所以真正的改动是三处，不是一处：

```java
// CircuitBreakerHandler：必须把「异常」也翻译成一次失败记录
try {
    GatewayResponse response = delegate.handle(context);
    breaker.record(response.success());
    String after = breaker.stateName();
    if (!after.equals(afterPass)) { context.trace("breaker: " + ... + " → " + after); }
    return response;
} catch (RuntimeException e) {
    breaker.record(false);        // ← 关键：超时/连接异常也算上游失败
    throw e;                       // ← 但不要吞掉，让统一异常层转成 504
}
```

这个例子的价值在于它推翻了一个常见的错觉：

> **装饰器链不是「随便加一层」的乐高。** 每加一层，就多一种失败形态，而所有**外层**都必须学会识别它。

这也解释了一个真实世界的现象：为什么很多网关的熔断器「从来不触发」？因为它们的熔断器只统计「HTTP 5xx」，而超时在客户端是 `SocketTimeoutException`——不是响应，自然没有状态码。**熔断器统计不到的东西，它就不会保护。**

#### 7.2.2 加签名校验：为什么不能塞进 `AuthFilter`

需求：「所有请求必须带 `X-Sign`，用 appSecret 对 `method + path + timestamp` 做 HMAC，时间戳偏差超过 5 分钟拒绝。」

一个很自然的想法是「反正都是认证，塞进 `AuthFilter` 就行」。但两者变化的原因完全不同：

| | 认证（现状） | 签名（新需求） |
|---|---|---|
| 回答的问题 | 「你是谁」 | 「这个请求是不是你发的」 |
| 依赖的数据 | token 表 | appSecret + 请求体 + 时间戳 |
| 失败语义 | 401（无法识别身份） | 401 **或** 403（身份有效但签名不对） |
| 会不会因为「换认证协议」而变 | 会（JWT / OAuth2 / SSO） | 不会 |
| 会不会因为「加一个签名算法」而变 | 不会 | 会（HMAC-SHA256 / RSA / 国密） |

**两列里没有任何一行是相同的**，说明它们是两个独立的变更轴。塞在一起的结果是：`AuthFilter` 会同时因为「换认证协议」和「换签名算法」被修改，每次都有人要重新读懂整个类。

正确做法就是新增一个过滤器，插在 `auth` 之后：

```java
List<GatewayFilter> filters = List.of(
        new AuthFilter(),              // 先确认「你是谁」
        new SignatureFilter(secretProvider),   // 再确认「这个请求是你发的」
        rateLimitFilter, ...);
```

**顺序为什么必须在 `auth` 之后？** 因为签名校验需要知道「用哪个 appSecret」——那正是 `principal` 提供的信息。放在 `auth` 之前，你得把「从 token 解出 appId」的逻辑复制一份。

这也是责任链的一个隐含约束：**链上的顺序不是随意的，它编码了数据依赖。** `context` 是这条链上唯一的传递通道，谁先写、谁后读，必须想清楚（§5.4 有完整的顺序理由表）。

#### 7.2.3 三个「零修改」的例子

这三个需求之所以能做到**不改任何既有类**，是因为它们只增加「同类的另一种实现」。

**(1) 换选路算法**

```java
public final class ConsistentHashBalancer implements LoadBalancer {
    @Override
    public UpstreamInstance pick(List<UpstreamInstance> candidates, CallContext context) {
        int slot = Math.floorMod(context.request().path().hashCode(), candidates.size());
        return candidates.get(slot);
    }

    @Override
    public String name() { return "consistent-hash"; }
}
```

`Gateway.createDefault` 里把 `LoadBalancer.leastConnections()` 换成 `new ConsistentHashBalancer()`，**`RouterFilter` 一行都不用改**。它只依赖 `LoadBalancer` 接口。

**(2) 熔断加「强制打开」**

```java
public record ForcedOpen(String instanceName) implements CircuitBreaker.BreakerState {
    @Override public String name() { return "FORCED_OPEN"; }
    @Override public boolean tryPass(CircuitBreaker breaker) { return false; }
    @Override public void onSuccess(CircuitBreaker breaker) { }
    @Override public void onFailure(CircuitBreaker breaker) { }
}
```

`Closed`、`Open`、`HalfOpen` 三个类**完全不用动**。这是 `sealed interface` + 每个状态自己实现行为带来的直接好处：加一个状态 = 加一个类，不需要在三个既有类里各加一个 `if`。

> 注意一个细节：`sealed` 意味着新增状态要改 `permits` 列表。这看似是「还得改一处」，但那是**编译器强制的**——它保证你不可能忘记处理新状态。**宁可被编译器拦一次，也不要上线后才发现漏了一个分支。**

**(3) 加链路追踪**

```java
gateway.addListener(event -> tracer.span("gateway")
        .tag("path", event.path())
        .tag("status", String.valueOf(event.status()))
        .tag("servedBy", event.servedBy())
        .end());
```

`GatewayListener` 是函数式接口（只有一个 `onEvent`），所以一行 lambda 就是一个订阅者。`Gateway.handle` 不需要认识「追踪」这个概念。

#### 7.2.4 两个「不该用模式」的需求

**(1) 缓存加 TTL**

有人会说「这是享元/缓存模式的事，应该加一个 `CachePolicy` 策略」。**不必要。**

`CacheFilter` 内部持有一个 `Map<String, GatewayResponse>`，加 TTL 只需要把 value 换成一个带时间的包装：

```java
private record Entry(GatewayResponse response, long expireAt) { }
```

或者在 key 上带时间片（`user-1 | /api/order | 第 N 个 30 秒`），连 value 都不用改。

**这里的变化是「一个字段」和「一次比较」，不是「一段可替换的算法」。** 上策略模式只会让 `CacheFilter` 多一个接口、多一个实现类、多一层间接调用，而读者要跳三次才能看到 `expireAt > now` 这一行。

**(2) 限流阈值调整**

`new RateLimitFilter(10)`。结束。

如果为了「让限流可配置」而引入 `RateLimitConfig`、`ConfigProvider`、`ConfigListener`、热更新……那是**配置系统**的职责，不是设计模式的职责。**把配置问题当成设计问题，是过度设计最常见的入口。**

判断标准很简单：

> **如果你要扩展的东西可以被「一个值」或「一个 boolean」表达，那就用参数，不要用模式。**

### 7.3 如果真要上生产，还缺什么

本模块是教学 Demo。它的 304 行输出证明了「六个模式能正确协作」，但它**不是一个能上生产的网关**。这一节把差距列清楚，避免读者产生错误预期。

#### （1）功能缺口

| 缺口 | 现状 | 生产需要 | 风险 |
|---|---|---|---|
| 缓存 TTL / 容量 | 无限 Map，永不过期 | TTL + LRU 上限 + 主动失效 | **必 OOM**（与 §7.1 是不同原因） |
| 重试退避 | 立即重试，无间隔 | 指数退避 + 抖动（jitter） | 上游抖动时重试风暴 |
| 上游超时 | 无 | 连接超时 + 读超时 + 总预算 | 一个慢请求拖死整个线程池 |
| 限流维度 | 仅 IP 固定窗口 | IP + 用户 + 接口，滑动窗口 / 令牌桶 | 固定窗口有 2 倍突发（§5.5 已提） |
| 灰度 / 权重 | 路由版本写死 | 按 header / 权重 / 用户分桶 | 无法灰度发布 |
| 请求体转发 | 只有 method + path | 完整 HTTP 报文、流式、大文件 | 无法代理真实流量 |

#### （2）健壮性缺口

| 缺口 | 现状 | 生产需要 |
|---|---|---|
| 订阅者异常隔离 | 同步调用，无 try/catch | 每个订阅者独立 try/catch，或异步队列 |
| `breakers` 并发安全 | `LinkedHashMap`，非线程安全 | `ConcurrentHashMap` + `computeIfAbsent` |
| 熔断半开的并发控制 | 单线程 Demo 未暴露 | 半开状态只允许 N 个探路请求（信号量） |
| 时间源 | 逻辑时钟（可手动推进） | 单调时钟（`System.nanoTime`） |
| 背压 | 无 | 队列长度限制 + 拒绝策略 |

前三条是**当前代码里真实存在的缺陷**，不是「没实现的功能」。它们被列进 §8.3 的已知瑕疵清单。

#### （3）可观测性缺口

| 缺口 | 现状 | 生产需要 |
|---|---|---|
| 耗时 | 只记状态码 | P50 / P95 / P99 直方图 |
| 关联 id | 有 `requestId`，但没有跨服务透传 | traceId / spanId 注入下游请求头 |
| 指标出口 | 内存里的 `Map` | Prometheus / OTLP 导出 |
| 日志 | `trace` 只在内存里，靠 Demo 打印 | 结构化日志 + 采样 |
| 熔断事件 | 只在轨迹里 | 独立的「状态变更」事件流（用于画状态图） |

#### （4）安全缺口

| 缺口 | 现状 | 生产需要 |
|---|---|---|
| token 存储 | 硬编码 `Map`，明文 | 密钥管理 + 短期凭证 |
| 签名 / 防重放 | 无 | HMAC 签名 + 时间戳 + nonce |
| 请求体校验 | 无 | Schema 校验、大小限制 |
| 审计 | 无 | 谁在什么时候访问了什么（合规要求） |

这份清单的作用不是「自我批评」，而是给读者一个**判断力标尺**：当你看到一篇讲「用六个模式实现网关」的文章时，可以拿这张表去对照——**如果它只讲了模式怎么组合，没讲这些缺口，那它讲的是「模式课」，不是「网关」。** 两者都有价值，但不能混淆。

### 7.4 明确未验证的项

这一节是本项目「不许说验证过」这条纪律的兑现。以下每一项都**没有在本 Demo 中运行过**，因此本文档不对其正确性做任何断言。

| # | 未验证项 | 为什么没做 | 如果做了会暴露什么 | 风险 |
|---|---|---|---|---|
| 1 | 并发下的 `FilterChain.Cursor` | Demo 全单线程 | `Cursor` 每次 `newCall` 独立，逻辑上安全；但 `context` 是每请求一份，未在真实线程池下跑过 | 中 |
| 2 | `inFlight` 的并发配对 | 同上 | 用的是 `AtomicInteger` + `try/finally`（代码上成对）；但「最少连接在并发下是否真的分散流量」没有压测过 | 中 |
| 3 | 多实例熔断隔离 | Demo 8 只有一台 | 「按实例熔断」在 2 台以上时的表现（一台挂、另一台正常，流量是否自动转移） | 中 |
| 4 | 半开状态只放一个探路 | 无并发 | 真实并发下可能有 N 个请求同时通过 `tryPass()`，全部打进正在恢复的上游 | **高** |
| 5 | 订阅者抛异常 | 两个订阅者都不抛 | 异常会中断后续订阅者并冒出 `handle`（代码上可确认，但未实测其后果） | 中 |
| 6 | `MetricsHandler` 与重试的计数差 | Demo 8 关闭重试 | 「逻辑调用 1 次 / 物理尝试 2 次」这个差额是否被正确记录 | 低 |
| 7 | 缓存 TTL 过期边界 | 未实现 TTL | —— | —— |
| 8 | 真实 HTTP 的流式 / 大 body | 无网络层 | 装饰器链是否适合流式（一旦缓冲整个 body，内存模型完全不同） | 中 |
| 9 | 长时间运行的熔断器 map 增长 | Demo 只跑 13 个请求 | `breakers` 按实例名无限增长；实例扩容/缩容后旧条目永不清理 | 中 |
| 10 | 逻辑时钟与真实时间的边界 | 用 tick 代替时间 | 「tick 2.999 到达的请求算不算期满」这类边界 | 低 |

第 4 项被标为**高风险**，因为它不是「没测」，而是**当前实现确实是错的**：`HalfOpen.tryPass()` 无条件 `return true`，所以在并发下，一个刚进入 `HALF_OPEN` 的熔断器会把**所有**同时到达的请求都放进正在恢复的上游——这不是「保护」，是「一次性把闸门全开」。单线程 Demo 永远只有一个请求，所以这个缺陷被完美地藏住了。

第 2 项的风险降到「中」：`inFlight` 用的是 `AtomicInteger`，`acquire/release` 由 `ServiceHandler` 的 `try/finally` 保证成对，逻辑上是安全的；但「最少连接在并发下是否真的分散流量」没有压测过——而 Demo 7 已经暴露了它在单线程下会退化成「永远选第一台」，所以这条只能算「实现正确、行为未验证」。

**知道哪里可能是错的，比假装哪里都对要好得多。** 第 4 项在 §8.3 里作为「已知瑕疵 #1」单独列出，并给出修法。

---

## 8. 优缺点与常见误区

### 8.1 六个模式各自的收益与代价

很少有文章把「代价」写在「收益」旁边。这一节按本模块的真实数字来写，每一行的收益都能指到某段输出或某个类。

| 模式 | 可指认的收益 | 支付的代价 | 收益不成立的条件 |
|---|---|---|---|
| **责任链** | 4 个关注点拆成 4 个类（40/60/70/89/122 行）；Demo 5 实测「顺序决定 `rate` 是否被执行」 | 一个 `Cursor`（117 行）+ 每请求一次 `newCall` | 只有 1~2 个环节，且可预见不会新增 |
| **策略** | Demo 7 能造出「只差算法」的两个网关做对照；换算法改 1 行装配 | 一个接口 + 2 个实现（128 行） | 只有一种算法；或算法需要读遍整个上下文才能决定 |
| **装饰器** | 4 层自由组合；加超时/日志 = 加一层，不改内层 | **顺序错就是 bug**（探针实测 3 次 vs 6 次上游调用）；栈越深，栈越难读 | 只有 1 个增强；或增强之间必须共享局部变量 |
| **状态** | 5 次切换逐次可指认；加 `ForcedOpen` 不改 3 个既有状态类 | 1 个 `sealed interface` + 3 个实现类（231 行） | 只有 2 个状态且没有自动恢复（`boolean` 够用） |
| **观察者** | 加订阅者改 0 行 `Gateway.handle`；指标与告警两种聚合共用同一批事件 | 事件对象必须定型；异常隔离、异步分发要自己补 | 只有一个消费者（直接调方法更清楚） |
| **享元** | 13 个请求只创建 3 条路由（命中率 66.7%）；`inFlight` 全局唯一 | 一个池 + 一套 key 设计 + 只读约束 | 配置对象很小、创建成本极低（每请求 `new` 也无所谓） |

这张表最该看的是**第三列**。它回答的是「什么时候这套东西不值得」——而不是「什么时候用它显得高级」。

### 8.2 19.5 倍的账：2927 行到底买到了什么

#### （1）先把数字拆干净

`2927 / 150 = 19.5`。这个倍数很吓人，但它至少混了三层水分：**演示代码、注释、以及「本来就该有的东西」**。

| 口径 | 本模块 | v0 | 倍数 |
|---|---|---|---|
| 物理行数（含注释、含 Client） | **2927** | 150 | **19.5×** |
| 去掉 `Client`（475 行，纯演示/取证） | 2452 | 150 | 16.3× |
| 再只算代码行（去掉 1250 行注释） | **1329** | 123 | **10.8×** |
| 全模块只算代码行 | 1677 | 123 | 13.6× |

几个必须说明的事实：

- **注释占 42.7%**（1250 / 2927），而 v0 只有 18%（27 / 150）。多出来的 1223 行注释里，绝大部分是「为什么这样写」而不是「这行在做什么」——它们是**文档**，不是**逻辑**。
- **`Client` 占 16.2%**（475 / 2927），它一行都不参与请求处理。没有它，这个模块无法验证；有了它，行数就不好看。**这是「可验证性」的价格。**
- 真正参与请求处理的代码是 **1329 行**，是 v0 的 10.8 倍。

#### （2）1329 行代码花在哪

按六个模式 + 组装层切开：

| 归属 | 文件 | 行数 | 占比 |
|---|---|---|---|
| 责任链 | `GatewayFilter` 40 + `FilterChain` 117 + `AuthFilter` 60 + `RateLimitFilter` 89 + `RouterFilter` 70 + `CacheFilter` 122 | **498** | 37.5% |
| 观察者 | `GatewayListener` 73 + `MetricsCollector` 99 + `AlertListener` 80 | 252 | 19.0% |
| 装饰器 | `UpstreamHandler` 42 + `ServiceHandler` 85 + `RetryHandler` 76 + `MetricsHandler` 46 + `CircuitBreakerHandler` 136 | 385 | 29.0% |
| 享元 | `RouteConfig` 80 + `RouteTable` 159 + `UpstreamInstance` 95 | 334 | 25.1% |
| 状态 | `CircuitBreaker` 231 | 231 | 17.4% |
| 策略 | `LoadBalancer` 128 | 128 | 9.6% |
| 组装与载体 | `Gateway` 256 + `CallContext` 194 + `GatewayRequest` 60 + `GatewayResponse` 114 | 624 | 47.0% |
| `Client` | —— | 348 | 26.2% |

> 占比加起来超过 100%，因为这是「按职责归属」而不是「按文件唯一归属」——`RouterFilter` 同时属于责任链和策略的调用方，`CircuitBreakerHandler` 同时属于状态和装饰器。**一个文件被两个模式使用，恰恰说明这两个模式在协作，而不是重复。**

#### （3）这 1329 行买到的五样东西

| # | 买到了什么 | 可指认的证据 | 没有它会怎样 |
|---|---|---|---|
| 1 | **顺序可验证** | Demo 5 的配额快照 `{}`→`{}` | 你无法证明 `401` 不会消耗配额 |
| 2 | **算法可替换** | Demo 7 的两份网关 | 换选路只能改热方法 |
| 3 | **保护可分层** | 装饰器顺序探针：3 次 vs 6 次上游 | 顺序错了不会报错，只会悄悄放大压力 |
| 4 | **状态可枚举** | Demo 8 的五次切换 | 熔断只能是一个 `boolean`，且永不恢复 |
| 5 | **失败可归因** | `servedBy=gateway` 与 `attempts=0` | §7.1 的 OOM 无法定位 |

**这五样东西里，没有一样是「代码更短」或「跑得更快」。** 全模块的 304 行输出里，没有一个数字是在证明「性能更好」——恰恰相反，多了一层装饰器，单次调用一定更慢。

> **模式买的不是性能，是「可改变」和「可证明」。** 如果你的场景不需要这两样，19.5 倍就是纯亏。

#### （4）哪些行是纯浪费

诚实地说，有。三处：

| 位置 | 问题 | 应该怎么办 |
|---|---|---|
| `RouteConfig.matches(String)` | **死代码**：全模块没有任何调用点（`RouteTable` 用自己的解析逻辑） | 删掉，或让 `RouteTable` 真的用它 |
| `GatewayFilter.name()` | 每个过滤器都实现了，但 `FilterChain` 从不调用（只在 `RouterFilter` 里用了 `balancer.name()`） | 要么让它进轨迹（`auth → rate → ...` 就来自它），要么删掉 |
| `CircuitBreakerHandler.breakers()` | 返回 `Map.copyOf(...)`，Demo 只用 `describeStates()` | 保留（对外可观测接口），但要在文档里标「未被 Demo 使用」 |

前两条是真正的浪费：**它们增加了读者的认知负担（「这个方法是不是在别处有用？」），却没有任何行为价值。** 按 §2.4 的说法，它们属于「删了不报错，也不会错」——那就该删。

> **一个类的价值不在于它存在，而在于它缺失时会发生什么。** 这句话的反面同样成立：**一个方法缺失时什么都不会发生，它的存在就是成本。**

#### （5）如果只保留 3 个模式，删哪三个

这是一个很好的压力测试。假设预算砍半，只能留三个：

| 保留 | 理由 | 删掉的后果 |
|---|---|---|
| **责任链** | 认证/限流/路由/缓存是四个正交关注点，合并必然导致「改一个动全部」 | 删了要有 4 个 `if` 塞进 `handle`，且顺序不可测 |
| **状态** | `boolean` 表达不了「冷却 + 探路」，这是表达能力问题，不是风格问题 | 删了熔断器打开后永不恢复（v0 的 C 段实测） |
| **装饰器** | 重试/熔断/指标的**顺序**是正确性问题（3 vs 6） | 删了得写 8 个子类覆盖两两组合 |

| 可删 | 理由 | 代价 |
|---|---|---|
| **策略** | 本 Demo 只有两种算法；真实网关也只有 3~5 种，一个 `switch` 也能接受 | `RouterFilter` 会知道所有算法，加算法要改它 |
| **观察者** | 只有 2 个订阅者时，直接调用更简单 | 加第 3 个订阅者要改 `Gateway.handle` |
| **享元** | 路由配置很小，每请求 `new` 一份在 Demo 规模下无感 | 失去「`inFlight` 全局唯一」这个**正确性**保证（这是它最难被替代的部分） |

注意最后一行的措辞：享元的收益在 Demo 里看起来只是「省了 3 次对象创建」（微不足道），但它真正不可替代的是**「同一个实例的 `inFlight` 只有一份」**。这一条是 §5.9 讲过的，也是它和「普通缓存」最大的区别。

**这个练习的价值不在于答案，而在于它逼你说出「每个模式到底在防哪种变化」。** 说不出来的，就是可以删的。

---

### 8.3 已知瑕疵清单

这一节列出本模块**已知存在、没有修复**的问题。它们是「教学取舍」或「刻意的反例」，不是「没想到」。

#### 瑕疵总表

| # | 瑕疵 | 代码位置 | 严重度 | 是否影响已发布的输出 |
|---|---|---|---|---|
| 1 | `HALF_OPEN` 不限制探路并发 | `CircuitBreaker.HalfOpen.tryPass()` | **高**（并发下） | 否（单线程） |
| 2 | 告警把 401 当系统失败 | `AlertListener.onEvent` | 中 | **是**（Demo 10 的告警） |
| 3 | `breakers` 非线程安全 | `CircuitBreakerHandler` 第 33 行 | **高**（并发下） | 否 |
| 4 | 订阅者无异常隔离 | `Gateway.handle` 的广播循环 | 中 | 否 |
| 5 | 死代码 | `RouteConfig.matches()`、`GatewayFilter.name()` | 低 | 否 |
| 6 | 每个 handler 自持时钟 | `CircuitBreakerHandler` 第 32 行 | 低（Demo 中） | 否 |
| 7 | 缓存无 TTL / 无上限 | `CacheFilter` | **高**（长跑） | 否 |
| 8 | 固定窗口有 2 倍突发 | `RateLimitFilter` | 中 | 否 |
| 9 | 熔断拒绝复用 503 | `CircuitBreakerHandler` 第 72 行 | 低 | 是（可接受） |
| 10 | 无 token 过期 / 明文 | `AuthFilter` 的 `TOKENS` | 中（生产不可接受） | 否 |

#### #1 `HALF_OPEN` 不限制探路并发（最严重）

```java
private static final class HalfOpen implements BreakerState {
    @Override
    public boolean tryPass(CircuitBreaker breaker) {
        return true;      // ← 无条件放行
    }
    ...
}
```

**现象**：熔断器进入 `HALF_OPEN` 后，所有到达的请求都会被放行。

**为什么单线程 Demo 看不出问题**：Demo 8 每个 tick 只有一个请求，`tryPass()` 被调用一次就返回，紧接着 `record()` 把状态改回 `OPEN` 或 `CLOSED`。**「放行一次」是单线程的巧合，不是设计。**

**并发下的后果**：冷却时间一到，恰好有 500 个并发请求在排队，它们会**同时**看到 `HALF_OPEN` 并全部通过——刚刚被判定为「可能已经恢复」的上游，瞬间被 500 个请求打满。**熔断器在最需要它的时刻，变成了一个放大器。**

**正确做法**：`HALF_OPEN` 必须持有一个「已放行的探路名额」计数，只允许 N 个（通常 1 个）通过：

```java
private static final class HalfOpen implements BreakerState {
    private final AtomicInteger probes = new AtomicInteger();

    @Override
    public boolean tryPass(CircuitBreaker breaker) {
        return probes.getAndIncrement() < 1;   // 只放第一个
    }
}
```

**为什么这个 Demo 不修**：修了之后需要写并发测试才能证明它有效，而模块内没有线程池。加一个「看起来对但没验证」的 `AtomicInteger`，比明确指出「这里是错的」更危险——**读者会以为它已经被验证过。**

#### #2 告警把 401 当系统失败（已暴露）

```java
if (!event.success()) {          // success() = status / 100 == 2
    consecutive++;
    ...
}
```

**现象**：Demo 10 的那条 `[ALERT] 连续 2 次失败，最近一次：r-7 → 401`。

**根因**：`AlertListener` 复用了 `Event.success()`，而 `success()` 的定义是「2xx」。它把「客户端错误」「限流」「熔断快速失败」「上游故障」四种语义完全不同的情况合并成了「失败」。

**正确判据**（本模块未采用）：

```java
boolean systemFailure = event.status() >= 500
        && !"gateway".equals(event.servedBy());   // 排除网关自身拒绝
```

**为什么本模块不修**：它是**刻意保留的反例**。如果修掉，Demo 9/10 的告警会变成「0 条」，读者就看不到「错误判据设计不当」的真实后果。这条在 §6.7 第（7）点已经完整说明。

#### #3 `breakers` 非线程安全

```java
private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
...
return breakers.computeIfAbsent(instanceName, name -> new CircuitBreaker(...));
```

**现象**：多线程同时为不同实例创建熔断器时，`LinkedHashMap` 可能丢条目、结构损坏；`breakers()` 里的 `Map.copyOf` 在迭代时可能抛 `ConcurrentModificationException`。

**正确做法**：

```java
private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
```

**为什么本模块不修**：改一行就对了，但改了之后**没有任何 Demo 能证明它变对了**（单线程下两者行为完全一致）。这是一个典型的「正确但不可验证」的修改——本项目的纪律是「不许声称验证过没验证的东西」，所以把它列在这里，比悄悄改掉更诚实。

> 不过这一条我倾向于**应该改**：`ConcurrentHashMap` 是零成本的正确性提升，不需要验证也能确定它不比 `LinkedHashMap` 差。**有些修改不需要证据，只需要常识**——这一条属于后者。

#### #4 订阅者无异常隔离

```java
for (GatewayListener listener : listeners) {
    listener.onEvent(event);       // 没有 try/catch
}
```

**后果**：一个订阅者抛运行时异常 → 它后面的订阅者收不到事件 → 异常冒出 `Gateway.handle` → 调用方拿到异常（而响应其实已经生成完毕）。

**修法**（三种，按彻底程度排序）：

| 方案 | 代码 | 代价 |
|---|---|---|
| 隔离异常 | `try { ... } catch (RuntimeException e) { log.warn(...); }` | 事件可能丢，但主流程不受影响 |
| 异步分发 | 投递到 `BlockingQueue`，单独线程消费 | 需要队列容量与丢弃策略 |
| 事件总线 | 交给 MQ / Disruptor | 引入外部依赖与顺序问题 |

**为什么本模块不修**：见 §6.6 第（5）点——「一个不报错的监控系统，比一个会报错的监控系统更危险」。在 Demo 里，让异常暴露出来比悄悄吞掉更有教学价值。

#### #5 死代码

| 方法 | 状态 | 处理建议 |
|---|---|---|
| `RouteConfig.matches(String path)` | 无调用点 | 删，或让 `RouteTable` 用它做匹配（现在是重复实现） |
| `GatewayFilter.name()` | 5 个实现，0 个调用点 | 让它进轨迹（`1. auth: ...` 的前缀就来自它），或删 |

**为什么保留**：`name()` 的设计意图是「让轨迹自动带上前缀」，但实现时选择了在每个 filter 里手写 `"auth: "`。**两者只能留一个**——现在的状态是最差的第三种：既没有自动化的好处，又多了要维护的方法。

> 这条瑕疵是所有瑕疵里最应该立刻修的：**它不影响任何输出，删掉是零风险的。**

#### #6 每个 handler 自持时钟

```java
private final AtomicLong clock = new AtomicLong();   // 每个 CircuitBreakerHandler 一个
```

**现象**：如果两个 `CircuitBreakerHandler` 被嵌套（比如内层保护上游、外层保护整体），`advance(3)` 只推进其中一个，另一个的冷却永远不到期。

**真实踩坑记录**：写 §3.3 的装饰器顺序探针时，第一版在「错误顺序」里放了两层熔断器，各自有独立的 clock，`advance(3)` 只推外层，导致内层永不冷却，输出完全乱掉。改用「一对一」的对照结构后才稳定。

**修法**：把 `Clock` 抽成接口，由 `Gateway` 注入同一个实例。这也是真实框架（Resilience4j、Sentinel）的做法——**时间源必须可注入，否则任何涉及时间的逻辑都无法测试。**

#### #7 缓存无 TTL / 无上限

`CacheFilter` 内部是 `Map<String, GatewayResponse>`，只增不减。**长时间运行必然 OOM**——注意这和 §7.1 的 OOM 是**两种完全不同的原因**：一个是死循环，一个是无界增长。同一个异常名，两个根因，这也是排障时不能只看异常类型的原因。

修法：`Caffeine` 一行搞定，或手写 `LinkedHashMap` 的 LRU + 时间戳。**本模块没做，因为它会让「缓存命中率 57.1%」这个可复现的数字变得依赖时间**（测试跑得快慢会影响过期）。这是「可复现性」与「真实性」的一次真实取舍。

#### #8 固定窗口的 2 倍突发

`RateLimitFilter` 是固定窗口：窗口边界处，一个客户端可以在「前一窗口最后 1 毫秒」和「后一窗口第 1 毫秒」各打 3 次，共 6 次——**是配额的两倍**。

修法：滑动窗口日志、滑动窗口计数、令牌桶、漏桶。四种算法各有取舍，本模块选固定窗口的唯一理由是：**它的状态只有「当前计数」一个整数，输出最容易复现和理解。**

#### #9 熔断拒绝复用 503

```java
return GatewayResponse.reject(503, "熔断器打开（" + ... + "），请求未打到上游");
```

**现象**：上游自己返回 503 时，和熔断器拒绝时，状态码都是 503。客户端无法从状态码区分「上游挂了」和「网关在保护你」。

**为什么仍可接受**：响应体里写了原因，且 `servedBy=gateway` 可以在日志里区分。但**客户端通常只看状态码**——所以严格来说，熔断快速失败应该用 `503` + `Retry-After` 头，或用 `429`（太多请求）来区分。

**本模块的选择**：保持 503，因为「网关拒的 503」和「上游的 503」在语义上都属于「服务暂时不可用」，客户端动作相同（退避重试）。这是一个**可辩护的取舍**，不是错误。

#### #10 认证是假的

`AuthFilter` 的 token 表是硬编码的 `Map.of(...)`，没有过期、没有签名、没有刷新。它的作用只是**演示「三个 401 分支」**，任何真实认证都不该长这样。

这一条不需要展开——`AuthFilter` 的 Javadoc 里已经写明「这是演示用的固定表」。

#### 这份清单的使用方式

如果你要把这个模块当模板改造：

1. **#1、#3、#7 必须修**（并发或长跑必炸）；
2. **#4 必须修**（一个订阅者能把整个网关搞挂）；
3. **#2、#5 应该修**（成本低、收益明确）；
4. **#6、#8、#9、#10 看场景**（取舍合理，但要知道自己在取舍什么）。

**最危险的不是有瑕疵，是不知道哪里有瑕疵。** 一个 3000 行的模块有 10 条已知问题，和一个 150 行的模块有 7 条已知问题（v0），后者才是真正的技术债——因为它连「哪里可能错」都说不清。

---

### 8.4 什么时候不该用模式

这一节比前面所有「怎么用」都更重要。因为**模式用错的代价，远高于不用模式**：不用模式只是代码丑一点；用错模式会让你在错误的地方建立抽象，而这个抽象会被后来的人当成「架构」去遵守。

#### （1）七条判定：出现任何一条，先别抽象

| # | 信号 | 为什么 | 反例（本模块怎么处理的） |
|---|---|---|---|
| 1 | **只有一种实现，且说不出第二种会长什么样** | 抽象的价值来自「可替换」，一种实现无可替换 | `UpstreamHandler` 有 5 个实现，成立；如果只有 `ServiceHandler`，这个接口就是浪费 |
| 2 | **变化的是值，不是行为** | 值是参数，不是模式 | 限流 3 → 10，改构造参数（§7.2.4） |
| 3 | **你正在为「将来可能需要」写代码** | 将来的需求大概率不是你猜的那个 | 本模块没做「多租户」「权重路由」「动态配置」，只在 §7.2 说明怎么加 |
| 4 | **抽象之后没有第二个使用者** | 一个实现的接口 = 一层无收益的间接 | `GatewayFilter` 有 4 个实现，成立 |
| 5 | **需要跨层共享可变状态才能工作** | 模式会让状态流向更难追踪 | `CallContext` 是**被显式传递**的，不是 ThreadLocal（§5.1 有理由） |
| 6 | **处于性能预算极紧的热路径** | 每层间接调用都有成本 | 本模块是教学 Demo，性能不是目标；真实网关会用对象池、零拷贝、代码生成 |
| 7 | **团队里没人能解释「为什么这样分层」** | 无法维护的抽象 = 负债 | 本文档存在的意义之一，就是让第 7 条不成立 |

#### （2）过度设计的五个典型症状

这五个症状比「用了几个模式」更能说明问题：

**症状 1：为了一个 `if` 建了一个接口**

```java
// 过度设计
public interface DiscountCalculator { BigDecimal calculate(Order order); }
public class DefaultDiscountCalculator implements DiscountCalculator { ... }

// 够用
BigDecimal discount = order.total().multiply(new BigDecimal("0.9"));
```

判断标准：**删除这个接口后，有没有任何一行代码需要改？** 没有，就是过度设计。

**症状 2：配置类比代码还多**

`RateLimitConfig` + `RateLimitConfigLoader` + `RateLimitConfigListener` + `RateLimitConfigCache`，只为了读一个 `3`。

**症状 3：每个类都只有一个方法，且方法名和类名重复**

`OrderValidator.validate()`、`OrderProcessor.process()`、`OrderConverter.convert()`——如果这些类之间没有协作关系，它们只是「把函数包装成类」，不产生任何设计价值。

**症状 4：分层之后，一个简单流程要跳 8 个文件才能读完**

本模块的 `Demo 1` 轨迹有 6 行，读者要理解它需要看 10 个文件。**这个成本是付得起的，因为有 304 行输出和 4200 行文档在解释它。** 没有文档的分层，就是「把复杂度转移给读者」。

**症状 5：为了「符合设计模式」而改代码**

最典型的一句话是「这里应该用策略模式」。正确的问法是：**「这里有几个会独立变化的算法？」** 如果一个都没有，那句话只是术语崇拜。

#### （3）本模块自己的「差点过度设计」

诚实记录三个被否决的方案：

| 被否决的方案 | 当时的理由 | 否决的原因 |
|---|---|---|
| 把 `RouteTable` 也做成策略（`RouteResolver` 接口） | 「将来可能要从配置中心拉路由」 | 现在只有一种解析方式（前缀匹配）；加接口等于为一个假想需求付 80 行 |
| 把 `GatewayResponse` 做成 builder | 「字段可能会变多」 | 只有 3 个字段；`record` + 静态工厂已经够用，builder 会让每次构造多 5 行 |
| 把 `trace` 做成事件流（`TraceListener`） | 「这样更解耦」 | 轨迹是**诊断信息**，不是**业务事件**；用观察者分发轨迹会让「谁写了这行」变得难查，而它的全部价值就是「谁写的」 |

第三个最有意思：**「更解耦」不总是更好。** 轨迹的用途是排障，排障时需要的是「**确定的因果关系**」，而不是「灵活的分发」。把它做成事件，反而破坏了它唯一的用途。

> **解耦是有方向的：解掉「会独立变化的」耦合，保留「必须一起理解的」耦合。** 分不清这两者，就会把系统拆成一堆互不相关、又谁也离不开谁的文件。

#### （4）一条实用规则

如果你只有 30 秒做决定，用这条：

> **数一数「未来半年内，这段代码会因为几个互不相关的原因被修改」。**
> 0~1 个：不要抽象。
> 2 个：可以用一个接口，但别急着分文件。
> 3 个以上：按变更原因拆开，每个原因一个类——这才是模式真正要解决的问题。

本模块的六条变更理由（§3.1 那张表）就是「6 个互不相关的原因」，所以它值得 2927 行。

---

### 8.5 相似模式对比：五组最容易混的

#### （1）一张总表

| 对比 | 相同点 | 关键差别 | 一句话判别 |
|---|---|---|---|
| **责任链 vs 装饰器** | 都是「持有一个同类型对象」，都能形成链 | 责任链**可以短路**（不调用下一个）；装饰器**必须调用**（前后都要执行） | 「不往下走」合法吗？合法 → 责任链 |
| **装饰器 vs 代理** | 结构完全一样（同接口 + 持有被包装者） | 装饰器**加能力**（可叠加 N 层）；代理**控制访问**（通常 1 层） | 「能叠 3 层还合理」→ 装饰器 |
| **策略 vs 状态** | 都是「把行为委托给另一个对象」 | 策略由**外部**切换；状态由**内部**切换 | 「谁决定换」→ 外部=策略，内部=状态 |
| **观察者 vs 中介者** | 都是「多对象通信」 | 观察者是**一对多广播**（发布者不认识订阅者）；中介者是**多对多协调**（大家都认识中介者） | 「发布者需要知道订阅者是谁吗」→ 不需要=观察者 |
| **享元 vs 缓存** | 都是「共享对象，避免重复创建」 | 享元共享的是**不可变的内在状态**（同一个对象被多处引用）；缓存共享的是**计算结果**（可能过期、可能失效） | 「共享物可以变吗」→ 不可变=享元 |

#### （2）责任链 vs 装饰器：本模块同时用了两者，为什么

这是最容易混的一组，因为**代码形状完全一样**：

```java
// GatewayFilter（责任链）
public interface GatewayFilter {
    GatewayResponse doFilter(CallContext context, FilterChain.Cursor cursor);
}

// UpstreamHandler（装饰器）
public interface UpstreamHandler {
    GatewayResponse handle(CallContext context);
}
```

差别在**控制权**：

```java
// 责任链：filter 自己决定要不要继续
public GatewayResponse doFilter(CallContext ctx, Cursor cursor) {
    if (不认识这个 token) {
        return GatewayResponse.reject(401, ...);   // ← 不调 cursor.proceed()，链断了
    }
    return cursor.proceed();                        // ← 继续
}

// 装饰器：必须继续，只是"包一层"
public GatewayResponse handle(CallContext ctx) {
    GatewayResponse response = delegate.handle(ctx);   // ← 无条件往下
    metrics.record(response);                           // ← 回来之后再做事
    return response;
}
```

**为什么本模块不把它们合成一个链？**

| | 过滤器链（4 层） | 装饰器栈（4 层） |
|---|---|---|
| 关注点 | 「能不能进来」 | 「进来之后怎么活下来」 |
| 是否会短路 | 会（401/429/缓存命中） | 不会（除了熔断，且那是「拒绝」不是「跳过」） |
| 顺序敏感度 | 高（Demo 5 证明） | **极高**（3 vs 6，正确性问题） |
| 变化频率 | 高（加签名、加灰度、加审计） | 中（加超时、加降级） |
| 出问题时看哪里 | 轨迹的前几行 | 轨迹的后几行 + `attempts` |

**如果合成一个链，会失去什么？** 失去「短路」与「包裹」在代码上的区分。读代码的人看到 `return next.handle(ctx)`，无法判断这是「过滤器放行」还是「装饰器记账」——而这两者的语义差别，恰恰是本模块最想教的东西。

> **形状相同 ≠ 意图相同。** 保留两个接口，多写了 82 行（40 + 42），换来的是「一眼能看出这一层是门卫还是保镖」。

#### （3）策略 vs 状态：谁有权切换

```java
// 策略：外部换
gateway.setBalancer(LoadBalancer.roundRobin());   // 调用方决定

// 状态：内部换
breaker.record(false);   // 熔断器自己决定要不要从 CLOSED 变成 OPEN
```

一个常被问的问题：**「熔断器的状态机能不能用策略实现？」**

答案是：**它本来就是策略**（`BreakerState` 就是策略接口的变体），区别在于**谁触发切换**。`BreakerState` 的每个实现都持有 `breaker` 引用，并且自己调用 `breaker.transition(...)`——这个「自己换自己」的动作，就是状态模式与策略模式的分界线。

如果把它做成纯策略（外部决定），`CircuitBreakerHandler` 就要写：

```java
// 伪代码：外部判断
if (breaker.consecutiveFailures() >= threshold) {
    breaker.setState(new Open());
}
```

这段判断会散落在「记录成功」「记录失败」「尝试通过」三个地方，而且每加一种状态就要在三处各加一个 `if`。**把它交给状态自己，是为了让「切换规则」只有一处。**

> **策略回答「用哪种算法」，状态回答「现在处于哪个阶段」。** 前者是无状态的，后者是有记忆的。

#### （4）观察者 vs 中介者：本模块为什么没用中介者

网关里也有「多对象协调」的需求：认证、限流、路由、缓存之间要传递 `principal`、`route`、`instance`。这些信息是怎么传的？

**不是中介者，是一个显式传递的 `CallContext`。**

| | 中介者 | 本模块的 `CallContext` |
|---|---|---|
| 对象之间是否互相认识 | 都认识中介者，不认识彼此 | 都认识 `CallContext`，不认识彼此 |
| 中介者是否知道业务语义 | **知道**（`ChatRoom` 知道谁该收到哪条消息） | **不知道**（`CallContext` 只是一堆字段） |
| 新增一个参与者 | 要改中介者（加转发逻辑） | 不用改 `CallContext`（可能加一个字段） |

**关键区别**：中介者是「**行为**的协调者」，`CallContext` 是「**数据**的载体」。前者要写逻辑（谁转发给谁），后者只提供存储。

本模块用 `CallContext` 而不是中介者，理由是：**四个过滤器之间没有「互相通知」的需求，只有「后者读前者写的数据」的需求。** 用中介者会把简单的问题复杂化——你会得到一个「什么都知道、什么都不做」的上帝对象。

#### （5）享元 vs 缓存：共享物能不能变

这两个经常被混为一谈，因为都涉及 `Map`：

```java
// RouteTable（享元）：共享的是「配置」，只读
private final Map<Key, RouteConfig> pool = new ConcurrentHashMap<>();

// CacheFilter（缓存）：共享的是「响应」，会失效
private final Map<String, GatewayResponse> cache = new HashMap<>();
```

| | 享元（`RouteTable`） | 缓存（`CacheFilter`） |
|---|---|---|
| 共享物的生命周期 | 与配置同寿命（进程级） | 与 TTL 相关（秒~分钟） |
| 是否可失效 | **不应该**（失效代表配置变了） | **必须**（数据会过期） |
| 共享物的可变性 | 不可变（`RouteConfig` 是 `record`） | 不可变（`GatewayResponse` 是 `record`） |
| 命中的含义 | 「这个配置我认识」 | 「这个结果我算过」 |
| 如果命中率低 | 说明配置太多，是结构问题 | 说明 key 太散或 TTL 太短，是参数问题 |

**最容易混的一点**：两者都用 `Map`，都叫「池 / 缓存」。但享元的正确性要求是「**同一个对象必须被所有使用者共享**」（否则 `inFlight` 会分裂），而缓存的正确性要求是「**过期的对象必须不再被使用**」。

> **享元的失败是「共享错了对象」，缓存的失败是「共享了太久」。** 一个错在身份，一个错在时间。

#### （6）五条判别口诀

| 场景 | 口诀 |
|---|---|
| 想加一个「可以跳过后续步骤」的环节 | 责任链 |
| 想加一个「前后都能做事」的包装 | 装饰器 |
| 想让调用方选一种算法 | 策略 |
| 想让对象自己管自己的阶段 | 状态 |
| 想让发布者不认识消费者 | 观察者 |
| 想让一堆对象共享同一份只读数据 | 享元 |
| 想让一堆对象通过一个中间人协调 | 中介者 |
| 想控制对某个对象的访问 | 代理 |

**这八句里没有一句提到「为了解耦」。** 因为「解耦」是结果，不是目的——先想清楚「要解掉哪种变化」，模式才有意义。

---

### 8.6 一句话总结 §8

> **模式的价值不在于让你写得更多，而在于让某一种变化只影响一个文件。**
> 说不清「哪种变化」的时候，19.5 倍的行数就是纯粹的负债；
> 说得清的时候，它是你唯一的资产。

---

## 9. 面试与自测

### 9.1 十二道自测题

这些题**没有一道**是「请说出策略模式的定义」。全部是「如果改动这里，会发生什么」——因为它们考的是**因果**，不是**名词**。

---

**Q1. 为什么 `RetryHandler` 必须在 `CircuitBreakerHandler` 外面？说出两个具体后果。**

<details>
<summary>答案要点</summary>

顺序必须是 `Retry(CircuitBreaker(Service))`：

1. **熔断器看不见每一次重试**。反过来时，`CircuitBreaker` 只看到「整个重试序列的最终结果」，一次请求内的 2 次失败被算成 1 次，熔断阈值失效（Demo 8 的阈值是 2，会变成实际 4 次失败才打开）。
2. **重试会放大上游压力**。探针实测：正确顺序 3 次上游调用，错误顺序 6 次——**同样的故障、同样的重试次数，上游压力差一倍**。

补充：正确顺序下，每次重试都重新经过 `tryPass()`，所以熔断一打开，剩余的重试会被快速失败挡掉。
</details>

---

**Q2. `CacheFilter` 的 key 是 `principal + " | " + path`，为什么不带 route 的版本号？如果带上会怎样？**

<details>
<summary>答案要点</summary>

**现在不带，是因为版本还没成为「缓存隔离维度」**：`order:v1` 和 `order:v2` 在灰度期间意味着「不同的数据」，如果两个版本共用一个 key，会互相污染。

带上版本号（`u-1001 | order:v1 | /api/order`）在灰度场景下是**正确的**，代价是：

1. 灰度期间同一个用户可能命中两份缓存，内存翻倍；
2. 全量发布后旧版本的 key 要等自然过期；
3. 同一份数据被缓存两次，命中率下降。

**本模块没带**，因为它没有灰度需求。这个字段的位置（`CacheFilter` 在 `RouterFilter` 之后）恰好留出了「将来能带」的余地——这就是 §6.2 说的「不要把 cache 提到 router 前面」的原因。
</details>

---

**Q3. 为什么 `RateLimitFilter` 必须在 `CacheFilter` 之前？给一个具体的攻击场景。**

<details>
<summary>答案要点</summary>

如果顺序反过来（cache 在前）：

```text
攻击者：GET /api/order（一个必然命中缓存的热门路径）× 100000 次
```

- 每次都在 `CacheFilter` 命中并直接返回，`RateLimitFilter` **一次都不会执行**；
- 网关的 CPU 被打满，但限流器显示「0 请求」；
- 上游毫发无伤（都是缓存），**唯一的受害者是网关自己**。

Demo 6 实测了正确顺序下的行为：第 2、3 次缓存命中（`attempts=0`）**仍然消耗配额**，第 4 次照样 429。

一句话：**限流保护的是网关，不是上游**，所以它必须挡在所有「便宜路径」的前面。
</details>

---

**Q4. `MetricsHandler` 为什么在最外层？如果放在 `RetryHandler` 里面会怎样？**

<details>
<summary>答案要点</summary>

`Metrics(Retry(CircuitBreaker(Service)))`，`MetricsHandler` 在最外层，它统计的是「**逻辑调用次数**」。

如果放在 `Retry` 里面（`Retry(Metrics(...))`）：

- 一次请求触发 2 次重试 → 指标记 **2 次调用**；
- 网关收到 1 个请求，但指标显示 2 个；
- 「请求数」和「上游调用数」混在一起，**你无法回答「上游 QPS 是多少」和「用户请求量是多少」这两个不同的问题**。

正确做法是**两个指标分开**：`MetricsHandler` 记逻辑调用（在最外层），`ServiceHandler` 记物理调用（在最内层）。本模块的 Demo 10 里 `已处理请求 13` 和 `进入上游层 5 次` 就是这两个数字。
</details>

---

**Q5. `FilterChain` 为什么不持有一个 `int cursor` 字段，而是每次调用都新建 `Cursor`？**

<details>
<summary>答案要点</summary>

因为 `int cursor` 是**可变状态**，而 `FilterChain` 是**单例**（`Gateway` 只有一个）。

并发场景：

```text
线程 A：cursor=0，进入 ath，cursor 变成 1
线程 B：读到 cursor=1，直接跳过 auth，进入 rate     ← 认证被跳过
```

这是一个**静默的越权漏洞**：单线程测试全绿，上线后偶发。

`Cursor` 每次 `newCall()` 新建，游标是局部状态，天然线程安全。这就是 §5.4 的结论：**「每请求一份」的东西，不要挂在「每进程一份」的对象上。**
</details>

---

**Q6. `CircuitBreakerHandler` 为什么按实例熔断，而不是按服务熔断？**

<details>
<summary>答案要点</summary>

按服务熔断（`order` 服务一个熔断器）的问题是：**局部故障会被放大成全局故障**。

```text
order-1 磁盘满了 → 连续失败 → order 服务的熔断器打开
order-2 完全健康 → 但所有流量都被熔断器挡住，一台好机器被"连坐"
```

按实例熔断后：

```text
order-1 熔断 → 流量全部转到 order-2
order-2 也熔断 → 才认为「整个服务挂了」
```

代价是：熔断器数量 = 实例数，`breakers` map 会更大（§7.4 第 9 项提到它不会自动清理）。
</details>

---

**Q7. `GatewayResponse.upstreamFailure()` 和 `retryable()` 差在哪？删掉前者会发生什么？**

<details>
<summary>答案要点</summary>

```java
retryable()       = status >= 500
upstreamFailure() = status >= 500 && !"gateway".equals(servedBy)
```

差别是「**谁产生的 5xx**」。删掉 `upstreamFailure()`、只用 `retryable()` 会发生：

1. 熔断器拒绝的 503 被当成「可重试」；
2. `RetryHandler` 拿着同一个被拒的请求重试，被同一道门再拒；
3. 如果循环条件是 `attempts < maxAttempts`，而熔断拒绝不增加 `attempts` → **无限循环 → OOM**（§7.1 原样复现）。
</details>

---

**Q8. `HalfOpen.onFailure()` 为什么不增加 `consecutiveFailures`？**

<details>
<summary>答案要点</summary>

`consecutiveFailures` 的语义是「**CLOSED 状态下**连续失败了多少次」，它是「打开熔断的依据」。

进入 `OPEN` 之后这个数字就冻结了，作为「这次熔断是由几次连续失败触发的」这个事实被保留。`HalfOpen.onFailure()` 只做一件事：`transitionTo(new Open())`。

如果在这里 `consecutiveFailures++`，会出两个问题：

1. 熔断器在 `OPEN → HALF_OPEN → OPEN` 之间循环时，这个数字会无限增长；
2. 任何用 `consecutiveFailures >= threshold` 做判断的地方，会永远为真——熔断**再也关不上**。

**同一个字段在不同状态下表达不同含义，是状态机最常见的 bug 来源。**
</details>

---

**Q9. `RouteConfig` 为什么是 `record`，而 `UpstreamInstance` 是普通类？**

<details>
<summary>答案要点</summary>

- `RouteConfig` 是**纯数据**：路径、版本、实例列表，创建后不变 → `record` 最合适（自动 `equals/hashCode/toString`，天然适合做享元的 key）。
- `UpstreamInstance` 有**可变状态**：`inFlight` 计数器。`record` 的语义是「不可变数据载体」，把计数器塞进去等于「宣称不会变却在背后改」——`record` 的字段是 `final` 的，你只能把可变状态藏在别的对象里，反而更难懂。

判断标准：**有身份、有生命周期、有可变状态的东西，就该是普通类。**
</details>

---

**Q10. `Gateway.handle` 为什么没有 `try/catch`？**

<details>
<summary>答案要点</summary>

因为网关里有两种完全不同的「失败」：

| 类型 | 例子 | 处理方式 |
|---|---|---|
| 上游业务失败 | 上游返回 503 | 是 `GatewayResponse`，不是异常 |
| 网关自身缺陷 | NPE、`IllegalStateException` | 冒到最外层，带堆栈，由统一异常层转 500 |

如果在 `handle` 里 `catch (Exception e) { return 500; }`，你只是把「一个能定位的崩溃」换成了「一坨看不出原因的 500」。**这是负优化。**

前提是：**所有「预期内的失败」都必须被建模成返回值**（`GatewayResponse`），而不是异常。本模块做到了——`retryable()` / `upstreamFailure()` 都是基于返回值判断。
</details>

---

**Q11. `MetricsCollector` 和 `AlertListener` 消费同一批事件，为什么判定逻辑不同？**

<details>
<summary>答案要点</summary>

**因为它们回答的问题不同**：

| | `MetricsCollector` | `AlertListener` |
|---|---|---|
| 关心 | 分布（多少 2xx / 5xx） | 连续性（是不是连续出错） |
| 阈值 | 无（全量统计） | 连续 N 次 |
| 对 401 的态度 | 计数，但不代表系统故障 | **错误地当成失败**（已知瑕疵 #2） |
| 流量敏感性 | 与流量成正比 | 与流量无关 |

「连续失败」之所以比「总量阈值」更适合告警：**总量会被流量规模污染**——高峰期 500 次失败可能只是 0.5% 错误率，低峰期 20 次失败可能是全站挂掉。§6.6 有完整对比表。

但两者**必须共用同一套「什么是失败」的定义**，否则就会出现本模块的 401 噪声。这是一条真实的教训：**同一个概念在两个地方各定义一次，早晚会不一致。**
</details>

---

**Q12. 如果要把 Demo 7 的「最少连接」改成真正的并发分散，最少要改哪几处？**

<details>
<summary>答案要点</summary>

三处：

1. **`ServiceHandler` 加模拟耗时**（否则请求瞬间结束，`inFlight` 永远同时归零）——比如 `Thread.sleep(1)` 或用一个可控的闩锁；
2. **`Client` 改成多线程**（`ExecutorService` + 并发提交），否则永远是串行；
3. **验证 `inFlight` 的峰值**：打印每次选路时各实例的 `inFlight`，确认它真的分叉过。

**不需要改**的是 `LoadBalancer` 和 `UpstreamInstance`——`inFlight` 已经是 `AtomicInteger`，`acquire/release` 已经在 `try/finally` 里。**这正是策略模式 + 享元的收益：改「怎么跑」不用改「怎么选」。**

⚠ 但要注意：一旦改成多线程，`ServiceHandler` 里的 `LinkedHashMap`（`callSeq`、`remainingFailures`）就不再安全，`breakers` 也一样。**并发不是「加个线程池」就完事的事。**
</details>

---

### 9.2 三道推导题（没有标准答案）

**D1. 需求：按用户 ID 灰度 10% 流量到 `order:v2`，其余走 `v1`。改哪里？**

<details>
<summary>参考思路</summary>

三个候选方案，各有代价：

| 方案 | 改哪里 | 代价 |
|---|---|---|
| 在 `RouterFilter` 里按 `principal.hashCode() % 10 == 0` 选版本 | 改 `RouterFilter`（+8 行） | 路由决策混进过滤器，不可复用 |
| 新增 `VersionSelector` 策略，`RouterFilter` 委托它 | 新增 1 个接口 + 2 个实现，改 `RouterFilter` 一行 | 多一层抽象，但灰度规则可换（按 IP / 按 header / 按白名单） |
| 在 `RouteTable` 里放两份配置，`RouterFilter` 拿到「候选列表」再选 | 改 `RouteTable` 的 key 设计 | 影响面最大，但最贴近真实网关的做法 |

关键判断：**灰度规则会不会变？** 会（从 10% 到 50%、从按用户到按地域）→ 用方案 2。永远只有这一种规则 → 方案 1 够用。

**注意一个隐藏问题**：切到 v2 之后，`CacheFilter` 的 key 里没有版本号（Q2），两个版本的数据会互相污染。所以这个需求**必须同时改缓存 key**——这就是「一个需求落在一个模式上，却牵动另一个模式」的典型例子。
</details>

---

**D2. 需求：上游返回 200，但响应体里有 `{"code": 500}`。怎么处理？**

<details>
<summary>参考思路</summary>

这是真实世界最常见、也最容易做错的一类：**HTTP 状态码撒谎**。

三个层次的答案：

1. **不要试图在网关里解析业务响应体**。网关不应该知道业务 schema，否则每个服务改字段都要改网关。
2. **让上游自己把错误映射成正确的状态码**（这是服务端契约问题，不是网关问题）。
3. 如果无法推动上游改，只能在网关加一个「响应体错误码提取器」——那是一个**业务耦合点**，必须显式标注、可配置、可关闭。

面试里这题想听的是第 1 点：**知道边界在哪里，比知道怎么实现更重要。**
</details>

---

**D3. 需求：限流要按「接口」维度，而不是 IP。改哪里？**

<details>
<summary>参考思路</summary>

`RateLimitFilter` 的 key 从 `ip` 换成 `ip + " | " + path`（或 `principal + " | " + path`）。**一行。**

但要想清楚三件事：

1. **为什么还要保留 IP 维度？** 只有接口维度时，单个 IP 可以打遍所有接口。真实网关是「IP 粗限 + 接口细限」两层。
2. **容量怎么定？** 每个接口的合理 QPS 不同（`/api/order` 和 `/api/report` 差 100 倍），所以需要「按路径配置容量」，这就从「一个 int」变成了「一张表」——**这才是真正的工作量，不是改 key**。
3. **爆炸半径**：key 从「N 个 IP」变成「N 个 IP × M 个接口」，`used` map 会大 M 倍，需要清理策略。

这道题的考点是：**「改一行」和「改对」之间的差距，通常不在代码，而在你没想清楚的那三件事。**
</details>

---

### 9.3 面试时怎么讲这个案例

**不要这样说**（听起来像背课文）：

> 「我用到了责任链、策略、装饰器、状态、观察者和享元六个设计模式，实现了过滤器链、负载均衡……」

**可以这样说**（先讲问题，再讲取舍）：

> 「我做过一个网关的请求管道。第一版是一个 150 行的 `handle` 方法，能跑，但我写了一个反例探针，跑出来五个问题：缓存越权、失败响应被缓存、抖动即熔断、熔断后永不恢复、三种 401 无法区分。
>
> 我把它们归成六类**互不重叠的变更理由**：能不能进来、打哪台、怎么保护、此刻是否放行、谁消费结果、共享什么。每一类一个抽象，一共六个模式。
>
> 代价是代码从 150 行涨到 1329 行（去掉注释和演示代码）。我算过账：多出来的行数买到的是『顺序可验证』和『失败可归因』——比如我能用一段 22 行的输出证明『重试在熔断外面』和『重试在熔断里面』会让上游压力差一倍。
>
> 我也不认为所有场景都该这么做。如果只有一种选路算法、只有一个监控消费者，那两个抽象就是负债。判断标准是『这段代码会因几个互不相关的原因被改』。」

这段话里有四个东西是面试官想听的：

1. **有反例**（不是一上来就设计）；
2. **有数字**（150 → 1329，3 vs 6）；
3. **有取舍**（明确说了什么时候不该用）；
4. **有验证方法**（探针、可复现输出）。

### 9.4 自评表：你在哪一层

| 层级 | 表现 | 对应本文档 |
|---|---|---|
| **L1 能解释** | 能说出六个模式各自的定义和在模块里的位置 | §1、§5 |
| **L2 能推导** | 能回答 Q1-Q12，并说清「反过来会怎样」 | §3、§4、§6 |
| **L3 能设计** | 给出一个新需求（D1-D3），能判断该不该加抽象、加在哪、代价是什么 | §7、§8 |
| **L4 能排障** | 拿到一段异常输出，能定位到具体是哪个模式协作出了问题 | §7.1 |
| **L5 能取舍** | 能说出「这个模块有 10 个已知瑕疵，其中 3 个必须修，7 个可以接受，因为……」 | §8.3 |

**大多数人停在 L1。** L1 靠背，L2 靠读代码，L3 靠做项目，L4 靠踩坑，L5 靠**在真实压力下做过决定**。

本文档能把你带到 L3。L4 和 L5 只能靠你自己去写、去崩、去修——**但至少你现在知道「崩了该看哪里」了。**

---

## 附录 A. 文件清单

### A.1 24 个源文件与 35 个编译单元

| # | 文件 | 物理行 | 类别 | 关键成员 |
|---|---|---|---|---|
| 1 | `Gateway.java` | 256 | 组装 | `handle`、`Result`、`addListener`、`advance`、`describeTopology` |
| 2 | `Client.java` | 475 | 演示 | `main` + 10 个 `demoN()` |
| 3 | `GatewayRequest.java` | 60 | 载体（不可变） | `record(requestId, method, path, authorization, clientIp)` |
| 4 | `GatewayResponse.java` | 114 | 载体（不可变） | `ok` / `reject` / `upstreamError`、`retryable()`、`upstreamFailure()` |
| 5 | `CallContext.java` | 194 | 载体（可变，每请求一份） | `principal`、`route`、`instance`、`attempts`、`trace` |
| 6 | `UpstreamInstance.java` | 95 | 享元参与者 | `inFlight`（`AtomicInteger`）、`acquire` / `release` |
| 7 | `RouteConfig.java` | 80 | 享元本体（`record`） | `key()`、`instances()`、`matches()`（未使用，§8.3 #5） |
| 8 | `RouteTable.java` | 159 | 享元工厂 | `resolve(path)`、`poolStats()`、`Key`、`Blueprint` |
| 9 | `LoadBalancer.java` | 128 | 策略 | `pick`、`pickExcluding`（默认方法）、`RoundRobin`、`LeastConnections` |
| 10 | `UpstreamHandler.java` | 42 | 装饰器抽象 | `handle`、`describe` |
| 11 | `ServiceHandler.java` | 85 | 装饰器（最内层） | 故障注入、`markAttempt`、`try/finally` 释放 `inFlight` |
| 12 | `RetryHandler.java` | 76 | 装饰器 | 本地 `tried` 计数、`upstreamFailure()` 判据 |
| 13 | `MetricsHandler.java` | 46 | 装饰器 | 逻辑调用计数（`calls`） |
| 14 | `CircuitBreaker.java` | 231 | 状态 | `sealed interface BreakerState` + `Closed`/`Open`/`HalfOpen` |
| 15 | `CircuitBreakerHandler.java` | 136 | 装饰器 + 状态宿主 | `breakerOf`、`tryPass`、`record`、`advance` |
| 16 | `GatewayFilter.java` | 40 | 责任链抽象 | `doFilter(ctx, cursor)`、`name()`（未使用） |
| 17 | `FilterChain.java` | 117 | 责任链调度 | 每请求一个 `Cursor`、`proceed()` |
| 18 | `AuthFilter.java` | 60 | 责任链节点 | 三个 401 分支 |
| 19 | `RateLimitFilter.java` | 89 | 责任链节点 | 固定窗口、`snapshot()`、`rollWindow()` |
| 20 | `RouterFilter.java` | 70 | 责任链节点 | 路径 → 路由 → 实例（调用策略） |
| 21 | `CacheFilter.java` | 122 | 责任链节点 | key 含主体、只缓存 GET 成功、`stats()` |
| 22 | `GatewayListener.java` | 73 | 观察者抽象 | `onEvent`、`record Event`（含 `describe`） |
| 23 | `MetricsCollector.java` | 99 | 观察者实现 | 状态码分布、来源分布、缓存命中 |
| 24 | `AlertListener.java` | 80 | 观察者实现 | 连续失败阈值、触发后归零 |

**总数核对**：`2927` 行，`115589` 字节，`35` 个 `.class`（`102034` 字节）。

11 个嵌套类型分别是：`CircuitBreaker$BreakerState/Closed/Open/HalfOpen`（4）、`FilterChain$Cursor`、`Gateway$Result`、`GatewayListener$Event`、`LoadBalancer$RoundRobin/LeastConnections`（2）、`RouteTable$Key/Blueprint`（2）。

### A.2 依赖方向

本模块**没有任何第三方依赖**，只有 `java.util.*` 和 `java.util.concurrent.atomic.*`。依赖方向是单向的：

```text
                    Client（只依赖 Gateway 与少数公开类型）
                       │
                       ▼
                    Gateway ──────────────┐
                    │      │              │
        FilterChain │      │ UpstreamHandler（装饰器栈）
                    ▼      ▼              │
            GatewayFilter  MetricsHandler │
              │  │  │  │        │         │
      ┌───────┘  │  │  └────────┼─────────┘
      ▼          ▼  ▼           ▼
  AuthFilter  Rate  Router   RetryHandler
                  Limit  │         │
                         ▼         ▼
                    RouteTable  CircuitBreakerHandler
                    LoadBalancer      │
                         │            ▼
                         └────► CircuitBreaker（状态）
                                      │
                                      ▼
                                 ServiceHandler
                                      │
                                      ▼
                                UpstreamInstance

  横切（被几乎所有层使用）：CallContext、GatewayResponse、GatewayRequest
  旁路（只被 Gateway 与 Client 使用）：GatewayListener、MetricsCollector、AlertListener
```

**三条值得注意的依赖规则**：

1. **过滤器不依赖装饰器**。`RouterFilter` 只负责把选中的实例写进 `CallContext`，它不知道后面还有熔断和重试。两者的唯一交点是一个字段。
2. **装饰器不依赖过滤器**。`RetryHandler` 不知道请求是怎么通过认证的，它只看 `GatewayResponse`。
3. **`CallContext` 被所有层依赖，但它不依赖任何层**。它是整个模块里唯一「零出边」的类——这是它能安全地当「共享载体」的原因。

### A.3 与 `orderservice` 组合案例的关系

| | `orderservice` | `apigateway` |
|---|---|---|
| 模式数 | 5（策略、工厂、装饰器、责任链、组合） | 6 |
| 业务域 | 订单结算（有真实业务语义） | 网关（无业务语义，只有管道） |
| 核心命题 | 「模式怎么服务业务规则」 | 「模式怎么组成一条有序的管道」 |
| 读者应先读哪个 | 先读 | 后读 |

`orderservice` 讲的是「**业务里的变化**」，`apigateway` 讲的是「**基础设施里的变化**」。前者让你理解「为什么要抽象」，后者让你理解「抽象之间怎么排顺序」。

---

## 附录 B. 编译运行与验证记录

### B.1 环境

```text
OS      : Windows 26H2
Shell   : PowerShell 8（pwsh）
JDK     : openjdk 25.0.4.1 2026-08-18 LTS
          OpenJDK Runtime Environment Microsoft-14951867 (build 25.0.4.1+1-LTS)
          64-Bit Server VM
javac   : --release 25 -encoding UTF-8 -Xlint:all
```

### B.2 编译

```powershell
$JDK = 'F:\enCodingApps\JDK\jdk-25'
$src = 'E:\codeFile\codeFather\design_patterns_pro\src'
$out = 'E:\codeFile\codeFather\design_patterns_pro\out'

& "$JDK\bin\javac.exe" --release 25 -encoding UTF-8 -Xlint:all -d $out `
    (Get-ChildItem -Recurse "$src\com\patterns\combined\apigateway\*.java").FullName
```

| 项 | 结果 |
|---|---|
| 退出码 | **0** |
| 警告 | **0 行**（`-Xlint:all` 全开） |
| 产物 | 35 个 `.class` / 102034 字节 |

### B.3 运行

```powershell
& "$JDK\bin\java.exe" '-Dstdout.encoding=UTF-8' -cp $out com.patterns.combined.apigateway.Client
```

| 项 | 结果 |
|---|---|
| 退出码 | **0** |
| stderr | **0 字节** |
| stdout | **304 行 / 16085 字节** |
| SHA-256 | `E4888CC8F3ACB967D4E9BFD0F712198BF8B5D46B54AC0AABC5A7CF4CD9B800E6` |

### B.4 可复现性

连续运行两次，逐字节一致：

```text
第 1 次：E4888CC8F3ACB967D4E9BFD0F712198BF8B5D46B54AC0AABC5A7CF4CD9B800E6
第 2 次：E4888CC8F3ACB967D4E9BFD0F712198BF8B5D46B54AC0AABC5A7CF4CD9B800E6
结论：REPRODUCIBLE = YES
```

**注意 PowerShell 的一个坑**：直接 `& java ... > out.txt` 时，PowerShell 会用控制台的默认编码解码子进程输出，中文会变成乱码（`姝ｅ父璇锋眰` 这种）。要拿到正确的中文，必须先：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
```

这也是为什么本文档所有「字节数」和「SHA」都注明是「`-Dstdout.encoding=UTF-8` + 正确重定向」的口径。

### B.5 三个取证探针

| 探针 | 位置 | 行数 | 输出 | 用途 | 证据强度 |
|---|---|---|---|---|---|
| `LegacyGatewayV0` | `design_patterns/_probe/apigw_v0/` | 150 | 28 行 / 1853 字节<br>SHA `6899188F…` | v0 的五类缺陷（§2.2） | B |
| `WrappingOrderProbe` | 同上 | 90 | 35 行 | 装饰器顺序 3 vs 6（§3.3） | B |
| `RetryLoopProbe` | 同上 | 132 | 22 行 | 重试循环不终止（§7.1） | B |

三个探针都**不放在 `src` 下**，因为它们只是取证工具，不参与模块统计。它们的源码可以单独编译运行：

```powershell
# 例：重试循环探针（依赖 apigateway 的 GatewayResponse）
& "$JDK\bin\javac.exe" -encoding UTF-8 -cp $out -d $out `
    'e:\codeFile\codeFather\design_patterns\_probe\apigw_v0\RetryLoopProbe.java'
& "$JDK\bin\java.exe" '-Dstdout.encoding=UTF-8' -cp $out RetryLoopProbe
```

### B.6 明确没有做的验证

| 项 | 状态 |
|---|---|
| 并发压测 | **未做**（无线程池） |
| 真实 HTTP 往返 | **未做**（`ServiceHandler` 是内存模拟） |
| 长时间运行（内存增长） | **未做**（只跑 13 个请求） |
| 多实例熔断隔离 | **未做**（Demo 8 单实例） |
| JVM 参数调优 / GC 行为 | **未做** |

这五项在 §7.4 有逐条说明「如果做了会暴露什么」。**本模块的所有「验证通过」只针对单线程、内存模拟、13 个请求的范围。**

### B.7 输出的一次小插曲（诚实记录）

本文档第一次尝试抓取 stdout 时，因为 PowerShell 重定向的编码问题，得到了一份**中文全部乱码**的 304 行输出。它和正确输出的行数完全一样——**如果当时只核对行数，这个错误会被漏掉**。

这也是为什么验收标准是**三条**（退出码 / 行数 / 字节数 + SHA），而不是一条。**一个数字对得上，不代表整份输出对得上。**

---

## 附录 C. 交叉引用

### C.1 本模块用到的六个单模式

| 模式 | 单模式文档 | 本模块对应的类 | 与本模块的差别 |
|---|---|---|---|
| 责任链 | [`behavioral/chainofresponsibility`](../../behavioral/chainofresponsibility/README.md) | `GatewayFilter` + `FilterChain` | 单模式示例通常只有 2~3 个处理器；这里强调**每请求游标**与**短路位置** |
| 策略 | [`behavioral/strategy`](../../behavioral/strategy/README.md) | `LoadBalancer` | 单模式示例讲「可替换」；这里多讲一个 `pickExcluding` 默认方法 |
| 装饰器 | [`structural/decorator`](../../structural/decorator/README.md) | `UpstreamHandler` 的四个实现 | 单模式示例只讲「加功能」；这里讲**顺序是正确性问题** |
| 状态 | [`behavioral/state`](../../behavioral/state/README.md) | `CircuitBreaker` | 单模式示例常用「订单状态」；这里是**带自动恢复**的状态机 |
| 观察者 | [`behavioral/observer`](../../behavioral/observer/README.md) | `GatewayListener` + 2 个订阅者 | 单模式示例讲「通知」；这里讲「**同一批事件、两种聚合**」 |
| 享元 | [`structural/flyweight`](../../structural/flyweight/README.md) | `RouteTable` + `RouteConfig` | 单模式示例讲「省内存」；这里讲**共享的可变状态必须唯一**（`inFlight`） |

### C.2 另一个组合案例

| 文档 | 关系 |
|---|---|
| [`../orderservice/README.md`](../orderservice/README.md) | 同为组合案例，5 个模式，业务驱动。**建议先读它，再读本模块** |

### C.3 项目级文档

| 文档 | 本模块在其中的位置 |
|---|---|
| [`docs/01-learning-path.md`](../../../../../docs/01-learning-path.md) | 组合案例阶段（第二阶段）的第二个样本 |
| [`docs/02-pattern-comparison.md`](../../../../../docs/02-pattern-comparison.md) | 六组相似模式的对比，§8.5 是它的实战版 |
| [`docs/03-optimization-log.md`](../../../../../docs/03-optimization-log.md) | 本模块的建立记录 |
| [`docs/05-verification.md`](../../../../../docs/05-verification.md) | 模块统计与验证矩阵 |

### C.4 与「命令模式」的一次擦肩

单模式模块里有 [`behavioral/command`](../../behavioral/command/README.md)（任务调度）。它和本模块的「过滤器链」看起来都像「把请求包装起来排队执行」，但意图完全不同：

| | 命令模式 | 本模块的过滤器链 |
|---|---|---|
| 请求的生命周期 | **被存储、被排队、可撤销** | 立即执行，不存储 |
| 是否可重放 | 可以（命令对象持有全部参数） | 不可以（`CallContext` 用完即弃） |
| 核心价值 | 把「请求」变成一等公民 | 把「处理步骤」变成可插拔的环节 |

**如果你要做的是「异步任务队列」，该看的是命令模式，不是这里。**

---

## 附录 D. 术语表

| 术语 | 本文档里的含义 | 容易混淆的地方 |
|---|---|---|
| **principal** | 认证后的调用方身份（本例是 `u-1001`） | 不是 `token`，也不是 `userId`——它是「认证结果」的抽象 |
| **短路（short-circuit）** | 某一层直接返回，后续层不执行 | 不是「快速返回」——重点是**没执行**，而不是**执行得快** |
| **attempts** | 真实打到上游的次数 | 不是「重试次数」（不含首次），也不是「逻辑请求数」 |
| **in-flight** | 已发出但未收到响应的请求数 | 不是「QPS」，也不是「连接数」 |
| **tick** | 逻辑时钟的一次推进 | 不是毫秒；本模块用 `advance(3)` 代替「等 3 秒」 |
| **half-open** | 熔断后的试探状态 | 不是「半开半闭」——它是**只允许少量流量通过**的状态 |
| **快速失败（fail-fast）** | 熔断打开时立即返回 503，不打上游 | 不是「提前校验参数」的那个 fail-fast |
| **upstreamFailure** | 上游真的失败（排除网关自身拒绝） | 不是 `retryable()`，后者只看状态码 |
| **servedBy** | 谁产生了这个响应（`gateway` 或实例名） | 不是「谁处理的请求」——在缓存命中时，它是**原始生成者** |
| **内在状态 / 外在状态** | 享元里「可共享的」/「每实例不同的」 | 本模块的 `RouteConfig` 是内在状态，`CallContext` 是外在状态 |
| **背压（backpressure）** | 消费方来不及处理时，让生产方减速 | 不是「限流」——限流是主动拒绝，背压是传导压力 |
| **单调时钟** | 只前进、不受系统时间调整影响的时钟（`nanoTime`） | 不是 `currentTimeMillis`（会被 NTP 调整、可能回退） |
| **sealed interface** | 只允许指定类型实现的接口 | 不是 `final class`；它约束的是「谁能实现我」 |
| **record** | 不可变数据载体（自动生成 `equals/hashCode/toString`） | 不是「简化的 class」；它的语义是**值**，不是**实体** |

---

## 附录 E. 缺陷取证台账

把散落在全文各处的缺陷集中到一张表，方便核对。

### E.1 v0 的七条缺陷（§2）

| 编号 | 缺陷 | 证据 | 修法 | 状态 |
|---|---|---|---|---|
| V0-1 | 缓存越权（key 只用 path） | 探针 A 段：`u-1002` 拿到 `data#1` | key 加 `principal` | 已修（`CacheFilter`） |
| V0-2 | 失败响应被缓存 | 探针 B 段：`{/api/order/c=503}` | 只缓存 2xx | 已修（`CacheFilter`） |
| V0-3 | 成功后不清零失败计数 | 探针 C 段：50% 成功率仍熔断 | `Closed.onSuccess` 清零 | 已修（`CircuitBreaker`） |
| V0-4 | 熔断打开后永不恢复 | 探针 C 段：第 5、6 次仍 `circuit open` | 三态状态机 + 冷却 | 已修（`CircuitBreaker`） |
| V0-5 | 三种 401 无法区分 | 探针 F 段：三行逐字相同 | 三个分支三句话 | 已修（`AuthFilter`） |
| V0-6 | 选路算法硬编码 | 代码可证（`rrCursor++ % 2`） | 抽出 `LoadBalancer` | 已修（策略） |
| V0-7 | 加监控必须改网关 | 代码可证（无监听器） | 抽出 `GatewayListener` | 已修（观察者） |

### E.2 开发过程中修掉的实现缺陷

| 编号 | 缺陷 | 发现方式 | 修法 | 状态 |
|---|---|---|---|---|
| FIX-1 | **重试无限循环 → OOM** | 第一次运行 Client 崩在 c-3 | `upstreamFailure()` + 本地 `tried` 计数（§7.1） | 已修 |
| FIX-2 | `OPEN → HALF_OPEN` 无轨迹 | 逐行核对 Demo 8 输出 | `tryPass()` 前后拍快照（§6.5） | 已修 |
| FIX-3 | 熔断器缺 `coolDownTicks()` 访问器 | 写轨迹时编译失败 | 新增访问器 | 已修 |
| FIX-4 | 缓存未命中无轨迹 | 读代码发现 miss 分支不写 trace | 补一行 trace（§5.5） | 已修 |
| FIX-5 | `Map.copyOf` 迭代顺序不稳定 | 担心 SHA 不可复现 | 改 `TreeMap`（限流快照、指标） | 已修 |
| FIX-6 | 死分支 `else if (!after.equals(before) && afterPass.equals(before))` | 读代码发现永不成立 | 删除 | 已修 |
| FIX-7 | `maxAttempts=1` 时仍打印「已尝试 N 次」 | 核对 Demo 8 输出 | 加 `maxAttempts > 1` 判断 | 已修 |
| FIX-8 | Demo 6 措辞与快照值矛盾（「已到 3 上限」vs `=4`） | 逐行核对输出 | 改成「计数涨到 4」（§6.3） | 已修 |
| FIX-9 | `println` 里混入 HTML 标签 `<b>` | 逐行核对输出 | 删除标签 | 已修 |

### E.3 刻意保留的十条瑕疵（§8.3）

| 编号 | 瑕疵 | 严重度 | 为什么保留 |
|---|---|---|---|
| IMP-1 | `HALF_OPEN` 不限制探路并发 | **高** | 修了需要并发测试才能证明；没验证的实现比已知缺陷更危险 |
| IMP-2 | 告警把 401 当失败 | 中 | 刻意保留的反例，让读者看到错误判据的真实后果 |
| IMP-3 | `breakers` 非线程安全 | **高** | 单线程下无法验证；但**这一条应该改**（常识性修复） |
| IMP-4 | 订阅者无异常隔离 | 中 | 让异常暴露，避免「不报错的监控系统」 |
| IMP-5 | 死代码（`matches` / `name`） | 低 | 记录在案，读者可自行删除 |
| IMP-6 | 每个 handler 自持时钟 | 低 | 单 handler 场景无影响；已记录真实踩坑 |
| IMP-7 | 缓存无 TTL / 无上限 | **高** | 加了 TTL 会让「命中率 57.1%」依赖运行速度 |
| IMP-8 | 固定窗口 2 倍突发 | 中 | 选它是为了「状态只有一个整数」，便于复现 |
| IMP-9 | 熔断拒绝复用 503 | 低 | 语义上可辩护（都表示「暂时不可用」） |
| IMP-10 | 认证是假的 | 中 | 只为演示「三个 401 分支」 |

### E.4 三个数字，三种证据强度

| 数字 | 来源 | 强度 |
|---|---|---|
| `13 / 5 / 4+3+2+4` | Demo 10 输出，可逐项回溯 | **A** |
| `3 次 vs 6 次` | `WrappingOrderProbe` | **B** |
| `50000 轮 / 1.6 MB` | `RetryLoopProbe` | **B** |

**没有一个数字来自「我觉得应该差不多」。**

---

## 附录 F. 版本与约定

### F.1 证据强度分级（全文通用）

| 级别 | 定义 | 是否允许出现「一定」「必然」 |
|---|---|---|
| **A · 实测** | 有真实运行输出，可复现，SHA 固定 | 允许 |
| **B · 探针实测** | 独立探针程序跑出来的 | 允许（限定在该探针的场景内） |
| **C · 代码可证** | 读代码即可确认，无需运行 | 允许（限定「代码怎么写」而非「运行会怎样」） |
| **D · 逻辑推理** | 说明原理，但本 Demo 没跑 | **不允许**，必须写「逻辑上」「理论上」 |
| **E · 明确未验证** | 知道存在，没测 | **不允许**，必须写「未验证」 |

本文档里所有 A/B 级结论，都能在 §6 或附录 B 找到对应的命令与输出。

### F.2 文档约定

| 约定 | 说明 |
|---|---|
| 数字一律用真实统计 | 行数、字节数、SHA 全部来自 `Get-Content` / `Get-FileHash` 的实际结果 |
| 输出块一律标 `text` | 便于区分「程序输出」与「代码示例」 |
| 代码块标明来源文件 | 节选必须能回到原文件 |
| 章节锚点与目录一致 | 否则 `audit_docs.ps1` 会报 `BAD_ANCHOR` |
| 不使用 emoji | 除 `⚠`（用于标注「需要特别注意」） |

### F.3 代码约定

| 约定 | 示例 |
|---|---|
| 类注释必须有「为什么」 | 不只写「这是什么类」 |
| 关键决策写「为什么不是另一种写法」 | 如 `RetryHandler` 为什么用 `tried` |
| 浮点格式化用 `Locale.ROOT` | 避免不同区域设置输出 `69,2%` 或 `69.2%` |
| 不依赖 `HashMap` 的迭代顺序 | 需要顺序时用 `TreeMap` / `LinkedHashMap` |
| 时间用 tick（可注入） | 不直接调 `System.currentTimeMillis()` |
| 每请求状态放 `CallContext` | 不放 `static`、不放 `ThreadLocal` |

### F.4 数字总表（便于核对）

| 项 | 值 |
|---|---|
| 源文件 | 24 个 `.java` |
| 物理行 | 2927 |
| 源码字节 | 115589 |
| 注释行 | 1250（42.7%） |
| 代码行 | 1677 |
| 编译产物 | 35 个 `.class` / 102034 字节 |
| 运行输出 | 304 行 / 16085 字节 |
| 输出 SHA-256 | `E4888CC8F3ACB967D4E9BFD0F712198BF8B5D46B54AC0AABC5A7CF4CD9B800E6` |
| v0 基线 | 150 行 / 28 行输出 / 1853 字节 / SHA `6899188F…` |
| 膨胀比（物理行） | 19.5× |
| 膨胀比（代码行，去掉 Client） | 10.8× |

### F.5 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-24 | 建立模块（24 个类），修 FIX-1 至 FIX-9，完成 §0-§9 与附录 A-F |
| 2026-10-24 | 新增 `RetryLoopProbe`（§7.1 的最小复现），补充 §8.2 的行数拆分与 §8.3 的瑕疵台账 |

### F.6 最后一句

> 这份文档里最重要的不是那六个模式，
> 而是 **§7.1 那次 OOM、§8.3 那十条瑕疵、§7.4 那十项未验证**。
>
> 因为它们才是真实的工程：
> **一半是设计，一半是知道自己哪里还没设计好。**
