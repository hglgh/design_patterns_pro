import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 练习 03 参考实现：消息通知系统。
 *
 * <p>运行方式（单文件源模式，不需要先编译）：</p>
 * <pre>java docs/practice/solutions/NotificationSolution.java</pre>
 *
 * <p>这个文件里放了<b>三套</b>实现，按顺序是：</p>
 * <ol>
 *   <li>{@link HardcodedNotifier} —— 改造前：所有渠道写在一个方法里；</li>
 *   <li>{@link NotificationCenter} —— 反例：用「中转站」硬编码路由，
 *       看起来像中介者，实际上把变化点全塞回一个类；</li>
 *   <li>{@link EventBus} + {@link Channel} —— 推荐：事件一对多广播，
 *       渠道自己决定订阅哪些事件。</li>
 * </ol>
 *
 * <p>为什么第三个才是正解：通知的本质是<b>一个事件、多个接收者、彼此独立</b>。
 * 责任链是「一个请求、多个候选、最终只有一个处理」，方向是反的；
 * 中介者适合「多个对象互相通信，拓扑复杂」，而通知的拓扑是星型的，
 * 引入中转站只会把订阅关系硬编码进中转站。</p>
 */
public final class NotificationSolution {

    // ==================== 一、事件与消息 ====================

    /** 业务事件类型。新增一种事件 = 新增一个枚举值，其余代码不用动。 */
    enum EventType {
        ORDER_CREATED("订单创建"),
        ORDER_PAID("支付成功"),
        ORDER_SHIPPED("订单发货"),
        ORDER_REFUNDED("退款完成");

        private final String label;

        EventType(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * 通知内容：不可变。
     *
     * <p>为什么把「标题 + 正文」拆开而不是传一个 String：
     * 短信只认正文、邮件要标题、站内信要跳转链接，硬拼一个字符串
     * 到最后每个渠道都要做字符串切割。</p>
     */
    record Notice(EventType type, String title, String body, String userPhone) {
        Notice {
            if (type == null || title == null || body == null) {
                throw new IllegalArgumentException("事件类型、标题、正文不能为空");
            }
        }
    }

    // ==================== 二、改造前：一个方法装下所有渠道 ====================

    /**
     * 改造前写法。
     *
     * <p>问题清单（先自己数一遍，再看下面）：</p>
     * <ol>
     *   <li>加一个渠道要改 {@link #send} 的方法体 —— 每加一次都要回归全部事件；</li>
     *   <li>「哪些渠道要发这个事件」写死在 if 里，运营改一次订阅关系就要发版；</li>
     *   <li>短信失败要不要影响邮件？try-catch 只能靠人肉排列组合；</li>
     *   <li>没法单测：测短信必须连带把邮件、站内信一起跑。</li>
     * </ol>
     */
    static final class HardcodedNotifier {

        void send(Notice notice) {
            // 渠道 1：短信
            if (notice.type() == EventType.ORDER_PAID || notice.type() == EventType.ORDER_SHIPPED) {
                System.out.println("    [短信] " + notice.userPhone() + " 收到：" + notice.body());
            }

            // 渠道 2：邮件
            if (notice.type() != EventType.ORDER_PAID) {
                System.out.println("    [邮件] " + notice.title() + " / " + notice.body());
            }

            // 渠道 3：站内信
            System.out.println("    [站内信] " + notice.title());

            // 渠道 4：APP 推送（营销类夜间不发）
            if (notice.type() != EventType.ORDER_CREATED) {
                System.out.println("    [推送] " + notice.title());
            }
        }
    }

    // ==================== 三、反例：像中介者的中转站 ====================

    /**
     * 反例：把所有订阅关系集中到一张表里。
     *
     * <p>它比 {@link HardcodedNotifier} 好一点 —— 至少订阅关系是数据不是 if。
     * 但它仍然是一个<b>知道所有渠道、所有事件</b>的上帝类：
     * 加渠道要改它，改订阅要改它，加一个「某渠道只看某类事件」的细则还是要改它。</p>
     *
     * <p>这类代码常被叫做「中介者模式」，其实是误用。真正的中介者是为了
     * <b>减少多对多通信的连线</b>；而通知本身就是一对多，没有需要消除的连线。</p>
     */
    static final class NotificationCenter {
        private static final Map<EventType, List<String>> ROUTES = new LinkedHashMap<>();

        static {
            ROUTES.put(EventType.ORDER_CREATED, List.of("email", "inbox"));
            ROUTES.put(EventType.ORDER_PAID, List.of("sms", "inbox", "push"));
            ROUTES.put(EventType.ORDER_SHIPPED, List.of("sms", "email", "inbox", "push"));
            ROUTES.put(EventType.ORDER_REFUNDED, List.of("sms", "email", "inbox"));
        }

        void send(Notice notice) {
            for (String channel : ROUTES.getOrDefault(notice.type(), List.of())) {
                switch (channel) {
                    case "sms" -> System.out.println("    [短信] " + notice.userPhone() + " 收到：" + notice.body());
                    case "email" -> System.out.println("    [邮件] " + notice.title() + " / " + notice.body());
                    case "inbox" -> System.out.println("    [站内信] " + notice.title());
                    case "push" -> System.out.println("    [推送] " + notice.title());
                    default -> throw new IllegalArgumentException("未知渠道：" + channel);
                }
            }
        }
    }

    // ==================== 四、推荐：事件总线 + 自注册订阅者 ====================

    /**
     * 订阅者：一个渠道关心的所有事情。
     *
     * <p>注意接口里<b>没有</b>「你订阅了哪些事件」这个方法。订阅关系是
     * 装配时建立的关系，不是渠道自身的能力 —— 让渠道自己声明订阅，
     * 就等于让渠道知道事件总线，反而多一层耦合。</p>
     */
    interface Channel {
        String name();

        void deliver(Notice notice);
    }

    /**
     * 事件总线：只做三件事 —— 登记、注销、广播。
     *
     * <p>它<b>不认识</b>任何具体渠道，只认识 {@link Channel} 接口。
     * 这是它和 {@link NotificationCenter} 的根本区别：
     * 后者知道所有渠道，前者一个也不知道。</p>
     */
    static final class EventBus {
        private final Map<EventType, List<Channel>> subscribers = new LinkedHashMap<>();

        void subscribe(EventType type, Channel channel) {
            subscribers.computeIfAbsent(type, k -> new ArrayList<>()).add(channel);
        }

        void unsubscribe(EventType type, Channel channel) {
            subscribers.getOrDefault(type, List.of()).remove(channel);
        }

        /** 广播：单个渠道失败不影响其他渠道。这是「强隔离」而不是「尽力而为」。 */
        List<String> publish(Notice notice) {
            List<String> failures = new ArrayList<>();
            for (Channel channel : subscribers.getOrDefault(notice.type(), List.of())) {
                try {
                    channel.deliver(notice);
                } catch (RuntimeException e) {
                    failures.add(channel.name() + "：" + e.getMessage());
                }
            }
            return failures;
        }

        int subscriberCount(EventType type) {
            return subscribers.getOrDefault(type, List.of()).size();
        }

        Set<String> channelNames(EventType type) {
            Set<String> names = new LinkedHashSet<>();
            for (Channel channel : subscribers.getOrDefault(type, List.of())) {
                names.add(channel.name());
            }
            return names;
        }

        Map<EventType, List<Channel>> snapshot() {
            Map<EventType, List<Channel>> copy = new LinkedHashMap<>();
            subscribers.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            return Collections.unmodifiableMap(copy);
        }
    }

    // ==================== 五、三个具体渠道 ====================

    static final class SmsChannel implements Channel {
        private int sent;

        public String name() {
            return "短信";
        }

        public void deliver(Notice notice) {
            sent++;
            System.out.println("    [短信] " + notice.userPhone() + " ← " + notice.body());
        }

        int sent() {
            return sent;
        }
    }

    record EmailChannel(String address) implements Channel {
        public String name() {
            return "邮件";
        }

        public void deliver(Notice notice) {
            System.out.println("    [邮件] " + address + " ← " + notice.title());
        }
    }

    /** 故意做成会失败的渠道，用来演示「一个渠道挂了不影响别人」。 */
    static final class FlakyPushChannel implements Channel {
        private int failAtCall;

        FlakyPushChannel(int failAtCall) {
            this.failAtCall = failAtCall;
        }

        public String name() {
            return "APP 推送";
        }

        public void deliver(Notice notice) {
            if (failAtCall-- == 0) {
                throw new IllegalStateException("推送网关 502");
            }
            System.out.println("    [推送] " + notice.title());
        }
    }

    // ==================== 六、策略：营销类的投递时间规则 ====================

    /**
     * 投递时间规则。
     *
     * <p>这一层刻意<b>不</b>放进 {@code Channel}：同一个渠道在不同业务下
     * 可能有不同的时间策略（短信：验证码随时发、营销只在 9-21 点）。
     * 把它做成独立的可替换规则，渠道本身就不用改。</p>
     */
    interface DeliveryWindow {
        boolean allows(int hour);

        String describe();

        static DeliveryWindow always() {
            return new DeliveryWindow() {
                public boolean allows(int hour) {
                    return true;
                }

                public String describe() {
                    return "随时可发";
                }
            };
        }

        static DeliveryWindow quietHours(int fromHour, int toHour) {
            return new DeliveryWindow() {
                public boolean allows(int hour) {
                    return hour >= fromHour && hour < toHour;
                }

                public String describe() {
                    return fromHour + ":00 - " + toHour + ":00";
                }
            };
        }
    }

    /** 给任意渠道套一层时间窗，渠道自己不知道有这回事。 */
    static final class TimeWindowChannel implements Channel {
        private final Channel delegate;
        private final DeliveryWindow window;
        private final int nowHour;
        private int suppressed;

        TimeWindowChannel(Channel delegate, DeliveryWindow window, int nowHour) {
            this.delegate = delegate;
            this.window = window;
            this.nowHour = nowHour;
        }

        public String name() {
            return delegate.name() + "（" + window.describe() + "）";
        }

        public void deliver(Notice notice) {
            if (!window.allows(nowHour)) {
                suppressed++;
                System.out.println("    [跳过] " + delegate.name() + " 不在投递时段 " + window.describe());
                return;
            }
            delegate.deliver(notice);
        }

        int suppressed() {
            return suppressed;
        }
    }

    // ==================== 七、跑一遍 ====================

    public static void main(String[] args) {
        Notice paid = new Notice(EventType.ORDER_PAID, "支付成功",
                "您购买的《设计模式》已支付 89.00 元", "138****0000");
        Notice created = new Notice(EventType.ORDER_CREATED, "订单创建",
                "订单已创建，请在 30 分钟内完成支付", "138****0000");

        System.out.println("=== 改造前：一个方法装下所有渠道 ===");
        HardcodedNotifier hardcoded = new HardcodedNotifier();
        System.out.println("ORDER_PAID 的实际通知对象：");
        hardcoded.send(paid);
        System.out.println("ORDER_CREATED 的实际通知对象：");
        hardcoded.send(created);
        System.out.println("问题：订阅关系藏在 if 里，加一个「钉钉」要改方法体，");
        System.out.println("      而方法体里已经有 4 个渠道 × 4 个事件的 16 种组合要想。");
        System.out.println();

        System.out.println("=== 反例：用中转站把订阅关系变成表 ===");
        new NotificationCenter().send(paid);
        System.out.println("好一点：订阅关系成了数据。");
        System.out.println("但还是上帝类：加渠道要改它、改订阅要改它、加细则还是要改它。");
        System.out.println();

        System.out.println("=== 推荐：事件总线 + 自注册订阅者 ===");
        EventBus bus = new EventBus();
        SmsChannel sms = new SmsChannel();

        // 装配：订阅关系只在这里出现一次，业务代码里再也看不到渠道名
        bus.subscribe(EventType.ORDER_PAID, sms);
        bus.subscribe(EventType.ORDER_SHIPPED, sms);
        bus.subscribe(EventType.ORDER_REFUNDED, sms);
        bus.subscribe(EventType.ORDER_PAID, new EmailChannel("buyer@example.com"));
        bus.subscribe(EventType.ORDER_SHIPPED, new EmailChannel("buyer@example.com"));
        bus.subscribe(EventType.ORDER_CREATED, new EmailChannel("buyer@example.com"));
        bus.subscribe(EventType.ORDER_PAID, new FlakyPushChannel(0));

        System.out.println("ORDER_PAID 订阅者：" + bus.channelNames(EventType.ORDER_PAID));
        List<String> failures = bus.publish(paid);
        System.out.println("失败渠道：" + (failures.isEmpty() ? "无" : failures));
        System.out.println("短信渠道实发条数：" + sms.sent() + "（推送挂了，短信照发）");
        System.out.println();

        System.out.println("=== 加一条「营销类夜间不发」的规则，只管 APP 推送 ===");
        EventBus marketingBus = new EventBus();
        TimeWindowChannel quietPush = new TimeWindowChannel(
                new FlakyPushChannel(Integer.MAX_VALUE), DeliveryWindow.quietHours(9, 21), 23);
        marketingBus.subscribe(EventType.ORDER_CREATED, quietPush);
        System.out.println("当前时间 23 点，规则 " + quietPush.name() + "：");
        marketingBus.publish(created);
        System.out.println("被拦截条数：" + quietPush.suppressed());
        System.out.println("注意：短信渠道完全没动，也不知道有宵禁这回事。");
        System.out.println();

        System.out.println("=== 运行时改订阅：用户退订营销推送 ===");
        EventBus live = new EventBus();
        Channel push = new FlakyPushChannel(Integer.MAX_VALUE);
        live.subscribe(EventType.ORDER_CREATED, push);
        live.subscribe(EventType.ORDER_PAID, push);
        System.out.println("退订前 ORDER_PAID 订阅者：" + live.channelNames(EventType.ORDER_PAID));
        live.unsubscribe(EventType.ORDER_PAID, push);
        System.out.println("退订后 ORDER_PAID 订阅者：" + live.channelNames(EventType.ORDER_PAID));
        System.out.println("ORDER_CREATED 仍有：" + live.channelNames(EventType.ORDER_CREATED));
        System.out.println("这就是「按事件类型退订」的能力 —— 一个 boolean 字段做不到，");
        System.out.println("因为同一个渠道对不同事件的去留可能不同。");
        System.out.println();

        System.out.println("=== 为什么这里不该用责任链 ===");
        System.out.println("责任链：一个请求进来，沿链找一个「能处理」的，处理完就停。");
        System.out.println("通知：一个事件发生，所有「关心它的」都要收到，谁也不许替别人决定。");
        System.out.println("如果硬套责任链，接口会变成 handle(notice) 返回 boolean，");
        System.out.println("然后每个渠道必须返回 true 才能继续 —— 这不是渠道该回答的问题。");
    }

    private NotificationSolution() {
    }
}
