package com.patterns.structural.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * JDK 动态代理的调用处理器：用<b>一份代码</b>为 Image 接口的所有方法织入
 * 日志、耗时统计与权限检查。
 *
 * <p><b>为什么需要动态代理？静态代理有什么问题？</b>
 * 看本包的三个静态代理：VirtualImageProxy、ProtectedImageProxy、CachingImageProxy。
 * 它们各自只做了<b>一件</b>横切的事，而且每个方法都要手写一遍委托：
 * <pre>
 *   public void display()      { /* 加料 *\/ target.display(); }
 *   public String getFilename(){ /* 加料 *\/ return target.getFilename(); }
 * </pre>
 * 如果 {@code Image} 接口有 20 个方法，每个静态代理就要写 20 个转发方法。
 * 再如果有 100 个接口都需要"日志 + 计时"，就要写 100 × 20 = 2000 个方法。
 *
 * <p>动态代理用反射在<b>运行时生成</b>代理类，一份 InvocationHandler 通吃所有接口所有方法。
 * <b>这就是 Spring AOP 的底层原理。</b>
 *
 * <p><b>JDK 动态代理的硬性限制</b>：只能代理<b>接口</b>。
 * 因为生成的代理类是 {@code class $Proxy0 extends java.lang.reflect.Proxy implements Image}，
 * Java 单继承已经用掉了 extends Proxy 这个名额，无法再 extends RealImage。
 * 要代理没有接口的类，只能用 CGLIB / ByteBuddy 生成目标类的<b>子类</b>
 * （因此目标类和方法都不能是 final）。
 *
 * @author design-patterns-pro
 * @see DynamicImageProxy 用 Proxy.newProxyInstance 生成代理的工厂
 */
public class ImageInvocationHandler implements InvocationHandler {

    /** 需要鉴权的方法名。其他方法（如 getFilename）直接放行。 */
    private static final Set<String> PROTECTED_METHODS = Set.of("display");

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "EDITOR", "VIEWER");

    /** 被代理的真实对象。声明为 Object，所以这个 handler 可以代理任何接口。 */
    private final Object target;

    /** 模拟的当前用户角色，用于演示权限拦截。 */
    private final String role;

    /**
     * @param target 被代理对象
     * @param role   当前用户角色
     */
    public ImageInvocationHandler(Object target, String role) {
        this.target = target;
        this.role = role;
    }

    /**
     * 所有对代理对象的方法调用都会被路由到这里。
     *
     * @param proxy  代理对象本身。<b>注意：绝大多数情况不该用它</b>，
     *               若写成 {@code method.invoke(proxy, args)} 会造成无限递归 StackOverflowError。
     * @param method 被调用的方法的反射对象
     * @param args   调用参数
     * @return 目标方法的返回值
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // ── 前置增强 1：日志 ──────────────────────────────
        // 注意：无参方法传进来的 args 是 null（不是空数组），直接 Arrays.toString(null)
        // 会打印出字面量 "null"，容易让人误以为传了一个 null 参数。
        System.out.println("      [DynamicProxy] → 调用 " + methodName
                + "，参数 " + (args == null ? "[]" : Arrays.toString(args))
                + "，角色 " + role);

        // ── 前置增强 2：权限检查（只对敏感方法）──────────
        if (PROTECTED_METHODS.contains(methodName) && !ALLOWED_ROLES.contains(role)) {
            System.out.println("      [DynamicProxy] × 权限不足，拦截 " + methodName);
            // 返回类型是 void 时返回 null；若是 boolean 应返回 false，否则调用方拆箱会 NPE
            return defaultValue(method.getReturnType());
        }

        // ── 环绕增强：计时 ────────────────────────────────
        long start = System.nanoTime();
        try {
            Object result = method.invoke(target, args);   // ← 反射调用真实对象
            return result;
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 目标方法抛出的真实异常被包在 InvocationTargetException 里，
            // 必须解包再抛，否则调用方看到的异常类型是错的
            System.out.println("      [DynamicProxy] ! 目标方法抛异常");
            throw e.getCause();
        } finally {
            long cost = (System.nanoTime() - start) / 1000;
            System.out.println("      [DynamicProxy] ← " + methodName + " 返回，耗时 " + cost + " μs");
        }
    }

    /**
     * 为基本返回类型提供合理的默认值，避免自动拆箱时的 NullPointerException。
     *
     * <p>这是动态代理的一个经典坑：如果被拦截的方法返回 {@code boolean}，
     * handler 返回 null，调用方 {@code if (image.isValid())} 会直接 NPE。
     */
    private Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) return false;
        if (returnType == void.class)    return null;
        if (returnType == char.class)    return '\0';
        if (returnType == byte.class)    return (byte) 0;
        if (returnType == short.class)   return (short) 0;
        if (returnType == int.class)     return 0;
        if (returnType == long.class)    return 0L;
        if (returnType == float.class)   return 0f;
        if (returnType == double.class)  return 0d;
        return null;
    }
}
