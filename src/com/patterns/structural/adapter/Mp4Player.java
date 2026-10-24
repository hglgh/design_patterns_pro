package com.patterns.structural.adapter;

/**
 * 具体适配者：MP4 播放器。
 *
 * @author design-patterns-pro
 */
public class Mp4Player implements AdvancedMediaPlayer {
    @Override
    public void playVlc(String fileName) {
        // 不支持，留空
    }

    @Override
    public void playMp4(String fileName) {
        System.out.println("[Mp4Player] 播放 MP4 文件：" + fileName);
    }
}
