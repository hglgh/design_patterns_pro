# 抽象工厂模式：通俗入门

> **第一层「入门」材料**：重点不是「工厂」两个字，而是「一族产品必须配套」。
> 与工厂方法、建造者的完整对比见 [README.md](README.md)。
>
> 可运行代码：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)

---

## 1. 一句话说人话

**抽象工厂就是：不单独买零件，而是从同一个「套装供应商」那里拿一整套，
保证拿到的按钮、复选框、输入框都是同一套风格。**

### 什么需求会让开发者想到它

一个后台系统支持亮色和暗色两套主题。界面上有按钮、复选框，未来还会加输入框。
第一版这样创建：

```java
Button button = factory.createButton("light");
Checkbox checkbox = factory.createCheckbox("dark");
```

代码能编译、能运行，界面上却出现了「亮色按钮 + 暗色复选框」。
用户不一定说得出哪里怪，但一眼就能看出不协调。

这不是「字符串拼错了」这么简单，而是**产品族的一致性没有任何约束**。
抽象工厂要解决的是：**把「造哪一族」变成一个对象，让同一族的产品一起被创建。**

---

## 2. 先看不使用抽象工厂的代码

完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`：

```java
class OldUiFactory {
    OldButton createButton(String theme) {
        return new OldButton(theme);
    }

    OldCheckbox createCheckbox(String theme) {
        return new OldCheckbox(theme);
    }
}
```

调用方：

```java
OldButton button = factory.createButton("light");
OldCheckbox checkbox = factory.createCheckbox("dark");   // 手滑
```

真实输出：

```text
  [按钮] light 主题
  [复选框] dark 主题
  ↑ 编译通过、运行不报错，但一个界面出现了两套主题。
```

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
LoginPage ──▶ OldUiFactory ──▶ new OldButton(theme)
                          └──▶ new OldCheckbox(theme)
```

主题只是两个互不相关的字符串参数，工厂不保证它们来自同一族。

### 3.2 问题出在哪

- 每个创建方法独立接收 `theme`，可以传不同值；
- 主题名是字符串，拼错了也不会编译报错；
- 新增主题要改两个方法；新增产品要改工厂和所有调用点。

| 新需求 | 要改哪里 | 风险 |
|---|---|---|
| 新增蓝色主题 | `createButton` 和 `createCheckbox` 各加分支 | 漏一个就混搭 |
| 新增文本框 | `OldUiFactory` 加方法 + 所有调用点适配 | 改动面大 |
| 保证同一个页面只用一个主题 | 靠调用方自觉 | 无法强制 |

### 3.3 为什么加个校验不够

可以在 `createCheckbox` 里检查主题是否一致，但「一致」这件事需要调用方把
上下文传进来，工厂方法会越来越像参数检查器。**结构问题应该用结构解决**：
让「主题」本身变成工厂对象。

---

## 4. 从问题推导出设计

### 第 1 步：抽出产品接口

```java
interface Button { void render(); }
interface Checkbox { void render(); }
```

**为什么？** 客户端只关心「能渲染」，不关心亮色还是暗色。
**不加会怎样？** 每个页面都要知道自己拿的是哪个具体类，主题无法整体替换。

### 第 2 步：让「主题」成为一个工厂接口

```java
interface ThemeFactory {
    Button createButton();
    Checkbox createCheckbox();
}
```

**为什么需要它？** 因为「按钮和复选框必须配套」这件事，需要一个**共同的创建者**来保证。
只要客户端拿的是同一个 `ThemeFactory`，两个产品就不可能来自不同主题。

**不加会怎样？** 又回到两个独立的创建方法，一致性只能靠约定。

### 第 3 步：每个主题实现一个工厂

```java
class LightThemeFactory implements ThemeFactory {
    public Button createButton() { return new LightButton(); }
    public Checkbox createCheckbox() { return new LightCheckbox(); }
}

class DarkThemeFactory implements ThemeFactory { ... }
```

**为什么工厂里可以写死具体类？** 因为「亮色主题就该配亮色按钮」是这一族内部的规则，
写死在 `LightThemeFactory` 里正是它的职责。变化被隔离在「换一个工厂」这一层。

### 第 4 步：客户端只接收工厂

```java
void renderLogin(ThemeFactory factory) {
    Button button = factory.createButton();
    Checkbox checkbox = factory.createCheckbox();
    ...
}
```

**为什么参数是工厂而不是主题字符串？** 字符串可以传两个不同的值；工厂只有一个，
从结构上消除了混搭的可能。

最终关系：

```text
renderLogin(ThemeFactory)
        │
        ├── LightThemeFactory ──▶ LightButton + LightCheckbox
        ├── DarkThemeFactory  ──▶ DarkButton  + DarkCheckbox
        └── BlueThemeFactory  ──▶ BlueButton  + BlueCheckbox（新增）
```

真实输出（节选）：

```text
  [按钮] 亮色主题
  [复选框] 亮色主题
  —— 换主题：只换一个工厂对象，renderLogin 不改 ——
  [按钮] 暗色主题
  [复选框] 暗色主题
  —— 新增蓝色主题：加 3 个类，renderLogin 不改 ——
  [按钮] 蓝色主题
  [复选框] 蓝色主题
```

---

## 5. 用执行过程理解代码

`renderLogin(new DarkThemeFactory())`：

1. 创建 `DarkThemeFactory`，它不创建任何产品；
2. `renderLogin` 调用 `factory.createButton()` → `DarkButton`；
3. 再调用 `factory.createCheckbox()` → `DarkCheckbox`；
4. 两个产品都来自同一个工厂实例，所以必然同族。

**最容易误解的点**：抽象工厂并没有「防止所有混搭」的魔法。
如果客户端自己 new 两个不同工厂，仍然能混搭。它保证的是：
**只要遵守「一个页面一个工厂」的用法，产品族就自然一致。**

---

## 6. 改造前后对比

### 6.1 新增蓝色主题

| | 改造前 | 改造后 |
|---|---|---|
| 修改已有工厂 | 两个创建方法各加分支 | 0 |
| 新增类 | 0（但分支变多） | `BlueButton`、`BlueCheckbox`、`BlueThemeFactory` |
| 客户端 | 传 `"blue"` 字符串 | 传 `new BlueThemeFactory()` |
| 一致性 | 靠自觉 | 结构保证 |

### 6.2 代价：加「一个产品」很难

如果现在要给所有主题加一个 `TextField`：

- `ThemeFactory` 接口加方法；
- `LightThemeFactory`、`DarkThemeFactory`、`BlueThemeFactory` 全部要实现；
- 所有测试替身也要改。

**「加一族产品容易，加一个产品难」是抽象工厂的固有取舍**，
不是实现没写好。产品种类稳定、主题经常增加时，它才划算。

### 6.3 什么时候不需要

- 只有一个主题、一种产品；
- 产品之间没有配套要求，各自独立创建即可；
- 变化方向是「不断加新产品」而不是「不断加新家族」——那更适合工厂方法或简单工厂。

---

## 7. 适用边界

### 适合

- 多套「皮肤/主题/平台」实现，每套包含多个必须配套的产品；
- 需要保证同一族产品一起使用（数据库驱动 + 连接 + 命令；不同操作系统的控件）；
- 产品族会新增，但产品种类相对稳定。

### 不适合

- 只有一族产品；
- 产品种类经常增加（每加一种都要改接口和所有工厂）；
- 只是想把 `new` 藏起来——普通工厂方法就够。

### 容易混淆

| 对比 | 区别 |
|---|---|
| **工厂方法** | 一个创建者负责**一个**产品；抽象工厂负责**一族**产品 |
| **建造者** | 关注「一个对象分步骤组装」；抽象工厂关注「多个配套对象一起创建」 |
| **策略** | 策略替换的是**算法**；抽象工厂替换的是**产品族** |

---

## 8. 小练习

### 练习 1（场景判断）

一个系统只支持 MySQL 一种数据库，有 `Connection`、`Command`、`Transaction` 三个接口，
且只有一个 `MySqlFactory`。需要抽象工厂吗？

**参考答案**：目前不需要。只有一族产品时，抽象工厂只是多一层间接。
等要支持第二种数据库（且三个产品必须配套）时，再引入抽象工厂，
把「换数据库」变成「换一个工厂」。

### 练习 2（解释设计原因）

为什么 `renderLogin` 的参数是 `ThemeFactory`，而不是 `String theme`？

**参考答案**：字符串是数据，两个方法可以收到不同的值，编译期无法约束一致性；
工厂是对象，一个页面只持有一个，按钮和复选框自然来自同一族。
**把「必须一起使用」的约束编码进类型，而不是写进注释。**

### 练习 3（取舍分析）

产品经理说「先加一个输入框，再加三套主题」。这个方向适合抽象工厂吗？

**参考答案**：要谨慎。加输入框需要修改 `ThemeFactory` 和所有已有工厂，成本高；
加主题则只是新增工厂和产品类。抽象工厂适合「产品种类稳定、族在增加」，
如果产品种类会持续增加，考虑拆小接口或改用其他组合方式。

---

## 9. 读完之后

你应该能回答：

1. 抽象工厂为什么存在？——保证一族产品配套创建，换族只换一个工厂。
2. 原始实现的问题？——每个产品独立按字符串创建，混搭无法被类型系统阻止。
3. 每个角色为什么存在？——产品接口统一使用，工厂接口约束一族，具体工厂决定这一族是谁。
4. 怎么协作？——客户端持有一个工厂 → 连续创建同族产品 → 产品各自渲染。
5. 新场景要不要用？——看是否存在「多族 × 多产品且必须配套」，以及产品种类是否稳定。

继续阅读：

- [深度文档](README.md)：工程版实现与完整取舍；
- [Client.java](Client.java)：工程版入口；
- [工厂方法入门](../factorymethod/BEGINNER.md)：只有一个产品时；
- [相似模式对比](../../../../../docs/02-pattern-comparison.md)。
