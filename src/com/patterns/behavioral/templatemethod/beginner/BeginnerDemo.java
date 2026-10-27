package com.patterns.behavioral.templatemethod.beginner;

/**
 * 模板方法入门演示：差旅报销与招待报销。
 *
 * <p>改造前：两种报销各写一遍完整流程，顺序靠人自觉，
 * 结果招待报销把打款写在复核前面，还漏了到账通知。
 *
 * <p>改造后：父类用 final 锁死五步顺序，子类只填有差异的步骤。
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
        System.out.println("========== 改造前：每种报销各写一遍流程 ==========");

        System.out.println("  差旅报销：");
        new OldTravelReimbursement().process("TRIP-01", 3800);

        System.out.println("  招待报销：");
        new OldEntertainmentReimbursement().process("ENT-02", 1200);

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 五步流程在两个类里各写一遍，改一步要改两处；");
        System.out.println("  2. 招待报销把「打款」写在「财务复核」前面，顺序错了；");
        System.out.println("  3. 招待报销还漏了到账通知，没有任何编译错误。");
    }

    static class OldTravelReimbursement {

        void process(String id, int amount) {
            System.out.println("    提交单据 " + id + "，金额 " + amount + " 元");
            System.out.println("    主管审批通过");
            System.out.println("    财务复核发票");
            System.out.println("    打款到工资卡");
            System.out.println("    短信通知已到账");
        }
    }

    static class OldEntertainmentReimbursement {

        void process(String id, int amount) {
            System.out.println("    提交单据 " + id + "，金额 " + amount + " 元");
            System.out.println("    部门经理审批通过");
            System.out.println("    打款到工资卡");          // 顺序反了
            System.out.println("    财务复核招待标准");    // 复核在打款之后
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：父类锁死流程，子类只填步骤 ==========");

        System.out.println("  差旅报销：");
        new TravelReimbursement().process("TRIP-01", 3800);

        System.out.println("  招待报销：");
        new EntertainmentReimbursement().process("ENT-02", 1200);

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 用继承实现，子类被父类的流程绑死；");
        System.out.println("  2. 流程真的需要不同顺序时，别硬套模板方法；");
        System.out.println("  3. 钩子方法一多，父类会变成半个业务类，要克制。");
    }

    /** 抽象父类：流程是 final 的，子类只能填步骤。 */
    abstract static class Reimbursement {

        final void process(String id, int amount) {
            submit(id, amount);
            approve(id, amount);
            review(id, amount);
            pay(id, amount);
            notifyDone(id);
        }

        protected void submit(String id, int amount) {
            System.out.println("    提交单据 " + id + "，金额 " + amount + " 元");
        }

        protected abstract void approve(String id, int amount);

        protected abstract void review(String id, int amount);

        protected abstract void pay(String id, int amount);

        protected void notifyDone(String id) {
            System.out.println("    短信通知已到账");
        }
    }

    static class TravelReimbursement extends Reimbursement {

        @Override
        protected void approve(String id, int amount) {
            System.out.println("    主管审批通过");
        }

        @Override
        protected void review(String id, int amount) {
            System.out.println("    财务复核发票");
        }

        @Override
        protected void pay(String id, int amount) {
            System.out.println("    打款到工资卡");
        }
    }

    static class EntertainmentReimbursement extends Reimbursement {

        @Override
        protected void approve(String id, int amount) {
            System.out.println("    部门经理审批通过");
        }

        @Override
        protected void review(String id, int amount) {
            System.out.println("    财务复核招待标准");
        }

        @Override
        protected void pay(String id, int amount) {
            System.out.println("    打款到工资卡");
        }
    }
}
