package com.patterns.behavioral.iterator.beginner;

import java.util.ArrayList;
import java.util.List;

/**
 * 迭代器入门演示：播放列表的正序/倒序遍历。
 *
 * <p>改造前：把内部 List 直接暴露给客户端，客户端自己按下标遍历，
 * 还能顺手 remove；想倒序又要在客户端写一套反向循环。
 * 改造后：遍历状态封装在迭代器里，客户端只使用 hasNext()/next()。
 */
public class BeginnerDemo {

    public static void main(String[] args) {
        before();
        System.out.println();
        after();
    }

    // ==================================================================
    // 改造前
    // ==================================================================

    private static void before() {
        System.out.println("========== 改造前：客户端直接操作内部 List ==========");

        OldPlaylist playlist = new OldPlaylist();
        playlist.add(new Song("晴天", "周杰伦"));
        playlist.add(new Song("平凡之路", "朴树"));
        playlist.add(new Song("起风了", "买辣椒也用券"));

        System.out.println("  正序：");
        List<Song> songs = playlist.getSongs();       // 内部结构直接暴露
        for (int i = 0; i < songs.size(); i++) {
            System.out.println("    " + songs.get(i));
        }

        System.out.println("  倒序（客户端自己写反向循环）：");
        for (int i = songs.size() - 1; i >= 0; i--) {
            System.out.println("    " + songs.get(i));
        }

        songs.remove(0);                              // 客户端可以随手删
        System.out.println("  客户端 remove 后，播放列表只剩 " + playlist.getSongs().size() + " 首");

        System.out.println();
        System.out.println("问题：");
        System.out.println("  1. 客户端依赖 List 的具体结构，换成数组/链表就要改遍历代码；");
        System.out.println("  2. 客户端能直接修改内部集合，封装被破坏；");
        System.out.println("  3. 倒序、过滤等遍历逻辑都堆在客户端，无法复用。");
    }

    static class Song {
        final String title;
        final String artist;

        Song(String title, String artist) {
            this.title = title;
            this.artist = artist;
        }

        @Override
        public String toString() {
            return title + " - " + artist;
        }
    }

    static class OldPlaylist {

        private final List<Song> songs = new ArrayList<>();

        void add(Song song) {
            songs.add(song);
        }

        List<Song> getSongs() {
            return songs;          // 直接把内部集合交出去
        }
    }

    // ==================================================================
    // 改造后
    // ==================================================================

    private static void after() {
        System.out.println("========== 改造后：遍历交给迭代器 ==========");

        Playlist playlist = new Playlist();
        playlist.add(new Song("晴天", "周杰伦"));
        playlist.add(new Song("平凡之路", "朴树"));
        playlist.add(new Song("起风了", "买辣椒也用券"));

        System.out.println("  正序：");
        printAll(playlist.iterator());

        System.out.println("  倒序：");
        printAll(playlist.reverseIterator());

        System.out.println();
        System.out.println("代价：");
        System.out.println("  1. 每个集合要多写一个迭代器类；");
        System.out.println("  2. 迭代期间修改集合可能让迭代器失效，需要额外规则（fail-fast）；");
        System.out.println("  3. Java 已有 Iterable/Iterator，业务里通常直接用，不必手写。");
    }

    /** 客户端只依赖这两个方法，不关心底层是 List 还是数组。 */
    private static void printAll(SongIterator iterator) {
        while (iterator.hasNext()) {
            System.out.println("    " + iterator.next());
        }
    }

    /** 迭代器接口：把「当前位置」从集合里拿出来。 */
    interface SongIterator {
        boolean hasNext();

        Song next();
    }

    /** 集合：只负责存数据，并负责创建迭代器。 */
    static class Playlist {

        private final List<Song> songs = new ArrayList<>();

        void add(Song song) {
            songs.add(song);
        }

        SongIterator iterator() {
            return new ForwardIterator(songs);
        }

        SongIterator reverseIterator() {
            return new ReverseIterator(songs);
        }
    }

    static class ForwardIterator implements SongIterator {

        private final List<Song> songs;
        private int cursor;

        ForwardIterator(List<Song> songs) {
            this.songs = songs;
        }

        @Override
        public boolean hasNext() {
            return cursor < songs.size();
        }

        @Override
        public Song next() {
            if (!hasNext()) {
                throw new IllegalStateException("没有更多元素");
            }
            return songs.get(cursor++);
        }
    }

    /** 新遍历方式：只新增一个迭代器，Playlist 和客户端方法都不改。 */
    static class ReverseIterator implements SongIterator {

        private final List<Song> songs;
        private int cursor;

        ReverseIterator(List<Song> songs) {
            this.songs = songs;
            this.cursor = songs.size() - 1;
        }

        @Override
        public boolean hasNext() {
            return cursor >= 0;
        }

        @Override
        public Song next() {
            if (!hasNext()) {
                throw new IllegalStateException("没有更多元素");
            }
            return songs.get(cursor--);
        }
    }
}
