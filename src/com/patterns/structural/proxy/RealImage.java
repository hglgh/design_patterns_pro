package com.patterns.structural.proxy;

/**
 * 真实主题（RealSubject）：真正执行"加载并显示图片"的对象。
 *
 * <p><b>相比原项目改了什么？为什么？</b>
 * 原项目写的是：
 * <pre>
 *   public record RealImage(String filename) implements Image {
 *       public RealImage(String filename) {
 *           this.filename = filename;
 *           loadFromDisk();          // 构造器里做 IO
 *       }
 *   }
 * </pre>
 * 改成普通 final class，原因有三：
 * <ol>
 *   <li><b>record 的语义是"纯数据载体"</b>，而 RealImage 的构造器带有 IO 副作用
 *       （从磁盘加载）。用 record 表达一个有副作用的对象会误导读者。</li>
 *   <li><b>record 自动生成 equals/hashCode/toString</b>，其中 toString 会打印所有组件。
 *       真实图片对象若持有 byte[] 像素数据，toString 会打印出巨长字符串，
 *       一旦有人 {@code log.info("img={}", realImage)} 就是灾难。</li>
 *   <li><b>record 隐式 final，无法被 CGLIB 等字节码框架代理</b>。
 *       虽然本 Demo 用 JDK 动态代理（基于接口，不受此限），
 *       但作为"真实主题"保留可继承性更符合框架生态的假设。</li>
 * </ol>
 *
 * <p>另外新增了 {@code loadCount} 静态计数器，用来<b>可观测地证明</b>
 * "虚拟代理确实延迟了加载""缓存代理确实避免了重复加载"。
 * 原项目只能靠肉眼看控制台输出的先后顺序推断，不够严谨。
 *
 * @author design-patterns-pro
 */
public final class RealImage implements Image {

    /** 全局加载次数计数器，用于验证代理是否真的省掉了加载。 */
    private static int loadCount = 0;

    private final String filename;

    /**
     * 构造即加载——这正是"昂贵"的地方，也是代理存在的理由。
     *
     * @param filename 文件名
     */
    public RealImage(String filename) {
        this.filename = filename;
        loadFromDisk();
    }

    /**
     * 模拟从磁盘加载图片（真实场景会读取文件、解码 PNG/JPEG、分配像素缓冲区）。
     */
    private void loadFromDisk() {
        loadCount++;
        System.out.println("      [RealImage] ★ 从磁盘加载图片：" + filename
                + "（第 " + loadCount + " 次真实加载）");
    }

    @Override
    public void display() {
        System.out.println("      [RealImage] 显示图片：" + filename);
    }

    @Override
    public String getFilename() {
        return filename;
    }

    /**
     * @return 至今为止真实加载图片的次数
     */
    public static int getLoadCount() {
        return loadCount;
    }

    /**
     * 重置计数器，便于各个演示场景独立统计。
     */
    public static void resetLoadCount() {
        loadCount = 0;
    }
}
