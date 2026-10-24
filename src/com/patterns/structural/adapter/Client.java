package com.patterns.structural.adapter;

/**
 * 适配器模式客户端演示。
 *
 * @author design-patterns-pro
 */
public class Client {
    public static void main(String[] args) {
        System.out.println("========== 适配器模式演示 ==========\n");

        AudioPlayer player = new DefaultAudioPlayer();

        player.play("mp3", "歌曲.mp3");
        player.play("mp4", "电影.mp4");
        player.play("vlc", "直播秀.vlc");
        player.play("avi", "老格式.avi");

        System.out.println("\n========== 扩展：新增 MKV 支持 ==========");
        // 新增格式只需：① 写一个 MkvPlayer 实现 AdvancedMediaPlayer
        //              ② 往 MediaAdapter.PLAYERS 里加一项 "mkv"
        //              ③ DefaultAudioPlayer 的 if 分支加一个 "mkv"
        // 使用方代码零修改
        System.out.println("（演示略，见 README 扩展对比章节）");
    }
}
