package com.patterns.creational.singleton;

/**
 * 枚举单例（Enum Singleton）—— <strong>最推荐的写法</strong>。
 *
 * <p>《Effective Java》Item 3 明确推荐：
 * <ul>
 *   <li>JVM 保证枚举实例的唯一性与线程安全。</li>
 *   <li>自动支持序列化（无需手写 {@code readResolve}）。</li>
 *   <li>绝对防止反射攻击（{@code Constructor.newInstance} 对枚举抛
 *       {@code IllegalArgumentException}）。</li>
 * </ul>
 *
 * <p>缺点：不能继承其他类（枚举隐式继承 {@link Enum}）。
 *
 * @author design-patterns-pro
 */
public enum EnumSingleton {

    /** 唯一实例。 */
    INSTANCE;

    /** 业务状态示例（枚举也可持有字段与方法）。 */
    private int counter;

    public void showMessage() {
        counter++;
        System.out.println("[EnumSingleton] 我是枚举单例，第 " + counter + " 次调用，hashCode=" + hashCode());
    }

    public int getCounter() {
        return counter;
    }
}
