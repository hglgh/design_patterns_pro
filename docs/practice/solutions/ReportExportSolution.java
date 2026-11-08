import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 练习 04 参考实现：报表文件导出。
 *
 * <p>运行方式（单文件源模式，不需要先编译）：</p>
 * <pre>java docs/practice/solutions/ReportExportSolution.java</pre>
 *
 * <p>这个练习只考一件事：<b>模板方法和策略不是二选一</b>。</p>
 * <ul>
 *   <li>「取数 → 转换 → 写出 → 收尾」这个<b>顺序</b>四年没变过 —— 用模板方法锁住；</li>
 *   <li>「写出成什么格式」三个月加一种 —— 用策略替换。</li>
 * </ul>
 *
 * <p>把整个导出都做成策略：三种格式会各自复制一遍取数和收尾。
 * 把整个导出都做成模板方法：每加一种格式都要改父类的 switch。
 * 两个都用，各管各的变化。</p>
 */
public final class ReportExportSolution {

    // ==================== 一、数据 ====================

    record OrderRow(String orderNo, String buyer, BigDecimal amount, LocalDate createdAt) {
    }

    static List<OrderRow> fetchOrders(LocalDate day) {
        return List.of(
                new OrderRow("SO-001", "张三", new BigDecimal("128.50"), day),
                new OrderRow("SO-002", "李四", new BigDecimal("89.00"), day),
                new OrderRow("SO-003", "王五", new BigDecimal("1024.00"), day));
    }

    // ==================== 二、策略：只管「怎么写出」 ====================

    /**
     * 输出格式策略。
     *
     * <p>注意接口里<b>没有</b> {@code export(List)}。如果有了，每个实现都会
     * 顺手把取数、日志、收尾写一遍 —— 那就是把模板方法的活抢走了。</p>
     */
    interface OutputFormat {
        String name();

        /** 表头行。CSV 要引号包住，Excel 不要 —— 格式细节留给实现。 */
        void begin(StringBuilder out, List<String> headers);

        void row(StringBuilder out, String[] cells);

        void end(StringBuilder out);
    }

    /** CSV：逗号分隔，字段含逗号要加引号。 */
    static final class CsvFormat implements OutputFormat {
        public String name() {
            return "CSV";
        }

        public void begin(StringBuilder out, List<String> headers) {
            out.append(String.join(",", headers)).append('\n');
        }

        public void row(StringBuilder out, String[] cells) {
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(escape(cells[i]));
            }
            out.append('\n');
        }

        public void end(StringBuilder out) {
            // CSV 没有尾巴
        }

        private static String escape(String cell) {
            return cell.contains(",") || cell.contains("\"")
                    ? '"' + cell.replace("\"", "\"\"") + '"'
                    : cell;
        }
    }

    /** 伪 Excel：用制表符分隔，外加一个"工作表"外壳。 */
    static final class ExcelFormat implements OutputFormat {
        public String name() {
            return "Excel";
        }

        public void begin(StringBuilder out, List<String> headers) {
            out.append("[Workbook: 订单报表]\n");
            out.append("[Sheet: Sheet1]\n");
            out.append(String.join("\t", headers)).append('\n');
        }

        public void row(StringBuilder out, String[] cells) {
            out.append(String.join("\t", cells)).append('\n');
        }

        public void end(StringBuilder out) {
            out.append("[EOF]\n");
        }
    }

    /** HTML：需要在开头结尾各写一个标签，且必须转义。 */
    static final class HtmlFormat implements OutputFormat {
        public String name() {
            return "HTML";
        }

        public void begin(StringBuilder out, List<String> headers) {
            out.append("<table>\n<tr>");
            for (String header : headers) {
                out.append("<th>").append(escape(header)).append("</th>");
            }
            out.append("</tr>\n");
        }

        public void row(StringBuilder out, String[] cells) {
            out.append("<tr>");
            for (String cell : cells) {
                out.append("<td>").append(escape(cell)).append("</td>");
            }
            out.append("</tr>\n");
        }

        public void end(StringBuilder out) {
            out.append("</table>\n");
        }

        private static String escape(String raw) {
            return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    // ==================== 三、模板方法：锁住顺序，留钩子 ====================

    /**
     * 导出模板。
     *
     * <p>{@code export} 是 final 的：<b>顺序是这个类唯一不可变的东西</b>，
     * 一旦放开，各格式就会开始偷偷改变步骤顺序（先写后取、忘了关流）。</p>
     *
     * <p>三个钩子各管一件事：</p>
     * <ul>
     *   <li>{@link #headers()} / {@link #cells} —— 列怎么来（数据映射，会变）；</li>
     *   <li>{@link #formatName()} —— 头部注释里写什么（小事，但每类都要改）；</li>
     *   <li>{@link #afterWrite} —— 收尾（CSV 什么都不做，Excel 要落盘）。</li>
     * </ul>
     */
    abstract static class ReportExporter {
        protected final OutputFormat format;

        protected ReportExporter(OutputFormat format) {
            this.format = format;
        }

        /** 骨架。子类不能改顺序。 */
        final String export(LocalDate day) {
            List<OrderRow> rows = fetchOrders(day);
            StringBuilder out = new StringBuilder();
            out.append("# ").append(formatName()).append(" 导出 ").append(day).append('\n');
            format.begin(out, headers());
            for (OrderRow row : rows) {
                format.row(out, cells(row));
            }
            format.end(out);
            String content = out.toString();
            afterWrite(rows);
            return content;
        }

        protected abstract String formatName();

        protected abstract List<String> headers();

        protected abstract String[] cells(OrderRow row);

        /** 钩子：默认什么都不做。这是模板方法里最有价值的那个空实现。 */
        protected void afterWrite(List<OrderRow> rows) {
        }
    }

    /** 财务口径：金额带币种、日期 ISO。 */
    static final class FinanceExporter extends ReportExporter {
        private final List<Integer> writtenCounts = new ArrayList<>();

        FinanceExporter(OutputFormat format) {
            super(format);
        }

        protected String formatName() {
            return "财务订单报表";
        }

        protected List<String> headers() {
            return List.of("订单号", "买家", "金额(CNY)", "下单日期");
        }

        protected String[] cells(OrderRow row) {
            return new String[]{
                    row.orderNo(),
                    row.buyer(),
                    row.amount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    row.createdAt().toString()};
        }

        protected void afterWrite(List<OrderRow> rows) {
            writtenCounts.add(rows.size());
        }

        List<Integer> writtenCounts() {
            return writtenCounts;
        }
    }

    /** 运营口径：金额按「分」取整、日期中式格式化、多一列「金额档位」。 */
    static final class OpsExporter extends ReportExporter {
        private static final DateTimeFormatter CN = DateTimeFormatter.ofPattern("yyyy年M月d日");

        OpsExporter(OutputFormat format) {
            super(format);
        }

        protected String formatName() {
            return "运营订单报表";
        }

        protected List<String> headers() {
            return List.of("订单号", "买家", "金额", "档位", "下单日期");
        }

        protected String[] cells(OrderRow row) {
            return new String[]{
                    row.orderNo(),
                    row.buyer(),
                    row.amount().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                    tier(row.amount()),
                    row.createdAt().format(CN)};
        }

        private static String tier(BigDecimal amount) {
            if (amount.compareTo(new BigDecimal("1000")) >= 0) {
                return "大额";
            }
            if (amount.compareTo(new BigDecimal("100")) >= 0) {
                return "中额";
            }
            return "小额";
        }
    }

    // ==================== 四、装饰器：导出之后的横切 ====================

    /**
     * 导出的装饰器。
     *
     * <p>为什么这一层不做成 {@link OutputFormat} 的装饰器：压缩、加密、
     * 上传的对象是<b>整个文件</b>，不是某一行。放在 format 上就得逐行处理，
     * 逻辑会变得很怪。这是「装饰器套在哪一层」的典型判断：
     * 看它作用的对象粒度。</p>
     */
    interface ExportedFile {
        String filename();

        String content();
    }

    record PlainFile(String filename, String content) implements ExportedFile {
    }

    /** 压缩：只改文件内容和后缀，不改内容语义。 */
    static final class Zipped implements ExportedFile {
        private final ExportedFile delegate;

        Zipped(ExportedFile delegate) {
            this.delegate = delegate;
        }

        public String filename() {
            return delegate.filename() + ".zip";
        }

        public String content() {
            return "[ZIP 头]\n" + delegate.content() + "[ZIP 尾]";
        }
    }

    /** 加密：换成密文，文件名加标记。 */
    static final class Encrypted implements ExportedFile {
        private final ExportedFile delegate;

        Encrypted(ExportedFile delegate) {
            this.delegate = delegate;
        }

        public String filename() {
            return delegate.filename() + ".enc";
        }

        public String content() {
            // 演示用：真加密请用 javax.crypto，别自己写
            return "[AES]" + Integer.toHexString(delegate.content().hashCode());
        }
    }

    // ==================== 五、执行记录（模拟上传） ====================

    static final class UploadRecorder {
        private final Map<String, Integer> uploaded = new LinkedHashMap<>();

        void upload(ExportedFile file) {
            uploaded.merge(file.filename(), file.content().length(), Integer::sum);
            System.out.println("    上传 " + file.filename() + "（" + file.content().length() + " 字符）");
        }

        Map<String, Integer> uploaded() {
            return uploaded;
        }
    }

    // ==================== 六、跑一遍 ====================

    public static void main(String[] args) {
        LocalDate day = LocalDate.of(2026, 11, 8);
        List<OutputFormat> formats = List.of(new CsvFormat(), new ExcelFormat(), new HtmlFormat());

        System.out.println("=== 同一份模板，三种格式 ===");
        for (OutputFormat format : formats) {
            FinanceExporter exporter = new FinanceExporter(format);
            System.out.println("--- " + format.name() + " ---");
            System.out.print(exporter.export(day));
            System.out.println();
        }

        System.out.println("=== 同一份模板，换一种业务口径 ===");
        System.out.print(new OpsExporter(new CsvFormat()).export(day));
        System.out.println();
        System.out.println("注意：财务和运营的差异只在 headers()/cells()，");
        System.out.println("      取数、遍历、写出、收尾一行都没重复。");
        System.out.println();

        System.out.println("=== 加一种 Excel 格式，改动面 ===");
        System.out.println("1. 新增一个 OutputFormat 实现（1 个类）；");
        System.out.println("2. ReportExporter 的两个子类完全不改；");
        System.out.println("3. 如果加的是「按门店分组」这种新口径，则新增一个 ReportExporter 子类，");
        System.out.println("   三种格式自动全部支持 —— 这就是 2×3 变成 2+3 的地方。");
        System.out.println();

        System.out.println("=== 装饰器：压缩 + 加密可以自由叠加 ===");
        UploadRecorder recorder = new UploadRecorder();
        ExportedFile base = new PlainFile("orders-2026-11-08.csv",
                new FinanceExporter(new CsvFormat()).export(day));
        recorder.upload(base);
        recorder.upload(new Zipped(base));
        recorder.upload(new Encrypted(base));
        recorder.upload(new Encrypted(new Zipped(base)));
        System.out.println("上传记录：" + recorder.uploaded());
        System.out.println();

        System.out.println("=== 反例：如果把「导出」整个做成策略 ===");
        System.out.println("每个格式实现里都要 fetchOrders()、都要写 afterWrite()。");
        System.out.println("财务口径改了取数范围 → 3 个格式类都要改。");
        System.out.println("这就是「选错了变化维度」：格式和口径是两个维度的变化，");
        System.out.println("一个维度用策略（格式），另一个维度用模板方法的子类（口径）。");
        System.out.println();

        System.out.println("=== 反例：如果把「导出」整个做成模板方法 ===");
        System.out.println("format 变成一个枚举 + 父类里的 switch，每加一种格式改父类。");
        System.out.println("格式的差异（转义规则、表头写法、结尾闭合）会挤在一个方法里，");
        System.out.println("而且没法在不改父类的前提下给某个客户单独加一种格式。");
    }

    private ReportExportSolution() {
    }
}
