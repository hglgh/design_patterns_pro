package com.patterns.creational.singleton.beginner;

/**
 * 单例模式入门演示：一份全局配置，被两个服务各自 new 了一次。
 *
 * <p>改造前：每个服务自己 {@code new ConfigFile()}，于是一份配置变成多份；
 * 改造后：私有构造 + 唯一实例，所有人拿到的都是同一个对象。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：谁用谁 new ==========");

        ConfigFile orderConfig = new ConfigFile();
        ConfigFile couponConfig = new ConfigFile();

        System.out.println("订单服务读到 timeout = " + orderConfig.timeoutSeconds() + " 秒");
        System.out.println("优惠券服务把 timeout 改成 30 秒");
        couponConfig.setTimeoutSeconds(30);

        System.out.println("订单服务再读 timeout = " + orderConfig.timeoutSeconds() + " 秒");
        System.out.println("配置被读取 " + ConfigFile.loadCount + " 次");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 同一个配置在内存里有多份，改一份，其他服务看不到；");
        System.out.println("  2. 每 new 一次就重新读一次文件，启动变成多次 IO；");
        System.out.println("  3. 谁都可以再 new，无法保证「全局只有一份」。");
    }

    static class ConfigFile {

        static int loadCount;
        private int timeoutSeconds;

        ConfigFile() {
            loadCount++;
            timeoutSeconds = 10;
            System.out.println("  [配置] 读取 config.properties（第 " + loadCount + " 次）");
        }

        int timeoutSeconds() {
            return timeoutSeconds;
        }

        void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：全局唯一实例 ==========");

        AppConfig first = AppConfig.getInstance();
        AppConfig second = AppConfig.getInstance();

        System.out.println("两次 getInstance 是同一个对象吗？" + (first == second));
        System.out.println("订单服务读到 timeout = " + first.timeoutSeconds() + " 秒");

        System.out.println("优惠券服务把 timeout 改成 30 秒");
        second.setTimeoutSeconds(30);

        System.out.println("订单服务再读 timeout = " + first.timeoutSeconds() + " 秒");
        System.out.println("配置构造发生了 " + AppConfig.constructCount + " 次");

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 这是一个全局可变状态，任何一个调用方改它都会影响所有人；");
        System.out.println("  2. 单元测试之间会共享它，测试顺序可能互相污染；");
        System.out.println("  3. 依赖被藏进 getInstance()，只看方法签名看不出它用了配置。");
        System.out.println("  如果只是几个纯函数，用静态工具类更简单；");
        System.out.println("  如果是业务对象，优先交给 Spring 等容器管理单例，而不是手写。");
    }

    static class AppConfig {

        /** 类加载时创建唯一实例；JVM 保证类初始化只执行一次。 */
        private static final AppConfig INSTANCE = new AppConfig();

        static int constructCount;

        private int timeoutSeconds;

        private AppConfig() {
            constructCount++;
            timeoutSeconds = 10;
            System.out.println("  [配置] 读取 config.properties（只读一次）");
        }

        static AppConfig getInstance() {
            return INSTANCE;
        }

        int timeoutSeconds() {
            return timeoutSeconds;
        }

        void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
