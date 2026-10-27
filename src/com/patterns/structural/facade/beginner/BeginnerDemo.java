package com.patterns.structural.facade.beginner;

/**
 * 外观入门演示：启动一个应用需要依次操作 CPU、内存、磁盘、网络四个子系统。
 *
 * <p>改造前：每个应用各自编排启动顺序，漏一步就启动失败，顺序还会写错；
 * 改造后：外观类提供一个 start()，把顺序和检查收在一处。
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
        System.out.println("========== 改造前：每个应用自己编排 ==========");

        Cpu cpu1 = new Cpu();
        Memory memory1 = new Memory();
        Disk disk1 = new Disk();
        Network network1 = new Network();

        System.out.println("  —— 订单应用启动 ——");
        cpu1.powerOn();
        memory1.load();
        disk1.mount();
        // 忘了 network.connect()
        System.out.println("  启动结果：" + (network1.isConnected() ? "成功" : "失败：网络未连接"));

        System.out.println("  —— 报表应用启动 ——");
        Cpu cpu2 = new Cpu();
        Memory memory2 = new Memory();
        Disk disk2 = new Disk();
        Network network2 = new Network();
        network2.connect();       // 顺序反了：网络先连
        cpu2.powerOn();
        memory2.load();
        // 忘了 disk.mount()
        System.out.println("  启动结果：磁盘未挂载，读不到报表数据");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 每个应用都要知道四个子系统、正确顺序和必要检查；");
        System.out.println("  2. 漏一步不会编译报错，要到运行时才失败；");
        System.out.println("  3. 启动逻辑复制多份，改顺序要改所有应用。");
    }

    static class Cpu {
        void powerOn() {
            System.out.println("    [CPU] 通电");
        }

        void powerOff() {
            System.out.println("    [CPU] 断电");
        }
    }

    static class Memory {
        void load() {
            System.out.println("    [内存] 加载系统");
        }

        void clear() {
            System.out.println("    [内存] 清空");
        }
    }

    static class Disk {
        void mount() {
            System.out.println("    [磁盘] 挂载");
        }

        void unmount() {
            System.out.println("    [磁盘] 卸载");
        }
    }

    static class Network {
        private boolean connected;

        void connect() {
            connected = true;
            System.out.println("    [网络] 连接");
        }

        void disconnect() {
            connected = false;
            System.out.println("    [网络] 断开");
        }

        boolean isConnected() {
            return connected;
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：外观类收口启动流程 ==========");

        ComputerFacade computer = new ComputerFacade();

        System.out.println("  —— 订单应用启动 ——");
        computer.start();
        System.out.println("  启动结果：成功");

        System.out.println("  —— 报表应用启动 ——");
        computer.start();
        System.out.println("  启动结果：成功");

        System.out.println("  —— 关机 ——");
        computer.shutdown();

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 外观类会逐渐变成「什么都知道」的大类，业务规则不要往里塞；");
        System.out.println("  2. 它只是简化入口，不阻止别人直接操作子系统；");
        System.out.println("  3. 子系统只有一两个、调用只有一处时，外观是多余的一层。");
    }

    /**
     * 外观：客户端只需要说「开机 / 关机」，不需要知道几个子系统、什么顺序。
     */
    static class ComputerFacade {

        private final Cpu cpu = new Cpu();
        private final Memory memory = new Memory();
        private final Disk disk = new Disk();
        private final Network network = new Network();

        void start() {
            cpu.powerOn();
            memory.load();
            disk.mount();
            network.connect();
        }

        void shutdown() {
            // 关机按启动的逆序来：先断网络，再卸磁盘，最后清内存/断电
            network.disconnect();
            disk.unmount();
            memory.clear();
            cpu.powerOff();
        }
    }
}
