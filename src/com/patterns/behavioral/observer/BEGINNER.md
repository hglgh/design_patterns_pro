# 观察者模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「发布者为什么不该认识每个接收者」，
> 以及通知失败、名单变化这些坑该由谁处理。
>
> 第二层：[README.md](README.md)（事件类型、投递报告、通知失败与名单一致性）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**观察者就是「我发一条动态，名单上的人自己来收」；**
**发布者只维护名单，不知道名单上的人具体是谁、是邮件还是短信。**

### 什么需求会让开发者想到它

一个内容平台，创作者发布一条动态后，要通知：

- 邮件订阅用户；
- App 站内信；
- 手机推送。

第一版很自然：`Creator.publish()` 里依次调用 `push.send()`、`email.send()`、`app.send()`。
三个渠道，三行代码，很清楚。

后来：要加短信、要能退订推送、某个渠道偶尔超时。
你会发现，`Creator` 不仅知道所有渠道，还要负责「失败怎么办」和「谁订阅了」。

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class OldCreator {
    private final OldPush push;
    private final OldEmail email;
    private final OldApp app;

    void publish(String title) {
        System.out.println("[Creator] 发布：" + title);
        push.send(title);
        email.send(title);
        app.send(title);
    }
}
```

其中 `OldPush.send()` 在标题包含「故障」时抛异常。

真实输出（节选）：

```text
[Creator] 发布：设计模式入门
  [推送] 设计模式入门
  [邮件] 设计模式入门
  [站内信] 设计模式入门
—— 推送网关超时 ——
[Creator] 发布：故障演练
[系统] 发布失败：推送网关超时
邮件和站内信都没有收到「故障演练」
```

**推送排在最前面，它一挂，后面两个渠道全部收不到。**
这不是推送的错，是 `publish()` 把三个渠道串成了一条必须全部成功的链。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
OldCreator ──▶ OldPush
           ──▶ OldEmail
           ──▶ OldApp
```

`OldCreator` 认识每一个具体渠道，连构造器都要按渠道数量扩展。
更关键的是：**渠道之间的顺序和失败关系也写在它里面**。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 新增短信渠道 | 构造器加参数、字段加一个、`publish()` 加一行 | 改核心类，所有创建 `Creator` 的地方都要改 |
| 退订推送 | 删掉 `push.send()` 或加 `if (pushEnabled)` | 退订状态侵入发布逻辑 |
| 调整渠道顺序 | 改 `publish()` 里的调用顺序 | 顺序是硬编码的 |
| 一个渠道失败不影响其他 | 每个调用外面包 `try/catch` | 发布方法越来越长 |
| 运行时决定订阅者 | 做不到，渠道在编译期固定 | 只能重启/改代码 |

### 3.3 为什么加一个 `if` 不够

加 `if (pushEnabled)` 只能处理「某个渠道开不开」，
解决不了「谁订阅了」「运行时增删」「一个失败是否影响其他」。
这些问题的共同点是：**发布者不该持有渠道，只该持有「一份名单」**。

---

## 4. 从问题推导出设计

### 第一步：抽出一个「被通知」的接口

```java
interface Observer {
    void onPublish(String title);
}
```

- **为什么需要它？** `Creator` 要能统一地通知邮件、站内信、推送，而不认识它们的具体类型。
- **不抽接口会怎样？** `Creator` 还是得为每种渠道写一个字段和一个调用。
- **它和具体渠道的区别？** 接口只声明「收到通知时做什么」，不关心怎么发。

### 第二步：让 Creator 只维护名单

```java
class Creator {
    private final List<Observer> observers = new ArrayList<>();

    void subscribe(Observer observer) {
        observers.add(observer);
    }

    void unsubscribe(Observer observer) {
        observers.remove(observer);
    }

    void publish(String title) {
        System.out.println("[Creator] 发布：" + title);

        int failed = 0;
        List<Observer> snapshot = new ArrayList<>(observers);
        for (Observer observer : snapshot) {
            try {
                observer.onPublish(title);
            } catch (RuntimeException e) {
                failed++;
                System.out.println("[系统] 一个渠道失败：" + e.getMessage());
            }
        }

        if (failed > 0) {
            System.out.println("通知结束：" + failed + " 个渠道失败，其他渠道不受影响");
        }
    }
}
```

这里有三个关键决定：

1. **遍历前复制名单**：通知过程中有人订阅/退订，不会让当前遍历抛
   `ConcurrentModificationException`，也不会漏掉/重复当前轮的人。
2. **每个观察者单独 `try/catch`**：一个渠道失败，不阻断后面的渠道。
3. **失败计数**：发布者知道本轮有几个失败，但**不关心具体是谁失败**——
   具体错误由渠道自己记录。

> 注意：第 2、3 点是**发布者的策略**，不是观察者模式自动提供的能力。
> 只写 `for (Observer o : observers) o.onPublish(title);` 也是观察者，
> 但一个渠道抛异常就会中断整轮通知。

### 第三步：渠道只实现「收到后做什么」

```java
class EmailSubscriber implements Observer {
    public void onPublish(String title) {
        System.out.println("[邮件] " + title);
    }
}

class BrokenPushSubscriber implements Observer {
    public void onPublish(String title) {
        throw new IllegalStateException("推送网关超时");
    }
}
```

渠道不知道 `Creator` 长什么样，也不知道还有哪些渠道。
**它们只对「被通知」这件事负责。**

### 最终对象关系

```text
Creator（Subject）
  │ 持有 List<Observer>
  │ publish() 遍历名单
  ▼
Observer（接口）
  ▲        ▲          ▲
Email    App        Push / Sms / Broken...
```

---

## 5. 用具体数据走一遍

### 5.1 订阅三个渠道

```java
creator.subscribe(email);
creator.subscribe(app);
creator.subscribe(push);

creator.publish("设计模式入门");
```

```text
名单：[email, app, push]
→ email.onPublish → [邮件]
→ app.onPublish   → [站内信]
→ push.onPublish  → [推送]
```

真实输出（节选）：

```text
[Creator] 发布：设计模式入门
  [邮件] 设计模式入门
  [站内信] 设计模式入门
  [推送] 设计模式入门
```

### 5.2 退订推送

```java
creator.unsubscribe(push);
creator.publish("第二期");
```

`unsubscribe` 按对象身份从名单里移除 `push`。
下一轮只通知 `email`、`app`：

```text
[Creator] 发布：第二期
  [邮件] 第二期
  [站内信] 第二期
```

### 5.3 新增短信

```java
creator.subscribe(new SmsSubscriber());
creator.publish("第三期");
```

`Creator` 一行没改，只是名单多了一个对象：

```text
[Creator] 发布：第三期
  [邮件] 第三期
  [站内信] 第三期
  [短信] 第三期
```

### 5.4 一个渠道故障

```java
creator.subscribe(new BrokenPushSubscriber());
creator.publish("故障演练");
```

```text
① email 成功
② app 成功
③ sms 成功
④ broken 抛异常 → 被 publish 的 catch 捕获，failed=1
⑤ 继续（本轮没有更多观察者）
⑥ 打印「1 个渠道失败，其他渠道不受影响」
```

真实输出：

```text
[Creator] 发布：故障演练
  [邮件] 故障演练
  [站内信] 故障演练
  [短信] 故障演练
  [系统] 一个渠道失败：推送网关超时
  通知结束：1 个渠道失败，其他渠道不受影响
```

### 5.5 最容易写错的三处

1. **直接遍历 `observers` 并在通知中增删**：会抛 `ConcurrentModificationException`，
   或者出现「这一轮到底通没通知到」的不确定行为。先复制一份名单是常用解法。
2. **把 `try/catch` 放在循环外**：那样只能保住异常之前的渠道，和改造前一样。
   要放在**循环内、每个观察者外面**。
3. **重复订阅**：`List` 允许同一个对象加入两次，它会收到两次通知。
   如果业务要求「同一个人只算一次」，要用 `Set` 或按用户 ID 去重。

---

## 6. 改造前后对比

### 新增短信渠道

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | `OldCreator` 的构造器、字段、`publish()` | 新增 `SmsSubscriber` |
| 要改的旧代码 | 三处 | 无（调用方 `subscribe` 一行） |
| 退订 | 改 `publish()` 或加开关 | `unsubscribe` |
| 失败隔离 | 自己写 `try/catch` | 发布策略里统一处理 |

### 但调用方仍然要改

- 新渠道要**创建并订阅**；
- 如果渠道需要配置（收件人、模板），调用方要传进去；
- 通知顺序仍然不保证，依赖顺序的业务不能靠观察者。

### 引入模式新增的成本

- 订阅名单是**运行期隐式状态**，排查「谁收到了」要看日志/调试；
- 同步通知会拖慢发布接口，订阅者多时要改异步；
- 忘记退订会导致对象无法回收（长生命周期 Subject 持有短生命周期 Observer）；
- 通知失败、重试、幂等都要额外设计，模式本身不提供。

---

## 7. 适用边界

### 适合用

- 一个事件发生后，需要**多个互不相关的消费者**各自处理；
- 消费者需要**运行时增删**（订阅/退订）；
- 发布者不应该依赖消费者的具体类型；
- 消费者之间不需要严格顺序，也不需要把处理结果返回给发布者。

### 不适合用

- 只有一个固定消费者——直接调用最清楚；
- 需要严格顺序、事务一致性、聚合返回值——这不是观察者擅长的；
- 消费者处理很重且必须可靠——应该上消息队列/事件总线，而不是进程内回调。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 中介者 | 中介者**知道角色和路由**，按主题定向通知；观察者是**广播名单**，发布者不知道谁是谁 |
| 发布订阅 | 观察者通常是**进程内、同步**的直接回调；发布订阅通过 broker，支持异步、持久化、跨进程 |
| 责任链 | 责任链是**一个请求沿链传递，可能停止**；观察者是**所有订阅者都收到** |

> 观察者的核心不是 `for` 循环，而是**发布者只依赖接口、名单在运行期可变**。
> 一个写死三个渠道的 `for` 循环，不是观察者。

---

## 8. 小练习

### 练习 1（场景判断）

「订单支付成功」后要做：发邮件、加积分、通知库存、写审计日志。
要不要用观察者？

**答案**：适合。四个动作互不依赖，未来还会加「发优惠券」「通知推荐系统」；
支付服务不应该知道积分和库存的类。用观察者后，新增消费者只加一个订阅者。
但要注意：积分和库存如果必须与支付**同一事务**成功，进程内观察者保证不了，
需要事务消息/可靠事件，这已经超出观察者的范围。

### 练习 2（解释设计原因）

为什么 `Creator` 只持有 `Observer` 接口，而不是 `EmailSubscriber` 等具体类？
去掉接口会怎样？

**答案**：接口让 `Creator` 只依赖「能被通知」这件事，新增渠道不改 `Creator`。
去掉接口，`Creator` 就要为每种渠道加字段、构造器参数和调用，
退订/启用也要写 `if`，又回到改造前。

### 练习 3（执行过程）

发布过程中，某个观察者在自己的 `onPublish` 里调用了 `creator.unsubscribe(this)`。
这一轮它还会收到通知吗？下一轮呢？

**答案**：示例先复制了名单，所以这一轮它**仍然会收到**（如果还没轮到它），
下一轮不会。如果不复制名单，直接在原 `ArrayList` 上遍历并删除，
可能抛 `ConcurrentModificationException`，行为取决于实现。
**「这一轮算不算」是业务语义，必须明确。**

### 练习 4（边界推导）

`BrokenPushSubscriber` 抛异常后，其他渠道仍然收到通知。这是观察者模式自动做到的吗？
如果不是，谁该负责？

**答案**：不是。示例在 `publish()` 里对每个观察者单独 `try/catch`，才做到失败隔离。
如果只写一个普通 `for` 循环，异常会直接中断整轮通知。
**观察者只提供「名单 + 回调」的结构，失败策略由发布者决定。**

---

## 读完后应能回答的 5 个问题

1. 观察者模式让发布者依赖了什么？为什么这能支持运行时新增渠道？
2. 为什么通知前要复制一份名单？
3. 一个渠道失败不影响其他渠道，靠的是模式还是发布策略？
4. 观察者和中介者的通知方式有什么根本不同？
5. 什么情况下应该用消息队列而不是进程内观察者？

---

## 下一步

- 深度文档：[README.md](README.md)（事件类型、投递报告、通知失败处理）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
- 组合案例：[combined/orderservice](../../combined/orderservice/)（订单事件通知）
