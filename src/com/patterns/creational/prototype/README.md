# 原型模式（Prototype Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：不重新 `new`，而是拿一个**已经存在的实例**当模板复制一份——模板身上带着"类定义里没有的运行时状态"，这是任何构造器都给不了的东西。
>
> 本目录实测：**4 个源文件 / 242 行**，`javac -Xlint:all` 零告警；运行输出 **37 行 / 1073 字节**，
> SHA-256 `7412EB2B5CA64B95…`（`-Dstdout.encoding=UTF-8` 口径，连跑两次逐行 diff 一致）。

## 0. 先看结论

| 问题 | 答案 |
|---|---|
| 它解决什么 | ① 创建成本高，不想每次重建；② 要复制的是**运行期状态**，不是"类 + 参数" |
| 它的分水岭是什么 | **复制的是实例状态，不是类型**。工厂说"给我一个 X"，原型说"给我一个和它一模一样的" |
| 它和工厂方法的区别 | 工厂用**类**创建；原型用**实例**创建。工厂不需要"已有一个对象"，原型必须有 |
| 什么时候别用 | 对象简单、构造便宜；或者对象不可变（不可变对象直接共享，不用复制） |
| 最容易犯的错 | 只做 `super.clone()`（浅拷贝），结果克隆体和原型**共享同一个可变字段**，改一个另一个跟着变 |

一句话判断：**"我手上已经有一个对象，它的状态不好重建，我只想改其中几处？"** 是，才需要它。

## 1. 现实案例：说人话版

### 1.1 三个生活类比

**简历模板**。招聘网站给你一份"标准简历模板"，你点"使用此模板"，系统复制一份给你编辑。**没人会从零开始建一个空简历**——模板里的字段结构、默认提示语、占位图片都是现成的。

**游戏里的怪物**。策划配好一只"哥布林原型"（血量、攻击力、掉落表、AI 参数），刷怪时按原型克隆 200 只。**每只哥布林的初始状态由原型决定，不是由构造器参数决定**——策划改一次原型，所有新刷的怪都跟着变。

**细胞分裂**。一个细胞分裂成两个，新细胞带着母细胞当时的全部状态（包括已经积累的损伤、当前的代谢水平）。**这不是"按图纸造一个新细胞"，这是"复制当前这个"。**

第三个类比点出了原型的本质：**复制的是"此刻的状态"，而状态是运行期才有的，图纸（类定义）里没有。**

### 1.2 业务场景：文档模板

`WordDocument` 有三个字段：标题、正文、图片列表。

需求：用户从模板库里选一个模板，系统给一份**可编辑的副本**，用户改完后原件不能受影响。

**不用原型**会怎样？

```java
WordDocument template = registry.load("resume");
WordDocument mine = new WordDocument(template.getTitle(),
                                     template.getContent(),
                                     template.getImages());
```

看起来还行。但：

1. **字段一多就崩**——10 个字段要写 10 行，加字段必忘；
2. **私有字段拿不到**——`getXxx()` 不一定都有；
3. **构造器可能有副作用**——如果构造器要查库、发事件、做校验，复制一份就多跑一遍；
4. **构造器可能拒绝复制**——比如构造器规定"新建文档必须有创建人"，而复制时你根本不想再走一遍这个规则。

**用原型**：

```java
WordDocument mine = (WordDocument) template.cloneDocument();
```

一行。而且**拷贝逻辑由类自己负责**——只有 `WordDocument` 自己知道哪些字段要深拷贝、哪些可以直接共享。

### 1.3 专业版：GoF 的定义与三个角色

> Specify the kinds of objects to create using a prototypical instance, and create new objects by copying this prototype.
> —— *Design Patterns*, GoF, 1994

| 角色 | 职责 | 本目录对应 |
|---|---|---|
| **Prototype（抽象原型）** | 声明"复制自己"的方法 | `DocumentPrototype` |
| **ConcretePrototype（具体原型）** | 实现复制，决定深/浅拷贝 | `WordDocument` |
| **Client（使用方）** | 通过复制原型来创建新对象 | `Client` |

GoF 原文里的关键词是 **"prototypical instance"**：被复制的**是一个实例**，不是一个类。这就是原型与所有其他创建型模式的分界线——**它需要一个"已经存在的对象"作为输入。**

**原型注册表**（`DocumentRegistry`）不是 GoF 的正式角色，但它是原型模式在工程里最常见的落地形态：**把原型当数据存起来，按 key 取，取出来就是一份克隆。** 这样"可用的产品种类"从代码里的 `new` 变成了注册表里的数据——加一种模板不用改代码。

### 1.4 JDK 里真实存在的原型

**① `Object.clone()`——一切的源头**

```java
protected native Object clone() throws CloneNotSupportedException;
```

它是 `protected` 且 `native`，会做一次**逐字段的内存复制**（浅拷贝），**不调用任何构造器**。

**② `ArrayList.clone()`**

```java
ArrayList<String> a = new ArrayList<>(List.of("x", "y"));
@SuppressWarnings("unchecked")
ArrayList<String> b = (ArrayList<String>) a.clone();
```

`ArrayList` 的 `clone()` 复制了**底层数组的引用**？不是——它复制了数组本身（`Arrays.copyOf(elementData, size)`），但**元素对象仍然共享**。所以对 `ArrayList<String>` 够用（`String` 不可变），对 `ArrayList<byte[]>` 就是坑。

**③ 数组的 `clone()`**

```java
int[] a = {1, 2, 3};
int[] b = a.clone();     // 真的复制了一份
```

数组是 JVM 特例：数组的 `clone()` 是**public** 的，且返回类型精确（`int[]`），不需要强转。这是 Java 里最"好用"的原型。

**④ `java.util.Date.clone()` / `Calendar` 的坑**

`Date` 实现了 `Cloneable`，`clone()` 返回 `Date`。但 `Date` 可变——所以任何"返回内部 `Date` 字段"的 getter 都必须返回 `clone()`，否则外部能改你的内部状态。**这是原型的另一个用途：防御性拷贝。**

> 《Effective Java》第 13 条干脆建议：**"谨慎地覆盖 `clone`"，优先用拷贝构造器或拷贝工厂。** 理由见 §7.2。

## 2. 原始实现：三版代码

### 2.1 v0：手动 `new` + 逐字段复制

```java
WordDocument mine = new WordDocument();
mine.setTitle(template.getTitle());
mine.setContent(template.getContent());
mine.setImages(new ArrayList<>(template.getImages()));   // 记住了要深拷贝
// 第 4 个字段呢？第 5 个呢？
```

**问题不在"麻烦"，在"责任错位"**：哪些字段要深拷贝，是 `WordDocument` 的内部知识，却由使用方来承担。任何人加一个新字段，所有复制点都要改，而编译器不会提醒。

### 2.2 v1：`super.clone()` 但不深拷贝

```java
@Override
public DocumentPrototype cloneDocument() {
    try {
        return (WordDocument) super.clone();     // 只做浅拷贝
    } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
    }
}
```

这看起来很"标准"，但它是**错的**。实测（探针输出，逐字）：

```text
[事实 1] super.clone() 是浅拷贝，可变字段仍然共享
  title 是同一个字符串吗：true（String 不可变，共享无害）
  images 是同一个 List 吗：true（危险）
  改 copy.title 后，proto.title = 模板（不受影响）
  改 copy.images 后，proto.images = [cover.png, hacked.png]  ← 被污染了
```

`super.clone()` 复制的是**字段的值**。对 `String title` 来说，"值"就是引用，但因为 `String` 不可变，共享无害。对 `List<String> images` 来说，"值"也是引用——**两个对象指向同一个 `ArrayList`**，谁改都影响对方。

**这就是原型模式最致命的一个坑**：它能编译、能跑、`toString()` 看起来也正常，只有在"改了一个发现另一个也变了"的时候才暴露。

### 2.3 v2：原项目的写法

原项目 `creationtype.PrototypePattern` 已经做对了深拷贝：

```java
@Override
public DocumentPrototype cloneDocument() {
    try {
        WordDocument copy = (WordDocument) super.clone();
        copy.images = new ArrayList<>(this.images);      // ← 关键的一行
        return copy;
    } catch (CloneNotSupportedException e) {
        throw new RuntimeException("Clone failed", e);
    }
}
```

核心逻辑是对的。但有 6 处可改进的地方——**这些都不是"bug"，是设计上的松动**：

| # | 问题 | 后果 |
|---|---|---|
| P1 | `DocumentPrototype extends Cloneable` | 把**实现细节**写进了接口契约，见 §4.5 |
| P2 | 接口返回 `DocumentPrototype`，调用方必须强转 | `Client` 里 4 处 `(WordDocument)`，见 §4.7 |
| P3 | 没有 getter | 外部**无法验证**深拷贝是否生效，只能靠 `show()` 打印肉眼看 |
| P4 | 没有注册表 | 原型最典型的用法（模板库）完全没演示 |
| P5 | `catch (CloneNotSupportedException)` 是死代码 | 因为 P1 让 `WordDocument` 必然 `Cloneable`，这个分支永不进入 |
| P6 | `show()` 写在接口里 | 一个纯演示方法成了契约的一部分，任何实现都必须实现它 |

P3 值得多说一句。**"无法验证"本身就是设计缺陷**：如果 `WordDocument` 提供 `getImages()`，就能写断言验证深拷贝；没有 getter，就只能靠看输出。测试写不出来的设计，通常是封装边界没想清楚。

### 2.4 重构版的改进

| 项 | 原项目 | 重构版 |
|---|---|---|
| 接口与 `Cloneable` | 接口 `extends Cloneable` | 接口**不继承** `Cloneable`，由 `WordDocument` 自己实现 |
| 调用方强转 | 4 处 `(WordDocument)` | 仍保留（接口的固有代价），但在 §7.3 给出泛型方案 |
| 可验证性 | 无 getter | 增加 `getTitle()` / `getImages()`，`getImages()` 返回**只读副本** |
| 注册表 | 无 | `DocumentRegistry`（注册 / 按 key 取克隆 / 预置模板） |
| 深拷贝验证 | 靠看打印 | 用**行为**验证：改克隆体，原型不变（见 §6） |
| 封装泄漏 | 无 getter，谈不上 | 有 getter，所以**必须**返回 `List.copyOf(images)` |

最后一行是本轮发现的**真实缺陷**：重构版最初写的是

```java
public List<String> getImages() { return images; }
```

这等于把内部可变集合的引用交出去——外部一句 `template.getImages().add("x")` 就能改掉原型的状态。已改为 `List.copyOf(images)`，探针验证：

```text
[事实 6] getImages() 返回只读副本，外部改不动原型
  抛 UnsupportedOperationException，原型安全
```

> **这条和"深拷贝"是同一个问题的两面**：要么在**拷贝时**隔离（`new ArrayList<>(...)`），要么在**暴露时**隔离（`List.copyOf(...)`）。只要有一个出口没堵住，封装就是漏的。

## 3. 设计动机

### 3.1 四个真实动机

| 动机 | 说明 |
|---|---|
| **构造昂贵** | 原型已经付过代价了。复制一个查过库、算过缓存的对象，比重新构造便宜得多 |
| **状态难以重建** | 对象的字段来自运行期（用户操作、外部事件、随机结果），构造器参数里根本没有这些信息 |
| **避免构造器副作用** | 构造器可能做校验、发事件、写日志。复制不该再触发一遍 |
| **运行时动态配置产品** | 注册表里存原型 → 加一种模板只是加一条数据，不改代码 |

第二条最容易被忽略，但它是原型**不可替代**的地方。工厂方法的 `createUser(name, age)` 只能表达"从参数造"，原型能表达"从**当前这个**造"。

### 3.2 什么时候**不该**用原型

**① 对象简单、构造便宜。** `new Money(100, CNY)` 复制它没有任何收益。

**② 对象不可变。** 不可变对象**直接共享**就行，不需要复制一份。`String`、`Integer`、`record`（全 `final` 且字段不可变）都属于这类。

**③ 字段很少且全是基本类型/不可变引用。** 手动 `new` 更直白，也没坑。

**④ 你其实需要"按参数造"。** 那是工厂方法。

**⑤ 你要屏蔽"造对象"这件事本身（比如远程对象、懒加载）。** 那是代理。

### 3.3 三种复制手段的边界

| 手段 | 优点 | 缺点 | 适用 |
|---|---|---|---|
| **`Object.clone()`** | 语言级支持；字段自动复制，加字段不用改代码 | `protected` + `Cloneable` 标记 + 浅拷贝 + 无构造器调用 + 不能给 `final` 字段重新赋值 | 老代码、需要 `Cloneable` 语义的场合 |
| **拷贝构造器** `new Xxx(other)` | 普通构造器，无标记接口、无受检异常、可 `final`、可校验 | 每个字段手写一行；加字段要记得改 | **推荐**，尤其新代码 |
| **序列化往返** | 天然深拷贝，不用手写每个字段 | 要求 `Serializable`；性能差（一个数量级）；`transient` 字段会丢 | 深层次对象图、临时工具 |

**本项目用 `clone()`**，因为它就是"原型模式"在 Java 里的标准形态，教学上必须讲。但 §7.2 会给出**拷贝构造器版本**——如果你要写生产代码，那个更值得抄。

## 4. 设计推导：从 v1 到最终代码

### 4.1 第 1 步：谁该负责"复制"

v0 的问题是使用方要逐字段复制。**复制逻辑必须搬进类里**，因为只有类自己知道：

- 有哪些字段（包括私有字段）；
- 哪些字段是不可变的（可以共享）；
- 哪些字段是可变引用（必须深拷贝）；
- 哪些字段是缓存/派生值（可以不复制）。

这就是"复制自己"的方法应该定义在原型上的原因。**它不是工具方法，是对象的能力。**

### 4.2 第 2 步：`Object.clone()` 的四个坑

`clone()` 是 Java 里最容易被误用的 API 之一。四个坑，每个都能实测：

**坑一：它是 `protected` 的。**

`Object.clone()` 是 `protected`，只有子类能调 `super.clone()`。外部想复制，必须暴露一个 `public` 方法（本项目的 `cloneDocument()`）。

**坑二：必须实现 `Cloneable`，否则运行时报错。**

```text
[事实 3] 没实现 Cloneable 时，super.clone() 抛 CloneNotSupportedException
  抛出了：java.lang.CloneNotSupportedException
```

`Cloneable` 是个**空标记接口**（没有任何方法）。它的唯一作用是让 `Object.clone()` 里的 `instanceof Cloneable` 检查通过。**这是 Java 里最反直觉的设计之一**：接口不提供方法，却改变父类方法的行为。

**坑三：它是浅拷贝。**

见 §2.2 实测。**"浅"的含义是：字段的值被复制，值指向的对象不被复制。**

**坑四：它不调用构造器。**

```text
[事实 2] clone 不调用构造器，构造器里的校验/防御性拷贝都会跳过
  new 1 次 + copy 2 次，构造器被调用了 1 次
```

这是**优点也是缺点**：

- 优点是"快"——不用重跑构造器里的逻辑；
- 缺点是"构造器里的保证会失效"。如果构造器做了"字段非空校验""列表包装成不可变"，`clone()` 出来的对象**跳过了这些保证**。

所以 `clone()` 出来的对象，其字段是从原型**按位复制**来的——**它继承了原型的合法性，但不重新验证合法性**。

### 4.3 第 3 步：深拷贝到底要"深"到哪一层

这是原型模式最需要判断力的地方。**没有"标准答案"，只有"哪些字段可以共享"的判断。**

```text
WordDocument
├─ String title      → 不可变，直接共享（super.clone 已经做了）
├─ String content    → 不可变，直接共享
└─ List<String> images
   ├─ List 对象本身  → 必须新建（否则 add 会互相影响）
   └─ 元素 String    → 不可变，共享无害
```

所以 `new ArrayList<>(this.images)` 就够了——**新建容器，复用元素**。不需要连元素也复制。

如果字段是 `List<Image>` 而 `Image` 可变呢？

```java
copy.images = this.images.stream().map(Image::copy).collect(Collectors.toList());
```

**"深"到哪一层，取决于哪一层的对象是可变的。** 判断方法：问自己"如果 A 和 B 共享这个对象，A 改了会不会影响 B？"会，就必须复制到那一层。

### 4.4 第 4 步：`final` 字段与 `clone()` 的冲突

这一步是"拷贝构造器优于 `clone()`"的**硬证据**。

想让 `images` 不可变？第一反应是加 `final`：

```java
private final List<String> images = new ArrayList<>();
```

然后在 `clone()` 里深拷贝：

```java
copy.images = new ArrayList<>(this.images);      // ← 编译失败
```

实测编译输出（错误原文）：

```text
PrototypeFinalProbe.java:16: 错误: 无法为 final 变量 images 分配值
            copy.images = new ArrayList<>(this.images);
                ^
1 个错误
```

**结论**：

| 需求 | `clone()` 方案 | 拷贝构造器方案 |
|---|---|---|
| 字段 `final` | ❌ 不能在 `clone()` 里替换 | ✅ 在新对象的构造器里赋值，天然合法 |
| 无受检异常 | ❌ `CloneNotSupportedException` | ✅ |
| 无标记接口 | ❌ 必须 `implements Cloneable` | ✅ |
| 加字段自动覆盖 | ✅ | ❌ 要手写 |
| 可校验 | ❌ 不调构造器，无法校验 | ✅ 构造器里可以校验 |

**所以：`clone()` 赢在"加字段不用改代码"，输在其他所有维度。** 现代 Java 更推荐拷贝构造器（§7.2）。

本项目仍用 `clone()`，因为这是"原型模式"的标准讲法。**但文档必须把这条代价写清楚，否则读者会以为 `clone()` 是好设计。**

### 4.5 第 5 步：接口要不要 `extends Cloneable`

原项目写法：

```java
public interface DocumentPrototype extends Cloneable {
    DocumentPrototype cloneDocument();
}
```

重构版写法：

```java
public interface DocumentPrototype {
    DocumentPrototype cloneDocument();
}

public class WordDocument implements DocumentPrototype, Cloneable { ... }
```

**为什么改？三个理由：**

**① `Cloneable` 是"怎么实现"，不是"能做什么"。**

`DocumentPrototype` 表达的是"这个对象能被复制"——这是**能力**。`Cloneable` 表达的是"这个对象用 `Object.clone()` 实现复制"——这是**实现方式**。接口里混进实现方式，等于把实现锁死了。

如果哪天 `WordDocument` 改用拷贝构造器实现 `cloneDocument()`，它就不需要 `Cloneable` 了——但接口逼着它保留这个标记，纯属负担。

**② 接口继承 `Cloneable` 会"传染"给所有实现。**

任何人 `implements DocumentPrototype` 就自动成了 `Cloneable`。这**削弱了这个标记的意义**——`Cloneable` 本来应该由"我确实支持 `super.clone()`"的类主动声明。

**③ 它让 `catch (CloneNotSupportedException)` 变成死代码。**

原项目里：

```java
try {
    WordDocument copy = (WordDocument) super.clone();
    ...
} catch (CloneNotSupportedException e) {
    throw new RuntimeException("Clone failed", e);     // 永远进不来
}
```

因为 `WordDocument implements DocumentPrototype extends Cloneable`，所以它必然是 `Cloneable`，`super.clone()` 必然成功。**这段 `catch` 无法被测试覆盖，也无法被删掉（编译器要求处理受检异常）。** 这是"接口设计不当"直接导致的死代码。

> 重构版保留了 `catch`（因为编译器要求），但改成 `throw new AssertionError(...)`——**语义上是"这不可能发生"**，而不是"这可能发生，包成 RuntimeException 交给你处理"。这是一个小但重要的区别。

### 4.6 第 6 步：为什么需要注册表

```java
public class DocumentRegistry {
    private final Map<String, DocumentPrototype> prototypes = new HashMap<>();

    public void register(String key, DocumentPrototype prototype) {
        prototypes.put(key, prototype);
    }

    public DocumentPrototype get(String key) {
        DocumentPrototype prototype = prototypes.get(key);
        if (prototype == null) {
            throw new IllegalArgumentException("未注册的原型：" + key);
        }
        return prototype.cloneDocument();     // 返回的是克隆体，不是原型本身
    }
}
```

三个设计点：

**① `get()` 返回克隆体，不返回原型。**

如果直接 `return prototype`，调用方改它就把注册表里的原型改坏了，**后面所有克隆都从被污染的原型出发**。这一行 `cloneDocument()` 是整个注册表安全性的关键。

**② 未注册时抛 `IllegalArgumentException`，不返回 `null`。**

返回 `null` 会让错误推迟到调用方 `NPE`，位置离原因很远。**"key 不存在"是一个调用错误，应该在调用点立刻爆出来。**

**③ `withDefaults()` 是静态工厂，不是构造器。**

它只是"预置两条数据"的便捷方法，不需要访问实例状态。放在这里让 `Client` 一行就能拿到可用的注册表。

**注册表的价值**：把"可用的产品类型"从**代码**（`switch` / `new`）变成了**数据**（`Map` 里的一条记录）。加一种模板不用改任何代码——这是原型模式相对工厂方法的一个真实优势。

### 4.7 第 7 步：4 处强转能不能消除

当前写法：

```java
WordDocument doc1 = (WordDocument) template.cloneDocument();     // 强转
WordDocument myResume = (WordDocument) registry.get("resume");    // 强转
```

`cloneDocument()` 返回 `DocumentPrototype`，但使用方想要 `WordDocument`。**这是"接口返回宽类型"的固有代价。**

四条出路：

| 方案 | 写法 | 代价 |
|---|---|---|
| **① 保持强转**（本项目） | `(WordDocument) x.cloneDocument()` | 4 处强转；类型错误在运行期才发现 |
| **② 协变返回** | `WordDocument` 里覆写为 `public WordDocument cloneDocument()` | 走接口引用调用时仍要强转，只在直接持有 `WordDocument` 时有用 |
| **③ 泛型自类型** | `interface Prototype<T> { T copy(); }` | 无强转，但泛型签名变复杂（§7.3） |
| **④ 注册表泛型化** | `DocumentRegistry<T extends DocumentPrototype>` | 注册表能给出精确类型，但一个表只能存一种类型 |

**本项目选 ①**，因为这是最朴素的写法，也最接近原项目。但**面试里能说出 ③ 是加分项**——它证明你理解"类型信息在接口边界上丢失"这个问题。

## 5. 代码解析

### 5.1 `DocumentPrototype`——抽象原型

```java
public interface DocumentPrototype {

    /**
     * 克隆当前文档，返回一个深拷贝副本。
     */
    DocumentPrototype cloneDocument();

    void show();
}
```

两个方法，各有一个问题值得说：

**① 为什么叫 `cloneDocument()` 而不叫 `clone()`？**

避免和 `Object.clone()` 混淆。`Object.clone()` 有特定的签名、特定的语义（浅拷贝、`Cloneable` 检查）、特定的异常。叫 `clone()` 会让人以为"这就是 `Object.clone()` 的覆写"，但实际上它是**另一个方法**（返回类型、异常声明都不同）。

**② `show()` 该不该在接口里？**

严格说不该。`show()` 是**演示需求**，不是原型模式的能力。真实项目里它可能叫 `render()` 或干脆不存在。

本项目保留了它（对齐原项目），但**这是一个已知的设计妥协**——如果重写，我会把打印逻辑放 `Client` 里，接口只留 `cloneDocument()`。

### 5.2 `WordDocument`——具体原型

```java
public class WordDocument implements DocumentPrototype, Cloneable {

    private String title;
    private String content;
    /** 可变引用类型字段，克隆时必须深拷贝。 */
    private List<String> images;

    public WordDocument(String title, String content, List<String> images) {
        this.title = title;
        this.content = content;
        // 构造时也做防御性拷贝，避免外部修改影响内部状态
        this.images = new ArrayList<>(images);
    }
```

**注意构造器里的 `new ArrayList<>(images)`**——这是**防御性拷贝**，和 `clone()` 里的深拷贝是两件事：

| 位置 | 防的是谁 |
|---|---|
| **构造器**里的 `new ArrayList<>(images)` | 防**调用方**保留 `images` 引用后偷偷改 |
| **`clone()`** 里的 `new ArrayList<>(this.images)` | 防**克隆体**和**原型**共享同一个 List |

两个都要有。少一个，就有一个方向能污染。

```java
    @Override
    public DocumentPrototype cloneDocument() {
        try {
            WordDocument copy = (WordDocument) super.clone();
            // 深拷贝可变引用字段
            copy.images = new ArrayList<>(this.images);
            return copy;
        } catch (CloneNotSupportedException e) {
            // 本类已实现 Cloneable，理论上不会抛出
            throw new AssertionError("Clone failed: " + e.getMessage());
        }
    }
```

逐行解释：

| 行 | 为什么 |
|---|---|
| `(WordDocument) super.clone()` | `super.clone()` 返回 `Object`，必须强转。强转是安全的——`super.clone()` 返回的是**运行期类型**的实例 |
| `copy.images = new ArrayList<>(this.images)` | 唯一的深拷贝点。`title` / `content` 是 `String`，共享无害 |
| `catch` 里抛 `AssertionError` | 语义是"这个分支不可能进入"。比包成 `RuntimeException` 更诚实 |

**为什么 `images` 不是 `final`？** 见 §4.4——`final` 字段不能在 `clone()` 里重新赋值，编译器直接报错。

```java
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void addImage(String image) { this.images.add(image); }

    public String getTitle() { return title; }

    /**
     * 返回图片清单的只读副本。
     */
    public List<String> getImages() { return List.copyOf(images); }
```

`getImages()` 返回 `List.copyOf(images)`——**这是本轮修掉的一个真实缺陷**（§2.4）。返回 `images` 本身，等于开了一个后门：外部 `getImages().add("x")` 直接改原型，所有克隆都受影响。

**代价**：每次调用都新建一个 List。对这种"演示级"访问频率完全可接受；高频场景可以缓存一个 `Collections.unmodifiableList(images)` 视图（但那样外部仍能看到变化，只是不能改——语义不同，要按需选）。

### 5.3 `DocumentRegistry`——原型注册表

见 §4.6 的三个设计点。这里补一个：**注册表本身不做深拷贝。**

```java
public void register(String key, DocumentPrototype prototype) {
    prototypes.put(key, prototype);      // 存的是引用，不是副本
}
```

为什么？因为原型本来就该是"不被外部修改的样板"。如果 `register` 也复制一份，那"注册后再改原型"就无效了——而"改原型让后续克隆都生效"恰恰是原型模式想要的（策划改哥布林原型，新刷的怪立刻生效）。

**这是一种有意的设计选择**，代价是：如果调用方注册后又改了那个原型对象，注册表里的内容会跟着变。文档里必须写清楚。

### 5.4 `Client`——三段演示

| 场景 | 演示什么 |
|---|---|
| 场景 1 | 从模板克隆两份，分别编辑，**原件不受影响** |
| 场景 2 | 注册表按 key 取克隆，两个不同模板互不干扰 |
| 场景 3 | **用行为证明深拷贝生效**：改 `doc1` 的图片，`template` 的图片不变 |

场景 3 的写法值得单独说。它最初是这么写的：

```java
System.out.println("template.images == doc1.images ? "
        + (template.getImages() == doc1.getImages()));
```

**这个断言没有意义**：`getImages()` 返回 `List.copyOf(images)`，每次调用都是新对象，`==` **永远**是 `false`——即使两者真的共享底层数据也一样。**永远成立的断言，证明不了任何事。**

改成用行为验证：

```java
doc1.addImage("new_image.png");
System.out.println("加图后 template：" + template.getImages());
System.out.println("（template 仍为 [cover.png]，说明两者的 images 互不影响）");
```

**"改 A，看 B"才是复制独立性的真证据。** 这个道理适用于所有"深拷贝是否生效"的验证。

## 6. 执行流程

以"从模板克隆出鱼皮的简历"为例：

```text
1. new WordDocument("招聘简历模板", "请填写个人信息", images)
     ├─ this.images = new ArrayList<>(images)     ← 防御性拷贝：与外部 images 断开
     └─ 此刻 title/content 是 String（不可变），images 是独立的 ArrayList

2. template.cloneDocument()
     ├─ super.clone()
     │    └─ 逐字段复制引用：title、content、images 三个引用被复制
     │       → 新对象与 template 指向同一个 ArrayList（浅拷贝）
     ├─ copy.images = new ArrayList<>(this.images)
     │    └─ 新建一个 ArrayList，把元素（String 引用）复制进去
     │       → 此刻 copy 与 template 的 images 已完全分离
     └─ return copy

3. doc1.setTitle("鱼皮的简历")       → 只改 copy 的字段，template 不受影响
4. doc1.addImage("yupi_avatar.png") → 只往 copy 的 ArrayList 加，template 的不变

5. template.cloneDocument()（第二次）
     └─ 又从**原件**复制，所以 doc2 里没有 yupi_avatar.png
```

**关键点**：第 5 步非常容易被误解。`doc1` 和 `doc2` 都是**从 template 克隆的**，不是从上一个克隆体克隆的。所以两次克隆互不影响。

**如果写成 `doc2 = doc1.cloneDocument()` 呢？** 那 `doc2` 会带着 `doc1` 的 `yupi_avatar.png`——这是"链式克隆"，语法上完全合法，语义上通常是 bug。**原型模式很容易写出这种"看起来对"的代码。**

### 6.1 完整输出（37 行，逐字）

```text
========== 原型模式演示 ==========

--- 场景 1：直接克隆 ---
== 原始模板 ==
Title: 招聘简历模板
Content: 请填写个人信息
Images: [cover.png]

== 克隆出的鱼皮的简历 ==
Title: 鱼皮的简历
Content: 鱼皮，5 年全栈开发经验
Images: [cover.png, yupi_avatar.png]

== 克隆出的 Yes 哥的简历 ==
Title: Yes 哥的简历
Content: Yes 哥，8 年 Java 开发经验
Images: [cover.png, yes_avatar.png]

== 原始模板（未被污染）==
Title: 招聘简历模板
Content: 请填写个人信息
Images: [cover.png]

--- 场景 2：原型注册表 ---
Title: 张三的简历
Content: 张三，3 年后端经验
Images: [cover.png]
Title: 第 42 周周报
Content: 完成设计模式重构
Images: [logo.png]

--- 场景 3：深拷贝验证 ---
加图前 template：[cover.png]
加图前 doc1    ：[cover.png, yupi_avatar.png]
加图后 doc1    ：[cover.png, yupi_avatar.png, new_image.png]
加图后 template：[cover.png]
（template 仍为 [cover.png]，说明两者的 images 互不影响）
```

**看第 33 行和第 34 行的对比**：`doc1` 加了三张图，`template` 始终只有 `[cover.png]`。这就是深拷贝生效的**行为证据**。

### 6.2 复现与哈希

```bash
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/prototype/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.prototype.Client > run.txt
```

| 指标 | 值 |
|---|---|
| 退出码 | 0 |
| 行数 | 37 |
| 字节数 | 1073（UTF-8 口径，含 CRLF） |
| SHA-256（前 16 位） | `7412EB2B5CA64B95` |
| 确定性 | 连跑两次哈希一致 |

> 不加 `-Dstdout.encoding=UTF-8` 时 Windows 默认 GBK，中文占 2 字节，字节数与哈希都会变，**行数不变**。详见 `docs/05-verification.md` §4.1.1。

## 7. 扩展对比

### 7.1 新增 `PdfDocument`：1 个类，0 处修改

```java
public class PdfDocument implements DocumentPrototype, Cloneable {

    private String title;
    private List<byte[]> pages;      // 元素也是可变的，必须逐个复制

    @Override
    public DocumentPrototype cloneDocument() {
        try {
            PdfDocument copy = (PdfDocument) super.clone();
            copy.pages = new ArrayList<>(this.pages.size());
            for (byte[] page : this.pages) {
                copy.pages.add(page.clone());        // 数组也要复制
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void show() { ... }
}
```

**注意 `byte[]` 的复制**：`new ArrayList<>(this.pages)` 只复制了"数组引用的列表"，每个 `byte[]` 还是共享的。**"深"到哪一层取决于哪一层可变**（§4.3）——这里是两层。

这就是为什么"复制逻辑必须放在类里"：**只有 `PdfDocument` 知道自己的 `pages` 里装的是可变数组。**

### 7.2 拷贝构造器替代 `clone()`（更推荐的写法）

```java
public final class WordDocument {

    private final String title;
    private final String content;
    private final List<String> images;

    public WordDocument(String title, String content, List<String> images) {
        this.title = Objects.requireNonNull(title);
        this.content = content;
        this.images = new ArrayList<>(images);
    }

    /** 拷贝构造器：从已有实例复制。 */
    public WordDocument(WordDocument other) {
        this.title = other.title;
        this.content = other.content;
        this.images = new ArrayList<>(other.images);     // 深拷贝
    }

    public WordDocument copy() {
        return new WordDocument(this);
    }
}
```

对比 `clone()`：

| 维度 | `clone()` | 拷贝构造器 |
|---|---|---|
| 字段 `final` | 做不到 | 可以 |
| 受检异常 | `CloneNotSupportedException` | 无 |
| 标记接口 | 需要 `Cloneable` | 不需要 |
| 构造器校验 | 跳过 | 执行 |
| 加字段忘改 | 不会忘（自动复制引用） | **会忘**——需要测试兜住 |
| 子类支持 | 有坑（`super.clone()` 返回子类但字段未初始化） | 需要子类自己的拷贝构造器 |

**"加字段忘改"这一条是真风险。** 缓解办法：写一个"复制一致性测试"——遍历所有 getter，断言 `copy.getX().equals(original.getX())`，且可变字段 `!=`。

本项目为了对齐"原型模式"的经典讲法用了 `clone()`，**但生产代码我建议用拷贝构造器**。

### 7.3 泛型自类型消灭 4 处强转

```java
public interface Prototype<T extends Prototype<T>> {
    T copy();
}

public class WordDocument implements Prototype<WordDocument> {
    @Override
    public WordDocument copy() {
        WordDocument copy = new WordDocument(this);     // 用拷贝构造器
        return copy;
    }
}

// 使用方：零强转
WordDocument doc1 = template.copy();
```

| 项 | 接口返回宽类型 + 强转 | 泛型自类型 |
|---|---|---|
| 调用点 | `(WordDocument) x.cloneDocument()` | `x.copy()` |
| 类型安全 | 运行期 `ClassCastException` | 编译期保证 |
| 可读性 | 强转是噪音 | 干净 |
| 复杂度 | 简单 | `T extends Prototype<T>` 需要理解自引用泛型 |

**代价**：`Prototype<T>` 这个签名对不熟悉泛型的人是障碍；而且它要求每个实现类都写自己的类型参数，加一个类要多写一处。

**决策依据**：调用点多（≥5 处）、类型经常搞错 → 上泛型；只有一两种文档、调用点少 → 保持强转。

### 7.4 序列化做深拷贝

```java
@SuppressWarnings("unchecked")
public static <T extends Serializable> T deepCopy(T obj) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(bos)) {
        oos.writeObject(obj);
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray()))) {
            return (T) ois.readObject();
        }
    } catch (IOException | ClassNotFoundException e) {
        throw new IllegalStateException("深拷贝失败", e);
    }
}
```

| 优点 | 缺点 |
|---|---|
| 不用手写每个字段的复制 | 要求整条引用链都 `Serializable` |
| 天然处理任意深度的对象图 | 性能差（一个数量级起步） |
| 处理循环引用 | `transient` 字段会变 `null` |

**什么时候值得用**：对象图很深、嵌套层次多、字段经常变，且性能不在关键路径上。**什么时候不值得**：本项目这种 3 个字段的文档——手写一行 `new ArrayList<>` 快得多，也清楚得多。

### 7.5 `record` 与不可变对象：根本不需要原型

```java
public record Document(String title, String content, List<String> images) {
    public Document {
        images = List.copyOf(images);     // 紧凑构造器里做防御性拷贝
    }
}
```

`record` 全字段 `final`，`images` 用 `List.copyOf` 变成不可变——**这样的对象直接共享就行，不需要复制。**

```java
Document a = new Document("简历", "内容", List.of("cover.png"));
Document b = a;                        // 共享，安全
Document c = new Document("简历", "新内容", a.images());   // 只改想改的字段
```

**这是"原型模式什么时候不需要"的最好回答**：对象不可变时，"复制"和"共享"没有区别，复制的成本白花了。

`record` 的 `with` 风格（Java 没有内建，但可以手写）：

```java
public Document withTitle(String newTitle) {
    return new Document(newTitle, content, images);
}
```

这是"只改一个字段"的现代写法，比"克隆再 set"更安全（不会忘记复制某个字段）。

### 7.6 原型 vs 工厂：一个具体对比

同一个需求"创建简历文档"，两种写法：

```java
// 工厂：从参数造
Document resume = factory.create("简历模板", "请填写个人信息", List.of("cover.png"));

// 原型：从实例复制
Document resume = template.cloneDocument();
```

| 维度 | 工厂 | 原型 |
|---|---|---|
| 输入 | 参数（类 + 配置） | **一个已存在的实例** |
| 支持运行期状态 | 不支持（参数里没有） | 支持 |
| 加一种产品 | 新增类（工厂方法）或改 `switch`（简单工厂） | 注册表加一条数据 |
| 构造器副作用 | 会触发 | **不触发**（`clone()` 不调构造器） |
| 拷贝深度问题 | 没有 | **有**（这是原型的核心难点） |

**关键区别一句话**：工厂回答"给我一个 X"，原型回答"给我一个和它一样的"。

### 7.7 与五个模式的分界

| 模式 | 关注点 | 与原型的分界 |
|---|---|---|
| **工厂方法** | 造**哪个类** | 工厂用类创建，原型用实例创建 |
| **抽象工厂** | 造**一族** | 抽象工厂一次给多个产品；原型复制一个 |
| **建造者** | **怎么分步组装** | 建造者从零拼装；原型复制现成的 |
| **单例** | 保证**只有一个实例** | 原型要多少份复制多少份，恰好相反 |
| **备忘录** | **保存/恢复状态** | 结构极像（都存状态快照），目的不同：备忘录为了**回滚**，原型为了**复制** |

**原型 vs 备忘录**是最容易混的一对。两者都"复制一份状态"，区别在**拿去干什么**：

- 备忘录：把状态存起来，之后**恢复到自己身上**（`undo`）；
- 原型：把状态复制出去，**当成一个新对象用**。

`Memento` 通常由 `Originator` 内部创建且对外不透明；`Prototype` 复制出来的对象就是完整可用的产品。

## 8. 优缺点与误区

### 8.1 优点

1. **绕过构造器**。构造昂贵的对象（查库、解析、计算）只付一次代价。
2. **能复制运行期状态**。这是工厂做不到的——参数里根本没有这些信息。
3. **不触发构造器副作用**。不发事件、不写日志、不重跑校验。
4. **复制逻辑就近封装**。哪些字段要深拷贝，由类自己决定，不用使用方操心。
5. **注册表让产品种类变成数据**。加一种模板不改代码。
6. **可用于防御性拷贝**。`getXxx()` 返回 `field.clone()`，防止外部改内部状态。

### 8.2 缺点

1. **深拷贝必须手写，且容易漏**。`byte[][]`、`List<List<String>>`、自引用对象图，每一层都要判断（§4.3）。
2. **`clone()` 的机制很别扭**。`protected` + `Cloneable` + 受检异常 + 不调构造器 + 与 `final` 冲突。
3. **`@Override` 不能省**。名字或签名写错（如写成 `Clone()`、`clone(Object)`）不会报错，会静默变成一个无关的新方法，`clone()` 永远不会被调用。
4. **接口边界丢类型信息**。`cloneDocument()` 返回宽类型，调用方要强转（§4.7）。
5. **循环引用会让"朴素深拷贝"栈溢出**。手写递归复制必须处理已访问对象。
6. **可变原型是共享风险**。注册表里的原型被改了，后续所有克隆都继承污染（§5.3）。

### 8.3 十个误区

| 误区 | 真相 |
|---|---|
| "`super.clone()` 就是复制好了" | 它是**浅拷贝**。可变字段仍然共享（§2.2 实测 `images` 是同一个 List） |
| "`String` / `Integer` 也要深拷贝" | 不用。它们不可变，共享无害。**只复制可变的那一层** |
| "原型模式一定要用 `clone()`" | 不一定。拷贝构造器、序列化往返都能实现原型语义，而且拷贝构造器更好（§7.2） |
| "`Cloneable` 是个接口所以有 `clone()` 方法" | 它是**空标记接口**，不声明任何方法。它的作用只是让 `Object.clone()` 的运行时检查通过 |
| "`clone()` 会调用构造器" | 不会。所以构造器里的校验、防御性拷贝**全部被跳过**（§4.2 实测构造器只被调了 1 次） |
| "字段声明 `final` 更安全，反正 clone 能复制" | `final` 字段**不能在 `clone()` 里重新赋值**，编译直接报错（§4.4 实测） |
| "接口 `extends Cloneable` 更好，实现类就自动 Cloneable 了" | 这是把实现细节写进契约，还让 `catch` 变成死代码（§4.5） |
| "注册表返回原型更快" | 返回原型是**共享**，调用方一改就污染全局。必须返回克隆体 |
| "原型和备忘录差不多" | 备忘录为了**回滚到自己**，原型为了**复制给别人**（§7.7） |
| "对象不可变，所以我要克隆它" | 不可变对象**直接共享**，克隆纯属浪费 |

### 8.4 出现这些信号，说明你在滥用原型

- 对象只有 2~3 个不可变字段 → 直接 `new`，或者用 `record` 共享。
- 类里写了 `clone()`，但**没有任何可变引用字段** → 那就是个 `new`，没必要走 `Cloneable`。
- 为了复制而复制，复制完立刻把大部分字段覆盖掉 → 你要的是"带默认值的工厂"，不是原型。
- `clone()` 里出现 `// TODO 这里的字段还没复制` → 深拷贝漏了，赶紧补。
- 注册表里存的是可变的、会被外部改的原型 → 要么返回克隆体，要么把原型做成不可变。
- `getXxx()` 直接返回内部可变集合 → 这不是原型问题，是**封装漏洞**（§2.4 的真实缺陷）。

## 9. 面试与自测

### 9.1 30 秒版

> 原型模式用一个已存在的实例当模板，通过复制它来创建新对象，而不是 `new`。Java 里的标准实现是 `Cloneable` + `Object.clone()`。它解决的是"构造昂贵"和"要复制运行期状态"这两类问题——后者是工厂做不到的。它的核心难点是深拷贝：`super.clone()` 只复制字段引用，可变字段（比如 `List`）会和原型共享，必须手动替换成新容器。工程上常见形态是"原型注册表"：把原型存进 `Map`，按 key 取出来克隆。

### 9.2 2 分钟版

> 我拿文档模板举例。用户从模板库选一个模板，系统给一份可编辑的副本，原件不能受影响。`WordDocument` 有三个字段：标题、正文、图片列表。
>
> 关键在深拷贝。`super.clone()` 是逐字段的内存复制——对 `String` 这种不可变类型没问题，但 `List<String> images` 复制后两个对象指向**同一个** `ArrayList`。我实测过：克隆体 `add` 一张图，原型的列表里也出现了那张图。所以 `cloneDocument()` 里必须补一行 `copy.images = new ArrayList<>(this.images)`。
>
> 还有个细节：`clone()` 不调用构造器。我测过，"new 一次 + copy 两次"，构造器只被调了 1 次。这意味着构造器里做的校验、防御性拷贝全部被跳过——克隆体继承原型的合法性，但不重新验证。
>
> 原项目有 6 处可改进：接口 `extends Cloneable` 把实现细节写进契约、调用方 4 处强转、没有 getter 所以深拷贝无法验证、没有注册表、`catch` 是死代码、`show()` 混进了接口。重构版把这 6 点都处理了，还加了一个真实缺陷的修复：`getImages()` 原来直接返回内部 `List`，外部一句 `add` 就能改原型，现在返回 `List.copyOf`。
>
> 最后一句实话：`clone()` 的机制并不好——它和 `final` 字段冲突（我实测编译报错"无法为 final 变量赋值"），还有受检异常和标记接口。生产代码我更推荐拷贝构造器 `new WordDocument(other)`，`clone()` 更适合当教学案例。

### 9.3 追问链

| 追问 | 要点 |
|---|---|
| 原型 vs 工厂方法 | 工厂用**类 + 参数**创建；原型用**已存在的实例**创建 |
| 原型 vs 建造者 | 建造者从零分步组装；原型复制现成状态 |
| 原型 vs 备忘录 | 都存状态快照；备忘录为了**回滚到自己**，原型为了**复制给别人** |
| `Cloneable` 是什么 | 空标记接口，不声明方法。作用是让 `Object.clone()` 的 `instanceof` 检查通过 |
| `super.clone()` 为什么叫浅拷贝 | 它逐字段复制**引用值**，引用指向的对象不复制 |
| 深拷贝要"深"到哪 | 到"可变的那一层"为止。`List<String>` 只需新建 List，元素可共享；`List<byte[]>` 还要复制每个数组 |
| `clone()` 调用构造器吗 | 不调用。校验和防御性拷贝会被跳过 |
| 为什么 `final` 字段和 `clone()` 冲突 | `final` 字段只能在构造器/初始化器里赋值，`clone()` 里给它赋新值编译不通过 |
| 为什么不推荐 `clone()` | 受检异常、标记接口、浅拷贝陷阱、与 `final` 冲突、不调构造器 → 《Effective Java》第 13 条建议用拷贝构造器 |
| 注册表为什么返回克隆体 | 返回原型等于共享，调用方一改就污染后续所有克隆 |
| 循环引用怎么深拷贝 | 手写递归复制时用 `IdentityHashMap` 记录已复制对象；或用序列化往返 |
| Spring 里哪里有原型 | `@Scope("prototype")` 每次注入都创建新实例——但那是**容器创建**，不是 `clone()`，属于"用工厂实现原型语义" |

### 9.4 自测题

**1. 下面这段代码有什么问题？**

```java
@Override
public DocumentPrototype cloneDocument() {
    try {
        return (WordDocument) super.clone();
    } catch (CloneNotSupportedException e) {
        throw new AssertionError(e);
    }
}
```

<details><summary>参考答案</summary>

**只做了浅拷贝。** `super.clone()` 复制字段引用，`images`（`List<String>`）会和原型指向同一个 `ArrayList`——克隆体 `add` 一张图，原型的列表里也会出现。实测证据：`images 是同一个 List 吗：true`，改克隆体后 `proto.images = [cover.png, hacked.png]`。

修法：`copy.images = new ArrayList<>(this.images);`。

注意 `title` / `content` 是 `String`，**不需要**深拷贝——它们不可变，共享无害。**只复制可变的那一层。**
</details>

**2. `Cloneable` 接口里有几个方法？它的作用是什么？**

<details><summary>参考答案</summary>

**一个方法都没有。** 它是空标记接口（marker interface）。

它的唯一作用是让 `Object.clone()` 里的运行时检查通过：`if (!(this instanceof Cloneable)) throw new CloneNotSupportedException();`。没实现 `Cloneable` 就调 `super.clone()`，实测会抛 `java.lang.CloneNotSupportedException`。

顺便说：这是 Java 里最反直觉的设计之一——**接口不提供任何方法，却改变了父类方法的行为**。
</details>

**3. `clone()` 会调用构造器吗？这有什么后果？**

<details><summary>参考答案</summary>

**不会。** 实测："new 1 次 + copy 2 次，构造器被调用了 1 次"。

后果有三：
1. **构造器里的校验不执行**——原型合法，克隆体继承合法性，但不重新验证；
2. **构造器里的防御性拷贝不执行**——`this.images = new ArrayList<>(images)` 这行不会跑；
3. **构造器里的副作用不发生**——不发事件、不写日志（这一条通常是好事）。

所以凡是"构造器保证了某种不变量"的类，`clone()` 都必须自己重新保证一遍。
</details>

**4. 为什么本项目的 `images` 字段不是 `final`？**

<details><summary>参考答案</summary>

因为 **`final` 字段不能在 `clone()` 里重新赋值**，编译器直接报错。实测错误原文：

```text
PrototypeFinalProbe.java:16: 错误: 无法为 final 变量 images 分配值
            copy.images = new ArrayList<>(this.images);
                ^
```

这是 `clone()` 机制的硬限制：`Object.clone()` 确实会复制 `final` 字段（内存级复制），但**深拷贝需要"替换掉那个引用"**，而 `final` 不允许。

想同时要"字段 `final`"和"深拷贝"，只能用**拷贝构造器**：`new WordDocument(other)` 里 `this.images = new ArrayList<>(other.images)` 是构造器赋值，完全合法。**这是拷贝构造器优于 `clone()` 的关键理由之一。**
</details>

**5. 接口该不该 `extends Cloneable`？**

<details><summary>参考答案</summary>

**不该。** 三个理由：

1. **实现细节泄漏**：`DocumentPrototype` 表达的是"能被复制"这个**能力**，`Cloneable` 表达的是"用 `Object.clone()` 实现"这个**实现方式**。混在一起就把实现锁死了。
2. **传染**：任何 `implements DocumentPrototype` 的类都自动成为 `Cloneable`，削弱了这个标记的语义。
3. **产生死代码**：`WordDocument` 必然 `Cloneable`，所以 `catch (CloneNotSupportedException)` 永远不执行，却又不能删（受检异常要求处理）。

正确做法：接口只声明 `cloneDocument()`，`Cloneable` 由具体类自己 `implements`。
</details>

**6. 注册表的 `get()` 为什么必须返回克隆体，不能返回原型？**

<details><summary>参考答案</summary>

因为返回原型等于**把原型本身交给调用方**，调用方一改（`setTitle` / `addImage`），注册表里的原型就被污染了——**之后所有克隆都从被污染的原型出发**。

实测验证了正确行为："a 改成 A 的简历 + 加了 a.png，但 b 拿到的仍是 '简历模板' + [cover.png]"。

代价：每次 `get()` 都要付一次克隆成本。这是必要的代价——**要么每次克隆，要么保证原型不可变**，没有第三条路。
</details>

**7. 深拷贝要"深"到第几层？怎么判断？**

<details><summary>参考答案</summary>

判断标准是：**"如果两个对象共享这个字段，改一个会不会影响另一个？"** 会，就必须复制到那一层；不会（因为不可变），就可以共享。

三个例子：

| 字段类型 | 要复制到哪一层 |
|---|---|
| `List<String>` | 只新建 List，`String` 元素共享 |
| `List<byte[]>` | 新建 List，**每个 `byte[]` 还要 `clone()`** |
| `Map<String, List<String>>` | 新建 Map，**每个 value 的 List 还要新建** |

**不要盲目"全深拷贝"**——不可变对象复制是纯浪费。也不要"只 `super.clone()`"——那是 bug（§2.2）。
</details>

**8. 手写题：用序列化实现通用深拷贝，写出方法签名和核心代码。**

<details><summary>参考答案</summary>

```java
@SuppressWarnings("unchecked")
public static <T extends Serializable> T deepCopy(T obj) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(bos)) {
        oos.writeObject(obj);
        try (ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray()))) {
            return (T) ois.readObject();
        }
    } catch (IOException | ClassNotFoundException e) {
        throw new IllegalStateException("深拷贝失败", e);
    }
}
```

**前提**：整条引用链都要 `Serializable`。
**代价**：性能差一个数量级；`transient` 字段会变 `null`。
**适用**：对象图很深、字段经常变、不在性能关键路径上。本项目 3 个字段的文档用手写一行更快更清楚。
</details>

**9. 判断对错：`record` 对象也需要原型模式来复制。**

<details><summary>参考答案</summary>

**错。** `record` 的所有字段都是 `final`；如果字段本身也是不可变的（`String`、基本类型、`List.copyOf` 的结果），那这个对象**完全不可变**——**不可变对象直接共享就行，不需要复制**。

```java
Document a = new Document("简历", "内容", List.of("cover.png"));
Document b = a;                   // 共享，安全，零成本
```

如果只想改一个字段，用 `with` 风格方法（`return new Document(newTitle, content, images)`）比"克隆再改"更安全——**不会忘记复制某个字段**。

只有当 `record` 里含可变字段（比如 `int[]`）时，才需要考虑复制。这时仍建议用拷贝构造器/静态工厂，而不是 `clone()`。
</details>

**10. 本项目 `getImages()` 为什么返回 `List.copyOf(images)` 而不是 `images`？**

<details><summary>参考答案</summary>

因为返回 `images` 本身等于**把内部可变集合的引用交出去**，外部一句 `template.getImages().add("x")` 就能改掉原型的状态——而且这个修改会传播给之后所有克隆体。

实测验证修复效果：
```text
[事实 6] getImages() 返回只读副本，外部改不动原型
  抛 UnsupportedOperationException，原型安全
```

**这和深拷贝是同一个问题的两面**：要么在**拷贝时**隔离（`new ArrayList<>(...)`），要么在**暴露时**隔离（`List.copyOf(...)`）。只要有一个出口没堵住，封装就是漏的。

代价：每次调用都新建一个 List。高频场景可以缓存一个 `unmodifiableList` 视图，但语义不同（视图能看到后续变化，只是不能改），要按需求选。
</details>

### 9.5 面试官想听什么

**想听**：

- "`super.clone()` 是浅拷贝，可变字段要手动深拷贝"——说明你知道最大的坑。
- "`clone()` 不调用构造器"——说明你理解它和 `new` 的本质差异。
- "深拷贝要到'可变的那一层'，不可变对象不用复制"——说明你不是无脑全深拷贝。
- "拷贝构造器比 `clone()` 好，因为 `final`、受检异常、标记接口"——说明你有取舍判断。
- "注册表必须返回克隆体，否则原型被污染"——说明你考虑过共享风险。

**不想听**：

- 只背"用原型实例指定创建对象的种类，通过拷贝创建新对象"。
- 认为 `clone()` 是深拷贝。
- 说不出 `Cloneable` 是个空接口。
- 认为原型只能靠 `clone()` 实现。
- 说"原型模式性能好"但说不出好在哪（应该是"省掉重复构造"，不是"复制比 new 快"）。

## 10. 附录

### 10.1 本目录文件清单

| 文件 | 行数 | 角色 | 职责 |
|---|---|---|---|
| `DocumentPrototype.java` | 28 | Prototype | 声明 `cloneDocument()`；**不继承** `Cloneable` |
| `WordDocument.java` | 71 | ConcretePrototype | `implements DocumentPrototype, Cloneable`；深拷贝 `images` |
| `DocumentRegistry.java` | 64 | 注册表（非 GoF 角色） | 注册 / 按 key 取克隆 / 预置两个模板 |
| `Client.java` | 79 | Client | 三段演示：直接克隆 / 注册表 / 深拷贝验证 |

### 10.2 运行方式

```bash
build.bat
java -cp out com.patterns.creational.prototype.Client

# 或只编译本目录
javac -Xlint:all --release 25 -encoding UTF-8 -d out src/com/patterns/creational/prototype/*.java
java -Dstdout.encoding=UTF-8 -cp out com.patterns.creational.prototype.Client
```

### 10.3 本次修订记录

| 改动 | 原因 |
|---|---|
| `WordDocument.getImages()` 改为返回 `List.copyOf(images)` | 原本直接返回内部可变 `List`，外部一句 `add` 就能污染原型——这是真实的封装漏洞 |
| `Client` 场景 3 改为用**行为**验证深拷贝 | 原写法 `getImages() == getImages()` 在返回副本后恒为 `false`，断言永远成立，证明不了任何事 |
| `Client` 增加注释说明"为什么不用 `==` 判断" | 这是一个容易复发的思维陷阱，必须在代码里留下理由 |

> 原项目的 6 处问题（接口 `extends Cloneable`、4 处强转、无 getter、无注册表、`catch` 死代码、`show()` 混入接口）均已在重构阶段处置，不是本轮改动，特此说明。本轮修的是 `getImages()` 封装泄漏与验证方式失效两个问题。

---

相关文档：[模式对比 §创建型](../../../../../docs/02-pattern-comparison.md) · [学习路径](../../../../../docs/01-learning-path.md) · [优化记录](../../../../../docs/03-optimization-log.md) · [验证记录](../../../../../docs/05-verification.md)
