package com.patterns.structural.proxy;

import java.lang.reflect.Proxy;

/**
 * 动态代理工厂：用 {@link Proxy#newProxyInstance} 在<b>运行时生成</b>代理类。
 *
 * <p><b>三个参数分别是什么？</b>
 * <ol>
 *   <li>{@code classLoader}：用哪个类加载器加载生成的 {@code $Proxy0} 类。
 *       必须能同时看到目标接口（Image）和 Proxy 类。</li>
 *   <li>{@code interfaces}：代理类要实现哪些接口。<b>JDK 动态代理只能代理接口</b>，
 *       这决定了它无法代理没有接口的类（那种情况要用 CGLIB）。</li>
 *   <li>{@code handler}：所有方法调用的路由目标。</li>
 * </ol>
 *
 * <p><b>运行时到底生成了什么？</b>
 * JVM 动态拼出这样一个类的字节码并加载：
 * <pre>
 *   public final class $Proxy0 extends java.lang.reflect.Proxy implements Image {
 *       private final InvocationHandler h;
 *
 *       public void display() {
 *           h.invoke(this, m_display, null);        // 全部转发给 handler
 *       }
 *       public String getFilename() {
 *           return (String) h.invoke(this, m_getFilename, null);
 *       }
 *   }
 * </pre>
 * 可以看到：<b>它 extends Proxy（用掉了唯一的继承名额），所以只能 implements 接口</b>。
 * 这就是 JDK 动态代理"必须基于接口"的根本原因。
 *
 * <p><b>实际运行时打印的类名是什么？</b>
 * 本项目 JDK 25 实测输出是 {@code jdk.proxy1.$Proxy0}，而不是裸的 {@code $Proxy0}。
 * 前缀 {@code jdk.proxy1} 是 JDK 9 模块化之后引入的：动态代理类被放进一个
 * <b>运行时才创建的动态模块</b>里，而不是无名包。所以判断"这是不是代理对象"
 * 不要写 {@code name.equals("$Proxy0")}，而要用 {@code Proxy.isProxyClass(clazz)}。
 *
 * <p>加上 JVM 参数 {@code -Djdk.proxy.ProxyGenerator.saveGeneratedFiles=true}
 * 可以把生成的 {@code $Proxy0.class} 落到磁盘，用反编译工具查看。
 *
 * @author design-patterns-pro
 */
public final class DynamicImageProxy {

    /** 工具类，禁止实例化。 */
    private DynamicImageProxy() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 为目标对象生成一个带日志、计时、权限检查的动态代理。
     *
     * @param target 被代理对象
     * @param role   模拟的当前用户角色
     * @return 代理对象，静态类型是 {@link Image}
     */
    public static Image wrap(Image target, String role) {
        return (Image) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{Image.class},
                new ImageInvocationHandler(target, role));
    }
}
