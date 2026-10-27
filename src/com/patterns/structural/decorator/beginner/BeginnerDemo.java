package com.patterns.structural.decorator.beginner;

/**
 * 装饰器入门演示：咖啡加奶、加糖、加燕麦奶。
 *
 * <p>改造前：用继承为每种组合写一个类，3 种加料要 8 个类，还不能运行时叠加；
 * 改造后：装饰器实现同一个 Coffee 接口并持有被装饰对象，像套娃一样一层层加。
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
        System.out.println("========== 改造前：每种组合一个子类 ==========");

        System.out.println("  " + new OldEspresso().describe());
        System.out.println("  " + new OldMilkEspresso().describe());
        System.out.println("  " + new OldSugarEspresso().describe());
        System.out.println("  " + new OldMilkSugarEspresso().describe());

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 3 种加料 = 8 个组合类，4 种 = 16 个；");
        System.out.println("  2. 想「加两份奶」？没有 MilkMilkEspresso 这个类；");
        System.out.println("  3. 想运行时根据用户选择叠加？继承在编译期就定死了。");
    }

    abstract static class OldCoffee {
        abstract int cost();

        abstract String describe();
    }

    static class OldEspresso extends OldCoffee {
        @Override
        int cost() {
            return 10;
        }

        @Override
        String describe() {
            return "浓缩（" + cost() + " 元）";
        }
    }

    static class OldMilkEspresso extends OldCoffee {
        @Override
        int cost() {
            return 12;
        }

        @Override
        String describe() {
            return "浓缩+奶（" + cost() + " 元）";
        }
    }

    static class OldSugarEspresso extends OldCoffee {
        @Override
        int cost() {
            return 11;
        }

        @Override
        String describe() {
            return "浓缩+糖（" + cost() + " 元）";
        }
    }

    static class OldMilkSugarEspresso extends OldCoffee {
        @Override
        int cost() {
            return 13;
        }

        @Override
        String describe() {
            return "浓缩+奶+糖（" + cost() + " 元）";
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：运行时一层层套 ==========");

        Coffee plain = new Espresso();
        Coffee milk = new Milk(plain);
        Coffee milkSugar = new Sugar(milk);
        Coffee doubleMilk = new Milk(new Milk(plain));

        show(plain);
        show(milk);
        show(milkSugar);
        show(doubleMilk);

        System.out.println("  —— 新增燕麦奶：只加一个装饰器类，已有类都不改 ——");
        show(new Sugar(new OatMilk(plain)));

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每加一种功能多一个小类，层数多了调试要逐层点进去；");
        System.out.println("  2. 装饰顺序会影响结果（描述顺序、某些前后处理），调用方要理解顺序；");
        System.out.println("  3. 如果功能组合是固定且很少的，直接写几个类更直观。");
    }

    /** 组件接口：被装饰者和装饰者都实现它。 */
    interface Coffee {
        int cost();

        String describe();
    }

    static class Espresso implements Coffee {
        @Override
        public int cost() {
            return 10;
        }

        @Override
        public String describe() {
            return "浓缩";
        }
    }

    private static void show(Coffee coffee) {
        System.out.println("  " + coffee.describe() + "（" + coffee.cost() + " 元）");
    }

    /** 装饰者基类：持有同一个接口，负责把调用转发给内层。 */
    abstract static class CoffeeDecorator implements Coffee {

        protected final Coffee inner;

        CoffeeDecorator(Coffee inner) {
            this.inner = inner;
        }
    }

    static class Milk extends CoffeeDecorator {

        Milk(Coffee inner) {
            super(inner);
        }

        @Override
        public int cost() {
            return inner.cost() + 2;
        }

        @Override
        public String describe() {
            return inner.describe() + " + 奶";
        }
    }

    static class Sugar extends CoffeeDecorator {

        Sugar(Coffee inner) {
            super(inner);
        }

        @Override
        public int cost() {
            return inner.cost() + 1;
        }

        @Override
        public String describe() {
            return inner.describe() + " + 糖";
        }
    }

    // ---- 新加料：只新增一个类 ----

    static class OatMilk extends CoffeeDecorator {

        OatMilk(Coffee inner) {
            super(inner);
        }

        @Override
        public int cost() {
            return inner.cost() + 3;
        }

        @Override
        public String describe() {
            return inner.describe() + " + 燕麦奶";
        }
    }
}
