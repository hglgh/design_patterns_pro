package com.patterns.creational.builder;

/**
 * 具体建造者：办公电脑建造者。
 *
 * <p>与 {@link GamingComputerBuilder} 共用同一套抽象接口，
 * 但每一步的选型不同，体现"同一构造流程，不同产品表示"。
 *
 * @author design-patterns-pro
 */
public class OfficeComputerBuilder implements ComputerBuilder {

    private final Computer.Builder builder = Computer.builder();

    @Override
    public void buildCpu() {
        builder.cpu("Intel i5-14400");
    }

    @Override
    public void buildMemory() {
        builder.memory("16GB DDR4 3200MHz");
    }

    @Override
    public void buildHardDisk() {
        builder.hardDisk("512GB NVMe SSD");
    }

    @Override
    public void buildGpu() {
        builder.gpu("集成显卡 Intel UHD 730");
    }

    @Override
    public void buildPowerSupply() {
        builder.powerSupply("400W 铜牌");
    }

    @Override
    public void buildCase() {
        builder.caseType("小型静音机箱");
    }

    @Override
    public Computer getResult() {
        return builder.build();
    }
}
