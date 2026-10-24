# 适配器模式（Adapter Pattern）

> **一句话**：把一个类的接口转换成使用方期望的另一个接口，让原本不能协同工作的双方能一起工作。
>
> 本目录实测：**7 个源文件 / 172 行**，`javac -Xlint:all` 零告警；运行输出 **9 行 / 336 字节**，
> SHA-256 `17E89E88B9DB6AF1…`（`-Dstdout.encoding=UTF-8` 口径，连跑两次逐行 diff 完全一致）。

## 0. 先看结论

如果只能记住三句话：

1. **适配器解决的是「接口不兼容」，不是「功能不够」。** 功能不够用装饰器，接口不对用适配器。
2. **它是事后补救，不是事前设计。** 能改源码就直接改接口；只有改不了（第三方库、遗留系统、已发布 API、别人的 jar）时，才包一层。
3. **它的实现只有两个动作**：实现目标接口 + 持有适配者引用（组合），然后在目标方法里转调。

| 你可能有的疑问 | 一句话回答 | 详见 |
|---|---|---|
| 适配器和装饰器到底差在哪 | 适配器**改接口**，装饰器**不改接口只加行为** | §8.3 |
| 为什么不用继承（类适配器） | Java 单继承，只能适配一个类，还把适配者的方法泄漏给使用方 | §7.3 |
| 适配器里能不能写业务逻辑 | 不能。它是翻译层，一旦写逻辑就变成"第 N 个业务类" | §8.2 |
| 接口是我们自己写的，还需要适配器吗 | 不需要，直接改接口；或者把格式参数从接口里去掉（§7.4） | §3.2 |
| Java 8 之后适配器是不是过时了 | 单方法接口的适配器可以退化成一行 lambda，但多方法接口仍需类 | §7.5 |

## 1. 现实案例：说人话版

### 1.1 三个生活里的转换头

**电源转换头**。你带着国标插头的充电器去美国，美标插座插不进去。你不需要改充电器（适配者），也不需要改墙上的插座（使用方），只需要一个转换头（适配器）。

这个类比里三方的关系值得掰开看：

| 类比 | 代码角色 | 在本目录里是谁 |
|---|---|---|
| 墙上的美标插座 | 目标接口 Target：使用方**期望**的形状 | `AudioPlayer` |
| 你的国标充电器 | 适配者 Adaptee：**已有**但形状不对的东西 | `AdvancedMediaPlayer` |
| 转换头 | 适配器 Adapter：一头插国标，一头插美标 | `MediaAdapter` |
| 你 | 使用方 Client：只认美标插座 | `DefaultAudioPlayer` / `Client` |

**读卡器**。相机存的是 SD 卡，笔记本只有 USB-C 口。读卡器不改变照片，也不改变笔记本，只改变"形状"。

**翻译**。中方工程师说中文，外方工程师说英文，翻译不改变任何一方的专业判断，只做接口转换。**这一点很关键**：翻译如果开始替双方做技术决策，他就不是翻译了——对应到代码，就是适配器里不该有业务逻辑。

### 1.2 一个真实的业务场景（假设场景，本目录未实现）

假设公司有一个通知服务，短信发送统一走这个接口：

```java
public interface SmsChannel {
    void send(String phone, String text);
}
```

现在要接三家供应商，而它们的 SDK 长这样：

```java
// 阿里云风格
class AliyunSms {
    void sendSms(String mobile, String templateCode, String params) { ... }
}

// 腾讯云风格
class TencentSmsClient {
    void post(int nationCode, String phoneNumber, String[] params) { ... }
}

// 自建老网关
class LegacyGateway {
    void push(String json) { ... }
}
```

三个 SDK，三种方法名、三种参数结构，**全都不实现 `SmsChannel`，而且一个都不能改**（jar 包里的）。

最直接的想法是在业务代码里写 if：

```java
// 反面写法
if (vendor.equals("aliyun")) {
    aliyun.sendSms(phone, "SMS_001", "{\"code\":\"" + text + "\"}");
} else if (vendor.equals("tencent")) {
    tencent.post(86, phone, new String[]{text});
} else if (vendor.equals("legacy")) {
    legacy.push("{\"phone\":\"" + phone + "\",\"text\":\"" + text + "\"}");
}
```

这段代码有三个问题，且都不是"风格问题"：

1. **每接一家新供应商，就要改业务代码**（开闭原则失效）。
2. **业务代码要知道每家的参数约定**（模板编号、国家码、JSON 格式），职责泄漏。
3. **测试无法替换实现**：`new AliyunSms()` 是硬编码，单元测试必须联网。

适配器把这段 if 变成三个类：

```java
class AliyunSmsAdapter implements SmsChannel {
    private final AliyunSms aliyun;
    AliyunSmsAdapter(AliyunSms aliyun) { this.aliyun = aliyun; }

    @Override
    public void send(String phone, String text) {
        aliyun.sendSms(phone, "SMS_001", "{\"code\":\"" + text + "\"}");
    }
}
```

业务代码从此只认 `SmsChannel`：

```java
SmsChannel channel = new AliyunSmsAdapter(new AliyunSms());
channel.send("13800000000", "1234");
```

**新增供应商 = 新增一个适配器类，业务代码零改动。**

> 注意上面这个业务场景里，**"参数翻译"也是适配器的一部分**（`text` → 模板参数 JSON）。这比媒体播放器的例子更接近真实工作：真实世界的适配器往往不只是改方法名，还要改参数结构、单位、编码。

### 1.3 专业版：GoF 的定义与四个角色

> Convert the interface of a class into another interface clients expect. Adapter lets classes work together that couldn't otherwise because of incompatible interfaces.
> —— *Design Patterns*, GoF, 1994

四个角色：

| 角色 | 职责 | 本目录对应 |
|---|---|---|
| **Target（目标接口）** | 使用方期望的接口，**由使用方定义** | `AudioPlayer` |
| **Adaptee（适配者）** | 已存在的、接口不兼容的类 | `AdvancedMediaPlayer` 及其实现 |
| **Adapter（适配器）** | 实现 Target，内部持有 Adaptee，做转调 | `MediaAdapter` |
| **Client（使用方）** | 只依赖 Target | `DefaultAudioPlayer` |

一个容易忽略的细节：**Target 的所有权在使用方**。这意味着目标接口不能为了适配者而改——否则就是适配者反向污染使用方。本目录里 `AudioPlayer` 的签名 `play(audioType, fileName)` 是"历史遗留的目标接口"，适配器必须去迁就它，而不是让它迁就适配者。§4.4 会专门讲这个签名带来的麻烦。

### 1.4 JDK 里真实存在的适配器（可复核）

不是"教科书上说有"，是真的能看到类名和签名：

**① `Executors.callable(Runnable, T)`**

```java
Callable<String> c = Executors.callable(() -> System.out.println("run"), "done");
```

用 `javap` 反编译 JDK 25 的实现类，能看到它就是一个标准适配器：

```text
final class java.util.concurrent.Executors$RunnableAdapter<T> implements java.util.concurrent.Callable<T> {
  private final java.lang.Runnable task;
  private final T result;
  java.util.concurrent.Executors$RunnableAdapter(java.lang.Runnable, T);
  public T call();
}
```

`Runnable` 的 `run()` 没有返回值、不能抛受检异常；`Callable` 的 `call()` 有返回值、能抛异常。`RunnableAdapter` 把前者包装成后者（`run()` 的"无返回值"用固定 `result` 补上）。**这就是适配器最纯粹的样子：方法名不同、返回值契约不同，包一层对齐。**

**② `InputStreamReader` / `OutputStreamWriter`**

```java
Reader reader = new InputStreamReader(new FileInputStream("a.txt"), StandardCharsets.UTF_8);
```

`FileInputStream` 给你字节（`int read()`），`Reader` 要字符（`int read()` 但语义是"一个字符"）。`InputStreamReader` 内部持有一个 `InputStream`，把字节流按字符集解码成字符流。方法名恰好相同，**语义完全不同**——这也是适配：适配不一定改方法名，也可能只是改契约。

**③ `Channels.newInputStream(ReadableByteChannel)`**

把 NIO 的 `Channel` 适配成老 IO 的 `InputStream`，让只认 `InputStream` 的老代码能用上新 API。

**④ `Collections.enumeration(Collection<T>)`**

把新的 `Collection` 适配成古老的 `Enumeration`（`hasMoreElements()` / `nextElement()`），为了兼容 1998 年的 API。

**⑤ JAXB 的 `XmlAdapter<ValueType, BoundType>`**

官方直接以 Adapter 命名，用来把"不可绑定类型"适配成"可绑定类型"，例如把 `Date` 适配成 `String`，实现 `marshal` / `unmarshal` 两个方向——这就是 §7.7 要讲的双向适配器。

**⑥ 反例：`java.awt.event.MouseAdapter` 不是标准适配器**

`MouseAdapter` 实现了 `MouseListener` 的**全部**空方法，让你只覆盖关心的那个。它的作用是"提供默认实现"，GoF 把这个变体叫 **Default Adapter（缺省适配器）**，与"接口转换"是两回事。混淆这两者，是面试里最常见的减分点（§8.3 有专表）。

**⑦ 外部世界（不在 JDK 里，作为延伸）**

- Spring MVC 的 `HandlerAdapter`：Controller 有 `@RequestMapping` 方法、`HttpRequestHandler`、`Servlet` 等多种形态，`DispatcherServlet` 只调 `HandlerAdapter.handle(...)`。
- SLF4J 的绑定层：把 Log4j / JUL / Logback 适配成统一的 `Logger`。
- Spring 5 起 `WebMvcConfigurerAdapter`、`HandlerInterceptorAdapter` 被废弃，原因正是接口加了 `default` 方法——**缺省适配器被语言特性取代**了（§7.6）。

## 2. 原始实现：三版代码，三个阶段的痛

重构不是"把代码改写成模式"，而是"问题一步步逼出结构"。下面三版都是能跑的。

### 2.1 v0：直接 new，Client 认识所有具体类

```java
public class Client {
    public static void main(String[] args) {
        VlcPlayer vlc = new VlcPlayer();
        vlc.playVlc("直播秀.vlc");

        Mp4Player mp4 = new Mp4Player();
        mp4.playMp4("电影.mp4");
    }
}
```

痛点：`playVlc` 和 `playMp4` 没有共同父类型，**无法多态调用**。想写一个"根据文件名自动播放"的方法都做不到，只能 if-else 分流。

### 2.2 v1：抽出目标接口，但创建逻辑还在实现类里

```java
public interface AudioPlayer {
    void play(String audioType, String fileName);
}

public class DefaultAudioPlayer implements AudioPlayer {
    @Override
    public void play(String audioType, String fileName) {
        if ("mp3".equalsIgnoreCase(audioType)) {
            System.out.println("播放MP3格式的文件: " + fileName);
        } else if ("mp4".equalsIgnoreCase(audioType)) {
            new Mp4Player().playMp4(fileName);      // 使用方直接认识适配者
        } else if ("vlc".equalsIgnoreCase(audioType)) {
            new VlcPlayer().playVlc(fileName);      // 再来一种格式就再来一个分支
        }
    }
}
```

多态有了，但 `DefaultAudioPlayer` 依然**直接依赖具体播放器**。它既是使用方，又承担了"格式分发"的职责，两个职责粘在一起。

### 2.3 v2：适配器登场（原项目的结构）

原项目的做法是抽出 `MediaAdapter implements AudioPlayer`，`DefaultAudioPlayer` 把 `mp4` / `vlc` 交给它：

```java
public class MediaAdapter implements AudioPlayer {
    private AdvancedMediaPlayer advancedMediaPlayer;

    public MediaAdapter(String audioType) {
        if ("vlc".equalsIgnoreCase(audioType)) {
            advancedMediaPlayer = new VlcPlayer();
        } else if ("mp4".equalsIgnoreCase(audioType)) {
            advancedMediaPlayer = new Mp4Player();
        }
    }

    @Override
    public void play(String audioType, String fileName) {
        if ("vlc".equalsIgnoreCase(audioType)) {
            advancedMediaPlayer.playVlc(fileName);
        } else if ("mp4".equalsIgnoreCase(audioType)) {
            advancedMediaPlayer.playMp4(fileName);
        }
    }
}
```

结构是对的：**实现目标接口 + 持有适配者 + 转调**。但这段代码埋了五个坑，其中两个能实测出来。

### 2.4 原项目的五个具体问题

| # | 问题 | 位置 | 后果 |
|---|---|---|---|
| D1 | 构造器与 `play()` 各写一遍双分支，**同一个判断写两次** | 构造器 + `play()` | 新增格式要改两处，漏改就静默失效 |
| D2 | `advancedMediaPlayer` 可能为 `null`（不支持的格式进不来判断） | 构造器 | `play()` 里两个 if 都不成立，**静默返回** |
| D3 | `play()` 用的是**参数** `audioType`，构造器用的是**自己的** `audioType` | 两个真值来源 | 两者不一致时按参数走，可能调用适配者不支持的方法 |
| D4 | `AdvancedMediaPlayer` 是宽接口（`playVlc` + `playMp4`） | 接口设计 | `VlcPlayer` 必须写一个空的 `playMp4`，空实现就是静默失败 |
| D5 | `"vlc"` / `"mp4"` 两个魔法字符串散落在 6 处 | 全类 | 拼错一个字母，运行期才发现 |

D2、D3、D4 的共同点是：**失败时什么都不说**。这是最难查的一类 bug——不抛异常、不打日志、流程继续，只是结果不对。

### 2.5 实测：两处静默失败（真跑出来的）

把原项目 7 个文件和一段探针一起编译，探针直接构造"非法输入"，输出如下（逐字）：

```text
=== 原项目 structuraltype.AdapterPattern ===
[场景 1] 用不支持的格式构造适配器，再 play 同一格式
  begin
  end   <-- 中间没有任何输出，也没有异常
[场景 2] 构造器说 vlc，play 说 mp4（两个真值来源打架）
  begin
  end   <-- 静默失败
[场景 3] 直接调用 VlcPlayer.playMp4（宽接口的代价：空实现）
  begin
  end   <-- 静默失败
[场景 4] 正常路径：mp4 交给 Mp4Player
  begin
播放MP4格式的文件: movie.mp4
  end
```

三个 `begin` / `end` 之间是空的——**代码没有报错，也没有做事**。

场景 2 值得单独说：构造时给 `"vlc"`，`play` 时给 `"mp4"`，字段是 `VlcPlayer`，参数让代码走进 `playMp4` 分支，而 `VlcPlayer.playMp4` 是空实现。于是"播放 mp4"这个调用，既没有播放，也没有报错。

> 这类 bug 在生产环境的典型表现：用户点"导出 PDF"，界面转了两秒，什么都没发生，后台日志一片安静。

## 3. 设计动机

### 3.1 四个动机，按真实频率排序

| 动机 | 说明 | 典型场景 |
|---|---|---|
| **接入不可修改的代码** | 第三方 SDK、遗留系统、已发布的 jar | 支付、短信、对象存储 SDK |
| **统一多个同类实现** | 同一个能力有多种实现，接口各不相同 | 多厂商短信、多支付渠道、多云存储 |
| **保护使用方** | 让业务代码只依赖稳定的自有接口，不被外部 API 变更波及 | 供应商升级 SDK |
| **渐进式重构** | 给旧类套一个新接口，新代码用新接口，旧代码继续用旧类 | 单体拆分、模块解耦 |

注意第四个：**适配器可以只加不改**。旧代码继续调 `legacy.push(json)`，新代码调 `channel.send(phone, text)`，两条路都能走，等旧调用方全部迁完再删老接口。这是大型重构里最实用的用法。

### 3.2 什么情况下**不该**用适配器

五条，每条都对应一个常见误用：

**① 接口本来就是一致的。** 两个类方法名、参数、语义都一样，还要包一层，纯属增加跳转。

**② 可以修改适配者的源码。** 那就直接让它实现目标接口，或者把公共接口提出来。**适配器的前提是"改不了"**——这个前提不成立，模式就失去意义。

**③ 只是参数个数不同。** 用重载或默认参数（Java 用重载）就够了，不需要新类型。

**④ 只是缺少某些方法。** 用抽象骨架类或接口 `default` 方法（§7.6），不是适配器。

**⑤ 想加功能（日志、缓存、权限、重试）。** 用装饰器或代理，接口不变。

判断口诀：**"接口变了吗？"** 变了才是适配器。

### 3.3 与"直接改接口"的成本账

假设你有权限改适配者源码，两种做法：

| 维度 | 直接让适配者实现目标接口 | 加一层适配器 |
|---|---|---|
| 代码量 | 0 新增类 | +1 类 |
| 适配者是否被污染 | 是（业务类被迫依赖使用方的接口） | 否 |
| 一个适配者能对接几个目标 | 只能对 1 个（接口已定） | 可以写多个适配器 |
| 第三方升级时 | 直接冲突 | 只改适配器 |
| 测试 | 简单 | 多一层可注入的间接性（**这是优点**） |

结论：**如果适配者和使用方都是你自己的代码，优先改接口；只有"改不了"或"要对接多个目标"时，才用适配器。**

## 4. 设计推导：从 v2 到最终代码

这一节是本文的核心：每一步都先给出"为什么必须这么改"，再给代码。

### 4.1 第 1 步：谁来实现目标接口？

`AudioPlayer` 是使用方要的接口，`AdvancedMediaPlayer` 不实现它——**桥必须由新类来搭**。

```java
public class MediaAdapter implements AudioPlayer {   // ① 实现 Target
    ...
}
```

如果偷懒让 `VlcPlayer implements AudioPlayer` 呢？那就是"改适配者"，前提不成立（假设 `AdvancedMediaPlayer` 是第三方 jar），而且 `VlcPlayer` 会同时背负两套契约。

### 4.2 第 2 步：桥的另一头怎么接——组合，不是继承

```java
// 对象适配器：持有引用
public class MediaAdapter implements AudioPlayer {
    private final AdvancedMediaPlayer advancedPlayer;
}

// 类适配器：继承（不推荐，见 §7.3）
public class VlcClassAdapter extends VlcPlayer implements AudioPlayer { ... }
```

选组合的三个理由：

1. **Java 单继承**：`extends VlcPlayer` 就再也不能继承别的；而且一个适配器只能适配一个具体类，`Mp4Player` 就得再写一个适配器。
2. **不泄漏适配者的方法**：继承会把 `playVlc()` 暴露给使用方，使用方又能绕过目标接口直接调用，"统一接口"形同虚设。
3. **能适配 final 类**：`String`、`Integer`、很多第三方类都是 `final`，继承直接编译失败。

### 4.3 第 3 步：格式在什么时候确定？

原项目在构造器里 `if` 一遍决定创建谁，在 `play()` 里再 `if` 一遍决定调谁——**同一个决策做两次**。

关键观察：一个 `MediaAdapter` 实例**只服务一种格式**。既然如此，格式就应该在**构造时定死**，之后不再改：

```java
public MediaAdapter(String audioType) {
    this.supportedType = audioType.toLowerCase();
    this.advancedPlayer = PLAYERS.get(supportedType);
}
```

字段用 `final`，语义就清楚了：**适配器是"一次性绑定"的翻译器**。

### 4.4 第 4 步：两个真值来源怎么办

这是本目录最有价值的一个设计问题。

目标接口是使用方定的：

```java
void play(String audioType, String fileName);   // 格式 + 文件名
```

而适配器在构造时**已经知道**格式了。于是出现了两个真值来源：

```java
new MediaAdapter("vlc").play("mp4", "movie.mp4");   // 听谁的？
```

三种处理方式：

| 方案 | 行为 | 评价 |
|---|---|---|
| A. 只听构造器 | 用 `VlcPlayer.playVlc` 播 `movie.mp4` | **最危险**：播错格式还不报错，文件名后缀与解码器不匹配 |
| B. 只听参数 | 用 `VlcPlayer.playMp4` | 走进空实现，静默失败（原项目的做法） |
| C. 校验两者一致，不一致就拒绝 | 打印明确错误 | **本项目采用** |

```java
if (!supportedType.equalsIgnoreCase(audioType)) {
    System.out.println("[MediaAdapter] 格式不一致：构造时是 " + supportedType
            + "，调用时是 " + audioType);
    return;
}
```

> 第一次实现时我写的是方案 A（`switch (supportedType)`，完全忽略参数），实测输出是
> `[VlcPlayer] 播放 VLC 文件：movie.mp4`——**用 VLC 解码器播了一个 mp4**。
> 这个 bug 比原项目的静默失败更危险，因为它"看起来成功了"。加上校验后才变成明确拒绝。

为什么不在构造器里抛异常？因为使用方（`DefaultAudioPlayer`）是按格式创建适配器的，构造时格式一定合法；不一致只会出现在**误用**场景。误用要给出可读的运行时反馈，而不是让程序崩在构造器里。

> 参数冗余的根因在目标接口：`play(audioType, fileName)` 把"格式"这个**适配器已知的信息**又传了一遍。
> 这是历史接口的债，适配器只能迁就。彻底解法是 §7.4：把格式从接口里拿掉。

### 4.5 第 5 步：把字符串分发收进 Map

原项目 6 处魔法字符串。改成一个表：

```java
private static final Map<String, AdvancedMediaPlayer> PLAYERS =
        Map.of(
                "vlc", new VlcPlayer(),
                "mp4", new Mp4Player()
        );
```

三个收益：

1. **"支持哪些格式"只有一个答案**（`PLAYERS.keySet()`），不会出现"构造器认识 avi、play 不认识"的不一致。
2. **新增格式只改一处**，不用在两个 if-else 里各加一个分支。
3. `Map.of` 返回**不可变** Map：想偷偷 `PLAYERS.put(...)` 会抛 `UnsupportedOperationException`，把"配置不该在运行时改"写进了类型里。

代价：`Map.of` 不能运行时扩展。如果确实需要插件式注册，见 §7.2。

> 为什么不用 `ConcurrentHashMap`？这里只有静态初始化一次、只读，`Map.of` 更合适。
> 只有"运行期并发注册"才需要并发容器——不要因为"可能会并发"就提前加锁。

### 4.6 第 6 步：失败必须出声

三道闸，按顺序：

```java
if (advancedPlayer == null) { ... return; }                     // 闸 1：格式根本不支持
if (!supportedType.equalsIgnoreCase(audioType)) { ... return; }  // 闸 2：两个真值来源打架
switch (supportedType) { ... default -> ... }                    // 闸 3：映射表加了格式但 switch 没跟上
```

闸 3 看起来多余（`PLAYERS` 只有 vlc/mp4，switch 也覆盖了），但它防的是**未来**：有人往 `PLAYERS` 加了 `"mkv"` 却忘了加 `case`，`default` 会立刻喊出来。

**"不可能发生"的分支要不要写日志？要。** 因为它一旦发生，就是静默 bug。

### 4.7 第 7 步：为什么 `DefaultAudioPlayer` 还留着 if/else

这是规划与实际的一个偏差，值得说明。

原计划是把 `DefaultAudioPlayer` 也改成表驱动。但改完之后发现一个问题：**如果使用方已经足够优雅，就没有"接口不兼容"的痛感了**，适配器模式的教学动机反而消失。

保留 if/else 是刻意的：

```java
String type = audioType.toLowerCase();
if ("mp3".equals(type)) {
    System.out.println("[DefaultAudioPlayer] 原生播放 MP3：" + fileName);
} else if ("vlc".equals(type) || "mp4".equals(type)) {
    new MediaAdapter(type).play(type, fileName);
} else {
    System.out.println("[DefaultAudioPlayer] 不支持的格式：" + audioType);
}
```

它真实反映了"历史代码"的形态：**原生能力写死在 if 里，新增能力才想到抽适配器**。真实项目里，适配器往往不是整体设计的产物，而是"再加一种格式"时被迫抽出来的。

代价也要说清楚：**"支持哪些格式"现在有两个地方**（这里的 if + `MediaAdapter.PLAYERS`）。新增 MKV 要改两处（§7.1），这是本实现已知的 OCP 缺口。

### 4.8 最终结构

```text
                    AudioPlayer（Target，使用方定义的接口）
                          △                    △
                          │                    │
              DefaultAudioPlayer        MediaAdapter（Adapter）
              （原生 mp3 + 分发）              │ 持有
                                               ▼
                                  AdvancedMediaPlayer（Adaptee 接口，不可改）
                                       △                    △
                                       │                    │
                                  VlcPlayer             Mp4Player
                                  playVlc() ✓           playVlc() 空
                                  playMp4()  空          playMp4() ✓
```

一句话读图：**左边一列是"使用方的世界"，右边一列是"第三方的世界"，`MediaAdapter` 是唯一的接缝。**

## 5. 代码解析：每个类为什么存在

### 5.1 `AudioPlayer`——目标接口

```java
public interface AudioPlayer {
    void play(String audioType, String fileName);
}
```

只有两件事值得说：

- **它是使用方的资产**，不是适配者的。接口的签名由使用方的历史决定（"我们一直这么调"），适配器必须迁就。
- 两个参数让它**不是函数式接口**，所以 §7.5 的 lambda 写法在这里用不了——这也是"接口设计决定演化空间"的例子。

如果删掉它：`DefaultAudioPlayer` 和 `MediaAdapter` 失去共同类型，`Client` 里的 `AudioPlayer player = new DefaultAudioPlayer()` 无法多态，跟 v0 一样退回 if-else。

### 5.2 `AdvancedMediaPlayer`——适配者接口

```java
public interface AdvancedMediaPlayer {
    void playVlc(String fileName);
    void playMp4(String fileName);
}
```

它是**宽接口**：任何实现者都被迫实现两个方法，哪怕只支持一种。`VlcPlayer.playMp4()` 和 `Mp4Player.playVlc()` 都是空方法。

**为什么不修？** 因为适配器的前提就是"适配者不可改"。真实场景里这就是第三方 SDK 的样子——你只能在自己的适配器里绕开它，不能改它的接口。

**重构版如何保证空实现永远走不到？** 靠 `PLAYERS` 的"格式 → 播放器"一对一绑定 + 闸 2 的一致性校验：`vlc` 只会映射到 `VlcPlayer`，`play()` 只会调用 `playVlc()`。实测场景 2 现在被明确拒绝（§2.5 后半段）。这是"用设计约束消灭一类 bug"的例子：不修适配者，但让错误路径不可达。

彻底解法是拆窄接口（§7.6）。

### 5.3 `VlcPlayer` / `Mp4Player`——具体适配者

```java
public class VlcPlayer implements AdvancedMediaPlayer {
    @Override
    public void playVlc(String fileName) {
        System.out.println("[VlcPlayer] 播放 VLC 文件：" + fileName);
    }

    @Override
    public void playMp4(String fileName) {
        // 不支持，留空
    }
}
```

空方法上的注释 `// 不支持，留空` 必须写。**不写注释的空方法，六个月后没人知道是"不支持"还是"忘了写"。** 更好的做法是抛 `UnsupportedOperationException`，但那样会改变第三方行为，这里保持原样。

### 5.4 `MediaAdapter`——唯一的接缝

完整代码（就是仓库里的代码）：

```java
public class MediaAdapter implements AudioPlayer {

    /** 格式 → 播放器 映射表。 */
    private static final Map<String, AdvancedMediaPlayer> PLAYERS =
            Map.of(
                    "vlc", new VlcPlayer(),
                    "mp4", new Mp4Player()
            );

    private final AdvancedMediaPlayer advancedPlayer;
    private final String supportedType;

    public MediaAdapter(String audioType) {
        this.supportedType = audioType.toLowerCase();
        this.advancedPlayer = PLAYERS.get(supportedType);
    }

    @Override
    public void play(String audioType, String fileName) {
        if (advancedPlayer == null) {
            System.out.println("[MediaAdapter] 不支持的格式：" + audioType);
            return;
        }
        // 适配器在构造时已经绑定了格式，目标接口却把格式又传了一遍。
        // 两个真值来源必须校验，否则会用错方法去播对的文件（比静默失败更危险）。
        if (!supportedType.equalsIgnoreCase(audioType)) {
            System.out.println("[MediaAdapter] 格式不一致：构造时是 " + supportedType
                    + "，调用时是 " + audioType);
            return;
        }
        switch (supportedType) {
            case "vlc" -> advancedPlayer.playVlc(fileName);
            case "mp4" -> advancedPlayer.playMp4(fileName);
            default -> System.out.println("[MediaAdapter] 未处理的格式：" + audioType);
        }
    }
}
```

逐点解释：

| 代码 | 为什么这么写 |
|---|---|
| `implements AudioPlayer` | 适配器的唯一标志：**它必须在使用方那一侧** |
| `PLAYERS` 用 `static final` + `Map.of` | 类级不可变配置，只读、无需并发容器 |
| 两个字段都 `final` | 绑定后不可变 ⇒ 线程安全、行为可预测 |
| 构造器里 `PLAYERS.get(...)` | **一次查找**，避免每次 `play()` 都查表 |
| `advancedPlayer == null` 闸 1 | 不支持的格式提前拦截 |
| `equalsIgnoreCase` 闸 2 | 挡住"构造 vlc、调用 mp4" |
| `switch (supportedType)` | 用**绑定值**而不是参数分发，避免参数再影响行为 |
| `default` 分支 | 防"加了格式忘了加分支" |

**一个严格性细节**：`audioType.toLowerCase()` 没带 `Locale`。在土耳其语环境的 JVM 里，`"AVI".toLowerCase()` 会得到 `"avı"`（无点 i），于是 Map 查不到。Java 的规范写法是 `toLowerCase(Locale.ROOT)`。本 Demo 运行在中文/英文环境，未做处理；生产代码应当加上。

**另一个细节**：`PLAYERS` 里放的是两个**无状态**的播放器实例，被所有适配器共享。因为 `VlcPlayer` / `Mp4Player` 没有可变字段，共享是安全的。如果适配者有状态（比如"已播放次数"），共享就会串数据——那时应该在构造器里 `new`，而不是共享。

### 5.5 `DefaultAudioPlayer`——使用方，不是适配器

```java
public class DefaultAudioPlayer implements AudioPlayer {
    @Override
    public void play(String audioType, String fileName) {
        String type = audioType.toLowerCase();
        if ("mp3".equals(type)) {
            System.out.println("[DefaultAudioPlayer] 原生播放 MP3：" + fileName);
        } else if ("vlc".equals(type) || "mp4".equals(type)) {
            new MediaAdapter(type).play(type, fileName);
        } else {
            System.out.println("[DefaultAudioPlayer] 不支持的格式：" + audioType);
        }
    }
}
```

它做三件事：**处理自己原生支持的能力**（mp3）、**把不支持的委托给适配器**、**对完全不认识的格式明确拒绝**。

它每次 `play` 都 `new MediaAdapter(...)`。有人会问是不是浪费——这里是为了演示"适配器按格式实例化"；如果在意，可以缓存一个 `Map<String, MediaAdapter>`，但那是性能优化，与本模式无关。

**它没有实现适配器模式的关键动作**（持有适配者 + 转调），所以它不是适配器角色。很多同学把"分发器"误认成适配器，这是概念混淆。

### 5.6 `Client`——只依赖目标接口

```java
AudioPlayer player = new DefaultAudioPlayer();

player.play("mp3", "歌曲.mp3");
player.play("mp4", "电影.mp4");
player.play("vlc", "直播秀.vlc");
player.play("avi", "老格式.avi");
```

`Client` 全程只认识 `AudioPlayer`，**根本不知道 `MediaAdapter`、`VlcPlayer` 的存在**。这就是适配器的验收标准：

> 把 `MediaAdapter`、`VlcPlayer`、`Mp4Player` 全删掉，`Client` 的代码除了 `mp4` / `vlc` 两行播放失败外，**编译不需要改一行**。

### 5.7 为什么没有 `AudioPlayerAdapter` 这个类

初学者常问：既然是"适配 AudioPlayer"，是不是应该有个类叫 `AudioPlayerAdapter`？

不是。命名规则是 **"适配者 + Adapter"**：

- `MediaAdapter` = 适配"媒体"（`AdvancedMediaPlayer`）的适配器。
- 如果要接 FFmpeg，就叫 `FFmpegAdapter`。
- `AudioPlayer` 是 Target，不会出现在适配器名字里（它是"变成什么"，不是"包什么"）。

## 6. 执行流程

### 6.1 `play("mp3", ...)`：不经过适配器

```text
Client.main
  └─ player.play("mp3", "歌曲.mp3")        player 的静态类型是 AudioPlayer
       └─ 动态分派到 DefaultAudioPlayer.play(...)
            ├─ type = "mp3"
            ├─ "mp3".equals(type) → true
            └─ println("[DefaultAudioPlayer] 原生播放 MP3：歌曲.mp3")
```

全程没有适配器。**这提醒一件事：用了适配器不代表所有路径都要过适配器**，原生能力直接处理更简单。

### 6.2 `play("mp4", ...)`：经过适配器

```text
Client.main
  └─ player.play("mp4", "电影.mp4")
       └─ DefaultAudioPlayer.play("mp4", "电影.mp4")
            ├─ type = "mp4"
            ├─ 第二个分支命中
            └─ new MediaAdapter("mp4")
                 ├─ supportedType = "mp4"
                 └─ advancedPlayer = PLAYERS.get("mp4") = Mp4Player 实例
               └─ adapter.play("mp4", "电影.mp4")
                    ├─ 闸 1：advancedPlayer != null → 通过
                    ├─ 闸 2：supportedType("mp4").equalsIgnoreCase("mp4") → 通过
                    ├─ switch("mp4") → case "mp4"
                    └─ advancedPlayer.playMp4("电影.mp4")
                         └─ println("[Mp4Player] 播放 MP4 文件：电影.mp4")
```

**两次动态分派**：一次是 `AudioPlayer` 接口（`DefaultAudioPlayer`），一次是 `AdvancedMediaPlayer` 接口（`Mp4Player`）。适配器本身就是一个"分派中转站"。

### 6.3 `play("avi", ...)`：在最外层被拒绝

```text
player.play("avi", "老格式.avi")
  └─ DefaultAudioPlayer.play(...)
       ├─ "mp3".equals("avi")  → false
       ├─ "vlc".equals("avi") || "mp4".equals("avi") → false
       └─ println("[DefaultAudioPlayer] 不支持的格式：avi")
```

**适配器根本没被创建**。拒绝位置在外层，不在适配器里——这是合理的：`DefaultAudioPlayer` 负责"我认识哪些格式"，`MediaAdapter` 负责"我负责的那一种怎么播"。

### 6.4 完整输出（9 行，逐字）

```text
========== 适配器模式演示 ==========

[DefaultAudioPlayer] 原生播放 MP3：歌曲.mp3
[Mp4Player] 播放 MP4 文件：电影.mp4
[VlcPlayer] 播放 VLC 文件：直播秀.vlc
[DefaultAudioPlayer] 不支持的格式：avi

========== 扩展：新增 MKV 支持 ==========
（演示略，见 README 扩展对比章节）
```

### 6.5 复现方式与哈希

```bash
# 编译（本目录）
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/structural/adapter/*.java

# 运行（必须固定编码，否则字节数会变）
java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.adapter.Client > run.txt
```

| 指标 | 值 |
|---|---|
| 退出码 | 0 |
| 行数 | 9 |
| 字节数 | 336（UTF-8 口径，含 CRLF） |
| SHA-256（前 16 位） | `17E89E88B9DB6AF1` |
| 确定性 | 连跑两次逐行 diff 一致（本模块不含时间、随机数、身份哈希） |

> 用 GBK 输出时字节数为 280、哈希不同；**行数恒为 9**。不同编码口径不可混比，见 `docs/05-verification.md` §4.1.1。

## 7. 扩展对比

### 7.1 新增 MKV 格式：要改哪几处？

**必须有 2 处（本实现）**：

1. 新增 `MkvPlayer implements AdvancedMediaPlayer`；
2. `MediaAdapter.PLAYERS` 加一项 `"mkv", new MkvPlayer()`；
3. `MediaAdapter.play` 的 `switch` 加 `case "mkv" -> advancedPlayer.playMkv(fileName)`——但 `AdvancedMediaPlayer` 没有 `playMkv`，所以**还得改适配者接口**（第 3 处，且通常改不了）。

**还有 1 处**：`DefaultAudioPlayer` 的 `else if` 要加上 `"mkv"`，否则请求根本到不了适配器。

**使用方 `Client` 零改动**：

```java
player.play("mkv", "movie.mkv");   // 自动走适配器
```

对比"不用适配器"：`Client` 要写 `new MkvPlayer().playMkv(...)`，每加一种格式就要改一次使用方，而且使用方要知道 `playMkv` 这个具体方法名。

> 这就是适配器的真实收益：**变化被关在适配器里，使用方只多一个字符串**。
> 也暴露了本实现的缺口：`"支持哪些格式"还是两处维护。要彻底解决，见 §7.2 或 §7.4。

### 7.2 改成"运行时可注册"

插件化场景（比如按配置文件加载格式）需要运行期注册：

```java
public final class MediaPlayerRegistry {
    private static final Map<String, AdvancedMediaPlayer> PLAYERS = new ConcurrentHashMap<>();

    public static void register(String type, AdvancedMediaPlayer player) {
        PLAYERS.put(type.toLowerCase(Locale.ROOT), Objects.requireNonNull(player));
    }

    static AdvancedMediaPlayer find(String type) {
        return PLAYERS.get(type.toLowerCase(Locale.ROOT));
    }
}
```

**收益**：新增格式不用改 `MediaAdapter` 源码，符合 OCP。

**代价**（必须写出来，否则就是鼓吹）：

1. **全局可变状态**：注册顺序、重复注册、清理都是问题。
2. **错误推迟到运行期**：编译期不再能发现"格式没注册"。
3. **需要并发容器**：注册可能来自多个线程，才轮到 `ConcurrentHashMap` 上场。
4. **`switch` 不再适用**：方法分发要么用反射，要么让适配者提供一个统一的 `play(type, file)`——后者等于把接口改窄，回到 §7.4。

**结论**：只有"格式由外部配置决定"时才值得这么做。Demo 里两种格式写死，`Map.of` 更好。

### 7.3 类适配器版本（以及为什么不用）

```java
// 类适配器：继承适配者 + 实现目标接口
public class VlcClassAdapter extends VlcPlayer implements AudioPlayer {
    @Override
    public void play(String audioType, String fileName) {
        if (!"vlc".equalsIgnoreCase(audioType)) {
            System.out.println("[VlcClassAdapter] 只支持 vlc：" + audioType);
            return;
        }
        playVlc(fileName);      // 继承来的方法
    }
}
```

四个硬伤：

| 问题 | 后果 |
|---|---|
| Java 单继承 | `VlcClassAdapter` 已经用掉了唯一的父类名额 |
| 一适配一 | 要支持 mp4 还得写 `Mp4ClassAdapter`，无法用一个类统一适配 |
| 方法泄漏 | 使用方拿到的是 `VlcClassAdapter`，可以直接调 `playVlc()`，绕过目标接口 |
| 适配 final 类 | `final class XxxSdk` 直接编译失败 |

**唯一优势**：能重写适配者的方法（因为继承了）。但如果你需要重写它的行为，你要的其实是装饰器或代理，不是适配器。

### 7.4 干净版：把格式从目标接口里拿掉

本目录的目标接口 `play(audioType, fileName)` 有一个先天缺陷：**格式本该由类型系统表达，却被塞进了字符串参数**。如果目标接口是我们自己定的，应该这样写：

```java
// 目标接口：只留文件名
public interface MediaPlayer {
    void play(String fileName);
}

// 每个适配器绑定一种格式，不再需要 type 参数
public class VlcPlayerAdapter implements MediaPlayer {
    private final VlcPlayer vlc = new VlcPlayer();
    @Override
    public void play(String fileName) {
        vlc.playVlc(fileName);
    }
}
```

好处：

- **只有一个真值来源**，"构造 vlc、调用 mp4"这种矛盾**在类型上就不可能出现**；
- `MediaPlayer` 变成**函数式接口**，可以用 lambda（§7.5）；
- 不需要 `Map`、不需要 `switch`、不需要闸 1/闸 2。

代价：

- 调用方必须**先选对适配器**（`new VlcPlayerAdapter()` 还是 `new Mp4PlayerAdapter()`），选错就是选错类型，容易发现；
- 如果格式来自用户输入（字符串），仍需要一个工厂把字符串映射到适配器——但那是工厂的职责，不是适配器的。

**这就是"接口设计决定演化空间"的实证**：同一个模式，接口签名差一个参数，实现复杂度差一个数量级。

### 7.5 lambda 版：单方法接口的适配器可以退化成一行

如果目标接口只有一个方法（函数式接口），适配器不需要类：

```java
// 等价于 VlcPlayerAdapter 的一个实例
MediaPlayer vlcPlayer = fileName -> new VlcPlayer().playVlc(fileName);
```

甚至可以直接用方法引用（当适配者的方法刚好匹配时）：

```java
AdvancedMediaPlayer mp4 = new Mp4Player();
// 若存在 playMp4(String) 这样的单方法接口，可以直接：
// Mp4Playable playable = mp4::playMp4;
```

**这算不算没写适配器？** 算适配器，只是用 lambda 表达了"持有 + 转调"。lambda 捕获了 `mp4` 这个引用，等价于一个只有一个字段的匿名类。

**局限**：

- 只适用于**单方法**目标接口。本目录的 `AudioPlayer` 有两个参数，虽然仍是一个方法，但参数里带着 `type`，转调时要判断，lambda 写出来也不干净（`(type, file) -> ...` 里还是要 if）。
- 适配需要**状态**或**多个适配者**时，lambda 很快就不够用（比如"同时支持 vlc 和 mp4 的适配器"）。
- 调试栈里只能看到 `lambda$main$0`，可读性差。

**结论**：lambda 能吃掉"简单适配器"这一小块，吃不掉整个模式。

### 7.6 宽接口问题：三种解法

`AdvancedMediaPlayer` 有两个方法，实现者必须写空方法。三种解法：

**方案 A：拆窄接口（推荐）**

```java
public interface VlcPlayable { void playVlc(String fileName); }
public interface Mp4Playable { void playMp4(String fileName); }

class VlcPlayer implements VlcPlayable { ... }
class Mp4Player implements Mp4Playable { ... }
```

每个实现只实现自己会的，空方法消失。代价：适配者通常改不了（第三方），**这个方案只适用于"接口也是自己的"**。

**方案 B：`default` 方法抛异常**

```java
public interface AdvancedMediaPlayer {
    default void playVlc(String fileName) {
        throw new UnsupportedOperationException("不支持 VLC");
    }
    default void playMp4(String fileName) {
        throw new UnsupportedOperationException("不支持 MP4");
    }
}
```

失败从"静默"变成"响亮"。代价：改变了第三方的接口（如果它是 jar 里的，做不到）。

**方案 C：抽象骨架类（缺省适配器）**

```java
public abstract class AbstractMediaPlayer implements AdvancedMediaPlayer {
    @Override public void playVlc(String fileName) { /* 空 */ }
    @Override public void playMp4(String fileName) { /* 空 */ }
}

class VlcPlayer extends AbstractMediaPlayer {
    @Override public void playVlc(String fileName) { ... }
}
```

这就是 `MouseAdapter` / `KeyAdapter` 的写法。它解决的是"实现者不想写全部方法"，**不是接口转换**——所以它是 Default Adapter，不是 Adapter。

**Java 8 之后方案 C 基本被淘汰**：接口有了 `default` 方法，不需要再写一个抽象类。这也解释了为什么 Spring 5 废弃了 `WebMvcConfigurerAdapter`。

### 7.7 双向适配器

一个类同时实现两个接口，两个方向都能用：

```java
public class TwoWayAdapter implements AudioPlayer, AdvancedMediaPlayer {
    private final VlcPlayer vlc = new VlcPlayer();

    @Override public void play(String audioType, String fileName) {
        vlc.playVlc(fileName);              // 新 → 旧
    }
    @Override public void playVlc(String fileName) {
        play("vlc", fileName);              // 旧 → 新
    }
    @Override public void playMp4(String fileName) {
        System.out.println("不支持 mp4");
    }
}
```

**使用场景**：过渡期，新旧两套代码要互相调用。
**风险**：两个方向的语义容易不一致，而且继承了两套契约，类会迅速膨胀。**非必要不用**——正常情况下写两个单向适配器更清楚。

### 7.8 与另外四个模式的边界

| 模式 | 接口 | 目的 | 使用方是否知道被包对象 |
|---|---|---|---|
| **适配器** | **改变** | 让不兼容的接口能协作 | 不知道 |
| **装饰器** | 不变 | 增加行为（可多层叠加） | 通常不知道（类型仍是接口） |
| **代理** | 不变 | 控制访问（延迟、权限、缓存、远程） | 通常不知道 |
| **外观** | 简化（多个接口 → 一个） | 降低使用复杂度 | 不知道（也可能知道） |
| **桥接** | 抽象与实现**各自**变化 | 两个维度独立扩展 | 通常由抽象层持有 |

一句话记忆：

- 接口变了 → **适配器**
- 接口没变，行为变多 → **装饰器**
- 接口没变，行为不变，访问方式变 → **代理**
- 多个接口合成一个 → **外观**
- 抽象和实现都要独立扩展 → **桥接**

## 8. 优缺点与误区

### 8.1 优点

1. **能接不可改的东西**。这是它存在的根本理由，其他优点都是附带的。
2. **使用方只依赖自己的接口**。供应商换 SDK，改适配器即可。
3. **一个适配者可以对接多个目标接口**。写两个适配器类即可，适配者本身不受影响。
4. **可测试性**。使用方依赖接口，测试时塞一个假的 `AudioPlayer` 就行，不需要真的播视频。
5. **渐进式迁移**。旧代码继续用旧接口，新代码用新接口，可以并存。

### 8.2 缺点

1. **多一层跳转**。调试时栈里多一层，读代码要跳一次。
2. **适配器会悄悄变胖**。一开始只转调，后来加了格式判断、参数校验、日志、重试，最后变成"第二个业务类"。
3. **掩盖接口腐化**。如果系统里到处是适配器，说明上游接口设计有问题——适配器治标不治本。
4. **无法适配语义差异**。如果两个接口的**语义**不同（比如一个"发送"是同步确认，另一个是异步排队），适配器只能对齐签名，对齐不了语义——这种差异必须在文档里讲清楚，否则是埋雷。

### 8.3 七个常见误区

| 误区 | 真相 | 判别方法 |
|---|---|---|
| "适配器就是装饰器" | 适配器**改接口**，装饰器**不改** | 使用方拿到的类型，还是原来那个接口吗？ |
| "`MouseAdapter` 是适配器模式" | 它是**缺省适配器**（Default Adapter），解决"不想实现全部方法" | 它转换了接口吗？没有，它只是填空实现 |
| "类适配器比对象适配器好，因为能重写方法" | 能重写说明你要的是**装饰器/代理**；Java 单继承让类适配器基本不可用 | 需要适配多个类吗？需要就别继承 |
| "适配器里可以写点业务逻辑，反正都一样" | 一旦写逻辑，它就不再是翻译层，后面的修改会绕开它 | 这段逻辑换个适配者还需要吗？需要就抽出去 |
| "有 if-else 就是适配器" | 分发不是适配。适配器的标志是**实现目标接口 + 持有适配者** | 它有 `implements Target` 吗？ |
| "适配器可以解决性能问题" | 不能。它只是转发，甚至会多一次调用 | 性能问题看代理/缓存/享元 |
| "接口不兼容就应该加适配器" | 先问"能不能改接口"。能改就别加 | 适配者源码在你手里吗？ |

### 8.4 出现这些信号，说明你在滥用适配器

- 一个适配器里出现 `if (businessType == ...)`：业务判断混进了翻译层。
- 适配器持有 3 个以上的适配者：它在做"聚合"或"路由"，该考虑外观或中介者。
- 适配器之间互相调用：接缝太多，接口体系可能需要重设计。
- 适配器的 `play()` 有 20 行以上：多半混入了校验、日志、缓存——拆成装饰器。
- 只有 1 个实现、且永远不会再接第二个：直接改接口，别包。

## 9. 面试与自测

### 9.1 30 秒版

> 适配器模式把一个类的接口转换成使用方期望的另一个接口，让本来不能协同工作的两个类能一起工作。实现上是"实现目标接口 + 持有适配者引用"，在目标方法里转调。典型场景是接第三方 SDK、遗留系统。它和装饰器的区别是：适配器改接口，装饰器不改接口只加行为。Java 单继承决定了对象适配器（组合）优于类适配器（继承）。

### 9.2 2 分钟版

> 我拿项目里的媒体播放器举例。使用方有一个历史接口 `AudioPlayer.play(type, file)`，只原生支持 mp3；第三方库提供 `AdvancedMediaPlayer`，方法叫 `playVlc` 和 `playMp4`，签名完全不兼容，而且改不了。
>
> 适配器 `MediaAdapter implements AudioPlayer`，内部持有 `AdvancedMediaPlayer`，构造时按格式从一张不可变 Map 里取出对应的播放器，`play()` 里做一致性校验后转调。
>
> 实现时踩过一个坑：目标接口把格式作为参数又传了一遍，和适配器构造时绑定的格式构成两个真值来源。第一版我直接忽略了参数，结果"构造 vlc、调用 mp4"会用 VLC 解码器去播 mp4——比静默失败更危险。加上校验后变成明确拒绝。这件事说明适配器的目标接口签名会限制它的实现质量，如果接口是我们自己定的，应该把格式从参数里去掉，让每个适配器绑定一种格式。
>
> 对象适配器 vs 类适配器：后者靠继承，Java 只能继承一个类，还会把适配者的方法泄漏给使用方，也会被 final 类挡住。
>
> 和装饰器的区别一句话：适配器让"用不了"变成"能用"，装饰器让"能用"变成"更好用"。

### 9.3 如果面试官继续追问

| 追问 | 回答要点 |
|---|---|
| 适配器会带来什么坏味道 | 多一层跳转；适配器膨胀成业务类；掩盖上游接口腐化；对齐签名但对齐不了语义 |
| 什么时候不用 | 能改接口、接口本来就一致、只是参数不同、只是缺默认实现 |
| Java 8 之后模式有变化吗 | 单方法接口的适配器可用 lambda；`default` 方法取代了缺省适配器（`*Adapter` 类大量消失） |
| Spring 里哪里有 | `HandlerAdapter`（多种 Controller 统一处理）、`MessageConverter`（对象 ↔ HTTP 消息） |
| JDBC 算适配器吗 | 可以这么理解：`Driver` 是 SPI，各数据库驱动把自家协议适配成 JDBC 标准接口 |
| 适配器能替代防腐层吗 | 防腐层（ACL）通常比适配器更厚：除了转换接口，还隔离领域模型、翻译异常、做降级 |

### 9.4 自测题

**1. 适配器与装饰器最本质的区别是什么？**

<details><summary>参考答案</summary>

看**使用方拿到的接口有没有变**。适配器让使用方看到的是目标接口（`AudioPlayer`），被包的对象（`AdvancedMediaPlayer`）接口不同；装饰器包完之后仍是同一个接口，可以继续套下一层。所以装饰器可以叠 3 层，适配器一般只套 1 层——套 2 层适配器没有意义（中间层既不是使用方要的，也不是适配者要的）。
</details>

**2. 为什么推荐对象适配器？**

<details><summary>参考答案</summary>

三点：① Java 单继承，类适配器用掉唯一的父类名额；② 一个类适配器只能适配一个具体类，支持多种格式要写多个；③ 继承会把适配者的方法暴露给使用方，使用方可以绕过目标接口直接调用，破坏了"统一接口"这个初衷。另外 final 类无法被继承，直接淘汰类适配器。
</details>

**3. 本项目 `MediaAdapter` 的两个字段为什么都是 `final`？**

<details><summary>参考答案</summary>

因为适配器是"一次性绑定"的翻译器：构造时确定格式和适配者，之后不该再变。`final` 让这个约束成为编译期事实，同时也带来线程安全（不可变对象天然可共享）。如果字段可变，就可能出现"构造 vlc、中途改成 mp4"这种更难查的状态。
</details>

**4. `advancedPlayer == null` 这个判断能不能删掉？**

<details><summary>参考答案</summary>

不能。`DefaultAudioPlayer` 只会对 mp4/vlc 创建适配器，但 `MediaAdapter` 是 `public` 类，任何人都能 `new MediaAdapter("avi")`。删掉判断会在 `switch` 里对 null 调用方法，抛 `NullPointerException`。适配器作为公共类，必须防御非法输入。
</details>

**5. 为什么用 `Map.of` 而不是 `HashMap`？**

<details><summary>参考答案</summary>

`Map.of` 返回不可变实例：① 表达"配置在运行期不该被改"的设计意图；② 天然线程安全，不需要额外同步；③ 只有两个静态条目，不需要 `HashMap` 的扩容能力。代价是无法运行期注册，需要插件化时改用 `ConcurrentHashMap` + 注册方法（§7.2）。
</details>

**6. `switch (supportedType)` 为什么不用参数 `audioType`？**

<details><summary>参考答案</summary>

因为字段才是"适配器真正绑定的格式"，参数只是目标接口强加进来的。用字段分发保证"适配器只做它会做的事"；参数用来做一致性校验（闸 2），而不是用来决定行为。如果反过来用参数分发，就回到了原项目的问题：参数一变，就走进了适配者不支持的方法。
</details>

**7. 新增 MKV 要改几处？使用方要不要改？**

<details><summary>参考答案</summary>

本实现要改 3 处：新增 `MkvPlayer`、`PLAYERS` 加一项、`DefaultAudioPlayer` 的 else-if 加一个格式（另外 `AdvancedMediaPlayer` 若没有对应方法还得加，通常改不了）。使用方 `Client` 零改动。这也是本实现的 OCP 缺口：格式清单在两处维护。
</details>

**8. 手写题：给定 `LegacyLogger.logMessage(String)`，把它适配成 `Logger.log(Level, String)`。**

<details><summary>参考答案</summary>

```java
public interface Logger {
    void log(Level level, String message);
}

public class LegacyLoggerAdapter implements Logger {
    private final LegacyLogger legacy;

    public LegacyLoggerAdapter(LegacyLogger legacy) {
        this.legacy = Objects.requireNonNull(legacy);
    }

    @Override
    public void log(Level level, String message) {
        legacy.logMessage("[" + level + "] " + message);   // 参数翻译
    }
}
```
要点：实现目标接口、组合适配者、在方法里做**参数翻译**（`Level` → 字符串前缀）。注意 `Objects.requireNonNull`：适配器不该接受 null 适配者。
</details>

**9. 手写题：把 `Runnable` 适配成 `Callable<String>`。**

<details><summary>参考答案</summary>

```java
public class RunnableToCallableAdapter implements Callable<String> {
    private final Runnable task;
    private final String result;

    public RunnableToCallableAdapter(Runnable task, String result) {
        this.task = task;
        this.result = result;
    }

    @Override
    public String call() {
        task.run();
        return result;      // Runnable 没有返回值，用固定值补上
    }
}
```
这就是 JDK `Executors.callable(Runnable, T)` 内部 `RunnableAdapter` 的做法。要点是**契约差异的处理**：`run()` 无返回值且不能抛受检异常，`call()` 有返回值且能抛异常。
</details>

**10. 判断对错：`AudioPlayer` 是实现适配器的那个接口，所以它是适配者接口。**

<details><summary>参考答案</summary>

错。`AudioPlayer` 是**目标接口（Target）**，是使用方期望的接口。适配者接口是 `AdvancedMediaPlayer`——它才是"已有但形状不对"的那一方。混淆二者的直接后果是：会把 `implements AudioPlayer` 当成"实现适配者"，从而写反依赖方向。
</details>

**11. 如果适配者的方法抛异常，适配器应该怎么处理？**

<details><summary>参考答案</summary>

要**翻译异常**，而不是原样抛出。第三方 SDK 的受检异常（如 `AliyunSdkException`）如果直接漏出去，使用方就被迫依赖第三方类型，适配器等于白写。正确做法是包装成自己的异常类型（如 `SmsSendException`），保留 cause。这是适配器最容易被忽略的职责——**接口不只是方法签名，还包括异常契约**。
</details>

**12. 单元测试怎么证明适配器是对的？**

<details><summary>参考答案</summary>

两件事：① 用假适配者（stub）验证"目标方法被调用时，适配者的对应方法被调用了、参数翻译正确"；② 用真实适配者验证"不支持的格式会明确失败"。本目录用探针（外部类）做到了第 ② 点：构造非法输入，断言输出里有拒绝信息——原项目在这里是**零输出**，这就是 bug 的证据。
</details>

### 9.5 面试官想听什么

**想听**：

- "接口不兼容才用，能改接口就不用"——说明你懂边界，不是背模式。
- "对象适配器优于类适配器，因为 Java 单继承 + 方法泄漏"——说明你懂代价。
- "目标接口的签名会影响实现质量"——说明你有设计经验（本目录 §7.4）。
- "适配器不该有业务逻辑，它只是翻译层"——说明你懂职责。
- "异常契约也要翻译"——说明你写过真实接入代码。

**不想听**：

- 只背定义，举不出一个自己的例子。
- 把 `*Adapter` 结尾的类都叫适配器（`MouseAdapter` 是缺省适配器）。
- 说"适配器可以提高性能"或"适配器可以解耦所有依赖"——过度承诺。
- 说不出缺点，认为模式只有好处。

## 10. 附录

### 10.1 本目录文件清单

| 文件 | 角色 | 职责 |
|---|---|---|
| `AudioPlayer.java` | Target | 使用方期望的接口 |
| `AdvancedMediaPlayer.java` | Adaptee 接口 | 第三方播放器的能力（不可改） |
| `VlcPlayer.java` | 具体 Adaptee | 只支持 VLC |
| `Mp4Player.java` | 具体 Adaptee | 只支持 MP4 |
| `MediaAdapter.java` | **Adapter** | 实现 Target，持有一个 Adaptee，做格式校验 + 转调 |
| `DefaultAudioPlayer.java` | 使用方 | 原生 mp3 + 把 mp4/vlc 委托给适配器 |
| `Client.java` | 入口 | 4 条调用路径演示 |

### 10.2 运行方式

```bash
# Windows
build.bat
java -cp out com.patterns.structural.adapter.Client

# 或只编译本目录
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/structural/adapter/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.adapter.Client
```

### 10.3 本次修订记录

| 改动 | 原因 |
|---|---|
| `MediaAdapter` 增加"格式一致性校验"闸 2 | 实测发现"构造 vlc、调用 mp4"会用错解码器，比静默失败更危险 |
| `PLAYERS` 的全限定名 `java.util.Map` 改为 `import java.util.Map` | 与项目其他类保持一致 |

以下**不是本次改动**，而是本目录一直采用的设计（容易误以为是修 bug 加上的，特此说明）：字段终态 `final`、`PLAYERS` 用不可变 `Map.of`、`switch` 带 `default`、空实现写 `// 不支持，留空`。

---

相关文档：[模式对比 §结构型](../../../../../docs/02-pattern-comparison.md) · [学习路径](../../../../../docs/01-learning-path.md) · [优化记录](../../../../../docs/03-optimization-log.md) · [验证记录](../../../../../docs/05-verification.md)
