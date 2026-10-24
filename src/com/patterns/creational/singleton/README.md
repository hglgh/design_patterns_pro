# 单例模式（Singleton Pattern）

> **一句话**：保证一个类**在整个 JVM 里只有一个实例**，并提供一个全局访问点。
>
> 本目录实测：**6 个源文件 / 263 行**，`javac -Xlint:all` 零告警；运行输出 **23 行 / 992 字节**，
> SHA-256 `DE9C97684D1E9331…`（`-Dstdout.encoding=UTF-8` 口径，连跑 5 次完全一致）。

> **诚实前置说明**：原项目 `design_patterns` 里**根本没有单例**（全项目搜不到任何 `*Singleton*` 文件或目录），
> 原任务书也写明"实际 22 种，缺单例"。所以本模块**不是"修原项目的 bug"，是从零补的一个模式**。
> 本文 §2 因此写成"五个版本的演化"，而不是"原项目错在哪"。

## 0. 先看结论

| 问题 | 答案 |
|---|---|
| 它解决什么 | 一个**有状态**的对象，全局必须只有一份（连接池、配置、计数器、缓存） |
| 它的分水岭是什么 | **私有构造器**。没有它，`new` 永远能造出第二个实例，其他一切白做 |
| 它和静态工具类的区别 | 静态类没有"实例"，不能实现接口、不能被注入、不能懒加载、不能序列化 |
| 它和 Spring 的 singleton 一样吗 | 不一样。Spring 是**容器里只有一个**；类本身可以 `new` 多个（测试时就这么干） |
| 什么时候别用 | 只是想"少 new 几次"；或者对象无状态（此时静态方法就够） |
| 最容易犯的错 | ① 懒汉式忘了同步 ② DCL 忘了 `volatile` ③ 只防了 `new`，没防反射和反序列化 |

一句话判断：**"这个对象必须有且只有一份，而且这份状态要被多方共享？"** 是，才需要它。**只是想省对象？不需要。**

## 1. 现实案例：说人话版

### 1.1 三个生活类比

**公司公章**。整个公司只有一枚，谁要用都得去同一个地方盖。**如果每个部门自己刻一枚，盖出来的效力就不一样了**——这就是"必须唯一"的动机。

**小区配电房**。一个小区一个配电房，所有住户都从它取电。不可能每户自己建一个——成本、安全、协调全都不允许。

**国家队主教练**。同一时刻只能有一个在任。任命是全局事件，所有人都看同一个"当前主教练"。

三个类比的共同点：**"唯一"本身是业务约束，不是技术优化。**

### 1.2 业务场景：应用配置

`AppConfig` 要读一个 `application.properties`，里面是数据库地址、超时时间、开关。

**不用单例**会怎样？

```java
// 每个用到配置的地方都 new 一个
AppConfig config = new AppConfig();       // 读一次磁盘
AppConfig config2 = new AppConfig();      // 又读一次
```

三个问题：

1. **重复 IO**：每 `new` 一次读一次磁盘/解析一次文件；
2. **状态不一致**：如果运行期改了某个开关（热更新），各个实例的值会不一样——**你到底听谁的**；
3. **无法共享监听器**：想"配置变了通知所有人"，多个实例根本做不到。

**用单例**：

```java
AppConfig config = AppConfig.getInstance();
```

全应用一份，改一处所有人都看到。

### 1.3 专业版：GoF 的定义与唯一的角色

> Ensure a class only has one instance, and provide a global point of access to it.
> —— *Design Patterns*, GoF, 1994

| 角色 | 职责 | 本目录对应 |
|---|---|---|
| **Singleton** | 自己创建自己、自己保存自己、对外提供访问点 | `EagerSingleton` 等 5 个类 |

**它只有一个角色**，是 23 个模式里最简单的。但正文只有两句话的定义，藏了三个必须同时成立的条件：

| 条件 | 实现手段 | 少一个会怎样 |
|---|---|---|
| **唯一** | 私有构造器 + 静态字段 | 别人能 `new`，就不唯一了 |
| **自己创建** | 静态字段初始化 / 懒加载 | 交给外部创建，就不受控了 |
| **全局访问** | `public static` 方法 | 拿不到，等于没有 |

### 1.4 JDK 里真实存在的单例

**① `java.lang.Runtime.getRuntime()`——最标准的饿汉式**

```java
Runtime rt = Runtime.getRuntime();
System.out.println(rt.availableProcessors());
```

`Runtime` 的构造器是私有的，`getRuntime()` 返回一个 `private static final Runtime currentRuntime = new Runtime();`。**这是"饿汉式"在 JDK 里的原型。**

**② `java.awt.Desktop.getDesktop()`——懒加载 + 同步**

`Desktop` 用 `synchronized` 方法 + 双重判空实现懒加载。**这是"懒汉式"的 JDK 版本。**

**③ `java.nio.file.FileSystems.getDefault()`**

文件系统抽象只有一个默认实现，通过静态工厂返回。**注意它叫"静态工厂"不叫"单例"**——因为它的实现里可能缓存，也可能每次返回等价对象，不一定保证 `==`。真正的单例必须保证同一个引用。

**④ 容易误认的：`System.out`、`Math`、`Collections`**

- `System.out` 是 `public static final PrintStream`——**它只是"一个静态字段"，不是单例模式**（没有私有构造器的约束，`PrintStream` 你能随便 new）；
- `Math`、`Collections` 是**静态工具类**，没有实例。

> 面试里把 `System.out` 当单例讲，会被追问："那 `new PrintStream(...)` 为什么能造出第二个？"——答不上来就露馅了。

**⑤ `Boolean.valueOf(boolean)`、`Integer.valueOf(int)`——不是单例，是"缓存"**

`Integer.valueOf(127) == Integer.valueOf(127)` 为 `true`（缓存），但 `Integer.valueOf(128) == Integer.valueOf(128)` 为 `false`。**这是享元，不是单例**——它允许存在多个实例，只是复用了一部分。

## 2. 从零推导：五个版本

原项目没有单例，所以这一节不是"批评原代码"，而是**把五种写法按"解决了什么问题、引入了什么新问题"的顺序推出来**。

### 2.1 v0：静态工具类——最像单例，但不是单例

```java
public final class ConfigUtil {
    private ConfigUtil() {}

    private static final Properties PROPS = load();

    public static String get(String key) {
        return PROPS.getProperty(key);
    }
}
```

**它能做到"全局一份数据"**，而且比单例更简单。但它有三个硬限制：

| 限制 | 后果 |
|---|---|
| **不能实现接口** | 想写 `Config` 接口 + 测试时替换成假实现，做不到 |
| **不能传递** | 方法参数想要"一个配置对象"，静态类给不了 |
| **不能懒加载 + 不能释放** | 类一被引用就初始化，且永远不释放 |

**结论**：如果配置是**只读的、无状态的、不需要替换**，静态工具类比单例更好——**它是单例的上位替代，不是劣化版。**

只有当"需要一个对象（实现接口 / 可注入 / 可替换）"时，才需要单例。

### 2.2 v1：饿汉式——最简单，但类加载即创建

```java
public final class EagerSingleton {

    private static final EagerSingleton INSTANCE = new EagerSingleton();

    private EagerSingleton() {
    }

    public static EagerSingleton getInstance() {
        return INSTANCE;
    }
}
```

| 维度 | 评价 |
|---|---|
| 线程安全 | ✅ **天然安全**。JVM 保证类初始化只执行一次（JLS 12.4.2） |
| 懒加载 | ❌ 类一被**主动使用**就创建，哪怕只调用了 `EagerSingleton.class` |
| 性能 | ✅ 无锁、无判断，一次静态字段读取 |
| 异常处理 | ⚠️ 构造器抛异常会变成 `ExceptionInInitializerError`，之后这个类**永久不可用** |

**"饿汉"这个名字的由来**：不管用不用，先创建了再说——像饿了很久的人先吃上。

**代价的真实含义**：如果单例持有重量级资源（数据库连接池、大缓存），而这个功能在本次启动里根本没被用到，你就白白付了代价。**这就是懒加载存在的理由。**

### 2.3 v2：懒汉式——懒加载了，但每次都抢锁

```java
public final class LazySingleton {

    private static LazySingleton instance;

    private LazySingleton() {
    }

    public static synchronized LazySingleton getInstance() {
        if (instance == null) {
            instance = new LazySingleton();
        }
        return instance;
    }
}
```

**问题很清楚**：`synchronized` 加在**方法**上，意味着**每次调用都要抢锁**——即使实例早就创建好了。

实测一下这个开销的量级（`synchronized` 在无竞争时是偏向锁/轻量级锁，纳秒级；但高并发下会膨胀成重量级锁，微秒级）。**对一个"每秒被调用几十万次"的访问点，这是不可接受的。**

于是有人想到：**先在锁外判一次空，只有为空时才进同步块。**

### 2.4 v3：双重检查锁定（DCL）——必须加 `volatile`

```java
public final class DoubleCheckLockSingleton {

    private static volatile DoubleCheckLockSingleton instance;

    private DoubleCheckLockSingleton() {
    }

    public static DoubleCheckLockSingleton getInstance() {
        if (instance == null) {                          // 第一次检查（无锁）
            synchronized (DoubleCheckLockSingleton.class) {
                if (instance == null) {                  // 第二次检查（有锁）
                    instance = new DoubleCheckLockSingleton();
                }
            }
        }
        return instance;
    }
}
```

**为什么需要第二次检查？**

线程 A 和 B 同时通过第一次检查（都看到 `instance == null`），A 先进锁创建了实例并退出，B 随后进锁——**如果 B 不再检查一次，就会再创建一个**。第二次检查是"排队进来后重新确认"。

**为什么必须 `volatile`？**

`instance = new DoubleCheckLockSingleton()` 在字节码层面是三步：

```text
1. 分配内存
2. 调用构造器，初始化字段
3. 把引用赋给 instance
```

JMM 允许把 3 排到 2 前面（**指令重排序**）。此时另一个线程在第一次检查时看到 `instance != null`，直接 `return instance`——**拿到的是一个"字段还没初始化完"的对象**。

`volatile` 的作用是**禁止这种重排**（写操作有 release 语义，读操作有 acquire 语义），保证"看到非 null 就一定看到完整初始化的对象"。

> **必须诚实地说**：在 x86 + HotSpot 上，这个 bug **极难复现**——因为 x86 是强内存模型（TSO），`StoreStore` 本来就不重排。但在 ARM 等弱内存模型架构上，它是真实存在的。
>
> 更重要的历史事实：**Java 5 之前 `volatile` 的语义不完整**，DCL 是当时著名的反例（"The Double-Checked Locking is Broken" Declaration，2001 年由 Booth、Bacon、Bloch、Click 等人发表）。Java 5 修了 `volatile` 语义，DCL 才变成正确写法。
>
> **所以 `volatile` 不是"可选优化"，是正确性的一部分。少了它，代码在规范层面就是错的。**

### 2.5 v4：静态内部类——懒加载 + 零同步

```java
public final class StaticInnerClassSingleton {

    private StaticInnerClassSingleton() {
    }

    private static final class Holder {
        private static final StaticInnerClassSingleton INSTANCE = new StaticInnerClassSingleton();
    }

    public static StaticInnerClassSingleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

**它为什么又懒又安全？**

两个 JVM 保证叠在一起：

1. **类只在"首次主动使用"时初始化**（JLS 12.4.1）。只要没人调用 `getInstance()`，`Holder` 就永远不会被加载，`INSTANCE` 也就不存在——**这就是懒加载**。
2. **类初始化由 JVM 加锁保证只执行一次**（JLS 12.4.2，即"类初始化锁"）。多线程同时触发 `Holder` 初始化时，JVM 自己会串行化——**这就是线程安全，而且你一行同步代码都不用写**。

**它比 DCL 好在哪？**

| 维度 | DCL | 静态内部类 |
|---|---|---|
| 正确性依赖 | 依赖 `volatile` 语义，写错就错 | 依赖 JVM 类加载机制，写不坏 |
| 代码复杂度 | 两次判空 + 同步块 | 一个内部类 + 一句 `return` |
| 性能 | 一次 volatile 读（有内存屏障） | 一次静态字段读（无屏障） |
| 懒加载 | ✅ | ✅ |

**它唯一的限制**：**没法传参**。`Holder.INSTANCE` 是静态初始化，构造器只能无参。如果单例需要读配置才能构造，DCL 或饿汉式更合适。

**结论**：**不需要传参的场景，静态内部类是"无脑最优解"。**

### 2.6 v5：枚举——防反射、防反序列化

```java
public enum EnumSingleton {

    INSTANCE;

    private int counter;

    public void showMessage() {
        counter++;
        System.out.println("[EnumSingleton] 我是枚举单例，第 " + counter + " 次调用");
    }

    public int getCounter() {
        return counter;
    }
}
```

《Effective Java》Item 3 的原话是：**"单元素的枚举类型经常成为实现 Singleton 的最佳方法。"** 三个理由都是**语言级保证**，不是编码纪律：

| 保证 | 机制 |
|---|---|
| **唯一性** | 枚举常量在类初始化时创建，JVM 保证只创建一次 |
| **防反射** | `Constructor.newInstance` 遇到枚举直接抛 `IllegalArgumentException`（§7.4 实测） |
| **防反序列化** | 反序列化枚举时 JVM 返回同一个常量，不走普通对象反序列化流程（§7.4 实测） |

**缺点**：

1. **不能继承类**（枚举已隐式 `extends Enum`）；
2. **不能懒加载**（常量在类初始化时创建）——但枚举常量极轻，这个代价通常可忽略；
3. **写法"不像单例"**，团队新人可能不认识；
4. 早期 Android 上有"枚举比静态常量多占内存"的说法，现代 ART 上已不成立。

### 2.7 五种写法总对照

| 写法 | 懒加载 | 线程安全 | 防反射 | 防反序列化 | 可传参 | 代码量 |
|---|---|---|---|---|---|---|
| 静态工具类 | ❌ | ✅（无实例） | — | — | ❌ | 最少 |
| 饿汉式 | ❌ | ✅ | ❌ | ❌ | ✅ | 少 |
| 懒汉式（同步方法） | ✅ | ✅ | ❌ | ❌ | ✅ | 少 |
| DCL + volatile | ✅ | ✅ | ❌ | ❌ | ✅ | 中 |
| 静态内部类 | ✅ | ✅ | ❌ | ❌ | ❌ | 少 |
| **枚举** | ❌ | ✅ | ✅ | ✅ | ❌ | 最少 |

**"防反射/防反序列化"两列全是 ❌ 的，不是"实现得不好"，是"这种写法本身防不住"**——实测见 §7.2、§7.3。

## 3. 设计动机

### 3.1 四个真实动机

| 动机 | 说明 |
|---|---|
| **共享状态必须一致** | 配置、开关、计数器——多份就会不一致 |
| **资源唯一且昂贵** | 连接池、线程池、文件句柄——多份就是浪费和冲突 |
| **全局协调点** | 需要一个地方统一调度（如 ID 生成器、限流器） |
| **受控的全局访问** | 比起 `public static` 字段，`getInstance()` 能在返回前做懒加载、校验、统计 |

### 3.2 什么时候**不该**用单例——这一节比上一节更重要

**① 对象无状态。**

`StringUtils.isEmpty()` 这种纯函数，静态方法就够。给它套单例只是多一层间接。

**② 你只是嫌 `new` 麻烦。**

`new ArrayList<>()` 很便宜。为了"省对象"用单例，是把简单问题复杂化。

**③ 你想用它当"全局变量容器"。**

这是单例最坏的一种用法：

```java
// 反面教材
public class GlobalContext {
    public static final GlobalContext INSTANCE = new GlobalContext();
    public User currentUser;          // 谁都能改，谁都能读
    public List<Order> cart;
}
```

**这就是一个带方法调用的全局变量**，而且是可变的。它带来的所有问题（隐式依赖、并发竞争、测试互相污染）一个不少。**单例本身不是错，"用单例藏全局状态"才是错。**

**④ 你其实需要"容器里只有一个"。**

Spring 的 `@Service` 默认是 singleton scope——但那**不是单例模式**：

| 维度 | GoF 单例 | Spring singleton scope |
|---|---|---|
| 谁能保证唯一 | 类自己（私有构造器） | 容器（类可以随便 `new`） |
| 测试时能造第二个吗 | 不能 | 能（`new MyService()` 直接测） |
| 依赖关系 | 通过静态方法全局获取 | 通过构造器注入 |

**Spring 的做法更好**，因为它把"唯一"这个约束**从类里搬到了容器里**——类本身还是普通类，测试友好。

**结论**：如果你的项目已经在用 DI 容器，**优先用容器的 scope，而不是手写单例**。

### 3.3 单例与可测试性（最容易被忽略的代价）

```java
public class OrderService {
    public void create(Order order) {
        AppConfig config = AppConfig.getInstance();     // 硬编码依赖
        if (config.getBoolean("order.check.enabled")) {
            // ...
        }
    }
}
```

想测 `OrderService`，你必须：

1. 让 `AppConfig` 先被正确初始化（可能读文件、连库）；
2. 或者用反射把 `AppConfig.INSTANCE` 替换掉（`final` 字段还改不了）；
3. 测试之间会**共享**这个全局实例——**上一个测试改了开关，下一个测试就受影响**。

**这就是"隐藏依赖"的代价**：构造器签名 `OrderService()` 里看不出它依赖 `AppConfig`。

**解法**（两种，都不是"别用单例"）：

```java
// 解法 A：构造器注入（推荐）
public OrderService(AppConfig config) { this.config = config; }

// 解法 B：单例只做"默认实现"，业务类依赖接口
public interface Config { boolean getBoolean(String key); }
```

**单例可以是实现，但不该是依赖的表达方式。**

## 4. 设计推导

### 4.1 第 1 步：私有构造器是唯一的支点

其他所有手段（静态字段、`volatile`、枚举）都建立在"别人不能 `new`"之上。**没有私有构造器，一切都是装饰。**

```java
private EagerSingleton() {
}
```

严格说还有两个漏洞：**反射**和**反序列化**。这两个漏洞只有枚举能彻底堵住（§7.2 / §7.3）。

### 4.2 第 2 步：静态字段要多"强"

`private static final INSTANCE` vs `private static INSTANCE`：

| 修饰 | 作用 |
|---|---|
| `static` | 属于类，不属于实例（这是"全局一份"的基础） |
| `private` | 外部拿不到字段，只能走 `getInstance()` |
| `final` | 引用不可变——**但反射能改，`final` 只是"编译期 + 普通赋值"的约束** |

`final` 是必要的，因为它让"这个字段不会被重新赋值"成为可读的事实。但**别把 `final` 当安全边界**——反射可以改（`Field.setAccessible(true)` + `Field.set`，对非静态 `final` 字段在 Java 17+ 会受到限制，但静态 `final` 在构造后仍可通过 `Unsafe` 改）。**真正的安全边界是"不给外部入口"，不是 `final`。**

### 4.3 第 3 步：懒加载到底值不值

懒加载的收益：**没用到就不付代价**。
懒加载的代价：**多一层判断、可能引入并发问题、错误延迟到首次使用才暴露**。

判断依据：

| 场景 | 建议 |
|---|---|
| 单例很轻（只持有几个字段） | **不用懒加载**，饿汉式最简单 |
| 单例很重（连接池、大缓存）且可能用不到 | 懒加载，且优先静态内部类 |
| 构造需要参数（读配置） | 懒加载 + DCL，或饿汉式（配置在类加载时已就绪） |
| 构造可能失败 | **懒加载有风险**：失败会变成 `ExceptionInInitializerError` 且无法重试 |

最后一行值得展开：**静态初始化失败是不可恢复的**。类一旦初始化失败，后续所有访问都抛 `NoClassDefFoundError`——**没有"重试"这回事**。如果单例的构造依赖网络，这一点必须权衡。

### 4.4 第 4 步：`synchronized` 加在哪

三种位置，性能差一个数量级：

```java
// A：方法级同步——每次调用都抢锁
public static synchronized X getInstance() { ... }

// B：同步块 + 双重检查——只有首次创建才抢锁
if (instance == null) { synchronized (X.class) { if (instance == null) { ... } } }

// C：静态内部类——JVM 在类初始化时帮你同步，业务代码零同步
return Holder.INSTANCE;
```

| 方案 | 稳态开销 | 第一次开销 |
|---|---|---|
| A | 一次锁获取/释放 | 一次锁 + 一次构造 |
| B | **一次 volatile 读**（有内存屏障） | 一次锁 + 一次构造 |
| C | **一次静态字段读** | 类加载（JVM 内部加锁） |

**C 在稳态下最快，因为它连 volatile 读的屏障都省了**——`Holder.INSTANCE` 是普通静态字段，且其初始化由类加载保证 happens-before。

### 4.5 第 5 步：为什么 DCL 的两次判空都不能少

```java
if (instance == null) {                  // ← 少了它：每次调用都进同步块，退化成 A
    synchronized (X.class) {
        if (instance == null) {          // ← 少了它：两个线程可能各创建一个
            instance = new X();
        }
    }
}
```

- **去掉第一次**：所有调用都进同步块 → 性能退化成"懒汉式（同步方法）"；
- **去掉第二次**：A、B 同时通过第一次检查，A 创建完退出，B 进锁后**不再检查**，又创建一个 → **唯一性被破坏**。

**两次判空分别负责"性能"和"正确性"，一个都不能省。**

### 4.6 第 6 步：为什么枚举能防住反射和反序列化

**防反射**：`java.lang.reflect.Constructor.newInstance` 里有一句：

```java
if ((clazz.getModifiers() & Modifier.ENUM) != 0)
    throw new IllegalArgumentException("Cannot reflectively create enum objects");
```

**这是 JDK 源码里的硬编码检查**，绕不过去（除非用 `Unsafe` 之类的后门）。实测输出：

```text
[攻击 2] 反射创建枚举
  抛出了：java.lang.IllegalArgumentException
  消息：Cannot reflectively create enum objects
```

**防反序列化**：普通对象反序列化会 `new` 一个新实例；枚举的反序列化由 `ObjectInputStream` 特殊处理——它按"类名 + 常量名"查找已有的枚举常量并返回。所以反序列化出来的**就是同一个对象**（实测 `true`）。

对比普通单例：反序列化**必然产生新实例**（实测 `false`），除非手写 `readResolve()`。

### 4.7 第 7 步：怎么选——一张决策表

```text
需要单例吗？
├─ 对象无状态 / 只是工具方法 → 静态工具类（不要单例）
├─ 用 DI 容器 → 交给容器管 scope（不要手写单例）
└─ 确实要手写
   ├─ 构造需要参数吗？
   │  ├─ 需要 → DCL + volatile（唯一正确选择）
   │  └─ 不需要
   │     ├─ 可能被反射/反序列化攻击吗？
   │     │  ├─ 是（框架、插件、RPC 参数） → 枚举
   │     │  └─ 否
   │     │     ├─ 初始化昂贵且可能不用 → 静态内部类
   │     │     └─ 很轻、一定会用 → 饿汉式
```

**本项目把五种都写出来**，正是为了让这张表有对应的代码可看。

## 5. 代码解析

### 5.1 `EagerSingleton`——饿汉式

```java
public final class EagerSingleton {

    /** 类加载时即初始化。 */
    private static final EagerSingleton INSTANCE = new EagerSingleton();

    private EagerSingleton() {
    }

    public static EagerSingleton getInstance() {
        return INSTANCE;
    }
}
```

| 细节 | 为什么 |
|---|---|
| `final class` | 防止子类。子类可以加可变状态，也能通过 `super()` 间接创建实例 |
| `static final INSTANCE` | `static` 保证全局一份，`final` 保证引用不变 |
| 私有构造器空实现 | 没有初始化逻辑，所以空着。**但空构造器也要显式写**——不写编译器会给一个 `public` 的默认构造器，单例立刻失效 |
| `getInstance()` 直接返回 | 无判断、无锁。这是饿汉式的全部收益 |

### 5.2 `LazySingleton`——懒汉式

```java
public final class LazySingleton {

    private static LazySingleton instance;

    private LazySingleton() {
    }

    public static synchronized LazySingleton getInstance() {
        if (instance == null) {
            instance = new LazySingleton();
        }
        return instance;
    }
}
```

**`instance` 没有 `volatile`，为什么这里可以？**

因为整个方法被 `synchronized` 保护：所有线程都必须先拿到锁才能读 `instance`，而锁的 `happens-before` 语义保证"前一个线程的写对后一个线程可见"。**同步方法把判空和赋值都包在锁里，所以不需要额外的 `volatile`。**

**代价**：这个方法每秒被调用 100 万次，就抢 100 万次锁。

### 5.3 `DoubleCheckLockSingleton`——DCL

```java
private static volatile DoubleCheckLockSingleton instance;

public static DoubleCheckLockSingleton getInstance() {
    if (instance == null) {                              // 第一次：性能
        synchronized (DoubleCheckLockSingleton.class) {
            if (instance == null) {                      // 第二次：正确性
                instance = new DoubleCheckLockSingleton();
            }
        }
    }
    return instance;
}
```

`volatile` 的两个作用，都要说清：

1. **可见性**：一个线程写入后，其他线程能立刻看到（普通字段可能读到线程本地缓存里的旧值）；
2. **禁止重排序**：禁止"引用赋值"排到"构造器执行"之前（§2.4）。

**为什么锁的是 `X.class` 而不是 `this`？**

单例是静态的，没有 `this`（`getInstance()` 是静态方法，没有实例）。锁 `X.class` 是锁这个类对象，全局唯一，正是我们要的。

**能不能锁一个专门的 `private static final Object LOCK = new Object()`？**

可以，而且更好——**避免别处也 `synchronized (X.class)` 时产生意外的锁竞争**。本项目用 `X.class` 是为了简洁，生产代码推荐专用锁对象。

### 5.4 `StaticInnerClassSingleton`——静态内部类

```java
public final class StaticInnerClassSingleton {

    private StaticInnerClassSingleton() {
    }

    private static final class Holder {
        private static final StaticInnerClassSingleton INSTANCE = new StaticInnerClassSingleton();
    }

    public static StaticInnerClassSingleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

三个细节：

**① `Holder` 是 `private static final class`。** `private` 让外部无法触发它的初始化；`static` 让它不持有外部类引用（否则每次访问都要先有外部类实例）。

**② `Holder.INSTANCE` 是 `private`，但外部类能访问。** 这是 Java 内部类的特性——**编译期会生成 `synthetic` 访问方法**（`static Synthetic access$000()`），所以 `getInstance()` 里读 `Holder.INSTANCE` 不需要 public。

**③ 它没有 `volatile`，也不需要。** `getInstance()` 里只有一次静态字段读，这次读的 happens-before 由 **`Holder` 的类初始化**保证：`Holder` 初始化完成（包括 `INSTANCE` 赋值）在任何一个线程读到 `Holder` 之前。**JVM 用类初始化锁替你做了这一切。**

### 5.5 `EnumSingleton`——枚举

```java
public enum EnumSingleton {

    INSTANCE;

    private int counter;

    public void showMessage() {
        counter++;
        System.out.println("[EnumSingleton] 我是枚举单例，第 " + counter + " 次 调用，hashCode=" + hashCode());
    }
}
```

**`INSTANCE` 后面那个分号不能省**——它是枚举常量的结束符，也是"后面还有成员"的分隔符。

**它证明了枚举单例可以很"完整"**：可以有字段、方法、构造器、接口实现。唯一的限制是不能 `extends` 别的类。

**注意 `counter` 是可变的**——**这正是"单例共享状态"的双刃剑**：`showMessage()` 调两次，输出"第 1 次""第 2 次"，说明状态真的在共享。这是特性，也是风险（§7.7）。

### 5.6 `SingletonClient`——六段演示

| 段 | 演示什么 |
|---|---|
| 1~5 | 五种写法各自验证"两次获取是同一对象" |
| 6 | 100 个线程并发获取 DCL 单例，验证全部为同一实例 |

第 6 段的写法值得看：

```java
final DoubleCheckLockSingleton[] results = new DoubleCheckLockSingleton[threadCount];
for (int i = 0; i < threadCount; i++) {
    final int idx = i;
    threads[i] = new Thread(() -> results[idx] = DoubleCheckLockSingleton.getInstance());
    threads[i].start();
}
for (Thread t : threads) {
    t.join();
}
```

**`final int idx = i;` 是必须的**——lambda 只能捕获 effectively final 的变量，直接用 `i` 编译不过。

**局限**：100 个线程同时 `start()` 并**不保证**真的同时执行到 `getInstance()`。这个测试能发现"没加锁"这类明显问题，但**不能证明 DCL 一定正确**——真正的并发验证需要 `CountDownLatch` 让所有线程在同一时刻放行，且需要跑很多轮。**本项目是演示，不是并发压测**（`docs/05-verification.md` §7 已如实标注）。

### 5.7 输出里的 `hashCode()` 是怎么回事

五个 `showMessage()` 都打印了 `hashCode()`。这是 `Object.hashCode()`——**身份哈希**，默认由 JVM 按对象头计算，**不是内容哈希**。

**它稳定吗？** 实测 5 次运行，输出完全一致（`DE9C97684D1E9331` 五次相同）。原因是：

- HotSpot 默认用 `-XX:hashCode=5`（线程本地 Marsaglia xor-shift），**种子是确定的**；
- 本模块是单线程顺序执行，`hashCode()` 的调用顺序固定；
- 所以每次运行得到的序列相同。

**但这不是语言保证。** 只要满足下面任一条件，输出就会变：

- 在任何 `hashCode()` 调用之前多一次/少一次身份哈希计算；
- 换个 JVM 实现或改 `-XX:hashCode=N`；
- 多线程同时触发 `hashCode()`（每个线程有自己的种子）。

**所以文档里记录的具体数字（`414493378` 等）只能作为"这一次运行的样本"，不能作为契约。** 想验证单例唯一性，**看 `两次获取是否同一对象：true` 这一行就够了**——布尔值才是稳定的断言。

> 这本身是个教学点：**日志里打印 `hashCode()` 会让输出不可复现**。生产代码里打印对象标识，应该用业务主键（订单号、用户 ID），而不是身份哈希。

## 6. 执行流程

以第 3 段（DCL）为例：

```text
1. DoubleCheckLockSingleton.getInstance()（主线程）
     ├─ 读 instance → null（类已加载，但字段未赋值）
     ├─ 进入 synchronized (DoubleCheckLockSingleton.class)
     ├─ 再读 instance → 仍是 null
     ├─ new DoubleCheckLockSingleton()
     │    ├─ 分配内存
     │    ├─ 执行私有构造器（空）
     │    └─ 把引用写入 instance（volatile 写，带 release 屏障）
     └─ return instance

2. 第二次 getInstance()
     ├─ 读 instance → 非 null（volatile 读，带 acquire 屏障）
     └─ 直接 return，不进同步块

3. 第 6 段：100 个线程并发 getInstance()
     ├─ 可能多个线程同时读到 null，一起进同步块
     ├─ 只有一个线程抢到锁并创建实例
     ├─ 其余线程依次进锁，第二次检查发现非 null，直接返回
     └─ 所有线程拿到同一个引用
```

**关键点**：`volatile` 的写（创建时）和读（后续访问）必须配对，才能建立 happens-before。**少了 `volatile`，第 2 步的"非 null"就不能保证"初始化完成"。**

### 6.1 完整输出（23 行，逐字）

```text
========== 1. 饿汉式 ==========
[EagerSingleton] 我是饿汉式单例，hashCode=414493378
两次获取是否同一对象：true

========== 2. 懒汉式（synchronized） ==========
[LazySingleton] 我是懒汉式单例（synchronized），hashCode=1309552426
两次获取是否同一对象：true

========== 3. 双重检查锁定（DCL） ==========
[DCLSingleton] 我是双重检查锁定单例，hashCode=1704856573
两次获取是否同一对象：true

========== 4. 静态内部类 ==========
[StaticInnerClassSingleton] 我是静态内部类单例，hashCode=1829164700
两次获取是否同一对象：true

========== 5. 枚举（推荐） ==========
[EnumSingleton] 我是枚举单例，第 1 次调用，hashCode=1311053135
[EnumSingleton] 我是枚举单例，第 2 次调用，hashCode=1311053135
两次获取是否同一对象：true

========== 6. 多线程并发验证（DCL） ==========
100 个线程并发获取，是否全部为同一实例：true
```

**第 18、19 行值得看**：两次调用 `hashCode` 相同（`1311053135`），且 `counter` 从 1 变到 2——**同一个实例、同一份状态**。

### 6.2 复现与哈希

```bash
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/singleton/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.singleton.SingletonClient > run.txt
```

| 指标 | 值 |
|---|---|
| 退出码 | 0 |
| 行数 | 23 |
| 字节数 | 992（UTF-8 口径，含 CRLF） |
| SHA-256（前 16 位） | `DE9C97684D1E9331` |
| 确定性 | 连跑 5 次哈希一致（但见 §5.7 的说明：依赖 JVM 的哈希算法与调用顺序） |

## 7. 扩展对比

### 7.1 五种写法的性能量级

| 写法 | 稳态开销 | 说明 |
|---|---|---|
| 静态工具类 | 一次静态方法调用 | JIT 通常能内联 |
| **饿汉式** | 一次静态字段读 | 无屏障、无判断 |
| **静态内部类** | 一次静态字段读 | 同上，且支持懒加载 |
| DCL | 一次 `volatile` 读 | 有 acquire 屏障，比普通读略贵 |
| 懒汉式（同步方法） | 一次锁获取/释放 | 无竞争时纳秒级，竞争时膨胀 |
| 枚举 | 一次静态字段读 + 一次判空（`Enum.valueOf` 不用时无） | 与饿汉式同量级 |

**结论**：真正高并发的访问点，**静态内部类的"零屏障"是最优**。DCL 的 `volatile` 读在 x86 上几乎免费（acquire 只是一次普通读 + 编译器屏障），但规范上贵。

### 7.2 反射攻击：五种写法全部能破（实测）

```text
[攻击 1] 反射调用私有构造器
  EagerSingleton：破解成功，与 getInstance() 是同一对象吗 = false
  LazySingleton：破解成功，与 getInstance() 是同一对象吗 = false
  DCLSingleton：破解成功，与 getInstance() 是同一对象吗 = false
  StaticInnerClass：破解成功，与 getInstance() 是同一对象吗 = false
  ReflectTarget：破解成功，与 getInstance() 是同一对象吗 = false
```

**`setAccessible(true)` 让私有构造器形同虚设。** 这意味着：任何非枚举的单例，**在"能执行任意代码"的前提下都不是真正的单例**。

**要不要防？**

| 场景 | 建议 |
|---|---|
| 普通业务代码（没人会反射你的类） | **不用防**。防反射是过度设计 |
| 框架/插件平台（第三方代码会扫描你的类） | 值得防 |
| 需要"绝对唯一"（如序列号生成器） | 用枚举 |

如果确实想给非枚举单例加一道锁，可以在构造器里判断：

```java
private EagerSingleton() {
    if (INSTANCE != null) {
        throw new IllegalStateException("单例已被创建，禁止反射创建第二个");
    }
}
```

**注意这个判断有时序问题**：`INSTANCE` 在静态初始化时赋值为 `new EagerSingleton()`——此时 `INSTANCE` 还是 `null`，所以第一次创建能通过；第二次（反射）时 `INSTANCE` 已非 null，抛异常。**能防住，但依赖初始化顺序，很脆弱。**

### 7.3 反序列化攻击：普通单例必破，枚举免疫（实测）

```text
[攻击 3] 反序列化：没有 readResolve 会拿到新实例
  反序列化后，是否同一对象：false

[攻击 4] 加上 readResolve 就能防住
  反序列化后，是否同一对象：true

[攻击 5] 枚举天然防序列化
  反序列化后，是否同一对象：true
```

**为什么普通单例会破？** 反序列化不走构造器，直接由 `ObjectInputStream` 分配对象并填字段——**必然是新对象**。

**`readResolve()` 为什么能修？** 反序列化完成后，`ObjectInputStream` 会检查类里有没有 `readResolve()`，有就**用它的返回值替换掉刚创建的对象**：

```java
private Object readResolve() {
    return INSTANCE;      // 丢弃反序列化出来的新对象，返回单例
}
```

**注意**：`readResolve` 必须是 `private`（或至少包私有），不能是 `public static`。它的签名是 `Object readResolve()`，无参、无受检异常。

**枚举为什么免疫？** 枚举的反序列化由 `ObjectInputStream.readEnum` 特殊处理——按 `(类名, 常量名)` 在 `Class` 里查找已有常量，**根本不创建新对象**。

### 7.4 枚举的反射防护（实测原文）

```text
[攻击 2] 反射创建枚举
  抛出了：java.lang.IllegalArgumentException
  消息：Cannot reflectively create enum objects
```

这句消息来自 `java.lang.reflect.Constructor#newInstance` 的源码：

```java
if ((clazz.getModifiers() & Modifier.ENUM) != 0)
    throw new IllegalArgumentException("Cannot reflectively create enum objects");
```

**注意 `setAccessible(true)` 也能调用成功**——它不阻止你"打开"构造器，它阻止的是"创建"。所以枚举的防护点是 `newInstance`，不是 `setAccessible`。

### 7.5 单例 vs 静态工具类

| 维度 | 单例 | 静态工具类 |
|---|---|---|
| 有实例吗 | 有 | 无 |
| 能实现接口吗 | 能 | 不能 |
| 能作为参数传递吗 | 能 | 不能 |
| 能懒加载吗 | 能 | 不能（类初始化即执行静态块） |
| 能被 DI 注入吗 | 能（虽然不推荐） | 不能 |
| 能被 mock 吗 | 能（注入接口时） | 不能（静态方法难 mock） |
| 代码量 | 多（构造器 + 字段 + 方法） | 少 |

**决策**：无状态 → 静态工具类；有状态或需要多态 → 单例。

### 7.6 单例 vs Spring 的 singleton scope

```java
@Service                      // 默认 singleton scope
public class ConfigService {
    public boolean isEnabled(String key) { ... }
}
```

| 维度 | GoF 单例 | Spring singleton scope |
|---|---|---|
| 唯一性由谁保证 | 类自己（私有构造器 + 静态字段） | 容器（`DefaultSingletonBeanRegistry`） |
| 类能被 `new` 多个吗 | **不能** | **能**（测试里常这么干） |
| 依赖怎么来 | 静态方法全局获取 | 构造器注入 |
| 生命周期 | 类加载到 JVM 退出 | 容器管理（可 `@PreDestroy`） |
| 测试友好度 | 差（全局状态） | 好（每个测试独立容器/直接 new） |

**关键区别**：Spring 的做法是**"容器里只有一个"，不是"这个类只能有一个"**。这让"唯一"从**类型约束**变成了**装配约束**——测试时可以绕过。

**这也是为什么现代 Java 项目很少手写单例**：DI 容器把"唯一"这件事做得更好，而且不牺牲可测试性。

### 7.7 单例持有可变状态 = 全局变量（实测）

```text
[陷阱] 单例持有可变状态 = 全局变量
[EnumSingleton] 我是枚举单例，第 1 次调用，hashCode=1896277646
  一次 showMessage 让 counter 从 0 变成 1，任何拿到 INSTANCE 的代码都能改它
```

`counter` 在 `INSTANCE` 上，任何拿到 `INSTANCE` 的代码都能改它。如果这个 `counter` 用来做"全局请求计数"，那么：

- **并发下要自己保证原子性**（`counter++` 不是原子操作，得用 `AtomicInteger`）；
- **测试之间会互相污染**（上一个测试把 `counter` 加到 5，下一个测试的断言就得从 5 开始）；
- **无法回滚**（没有"重置"入口，重启才能清零）。

**三条纪律**：

1. 单例**尽量无状态**（只读配置、无状态工具）；
2. 必须有状态时，用**线程安全的数据结构**（`AtomicInteger`、`ConcurrentHashMap`）；
3. 提供**显式的 reset / close** 方法，给测试用。

### 7.8 与五个模式的分界

| 模式 | 关注点 | 与单例的分界 |
|---|---|---|
| **静态工具类** | 无实例的静态方法集合 | 结构相似，但没有"唯一实例"，也不能多态 |
| **工厂方法** | 造**哪个类** | 工厂可以每次造新的；单例永远只有一个 |
| **原型** | **复制**出多个 | 恰好相反：原型要多少份复制多少份 |
| **享元** | **复用**多个细粒度对象 | 享元是"多个不同对象共享"，单例是"只有一个对象" |
| **外观** | 简化子系统调用 | 外观常被实现为单例（无状态），但目的不同：外观为了**简化接口** |

**单例 vs 享元**最容易混。区别是**数量**：

- 单例：整个类**只有 1 个**实例；
- 享元：**可以有 N 个**实例，按 key 共享（如 26 个字母的 Glyph）。

`Integer.valueOf` 的缓存是享元，不是单例——因为它对不同的值返回不同的对象。

## 8. 优缺点与误区

### 8.1 优点

1. **唯一性有保证**。共享状态不会出现"两份不一致"。
2. **全局访问点**。不需要层层传参（但这也是缺点，见 8.2）。
3. **懒加载**（部分写法）。不用就不创建。
4. **可控的初始化**。可以在 `getInstance()` 里做校验、统计、日志。
5. **节省资源**。昂贵的对象只创建一次。

### 8.2 缺点

1. **隐藏依赖**。`new OrderService()` 看不出它依赖 `AppConfig`，读代码要靠搜。
2. **全局可变状态**。等价于一个"谁都能改的全局变量"，并发和测试都会痛（§7.7）。
3. **测试困难**。状态跨测试共享；替换实现要动反射。
4. **并发问题被掩盖**。看起来"只有一个对象"很安全，实际上这个对象的所有方法都要自己保证线程安全。
5. **违反单一职责**。单例类同时负责"业务逻辑"和"控制实例数量"。
6. **类加载器隔离会破功**。同一个类被两个 `ClassLoader` 加载 → 两个 `INSTANCE`。这在 OSGi、Tomcat 多 webapp、插件系统里是真实问题。
7. **序列化/反射能破**（非枚举）。

### 8.3 十个误区

| 误区 | 真相 |
|---|---|
| "单例 = 静态方法集合" | 不是。单例有实例，能实现接口、能传递、能懒加载 |
| "私有构造器就够了" | 不够。反射和反序列化都能绕过去（§7.2 / §7.3 实测） |
| "DCL 不用 `volatile` 也行，我在 x86 上测过没问题" | x86 是强内存模型，本来就难复现。**JMM 允许重排，代码在规范层面就是错的** |
| "`synchronized` 加在方法上最简单" | 正确但慢。每次调用都抢锁，高并发下是瓶颈 |
| "懒加载一定比饿汉好" | 不一定。单例很轻时，懒加载的判断和并发处理都是白付的成本 |
| "静态初始化失败可以重试" | 不能。类初始化失败后，后续访问抛 `NoClassDefFoundError`，**没有重试机会** |
| "枚举单例不能有状态/方法" | 可以有字段、方法、构造器、接口实现。只是不能 `extends` 类 |
| "Spring 的 singleton 就是单例模式" | 不是。容器里只有一个，类本身能 `new` 多个——**这反而更好** |
| "单例线程安全" | 单例的**创建**可能安全，但**它的状态**不一定。`counter++` 就不是原子的 |
| "单例省内存所以要多用" | 省的是"重复创建"的成本。对象很轻时，省下的不如引入的复杂度多 |

### 8.4 出现这些信号，说明你在滥用单例

- 单例里有 5 个以上可变字段 → 那是个全局变量容器，不是单例。
- 单例的方法里出现 `synchronized` → 说明状态有并发问题，考虑改成无状态或换数据结构。
- 测试里要写 `resetSingleton()` 这种方法 → 单例在污染测试。
- 一个类里出现 `Xxx.getInstance().getYyy().getZzz()` 这种链式全局调用 → 隐藏依赖，该改成注入。
- 业务类的方法签名里**看不到**它依赖什么（全靠 `getInstance()`） → 依赖关系被藏起来了。
- 单例的构造器里连数据库 → 启动顺序变得脆弱，类加载失败整个应用起不来。

## 9. 面试与自测

### 9.1 30 秒版

> 单例保证一个类在 JVM 里只有一个实例，并提供全局访问点。核心是私有构造器 + 静态字段 + 静态访问方法。五种常见写法：饿汉式、懒汉式（同步方法）、双重检查锁定、静态内部类、枚举。其中静态内部类靠 JVM 类加载机制实现懒加载 + 线程安全，代码最简洁；枚举靠语言级保证防住反射和反序列化，是《Effective Java》推荐写法。DCL 必须加 `volatile`，否则引用赋值可能重排到构造器执行之前。

### 9.2 2 分钟版

> 单例要同时满足三件事：唯一、自己创建、全局访问。私有构造器是唯一的支点——没有它，别的都白做。
>
> 五种写法是一次次"解决问题"推出来的：饿汉式最简单，但类加载就创建；懒汉式解决了懒加载，但每次调用都抢锁；DCL 把锁缩小到"首次创建"，但必须加 `volatile`，因为 `new` 的三步（分配、初始化、赋值）可能重排，导致别的线程拿到未初始化完的对象；静态内部类绕开了这个问题——`Holder` 类只在你第一次调 `getInstance()` 时初始化，JVM 的类初始化锁天然保证线程安全，代码里一行同步都不用写；枚举则靠语言级保证防住反射和反序列化。
>
> 我实测过攻击：`setAccessible(true)` 之后，饿汉、懒汉、DCL、静态内部类**全都能被反射造出第二个实例**；反序列化也能造出新的，除非手写 `readResolve`；只有枚举，反射时抛 `IllegalArgumentException: Cannot reflectively create enum objects`，反序列化返回同一个常量。
>
> 但我要说一句反话：单例最大的问题不是"怎么实现唯一"，而是**它是全局可变状态**。我实测过，枚举单例里一个 `counter` 被 `showMessage` 改掉，任何拿到 `INSTANCE` 的代码都能改它——这跟全局变量没区别。所以现代项目里，如果已经用了 Spring，我更倾向用容器的 singleton scope：类本身是普通类，测试时能 `new` 多个，唯一性交给容器管。**手写单例更适合"没有 DI 容器"或者"确实需要类型级唯一"的场景。**

### 9.3 追问链

| 追问 | 要点 |
|---|---|
| 单例和静态类的区别 | 单例有实例，能实现接口、能传递、能懒加载；静态类都没有 |
| 饿汉 vs 懒汉 | 饿汉类加载即创建（无线程问题但费资源）；懒汉首次调用创建（要处理并发） |
| DCL 为什么两次判空 | 外层管性能（避免每次抢锁），内层管正确性（防止重复创建） |
| DCL 为什么必须 volatile | 禁止"引用赋值"重排到"构造器执行"之前，保证看到非 null 就看到完整对象 |
| 静态内部类为什么线程安全 | JVM 保证类初始化只执行一次（JLS 12.4.2），且初始化有 happens-before |
| 静态内部类能传参吗 | 不能。`Holder.INSTANCE` 是静态初始化，构造器必须无参 |
| 枚举为什么防反射 | `Constructor.newInstance` 里有 `Modifier.ENUM` 检查，直接抛异常 |
| 枚举为什么防反序列化 | `ObjectInputStream` 对枚举按"类名 + 常量名"查已有常量，不创建新对象 |
| 普通单例怎么防反序列化 | 手写 `private Object readResolve() { return INSTANCE; }` |
| 单例会被类加载器破坏吗 | 会。同一个类被两个 ClassLoader 加载 → 两个实例（OSGi、Tomcat 多 webapp） |
| 单例线程安全吗 | 创建过程可能安全；**它持有的状态**不一定，`counter++` 就不是原子的 |
| 为什么说单例是反模式 | 它是全局可变状态 + 隐藏依赖 + 测试污染。但"反模式"是滥用导致的，不是模式本身坏 |
| Spring 的 singleton 是单例模式吗 | 不是。容器里只有一个，类本身能 new 多个——这是更好的做法 |

### 9.4 自测题

**1. 下面这个 DCL 有什么问题？**

```java
private static DoubleCheckLockSingleton instance;

public static DoubleCheckLockSingleton getInstance() {
    if (instance == null) {
        synchronized (DoubleCheckLockSingleton.class) {
            if (instance == null) {
                instance = new DoubleCheckLockSingleton();
            }
        }
    }
    return instance;
}
```

<details><summary>参考答案</summary>

**少了 `volatile`。** `new` 在字节码层面是"分配内存 → 执行构造器 → 引用赋值"三步，JMM 允许把"引用赋值"排到"执行构造器"之前。此时另一个线程在第一次判空时看到 `instance != null`，直接返回——**拿到的是字段还没初始化完的对象**。

修法：`private static volatile DoubleCheckLockSingleton instance;`。

补充：这个 bug 在 x86 + HotSpot 上极难复现（x86 是强内存模型，`StoreStore` 不重排），但在 ARM 上真实存在。Java 5 之前 `volatile` 语义不完整，DCL 是当时著名的反例。
</details>

**2. 静态内部类为什么不需要 `volatile` 也不需要 `synchronized`？**

<details><summary>参考答案</summary>

因为它压根没有"检查 + 创建"这个竞态窗口。

- **懒加载**来自 JLS 12.4.1：类只在**首次主动使用**时初始化。没人调 `getInstance()`，`Holder` 就不会初始化。
- **线程安全**来自 JLS 12.4.2：类初始化由 JVM 加锁，**保证只执行一次**。多个线程同时触发 `Holder` 初始化时，JVM 会串行化。

`getInstance()` 里只有一句 `return Holder.INSTANCE`——一次普通的静态字段读，没有判断、没有锁、没有内存屏障。**它的正确性完全由 JVM 类加载机制保证，所以写不坏。**
</details>

**3. 枚举单例为什么能防反射？JDK 里哪一行拦住了？**

<details><summary>参考答案</summary>

`java.lang.reflect.Constructor#newInstance` 里有一段硬编码检查：

```java
if ((clazz.getModifiers() & Modifier.ENUM) != 0)
    throw new IllegalArgumentException("Cannot reflectively create enum objects");
```

实测输出：

```text
抛出了：java.lang.IllegalArgumentException
消息：Cannot reflectively create enum objects
```

**注意拦截点是 `newInstance`，不是 `setAccessible`**——`setAccessible(true)` 仍然能执行成功，但它打开构造器之后，`newInstance` 会拒绝创建。所以枚举的防护点在"创建"这一步。
</details>

**4. 普通单例被反序列化破坏后，为什么 `readResolve` 能修？**

<details><summary>参考答案</summary>

反序列化流程是：`ObjectInputStream` 分配对象（不走构造器）→ 填字段 → **检查类里有没有 `readResolve()`** → 有就用返回值**替换**刚创建的对象。

```java
private Object readResolve() {
    return INSTANCE;      // 丢弃新创建的对象
}
```

实测对比：没有 `readResolve` 时 `s1 == s2` 为 `false`；加上之后为 `true`。

**注意**：`readResolve` 必须是 `private`（或包私有），签名是 `Object readResolve()`，不能有参数。
</details>

**5. 为什么说"单例持有可变状态 = 全局变量"？**

<details><summary>参考答案</summary>

因为单例的字段对所有拿到 `INSTANCE` 的代码可见、可改。实测：

```text
[EnumSingleton] 我是枚举单例，第 1 次调用
  一次 showMessage 让 counter 从 0 变成 1，任何拿到 INSTANCE 的代码都能改它
```

后果三条：

1. **并发不安全**：`counter++` 不是原子操作，多线程会丢更新；
2. **测试污染**：上一个测试改的值，下一个测试还能看到；
3. **无法回滚**：没有 reset 入口，只能重启。

**纪律**：单例尽量无状态；有状态时用线程安全的数据结构；提供显式的 reset/close。
</details>

**6. 单例类和静态工具类，怎么选？**

<details><summary>参考答案</summary>

| 需求 | 选择 |
|---|---|
| 无状态、纯函数 | **静态工具类**（更简单） |
| 需要实现接口 / 多态替换 | 单例 |
| 需要作为参数传递 | 单例 |
| 需要懒加载（初始化很贵） | 单例（静态工具类做不到） |
| 需要被 DI 注入或 mock | 单例（实现接口后） |

**关键判断**：需不需要"一个对象"。不需要对象，静态类就够——**别为了模式而模式**。
</details>

**7. 手写题：写一个"线程安全 + 懒加载 + 能传参"的单例。**

<details><summary>参考答案</summary>

能传参就必须放弃静态内部类（`Holder` 是静态初始化，只能无参）。用 DCL：

```java
public final class ConfigSingleton {

    private static volatile ConfigSingleton instance;

    private final String path;
    private final Properties props;

    private ConfigSingleton(String path) {
        this.path = path;
        this.props = load(path);
    }

    public static ConfigSingleton getInstance(String path) {
        if (instance == null) {
            synchronized (ConfigSingleton.class) {
                if (instance == null) {
                    instance = new ConfigSingleton(path);
                }
            }
        }
        return instance;
    }
}
```

**但要注意一个语义问题**：第二次调用传入的 `path` 会被**静默忽略**——单例已经创建好了。这是个陷阱，应该显式处理：

```java
public static ConfigSingleton getInstance(String path) {
    if (instance != null && !instance.path.equals(path)) {
        throw new IllegalStateException("单例已用路径 " + instance.path + " 创建，不能改成 " + path);
    }
    // ... 原来的 DCL
}
```

**更好的设计**：把"路径"这类配置从"每次调用传"改成"启动时初始化一次"（`init(path)`），避免"第二次调用参数无效"的歧义。
</details>

**8. 判断对错：单例一定能保证线程安全。**

<details><summary>参考答案</summary>

**错，而且这是最常见的误解。**

单例保证的是"**实例的创建**"过程安全（不重复创建）。但**实例里的状态**是否线程安全，是另一回事：

```java
public enum EnumSingleton {
    INSTANCE;
    private int counter;
    public void increment() { counter++; }      // 非原子，并发会丢更新
}
```

`counter++` 是"读-改-写"三步，多线程同时执行会丢更新。要线程安全必须用 `AtomicInteger` 或加锁。

**结论**：单例解决"唯一"，不解决"并发"。**无状态单例天然线程安全；有状态单例要自己保证。**
</details>

**9. 为什么现代项目更推荐用 DI 容器的 singleton scope？**

<details><summary>参考答案</summary>

因为容器把"唯一"从**类型约束**变成了**装配约束**：

| 维度 | 手写单例 | 容器 scope |
|---|---|---|
| 唯一性 | 类自己保证（私有构造器） | 容器保证（`DefaultSingletonBeanRegistry`） |
| 类能 `new` 多个吗 | 不能 | **能**——测试时就这么干 |
| 依赖从哪来 | 静态方法全局获取（隐藏依赖） | 构造器注入（依赖显式） |
| 测试 | 要反射替换全局实例 | 每个测试独立容器 / 直接 `new` |

**Spring 的做法没有牺牲可测试性**，这是它优于手写单例的核心原因。
</details>

**10. 单例在什么情况下会"不止一个"？**

<details><summary>参考答案</summary>

四种破法：

1. **反射**：`setAccessible(true)` + `newInstance()`（枚举除外，实测抛异常）；
2. **反序列化**：普通单例必破，除非写 `readResolve()`（枚举除外）；
3. **类加载器隔离**：同一个 `.class` 被两个 `ClassLoader` 加载 → 两个 `INSTANCE`。真实场景：OSGi、Tomcat 多 webapp、插件系统；
4. **分布式部署**：单例只在**单个 JVM** 内唯一。两个实例部署在两个节点上，就是两份——**这是最容易忽略的一条**，很多人以为"单例 = 全局唯一"，其实是"JVM 内唯一"。

第 4 条尤其重要：**跨进程共享状态要用 Redis / 数据库 / 分布式锁，单例管不了。**
</details>

### 9.5 面试官想听什么

**想听**：

- "DCL 必须 `volatile`，因为 `new` 的三步可能重排"——说明你理解 JMM 而不是背代码。
- "静态内部类靠 JLS 12.4 的类初始化保证，代码里不需要同步"——说明你知道它为什么对。
- "枚举防反射是因为 `Constructor.newInstance` 里有 `Modifier.ENUM` 检查"——说明你看过源码。
- "单例是 JVM 内唯一，不是全局唯一；分布式要另外解决"——说明你有工程视野。
- "单例最大的问题是全局可变状态和可测试性，不是'怎么实现唯一'"——说明你想过代价。

**不想听**：

- 只背"饿汉、懒汉、DCL、静态内部类、枚举"五种名字。
- 说"懒汉式加 synchronized 就线程安全了"却不知道性能问题。
- 认为"私有构造器就够了"，不知道反射和反序列化。
- 说"单例线程安全"。
- 把 `System.out`、`Integer.valueOf` 当单例。

## 10. 附录

### 10.1 本目录文件清单

| 文件 | 行数 | 角色 | 懒加载 | 防反射/反序列化 |
|---|---|---|---|---|
| `EagerSingleton.java` | 35 | 饿汉式 | ❌ | ❌ |
| `LazySingleton.java` | 35 | 懒汉式（`synchronized` 方法） | ✅ | ❌ |
| `DoubleCheckLockSingleton.java` | 46 | DCL + `volatile` | ✅ | ❌ |
| `StaticInnerClassSingleton.java` | 39 | 静态内部类 | ✅ | ❌ |
| `EnumSingleton.java` | 34 | 枚举 | ❌ | ✅ |
| `SingletonClient.java` | 74 | 六段演示 | — | — |

### 10.2 运行方式

```bash
build.bat
java -cp out com.patterns.creational.singleton.SingletonClient

# 或只编译本目录
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/singleton/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.singleton.SingletonClient
```

### 10.3 本次修订记录

**本模块是新增的，不是重构的。** 原项目 22 种模式里没有单例（全项目 `Glob **/*Singleton*.java` 命中 0 个文件），原任务书也写明"实际 22 种，缺单例"。所以：

| 项 | 说明 |
|---|---|
| 代码来源 | 从零编写，覆盖五种经典写法 + 100 线程并发验证 |
| 有无原项目缺陷可对照 | 无。本文 §2 因此写成"五个版本的演化"，不是"原项目错在哪" |
| 与其他模块的差异 | 其他模块都有"原项目写法 → 缺陷取证 → 重构"三段，本模块没有第一、二段 |

---

相关文档：[模式对比 §创建型](../../../../../docs/02-pattern-comparison.md) · [学习路径](../../../../../docs/01-learning-path.md) · [优化记录](../../../../../docs/03-optimization-log.md) · [验证记录](../../../../../docs/05-verification.md)
