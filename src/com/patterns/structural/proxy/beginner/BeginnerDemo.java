package com.patterns.structural.proxy.beginner;

/**
 * 代理入门演示：报表又大又敏感，需要「先鉴权，再按需加载」。
 *
 * <p>改造前：每个入口自己写权限判断和加载逻辑，漏一处就泄密或白加载；
 * 改造后：代理和目标实现同一个接口，代理负责鉴权、延迟加载、记录访问。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：每个入口自己判断 ==========");

        User guest = new User("访客", false);
        User admin = new User("管理员", true);

        ReportViewer viewer = new ReportViewer();
        viewer.open(guest);
        viewer.open(admin);

        ExportService export = new ExportService();
        export.export(guest);          // 这个入口忘了鉴权

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 访客打开报表时，50MB 数据已经加载完才被拒绝，白耗资源；");
        System.out.println("  2. 导出入口忘了鉴权，访客直接导出了敏感报表；");
        System.out.println("  3. 每个新入口都要复制一遍「鉴权 + 加载」，漏一处就是事故。");
    }

    static class User {
        final String name;
        final boolean admin;

        User(String name, boolean admin) {
            this.name = name;
            this.admin = admin;
        }
    }

    /** 真实报表：构造即加载，成本很高。 */
    static class OldRealReport {

        OldRealReport() {
            System.out.println("    [报表] 加载 50MB 数据...");
        }

        void display() {
            System.out.println("    [报表] 显示销售明细");
        }
    }

    static class ReportViewer {
        void open(User user) {
            OldRealReport report = new OldRealReport();      // 先加载，再鉴权
            if (!user.admin) {
                System.out.println("    拒绝 " + user.name + "：没有报表权限");
                return;
            }
            report.display();
        }
    }

    static class ExportService {
        void export(User user) {
            OldRealReport report = new OldRealReport();
            report.display();                          // 忘了鉴权
            System.out.println("    导出完成，操作人：" + user.name);
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：所有入口只认 Report 接口 ==========");

        User guest = new User("访客", false);
        User admin = new User("管理员", true);
        Report report = new ReportProxy();

        System.out.println("  访客打开：");
        report.display(guest);

        System.out.println("  管理员打开：");
        report.display(admin);

        System.out.println("  管理员再导出：");
        report.display(admin);

        System.out.println("  访客导出：");
        report.display(guest);

        System.out.println();
        System.out.println("  真实报表一共加载了 " + ReportProxy.loadCount + " 次。");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 多了一层间接，所有调用都要经过代理；");
        System.out.println("  2. 鉴权/缓存逻辑集中在代理，代理写错会影响所有入口；");
        System.out.println("  3. 如果对象很便宜、也没有访问控制需求，代理只是多余的包装。");
    }

    /** 客户端只认这个接口，不关心拿到的是真实报表还是代理。 */
    interface Report {
        void display(User user);
    }

    static class RealReport implements Report {

        RealReport() {
            System.out.println("    [报表] 加载 50MB 数据...");
        }

        @Override
        public void display(User user) {
            System.out.println("    [报表] 显示销售明细（" + user.name + "）");
        }
    }

    /**
     * 代理：实现同一接口，控制对 RealReport 的访问。
     *
     * <p>它做三件真实报表不该关心的事：鉴权、延迟创建、访问记录。
     */
    static class ReportProxy implements Report {

        static int loadCount;

        private RealReport real;       // 第一次真正需要时才创建

        @Override
        public void display(User user) {
            if (!user.admin) {
                System.out.println("    拒绝 " + user.name + "：没有报表权限（未加载数据）");
                return;
            }
            if (real == null) {
                real = new RealReport();
                loadCount++;
            }
            System.out.println("    [代理] 记录访问日志：" + user.name);
            real.display(user);
        }
    }
}
