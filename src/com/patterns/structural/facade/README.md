# 外观模式（Facade Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：给一团复杂的子系统配一个"总开关"，客户端按一下就行，不用知道里面有几个零件、按什么顺序转。

| 项目 | 内容 |
|---|---|
| 分类 | 结构型模式 |
| 包路径 | `com.patterns.structural.facade` |
| 运行入口 | `Client.main()` |
| 核心角色 | `UserServiceFacade`（外观）+ 4 个子系统（`UserValidator` / `UserRepository` / `AuditLogger` / `EmailNotifier`） |
| 一句话记忆 | **"一键开机：你只按一个键，主板/硬盘/风扇自己忙去"** |

---

## 0. 先看结论

| 问题 | 结论 |
|---|---|
| 外观模式解决什么 | 把"多个子系统的固定协作流程"收敛成一个业务方法，降低客户端与子系统的耦合 |
| 它**不是**什么 | 不是"给 DAO 套个壳"。如果一个外观方法只转发一次调用，那它毫无价值 |
| 成立的前提 | **一次业务操作要协调 ≥ 2 个子系统**。否则客户端自己调更简单 |
| 关键边界 | 外观只做**流程编排**，不做业务逻辑。校验归 Validator，存储归 Repository |
| 要不要抽接口 | 通常**不抽**。外观是给客户端用的简化入口，多一层接口反而更复杂（§4.3） |
| 会不会挡住子系统 | **不会**。外观是"入口"不是"牢笼"，高级用户仍可直接用子系统（§4.5） |
| 最常见的错误 | 把业务逻辑写进外观，让它膨胀成上帝类（§8.4） |
| 和中介者的分界 | 外观是**单向**的（客户端 → 子系统）；中介者是**多向**的（同事之间通过中介者互相通信） |
| 本项目做了什么 | 把"1 个子系统"扩展到"4 个子系统"，让外观的价值真正成立；补依赖注入、返回值、只读视图 |

### 诚实前置说明

原项目的外观模式**能跑通**（10 行输出 / 272 字节），但它有一个**结构性问题**：

```java
public class UserServiceFacade {
    private final UserDAO userDAO;

    public UserServiceFacade() {
        this.userDAO = new UserDAOImpl();
    }

    public void createUser(int id, String name) {
        User user = new User(id, name);
        userDAO.insert(user);          // ← 只调了 1 个子系统
    }
    // ...
}
```

它只包装了**一个** `UserDAO`。客户端不用外观，自己写也就两行：

```java
UserDAO dao = new UserDAOImpl();
dao.insert(new User(1, "鱼皮"));
```

用了外观是一行，不用是两行 —— **省了一行，多了一层**。这不是外观模式，这是"转发层"。

> 教学 Demo 里这个偏差很常见，而且危害不小：学习者会得出"外观 = 给 DAO 套壳"的错误结论，
> 然后在真实项目里到处套壳，制造大量无价值的转发类。

本项目把子系统补齐到 4 个（校验 / 存储 / 审计 / 通知），让"一次业务操作 = 多次子系统协作"
这个前提真正成立。**外观的价值只有在"协作确实复杂"时才存在。**

---

## 1. 现实案例

### 说人话版

按一下电脑的开机键。你不需要知道：

- 电源要先给主板供电，主板再给 CPU 发复位信号
- BIOS 要自检内存、显卡、硬盘
- 引导程序要从硬盘读分区表、加载内核
- 内核要初始化驱动、挂载文件系统、启动服务

你只做了一件事：**按一下键**。

这个"开机键"就是一个外观（Facade）。它把十几个子系统、几十步协作，封装成了一个动作。
**但注意：开机键并没有让主板变得不可访问**。你仍然可以进 BIOS、可以拆机换内存 ——
外观只是提供了一条"最简单的路"，不是"唯一的路"。

### 三个真实场景

**场景 1：餐厅点菜**

顾客对服务员说"来一份宫保鸡丁"。这一句话背后：服务员下单 → 厨房备料 → 炉灶烹饪 →
传菜员送餐 → 收银记账。顾客不需要认识厨师、不需要知道菜是第几个灶台做的。
**服务员就是外观。**

**场景 2：微服务网关**

前端调 `/api/order/123`，网关在背后做：鉴权 → 限流 → 路由到订单服务 → 聚合用户服务的信息 →
统一格式化响应 → 记录访问日志。前端只看到一个 REST 接口。
**API 网关就是外观**（也常被叫 BFF，Backend for Frontend）。

**场景 3：`javax.faces` / Spring 的 `JdbcTemplate`**

`JdbcTemplate.query(sql, rowMapper)` 内部做了：获取连接 → 创建 `PreparedStatement` →
设置参数 → 执行 → 遍历 `ResultSet` → 映射成对象 → 关闭资源 → 处理异常。
原本需要 20 行样板代码，现在 1 行。**`JdbcTemplate` 就是外观。**

### 专业版

外观模式（Facade Pattern）为子系统中的一组接口提供一个**一致的界面**，
定义一个高层接口，让子系统更容易使用。

GoF 原文对这个模式的定位很克制，只说了两件事：

1. 它知道哪个子系统类负责处理请求
2. 它将客户的请求代理给适当的子系统对象

> **注意**：GoF 原文特意强调 "subsystems do not know about the facade"。
> 子系统**不认识外观** —— 依赖是单向的（外观 → 子系统），不是双向的。
> 如果某个子系统反过来调用了外观，那就不是外观模式，而是中介者模式了。§7.4 会展开。

---

## 2. 原始实现：不用外观会怎样，以及原项目写成了什么样

### 2.1 v0：客户端亲自编排

假设要注册一个用户，需要 6 步：校验用户名、校验邮箱、查重、落库、记审计、发欢迎邮件。

**不用外观**，客户端代码长这样：

```java
// ❌ 反例：客户端必须认识全部 4 个子系统，并且知道正确的调用顺序
UserRepository repository = new InMemoryUserRepository();
UserValidator validator = new UserValidator();
AuditLogger auditLogger = new AuditLogger();
EmailNotifier emailNotifier = new EmailNotifier();

Optional<String> usernameError = validator.validateUsername(username);
if (usernameError.isPresent()) {
    System.out.println("注册失败：" + usernameError.get());
    return;
}
Optional<String> emailError = validator.validateEmail(email);
if (emailError.isPresent()) {
    System.out.println("注册失败：" + emailError.get());
    return;
}
if (repository.findByUsername(username).isPresent()) {
    System.out.println("注册失败：用户名已存在");
    return;
}
User user = new User(id, username, email, LocalDateTime.now());
repository.save(user);
auditLogger.log("REGISTER", "新用户注册 id=" + id);
emailNotifier.send(email, "欢迎加入", "你好 " + username);
```

这段代码在 `Client.demoWithoutFacade()` 里是**真实存在**的，可以运行验证。

会遇到什么问题？

| # | 问题 | 具体表现 |
|---|---|---|
| 1 | **客户端被迫认识太多类型** | 依赖 4 个类 + 1 个 model，导入 5 个 import |
| 2 | **顺序错了就出错** | 先落库后校验，会写入脏数据；先发邮件后落库，用户可能收到邮件但账号不存在 |
| 3 | **每个调用点都要重写** | 注册、批量导入、第三方登录同步 —— 每处都写一遍这 30 行 |
| 4 | **改流程要改所有调用点** | 新增"发送短信验证码"一步，10 个调用点改 10 次 |
| 5 | **失败处理散落** | 每个 `return` 前都要记审计、打日志，漏一处就是审计缺口 |

**根因**：业务流程的**知识**（先校验、再落库、后通知）散落在每个调用方。

### 2.2 v1：原项目的实现 —— 结构对了，但没东西可封装

原项目 `structuraltype.FacadePattern` 的五个文件：

```java
public interface UserDAO {
    void insert(User user);
    User findById(int id);
    void update(User user);
    void delete(int id);
}

public class UserDAOImpl implements UserDAO {
    private final Map<Integer, User> database = new HashMap<>();
    // ... 四个方法的实现，都是 Map 操作 + println
}

public class UserServiceFacade {
    private final UserDAO userDAO;

    public UserServiceFacade() {
        // 实际开发中可注入
        this.userDAO = new UserDAOImpl();
    }

    public void createUser(int id, String name) {
        User user = new User(id, name);
        userDAO.insert(user);
    }

    public void viewUser(int id) {
        User user = userDAO.findById(id);
        if (user != null) {
            System.out.println("用户信息：ID=" + user.id() + "，姓名=" + user.name());
        } else {
            System.out.println("未找到对应的用户");
        }
    }

    public void renameUser(int id, String newName) {
        User user = userDAO.findById(id);
        if (user != null) {
            User updatedUser = new User(id, newName);
            userDAO.update(updatedUser);
        } else {
            System.out.println("无法重命名，用户不存在");
        }
    }

    public void removeUser(int id) {
        userDAO.delete(id);
    }
}
```

客户端：

```java
UserServiceFacade userService = new UserServiceFacade();
userService.createUser(1, "鱼皮");
userService.viewUser(1);
userService.renameUser(1, "Yes");
userService.viewUser(1);
userService.removeUser(1);
userService.viewUser(1);
```

实测输出（10 行 / 272 字节）：

```text
插入用户：鱼皮
根据 ID 查询用户：1
用户信息：ID=1，姓名=鱼皮
根据 ID 查询用户：1
更新用户：Yes
根据 ID 查询用户：1
用户信息：ID=1，姓名=Yes
删除用户：Yes
根据 ID 查询用户：1
未找到对应的用户
```

功能是正确的。问题在于：**每个外观方法都只调用 1 个 DAO 方法，最多 2 个。**

| 外观方法 | 内部调用了几个子系统方法 | 封装价值 |
|---|---|---|
| `createUser` | 1（`insert`） | 等于 0 |
| `viewUser` | 1（`findById`）+ 一个 if | 等于 0 |
| `renameUser` | 2（`findById` + `update`） | 很小 |
| `removeUser` | 1（`delete`） | 等于 0 |

`viewUser` 里的 `if (user != null) println(...)` 是**表现逻辑**，不是**流程编排**。
它放在外观里，说明外观正在承担"渲染"的职责 —— 这是越界。

### 2.3 用探针钉死五个缺陷

探针 `FacadeProbe` 放在原项目的包里，完整输出 31 行：

```text
[事实 1] 外观不校验，空用户名和超长用户名都能注册
插入用户：
根据 ID 查询用户：1
用户信息：ID=1，姓名=
插入用户：这是一个非常非常非常非常非常非常长的用户名超过二十个字符
根据 ID 查询用户：2
用户信息：ID=2，姓名=这是一个非常非常非常非常非常非常长的用户名超过二十个字符

[事实 2] 外观不查重，同名用户能注册无限个
插入用户：鱼皮
插入用户：鱼皮
根据 ID 查询用户：3
用户信息：ID=3，姓名=鱼皮
根据 ID 查询用户：4
用户信息：ID=4，姓名=鱼皮

[事实 3] 同 ID 重复注册是静默覆盖，调用方无从察觉
插入用户：原名
插入用户：后来改的
根据 ID 查询用户：5
用户信息：ID=5，姓名=后来改的

[事实 4] 外观所有方法都返回 void，调用方只能靠控制台输出猜结果
  void createUser(参数个数 2)
  void removeUser(参数个数 1)
  void viewUser(参数个数 1)
  void renameUser(参数个数 2)

[事实 5] 删不存在的用户，外观没有任何反馈
删除用户：未知
  removeUser(999) 调用结束，返回 void，调用方不知道删没删到
```

逐条分析：

**事实 1：没有校验。** 空用户名能注册成功（输出里 `姓名=` 后面什么都没有）。
超长用户名也能注册 —— 如果这个字段对应数据库 `VARCHAR(20)`，插入时会被截断或报错，
而错误发生在**存储层**，排查时已经看不到"是哪个入口传进来的"。

**事实 2：没有查重。** 两个 `鱼皮` 同时存在，id 分别是 3 和 4。
如果业务上"用户名唯一"，那这个系统现在有脏数据了。

**事实 3：静默覆盖。** `createUser(5, "原名")` 再 `createUser(5, "后来改的")`，
第二条把第一条覆盖了 —— 因为 `Map.put` 就是覆盖。调用方完全不知道发生了什么。

**事实 4：全是 `void`。** 四个方法都返回 `void`。调用方想判断"注册成功了吗"，
只能去解析控制台输出。这在 Web 应用里根本不可行（控制台输出不会返回给前端）。

**事实 5：删除失败无反馈。** `removeUser(999)` 删除一个不存在的 id，
`UserDAOImpl.delete()` 打印 `删除用户： 未知`（注意那个双空格 —— 因为三元表达式返回的是 `" 未知"`），
然后 `removeUser` 直接返回。调用方拿到的是 `void`，什么都不知道。

整理成表：

| 编号 | 缺陷 | 性质 | 后果 |
|---|---|---|---|
| P1 | 外观不做参数校验 | 业务规则缺失 | 空值、超长值流进存储层 |
| P2 | 外观不做唯一性检查 | 业务规则缺失 | 同名用户可无限注册 |
| P3 | 同 ID 重复注册静默覆盖 | 静默错误 | 数据被覆盖且无任何提示 |
| P4 | 全部方法返回 `void` | 接口设计缺陷 | 调用方无法判断成败，只能看控制台 |
| P5 | 删除失败无反馈 | 接口设计缺陷 | 调用方不知道是否真的删到了 |
| P6 | 构造器内 `new UserDAOImpl()` | 硬编码依赖 | 无法注入 Mock，单元测试只能测真实实现 |
| P7 | `findById` 返回 `null` | 可空性未表达 | 调用方必须记得判空，漏一处就是 NPE |
| P8 | **只包了 1 个子系统** | **模式误用** | 外观退化成转发层，没有体现"协调多个子系统"的核心价值 |

> P6 原文有一行注释 `// 实际开发中可注入` —— 作者知道这个问题，只是 Demo 里没做。
> 这也说明：**"可注入"不是嘴上说的，得真有那个构造器**。

### 2.4 v2：重构版改了什么

| 缺陷 | 重构版的处理 | 代码位置 |
|---|---|---|
| P1 / P2 / P3 | 抽出 `UserValidator` 子系统，注册前统一校验 + 查重 | `UserValidator` + `UserServiceFacade.register` |
| P4 / P5 | 返回值改为 `Optional<User>` / `boolean` | 全部公开方法 |
| P6 | 提供 4 参构造器做依赖注入，另留无参构造器做默认装配 | `UserServiceFacade` 构造器 |
| P7 | `UserRepository` 全部查询返回 `Optional<User>` | `UserRepository` |
| P8 | **补齐 4 个子系统**：校验、存储、审计、通知 | `subsystem` 包 |
| （新增） | `changeEmail` 演示"不同业务方法有不同的协作顺序" | `UserServiceFacade.changeEmail` |
| （新增） | `fail()` 私有方法统一失败处理 | `UserServiceFacade` |
| （新增） | `getAuditRecords()` / `getSentEmailCount()` 暴露只读摘要 | `UserServiceFacade` |

---

## 3. 设计动机

### 为什么需要外观模式？

| 动机 | 说明 |
|---|---|
| **降低耦合** | 客户端只依赖 1 个外观类，不再依赖 4 个子系统的 5 个类型 |
| **固化流程知识** | "先校验、再查重、再落库、再审计、再通知"这个顺序只写一次 |
| **简化接口** | 客户端看到的是 `register(id, name, email)`，不是 6 个方法调用 |
| **便于演进** | 新增一步只需改外观一处，所有调用点自动获得新流程 |
| **分层清晰** | 外观层、子系统层职责分明，便于团队分工 |

### 最后一条值得展开

假设团队有 5 个人，注册流程涉及校验、存储、审计、通知四块。
**没有外观**时，每个调用方都要理解这四块怎么配合 —— 相当于每个业务开发者都得懂四块细节。

**有外观**时：

- 张三负责 `UserValidator`（校验规则）
- 李四负责 `UserRepository`（存储实现）
- 王五负责 `UserServiceFacade`（流程编排）
- 业务方只调 `facade.register(...)`

**这就是"分层"的实际价值**：它让不同的人可以在不同的抽象层上工作，而不用同时理解全部。

### 什么情况下**不需要**外观？

**① 只有一个子系统，或者一次操作只调一个方法**

这就是原项目的问题。如果一个"外观方法"的内部只有一行 `dao.insert(user)`，
那它不是外观，是**转发层**。转发层会带来三个成本：
- 多一个文件要维护
- 多一层调用栈（调试时多一次跳转）
- 多一个"这个类到底干嘛的"疑问

判断标准：**外观方法内部至少要有 2 个子系统的协作，或者至少 3 个步骤。**
低于这个量级，直接把子系统方法暴露给客户端更合适。

**② 客户端本来就该知道细节**

有些场景下，"知道细节"是需求的一部分。比如：
- **数据库客户端**：`jdbcTemplate.query(sql, mapper)` 是外观，但 DBA 需要能看执行计划、
  能改连接池参数 —— 所以 `JdbcTemplate` 同时暴露了 `getDataSource()`。
- **构建工具**：Maven 的 `mvn package` 是外观，但开发者需要能改 `pom.xml` 里的插件配置。
- **配置中心**：一个 `getConfig(key)` 是外观，但运维需要能看到配置的来源、版本、灰度规则。

**外观应该提供一个"简单的默认路径"，而不是"唯一的路径"**。§4.5 会详细讲这一点。

**③ 子系统本身还在剧烈变化**

如果子系统接口天天改，外观会跟着天天改，反而成了负担。
这种情况先用外观模式会"过早固化" —— 等子系统稳定了再抽。

**④ 客户端数量很少且都是一次性脚本**

如果只有一个客户端、只调用一次，直接写子系统调用更快。
**模式是用来降低"重复成本"和"协作成本"的，没有重复就没有收益。**

---

## 4. 设计推导：为什么是这些角色？

### 4.1 从需求倒推结构

需求：客户端只想调一个方法就完成注册，且不知道内部有几步。

```text
需求：facade.register(id, name, email) 一次搞定
  ↓
必须有一个类专门负责"编排"，把 6 个步骤写在一起
  ↓
【角色 1】外观 UserServiceFacade
  ↓
但这 6 个步骤涉及四种完全不同的能力：校验、存储、审计、通知
把它们都写进外观 → 外观变成上帝类，且无法独立测试
  ↓
【角色 2】子系统：UserValidator / UserRepository / AuditLogger / EmailNotifier
  ↓
外观需要拿到这些子系统的实例 → 谁来创建？
如果外观内部 new → 无法替换实现（测试、切库都改不了源码）
  ↓
【角色 3】依赖注入：构造器接收 4 个子系统
  ↓
客户端嫌麻烦？再提供一个无参构造器做默认装配
```

这个推导的关键在倒数第二步：**外观与子系统的"连接方式"决定了它能否被测试**。

### 4.2 类结构图

```text
                        ┌───────────────────────────────┐
                        │        Client（客户端）        │
                        └───────────────────────────────┘
                                      │ 只依赖 1 个类型
                                      ▼
                        ┌───────────────────────────────┐
                        │      UserServiceFacade        │
                        │        （Facade）             │
                        │───────────────────────────────│
                        │ - repository   : UserRepository│
                        │ - validator    : UserValidator │
                        │ - auditLogger  : AuditLogger   │
                        │ - emailNotifier: EmailNotifier │
                        │───────────────────────────────│
                        │ + register(id,name,email)      │
                        │ + changeEmail(id,newEmail)     │
                        │ + unregister(id) : boolean     │
                        │ + findUser(id)  : Optional     │
                        │ + listUsers()   : List         │
                        │ + getAuditRecords() : List     │
                        │ + getSentEmailCount() : int    │
                        └───────────────────────────────┘
                           │        │         │        │
            ┌──────────────┘        │         │        └──────────────┐
            ▼                       ▼         ▼                       ▼
  ┌──────────────────┐  ┌────────────────┐ ┌──────────────┐ ┌──────────────────┐
  │ «interface»      │  │ UserValidator  │ │ AuditLogger  │ │ EmailNotifier    │
  │ UserRepository   │  │────────────────│ │──────────────│ │──────────────────│
  │──────────────────│ │ + validateName │ │ + log()      │ │ + send()         │
  │ + save()         │ │ + validateEmail│ │ + getRecords │ │ + getSentCount   │
  │ + findById()     │ └────────────────┘ └──────────────┘ └──────────────────┘
  │ + findByUsername │
  │ + deleteById()   │
  │ + findAll()      │
  └──────────────────┘
            △
            │ implements
  ┌──────────────────────────┐
  │ InMemoryUserRepository   │   ← 可替换为 MySQL / Redis / 远程 HTTP
  └──────────────────────────┘

  ★ 依赖方向：Client → Facade → Subsystems（单向）
  ★ 子系统完全不知道 Facade 的存在
```

### 4.3 关键设计决策一：外观**不**抽接口

这是外观模式与策略/适配器最关键的区别。看代码：

```java
// 客户端
UserServiceFacade facade = new UserServiceFacade();
```

**为什么不是 `UserService` 接口 + `UserServiceFacade` 实现？**

因为**外观的职责就是"简化"**。多加一层接口意味着：

```java
// ❌ 过度设计
public interface UserService {
    Optional<User> register(int id, String username, String email);
    // ...
}

public class UserServiceFacade implements UserService {
    // ...
}

// 客户端还得选实现
UserService service = new UserServiceFacade();
```

客户端多做了一次"选实现"的决定，而它根本不关心这个决定 —— 因为它只想"注册一个用户"。

| | 外观抽接口 | 外观不抽接口（本项目） |
|---|---|---|
| 客户端代码 | `UserService s = new UserServiceFacade();` | `UserServiceFacade f = new UserServiceFacade();` |
| 客户端认知负担 | 要理解接口 + 实现 | 只有一个类 |
| 何时需要 | **有多个外观实现**（本地版 / 远程版 / Mock 版） | 只有一个 |
| 典型反例 | 为每个 Service 都抽 `IXxxService`（.NET 早期风格） | — |

**判断标准**：如果确实需要"换一套外观实现"（比如单体版 vs 微服务版，调用方式完全不同），
那才抽接口。否则**直接暴露类**。

> **本项目为什么不抽接口？** 因为两种"实现"的差异不在外观层，而在**子系统层**。
> 想换存储，注入另一个 `UserRepository` 就行（探针 §6.3 事实 1 验证过）。
> 想换通知方式，注入另一个 `EmailNotifier` 就行。**外观本身不需要变。**

### 4.4 关键设计决策二：外观只做编排，不做业务

看 `register` 的实现：

```java
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
```

**注意：这里没有任何一行"业务规则"**。

| 内容 | 在哪 | 为什么不在外观里 |
|---|---|---|
| "用户名 2~20 字符" | `UserValidator` | 规则会变（敏感词、字符集限制），改它不该动流程 |
| "邮箱正则" | `UserValidator` | 同上 |
| "用户名唯一" | `UserRepository.findByUsername` | 唯一性的保证是存储层的职责（数据库唯一索引） |
| "密码怎么加密" | 不在这个 Demo 里 | 属于领域逻辑，应该有自己的子系统 |
| 审计格式 | `AuditLogger` | 换日志格式不该动流程 |
| 邮件模板 | `EmailNotifier` | 换文案不该动流程 |

**外观只回答一个问题："先做什么，后做什么，失败了怎么办。"**

这条边界如果破了，会有什么后果？

```java
// ❌ 反面教材：外观里写业务规则
public Optional<User> register(int id, String username, String email) {
    if (username.length() < 2 || username.length() > 20) {
        return Optional.empty();     // 规则写死在这里
    }
    if (!email.matches("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$")) {
        return Optional.empty();     // 又是规则
    }
    // ...
}
```

三个月后需求变化："VIP 用户的用户名可以到 50 个字符"。你得改外观。
六个月后需求变化："手机号注册不用邮箱"。你还得改外观。
一年后：`UserServiceFacade` 变成 2000 行，没人敢动。

这就是 §8.4 说的"上帝类"。

### 4.5 关键设计决策三：外观是入口，不是牢笼

看这三个方法：

```java
public List<String> getAuditRecords() {
    return auditLogger.getRecords();     // 暴露子系统状态
}

public int getSentEmailCount() {
    return emailNotifier.getSentCount(); // 暴露子系统状态
}

public Optional<User> findUser(int id) {
    return repository.findById(id);      // 轻量转发，不做编排
}
```

`findUser` 只调了一次 `findById` —— 按 §3 的判断标准，它"不够格"做外观方法。
但**它仍然应该存在**，理由：

1. 客户端需要一个"查询用户"的入口，而这个入口的语义是清晰的
2. 如果强制客户端自己 `new InMemoryUserRepository()`，就会出现两套数据源（各存各的 Map）
3. 它让外观的 API 集完整：注册、修改、删除、查询，四件套齐全

**关键区别在于**：`findUser` 是"为了让 API 完整"而存在的转发，
不是"为了假装自己在封装"而存在的转发。

再看 `getAuditRecords()`：

```java
// AuditLogger.java
public List<String> getRecords() {
    return Collections.unmodifiableList(records);
}
```

**为什么外观要暴露审计记录？** 因为它证明了"外观不吞掉子系统"。

一个设计糟糕的外观会是这样：

```java
// ❌ 牢笼式外观：所有子系统都是 private，外界只能通过外观的固定方法访问
public class UserServiceFacade {
    private final AuditLogger auditLogger;   // 没有任何 getter
    // 客户端想看审计记录？对不起，没门
}
```

这样做的后果是：运维想排查问题时，只能去改源码加一个方法。
**外观模式的意图是"提供便利"，不是"限制自由"。**

> **类比**：大楼有正门（外观），但消防通道、货梯、后门也都存在。
> 正门是"最方便的入口"，不是"唯一的入口"。把其他门都封死，是安全管理，不是建筑设计。

### 4.6 关键设计决策四：依赖注入的两个构造器

```java
/**
 * 默认装配：使用内存存储。适合 Demo 与快速启动。
 */
public UserServiceFacade() {
    this(new InMemoryUserRepository(), new UserValidator(), new AuditLogger(), new EmailNotifier());
}

/**
 * 依赖注入装配：便于单元测试替换子系统实现。
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
```

两个构造器，各司其职：

| 构造器 | 谁用 | 好处 |
|---|---|---|
| 无参 | Demo、快速原型、默认场景 | 一行就能用，不用关心 4 个参数 |
| 4 参 | 单元测试、生产装配、切换实现 | 可以注入 Mock / MySQL 版 / 远程版 |

**无参构造器是通过"委托给 4 参构造器"实现的**，而不是各写一份赋值：

```java
this(new InMemoryUserRepository(), new UserValidator(), new AuditLogger(), new EmailNotifier());
```

这一行 `this(...)` 是**构造器链**，保证只有一处赋值逻辑。
如果各写一份，将来加第 5 个子系统时，很容易只改一个构造器、漏掉另一个 —— 然后出现空指针。

探针 `FacadeProProbe` 事实 1 验证了注入的效果：

```text
[事实 1] 构造器注入让外观可被测试：换成假存储，外观代码一行不用改
  >>> 注册流程开始：id=1, username=测试用户
      [Validator] 校验用户名：测试用户
      [Validator] 校验邮箱：t@example.com
      [Audit] 09:15:15  REGISTER     新用户注册 id=1, username=测试用户
      [Email] → t@example.com | 主题：欢迎加入 | 正文：你好 测试用户，注册成功！
  [OK] 注册流程结束：成功
  register 返回值 present = true
  假存储 save 被调用次数 = 1
  假存储收到的用户 = User[id=1, username=测试用户, email=t@example.com, createdAt=2026-10-23T09:15:15.814366200]
```

探针里的 `FakeRepository` 是一个"假存储"，`save()` 只记数不落库。
外观代码**一行没改**，就完成了替换 —— 这就是依赖注入的收益。

> 注意输出里的 `createdAt=2026-10-23T09:15:15.814366200`：这是当前系统时间，
> **每次运行都不同**。这一点在 §6.4 会变成一个重要话题。

### 4.7 关键设计决策五：返回值用 `Optional` / `boolean` 而不是 `void`

原项目四个方法全是 `void`（探针事实 4 实测）。重构版：

| 方法 | 返回类型 | 为什么 |
|---|---|---|
| `register` | `Optional<User>` | 成功要给新用户（含生成的 createdAt），失败要能表达"没有" |
| `changeEmail` | `Optional<User>` | 同上 |
| `unregister` | `boolean` | 只有两种结果，不需要携带数据 |
| `findUser` | `Optional<User>` | 可能不存在 |
| `listUsers` | `List<User>` | 永不为 null，空列表表示没有 |

**为什么 `register` 不返回 `boolean`？**

因为成功时调用方通常需要用户对象：

```java
// boolean 版本：调用方还得再查一次
if (facade.register(1, "鱼皮", "yupi@x.com")) {
    User u = facade.findUser(1).orElseThrow();   // 多一次查询
}

// Optional<User> 版本：一次拿到
facade.register(1, "鱼皮", "yupi@x.com")
       .ifPresent(u -> System.out.println("注册成功：" + u.username()));
```

**为什么 `findUser` 不返回 `null`？**

原项目的 `findById` 返回 `null`（P7）。这要求每个调用点都写 `if (user != null)`，
漏一处就是 NPE。`Optional` 把"可能不存在"写进**方法签名**，编译器会提醒你处理。

> **关于 `Optional` 的一个常见误用**：不要用它做字段类型或方法参数。
> `Optional<User> user` 作为字段，意味着"这个 User 可能不存在" —— 那你为什么要有这个字段？
> `Optional` 的设计意图就是**方法返回值**，只在这一处用。

### 4.8 关键设计决策六：统一的失败处理

```java
private Optional<User> fail(String action, String reason) {
    auditLogger.log(action + "_FAIL", reason);
    System.out.println("  [FAIL] 流程终止：" + reason);
    return Optional.empty();
}
```

这个私有方法做了三件事，每件都很容易被漏掉：

| 动作 | 如果漏掉会怎样 |
|---|---|
| 记审计 | 失败操作没有留痕，出问题无从追溯 |
| 打印原因 | 用户/开发者不知道为什么失败 |
| 返回 `empty` | 调用方拿到 `null` 或错误的对象 |

**为什么要抽成方法而不是复制粘贴？**

`register` 里有 3 处失败路径，`changeEmail` 里有 2 处，`unregister` 里有 1 处。
一共 6 处。如果每处都写三段代码：

```java
// ❌ 重复 6 次
auditLogger.log("REGISTER_FAIL", reason);
System.out.println("  [FAIL] 流程终止：" + reason);
return Optional.empty();
```

那么将来"失败时要发告警邮件"这个需求加进来，就要改 6 处。抽成 `fail()` 后改 1 处。

> 这个模式叫 **"统一出口"**。它和"统一返回格式"是同一个思路 ——
> **凡是每个分支都要做的事，就应该收敛到一处。**

---

## 5. 代码解析：每个类为什么必须存在？

| 类 | 层 | 职责 | 删掉会怎样 |
|---|---|---|---|
| `UserServiceFacade` | 外观层 | 编排 4 个子系统，暴露业务方法 | 客户端回到 §2.1 的 30 行代码 |
| `UserValidator` | 子系统 | 用户名/邮箱的规则校验 | 校验逻辑散落到外观或调用方 |
| `UserRepository` | 子系统接口 | 定义存储能力 | 外观直接依赖 `InMemoryUserRepository`，无法切库 |
| `InMemoryUserRepository` | 子系统实现 | 用 `HashMap` 模拟数据库 | 没有可运行的存储 |
| `AuditLogger` | 子系统 | 记录敏感操作 | 审计缺口 |
| `EmailNotifier` | 子系统 | 发送通知 | 用户收不到反馈 |
| `User` | 领域模型 | 不可变数据载体 | 无法表达用户 |
| `Client` | 客户端 | 对比"用/不用外观" | 无法运行验证 |

### 5.1 外观方法的三段式结构

看 `changeEmail`，它的结构和 `register` 明显不同：

```java
public Optional<User> changeEmail(int id, String newEmail) {
    System.out.println("  >>> 改邮箱流程开始：id=" + id + ", newEmail=" + newEmail);

    // 第一步：校验新邮箱
    Optional<String> emailError = validator.validateEmail(newEmail);
    if (emailError.isPresent()) {
        return fail("CHANGE_EMAIL", emailError.get());
    }

    // 第二步：查用户是否存在
    Optional<User> existing = repository.findById(id);
    if (existing.isEmpty()) {
        return fail("CHANGE_EMAIL", "用户不存在：id=" + id);
    }

    // 第三步：更新 + 审计 + 通知
    User old = existing.get();
    User updated = old.withEmail(newEmail);   // 不可变更新：返回新对象
    repository.save(updated);
    auditLogger.log("CHANGE_EMAIL",
            "id=" + id + " 邮箱 " + old.email() + " → " + newEmail);
    emailNotifier.send(newEmail, "邮箱变更通知", "你的登录邮箱已变更为 " + newEmail);

    System.out.println("  [OK] 改邮箱流程结束：成功");
    return Optional.of(updated);
}
```

对比 `register` 和 `changeEmail` 的**步骤顺序**：

| 步骤 | `register` | `changeEmail` |
|---|---|---|
| 1 | 校验用户名 | 校验邮箱 |
| 2 | 校验邮箱 | 查用户存在 |
| 3 | 查重 | 更新 |
| 4 | 落库 | 审计 |
| 5 | 审计 | 通知 |
| 6 | 通知 | — |

**每个业务方法的编排顺序可以完全不同** —— 这不是缺陷，而是外观的价值：
每个业务有它自己的正确顺序，外观把它们各自固化下来，客户端不用记。

> 如果所有方法都是"校验 → 查 → 改 → 通知"这一个套路，那你需要的不是外观，
> 是一个**模板方法**（把骨架固定，把可变步骤交给子类）。§7.5 会对比。

### 5.2 `withEmail` 为什么返回新对象

```java
// User.java
public record User(int id, String username, String email, LocalDateTime createdAt) {

    public User withEmail(String newEmail) {
        return new User(id, username, newEmail, createdAt);
    }
}
```

`withEmail` **没有**修改当前对象，而是返回一个新 `User`。原因是 record 的字段是 final，
本来也改不了。但更重要的是**设计意图**：

```java
User old = existing.get();
User updated = old.withEmail(newEmail);
repository.save(updated);
auditLogger.log("CHANGE_EMAIL", "id=" + id + " 邮箱 " + old.email() + " → " + newEmail);
//                                            ↑ 这里 old.email() 还是旧值
```

因为 `old` 没有被修改，审计日志能正确记录"从哪个邮箱改到哪个邮箱"。
**如果 `User` 是可变的，`save(updated)` 之后 `old` 也变了，审计日志就会写成"从新邮箱改成新邮箱"** ——
这种 bug 极其难查。

> **不可变对象的价值**：它让"同一份数据在不同时刻的状态"可以共存。
> 这在审计、并发、事件溯源里都是刚需。

### 5.3 `UserValidator` 为什么返回 `Optional<String>` 而不是 `boolean`

```java
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
```

语义读法很特别：

- `Optional.empty()` → 校验**通过**（没有错误）
- `Optional.of(msg)` → 校验**失败**（有一条错误消息）

这个"反直觉"的语义（空 = 好）是**故意的**：

| 方案 | 调用方写法 | 问题 |
|---|---|---|
| `boolean` | `if (!validator.validate(u)) { ... }` | 只知道失败，不知道为什么 |
| `String` 返回 null 表示成功 | `if (msg != null) { ... }` | 语义颠倒，容易写反 |
| `String` 返回消息，成功返回 `""` | `if (!msg.isEmpty()) { ... }` | 空字符串和 null 两种"无" |
| `Optional<String>` | `if (err.isPresent()) { ... }` | 一次拿到结论 + 原因，类型强制处理 |

第三种方案（返回 `""`）在 Java 里特别常见，但它的坑在于：调用方可能写成
`if (msg != null)` —— 然后空字符串被当成"有错误"。**用 `Optional` 就不会有这种歧义。**

### 5.4 `AuditLogger.getRecords()` 返回只读视图

```java
private final List<String> records = new ArrayList<>();

public List<String> getRecords() {
    return Collections.unmodifiableList(records);
}
```

**为什么不直接 `return records`？**

如果直接返回，外部一句 `getRecords().clear()` 就能清空全部审计记录 ——
审计数据被篡改，比没有审计数据更可怕（因为你会以为审计是完整的）。

这和组合模式 `Directory.children()` 用的是同一个手法（`unmodifiableList`），
但**语义不同**：

| | `Directory.children()` | `AuditLogger.getRecords()` |
|---|---|---|
| 返回类型 | 只读视图 | 只读视图 |
| 外部需要看到最新状态吗 | 需要（树会变） | 需要（审计会追加） |
| 所以用视图还是副本 | 视图 | 视图 |

如果换成"每次调用都要一份快照"，就该用 `List.copyOf`（原型模式的 `getImages()` 就是那样）。

探针验证了只读性：

```text
[事实 3] 失败也会写审计，且审计列表是只读视图
  审计条数 = 2
    09:15:15  REGISTER     新用户注册 id=1, username=测试用户
    09:15:15  REGISTER_FAIL 用户名长度必须在 2~20 之间，当前为 1
  往审计列表 add 会抛：java.lang.UnsupportedOperationException
```

### 5.5 `EmailNotifier` 为什么要有 `sentCount`

```java
private int sentCount = 0;

public void send(String to, String subject, String body) {
    sentCount++;
    System.out.println("      [Email] → " + to + " | 主题：" + subject + " | 正文：" + body);
}

public int getSentCount() {
    return sentCount;
}
```

这个计数器在真实业务里叫**度量（metric）**：发了多少封邮件、失败了多少封、平均耗时多少。

它被放在子系统里而不是外观里，理由和 §4.4 一样：
**"发了多少封"是通知子系统自己的状态**，外观只是把它转出来给客户端看
（`facade.getSentEmailCount()`）。

如果放进外观：

```java
// ❌ 外观维护子系统的状态
private int sentEmailCount = 0;
public Optional<User> register(...) {
    // ...
    emailNotifier.send(...);
    sentEmailCount++;          // 外观要知道"发一封就加一"
}
```

那么将来 `EmailNotifier.send()` 内部改成"批量发送"或者"失败重试"，
外观的计数就不对了。**状态应该由持有它的对象自己维护。**

### 5.6 为什么 `UserRepository` 要抽接口，而 `UserValidator` 不抽

这是一个很有意思的不对称。看两个类的定义：

```java
public interface UserRepository { ... }       // 抽了接口
public class InMemoryUserRepository implements UserRepository { ... }

public class UserValidator { ... }            // 没抽接口
public class AuditLogger { ... }              // 没抽接口
public class EmailNotifier { ... }            // 没抽接口
```

**为什么只有 `UserRepository` 抽了接口？**

| 子系统 | 有可能换实现吗 | 抽接口的价值 |
|---|---|---|
| `UserRepository` | **极可能**：内存 → MySQL → Redis → 远程 HTTP | 高。切库是常态 |
| `UserValidator` | 不太可能：规则会变，但"校验"这个行为不会换成别的东西 | 低 |
| `AuditLogger` | 可能：控制台 → 文件 → ELK | 中。但目前只有一个实现 |
| `EmailNotifier` | 可能：邮件 → 短信 → 站内信 | 中。但语义会变，不只是实现变 |

**判断标准**：**如果"换实现"是一个常见且明确的需求，就抽接口。**
否则等到真的需要时再抽（重构成本很低，因为只有一个实现类）。

> 这就是 **YAGNI**（You Aren't Gonna Need It）原则的具体应用。
> 提前抽接口的代价是：多一个文件、多一层间接、IDE 里"跳转到定义"多跳一次。

---

## 6. 执行流程：从 main 到输出

### 6.1 `register` 的完整调用链

```text
Client.main()
└─ demoWithFacade()
   └─ facade.register(1, "鱼皮", "yupi@example.com")
      │
      ├─[1] validator.validateUsername("鱼皮")
      │     └─ 打印 [Validator] 校验用户名：鱼皮
      │     └─ 返回 Optional.empty()         ← 通过
      │
      ├─[2] validator.validateEmail("yupi@example.com")
      │     └─ 打印 [Validator] 校验邮箱：yupi@example.com
      │     └─ 返回 Optional.empty()         ← 通过
      │
      ├─[3] repository.findByUsername("鱼皮")
      │     └─ 遍历 table.values()，filter username.equals("鱼皮")
      │     └─ 返回 Optional.empty()         ← 不重复
      │
      ├─[4] new User(1, "鱼皮", "yupi@example.com", LocalDateTime.now())
      ├─[5] repository.save(user)
      │     └─ table.put(1, user)
      │     └─ 打印 [Repository] 写入记录：id=1, name=鱼皮
      │
      ├─[6] auditLogger.log("REGISTER", "新用户注册 id=1, username=鱼皮")
      │     └─ records.add("09:14:34  REGISTER     新用户注册 id=1, username=鱼皮")
      │     └─ 打印 [Audit] 09:14:34  REGISTER     新用户注册 id=1, username=鱼皮
      │
      ├─[7] emailNotifier.send("yupi@example.com", "欢迎加入", "你好 鱼皮，注册成功！")
      │     └─ sentCount++
      │     └─ 打印 [Email] → yupi@example.com | 主题：欢迎加入 | 正文：你好 鱼皮，注册成功！
      │
      ├─ 打印 [OK] 注册流程结束：成功
      └─ 返回 Optional.of(user)
```

**七个子系统调用，四次提前返回的可能性**：

| 检查点 | 失败时 |
|---|---|
| 用户名校验 | `fail("REGISTER", "用户名不能为空")` |
| 邮箱校验 | `fail("REGISTER", "邮箱格式不正确：...")` |
| 查重 | `fail("REGISTER", "用户名已存在：...")` |
| 落库 | 不检查（内存 Map 不会失败） |

**注意第 4 个"不检查"** —— 真实项目里 `repository.save()` 可能抛
`DataAccessException`（数据库连接断开、唯一索引冲突）。这些异常**不应该被外观吞掉**，
而应该让它冒泡到更高层（Controller 的统一异常处理器）。

> 这就是外观模式的一个边界：**它编排"正常流程"，但不隐藏"异常"**。
> 一个把异常全部 catch 掉然后返回 `Optional.empty()` 的外观，会让调用方完全瞎掉。

### 6.2 实际输出（JDK 25 实测）

```text
java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.facade.Client
```

结果：**74 行 / 3921 字节**

```text
=== 1. 使用外观：客户端代码只有 1 行 ===
  >>> 注册流程开始：id=1, username=鱼皮
      [Validator] 校验用户名：鱼皮
      [Validator] 校验邮箱：yupi@example.com
      [Repository] 写入记录：id=1, name=鱼皮
      [Audit] 09:14:34  REGISTER     新用户注册 id=1, username=鱼皮
      [Email] → yupi@example.com | 主题：欢迎加入 | 正文：你好 鱼皮，注册成功！
  [OK] 注册流程结束：成功

  客户端后续操作同样简单：
      查询到用户：鱼皮 / yupi@example.com
  >>> 改邮箱流程开始：id=1, newEmail=yupi_new@example.com
      [Validator] 校验邮箱：yupi_new@example.com
      [Repository] 写入记录：id=1, name=鱼皮
      [Audit] 09:14:34  CHANGE_EMAIL id=1 邮箱 yupi@example.com → yupi_new@example.com
      [Email] → yupi_new@example.com | 主题：邮箱变更通知 | 正文：你的登录邮箱已变更为 yupi_new@example.com
  [OK] 改邮箱流程结束：成功
  >>> 注销流程开始：id=1
      [Repository] 删除记录：id=1（鱼皮）
      [Audit] 09:14:34  UNREGISTER   用户注销 id=1, username=鱼皮
      [Email] → yupi_new@example.com | 主题：账号已注销 | 正文：再见 鱼皮，你的账号已注销。
  [OK] 注销流程结束：成功
      确认已注销，查询返回 empty

=== 2. 不使用外观：同样的注册，客户端要写这么多 ===
      [Validator] 校验用户名：程序员
      [Validator] 校验邮箱：dev@example.com
      [Repository] 写入记录：id=2, name=程序员
      [Audit] 09:14:34  REGISTER     新用户注册 id=2
      [Email] → dev@example.com | 主题：欢迎加入 | 正文：你好 程序员

  ↑ 上面这一大段就是外观替你隐藏掉的复杂度。
    客户端被迫依赖了 4 个类型、6 个方法调用、3 个 if 分支。
    如果注册流程新增一步'发送短信验证码'，
    用外观：只改 UserServiceFacade 一处；不用外观：每个调用点都要改。

=== 3. 失败场景 & 外观汇总的统计信息 ===
  >>> 注册流程开始：id=10, username=张三
      [Validator] 校验用户名：张三
      [Validator] 校验邮箱：zhangsan@example.com
      [Repository] 写入记录：id=10, name=张三
      [Audit] 09:14:34  REGISTER     新用户注册 id=10, username=张三
      [Email] → zhangsan@example.com | 主题：欢迎加入 | 正文：你好 张三，注册成功！
  [OK] 注册流程结束：成功

  场景 A：用户名太短，被校验子系统拦下
  >>> 注册流程开始：id=11, username=李
      [Validator] 校验用户名：李
      [Audit] 09:14:34  REGISTER_FAIL 用户名长度必须在 2~20 之间，当前为 1
  [FAIL] 流程终止：用户名长度必须在 2~20 之间，当前为 1

  场景 B：邮箱格式错误，被校验子系统拦下
  >>> 注册流程开始：id=12, username=王五
      [Validator] 校验用户名：王五
      [Validator] 校验邮箱：not-an-email
      [Audit] 09:14:34  REGISTER_FAIL 邮箱格式不正确：not-an-email
  [FAIL] 流程终止：邮箱格式不正确：not-an-email

  场景 C：用户名重复，被存储子系统查重拦下
  >>> 注册流程开始：id=13, username=张三
      [Validator] 校验用户名：张三
      [Validator] 校验邮箱：another@example.com
      [Audit] 09:14:34  REGISTER_FAIL 用户名已存在：张三
  [FAIL] 流程终止：用户名已存在：张三

  当前用户列表：
      - id=10, 张三, zhangsan@example.com

  累计发送邮件：1 封
  审计记录共 4 条：
      09:14:34  REGISTER     新用户注册 id=10, username=张三
      09:14:34  REGISTER_FAIL 用户名长度必须在 2~20 之间，当前为 1
      09:14:34  REGISTER_FAIL 邮箱格式不正确：not-an-email
      09:14:34  REGISTER_FAIL 用户名已存在：张三
```

**五处值得盯住看**：

1. **第 1 段开头的缩进层次**：`>>>` 是流程边界，`[Validator]` / `[Repository]` 等带 6 个空格，
   `[OK]` / `[FAIL]` 带 2 个空格。这个缩进不是装饰，它让 74 行输出**能看出调用层次**。
2. **第 2 段没有 `>>>` 和 `[OK]`**：因为这一段是客户端自己编排的，没有外观，也就没有
   流程边界的打印。**这恰好证明了"流程编排"这件事原本是客户端的责任。**
3. **第 3 段场景 A 只调了一次 Validator**：用户名就挂了，邮箱都没检查。
   这说明外观是**短路求值**的 —— 前面失败就不往后走。
4. **第 3 段场景 C 走完了两次校验才被拦**：用户名和邮箱都合法，卡在查重。
   三次失败的拦截点各不相同，但都走了同一个 `fail()` 出口。
5. **"累计发送邮件：1 封"**：场景 A/B/C 都失败了，没有触发邮件。
   只有 id=10 那次成功注册发了 1 封。这个数字验证了"失败就短路"。

### 6.3 重构版的行为探针

`FacadeProProbe` 验证四个设计收益，完整 29 行：

```text
[事实 1] 构造器注入让外观可被测试：换成假存储，外观代码一行不用改
  >>> 注册流程开始：id=1, username=测试用户
      [Validator] 校验用户名：测试用户
      [Validator] 校验邮箱：t@example.com
      [Audit] 09:15:15  REGISTER     新用户注册 id=1, username=测试用户
      [Email] → t@example.com | 主题：欢迎加入 | 正文：你好 测试用户，注册成功！
  [OK] 注册流程结束：成功
  register 返回值 present = true
  假存储 save 被调用次数 = 1
  假存储收到的用户 = User[id=1, username=测试用户, email=t@example.com, createdAt=2026-10-23T09:15:15.814366200]

[事实 2] 校验失败时返回 empty，调用方可以分支而不是猜控制台
  >>> 注册流程开始：id=2, username=x
      [Validator] 校验用户名：x
      [Audit] 09:15:15  REGISTER_FAIL 用户名长度必须在 2~20 之间，当前为 1
  [FAIL] 流程终止：用户名长度必须在 2~20 之间，当前为 1
  用户名太短，返回值 present = false

[事实 3] 失败也会写审计，且审计列表是只读视图
  审计条数 = 2
    09:15:15  REGISTER     新用户注册 id=1, username=测试用户
    09:15:15  REGISTER_FAIL 用户名长度必须在 2~20 之间，当前为 1
  往审计列表 add 会抛：java.lang.UnsupportedOperationException

[事实 4] 注销不存在的用户返回 false，调用方拿得到结论
  >>> 注销流程开始：id=999
      [Audit] 09:15:15  UNREGISTER_FAIL 用户不存在：id=999
  [FAIL] 流程终止：用户不存在：id=999
  unregister(999) 返回：false
```

**事实 1 最值得注意**：探针里的 `FakeRepository` 是探针自己写的类，
外观完全不知道它的存在。**这就是"依赖接口而非实现"的实际收益** ——
换一套存储，不用改外观一行代码。

### 6.4 一个必须先说清楚的事：这份输出**不可复现**

这是本项目中唯一一份"两次运行结果不同"的 Demo 输出。

我在同一个 JVM、同一台机器上连续运行两次，得到：

| 运行 | 行数 | 字节数 | SHA-256 前 16 位 |
|---|---|---|---|
| 第一次 | 74 | 3921 | `1748D32F9E8F7DBD` |
| 第二次 | 74 | 3921 | `ECDB38DD1C48EF1C` |

**行数和字节数完全一致，哈希却不同。** 原因只有一个：输出里含时间戳。

`AuditLogger` 用的是：

```java
private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
// ...
String entry = String.format("%s  %-12s %s", LocalTime.now().format(TIME), action, detail);
```

每次运行到这一行，`LocalTime.now()` 返回的秒数都不一样。同理，`User` 的 `createdAt`
也是 `LocalDateTime.now()`，精确到纳秒。

**这意味着什么？**

| 影响 | 说明 |
|---|---|
| 不能用文件哈希做"输出是否变化"的回归校验 | 同一份代码，两次运行哈希必然不同 |
| 不能用全文 diff 做断言 | 时间戳那一列全是噪音 |
| 但可以断言**行数**和**结构** | 74 行是稳定的，每个 `[Audit]` 前缀都在固定位置 |

**正确的回归校验方式**：

```text
# 把时间戳替换成固定占位符，再比较
Get-Content out.txt | ForEach-Object { $_ -replace '\d{2}:\d{2}:\d{2}', 'HH:mm:ss' } | ...
```

**这也解释了为什么前面的模块都能给出稳定哈希**：组合模式、装饰器模式的输出
全是确定性的字符串，没有任何时间、随机数、哈希码。而外观模式引入了系统时间，
就失去了这个性质。

> **一个工程习惯**：写 Demo 时如果输出要用于文档/回归，就把时间戳改成可注入的
> `Clock`（`java.time.Clock`），默认用 `Clock.systemDefaultZone()`，测试时注入固定时钟。
> `LocalTime.now(clock)` 就能得到确定值。本项目为了 Demo 简洁没有这么做，
> 但**读者应该知道这是可以修的**。

### 6.5 74 行输出是怎么构成的

| 段落 | 行数 | 内容 |
|---|---|---|
| 场景 1：使用外观 | 20 | 注册 + 查询 + 改邮箱 + 注销 + 确认 |
| 场景 2：不使用外观 | 12 | 客户端手写 6 步 |
| 场景 3：失败与统计 | 34 | 1 次成功 + 3 次失败 + 列表 + 统计 |
| 段间空行 | 8 | — |
| 合计 | 74 | — |

**场景 2 的 12 行对比场景 1 的 20 行**，看起来"不用外观反而更短"？
不是的 —— 场景 2 只是**单个注册**，而场景 1 做了**四件事**（注册、查询、改邮箱、注销）。

公平的比较是：**"注册"这一件事**

| | 用外观 | 不用外观 |
|---|---|---|
| 客户端代码行数 | 1 | 30+ |
| 客户端依赖的类型 | 1（`UserServiceFacade`） | 5（4 个子系统 + `User`） |
| 客户端要写的 if 分支 | 0 | 3 |

这正是 `Client` 里那句话的意思："客户端被迫依赖了 4 个类型、6 个方法调用、3 个 if 分支。"

---

## 7. 扩展对比

### 7.1 新增"注册时发送短信验证码"要改几个文件

**用外观**：

```java
// UserServiceFacade.register() 里加一步
auditLogger.log("REGISTER", "新用户注册 id=" + id + ", username=" + username);
emailNotifier.send(email, "欢迎加入", "你好 " + username + "，注册成功！");
smsNotifier.send(phone, "您的验证码是 " + code);        // ← 新增 1 行
```

**改动清单**：

| 文件 | 改动 |
|---|---|
| `SmsNotifier.java` | 新增（子系统） |
| `UserServiceFacade.java` | 加 1 个字段、1 个构造器参数、1 行调用 |
| `Client.java` | 零改动（除非想演示短信） |

**不用外观**：每个调用点（注册、批量导入、第三方登录同步…）都要加。
10 个调用点 = 10 处修改，漏一处就是"有些用户收不到验证码"。

### 7.2 外观 vs 中介者（Mediator）

这是最容易混淆的一对，因为两者都"协调多个对象"。

| | 外观 | 中介者 |
|---|---|---|
| 通信方向 | **单向**：客户端 → 外观 → 子系统 | **多向**：同事之间都通过中介者互发消息 |
| 子系统知道中介者吗 | **不知道**（GoF 明确要求） | **知道**（同事持有中介者引用） |
| 目的 | **简化接口**（让客户端少写代码） | **解耦同事**（让对象之间不直接引用） |
| 双方数量 | 1 个客户端 → N 个子系统 | M 个同事 ↔ N 个同事 |
| 典型场景 | 一键开机、API 网关、`JdbcTemplate` | 聊天室、机场塔台、`java.util.Timer` |

**一句话**：外观是"秘书替你转达给各部门"（各部门不认识秘书）；
中介者是"对讲机，所有人都调到同一频道"（所有人都认识对讲机）。

**代码上的判据**：

```java
// 外观：子系统完全不知道外观存在
class UserRepository { /* 没有 facade 字段 */ }

// 中介者：同事持有中介者引用
class ChatUser {
    private final ChatRoomMediator mediator;   // ← 有反向引用
    void send(String msg) { mediator.broadcast(this, msg); }
}
```

本项目聊天室模块（`behavioral.mediator`）就是标准的中介者。

### 7.3 外观 vs 适配器

| | 外观 | 适配器 |
|---|---|---|
| 接口数量 | 把 **N 个接口**合成 **1 个新接口** | 把 **1 个接口**转成 **另 1 个接口** |
| 目的 | 简化 | 兼容 |
| 典型 | `JdbcTemplate` 包 JDBC 的一堆接口 | `InputStreamReader` 把 `InputStream` 转成 `Reader` |
| 是否新建接口 | **是**（`register` 是全新的业务方法） | 否（转换到已有的目标接口） |

**一句话**：外观是"把十个遥控器合成一个"；适配器是"给美标插头套一个国标转换头"。

### 7.4 外观 vs 代理

| | 外观 | 代理 |
|---|---|---|
| 接口 | **新接口**（业务方法名） | **同一接口**（和本体一模一样） |
| 客户端感知 | 知道自己用的是外观 | 以为用的是本体 |
| 目的 | 简化 | 控制访问 |

### 7.5 外观 vs 模板方法

两者都"固化流程"，区别在**流程由谁执行**：

| | 外观 | 模板方法 |
|---|---|---|
| 流程写在哪 | 外观类的方法体里 | 父类的 `final` 方法里 |
| 可变部分怎么扩展 | **无扩展点**，改外观源码 | **子类覆盖钩子方法** |
| 适合 | 流程**固定**、不需要多套实现 | 流程**骨架固定**、但步骤实现要变 |
| 例子 | `register` 就是这 6 步 | `HttpServlet.service()` → `doGet`/`doPost` |

**如果发现"外观里的流程要按不同场景变"，说明该上模板方法了**：

```java
// ❌ 用外观硬撑多种流程
public Optional<User> register(...) {
    if (type == VIP) { ...12 步... }
    else if (type == NORMAL) { ...6 步... }
    else { ...3 步... }
}

// ✅ 改成模板方法
public abstract class RegisterTemplate {
    public final Optional<User> register(int id, String name, String email) {
        if (!validate(...)) return Optional.empty();
        User u = doCreate(id, name, email);      // 子类实现
        afterCreate(u);                          // 子类可覆盖
        return Optional.of(u);
    }
    protected abstract User doCreate(...);
    protected void afterCreate(User u) { /* 默认空实现 */ }
}
```

### 7.6 外观 vs 分层架构（Controller / Service / DAO）

严格说，**Service 层就是一个企业级的外观**：

```text
Controller  →  UserService  →  UserRepository / UserValidator / AuditLogger
              ↑
        它就是这个 Demo 里的 UserServiceFacade
```

| 概念 | 对应本 Demo |
|---|---|
| Controller | `Client` |
| Service（应用服务） | `UserServiceFacade` |
| Domain Service（领域服务） | `UserValidator` |
| Repository | `UserRepository` |
| Infrastructure | `AuditLogger` / `EmailNotifier` |

**所以"外观模式"在真实项目里往往不叫 Facade，而叫 `XxxService`。**
理解这一点很重要 —— 否则你会以为外观模式是个"教学玩具"，实际天天在用。

### 7.7 外观 + 依赖注入框架

本项目的依赖注入是手写的（构造器）。真实项目通常用 Spring：

```java
@Service
public class UserServiceFacade {
    private final UserRepository repository;
    private final UserValidator validator;
    private final AuditLogger auditLogger;
    private final EmailNotifier emailNotifier;

    public UserServiceFacade(UserRepository repository,
                             UserValidator validator,
                             AuditLogger auditLogger,
                             EmailNotifier emailNotifier) {
        this.repository = repository;
        // ...
    }
}
```

**注意 Spring 的构造器注入不需要 `@Autowired`**（Spring 4.3+ 单构造器自动注入）。
加了反而显得啰嗦。

> **一个常见误区**：以为用了 Spring 就"自动实现了外观模式"。
> 不对 —— Spring 只是帮你**创建和管理**外观对象，外观的**编排逻辑**还是你自己写的。
> 没有 `@Service` 的 `UserServiceFacade` 依然是外观，只是装配方式不同。

### 7.8 外观的"薄"与"厚"

```
薄外观（Thin Facade）    只转发，不编排                ← 原项目（价值≈0）
中等外观                编排 2-5 个子系统              ← 本项目
厚外观（Fat Facade）    编排 + 业务规则 + 事务 + 缓存    ← 上帝类预警
```

**判断标准**：

- 如果一个"外观方法"里只有 1 行转发 → **薄**，考虑删掉，直接暴露子系统
- 如果有 3-8 步、涉及 2-4 个子系统 → **中等**，这是外观最舒服的位置
- 如果超过 10 步、涉及 5 个以上子系统、还包含业务规则 → **厚**，该拆了

**拆的办法**：

1. 把业务规则移到领域对象或领域服务
2. 把"多步流程"进一步拆成子流程（每个子流程一个私有方法）
3. 如果流程要按场景变，改成模板方法
4. 如果流程要按配置变，改成职责链或流程引擎

---

## 8. 优缺点与常见误区

### 8.1 优点

1. **降低耦合**：客户端只依赖 1 个类型，不依赖子系统。
2. **固化流程**：正确顺序只写一次，不会有人写错。
3. **简化接口**：30 行变 1 行。
4. **便于演进**：加一步只改一处。
5. **分层清晰**：外观层、子系统层职责分明，便于团队分工。
6. **可测试性强**（配合依赖注入）：可以注入 Mock 子系统。

### 8.2 缺点

1. **可能变成上帝类**：所有业务方法都往外观里塞，最后臃肿不堪。
2. **可能成为性能瓶颈**：如果外观做了不该做的重活（比如每次调用都做全量统计）。
3. **掩盖了子系统的复杂度**：新人可能不知道"一行 `register` 背后有 7 个调用"，
   出问题时无从下手。
4. **过度封装的风险**：把本来简单的事情包起来，反而增加理解成本（原项目就是这个毛病）。
5. **不容易做细粒度控制**：外观方法通常"全有或全无"，无法只执行其中几步。

### 8.3 关于"掩盖复杂度"

第 3 条缺点值得展开。外观让代码变简单了，但也让**调试变难了**：

```java
facade.register(1, "鱼皮", "yupi@x.com");   // 一行
```

如果这一行报错了，你得知道它是走到哪一步挂的。解决办法：

1. **日志要有层次**（本项目用缩进区分：`>>>` / 6 空格 / 2 空格）
2. **异常要有上下文**（不要只抛 `RuntimeException`，要带"哪一步、哪个参数"）
3. **提供诊断方法**（比如 `facade.getAuditRecords()` 能看到每一步）

### 8.4 常见误区

| 误区 | 真相 |
|---|---|
| "外观就是给 DAO 套个壳" | 那是**转发层**。外观的核心是"协调多个子系统"，只调 1 个方法的不是外观 |
| "外观必须把所有子系统都藏起来" | 错。外观是入口不是牢笼，应该保留"绕过外观直接用子系统"的可能 |
| "外观应该抽个接口方便替换" | 通常不需要。外观的职责是简化，多一层接口是复杂化。除非真有多个外观实现 |
| "外观里可以写业务规则" | 不行。规则会变，写进外观后每次改规则都要动流程代码，最终变成上帝类 |
| "用了外观就不能直接用子系统了" | 可以。真实项目里运维、DBA、高级用户经常需要绕过外观 |
| "外观和中介者是一回事" | 方向不同：外观是单向（客户端→子系统），中介者是多向（同事互相通信） |
| "外观方法越少越好" | 外观的 API 应该**按业务场景**组织，不是按子系统组织。`register` / `changeEmail` / `unregister` 是三个场景，不是三个 DAO 方法的转发 |
| "有了 Spring 就不需要外观模式" | Spring 管理 Bean，不管流程编排。`@Service` 类里的编排逻辑依然是外观模式 |
| "外观一定要单例" | 不一定。本项目的 `UserServiceFacade` 每次 `new` 都可以，因为它**无状态**（4 个字段全是 final 的引用，且子系统本身无状态或状态独立）。如果子系统有全局状态（如 `AuditLogger` 的 records），单例才有意义 |

### 8.5 一个反直觉的点：外观让"改需求"变容易，也让"看清问题"变难

假设注册流程有 bug：某些用户注册后收不到邮件。

**没有外观时**，你能在客户端代码里直接看到：

```java
repository.save(user);
auditLogger.log("REGISTER", "...");
emailNotifier.send(email, "欢迎加入", "你好 " + username);
```

顺序一目了然，一眼就能发现"邮件发在审计之后"。

**有外观时**，客户端只有：

```java
facade.register(1, "鱼皮", "yupi@x.com");
```

你得**跳进外观**才能看到流程。如果外观有 500 行、20 个方法，跳进去还是一团雾。

**所以外观必须配套**：

| 配套措施 | 作用 |
|---|---|
| 流程日志（本项目用缩进） | 从输出就能看出走到哪一步 |
| 方法拆小（`fail()` 抽取） | 一个方法不超过 50 行 |
| 注释写明步骤序号（`// 步骤 1-2`） | 让读者快速定位 |
| 单测覆盖每条失败路径 | 改流程时能立刻发现回归 |

**外观不是"隐藏复杂度"，而是"把复杂度集中到一处并管理好它"。**
如果只是把代码搬进一个类，那不叫封装，叫搬家。

---

## 9. 面试与自测

### 9.1 面试讲解（30 秒版）

> "外观模式为多个子系统提供一个统一的高层接口，让客户端不需要认识子系统、也不需要知道调用顺序。典型例子是电脑开机键、API 网关、Spring 的 `JdbcTemplate`。它的关键点是：外观只做**流程编排**，不做业务逻辑；它知道子系统，但子系统**不知道**它。判断是否该用外观的标准是：一次业务操作是否需要协调**两个以上**子系统 —— 如果只调一个方法，那就是转发层，不是外观。"

### 9.2 面试讲解（2 分钟版）

> "外观模式解决的是'客户端与复杂子系统耦合过深'的问题。
>
> **不用它会怎样**：注册用户需要 6 步（校验用户名、校验邮箱、查重、落库、审计、发邮件），
> 客户端要依赖 4 个类型、写 3 个 if 分支、30 行代码。而且每个调用点都要重写一遍，
> 顺序错了就出脏数据。
>
> **角色很简单**：一个外观类 + N 个子系统。外观持有子系统的引用，把它们的调用编排成一个业务方法。
>
> **几个设计取舍**：
> ① **外观通常不抽接口**。它的职责是简化，多一层接口反而是复杂化。只有真的有多个外观实现（本地版/远程版）时才抽。
> ② **外观只编排不做业务**。校验规则归 `UserValidator`，存储归 `UserRepository`。规则会变，写进外观就变成上帝类。
> ③ **子系统通过构造器注入**。原项目在构造器里 `new UserDAOImpl()`，导致无法注入 Mock 做单测。
> ④ **返回值用 `Optional` / `boolean` 而不是 `void`**。原项目四个方法全返回 void，调用方只能靠看控制台猜结果。
> ⑤ **外观是入口不是牢笼**。它提供最方便的路径，但不阻止高级用户直接用子系统。
>
> **和中介者的区别**常被问：外观是单向的（客户端→子系统），子系统不认识外观；中介者是多向的（同事之间通过中介者互发消息），同事持有中介者引用。GoF 原文特意强调 'subsystems do not know about the facade'，这是判据。
>
> **最大的风险是变成上帝类**。所以我会给外观设三条线：一个方法不超过 8 步、不超过 4 个子系统、不含业务规则。超了就拆。"

### 9.3 高频追问

**Q1：外观模式要不要抽接口？**

> 通常**不抽**。外观的职责是"简化"，多一层接口意味着客户端要做一次"选实现"的决定，
> 而它根本不关心这个决定。
>
> 只有一种情况该抽：**确实存在多个外观实现，且调用方式完全不同**。比如：
> - 单体版 `LocalUserService`（直接调本地子系统）
> - 微服务版 `RemoteUserService`（调远程 HTTP 接口）
>
> 这时抽一个 `UserService` 接口，让客户端依赖它，是有价值的。
>
> **反例**：为了"面向接口编程"的口号，给每个 Service 都抽一个 `IXxxService` 接口，
> 结果 90% 的接口永远只有一个实现 —— 这是纯粹的样板代码。

**Q2：外观和中介者到底怎么区分？**

> 三个判据：
>
> 1. **依赖方向**：外观 → 子系统（单向）；中介者 ← → 同事（双向，同事持有中介者引用）
> 2. **子系统/同事之间是否通信**：外观模式下子系统之间**不通信**（各干各的）；
>    中介者模式下同事之间**必须**通过中介者通信
> 3. **目的**：外观是"简化接口"；中介者是"解耦对象间的网状依赖"
>
> 类比：外观是"公司前台"，你找谁前台帮你转；中介者是"公司内部的对讲机"，
> 所有人都调到同一个频道。
>
> 代码判据：看到 `class ChatUser { private final ChatRoomMediator mediator; }` 就是中介者。

**Q3：外观会不会成为性能瓶颈？**

> 外观本身只是多一次方法调用，开销可以忽略。但如果外观做了这三件事，就会成为瓶颈：
>
> ① **每次调用都做全量统计**（比如 `register` 里遍历所有用户算总数）
> ② **外观内部串行调用本该并行的子系统**（比如发邮件、发短信、写审计可以并行）
> ③ **外观持有全局锁**（`synchronized` 加在方法上）
>
> 正确的做法：外观只负责**编排**，耗时操作交给子系统异步执行。
> 例如 `emailNotifier.sendAsync(...)`，外观立刻返回，邮件在后台发。

**Q4：外观能不能调用另一个外观？**

> **技术上可以，设计上要小心。**
>
> 可以的情况：分层。比如 `OrderFacade` 调 `UserFacade.findById()` 拿用户信息。
> 这时形成了"外观的层次结构"，是合理的。
>
> 要小心的情况：**循环调用**。`UserFacade` 调 `OrderFacade`，`OrderFacade` 又调 `UserFacade` ——
> 这就是循环依赖，Spring 启动都可能失败。
>
> 判断标准：**外观之间应该是单向的、层次化的**（高层调低层）。
> 如果出现互相调用，说明这两个外观的边界划错了，应该合并或者抽出更小的服务。

**Q5：如果一个业务操作要调 10 个子系统，还能用外观吗？**

> 能，但要警惕。10 个子系统的编排通常意味着两种情况：
>
> **① 流程确实复杂**（比如电商下单：库存、价格、优惠券、积分、风控、支付、物流、通知、审计、埋点）。
>    这时用外观是对的，但要：
>    - 把 10 步按业务语义分组（下单前 / 下单 / 下单后）
>    - 每组抽一个私有方法，主方法只有 3 行
>    - 考虑引入**流程引擎**（如 Camunda）或**事件驱动**（Spring Event）替代硬编码
>
> **② 外观承担了不该承担的职责**（比如把日志、监控、缓存都算成"子系统"）。
>    这些是**横切关注点**，应该用 AOP 或装饰器处理，不该进外观的流程编排。
>
> 我的经验：**超过 8 步就该重新审视边界了**。

**Q6：`Optional` 和 `null`，外观该用哪个？**

> 用 `Optional`，但只用在**返回值**上。三条规则：
>
> ① **返回值**：`Optional<User> findUser(int id)` —— ✅ 推荐，把"可能不存在"写进签名
> ② **字段**：`private Optional<User> currentUser;` —— ❌ 不推荐，直接用可空字段或 `null` 处理逻辑
> ③ **参数**：`void update(Optional<String> name)` —— ❌ 不推荐，用重载或 `@Nullable` 注解
>
> 另外注意：**`Optional` 不是万能药**。如果一个方法"失败"有多种原因（比如 `register` 有
> 用户名重复、邮箱格式错、数据库异常三种失败），`Optional.empty()` 无法区分。
> 这时应该用**异常**（对不可恢复的错误）或**结果对象**（`Result<User>`）：
>
> ```java
> public sealed interface RegisterResult permits Success, ValidationFailed, DuplicateName {}
> ```
>
> 本项目用了 `Optional` + `fail()` 打印原因，是 Demo 级的简化。
> 生产代码里应该把失败原因结构化返回。

### 9.4 自测题

**1. 原项目的外观为什么"价值几乎为零"？**

<details><summary>参考答案</summary>

因为它只包装了**一个** `UserDAO`，而且每个方法内部只调 1-2 个 DAO 方法：

```java
public void createUser(int id, String name) {
    User user = new User(id, name);
    userDAO.insert(user);        // 只调了 1 次
}
```

客户端不用外观，自己写是 2 行；用了外观是 1 行。**省 1 行，多一层。**
这不是外观模式，是**转发层**。

外观模式的核心价值在于"把多个子系统的协作固定下来"。只有 1 个子系统时，
没有任何"协作"需要固定，外观就退化成纯粹的开销。

**这给我们的教训**：判断一个模式该不该用，先看它的**前提条件是否成立**。
外观的前提是"协作复杂"，不成立就不该用。
</details>

**2. 为什么 `register` 返回 `Optional<User>` 而不是 `boolean`？**

<details><summary>参考答案</summary>

因为成功时调用方通常需要用户对象（含 `id`、`createdAt` 等生成的值）。

用 `boolean` 的代价：

```java
if (facade.register(1, "鱼皮", "yupi@x.com")) {
    User u = facade.findUser(1).orElseThrow();   // 多一次查询
    // 而且如果中间被别的线程删了，这里会抛异常
}
```

用 `Optional<User>`：

```java
facade.register(1, "鱼皮", "yupi@x.com")
       .ifPresent(u -> System.out.println("注册成功：" + u.username()));
```

一次调用，两种信息（成功与否 + 结果对象）。

**那 `unregister` 为什么用 `boolean`？** 因为注销不需要返回数据 ——
"删掉了"和"没删掉"就是全部信息。**返回类型应该携带调用方需要的全部信息，不多不少。**
</details>

**3. 如果把 `UserValidator` 的校验规则直接写进 `UserServiceFacade.register()`，会怎样？**

<details><summary>参考答案</summary>

短期没问题，长期三个后果：

**① 外观膨胀成上帝类**。用户名的敏感词、邮箱的域名白名单、手机号归属地、密码复杂度……
这些规则会持续增长。每条都写进外观，一年后 `register` 变成 300 行。

**② 规则无法复用**。`changeEmail` 也要校验邮箱，`updateProfile` 也要校验用户名 ——
要么复制代码，要么把外观的方法改成 public（破坏封装）。

**③ 规则无法独立测试**。想测"用户名长度 1 个字符被拒"，得先造一个完整的
`UserServiceFacade`（含 4 个子系统），而不能只 `new UserValidator()` 然后断言。

**正确的边界**：外观只回答"先做什么后做什么"，不回答"什么算合法"。
</details>

**4. 为什么 `UserServiceFacade()` 无参构造器要写成 `this(...)` 而不是各写一份赋值？**

<details><summary>参考答案</summary>

```java
// ✅ 本项目做法
public UserServiceFacade() {
    this(new InMemoryUserRepository(), new UserValidator(), new AuditLogger(), new EmailNotifier());
}

// ❌ 危险做法
public UserServiceFacade() {
    this.repository = new InMemoryUserRepository();
    this.validator = new UserValidator();
    this.auditLogger = new AuditLogger();
    this.emailNotifier = new EmailNotifier();
}
```

差别在**未来加第 5 个子系统**时：

- `this(...)` 版本：只改 4 参构造器（加参数 + 赋值），无参构造器只在
  `this(...)` 里补一个实参。编译器会强制你补（参数个数对不上）。
- 各写一份版本：两个构造器各有一份赋值逻辑。你可能只改了 4 参的，忘了无参的 ——
  然后 `new UserServiceFacade()` 的第 5 个字段是 `null`，运行时 NPE。

**原则：同一段逻辑只写一次。** 构造器链（`this(...)`）是 Java 提供的标准手段。

顺带一提：`super(...)` 同理 —— 子类构造器应该调用父类构造器，而不是"自己也赋一遍值"。
</details>

**5. 这份 Demo 的输出为什么不能用哈希做回归校验？怎么改才能？**

<details><summary>参考答案</summary>

**原因**：输出里含 `LocalTime.now()` 产生的 `HH:mm:ss` 时间戳（`AuditLogger`），
以及 `LocalDateTime.now()` 产生的纳秒级时间（`User.createdAt`）。
每次运行时间不同，所以哈希必然不同。

实测：同一份代码连续运行两次，行数（74）和字节数（3921）都一致，
但 SHA-256 前 16 位分别是 `1748D32F9E8F7DBD` 和 `ECDB38DD1C48EF1C`。

**三种改法**：

① **注入 `Clock`**（最规范）：

```java
public class AuditLogger {
    private final Clock clock;
    public AuditLogger() { this(Clock.systemDefaultZone()); }
    public AuditLogger(Clock clock) { this.clock = clock; }

    public void log(String action, String detail) {
        String entry = String.format("%s  %-12s %s",
                LocalTime.now(clock).format(TIME), action, detail);
        // ...
    }
}
```

测试时注入 `Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)`，
输出就完全确定。

② **输出后归一化**：把时间戳替换成占位符再比较（治标不治本，但简单）。

③ **只断言结构不断言内容**：断言"包含 4 条 `[Audit]`"、
"第 3 条以 `REGISTER_FAIL` 开头"，而不是断言全文。

**这个问题不只是 Demo 的问题**：真实项目里的日志、快照测试、契约测试都会遇到。
**凡是"输出里含非确定内容"的地方，都必须把那个不确定源抽出来注入。**
</details>

**6. 如果一个外观方法里调用了另一个外观方法，要注意什么？**

<details><summary>参考答案</summary>

要注意三件事：

**① 循环依赖**。`OrderFacade` 调 `UserFacade`，同时 `UserFacade` 调 `OrderFacade` ——
Spring 启动可能直接失败。即使勉强启动，调试时也会陷入"调到哪去了"的迷宫。

**② 事务边界**。如果两个外观各自开了事务，嵌套调用会出现"事务传播"问题
（`REQUIRED` / `REQUIRES_NEW` 的行为完全不同）。

**③ 重复的横切逻辑**。如果两个外观方法都记审计、都做权限校验，嵌套调用会记两次。

**正确的做法**：

- 外观之间保持**单向**（高层调低层），不互调
- 需要复用逻辑时，抽到**更底层的服务**里，让两个外观都调它
- 事务边界放在**最外层**（Controller 或最上层的外观方法）

**判断标准**：如果 A 外观调 B 外观、B 外观又调 A 外观，说明这两个外观的边界划错了 ——
应该合并成一个，或者把公共部分抽出来。
</details>

**7. 外观模式的"子系统"边界怎么划？**

<details><summary>参考答案</summary>

四条经验：

**① 按"变化原因"划**。`UserValidator` 变化的原因是"业务规则变了"；
`UserRepository` 变化的原因是"存储介质变了"；`EmailNotifier` 变化的原因是"通知渠道变了"。
三种不同的变化原因 → 三个子系统。

**② 按"能否独立测试"划**。如果一段逻辑你希望能脱离其他部分单独测试，就应该独立成类。

**③ 按"是否需要替换实现"划**。`UserRepository` 需要换（内存/MySQL/Redis），所以抽接口；
`UserValidator` 不需要换（规则会变，但"校验"这个行为不会换成别的东西），所以不抽。

**④ 不要为每个方法建一个类**。这是最常见的过度拆分 —— `UserSaveService`、
`UserQueryService`、`UserDeleteService` 三个类各有一个方法，它们的"变化原因"完全一样
（都是存储），应该合成一个 `UserRepository`。

**一个实用判据**：如果一个子系统的所有方法总是**一起变化**，
那它就不该被拆成多个子系统。
</details>

---

## 10. 附录

### 10.1 文件清单

| 文件 | 行数 | 层 | 角色 |
|---|---|---|---|
| `UserServiceFacade.java` | 220 | 外观 | 编排 4 个子系统，6 个公开方法 |
| `Client.java` | 132 | 客户端 | 三个场景：用外观 / 不用外观 / 失败与统计 |
| `model/User.java` | 29 | 领域模型 | record，含 `withEmail` 不可变更新 |
| `subsystem/UserRepository.java` | 55 | 子系统接口 | 5 个方法，解决"可替换存储" |
| `subsystem/InMemoryUserRepository.java` | 63 | 子系统实现 | `HashMap` 模拟数据库 |
| `subsystem/UserValidator.java` | 58 | 子系统 | 用户名/邮箱校验，返回 `Optional<String>` |
| `subsystem/AuditLogger.java` | 43 | 子系统 | 审计日志，时间为 `HH:mm:ss` |
| `subsystem/EmailNotifier.java` | 35 | 子系统 | 模拟发邮件 + 计数 |
| `README.md` | 本文档 | — | — |

### 10.2 本文引用的探针

| 探针 | 位置 | 用途 | 输出 |
|---|---|---|---|
| `FacadeProbe.java` | 原项目包内 | 原项目 5 个缺陷（不校验 / 不查重 / 静默覆盖 / 全 void / 删除无反馈） | 31 行 |
| `FacadeProProbe.java` | 默认包 | 重构版 4 个收益（注入可替换 / 返回值可分支 / 审计只读 / 删除有反馈） | 29 行 |

### 10.3 实测环境

| 项 | 值 |
|---|---|
| JDK | `javac 25.0.4.1` |
| 运行参数 | `java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.facade.Client` |
| 重构版输出 | **74 行 / 3921 字节**（行数与字节数稳定） |
| 重构版哈希 | **不稳定**：`1748D32F9E8F7DBD`（第一次）/ `ECDB38DD1C48EF1C`（第二次） |
| 原项目输出 | 10 行 / 272 字节 |

> **哈希不稳定的原因见 §6.4**：输出含 `LocalTime.now()` 与 `LocalDateTime.now()`。
> 这是本项目 24 个模块里唯一一个输出不可复现的模块。

### 10.4 本次修订记录

| 改动 | 原因 |
|---|---|
| **子系统从 1 个扩展到 4 个** | 原项目只包 1 个 DAO，外观退化成转发层，模式价值不成立 |
| 新增 `UserValidator` | 把业务规则从外观剥离，避免上帝类 |
| 新增 `AuditLogger` / `EmailNotifier` | 让"一次业务操作 = 多次子系统协作"这个前提成立 |
| 构造器改为依赖注入（保留无参默认装配） | 原项目在构造器内 `new UserDAOImpl()`，无法注入 Mock |
| 返回值 `void` → `Optional<User>` / `boolean` | 原项目 4 个方法全返回 void，调用方只能靠控制台猜结果 |
| `findById` 返回 `null` → `Optional<User>` | 把"可能不存在"写进签名，避免漏判空导致 NPE |
| 新增 `changeEmail` | 演示"不同业务方法有不同的协作顺序" |
| 新增 `fail()` 私有方法 | 6 处失败路径统一出口，避免重复三段代码 |
| 新增 `getAuditRecords()` / `getSentEmailCount()` | 体现"外观是入口不是牢笼"，高级用户能看子系统状态 |
| 新增 `withEmail` 不可变更新 | 保证审计日志能记录"从旧值改到新值"而不是"新值到新值" |
| 本文档新增 §0 结论、§6.4 不可复现说明、§10 附录 | 原文档 658 行，缺少对"输出不可哈希"这一事实的交代 |
