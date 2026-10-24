package com.patterns.behavioral.visitor;

import com.patterns.behavioral.visitor.document.Document;
import com.patterns.behavioral.visitor.document.DocumentBundle;
import com.patterns.behavioral.visitor.document.DocumentStatistics;
import com.patterns.behavioral.visitor.document.DocumentVisitor;
import com.patterns.behavioral.visitor.document.HtmlExportVisitor;
import com.patterns.behavioral.visitor.document.HtmlFragment;
import com.patterns.behavioral.visitor.document.PdfDocument;
import com.patterns.behavioral.visitor.document.PlainTextVisitor;
import com.patterns.behavioral.visitor.document.SpreadsheetDocument;
import com.patterns.behavioral.visitor.document.StatisticsVisitor;
import com.patterns.behavioral.visitor.document.WordDocument;
import com.patterns.behavioral.visitor.pricing.BuyOneGetOneItem;
import com.patterns.behavioral.visitor.pricing.CartSummary;
import com.patterns.behavioral.visitor.pricing.DiscountItem;
import com.patterns.behavioral.visitor.pricing.LineResult;
import com.patterns.behavioral.visitor.pricing.Money;
import com.patterns.behavioral.visitor.pricing.PricingVisitor;
import com.patterns.behavioral.visitor.pricing.ReceiptVisitor;
import com.patterns.behavioral.visitor.pricing.RegularItem;
import com.patterns.behavioral.visitor.pricing.SavingsVisitor;
import com.patterns.behavioral.visitor.pricing.ShoppingCart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 访问者模式统一入口：两个彼此独立的 Demo。
 *
 * <p>Demo 1-4 是「文档导出」：元素固定为 PDF/Word/表格三种，
 * 操作是"导出 HTML""导出纯文本""统计"三种，演示"加操作只加类"。</p>
 *
 * <p>Demo 5-8 是「购物车计价」：演示"访问者返回结果而不是累加字段"、
 * "接口不要混入不适用的方法"、"金额用整数分"三件事。</p>
 *
 * <p>两个 Demo 都刻意保持独立，不共享任何业务对象 ——
 * 访问者模式的价值在于"元素族稳定、操作多变"，
 * 硬把两个场景合成一个系统只会让读者以为这是框架。</p>
 */
public final class Client {

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

    // ================================================================ Demo 1

    /**
     * 同一批文档，三种操作。
     *
     * <p>注意三次调用之间，{@link PdfDocument}、{@link WordDocument}、
     * {@link SpreadsheetDocument} 一行代码都没有改动 ——
     * 新增操作的成本是"加一个类"，而不是"改三个元素类"。</p>
     */
    private static void demo1ThreeOperations() {
        section("Demo 1：同一批文档，三种操作，元素类零改动");

        List<Document> documents = List.of(
                new PdfDocument(
                        "设计模式：可复用面向对象软件的基础",
                        395,
                        "GoF 四人组把面向对象设计经验归纳为 23 种模式。"),
                new WordDocument(
                        "重构：改善既有代码的设计",
                        "重构是在不改变软件可观察行为的前提下改善其内部结构。"),
                new SpreadsheetDocument(
                        "2025 年第一季度销售",
                        List.of(
                                List.of("产品", "单价", "销量"),
                                List.of("机械键盘", "499.00", "128"),
                                List.of("人体工学椅", "1899.00", "36"),
                                List.of("显示器支架", "299.00", "210"))));

        DocumentBundle bundle = new DocumentBundle(documents);

        System.out.println("文档清单：");
        for (int index = 0; index < documents.size(); index++) {
            System.out.println("  " + (index + 1) + ". " + describe(documents.get(index)));
        }

        System.out.println();
        System.out.println("--- 操作 A：HtmlExportVisitor（返回 HtmlFragment，不打印）---");
        List<HtmlFragment> fragments = bundle.acceptAll(new HtmlExportVisitor());
        for (HtmlFragment fragment : fragments) {
            System.out.print(fragment.value());
        }

        System.out.println();
        System.out.println("--- 操作 B：PlainTextVisitor（返回 String）---");
        List<String> texts = bundle.acceptAll(new PlainTextVisitor());
        for (String text : texts) {
            System.out.print(text);
        }

        System.out.println();
        System.out.println("--- 操作 C：StatisticsVisitor（返回可加的 DocumentStatistics）---");
        DocumentStatistics statistics =
                bundle.acceptAndFold(new StatisticsVisitor(), DocumentStatistics.empty(),
                        DocumentStatistics::plus);
        System.out.println(statistics.summary());
    }

    // ================================================================ Demo 2

    /**
     * 文档不可变：外部改不动内容，非法构造直接失败。
     *
     * <p>对应探针 V2（record 泄漏可变数组）与 V7（不校验 null）。</p>
     */
    private static void demo2Immutability() {
        section("Demo 2：文档对外不可变，非法入参构造期即失败");

        List<List<String>> raw = new ArrayList<>();
        raw.add(new ArrayList<>(List.of("姓名", "成绩")));
        raw.add(new ArrayList<>(List.of("鱼皮", "90")));
        SpreadsheetDocument sheet = new SpreadsheetDocument("期末成绩", raw);

        raw.get(1).set(0, "被外部改成了黑客");
        System.out.println("修改原始 List 之后，文档第 2 行第 1 列 = "
                + sheet.rows().get(1).get(0) + "（未被污染）");

        try {
            sheet.rows().get(0).set(0, "试图改文档");
            System.out.println("修改文档内部 List：没有抛异常（与预期不符）");
        } catch (UnsupportedOperationException e) {
            System.out.println("修改文档内部 List -> UnsupportedOperationException");
        }

        List<List<String>> withNullCell = new ArrayList<>();
        withNullCell.add(new ArrayList<>(List.of("a", "b")));
        withNullCell.add(Arrays.asList("c", null));
        expectFailure("null 表格", () -> new SpreadsheetDocument("坏表", null));
        expectFailure("含 null 单元格", () -> new SpreadsheetDocument("坏表", withNullCell));
        expectFailure("空白标题", () -> new PdfDocument("   ", 1, "正文"));
        expectFailure("页数 0", () -> new PdfDocument("标题", 0, "正文"));
    }

    // ================================================================ Demo 3

    /**
     * 访问者无状态，可以反复使用。
     *
     * <p>对应探针 V3：原 {@code PriceCalculatorVisitor} 第二次 accept
     * 会把总价从 99.0 累加到 198.0。</p>
     */
    private static void demo3StatelessVisitor() {
        section("Demo 3：访问者无状态，同一对象可反复使用");

        DocumentBundle bundle = new DocumentBundle(List.of(
                new PdfDocument("白皮书", 12, "正文内容若干。"),
                new WordDocument("会议纪要", "今天讨论了访问者模式。")));

        HtmlExportVisitor htmlVisitor = new HtmlExportVisitor();
        List<HtmlFragment> first = bundle.acceptAll(htmlVisitor);
        List<HtmlFragment> second = bundle.acceptAll(htmlVisitor);
        System.out.println("同一 HtmlExportVisitor 连续两次导出，结果相等 = "
                + first.equals(second));
        System.out.println("两次导出的首段长度 = " + first.get(0).length()
                + " / " + second.get(0).length());

        StatisticsVisitor statisticsVisitor = new StatisticsVisitor();
        DocumentStatistics once =
                bundle.acceptAndFold(statisticsVisitor, DocumentStatistics.empty(),
                        DocumentStatistics::plus);
        DocumentStatistics twice =
                bundle.acceptAndFold(statisticsVisitor, DocumentStatistics.empty(),
                        DocumentStatistics::plus);
        System.out.println("同一 StatisticsVisitor 连续两次统计，结果相等 = "
                + once.equals(twice));
        System.out.println("两次统计： " + once.summary());
    }

    // ================================================================ Demo 4

    /**
     * 返回值让"折叠"成为可能。
     *
     * <p>原项目所有 visit 都是 void，调用方只能拿到"已经打印出去的字"，
     * 无法把三份文档的 HTML 合成一页。这里用 {@code acceptAndFold}
     * 把三次 visit 的返回值拼成一个完整页面。</p>
     */
    private static void demo4FoldByReturnValue() {
        section("Demo 4：返回值让折叠成为可能（void 做不到）");

        DocumentBundle bundle = new DocumentBundle(List.of(
                new PdfDocument("手册", 8, "安装说明。"),
                new WordDocument("FAQ", "常见问题汇总。"),
                new SpreadsheetDocument("参数表", List.of(
                        List.of("参数", "默认值"),
                        List.of("timeout", "30s")))));

        HtmlFragment page = bundle.acceptAndFold(
                new HtmlExportVisitor(), HtmlFragment.empty(), HtmlFragment::concat);
        System.out.println("三份文档折叠成一页，总长度 = " + page.length() + " 字符");
        System.out.println("页面片段以 HTML 声明开头 = " + page.value().startsWith("<section"));

        List<HtmlFragment> separate = bundle.acceptAll(new HtmlExportVisitor());
        int sum = 0;
        for (HtmlFragment fragment : separate) {
            sum += fragment.length();
        }
        System.out.println("分开导出的长度之和 = " + sum
                + "，与折叠结果相等 = " + (sum == page.length()));
    }

    // ================================================================ Demo 5

    /**
     * 同一辆购物车，三个访问者，三种结果类型。
     *
     * <p>对应探针 V4/V5/V6：没有 {@code getTotalPrice()} 的接口，
     * 买一赠一公式只有一份，金额用整数分。</p>
     */
    private static void demo5CartThreeVisitors() {
        section("Demo 5：同一辆购物车，三个访问者，三种结果类型");

        ShoppingCart cart = new ShoppingCart()
                .add(new RegularItem("《深入理解计算机系统》", Money.ofYuan("99.00"), 1))
                .add(new DiscountItem("《设计模式》", Money.ofYuan("58.00"), 1,
                        new BigDecimal("0.8")))
                .add(new BuyOneGetOneItem("可口可乐", Money.ofYuan("3.00"), 3));

        System.out.println("购物车：" + cart.lineCount() + " 种商品 / "
                + cart.itemCount() + " 件");

        System.out.println();
        System.out.println("--- 操作 A：PricingVisitor（返回 List<LineResult>）---");
        List<LineResult> lines = cart.acceptAll(new PricingVisitor());
        for (LineResult line : lines) {
            System.out.println("  " + render(line));
        }
        CartSummary summary = CartSummary.of(lines);
        System.out.println("  ------------------------------------------");
        System.out.println("  应付 " + summary.total().format()
                + " | 优惠 " + summary.saved().format()
                + " | 原价合计 " + summary.total().plus(summary.saved()).format());
        Money gross = summary.total().plus(summary.saved());
        System.out.println("  恒等式 total + saved == 166.00 -> "
                + gross.equals(Money.ofYuan("166.00")));

        System.out.println();
        System.out.println("--- 操作 B：ReceiptVisitor（返回 String，不碰金额计算）---");
        for (String line : cart.acceptAll(new ReceiptVisitor())) {
            System.out.println("  " + line);
        }

        System.out.println();
        System.out.println("--- 操作 C：SavingsVisitor（只返回 Money）---");
        Money saved = Money.ZERO;
        for (Money amount : cart.acceptAll(new SavingsVisitor())) {
            saved = saved.plus(amount);
        }
        System.out.println("  本次共优惠 " + saved.format() + " 元");
        System.out.println("  与 PricingVisitor 汇总的优惠一致 = "
                + saved.equals(summary.saved()));
    }

    // ================================================================ Demo 6

    /**
     * 购物车访问者同样无状态、可复用。
     *
     * <p>对应探针 V3 的购物车版本：原 {@code PriceCalculatorVisitor}
     * 连跑两次，总价翻倍。</p>
     */
    private static void demo6CartVisitorReusable() {
        section("Demo 6：购物车访问者可复用，第二次调用不会翻倍");

        ShoppingCart cart = new ShoppingCart()
                .add(new RegularItem("笔记本", Money.ofYuan("20.00"), 2))
                .add(new BuyOneGetOneItem("签字笔", Money.ofYuan("5.00"), 4));

        PricingVisitor visitor = new PricingVisitor();
        CartSummary first = CartSummary.of(cart.acceptAll(visitor));
        CartSummary second = CartSummary.of(cart.acceptAll(visitor));
        System.out.println("第一次结账：应付 " + first.total().format()
                + " | 优惠 " + first.saved().format());
        System.out.println("第二次结账：应付 " + second.total().format()
                + " | 优惠 " + second.saved().format());
        System.out.println("两次结果完全相等 = " + first.equals(second));

        SavingsVisitor savingsVisitor = new SavingsVisitor();
        Money firstSaved = sum(cart.acceptAll(savingsVisitor));
        Money secondSaved = sum(cart.acceptAll(savingsVisitor));
        System.out.println("SavingsVisitor 两次结果 = " + firstSaved.format()
                + " / " + secondSaved.format()
                + "，相等 = " + firstSaved.equals(secondSaved));
    }

    // ================================================================ Demo 7

    /**
     * 非法入参在构造期就被拒绝。
     *
     * <p>对应探针 V7（null 表格一路通过到访问者才 NPE）
     * 与 V8（{@code addItem(null)} 延迟到遍历才炸）。</p>
     */
    private static void demo7CartValidation() {
        section("Demo 7：非法入参在构造期被拒绝（错误靠近源头）");

        ShoppingCart cart = new ShoppingCart();
        cart.add(new RegularItem("正常商品", Money.ofYuan("10.00"), 1));
        expectFailure("第 2 次 add 传 null", () -> cart.add(null));
        expectFailure("数量为 0", () -> new RegularItem("坏商品", Money.ofYuan("1.00"), 0));
        expectFailure("负单价", () -> new RegularItem("坏商品", Money.ofCents(-1), 1));
        expectFailure("折扣率 1.2", () -> new DiscountItem("坏商品",
                Money.ofYuan("100.00"), 1, new BigDecimal("1.2")));
        expectFailure("折扣率 0", () -> new DiscountItem("坏商品",
                Money.ofYuan("100.00"), 1, BigDecimal.ZERO));
        expectFailure("金额文本 abc", () -> Money.ofYuan("abc"));
        expectFailure("负数金额", () -> Money.ofYuan("-1.00"));

        System.out.println("购物车仍然只有 " + cart.lineCount() + " 种商品，未被污染");
    }

    // ================================================================ Demo 8

    /**
     * 买一赠一公式只有一份：{@link BuyOneGetOneItem#billableUnits()}。
     *
     * <p>对应探针 V5：原项目在两个访问者里各写了一遍
     * {@code quantity / 2 + quantity % 2}。</p>
     */
    private static void demo8BuyOneGetOneSingleFormula() {
        section("Demo 8：买一赠一公式只有一份，三个访问者共用");

        System.out.println("  quantity | 应付件数 | 赠送件数 | PricingVisitor 小计 | ReceiptVisitor 文本");
        for (int quantity = 1; quantity <= 6; quantity++) {
            BuyOneGetOneItem item = new BuyOneGetOneItem("可乐", Money.ofYuan("3.00"), quantity);
            LineResult line = item.accept(new PricingVisitor());
            String receipt = item.accept(new ReceiptVisitor());
            System.out.println("  " + quantity
                    + "        | " + item.billableUnits()
                    + "        | " + item.freeUnits()
                    + "        | " + line.subtotal().format()
                    + "              | " + receipt);
        }
        System.out.println("  两个访问者的金额来自同一个 billableUnits()，不存在第二份公式");
    }

    // ================================================================ 工具方法

    private static String describe(Document document) {
        return document.accept(new DocumentDescriber());
    }

    /**
     * 一个只在 Demo 1 里用来生成"清单描述"的访问者。
     *
     * <p>它同时也证明了第 4 种操作同样只加一个类 ——
     * 这个内部类就是"加操作"成本的全部。</p>
     */
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
            return "表格 《" + document.title() + "》 "
                    + document.rowCount() + " 行 × " + document.columnCount() + " 列";
        }
    }

    private static String render(LineResult line) {
        StringBuilder text = new StringBuilder(96);
        text.append(line.label());
        text.append(" | ").append(line.name());
        text.append(" | ×").append(line.quantity());
        text.append(" | 单价 ").append(line.unitPrice().format());
        text.append(" | 小计 ").append(line.subtotal().format());
        if (line.hasDiscount()) {
            text.append(" | 省 ").append(line.discount().format());
        }
        return text.toString();
    }

    private static Money sum(List<Money> amounts) {
        Money total = Money.ZERO;
        for (Money amount : amounts) {
            total = total.plus(amount);
        }
        return total;
    }

    private static void expectFailure(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + " -> 没有抛异常（与预期不符）");
        } catch (IllegalArgumentException e) {
            System.out.println("  " + label + " -> IllegalArgumentException: " + e.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("================================================================");
        System.out.println(title);
        System.out.println("================================================================");
    }

    private Client() {
    }
}
