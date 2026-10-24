package com.patterns.structural.facade;

import com.patterns.structural.facade.model.User;
import com.patterns.structural.facade.subsystem.AuditLogger;
import com.patterns.structural.facade.subsystem.EmailNotifier;
import com.patterns.structural.facade.subsystem.InMemoryUserRepository;
import com.patterns.structural.facade.subsystem.UserRepository;
import com.patterns.structural.facade.subsystem.UserValidator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 外观（Facade）：把 4 个子系统的复杂协作，封装成几个业务语义清晰的方法。
 *
 * <p><b>外观模式的本质是"降低客户端与子系统之间的耦合度"。</b>
 * 以 {@link #register} 为例，客户端原本要亲自完成 6 步：
 * <ol>
 *   <li>调 Validator 校验用户名</li>
 *   <li>调 Validator 校验邮箱</li>
 *   <li>调 Repository 查用户名是否重复</li>
 *   <li>调 Repository 保存</li>
 *   <li>调 AuditLogger 记审计</li>
 *   <li>调 EmailNotifier 发欢迎邮件</li>
 * </ol>
 * 有了外观，客户端只需一句 {@code facade.register(1, "鱼皮", "yupi@x.com")}。
 *
 * <p><b>相比原项目改了什么？为什么？</b>
 * <ul>
 *   <li><b>原项目的外观只包了一个 UserDAO</b>，注册流程只有"new User + insert"两步。
 *       这种情况下外观的价值几乎为零——客户端直接调 DAO 也就两行代码，
 *       反而多了一层转发。这会让学习者误以为"外观 = 给 DAO 套个壳"。
 *       本项目补齐了 Validator / AuditLogger / EmailNotifier 三个子系统，
 *       让"一次业务操作 = 多次子系统协作"这个前提真正成立，外观的价值才看得见。</li>
 *   <li><b>子系统通过构造器注入</b>，而不是原项目在构造器里 {@code new UserDAOImpl()}。
 *       硬编码 new 会让外观无法替换子系统实现（测试时想换成 Mock 存储就得改源码），
 *       违反依赖倒置原则。这里保留一个无参构造器做默认装配，方便 Demo 直接使用。</li>
 *   <li><b>方法返回 Optional / boolean</b>，让调用方能明确知道操作是否成功，
 *       而不是像原项目那样只能靠看控制台输出猜结果。</li>
 *   <li><b>外观不吞掉子系统</b>：{@link #getAuditRecords()} 暴露了只读的审计记录，
 *       体现"外观是入口，不是牢笼"——高级用户仍可绕过外观直接用子系统（见 README §8）。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public class UserServiceFacade {

    private final UserRepository repository;
    private final UserValidator validator;
    private final AuditLogger auditLogger;
    private final EmailNotifier emailNotifier;

    /**
     * 默认装配：使用内存存储。适合 Demo 与快速启动。
     */
    public UserServiceFacade() {
        this(new InMemoryUserRepository(), new UserValidator(), new AuditLogger(), new EmailNotifier());
    }

    /**
     * 依赖注入装配：便于单元测试替换子系统实现。
     *
     * @param repository    存储子系统
     * @param validator     校验子系统
     * @param auditLogger   审计子系统
     * @param emailNotifier 通知子系统
     */
    public UserServiceFacade(UserRepository repository,
                             UserValidator validator,
                             AuditLogger auditLogger,
                             EmailNotifier emailNotifier) {
        this.repository = repository;
        this.validator = validator;
        this.auditLogger = auditLogger;
        this.emailNotifier = emailNotifier;
    }

    /**
     * 注册用户。<b>这一个方法内部编排了 4 个子系统、6 个步骤。</b>
     *
     * @param id       用户 ID
     * @param username 用户名
     * @param email    邮箱
     * @return 注册成功返回新用户；任一步失败返回 {@link Optional#empty()}（原因已打印）
     */
    public Optional<User> register(int id, String username, String email) {
        System.out.println("  >>> 注册流程开始：id=" + id + ", username=" + username);

        // 步骤 1-2：参数校验（校验子系统）
        Optional<String> usernameError = validator.validateUsername(username);
        if (usernameError.isPresent()) {
            return fail("REGISTER", usernameError.get());
        }
        Optional<String> emailError = validator.validateEmail(email);
        if (emailError.isPresent()) {
            return fail("REGISTER", emailError.get());
        }

        // 步骤 3：唯一性检查（存储子系统）
        if (repository.findByUsername(username).isPresent()) {
            return fail("REGISTER", "用户名已存在：" + username);
        }

        // 步骤 4：落库（存储子系统）
        User user = new User(id, username, email, LocalDateTime.now());
        repository.save(user);

        // 步骤 5：审计（审计子系统）
        auditLogger.log("REGISTER", "新用户注册 id=" + id + ", username=" + username);

        // 步骤 6：发送欢迎邮件（通知子系统）
        emailNotifier.send(email, "欢迎加入", "你好 " + username + "，注册成功！");

        System.out.println("  [OK] 注册流程结束：成功");
        return Optional.of(user);
    }

    /**
     * 修改邮箱。演示"外观方法可以有各自不同的子系统编排顺序"。
     *
     * @param id       用户 ID
     * @param newEmail 新邮箱
     * @return 成功返回更新后的用户；失败返回 empty
     */
    public Optional<User> changeEmail(int id, String newEmail) {
        System.out.println("  >>> 改邮箱流程开始：id=" + id + ", newEmail=" + newEmail);

        Optional<String> emailError = validator.validateEmail(newEmail);
        if (emailError.isPresent()) {
            return fail("CHANGE_EMAIL", emailError.get());
        }

        Optional<User> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return fail("CHANGE_EMAIL", "用户不存在：id=" + id);
        }

        User old = existing.get();
        User updated = old.withEmail(newEmail);   // 不可变更新：返回新对象
        repository.save(updated);
        auditLogger.log("CHANGE_EMAIL",
                "id=" + id + " 邮箱 " + old.email() + " → " + newEmail);
        emailNotifier.send(newEmail, "邮箱变更通知", "你的登录邮箱已变更为 " + newEmail);

        System.out.println("  [OK] 改邮箱流程结束：成功");
        return Optional.of(updated);
    }

    /**
     * 注销用户。
     *
     * @param id 用户 ID
     * @return 是否注销成功
     */
    public boolean unregister(int id) {
        System.out.println("  >>> 注销流程开始：id=" + id);

        Optional<User> existing = repository.findById(id);
        if (existing.isEmpty()) {
            fail("UNREGISTER", "用户不存在：id=" + id);
            return false;
        }

        User user = existing.get();
        boolean deleted = repository.deleteById(id);
        if (deleted) {
            auditLogger.log("UNREGISTER", "用户注销 id=" + id + ", username=" + user.username());
            emailNotifier.send(user.email(), "账号已注销",
                    "再见 " + user.username() + "，你的账号已注销。");
        }

        System.out.println("  [OK] 注销流程结束：" + (deleted ? "成功" : "失败"));
        return deleted;
    }

    /**
     * 查询单个用户。演示外观也可以只做"轻量转发"——
     * 并非每个外观方法都必须编排多个子系统，简单查询直接委托即可。
     *
     * @param id 用户 ID
     * @return 用户，不存在返回 empty
     */
    public Optional<User> findUser(int id) {
        return repository.findById(id);
    }

    /**
     * @return 全部用户
     */
    public List<User> listUsers() {
        return repository.findAll();
    }

    /**
     * @return 只读审计记录
     */
    public List<String> getAuditRecords() {
        return auditLogger.getRecords();
    }

    /**
     * @return 已发送邮件数
     */
    public int getSentEmailCount() {
        return emailNotifier.getSentCount();
    }

    /**
     * 统一的失败处理：记审计 + 打印原因。
     *
     * <p>把"失败时该做什么"收敛到一个私有方法，
     * 避免每个业务方法里都写一遍 {@code auditLogger.log(...); System.out.println(...);}。
     */
    private Optional<User> fail(String action, String reason) {
        auditLogger.log(action + "_FAIL", reason);
        System.out.println("  [FAIL] 流程终止：" + reason);
        return Optional.empty();
    }
}
