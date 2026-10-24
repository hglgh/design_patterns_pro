package com.patterns.structural.decorator;

/**
 * 客户端：演示装饰器的动态组合能力。
 *
 * <p>运行方式：
 * <pre>
 *   java -cp out com.patterns.structural.decorator.Client
 * </pre>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demo1BasicDecoration();
        demo2OrderMatters();
        demo3RuntimeComposition();
        demo4RepeatAndParameterize();
        demo5UniformType();
    }

    /**
     * 演示 1：最基础的装饰——一层层包装，最后调用 render()。
     */
    private static void demo1BasicDecoration() {
        System.out.println("=== 1. 基础装饰：加粗 + 红色 ===");

        TextComponent text = new PlainText("Hello, Decorator!");
        System.out.println("原始文本       : " + text.render());

        text = new BoldDecorator(text);
        System.out.println("加粗后         : " + text.render());

        text = new ColorDecorator(text, "red");
        System.out.println("再加红色后     : " + text.render());

        text = new ItalicDecorator(text);
        System.out.println("再加斜体后     : " + text.render());
        System.out.println();
    }

    /**
     * 演示 2：装饰顺序不同，结果不同。
     *
     * <p>这是装饰器与继承的重要差别：继承的层次结构在编译期就固定了，
     * 而装饰器的包装顺序由<b>构造代码的书写顺序</b>在运行期决定。
     */
    private static void demo2OrderMatters() {
        System.out.println("=== 2. 装饰顺序会影响最终结果 ===");

        TextComponent a = new BoldDecorator(
                new ColorDecorator(new PlainText("顺序A"), "blue"));
        TextComponent b = new ColorDecorator(
                new BoldDecorator(new PlainText("顺序B")), "blue");

        System.out.println("先变色再加粗 : " + a.render());
        System.out.println("先加粗再变色 : " + b.render());
        System.out.println("说明：外层装饰器包在更外面，标签嵌套层次随构造顺序改变");
        System.out.println();
    }

    /**
     * 演示 3：根据运行时条件动态决定是否装饰（继承做不到）。
     */
    private static void demo3RuntimeComposition() {
        System.out.println("=== 3. 按运行时条件动态组装样式 ===");

        boolean isVip = true;
        boolean needHighlight = false;

        for (String userName : new String[]{"张三", "李四"}) {
            TextComponent text = new PlainText("欢迎，" + userName);

            if (isVip) {
                text = new BoldDecorator(text);
                text = new ColorDecorator(text, "orange");
            }
            if (needHighlight) {
                text = new UnderlineDecorator(text);
            }

            System.out.println("  " + text.render());
            isVip = false;   // 第二个用户不是 VIP，走不同的装饰路径
        }
        System.out.println();
    }

    /**
     * 演示 4：同一个装饰器可以叠加多次；带参数的装饰器一个类顶多个类。
     */
    private static void demo4RepeatAndParameterize() {
        System.out.println("=== 4. 重复装饰 & 参数化装饰 ===");

        TextComponent quoted = new ItalicDecorator(
                new ItalicDecorator(new PlainText("双层斜体")));
        System.out.println("同一装饰器叠两次 : " + quoted.render());

        // 同一个 ColorDecorator 类，靠参数产生三种不同效果
        System.out.println("红色             : "
                + new ColorDecorator(new PlainText("红"), "red").render());
        System.out.println("蓝色             : "
                + new ColorDecorator(new PlainText("蓝"), "blue").render());
        System.out.println("十六进制色值     : "
                + new ColorDecorator(new PlainText("自定义"), "#336699").render());

        try {
            new ColorDecorator(new PlainText("x"), "彩虹色");
        } catch (IllegalArgumentException e) {
            System.out.println("非法颜色已拦截   : " + e.getMessage());
        }
        System.out.println();
    }

    /**
     * 演示 5：无论套了多少层，对外的类型始终是 TextComponent。
     *
     * <p>这意味着装饰后的对象可以<b>无缝传给任何接收 TextComponent 的方法</b>，
     * 接收方完全不需要知道它被装饰过——这就是"不改变接口，只增强功能"。
     */
    private static void demo5UniformType() {
        System.out.println("=== 5. 装饰前后类型不变，可无缝传递 ===");

        TextComponent plain = new PlainText("纯文本");
        TextComponent fancy = new UnderlineDecorator(
                new ItalicDecorator(
                        new BoldDecorator(
                                new ColorDecorator(new PlainText("花哨文本"), "purple"))));

        // 同一个方法，接收"裸"对象和"套了 4 层"的对象，代码完全一样
        publish(plain);
        publish(fancy);

        System.out.println("plain 实际类型 : " + plain.getClass().getSimpleName());
        System.out.println("fancy 实际类型 : " + fancy.getClass().getSimpleName());
        System.out.println("但静态类型都是 TextComponent，所以 publish() 不需要重载");
        System.out.println();
    }

    /**
     * 模拟一个"发布"业务方法：它只依赖 TextComponent 抽象。
     */
    private static void publish(TextComponent text) {
        System.out.println("  [发布] " + text.render());
    }
}
