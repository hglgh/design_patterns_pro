# 解释器模式（Interpreter Pattern）

> **一句话**：把一条「规则文本」解析成对象树，再让树的每个节点自己解释自己。
> 文法里每一个产生式对应一个类，树的形状就是求值顺序 ——
> 客户端只写 `parse("age > 18 && score < 500")`，不需要知道树是怎么搭的。

| 项目 | 数值 |
|---|---|
| 原项目规模 | 12 个源文件 / 257 行（规则引擎 7 文件 145 行 + AST Demo 5 文件 112 行） |
| 原项目运行 | 两个入口均 exit=0，GBK 编码各 1 行 |
| 缺陷取证 | 探针 `InterpProbe` 10 条证据（I1a–I10），67 行，SHA-256 `EBB28012…07209B` |
| 重构后规模 | 16 个源文件 / 1987 物理行 / 23 个 class / 69870 字节 |
| 重构后运行 | 10 段 Demo，134 行输出 / 6501 字节，三次运行哈希一致 `FD30D6AE…3F5599` |
| 编译 | `javac -Xlint:all` 零警告（JDK 25） |

---

## 目录

- [1. 现实案例：一条规则文本如何变成可执行逻辑](#1-现实案例一条规则文本如何变成可执行逻辑)
- [2. 原始实现：12 个文件 257 行与十个问题](#2-原始实现12-个文件-257-行与十个问题)
- [3. 设计动机：解释器模式到底解决了什么](#3-设计动机解释器模式到底解决了什么)
- [4. 设计推导：从-split-推到-16-个文件](#4-设计推导从-split-推到-16-个文件)
- [5. 代码解析：每个文件为什么存在](#5-代码解析每个文件为什么存在)
- [6. 执行流程：十段 Demo 的真实运行结果](#6-执行流程十段-demo-的真实运行结果)
- [7. 扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
- [8. 优缺点与常见误区](#8-优缺点与常见误区)
- [9. 面试与自测](#9-面试与自测)
- [附录 A：文件清单](#附录-a文件清单)
- [附录 B：运行方式与验证记录](#附录-b运行方式与验证记录)
- [附录 C：相关模式交叉索引](#附录-c相关模式交叉索引)
- [附录 D：原项目十个问题对照表](#附录-d原项目十个问题对照表)
- [附录 E：概念速查](#附录-e概念速查)
- [附录 F：版本与工具链](#附录-f版本与工具链)

---

## 1. 现实案例：一条规则文本如何变成可执行逻辑

### 1.1 说人话版：运营改一条门槛，为什么非要开发发版

一家电商公司做优惠券，领券门槛写在 Java 代码里：

```java
if (order.getAmount() > 199 && user.getLevel() >= 3) {
    coupon.setUsable(true);
}
```

上线两周，运营提了七个需求：

1. 门槛从 199 改成 299；
2. 加一个条件「新用户也可领」；
3. 从「且」改成「或」；
4. 会员等级改成消费金额；
5. 改成「金额 > 299 且（等级 >= 3 或 新用户）」；
6. ……

每一条都意味着：改一行代码 → 提交 → 等测试 → 等发版 → 灰度。第七次时，运营直接在群里问：
**「能不能我自己在后台配？」**

技术上这叫「业务规则外置」。要让运营在后台填一段文本就能生效，
系统必须自己看懂这段文本：

```text
amount > 299 && (level >= 3 || isNewUser == 1)
```

这个过程拆成三步，一步都不能省：

| 步骤 | 专业名称 | 干的活 | 不做会怎样 |
|---|---|---|---|
| 1 | 词法分析（Lexing） | 把字符流切成记号：`amount`、`>`、`299`、`&&`、`(` …… | 只能靠 `contains(">")` 猜，`>=` 会被误判成 `>` |
| 2 | 语法分析（Parsing） | 按优先级把记号拼成树：`&&` 的左边是金额判断，右边是括号组 | 优先级全靠手工 `new` 的顺序，写错就静默算错 |
| 3 | 解释执行 | 每个节点拿上下文算自己的值，父节点合并子节点的结果 | 只能写死一条规则 |

**解释器模式负责的正是第 3 步的组织方式，并倒逼 1、2 两步必须存在。**

它给出的规则很朴素：

> 文法里有几种「东西」，就写几个类；每个类都有一个 `interpret(ctx)`；
> 组合节点在 `interpret` 里调用子节点的 `interpret`。

于是「金额判断」「等级判断」「且」「或」「括号」各是一个类，
它们拼成的树就是这段规则的**可执行形态**。同一棵树换一份数据就能反复求值：

```text
金额=350, 等级=2, 新用户=否  -> false
金额=350, 等级=5, 新用户=否  -> true
金额=120, 等级=1, 新用户=是  -> true
```

### 1.2 三个必须回答的问题

| 问题 | 直觉答案 | 真实答案 |
|---|---|---|
| 为什么不直接 `eval` 或者上脚本引擎？ | 一行代码搞定 | 内嵌 JS/Groovy 会引入一整套运行时与安全边界（沙箱、超时、内存），而且业务方写错一个字母，错误信息是宿主语言的报错。文法固定、规模可控时，自己实现 16 个类反而更小更可控 |
| 为什么不写一个大的 `switch` 遍历 token？ | 也挺集中 | 能跑，但求值顺序、优先级、短路会全挤进一个方法，加一种运算符就要改这个 `switch` 的每个分支。**解释器模式把「每种语法的行为」分到各自的类里**，加运算符是加一个枚举值，加速度是加一个类 |
| 为了一条规则就写十几个类，值得吗？ | 类是不是太多了 | 只有一条写死的规则时不值得 —— 直接 `if` 最省。但规则要**由文本驱动、要能被运营改、要有优先级和错误定位**，这十几个类就是这套能力的最小代价 |

### 1.3 一个真实事故：第三个条件把第二个条件吃了

原项目 `RuleParser` 的核心只有这么几行：

```java
Expression left = null;
Expression right = null;
for (String token : tokens) {
    if (token.contains(">")) {
        left = new GreaterThanExpression(field, value);   // 大于只会写进 left
    } else if (token.contains("<")) {
        right = new LessThanExpression(field, value);     // 小于只会写进 right
    }
}
return new AndExpression(left, right);
```

**只有两个格子。** 一条规则里出现第三个条件时，必然有一个格子被覆盖。

探针 `I1a` 把这件事钉死成了证据。规则是：

```text
age > 18 && score < 500 && age < 30
```

数据是 `age=25, score=1000`。手工推算：

```text
25 > 18     -> true
1000 < 500  -> false          <- 这一条不满足
25 < 30     -> true
true && false && true -> false
```

但原项目实际输出是：

```text
表达式结果: true
```

原因：循环处理到第三条 `age < 30` 时，`right` 被重新赋值，
第二条 `score < 500` 的表达式对象直接丢失 —— 树里根本没有它。
最终参与求值的只有 `25 > 18 && 25 < 30`，当然成立。

这不是"示例写错了"，而是**解析器的数据结构装不下文法**。
一条规则有几个条件，得看它有几个槽位；而槽位是硬编码的两个。

更危险的版本是探针 `I1b`：规则 `age > 18 && score < 500 && weight > 60`，
数据 `age=10, score=100`。实际结果 `false` —— **看起来完全正确**，
因为 `age=10` 确实不满足。但真正让它返回 false 的是
「`weight` 这个字段不存在、恒假」，而不是 `age` 在起作用。
两个 bug 互相抵消，测试全绿，直到某个用例把被丢掉的条件变成关键。

> 这就是解释器模式最想消灭的一类失败：**不报错，只给错答案。**
> 一条规则只要还能算出 true/false，调用方就永远不知道它其实少了一半。

### 1.4 本项目的示例：一个规则引擎，一个算术求值器

重构后的示例保留了原项目的两条线，并把它们统一成一套语法体系：

```text
interpreter/
├── Client.java                     10 段 Demo，逐条对照探针 I1-I10
├── Expression.java                 抽象表达式：evaluate + describe
├── Value.java                      求值结果：数字或布尔（密封接口 + 两个 record）
├── Context.java                    不可变变量表
│
├── TokenType.java                  18 种词法单元类型
├── Token.java                      类型 + 原文 + 源码下标
├── Lexer.java                      字符流 -> 记号流（最长匹配）
├── Parser.java                     记号流 -> 表达式树（递归下降 + 九层优先级）
│
├── NumberExpression.java           终结符：数字
├── VariableExpression.java         终结符：变量（带源码位置）
├── ArithmeticExpression.java       非终结符：+ - * /
├── UnaryExpression.java            非终结符：- ! （一元）
├── ComparisonExpression.java       非终结符：> >= < <= == !=
├── LogicalExpression.java          非终结符：&& || （短路）
│
├── ExpressionException.java        统一异常 + caret 定位 + 窗口截断
└── ExpressionEngine.java           门面：parse / evaluate / test / describe
```

两条线合在一套语法上：

- **规则线**回答「一条文本规则怎么安全地求值」——
  对应原项目的 `InterpreterPattern` 七个文件；
- **算术线**回答「一棵语法树怎么支持优先级、结合性和多种运算符」——
  对应原项目的 `AST_Demo` 五个文件。

原项目把这两件事写成两套互不相通的类型（`Expression` 返回 boolean、
`Node.evaluate` 返回 int），本项目让它们共用 `Expression` + `Value`：

```text
(age + 5) * 2 > 50 && age < 30
 └── 算术子树 ──┘ └比较┘ └── 逻辑 ──┘
```

这条规则里算术、比较、逻辑三种节点同时存在，靠的就是
`evaluate` 统一返回 `Value`，每个父节点在需要时声明自己要数字还是要布尔。

### 1.5 双轨速览：同一件事的两种说法

| 说人话 | 专业术语 | 在本项目里是谁 |
|---|---|---|
| 能被求值的东西 | AbstractExpression（抽象表达式） | `Expression` |
| 具体是哪种求值 | Terminal / Nonterminal Expression | 六个 `*Expression` record |
| 递归的终点 | TerminalExpression（终结符表达式） | `NumberExpression`、`VariableExpression` |
| 会调用子节点的节点 | NonterminalExpression（非终结符表达式） | `Arithmetic/Unary/Comparison/Logical` |
| 求值时的数据表 | Context（上下文） | `Context` |
| 求值结果 | Value | `Value.Num` / `Value.Bool` |
| 一段文字变成树 | Parse（语法分析） | `Lexer` + `Parser` |
| 客户端的唯一入口 | Facade（门面） | `ExpressionEngine` |
| 报错带箭头 | Diagnostic（诊断信息） | `ExpressionException.render()` |

---

## 2. 原始实现：12 个文件 257 行与十个问题

### 2.1 十二个文件，两套互不相通的语法体系

原项目的解释器示例放在两个目录里：

| 目录 | 文件数 | 行数 | 内容 |
|---|---:|---:|---|
| `BehaviorType/InterpreterPattern/` | 7 | 145 | 规则引擎：`Expression` 返回 boolean，字段硬编码 age/score |
| `BehaviorType/InterpreterPattern/AST_Demo/` | 5 | 112 | 算术求值：`Node` 抽象类返回 int，只有 `+` |
| **合计** | **12** | **257** | 两套语法、两套节点、两套解析器 |

两个目录只差一层文件夹，却没有任何类型复用：

```text
规则引擎：interface Expression { boolean interpret(Context) }
算术 Demo：abstract class Node { int evaluate() }
```

一个是接口、一个是抽象类；一个吃 `Context`、一个不要参数；
一个返回布尔、一个返回整数。**同一门课的两个作业，写在同一个包的两层目录里。**

这不是"示例要独立"能解释的：两套都叫「解释器模式」，
而解释器模式的骨架恰恰是「统一的抽象表达式 + 递归子节点」。
两套各自成立，合起来看却正好证明了——
**抽象表达式的返回类型一旦选窄，就只能表达一类文法。**

### 2.2 完整源码（省略了每份文件顶部的 `@ClassName/@Author` 注释块）

**规则引擎：抽象表达式与上下文**

```java
// Expression.java
public interface Expression {
    boolean interpret(Context context);  // 解释表达式，返回布尔值
}

// Context.java
public record Context(int age, int score) {
}
```

**规则引擎：两个终结符表达式**

```java
// GreaterThanExpression.java
public record GreaterThanExpression(String field, int value) implements Expression {

    @Override
    public boolean interpret(Context context) {
        if ("age".equals(field)) {
            return context.age() > value;
        } else if ("score".equals(field)) {
            return context.score() > value;
        }
        return false;
    }
}

// LessThanExpression.java
public record LessThanExpression(String field, int value) implements Expression {

    @Override
    public boolean interpret(Context context) {
        if ("age".equals(field)) {
            return context.age() < value;
        } else if ("score".equals(field)) {
            return context.score() < value;
        }
        return false;
    }
}
```

**规则引擎：唯一的组合节点**

```java
// AndExpression.java
public record AndExpression(Expression expr1, Expression expr2) implements Expression {

    @Override
    public boolean interpret(Context context) {
        return expr1.interpret(context) && expr2.interpret(context);
    }
}
```

**规则引擎：解析器与客户端**

```java
// RuleParser.java
public class RuleParser {
    public static Expression parse(String rule) {
        // 按“&&”分割表达式
        String[] tokens = rule.split("&&");
        Expression left = null;
        Expression right = null;

        for (String token : tokens) {
            token = token.trim();
            if (token.contains(">")) {
                String[] parts = token.split(">");
                String field = parts[0].trim();
                int value = Integer.parseInt(parts[1].trim());
                left = new GreaterThanExpression(field, value);
            } else if (token.contains("<")) {
                String[] parts = token.split("<");
                String field = parts[0].trim();
                int value = Integer.parseInt(parts[1].trim());
                right = new LessThanExpression(field, value);
            }
        }
        // 合并左右表达式
        return new AndExpression(left, right);
    }
}

// Client.java
public class Client {
    public static void main(String[] args) {
        // 用户输入的动态规则
        String rule = "age > 18 && score < 500";

        // 创建上下文对象
        // 假设 age = 20, score = 450
        Context context = new Context(20, 450);

        // 解析规则表达式
        Expression expression = RuleParser.parse(rule);

        // 判断规则是否成立
        boolean result = expression.interpret(context);
        // 输出结果
        System.out.println("表达式结果: " + result);
    }
}
```

**算术 Demo：抽象节点与两个具体节点**

```java
// AST_Demo/Node.java
public abstract class Node {
    public abstract int evaluate();
}

// AST_Demo/NumberNode.java
public class NumberNode extends Node {
    private final int value;

    public NumberNode(int value) {
        this.value = value;
    }

    @Override
    public int evaluate() {
        return value;
    }
}

// AST_Demo/AddNode.java
public class AddNode extends Node {
    private final Node left;
    private final Node right;

    public AddNode(Node left, Node right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public int evaluate() {
        return left.evaluate() + right.evaluate();
    }
}
```

**算术 Demo：解析器与客户端**

```java
// AST_Demo/SimpleParser.java
public class SimpleParser {
    private final String input;
    private int pos = 0;

    public SimpleParser(String input) {
        // 去除空格
        this.input = input.replaceAll(" ", "");
    }

    public Node parse() {
        return parseAddition();
    }

    // 解析加法表达式
    private Node parseAddition() {
        Node left = parseNumber();
        if (pos < input.length() && input.charAt(pos) == '+') {
            pos++; // 跳过'+'
            Node right = parseAddition();
            return new AddNode(left, right);
        }
        return left;
    }

    // 解析数字
    private Node parseNumber() {
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        int value = Integer.parseInt(input.substring(start, pos));
        return new NumberNode(value);
    }
}

// AST_Demo/SimpleASTMain.java
public class SimpleASTMain {
    public static void main(String[] args) {
        // 解析 "10+5+2"
        SimpleParser parser = new SimpleParser("10+5+2");
        Node ast = parser.parse();

        // 输出: 17
        System.out.println("计算结果: " + ast.evaluate());

        // 对应的AST结构:
        //      AddNode
        //     /       \
        // Number(10)  AddNode
        //            /       \
        //       Number(5)   Number(2)
    }
}
```

### 2.3 真实输出：两段，逐字

编译 12 个文件时 `javac -Xlint:all` 零警告，两个入口都正常退出：

```text
$ java BehaviorType.InterpreterPattern.Client
表达式结果: true
```

```text
$ java BehaviorType.InterpreterPattern.AST_Demo.SimpleASTMain
计算结果: 17
```

第一条 1 行 / 18 字节，SHA-256 `A0576E059CCDC981F7161B9A5D579B7400E95A0004910968E0C59D5B52ECF109`；
第二条 1 行 / 14 字节，SHA-256 `92C1ABF8A01AB50D5C81754297545D678FDEADCE754012C3562AE578A111F777`。
**注意**：两段都是 GBK（代码页 936）编码的字节，不是 UTF-8 —— 原项目没有指定编码，
Windows 中文环境默认用 GBK。

这两行输出全部"正确"，但它们只覆盖了两条最短的规则。
下面十个问题，全部发生在这两行之外。

### 2.4 问题一：`RuleParser` 只有两个槽位，第三条条件覆盖第二条（探针 I1）

```java
Expression left = null;    // 大于只会写这里
Expression right = null;   // 小于只会写这里
```

一条规则里的条件数量是**由用户决定的**，而这两个变量的数量是**由作者决定的**。
用户写了三个条件，第三个必然覆盖掉前面同侧的表达式。

探针实测：

```text
I1a 规则: age > 18 && score < 500 && age < 30
    上下文: age=25, score=1000
    逐步手算: 25>18 成立, 1000<500 不成立 -> 应为 false
    实际结果: true  <-- 错判为 true
    原因: 第三条 age<30 把第二条 score<500 覆盖（两者都含'<'），
          score 条件被丢了；25>18 && 25<30 当然成立。
```

附带一个更隐蔽的变体：

```text
I1b 规则: age > 18 && score < 500 && weight > 60
    上下文: age=10, score=100
    实际结果: false
    误以为对: age=10 确实不满足 age>18，期望也是 false。
             但真实原因是 weight 字段不存在恒为假，而不是 age 在起作用。
```

**问题的本质不是"循环少了一个变量"，而是把「任意数量的条件」硬塞进了固定两个槽位。**
真要修，得让解析器的数据结构跟文法同构 —— 文法里 `&&` 是二元的、可任意嵌套的，
那么解析结果就必须是一棵**可以一直往左上生长的树**，而不是两个字段。

### 2.5 问题二：单条件规则必 NPE（探针 I2）

```text
"age > 18"    -> NullPointerException @ BehaviorType.InterpreterPattern.AndExpression.interpret:14
"score < 500" -> NullPointerException @ BehaviorType.InterpreterPattern.AndExpression.interpret:14
```

因为 `parse` 无条件返回 `new AndExpression(left, right)`：
只写一个条件时，另一侧永远是 `null`，`interpret` 一解引用就炸。

注意堆栈指向 `AndExpression` —— **离真正出错的地方（`RuleParser` 少填了一个槽位）隔了一层**。
调用方看到的是"与运算里有个空指针"，而不是"你这条规则少写了一半"。

单条件规则是最常见的写法：阈值判断、黑白名单、开关条件，全都只有一个比较。
**这个 bug 意味着原项目连最简单的规则都跑不通。**

### 2.6 问题三：`>=` 被 `contains(">")` 误判为 `>`（探针 I3）

```text
"age >= 18 && score < 500" -> NumberFormatException
信息: For input string: "= 18"
```

拆解一下 `token.contains(">")` + `token.split(">")` 对 `age >= 18` 做了什么：

```text
contains(">")  -> true                     （命中大于分支）
split(">")     -> ["age ", "= 18"]         （等号被留在了后半段）
parseInt("= 18") -> NumberFormatException
```

用户写的是 `>=`，报的是「`= 18` 不能转数字」。两者之间没有任何可读的联系。

**根因是「字符」和「单词」被混为一谈**：解析器在字符层面找 `>`，
而在语法层面 `>` 和 `>=` 是两个不同的运算符。
只要没有词法层把 `>=` 先切成一个整体，语法层就永远会看到"半个运算符"。

### 2.7 问题四：阈值不是数字时，异常不含规则也不含位置（探针 I4）

```text
"age > eighteen && score < 500" -> NumberFormatException: For input string: "eighteen"
```

`Integer.parseInt` 的异常消息只描述"哪个字符串不能转数字"，
不包含：规则原文、出错位置、期望什么。

规则一多（比如配置中心里有 300 条），运维拿着这句
`For input string: "eighteen"` 完全无法定位是哪个服务的哪条规则的第几个字符。

### 2.8 问题五：未知字段静默返回 `false`（探针 I5）

```text
"weight > 60 && score < 500" -> false
"score < 500 && weight > 60" -> false
"age > 18 && score < 500"    -> true
```

前两条只有字段名不同（`weight` 不存在，`score` 存在），但都不报错。
`GreaterThanExpression.interpret` 的最后一行是：

```java
return false;
```

**这是整个原项目里最坏的一行代码。** 它让三种完全不同的情况返回同一个值：

| 情况 | 应该的行为 | 实际返回 |
|---|---|---|
| 字段拼错了 | 报错"未知字段" | `false` |
| 字段合法但判断不成立 | `false` | `false` |
| 字段合法但判断成立 | `true` | `true`（只有这一种是对的） |

结果是：**拼错的规则永远不会通过，而日志里什么都没有。**
用户只会觉得「规则没生效」，然后找开发，开发再看一遍配置，再看一遍代码，
最后发现是 `scroe` 少写了一个字母。

### 2.9 问题六：字段名大小写敏感且无提示（探针 I6）

```text
age > 18 (实际 age=20) -> true
AGE > 18 (实际 age=20) -> false
Age > 18 (实际 age=20) -> false
aGe > 18 (实际 age=20) -> false
```

`"age".equals(field)` 是区分大小写的。而配置中心把 KEY 统一转成大写，
是**极其常见**的做法（几乎所有 properties 系统都这么干）。
这条规则上线之后，会安静地把所有人挡在门外，排查方向永远指向"数据不对"。

**I5 和 I6 是同一个根因**：字段名到取值的映射被写死成两个 `if`，
所以"不认识的名字"只能落到 `return false`；
而正确的设计应该是让上下文自己承载"名字 → 值"的映射，认不出就抛异常。

### 2.10 问题七：AST 解析器不检查剩余输入（探针 I7）

```text
"10+5+2" -> 17
"10*5"   -> 10
"10abc"  -> 10
"10 中文" -> 10
```

`parseAddition` 只认 `+`，遇到别的字符就直接返回左边那半棵树，
**从 `parse()` 到 `main` 没有人回头看 `pos` 是否走到了结尾**。

于是 `10*5` 安静地算出 `10`：

```java
Node left = parseNumber();                        // 读到 10，pos 停在 '*'
if (pos < input.length() && input.charAt(pos) == '+') { ... }  // 不是 '+'
return left;                                       // 直接把 10 交出去
```

这是解释器**最危险的失败方式**：不报错，只给错答案。
`10*5` 的正确答案是 50 或"不支持 `*`"，绝不该是 10。

### 2.11 问题八：空输入报 `parseInt("")`（探针 I8）

```text
""      -> NumberFormatException: For input string: ""
"abc"   -> NumberFormatException: For input string: ""
"10+"   -> NumberFormatException: For input string: ""
"-5+3"  -> NumberFormatException: For input string: ""
"1++2"  -> NumberFormatException: For input string: ""
```

五种完全不同的错误，报的是同一句话。
`parseNumber` 在 `pos` 没有前进时直接
`Integer.parseInt(input.substring(start, pos))`，`substring` 得到空串。

对用户来说，这五种情况的修法完全不同：

| 输入 | 用户该怎么改 |
|---|---|
| `""` | 至少写一个操作数 |
| `"abc"` | 不要用字母开头 |
| `"10+"` | `+` 后面补一个操作数 |
| `"-5+3"` | 不支持前导负号，得写成 `0-5+3` |
| `"1++2"` | `+` 重复了 |

**同一句报错无法区分五种修法**，这就是"异常信息不带位置"的实际代价。

### 2.12 问题九：右递归下降，递归深度等于项数（探针 I9）

```java
private Node parseAddition() {
    Node left = parseNumber();
    if (pos < input.length() && input.charAt(pos) == '+') {
        pos++;
        Node right = parseAddition();   // <- 递归调用自己
        return new AddNode(left, right);
    }
    return left;
}
```

这是**右递归**，每个 `+` 压一层栈。探针实测：

```text
1000 项相加  -> 1000（求值正确，但解析时已经压了 1000 层栈）
10000 项相加 -> StackOverflowError
100000 项相加 -> StackOverflowError
```

两个后果：

1. **栈深度与输入长度线性相关** —— 输入多长，栈就多深，用户能控制的输入变成了能控制栈深度的东西；
2. **结合性错了** —— `parseAddition` 先递归右边，得到的是右结合树：
   `10-3-2` 若支持减法，会算成 `10-(3-2)=9` 而不是 `(10-3)-2=5`。
   当前只支持 `+` 看不出来（加法满足结合律），一加减法就暴露。

正确写法是**左递归用循环实现**：

```java
Node left = parseMultiplicative();
while (遇到 '+' 或 '-') {
    left = new BinaryNode(operator, left, parseMultiplicative());  // 往左上生长
}
return left;
```

循环不压栈，同层任意多项只占一层栈，且天然左结合。

### 2.13 问题十：两套语法体系互不相通（探针 I10）

```text
包内节点类型: Node(abstract) / NumberNode / AddNode
"10-5"       -> 10
"10/2"       -> 10
"(10+5)*2"   -> NumberFormatException
```

算术 Demo 只认识 `+` 和数字，不支持 `-`、`/`、括号。
这本身作为教学示例可以接受。真正的问题是：

- 规则引擎用的是 `Expression` 接口 + `Context`，返回 boolean；
- 算术 Demo 用的是 `Node` 抽象类 + 无参数，返回 int；
- **两边的解析器、错误处理、扩展方式完全不通用**。

想给规则引擎加一个 `(age + 5) * 2 > 50`，两套都不能用：
规则引擎没有算术，算术 Demo 没有变量和比较。
结果就是「解释器模式」在这个项目里被教了两遍，却没有一遍是完整的。

### 2.14 探针完整输出（67 行 / 4375 字节）

```text
[I1] 三个 && 条件时必有一个条件被覆盖丢失
     I1a 规则: age > 18 && score < 500 && age < 30
        上下文: age=25, score=1000
        逐步手算: 25>18 成立, 1000<500 不成立 -> 应为 false
        实际结果: true  <-- 错判为 true
        原因: 第三条 age<30 把第二条 score<500 覆盖（两者都含'<'），score 条件被丢了；25>18 && 25<30 当然成立。
     I1b 规则: age > 18 && score < 500 && weight > 60
        上下文: age=10, score=100
        实际结果: false
        误以为对: age=10 确实不满足 age>18，期望也是 false。但真实原因是 weight 字段不存在恒为假，而不是 age 在起作用。
     影响: 两个 bug（丢条件 + 未知字段恒假）碰巧给出正确答案，这是最危险的一类缺陷：测试全绿，直到某个 case 把被丢的条件变关键。

[I2] 只写一个条件 -> AndExpression 的另一半是 null -> NPE
     "age > 18" -> NullPointerException @ BehaviorType.InterpreterPattern.AndExpression.interpret:14
     "score < 500" -> NullPointerException @ BehaviorType.InterpreterPattern.AndExpression.interpret:14
     影响: 单条件规则是最常见的写法，而它的正确性取决于写的是 > 还是 < —— 写 > 必崩，写 < 也必崩，因为缺的那一半永远是 null。

[I3] >= 被 contains(">") 误判为 >，解析出 "= 18" -> NumberFormatException
     "age >= 18 && score < 500" -> NumberFormatException
     信息: For input string: "= 18"
     影响: 用户按常识写 >= ，得到的是一个和业务毫无关系的数字格式异常。

[I4] 阈值不是数字 -> NumberFormatException，且异常不含规则原文
     "age > eighteen && score < 500" -> NumberFormatException: For input string: "eighteen"
     影响: 异常消息是 "For input string: \"eighteen\""，没有规则原文、没有位置，规则多了之后无法定位。

[I5] 未知字段静默返回 false -> 拼错的规则永远不会通过
     "weight > 60 && score < 500" -> false
     "score < 500 && weight > 60" -> false
     "age > 18 && score < 500" -> true
     对照: 前两条只有字段名不同（weight 不存在，score 存在），但都没有任何报错；第三条字段全合法，结果为 true。
     影响: 字段名拼错不会报错，只会让规则变成恒 false；用户只会觉得「规则没生效」。

[I6] 字段名大小写敏感 -> AGE / Age 全部静默判假
     age > 18 (实际 age=20) -> true
     AGE > 18 (实际 age=20) -> false
     Age > 18 (实际 age=20) -> false
     aGe > 18 (实际 age=20) -> false
     影响: 配置中心把 KEY 统一转成大写是常见做法，这条规则上线后会安静地把所有人挡在门外。

[I7] AST 解析器不检查剩余输入 -> "10*5" 得到 10
     "10+5+2" -> 17
     "10*5" -> 10
     "10abc" -> 10
     "10 中文" -> 10
     影响: "10*5" 应该是 50 或直接报错，实际安静地返回 10 —— 这是解释器最危险的失败方式：不报错，只给错答案。

[I8] 空输入 / 非数字起始 -> parseInt("") -> NumberFormatException
     "" -> NumberFormatException: For input string: ""
     "abc" -> NumberFormatException: For input string: ""
     "10+" -> NumberFormatException: For input string: ""
     "-5+3" -> NumberFormatException: For input string: ""
     "1++2" -> NumberFormatException: For input string: ""
     影响: 报错信息是 "For input string: \"\""，既没说期望什么，也没说错在第几个字符。

[I9] 右递归下降 -> 递归深度 = 项数，长表达式直接触顶栈深度
     1000 项相加 -> 1000 (求值正确，但解析时已经压了 1000 层栈)
     10000 项相加 -> StackOverflowError
     100000 项相加 -> StackOverflowError
     对比: 左递归改造后（累加式循环）深度可以降到 O(1)，与项数无关；当前实现把输入长度和栈深度绑死了。

[I10] AST 语法体系只有 NumberNode + AddNode，运算符覆盖极窄
     包内节点类型: Node(abstract) / NumberNode / AddNode
     "10-5" -> 10
     "10/2" -> 10
     "(10+5)*2" -> NumberFormatException
     影响: 作为教学 Demo 可以接受，但它与主规则解释器是两套完全独立的 Expression/Node 体系，两边的解析器、错误处理、扩展方式都不通用。
```

### 2.15 十个问题里，只有三个跟"上下文设计"有关

必须说清楚，否则读者会以为"用了解释器就会写出这些 bug"：

| 编号 | 问题 | 属于 | 说明 |
|---|---|---|---|
| I5 | 未知字段静默 `false` | **上下文设计** | 字段名到值的映射写死在节点里 |
| I6 | 大小写敏感无提示 | **上下文设计** | 同上，同一个根因的第二个表现 |
| I10 | 两套语法互不相通 | **抽象层次** | 抽象表达式的返回类型选窄了 |
| I1 | 第三条条件覆盖第二条 | 解析实现 | 用两个变量装任意数量的条件 |
| I2 | 单条件 NPE | 解析实现 | 无条件构造二元节点，不校验 |
| I3 | `>=` 被误判 | 词法缺失 | 没有词法层，字符与单词混为一谈 |
| I4 | 异常不含规则与位置 | 诊断缺失 | 直接抛 JDK 异常 |
| I7 | 尾部垃圾被丢弃 | 解析实现 | 不检查是否消费完 |
| I8 | 五种错误同一句报错 | 诊断缺失 | 同上 |
| I9 | 右递归爆栈 | 算法选择 | 递归代替循环 |

换句话说：

> **解释器模式本身只贡献了两条约束** ——
> ① 抽象表达式的返回类型决定了它能表达多大的文法（I10）；
> ② 上下文长什么样，决定了"名字找不到"这件事能不能被表达成错误（I5/I6）。
> 其余七个问题，用不用解释器都应该修。

第四条值得单独强调：**I2 和 I7 是"该报错时没报错"，I5 和 I6 是"该报错时返回了 false"。**
四种失败方式指向同一个工程习惯 ——
**错误必须在最早能发现它的地方抛出，而不是悄悄变成某个默认值。**

### 2.16 一个更根本的问题：示例只证明了"能跑"

两个 `Client` 各自只有三五行有效代码：

```java
// 规则引擎
Client -> RuleParser.parse(rule) -> expression.interpret(context) -> println

// 算术 Demo
new SimpleParser("10+5+2").parse().evaluate() -> println
```

它们能证明"抛出一个布尔"和"加出 17"，但回答不了这些：

- 同一棵树换个上下文能不能复用？（原项目 `Context` 是 record，创建一次就丢）
- 优先级对不对？`10+5*2` 应该是 20，结果是 20 究竟因为乘法先算，还是碰巧？
- 一条规则里既有算术又有比较，怎么表达？`(age + 5) * 2 > 50`
- 规则写错了，用户看到什么？`For input string: ""` 算不算"能看懂"？
- 长规则会不会崩？10000 项相加直接 `StackOverflowError`。

这五个问题，正是下一节设计推导的起点。

---

## 3. 设计动机：解释器模式到底解决了什么

### 3.1 本质：把"语法规则"变成"对象结构"

任何一种"文本 → 行为"的转换，都可以拆成三层：

```text
字符流  ──词法分析──>  记号流  ──语法分析──>  语法树  ──解释执行──>  结果
"10+5*2"           [10][+][5][*][2]        (+)             20
                                            / \
                                           10  *
                                              / \
                                             5   2
```

解释器模式只规定第三层怎么组织：

> **每个文法产生式对应一个类；类的 `interpret` 方法负责计算自己；
> 组合节点在自己的 `interpret` 里调用子节点的 `interpret`。**

于是「树」不只是数据，而是一台可以自己运转的机器。
这带来三个直接后果：

| 后果 | 含义 | 在本项目里的体现 |
|---|---|---|
| 求值顺序由树决定 | 不需要一个中央 `evaluate()` 去判断节点类型 | 六个节点类各自实现 `evaluate`，没有一个 `switch (node.type)` |
| 加一种语法 = 加一个类 | 不动任何已有节点 | 从"只有 + "到"+ - * / 比较 逻辑"，是加 4 个类与 4 个枚举值 |
| 上下文与树解耦 | 同一棵树可以喂不同数据 | Demo 1 解析一次、三次求值 |

第三条是业务价值的来源：**规则只解析一次，之后每次请求只是求值。**
解析成本 O(文本长度)，求值成本 O(节点数)，且树可以缓存、可以并发共享
（前提是树和上下文都不可变 —— 这是第 4 节的决定之一）。

### 3.2 什么时候**不需要**解释器模式

这一节比上一节更重要，因为过度使用解释器是常见病。

**不适合的场景：**

| 场景 | 为什么不适合 | 更好的选择 |
|---|---|---|
| 规则只有一条，且不会变 | 十几个类换来的是"没有变化" | 直接写 `if` |
| 文法会频繁大改（每周加新语法） | 每加一个产生式都要加类 + 改解析器 + 改测试 | 用现成脚本引擎（GraalJS、Aviator、QLExpress） |
| 需要对规则做复杂优化（常量折叠、索引下推） | 朴素的树求值没有优化层 | 编译成字节码 / 用 SQL 表达 |
| 文法本身极其简单（只有 `AND` 串联） | 一个 `List<Predicate<T>>` 就够了 | 组合谓词 + Stream |
| 输入规模极大（百万级 token） | 递归下降 + 递归求值都受栈限制 | 显式栈的字节码机 / 表驱动解析 |

**判断口诀：**

> 文法稳定、规则要由人写、规模可控 —— 用解释器模式。
> 文法不稳定、或规则要极致性能 —— 别自己写解析器。

本项目选它的理由很具体：文法固定（表达式 + 六种运算 + 括号），
规则由人写（所以需要错误定位），规模可控（单条规则几十个 token）。

### 3.3 解释器 vs 组合 vs 访问者 vs 策略

这四个模式都涉及"一堆对象 + 统一操作"，容易混。

| 模式 | 一句话区别 | 典型问题 | 本项目里有没有 |
|---|---|---|---|
| **解释器** | 用对象树表达**一门文法**，每个节点知道怎么算自己 | `10+5*2` 怎么算 | 有，全部 16 个文件 |
| **组合** | 用树表达**部分-整体**，叶子和容器接口一致 | 文件夹/文件、组织架构 | 树的形状像组合，但组合不规定"求值" |
| **访问者** | 元素固定、**操作**经常新增，把操作抽出去 | 同一批文档导出 HTML / 统计 / 审计 | 本包没有，见 `visitor/` |
| **策略** | 一个接口多个算法，运行时替换 | 三种折扣算法 | 本包没有，见 `strategy/` |

两处最容易搞混的地方：

**1）解释器 vs 组合。**
解释器的树确实"组合"了子表达式，但组合模式的目的是
**让客户端统一对待叶子和容器**（`folder.size()` 和 `file.size()` 一样调用）；
解释器的目的是**表达一门有优先级的文法**。
组合树里换子节点的顺序通常不影响结果（求和），
解释器树里换顺序就换了语义（`10-3` ≠ `3-10`）。

**2）解释器 vs 访问者。**
访问者适用于「元素类型固定、操作不断新增」；
解释器适用于「文法固定、表达式实例不断新增」。
如果有一天要给解释器树加「导出成 JSON」「做常量折叠」「统计节点数」，
**那才轮到访问者上场** —— 因为这些操作不属于文法，属于"对树的额外加工"。
本项目没有做这一步，因为三个操作都可以直接写在节点里而不破坏文法边界。
这是一个"刻意不用模式"的决定，理由见 3.2。

### 3.4 一句话判据

> **当「用户输入」本身就是一门语言（哪怕只有加减和与或），
> 而你需要精确控制它的优先级、结合性、短路和报错位置时，用解释器模式。**

反过来，如果输入只是"几个固定选项"（下拉框、枚举、开关），
那不是语言，是配置 —— 用 `switch` 或策略模式。

### 3.5 本项目在十个问题上各做什么

| 原项目问题 | 本项目的做法 | 详见 |
|---|---|---|
| I1 第三条条件覆盖第二条 | `Parser` 用 `while` 循环把同层运算**向左折叠**，条件数量不限 | 第 4.9 节、第 5.9 节 |
| I2 单条件 NPE | 不再有无条件构造二元节点；`primary` 返回什么树就是什么 | 第 4.9 节、第 5.9 节、Demo 3 |
| I3 `>=` 误判 | `Lexer` 最长匹配，`>=` 在切词阶段就是一个整体 | 第 4.4 节、第 5.5 节、Demo 4 |
| I4 报错无规则无位置 | 每个 `Token` 带 `position`，异常渲染成三行 + `^` | 第 4.11 节、第 5.7 节、Demo 5 |
| I5 未知字段静默 false | `Context.require` 抛「未定义的变量」，并列出已定义变量 | 第 4.6 节、第 5.4 节、Demo 6 |
| I6 大小写敏感无提示 | 同上：大小写不一致同样落到"未定义"，消息里直接给出可用名字 | 第 5.4 节、Demo 6 |
| I7 尾部垃圾丢弃 | `parse()` 结尾检查 `END_OF_INPUT`，否则报"多余的内容" | 第 4.10 节、第 5.9 节、Demo 8 |
| I8 五种错误同一句 | 每种情况一条专门的消息，全部带位置 | 第 5.5 节、第 5.7 节、Demo 5 |
| I9 右递归爆栈 | 解析改左递归循环；求值仍递归，但用 Demo 9 诚实测出上限 | 第 4.8 节、第 5.9 节、Demo 9 |
| I10 两套体系不通用 | 统一成 `Expression` + `Value`，算术与布尔共处一棵树 | 第 4.3 节、第 5.2 节、Demo 7 |

下一节从"如果不用解释器会怎样"开始，一步一步推导出这 16 个文件。

---
## 4. 设计推导：从 `split("&&")` 推到 16 个文件

这一节不贴最终代码，而是按「遇到什么问题 → 有哪几种选择 → 为什么选这个 → 代价是什么」
的顺序，把 16 个文件一个一个"逼"出来。读完之后，你应该能在不看源码的情况下
复述每个类存在的理由。

### 4.1 第一个决定：要不要先做词法分析

**问题**：`RuleParser` 直接对原始字符串做 `contains(">")` / `split("&&")`，
导致 `>=` 被切成 `> ` + `= 18`（探针 I3）。

**三种选择**：

| 方案 | 做法 | 结果 |
|---|---|---|
| A 打补丁 | 先 `replace(">=", "GE")` 再解析 | 能修 `>=`，但 `!=`、`<=`、`==` 都要各打一个补丁；补丁之间还会互相干扰（`>=` 被替换后，`>` 的判断又命中 `GE` 里的字符） |
| B 用正则 | `Pattern.compile("(>=|<=|==|!=|>|<)")` 一次性切 | 能跑，但正则在"最长匹配"这件事上仍然要自己写、位置信息仍然会丢 |
| C 引入词法层 | 写一个 `Lexer`，逐字符扫描，产出 `Token(type, text, position)` | 每个记号自带类型与位置；语法层永远看不到"半个运算符" |

**选 C。** 理由不是"更设计模式"，而是**位置信息**：

```text
"age >= 18"
     ^
  位置 4 才是 >= 的起点
```

一旦用 `split`，`age >= 18` 变成 `["age ", "= 18"]`，原始下标就再也找不回来了。
而报错不带位置，是原项目 I4/I8 的共同病根。
**词法层是唯一能同时给出"类型"和"位置"的地方** —— 因为只有它在逐字符读原始文本。

**代价**：多一个类、多一份枚举（`TokenType` 18 个值）、多一层调用。
换来的是后面所有错误消息都能画出 `^`。

顺带解决了 I3 的根因：`Lexer` 读 `>` 时先看下一个字符是不是 `=`，
是就产出 `GREATER_EQUAL`（最长匹配）。语法层拿到的永远是完整运算符。

### 4.2 第二个决定：`evaluate` 返回 `boolean` 还是 `Value`

**问题**：原项目有两套抽象 —— `Expression.interpret` 返回 `boolean`，
`Node.evaluate` 返回 `int`。想写 `(age + 5) * 2 > 50`，两套都用不了（探针 I10）。

**三种选择**：

| 方案 | 做法 | 缺点 |
|---|---|---|
| A 全用 `boolean` | 算术结果转布尔（非 0 即 true） | `10 && 5` 会算成 `true && true`，用户写错了却得到"合法"结果 |
| B 全用 `long` | 布尔用 1/0 表示 | 反过来：`age > 18` 得到 `1`，打印出来是 `1` 不是 `true`；且丢失"这是布尔"的语义 |
| C 用一个密封的 `Value` | `Value.Num` / `Value.Bool` 两个分支，父节点显式声明要哪种 | 每个取值点都要写 `asNumber(where)` 或 `asBoolean(where)`，略啰嗦 |

**选 C。** 关键理由是 Demo 10 的第一条：

```text
10 && 5  ->  运算符 '&&' 的左操作数 需要布尔值，实际是数字 10
```

如果选 A 或 B，这条表达式会安静地得到一个结果。而用户写 `10 && 5` 显然是想写
`age > 10 && score < 5` 之类的，**"类型错"是唯一的真相**。
`Value` 把类型检查从"运行时的隐式转换"变成"每个操作数进入运算前的显式断言"。

**为什么是 sealed interface + 两个 record，而不是泛型？**

```java
// 泛型的写法
interface Expression<T> { T evaluate(Context ctx); }
```

泛型看起来更"类型安全"，但它要求**编译期就知道每个节点的返回类型**。
而解析器的输入是文本，`Parser.parse("age > 18")` 的返回类型
只能是 `Expression<?>` 或者统一成 `Expression<Value>` ——
前者在运行时到处 `instanceof`，后者就是 C 方案的 `Value`。
既然运行时必须有一次类型判断，不如把它做成一个显式的、带错误消息的方法（`asNumber`）。

**代价**：`Value` 多两个方法、每个父节点多一行 `asNumber(role + " 的左操作数")`。
换来的是一条能指出"哪个运算、哪一侧、实际是什么"的错误消息。

### 4.3 第三个决定：`Context` 用固定 `record` 还是 `Map`

**问题**：原项目 `record Context(int age, int score)` 把字段名写死。
后果是 `GreaterThanExpression` 里必须写：

```java
if ("age".equals(field)) { return context.age() > value; }
else if ("score".equals(field)) { return context.score() > value; }
return false;                       // <- I5 和 I6 都从这里来
```

**三种选择**：

| 方案 | 做法 | 缺点 |
|---|---|---|
| A 继续用固定 record | 加字段就改 record | 每加一个变量都要改 record + 改两个节点类；认不出的名字只能 `return false` |
| B `Map<String, Integer>` | 变量表 | 解决了扩展性，但仍要决定"找不到时怎么办" |
| C `Map<String, Long>` + `require` | 找不到就抛异常，消息里列出所有可用名字 | 需要定义异常语义 |

**选 C**，并且给 `require` 定死一条语义：

```java
public long require(String name, int position) {
    Long value = variables.get(name);
    if (value == null) {
        throw new ExpressionException(
                "未定义的变量 '" + name + "'（已定义: " + namesText() + "）", null, position);
    }
    return value;
}
```

**为什么不用 `Map.getOrDefault(name, 0L)`？** 因为那会把"变量不存在"变成"变量等于 0"，
正是探针 I5 的翻版 —— 规则悄悄失效。**规则引擎里没有"默认值"这个选项**：
名字找不到就是配置错了，必须让人知道。

**为什么消息里要列 `已定义: age, score`？** 因为最常见的错误是拼写和大小写。
把可用名字直接摆在眼前，`AGE` 和 `age` 的差别一眼就能看出来（探针 I6）。
这比"Unknown variable"六个字有用得多。

**为什么用 `Long` 而不是 `long`？** 因为 `Map.get` 返回 `null` 才能表示"不存在"。
用 `long` 的话，"不存在"和"值为 0"无法区分 —— 又是 I5 那个坑。

**代价**：变量名从"编译期字段"变成"运行期字符串"，拼错不会被编译器发现。
但这个代价原项目也有（`field` 本来就是字符串），区别只在于**发现时机**：
原项目是"用户发现规则没生效"，本项目是"解析后第一次求值立刻抛异常"。

### 4.4 第四个决定：`Context` 可变还是不可变

**问题**：同一棵树要喂三份数据（Demo 1 的 17/25/25）。如果 `Context` 可变，
"加一个变量"就会影响所有持有同一份上下文的调用。

**两种选择**：

| 方案 | 做法 | 后果 |
|---|---|---|
| A 可变 | `void set(String name, long value)` | 树求值过程中如果有节点改了上下文，父节点的结果就不可复现；并发下直接数据竞争 |
| B 不可变 | `Context with(String name, long value)` 返回新实例 | 每次派生复制一次 Map |

**选 B。** 三条理由：

1. **求值必须可复现**：同一棵树 + 同一份上下文 = 同一个结果。可变上下文让这句话失效。
2. **树可以并发共享**：解析一次、多个线程求值，是规则引擎最典型的用法。
3. **错误定位**：如果求值到一半上下文被改了，报错里的 `已定义: age, score`
   可能和用户以为的不一致。不可变上下文保证"报错时的变量表"就是"求值时的变量表"。

**代价**：`with` 每次复制 `LinkedHashMap`，n 个变量就是 O(n) 次复制，构建 k 个变量是 O(k²)。
本项目变量只有两三个，成本可以忽略。
**如果变量上千**，正确做法不是改回可变，而是提供一个 `Context.of(Map)` 一次性建好。

**为什么用 `LinkedHashMap` 而不是 `HashMap`？**
因为 `describe()` 和错误消息都要列变量名，而 README 的哈希校验要求输出**逐字节可复现**。
`HashMap` 的迭代顺序在同一 JDK 内碰巧稳定，但那是实现细节，不该依赖。

**为什么 `empty()` 用 `Collections.emptyMap()`？**
空 Map 是共享的单例，`with` 第一次派生时才 `new LinkedHashMap<>(emptyMap)`。
这样"没有任何变量的上下文"不占额外对象。

### 4.5 第五个决定：一元负号归词法层还是语法层

**问题**：`-5` 里的负号，和 `10-5` 里的减号，是同一个字符。
如果 `Lexer` 直接把 `-5` 切成一个负数，那么 `10-5` 会被切成 `[10][-5]` ——
两个数字挨着，语法层无法解释。

**两种选择**：

| 方案 | 做法 | 后果 |
|---|---|---|
| A 词法层吞前导负号 | 遇到 `-` 且后面是数字，产出负数 token | `10-5` 变成 `[10][-5]`，必须靠"前一个 token 是什么"来区分，词法层被迫理解语法 |
| B 词法层只切 `MINUS` | 语法层在"期望操作数"的位置接受 `-`，当作一元运算 | 需要多一个 `UnaryExpression` 类 |

**选 B。** 这是编译原理里的经典分工：

> **词法层只看"字符能不能组成一个单词"，语法层才看"这个单词在这个位置是什么意思"。**

`10 - 5` 和 `-5` 里的 `-` 在词法层完全一样（都是 `MINUS`），
区别只在于它前面有没有操作数 —— 这是**语法位置**，只有语法层知道。

于是 `Parser.unary()` 承担了这个判断：

```java
private Expression unary() {
    if (check(TokenType.MINUS) || check(TokenType.NOT)) {
        Token token = advance();
        // 这里明确知道：前面没有操作数，所以这是"一元"
        return new UnaryExpression(operator, unary());
    }
    return primary();
}
```

**顺带的收益**：`!` 也走同一个分支。`-` 和 `!` 结构完全一样（一个操作数 + 一个前置符号），
合并成一个 `UnaryExpression` 之后，`describe` 和 `Parser` 都只需要处理一种形状。
代价是 `evaluate` 里要按 `operator` 分支 —— 而这正好是类型检查点：
`!5` 会报「需要布尔值，实际是数字 5」，而不是算出 `false`。

### 4.6 第六个决定：同层运算用递归还是循环

**问题**：原项目 `parseAddition` 递归调用自己，两个后果（探针 I9）：
栈深度 = 项数、结合性变右结合。

**两种选择**：

```java
// A 递归（右结合，栈深 = 项数）
Node parseAddition() {
    Node left = parseNumber();
    if (match('+')) {
        return new AddNode(left, parseAddition());   // 递归
    }
    return left;
}

// B 循环（左结合，栈深 = 1）
Node additive() {
    Node left = multiplicative();
    while (match('+') || match('-')) {
        left = new ArithmeticExpression(op, left, multiplicative());   // 往左上生长
    }
    return left;
}
```

**选 B。** 两个理由都很硬：

**1）结合性。** `10 - 3 - 2` 必须等于 5。
B 方案第一次循环得到 `(10-3)`，第二次得到 `((10-3)-2)` —— 正确。
A 方案得到 `10-(3-2)=9` —— 错误。加法看不出差别（结合律），减法立刻暴露。

**2）栈深度。** B 方案同层 1000 项只占一层栈（循环变量 `left` 反复替换）。
A 方案 1000 项压 1000 层栈。Demo 9 实测：原项目 10000 项就 `StackOverflowError`。

**这不是"优化"，是正确性问题。** 一个 1000 项的规则在业务里并不夸张
（比如"满足以下任意 1000 个 SKU 之一"），原项目直接崩。

**代价**：B 方案的树是**左深**的（`(((1+1)+1)+1)`），
求值时仍然要递归 1000 层 —— 所以 Demo 9 里本项目 10000 项求值也会 `StackOverflowError`。
这一半是诚实的遗留问题，见 4.14。

**左深树长什么样**（Demo 9 的 4 项形状）：

```text
        (+)
       /   \
     (+)    1
    /   \
  (+)    1
  / \
 1   1
```

### 4.7 第七个决定：`&&` / `||` 要不要短路

**问题**：`count > 0 && total / count > 10` 是规则里最常见的写法 ——
用前一个条件保护后一个条件。如果 `&&` 先把两边都算完再合并，`count=0` 时就会抛「除数为 0」。

**两种选择**：

| 方案 | 做法 | 后果 |
|---|---|---|
| A 先算两边 | `left.evaluate() && right.evaluate()`（Java 的 `&&` 也是短路，但若先存到变量里就失去短路） | `count=0` 时抛除零，规则永远无法表达"先判断再算" |
| B 显式短路 | 先算左边，`AND` 且左边为 false 就直接返回 false；`OR` 且左边为 true 就直接返回 true | 需要 4 行额外代码 |

**选 B。** 而且 B 不只是性能优化，**短路是语义**：

```java
boolean leftValue = left.evaluate(context).asBoolean(role + " 的左操作数");
if (operator == Operator.AND && !leftValue) {
    return Value.of(false);          // 右边不求值
}
if (operator == Operator.OR && leftValue) {
    return Value.of(true);
}
boolean rightValue = right.evaluate(context).asBoolean(role + " 的右操作数");
```

Demo 10 实测：

```text
count=0, total=100 -> false（右侧未求值，没有抛除零）
count=2, total=100 -> true
```

**短路也带来一个必须承认的代价**：右侧的"未定义变量"可能永远不被检查。

```text
false && AGE > 18   ->  false，不报错
```

这不是 bug，是短路的定义。我们选择与 Java 的 `&&` 保持一致 ——
**一致性比"更严格"更重要**，否则用户会因为条件顺序不同而看到不同的报错，
排查时更困惑。这一条写在 `LogicalExpression` 的 javadoc 里，作为显式约定。

**为什么不用 `|` / `&` 这种非短路版本？** 因为规则语言里 99% 的场景需要保护。
Java 里 `&&` 和 `&` 的取舍是"性能 vs 副作用"，规则语言里是"能不能写出安全的规则"。
后者没有商量余地。

---
### 4.8 第八个决定：括号信息保留不保留

**问题**：`(10 + 5) * 2` 和 `10 + 5 * 2` 的区别，在解析完成后还需要吗？

**两种选择**：

| 方案 | 做法 | 后果 |
|---|---|---|
| A 保留括号节点 | 加一个 `GroupExpression` | 树上多一种"什么都没干"的节点；`describe()` 会把用户写的括号原样输出，但无法证明优先级 |
| B 丢掉括号 | 括号只影响"何时返回"，不进入树 | `(10) + 5` 与 `10 + 5` 得到同一棵树 |

**选 B。** 这是所有语法分析器的常态：

> **树表达语义，不表达排版。**

`(10) + 5` 和 `10 + 5` 语义完全相同，没有理由产生两棵不同的树。
括号的作用在 `Parser.primary` 里已经完成：

```java
if (check(TokenType.LEFT_PAREN)) {
    advance();
    guardNesting(token);
    nesting++;
    try {
        Expression inner = or();               // 括号里重新从最低优先级开始
        expect(TokenType.RIGHT_PAREN, "缺少右括号 ')'");
        return inner;                          // 直接把内部表达式交出去，不包装
    } finally {
        nesting--;
    }
}
```

`return inner` 这一行就是"丢掉括号"的实现。括号的价值体现在
**它让内部表达式在 `multiplicative` 还没轮到的时候先成型**：

```text
(10 + 5) * 2
         ↑            乘法发现左边已经是一个完整的 (+) 子树

10 + 5 * 2
       ↑              加法发现右边是一个完整的 (*) 子树
```

**代价**：`describe()` 输出的括号是"规范化"的，不一定和用户写的一样。
用户写 `(10) + 5`，`describe()` 输出 `(10 + 5)`。
这不是失真，反而是好事 —— **它证明的是"我理解的优先级"，而不是"你写的字"。**

**为什么括号也要算进嵌套层数？** 因为它会引起递归：`primary -> or -> ... -> primary`。
500 层括号就是 500 圈递归，不设上限就会在解析阶段爆栈（而不是求值阶段）。
详见 4.14。

### 4.9 第九个决定：解析结束要不要检查剩余输入

**问题**：原项目 `10*5` 算出 10，`10abc` 算出 10（探针 I7）。

**两种选择**：

| 方案 | 做法 | 后果 |
|---|---|---|
| A 不管 | 解析到哪算哪 | 静默给出错答案，最危险 |
| B 强制消费完 | `parse()` 结尾检查 `peek().isEnd()` | 需要给"输入结束"一个真实的 token（而不是 `pos < length` 这种边界判断） |

**选 B**，并且为它专门引入了 `END_OF_INPUT` 这个 token：

```java
public Expression parse() {
    if (peek().isEnd()) {
        throw error(peek(), "表达式不能为空");
    }
    Expression expression = or();
    Token token = peek();
    if (!token.isEnd()) {
        throw error(token, "多余的内容 " + token.display() + "，表达式应在此结束");
    }
    return expression;
}
```

**为什么要一个真实的 `END_OF_INPUT` token，而不是判断 `index >= tokens.size()`？**

三个实际好处：

1. **`peek()` 永远不需要判空**：`tokens.get(index)` 永远合法（末尾是 END），不会 `IndexOutOfBoundsException`。
2. **错误消息统一**：`token.display()` 对 END 返回"输入结束"，于是
   `10 +` 报的是「表达式在此处意外结束，缺少操作数」，而不是「数组越界」。
3. **`advance()` 不会越过末尾**：`if (!token.isEnd()) index++`，
   语法层永远停在 END 上，不会出现"越界后 `peek` 炸掉"。

**代价**：`Lexer` 必须无条件追加一个 token，
且 `Token.text` 对 END 是空串（需要一个特例）。

**这一条的危险性值得单独强调**：I7 是全部十个问题里唯一
**"输入明显错误但程序完全不报错"** 的一条。
`10*5 -> 10` 意味着用户写错的规则会被当成另一条规则执行 —— 在规则引擎里，
这比崩溃严重得多。

### 4.10 第十个决定：每种运算符拆类还是用枚举

**问题**：六种比较、四种算术、两种逻辑、两种一元。全拆类就是 14 个类，
全合成一个类就是一个巨大的 `switch`。

**三种选择**：

| 方案 | 做法 | 缺点 |
|---|---|---|
| A 每个运算符一个类 | `AddExpression`、`SubtractExpression`、`MultiplyExpression`…… | 14 个类的字段声明和 `evaluate` 骨架完全相同，只有一行不同 |
| B 全部塞进一个 `BinaryExpression` | 一个类 + 一个 14 值的枚举 | 算术、比较、逻辑的**返回类型不同**、**求值顺序不同**（短路）、**错误语义不同**，塞在一起 `evaluate` 会出现"这个分支不适用"的尴尬 |
| C 按"结构"拆成 4 类，每类内部用枚举 | `Arithmetic` / `Comparison` / `Logical` / `Unary` 四类，各带一个枚举 | 需要判断"什么算结构相同" |

**选 C。** 判断标准一句话：

> **结构不同才拆类，行为不同用枚举。**

按这个标准逐一检查：

| 节点 | 字段 | 子节点求值顺序 | 返回类型 | 是否拆类 |
|---|---|---|---|---|
| 算术 `+ - * /` | operator + 左 + 右 | 先左后右（都要算） | 数字 | 四者相同 → 一个类 |
| 比较 `> >= < <= == !=` | operator + 左 + 右 | 先左后右（都要算） | 布尔 | 四者相同 → 一个类 |
| 逻辑 `&& ||` | operator + 左 + 右 | **可能不算右边** | 布尔 | 与前两者不同 → 单独一个类 |
| 一元 `- !` | operator + 一个子节点 | 只算一个 | 数字或布尔 | 字段数量不同 → 单独一个类 |
| 数字 / 变量 | 无子节点 / 名字 | 不递归 | 数字 | 终结符 → 各一个 record |

四类非终结符的**结构差异**是真实存在的：

- `ArithmeticExpression` 和 `ComparisonExpression` 结构完全一样，
  只是"往外吐的值类型"不同 —— 如果合并，`evaluate` 要返回一个 `Value`，
  而调用方还得自己判断"这是算术节点还是比较节点"。分开更清楚。
- `LogicalExpression` 必须自己控制求值顺序（短路），不能复用"先算两个再合并"的模板。
- `UnaryExpression` 只有一个子节点，字段数量都不同。

**为什么算术的符号和计算逻辑放在同一个枚举里？**

```java
public enum Operator {
    ADD("+")     { long compute(long a, long b) { return Math.addExact(a, b); } },
    SUBTRACT("-"){ ... },
    ...
}
```

因为"多一个运算符"应该只改一处。
如果 `Parser` 里 `switch` 一遍把 token 映射成枚举、
`ArithmeticExpression` 里再 `switch` 一遍决定怎么算，
两处 `switch` 就会慢慢不同步 —— 原项目里"买一赠一公式写两份"（访问者探针 V5）就是同类问题。

本项目里 `Parser` 只做「token → 枚举」，`Operator` 只做「枚举 → 计算」，
新增 `%` 需要改：`TokenType` 加一项、`Lexer` 加一个 case、`Operator` 加一项、
`Parser.multiplicative` 的 while 条件加一项。**四处，每处一行，编译器会全部提示。**

### 4.11 第十一个决定：为什么要 `describe()`

**问题**：`10 + 5 * 2` 求值得 20。这个 20 能证明"乘法先算"吗？

不能。如果解析成 `(10 + 5) * 2`，结果是 30，确实不同 ——
但 `2 * 3 + 4` 与 `2 * (3 + 4)` 一个 10 一个 14，也"不同"，
可如果换成 `1 * 3 + 0` 呢？两种结合都是 3。
**求值结果相同，不能证明树相同。**

**两种选择**：

| 方案 | 做法 | 后果 |
|---|---|---|
| A 只靠求值结果 | 写一堆用例，比较数字 | 需要精心挑选"两种结合结果不同"的输入；覆盖不全就漏掉解析错误 |
| B 把树还原成文本 | 每个节点实现 `describe()`，输出带完整括号的规范文本 | 多一个方法（每个节点 1 行） |

**选 B。** 这是"结果对 ≠ 实现对"的检查手段：

```text
10 + 5 * 2     树 (10 + (5 * 2))        值 20
(10 + 5) * 2   树 ((10 + 5) * 2)        值 30
10 - 3 - 2     树 ((10 - 3) - 2)        值 5
```

一眼就能看出乘法被优先结合、减法左结合。**只要看树，不需要设计用例。**

**为什么 `describe()` 要无条件加括号？**
因为不加括号的 `describe` 又变成了"靠读者脑补优先级"，等于没解决问题。
全括号形式没有歧义：`((10 + 5) * 2)` 只有一种读法。

**代价**：`describe()` 在深层树上很贵 —— 每个节点都要拼自己两个子树的文本，
左深链 1000 项的总字符数是 O(n²) 量级。Demo 9 实测：

```text
1000 项的 describe() 长度 5995 字符
```

这个代价引出了一个真实的性能事故（见 4.12 末尾与第 5.10 节）：
**求值路径上绝对不能调 `describe()`**。

### 4.12 第十二个决定：异常怎么带位置

**问题**：原项目报 `For input string: "eighteen"`（I4）、`For input string: ""`（I8），
既没有规则原文，也没有位置。

**三种选择**：

| 方案 | 做法 | 缺点 |
|---|---|---|
| A 直接用 JDK 异常 | `NumberFormatException` / `NullPointerException` | 消息描述的是"哪个 JDK 方法失败"，不是"用户哪里写错" |
| B 自定义异常，只带消息 | `throw new ExpressionException("变量未定义")` | 有中文了，但仍然无法定位 |
| C 自定义异常 + 源码 + 下标 | `ExpressionException(message, source, position)` + `render()` | 每个抛异常的地方都要能拿到 `position` |

**选 C。** 这解释了为什么 `Token` 必须带 `position`、`VariableExpression` 必须存 `position`：

- 词法错误：字符在 `source` 里的下标，`Lexer` 直接知道；
- 语法错误：出问题的 token 的 `position`，`Parser` 从 `Token` 里拿；
- 求值错误（变量未定义）：变量 token 的位置，`VariableExpression` 在解析时存下来。

三条链都指向同一个字段，`position` 因此成为"整套重构里最有价值的一个字段"。

**为什么 `render()` 和 `getMessage()` 分开？**

| 方法 | 内容 | 用途 |
|---|---|---|
| `getMessage()` | 一句话：「未定义的变量 'AGE'（已定义: age, score）」 | 写日志、往上层抛 |
| `render()` | 三行：消息 + `规则: ...` + `^` | 直接给终端用户看 |

如果只有 `getMessage()`，为了好看就得把源码拼进消息，日志里会多出一堆换行；
如果只有 `render()`，日志系统会拿到多行文本，难以聚合。
**两个消费者，两个方法。**

**`render()` 的窗口截断是怎么来的？**

第一版没有截断。Demo 10 跑「201 层括号」时，错误信息把 403 字符的规则整段打印出来，
控制台折行后 `^` 完全对不上位置：

```text
表达式嵌套层数超过上限 200，请拆分规则
  规则: ((((((((((((((((((((((((((((... 403 个字符 ...)))))))))))))))))))))))))))
                                                              ^   <- 折行后跑到下一行
```

于是加了 `MAX_SOURCE_CHARS = 100` + `CARET_CONTEXT = 30`：只保留出错位置附近的窗口，
两侧用 `...` 标记。**注意用 3 个 ASCII 点而不是省略号字符 `…`** ——
一个汉字宽的字符会让 `^` 的空格数和显示宽度对不上。

**性能事故（真实发生）**：第一版 `ArithmeticExpression.evaluate` 里写了

```java
String where = describe();                       // 每个节点都拼一次整棵子树
long leftValue = left.evaluate(context).asNumber(where + " 的左操作数");
```

左深链 50000 项时，根节点拼 50000 个字符、下一层 49999 个……总计约 1.25e9 次字符拷贝。
实测：**进程 CPU 跑到 272 秒仍未结束**，输出停在 Demo 9 第 111 行。
修复方式是把 `describe()` 换成短前缀：

```java
String role = "运算符 '" + operator.symbol() + "'";     // 只有几个字符
```

完整 `describe()` 只在真正抛异常（除零）时调用一次。
这一条写进了 `ArithmeticExpression` 的 javadoc，因为它**违反了"日志/诊断代码不在热路径"的常识，
而且用一次实测卡死才被发现**。

### 4.13 第十三个决定：要不要加 `ExpressionEngine` 门面

**问题**：客户端要做的事其实只有一句："给定规则和数据，告诉我结果"。
但如果直接暴露 `Parser` + `Expression`：

```java
Expression tree = new Parser(rule).parse();       // 三步
Value value = tree.evaluate(context);
boolean ok = value.asBoolean(tree.describe());
```

**两种选择**：

| 方案 | 做法 | 缺点 |
|---|---|---|
| A 不加门面 | 调用方自己组合 `Lexer`/`Parser`/`Expression` | 90% 的调用方会写错顺序（先 `evaluate` 还是先 `parse`）；`asBoolean` 的 `where` 参数没人知道该传什么 |
| B 加门面 | 四个静态方法：`parse` / `evaluate` / `test` / `describe` | 多一个类 |

**选 B，但要强调门面没有隐藏任何能力**：

```java
public static Expression parse(String source)        // 要树？给你树
public static Value evaluate(String source, Context) // 只要值？
public static boolean test(String source, Context)   // 只要布尔？
public static String describe(String source)         // 只要规范文本？
```

`parse` 照样把整棵树交出去，想自己遍历、自己缓存、自己 `describe` 都行。
门面只是把"调用顺序"固化成一句代码。

**为什么是静态方法而不是单例 + 实例方法？**
引擎没有任何状态（状态全在 `Expression` 树和 `Context` 里）。静态方法最直白，
也不需要解释"这个单例为什么是单例"。
真正需要状态的是**规则缓存**，那是调用方该关心的事 —— 本项目不引入缓存，
因为示例里每条规则只解析一两次，收益为零。

**私有构造器为什么抛 `AssertionError` 而不是空着？**

```java
private ExpressionEngine() {
    throw new AssertionError("工具类不允许实例化");
}
```

空构造器在反射调用时会安静地创建对象；抛 `AssertionError` 能在
"有人用反射实例化工具类"这种诡异场景下立刻失败。
这是 JDK 自己的 `java.lang.reflect.Modifier`、`Collections` 等工具类的惯例。

### 4.14 第十四个决定：求值的递归要不要也消除

**问题**：4.6 把解析改成了循环，但求值仍然是递归：

```java
// ArithmeticExpression.evaluate
long leftValue = left.evaluate(context).asNumber(...);    // 递归
long rightValue = right.evaluate(context).asNumber(...);  // 递归
```

左深链 10000 项 → 10000 层求值递归 → `StackOverflowError`。

**三种选择**：

| 方案 | 做法 | 后果 |
|---|---|---|
| A 不管 | 承认限制，写进文档 | 10000 项以上的规则不可用 |
| B 显式栈 | 用 `Deque<Frame>` 手工模拟递归 | 所有节点类的 `evaluate` 都要重写成"状态机"，代码量翻倍，可读性大幅下降 |
| C 摊平成 N 元节点 | `AddExpression(List<Expression>)` 而不是二元 | 只解决"同层"问题，嵌套（括号）仍然递归 |

**本项目选 A，并把实测数据写进 Demo 9。**

理由：

1. **真实规则不会有一万项。** 一条风控规则几十个 token 已经很长。
2. **B 的代价太大**：显式栈版本会把"每个节点自己解释自己"变成
   "一个中央解释器 + 一堆状态"，**恰好破坏了模式的核心价值**。
3. **C 值得做，但属于另一个需求**：它只优化"同级长链"，而且会让
   `describe()` 的输出形式改变（`(1 + 1 + 1)` 而不是 `(((1 + 1) + 1) + 1)`）。

但"承认限制"不等于"不测量"。Demo 9 做了三件事：

```text
1000 项    文本 3997 字符     解析成功，默认栈求值 -> 结果 1000
10000 项   文本 39997 字符    解析成功，默认栈求值 -> StackOverflowError
50000 项   文本 199997 字符   解析成功，默认栈求值 -> StackOverflowError
```

```text
50000 项换 256 MB 栈的线程 -> 结果 50000
```

最后一行是关键：**换个更大的栈就成功了，说明算得对，只是栈不够。**
这是"限制"和"bug"的区别：

- 如果是 bug，换多大栈都会算错；
- 如果是栈限制，栈够大就对。

**这也解释了为什么必须诚实报告**：如果 Demo 9 只写"支持长表达式"，
读者 10000 项时就崩；只有把三档实测摆出来，读者才知道边界在哪。

**未来的正确改法**（写进 `Client` 的注释）：

```text
真要跑百万项，得改成显式栈或把同级链摊平成 N 元节点
```

**总结这十四个决定：**

| # | 决定 | 选了什么 | 核心理由 |
|---:|---|---|---|
| 1 | 要不要词法层 | 要 `Lexer` + `Token` | 只有逐字符扫描能同时给出类型与位置 |
| 2 | 返回 `boolean` 还是 `Value` | `Value`（Num / Bool） | 类型错必须能表达成错误 |
| 3 | `Context` 用 record 还是 Map | `Map` + `require` 抛异常 | 名字找不到不能变成 `false` |
| 4 | `Context` 可变还是不可变 | 不可变 `with` | 求值可复现、可并发 |
| 5 | 一元负号归谁 | 语法层 `UnaryExpression` | 词法层只看字符，语法层才看位置 |
| 6 | 同层用递归还是循环 | 循环 | 结合性正确 + 栈深 O(1) |
| 7 | 逻辑运算是否短路 | 是，显式短路 | 短路是规则语义，不是优化 |
| 8 | 括号是否保留 | 丢掉 | 树表达语义，不表达排版 |
| 9 | 是否检查剩余输入 | 是，`END_OF_INPUT` | I7 是最危险的静默错误 |
| 10 | 运算符拆类还是枚举 | 按结构拆 4 类 + 枚举 | 结构不同才拆类 |
| 11 | 要不要 `describe()` | 要 | 结果对 ≠ 树对 |
| 12 | 异常怎么带位置 | 自定义异常 + `render()` | 用户要的是"哪里错"，不是"哪个 JDK 方法失败" |
| 13 | 要不要门面 | 要，四个静态方法 | 固化调用顺序，但不隐藏能力 |
| 14 | 求值递归是否消除 | 不消除，但实测并写明边界 | 消除的代价是破坏模式本身 |

---

## 5. 代码解析：每个文件为什么存在

这一节逐个文件讲「它负责什么、关键代码为什么这么写、删掉或合并会怎样」。
16 个文件按依赖顺序排列：先抽象（`Expression`、`Value`、`Context`），
再词法语法（`TokenType`、`Token`、`Lexer`、`Parser`），
再六个节点，最后异常、门面、客户端。

### 5.1 `Expression`：整个模式的地基，只有两个方法

**84 行，`sealed interface`，permits 六个实现。**

```java
public sealed interface Expression permits NumberExpression, VariableExpression,
        ArithmeticExpression, UnaryExpression, ComparisonExpression, LogicalExpression {

    Value evaluate(Context context);

    String describe();

    default boolean test(Context context) {
        return evaluate(requireContext(context)).asBoolean(describe());
    }

    static Context requireContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为 null");
        }
        return context;
    }
}
```

**为什么只有 `evaluate` 和 `describe` 两个抽象方法？**

因为它们对应解释器模式的两个基本能力：

| 方法 | 回答的问题 | 谁在用 |
|---|---|---|
| `evaluate(context)` | 这棵树在**这份数据**下等于什么 | 父节点递归调用、`ExpressionEngine.evaluate` |
| `describe()` | 这棵树**长什么样** | Demo 7 验证优先级、`test()` 的错误消息 |

一个节点如果没有 `describe`，就只能靠"猜"验证解析是否正确（见 4.11）。
一个节点如果没有 `evaluate`，就不叫表达式。

**`test()` 为什么是 `default` 而不是抽象方法？**

因为它在六个节点里的实现会完全一样：

```java
return evaluate(context).asBoolean(describe());
```

放进接口，六个 record 一个都不用写。而它存在的理由是使用频率：
规则引擎 90% 的调用方只想要一个 `boolean`，
不想写 `ExpressionEngine.evaluate("age > 18", ctx).asBoolean("...")`。

注意 `test()` 里 `asBoolean(describe())` 的 `where` 参数传的是 `describe()` ——
只有在这一处（用户主动要求布尔）才值得付出拼整棵树的代价，
因为一旦失败，消息里能给出完整的规则形状。**这也是 `describe()` 唯一出现在
非诊断路径上的地方**，且只在 `test` 的最外层调用一次，不是每个节点都调。

**`requireContext` 为什么是接口里的 `static` 方法？**

三个好处：

1. **消息统一**：所有地方都是「context 不能为 null」，不会有人抛 NPE、有人抛 IAE。
2. **叶子节点自己调**：`NumberExpression.evaluate` 第一行就是 `Expression.requireContext(context)` ——
   它虽然不需要上下文，但**接口承诺了"传进来的上下文一定非 null"**，要守住。
3. **组合节点不用重复**：`ArithmeticExpression` 只是把 context 往下传，
   真正的校验发生在叶子，所以组合节点一行都不用写。

**为什么是 `sealed`？**

节点类型是封闭的六种。`sealed` 让编译器知道"所有实现都在这个文件列表里"，
将来加第七种节点时，所有需要同步修改的地方都会暴露（虽然本项目没有 exhaustive switch，
但 `permits` 列表本身就是一份权威清单）。
这和访问者模式里 `Document` 用 `sealed` 是同一个理由。

**删掉会怎样？**
六个节点无法被统一递归调用 —— 父节点的字段类型就得写成 `Object`，
每个 `evaluate` 开头先 `instanceof` 判断。那正是模式要消灭的东西。

### 5.2 `Value`：让"类型错"成为一等公民

**125 行，`sealed interface` + 两个嵌套 record。**

```java
public sealed interface Value {

    static Value of(long value)     { return new Num(value); }
    static Value of(boolean value)  { return new Bool(value); }

    long    asNumber(String where);
    boolean asBoolean(String where);
    String  display();

    record Num(long value) implements Value { ... }
    record Bool(boolean value) implements Value { ... }
}
```

**它解决的三个具体问题：**

**1）`10 && 5` 必须报错。**

```java
// Num 的实现
public boolean asBoolean(String where) {
    throw new ExpressionException(where + " 需要布尔值，实际是数字 " + value, null, 0);
}
```

Demo 10 的实际输出：

```text
运算符 '&&' 的左操作数 需要布尔值，实际是数字 10
```

如果 `Value` 只有一种类型（比如统一用 `long`，true=1），这句话永远说不出来。

**2）`(age > 18) + 1` 必须报错。**

```text
运算符 '+' 的左操作数 需要数字，实际是布尔值 true
```

`Bool.asNumber` 抛异常，`ArithmeticExpression` 不需要写任何判断。

**3）父节点必须显式声明要什么。**

看 `ArithmeticExpression.evaluate` 的两行：

```java
long leftValue = left.evaluate(context).asNumber(role + " 的左操作数");
long rightValue = right.evaluate(context).asNumber(role + " 的右操作数");
```

`role` 是 `"运算符 '+'"`，拼出来的 `where` 是
`"运算符 '+' 的左操作数"`。**每一侧都有独立的描述**，
所以错误消息能精确到"哪个运算符的哪一侧"。

**为什么两个 record 嵌在接口里，而不是独立文件？**

- 它们各自只有一个字段、两三行逻辑；
- 独立成文件会让读者在"值"和"值类型"之间来回跳；
- 嵌进来后，`sealed` 的 `permits` 可以省略（同一编译单元里的实现类自动被允许）。

**为什么 `display()` 不能直接 `toString()`？**
`record` 的 `toString()` 是 `Num[value=10]`。输出需要的是 `10` / `true`。
`display()` 把这个"给人看的形式"和"调试用的形式"分开。

**删掉会怎样？**
只剩两种选择：全部用 `boolean`（算术丢失精度与语义）或全部用 `long`（布尔变成 1/0）。
两者都会让 `10 && 5` 得到一个"合法"的结果。

### 5.3 `Context`：变量表，以及"找不到"的处理哲学

**165 行，不可变，内部 `Map<String, Long>`。**

```java
public final class Context {

    private final Map<String, Long> variables;

    private Context(Map<String, Long> variables) {   // 私有：只能通过 empty/with 创建
        this.variables = variables;
    }

    public static Context empty() {
        return new Context(Collections.emptyMap());
    }

    public Context with(String name, long value) {
        if (name == null)  { throw new IllegalArgumentException("变量名不能为 null"); }
        if (name.isBlank()){ throw new IllegalArgumentException("变量名不能为空白字符串"); }
        Map<String, Long> copy = new LinkedHashMap<>(variables);
        copy.put(name, value);
        return new Context(Collections.unmodifiableMap(copy));
    }

    public long require(String name, int position) {
        Long value = variables.get(name);
        if (value == null) {
            throw new ExpressionException(
                    "未定义的变量 '" + name + "'（已定义: " + namesText() + "）", null, position);
        }
        return value;
    }
    // contains / names / size / describe / namesText / toString
}
```

**三个设计点：**

**1）`with` 返回新实例，`this` 不变。**

```text
Context.empty().with("age", 25).with("score", 300)
       ↑ 空               ↑ 新实例          ↑ 又一个新实例
```

Demo 1 里 `minor` / `adult` / `adultHighScore` 三个上下文互不影响，
就是因为每次 `with` 都是新的 Map。如果 `with` 是 `void`，
三次构建后只剩最后一份数据，前两次的求值结果会跟着变 —— 而且是在求值之后变。

**2）`require` 的四种失败信息（实际只有一种，但要点很多）：**

```text
未定义的变量 'AGE'（已定义: age, score）
  规则: AGE > 18
        ^
```

- `'AGE'` 是用户写的名字，原样回显；
- `已定义: age, score` 按插入顺序列出，`AGE` vs `age` 一眼可见；
- `position` 来自 `VariableExpression` 保存的 token 下标，所以 `^` 指得准。

**3）`LinkedHashMap` + `unmodifiableMap` 的双重保证。**

- `LinkedHashMap` 保证迭代顺序 = 插入顺序 → `describe()` 和错误消息可复现；
- `unmodifiableMap` 保证 `names()` 返回的 `Set` 不能被外部改 → 上下文真正不可变。

**为什么 `require` 的 `source` 参数传 `null`？**
求值阶段已经拿不到原始规则文本了（`Context` 不持有源码）。
`ExpressionException.render()` 对 `source == null` 有专门处理：
只输出消息和位置，不画 `规则:` 行。
这是"异常能带多少信息就带多少"的折中 ——
在解析阶段抛的异常（词法/语法）都有完整源码，求值阶段的没有。

**删掉会怎样？**
退回 `record Context(int age, int score)`，字段名写死。
`weight` 这个词永远找不到，且只能 `return false`（探针 I5/I6）。

### 5.4 `TokenType`：18 个值，每一个都有理由

**74 行，一个枚举。**

```java
public enum TokenType {
    NUMBER, IDENTIFIER,                                  // 两大类操作数
    PLUS, MINUS, STAR, SLASH,                            // 算术
    GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,            // 比较（4 个）
    EQUAL, NOT_EQUAL,                                    // 相等（2 个）
    AND, OR, NOT,                                        // 逻辑
    LEFT_PAREN, RIGHT_PAREN,                             // 括号
    END_OF_INPUT                                         // 输入结束
}
```

**为什么比较运算和相等运算分开列，而不是一个 `COMPARISON`？**
因为它们的**优先级不同**：

```text
age > 18 && score < 500      // > 和 < 高于 &&
age == 18 && score != 500    // == 和 != 低于 > <，高于 &&
```

文法里的层次是 `equality -> comparison -> additive`。
如果合成一个 token 类型，`Parser` 就分不清 `a > b == c` 该怎么结合
（Java 里 `==` 低于 `>`，所以是 `(a > b) == c`）。
分开列，`equality()` 和 `comparison()` 各管一层，优先级自动正确。

**为什么 `END_OF_INPUT` 是真实 token？**
见 4.9。三个好处：`peek()` 永不越界、错误消息统一、`advance()` 不会跑出末尾。

**删掉会怎样？**
退回 `String.contains(">")`：`>=` 被切成两半（I3），
`10*5` 的 `*` 根本不被识别（I7），报错没有位置（I4）。

### 5.5 `Token`：三个字段，一个最重要的

**66 行，`record`。**

```java
public record Token(TokenType type, String text, int position) {

    public Token {                                    // 紧凑构造器
        if (type == null)     { throw new IllegalArgumentException("type 不能为 null"); }
        if (text == null)     { throw new IllegalArgumentException("text 不能为 null"); }
        if (position < 0)     { throw new IllegalArgumentException("position 不能为负，实际 " + position); }
    }

    public String display() {
        if (type == TokenType.END_OF_INPUT) { return "输入结束"; }
        return "'" + text + "'";
    }

    public boolean isEnd() {
        return type == TokenType.END_OF_INPUT;
    }
}
```

**`position` 是整套重构里最有价值的一个字段。**

它让三种错误都能画出 `^`：

| 错误类型 | position 从哪来 | 实际输出 |
|---|---|---|
| 词法错误 | `Lexer` 的 `start` | `无法识别的字符 '@'` + 指针 |
| 语法错误 | 出问题的 token | `多余的内容 'abc'，表达式应在此结束` + 指针 |
| 求值错误（变量） | `VariableExpression` 存下的 token 下标 | `未定义的变量 'AGE'` + 指针 |

**`display()` 为什么把 END 显示成"输入结束"？**

因为错误消息要读得像人话：

```text
表达式在此处意外结束，缺少操作数          <- 好
这里需要一个数字、变量名或左括号，实际是 ''   <- 差（空字符串看不出发生了什么）
```

**为什么 `position` 是 int 而不是行/列？**
表达式是单行文本，没有换行概念。要用多行规则时再引入"行号+列号"，
换算只会在异常渲染处出现一次。现在就加行号只会增加没人用的字段。

**删掉会怎样？**
`Lexer` 只能返回 `List<String>`，位置信息彻底丢失，所有错误退回"不知道哪里错"。

### 5.6 `Lexer`：逐字符扫描，最长匹配

**206 行，`source` + `position` 两个字段。**

主循环非常短：

```java
public List<Token> tokenize() {
    List<Token> tokens = new ArrayList<>();
    while (position < source.length()) {
        char current = source.charAt(position);
        if (Character.isWhitespace(current)) { position++; continue; }
        int start = position;
        if (Character.isDigit(current))            { tokens.add(readNumber(start)); }
        else if (isIdentifierStart(current))       { tokens.add(readIdentifier(start)); }
        else                                       { tokens.add(readSymbol(start)); }
    }
    tokens.add(new Token(TokenType.END_OF_INPUT, "", source.length()));
    return List.copyOf(tokens);
}
```

**最长匹配（maximal munch）体现在 `readSymbol`：**

```java
case '>':
    position++;
    if (match('=')) { return new Token(TokenType.GREATER_EQUAL, ">=", start); }
    return new Token(TokenType.GREATER, ">", start);
```

先吃掉 `>`，再看下一个是不是 `=`。是就产出 `GREATER_EQUAL`，
不是就产出 `GREATER`。**语法层永远拿到完整运算符** —— 这是 I3 的根治。

同样的模式用于 `<=`、`==`、`!=`、`&&`、`||`。

**三个"单字符运算符"为什么不放行，而是直接报错：**

```java
case '=':
    position++;
    if (match('=')) { return new Token(TokenType.EQUAL, "==", start); }
    throw new ExpressionException("单个 '=' 不是比较运算符，判断相等请写 '=='", source, start);

case '&':
    position++;
    if (match('&')) { return new Token(TokenType.AND, "&&", start); }
    throw new ExpressionException("单个 '&' 不是逻辑运算符，逻辑与请写 '&&'", source, start);
```

因为 `age = 18` 和 `age = = 18`、`a & b` 都是**极常见的手误**，
而报错消息直接给出正确写法。Demo 5 的第二条就是它：

```text
· age = 18（单个等号）
  单个 '=' 不是比较运算符，判断相等请写 '=='
    规则: age = 18
              ^
```

**数字为什么不允许前导正负号？**

```java
private Token readNumber(int start) {
    while (position < source.length() && Character.isDigit(source.charAt(position))) {
        position++;
    }
    return new Token(TokenType.NUMBER, source.substring(start, position), start);
}
```

只吃数字。`-5` 会被切成 `MINUS` + `NUMBER`，由 `Parser.unary()` 当作一元负号。
理由见 4.5：负号和减号是同一个字符，只有语法层知道它是几元的。

**标识符为什么要求"字母或下划线开头"？**

```java
private static boolean isIdentifierStart(char ch) { return Character.isLetter(ch) || ch == '_'; }
private static boolean isIdentifierPart(char ch)  { return Character.isLetterOrDigit(ch) || ch == '_'; }
```

如果允许数字开头，`10abc` 会被读成一个**名字叫 `10abc` 的变量**，
而不是"数字 10 后面跟了垃圾 `abc`"。前者会变成"未定义变量"错误，
后者才是真正的问题（Demo 8）。**词法层的边界划分决定了错误的可读性。**

**`List.copyOf` 为什么必须？**
词法结果是解析器唯一的输入。一旦被外部改动，同一个 `Parser`
两次 `parse()` 可能得到不同结果。`List.copyOf` 让这份"输入快照"不可篡改。

**删掉会怎样？**
`Parser` 直接在原始字符串上 `split`/`contains`，
`>=` 误判（I3）、`*` 不识别（I7）、位置丢失（I4/I8）全部复现。

### 5.7 `Parser`：九层优先级，一个方法一层

**286 行，是最大的文件，但结构最规整。**

字段只有四个：

```java
public static final int MAX_NESTING = 200;
private final String source;
private final List<Token> tokens;
private int index;
private int nesting;
```

构造器**立刻做词法**：

```java
public Parser(String source) {
    if (source == null) { throw new IllegalArgumentException("source 不能为 null"); }
    this.source = source;
    this.tokens = new Lexer(source).tokenize();
    this.index = 0;
    this.nesting = 0;
}
```

为什么在构造器里做？因为"词法错误"和"语法错误"的抛出时机因此分开：
`new Parser("@")` 立刻抛词法错，`new Parser("10 +")` 要等 `.parse()` 才抛语法错。
这个区分在使用时有用（词法错意味着规则文本里有非法字符，
语法错意味着字符都合法但组合不对）。

**九个方法构成九层优先级，从低到高：**

```text
parse()         -> or()              入口 + 末尾检查
or()            -> and()              ||
and()           -> equality()        &&
equality()      -> comparison()      == !=
comparison()    -> additive()        > >= < <=
additive()      -> multiplicative()  + -
multiplicative()-> unary()           * /
unary()         -> unary() / primary()  - !
primary()       -> NUMBER / IDENTIFIER / ( expression )
```

**每个二元方法都是同一个形状：**

```java
private Expression additive() {
    Expression left = multiplicative();                       // 先拿一个高优先级的
    while (check(TokenType.PLUS) || check(TokenType.MINUS)) { // 只要还是同层运算符
        Token token = advance();
        ArithmeticExpression.Operator operator = token.type() == TokenType.PLUS
                ? ArithmeticExpression.Operator.ADD
                : ArithmeticExpression.Operator.SUBTRACT;
        left = new ArithmeticExpression(operator, left, multiplicative());  // 往左上生长
    }
    return left;
}
```

三行要点：

1. **`left` 反复被替换** —— 这就是左结合（4.6）；
2. **`multiplicative()` 每次重新调用** —— 右边永远先按更高优先级成型；
3. **`while` 循环不递归** —— 同层任意多项只占一层栈。

**`unary()` 的两个分支：**

```java
private Expression unary() {
    if (check(TokenType.MINUS) || check(TokenType.NOT)) {
        Token token = advance();
        UnaryExpression.Operator operator = token.type() == TokenType.MINUS
                ? UnaryExpression.Operator.NEGATE
                : UnaryExpression.Operator.NOT;
        guardNesting(token);
        nesting++;
        try {
            return new UnaryExpression(operator, unary());    // 递归：--5、!!x 都合法
        } finally {
            nesting--;
        }
    }
    return primary();
}
```

注意 `finally { nesting--; }`：即使递归里抛异常，计数也要恢复。
这是 `try/finally` 的标准用法 —— **不用它，一次失败会让同一个 `Parser` 实例的计数永久偏大**。

**`primary()` 三种情况 + 两种错误：**

```java
if (check(TokenType.NUMBER))     { ... Long.parseLong ... }
if (check(TokenType.IDENTIFIER)) { return new VariableExpression(token.text(), token.position()); }
if (check(TokenType.LEFT_PAREN)) { ... or() ... expect(RIGHT_PAREN) ... }

if (token.isEnd()) { throw error(token, "表达式在此处意外结束，缺少操作数"); }
throw error(token, "这里需要一个数字、变量名或左括号，实际是 " + token.display());
```

数字分支里还有一个 `try/catch`：

```java
try {
    return new NumberExpression(Long.parseLong(token.text()));
} catch (NumberFormatException e) {
    throw error(token, "数字超出 long 范围: " + token.text());
}
```

**为什么不用 `Integer.parseInt`？** 因为规则里的阈值可能是金额（分）、
时间戳（毫秒），`int` 装不下。`long` 是规则引擎的合理默认。

**`guardNesting` 的两处调用：**

```java
private void guardNesting(Token token) {
    if (nesting + 1 > MAX_NESTING) {
        throw error(token, "表达式嵌套层数超过上限 " + MAX_NESTING + "，请拆分规则");
    }
}
```

括号（`primary`）和一元（`unary`）各调一次 —— 因为它们都会引起递归。
`or/and/equality/comparison/additive/multiplicative` 用的是 `while`，不压栈，不需要计数。

Demo 10 实测边界：

```text
201 层括号 -> 表达式嵌套层数超过上限 200，请拆分规则
200 层括号 -> 1（边界值仍然可用）
```

**`advance()` 为什么对 END 不前进？**

```java
private Token advance() {
    Token token = tokens.get(index);
    if (!token.isEnd()) { index++; }
    return token;
}
```

如果无条件 `index++`，两次 `advance()` 越过末尾后 `peek()` 就 `IndexOutOfBounds`。
停在 END 上，语法层的所有错误检查都能安全地读 `peek()`。

**`error` 方法为什么返回异常而不是抛？**

```java
private ExpressionException error(Token token, String message) {
    return new ExpressionException(message, source, token.position());
}
```

调用处写 `throw error(...)`，语义上是"在出错的位置构造异常"。
如果改成在 `error` 里 `throw`，编译器不知道它一定抛，
`primary()` 结尾就必须写 `return null` 或者 `throw new IllegalStateException` 来收尾。
**返回异常是让"抛"这个动作留在调用点**，代码更好读。

**删掉会怎样？**
只剩"手工 `new AndExpression(...)`"，规则文本完全绕过 ——
这正是原项目 `Client` 的做法（探针 I3/I7 的根源）。

---
### 5.8 `NumberExpression`：34 行，文件里最短的一个

```java
public record NumberExpression(long value) implements Expression {

    @Override
    public Value evaluate(Context context) {
        Expression.requireContext(context);
        return Value.of(value);
    }

    @Override
    public String describe() {
        return Long.toString(value);
    }
}
```

**它是递归的终点。** 整棵树里唯一不调用任何子节点的节点之一（另一个是 `VariableExpression`）。

**它真的需要 `context` 吗？不需要，但签名必须带着。**

这是组合的代价：父节点拿到的字段类型永远是 `Expression`，
不能为叶子特化一个"不带参数"的方法。参数被忽略是正常的 ——
但**第一行的 `requireContext(context)` 不能省**：
它守住了接口的承诺（"传进来的上下文一定非 null"），
让 `NumberExpression.evaluate(null)` 抛出的是
`IllegalArgumentException: context 不能为 null`，而不是在后面某处冒出 NPE。

**为什么单独一个 record，而不是在 `ArithmeticExpression` 里用 `Long leftLiteral`？**
因为那会引出"左操作数是字面量还是子树"的两套分支。
一旦有了两套，`describe`、`evaluate`、`Parser` 都要各写两遍。
每多一个"可选的形态"，分支数翻倍 —— 这是解释器模式要消灭的东西。

**31 行 javadoc + 3 行代码**，注释比代码长，但每一段都在回答"为什么"。

### 5.9 `VariableExpression`：带位置的变量

**57 行，字段是 `(String name, int position)`。**

```java
public record VariableExpression(String name, int position) implements Expression {

    public VariableExpression {                          // 紧凑构造器
        if (name == null)   { throw new IllegalArgumentException("变量名不能为 null"); }
        if (name.isBlank()) { throw new IllegalArgumentException("变量名不能为空白字符串"); }
        if (position < 0)   { throw new IllegalArgumentException("position 不能为负，实际 " + position); }
    }

    @Override
    public Value evaluate(Context context) {
        return Value.of(Expression.requireContext(context).require(name, position));
    }

    @Override
    public String describe() {
        return name;
    }
}
```

**它是整棵树唯一和外部数据发生关系的节点。**

规则文本可以写死（`age > 18` 永远是这个形状），
但 `age` 到底是 25 还是 10 由 `Context` 决定。
**换一份上下文就能复用同一棵树** —— 这是规则引擎的业务价值所在
（Demo 1：解析一次、求值三次）。

**为什么要存 `position`？**

因为最常见的错误是变量名拼错。报错时要指到具体位置：

```text
未定义的变量 'AGE'（已定义: age, score）
  规则: AGE > 18
        ^
```

`position` 在解析时由 `Parser.primary` 从 `Token` 里取出传入：

```java
return new VariableExpression(token.text(), token.position());
```

**为什么 `position` 会影响 `equals`？**

`record` 的 `equals` 会把所有字段算进去，所以
`new VariableExpression("age", 1)` 和 `new VariableExpression("age", 5)` **不相等**。
这是有意为之：两个来自不同位置的同名变量，报错时应该指向各自的位置，
不该被当成"相同对象"。如果将来要做子树去重（CSE），
需要单独写一个只比较 `name` 的方法，而不是改 `equals` ——
改 `equals` 会让"位置"这个诊断信息在去重时丢失。

**删掉会怎样？**
变量只能写死在节点里（`new GreaterThanExpression("age", 18)`），
规则文本里的 `age` 两个字就没人读了 —— 又回到原项目的硬编码形态。

### 5.10 `ArithmeticExpression`：`describe()` 的性能陷阱就在这

**132 行，`(Operator, Expression left, Expression right)`。**

```java
public record ArithmeticExpression(Operator operator, Expression left, Expression right)
        implements Expression {

    @Override
    public Value evaluate(Context context) {
        String role = "运算符 '" + operator.symbol() + "'";
        long leftValue = left.evaluate(context).asNumber(role + " 的左操作数");
        long rightValue = right.evaluate(context).asNumber(role + " 的右操作数");
        if (operator == Operator.DIVIDE && rightValue == 0) {
            throw new ExpressionException("除数为 0: " + describe(), null, 0);
        }
        try {
            return Value.of(operator.compute(leftValue, rightValue));
        } catch (ArithmeticException e) {
            throw new ExpressionException(
                    "整数溢出: " + leftValue + " " + operator.symbol() + " " + rightValue, null, 0);
        }
    }

    @Override
    public String describe() {
        return "(" + left.describe() + " " + operator.symbol() + " " + right.describe() + ")";
    }
}
```

**三个关键设计：**

**1）`role` 而不是 `describe()`（性能事故的修复）。**

第一版是这样写的：

```java
String where = describe();                       // 每个节点拼一次整棵子树
long leftValue = left.evaluate(context).asNumber(where + " 的左操作数");
```

左深链 50000 项时，字符拷贝总量约 1.25e9 —— 实测**进程 CPU 272 秒仍未结束**。
改成 `role = "运算符 '+'"`（几个字符）后，50000 项毫秒级完成。
完整的 `describe()` 只在真正抛异常（除零）时调用一次。

**这条经验值得单独记住**：诊断信息的拼接成本必须按"出错时才付"来设计，
不能按"每次调用都付"。否则日志/诊断代码会变成性能杀手。

**2）除零单独判断，给出完整表达式。**

`10 / 0` 在 Java 里抛 `ArithmeticException: / by zero`，
消息里既没有哪条规则，也没有中文说明。这里提前判断：

```text
除数为 0: (10 / 0)
```

`describe()` 在这里调用是**正确**的 —— 只有出错的那一次才付代价。

**3）`Math.*Exact` 让溢出变成异常。**

```java
ADD      -> Math.addExact(a, b)
SUBTRACT -> Math.subtractExact(a, b)
MULTIPLY -> Math.multiplyExact(a, b)
DIVIDE   -> a / b                  // 除零已被前置拦截
```

Demo 10：

```text
整数溢出: 9223372036854775807 + 1
```

如果不用 `Exact`，`Long.MAX_VALUE + 1` 会安静地变成 `-9223372036854775808`。
在规则引擎里，"库存扣成负数"和"抛出溢出异常"之间，必须选后者。

**`Operator` 枚举为什么把符号和计算放在一起？**

```java
public enum Operator {
    ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/");

    private final String symbol;
    Operator(String symbol) { this.symbol = symbol; }
    public String symbol() { return symbol; }

    long compute(long leftValue, long rightValue) {
        return switch (this) {
            case ADD      -> Math.addExact(leftValue, rightValue);
            case SUBTRACT -> Math.subtractExact(leftValue, rightValue);
            case MULTIPLY -> Math.multiplyExact(leftValue, rightValue);
            case DIVIDE   -> leftValue / rightValue;
        };
    }
}
```

如果让 `Parser` 自己 `switch` 一遍运算、`ArithmeticExpression` 再 `switch` 一遍，
两处 `switch` 会慢慢不同步。放在枚举里，"加一个运算符"只改一处
（外加 `TokenType`、`Lexer`、`Parser` 的识别逻辑，编译器会提示）。

### 5.11 `UnaryExpression`：一个子节点，两种运算符

**90 行，`(Operator, Expression operand)`。**

```java
@Override
public Value evaluate(Context context) {
    String role = "运算符 '" + operator.symbol() + "'";
    Value value = operand.evaluate(context);
    return switch (operator) {
        case NEGATE -> Value.of(negate(value.asNumber(role + " 的操作数"), role));
        case NOT    -> Value.of(!value.asBoolean(role + " 的操作数"));
    };
}
```

**`NEGATE` 需要额外的溢出保护：**

```java
private static long negate(long number, String role) {
    try {
        return Math.negateExact(number);
    } catch (ArithmeticException e) {
        throw new ExpressionException("整数溢出: " + role + " 操作数 " + number + "（取负后超出 long 范围）", null, 0);
    }
}
```

`Long.MIN_VALUE` 取负会溢出（`-(-2^63)` 超出 `long` 上限）。
加法/减法/乘法的溢出由 `Operator.compute` 的 `Exact` 方法拦住，
取负没有"二元运算符"可用，只能单独 `try`。

**为什么 `NOT` 和 `NEGATE` 合并成一个类？**

因为它们**结构完全一样**：一个前置符号 + 一个操作数。
合并后 `describe()` 只需要处理一种形状：

```java
return "(" + operator.symbol() + operand.describe() + ")";
```

`-5` → `(-5)`，`!ok` → `(!ok)`。
如果拆成两个类，`describe`、`Parser`、`evaluate` 的骨架会有两份几乎一样的代码。

**代价**：`evaluate` 必须按 `operator` 分支，且两个分支要求的值类型不同
（一个要数字、一个要布尔）。**而这正好是类型检查点**：
`!5` 报「需要布尔值，实际是数字 5」，`-true` 报「需要数字，实际是布尔值 true」。
如果拆成两个类，"类型错"会变成两处独立的判断，容易漏掉一处。

### 5.12 `ComparisonExpression`：六个运算符，一个类

**111 行，结构与 `ArithmeticExpression` 完全一样。**

```java
@Override
public Value evaluate(Context context) {
    String role = "运算符 '" + operator.symbol() + "'";
    long leftValue = left.evaluate(context).asNumber(role + " 的左操作数");
    long rightValue = right.evaluate(context).asNumber(role + " 的右操作数");
    return Value.of(operator.test(leftValue, rightValue));
}
```

枚举里放六种比较：

```java
boolean test(long leftValue, long rightValue) {
    return switch (this) {
        case GREATER       -> leftValue > rightValue;
        case GREATER_EQUAL -> leftValue >= rightValue;
        case LESS          -> leftValue < rightValue;
        case LESS_EQUAL    -> leftValue <= rightValue;
        case EQUAL         -> leftValue == rightValue;
        case NOT_EQUAL     -> leftValue != rightValue;
    };
}
```

**它是"算术"和"逻辑"的接缝。**

```text
(age + 5) * 2 > 50 && age < 30
 └─ 算术子树 ─┘ └比较┘ └── 逻辑 ──┘
```

左边的算术子树通过 `asNumber` 提供数字，本节点产出 `Value.Bool`，
上面的逻辑节点通过 `asBoolean` 消费它。
**每一层都用同一个 `Value` 传递结果，所以三种类型的节点可以自由嵌套。**

**为什么不支持字符串比较？**

因为 `Value` 只有两种类型。要加字符串，得：
给 `Value` 加一个 `Str` 分支、给 `Lexer` 加字符串字面量（引号、转义）、
给 `<` 定义"字典序"语义、给 `==` 定义"值相等还是引用相等"。
**那是另一个需求，不该顺手塞进这个示例。** 这正是"不为套用模式而过度设计"。

如果真要做，改动是可控的：`Value` 加分支（编译器会提示所有 `switch` 补 case）、
`ComparisonExpression` 的 `test` 改成按类型分派、
`Context` 的值类型从 `Long` 换成 `Object` 或一个 `Value`。四处，每处都有编译错误兜底。

### 5.13 `LogicalExpression`：唯一控制求值顺序的节点

**88 行，`(Operator, Expression left, Expression right)`。**

```java
@Override
public Value evaluate(Context context) {
    String role = "运算符 '" + operator.symbol() + "'";
    boolean leftValue = left.evaluate(context).asBoolean(role + " 的左操作数");
    if (operator == Operator.AND && !leftValue) {
        return Value.of(false);
    }
    if (operator == Operator.OR && leftValue) {
        return Value.of(true);
    }
    boolean rightValue = right.evaluate(context).asBoolean(role + " 的右操作数");
    return Value.of(rightValue);
}
```

**它是整棵树里唯一"可能不评估右子树"的节点。**

算术和比较都是"两个子节点都要算"，逻辑不是。
这个差异**不能**用"先算两个再合并"的模板实现 ——
那会在 `count > 0 && total / count > 10` 且 `count=0` 时抛除零。

Demo 10 的实测：

```text
短路保护除零: 「count > 0 && total / count > 10」
  count=0, total=100 -> false（右侧未求值，没有抛除零）
  count=2, total=100 -> true
```

**短路是语义，不是优化。** 规则里 99% 的"先判断再计算"都依赖它。

**代价（必须承认）：右侧的未定义变量可能永远不被检查。**

```text
false && AGE > 18   ->  false，不报错
```

这不是 bug，是短路的定义。选择与 Java 的 `&&` 保持一致 ——
一致性比"更严格"更重要，否则用户会因为条件顺序不同而看到不同的报错。
这一条写在 javadoc 里，作为显式约定。

**为什么不用 `&` / `|`（非短路）？**
因为规则语言里，"用前一个条件保护后一个条件"是刚需。
Java 里 `&&` 与 `&` 的取舍是"性能 vs 副作用"，
规则语言里是"能不能写出安全的规则"—— 后者没有商量余地。

### 5.14 `ExpressionException`：三类信息 + 窗口截断

**122 行，`RuntimeException` 的子类。**

```java
public ExpressionException(String message, String source, int position) {
    super(message);
    this.source = source;
    this.position = Math.max(position, 0);       // 负数当成 0
}
```

**为什么是 `RuntimeException` 而不是受检异常？**

因为它会在 `evaluate` 的深层递归里抛出。如果用受检异常，
每个 `evaluate` 都要写 `throws ExpressionException`，
而它们其实无法处理（只能往上抛）。
`RuntimeException` 让"解析/求值失败"沿着调用链自然上浮，
由最外层的 `Client` 或业务代码统一 `catch`。

**`render()` 与 `getMessage()` 分开（见 4.12）：**

```text
getMessage() -> 未定义的变量 'AGE'（已定义: age, score）
render()     -> 未定义的变量 'AGE'（已定义: age, score）
                  规则: AGE > 18
                        ^
```

**`render()` 的三种形态：**

| 情况 | 输出 |
|---|---|
| `source == null` 且 `position == 0` | 只有消息（求值阶段的除零、溢出） |
| `source == null` 且 `position > 0` | 消息 + `位置: 第 N 个字符`（变量未定义） |
| `source != null` | 消息 + `规则: ...` + `^` 指针（词法/语法错误） |

**窗口截断的边界计算值得逐行看：**

```java
int caretIndex = Math.min(position, source.length());
if (source.length() <= MAX_SOURCE_CHARS) {
    shownSource = source;
    shownCaret = caretIndex;
} else {
    int start = Math.max(0, caretIndex - CARET_CONTEXT);
    int end = Math.min(source.length(), start + MAX_SOURCE_CHARS);
    start = Math.max(0, end - MAX_SOURCE_CHARS);         // 靠右时向左补齐
    String prefix = start > 0 ? "..." : "";
    String suffix = end < source.length() ? "..." : "";
    shownSource = prefix + source.substring(start, end) + suffix;
    shownCaret = prefix.length() + (caretIndex - start);
}
```

三个细节：

1. **`start` 算两遍**：先按"出错位置左侧留 30 字符"算，
   再按"窗口最多 100 字符"反向补齐。不补的话，靠近末尾的错误会显示不到 100 字符。
2. **`shownCaret = prefix.length() + (caretIndex - start)`**：
   `^` 的空格数必须算上 `...` 这 3 个字符，否则指针会偏 3 格。
3. **`...` 用 3 个 ASCII 点**：省略号字符 `…` 是宽字符，会让终端显示宽度和字符数不一致。

**换行固定用 `"\n"` 而不是 `System.lineSeparator()`**：

README 里的哈希校验依赖"逐字节可复现"。Windows 的 `\r\n` 与 Linux 的 `\n`
会让同一份代码在两个平台产生不同字节数。固定 `\n` 牺牲了平台习惯，
换来的是**可复现** —— 对教学项目来说后者更重要。

### 5.15 `ExpressionEngine`：四个静态方法

**81 行，纯门面。**

```java
public final class ExpressionEngine {

    private ExpressionEngine() {
        throw new AssertionError("工具类不允许实例化");
    }

    public static Expression parse(String source)                    { return Parser.parse(source); }
    public static Value evaluate(String source, Context context)     { return Parser.parse(source).evaluate(Expression.requireContext(context)); }
    public static boolean test(String source, Context context)       { return Parser.parse(source).test(context); }
    public static String describe(String source)                     { return Parser.parse(source).describe(); }
}
```

**四个方法的适用场景：**

| 方法 | 什么时候用 | 成本 |
|---|---|---|
| `parse` | 规则要反复求值（缓存树） | 解析一次，之后只有求值 |
| `evaluate` | 只求一次，且结果可能是数字 | 每次都要重新解析 |
| `test` | 规则引擎的典型调用 | 每次都要重新解析，且失败时要拼 `describe()` |
| `describe` | 验证优先级 / 打印日志 | 每次都要重新解析 |

**注意 `evaluate` 和 `test` 每次都重新 `parse`。** 这是刻意的：
本项目不引入缓存，避免"缓存键怎么设计、失效怎么办"这些与解释器模式无关的问题。
**如果要做缓存**，正确的位置是调用方：

```java
Map<String, Expression> cache = new ConcurrentHashMap<>();
Expression tree = cache.computeIfAbsent(rule, ExpressionEngine::parse);
```

树是不可变的（所有节点都是 record），可以安全地在多线程间共享 ——
这也是 4.4 坚持"上下文与树都不可变"的收益之一。

**为什么私有构造器抛 `AssertionError`？**
反射可以绕过 `private` 创建对象。抛 `AssertionError` 让这种情况下立刻失败。
这是 JDK 工具类的惯例（`java.lang.reflect.Modifier` 等）。

### 5.16 `Client`：十段 Demo，每段对应一个探针

**266 行，十个 `private static void demoXxx()` + 三个工具方法。**

```java
public static void main(String[] args) throws Exception {
    demo1TextToTreeAndReuse();
    demo2ThreeConditions();
    demo3SingleCondition();
    demo4ComparisonOperators();
    demo5ErrorPosition();
    demo6UnknownVariable();
    demo7PrecedenceAndAssociativity();
    demo8TrailingGarbage();
    demo9LongChain();
    demo10SafetyNet();
}
```

**为什么 Demo 要按"原项目问题 → 现在的行为"组织？**

因为设计模式最容易被讲成"多写了几个类"。
只有把同一段输入放到两套实现下对比，才能看清那些类换来了什么：
一次明确的报错、一个不会丢的条件、一条可复现的结果。
所以每个 demo 的末尾几乎都有一行 `原项目：...`。

**十段与探针的对应关系：**

| Demo | 验证什么 | 对应探针 | 用到的 API |
|---|---|---|---|
| 1 | 解析一次、求值三次，上下文互不影响 | —— | `parse` + `test` |
| 2 | 三个条件不再丢一个 | I1a | `describe` + `test` |
| 3 | 单条件不再 NPE | I2 | `test` / `evaluate` |
| 4 | 六种比较 + 边界值 | I3 | `test` |
| 5 | 五种错误各自的 caret | I4 / I8 | `parse` + `expectFailure` |
| 6 | 未定义变量 + 大小写 | I5 / I6 | `test` + `expectFailure` |
| 7 | 优先级与结合性（看树） | I10 | `parse` + `describe` + `evaluate` |
| 8 | 尾部垃圾不再静默 | I7 | `evaluate` |
| 9 | 长链的栈上限（诚实测量） | I9 | `parse` + `evaluate` + 大栈线程 |
| 10 | 类型、除零、溢出、短路、嵌套上限 | —— | `evaluate` / `test` / `parse` |

**三个工具方法：**

```java
private static void expectFailure(String label, Runnable action) {
    System.out.println("  · " + label);
    try {
        action.run();
        System.out.println("    -> 没有抛异常（与预期不符）");
    } catch (ExpressionException e) {
        for (String line : e.render().split("\n")) {
            System.out.println("    " + line);
        }
    } catch (IllegalArgumentException e) {
        System.out.println("    -> IllegalArgumentException: " + e.getMessage());
    }
}
```

`expectFailure` 有两个"意料之外"的处理：

1. **没抛异常时打印"与预期不符"** —— 测试没有静默通过。
   如果某个 `Demo 5` 的用例意外成功了，输出里会立刻出现这一行。
2. **`IllegalArgumentException` 也接住** —— `evaluate(null)` 这类用法错误
   和规则错误要区分开，不能混在同一个分支里。

`split("\n")` 加逐行 `println` 而不是直接打印：为了让每一行都带 4 个空格缩进，
和 Demo 的其他输出对齐。直接 `println(e.render())` 只有第一行有缩进。

```java
private static String pad(String text, int width) {
    StringBuilder builder = new StringBuilder(text);
    while (builder.length() < width) { builder.append(' '); }
    return builder.toString();
}
```

**为什么用 `pad` 而不是 `String.format("%-14s", text)`？**
因为中文字符的显示宽度是 2，但 `String.length()` 算 1。
`format` 按字符数补齐，中文规则（如 `重量 > 18`）会对不齐；
`pad` 按实际字符数补齐，至少保证**同一批输出的列位置一致**
（都是 ASCII 运算符时完美对齐，含中文时略有偏移，但比 `format` 可控）。

```java
private static void section(String title) {
    System.out.println();
    System.out.println("================================================================");
    System.out.println(title);
    System.out.println("================================================================");
}
```

64 个 `=`，每段 Demo 之间空一行。**固定宽度是为了让输出可以逐字节比较** ——
README 里的 134 行哈希就是用这个格式算出来的。

**`demo9` 的 `final Expression lastTree = biggest` 为什么要这一行？**

```java
Expression biggest = null;
for (int size : sizes) { ... biggest = tree; }
final Expression lastTree = biggest;

Thread thread = new Thread(null, () -> {
    result.set("结果 " + lastTree.evaluate(Context.empty()).display());
}, "big-stack-demo", 256L * 1024 * 1024);
```

Java 的 lambda 只能捕获 **effectively final** 的局部变量。
`biggest` 在循环里被反复赋值，不是 effectively final。
引入 `lastTree` 一次赋值，lambda 就能捕获它。

`new Thread(ThreadGroup, Runnable, String, long stackSize)` 这个构造器的第四个参数
是**栈大小**（字节）。`256L * 1024 * 1024` = 256 MB。
Demo 9 用它证明"50000 项算得对，只是默认栈不够"：

```text
50000 项换 256 MB 栈的线程 -> 结果 50000
```

**为什么不用 `-Xss` 参数？**
因为 Demo 要能在"默认 JVM 参数"下复现。用线程的 `stackSize` 参数，
影响范围只有那一个线程，其他 Demo 仍在默认栈下运行 ——
这样"默认栈溢出、大栈成功"的对比才能在**同一次运行**里出现。

---

## 6. 执行流程：十段 Demo 的真实运行结果

这一节把 134 行输出逐段拆开：每段先说清"入口方法做了什么、创建了哪些对象、调用了哪些方法"，
再贴逐字输出。所有输出都来自实际运行（哈希 `FD30D6AE…`），不是手写示意。

**运行命令**（Windows + JDK 25）：

```powershell
java -Dstdout.encoding=UTF-8 -cp out com.patterns.behavioral.interpreter.Client
```

`-Dstdout.encoding=UTF-8` 是必须的：原项目没加这个参数，
输出是 GBK；本项目统一用 UTF-8，才能和 README 里的哈希对齐。

### 6.1 Demo 1：解析一次，求值三次

**入口**：`demo1TextToTreeAndReuse()`

**做什么**：

```java
String rule = "age > 18 && score < 500";
Expression tree = ExpressionEngine.parse(rule);          // ① 解析一次

Context minor = Context.empty().with("age", 17).with("score", 300);          // ②
Context adult = Context.empty().with("age", 25).with("score", 300);
Context adultHighScore = Context.empty().with("age", 25).with("score", 1000);
System.out.println("  " + minor.describe() + " -> " + tree.test(minor));    // ③ 求值三次
```

**对象创建顺序**：

```text
① new Lexer("age > 18 && score < 500").tokenize()
   -> [IDENTIFIER(age,0), GREATER(4), NUMBER(18,6), AND(9), IDENTIFIER(score,12), LESS(18), NUMBER(500,20), END(23)]
② new Parser(...).parse()
   -> LogicalExpression(AND,
        ComparisonExpression(GREATER, VariableExpression("age",0), NumberExpression(18)),
        ComparisonExpression(LESS,    VariableExpression("score",12), NumberExpression(500)))
③ Context.empty()  -> 空 Map
   .with("age", 17) -> 新 Map {age=17}
   .with("score", 300) -> 新 Map {age=17, score=300}
④ tree.test(minor)
   -> LogicalExpression.evaluate
      -> 左 ComparisonExpression.evaluate
         -> VariableExpression.evaluate -> context.require("age", 0) -> 17
         -> NumberExpression.evaluate -> 18
         -> 17 > 18 -> Value.Bool(false)
      -> 短路：AND 且左侧 false -> 直接返回 false（右侧不求值）
```

**真实输出**（第 1–11 行）：

```text

================================================================
Demo 1 文本进、树出来，同一棵树换上下文复用
================================================================
  规则文本: age > 18 && score < 500
  语法树  : ((age > 18) && (score < 500))
  age=17, score=300 -> false
  age=25, score=300 -> true
  age=25, score=1000 -> false
  只解析一次、求值三次；Context 不可变，三次互不影响
  原项目：手工 new AndExpression(new GreaterThanExpression(...), ...)，文本不参与
```

**三个关键点：**

1. **第一行是空行** —— `section()` 的第一个 `println()` 没参数。
   这一段空行让 10 段 Demo 之间有呼吸感，也是哈希的一部分。
2. **`语法树` 那一行是 `describe()` 的输出**：`((age > 18) && (score < 500))`。
   全括号形式，一眼能看出 `&&` 的两个操作数各是一个比较表达式。
3. **第三次求值 `false`，第二次 `true`** —— 唯一的差别是 `score` 从 300 变成 1000。
   **树没有变，变的只是上下文。** 这证明 `Context` 的不可变性与树的可复用性：

```text
同一棵 tree ──┬── minor(17,300)      -> false
              ├── adult(25,300)      -> true
              └── adultHigh(25,1000) -> false
```

如果 `Context.with` 是可变方法，三次 `println` 里前两次会打印出第三次的数据 ——
因为它们持有同一个对象。

### 6.2 Demo 2：三个条件不再丢一个

**入口**：`demo2ThreeConditions()`

**做什么**：

```java
String rule = "age > 18 && score < 500 && age < 30";
System.out.println("  语法树  : " + ExpressionEngine.describe(rule));
Context failed = Context.empty().with("age", 25).with("score", 1000);
System.out.println("  " + failed.describe() + " -> " + ExpressionEngine.test(rule, failed));
```

**树是怎么长出来的**（`Parser.and()` 的循环）：

```text
第一次循环：left = (age > 18)                 right = (score < 500)  -> left = ((age > 18) && (score < 500))
第二次循环：left = ((age > 18) && (score < 500))  right = (age < 30) -> left = (((age > 18) && (score < 500)) && (age < 30))
循环结束：返回整个左深树
```

**关键：循环不限制条件数量。** 原项目用两个变量（`left`/`right`）装条件，
第三条必然覆盖第二条；这里 `left` 反复被替换，装多少条都行。

**求值顺序**（短路生效）：

```text
((age > 18) && (score < 500)) && (age < 30)
  └── true ──┘    └── false ──┘
       └──────── false ────────┘   <- 左侧已 false，右侧 (age < 30) 根本没求值
  -> false
```

**真实输出**（第 13–22 行）：

```text

================================================================
Demo 2 三个条件不再丢一个（原项目探针 I1a：应 false 实 true）
================================================================
  规则文本: age > 18 && score < 500 && age < 30
  语法树  : (((age > 18) && (score < 500)) && (age < 30))
  age=25, score=1000 -> false
  期望 false：score=1000 不满足 score < 500
  原项目：第三条 age < 30 覆盖了 score < 500，只比较 age，错判为 true
  age=25, score=300 -> true
```

**对照原项目**：同样的输入，原项目输出 `true`（探针 I1a）。
差别的根源不是"哪个运算符写错了"，而是**原项目的树里根本没有 `score < 500` 这个节点**。
`describe()` 输出三层的括号结构，正好把这件事可视化：
如果原项目也能 `describe()`，它会输出 `((age > 18) && (age < 30))` ——
一眼就能看出少了一层。

**这也是"为什么需要 `describe()`"的最好例证**：光看 `false`，你无法区分
"树对了、数据不满足"和"树错了、碰巧算出 false"。

### 6.3 Demo 3：单条件不再空指针

**入口**：`demo3SingleCondition()`

**做什么**：

```java
Context context = Context.empty().with("age", 25).with("score", 300);
System.out.println("  age > 18    -> " + ExpressionEngine.test("age > 18", context));
System.out.println("  score < 500 -> " + ExpressionEngine.test("score < 500", context));
System.out.println("  age + 5     -> " + ExpressionEngine.evaluate("age + 5", context).display());
```

**三条规则各自得到的树**：

```text
"age > 18"    -> ComparisonExpression(GREATER, Variable(age), Number(18))
"score < 500" -> ComparisonExpression(LESS,    Variable(score), Number(500))
"age + 5"     -> ArithmeticExpression(ADD,     Variable(age), Number(5))
```

**没有任何 `AndExpression`。** 这正是修复 I2 的方式：
原项目 `parse` 无条件返回 `new AndExpression(left, right)`，
所以单条件时另一侧是 `null`；本项目 `parse` 返回的就是 `primary` 建出来的那棵树，
**规则的形状决定树的形状**，没有"额外的包装节点"。

**真实输出**（第 24–29 行）：

```text

================================================================
Demo 3 单条件不再空指针（原项目探针 I2）
================================================================
  age > 18    -> true
  score < 500 -> true
  age + 5     -> 30
  原项目：单独的 age > 18 让 AndExpression 的 right 保持 null，求值即 NPE
```

**第三条 `age + 5 -> 30` 值得注意**：它证明同一套语法既支持"布尔表达式"
也支持"算术表达式"。`test()` 只能用于布尔，`evaluate()` 两者都行 ——
`30` 是 `Value.Num` 的 `display()`，不是布尔转换的结果。
如果对 `age + 5` 调 `test()`，会得到：

```text
运算符 '+' 需要布尔值，实际是数字 30
```

（`test()` 内部 `asBoolean(describe())`，`where` 是整棵树的描述。）

### 6.4 Demo 4：六种比较 + 边界值

**入口**：`demo4ComparisonOperators()`

**做什么**：

```java
Context context = Context.empty().with("age", 18).with("score", 500).with("weight", 61);
String[] rules = { "age > 18", "age >= 18", "score < 500", "score <= 500", "age == 18", "age != 18" };
for (String rule : rules) {
    System.out.println("  " + pad(rule, 14) + " -> " + ExpressionEngine.test(rule, context));
}
Context slight = Context.empty().with("weight", 60);
// weight > 60 -> false，weight >= 60 -> true
```

**六条规则对应的 token 序列**（关键在 `>=` 和 `<=`）：

```text
"age >= 18"   -> [IDENTIFIER(age,0), GREATER_EQUAL(4), NUMBER(18,7), END]
"score <= 500"-> [IDENTIFIER(score,0), LESS_EQUAL(6), NUMBER(500,9), END]
"age == 18"   -> [IDENTIFIER(age,0), EQUAL(4), NUMBER(18,7), END]
```

`GREATER_EQUAL` 是 `Lexer` 在 `case '>'` 里 `match('=')` 成功后产出的。
**语法层看到的永远是一个完整 token**，不存在"半个运算符"。

**真实输出**（第 31–41 行）：

```text

================================================================
Demo 4 六种比较运算符，包括原项目误判的 >=（探针 I3）
================================================================
  age > 18       -> false
  age >= 18      -> true
  score < 500    -> false
  score <= 500   -> true
  age == 18      -> true
  age != 18      -> false
  原项目：age >= 18 被按 > 切开，数字部分剩下 = 18，抛 NumberFormatException
  边界值 weight=60：weight > 60 -> false，weight >= 60 -> true
```

**边界值那一行的意义**：

```text
weight = 60
weight > 60   -> false     严格大于
weight >= 60  -> true      包含等于
```

`>` 和 `>=` 在**刚好等于阈值**时结果不同。如果 `>=` 被误判成 `>`（原项目 I3），
这一行会变成 `false / false` —— 边界值一个也过不了。
用 `weight=60` 而不是 `weight=61`，就是为了让"等于"这个 case 显形。

**为什么 `age > 18` 用 `age=18` 测试得到 `false`？**
同一条规则用 `age=25`（Demo 3）会得到 `true`。
同一个表达式、不同的上下文 —— 再次证明求值只依赖上下文。

### 6.5 Demo 5：五种错误，五条消息，五个 `^`

**入口**：`demo5ErrorPosition()`

**做什么**：五次 `expectFailure`，每次传一个 `Runnable`：

```java
expectFailure("age > @18（非法字符）", () -> ExpressionEngine.parse("age > @18"));
expectFailure("age = 18（单个等号）", () -> ExpressionEngine.parse("age = 18"));
expectFailure("10 +（缺右操作数）", () -> ExpressionEngine.parse("10 +"));
expectFailure("10 + 5)（多右括号）", () -> ExpressionEngine.parse("10 + 5)"));
expectFailure("（空规则）", () -> ExpressionEngine.parse(""));
```

**五次错误分别在哪一层被拦下：**

| 输入 | 拦截层 | 抛出点 | position |
|---|---|---|---:|
| `age > @18` | 词法 | `Lexer.readSymbol` 的 `default` 分支 | 6（`@` 的位置） |
| `age = 18` | 词法 | `Lexer.readSymbol` 的 `case '='` | 4（`=` 的位置） |
| `10 +` | 语法 | `Parser.primary` 的 `token.isEnd()` 分支 | 4（字符串长度，END 的位置） |
| `10 + 5)` | 语法 | `Parser.parse` 的"多余的内容"检查 | 6（`)` 的位置） |
| `（空规则）` | 语法 | `Parser.parse` 开头的 `peek().isEnd()` | 0（END 的位置） |

**真实输出**（第 43–64 行）：

```text

================================================================
Demo 5 错误带位置，caret 指到你写错的那个字符（探针 I4/I8）
================================================================
  · age > @18（非法字符）
    无法识别的字符 '@'
      规则: age > @18
                  ^
  · age = 18（单个等号）
    单个 '=' 不是比较运算符，判断相等请写 '=='
      规则: age = 18
                ^
  · 10 +（缺右操作数）
    表达式在此处意外结束，缺少操作数
      规则: 10 +
                ^
  · 10 + 5)（多右括号）
    多余的内容 ')'，表达式应在此结束
      规则: 10 + 5)
                  ^
  · （空规则）
    表达式不能为空
  原项目：NumberFormatException: For input string: ""，不含规则原文也不含位置
```

**逐条核对位置：**

| 输入 | 出错字符 | 字符下标（position） | 显示效果 |
|---|---|---:|---|
| `age > @18` | `@` | 6 | `^` 落在 `@` 正下方 |
| `age = 18` | `=` | 4 | 落在 `=` 正下方 |
| `10 +` | 输入结束 | 4（字符串长度） | 落在 `+` 右侧一格的空白处 |
| `10 + 5)` | `)` | 6 | 落在 `)` 正下方 |

> **为什么看着是对齐的，字符数却对不上？**
> `render()` 的规则行前缀是 `"  规则: "`（2 空格 + "规则" + ": "），
> 而 `^` 行的前缀是 `"        "`（8 个空格）。
> 按**字符数**算，`"  规则: "` 只有 6 个字符；
> 按**显示宽度**算，"规则"两个汉字各占 2 列，合计 8 列 —— 正好等于 `^` 行的 8 个空格。
> 所以终端里 `^` 与出错字符视觉对齐，而字符串里 `^` 前面有 `8 + position` 个空格。
> **写断言时用 `position`，不要去数空格。**

**空规则为什么没有"规则:"行？**
`""` 的 `source` 是空串，`render()` 走 `source == null || source.isEmpty()` 分支，
只输出消息。**空字符串没有可指的位置**，画一个 `^` 在第 0 位反而是噪音。

**这五条消息的共同点：**

| 消息 | 说了"期望什么" | 说了"实际是什么" | 说了"在哪" |
|---|---|---|---|
| 无法识别的字符 '@' | 隐含（支持的符号集） | 是 `@` | `^` |
| 单个 '=' 不是比较运算符，判断相等请写 '==' | 是 `==` | 是 `=` | `^` |
| 表达式在此处意外结束，缺少操作数 | 缺操作数 | 输入结束 | `^` |
| 多余的内容 ')'，表达式应在此结束 | 应结束 | 是 `)` | `^` |
| 表达式不能为空 | 至少一个操作数 | 空 | ——（无位置可指） |

**每一条都能让用户自己修好** —— 这是"错误消息"和"异常堆栈"的区别。

---
### 6.6 Demo 6：未定义变量立刻报错

**入口**：`demo6UnknownVariable()`

**做什么**：

```java
Context context = Context.empty().with("age", 25).with("score", 300);
expectFailure("AGE > 18（大小写不一致）", () -> ExpressionEngine.test("AGE > 18", context));
expectFailure("weight > 60（变量不存在）", () -> ExpressionEngine.test("weight > 60", context));
```

**错误发生在哪一步**：

```text
parse("AGE > 18")    -> 成功（AGE 是合法的标识符）
  -> new VariableExpression("AGE", 0)
test(context)        -> evaluate
  -> VariableExpression.evaluate
     -> context.require("AGE", 0)
        -> variables.get("AGE") == null
        -> throw ExpressionException("未定义的变量 'AGE'（已定义: age, score）", null, 0)
```

**关键：解析阶段不报错。** `AGE` 在语法上完全合法（标识符），
只有到求值、真正去查上下文时，才知道这个名字存不存在。
这是正确的分层：**语法层不该知道业务字段名。**

**真实输出**（第 66–74 行）：

```text

================================================================
Demo 6 未定义变量立刻报错，并列出已定义变量（探针 I5/I6）
================================================================
  上下文: age=25, score=300
  · AGE > 18（大小写不一致）
    未定义的变量 'AGE'（已定义: age, score）
  · weight > 60（变量不存在）
    未定义的变量 'weight'（已定义: age, score）
  原项目：两种情况都静默返回 false，规则悄悄失效，没有任何日志
```

**为什么没有 `位置: 第 N 个字符` 这一行？**

因为变量在规则**开头**，`position = 0`，而 `render()` 的分支是：

```java
if (source == null || source.isEmpty()) {
    if (position > 0) {
        return getMessage() + "\n  位置: 第 " + position + " 个字符";
    }
    return getMessage();
}
```

`position > 0` 才追加位置行。变量在开头时位置信息没有增量价值（难道还会有疑问吗），
所以省掉。**如果规则是 `age > 18 && weight > 60`，`weight` 的 position 是 13，
输出就会多出 `位置: 第 13 个字符`。**

这是 `render()` 的一个边界设计：**只在位置能提供新信息时才显示位置。**
严格说这里可以改进（`position > 0` 改成"总是显示"），
但当前行为是确定的、可解释的，README 就按实际输出记录。

**「已定义: age, score」为什么这么重要？**

对比两种消息：

```text
未定义的变量 'AGE'                              <- 用户得回去翻代码
未定义的变量 'AGE'（已定义: age, score）        <- 一眼看出大小写不一致
```

规则引擎的用户是运营/风控，不是程序员。**把可选项摆出来，
比解释"什么是大小写敏感"有用一百倍。**

**探针 I5 和 I6 的区别在这里合流**：`AGE`（大小写）和 `weight`（不存在）
在本项目里走**同一条**错误路径 —— 因为 `Context` 用 `Map` 存变量名，
两者都是"查不到"。原项目却把它们分成"静默 false"和"静默 false"，
连区别都没有。

### 6.7 Demo 7：优先级与结合性

**入口**：`demo7PrecedenceAndAssociativity()`

**做什么**：

```java
Context context = Context.empty().with("age", 25);
String[] rules = { "10 + 5 * 2", "(10 + 5) * 2", "10 - 3 - 2", "2 * 3 + 4 * 5",
                   "100 / 7", "-5 + 3", "age + 5" };
for (String rule : rules) {
    Expression tree = ExpressionEngine.parse(rule);
    System.out.println("  " + pad(rule, 14) + " 树 " + pad(tree.describe(), 26)
            + " 值 " + tree.evaluate(context).display());
}
```

**七条规则各自验证什么：**

| 规则 | 树 | 验证点 |
|---|---|---|
| `10 + 5 * 2` | `(10 + (5 * 2))` | 乘法优先于加法 |
| `(10 + 5) * 2` | `((10 + 5) * 2)` | 括号能改变优先级 |
| `10 - 3 - 2` | `((10 - 3) - 2)` | **左结合**（右结合会得 9） |
| `2 * 3 + 4 * 5` | `((2 * 3) + (4 * 5))` | 两侧乘法各自先成型 |
| `100 / 7` | `(100 / 7)` | 整数除法，14 不是 14.28 |
| `-5 + 3` | `((-5) + 3)` | 一元负号优先于加法 |
| `age + 5` | `(age + 5)` | 变量参与算术 |

**`10 - 3 - 2` 是本节的核心。** 它同时验证两件事：

```text
如果 additive 写成递归：10 - (3 - 2) = 10 - 1 = 9     <- 错
本项目用 while 循环：  (10 - 3) - 2 = 7 - 2  = 5     <- 对
```

**加法看不出结合性差异**（`1+(2+3)` 和 `(1+2)+3` 都是 6），
所以必须用减法测。这也是为什么 Demo 里特意放了 `10 - 3 - 2` 而不是 `1 + 2 + 3`。

**真实输出**（第 76–87 行）：

```text

================================================================
Demo 7 优先级与结合性：看 describe()，而不是只看结果
================================================================
  10 + 5 * 2     树 (10 + (5 * 2))             值 20
  (10 + 5) * 2   树 ((10 + 5) * 2)             值 30
  10 - 3 - 2     树 ((10 - 3) - 2)             值 5
  2 * 3 + 4 * 5  树 ((2 * 3) + (4 * 5))        值 26
  100 / 7        树 (100 / 7)                  值 14
  -5 + 3         树 ((-5) + 3)                 值 -2
  age + 5        树 (age + 5)                  值 30
  10 - 3 - 2 = 5 说明减法左结合；若写成右结合会得 9
  原项目：只支持 +，10-5 算成 10，10/2 也算成 10（探针 I10）
```

**逐行核对求值：**

```text
10 + 5 * 2      = 10 + 10            = 20    ✓
(10 + 5) * 2    = 15 * 2             = 30    ✓
10 - 3 - 2      = 7 - 2              = 5     ✓
2 * 3 + 4 * 5   = 6 + 20             = 26    ✓
100 / 7         = 14（整数除法）      = 14    ✓
-5 + 3          = (-5) + 3           = -2    ✓
age + 5         = 25 + 5             = 30    ✓
```

**注意 `100 / 7 = 14` 而不是 14.2857。** 这是整数除法的定义
（`long / long` 截断小数）。规则引擎里这是正确选择：
年龄、库存、次数都不能有小数。**如果业务需要小数，应该用"分"为单位存整数**
（金额）或显式引入 `BigDecimal`，而不是把整个 `Value` 体系改成浮点 ——
后者的代价见访问者 README 里 `double` 金额的事故（`46.400000000000006`）。

**为什么说"看树"比"看值"可靠？**

`2 * 3 + 4 * 5 = 26` 这个值，如果解析成 `2 * (3 + 4) * 5` 会得到 70，
听起来"值不同所以能发现错误"。但换成 `2 * 3 + 4 * 0`：
两种结合都是 6 —— **值相同，树不同，错误被掩盖了。**
`describe()` 不受这个影响：它直接输出树的形状。

### 6.8 Demo 8：尾部垃圾不再被静默丢弃

**入口**：`demo8TrailingGarbage()`

**做什么**：

```java
System.out.println("  「10*5」   -> " + ExpressionEngine.evaluate("10*5", Context.empty()).display());
System.out.println("  「  10  +  5  」 -> " + ExpressionEngine.evaluate("  10  +  5  ", Context.empty()).display());
expectFailure("10abc", () -> ExpressionEngine.evaluate("10abc", Context.empty()));
expectFailure("10 20", () -> ExpressionEngine.evaluate("10 20", Context.empty()));
```

**四种输入的处理路径：**

| 输入 | 词法结果 | 语法结果 |
|---|---|---|
| `10*5` | `[10][*][5][END]` | `(10 * 5)` → 50 |
| `  10  +  5  ` | 空白全部跳过，`[10][+][5][END]` | `(10 + 5)` → 15 |
| `10abc` | `[10][abc][END]` | `parse()` 读完 10 后发现 `abc` → 报错 |
| `10 20` | `[10][20][END]` | 读完 10 后发现 20 → 报错 |

**`10abc` 为什么被切成 `[10][abc]` 而不是 `[10abc]`？**

因为 `Lexer.readNumber` 只吃数字，读到 `a` 就停；`readIdentifier` 要求字母开头。
`10` 先被切成 `NUMBER`，然后 `abc` 被切成 `IDENTIFIER`。
**两个相邻 token 中间没有运算符** —— 语法层在 `parse()` 结尾发现还有剩余，报错。

这正是 5.6 里那条规则的直接效果：**标识符不允许数字开头**，
所以 `10abc` 不会被误读成"名为 10abc 的变量"。

**真实输出**（第 89–102 行）：

```text

================================================================
Demo 8 尾部垃圾不再被静默丢弃（探针 I7）
================================================================
  「10*5」   -> 50
  「  10  +  5  」 -> 15
  · 10abc
    多余的内容 'abc'，表达式应在此结束
      规则: 10abc
              ^
  · 10 20
    多余的内容 '20'，表达式应在此结束
      规则: 10 20
               ^
  原项目：10abc、10 中文、10 20 全部算出 10，多余内容无人过问
```

**`^` 的位置：**

```text
10abc      1(0) 0(1) a(2) b(3) c(4)      -> position 2（abc 的起点）
10 20      1(0) 0(1) 空格(2) 2(3) 0(4)   -> position 3（20 的起点）
```

`Token.position` 是 token **第一个字符**的下标，所以 `^` 落在 `a` 和 `2` 下方。

**对照原项目**：`10abc` 原来算出 `10`。这意味着什么？

```text
用户以为写的规则：  10abc         （可能是想写 10 * abc）
系统实际执行的规则： 10            （安静地少了一半）
```

在风控/计价场景里，这种"少执行一半"的规则可能让所有限制失效。
**I7 是十个问题中唯一"输入明显错误但程序完全不报错"的一条，**
所以本项目把它作为"必须检查剩余输入"的硬性理由（4.9）。

### 6.9 Demo 9：长链的三档实测

**入口**：`demo9LongChain()`（`throws Exception`，因为要 `Thread.join()`）

**做什么**：

```java
int[] sizes = {1000, 10000, 50000};
Expression biggest = null;
int describeLength = 0;
for (int size : sizes) {
    String rule = sumRule(size);                    // "1 + 1 + ... + 1"
    Expression tree = ExpressionEngine.parse(rule); // 解析：循环，不压栈
    String outcome;
    try {
        outcome = "结果 " + tree.evaluate(Context.empty()).display();   // 求值：递归
    } catch (StackOverflowError e) {
        outcome = "StackOverflowError";
    }
    System.out.println("  " + pad(size + " 项", 10) + "文本 " + pad(rule.length() + " 字符", 12)
            + "解析成功，默认栈求值 -> " + outcome);
    if (size == sizes[0]) { describeLength = tree.describe().length(); }
    biggest = tree;
}
```

**三个数字是怎么来的：**

`sumRule` 的写法是

```java
for (int index = 0; index < terms; index++) {
    if (index > 0) { text.append(" + "); }   // 3 个字符
    text.append('1');                        // 1 个字符
}
```

总长度 = `terms`（每个 1） + `3 × (terms - 1)`（分隔符）：

| 项数 | 文本长度 | 实际输出 |
|---:|---:|---|
| 1000 | 1000 + 3×999 = 3997 | `3997 字符` ✓ |
| 10000 | 10000 + 3×9999 = 39997 | `39997 字符` ✓ |
| 50000 | 50000 + 3×49999 = 199997 | `199997 字符` ✓ |

**`1000 项的 describe() 长度 5995` 是怎么来的？**

左深树的 `describe()` 每层加一对括号和两个空格：

```text
1 项        -> "1"                                  长度 1
2 项        -> "(1 + 1)"                            长度 7
3 项        -> "((1 + 1) + 1)"                      长度 13
4 项        -> "(((1 + 1) + 1) + 1)"                长度 19
```

每加一项，长度增加 6（2 个括号 + 2 个空格 + 1 个数字 + 1 个运算符）。
`n 项` 长度 = `1 + 6 × (n - 1)`：

```text
n = 1000 -> 1 + 6 × 999 = 5995   ✓ 与输出一致
```

**这个公式同时说明 `describe()` 的总代价是高阶的**：每个节点都要拼整个子树，
n 个节点的总字符拷贝量 ≈ `6 × n² / 2` = O(n²)。这正是 5.10 里那个
"50000 项 CPU 272 秒"事故的量化解释：
`n = 50000` 时约 7.5e9 次字符操作。

**真实输出**（第 104–114 行）：

```text

================================================================
Demo 9 长链：解析用循环，求值仍是递归（探针 I9）
================================================================
  1000 项    文本 3997 字符     解析成功，默认栈求值 -> 结果 1000
  10000 项   文本 39997 字符    解析成功，默认栈求值 -> StackOverflowError
  50000 项   文本 199997 字符   解析成功，默认栈求值 -> StackOverflowError
  4 项的形状: (((1 + 1) + 1) + 1)（左深树，左结合的证据）
  1000 项的 describe() 长度 5995 字符
  50000 项换 256 MB 栈的线程 -> 结果 50000
  结论：递归求值的深度上限由 JVM 栈决定，不是算法算错；
        真要跑百万项，得改成显式栈或把同级链摊平成 N 元节点
```

**三档数据说明什么：**

| 项数 | 解析 | 默认栈求值 | 说明 |
|---:|---|---|---|
| 1000 | 成功 | 1000 | 1000 层递归在默认栈内 |
| 10000 | 成功 | `StackOverflowError` | 解析仍然成功（循环），求值崩了（递归） |
| 50000 | 成功 | `StackOverflowError` | 同上，且文本近 20 万字符 |

**三个关键结论：**

1. **解析和求值的栈行为不同。** 解析用循环，50000 项也不压栈；
   求值用递归，10000 项就爆。这是 4.6 改解析、4.14 保留求值递归的直接结果。
2. **`StackOverflowError` 不是"算错了"。** 换 256 MB 栈的线程，50000 项得 50000。
   值是对的，只是默认栈（通常 512 KB ~ 1 MB）装不下 50000 层递归。
3. **边界是量化过的。** 不是"支持长表达式"这种含糊说法，而是
   "默认栈 1000 项可以、10000 项不行、256 MB 栈 50000 项可以"。

**为什么用 `Error` 而不是 `Exception` 捕获？**

`StackOverflowError` 继承自 `Error`，不是 `Exception`。
`catch (Exception e)` 抓不到它。Demo 里写的是 `catch (StackOverflowError e)`，
**这是少数几个应该捕获 `Error` 的场景**：我们知道它会发生，
且想把"崩溃"变成"可比较的输出行"。生产代码里通常不该这么做
（栈溢出后 JVM 状态可能不稳定），但演示需要它出现在输出里。

**`final Expression lastTree = biggest;` 这一行为什么必需？**
见 5.16：lambda 只能捕获 effectively final 的变量，而 `biggest` 在循环里被赋值。

### 6.10 Demo 10：安全网

**入口**：`demo10SafetyNet()`

这一段是"除了功能正确，还要在错误输入下给出可读错误"的集中演示。
五项检查：

| 检查 | 输入 | 期望 |
|---|---|---|
| 逻辑运算要求布尔 | `10 && 5` | 报"需要布尔值" |
| 算术运算要求数字 | `(age > 18) + 1` | 报"需要数字" |
| 除零 | `10 / 0` | 报"除数为 0" + 完整表达式 |
| 溢出 | `9223372036854775807 + 1` | 报"整数溢出" |
| 短路 | `count > 0 && total / count > 10` | `count=0` 不报除零 |
| 嵌套上限 | 201 层括号 / 200 层括号 | 前者报错，后者正常 |

**真实输出**（第 116–134 行）：

```text

================================================================
Demo 10 安全网：类型、除零、溢出、短路、嵌套上限
================================================================
  · 10 && 5（逻辑运算拿到数字）
    运算符 '&&' 的左操作数 需要布尔值，实际是数字 10
  · (age > 18) + 1（算术运算拿到布尔）
    运算符 '+' 的左操作数 需要数字，实际是布尔值 true
  · 10 / 0
    除数为 0: (10 / 0)
  · 9223372036854775807 + 1（溢出）
    整数溢出: 9223372036854775807 + 1
  短路保护除零: 「count > 0 && total / count > 10」
    count=0, total=100 -> false（右侧未求值，没有抛除零）
    count=2, total=100 -> true
  · 201 层括号（超过上限 200）
    表达式嵌套层数超过上限 200，请拆分规则
      规则: ...(((((((((((((((((((((((((((((((1))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))...
                                            ^
  正好 200 层括号 -> 1（边界值仍然可用）
```

**逐项拆解：**

**① `10 && 5`**

```text
LogicalExpression.evaluate
  -> left.evaluate -> Value.Num(10)
  -> asBoolean("运算符 '&&' 的左操作数")
  -> Num.asBoolean 抛：where + " 需要布尔值，实际是数字 " + value
```

消息里三个信息都在：**哪个运算符（`&&`）、哪一侧（左）、实际是什么（数字 10）**。
这三样缺一样，用户都得自己猜。

**② `(age > 18) + 1`**

```text
ArithmeticExpression.evaluate
  -> left.evaluate -> Value.Bool(true)
  -> asNumber("运算符 '+' 的左操作数")
  -> Bool.asNumber 抛：where + " 需要数字，实际是布尔值 " + value
```

注意括号**没有**进入树 —— `(age > 18) + 1` 的左边就是 `ComparisonExpression`，
括号只影响了"先把比较表达式建出来"。所以 `role` 里只有 `运算符 '+'`，
没有括号的痕迹。

**③ `10 / 0`**

```text
ArithmeticExpression.evaluate
  -> leftValue = 10, rightValue = 0
  -> operator == DIVIDE && rightValue == 0
  -> throw new ExpressionException("除数为 0: " + describe(), null, 0)
```

消息里带完整表达式 `(10 / 0)`，因为此时**已经确定要抛异常**，
调用 `describe()` 只发生这一次 —— 不是每个节点都拼。

**④ `9223372036854775807 + 1`**

```text
NumberExpression(9223372036854775807)  -> Long.MAX_VALUE
operator.compute -> Math.addExact -> 溢出 -> ArithmeticException
catch -> ExpressionException("整数溢出: 9223372036854775807 + 1", null, 0)
```

`Math.addExact` 让溢出从"安静变成负数"变成"立刻抛异常"。
`Long.MAX_VALUE` 能不能被 `Long.parseLong` 解析？
能 —— `9223372036854775807` 正好是 `Long.MAX_VALUE` 的十进制形式。
**再加 1 才溢出**，所以这个输入精准地卡在边界上。

**⑤ 短路**

```text
count=0: count > 0 -> false; AND 短路 -> 返回 false，右侧 total / count 从未求值
count=2: count > 0 -> true;  继续求值 total / count -> 100/2=50 -> 50 > 10 -> true
```

如果 `LogicalExpression` 先算两边，`count=0` 时会抛 `除数为 0`，
而不是返回 `false`。**这条输出是"短路是语义"的直接证据。**

**⑥ 嵌套上限**

```java
String deep    = "(".repeat(201) + "1" + ")".repeat(201);
String allowed = "(".repeat(200) + "1" + ")".repeat(200);
```

201 层时报错，200 层正常。**边界值两边都测** —— 只测 201 层会让人以为
"嵌套上限工作正常"，但无法排除"上限被设成了 100" 这种错误。
200 层成功、201 层失败，才证明上限确实是 200。

**规则那一行为什么被截断？**

201 层括号的规则文本是 403 个字符（201 + 1 + 201）。
`render()` 的窗口是 100 字符，超出后只保留出错位置附近的片段：

```text
规则: ...(((((((((((((((((((((((((((((((1))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))...
                                            ^
```

行长度 116（6 空格 + `规则: ` + 3 个点 + 100 字符 + 3 个点 = 6 + 4 + 3 + 100 + 3 = 116 ✓）。
`^` 行 46 个字符：45 个空格 + `^`。

**为什么 `^` 指向左括号区域而不是末尾？**
因为出错的 token 是第 201 个 `(`，它的 position 是 200；
窗口从 `position - 30 = 170` 开始，取 100 字符到 270。
`^` 的偏移 = `3（前缀 "..."） + (200 - 170) = 33`，
再加上 `render()` 的 8 格前缀与 `expectFailure` 的 4 格缩进 = 45 格。✓

**这六项的共同主题：**

> **错误输入必须在最早的位置、以最具体的方式失败。**
> 类型错在 `asNumber/asBoolean` 失败，除零在除法前失败，
> 溢出在 `Exact` 里失败，嵌套在解析时失败 ——
> 没有一项是靠"后面某处出现奇怪结果"来暴露的。

---

## 7. 扩展对比：新需求来了改哪里

这一节用六个真实需求，量化"解释器模式的扩展成本"。
每个需求都写清：改哪几个文件、每处改什么、为什么不能更少。

### 7.1 需求一：加取余运算符 `%`

**改动清单（4 个文件，4 处）：**

| 文件 | 改什么 | 行数 |
|---|---|---:|
| `TokenType` | 加一个枚举值 `PERCENT` | +2（含注释） |
| `Lexer.readSymbol` | 加 `case '%': position++; return new Token(PERCENT, "%", start);` | +3 |
| `ArithmeticExpression.Operator` | 加 `MODULO("%")` + `compute` 里加 `case MODULO -> leftValue % rightValue;` | +3 |
| `Parser.multiplicative` | `while` 条件加 `|| check(TokenType.PERCENT)`，映射加一个三元 | +3 |

**为什么是四处而不是一处？**

因为一个运算符在四个层次上各有一份"身份"：

```text
字符 '%'          -> 词法层：怎么切出来（Lexer）
TokenType.PERCENT -> 语法层：怎么识别（Parser）
Operator.MODULO   -> 语义层：怎么算（ArithmeticExpression）
"%"               -> 输出层：describe 怎么打印（Operator.symbol）
```

**这四处缺一处，编译器都会报错**（除了 `Lexer` 的 `default` 分支会变成
"无法识别的字符 '%'"，是运行时错误，但消息很清楚）。
这正是"加一种语法 = 加一个类/枚举值"的具体形态 ——
**改动分散但每一处都有兜底，而不是集中在一个巨型 `switch` 里靠人记住。**

**测试点：**

```text
10 % 3      -> 1
10 % 0      -> 除数为 0（需要在 ArithmeticExpression 里把 MODULO 也纳入除零检查）
```

**注意第二个测试点**：`%` 和 `/` 一样有除零问题。
如果只在 `operator == DIVIDE` 时检查，`10 % 0` 会抛 `ArithmeticException: / by zero`
而不是可读的消息。**加运算符时要想清楚它有没有和现有运算符相同的前置校验。**

### 7.2 需求二：支持字符串比较

**改动清单（5 个文件，且第 3 处会引发连锁）：**

| 文件 | 改什么 | 影响 |
|---|---|---|
| `TokenType` | 加 `STRING` | 小 |
| `Lexer` | 加字符串字面量：引号、转义（`\"`、`\\`） | 中，要处理未闭合引号 |
| `Value` | 加 `Str(String)` 分支 + `asText` | **大：所有 `switch`/`asNumber`/`asBoolean` 的位置都要考虑新类型** |
| `ComparisonExpression` | `test` 从"两个 long 比较"改成"按类型分派" | 大，六种运算符 × 三种类型 |
| `Context` | 值类型从 `Long` 变成 `Value` 或 `Object` | 中，`with(String, long)` 要加重载 |

**为什么第 3 处"大"？**

`Value` 现在是 `sealed interface`，只有 `Num` 和 `Bool` 两个实现。
加 `Str` 之后，所有 `asNumber` / `asBoolean` 的调用点都要回答：
"字符串能转数字吗？"

```text
"10" + 5     -> 15？还是报错？
"abc" > "abd" -> 按字典序？
"10" == 10   -> 类型不同，false 还是报错？
```

**这三个问题没有标准答案，取决于业务。** 这正是 5.12 里说的
"那是另一个需求，不该顺手塞进这个示例"。

**如果要做的正确顺序：**

1. 先定语义（字符串能不能自动转数字？`==` 是值相等还是类型+值？）；
2. 写测试用例（把上面三个问题固化成断言）；
3. 再改代码。

**先改代码后想语义，一定会得到一套自相矛盾的规则。**

### 7.3 需求三：加函数调用 `max(a, b)`

**改动清单（1 个新类 + 2 处修改）：**

| 文件 | 改什么 |
|---|---|
| `Parser.primary` | `IDENTIFIER` 分支里看下一个 token 是不是 `(`；是就解析参数列表 |
| 新增 `FunctionExpression` | `(String name, List<Expression> arguments)` + 一张函数表 |
| `TokenType` | 不用改！`(` `)` `,` 里只有逗号是新的 |

**为什么 `,` 是新问题？**

参数分隔需要逗号，而当前语言里没有逗号 token。
所以要加 `TokenType.COMMA` + `Lexer` 的 `case ','` —— 又回到 7.1 的"四处身份"。

**`FunctionExpression.evaluate` 长什么样：**

```java
public Value evaluate(Context context) {
    List<Value> values = new ArrayList<>(arguments.size());
    for (Expression argument : arguments) {
        values.add(argument.evaluate(context));       // 参数先全部求值
    }
    return FUNCTIONS.get(name).apply(values);         // 再调用函数
}
```

**注意参数是"先全部求值"**，不像 `&&` 那样短路 ——
因为函数参数没有"短路"语义。**加新节点时，求值顺序必须单独想清楚**，
不能照抄 `ArithmeticExpression` 的模板。

**函数表放哪？**

- 放 `Context` 里 → 每次求值都能注入不同函数，测试友好，但 `Context` 变成"变量 + 函数"两个职责；
- 放 `static Map` → 简单，但无法按租户定制；
- 放 `ExpressionEngine` → 门面职责膨胀。

三种都能用，本项目若要做会选第一种（`Context` 改名 `Environment`）。
**这也是一个"看需求"的决定，没有唯一正确答案。**

### 7.4 需求四：变量要有类型（整数/小数/字符串）

**改动清单（核心 2 处，连锁 5 处）：**

| 位置 | 改动 |
|---|---|
| `Context` | `Map<String, Long>` → `Map<String, Value>`；`with(String, long)` 加重载 |
| `VariableExpression` | `Value.of(context.require(...))` → 直接返回 `context.require(...)` |
| `Value` | 新增类型（如需小数） |
| 所有 `asNumber` / `asBoolean` 调用点 | 语义不变，但错误消息里的"实际是"要能打印新类型 |
| `Context.require` | 返回值类型变化 |
| Demo 6 的"已定义"列表 | 可能要显示类型 |

**为什么 `Context` 存 `Value` 而不是 `Object`？**

因为 `Object` 会让"取出变量"这一步失去类型约束：
`context.require("age")` 返回 `Object`，调用方还得 `instanceof`。
存 `Value` 则可以直接复用 `asNumber` / `asBoolean` 的错误消息体系。

**代价**：`Value.of(10)` 每次 `with` 都要包一层。
如果变量很多，可以考虑 `Map<String, Value>` + 复用的 `Value.Num` 常量池（小的整数缓存），
但这是优化，不是设计问题。

### 7.5 需求五：规则要缓存（同一条规则反复求值）

**改动清单：0 个源文件。**

```java
private static final Map<String, Expression> CACHE = new ConcurrentHashMap<>();

public static boolean testCached(String rule, Context context) {
    Expression tree = CACHE.computeIfAbsent(rule, ExpressionEngine::parse);
    return tree.test(context);
}
```

**为什么不用改源码？**

因为树是不可变的：

- 六个节点都是 `record`，字段 `final`；
- `record` 的字段类型是 `Expression` 或 `Operator`，都是不可变的；
- 没有 setter，没有可变集合。

**不可变性是缓存的前提。** 如果节点里有可变字段（比如原项目
`PriceCalculatorVisitor` 的 `totalPrice`），缓存就会把上一次的结果带进下一次。
访问者 README 的探针 V3 就是这个问题（第二次 `accept` 结果翻倍）。

**缓存键用什么？**
规则文本本身。要注意两点：

1. **空白差异**：`"age > 18"` 和 `"age  >  18"` 是两个键，但语义相同。
   如果要归一化，应该在**解析前**做（且归一化函数要可测试）。
2. **内存**：规则数量有上限吗？如果是用户自定义规则，需要 LRU 或容量上限。
   `ConcurrentHashMap` 无上限，长期运行会泄漏。

**这两个问题都不是解释器模式的问题**，是缓存本身的问题 ——
所以它们不该污染 `Expression`/`Parser`。**门面把"解析"和"缓存"分开，正是为了让它们各自演化。**

### 7.6 需求六：把树导出成 JSON / 统计节点数 / 做常量折叠

**改动清单：6 个节点类各加一个 `accept`，加 1 个访问者接口 + N 个实现。**

这就是**访问者模式**上场的时刻。判断依据：

> 元素类型固定（六个节点），操作不断新增（JSON、统计、优化、加密……）。
> **加操作会频繁改所有节点类，而节点类型本身不变。**

如果硬把 `toJson()` 写进每个节点：

```java
// ArithmeticExpression
public String toJson() { return "{\"op\":\"" + operator + "\",\"left\":" + left.toJson() + ",\"right\":" + right.toJson() + "}"; }
```

六个节点各写一份，**每加一种输出格式（YAML、S-expression、SQL）就再写六份**。
访问者把"六份 × N 种格式"变成"N 个访问者 + 六个 `accept`"：

```java
public interface ExpressionVisitor<R> {
    R visit(NumberExpression node);
    R visit(VariableExpression node);
    R visit(ArithmeticExpression node);
    R visit(UnaryExpression node);
    R visit(ComparisonExpression node);
    R visit(LogicalExpression node);
}
```

**代价**：加第七种节点时，所有访问者都要改（类型封闭）。
**收益**：加第七种操作时，一个节点都不用改。

**本项目的取舍**：只做"求值"一种操作，所以不引入访问者 ——
**为一种操作写访问者是过度设计**（详见 3.2 与 `visitor/README.md` 第 8 节）。

### 7.7 六个需求的改动量对照

| 需求 | 新增类 | 修改文件 | 修改处 | 难度 | 主要风险 |
|---|---:|---:|---:|---|---|
| `%` 运算符 | 0 | 4 | 4 | 低 | 忘了 `% 0` 的除零检查 |
| 字符串比较 | 0 | 5 | 5+ | 高 | 语义没定清就动手 |
| 函数调用 `max(a,b)` | 1 | 3 | 4 | 中 | 参数求值顺序、函数表放哪 |
| 变量带类型 | 0 | 2 | 2+5 | 中 | `Context` 泛化引发连锁 |
| 规则缓存 | 0 | 0 | 0 | 低 | 键归一化、内存上限 |
| 导出 JSON | 1+N | 6 | 6+N | 中 | 该切到访问者 |

**四条规律：**

1. **加语法（运算符/字面量）→ 改 4 处左右，编译器全程兜底。** 这是解释器模式最擅长的扩展。
2. **改语义（字符串、类型）→ 改动量取决于"语义定得清不清"，不是代码结构。** 结构再好也救不了含糊的需求。
3. **加能力（缓存）→ 源码零改动。** 这是"树不可变 + 门面"的直接收益。
4. **加操作（导出/统计/优化）→ 该换模式了。** 继续往节点里塞方法，就是 1.1 里说的"上帝类"。

---

## 8. 优缺点与常见误区

### 8.1 适用场景

**适合（四条同时成立时）：**

| 条件 | 含义 | 本项目 |
|---|---|---|
| 文法稳定 | 不会每周加新语法 | 表达式 + 六种运算 + 括号，教科书级稳定 |
| 规则由人写 | 需要可读的错误消息 | 运营/风控在后台填规则 |
| 规模可控 | 单条规则几十到几百 token | 实测 1000 项可以、10000 项需大栈 |
| 需要精确语义 | 优先级、结合性、短路必须可控 | `10-3-2`、`count>0 && total/count>10` |

**不适合（任一条成立时）：**

| 条件 | 后果 | 替代方案 |
|---|---|---|
| 规则写死在代码里，从不改 | 十几个类是纯负担 | 直接 `if` |
| 文法每周变 | 每次都要改解析器 + 所有节点 | 用现成脚本引擎 |
| 需要极致性能（百万 QPS） | 递归求值 + 每次解析太慢 | 编译成字节码 / 预编译表达式树 |
| 规则数量上万条 | 内存与解析成本爆炸 | 规则索引 + 只解析命中的规则 |
| 只是"选一个选项" | 那不是语言 | `switch` / 策略模式 |

### 8.2 设计代价

**① 类的数量。**

16 个文件里，真正"干活"的只有 6 个节点类；
另外 10 个是类型（`Value`/`Token`）、基础设施（`Lexer`/`Parser`）、
诊断（`ExpressionException`）、门面（`ExpressionEngine`）、客户端（`Client`）。

**这不是"为了模式而多写类"** —— 每一个都能回答"删掉会怎样"（见第 5 节）。
但必须承认：**要读懂 1987 行才能完整理解一条规则怎么算出来。**

**② 解析器是最难的部分。**

`Parser` 286 行，比任何一个节点类都复杂。
递归下降本身不难，难的是：

- 九层优先级的顺序不能错；
- 左结合要写成循环；
- `END_OF_INPUT` 的边界；
- 嵌套上限的计数。

**这些难点和"解释器模式"无关，和"写一个解析器"有关。**
如果团队里没人写过解析器，这一块是真实的门槛。

**③ 递归求值的栈限制。**

见 6.9：默认栈 1000 项可以、10000 项 `StackOverflowError`。
虽然不是 bug，但**必须写进文档**，否则用户会在生产环境遇到。

**④ `describe()` 的 O(n²) 陷阱。**

见 5.10：诊断代码不能在热路径上。这一条要作为团队约定写下来。

**⑤ 学习成本。**

一个新人要理解四层：

```text
字符 -> Token -> 语法树 -> Value
```

任何一层跳过，后面的代码都读不懂。而 `if/else` 版本只需要一层。

### 8.3 六个常见误区

**误区一：「解释器模式 = 写一个脚本引擎」**

不是。解释器模式只规定"每个文法产生式一个类 + 递归求值"。
真正的脚本引擎还有：作用域、闭包、异常、GC、JIT、沙箱、超时。
**本项目是"小而完整的表达式引擎"，不是通用脚本引擎。**
它的价值在于：**用 2000 行把"优先级、结合性、短路、错误定位"讲透**。

**误区二：「只有复杂的语言才需要解释器」**

反了。恰恰是**极简文法**最适合解释器模式：

```text
"amount > 199 && level >= 3"
```

只有比较 + 与或，没有变量声明、没有循环、没有函数。
文法越简单，节点类越少，收益越明显。
反过来，如果文法复杂到需要脚本引擎，就不该自己实现。

**误区三：「节点类越多越像模式」**

六个节点已经足够覆盖：终结符（数字/变量）+ 二元（算术/比较/逻辑）+ 一元。
再拆下去（`AddExpression`、`SubtractExpression` 各一个类）就是
**"每个运算符一个类"** —— 14 个类，每个 3 行代码，只有 `compute` 不同。
判断标准见 4.10：**结构不同才拆类，行为不同用枚举。**

**误区四：「把业务规则写进节点」**

比如在 `ComparisonExpression` 里加：

```java
if (rightValue == 500 && "score".equals(name)) { ... }   // 绝对不要
```

节点类只该知道">` 怎么算"，不该知道"500 分是及格线"。
**业务规则属于 Context 或调用方，不属于文法。**
一旦写进去，这条文法就只能服务于一个业务了。

**误区五：「忽略短路和类型」**

两个最容易写错的地方：

```java
// 错误：先算两边
boolean l = left.evaluate(ctx).asBoolean(...);
boolean r = right.evaluate(ctx).asBoolean(...);     // count=0 时这里就炸了
return Value.of(l && r);

// 正确：短路
boolean l = left.evaluate(ctx).asBoolean(...);
if (operator == AND && !l) { return Value.of(false); }
```

```java
// 错误：布尔用 1/0 表示
long v = expr.evaluate(ctx).asNumber(...);          // true 变成 1
return Value.of(v != 0);                            // 10 && 5 算出 true
```

**这两个错误都不会在正常输入下暴露**，只在边界条件（`count=0`、`10 && 5`）出现。
Demo 10 专门测这两个 case，就是为了防止它们悄悄回归。

**误区六：「有了模式就不用管错误消息」**

原项目就是典型：模式用对了（`Expression` + `AndExpression` + 两个终结符），
但错误消息全是 `For input string: ""`。
**模式解决结构问题，不解决可用性问题。** 十个问题里七个与模式无关（2.15），
它们只能靠"自定义异常 + 位置 + 具体消息"来修。

### 8.4 什么时候该切到另一个模式

| 信号 | 该切到 | 理由 |
|---|---|---|
| 元素类型固定、操作不断新增 | **访问者** | 避免每次加操作都改所有节点类 |
| 树的结构不变、但要按不同业务算 | **策略** | 把"怎么算"抽成可替换的算法 |
| 只有 2-3 条固定规则 | **不用模式** | `if` 最省 |
| 规则要支持超长文本（百万 token） | **编译成字节码/表驱动** | 递归下降受栈限制 |
| 规则的组合方式是固定的几层 | **责任链 / 装饰器** | 不需要通用文法 |

**特别提醒**：本项目已经有"组合"和"访问者"的影子 ——
树的结构像组合，`describe()` 像访问者里的一个操作。
但**我们刻意没有引入它们**，因为：

- 组合模式的核心是"叶子和容器接口一致"，本项目里 `NumberExpression` 和
  `ArithmeticExpression` 的接口确实一致，但**没有"容器"的概念**（没有 `addChild`）；
- 访问者模式需要"操作多于类型"，本项目只有 `evaluate` + `describe` 两个操作。

**"结构相似"不等于"该用那个模式"。** 判断依据永远是：**哪个维度会变。**

### 8.5 本项目没做的事（诚实清单）

| 没做 | 原因 | 如果要做 |
|---|---|---|
| 消除求值递归 | 代价是破坏"节点自己解释自己" | 显式栈的字节码机 |
| 规则缓存 | 示例里每条规则只解析一两次 | 调用方 `ConcurrentHashMap` |
| 字符串/小数 | 语义未定，会引发连锁改动 | 先定语义再改（见 7.2） |
| 函数调用 | 示例用不到 | `FunctionExpression` + 函数表 |
| 多行规则与行列号 | 表达式是单行文本 | `Token` 加行号，`render` 做换算 |
| 并发压测 | 不可变设计已保证安全，但没测吞吐 | JMH |
| 模糊测试 | 手写用例覆盖了边界 | jqwik / 随机表达式生成器 |

**最后一条尤其重要**：本项目的 10 段 Demo 覆盖了 10 个探针问题 + 各类边界，
但**没有做随机输入的模糊测试**。如果要上线，第一步应该是：

```text
随机生成 10 万条表达式 -> 解析 + 求值 -> 断言"不抛非 ExpressionException 的异常"
```

这一条能抓出"某些输入组合导致 `IllegalStateException`"这类漏网问题。
本项目里 `Parser.comparison` 的 `default -> throw new IllegalStateException("不可能到达: " + token)`
就是这样一个"理论上不可达"的分支 —— 模糊测试能证明它真的不可达。

---

## 9. 面试与自测

### 9.1 十道自测题

**第 1 题：为什么 `evaluate` 返回 `Value` 而不是 `boolean`？**

<details>
<summary>答案要点</summary>

- 布尔返回类型只能表达"规则"，无法表达算术，`(age + 5) * 2 > 50` 就写不出来；
- 原项目因此搞了两套体系（`Expression` 返回 boolean，`Node` 返回 int），互不相通（探针 I10）；
- `Value` 让"类型错"可以表达成错误：`10 && 5` 报「需要布尔值，实际是数字 10」；
- 如果统一用 long（true=1），`10 && 5` 会安静地算出 true —— 用户写错了却得到合法结果。

一句话：**返回类型的宽度决定了文法的表达力，也决定了错误能不能被发现。**
</details>

**第 2 题：有 `Parser` 了，为什么还需要 `Lexer`？**

<details>
<summary>答案要点</summary>

- 只有 `Lexer` 在"逐字符"读原文，因此只有它能同时给出**类型**和**位置**；
- 没有词法层，`>=` 会被 `contains(">")` 误判成 `>`（探针 I3）；
- `10abc` 无法区分"数字 10 后面跟垃圾"和"名为 10abc 的变量"（探针 I7）；
- 最长匹配只能在字符层实现：看到 `>` 要先看下一个是不是 `=`。

一句话：**词法层负责"字符能不能组成单词"，语法层负责"这个单词在这个位置是什么意思"。**
</details>

**第 3 题：`additive()` 为什么用 `while` 而不是递归？**

<details>
<summary>答案要点</summary>

两个理由，缺一不可：

- **结合性**：递归写法是右结合，`10-3-2` 会算成 `10-(3-2)=9`；`while` 写法往左上生长，得到 `(10-3)-2=5`；
- **栈深度**：递归每项压一层栈，10000 项就 `StackOverflowError`（探针 I9）；`while` 同层只占一层。

注意加法看不出结合性差异（`1+(2+3)` 和 `(1+2)+3` 都是 6），
所以必须用减法验证 —— Demo 7 特意放了 `10 - 3 - 2`。
</details>

**第 4 题：`&&` 的短路是性能优化还是语义？**

<details>
<summary>答案要点</summary>

**是语义。** 规则里最常见的写法是：

```text
count > 0 && total / count > 10
```

用前一个条件保护后一个条件。如果先算两边，`count=0` 时会抛"除数为 0"，
而不是返回 false。Demo 10 实测：`count=0 -> false（右侧未求值）`。

代价：`false && AGE > 18` 不会报"未定义变量"。
这是短路的必然结果，本项目选择与 Java 的 `&&` 保持一致 ——
一致性比"更严格"更重要，否则用户会因为条件顺序不同看到不同报错。
</details>

**第 5 题：`Context` 为什么不可变？代价是什么？**

<details>
<summary>答案要点</summary>

三个理由：

- **求值可复现**：同一棵树 + 同一份上下文 = 同一个结果；
- **可并发**：解析一次、多线程求值，是规则引擎的典型用法；
- **错误定位准确**：报错时的变量表就是求值时的变量表，不会"中途被改"。

代价：`with` 每次复制 `LinkedHashMap`，建 k 个变量是 O(k²)。
本项目变量只有两三个，可以忽略；变量上千时应提供 `Context.of(Map)` 一次性建好，
而不是改回可变。
</details>

**第 6 题：为什么 `describe()` 不能出现在 `evaluate` 的成功路径上？**

<details>
<summary>答案要点</summary>

因为它是 O(子树大小)，而 `evaluate` 会被每个节点调用一次 ——
总代价 O(n²)。实测：左深链 50000 项时，字符拷贝约 1.25e9 次，
**进程 CPU 272 秒仍未结束**。

正确做法：

- 成功路径只拼短前缀：`"运算符 '" + operator.symbol() + "'"`；
- 完整 `describe()` 只在真正抛异常（除零）时调用一次。

一句话：**诊断信息的成本必须按"出错时才付"来设计。**
</details>

**第 7 题：为什么 `END_OF_INPUT` 要作为一个真实 token？**

<details>
<summary>答案要点</summary>

三个好处：

- `peek()` 永远合法（`tokens.get(index)` 不越界），语法层不需要判空；
- 错误消息统一：`display()` 对 END 返回"输入结束"，于是 `10 +` 报的是
  「表达式在此处意外结束，缺少操作数」，而不是数组越界；
- `advance()` 不会越过末尾（`if (!token.isEnd()) index++`）。

如果没有它，就只能靠 `index >= tokens.size()` 判断，
**"检查剩余输入"这件事会散落在每个方法里，迟早漏一处**（探针 I7 就是这么漏的）。
</details>

**第 8 题：`MAX_NESTING = 200` 是怎么定的？**

<details>
<summary>答案要点</summary>

不是从业务需求推出来的（真实规则不会嵌套上百层），
而是**把一个不可读的错误换成可读的错误**：

- 不设上限：500 层括号会在解析阶段 `StackOverflowError`，没有任何位置信息；
- 设上限：抛「表达式嵌套层数超过上限 200，请拆分规则」，带 `^` 指针。

200 这个值的依据：默认 JVM 栈（512 KB ~ 1 MB）能容纳的递归深度通常在几千层，
200 留了足够余量；同时 Demo 10 测了 200 成功 / 201 失败，证明边界精确。

**关键点：边界值两边都要测。** 只测 201 失败，无法排除"上限被设成 100"。
</details>

**第 9 题：把原项目 `RuleParser` 的 `left/right` 换成 `List<Expression>` 能修好 I1 吗？**

<details>
<summary>答案要点</summary>

**不能修好，只是换一种错法。**

`List` 能装任意数量的条件，但 `AndExpression` 是二元的：

```java
return new AndExpression(left, right);      // 三个条件怎么塞进两个参数？
```

要么手工折叠（`list.get(0) && list.get(1) && list.get(2)`），
要么改成 N 元节点 —— 而这两条路都要求**先有一个正确的解析过程**：

- 手工折叠：条件数量一多，代码就成了循环里套 `if`；
- N 元节点：`and` 就变成了"变长运算符"，`describe` 与求值都要改。

真正的修法是第 4.6 节的方案：**解析时用循环向左折叠，树本身就是二元的、可嵌套的。**
数据结构要跟文法同构，而不是"多给几个格子"。
</details>

**第 10 题：给语言加 `%` 运算符，要改哪几处？**

<details>
<summary>答案要点</summary>

四处（见 7.1）：

| 层 | 文件 | 改什么 |
|---|---|---|
| 词法 | `TokenType` + `Lexer.readSymbol` | 加 `PERCENT` 与 `case '%'` |
| 语法 | `Parser.multiplicative` | `while` 条件加 `|| check(PERCENT)` |
| 语义 | `ArithmeticExpression.Operator` | 加 `MODULO("%")` + `compute` 的 case |
| 输出 | 同上（`symbol()`） | `describe()` 自动印出 `%` |

**加分点**：还要把 `%` 纳入除零检查（`10 % 0`），
否则会抛裸的 `ArithmeticException: / by zero`，而不是可读消息。
</details>

### 9.2 三道开放题

**开放题一：支持集合判断 `level in [1, 2, 3]`，怎么加？**

要点：

- 词法：新增 `IN`、`LEFT_BRACKET`、`RIGHT_BRACKET`、`COMMA` 四个 token；
- 语法：`in` 的优先级介于比较和相等之间（或与比较同级），需要新的 `in()` 层；
- 节点：新增 `InExpression(Expression target, List<Expression> candidates)`；
- 求值：`target` 求值一次，逐个比较（可短路：命中即返回 true）；
- 风险：`[1, 2, 3]` 里的元素要不要允许变量？允许的话就是"表达式列表"，
  不允许就是"字面量列表" —— **语义要先定**。

**开放题二：如果规则要支持用户自定义函数，`Context` 怎么改？**

要点：

- `Context` 从"变量表"升级为"环境"：`Map<String, Long> variables` + `Map<String, Function> functions`；
- `Function` 接口：`Value apply(List<Value> args)`；
- 新增 `FunctionExpression.evaluate` 时先求参数、再查函数、再调用；
- 关键问题：**函数能访问上下文吗？** 能就是"闭包"，语义复杂度陡增；
- 建议第一版只支持纯函数（只依赖参数），把"闭包"留给下一个需求。

**开放题三：规则缓存如何保证并发安全？**

要点：

- 树不可变（六个节点都是 `record`）→ 可以安全共享；
- `Context` 不可变 → 同一份上下文也可共享；
- 用 `ConcurrentHashMap.computeIfAbsent(rule, ExpressionEngine::parse)`；
- 注意两点：① 键要归一化（空白差异）；② 要设容量上限或 LRU，否则内存泄漏；
- `computeIfAbsent` 的映射函数里**不要做耗时操作**（会阻塞同一个桶的其他线程），
  解析一条规则通常在毫秒级，可以接受；如果是秒级，应该"先算后放"。

### 9.3 面试回答模板

**60 秒版：**

> 解释器模式把一门文法的每个产生式映射成一个类，每个类实现一个 `interpret/evaluate`
> 方法，组合节点在方法里递归调用子节点。它适合"文法稳定、规则由人写、规模可控"的场景，
> 比如风控规则、促销条件。本项目用 16 个文件实现了：词法分析（最长匹配）、
> 九层优先级的递归下降解析（左结合用循环）、六个 AST 节点（数字、变量、算术、一元、比较、逻辑）、
> 以及带位置指针的错误诊断。原项目 12 个文件有 10 个实测缺陷 ——
> 条件丢失、单条件 NPE、`>=` 误判、静默 false、尾部垃圾被丢弃等 ——
> 全部在重构里修复并被 Demo 覆盖。

**3 分钟版（加三个技术点）：**

> 第一个技术点是**返回类型**。原项目的 `Expression.interpret` 返回 boolean，
> 所以它无法表达算术，只能另写一套 `Node.evaluate` 返回 int，两套互不相通。
> 本项目统一用 `Value`（密封接口 + Num/Bool 两个 record），
> 类型错误就能变成明确异常：`10 && 5` 报「需要布尔值，实际是数字 10」。
>
> 第二个技术点是**解析**。递归下降每个优先级一个方法；
> 同层用 `while` 循环向左折叠，既保证左结合（`10-3-2=5` 而不是 9），
> 又让栈深与项数无关。但求值仍是递归，实测默认栈 1000 项可以、10000 项溢出，
> 换 256 MB 栈 50000 项成功 —— 我在 Demo 里如实测了三档，没有掩盖这个限制。
>
> 第三个技术点是**错误诊断**。每个 token 带源码下标，
> 异常能渲染成"消息 + 规则原文 + `^` 指针"三行；超长规则会截断成 100 字符窗口，
> 否则 201 层括号的错误信息会把控制台撑爆、`^` 也对不上。
> 这里还踩过一个性能坑：`describe()` 拼的是整棵子树，如果在每个节点的求值路径上都调，
> 就是 O(n²)，50000 项实测卡死 272 秒 —— 后来改成短前缀，只在真正抛异常时才拼完整表达式。

---

## 附录 A：文件清单

```text
src/com/patterns/behavioral/interpreter/
├── Client.java                     266 行    10 段 Demo
├── Expression.java                  84       抽象表达式（sealed）
├── Value.java                      125       求值结果（sealed + 2 record）
├── Context.java                    165       不可变变量表
├── TokenType.java                   74       18 种词法单元
├── Token.java                       66       类型 + 原文 + 下标
├── Lexer.java                      206       字符流 -> 记号流
├── Parser.java                     286       记号流 -> 表达式树
├── NumberExpression.java            34       终结符：数字
├── VariableExpression.java          57       终结符：变量
├── ArithmeticExpression.java       132       非终结符：+ - * /
├── UnaryExpression.java             90       非终结符：- !（一元）
├── ComparisonExpression.java       111       非终结符：> >= < <= == !=
├── LogicalExpression.java           88       非终结符：&& ||（短路）
├── ExpressionException.java        122       统一异常 + caret + 窗口截断
└── ExpressionEngine.java            81       门面：四个静态方法
```

| 指标 | 数值 |
|---|---:|
| Java 文件 | 16 |
| 物理行 | 1987 |
| class 文件 | 23 |
| class 字节 | 69870 |
| 编译警告 | 0 |

**为什么 16 个文件会产出 23 个 class？**

这 23 个 `.class` 文件来自三类：16 个顶层类型 + 6 个手写的嵌套类型 + 1 个编译器合成类。

手写的 6 个嵌套类型：

| 外层 | 嵌套类型 | 数量 |
|---|---|---:|
| `Value` | `Value.Num`、`Value.Bool` | 2 |
| `ArithmeticExpression` | `ArithmeticExpression.Operator` | 1 |
| `UnaryExpression` | `UnaryExpression.Operator` | 1 |
| `ComparisonExpression` | `ComparisonExpression.Operator` | 1 |
| `LogicalExpression` | `LogicalExpression.Operator` | 1 |
| **小计** | | **6** |

还有 1 个不是手写的：`Parser$1.class`（882 字节）。
它是 javac 为 `switch` 表达式在枚举上生成的合成类 ——
`Parser` 里按 `TokenType` 分支时用到了 switch，编译器需要一张
「枚举序号 → 分支」的映射表，就生成了这个没有源码对应物的类。

```text
16 个顶层 + 6 个手写嵌套 + 1 个编译器合成 = 23 个 .class ✓
```

这也解释了一个常见困惑：**数 `.class` 文件来估算「写了几个类」并不可靠**，
因为枚举 switch、lambda、匿名类、record 的 accessor 都可能额外生成文件。
本项目统计「23 class」指的是编译产物数量（`69870` 字节实测），
而「16 个源文件」才是人手写的规模。

## 附录 B：运行方式与验证记录

**编译（JDK 25，零警告）：**

```powershell
$jdk = "F:\enCodingApps\JDK\jdk-25"
$src = "src\com\patterns\behavioral\interpreter"
javac -Xlint:all -encoding UTF-8 -d out $src\*.java
```

**运行（Windows，必须指定 UTF-8）：**

```powershell
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 `
     -cp out com.patterns.behavioral.interpreter.Client
```

**三次运行结果（完全相同）：**

```text
run1: exit=0 lines=134 bytes=6501 err=0 sha256=FD30D6AE5E2C10264DCD857DB3B7C8993074FFD13F603802B27897EA523F5599
run2: exit=0 lines=134 bytes=6501 err=0 sha256=FD30D6AE5E2C10264DCD857DB3B7C8993074FFD13F603802B27897EA523F5599
run3: exit=0 lines=134 bytes=6501 err=0 sha256=FD30D6AE5E2C10264DCD857DB3B7C8993074FFD13F603802B27897EA523F5599
```

**三次哈希一致说明输出不含时间戳、随机数、哈希表迭代顺序等不确定性。**
这也是 `Context` 用 `LinkedHashMap`、`ExpressionException.render()` 固定用 `\n` 的原因。

**原项目基线（重构前的对照数据）：**

| 入口 | 行数 | 字节 | 编码 | SHA-256 |
|---|---:|---:|---|---|
| `BehaviorType.InterpreterPattern.Client` | 1 | 18 | GBK | `A0576E059CCDC981F7161B9A5D579B7400E95A0004910968E0C59D5B52ECF109` |
| `...AST_Demo.SimpleASTMain` | 1 | 14 | GBK | `92C1ABF8A01AB50D5C81754297545D678FDEADCE754012C3562AE578A111F777` |
| `InterpProbe`（缺陷取证） | 67 | 4375 | UTF-8 | `EBB2801286C0B007CAD98CD0AE1C0F626349F69B947F48831291D16E6407209B` |

原项目两个入口都是"正确"的输出，缺陷只在探针的额外输入下暴露 ——
这也是为什么**只看 Demo 输出不足以判断实现质量**（2.16）。

## 附录 C：相关模式交叉索引

| 模式 | 关系 | 本项目位置 |
|---|---|---|
| **组合（Composite）** | 结构相似：都组织成树，父节点递归调用子节点。区别：组合的树用于"统一对待部分与整体"，解释器的树用于"表达带优先级的文法" | `structural/composite/` |
| **访问者（Visitor）** | 解释器的"扩展操作"解法。树的结构不变、要加 JSON 导出/统计/优化时，切到访问者 | `behavioral/visitor/` |
| **策略（Strategy）** | 都是"把行为封装成对象"。区别：策略是平级的可替换算法，解释器是有层级的文法结构 | `behavioral/strategy/` |
| **责任链（Chain of Responsibility）** | 都沿一条链处理请求。区别：责任链的链是线性的、每个节点可选择"传给下一个"，解释器的树是嵌套的、每个节点必须处理子节点的结果 | `behavioral/chainofresponsibility/` |
| **命令（Command）** | 都把"请求"对象化。区别：命令封装"一次操作 + 参数"，解释器封装"一门语言的结构" | `behavioral/command/` |
| **模板方法（Template Method）** | 都在父类/接口里固定骨架、子类填细节。区别：模板方法的骨架是调用顺序，解释器的骨架是递归组合 | `behavioral/templatemethod/` |
| **中介者（Mediator）** | 都减少对象间的直接依赖。区别：中介者处理"多方通信"，解释器处理"文法求值" | `behavioral/mediator/` |

**最值得对照阅读的是访问者**：两者的差别正好是"哪个维度会变"的教科书案例 ——

```text
解释器：文法（类型）固定，表达式（实例）不断新增
访问者：元素类型固定，操作不断新增
```

## 附录 D：原项目十个问题对照表

| 编号 | 问题 | 原项目位置 | 本项目修复位置 | 验证 |
|---|---|---|---|---|
| I1 | 第三条条件覆盖第二条 | `RuleParser.parse` 的 `left/right` | `Parser.and()` 的 `while` 折叠 | Demo 2 |
| I2 | 单条件 NPE | `RuleParser` 无条件 `new AndExpression` | 不再包装，树的形状 = 规则形状 | Demo 3 |
| I3 | `>=` 被误判 | `RuleParser.contains(">")` + `split(">")` | `Lexer.readSymbol` 最长匹配 | Demo 4 |
| I4 | 异常不含规则与位置 | `Integer.parseInt` 裸抛 | `ExpressionException` + `position` + `render()` | Demo 5 |
| I5 | 未知字段静默 false | `GreaterThanExpression` 的 `return false` | `Context.require` 抛异常 | Demo 6 |
| I6 | 大小写敏感无提示 | `"age".equals(field)` | 同上，消息里列出可用名字 | Demo 6 |
| I7 | 尾部垃圾被丢弃 | `SimpleParser.parseAddition` 不检查剩余 | `Parser.parse()` 检查 `END_OF_INPUT` | Demo 8 |
| I8 | 五种错误同一句 | `parseNumber` 的 `parseInt("")` | 每种情况独立消息 + 位置 | Demo 5 |
| I9 | 右递归爆栈 | `SimpleParser.parseAddition` 递归 | `while` 循环解析；求值递归保留并实测边界 | Demo 9 |
| I10 | 两套语法互不相通 | `Expression`（boolean）与 `Node`（int） | 统一 `Expression` + `Value` | Demo 7 |

## 附录 E：概念速查

| 术语 | 英文 | 一句话解释 | 本项目对应 |
|---|---|---|---|
| 抽象表达式 | AbstractExpression | 声明 `interpret/evaluate` 的公共接口 | `Expression` |
| 终结符表达式 | TerminalExpression | 递归终点，不调用子节点 | `NumberExpression`、`VariableExpression` |
| 非终结符表达式 | NonterminalExpression | 持有子节点并递归求值 | 四个 `*Expression` |
| 上下文 | Context | 求值时的外部数据 | `Context` |
| 抽象语法树 | AST | 表达求值顺序的树 | 六个节点拼成的结构 |
| 词法分析 | Lexical Analysis | 字符流 → 记号流 | `Lexer` |
| 语法分析 | Parsing | 记号流 → 语法树 | `Parser` |
| 递归下降 | Recursive Descent | 每个非终结符一个方法 | `or/and/.../primary` |
| 左递归 | Left Recursion | 结果向左结合的递归；实现时用循环 | `while` 折叠 |
| 最长匹配 | Maximal Munch | 能匹配更长的符号就匹配更长的 | `>=` 先于 `>` |
| 短路求值 | Short-circuit | 左边能决定结果就不算右边 | `LogicalExpression` |
| 双重分派 | Double Dispatch | 调用由"节点类型 + 访问者类型"共同决定 | 访问者模式（本包未用） |
| 门面 | Facade | 把多步调用固化成一句 | `ExpressionEngine` |
| 诊断信息 | Diagnostic | 面向用户的错误说明 + 位置 | `ExpressionException.render()` |

## 附录 F：版本与工具链

| 项目 | 版本/配置 |
|---|---|
| JDK | 25（`F:\enCodingApps\JDK\jdk-25`） |
| 编译选项 | `-Xlint:all -encoding UTF-8` |
| 运行选项 | `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` |
| 语言特性 | `record`、`sealed interface`、`switch` 表达式、`Math.*Exact` |
| 第三方依赖 | 无（仅 JDK 标准库 `java.util`、`java.util.concurrent.atomic`） |
| 构建方式 | 直接 `javac` 编译，无 Maven/Gradle（与原项目一致） |
| 输出编码 | UTF-8（原项目为 GBK，见附录 B） |
| 验证方式 | 编译零警告 + 三次运行哈希一致 + 逐段核对输出 |

**为什么坚持不用构建工具？**
与原项目保持一致 —— 任务要求"不随意改变构建方式"。
代价是没有依赖管理，但本项目零依赖，用 `javac` 反而更透明：
读者能看到"16 个文件编译成 23 个 class"这件事本身。

---

## 写在最后

解释器模式是最容易被"讲成类图"的一个模式：
`Expression` 在中间、六个节点在下面、`Context` 在右边 —— 画出来很整齐。
但真正难的是**那些看不见的决定**：

- 为什么 `evaluate` 返回 `Value` 而不是 `boolean`；
- 为什么同层运算必须用循环；
- 为什么 `describe()` 不能出现在成功路径上；
- 为什么 `END_OF_INPUT` 要是一个真实 token；
- 为什么 50000 项会崩，崩了为什么还说"算对了"。

这五个问题的答案，构成了本项目 1987 行里的绝大部分价值。

原项目的 12 个文件 257 行，其实"骨架"是对的：
有抽象表达式、有终结符、有非终结符、有上下文。
它缺的不是模式知识，而是**把模式用对、把边界想全、把错误说清**。

所以这份文档反复在做一件事：

> **把"能跑"和"对"分开，把"对"和"可诊断"分开。**

`10*5` 算出 10 是"能跑"；报"多余的内容"才是"对"。
报"多余的内容"是"对"；画出 `^` 指到 `*` 才是"可诊断"。

解释器模式给了我们一个结构，让我们有机会在这三层上都做到位。
结构本身不值钱，**结构带来的"每一层都能被检查和修正"才值钱。**

---

*本文档基于实际运行结果编写：所有输出均为逐字复制，
所有行数与哈希均为实测，所有"原项目缺陷"均有探针证据。*
