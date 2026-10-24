package com.patterns.creational.singleton;

/**
 * 静态内部类单例（Initialization-on-Demand Holder Idiom）。
 *
 * <p>利用 JVM 类加载机制保证线程安全：
 * <ul>
 *   <li>外部类加载时，内部类 {@code Holder} 不会被加载（懒加载）。</li>
 *   <li>第一次调用 {@code getInstance()} 时才触发 {@code Holder} 类加载，
 *       JVM 保证一个类只被加载一次，且加载过程线程安全。</li>
 * </ul>
 *
 * <p>优点：懒加载 + 无线程同步开销 + 实现优雅。<strong>推荐写法之一</strong>。
 *
 * @author design-patterns-pro
 */
public final class StaticInnerClassSingleton {

    private StaticInnerClassSingleton() {
    }

    /** 静态内部类，持有单例实例。 */
    private static final class Holder {
        private static final StaticInnerClassSingleton INSTANCE = new StaticInnerClassSingleton();
    }

    /**
     * 获取唯一实例。
     *
     * @return 单例对象
     */
    public static StaticInnerClassSingleton getInstance() {
        return Holder.INSTANCE;
    }

    public void showMessage() {
        System.out.println("[StaticInnerClassSingleton] 我是静态内部类单例，hashCode=" + hashCode());
    }
}
