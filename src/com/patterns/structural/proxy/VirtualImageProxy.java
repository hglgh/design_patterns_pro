package com.patterns.structural.proxy;

/**
 * 虚拟代理（Virtual Proxy）= 延迟加载代理。控制"真实对象<b>何时</b>被创建"。
 *
 * <p>这就是原项目的 {@code ProxyImage}，逻辑完全保留，只做了两处改进：
 * <ul>
 *   <li>类名从 {@code ProxyImage} 改为 {@code VirtualImageProxy}。
 *       原名字只说了"它是代理"，没说"它是哪种代理"；
 *       本项目要演示 4 种代理，命名必须体现类型，否则学习者分不清区别。</li>
 *   <li>补上了 {@code getFilename()} 的实现。原项目的 Image 接口只有 display()，
 *       导致缓存代理无法拿到 key、日志代理无法打印文件名，只能强转成 RealImage。
 *       把 getFilename() 提到接口里，代理才能在不接触真实对象的前提下完成自己的工作。</li>
 * </ul>
 *
 * <p><b>为什么需要延迟加载？</b>
 * 想象一个相册页面要展示 100 张缩略图对应的原图入口。
 * 如果一进页面就 {@code new RealImage()} 加载全部 100 张原图（每张几 MB），
 * 内存瞬间爆掉，而用户可能只点开了其中 2 张。
 * 虚拟代理让"创建 100 个代理对象"的成本极低（只存一个文件名字符串），
 * 真正的加载推迟到 {@code display()} 被调用的那一刻。
 *
 * <p><b>典型应用</b>：Hibernate / JPA 的懒加载实体（{@code ManyToOne(fetch = LAZY)}）、
 * MyBatis 的延迟加载、Spring 的 {@code @Lazy} 注入、网页图片的 lazy-load。
 *
 * @author design-patterns-pro
 */
public class VirtualImageProxy implements Image {

    /**
     * 真实对象的引用。<b>初始为 null</b>，第一次 display() 时才创建。
     * 注意它不是 final——这正是"延迟"的实现方式。
     */
    private RealImage realImage;

    /** 只保存文件名，创建代理的成本极低。 */
    private final String filename;

    /**
     * 构造代理时<b>不会</b>触发图片加载。
     *
     * @param filename 文件名
     */
    public VirtualImageProxy(String filename) {
        this.filename = filename;
        System.out.println("      [VirtualProxy] 创建代理（未加载图片）：" + filename);
    }

    @Override
    public void display() {
        // 关键：只有真正要用时才创建真实对象
        if (realImage == null) {
            System.out.println("      [VirtualProxy] 首次访问，现在才创建真实对象");
            realImage = new RealImage(filename);
        } else {
            System.out.println("      [VirtualProxy] 真实对象已存在，直接复用");
        }
        realImage.display();
    }

    /**
     * <b>注意这个方法不会触发加载</b>——文件名在代理自己身上就有。
     *
     * <p>这是虚拟代理的一个重要性质：<b>不需要真实对象就能回答的问题，
     * 代理自己回答</b>。如果这里写成 {@code return getReal().getFilename();}，
     * 那么仅仅为了拿个文件名就把整张图片加载进内存，延迟加载就白做了。
     */
    @Override
    public String getFilename() {
        return filename;
    }

    /**
     * @return 真实对象是否已被创建，用于验证延迟加载是否生效
     */
    public boolean isLoaded() {
        return realImage != null;
    }
}
