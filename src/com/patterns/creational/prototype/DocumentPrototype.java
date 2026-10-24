package com.patterns.creational.prototype;

/**
 * 抽象原型：文档原型接口。
 *
 * <p>声明克隆方法 {@link #cloneDocument()}，所有具体原型必须实现。
 * 使用方只依赖此接口，不关心具体是 Word 文档还是 PPT 文档。
 *
 * <p>注意：本接口<strong>不继承 {@link Cloneable}</strong>。
 * {@code Cloneable} 是标记接口，继承它不会让子接口获得 {@code clone()} 方法。
 * 具体实现类（如 {@link WordDocument}）自行实现 {@code Cloneable} 即可。
 *
 * @author design-patterns-pro
 */
public interface DocumentPrototype {

    /**
     * 克隆当前文档，返回一个深拷贝副本。
     *
     * @return 文档副本
     */
    DocumentPrototype cloneDocument();

    /**
     * 展示文档内容（用于演示）。
     */
    void show();
}
