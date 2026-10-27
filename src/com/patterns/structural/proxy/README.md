# 代理模式（Proxy Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> 包路径：`com.patterns.structural.proxy`
> 运行入口：`com.patterns.structural.proxy.Client`
> 分类：结构型模式
> 一句话：**为某个对象提供一个"替身"，客户端拿到的是替身，替身决定什么时候、要不要、让谁去调用真身。**

---

## 目录

1. [现实案例：为什么需要代理](#1-现实案例为什么需要代理)
2. [原始实现：不用代理会写出什么代码](#2-原始实现不用代理会写出什么代码)
3. [设计动机：什么时候该用，什么时候不该用](#3-设计动机什么时候该用什么时候不该用)
4. [设计推导：这些类是怎么一步步"逼"出来的](#4-设计推导这些类是怎么一步步逼出来的)
5. [代码解析：每个类为什么必须存在](#5-代码解析每个类为什么必须存在)
6. [执行流程：从 main 方法到控制台输出](#6-执行流程从-main-方法到控制台输出)
7. [扩展对比：新增一种代理要改哪些代码](#7-扩展对比新增一种代理要改哪些代码)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试与自测](#9-面试与自测)

---

## 1. 现实案例：为什么需要代理

### 说人话版

想象你是一家公司的老板，有个人要来见你。

- 他**不会**直接冲到你家门口敲门，他先联系你的**秘书**。
- 秘书会问：「你是谁？有预约吗？」——**这是保护代理（鉴权）**。
- 秘书会说：「老板现在在开会，你下午再来。」——**这是虚拟代理（延迟到真正需要时）**。
- 秘书会说：「这个问题你上周问过了，答案是 XXX，我直接告诉你，不用打扰老板。」——**这是缓存代理**。
- 秘书每次都会在小本子上记：「几点几分，谁来过，谈了多久。」——**这是日志代理**。

**关键点**：来访者从头到尾只跟秘书打交道，他以为自己见到的就是老板。
秘书和老板**对外表现完全一样**（都能"接见来访者"），但秘书可以在"接见"这个动作前后**做任何额外的事**，
甚至可以**直接拒绝，老板压根不知道有人来过**。

### 专业版

代理模式解决的问题是：**如何在不修改目标对象、不修改客户端调用代码的前提下，为目标对象的方法调用织入额外的控制逻辑。**

这里的"额外控制逻辑"在 AOP 术语里叫**横切关注点（Cross-Cutting Concern）**——
它不属于业务逻辑本身，但几乎每个业务方法都需要：日志、事务、鉴权、缓存、限流、监控、延迟加载、远程通信。

本项目用的业务场景是**图片查看器**（GoF 原书用的也是这个例子，因为它最直观）：

| 现实痛点 | 对应代理类型 | 本项目实现类 |
|---------|------------|------------|
| 一张 4K 图 20MB，列表页有 100 张图，全加载会 OOM | 虚拟代理（延迟加载） | `VirtualImageProxy` |
| 财务报表图片不能让访客看到 | 保护代理（权限控制） | `ProtectedImageProxy` |
| 同一张图在页面来回滚动时被反复渲染，每次几十毫秒 | 缓存代理 | `CachingImageProxy` |
| 想给所有图片操作加日志、计时、鉴权，但不想为每种增强写一个类 | 动态代理 | `ImageInvocationHandler` + `DynamicImageProxy` |

### 这些场景在真实框架里长什么样

代理模式**不是教科书玩具**，它是 Java 企业开发的地基：

- **Spring AOP**：`@Transactional`、`@Cacheable`、`@Async`、`@PreAuthorize` 全部靠代理实现。
  你注入的 `UserService` 很可能根本不是 `UserServiceImpl`，而是 `UserServiceImpl$$SpringCGLIB$$0`。
- **MyBatis Mapper**：你只写了接口 `UserMapper`，没有实现类。
  `getMapper()` 返回的是 JDK 动态代理对象，代理在 `invoke()` 里根据方法名找到对应 SQL 并执行。
- **Hibernate/JPA 延迟加载**：`@ManyToOne(fetch = LAZY)` 时，`order.getUser()` 返回的是一个代理，
  只有你调用 `user.getName()` 时才真正发 SQL。
- **RPC 框架（Dubbo/gRPC stub）**：本地调用一个接口方法，代理把它序列化成网络请求发给远程机器。
  这就是**远程代理**，客户端代码看起来和调本地方法一模一样。
- **Mockito**：`mock(UserDao.class)` 生成的就是一个动态代理对象。

---

## 2. 原始实现：不用代理会写出什么代码

### 原项目（重构前）的代码

原项目 `src/structuraltype/ProxyPattern/` 只有 4 个文件，是标准的 GoF 虚拟代理示例：

```java
// Image.java —— 只有 display()
public interface Image {
    void display();
}

// RealImage.java —— 用 record 承载 IO 副作用
public record RealImage(String filename) implements Image {
    public RealImage {
        System.out.println("Loading image: " + filename);   // 构造器里做 IO
    }
    @Override
    public void display() {
        System.out.println("Displaying image: " + filename);
    }
}

// ProxyImage.java —— 虚拟代理
public class ProxyImage implements Image {
    private RealImage realImage;
    private final String filename;

    public ProxyImage(String filename) { this.filename = filename; }

    @Override
    public void display() {
        if (realImage == null) {
            realImage = new RealImage(filename);
        }
        realImage.display();
    }
}
```

**这个实现本身是正确的**，虚拟代理的核心逻辑（`if (realImage == null) new RealImage(...)`）完全没问题。
但它有三个可以改进的地方，也正是本次重构的出发点。

### 问题 1：`Image` 接口只有 `display()`，代理拿不到文件名

假设现在要写一个**缓存代理**，缓存的 key 该用什么？当然是文件名。

```java
public class CachingImageProxy implements Image {
    private final Image target;
    private final Map<String, String> cache = new HashMap<>();

    @Override
    public void display() {
        String key = ???;   // ← 卡住了！target 是 Image 类型，Image 没有 getFilename()
    }
}
```

只有三条烂路可走：

**烂路 A：构造时把 filename 再传一遍**

```java
public CachingImageProxy(Image target, String filename) { ... }
```

→ 调用方变成 `new CachingImageProxy(new VirtualImageProxy("a.png"), "a.png")`，
**同一个字符串写两遍**，改一处忘一处就是"缓存永远不命中"的诡异 bug（不报错，只是慢，极难排查）。

**烂路 B：强转成 RealImage**

```java
String key = ((RealImage) target).filename();
```

→ 直接**毁掉代理链**。缓存代理只能包 `RealImage`，不能包 `VirtualImageProxy`
（会抛 `ClassCastException`）。而"鉴权 → 缓存 → 延迟加载"这种链式组合正是代理模式最有价值的用法。

**烂路 C：缓存代理自己维护一个"当前 key"**

→ 状态冗余，且和目标对象的状态可能不一致。

**本次重构的修正**：给 `Image` 接口加上 `getFilename()`。

```java
public interface Image {
    void display();
    String getFilename();   // ← 新增
}
```

这不是"多加一个方法"这么简单，它体现了代理模式的一个设计准则：

> **抽象主题的接口应该包含"标识性/元数据"方法，让代理能在不触发真实操作的前提下拿到必要信息。**

`getFilename()` 是一个**廉价的、无副作用的**方法：代理实现它只需转发（`return target.getFilename()`），
`VirtualImageProxy` 实现它只需返回自己存的字符串，**完全不会触发图片加载**。
正是这一点让"鉴权失败时图片根本没被加载"成为可能（见第 6 节 demo2 实测输出）。

### 问题 2：`RealImage` 用 `record` 承载 IO 副作用

三个理由说明这里不该用 `record`：

1. **语义错位**：`record` 的设计意图是**透明的不可变数据载体**（"数据即接口"）。
   它的构造器应该只做字段赋值和参数校验，做 IO 违反了 `record` 的契约。
   看到 `record` 的人会默认"new 一个很便宜"，而这里 new 一次要读几十 MB 磁盘。
2. **`toString()` 会失控**：真实的图片对象会有 `byte[] data` 字段。
   `record` 自动生成的 `toString()` 会把它打印出来；
   自动生成的 `equals()`/`hashCode()` 也会遍历整个数组，两个内容相同的图片比较退化成 O(n) 全量对比。
3. **`record` 隐式 `final`，无法被 CGLIB 代理**：CGLIB 的原理是**生成目标类的子类**。
   `final class` 不能被继承，所以 Spring 无法为 `record` 类型创建 CGLIB 代理。
   虽然本项目不用 CGLIB，但这是"为什么真实主题不该是 record"的一个现实理由。

**本次重构的修正**：改成 `final class`，把"加载"抽成显式的私有方法，并加上计数器。

```java
public final class RealImage implements Image {
    private static int loadCount = 0;          // ← 新增：让代理效果可测量
    private final String filename;

    public RealImage(String filename) {
        this.filename = filename;
        loadFromDisk();                         // 副作用仍在构造器，但语义明确、可被计数
    }

    private void loadFromDisk() {
        loadCount++;
        System.out.println("      [RealImage] ★ 从磁盘加载图片：" + filename
                + "（第 " + loadCount + " 次真实加载）");
    }

    public static int getLoadCount() { return loadCount; }
    public static void resetLoadCount() { loadCount = 0; }
}
```

`loadCount` 是本次重构加的**教学仪器**。原项目跑完你只知道"打印了几行 Loading image"，需要自己数。
现在 `Client` 每一段演示都能直接输出数字：

```
真实加载次数 = 0（3 个代理对象只占了 3 个字符串的内存）
...
结论：加载了 1 张而不是 3 张，节省 2 次昂贵 IO
```

**代理模式的价值是"少做事"，而"少做事"必须能被量化，否则你无法向别人证明它有用。**

### 问题 3：只有一种代理，看不出"代理"这个抽象的通用性

原项目只有虚拟代理。这会让人误以为「代理模式 = 延迟加载」。
实际上 GoF 原书列举了**四种**代理，它们的差别只在"控制什么"：

| 代理类型 | 控制的问题 | 判断发生在 |
|---------|-----------|----------|
| 虚拟代理 | **什么时候**创建真实对象 | `display()` 内部，`if (real == null)` |
| 保护代理 | **谁**能访问真实对象 | `display()` 开头，鉴权失败直接 `return` |
| 缓存代理 | **是否需要**访问真实对象 | `display()` 开头，查缓存命中就 `return` |
| 远程代理 | 真实对象**在哪个进程** | `display()` 内部，序列化 + 网络调用 |

它们的骨架**完全一样**：

```java
public void display() {
    if (不满足某个条件) {
        return;             // 或返回默认值，真实对象完全不参与
    }
    target.display();       // 条件满足才委托
    // 可选的后置动作
}
```

**本次重构的修正**：把四种都实现出来（远程代理需要网络与两个进程，用动态代理代替演示"横切增强"），
并在 `Client` 里加了一段**代理链**演示——这是原项目完全缺失、但最能体现代理威力的部分。

### 如果彻底不用代理，代码会变成什么样

假设坚持"不要代理模式"，把延迟加载、鉴权、缓存、日志全塞进 `RealImage`：

```java
public class RealImage {
    private final String filename;
    private byte[] data;                       // 延迟加载
    private boolean loaded = false;
    private final Map<String, String> cache;   // 缓存
    private final String currentUserRole;      // 鉴权

    public RealImage(String filename, String role, Map<String, String> cache) {
        this.filename = filename;
        this.currentUserRole = role;
        this.cache = cache;
    }

    public void display() {
        // ① 鉴权
        if (!ALLOWED_ROLES.contains(currentUserRole)) {
            System.out.println("拒绝访问");
            return;
        }
        // ② 查缓存
        String snapshot = cache.get(filename);
        if (snapshot != null) { System.out.println(snapshot); return; }
        // ③ 延迟加载
        if (!loaded) { data = readFromDisk(filename); loaded = true; }
        // ④ 日志 + 计时
        long start = System.nanoTime();
        render(data);
        System.out.println("耗时 " + (System.nanoTime() - start));
        // ⑤ 写缓存
        cache.put(filename, "...");
    }
}
```

这段代码**能跑**，但它有 5 个致命问题：

1. **单一职责被彻底破坏**：一个类同时管渲染、鉴权、缓存、日志、加载时机。
   改任何一件事都要动这个类，改错任何一处都会影响其他四处。
2. **无法按需组合**：批处理任务不需要鉴权（它是内部调用），但被迫传一个假的 `role`；
   导出工具不需要缓存（它要每次都拿最新结果），但被迫传一个 `cache`。
   参数会退化成 `RealImage(String, String, Map, boolean skipAuth, boolean useCache, boolean log)`。
3. **顺序耦合**：鉴权必须在加载之前。这个顺序被硬编码在方法体里，
   有一天需求变成"鉴权失败也要记一条审计日志"，你要在 `return` 前插代码，很容易插错位置。
4. **无法测试**：想单测渲染逻辑，必须准备角色、准备缓存 Map；
   想单测鉴权，必须真的去读磁盘（或者 mock 一堆东西）。
5. **无法复用横切逻辑**：明天新增 `Video` 类，这 5 段代码要**从头再抄一遍**。100 个业务类就是 100 份重复。

而代理模式的解法是：**每个横切关注点独立成一个类，用组合按需叠加。**

```java
Image image = new ProtectedImageProxy(              // 要鉴权？套一层
        new CachingImageProxy(                       // 要缓存？再套一层
                new VirtualImageProxy("report.pdf")  // 要延迟加载？再套一层
        ), "EDITOR");
```

不需要哪个，就不套哪个。**这就是代理模式的全部价值：把"控制访问"从"业务逻辑"里剥离出来，变成可插拔的层。**

---

## 3. 设计动机：什么时候该用，什么时候不该用

### 该用的 6 个信号

| 信号 | 具体表现 | 用哪种代理 |
|-----|---------|----------|
| **创建/访问成本高** | 加载大文件、建立数据库连接、调用远程服务 | 虚拟代理 |
| **需要权限控制** | 不同角色能做的事不同，且校验必须在业务动作之前 | 保护代理 |
| **结果可复用且计算昂贵** | 同一入参反复出现，每次几十毫秒以上 | 缓存代理 |
| **需要横切增强** | 日志、计时、事务、监控，且要应用到很多类 | 动态代理 |
| **真实对象在别处** | 跨进程、跨机器、跨网络 | 远程代理 |
| **需要控制生命周期** | 引用计数、写时复制、对象归还池 | 智能引用代理 |

### 不该用的 4 种情况

**① 没有"控制访问"的需求，只是想改行为**
→ 那是**装饰器模式**的领域。区分标准见第 8 节。

**② 目标类没有接口，且你也不打算抽接口**
→ JDK 动态代理用不了。可以上 CGLIB/ByteBuddy，但要评估：
目标类和方法不能是 `final`，构造器会被调用两次（CGLIB 用 Objenesis 可规避），
生成子类有额外的类加载开销。**如果一个类只有一两处需要增强，直接写静态代理更清楚。**

**③ 增强逻辑只有一两行，且只有一处调用点**
→ 直接在调用处写 `if` 就好。为一个 `if (role.equals("ADMIN"))` 建三个类是过度设计。

**④ 性能敏感的热点路径**
→ 动态代理每次调用都要经过反射 `Method.invoke()`，比直接调用慢若干数量级
（本项目实测：动态代理的 `getFilename()` 首次调用约 585 μs，其中绝大部分是反射开销、JIT 预热与控制台 IO）。
在每秒百万次调用的场景，反射开销会成为瓶颈。
**这也是为什么高性能框架（如 Netty）不用动态代理，以及 Spring 提供 CGLIB 作为备选。**

### 为什么"代理"和"装饰器"结构一模一样却算两个模式

这是最容易困惑的一点。看代码：

```java
// 代理
public class ProtectedImageProxy implements Image {
    private final Image target;
    @Override public void display() {
        if (!allowed()) return;      // ← 可能不调用 target
        target.display();
    }
}

// 装饰器
public class BoldDecorator implements TextComponent {
    protected final TextComponent component;
    @Override public String render() {
        return "<b>" + component.render() + "</b>";   // ← 一定调用 component，且改变返回值
    }
}
```

**类结构确实一样**（都持有同类型引用 + 都实现同一接口 + 都委托）。区别在**意图**：

| 维度 | 代理 | 装饰器 |
|-----|-----|-------|
| **意图** | **控制**对目标的访问 | **增强**目标的功能 |
| **是否一定调用目标** | 不一定（鉴权失败、缓存命中就不调） | 一定调用（不调就没意义了） |
| **目标由谁创建** | 通常**代理自己创建/持有**目标（`VirtualImageProxy` 内部 `new RealImage`） | **由外部传入**，装饰器不关心目标怎么来 |
| **客户端是否知情** | 客户端**以为**自己在直接用目标 | 客户端**知道**自己在层层包装 |
| **返回值** | 通常原样返回或返回默认值 | 通常在返回值上加工 |
| **典型叠加数** | 1~3 层（鉴权→缓存→延迟） | 任意层，且**顺序影响结果** |

**一句话记忆**：
- 代理是**看门人**——「你能不能进去、什么时候进去、进去了要不要真的找老板，我说了算。」
- 装饰器是**化妆师**——「你进去吧，但我先给你化个妆，让你进去时更好看。」

**最快的代码级判别法**：看那个 `target.xxx()` 调用**是否可能被跳过**。
可能被跳过 → 代理；必然执行且在返回值上加工 → 装饰器。

---

## 4. 设计推导：这些类是怎么一步步"逼"出来的

这一节回答核心问题：**为什么需要这些接口、抽象类、实现类？为什么采用当前的继承/组合/委托关系？**

### 第 0 步：起点——客户端直接 new 真实对象

```java
Image image = new RealImage("photo.png");   // 构造时就加载了
image.display();
```

**痛点暴露**：`new RealImage(...)` 这个动作**立刻**触发了磁盘 IO。
但客户端可能只是想拿到一个"图片引用"，稍后才决定要不要显示。

### 第 1 步：想在 `new` 和"真正加载"之间插入一个决策点

问题的本质是：**"拥有对象引用"和"对象已就绪"这两件事被强行绑定了。**

要解绑，只能引入一个**中间对象**：它现在就能被创建（很便宜），但它内部**不持有**真实对象，
等到某个方法被调用时，它才决定要不要去创建真实对象。

```java
public class VirtualImageProxy {
    private RealImage realImage;        // ← 初始为 null，这就是"延迟"的物理实现
    private final String filename;      // ← 只存一个字符串，创建成本几乎为零

    public VirtualImageProxy(String filename) {
        this.filename = filename;       // ← 构造器里什么都不加载
    }

    public void display() {
        if (realImage == null) {                        // ← 决策点！
            realImage = new RealImage(filename);        // ← 决策结果：现在才加载
        }
        realImage.display();                            // ← 委托
    }
}
```

`private RealImage realImage;` 这个**非 final 的、初始为 null 的字段**，就是虚拟代理的全部技术核心。
延迟加载没有魔法，就是"把 new 从构造器挪到方法里"。

### 第 2 步：客户端必须能"无感"切换

现在有个问题：

```java
Image image = new RealImage("a.png");                          // 写法 1
VirtualImageProxy image = new VirtualImageProxy("a.png");      // 写法 2
```

如果客户端要改成写法 2，就得改**变量声明类型**，那所有用到 `image` 的地方都可能受影响。
更糟的是：某处代码需要真的立即加载（比如启动时的欢迎图），它得用写法 1；
另一处需要延迟加载，用写法 2。**同一份业务代码要区分两种类型，这就是耦合。**

**解决方案：让代理和真实对象实现同一个接口。**

```java
public interface Image {
    void display();
    String getFilename();
}

public final class RealImage implements Image { ... }
public class VirtualImageProxy implements Image { ... }
```

于是客户端可以写：

```java
Image image = getImageSomehow();   // 声明类型是接口，拿到的是谁都行
image.display();                   // 多态分发
```

**这就是为什么必须有 `Image` 这个抽象主题接口**：
它是代理和真实对象之间的**契约**，也是多态得以发生的前提。
没有它，代理就无法"冒充"真实对象，客户端就必须知道自己在用代理。

> **这就是里氏替换原则（LSP）的直接应用**：
> 任何使用 `Image` 的地方，都能无缝替换成 `RealImage` 或 `VirtualImageProxy`，行为不变（只是时机变了）。

### 第 3 步：为什么代理要 `implements Image`，而不是 `extends RealImage`

有人会想：既然要"冒充"，为什么不直接继承？

```java
public class VirtualImageProxy extends RealImage {   // ✗ 行不通
    public VirtualImageProxy(String filename) {
        super(filename);   // ← 这一行就立刻加载了！延迟加载彻底失败
    }
}
```

**继承会导致父类构造器被调用**，而 `RealImage` 的加载逻辑就在构造器里。
就算把加载逻辑挪出构造器，继承还有别的问题：

- `RealImage` 是 `final` 的（本次重构特意这么设计），无法被继承。
- 继承是**编译期静态绑定**，无法在运行时决定"这次用真实对象，那次用代理"。
- 继承会让代理**拥有**真实对象的所有内部状态（`data` 数组），
  而代理其实**不该有**这些状态——它只是控制访问，不该持有数据。

**所以代理模式必然采用组合（持有一个引用）而非继承。**
这也是"组合优于继承"原则最典型的例证之一。

### 第 4 步：加入鉴权——为什么不能写在 RealImage 里

需求变了：财务报表图片只能给 ADMIN/EDITOR 看。最直觉的写法是改 `RealImage.display()`：

```java
public void display() {
    if (!hasPermission()) { throw new SecurityException(); }   // ← 加在这里
    System.out.println("显示图片：" + filename);
}
```

**这个写法有三个硬伤**（`ProtectedImageProxy` 的 Javadoc 里也写了）：

1. **职责污染**：`RealImage` 的职责是"加载并渲染图片"。
   它凭什么知道什么是"角色"？加了之后，渲染逻辑和安全策略绑死在一个类里。
2. **顺序已经错了 —— 这是最致命的一点**：
   `RealImage` 的加载发生在**构造器**里。等你调用 `display()` 做鉴权时，
   图片**早就加载进内存了**。鉴权只是阻止了"显示"，没有阻止"消耗资源"。
   恶意用户可以循环 `new RealImage("机密.png")` 让你的服务器 OOM，鉴权一点用都没有。
3. **无法复用**：内部的批量缩略图生成任务不需要鉴权，但它要用 `RealImage`。
   只能加一个 `RealImage(String filename, boolean skipAuth)` 参数——然后这个布尔参数会传染到所有调用点。

**正确的解法**：把鉴权做成一层**包在外面**的代理，且让它包在 `VirtualImageProxy` 外面：

```java
Image secret = new VirtualImageProxy("财务报表.xlsx");      // 还没加载
Image guestView = new ProtectedImageProxy(secret, "GUEST"); // 套一层鉴权
guestView.display();   // ← 鉴权失败，直接 return，target.display() 根本没被调用
```

**实测输出（本项目 JDK 25 真实运行结果）**：

```
=== 2. 保护代理：权限控制 ===
      [VirtualProxy] 创建代理（未加载图片）：财务报表.xlsx
  -- 访客尝试查看 --
      [ProtectedProxy] × 拒绝访问：角色 'GUEST' 无权查看 财务报表.xlsx
      真实加载次数 = 0  ← 关键：鉴权失败时图片根本没被加载，内存和 IO 都省了
```

`真实加载次数 = 0` 这个数字，就是"代理必须包在延迟加载代理外面"这个设计决策的**可验证证据**。

**代理的嵌套顺序是有意义的**：
- `保护(虚拟(真实))` → 先鉴权，鉴权不过连对象都不创建。✅ 正确
- `虚拟(保护(真实))` → 先创建保护代理（很便宜），再鉴权。也能工作，但延迟加载的价值打折。
- 如果把鉴权放在最内层，就变成了"对象已加载，只是不让看"，等于回到上面的硬伤 2。

### 第 5 步：加入缓存——为什么 `target` 必须声明为 `Image` 而不是 `RealImage`

缓存代理需要 key，key 来自 `target.getFilename()`（第 2 步补的接口方法）。

```java
public class CachingImageProxy implements Image {
    private final Image target;      // ← 声明为接口类型，不是 RealImage
    private final Map<String, String> cache = new HashMap<>();
}
```

**如果声明成 `private final RealImage target`**：

```java
new CachingImageProxy(new RealImage("a.png"));              // 只能这样
new CachingImageProxy(new VirtualImageProxy("a.png"));      // ✗ 编译错误
```

你就永远无法组合"缓存 + 延迟加载"了。而这两个恰恰是**最常一起用**的：
先查缓存，缓存没有才延迟加载，加载完写缓存。

**声明为接口类型 `Image`，是让代理链成为可能的唯一前提。**
这就是**依赖倒置原则（DIP）**：高层模块（缓存代理）不依赖低层模块（真实图片），二者都依赖抽象（`Image`）。

### 第 6 步：静态代理的天花板——为什么需要动态代理

现在有 3 个静态代理类。假设业务扩展：`Image` 接口有 20 个方法，
公司里有 100 个接口都需要"日志 + 计时"。

**静态代理的成本**：
- 每个代理类要为**每个方法**手写一遍转发：`100 接口 × 20 方法 = 2000 个转发方法`。
- 每种增强要写一个类：日志代理、计时代理、鉴权代理……
  想同时要日志和计时，就得再写一个 `LoggingAndTimingProxy`（**类爆炸**）。
- 接口加一个方法，所有代理类都要跟着改，否则编译不过。

**观察一个事实**：这 2000 个转发方法**长得几乎一模一样**：

```java
public void methodA() { /* 加料 */ target.methodA(); }
public void methodB() { /* 加料 */ target.methodB(); }
public String methodC() { /* 加料 */ return target.methodC(); }
```

只有方法名、参数、返回值类型不同。**"只有元信息不同、逻辑完全相同"的代码，就应该被元编程消除。**

**动态代理的做法**：不写代理类，让 JVM 在运行时**生成**代理类，
所有方法调用统一路由到一个 `invoke(Object proxy, Method method, Object[] args)` 方法。

```java
public class ImageInvocationHandler implements InvocationHandler {
    private final Object target;   // ← 注意是 Object，不是 Image！

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 这一段代码，服务于 Image 接口的所有方法
        System.out.println("调用 " + method.getName());
        long start = System.nanoTime();
        Object result = method.invoke(target, args);
        System.out.println("耗时 " + (System.nanoTime() - start));
        return result;
    }
}
```

`private final Object target;` 声明为 `Object` 是刻意的——
这意味着**这一个 handler 类可以代理任何接口的任何对象**。
2000 个转发方法，变成了 1 个 `invoke`。

### 第 7 步：为什么 JDK 动态代理只能代理接口（面试高频）

这不是一个"规定"，而是一个**技术必然**。看 JVM 实际生成的类：

```java
public final class $Proxy0 extends java.lang.reflect.Proxy implements Image {
    private final InvocationHandler h;

    public $Proxy0(InvocationHandler h) { super(h); this.h = h; }

    @Override
    public void display() {
        h.invoke(this, m_display, null);        // 全部转发给 handler
    }
    @Override
    public String getFilename() {
        return (String) h.invoke(this, m_getFilename, null);
    }
}
```

**`extends java.lang.reflect.Proxy` 用掉了 Java 单继承的唯一名额。**
它已经没有机会再 `extends RealImage` 了，所以只能 `implements Image`。这就是根本原因。由此推出：

- **JDK 动态代理必须有接口**。
- 要代理**没有接口的类**，只能用 CGLIB / ByteBuddy，它们的原理是**生成目标类的子类**
  （`class RealImage$$EnhancerByCGLIB extends RealImage`），
  因此目标类和目标方法**都不能是 `final`**（`final` 不能被继承/覆盖）。
- Spring AOP 的策略：**目标类实现了接口 → 可用 JDK 动态代理；没有接口 → 用 CGLIB。**
  Spring Boot 2.x 之后默认强制 CGLIB（`spring.aop.proxy-target-class=true`），
  原因是 JDK 代理会导致"注入的 Bean 不能声明为实现类类型"这一常见坑。

**本项目实测输出**（JDK 25）：

```
proxy.getClass().getName() = jdk.proxy1.$Proxy0
```

注意前缀 `jdk.proxy1`——这是 JDK 9 模块化之后引入的：
动态代理类被放进一个**运行时才创建的动态模块**里，而不是无名包。
所以判断"这是不是代理对象"不要写 `name.equals("$Proxy0")`，要用 `Proxy.isProxyClass(clazz)`。

### 结构总览

```
                    ┌──────────────┐
                    │   Client     │  只认识 Image 接口
                    └──────┬───────┘
                           │ 依赖抽象
                           ▼
                  ◇─────────────────◇
                 ╱   <<interface>>    ╲
                ╱       Image          ╲
               ╱  + display()           ╲
              ╱   + getFilename()        ╲
             ╱                            ╲
   ┌────────────────┐          ┌───────────────────────┐
   │   RealImage    │          │  VirtualImageProxy    │
   │  (真实主题)     │          │  (虚拟代理)            │
   ├────────────────┤          ├───────────────────────┤
   │ - filename     │          │ - filename            │
   │ - loadCount$   │          │ - realImage: RealImage│ ← 初始 null
   ├────────────────┤          ├───────────────────────┤
   │ + display()    │          │ + display()           │
   │ + getFilename()│          │ + getFilename()       │
   │ - loadFromDisk │          │ + isLoaded()          │
   └────────────────┘          └───────────┬───────────┘
              ▲                            │
              │ 最终委托                     │ 可作为 target
              │                            │
   ┌──────────┴──────────────────────────┴────────────────┐
   │  ProtectedImageProxy         CachingImageProxy        │
   │  (保护代理)                   (缓存代理)                │
   ├───────────────────────────────────────────────────────┤
   │ - target: Image              - target: Image           │
   │ - currentUserRole            - cache: Map<String,String>│
   │ - ALLOWED_ROLES$             - hitCount / missCount    │
   ├───────────────────────────────────────────────────────┤
   │ + display()  ← 鉴权          + display() ← 查缓存       │
   │ + getFilename() ← 转发       + getFilename() ← 转发     │
   │                              + invalidate()            │
   └───────────────────────────────────────────────────────┘

   ┌───────────────────────────────────────────────────────┐
   │  动态代理（运行时生成，源码里不存在对应文件）             │
   ├───────────────────────────────────────────────────────┤
   │  DynamicImageProxy.wrap(target, role)                  │
   │      └─ Proxy.newProxyInstance(cl, {Image.class}, h)   │
   │              └─ ImageInvocationHandler                 │
   │                   - target: Object   ← 可代理任何接口   │
   │                   - role: String                       │
   │                   + invoke(proxy, method, args)        │
   │                        日志 → 鉴权 → 计时 → 反射调用     │
   └───────────────────────────────────────────────────────┘

   ◇ = 实现关系    $ = 静态成员    → = 委托方向
```

**代理链的运行时对象结构**（demo4 实测的 `EDITOR` 那条链）：

```
Client 持有
  │
  ▼
ProtectedImageProxy(role="EDITOR")
  │ target
  ▼
CachingImageProxy(cache={})
  │ target
  ▼
VirtualImageProxy(filename="report.pdf", realImage=null)
  │ realImage（首次 display 后才非 null）
  ▼
RealImage(filename="report.pdf")
```

**四层对象，只有一个 `RealImage` 在真正干活。**
每一层都在做一件事，且**每一层都可以被单独移除**——这就是可插拔。

---

## 5. 代码解析：每个类为什么必须存在

### 5.1 `Image`（抽象主题）

```java
public interface Image {
    void display();
    String getFilename();
}
```

**为什么存在**：它是代理能"冒充"真实对象的**唯一前提**。
客户端声明 `Image image = ...`，就再也不用关心拿到的是真实对象还是第几层代理。

**`getFilename()` 是本次重构新增的**，理由见第 2 节问题 1。
它的存在让"廉价元数据查询"和"昂贵业务操作"在接口层面被区分开，两个代理都依赖它：
- 保护代理用它打印「无权查看 XXX」（拒绝时也要说清拒的是什么）
- 缓存代理用它做缓存 key

**如果删掉这个接口会怎样？**
- `RealImage` 和 `VirtualImageProxy` 之间没有共同类型。
- 客户端必须写死具体类型，代理链彻底无法组合。
- `new CachingImageProxy(new VirtualImageProxy(...))` 编译不过。
- **代理模式退化为一堆互不相干的类，模式本身消失。**

**为什么用 `interface` 而不是 `abstract class`？**
- 代理和目标之间**没有共享状态**（代理不该持有图片数据），没有需要复用的实现代码。
- `RealImage` 是 `final` 的，用抽象类会强制它继承，反而限制了它。
- 用接口才能让 JDK 动态代理生效（第 4 节第 7 步）。
- **判据**：需要共享字段或默认实现 → 抽象类；只定义契约 → 接口。这里只需要契约。

### 5.2 `RealImage`（真实主题）

**为什么存在**：它是**真正干活的那个对象**。代理模式的所有价值都建立在
"真实对象的操作足够昂贵"这个前提上——如果 `RealImage` 的构造只要 1 纳秒，代理就是纯粹的开销。

**三个设计决策**：

1. **`final class`**：真实主题不需要被继承。
   声明 `final` 明确表达"这是终点，代理才是扩展方式"，
   同时避免有人误用 `extends RealImage` 去做代理（第 4 节第 3 步已论证这条路走不通）。
2. **`loadFromDisk()` 是私有方法而非内联在构造器**：
   让"加载"这个动作有名字、有边界，便于阅读、便于计数、便于将来改成异步加载。
3. **`static int loadCount`**：教学仪器，让"少加载了几次"变成可打印的数字。
   生产代码不该有这种全局计数器（它是静态可变状态，多线程不安全），
   但在 Demo 里它是**证明代理有效**的最直接手段。
   `resetLoadCount()` 让 `Client` 的每段演示互相独立。

**如果删掉这个类会怎样？**
代理没有目标，`target.display()` 无从委托。整个模式变成空壳——
你只剩下一堆"什么都不做只是转发"的类。

**如果把加载逻辑合并进 `VirtualImageProxy` 会怎样？**

```java
public class VirtualImageProxy implements Image {
    private byte[] data;
    public void display() {
        if (data == null) data = readFromDisk(filename);   // 加载逻辑在这
        render(data);                                       // 渲染逻辑也在这
    }
}
```

→ `VirtualImageProxy` 变成了 `RealImage` 的复制品，只是加载时机不同。
→ 保护代理和缓存代理如果想直接包一个"立即加载的图片"（比如启动画面），就没有 `RealImage` 可用了。
→ **"真实主题"和"延迟加载策略"混在一个类里，无法单独使用任何一个。**

### 5.3 `VirtualImageProxy`（虚拟代理）

```java
public class VirtualImageProxy implements Image {
    private RealImage realImage;      // ← 非 final！初始为 null
    private final String filename;

    @Override
    public void display() {
        if (realImage == null) {
            realImage = new RealImage(filename);
        }
        realImage.display();
    }

    @Override
    public String getFilename() {
        return filename;      // ← 返回自己存的字符串，不触发加载
    }

    public boolean isLoaded() { return realImage != null; }
}
```

**为什么 `realImage` 不能是 `final`？**
因为 `final` 字段必须在构造器里赋值。而"在构造器里赋值"就意味着"立即创建"，
延迟加载直接失效。**这个非 final 字段是虚拟代理的物理本质。**

**为什么 `getFilename()` 返回 `this.filename` 而不是 `realImage.getFilename()`？**
因为后者会 NPE（`realImage` 可能是 null），而且**会诱导你去创建 realImage**。
代理自己存一份 `filename`，就是为了能在**目标不存在时**也回答关于目标的问题。

**这也是虚拟代理的通用形态**：代理持有创建真实对象**所需的信息**（这里是文件名），
但不持有真实对象本身。Hibernate 的懒加载代理持有的是主键 ID，同理。

**`isLoaded()` 是本次重构新增的**，它让"延迟是否真的生效"可被断言：

```
img1.isLoaded() = true
img2.isLoaded() = false
img3.isLoaded() = false
```

**线程安全提示**：这个实现**不是线程安全的**。
两个线程同时首次调用 `display()`，可能创建两个 `RealImage`。生产环境的写法：

```java
// 方案 A：双重检查锁（要求 realImage 是 volatile）
private volatile RealImage realImage;
public void display() {
    RealImage r = realImage;
    if (r == null) {
        synchronized (this) {
            r = realImage;
            if (r == null) { r = new RealImage(filename); realImage = r; }
        }
    }
    r.display();
}

// 方案 B：Supplier + 记忆化（更简洁）
private final Supplier<RealImage> holder = memoize(() -> new RealImage(filename));
```

**本项目保持非线程安全版本**，因为加锁会把读者的注意力从"延迟加载"这个核心概念上引开。
但必须明确说明这一点——这是 Demo 与生产代码的差距。

### 5.4 `ProtectedImageProxy`（保护代理）

```java
public class ProtectedImageProxy implements Image {
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "EDITOR", "VIEWER");
    private final Image target;             // ← 接口类型，可以包任何 Image
    private final String currentUserRole;

    @Override
    public void display() {
        if (!ALLOWED_ROLES.contains(currentUserRole)) {
            System.out.println("      [ProtectedProxy] × 拒绝访问：角色 '"
                    + currentUserRole + "' 无权查看 " + target.getFilename());
            return;                          // ← 关键：直接返回，target.display() 不执行
        }
        System.out.println("      [ProtectedProxy] √ 鉴权通过（角色 " + currentUserRole + "），放行");
        target.display();
    }

    @Override
    public String getFilename() {
        return target.getFilename();         // ← 不敏感，直接转发，不鉴权
    }
}
```

**核心是那个 `return`**。保护代理和虚拟代理的本质区别就在这里：
- 虚拟代理是**延迟**调用（早晚会调）
- 保护代理是**可能不调用**（鉴权失败就永远不调）

**为什么 `getFilename()` 不鉴权？**
这体现了一个重要原则：**保护代理只需保护"敏感方法"**。
文件名不是机密（列表页要显示图片名），图片内容才是。
如果给每个方法都加一遍权限检查，会写出大量无意义的重复代码。

**为什么角色通过构造器传入，而不是每次调用传参？**
因为"当前用户是谁"在一次会话里是**固定的**。
如果写成 `display(String role)`，就改变了 `Image` 接口的签名，
`RealImage` 也被迫接受一个它根本不需要的参数——**抽象被污染了**。

**为什么用 `Set.of(...)` 而不是 `if (role.equals("ADMIN") || role.equals("EDITOR") || ...)`？**
- 新增角色只改一行常量，不改判断逻辑（开闭原则）
- `Set.contains()` 语义清晰，`Set.of` 返回的不可变集合有 O(1) 查找
- 避免了 `role.equals("ADMIN")` 在 `role` 为 null 时的 NPE

**真实场景的差异**：生产代码里 `currentUserRole` 不会通过构造器传，
而是从 `SecurityContextHolder.getContext().getAuthentication()` 之类的**线程上下文**里取。
本项目用构造器传是为了让 Demo 可运行、可预测（不依赖任何框架）。

### 5.5 `CachingImageProxy`（缓存代理）

```java
public class CachingImageProxy implements Image {
    private final Map<String, String> cache = new HashMap<>();
    private final Image target;
    private int hitCount = 0;
    private int missCount = 0;

    @Override
    public void display() {
        String key = target.getFilename();       // ← 依赖 Image 接口的 getFilename()
        String snapshot = cache.get(key);

        if (snapshot != null) {
            hitCount++;
            System.out.println("      [CacheProxy] ◆ 缓存命中，跳过真实渲染");
            return;                              // ← 命中就直接返回，真实对象完全不参与
        }

        missCount++;
        target.display();                        // 真正干活
        cache.put(key, "bitmap-snapshot-of-" + key + "@" + System.nanoTime());
    }

    public void invalidate() { cache.clear(); hitCount = 0; missCount = 0; }
}
```

**为什么要写 `invalidate()`？**
这是**缓存代理最容易被忽略的责任**。缓存一致性问题是生产环境最常见的 bug 来源：
图片文件被替换了，但缓存里还是旧快照，用户永远看到旧图。

**真实场景的淘汰策略**：本 Demo 用 `HashMap` 永久缓存，会**内存泄漏**。生产环境要用：
- `LinkedHashMap` + `removeEldestEntry`（简易 LRU）
- Caffeine（`maximumSize` + `expireAfterWrite`，带 Window TinyLFU 淘汰算法）
- Redis（分布式缓存，带 TTL）

**Demo 保持 `HashMap` 是有意的**：换成 Caffeine 会引入第三方依赖，
违背"优先使用原生 Java"的原则，也会让读者把注意力放在缓存库的 API 上而不是代理模式本身。
但必须知道它的局限。

**为什么缓存的是"快照字符串"而不是"渲染结果对象"？**
Demo 里用字符串模拟。真实场景缓存的是 `BufferedImage` 或渲染好的位图字节。
注意：**如果缓存的是可变对象，必须返回副本**，否则调用方改了缓存里的对象，
下一个调用方会看到被污染的数据。这是缓存代理的隐藏陷阱。

### 5.6 `ImageInvocationHandler` + `DynamicImageProxy`（动态代理）

**为什么这两个类要分开？**

- `ImageInvocationHandler`：**行为**——所有方法调用被路由到这里，决定做什么增强。
- `DynamicImageProxy`：**创建**——一个静态工厂，封装 `Proxy.newProxyInstance` 的三个参数。

如果合并成一个类，客户端就要写：

```java
Image proxy = (Image) Proxy.newProxyInstance(
        target.getClass().getClassLoader(),          // 每次都要想：用哪个 ClassLoader？
        new Class<?>[]{Image.class},                 // 每次都要写：代理哪些接口？
        new ImageInvocationHandler(target, role));   // 还要强转
```

三行样板代码 + 一个容易写错的强转，每个调用点都要重复。
`DynamicImageProxy.wrap(target, role)` 把它压成一行，且**强转只发生在一处**。

**`DynamicImageProxy` 的私有构造器**：

```java
private DynamicImageProxy() { throw new AssertionError("工具类不允许实例化"); }
```

纯静态工厂类不该被实例化。抛 `AssertionError` 而不是 `UnsupportedOperationException`，
是因为"实例化工具类"是**程序员的错误**（Error），不是**运行环境的例外**（Exception）。

**`invoke()` 里的三个生产级细节**：

**① 为什么是 `method.invoke(target, args)` 而不是 `method.invoke(proxy, args)`？**

```java
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return method.invoke(proxy, args);   // ✗ 无限递归 → StackOverflowError
}
```

`proxy` 是那个 `$Proxy0` 对象。调用它的方法 → 又进入 `invoke` → 又调用 `proxy` → ……
**这是动态代理最经典的新手错误。** 必须调用 `target`（真实对象）。

**② 为什么要解包 `InvocationTargetException`？**

```java
} catch (java.lang.reflect.InvocationTargetException e) {
    throw e.getCause();     // ← 必须解包
}
```

反射调用时，目标方法抛出的异常会被 JDK **包装**在 `InvocationTargetException` 里。
如果不解包直接抛，调用方会看到：

```java
try {
    image.display();
} catch (ImageNotFoundException e) {   // ← 永远捕获不到！
    ...
}
```

实际抛出的是 `InvocationTargetException`，业务代码的 `catch` 全部失效。
**Spring AOP 也做了同样的解包**，所以你用 `@Transactional` 时感觉不到反射的存在。

**③ 为什么要写 `defaultValue(method.getReturnType())`？**

```java
if (鉴权失败) {
    return defaultValue(method.getReturnType());   // 而不是简单 return null
}
```

假设 `Image` 有个方法 `boolean isValid()`。鉴权失败时 handler 返回 `null`，调用方写：

```java
if (image.isValid()) { ... }   // ← null 自动拆箱成 boolean → NullPointerException
```

**NPE 出现在调用方，而调用方完全看不出问题在哪**——它以为自己调的是一个正常的方法。
这是动态代理的第二大经典坑。所以必须为基本类型返回合理的默认值：
`boolean → false`、`int → 0`、`char → '\0'`、`void → null`……

**④ 无参方法的 `args` 是 `null`，不是空数组**（本次重构修的细节）：

```java
System.out.println("参数 " + (args == null ? "[]" : Arrays.toString(args)));
```

JDK 的约定是：无参方法调用时 `invoke()` 收到的 `args` 是 `null`。
直接 `Arrays.toString(null)` 返回字符串 `"null"`，日志看起来像"传了一个 null 参数"，容易误导排查。

**`private final Object target;` 为什么声明成 `Object`？**
这样这个 handler 就**不绑定 `Image` 接口**了，理论上可以代理任何接口。
把它改成通用的 `LoggingHandler`，就能给全项目所有 Service 加日志——
**这就是 Spring AOP 的实现思路**（Spring 的 `JdkDynamicAopProxy` 内部持有的是 `Object target` + 一个拦截器链）。


---

## 6. 执行流程：从 main 方法到控制台输出

以下是本项目在 **JDK 25（`F:\enCodingApps\JDK\jdk-25`）下的真实运行输出**，命令：

```powershell
javac --release 25 -encoding UTF-8 -d out (Get-ChildItem src\com\patterns\structural\proxy -Recurse -Filter *.java).FullName
java "-Dfile.encoding=UTF-8" -cp out com.patterns.structural.proxy.Client
```

`main` 方法依次调用 6 个 demo：

```java
public static void main(String[] args) {
    demo0WithoutProxy();      // 对照基线：没有代理会怎样
    demo1VirtualProxy();      // 延迟加载
    demo2ProtectedProxy();    // 权限控制
    demo3CachingProxy();      // 缓存
    demo4ProxyChain();        // 三层嵌套（最能体现代理威力）
    demo5DynamicProxy();      // JDK 动态代理
}
```

每段开头都调用 `RealImage.resetLoadCount()`，让各段统计**互相独立**。

### 6.0 demo0：不用代理的对照基线

```
=== 0. 不用代理：构造即加载，无法延迟 ===
  -- 创建对象（还没打算显示，但已经被迫加载了）--
      [RealImage] ★ 从磁盘加载图片：photo.png（第 1 次真实加载）
  -- 假设业务判断后决定不显示这张图 --
      （图片已经白白加载进内存了，真实加载次数 = 1）
```

**逐步拆解**：

| 步骤 | 代码 | 发生了什么 | 关键变量 |
|-----|------|-----------|---------|
| 1 | `RealImage.resetLoadCount()` | 静态计数器归零 | `loadCount = 0` |
| 2 | `Image image = new RealImage("photo.png")` | 进入 `RealImage` 构造器 | — |
| 3 | 构造器内参数校验 + `this.filename = filename` | 字段赋值 | `filename = "photo.png"` |
| 4 | 构造器内 `loadFromDisk()` | **立即**执行昂贵 IO | `loadCount: 0 → 1` |
| 5 | 打印 `★ 从磁盘加载图片` | 副作用可见 | — |
| 6 | 构造器返回 | `image` 赋值完成，**图片已在内存** | `image != null` |
| 7 | 业务决定不显示 | 什么都没做 | `loadCount` 仍为 1 |

**这段的价值**：它证明了"没有代理时，`new` 和"加载"是**原子绑定**的"。
第 6 步和第 7 步之间，客户端**没有任何干预手段**——对象一旦创建，资源就已经消耗了。
这是后面 5 段演示要解决的共同问题。

### 6.1 demo1：虚拟代理延迟加载

```
=== 1. 虚拟代理：延迟加载 ===
  -- 创建 3 个代理，一个都没加载图片 --
      [VirtualProxy] 创建代理（未加载图片）：banner.jpg
      [VirtualProxy] 创建代理（未加载图片）：avatar.png
      [VirtualProxy] 创建代理（未加载图片）：logo.svg
      真实加载次数 = 0（3 个代理对象只占了 3 个字符串的内存）

  -- 用户只点开了 img1 --
      [VirtualProxy] 首次访问，现在才创建真实对象
      [RealImage] ★ 从磁盘加载图片：banner.jpg（第 1 次真实加载）
      [RealImage] 显示图片：banner.jpg
      真实加载次数 = 1

  -- 再显示一次 img1（对象已存在，不再加载）--
      [VirtualProxy] 真实对象已存在，直接复用
      [RealImage] 显示图片：banner.jpg
      真实加载次数 = 1

  -- 验证：img2/img3 从未被访问，所以从未加载 --
      img1.isLoaded() = true
      img2.isLoaded() = false
      img3.isLoaded() = false
      getFilename() 不会触发加载：avatar.png，isLoaded 仍为 false

      结论：加载了 1 张而不是 3 张，节省 2 次昂贵 IO
```

**调用栈展开（`img1.display()` 第一次）**：

```
Client.demo1VirtualProxy()
 └─ VirtualImageProxy.display()                    [img1]
     ├─ 判断 realImage == null                     → true（首次）
     ├─ new RealImage("banner.jpg")
     │   └─ RealImage.<init>(String)
     │       ├─ this.filename = "banner.jpg"
     │       └─ loadFromDisk()
     │           ├─ loadCount++                    → loadCount = 1
     │           └─ println("★ 从磁盘加载图片...")
     ├─ this.realImage = <上面创建的对象>
     └─ realImage.display()
         └─ println("显示图片：banner.jpg")
```

**关键变量追踪表**：

| 时刻 | `img1.realImage` | `img2.realImage` | `img3.realImage` | `RealImage.loadCount` |
|-----|-----------------|-----------------|-----------------|---------------------|
| 创建 3 个代理后 | `null` | `null` | `null` | **0** |
| `img1.display()` 第 1 次 | `RealImage@banner` | `null` | `null` | **1** |
| `img1.display()` 第 2 次 | `RealImage@banner`（同一对象） | `null` | `null` | **1**（未变） |
| `img2.getFilename()` 后 | 同上 | `null`（未变！） | `null` | **1**（未变） |

**最后一行是本段演示最有价值的数据点**：
调用 `getFilename()` **没有**触发加载。这证明了第 2 节的接口设计决策是对的——
如果 `VirtualImageProxy.getFilename()` 写成 `return realImage.getFilename()`，
这一行就会变成 `isLoaded 仍为 true`，`loadCount` 变成 2。

**为什么第二次 display 不再加载？**
因为 `realImage` 字段已经被赋值，`if (realImage == null)` 判断为 false，走 `else` 分支打印"直接复用"。
**虚拟代理 = 延迟加载 + 单例语义**（每个代理最多创建一个真实对象）。

**量化收益**：3 张图只加载了 1 张。
在真实场景里，一个电商列表页有 100 张商品图，用户只点开了 3 张详情页——
**节省 97 次磁盘 IO + 97 × 20MB ≈ 1.9GB 内存**。这就是虚拟代理的商业价值。

### 6.2 demo2：保护代理

```
=== 2. 保护代理：权限控制 ===
      [VirtualProxy] 创建代理（未加载图片）：财务报表.xlsx
  -- 访客尝试查看 --
      [ProtectedProxy] × 拒绝访问：角色 'GUEST' 无权查看 财务报表.xlsx
      真实加载次数 = 0  ← 关键：鉴权失败时图片根本没被加载，内存和 IO 都省了

  -- 管理员查看 --
      [ProtectedProxy] √ 鉴权通过（角色 ADMIN），放行
      [VirtualProxy] 首次访问，现在才创建真实对象
      [RealImage] ★ 从磁盘加载图片：财务报表.xlsx（第 1 次真实加载）
      [RealImage] 显示图片：财务报表.xlsx
      真实加载次数 = 1

      getFilename() 不敏感，无需鉴权：财务报表.xlsx
```

**对象创建顺序（重点）**：

```java
Image secret    = new VirtualImageProxy("财务报表.xlsx");        // ① 只创建虚拟代理，loadCount 仍为 0
Image adminView = new ProtectedImageProxy(secret, "ADMIN");      // ② 套第一层壳（同一个 secret）
Image guestView = new ProtectedImageProxy(secret, "GUEST");      // ③ 套第二层壳（同一个 secret）
```

**注意 ② 和 ③ 包的是同一个 `secret` 对象**。
两个不同权限的"视图"共享同一个底层数据源。这在真实系统里很常见：
同一个 Service Bean，被两个不同角色的代理包装后分发给不同的 Controller。

**`guestView.display()` 的调用栈**：

```
Client.demo2ProtectedProxy()
 └─ ProtectedImageProxy.display()                  [guestView, role="GUEST"]
     ├─ ALLOWED_ROLES.contains("GUEST")            → false
     ├─ target.getFilename()                       → "财务报表.xlsx"（不触发加载！）
     ├─ println("× 拒绝访问...")
     └─ return                                     ← 到此结束
                                                    ✗ target.display() 从未被调用
                                                    ✗ RealImage 从未被创建
                                                    ✗ loadCount 保持 0
```

**`真实加载次数 = 0` 是这段演示的核心结论。**

对比第 2 节问题 3 里"把鉴权写进 RealImage"的方案：那种写法下，
`new RealImage("财务报表.xlsx", "GUEST")` 这一行就已经把图片加载进内存了，
鉴权只能阻止"显示"，无法阻止"消耗资源"。**loadCount 会是 1，而不是 0。**

**一个数字的差别，就是"鉴权有效"和"鉴权形同虚设"的差别。**

**最后一行 `getFilename() 不敏感，无需鉴权`**：
`guestView.getFilename()` 走的是 `ProtectedImageProxy.getFilename()`，
它没有鉴权逻辑，直接 `return target.getFilename()`。
访客能看到文件名（列表页需要），但看不到内容。**权限的粒度是方法级的。**

### 6.3 demo3：缓存代理

```
=== 3. 缓存代理：避免重复渲染 ===
      [VirtualProxy] 创建代理（未加载图片）：hero.png
  -- 第 1 次显示（未命中）--
      [CacheProxy] ◇ 缓存未命中，转发给真实对象
      [VirtualProxy] 首次访问，现在才创建真实对象
      [RealImage] ★ 从磁盘加载图片：hero.png（第 1 次真实加载）
      [RealImage] 显示图片：hero.png
  -- 第 2 次显示（命中）--
      [CacheProxy] ◆ 缓存命中，跳过真实渲染（第 1 次命中）
      [CacheProxy]   使用快照：bitmap-snapshot-of-hero.png@8613488310100
  -- 第 3 次显示（命中）--
      [CacheProxy] ◆ 缓存命中，跳过真实渲染（第 2 次命中）
      [CacheProxy]   使用快照：bitmap-snapshot-of-hero.png@8613488310100

      命中 2 次，未命中 1 次，真实加载 1 次

  -- 图片文件被修改，必须让缓存失效 --
      [CacheProxy] 缓存已清空（原 1 条）
      [CacheProxy] ◇ 缓存未命中，转发给真实对象
      [VirtualProxy] 真实对象已存在，直接复用
      [RealImage] 显示图片：hero.png
      失效后重新加载，真实加载次数 = 1
```

**注意这里的结构**：`new CachingImageProxy(new VirtualImageProxy("hero.png"))`——
缓存代理**已经在包虚拟代理了**，这是一个两层的代理链。
它能这么写，全靠 `CachingImageProxy` 的构造参数声明为 `Image` 而不是 `RealImage`（第 4 节第 5 步）。

**缓存状态变化表**：

| 时刻 | `cache` 内容 | `hitCount` | `missCount` | `loadCount` |
|-----|------------|-----------|------------|------------|
| 创建代理后 | `{}` | 0 | 0 | 0 |
| 第 1 次 display | `{hero.png → ...@8613488310100}` | 0 | **1** | **1** |
| 第 2 次 display | 同上（未变） | **1** | 1 | 1（未变） |
| 第 3 次 display | 同上（未变） | **2** | 1 | 1（未变） |
| `invalidate()` 后 | `{}` | 0 | 0 | 1 |
| 第 4 次 display | `{hero.png → ...@新时间戳}` | 0 | **1** | 1（**仍未变**） |

**最后两行有一个非常值得注意的细节**：
`invalidate()` 之后重新 display，`loadCount` **没有增加**，仍是 1。

为什么？因为**缓存代理清空的只是"渲染快照"，虚拟代理里的 `RealImage` 对象还活着**
（输出里的 `[VirtualProxy] 真实对象已存在，直接复用` 就是证据）。
缓存失效只省掉了"重新渲染"，没有省掉"重新加载"。

**这揭示了一个真实的工程问题**：多层代理各自的缓存/状态是**独立**的，
`invalidate()` 只作用于当前这一层。如果需求是"图片文件被改了，要重新从磁盘读"，
就必须让 `VirtualImageProxy` 也支持失效（把 `realImage` 置回 null）。

**这是本 Demo 有意保留的"不完美"**——它真实反映了缓存分层的复杂性，
比"一切都能一键刷新"的理想化演示更有教学价值。详见第 9 节自测题 5。

**命中率计算**：2 / (2+1) ≈ 66.7%。真实场景里图片被反复访问的比例远高于此，
浏览器 HTTP 缓存的命中率通常能到 90% 以上。

### 6.4 demo4：代理链

```
=== 4. 代理链：鉴权 → 缓存 → 延迟加载 → 真实对象 ===
      [VirtualProxy] 创建代理（未加载图片）：report.pdf
  -- 无权限用户（换成 GUEST 会怎样？见下）--
      [ProtectedProxy] √ 鉴权通过（角色 EDITOR），放行
      [CacheProxy] ◇ 缓存未命中，转发给真实对象
      [VirtualProxy] 首次访问，现在才创建真实对象
      [RealImage] ★ 从磁盘加载图片：report.pdf（第 1 次真实加载）
      [RealImage] 显示图片：report.pdf
  -- 第二次（应命中缓存）--
      [ProtectedProxy] √ 鉴权通过（角色 EDITOR），放行
      [CacheProxy] ◆ 缓存命中，跳过真实渲染（第 1 次命中）
      [CacheProxy]   使用快照：bitmap-snapshot-of-report.pdf@8613497973300

      [VirtualProxy] 创建代理（未加载图片）：salary.xlsx
      [ProtectedProxy] × 拒绝访问：角色 'GUEST' 无权查看 salary.xlsx
      整条链路在第 1 层就被截断，真实加载次数 = 1
```

**这是本项目最能体现代理模式威力的一段。** 构造代码只有 4 行：

```java
Image image = new ProtectedImageProxy(               // 第 1 层：先鉴权
        new CachingImageProxy(                       // 第 2 层：再查缓存
                new VirtualImageProxy("report.pdf")  // 第 3 层：延迟加载
        ), "EDITOR");
```

**四层调用栈完整展开（第 1 次 display）**：

```
Client.demo4ProxyChain()
 └─ ProtectedImageProxy.display()               [role="EDITOR"]
     ├─ ALLOWED_ROLES.contains("EDITOR")        → true，放行
     ├─ println("√ 鉴权通过")
     └─ target.display()
         └─ CachingImageProxy.display()
             ├─ key = target.getFilename()
             │   └─ VirtualImageProxy.getFilename()  → "report.pdf"（不触发加载）
             ├─ cache.get("report.pdf")         → null，未命中
             ├─ missCount++                     → 1
             ├─ target.display()
             │   └─ VirtualImageProxy.display()
             │       ├─ realImage == null       → true
             │       ├─ new RealImage("report.pdf")
             │       │   └─ loadFromDisk()      → loadCount = 1
             │       └─ realImage.display()
             │           └─ println("显示图片：report.pdf")
             └─ cache.put("report.pdf", "bitmap-snapshot-of-...")
```

**四层对象，一次调用穿透四层，只有最内层的 `RealImage` 在真正干活。**

**第 2 次 display 的调用栈被"截断"在第 2 层**：

```
Client.demo4ProxyChain()
 └─ ProtectedImageProxy.display()               → 鉴权通过
     └─ CachingImageProxy.display()
         ├─ cache.get("report.pdf")             → 命中！
         ├─ hitCount++                          → 1
         ├─ println("◆ 缓存命中")
         └─ return                              ← 到此结束
                                                 ✗ VirtualImageProxy.display() 未被调用
                                                 ✗ RealImage.display() 未被调用
```

**GUEST 那条链被"截断"在第 1 层**：

```
Client.demo4ProxyChain()
 └─ ProtectedImageProxy.display()               [role="GUEST"]
     ├─ ALLOWED_ROLES.contains("GUEST")         → false
     ├─ target.getFilename()                    → "salary.xlsx"（穿透 2 层，但不触发加载）
     ├─ println("× 拒绝访问")
     └─ return                                  ← 到此结束
                                                 ✗ CachingImageProxy.display() 未被调用
                                                 ✗ VirtualImageProxy.display() 未被调用
                                                 ✗ RealImage 从未被创建
```

**`真实加载次数 = 1` 证明：GUEST 的这次尝试没有产生任何新的加载**
（那个 1 是前面 EDITOR 加载 report.pdf 留下的，因为 `resetLoadCount()` 在段首只调了一次）。

**注意 `target.getFilename()` 穿透了两层代理**：
`ProtectedImageProxy` → `CachingImageProxy` → `VirtualImageProxy` → 返回字符串。
每一层都只是转发，**没有任何一层触发加载**。
这就是"元数据方法与业务方法分离"的价值：**代理可以在不付出代价的情况下回答关于目标的问题。**

**这正是 Spring AOP 的运作方式**。一个标注了多个注解的 Service：

```java
@Service
public class OrderService {
    @Transactional                       // 事务代理
    @Cacheable("orders")                 // 缓存代理
    @PreAuthorize("hasRole('ADMIN')")    // 鉴权代理
    @Timed                               // 监控代理
    public Order getOrder(long id) { ... }
}
```

Spring 会生成一条**拦截器链（Interceptor Chain）**，
调用 `getOrder()` 时依次穿过 `TransactionInterceptor` → `CacheInterceptor` → `SecurityInterceptor` → 真实方法。
和本 Demo 的三层嵌套是**完全相同的机制**，只是 Spring 用 `MethodInterceptor` 列表代替了对象嵌套，
这样增删一个横切关注点不需要重新包装对象。

**顺序有意义吗？** 有，而且很重要：

- 鉴权必须在最外层：鉴权失败就不该有任何后续动作
  （连缓存都不该查——缓存命中本身就可能泄露"这个资源存在"这一信息）。
- 缓存应该在事务内层还是外层？取决于你要不要缓存"事务内的中间结果"。
  Spring 默认 `@Cacheable` 在 `@Transactional` **外层**（先查缓存，命中就不开事务）。
- 监控应该在最外层（记录完整的端到端耗时）还是最内层（只记录业务方法耗时）？看你的指标定义。

### 6.5 demo5：JDK 动态代理

```
=== 5. JDK 动态代理：日志 + 计时 + 鉴权，零手写转发 ===
      [VirtualProxy] 创建代理（未加载图片）：dynamic.png
  -- 代理对象的运行时类型 --
      proxy.getClass().getName() = jdk.proxy1.$Proxy0
      是 JVM 运行时生成的类，源码里根本不存在这个文件

  -- 调用 getFilename()（非敏感方法，放行）--
      [DynamicProxy] → 调用 getFilename，参数 []，角色 ADMIN
      [DynamicProxy] ← getFilename 返回，耗时 585 μs
      返回：dynamic.png

  -- 调用 display()（敏感方法，ADMIN 放行）--
      [DynamicProxy] → 调用 display，参数 []，角色 ADMIN
      [VirtualProxy] 首次访问，现在才创建真实对象
      [RealImage] ★ 从磁盘加载图片：dynamic.png（第 1 次真实加载）
      [RealImage] 显示图片：dynamic.png
      [DynamicProxy] ← display 返回，耗时 1879 μs

      [VirtualProxy] 创建代理（未加载图片）：secret.png
  -- 同一份 handler 代码，换个角色就拦截 --
      [DynamicProxy] → 调用 display，参数 []，角色 ANONYMOUS
      [DynamicProxy] × 权限不足，拦截 display
      真实加载次数 = 1（拦截发生在加载之前）

      对比静态代理：3 种代理 = 3 个类，每个方法都要手写转发。
      动态代理：1 个 InvocationHandler 搞定全部方法、全部增强。
```

**`jdk.proxy1.$Proxy0` 是怎么来的？**

```java
Image proxy = DynamicImageProxy.wrap(new VirtualImageProxy("dynamic.png"), "ADMIN");
```

展开：

```
DynamicImageProxy.wrap(target, "ADMIN")
 └─ Proxy.newProxyInstance(
        target.getClass().getClassLoader(),   // 加载 VirtualImageProxy 的那个 ClassLoader（AppClassLoader）
        new Class<?>[]{Image.class},          // 代理类要实现的接口
        new ImageInvocationHandler(target, "ADMIN"))
     ├─ 检查缓存：这个 ClassLoader + 这组接口 是否已生成过代理类？
     │   └─ 首次调用：没有 → 进入生成流程
     ├─ ProxyBuilder.generateProxyClass()
     │   ├─ 创建动态模块 jdk.proxy1（JDK 9+ 的模块化处理）
     │   ├─ 用字节码拼装出 $Proxy0 类：
     │   │     - extends java.lang.reflect.Proxy
     │   │     - implements Image
     │   │     - 为 Image 的每个方法生成一个转发方法体
     │   │     - 为 Object 的 hashCode/equals/toString 也生成转发（所以这三个也会进 invoke！）
     │   └─ defineClass 加载进 JVM
     ├─ 缓存这个 Class，下次同接口直接用
     └─ 反射调用 $Proxy0 的构造器，传入 handler → 返回实例
```

**`proxy.getClass().getName()` 打印出 `jdk.proxy1.$Proxy0`** ——
这个类**在你的源码目录里根本不存在**，`out` 目录里也找不到 `$Proxy0.class`
（除非加 `-Djdk.proxy.ProxyGenerator.saveGeneratedFiles=true`）。
它是 JVM 在运行时**凭空拼出来的字节码**。

**`proxy.getFilename()` 的调用栈**：

```
Client.demo5DynamicProxy()
 └─ $Proxy0.getFilename()                        [JVM 生成的方法]
     └─ h.invoke(this, m_getFilename, null)      [h = ImageInvocationHandler]
         ├─ methodName = "getFilename"
         ├─ args == null → 打印 "[]"             ← 本次重构修的细节，原来打印 "null"
         ├─ println("→ 调用 getFilename...")
         ├─ PROTECTED_METHODS.contains("getFilename")  → false，跳过鉴权
         ├─ start = System.nanoTime()
         ├─ method.invoke(target, null)
         │   └─ VirtualImageProxy.getFilename()  → "dynamic.png"（不触发加载）
         ├─ finally: println("← 返回，耗时 585 μs")
         └─ return "dynamic.png"
     └─ (String) 强制转型                         [$Proxy0 里生成的代码]
```

**为什么 `getFilename()` 耗时 585 μs 而 `display()` 耗时 1879 μs？**
`display()` 里包含了 `new RealImage()`（模拟磁盘 IO）+ 两次 `println`。
585 μs 几乎全是**反射调用的固定开销 + 首次调用的 JIT 预热 + 控制台 IO**。

**这个数字很有教学意义**：动态代理**不便宜**。
585 μs 里绝大部分是反射开销，而真实的 `getFilename()` 只需要几纳秒。
在每秒调用百万次的热点路径上，这个开销是致命的。生产环境的高性能场景会用：

- **方法句柄（MethodHandle）** 替代 `Method.invoke()`（JDK 7+，JIT 可以更好地内联）
- **生成字节码时直接内联调用**（ByteBuddy 可以做到，比 JDK 动态代理快很多）
- **LambdaMetafactory** 把反射调用编译成 lambda（Spring 6 的部分优化）

**`ANONYMOUS` 被拦截，`真实加载次数 = 1`**：
那个 1 是前面 ADMIN 加载 `dynamic.png` 留下的。
`secret.png` 的 `VirtualImageProxy` 从未被触发，`RealImage` 从未被创建。
**同一份 `ImageInvocationHandler` 代码，只因为构造时传的 `role` 不同，行为就完全不同**——
这就是"配置驱动"而非"代码驱动"，也是动态代理相对静态代理的核心优势。

**一个 `finally` 的细节**：

```java
try {
    return method.invoke(target, args);
} catch (InvocationTargetException e) {
    throw e.getCause();
} finally {
    println("← 返回，耗时 ...");    // ← 即使抛异常也会执行
}
```

把耗时统计放在 `finally` 里，保证**异常路径也能记录耗时**。
这是监控类增强的标准写法——你最需要知道耗时的，恰恰是那些失败了的调用。

---

## 7. 扩展对比：新增一种代理要改哪些代码

### 场景 A：新增"限流代理"——每秒最多允许 10 次 display

**要新增的文件（1 个）**：

```java
public class RateLimitImageProxy implements Image {
    private final Image target;
    private final int maxPerSecond;
    private long windowStart = System.currentTimeMillis();
    private int countInWindow = 0;

    public RateLimitImageProxy(Image target, int maxPerSecond) {
        this.target = target;
        this.maxPerSecond = maxPerSecond;
    }

    @Override
    public void display() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) { windowStart = now; countInWindow = 0; }
        if (++countInWindow > maxPerSecond) {
            System.out.println("      [RateLimit] 触发限流，本次请求被拒绝");
            return;
        }
        target.display();
    }

    @Override
    public String getFilename() { return target.getFilename(); }
}
```

**要修改的文件：0 个。**

| 文件 | 是否需要修改 | 原因 |
|-----|------------|-----|
| `Image.java` | ✗ 不改 | 新代理实现同一接口 |
| `RealImage.java` | ✗ 不改 | 真实对象对代理毫不知情 |
| `VirtualImageProxy.java` | ✗ 不改 | 各代理互相独立 |
| `ProtectedImageProxy.java` | ✗ 不改 | 同上 |
| `CachingImageProxy.java` | ✗ 不改 | 同上 |
| `ImageInvocationHandler.java` | ✗ 不改 | 动态代理与静态代理互不干扰 |
| `Client.java` | 可选：加一段演示 | 只是使用方，不是模式的一部分 |

**使用**：

```java
Image image = new RateLimitImageProxy(
        new ProtectedImageProxy(
                new CachingImageProxy(
                        new VirtualImageProxy("a.png")), "ADMIN"),
        10);
```

**这就是开闭原则（OCP）的完整体现：对扩展开放（新增一个类），对修改关闭（0 个已有类被改动）。**

### 场景 B：给 `Image` 接口新增一个方法 `resize(int width)`

**这是代理模式的痛点，必须诚实说明。**

| 文件 | 是否需要修改 | 改动内容 |
|-----|------------|---------|
| `Image.java` | ✔ 改 | 加 `void resize(int width);` |
| `RealImage.java` | ✔ 改 | 实现 `resize()` |
| `VirtualImageProxy.java` | ✔ 改 | 加 `resize()` 转发（且要处理延迟加载） |
| `ProtectedImageProxy.java` | ✔ 改 | 加 `resize()` 转发 + 决定要不要鉴权 |
| `CachingImageProxy.java` | ✔ 改 | 加 `resize()` 转发 + 决定缓存是否失效 |
| `RateLimitImageProxy.java`（场景 A 新增的） | ✔ 改 | 加 `resize()` 转发 |

**静态代理：接口有 N 个方法、M 个代理类，就要写 N × M 个转发方法。
接口一改，M 个类全部要跟着改。** 这就是第 4 节第 6 步说的"静态代理的天花板"。

**动态代理：0 个改动。**

`ImageInvocationHandler.invoke()` 是按 `method.getName()` 分发的，
它**不关心接口有几个方法**。新增 `resize()` 后，
调用 `proxy.resize(100)` 会自动进入 `invoke()`，日志、计时、鉴权全部自动生效。

| 增强方式 | 新增 1 个接口方法的成本 | 新增 1 种增强的成本 |
|---------|---------------------|-------------------|
| 静态代理 | 改 M 个代理类，每个加 1 个方法 | 新增 1 个类，写 N 个转发方法 |
| 动态代理 | **0** | 在 `invoke()` 里加几行判断 |

**但动态代理也有代价**（第 8 节详述）：反射开销、类型不安全
（`method.getName()` 是字符串，方法改名后编译不报错、运行时静默失效）、
调试困难（栈里多一层 `$Proxy0`）。

**工程上的选择**：
- 接口稳定、增强种类少、性能敏感 → 静态代理
- 接口多变、增强种类多、要应用到大量类 → 动态代理
- 目标类没有接口 → CGLIB / ByteBuddy

### 场景 C：把静态代理全部换成动态代理

```java
// 原来
Image image = new ProtectedImageProxy(new CachingImageProxy(new VirtualImageProxy("a.png")), "ADMIN");

// 换成动态代理（一份 handler 同时做鉴权 + 缓存 + 日志）
Image image = DynamicImageProxy.wrap(new VirtualImageProxy("a.png"), "ADMIN");
```

需要在 `ImageInvocationHandler.invoke()` 里加入缓存逻辑：

```java
private final Map<String, String> cache = new HashMap<>();

public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if ("display".equals(method.getName())) {
        String key = ((Image) target).getFilename();
        if (cache.containsKey(key)) { return null; }   // 命中直接返回
        Object r = method.invoke(target, args);
        cache.put(key, "...");
        return r;
    }
    ...
}
```

**看得出问题吗？** `invoke()` 开始变成一个巨大的 `if-else` 方法名分发器。
方法名字符串硬编码、返回类型要强转、逻辑全挤在一个方法里。

**Spring 的解法**：不是一个 `invoke()` 干所有事，而是维护一个**拦截器链**：

```java
public class JdkDynamicAopProxy implements InvocationHandler {
    private final AdvisedSupport advised;   // 内部持有 Advisor 列表

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<Object> chain = advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
        if (chain.isEmpty()) {
            return method.invoke(target, args);            // 没有增强，直接反射调用
        }
        return new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain)
                   .proceed();                              // 递归推进拦截器链
    }
}
```

`proceed()` 递归调用链上的下一个拦截器——**本质上又回到了"嵌套代理"的模型**，
只是用列表 + 索引代替了对象嵌套，方便动态增删。

**这个演进路径值得记住**：
静态代理（对象嵌套）→ JDK 动态代理（单个 invoke）→ 拦截器链（列表 + proceed）→ Spring AOP。
每一步都是为了解决上一步的具体痛点，不是为了炫技。

---

## 8. 优缺点与常见误区

### 优点

| 优点 | 具体体现 | 本项目的证据 |
|-----|---------|------------|
| **解耦"访问控制"与"业务逻辑"** | `RealImage` 只管渲染，不知道鉴权/缓存的存在 | `RealImage` 全文没有一个字提到 role 或 cache |
| **降低资源消耗** | 不需要的对象根本不创建 | demo1：3 张图只加载 1 张；demo2：鉴权失败 `loadCount=0` |
| **符合开闭原则** | 新增代理类型不改任何已有代码 | 第 7 节场景 A：新增文件 1 个，修改文件 0 个 |
| **可自由组合** | 代理链按需叠加 | demo4：鉴权 → 缓存 → 延迟加载三层嵌套 |
| **客户端无感知** | 声明类型是接口，换代理不改调用代码 | 所有 demo 里客户端都写 `Image image = ...` |
| **横切逻辑可复用** | 一个动态代理 handler 服务所有接口 | demo5：`target` 声明为 `Object` |

### 缺点

| 缺点 | 说明 | 缓解方式 |
|-----|-----|---------|
| **类的数量增加** | 每种代理一个类，静态代理容易类爆炸 | 用动态代理 |
| **接口一改，所有静态代理都要改** | 第 7 节场景 B：N 方法 × M 代理 | 用动态代理，或抽一个 `AbstractImageProxy` 提供默认转发 |
| **调用链变长，调试困难** | 栈里出现 `$Proxy0`、`CglibProxy`，看不出真实调用路径 | IDE 配置过滤代理帧；打日志时带上 `target.getClass()` |
| **动态代理有反射开销** | 实测 `getFilename()` 585 μs（含预热） | 热点路径用 ByteBuddy / MethodHandle / 静态代理 |
| **增加理解成本** | 新人看到 `Image image = ...` 不知道拿到的是第几层代理 | 命名规范（`XxxProxy`）、工厂方法集中创建、文档说明 |
| **多层缓存的一致性** | demo3 里 `invalidate()` 只清一层 | 设计时明确"谁是缓存的唯一真相源" |
| **可能的内存泄漏** | `CachingImageProxy` 用 `HashMap` 无淘汰 | 生产用 Caffeine/Redis 带 TTL 和 maxSize |
| **线程安全需自行保证** | `VirtualImageProxy` 并发首次访问可能创建两个真实对象 | volatile + 双重检查锁，或 `Suppliers.memoize` |

### 常见误区

**误区 1：「代理模式就是延迟加载」**

延迟加载（虚拟代理）只是四种代理之一。保护代理、缓存代理、远程代理、智能引用代理
都完全可以在**对象已创建**的前提下工作。
本项目 demo2 的核心结论 `loadCount = 0` 恰恰说明：保护代理的价值不在"延迟"，而在"**根本不发生**"。

**误区 2：「代理和装饰器是一回事，只是名字不同」**

结构相似，意图相反。判据见第 3 节末尾的对比表。
**最快的判别法**：看那个 `target.xxx()` 调用**是否可能被跳过**。
- 可能被跳过（`if (不允许) return;`）→ 代理
- 一定会执行，且在返回值上加工 → 装饰器

**误区 3：「代理和适配器是一回事」**

| | 代理 | 适配器 |
|--|-----|-------|
| **接口** | 与目标**相同**接口 | 转换成**不同**接口 |
| **目的** | 控制访问 | 让不兼容的双方能协作 |
| **客户端期望** | 以为在用目标本身 | 明确知道这是一个转换层 |
| **本项目** | `VirtualImageProxy implements Image` | `MediaAdapter implements MediaPlayer`（包装 `AdvancedMediaPlayer`） |

**一句话**：代理**保持**接口不变，适配器**改变**接口。

**误区 4：「JDK 动态代理可以代理任何类」**

不行，**只能代理接口**。原因见第 4 节第 7 步（`extends Proxy` 用掉了继承名额）。
没有接口的类必须用 CGLIB/ByteBuddy。

**误区 5：「Spring 里同类内部方法调用，AOP 也会生效」**

**这是最高频的生产 bug。**

```java
@Service
public class OrderService {
    @Transactional
    public void createOrder() { ... }

    public void batchCreate(List<Order> orders) {
        for (Order o : orders) {
            createOrder();     // ← @Transactional 失效！
        }
    }
}
```

原因：`this.createOrder()` 是**对象内部直接调用**，绕过了代理。
外部调用 `orderService.batchCreate()` 时进入的是代理对象，
但代理对象把 `batchCreate` 转发给 target 之后，
target 内部的 `createOrder()` 走的是 `this`（真实对象），**不再经过代理**。

三种解法：
1. 拆成两个 Bean（推荐，符合单一职责）
2. `((OrderService) AopContext.currentProxy()).createOrder()`（需 `@EnableAspectJAutoProxy(exposeProxy = true)`）
3. 注入自己（`@Autowired private OrderService self;` 然后 `self.createOrder()`）

**这个 bug 的根源恰恰证明了代理模式的机制**：所有增强都在代理对象上，
真实对象本身是"干净"的。**绕过代理 = 绕过所有增强。**

**误区 6：「`invoke()` 里可以用 `proxy` 参数调用目标方法」**

```java
return method.invoke(proxy, args);   // ✗ StackOverflowError
```

`proxy` 就是 `$Proxy0` 自己，调它的方法又回到 `invoke()`，无限递归。必须用 `target`。

**误区 7：「鉴权失败时 handler 直接 `return null` 就行」**

如果被拦截的方法返回 `boolean`/`int` 等基本类型，调用方拆箱会 NPE。
必须用 `defaultValue(method.getReturnType())`。见第 5.6 节细节 ③。

**误区 8：「动态代理生成的类名是 `$Proxy0`，可以靠字符串判断」**

JDK 9+ 实际是 `jdk.proxy1.$Proxy0`（本项目 JDK 25 实测）。
不同 ClassLoader、不同接口组合会生成 `$Proxy0`、`$Proxy1`、`jdk.proxy2.$Proxy0`……
正确做法：`Proxy.isProxyClass(obj.getClass())`。

**误区 9：「代理只是给对象加功能，所以代理越多越好」**

每一层代理都会增加一次方法调用、一个对象头（16 字节）、一次多态分发。
demo4 的三层链跑一次 `display()` 要经过 4 次方法调用。
**代理链应该有明确的上限**（Spring 里超过一定层数会有性能告警）。
如果发现自己套了 5 层以上，说明横切关注点太多，应该考虑用拦截器链（列表）而不是对象嵌套。

### 与相似模式的完整对比

| 维度 | 代理 Proxy | 装饰器 Decorator | 适配器 Adapter | 外观 Facade |
|-----|-----------|-----------------|--------------|-----------|
| **接口变化** | 不变 | 不变 | **改变** | **新建简化接口** |
| **包装对象数量** | 1 个 | 1 个（可层层嵌套） | 1 个 | **多个子系统** |
| **意图** | 控制访问 | 增强功能 | 兼容转换 | 简化调用 |
| **是否可能不调用目标** | **可能** | 不可能 | 不可能 | 不可能（会编排多个） |
| **目标由谁创建** | 常由代理自己创建 | 外部传入 | 外部传入 | 外观内部持有 |
| **客户端知情度** | 以为在用目标 | 知道在包装 | 知道在转换 | 知道在用简化入口 |
| **本项目示例** | `proxy` 包 | `decorator` 包 | `adapter` 包 | `facade` 包 |

**代理 vs 外观**：
- 代理包装**一个**对象，接口**不变**（`Image` → `Image`）。
- 外观包装**一组**子系统，接口**全新且更简单**
  （4 个子系统的十几次调用 → `register(id, name, email)` 一次调用）。
- 本项目 `UserServiceFacade` 编排 `UserRepository` + `UserValidator` + `AuditLogger` + `EmailNotifier`，
  它**不实现**任何子系统的接口——这就足以区分它不是代理。

**代理 vs 享元**（都涉及"不创建那么多对象"）：
- 代理：**每个代理对应一个目标**，是 1:1 的替身关系。3 个 `VirtualImageProxy` 对应 3 张不同的图。
- 享元：**多个使用点共享同一个对象**，是 N:1 的复用关系。105 个字符视图共享 26 个 `CharacterGlyph`。
- 代理关心"**什么时候/要不要**访问目标"，享元关心"**能不能少创建**目标"。

**代理 vs 单例**：
- 单例保证"全局只有一个实例"，是**创建型**模式。
- 虚拟代理保证"每个代理最多创建一个真实对象"，是**局部**的惰性单例。
- `VirtualImageProxy` 内部的 `realImage` 字段，本质上就是一个"实例级单例"。

---

## 9. 面试与自测

### 30 秒版（面试官问"讲讲代理模式"）

> 代理模式是给目标对象加一个"替身"，替身和真身实现同一个接口，客户端拿到的是替身。
> 替身可以在调用真身之前、之后、甚至**替代**真身做任何事。
>
> 它解决的核心问题是：**把"访问控制"这类横切逻辑从业务代码里剥离出来。**
> 常见有四种——虚拟代理做延迟加载、保护代理做鉴权、缓存代理避免重复计算、远程代理跨进程调用。
>
> 结构上就是「同接口 + 持有一个同类型引用 + 委托」，和装饰器很像，
> 区别在**意图**：代理是**控制**能不能访问（可能不调用真身），装饰器是**增强**功能（一定调用并加工结果）。
>
> Java 里最重要的应用是 Spring AOP，底层是 JDK 动态代理或 CGLIB。

### 2 分钟版（要求深入讲）

在 30 秒版基础上补充：

> **为什么必须有接口？** 因为客户端要能无感切换真身和替身，这靠的是多态。
> 我做过一个图片查看器的例子：`Image` 接口有 `display()` 和 `getFilename()`，
> `RealImage` 在构造器里读磁盘，`VirtualImageProxy` 构造时只存文件名字符串、
> 内部持有一个初始为 null 的 `RealImage` 引用，`display()` 时才 `if (realImage == null) new RealImage(...)`。
> 实测创建 3 个代理加载次数是 0，用户点开 1 个之后才变成 1，**节省 2 次昂贵 IO**。
>
> **一个容易忽略的设计细节**：我特意给接口加了 `getFilename()`。
> 因为缓存代理需要 key、保护代理拒绝时要打印文件名，
> 如果接口只有 `display()`，代理就拿不到文件名，只能强转 `RealImage`——那样代理链就断了。
> 加了之后，`VirtualImageProxy.getFilename()` 直接返回自己存的字符串，**不触发加载**。
> 效果是：鉴权失败时 `loadCount` 实测是 **0**，图片根本没进内存。
> 如果把鉴权写在 `RealImage.display()` 里，因为加载在构造器，那时图片早已加载，鉴权就是形同虚设。
>
> **代理链**：我还做了三层嵌套——`保护(缓存(虚拟(真实)))`。
> 调用一次 `display()` 会穿透四层，第二次调用在第 2 层缓存命中就 return，
> GUEST 角色在第 1 层就被截断。**顺序有意义**：鉴权必须最外层，
> 这和 Spring 里 `@PreAuthorize` 在 `@Cacheable` 外层是同一个道理。
>
> **静态代理的天花板**：3 个静态代理类，每个方法都要手写转发。
> 接口有 20 个方法、100 个类要加日志，就是 2000 个几乎一样的转发方法。
> 所以有了 JDK 动态代理：`Proxy.newProxyInstance` 在运行时生成 `$Proxy0`，
> 所有方法路由到一个 `invoke(proxy, method, args)`，一份 handler 通吃。
>
> **JDK 动态代理为什么只能代理接口？** 因为生成的类是
> `class $Proxy0 extends java.lang.reflect.Proxy implements Image`，
> `extends Proxy` 用掉了 Java 单继承的唯一名额，没法再 `extends RealImage`。
> 要代理没有接口的类只能用 CGLIB 生成子类，所以目标类和方法都不能是 final。
> Spring 的策略是有接口可用 JDK、没接口用 CGLIB，Boot 2.x 之后默认全用 CGLIB。
>
> **动态代理有三个坑我踩过**：
> ① `invoke()` 里必须 `method.invoke(target, args)`，写成 `proxy` 会无限递归 StackOverflow；
> ② 反射抛出的异常包在 `InvocationTargetException` 里，必须 `throw e.getCause()` 解包，
> 否则调用方的 `catch (BusinessException)` 全部失效；
> ③ 鉴权拦截返回 null 时，如果方法返回类型是 `boolean`，调用方拆箱会 NPE，
> 要按 `method.getReturnType()` 返回 `false`/`0`/`'\0'` 这类默认值。
>
> **最后一个高频考点**：Spring 里同类内部方法自调用，AOP 会失效。
> 因为 `this.method()` 绕过了代理对象。这恰恰反证了代理的机制——
> 所有增强都在代理上，真实对象是干净的，**绕过代理就是绕过所有增强**。

### 高频追问

**Q1：JDK 动态代理和 CGLIB 的区别？各自什么时候用？**

| | JDK 动态代理 | CGLIB |
|--|------------|-------|
| 原理 | 生成 `implements 接口` 的类 | 生成 `extends 目标类` 的子类 |
| 要求 | 目标必须实现接口 | 目标类和方法不能是 `final` |
| 生成方式 | JDK 内部拼字节码 | 用 ASM 生成字节码 |
| 性能（现代 JDK） | 生成快，调用略慢（反射） | 生成慢，调用快（FastClass 索引直接调） |
| 依赖 | JDK 内置 | 需要 cglib/byte-buddy 库（Spring 已内置） |
| Spring 选择 | 目标有接口时（Boot 2.x 前默认） | 目标无接口时；Boot 2.x+ 全局默认 |

补充：CGLIB 生成子类会**调用父类构造器两次**（一次是代理对象自己，一次是目标对象），
Spring 用 Objenesis 绕过构造器来规避这个问题。
另外 JDK 8+ 之后 JDK 动态代理的调用性能已经大幅优化，二者差距不像早年那么大。

**Q2：`Proxy.newProxyInstance` 的三个参数分别是什么？为什么第一个是 ClassLoader？**

```java
Proxy.newProxyInstance(ClassLoader loader, Class<?>[] interfaces, InvocationHandler h)
```

- `loader`：用哪个类加载器**定义**生成的 `$Proxy0` 类。必须能同时"看见"所有目标接口。
  通常用 `target.getClass().getClassLoader()`。
  **为什么需要它**：Java 里一个类由「ClassLoader + 全限定名」共同唯一确定。
  生成的代理类要放进某个 ClassLoader 的命名空间，否则会与其他 ClassLoader 里的同名类冲突。
  在 OSGi、Tomcat 多 webapp 这种多 ClassLoader 环境里，传错 ClassLoader 会导致
  `ClassCastException`（同名类但不同 ClassLoader 视为不同类型）。
- `interfaces`：代理类要实现的接口数组。可以一次实现多个接口。
- `h`：所有方法调用的路由目标。

**Q3：动态代理生成的类会代理 `Object` 的方法吗？**

会代理 `hashCode()`、`equals()`、`toString()` 这三个（它们也会进 `invoke()`），
但**不会**代理 `getClass()`、`wait()`、`notify()`、`clone()` 等 `final` 方法（`final` 方法无法被覆盖）。

所以 `invoke()` 里如果无脑打印所有方法名，日志里会突然冒出 `hashCode`、`equals`，需要过滤。
Spring 的 `JdkDynamicAopProxy` 就专门处理了这三个方法
（`equals` 会比较是不是同一个代理，`hashCode` 返回 identity hash，`toString` 返回代理描述）。

**Q4：怎么把生成的 `$Proxy0` 类 dump 出来看？**

```powershell
java "-Djdk.proxy.ProxyGenerator.saveGeneratedFiles=true" -cp out com.patterns.structural.proxy.Client
```

会在**工作目录**下生成 `jdk/proxy1/$Proxy0.class`，用 JD-GUI / IDEA / `javap -c` 反编译即可。

（JDK 8 及以前的参数名是 `-Dsun.misc.ProxyGenerator.saveGeneratedFiles=true`，JDK 9+ 改成了 `jdk.proxy.`。）

CGLIB 的对应参数是 `-Dcglib.debugLocation=<路径>`，
ByteBuddy 用 `.with(TypeWriter.DebugDumpLocation.of(path))`。

**Q5：代理模式在 JDK/Spring 源码里有哪些真实应用？**

| 位置 | 用途 | 代理类型 |
|-----|-----|---------|
| `java.lang.reflect.Proxy` | JDK 动态代理基础设施 | 动态 |
| `java.rmi.*` | RMI Stub/Skeleton | 远程 |
| Spring `JdkDynamicAopProxy` | AOP 核心 | 动态 |
| Spring `ObjenesisCglibAopProxy` | AOP（无接口时） | 动态（CGLIB） |
| Spring `LazyInitializationTargetSource` | `@Lazy` 注入 | 虚拟 |
| Hibernate `HibernateProxy` | 实体懒加载 | 虚拟 |
| MyBatis `MapperProxy` | Mapper 接口 → SQL 执行 | 动态 |
| Mockito | 生成 mock 对象 | 动态（ByteBuddy） |
| Dubbo `InvokerInvocationHandler` | RPC 客户端 stub | 远程 |

**MyBatis 的 `MapperProxy` 是最好的学习样本**：它只有 100 行左右代码，
`invoke()` 里判断是 Object 方法 / 默认方法 / 接口方法，
然后把接口方法转成 `MapperMethod` 执行 SQL。**建议直接读它的源码。**

**Q6：代理模式怎么做到"客户端无感知"的？如果客户端 `instanceof RealImage` 会怎样？**

无感知靠的是**面向接口编程 + 多态分发**。客户端声明 `Image image`，
编译期只依赖接口，运行期由 JVM 的方法表决定调用哪个实现。

如果客户端写 `if (image instanceof RealImage)`，**无感知就被破坏了**——
代理对象永远不是 `RealImage` 的实例（它们是兄弟关系，都实现 `Image`，而不是父子关系）。
这类代码会导致"加了代理之后业务突然失效"的诡异 bug。

**这也是里氏替换原则的检验标准**：如果你的代码需要 `instanceof` 判断具体类型，
说明你的抽象设计有漏洞。

**Q7：本项目哪里体现了"不为使用模式而过度设计"？**

三处：

1. **没有抽 `AbstractImageProxy` 基类**。三个静态代理各只有 20~40 行，
   共同的只有 `private final Image target` 和一行 `getFilename()` 转发。
   为这点重复引入一层继承，会让读者先理解基类才能理解子类，得不偿失。
   **重复 3 次的两行代码，不值得抽象。**
2. **`CachingImageProxy` 用 `HashMap` 而不是 Caffeine**。
   引入第三方依赖会偏离"原生 Java"的目标，也会让焦点从代理模式转到缓存库 API 上。
   但文档里明确写了它的内存泄漏风险和生产的替代方案。
3. **没有实现远程代理**。RMI 需要注册中心、需要启动两个进程，Demo 无法单文件运行。
   用动态代理演示"横切增强"覆盖了同样的教学点。

### 自测题

**1. 为什么 `VirtualImageProxy` 里的 `realImage` 字段不能声明为 `final`？**

<details>
<summary>点击查看答案</summary>

因为 `final` 字段必须在构造器执行完毕前赋值。
而虚拟代理的核心就是"构造时**不**创建真实对象，等第一次用到时才创建"。
如果声明 `final`，就必须写成 `private final RealImage realImage = new RealImage(filename);`
（或在构造器里赋值），这等于立即加载，延迟加载彻底失效。

**这个非 final 字段就是虚拟代理的物理本质**：它让"持有引用"和"对象就绪"这两件事可以分离。

顺带一个知识点：如果要求线程安全的延迟加载，可以用 `volatile` + 双重检查锁，
或 `Supplier` + memoize，但**都不能用 `final`**
（除了 Holder 静态内部类那种"类加载即初始化"的变通写法）。
</details>

**2. 如果把 `Image` 接口的 `getFilename()` 删掉，`CachingImageProxy` 有哪几种改法？各有什么问题？**

<details>
<summary>点击查看答案</summary>

**改法 A：构造时额外传 filename**

```java
public CachingImageProxy(Image target, String filename)
```

问题：调用方要写两遍同一个字符串
`new CachingImageProxy(new VirtualImageProxy("a.png"), "a.png")`，
改一处忘一处 → 缓存永远不命中，且这种 bug 极难排查（不报错，只是慢）。

**改法 B：强转 target**

```java
String key = ((RealImage) target).filename();
```

问题：**代理链彻底断裂**。`new CachingImageProxy(new VirtualImageProxy("a.png"))`
会抛 `ClassCastException`。而"缓存 + 延迟加载"恰恰是最常一起用的组合。

**改法 C：缓存代理内部维护一个固定 key**

```java
private final String key = "single";   // 只缓存一个结果
```

问题：只能缓存"最后一次"，多个图片共用一个缓存代理时数据会串。
（如果确定一个缓存代理只服务一个图片，这倒是最简单的方案，但通用性为零。）

**结论**：三条路都不如给接口加一个方法。
这体现了"**抽象主题的接口应该包含廉价的元数据方法**"这一设计准则——
代理需要在**不触发昂贵操作**的前提下了解目标。
</details>

**3. `ProtectedImageProxy` 包在 `VirtualImageProxy` 外面，和包在里面，有什么区别？**

<details>
<summary>点击查看答案</summary>

**外面（本项目的做法）**：`new ProtectedImageProxy(new VirtualImageProxy("a.png"), "GUEST")`

- 调用 `display()` → 先鉴权 → 失败直接 return
- **实测 `loadCount = 0`**：图片根本没被加载，内存和 IO 都省了
- 鉴权是真正的"门卫"，拦在门外

**里面**：`new VirtualImageProxy(new ProtectedImageProxy(realImage, "GUEST"))`

- 这个写法其实**编译不过**——`VirtualImageProxy` 的构造参数是 `String filename`，
  它自己 `new RealImage`，不接受外部传入的目标。
- 要让它成立，得改成 `VirtualImageProxy(Image target)`，
  那么"延迟创建"就变成了"延迟包装"，语义已经变了。
- 假设真这么写了：调用 `display()` → 虚拟代理创建/持有保护代理 → 保护代理鉴权失败 return。
  但 `RealImage` 是谁创建的？如果是虚拟代理在鉴权**之后**才创建，效果和外面一样；
  如果鉴权代理内部已经持有了 `RealImage`，那图片早就加载了。

**结论**：**代理的嵌套顺序决定了增强的执行顺序，也决定了资源消耗的时机。**
鉴权这类"应该尽早失败"的增强必须放在最外层。
这和 Spring AOP 里用 `@Order` 控制拦截器顺序是同一件事。
</details>

**4. `invoke()` 里写 `method.invoke(proxy, args)` 会发生什么？为什么？`proxy` 参数还有什么用？**

<details>
<summary>点击查看答案</summary>

**`StackOverflowError`（无限递归）。**

调用链：

```
proxy.display()
 └─ $Proxy0.display()  →  h.invoke(proxy, m_display, null)
     └─ method.invoke(proxy, args)
         └─ 又调用 proxy.display()
             └─ $Proxy0.display()  →  h.invoke(...)
                 └─ ... 无限循环直到栈溢出
```

`proxy` 参数就是 JVM 生成的那个 `$Proxy0` 实例本身。
它的所有方法体都是"转发给 handler"，所以调它等于调 handler，调 handler 又调它。

**正确写法是 `method.invoke(target, args)`**，`target` 是 handler 构造时传入的真实对象。

**那 `proxy` 参数存在的意义是什么？** 用于需要"把代理自身传下去"的场景：

- 拦截器链里要把 proxy 传给下一个拦截器（Spring 的 `MethodInvocation.getProxy()`）
- `AopContext.currentProxy()` 的实现依赖它
- 某些需要在目标方法内部再次走代理的场景（解决自调用失效问题的一种方案）

**99% 的情况下你不需要用 `proxy` 参数。**
</details>

**5. demo3 里 `invalidate()` 之后重新 display，为什么 `loadCount` 没有增加？这暴露了什么问题？**

<details>
<summary>点击查看答案</summary>

因为 `CachingImageProxy.invalidate()` 只清空了**自己那一层的快照缓存**，
而它包装的 `VirtualImageProxy` 里的 `realImage` 字段**依然指向那个已创建的 `RealImage` 对象**。

所以调用链是：

```
CachingImageProxy.display()
 ├─ cache 为空，未命中，missCount++
 └─ target.display()
     └─ VirtualImageProxy.display()
         ├─ realImage != null      ← 关键！对象还活着
         ├─ println("真实对象已存在，直接复用")
         └─ realImage.display()    ← 不再走 loadFromDisk()
```

实测输出正好印证：

```
[CacheProxy] 缓存已清空（原 1 条）
[CacheProxy] ◇ 缓存未命中，转发给真实对象
[VirtualProxy] 真实对象已存在，直接复用     ← 这一行
[RealImage] 显示图片：hero.png              ← 没有 ★ 加载 那一行
失效后重新加载，真实加载次数 = 1
```

**这揭示了一个真实的工程问题：多层代理的状态是各自独立的，一层的失效不会传播到其他层。**

如果需求是"文件被改了，要重新从磁盘读"，就必须让 `VirtualImageProxy` 也提供失效能力：

```java
public void reload() { this.realImage = null; }
```

然后缓存代理的 `invalidate()` 里判断 `if (target instanceof VirtualImageProxy v) v.reload();`。

**但这么做会引入层间耦合**（缓存代理知道了虚拟代理的具体类型），
更好的做法是抽一个 `Refreshable` 接口，让需要刷新的代理都实现它：

```java
public interface Refreshable { void refresh(); }

// invalidate() 里
if (target instanceof Refreshable r) r.refresh();
```

这样缓存代理只依赖 `Refreshable` 抽象，不依赖 `VirtualImageProxy` 具体类——依然是依赖倒置。

本项目**有意保留了这个"不完美"**，因为它真实反映了缓存分层的复杂性，
比"一键刷新所有层"的理想化演示更有价值。
</details>

**6. 为什么 `RealImage` 用 `final class` 而不是 `record`？（原项目用的是 record）**

<details>
<summary>点击查看答案</summary>

三个理由：

**① 语义错位**：`record` 的设计契约是"**透明的不可变数据载体**"。
看到 `record` 的人会默认「new 一个很便宜、只是几个字段赋值」。
但 `RealImage` 的构造器要做磁盘 IO（模拟读几十 MB）。
把重副作用藏在一个看起来"很轻"的语法结构里，是**对读者的误导**。
`record` 的紧凑构造器应该只做**参数校验和归一化**，不该做 IO。

**② `toString()` / `equals()` 会失控**：真实的图片类会有 `byte[] data` 字段。
`record` 自动生成的 `toString()` 会把它打印出来（`[B@1b6d3586` 还算好，
如果为了可读性用了 `Arrays.toString(data)`，就是几万行乱码刷屏）。
自动生成的 `equals()`/`hashCode()` 也会遍历整个数组，
两个内容相同的图片比较退化成 O(n) 的全量对比。

**③ `record` 隐式 `final`，无法被 CGLIB 代理**：
CGLIB 的原理是生成目标类的**子类**。`final` 类不能被继承，
所以 Spring 无法为 `record` 类型创建 CGLIB 代理。
虽然本项目不用 CGLIB，但这是"为什么真实主题不该是 record"的现实理由。

**改成 `final class` 之后**：

- 依然不可变（字段全 `final`，没有 setter）
- 但加载逻辑写在一个**有名字的私有方法** `loadFromDisk()` 里，语义清晰、可计数、可改成异步
- 显式的 `final` 表达了"这是终点，扩展方式是代理而不是继承"
- `toString()` 自己控制，只打印 filename

**这是一个很好的"语法糖不是免费的"的例子**：
`record` 让代码变短了，但它附带了一整套语义承诺（透明、廉价、纯数据），
一旦你的类不符合这些承诺，用 `record` 就是在给未来埋坑。
</details>

**7. 如果要给这个项目加一个"远程代理"，需要哪些改动？和虚拟代理的关键区别是什么？**

<details>
<summary>点击查看答案</summary>

**需要的改动**：

1. 新增 `RemoteImageProxy implements Image`
2. 构造参数是**远程地址**（`String host, int port, String objectId`）而不是文件名
3. `display()` 内部：建立连接 → 序列化请求 → 发送 → 阻塞等待响应 → 反序列化结果
4. 需要处理：连接超时、网络异常、重试、序列化失败
5. **`Image` 接口的所有参数和返回值必须 `Serializable`**

**关键区别**：

| | 虚拟代理 | 远程代理 |
|--|---------|---------|
| **真实对象在哪** | 同一个 JVM 的堆里 | 另一个进程/机器 |
| **`target` 字段类型** | `RealImage`（真实类型） | 一个 **Stub/网络客户端**（本地根本没有真实对象） |
| **调用开销** | 纳秒级（普通方法调用） | 毫秒级（网络往返） |
| **失败模式** | 只可能 OOM / IO 异常 | 超时、断连、丢包、部分成功 |
| **必须额外处理** | 线程安全的延迟创建 | 序列化、异常传播、幂等性 |

**最本质的区别**：虚拟代理**最终会在本地创建真实对象**；
远程代理**永远不会**——它持有的是一个"通信句柄"，真实对象在别处，本地只有一个替身。

**实现方式**：
- 原生：Java RMI（`Remote` 接口 + `UnicastRemoteObject.exportObject` + `Naming.lookup`）
- 现代：gRPC（protobuf 定义接口，自动生成 stub）、Dubbo（`InvokerInvocationHandler`）

**为什么本项目没实现远程代理？**
需要启动两个进程（服务端 + 客户端）、需要占用端口、需要处理网络异常，
Demo 无法用一条 `java -cp out Client` 命令直接运行。
这违背了"提供能够编译运行的完整代码"和"通俗易懂"的目标。
但动态代理那一节已经覆盖了远程代理的核心机制——
**"客户端调一个本地接口，实际动作发生在别处"**，只是"别处"从"另一个进程"换成了"另一个对象"。
</details>

**8. 综合题：给你一个 `ReportService`，需求是「有权限的用户调用时，结果缓存 5 分钟，每次调用记日志，
超过 100ms 打告警」。请用本项目的思路给出设计方案，并说明为什么这样分层。**

<details>
<summary>点击查看答案</summary>

**方案一：静态代理链（4 层嵌套）**

```java
ReportService service =
    new LoggingReportProxy(                    // 第 1 层：日志（记录完整端到端耗时）
        new ProtectedReportProxy(              // 第 2 层：鉴权（尽早失败）
            new CachingReportProxy(            // 第 3 层：缓存（5 分钟 TTL）
                new ReportServiceImpl(),       // 第 4 层：真实对象
                Duration.ofMinutes(5)),
            currentUser),
        warnThresholdMillis = 100);            // 告警逻辑放在日志层
```

**分层理由**：

- **日志在最外层**：它要记录的是"用户视角的完整耗时"，包含鉴权和缓存查询的时间。
  如果放在最内层，缓存命中时日志根本不会打，你就无法统计缓存命中率。
- **鉴权在缓存之前**：这是**安全要求**，不只是性能要求。
  如果缓存在外层，无权限用户可能通过"缓存命中/未命中的响应时间差异"推断出
  「这个报表存在且被别人查过」——这叫**时序侧信道攻击**。
- **缓存在最内层（紧贴真实对象）**：它要拦截的是"真正昂贵的那次调用"。
- **告警合并进日志层**：两者都需要耗时数据，分成两层会导致 `System.nanoTime()` 调两次、
  两个层都要维护阈值配置。合并是合理的（它们属于同一个关注点：可观测性）。

**方案二：动态代理（1 个 handler，4 段逻辑）**

```java
public class ReportHandler implements InvocationHandler {
    private final ReportService target;
    private final String role;
    private final Map<String, CachedEntry> cache = new HashMap<>();

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log("→", method, args);                          // ① 日志
        if (isSensitive(method) && !allowed(role)) {     // ② 鉴权
            log("×", "权限不足");
            return defaultValue(method.getReturnType());
        }
        String key = cacheKey(method, args);
        CachedEntry hit = cache.get(key);                // ③ 缓存
        if (hit != null && !hit.expired()) { log("◆ 命中"); return hit.value(); }

        long start = System.nanoTime();
        try {
            Object r = method.invoke(target, args);
            cache.put(key, new CachedEntry(r, Instant.now().plus(Duration.ofMinutes(5))));
            return r;
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            if (costMs > 100) log("! 慢调用告警：" + costMs + "ms");   // ④ 告警
            log("←", costMs);
        }
    }
}
```

**两种方案的取舍**：

| | 静态代理链 | 动态代理 |
|--|----------|---------|
| 代码量 | 4 个类，每个 30 行 | 1 个类，80 行 |
| 可读性 | 每层职责一目了然 | 全挤在 `invoke()` 里 |
| 接口新增方法 | 4 个类都要改 | **0 改动** |
| 顺序调整 | 改一行嵌套代码 | 挪动 `invoke()` 里的代码块 |
| 单层单测 | 容易（每层独立） | 难（要构造反射上下文） |
| 运行时开销 | 4 次普通方法调用 | 1 次反射调用 |

**我的推荐**：
- 如果 `ReportService` 接口**稳定**、只有这一处需要增强 → **静态代理链**（可读性和可测性更重要）
- 如果这个增强要**应用到几十上百个 Service** → **动态代理**，
  并且进一步演进成 Spring 那样的**拦截器链**（4 个独立的 `Interceptor` + 一个 `proceed()` 推进器），
  兼得"单一职责"和"零转发代码"。

**这个演进路径本身就是答案**：
`静态代理（对象嵌套）→ 动态代理（单 invoke）→ 拦截器链（列表 + proceed）→ Spring AOP`。
每一步都是为了解决上一步的具体痛点。
</details>

---

## 附：本包文件清单

| 文件 | 角色 | 行数 | 本次重构的改动 |
|-----|-----|-----|--------------|
| `Image.java` | 抽象主题 | ~30 | **新增 `getFilename()`** |
| `RealImage.java` | 真实主题 | ~60 | record → `final class`，新增 `loadCount` 计数器 |
| `VirtualImageProxy.java` | 虚拟代理 | ~70 | 原 `ProxyImage` 重命名，新增 `isLoaded()` |
| `ProtectedImageProxy.java` | 保护代理 | ~68 | **全新**（原项目没有） |
| `CachingImageProxy.java` | 缓存代理 | ~95 | **全新**（原项目没有） |
| `ImageInvocationHandler.java` | 动态代理处理器 | ~120 | **全新**（原项目没有） |
| `DynamicImageProxy.java` | 动态代理工厂 | ~65 | **全新**（原项目没有） |
| `Client.java` | 客户端 | ~198 | 从 1 段演示扩展到 6 段 |

**编译与运行验证**：JDK 25（`F:\enCodingApps\JDK\jdk-25`），
`javac --release 25 -encoding UTF-8` 编译通过（0 error / 0 warning），
`java -Dfile.encoding=UTF-8` 运行通过，6 段演示全部输出正常。
本文档第 6 节的所有输出均为**真实运行结果**，非手写模拟。
