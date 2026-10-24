package com.patterns.structural.facade.subsystem;

import java.util.Optional;

/**
 * 子系统 2：参数校验。集中管理"用户名长度""邮箱格式"等业务规则。
 *
 * <p><b>为什么单独拆成一个子系统？</b>
 * 校验规则会随业务不断膨胀（用户名敏感词、邮箱域名白名单、手机号归属地…）。
 * 如果写在外观里，外观会变成一个上千行的上帝类，
 * 违背了"外观只做流程编排，不做具体业务"的原则。
 *
 * <p><b>为什么返回 {@code Optional<String>} 而不是 boolean？</b>
 * 校验失败时用户需要知道"为什么失败"。用 Optional 承载错误描述：
 * {@code isEmpty()} 表示通过，{@code get()} 拿到失败原因。
 * 这样一次调用同时给出结论和原因，且比"返回 null 表示成功"更不容易误用
 * （返回 null 的设计会让调用方写出 {@code if (msg != null)} 这种语义颠倒的判断）。
 *
 * @author design-patterns-pro
 */
public class UserValidator {

    private static final String EMAIL_REGEX = "^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$";

    /**
     * 校验用户名。
     *
     * @param username 用户名
     * @return {@link Optional#empty()} 表示通过；否则装着错误原因
     */
    public Optional<String> validateUsername(String username) {
        System.out.println("      [Validator] 校验用户名：" + username);
        if (username == null || username.isBlank()) {
            return Optional.of("用户名不能为空");
        }
        if (username.length() < 2 || username.length() > 20) {
            return Optional.of("用户名长度必须在 2~20 之间，当前为 " + username.length());
        }
        return Optional.empty();
    }

    /**
     * 校验邮箱格式。
     *
     * @param email 邮箱
     * @return {@link Optional#empty()} 表示通过；否则装着错误原因
     */
    public Optional<String> validateEmail(String email) {
        System.out.println("      [Validator] 校验邮箱：" + email);
        if (email == null || email.isBlank()) {
            return Optional.of("邮箱不能为空");
        }
        if (!email.matches(EMAIL_REGEX)) {
            return Optional.of("邮箱格式不正确：" + email);
        }
        return Optional.empty();
    }
}
