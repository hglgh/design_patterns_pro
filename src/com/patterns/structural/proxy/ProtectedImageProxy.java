package com.patterns.structural.proxy;

import java.util.Set;

/**
 * 保护代理（Protection Proxy）：控制"<b>谁</b>能访问真实对象"。
 *
 * <p><b>为什么权限校验要用代理，而不是写在 RealImage 里？</b>
 * 如果写在 RealImage.display() 里：
 * <ul>
 *   <li><b>职责混乱</b>：RealImage 的职责是"加载并渲染图片"，它不该知道
 *       什么是"角色"、什么是"权限"。加进去就违反单一职责原则。</li>
 *   <li><b>无法复用</b>：另一个不需要鉴权的内部批处理任务想直接用 RealImage，
 *       会被权限检查拦住，只能再加个 {@code skipAuth} 布尔参数——越改越脏。</li>
 *   <li><b>顺序错误</b>：鉴权必须在"加载图片"之前，但 RealImage 的加载发生在构造器里。
 *       等到能调 display() 时，图片早就加载完了，鉴权失去意义（内存已经消耗）。</li>
 * </ul>
 *
 * <p>用保护代理后：鉴权在<b>加载之前</b>发生，未授权的用户连图片都不会被加载。
 * RealImage 保持纯粹，可以随意复用。
 *
 * <p><b>典型应用</b>：Spring Security 的方法级鉴权（{@code @PreAuthorize}）、
 * Java RMI 的远程访问控制、Spring AOP 的 {@code @Transactional} 权限检查。
 *
 * @author design-patterns-pro
 */
public class ProtectedImageProxy implements Image {

    /** 允许查看图片的角色白名单。 */
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "EDITOR", "VIEWER");

    private final Image target;
    private final String currentUserRole;

    /**
     * @param target           被保护的对象（可以是 RealImage，也可以是另一个代理）
     * @param currentUserRole  当前登录用户的角色
     */
    public ProtectedImageProxy(Image target, String currentUserRole) {
        this.target = target;
        this.currentUserRole = currentUserRole;
    }

    @Override
    public void display() {
        if (!ALLOWED_ROLES.contains(currentUserRole)) {
            // 鉴权失败：直接拒绝，target.display() 根本不会被调用
            // 若 target 是 VirtualImageProxy，图片连加载都不会发生
            System.out.println("      [ProtectedProxy] × 拒绝访问：角色 '"
                    + currentUserRole + "' 无权查看 " + target.getFilename());
            return;
        }
        System.out.println("      [ProtectedProxy] √ 鉴权通过（角色 " + currentUserRole + "），放行");
        target.display();
    }

    /**
     * 文件名不涉及敏感内容，无需鉴权即可返回——直接委托给 target。
     *
     * <p><b>这体现了一个重要原则</b>：保护代理只需保护"敏感方法"，
     * 其他方法原样转发即可。不需要给每个方法都加一遍权限检查。
     */
    @Override
    public String getFilename() {
        return target.getFilename();
    }
}
