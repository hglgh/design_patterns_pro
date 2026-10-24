package com.patterns.structural.facade.model;

import java.time.LocalDateTime;

/**
 * 领域模型：用户。
 *
 * <p><b>为什么用 record？</b>
 * User 是不可变的数据载体。特别是 {@link #withEmail} 方法体现了
 * "修改属性 = 返回新对象"的不可变风格，避免各子系统之间因为共享可变对象而互相干扰。
 *
 * @param id        用户 ID
 * @param username  用户名
 * @param email     邮箱
 * @param createdAt 注册时间
 * @author design-patterns-pro
 */
public record User(int id, String username, String email, LocalDateTime createdAt) {

    /**
     * 返回一个只替换了邮箱的新 User，原对象保持不变。
     *
     * @param newEmail 新邮箱
     * @return 新的 User 实例
     */
    public User withEmail(String newEmail) {
        return new User(id, username, newEmail, createdAt);
    }
}
