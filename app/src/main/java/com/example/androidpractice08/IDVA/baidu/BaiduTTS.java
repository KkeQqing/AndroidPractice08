package com.example.androidpractice08.IDVA.baidu;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import okhttp3.*;

public class BaiduTTS {
    private static final String TTS_URL = "https://tsn.baidu.com/text2audio";
    // TTS串行任务队列，防止多个MediaPlayer并发错乱
    private static final ConcurrentLinkedQueue<TtsTask> taskQueue = new ConcurrentLinkedQueue<>();
    private static boolean isPlaying = false;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 持有当前播放播放器，用于强制中断
    private static MediaPlayer currentPlayer = null;
    // 高频文本缓存：text -> 本地音频路径
    private static final Map<String, File> voiceCache = new ConcurrentHashMap<>();
    // 固定高频话术配置
    public static final String CACHE_HELLO = "您好";
    public static final String CACHE_OK = "好的";
    public static final String CACHE_UNKNOWN_CMD = "未识别到有效指令";
    public static final String CACHE_SPEED_WARN = "超速提醒，请控制车速";

    private static class TtsTask {
        Context context;
        TtsConfig config;
        String token;
        MediaPlayer.OnCompletionListener listener;
    }

    // 原简单接口，保持兼容
    public static void speak(Context context, String text, String accessToken, MediaPlayer.OnCompletionListener listener) {
        TtsConfig config = new TtsConfig();
        config.text = text;
        speak(context, config, accessToken, listener);
    }

    // 新接口：使用 TtsConfig 动态配置 + 缓存判断
    public static void speak(Context context, TtsConfig config, String accessToken, MediaPlayer.OnCompletionListener listener) {
        TtsTask task = new TtsTask();
        task.context = context.getApplicationContext();
        task.config = config;
        task.token = accessToken;
        task.listener = listener;
        taskQueue.offer(task);
        runNextTts();
    }

    private static void runNextTts() {
        if (isPlaying || taskQueue.isEmpty()) {
            return;
        }
        TtsTask task = taskQueue.poll();
        isPlaying = true;
        String text = task.config.text.trim();
        File cacheFile = voiceCache.get(text);

        // 命中缓存：直接播放本地MP3，不再请求百度
        if (cacheFile != null && cacheFile.exists()) {
            playLocalAudio(task, cacheFile);
            return;
        }

        // 未命中缓存：请求百度合成
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            HttpUrl.Builder urlBuilder = HttpUrl.parse(TTS_URL).newBuilder();
            urlBuilder.addQueryParameter("tex", task.config.text);
            urlBuilder.addQueryParameter("tok", task.token);
            urlBuilder.addQueryParameter("cuid", "android_demo");
            urlBuilder.addQueryParameter("ctp", "1");
            urlBuilder.addQueryParameter("lan", "zh");
            urlBuilder.addQueryParameter("spd", task.config.speed);
            urlBuilder.addQueryParameter("pit", task.config.pitch);
            urlBuilder.addQueryParameter("vol", task.config.volume);
            urlBuilder.addQueryParameter("per", task.config.person);

            Request request = new Request.Builder().url(urlBuilder.build()).build();
            Response response = null;
            final TtsTask finalTask = task;
            final File audioFile = new File(finalTask.context.getCacheDir(), "tts_" + text.hashCode() + ".mp3");

            try {
                response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    FileOutputStream fos = new FileOutputStream(audioFile);
                    fos.write(response.body().bytes());
                    fos.close();
                    // 高频话术存入缓存
                    if (text.equals(CACHE_HELLO) || text.equals(CACHE_OK)
                            || text.equals(CACHE_UNKNOWN_CMD) || text.equals(CACHE_SPEED_WARN)) {
                        voiceCache.put(text, audioFile);
                    }
                    mainHandler.post(() -> playLocalAudio(finalTask, audioFile));
                } else {
                    mainHandler.post(() -> {
                        isPlaying = false;
                        if (finalTask.listener != null) finalTask.listener.onCompletion(null);
                        runNextTts();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    isPlaying = false;
                    if (finalTask.listener != null) finalTask.listener.onCompletion(null);
                    runNextTts();
                });
            } finally {
                if (response != null) response.close();
            }
        }).start();
    }

    // 播放本地音频文件
    private static void playLocalAudio(TtsTask task, File audioFile) {
        mainHandler.post(() -> {
            MediaPlayer mp = new MediaPlayer();
            currentPlayer = mp;
            try {
                mp.setDataSource(audioFile.getAbsolutePath());
                mp.prepare();
                final MediaPlayer finalMp = mp;
                mp.setOnCompletionListener(mp2 -> {
                    safeReleaseMediaPlayer(finalMp);
                    if (currentPlayer == finalMp) currentPlayer = null;
                    isPlaying = false;
                    if (task.listener != null) task.listener.onCompletion(finalMp);
                    runNextTts();
                });
                mp.start();
            } catch (Exception e) {
                safeReleaseMediaPlayer(mp);
                if (currentPlayer == mp) currentPlayer = null;
                isPlaying = false;
                if (task.listener != null) task.listener.onCompletion(null);
                runNextTts();
            }
        });
    }

    /**
     * 安全释放MediaPlayer，捕获重复释放、已销毁异常
     */
    private static void safeReleaseMediaPlayer(MediaPlayer mp) {
        if (mp == null) return;
        try {
            if (mp.isPlaying()) mp.stop();
            mp.reset();
            mp.release();
        } catch (Exception ignored) {
        }
    }

    /**
     * 强制停止当前正在播放的语音（修复原interruptCurrent无效问题）
     */
    public static void stopCurrentTTS() {
        mainHandler.post(() -> {
            if (currentPlayer != null) {
                safeReleaseMediaPlayer(currentPlayer);
                currentPlayer = null;
            }
            isPlaying = false;
        });
    }

    /**
     * 全局终止所有TTS、清空排队任务，给VoiceBroadcastManager.destroy()调用
     */
    public static void stopAllTTS() {
        taskQueue.clear();
        stopCurrentTTS();
    }

    /**
     * 清空缓存（可选，清理空间使用）
     */
    public static void clearVoiceCache() {
        voiceCache.clear();
    }
}