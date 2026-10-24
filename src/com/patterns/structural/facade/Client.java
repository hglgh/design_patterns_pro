package com.patterns.structural.facade;

import com.patterns.structural.facade.model.User;
import com.patterns.structural.facade.subsystem.AuditLogger;
import com.patterns.structural.facade.subsystem.EmailNotifier;
import com.patterns.structural.facade.subsystem.InMemoryUserRepository;
import com.patterns.structural.facade.subsystem.UserRepository;
import com.patterns.structural.facade.subsystem.UserValidator;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 客户端：对比"用外观"与"不用外观"两种写法的差异。
 *
 * <p>运行方式：
 * <pre>
 *   java -cp out com.patterns.structural.facade.Client
 * </pre>
 *
 * @author design-patterns-pro
 */
public class Client {

    public static void main(String[] args) {
        demoWithFacade();
        System.out.println();
        demoWithoutFacade();
        System.out.println();
        demoFailureAndStatistics();
    }

    // ================================================================
    // 演示 1：使用外观——客户端只看到一个简单的业务方法
    // ================================================================
    private static void demoWithFacade() {
        System.out.println("=== 1. 使用外观：客户端代码只有 1 行 ===");

        UserServiceFacade facade = new UserServiceFacade();
        facade.register(1, "鱼皮", "yupi@example.com");

        System.out.println();
        System.out.println("  客户端后续操作同样简单：");
        facade.findUser(1).ifPresent(u ->
                System.out.println("      查询到用户：" + u.username() + " / " + u.email()));
        facade.changeEmail(1, "yupi_new@example.com");
        facade.unregister(1);
        facade.findUser(1).ifPresentOrElse(
                u -> System.out.println("      仍存在：" + u),
                () -> System.out.println("      确认已注销，查询返回 empty"));
    }

    // ================================================================
    // 演示 2：不使用外观——客户端必须亲自认识并编排所有子系统
    // ================================================================
    private static void demoWithoutFacade() {
        System.out.println("=== 2. 不使用外观：同样的注册，客户端要写这么多 ===");

        // 客户端必须自己 new 出全部 4 个子系统，并知道它们的调用顺序
        UserRepository repository = new InMemoryUserRepository();
        UserValidator validator = new UserValidator();
        AuditLogger auditLogger = new AuditLogger();
        EmailNotifier emailNotifier = new EmailNotifier();

        int id = 2;
        String username = "程序员";
        String email = "dev@example.com";

        // 步骤 1：校验用户名
        Optional<String> usernameError = validator.validateUsername(username);
        if (usernameError.isPresent()) {
            System.out.println("      注册失败：" + usernameError.get());
            return;
        }
        // 步骤 2：校验邮箱
        Optional<String> emailError = validator.validateEmail(email);
        if (emailError.isPresent()) {
            System.out.println("      注册失败：" + emailError.get());
            return;
        }
        // 步骤 3：查重
        if (repository.findByUsername(username).isPresent()) {
            System.out.println("      注册失败：用户名已存在");
            return;
        }
        // 步骤 4：落库
        User user = new User(id, username, email, LocalDateTime.now());
        repository.save(user);
        // 步骤 5：审计
        auditLogger.log("REGISTER", "新用户注册 id=" + id);
        // 步骤 6：通知
        emailNotifier.send(email, "欢迎加入", "你好 " + username);

        System.out.println();
        System.out.println("  ↑ 上面这一大段就是外观替你隐藏掉的复杂度。");
        System.out.println("    客户端被迫依赖了 4 个类型、6 个方法调用、3 个 if 分支。");
        System.out.println("    如果注册流程新增一步'发送短信验证码'，");
        System.out.println("    用外观：只改 UserServiceFacade 一处；不用外观：每个调用点都要改。");
    }

    // ================================================================
    // 演示 3：失败场景与统计信息
    // ================================================================
    private static void demoFailureAndStatistics() {
        System.out.println("=== 3. 失败场景 & 外观汇总的统计信息 ===");

        UserServiceFacade facade = new UserServiceFacade();
        facade.register(10, "张三", "zhangsan@example.com");

        System.out.println();
        System.out.println("  场景 A：用户名太短，被校验子系统拦下");
        facade.register(11, "李", "li@example.com");

        System.out.println();
        System.out.println("  场景 B：邮箱格式错误，被校验子系统拦下");
        facade.register(12, "王五", "not-an-email");

        System.out.println();
        System.out.println("  场景 C：用户名重复，被存储子系统查重拦下");
        facade.register(13, "张三", "another@example.com");

        System.out.println();
        System.out.println("  当前用户列表：");
        facade.listUsers().forEach(u ->
                System.out.println("      - id=" + u.id() + ", " + u.username() + ", " + u.email()));

        System.out.println();
        System.out.println("  累计发送邮件：" + facade.getSentEmailCount() + " 封");
        System.out.println("  审计记录共 " + facade.getAuditRecords().size() + " 条：");
        facade.getAuditRecords().forEach(r -> System.out.println("      " + r));
    }
}
