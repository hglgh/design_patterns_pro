# 观察者模式（Observer）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：一个对象状态变了，一群对象各自收到通知——
> 而**发通知的那个对象，从来不知道收通知的都是谁**。

> **它不是"for 循环"**。for 循环是"我知道你是谁，我挨个叫你"；
> 观察者模式是"我不知道你是谁，但名单在我手里，我照着名单叫"。
>
> 真正的难点不在"怎么通知"，在**通知失败、通知途中名单变了、同一个人算不算两个**。

> **本目录代码已用 JDK 25 实际编译运行验证**。文中所有输出、
> 所有"原项目跑出来的结果"都是真实执行得到的，不是编造的。
> 验证方式见[附录 B](#附录-b运行方式与验证记录)。

---

## 目录

1. [现实案例：一条动态的六次投递](#1-现实案例一条动态的六次投递)
2. [原始实现：原项目 144 行与它的七个问题](#2-原始实现原项目-144-行与它的七个问题)
3. [设计动机：观察者模式到底解决了什么](#3-设计动机观察者模式到底解决了什么)
4. [设计推导：从"for 循环通知"推到 11 个文件](#4-设计推导从for-循环通知推到-11-个文件)
5. [代码解析：每个文件为什么存在](#5-代码解析每个文件为什么存在)
6. [执行流程：五段 Demo 的真实运行结果](#6-执行流程五段-demo-的真实运行结果)
7. [扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
8. [优缺点与常见误区](#8-优缺点与常见误区)
9. [面试与自测](#9-面试与自测)
- 附录
  - [A：文件清单](#附录-a文件清单)
  - [B：运行方式与验证记录](#附录-b运行方式与验证记录)
  - [C：相关模式交叉索引](#附录-c相关模式交叉索引)
  - [D：原项目七个问题对照表](#附录-d原项目七个问题对照表)
  - [E：概念速查](#附录-e概念速查)
  - [F：版本与工具链](#附录-f版本与工具链)

---

## 1. 现实案例：一条动态的六次投递

### 1.1 说人话版：你关注了一个创作者，然后他发了视频

你在某个内容平台关注了一个讲设计模式的账号。

某天晚上八点，他发布了一条新视频。接下来发生的事情，你大概率都经历过：

| 发生在哪 | 你看到什么 | 什么时候到 |
|---|---|---|
| 手机锁屏 | 一条横幅：「你关注的 hgl 发布了新视频」 | 几秒内 |
| App 通知中心 | 一条未读消息 | 稍后，甚至合并成「3 条新动态」 |
| 邮箱 | 一封标题带视频链接的邮件 | 可能几分钟后，也可能你的设置里根本没开 |
| 微信服务号 | 一条模板消息 | 如果平台做了这个渠道 |

四个地方，四套完全不同的投递机制。而在这背后，发视频的那个人只做了一件事：

```java
creator.publishVideo("十分钟看懂观察者模式", "从一条动态说起");
```

**他不知道你是谁，不知道你的手机型号，不知道你有没有开邮件提醒。**
他甚至不知道一共有多少个粉丝。

这就是观察者模式要解决的问题：**让"发布"这件事与"接收"彻底解耦。**

### 1.2 三个必须回答的问题

把上面的场景拆开，后端要回答的问题只有三个：

1. **谁在关注？**
   —— 名单存在哪里，由谁维护，怎么加人、怎么踢人。
2. **怎么通知到？**
   —— 站内信写库、邮件走 SMTP、推送走 APNs/厂商通道。
   三条路径的代码没有任何共同点。
3. **通知失败怎么办？**
   —— 某个用户的推送 token 过期了，发不出去。
   是让整个发布失败？还是跳过他自己记一笔？

**这三个问题，构成了观察者模式的全部内容。**

第 1 个问题决定接口长什么样，第 2 个问题决定观察者怎么组织，
第 3 个问题决定返回值与异常策略。

绝大多数讲观察者模式的文章只讲第 1 个和第 2 个，
而**真正让生产环境出事的是第 3 个**。

### 1.3 一个真实事故：漏发的到货通知

某电商平台的"到货提醒"功能：

- 用户对缺货商品点了「到货提醒」；
- 商品补货入库时，系统遍历提醒名单，逐个发短信；
- 名单用 `ArrayList` 存，通知循环直接 `for` 遍历。

某天补货，运营发现：**名单里 8 万人，实际发出的短信只有前 2 千条。**

排查过程：

1. 短信服务商那边的记录显示，只收到了 2 千次调用；
2. 应用日志里，有一条 `NullPointerException`，发生在第 2001 个用户身上——
   那个用户的手机号字段是空的（历史数据，注册时没填）；
3. 异常沿着调用栈往上冒，被最外层的 `catch (Exception e)` 接住，
   打了一行日志，**没有重试、没有记录进度、没有告警**。

结果：**79999 个用户白等了一场。**

三个独立的缺陷叠在一起：

- 一个用户的数据问题，中断了全部用户的投递；
- 没有失败清单，所以不知道"发到哪了、还差谁"；
- 修复代码把异常兜底在太高的层级，让问题看起来像"偶发失败"。

如果当初写的是观察者模式的正确版本，这三个缺陷都不会存在：
每个观察者的失败被单独捕获，失败的进报告，成功的照常发。

### 1.4 本项目的示例：一个内容平台

本项目用一个内容平台把上面的场景**收敛到能一口气看懂的规模**：

| 角色 | 类 | 说明 |
|---|---|---|
| 被观察者 | `ContentCreator` | 创作者，持有粉丝名单 |
| 事件 | `Event` + `EventType` | 一条内容：文章 / 视频 / 直播 |
| 观察者接口 | `Observer` | 只要实现 `onEvent` |
| 观察者实现 | `InAppObserver` | 站内信：全部记录 |
| 观察者实现 | `EmailObserver` | 邮件：实时事件不发（发了也没用） |
| 观察者实现 | `PushObserver` | 推送：实时事件走高优先级，通道可能坏 |

三件刻意保留的东西：

- **直播不发邮件**——用来证明"同一个事件，不同渠道可以有不同处理"；
- **推送通道可关闭**——用来证明"一个观察者失败，其他人不受影响"；
- **两个同名的张三**——用来证明"身份必须是 ID，不能是名字"。

三件刻意去掉的东西：

- 时间戳、UUID、随机数——它们会让 Demo 输出**不可复现**；
- 真实网络调用——会把一次运行变成几秒钟，且结果不稳定；
- Spring 的 `ApplicationEventPublisher`——原生 Java 足够表达，
  引入框架只会让读者分不清"哪些是模式，哪些是框架".

---

## 2. 原始实现：原项目 144 行与它的七个问题

### 2.1 六个文件，144 行

原项目位于 `src/BehaviorType/ObserverPattern/`，共 6 个文件：

| 文件 | 物理行 | 代码行 | 职责 |
|---|---:|---:|---|
| `User.java` | 14 | 4 | 被观察者接口，只有 `notifyFollowers` |
| `Follower.java` | 13 | 4 | 观察者接口，只有 `update` |
| `Followable.java` | 14 | 5 | **关注能力**接口，`follow` / `unfollow` |
| `ConcreteUser.java` | 41 | 27 | 被观察者实现，持有 `List<Follower>` |
| `ConcreteFollower.java` | 30 | 19 | 观察者实现，**同时也是** `Followable` |
| `Client.java` | 32 | 15 | 演示：hgl 发两条动态 |
| **合计** | **144** | **74** | |

六个文件里，有三个是接口，每个接口只有 1～2 个方法。
看起来结构很清晰——但清晰的结构不等于正确的结构。

### 2.2 完整源码

**`User.java`** —— 被观察者接口：

```java
public interface User {
    void notifyFollowers(String postContent);
}
```

只有一个方法。**没有 `subscribe`，没有 `unsubscribe`，没有 `getFollowers`。**
那么这个"名单"到底在哪儿维护？答案是：在实现类的私有字段里，
而唯一的写入入口，在**另一个接口**上。

**`Follower.java`** —— 观察者接口：

```java
public interface Follower {
    void update(String userName, String postContent);
}
```

注意参数：两个 `String`。没有事件对象，没有类型，没有 ID。

**`Followable.java`** —— "可被关注"的能力：

```java
public interface Followable {
    void follow(User target);
    void unfollow(User target);
}
```

**这个接口是整个设计的根因。** 它被 `ConcreteFollower` 实现——
也就是说，"关注"这个动作，**长在了观察者身上**。

**`ConcreteUser.java`** —— 被观察者实现：

```java
public class ConcreteUser implements User {
    private final String userName;
    private final List<Follower> followers;

    public ConcreteUser(String userName) {
        this.userName = userName;
        this.followers = new ArrayList<>();
    }

    public void addFollower(Follower follower) {
        followers.add(follower);
    }

    public void removeFollower(Follower follower) {
        followers.remove(follower);
    }

    @Override
    public void notifyFollowers(String postContent) {
        for (Follower follower : followers) {
            follower.update(userName, postContent);
        }
    }

    public void post(String content) {
        System.out.println(userName + " 发布动态：" + content);
        notifyFollowers(content);
    }
}
```

`addFollower` / `removeFollower` 是 `public` 的，但**不在任何接口里**。
要用它们，必须拿到 `ConcreteUser` 的具体类型。

**`ConcreteFollower.java`** —— 观察者实现：

```java
public record ConcreteFollower(String name) implements Follower, Followable {

    @Override
    public void update(String userName, String postContent) {
        System.out.println(name + " 收到通知：@" + userName
                + " 发了一条新动态：" + postContent);
    }

    @Override
    public void follow(User target) {
        if (target instanceof ConcreteUser) {
            ((ConcreteUser) target).addFollower(this);
        }
    }

    @Override
    public void unfollow(User target) {
        if (target instanceof ConcreteUser) {
            ((ConcreteUser) target).removeFollower(this);
        }
    }
}
```

`instanceof` + 强转。**面向接口编程到这里就断了。**

**`Client.java`** —— 演示：

```java
public class Client {
    public static void main(String[] args) {
        ConcreteUser hgl = new ConcreteUser("hgl");

        ConcreteFollower zhangSan = new ConcreteFollower("张三");
        ConcreteFollower liSi = new ConcreteFollower("李四");
        ConcreteFollower wangWu = new ConcreteFollower("王五");

        zhangSan.follow(hgl);
        liSi.follow(hgl);
        wangWu.follow(hgl);

        hgl.post("今天阳光真好，适合写代码！");

        liSi.unfollow(hgl);
        hgl.post("写完代码了,该玩起来了！");
    }
}
```

### 2.3 真实输出

用 JDK 25 编译运行原项目，逐字输出如下（7 行）：

```text
hgl 发布动态：今天阳光真好，适合写代码！
张三 收到通知：@hgl 发了一条新动态：今天阳光真好，适合写代码！
李四 收到通知：@hgl 发了一条新动态：今天阳光真好，适合写代码！
王五 收到通知：@hgl 发了一条新动态：今天阳光真好，适合写代码！
hgl 发布动态：写完代码了,该玩起来了！
张三 收到通知：@hgl 发了一条新动态：写完代码了,该玩起来了！
王五 收到通知：@hgl 发了一条新动态：写完代码了,该玩起来了！
```

第二次发布，李四确实没收到——**功能是能跑的**。

这是必须说清楚的一点：**原项目不是"错的代码"，它是一个"在小规模、
理想数据、正常网络下能跑通的代码"。** 它的七个问题都藏在
"数据开始不理想"之后：

- 用户重名了；
- 有人在通知过程中退订了；
- 某个渠道挂了；
- 想再加一个渠道；
- 想知道这次到底发给了谁。

下面逐个说。

### 2.4 问题一：关注动作长在了观察者身上

看依赖方向：

```
ConcreteFollower.follow(User target)
        │
        │  target 是 User 接口
        ▼
  instanceof ConcreteUser   ←── 必须知道具体类
        │
        ▼
  ((ConcreteUser) target).addFollower(this)
```

**观察者为了"被注册"，必须反向操作被观察者的内部集合。**

这带来两个直接后果：

**后果一：接口形同虚设。**

```java
User u = new ConcreteUser("hgl");
Follower f = new ConcreteFollower("张三");

f.follow(u);      // 编译通过
                  // 内部 instanceof 成功，正常注册
```

```java
User u = new AnotherUser("hgl");     // 另一个实现
f.follow(u);                          // 编译通过
                                      // 内部 instanceof 失败
                                      // 什么也没发生，没有异常，没有返回值
```

**第二个调用静默失败。** 调用方无法区分"关注成功"和"关注被忽略"。

**后果二：能力放错了层。**

`Followable` 意味着"每个观察者都必须会关注别人"。
可现实是：

- 一个监控系统的告警观察者（发钉钉消息），它需要"关注"谁吗？不需要；
- 一个日志观察者，它需要吗？不需要；
- 只有"社交场景里的粉丝"才需要。

**把一个只有部分实现需要的能力放进接口，就是在逼其他实现写空方法。**

正确的方向只有一句话：

> **名单在谁手里，注册方法就写在谁身上。**

`ConcreteUser` 持有 `followers`，那么 `subscribe` / `unsubscribe`
就应该是 `User` 接口的方法。

### 2.5 问题二：用 `equals` 当身份

`ConcreteFollower` 是 `record`：

```java
public record ConcreteFollower(String name) implements Follower, Followable { }
```

`record` 自动生成 `equals`，按 `name` 比较。于是：

```java
ConcreteFollower a = new ConcreteFollower("张三");
ConcreteFollower b = new ConcreteFollower("张三");

a.equals(b);      // true   ← 两个不同的人，判等为 true
```

在 `ArrayList` 里，这会导致三个连锁问题：

**① 允许重复入列**

`List.add` 不检查重复，所以 `a` 和 `b` 都在名单里。

**② 同一个人收两条**

```java
hgl.post("第一条动态");
// 张三 收到通知：...
// 张三 收到通知：...
```

一个粉丝收到两条一模一样的通知。用户侧看到的是"App 重复弹了两次"。

**③ 取消关注删错人**

```java
a.unfollow(hgl);      // 内部 followers.remove(this)
```

`List.remove(Object)` 按 `equals` 找**第一个**匹配项。删掉的是 `a`，
但 `b` 还在名单里。于是：

```java
hgl.post("第二条动态");
// 张三 收到通知：...       ← 仍然收到，而且分不清是 a 还是 b
```

调用方以为"我取消关注了"，实际上另一个同名账号还在收。

**这不是 `equals` 写错了，是用 `equals` 当身份这件事本身就错了。**

`equals` 回答的是"这两个对象在业务上是否可以视为同一份数据"；
而观察者名单需要回答的是"这是不是同一个订阅者"。

前者的答案可能是"名字一样就算一样"，后者的答案永远必须是"ID 一样才算一样"。

### 2.6 问题三：边遍历边删

`notifyFollowers` 直接遍历 `followers`：

```java
for (Follower follower : followers) {
    follower.update(userName, postContent);      // 观察者内部可能 unfollow
}
```

如果某个观察者在 `update` 里取消了对自己的关注：

```java
public void update(String userName, String postContent) {
    System.out.println("[Quitter] 收到通知，现在取消关注");
    subject.unfollow(this);      // 修改 followers
}
```

这会在 `ArrayList` 的迭代器上触发 `ConcurrentModificationException`。

**但这不是最阴的地方。** 最阴的地方是：**它有时不报错。**

`ArrayList.Itr.hasNext()` 的实现是：

```java
public boolean hasNext() {
    return cursor != size;
}
```

它**不检查** `modCount`。只有 `next()` 才检查。

于是有一个精确的边界：

| 元素数 | 删掉的位置 | remove 后 cursor | remove 后 size | hasNext() | 结果 |
|---:|---|---:|---:|---|---|
| 3 | 第 0 个（靠前） | 1 | 2 | `1 != 2` → true | 继续 → `next()` 检查 → **抛 CME** |
| 3 | 第 1 个（**倒数第二**） | 2 | 2 | `2 != 2` → **false** | 循环直接结束 → **不抛异常** |
| 3 | 第 2 个（最后） | 3 | 2 | `3 != 2` → true | 继续 → `next()` 检查 → **抛 CME** |

第二种情况：**不报错，但最后一个观察者静默地漏掉了。**

生产环境里最可怕的就是这种：没有异常、没有日志、监控一片绿，
只有用户投诉"我没收到通知"。

### 2.7 问题四：一个异常中断全链

`notifyFollowers` 没有任何 `try-catch`：

```java
for (Follower follower : followers) {
    follower.update(userName, postContent);
}
```

任何一个 `update` 抛出运行时异常（推送 token 失效、数据库连接超时、
第三方 SDK 报错），循环立刻中断：

- 排在后面的观察者**全部收不到**；
- 异常冒泡到 `post()` 的调用方；
- 调用方以为"动态发布失败"，可实际上内容早就发布成功了。

**"内容发布成功"和"通知全部送达"是两件事**，
原项目用一个异常把它们绑死了。

### 2.8 问题五：`void` 通知没有回执

```java
public void notifyFollowers(String postContent) { ... }
public void post(String content) { ... }
```

两个方法都返回 `void`。

于是调用方**永远无法回答**这些问题：

- 这次发给了几个人？
- 有没有失败的？
- 失败的是谁？为什么？

唯一的信号是异常，而异常又会中断整个流程（问题四）。
于是代码陷入两难：**想知道失败就得收异常，收了异常就发不全。**

### 2.9 问题六：事件只有一个 `String`

```java
void update(String userName, String postContent);
```

一条事件的全部信息 = 作者名 + 一段文本。

加一个需求："直播要实时推送，文章可以合并推送"。
怎么办？

- 只能把 `postContent` 前面拼一个前缀（`"直播:" + content`），然后在观察者里 `startsWith` 判断；
- 或者把方法签名改成 `update(String userName, String postContent, String type)`，
  **所有实现类全部要改**；
- 或者让观察者去查数据库"这条内容是什么类型"——又依赖了一个外部状态。

**类型信息丢失，是所有字符串化设计的第一笔债。**

### 2.10 问题七：没有去重，也没有顺序保证

原项目用 `ArrayList`：

- **不去重**：同一个观察者 `follow` 两次，名单里就有两份；关于 `ArrayList` 的这个问题，前面 2.5 已经讲过。
- **删除是 O(n)**：`remove(Object)` 要线性扫描，8 万人的名单，每次退订都要扫。
- **没有稳定 ID**：想按 ID 精确删除，做不到。

而如果换成 `HashSet` 呢？去重有了，但**迭代顺序不稳定**——
同一份代码跑两次，通知顺序可能不同，Demo 输出就不可复现，
线上排查时"为什么这次是王五先收到"也解释不清。

### 2.11 用探针把七个问题变成实证

上面说的都是"读代码推断"。为了让每一条都变成可复现的事实，
我写了一个 110 行的探针程序（`Probe.java`），直接调用原项目的类，
把四个最关键的缺陷跑出来。

真实输出如下（38 行，逐字引用）：

```text
=== 探针 1：重复关注 + 同名冲突 ===
  两个「张三」都关注了，star.post 一次后：
hgl 发布动态：第一条动态
张三 收到通知：@hgl 发了一条新动态：第一条动态
张三 收到通知：@hgl 发了一条新动态：第一条动态
  → 「张三」收到了 2 条（List 允许重复，没有去重）

  现在 a.unfollow(star)，因为 equals 相同，删掉的是列表里第一个：
hgl 发布动态：第二条动态
张三 收到通知：@hgl 发了一条新动态：第二条动态
  → 仍然有一个「张三」在收通知，但已经分不清是 a 还是 b

=== 探针 2：通知途中取消关注 ===
  --- 场景 A：取消关注者在列表靠前，后面还有人 ---
hgl2 发布动态：触发并发修改
  [Quitter] 收到通知，现在取消关注
  → 抛出 ConcurrentModificationException
     后果：这条动态后面的粉丝一个都没收到，而且异常冒泡给了调用方

  --- 场景 B：取消关注者恰好是倒数第二个 ---
hgl2b 发布动态：中间那个自己退订
  [正常粉丝 1] 收到通知
  [Quitter] 收到通知，现在取消关注
  → 居然没有抛异常
     原因：remove 后 size 从 3 变 2，而迭代器 cursor 恰好也是 2，hasNext() 直接返回 false，轮不到 checkForComodification()
     最阴的地方：同一个 bug，删倒数第二个不报错，而「正常粉丝 2」静默地没收到通知

=== 探针 3：一个观察者抛异常，其他人全部收不到 ===
hgl3 发布动态：有坏观察者的一条动态
  → 抛出：模拟：某个粉丝的推送通道坏了
     后果：B 和 C 都没有收到通知，而且 post() 本身失败了

=== 探针 4：follow() 遇到别的 User 实现，静默失败 ===
  fan.follow(other) 执行完毕：没有输出、没有异常、没有返回值
  → 调用方无法知道关注到底成功了没有
  [别的 User 实现] 发布了：我发了一条动态
  → other 的通知列表根本没有被碰过，赵六永远不会收到
```

**探针 2 的场景 B 值得单独说一句。** 我最初的假设是
"删最后一个不报错"，实测**报错了**；改成"删倒数第二个"才复现出
"不抛异常、静默漏发"。这个反直觉的边界，正是 `cursor != size`
这个实现细节造成的。

如果不实际跑一遍，只靠读代码，**很容易把这条写错**——
这也是本项目坚持"所有结论都要有真实输出"的原因。

---

## 3. 设计动机：观察者模式到底解决了什么

### 3.1 本质：一对多 + 单向依赖

观察者模式的结构可以压缩成一句话：

> **一个对象（Subject）持有一组对象的引用（Observer），
> 在特定时刻逐个调用它们的同一个方法。**

关键在"单向"：

```
   Subject  ──────>  Observer（接口）
      │                    ▲
      │                    │
      └── 持有 List<Observer>
                           │
              InAppObserver ┘
              EmailObserver ┘
              PushObserver  ┘
```

- Subject **知道** Observer 接口；
- Observer **不知道** Subject 的存在（它只收到一个 `Event`）；
- 箭头是单向的，所以加一个新渠道，Subject 一行都不用改。

**这是它唯一的、也是全部的价值：让"发布"与"接收"在编译期互不依赖。**

### 3.2 什么时候**不需要**观察者模式

这一节比"什么时候需要"更重要。因为观察者模式是最容易被滥用的模式之一。

**① 只有一个观察者，而且以后也不会多**

```java
// 不要这样
orderService.addListener(new EmailSender());

// 直接调用就行
emailSender.send(order);
```

一个观察者的"观察者模式"，只是把一次方法调用拆成了三步：
定义接口、注册、回调。**收益为零，成本是三个文件。**

**② 调用方需要同步拿到结果**

观察者模式的语义是"发出去就不管了"（fire-and-forget）。
如果业务上必须"通知成功才继续"，那它本质上是一次**同步调用**，
应该写成 `boolean result = notifier.notify(event);`，而不是观察者。

**③ 观察者的行为需要严格顺序且互相依赖**

如果 B 的处理依赖 A 的结果，那它们不是"一群独立的观察者"，
而是一条**责任链**或**流水线**。
观察者之间是**平行、互不关心**的关系，顺序只是注册顺序的副产品。

**④ 事件只有一两种，而且不会增加**

如果系统里只有"下单成功"一个事件、一个处理方，
用一个 `@EventListener` 或者直接调用，比一整套 Subject/Observer 便宜得多。

**⑤ 需要"跨进程"的通知**

观察者模式是**进程内**的。一旦接收方在另一个服务里，
你需要的是消息队列（Kafka、RocketMQ）。
用进程内的观察者去模拟跨服务通知，会得到一个**不可靠的、没有持久化的、
重启就丢消息的伪消息队列**。

### 3.3 观察者 vs 回调 vs 事件总线 vs 消息队列

这四个东西经常被混为一谈。区别在**解耦的层次**：

| | 谁知道谁 | 一对多 | 进程内 | 可靠性 |
|---|---|---|---|---|
| **直接调用** | A 知道 B 的具体类型 | 否 | 是 | 调用方负责 |
| **回调（函数式接口）** | A 知道一个函数，不知道是谁 | 否（通常一个） | 是 | 调用方负责 |
| **观察者模式** | Subject 只知道 Observer 接口 | **是** | 是 | **Subject 负责隔离与报告** |
| **事件总线** | 双方都不知道对方，只知道"主题" | 是 | 是 | 依赖总线实现 |
| **消息队列** | 双方都不知道对方，也不知道有几个 | 是 | **否** | 由 MQ 保证 |

用一句话区分：

- **回调**是"我留个电话，你有事打给我"——一对一；
- **观察者**是"我这儿有份名单，有事我挨个打"——一对多，且**名单在发布方**；
- **事件总线**是"大家去公告栏看"——连名单都没有，靠主题匹配；
- **消息队列**是"寄快递"——跨进程、可持久化、可重试。

**面试里最常问的是"观察者和发布订阅有什么区别"，答案就是第三行和第四行：
观察者的 Subject 和 Observer 是相互知道的（至少 Subject 知道接口），
发布订阅引入了一个中间的 Broker，双方连对方存在都不知道。**

### 3.4 一句话判据

什么时候该用观察者模式？

> **当"一个变化"需要触发"多个互不相干的动作"，
> 而且这些动作的具体内容在编译期不应该被变化源知道时。**

反过来，只要满足下面任意一条，就不该用：

- 动作只有一个，且短期内不会增加；
- 动作必须同步返回结果；
- 动作之间有依赖顺序；
- 接收方在另一个进程里。

### 3.5 本项目要在七个问题上各做什么

把第 2 章的七个问题，逐条对应到设计决策：

| 原项目问题 | 本项目的决策 | 落在哪个文件 |
|---|---|---|
| 关注动作在观察者身上 | 把 `subscribe` / `unsubscribe` 移到 Subject | `Subject` |
| 用 `equals` 当身份 | `Observer.id()` 显式身份，`Map` 按 ID 去重 | `Observer` / `ContentCreator` |
| 边遍历边删 | 先快照再遍历 | `ContentCreator.publish` |
| 一个异常中断全链 | 逐个 `try-catch`，只捕获 `RuntimeException` | `ContentCreator.publish` |
| `void` 没有回执 | 返回 `DispatchReport` | `DispatchReport` / `Delivery` |
| 事件只有一个 `String` | `Event` 不可变 record + `EventType` 枚举 | `Event` / `EventType` |
| 没有去重、O(n) 删除 | `LinkedHashMap<String, Observer>` | `ContentCreator` |

这七条对应的具体推导，是下一章的内容。

---
## 4. 设计推导：从"for 循环通知"推到 11 个文件

这一章是整篇文档的核心。它不展示最终代码，只回答一个问题：

> **每一行代码，是被哪个具体的问题逼出来的？**

如果一个决定说不出"不这样做会出什么事"，那它就不该存在。

### 4.0 起点：能跑的最小版本

假设我们只有一段最朴素的通知逻辑：

```java
public class ContentCreator {
    private final List<Follower> followers = new ArrayList<>();

    public void publish(String content) {
        for (Follower follower : followers) {
            follower.update(content);
        }
    }
}
```

这段代码能跑，而且**在这个规模下是合理的**。
接下来的十个决定，每一个都要先证明"这个最小版本会出事"。

### 4.1 决定一：`Observer` 接口要几个方法

**候选方案**

```java
// 方案 A：只有一个回调
public interface Observer {
    void onEvent(Event event);
}

// 方案 B：回调 + 名字
public interface Observer {
    String name();
    void onEvent(Event event);
}

// 方案 C：回调 + ID + 展示名  ← 本项目选这个
public interface Observer {
    String id();
    String displayName();
    void onEvent(Event event);
}
```

**为什么 A 不够**

因为 Subject 需要用某种方式**记住**观察者。如果接口里没有任何标识，
Subject 只能：

- 用对象引用本身当标识（`List<Observer>`）——那就退回了 `ArrayList` 的所有问题：
  不能去重、不能 O(1) 删除、不能按 ID 找；
- 或者要求"所有观察者必须实现 `hashCode`"——这是隐式契约，编译器不检查，
  忘写就静默出错。

**方案 A 的致命后果，是身份无法跨越边界传递。**
比如前端点"取消关注"，请求里带的是 `fanId=1001`，
服务端只有字符串 `"1001"`，拿不到当初那个 `Observer` 实例。
没有 `id()`，这个请求就无法处理。

**为什么 B 不够**

`name` 是**给人看的**，它会变：

- 用户改昵称，名字变了；
- 两个用户都叫「张三」，名字一样；
- 运营要把「站内信·张三」改成「私信·张三」，名字变了。

**任何会变的东西，都不能当键。**

用名字当身份，就是在赌"全站没有重名"——
而社交产品里，重名是常态不是异常。

**为什么 C 是必需的**

`id()` 满足三个条件：

1. **稳定**：注册那一刻确定，之后不变；
2. **唯一**：由业务保证（数据库主键、用户 ID）；
3. **可传递**：是一个 `String`，能进 URL、能进日志、能进 JSON。

`displayName()` 单独一个方法，是因为它服务于另一个目的：
报告要打印"谁失败了"，打印 `fan-1001` 不如打印「站内信·张三」。
两个需求，两个方法，不互相妥协。

**如果把 `displayName()` 删掉会怎样？**

`Delivery.describe()` 只能写：

```text
[失败] com.patterns.behavioral.observer.PushObserver@1a2b3c — ...
```

运营看不懂，客服没法用，日志也没法搜。

### 4.2 决定二：订阅方法写在谁身上

**候选方案**

```java
// 方案 A（原项目）：观察者主动操作 Subject
interface Followable {
    void follow(User target);
    void unfollow(User target);
}

// 方案 B：Subject 提供注册方法  ← 本项目选这个
interface Subject {
    void subscribe(Observer observer);
    boolean unsubscribe(String observerId);
}

// 方案 C：第三方注册中心
class ObserverRegistry {
    void register(Subject subject, Observer observer);
}
```

**方案 A 为什么是错的**

不是因为"不好看"，而是因为**它让接口失去意义**。

`follow(User target)` 的签名里，`target` 是接口 `User`。
但实现里必须 `instanceof ConcreteUser` 才能调 `addFollower`：

```java
public void follow(User target) {
    if (target instanceof ConcreteUser) {
        ((ConcreteUser) target).addFollower(this);
    }
    // 否则静默失败
}
```

于是出现了 Java 里最典型的一种坏味道：**签名说接口，实现要具体类。**

调用方被签名骗了。他以为自己可以传任何 `User`，
实际上只有一种能工作，而且失败了还不报错。

**更深一层：能力放错了位置。**

`Followable` 意味着"实现者必须会关注别人"。
可现实是：

- 一个把事件写进日志文件的观察者，需要"关注"谁吗？
- 一个把事件转发到 Kafka 的观察者，需要吗？
- 一个在测试里用的 `MockObserver`，需要吗？

**只有社交场景里的粉丝才需要。** 把一个只有部分实现需要的能力
放进接口，就是逼其他所有实现写空方法，或者写一个 `throw new UnsupportedOperationException()`。

这是**接口污染**：接口本该描述"这一类对象共同具备的能力"，
一旦混进了"个别对象才有的能力"，它就不再是可实现的契约。

**方案 B 为什么是对的**

一句话：**谁持有数据，谁提供操作数据的方法。**

`observers` 这个 `Map` 是 `ContentCreator` 的私有字段。
按封装原则，能改它的只能是 `ContentCreator` 自己。
那么"加一个人"和"删一个人"就应该是它的公开方法。

这样还有一个额外好处：**`Observer` 接口彻底干净了。**
它只有三个方法，全部关于"我是谁"和"我怎么被通知"，
没有任何一个方法是"我怎么去操作别人"。

**依赖方向从"双向"变成了"单向"**：

```
方案 A:  ConcreteFollower ──> ConcreteUser      （观察者依赖被观察者的具体类）
方案 B:  ContentCreator  ──> Observer           （被观察者依赖观察者接口）
```

**方案 C 为什么不用**

`ObserverRegistry` 在技术上可行，很多框架（Spring 的
`ApplicationEventMulticaster`）就是这么做的。但它引入了新的问题：

- `Subject` 和 `Observer` 的生命周期要被 `Registry` 管，谁负责清理？
- 一个观察者订阅多个 Subject 时，Registry 要维护多对多关系；
- 多了一个必须存在的第三方便，Demo 的阅读理解成本上升。

**在本示例的规模下（一个作者、一份名单），方案 C 是纯负债。**
它的价值要在"跨多个 Subject 的统一订阅管理"场景下才体现——
那属于框架层，不属于模式教学层。

### 4.3 决定三：名单用什么集合

这是全篇推导里最关键的一步，因为它同时决定了去重、顺序、性能三件事。

**逐个淘汰**

| 候选 | 去重 | 顺序 | 删除 | 淘汰原因 |
|---|---|---|---|---|
| `ArrayList`（原项目） | ✗ | 插入序 | O(n) | 三个都不满足 |
| `HashSet` | ✓ | **不稳定** | O(1) | 顺序不稳定 |
| `HashMap` | ✓ | **不稳定** | O(1) | 同上 |
| `ConcurrentHashMap` | ✓ | **不稳定** | O(1) | 同上，且本示例单线程 |
| `CopyOnWriteArrayList` | ✗ | 插入序 | O(n) | 不去重、删除慢（见下） |
| `LinkedHashMap` ← 选它 | ✓ | **插入序** | O(1) | —— |

**`HashSet` / `HashMap` 的顺序问题有多严重？**

不是"理论上不稳定"，是**同一份代码、同一个 JVM，跑两次可能不同**。
`HashMap` 的迭代顺序取决于哈希值和扩容历史，
`Set.of(...)` 的迭代顺序甚至是**每次 JVM 启动都不同**（它加了随机化盐值防哈希碰撞攻击）。

后果：

- Demo 输出不可复现，"为什么这次王五先收到"解释不清；
- 单元测试断言"通知顺序"会随机失败；
- 排查线上问题时，日志顺序每次不一样，无法对比。

**`CopyOnWriteArrayList` 为什么不行？**

它确实能解决"边遍历边删"（因为迭代器看到的是旧数组）。
但它解决不了另外两件事：

```java
// 去重：还是要自己遍历查
if (!followers.contains(observer)) { ... }      // O(n)

// 删除：还是要按 equals 线性扫描
followers.remove(observer);                     // O(n)
```

而且 `CopyOnWriteArrayList` 的"写时复制"意味着
**每次 `subscribe` 都会复制整个数组**。8 万人的名单，
每加一个粉丝就复制 8 万个引用——这个代价在"注册频繁"的场景下不可接受。

**`LinkedHashMap<String, Observer>` 赢在哪**

```java
private final Map<String, Observer> observers = new LinkedHashMap<>();
```

1. **键去重**：`putIfAbsent(id, observer)`，同一个 ID 只可能有一份，
   而且去重发生在**注册那一刻**，不是每次遍历时判重；
2. **插入序**：`LinkedHashMap` 内部维护一条双向链表，迭代顺序 = 插入顺序。
   "投递顺序 = 注册顺序"这条契约**不需要写任何代码**就成立了；
3. **O(1) 删除**：`remove(id)` 直接按哈希定位。

**为什么键是 `String` 而不是 `Observer` 对象？**

如果键是 `Observer`，那么 `HashMap` 就要调用 `observer.hashCode()` 和
`observer.equals()`。而我们刚刚在 4.1 论证过：
**对象本身的相等语义，与"是不是同一个订阅者"无关。**

一个 `InAppObserver` 和另一个 `InAppObserver`，即使所有字段一样，
也可能是两个不同的人（比如两个用户的站内信渠道配置完全相同）。
反过来，同一个用户换了个设备，可能是两个不同的对象实例，但 ID 相同。

**用 ID 当键，等于用业务身份取代对象身份。** 这是整个设计的支点。

### 4.4 决定四：为什么必须快照

**问题**：观察者可能在 `onEvent` 里调用 `unsubscribe`。

**候选方案**

```java
// 方案 A：直接遍历原集合（原项目）
for (Observer o : observers.values()) { o.onEvent(event); }

// 方案 B：用迭代器，允许 Iterator.remove
Iterator<Observer> it = observers.values().iterator();
while (it.hasNext()) { it.next().onEvent(event); }

// 方案 C：先复制一份，遍历副本  ← 本项目选这个
List<Observer> snapshot = new ArrayList<>(observers.values());
for (Observer o : snapshot) { o.onEvent(event); }
```

**方案 A 为什么必炸**

第 2.6 节已经用探针实证了：`ArrayList` 的迭代器
在 `hasNext()` 时不检查 `modCount`，于是出现"有时抛 CME、
有时静默漏发"的双重行为。

对 `LinkedHashMap` 来说，`modCount` 的检查逻辑类似，
**同一类问题一样存在**。

**方案 B 为什么不行**

`Iterator.remove()` 只能删**当前迭代到的那个元素**。
但观察者要删的可能是**别人**：

```java
// 用户 A 收到通知后，把用户 B 也拉黑了
subject.unsubscribe("fan-B");
```

这不是"删自己"，`Iterator.remove()` 无能为力。
而且要把 `Iterator` 传给观察者，等于把集合的内部结构
暴露给外部——**封装直接破了**。

**方案 C 解决了两件事**

第一，**技术上的安全**。遍历的是独立数组，
观察者怎么改原 `Map` 都不影响本轮。

第二，**语义上的明确**。这条更重要：

> **本次通知的名单，在开始的那一刻就固定了。**

由此推出的两个边界行为，都有确定答案：

| 情况 | 行为 | 为什么 |
|---|---|---|
| 通知途中 `subscribe` 一个新观察者 | 本轮**收不到** | 名单在开始时已固定，"他来得太晚" |
| 通知途中 `unsubscribe` 自己 | 本轮**仍然收到** | 同上，"他在开始时还在名单里" |

**如果没有这条语义，同一段代码在不同人眼里会有不同理解：**
有人认为"中途订阅应该立刻收到"，有人认为"下一轮才生效"。
两个人都能从代码里找到"证据"，因为代码根本没表态。

**快照的代价**

每次 `publish` 复制一次 `List`。8 万人的名单，每次发布复制 8 万个引用
（约 640KB 的指针数组）。这个代价在"发布频率远低于通知人数"的场景下
完全可以接受。

如果发布极其频繁（比如每秒上千次），可以缓存快照，只在
`subscribe` / `unsubscribe` 时重建——**这就是 `CopyOnWriteArrayList` 的思路**。
本项目不这么做，因为"每次重建"的实现更简单，
而简单在示例代码里比性能重要。

### 4.5 决定五：`onEvent` 返回 `void` 还是 `boolean`

**候选方案**

```java
// 方案 A：返回 boolean
boolean onEvent(Event event);

// 方案 B：返回一个结果对象
Delivery onEvent(Event event);

// 方案 C：返回 void，失败靠异常  ← 本项目选这个
void onEvent(Event event);
```

**方案 A 的问题：返回值会被误用**

一旦有了 `boolean`，调用方立刻会写出这种代码：

```java
if (!observer.onEvent(event)) {
    retry(observer, event);              // 重试几次？
    alert("通知失败");                    // 谁来告警？
    log.error("失败了，但不知道为什么");    // 失败原因呢？
}
```

问题在于：

1. **`false` 说不出原因。** 是 token 过期？是网络超时？是内容违规被拒？
   布尔值只有一个比特，装不下这些；
2. **重试策略不属于观察者。** 观察者只该回答"我这次收到了没有"，
   "失败了要不要重试、重试几次、退避多久"是 Subject 或上层调度的事。
   让 `onEvent` 返回一个可被解读为"请重试"的值，
   等于把决策权偷偷塞给了错误的一方；
3. **返回值可以被忽略而不报错。** 这是最危险的——
   `observer.onEvent(event);` 这行代码编译通过，返回值直接丢弃，
   编译器不会提醒你。而异常**不能被静默忽略**（要么冒泡，要么被显式 `catch`）。

**方案 B 的问题：观察者被迫知道汇报格式**

如果 `onEvent` 要返回 `Delivery`，那么每个观察者都要写：

```java
return new Delivery(this.id(), this.displayName(), false, "token 过期");
```

它需要知道 `Delivery` 有几个字段、失败原因怎么拼、成功时传什么。
**观察者的职责从"处理事件"膨胀成了"处理事件并生成汇报"。**

而且它还要自己判断"什么算成功"——
一个邮件渠道返回 `Delivery(ok)` 但邮件其实进了垃圾箱，谁来管？
汇报的准确性应该由 Subject 从"有没有抛异常"这个客观事实推导，
而不是由观察者自述。

**方案 C 为什么是对的**

约定只有两句：

```text
正常返回  = 投递成功
抛 RuntimeExcepiton = 投递失败
```

这个约定有三个好处：

1. **信息量大**：异常有类型（`IllegalStateException` vs `IllegalArgumentException`）、
   有消息、有堆栈。"通道关闭"和"参数非法"能区分开；
2. **无法被静默忽略**：忘了处理，编译不报错但运行时会炸，
   这比"忘了检查返回值"要安全得多；
3. **实现最简单**：观察者只管干活，不管汇报：

```java
public void onEvent(Event event) {
    if (!channelOpen) {
        throw new IllegalStateException("推送通道已关闭：" + deviceId);
    }
    // 正常发
}
```

**异常的代价也要说清楚**

抛异常要填充堆栈，比返回一个 `false` 慢一到两个数量级。
在高频路径（每秒百万次）上，这会成为瓶颈。
那时正确的做法是用 `Result<T, E>` 这样的值对象（Rust 的 `Result`、
Java 里的 `Either`），而不是异常。

**本项目选异常，是因为它把"失败原因"这件最难表达的事，
用语言内置机制解决了。** 在示例代码的规模下，清晰 > 性能。

### 4.6 决定六：失败怎么隔离

确定了"抛异常表示失败"之后，下一个问题是：
**谁来 catch？**

**候选方案**

```java
// 方案 A：观察者自己 catch（原项目风格）
public void onEvent(Event event) {
    try {
        doSend(event);
    } catch (Exception e) {
        e.printStackTrace();          // 然后呢？
    }
}

// 方案 B：Subject 统一 catch  ← 本项目选这个
for (Observer o : snapshot) {
    try {
        o.onEvent(event);
    } catch (RuntimeException e) {
        deliveries.add(Delivery.failed(o, e));
    }
}
```

**方案 A 为什么不行**

观察者自己吞掉异常之后，**发布方永远不知道推送其实没发出去**：

- 报告里全员成功，实际有一半失败；
- 监控看不到失败率，告警永远不响；
- 用户投诉"没收到"，翻日志只有一行 `printStackTrace`。

而且每个观察者都要写一遍 `try-catch`，写法还各不相同：
有人打日志，有人吞掉，有人转成别的异常再抛。**没有统一契约。**

**方案 B 为什么只 catch `RuntimeException`**

Java 的异常分两类：

```
Throwable
├── Error                      ← 不捕获
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
└── Exception
    ├── RuntimeException       ← 捕获
    │   ├── NullPointerException
    │   ├── IllegalStateException
    │   └── ...
    └── 受检异常              ← 接口签名里没声明，编译器保证不会出现
        ├── IOException
        └── ...
```

**① 为什么不捕获 `Error`**

`OutOfMemoryError` 意味着 JVM 已经无法分配对象。
这时候继续执行 `deliveries.add(...)` 会再触发一次 OOM。
`StackOverflowError` 意味着调用栈已满，继续调用会再次溢出。

**捕获 `Error` 不是"容错"，是"掩盖灾难"。**
正确做法是让进程退出，由外部（K8s、systemd）拉起。

**② 为什么不用 `catch (Throwable)`**

因为它把 `Error` 也包进去了，等于上面那条的反面教材。
`catch (Throwable)` 是代码审查里最应该被拒绝的写法之一。

**③ 为什么不用担心受检异常**

`Observer.onEvent` 的签名里没有 `throws`，
所以编译器**保证**它不会抛出受检异常。
这里 `catch (RuntimeException)` 已经覆盖了所有可能。

**隔离的粒度：一个人 vs 一批人**

```java
for (Observer observer : snapshot) {
    try {
        observer.onEvent(event);            // 每次调用一个独立的 try
    } catch (RuntimeException e) {
        deliveries.add(Delivery.failed(observer, e));
    }
}
```

`try` 在**循环体内**，不是包住整个循环。
这一个括号的位置，决定了"一个坏人不拖累所有人"。

如果写成：

```java
try {
    for (Observer observer : snapshot) {     // ← try 在外面
        observer.onEvent(event);
    }
} catch (RuntimeException e) {
    // 第 3 个人失败，第 4～N 个人全部没发
}
```

就又回到了原项目的老路。

### 4.7 决定七：`publish` 返回什么

**候选方案**

```java
// 方案 A：void（原项目）
void publish(Event event);

// 方案 B：int，成功数量
int publish(Event event);

// 方案 C：List<RuntimeException>，失败列表
List<RuntimeException> publish(Event event);

// 方案 D：DispatchReport  ← 本项目选这个
DispatchReport publish(Event event);
```

**方案 A** 已经论证过：调用方无法回答"发给了谁、失败几个"。

**方案 B 的信息量不够**：

```java
int ok = publish(event);
// ok = 2，一共 3 个人
// 谁失败了？为什么？不知道。
```

而且只有数字没法排查——运营问"李四为什么没收到"，
你只能回答"因为 3 个人里成功了 2 个"。

**方案 C 有两个问题**：

1. **只有失败，没有成功。** 想统计"总人数"还得自己加；
2. **持有 `Throwable` 会拖住内存。** 异常对象持有堆栈、持有它引用的所有局部变量
   （可能包括连接、缓冲区、整个请求体）。把一批异常放进一个报告对象、
   再传给上层、再塞进缓存——每一个 `Throwable` 都是一个潜在的内存泄漏点。

**方案 D：`DispatchReport`**

一个不可变的记录，包含：

```java
public record DispatchReport(
        Event event,                  // 发了什么
        List<Delivery> deliveries,    // 每人一条结果
        int succeeded,                // 成功数
        int failed                   // 失败数
) { }
```

它同时满足：

- **可统计**：`attempted()`、`allSucceeded()`；
- **可归因**：每条 `Delivery` 有 `observerId` + `observerName` + `errorMessage`；
- **可打印**：`describeAsText()` 直接给人看；
- **可测试**：`record` 的 `equals` 让"整个报告"成为一个可断言的值；
- **不拖内存**：`Delivery.errorMessage` 是 `String`，不是 `Throwable`。

**为什么把 `succeeded` / `failed` 存成字段**

理论上，它们可以从 `deliveries` 现算：

```java
public int succeeded() {
    return (int) deliveries.stream().filter(Delivery::succeeded).count();
}
```

不这么做的原因有两个：

1. **性能**：报告会被反复读（打印、打点、告警判断），每次 O(n) 不值；
2. **自洽性**：这是更重要的。如果三个数字是现算的，
   就永远不可能出错；但如果它们是**构造参数**，
   就必须在构造处校验"数字与明细是否对得上"。

```java
public DispatchReport {
    deliveries = List.copyOf(deliveries);
    long okCount = deliveries.stream().filter(Delivery::succeeded).count();
    if (okCount != succeeded || deliveries.size() - okCount != failed) {
        throw new IllegalArgumentException("统计数字与明细不符");
    }
}
```

这段校验的意义在于：**报告不能自己说自己。**
如果允许"声明 0 失败、明细里有 3 个失败"这种情况存在，
那这份报告在三个月后一定会误导某个人。

**为什么 `List.copyOf` 而不是直接存引用**

```java
deliveries = List.copyOf(deliveries);
```

- 直接存引用：如果调用方在构造后往原 `List` 里 `add` 一条，
  报告的明细就变了，但 `succeeded` / `failed` 没变——**自洽性被破坏**；
- `Collections.unmodifiableList(...)`：它只是一个**视图**。
  它禁止你改，但原 `List` 被别处改了，视图会跟着变；
- `List.copyOf(...)`：做一次**真正的复制**，返回不可变列表。
  原 `List` 怎么改都与这份报告无关。

报告要的是"那一刻的冻结事实"，必须真拷贝。

### 4.8 决定八：`Event` 长什么样

**字段推导**

每加一个字段，都要有一个"不加会怎样"的理由：

| 字段 | 不加会怎样 |
|---|---|
| `type` | 渠道没法区分直播和文章（`EmailObserver` 需要它跳过直播） |
| `authorId` | 观察者要路由/去重时，得回查作者信息 |
| `authorName` | 通知文案要显示"@hgl"，回查一次数据库不值 |
| `title` | 文案里要出现《标题》，而且空标题要能在构造处拦住 |
| `content` | 正文摘要，通知里要展示 |

**为什么是 `record` 而不是普通类**

三个理由，按重要性排序：

1. **不可变**。一条事件会广播给 N 个观察者。如果它是可变的，
   第一个观察者改了标题，第二个观察者收到的就是被改过的内容。
   这种 bug 只在"某个观察者顺手改了事件对象"时出现，**极难排查**。
   `record` 的字段全是 `final`，从语言层面堵死；
2. **构造校验**。紧凑构造器是唯一能保证"对象一出生就是合法的"的地方：

```java
public Event {
    Objects.requireNonNull(type, "事件类型不能为 null");
    requireText(authorId, "作者 ID");
    requireText(authorName, "作者名");
    requireText(title, "标题");
    content = (content == null) ? "" : content;
}
```

如果没有这一步，`null` 标题会一路传到五个观察者，
然后五个观察者各炸一次——**排查时看到五条不同的空指针，其实是同一个数据问题**；

3. **`equals` / `toString` 免费**。测试里可以直接断言整个事件对象。

**为什么没有 `eventId` 和 `publishedAt`**

真实系统需要，本示例故意不加：

- `publishedAt` 要用 `Instant.now()`，输出里就会出现时间戳，
  **Demo 输出不可复现**；
- `eventId` 通常是 UUID，同样不可复现。

本项目所有示例都遵守同一条纪律：

> **输出可复现优先于字段完整。**

一个每次运行结果都不同的 Demo，无法用来验证"我改的这行代码有没有影响输出"。

**`describe()` 和 `summary()` 为什么放在 `Event` 里**

粗看这像是"把展示逻辑混进了数据对象"，但仔细想：

- 数据对象不是"纯数据"，它是**领域对象**。一条事件的"一行式描述"
  是它的固有属性，不是某个渠道的偏好；
- 三个渠道里有两个用同一格式，第三个（`PushObserver`）只是在前面加了优先级标记——
  **说明这个格式确实是共性的**；
- 如果某个渠道要完全不同的格式，它可以直接用 getter 自己拼：

```java
String custom = event.type() + "|" + event.authorId() + "|" + event.title();
```

`describe()` 存在，不**妨碍**定制。它只是把"默认格式"收进了一处。

**`EventType` 为什么是枚举**

三个理由：

1. **有限集合**。文章、视频、直播，不会无限增加；
2. **需要附加数据**。每种类型带一个中文名和一个 `realtime` 标记：

```java
ARTICLE("文章", false)
VIDEO("视频", false)
LIVE("直播", true)
```

如果用 `String` 常量，这两个附加信息就得写成两个 `switch`，
而且拼错了编译器不报错（`"Lvie"` 是合法字符串）；

3. **`switch` 穷尽性**。将来如果加了 `SHORT_VIDEO`，
   所有 `switch` 都会编译失败（JDK 21+ 的 pattern matching for switch 要求穷尽），
   **编译器帮你找全所有要改的地方**。

**为什么 `realtime` 是字段而不是方法**

```java
// 方案 A：字段
LIVE("直播", true)

// 方案 B：方法
public boolean realtime() {
    return this == LIVE;          // 加一种实时类型就要改这里
}
```

方案 B 把"哪些类型是实时的"这条规则写进了**代码逻辑**，
而方案 A 把它写进了**数据**。

差别在于：将来加一种"限时秒杀"事件、也要求实时，
方案 B 要改方法体，方案 A 只要在枚举常量上写 `true`。

**这条原则和状态模式里"能用数据描述的差异不要用类"是同一个。**
`if (type == LIVE)` 和 `realtime` 字段，表达的是同一件事，
但后者的修改成本更低、也更难写错。

### 4.9 决定九：为什么不加"按类型订阅"

真实需求一定会有：

- "我只想收直播通知，文章别烦我"；
- "邮件渠道不发直播"。

**候选方案**

```java
// 方案 A：给 publish 加过滤参数
DispatchReport publish(Event event, Predicate<Observer> filter);

// 方案 B：把过滤条件收进订阅关系
record Subscription(Observer observer, Predicate<Event> filter) { }
Map<String, Subscription> subscriptions;

// 方案 C：观察者自己在 onEvent 里过滤  ← 本项目选这个
public void onEvent(Event event) {
    if (event.type().realtime()) return;      // EmailObserver 的做法
    // ...
}
```

**方案 A 为什么是错的**

`filter` 的参数是 `Observer`，但它要过滤的显然是**事件类型**。
让调用方在每次 `publish` 时传一个"哪些观察者要发"的谓词，
等于把"订阅关系"变成了"一次性的临时决定"——
**订阅就不再是持久状态，而是每次调用的参数。**

而且这个谓词会跟 `Event` 的内容脱节：

```java
publish(liveEvent, o -> !(o instanceof EmailObserver));
// 每次都要写，写错一次就漏发
```

**方案 B 什么时候才需要**

当过滤条件是**平台级策略**时：

> "所有邮件渠道都不发直播" —— 这条规则应该由配置决定，
> 而不是每个 `EmailObserver` 各写一遍 `if (realtime) return;`。

那时 `Subscription` 的价值就出来了：
过滤条件跟着订阅走，注册时确定，之后不用管。

**方案 C 为什么在本示例里够用**

因为 `Event` 里已经带了 `type` 和 `type().realtime()`，
**观察者完全有能力自己判断**。

而且"邮件不发直播"这条规则，本质上是**邮件渠道的业务规则**，
不是平台规则。它就应该待在 `EmailObserver` 里。

**判断标准：**

> 过滤条件是"这个渠道自己的偏好"，还是"平台对所有渠道的统一约束"？
> 前者放观察者里，后者做成 `Subscription`。

本示例只有前者，所以不引入 `Subscription`。
但要把这条边界写清楚——**不做的理由，比做了更重要。**

### 4.10 决定十：十一个文件是怎么长出来的

把前面九个决定串起来，看文件是怎么一个一个出现的：

| 步骤 | 新增文件 | 被哪个问题逼出来 | 如果不拆会怎样 |
|---:|---|---|---|
| 0 | （一个 `for` 循环） | 起点 | —— |
| 1 | `Observer` | "通知谁"需要一个抽象 | Subject 依赖每个具体渠道 |
| 2 | `Event` + `EventType` | "通知什么"需要携带类型 | 加类型要改所有方法签名 |
| 3 | `Subject` | "名单归谁管"需要明确 | 注册方法散落在实现类里 |
| 4 | `ContentCreator` | Subject 的具体实现 | 无法创建对象 |
| 5 | `Delivery` | "每个人成没成"需要记录 | 失败只能靠异常冒泡 |
| 6 | `DispatchReport` | "整批成没成"需要汇总 | `publish` 只能返回 `void` |
| 7 | `InAppObserver` | 第一个渠道 | 没有可演示的观察者 |
| 8 | `EmailObserver` | 证明"渠道可以有不同规则" | 无法展示"直播不发邮件" |
| 9 | `PushObserver` | 证明"渠道会失败" | 无法展示失败隔离 |
| 10 | `Client` | 把五件事演示出来 | —— |

**十一个文件不是一个设计出来的，是十个问题逼出来的。**

这也是本项目反复强调的方法论：

> 不要先想"我要用观察者模式，所以我要建这几个类"，
> 而是先想"这段代码在什么情况下会坏"，
> 每一个坏法，对应一个类或一行代码。

反过来检验：**如果删掉某个文件，会出什么事？**

| 删掉 | 后果 |
|---|---|
| `Observer` | Subject 必须为每个渠道写一个 `if`，加渠道要改 Subject |
| `Event` | 通知参数退化成 `String`，类型信息丢失（回到原项目） |
| `EventType` | 无法表达"直播要实时"，也没法在编译期发现漏改 |
| `Subject` | 接口消失，调用方必须依赖 `ContentCreator` 具体类 |
| `Delivery` | 报告里只有数字，出问题查不到人 |
| `DispatchReport` | `publish` 只能返回 `void` 或 `int` |
| `EmailObserver` | 无法证明"不同渠道可以有不同处理" |
| `PushObserver` | 无法证明"失败隔离" |
| `Client` | 代码无法运行 |

**每一个文件删掉都会出具体的事**——这就是它该存在的理由。

---

## 5. 代码解析：每个文件为什么存在

### 5.1 全景

| 文件 | 物理行 | 代码行 | 一句话职责 | 删掉会怎样 |
|---|---:|---:|---|---|
| `EventType.java` | 64 | 18 | 三种事件类型 + 是否实时 | 类型变成魔法字符串，`switch` 不再穷尽 |
| `Event.java` | 93 | 30 | 不可变事件，自包含作者信息 | 通知退化成 `String`，类型信息丢失 |
| `Observer.java` | 120 | 6 | 观察者契约：ID + 名字 + 回调 | Subject 只能依赖每个具体渠道 |
| `Subject.java` | 99 | 7 | 被观察者契约：注册 + 注销 + 发布 | 调用方必须依赖 `ContentCreator` 具体类 |
| `Delivery.java` | 119 | 37 | 单次投递结果 | 报告里只剩数字，查不到是谁失败 |
| `DispatchReport.java` | 141 | 43 | 整次广播的汇总 | `publish` 只能返回 `void` |
| `ContentCreator.java` | 274 | 73 | 名单 + 快照 + 失败隔离 | 没有可运行的被观察者 |
| `InAppObserver.java` | 83 | 30 | 站内信渠道 | 没有最简单的观察者示例 |
| `EmailObserver.java` | 113 | 38 | 邮件渠道，跳过实时事件 | 无法展示"渠道可以有不同规则" |
| `PushObserver.java` | 144 | 47 | 推送渠道，通道可关闭 | 无法展示失败隔离 |
| `Client.java` | 285 | 125 | 五段 Demo | 代码无法运行 |
| **合计** | **1535** | **454** | | |

对比原项目：**144 物理行 / 74 代码行 → 1535 物理行 / 454 代码行**。

- 按物理行：**10.7 倍**
- 按代码行：**6.1 倍**

这个倍数不是"设计模式"带来的，是**七件事**带来的：

| 多出来的东西 | 代码行增量（估） | 属于模式还是工程 |
|---|---:|---|
| 三个渠道实现 | ~115 | 模式（演示一对多） |
| 投递结果 + 报告 | ~80 | **工程**（可观测性） |
| 事件 + 类型 | ~48 | 模式（事件建模） |
| 快照 + 失败隔离 | ~20 | **工程**（健壮性） |
| 全套 javadoc | ~700 | 教学 |
| 五段 Demo | ~125 | 教学 |

**真正的模式骨架只有 6 个文件、几十行代码**：

```
Observer（3 个方法）
Subject（4 个方法）
Event + EventType（数据）
ContentCreator.publish（15 行核心逻辑）
```

剩下的都是"让它能在生产环境活下来"的东西。

### 5.2 `EventType.java`：三种类型，两个属性

```java
public enum EventType {

    /** 文章：可以合并推送，晚几分钟无所谓。 */
    ARTICLE("文章", false),

    /** 视频：同文章，但通常带封面。 */
    VIDEO("视频", false),

    /** 直播：错过就没了，必须实时推送。 */
    LIVE("直播", true);

    private final String label;
    private final boolean realtime;
```

**为什么 `label` 和 `realtime` 都挂在枚举常量上？**

因为它们都是"这个类型固有的属性"，而不是"使用方临时决定的":

```java
// 好处一：文案改一处
event.type().label()        // "直播"

// 如果不用枚举，会写成
String type = "直播";        // 散落在 5 个文件里，改一次改 5 处
```

```java
// 好处二：规则变成数据
event.type().realtime()     // true

// 而不是
boolean realtime = (type == EventType.LIVE);   // 每个使用方各写一遍
```

第二条更关键。假设明天加了一种 `FLASH_SALE("限时秒杀", true)`：

- 用 `realtime()` 的代码：**一行都不用改**；
- 用 `type == LIVE` 的代码：**每一处都要改**，而且漏一处就出 bug，
  编译器还不报错（`==` 是合法表达式，只是结果不对）。

**这就是"把 if 变成数据"的价值。** 它不是风格问题，是**修改成本的量级差异**。

**为什么不用 `String` 常量？**

```java
public static final String ARTICLE = "article";
public static final String LIVE = "live";
```

三个问题：

1. **没有 `switch` 穷尽性**。用 `String` 的 `switch` 不需要覆盖所有值，
   加一个新类型，编译器不会提醒你"这里漏了"；
2. **可以传错值**。`publish("lvie", ...)` 是合法调用，运行时才发现；
3. **不能挂附加数据**。`"直播"` 和 `true` 得分成两个常量组维护，
   迟早对不上。

### 5.3 `Event.java`：一个会自己校验的信封

```java
public record Event(
        EventType type,
        String authorId,
        String authorName,
        String title,
        String content
) {
    public Event {
        Objects.requireNonNull(type, "事件类型不能为 null");
        requireText(authorId, "作者 ID");
        requireText(authorName, "作者名");
        requireText(title, "标题");
        content = (content == null) ? "" : content;
    }
```

**逐行看这个紧凑构造器**

| 行 | 做了什么 | 不做会怎样 |
|---|---|---|
| `requireNonNull(type, ...)` | 类型非空 | `describe()` 里 `type.label()` 空指针 |
| `requireText(authorId, ...)` | 作者 ID 非空非空白 | 报告里出现 `→  张三`（ID 是空串） |
| `requireText(authorName, ...)` | 作者名非空非空白 | 文案里出现 `：《标题》` 前面没名字 |
| `requireText(title, ...)` | 标题非空非空白 | 文案里出现 `《》`，像排版 bug |
| `content = (content == null) ? "" : content` | 正文允许为空，`null` 归一成 `""` | `summary()` 里空指针 |

**为什么 `content` 允许为空，其他四个不允许？**

因为**直播没有正文**：

```java
public DispatchReport publishLive(String title) {
    return publish(new Event(EventType.LIVE, authorId, authorName, title, ""));
}
```

一条"今晚八点开播"的事件，本来就没有文字内容。
如果强行要求 `content` 非空，调用方会传一个 `" "`（空格）来绕过校验——
**校验被绕过的那一刻，它就失效了**。

而 `title` 必须非空：一条没有标题的事件，通知文案无从写起。

**为什么 `content` 归一成 `""` 而不是保留 `null`？**

```java
public String summary() {
    return content.isEmpty() ? "（无正文）" : content;
}
```

如果保留 `null`，`summary()` 要先判 `null` 再判 `isEmpty`：

```java
if (content == null || content.isEmpty()) { ... }
```

两处判断，任何一处漏掉就空指针。
**在入口把 `null` 归一成一个确定的合法值，下游就只需要处理一种情况。**

这条原则和"数据库字段 `NOT NULL DEFAULT ''`"是同一个思路。

**`describe()`：一处生成，三处复用**

```java
public String describe() {
    String realtimeMark = type.realtime() ? "[实时]" : "";
    return "[" + type.label() + "]" + realtimeMark + " "
            + authorName + "：《" + title + "》";
}
```

输出形如：

```text
[文章] hgl：《设计模式到底在解决什么》
[直播][实时] hgl：《今晚八点聊聊观察者模式》
```

三个观察者、报告标题行，全部调用同一个方法。
**如果哪天文案要改成「hgl 发布了《...》」，改一处就够了。**

**为什么 `describe()` 和 `summary()` 不做成字段？**

因为它们是**派生数据**：

```java
String describe;      // 存起来 → type/title 改了它不会变 → 数据不一致
```

派生数据的唯一安全形态是"每次从源数据算出来"。这也是 `record`
不生成 setter 的另一个好处——源数据不会变，派生结果就永远正确。

### 5.4 `Observer.java`：只有三个方法

```java
public interface Observer {
    String id();
    String displayName();
    void onEvent(Event event);
}
```

三个方法对应三个"谁在用"：

| 方法 | 谁在用 | 如果删掉 |
|---|---|---|
| `id()` | `ContentCreator` 的 `Map` 键、`unsubscribe` 的参数、报告的归因 | 只能按 `equals` 去重，同名观察者互相干扰 |
| `displayName()` | `Delivery.describe()`、报告打印 | 报告里只有 `PushObserver@1a2b3c` |
| `onEvent(Event)` | `ContentCreator.publish` 的回调 | 观察者没有存在意义 |

**注意这里没有什么：**

- 没有 `follow(Subject)` / `unfollow(Subject)`——订阅是 Subject 的事；
- 没有 `getType()` / `supports(EventType)`——过滤条件放 `onEvent` 里；
- 没有 `onSubscribe()` / `onUnsubscribe()`——生命周期钩子在本示例中用不到；
- 没有 `close()` / `shutdown()`——资源清理应该由实现类的生命周期管理负责。

**接口里每多一个方法，就多一份所有实现都要承担的契约。**
`Observer` 只有 3 个方法，所以 `MockObserver` 在测试里 5 行就能写完：

```java
class MockObserver implements Observer {
    List<Event> received = new ArrayList<>();
    public String id() { return "mock"; }
    public String displayName() { return "Mock"; }
    public void onEvent(Event event) { received.add(event); }
}
```

**`onEvent` 的 javadoc 里写了三条契约**，这是接口文档最重要的部分：

```text
1. 本方法可能被多个线程并发调用，实现应该无状态或自行保证线程安全；
2. 不得抛出 Error；抛 RuntimeException 视为投递失败，由 Subject 捕获；
3. 不要在这里调用 subject.subscribe(...) 期待"立刻收到本次事件"。
```

第 3 条尤其重要。它把 4.4 节讨论的"快照语义"**写进了契约**，
而不是留在实现里让每个人自己猜。

### 5.5 `Subject.java`：四个方法，两种返回值

```java
public interface Subject {
    void subscribe(Observer observer);
    boolean unsubscribe(String observerId);
    int subscriberCount();
    DispatchReport publish(Event event);
}
```

**为什么 `subscribe` 返回 `void`，`unsubscribe` 返回 `boolean`？**

这不是随意的不对称，而是**语义不同**：

| | 语义 | 重复调用时 | 调用方关心什么 |
|---|---|---|---|
| `subscribe` | "确保他在名单里"（幂等） | 无副作用 | 不关心之前有没有 |
| `unsubscribe` | "把他从名单里去掉" | 第二次返回 `false` | **关心到底删掉了没有** |

具体到业务：

```java
// 前端点"关注" → 重复点两次，不该报错
POST /follow    →  subscribe()    →  幂等，永远成功

// 前端点"取消关注" → 要更新按钮状态
POST /unfollow  →  unsubscribe()  →  false 说明本来就没关注
                                   →  可能是前端状态错了，值得记一笔日志
```

**如果 `unsubscribe` 也返回 `void`：**

```java
subject.unsubscribe("fan-9999");     // 这个人根本不存在
// 调用方以为成功了，前端按钮变灰，但实际什么也没发生
```

**为什么 `subscribe` 不返回 `boolean`（"是否新增"）？**

因为它会诱导调用方写出这种代码：

```java
if (subject.subscribe(observer)) {
    sendWelcomeEmail();              // 第一次关注才发欢迎邮件
}
```

"是不是第一次"是**业务判断**，应该由业务层查关注关系表来决定，
而不是让 Subject 兼职做"关注关系查询服务"。

**`subscriberCount()` 为什么在接口里？**

它看起来像"为了测试而存在的方法"，但它的用途是真实的：

- Demo 里打印"当前 3 位订阅者"；
- 运营后台显示"这个作者有多少粉丝"；
- 健康检查里判断"名单是不是被意外清空了"。

如果接口里没有它，调用方只能反射读私有字段——那才是真的为测试污染设计。

**`publish` 的契约写了三条**，这三条是整个设计的核心：

```text
1. 先快照再遍历——中途订阅收不到本次，中途退订不会抛 CME；
2. 单个观察者抛 RuntimeException 只影响它自己；
3. 不捕获 Error。
```

**接口的 javadoc 是唯一能同时约束"所有实现"和"所有调用方"的地方。**
如果这三条只写在 `ContentCreator` 的注释里，
将来写一个 `ContentCreatorV2` 就没人知道还要遵守它们。

### 5.6 `Delivery.java`：一条投递结果

```java
public record Delivery(
        String observerId,
        String observerName,
        boolean succeeded,
        String errorMessage
) {
    public Delivery {
        Objects.requireNonNull(observerId, "观察者 ID 不能为 null");
        Objects.requireNonNull(observerName, "观察者展示名不能为 null");
        if (succeeded && errorMessage != null) {
            throw new IllegalArgumentException("投递成功时不应携带失败原因：" + observerId);
        }
        if (!succeeded && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("投递失败时必须给出失败原因：" + observerId);
        }
    }
```

**这两条校验为什么重要？**

它们钉死了一条不变量：

```text
succeeded == true   ⟺   errorMessage == null
```

没有这条约束，就会出现四种组合：

| `succeeded` | `errorMessage` | 是否合法 | 打印出来 |
|---|---|---|---|
| `true` | `null` | ✓ | `[成功] 张三` |
| `false` | `"超时"` | ✓ | `[失败] 张三 — 超时` |
| `true` | `"超时"` | **✗** | `[成功] 张三`（错因被丢弃，自相矛盾） |
| `false` | `null` | **✗** | `[失败] 张三 — null`（不可读） |

后两种一旦进入报告，就是"报告自己说自己"——**读者永远不知道该信哪一半**。

**为什么提供 `ok()` / `failed()` 静态工厂？**

```java
public static Delivery ok(Observer observer) {
    return new Delivery(observer.id(), observer.displayName(), true, null);
}

public static Delivery failed(Observer observer, RuntimeException cause) {
    String message = cause.getMessage();
    String reason = cause.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    return new Delivery(observer.id(), observer.displayName(), false, reason);
}
```

调用方只需要写：

```java
deliveries.add(Delivery.ok(observer));
deliveries.add(Delivery.failed(observer, e));
```

对比直接 `new`：

```java
new Delivery(observer.id(), observer.displayName(), true, null)
//                                                       ↑
//                                    这个 null 每次都要调用方自己记住
```

**工厂方法把"成功时传 null"这条知识收进了 `Delivery` 自己。**

**`failed()` 里为什么拼"异常类名 + 消息"？**

```java
String reason = cause.getClass().getSimpleName()
        + (message == null || message.isBlank() ? "" : ": " + message);
```

产出：

```text
IllegalStateException: 推送通道已关闭：device-l4
```

- **类名**告诉排查者"是哪一类故障"——`IllegalStateException`
  通常意味着环境问题（可以重试），`IllegalArgumentException`
  通常意味着代码 bug（重试没用）；
- **消息**告诉排查者"具体是什么"。

**为什么不存 `Throwable`？**

因为报告会被打印、被序列化、被塞进日志系统。
一个 `Throwable` 持有：

- 堆栈（几十个 `StackTraceElement`）；
- `cause` 链（可能还有好几层）；
- 每个栈帧引用的局部变量（可能包括 `Connection`、`byte[]`、整个请求体）。

把一批这样的对象放进一个报告、再传给上层、再缓存起来——
**每一个 `Throwable` 都是一个潜在的内存泄漏点。**

需要堆栈的时候去日志里找——那是日志系统该干的事。

**`describe()`：一行式输出**

```java
public String describe() {
    if (succeeded) {
        return "[成功] " + observerName;
    }
    return "[失败] " + observerName + " — " + errorMessage;
}
```

注意这里**不打印 `observerId`**。因为报告是给人看的，ID 对人不友好。
但也正因为 `observerId` 在记录里存在，Demo 2 才能用它区分两个同名的张三：

```java
for (Delivery delivery : report.deliveries()) {
    System.out.println("    " + delivery.observerId() + " → " + delivery.observerName());
}
```

**"给人看的"和"给机器用的"同时存在，各走各的路。**
这是 `displayName()` 和 `id()` 分成两个方法的原因，也是报告里两个字段都存的原因。

### 5.7 `DispatchReport.java`：整次广播的汇总

```java
public record DispatchReport(
        Event event,
        List<Delivery> deliveries,
        int succeeded,
        int failed
) {
    public DispatchReport {
        Objects.requireNonNull(event, "事件不能为 null");
        deliveries = List.copyOf(deliveries);

        long okCount = deliveries.stream().filter(Delivery::succeeded).count();
        long failCount = deliveries.size() - okCount;
        if (okCount != succeeded || failCount != failed) {
            throw new IllegalArgumentException(
                    "统计数字与明细不符：声明 " + succeeded + " 成功 / " + failed
                            + " 失败，实际 " + okCount + " 成功 / " + failCount + " 失败");
        }
    }
```

**`List.copyOf` 那一行，是整份设计里最容易写错的一行**

三种常见写法，只有一种是真拷贝：

```java
// 写法一：直接存引用 —— 危险
this.deliveries = deliveries;
// 调用方在构造后 add 一条 → 明细变了，succeeded 没变 → 报告自相矛盾

// 写法二：不可变视图 —— 不够
this.deliveries = Collections.unmodifiableList(deliveries);
// 你不能改，但原 List 被别处改时，这里会跟着变
// unmodifiableList 只是"禁止通过这个引用修改"，不是"冻结内容"

// 写法三：真拷贝 —— 正确
this.deliveries = List.copyOf(deliveries);
// 复制出一个新的不可变列表，原 List 怎么变都与它无关
```

**这是一个极容易被忽略的细节**：`Collections.unmodifiableList`
返回的是一个**包装器**（wrapper），它内部持有原列表的引用。
原列表一旦被修改，包装器看到的内容也会变。

```java
List<String> src = new ArrayList<>(List.of("A"));
List<String> view = Collections.unmodifiableList(src);
src.add("B");
System.out.println(view);        // [A, B]  ← 视图跟着变了
```

而 `List.copyOf`：

```java
List<String> copy = List.copyOf(src);
src.add("C");
System.out.println(copy);        // [A, B]  ← 不变
```

**"报告是那一刻的事实"，这个语义要求真拷贝。**

**为什么还有 `of()` 这个静态工厂**

```java
public static DispatchReport of(Event event, List<Delivery> deliveries) {
    List<Delivery> copy = List.copyOf(deliveries);
    int ok = (int) copy.stream().filter(Delivery::succeeded).count();
    return new DispatchReport(event, copy, ok, copy.size() - ok);
}
```

因为让调用方自己数数**一定会数错**：

```java
// 没有 of() 时，调用方要这么写
int ok = 0;
for (Delivery d : deliveries) {
    if (d.succeeded()) ok++;
}
return new DispatchReport(event, deliveries, ok, deliveries.size() - ok);
//                                                              ↑
//                                              5 行重复逻辑，每处都可能写错
```

`of()` 把"从明细算统计"这件事收进了报告自己。

**为什么两个数字还要做成字段？**

如果是现算的：

```java
public int succeeded() {
    return (int) deliveries.stream().filter(Delivery::succeeded).count();
}
```

就不会有"数字与明细不符"的可能。但项目仍然选择存字段 + 构造校验，原因是：

1. **O(1) 读取**。报告会被反复读（打印、打点、告警），每次 O(n) 不值；
2. **校验即文档**。那段 `if` 明确告诉后来的维护者：
   "这三个数字必须自洽"，而不是"随便传，反正能算"。

**`attempted()` 和 `allSucceeded()` 为什么做成方法？**

```java
public int attempted() {
    return deliveries.size();
}

public boolean allSucceeded() {
    return failed == 0;
}
```

因为它们可以从已有字段派生，加字段是冗余的。
而 `succeeded` / `failed` 必须存，是因为它们是**外部传入的**（
在 `of()` 之外，`new DispatchReport(...)` 仍然允许直接构造）。

**这是一个可以讲清楚的取舍**：

> 能从字段派生的，做成方法（`attempted`、`allSucceeded`）；
> 需要外部传入并校验的，做成字段（`succeeded`、`failed`）。

**`allSucceeded()` 的空真问题**

没有订阅者时，`failed == 0`，`allSucceeded()` 返回 `true`。

这是数学上的"空真"（vacuous truth）："所有人都成功了"在"没有人"时成立。
但在业务上可能不是想要的：

```java
if (report.allSucceeded()) {
    log.info("推送全部成功");           // 实际一个都没发
}
```

所以 javadoc 里明确写了这条，并指出正确做法：

```java
if (report.attempted() == 0) {
    log.warn("没有任何订阅者");          // 这才是想表达的意思
}
```

**`describeAsText()`：为什么不直接用 `toString()`**

`record` 自动生成的 `toString()` 会长这样：

```text
DispatchReport[event=Event[type=ARTICLE, ...], deliveries=[Delivery[...], Delivery[...]], succeeded=3, failed=0]
```

六个人就是六条 `Delivery` 挤在一行里。终端里根本读不了。

所以自定义一个多行渲染：

```java
public String describeAsText() {
    StringBuilder sb = new StringBuilder();
    sb.append("投递报告｜").append(event.describe()).append('\n');
    for (Delivery delivery : deliveries) {
        sb.append("  ").append(delivery.describe()).append('\n');
    }
    sb.append("  合计：尝试 ").append(attempted())
            .append(" 人，成功 ").append(succeeded)
            .append(" 人，失败 ").append(failed).append(" 人");
    return sb.toString();
}
```

输出：

```text
投递报告｜[文章] hgl：《失败隔离》
  [成功] 张三
  [失败] 李四 — IllegalStateException: 推送通道已关闭：device-l4
  [成功] 王五
  合计：尝试 3 人，成功 2 人，失败 1 人
```

**注意最后一行没有 `\n`**，这样调用方 `System.out.println(report.describeAsText())`
不会多出一个空行。

这是一个小细节，但它体现了"方法要为自己被怎么调用负责"。

---

### 5.8 `ContentCreator.java`：15 行核心逻辑

这是全场唯一的"实现类"，也是唯一同时承担两个角色的类：

```java
public class ContentCreator implements Subject {
    private final String authorId;                                  // 第 67 行
    private final String authorName;                                // 第 68 行
    private final Map<String, Observer> observers = new LinkedHashMap<>();   // 第 76 行
```

**为什么这三行字段的顺序值得说？**

因为它们体现了三个"不可变"的层次：

| 字段 | 可变性 | 为什么 |
|---|---|---|
| `authorId` | `final` + 构造注入 | 作者身份一生不变 |
| `authorName` | `final` + 构造注入 | 本示例不支持改名（真实系统用 setter + 校验） |
| `observers` | `final` **引用**，但内容可变 | 名单本身必须能增删 |

`final` 加在 `Map` 上，锁的是**引用不能换**，不是**内容不能改**。
这是一个常见的误解：

```java
final Map<String, Observer> observers = new LinkedHashMap<>();
observers = new LinkedHashMap<>();        // ✗ 编译错误
observers.put("x", y);                    // ✓ 合法
```

**如果哪天需要换实现，想"换掉整个 Map"是做不到的**——这正是想要的：
名单只会被增删，不会被整体替换。

**`subscribe`：`putIfAbsent` 而不是 `put`**

```java
@Override
public void subscribe(Observer observer) {
    Objects.requireNonNull(observer, "观察者不能为 null");
    String id = Objects.requireNonNull(observer.id(), "观察者 ID 不能为 null");
    observers.putIfAbsent(id, observer);
}
```

三个细节：

**① 两次 `requireNonNull`**

第一次查 `observer` 本身，第二次查 `observer.id()`。
如果只查第一次，一个 `id()` 返回 `null` 的实现会让 `LinkedHashMap`
接受一个 `null` 键（它允许），然后在 `unsubscribe(null)` 时行为诡异。

**校验要查到"真正被使用的那一层"。**

**② `putIfAbsent` vs `put`**

```java
observers.put(id, observer);           // 后来者覆盖先来者
observers.putIfAbsent(id, observer);   // 先来者保留
```

差别在"同一个用户在新设备上订阅"这个场景：

```java
creator.subscribe(new InAppObserver("fan-2001", "张三"));          // 手机
creator.subscribe(new InAppObserver("fan-2001", "张三（新设备）"));  // 平板
```

- 用 `put`：名单里是"张三（新设备）"，手机那个实例被**静默丢弃**。
  如果两个实例持有不同的状态（比如不同的重试计数、不同的连接），
  就会出现"注册了 A，通知却发给了 B"；
- 用 `putIfAbsent`：保留第一个，"张三（新设备）"的订阅请求被**幂等地忽略**。

**Demo 2 里那行「张三（新设备）没能顶掉原来的 fan-2001」就是在验证这一点。**

**③ 不返回任何东西**

`subscribe` 的返回值在 5.5 节已经论证过：幂等操作不需要回报。

**`unsubscribe`：返回值的来源**

```java
@Override
public boolean unsubscribe(String observerId) {
    Objects.requireNonNull(observerId, "观察者 ID 不能为 null");
    return observers.remove(observerId) != null;
}
```

`Map.remove(key)` 返回被删掉的值，键不存在时返回 `null`。

**为什么不能写成这样：**

```java
observers.remove(observerId);
return true;                      // ✗ 永远返回 true
```

因为调用方需要这个信号：

```java
if (!subject.unsubscribe("fan-9999")) {
    log.warn("fan-9999 本来就没订阅，可能是前端状态不同步");
}
```

**一个不能失败的方法，是没法被用来做诊断的。**

**`publish`：全篇最重要的 15 行**

```java
@Override
public DispatchReport publish(Event event) {
    Objects.requireNonNull(event, "事件不能为 null");              // 第 187 行

    List<Observer> snapshot = new ArrayList<>(observers.values());  // 第 189 行
    List<Delivery> deliveries = new ArrayList<>(snapshot.size());   // 第 190 行

    for (Observer observer : snapshot) {                            // 第 192 行
        try {
            observer.onEvent(event);                                // 第 194 行
            deliveries.add(Delivery.ok(observer));                  // 第 195 行
        } catch (RuntimeException e) {                              // 第 196 行
            deliveries.add(Delivery.failed(observer, e));           // 第 197 行
        }
    }

    return DispatchReport.of(event, deliveries);                    // 第 201 行
}
```

逐行拆解：

| 行 | 代码 | 作用 | 删掉会怎样 |
|---:|---|---|---|
| 187 | `requireNonNull(event, ...)` | 入口校验 | `null` 事件传到每个观察者，炸 N 次 |
| 189 | `new ArrayList<>(observers.values())` | **快照** | 观察者中途退订 → CME 或静默漏发 |
| 190 | `new ArrayList<>(snapshot.size())` | 预分配容量 | 不影响正确性，只是扩容几次 |
| 192 | `for (Observer observer : snapshot)` | 遍历**副本** | 改成 `observers.values()` 就把 189 行白做了 |
| 193 | `try {` | **在循环内** | `try` 挪到循环外 → 一个失败全链断 |
| 194 | `observer.onEvent(event)` | 回调 | 没有通知 |
| 195 | `Delivery.ok(observer)` | 记成功 | 报告里只有失败，没有总数 |
| 196 | `catch (RuntimeException e)` | 只捕获运行时异常 | 改成 `Exception` 会连受检异常一起吞（虽不可能发生）；改成 `Throwable` 会吞 `Error` |
| 197 | `Delivery.failed(observer, e)` | 记失败 | 失败被静默丢弃 |
| 201 | `DispatchReport.of(...)` | 汇总 | `publish` 只能返回 `void` |

**第 189 行和第 192 行的配对，是最容易出错的地方。**

新手常见的改法：

```java
for (Observer observer : observers.values()) {      // 忘了用 snapshot
    try {
        observer.onEvent(event);
```

`snapshot` 变量还在，但**没人用了**。编译器不报错（它被赋值了），
IDE 可能提示"变量未使用"，但很多人会忽略这条提示。

**这就是为什么第 189 行和第 192 行必须一起讲**——
它们不是两个独立的设计，是一个设计的两个端点。

**为什么 `deliveries` 要预分配 `snapshot.size()`？**

因为最终每个观察者都会产生恰好一条 `Delivery`（成功或失败各一条）。
预分配避免 `ArrayList` 扩容时的数组复制。

**这条不是必须的**，但它是"我知道这个循环一定会产出 n 条记录"的直接表达。

**为什么 `catch` 里不重新抛？**

```java
} catch (RuntimeException e) {
    deliveries.add(Delivery.failed(observer, e));
    throw e;                                  // ✗ 又回到原项目的老路
}
```

重新抛会让"一个失败中断全链"重新出现。
**唯一的例外是 `Error`**——但它根本不会进这个 `catch`（见 4.6）。

**三个便捷发布方法**

```java
public DispatchReport publishArticle(String title, String content) {
    return publish(new Event(EventType.ARTICLE, authorId, authorName, title, content));
}

public DispatchReport publishVideo(String title, String content) {
    return publish(new Event(EventType.VIDEO, authorId, authorName, title, content));
}

public DispatchReport publishLive(String title) {
    return publish(new Event(EventType.LIVE, authorId, authorName, title, ""));
}
```

**它们的存在理由不是"少写几个字"，而是"消除一类错误"：**

如果没有它们，每个调用点都要写：

```java
creator.publish(new Event(EventType.ARTICLE, creator.authorId(), creator.authorName(), title, content));
//                                                                          ↑
//                                                    这里可能写成 "hgl" 硬编码，或者传错 ID
```

三个便捷方法把 `authorId` / `authorName` 的填法**收进了唯一的正确位置**。

**注意 `publishLive` 的签名只有 `title`**，没有 `content`。
这不只是为了方便——**签名本身就是约束**：

```java
publishLive("今晚八点", "随便写点什么");      // ✗ 编译不通过
```

"直播没有正文"这条业务规则，被写进了方法签名里，
**调用方想犯错都没有机会**。

**这是比 `if` 校验更强的约束**：`if` 在运行时才生效，签名在编译期就拦住了。

**为什么要实现 `Subject` 接口，而不是直接用这个类？**

```java
ContentCreator creator = new ContentCreator("author-hgl", "hgl");     // 具体类
Subject subject = creator;                                            // 接口
```

实现接口的价值在**调用方**：

```java
// 一个只管发布、不关心是谁在发布的工具方法
static void broadcastTo(Subject subject, Event event) {
    subject.publish(event);
}
```

`broadcastTo` 的参数是 `Subject`，所以它能接受任何实现——
将来写一个 `Newsletter`（邮件列表）或者 `LiveRoom`（直播间），
这个方法一行都不用改。

**如果 `ContentCreator` 不实现接口**，这个方法只能写成
`broadcastTo(ContentCreator creator, ...)`，就永远绑死了。

### 5.9 `InAppObserver.java`：最标准的观察者

```java
public class InAppObserver implements Observer {

    private final String id;
    private final String displayName;

    @Override
    public void onEvent(Event event) {
        Objects.requireNonNull(event, "事件不能为 null");
        System.out.println("  [站内信] " + displayName + " ← " + event.describe()
                + "｜" + event.summary());
    }
}
```

**它证明了一件事：一个观察者可以非常简单。**

三个字段、三个方法、一行打印。没有 `Subject` 引用，
没有集合操作，没有异常处理——**这些都不该由观察者负责**。

**对比原项目的 `ConcreteFollower`：**

| | 原项目 `ConcreteFollower` | 本项目 `InAppObserver` |
|---|---|---|
| 实现的接口 | `Follower` + `Followable` | 只有 `Observer` |
| 方法数 | 3（`update` / `follow` / `unfollow`） | 3（`id` / `displayName` / `onEvent`） |
| 是否知道 Subject | **是**（要强转 `ConcreteUser`） | **否** |
| 是否操作别人的集合 | **是** | 否 |
| 需要的字段 | `name` | `id` + `displayName` |

**方法数一样，但承担的责任完全不同。**

原项目的 3 个方法里，有 2 个是"操作别人"；
本项目的 3 个方法全部是"描述自己"和"处理事件"。

**这就是"职责单一"的具体样子**：不是方法少，是**每个方法都在说自己**。

**为什么 `onEvent` 里要 `requireNonNull(event)`？**

因为 `Observer` 是 `public` 的，它的实现可能被任何代码直接调用
（不只是 `ContentCreator`）：

```java
observer.onEvent(null);      // 直接调用，绕过 Subject
```

在入口挡住 `null`，错误信息会直接指向调用方。
如果不挡，`event.describe()` 会在下一行抛出
`NullPointerException`，堆栈指向 `InAppObserver`——
**排查者会以为是观察者的问题，实际上是调用方传了 `null`。**

**防御性校验的价值不只是"不崩"，更是"崩在正确的位置"。**

**为什么输出里带 `"  "`（两个空格）？**

因为它在报告里是一段"投递明细"，视觉上应该缩进。
三个观察者的输出都以两个空格开头，拼在一起就是：

```text
  [站内信] 张三 ← [文章] hgl：《...》｜...
  [邮件] 李四(lisi@example.com) ← [文章] hgl：《...》｜...
  [推送/普通] device-w5 ← [文章] hgl：《...》｜...
```

**输出格式的一致性，靠的是"每个实现都遵守同一个约定"**，
而不是靠 Subject 统一渲染——那会破坏"每个渠道自主决定呈现方式"的原则。

这是一个可以争论的取舍：

- **让观察者自己打印**：渠道可以完全自定义格式（本项目选这个）；
- **让观察者返回字符串，Subject 统一打印**：格式统一，但观察者
  被迫把"结果"变成"文本"，而真实系统里它应该是"写库""调 API"。

本示例是教学代码，打印够用，所以选前者。

### 5.10 `EmailObserver.java`：一个有自己规则的渠道

```java
public class EmailObserver implements Observer {

    private final String id;
    private final String displayName;
    private final String email;              // ← 观察者自己的状态

    @Override
    public void onEvent(Event event) {
        Objects.requireNonNull(event, "事件不能为 null");
        if (event.type().realtime()) {
            return;                           // ← 直播不发邮件
        }
        System.out.println("  [邮件] " + displayName + "(" + email + ") ← "
                + event.describe() + "｜" + event.summary());
    }
}
```

**多出来的 `email` 字段，说明了一件重要的事**

观察者**可以有自己的状态**，只要这份状态与"事件长什么样"无关。

| 状态 | 属于观察者？ | 为什么 |
|---|---|---|
| 邮箱地址 | ✓ | "这个人怎么被联系到"，与事件无关 |
| 设备 ID | ✓ | 同上 |
| 重试计数 | ✓ | 渠道自己的运行状态 |
| "这条事件属于哪个话题" | ✗ | 从事件算出来的，应该待在 `Event` 里 |
| "当前事件的作者是谁" | ✗ | 同上 |

**判断标准：这个字段的值，会不会因为换了另一条事件而改变？**

- 邮箱地址：不会（同一个用户永远是同一个邮箱）→ 属观察者；
- 作者名：会（换一条事件作者就变了）→ 属事件。

**这一条如果搞反，就会出现"观察者缓存了上一条事件的数据"这种极难排查的 bug。**

**为什么直播不发邮件**

```java
if (event.type().realtime()) {
    return;
}
```

直播是"错过就没了"的事件。一封邮件从发送到被打开平均要几小时，
等用户看到，直播早结束了。**发出去只是制造一条垃圾邮件。**

但注意这里**没有打印"跳过"**，也**没有抛异常**：

- 不打印：避免每次直播都刷一行"邮件跳过"，污染日志；
- 不抛异常：跳过不是失败。

**这是一个很容易做错的判断。** 很多人会写成：

```java
if (event.type().realtime()) {
    throw new IllegalStateException("直播不发邮件");     // ✗ 大错
}
```

后果：报告里每次直播都有一条 `[失败] 李四 — 直播不发邮件`。
明明一切正常，告警却在响。

**"不想发"和"发不出去"是两件事。**
前者是策略，后者是故障。**只有后者该进失败列表。**

**为什么判断用 `realtime()` 而不是 `== LIVE`**

```java
if (event.type().realtime()) { ... }        // ✓
if (event.type() == EventType.LIVE) { ... } // ✗ 将来加新类型要改
```

如果明天加一种 `FLASH_SALE("限时秒杀", true)`，
第一行自动生效，第二行漏掉。

**这是"规则即数据"的第二个收益点**（第一个在 5.2 的 `EventType`）。
它出现在这里，说明这个设计真的被用上了，不是为了演示而演示。

**这个类证明了观察者模式的另一半价值**

前面说观察者模式的价值是"发布方不需要知道接收方"。这个类展示了**另一半**：

> **接收方也不需要知道彼此。**

`EmailObserver` 不知道 `PushObserver` 存在，更不知道
`PushObserver` 对直播的处理是"必须立刻发"——**正好相反**。

两条互相矛盾的规则，住在两个互不相干的类里，
加第三个渠道时谁都不用改。

**如果这两条规则写在 `publish` 里：**

```java
for (Observer o : snapshot) {
    if (event.type() == LIVE && o instanceof EmailObserver) continue;
    if (event.type() == LIVE && o instanceof PushObserver) highPriority(o);
    // 加第四个渠道 → 再加一个 if
}
```

`publish` 会变成一个越来越长的 `if` 塔，
而且**每加一个渠道都要改 Subject**——模式的意义完全丧失。

### 5.11 `PushObserver.java`：一个会失败的渠道

```java
public class PushObserver implements Observer {

    private final String id;
    private final String displayName;
    private final String deviceId;
    private final boolean channelOpen;          // ← 通道是否可用

    @Override
    public void onEvent(Event event) {
        Objects.requireNonNull(event, "事件不能为 null");
        if (!channelOpen) {
            throw new IllegalStateException("推送通道已关闭：" + deviceId);
        }
        String priority = event.type().realtime() ? "实时" : "普通";
        System.out.println("  [推送/" + priority + "] " + deviceId + " ← "
                + event.describe() + "｜" + event.summary());
    }
}
```

**注意这个类里没有 `try-catch`**

这是刻意的。观察者**不应该自己兜底**：

```java
// 错误写法：自己吞掉
public void onEvent(Event event) {
    try {
        doSend(event);
    } catch (Exception e) {
        System.out.println("发送失败");        // 然后呢？发布方永远不知道
    }
}
```

自己吞掉的后果：

- `publish` 的报告里这一条记为**成功**；
- 监控看到 100% 成功率；
- 用户投诉"没收到推送"，翻日志只有一行中文。

**观察者只该如实报告"我失败了"，兜底是 Subject 的责任。**

**为什么抛 `IllegalStateException`**

异常类型是一种**分类信息**：

| 异常 | 含义 | 该怎么办 |
|---|---|---|
| `IllegalArgumentException` | 调用方传错了参数 | 修代码，重试没用 |
| `NullPointerException` | 少了必要数据 | 同上 |
| `IllegalStateException` | 对象当前状态不允许这个操作 | **环境问题，重试可能成功** |
| `UnsupportedOperationException` | 这个实现不支持该操作 | 选错了实现 |

"推送通道已关闭"明显是第三类：对象本身没被用错，只是它现在不可用。

**选对异常类型，等于给排查者留了一条分类线索。**
如果所有地方都抛 `RuntimeException("出错了")`，日志里就只有一堆"出错了"。

**为什么 `channelOpen` 是构造参数，不是 setter**

```java
public PushObserver(String id, String displayName, String deviceId, boolean channelOpen) { ... }

public static PushObserver closed(String id, String displayName, String deviceId) {
    return new PushObserver(id, displayName, deviceId, false);
}
```

setter 会引入可变状态：

- 这个类就不再是"初始化后只读"的；
- 线程安全要重新考虑（`channelOpen` 得加 `volatile`）；
- 测试变得不确定（同一个对象，前一个测试改了状态，后一个测试受影响）。

**而本示例要演示的只有一件事：一个观察者坏了，其他人不受影响。**
一个不可变的"坏观察者"就足够说明问题。

**静态工厂 `closed()` 的价值**

```java
new PushObserver("fan-4002", "李四", "device-l4", false)      // 第四个参数是什么？
PushObserver.closed("fan-4002", "李四", "device-l4")          // 一眼看出
```

布尔参数在调用点永远是谜。工厂方法把意图写进了名字。

**这是一个小技巧，但在代码审查里非常有效**——
审查者看到 `closed(...)` 就知道"这是故意弄坏的"，
不会误以为"这段输出是跑错了"。

**`[推送/实时]` 和 `[推送/普通]`**

```java
String priority = event.type().realtime() ? "实时" : "普通";
```

同样用 `realtime()` 而不是 `== LIVE`。

但注意：这里它**不跳过**非实时事件，只是降低优先级。
**同一个 `realtime()` 判断，在两个渠道里得到了完全不同的使用方式：**

- `EmailObserver`：实时 → 跳过；
- `PushObserver`：实时 → 升优先级。

**这就是"数据"和"策略"分离的样子。**
`EventType` 只负责回答"是不是实时"，
"实时该怎么处理"由每个渠道自己决定。

### 5.12 `Client.java`：五段 Demo

```java
public final class Client {

    private Client() { }

    public static void main(String[] args) {
        demo1ThreeChannels();
        demo2IdentityIsId();
        demo3UnsubscribeDuringPublish();
        demo4FailureIsolation();
        demo5EventTypeDrivesChannel();

        System.out.println("五段 Demo 对应原项目的五个问题：");
        // ...
    }
}
```

**为什么是 `final` + 私有构造器？**

一个只有静态方法的类不应该被实例化：

```java
new Client();      // 毫无意义
```

`final` 阻止继承（继承一个没有实例状态的类没有意义），
私有构造器阻止实例化。**这是工具类的标准写法。**

**这和外层目录里那些 `Client` 不一样吗？**

原项目的 `Client` 是一个普通类，`main` 里写全部逻辑。
本项目把它拆成 5 个私有方法，每个对应一个可独立验证的场景。

理由：**一段 30 行的 `main` 无法单独解释任何一个设计决策。**
拆成 5 段后，每段都能独立说清楚"这段在证明什么"。

**`SelfUnsubscribingObserver`：为什么它持有 `Subject` 引用**

```java
private static final class SelfUnsubscribingObserver implements Observer {

    private final String id;
    private final String displayName;
    private final Subject subject;              // ← 持有 Subject

    @Override
    public void onEvent(Event event) {
        System.out.println("  [站内信] " + displayName + " ← " + event.describe()
                + "｜（收到后立刻退订）");
        subject.unsubscribe(id);
    }
}
```

**这看起来像走回了原项目的老路**（观察者持有被观察者的引用），
但实际上完全不同。区别在**这个能力放在哪一层**：

| | 原项目 | 本项目 |
|---|---|---|
| `follow` / `unfollow` 在哪 | **`Followable` 接口里** | 这个 Demo 类的**私有字段**里 |
| 谁必须实现它 | **每一个观察者** | 只有这一个 Demo 类 |
| 参数类型 | `User` 接口，内部 `instanceof` 强转 | `Subject` 接口，直接调用 |
| 静默失败？ | **会**（`instanceof` 不匹配时） | 不会（接口方法一定存在） |

**判断标准只有一句话：**

> **这个能力是"每个实现都必须有"，还是"这一个实现恰好要用"？**
> 前者进接口，后者进字段。

"收到通知后取消关注"显然不是每个观察者都要做的事——
一个邮件渠道、一个推送渠道，它们凭什么要会退订？

**真实系统里这个引用通常不来自字段，而来自上下文：**

```java
// 从请求上下文里拿
public void onEvent(Event event) {
    UserContext ctx = RequestContext.current();
    ctx.unsubscribe(id);
}
```

本示例为了让它能独立运行，把引用显式传了进来。

---

## 6. 执行流程：五段 Demo 的真实运行结果

### 6.1 运行方式与哈希

```bash
cd E:\codeFile\codeFather\design_patterns_pro

# 编译（JDK 25，零告警）
javac -encoding UTF-8 -Xlint:all -d out ^
      src\com\patterns\behavioral\observer\*.java

# 运行（两个编码参数缺一不可）
java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 ^
     -cp out com.patterns.behavioral.observer.Client
```

**为什么必须同时设置两个编码参数？**

- `-Dfile.encoding=UTF-8`：影响 `String` 与字节数组之间的转换；
- `-Dstdout.encoding=UTF-8`：影响 `System.out` 写出的字节。

在 Windows 上，控制台默认代码页是 GBK。只设其中一个，
中文仍可能在某一环被错误编码，出现 `æˆ‘` 这种乱码。

**验证记录：**

| 项 | 值 |
|---|---|
| 编译参数 | `-encoding UTF-8 -Xlint:all` |
| 编译告警 | **0** |
| 生成的 class 文件 | 12 个（11 个源文件 + 1 个内部类） |
| 输出行数 | **83 行** |
| SHA256 | `D71E197C8F2737B0547D86C5930394F906304E72EEB62D9AAC19F922C3F7D0C1` |
| 三次运行一致性 | **完全一致** |

**"三次运行哈希一致"这句话验证的是什么？**

它排除了一类很容易被忽略的问题：**输出里混进了不可复现的东西。**

常见的不可复现来源：

| 来源 | 例子 | 本项目怎么避免 |
|---|---|---|
| 时间戳 | `2026-10-22 20:31:05 发布` | 事件不带 `publishedAt` |
| UUID | `eventId=3f2a...` | 事件不带 ID |
| `Object.hashCode()` | `PushObserver@1a2b3c` | 不打印对象本身 |
| `HashMap` 迭代序 | 三个渠道顺序随机 | `LinkedHashMap` + 注册序 |
| `Set.of(...)` 迭代序 | 每次 JVM 启动不同 | 不用 `Set` 存有序数据 |

**Hash 一致 = 上面五类问题一个都没有。**

### 6.2 Demo 1：一次发布，三个渠道

**真实输出（10 行）：**

```text
[Demo 1] 一次发布，三个渠道
  订阅者（注册顺序）：[张三, 李四, 王五]
  [站内信] 张三 ← [文章] hgl：《设计模式到底在解决什么》｜从「加需求时要不要改旧代码」说起
  [邮件] 李四(lisi@example.com) ← [文章] hgl：《设计模式到底在解决什么》｜从「加需求时要不要改旧代码」说起
  [推送/普通] device-w5 ← [文章] hgl：《设计模式到底在解决什么》｜从「加需求时要不要改旧代码」说起
投递报告｜[文章] hgl：《设计模式到底在解决什么》
  [成功] 张三
  [成功] 李四
  [成功] 王五
  合计：尝试 3 人，成功 3 人，失败 0 人
```

**逐步调用链：**

```text
main()
└─ demo1ThreeChannels()                                  第 70 行
   ├─ new ContentCreator("author-hgl", "hgl")
   │     authorId   = "author-hgl"
   │     authorName = "hgl"
   │     observers  = {}                     ← 空 LinkedHashMap
   │
   ├─ creator.subscribe(new InAppObserver("fan-1001", "张三"))
   │     observers = { "fan-1001" → InAppObserver(张三) }
   │
   ├─ creator.subscribe(new EmailObserver("fan-1002", "李四", "lisi@example.com"))
   │     observers = { "fan-1001" → …,
   │                   "fan-1002" → EmailObserver(李四, lisi@example.com) }
   │
   ├─ creator.subscribe(new PushObserver("fan-1003", "王五", "device-w5"))
   │     observers = { …, "fan-1003" → PushObserver(王五, device-w5, true) }
   │
   ├─ creator.subscriberNames()
   │     ["张三", "李四", "王五"]             ← 插入序（不是哈希序）
   │
   └─ creator.publishArticle("设计模式到底在解决什么",
   │                         "从「加需求时要不要改旧代码」说起")
      ├─ new Event(ARTICLE, "author-hgl", "hgl", title, content)
      │     紧凑构造器：4 个非空校验通过；content 保留原值
      │
      └─ publish(event)                                  第 186 行
         ├─ requireNonNull(event)                         第 187 行 ✓
         ├─ snapshot   = [张三观察者, 李四观察者, 王五观察者]   第 189 行
         ├─ deliveries = []                               第 190 行
         │
         ├─ 第 1 次：InAppObserver.onEvent(event)
         │    打印 → "  [站内信] 张三 ← [文章] hgl：《…》｜从「…」说起"
         │    deliveries += Delivery.ok(张三)             第 195 行
         │
         ├─ 第 2 次：EmailObserver.onEvent(event)
         │    event.type().realtime() → false → 不 return
         │    打印 → "  [邮件] 李四(lisi@example.com) ← …"
         │    deliveries += Delivery.ok(李四)
         │
         ├─ 第 3 次：PushObserver.onEvent(event)
         │    channelOpen == true → 不抛
         │    priority = "普通"（因为不是实时）
         │    打印 → "  [推送/普通] device-w5 ← …"
         │    deliveries += Delivery.ok(王五)
         │
         └─ DispatchReport.of(event, [3 条 ok])           第 201 行
               List.copyOf → 3 条
               ok = 3
               new DispatchReport(event, copy, 3, 0)
               紧凑构造器校验：3 == 3 ✓，0 == 0 ✓
```

**三个要点：**

**① 投递顺序就是注册顺序**

```text
张三 → 李四 → 王五
```

这不是巧合，是 `LinkedHashMap` 的插入序。
如果换成 `HashMap`，可能是 `李四 → 王五 → 张三`，而且**每次运行可能不同**。

**② 三个观察者的输出格式各不相同**

```text
[站内信] 张三 ← ...
[邮件] 李四(lisi@example.com) ← ...
[推送/普通] device-w5 ← ...
```

`publish` 完全不知道这些差异——它只调 `onEvent`。
**"怎么呈现"是每个渠道自己的事。**

**③ 报告的明细顺序与投递顺序一致**

因为 `deliveries` 是按 `snapshot` 的顺序 append 的。

### 6.3 Demo 2：身份是 ID，不是名字

**真实输出（14 行）：**

```text
[Demo 2] 身份是 ID，不是名字
  订阅 4 次（含 2 次重复），实际名单：[张三, 张三]
  subscriberCount() = 2
  「张三（新设备）」没能顶掉原来的 fan-2001
  [站内信] 张三 ← [文章] hgl：《名字与身份》｜两个张三，一个都不多
  [站内信] 张三 ← [文章] hgl：《名字与身份》｜两个张三，一个都不多
投递报告｜[文章] hgl：《名字与身份》
  [成功] 张三
  [成功] 张三
  合计：尝试 2 人，成功 2 人，失败 0 人
  报告里两行都叫「张三」，靠 observerId 才分得清：
    fan-2001 → 张三
    fan-2002 → 张三
  两行不是重复投递，是两个不同 ID 的粉丝恰好同名
```

**四次订阅，只有两次生效：**

| 序 | 调用 | 结果 | 为什么 |
|---:|---|---|---|
| 1 | `subscribe(fan-2001, "张三")` | **入列** | 名单空，直接放入 |
| 2 | `subscribe(fan-2002, "张三")` | **入列** | ID 不同，是另一个人 |
| 3 | `subscribe(fan-2001, "张三")` | 忽略 | 同 ID，`putIfAbsent` 发现键已存在 |
| 4 | `subscribe(fan-2001, "张三（新设备）")` | 忽略 | 同 ID，**保留第一个实例** |

**第 3 次为什么不是"替换"？**

见 5.8 节的论证：`put` 会让后来者**静默顶掉**先来者。
如果两个实例持有不同的状态（重试计数、连接池），
就会出现"注册了 A，通知发给了 B"。

**`putIfAbsent` 的语义是"如果已经有了，就当我没说"。**

**第 4 次是关键：同 ID 但不同实例**

```java
creator.subscribe(new InAppObserver("fan-2001", "张三（新设备）"));
```

名单里**仍然是** `"张三"`，不是 `"张三（新设备）"`。
输出第 4 行明确说了这一点：

```text
「张三（新设备）」没能顶掉原来的 fan-2001
```

**为什么这个设计是对的？**

因为"用户在平板上也登录了"这件事，应该由**账号系统**处理
（一个账号对应多个设备），**不应该由观察者名单处理**。

观察者名单的语义是"谁订阅了我"，不是"谁在哪台设备上"。
如果真要做多设备推送，正确做法是：

```
一个 Observer（用户）
  └─ 内部持有多个 DeviceToken
        ├─ device-phone
        └─ device-tablet
```

**而不是往名单里塞两个同 ID 的观察者。**

**报告里两行都叫「张三」**

```text
  [成功] 张三
  [成功] 张三
```

不了解内情的人会觉得"重复投递了"。所以 Demo 里紧接着打印了 ID：

```text
    fan-2001 → 张三
    fan-2002 → 张三
```

**这两行输出的存在，本身就说明了一个设计判断：**

> `Delivery` 里必须**同时**有 `observerId` 和 `observerName`。
> 只有名字，报告会误导人；只有 ID，报告没人看得懂。

**对比原项目的同名场景：**

| | 原项目 | 本项目 |
|---|---|---|
| 两个同名的人订阅 | 都进 `List`，都收到 2 条 | 都进 `Map`，但键不同，各收 1 条 |
| 其中一个退订 | `remove(equals)` 删掉第一个，另一个还在 | `remove("fan-2001")` 精确删除 |
| 能否区分 | **不能**（只有 `name` 字段） | 能（`id` 字段） |

### 6.4 Demo 3：通知途中退订

**真实输出（17 行）：**

```text
[Demo 3] 通知途中退订：不抛 ConcurrentModificationException
  发布前名单：[正常粉丝 1, 临时粉丝, 正常粉丝 2]
  [站内信] 正常粉丝 1 ← [文章] hgl：《快照遍历的意义》｜边遍历边改会出事
  [站内信] 临时粉丝 ← [文章] hgl：《快照遍历的意义》｜（收到后立刻退订）
  [站内信] 正常粉丝 2 ← [文章] hgl：《快照遍历的意义》｜边遍历边改会出事
投递报告｜[文章] hgl：《快照遍历的意义》
  [成功] 正常粉丝 1
  [成功] 临时粉丝
  [成功] 正常粉丝 2
  合计：尝试 3 人，成功 3 人，失败 0 人
  发布后名单：[正常粉丝 1, 正常粉丝 2]
  本轮三个人都收到了——名单在开始通知那一刻就固定了

  再发一篇，临时粉丝已经不在名单里：
  [站内信] 正常粉丝 1 ← [文章] hgl：《第二篇》｜这次只有两个人
  [站内信] 正常粉丝 2 ← [文章] hgl：《第二篇》｜这次只有两个人
  attempted() = 2，名单里只剩两个人
```

**第一次发布时的关键时刻：**

```text
snapshot = [正常粉丝1, 临时粉丝, 正常粉丝2]     ← 第 189 行，3 个
observers = { "fan-3001", "fan-3002", "fan-3003" }

第 1 次循环：正常粉丝1.onEvent
   打印 → "  [站内信] 正常粉丝 1 ← …"

第 2 次循环：临时粉丝.onEvent
   打印 → "  [站内信] 临时粉丝 ← …（收到后立刻退订）"
   subject.unsubscribe("fan-3002")
      observers.remove("fan-3002") → 返回临时粉丝实例
      observers.size() 从 3 变成 2          ← 原 Map 变了
   ★ 但 snapshot 仍然是 3 个元素
   ★ 下一个元素仍然是「正常粉丝 2」

第 3 次循环：正常粉丝2.onEvent
   打印 → "  [站内信] 正常粉丝 2 ← …"

报告：3 条成功
```

**第 3 次循环那一行，是本项目与原项目最本质的差别。**

原项目在这一步会发生：

```text
ArrayList 迭代器：cursor = 2, size = 2
hasNext() → cursor != size → 2 != 2 → false
循环结束
→ 正常粉丝 2 静默地没收到通知
→ 不抛任何异常
```

这正是探针 2 场景 B 复现出来的行为（见 2.11）。

**快照的机制为什么会避免这个问题**

因为遍历的是 `snapshot`，一个独立的 `ArrayList`：

```java
List<Observer> snapshot = new ArrayList<>(observers.values());
for (Observer observer : snapshot) { ... }
```

`snapshot` 的 `size` 始终是 3，`cursor` 从 0 走到 3，
`hasNext()` 在 `cursor != size` 时始终为 `true`。

**观察者怎么改 `observers`，都影响不到 `snapshot`。**

**第二次发布：名单已经变了**

```text
发布后名单：[正常粉丝 1, 正常粉丝 2]
再发一篇：
  [站内信] 正常粉丝 1 ← ...
  [站内信] 正常粉丝 2 ← ...
attempted() = 2，名单里只剩两个人
```

**这一次只有 2 条 `Delivery`**，因为 `snapshot` 只有 2 个元素。

**这就是"从下一轮起才不在名单里"的含义：**

| 时刻 | `observers` | 本轮的 `snapshot` | 他还会收到吗 |
|---|---|---|---|
| 开始通知前 | 3 个 | 3 个 | ✓ |
| 通知到他自己 | 3 → 2 个 | 3 个（不变） | ✓ 已收到 |
| 通知到第三个人 | 2 个 | 3 个（不变） | —— |
| **下一次 publish** | 2 个 | 2 个 | **✗** |

**边界情况的答案必须唯一。**
如果没有快照，`ArrayList` 的行为是"有时抛异常、有时静默漏发"——

- 抛异常时，后面的全部收不到；
- 静默时不报错，但有人漏了。

**两种都不可接受，而它们来自同一行代码。**

### 6.5 Demo 4：失败隔离

**真实输出（10 行）：**

```text
[Demo 4] 一个观察者坏了，其他人照收
  [站内信] 张三 ← [文章] hgl：《失败隔离》｜一坏不拖累其余
  [站内信] 王五 ← [文章] hgl：《失败隔离》｜一坏不拖累其余
投递报告｜[文章] hgl：《失败隔离》
  [成功] 张三
  [失败] 李四 — IllegalStateException: 推送通道已关闭：device-l4
  [成功] 王五
  合计：尝试 3 人，成功 2 人，失败 1 人
  publish 正常返回，说明「发布」本身成功了
  allSucceeded() = false，失败明细可以在 deliveries() 里逐条查
```

**注意输出的行数：投递明细只有 2 行，但报告有 3 行**

```text
  [站内信] 张三 ← ...      ← 张三点 onEvent 打印的
  [站内信] 王五 ← ...      ← 王五的 onEvent 打印的
                            ← 李四没有打印（因为他在打印前就抛了）
投递报告｜...
  [成功] 张三
  [失败] 李四 — ...         ← 这一行来自 Delivery，不是来自李四
  [成功] 王五
```

**李四那一行不是他自己打印的。他是被 Subject 记录的。**

这个细节很重要：如果报告依赖"观察者自己打印"，李四失败了就不会有输出。
**报告必须由 Subject 生成，因为只有它知道"全体"。**

**逐步调用链：**

```text
snapshot = [张三, 李四(closed), 王五]

第 1 次：张三.onEvent(event)
   channelOpen 不存在（InAppObserver 没有这个字段）
   打印 → "  [站内信] 张三 ← …"
   deliveries += Delivery.ok(张三)

第 2 次：李四.onEvent(event)
   channelOpen == false
   throw new IllegalStateException("推送通道已关闭：device-l4")
   ↓
   catch (RuntimeException e)                         第 196 行
   ↓
   Delivery.failed(李四, e)
      message = "推送通道已关闭：device-l4"
      reason  = "IllegalStateException" + ": " + message
              = "IllegalStateException: 推送通道已关闭：device-l4"
      new Delivery("fan-4002", "李四", false, reason)
   ↓
   deliveries += 该条
   ★ 循环没有中断

第 3 次：王五.onEvent(event)
   打印 → "  [站内信] 王五 ← …"
   deliveries += Delivery.ok(王五)

DispatchReport.of(event, [ok, failed, ok])
   3 条 → ok = 2, fail = 1
```

**`try` 的位置决定了什么**

```java
// 本项目：try 在循环内
for (Observer observer : snapshot) {
    try {
        observer.onEvent(event);
    } catch (RuntimeException e) {
        deliveries.add(Delivery.failed(observer, e));
    }
}
```

```java
// 原项目：没有 try（等价于 try 在循环外）
for (Follower follower : followers) {
    follower.update(userName, postContent);
}
// 第 2 个人抛异常 → 第 3 个人永远不被调用 → 异常冒泡给 post()
```

**一个括号的位置，决定了整个模式有没有意义。**

如果不做失败隔离，观察者模式在"多个实现由不同团队维护"的场景下
就是**不可用**的——A 团队的渠道挂了，B 团队的功能也跟着不可用。

**`publish` 为什么没抛异常**

```text
publish 正常返回，说明「发布」本身成功了
```

因为所有 `RuntimeException` 都在循环内被捕获了。
`publish` 的返回值是一个正常的 `DispatchReport`，`main` 继续往下执行。

**这个行为对应一条业务判断：**

> **"内容发布"和"通知全部送达"是两件事。**

文章已经写进数据库、已经可以被用户访问了——
**通知没送到，不应该让它回滚。**

如果 `publish` 抛异常，调用方会以为"发布失败"，然后：

- 可能重试整个发布 → 文章发两遍；
- 可能回滚事务 → 数据库里没有这篇文章，但内容其实已经生成。

**两种后果都比"通知少发了几个人"严重。**

**`allSucceeded() = false` 之后该做什么**

Demo 里只打印了这句话，真实系统里应该：

```java
DispatchReport report = creator.publishArticle(title, content);
if (!report.allSucceeded()) {
    // 1. 打点：失败率
    metrics.counter("notify.failed", report.failed());

    // 2. 对可重试的失败，进重试队列
    for (Delivery d : report.deliveries()) {
        if (!d.succeeded() && isRetryable(d.errorMessage())) {
            retryQueue.add(d.observerId(), d.errorMessage());
        }
    }

    // 3. 失败率超过阈值才告警（避免个位数失败就报警）
    if (report.failed() * 100 / report.attempted() > 5) {
        alert.warn("通知失败率超 5%：" + report.describeAsText());
    }
}
```

**注意第 2 步：不是所有失败都该重试。**

- `IllegalStateException: 推送通道已关闭` → 环境问题，**可以重试**；
- `IllegalArgumentException: 作者 ID 不能为空` → 代码 bug，**重试没用**。

这就是为什么 `Delivery.errorMessage` 里要保留**异常类名**（见 5.6 节）。

### 6.6 Demo 5：事件类型驱动渠道

**真实输出（21 行）：**

```text
[Demo 5] 同一个事件，三个渠道三种处理
  ── 发文章 ──
  [站内信] 张三 ← [文章] hgl：《设计模式速查》｜三个渠道都会收到
  [邮件] 李四(lisi@example.com) ← [文章] hgl：《设计模式速查》｜三个渠道都会收到
  [推送/普通] device-w5 ← [文章] hgl：《设计模式速查》｜三个渠道都会收到

  ── 发视频 ──
  [站内信] 张三 ← [视频] hgl：《十分钟看懂观察者》｜三个渠道都会收到
  [邮件] 李四(lisi@example.com) ← [视频] hgl：《十分钟看懂观察者》｜三个渠道都会收到
  [推送/普通] device-w5 ← [视频] hgl：《十分钟看懂观察者》｜三个渠道都会收到

  ── 开直播 ──
  [站内信] 张三 ← [直播][实时] hgl：《今晚八点聊聊观察者模式》｜（无正文）
  [推送/实时] device-w5 ← [直播][实时] hgl：《今晚八点聊聊观察者模式》｜（无正文）
投递报告｜[直播][实时] hgl：《今晚八点聊聊观察者模式》
  [成功] 张三
  [成功] 李四
  [成功] 王五
  合计：尝试 3 人，成功 3 人，失败 0 人
  邮件渠道静默跳过了直播，报告里仍记为「成功」
  「这个渠道不需要这条事件」不是失败
```

**三种事件 × 三个渠道的矩阵：**

| | 站内信 | 邮件 | 推送 |
|---|---|---|---|
| **文章** | ✓ 记录 | ✓ 发邮件 | ✓ 普通推送 |
| **视频** | ✓ 记录 | ✓ 发邮件 | ✓ 普通推送 |
| **直播** | ✓ 记录 | **静默跳过** | ✓ **实时**推送 |

**代码行数：**

- 加一条"直播不发邮件"，改了 `EmailObserver` 里 **3 行**；
- 加一条"直播走实时通道"，改了 `PushObserver` 里 **1 行**；
- `ContentCreator`、`Subject`、`Event`、`EventType`：**一行都没改**。

**这就是"对扩展开放，对修改关闭"的可测量形式：**
不是"设计得好所以不用改"，而是"改动被限制在一个文件里"。

**直播那一轮的完整链路：**

```text
publishLive("今晚八点聊聊观察者模式")
└─ publish(new Event(LIVE, "author-hgl", "hgl", title, ""))
   └─ event.describe()
      type.label()    = "直播"
      type.realtime() = true → realtimeMark = "[实时]"
      → "[直播][实时] hgl：《今晚八点聊聊观察者模式》"

   └─ 第 1 次：张三.onEvent
      打印 → "  [站内信] 张三 ← [直播][实时] hgl：《…》｜（无正文）"
      ↑ summary() 返回 "（无正文）"，因为 content 是空串
      Delivery.ok(张三)

   └─ 第 2 次：李四.onEvent
      event.type().realtime() → true
      return                          ← 直接返回，不打印、不抛异常
      Delivery.ok(李四)               ← 仍然记为成功

   └─ 第 3 次：王五.onEvent
      channelOpen == true
      priority = "实时"
      打印 → "  [推送/实时] device-w5 ← [直播][实时] hgl：《…》｜（无正文）"
      Delivery.ok(王五)

   └─ 报告：3 人尝试，3 人成功，0 人失败
```

**"静默跳过"为什么记为成功**

这是本章最需要想清楚的一个判断。

```java
if (event.type().realtime()) {
    return;                        // 没有抛异常 → 记为成功
}
```

对比另一种写法：

```java
if (event.type().realtime()) {
    throw new IllegalStateException("直播不发邮件");    // → 记为失败
}
```

两种写法的报告分别是：

```text
（项目选择）
  合计：尝试 3 人，成功 3 人，失败 0 人

（另一种写法）
  合计：尝试 3 人，成功 2 人，失败 1 人
  [失败] 李四 — IllegalStateException: 直播不发邮件
```

**第二种写法会导致每次直播都触发一次"失败告警"。**

**失败的定义应该是"想发但没发出去"，而不是"决定不发"。**

这条判断可以推广成一句话：

> **观察者的"不处理"和"处理失败"，必须能区分开。**

- 不处理 = 策略选择 = 正常返回；
- 处理失败 = 故障 = 抛异常。

**如果两者都表现为异常，报告就失去了诊断价值**——
你分不清"这次 3 个失败是因为通道挂了"还是"因为本来就不该发"。

### 6.7 五段串起来看

**83 行输出，覆盖了七个设计决策：**

| 输出片段 | 证明了哪个决策 | 对应章节 |
|---|---|---|
| `订阅者（注册顺序）：[张三, 李四, 王五]` | `LinkedHashMap` 的插入序 | 4.3 |
| `实际名单：[张三, 张三]` / `subscriberCount() = 2` | 按 ID 去重，同名不冲突 | 4.1 |
| `「张三（新设备）」没能顶掉原来的 fan-2001` | `putIfAbsent` 保留第一个实例 | 5.8 |
| `fan-2001 → 张三` / `fan-2002 → 张三` | `id()` 与 `displayName()` 分离 | 4.1 |
| `正常粉丝 2 ← …`（退订后仍收到） | 快照遍历 | 4.4 |
| `发布后名单：[正常粉丝 1, 正常粉丝 2]` | 退订立即生效于下一轮 | 4.4 |
| `[失败] 李四 — IllegalStateException: …` | 失败隔离 + 结果对象化 | 4.6 / 4.7 |
| `publish 正常返回` | 发布与通知解耦 | 4.7 |
| `[推送/实时]` vs `[推送/普通]` | 事件类型驱动渠道行为 | 4.8 |
| `邮件渠道静默跳过了直播，报告里仍记为「成功」` | 不处理 ≠ 失败 | 6.6 |

**和原项目的 7 行输出对比：**

| | 原项目 | 本项目 |
|---|---:|---:|
| 输出行数 | 7 | 83 |
| 能看出投递顺序吗 | 能（`List` 的插入序） | 能 |
| 能看出失败吗 | **不能**（失败 = 异常 = 中断） | 能（报告明细） |
| 能区分同名用户吗 | **不能** | 能（ID） |
| 能看出事件类型吗 | **不能**（只有 `String`） | 能 |
| 退订途中会崩吗 | **会**（CME 或静默漏发） | 不会 |

**83 行不是"代码变复杂了"，是"原本被隐藏的信息被显示出来了"。**

原项目那 7 行看着干净，是因为它**没有报告**——
成功了 3 个还是 2 个，失败了几个，看输出是看不出来的。
本项目多出来的 76 行，绝大部分是**把原本看不见的东西打印出来**。

---

## 7. 扩展对比：新需求来了改哪里

这一章用六个真实需求来量化"这个设计的扩展成本"。

**衡量标准只有两个：**

1. 改了几个文件？
2. 有没有改**已经能跑的代码**（修改 vs 新增）？

### 7.1 需求一：加一个短信渠道

**需求**：所有事件都发一条短信。

**改动**：新增 1 个文件，修改 1 行。

```java
// 新增 SmsObserver.java
public class SmsObserver implements Observer {

    private final String id;
    private final String displayName;
    private final String phone;

    public SmsObserver(String id, String displayName, String phone) {
        this.id = id;
        this.displayName = displayName;
        this.phone = phone;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public void onEvent(Event event) {
        System.out.println("  [短信] " + phone + " ← " + event.describe());
    }
}
```

```java
// Client 里加一行
creator.subscribe(new SmsObserver("fan-6001", "赵六", "138****6666"));
```

**没有改的：**

- `Subject` / `ContentCreator`：**0 行**
- `Observer`：**0 行**
- `Event` / `EventType`：**0 行**
- 已有的三个观察者：**0 行**

**这就是开闭原则的可测量形式。**

对比原项目加一个渠道要做的事：

```java
// 1. 新增一个实现 Follower 的类
public class SmsSender implements Follower {
    @Override
    public void update(String userName, String postContent) { ... }
}

// 2. Client 里注册
smsSender.follow(hgl);       // ← 必须实现 Followable！

// 如果 SmsSender 只实现了 Follower，没实现 Followable：
smsSender.follow(hgl);       // 编译错误：cannot find symbol
```

**原项目的"加渠道"被迫要连带实现"关注能力"**——
一个短信发送器为什么要会"关注"别人？**这就是接口污染的代价。**

### 7.2 需求二：加一种事件类型（比如"专栏"）

**需求**：新增 `COLUMN("专栏", false)`。

**改动**：1 个文件、1 行。

```java
public enum EventType {
    ARTICLE("文章", false),
    VIDEO("视频", false),
    COLUMN("专栏", false),        // ← 新增这一行
    LIVE("直播", true);
}
```

**然后编译器会告诉你还有哪里要改**——如果用了 `switch`：

```java
switch (event.type()) {
    case ARTICLE -> ...;
    case VIDEO   -> ...;
    case LIVE    -> ...;
    // 编译器报错：switch 表达式未覆盖所有可能值
}
```

**这是枚举相对字符串常量的最大优势**：新增值会让所有遗漏处**编译失败**。

**如果用 `String`：**

```java
public static final String ARTICLE = "article";
public static final String COLUMN  = "column";   // 新增

// 某处的 switch
switch (type) {
    case "article": ...
    case "video": ...
    case "live": ...
    // "column" 没有分支 → 运行到此处抛 IllegalArgumentException
    // 编译器不会提醒你
}
```

**同一份代码，枚举在编译期报错，字符串在运行时炸。**

**本项目实际的改动量：0 个文件。**

因为三个观察者都没有 `switch (type)`：
- `InAppObserver`：调 `describe()`，不关心类型；
- `EmailObserver`：判断 `realtime()`，不关心类型；
- `PushObserver`：判断 `realtime()`，不关心类型。

**加一种类型，连枚举都不用改**（当然要先加枚举常量）。
这正是"把规则做成数据"的收益：**用数据的地方不用改，用 switch 的地方必须改。**

### 7.3 需求三：按类型订阅

**需求**："我只想收直播通知"。

**方案 A：观察者内部过滤（当前能力，0 改动）**

用户在注册时，包一层：

```java
public class TypeFilteredObserver implements Observer {

    private final Observer delegate;
    private final Set<EventType> accepted;

    public TypeFilteredObserver(Observer delegate, EventType... accepted) {
        this.delegate = delegate;
        this.accepted = Set.of(accepted);
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String displayName() {
        return delegate.displayName();
    }

    @Override
    public void onEvent(Event event) {
        if (accepted.contains(event.type())) {
            delegate.onEvent(event);
        }
    }
}
```

用法：

```java
creator.subscribe(new TypeFilteredObserver(
        new InAppObserver("fan-7001", "只看直播的张三"),
        EventType.LIVE));
```

**这是装饰器模式在观察者上的自然应用**：不改任何已有代码，
用组合给观察者加能力。

**注意 `id()` 和 `displayName()` 都委托给被装饰者**——
这样 `unsubscribe("fan-7001")` 仍然有效。
如果 `id()` 返回自己的标识，用户就无法按原 ID 退订了。

**方案 B：把过滤条件收进订阅关系（改 3 个文件）**

```java
// 修改 Subject 接口
public interface Subject {
    void subscribe(Observer observer);
    void subscribe(Observer observer, Predicate<Event> filter);   // 新增重载
    boolean unsubscribe(String observerId);
    int subscriberCount();
    DispatchReport publish(Event event);
}
```

内部结构从 `Map<String, Observer>` 变成 `Map<String, Subscription>`：

```java
record Subscription(Observer observer, Predicate<Event> filter) {
    boolean accepts(Event event) {
        return filter.test(event);
    }
}
```

`publish` 加一个判断：

```java
for (Subscription subscription : snapshot) {
    Observer observer = subscription.observer();
    if (!subscription.accepts(event)) {
        deliveries.add(Delivery.ok(observer));      // 跳过也算成功
        continue;
    }
    // ...原来的投递逻辑
}
```

**改动清单：**

| 文件 | 改动 |
|---|---|
| `Subject` | +1 个方法声明 |
| `ContentCreator` | 内部结构换类型，`publish` 加 5 行 |
| `Delivery` | 可能要加一个 `skipped` 状态（否则"跳过"和"成功"无法区分） |
| `Client` | Demo 里可以展示 |

**什么时候该上方案 B？**

**当过滤条件是平台级策略时**：

```java
// 平台配置：所有邮件渠道都不发直播
notificationPolicy.register(ChannelType.EMAIL, event -> !event.type().realtime());
```

这时候过滤条件不该由每个观察者各写一遍——
它应该由配置驱动，观察者只管"收到就发"。

**判断标准：**

> 过滤条件是"这个用户的选择"，还是"平台的统一规则"？
> 前者用方案 A（装饰器），后者用方案 B（`Subscription`）。

**本项目选方案 A 的现状，是因为它没超出"用户选择"的范围。**
但把方案 B 的路径写清楚，读者才知道什么时候该升级。

### 7.4 需求四：改成异步投递

**需求**：通知太慢（假设每个渠道要 200ms），不能阻塞发布。

**方案：把 `publish` 改成提交任务**

```java
public class AsyncContentCreator implements Subject {

    private final ContentCreator delegate;
    private final ExecutorService executor;

    public AsyncContentCreator(ContentCreator delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public void subscribe(Observer observer) {
        delegate.subscribe(observer);
    }

    @Override
    public boolean unsubscribe(String observerId) {
        return delegate.unsubscribe(observerId);
    }

    @Override
    public int subscriberCount() {
        return delegate.subscriberCount();
    }

    @Override
    public DispatchReport publish(Event event) {
        throw new UnsupportedOperationException("异步模式请用 publishAsync");
    }

    public CompletableFuture<DispatchReport> publishAsync(Event event) {
        return CompletableFuture.supplyAsync(() -> delegate.publish(event), executor);
    }
}
```

**注意这里做了什么：**

- 没有改 `ContentCreator` 一行；
- 返回值从 `DispatchReport` 变成 `CompletableFuture<DispatchReport>`；
- `publish` 同步版本保留但抛异常，防止误用。

**异步带来的三个新问题（必须回答）：**

**① 快照什么时候取？**

```java
// 在提交任务时取（推荐）
List<Observer> snapshot = new ArrayList<>(observers.values());
executor.submit(() -> deliverTo(snapshot, event));

// 还是在任务执行时取？
executor.submit(() -> deliverTo(new ArrayList<>(observers.values()), event));
```

前者语义是"提交这一刻的名单"，后者是"实际执行那一刻的名单"。
如果排队很久，后者可能发给很多"提交之后才订阅"的人。

**必须选一个并写进文档**，否则又是一个"两种理解都说得通"的坑。

**② 失败去哪里了？**

同步时失败在 `DispatchReport` 里；
异步时调用方只拿到 `CompletableFuture`，**如果没人 `get()`，失败就被吞了**。

```java
creator.publishAsync(event);          // 返回值丢了 → 失败无人知晓
```

正确做法是统一挂一个 `whenComplete`：

```java
creator.publishAsync(event)
       .whenComplete((report, ex) -> {
           if (ex != null) log.error("异步投递异常", ex);
           else if (!report.allSucceeded()) alert.warn(report.describeAsText());
       });
```

**③ 线程安全**

`ContentCreator` 的 `LinkedHashMap` 不是线程安全的。
异步之后，"主线程 `subscribe`"与"工作线程遍历"会**真正并发**。

最小改动：

```java
private final Map<String, Observer> observers =
        Collections.synchronizedMap(new LinkedHashMap<>());
```

但 `synchronizedMap` 的 `values()` 迭代**仍需手动加锁**：

```java
List<Observer> snapshot;
synchronized (observers) {
    snapshot = new ArrayList<>(observers.values());
}
```

**这是 5.8 节"线程安全说明"的具体兑现。**
本项目不做，是因为同步示例不需要；但读者必须知道异步之后它变成必需。

### 7.5 需求五：加事件历史与重放

**需求**：新用户注册后，能看到"最近 3 条历史动态"。

**最简单的做法：让 `Subject` 记一份历史**

```java
private final Deque<Event> history = new ArrayDeque<>();
private static final int MAX_HISTORY = 100;

public DispatchReport publish(Event event) {
    history.addLast(event);
    if (history.size() > MAX_HISTORY) {
        history.removeFirst();
    }
    // ...原来的投递逻辑
}

public List<Event> recentEvents(int limit) {
    return history.stream()
            .skip(Math.max(0, history.size() - limit))
            .toList();
}
```

**注意这里的边界：**

- `ArrayDeque` 有界，防内存泄漏；
- `recentEvents` 返回 `toList()` 的不可变副本，调用方改不了内部队列；
- **历史只用于展示，不用于重放投递**——如果要重放，就变成事件溯源（Event Sourcing）了，那是另一个架构决策。

**为什么"历史"不该由观察者自己维护？**

如果每个观察者都存一份历史：

```java
// 站内信观察者内部
List<Event> myHistory = new ArrayList<>();
```

那么：
- 内存 × N（N 个观察者）；
- 新观察者订阅后，历史是空的（除非 Subject 补发）；
- 一致性无法保证（有的存了 100 条，有的存了 10 条）。

**"过去发生了什么"是 Subject 的事实，不是每个观察者的私有记忆。**

### 7.6 需求六：改成线程安全

**需求**：多线程环境下使用。

**方案 A：全类加锁（最简单，也最慢）**

```java
public synchronized void subscribe(Observer observer) { ... }
public synchronized boolean unsubscribe(String observerId) { ... }
public synchronized DispatchReport publish(Event event) { ... }
```

问题：`publish` 持锁期间会执行**所有观察者的 `onEvent`**，
一个慢渠道（200ms）会把所有 `subscribe` 阻塞住。

**方案 B：写时复制（读多写少，推荐）**

```java
private volatile Map<String, Observer> observers = new LinkedHashMap<>();

public synchronized void subscribe(Observer observer) {
    Map<String, Observer> copy = new LinkedHashMap<>(observers);
    copy.putIfAbsent(observer.id(), observer);
    observers = copy;                          // volatile 写，其他线程立刻可见
}

public DispatchReport publish(Event event) {
    List<Observer> snapshot = new ArrayList<>(observers.values());   // 读 volatile
    // ...投递（不持锁）
}
```

**注意这里快照是"免费"的**——它读的 `observers` 本身就是一个
不可变的 `Map` 快照（每次 `subscribe` 都换成一个新的）。
所以 `snapshot` 只是把 `Map` 的 `values` 复制成 `List`，不需要额外同步。

**代价：每次 `subscribe` 复制整个 `Map`。**
8 万人的名单，每加一个粉丝复制 8 万个 `Entry`。
在"注册频繁"的场景下不可接受。

**方案 C：`ConcurrentHashMap`（适合读多写多）**

```java
private final Map<String, Observer> observers = new ConcurrentHashMap<>();
```

- `putIfAbsent` / `remove` 是原子的；
- `publish` 里 `new ArrayList<>(observers.values())` 得到的是**弱一致快照**
  （遍历期间发生的修改可能看到也可能看不到，但不会抛 CME）。

**代价：丢失插入序。** `ConcurrentHashMap` 的迭代顺序不确定，
"投递顺序 = 注册顺序"这条契约就没了。

**如果顺序必须保证，正确做法是：**
`ConcurrentHashMap` + 每个值带一个自增序号，快照后按序号排序。

**这就是工程的常态：没有免费的方案，只有明确的取舍。**

**本项目选"单线程 + 文档说明"，是因为：**

- 示例要展示的是模式结构，不是并发技巧；
- 一个半吊子的并发实现比"明确说明非线程安全"更危险。

### 7.7 与发布订阅（Pub/Sub）的距离

经常有人问："观察者模式和发布订阅有什么区别？"

**结构上的差别只有一处：中间有没有 Broker。**

```text
观察者模式：
   Publisher ──────> Observer（接口）
     （Publisher 持有 List<Observer>）

发布订阅：
   Publisher ──> Broker ──> Subscriber
                （Topic 路由）
```

**具体到代码：**

```java
// 观察者：发布者自己知道名单
class ContentCreator implements Subject {
    private final Map<String, Observer> observers;      // ← 名单在这里
    public DispatchReport publish(Event event) { ... }
}

// 发布订阅：发布者只知道主题，不知道谁订阅
class EventBus {
    private final Map<String, List<Subscriber>> topics; // ← 名单在 Broker

    public void publish(String topic, Event event) {
        topics.getOrDefault(topic, List.of()).forEach(s -> s.onEvent(event));
    }

    public void subscribe(String topic, Subscriber subscriber) { ... }
}
```

**带来的四个实质差别：**

| | 观察者模式 | 发布订阅 |
|---|---|---|
| 发布者知道有几个人吗 | **知道**（`subscriberCount()`） | 不知道 |
| 能保证送达顺序吗 | 能（注册序） | 通常不能（多订阅者并发） |
| 需要 Topic 概念吗 | 不需要 | **需要** |
| 能跨进程吗 | 不能 | 可以（MQ） |

**什么时候该从观察者升级到发布订阅？**

- 订阅者需要按主题过滤，而主题数量在增长；
- 发布者不该知道"有多少人在听"；
- 需要跨进程/跨服务。

**什么时候不该升级？**

- 只有一两个事件类型；
- 需要同步拿到投递结果（`DispatchReport`）；
- 发布者和订阅者本来就在同一个模块里。

**本项目的规模（一个作者、3 个渠道）用观察者模式是正确的。
引入 EventBus 只会增加一层间接，没有任何收益。**

### 7.8 成本总表

| 需求 | 新增文件 | 修改已有文件 | 修改已有代码行数 | 风险 |
|---|---:|---:|---:|---|
| 加短信渠道 | 1 | 1（Client） | 1 | 极低 |
| 加事件类型 | 0 | 1（EventType） | 1 | 低（编译器帮查 `switch`） |
| 按类型订阅（装饰器） | 1 | 0 | 0 | 极低 |
| 按类型订阅（`Subscription`） | 0 | 3 | ~15 | 中（要处理"跳过"语义） |
| 异步投递 | 1 | 0 | 0 | 中（快照时机 + 失败去向 + 线程安全） |
| 事件历史 | 0 | 1 | ~12 | 低（要有界） |
| 线程安全（写时复制） | 0 | 1 | ~8 | 中（写放大） |
| 升级为 EventBus | 3+ | 全部调用方 | 大 | 高（换架构） |

**读这张表的正确方式：**

- 前三条（加渠道、加类型、按类型订阅）成本极低，**因为模式结构正确**；
- 中间三条（异步、历史、线程安全）成本中等，**因为它们是新维度的需求**，
  不是模式没设计好；
- 最后一条成本高，**因为它是架构级变更**，不该期待"改几行搞定"。

**如果一张表告诉你"什么需求都只要改一行"，那它一定在骗你。**

---

## 8. 优缺点与常见误区

### 8.1 四个优点，每个都带代价

**优点一：发布方与接收方解耦**

`ContentCreator` 只依赖 `Observer` 接口，不知道有三个还是三十个实现。

**代价**：调用链变长，调试时"这行代码是谁调的"更难追。
一个 `onEvent` 可能来自任何一次 `publish`，堆栈里看不出业务语义。

**缓解**：在 `publish` 里打一条带 `event.describe()` 的日志，
让每次广播都有可检索的锚点。

**优点二：符合开闭原则**

加渠道不改 `Subject`。

**代价**：**"不改代码"不等于"没有风险"**。
新增一个写错的观察者，会让每次发布都多一条失败记录，
而 `Subject` 的代码一行没动——**问题出在新的地方，症状出现在老的地方。**

**缓解**：`DispatchReport` + 失败率告警。这正是本项目把它做出来的原因。

**优点三：运行时动态**

订阅和退订都是运行时行为，不需要重启。

**代价**：**行为依赖于运行时状态**，同样的输入可能产生不同的输出。
"为什么这次只发给两个人？"——因为五分钟前有人退订了。

**缓解**：`subscriberCount()` + `subscriberNames()`，
让"当前名单"随时可查。

**优点四：职责清晰**

每个观察者只关心自己那个渠道。

**代价**：类的数量增加。11 个文件 vs 原项目 6 个。

**代价的量化**：多出来的 5 个文件里，
3 个是渠道实现（业务需要），2 个是报告（工程需要）。
**没有一个是"为了模式而模式"。**

### 8.2 四个缺点，每个都给缓解方案

**缺点一：通知顺序不确定（在错误的实现下）**

用 `HashSet` / `HashMap` / `Set.of(...)` 存观察者，
迭代顺序不稳定，**同一份代码跑两次顺序可能不同**。

**缓解**：`LinkedHashMap`。本项目已采用，并在 6.1 用哈希验证了三次一致。

**缺点二：可能出现循环通知**

```java
// 观察者 A 收到事件后，又发布了一个新事件
public void onEvent(Event event) {
    creator.publishArticle("转发了：" + event.title(), event.content());
}
```

如果 B 也这么做，就会无限循环，直到 `StackOverflowError`。

**缓解**：

- 在 `Event` 里带一个 `depth` 或 `source` 字段，超过深度就丢弃；
- 或者在 `ContentCreator` 里加一个 `ThreadLocal<Boolean> publishing`，
  检测到重入就拒绝（或者告警）。

**本项目不做**，因为 Demo 里没有这个场景。
但必须在文档里写出来——**这是观察者模式最经典的生产事故之一。**

**缺点三：内存泄漏（观察者忘记退订）**

长生命周期的 Subject 持有短生命周期的 Observer：

```java
// 一个请求级的观察者订阅了一个应用级的 Subject
requestScopedObserver.subscribeTo(appScopedCreator);
// 请求结束，观察者没人引用，但 Subject 的 Map 里还有它 → 无法 GC
```

**缓解**：

- 用弱引用（`WeakReference<Observer>`）——但会引入"什么时候被回收"的不确定性；
- 或者提供 `close()` / `AutoCloseable`，由容器在销毁时统一退订；
- 或者在 `subscribe` 时返回一个 `Subscription` 句柄，句柄 `close()` 即退订。

**本项目不做**，因为 `Client` 是短生命周期进程。
但这是真实项目里**必须处理**的问题。

**缺点四：调试困难**

一个 `publish` 触发 N 个 `onEvent`，其中任何一个出错，
堆栈都在 `publish` 的循环里，看不出业务上下文。

**缓解**：

- `Delivery` 里带 `observerId`，日志可以精确到人；
- `DispatchReport` 一次性看全貌；
- 每个观察者的 `onEvent` 第一行打日志（本项目用打印代替）。

### 8.3 七个常见误区

**误区一："观察者模式就是事件监听器"**

不完全是。监听器（`ActionListener`）通常是**一个**回调，
观察者模式强调**一对多 + 名单管理**。

一个 `button.addActionListener(...)` 可以是观察者模式的实例，
但"只有一个监听器"时，它退化成了一次普通回调。

**误区二："必须用接口，不能用抽象类"**

接口和抽象类都可以。选择标准：

| | 接口 | 抽象类 |
|---|---|---|
| 观察者需要继承别的类 | ✓ 只能用接口 | ✗ 单继承限制 |
| 需要提供默认实现 | 可用 `default` 方法 | ✓ 天然支持 |
| 需要持有状态 | ✗ | ✓ |

**本项目用接口**，因为观察者可能已经有父类（比如 `HttpServlet`）。

**误区三："通知必须是同步的"**

不一定。同步和异步是**两个不同的设计决策**，与模式本身无关。
本项目的选择是同步，理由是"要返回 `DispatchReport`"。
如果不需要返回值，异步更常见（见 7.4）。

**误区四："观察者要自己处理异常"**

**这是最危险的误区。** 观察者自己 `catch` 之后：

- `publish` 的报告里记为成功；
- 监控看到 100% 成功率；
- 用户投诉没收到，日志里只有一行"发送失败"。

**正确做法：让异常抛出去，由 Subject 统一捕获和记录。**

**误区五："通知失败就应该重试"**

不是所有失败都该重试：

| 失败原因 | 该重试吗 |
|---|---|
| 网络超时 | ✓ |
| 推送通道关闭 | ✓ |
| 用户 ID 为空 | ✗（代码 bug） |
| 消息内容超长 | ✗（数据问题） |

**无差别重试只会让故障放大。** 重试策略应该基于 `Delivery.errorMessage` 里的异常类型。

**误区六："`List<Observer>` 就够了，不用 `Map`"**

`List` 在三个场景下都会出问题（见 2.5 / 2.10）：

- 重复订阅；
- 按 ID 精确删除；
- O(1) 查找。

**只有一种情况 `List` 是对的：观察者没有稳定 ID，且允许重复。**
这在真实业务里几乎不存在。

**误区七："观察者模式能替代消息队列"**

**不能。** 三个根本差别：

| | 观察者模式 | 消息队列 |
|---|---|---|
| 进程 | 同一进程 | 跨进程 |
| 持久化 | 无（内存里的名单） | 有（磁盘） |
| 进程重启 | 名单丢失 | 消息还在 |

用观察者模式去模拟 MQ，会得到一个**重启就丢消息、没有重试、没有死信队列**
的"假 MQ"。它在测试环境能跑，上线就出事。

### 8.4 与相关模式的对比

**观察者 vs 中介者（Mediator）**

| | 观察者 | 中介者 |
|---|---|---|
| 拓扑 | 一对多（星形） | 多对多（都连到中心） |
| 谁发起 | Subject 主动广播 | 任何同事对象都通过中介者转发 |
| 通信方向 | **单向**（Subject → Observer） | **多向**（A → Mediator → B/C） |
| 典型例子 | 发布动态 | 聊天室 |

**关键差别**：观察者是"我说，你们听"；中介者是"大家说，我来转"。

本项目里的 `ContentCreator` 是观察者模式的 Subject（单向），
不是中介者（没有"观察者之间互相通信"的能力）。

**观察者 vs 责任链（Chain of Responsibility）**

| | 观察者 | 责任链 |
|---|---|---|
| 观察者之间的关系 | **互不知道** | 知道下一个是谁 |
| 处理数量 | **全部**都处理 | 通常**一个**处理完就停 |
| 顺序重要吗 | 只是输出顺序 | **是核心语义** |
| 失败怎么办 | 隔离，继续下一个 | 决定是否继续 |

**一句话**：观察者是"广播"，责任链是"接力"。

**观察者 vs 发布订阅（Pub/Sub）**

见 7.7 节。核心差别是**中间有没有 Broker**。

**观察者 vs 状态模式（State）**

这两个经常被搞混，因为都涉及"行为随状态变化"：

| | 观察者 | 状态 |
|---|---|---|
| 变化的是 | **人数**（谁在听） | **状态**（现在是什么阶段） |
| 行为差异来自 | 不同的**观察者实现** | 不同的**状态对象** |
| 调用方 | 发布方不知道有几个 | 调用方调用同一个方法 |
| 典型代码 | `for (o : observers) o.onEvent(e)` | `state.handle()` |

**本项目里两者都有示例**（`StatePattern` 目录）。
一个直观的区别：**状态模式里，对象只有一个"当前状态"；
观察者模式里，Subject 有一"群"观察者。**

**观察者 vs 策略（Strategy）**

| | 观察者 | 策略 |
|---|---|---|
| 数量 | 一对多 | 一对一 |
| 调用方是否选择 | 否（全部通知） | **是**（选一个） |
| 变化时机 | 运行时动态增删 | 通常在构造时确定 |

**一句话**：策略是"我在几个算法里选一个"，观察者是"我把消息告诉所有人"。

---

## 9. 面试与自测

### 9.1 十道自测题

先自己回答，再看参考答案。**能说出来才算会，看懂了不算。**

---

**Q1. 观察者模式中，`subscribe` 方法应该定义在谁身上？为什么？**

<details>
<summary>参考答案</summary>

定义在 **Subject（被观察者）** 身上。

理由：**谁持有数据，谁提供操作数据的方法。** 观察者名单是 Subject 的私有字段，
按封装原则，能改它的只能是 Subject 自己。

原项目的错误不是"方法名起错了"，而是**把 `follow` 放在了观察者接口上**，
导致观察者必须反向操作 Subject 的内部集合，还要 `instanceof` 强转。

**判断标准**：这个能力是"每个实现都必须有"，还是"这一个实现恰好要用"？
前者进接口，后者进字段。

</details>

---

**Q2. 为什么 `Observer` 必须有一个显式的 `id()`，而不能用 `equals` 判等？**

<details>
<summary>参考答案</summary>

因为 **`equals` 回答的问题和"是不是同一个订阅者"不是同一个问题**。

- `equals` 回答："这两个对象在业务上是否可以视为同一份数据"；
- 名单需要回答："这是不是同一个订阅者"。

`record ConcreteFollower(String name)` 的 `equals` 按 `name` 比较，于是：

```java
张三a.equals(张三b)  →  true     // 两个不同的人
```

后果链：允许重复入列 → 一个人收两条 → 退订时删错人。

**根因**：`equals` 是由字段推导出来的，而字段会重复、会变。
**身份必须是显式的、稳定的、由业务给定的。**

</details>

---

**Q3. 为什么 `publish` 必须先快照再遍历？不这样做会出什么事？**

<details>
<summary>参考答案</summary>

因为观察者可能在 `onEvent` 里调用 `unsubscribe`，修改正在遍历的集合。

不这样做会出现**两种**结果，取决于删的是第几个元素：

| 删的位置 | 结果 |
|---|---|
| 非倒数第二个 | 抛 `ConcurrentModificationException`，后面的全部收不到 |
| **倒数第二个** | **不抛异常，但最后一个静默漏发** |

第二种的根因是 `ArrayList.Itr.hasNext()` 的实现是 `cursor != size`，
**不检查 `modCount`**。`remove` 后 `size` 变小，`cursor` 恰好追上了它，
`hasNext()` 直接返回 `false`。

**同一个 bug 两种表现，其中一种完全无声。** 这是探针 2 实测出来的。

快照（`new ArrayList<>(observers.values())`）同时消灭了这两种。

</details>

---

**Q4. `DispatchReport` 里的 `succeeded` / `failed` 为什么存字段，而不是现算？**

<details>
<summary>参考答案</summary>

两个原因，第二个更重要：

1. **性能**：报告会被反复读（打印、打点、告警判断），每次 `stream().count()` 是 O(n)；
2. **自洽性**：作为构造参数，**构造处可以校验**"数字与明细是否对得上"：

```java
if (okCount != succeeded || failCount != failed) {
    throw new IllegalArgumentException("统计数字与明细不符");
}
```

如果是现算的，就永远不可能不自洽——但也就**失去了"报告不能自己说自己"这道防线**。

配合 `List.copyOf`（真拷贝，不是 `unmodifiableList` 视图），
报告成为一个**冻结的事实**。

</details>

---

**Q5. `catch (RuntimeException)` 和 `catch (Exception)` 有什么区别？为什么不 `catch (Throwable)`？**

<details>
<summary>参考答案</summary>

- `RuntimeException`：运行时异常（`NullPointerException`、`IllegalStateException` 等），
  属于"这个观察者坏了"，应该记录并继续；
- `Exception`：包含受检异常。但 `Observer.onEvent` 的签名没有 `throws`，
  编译器保证不会有受检异常，所以多 catch 没有意义；
- `Throwable`：把 `Error` 也包进去了。

**为什么不能 catch `Error`**：

`OutOfMemoryError` 意味着 JVM 已经无法分配对象，这时候继续执行
`deliveries.add(...)` 会再触发一次 OOM。
`StackOverflowError` 意味着调用栈已满，继续调用会再次溢出。

**捕获 `Error` 不是容错，是掩盖灾难。** 正确做法是让进程退出，由外部拉起。

</details>

---

**Q6. `subscribe` 为什么返回 `void`，而 `unsubscribe` 返回 `boolean`？**

<details>
<summary>参考答案</summary>

因为语义不同：

| | 语义 | 重复调用 | 调用方关心什么 |
|---|---|---|---|
| `subscribe` | "确保他在名单里"（幂等） | 无副作用 | 不关心之前有没有 |
| `unsubscribe` | "把他从名单里去掉" | 第二次返回 `false` | **关心到底删掉了没有** |

`unsubscribe` 返回 `false` 的诊断价值：

```java
if (!subject.unsubscribe("fan-9999")) {
    log.warn("fan-9999 本来就没订阅，可能是前端状态不同步");
}
```

如果 `subscribe` 也返回 `boolean`（"是否新增"），会诱导调用方写
"第一次关注才发欢迎邮件"这种代码——那是业务判断，不该由 Subject 兼职。

</details>

---

**Q7. 观察者模式的"失败隔离"具体指什么？为什么 `try` 必须在循环内？**

<details>
<summary>参考答案</summary>

**失败隔离** = 一个观察者抛异常，不影响其他观察者收到通知，
且失败被记录、可追溯。

`try` 必须在**循环体内**：

```java
for (Observer observer : snapshot) {
    try {
        observer.onEvent(event);        // ← try 在这里
    } catch (RuntimeException e) { ... }
}
```

如果 `try` 在循环外：

```java
try {
    for (Observer observer : snapshot) {
        observer.onEvent(event);
    }
} catch (RuntimeException e) {
    // 第 3 个人失败，第 4～N 个人全部没发
}
```

就回到了原项目的老路。**一个括号的位置，决定了整个模式有没有意义。**

</details>

---

**Q8. 观察者模式和发布订阅模式的区别是什么？**

<details>
<summary>参考答案</summary>

**结构上的唯一差别：中间有没有 Broker。**

```text
观察者：Publisher ──持有名单──> Observer
发布订阅：Publisher ──> Broker ──Topic 路由──> Subscriber
```

四个实质差别：

| | 观察者 | 发布订阅 |
|---|---|---|
| 发布者知道有几个人吗 | 知道（`subscriberCount`） | 不知道 |
| 能保证顺序吗 | 能（注册序） | 通常不能 |
| 需要 Topic 吗 | 不需要 | 需要 |
| 能跨进程吗 | 不能 | 可以 |

**什么时候该升级**：订阅需要按主题过滤、发布者不该知道听众数量、需要跨进程。

**什么时候不该**：只有一两个事件类型、需要同步拿到投递结果、本来就在同一模块内。

</details>

---

**Q9. 观察者自己 catch 异常，会有什么后果？**

<details>
<summary>参考答案</summary>

**这是最危险的误区。** 后果：

- `publish` 的报告里这一条记为**成功**；
- 监控看到 100% 成功率；
- 用户投诉"没收到推送"，日志里只有一行中文。

**正确做法**：观察者只负责如实报告"我失败了"，
**兜底是 Subject 的责任**。

```java
// 错误
public void onEvent(Event event) {
    try { doSend(event); }
    catch (Exception e) { System.out.println("发送失败"); }
}

// 正确
public void onEvent(Event event) {
    if (!channelOpen) {
        throw new IllegalStateException("推送通道已关闭：" + deviceId);
    }
    doSend(event);
}
```

</details>

---

**Q10. "跳过"和"失败"有什么区别？为什么 `EmailObserver` 跳过直播时不能抛异常？**

<details>
<summary>参考答案</summary>

- **跳过** = 策略选择（"这个渠道不需要这条事件"）→ 正常返回 → **记为成功**；
- **失败** = 故障（"想发但没发出去"）→ 抛异常 → **记为失败**。

如果跳过也抛异常：

```text
合计：尝试 3 人，成功 2 人，失败 1 人
[失败] 李四 — IllegalStateException: 直播不发邮件
```

**每次直播都会触发一次"失败告警"**，而实际上一切正常。

**这条判断可以推广成一句话**：

> 观察者的"不处理"和"处理失败"，必须能区分开。
> 如果两者都表现为异常，报告就失去了诊断价值。

</details>

---

### 9.2 三道推导题

**推导题一：如果 `ContentCreator` 用 `List<Observer>` 而不是 `Map`，会发生什么？**

<details>
<summary>参考答案</summary>

**三个能力同时丢失：**

1. **无法去重**：`add` 不检查重复，同一个 ID 订阅两次就在名单里两份；
2. **无法按 ID 精确删除**：`remove(Object)` 依赖 `equals`，
   而 `Observer` 没有合理的 `equals` → 只能遍历比对 `id()`，O(n)；
3. **删除是 O(n)** 而不是 O(1)。

**唯一还能用的场景**：观察者没有稳定身份，且允许重复订阅。
这在真实业务里几乎不存在。

**反推**：如果只能用 `List`，为了正确性必须自己做三件事：

```java
// 1. 注册时查重
for (Observer o : list) {
    if (o.id().equals(observer.id())) return;
}
list.add(observer);

// 2. 删除时按 id 遍历
Iterator<Observer> it = list.iterator();
while (it.hasNext()) {
    if (it.next().id().equals(id)) { it.remove(); return true; }
}

// 3. 查找也是 O(n)
```

**这三段就是 `LinkedHashMap` 免费提供的。**

</details>

---

**推导题二：如果把 `publish` 改成返回 `void`，还会有"失败隔离"吗？**

<details>
<summary>参考答案</summary>

**技术上还能做到隔离**（`try-catch` 仍然可以让其余人收到），
**但失败就不可见了**：

```java
public void publish(Event event) {
    for (Observer observer : snapshot) {
        try {
            observer.onEvent(event);
        } catch (RuntimeException ignored) {
            // 吞掉 → 没人知道失败了
        }
    }
}
```

- 调用方只知道"发布完了"，不知道"发给几个人"、"失败几个"；
- 唯一的替代方案是**打日志**，但日志无法被程序消费——
  你不能在代码里写 `if (失败率 > 5%) alert()`；
- 也没法做重试（不知道该重试谁）。

**所以"失败隔离"和"可观测性"是一对**：
隔离让失败不再中断，返回值让失败可被处理。
**只做前者，等于把问题藏起来。**

这也是本项目把 `DispatchReport` / `Delivery` 做出来的原因——
它们不是"额外的设计"，是"失败隔离"的必要配套。

</details>

---

**推导题三：如果观察者需要按优先级排序（高优先级先收到），要改哪里？**

<details>
<summary>参考答案</summary>

**最小改动：在 `subscribe` 时排序，或者换一个有序容器。**

方案 A：注册时带优先级，`publish` 前排序

```java
record Subscription(Observer observer, int priority) { }

private final Map<String, Subscription> observers = new LinkedHashMap<>();

public DispatchReport publish(Event event) {
    List<Subscription> snapshot = new ArrayList<>(observers.values());
    snapshot.sort(Comparator.comparingInt(Subscription::priority).reversed());
    // ...
}
```

**注意 `sort` 必须在快照之后。** 如果直接对 `observers.values()` 排序，
会抛 `UnsupportedOperationException`（`Map` 的 `values()` 不支持排序）。

方案 B：用 `TreeMap` + 优先级当键

```java
private final Map<PriorityKey, Observer> observers = new TreeMap<>();
```

**问题**：优先级会重复，需要复合键（`priority + id`），
而且改优先级等于"删了重加"。**比方案 A 复杂，收益不明显。**

方案 C：`PriorityQueue`

**问题**：`PriorityQueue` 不保证稳定（同优先级顺序不确定），
而且不支持 O(1) 按 ID 删除。

**结论**：方案 A 最合适。改动量：

| 文件 | 改动 |
|---|---|
| `Observer` | 可能加 `int priority()`（或者用 `Subscription` 包一层） |
| `ContentCreator` | `observers` 换类型，`publish` 加 1 行 sort |
| `Subject` | 可能要加重载 `subscribe(observer, priority)` |
| `Delivery` / `DispatchReport` | **不需要改** |

**关键判断**：优先级是"注册时的属性"还是"每次发布时的动态决定"？
- 前者 → 进 `Subscription`；
- 后者 → `publish(event, comparator)` 参数。

**绝大多数业务是前者**（"VIP 用户优先收到"）。

</details>

---

### 9.3 五轮模拟面试

**第一轮：基础概念**

> **面试官**：说说观察者模式。
>
> **你**：一个对象（Subject）维护一组对象的引用（Observer），
> 状态变化时逐个调用它们的同一个方法。
>
> 核心是**单向依赖**：Subject 知道 Observer 接口，Observer 不知道 Subject。
> 所以加一个新观察者，Subject 一行都不用改。
>
> 它解决的是"一对多的通知"，而且这些通知的**具体内容在编译期不该被变化源知道**。

> **面试官**：举个你项目里的例子。
>
> **你**：内容平台的粉丝通知。作者发一条动态，
> 站内信、邮件、推送三个渠道各自处理。三个渠道的代码没有任何共同点，
> 唯一的共同点是它们都实现 `onEvent`。

---

**第二轮：为什么这样设计**

> **面试官**：为什么 `subscribe` 不放在 `Observer` 上？
>
> **你**：因为名单在 Subject 手里。谁持有数据，谁提供操作数据的方法。
>
> 放在 Observer 上会有两个后果：
> 一是必须 `instanceof` 强转成具体 Subject 才能调 `addFollower`，
> 面向接口编程直接破了；
> 二是换个 Subject 实现就静默失败——`instanceof` 不匹配时什么也不做。
>
> 还有一个更隐蔽的：`Followable` 这个接口意味着**每个观察者都必须会关注别人**，
> 但一个日志观察者、一个 Kafka 转发观察者，它们凭什么要会关注？

> **面试官**：那 `Observer` 为什么需要 `id()`？
>
> **你**：因为 `equals` 不能当身份。
>
> 原项目用 `record ConcreteFollower(String name)`，`equals` 按名字比。
> 两个同名的张三 `equals` 返回 true，于是：都进列表、都收到通知、
> 退订时删错人。
>
> `equals` 回答的是"这两个对象是不是同一份数据"，而名单要回答的是
> "这是不是同一个订阅者"。**身份必须是显式的、稳定的，由业务给定的。**

---

**第三轮：踩过的坑**

> **面试官**：观察者模式有什么坑？
>
> **你**：三个，都踩过。
>
> **一、边遍历边改。** 观察者在 `onEvent` 里退订，
> 用 `ArrayList` 直接遍历会抛 `ConcurrentModificationException`。
> 更阴的是——**删倒数第二个元素时不抛异常**，
> 因为 `hasNext()` 是 `cursor != size`，remove 之后两者恰好相等，
> 循环直接结束，最后一个观察者静默漏发。
>
> 修法是先快照再遍历。
>
> **二、一个坏观察者拖累所有人。** 原项目没有任何 `try-catch`，
> 第 2001 个用户的推送 token 失效，后面的 7 万多人全部没收到。
> 修法是把 `try` 放在循环体内，只 catch `RuntimeException`。
>
> **三、观察者自己吞异常。** 这样报告里全是成功，监控一片绿，
> 用户投诉没收到。修法是让异常抛出去，由 Subject 统一记录。

> **面试官**：第二个坑，为什么只 catch `RuntimeException` 不 catch `Throwable`？
>
> **你**：`Throwable` 包含 `Error`。`OutOfMemoryError` 的时候
> 你已经没法分配对象了，继续执行 `deliveries.add(...)` 会再 OOM 一次。
> 捕获 `Error` 不是容错，是掩盖灾难。

---

**第四轮：扩展与取舍**

> **面试官**：如果要支持"我只想收直播通知"，怎么改？
>
> **你**：要看这个过滤属于谁。
>
> **如果是用户自己的选择**，用装饰器，零改动：
>
> ```java
> creator.subscribe(new TypeFilteredObserver(
>         new InAppObserver("fan-7001", "张三"),
>         EventType.LIVE));
> ```
>
> `TypeFilteredObserver` 的 `id()` 和 `displayName()` 都委托给被装饰者，
> 所以 `unsubscribe` 仍然有效。
>
> **如果是平台统一策略**（比如"所有邮件渠道都不发直播"），
> 那应该把过滤条件收进订阅关系，引入 `Subscription(Observer, Predicate<Event>)`，
> 让它跟着订阅走，而不是每个观察者各写一遍 `if`。

> **面试官**：观察者模式和发布订阅有什么区别？
>
> **你**：结构上的唯一差别是**中间有没有 Broker**。
>
> 观察者的 Subject 自己持有名单，知道有几个人；
> 发布订阅的发布者只知道 Topic，名单在 Broker 里。
>
> 由此带来四个差别：观察者能保证顺序、能同步拿到投递结果、不能跨进程；
> 发布订阅顺序不保证、异步、可跨进程。
>
> 判断标准：如果需要按主题过滤、发布者不该知道听众数量、需要跨进程，
> 就该升级到 EventBus 或 MQ。

---

**第五轮：反问与边界**

> **面试官**：你觉得观察者模式被滥用了吗？
>
> **你**：是，而且是重灾区。
>
> 最常见的滥用是**"只有一个观察者"**。这时候它只是把一次方法调用
> 拆成了三步：定义接口、注册、回调。**收益为零，成本是三个文件。**
>
> 第二种滥用是**"需要同步拿到结果"**。观察者的语义是发出去就不管了，
> 如果业务上必须"通知成功才继续"，那它本质上是一次同步调用，
> 应该写成 `boolean result = notifier.notify(event)`。
>
> 第三种是**"观察者之间有依赖顺序"**。那它其实是责任链，不是观察者。

> **面试官**：如果让你重新设计，你会改什么？
>
> **你**：会补三件事，都是生产环境必需的：
>
> 1. **退订句柄**。`subscribe` 返回一个 `Subscription`，
>    实现 `AutoCloseable`，用 try-with-resources 管理生命周期。
>    不加这个，长生命周期的 Subject 持有短生命周期的 Observer 就是内存泄漏；
> 2. **循环通知检测**。观察者在 `onEvent` 里又 `publish`，
>    会无限递归。需要一个深度计数或者 `ThreadLocal` 重入检测；
> 3. **异步支持**。现在的同步版本在一个慢渠道上会阻塞整个发布，
>    真实系统里应该是 `CompletableFuture`，但要同时解决
>    "快照什么时候取"和"失败去哪里"两个问题。
>
> **但我不建议一开始就加这些。** 先让它能正确工作，再加可靠性。
> 一上来就做异步 + 弱引用 + 重试，通常会得到一个谁也不敢改的类。

---

### 9.4 一页纸总结

```text
【观察者模式 · 一页纸】

本质
  一对多 + 单向依赖。
  Subject 知道 Observer 接口；Observer 不知道 Subject。
  加渠道不改 Subject。

四要素
  Subject      持有名单，提供 subscribe / unsubscribe / publish
  Observer     实现 onEvent，只需知道自己
  Event        不可变，自包含（类型 + 作者 + 标题 + 正文）
  名单容器     LinkedHashMap<String, Observer>（去重 + 插入序 + O(1) 删）

五个必须记住的决定
  1. subscribe 在 Subject 上，不在 Observer 上
     —— 谁持有数据，谁提供操作数据的方法
  2. 身份用 id()，不用 equals
     —— equals 是"数据是否相同"，id 是"是不是同一个人"
  3. publish 先快照再遍历
     —— 否则边遍历边删会抛 CME 或静默漏发
  4. try 在循环体内，只 catch RuntimeException
     —— 一个坏人不拖累所有人；不吞 Error
  5. 失败进 DispatchReport，不抛给调用方
     —— 内容发布成功 ≠ 通知全部送达

三个最容易做错的判断
  ✗ 观察者自己 catch 异常        → 报告全是成功，监控看不见
  ✗ 把"跳过"写成抛异常          → 每次直播都触发失败告警
  ✗ 用 Set / HashMap 存名单     → 顺序不稳定，输出不可复现

什么时候不要用
  · 只有一个观察者，且不会增加
  · 需要同步返回结果（那是普通调用）
  · 观察者之间有依赖顺序（那是责任链）
  · 接收方在另一个进程（那是 MQ）

三个生产环境必须补的东西
  · 退订句柄（AutoCloseable）—— 防内存泄漏
  · 循环通知检测 —— 防无限递归
  · 异步投递 —— 防慢渠道阻塞发布

一句话
  观察者模式的难点不在"怎么通知"，
  在"通知失败、通知途中名单变了、同一个人算不算两个"。
```

---

## 附录 A：文件清单

### A.1 源文件（11 个，1535 物理行 / 454 代码行）

| 文件 | 物理行 | 代码行 | 类型 |
|---|---:|---:|---|
| `EventType.java` | 64 | 18 | `enum` |
| `Event.java` | 93 | 30 | `record` |
| `Observer.java` | 120 | 6 | `interface` |
| `Subject.java` | 99 | 7 | `interface` |
| `Delivery.java` | 119 | 37 | `record` |
| `DispatchReport.java` | 141 | 43 | `record` |
| `ContentCreator.java` | 274 | 73 | `class` |
| `InAppObserver.java` | 83 | 30 | `class` |
| `EmailObserver.java` | 113 | 38 | `class` |
| `PushObserver.java` | 144 | 47 | `class` |
| `Client.java` | 285 | 125 | `final class` |
| **合计** | **1535** | **454** | |

### A.2 依赖关系

```text
Client
  ├─> ContentCreator ──implements──> Subject
  │     ├─ 字段 Map<String, Observer>
  │     ├─ publish 返回 DispatchReport
  │     └─ 内部使用 Event / EventType
  │
  ├─> InAppObserver  ──implements──> Observer
  ├─> EmailObserver  ──implements──> Observer
  └─> PushObserver   ──implements──> Observer

DispatchReport
  ├─ 持有 Event
  └─ 持有 List<Delivery>

Delivery
  └─ 只依赖 Observer（读 id / displayName）
```

**注意依赖方向：所有箭头都指向接口或数据，没有一个箭头指向 `ContentCreator`。**
（除了 `Client` 这一层——它是组装者，本来就应该知道具体类。）

### A.3 与原项目的文件对应

| 原项目 | 本项目 | 关系 |
|---|---|---|
| `User.java`（14 行） | `Subject.java`（99 行） | 接口从 1 个方法扩到 4 个（注册/注销/计数/发布） |
| `Follower.java`（13 行） | `Observer.java`（120 行） | 加 `id()` / `displayName()`，去掉 `Followable` |
| `Followable.java`（14 行） | **删除** | 订阅能力移到 `Subject` |
| `ConcreteUser.java`（41 行） | `ContentCreator.java`（274 行） | 换 `List` 为 `LinkedHashMap`，加快照与失败隔离 |
| `ConcreteFollower.java`（30 行） | `InAppObserver.java`（83 行） | 只保留"被通知"的能力 |
| `Client.java`（32 行） | `Client.java`（285 行） | 1 段演示 → 5 段 Demo |
| —— | `Event.java` / `EventType.java` | 新增：事件建模 |
| —— | `Delivery.java` / `DispatchReport.java` | 新增：投递结果与报告 |
| —— | `EmailObserver.java` / `PushObserver.java` | 新增：证明"渠道可以有差异"和"渠道会失败" |

---

## 附录 B：运行方式与验证记录

### B.1 环境

| 项 | 值 |
|---|---|
| JDK | `F:\enCodingApps\JDK\jdk-25` |
| 源码编码 | UTF-8 |
| 编译参数 | `-encoding UTF-8 -Xlint:all` |
| 运行参数 | `-Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8` |
| 操作系统 | Windows（控制台默认代码页 GBK） |

### B.2 编译

```bat
cd E:\codeFile\codeFather\design_patterns_pro
javac -encoding UTF-8 -Xlint:all -d out ^
      src\com\patterns\behavioral\observer\*.java
```

**实际结果：**

```text
EXIT=0
javac 输出：（空）
生成的 class 文件：12 个
```

**"输出为空"是零告警的证据。** `-Xlint:all` 会打开所有可选警告，
包括 `serial`、`fallthrough`、`this-escape` 等。
本项目 12 个 class 全部零告警。

### B.3 运行

```bat
java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 ^
     -cp out com.patterns.behavioral.observer.Client
```

**实际结果：**

```text
83 行输出
```

### B.4 三次运行一致性

| 运行 | 行数 | SHA256 |
|---|---:|---|
| 第 1 次 | 83 | `D71E197C8F2737B0547D86C5930394F906304E72EEB62D9AAC19F922C3F7D0C1` |
| 第 2 次 | 83 | `D71E197C8F2737B0547D86C5930394F906304E72EEB62D9AAC19F922C3F7D0C1` |
| 第 3 次 | 83 | `D71E197C8F2737B0547D86C5930394F906304E72EEB62D9AAC19F922C3F7D0C1` |

**三次完全一致。**

### B.5 本文引用的输出是怎么校验的

第 6 章里五段 Demo 的输出，**不是手工复制粘贴的**。

校验方法：

1. 把真实运行结果保存为 `run1.txt`（83 行）；
2. 从本文提取所有 ` ```text ` 代码块；
3. 找到以 `[Demo N]` 开头的块；
4. 与 `run1.txt` 的对应行范围**逐行逐字符比对**。

**校验结果：**

```text
Demo 1 : MATCH (10 lines)
Demo 2 : MATCH (14 lines)
Demo 3 : MATCH (17 lines)
Demo 4 : MATCH (10 lines)
Demo 5 : MATCH (21 lines)
---
blocks found for demos = 5, matched = 5
```

**5/5 全匹配。**

### B.6 原项目输出的来源

第 2 章的 7 行输出，是把原项目
（`src/BehaviorType/ObserverPattern/`）用同一个 JDK 25 编译运行得到的。

第 2.11 节的 38 行探针输出，是另写一个 `Probe.java` 直接调用原项目的类
（`ConcreteUser` / `ConcreteFollower`）得到的。

**两者都不是编造的。**

### B.7 已知限制

| 限制 | 说明 |
|---|---|
| 单线程 | `ContentCreator` 非线程安全，见 5.8 节 |
| 无网络调用 | 三个观察者都是 `System.out.println`，不是真的发邮件/推送 |
| 无持久化 | 名单只存在内存，进程退出即消失 |
| 无重试 | 失败的 `Delivery` 只被记录，不自动重试（见 6.5 节的建议代码） |
| 无循环检测 | 观察者在 `onEvent` 里 `publish` 会无限递归（见 8.2 节） |

**这五条都不是疏漏，是"示例范围"的主动选择。**
每一条都在正文里有对应的扩展方案。

---

## 附录 C：相关模式交叉索引

| 模式 | 与本项目的关系 | 文档 |
|---|---|---|
| **状态（State）** | 都涉及"行为因条件而变"，但状态是"对象只有一个当前状态"，观察者是"一群订阅者" | [`../state/README.md`](../state/README.md) |
| **中介者（Mediator）** | 都是解耦多方通信，但中介者是**多向**（大家说、我来转），观察者是**单向**（我说、你们听） | 待补 |
| **责任链（Chain of Responsibility）** | 都是"多个处理者"，但责任链**通常一个处理完就停**，观察者是**全部处理** | [`../chainofresponsibility/README.md`](../chainofresponsibility/README.md) |
| **命令（Command）** | 都可用来解耦"发起"与"执行"，但命令是**一对一**且可撤销，观察者是一对多且不可撤销 | [`../command/README.md`](../command/README.md) |
| **策略（Strategy）** | 都是接口 + 多实现，但策略**由调用方选一个**，观察者**全部调用** | [`../strategy/README.md`](../strategy/README.md) |
| **装饰器（Decorator）** | 7.3 节的 `TypeFilteredObserver` 就是装饰器套在观察者上 | [`../../structural/decorator/README.md`](../../structural/decorator/README.md) |

**最容易混淆的一对是"观察者 vs 责任链"：**

```text
观察者：publish → A ✓  B ✓  C ✓      （全部执行，互不影响）
责任链：handle  → A → B → C           （A 处理完可能就不往下传了）
```

---

## 附录 D：原项目七个问题对照表

| # | 原项目问题 | 表现 | 本项目的修复 | 落在哪 |
|---:|---|---|---|---|
| 1 | 关注动作在观察者身上 | `instanceof ConcreteUser` 强转，换个实现静默失败 | `subscribe` / `unsubscribe` 移到 `Subject` | `Subject.java` |
| 2 | 用 `equals` 当身份 | 两个张三互相干扰，退订删错人 | `Observer.id()` 显式身份 | `Observer.java` |
| 3 | 边遍历边删 | CME（常见）或静默漏发（删倒数第二个时） | 先快照再遍历 | `ContentCreator.publish` 第 189 行 |
| 4 | 一个异常全链中断 | 后面的观察者全部收不到，`post()` 也"失败" | `try` 在循环内，只 catch `RuntimeException` | `ContentCreator.publish` 第 193-198 行 |
| 5 | `void` 没有回执 | 无法回答"发给了谁、失败几个" | 返回 `DispatchReport` | `DispatchReport.java` |
| 6 | 事件只有一个 `String` | 加"直播实时推送"要改所有实现 | `Event` + `EventType` | `Event.java` / `EventType.java` |
| 7 | 没有去重、O(n) 删除 | 重复订阅收两条，退订慢 | `LinkedHashMap<String, Observer>` | `ContentCreator.java` 第 76 行 |

**每一条都有对应的探针实证或 Demo 输出：**

| # | 证据 |
|---:|---|
| 2 | 探针 1（38 行输出的第 1-11 行） |
| 3 | 探针 2（场景 A 抛 CME / 场景 B 静默漏发） |
| 4 | 探针 3 |
| 1 | 探针 4 |
| 2/3/4/6/7 | Demo 2 / Demo 3 / Demo 4 / Demo 5 的输出 |

---

## 附录 E：概念速查

| 术语 | 一句话解释 | 本项目的例子 |
|---|---|---|
| **Subject（被观察者）** | 持有观察者名单的一方 | `ContentCreator` |
| **Observer（观察者）** | 被通知的一方 | `InAppObserver` 等 |
| **订阅（subscribe）** | 把自己加进名单 | `creator.subscribe(...)` |
| **退订（unsubscribe）** | 把自己从名单移除 | `creator.unsubscribe("fan-1001")` |
| **广播（publish）** | 把事件发给所有观察者 | `creator.publishArticle(...)` |
| **快照（snapshot）** | 遍历前复制一份名单 | `new ArrayList<>(observers.values())` |
| **失败隔离** | 一个观察者失败不影响其他 | 循环内的 `try-catch` |
| **投递报告** | 一次广播的完整结果 | `DispatchReport` |
| **幂等（idempotent）** | 重复调用结果相同 | `subscribe` 用 `putIfAbsent` |
| **观察者 ID** | 观察者的稳定标识 | `Observer.id()` 返回 `"fan-1001"` |
| **事件（Event）** | 被广播的数据 | `record Event(...)` |
| **空真（vacuous truth）** | 没有元素时"所有都满足"为真 | `allSucceeded()` 在 0 人时返回 `true` |

---

## 附录 F：版本与工具链

| 项 | 值 |
|---|---|
| Java 语言级别 | 25 |
| `record` | Java 16+ |
| `switch` 表达式 | Java 14+ |
| `List.copyOf` | Java 10+ |
| `String.isBlank()` | Java 11+ |
| 文本块（本文未使用） | Java 15+ |
| 构建工具 | 无（`javac` 直接编译） |
| 依赖 | **零**（只用 JDK 标准库） |
| 输出编码 | UTF-8（`-Dstdout.encoding` + `-Dfile.encoding`） |

**为什么不用 Maven / Gradle？**

因为本项目是**模式教学**，不是工程脚手架演示。
一个 `javac` 命令就能跑，读者不需要先配环境。

**为什么零依赖？**

因为观察者模式的价值在于**结构**，不在于用了哪个框架。
引入 Spring 的 `ApplicationEventPublisher` 会让读者分不清
"哪些是模式本身，哪些是框架的封装"。

---

## 写在最后

观察者模式大概是所有设计模式里**最容易被"看懂"、又最容易"写错"**的一个。

看懂它只需要五分钟：一个列表，一个循环，一次回调。

写对它需要想清楚四件事：

1. **名单归谁管**——决定接口长什么样；
2. **身份是什么**——决定能不能正确去重和退订；
3. **遍历时名单变了怎么办**——决定会不会静默漏发；
4. **一个人失败了怎么办**——决定这个模式在生产环境能不能用。

原项目在这四条上各错了一次，而它的 7 行输出**看起来完全正常**。

**这才是设计模式真正的难点：**

> 错的代码在理想数据下能跑，只有在"数据不理想"的时候才暴露。
> 而设计模式的价值，恰恰是让那些"不理想"在写代码的时候就被想到。

最后留一句话，是整篇文档的浓缩：

> **观察者模式不是"我通知你"，而是"我不知道你是谁，
> 但我知道名单在我手里，而且我要为每一个人的失败负责"。**

---

**相关文档**

- [状态模式](../state/README.md)——"行为随条件变化"的另一种解法
- [责任链模式](../chainofresponsibility/README.md)——"多个处理者"的另一种组织方式
- [命令模式](../command/README.md)——"解耦发起与执行"的另一种思路
- [策略模式](../strategy/README.md)——"接口 + 多实现"的最简形式
