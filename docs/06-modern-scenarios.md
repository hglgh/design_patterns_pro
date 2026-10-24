# 现代场景：这 23 个模式今天活在哪里

> **一句话**：模式不是 1994 年的课本内容，它每天都在你 `import` 的包里运行。
> 你每天都在用它们，只是不知道那叫「责任链」。

本文回答三个问题：

1. 这个模式**在 JDK 里**对应哪个类？（可立即点开源码看）
2. 这个模式**在主流框架里**是哪个扩展点？（你写过的那行 `@Override`）
3. **现代语言特性**（record / sealed / 虚拟线程）出现后，这个模式还需要手写吗？

**全部落点都可以在本地 JDK 与开源代码里核对**，没有一个是「据说」。

---

## 如何使用本文

三种读法，按你的目的选：

| 你的目的 | 怎么读 | 耗时 |
|---|---|---|
| 想验证「模式真的有用」 | 只读 §1，每个模式点开对应的 JDK 类看一眼 | 40 分钟 |
| 想在工作中认出来 | 读 §2 与 §3，对照你正在用的框架 | 30 分钟 |
| 想判断「还要不要手写」 | 读 §4 与 §5 | 20 分钟 |

**不要一次读完。** 这篇文档的价值是「索引」，不是「教程」——真正的教程是每个模式目录下的 `README.md`。

### 本文的可靠性说明

| 内容 | 可靠性 | 说明 |
|---|---|---|
| JDK 类名、方法名 | **已在 JDK 25 上核对** | 全部来自 `java.base` 等模块的真实 API |
| 框架扩展点 | **公开文档 + 常见用法** | 版本差异较大，文中标注了适用的版本范围 |
| 语言特性 | **只写 JDK 21 已正式的特性** | record(16) / sealed(17) / switch 模式匹配(21) / 虚拟线程(21) |
| 「现代替代」的判断 | **观点** | 明确标注，不是事实 |

> **关于 JDK 25**：本项目用 `--release 25` 编译，但本文**不讨论** JDK 22-25 的新特性（作用域值、结构化并发等尚未全部正式）。理由是：**我没在真实项目里用过它们，不写没验证的东西。**

---

## 0. 先给结论：三个层次的落点

```
┌──────────────────────────────────────────────────────────┐
│ 第三层：云原生与分布式                                      │
│   Service Mesh / API Gateway / Saga / 事件驱动             │
│   → 模式是「架构组件」的骨架，你买的是别人的实现              │
├──────────────────────────────────────────────────────────┤
│ 第二层：框架的扩展点                                        │
│   Spring 的 BeanPostProcessor、Netty 的 ChannelHandler、    │
│   MyBatis 的 Interceptor、JUnit 的 Extension               │
│   → 模式是「你要实现的那个接口」                             │
├──────────────────────────────────────────────────────────┤
│ 第一层：JDK                                              │
│   Comparator、InputStream 家族、Iterator、Proxy、Flow       │
│   → 模式是「你每天都在调用的 API」                            │
└──────────────────────────────────────────────────────────┘
```

**越往下，模式越「隐形」；越往上，模式越「显性」。**

- 在第一层，你**不需要知道** `BufferedInputStream` 是装饰器，也能用得很好；
- 在第二层，你**必须知道** `ChannelHandler` 是责任链，否则写不出正确的 `pipeline.addLast()` 顺序；
- 在第三层，你**必须知道**熔断是状态 + 装饰器，否则调不好 Resilience4j 的参数。

**本文的核心判断**：

> 模式的价值不在于「你会不会手写」，而在于**「你能不能在别人的实现里认出它」**。
> 认出来了，你就知道该改哪个参数、该实现哪个方法、该避开哪个坑。

---

## 1. JDK 里的 23 个模式

### 1.1 创建型（5 个）：对象怎么造

#### ① 工厂方法 —— `Calendar.getInstance()`

```java
Calendar cal = Calendar.getInstance();          // 返回 GregorianCalendar，但你不需要知道
Charset cs   = Charset.forName("UTF-8");        // 可能是 sun.nio.cs.UTF_8
List<String> empty = List.of();                 // JDK 9 的静态工厂
```

| 落点 | 类型 |
|---|---|
| `Calendar.getInstance()` | 静态工厂，按 Locale/TimeZone 返回不同子类 |
| `java.nio.charset.Charset.forName(String)` | 按名字返回具体字符集实现 |
| `List.of()` / `Set.of()` / `Map.of()` | 静态工厂，返回不可变实现 |
| `Executors.newFixedThreadPool(int)` | 隐藏 `ThreadPoolExecutor` 的 7 个构造参数 |

**关键差别**：JDK 的静态工厂**不暴露实现类**。`List.of()` 返回的 `ImmutableCollections.ListN` 是包私有的——你无法 `new` 它，也无法继承它。

> **这正是工厂方法最重要的收益：把「造什么」和「怎么造」分开，而且可以随时换。**

**本项目对照**：[`factorymethod`](../src/com/patterns/creational/factorymethod/README.md) 里的 `MessageFactory` 用的是**实例工厂**（可以注入、可以 mock），而 JDK 用的是**静态工厂**（无状态、无法替换）。两者的取舍在于「要不要在测试里替换它」。

#### ② 抽象工厂 —— `DocumentBuilderFactory`

```java
DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
DocumentBuilder builder = f.newDocumentBuilder();       // 产品 1
// 同一个工厂还能造出配套的 XPathFactory、TransformerFactory
```

| 落点 | 说明 |
|---|---|
| `javax.xml.parsers.DocumentBuilderFactory` | 造一族 XML 解析器（DOM / SAX / StAX） |
| `javax.xml.transform.TransformerFactory` | 造 XSLT 转换器 |
| `java.nio.channels.spi.SelectorProvider` | 造 Selector / Channel / Pipe 一族 |
| `java.util.spi.ToolProvider` | 造 javac / jar 等工具 |

**关键点**：抽象工厂的「族」约束在 JDK 里体现为**实现必须配套**——你不能用 DOM 的 `DocumentBuilder` 配 SAX 的 `XMLReader`。这和本项目 [`abstractfactory`](../src/com/patterns/creational/abstractfactory/README.md) 里「Android 的 Button 不能配 Web 的 TextBox」是同一个约束。

#### ③ 建造者 —— `HttpRequest.newBuilder()`

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://example.com/api/order"))
        .timeout(Duration.ofSeconds(3))
        .header("Authorization", "Bearer t-1001")
        .GET()
        .build();
```

| 落点 | 说明 |
|---|---|
| `java.net.http.HttpRequest.Builder`（JDK 11） | 现代 JDK 最标准的建造者 |
| `java.lang.StringBuilder` | 名字叫 Builder，但它是「拼接器」，不是 GoF 建造者 |
| `java.util.stream.Stream.Builder` | 逐步 `add` 再 `build` |
| `java.time.format.DateTimeFormatterBuilder` | 复杂格式化的分步构建 |
| `java.util.Locale.Builder` | 语言/国家/变体分步设置 |

**注意一个常见的误判**：`StringBuilder` **不是**建造者模式。它没有「产品」概念（`toString()` 返回的是同一个字符串值），也没有「分步构建不同表示」的意图。它是**可变字符缓冲区**。

> **判断标准：建造者的 `build()` 是否返回一个「与构建过程分离的、可能不可变的产品」？** `HttpRequest` 是，`StringBuilder.toString()` 不是。

**本项目对照**：[`builder`](../src/com/patterns/creational/builder/README.md) 里的 `ComputerBuilder` 有 `Director` 负责「装配顺序」，而 `HttpRequest.Builder` 没有 Director——**调用方自己就是 Director**。这是建造者的两种常见形态。

#### ④ 原型 —— `ArrayList.clone()`

```java
List<String> copy = new ArrayList<>(original);   // 拷贝构造器（现代首选）
Object cloned = original.clone();                // 原型模式（Cloneable）
```

| 落点 | 说明 |
|---|---|
| `Object.clone()` + `Cloneable` | 原型模式的原生支持 |
| `ArrayList.clone()` / `HashMap.clone()` | 集合的浅拷贝 |
| `Arrays.copyOf(T[], int)` | 数组拷贝 |
| `record` 的拷贝构造器（手写） | 现代替代方案 |

**一个必须知道的事实**：`Cloneable` 是**标记接口**，它不包含 `clone()` 方法（`clone()` 在 `Object` 上）。这是 JDK 里最著名的设计失误之一——**接口没有定义行为，却改变了 `Object.clone()` 的行为**。

> 这也解释了为什么《Effective Java》建议「**不要实现 Cloneable，用拷贝构造器**」。本项目的 [`prototype`](../src/com/patterns/creational/prototype/README.md) 两种都写了，并在文档里说明了取舍。

**现代替代**：`record` 天然适合做「不可变原型」——`with` 风格的拷贝只需要一行构造：

```java
record Order(String id, int amount) {
    Order withAmount(int newAmount) { return new Order(id, newAmount); }
}
```

#### ⑤ 单例 —— `Runtime.getRuntime()`

```java
Runtime rt = Runtime.getRuntime();        // 全进程唯一，JVM 保证
Runtime.version();                        // JDK 9+ 的静态方法
```

| 落点 | 说明 |
|---|---|
| `Runtime.getRuntime()` | 最标准的单例（饿汉 + 静态 final） |
| `System.console()` | 可能返回 `null`，不是严格单例 |
| `java.awt.Desktop.getDesktop()` | 懒加载单例，可能抛异常 |
| `enum` 类型的任意枚举 | **JVM 保证**每个枚举常量只创建一次 |

**JDK 自己怎么防反射破坏单例？**

`Runtime` 的写法是「饿汉 + 静态 final」，构造器 `private`：

```java
public class Runtime {
    private static final Runtime currentRuntime = new Runtime();

    public static Runtime getRuntime() {
        return currentRuntime;
    }

    private Runtime() {}        // 只有 private，没有「已存在就抛异常」的检查
}
```

**注意：这一层 `private` 本身拦不住反射。** 真正拦住的是 **JDK 9+ 的模块系统**：

```java
Constructor<Runtime> c = Runtime.class.getDeclaredConstructor();
c.setAccessible(true);      // ← 在 JDK 9+ 直接抛 InaccessibleObjectException
```

因为 `java.lang` 属于 `java.base` 模块，默认**不**对未命名模块开放反射（除非加 `--add-opens java.base/java.lang=ALL-UNNAMED`）。

> **所以「`Runtime` 是防反射的单例」这个说法是不准确的**：它靠的是**模块系统的封装**，不是单例写法本身。
> 一旦有人用 `--add-opens` 打开了包，它照样能被破坏。

**唯一能防住反射和反序列化的写法是枚举**：

```java
public enum ConfigHolder {
    INSTANCE;
    private final Config config = load();
}
```

JVM 在类加载时就保证枚举常量的唯一性。而且反射 `newInstance()` 枚举会直接抛 `IllegalArgumentException`——**这是 `Constructor.newInstance` 里硬编码的判断**，和模块开放与否无关，所以加 `--add-opens` 也绕不过去。

**这不是「据说」，是实测过的**（`_probe/modern/EnumVsRuntimeProbe.java`，JDK 25.0.4.1，退出码 0）：

```text
--- 断言 A：反射构造枚举 ---
  拿到构造器  private EnumVsRuntimeProbe$ConfigHolder(java.lang.String,int)
  setAccessible(true) 成功（枚举的构造器允许打开）
  构造被拒绝  java.lang.IllegalArgumentException: Cannot reflectively create enum objects

--- 断言 B：反射构造 Runtime ---
  拿到构造器  private java.lang.Runtime()
  setAccessible 被拒绝  java.lang.reflect.InaccessibleObjectException
  原因  Unable to make private java.lang.Runtime() accessible:
        module java.base does not "opens java.lang" to unnamed module @659e0bfd

--- 对照：自己写的类，private 拦不住反射 ---
  已造出第二个实例  PrivateHolder@7852e922（同一个类，两个对象）
```

> **三个档位的区别一目了然**：
>
> | 写法 | 反射能不能破 | 靠什么拦 |
> |---|---|---|
> | 普通类 + `private` 构造器 | **能** | 只靠命名约定 |
> | `Runtime`（JDK 内部类） | **不能**（除非 `--add-opens`） | **模块系统** |
> | `enum` | **永远不能** | `Constructor.newInstance` 的硬编码判断 |
>
> 输出里的 `PrivateHolder@7852e922` 每次运行都不同（对象哈希），其余行稳定。

**本项目对照**：[`singleton`](../src/com/patterns/creational/singleton/README.md) 给了五种写法（懒汉、饿汉、DCL、静态内部类、枚举），并实测了反射与反序列化破坏的效果。

---

### 1.2 结构型（7 个）：对象怎么组合

#### ⑥ 适配器 —— `InputStreamReader`

```java
Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
// 字节流（InputStream）→ 字符流（Reader），接口完全不同
```

| 落点 | 说明 | 适配什么到什么 |
|---|---|---|
| `InputStreamReader` | 字节 → 字符 | `InputStream` → `Reader` |
| `OutputStreamWriter` | 字符 → 字节 | `Writer` → `OutputStream` |
| `Arrays.asList(T[])` | 数组 → List | `T[]` → `List<T>` |
| `Collections.list(Enumeration<T>)` | 老接口 → 新接口 | `Enumeration` → `ArrayList` |
| `Executors.callable(Runnable)` | 无返回值 → 有返回值 | `Runnable` → `Callable<T>` |

**关键点**：适配器**改变接口，不改变行为**。`InputStreamReader` 不做缓冲、不做转换之外的任何事——加缓冲是 `BufferedReader` 的职责（那是装饰器）。

> **一句话区分：适配器让「不能用的」能用；装饰器让「能用的」更好用。**

**本项目对照**：[`adapter`](../src/com/patterns/structural/adapter/README.md) 里的 `MediaAdapter` 把 `AdvancedMediaPlayer` 适配成 `MediaPlayer`，和 `InputStreamReader` 是同一个思路。

#### ⑦ 桥接 —— `java.sql.Driver`

```java
// JDK 只定义抽象
public interface Driver {
    Connection connect(String url, Properties info);
}

// 具体实现由各数据库厂商提供（mysql-connector-j、ojdbc…）
```

| 落点 | 抽象 | 实现 |
|---|---|---|
| JDBC | `java.sql.Driver` / `Connection` | MySQL / PostgreSQL / Oracle 的驱动 |
| `java.nio.channels` | `Channel` / `Selector` | 各平台的 `sun.nio.ch.*` |
| AWT | `java.awt.Component` | `java.awt.peer.*`（平台原生控件） |
| SLF4J（非 JDK） | `org.slf4j.Logger` | Logback / Log4j2 绑定 |

**桥接的核心是「两个维度独立变化」**：JDBC 的抽象维度是「SQL 语义」，实现维度是「具体数据库协议」。加一个数据库不用改接口，改接口不用管数据库。

**本项目对照**：[`bridge`](../src/com/patterns/structural/bridge/README.md) 里 `Message`（消息类型）× `Sender`（发送渠道）就是两个维度。

#### ⑧ 组合 —— `java.awt.Container`

```java
Container panel = new JPanel();
panel.add(new JButton("OK"));      // Container 里可以放 Container，也可以放叶子组件
panel.add(new JPanel());
```

| 落点 | 说明 |
|---|---|
| `java.awt.Container` / `Component` | 容器与叶子同为 `Component`，可以无限嵌套 |
| `javax.swing.JComponent` 树 | Swing 组件树 |
| `java.io.File`（勉强） | 目录与文件同类型，`listFiles()` 天然递归 |
| `java.nio.file.Path` | 路径可以拼接，但不是组合模式 |

**判断标准**：**「整体」与「部分」是否实现了同一个接口？** AWT 的 `Container extends Component`，所以「把容器当组件用」是类型安全的——这是组合模式的标志。

**本项目对照**：[`composite`](../src/com/patterns/structural/composite/README.md) 有两份 Demo（文件系统、菜单树），都在强调「客户端不区分叶子与容器」。

#### ⑨ 装饰器 —— `BufferedInputStream`

```java
InputStream in = new BufferedInputStream(          // 加缓冲
                     new GZIPInputStream(           // 加解压
                         new FileInputStream("a.gz")));   // 基础流
```

| 落点 | 说明 |
|---|---|
| `java.io.FilterInputStream` 及其子类 | `BufferedInputStream` / `DataInputStream` / `GZIPInputStream` |
| `Collections.unmodifiableList()` | 加「只读」语义 |
| `Collections.synchronizedList()` | 加「同步」语义 |
| `java.io.FilterReader` / `FilterWriter` | 字符流的装饰器 |
| `HttpResponse.BodySubscribers`（JDK 11） | 对响应体做流式处理 |

**这是 JDK 里最标准的装饰器证据**：`FilterInputStream` 的构造器接收一个 `InputStream`，自己也是 `InputStream`——**类型相同、持有被装饰者、可以无限叠加**。三个条件缺一不可。

**本项目对照**：[`decorator`](../src/com/patterns/structural/decorator/README.md) 里的 `TextDecorator`（加粗、变色）与 `BufferedInputStream` 结构完全一致。

> **最值得记住的一点**：JDK 用装饰器解决的是「**排列组合爆炸**」。`Buffered + GZIP + File`、`GZIP + Buffered + File`、`Data + Buffered + File`……如果每个组合写一个类，需要 2^n 个类。这是本项目 [`apigateway`](../src/com/patterns/combined/apigateway/README.md) §3.3 用探针实测过的同一件事。

#### ⑩ 外观 —— `HttpClient`

```java
HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(3))
        .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

`HttpClient` 背后隐藏了：连接池、HTTP/2 多路复用、重定向、代理、cookie、超时、TLS 握手。**调用方只看到两个方法。**

| 落点 | 说明 |
|---|---|
| `java.net.http.HttpClient`（JDK 11） | 封装整个 HTTP 栈 |
| `java.sql.DriverManager` | 封装驱动加载 + 连接创建 |
| `javax.script.ScriptEngineManager` | 封装脚本引擎发现与实例化 |
| `java.util.concurrent.Executors` | 封装线程池的复杂构造（也可视为工厂） |

**外观与工厂的差别**：外观是为了「**让复杂的东西用起来简单**」，工厂是为了「**让创建过程可替换**」。`Executors` 同时具备两者特征，所以它在两个模式里都能出现——**一个类可以同时扮演多个角色，这不是设计错误。**

**本项目对照**：[`facade`](../src/com/patterns/structural/facade/README.md) 里的 `UserServiceFacade` 把 DAO + 校验 + 事务包成一个方法。

#### ⑪ 享元 —— `Integer.valueOf()` 的缓存

```java
Integer a = Integer.valueOf(127);
Integer b = Integer.valueOf(127);
System.out.println(a == b);        // true  ← 同一个对象（-128~127 缓存）

Integer c = Integer.valueOf(128);
Integer d = Integer.valueOf(128);
System.out.println(c == d);        // false ← 超出缓存范围，两个对象
```

| 落点 | 缓存范围 | 说明 |
|---|---|---|
| `Integer.valueOf(int)` | -128 ~ 127（可通过 `-XX:AutoBoxCacheMax` 扩大） | 最经典的享元 |
| `Long.valueOf` / `Short.valueOf` / `Byte.valueOf` | 同上 | 同样的缓存 |
| `Boolean.valueOf` | `TRUE` / `FALSE` | 只有两个 |
| `Character.valueOf` | 0 ~ 127 | ASCII 范围 |
| `String.intern()` | 运行时常量池 | 字符串享元，但代价是永久代/元空间压力 |

**这是「享元」最容易被感知的地方，也是最容易踩坑的地方**：

```java
Integer x = 127, y = 127;   x == y   // true
Integer x = 128, y = 128;   x == y   // false  ← 面试常考
```

> **享元的本质是「共享不可变的内在状态」。** `Integer` 是不可变的，所以共享安全；如果共享的是可变对象，就必须保证「所有使用者看到的是同一份状态」——这正是本项目 [`apigateway`](../src/com/patterns/combined/apigateway/README.md) 里 `RouteTable` 必须保证 `inFlight` 唯一的理由。

#### ⑫ 代理 —— `java.lang.reflect.Proxy`

```java
OrderService proxy = (OrderService) Proxy.newProxyInstance(
        OrderService.class.getClassLoader(),
        new Class<?>[]{ OrderService.class },
        (p, method, args) -> {
            long start = System.nanoTime();
            Object result = method.invoke(target, args);   // 前置 + 后置
            System.out.println(method.getName() + " 耗时 " + (System.nanoTime() - start));
            return result;
        });
```

| 落点 | 说明 |
|---|---|
| `java.lang.reflect.Proxy` | 动态代理，Spring AOP 的底层之一 |
| `java.rmi.server.UnicastRemoteObject` | RMI 的 stub（远程代理） |
| `javax.persistence` 的 `LazyLoading`（非 JDK） | 延迟加载代理 |
| `Method.invoke()` | 反射调用，代理的实现手段 |

**代理 vs 装饰器：结构一样，意图不同。**

| | 装饰器 | 代理 |
|---|---|---|
| 目的 | **加能力** | **控制访问** |
| 层数 | 可以叠 N 层 | 通常 1 层 |
| 被包装对象 | 由调用方传入 | 通常由代理自己创建/持有 |
| 典型 | `BufferedInputStream` | `Proxy.newProxyInstance` |

> **Spring 的 `@Transactional` 是代理，不是装饰器**：它不「加功能」，它「拦截方法调用、在外面开事务」。这个区别在排查「为什么同类内部调用不生效」时非常关键——**代理只能拦截「从外面进来」的调用**。

---

### 1.3 行为型（11 个）：对象怎么协作

#### ⑬ 责任链 —— `ClassLoader` 的双亲委派

```java
protected Class<?> loadClass(String name, boolean resolve) {
    synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            c = parent.loadClass(name, false);     // ← 先交给父加载器
            if (c == null) c = findClass(name);    // ← 父加载器处理不了才自己找
        }
        return c;
    }
}
```

| 落点 | 说明 |
|---|---|
| `ClassLoader.loadClass()` | 双亲委派：自己处理不了就交给 parent |
| `java.util.logging.Logger` 的 parent 链 | 日志级别沿链向上传递 |
| `java.security.Provider` 链 | 安全提供者的查找链 |
| `javax.servlet.FilterChain`（非 JDK） | Web 过滤器链 |

**注意双亲委派与标准责任链的差别**：标准责任链是「**沿链找到能处理的那个**」，双亲委派是「**先让父处理，父处理不了才自己来**」。两者的方向相反，但「一个请求沿多个处理者传递」的本质相同。

> **这也是「模式是骨架，不是模板」的例子**：同一个骨架可以有不同的传递方向，不需要为了「像模式」而改掉正确的逻辑。

**本项目对照**：[`chainofresponsibility`](../src/com/patterns/behavioral/chainofresponsibility/README.md) 的折扣链是「找到能处理的就停」，`apigateway` 的过滤器链是「有一票否决就短路」。

#### ⑭ 命令 —— `Runnable` + `ExecutorService`

```java
Runnable task = () -> System.out.println("do something");   // 命令对象
executor.submit(task);                                       // 排队、异步执行
```

| 落点 | 说明 |
|---|---|
| `java.lang.Runnable` / `Callable<T>` | 最小的命令接口 |
| `ExecutorService.submit()` | 命令的接收者与调度者 |
| `javax.swing.Action` | 命令 + 状态（enabled/disabled） |
| `java.lang.Thread` | 命令的另一种载体 |

**命令模式在 JDK 里被简化到了极致**：GoF 的 Command 有 `execute()` / `undo()` / `Receiver`，而 `Runnable` 只有 `run()`。**「可撤销」这一半在现代 Java 里几乎消失了**——因为撤销需要保存状态，而状态保存的成本比「重做一遍」高得多。

> **这是模式演进的一个真实案例：命令模式的两个意图（封装调用、支持撤销）在 JDK 里只剩下了前一个。**

**本项目对照**：[`command`](../src/com/patterns/behavioral/command/README.md) 里的 `Task` 保留了 `undo` 的演示，同时也说明了「真实系统里很少用」。

#### ⑮ 解释器 —— `Pattern`

```java
Pattern p = Pattern.compile("^(\\d{3})-(\\d{4})$");   // 解析成 AST（内部）
Matcher m = p.matcher("123-4567");
if (m.matches()) { ... }
```

| 落点 | 说明 |
|---|---|
| `java.util.regex.Pattern` | 正则表达式 → 内部 AST → 匹配 |
| `java.text.MessageFormat` | 模板字符串解析（`{0}`、`{1}`） |
| `java.time.format.DateTimeFormatter` | 格式模式解析（`yyyy-MM-dd`） |
| `javax.script.ScriptEngine`（JDK 15 后无内置引擎） | 脚本解释 |

**为什么正则表达式是解释器模式？** 因为它把「一种小语言」的句子（pattern）解析成抽象语法树，然后对树求值（匹配）。你写的 `(\\d{3})-(\\d{4})` 就是那个小语言的「句子」。

> **解释器模式的判断标准：是否存在一种「用户可写的小语言」？** 有 → 解释器；只是「固定规则的判断」→ 策略或责任链。

**本项目对照**：[`interpreter`](../src/com/patterns/behavioral/interpreter/README.md) 做了一个规则引擎（`age > 18 AND city = "北京"`），AST 打印是它最有价值的输出。

#### ⑯ 迭代器 —— `Iterator` 与 `Stream` 的分野

```java
// 外部迭代：你自己控制循环
Iterator<String> it = list.iterator();
while (it.hasNext()) { String s = it.next(); ... }

// 内部迭代：把「怎么遍历」交给库
list.stream().filter(s -> s.length() > 3).forEach(System.out::println);
```

| 落点 | 说明 |
|---|---|
| `java.util.Iterator` | 外部迭代，`hasNext` / `next` / `remove` |
| `java.util.stream.Stream` | 内部迭代，`map` / `filter` / `reduce` |
| `java.nio.file.DirectoryStream` | 目录遍历 |
| `java.sql.ResultSet` | 结果集游标（本质是迭代器） |

**一个重要判断**：**Stream 不是迭代器模式**。它没有 `hasNext()`，遍历控制权在库手里。它是「内部迭代 + 函数式管道」，属于**另一个模式家族**（可以理解为「管道/责任链在数据流上的变体」）。

> 但 `Stream` 的**惰性求值**确实借鉴了迭代器的思想：`filter` 不立即执行，直到遇到终止操作。这一点在排查「为什么我的 `peek` 没打印」时很重要。

**本项目对照**：[`iterator`](../src/com/patterns/behavioral/iterator/README.md) 做的是工作流节点的遍历，重点在「不暴露内部结构」。

#### ⑰ 中介者 —— `ExecutorService`

```java
// 提交者不认识执行者，只认识「线程池」
executor.submit(task);

// 线程池负责：排队、分配线程、拒绝策略、关闭
```

| 落点 | 说明 |
|---|---|
| `java.util.concurrent.ExecutorService` | 提交者与执行线程之间的中介 |
| `java.util.Timer` / `ScheduledExecutorService` | 任务与时间之间的中介 |
| `java.awt.EventQueue` | 事件源与事件处理者之间的中介 |
| `java.util.concurrent.ForkJoinPool` | 任务与工作线程之间的中介 |

**JDK 里没有教科书式的中介者**（一个 `ChatRoom` 认识所有 `User`，负责转发）。JDK 的中介者都是**单向协调**：提交者 → 中介 → 执行者，执行者不认识提交者。

> **这是中介者模式的一个变体：不是「互相通信的协调」，而是「解耦提交与执行」。** 严格说它更接近「生产者-消费者」模式，但它确实承担了中介者的核心职责——**让两个本来要互相认识的对象，只认识中间那个**。

**本项目对照**：[`mediator`](../src/com/patterns/behavioral/mediator/README.md) 的聊天室是标准中介者：用户之间不直接发消息，都经过 `ChatRoomMediator`。

#### ⑱ 备忘录 —— 序列化与 `UndoManager`

```java
// 快照 = 把对象状态写到字节流
ByteArrayOutputStream bos = new ByteArrayOutputStream();
try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
    out.writeObject(document);
}
byte[] memento = bos.toByteArray();      // 这就是备忘录
```

| 落点 | 说明 |
|---|---|
| `ObjectOutputStream` / `ObjectInputStream` | 全量快照 |
| `java.util.Date.clone()` | 单字段快照（Date 可变，必须 clone） |
| `javax.swing.undo.UndoManager` | Swing 的撤销栈（标准备忘录） |
| 数据库事务的 `savepoint` | 另一种「可回滚的快照」 |

**备忘录在现代 Java 里的地位下降了**，原因是：

1. **不可变对象**越来越多（`record`、`List.of()`），「快照」就是「持有旧引用」，不需要专门的备忘录类；
2. **事件溯源（Event Sourcing）** 把「存快照」换成了「存变更」，等价但更省空间。

> **这是「模式被语言特性吸收」的典型案例**：`record` 让「备忘录」退化成了一个普通的字段赋值。

**本项目对照**：[`memento`](../src/com/patterns/behavioral/memento/README.md) 的 `History` 保存 `DocumentMemento` 栈，是标准实现。

#### ⑲ 观察者 —— `java.util.concurrent.Flow`

```java
// JDK 9 的响应式流：发布者 / 订阅者 / 订阅 / 处理器
public interface Publisher<T> { void subscribe(Subscriber<? super T> s); }
public interface Subscriber<T> {
    void onSubscribe(Subscription s);
    void onNext(T item);
    void onError(Throwable t);
    void onComplete();
}
```

| 落点 | 说明 | 状态 |
|---|---|---|
| `java.util.concurrent.Flow`（JDK 9） | 响应式流标准，带**背压** | **推荐** |
| `java.beans.PropertyChangeListener` | JavaBeans 的属性变更通知 | 仍在用 |
| `java.util.Observer` / `Observable` | 最古老的观察者 | **JDK 9 起废弃** |
| `javax.swing.event.ChangeListener` | Swing 事件 | 仍在用 |

**为什么 `Observer` / `Observable` 被废弃？** 官方给出的理由：

1. `Observable` 是一个**类**，不是接口 —— 你的类继承了它就不能继承别的；
2. 它**没有提供任何线程安全保证**；
3. `setChanged()` / `notifyObservers()` 的语义容易误用。

**`Flow` 补齐了观察者模式最缺的东西：背压（backpressure）。** 订阅者通过 `Subscription.request(n)` 告诉发布者「我只能处理 n 个」——这解决了「发布者太快，订阅者被淹没」的问题。

> **这是观察者模式在现代最重要的演进**：从「通知」升级为「可控速率的通知」。本项目 `apigateway` 的 `AlertListener` 没有背压，所以 §8.3 把「订阅者无异常隔离」列为已知瑕疵。

**本项目对照**：[`observer`](../src/com/patterns/behavioral/observer/README.md) 是同步通知；`apigateway` 的观察者增加了「同一批事件、两种聚合」的对比。

#### ⑳ 状态 —— `Thread.State`

```java
Thread t = new Thread(task);
Thread.State state = t.getState();     // NEW / RUNNABLE / BLOCKED / WAITING / TIMED_WAITING / TERMINATED
```

| 落点 | 说明 |
|---|---|
| `java.lang.Thread.State` | 枚举了 6 个状态，`Thread.getState()` 返回当前状态 |
| `java.util.concurrent.Future` 的 `isDone()` / `isCancelled()` | 隐式状态判断 |
| `java.util.concurrent.locks.AbstractQueuedSynchronizer` 的 `state` | 用 int 编码状态 |
| `javax.swing.ButtonModel` | 按钮的 pressed/armed/rollover 状态 |

**JDK 里状态模式的两种形态**：

| 形态 | 例子 | 特点 |
|---|---|---|
| **状态枚举 + 分支** | `Thread.State` + `switch` | 简单，但每加一个状态要改所有 `switch` |
| **状态对象 + 多态** | `AQS` 的子类实现 | 复杂，但加状态只加一个类 |

> **本项目的 `CircuitBreaker` 选了第二种**（`sealed interface` + 三个状态类），因为熔断的三个状态**各自有完全不同的行为**（`tryPass` 的返回值不同）。`Thread.State` 选第一种，因为它的行为差异很小（主要是展示）。

**本项目对照**：[`state`](../src/com/patterns/behavioral/state/README.md) 用订单状态演示；`apigateway` 的 `CircuitBreaker` 是「带自动恢复」的状态机。

#### ㉑ 策略 —— `Comparator` 与拒绝策略

```java
list.sort(Comparator.comparing(Order::amount).reversed());       // 策略 1
list.sort(Comparator.naturalOrder());                            // 策略 2

// 线程池的拒绝策略：同样是策略模式
new ThreadPoolExecutor(1, 1, 0, SECONDS, queue, new AbortPolicy());
new ThreadPoolExecutor(1, 1, 0, SECONDS, queue, new CallerRunsPolicy());
```

| 落点 | 说明 |
|---|---|
| `java.util.Comparator` | 最常用的策略接口 |
| `ThreadPoolExecutor` 的四种 `RejectedExecutionHandler` | `AbortPolicy` / `CallerRunsPolicy` / `DiscardPolicy` / `DiscardOldestPolicy` |
| `java.util.concurrent.ThreadFactory` | 线程的创建策略 |
| `java.time.temporal.TemporalAdjuster` | 日期调整策略 |
| `java.util.stream.Collector` | 归约策略 |

**`Comparator` 是策略模式最纯粹的例子**：接口只有一个方法（`compare`），实现可以有无限多个，调用方（`sort`）完全不知道用的是哪个。

> **注意 `Comparator` 是函数式接口**，所以现代写法用 lambda 而不是匿名类——**这恰恰说明策略模式的「一个方法 + 可替换」已经被 lambda 吸收了**。§4.5 会详细说这一点。

**本项目对照**：[`strategy`](../src/com/patterns/behavioral/strategy/README.md) 的折扣策略；`apigateway` 的 `LoadBalancer` 是「带上下文参数的策略」。

#### ㉒ 模板方法 —— `AbstractList`

```java
public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {
    public boolean add(E e) {
        add(size(), e);       // ← 调用抽象方法
        return true;
    }
    public abstract void add(int index, E element);   // ← 子类实现
}
```

| 落点 | 说明 |
|---|---|
| `java.util.AbstractList` / `AbstractMap` / `AbstractSet` | 「骨架实现」，子类只需实现少数方法 |
| `java.io.InputStream.read(byte[], int, int)` | 默认实现调用 `read()` |
| `java.util.concurrent.AbstractQueuedSynchronizer` | `tryAcquire` / `tryRelease` 由子类实现 |
| `java.io.OutputStream` | 同上 |

**模板方法是「继承」派最重要的模式**，也是**现代 Java 最被冷落的模式**——因为它要求继承，而现代设计更偏好组合。

> **一个必须知道的对比**：`AbstractList` 用模板方法（继承），`Collections.unmodifiableList` 用装饰器（组合）。前者「子类填空」，后者「包装替换」。**能组合就别继承**，这是现代 Java 的共识。

**本项目对照**：[`templatemethod`](../src/com/patterns/behavioral/templatemethod/README.md) 用订单处理流程演示（校验 → 支付 → 发货 → 通知），固定步骤 + 可变步骤。

#### ㉓ 访问者 —— `FileVisitor`

```java
Files.walkFileTree(Paths.get("/data"), new SimpleFileVisitor<Path>() {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        System.out.println("文件：" + file);
        return FileVisitResult.CONTINUE;
    }
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        System.out.println("进入目录：" + dir);
        return FileVisitResult.CONTINUE;
    }
});
```

| 落点 | 说明 |
|---|---|
| `java.nio.file.FileVisitor` / `SimpleFileVisitor` | 文件树遍历，**访问者决定「遇到什么类型做什么」** |
| `javax.lang.model.element.ElementVisitor` | 注解处理器遍历 AST 元素 |
| `com.sun.source.tree.TreeVisitor` | 编译器内部遍历语法树 |
| `java.util.function` 的 `Consumer` | 简化版访问者（只访问，不返回值） |

**访问者模式的核心是「双分派」**：`element.accept(visitor)` 转调 `visitor.visitXxx(element)`。第一次分派决定「元素类型」，第二次决定「访问者类型」。

> **这是 23 个模式里唯一必须靠「双分派」才能实现的结构**，也是它最容易被误用的原因——大多数「加个操作」的需求，用策略或责任链就够了。

**本项目对照**：[`visitor`](../src/com/patterns/behavioral/visitor/README.md) 有两个 Demo（文档导出、价格计算），都强调「新增操作不改元素类」。

**现代替代**：JDK 21 的 `sealed` + `switch` 模式匹配可以替代一部分访问者：

```java
sealed interface Shape permits Circle, Square { }
record Circle(double r) implements Shape { }
record Square(double a) implements Shape { }

double area(Shape s) {
    return switch (s) {
        case Circle c -> Math.PI * c.r() * c.r();
        case Square q -> q.a() * q.a();
    };   // 编译器保证穷尽，加一个 Shape 实现会编译报错
}
```

**这与访问者的差别**：`switch` 版本把「操作」写在了外部函数里，加一个**形状**要改所有函数（和访问者相反），加一个**操作**只需加一个函数（访问者要加一个 Visitor 实现）。**选择哪一个，取决于「形状多还是操作多」。** 详见 §4.2。

---

## 2. 框架里的模式：你写的那个 `@Override` 就是模式

### 2.0 一个判断，先把整章说完

你在 Spring / Netty / MyBatis 里写的每一个「扩展点实现」，都不是随便设计的。它们的形状全部收敛到两条：

> 凡是「**框架定流程 + 你填空**」的设计 → 背后一定是**模板方法**。
> 凡是「**多个实现 + 按顺序执行 + 你可以插进去**」的设计 → 背后一定是**责任链**。

再加一条更隐蔽的：

> 凡是「**你以为你在直接调对象，其实中间隔了一层**」的设计 → 背后是**动态代理**。

**为什么框架必须用模式？** 因为框架和业务的关系是「**控制反转**」：框架负责调用你，而不是你调用框架。要能调用你，它就必须在代码里留下一个「**稳定的空洞**」。这个空洞的名字，就是上面三个。

下面这张表是整章的索引。**每一个都能在本地查到源码，没有一个是「据说」**：

| 框架 | 扩展点 | 模式 | 你写的那个方法 |
|---|---|---|---|
| Spring | `BeanPostProcessor` | 责任链（可替换对象） | `postProcessBeforeInitialization` / `postProcessAfterInitialization` |
| Spring | `InstantiationAwareBeanPostProcessor` | 责任链（创建阶段） | `postProcessProperties` |
| Spring | `ApplicationListener` | 观察者 | `onApplicationEvent` |
| Spring | `HandlerInterceptor` | 责任链（数组式） | `preHandle` / `postHandle` / `afterCompletion` |
| Spring | `@Transactional` / `@Async` / `@Cacheable` | 代理 | 无（你只写注解） |
| Spring | `AbstractApplicationContext.refresh()` | 模板方法 | `onRefresh` / `postProcessBeanFactory` |
| Spring | `JdbcTemplate.execute()` | 模板方法 + 回调 | `doInStatement` |
| Spring | `Converter` / `HandlerMethodArgumentResolver` | 策略 | `convert` / `resolveArgument` |
| Spring | `FactoryBean` | 工厂方法 | `getObject` |
| Spring Boot | `@Conditional` / `AutoConfiguration` | 策略 + 链 | `matches` |
| Netty | `ChannelPipeline` + `ChannelHandler` | 责任链（**双向**） | `channelRead` / `write` |
| Netty | `ByteToMessageDecoder` | 模板方法 | `decode` |
| Netty | `ByteBufAllocator` | 抽象工厂 + 享元 | 无（你只调 `alloc()`） |
| MyBatis | `Interceptor` + `Plugin` | 代理 + 责任链（**两层**） | `intercept` |
| MyBatis | `BaseExecutor` | 模板方法 | `doQuery` / `doUpdate` |
| MyBatis | `TypeHandler` | 策略 | `setParameter` / `getResult` |
| SLF4J | `Logger` / `SLF4JServiceProvider` | 外观 + 桥接 | 无（你只依赖接口） |
| Jackson | `JsonSerializer` / `JsonDeserializer` | 策略 | `serialize` / `deserialize` |
| JUnit 5 | `Extension` 子接口 | 责任链 + 策略 | `beforeEach` / `handleTestExecutionException` |
| Kafka | `ProducerInterceptor` | 责任链 | `onSend` |
| Reactor | `Flux` 操作符链 | 装饰器 + 观察者 + 背压 | 无（你只写 lambda） |

**读法建议**：这张表不用背。**下次你在 IDE 里 `@Override` 一个框架方法时，回来查一次**，三次之后你就形成条件反射了。

---

### 2.1 Spring：一个容器，装下了 10 个模式

Spring 的复杂度被吐槽了二十年，但它的**扩展机制本身极简**：整个容器只提供一个生命周期，然后在生命周期上开了十几个「回调点」。**每一个回调点就是一个模式。**

#### 2.1.1 `BeanPostProcessor` —— 责任链，而且能「换掉对象」

```java
public interface BeanPostProcessor {
    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }
    default Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }
}
```

**注意签名：参数是 `Object`，返回值也是 `Object`。** 这一个小细节决定了它的能力上限：

> **它可以返回一个完全不同的对象，替换掉原来的 bean。**

框架侧的调用（`AbstractAutowireCapableBeanFactory`，简化）：

```java
public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName) {
    Object result = existingBean;
    for (BeanPostProcessor processor : getBeanPostProcessors()) {   // ← 排好序的集合
        Object current = processor.postProcessAfterInitialization(result, beanName);
        if (current == null) {
            return result;          // ← 返回 null 表示「到此为止，不再往下传」
        }
        result = current;           // ← 把上一个的输出当作下一个的输入
    }
    return result;
}
```

**这三行就是责任链的全部**：

| 代码 | 在责任链里的角色 |
|---|---|
| `for (processor : getBeanPostProcessors())` | 沿链前进 |
| `processor.postProcess...(result, ...)` | 交给当前处理者 |
| `result = current` | 处理者的输出成为下一环的输入（**这就是「可替换」**） |
| `if (current == null) return result` | 短路 |

**关键差别：本项目的链是「传上下文」，Spring 的链是「传对象本身」。**

在 [`apigateway`](../src/com/patterns/combined/apigateway/README.md) 里，`GatewayFilter` 拿到的永远是同一个 `CallContext`，过滤器只能改它的字段，不能把它换成别的类型。Spring 的 `BeanPostProcessor` 拿到的就是 bean 本身，并且**可以把 `OrderService` 换成一个 `OrderService` 的代理**——类型没变，实现变了。

> **这就是「链上传递什么」的设计分歧**：传上下文（稳定、易调试）vs 传对象（强大、难调试）。选哪个取决于你需不需要「替换身份」。

**这一个接口撑起了 Spring 的一半功能**：

| 你不是手写的功能 | 其实是哪个 `BeanPostProcessor` 干的 |
|---|---|
| `@Autowired` / `@Value` 注入 | `AutowiredAnnotationBeanPostProcessor`（`postProcessProperties`） |
| `@PostConstruct` / `@PreDestroy` | `CommonAnnotationBeanPostProcessor`（`postProcessBeforeInitialization`） |
| `@Transactional` / `@Async` / `@Cacheable` | `AnnotationAwareAspectJAutoProxyCreator`（`postProcessAfterInitialization` 里造代理） |
| `@Valid` 参数校验 | `MethodValidationPostProcessor` |
| `@ConfigurationProperties` 绑定 | `ConfigurationPropertiesBindingPostProcessor` |
| JMX 导出 | `MBeanExportAutoProxy` 系列 |

**完整生命周期（记住这一条的收益极大）**：

```text
① 实例化前   postProcessBeforeInstantiation   ← 可以「短路」，直接返回替代对象
② 构造器
③ 属性填充   postProcessProperties            ← @Autowired / @Value 在这里
④ 初始化前   postProcessBeforeInitialization  ← @PostConstruct 在这里
⑤ 初始化     InitializingBean.afterPropertiesSet() → init-method
⑥ 初始化后   postProcessAfterInitialization   ← AOP 代理在这里生成（@Transactional）
⑦ 使用
⑧ 销毁       @PreDestroy → DisposableBean.destroy() → destroy-method
```

**不知道这张表会踩什么坑？** 三个最常见的生产事故都出自这张表：

| 现象 | 根因（对着上表看） |
|---|---|
| `@Autowired` 的字段在构造器里是 `null` | 构造器在 ②，注入在 ③。**构造器里永远看不到注入结果** |
| `@Transactional` 方法自己调自己不生效 | 代理在 ⑥ 才生成。**`this.foo()` 绕过代理直接调原对象** |
| `@PostConstruct` 里用 `@Async` 方法没异步 | `@Async` 的代理在 ⑥，`@PostConstruct` 在 ④。**调用时还没有代理** |

> 这三条都可以用一句话概括：**「代理只能在 ⑥ 之后存在，任何在 ⑥ 之前的自我调用都是裸调用。」**
>
> 这与 [`proxy`](../src/com/patterns/structural/proxy/README.md) 文档里的「代理只能拦截从外面进来的调用」是同一件事。

#### 2.1.2 `refresh()` 与 `JdbcTemplate` —— 模板方法的两个极端

模板方法在 Spring 里有两个极端形态，一「大」一「小」。

**大的：整个容器的启动流程**

```java
// AbstractApplicationContext
public void refresh() throws BeansException, IllegalStateException {
    prepareRefresh();                                     // 固定
    ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();   // 固定
    prepareBeanFactory(beanFactory);                      // 固定
    try {
        postProcessBeanFactory(beanFactory);              // ← 可覆盖（很多框架在这里插一脚）
        invokeBeanFactoryPostProcessors(beanFactory);     // 固定
        registerBeanPostProcessors(beanFactory);          // 固定
        initMessageSource();                              // 固定
        initApplicationEventMulticaster();                // 固定
        onRefresh();                                      // ← 可覆盖（Spring Boot 用它启动 Tomcat）
        registerListeners();                              // 固定
        finishBeanFactoryInitialization(beanFactory);     // 固定（实例化所有单例）
        finishRefresh();                                  // 固定
    } catch (BeansException ex) {
        destroyBeans();
        cancelRefresh(ex);
        throw ex;
    }
}
```

**这 12 行的顺序不能变，任何一行挪位置都会导致启动失败。** 这就是模板方法的定义：「**顺序固定，个别步骤可变**」。

`onRefresh()` 在 `AbstractApplicationContext` 里是空方法：

```java
protected void onRefresh() throws BeansException {
    // For subclasses: do nothing by default.
}
```

而 `ServletWebServerApplicationContext` 覆盖了它：

```java
@Override
protected void onRefresh() {
    super.onRefresh();
    try {
        createWebServer();        // ← 整个 Tomcat 就是在这一个方法里起来的
    } catch (Throwable ex) { ... }
}
```

> **看到这里你就明白了：Spring Boot「内嵌 Tomcat」不是什么黑魔法，它就是往一个 `protected void onRefresh()` 里塞了一行。**

**小的：一次数据库调用**

```java
// JdbcTemplate
public <T> T execute(StatementCallback<T> action) throws DataAccessException {
    Connection con = DataSourceUtils.getConnection(obtainDataSource());   // 固定：拿连接
    Statement stmt = null;
    try {
        stmt = con.createStatement();                                     // 固定：建语句
        applyStatementSettings(stmt);                                     // 固定：设超时/批量
        T result = action.doInStatement(stmt);                            // ← 可变：你写的
        handleWarnings(stmt);                                             // 固定：处理告警
        return result;
    } catch (SQLException ex) {
        String sql = getSql(action);
        JdbcUtils.closeStatement(stmt);
        stmt = null;
        DataSourceUtils.releaseConnection(con, getDataSource());
        con = null;
        throw translateException("StatementCallback", sql, ex);            // 固定：异常翻译
    } finally {
        JdbcUtils.closeStatement(stmt);                                    // 固定：关语句
        DataSourceUtils.releaseConnection(con, getDataSource());           // 固定：还连接
    }
}
```

**这段代码值得逐行读三遍**，因为它解释了为什么你永远不该手写 JDBC：

| 如果你手写，你会漏掉什么 | `JdbcTemplate` 替你做了什么 |
|---|---|
| `finally` 里忘了关 `Statement` | `JdbcUtils.closeStatement(stmt)` |
| 忘了把连接还回池（不是 close！） | `DataSourceUtils.releaseConnection(...)` |
| 异常没翻译成 Spring 的 `DataAccessException` | `translateException(...)` |
| `catch` 里没关资源就 `throw` | 先关再抛（注意 `stmt = null` 防重复关） |

**这正是本项目 [`templatemethod`](../src/com/patterns/behavioral/templatemethod/README.md) 讲的「好莱坞原则」：`Don't call us, we'll call you`。**

> **模板方法最容易被误判为「平平无奇的继承」**。判断标准不是「有没有 abstract 方法」，而是：
>
> **「这段代码的控制权是不是从调用方转移到了父类？」**
>
> `JdbcTemplate` 的调用方只提供一个 lambda，其余全由框架决定——是模板方法。
> `AbstractList.add()` 调用子类的 `add(index, e)`——也是模板方法。
> 而一个 `AbstractService` 里放几个公共方法让子类去调——**不是**，那是「公用基类」，没有控制反转。

#### 2.1.3 `ApplicationListener` —— 观察者，附带一个「同步/异步」开关

```java
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {
    void onApplicationEvent(E event);
}
```

```java
@Component
public class OrderCreatedNotifier implements ApplicationListener<OrderCreatedEvent> {
    @Override
    public void onApplicationEvent(OrderCreatedEvent event) {
        // 你只关心「事件到了」，不关心谁发的
    }
}
```

框架侧（`SimpleApplicationEventMulticaster`，简化）：

```java
@Override
public void multicastEvent(final ApplicationEvent event, @Nullable ResolvableType eventType) {
    ResolvableType type = (eventType != null ? eventType : resolveDefaultEventType(event));
    Executor executor = getTaskExecutor();                        // ← 注意这一行
    for (ApplicationListener<?> listener : getApplicationListeners(event, type)) {
        if (executor != null) {
            executor.execute(() -> invokeListener(listener, event));   // 异步
        } else {
            invokeListener(listener, event);                            // 同步
        }
    }
}
```

**整个「同步还是异步」的差别，就在 `executor != null` 这一个判断上。** 而 `TaskExecutor` 是通过 `applicationEventMulticaster.setTaskExecutor(...)` 注入的——**默认是 `null`，也就是默认同步**。

| 配置 | 行为 | 后果 |
|---|---|---|
| 不设 `TaskExecutor`（默认） | 同步、在发布者线程里执行 | 监听器抛异常 → **发布者的事务回滚** |
| 设了 `TaskExecutor` | 异步、在线程池里执行 | 监听器抛异常 → **静默丢失**（进线程池就没人管了） |

**这个坑极其常见**：`@Transactional` 方法里 `publishEvent`，监听器发邮件失败 → 整个订单回滚。开发者以为是「通知失败不应该影响主流程」，实际上默认行为就是会。

> **这与 [`apigateway`](../src/com/patterns/combined/apigateway/README.md) §8.3 记录的「订阅者无异常隔离」是同一类缺陷**：观察者模式把「谁在听」解耦了，但**没有自动解耦「听的人失败」**。要不要隔离，是使用方必须显式决定的事。

Spring 还有个更现代的写法，是「注解 + 观察者」：

```java
@EventListener
public void handle(OrderCreatedEvent event) { ... }

@Async
@EventListener
public void handleAsync(OrderCreatedEvent event) { ... }   // 显式异步
```

**`@Async` 在这里比 `setTaskExecutor` 更好**：它把「异步」这个决定放在了监听器自己身上，而不是全局开关上。**全局开关会让所有监听器一起变，颗粒度太粗。**

#### 2.1.4 `HandlerInterceptor` —— 数组式责任链（和嵌套式不是一回事）

```java
public interface HandlerInterceptor {
    default boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        return true;                    // 返回 false = 中断后续
    }
    default void postHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler, ModelAndView modelAndView) { }
    default void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) { }
}
```

框架侧（`HandlerExecutionChain`）：

```java
boolean applyPreHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    for (int i = 0; i < this.interceptorList.size(); i++) {
        HandlerInterceptor interceptor = this.interceptorList.get(i);
        if (!interceptor.preHandle(request, response, handler)) {
            triggerAfterCompletion(request, response, null);       // ← 只回滚「已经过的那几个」
            return false;
        }
        this.interceptorIndex = i;      // ← 记住走到哪了
    }
    return true;
}

void applyPostHandle(HttpServletRequest request, HttpServletResponse response, ...) {
    for (int i = this.interceptorList.size() - 1; i >= 0; i--) {    // ← 倒序！
        this.interceptorList.get(i).postHandle(request, response, modelAndView);
    }
}
```

**注意这里没有 `chain.doFilter()`。** 它是**数组循环**，不是**递归嵌套**。这两种实现方式看起来结果一样，其实差别很大：

| | 嵌套式（`Filter.doFilter`） | 数组式（`HandlerInterceptor`） |
|---|---|---|
| 代码形状 | `chain.doFilter(req, res)` 递归下去 | `for` 循环 |
| 「后置」逻辑怎么写 | 自然写在 `doFilter` 调用**之后** | 必须**显式倒序再循环一遍** |
| 能中途改变「下一个是谁」吗 | 能（`chain` 是对象） | 不能（数组固定） |
| 出异常时「后置」还会执行吗 | 用 `try/finally` 可以保证 | 靠 `triggerAfterCompletion` 单独处理 |
| 典型 | Servlet `Filter`、本项目 `GatewayFilter` | Spring MVC `HandlerInterceptor` |

**为什么 Spring MVC 选了数组式？** 因为拦截器需要「**在任何一步失败时，精确地只回滚已经执行过的那些**」（`interceptorIndex`）。用数组 + 下标最直接；用递归嵌套就得每层自己 `try/finally`。

> **这是「同一个模式，两种正确实现」的典型例子。**
>
> 不要因为教科书上画的是「递归 + next」，就认为数组式「不是责任链」。**判断标准是「一个请求沿多个处理者传递，且可以在中途截断」，不是代码长相。**

**三个方法各自的执行时机（这张表决定了你该把逻辑写在哪里）**：

```text
preHandle ①  →  preHandle ②  →  preHandle ③  →  Controller  →  postHandle ③ ② ①
                                                                    ↓
                                                        afterCompletion ③ ② ①（无论成败）
```

| 你想干的事 | 写在哪 | 为什么 |
|---|---|---|
| 权限校验 | `preHandle` | 拿得到 `handler`，能提前返回 `false` |
| 往 `ModelAndView` 里塞公共数据 | `postHandle` | 此时 Controller 已执行完 |
| 记录耗时 / 释放 ThreadLocal | `afterCompletion` | **只有它保证一定执行**（即使抛异常） |
| 修改响应体 | 做不到 | 响应已提交，应该用 `ResponseBodyAdvice` 或 `Filter` |

> **最容易踩的坑**：在 `preHandle` 里往 `ThreadLocal` 存了东西，却在 `postHandle` 里清理。**controller 抛异常时 `postHandle` 不执行，`ThreadLocal` 泄漏到下一个请求。** 必须写在 `afterCompletion`。

---

### 2.2 Netty：`ChannelPipeline` 是**双向**责任链

如果你只记一件事，记这个：**Netty 的链有两个方向，`ctx.write()` 和 `channel.write()` 的起点不一样。**

```java
public interface ChannelPipeline extends ChannelInboundInvoker, ChannelOutboundInvoker {
    ChannelPipeline addLast(String name, ChannelHandler handler);
    ChannelPipeline addFirst(String name, ChannelHandler handler);
}
```

用法：

```java
ServerBootstrap b = new ServerBootstrap();
b.childHandler(new ChannelInitializer<SocketChannel>() {
    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
          .addLast(new IdleStateHandler(60, 0, 0))       // 入站：空闲检测
          .addLast(new LengthFieldBasedFrameDecoder(...)) // 入站：拆包
          .addLast(new ByteToMessageDecoder() {           // 入站：解码（模板方法）
              @Override protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) { }
          })
          .addLast(new BusinessHandler())                 // 入站：业务
          .addLast(new LengthFieldPrepender(4))           // 出站：加长度字段
          .addLast(new StringEncoder());                  // 出站：编码
    }
});
```

**方向规则（Netty 官方文档的原话，我转成一张表）**：

| 事件类型 | 传播方向 | 触发方式 |
|---|---|---|
| 入站（`channelRead`、`channelActive`、`exceptionCaught`） | **head → tail** | `ctx.fireChannelRead(msg)` 传给下一个**入站** handler |
| 出站（`write`、`flush`、`connect`、`bind`） | **tail → head** | `ctx.write(msg)` 传给**上一个**出站 handler |

**而 `ctx` 和 `channel` 的区别，是 Netty 排名第一的坑**：

```java
ctx.write(msg);            // 从「当前 handler 的前一个出站 handler」开始往回走
ctx.channel().write(msg);  // 从 tail 开始，走完整条链（会穿过当前 handler 自己）
```

**为什么这个区别致命？** 举个真实的结构：

```text
head ← [decoder(in)] ← [bizHandler(in)] ← [encoder(out)] ← tail
```

在 `bizHandler` 里调 `ctx.channel().write(resp)`：从 `tail` 出发往回找，先遇到 `encoder`，编码，OK。
在 `bizHandler` 里调 `ctx.write(resp)`：从 `bizHandler` 往回找 —— 前面只有 `decoder`，它是 **inbound**，跳过 —— **一路走到 head，什么都没做，直接发原始 `ByteBuf`**。

> **结果：对端收到的是一堆没编码的字节。** 代码不报错，日志不打印，只有抓包能看出来。

**这不是「记不记得住 API」的问题，是「有没有在脑子里画出那条链和它的方向」的问题。** 而这条链的形状，就是责任链模式。

**再看 `ChannelHandler` 家族里的其他模式**：

| Netty 组件 | 模式 | 证据 |
|---|---|---|
| `ByteToMessageDecoder` | 模板方法 | `decode()` 由子类实现，`channelRead` 里的「累积 → 循环 decode → 释放」全在父类 |
| `ByteBufAllocator` | 抽象工厂 + 享元 | `buffer()` / `heapBuffer()` / `directBuffer()` 造一族；`PooledByteBufAllocator` 复用内存块 |
| `EventLoopGroup` | 中介者 | 提交者不认识具体线程，只 `group.next().execute(task)` |
| `ChannelHandlerAdapter` / `SimpleChannelInboundHandler` | 适配器 | 把 7 个方法的接口适配成「只覆盖你关心的那个」 |
| `ReferenceCountUtil.release()` | 备忘录的反面 | 显式管理生命周期，而不是快照 |

**`SimpleChannelInboundHandler` 是适配器的活教材**：

```java
public abstract class SimpleChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        boolean release = true;
        try {
            if (acceptInboundMessage(msg)) {
                I imsg = (I) msg;
                channelRead0(ctx, imsg);      // ← 你只写这一个
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (autoRelease && release) {
                ReferenceCountUtil.release(msg);   // ← 你不用管释放
            }
        }
    }
    protected abstract void channelRead0(ChannelHandlerContext ctx, I msg);
}
```

> **记住这个判断**：`ChannelInboundHandler` 有 7 个方法，你通常只关心 1 个。**当一个接口的方法太多而你只需要一个时，适配器（或抽象适配器）就是标准答案。** 这与 [`adapter`](../src/com/patterns/structural/adapter/README.md) 里「`MediaAdapter` 把 3 个方法的接口适配成 1 个」是同一个动作。

---

### 2.3 MyBatis：代理套责任链，两层

MyBatis 的插件机制是 23 个模式里**最值得反复读的一处真实代码**，因为它把两个模式**串联**了起来，而且只用了一个注解。

```java
public interface Interceptor {
    Object intercept(Invocation invocation) throws Throwable;

    default Object plugin(Object target) {
        return Plugin.wrap(target, this);        // ← 默认实现已经够用
    }

    default void setProperties(Properties properties) { }
}
```

你的插件：

```java
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = { MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class }
    )
})
public class PageInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 前置：改 SQL，加 LIMIT
        // 关键：一定要调用 proceed，否则原方法不会执行
        Object result = invocation.proceed();
        // 后置：把结果包装成 Page
        return result;
    }
}
```

**第一层：责任链（`InterceptorChain`）**

```java
public class InterceptorChain {
    private final List<Interceptor> interceptors = new ArrayList<>();

    public Object pluginAll(Object target) {
        for (Interceptor interceptor : this.interceptors) {
            target = interceptor.plugin(target);       // ← 每加一个，就多包一层
        }
        return target;
    }
}
```

**第二层：动态代理（`Plugin`）**

```java
public class Plugin implements InvocationHandler {
    private final Object target;
    private final Interceptor interceptor;
    private final Map<Class<?>, Set<Method>> signatureMap;

    public static Object wrap(Object target, Interceptor interceptor) {
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
        Class<?> type = target.getClass();
        Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
        if (interfaces.length > 0) {
            return Proxy.newProxyInstance(                       // ← 动态代理
                type.getClassLoader(), interfaces,
                new Plugin(target, interceptor, signatureMap));
        }
        return target;                                            // 没有匹配接口就原样返回
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            Set<Method> methods = signatureMap.get(method.getDeclaringClass());
            if (methods != null && methods.contains(method)) {     // ← 只拦你声明过的方法
                return interceptor.intercept(new Invocation(target, method, args));
            }
            return method.invoke(target, args);                    // 其他方法直接透传
        } catch (Exception e) {
            throw ExceptionUtil.unwrapThrowable(e);                // ← 去掉 InvocationTargetException 包装
        }
    }
}
```

**把两层叠起来看，一个 `executor.query(...)` 的实际链路是**：

```text
你的代码
  └─→ proxy3 (拦截器 3 最外层)
        └─→ proxy2 (拦截器 2)
              └─→ proxy1 (拦截器 1 最早注册，最内层)
                    └─→ 真正的 SimpleExecutor.query(...)
```

**三个由代码直接推出的结论**：

| 结论 | 依据 |
|---|---|
| **后注册的拦截器在外层，先被调用** | `for` 循环里 `target = plugin(target)`，最后包的是最外层 |
| **不调 `invocation.proceed()` 就等于「吃掉」了这个调用** | 只有 `proceed()` 会执行 `method.invoke(target, args)` |
| **拦截器自己抛的异常不会被 `unwrapThrowable` 处理** | `catch` 只包住 `method.invoke` 和 `intercept`，`intercept` 里抛的原始异常会被再包一层 |

**为什么分页插件必须最后配置？** 因为分页要改写 SQL：

- 如果分页插件在**内层**，它改写完 SQL 后，外层的日志插件打印的是**改写后**的 SQL —— 你在日志里看不到原始 SQL；
- 如果分页插件在**外层**，它先改 SQL 再交给内层，内层的日志插件打印的是**改写后**的 SQL —— 你能看到真正执行的 SQL。

> **这是「链的顺序」最有说服力的一次实战证明**：模式不难，**难的是决定谁在谁前面**。本项目的 [`apigateway`](../src/com/patterns/combined/apigateway/README.md) §3.2 用「配额在缓存后面会漏算」把这件事讲透了——**同一个道理，一个在 MyBatis，一个在网关。**

**MyBatis 里还有一组策略**：

| 扩展点 | 模式 | 你实现它来做什么 |
|---|---|---|
| `TypeHandler<T>` | 策略 | Java 类型 ↔ JDBC 类型的转换（`LocalDateTimeTypeHandler`） |
| `StatementHandler` | 策略 | 预编译 / 普通 / 存储过程三种 |
| `Executor` | 模板方法 + 策略 | `BaseExecutor` 定流程（一级缓存、事务），`SimpleExecutor` / `ReuseExecutor` / `BatchExecutor` 填实现 |
| `ObjectFactory` | 工厂方法 | 创建结果对象 |
| `KeyGenerator` | 策略 | 自增主键回填 |
| `MappedStatement.Builder` | 建造者 | 十几个可选参数，必须分步构建 |

**`BaseExecutor` 是模板方法的干净样本**：

```java
@Override
public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                         ResultHandler resultHandler) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);   // ← 模板
}

private <E> List<E> query(..., CacheKey key, BoundSql boundSql) throws SQLException {
    list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;   // 一级缓存
    if (list != null) {
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
    } else {
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);  // ← 查库
    }
    return list;
}

protected abstract List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                   ResultHandler resultHandler, BoundSql boundSql) throws SQLException;
```

> **看完这段你就知道为什么「一级缓存关不掉」了**：它是 `query` 方法里的固定步骤，不是可覆盖的钩子。**模板方法的固定部分越强，子类的自由度越低——这是它的固有代价，不是 bug。**

---

### 2.4 日志：facade / bridge / adapter 三个词不是一回事

这三个词在中文技术圈几乎被混用，但在日志生态里它们指**三个不同的东西**：

```text
你的代码
   │  只依赖 org.slf4j.Logger          ← ① 外观（Facade）
   ▼
SLF4J API
   │  运行时通过 SPI 找到实现            ← ② 桥接（Bridge）
   ▼
Logback / Log4j2  （实现）
```

| 词 | 在这个图里是什么 | 具体类 | 模式 |
|---|---|---|---|
| **外观** | 你依赖的稳定接口 | `org.slf4j.Logger`、`LoggerFactory` | 外观（门面） |
| **桥接** | 抽象与实现的分离 + 运行时可替换 | `SLF4JServiceProvider`（SPI） | 桥接 |
| **适配器** | 把第三方日志 API **转**成 SLF4J | `jul-to-slf4j`、`jcl-over-slf4j`、`log4j-over-slf4j` | 适配器 |

**为什么必须区分？** 因为它们解决的是三种不同的问题：

| 问题 | 用哪个 |
|---|---|
| 「我的代码不想依赖具体日志实现」 | 外观（依赖 `slf4j-api`） |
| 「同一个 jar 在不同环境用不同实现」 | 桥接（换 classpath 上的实现包） |
| 「第三方库用的是 JUL/JCL/Log4j1，我想统一收口」 | 适配器（`-over-slf4j` / `jul-to-slf4j`） |

**这是本项目 [`facade`](../src/com/patterns/structural/facade/README.md)、[`bridge`](../src/com/patterns/structural/bridge/README.md)、[`adapter`](../src/com/patterns/structural/adapter/README.md) 三个模式唯一的「同框」案例**——它们经常同时出现在同一个框架里，**但职责完全不同**。

> **一个可直接用的判断口诀**：
>
> - **外观**：`A.foo()` 背后调了 A、B、C，但**接口数量变少了**。
> - **桥接**：`A` 里持有 `B` 的接口引用，**A 和 B 各自都能独立扩展**。
> - **适配器**：`A` 不满足 `I`，于是写个东西**同时是 `I` 又持有 `A`**。

**日志链本身也是责任链**：`Appender` 链 + `Filter` 链。

```text
Logger → Logger(父) → Root Logger → Appender1 → Filter → Layout → 输出
                                  → Appender2 → ...
```

| 组件 | 模式 |
|---|---|
| `Logger` 的 parent 继承 + 级别传递 | 责任链（`ClassLoader` 双亲委派是同一个形状） |
| `Appender` 集合 | 责任链（每个都能决定「写完继续 / 不写」） |
| `Filter`（`ThresholdFilter` / `BurstFilter`） | 责任链的「拦截器」 |
| `Layout` / `Encoder` | 策略（`PatternLayout` / `JsonLayout`） |

**一个几乎所有人都会踩的坑**：`logback.xml` 里的 `<root level="INFO">` 和 `<logger name="com.foo" level="DEBUG">` —— 子 logger 只设级别、不设 `additivity="false"` 时，**日志会被处理两次**（自己的 appender 一次 + 父的 appender 一次）。

> 这不是配置语法问题，是**责任链没「截断」**。`additivity="false"` 就是那条「短路」语句。

---

### 2.5 序列化：Jackson 的策略与组合

```java
public abstract class JsonSerializer<T> {
    public abstract void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException;
}

public abstract class JsonDeserializer<T> {
    public abstract T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException;
}
```

**这就是策略模式，而且是最干净的那种**：一个方法、多个实现、由外部注册、运行时按类型选择。

```java
public class MoneySerializer extends JsonSerializer<Money> {
    @Override
    public void serialize(Money value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.getAmount().toPlainString() + " " + value.getCurrency());
    }
}

SimpleModule module = new SimpleModule();
module.addSerializer(Money.class, new MoneySerializer());
mapper.registerModule(module);
```

| Jackson 组件 | 模式 | 说明 |
|---|---|---|
| `JsonSerializer` / `JsonDeserializer` | 策略 | 按类型选序列化实现 |
| `JsonMapper.builder()` | 建造者 | 二十多个开关，必须分步 |
| `ObjectMapper` | 外观 | 一个 `readValue` 藏了 tokenizer + 绑定 + 校验 |
| `JsonNode` 树 | 组合 | `ObjectNode` / `ArrayNode` / `ValueNode` 同为 `JsonNode` |
| `BeanSerializerModifier` | 责任链 | 在你注册的修饰器里逐个改写序列化器 |
| `AnnotationIntrospector` | 策略 | 决定注解怎么解释 |
| `SerializationFeature` / `DeserializationFeature` | 枚举 + 开关 | 不是模式，是配置 |

**`JsonNode` 是「组合模式」的现代样本**，而且它说明了组合模式的一个重要取舍：

```java
JsonNode root = mapper.readTree(json);
JsonNode city = root.path("user").path("address").path("city");
if (city.isMissingNode()) { ... }        // ← 缺失节点返回 MissingNode，不是 null
```

| 与经典组合的差别 | AWT `Container` | Jackson `JsonNode` |
|---|---|---|
| 容器能加子节点吗 | 能（`add`） | `ObjectNode` 能，`ArrayNode` 能，`ValueNode` 不能 |
| 缺失怎么表示 | `null` | `MissingNode`（**空对象模式**） |
| 好处 | 类型统一 | 链式 `path` 不会 NPE |

> **`MissingNode` 是「空对象模式（Null Object）」的教科书用法**：它同样是 `JsonNode`，所有 `path` / `asText` 都能调，只是永远返回默认值。这样 `root.a().b().c()` 就不需要三层 `if (x != null)`。

**Jackson 里一个值得注意的「不是模式」**：

`@JsonIgnore` / `@JsonProperty` 这些注解**不是模式**，它们是**声明式配置**。很多人把「注解驱动」当成一种设计模式，其实不是——**注解只是把「策略的选择」从代码挪到了元数据里**。真正的模式仍是那个被选的策略（`JsonSerializer`）。

---

### 2.6 测试：JUnit 5 的 `Extension` 是「责任链 + 策略」的组合

```java
public interface Extension { }        // 纯标记接口

// 具体能力全是子接口：
public interface BeforeEachCallback extends Extension {
    void beforeEach(ExtensionContext context) throws Exception;
}
public interface AfterEachCallback extends Extension {
    void afterEach(ExtensionContext context) throws Exception;
}
public interface TestExecutionExceptionHandler extends Extension {
    void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Exception;
}
public interface ParameterResolver extends Extension {
    boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext);
    Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext);
}
public interface ExecutionCondition extends Extension {
    ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context);
}
```

用法：

```java
@ExtendWith(TimingExtension.class)
class OrderServiceTest { ... }

public class TimingExtension implements BeforeEachCallback, AfterEachCallback {
    @Override public void beforeEach(ExtensionContext ctx) { start.put(ctx.getUniqueId(), System.nanoTime()); }
    @Override public void afterEach(ExtensionContext ctx)  { /* 算耗时 */ }
}
```

**为什么 `Extension` 是个空接口 + 一堆子接口，而不是一个大接口？**

| 如果是一个大接口 | 现在是多个子接口 |
|---|---|
| 每个扩展都要实现 10 个方法 | 只实现你要的那几个 |
| 无法表达「我只需要 `ParameterResolver`」 | 类型系统直接表达能力 |
| 框架无法按能力分派 | 框架按 `instanceof` 分派到不同阶段 |

> **这是「接口隔离原则（ISP）」在模式层面的应用，也是适配器的另一种解法**：
>
> 上一节 Netty 用「抽象适配器类」解决「接口太大」；JUnit 5 用「拆成多个小接口」解决同一个问题。
> **一个是类继承，一个是接口组合——现代 Java 偏好后者。**

**责任链体现在哪？** 在 `ExecutionCondition` 上：

```text
遍历所有注册的 ExecutionCondition：
    evaluateExecutionCondition(ctx)
       → DISABLED 就立刻跳过这个测试（短路）
       → ENABLED  就继续问下一个
```

**这就是 `@Disabled`、`@EnabledOnOs`、`@EnabledIf` 能叠加的原因**：每一条都是一环，任意一环说「不」，测试就不跑。

| 扩展点 | 模式 | 与 [本项目](../src/com/patterns/combined/apigateway/README.md) 的对应 |
|---|---|---|
| `ExecutionCondition` | 责任链（短路） | `GatewayFilter` 的「一票否决」 |
| `BeforeEachCallback` / `AfterEachCallback` | 责任链（前后置） | `MetricsHandler` 的前后计时 |
| `ParameterResolver` | 策略 | `LoadBalancer` 的「按条件选一个」 |
| `TestExecutionExceptionHandler` | 策略 | 异常分类（对应 `GatewayResponse` 的 `status` 分桶） |
| `@Nested` | 组合 | 树形结构 |

---

### 2.7 响应式：操作符链 = 装饰器 + 观察者 + 背压

```java
Flux.fromIterable(orders)
    .filter(o -> o.amount() > 100)
    .map(this::enrich)
    .timeout(Duration.ofMillis(500))
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe(this::onNext, this::onError);
```

**这一串链，每一环在类型上都是一个 `Flux<T>`** —— 和 `BufferedInputStream` 完全同构：

| 特性 | `BufferedInputStream` | `Flux.filter` |
|---|---|---|
| 类型是否相同 | 输入输出都是 `InputStream` | 输入输出都是 `Flux` |
| 是否持有被包装者 | 是（构造器传入） | 是（`this.source`） |
| 能否无限叠加 | 能 | 能 |
| 什么时候真正执行 | 读的时候 | **`subscribe` 的时候**（惰性） |

**所以整条链是装饰器**。但 `Flux` 的装饰不是「先包好再传」，而是**在 `subscribe` 时倒着组装**：

```java
// 简化后的 FluxMap
public final <V> Flux<V> map(Function<? super T, ? extends V> mapper) {
    return new FluxMap<>(this, mapper);      // 只是记住「上游是谁 + 要做什么」
}

@Override
public void subscribe(CoreSubscriber<? super R> actual) {
    source.subscribe(new MapSubscriber<>(actual, mapper));   // ← 真正组装发生在订阅时
}
```

**为什么要倒着？** 因为**数据流的方向和调用方向相反**：

```text
调用方向（组装）：  subscriber ← map ← filter ← source
数据方向（运行）：  subscriber → map → filter → source   （其实也是「向上游请求」）
```

**这个「装配期 / 运行期分离」正是响应式的难点**，也是「为什么我的 `doOnNext` 没打印」这类问题的根源：**没有终止操作（`subscribe`）就一个都不会执行**。

**`retryWhen` 与本项目的 `RetryHandler` 是同一个模式**：

| | `apigateway` 的 `RetryHandler` | Reactor 的 `retryWhen` |
|---|---|---|
| 重试次数 | `maxAttempts` | `Retry.backoff(3, ...)` |
| 换目标 | `repickInstance()` | 重新 `subscribe` 上游 |
| 判断条件 | `upstreamFailure()` | `onError` 的异常类型 |
| 退避 | 没有（立即重试） | 内置指数退避 + 抖动 |
| 熔断 | `CircuitBreakerHandler` 包在外面 | `resilience4j` 的操作符 |

> **`apigateway` 的 §7.1 OOM 事故在 Reactor 里不可能发生**——因为「重试」不是 `while` 循环，而是**重新订阅**。**没有循环变量，就没有「循环变量写错」这个 bug。**
>
> **这是「模式被库吸收」的正面价值：不是让你少写代码，而是让一类 bug 不可能出现。**

**背压是观察者的升级**（§1.3 ⑲ 讲过），在代码里长这样：

```java
Flux.range(1, 1_000_000)
    .subscribe(new BaseSubscriber<Integer>() {
        @Override protected void hookOnSubscribe(Subscription subscription) {
            request(1);                       // ← 只要 1 个
        }
        @Override protected void hookOnNext(Integer value) {
            // 处理完再要下一个
            request(1);
        }
    });
```

**没有背压会怎样？** 发布者全速生产，订阅者处理不过来，队列撑爆 → `OutOfMemoryError`。**这和 §7.1 的 OOM 是同一类问题：生产者没有限速。**

---

### 2.8 小结：框架扩展点 → 模式（一张可直接查的表）

| 你 `@Override` 的那个方法 | 模式 | 一句话说明为什么它是这个模式 |
|---|---|---|
| `BeanPostProcessor.postProcessAfterInitialization` | 责任链 | 排好序逐个执行，输出作为下一个的输入，`null` 短路 |
| `AbstractApplicationContext.onRefresh` | 模板方法 | 框架定 12 步顺序，你只填其中一步 |
| `ApplicationListener.onApplicationEvent` | 观察者 | 发布者不认识你，你只认识事件 |
| `HandlerInterceptor.preHandle` | 责任链（数组） | 循环 + 下标 + `false` 截断 |
| `ChannelHandler.channelRead` | 责任链（双向） | 入站正向、出站反向 |
| `ByteToMessageDecoder.decode` | 模板方法 | 累积、循环、释放都在父类 |
| `Interceptor.intercept` | 代理 + 责任链 | `Plugin` 造代理，`pluginAll` 串链 |
| `TypeHandler.setParameter` | 策略 | 一个方法、多实现、运行时选 |
| `JsonSerializer.serialize` | 策略 | 同上 |
| `ExecutionCondition.evaluateExecutionCondition` | 责任链 | 任一环 `DISABLED` 即短路 |
| `ProducerInterceptor.onSend` | 责任链 | 逐环改写 `ProducerRecord` |
| `Converter.convert` | 策略 | 类型转换的可替换规则 |
| `FactoryBean.getObject` | 工厂方法 | 把「造对象」这件事本身变成一个 bean |
| `@Transactional`（你不写方法） | 代理 | 你只写注解，代理帮你开事务 |

**如果只能记住一条**：

> **框架给你的每一个 `@Override`，都是它自己不敢做的决定。**
> 它不敢决定「用什么序列化」「失败怎么办」「顺序怎么排」，所以留一个洞让你填。
> **你填的形状，就是模式。**

---

## 3. 云原生：模式被放大了 1000 倍

### 3.0 先说结论：分布式系统不是新东西，是同一批模式换了单位

你在一个 JVM 里写的这些东西：

```text
方法调用 → 过滤器链 → 装饰器栈 → 策略选择 → 状态机
```

到了分布式系统里变成了：

```text
RPC 调用 → 网关插件链 → Sidecar 代理链 → 负载均衡策略 → 熔断器集群状态
```

**形状完全一样，只是「调用」变成了「网络请求」。** 这个观察有一个非常实际的用处：

> **你在单机上想不明白的设计，去分布式里找一个成熟的对应物，它一定已经解决了同样的问题。**
> 反之亦然——**分布式里的复杂方案，往往能在单机模式里找到它最初的原型。**

下面这张表是本章的索引：

| 分布式组件 | 它其实是 | 本章小节 |
|---|---|---|
| Nginx / Kong / Spring Cloud Gateway / Envoy | 责任链 + 策略 + 享元 | §3.1 |
| Service Mesh / Sidecar | 把**装饰器栈**搬到进程外 | §3.2 |
| Resilience4j / Sentinel / Hystrix | 装饰器 + 状态 + 策略 | §3.3 |
| Saga / TCC | 状态机 + 命令（带补偿） | §3.4 |
| Kafka / RocketMQ | 中介者 + 观察者（带持久化） | §3.5 |
| 注册中心 / 配置中心 | 中介者 + 观察者 + 享元 | §3.6 |

---

### 3.1 网关：模式密度最高的一个组件

本项目已经有一个完整的网关实现：[`apigateway`](../src/com/patterns/combined/apigateway/README.md)（6 个模式，2927 行，304 行可复现输出）。这里做的是**把它和工业级网关摆在一起对照**——目的不是「证明我们写得对」，而是**发现哪些地方工业实现做了同样的选择，哪些地方它做得更狠**。

#### 3.1.1 四个真实网关的「链」长什么样

| 网关 | 链的单位 | 链的形状 | 证据（可查） |
|---|---|---|---|
| **Nginx** | phase（阶段） | **11 个固定阶段**，模块注册到某个阶段 | `NGX_HTTP_POST_READ_PHASE` → `SERVER_REWRITE` → `FIND_CONFIG` → `REWRITE` → `POST_REWRITE` → `PREACCESS` → `ACCESS` → `POST_ACCESS` → `PRECONTENT` → `CONTENT` → `LOG` |
| **OpenResty / Kong** | plugin + phase | 插件挂在 `access` / `header_filter` / `body_filter` / `log` 四个阶段 | `kong.plugins.*.access(self)` |
| **Spring Cloud Gateway** | `GatewayFilter` | **有序链**（`Ordered` 接口），`GlobalFilter` 混在同一个链里 | `FilteringWebHandler.handle()` 里 `new DefaultGatewayFilterChain(filters).filter(exchange)` |
| **Envoy** | HTTP filter | 有序链，分 **decoder**（请求方向）与 **encoder**（响应方向）两趟 | `HttpConnectionManager` 的 `http_filters` 配置 |

**看到 Nginx 的 11 个 phase，你应该立刻想起 §2.1.2 的 `refresh()`**：

```text
Nginx:  11 个固定阶段，模块只能选「挂在哪个阶段」
Spring: 12 个固定步骤，子类只能覆盖「某几步」
```

**这是同一个东西的两个尺度的表达**：**模板方法把「顺序」变成了框架的资产，把「变化」变成了你的接口。**

> **Nginx 的 phase 设计还有一个更狠的地方**：它不是「一个请求走完整条链」，而是**每个 phase 都有一个 handler 列表，但绝大多数 phase 只允许一个模块处理**（`FIND_CONFIG`、`POST_REWRITE`、`POST_ACCESS` 是严格的单处理者）。**这就是「责任链」和「流水线」的中间态**——某些环节允许多个处理者依次执行，某些环节只能有一个。

#### 3.1.2 与本项目的逐层对照（这张表就是「你学的模式在哪里」）

| 本项目 | 工业实现 | 相同点 | 工业实现多做的那一步 |
|---|---|---|---|
| `GatewayFilter` + `FilterChain.Cursor` | `GatewayFilter` + `DefaultGatewayFilterChain` | 同一个 `CallContext` 沿链传递 | 支持异步（`Mono<Void>`）与 `Ordered` 排序 |
| `AuthFilter` | `ext_authz`（Envoy）/ Kong `key-auth` | 一票否决（401） | 支持外部授权服务、JWT 验签、mTLS |
| `RateLimitFilter`（固定窗口） | Redis + 滑动窗口 / 令牌桶 | 按 key 计数 | 跨节点共享计数（本项目是单机 `Map`） |
| `CacheFilter`（`principal \| path`） | `proxy_cache` / `Cache-Control` | 按人 + 路径缓存 | 支持 stale-while-revalidate、缓存分片 |
| `RetryHandler` | `retry_policy`（Envoy） | 换实例重试 | 支持退避 + 抖动 + 预算（retry budget） |
| `CircuitBreakerHandler` | `outlier_detection` | 按实例熔断 | 半开探测 + 最小请求数 + 异常比例 |
| `LoadBalancer`（轮询 / 最少连接） | `round_robin` / `least_request` / `ring_hash` | 按策略选实例 | 支持一致性哈希、区域感知、慢启动 |
| `RouteTable` + `RouteConfig` | `CDS` / `RDS`（Envoy xDS） | 配置与实例池分离 | 配置**可热更新**且不重建连接 |
| `MetricsHandler` + `GatewayListener` | access log + stats + tracing | 在最外层计时 | 采样、聚合、上报到远端 |

**三个值得注意的「本项目做得不够、但模式没错」的地方**：

1. **`breakers` 是普通 `LinkedHashMap`**（§8.3 #3）——工业实现必须并发安全。**模式没问题，实现有问题。**
2. **`RateLimitFilter` 的计数在单机上**——多副本网关会各算各的。工业实现把计数搬到 Redis，**但模式（策略 + 责任链）一点没变**。
3. **没有配置热更新**——`RouteTable` 构造时确定，改配置要重启。工业实现用监听 + 重建表，**对应的是观察者 + 享元，不是新模式**。

> **这三条恰好说明一件事：模式解决「结构」，不解决「持久化、并发、一致性」。**
> 把模式当银弹的人，往往在这三件事上翻车。

#### 3.1.3 Spring Cloud Gateway 为什么和本项目「长得几乎一样」

看它的核心执行方法（简化）：

```java
public Mono<Void> handle(ServerWebExchange exchange) {
    Route route = exchange.getRequiredAttribute(GATEWAY_ROUTE_ATTR);
    List<GatewayFilter> gatewayFilters = route.getFilters();

    List<GatewayFilter> combined = new ArrayList<>(this.globalFilters);
    combined.addAll(gatewayFilters);
    AnnotationAwareOrderComparator.sort(combined);      // ← 排序！

    return new DefaultGatewayFilterChain(combined).filter(exchange);
}
```

```java
private static class DefaultGatewayFilterChain implements GatewayFilterChain {
    private final int index;
    private final List<GatewayFilter> filters;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            if (this.index < filters.size()) {
                GatewayFilter filter = filters.get(this.index);
                DefaultGatewayFilterChain chain = new DefaultGatewayFilterChain(this, this.index + 1);
                return filter.filter(exchange, chain);       // ← 递归下去
            } else {
                return Mono.empty();                        // ← 链尾
            }
        });
    }
}
```

**对照本项目的 `FilterChain.Cursor`**：

| | Spring Cloud Gateway | 本项目 `apigateway` |
|---|---|---|
| 链的载体 | `List<GatewayFilter>` + `index` | `List<GatewayFilter>` + `Cursor.position` |
| 前进方式 | 每次 new 一个 `index+1` 的 chain | 每次 new 一个 `Cursor(position+1)` |
| 为什么每次 new | **防止同一个 chain 被重试时状态错乱** | 同上（README §3.1 讲过） |
| 排序 | `AnnotationAwareOrderComparator.sort` | 构造时按注册顺序（`List` 顺序） |
| 短路 | 不调 `chain.filter()` 即可 | 不调 `chain.next()` 即可 |

> **「每次 new 一个链对象」这个细节，两边独立地做了同一个选择**——因为它不是风格问题，是**正确性问题**。
>
> **如果 `Cursor` 是复用的，一次重试就会从错误的位置继续，某些过滤器会被跳过。** 这类 bug 只在「重试 + 多过滤器」同时出现时才暴露，测试很难覆盖。
>
> 想验证这一点，去读 `apigateway` 的 `FilterChain.java`：`next()` 返回 `new Cursor(filters, position + 1)`。

---

### 3.2 Service Mesh：把「装饰器栈」搬到进程之外

#### 3.2.1 同一份逻辑，两种放法

本项目的服务包装（`Gateway.java` 里）：

```java
UpstreamHandler service = new ServiceHandler(instance);
UpstreamHandler breaker = new CircuitBreakerHandler(service, breakers, config);
UpstreamHandler retry   = new RetryHandler(breaker, config.maxAttempts());
UpstreamHandler metrics = new MetricsHandler(retry, collector);
```

把它画成图：

```text
[业务代码] → Metrics → Retry → CircuitBreaker → Service → [真实上游]
```

**Service Mesh 干的事，是把这个栈从「你的 JVM 里」搬到「你的 JVM 外面」**：

```text
┌─────────────────┐        ┌─────────────────┐
│  你的应用容器    │        │  Envoy sidecar  │
│  （只有业务代码）│  →     │  RateLimit      │
│                 │  网络   │  CircuitBreaker │
└─────────────────┘        │  Retry          │
                           │  Metrics        │
                           │  mTLS           │
                           └────────┬────────┘
                                    ↓
                                 上游服务
```

**关键点：`Metrics` / `Retry` / `CircuitBreaker` 这三层在两边是完全对应的。** 变的只有「调用方式」——从**方法调用**变成**网络请求**。

| 本项目（进程内） | Service Mesh（进程外） | 差别 |
|---|---|---|
| `MetricsHandler` 直接调 `collector.record(...)` | Envoy 打 stats / access log | 传输方式 |
| `RetryHandler` 里 `context.repickInstance()` | Envoy 重新选 cluster endpoint | 选点方式 |
| `CircuitBreakerHandler` 持有 `Map<String, CircuitBreaker>` | Envoy 的 `outlier_detection` 按 endpoint 统计 | 存储位置 |
| **改一行代码 + 重新编译** | **改一份 YAML + 热加载** | **变更成本** |

**最后一行才是 Service Mesh 存在的理由**：

> **进程内的装饰器栈，改一次要发一次版；进程外的装饰器栈，改一次只改配置。**
>
> 当一个组织有 200 个服务、每个服务都用同一套「重试 + 熔断 + 限流」时，**把这份重复 200 遍的代码换成一份共享的 sidecar，是唯一算得过来的账。**

#### 3.2.2 代价：装饰器搬到网络上的三个新问题

| 问题 | 进程内 | 进程外 |
|---|---|---|
| **延迟** | 一次方法调用（纳秒） | 多一跳网络（0.1~1 ms） |
| **上下文传递** | `CallContext` 对象直接传 | 必须塞进 HTTP header（`x-request-id`、`x-b3-traceid`） |
| **失败模式** | 抛异常 | 超时、连接重置、半开连接、HTTP 200 但 body 是错误 |

**第三行是最容易低估的**。本项目的 `GatewayResponse.upstreamFailure()` 判断的是「**可重试 且 不是网关拒绝**」。搬到网络上以后，这个判断变成了：

```text
哪些 HTTP 状态码可以重试？
  502 / 503 / 504  → 可以（上游真的挂了）
  500            → 通常不可以（可能是业务异常，重试会重复下单）
  429            → 可以，但要退避
  401 / 403      → 绝对不可以（重试一百次也是 401）
```

> **这与本项目 §6.3「三条 401 只有 1 行轨迹」是完全同一个判断**：401 不可重试，所以第一次就被 `AuthFilter` 短路了。
>
> **模式没变，只是「状态码」从 `int` 变成了 HTTP 语义。** 而 §7.1 的 OOM 事故，本质就是**没把「网关拒绝」和「上游故障」分开**——如果当时代码里判断的是「HTTP 503 才重试」，这个 bug 根本不会存在。

#### 3.2.3 什么时候**不该**上 Service Mesh

| 场景 | 判断 |
|---|---|
| 服务数 < 10 | 不值得。sidecar 的运维成本 > 收益 |
| 只需要「重试 + 超时」 | 客户端库（Resilience4j）足够，不用引入网格 |
| 团队没有 K8s 运维能力 | 会引入一类全新的故障（sidecar 注入失败、iptables 劫持错误） |
| 需要极低延迟（高频交易） | 多一跳不可接受 |

> **这是「模式在哪一层落地」的决策，不是「模式好不好」的决策。**
> **同一个装饰器栈**，放在代码里是「库」，放在进程外是「网格」，放在网关里是「插件」。
> **三种都对，取决于谁需要改它、多久改一次。**

---

### 3.3 熔断 / 限流 / 重试：工业实现怎么组装这三层

#### 3.3.1 Resilience4j：显式的装饰器链

```java
Supplier<GatewayResponse> decorated = Decorators.ofSupplier(() -> callUpstream())
        .withRetry(retry)                       // 最外层
        .withCircuitBreaker(circuitBreaker)
        .withRateLimiter(rateLimiter)
        .withTimeLimiter(timeLimiter)
        .withBulkhead(bulkhead)                 // 最内层
        .withFallback(List.of(CallNotPermittedException.class), e -> GatewayResponse.reject(503, "熔断中"))
        .decorate();
```

**这里有一个必须先说清的坑**：链式 API 的**书写顺序**和**运行时执行顺序**往往相反，而且**不同库的约定并不一样**。

- `Decorators.ofSupplier(...)` 的每一次 `withXxx` 都返回**一个新的装饰器**，装饰的是**上一步的结果**；
- 至于「越晚写越靠外」还是「越晚写越靠内」，**取决于这个库的 `withXxx` 是包在外面还是包在里面**——**去看你的版本的文档，不要凭记忆**。

> **所以不要去背「谁在前谁在后」，要背「为什么这个顺序是对的」。**
> 下面这张表是**分层意图**（外层 → 内层）以及每一条的**理由**——
> 只要你记住了「理由」，换任何一个库你都能自己推出顺序。

| 层（外 → 内） | 为什么必须在外面 |
|---|---|
| **Retry** | 要能重试**整条**内层链路，包括熔断器的拒绝 |
| **CircuitBreaker** | 要统计**每一次**上游尝试的结果 |
| **RateLimiter** | 配额按「一次用户请求」扣，不按「一次尝试」扣 |
| **TimeLimiter** | 单次调用的超时，应该只包住这一次真实调用 |
| **Bulkhead** | 最内层，限制**真实并发**的调用数 |

**而下面这张「顺序反了会怎样」才是你真正应该记住的东西**：

| 如果顺序反了 | 会发生什么 |
|---|---|
| `CircuitBreaker` 在 `Retry` **外面** | 整个重试序列只算**一次**熔断统计。3 次重试全失败 = 熔断器只记 1 次失败 |
| `RateLimiter` 在 `Retry` **里面** | 每次重试都消耗一个令牌 → **一次请求消耗 4 个配额** |
| `Bulkhead` 在 `Retry` **外面** | 重试期间一直占着信号量 → 并发数虚高 |
| `TimeLimiter` 在 `Retry` **外面** | 总超时 = 单次超时 → 第一次超时就整体失败，重试没机会 |

> **这四条，和本项目 README §6.6「重试 vs 熔断方向相反」讲的是同一件事**：
>
> **重试希望「失败被看见」（让熔断器记下每次失败），熔断希望「失败被拦住」（不要让重试打到已挂的实例）。**
> 两个目标相反，所以**顺序必须由你来定，框架不会替你选**。

#### 3.3.2 Sentinel：把责任链做成了「插槽」

Sentinel 的核心不是装饰器，而是**一条固定的责任链**：

```text
ProcessorSlotChain
  ├── NodeSelectorSlot      构建调用树
  ├── ClusterBuilderSlot    构建 ClusterNode
  ├── LogSlot               （异常时）记录日志
  ├── StatisticSlot         统计（RT、QPS、线程数）
  ├── AuthoritySlot         黑白名单
  ├── SystemSlot            系统自适应保护（全局 QPS/RT/线程数）
  ├── ParamFlowSlot         热点参数限流
  ├── FlowSlot              流控
  └── DegradeSlot           熔断降级
```

**这张顺序表本身就是一份设计文档**：

| 顺序 | 为什么必须在这个位置 |
|---|---|
| `StatisticSlot` 在 `FlowSlot` **之前** | 流控要看统计结果，统计必须先跑 |
| `AuthoritySlot` 在 `FlowSlot` **之前** | 黑名单直接拒，不该消耗配额（**和本项目一致**） |
| `SystemSlot` 在 `FlowSlot` **之前** | 系统整体过载时，先于单资源限流拦截 |
| `DegradeSlot` 在**最后** | 熔断是最后一道闸，前面的都放行了才轮到它 |

> **对照本项目 §6.4 的「401 不消耗配额」**：`AuthFilter` 在 `RateLimitFilter` 之前。
> **一模一样的取舍，在 Sentinel 里是 `AuthoritySlot` 在 `FlowSlot` 之前。**
>
> **这不是巧合**——只要「拒绝要免费」这个原则成立，顺序就只有一种正确解。

#### 3.3.3 三个实现的对照

| | 本项目 `apigateway` | Resilience4j | Sentinel |
|---|---|---|---|
| 组装方式 | 手写 `new A(new B(new C(...)))` | `Decorators` 链式 API | 固定 SlotChain |
| 链的顺序 | 你决定 | 你决定（有推荐） | **框架决定，你不能改** |
| 状态存储 | `Map<String, CircuitBreaker>`（单机） | 每实例一个对象 | 内存 + 可扩展 |
| 扩展新能力 | 加一个 `GatewayFilter` | 加一个 `Decorator` | 加一个 `Slot`（需改框架） |
| 优点 | 看得见每一层 | 灵活 | 保证正确顺序 |
| 缺点 | 顺序错了没人拦你 | 顺序错了没人拦你 | 想插入自定义逻辑很难 |

> **最后两行是这个对比里最值钱的部分**：
>
> **「顺序自由」和「顺序正确」是一对矛盾。**
> 手写组装给你全部自由，也给你全部犯错的空间——§7.1 的 OOM 就是这么来的。
> Sentinel 用「固定顺序」消灭了这类错误，代价是你不能插入自己的 Slot。
>
> **没有更好的选择，只有更适合当前阶段的权衡。**

---

### 3.4 Saga：状态机 + 命令（带补偿的那种）

#### 3.4.1 问题：跨服务的事务怎么办

单机里你有 `@Transactional`。跨服务没有——`下单` 成功了，`扣库存` 失败了，怎么办？

**Saga 的答案：每个正向操作配一个补偿操作，失败就倒着补偿回去。**

```text
正向：  创建订单 → 扣库存 → 扣余额 → 发通知
补偿：  取消订单 ← 还库存 ← 退余额 ← （通知不用补）
```

#### 3.4.2 用模式翻译一遍

| Saga 的概念 | 对应模式 | 用什么表达 |
|---|---|---|
| 每一步（正向 + 补偿） | **命令** | `record Step(Command doIt, Command undoIt)` |
| 当前走到第几步 | **状态** | `sealed interface SagaState` |
| 整体编排 | **模板方法** | 固定「执行 → 失败 → 逆序补偿」 |
| 补偿触发 | **观察者** | 监听失败事件 |

**用 `sealed` 表达状态（JDK 17+）**：

```java
sealed interface SagaState permits Running, Compensating, Completed, Failed { }

record Running(int nextStep) implements SagaState { }
record Compensating(int step) implements SagaState { }
record Completed() implements SagaState { }
record Failed(String reason) implements SagaState { }
```

**为什么用 `sealed` 而不是 `enum`？** 因为 `Compensating` 要带「补偿到第几步」，`Failed` 要带原因。**`enum` 携带不了不同的数据，`sealed interface` 可以。**

> **这就是 §4.2 要讲的「模式被语言吸收」的一个真实落点**：Saga 的状态机用 `sealed` + `switch` 写，比用三个类 + 一个接口简洁得多，而且**编译器保证穷尽**——加一个新状态，所有 `switch` 都会编译报错。

#### 3.4.3 两个必须知道的坑

| 坑 | 症状 | 根因 |
|---|---|---|
| **补偿不是回滚** | 数据「看起来恢复了」，但库存已经真实扣减又加回，中间有窗口 | 补偿是**另一个正向操作**，不是数据库的 undo |
| **补偿失败怎么办** | 卡在中间状态，人工介入 | Saga 没有「补偿的补偿」。只能靠**幂等 + 重试 + 对账** |

> **这是「命令模式」在分布式里的一个残酷现实**：GoF 的 `undo()` 假设「撤销是精确的」，而真实的分布式补偿**永远不是精确的**。
>
> 本项目的 [`command`](../src/com/patterns/behavioral/command/README.md) 文档里说了「真实系统里很少用 undo」——Saga 是那个例外：**它用，但用的是「补偿」而不是「撤销」。**

---

### 3.5 事件驱动：观察者 vs 发布/订阅，一字之差

这两个词经常被混用，但它们是**不同的东西**：

| | 观察者模式 | 发布/订阅 |
|---|---|---|
| 发布者认识订阅者吗 | **认识**（持有 `List<Listener>`） | **不认识**（只认识 broker） |
| 中间有 broker 吗 | **没有** | **有**（Kafka / RabbitMQ / Redis） |
| 同步还是异步 | 通常同步 | 通常异步 |
| 失败了怎么办 | 异常传回发布者 | broker 负责重投 |
| 本项目例子 | `apigateway` 的 `MetricsCollector` / `AlertListener` | **没有**（这是它的边界） |

**Kafka 在模式层面是什么？** 三个角色叠在一起：

```text
Producer  ──→  Broker（中介者）  ──→  ConsumerGroup（观察者容器）
   ↑                  │
   └── 不认识任何 Consumer ──┘
```

| Kafka 组件 | 模式 | 说明 |
|---|---|---|
| Broker | **中介者** | Producer 和 Consumer 只认识它，互不认识 |
| Consumer Group 内的分发 | **观察者** | 一个 partition 只给一个 consumer，组内是分配而非广播 |
| `ProducerInterceptor` | **责任链** | `onSend` 逐环改写 |
| `Partitioner` / `Serializer` | **策略** | 按 key 选分区、按类型选序列化 |
| `ConsumerRebalanceListener` | **观察者** | 重平衡前后的回调 |

**「观察者 vs 发布/订阅」的这个区别，有一个非常实际的后果**：

> **观察者模式的失败会传染给发布者，发布/订阅的失败不会。**
>
> 本项目的 `MetricsCollector` 如果抛异常，会打断整条 `GatewayFilter` 链（§8.3 #4 已列为瑕疵）。
> 换成 Kafka，`producer.send()` 失败只影响「指标丢了一条」。
>
> **所以「要不要引入 broker」这个问题，等价于「订阅者的失败要不要影响主流程」。**

**另一个必须知道的问题：双写不一致。**

```java
@Transactional
public void createOrder(Order order) {
    orderDao.insert(order);        // 数据库
    kafkaTemplate.send("order-created", order);   // 消息
}   // ← 事务提交时，消息可能已经发出去了
```

**如果事务回滚，消息已经发出去了 → 下游白白创建了一个不存在的订单。** 这就是**Outbox 模式**要解决的：

```text
同一个事务里：写业务表 + 写 outbox 表
事务提交后：一个独立进程读 outbox 表 → 发消息 → 标记已发
```

| 模式 | 用在哪 |
|---|---|
| 命令 | outbox 里存的是一条「待执行的消息发送命令」 |
| 观察者 | 下游订阅 |
| 中介者 | 消息总线 |

> **Outbox 是最典型的「模式组合解决一个具体问题」**：单用任何一个模式都不行，**必须三个一起**。

---

### 3.6 注册中心 / 配置中心：观察者 + 享元

**Nacos / Consul / Eureka 在模式层面做两件事**：

| 功能 | 模式 | 说明 |
|---|---|---|
| 服务注册与发现 | **中介者** | 服务 A 不认识服务 B，只认识注册中心 |
| 实例列表本地缓存 | **享元** | 一份 `ServiceInstance` 列表被所有调用方共享 |
| 配置变更推送 | **观察者** | 客户端注册 `Listener`，服务端推送变更 |
| 健康检查 | **状态** | `UP` / `DOWN` / `STARTING` / `OUT_OF_SERVICE` |
| 负载均衡 | **策略** | 客户端从本地缓存里选一个实例 |

**这和本项目的 `RouteTable` 是同一个结构**：

```java
// apigateway：享元池 + 每个实例一份可变状态
final class RouteTable {
    private final Map<Key, List<UpstreamInstance>> pools;   // 共享实例列表
}
```

**关键洞察：享元共享的必须是「不可变的配置」，不能是「可变的运行时状态」。**

| 共享什么 | 安全吗 | 为什么 |
|---|---|---|
| `RouteConfig`（路径、服务名、目标版本） | **安全** | 不可变 `record`，改了就是新对象 |
| `UpstreamInstance` 的 `inFlight` | **不安全，但必须共享** | 它是「配额」，共享才有意义 |
| 整个 `List<UpstreamInstance>` | 要注意 | 列表本身要不可变，元素要线程安全 |

> **这就是 `apigateway` 里 `UpstreamInstance.inFlight` 必须用 `AtomicInteger` 的原因**（§7.4 已核实）：**它不是「每个调用方一份」，而是「所有调用方共用一份」，所以必须是并发的。**

---

### 3.7 一条警告：分布式里最贵的错误，是在错误的层用模式

**重试风暴（retry storm）** 是这条警告最惨烈的案例：

```text
客户端重试 3 次
  → 网关也重试 3 次
    → 服务 A 也重试 3 次
      → 服务 B 也重试 3 次
```

**一次用户请求，打到服务 B 的请求量是 3 × 3 × 3 = 27 次。** 而上游已经挂了，**它需要的是「少一点请求」，不是「多一点」**。

| 层次 | 该不该重试 | 理由 |
|---|---|---|
| 客户端（App/浏览器） | 谨慎 | 用户能自己点重试 |
| 网关 | **应该** | 它能换实例，且知道哪些错误可重试 |
| 服务 A 调 B | 可以，但**必须带退避 + 预算** | 否则放大 |
| 服务 B 调 C | **通常不该** | 如果每层都重试，放大是乘法的 |

**正确的做法是「只在最有信息的那一层重试」**：

> **谁最清楚「这个失败能不能重试」，谁负责重试。**
> 网关知道「503 = 上游挂了，换一个实例」，服务 B 不知道——它只知道「A 挂了」。
> **每层都重试 = 每层都在猜。**

**这与本项目 §8.4「数变更原因」是同一条原则的不同表达**：

> **一个决策，如果两个地方都能做，那它一定会变成两个不一致的决策。**
> 重试决策放两层，结果就是「网关重试了，服务又重试了，谁也不知道实际打了几次」。

**其他三个同类的「层次错误」**：

| 错误 | 症状 | 正确位置 |
|---|---|---|
| 在网关做业务校验 | 网关里塞了「订单金额必须 > 0」 | 服务内（网关不该认识业务语义） |
| 在服务内做认证 | 每个服务都实现一遍 JWT 验签 | 网关（一处验，下游信任） |
| 在数据库做限流 | 用 `SELECT ... FOR UPDATE` 当限流器 | 网关或服务内（数据库只该管数据） |

---

### 3.8 小结：云原生的模式地图

```text
                     ┌──────────────────────────────┐
                     │   Service Mesh（装饰器，进程外）│
                     │   重试 / 熔断 / mTLS / 遥测    │
                     └───────────────┬──────────────┘
                                     │
  客户端 ──→ ┌───────────────────────▼──────────────────────┐
             │  API Gateway（责任链 + 策略 + 享元）           │
             │  鉴权 → 限流 → 路由 → 缓存 → 重试 → 熔断      │
             └───────────────┬──────────────────────────────┘
                             │
                 ┌───────────▼───────────┐
                 │  服务 A（Saga 状态机）  │
                 │  ┌─────────────────┐  │
                 │  │ 业务 + Outbox    │  │
                 │  └────────┬────────┘  │
                 └───────────┼───────────┘
                             │  命令（待发消息）
                    ┌────────▼────────┐
                    │ Broker（中介者） │
                    └────────┬────────┘
                             │  观察者
              ┌──────────────▼───────────────┐
              │  下游服务（订阅 + 幂等 + 对账） │
              └──────────────────────────────┘
```

**这张图里，每一层都只是一两个模式。** 复杂的是**它们的组合**，不是它们本身。

> **云原生没有发明新模式。**
> 它把「一个 JVM 里的方法调用」换成了「跨进程的网络请求」，然后发现：
> **原来那套接口、顺序、状态、共享的问题，一个都没少，只是每个都被放大了 1000 倍。**

---

## 4. 语言在吃掉模式：record / sealed / 虚拟线程 / lambda

### 4.0 一个必须先说清的事实

**设计模式的书是 1994 年写的，那时 Java 还没有泛型、没有 lambda、没有 record。** 书里很多「模式」，本质上是在**用面向对象的语法弥补语言表达力的不足**。

三十年后，语言补上了这些表达力。于是出现了一个持续了二十年的争论：**模式是不是过时了？**

**我的答案（明确标注：这是观点）**：

> **语言吃掉的是「模式的样板代码」，吃不掉「模式的结构」。**
>
> 一个模式如果只剩「一个接口 + 一个实现类 + 一个 lambda」，它就被吃掉了。
> 一个模式如果还涉及「谁持有谁、谁先执行、谁替换谁」，它就没被吃掉——**因为那是设计问题，不是语法问题**。

这一章逐个检查：**哪些被吃了，哪些没有，以及判断的依据**。

> 本章只讨论 **JDK 21 已正式**的特性：`record`(16)、`sealed`(17)、`instanceof` 模式匹配(16)、`switch` 模式匹配(21)、虚拟线程(21)。JDK 22-25 的新特性不写（理由见开头的可靠性说明）。

---

### 4.1 `record`：吃掉了「值对象样板」，没吃掉「原型」

#### 4.1.1 它到底省掉了什么

```java
// JDK 16 之前：一个「值对象」要写这么多
public final class Point {
    private final int x;
    private final int y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public int getX() { return x; }
    public int getY() { return y; }
    @Override public boolean equals(Object o) { /* 6 行 */ }
    @Override public int hashCode() { return Objects.hash(x, y); }
    @Override public String toString() { return "Point[x=" + x + ", y=" + y + "]"; }
}

// JDK 16 之后：一行
public record Point(int x, int y) { }
```

**这是 40 行变 1 行。** 但注意：`record` 省掉的全是**机械代码**，**没有一行是设计决策**。

| `record` 自动生成 | 这原本属于哪个模式的职责 |
|---|---|
| 全字段 `private final` | 「不可变值对象」的通用要求（不是任何一个模式） |
| 构造器 + 访问器 | 数据载体的样板 |
| `equals` / `hashCode` | 值语义 |
| `toString` | 调试输出 |

> **结论：`record` 吃掉的不是某个模式，是「所有模式实现里都要写的样板」。**
> 它让**每个**模式的实现都变短了，但**没有让任何一个模式消失**。

#### 4.1.2 `record` 与建造者：不是替代关系，是互补关系

一个常见误解：「有了 `record` 就不用建造者了」。**错。**

```java
// record 的构造器：参数一多就难用
public record HttpRequest(URI uri, String method, Map<String, String> headers,
                          Duration timeout, BodyPublisher body, Version version) { }

// 调用现场：这是什么？
new HttpRequest(uri, "GET", Map.of(), Duration.ofSeconds(3), BodyPublishers.noBody(), Version.HTTP_2);
```

**三个致命问题**：

1. **位置参数**：第 4 个参数是超时还是重试次数？看不出来。
2. **可选参数**：只想改 `timeout`，其他全用默认值——做不到，必须全填。
3. **演进**：加一个 `followRedirects` 字段，**所有调用点全炸**。

所以 JDK 自己的 `HttpRequest` **不是** `record`，而是「`final` 类 + 内部 `Builder`」：

```java
HttpRequest request = HttpRequest.newBuilder()     // ← builder
        .uri(uri)
        .timeout(Duration.ofSeconds(3))            // ← 只设想改的
        .GET()
        .build();
```

**正确的组合方式**：

```java
public record UserProfile(String name, String email, int age) {
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String email;
        private int age;
        public Builder name(String n) { this.name = n; return this; }
        public Builder email(String e) { this.email = e; return this; }
        public Builder age(int a) { this.age = a; return this; }
        public UserProfile build() { return new UserProfile(name, email, age); }
    }
}
```

> **`record` 负责「不可变 + 值语义」，`Builder` 负责「分步 + 可选 + 可演进」。**
> **两个都要，缺一不可。** 本项目的 [`builder`](../src/com/patterns/creational/builder/README.md) 演示了带 `Director` 的经典形态，而 JDK 的 `HttpRequest.Builder` 是「调用方自己当 Director」的现代形态——**两种都算建造者。**

#### 4.1.3 `record` 让「备忘录」退化

本项目的 [`memento`](../src/com/patterns/behavioral/memento/README.md) 里，`Document` 要保存快照，必须造一个专门的 `DocumentMemento` 类（还必须是 `final` + 不可变）。

**如果状态本身是不可变的，备忘录就不需要了**：

```java
public record Document(String title, String body) { }

// 快照 = 持有旧引用
Document old = current;      // ← 这就是备忘录
current = current.withBody("新内容");
```

**为什么可以这么简单？** 因为 `record` 不可变——**你手里的 `old` 永远不会被别人改掉**。

| 状态是否可变 | 备忘录怎么写 |
|---|---|
| **可变**（`StringBuilder`、`List`） | 必须深拷贝一份快照，还要防「快照被外部改」 |
| **不可变**（`record`、`List.of`） | **持有旧引用就是快照**，零成本 |

> **这是「语言特性吸收模式」最干净的一例**：
> 不是「不需要备份」了，而是**「备份」这个动作变得免费，于是不再需要一个专门的模式类去承载它**。
>
> 这与 §1.3 ⑱ 的结论一致：**备忘录在现代 Java 里的地位下降了，因为不可变对象变便宜了。**

#### 4.1.4 `record` 吃掉了「单例」吗

没有。`record` 和单例解决的是两件事：

| | `record` | 单例 |
|---|---|---|
| 关注 | 「值相同即相等」 | 「全进程只有一个实例」 |
| 实例数 | **想造几个造几个** | **只能有一个** |
| 典型误用 | 用 `record` 表示有身份的对象（如 `User` 有 id） | 用单例包一个无状态工具类 |

**但有一个交集**：`record` 可以当「不可变配置」的载体，配合单例持有：

```java
public enum Config {
    INSTANCE;                                       // enum 单例（唯一能防反射的写法）
    private final Limits limits = new Limits(3, 1000, 60);
}

record Limits(int maxAttempts, int rateLimit, int windowSeconds) { }
```

> **`enum` 单例 + `record` 配置**，是 JDK 21 下最简的「全局不可变配置」写法。**两行代码，同时拿到了「唯一」和「不可变」。**

---

### 4.2 `sealed` + `switch`：吃掉「访问者」的一半

这是本章最重要的一节，因为它对应 §1.3 ㉓ 承诺的「详见 §4.2」。

#### 4.2.1 先看传统访问者有多重

```java
// 元素侧：每种形状都要写一个 accept
interface Shape { <R> R accept(ShapeVisitor<R> visitor); }

record Circle(double r) implements Shape {
    public <R> R accept(ShapeVisitor<R> v) { return v.visitCircle(this); }
}
record Square(double a) implements Shape {
    public <R> R accept(ShapeVisitor<R> v) { return v.visitSquare(this); }
}

// 访问者侧：每个操作都要写一个 Visitor
interface ShapeVisitor<R> {
    R visitCircle(Circle c);
    R visitSquare(Square s);
}

class AreaVisitor implements ShapeVisitor<Double> {
    public Double visitCircle(Circle c) { return Math.PI * c.r() * c.r(); }
    public Double visitSquare(Square s) { return s.a() * s.a(); }
}
```

**为了算一个面积，写了 4 个类型、3 个方法声明、2 个 `accept` 实现。** 这就是访问者被诟病的「重」。

#### 4.2.2 `sealed` + `switch` 版本

```java
sealed interface Shape permits Circle, Square { }
record Circle(double r) implements Shape { }
record Square(double a) implements Shape { }

// 算面积
double area(Shape s) {
    return switch (s) {
        case Circle c -> Math.PI * c.r() * c.r();
        case Square q -> q.a() * q.a();
    };
}

// 算周长
double perimeter(Shape s) {
    return switch (s) {
        case Circle c -> 2 * Math.PI * c.r();
        case Square q -> 4 * q.a();
    };
}
```

**同样两个操作，代码量差 5 倍。** 而且**编译器强制穷尽**：加一个 `Triangle` 到 `permits` 列表里，两个 `switch` 都**立刻编译报错**。

> **这一句「编译期报错」是关键**：传统访问者如果新增了一个元素类型、忘了在某个 Visitor 里加方法，**要么编译不过（因为接口加了抽象方法），要么要写默认实现而悄悄漏掉**。`sealed` + `switch` 把这件事变成了**类型系统保证**。

#### 4.2.3 双分派 vs 类型切换：本质差别在哪

| | 访问者（双分派） | `sealed` + `switch` |
|---|---|---|
| 第一次分派 | `shape.accept(v)` → 虚方法，选中「哪个形状」 | `switch (s)` → 类型模式匹配 |
| 第二次分派 | `v.visitCircle(this)` → 虚方法，选中「哪个访问者」 | **没有**（操作就是一个函数） |
| 加一个新**操作** | 加一个 `Visitor` 实现（**不改元素**） | 加一个函数（**不改形状**） |
| 加一个新**形状** | 改 `ShapeVisitor` 接口 + **所有** Visitor 实现 | 改 `permits` + **所有** `switch` |
| 穷尽性检查 | 靠接口的抽象方法（编译器帮你） | 靠 `sealed`（编译器帮你） |
| 元素类能被外部扩展吗 | 能 | **不能**（`sealed` 关闭了扩展） |

**注意最后一行，这是 `sealed` 的真实代价**：**`sealed` 要求所有实现都在同一个模块内（同一个包，或同一个 `module-info` 的 `permits` 列表里）**。

> **所以判断很简单**：
>
> - 元素类型**你能控制、且不会外部扩展** → 用 `sealed` + `switch`（简单得多）
> - 元素类型**在别人的 jar 里**（比如 `javax.lang.model.element.Element`）→ 只能用访问者
>
> **JDK 自己就是这么选的**：`ElementVisitor`（元素类型是注解处理器 API，必须允许第三方扩展）用访问者；`java.lang.constant` 的 `ConstantDesc` 系列（JDK 内部封闭）反而更适合 `sealed`。

#### 4.2.4 一个反例：本项目同样用了 `sealed`，但**不该**用 `switch`

`apigateway` 的熔断器：

```java
sealed interface BreakerState permits Closed, Open, HalfOpen {
    GatewayResponse tryPass(GatewayRequest request, UpstreamInstance instance, long now);
    void onSuccess(long now);
    void onFailure(long now);
}

final class Closed implements BreakerState { ... }
final class Open implements BreakerState { ... }
final class HalfOpen implements BreakerState { ... }
```

**同样是 `sealed`，这里却在用「多态」而不是「`switch`」。为什么？**

因为**每个状态的行为都很复杂，而且状态之间的转换和计时强相关**：

| 状态 | `tryPass` | `onSuccess` | `onFailure` |
|---|---|---|---|
| `Closed` | 永远放行 | 清零连续失败 | 失败数 +1，达阈值 → `Open` |
| `Open` | 时间没到 → 拒绝；到了 → 转 `HalfOpen` 再试 | — | — |
| `HalfOpen` | 有限放行（本项目的实现有缺陷，见 §8.3 #1） | → `Closed` | → `Open`，重新计时 |

**如果用 `switch` 版本**：

```java
GatewayResponse tryPass(BreakerState state, ...) {
    return switch (state) {
        case Closed c -> GatewayResponse.ok(...);
        case Open o -> {
            if (now < o.openUntil()) yield GatewayResponse.reject(503, "熔断中");
            // 这里要「把状态改成 HalfOpen」，但 switch 里改不了外部状态！
            yield ...;
        }
        case HalfOpen h -> ...;
    };
}
```

**问题立刻出现：`switch` 的表达式没法自然地「修改状态」**。你得让 `tryPass` 返回「新状态 + 响应」两个东西，然后每个调用点都负责写回——**这就把「状态转换」这个内聚逻辑打散了**。

> **判断口诀（这一条比整章都值钱）**：
>
> - **行为差异小、逻辑短、纯粹「算一个值」** → `switch` 更好（`Shape` 的面积、Saga 的状态渲染）
> - **行为差异大、需要读改写状态、带副作用** → 多态更好（熔断器、订单状态机）
>
> **`sealed` 只是「允许穷尽检查」，它不规定你用 `switch` 还是多态。** 本项目在同一个模块里两种都用了一处，正好可以做对照。

---

### 4.3 虚拟线程：吃掉了「线程池」，没吃掉「享元」

#### 4.3.1 它改变了「什么是昂贵的」

```java
// 传统：线程贵（1MB 栈 + 内核调度），必须池化
ExecutorService pool = Executors.newFixedThreadPool(200);

// 虚拟线程：线程便宜（几 KB，用户态调度），不必池化
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

| 资源 | JDK 21 之前 | 虚拟线程之后 |
|---|---|---|
| **线程** | 昂贵 → 必须池化 | **便宜** → 不必池化，一个任务一个 |
| **连接**（DB / HTTP） | 昂贵 → 必须池化 | **仍然昂贵** → 仍然必须池化 |
| **内存缓冲区** | 昂贵 → 必须复用 | **仍然昂贵** → 仍然必须复用 |
| **文件句柄** | 昂贵 → 必须限流 | **仍然昂贵** → 仍然必须限流 |

> **虚拟线程只改变了「线程」这一项的成本，其他都没变。**
>
> 这是最容易误推的地方：**「线程池过时了」不等于「池化过时了」**。

#### 4.3.2 对本项目的具体影响

`apigateway` 里有限流和最少连接两种机制：

| 机制 | 它限制的是什么 | 虚拟线程来了还必要吗 |
|---|---|---|
| `RateLimitFilter`（每 IP 每路径 N 次/窗口） | **上游调用次数**（保护上游） | **必要**（和保护本地线程无关） |
| `LoadBalancer.LeastConnections`（`inFlight` 最小） | **上游实例的并发** | **必要**（上游的容量没变） |
| `MetricsHandler`（计时） | 无关 | 无关 |
| （隐含的）「一个请求一个线程」 | 本地线程资源 | **不需要了**（虚拟线程直接开） |

**结论：`apigateway` 里唯一「因为线程贵才存在」的东西是「一个请求一个线程」这个假设，而它没有被写进代码。** 所以**这个项目迁移到虚拟线程几乎零改动**——因为它的限流限的是上游，不是线程。

> **这是「限流到底限什么」的一个值得反复问的问题**：
> 如果你限的是**自己的线程**，虚拟线程之后可以放开；
> 如果你限的是**下游的容量**，虚拟线程之后一点都不能放。
>
> **本项目的 `inFlight` 属于后者。** 这也是为什么 §3.6 说「享元共享的必须是配额」——**配额的上限由下游决定，不由你的运行时决定。**

#### 4.3.3 JDK 21 上虚拟线程的三个真实陷阱

| 陷阱 | 症状 | 根因 |
|---|---|---|
| `synchronized` 块阻塞 | 吞吐量突然掉到个位数，`jcmd` 显示载体线程全被占 | **JDK 21 上 `synchronized` 会 pin 住载体线程**，改用 `ReentrantLock` |
| `ThreadLocal` 滥用 | 内存暴涨 | 虚拟线程数量可能上百万，每个都带 `ThreadLocal` 副本 |
| 池化虚拟线程 | `newFixedThreadPool` 里放虚拟线程 == 白搞 | 虚拟线程的收益来自「不池化 + 每个任务一个」 |

> **第二条对本项目有直接意义**：`apigateway` 的 `CallContext` 是**显式传递**的（沿链传参），没有用 `ThreadLocal`。**这个设计在虚拟线程时代是加分项**——§2.1.4 说的「`ThreadLocal` 泄漏」在这里根本不存在。

#### 4.3.4 虚拟线程吃掉的是哪个模式

严格说，它没有「吃掉」模式，它**改变了模式的默认参数**：

| 模式 | 变化 |
|---|---|
| 享元（线程池） | 「线程池」这个特定享元的收益归零，但**其他享元（连接池、缓冲区）收益不变** |
| 中介者（`ExecutorService`） | 仍然是中介者，只是 `newVirtualThreadPerTaskExecutor()` 取代了固定池 |
| 命令（`Runnable`） | 不变。虚拟线程执行的正是一个个命令 |
| 观察者 | 不变。但「异步通知」的成本降低了 |

> **记住这句话**：
> **虚拟线程降低的是「并发」的成本，不是「资源」的成本。**
> 所有限制「资源」的模式（享元、限流、熔断、池化）**一个都不会消失**。

---

### 4.4 `instanceof` 模式匹配：吃掉的是「类型转换样板」

```java
// JDK 16 之前
if (node instanceof BinaryNode) {
    BinaryNode binary = (BinaryNode) node;       // ← 重复一次类型名
    return eval(binary.left()) + eval(binary.right());
}

// JDK 16 之后
if (node instanceof BinaryNode binary) {         // ← 一次搞定
    return eval(binary.left()) + eval(binary.right());
}
```

**省掉的是「转换 + 变量声明」这一行。** 它对应的其实是**访问者模式的「穷人版」**——很多项目里的「访问者」实际上就是一堆 `instanceof`：

```java
// 这是很多真实项目里的「伪访问者」
for (Node n : nodes) {
    if (n instanceof Paragraph p) { renderParagraph(p); }
    else if (n instanceof Table t) { renderTable(t); }
    else if (n instanceof Image i) { renderImage(i); }
}
```

**`instanceof` 模式匹配让这种写法稍微好看一点，但它没有解决核心问题**：

| 问题 | `instanceof` 链 | `sealed` + `switch` |
|---|---|---|
| 新增一个类型时，编译器会提醒你吗 | **不会**（悄悄走 `else`） | **会**（编译报错） |
| 能表达「只有这几种」吗 | 不能 | 能（`sealed`） |
| 代码审查能看出漏了吗 | 很难 | 一眼看出 |

> **所以 `instanceof` 模式匹配不是「访问者的替代品」，它是「让烂代码不那么难看」的语法糖。**
> **真正替代访问者的是 `sealed` + `switch`（§4.2），而不是 `instanceof`。**

**一个真实的判断标准**：如果你的 `instanceof` 链有 **超过 3 个分支**、并且**未来可能加分支**，就该考虑 `sealed` + `switch`。3 个以内、且确定不会变，`instanceof` 完全没问题。

---

### 4.5 lambda：吃掉了「策略」的写法，没吃掉「策略」的选择

#### 4.5.1 同一个 `Comparator`，三个时代

```java
// JDK 8 之前：匿名内部类（6 行）
list.sort(new Comparator<Order>() {
    @Override
    public int compare(Order a, Order b) {
        return Integer.compare(a.amount(), b.amount());
    }
});

// JDK 8：lambda（1 行）
list.sort((a, b) -> Integer.compare(a.amount(), b.amount()));

// JDK 8+：方法引用 + 工厂方法（更短，且声明了意图）
list.sort(Comparator.comparingInt(Order::amount));
```

**注意第三行**：`Comparator.comparingInt` 返回的是一个 **`Comparator` 实现**。也就是说——**策略对象还在，只是不再是「你写的类」，而是「库造的对象」**。

> **这是这一节最重要的观察**：
> **lambda 没有消灭策略模式，它消灭的是「策略的实现类」。**
> `Comparator` 仍然是策略接口，`list.sort` 仍然是「接收策略的上下文」，**结构一点没变**。

#### 4.5.2 lambda 吃掉了哪些模式的「实现类」

| 模式 | 被 lambda 简化成 | 但什么还在 |
|---|---|---|
| **策略** | `Comparator.comparing(...)` / `(a, b) -> ...` | 接口、上下文、选择时机 |
| **命令** | `Runnable` / `Callable` 的 lambda | 排队、撤销、日志（GoF 的后半个意图） |
| **观察者** | `list.forEach(x -> ...)` / `addListener(e -> ...)` | 注册、注销、通知顺序、异常隔离 |
| **模板方法** | `JdbcTemplate.execute(stmt -> ...)` | **固定流程本身**（这才是模板方法的核心） |
| **工厂方法** | `Supplier<T>` / `Function<A, B>` | 多产品族、需要配置的创建 |
| **责任链** | **吃不动** | 链的组装、顺序、短路 —— lambda 表达不了 |

#### 4.5.3 「继承式模板方法」→「回调式模板方法」，这是真正的进步

**本项目 [`templatemethod`](../src/com/patterns/behavioral/templatemethod/README.md) 用的是继承式**：

```java
public abstract class OrderProcessTemplate {
    public final void process(Order order) {          // final：不许改流程
        validate(order);
        pay(order);
        ship(order);
        notifyUser(order);
    }
    protected abstract void pay(Order order);         // 子类填
    protected abstract void ship(Order order);
}

class PhysicalProductOrder extends OrderProcessTemplate { ... }
```

**这是标准 GoF 写法，但它有一个代价**：**`PhysicalProductOrder` 只能继承这一个类**。如果它还想复用另一套流程，做不到（Java 单继承）。

**回调式版本（现代写法）**：

```java
public final class OrderProcess {
    public void process(Order order, Payment payment, Shipping shipping) {
        validate(order);
        payment.pay(order);          // ← 传入的策略
        shipping.ship(order);
        notifyUser(order);
    }
}

// 调用现场
orderProcess.process(order,
        o -> alipay.charge(o),       // lambda
        o -> sfExpress.send(o));
```

| | 继承式 | 回调式 |
|---|---|---|
| 复用方式 | 继承（只能用一次） | 组合（可以任意组合） |
| 流程可变吗 | `final` 保护 | `final` 保护（同样不可变） |
| 测试 | 要造子类 | 传 lambda |
| 状态 | 可以放字段 | 无状态（或用闭包） |
| 典型 | `AbstractList`（JDK 早期） | `JdbcTemplate`（JDK 8 时代） |

> **注意一个关键事实：`AbstractList` 至今仍然是继承式的。** 为什么？因为它要**提供 20 多个默认方法**，这个体量用 lambda 参数化是不现实的。
>
> **判断标准：需要「填一个洞」还是「填一组洞」？**
> **填一个洞** → 回调/lambda（`JdbcTemplate`）
> **填一组洞 + 需要共享状态** → 继承（`AbstractList`）

#### 4.5.4 lambda 吃不动的东西

**lambda 有三个硬限制，正好对应三类模式吃不动**：

| 限制 | 吃不动的模式 |
|---|---|
| **一个 lambda 只能实现一个方法** | 责任链（要 `next()`）、中介者（要注册 + 转发）、外观（要聚合多个） |
| **lambda 没有身份，不能注册/注销** | 观察者（要 `removeListener`，必须能比较引用） |
| **lambda 捕获的变量必须 effectively final** | 状态（要改字段）、命令的 undo（要改历史） |

> **所以「观察者被 lambda 吃掉」这句话只对了一半**：
> `addListener(e -> ...)` 确实更短，但**如果你想 `removeListener`，就必须保留那个 lambda 的引用**：
>
> ```java
> Consumer<Event> listener = e -> handle(e);
> bus.addListener(listener);
> bus.removeListener(listener);      // ← 必须存着这个变量
> ```
>
> **这就解释了为什么很多框架（Spring、Guava）的 `addListener` 返回一个 `Registration` 对象**——**用一个对象代表「这个 lambda 的身份」**。而这个 `Registration` 本身，就是一个命令模式的对象。

---

### 4.6 一个都没被吃掉的六个

**这六个模式，语法糖完全无能为力**，因为它们解决的是「**对象之间的关系**」，不是「**代码的啰嗦**」：

| 模式 | 为什么吃不掉 |
|---|---|
| **装饰器** | 核心是「同类型 + 持有 + 可叠加」。lambda 无法表达「包装并保持类型」 |
| **代理** | 核心是「替换身份」。lambda 只是实现手段（`InvocationHandler` 就是函数式接口） |
| **组合** | 核心是「整体与部分同接口」。这是类型设计，不是语法 |
| **桥接** | 核心是「两个维度独立变化」。这是架构决策 |
| **抽象工厂** | 核心是「产品族一致性约束」。编译器帮不了你 |
| **责任链** | 核心是「顺序 + 短路 + 传递」。lambda 没有「下一个」的概念 |
| **中介者** | 核心是「多对多改成一对多」。这是拓扑结构，不是语法 |

**半吃不掉的**：

| 模式 | 吃了什么 | 剩什么 |
|---|---|---|
| 策略 | 实现类的写法 | **选择时机**（谁在什么时候选哪个） |
| 命令 | `Runnable` 的写法 | **撤销、日志、排队**（GoF 的后半截） |
| 模板方法 | 「填一个洞」的场景 | **「填一组洞 + 共享状态」** |
| 访问者 | 一部分被 `sealed` + `switch` | **元素可被外部扩展**的场景 |
| 备忘录 | 不可变对象的快照 | **可变对象的深拷贝** |
| 单例 | `enum` 写法 | **多实例、可注入的「近似单例」**（Spring 的 singleton scope） |
| 工厂方法 | 简单创建的写法 | **需要配置、需要池、需要缓存的创建** |
| 建造者 | `record` + 少量字段 | **多可选字段 + 可演进** |
| 原型 | `record` 的 `with` 拷贝 | **深拷贝、循环引用、性能优化** |
| 迭代器 | `for-each` 语法 | **自定义遍历顺序、并发修改检测** |

---

### 4.7 23 个模式 × 语言特性：一张总表

| # | 模式 | 被什么吸收 | 程度 | 还必须手写什么 |
|---|---|---|---|---|
| 1 | 工厂方法 | `Supplier` / 静态工厂方法 | 中 | 多态创建、需要配置的创建 |
| 2 | 抽象工厂 | 无 | **0** | 产品族一致性 |
| 3 | 建造者 | `record`（部分） | 低 | 多可选字段、可演进 |
| 4 | 原型 | `record` 的拷贝构造 | 中 | 深拷贝、循环引用 |
| 5 | 单例 | `enum` | 中 | 容器管理的单例（Spring scope） |
| 6 | 适配器 | 无 | **0** | 接口不兼容的桥接 |
| 7 | 桥接 | 无 | **0** | 两维度独立变化 |
| 8 | 组合 | 无 | **0** | 整体-部分同构 |
| 9 | 装饰器 | 无 | **0** | 同类型包装、可叠加 |
| 10 | 外观 | 无 | **0** | 聚合多个子系统 |
| 11 | 享元 | 无 | **0** | 共享内在状态（**虚拟线程没动摇它**） |
| 12 | 代理 | `InvocationHandler`（lambda） | 低 | 控制访问、身份替换 |
| 13 | 责任链 | 无 | **0** | 顺序、短路、传递 |
| 14 | 命令 | `Runnable` / `Callable` | 高 | undo、日志、事务 |
| 15 | 解释器 | `Pattern`（库） | 高 | 自定义 DSL |
| 16 | 迭代器 | `Iterable` + `for-each` | 高 | 自定义遍历 |
| 17 | 中介者 | 无 | **0** | 多对多解耦 |
| 18 | 备忘录 | `record`（不可变时） | 中 | 可变对象的深拷贝 |
| 19 | 观察者 | `Flow` / 函数式接口 | 中 | 注销、背压、异常隔离 |
| 20 | 状态 | `sealed` + `switch`（部分） | 中 | 行为复杂、需读改状态时仍用多态 |
| 21 | 策略 | 函数式接口 | **高** | **选择时机**（谁选、何时选） |
| 22 | 模板方法 | 回调（填一个洞时） | 中 | 填一组洞 + 共享状态 |
| 23 | 访问者 | `sealed` + `switch` | **高**（封闭类型时） | 元素可外部扩展时仍需访问者 |

**统计一下**：

| 吸收程度 | 数量 | 是哪些 |
|---|---|---|
| **0（完全没变）** | **9** | 抽象工厂、适配器、桥接、组合、装饰器、外观、享元、责任链、中介者 |
| 低 | 2 | 建造者、代理 |
| 中 | 7 | 工厂方法、原型、单例、备忘录、观察者、状态、模板方法 |
| **高** | **5** | 命令、解释器、迭代器、策略、访问者 |
| 合计 | 23 | —— |

> **也就是说：23 个模式里，有 9 个（近 40%）在 JDK 21 下「一行代码都没省」**。
>
> 而这 9 个恰好是**结构型为主**——**它们描述的是「谁持有谁」，而这不是语法能表达的东西。**

---

### 4.8 一条判断口诀，收束这一章

下次你在犹豫「这个模式还要不要写」时，问自己一个问题：

> **「如果我用 lambda / record / sealed 写这个模式，还剩下几个类型？」**

| 剩下的类型数 | 判断 |
|---|---|
| **1 个接口 + 几个 lambda** | **已经被语言吸收了**。直接用 lambda，不要造类 |
| **2~3 个类，且都是数据载体** | **半吸收**。看情况，优先用 `record` |
| **≥3 个类，且有明确的持有/顺序/替换关系** | **没被吸收**。老老实实写模式 |

**举三个例子验证这条口诀**：

| 场景 | 剩下的类型 | 判断 |
|---|---|---|
| 「按金额排序」 | `Comparator` 接口 + 1 个 lambda | 已吸收 → `Comparator.comparingInt(Order::amount)` |
| 「下单后发短信/发邮件」 | `Notifier` 接口 + 2 个实现 + 1 个持有点 | 半吸收 → 如果是无状态、只有 2 个，用 `Map<Channel, Consumer<Order>>` 就够；如果要注册注销、要异常隔离，还是写类 |
| 「请求要过 6 道关卡，任一关可拒」 | `Filter` 接口 + 6 个实现 + 1 个链 + 1 个上下文 | **没吸收** → 老老实实写责任链 |

> **最后一句，也是整章的核心**：
>
> **语言变强了，模式没变弱——变弱的是「为了模式而模式」的那部分代码。**
> **真正留下来的，是那些「非它不可」的结构。**


---

## 5. 现代误用清单：十种「学了模式之后反而写坏」的代码

> 这一章是全文唯一一章**几乎全是反面例子**。
> 理由很简单：**前四章告诉你去哪儿找模式，这一章告诉你哪儿别用。**
> 每一条都给出「症状代码 → 为什么错 → 怎么改 → 一条判断」，**判断那一条可以直接背下来在 code review 时用。**

### 5.1 症状速查表

| # | 误用 | 一句话症状 | 判断（背这一列就够了） |
|---|---|---|---|
| 1 | 策略只有一个实现 | `interface` 只有一个 `impl`，且从没换过 | **一个实现不是策略，是抽象** |
| 2 | 工厂只造一个产品 | `XxxFactory.create()` 里就一行 `new Xxx()` | **一行的工厂 = 一行的累赘** |
| 3 | 单例带可变状态 | 单例里有 `set` / `add` / `clear` | **有状态单例 = 全局变量** |
| 4 | 责任链全放行 | 所有 `filter` 都 `return true` / `next()` | **没有拒绝能力的链不是链** |
| 5 | 观察者当 MQ | 用进程内事件实现「最终一致」 | **进程内通知没有持久化，重启就丢** |
| 6 | 布尔参数代替装饰器 | `new Service(true, false, true)` | **三个 boolean 就是 2³ 个类没拆开** |
| 7 | 接口爆炸 | 每个类配一个「只有它能实现」的接口 | **接口是给别人实现的，不是给自己贴标签** |
| 8 | 层次过深 | `Controller→Service→ServiceImpl→Manager→Handler→Helper` | **超过 4 层一定是职责没划清** |
| 9 | DTO 搬运 | `Entity→VO→DTO→BO` 四份字段一样的类 | **字段一样就不是「不同的模型」** |
| 10 | 命名污染 | `XxxManager` / `XxxHelper` / `XxxUtil` 里什么都干 | **说不出职责的名字，就是没有职责** |

---

### 5.2 逐条拆开

#### 误用 ①：策略只有一个实现

```java
// 症状：接口 + 唯一实现，注释写着「预留扩展」
public interface DiscountStrategy {
    BigDecimal calculate(BigDecimal amount);
}

public class DefaultDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculate(BigDecimal amount) {
        return amount;                      // ← 什么都没做
    }
}

// 使用处
public class PriceCalculator {
    private final DiscountStrategy strategy = new DefaultDiscountStrategy();
    public BigDecimal calculate(BigDecimal amount) {
        return strategy.calculate(amount);      // ← 绕了一圈
    }
}
```

**为什么错**：这不是策略模式，这是**「为了将来可能的需求，现在就加一层」**。而「将来」往往不来——更糟的是，**它来了之后，你会因为「已经有策略模式了」而不好意思删掉这个空实现**。

**怎么改**：删掉接口和实现，直接返回 `amount`。等第二个策略**真的出现时**再抽（那时改动只要 5 分钟）。

```java
public class PriceCalculator {
    public BigDecimal calculate(BigDecimal amount) {
        return amount;
    }
}
```

> **判断：一个实现不是策略，是抽象。** 抽象只有在**有两个以上真实实现**时才有价值。
> **「预留扩展」不构成实现**——预留的东西通常永远不会被用到，但它每天都在增加阅读成本。

#### 误用 ②：工厂只造一个产品

```java
// 症状
public class OrderServiceFactory {
    public static OrderService create() {
        return new OrderServiceImpl();       // ← 就这一行
    }
}

// 调用处
OrderService service = OrderServiceFactory.create();
```

**为什么错**：工厂的价值是「**把「造哪个」这个决定挪到一个地方**」。如果永远只造同一个，这个决定根本不存在。

**但有一个例外，必须说清楚**：

```java
public class OrderServiceFactory {
    public static OrderService create() {
        return new OrderServiceImpl(
            DataSourceHolder.get(),          // ← 依赖了静态获取的全局对象
            RetryPolicy.defaults()
        );
    }
}
```

**这个工厂是合理的**，因为它的价值不在「造哪个」，而在**「把三个 new 参数藏起来」**。判断标准不是「有几个产品」，而是：

> **判断：工厂削减的「构造复杂度」是否大于它增加的「一层间接」？**
> 五行的构造 + 一处调用 → 不要工厂。
> 多处调用 + 每次都要填同样五个参数 → 要工厂（或者依赖注入）。

#### 误用 ③：单例带可变状态

```java
// 症状
public class RequestContextHolder {
    private static final RequestContextHolder INSTANCE = new RequestContextHolder();
    private String currentUser;                  // ← 可变！
    private final Map<String, Object> attrs = new HashMap<>();

    public static RequestContextHolder getInstance() { return INSTANCE; }
    public void setCurrentUser(String u) { this.currentUser = u; }
    public String getCurrentUser() { return currentUser; }
}
```

**为什么错**：这就是**披着单例外衣的全局变量**。两个线程同时请求时，`currentUser` 会互相覆盖。

**这是本项目刻意避开的一个坑**：`apigateway` 的 `CallContext` 是**显式沿链传递**的，不是单例、不是 `ThreadLocal`。

```java
// apigateway 的做法：每个请求一个对象，显式传
final class CallContext {
    private final GatewayRequest request;
    private final List<String> trace;
    private UpstreamInstance instance;
    // ……没有 static，没有单例
}

// 沿链传递
public interface GatewayFilter {
    GatewayResponse handle(CallContext context, FilterChain chain);
}
```

**怎么改（如果必须共享）**：

| 场景 | 正确做法 |
|---|---|
| 请求范围内的数据 | **显式传参**（`CallContext`）或框架的 request scope |
| 每个线程独立 | `ThreadLocal`（**但虚拟线程下要谨慎，见 §4.3.3**） |
| 真正的全局配置 | **不可变**单例（`record` + `final`） |

> **判断：有状态单例 = 全局变量。** 单例只配得上「**不可变**」或者「**自己管好并发**」的对象。
> `Runtime` 是前者（只读），`MetricsCollector` 是后者（内部 `AtomicInteger` / `ConcurrentHashMap`）。

#### 误用 ④：责任链全放行

```java
// 症状：三个 filter，没有一个能拒绝
public class LogFilter implements GatewayFilter {
    public GatewayResponse handle(CallContext ctx, FilterChain chain) {
        log.info("收到请求");
        return chain.next(ctx);                // ← 永远放行
    }
}

public class HeaderFilter implements GatewayFilter {
    public GatewayResponse handle(CallContext ctx, FilterChain chain) {
        ctx.header("X-Foo", "bar");
        return chain.next(ctx);                // ← 永远放行
    }
}
```

**为什么错**：**这三层完全可以合并成一行代码**。责任链的全部价值在「**可以拒绝**」和「**可以决定顺序**」；如果没有人拒绝，顺序也不影响结果，那这个链就只是一个「好看的 for 循环」。

**这不是说「全放行」的 filter 没价值**——`MetricsHandler` 和 `CacheFilter` 就常常全放行。但它们**必须和「能拒绝的 filter」在同一个链上**，价值才成立。

> **判断：没有拒绝能力的链不是链，是「方法调用的集合」。**
> **反过来说：一个真实的网关链里，至少要有一个能返回非 2xx 的环节**——`apigateway` 的 304 行输出里有 401、429、503，这就是链在工作的证据。

#### 误用 ⑤：观察者当 MQ

```java
// 症状：进程内事件冒充「可靠通知」
@Transactional
public void createOrder(Order order) {
    orderDao.insert(order);
    eventPublisher.publishEvent(new OrderCreatedEvent(order));   // ← 进程内
}

@EventListener
public void sendEmail(OrderCreatedEvent e) {
    mailClient.send(e.order().email(), "下单成功");              // ← 失败了就丢了
}
```

**为什么错**：三个致命点——

| 问题 | 进程内观察者 | 消息队列 |
|---|---|---|
| 应用重启时未处理的事件 | **丢失** | 保留（broker 持久化） |
| 消费失败 | 抛异常回传给发布者（默认同步） | 重投 / 进死信队列 |
| 多个消费者 | 每个 listener 一份（广播） | 同一 group 内负载均衡 |
| 跨进程 | 做不到 | 天然支持 |

**怎么改**：如果「通知失败可以接受」→ 观察者没问题，但要**显式异步 + 兜里兜底日志**。如果「通知必须送到」→ 用 Outbox（§3.5）或 MQ。

> **判断：进程内通知没有持久化，重启就丢。**
> **「最终一致」这个词的前提是「有持久化的中间状态」，而进程内事件恰恰没有。**

#### 误用 ⑥：布尔参数代替装饰器

```java
// 症状：一个类，三个开关
public class OrderService {
    private final boolean enableCache;
    private final boolean enableRetry;
    private final boolean enableMetrics;

    public OrderService(boolean enableCache, boolean enableRetry, boolean enableMetrics) { ... }

    public Order get(String id) {
        if (enableMetrics) { long t = System.nanoTime(); /* ... */ }
        if (enableCache) {
            Order cached = cache.get(id);
            if (cached != null) { if (enableMetrics) { /* 记账 */ } return cached; }
        }
        Order result;
        if (enableRetry) {
            result = doGetWithRetry(id);
        } else {
            result = doGet(id);
        }
        if (enableCache) { cache.put(id, result); }
        if (enableMetrics) { /* 记账 */ }
        return result;
    }
}
```

**为什么错**：`if` 的组合数是 **2³ = 8 种状态**，而每一种都可能是 bug。想加第四个开关就是 16 种。

**正确的写法**（就是本项目的做法）：

```java
// 每一层只做一件事，组合在外层决定
Handler service = new ServiceHandler(instance);
Handler cache   = new CacheHandler(service, cacheStore);
Handler retry   = new RetryHandler(cache, maxAttempts);
Handler metrics = new MetricsHandler(retry, collector);

// 想要「有缓存但不重试」？换一行组装就行
Handler noRetry = new MetricsHandler(new CacheHandler(service, cacheStore), collector);
```

| | 布尔参数 | 装饰器 |
|---|---|---|
| 组合数 | 2ⁿ 个 `if` 分支 | n 个类，任意组合 |
| 加第 4 个能力 | 改构造器 → **所有调用点编译错** | 加一个类 |
| 能单独测试某一层吗 | 不能 | 能（单独 new 一层） |
| 执行顺序由谁定 | 写死在 `if` 的顺序里 | 组装时决定 |

> **判断：三个 boolean 就是 2³ 个类没拆开。**
> **这条判断可以直接用**：你在 code review 里看到构造器有 ≥3 个 `boolean` 参数，几乎一定是该拆装饰器（或该拆成几个方法）了。

#### 误用 ⑦：接口爆炸

```java
// 症状：每个类都配一个接口，且接口只有这一个实现
public interface OrderRepository { Order findById(String id); }
public class OrderRepositoryImpl implements OrderRepository { ... }

public interface OrderMapper { OrderDO toDO(Order o); }
public class OrderMapperImpl implements OrderMapper { ... }

public interface OrderValidator { void validate(Order o); }
public class OrderValidatorImpl implements OrderValidator { ... }
```

**为什么错**：**接口是「给别人实现的契约」**。如果它永远只有一个实现，那它只是「多了一层跳转」。

**什么时候接口是必要的**（这三个理由之一，且只有一个就够）：

| 理由 | 例子 |
|---|---|
| **有多个实现，且运行时选** | `LoadBalancer` → `RoundRobin` / `LeastConnections` |
| **需要被 mock（测试隔离）** | 外部依赖（DB、HTTP、MQ） |
| **是模块间的公开契约** | `java.sql.Driver`（厂商实现） |

**如果只是「为了单元测试」**，现代 Mock 框架（Mockito 的 `mock(Class)`）可以直接 mock 具体类，**大部分情况下不需要为测试造接口**。

> **判断：接口是给别人实现的，不是给自己贴标签。**
> **一个永远只有一个实现的接口，等于把 `class` 换成 `interface` + `class`，只增加了一个文件。**

#### 误用 ⑧：层次过深

```text
OrderController
  └─ OrderService（接口）
       └─ OrderServiceImpl
            └─ OrderManager
                 └─ OrderHandler
                      └─ OrderHelper
                           └─ OrderUtil
```

**为什么错**：**每一层都要做一次「参数搬运 + 返回值搬运」**，而且**每一层都可以「稍微处理一下」**，于是「这个字段到底在哪一层被改的」变成了一个考古问题。

**层数多少合适**？我的经验值（**观点，不是定理**）：

| 项目规模 | Controller → ? → DAO | 说明 |
|---|---|---|
| 单体、< 20 个接口 | **1 层**（Controller → Repository） | 加 Service 层纯属仪式 |
| 单体、20~200 个接口 | **2 层**（Controller → Service → Repository） | 有业务逻辑需要隔离 |
| 微服务、有跨服务编排 | **3 层**（Controller → AppService → DomainService → Repository） | 编排与领域逻辑必须分开 |
| 任何规模 | **超过 4 层** | **一定要重新划职责** |

> **判断：超过 4 层一定是职责没划清。**
> **一个更狠的验证方法**：随便挑一个字段，从 Controller 追到 DAO，数它被「赋值」了几次。**超过 2 次，就是层次过多。**

#### 误用 ⑨：DTO 搬运

```java
// 症状：四份字段完全一样的类
public class OrderDO { String id; BigDecimal amount; int status; }      // 数据库
public class OrderBO { String id; BigDecimal amount; int status; }      // 业务
public class OrderVO { String id; BigDecimal amount; int status; }      // 视图
public class OrderDTO { String id; BigDecimal amount; int status; }     // 传输

// 以及四份转换器
OrderBO bo = OrderConverter.doToBo(do_);
OrderVO vo = OrderConverter.boToVo(bo);
OrderDTO dto = OrderConverter.voToDto(vo);
```

**为什么错**：**字段一样就不是「不同的模型」**。四份一样的类，只带来四份 `get/set` 和三个转换器，**没有任何一个字节的信息增量**。

**什么时候分层模型是必要的**：

| 理由 | 例子 |
|---|---|
| **字段真的不同** | 数据库有 `deleted_at`，API 不能暴露 |
| **生命周期不同** | `OrderBO` 是不可变的，`OrderDO` 是 ORM 的可变实体 |
| **依赖方向需要隔离** | 领域层不能依赖数据库注解 |

**如果三个理由都不成立**，就**用一个类**。真需要隐藏字段时，用 `record` + 紧凑构造器，或者注解（`@JsonIgnore`）。

> **判断：字段一样就不是「不同的模型」。**
> **一个可执行的检查**：把两个 DTO 的字段列表打出来 diff 一下，如果 diff 为空，删掉一个。

#### 误用 ⑩：命名污染

```java
// 症状：名字里带 Manager / Helper / Util / Handler，但什么都干
public class OrderManager {
    public Order get(String id) { ... }            // 查询
    public void sendEmail(Order o) { ... }         // 发邮件
    public BigDecimal calcTax(Order o) { ... }     // 算税
    public void exportExcel(List<Order> list) { }  // 导 Excel
    public void syncToEs(Order o) { ... }          // 同步索引
}
```

**为什么错**：**`Manager` 这个词没有告诉任何人「这个类负责什么」**。它的真实含义是「我不知道该把它放哪儿，先扔这儿」。

**怎么改**：按**变更原因**拆（这是 §8.4 讲的那条规则的直接应用）：

```java
public class OrderQueryService { Order get(String id); }         // 变更原因：查询需求
public class OrderNotifier { void sendEmail(Order o); }          // 变更原因：通知渠道
public class TaxCalculator { BigDecimal calc(Order o); }         // 变更原因：税率政策
public class OrderExporter { void toExcel(List<Order> list); }   // 变更原因：导出格式
public class OrderIndexer { void sync(Order o); }                // 变更原因：搜索索引结构
```

> **判断：说不出职责的名字，就是没有职责。**
> **「Manager」「Helper」「Util」「Common」「Base」这五个词，在 code review 里应该被当成红旗**——它们不是名字，是「暂时放一下」的借口。

---

### 5.3 十条判断，做成一张卡片

> 下面这十条，**是我认为最值得贴在工位上的**。它们不是「模式知识」，是「止损线」。

```text
① 一个实现不是策略，是抽象              → 等第二个实现出现再抽
② 一行的工厂 = 一行的累赘              → 看它削减的复杂度
③ 有状态单例 = 全局变量                → 单例只配不可变或自带并发
④ 没有拒绝能力的链不是链                → 链的价值在「可拒绝 + 可排序」
⑤ 进程内通知没有持久化，重启就丢        → 「最终一致」要持久化
⑥ 三个 boolean 就是 2³ 个类没拆开      → 装饰器 / 拆方法
⑦ 接口是给别人实现的，不是给自己贴标签   → 多实现 / mock / 公开契约
⑧ 超过 4 层一定是职责没划清            → 数字段被赋值几次
⑨ 字段一样就不是「不同的模型」          → diff 字段列表
⑩ 说不出职责的名字，就是没有职责         → Manager/Helper/Util 是红旗
```

**最后补一条元规则**：

> **如果你的「模式」让代码变长了，而且没有让任何一个「未来可能的变化」变短，那它就是过度设计。**
>
> 这句话的反面也成立：**如果它让某一种变化从「改 6 个文件」变成「加 1 个类」，那它值回票价**——即使今天看起来多写了 40 行。

---

### 5.4 一个诚实的自评：本项目自己犯了几条

**这份文档如果只批评别人，就没有可信度。** 本项目 23 个模式的实现里，我自己认为犯了这几条：

| 误用 | 本项目的情况 | 判断 |
|---|---|---|
| ① 策略只有一个实现 | `apigateway` 的 `AlertListener` 只有 1 个实现（`MetricsCollector` 是另一个，但职责不同） | **可接受**：观察者本来就允许单订阅者 |
| ② 工厂只造一个产品 | `RouteTable` 的 `Blueprint → RouteConfig` 转换只有一处调用 | **可接受**：它削减的是「校验 + 默认值 + 不可变化」，不是「造哪个」 |
| ⑦ 接口爆炸 | 23 个目录里有 6 个是「接口 + 唯一实现」（如 `GatewayFilter` 之外的 `UpstreamHandler`） | **勉强可接受**：`UpstreamHandler` 有 4 个装饰器实现 |
| ⑧ 层次过深 | `apigateway` 最深链路：`Client → Gateway → FilterChain → Metrics → Retry → Breaker → Service` **7 层** | **这是刻意的**：演示多模式协作必须堆深。**生产代码里应该至少砍掉 2 层** |

**最后一条特别值得说**：

> `apigateway` 是**教学项目**，它的目标是「让你看见每一层在做什么」，所以它故意保留了 7 层。
> **真实的生产网关（Envoy）也有 7 层以上**——但那是**配置驱动的链**，不是**代码里的 7 次 `new`**。
>
> **这就是「教学代码」和「生产代码」的差别**：教学代码把结构摊开给你看，生产代码把结构藏进配置里。


---

## 6. 总表：23 个模式 × JDK × 框架 × 现代替代 × 本项目

> **这张表是全文唯一的「一页纸」。** 如果你只想保存一页，保存这一页。

### 6.1 创建型（5 个）：对象怎么造

| 模式 | JDK 落点（可立即打开） | 框架落点（你写过的） | 现代替代 / 被吸收程度 | 本项目 |
|---|---|---|---|---|
| **工厂方法** | `Calendar.getInstance()`<br>`List.of()`<br>`Charset.forName()`<br>`Executors.newFixedThreadPool()` | Spring `FactoryBean.getObject()`<br>MyBatis `ObjectFactory` | `Supplier<T>` 吸收「一行的工厂」<br>**吸收程度：中** | [factorymethod](../src/com/patterns/creational/factorymethod/README.md) |
| **抽象工厂** | `DocumentBuilderFactory`<br>`TransformerFactory`<br>`SelectorProvider` | Spring `DataSource`（一族连接对象）<br>SLF4J 的 binding 族 | **无**。产品族一致性约束编译器管不了<br>**吸收程度：0** | [abstractfactory](../src/com/patterns/creational/abstractfactory/README.md) |
| **建造者** | `HttpRequest.Builder`<br>`Stream.Builder`<br>`DateTimeFormatterBuilder`<br>`Locale.Builder` | Spring `BeanDefinitionBuilder`<br>Jackson `JsonMapper.builder()`<br>MyBatis `MappedStatement.Builder` | `record` 吸收「少字段的值对象」<br>**吸收程度：低** | [builder](../src/com/patterns/creational/builder/README.md) |
| **原型** | `Object.clone()` + `Cloneable`<br>`ArrayList.clone()`<br>`Arrays.copyOf()` | Spring 的 `prototype` scope<br>（不是 `clone`，是「每次新建」） | `record` 的 `with` 拷贝构造<br>**吸收程度：中** | [prototype](../src/com/patterns/creational/prototype/README.md) |
| **单例** | `Runtime.getRuntime()`<br>`Desktop.getDesktop()`<br>任意 `enum` 常量 | Spring `singleton` scope（**容器级单例，不是 JVM 级**）<br>Logback 的 `LoggerContext` | `enum` 吸收「写法」，不吸收「唯一性」<br>**吸收程度：中** | [singleton](../src/com/patterns/creational/singleton/README.md) |

**三条最容易记错的**：

1. `StringBuilder` **不是**建造者（§1.1 ③）。
2. `Cloneable` 是**标记接口**，`clone()` 在 `Object` 上——这是 JDK 最著名的设计失误之一（§1.1 ④）。
3. Spring 的「单例」和作用域是**容器**，不是 JVM。两个 Spring 容器里可以有同一个 bean 的两个实例。

### 6.2 结构型（7 个）：对象怎么组合

| 模式 | JDK 落点（可立即打开） | 框架落点（你写过的） | 现代替代 / 被吸收程度 | 本项目 |
|---|---|---|---|---|
| **适配器** | `InputStreamReader`<br>`OutputStreamWriter`<br>`Arrays.asList()`<br>`Collections.list()`<br>`Executors.callable()` | Netty `ChannelHandlerAdapter`<br>`SimpleChannelInboundHandler`<br>SLF4J 的 `jul-to-slf4j` | **无**。接口不兼容是永恒问题<br>**吸收程度：0** | [adapter](../src/com/patterns/structural/adapter/README.md) |
| **桥接** | `java.sql.Driver`<br>`java.nio.channels`<br>AWT `Component` / `peer` | SLF4J `Logger` + binding<br>JDBC + 各厂商驱动 | **无**。两维度独立变化是架构决策<br>**吸收程度：0** | [bridge](../src/com/patterns/structural/bridge/README.md) |
| **组合** | `java.awt.Container`<br>`JComponent` 树<br>Jackson `JsonNode` 树 | Spring 的 `CompositeXxx`（如 `CompositeCacheManager`）<br>JUnit 5 `@Nested` | **无**。整体-部分同构是类型设计<br>**吸收程度：0** | [composite](../src/com/patterns/structural/composite/README.md) |
| **装饰器** | `FilterInputStream` 全家族<br>`BufferedInputStream`<br>`Collections.unmodifiableList()`<br>`Collections.synchronizedList()` | Servlet `Filter`<br>Resilience4j `Decorator`<br>Reactor 操作符链 | **无**。同类型 + 持有 + 可叠加<br>**吸收程度：0** | [decorator](../src/com/patterns/structural/decorator/README.md) |
| **外观** | `HttpClient`<br>`DriverManager`<br>`ScriptEngineManager`<br>`Executors` | Slf4J `LoggerFactory`<br>Spring `JdbcTemplate`<br>MyBatis `SqlSession` | **无**。聚合多个子系统<br>**吸收程度：0** | [facade](../src/com/patterns/structural/facade/README.md) |
| **享元** | `Integer.valueOf()` 缓存<br>`Long/Short/Byte/Character`<br>`Boolean.valueOf()`<br>`String.intern()` | 线程池（**虚拟线程后收益归零**）<br>Netty `PooledByteBufAllocator`<br>连接池 | **无**。虚拟线程只降低了「线程」的成本<br>**吸收程度：0** | [flyweight](../src/com/patterns/structural/flyweight/README.md) |
| **代理** | `Proxy.newProxyInstance()`<br>`UnicastRemoteObject`<br>`Method.invoke()` | Spring AOP（`@Transactional`）<br>MyBatis `Plugin`<br>RPC 的 stub | `InvocationHandler` 是函数式接口，lambda 可写<br>**吸收程度：低** | [proxy](../src/com/patterns/structural/proxy/README.md) |

**三条最容易混的**：

1. **装饰器 vs 代理**：结构一样，意图不同（加能力 vs 控访问）。`@Transactional` 是代理不是装饰器（§1.2 ⑫）。
2. **外观 vs 工厂**：外观让「复杂的东西好用」，工厂让「创建过程可替换」。`Executors` 两者兼具——**一个类可以同时是多个模式，不是错误**。
3. **享元 vs 缓存**：享元共享的是**同一个对象的同一份状态**，缓存存的是**不同 key 的结果**。`Integer` 缓存是享元，`CacheFilter` 是缓存。

### 6.3 行为型（11 个）：对象怎么协作

| 模式 | JDK 落点（可立即打开） | 框架落点（你写过的） | 现代替代 / 被吸收程度 | 本项目 |
|---|---|---|---|---|
| **责任链** | `ClassLoader.loadClass()`（双亲委派）<br>`java.util.logging.Logger` parent 链<br>`java.security.Provider` 链 | Spring `BeanPostProcessor`<br>Spring `HandlerInterceptor`<br>Netty `ChannelPipeline`<br>MyBatis `InterceptorChain`<br>Servlet `FilterChain`<br>Sentinel `SlotChain` | **无**。顺序 + 短路 + 传递<br>**吸收程度：0** | [chainofresponsibility](../src/com/patterns/behavioral/chainofresponsibility/README.md) · [apigateway](../src/com/patterns/combined/apigateway/README.md) |
| **命令** | `Runnable` / `Callable`<br>`ExecutorService.submit()`<br>`javax.swing.Action` | Spring `TransactionSynchronization`<br>Kafka 的 `ProducerRecord`（待发命令） | `Runnable` lambda 吸收「实现类」<br>**吸收程度：高** | [command](../src/com/patterns/behavioral/command/README.md) |
| **解释器** | `Pattern`<br>`MessageFormat`<br>`DateTimeFormatter` | Spring EL（`@Value("#{...}")`）<br>MyBatis 动态 SQL（`<if>`）<br>Nginx 配置语法 | 库吸收「通用小语言」<br>**吸收程度：高** | [interpreter](../src/com/patterns/behavioral/interpreter/README.md) |
| **迭代器** | `Iterator`<br>`DirectoryStream`<br>`ResultSet` | MyBatis `Cursor<T>`<br>Spring `DataBuffer` | `for-each` + `Iterable` 吸收「遍历语法」<br>**吸收程度：高** | [iterator](../src/com/patterns/behavioral/iterator/README.md) |
| **中介者** | `ExecutorService`<br>`Timer` / `ScheduledExecutorService`<br>`EventQueue`<br>`ForkJoinPool` | Kafka Broker<br>注册中心（Nacos/Consul）<br>Spring `ApplicationEventMulticaster` | **无**。多对多改一对多是拓扑变化<br>**吸收程度：0** | [mediator](../src/com/patterns/behavioral/mediator/README.md) |
| **备忘录** | `ObjectOutputStream`（序列化快照）<br>`Date.clone()`<br>Swing `UndoManager`<br>DB `savepoint` | Spring 的 `TransactionSynchronization` 保存点<br>Redis 快照（RDB） | `record` 让「不可变快照」免费<br>**吸收程度：中** | [memento](../src/com/patterns/behavioral/memento/README.md) |
| **观察者** | `java.util.concurrent.Flow`（**带背压**）<br>`PropertyChangeListener`<br>`java.util.Observer`（**JDK 9 废弃**） | Spring `ApplicationListener`<br>Kafka Consumer（经 broker）<br>JUnit 5 `TestWatcher` | `Flow` 补上背压；函数式接口吸收写法<br>**吸收程度：中** | [observer](../src/com/patterns/behavioral/observer/README.md) · [apigateway](../src/com/patterns/combined/apigateway/README.md) |
| **状态** | `Thread.State`<br>`Future.isDone()`<br>AQS 的 `state`<br>`ButtonModel` | Spring `StateMachine`（独立项目）<br>订单/支付状态机<br>Sentinel 熔断器 | `sealed` + `switch`（行为简单时）<br>**吸收程度：中** | [state](../src/com/patterns/behavioral/state/README.md) · [apigateway](../src/com/patterns/combined/apigateway/README.md) |
| **策略** | `Comparator`<br>`RejectedExecutionHandler`（4 种）<br>`ThreadFactory`<br>`TemporalAdjuster`<br>`Collector` | Spring `Converter` / `HandlerMethodArgumentResolver`<br>Spring `PlatformTransactionManager`<br>Netty `ByteBufAllocator`<br>MyBatis `TypeHandler` | 函数式接口吸收「实现类」<br>**吸收程度：高** | [strategy](../src/com/patterns/behavioral/strategy/README.md) · [apigateway](../src/com/patterns/combined/apigateway/README.md) |
| **模板方法** | `AbstractList` / `AbstractMap`<br>`InputStream.read(byte[],int,int)`<br>`OutputStream`<br>AQS | Spring `refresh()`<br>Spring `JdbcTemplate.execute()`<br>Netty `ByteToMessageDecoder`<br>MyBatis `BaseExecutor` | 回调（lambda）吸收「填一个洞」<br>**吸收程度：中** | [templatemethod](../src/com/patterns/behavioral/templatemethod/README.md) |
| **访问者** | `FileVisitor` / `SimpleFileVisitor`<br>`ElementVisitor`（注解处理）<br>`TreeVisitor`（编译器内部） | Jackson `BeanSerializerModifier`（近似）<br>ANTLR 的 AST visitor | `sealed` + `switch` 替代「封闭类型集」<br>**吸收程度：高** | [visitor](../src/com/patterns/behavioral/visitor/README.md) |

**四条最容易错的**：

1. **双亲委派的方向和标准责任链相反**：它是「先让父处理，父处理不了才自己来」（§1.3 ⑬）。
2. **`Stream` 不是迭代器模式**：没有 `hasNext()`，遍历权在库手里，是「内部迭代」（§1.3 ⑯）。
3. **JDK 没有教科书式的中介者**（`ChatRoom` 那种互相认识所有 User 的）。JDK 的中介者是「单向协调」（§1.3 ⑰）。
4. **`Observer` / `Observable` 从 JDK 9 起废弃**，因为 `Observable` 是类不是接口、无线程安全保证（§1.3 ⑲）。

### 6.4 反向索引：按「你现在要干什么」找入口

| 你的处境 | 从哪一节开始 |
|---|---|
| 「我怀疑这个模式没用，想验证一下」 | §1，挑你正在用的那个 JDK 类，打开源码看一眼 |
| 「我在用 Spring，但不知道它为什么这么设计」 | §2.1（尤其是 `BeanPostProcessor` 的四阶段生命周期表） |
| 「我写 Netty，`ctx.write` 和 `channel().write` 有什么区别」 | §2.2（方向规则表 + 那条真实的结构图） |
| 「MyBatis 插件顺序为什么重要」 | §2.3（代理套链的两层结构） |
| 「我要上服务网格，值不值得」 | §3.2（尤其是 §3.2.3 的「什么时候不该上」） |
| 「重试/熔断的顺序怎么定」 | §3.3（四条「顺序反了会怎样」的表） |
| 「跨服务事务怎么办」 | §3.4（Saga 与「补偿不是回滚」） |
| 「要不要上 MQ」 | §3.5（观察者 vs 发布/订阅的对照表） |
| 「record / sealed 之后还要不要写模式」 | §4（尤其是 §4.7 的 23 模式总表与 §4.8 的判断口诀） |
| 「我的代码是不是过度设计」 | §5.3 的十条判断卡片 |
| 「我要 review 别人的代码」 | §5.3（十条止损线，可以直接背） |
| 「我想看一个真实的、多模式协作的案例」 | [apigateway 的 5092 行 README](../src/com/patterns/combined/apigateway/README.md) |

---

## 7. 自测：18 题，答不上来就回去重读

> **规则**：先自己想 30 秒，再点开答案。
> **如果一题你只能答出「是什么」而答不出「为什么」，算没答对。**

### 7.1 A 组：JDK 落点（6 题）

**A1.** `List.of()` 返回的实现类为什么你 `new` 不出来？这体现了哪个模式的什么收益？

<details><summary>答案要点</summary>

返回的是包私有的 `ImmutableCollections.ListN` 等内部类。**工厂方法的核心收益不是「少写 new」，而是「把实现类藏起来」**——藏起来之后，JDK 可以随时换实现（比如 `List.of()` 在元素少时返回 `List12`，多时返回 `ListN`），而调用方一行都不用改。

**判断依据**：如果你能 `new` 出这个类，那它就不是「被工厂隐藏的实现」。
</details>

**A2.** `StringBuilder` 为什么不是建造者模式？给一个判断标准。

<details><summary>答案要点</summary>

两个理由：

1. **没有「产品」概念**：`toString()` 返回的是「当前缓冲区的内容」，这个值随缓冲区变化而变化，不是一份独立于构建过程的产品；
2. **没有「分步构建不同表示」的意图**：建造者的目的是「同一套步骤，产出不同表示」（`Director` 换一个 `Builder` 就产出不同产品），`StringBuilder` 只有一个输出。

**判断标准**：**`build()` 是否返回一个「与构建过程分离的、可能不可变的产品」？** `HttpRequest` 是，`StringBuilder.toString()` 不是。
</details>

**A3.** `Integer.valueOf(127) == Integer.valueOf(127)` 是 `true`，`128` 是 `false`。这属于哪个模式？为什么能共享？

<details><summary>答案要点</summary>

**享元**。能共享的前提是 **`Integer` 不可变**——所有使用者看到的是同一份值，谁也改不了它。

**这个例子最值钱的一句**：**享元共享的必须是不可变的内在状态**。如果共享的是可变对象，就必须保证「所有使用者看到同一份状态」，那就变成了「共享可变状态」，需要加锁，收益立刻被成本吃掉。

`-XX:AutoBoxCacheMax` 可以扩大上界，但**不能改变「这是享元」的事实**。
</details>

**A4.** `BufferedInputStream` 满足哪三个条件才叫装饰器？`InputStreamReader` 满足吗？

<details><summary>答案要点</summary>

装饰器的三个条件：**① 类型相同（都是 `InputStream`）② 持有被装饰者（构造器传入）③ 可以无限叠加**。

`BufferedInputStream` 三条全满足。

`InputStreamReader` **只满足前两条**：它从 `InputStream` 变成 `Reader`，**类型变了**——所以它是**适配器**，不是装饰器。

**一句话区分**：**适配器让「不能用的」能用；装饰器让「能用的」更好用。**
</details>

**A5.** `Flow` 相比 `Observer` 补上了什么？为什么这个补充很重要？

<details><summary>答案要点</summary>

补上了**背压（backpressure）**：订阅者通过 `Subscription.request(n)` 告诉发布者「我只能处理 n 个」。

**为什么重要**：没有背压，发布者全速生产、订阅者处理不过来，队列会撑爆 → `OutOfMemoryError`。**这和 §7.1 那个 OOM 事故是同一类问题：生产者没有限速。**

`Observer` / `Observable` 在 JDK 9 被废弃的三个官方理由：是类不是接口、没有线程安全保证、`setChanged` / `notifyObservers` 语义易误用。
</details>

**A6.** `ClassLoader` 的双亲委派是责任链吗？它和标准责任链有什么不同？

<details><summary>答案要点</summary>

**是责任链，但传递方向相反。**

标准责任链：**沿链找到「能处理的那个」**，找到就停。
双亲委派：**先让父加载器处理，父处理不了才自己来**——它是「从末端往起点找」。

**这个例子的价值在于**：**模式是骨架，不是模板**。同一个骨架可以有不同的传递方向，**不需要为了「像模式」而改掉正确的逻辑**。

顺带一个实用结论：`ClassLoader` 的 `parent` 不能随便改，否则会破坏「同一个类只加载一次」的保证。
</details>

### 7.2 B 组：框架扩展点（6 题）

**B1.** 为什么 `@Autowired` 的字段在构造器里是 `null`？

<details><summary>答案要点</summary>

对照 §2.1.1 的生命周期表：

```text
② 构造器
③ 属性填充   postProcessProperties   ← @Autowired 在这里
```

**构造在 ②，注入在 ③。构造器执行时注入还没发生。**

**同一个表还能解释另外两个高频问题**：

- `@Transactional` 自己调自己不生效 → 代理在 ⑥ 才生成，`this.foo()` 绕过代理；
- `@PostConstruct` 里用 `@Async` 没异步 → `@Async` 的代理在 ⑥，`@PostConstruct` 在 ④。
</details>

**B2.** `HandlerInterceptor` 的 `postHandle` 为什么是**倒序**执行的？

<details><summary>答案要点</summary>

因为它用的是**数组式责任链**（`for` 循环），不是**嵌套式**（`chain.doFilter` 递归）。

嵌套式里，「后置逻辑」自然写在 `doFilter` 调用**之后**，栈回溯时天然逆序。
数组式里没有递归，所以必须**显式倒序再循环一遍**才能得到同样的效果。

**这个对比的实用价值**：想知道「后置逻辑在异常时会不会执行」，必须先知道「这个链是数组式还是嵌套式」——嵌套式要靠 `try/finally` 保证，数组式靠 `triggerAfterCompletion` 单独处理。
</details>

**B3.** Netty 里 `ctx.write(msg)` 和 `ctx.channel().write(msg)` 有什么区别？会导致什么 bug？

<details><summary>答案要点</summary>

- `ctx.write(msg)`：从**当前 handler 的前一个出站 handler** 开始往回走；
- `ctx.channel().write(msg)`：从 **tail** 开始走完整条链。

**典型 bug**：在业务 handler 里用 `ctx.write()`，如果它的前面只有入站 handler（比如 decoder），那么出站 handler（比如 encoder）**一个都不会经过**，直接发原始 `ByteBuf` 到 head。**代码不报错，日志不打印，只有抓包能看出来。**

**根因不是 API 记不住，是脑子里没画出那条链和它的方向。**
</details>

**B4.** MyBatis 的插件为什么「后注册的在最外层」？

<details><summary>答案要点</summary>

```java
public Object pluginAll(Object target) {
    for (Interceptor interceptor : this.interceptors) {
        target = interceptor.plugin(target);       // ← 每加一个，就多包一层
    }
    return target;
}
```

`target` 的初值是真正的 `Executor`。第一次循环后 `target = proxy1(real)`，第二次后 `target = proxy2(proxy1)`。**最后包的是最外层、最先被调用。**

**实用结论**：分页插件应该在**最外层**，这样内层的日志插件打印的是**改写后的 SQL**（你能看到真正执行的 SQL）。

**这是「链的顺序」最有说服力的一次实战证明**——和 `apigateway` 里「配额必须在缓存后面」是同一个道理。
</details>

**B5.** `BeanPostProcessor` 的方法签名是 `Object -> Object`。这个签名给了它什么能力，以及什么风险？

<details><summary>答案要点</summary>

**能力**：**可以返回一个完全不同的对象，替换掉原来的 bean**。这正是 Spring AOP 能在 `postProcessAfterInitialization` 里把 `OrderService` 换成「`OrderService` 的代理」的原因——**类型没变，实现变了**。

**风险**：返回值的类型框架无法校验（编译期是 `Object`），返回错了类型直到运行时才炸，而且**堆栈会指向业务代码**（因为代理替换发生在业务代码看起来「正常」的调用链里）。

对照本项目：`GatewayFilter` 拿到的永远是同一个 `CallContext`，**不能替换**，只能改字段。这是「传上下文（稳定、易调试）vs 传对象（强大、难调试）」的取舍。
</details>

**B6.** `refresh()` 的 12 个步骤和 Nginx 的 11 个 phase，为什么是同一个模式？

<details><summary>答案要点</summary>

因为两者的本质都是：**把「顺序」变成框架的资产，把「变化」变成你的接口。**

- `refresh()`：12 步固定，你能覆盖 `onRefresh()` / `postProcessBeanFactory()`；
- Nginx：11 个 phase 固定，模块只能选「挂在哪个 phase」。

**这就是模板方法的定义**：**顺序固定，个别步骤可变**。

**顺带一个具体结论**：Spring Boot 的「内嵌 Tomcat」不是黑魔法，它就是往 `protected void onRefresh()` 里塞了一行 `createWebServer()`。
</details>

### 7.3 C 组：判断力（6 题）

**C1.** 一段代码里 `class Service { Service(boolean cache, boolean retry, boolean metrics) }`，问题在哪？怎么改？

<details><summary>答案要点</summary>

**问题**：`if` 的组合数是 **2³ = 8 种**，每一种都可能是 bug。加第四个开关就是 16 种。

**原因**：**三个 boolean 就是 2³ 个类没拆开。**

**改法**：拆成三个装饰器，在组装处决定组合：

```java
Handler service = new ServiceHandler(instance);
Handler cache   = new CacheHandler(service, store);
Handler retry   = new RetryHandler(cache, 3);
Handler metrics = new MetricsHandler(retry, collector);
```

**收益**：加第 4 个能力只加一个类（不动构造器）；每一层可以单独测试；执行顺序由组装处决定，不藏在 `if` 的顺序里。
</details>

**C2.** 只写一个接口 + 一个实现，理由是「预留扩展」。这个理由成立吗？

<details><summary>答案要点</summary>

**不成立。**

**理由**：接口是「**给别人实现的契约**」，只在三种情况下必要——**① 有多个实现且运行时选 ② 需要被 mock 隔离外部依赖 ③ 是模块间的公开契约**。「预留」不在其中。

**而且它有实际成本**：多一个文件、多一次跳转、多一层阅读负担，**并且它存在得越久越难删**（因为「已经有接口了」）。

**正确做法**：等第二个实现**真的出现**再抽。那时改动只要 5 分钟，而现在删掉它能省下每天 5 秒的阅读成本。
</details>

**C3.** 「用 Spring 的 `ApplicationListener` 实现订单创建后发邮件」，这个设计有什么风险？

<details><summary>答案要点</summary>

**默认是同步的**（`SimpleApplicationEventMulticaster` 的 `taskExecutor` 默认是 `null`），所以：

1. **监听器抛异常 → 发布者的事务回滚**。很多人以为是「通知失败不影响主流程」，实际上默认就会影响；
2. **没有持久化** → 应用重启时正在处理的事件丢失；
3. **没有重试** → 邮件发失败就是失败。

**三种改法，取决于语义**：

| 需求 | 改法 |
|---|---|
| 通知失败可以接受 | `@Async` + `@EventListener`，并加兜底日志 |
| 通知必须送到 | Outbox 表 + 独立发送进程（§3.5） |
| 要可靠 + 要跨服务 | MQ |

**关键判断**：**进程内通知没有持久化，重启就丢。** 「最终一致」的前提是「有持久化的中间状态」。
</details>

**C4.** 下面这段代码的问题是什么？

```java
public class PriceCalculator {
    public BigDecimal calculate(BigDecimal amount, String type) {
        return switch (type) {
            case "vip"    -> amount.multiply(new BigDecimal("0.9"));
            case "coupon" -> amount.subtract(new BigDecimal("20"));
            default       -> amount;
        };
    }
}
```

<details><summary>答案要点</summary>

**表面上**它想用策略模式，**实际上**是一个「类型字符串 + switch」的分支。它有四个具体问题：

1. **字符串不是类型**：`"VIP"` / `"Vip"` 都会掉进 `default`，编译器不检查；
2. **加一个策略要改这里**：违反开闭原则——但**更严重的是下面两条**；
3. **无法携带参数**：优惠券面额、VIP 折扣率都写死在代码里，改一次要发版；
4. **无法单元测试单个策略**：只能测整个 `calculate`。

**正确做法**：`DiscountStrategy` 接口 + 三个实现 + 一个 `Map<String, DiscountStrategy>` 注册表（或者直接从配置里读）。

**但要说清楚边界**：**如果只有这两种折扣、且参数永远不变，`switch` 版本更好**——它更短、更直观、没有多余类型。**判断依据是「变更频率」和「是否需要带参数」，不是「像不像模式」。**

> 这条题的真正考点：**不要看到 `switch` 就喊「应该用策略」**。§5.1 第 1 条说的是「一个实现不是策略」，这条说的是「`switch` 也不一定是坏味道」。
</details>

**C5.** 服务网格（Service Mesh）值不值得上？给出你的判断条件。

<details><summary>答案要点</summary>

**核心权衡**：进程内的装饰器栈，改一次要发一次版；进程外的装饰器栈，改一次只改配置。**当一个组织有 200 个服务、每个都用同一套「重试 + 熔断 + 限流」时，把这份重复 200 遍的代码换成一份共享的 sidecar，是唯一算得过来的账。**

**不该上的四种情况**（§3.2.3）：

| 场景 | 判断 |
|---|---|
| 服务数 < 10 | 不值得，运维成本 > 收益 |
| 只需要「重试 + 超时」 | 客户端库（Resilience4j）足够 |
| 没有 K8s 运维能力 | 会引入一类全新故障（sidecar 注入失败、iptables 劫持错误） |
| 极端低延迟 | 多一跳不可接受 |

**还要能说出代价**：延迟 +0.1~1ms、上下文必须塞进 header、失败模式从「抛异常」变成「超时/连接重置/200 但 body 是错误」。
</details>

**C6.** 下面这个 `retry` 实现有什么问题？

```java
public Response call(Request req) {
    Response resp = doCall(req);
    int attempts = 0;
    while (resp.isRetryable() && attempts < 3) {
        resp = doCall(req);
    }
    return resp;
}
```

<details><summary>答案要点</summary>

**两个 bug，一个致命**：

1. **`attempts` 从不自增** → 如果第一次就是可重试的失败，**这是一个死循环**。这就是 §7.1 那个真实 OOM 事故的**最小复现**（本项目写了 `RetryLoopProbe` 把 50000 次迭代、1.6MB 的 trace 固定下来取证）；
2. **没有换实例** → 即使不自增的问题修了，三次都打同一个已经挂掉的实例，重试毫无意义。

**还有第三个更隐蔽的**：

3. **`isRetryable()` 分不清「谁拒绝的」**。如果网关自己返回 `503`（熔断中），它也是 `retryable`，那就会重试一个**根本不会打到上游**的请求。正确判断是 `upstreamFailure() = retryable() && !"gateway".equals(servedBy)`。

**修好的版本**：

```java
public Response call(Request req) {
    Response resp = doCall(req);
    int tried = 1;
    while (resp.upstreamFailure() && tried < maxAttempts) {
        req = req.withNextInstance();     // ← 换实例
        tried++;                          // ← 自增
        resp = doCall(req);
    }
    return resp;
}
```

**这条题的真正考点**：**「无限循环在 Java 里最常见的死法是 OOM，不是卡死」**——因为循环体里在往 `trace` 里追加字符串。
</details>

### 7.4 三道推导题（没有标准答案）

**D1.** 如果让你给 `apigateway` 加一个「超时」能力，你会加在哪一层？为什么？

**提示**：考虑「超时了要算失败吗」「算谁的失败」「重试要不要重新计时」。
**参考**：`CircuitBreakerHandler` 里已经有 `try/catch`（§7.2 讲过为什么加超时要改它），因为超时抛的是异常，而熔断器统计失败是在 `catch` 里。

**D2.** 如果让你的系统从「单机网关」变成「多副本网关」，哪些东西会立刻出问题？按严重程度排序。

**提示**：`RateLimitFilter` 的 `Map`、`CacheFilter` 的 `Map`、`CircuitBreaker` 的状态、`MetricsCollector` 的计数。
**参考**：按「错得最隐蔽」排序，而不是按「最容易发现」。**限流在多副本下会变成 N 倍**（最容易被当成「偶发」忽略），**缓存不一致**次之，**熔断状态各自为政**再次之。

**D3.** 用一句话说服一个「认为设计模式是八股文」的同事。

**提示**：不要说「模式很重要」，要说「模式让你少踩哪个坑」。
**参考**：**「`@Transactional` 自己调自己不生效」这一条，就是代理模式的定义。你不知道代理，就只能记住「它不生效」这个现象；你知道代理，就知道「所有在代理里面自己调自己的东西都不生效」——包括 `@Async`、`@Cacheable`、`@Retryable`。**

### 7.5 自评

| 得分（A 组 / B 组 / C 组） | 说明 |
|---|---|
| **6 / 6 / 6** | 你已经可以在 code review 里用模式做判断，而不是背定义 |
| **4+ / 4+ / 4+** | 基础扎实，建议回去补 §2.1.1 的生命周期表和 §5.3 的十条判断 |
| **A 组高、B/C 低** | 「知道但没用过」。去读 §2，把你自己项目里的三个扩展点找出来对着看 |
| **C 组高、A/B 低** | 「感觉派」。判断力好但缺事实支撑，容易在细节上翻车（比如 `ctx.write` 的方向） |
| **三组都低** | 别急。**把 §6 的总表打印出来，每次 `@Override` 一个框架方法就回来查一次**，三周之后自然形成条件反射 |

---

## 关于本文的边界（诚实声明）

**这篇文档有四个已知边界，你应该知道**：

| 边界 | 具体说明 |
|---|---|
| **只覆盖 JDK 21 已正式的特性** | 不讨论 JDK 22-25 的作用域值、结构化并发等——**我没在真实项目里用过它们，不写没验证的东西** |
| **框架版本有差异** | Spring 的 `BeanPostProcessor` 四阶段在 Spring 5/6 一致，但 `refresh()` 的步骤数在不同版本可能有细微差别；Netty 的 `ChannelPipeline` 从 4.x 起稳定 |
| **「现代替代」是观点** | §4.7 的「吸收程度」表、§4.8 的判断口诀、§5 的十条判断，**全部是观点，不是事实**。你可以不同意，但你应该知道它们是观点 |
| **没有跑过所有框架示例** | 本文的**本项目代码**全部编译运行并核对过输出（见 [`05-verification.md`](./05-verification.md)）；**框架代码片段是为了说明结构而摘录的**，没有逐个跑过，**版本敏感的地方我都标注了** |

**一句话**：**能核对的我都标了在哪核对；不能核对的我都标了是观点。** 这是本文和「网上抄来的模式文章」唯一的区别。

---

## 最后三句话

**第一句，关于 JDK**：
> 你今天 `import` 的每一个包，都至少藏着一个模式。**认出它，你就知道该改哪个参数。**

**第二句，关于框架**：
> 你写的每一个 `@Override`，都是框架不敢替你做的决定。**你填的形状，就是模式。**

**第三句，关于学习**：
> **模式不是用来「用」的，是用来「读」的。**
> 你写了三年代码可能一次都没「用上」访问者——但你一定读过别人的 `instanceof` 链，**那时候你就知道它本来应该长什么样。**

