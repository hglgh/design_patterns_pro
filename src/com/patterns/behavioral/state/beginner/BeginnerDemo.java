package com.patterns.behavioral.state.beginner;

/**
 * 状态入门演示：订单的支付、发货、取消。
 *
 * <p>改造前：状态是一个公开字符串，每个动作都写一遍 if；
 * 新增状态要改所有方法，客户端还能改出「已付款 」这种非法值。
 *
 * <p>改造后：每个状态一个类，行为跟着状态走，订单只负责转发。
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
        System.out.println("========== 改造前：状态是字符串，判断散落在每个方法里 ==========");

        OldOrder order = new OldOrder();
        System.out.println("  新订单，状态=" + order.state);
        order.pay();
        order.ship();
        order.cancel();

        System.out.println("  把状态改成「已付款 」（末尾多个空格），再 ship()：");
        order.state = "已付款 ";
        order.ship();
        System.out.println("  看起来是已付款，却发不了货；编译器不会报错。");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 每个动作都要重新写一遍状态判断；");
        System.out.println("  2. 新增「退款中」状态，pay/ship/cancel 三个方法都要改；");
        System.out.println("  3. 状态是公开字符串，任何地方都能改出非法值。");
    }

    static class OldOrder {

        String state = "待付款";

        boolean pay() {
            if (state.equals("待付款")) {
                state = "已付款";
                System.out.println("  pay() → 成功，状态=" + state);
                return true;
            }
            System.out.println("  pay() → 失败，状态=" + state);
            return false;
        }

        boolean ship() {
            if (state.equals("已付款")) {
                state = "已发货";
                System.out.println("  ship() → 成功，状态=" + state);
                return true;
            }
            System.out.println("  ship() → 失败，状态=「" + state + "」");
            return false;
        }

        boolean cancel() {
            if (state.equals("待付款") || state.equals("已付款")) {
                state = "已取消";
                System.out.println("  cancel() → 成功，状态=" + state);
                return true;
            }
            System.out.println("  cancel() → 失败，状态=" + state);
            return false;
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：行为跟着状态对象走 ==========");

        Order order = new Order();
        order.pay();
        order.ship();
        order.cancel();

        System.out.println("  —— 另一个订单：付款后取消 ——");
        Order another = new Order();
        another.pay();
        another.cancel();
        another.pay();

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每个状态一个类，状态一多类就多；");
        System.out.println("  2. 状态之间的转移散在各状态类里，整体流程不容易一眼看完；");
        System.out.println("  3. 只有两三个状态、流程基本线性时，if/switch 更短更直接。");
    }

    /** 状态接口：每个动作在具体状态里给出自己的答案。 */
    interface OrderState {

        boolean pay(Order order);

        boolean ship(Order order);

        boolean cancel(Order order);

        String name();
    }

    static class PendingState implements OrderState {

        @Override
        public boolean pay(Order order) {
            order.setState(new PaidState());
            return true;
        }

        @Override
        public boolean ship(Order order) {
            return false;
        }

        @Override
        public boolean cancel(Order order) {
            order.setState(new CancelledState());
            return true;
        }

        @Override
        public String name() {
            return "待付款";
        }
    }

    static class PaidState implements OrderState {

        @Override
        public boolean pay(Order order) {
            return false;
        }

        @Override
        public boolean ship(Order order) {
            order.setState(new ShippedState());
            return true;
        }

        @Override
        public boolean cancel(Order order) {
            order.setState(new CancelledState());
            return true;
        }

        @Override
        public String name() {
            return "已付款";
        }
    }

    static class ShippedState implements OrderState {

        @Override
        public boolean pay(Order order) {
            return false;
        }

        @Override
        public boolean ship(Order order) {
            return false;
        }

        @Override
        public boolean cancel(Order order) {
            return false;          // 已发货不能直接取消
        }

        @Override
        public String name() {
            return "已发货";
        }
    }

    static class CancelledState implements OrderState {

        @Override
        public boolean pay(Order order) {
            return false;
        }

        @Override
        public boolean ship(Order order) {
            return false;
        }

        @Override
        public boolean cancel(Order order) {
            return false;
        }

        @Override
        public String name() {
            return "已取消";
        }
    }

    /** Context：把动作转发给当前状态，自己不写任何 if。 */
    static class Order {

        private OrderState state = new PendingState();

        void setState(OrderState state) {
            this.state = state;
        }

        void pay() {
            boolean ok = state.pay(this);
            System.out.println("  pay() → " + (ok ? "成功" : "被拒绝") + "，状态=" + state.name());
        }

        void ship() {
            boolean ok = state.ship(this);
            System.out.println("  ship() → " + (ok ? "成功" : "被拒绝") + "，状态=" + state.name());
        }

        void cancel() {
            boolean ok = state.cancel(this);
            System.out.println("  cancel() → " + (ok ? "成功" : "被拒绝") + "，状态=" + state.name());
        }
    }
}
