package com.patterns.structural.facade.subsystem;

import com.patterns.structural.facade.model.User;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link UserRepository} 的内存实现，用 HashMap 模拟数据库。
 *
 * <p><b>相比原项目改了什么？</b>
 * <ul>
 *   <li>{@code findById} 由「返回 null」改为「返回 {@link Optional}」。
 *       原实现中外观层必须写 {@code if (user != null)}，一旦漏判就是 NPE；
 *       Optional 把"可能不存在"写进方法签名，编译器强制调用方处理。</li>
 *   <li>{@code insert}/{@code update} 合并为 {@code save}。
 *       对 Map 存储而言两者实现完全相同（都是 put），分成两个方法只是噪音；
 *       原外观的 {@code renameUser} 也因此少了一次"先查后改"的判断分支。</li>
 *   <li>{@code delete} 返回 boolean，让外观能区分"删掉了"和"本来就不存在"。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<Integer, User> table = new HashMap<>();

    @Override
    public void save(User user) {
        table.put(user.id(), user);
        System.out.println("      [Repository] 写入记录：id=" + user.id() + ", name=" + user.username());
    }

    @Override
    public Optional<User> findById(int id) {
        return Optional.ofNullable(table.get(id));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return table.values().stream()
                .filter(u -> u.username().equals(username))
                .findFirst();
    }

    @Override
    public boolean deleteById(int id) {
        User removed = table.remove(id);
        System.out.println("      [Repository] 删除记录：id=" + id
                + (removed != null ? "（" + removed.username() + "）" : "（记录不存在）"));
        return removed != null;
    }

    @Override
    public List<User> findAll() {
        return table.values().stream()
                .sorted(Comparator.comparingInt(User::id))
                .toList();
    }
}
