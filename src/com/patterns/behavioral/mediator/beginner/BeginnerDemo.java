package com.patterns.behavioral.mediator.beginner;

import java.util.ArrayList;
import java.util.List;

/**
 * 中介者入门演示：客户、客服、财务、仓库之间的售后协作。
 *
 * <p>改造前：每个同事都持有其他同事的引用，彼此直接调用，依赖是 N×N；
 * 改造后：同事只认识一个 Hub，由 Hub 负责把消息路由给相关角色。
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
        System.out.println("========== 改造前：同事之间互相持有 ==========");

        OldWarehouse warehouse = new OldWarehouse();
        OldFinance finance = new OldFinance();
        OldAgent agent = new OldAgent(finance, warehouse);
        OldCustomer customer = new OldCustomer(agent, finance, warehouse);

        customer.askRefund("ORDER-1001");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. Customer 持有 3 个同事，Agent 又持有 2 个，依赖是网状；");
        System.out.println("  2. 新增「主管」角色，Customer/Agent/Finance 都可能要改；");
        System.out.println("  3. 协作流程散落在每个同事里，想调整顺序要改多处。");
    }

    static class OldWarehouse {
        void checkStock(String orderNo) {
            System.out.println("    [仓库] 检查 " + orderNo + " 的退货入库状态");
        }
    }

    static class OldFinance {
        void checkRefund(String orderNo) {
            System.out.println("    [财务] 核对 " + orderNo + " 的退款金额");
        }
    }

    static class OldAgent {

        private final OldFinance finance;
        private final OldWarehouse warehouse;

        OldAgent(OldFinance finance, OldWarehouse warehouse) {
            this.finance = finance;
            this.warehouse = warehouse;
        }

        void handle(String orderNo) {
            System.out.println("    [客服] 受理 " + orderNo + " 的退款申请");
            finance.checkRefund(orderNo);
            warehouse.checkStock(orderNo);
        }
    }

    static class OldCustomer {

        private final OldAgent agent;
        private final OldFinance finance;
        private final OldWarehouse warehouse;

        OldCustomer(OldAgent agent, OldFinance finance, OldWarehouse warehouse) {
            this.agent = agent;
            this.finance = finance;
            this.warehouse = warehouse;
        }

        void askRefund(String orderNo) {
            System.out.println("  [客户] 申请退款：" + orderNo);
            agent.handle(orderNo);
            finance.checkRefund(orderNo);      // 又直接找财务
            warehouse.checkStock(orderNo);     // 又直接找仓库
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：同事只认识 Hub ==========");

        AfterSalesHub hub = new AfterSalesHub();
        Customer customer = new Customer("客户小王", hub);
        Agent agent = new Agent("客服小李", hub);
        Finance finance = new Finance("财务小张", hub);
        Warehouse warehouse = new Warehouse("仓库小赵", hub);

        hub.register(customer);
        hub.register(agent);
        hub.register(finance);
        hub.register(warehouse);

        customer.askRefund("ORDER-1001");

        System.out.println();
        System.out.println("  —— 新增主管角色：实现 Participant + 注册，其他同事不改 ——");
        Supervisor supervisor = new Supervisor("主管老周", hub);
        hub.register(supervisor);
        customer.escalate("ORDER-1001");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. Hub 会越来越知道所有角色和路由规则，容易变成上帝类；");
        System.out.println("  2. Hub 是单点，它出问题所有协作都受影响；");
        System.out.println("  3. 只有两三个对象、调用关系简单时，直接调用更清楚。");
    }

    /** 同事：只持有 Hub，不再持有其他同事。 */
    abstract static class Participant {

        protected final String name;
        protected final Hub hub;

        Participant(String name, Hub hub) {
            this.name = name;
            this.hub = hub;
        }

        abstract String role();

        void receive(String from, String topic, String message) {
            System.out.println("    [" + name + "] 收到 " + from + " 的 " + topic + "：" + message);
        }
    }

    static class Customer extends Participant {

        Customer(String name, Hub hub) {
            super(name, hub);
        }

        @Override
        String role() {
            return "CUSTOMER";
        }

        void askRefund(String orderNo) {
            System.out.println("  [客户] 申请退款：" + orderNo);
            hub.send(this, "refund", orderNo);
        }

        void escalate(String orderNo) {
            System.out.println("  [客户] 升级投诉：" + orderNo);
            hub.send(this, "escalate", orderNo);
        }
    }

    static class Agent extends Participant {

        Agent(String name, Hub hub) {
            super(name, hub);
        }

        @Override
        String role() {
            return "AGENT";
        }
    }

    static class Finance extends Participant {

        Finance(String name, Hub hub) {
            super(name, hub);
        }

        @Override
        String role() {
            return "FINANCE";
        }
    }

    static class Warehouse extends Participant {

        Warehouse(String name, Hub hub) {
            super(name, hub);
        }

        @Override
        String role() {
            return "WAREHOUSE";
        }
    }

    // ---- 新角色：只新增一个类 ----

    static class Supervisor extends Participant {

        Supervisor(String name, Hub hub) {
            super(name, hub);
        }

        @Override
        String role() {
            return "SUPERVISOR";
        }
    }

    interface Hub {
        void register(Participant participant);

        void send(Participant from, String topic, String message);
    }

    /** 中介者：知道有哪些同事、哪个主题该通知谁。 */
    static class AfterSalesHub implements Hub {

        private final List<Participant> participants = new ArrayList<>();

        @Override
        public void register(Participant participant) {
            participants.add(participant);
        }

        @Override
        public void send(Participant from, String topic, String message) {
            List<String> targets = switch (topic) {
                case "refund" -> List.of("AGENT", "FINANCE");
                case "escalate" -> List.of("SUPERVISOR", "AGENT");
                default -> List.of();
            };

            for (Participant participant : participants) {
                if (participant != from && targets.contains(participant.role())) {
                    participant.receive(from.name, topic, message);
                }
            }
        }
    }
}
