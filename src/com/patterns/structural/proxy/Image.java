package com.patterns.structural.proxy;

/**
 * 抽象主题（Subject）：代理与真实对象共同实现的接口。
 *
 * <p><b>这个接口是代理模式成立的唯一前提。</b>
 * 只有代理和真实对象类型相同，客户端才能"无感知"地持有代理：
 * <pre>
 *   Image image = new VirtualImageProxy("a.png");  // 客户端以为拿到的是图片
 *   image.display();                                // 实际先经过代理的逻辑
 * </pre>
 * 如果代理不实现这个接口，客户端就必须写成 {@code proxy.getRealImage().display()}，
 * 代理的"透明性"荡然无存。
 *
 * @author design-patterns-pro
 * @see RealImage           真实主题：真正干活的对象
 * @see VirtualImageProxy   虚拟代理：控制"何时创建"真实对象
 * @see ProtectedImageProxy 保护代理：控制"谁能访问"真实对象
 * @see CachingImageProxy   缓存代理：控制"是否真的去访问"真实对象
 */
public interface Image {

    /**
     * 显示图片。
     */
    void display();

    /**
     * @return 文件名，用于日志与缓存 key
     */
    String getFilename();
}
