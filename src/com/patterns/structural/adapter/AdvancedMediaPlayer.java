package com.patterns.structural.adapter;

/**
 * 适配者接口：高级媒体播放器。
 *
 * <p>已有的、接口不兼容的第三方播放器，支持 vlc / mp4。
 *
 * @author design-patterns-pro
 */
public interface AdvancedMediaPlayer {
    void playVlc(String fileName);
    void playMp4(String fileName);
}
