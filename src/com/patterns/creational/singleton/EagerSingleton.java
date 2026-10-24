package com.patterns.creational.singleton;

/**
 * 饿汉式单例（Eager Initialization）。
 *
 * <p>类加载时就创建实例，由 JVM 类加载机制保证线程安全。
 * 优点：实现简单、无线程同步开销；缺点：不支持懒加载，类加载即占用内存。
 *
 * <p>适用场景：实例占用资源少、程序启动后一定会用到。
 *
 * @author design-patterns-pro
 */
public final class EagerSingleton {

    /** 类加载时即初始化，final 保证不可被反射修改（仍可通过反射破解，见枚举方案）。 */
    private static final EagerSingleton INSTANCE = new EagerSingleton();

    /** 私有构造器，阻止外部 new。 */
    private EagerSingleton() {
    }

    /**
     * 获取唯一实例。
     *
     * @return 单例对象
     */
    public static EagerSingleton getInstance() {
        return INSTANCE;
    }

    /** 业务方法示例。 */
    public void showMessage() {
        System.out.println("[EagerSingleton] 我是饿汉式单例，hashCode=" + hashCode());
    }
}
