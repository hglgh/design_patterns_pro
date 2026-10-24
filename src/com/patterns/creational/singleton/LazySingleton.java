package com.patterns.creational.singleton;

/**
 * 懒汉式单例（Lazy Initialization，线程安全版）。
 *
 * <p>第一次调用 {@code getInstance()} 时才创建实例，整个方法加 {@code synchronized}。
 * 优点：支持懒加载；缺点：每次获取实例都要抢锁，性能差。
 *
 * <p>适用场景：实例创建开销大、访问频率低的教学演示。生产环境更推荐 DCL 或静态内部类。
 *
 * @author design-patterns-pro
 */
public final class LazySingleton {

    private static LazySingleton instance;

    private LazySingleton() {
    }

    /**
     * 获取唯一实例，整个方法同步。
     *
     * @return 单例对象
     */
    public static synchronized LazySingleton getInstance() {
        if (instance == null) {
            instance = new LazySingleton();
        }
        return instance;
    }

    public void showMessage() {
        System.out.println("[LazySingleton] 我是懒汉式单例（synchronized），hashCode=" + hashCode());
    }
}
