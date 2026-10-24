package com.patterns.structural.facade.subsystem;

import com.patterns.structural.facade.model.User;

import java.util.List;
import java.util.Optional;

/**
 * 子系统 1：持久化层。负责用户数据的存取，<b>不关心业务规则</b>。
 *
 * <p><b>为什么子系统要抽接口？</b>
 * 让外观（{@code UserServiceFacade}）依赖抽象而非具体实现，
 * 将来把内存 Map 换成 MySQL、Redis 或远程 HTTP 服务时，外观代码一行都不用改。
 * 这也是原项目做得对的地方，予以保留。
 *
 * @author design-patterns-pro
 */
public interface UserRepository {

    /**
     * 保存用户（存在则覆盖）。
     *
     * @param user 用户对象
     */
    void save(User user);

    /**
     * 按 ID 查询。
     *
     * @param id 用户 ID
     * @return 用户，不存在返回 {@link Optional#empty()}
     */
    Optional<User> findById(int id);

    /**
     * 按用户名查询，用于注册时查重。
     *
     * @param username 用户名
     * @return 用户，不存在返回 {@link Optional#empty()}
     */
    Optional<User> findByUsername(String username);

    /**
     * 删除用户。
     *
     * @param id 用户 ID
     * @return 是否确实删除了一条记录
     */
    boolean deleteById(int id);

    /**
     * @return 全部用户，按 ID 升序
     */
    List<User> findAll();
}
