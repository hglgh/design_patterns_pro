package com.patterns.behavioral.observer.beginner;

import java.util.ArrayList;
import java.util.List;

/**
 * 观察者入门演示：内容创作者发布动态，多个渠道收到通知。
 *
 * <p>改造前：Creator 认识每一个渠道，渠道顺序写死，一个渠道抛异常后面全收不到。
 * 改造后：渠道自己订阅，Creator 只维护一份名单，发布时按名单通知。
 *
 * <p>注意：失败隔离需要在通知循环里加 try/catch，这是发布方的策略，
 * 不是观察者模式自动送的能力。
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
        System.out.println("========== 改造前：发布方法里写死三个渠道 ==========");

        OldCreator creator = new OldCreator(new OldPush(), new OldEmail(), new OldApp());
        creator.publish("设计模式入门");

        System.out.println("  —— 推送网关超时 ——");
        try {
            creator.publish("故障演练");
        } catch (IllegalStateException e) {
            System.out.println("  [系统] 发布失败：" + e.getMessage());
            System.out.println("  邮件和站内信都没有收到「故障演练」");
        }

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. Creator 认识每一个渠道，新增短信就要改构造器和 publish；");
        System.out.println("  2. 渠道顺序写死在代码里，想调整顺序也要改 publish；");
        System.out.println("  3. 一个渠道抛异常，后面的渠道全部收不到通知。");
    }

    static class OldEmail {
        void send(String title) {
            System.out.println("    [邮件] " + title);
        }
    }

    static class OldApp {
        void send(String title) {
            System.out.println("    [站内信] " + title);
        }
    }

    static class OldPush {
        void send(String title) {
            if (title.contains("故障")) {
                throw new IllegalStateException("推送网关超时");
            }
            System.out.println("    [推送] " + title);
        }
    }

    static class OldCreator {

        private final OldPush push;
        private final OldEmail email;
        private final OldApp app;

        OldCreator(OldPush push, OldEmail email, OldApp app) {
            this.push = push;
            this.email = email;
            this.app = app;
        }

        void publish(String title) {
            System.out.println("  [Creator] 发布：" + title);
            push.send(title);
            email.send(title);
            app.send(title);
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：渠道订阅 Creator，互不认识 ==========");

        Creator creator = new Creator();
        EmailSubscriber email = new EmailSubscriber();
        AppSubscriber app = new AppSubscriber();
        PushSubscriber push = new PushSubscriber();

        creator.subscribe(email);
        creator.subscribe(app);
        creator.subscribe(push);

        creator.publish("设计模式入门");

        System.out.println("  —— 退订推送 ——");
        creator.unsubscribe(push);
        creator.publish("第二期");

        System.out.println("  —— 新增短信渠道：只加一个订阅者 ——");
        creator.subscribe(new SmsSubscriber());
        creator.publish("第三期");

        System.out.println("  —— 一个渠道故障，其他渠道照常 ——");
        creator.subscribe(new BrokenPushSubscriber());
        creator.publish("故障演练");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 通知顺序不保证，渠道之间有先后依赖时不能靠观察者；");
        System.out.println("  2. 订阅者太多时，一次发布可能很慢，必要时改成异步；");
        System.out.println("  3. 订阅名单是隐式的，排查「谁收到了」要看运行期状态。");
    }

    /** 观察者接口：只关心「被通知」这一件事。 */
    interface Observer {
        void onPublish(String title);
    }

    static class EmailSubscriber implements Observer {

        @Override
        public void onPublish(String title) {
            System.out.println("    [邮件] " + title);
        }
    }

    static class AppSubscriber implements Observer {

        @Override
        public void onPublish(String title) {
            System.out.println("    [站内信] " + title);
        }
    }

    static class PushSubscriber implements Observer {

        @Override
        public void onPublish(String title) {
            System.out.println("    [推送] " + title);
        }
    }

    static class SmsSubscriber implements Observer {

        @Override
        public void onPublish(String title) {
            System.out.println("    [短信] " + title);
        }
    }

    static class BrokenPushSubscriber implements Observer {

        @Override
        public void onPublish(String title) {
            throw new IllegalStateException("推送网关超时");
        }
    }

    /** 被观察者：只维护名单，不知道每个订阅者具体是谁。 */
    static class Creator {

        private final List<Observer> observers = new ArrayList<>();

        void subscribe(Observer observer) {
            observers.add(observer);
        }

        void unsubscribe(Observer observer) {
            observers.remove(observer);
        }

        void publish(String title) {
            System.out.println("  [Creator] 发布：" + title);

            int failed = 0;
            // 先复制一份名单，避免通知过程中有人订阅/退订导致遍历出错
            List<Observer> snapshot = new ArrayList<>(observers);
            for (Observer observer : snapshot) {
                try {
                    observer.onPublish(title);
                } catch (RuntimeException e) {
                    failed++;
                    System.out.println("    [系统] 一个渠道失败：" + e.getMessage());
                }
            }

            if (failed > 0) {
                System.out.println("    通知结束：" + failed + " 个渠道失败，其他渠道不受影响");
            }
        }
    }
}
