package com.patterns.structural.adapter;

/**
 * 具体适配者：VLC 播放器。
 *
 * @author design-patterns-pro
 */
public class VlcPlayer implements AdvancedMediaPlayer {
    @Override
    public void playVlc(String fileName) {
        System.out.println("[VlcPlayer] 播放 VLC 文件：" + fileName);
    }

    @Override
    public void playMp4(String fileName) {
        // 不支持，留空
    }
}
