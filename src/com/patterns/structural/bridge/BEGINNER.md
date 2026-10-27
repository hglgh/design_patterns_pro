# 桥接模式：通俗入门

> **第一层「入门」材料**：重点不是「桥」这个比喻，而是「两个维度各自变化时，继承为什么会爆炸」。
> 桥接与策略、适配器的深入对比见 [README.md](README.md)。
>
> 可运行代码：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)

---

## 1. 一句话说人话

**桥接就是：当一件事有两个独立的变化方向时，别用继承把它们绑死，
而是把其中一个方向抽出来，用「持有」代替「继承」。**

### 什么需求会让开发者想到它

消息系统要支持两种消息（文本、图片）和两种发送渠道（短信、邮件）。
用继承写，需要：

```text
TextSmsMessage、TextEmailMessage、ImageSmsMessage、ImageEmailMessage
```

2 × 2 = 4 个类。产品说下个月加「推送」，于是再加 2 个类；
再下个月加「语音消息」，又加 3 个类。3 × 3 = 9 个类，4 × 4 = 16 个。

真正的问题不是类多，而是：**消息类型和发送渠道是两个独立变化的方向，
继承却要求你把它们焊死在一个类名里。**

---

## 2. 先看不使用桥接的代码

完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`：

```java
class TextSmsMessage {
    void send(String text) {
        System.out.println("  [短信] 文本：" + text);
    }
}

class TextEmailMessage { ... }
class ImageSmsMessage  { ... }
class ImageEmailMessage { ... }
```

调用：

```java
new TextSmsMessage().send("订单已发货");
new TextEmailMessage().send("订单已发货");
new ImageSmsMessage().send("https://img.shop.com/1.png");
new ImageEmailMessage().send("https://img.shop.com/1.png");
```

真实输出：

```text
  [短信] 文本：订单已发货
  [邮件] 文本：订单已发货
  [短信] 图片：https://img.shop.com/1.png
  [邮件] 图片：https://img.shop.com/1.png
```

能跑，但每加一个维度值，类的数量按乘法增长。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
TextSmsMessage ──继承──▶ （没有共同父类，只是各写各的）
TextEmailMessage
ImageSmsMessage
ImageEmailMessage
```

每个类都同时包含两个决定：**发什么**（文本/图片）和**怎么发**（短信/邮件）。
两个决定被写进同一个类名，无法单独复用。

### 3.2 新增一个维度值会怎样

| 新需求 | 要新增的类 | 数量 |
|---|---|---|
| 加「推送」渠道 | TextPush、ImagePush | 2 |
| 加「语音」消息 | VoiceSms、VoiceEmail、VoicePush | 3 |
| 再加「站内信」 | TextInApp、ImageInApp、VoiceInApp… | 3 |

**为什么加个 if-else 不够？** 如果在 `send` 里用 if 判断渠道，
那所有消息类都要重复这段判断，渠道逻辑还是散落的；
而且「消息类型 × 渠道」的组合仍然要一个一个写。

---

## 4. 从问题推导出设计

### 第 1 步：抽出「渠道」这个维度

```java
interface Channel {
    void deliver(String content);
}
```

**为什么先抽渠道？** 因为渠道的发送方式（短信网关、SMTP、推送服务）是独立变化的，
和消息内容无关。**不抽会怎样？** 渠道逻辑继续散落在每个消息类里。

### 第 2 步：让消息持有渠道，而不是继承渠道

```java
abstract class Message {
    protected final Channel channel;

    Message(Channel channel) {
        this.channel = channel;
    }

    abstract void send();
}
```

**为什么用持有？** 一个文本消息可以在运行时选择短信或邮件，不需要为每种组合建类。
**不用持有会怎样？** 只能回到 `TextSmsMessage`、`TextEmailMessage` 的乘法爆炸。

### 第 3 步：消息子类只关心「发什么」

```java
class TextMessage extends Message {
    private final String text;

    void send() {
        channel.deliver("文本：" + text);
    }
}
```

**为什么消息类不直接打印？** 打印/发送是渠道的职责；消息类只负责组织内容。
**不分开会怎样？** 渠道一换，所有消息类都要改。

### 第 4 步：渠道子类只关心「怎么发」

```java
class SmsChannel implements Channel {
    public void deliver(String content) {
        System.out.println("  [短信] " + content);
    }
}
```

最终关系：

```text
        Message（发什么） ──持有──▶ Channel（怎么发）
        ▲                              ▲
   TextMessage                    SmsChannel
   ImageMessage                   EmailChannel
                                  PushChannel（新增）
```

两个继承体系各自扩展，组合在运行时完成。2 种消息 + 3 种渠道 = 5 个类，
而不是 6 个组合类；新增渠道只加 1 个类。

真实输出（节选）：

```text
  [短信] 文本：订单已发货
  [邮件] 文本：订单已发货
  [短信] 图片：https://img.shop.com/1.png
  [邮件] 图片：https://img.shop.com/1.png
  —— 新增推送渠道：只加一个 Channel，消息类都不改 ——
  [推送] 文本：订单已发货
  [推送] 图片：https://img.shop.com/1.png
```

---

## 5. 用执行过程理解代码

```java
Message message = new TextMessage(new PushChannel(), "订单已发货");
message.send();
```

1. 创建 `PushChannel`（实现维度）；
2. 创建 `TextMessage`，把 channel 存进父类字段；
3. `message.send()` 动态分派到 `TextMessage.send()`；
4. `TextMessage.send()` 调 `channel.deliver("文本：...")`；
5. 动态分派到 `PushChannel.deliver`，输出推送。

**最容易混淆的点**：`Message` 是抽象类，`Channel` 是接口，两者是**组合**关系，
不是继承关系。桥接的关键一行是：

```java
protected final Channel channel;
```

如果写成 `class TextMessage extends SmsChannel`，就退化成继承了。

---

## 6. 改造前后对比

### 6.1 新增推送渠道

| | 改造前 | 改造后 |
|---|---|---|
| 新增类 | TextPush、ImagePush（2 个） | PushChannel（1 个） |
| 修改消息类 | 0，但组合类数量翻倍 | 0 |
| 运行时换渠道 | 做不到（类名写死） | 换一个 Channel 对象即可 |
| 渠道逻辑复用 | 每个组合类重复 | 只在 Channel 里一份 |

### 6.2 代价

- 多了一层抽象，简单场景读起来更绕；
- 两个维度必须真的独立变化，否则只是把 if/else 换了个地方；
- 如果只有一个变化维度，用策略模式更直接；
- 组合关系在运行时才确定，阅读代码时要追到装配处才知道用的哪个渠道。

### 6.3 什么时候不需要

- 只有一种消息类型，或只有一种渠道；
- 组合数量很少且固定，写几个类更直观；
- 变化只有一个方向——那就是策略，不是桥接。

---

## 7. 适用边界

### 适合

- 两个维度都会独立扩展（消息类型 × 渠道、图形 × 渲染 API、遥控器 × 设备）；
- 希望避免继承层级的乘法爆炸；
- 需要在运行时切换其中一个维度；
- 两个维度的实现细节希望各自独立演化。

### 不适合

- 只有一个维度变化；
- 两个维度其实强相关，拆开后还是要互相判断；
- 组合数量很少、未来也不会增加。

### 容易混淆

| 模式 | 区别 |
|---|---|
| **策略** | 策略是「一个上下文，运行时换一种算法」；桥接是「两个继承体系各自变化，再用组合连接」 |
| **适配器** | 适配器解决「已有接口不兼容」；桥接是**提前设计**成两个维度，通常没有既有接口要转换 |
| **抽象工厂** | 抽象工厂创建一族产品；桥接是把一个维度的实现注入另一个维度 |

> 桥接和策略在代码上可能长得一样（都是「持有接口」），区别在**意图**：
> 策略是替换算法，桥接是拆开两个会各自膨胀的继承维度。

---

## 8. 小练习

### 练习 1（场景判断）

一个报表导出功能，只支持「导出 PDF」，未来也只可能导出 PDF，但导出的
「纸张大小」有 A4/A3 两种。需要桥接吗？

**参考答案**：不需要。只有一个变化维度（纸张大小），用策略或一个参数就够。
桥接的前提是**两个维度都独立变化**，为了两个值就拆两个继承体系是过度设计。

### 练习 2（解释设计原因）

为什么 `TextMessage` 不直接 `extends SmsChannel`？

**参考答案**：继承会把「消息类型」和「渠道」焊死，`TextMessage` 只能是短信，
想发邮件就得再写一个类；而且 Java 单继承，继承渠道后不能再继承消息体系的父类。
持有 `Channel` 让两者独立变化，还能运行时替换。

### 练习 3（扩展推导）

现在要加「语音消息」和「站内信渠道」。分别要加几个类？已有类要改吗？

**参考答案**：语音消息加 1 个 `VoiceMessage`；站内信加 1 个 `InAppChannel`。
已有消息类、渠道类和客户端方法都不需要改，只是装配时多一种组合。
如果是继承方案，语音 × 3 个渠道要加 3 个类，站内信 × 3 种消息又要加 3 个。

---

## 9. 读完之后

你应该能回答：

1. 桥接为什么存在？——把两个独立变化的维度拆开，避免继承层级乘法爆炸。
2. 原始实现的问题？——每个组合一个类，加一个维度值就新增一批类。
3. 每个角色为什么存在？——`Message` 管内容，`Channel` 管发送，组合关系连接两者。
4. 怎么协作？——创建具体消息时注入具体渠道 → `send()` → 渠道 `deliver()`。
5. 新场景要不要用？——看是否存在两个会各自扩展的维度，而不是看「能不能拆」。

继续阅读：

- [深度文档](README.md)：桥接的工程版与更多维度案例；
- [Client.java](Client.java)：工程版入口；
- [策略入门](../../behavioral/strategy/BEGINNER.md)：只有一个变化维度时；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)。
