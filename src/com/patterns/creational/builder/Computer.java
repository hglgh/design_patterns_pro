package com.patterns.creational.builder;

/**
 * 产品：电脑。
 *
 * <p>包含多个可选部件（CPU / 内存 / 硬盘 / GPU / 电源 / 机箱），
 * 构造过程复杂，适合用建造者模式分步骤组装。
 *
 * <p>本类同时提供两种建造方式：
 * <ul>
 *   <li>传统 Builder（{@link ComputerBuilder} + {@link Director}）：适合有固定组装流程的场景。</li>
 *   <li>简化 Builder（{@link Builder} 静态内部类）：适合链式调用、无固定流程的场景。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public final class Computer {

    private final String cpu;
    private final String memory;
    private final String hardDisk;
    private final String gpu;
    private final String powerSupply;
    private final String caseType;

    /** 私有构造器，只能通过 Builder 创建。 */
    private Computer(Builder builder) {
        this.cpu = builder.cpu;
        this.memory = builder.memory;
        this.hardDisk = builder.hardDisk;
        this.gpu = builder.gpu;
        this.powerSupply = builder.powerSupply;
        this.caseType = builder.caseType;
    }

    public String getCpu() { return cpu; }
    public String getMemory() { return memory; }
    public String getHardDisk() { return hardDisk; }
    public String getGpu() { return gpu; }
    public String getPowerSupply() { return powerSupply; }
    public String getCaseType() { return caseType; }

    @Override
    public String toString() {
        return "Computer{" +
                "cpu='" + cpu + '\'' +
                ", memory='" + memory + '\'' +
                ", hardDisk='" + hardDisk + '\'' +
                ", gpu='" + gpu + '\'' +
                ", powerSupply='" + powerSupply + '\'' +
                ", caseType='" + caseType + '\'' +
                '}';
    }

    /** 获取简化 Builder 实例。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 简化 Builder（静态内部类）。
     *
     * <p>链式调用，build() 时校验必填字段。
     */
    public static final class Builder {
        // 必填字段
        private String cpu;
        private String memory;
        // 可选字段
        private String hardDisk = "512GB SSD";       // 默认值
        private String gpu = "集成显卡";
        private String powerSupply = "500W";
        private String caseType = "标准机箱";

        public Builder cpu(String cpu) { this.cpu = cpu; return this; }
        public Builder memory(String memory) { this.memory = memory; return this; }
        public Builder hardDisk(String hardDisk) { this.hardDisk = hardDisk; return this; }
        public Builder gpu(String gpu) { this.gpu = gpu; return this; }
        public Builder powerSupply(String powerSupply) { this.powerSupply = powerSupply; return this; }
        public Builder caseType(String caseType) { this.caseType = caseType; return this; }

        /**
         * 构建 Computer 实例，校验必填字段。
         *
         * @return 不可变的 Computer 对象
         * @throws IllegalStateException 如果必填字段未设置
         */
        public Computer build() {
            if (cpu == null || cpu.isBlank()) {
                throw new IllegalStateException("CPU 不能为空");
            }
            if (memory == null || memory.isBlank()) {
                throw new IllegalStateException("内存不能为空");
            }
            return new Computer(this);
        }
    }
}
