package com.patterns.structural.bridge.beginner;

/**
 * 桥接入门演示：消息类型（文本/图片）× 发送渠道（短信/邮件/推送）。
 *
 * <p>改造前：用继承表达所有组合，2×2 需要 4 个类，加一个渠道要加 2 个类；
 * 改造后：把「渠道」抽成一个独立维度，消息持有渠道，组合在运行时拼。
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
        System.out.println("========== 改造前：继承表达所有组合 ==========");

        new TextSmsMessage().send("订单已发货");
        new TextEmailMessage().send("订单已发货");
        new ImageSmsMessage().send("https://img.shop.com/1.png");
        new ImageEmailMessage().send("https://img.shop.com/1.png");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 2 种消息 × 2 种渠道 = 4 个类；3 × 3 就是 9 个类；");
        System.out.println("  2. 新增「语音消息」要写 2 个类，新增「推送」又要写 3 个类；");
        System.out.println("  3. 渠道的发送逻辑在每个组合类里重复。");
    }

    static class TextSmsMessage {
        void send(String text) {
            System.out.println("  [短信] 文本：" + text);
        }
    }

    static class TextEmailMessage {
        void send(String text) {
            System.out.println("  [邮件] 文本：" + text);
        }
    }

    static class ImageSmsMessage {
        void send(String url) {
            System.out.println("  [短信] 图片：" + url);
        }
    }

    static class ImageEmailMessage {
        void send(String url) {
            System.out.println("  [邮件] 图片：" + url);
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：消息与渠道各自独立 ==========");

        Channel sms = new SmsChannel();
        Channel email = new EmailChannel();

        new TextMessage(sms, "订单已发货").send();
        new TextMessage(email, "订单已发货").send();
        new ImageMessage(sms, "https://img.shop.com/1.png").send();
        new ImageMessage(email, "https://img.shop.com/1.png").send();

        System.out.println("  —— 新增推送渠道：只加一个 Channel，消息类都不改 ——");
        Channel push = new PushChannel();
        new TextMessage(push, "订单已发货").send();
        new ImageMessage(push, "https://img.shop.com/1.png").send();

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 多了一层「渠道」抽象，简单场景读起来更绕；");
        System.out.println("  2. 只有一个变化维度时，用策略模式就够了，不必上桥接；");
        System.out.println("  3. 两个维度必须真的独立变化，否则只是把 if/else 换了个地方。");
    }

    /** 实现维度：怎么发。 */
    interface Channel {
        void deliver(String content);
    }

    static class SmsChannel implements Channel {
        @Override
        public void deliver(String content) {
            System.out.println("  [短信] " + content);
        }
    }

    static class EmailChannel implements Channel {
        @Override
        public void deliver(String content) {
            System.out.println("  [邮件] " + content);
        }
    }

    /** 抽象维度：发什么。它持有渠道，而不是继承某个渠道。 */
    abstract static class Message {

        protected final Channel channel;

        Message(Channel channel) {
            this.channel = channel;
        }

        abstract void send();
    }

    static class TextMessage extends Message {

        private final String text;

        TextMessage(Channel channel, String text) {
            super(channel);
            this.text = text;
        }

        @Override
        void send() {
            channel.deliver("文本：" + text);
        }
    }

    static class ImageMessage extends Message {

        private final String url;

        ImageMessage(Channel channel, String url) {
            super(channel);
            this.url = url;
        }

        @Override
        void send() {
            channel.deliver("图片：" + url);
        }
    }

    // ---- 新渠道：只新增一个类 ----

    static class PushChannel implements Channel {
        @Override
        public void deliver(String content) {
            System.out.println("  [推送] " + content);
        }
    }
}
