package com.patterns.behavioral.chainofresponsibility.beginner;

/**
 * 责任链入门演示：差旅报销按金额分级审批。
 *
 * <p>改造前：一个方法里用 if/else 写死所有审批级别，加一级就要改方法；
 * 改造后：每个审批人是一个节点，持有下一个节点，请求沿链传递。
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
        System.out.println("========== 改造前：一个方法里的 if/else ==========");

        ExpenseService service = new ExpenseService();
        service.approve("张三", 800);
        service.approve("李四", 4000);
        service.approve("王五", 15000);
        service.approve("赵六", 50000);

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 所有级别、金额上限、审批顺序都写死在一个方法里；");
        System.out.println("  2. 加一个副总（<= 50000）必须改这个方法，还要重新回归所有金额；");
        System.out.println("  3. 想把「总监」提到「经理」前面，只能改 if 的顺序；");
        System.out.println("  4. 审批规则和业务方法绑死，无法单独复用某一个审批人。");
    }

    static class ExpenseService {

        void approve(String applicant, int amount) {
            if (amount <= 1000) {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 组长审批");
            } else if (amount <= 5000) {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 经理审批");
            } else if (amount <= 20000) {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 总监审批");
            } else {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 驳回：金额超出权限");
            }
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：请求沿审批链传递 ==========");

        Approver teamLead = new TeamLead();
        Approver manager = new Manager();
        Approver director = new Director();

        // 接线：组长 → 经理 → 总监
        teamLead.setNext(manager).setNext(director);

        teamLead.approve("张三", 800);
        teamLead.approve("李四", 4000);
        teamLead.approve("王五", 15000);
        teamLead.approve("赵六", 50000);

        System.out.println();
        System.out.println("  —— 新增副总（<= 50000）：只加一个类，再重新接线 ——");
        Approver vicePresident = new VicePresident();
        director.setNext(vicePresident);

        teamLead.approve("孙七", 30000);

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 请求可能走到链尾都没人处理，必须显式设计兜底；");
        System.out.println("  2. 链太长时，一个请求要经过多个节点，调试要跟整条链；");
        System.out.println("  3. 只有一两个固定规则时，if/else 更直接。");
    }

    /** 处理器：持有下一个处理器，自己不处理就往后传。 */
    abstract static class Approver {

        private Approver next;

        Approver setNext(Approver next) {
            this.next = next;
            return next;             // 方便链式接线
        }

        abstract void approve(String applicant, int amount);

        /** 统一的后传逻辑，避免每个子类都写一遍 null 判断。 */
        protected void forward(String applicant, int amount) {
            if (next != null) {
                next.approve(applicant, amount);
            } else {
                System.out.println("  " + applicant + " 报销 " + amount
                        + " 元 → 无人可批，转总经理办公室");
            }
        }
    }

    static class TeamLead extends Approver {
        @Override
        void approve(String applicant, int amount) {
            if (amount <= 1000) {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 组长审批");
            } else {
                forward(applicant, amount);
            }
        }
    }

    static class Manager extends Approver {
        @Override
        void approve(String applicant, int amount) {
            if (amount <= 5000) {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 经理审批");
            } else {
                forward(applicant, amount);
            }
        }
    }

    static class Director extends Approver {
        @Override
        void approve(String applicant, int amount) {
            if (amount <= 20000) {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 总监审批");
            } else {
                forward(applicant, amount);
            }
        }
    }

    // ---- 新审批级别：只新增一个类 ----

    static class VicePresident extends Approver {
        @Override
        void approve(String applicant, int amount) {
            if (amount <= 50000) {
                System.out.println("  " + applicant + " 报销 " + amount + " 元 → 副总审批");
            } else {
                forward(applicant, amount);
            }
        }
    }
}
