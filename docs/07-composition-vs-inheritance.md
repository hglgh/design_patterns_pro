# 组合 vs 继承：设计模式的第一道分水岭

> 本文不对比「模式与模式」（那是 [02-pattern-comparison.md](02-pattern-comparison.md) 的活），
> 只对比**两种复用机制**：你想要的代码 / 能力，是写进父类**继承**下来，还是做成对象**装**进来。
>
> 一句话先说：**继承表达「你是什么」，组合表达「你用什么」。**
> 而需求的变化，绝大多数发生在「用什么」上，不是在「是什么」上。

## 0. 为什么这个概念是设计模式的前提

23 个模式里，除了单例和模板方法，**其余 21 个都是组合的变体**：

| 模式 | 组合了什么 |
|---|---|
| 策略 | 算法对象 |
| 装饰器 / 代理 / 责任链 | 同类型的另一个对象 |
| 组合模式 | 多个同类型子节点 |
| 桥接 | 另一个维度的实现对象 |
| 观察者 | 观察者列表 |
| 状态 | 当前状态对象 |
| 命令 | 接收者 + 参数 |
| 访问者 | 访问者对象 |

所以「组合优于继承」不是一句风格建议，而是**设计模式大半方法论的来源**。搞不清这一条，后面 21 个模式都只能靠背。

---

## 1. 先把两个词钉死

### 1.1 is-a 与 has-a

| | 继承 `extends` | 组合 `has-a` |
|---|---|---|
| 语义 | 子类**是一种**父类（猫是一种动物） | 当前类**有一个**对象（车有一个引擎） |
| 复用来源 | 父类的方法实现 | 被持有对象的方法实现 |
| 代码长相 | `class A extends B` | `private final B b;` + 构造器传入 |
| 数量 | 只能有 **1** 个父类 | 想持有几个就几个 |

### 1.2 白盒复用 vs 黑盒复用

这是最实用的一组区分：

- **继承是白盒**：子类能看到父类的 `protected` 成员，**父类的内部结构变成了子类的依赖**。父类换个字段类型、改个初始化顺序，子类可能编译不过或行为错乱。
- **组合是黑盒**：宿主只通过**接口**调用被持有者，对方内部怎么实现与宿主无关。对方重构，宿主零改动。

一句话：**继承让你和父类的实现细节绑定，组合只让你和对方的契约绑定。**

### 1.3 一个必须澄清的术语坑

「组合」这个词被用在两个完全不同的语境里，混在一起会答错题：

| 语境 | 「组合」指什么 | 典型问法 |
|---|---|---|
| **UML 建模** | composition（组合/合成）= **强拥有**，整体销毁部分跟着销毁（人 ↔ 心脏）；与 aggregation（聚合，弱拥有，如部门 ↔ 员工）相对 | 「组合和聚合有什么区别？」 |
| **设计原则** | Favor object composition over class inheritance 里的 composition = **对象组合 + 委托**的统称，**包含**聚合，甚至包含「只是用一个对象」 | 「为什么优先组合而不是继承？」 |

**结论**：被问「组合 vs 聚合」答 UML 的强弱拥有；被问「组合 vs 继承」答复用机制。本文全篇讲的是后者。

---

## 2. 四个现实场景，逐条映射到代码

每个场景固定四段：**现实痛点 → 元素映射 → 项目代码 → 比喻失效边界**。

### 2.1 装机：组合的最小形态（对应策略模式）

**现实痛点**：买整机，显卡焊死在主板上。想升级只能整台换。

**元素映射**：

| 现实世界 | 代码世界 |
|---|---|
| 主板上的插槽规格（PCIe / DDR5） | 字段声明成**接口类型** |
| 显卡、内存条（现成的配件） | 实现该接口的类 |
| 你自己把配件插上去 | 构造器注入 |
| 通电后显卡开始渲染 | 方法里**转发**给配件 |
| 换一块显卡 | 传入另一个实现，宿主代码不动 |

**完整可运行示例**（已用 `javac --release 25` 编译并运行，输出见代码后）：

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 组合的最小可运行示例：主机只持有插槽，干活的是配件。
 * 运行：java CompositionDemo.java
 */
public class CompositionDemo {

    /** 插槽规格：价格规则。 */
    interface PriceRule {
        String name();

        BigDecimal apply(BigDecimal original);
    }

    /** 配件一：百分比打折。 */
    static final class PercentOff implements PriceRule {
        private final BigDecimal rate;

        PercentOff(String rate) {
            this.rate = new BigDecimal(rate);
        }

        @Override
        public String name() {
            return "打折 " + rate.movePointRight(2).toPlainString() + "%";
        }

        @Override
        public BigDecimal apply(BigDecimal original) {
            return original.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** 配件二：满减。 */
    static final class FullReduction implements PriceRule {
        private final BigDecimal threshold;
        private final BigDecimal cut;

        FullReduction(String threshold, String cut) {
            this.threshold = new BigDecimal(threshold);
            this.cut = new BigDecimal(cut);
        }

        @Override
        public String name() {
            return "满 " + threshold.toPlainString() + " 减 " + cut.toPlainString();
        }

        @Override
        public BigDecimal apply(BigDecimal original) {
            BigDecimal result = original.compareTo(threshold) >= 0 ? original.subtract(cut) : original;
            return result.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** 主机：自己不算价，只负责「装」和「转发」。 */
    static final class Calculator {
        private final PriceRule rule;      // ① 插槽：字段类型写接口

        Calculator(PriceRule rule) {       // ② 装机：配件从外面传进来
            this.rule = rule;
        }

        BigDecimal price(BigDecimal original) {
            return rule.apply(original);   // ③ 通电：干活的是配件
        }

        String describe() {
            return rule.name();
        }
    }

    public static void main(String[] args) {
        BigDecimal original = new BigDecimal("100.00");
        List<PriceRule> parts = List.of(new PercentOff("0.10"), new FullReduction("100.00", "20.00"));

        for (PriceRule part : parts) {
            Calculator calculator = new Calculator(part);   // 同一台主机，插不同的配件
            BigDecimal finalPrice = calculator.price(original);
            System.out.println(calculator.describe() + " → " + finalPrice.toPlainString()
                    + "（省 " + original.subtract(finalPrice).toPlainString() + "）");
        }
    }
}
```

运行结果：

```text
打折 10% → 90.00（省 10.00）
满 100.00 减 20.00 → 80.00（省 20.00）
```

**本项目对照**：

- [`PriceCalculator`](../src/com/patterns/behavioral/strategy/PriceCalculator.java) 的字段就是那个插槽：`private final DiscountStrategy strategy`，构造器 `Objects.requireNonNull` 保证插槽里一定有配件，于是 `calculatePrice()` 里一行判空都不需要。
- [`PriceCalculator.withStrategy()`](../src/com/patterns/behavioral/strategy/PriceCalculator.java) 是「换配件」的正确姿势：**返回新主机，而不是把旧主机的显卡拔掉**。这样旧价格和旧策略都还在，界面上才能同时算「用券 A 多少钱、用券 B 多少钱」。
- 用继承的话，你会得到 `VipPriceCalculator extends AbstractPriceCalculator`，而「满减 × VIP 可叠加」会直接变成类爆炸。

**比喻的失效边界**：

> 主板的插槽是**物理存在的**，配件厂商照着 PCIe 标准造就行。代码里的插槽得**你自己造**——定义接口这件事没有硬件兜底，你不定义，配件就插不上。
> 而且现实里的显卡插上去能跑，代码里「跑不起来」不是蓝屏，是编译错误或 `ClassCastException`。

### 2.2 奶茶加料：组合的「可叠加」形态（对应装饰器）

**现实痛点**：菜单爆炸。三种小料要 7 种奶茶，四种小料要 15 种，**每加一种小料，菜单翻一倍**。

**元素映射**：

| 现实世界 | 代码世界 |
|---|---|
| 一杯基础奶茶 | 被装饰对象（`PlainText`） |
| 珍珠 / 椰果 / 布丁 | 装饰器类（`BoldDecorator` 等） |
| 「加珍珠再加椰果」 | 嵌套构造 `new 椰果(new 珍珠(奶茶))` |
| 「先珍珠后椰果」和「先椰果后珍珠」 | 构造顺序不同，结果不同 |
| 加 4 种小料要 15 个类 | 用继承时的 2ⁿ-1 爆炸 |

**本项目对照**：

- [`TextComponent`](../src/com/patterns/structural/decorator/TextComponent.java) 的注释直接算了这笔账：加粗 / 变色 / 斜体任意组合是 2³-1 = **7 个类**，再加下划线变成 2⁴-1 = **15 个类**。
- [`TextDecorator`](../src/com/patterns/structural/decorator/TextDecorator.java) 把「持有被装饰者 + 默认转发」抽成一处，4 个具体装饰器就够覆盖所有组合。
- [`Client.demo3RuntimeComposition()`](../src/com/patterns/structural/decorator/Client.java) 最能说明问题：`if (isVip)` 才套两层，`if (needHighlight)` 再套一层——**继承的结构在编译期就固定，装饰链在运行期才拼**。

**比喻的失效边界**：

> 奶茶的小料加进去就取不出来了；代码里的装饰链**也同样不可变**——`TextDecorator.component` 是 `protected final`，想「去掉一层」不能 `remove`，只能重新构造一条新链。
> 换句话说，装饰链是**不可变对象**，不是可变集合。这一点和「组合模式里 `Directory.add()` 能动态增删」正好相反，别混。

### 2.3 泡茶与泡咖啡：继承的正解（对应模板方法）

**现实痛点**：流程固定为「烧水 → 冲泡 → 倒杯 → 加调料」，只有「冲什么」「加什么」不同。**顺序错了就是事故**（先倒杯再烧水）。

**元素映射**：

| 现实世界 | 代码世界 |
|---|---|
| 门店 SOP 手册（步骤与顺序） | `public final` 模板方法 |
| 固定步骤（烧水、倒杯） | `private` 具体方法，子类看不见 |
| 必须自己做的步骤 | `protected abstract`，编译器强制实现 |
| 可选步骤（要不要加冰） | 钩子方法，有默认实现 |

**完整可运行示例**（已验证编译运行）：

```java
/**
 * 继承的正解：顺序固定的算法骨架（模板方法）。
 * 运行：java BeverageDemo.java
 */
public abstract class BeverageDemo {

    /** 模板方法：public final —— 骨架不可篡改。 */
    public final void prepare() {
        boilWater();
        brew();
        pourIntoCup();
        addCondiments();
    }

    private void boilWater() {
        System.out.println("  1. 烧水");
    }

    private void pourIntoCup() {
        System.out.println("  3. 倒进杯子");
    }

    /** 可变步骤：子类必须实现。 */
    protected abstract void brew();

    /** 可变步骤：子类必须实现。 */
    protected abstract void addCondiments();

    static final class Tea extends BeverageDemo {
        @Override
        protected void brew() {
            System.out.println("  2. 冲泡茶叶");
        }

        @Override
        protected void addCondiments() {
            System.out.println("  4. 加柠檬");
        }
    }

    static final class Coffee extends BeverageDemo {
        @Override
        protected void brew() {
            System.out.println("  2. 冲泡咖啡粉");
        }

        @Override
        protected void addCondiments() {
            System.out.println("  4. 加糖和奶");
        }
    }

    public static void main(String[] args) {
        System.out.println("泡茶：");
        new Tea().prepare();
        System.out.println("泡咖啡：");
        new Coffee().prepare();
    }
}
```

运行结果：

```text
泡茶：
  1. 烧水
  2. 冲泡茶叶
  3. 倒进杯子
  4. 加柠檬
泡咖啡：
  1. 烧水
  2. 冲泡咖啡粉
  3. 倒进杯子
  4. 加糖和奶
```

**本项目对照**：[`OrderProcessTemplate`](../src/com/patterns/behavioral/templatemethod/OrderProcessTemplate.java) 把「检查库存 → 支付 → 发货 → 通知」写进 `public final processOrder()`，把 `pay()` / `deliver()` 留成 `protected abstract`，把「要不要检查库存」做成钩子 `needsStockCheck()`。选择继承而非组合的理由很硬：**这个顺序是业务强制的，不存在「某个客户希望先发货再付款」的需求**。

**比喻的失效边界**：

> 模板方法要求「你只能是我儿子」。一旦订单类型还需要接入另一个框架的基类（比如某个支付 SDK 强制 `extends BasePayHandler`），立刻撞上**单继承墙**，此时只能退回「策略 + 组合」。
> 另外现实里的 SOP 可以口头微调，代码里的骨架**必须加 `final`** 才真的不能改；不加 `final`，子类一重写骨架，模式立刻失效。

### 2.4 目录与文件：组合的「递归」形态（对应组合模式）

**现实痛点**：文件夹里能放文件，也能放文件夹。如果容器只能装叶子、叶子不能装东西，就表达不了「无限层嵌套」。

**元素映射**：

| 现实世界 | 代码世界 |
|---|---|
| 文件 | 叶子节点 `FileLeaf` |
| 文件夹 | 容器节点 `Directory` |
| 文件夹里同时能放文件和文件夹 | 子节点集合声明为 `List<FileSystemNode>`（接口） |
| 算整个文件夹多大 | 递归 `child.size()` |

**本项目对照**：[`Directory`](../src/com/patterns/structural/composite/Directory.java) 持有 `List<FileSystemNode> children`，`size()` 只有一句「遍历孩子，累加 `child.size()`」——它不关心孩子是文件还是子目录，**递归深度由对象树自己决定，代码完全不用改**。

**比喻的失效边界**：

> 现实的文件系统由 OS 保证不会出现「A 在 B 里、B 又在 A 里」。代码里的树**必须自己防环**，否则 `size()` 无限递归直接 `StackOverflowError`——[`Directory.add()`](../src/com/patterns/structural/composite/Directory.java) 里那段 `contains()` 校验就是为此存在的，教学 Demo 里最容易被省略。

---

## 3. 组合在代码里的三种形态

同样是 `has-a`，字段形状不同，对应的模式也不同：

| 形态 | 字段长相 | 组装时机 | 本项目文件 | 对应模式 |
|---|---|---|---|---|
| **单个委托** | `private final Strategy s;` | 构造时定，可换新对象 | [`PriceCalculator`](../src/com/patterns/behavioral/strategy/PriceCalculator.java) | 策略 / 状态 / 桥接 |
| **同类嵌套** | `private final Component inner;` | 构造时定，可叠多层 | [`TextDecorator`](../src/com/patterns/structural/decorator/TextDecorator.java) | 装饰器 / 代理 / 责任链 |
| **多个同类** | `private final List<Node> children;` | 运行中可增删 | [`Directory`](../src/com/patterns/structural/composite/Directory.java) | 组合 / 观察者 |

**三者唯一的共同点：字段类型永远是接口（或抽象类型）**。这一条是组合能不能成立的技术前提——字段一旦写成具体类，插槽就焊死了，灵活性归零。

---

## 4. 继承的四个硬伤

### 4.1 脆弱基类：父类改实现，子类全崩

子类能读 `protected` 字段，等于**父类的内部结构成了公开接口**。父类把 `ArrayList` 换成 `LinkedList`、把 `protected` 改成 `private`，所有子类一起遭殃。这叫**脆弱基类问题（Fragile Base Class）**，本质是封装被破坏。

**项目证据**：[`DiscountStrategy`](../src/com/patterns/behavioral/strategy/DiscountStrategy.java) 的注释专门解释了「为什么两个校验工具方法放进接口的 `static`，而不是抽一个 `AbstractDiscountStrategy` 基类」——**它们之间只有两行无状态工具代码是共同的，没有任何共同状态**，为此引入一层继承，代价是子类被绑死在一条链上，而且 `record` 根本无法继承。

### 4.2 单继承是稀缺资源

Java 只允许一个 `extends`。子类一旦继承了 `OrderProcessTemplate`，就再也不能继承别的类——这是模板方法模式的固有代价，也是很多框架扩展点只能做成「接口 + 组合」的原因。

现代 Java 更明显：`record` 天生 `extends Record`，**不能再继承普通类**，所以「用 record 实现策略」的写法只有在策略是**接口**时才成立（见 [`SimpleDiscountStrategy`](../src/com/patterns/behavioral/strategy/SimpleDiscountStrategy.java)）。

### 4.3 类爆炸：组合数是指数的，继承只能穷举

- 装饰器：3 种能力 → 7 个类，4 种 → 15 个类，5 种 → 31 个类；
- 桥接：[`Message`](../src/com/patterns/structural/bridge/Message.java)（3 种）× [`MessageSender`](../src/com/patterns/structural/bridge/MessageSender.java)（4 种）= **12 种组合**，用组合只要 3+4 = **7 个类**；如果每个组合一个类，那就是 12 个，再加一个渠道就是 15 个。

**规律**：当变化有 2 个以上维度时，继承的成本是**乘积**，组合的成本是**加法**。

### 4.4 编译期绑定：运行期想换就得重建对象

继承得到的能力是**类的一部分**，`new` 出来是什么就永远是什么。组合的能力是**字段的一部分**，换一个实现了同一接口的对象就行。

[`Client.demo3RuntimeComposition()`](../src/com/patterns/structural/decorator/Client.java) 演示了这一点：同一个 `new PlainText(...)` 之后，按 `isVip` 决定套不套装饰器——这是「运行期装配」，继承连表达都表达不出来。

---

## 5. 那继承什么时候是对的

**只有在你要控制「骨架」而不是「零件」时才用继承**。三个正当场合：

| 场合 | 判别特征 | 本项目 / JDK 例子 |
|---|---|---|
| **算法骨架固定** | 步骤与顺序业务强制，子类只填其中几步 | [`OrderProcessTemplate`](../src/com/patterns/behavioral/templatemethod/OrderProcessTemplate.java) |
| **框架扩展点** | 框架要「回调」你的实现，并按固定时机调用 | `HttpServlet` 的 `doGet/doPost`、`AbstractList`、JUnit 的 `TestCase` |
| **类型层次真实且稳定** | 是一棵「分类树」，不是「能力组合」 | `IOException` 家族、几何图形 `Shape` |

三条判据一起看更准：

1. **变化在「顺序」还是在「零件」？** 顺序固定 → 继承；零件可换 → 组合。
2. **子类会不会被迫继承一堆用不到的方法？** 会 → 抽象层次切错了。
3. **父类的实现细节你控制得了吗？** 控制不了（第三方库）→ 一律用组合包一层，别继承。

**最容易踩的坑**：把「复用几行代码」当成用继承的理由。复用代码有更轻的手段——`static` 工具方法、普通委托、接口默认方法。继承是**最强耦合**，应该留给真正需要「骨架 + 回调」的场景。

---

## 6. 决策清单

写 `extends` 之前，按顺序问五句：

1. **是 is-a 吗？** 它能不能在**完全不改变调用方预期**的前提下替换父类？（正方形不能替换长方形，这是经典反例）
2. **我改的是骨架还是零件？** 骨架 → 抽象类；零件 → 接口 + 组合。
3. **变化维度有几个？** ≥2 个 → 必须组合（否则乘积爆炸）。
4. **父类是我能控制的代码吗？** 不是 → 组合包一层。
5. **我只是想复用几行代码吗？** 是 → `static` 方法 / 默认方法 / 普通委托。

如果五问都指向继承，那就大胆继承，并且记得：

- 骨架方法加 `final`；
- 步骤方法用 `protected` 而不是 `public`（否则外部能绕过流程直接调某一步）；
- 子类必须提供的东西才用 `abstract`，可选的用钩子（有默认实现）。

---

## 7. 五个常见误用

### 7.1 插槽焊死：字段写成具体类

```java
private final FullReductionStrategy strategy;   // ✗ 只能满减，换不了
```

改成接口类型才能换；需要多个插槽就开多个字段，而不是塞成一个 `List<Object>`。

### 7.2 为复用一行代码抽基类

两个策略都需要「校验价格非负」，正确做法是接口里的 `static` 工具方法（[`DiscountStrategy.requireValidPrice()`](../src/com/patterns/behavioral/strategy/DiscountStrategy.java)），而不是 `AbstractDiscountStrategy`。

### 7.3 用继承表达多个维度

「消息类型 × 发送渠道」用继承会得到 12 个类，用[桥接](../src/com/patterns/structural/bridge/Message.java)只要 7 个。**维度 ≥2 就是组合的信号**。

### 7.4 组合层数无节制

装饰链套 5 层以上，`toString()`、日志、断点都会变得难读。至少保证：

- 每个装饰器能在日志里报出自己的名字（[`DiscountStrategy.name()`](../src/com/patterns/behavioral/strategy/DiscountStrategy.java) 就是为此存在的）；
- 链的构造集中在一处（工厂），不要让 `new` 散落在业务代码里。

### 7.5 把「组合」理解成「自己重写」

组合不是「什么都自己实现」，而是「**零件用现成的，装配权在自己手里**」。误解成前者，就会退化成重复造轮子——这恰恰是组合要消灭的。

---

## 8. 自测

| # | 问题 | 参考答案要点 |
|---|---|---|
| 1 | 一句话说清组合与继承的区别 | 继承是 is-a + 编译期白盒复用；组合是 has-a + 运行期黑盒委托 |
| 2 | UML 里的「组合」和设计原则里的「组合」是一回事吗 | 不是。前者是强拥有（整体销毁部分销毁），后者是对象组合 + 委托的统称，包含聚合 |
| 3 | 为什么 `TextDecorator` 既 `implements TextComponent` 又持有一个 `TextComponent` | 实现同一接口才能被再次装饰（无限套娃）；持有同类型字段才能委托给内层 |
| 4 | 模板方法为什么必须 `final` | 骨架是模式全部价值所在，子类一改顺序就退化成各自为政的方法 |
| 5 | 装饰器和组合模式都持有同类对象，区别在哪 | 装饰器恰好持有 1 个（链），组合模式持有多个（树），且容器要递归聚合 |
| 6 | 两个变化维度时为什么必须用组合 | 继承成本是维度乘积（类爆炸），组合是维度之和 |
| 7 | 「组合优于继承」是不是意味着永远不用继承 | 不是。骨架固定、框架回调、类型层次真实时继承更合适 |
| 8 | 组合的最大代价是什么 | 对象数量变多、转发样板代码、调用链变深，排错要顺链找 |

---

## 9. 相关文档

- [01-learning-path.md](01-learning-path.md) —— 三个前置概念（本文对应其中的「组合优于继承」）
- [02-pattern-comparison.md](02-pattern-comparison.md) —— 模式之间的对比，其中 §2.2 是「装饰器 vs 继承」的专项对比
- [06-modern-scenarios.md](06-modern-scenarios.md) —— `record` / `sealed` / 虚拟线程出现后，哪些模式被语言吃掉了
- 组合的最佳实物对照：[`PriceCalculator`](../src/com/patterns/behavioral/strategy/PriceCalculator.java)、[`TextDecorator`](../src/com/patterns/structural/decorator/TextDecorator.java)、[`Directory`](../src/com/patterns/structural/composite/Directory.java)
- 继承的最佳实物对照：[`OrderProcessTemplate`](../src/com/patterns/behavioral/templatemethod/OrderProcessTemplate.java)
