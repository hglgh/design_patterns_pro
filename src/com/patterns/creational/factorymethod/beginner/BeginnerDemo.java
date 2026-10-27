package com.patterns.creational.factorymethod.beginner;

/**
 * 工厂方法入门演示：通知渠道的创建散落在两个业务类里。
 *
 * <p>改造前：每个调用点都写一遍 {@code if ("email") ... else if ("sms") ...}；
 * 改造后：由「工厂子类」决定创建哪种发送器，业务方法只依赖工厂和 Sender 接口。
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
        System.out.println("========== 改造前：每个调用点自己判断类型 ==========");

        NotificationService service = new NotificationService();
        service.notify("email", "u1@shop.com", "订单已发货");
        service.notify("sms", "138****0000", "订单已发货");

        BatchJob job = new BatchJob();
        job.sendDaily();

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 「怎么创建发送器」的判断在 NotificationService 和 BatchJob 里各写了一遍；");
        System.out.println("  2. 新增 push 渠道，两处都要改，漏一处就线上报错；");
        System.out.println("  3. 业务方法和具体发送器绑死，想单独测试「批量任务」也很麻烦。");
    }

    static class NotificationService {

        void notify(String type, String to, String message) {
            if ("email".equals(type)) {
                System.out.println("  [邮件] " + to + "：" + message);
            } else if ("sms".equals(type)) {
                System.out.println("  [短信] " + to + "：" + message);
            } else {
                throw new IllegalArgumentException("不支持的渠道：" + type);
            }
        }
    }

    static class BatchJob {

        void sendDaily() {
            // 同一个 if/else，又抄了一遍
            String type = "email";
            String to = "ops@shop.com";
            String message = "日报已生成";
            if ("email".equals(type)) {
                System.out.println("  [批量-邮件] " + to + "：" + message);
            } else if ("sms".equals(type)) {
                System.out.println("  [批量-短信] " + to + "：" + message);
            }
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：创建交给工厂子类 ==========");

        // 装配时决定用哪个工厂；业务方法只收 SenderFactory
        sendWelcome(new EmailSenderFactory(), "u1@shop.com");
        sendWelcome(new SmsSenderFactory(), "138****0000");

        System.out.println("  —— 新增 push 渠道：只加两个类，sendWelcome 不改 ——");
        sendWelcome(new PushSenderFactory(), "device-42");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每种渠道多一个 Factory 类；渠道很少时，一个简单工厂方法就够了；");
        System.out.println("  2. 「选哪个工厂」仍然要在启动/装配处决定，这行代码不会消失。");
    }

    /** 业务方法只认识两个抽象：SenderFactory 和 Sender。 */
    private static void sendWelcome(SenderFactory factory, String to) {
        Sender sender = factory.createSender();
        sender.send(to, "欢迎注册");
    }

    interface Sender {
        void send(String to, String message);
    }

    static class EmailSender implements Sender {
        @Override
        public void send(String to, String message) {
            System.out.println("  [邮件] " + to + "：" + message);
        }
    }

    static class SmsSender implements Sender {
        @Override
        public void send(String to, String message) {
            System.out.println("  [短信] " + to + "：" + message);
        }
    }

    /** 工厂方法的核心：创建者把「new 哪一个」推迟到子类。 */
    abstract static class SenderFactory {

        abstract Sender createSender();

        /** 创建者也可以带业务方法：先创建，再使用。 */
        void send(String to, String message) {
            createSender().send(to, message);
        }
    }

    static class EmailSenderFactory extends SenderFactory {
        @Override
        Sender createSender() {
            return new EmailSender();
        }
    }

    static class SmsSenderFactory extends SenderFactory {
        @Override
        Sender createSender() {
            return new SmsSender();
        }
    }

    // ---- 新渠道：只新增下面两个类，上面的业务方法一行没改 ----

    static class PushSender implements Sender {
        @Override
        public void send(String to, String message) {
            System.out.println("  [推送] " + to + "：" + message);
        }
    }

    static class PushSenderFactory extends SenderFactory {
        @Override
        Sender createSender() {
            return new PushSender();
        }
    }
}
