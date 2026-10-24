package com.patterns.structural.decorator;

/**
 * 装饰器基类（Decorator）：持有一个被装饰对象，并<b>默认原样转发</b>请求。
 *
 * <p><b>为什么需要这个中间抽象类，不让每个装饰器直接实现 TextComponent？</b>
 * 三个原因：
 * <ol>
 *   <li><b>消除重复</b>：所有装饰器都要"持有 component + 构造器赋值"，
 *       抽到父类只写一次。4 个装饰器就省了 4 份同样的字段与构造器。</li>
 *   <li><b>提供默认转发实现</b>：本类的 {@link #render()} 直接委托给 component。
 *       子类如果只想在<b>渲染前后加料</b>，调用 {@code super.render()} 即可；
 *       不想改行为的装饰器甚至可以完全不覆盖 render()。</li>
 *   <li><b>类型约束</b>：让"装饰器"成为一个明确的概念，
 *       而不是散落在各处的、恰好实现了同一接口的类。</li>
 * </ol>
 *
 * <p><b>相比原项目改了什么？</b>
 * <ul>
 *   <li>字段由 {@code protected TextComponent component} 改为
 *       {@code protected final}——装饰关系一旦建立就不该被中途替换，
 *       final 既表达设计意图，也让 JVM 有更明确的不可变假设。</li>
 *   <li>新增了 {@link #render()} 的默认转发实现。原实现中 TextDecorator 没有
 *       任何方法体，导致每个子类都必须自己写 {@code component.render()}，
 *       一旦漏写就变成"忘记委托给内层"，装饰链断裂。</li>
 *   <li>构造器增加 null 校验，把 NPE 从渲染期提前到构造期。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public abstract class TextDecorator implements TextComponent {

    /**
     * 被装饰的对象。声明类型为 {@link TextComponent}（抽象组件），
     * 因此它可以是 PlainText，也可以是<b>另一个装饰器</b>——
     * 这正是装饰链能够层层嵌套的关键。
     */
    protected final TextComponent component;

    /**
     * @param component 被装饰对象，不能为 null
     */
    protected TextDecorator(TextComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("被装饰对象不能为 null");
        }
        this.component = component;
    }

    /**
     * 默认行为：原样委托给被装饰对象，不加任何修饰。
     *
     * <p>子类通常覆盖本方法，形如：
     * <pre>{@code
     * @Override
     * public String render() {
     *     return "<b>" + super.render() + "</b>";   // 在内层结果外面加料
     * }
     * }</pre>
     */
    @Override
    public String render() {
        return component.render();
    }
}
