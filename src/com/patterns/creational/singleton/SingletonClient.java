package com.patterns.creational.singleton;

/**
 * 单例模式客户端演示。
 *
 * <p>依次展示 5 种单例实现，并通过多线程验证唯一性。
 *
 * @author design-patterns-pro
 */
public class SingletonClient {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== 1. 饿汉式 ==========");
        EagerSingleton eager1 = EagerSingleton.getInstance();
        EagerSingleton eager2 = EagerSingleton.getInstance();
        eager1.showMessage();
        System.out.println("两次获取是否同一对象：" + (eager1 == eager2));

        System.out.println("\n========== 2. 懒汉式（synchronized） ==========");
        LazySingleton lazy1 = LazySingleton.getInstance();
        LazySingleton lazy2 = LazySingleton.getInstance();
        lazy1.showMessage();
        System.out.println("两次获取是否同一对象：" + (lazy1 == lazy2));

        System.out.println("\n========== 3. 双重检查锁定（DCL） ==========");
        DoubleCheckLockSingleton dcl1 = DoubleCheckLockSingleton.getInstance();
        DoubleCheckLockSingleton dcl2 = DoubleCheckLockSingleton.getInstance();
        dcl1.showMessage();
        System.out.println("两次获取是否同一对象：" + (dcl1 == dcl2));

        System.out.println("\n========== 4. 静态内部类 ==========");
        StaticInnerClassSingleton inner1 = StaticInnerClassSingleton.getInstance();
        StaticInnerClassSingleton inner2 = StaticInnerClassSingleton.getInstance();
        inner1.showMessage();
        System.out.println("两次获取是否同一对象：" + (inner1 == inner2));

        System.out.println("\n========== 5. 枚举（推荐） ==========");
        EnumSingleton enum1 = EnumSingleton.INSTANCE;
        EnumSingleton enum2 = EnumSingleton.INSTANCE;
        enum1.showMessage();
        enum2.showMessage();
        System.out.println("两次获取是否同一对象：" + (enum1 == enum2));

        System.out.println("\n========== 6. 多线程并发验证（DCL） ==========");
        verifyThreadSafety();
    }

    /**
     * 启动 100 个线程并发获取 DCL 单例，验证所有线程拿到的是同一对象。
     */
    private static void verifyThreadSafety() throws InterruptedException {
        final int threadCount = 100;
        Thread[] threads = new Thread[threadCount];
        final DoubleCheckLockSingleton[] results = new DoubleCheckLockSingleton[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> results[idx] = DoubleCheckLockSingleton.getInstance());
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }

        boolean allSame = true;
        for (int i = 1; i < threadCount; i++) {
            if (results[i] != results[0]) {
                allSame = false;
                break;
            }
        }
        System.out.println(threadCount + " 个线程并发获取，是否全部为同一实例：" + allSame);
    }
}
