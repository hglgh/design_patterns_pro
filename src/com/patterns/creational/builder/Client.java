package com.patterns.creational.builder;

/**
 * 建造者模式客户端演示。
 *
 * <p>同时展示两种 Builder 风格：
 * <ol>
 *   <li>传统 Builder（Director + ComputerBuilder）：适合有固定组装流程。</li>
 *   <li>简化 Builder（Computer.builder() 链式调用）：适合参数可选、顺序自由。</li>
 * </ol>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        System.out.println("========== 建造者模式演示 ==========\n");

        // ===== 方式 1：传统 Builder（Director 编排）=====
        System.out.println("--- 方式 1：传统 Builder ---");

        Director gamingDirector = new Director(new GamingComputerBuilder());
        Computer gamingPc = gamingDirector.construct();
        System.out.println("游戏电脑：" + gamingPc);

        Director officeDirector = new Director(new OfficeComputerBuilder());
        Computer officePc = officeDirector.construct();
        System.out.println("办公电脑：" + officePc);

        // 同一套流程，只走前 3 步。注意这里必须换一个全新的 Builder：
        // Builder 是有状态的容器，上面 construct() 已经把 6 个字段全写进去了，
        // 复用同一个实例再调 constructMinimal() 只会拿到上一次的残留值。
        Computer minimalPc = new Director(new GamingComputerBuilder()).constructMinimal();
        System.out.println("最小配置（只装核心部件）：" + minimalPc);

        // ===== 方式 2：简化 Builder（链式调用）=====
        System.out.println("\n--- 方式 2：简化 Builder（链式调用）---");

        Computer customPc = Computer.builder()
                .cpu("AMD Ryzen 9 7950X")
                .memory("32GB DDR5")
                .hardDisk("1TB NVMe SSD")
                .gpu("AMD RX 7900 XTX")
                .powerSupply("850W 金牌")
                .caseType("海景房机箱")
                .build();
        System.out.println("自定义电脑：" + customPc);

        // 使用默认值（只设置必填字段）
        Computer budgetPc = Computer.builder()
                .cpu("Intel i3-14100")
                .memory("8GB DDR4")
                .build();
        System.out.println("入门电脑（其余用默认值）：" + budgetPc);

        // ===== 方式 3：校验必填字段 =====
        System.out.println("\n--- 方式 3：校验必填字段 ---");
        try {
            Computer invalid = Computer.builder()
                    .memory("16GB")  // 没设 CPU
                    .build();
        } catch (IllegalStateException e) {
            System.out.println("构建失败（符合预期）：" + e.getMessage());
        }
    }
}
