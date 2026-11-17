package com.example.androidpractice08.IDVA.command;

import android.content.Context;
import android.os.Handler;


import com.example.androidpractice08.IDVA.baidu.BaiduTTS;
import com.example.androidpractice08.IDVA.baidu.TtsConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;

public class VoiceBroadcastManager {
    // 修复1：补全泛型声明，编译器识别队列方法
    private final PriorityBlockingQueue<PlayMessage> queue = new PriorityBlockingQueue<>();
    private volatile boolean isSpeaking = false;
    private volatile boolean isEmergencyMode = false;  // 接管锁定态
    private final Context context;
    private final String accessToken;
    // 告警去重：5秒内同一个eventId不再重复入队
    private final Set<String> recentEventSet = new HashSet<>();
    private final Handler uiHandler = new Handler();

    public VoiceBroadcastManager(Context context, String accessToken) {
        this.context = context.getApplicationContext();
        this.accessToken = accessToken;
    }

    // 外部调用：收到预警数据入口
    public void onWarningReceived(int riskLevel, String message, String eventId) {
        // 去重拦截
        if (recentEventSet.contains(eventId)) return;
        recentEventSet.add(eventId);
        uiHandler.postDelayed(() -> recentEventSet.remove(eventId), 5000);

        if (riskLevel == 3) {
            // 标记终止当前播放、清空所有排队播报、开启紧急独占锁定
            interruptCurrent();
            queue.clear();
            isEmergencyMode = true;
            TtsConfig config = VoiceTemplate.getConfig(3, message);
            speakNowEmergency(config);
            return;
        }

        // 1、2级普通预警，加入优先级队列排队
        TtsConfig config = VoiceTemplate.getConfig(riskLevel, message);
        PlayMessage msg = new PlayMessage(riskLevel, config, System.currentTimeMillis(), eventId);
        queue.offer(msg);
        processQueue();
    }

    /**
     * 专门处理3级紧急播报
     * 语音播放完成后自动解除紧急锁定，恢复普通队列播放
     */
    private void speakNowEmergency(TtsConfig config) {
        isSpeaking = true;
        BaiduTTS.speak(context, config, accessToken, mp -> {
            isSpeaking = false;
            // 播放结束自动解锁紧急模式
            releaseEmergencyMode();
        });
    }

    /**
     * 普通等级播报播放方法
     */
    private void speakNow(TtsConfig config) {
        isSpeaking = true;
        BaiduTTS.speak(context, config, accessToken, mp -> {
            isSpeaking = false;
            processQueue();
        });
    }

    /**
     * 队列调度核心：自动取出最高优先级消息连续播放
     */
    private void processQueue() {
        // 正在播放 或 紧急模式锁定状态，直接退出不处理队列
        if (isSpeaking || isEmergencyMode) return;
        PlayMessage next = queue.poll();
        if (next != null) {
            speakNow(next.config);
        }
    }

    /**
     * 真正中断当前播放（调用BaiduTTS强制停止播放器）
     */
    private void interruptCurrent() {
        BaiduTTS.stopCurrentTTS();
        isSpeaking = false;
    }

    /**
     * 查询当前是否处于紧急锁定模式
     */
    public boolean isEmergencyMode() {
        return isEmergencyMode;
    }

    /**
     * 解除紧急模式，解锁后自动继续消费播报队列
     * 既可内部自动调用，也支持外部手动调用解锁
     */
    public void releaseEmergencyMode() {
        this.isEmergencyMode = false;
        processQueue();
    }

    // ===================== 新增destroy销毁方法，修复MainActivity调用报错 =====================
    public void destroy() {
        // 清空待播放队列
        queue.clear();
        recentEventSet.clear();
        uiHandler.removeCallbacksAndMessages(null);
        // 停止调度标记
        isSpeaking = false;
        isEmergencyMode = false;
        // 全局终止所有TTS播放器，释放MediaPlayer资源
        BaiduTTS.stopAllTTS();
    }

    /**
     * 播报任务实体，实现Comparable实现优先级排序
     * 优先级3 > 2 > 1；同优先级按入队时间先后排序
     */
    private static class PlayMessage implements Comparable<PlayMessage> {
        final int priority;
        final TtsConfig config;
        final long timestamp;
        final String eventId;

        // 修复2：删除多余冗余构造方法，避免重载混淆
        PlayMessage(int priority, TtsConfig config, long timestamp, String eventId) {
            this.priority = priority;
            this.config = config;
            this.timestamp = timestamp;
            this.eventId = eventId;
        }

        @Override
        public int compareTo(PlayMessage o) {
            // 优先级降序：数值越大越优先
            if (this.priority != o.priority) {
                return Integer.compare(o.priority, this.priority);
            }
            // 同优先级，先入队的先播放
            return Long.compare(this.timestamp, o.timestamp);
        }
    }
}