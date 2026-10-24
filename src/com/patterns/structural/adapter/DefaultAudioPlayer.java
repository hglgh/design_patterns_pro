package com.patterns.structural.adapter;

/**
 * 默认音频播放器：原生支持 mp3，其他格式委托给适配器。
 *
 * @author design-patterns-pro
 */
public class DefaultAudioPlayer implements AudioPlayer {

    @Override
    public void play(String audioType, String fileName) {
        String type = audioType.toLowerCase();
        if ("mp3".equals(type)) {
            System.out.println("[DefaultAudioPlayer] 原生播放 MP3：" + fileName);
        } else if ("vlc".equals(type) || "mp4".equals(type)) {
            // 委托给适配器
            new MediaAdapter(type).play(type, fileName);
        } else {
            System.out.println("[DefaultAudioPlayer] 不支持的格式：" + audioType);
        }
    }
}
