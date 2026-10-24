package com.patterns.structural.adapter;

import java.util.Map;

/**
 * 适配器：把 {@link AdvancedMediaPlayer} 适配成 {@link AudioPlayer}。
 *
 * <p>对象适配器（组合方式）：持有适配者的引用，在目标接口方法里
 * 转调适配者的方法。比类适配器（继承）更灵活，符合"组合优于继承"。
 *
 * <p>本实现使用 Map 注册格式 → 播放器映射，避免 if-else，
 * 新增格式只需往 Map 里加一项。
 *
 * @author design-patterns-pro
 */
public class MediaAdapter implements AudioPlayer {

    /** 格式 → 播放器 映射表。 */
    private static final Map<String, AdvancedMediaPlayer> PLAYERS =
            Map.of(
                    "vlc", new VlcPlayer(),
                    "mp4", new Mp4Player()
            );

    private final AdvancedMediaPlayer advancedPlayer;
    private final String supportedType;

    /**
     * 构造时确定要适配的格式。
     *
     * @param audioType 格式（vlc / mp4）
     */
    public MediaAdapter(String audioType) {
        this.supportedType = audioType.toLowerCase();
        this.advancedPlayer = PLAYERS.get(supportedType);
    }

    @Override
    public void play(String audioType, String fileName) {
        if (advancedPlayer == null) {
            System.out.println("[MediaAdapter] 不支持的格式：" + audioType);
            return;
        }
        // 适配器在构造时已经绑定了格式，目标接口却把格式又传了一遍。
        // 两个真值来源必须校验，否则会用错方法去播对的文件（比静默失败更危险）。
        if (!supportedType.equalsIgnoreCase(audioType)) {
            System.out.println("[MediaAdapter] 格式不一致：构造时是 " + supportedType
                    + "，调用时是 " + audioType);
            return;
        }
        switch (supportedType) {
            case "vlc" -> advancedPlayer.playVlc(fileName);
            case "mp4" -> advancedPlayer.playMp4(fileName);
            default -> System.out.println("[MediaAdapter] 未处理的格式：" + audioType);
        }
    }
}
