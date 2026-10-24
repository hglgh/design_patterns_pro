package com.patterns.structural.adapter;

/**
 * 目标接口：音频播放器。
 *
 * <p>使用方期望的统一接口，只支持 mp3。
 *
 * @author design-patterns-pro
 */
public interface AudioPlayer {
    /**
     * 播放音频。
     *
     * @param audioType 格式（mp3 / mp4 / vlc / ...）
     * @param fileName  文件名
     */
    void play(String audioType, String fileName);
}
