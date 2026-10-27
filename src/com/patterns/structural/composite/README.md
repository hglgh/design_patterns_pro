# 组合模式（Composite Pattern）

> **第一次接触这个模式？** 先读 [BEGINNER.md](BEGINNER.md)（通俗入门），
> 再回来读这篇工程深度版。

> **一句话**：把"单个对象"和"对象容器"统一到同一个接口下，让客户端用**同一套代码**既能操作一个文件，也能操作一整棵目录树。

| 项目 | 内容 |
|---|---|
| 分类 | 结构型模式 |
| 包路径 | `com.patterns.structural.composite` |
| 运行入口 | `Client.main()` |
| 核心角色 | `FileSystemNode`（抽象组件）、`FileLeaf`（叶子）、`Directory`（容器） |
| 一句话记忆 | **"把树当成一个节点来用"** |

---

## 0. 先看结论

在动手之前，先把整篇要讲的东西压缩成一张表。后面每一节都是这张表的展开。

| 问题 | 结论 |
|---|---|
| 组合模式到底解决什么 | 让"整体"和"部分"共用同一个类型。客户端写 `node.size()` 时，不需要知道眼前是文件还是整棵树 |
| 三个角色分别是什么 | 抽象组件（统一契约）、叶子（递归终点）、容器（递归步骤） |
| 关键实现只有一行 | `Directory.size()` 里的 `total += child.size()`。多态让这一行同时覆盖"加一个数"和"再走一层" |
| 最容易踩的坑 | 没有防环校验。`folder.add(folder)` 之后任何一次遍历都会 `StackOverflowError` |
| 第二个坑 | `remove()` 用 `equals` 匹配。record 的值相等语义会导致"删一个陌生对象，却删掉了你的成员" |
| 什么时候不该用 | 结构本来只有一层、或者叶子和容器的操作差异极大（容器 20 个方法，叶子只用 2 个） |
| 和装饰器的分界 | 组合持有 `List<Component>`（一对多、聚合结果）；装饰器持有单个 `Component`（一对一、转发增强） |
| 本项目做了什么 | 合并原项目 demo1/demo2 的能力（打印 + 统计），补上防环、参数校验、只读视图、sealed 穷尽检查 |

### 诚实前置说明

原项目有**两个**组合模式 demo，各自都能编译运行，但**各缺一半能力**：

| | demo1 `FileSystemClient` | demo2 `Client` |
|---|---|---|
| 接口方法 | `display(String indent)` | `showDetails()` + `size()` |
| 能打印结构 | ✅ 带缩进 | ❌ **不带缩进，层级丢失** |
| 能统计大小 | ❌ 接口里没有 `size()` | ✅ |
| 叶子的 `size` 字段 | ❌ 没有 | ✅ |

所以本模块不是"修了一个 bug"，而是**把两个半成品合成一个完整的**，再把两边共同的缺陷补上。所有缺陷都在本文 §2 用探针实测取证过，不是推测。

---

## 1. 现实案例

### 说人话版

你打开电脑的"文件资源管理器"，右键点一个**文件夹**选"属性"，系统告诉你"大小：2.3 GB，包含 1,847 个文件"。
你再右键点一个**文件**选"属性"，系统也告诉你"大小：15 KB"。

注意一件事：**你用的是同一个菜单、同一个"属性"操作**，但背后系统做的事情完全不同——

- 对文件：直接读它的字节数。
- 对文件夹：递归钻进所有子文件夹，把成千上万个文件的大小加起来。

而你（客户端）**根本不需要知道**自己点的是文件还是文件夹，也不需要写"如果是文件夹就递归"的逻辑。这就是组合模式在做的事。

同样的结构在业务系统里到处都是：

| 场景 | 叶子节点 | 容器节点 | 统一操作 |
|---|---|---|---|
| 文件系统 | 文件 | 文件夹 | 计算大小、打印结构 |
| 公司组织架构 | 员工 | 部门（含子部门） | 统计人数、汇总薪资 |
| 电商商品分类 | 具体商品 | 分类（含子分类） | 统计商品数、批量打折 |
| 前端 UI | 按钮、文本 | 面板、容器 | 渲染、计算尺寸 |
| 菜单/权限树 | 菜单项 | 菜单组 | 递归鉴权、渲染 |
| 审批流 | 审批人 | 审批组 | 逐级上报、计算总时长 |

### 三个现实中的例子，感受一下"统一"的价值

**例子 1：电商后台的"全店打折"**

运营点了"全店 8 折"。商品分类是三层树：`女装 > 连衣裙 > 具体 SKU`。如果不用组合模式，代码得写：

```java
// ❌ 每层都要判断是商品还是分类
void discount(Object node) {
    if (node instanceof Category c) {
        for (Object child : c.children) discount(child);   // 递归散落在客户端
    } else if (node instanceof Sku s) {
        s.price *= 0.8;
    }
}
```

用组合模式，客户端只有一句：`root.applyDiscount(0.8)`。

**例子 2：组织架构建模**

公司有集团、事业部、部门、小组、员工五层。要算"研发中心的总人力成本"。同样的困境：层级是数据决定的，代码不能写死。组合模式让 `org.totalCost()` 一次搞定，而且**明年公司改成四级或六级，代码一行不用改**。

**例子 3：前端组件树**

React / Vue 的组件树本身就是组合模式：`<div>` 里可以放 `<span>`，也可以放另一个 `<div>`。渲染引擎不需要知道当前节点是叶子还是容器——它只调用 `render()`。

### 专业版

组合模式（Composite Pattern）将对象组合成**树形结构**以表示"部分—整体"的层次关系。
它让客户端对**单个对象（Leaf）**和**对象组合（Composite）**的使用具有一致性——
即"统一接口 + 递归委托"：容器节点持有 `Component` 类型的子节点集合，
并把接收到的请求递归转发给所有孩子，容器自身的实现只描述"如何聚合孩子的结果"。

> **注意术语**：GoF 原文把"统一接口 + 递归委托"两件事分开讲。很多资料只强调"递归"，这是误解 —— 递归只是实现手段，**统一接口才是模式的目的**。§8 会专门拆这个误区。

---

## 2. 原始实现：不用组合模式会怎样，以及原项目写成了什么样

这一节分三段：先看"完全不用组合模式"的反例，再看原项目两个 demo 的真实代码和实测输出，最后用探针把缺陷钉死。

### 2.1 v0：完全不用组合模式

假设要统计一个项目目录的总大小，最自然的写法是给文件和文件夹写两个不同的类：

```java
// ❌ 反例：文件和文件夹是两套完全不同的类型
class FileItem {
    String name;
    long size;
}

class FolderItem {
    String name;
    List<FileItem> files;      // 只能装文件
    List<FolderItem> folders;  // 只能装文件夹
}
```

客户端想统计总大小，就必须自己写递归，而且**每一层都要做类型判断**：

```java
// ❌ 反例：递归逻辑散落在客户端
long calcFolderSize(FolderItem folder) {
    long total = 0;
    for (FileItem f : folder.files) {
        total += f.size;
    }
    for (FolderItem sub : folder.folders) {
        total += calcFolderSize(sub);   // 客户端自己递归
    }
    return total;
}

// 打印结构时，同样的递归要再写一遍
void printFolder(FolderItem folder, String indent) {
    for (FileItem f : folder.files) { System.out.println(indent + f.name); }
    for (FolderItem sub : folder.folders) { printFolder(sub, indent + "  "); }
}
```

会遇到什么问题？

| # | 问题 | 具体表现 |
|---|---|---|
| 1 | **递归逻辑重复散落** | 每新增一个"需要遍历整棵树"的操作（统计大小、打印、查找、删除、算文件数…），就要在客户端重写一遍递归。10 个操作 = 10 段几乎一样的递归代码 |
| 2 | **客户端被迫区分类型** | 每个调用点都要写 `for files` + `for folders` 两套循环，或者 `instanceof` 判断。类型细节泄漏到了业务层 |
| 3 | **无法统一传递** | 想把"某个节点"作为参数传给一个方法，参数类型该写 `FileItem` 还是 `FolderItem`？只能写两个重载，或者退化成 `Object` 然后强转 |
| 4 | **结构变化就崩** | 如果哪天 `FolderItem` 里还要装"快捷方式"这种第三种节点，所有递归代码都得改 |

**根因**：叶子和容器没有共同的抽象，导致"树"这个结构对客户端是**透明可见**的，客户端不得不亲自遍历它。

### 2.2 v1：原项目 demo1 —— 能打印，不能统计

原项目 `structuraltype.CompositePattern.demo1` 的四个文件，接口定义如下：

```java
public interface FileComponent {
    void display(String indent);
}

public record FileLeaf(String name) implements FileComponent {
    @Override
    public void display(String indent) {
        System.out.println(indent + "- 文件：" + name);
    }
}

public class FolderComposite implements FileComponent {
    private final String name;
    private final List<FileComponent> children = new ArrayList<>();

    public FolderComposite(String name) { this.name = name; }

    public void add(FileComponent component) { children.add(component); }

    public void remove(FileComponent component) { children.remove(component); }

    @Override
    public void display(String indent) {
        System.out.println(indent + "+ 文件夹：" + name);
        for (FileComponent child : children) {
            child.display(indent + "  ");
        }
    }
}
```

客户端构建三层的树，然后 `root.display("")`。实测输出（`java -Dstdout.encoding=UTF-8` 重定向，6 行 / 148 字节）：

```text
+ 文件夹：根目录
  + 文件夹：文档
    - 文件：readme.txt
  + 文件夹：图片
    - 文件：logo.png
  - 文件：data.csv
```

结构打印得挺清楚。但请注意：**这个接口里没有 `size()`**。

用反射确认这一点：

```java
for (Method m : FileComponent.class.getDeclaredMethods()) {
    System.out.println("  " + m.getReturnType().getSimpleName() + " " + m.getName()
            + "(参数个数 " + m.getParameterCount() + ")");
}
```

探针实测输出：

```text
[事实 1] FileComponent 接口只声明了 display()，没有任何统计能力
  void display(参数个数 1)
```

只有一个 `void display(int)`。所以 demo1 能告诉你"目录里有什么"，但**永远回答不了"这个目录多大"**。

### 2.3 v2：原项目 demo2 —— 能统计，但打印不带缩进

原项目 `structuraltype.CompositePattern.demo2` 换了一套命名，接口加了 `size()`：

```java
interface FileSystemComponent {
    void showDetails();
    long size();
}

record File(String name, long size) implements FileSystemComponent {
    @Override
    public void showDetails() {
        System.out.println("文件: " + name + ", 大小: " + size + "KB");
    }
}

class Folder implements FileSystemComponent {
    private final String name;
    private final List<FileSystemComponent> children = new ArrayList<>();

    public Folder(String name) { this.name = name; }

    public void add(FileSystemComponent component) { children.add(component); }

    @Override
    public void showDetails() {
        System.out.println("文件夹: " + name);        // ← 没有 indent 参数
        for (FileSystemComponent child : children) {
            child.showDetails();                     // ← 不传缩进
        }
    }

    @Override
    public long size() {
        long total = 0;
        for (FileSystemComponent child : children) {
            total += child.size();
        }
        return total;
    }
}
```

实测输出（6 行 / 168 字节）：

```text
文件夹: 根目录
文件夹: 我的文档
文件: 文档.txt, 大小: 100KB
文件: 图片.jpg, 大小: 200KB
文件: README.md, 大小: 50KB
总大小: 350KB
```

**这就是问题所在**：`showDetails()` 没有 `indent` 参数，所以输出是**完全平铺**的。
从这 6 行里，你无法判断 `文档.txt` 属于"根目录"还是"我的文档"。
树建好了，结构信息却在打印时丢掉了。

`size()` 倒是正确的（100 + 200 + 50 = 350）。

### 2.4 用探针把缺陷钉死

上面的问题肉眼能看出来，但"能看出来"不够 —— 本文档里的每条结论都要有可复现的证据。
下面四个事实由探针 `CompositeDemo2Probe` 实测得到，完整输出 16 行：

```text
[事实 1] showDetails() 不带缩进，层级信息在输出里完全丢失
文件夹: 根目录
文件夹: 我的文档
文件: 文档.txt, 大小: 100KB
文件: 图片.jpg, 大小: 200KB
文件: README.md, 大小: 50KB

[事实 2] add(null) 能通过，遍历时才炸
文件夹: 含 null 的目录
  抛出了：java.lang.NullPointerException

[事实 3] File 没有参数校验，空文件名和负大小都能创建
  new File("", -5) = File[name=, size=-5]

[事实 4] add 没有防环校验，把目录加进自己会让 size() 栈溢出
  抛出了：java.lang.StackOverflowError
```

> 说明：事实 4 一开始我是用 `showDetails()` 测的，结果刷了十几万行"文件夹: 自引用"才溢出。改用 `size()` 后输出干净了 —— 因为 `size()` 不打印。

四条缺陷整理成表：

| 编号 | 缺陷 | 后果 |
|---|---|---|
| P1 | `showDetails()` 无缩进参数 | 层级信息在输出里丢失，树形结构退化成平铺列表 |
| P2 | `add()` 不校验 `null` | 非法状态被接受，错误延迟到遍历时才暴露 NPE |
| P3 | `File` 无构造校验 | `new File("", -5)` 合法，空名字、负大小流进系统 |
| P4 | `add()` 无防环校验 | 自我引用后任何一次遍历都 `StackOverflowError` |

demo1 还有两个 demo2 没有的问题：

```text
[事实 3] remove 返回 void，且按 equals 匹配——传一个从未加入过的新对象也能删掉
  stranger 从未 add 过，是同一对象吗：false
  remove(stranger) 已执行（无返回值，是否真的删掉了只能再看一次）：
  + 文件夹：f

[事实 4] 同名同值文件加入两个，remove 只删第一个，客户端无法指定删哪一个
  加入 left 和 right 后：
  + 文件夹：dup
    - 文件：same.txt
    - 文件：same.txt
  dup.remove(left) 已执行，删除后：
  （还剩一个，但到底是 left 还是 right，外部无从分辨）：
  + 文件夹：dup
    - 文件：same.txt
```

注意事实 3 的输出：`remove(stranger)` 之后，目录 `f` 下面**空了**。`stranger` 是一个刚 `new` 出来、
从未被 `add` 过的对象，却删掉了一个真实成员。原因是 `FileLeaf` 是 record，`equals` 按 `name` 比较：

```text
[事实 2] FileLeaf 是 record，equals 按值比较，不是按身份
  a1 == a2      ：false
  a1.equals(a2) ：true
```

`List.remove(Object)` 用的是 `equals`，所以"删掉内容相同的元素"而不是"删掉这个对象"。
事实 4 是它的直接后果：两个同名同值的文件都加进去后，`remove(left)` 删掉的是**第一个**匹配项，
客户端既无法指定删哪一个，也无法知道自己删掉的是哪一个。

| 编号 | 缺陷 | 后果 |
|---|---|---|
| P5 | `remove()` 返回 `void` | 调用方无法知道是否真的删除成功 |
| P6 | `remove()` 依赖 `equals` | 传一个从未加入的等价对象也能删除；同名同值节点无法区分 |
| P7 | 叶子类叫 `File` | 与 `java.io.File` 同名，见下 |

P7 单独说。原项目 demo2 的叶子类就叫 `File`，与 `java.io.File` 完全同名。
如果有人在这个包里需要操作真实磁盘文件，写了 `import java.io.File;`，会发生什么？
探针 `CompositeNameClashProbe` 实测：

```text
File 这个名字解析成了：java.io.File
```

**编译通过，没有任何警告**（`JAVAC_EXIT=0`）。单类型导入静默遮蔽了同包的类型 ——
不是报错，而是"你以为在用自己写的 `File`，实际上拿到的是 JDK 的 `File`"。
这种错误只有在运行时看到莫名的 `NoSuchMethodError` 或行为不对才可能发现。

这就是重构版为什么把叶子改名为 `FileLeaf`、容器改名为 `Directory`。`Directory.java` 的类注释里
也写明了这个理由 —— 不是洁癖，是踩过的坑。

### 2.5 v3：重构版补齐了什么

| 缺陷 | 重构版的处理 | 代码位置 |
|---|---|---|
| P1 无缩进 | `print(String indent)` 带缩进参数，每层加 `"   "` | `FileSystemNode.print` |
| P2 `add(null)` | 显式判空，抛 `IllegalArgumentException` | `Directory.add` |
| P3 无构造校验 | `FileLeaf` 紧凑构造器校验名称与大小；`Directory` 构造器校验名称 | `FileLeaf` / `Directory` |
| P4 无防环 | `add()` 里检查 `child == this` 且检查 `child` 是否为当前目录的后代 | `Directory.add` |
| P5 `remove` 返回 void | 改为返回 `boolean`（`List.remove` 的返回值） | `Directory.remove` |
| P6 依赖 `equals` | **保留**，但在 §5.6 明确说明这个取舍与代价 | `Directory.remove` |
| P7 类名冲突 | 改名 `FileLeaf` / `Directory` | 全部 |
| （新增）能力合并 | 同时提供 `size()` 与 `print(indent)` | `FileSystemNode` |
| （新增）只读视图 | `children()` 返回 `Collections.unmodifiableList` | `Directory.children` |
| （新增）穷尽检查 | `sealed interface` + `switch` 模式匹配，无 `default` | `FileSystemNode` / `Directory.fileCount` |

---

## 3. 设计动机

### 为什么需要引入组合模式？

| 动机 | 说明 |
|---|---|
| **一致地使用单个对象和组合对象** | 客户端拿到 `FileSystemNode`，直接调 `size()` / `print()`，不必知道是叶子还是树 |
| **把递归逻辑收进类内部** | 遍历树的代码写在 `Directory` 里，只写一次。客户端调用 `root.size()` 就够了 |
| **符合开闭原则** | 新增一种叶子节点（如"快捷方式"）只需新增一个实现类，`Directory` 和客户端代码都不用改 |
| **符合依赖倒置** | `Directory` 的 `children` 声明为 `List<FileSystemNode>`（抽象），而不是 `List<FileLeaf>`（具体） |
| **天然表达层级结构** | 树形结构直接映射为对象引用关系，不需要额外的 parent/child 索引字段 |

### 什么情况下**不需要**组合模式？

这一节比上面那节更重要。设计模式最大的危害不是"不会用"，而是"到处用"。

**① 结构本来只有一层**

如果商品列表是平铺的（没有"分类"概念），`List<Product>` 就够了。
硬套组合模式会得到一个永远只有叶子、没有容器的"树" —— 这是纯粹的自找麻烦。

**② 叶子和容器的操作差异极大**

假设容器有 20 个方法（批量导入、批量导出、权限继承、排序策略…），而叶子只用得到其中 2 个。
强行统一接口的结果是：叶子被迫实现 18 个无意义的方法，全写 `throw new UnsupportedOperationException()`。
这时候正确的做法是**拆成两个接口**，让客户端在需要遍历时显式判断类型。

判断标准很简单：**如果接口里超过一半的方法对叶子都无意义，就别合并。**

**③ 客户端永远只关心单个元素**

如果业务里从来没有"对整棵树做一件事"的需求，遍历都是框架代劳（比如 MyBatis 查出平铺列表，前端自己拼树），
那就不需要抽象组件 —— 数据结构用两个类表达反而更清晰。

**④ 层级固定且很浅**

比如"省—市"两级永不变化。写两个类 + 一个 `List` 字段，比引入 `Component` 抽象直观得多。
组合模式的价值随**层级的不确定性**增长：层级越可能变深，收益越大。

**⑤ 需要强类型约束的场景**

如果业务规则是"图片文件夹里只能放图片"，组合模式给不了这个保证 —— 因为它故意把类型擦成
统一的 `Component`。这时候应该用**泛型 + 边界**，或者干脆放弃组合模式，为每层写专门的类。

### 顺便说说：什么信号说明"该用组合模式了"？

如果你在代码里看到下面这些症状，基本就可以动手了：

- 出现了 `if (x instanceof Folder) ... else if (x instanceof File) ...`，而且这个判断在**多个方法**里重复
- 有一组方法（`calcSize`、`print`、`find`、`count`、`export`）长得几乎一样，都带一个"递归调用自己"的尾巴
- 新增一个"遍历操作"的代价是"再写一遍同样的递归骨架"
- 需求方说"我们的分类可能会变成三层，也可能变成四层"

---

## 4. 设计推导：为什么是这三个角色？

### 4.1 从需求倒推结构

我们的目标是让 `root.size()` 能工作。倒推一下需要什么：

```text
需求：客户端对任意节点调用 size()
  ↓
必须存在一个共同类型，让客户端能声明 FileSystemNode node = ...
  ↓
【角色 1】抽象组件 FileSystemNode：声明 name() / size() / print()
  ↓
叶子要能返回自身大小
  ↓
【角色 2】FileLeaf：实现 size() 返回自己的字段值（递归终点）
  ↓
容器要能返回所有子孙的大小之和
  ↓
【角色 3】Directory：持有 List<FileSystemNode>，size() 遍历累加（递归步骤）
```

这个倒推过程的关键在第 3 步：**必须先有统一类型，才有组合模式**。
很多人写代码时顺序反了 —— 先写 `Directory` 和 `FileLeaf` 两个独立的类，最后才想"要不要抽个接口"。
那样抽出来的接口往往只包含两者的**交集**（比如只有 `getName()`），起不到统一作用。

正确的顺序是：**先想清楚客户端要怎么用**（`node.size()`），再决定接口上应该有什么（`size()`），
最后才写两个实现。

### 4.2 类结构图

```text
                ┌──────────────────────────────┐
                │  «sealed interface»          │
                │  FileSystemNode              │
                │──────────────────────────────│
                │ + name() : String            │
                │ + size() : long              │
                │ + print(indent : String)     │
                └──────────────────────────────┘
                     △                  △
        implements   │                  │  implements
       ┌─────────────┘                  └─────────────┐
       │                                              │
┌──────────────────────┐              ┌──────────────────────────┐
│ «record» FileLeaf    │              │ «final class» Directory   │
│──────────────────────│              │──────────────────────────│
│ - name : String      │              │ - name : String          │
│ - size : long        │              │ - children : List<       │
│──────────────────────│              │       FileSystemNode>  ◇─┼──┐
│ + print(indent)      │              │──────────────────────────│  │
└──────────────────────┘              │ + add(child) : Directory │  │
                                      │ + remove(child): boolean │  │
        递归终点                       │ + children() : List      │  │
   （返回自己的 size）                  │ + size() : long          │  │
                                      │ + print(indent)          │  │
                                      │ + find(name) : Optional  │  │
                                      │ + fileCount() : int      │  │
                                      └──────────────────────────┘  │
                                                   △                 │
                                                   └─────── 聚合 ─────┘
                                              （children 里可以是 FileLeaf，
                                                也可以是另一个 Directory）
```

### 4.3 关键设计决策一：`sealed` 还是普通 `interface`

**不用 `abstract class` 的理由**：`FileLeaf` 是纯数据载体，用 `record` 最合适，而 record 不能 `extends` 类，只能 `implements` 接口。

**用 `sealed` 的理由**：节点在业务上只有"文件"和"目录"两种。`sealed ... permits FileLeaf, Directory` 把这个约束交给编译器保管。

这个好处不是口号，是可以实测的。探针 `CompositeSealedProbe` 定义了一个三种实现、却只写两个 `case` 的 switch：

```java
sealed interface Node permits LeafA, LeafB, LeafC {}
record LeafA() implements Node {}
record LeafB() implements Node {}
record LeafC() implements Node {}

static int count(Node node) {
    return switch (node) {
        case LeafA a -> 1;
        case LeafB b -> 2;
    };
}
```

编译结果（`JAVAC_EXIT=1`）：

```text
CompositeSealedProbe.java:15: 错误: switch 表达式不包含所有可能的输入值
        return switch (node) {
               ^
1 个错误
```

**如果没有 `sealed`**，编译器就无法证明 `Node` 只有三个实现，会强制你写 `default` 分支。
而 `default` 分支一旦写上，将来新增实现时**编译器不会提醒你** —— 新节点会静默走进 default，
返回一个错误的结果。这是"编译期安全网"与"静默 bug"的区别。

`Directory.fileCount()` 正是靠这个机制，只写两个 case 就能穷尽：

```java
public int fileCount() {
    int count = 0;
    for (FileSystemNode child : children) {
        count += switch (child) {
            case FileLeaf ignored -> 1;
            case Directory dir -> dir.fileCount();
        };
    }
    return count;
}
```

`case Directory dir -> dir.fileCount();` 是递归步骤，`case FileLeaf ignored -> 1;` 是递归终点。

### 4.4 关键设计决策二：`children` 的类型

```java
private final List<FileSystemNode> children = new ArrayList<>();
```

声明成 `FileSystemNode` 而不是 `Directory`，是组合模式**成立的前提**。
只有声明为抽象组件类型，容器才能同时容纳叶子和其他容器，树才能任意深度嵌套。
声明成 `List<Directory>` 就只能装目录 —— 树退化成"有枝无叶"的空壳。

这一条看起来是废话，但它是初学者最常犯的错：写 `List<Folder> subFolders` 和
`List<File> files` 两个字段，然后**立刻回到 §2.1 的两套循环** —— 你以为自己在用组合模式，
实际上只是把反例改了个名字。

### 4.5 关键设计决策三：安全式 vs 透明式

这是组合模式最经典的分歧点。

**透明式（transparent）**：把 `add` / `remove` 声明在抽象组件上。

```java
// 透明式
public interface FileSystemNode {
    String name();
    long size();
    void add(FileSystemNode child);      // ← 放在接口里
    void remove(FileSystemNode child);
}
```

叶子只能抛异常：

```java
public record FileLeaf(String name, long size) implements FileSystemNode {
    @Override
    public void add(FileSystemNode child) {
        throw new UnsupportedOperationException("文件不能有子节点");
    }
}
```

| | 透明式 | 安全式 |
|---|---|---|
| 客户端写法 | 完全统一，拿谁都敢 `add` | 添加前要先确认是容器 |
| 错误发现时机 | **运行期**（`UnsupportedOperationException`） | **编译期**（方法根本不存在） |
| 接口纯净度 | 叶子携带了做不到的操作 | 接口只包含所有实现都能做的事 |
| 典型使用者 | 早期 GoF 教科书、`java.awt.Container` | Spring 的 `CompositeXxx`、JUnit 5 |

**本项目选安全式**，因为 Demo 里"构建结构"和"遍历结构"是两个清晰分离的阶段：

```java
// 构建阶段：变量的静态类型就是 Directory，本来就知道自己在加孩子
Directory docs = new Directory("docs");
docs.add(new FileLeaf("需求说明书.docx", 120))
        .add(new FileLeaf("架构图.png", 340));

// 遍历阶段：只依赖统一接口，这里才需要"不区分类型"
FileSystemNode node = root;
System.out.println(node.size());
```

这样既不牺牲类型安全，又享受了统一操作的好处。

> **什么时候该选透明式？** 当客户端是"通用树编辑器"——它拿到任意节点都可能想要插入子节点，
> 而且这个节点是用户从界面上点出来的，编译期根本不知道类型。这时候透明式的"先调再说，不行就抛"
> 反而更合适。

### 4.6 关键设计决策四：`FileLeaf` 用 record，`Directory` 用 class

| | `FileLeaf` | `Directory` |
|---|---|---|
| 状态 | 不可变（名字 + 大小） | 可变（children 会增删） |
| 需要 `equals` / `hashCode` | 是（record 免费给） | 否（默认引用相等即可） |
| 适合 record 吗 | ✅ 完美契合 | ❌ record 的"值语义"与可变集合天然冲突 |

**record 的隐藏好处**：`record FileLeaf(String name, long size)` 自动生成的访问器就叫 `name()` 和 `size()`，
**签名恰好与接口一致**，所以实现 `FileSystemNode` 时这两个方法一行都不用写。剩下的只有 `print`。

**record 的隐藏代价**：自动生成的 `equals` 是值比较。这一点在 §2.4 已经变成了真实的缺陷（P6）。
换句话说，选了 record 就等于选了"值语义"，而值语义又影响了 `remove` 的行为 ——
设计决策从来不是孤立的。

### 4.7 关键设计决策五：`add()` 里的防环校验

这是本项目相比原项目**新增**的最重要的一行逻辑：

```java
public Directory add(FileSystemNode child) {
    if (child == null) {
        throw new IllegalArgumentException("子节点不能为 null");
    }
    // 防环校验：把自己或自己的祖先加进来，会让 size()/print() 无限递归导致 StackOverflowError。
    if (child == this || (child instanceof Directory dir) && dir.contains(this)) {
        throw new IllegalArgumentException("不能将目录添加到自身或其子目录下，会形成循环引用");
    }
    children.add(child);
    return this;
}
```

两个判断分别拦两种情况：

| 情况 | 例子 | 判断方式 |
|---|---|---|
| 加自己 | `root.add(root)` | `child == this` |
| 加祖先 | `b.add(root)`，而 `root → a → b` | `dir.contains(this)` 检查当前目录是否在 `child` 的子树里 |

为什么第二种情况也要拦？因为把祖先塞进后代，同样会形成环：
`root → a → b → root → a → b → ...`。此时从 `root` 开始遍历，会无限循环。

**为什么用 `==` 而不是 `equals`？** 因为循环引用的本质是"某个对象成为了自己的子孙"，
这是**引用层面**的问题。`FileLeaf` 是 record，`equals` 比的是内容，两个 `new FileLeaf("a.txt", 5)`
会 equals 相等，但它们同时加入目录完全合法。误用 `equals` 会导致"两个同名文件不能共存"这种诡异限制。

探针实测（`CompositeProProbe`，完整 17 行输出见 §6.3）：

```text
[事实 2] 防环校验不只拦 root.add(root)，也拦祖先被塞进后代
  b.add(root)  -> 不能将目录添加到自身或其子目录下，会形成循环引用
  root.add(root) -> 不能将目录添加到自身或其子目录下，会形成循环引用
```

### 4.8 关键设计决策六：`children()` 返回视图还是副本

```java
private final List<FileSystemNode> children = new ArrayList<>();

public List<FileSystemNode> children() {
    return Collections.unmodifiableList(children);   // 只读视图，不是快照
}
```

为什么不直接 `return children`？因为外部拿到可变引用后，一句 `children().add(x)` 就绕过了 `add()` 的
防环校验 —— 防了半天，后门开着。

这里返回的是**视图**而不是**副本**，这是一个刻意的选择：

| | 只读视图（本项目） | 只读副本（`List.copyOf`） |
|---|---|---|
| 外部能改吗 | 不能 | 不能 |
| 原集合变化后，之前拿到的引用会看到吗 | **会** | 不会 |
| 每次调用开销 | O(1) | O(n) 拷贝 |
| 适合场景 | 遍历"当前最新状态" | 需要一份稳定快照 |

组合模式里希望外部"看到最新的树"，所以选视图。

> **对比原型模式**：那边的 `getImages()` 用的是 `List.copyOf`（副本）。
> 两者的差异不是随便定的 —— 原型模式返回的是"某个时刻的配置"，要的是**稳定快照**；
> 组合模式返回的是"当前的树长什么样"，要的是**实时视角**。同一个 API 形状，语义完全不同。

探针实测（`CompositeProProbe`）：

```text
[事实 1] children() 返回的是只读视图，不是快照
  取视图时 a 的子节点数：1
  之后 a 又多了一个孩子，同一个视图现在有：2 个
  直接往视图里 add 会抛：java.lang.UnsupportedOperationException
```

### 4.9 关键设计决策七：为什么要给 `size` 加类型

`Directory.size()` 的返回类型是 `long`，不是 `int`。

原因是文件大小会溢出。`int` 最大约 21 亿 —— 按 KB 算只有 **2 TB**。
一个稍大的文件服务器、或者一个装了几个月日志的服务器，累计大小轻松超过这个数。
用 `int` 的结果是：`total` 悄悄变成负数，而你很难想到是这里出了问题。

`FileLeaf.size` 也用 `long`，并在紧凑构造器里校验非负：

```java
public FileLeaf {
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("文件名不能为空");
    }
    if (size < 0) {
        throw new IllegalArgumentException("文件大小不能为负数：" + size);
    }
}
```

**"校验放在构造器"是组合模式里一个容易忽略的点**：树一旦建错，后面的所有统计都是错的，
而且错误会沿着递归一路传播（一个负数会让整棵树的 `size()` 都偏小）。在入口处挡住，比在出口处排查便宜得多。

---

## 5. 代码解析：每个类为什么必须存在？

| 类 | 角色 | 职责 | 如果删掉/合并会怎样 |
|---|---|---|---|
| `FileSystemNode` | 抽象组件 | 声明统一契约 `name/size/print` | **组合模式崩塌**。客户端无法用同一变量引用文件和目录，必须回到 §2.1 的类型判断地狱 |
| `FileLeaf` | 叶子 | 保存自身数据，实现 `size()` 返回自身大小 | **递归失去数据来源**。`Directory.size()` 加到的永远是 0，业务结果全错 |
| `Directory` | 容器 | 持有子节点，实现递归委托 | 树退化成单层列表，无法表达层级 |
| `Client` | 客户端 | 构建对象树，演示统一操作 | 无法运行验证 |

### 5.1 最关键的一段代码

```java
// Directory.java
@Override
public long size() {
    long total = 0;
    for (FileSystemNode child : children) {
        total += child.size();      // ← 组合模式的全部魔法就在这一行
    }
    return total;
}
```

`child.size()` 里的 `child` **静态类型是 `FileSystemNode`**，编译期只知道"它有 size() 方法"；
运行期 JVM 通过**动态绑定（多态）**决定实际执行哪个实现：

- 如果 `child` 实际是 `FileLeaf` → 执行 record 自动生成的 `size()`，返回字段值，**递归终止**。
- 如果 `child` 实际是 `Directory` → 执行上面这段代码，**继续向下递归**。

`Directory` 完全不知道树有多深、每层有几个孩子。它只负责"把我的孩子的 size 加起来"。

### 5.2 为什么 `print` 要传 `indent` 而不是依赖内部状态

```java
@Override
public void print(String indent) {
    System.out.println(indent + "└─ [目录] " + name + " (" + size() + " KB)");
    String childIndent = indent + "   ";
    for (FileSystemNode child : children) {
        child.print(childIndent);
    }
}
```

`indent` 是**参数**而不是字段。这个选择看起来无关紧要，其实有两个实际好处：

**① 天然线程安全**。如果写成 `int depth` 字段，递归时 `depth++`、返回前 `depth--`，
一旦有人并发调用 `print()`，两个线程会互相污染缩进。用参数就没这个问题 —— 每层栈帧有自己的值。

**② 同一棵树可以用不同缩进打印**。传给 `print("")` 是从左边界开始，传 `print("    ")`
就整体右移 4 格，可以直接嵌进日志里。

对比原项目 demo2 的 `showDetails()` —— 它**没有参数**，所以缩进状态无处可放，
最终就只能不缩进。参数化的写法不是"更优雅"，而是**让缩进这件事成为可能**。

### 5.3 `fileCount()` 里的 switch 为什么能不带 default

```java
public int fileCount() {
    int count = 0;
    for (FileSystemNode child : children) {
        count += switch (child) {
            case FileLeaf ignored -> 1;
            case Directory dir -> dir.fileCount();
        };
    }
    return count;
}
```

因为 `FileSystemNode` 是 `sealed` 的，编译器知道只有 `FileLeaf` 和 `Directory` 两种实现，
两个 case 即穷尽。§4.3 已经用编译错误证明了这一点：漏一个 case 就编不过。

`ignored` 这个名字是 JDK 21+ 的约定（未使用的模式变量可以叫 `_`，但 `_` 在某些场景有限制，
用 `ignored` 更保险）。

### 5.4 `find()` 为什么写在 `Directory` 上而不是接口上

```java
public Optional<FileSystemNode> find(String targetName) {
    for (FileSystemNode child : children) {
        if (child.name().equals(targetName)) {
            return Optional.of(child);
        }
        if (child instanceof Directory dir) {
            Optional<FileSystemNode> found = dir.find(targetName);
            if (found.isPresent()) {
                return found;
            }
        }
    }
    return Optional.empty();
}
```

因为"查找"只对容器有意义 —— 一个文件没有"孩子"可查，在它身上调 `find` 只能返回 `Optional.empty()`。
如果放进接口，`FileLeaf` 就得实现一个永远返回空的方法，这正是 §4.5 讨论的"透明式的代价"。

代价是：客户端拿到 `FileSystemNode` 想查找时，必须先判断类型。

```java
// 如果 root 的静态类型是 FileSystemNode
Optional<FileSystemNode> r = (root instanceof Directory d) ? d.find("a.png") : Optional.empty();
```

这个不便是**安全式的固有成本**。它换来的是：你永远不会在文件上误调 `find`。

### 5.5 `add()` 为什么返回 `this` 而不是 `void`

```java
public Directory add(FileSystemNode child) {
    // ...
    children.add(child);
    return this;
}
```

返回 `this` 让构建树变成链式：

```java
Directory docs = new Directory("docs");
docs.add(new FileLeaf("需求说明书.docx", 120))
        .add(new FileLeaf("架构图.png", 340));
```

对比原项目 demo1 的 `void add(...)`，每个孩子都要单独一行 `docs.add(...)`。
对于测试代码里动辄几十个节点的树，链式写法可读性差距很明显。

> 这个技巧叫 **Fluent Interface**（流畅接口），Builder 模式里更常见。
> 注意它和"返回新对象"的不可变风格不同 —— 这里每次 `add` 都修改了同一个对象。

### 5.6 `remove` 依赖 `equals`：一个诚实保留的取舍

```java
public boolean remove(FileSystemNode child) {
    return children.remove(child);
}
```

`List.remove(Object)` 内部用 `equals` 比较。对 `Directory`（普通类）来说 `equals` 是引用相等，
没问题；但对 `FileLeaf`（record）来说 `equals` 是值相等，于是就有了 §2.4 里 P6 的问题。

**为什么没有改成 `==` 匹配？** 因为两种语义各有用途：

| 语义 | 代码 | 行为 | 适合场景 |
|---|---|---|---|
| 按身份删除 | `children.removeIf(c -> c == child)` | 只删除"就是这一个" | 树编辑器中"删掉我选中的节点" |
| 按值删除 | `children.remove(child)`（现状） | 删除"第一个内容相等的" | 配置管理里"删掉名叫 a.txt 的项" |

本项目选了按值删除，因为文件系统的直觉就是"路径唯一"（同名文件在同一目录下不该存在）。
但这个选择有个**隐含前提**：同一目录下不允许有同名节点。而 `add()` 并没有检查这个前提 ——
这就是一个已知的不一致，§8 会列为"遗留瑕疵"。

如果要在生产系统里做，正确做法是二选一：要么在 `add()` 里拒绝同名，要么把 `remove` 改成
`removeIf(c -> c == child)` 并在文档里写清楚"按引用删除"。

### 5.7 能不能把 `Directory` 和 `FileLeaf` 合并成一个类？

有人会想：既然都是 `FileSystemNode`，能不能写一个类，`children` 为空就是文件，非空就是目录？

```java
// ❌ 不推荐：合并成一个类
class Node implements FileSystemNode {
    String name;
    long size;
    List<Node> children = new ArrayList<>();
}
```

技术上可行，但有三个问题：

1. **语义模糊**：`children` 为空的 `Node` 到底是"空文件夹"还是"文件"？无法区分。
   而"空文件夹"是合法且常见的状态（新建一个还没放东西的目录）。探针实测证明重构版确实支持空目录：

   ```text
   [事实 4] 空目录是合法状态：size 为 0、fileCount 为 0，仍能打印
     空目录 size = 0，fileCount = 0
   └─ [目录] 空目录 (0 KB)
   ```

   合并成一个类之后，最后这一行还能打出来吗？能 —— 但前提是你额外维护一个 `boolean isDirectory` 标志。
   而一旦需要这个标志，就等于承认"其实是两种东西，只是被塞进了一个类"。

2. **数据冗余**：文件永远用不到 `children` 这个 `ArrayList` 对象。百万级文件场景下，
   白白多百万个空集合的内存开销（这正是**享元模式**要解决的问题）。
   一个空的 `ArrayList` 对象在 64 位 JVM 上约占 24 字节（对象头 12 + 数组引用 4/8 + size 字段 4，对齐后 24），
   一百万个就是 24 MB —— 只为存"我没有孩子"这个信息。

3. **失去类型安全**：任何地方都能给"文件"塞孩子，无法阻止非法结构。

---

## 6. 执行流程：从 main 到输出

以 `root.size()` 为例，目录树是：

```text
design-patterns/
├─ src/
│  ├─ Main.java (15)
│  └─ docs/
│     ├─ 需求说明书.docx (120)
│     └─ 架构图.png (340)
├─ README.md (6)
└─ pom.xml (3)
```

### 6.1 调用栈展开

```text
main()
└─ root.size()                                    // Directory("design-patterns")
   │  children = [src(Directory), README.md(FileLeaf), pom.xml(FileLeaf)]
   ├─ child[0] = src → src.size()                 // 多态分发到 Directory.size()
   │  │  children = [Main.java(FileLeaf), docs(Directory)]
   │  ├─ child[0] = Main.java → size()            // 多态分发到 record 访问器
   │  │  └─ return 15                              ← 递归终止
   │  ├─ child[1] = docs → docs.size()            // 多态分发到 Directory.size()
   │  │  │  children = [需求说明书.docx, 架构图.png]
   │  │  ├─ 需求说明书.docx.size() → return 120     ← 递归终止
   │  │  ├─ 架构图.png.size()      → return 340     ← 递归终止
   │  │  └─ return 120 + 340 = 460
   │  └─ return 15 + 460 = 475
   ├─ child[1] = README.md → size() → return 6      ← 递归终止
   ├─ child[2] = pom.xml   → size() → return 3      ← 递归终止
   └─ return 475 + 6 + 3 = 484
```

**关键变量追踪**：

| 时刻 | 变量 | 值 |
|---|---|---|
| 最外层 `total` 累加中 | `total` | 0 → 475 → 481 → 484 |
| `src.size()` 内部 | `total` | 0 → 15 → 475 |
| `docs.size()` 内部 | `total` | 0 → 120 → 460 |
| 递归深度 | — | 3 层（root → src → docs） |

**注意每个 `size()` 调用都有自己独立的局部变量 `total`**（栈帧隔离），这就是递归能正确累加的原因。
如果 `total` 写成字段，三次递归会共用同一个变量，结果立刻变成乱账。

### 6.2 实际输出（JDK 25 实测）

命令：

```text
javac --release 25 -encoding UTF-8 -d out (所有 .java)
java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.composite.Client
```

结果为 **27 行 / 1097 字节 / SHA-256 前 16 位 `DF2F01838AA366E1`**：

```text
=== 1. 打印目录结构（递归遍历整棵树）===
└─ [目录] design-patterns (484 KB)
   └─ [目录] src (475 KB)
      ├─ Main.java (15 KB)
      └─ [目录] docs (460 KB)
         ├─ 需求说明书.docx (120 KB)
         ├─ 架构图.png (340 KB)
   ├─ README.md (6 KB)
   ├─ pom.xml (3 KB)

=== 2. 统一调用 size()：客户端不关心是文件还是目录 ===
单个文件 '独立文件.txt' 大小 = 8 KB
整个项目 'design-patterns' 大小 = 484 KB

=== 3. 递归查找深层文件 ===
找到了：架构图.png，大小 340 KB
查找不存在的文件 -> Optional.empty

=== 4. 统计文件总数（sealed + switch 模式匹配）===
项目下共有 5 个文件

=== 5. 动态增删：树结构变化后，所有统计自动跟着变 ===
新增 tests 目录后 -> 文件数 = 6，总大小 = 496 KB
移除 tests 目录后 -> 文件数 = 5，总大小 = 484 KB

=== 6. 防环校验：把父目录加到自己孩子里 ===
已拦截非法操作：不能将目录添加到自身或其子目录下，会形成循环引用
```

**输出里有三处值得盯住看**：

1. **第 1 段的缩进**：`src` 比 `design-patterns` 右移 3 格，`docs` 再右移 3 格。
   缩进完全由 `print` 的参数传递决定，`Directory` 自己不知道深度。
2. **第 2 段的两行**：`leaf.size()` 和 `root.size()` 语法完全一样，但一个返回字段、一个遍历全树。
   这就是组合模式给客户端带来的"一致性"。
3. **第 5 段的数字联动**：加一个 `tests` 目录（1 个文件 12 KB），文件数 5 → 6，大小 484 → 496。
   没有任何"重新统计"的调用，树变了，答案自然就变了。

### 6.3 重构版的行为探针

`CompositeProProbe` 验证了重构版的四个细节，完整输出 17 行：

```text
[事实 1] children() 返回的是只读视图，不是快照
  取视图时 a 的子节点数：1
  之后 a 又多了一个孩子，同一个视图现在有：2 个
  直接往视图里 add 会抛：java.lang.UnsupportedOperationException

[事实 2] 防环校验不只拦 root.add(root)，也拦祖先被塞进后代
  b.add(root)  -> 不能将目录添加到自身或其子目录下，会形成循环引用
  root.add(root) -> 不能将目录添加到自身或其子目录下，会形成循环引用

[事实 3] 参数校验发生在构造期和 add 时，不留给遍历期
  new FileLeaf("x.txt", -1) -> 文件大小不能为负数：-1
  new Directory("  ") -> 目录名不能为空
  root.add(null) -> 子节点不能为 null

[事实 4] 空目录是合法状态：size 为 0、fileCount 为 0，仍能打印
  空目录 size = 0，fileCount = 0
└─ [目录] 空目录 (0 KB)
```

### 6.4 `print()` 到底调了几次 `size()`？—— 一个实测出来的数字

这是本文档最容易写错的一段。直觉会说"3 个目录各算一次，一共 3 次"，
但实测数字是 **6 次**。

探针 `CompositeSizeCallsProbe` 用一份**逻辑完全相同、只多了计数器**的实现来测量
（不是直接改重构版的源码加计数器，避免污染交付代码）：

```text
树中叶子（文件）总数：5
只调一次 root.size()：Directory.size() 被调用 3 次，FileLeaf.size() 被调用 5 次，结果 484 KB
└─ [目录] design-patterns (484 KB)
   └─ [目录] src (475 KB)
      ├─ Main.java (15 KB)
      └─ [目录] docs (460 KB)
         ├─ 需求说明书.docx (120 KB)
         ├─ 架构图.png (340 KB)
   ├─ README.md (6 KB)
   ├─ pom.xml (3 KB)
调用一次 root.print("")：Directory.size() 被调用 6 次，FileLeaf.size() 被调用 10 次
```

逐个拆开看这 6 次是怎么来的：

| print 走到哪 | 触发的 size 调用 | 累计 Directory.size() |
|---|---|---|
| `root.print("")` 打印自己时调 `size()` | `root.size()` → 内部又调 `src.size()`、`docs.size()` | 3 |
| 接着 `src.print(...)` 打印自己时调 `size()` | `src.size()` → 内部又调 `docs.size()` | 5 |
| 接着 `docs.print(...)` 打印自己时调 `size()` | `docs.size()` | 6 |

**根因**：`print()` 每打印一层都要调一次 `size()`，而 `size()` 本身又是全子树遍历。
所以总调用次数不是"目录个数"，而是"**所有目录的子树大小之和**"。

这棵树：`root` 子树 8 个节点 + `src` 子树 5 个节点 + `docs` 子树 3 个节点 = 16 次 `size()` 调用
（6 次 Directory + 10 次 FileLeaf）。对一条链状树，这个和是 n + (n-1) + … + 1 = **O(n²)**。

这不是组合模式的缺陷，而是"`print` 里顺手调 `size`"这个写法的代价。
真实系统里有两种解法：

1. **先算一次，再传下去**：`print(indent, totalSize)`，根节点算一次就够了。
2. **加缓存 + 脏标记**：`Directory` 维护 `cachedSize`，`add`/`remove` 时失效。

本项目保持现状，因为 Demo 的树只有 8 个节点，可读性优先。

---

## 7. 扩展对比：新增一种节点类型

**需求**：项目里要支持"符号链接（快捷方式）"，它指向另一个节点，大小算 1 KB。

### 7.1 使用组合模式：新增 1 个文件，改 1 处 permits

```java
// 新增 ShortcutLink.java
public record ShortcutLink(String name, FileSystemNode target) implements FileSystemNode {
    @Override
    public long size() {
        return 1;                     // 快捷方式本身只占 1 KB
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + "├─ " + name + " -> " + target.name()
                + " (" + size() + " KB)");
    }
}
```

```java
// FileSystemNode.java 只需在 permits 里加一个名字
public sealed interface FileSystemNode permits FileLeaf, Directory, ShortcutLink { }
```

**改动清单**：

| 文件 | 改动 |
|---|---|
| `ShortcutLink.java` | **新增** |
| `FileSystemNode.java` | permits 加一个类名 |
| `Directory.java` | 基本无需修改（`add()` 参数是 `FileSystemNode`，自动接受新类型；`size()` 遍历 `child.size()`，多态自动生效） |
| `Client.java` | **无需修改**（除非想演示新类型） |

`fileCount()` 里的 switch 会**编译报错** —— 因为 sealed 让编译器发现少了一个 case。
这正是我们想要的：编译器帮我们找到了所有需要决策的地方。加上 `case ShortcutLink ignored -> 0;` 即可。

> 注意"改动清单"里 `Directory.java` 写的是"基本无需修改"而不是"无需修改"。
> 唯一要动的地方就是 `fileCount()` 的 switch —— 而这正是 sealed 帮你找出来的。
> **如果没有 sealed**，这个 case 会被 `default` 静默吞掉，快捷方式的文件数就统计错了。

### 7.2 如果不用组合模式（§2.1 的反例结构）

`FolderItem` 里必须新增一个 `List<ShortcutLink> links` 字段，然后：

- `calcFolderSize()` 加一段 `for (ShortcutLink l : folder.links) total += 1;`
- `printFolder()` 加一段循环
- 每一个已有的递归方法都要加一段循环

**10 个遍历操作 = 10 处修改**，漏改一处就是 bug。

### 7.3 三种"统一"方案对比

组合模式不是唯一能让"整体和部分一致"的手段。至少有三条路：

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **组合模式** | 统一接口 + 容器持有 `List<Component>` | 类型安全、可无限嵌套、递归封装在内部 | 需要为每个操作在接口上加方法 |
| **扁平列表 + parent 指针** | 所有节点放一个 `List`，每个节点记录 `parentId` | 存储简单、SQL 友好（一条 `select *` 全查出来） | 递归要自己写，且只能"先查出来再在内存里拼树" |
| **访问者 + 组合** | 节点只暴露结构，操作放在 Visitor 里 | 新增操作不动节点类（开闭原则的更强版本） | 新增节点类型要改所有 Visitor |

**怎么选**：

- 数据来自数据库、层级查询很频繁 → 扁平列表 + parent 指针（配合递归 CTE 或内存拼树）
- 操作种类会不断增加（打印、导出 JSON、鉴权、统计…）→ **组合 + 访问者**，见 §7.5
- 操作种类固定、层级可能很深 → 纯组合模式，本项目即此类

### 7.4 组合 + 迭代器：把树变成 Stream

`Directory` 现在只能 `print`，客户端拿不到"可遍历的序列"。加一个深度优先迭代器就能接上 Stream：

```java
public Stream<FileSystemNode> stream() {
    return Stream.concat(
            Stream.of(this),
            children.stream().flatMap(child ->
                    child instanceof Directory dir ? dir.stream() : Stream.of(child)));
}
```

然后客户端可以：

```java
long total = root.stream().mapToLong(FileSystemNode::size).sum();   // 注意：会重复计算目录大小
List<String> names = root.stream().map(FileSystemNode::name).toList();
```

> ⚠️ 第一个例子是**错的示范**：`mapToLong(size)` 会把每个目录的 size 也加进去，
> 而目录的 size 已经包含了孩子的 size，结果是重复累加。
> 正确的写法是 `root.stream().filter(n -> n instanceof FileLeaf).mapToLong(...)`。
> 这个坑恰好说明：**组合模式把"整体"和"部分"统一了，所以"求和"时必须主动区分二者**。

### 7.5 组合 + 访问者：当操作越来越多

现在的 `Directory` 有 `size`、`print`、`find`、`fileCount` 四个方法。如果再加导出 JSON、
导出 YAML、生成目录树图片、检查循环引用、统计最大深度……`Directory` 会迅速膨胀成"上帝类"。

这时可以引入访问者模式：

```java
public interface NodeVisitor<R> {
    R visitFile(FileLeaf file);
    R visitDirectory(Directory dir);
}
```

`FileSystemNode` 加一个 `accept` 方法，具体操作都写成 Visitor 的实现类。
代价是每次新增节点类型（如 `ShortcutLink`），**所有 Visitor 都要改** —— 这就是著名的
"表达式问题"（expression problem）：数据扩展和操作扩展，只能二选一。

### 7.6 组合 + 享元：大量重复的叶子

文本编辑器里每个字符是一个叶子节点。一份 10 万字的文档就有 10 万个叶子对象，
而字符种类只有几千个。这时用享元模式把"字符内容 + 字体"作为内部状态共享，
只把"位置"作为外部状态传进去。

`Integer.valueOf(-128..127)` 的缓存、`String` 常量池都是这个思路。

### 7.7 组合 + 职责链：请求沿组织树向上传递

审批场景：员工提交报销 → 组长审批 → 部门经理 → 财务。每一级只判断"是不是我该管的金额范围"，
不该我管就交给上级。这是把树形结构与职责链结合起来的典型业务。

### 7.8 与相似模式对比

| 模式 | 持有关系 | 意图 | 典型场景 |
|---|---|---|---|
| **组合 Composite** | 容器持有 **多个** 子节点（List） | 表达"部分—整体"树形结构，统一操作叶子与容器 | 文件系统、组织架构、菜单树 |
| **装饰器 Decorator** | 装饰器持有 **单个** 被装饰对象 | **动态叠加功能**，接口不变但行为增强 | IO 流、给文本加样式 |
| **迭代器 Iterator** | 迭代器持有 **一个** 集合的游标 | **顺序访问**集合元素，不暴露内部结构 | 遍历 List/Set |
| **访问者 Visitor** | 访问者 **不持有** 结构，被结构接受 | 在**不改变节点类**的前提下新增操作 | 对 AST 做多种分析 |
| **职责链 CoR** | 处理器持有 **下一个** 处理器 | 让多个对象都有机会处理请求 | 审批流、日志分级 |

> **组合模式常与其他模式协作**：
> - **组合 + 迭代器**：`Directory` 提供深度优先遍历流，客户端可用 Stream 处理整棵树（§7.4）
> - **组合 + 访问者**：当需要对树做很多种不同操作时，用访问者把操作从节点类中抽离（§7.5）
> - **组合 + 享元**：叶子节点若大量重复，用享元共享叶子的内部状态（§7.6）
> - **组合 + 职责链**：审批流中，请求沿组织树向上传递（§7.7）

---

## 8. 优缺点与常见误区

### 8.1 优点

1. **客户端代码极简**：一次 `root.size()` 搞定整棵树，递归被完全隐藏。
2. **扩展性优秀**：新增节点类型不影响已有遍历逻辑（开闭原则）。
3. **天然表达层级**：对象引用关系 = 树结构，无需额外建模。
4. **遍历操作只写一次**：所有递归逻辑集中在 `Directory` 内。

### 8.2 缺点

1. **设计过于泛化时难以约束**：无法在编译期限制"某容器只能装某类叶子"（例如"图片文件夹只能放图片"），只能运行期校验。
2. **透明式的类型安全隐患**：如果把 `add`/`remove` 放接口里，叶子必须抛异常，运行期才暴露错误。
3. **性能开销**：每次 `size()` 都要遍历整棵树。§6.4 实测：一次 `print()` 触发 16 次 `size()` 调用。
4. **新增"仅容器有的行为"较麻烦**：`find()` / `fileCount()` 只能定义在 `Directory` 上，客户端拿到 `FileSystemNode` 时要先 `instanceof` 判断。
5. **接口会持续膨胀**：每加一种"对整棵树做的操作"，都要在接口上加一个方法（这正是 §7.5 引入访问者的动机）。

### 8.3 本项目已知的遗留瑕疵

诚实列出来，不粉饰：

| 瑕疵 | 具体表现 | 生产环境应该怎么改 |
|---|---|---|
| `remove` 的语义不一致 | `add` 不禁止同名，`remove` 却按值删除。§5.6 已分析 | 要么 `add` 拒绝同目录同名，要么 `remove` 改为按引用 |
| `add` 的防环校验是 O(n) | 每次 `add` 都递归检查 `child` 的整棵子树 | 维护一个 `Set<Directory>` 祖先链，或只在批量导入后统一校验一次 |
| `children()` 视图有并发风险 | 遍历时若另一个线程 `add`，会 `ConcurrentModificationException` | 改用 `CopyOnWriteArrayList`，或明确文档标注"非线程安全" |
| `size` 单位不明确 | 字段叫 `size`，注释说 KB，但类型只是一个 `long` | 生产代码应封装成 `record Size(long kb)`，避免单位混用 |
| `print` 直接 `System.out` | 无法重定向到文件或日志框架 | 注入一个 `PrintWriter` 或返回 `String` |

### 8.4 常见误区

| 误区 | 真相 |
|---|---|
| "组合模式就是递归" | 递归只是**实现手段**。组合模式的本质是"**统一接口**让客户端不必区分叶子与容器"。有递归但没统一接口（如 §2.1 反例）不是组合模式 |
| "组合模式 = 树形数据结构" | 树只是它的典型载体。核心是"部分—整体"关系的**一致操作**。LinkedList 内部也是链式结构，但它不是组合模式 |
| "叶子节点必须抛 UnsupportedOperationException" | 那是**透明式**的做法。**安全式**（本项目）把 add/remove 只放容器上，叶子根本不需要实现，编译期更安全 |
| "组合模式和装饰器模式一样" | 结构相似（都是一个对象持有同类型对象），但**意图完全不同**：组合是"一对多的树形聚合"（children 是集合），装饰器是"一对一的功能叠加"（只持有单个 component） |
| "有了 Stream 就不需要组合模式" | Stream 只能遍历**平铺**集合。要遍历嵌套树，你得先手动 `flatMap` 展开——展开本身就是递归，等于把 `Directory.size()` 的逻辑搬到了客户端 |
| "组合模式一定要有一个 `Component` 抽象类" | 接口、抽象类、sealed interface 都可以。关键是"统一契约"，不是"必须叫 Component" |
| "容器节点必须显式实现 size 的累加" | 也可以用 `children.stream().mapToLong(FileSystemNode::size).sum()`。写法不同，语义一致 |
| "组合模式只能表示树" | 也能表示**图**，只要允许一个节点被多个父引用 —— 但那就必须处理共享与循环，复杂度陡增 |

### 8.5 一个反直觉的点：组合模式让"求和"变危险

统一接口带来了一个副作用：**你很容易把容器和叶子的值重复累加**。

```java
// ❌ 错：目录的 size 已经包含孩子的 size，再加一遍等于算两遍
long total = root.stream().mapToLong(FileSystemNode::size).sum();
```

对比 §6.2 第 4 段输出的 "项目下共有 5 个文件" —— 如果 `fileCount()` 也写成
"遍历所有节点，每个节点返回 1"，结果会是 8（3 个目录 + 5 个文件），而不是 5。

**这就是统一接口的代价**：调用方在需要"只统计叶子"时，必须主动过滤。
`fileCount()` 里的 `switch` 正是在做这件事 —— 它把"目录返回孩子的数量"和
"文件返回 1"分开处理，而不是无脑加一。

---

## 9. 面试与自测

### 9.1 面试讲解（30 秒版）

> "组合模式用统一的接口描述'单个对象'和'对象容器'，把树形结构的递归遍历封装在容器内部。典型场景是文件系统：文件和文件夹都实现同一个 `FileSystemNode` 接口，都有 `size()` 方法。文件夹的 `size()` 就是遍历孩子累加 `child.size()`，靠多态自动向下递归。客户端调 `root.size()` 完全不需要知道树有几层。我的实现用了 sealed interface 限制只有两种节点，这样 switch 模式匹配能获得编译期穷尽性检查；`add/remove` 只放在 Directory 上（安全式），避免叶子被迫抛 UnsupportedOperationException；`add()` 里还做了防环校验，否则把父目录加进子目录会导致无限递归栈溢出。"

### 9.2 面试讲解（2 分钟版）

> "组合模式解决的是'部分—整体'层级结构的一致操作问题。
>
> **不用它会怎样**：文件和文件夹是两套类型，客户端每加一个遍历操作（算大小、打印、查找、统计数量）就得重写一遍递归，还要在每个调用点区分类型，递归逻辑散落各处。
>
> **三个角色**：抽象组件 `FileSystemNode` 声明统一契约（`name/size/print`）；叶子 `FileLeaf` 用 record 实现，`size()` 返回自身字段，是递归终点；容器 `Directory` 持有 `List<FileSystemNode>`，`size()` 遍历孩子累加，是递归步骤。
>
> **核心机制是多态 + 递归委托**。`child.size()` 的静态类型是接口，运行期动态绑定：遇到 FileLeaf 就终止，遇到 Directory 就继续向下。容器自身不知道树的深度。
>
> **几个设计取舍**：① 用 sealed interface 而非普通 interface，因为节点只有两种，sealed 让 switch 获得穷尽性检查，新增节点类型时编译器会指出所有待改点 —— 我实测过，漏一个 case 就是 `switch 表达式不包含所有可能的输入值`。② `add/remove` 采用安全式设计只放在 Directory，不放接口——'往文件里加子文件'在业务上无意义，放接口里叶子只能抛异常，把编译期错误推到运行期。③ `add()` 里做了防环校验，能拦住 `root.add(root)` 和 `b.add(root)` 两种成环方式，否则 `size()` 会 `StackOverflowError`。④ 叶子用 record，白拿 `name()`/`size()` 两个访问器，签名正好和接口一致。但 record 的 `equals` 是值相等，这让 `remove` 变成了'按内容删除'，是一个我在文档里明确标注的取舍。
>
> **性能一个真实数字**：`root.size()` 一次会调用 8 次 `size()`（等于节点数）；但 `root.print("")` 一次会调用 16 次 —— 因为每打印一层都要重算一次整棵子树。这是 O(n × depth) 的来源。
>
> **和装饰器的区别**常被问：结构像，但组合是'一对多的树形聚合'（持有 List），装饰器是'一对一的功能叠加'（持有单个 component）；组合的意图是统一操作整体与部分，装饰器的意图是动态增强功能。"

### 9.3 高频追问

**Q1：透明式和安全式组合模式怎么选？**

> 透明式把 add/remove 放抽象组件，客户端可完全无差别操作，但叶子要抛异常，运行期才报错；
> 安全式只放容器上，编译期就安全，但客户端添加子节点时需先确认类型。
> **如果客户端主要在构建期操作容器、遍历期用统一接口（大多数业务场景），选安全式**；
> 如果客户端需要"拿到任意节点就往里塞东西"（如通用树编辑器），选透明式。
>
> 还有一个判断依据：**叶子的数量级**。如果有几十万叶子，透明式意味着每个叶子对象都携带
> 一个"永远会抛异常"的方法 —— 虽然不占额外内存，但接口文档会变得很难看。

**Q2：组合模式的性能问题怎么优化？**

> `size()` 每次全树遍历，O(n)。高频调用时可**缓存结果 + 脏标记**：`Directory` 维护 `cachedSize`
> 和 `dirty` 标志，`add/remove` 时置 `dirty = true` 并向上传播（需维护 parent 引用），
> `size()` 命中缓存直接返回。代价是引入可变状态和 parent 指针，复杂度上升，需权衡。
>
> 另一种更简单的优化：**只缓存不变的部分**。如果树构建完成后只读（这是常见情况），
> 可以在构建结束时调一次 `freeze()`，之后所有 `size()` 直接返回缓存值。
>
> 注意 `print` 里的 `size()` 调用是 §6.4 实测出来的 16 次 —— 优化时优先改这里，
> 因为它比单次 `size()` 更浪费。

**Q3：如何避免循环引用导致的 StackOverflowError？**

> 两种方式：
> ① **add 时校验**（本项目做法），检查 child 是否为 this 或 this 的祖先，是则拒绝；
> ② **遍历时维护一个 `visited` 集合**（`IdentityHashMap`），遇到已访问节点跳过。
>
> 方式①更好，因为它保证数据结构始终是合法的树；方式②只是"遍历时不炸"，树本身还是坏的，
> 而且每次遍历都要付出 `Set` 的开销。**在入口处防守比在每个出口处补救便宜。**

**Q4：JDK 或主流框架里有哪些组合模式？**

> - `java.awt.Container`：`Component` 是抽象组件，`Container` 是组合节点（可装 Component 也可装 Container）
> - **Spring Security** 的 `CompositeFilter`、**Spring MVC** 的 `HandlerInterceptor` 组合
> - **Netty** 的 `CompositeChannelHandler` / `DefaultChannelGroup`
> - **JUnit 5** 的 `Test`（`TestCase` 叶子 / `TestSuite` 容器）——这是教科书级案例
> - **JSON 库**（Jackson/Gson）的树模型：`JsonNode` 抽象，`ObjectNode`/`ArrayNode` 是容器，
>   `TextNode`/`IntNode` 是叶子
> - **MyBatis** 的 `SqlNode`（`MixedSqlNode` 持有 `List<SqlNode>`）——动态 SQL 的 `<if>`/`<where>` 就是这样解析的
> - **Java 9+ 的 `Module` 系统**：模块图

**Q5：为什么 `FileLeaf` 用 record 而不是普通 class？**

> 三个好处：① 自动生成 `name()`/`size()` 访问器，签名与接口完全一致，一行代码都不用写；
> ② 自动生成 `equals`/`hashCode`/`toString`，调试时打印出来直接可读；
> ③ 不可变，天然线程安全。
>
> 一个代价：`equals` 是值相等，导致 `remove` 变成"按内容删除"（§5.6）。
> 如果业务需要"同名文件可以共存"，就不该用 record，或者应该用 `IdentityHashMap` 做索引。

**Q6：如果树非常大（百万节点），组合模式还适用吗？**

> 适用，但要注意三件事：
> ① **递归深度**：JVM 默认栈大约支持几千到一万层。层级超过这个数会 `StackOverflowError`，
>    需要改成显式栈（`Deque`）迭代。
> ② **内存布局**：每个节点一个对象，百万节点意味着百万次指针跳转，CPU 缓存命中率极低。
>    这时候"扁平数组 + 索引"（如 ECS 架构）可能快几个数量级。
> ③ **惰性求值**：`size()` 不必一次算完，可以返回一个 `LongSupplier` 或 `Stream`。
>
> 换句话说：**组合模式管的是"结构一致"，不管"性能"**。结构合适、性能不够时，才考虑底层优化。

### 9.4 自测题

**1. 为什么 `Directory` 的 `children` 字段类型必须是 `List<FileSystemNode>` 而不能是 `List<Directory>`？**

<details><summary>参考答案</summary>

因为组合模式要表达**任意深度的树**。声明为 `List<Directory>` 就只能装子目录，无法装文件（叶子），
树会退化成"只有目录没有文件"的空壳，或者被迫限制成固定层级。

声明为抽象组件类型，容器才能同时容纳叶子和其他容器，这正是"部分—整体"结构的基础。

更实际的说法：如果你发现自己写了 `List<Folder> subFolders` 和 `List<File> files` 两个字段，
那你写的不是组合模式，而是 §2.1 的反例换了个名字。
</details>

**2. 如果删掉 `FileLeaf.size()` 的实现（假设接口给了 default 返回 0），会发生什么？**

<details><summary>参考答案</summary>

代码仍能编译运行，但 `root.size()` 会返回 0——因为所有叶子都返回 0，容器累加再多次也是 0。

**递归仍然会正常终止**（叶子没有 children 可遍历），但业务结果错误。

这说明叶子节点的实现不是"递归终止条件"（终止条件是"没有孩子可遍历"），而是"递归的**数据来源**"。
两件事容易混：
- 终止条件 = 容器里没有可遍历的孩子（由 `for` 循环自然满足）
- 数据来源 = 叶子的 `size()` 返回值
</details>

**3. `root.print("")` 输出的目录大小是 484 KB，这个过程一共调了几次 `Directory.size()`？**

<details><summary>参考答案</summary>

**6 次**，不是 3 次。

拆开算：
- `root.print` 打印自己时调 `size()` → 内部递归触发 `src.size()`、`docs.size()`（累计 3 次）
- `src.print` 打印自己时调 `size()` → 内部递归触发 `docs.size()`（累计 5 次）
- `docs.print` 打印自己时调 `size()`（累计 6 次）

总计 6 次 `Directory.size()` + 10 次 `FileLeaf.size()` = 16 次 `size()` 调用。

**这个数字是用探针实测得到的**（`CompositeSizeCallsProbe`，见 §6.4），
一开始我按直觉写成 3 次，是错的。规律是：总调用次数 = 所有目录的子树节点数之和，
对链状树是 O(n²)。
</details>

**4. 组合模式和装饰器模式在代码结构上很像，如何一眼区分？**

<details><summary>参考答案</summary>

看**持有关系的基数**和**意图**：

- 组合：持有 `List<Component>`（一对多），意图是"把整体和部分统一对待"，容器节点会**聚合**孩子的结果（求和、拼接、计数）。
- 装饰器：持有单个 `Component`（一对一），意图是"在不改变接口的前提下增强功能"，装饰器**转发**请求给被装饰者并在前后加料。

一句话：组合是"我有一个团队"，装饰器是"我穿了一件外套"。

再补一个判据：**装饰器可以无限叠加**（`new Bold(new Italic(new Plain("x")))`），
每层只增强一点；**组合是分层展开**（一层可能有 N 个孩子），每层是并列关系。
</details>

**5. 本项目 `add()` 方法里的防环校验为什么用 `child == this` 而不是 `child.equals(this)`？**

<details><summary>参考答案</summary>

因为要判断的是**同一个对象**（引用相等），而不是"内容相同的对象"。

`FileLeaf` 是 record，`equals` 比较字段值，两个 `new FileLeaf("a.txt", 5)` 会 equals 但是不同对象——
它们同时加入目录是完全合法的。

而循环引用的本质是"某个对象成为了自己的子孙"，这是**引用层面**的问题，必须用 `==` 判断身份。
同理 `contains()` 里也用 `child == target`。

反过来说：如果用 `equals`，会产生一个荒谬的限制 —— **同一目录下不能有两个同名同大小的文件**，
而这在真实文件系统里虽然不常见，但在内存树（比如配置树、菜单树）里是完全可能的。
</details>

**6. `children()` 返回 `Collections.unmodifiableList(children)` 就安全了吗？**

<details><summary>参考答案</summary>

**不完全安全**，有两个坑：

① **它是视图，不是快照**。外部拿到的 `List` 会随原集合变化。如果外部一边遍历一边有人 `add`，
会抛 `ConcurrentModificationException`。

② **它挡不住"通过元素修改"**。`unmodifiableList` 只禁止对列表本身做增删改，
但如果元素本身可变（比如 `Directory` 的 `name` 有 setter），外部照样能改元素内部状态。
真正的不可变需要"列表不可变 + 元素不可变"。

本项目的 `FileLeaf` 是 record（不可变），`Directory` 的 `name` 是 final，所以这个问题不存在。
但如果将来给 `Directory` 加了 `rename(String)`，`children()` 的保护就出现了缺口。
</details>

**7. 如果把 `size()` 的返回类型从 `long` 改成 `int`，什么情况下会出问题？**

<details><summary>参考答案</summary>

累加超过 `Integer.MAX_VALUE`（2,147,483,647）时溢出成负数。

按 KB 算，`int` 最多表示约 **2 TB**。听起来很大，但：
- 一台装了几个月日志的服务器轻松超过
- 云存储的"统计整个桶的大小"必然是 TB 甚至 PB 级
- 更隐蔽的是：溢出后 `total` 变成负数，而 `size()` 的调用方往往不会检查负数

而且**溢出是静默的** —— Java 的整数运算不抛异常，直接回绕。所以正确做法是从一开始就用 `long`，
不要等出了问题再改（改的时候所有调用方、所有序列化格式都要跟着动）。
</details>

---

## 10. 附录

### 10.1 文件清单

| 文件 | 行数 | 角色 |
|---|---|---|
| `FileSystemNode.java` | 55 | sealed 抽象组件，声明 `name` / `size` / `print` |
| `FileLeaf.java` | 44 | record 叶子，紧凑构造器做参数校验 |
| `Directory.java` | 172 | 容器，含防环校验、只读视图、`find`、`fileCount` |
| `Client.java` | 94 | 六个场景的演示入口 |
| `README.md` | 本文档 | — |

### 10.2 本文引用的探针

探针不属于交付代码，只用于取证。全部放在原项目的 `_probe` 目录下。

| 探针 | 用途 | 输出 |
|---|---|---|
| `CompositeDemo1Probe.java` | 原项目 demo1 的 `remove` 语义与接口能力 | 21 行 |
| `CompositeDemo2Probe.java` | 原项目 demo2 的缩进、null、校验、防环 | 16 行 / 605 字节 |
| `CompositeProProbe.java` | 重构版的视图语义、防环、校验、空目录 | 17 行 |
| `CompositeSizeCallsProbe.java` | `print()` 触发 `size()` 的次数 | 13 行 |
| `CompositeNameClashProbe.java` | `File` 与 `java.io.File` 的命名冲突 | 1 行：`File 这个名字解析成了：java.io.File` |
| `CompositeSealedProbe.java` | sealed 的编译期穷尽性检查 | **故意编译失败**：`switch 表达式不包含所有可能的输入值` |

### 10.3 实测环境

| 项 | 值 |
|---|---|
| JDK | `javac 25.0.4.1` |
| 运行参数 | `java -Dstdout.encoding=UTF-8 -cp out com.patterns.structural.composite.Client` |
| 输出统计 | 27 行 / 1097 字节 / SHA-256 前 16 位 `DF2F01838AA366E1` |
| 原 demo1 输出 | 6 行 / 148 字节 |
| 原 demo2 输出 | 6 行 / 168 字节 |

> **关于输出统计的口径**：必须用 `Start-Process -RedirectStandardOutput` 落盘后统计。
> 直接在 PowerShell 里用 `>` 重定向会转码，导致字节数失真（同一段输出在 UTF-8 与 GBK 下
> 字节数差一个汉字数）。本文档所有字节数都是 UTF-8 口径。

### 10.4 本次修订记录

| 改动 | 原因 |
|---|---|
| 合并原项目 demo1 / demo2 的能力 | demo1 只有 `display`，demo2 只有 `size`，各缺一半 |
| `File` → `FileLeaf`，`Folder` → `Directory` | 避开与 `java.io.File` 同名；实测单类型导入会静默遮蔽同包类 |
| `showDetails()` → `print(String indent)` | 原版不带缩进，层级信息在输出里丢失 |
| 新增 `add()` 的 null 校验与防环校验 | 原版 `add(null)` 延迟到遍历才 NPE；`add(loop)` 直接栈溢出 |
| 新增构造器参数校验 | 原版 `new File("", -5)` 合法 |
| `remove()` 由 `void` 改为 `boolean` | 原版调用方无法知道是否删除成功 |
| 接口由 `interface` 改为 `sealed interface` | 配合 switch 获得编译期穷尽性检查 |
| 新增 `children()` 只读视图 | 防止外部绕过 `add()` 的防环校验 |
| 新增 `find` / `fileCount` | 演示"统一接口"在更多操作上的收益 |
| 修正原文"共 3 次 `Directory.size()`" | 探针实测为 6 次，原文按直觉推导，是错的 |
