package com.patterns.creational.builder;

/**
 * 抽象建造者：电脑建造者接口。
 *
 * <p>传统 Builder 模式的核心 —— 定义"分步骤构造产品"的抽象接口。
 * 具体建造者（{@link GamingComputerBuilder} / {@link OfficeComputerBuilder}）
 * 实现每一步的具体逻辑。
 *
 * <p>与简化 Builder（{@link Computer.Builder}）的区别：
 * <ul>
 *   <li>传统 Builder：有 Director 编排固定流程，适合"组装步骤固定"的场景。</li>
 *   <li>简化 Builder：链式调用，无固定流程，适合"参数可选、顺序自由"的场景。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public interface ComputerBuilder {

    void buildCpu();

    void buildMemory();

    void buildHardDisk();

    void buildGpu();

    void buildPowerSupply();

    void buildCase();

    /**
     * 返回构造完成的产品。
     *
     * @return 电脑实例
     */
    Computer getResult();
}
