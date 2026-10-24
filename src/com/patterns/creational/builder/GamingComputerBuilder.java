package com.patterns.creational.builder;

/**
 * 具体建造者：游戏电脑建造者。
 *
 * <p>每个 buildXxx 方法负责一个部件的选型，体现"分步骤构造"。
 *
 * @author design-patterns-pro
 */
public class GamingComputerBuilder implements ComputerBuilder {

    private final Computer.Builder builder = Computer.builder();

    @Override
    public void buildCpu() {
        builder.cpu("Intel i9-14900K");
    }

    @Override
    public void buildMemory() {
        builder.memory("64GB DDR5 6000MHz");
    }

    @Override
    public void buildHardDisk() {
        builder.hardDisk("2TB NVMe Gen5 SSD");
    }

    @Override
    public void buildGpu() {
        builder.gpu("NVIDIA RTX 4090 24GB");
    }

    @Override
    public void buildPowerSupply() {
        builder.powerSupply("1200W 金牌全模组");
    }

    @Override
    public void buildCase() {
        builder.caseType("全塔电竞机箱（带 RGB）");
    }

    @Override
    public Computer getResult() {
        return builder.build();
    }
}
