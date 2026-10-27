# 建造者模式：通俗入门

> **第一层「入门」材料**：重点不是链式调用的写法，而是「为什么长参数构造器和 setter 都不够用」。
> 完整工程版（含导演者、分步构建、不可变对象）见 [README.md](README.md)。
>
> 可运行代码：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)

---

## 1. 一句话说人话

**建造者就是：像填一张带字段名的表单一样，一步一步描述你要的对象，
最后按「提交」时才一次性校验并生成，生成之后就不能再乱改。**

### 什么需求会让开发者想到它

要发一个 HTTP 请求，需要这些参数：

```text
url、method、headers、body、timeoutSeconds、retries
```

第一版用一个 6 参数构造器。调用方写成：

```java
new HttpRequest("https://api.shop.com/orders", "POST", null, "{\"id\":1}", 0, 0);
```

读代码的人必须回头查构造器定义，才知道第 3 个 `null` 是 headers、第 5 个 `0` 是 timeout。
更糟的是，对象建好之后还能 `setTimeoutSeconds(-3)`，变成一个「非法但没人拦」的请求。

建造者要解决两个问题：**参数太多导致调用点难读**，以及**对象在构建过程中处于非法状态**。

---

## 2. 先看不使用建造者的代码

完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`：

```java
class OldHttpRequest {
    OldHttpRequest(String url, String method, Map<String, String> headers,
                   String body, int timeoutSeconds, int retries) { ... }

    void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
```

调用：

```java
OldHttpRequest request = new OldHttpRequest(
        "https://api.shop.com/orders", "POST", null, "{\"id\":1}", 0, 0);

request.setTimeoutSeconds(-3);
```

真实输出：

```text
  构造出来的请求：OldHttpRequest{method=POST, timeout=0, retries=0, body={"id":1}}
  ↑ 第 3、5、6 个参数是什么？必须回头看定义。
  有人把 timeout 改成 -3：OldHttpRequest{method=POST, timeout=-3, retries=0, body={"id":1}}
  ↑ 对象已经非法，但没有任何地方拦截。
```

---

## 3. 为什么原来的设计不方便

### 3.1 调用点依赖「参数位置」

```text
调用方 ──按位置传参──▶ OldHttpRequest 构造器
        （第 3 个是 headers，第 5 个是 timeout……）
```

位置是隐式契约：参数一多，读和改都容易错；两个 `String` 相邻时，编译器也帮不上忙。

### 3.2 对象有「半成品」状态

```java
OldHttpRequest req = new OldHttpRequest(url, null, null, null, 0, 0);
req.setMethod("POST");        // 在这之前，对象是不完整的
req.setTimeoutSeconds(3);
```

只要 setter 存在，对象就可能被用在「还没设置完」的时候。校验散落在每个 setter 里，
新增一个必填字段时，所有调用点都要补。

### 3.3 加参数会怎样

| 新需求 | 改造前 | 结果 |
|---|---|---|
| 加 `authToken` | 改构造器签名 | 所有调用点都要改 |
| 加参数校验 | 每个 setter 里写一遍 | 漏一个就出脏数据 |
| 让请求不可变、可安全共享 | setter 还在，做不到 | 只能靠约定 |

**为什么加个全参构造器不够？** 参数只会越来越多，调用点会变成一长串 `null`；
而且构造器无法表达「可选参数」和「分步组装」。

---

## 4. 从问题推导出设计

### 第 1 步：把「构建过程」单独放到一个 Builder 里

```java
class Builder {
    private final String url;              // 必填
    private String method = "GET";         // 有默认值
    private final Map<String, String> headers = new HashMap<>();
    private int timeoutSeconds = 5;

    Builder(String url) { ... }
}
```

**为什么需要 Builder？** 让「设置参数」发生在 Builder 上，目标对象可以先不出现。
**不加会怎样？** 只能在目标对象上开一堆 setter，半成品状态继续存在。

### 第 2 步：每个设置方法返回 `this`

```java
Builder method(String method) {
    this.method = method;
    return this;
}
```

**为什么返回 `this`？** 让调用可以链式书写，每个参数前面都带名字，顺序不再重要。
**不加会怎样？** 调用点又变成一行行孤立的 `builder.setXxx(...)`，可读性差一些，
但功能上仍可用——链式只是表达方式，不是模式的本质。

### 第 3 步：`build()` 统一校验并创建目标对象

```java
HttpRequest build() {
    if (timeoutSeconds <= 0) {
        throw new IllegalArgumentException("timeoutSeconds 必须大于 0");
    }
    return new HttpRequest(this);
}
```

**为什么校验放在 build()？** 这时所有参数都已就位，可以一次性检查跨字段规则。
**不放会怎样？** 校验散落在 setter 里，构造过程中可能已经产生非法中间状态。

### 第 4 步：目标对象只读

```java
private HttpRequest(Builder builder) { ... }   // 构造器私有
// 所有字段 final，没有 setter
```

**为什么？** 构建完成后对象不会再变，可以安全传递、共享、缓存。
**不加会怎样？** 别人拿到对象后还能改，之前校验过的约束可能再次被破坏。

最终关系：

```text
调用方 ──▶ HttpRequest.builder(url)
             │ .method("POST")
             │ .header(...)
             │ .timeoutSeconds(3)
             ▼
          Builder（可变，收集参数）
             │ build() 校验
             ▼
          HttpRequest（不可变）
```

真实输出（节选）：

```text
  HttpRequest{method=POST, timeout=3, retries=2, headers={Content-Type=application/json}, body={"id":1}}
  ↑ 每个参数前面都有名字，顺序不再重要。
  非法参数在 build() 被拦住：timeoutSeconds 必须大于 0，当前 -3
```

---

## 5. 用执行过程理解代码

```java
HttpRequest request = HttpRequest.builder("https://api.shop.com/orders")
        .method("POST")
        .header("Content-Type", "application/json")
        .timeoutSeconds(3)
        .build();
```

1. `builder(url)` 创建 Builder，url 必填、当场校验；
2. `method` / `header` / `timeoutSeconds` 都返回同一个 Builder，参数被逐步填进去；
3. `build()` 检查 timeout、retries；
4. 通过后调用私有构造器，把 Builder 的字段复制到 `HttpRequest`；
5. headers 被复制成不可修改的 Map，之后外部改不到它。

**最容易忽略的一行**：

```java
this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
```

如果直接 `this.headers = builder.headers`，Builder 之后还能改这个 Map，
「不可变对象」就被破坏了一个口子。

---

## 6. 改造前后对比

### 6.1 新增 `authToken`

| | 改造前 | 改造后 |
|---|---|---|
| 目标类 | 改构造器签名 | 加一个 final 字段 |
| Builder | — | 加一个设置方法 |
| 已有调用点 | 全部要改（参数位置变了） | **不用改**（不设置就用默认值） |
| 校验 | 散落 | 集中在 build() |

### 6.2 代价

- 多一个 Builder 类，字段要重复声明；
- 代码量比「一个构造器」多；
- 对象不可变，要改只能重新 build；
- 如果参数只有 2～3 个，构造器更短更清楚。

### 6.3 什么时候不需要

- 参数少且都必填；
- 对象允许在生命周期内多次修改；
- 只是想把 setter 换成链式，没有校验/不可变需求——收益很小。

---

## 7. 适用边界

### 适合

- 参数多，且有大量可选参数/默认值；
- 构建过程需要分步骤，或步骤顺序有意义；
- 需要在「完成构建」时做跨字段校验；
- 希望得到不可变对象。

### 不适合

- 参数很少；
- 对象本来就是可变的、需要频繁修改；
- 构建逻辑非常简单，引入 Builder 只是增加跳转。

### 容易混淆

| 对比 | 区别 |
|---|---|
| **工厂方法** | 关注「创建哪一个实现」，一步完成；建造者关注「一个对象怎么分步组装」 |
| **抽象工厂** | 创建一族配套对象；建造者只构建一个复杂对象 |
| **setter** | setter 修改已存在对象，允许半成品；Builder 在构建完成后才产出对象 |
| **record / Lombok** | 是减少样板代码的工具，不改变「分步构建 + 校验」这个设计动机 |

---

## 8. 小练习

### 练习 1（场景判断）

一个 `Point` 类只有 `x`、`y` 两个必填参数。需要建造者吗？

**参考答案**：不需要。两个参数用构造器 `new Point(1, 2)` 最清楚；
Builder 会多出一个类、多一层调用，收益为负。

### 练习 2（解释设计原因）

为什么 `HttpRequest` 的构造器要私有，字段要 `final`？

**参考答案**：私有构造器保证只能通过 Builder 创建，校验不会被绕过；
`final` 保证构建完成后引用不再变化。两者一起，才让「build() 校验过的对象」
在之后的使用中仍然合法。

### 练习 3（找漏洞）

如果把 `build()` 里的 headers 拷贝去掉，直接 `this.headers = builder.headers`，
会有什么问题？

**参考答案**：Builder 在 `build()` 返回后仍可被持有和修改，改 headers 会同时改变
已经生成的 `HttpRequest`，不可变承诺被破坏。防御性拷贝是让「不可变」真正成立的必要步骤。

---

## 9. 读完之后

你应该能回答：

1. 建造者为什么存在？——解决长参数构造器难读、setter 留下非法中间态的问题。
2. 原始实现的问题？——按位置传参、对象可被改成非法、校验分散。
3. 每个角色为什么存在？——Builder 收集参数，build() 校验，目标对象只读。
4. 怎么协作？——链式设置 → build 校验 → 私有构造器复制字段 → 返回不可变对象。
5. 新场景要不要用？——看参数数量、可选参数、校验和不可变需求，而不是看别人用了。

继续阅读：

- [深度文档](README.md)：工程版建造者、导演者与分步构建；
- [Client.java](Client.java)：工程版入口；
- [工厂方法入门](../factorymethod/BEGINNER.md)：只需要选择实现时；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)。
