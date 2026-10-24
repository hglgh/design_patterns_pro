package com.patterns.creational.prototype;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原型注册表（Prototype Registry）。
 *
 * <p>原型模式的常见用法：把常用原型预先注册到表里，
 * 使用方按 key 取出原型并克隆，避免重复构造复杂对象。
 *
 * <p>典型场景：
 * <ul>
 *   <li>游戏里"小兵"原型注册，每次出兵克隆一份。</li>
 *   <li>文档模板库，用户选模板后克隆一份编辑。</li>
 *   <li>配置对象缓存，避免重复解析配置文件。</li>
 * </ul>
 *
 * @author design-patterns-pro
 */
public class DocumentRegistry {

    private final Map<String, DocumentPrototype> prototypes = new HashMap<>();

    /**
     * 注册一个原型。
     *
     * @param key       原型标识（如 "resume-template"）
     * @param prototype 原型实例
     */
    public void register(String key, DocumentPrototype prototype) {
        prototypes.put(key, prototype);
    }

    /**
     * 按 key 获取原型的克隆副本。
     *
     * @param key 原型标识
     * @return 克隆出的新文档
     * @throws IllegalArgumentException 如果 key 未注册
     */
    public DocumentPrototype get(String key) {
        DocumentPrototype prototype = prototypes.get(key);
        if (prototype == null) {
            throw new IllegalArgumentException("未注册的原型：" + key);
        }
        return prototype.cloneDocument();
    }

    /**
     * 预加载常用模板（演示用）。
     *
     * @return 已填充默认模板的注册表
     */
    public static DocumentRegistry withDefaults() {
        DocumentRegistry registry = new DocumentRegistry();
        registry.register("resume",
                new WordDocument("简历模板", "请填写个人信息", List.of("cover.png")));
        registry.register("report",
                new WordDocument("周报模板", "请填写本周工作总结", List.of("logo.png")));
        return registry;
    }
}
