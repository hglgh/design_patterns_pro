# 备忘录模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「撤销为什么要三件套」，
> 以及为什么不能让客户端自己保存对象字段。
>
> 第二层：[README.md](README.md)（不透明快照、历史监听、容量限制、元信息）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**备忘录就是给对象拍一张「当时的照片」，以后想回去时，把照片还给它；**
**拍照和保管照片的人，都不需要看懂照片里有什么。**

### 什么需求会让开发者想到它

一个在线文档编辑器，用户会：

- 输入文字；
- 调整字号；
- 按 `Ctrl+Z` 撤销；
- 按 `Ctrl+Y` 重做。

第一版很直接：`Editor` 的 `text`、`cursor`、`fontSize` 都是 `public`，
客户端在每次编辑前自己记住这几个字段，撤销时再写回去。

能跑。但只要有一个人忘了备份某个字段，撤销就会「只回去一半」。

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class OldEditor {
    String text = "";
    int cursor;
    int fontSize = 14;
}

// 客户端自己备份「它记得的」两个字段
String savedText = editor.text;
int savedCursor = editor.cursor;

editor.text = "设计模式真有趣";
editor.cursor = 7;
editor.fontSize = 18;

// 撤销
editor.text = savedText;
editor.cursor = savedCursor;
```

真实输出：

```text
初始：text=「设计模式」, cursor=4, fontSize=14
编辑后：text=「设计模式真有趣」, cursor=7, fontSize=18
撤销后：text=「设计模式」, cursor=4, fontSize=18
```

文字和光标回去了，**字号停在 18**。
这不是客户端粗心，而是「该备份哪些字段」本来就不该由客户端决定。

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
客户端 ──读取/写入──▶ Editor.text / cursor / fontSize
   │
   └── 自己维护「上一次的 text/cursor」
```

客户端直接依赖 `Editor` 的**全部内部字段**。
`Editor` 的字段一改，客户端就要跟着改；字段一多，备份代码就变成一份手写的字段清单。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 增加「选中区域」字段 | 所有备份/恢复代码都要加一个变量 | 调用方分散，漏一处就产生半截状态 |
| 撤销时连字号也恢复 | 在客户端补一行 `fontSize` 备份 | 靠人记住，编译器不提醒 |
| 支持多步撤销 | 客户端自己维护一个 `List`，还要处理重做 | 时间线逻辑侵入业务代码 |
| 限制历史条数 | 客户端自己裁剪 `List` | 每个调用方各写一套 |
| 不允许外部改字段 | 把字段改 `private`，客户端立刻编译失败 | 说明原来的「撤销」建立在破坏封装上 |

### 3.3 为什么加一个 `if` 不够

重复代码不是核心问题，核心是**「快照应该包含什么」这个知识放错了地方**。
只有 `Editor` 自己知道 `text`、`cursor`、`fontSize` 是一组必须一起恢复的状态。
客户端不可能可靠地维护这份知识，每加一个字段就会多一次遗漏的机会。

---

## 4. 从问题推导出设计

### 第一步：让 Editor 自己拍快照

```java
class Editor {
    private String text = "";
    private int cursor;
    private int fontSize = 14;

    Snapshot snapshot() {
        return new Snapshot(text, cursor, fontSize);
    }

    void restore(Snapshot snapshot) {
        this.text = snapshot.text;
        this.cursor = snapshot.cursor;
        this.fontSize = snapshot.fontSize;
    }
}
```

- **为什么快照由 `Editor` 创建？** 只有它知道哪些字段构成一个完整状态。
- **为什么 `restore` 也由 `Editor` 执行？** 只有它知道怎么把字段写回去。
- **如果让客户端 `new Snapshot(...)` 会怎样？** 客户端又要知道字段清单，封装白做了。

### 第二步：把快照做成「只能看预览」的对象

```java
static final class Snapshot {
    private final String text;
    private final int cursor;
    private final int fontSize;

    private Snapshot(String text, int cursor, int fontSize) {
        this.text = text;
        this.cursor = cursor;
        this.fontSize = fontSize;
    }

    String preview() {
        return text.length() <= 6 ? text : text.substring(0, 6) + "…";
    }
}
```

- **为什么字段是 `private final`？** 快照代表过去，不能被后来的人改掉。
- **为什么只暴露 `preview()`？** 保管者需要展示「这是哪一步」，但不需要拿到全文。
- 真实工程里还会加 `ownerToken` 之类的校验，防止别的对象拿快照乱恢复；
  入门版用嵌套类 + 私有字段示意，深度版会讲完整做法。

### 第三步：让 History 只管时间线

```java
class History {
    private final Deque<Editor.Snapshot> past = new ArrayDeque<>();
    private final Deque<Editor.Snapshot> future = new ArrayDeque<>();

    void save(Editor.Snapshot snapshot) {
        past.push(snapshot);
        future.clear();
    }

    Editor.Snapshot undo(Editor.Snapshot current) {
        if (past.isEmpty()) {
            return null;
        }
        future.push(current);
        return past.pop();
    }

    Editor.Snapshot redo(Editor.Snapshot current) {
        if (future.isEmpty()) {
            return null;
        }
        past.push(current);
        return future.pop();
    }
}
```

- **为什么需要 `History`？** 撤销/重做是时间线问题，不该塞进 `Editor` 的业务方法。
- **为什么 `History` 不读快照内容？** 它只负责存取；内容对它是黑盒。
- **如果让 `History` 直接操作 `Editor` 字段会怎样？** 保管者又依赖了编辑器的内部结构。

### 最终对象关系

```text
客户端
  │ 1. 编辑
  ▼
Editor（Originator）──创建──▶ Snapshot（Memento，不透明）
  ▲                              │
  │ 4. restore(snapshot)         │ 2. 保存
  │                              ▼
  └──────────────────────── History（Caretaker）
                                 │ 3. undo / redo
                                 └─ 只保管，不拆开看
```

---

## 5. 用具体数据走一遍

### 5.1 保存历史

```java
Editor editor = new Editor();
History history = new History();
history.save(editor.snapshot());       // 基线：空文档

editor.type("设计模式");
history.save(editor.snapshot());       // 状态 A：text=设计模式, fontSize=14

editor.setFontSize(18);
history.save(editor.snapshot());       // 状态 B：text=设计模式, fontSize=18

editor.type("真有趣");                  // 当前：text=设计模式真有趣, fontSize=18
```

注意：**最后一次编辑之后没有再保存**。
历史里保存的是「可以回去的状态」，当前状态由 `Editor` 自己持有。
如果编辑后再保存当前状态，第一次撤销会回到「和现在一样」的状态，看起来像没反应——
这是实现撤销时非常常见的一个坑。

真实输出（节选）：

```text
输入「设计模式」：text=「设计模式」, cursor=4, fontSize=14
字号改为 18：text=「设计模式」, cursor=4, fontSize=18
输入「真有趣」：text=「设计模式真有趣」, cursor=7, fontSize=18
```

### 5.2 第一次撤销

```java
Editor.Snapshot current = editor.snapshot();  // 当前：真有趣
Editor.Snapshot target = history.undo(current);
// past 弹出状态 B，future 压入当前
editor.restore(target);
```

```text
撤销后：text=「设计模式」, cursor=4, fontSize=18
```

文字回到「设计模式」，字号仍是 18——因为状态 B 就是「输入完设计模式后调了字号」。

### 5.3 第二次撤销与重做

```text
撤销后：text=「设计模式」, cursor=4, fontSize=14
重做后：text=「设计模式」, cursor=4, fontSize=18
历史：可撤销 2 步，可重做 1 步
```

`undo` 把当前状态压进 `future`，`redo` 再把它弹回来。
**撤销不是删除历史，而是把「现在」移到另一条栈上。**

### 5.4 最容易写错的三处

1. **保存时机**：保存「操作后的状态」时，当前状态不要再存一遍；否则第一次撤销无效。
   更常见的做法是**每次修改前保存**，两种方式选一种，不能混。
2. **`save` 要清空 `future`**：撤销几步后重新编辑，原来的重做分支就失效了。
   示例里 `save()` 中的 `future.clear()` 就是做这件事。
3. **可变字段要深拷贝**：如果 `Snapshot` 里存的是 `List` 或 `StringBuilder` 的引用，
   之后的修改会污染历史。示例用 `String`（不可变）避开了这个问题，真实项目要显式复制。

---

## 6. 改造前后对比

### 新增「选中区域」字段

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | 所有保存/恢复的调用方 | `Editor` + `Snapshot` |
| 要改的代码 | 每个备份点加变量、每个恢复点加赋值 | `snapshot()`/`restore()` 各加一行 |
| `History` | 不存在的逻辑，客户端各自实现 | 完全不用改 |
| 漏改的后果 | 撤销后选区错乱，且很难发现 | 字段在同一个方法里，漏改一眼能看见 |

### 但调用方仍然要改

- 仍然要在合适的时机调用 `history.save(...)`；
- 仍然要决定历史保留多少步、超出后丢哪一端；
- 如果快照很大，仍然要选择增量、压缩或落盘策略。

### 引入模式新增的成本

- 每次快照都复制一份状态，内存随编辑次数增长；
- 必须给历史设上限，否则长时间运行会 OOM；
- 快照里的可变对象要深拷贝，拷贝逻辑本身也可能出错；
- 只是「一个布尔开关的回退」，用不着三件套。

---

## 7. 适用边界

### 适合用

- 需要**多步撤销/重做**，且状态由多个字段共同构成；
- 需要在不暴露对象内部结构的前提下保存/恢复状态；
- 需要「草稿恢复」「操作回滚」这类**内存中的时间旅行**；
- 状态快照比「反向操作」更容易实现（比如文本编辑）。

### 不适合用

- 只需要回退一步，一个 `boolean` 或一个临时变量就够；
- 状态非常大且变化很小——每次全量快照成本太高，考虑增量/命令模式；
- 需要跨进程持久化、审计、版本管理——那是存储/版本系统的问题，不是备忘录模式的强项。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 命令模式撤销 | 命令靠 `undo()` **反向执行**；备忘录靠**保存整份状态**再恢复。命令更省内存，备忘录更容易做对 |
| 原型 | 原型复制对象是为了**产生新对象**；备忘录复制状态是为了**回到过去**，并不暴露副本 |
| 状态模式 | 状态模式管理「现在是什么状态」；备忘录管理「过去是什么状态」 |

---

## 8. 小练习

### 练习 1（场景判断）

一个后台表单有 20 个字段，用户希望「保存草稿」和「恢复到上次保存」。
要不要用备忘录？

**答案**：如果只是「存一份到服务器、以后读回来」，用序列化/数据库更直接，不需要备忘录。
如果要求**同一页面内多步撤销**，且字段之间有关联（必须一起恢复），备忘录的三方结构才划算。
「保存草稿」通常只需要一份快照；「多步撤销」才需要 `History`。

### 练习 2（解释设计原因）

为什么 `Snapshot` 的字段是 `private`，而且只提供 `preview()`？
如果提供 `getText()` 会怎样？

**答案**：备忘录的关键是「保管者看不见内容」。提供 `getText()` 后，`History` 或任何拿到快照的人
都可以读取甚至依赖内部结构，封装重新被打破；以后 `Editor` 改字段，外部代码可能受影响。
`preview()` 只提供展示所需的最小信息，不暴露完整状态。

### 练习 3（执行过程）

历史栈里依次压入：基线、状态 A、状态 B。当前是状态 C（未保存）。
连续撤销两次，再重做一次，当前状态是什么？`future` 里有什么？

**答案**：第一次撤销回到 B，`future=[C]`；第二次撤销回到 A，`future=[B, C]`；
重做弹出 B，当前回到 B，`future=[C]`。`past` 里剩下基线、A。
关键：撤销把「当前」压入 `future`，所以当前状态永远不会丢。

### 练习 4（边界推导）

如果文档有 10MB，用户每输入一个字符都保存一次快照，会发生什么？怎么改？

**答案**：内存会迅速膨胀（每步一份 10MB），而且大多数内容没有变化。
可以改成：只保存**变化的区间/增量**，或者用命令模式记录「插入了什么、删除了什么」；
也可以限制历史步数、把旧快照压缩/落盘。备忘录不是「无脑存全量」，实现时要考虑成本。

---

## 读完后应能回答的 5 个问题

1. 为什么快照必须由 `Editor` 自己创建，而不能让客户端 `new`？
2. `History` 为什么不能读取快照内容？
3. 保存时机选错（保存了当前状态）会导致什么现象？
4. 备忘录和命令模式的撤销，各自适合什么场景？
5. 快照很大时，应该从哪些方向优化？

---

## 下一步

- 深度文档：[README.md](README.md)（不透明快照、ownerToken、历史监听、容量限制）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
