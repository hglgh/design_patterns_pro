package com.patterns.creational.prototype;

import java.util.ArrayList;
import java.util.List;

/**
 * 具体原型：Word 文档。
 *
 * <p>实现 {@link Cloneable} 以支持 {@code Object.clone()}，
 * 并在 {@link #cloneDocument()} 中对可变字段（{@code images}）做深拷贝，
 * 避免克隆体与原型共享引用。
 *
 * @author design-patterns-pro
 */
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

    /**
     * 克隆文档：先调用 {@code Object.clone()} 做浅拷贝，
     * 再对 {@code images} 做深拷贝。
     *
     * @return 深拷贝的文档副本
     */
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

    @Override
    public void show() {
        System.out.println("Title: " + title);
        System.out.println("Content: " + content);
        System.out.println("Images: " + images);
    }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void addImage(String image) { this.images.add(image); }

    public String getTitle() { return title; }

    /**
     * 返回图片清单的只读副本。
     *
     * <p>不能直接返回 {@code images} 字段：那等于把内部可变集合的引用交出去，
     * 外部一句 {@code getImages().add("x")} 就能改掉原型的状态。
     * 这与“深拷贝”是同一个问题的两面——要么在拷贝时隔离，要么在暴露时隔离。
     *
     * @return 不可变的图片清单
     */
    public List<String> getImages() { return List.copyOf(images); }
}
