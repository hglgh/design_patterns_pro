package com.patterns.behavioral.observer;

/**
 * 观察者模式完整演示：五段 Demo，逐条对着原项目的五个问题。
 *
 * <table border="1">
 *   <caption>五段 Demo 各自证明什么</caption>
 *   <tr><th>Demo</th><th>证明的事</th><th>原项目对应的问题</th></tr>
 *   <tr><td>1</td><td>一次发布，三个渠道各自处理</td>
 *       <td>只有一个 {@code update} 打印，渠道是写死的</td></tr>
 *   <tr><td>2</td><td>身份是 ID，同名不冲突，重复订阅幂等</td>
 *       <td>{@code record equals} 当身份，两个张三互相干扰</td></tr>
 *   <tr><td>3</td><td>通知途中退订不抛 {@code ConcurrentModificationException}</td>
 *       <td>边遍历边删，轻则异常重则静默漏发</td></tr>
 *   <tr><td>4</td><td>一个观察者失败，其余照收，失败进报告</td>
 *       <td>一个 {@code update} 抛异常，全链中断</td></tr>
 *   <tr><td>5</td><td>同一个事件，不同渠道不同处理</td>
 *       <td>事件只有一个 {@code String}，类型信息丢失</td></tr>
 * </table>
 *
 * <p><b>运行方式</b>（JDK 25，项目根目录 {@code design_patterns_pro} 下）：
 *
 * <pre>
 *   javac -encoding UTF-8 -Xlint:all -d out ^
 *         src\com\patterns\behavioral\observer\*.java
 *   java -Dstdout.encoding=UTF-8 -cp out com.patterns.behavioral.observer.Client
 * </pre>
 *
 * <p>全部输出<b>不含时间戳、不含哈希码、不依赖集合的哈希顺序</b>，
 * 因此同一份代码反复运行，逐字节完全一致。
 *
 * @author design-patterns-pro
 */
public final class Client {

    /**
     * 工具类，禁止实例化。
     */
    private Client() {
    }

    /**
     * 程序入口：依次跑五段 Demo。
     *
     * @param args 未使用
     */
    public static void main(String[] args) {
        demo1ThreeChannels();
        demo2IdentityIsId();
        demo3UnsubscribeDuringPublish();
        demo4FailureIsolation();
        demo5EventTypeDrivesChannel();

        System.out.println("五段 Demo 对应原项目的五个问题：");
        System.out.println("  1. 三个渠道各自独立     —— 原项目只有一个 update 打印");
        System.out.println("  2. ID 去重、同名不冲突  —— 原项目用 record equals 当身份");
        System.out.println("  3. 快照遍历不抛 CME     —— 原项目边遍历边删必炸");
        System.out.println("  4. 失败隔离 + 投递报告  —— 原项目一个异常全链中断");
        System.out.println("  5. 事件类型驱动渠道差异 —— 原项目只有一个 String");
    }

    /**
     * Demo 1：一次发布，三个渠道。
     *
     * <p>三个观察者实现同一个 {@link Observer} 接口，
     * {@link ContentCreator} 完全不知道它们是站内信、邮件还是推送——
     * 它只会在循环里调 {@code onEvent}。这就是"对扩展开放"：
     * 将来加一个"短信观察者"，改的只有 Client 里的注册那一行。
     */
    private static void demo1ThreeChannels() {
        System.out.println("[Demo 1] 一次发布，三个渠道");
        ContentCreator creator = new ContentCreator("author-hgl", "hgl");

        creator.subscribe(new InAppObserver("fan-1001", "张三"));
        creator.subscribe(new EmailObserver("fan-1002", "李四", "lisi@example.com"));
        creator.subscribe(new PushObserver("fan-1003", "王五", "device-w5"));

        System.out.println("  订阅者（注册顺序）：" + creator.subscriberNames());
        DispatchReport report = creator.publishArticle(
                "设计模式到底在解决什么",
                "从「加需求时要不要改旧代码」说起");
        System.out.println(report.describeAsText());
        System.out.println();
    }

    /**
     * Demo 2：身份是 ID，不是名字。
     *
     * <p>四次订阅里只有两次生效：
     * <ul>
     *   <li>{@code fan-2001} 订阅两次——第二次被 {@code putIfAbsent} 吃掉；</li>
     *   <li>另一个实例顶着同一个 {@code fan-2001}、名字叫「张三（新设备）」——
     *       也没能顶掉原来的实例；</li>
     *   <li>{@code fan-2002} 也叫「张三」，但它确实是另一个人，正常入列。</li>
     * </ul>
     *
     * <p>最后两行用 {@code observerId} 区分两个同名的人——
     * <b>展示名可能重复，ID 不会</b>。如果 ID 也会重复，
     * 那不是观察者模式的问题，是业务主键设计的问题。
     */
    private static void demo2IdentityIsId() {
        System.out.println("[Demo 2] 身份是 ID，不是名字");
        ContentCreator creator = new ContentCreator("author-hgl", "hgl");

        InAppObserver zhangSanA = new InAppObserver("fan-2001", "张三");
        InAppObserver zhangSanB = new InAppObserver("fan-2002", "张三");

        creator.subscribe(zhangSanA);
        creator.subscribe(zhangSanB);
        creator.subscribe(zhangSanA);
        creator.subscribe(new InAppObserver("fan-2001", "张三（新设备）"));

        System.out.println("  订阅 4 次（含 2 次重复），实际名单：" + creator.subscriberNames());
        System.out.println("  subscriberCount() = " + creator.subscriberCount());
        System.out.println("  「张三（新设备）」没能顶掉原来的 fan-2001");

        DispatchReport report = creator.publishArticle("名字与身份", "两个张三，一个都不多");
        System.out.println(report.describeAsText());
        System.out.println("  报告里两行都叫「张三」，靠 observerId 才分得清：");
        for (Delivery delivery : report.deliveries()) {
            System.out.println("    " + delivery.observerId() + " → " + delivery.observerName());
        }
        System.out.println("  两行不是重复投递，是两个不同 ID 的粉丝恰好同名");
        System.out.println();
    }

    /**
     * Demo 3：通知途中退订。
     *
     * <p>第二个观察者在 {@code onEvent} 里调用 {@code unsubscribe} 把自己删掉。
     * 因为 {@code publish} 遍历的是<b>快照</b>，所以：
     * <ul>
     *   <li>不会抛 {@code ConcurrentModificationException}；</li>
     *   <li>第三个观察者不会漏发；</li>
     *   <li>本轮的名单在开始时就固定了——他这一轮仍然收到通知，
     *       从下一轮起才不在名单里。</li>
     * </ul>
     *
     * <p>这个场景在原项目里是最危险的：删倒数第二个元素时，
     * {@code ArrayList} 的迭代器 {@code hasNext()} 会直接返回 {@code false}，
     * <b>不报任何错</b>，最后一个粉丝静默漏发。
     */
    private static void demo3UnsubscribeDuringPublish() {
        System.out.println("[Demo 3] 通知途中退订：不抛 ConcurrentModificationException");
        ContentCreator creator = new ContentCreator("author-hgl", "hgl");

        creator.subscribe(new InAppObserver("fan-3001", "正常粉丝 1"));
        creator.subscribe(new SelfUnsubscribingObserver("fan-3002", "临时粉丝", creator));
        creator.subscribe(new InAppObserver("fan-3003", "正常粉丝 2"));

        System.out.println("  发布前名单：" + creator.subscriberNames());
        DispatchReport report = creator.publishArticle("快照遍历的意义", "边遍历边改会出事");
        System.out.println(report.describeAsText());

        System.out.println("  发布后名单：" + creator.subscriberNames());
        System.out.println("  本轮三个人都收到了——名单在开始通知那一刻就固定了");
        System.out.println();

        System.out.println("  再发一篇，临时粉丝已经不在名单里：");
        DispatchReport second = creator.publishArticle("第二篇", "这次只有两个人");
        System.out.println("  attempted() = " + second.attempted() + "，名单里只剩两个人");
        System.out.println();
    }

    /**
     * Demo 4：一个观察者坏了，其他人照收。
     *
     * <p>李四的推送通道是关的，{@code onEvent} 会抛
     * {@link IllegalStateException}。{@code publish} 捕获它、
     * 记进报告、继续发给王五。
     *
     * <p><b>注意 {@code publish} 没有抛异常</b>——"内容发布"成功了。
     * 通知没送到是另一件事，它体现在报告里。
     * 如果这里抛异常，调用方会以为"文章没发出去"，可实际上已经发出去了。
     */
    private static void demo4FailureIsolation() {
        System.out.println("[Demo 4] 一个观察者坏了，其他人照收");
        ContentCreator creator = new ContentCreator("author-hgl", "hgl");

        creator.subscribe(new InAppObserver("fan-4001", "张三"));
        creator.subscribe(PushObserver.closed("fan-4002", "李四", "device-l4"));
        creator.subscribe(new InAppObserver("fan-4003", "王五"));

        DispatchReport report = creator.publishArticle("失败隔离", "一坏不拖累其余");
        System.out.println(report.describeAsText());
        System.out.println("  publish 正常返回，说明「发布」本身成功了");
        System.out.println("  allSucceeded() = " + report.allSucceeded()
                + "，失败明细可以在 deliveries() 里逐条查");
        System.out.println();
    }

    /**
     * Demo 5：同一个事件，不同渠道不同处理。
     *
     * <p>文章和视频三个渠道都收；直播只有站内信和推送收，
     * 邮件渠道主动跳过（直播错过就没了，邮件发出去也是废纸）。
     *
     * <p>跳过的那一次在报告里仍然记为<b>成功</b>——
     * "这个渠道不需要这条事件"不是失败，
     * 失败指的是"想发但没发出去"。
     */
    private static void demo5EventTypeDrivesChannel() {
        System.out.println("[Demo 5] 同一个事件，三个渠道三种处理");
        ContentCreator creator = new ContentCreator("author-hgl", "hgl");

        creator.subscribe(new InAppObserver("fan-5001", "张三"));
        creator.subscribe(new EmailObserver("fan-5002", "李四", "lisi@example.com"));
        creator.subscribe(new PushObserver("fan-5003", "王五", "device-w5"));

        System.out.println("  ── 发文章 ──");
        creator.publishArticle("设计模式速查", "三个渠道都会收到");
        System.out.println();

        System.out.println("  ── 发视频 ──");
        creator.publishVideo("十分钟看懂观察者", "三个渠道都会收到");
        System.out.println();

        System.out.println("  ── 开直播 ──");
        DispatchReport live = creator.publishLive("今晚八点聊聊观察者模式");
        System.out.println(live.describeAsText());
        System.out.println("  邮件渠道静默跳过了直播，报告里仍记为「成功」");
        System.out.println("  「这个渠道不需要这条事件」不是失败");
        System.out.println();
    }

    /**
     * 收到通知就取消关注的观察者，专供 Demo 3 使用。
     *
     * <p><b>它持有 {@link Subject} 引用，会不会又走回原项目的老路？</b>
     * 不会。区别在于<b>这个能力放在哪一层</b>：
     * <ul>
     *   <li>原项目把 {@code follow/unfollow} 写进了
     *       {@code Followable} 接口，于是<b>每一个观察者都被迫具备</b>
     *       操作别人的能力，还得 {@code instanceof} 强转；</li>
     *   <li>这里的 {@link Observer} 接口只有 {@code onEvent}，
     *       <b>没有任何一个观察者被要求会退订</b>。这个类只是 Demo 里
     *       一个具体的观察者，它拿着的引用是用来模拟"用户在通知里点了取消"——
     *       真实系统里这个引用通常来自会话或上下文，而不是接口的一部分。</li>
     * </ul>
     *
     * <p>判断标准很简单：<b>能力是"每个实现都必须有"，还是"这一个实现恰好要用"？</b>
     * 前者进接口，后者进字段。
     */
    private static final class SelfUnsubscribingObserver implements Observer {

        private final String id;
        private final String displayName;
        private final Subject subject;

        /**
         * 创建一个会在收到通知后自我退订的观察者。
         *
         * @param id          观察者 ID
         * @param displayName 展示名
         * @param subject     要退订的被观察者
         */
        SelfUnsubscribingObserver(String id, String displayName, Subject subject) {
            this.id = id;
            this.displayName = displayName;
            this.subject = subject;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        /**
         * 收到通知，然后把自己从名单里删掉。
         *
         * @param event 事件
         */
        @Override
        public void onEvent(Event event) {
            System.out.println("  [站内信] " + displayName + " ← " + event.describe()
                    + "｜（收到后立刻退订）");
            subject.unsubscribe(id);
        }
    }
}
