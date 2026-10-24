package com.patterns.structural.proxy;

/**
 * 客户端：对比 4 种代理的效果。
 *
 * <p>运行方式：
 * <pre>
 *   java -cp out com.patterns.structural.proxy.Client
 * </pre>
 *
 * <p>想查看 JVM 动态生成的代理类字节码，加上参数：
 * <pre>
 *   java -Djdk.proxy.ProxyGenerator.saveGeneratedFiles=true -cp out ...
 * </pre>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demo0WithoutProxy();
        demo1VirtualProxy();
        demo2ProtectedProxy();
        demo3CachingProxy();
        demo4ProxyChain();
        demo5DynamicProxy();
    }

    /**
     * 演示 0：不用代理的原始写法（保留原项目 Client 的对照基线）。
     */
    private static void demo0WithoutProxy() {
        System.out.println("=== 0. 不用代理：构造即加载，无法延迟 ===");
        RealImage.resetLoadCount();

        System.out.println("  -- 创建对象（还没打算显示，但已经被迫加载了）--");
        Image image = new RealImage("photo.png");

        System.out.println("  -- 假设业务判断后决定不显示这张图 --");
        System.out.println("      （图片已经白白加载进内存了，真实加载次数 = "
                + RealImage.getLoadCount() + "）");
        System.out.println();
    }

    /**
     * 演示 1：虚拟代理 = 延迟加载。这就是原项目 ProxyImage 的场景。
     */
    private static void demo1VirtualProxy() {
        System.out.println("=== 1. 虚拟代理：延迟加载 ===");
        RealImage.resetLoadCount();

        System.out.println("  -- 创建 3 个代理，一个都没加载图片 --");
        VirtualImageProxy img1 = new VirtualImageProxy("banner.jpg");
        VirtualImageProxy img2 = new VirtualImageProxy("avatar.png");
        VirtualImageProxy img3 = new VirtualImageProxy("logo.svg");
        System.out.println("      真实加载次数 = " + RealImage.getLoadCount()
                + "（3 个代理对象只占了 3 个字符串的内存）");

        System.out.println();
        System.out.println("  -- 用户只点开了 img1 --");
        img1.display();
        System.out.println("      真实加载次数 = " + RealImage.getLoadCount());

        System.out.println();
        System.out.println("  -- 再显示一次 img1（对象已存在，不再加载）--");
        img1.display();
        System.out.println("      真实加载次数 = " + RealImage.getLoadCount());

        System.out.println();
        System.out.println("  -- 验证：img2/img3 从未被访问，所以从未加载 --");
        System.out.println("      img1.isLoaded() = " + img1.isLoaded());
        System.out.println("      img2.isLoaded() = " + img2.isLoaded());
        System.out.println("      img3.isLoaded() = " + img3.isLoaded());
        System.out.println("      getFilename() 不会触发加载：" + img2.getFilename()
                + "，isLoaded 仍为 " + img2.isLoaded());
        System.out.println();
        System.out.println("      结论：加载了 1 张而不是 3 张，节省 "
                + (3 - RealImage.getLoadCount()) + " 次昂贵 IO");
        System.out.println();
    }

    /**
     * 演示 2：保护代理 = 权限控制。
     */
    private static void demo2ProtectedProxy() {
        System.out.println("=== 2. 保护代理：权限控制 ===");
        RealImage.resetLoadCount();

        Image secret = new VirtualImageProxy("财务报表.xlsx");

        Image adminView = new ProtectedImageProxy(secret, "ADMIN");
        Image guestView = new ProtectedImageProxy(secret, "GUEST");

        System.out.println("  -- 访客尝试查看 --");
        guestView.display();
        System.out.println("      真实加载次数 = " + RealImage.getLoadCount()
                + "  ← 关键：鉴权失败时图片根本没被加载，内存和 IO 都省了");

        System.out.println();
        System.out.println("  -- 管理员查看 --");
        adminView.display();
        System.out.println("      真实加载次数 = " + RealImage.getLoadCount());

        System.out.println();
        System.out.println("      getFilename() 不敏感，无需鉴权：" + guestView.getFilename());
        System.out.println();
    }

    /**
     * 演示 3：缓存代理 = 避免重复的昂贵操作。
     */
    private static void demo3CachingProxy() {
        System.out.println("=== 3. 缓存代理：避免重复渲染 ===");
        RealImage.resetLoadCount();

        CachingImageProxy cached = new CachingImageProxy(new VirtualImageProxy("hero.png"));

        System.out.println("  -- 第 1 次显示（未命中）--");
        cached.display();
        System.out.println("  -- 第 2 次显示（命中）--");
        cached.display();
        System.out.println("  -- 第 3 次显示（命中）--");
        cached.display();

        System.out.println();
        System.out.println("      命中 " + cached.getHitCount() + " 次，未命中 "
                + cached.getMissCount() + " 次，真实加载 " + RealImage.getLoadCount() + " 次");

        System.out.println();
        System.out.println("  -- 图片文件被修改，必须让缓存失效 --");
        cached.invalidate();
        cached.display();
        System.out.println("      失效后重新加载，真实加载次数 = " + RealImage.getLoadCount());
        System.out.println();
    }

    /**
     * 演示 4：代理链——多个代理层层嵌套，各管一件事。
     *
     * <p>这正是 Spring AOP 的做法：一个 Bean 可能同时被套上
     * 事务代理、缓存代理、鉴权代理、日志代理。
     */
    private static void demo4ProxyChain() {
        System.out.println("=== 4. 代理链：鉴权 → 缓存 → 延迟加载 → 真实对象 ===");
        RealImage.resetLoadCount();

        Image image = new ProtectedImageProxy(          // 第 1 层：先鉴权
                new CachingImageProxy(                  // 第 2 层：再查缓存
                        new VirtualImageProxy("report.pdf")  // 第 3 层：延迟加载
                ), "EDITOR");

        System.out.println("  -- 无权限用户（换成 GUEST 会怎样？见下）--");
        image.display();
        System.out.println("  -- 第二次（应命中缓存）--");
        image.display();

        System.out.println();
        Image denied = new ProtectedImageProxy(
                new CachingImageProxy(new VirtualImageProxy("salary.xlsx")), "GUEST");
        denied.display();
        System.out.println("      整条链路在第 1 层就被截断，真实加载次数 = "
                + RealImage.getLoadCount());
        System.out.println();
    }

    /**
     * 演示 5：JDK 动态代理——一份代码织入所有增强。
     */
    private static void demo5DynamicProxy() {
        System.out.println("=== 5. JDK 动态代理：日志 + 计时 + 鉴权，零手写转发 ===");
        RealImage.resetLoadCount();

        Image proxy = DynamicImageProxy.wrap(new VirtualImageProxy("dynamic.png"), "ADMIN");

        System.out.println("  -- 代理对象的运行时类型 --");
        System.out.println("      proxy.getClass().getName() = " + proxy.getClass().getName());
        System.out.println("      是 JVM 运行时生成的类，源码里根本不存在这个文件");

        System.out.println();
        System.out.println("  -- 调用 getFilename()（非敏感方法，放行）--");
        System.out.println("      返回：" + proxy.getFilename());

        System.out.println();
        System.out.println("  -- 调用 display()（敏感方法，ADMIN 放行）--");
        proxy.display();

        System.out.println();
        Image denied = DynamicImageProxy.wrap(new VirtualImageProxy("secret.png"), "ANONYMOUS");
        System.out.println("  -- 同一份 handler 代码，换个角色就拦截 --");
        denied.display();
        System.out.println("      真实加载次数 = " + RealImage.getLoadCount()
                + "（拦截发生在加载之前）");
        System.out.println();
        System.out.println("      对比静态代理：3 种代理 = 3 个类，每个方法都要手写转发。");
        System.out.println("      动态代理：1 个 InvocationHandler 搞定全部方法、全部增强。");
    }
}
