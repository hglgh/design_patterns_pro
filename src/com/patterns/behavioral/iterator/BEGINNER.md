# 迭代器模式：通俗入门

> **这是第一层「入门」材料。** 目标：说清「为什么要把遍历从集合里拿出来」，
> 以及什么时候直接用 Java 自带的 `Iterator` 就行。
>
> 第二层：[README.md](README.md)（工作流节点的广度/深度/过滤遍历）。

**可运行代码**：[beginner/BeginnerDemo.java](beginner/BeginnerDemo.java)
（纯 Java，无框架；输出与本文第 5 节逐字一致）

---

## 1. 一句话说人话

**迭代器就是把「下一个是谁」这件事从集合里拿出来，交给一个专门的小对象记着；**
**客户端只管问「还有吗」「给我下一个」，不用知道数据到底存在数组、链表还是树里。**

### 什么需求会让开发者想到它

一个音乐 App 的播放列表：

- 默认正序播放；
- 用户可以切成倒序；
- 以后还想加「按歌手过滤」「随机播放」。

第一版很直接：`Playlist` 内部是 `List<Song>`，客户端拿到这个 List，自己写 `for` 循环。
正序没问题，倒序再写一个反向循环。看起来也能用。

问题出在：**客户端一旦拿到内部 List，就什么都能做**——包括 `remove`。
播放列表少了歌，不一定是播放器的 bug，可能只是某个页面顺手删了。

---

## 2. 先看不使用设计模式的代码

改造前（完整版在 [BeginnerDemo.java](beginner/BeginnerDemo.java) 的 `before()`）：

```java
class Playlist {
    private final List<Song> songs = new ArrayList<>();

    List<Song> getSongs() {
        return songs;          // 直接把内部集合交出去
    }
}
```

客户端：

```java
List<Song> songs = playlist.getSongs();
for (int i = 0; i < songs.size(); i++) {
    System.out.println(songs.get(i));
}

for (int i = songs.size() - 1; i >= 0; i--) {
    System.out.println(songs.get(i));
}

songs.remove(0);   // 客户端可以随手删
```

真实输出（节选）：

```text
正序：
  晴天 - 周杰伦
  平凡之路 - 朴树
  起风了 - 买辣椒也用券
倒序（客户端自己写反向循环）：
  起风了 - 买辣椒也用券
  平凡之路 - 朴树
  晴天 - 周杰伦
客户端 remove 后，播放列表只剩 2 首
```

---

## 3. 为什么原来的设计不方便

### 3.1 谁依赖谁

```text
客户端 ──依赖──▶ List<Song>（具体集合）
                    ▲
                    │ 直接暴露
                Playlist
```

客户端依赖的不是「播放列表能遍历」，而是「它恰好用 `List` 存」。
这个依赖一旦成立，`Playlist` 换实现（数组、链表、数据库分页）就会打断所有客户端。

### 3.2 每个新需求要改哪里

| 新需求 | 改造前要改什么 | 代价 |
|---|---|---|
| 增加倒序 | 每个使用处都写一遍反向 `for` | 遍历逻辑重复，容易越界 |
| 增加「按歌手过滤」 | 每个使用处都写 `if` 跳过 | 过滤规则散落，无法复用 |
| 内部换成数组 | 改 `getSongs()` 返回类型 + 所有客户端 | 一处存储变更，牵连全部调用方 |
| 禁止客户端删除 | 返回副本或 `unmodifiableList` | 能做，但遍历方式仍然没封装 |
| 统计「遍历到第几个」 | 客户端自己维护下标 | 遍历状态和业务逻辑混在一起 |

### 3.3 为什么「返回不可变 List」还不够

`Collections.unmodifiableList(songs)` 能阻止删除，但解决不了另外两件事：

1. 客户端仍然依赖 `List` 的下标访问方式，换成树/图就废了；
2. 倒序、过滤等遍历算法仍然要每个客户端各写一遍。

迭代器要解决的是**遍历方式和存储结构解耦**，不只是防删除。

---

## 4. 从问题推导出设计

### 第一步：把「当前位置」抽出来

```java
interface SongIterator {
    boolean hasNext();
    Song next();
}
```

- **为什么需要它？** 客户端要一个统一的方式问「还有吗」「下一个」。
- **为什么不直接把 List 给客户端？** 那客户端就绑定了 List 的下标模型。
- **不加会怎样？** 每种集合都要给客户端一套自己的遍历 API。

### 第二步：让集合负责创建迭代器

```java
class Playlist {
    private final List<Song> songs = new ArrayList<>();

    SongIterator iterator() {
        return new ForwardIterator(songs);
    }

    SongIterator reverseIterator() {
        return new ReverseIterator(songs);
    }
}
```

集合仍然拥有数据，但**不再把数据交出去**。
它只交出「一个会遍历它的对象」。

### 第三步：每个迭代器自己记 cursor

```java
class ForwardIterator implements SongIterator {
    private final List<Song> songs;
    private int cursor;

    public boolean hasNext() {
        return cursor < songs.size();
    }

    public Song next() {
        if (!hasNext()) {
            throw new IllegalStateException("没有更多元素");
        }
        return songs.get(cursor++);
    }
}
```

倒序迭代器只是把 `cursor` 初始化成 `size - 1`，`next()` 里递减。
**存储没变，遍历方向变了。**

### 第四步：客户端只依赖接口

```java
private static void printAll(SongIterator iterator) {
    while (iterator.hasNext()) {
        System.out.println(iterator.next());
    }
}
```

```java
printAll(playlist.iterator());
printAll(playlist.reverseIterator());
```

同一个 `printAll`，不关心是正序还是倒序，更不关心底层是 `List` 还是数组。

### 最终对象关系

```text
客户端 ──依赖──▶ SongIterator（接口）
                    ▲
        ┌───────────┴───────────┐
 ForwardIterator          ReverseIterator
        │                       │
        └──────持有 List<Song>───┘
                    ▲
                Playlist（创建迭代器，不暴露 List）
```

---

## 5. 用具体数据走一遍

播放列表有 3 首歌：`晴天`、`平凡之路`、`起风了`。

### 5.1 正序

```text
cursor=0：hasNext? 0<3 true  → next() 返回 songs[0]，cursor=1
cursor=1：hasNext? 1<3 true  → next() 返回 songs[1]，cursor=2
cursor=2：hasNext? 2<3 true  → next() 返回 songs[2]，cursor=3
cursor=3：hasNext? 3<3 false → 结束
```

### 5.2 倒序

```text
cursor=2：hasNext? 2>=0 true  → 返回 songs[2]，cursor=1
cursor=1：hasNext? 1>=0 true  → 返回 songs[1]，cursor=0
cursor=0：hasNext? 0>=0 true  → 返回 songs[0]，cursor=-1
cursor=-1：hasNext? false     → 结束
```

### 5.3 最容易写错的两处

1. **`next()` 不检查就越界**：如果客户端不先问 `hasNext()`，正序会 `IndexOutOfBoundsException`，
   倒序会访问 `songs[-1]`。示例里主动抛 `IllegalStateException`，是为了让错误信息更明确。
2. **迭代期间修改集合**：示例的迭代器直接持有原 List，遍历时 `add/remove` 会让结果不可预测。
   Java 标准库用 `modCount` 做 fail-fast；自己写迭代器时，要么禁止修改，要么定义清楚规则。

---

## 6. 改造前后对比

### 新增「按歌手过滤」

| | 改造前 | 改造后 |
|---|---|---|
| 要改的类 | 每个客户端 | 新增 `FilteringIterator`（或 Playlist 加一个方法） |
| 要改的旧代码 | 每个循环里加 `if` | 客户端只换成新的迭代器 |
| 复用 | 过滤逻辑复制 N 份 | 一个迭代器到处用 |
| 存储变更 | 所有客户端受影响 | 只要迭代器适配，客户端不变 |

### 但调用方仍然要做选择

- 客户端仍然要决定「用正序还是倒序」；
- 新增遍历方式通常要在 `Playlist` 上再加一个工厂方法（或者让客户端自己包装）；
- 迭代器本身不是线程安全的，多线程遍历要额外处理。

### 引入模式新增的成本

- 每种遍历一个类；
- 迭代器和集合的生命周期要协调（集合被改，迭代器可能失效）；
- Java 已经有 `Iterable`/`Iterator`，业务代码里**优先用标准接口**，手写迭代器主要用于教学或树/图等特殊结构。

---

## 7. 适用边界

### 适合用

- 一个集合需要**多种遍历方式**（正序、倒序、过滤、广度/深度优先）；
- 不想把内部存储结构暴露给客户端；
- 遍历算法比较复杂，希望复用而不是每个调用方各写一遍；
- 遍历状态需要独立于集合（多个客户端同时遍历互不干扰）。

### 不适合用

- 只是遍历一次 `List`，直接用 `for-each` 最清楚；
- 集合本身就是标准库类型，没有自定义遍历需求——用现成的 `Iterator`；
- 遍历逻辑只有一行，封装成类反而增加阅读成本。

### 和容易混淆的模式对比

| 模式 | 关键区别 |
|---|---|
| 组合 | 组合关注「树形结构如何统一处理」；迭代器关注「如何逐个访问」，不关心结构 |
| 访问者 | 访问者把「对每个元素做什么」外置；迭代器只负责「按顺序拿出来」 |
| 工厂方法 | 迭代器常由集合的工厂方法创建，但工厂方法关注创建，迭代器关注遍历 |

---

## 8. 小练习

### 练习 1（场景判断）

一个分页查询接口返回 `Page<User>`，页面需要「正序展示」「倒序展示」「只看活跃用户」。
要不要自己写迭代器？

**答案**：如果三种遍历都基于同一个 `List<User>`，而且只在少数几个页面用，
用 `Stream` 的 `sorted`/`filter` 更短。只有当遍历逻辑需要复用、或者数据源不是 List
（比如游标分页、树形组织架构）时，自定义迭代器才值得。
判断标准是：**遍历方式是否成为需要独立维护和复用的概念**。

### 练习 2（解释设计原因）

`next()` 在没元素时抛 `IllegalStateException`，而不是返回 `null`。为什么？

**答案**：返回 `null` 会把「没有下一个」和「下一个元素本身就是 null」混在一起，
客户端无法区分。抛异常能立即暴露调用错误（没先 `hasNext()`）。
如果业务允许空元素，可以用 `Optional`，但迭代器协议本身更常见的是抛 `NoSuchElementException`。

### 练习 3（执行过程）

倒序迭代器初始化时 `cursor = songs.size() - 1`，列表有 3 个元素。
连续调用 4 次 `next()`（中间不检查 `hasNext()`）会发生什么？

**答案**：第 1～3 次分别返回下标 2、1、0，`cursor` 变成 -1；
第 4 次 `hasNext()` 为 false，`next()` 抛 `IllegalStateException`。
所以「先 hasNext 再 next」不是形式，是协议。

### 练习 4（设计对比）

`printAll(SongIterator)` 如果改成 `printAll(Playlist)`，会失去什么？

**答案**：`printAll` 会重新依赖 `Playlist` 这个具体集合，无法复用于其他集合；
也无法在调用时自由选择正序/倒序（除非再加参数和 `if`）。
依赖接口后，任何 `SongIterator` 都能传进来，遍历方式和打印逻辑解耦。

---

## 读完后应能回答的 5 个问题

1. 迭代器把什么状态从集合里拿走了？为什么这样能支持多种遍历？
2. 为什么「返回不可变 List」不等于「封装了遍历」？
3. `hasNext()` 和 `next()` 的协议是什么？不遵守会怎样？
4. 什么时候应该直接用 Java 的 `Iterator`，而不是自己写？
5. 迭代器和访问者都涉及「遍历元素」，它们各自负责什么？

---

## 下一步

- 深度文档：[README.md](README.md)（工作流节点的广度/深度/过滤遍历）
- 工程版入口：[Client.java](Client.java)
- 模式对比：[docs/02-pattern-comparison.md](../../../../../docs/02-pattern-comparison.md)
- 现代场景：[docs/06-modern-scenarios.md](../../../../../docs/06-modern-scenarios.md)
