# 单例模式：通俗入门

> **第一层「入门」材料**：先讲清「为什么全局只能有一份」，再谈怎么保证。
> 深入的双检锁、静态内部类、枚举、反射与序列化攻击，见 [README.md](README.md)。
>
> 可运行代码：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)

---

## 1. 一句话说人话

**单例就是：这个类只允许存在一个对象，谁要用都找它拿，不许自己再造一个。**

### 什么需求会让开发者想到它

一个订单系统启动时要读 `config.properties`：里面有超时时间、重试次数、开关。
最直接的写法是：

```java
ConfigFile orderConfig = new ConfigFile();    // 订单服务读一次
ConfigFile couponConfig = new ConfigFile();   // 优惠券服务又读一次
```

一开始没问题。直到某天运营在后台把「超时时间」从 10 秒改成 30 秒，
订单服务仍然按 10 秒超时——因为它手里是**另一份配置对象**。

这不是「配置读得慢」的问题，而是**同一份事实在内存里出现了多个副本**。
单例要解决的就是：某些东西，整个进程只应该有一份。

---

## 2. 先看不使用单例的代码

完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`：

```java
class ConfigFile {

    static int loadCount;
    private int timeoutSeconds;

    ConfigFile() {
        loadCount++;
        timeoutSeconds = 10;
        System.out.println("  [配置] 读取 config.properties（第 " + loadCount + " 次）");
    }

    int timeoutSeconds() { return timeoutSeconds; }
    void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
}
```

两个服务各自 `new`：

```java
ConfigFile orderConfig = new ConfigFile();
ConfigFile couponConfig = new ConfigFile();

couponConfig.setTimeoutSeconds(30);
System.out.println(orderConfig.timeoutSeconds());   // 仍然是 10
```

真实输出：

```text
  [配置] 读取 config.properties（第 1 次）
  [配置] 读取 config.properties（第 2 次）
订单服务读到 timeout = 10 秒
优惠券服务把 timeout 改成 30 秒
订单服务再读 timeout = 10 秒
配置被读取 2 次
```

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
OrderService  ──new──▶ ConfigFile（副本 A）
CouponService ──new──▶ ConfigFile（副本 B）
```

两个服务都以为自己在用「配置」，实际上各用各的副本。

### 3.2 问题出在哪一行

问题不在 `setTimeoutSeconds`，而在**构造函数是 public 的**：

```java
ConfigFile() { ... }     // 谁都可以调用，副本数量不可控
```

### 3.3 新增需求会怎样

| 需求 | 要改哪里 | 结果 |
|---|---|---|
| 再加一个支付服务读配置 | 支付服务里再 `new ConfigFile()` | 又多一份副本 |
| 让所有服务看到同一个开关 | 把引用层层传下去 | 每个调用链都要改 |
| 保证只读一次文件 | 在构造器里加静态标记 | 仍然是「靠自觉」，挡不住 new |

**为什么加个 `static` 字段不够？** 如果字段是 static、构造器仍公开，
别人依旧可以 `new ConfigFile()` 造出第二份对象，只是没人用它——问题从「多份」
变成了「不知道哪份才算数」，更难查。

---

## 4. 从问题推导出设计

### 第 1 步：把构造器藏起来

```java
private ConfigFile() { ... }
```

**为什么？** 这是「唯一」的第一道保证：类外部再也不能 `new`。
**不加会怎样？** 无论你怎么写 `getInstance()`，别人都能绕过去再造一个。

### 第 2 步：类自己创建唯一实例

```java
private static final AppConfig INSTANCE = new AppConfig();

static AppConfig getInstance() {
    return INSTANCE;
}
```

**为什么由类自己创建？** 构造器已经私有了，外部造不出来，只能由类内部造。
**为什么用 `static final`？** 它属于类，不属于某个对象；`final` 保证引用不会再指向别人。
**为什么不加会怎样？** 没有实例，`getInstance()` 没有东西可返回。

### 第 3 步：所有人统一走 `getInstance()`

```java
AppConfig first = AppConfig.getInstance();
AppConfig second = AppConfig.getInstance();
System.out.println(first == second);   // true
```

**为什么这样就一致了？** 因为两个变量指向堆上的同一个对象；改 `second` 的字段，
`first` 读到的自然是新值。

最终关系：

```text
OrderService ─┐
              ├──▶ AppConfig.getInstance() ──▶ 唯一的 AppConfig 实例
CouponService ┘
```

真实输出：

```text
  [配置] 读取 config.properties（只读一次）
两次 getInstance 是同一个对象吗？true
订单服务读到 timeout = 10 秒
优惠券服务把 timeout 改成 30 秒
订单服务再读 timeout = 30 秒
配置构造发生了 1 次
```

### 第 4 步（可选）：延迟创建 / 线程安全

上面的写法在类加载时就创建，**天生线程安全**，适合配置这种启动就要用的对象。
如果对象很大、可能一直用不到，才需要「第一次调用时再创建」；那就必须处理多线程同时
首次调用的问题（`synchronized`、静态内部类、枚举等）。这些是**实现细节**，不是单例的
定义本身，见深度文档。

---

## 5. 用执行过程理解代码

按时间顺序：

1. `AppConfig` 类第一次被使用时，JVM 执行 `private static final AppConfig INSTANCE = new AppConfig();`
   → 打印一次「读取 config.properties」；
2. `first = getInstance()` 直接返回 `INSTANCE`，没有新建；
3. `second = getInstance()` 返回同一个引用，所以 `first == second` 为 `true`；
4. `second.setTimeoutSeconds(30)` 改的是 `INSTANCE` 的字段；
5. `first.timeoutSeconds()` 读的也是 `INSTANCE`，所以是 30。

**最容易误解的一行**：

```java
private static final AppConfig INSTANCE = new AppConfig();
```

它不是在 `getInstance()` 里执行的，而是在**类初始化**时执行一次。
所以 `getInstance()` 本身没有 `new`，也不需要考虑「每次调用都创建」。

---

## 6. 改造前后对比

| 新增需求 | 改造前 | 改造后 |
|---|---|---|
| 加一个支付服务读配置 | 再 `new` 一份，副本 +1 | 调 `getInstance()`，拿同一份 |
| 修改配置后所有服务生效 | 需要把引用传来传去 | 自动生效（因为只有一份） |
| 保证只加载一次文件 | 靠调用方自觉 | 构造器私有，类只初始化一次 |
| 单元测试替换配置 | 直接 new 一个假的 | 需要额外手段（反射/依赖注入），更麻烦 |

### 代价

- **全局可变状态**：任何调用方都能改，改了影响所有人；
- **测试污染**：多个测试共享同一实例，执行顺序可能互相影响；
- **隐藏依赖**：方法签名里看不到它用了配置，阅读代码时容易漏；
- **并发写**：单例只保证「一个对象」，不保证「线程安全地改字段」。

### 什么时候不需要

- 只是几个无状态的工具方法 → **静态工具类**更简单；
- 配置项很少且不变 → 常量或不可变对象即可；
- 业务对象（Service、Repository）→ 交给 Spring 等容器管理单例，
  **不要手写 `getInstance()`**，否则依赖关系会被藏起来。

---

## 7. 适用边界

### 适合

- 进程级共享的只读配置、日志器、缓存管理器；
- 创建成本高、确实只需要一份的资源（连接池管理器等，通常由框架提供）；
- 必须全局协调的状态（计数器、注册表）。

### 不适合

- 有状态的业务对象：单例会让请求之间互相污染；
- 需要多实例做隔离/测试的场景；
- 只是为了「方便到处取」——那是在用单例掩盖依赖没传清楚。

### 容易混淆

| 对比 | 区别 |
|---|---|
| **静态工具类** | 静态类没有实例、不能实现接口、不能携带可变状态；单例是一个真正的对象，可以有状态、实现接口、被传递 |
| **全局变量** | 全局变量不限制创建数量；单例把「唯一」写进了构造器 |
| **对象池** | 池里可以有多个对象，用完归还；单例从头到尾只有一个 |

> 单例最常见的误用，是把「我需要到处访问它」当成「它必须唯一」。
> 前者是依赖传递问题，后者才是单例要解决的问题。

---

## 8. 小练习

### 练习 1（场景判断）

一个 `OrderService` 需要调用 `SmsClient` 发短信。`SmsClient` 内部持有 HTTP 连接、
可以并发使用、也没有共享状态。团队想把它做成单例，理由是「短信客户端只需要一个」。
**需要吗？**

**参考答案**：不是必须。若连接资源确实需要复用，可以让容器创建一个实例并注入，
但这不是单例模式的必要场景；`SmsClient` 本身无状态，多个实例也不会造成数据不一致。
**判断标准是「多份会不会导致事实不一致或资源浪费」，不是「听起来只需要一个」。**

### 练习 2（解释设计原因）

为什么构造器必须声明为 `private`？只把 `getInstance()` 写成静态方法不行吗？

**参考答案**：不行。只要构造器是 public，任何类都能 `new AppConfig()` 造出第二份，
`getInstance()` 只能保证「你调它时拿到同一个」，不能保证「系统里只有一个」。
私有构造器把「创建权」收回到类内部，这才是单例的约束力所在。

### 练习 3（执行过程）

把 `INSTANCE` 改成在 `getInstance()` 里 `if (instance == null) instance = new AppConfig();`，
单线程下结果一样吗？多线程下可能出什么问题？

**参考答案**：单线程一样；多线程下两个线程可能同时判断为 `null`，各自创建一个实例，
后写的覆盖先写的，于是「单例」被破坏。需要加锁、静态内部类或枚举等方案。
**这也说明：单例的核心是「唯一」，线程安全是另一个必须单独解决的问题。**

---

## 9. 读完之后

你应该能回答：

1. 单例为什么存在？——保证进程内某个类只有一个实例，避免多份状态不一致。
2. 原始实现的问题？——public 构造器让副本数量失控，改一份不影响另一份。
3. 每个角色为什么存在？——私有构造器挡住外部创建，静态实例持有唯一对象，`getInstance()` 提供访问点。
4. 怎么协作？——类初始化创建一次 → 所有调用方拿同一个引用。
5. 新场景要不要用？——看「多份是否会导致事实不一致或资源浪费」，而不是「是否方便」。

继续阅读：

- [深度文档](README.md)：双检锁、静态内部类、枚举、反射/序列化破坏与防护；
- [SingletonClient.java](SingletonClient.java)：工程版入口；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)；
- [现代场景](../../../../../docs/06-modern-scenarios.md)：Spring 的单例 Bean 与 GoF 单例的区别。
