package com.example.androidpractice08.IDVA.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 车载语音助手PCM录音工具类
 * 适配百度实时/短语音识别：16kHz 单声道 16bit 原始PCM
 * 项目：IDVA Intelligent Driving Voice Assistant
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorderUtil";
    // 百度语音强制标准参数
    public static final int SAMPLE_RATE = 16000;
    // 声道配置：单声道
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    // 音频编码格式：16 位脉冲编码调制（PCM）
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // 录音互斥标记，防止多实例抢占麦克风
    private static boolean isRecording = false;

    /**
     * 录制固定时长PCM音频
     * @param outputFile 输出pcm文件
     * @param durationMs 录音时长(毫秒)
     * @return 成功返回文件对象，失败返回null
     */
    public synchronized static File recordPcm(File outputFile, int durationMs) {
        // 互斥判断：正在录音直接拒绝
        if (isRecording) {
            Log.w(TAG, "当前正在录音，请勿重复触发");
            return null;
        }
        AudioRecord recorder = null;
        FileOutputStream fos = null;
        isRecording = true;

        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize <= 0) {
                Log.e(TAG, "获取AudioRecord最小缓冲区失败");
                return null;
            }

            // 实例化录音机
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            // 校验初始化状态
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败：权限不足 / 麦克风被其他应用占用");
                return null;
            }

            fos = new FileOutputStream(outputFile);
            recorder.startRecording();
            byte[] buffer = new byte[bufferSize];
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < durationMs) {
                int readLen = recorder.read(buffer, 0, bufferSize);
                if (readLen > 0) {
                    fos.write(buffer, 0, readLen);
                } else if (readLen == AudioRecord.ERROR_INVALID_OPERATION
                        || readLen == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "音频读取异常，终止录音");
                    break;
                }
            }
            Log.d(TAG, "录音完成,路径:" + outputFile.getAbsolutePath());
            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "录音过程发生异常", e);
            return null;
        } finally {
            // 强制释放资源，避免麦克风常驻占用
            try {
                if (recorder != null) {
                    if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop();
                    }
                    recorder.release();
                }
                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "文件流释放异常", e);
            }
            isRecording = false;
        }
    }

    /**
     * 外部查询当前是否正在录音
     */
    public static boolean isRecording() {
        return isRecording;
    }

    public synchronized static void setRecording(boolean flag) {
        isRecording = flag;
    }
}