package com.patterns.creational.abstractfactory.beginner;

/**
 * 抽象工厂入门演示：一套 UI 主题里的按钮和复选框必须来自同一个主题。
 *
 * <p>改造前：客户端分别按字符串创建按钮和复选框，很容易把亮色按钮和暗色复选框拼在一起；
 * 改造后：一个主题工厂同时负责创建这一族产品，客户端只拿一个工厂，混搭在结构上就做不到。
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
        System.out.println("========== 改造前：按字符串分别创建 ==========");

        OldUiFactory factory = new OldUiFactory();
        OldButton button = factory.createButton("light");
        OldCheckbox checkbox = factory.createCheckbox("dark");   // 手滑写成 dark

        button.render();
        checkbox.render();

        System.out.println("  ↑ 编译通过、运行不报错，但一个界面出现了两套主题。");
        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 「按钮用亮色、复选框用暗色」这种错误，只能靠人眼发现；");
        System.out.println("  2. 新增蓝色主题，两个创建方法都要改；");
        System.out.println("  3. 新增文本框，也要改这个工厂和所有调用点。");
    }

    static class OldButton {
        private final String theme;

        OldButton(String theme) {
            this.theme = theme;
        }

        void render() {
            System.out.println("  [按钮] " + theme + " 主题");
        }
    }

    static class OldCheckbox {
        private final String theme;

        OldCheckbox(String theme) {
            this.theme = theme;
        }

        void render() {
            System.out.println("  [复选框] " + theme + " 主题");
        }
    }

    static class OldUiFactory {

        OldButton createButton(String theme) {
            return new OldButton(theme);
        }

        OldCheckbox createCheckbox(String theme) {
            return new OldCheckbox(theme);
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：一个工厂负责一族产品 ==========");

        renderLogin(new LightThemeFactory());
        System.out.println("  —— 换主题：只换一个工厂对象，renderLogin 不改 ——");
        renderLogin(new DarkThemeFactory());

        System.out.println();
        System.out.println("  —— 新增蓝色主题：加 3 个类，renderLogin 不改 ——");
        renderLogin(new BlueThemeFactory());

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 现在想给所有主题加一个「文本框」，要改 ThemeFactory 和 3 个工厂；");
        System.out.println("     「加一族产品容易，加一个产品难」正是抽象工厂的边界。");
        System.out.println("  2. 主题只有一套、产品只有两种时，直接 new 更省事。");
    }

    /** 客户端只收一个工厂，按钮和复选框必然来自同一族。 */
    private static void renderLogin(ThemeFactory factory) {
        Button button = factory.createButton();
        Checkbox checkbox = factory.createCheckbox();
        button.render();
        checkbox.render();
    }

    interface Button {
        void render();
    }

    interface Checkbox {
        void render();
    }

    /** 抽象工厂：声明「这一族产品」分别怎么创建。 */
    interface ThemeFactory {
        Button createButton();

        Checkbox createCheckbox();
    }

    static class LightButton implements Button {
        @Override
        public void render() {
            System.out.println("  [按钮] 亮色主题");
        }
    }

    static class LightCheckbox implements Checkbox {
        @Override
        public void render() {
            System.out.println("  [复选框] 亮色主题");
        }
    }

    static class LightThemeFactory implements ThemeFactory {
        @Override
        public Button createButton() {
            return new LightButton();
        }

        @Override
        public Checkbox createCheckbox() {
            return new LightCheckbox();
        }
    }

    static class DarkButton implements Button {
        @Override
        public void render() {
            System.out.println("  [按钮] 暗色主题");
        }
    }

    static class DarkCheckbox implements Checkbox {
        @Override
        public void render() {
            System.out.println("  [复选框] 暗色主题");
        }
    }

    static class DarkThemeFactory implements ThemeFactory {
        @Override
        public Button createButton() {
            return new DarkButton();
        }

        @Override
        public Checkbox createCheckbox() {
            return new DarkCheckbox();
        }
    }

    // ---- 新主题：只新增下面三个类 ----

    static class BlueButton implements Button {
        @Override
        public void render() {
            System.out.println("  [按钮] 蓝色主题");
        }
    }

    static class BlueCheckbox implements Checkbox {
        @Override
        public void render() {
            System.out.println("  [复选框] 蓝色主题");
        }
    }

    static class BlueThemeFactory implements ThemeFactory {
        @Override
        public Button createButton() {
            return new BlueButton();
        }

        @Override
        public Checkbox createCheckbox() {
            return new BlueCheckbox();
        }
    }
}
