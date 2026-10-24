# 访问者模式（Visitor Pattern）

> **一句话**：把「对一组对象的操作」从对象里抽出来，做成独立的访问者。
> 元素只负责 `accept` 交出自己，访问者只负责 `visit` 处理自己；
> 一次调用在「元素的具体类型」和「访问者的具体类型」上各分派一次，所以叫**双重分派**。

| 项目 | 数值 |
|---|---|
| 原项目规模 | 16 个源文件 / 348 行（文档 7 文件 141 行 + 购物车 9 文件 207 行） |
| 原项目运行 | 两个入口均 exit=0，GBK 编码输出 7 行 / 8 行 |
| 缺陷取证 | 探针 `VisitorProbe` 8 条证据，53 行，SHA-256 `93EB804C…8FFE0A` |
| 重构后规模 | 26 个源文件 / 2563 物理行 / 27 个 class / 85199 字节 |
| 重构后运行 | 8 段 Demo，123 行输出 / 6948 字节，三次运行哈希一致 `07BD39FA…DC4DDB` |
| 编译 | `javac -Xlint:all` 零警告（JDK 25） |

---

## 目录

- [1. 现实案例：同一份文档，四种操作](#1-现实案例同一份文档四种操作)
- [2. 原始实现：原项目 348 行与八个问题](#2-原始实现原项目-348-行与八个问题)
- [3. 设计动机：访问者模式到底解决了什么](#3-设计动机访问者模式到底解决了什么)
- [4. 设计推导：从 if/else 推到 26 个文件](#4-设计推导从-ifelse-推到-26-个文件)
- [5. 代码解析：每个文件为什么存在](#5-代码解析每个文件为什么存在)
- [6. 执行流程：八段 Demo 的真实运行结果](#6-执行流程八段-demo-的真实运行结果)
- [7. 扩展对比：新需求来了改哪里](#7-扩展对比新需求来了改哪里)
- [8. 优缺点与常见误区](#8-优缺点与常见误区)
- [9. 面试与自测](#9-面试与自测)
- [附录 A：文件清单](#附录-a文件清单)
- [附录 B：运行方式与验证记录](#附录-b运行方式与验证记录)
- [附录 C：相关模式交叉索引](#附录-c相关模式交叉索引)
- [附录 D：原项目八个问题对照表](#附录-d原项目八个问题对照表)
- [附录 E：概念速查](#附录-e概念速查)
- [附录 F：版本与工具链](#附录-f版本与工具链)

---

## 1. 现实案例：同一份文档，四种操作

### 1.1 说人话版：内容团队的一堆文档，被四个部门同时盯上

一家做在线课程的公司，所有课程资料都存成三种文档：

- **PDF**：主要是课件，看重页数；
- **Word**：主要是讲义，看重字数；
- **表格**：课程排期、价格表，看重行列结构。

突然有一天，四个需求同时压过来：

1. **运营**要把文档导出成 HTML，挂到网页上；
2. **搜索**要把文档转成纯文本，喂给索引；
3. **数据**要统计文档数量、总页数、总字数、单元格数；
4. **合规**要扫描敏感词，输出一份审计清单。

如果你按最直觉的方式写——在每个文档类里加四个方法：

```java
class PdfDocument {
    String toHtml()        { ... }
    String toPlainText()   { ... }
    Stats  statistics()    { ... }
    List<String> audit()   { ... }
}
```

那么**每加一个操作，就要打开三个类改一遍**。类里的方法会像滚雪球一样增长，
PDF 类最终会同时知道 HTML 标签、搜索引擎分词规则、统计口径和合规名单——
它只是一个文档，凭什么知道这些？

访问者模式给出的答案是：

> **把"操作"从"数据"里搬出去。**
> 文档只保留自己的数据和一个 `accept(visitor)`；
> 每个操作各自独立成一个访问者类。

于是 4 个操作 = 4 个访问者类，3 种文档 = 3 个元素类，
`4 × 3 = 12` 个 `visit` 方法被分散在 4 个文件里，而不是堆在 3 个文档类里。

### 1.2 三个必须回答的问题

| 问题 | 直觉答案 | 真实答案 |
|---|---|---|
| 为什么不能把导出逻辑写在文档类里？ | 能跑，但越写越乱 | 会同时引入 HTML、统计、审计三套依赖，类变成"知道一切"的上帝类；且每加一种操作都要改所有文档类，违反开闭原则 |
| 为什么不能用一个 `switch (doc.type())` 集中处理？ | 看起来也挺集中 | 可以，而且类型只有 3 种时它更简单。**访问者的价值在于"类型封闭 + 操作开放"**，并且能把编译期检查变成"漏实现一个类型就编译不过" |
| 为了一种新操作就新建一个类，值得吗？ | 类会不会太多 | 一个操作通常对应一个团队、一个发布节奏、一份测试。类的数量换来的是**边界**：合规团队改合规访问者，不会碰导出逻辑 |

### 1.3 一个真实事故：报价单上出现了 46.400000000000006

原项目购物车演示里有这样一行输出（第 5 行，逐字）：

```text
- 打折商品: 《设计模式》, 价格: 46.400000000000006
```

顾客看到的报价是「46.400000000000006 元」。

根因不是访问者模式，而是金额用了 `double`：`58.0 * 0.8` 在二进制浮点里
无法精确表示，误差直接进了用户界面。这个问题在原项目里真实存在，
探针 V6 用 `%.17g` 把它固定成了证据：

```text
58.0 * 0.8      = 46.400000000000006
99.0+d+6.0      = 151.40000000000000
```

本书把它放在第 1 节，是想先说明一件事：

> **设计模式解决的是"结构"问题，不解决"类型"问题。**
> 一个结构再漂亮的访问者，只要金额字段还是 `double`，
> 用户看到的仍然是 46.400000000000006。

后面第 2 节的问题清单会把这类"和模式无关、但和交付质量有关"的缺陷单独标注出来。

### 1.4 本项目的示例：一个文档包，一辆购物车

重构后的示例保留了原项目的两条线，并把它们做成两个子包：

```text
visitor/
├── Client.java                     8 段 Demo，两个子包的共同入口
├── document/                       文档线：3 种元素 + 4 种操作
│   ├── Document.java               密封接口，permits 三种文档
│   ├── DocumentVisitor.java        泛型访问者，3 个 visit 返回 R
│   ├── PdfDocument.java            PDF：页数 + 正文
│   ├── WordDocument.java           Word：正文
│   ├── SpreadsheetDocument.java    表格：不可变二维表
│   ├── HtmlExportVisitor.java      操作 A：导出 HTML 片段
│   ├── PlainTextVisitor.java       操作 B：导出纯文本
│   ├── StatisticsVisitor.java      操作 C：统计一份文档的贡献
│   ├── DocumentDescriber.java      操作 D：Client 里的私有内部类
│   ├── DocumentBundle.java         对象结构：批量 accept 与折叠
│   ├── DocumentStatistics.java     可相加的统计结果
│   ├── HtmlFragment.java           转义后的小段 HTML
│   └── DocumentText.java           包内校验与表格工具
└── pricing/                        购物车线：3 种商品 + 3 种操作
    ├── CartItem.java               密封接口，permits 三种商品
    ├── CartVisitor.java            泛型访问者，3 个 visit 返回 R
    ├── RegularItem.java            原价商品
    ├── DiscountItem.java           折扣商品
    ├── BuyOneGetOneItem.java       买一赠一
    ├── PricingVisitor.java         操作 A：逐行明细
    ├── ReceiptVisitor.java         操作 B：小票文本
    ├── SavingsVisitor.java         操作 C：只算优惠总额
    ├── ShoppingCart.java           对象结构
    ├── CartSummary.java            可汇总的结果
    ├── LineResult.java             单行结果
    ├── Money.java                  整数分金额
    └── PricingText.java            包内校验工具
```

文档线回答「**操作多、类型少**」时访问者怎么用；
购物车线回答「**结果要被汇总**」时 `void visit` 为什么不够用。
两条线共用同一套模式骨架，但业务含义完全不同——这正是示例该有的样子。

### 1.5 双轨速览：同一件事的两种说法

| 说人话 | 专业术语 | 在本项目里是谁 |
|---|---|---|
| 能被访问的东西 | Element（元素） | `Document`、`CartItem` |
| 具体是哪一种东西 | ConcreteElement | `PdfDocument`、`DiscountItem` 等 |
| 交出自己的那一下 | `accept(visitor)` | 每个元素唯一的模式方法 |
| 干活的工具 | Visitor（访问者） | `HtmlExportVisitor`、`PricingVisitor` 等 |
| 工具对每种东西的处理 | `visit(ConcreteElement)` | 每个访问者的 3 个重载 |
| 一堆元素的集合 | ObjectStructure（对象结构） | `DocumentBundle`、`ShoppingCart` |
| 一次调用分派两次 | Double Dispatch（双重分派） | `element.accept(v)` → `v.visit(element)` |

---

## 2. 原始实现：原项目 348 行与八个问题

### 2.1 十六个文件，两个互不相干的示例

原项目的访问者示例放在两个目录里，彼此没有任何代码复用：

| 目录 | 文件数 | 行数 | 内容 |
|---|---:|---:|---|
| `BehaviorType/VisitorPattern/` | 7 | 141 | 文档导出 HTML：`Document` + `HtmlExportVisitor` |
| `BehaviorType/VisitorPattern/CalculatePrice/` | 9 | 207 | 购物车计价：`CartItem` + `ShoppingCartVisitor` |
| **合计** | **16** | **348** | 两套几乎相同的访问者骨架 |

有意思的是：**同一个项目里写了两遍访问者模式，却没有抽出任何公共部分**。
这不是"示例要独立"能解释的——两份 `accept` 的写法一字不差，
说明作者知道骨架长什么样，只是复制了一遍。

### 2.2 完整源码（省略了每份文件顶部 5 行 `@ClassName/@Author` 注释）

**文档线：接口与元素**

```java
// Document.java
public interface Document {
    /**
     * 接受访问者的访问
     *
     * @param visitor 访问者
     */
    void accept(DocumentVisitor visitor);
}

// DocumentVisitor.java
public interface DocumentVisitor {
    void visit(PDFDocument pdf);
    void visit(WordDocument word);
    void visit(ExcelDocument excel);
}

// PDFDocument.java
public record PDFDocument(String content) implements Document {
    @Override
    public void accept(DocumentVisitor visitor) {
        visitor.visit(this);
    }
}

// WordDocument.java
public record WordDocument(String content) implements Document {
    @Override
    public void accept(DocumentVisitor visitor) {
        visitor.visit(this);
    }
}

// ExcelDocument.java
public record ExcelDocument(String[][] table) implements Document {
    @Override
    public void accept(DocumentVisitor visitor) {
        visitor.visit(this);
    }
}
```

**文档线：唯一的访问者**

```java
// HtmlExportVisitor.java
public class HtmlExportVisitor implements DocumentVisitor {
    @Override
    public void visit(PDFDocument pdf) {
        System.out.println("<html><body><h1>PDF 内容</h1><p>" + pdf.content() + "</p></body></html>");
    }

    @Override
    public void visit(WordDocument word) {
        System.out.println("<html><body><h1>Word 内容</h1><p>" + word.content() + "</p></body></html>");
    }

    @Override
    public void visit(ExcelDocument excel) {
        System.out.println("<html><body><h1>Excel 内容</h1><table border='1'>");
        for (String[] row : excel.table()) {
            System.out.print("<tr>");
            for (String cell : row) {
                System.out.print("<td>" + cell + "</td>");
            }
            System.out.println("</tr>");
        }
        System.out.println("</table></body></html>");
    }
}
```

**文档线：客户端**

```java
// Client.java
public class Client {
    public static void main(String[] args) {
        Document pdf = new PDFDocument("这是 PDF 文件的内容");
        Document word = new WordDocument("这是 Word 文档的内容");
        Document excel = new ExcelDocument(new String[][] {
                {"姓名", "成绩"},
                {"鱼皮", "90"},
                {"Yes哥", "95"}
        });

        DocumentVisitor htmlExporter = new HtmlExportVisitor();

        pdf.accept(htmlExporter);
        word.accept(htmlExporter);
        excel.accept(htmlExporter);
    }
}
```

**购物车线：接口与元素**

```java
// CartItem.java
public interface CartItem {
    // 这是关键！接受一个访问者，并把“自己”交给他
    void accept(ShoppingCartVisitor visitor);
}

// ShoppingCartVisitor.java
public interface ShoppingCartVisitor {
    // 必须为每一种商品类型都提供一个 visit 方法
    void visit(NormalItem item);
    void visit(DiscountItem item);
    void visit(BogofItem item);
    double getTotalPrice(); // 拿到计算结果
}

// NormalItem.java
public record NormalItem(String name, double price) implements CartItem {
    @Override
    public void accept(ShoppingCartVisitor visitor) {
        // 我把自己交给访问者，访问者知道该调用哪个 visit 方法
        visitor.visit(this);
    }
}

// DiscountItem.java
public record DiscountItem(String name, double price, double discountRate) implements CartItem {
    @Override
    public void accept(ShoppingCartVisitor visitor) {
        visitor.visit(this);
    }
}

// BogofItem.java
public record BogofItem(String name, double price, int quantity) implements CartItem {
    @Override
    public void accept(ShoppingCartVisitor visitor) {
        visitor.visit(this);
    }
}
```

**购物车线：两个访问者**

```java
// PriceCalculatorVisitor.java
public class PriceCalculatorVisitor implements ShoppingCartVisitor {
    private double totalPrice = 0;

    @Override
    public void visit(NormalItem item) {
        System.out.println("计算普通商品: " + item.name());
        totalPrice += item.price();
    }

    @Override
    public void visit(DiscountItem item) {
        System.out.println("计算打折商品: " + item.name());
        totalPrice += item.price() * item.discountRate();
    }

    @Override
    public void visit(BogofItem item) {
        System.out.println("计算买一赠一商品: " + item.name());
        int quantity = item.quantity();
        totalPrice += ( quantity / 2 + quantity % 2) * item.price();
    }

    @Override
    public double getTotalPrice() {
        return totalPrice;
    }
}

// PrintListVisitor.java
public class PrintListVisitor implements ShoppingCartVisitor {
    @Override
    public void visit(NormalItem item) {
        System.out.println("- 普通商品: " + item.name() + ", 价格: " + item.price());
    }

    @Override
    public void visit(DiscountItem item) {
        System.out.println("- 打折商品: " + item.name() + ", 价格: " + item.price() * item.discountRate());
    }

    @Override
    public void visit(BogofItem item) {
        System.out.println("- 买一赠一商品: " + item.name() + ", 价格: " + (item.quantity() / 2 + item.quantity() % 2) * item.price());
    }

    @Override
    public double getTotalPrice() {
        return 0;
    }
}
```

**购物车线：对象结构与客户端**

```java
// ShoppingCart.java
public class ShoppingCart {
    private final List<CartItem> items = new ArrayList<>();

    public void addItem(CartItem item) {
        items.add(item);
    }

    // 这是发起“主动访问”的地方！
    public void accept(ShoppingCartVisitor visitor) {
        for (CartItem item : items) {
            // 让每个商品都去接受访问
            item.accept(visitor);
        }
    }
}

// Client.java
public class Client {
    public static void main(String[] args) {
        // 1. 准备一个购物车
        ShoppingCart cart = new ShoppingCart();
        cart.addItem(new NormalItem("《深入理解计算机系统》", 99.0));
        // 八折
        cart.addItem(new DiscountItem("《设计模式》", 58.0, 0.8));
        // 买三，实际上只算两瓶的钱
        cart.addItem(new BogofItem("可口可乐", 3.0, 3));

        // 2. 创建一个专精的“会计”
        PriceCalculatorVisitor priceCalculator = new PriceCalculatorVisitor();
        PrintListVisitor printListVisitor = new PrintListVisitor();

        // 3. 【主动且有感知地】让会计开始工作
        cart.accept(priceCalculator);
        cart.accept(printListVisitor);

        // 4. 获取结果
        System.out.println("----------------------------");
        System.out.println("购物车总价为: " + priceCalculator.getTotalPrice());
    }
}
```

### 2.3 真实输出：两段，逐字

编译 16 个文件时 `javac -Xlint:all` 零警告，两个入口都正常退出：

```text
$ java BehaviorType.VisitorPattern.Client
<html><body><h1>PDF 内容</h1><p>这是 PDF 文件的内容</p></body></html>
<html><body><h1>Word 内容</h1><p>这是 Word 文档的内容</p></body></html>
<html><body><h1>Excel 内容</h1><table border='1'>
<tr><td>姓名</td><td>成绩</td></tr>
<tr><td>鱼皮</td><td>90</td></tr>
<tr><td>Yes哥</td><td>95</td></tr>
</table></body></html>
```

```text
$ java BehaviorType.VisitorPattern.CalculatePrice.Client
计算普通商品: 《深入理解计算机系统》
计算打折商品: 《设计模式》
计算买一赠一商品: 可口可乐
- 普通商品: 《深入理解计算机系统》, 价格: 99.0
- 打折商品: 《设计模式》, 价格: 46.400000000000006
- 买一赠一商品: 可口可乐, 价格: 6.0
----------------------------
购物车总价为: 151.4
```

第一条 7 行 / 327 字节，SHA-256 `729D377B6397F906F867CC4B0F29CCBD36C15BEEC4DCC1D23E1E87C817683961`；
第二条 8 行 / 282 字节，SHA-256 `C7432672F45A6BDC5E104A24D4F8CA7391E6FB379CE0FD0779C5F3E2C0D3194B`。
**注意**：这两段是 GBK（代码页 936）编码的字节，不是 UTF-8 —— 原项目没有指定编码，
Windows 中文环境默认用 GBK。

### 2.4 问题一：`void visit` 让结果只能靠副作用带出（探针 V1）

`DocumentVisitor` 的三个方法都返回 `void`：

```text
方法签名: visit(...) -> void
方法签名: visit(...) -> void
方法签名: visit(...) -> void
```

于是 `HtmlExportVisitor` 只能 `System.out.println`。调用方拿不到 HTML 字符串，
无法做这些再普通不过的事：

- 断言「导出的 HTML 里一定包含 `<table>`」；
- 把三份文档的 HTML 拼成一页；
- 写进文件、发到消息队列、塞进 HTTP 响应。

**这不是"示例简化"，而是接口把路堵死了。** 只要 `visit` 是 void，
所有结果就只能通过打印、字段、ThreadLocal 这类副作用传出来，
而副作用既不能组合，也不能测试。

### 2.5 问题二：`ExcelDocument.table()` 把内部数组交了出去（探针 V2）

```java
public record ExcelDocument(String[][] table) implements Document { ... }
```

record 的访问器 `table()` 返回的是**数组本体**。外部拿到引用后可以随便改：

```text
篡改原始数组后，文档打印出:
<html><body><h1>Excel 内容</h1><table border='1'>
<tr><td>姓名</td><td>成绩</td></tr>
<tr><td>被外部改成了黑客</td><td>0</td></tr>
</table></body></html>
```

注意：**文档对象本身没变，`accept` 也没变**，变的只是当初传进去的那个数组。
访问者每次读到的都是"最新"的数据。这在并发环境下就是数据竞争，
在审计场景下就是"证据可以被事后修改"。

### 2.6 问题三：访问者不是无状态工具，而是会越用越大的累加器（探针 V3）

```java
public class PriceCalculatorVisitor implements ShoppingCartVisitor {
    private double totalPrice = 0;   // 状态长在访问者身上
    ...
}
```

同一个访问者对同一个元素 `accept` 两次：

```text
计算普通商品: 《深入理解计算机系统》
计算普通商品: 《深入理解计算机系统》
     第一次 accept 后总价 = 99.0
     第二次 accept 后总价 = 198.0
```

访问者是可复用的对象，但它的状态**从来不会被重置**。在 Web 应用里，
如果把这个访问者放进单例或缓存，第二个用户的购物车会从第一个用户的总额开始累加。

### 2.7 问题四：接口混入 `getTotalPrice()`，打印访问者被迫返回 0（探针 V4）

```java
public interface ShoppingCartVisitor {
    void visit(NormalItem item);
    void visit(DiscountItem item);
    void visit(BogofItem item);
    double getTotalPrice(); // 拿到计算结果
}
```

打印访问者根本不计算总价，但接口要求它必须实现：

```java
@Override
public double getTotalPrice() {
    return 0;
}
```

探针实测：

```text
- 普通商品: 《设计模式》, 价格: 108.0
     对打印访问者调用 getTotalPrice() = 0.0
```

`0.0` 在语法上完全合法，在语义上是**谎言**。更糟的是：调用方无法从类型上
判断一个访问者到底"能不能算总价"，只能凭文档或记忆。

### 2.8 问题五：买一赠一公式写了两份（探针 V5）

`PriceCalculatorVisitor` 第 33 行：

```java
totalPrice += ( quantity / 2 + quantity % 2) * item.price();
```

`PrintListVisitor` 第 21 行：

```java
System.out.println("- 买一赠一商品: " + item.name() + ", 价格: "
        + (item.quantity() / 2 + item.quantity() % 2) * item.price());
```

同一套规则，两个地方各写一遍。探针把数量 1-6 的两种算法结果逐一对比，
目前**全部一致**：

```text
quantity=1 计算访问者=3.0 打印访问者=3.0 一致=true
quantity=2 计算访问者=3.0 打印访问者=3.0 一致=true
quantity=3 计算访问者=6.0 打印访问者=6.0 一致=true
quantity=4 计算访问者=6.0 打印访问者=6.0 一致=true
quantity=5 计算访问者=9.0 打印访问者=9.0 一致=true
quantity=6 计算访问者=9.0 打印访问者=9.0 一致=true
```

"目前一致"正是最危险的状态：它靠人工同步维持。哪天运营说"买二赠一"，
有人只改了一个访问者，另一个就会在小票上印出不同的价格。

更深一层：**"买一赠一怎么计价"是本商品的属性，却写在访问者里。**
元素类反而不知道自己怎么算钱。

### 2.9 问题六：`double` 金额把浮点误差送到用户面前（探针 V6）

```text
58.0 * 0.8      = 46.400000000000006
99.0+d+6.0      = 151.40000000000000
用户看到的一行: 价格: 46.400000000000006
```

原项目 Client 的第 5 行输出就是证据，不是理论风险。金额必须用整数分
（`long cents`）或 `BigDecimal`，并且**舍入只能发生一次**（见第 5 节 `Money` 与 `DiscountItem`）。

### 2.10 问题七：不做入参校验，null 一直流到渲染层（探针 V7）

```text
null table -> NullPointerException @ BehaviorType.VisitorPattern.HtmlExportVisitor.visit:24
null cell 渲染结果:
<html><body><h1>Excel 内容</h1><table border='1'>
<tr><td>a</td><td>null</td></tr>
<tr><td>b</td><td>c</td></tr>
</table></body></html>
```

`new ExcelDocument(null)` 能构造成功，直到访问者遍历时才炸，
堆栈指向 `HtmlExportVisitor`——**离真正出错的地方隔了一层**。
`null` 单元格更隐蔽：不报错，直接渲染成字符串 `null`，页面看起来"有内容"。

### 2.11 问题八：`addItem(null)` 延迟爆炸（探针 V8）

```text
- 普通商品: 正常商品, 价格: 10.0
     accept 第 2 件时 -> NullPointerException @ BehaviorType.VisitorPattern.CalculatePrice.ShoppingCart:24
```

`addItem` 什么都没检查，`null` 被安静地放进 `List`。
调用链走远之后才在遍历时炸，报错位置离出错位置很远，
调用方也无从知道是哪一次 `addItem` 传了 `null`。

### 2.12 探针完整输出（53 行 / 3153 字节）

```text
[V1] 访问者接口的所有 visit 返回 void -> 结果只能靠副作用带出
     方法签名: visit(...) -> void
     方法签名: visit(...) -> void
     方法签名: visit(...) -> void
     影响: HtmlExportVisitor 只能 System.out.println，调用方拿不到生成的 HTML 字符串，无法在内存里断言。

[V2] ExcelDocument.table() 返回内部可变数组本体 -> 外部可篡改文档
     篡改原始数组后，文档打印出:
<html><body><h1>Excel 内容</h1><table border='1'>
<tr><td>姓名</td><td>成绩</td></tr>
<tr><td>被外部改成了黑客</td><td>0</td></tr>
</table></body></html>

[V3] 访问者内部可变累加器 -> 同一访问者第二次 accept 结果翻倍
计算普通商品: 《深入理解计算机系统》
计算普通商品: 《深入理解计算机系统》
     第一次 accept 后总价 = 99.0
     第二次 accept 后总价 = 198.0
     影响: 访问者不是「无状态工具」，而是一个会用坏的累加器；跨请求复用会算错钱。

[V4] 接口混入 getTotalPrice() -> 打印访问者被迫返回 0
- 普通商品: 《设计模式》, 价格: 108.0
     对打印访问者调用 getTotalPrice() = 0.0
     影响: 0.0 是一个语法上合法、语义上错误的返回值，调用方无法从类型上区分「能算总价的访问者」和「不能算的」。

[V5] 买一赠一公式在计算访问者与打印访问者中各写一份
     quantity=1 计算访问者=3.0 打印访问者=3.0 一致=true
     quantity=2 计算访问者=3.0 打印访问者=3.0 一致=true
     quantity=3 计算访问者=6.0 打印访问者=6.0 一致=true
     quantity=4 计算访问者=6.0 打印访问者=6.0 一致=true
     quantity=5 计算访问者=9.0 打印访问者=9.0 一致=true
     quantity=6 计算访问者=9.0 打印访问者=9.0 一致=true
     影响: 两处公式现在一致纯粹靠人工同步；「买一赠一」的规则细节写在两个访问者里，元素类反而不知道自己怎么计价。

[V6] 金额用 double -> 二进制浮点误差直接进入用户可见输出
     58.0 * 0.8      = 46.400000000000006
     99.0+d+6.0      = 151.40000000000000
     用户看到的一行: 价格: 46.400000000000006
     影响: 该误差已经在原项目 Client 的第 5 行输出里出现，不是理论风险。

[V7] ExcelDocument 不做任何入参校验 -> null 表格 NPE、null 单元格打印 null
<html><body><h1>Excel 内容</h1><table border='1'>
     null table -> NullPointerException @ BehaviorType.VisitorPattern.HtmlExportVisitor.visit:24
     null cell 渲染结果:
<html><body><h1>Excel 内容</h1><table border='1'>
<tr><td>a</td><td>null</td></tr>
<tr><td>b</td><td>c</td></tr>
</table></body></html>

[V8] ShoppingCart 允许 null 商品 -> 错误延迟到遍历时才暴露
- 普通商品: 正常商品, 价格: 10.0
     accept 第 2 件时 -> NullPointerException @ BehaviorType.VisitorPattern.CalculatePrice.ShoppingCart:24
     影响: addItem 通过，若干行之后才炸，报错位置离出错位置很远；调用方无从得知是哪一次 addItem 传了 null。
```

### 2.13 八个问题里，只有两个是"访问者模式"的问题

这一点必须说清楚，否则读者会误以为"用了访问者就会写出这些 bug"：

| 编号 | 问题 | 属于 | 说明 |
|---|---|---|---|
| V1 | `visit` 返回 void | **模式误用** | 访问者完全可以返回 `R`，原项目选择了 void |
| V4 | 接口混入 `getTotalPrice()` | **模式误用** | 把"某个访问者的私有结果"提升成"所有访问者的公共契约" |
| V3 | 访问者带可变累加器 | 实现缺陷 | 换成返回值 + 外部折叠就没有状态 |
| V5 | 公式复制两份 | 实现缺陷 | 公式应属于元素（商品），不属于访问者 |
| V2 | 数组引用泄漏 | 封装缺陷 | 与模式无关，属于对象不可变性 |
| V6 | `double` 金额 | 类型选择 | 与模式无关，属于金额表示 |
| V7 | 缺入参校验 | 健壮性 | 与模式无关 |
| V8 | 延迟 NPE | 健壮性 | 与模式无关 |

换句话说：**访问者模式本身只贡献了"类型封闭"这个约束**——
加一种文档类型要改所有访问者，这是它的代价；
其余六个问题，用不用访问者都应该修。

### 2.14 一个更根本的问题：示例只证明了"能跑"

文档线的 `Client` 是手写的三条 `accept`，购物车线的 `Client` 也是手写的三条 `addItem`。
两段代码都只能演示"访问者能跑"，无法回答：

- 加第四种文档时要改几个文件？
- 同一个元素被两个访问者访问，结果能不能比较？
- 一个访问者的结果能不能和另一个访问者的结果合并？

这三个问题，正是第 4 节设计推导的起点。

---

## 3. 设计动机：访问者模式到底解决了什么

### 3.1 本质：把"类型判断"从运行时搬到编译期

不用访问者时，处理"不同类型的文档"只能靠类型判断：

```java
for (Document doc : documents) {
    if (doc instanceof PdfDocument pdf) {
        html += renderPdf(pdf);
    } else if (doc instanceof WordDocument word) {
        html += renderWord(word);
    } else if (doc instanceof SpreadsheetDocument sheet) {
        html += renderSheet(sheet);
    }
}
```

这段代码有两个问题：

1. **漏掉一种类型不会报错**。新增 `MarkdownDocument` 后，所有 `if/else` 链条
   都会安静地跳过它，输出少一块，没人发现；
2. **每加一种操作就要再写一遍同样的 if/else 骨架**。

访问者把这两件事同时解决：

- 元素接口是 `sealed` 的，访问者必须为每一种元素实现一个 `visit`——
  **新增元素类型时，所有访问者都编译不过**，编译器替你列出待改清单；
- 每种操作的 if/else 被拆成一组重载方法，`accept` 的双重分派替你做类型判断，
  代码里不再出现 `instanceof`。

这就是"双重分派"的真正价值：**不是炫技，是把类型判断交给编译器和虚方法表。**

### 3.2 什么时候**不需要**访问者模式

访问者不是万能钥匙。以下情况它只会添乱：

| 场景 | 更合适的选择 | 原因 |
|---|---|---|
| 元素类型只有 1-2 种，且几乎不变 | 直接在元素上写方法 | 一个 `visit` 接口 + 一个实现类，收益抵不过文件数 |
| 操作只有 1-2 种，元素类型经常增加 | 策略模式 / 普通多态 | 加元素时不用改所有访问者，访问者反而成了负担 |
| 操作需要频繁访问元素内部私有状态 | 把逻辑放回元素 | 访问者只能看到公开访问器，硬塞会逼你开放内部细节 |
| 元素之间有复杂的状态流转 | 状态模式 | 访问者是无状态操作，不负责"下一步是谁" |
| 只是想把一堆对象转成另一种格式 | 迭代器 + 转换函数 | 不需要双重分派，一个 `map` 就够了 |

一条更实用的判据：

> **元素类型稳定、操作种类会持续增加 → 访问者；
> 元素类型会持续增加、操作稳定 → 普通多态（或策略）。**

### 3.3 访问者 vs 策略 vs 迭代器 vs 装饰器

| 维度 | 访问者 | 策略 | 迭代器 | 装饰器 |
|---|---|---|---|---|
| 解决的问题 | 在不改元素的前提下加操作 | 替换一个算法 | 遍历聚合而不暴露内部结构 | 动态叠加职责 |
| 关键动作 | `element.accept(visitor)` 双重分派 | `context.setStrategy(s)` | `iterator.next()` | `new Decorator(component)` |
| 谁在变化 | 操作（访问者）增加 | 算法实现增加 | 遍历方式增加 | 职责组合增加 |
| 是否要知道元素类型 | **是**，每个 `visit` 对应一种 | 否 | 否 | 否 |
| 加新元素类型的代价 | 改所有访问者 | 无 | 无 | 无 |
| 典型误用 | 用访问者替代所有多态 | 策略里塞状态 | 迭代器里做业务 | 装饰器套五层 |

最容易混的是**访问者与策略**：两者都"把行为抽出去"。
区别在于——策略是**一个**算法替换**一个**上下文的行为；
访问者是**一组**操作分别处理**一组**元素类型，并且需要双重分派才能落地。
策略不需要知道"我处理的是哪种具体类型"，访问者的每一条 `visit` 恰恰就是在回答这个问题。

### 3.4 一句话判据

> 当你发现自己在写第 3 个 `instanceof` 分支，而且这些分支还会继续增加时，
> 就该考虑访问者；当你发现元素类型每个月都在加时，就该考虑放弃它。

### 3.5 本项目在八个问题上各做什么

| 原问题 | 本项目的处理 | 在哪能看到 |
|---|---|---|
| V1 void | `DocumentVisitor<R>` / `CartVisitor<R>` 的 `visit` 返回 `R` | 第 5.2 节、Demo 4 |
| V2 数组泄漏 | 表格改 `List<List<String>>`，构造时深拷贝成不可变 | 第 5.5 节、Demo 2 |
| V3 可变累加器 | 访问者全部无状态，聚合交给 `acceptAndFold` / `CartSummary.of` | 第 5.11 节、Demo 3 |
| V4 接口污染 | 删掉 `getTotalPrice()`；"能算总价"由返回类型 `CartSummary` 表达 | 第 5.14 节、第 5.21 节、Demo 5 |
| V5 公式重复 | 买一赠一公式收敛成 `BuyOneGetOneItem.billableUnits()` 一份 | 第 5.17 节、Demo 8 |
| V6 double | `Money` 用整数分，`format()` 手工拼接不受 Locale 影响 | 第 5.18 节、Demo 5 |
| V7 缺校验 | 所有元素在构造期校验，错误消息带行列号 | 第 5.3 节、第 5.5 节、第 5.19 节、Demo 7 |
| V8 延迟 NPE | `ShoppingCart.add` 立即拒绝 null，消息带"第 N 次" | 第 5.22 节、Demo 7 |

下一节从"如果不用访问者会怎样"开始，一步一步推导出这 26 个文件。

---

## 4. 设计推导：从 if/else 推到 26 个文件

这一节不贴最终代码，而是按「遇到什么问题 → 有哪几种选择 → 为什么选这个 → 代价是什么」
的顺序，把 26 个文件一个一个"逼"出来。读完之后，你应该能在不看源码的情况下
复述每个类存在的理由。

### 4.1 第一个决定：元素接口要不要 `sealed`

**问题**：`Document` 是一个普通 `interface` 时，编译器不知道"到底有几种文档"。
新增一种文档后，访问者少实现一个 `visit`，代码照样编译、照样运行，
只在运行时静默跳过。

**选择**：

| 方案 | 效果 |
|---|---|
| 普通 `interface` | 加类型无感；漏实现不报错 |
| `enum DocumentType` + 一个万能类 | 类型有穷举，但字段全挤在一个类里，PDF/Word/表格的属性互相污染 |
| `sealed interface` + 每种文档一个 record | 类型有穷举，字段各自独立，`permits` 就是清单 |

**决定**：`sealed interface Document permits PdfDocument, WordDocument, SpreadsheetDocument`。

**代价**：新增文档类型必须改 `Document` 的 `permits` 子句，
**并且所有访问者都会编译失败**。这个"编译失败"不是副作用，而是设计目标：
它把"你还有 3 个访问者没适配新类型"这件事，从运行时的静默错误变成了构建失败。

购物车线的 `CartItem` 用同一套写法，`permits RegularItem, DiscountItem, BuyOneGetOneItem`。

### 4.2 第二个决定：元素用 `record` 还是普通类

**问题**：元素是"数据的载体"，还是"带行为的对象"？

**决定**：用 `record`，字段只读，构造期校验。

理由有三条：

1. 访问者模式的前提是**元素足够稳定**。元素一旦可变，访问者拿到的数据在遍历
   途中可能被别的线程改掉（原项目 V2 就是活证据）；
2. `record` 自带 `equals/hashCode/toString`，Demo 3 才能用
   `两次导出结果相等 = true` 直接验证"访问者无状态"；
3. 数据类不该有几十个 setter。需要"改一个字段"时，`with` 风格派生新对象即可，
   但本示例里没有这种需求，所以不预先设计。

**代价**：`record` 的访问器会把字段交出去。如果字段本身可变（数组、`List`、`Date`），
就必须在构造器里做防御性拷贝——这正是第 5.5 节 `SpreadsheetDocument` 要解决的问题。

### 4.3 第三个决定：`accept` 返回 `void` 还是泛型 `R`

**问题**：原项目 `void accept(DocumentVisitor)` 让所有结果只能靠 `System.out.println`。
如果改成 `String accept(...)`，统计访问者就没法返回 `DocumentStatistics`；
如果返回 `Object`，调用方每次都要强转，类型安全荡然无存。

**决定**：

```java
public interface DocumentVisitor<R> {
    R visit(PdfDocument document);
    R visit(WordDocument document);
    R visit(SpreadsheetDocument document);
}

public sealed interface Document permits PdfDocument, WordDocument, SpreadsheetDocument {
    String title();
    <R> R accept(DocumentVisitor<R> visitor);
}
```

`R` 由**访问者**决定，而不是由元素决定。同一个 `PdfDocument`：

- 交给 `HtmlExportVisitor`（`DocumentVisitor<HtmlFragment>`）得到 `HtmlFragment`；
- 交给 `PlainTextVisitor`（`DocumentVisitor<String>`）得到 `String`；
- 交给 `StatisticsVisitor`（`DocumentVisitor<DocumentStatistics>`）得到统计值。

**代价**：`R` 是泛型，无法在元素里对结果做任何假设——元素也确实不该假设。
另外，一个访问者只能有一种返回类型；如果某个访问者想同时返回"HTML + 警告列表"，
需要自己定义一个结果 record（这正是 `HtmlFragment`、`DocumentStatistics` 这类类的由来）。

### 4.4 第四个决定：访问者接口里能不能放 `getTotalPrice()`

**问题**：原项目把"总价"放进了 `ShoppingCartVisitor`，导致打印访问者被迫 `return 0`。

**选择**：

| 方案 | 问题 |
|---|---|
| 接口里放 `getTotalPrice()` | 每个访问者都必须实现，不计算的只能撒谎（V4） |
| 拆成 `PricingVisitor extends ShoppingCartVisitor` | 接口层级变深，`accept` 参数类型怎么写都别扭 |
| 让 `visit` 返回结果，聚合交给调用方 | 接口只描述"一次访问产出什么"，不描述"多次访问怎么合并" |

**决定**：第三种。`CartVisitor<R>` 只有三个 `visit`，没有 `getTotalPrice()`。
"总价"是 `CartSummary`（`PricingVisitor` 返回 `LineResult`，再 `CartSummary.of(lines)`）。

**代价**：调用方要多写一步聚合。但这一步换来的是：

- `SavingsVisitor` 只返回 `Money`，不需要假装自己知道总价；
- `ReceiptVisitor` 只返回 `String`，不需要返回一个它根本不算的数字；
- 聚合逻辑（`CartSummary.of`）独立可测，且能被任何 `List<LineResult>` 复用。

### 4.5 第五个决定：访问者要不要持有状态

**问题**：原项目 `PriceCalculatorVisitor` 的 `totalPrice` 是字段，
同一个访问者用两次就从 99 累加到 198（V3）。

**决定**：**所有访问者都是无状态的 `final class`，每次 `visit` 只返回"这一次访问的贡献"。**

```java
// StatisticsVisitor：返回"这一份文档的贡献"，不累加
@Override
public DocumentStatistics visit(PdfDocument document) {
    return new DocumentStatistics(1, 1, 0, 0, document.pageCount(), document.bodyLength(), 0);
}
```

累加交给对象结构：

```java
// DocumentBundle
public <R> R acceptAndFold(DocumentVisitor<R> visitor, R identity, BinaryOperator<R> folder) {
    R accumulator = identity;
    for (Document document : documents) {
        accumulator = folder.apply(accumulator, document.accept(visitor));
    }
    return accumulator;
}
```

**代价**：调用方要提供"零值"和"合并函数"。但这两个东西都是纯函数，
可以单独测试，也可以换一种合并策略（求和、取最大值、拼字符串）而不用改访问者。

**为什么不用 `void` + 访问者内部 `List` 收集？**
因为那样访问者又变成有状态的了；而且"收集"这件事和"访问"是两件事，
混在一起会让访问者既要知道业务，又要管容器。

### 4.6 第六个决定：结果类型要不要支持"相加"

**问题**：统计一份文档和统计十份文档，是两种不同的操作。
如果 `DocumentStatistics` 只是一个普通 record，`acceptAndFold` 就没法写。

**决定**：给结果类型提供三个东西：

```java
public static DocumentStatistics empty();                       // 零值
public DocumentStatistics plus(DocumentStatistics other);       // 合并
public String summary();                                        // 展示
```

`plus` 里每个字段各加各的，**不引入任何业务判断**。这样"3 份文档 / 395 页 / 100 字 / 12 单元格"
就是 3 次 `plus` 的结果，而不是某个访问者内部循环累加的副作用。

**代价**：多写三个方法。收益是折叠逻辑变成了 `reduce(empty, plus)`，
一行就能表达，且天然可并行（虽然本示例没并行）。

### 4.7 第七个决定：买一赠一的公式放在哪

**问题**：公式写在访问者里，就有两份（V5）；写在元素里，访问者只负责展示。

**决定**：公式回到元素——`BuyOneGetOneItem.billableUnits()`：

```java
public int billableUnits() {
    return (quantity + 1) / 2;   // 1→1, 2→1, 3→2, 4→2, 5→3, 6→3
}
```

原公式是 `quantity / 2 + quantity % 2`。两者等价吗？逐项验证：

| quantity | 原式 `q/2 + q%2` | 新式 `(q+1)/2` |
|---:|---:|---:|
| 1 | 0+1=1 | 1 |
| 2 | 1+0=1 | 1 |
| 3 | 1+1=2 | 2 |
| 4 | 2+0=2 | 2 |
| 5 | 2+1=3 | 3 |
| 6 | 3+0=3 | 3 |

等价。但新式只有一个运算符，且语义直白："每两件收一件的钱，奇数时多收一件"。
三个访问者都调它，公式只有一份。

**代价**：元素要知道"自己怎么计价"——这本来就是商品的属性，不是越权。

### 4.8 第八个决定：金额用什么类型

**问题**：`double` 会产出 46.400000000000006（V6）。

**选择**：

| 方案 | 优点 | 缺点 |
|---|---|---|
| `double` | 简单 | 二进制误差进用户界面 |
| `BigDecimal` | 精确、支持任意精度 | 每次运算都要给 `RoundingMode`，容易忘；`equals` 还区分 `1.0` 和 `1.00` |
| `long cents` | 精确、运算快、比较直接用 `==` | 只能表示到分；乘法要管舍入 |

**决定**：`record Money(long cents)`。理由：电商金额精确到分足够；
`long` 范围 ±9.2e16 分，即 ±9.2e14 元，远超业务需要；
所有运算走 `Math.addExact/subtractExact/multiplyExact`，溢出立刻抛错。

**关键细节：舍入只能发生一次。** `DiscountItem` 先乘数量再打折：

```java
public Money beforeDiscount() { return unitPrice.multiply(quantity); }
@Override
public Money subtotal() { return beforeDiscount().times(rate); }   // 只在这里舍入一次
```

反例：3.33 元 × 10 件 × 8 折。先乘数量得 33.30，再打折得 26.64；
若先打折得 2.664 → 舍入成 2.66，再乘 10 得 26.60。**差 4 分钱**。
演示里的每一行小计都必须能回答"你在哪一步舍入"。

### 4.9 第九个决定：谁来负责遍历

**问题**：原项目 `ShoppingCart.accept` 遍历商品，`Client` 手动对三份文档逐个 `accept`。
两种做法都能跑，但后者把"对象结构"的职责丢给了调用方。

**决定**：对象结构负责遍历，并提供两个层次的能力：

```java
// 文档：批量 + 折叠
public <R> List<R> acceptAll(DocumentVisitor<R> visitor)
public <R> R acceptAndFold(DocumentVisitor<R> visitor, R identity, BinaryOperator<R> folder)

// 购物车：流式添加 + 批量访问 + 一步汇总
public ShoppingCart add(CartItem item)
public <R> List<R> acceptAll(CartVisitor<R> visitor)
public CartSummary summarize()
```

**代价**：对象结构要知道"怎么遍历自己"。这是它的本职，不是越权。
真正的收益是：如果以后文档从 `List` 换成树，只改 `DocumentBundle`，访问者一行不动。

### 4.10 第十个决定：校验逻辑放哪

**问题**：三个文档类、三个商品类都要校验 `title`/`name`/`price`/`quantity`。
如果每个类各写一遍 `if (x == null) throw ...`，消息风格很快就会不一致。

**决定**：包内私有工具类 `DocumentText` / `PricingText`，只放静态方法，不继承。

```java
final class PricingText {
    static String requireName(String name) { ... }
    static Money requirePrice(Money price) { ... }
    static int requireQuantity(int quantity) { ... }
    static BigDecimal requireRate(BigDecimal rate) { ... }
}
```

**为什么不抽一个 `AbstractDocument` 基类？**
因为校验是**静态的、无状态的**，不需要继承。继承会带来构造器链、
`protected` 字段、子类必须 `super(...)` 的约束，而收益只是省一次调用。
"优先组合，避免为了复用而继承"在这里是具体的：`record` 本来就不能继承类。

**代价**：两个工具类各有 4-5 个方法，看起来"多了一个文件"。
但错误消息的风格统一了（Demo 7 的 7 条消息格式一致），这比少一个文件重要。

### 4.11 第十一个决定：新增一种操作，要改几个文件

**验证方式**：Demo 1 里有一个 `DocumentDescriber`，它是 `Client` 的私有内部类，
实现 `DocumentVisitor<String>`，输出"PDF 《书名》 395 页"这样的描述。

它没有改任何元素类、没有改 `DocumentVisitor`、没有改 `DocumentBundle`。
**新增一种操作 = 新增一个类**，这就是访问者模式承诺的开闭原则。

**但要诚实**：新增一种**元素类型**（比如 `MarkdownDocument`）要改的
是 3 个访问者 + `Document` 的 `permits` + 可能还有 `DocumentText`，
一共 4-5 个文件。这是访问者模式**必然的**代价，不存在"两头都开"的设计。

### 4.12 第十二个决定：两条业务线要不要抽公共泛型

文档线和购物车线的骨架几乎一样：

```java
interface Visitor<R> { R visit(A a); R visit(B b); }
interface Element { <R> R accept(Visitor<R> visitor); }
```

理论上可以抽成 `Visitor<R, E1, E2, E3>` 或者用 `sealed interface Element permits ...` 的公共父接口。

**决定**：**不抽**。理由：

1. 泛型参数一多，`accept` 的签名会变得难以阅读；
2. 两条线的元素没有任何业务共性，唯一的共性是"都被访问"——这个共性太薄；
3. 示例的价值在于"两条线各自完整"，而不是"抽出一个框架"。

这是"不为套用模式而过度设计"的具体体现：**模式是解决问题的工具，不是要复用的产品。**

### 4.13 既然 Java 21 有 switch 模式匹配，还要访问者吗

这是一个必须正面回答的问题。JDK 21 起可以写：

```java
static String describe(Document document) {
    return switch (document) {
        case PdfDocument pdf -> "PDF " + pdf.title();
        case WordDocument word -> "Word " + word.title();
        case SpreadsheetDocument sheet -> "表格 " + sheet.title();
    };
}
```

`sealed` + 模式匹配已经能做到**编译期穷尽**，而且不用写 `accept`。
那访问者还有什么用？

| 维度 | switch 模式匹配 | 访问者 |
|---|---|---|
| 穷尽检查 | 有（sealed） | 有（每个 visit 都必须实现） |
| 新增元素类型 | 改所有 switch | 改所有访问者 |
| 新增操作 | 加一个新方法（可能很长） | 加一个新类（独立文件、独立测试） |
| 操作需要独立发布 | 难（都挤在一个类里） | 容易（一个类一个 jar/模块） |
| 操作需要携带配置 | 方法参数越加越多 | 访问者的字段就是配置 |
| 结果类型 | 所有分支返回同一类型 | 每个访问者可以有不同 `R` |
| 可组合性 | 折叠要另写 | `acceptAndFold` 天然支持 |

**本项目为什么仍然用访问者？**

1. 操作会持续增加，而且每个操作都要独立测试（HTML 转义、统计口径、合规扫描）；
2. `visit` 的返回类型各不相同（`HtmlFragment` / `String` / `DocumentStatistics`），
   switch 版本要么返回 `Object`，要么每个操作写一个 switch 方法；
3. 这是教学项目，需要把双重分派讲清楚。

**如果只有 2 种元素、1 种操作，switch 模式匹配更简单。**
第 8 节的"常见误区"会把这条写进去。

---

## 5. 代码解析：每个文件为什么存在

本节按"骨架 → 元素 → 工具 → 访问者 → 对象结构 → 客户端"的顺序逐个说明。
每个类回答三个问题：**它负责什么？关键实现为什么这么写？删掉或合并会怎样？**

### 5.1 `Document`：元素接口与它的 `permits` 清单

```java
public sealed interface Document permits PdfDocument, WordDocument, SpreadsheetDocument {
    String title();
    <R> R accept(DocumentVisitor<R> visitor);
}
```

- `title()` 是三种文档唯一的公共属性，提取到接口是为了让
  `HtmlExportVisitor` 之类的访问者能统一拿到标题，不必为每种类型写一遍。
- `accept` 是模式方法：它不做业务，只把 `this` 交给访问者。
  **元素的具体类型在这一刻被"固化"进编译期**——`visitor.visit(this)` 里
  `this` 的静态类型就是 `PdfDocument`，所以重载解析能选中正确的方法。
- 删掉 `Document` 接口：三个 record 无法被统一存放，`DocumentBundle`
  就只能写三个重载，访问者的 `accept` 也就失去了多态入口。

### 5.2 `DocumentVisitor<R>`：三个重载就是三种类型

```java
public interface DocumentVisitor<R> {
    R visit(PdfDocument document);
    R visit(WordDocument document);
    R visit(SpreadsheetDocument document);
}
```

- 三个方法同名不同参，是**重载**，不是三个不同名字的方法。
  这样 `visitor.visit(this)` 才能靠 `this` 的静态类型选中正确分支。
- 若改成 `visitPdf/visitWord/visitSheet`，元素里就要写
  `visitor.visitPdf(this)`——元素必须知道自己是什么类型，
  双重分派退化成"元素自己 if/else"，模式的意义消失。
- 删掉这个接口：`accept` 的参数类型就只能是 `Object`，
  访问者内部的类型判断又回来了。

### 5.3 `PdfDocument`：三个字段，两处校验

```java
public record PdfDocument(String title, int pageCount, String body) implements Document {
    public PdfDocument {
        title = DocumentText.requireText(title, "title");
        body = DocumentText.requireText(body, "body");
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount 至少为 1，实际 " + pageCount);
        }
    }

    public int bodyLength() { return body.length(); }

    @Override
    public <R> R accept(DocumentVisitor<R> visitor) {
        if (visitor == null) { throw new IllegalArgumentException("visitor 不能为 null"); }
        return visitor.visit(this);
    }
}
```

- `pageCount >= 1`：0 页的 PDF 不存在，让它在构造期失败，
  比让统计结果出现 0 页更早暴露问题（Demo 2 有对应输出）。
- `accept` 里校验 `visitor` 非 null：`visitor.visit(this)` 在 visitor 为 null 时
  会抛 NPE，堆栈指向元素内部，与调用方传入 null 的位置隔了一层。
  提前检查能给出更准确的消息。
- `bodyLength()` 是给访问者用的派生属性。**它属于元素**，
  因为"正文有多长"是文档自己的事实，不是某个访问者的计算。

### 5.4 `WordDocument`：为什么它和 PDF 不合并

```java
public record WordDocument(String title, String body) implements Document {
    public WordDocument {
        title = DocumentText.requireText(title, "title");
        body = DocumentText.requireText(body, "body");
    }

    public int bodyLength() { return body.length(); }
    ...
}
```

两者都有 `title` 和 `body`，看起来可以合并成一个
`TextDocument(title, body, pageCount)`，用 `pageCount == 0` 表示 Word。

不合并的理由：

1. 访问者的每个 `visit` 对应一种**业务类型**，合并后
   `visit(TextDocument)` 内部又要判断"是不是 PDF"，类型判断回来了；
2. PDF 有页数、Word 没有，合并会让 Word 携带一个永远为 0 的字段，
   这正是"用 null/0 表示不存在"的坏味道；
3. 加第三种文本文档（Markdown）时，合并版会继续膨胀。

**判据**：字段可以共享，但**业务规则和访问分支**不同，就该分开。

### 5.5 `SpreadsheetDocument`：防御性拷贝与不可变二维表

```java
public record SpreadsheetDocument(String title, List<List<String>> rows) implements Document {
    public SpreadsheetDocument {
        title = DocumentText.requireText(title, "title");
        rows = DocumentText.normalizeTable(rows);   // 深拷贝 + 不可变 + 拒绝 null 单元格
    }

    public int rowCount() { return rows.size(); }
    public int columnCount() { return DocumentText.columnCount(rows); }
    public boolean isRectangular() { return DocumentText.isRectangular(rows); }
    public int cellCount() { ... }
}
```

- 原项目用 `String[][]`，`table()` 直接返回数组本体，外部改数组就能改文档（V2）。
- 这里换成 `List<List<String>>`，`normalizeTable` 做三件事：
  1. 拒绝 `null` 的 `rows`；
  2. 每一行 `List.copyOf` 成不可变，任一单元格为 `null` 就抛
     `第 X 行第 Y 列为 null`（行列都从 1 开始，给人看的）；
  3. 外层再 `List.copyOf`，得到不可变嵌套结构。
- **为什么不每次 `rows()` 时再拷贝？** 那样每次读取都是 O(n)，
  而构造只发生一次。不可变对象的标准做法是"构造时付一次钱，之后读取零成本"。
- `isRectangular()` 用于判断"参差表格"：`columnCount` 取第一行长度，
  参差时统计仍能工作，但展示层可以选择报警。这是"先保证不崩，再谈规则"。

### 5.6 `DocumentText`：包内工具类为什么不是基类

```java
final class DocumentText {
    static String requireText(String value, String field) {
        if (value == null) { throw new IllegalArgumentException(field + " 不能为 null"); }
        if (value.isBlank()) { throw new IllegalArgumentException(field + " 不能为空白字符串"); }
        return value;
    }

    static List<List<String>> normalizeTable(List<List<String>> rows) { ... }
    static int columnCount(List<List<String>> rows) { ... }
    static boolean isRectangular(List<List<String>> rows) { ... }
    static String escapeHtml(String raw) { ... }

    private DocumentText() { }
}
```

- 全部 `static`，无状态，不需要实例，也不需要继承。
- 包内可见（没有 `public`）：它是实现细节，不该出现在使用者的 IDE 补全里。
- `escapeHtml` 把 `& < > " '` 转成实体。**转义必须发生在拼接 HTML 的那一刻**，
  而不是在存储时——同一份正文导出纯文本时不需要转义。
- 删掉它：三个元素类各写一遍校验，两个 HTML 访问者各写一遍转义，
  消息格式和转义规则会慢慢分叉。

### 5.7 `HtmlFragment`：为什么返回值不是 `String`

```java
public record HtmlFragment(String value) {
    public HtmlFragment {
        if (value == null) { throw new IllegalArgumentException("value 不能为 null"); }
    }

    public static HtmlFragment empty() { return new HtmlFragment(""); }
    public HtmlFragment concat(HtmlFragment next) { ... }
    public static HtmlFragment join(List<HtmlFragment> fragments) { ... }
    public int length() { return value.length(); }
    public boolean isEmpty() { return value.isEmpty(); }
}
```

- 它只是一个 `String` 包装，但它让 `HtmlExportVisitor` 的返回类型**自解释**：
  `DocumentVisitor<HtmlFragment>` 一眼能看出"产出的是 HTML 片段"，
  而不是"某个字符串"。
- `concat`/`join` 让"把三份文档拼成一页"变成一行（Demo 4）。
  如果返回裸 `String`，拼接逻辑会散落在调用方。
- **不在这里做转义**：转义是 `HtmlExportVisitor` 的职责。
  如果 `HtmlFragment` 构造时自动转义，那么调用方传进来的已经是
  拼好的 `<section>` 标签也会被转义，整个页面就废了。
  **"在哪一层转义"必须唯一且明确。**

### 5.8 `HtmlExportVisitor`：一个操作，三个分支

```java
public final class HtmlExportVisitor implements DocumentVisitor<HtmlFragment> {
    private static final String TABLE_STYLE = "border='1' cellspacing='0' cellpadding='4'";

    @Override
    public HtmlFragment visit(PdfDocument document) {
        return new HtmlFragment("<section class='pdf'>\n"
                + "  <h2>" + DocumentText.escapeHtml(document.title()) + "</h2>\n"
                + "  <p class='meta'>PDF · " + document.pageCount() + " 页</p>\n"
                + "  <pre>" + DocumentText.escapeHtml(document.body()) + "</pre>\n"
                + "</section>\n");
    }
    ...
}
```

- 访问者是 `final` 且没有字段：它不需要被继承，也不需要状态。
- 转义只作用于"用户数据"（标题、正文、单元格），不作用于自己拼的标签。
- 表格用 `<th>` 渲染第一行、`<td>` 渲染其余行——这是展示规则，
  放在访问者里正合适（纯文本访问者就不需要表头概念）。
- 删掉它：HTML 导出逻辑会回到三个元素类里，元素开始知道 `<section>` 和 `<pre>`。

### 5.9 `PlainTextVisitor`：同样的元素，另一种"看法"

```java
@Override
public String visit(SpreadsheetDocument document) {
    StringBuilder text = new StringBuilder();
    for (List<String> row : document.rows()) {
        if (!text.isEmpty()) { text.append('\n'); }
        text.append(String.join(" | ", row));
    }
    return text.toString();
}
```

- 表格用 `" | "` 分隔而不是对齐空格：中文字符宽度不一致，
  用空格对齐在不同字体下必然错位，而 `" | "` 与字体无关。
- 返回 `String` 而不是 `HtmlFragment`：**同一个元素，不同的访问者可以产出完全不同的类型**，
  这正是泛型 `R` 的意义。
- 删掉它：搜索索引就得自己解析 HTML，或者退回到元素里加 `toPlainText()`。

### 5.10 `DocumentStatistics`：可相加的统计结果

```java
public record DocumentStatistics(int documents,
                                 int pdfs,
                                 int words,
                                 int spreadsheets,
                                 int totalPages,
                                 int totalBodyChars,
                                 int totalCells) {

    public static DocumentStatistics empty() { ... }
    public DocumentStatistics plus(DocumentStatistics other) { ... }
    public String summary() { ... }
}
```

- 七个字段分别对应"总数 / 三种类型数 / 页数 / 正文字符 / 单元格"。
  **为什么不用 `Map<String, Integer>`？** 因为字段名拼错时 Map 不会报错，
  而 record 的访问器是编译期检查的。
- `empty()` 是折叠的单位元；`plus` 是结合律的合并。两者配齐，
  `acceptAndFold` 才能写成 `reduce`。
- 紧凑构造器拒绝负数：折叠逻辑写错（比如误用减法）时立刻失败，
  而不是产出一份负数报表。
- `summary()` 负责展示，**不负责计算**——计算在 `plus` 里，展示可以随时改格式。

### 5.11 `StatisticsVisitor`：返回"一份的贡献"

```java
@Override
public DocumentStatistics visit(PdfDocument document) {
    return new DocumentStatistics(1, 1, 0, 0, document.pageCount(), document.bodyLength(), 0);
}

@Override
public DocumentStatistics visit(WordDocument document) {
    return new DocumentStatistics(1, 0, 1, 0, 0, document.bodyLength(), 0);
}

@Override
public DocumentStatistics visit(SpreadsheetDocument document) {
    return new DocumentStatistics(1, 0, 0, 1, 0, document.bodyLength(), document.cellCount());
}
```

- 注意每个返回值里只有一份文档的数据，**没有任何累加**。
- 这样同一个 `StatisticsVisitor` 实例可以安全地复用（Demo 3），
  也可以并行处理不同文档（把 `acceptAndFold` 换成并行流即可）。
- 删掉它：统计逻辑会散在调用方，或者退化成元素上的 `statistics()` 方法。

### 5.12 `DocumentBundle`：对象结构的两种用法

```java
public final class DocumentBundle {
    private final List<Document> documents;

    public DocumentBundle(List<Document> documents) {
        if (documents == null) { throw new IllegalArgumentException("documents 不能为 null"); }
        this.documents = List.copyOf(documents);
    }

    public <R> List<R> acceptAll(DocumentVisitor<R> visitor) { ... }

    public <R> R acceptAndFold(DocumentVisitor<R> visitor, R identity, BinaryOperator<R> folder) { ... }
}
```

- 构造时 `List.copyOf`：外部改原 List 不会影响文档包，也会拒绝 `null` 元素。
- `acceptAll` 返回"每个元素一份结果"，适合同一批文档分别导出（Demo 1）。
- `acceptAndFold` 返回"折叠成一个结果"，适合统计（Demo 4）。
  **两个方法都在对象结构上，而不是访问者上**——因为"怎么遍历"是容器的职责。
- 删掉它：`Client` 里会出现 `for (Document d : documents) { ... }` 的循环，
  一旦文档容器换实现（比如树），所有调用方都要改。

### 5.13 `CartItem`：接口里为什么没有 `discountAmount()`

```java
public sealed interface CartItem permits RegularItem, DiscountItem, BuyOneGetOneItem {
    String name();
    Money unitPrice();
    int quantity();
    Money subtotal();
    <R> R accept(CartVisitor<R> visitor);
}
```

- `discountAmount()` 被**故意排除**。原价商品没有折扣，
  如果接口要求它实现，它只能返回 `Money.ZERO`——虽然比 `getTotalPrice() = 0.0` 诚实，
  但语义上仍然是"这个接口假装所有商品都有折扣"。
- 需要优惠金额的访问者（`PricingVisitor`、`SavingsVisitor`）
  各自处理三种类型：原价商品返回 `ZERO`，折扣商品和买一赠一返回真实金额。
  **"零"出现在访问者里，而不是元素里**，因为"有没有优惠"是访问者关心的问题。
- 删掉 `subtotal()`：每个访问者都要自己算一遍，公式又会分叉。
  `subtotal()` 是元素的**计价职责**，不是展示职责。

### 5.14 `CartVisitor<R>`：三个 `visit`，没有别的

```java
public interface CartVisitor<R> {
    R visit(RegularItem item);
    R visit(DiscountItem item);
    R visit(BuyOneGetOneItem item);
}
```

- 没有 `getTotalPrice()`、没有 `reset()`、没有 `getLines()`。
- 每个访问者只回答"我处理这一件商品时产出什么"。
- 这正是 V4 的解药：**接口只描述共性，不描述某个实现的私有需求。**

### 5.15 `RegularItem`：最简单的元素

```java
public record RegularItem(String name, Money unitPrice, int quantity) implements CartItem {
    public RegularItem {
        name = PricingText.requireName(name);
        unitPrice = PricingText.requirePrice(unitPrice);
        quantity = PricingText.requireQuantity(quantity);
    }

    @Override
    public Money subtotal() { return unitPrice.multiply(quantity); }
    ...
}
```

- 只有一条计价规则：单价 × 数量。
- `Money.multiply(int)` 内部用 `Math.multiplyExact`，溢出立刻抛异常。
- 删掉它：原价商品在访问者里又要单独判断"没有折扣"，类型判断回来了。

### 5.16 `DiscountItem`：一次舍入的证据

```java
public record DiscountItem(String name, Money unitPrice, int quantity, BigDecimal rate)
        implements CartItem {

    public Money beforeDiscount() { return unitPrice.multiply(quantity); }

    @Override
    public Money subtotal() { return beforeDiscount().times(rate); }

    public Money discountAmount() { return beforeDiscount().minus(subtotal()); }

    public String rateLabel() {
        return rate.movePointRight(1).stripTrailingZeros().toPlainString() + " 折";
    }
}
```

- `rate` 用 `BigDecimal`（0.8 表示 8 折），因为折扣率需要精确十进制。
  校验在 `PricingText.requireRate`：必须 `> 0` 且 `<= 1`。
- **`subtotal` 只在最后舍入一次**（第 4.8 节已给出 3.33 × 10 × 0.8 的反例）。
- `rateLabel()` 把 `0.8` 变成 `"8 折"`：`movePointRight(1)` → `8.0`，
  `stripTrailingZeros()` → `8`，`toPlainString()` → `"8"`。
  若用 `toString()`，`8E+0` 这种科学计数法会出现在小票上。
- 删掉它：折扣逻辑会回到访问者，`PricingVisitor` 和 `ReceiptVisitor` 各写一份。

### 5.17 `BuyOneGetOneItem`：公式只此一份

```java
public int billableUnits() { return (quantity + 1) / 2; }
public int freeUnits()     { return quantity - billableUnits(); }
public Money beforeDiscount() { return unitPrice.multiply(quantity); }

@Override
public Money subtotal() { return unitPrice.multiply(billableUnits()); }

public Money discountAmount() { return unitPrice.multiply(freeUnits()); }
```

- `billableUnits()` 是唯一公式；`freeUnits()` 由它派生，不再写第二份。
- Demo 8 把 quantity 1-6 的"应付件数 / 赠送件数 / 两种访问者的小计"并排打印，
  证明三个访问者读到的是同一份规则。
- 删掉它：`PricingVisitor` 里会出现 `(q+1)/2`，`ReceiptVisitor` 里再出现一次，
  V5 回来了。

### 5.18 `Money`：整数分与手工格式化

```java
public record Money(long cents) implements Comparable<Money> {
    public static final Money ZERO = new Money(0);

    public static Money ofYuan(String yuan) { ... }      // "99" / "99.5" / "99.50"
    public Money plus(Money other)  { return new Money(Math.addExact(cents, other.cents)); }
    public Money minus(Money other) { return new Money(Math.subtractExact(cents, other.cents)); }
    public Money multiply(int quantity) { return new Money(Math.multiplyExact(cents, quantity)); }
    public Money times(BigDecimal rate) { ... }          // 只在这里 HALF_UP 一次

    public String format() {
        long absolute = Math.abs(cents);
        long yuanPart = absolute / 100;
        long centPart = absolute % 100;
        String sign = cents < 0 ? "-" : "";
        return sign + yuanPart + "." + (centPart < 10 ? "0" : "") + centPart;
    }
}
```

- `ofYuan` 接受字符串并用 `BigDecimal` 解析，`setScale(0, HALF_UP)` 后 `longValueExact()`。
  入口只收字符串而**不提供 `double` 重载**：一旦提供，调用方就会写出
  `Money.ofYuan(0.1 + 0.2)`，而误差在进入方法之前就已经发生了。
- `format()` 手工拼而不用 `String.format("%.2f", ...)`：
  **`String.format` 依赖默认 `Locale`**，在某些区域会把小数点变成逗号，
  输出就不再逐字节可复现。演示的哈希校验依赖这一点。
- `Math.*Exact`：金额溢出比崩溃更可怕（负数账单），所以宁可抛 `ArithmeticException`。
- 删掉它：金额重新变回 `double`，V6 回来了。

### 5.19 `PricingText`：商品侧的四道门

```java
final class PricingText {
    static String requireName(String name) { ... }
    static Money requirePrice(Money price) { ... }        // 非 null、非负
    static int requireQuantity(int quantity) { ... }      // 至少 1
    static BigDecimal requireRate(BigDecimal rate) { ... } // (0, 1]
    private PricingText() { }
}
```

- 四个方法对应四类错误，消息格式统一为"字段 + 规则 + 实际值"，
  例如 `数量至少为 1，实际 0`。
- 折扣率上限是 1（不打折），下限是 0（不含）：`0 折` 意味着白送，
  在业务上通常是录入错误，所以拒绝。
- 删掉它：六个校验点各写各的，Demo 7 里 7 条消息的风格就会不一致。

### 5.20 `LineResult`：一行结果的六元组

```java
public record LineResult(String label, String name, int quantity,
                         Money unitPrice, Money subtotal, Money discount) {
    public LineResult { ... 拒绝空白与 null ... }
    public boolean hasDiscount() { return !discount.isZero(); }
}
```

- 六个字段刚好覆盖"小票一行"需要的信息：计价方式、商品名、件数、
  单价、应付、优惠。
- `hasDiscount()` 是展示用的派生判断，避免调用方写 `discount.cents() != 0`。
- 为什么不让 `PricingVisitor` 直接拼字符串？因为**同一份明细可能被
  文本小票、HTML 表格、JSON 接口三种方式消费**。结果类型是数据，
  格式化是访问者或展示层的事。

### 5.21 `CartSummary`：把明细汇总成三个数

```java
public record CartSummary(List<LineResult> lines, Money total, Money saved) {
    public CartSummary {
        lines = List.copyOf(lines);
        ...
    }

    public static CartSummary of(List<LineResult> lines) {
        Money total = Money.ZERO;
        Money saved = Money.ZERO;
        for (LineResult line : lines) {
            total = total.plus(line.subtotal());
            saved = saved.plus(line.discount());
        }
        return new CartSummary(lines, total, saved);
    }

    public int lineCount() { ... }
    public int itemCount() { ... }
    public boolean isEmpty() { ... }
}
```

- `of` 是**唯一**的汇总入口：总价 = 各行小计之和，优惠 = 各行优惠之和。
  两条公式各只有一份，Demo 5 用恒等式
  `total + saved == 166.00` 验证了它们自洽。
- `lines` 用 `List.copyOf`：汇总结果一旦生成，明细不能被外部修改。
- 删掉它：`Client` 里会出现两个 `for` 循环分别累加 total 和 saved，
  而且"总价是否等于明细之和"这个不变量没人保证。

### 5.22 `ShoppingCart`：对象结构 + 入口校验

```java
public final class ShoppingCart {
    private final List<CartItem> items = new ArrayList<>();

    public ShoppingCart add(CartItem item) {
        if (item == null) {
            throw new IllegalArgumentException("第 " + (items.size() + 1) + " 次 add 传入的商品为 null");
        }
        items.add(item);
        return this;
    }

    public List<CartItem> items() { return List.copyOf(items); }

    public <R> List<R> acceptAll(CartVisitor<R> visitor) {
        List<R> results = new ArrayList<>(items.size());
        for (CartItem item : items) {
            results.add(item.accept(visitor));
        }
        return List.copyOf(results);
    }

    public CartSummary summarize() {
        return CartSummary.of(acceptAll(new PricingVisitor()));
    }
}
```

- `add` 立即拒绝 null，消息里带"第 N 次"（V8 的解药）：
  调用方一眼能定位是哪一次添加出错。
- 返回 `this` 支持链式调用，Demo 里构造购物车只需一个表达式。
- `items()` 返回副本：外部拿到的是快照，无法通过它往购物车里塞东西。
- `summarize()` 是便利方法，内部固定用 `PricingVisitor`。
  **它不是访问者**，只是一个"常用组合"的快捷方式。

### 5.23 `PricingVisitor`：最小可用访问者

```java
@Override
public LineResult visit(RegularItem item) {
    return new LineResult("原价", item.name(), item.quantity(),
            item.unitPrice(), item.subtotal(), Money.ZERO);
}

@Override
public LineResult visit(DiscountItem item) {
    return new LineResult(item.rateLabel(), item.name(), item.quantity(),
            item.unitPrice(), item.subtotal(), item.discountAmount());
}

@Override
public LineResult visit(BuyOneGetOneItem item) {
    return new LineResult("买一赠一", item.name(), item.quantity(),
            item.unitPrice(), item.subtotal(), item.discountAmount());
}
```

- 三个分支都只是"把元素的数据搬进 `LineResult`"，**不做任何金额运算**。
  金额全部来自元素的 `subtotal()` / `discountAmount()`。
- 这是"访问者无状态"的直接结果：它没有字段，每次调用都只依赖参数。
- 删掉它：总价计算回到 `Client`，明细格式和计算又混在一起。

### 5.24 `ReceiptVisitor`：同样的数据，另一种排版

```java
@Override
public String visit(DiscountItem item) {
    return line(item.name(), item.rateLabel(), item.quantity(),
            item.unitPrice(), item.subtotal(), item.discountAmount());
}

private static String line(String name, String label, int quantity,
                           Money unitPrice, Money subtotal, Money discount) {
    String base = name + "(" + label + ") | ×" + quantity
            + " | 单价 " + unitPrice.format() + " | 小计 " + subtotal.format();
    return discount.isZero() ? base : base + " | 省 " + discount.format();
}
```

- 返回 `String`，不碰金额计算，也不产生 `LineResult`。
- `line` 是私有静态方法：三个分支共用同一套排版，避免小票格式分叉。
- 与 `PricingVisitor` 对比可以看出：**同一个元素、同样的返回值类型都可能不同**，
  访问者的 `R` 可以是 `LineResult`，也可以是 `String`。
- 删掉它：小票排版会跑到 `Client`，或者硬塞进 `PricingVisitor`（那就是 `getTotalPrice` 的翻版）。

### 5.25 `SavingsVisitor`：只算优惠，返回 `Money`

```java
@Override
public Money visit(RegularItem item) { return Money.ZERO; }

@Override
public Money visit(DiscountItem item) { return item.discountAmount(); }

@Override
public Money visit(BuyOneGetOneItem item) { return item.discountAmount(); }
```

- 同样的零，一个是谎言，一个是事实：
  原项目 `PrintListVisitor.getTotalPrice()` 返回 `0.0` 是"我算不了，但接口逼我返回"；
  这里的 `Money.ZERO` 是"这件商品确实没有优惠"。
- 三行代码就实现了"只算优惠"的操作，说明访问者的粒度可以很细。
- 删掉它：想单独统计优惠，只能在 `PricingVisitor` 的结果里二次遍历。

### 5.26 `Client`：八段 Demo 与一个内部类

```java
public static void main(String[] args) {
    demo1ThreeOperations();       // 三种文档 × 三种操作，元素零改动
    demo2Immutability();          // 外部改 List 不影响文档；非法入参构造期失败
    demo3StatelessVisitor();      // 同一访问者两次结果相等
    demo4FoldByReturnValue();     // 三份文档折叠成一页（void 做不到）
    demo5CartThreeVisitors();     // 一辆车 × 三个访问者 × 三种结果类型
    demo6CartVisitorReusable();   // 第二次结账不翻倍
    demo7CartValidation();        // 七条校验消息
    demo8BuyOneGetOneSingleFormula(); // 公式只有一份
}
```

`DocumentDescriber` 是 `Client` 的私有内部类：

```java
private static final class DocumentDescriber implements DocumentVisitor<String> {
    @Override
    public String visit(PdfDocument document) {
        return "PDF  《" + document.title() + "》 " + document.pageCount() + " 页";
    }
    @Override
    public String visit(WordDocument document) {
        return "Word 《" + document.title() + "》 " + document.bodyLength() + " 字";
    }
    @Override
    public String visit(SpreadsheetDocument document) {
        return "表格 《" + document.title() + "》 " + document.rowCount() + " 行 × " + document.columnCount() + " 列";
    }
}
```

- 它证明"新增一种操作 = 新增一个类"：没有改元素、没有改接口、没有改对象结构。
- 放在 `Client` 内部而不是单独文件，是因为它只服务于 Demo。
  **如果它是真实需求（比如控制台摘要），就应该独立成文件**——
  判断标准是"有没有第二个使用者"，不是"代码有多长"。
- `Client` 里的 `expectFailure(String, Runnable)` 把"期望抛异常"的用例
  收成一个方法，避免 Demo 7 出现七个 try/catch。

下一节用真实输出走一遍这 26 个类在运行时的协作顺序。
---

## 6. 执行流程：八段 Demo 的真实运行结果

本节用重构后 `Client` 的实际输出，逐段说明**对象是什么时候创建的、方法调用怎么走、
关键变量变成了什么、最后为什么打印出这一行**。所有输出都是
`java -Dstdout.encoding=UTF-8` 的真实结果，不是手写示意。

### 6.0 入口与总览

```java
public static void main(String[] args) {
    demo1ThreeOperations();
    demo2Immutability();
    demo3StatelessVisitor();
    demo4FoldByReturnValue();
    demo5CartThreeVisitors();
    demo6CartVisitorReusable();
    demo7CartValidation();
    demo8BuyOneGetOneSingleFormula();
}
```

八段 Demo 的执行顺序与依赖关系：

```text
main
 ├─ Demo 1  文档线：3 元素 × 3 访问者 + 1 个内部类访问者
 ├─ Demo 2  文档线：不可变性与构造期校验
 ├─ Demo 3  文档线：访问者无状态（同一实例两次调用）
 ├─ Demo 4  文档线：acceptAndFold 折叠
 ├─ Demo 5  购物车线：3 访问者 × 3 结果类型
 ├─ Demo 6  购物车线：访问者复用
 ├─ Demo 7  购物车线：7 条校验消息
 └─ Demo 8  购物车线：买一赠一公式唯一
```

### 6.1 Demo 1：同一批文档，三种操作，元素类零改动

**输出（逐字）**

```text
================================================================
Demo 1：同一批文档，三种操作，元素类零改动
================================================================
文档清单：
  1. PDF  《设计模式：可复用面向对象软件的基础》 395 页
  2. Word 《重构：改善既有代码的设计》 26 字
  3. 表格 《2025 年第一季度销售》 4 行 × 3 列

--- 操作 A：HtmlExportVisitor（返回 HtmlFragment，不打印）---
<section class='pdf'>
  <h2>设计模式：可复用面向对象软件的基础</h2>
  <p class='meta'>PDF · 395 页</p>
  <pre>GoF 四人组把面向对象设计经验归纳为 23 种模式。</pre>
</section>
<section class='word'>
  <h2>重构：改善既有代码的设计</h2>
  <p class='meta'>Word · 26 字</p>
  <p>重构是在不改变软件可观察行为的前提下改善其内部结构。</p>
</section>
<section class='sheet'>
  <h2>2025 年第一季度销售</h2>
  <p class='meta'>表格 · 4 行 × 3 列</p>
  <table border='1' cellspacing='0' cellpadding='4'>
    <tr><th>产品</th><th>单价</th><th>销量</th></tr>
    <tr><td>机械键盘</td><td>499.00</td><td>128</td></tr>
    <tr><td>人体工学椅</td><td>1899.00</td><td>36</td></tr>
    <tr><td>显示器支架</td><td>299.00</td><td>210</td></tr>
  </table>
</section>

--- 操作 B：PlainTextVisitor（返回 String）---
[PDF] 设计模式：可复用面向对象软件的基础 (395 页)
GoF 四人组把面向对象设计经验归纳为 23 种模式。
[Word] 重构：改善既有代码的设计 (26 字)
重构是在不改变软件可观察行为的前提下改善其内部结构。
[表格] 2025 年第一季度销售 (4 行 × 3 列)
产品 | 单价 | 销量
机械键盘 | 499.00 | 128
人体工学椅 | 1899.00 | 36
显示器支架 | 299.00 | 210

--- 操作 C：StatisticsVisitor（返回可加的 DocumentStatistics）---
3 份文档（PDF 1 / Word 1 / 表格 1） · 合计 395 页 · 正文 100 字 · 单元格 12 个
```

**逐步拆解**

1. `Client` 先构造三份文档（正文与表格内容见输出）：

   ```java
   List<Document> documents = List.of(
           new PdfDocument("设计模式：可复用面向对象软件的基础", 395,
                   "GoF 四人组把面向对象设计经验归纳为 23 种模式。"),
           new WordDocument("重构：改善既有代码的设计",
                   "重构是在不改变软件可观察行为的前提下改善其内部结构。"),
           new SpreadsheetDocument("2025 年第一季度销售",
                   List.of(List.of("产品", "单价", "销量"), /* 其余 3 行见输出 */)));
   ```

   构造 `SpreadsheetDocument` 时，`DocumentText.normalizeTable(rows)` 立即把
   `rows` 深拷贝成不可变结构。**此时原始 `rows` 已经和文档无关了**——
   这是 Demo 2 要验证的点。

2. 三份文档放进 `DocumentBundle`，`List.copyOf` 再拷一次外层。

3. 操作 A：

   ```java
   HtmlExportVisitor html = new HtmlExportVisitor();
   List<HtmlFragment> fragments = bundle.acceptAll(html);
   ```

   `acceptAll` 内部循环调用 `document.accept(html)`；
   每个元素的 `accept` 执行 `html.visit(this)`。以 PDF 为例，调用栈是：

   ```text
   DocumentBundle.acceptAll(HtmlExportVisitor)
     └─ PdfDocument.accept(DocumentVisitor)
          └─ HtmlExportVisitor.visit(PdfDocument)
               └─ DocumentText.escapeHtml(...)  ×2
                    └─ return new HtmlFragment("<section ...>")
   ```

   **注意返回值一路向上传**：`visit` 返回 `HtmlFragment`，`accept` 原样返回，
   `acceptAll` 收集成 `List<HtmlFragment>`。原项目的 `void` 版本在这里什么都拿不到。

4. 操作 B：换成 `PlainTextVisitor`，**元素对象一个都没变**，
   还是那三个 record，只是交出去的访问者不同。

5. 操作 C：

   ```java
   DocumentStatistics stats = bundle.acceptAndFold(
           new StatisticsVisitor(), DocumentStatistics.empty(), DocumentStatistics::plus);
   ```

   `empty()` 是零值，`plus` 逐份累加，得到
   `3 份文档（PDF 1 / Word 1 / 表格 1） · 合计 395 页 · 正文 100 字 · 单元格 12 个`。

   **这三个数字怎么来的？**

   | 文档 | 页数 | 正文字符 | 单元格 |
   |---|---:|---:|---:|
   | PDF | 395 | 27（"GoF 四人组把面向对象设计经验归纳为 23 种模式。"） | 0 |
   | Word | 0 | 26（"重构是在不改变软件可观察行为的前提下改善其内部结构。"） | 0 |
   | 表格 | 0 | 47（4 行 × 3 列的所有单元格字符） | 12 |
   | **合计** | **395** | **100** | **12** |

6. 文档清单的那三行（`1. PDF 《设计模式…》 395 页` 等）来自第四种操作
   `DocumentDescriber`：`describe(document)` 内部执行
   `document.accept(new DocumentDescriber())`，返回 `String`。
   它证明"新增操作只加一个类"，详见 7.1。

### 6.2 Demo 2：文档对外不可变，非法入参构造期即失败

**输出（逐字）**

```text
================================================================
Demo 2：文档对外不可变，非法入参构造期即失败
================================================================
修改原始 List 之后，文档第 2 行第 1 列 = 鱼皮（未被污染）
修改文档内部 List -> UnsupportedOperationException
  null 表格 -> IllegalArgumentException: rows 不能为 null
  含 null 单元格 -> IllegalArgumentException: 第 2 行第 2 列为 null
  空白标题 -> IllegalArgumentException: title 不能为空白字符串
  页数 0 -> IllegalArgumentException: pageCount 至少为 1，实际 0
```

**逐步拆解**

1. 先构造一个可变 `List<List<String>>`，交给 `SpreadsheetDocument`；
2. 构造器里的 `normalizeTable` 做了三层拷贝：每行 `List.copyOf`、外层 `List.copyOf`、
   单元格 `null` 检查；
3. 外部再改原始 `List`（把第 2 行第 1 列改成"黑客"），
   文档读出来仍是 `鱼皮` —— **因为文档持有的是一份副本，不是引用**；
4. 尝试 `document.rows().get(0).set(0, "x")`：`rows()` 返回的是不可变 List，
   抛 `UnsupportedOperationException`；
5. 四个非法入参全部在**构造期**失败，消息里带行列号或具体值。

**为什么这四条校验要放在构造器里？**
因为它们都是"对象一旦带上这个错误值，后续所有访问者都会算错"的问题。
早失败一秒钟，就少一次跨模块排查（对比原项目 V7：`null` 表格一直到
`HtmlExportVisitor.visit:24` 才炸）。

### 6.3 Demo 3：访问者无状态，同一对象可反复使用

**输出（逐字）**

```text
================================================================
Demo 3：访问者无状态，同一对象可反复使用
================================================================
同一 HtmlExportVisitor 连续两次导出，结果相等 = true
两次导出的首段长度 = 102 / 102
同一 StatisticsVisitor 连续两次统计，结果相等 = true
两次统计： 2 份文档（PDF 1 / Word 1 / 表格 0） · 合计 12 页 · 正文 18 字 · 单元格 0 个
```

**逐步拆解**

1. `HtmlExportVisitor` 没有字段，`visit` 只依赖参数，所以两次输出的
   `HtmlFragment` 满足 `equals`（record 的 `equals` 逐字段比较）；
2. 首段长度两次都是 102，说明内容完全一致；
3. `StatisticsVisitor` 同样无状态，两次 `acceptAndFold` 的结果 `equals` 为 `true`；
4. 对照原项目 V3：同一个 `PriceCalculatorVisitor` 第二次 `accept` 后总价从
   99.0 变成 198.0 —— 因为状态长在访问者身上。

**如果访问者必须携带状态怎么办？**
把它变成一次性的（每次使用 new 一个），或者把状态放进方法参数/上下文对象。
本项目选择"访问者只返回本次贡献，聚合交给调用方"，从根上避免了这个问题。

### 6.4 Demo 4：返回值让折叠成为可能（void 做不到）

**输出（逐字）**

```text
================================================================
Demo 4：返回值让折叠成为可能（void 做不到）
================================================================
三份文档折叠成一页，总长度 = 427 字符
页面片段以 HTML 声明开头 = true
分开导出的长度之和 = 427，与折叠结果相等 = true
```

**逐步拆解**

1. `bundle.acceptAndFold(new HtmlExportVisitor(), HtmlFragment.empty(), HtmlFragment::concat)`
   —— 以一个空片段为起点，逐份拼接；
2. 返回的 `HtmlFragment` 长度 427；三份分别导出再相加也是 427，
   说明 `concat` 既没丢内容也没重复；
3. **`void` 版本无法表达这个操作**：没有返回值，就没有"从 A 和 B 得到 C"的函数，
   折叠只能靠可变累加器（那就回到 V3 了）。

### 6.5 Demo 5：同一辆购物车，三个访问者，三种结果类型

**输出（逐字）**

```text
================================================================
Demo 5：同一辆购物车，三个访问者，三种结果类型
================================================================
购物车：3 种商品 / 5 件

--- 操作 A：PricingVisitor（返回 List<LineResult>）---
  原价 | 《深入理解计算机系统》 | ×1 | 单价 99.00 | 小计 99.00
  8 折 | 《设计模式》 | ×1 | 单价 58.00 | 小计 46.40 | 省 11.60
  买一赠一 | 可口可乐 | ×3 | 单价 3.00 | 小计 6.00 | 省 3.00
  ------------------------------------------
  应付 151.40 | 优惠 14.60 | 原价合计 166.00
  恒等式 total + saved == 166.00 -> true

--- 操作 B：ReceiptVisitor（返回 String，不碰金额计算）---
  《深入理解计算机系统》 | ×1 | 单价 99.00 | 小计 99.00
  《设计模式》(8 折) | ×1 | 单价 58.00 | 小计 46.40 | 省 11.60
  可口可乐(买一赠一) | ×3 | 单价 3.00 | 小计 6.00 | 省 3.00

--- 操作 C：SavingsVisitor（只返回 Money）---
  本次共优惠 14.60 元
  与 PricingVisitor 汇总的优惠一致 = true
```

**逐步拆解**

1. 购物车由三个元素构成：

   | 元素 | 单价 | 件数 | 计价规则 | 小计 |
   |---|---:|---:|---|---:|
   | `RegularItem` | 99.00 | 1 | 单价 × 件数 | 99.00 |
   | `DiscountItem` | 58.00 | 1 | 单价 × 件数 × 0.8 | 46.40 |
   | `BuyOneGetOneItem` | 3.00 | 3 | 单价 × ⌈件数/2⌉ | 6.00 |

2. 操作 A 调用 `cart.acceptAll(new PricingVisitor())`，得到 3 个 `LineResult`；
   `CartSummary.of(lines)` 再累加出 `total = 151.40`、`saved = 14.60`。

3. **恒等式验证**：`total + saved = 151.40 + 14.60 = 166.00`，
   正好等于三件商品的原价合计 `99.00 + 58.00 + 9.00 = 166.00`。
   这条断言的价值在于：它同时检查了"折后价"、"优惠额"、"原价"三个数是否自洽。
   如果 `DiscountItem` 的舍入顺序写错（先打折再乘数量），这里会立刻不成立。

4. 操作 B 换成 `ReceiptVisitor`，返回类型从 `LineResult` 变成 `String`；
   操作 C 换成 `SavingsVisitor`，返回类型变成 `Money`。**元素对象始终是那三个。**

5. 对比原项目：
   - `46.400000000000006` → `46.40`（`Money` 整数分）；
   - `getTotalPrice()` 被 `CartSummary` 取代；
   - 打印访问者不再需要实现"算总价"。

### 6.6 Demo 6：购物车访问者可复用，第二次调用不会翻倍

**输出（逐字）**

```text
================================================================
Demo 6：购物车访问者可复用，第二次调用不会翻倍
================================================================
第一次结账：应付 50.00 | 优惠 10.00
第二次结账：应付 50.00 | 优惠 10.00
两次结果完全相等 = true
SavingsVisitor 两次结果 = 10.00 / 10.00，相等 = true
```

**逐步拆解**

1. 同一辆购物车、同一个 `PricingVisitor` 实例，`summarize()` 两次；
2. 两次都是 `应付 50.00 | 优惠 10.00`，因为 `PricingVisitor` 没有字段，
   `CartSummary.of` 每次都从零开始累加；
3. `SavingsVisitor` 两次都是 `10.00`，同样因为无状态；
4. 对照原项目 V3（99.0 → 198.0）：访问者的状态是**跨调用泄漏**的，
   而这里的状态只存在于方法局部变量里。

### 6.7 Demo 7：非法入参在构造期被拒绝

**输出（逐字）**

```text
================================================================
Demo 7：非法入参在构造期被拒绝（错误靠近源头）
================================================================
  第 2 次 add 传 null -> IllegalArgumentException: 第 2 次 add 传入的商品为 null
  数量为 0 -> IllegalArgumentException: 数量至少为 1，实际 0
  负单价 -> IllegalArgumentException: 单价不能为负，实际 -0.01
  折扣率 1.2 -> IllegalArgumentException: 折扣率不能大于 1（1 表示不打折），实际 1.2
  折扣率 0 -> IllegalArgumentException: 折扣率必须大于 0，实际 0
  金额文本 abc -> IllegalArgumentException: 金额格式非法: "abc"（示例: 99 / 99.5 / 99.50）
  负数金额 -> IllegalArgumentException: 金额不能为负，实际 -1.00
购物车仍然只有 1 种商品，未被污染
```

**逐步拆解**

1. 第 1 条来自 `ShoppingCart.add`：`items.size() + 1` 算出"这是第 2 次 add"；
2. 第 2-5 条来自 `PricingText` 的四个方法；
3. 第 6-7 条来自 `Money.ofYuan(String)`；
4. 最后一行验证"失败的操作没有副作用"：`add` 抛异常时没有 `items.add`，
   购物车仍是 1 种商品。

**为什么错误消息要带实际值？**
因为排查时最常问的是"到底传了什么"。`数量至少为 1，实际 0` 比
`数量非法` 多了一个关键信息，而成本只是一个字符串拼接。

### 6.8 Demo 8：买一赠一公式只有一份

**输出（逐字）**

```text
================================================================
Demo 8：买一赠一公式只有一份，三个访问者共用
================================================================
  quantity | 应付件数 | 赠送件数 | PricingVisitor 小计 | ReceiptVisitor 文本
  1        | 1        | 0        | 3.00              | 可乐(买一赠一) | ×1 | 单价 3.00 | 小计 3.00
  2        | 1        | 1        | 3.00              | 可乐(买一赠一) | ×2 | 单价 3.00 | 小计 3.00 | 省 3.00
  3        | 2        | 1        | 6.00              | 可乐(买一赠一) | ×3 | 单价 3.00 | 小计 6.00 | 省 3.00
  4        | 2        | 2        | 6.00              | 可乐(买一赠一) | ×4 | 单价 3.00 | 小计 6.00 | 省 6.00
  5        | 3        | 2        | 9.00              | 可乐(买一赠一) | ×5 | 单价 3.00 | 小计 9.00 | 省 6.00
  6        | 3        | 3        | 9.00              | 可乐(买一赠一) | ×6 | 单价 3.00 | 小计 9.00 | 省 9.00
  两个访问者的金额来自同一个 billableUnits()，不存在第二份公式
```

**逐步拆解**

1. 每一行都新建一个 `BuyOneGetOneItem("可乐", Money.ofYuan("3.00"), quantity)`；
2. `billableUnits()` 和 `freeUnits()` 给出应付/赠送件数；
3. `PricingVisitor.visit` 调 `item.subtotal()`，`ReceiptVisitor.visit` 也调
   `item.subtotal()` 和 `item.discountAmount()` —— **两条路径读的是同一份公式**；
4. 如果哪天规则变成"买二赠一"，只改 `billableUnits()` 一处，
   两个访问者的输出会同时变化。

### 6.9 一次 `accept` 的完整调用栈

以 Demo 5 中"折扣商品"这一行为例，从 `summarize()` 到最终字符串：

| 步骤 | 调用 | 关键变量 | 结果 |
|---:|---|---|---|
| 1 | `cart.summarize()` | `items = [Regular, Discount, Bogo]` | 进入汇总 |
| 2 | `acceptAll(new PricingVisitor())` | `visitor` 无状态 | 准备结果列表 |
| 3 | `item.accept(visitor)` | `this = DiscountItem` | 双重分派开始 |
| 4 | `visitor.visit(this)` | 静态类型 `DiscountItem` | 选中 `visit(DiscountItem)` |
| 5 | `item.subtotal()` | `beforeDiscount = 58.00` | `46.40` |
| 6 | `item.discountAmount()` | `beforeDiscount - subtotal` | `11.60` |
| 7 | `new LineResult("8 折", …)` | 6 个字段 | 一行结果 |
| 8 | `CartSummary.of(lines)` | `total = 0 + 99.00 + 46.40 + 6.00` | `151.40` |
| 9 | `saved = 0 + 0 + 11.60 + 3.00` | — | `14.60` |

第 3-4 步就是**双重分派**：

- 第一次分派发生在 `accept` 的**虚方法**调用上——`item` 的运行时类型决定
  进入哪个 `accept` 实现；
- 第二次分派发生在 `visit(this)` 的**重载解析**上——`this` 的静态类型
  （`DiscountItem`）决定选中哪个 `visit` 重载。

Java 只支持单分派（虚方法按接收者分派，重载按静态类型分派），
访问者模式用"两次单分派"拼出了双分派的效果。这就是它必须依赖
"每种元素一个 `accept` 实现"的原因：**`this` 的静态类型只有在元素自己的
`accept` 方法里才是精确的。**

---

## 7. 扩展对比：新需求来了改哪里

### 7.1 新增一种操作：控制台摘要

**需求**：在控制台打印"PDF 《书名》 395 页"这样的摘要。

**改动**：新增 1 个类（`Client` 里的 `DocumentDescriber` 就是它）。

```java
private static final class DocumentDescriber implements DocumentVisitor<String> {
    @Override public String visit(PdfDocument document) {
        return "PDF  《" + document.title() + "》 " + document.pageCount() + " 页";
    }
    @Override public String visit(WordDocument document) {
        return "Word 《" + document.title() + "》 " + document.bodyLength() + " 字";
    }
    @Override public String visit(SpreadsheetDocument document) {
        return "表格 《" + document.title() + "》 " + document.rowCount() + " 行 × " + document.columnCount() + " 列";
    }
}
```

| 需要改的文件 | 数量 |
|---|---:|
| 新增访问者 | 1 |
| 元素类 | 0 |
| `DocumentVisitor` 接口 | 0 |
| 对象结构 | 0 |

**这就是访问者模式的开闭原则：对扩展开放（加操作），对修改关闭（元素不动）。**

### 7.2 新增一种元素：Markdown 文档

**需求**：支持 `MarkdownDocument(title, body)`。

| 需要改的文件 | 具体改动 |
|---|---|
| `Document` | `permits` 加一项 |
| `DocumentVisitor` | 加 `R visit(MarkdownDocument document)` |
| `HtmlExportVisitor` | 实现新 `visit` |
| `PlainTextVisitor` | 实现新 `visit` |
| `StatisticsVisitor` | 实现新 `visit` |
| `DocumentDescriber` | 实现新 `visit` |
| **合计** | **6 个文件** |

编译器会**强制**你改完后 4 个——这就是 `sealed` 的价值。但也必须承认：
**这是访问者模式最真实的代价**。如果元素类型会经常增加，访问者不是好选择。

### 7.3 其他常见改动

| 需求 | 改动点 | 说明 |
|---|---|---|
| 加一种商品（如满减） | `CartItem.permits` + `CartVisitor` + 3 个访问者 | 同 7.2，元素封闭的代价 |
| 改买一赠一规则 | 只改 `BuyOneGetOneItem.billableUnits()` | 三个访问者同时生效（Demo 8） |
| 改折扣精度 | 只改 `Money.times` 的 `RoundingMode` | 全项目一处舍入 |
| 加"导出 JSON" | 新增 `JsonExportVisitor implements DocumentVisitor<String>` | 1 个文件 |
| 加"合并两个统计结果" | 已经存在：`DocumentStatistics.plus` | 无需改动 |
| 改金额展示格式（千分位） | 只改 `Money.format()` | 所有输出同步变化 |
| 把购物车改成树形（分类嵌套） | 新增 `Category implements CartItem` + 复合遍历 | 元素结构变化，访问者接口加一个 `visit(Category)` |
| 同一个访问者要并发处理多份文档 | 无状态已保证安全，直接并行调用 | `acceptAndFold` 换成并行流即可 |

### 7.4 一张"改动成本"对照表

| 变化方向 | 策略模式 | 访问者模式 |
|---|---|---|
| 加一种算法/操作 | 加一个策略类 | 加一个访问者类 |
| 加一种数据类型 | 不需要改策略接口 | 改所有访问者 |
| 结果类型多样 | 策略接口固定返回类型 | 每个访问者自定义 `R` |
| 需要遍历一组对象 | 调用方自己循环 | 对象结构提供 `acceptAll/acceptAndFold` |

结论：**"操作会持续增加、元素类型稳定"选访问者；
"元素类型会持续增加"选策略或普通多态。** 两者的分界线不是"哪个更高级"，
而是"哪一边会变"。

---

## 8. 优缺点与常见误区

### 8.1 优点（按重要性排序）

1. **开闭原则的少数真实落地**：加操作只加类，不改元素（Demo 1、7.1）；
2. **类型安全**：`sealed` + 接口重载，漏实现编译不过，没有 `instanceof` 链条；
3. **结果可组合**：泛型 `R` + 折叠，让"逐份结果"能变成"整体结果"（Demo 4、Demo 5）；
4. **无状态可复用**：访问者天然适合缓存、单例、并发（Demo 3、Demo 6）；
5. **职责边界清晰**：HTML 转义、纯文本排版、统计口径各自独立，互不影响。

### 8.2 代价

1. **加元素类型要改所有访问者**（7.2 的 6 个文件），这是最大的代价；
2. **理解门槛高**：双重分派是"绕"出来的，第一次读很难相信
   `visitor.visit(this)` 能选中正确重载；
3. **要求元素暴露足够的信息**：访问者只能看公开访问器，
   如果某个操作需要私有状态，就得开口子，可能破坏封装；
4. **类数量增加**：每个操作一个类，小项目里会显得"文件很多"；
5. **调试链路变长**：一次 `accept` 跨两个对象、两层分派，堆栈比直接调用深。

### 8.3 五个常见误区

**误区 1：把所有多态都换成访问者。**
访问者解决的是"操作维度的扩展"，不是"替换多态"。
`Money.plus`、`DocumentText.escapeHtml` 这类行为就该待在原处。
判断标准：**如果操作只有一种，或者元素类型经常增加，别用访问者。**

**误区 2：`visit` 返回 void，用字段收集结果。**
这是原项目 V1/V3 的组合错误。只要结果是"带出来的"而不是"返回的"，
访问者就会变成有状态累加器，复用必翻倍（Demo 6 反证）。

**误区 3：把业务规则写进访问者。**
买一赠一公式属于商品，不属于访问者（V5）。
访问者应该只做"读数据 + 排版/汇总"，一旦发现自己在访问者里写
`if (item.quantity() > 2)` 这种规则，就该把它移回元素。

**误区 4：元素把内部集合直接返回。**
`String[][] table()` 是 V2 的根源。record 的访问器不会自动防御，
凡是数组、`List`、`Map` 字段，都要在构造期拷贝或返回不可变视图。

**误区 5：以为访问者能解决"类型不断增加"。**
访问者恰恰最怕类型增加。它的假设是"类型封闭、操作开放"。
如果业务是"每月新增一种文档格式"，用访问者会让每次新增都变成
"改 6 个文件 + 重新测试所有操作"。

### 8.4 什么时候选谁：一棵决策树

```text
需要在不修改元素的前提下增加操作？
├─ 否 → 直接在元素上写方法 / 用策略
└─ 是
   ├─ 元素类型会经常增加？
   │  ├─ 是 → 普通多态 / 策略（访问者代价太大）
   │  └─ 否
   │     ├─ 操作数量 ≥ 3，且各自需要独立测试/发布？
   │     │  ├─ 是 → 访问者 ✅
   │     │  └─ 否 → Java 21 switch 模式匹配（更简单）
   │     └─ 操作需要返回不同类型？
   │        ├─ 是 → 访问者（泛型 R）
   │        └─ 否 → switch 也行
   └─ 元素有复杂的内部结构（组合体）？
      ├─ 是 → 组合 + 访问者（遍历交给组合，操作交给访问者）
      └─ 否 → 上面的判断已经够用
```

### 8.5 访问者与"破坏封装"的关系

GoF 原文把"访问者可能破坏封装"列为缺点，理由是访问者需要访问元素的内部状态。
本项目的处理方式是：

- 元素只暴露**业务上本来就应该公开**的访问器（`title()`、`body()`、`rows()`）；
- 需要"派生数据"时，由元素自己提供（`bodyLength()`、`cellCount()`、`subtotal()`），
  而不是让访问者去拼装；
- 私有状态（如 `Money` 的内部 `cents`）通过 `format()` 等语义方法暴露，
  不直接给字段。

结论：**访问者需要的不是"内部状态"，而是"足够的公开语义"。**
如果某个访问者非要看私有字段，通常是元素缺少一个语义方法，而不是访问者的错。

### 8.6 什么时候可以完全不用访问者

- 元素类型只有 2-3 种、操作只有 1-2 种：一个 `switch` 就够了；
- 团队不熟悉双重分派，且操作不会频繁增加：`switch` 模式匹配更好维护；
- 需要极致性能：双重分派比直接调用多一次虚方法，热路径上可以用
  `switch` + 内联替代（但要先测量，不要凭感觉优化）。

访问者是**结构性投资**：它用"每个操作一个类"换来"加操作不改元素"。
投资回报取决于"操作会增加多少次"。

---

## 9. 面试与自测

### 9.1 五个高频面试题

**Q1：访问者模式的双重分派是怎么实现的？Java 不是单分派吗？**

Java 的方法调用有两条独立的解析路径：

- **虚方法**（`accept`）按**运行时类型**分派；
- **重载**（`visit`）按**编译期静态类型**分派。

访问者让元素在自己的 `accept` 里写 `visitor.visit(this)`。
此时 `this` 的静态类型就是元素的具体类型（`PdfDocument`），
所以重载解析能选中 `visit(PdfDocument)`；而 `accept` 本身是虚方法，
按运行时类型进入正确的元素实现。两次单分派串起来，效果等于双分派。

**Q2：为什么 `visit` 方法不能合并成一个 `visit(Document)`？**

合并后静态类型是 `Document`，重载解析只能选中 `visit(Document)`，
访问者内部又得 `instanceof` 判断具体类型——模式退化成 if/else 链，
而且新增类型时编译器不会提醒你漏了分支。

**Q3：访问者和策略模式的区别？**

策略是"一个上下文 + 可替换的一个算法"，调用方主动 `setStrategy`，
策略不需要知道上下文的具体类型。
访问者是"一组元素 + 一组操作"，每次调用都要靠元素的 `accept` 完成类型分派，
访问者的每个 `visit` 恰好对应一种元素类型。
**策略按"算法"分派，访问者按"元素类型 × 操作"分派。**

**Q4：新增元素类型和新增操作，访问者分别要改什么？**

- 新增操作：加一个访问者类，元素零改动（Demo 1）；
- 新增元素：改 `permits`、访问者接口、所有访问者实现，
  本项目是 6 个文件（7.2）。
  这就是"类型封闭、操作开放"的准确含义。

**Q5：访问者模式会破坏封装吗？怎么避免？**

会——如果访问者需要读元素的私有字段，就必须开口子。
避免方式是让元素提供**语义方法**而不是暴露字段：
`bodyLength()` 而不是 `body`，`subtotal()` 而不是 `unitPrice × quantity` 的裸数据，
`rows()` 返回不可变副本而不是内部 `List`。
封装的边界应该由"业务语义"决定，而不是由"访问者想不想看"决定。

### 9.2 十道自测题

1. 为什么 `Document` 要用 `sealed`，而不是普通 `interface`？
2. `accept` 返回 `R` 相比 `void`，多解决了哪三类问题？
3. 为什么访问者接口里不应该有 `getTotalPrice()`？
4. `StatisticsVisitor` 为什么返回"一份文档的贡献"而不是累加值？
5. `DocumentBundle.acceptAndFold` 的三个参数分别是什么？为什么需要 `identity`？
6. `SpreadsheetDocument` 为什么在构造器里拷贝，而不是在 `rows()` 里拷贝？
7. `DiscountItem.subtotal()` 为什么必须先乘数量再打折？
8. `BuyOneGetOneItem.billableUnits()` 与原公式 `q/2 + q%2` 为什么等价？
9. `Money.format()` 为什么不用 `String.format("%.2f")`？
10. 如果业务变成"每月新增一种文档格式"，你还用访问者吗？为什么？

**参考答案要点**

1. `sealed` 让元素类型封闭，新增类型时所有访问者编译失败，把"漏实现"变成构建错误；
   普通接口无法穷尽，只能靠运行时 `instanceof` 或默认实现静默跳过。
2. ①结果可返回而非靠副作用；②结果类型可由访问者决定（`HtmlFragment`/`String`/统计值）；
   ③结果可组合（`concat`/`plus`/`CartSummary.of`），`void` 无法折叠。
3. 它把某个访问者的私有需求提升成所有访问者的公共契约，
   打印访问者只能返回 `0.0`（V4）；应让 `visit` 返回结果，聚合交给调用方。
4. 无状态才能复用、并发安全；累加交给 `plus` 与 `acceptAndFold`，
   聚合策略可替换。
5. `visitor`、`identity`（零值）、`folder`（合并函数）；
   `identity` 是折叠的起点，没有它就必须对"空集合"和"第一个元素"特判。
6. 构造期付一次 O(n)，之后读取零成本；若在 `rows()` 里拷贝，每次访问都是 O(n)，
   而且访问者可能拿到不同快照，`equals` 也会变得不可预期。
7. 3.33 元 × 10 件 × 8 折：先乘数量得 33.30，再打折得 26.64；
   先打折会舍入成 2.66，再乘 10 得 26.60，差 4 分钱。舍入只能发生一次。
8. 两者都等于 `⌈q/2⌉`：`q/2 + q%2` 是"整除商 + 余数"，
   `(q+1)/2` 是向上取整，数学上等价；后者只有一个运算符，更不易写错。
9. `String.format` 依赖默认 `Locale`，某些区域会用逗号做小数点，
   输出不再逐字节可复现；手工拼接不受 Locale 影响。
10. 不用。访问者的假设是"类型封闭"，每月新增类型意味着每次都要改所有访问者，
    应该改用普通多态或策略，或者用 `switch` 模式匹配集中处理。

### 9.3 三道推导题

**题 1**：一个报表系统有 4 种图表（折线、柱状、饼图、表格），要支持
3 种导出（PNG、SVG、PDF），未来每季度加一种图表、每年加一种导出格式。
用访问者吗？

> 要点：**不用**。图表类型每季度增加，访问者的代价（改所有导出器）太高；
> 应该让图表自己实现 `render(format)`，或者用策略组合"图表 × 导出"。

**题 2**：一个审批系统有 5 种单据（请假、报销、采购、合同、用印），
要支持 6 种操作（校验、审批、归档、打印、审计、统计），
单据类型已经两年没变，操作平均每季度加一个。用访问者吗？

> 要点：**用**。类型稳定、操作持续增加，正是访问者的最佳场景；
> 用 `sealed` 保证穷尽，用泛型 `R` 让每种操作返回自己的结果类型。

**题 3**：在题 2 的基础上，要求"审批操作"可以按角色配置不同规则
（经理看金额、财务看科目、法务看条款）。访问者还够用吗？

> 要点：访问者仍可用，但"审批规则"本身应该下沉为策略：
> `ApprovalVisitor` 持有 `ApprovalPolicy`，`visit` 里委托给策略。
> 访问者负责"按单据类型分派"，策略负责"同一类单据的不同规则"。
> **模式组合，而不是让一个模式承担所有变化。**

### 9.4 五分钟复述模板

如果你只能用五分钟讲清访问者模式，可以按这个顺序：

1. **问题**：元素类型稳定，操作不断增加；写在元素里会变成上帝类，
   `if/else` 会漏分支；
2. **做法**：元素只留 `accept(visitor)`；每种操作一个访问者类，
   每个访问者给每种元素一个 `visit`；
3. **关键机制**：`visitor.visit(this)` 完成双重分派，`this` 的静态类型
   决定选中哪个重载；
4. **收益**：加操作只加类；漏实现编译不过；返回值可折叠；
   访问者无状态可复用；
5. **代价**：加元素要改所有访问者，这是"类型封闭"的必然结果；
6. **判据**：类型稳、操作增 → 访问者；类型增 → 普通多态/策略；
   只有两三种类型一两种操作 → `switch` 模式匹配更简单。

---

## 附录 A：文件清单

```text
src/com/patterns/behavioral/visitor/
├── Client.java                        421 行
├── document/
│   ├── Document.java                   47
│   ├── DocumentVisitor.java            52
│   ├── PdfDocument.java                57
│   ├── WordDocument.java               47
│   ├── SpreadsheetDocument.java        99
│   ├── DocumentText.java              138
│   ├── HtmlFragment.java              110
│   ├── HtmlExportVisitor.java          76
│   ├── PlainTextVisitor.java           56
│   ├── DocumentStatistics.java         95
│   ├── StatisticsVisitor.java          70
│   └── DocumentBundle.java            121
└── pricing/
    ├── CartItem.java                   74
    ├── CartVisitor.java                56
    ├── RegularItem.java                54
    ├── DiscountItem.java               95
    ├── BuyOneGetOneItem.java           99
    ├── Money.java                     215
    ├── PricingText.java                91
    ├── LineResult.java                 65
    ├── CartSummary.java               110
    ├── ShoppingCart.java              116
    ├── PricingVisitor.java             78
    ├── ReceiptVisitor.java             74
    └── SavingsVisitor.java             47
```

| 指标 | 数值 |
|---|---:|
| Java 文件 | 26 |
| 物理行 | 2563 |
| class 文件 | 27 |
| class 字节 | 85199 |
| 编译警告 | 0 |

## 附录 B：运行方式与验证记录

**编译**（Windows，JDK 25）：

```powershell
$jdk = "F:\enCodingApps\JDK\jdk-25"
$src = "E:\codeFile\codeFather\design_patterns_pro\src"
$out = "E:\codeFile\codeFather\design_patterns_pro\out\visitor"

New-Item -ItemType Directory -Path $out -Force | Out-Null
$files = Get-ChildItem "$src\com\patterns\behavioral\visitor" -Recurse -Filter *.java |
         ForEach-Object { $_.FullName }
& "$jdk\bin\javac.exe" -Xlint:all -encoding UTF-8 -d $out $files
```

**运行**：

```powershell
& "$jdk\bin\java.exe" -Dstdout.encoding=UTF-8 -cp $out `
    com.patterns.behavioral.visitor.Client
```

**验证记录（三次运行）**：

| 项目 | 结果 |
|---|---|
| 编译退出码 | 0，`-Xlint:all` 无任何输出（零警告） |
| 运行退出码 | 0（三次） |
| 标准错误字节 | 0（三次） |
| 输出行数 | 123 |
| 输出字节 | 6948 |
| SHA-256（三次一致） | `07BD39FA1718244270A1E856B3E122C10A8BFBBE0D6B5B7FE8E6315570DC4DDB` |

复现命令：

```powershell
(Get-FileHash .\visitor_output.txt -Algorithm SHA256).Hash
```

**原项目基线（对照）**：

| 入口 | 行数 | 字节 | SHA-256 |
|---|---:|---:|---|
| `BehaviorType.VisitorPattern.Client` | 7 | 327 | `729D377B…683961` |
| `BehaviorType.VisitorPattern.CalculatePrice.Client` | 8 | 282 | `C7432672…D3194B` |

## 附录 C：相关模式交叉索引

| 模式 | 与本模式的关系 | 本项目中的位置 |
|---|---|---|
| 组合（Composite） | 访问者常配合组合：组合负责遍历，访问者负责操作 | `DocumentBundle` 是"扁平组合"；树形组合可替换它 |
| 迭代器（Iterator） | 迭代器负责"逐个取出"，访问者负责"逐个处理"；两者可叠加 | `DocumentBundle.acceptAll` 内部就是一次遍历 |
| 策略（Strategy） | 策略替换"一个算法"，访问者分派"一组操作"；可组合（见 9.3 题 3） | `pricing` 中的计价规则在元素里，展示策略在访问者里 |
| 装饰器（Decorator） | 装饰器叠加职责，访问者新增操作；方向不同 | `HtmlFragment.concat` 是轻量组合，不是装饰器 |
| 模板方法（Template Method） | 模板方法固定流程，访问者固定分派骨架 | `accept` 的骨架对所有元素一致 |
| 状态（State） | 状态管"对象随状态改变行为"，访问者管"外部操作分派" | 购物车无状态流转，不用状态模式 |

## 附录 D：原项目八个问题对照表

| 编号 | 原问题 | 本项目修复 | 验证位置 |
|---|---|---|---|
| V1 | `visit` 返回 void | 泛型 `R` + 返回值 | 5.2、6.1、Demo 4 |
| V2 | `table()` 泄漏数组 | `List<List<String>>` + 构造期深拷贝 | 5.5、6.2 |
| V3 | 访问者带可变累加器 | 无状态访问者 + `plus`/`of` 聚合 | 5.11、5.21、6.3、6.6 |
| V4 | 接口混入 `getTotalPrice()` | 删除；`CartSummary` 表达结果 | 5.14、5.21、6.5 |
| V5 | 买一赠一公式两份 | `billableUnits()` 唯一公式 | 5.17、6.8 |
| V6 | `double` 金额 | `Money(long cents)` + 一次舍入 | 5.16、5.18、6.5 |
| V7 | 无入参校验 | 构造期校验 + 行列号 | 5.3、5.5、5.19、6.2 |
| V8 | `addItem(null)` 延迟爆炸 | `add` 立即拒绝 + 第 N 次 | 5.22、6.7 |

## 附录 E：概念速查

| 术语 | 含义 | 本项目对应 |
|---|---|---|
| Element | 被访问的元素接口 | `Document`、`CartItem` |
| ConcreteElement | 具体元素 | `PdfDocument`、`DiscountItem` 等 |
| Visitor | 访问者接口 | `DocumentVisitor<R>`、`CartVisitor<R>` |
| ConcreteVisitor | 具体访问者 | `HtmlExportVisitor`、`PricingVisitor` 等 |
| ObjectStructure | 元素集合，负责遍历 | `DocumentBundle`、`ShoppingCart` |
| Double Dispatch | 两次单分派拼成的双分派 | `element.accept(v)` + `v.visit(element)` |
| `accept` | 元素交给访问者的唯一入口 | 每个元素一个实现 |
| `visit` | 访问者对某种元素的处理 | 每个访问者 N 个重载 |
| Fold | 把多个结果合并成一个 | `acceptAndFold`、`CartSummary.of` |

## 附录 F：版本与工具链

| 项目 | 版本/说明 |
|---|---|
| JDK | `F:\enCodingApps\JDK\jdk-25`（JDK 25） |
| 编译选项 | `-Xlint:all -encoding UTF-8` |
| 运行选项 | `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` |
| 源文件编码 | UTF-8 |
| 输出编码 | UTF-8（哈希基于 UTF-8 字节） |
| 语言特性 | `sealed interface`、`record`、`instanceof` 模式匹配、泛型方法 |
| 外部依赖 | 无（仅 JDK 标准库） |
| 测试方式 | 编译 + 三次运行 + SHA-256 比对 + 逐行核对输出 |

---

## 写在最后

访问者模式最容易被讲成"两个接口 + 一堆 visit 方法"，然后就没了。
但真正需要理解的是它的**假设**：

> 元素类型是封闭的，操作是开放的。

只要这个假设成立，访问者就能把"修改"关在门外；一旦假设被打破
（每个月加一种文档），它就会变成最沉重的负担。

所以判断要不要用它，不要问"这是不是 23 种模式之一"，
而要问两个问题：

1. **未来一年，是操作变多，还是类型变多？**
2. **这些操作需要独立发布、独立测试、独立返回类型吗？**

两个答案都是"操作/是"，访问者就是对的；
否则，`switch` + 模式匹配可能更好——**简单永远不是缺点**。
