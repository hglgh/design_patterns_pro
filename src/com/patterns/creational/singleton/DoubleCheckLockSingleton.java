package com.patterns.creational.singleton;

/**
 * 双重检查锁定单例（Double-Checked Locking，DCL）。
 *
 * <p>两次 null 检查 + {@code volatile} 关键字，兼顾懒加载与性能。
 * <ul>
 *   <li>第一次检查：避免不必要的同步（实例已存在时直接返回）。</li>
 *   <li>同步块：保证只有一个线程进入创建逻辑。</li>
 *   <li>第二次检查：防止多个线程在锁外等待时重复创建。</li>
 *   <li>{@code volatile}：禁止指令重排序，保证其他线程看到的是完整初始化的对象。</li>
 * </ul>
 *
 * <p>注意：没有 {@code volatile}，{@code new} 操作的三步（分配内存 → 初始化 → 引用赋值）
 * 可能被重排序为 1→3→2，导致其他线程拿到未初始化完成的对象。
 *
 * @author design-patterns-pro
 */
public final class DoubleCheckLockSingleton {

    /** volatile 禁止指令重排序，保证可见性。 */
    private static volatile DoubleCheckLockSingleton instance;

    private DoubleCheckLockSingleton() {
    }

    /**
     * 获取唯一实例，双重检查锁定。
     *
     * @return 单例对象
     */
    public static DoubleCheckLockSingleton getInstance() {
        if (instance == null) {                         // 第一次检查（无锁）
            synchronized (DoubleCheckLockSingleton.class) {
                if (instance == null) {                 // 第二次检查（有锁）
                    instance = new DoubleCheckLockSingleton();
                }
            }
        }
        return instance;
    }

    public void showMessage() {
        System.out.println("[DCLSingleton] 我是双重检查锁定单例，hashCode=" + hashCode());
    }
}
