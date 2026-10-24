package com.patterns.creational.builder;

/**
 * 指挥者：负责编排建造步骤的顺序。
 *
 * <p>Director 把"构造流程"与"具体选型"解耦：
 * <ul>
 *   <li>构造流程（先 CPU、再内存、再硬盘...）由 Director 固定。</li>
 *   <li>具体选型（i9 还是 i5）由注入的 {@link ComputerBuilder} 决定。</li>
 * </ul>
 *
 * <p>同一个 Director 可以搭配不同的 Builder，产出不同配置的电脑。
 *
 * @author design-patterns-pro
 */
public class Director {

    private final ComputerBuilder builder;

    public Director(ComputerBuilder builder) {
        this.builder = builder;
    }

    /**
     * 按固定顺序执行全部建造步骤。
     *
     * @return 构造完成的电脑
     */
    public Computer construct() {
        builder.buildCpu();
        builder.buildMemory();
        builder.buildHardDisk();
        builder.buildGpu();
        builder.buildPowerSupply();
        builder.buildCase();
        return builder.getResult();
    }

    /**
     * 只构造核心部件（CPU + 内存 + 硬盘），用于"最小可用配置"场景。
     *
     * <p>体现 Director 的价值：同一 Builder，不同构造流程。
     *
     * @return 最小配置的电脑
     */
    public Computer constructMinimal() {
        builder.buildCpu();
        builder.buildMemory();
        builder.buildHardDisk();
        return builder.getResult();
    }
}
