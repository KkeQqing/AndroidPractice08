package com.example.androidpractice08.IDVA;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;


import com.example.androidpractice08.IDVA.audio.AudioRecorder;
import com.example.androidpractice08.IDVA.baidu.BaiduASR;
import com.example.androidpractice08.IDVA.baidu.BaiduTTS;
import com.example.androidpractice08.IDVA.baidu.BaiduTokenFetcher;
import com.example.androidpractice08.IDVA.command.CommandMapper;
import com.example.androidpractice08.IDVA.command.VoiceBroadcastManager;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;

/**
 * 语音助手全局封装类，外部页面一键接入
 * 对接UI：任意页面按钮点击 → VoiceHelper.getInstance().triggerVoiceWake(回调)
 */
public class VoiceHelper {
    // ========== 配置常量统一管理 ==========
    private static final String APIKEY = "BwySgqVH9oYG7RKzxm106ZHR";
    private static final String SECRETKEY = "34zLXOB3wagsxsPOMi5EZrIcWskP1xpU";
    private static final String SERVER_URI = "ws://192.168.75.109:8765";

    // 单例
    private static VoiceHelper sInstance;
    private Context mAppContext;
    private String accessToken;
    private WebSocketClient wsClient;
    private VoiceBroadcastManager broadcastManager;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // 初始化状态回调（解决初始化快慢问题）
    public interface InitCompleteCallback {
        void onInitSuccess();
        void onInitFail(String errorMsg);
    }
    private InitCompleteCallback mInitCallback;

    // 识别结果回调接口（UI层实现）
    public interface VoiceResultCallback {
        // 识别成功，返回指令文本
        void onResult(String commandText);
        // 识别为空/失败
        void onEmptyOrFail();
        // 初始化未完成提示
        void onNotReady(String msg);
    }

    private VoiceHelper() {}

    public static VoiceHelper getInstance() {
        if (sInstance == null) {
            synchronized (VoiceHelper.class) {
                if (sInstance == null) {
                    sInstance = new VoiceHelper();
                }
            }
        }
        return sInstance;
    }

    // 设置初始化监听
    public void setInitCompleteCallback(InitCompleteCallback callback) {
        mInitCallback = callback;
    }

    /**
     * APP初始化调用一次（建议在Application中初始化）
     */
    public void init(Context applicationContext) {
        mAppContext = applicationContext.getApplicationContext();
        fetchBaiduToken();
        initWebSocket();
    }

    /**
     * 外部页面【一键触发语音唤醒】入口
     * @param callback 接收识别结果回调
     */
    public void triggerVoiceWake(VoiceResultCallback callback) {
        if (accessToken == null || broadcastManager == null) {
            if (callback != null) callback.onNotReady("语音初始化未完成，请稍后");
            return;
        }
        if (broadcastManager.isEmergencyMode()) {
            Toast.makeText(mAppContext, "车辆需立即接管，操作禁用", Toast.LENGTH_SHORT).show();
            return;
        }
        // TTS应答 + 自动录音识别
        new Thread(() -> {
            try {
                BaiduTTS.speak(mAppContext, BaiduTTS.CACHE_HELLO, accessToken, mp -> {
                    startRecordAndRecognize(callback);
                });
            } catch (Exception e) {
                startRecordAndRecognize(callback);
            }
        }).start();
    }

    // 录音+百度识别
    private void startRecordAndRecognize(VoiceResultCallback callback) {
        if (AudioRecorder.isRecording()) {
            Toast.makeText(mAppContext, "正在录音，请稍等", Toast.LENGTH_SHORT).show();
            return;
        }
        File pcmFile = new File(mAppContext.getCacheDir(), "voice_temp.pcm");
        File resultFile = AudioRecorder.recordPcm(pcmFile, 3000);
        String resultText = null;
        if (resultFile != null) {
            resultText = BaiduASR.recognize(pcmFile, accessToken);
        }
        if (pcmFile.exists()) pcmFile.delete();

        final String finalRes = resultText;
        mMainHandler.post(() -> {
            if (finalRes == null || finalRes.isBlank()) {
                if (callback != null) callback.onEmptyOrFail();
                return;
            }
            // 指令解析 + 发送WebSocket
            handleCommand(finalRes);
            if (callback != null) callback.onResult(finalRes);
        });
    }

    // 指令处理、发送网络
    private void handleCommand(String result) {
        String cmd = CommandMapper.parse(result);
        if (cmd != null) {
            if (CommandMapper.isEmergencyRelease(cmd) && broadcastManager.isEmergencyMode()) {
                broadcastManager.releaseEmergencyMode();
            }
            sendCommand(cmd);
            playTtsReply(BaiduTTS.CACHE_OK);
        } else {
            playTtsReply(BaiduTTS.CACHE_UNKNOWN_CMD);
        }
    }

    private void playTtsReply(String text) {
        new Thread(() -> {
            try {
                BaiduTTS.speak(mAppContext, text, accessToken, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendCommand(String command) {
        if (wsClient != null && wsClient.isOpen()) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "voice_command");
                json.put("command", command);
                json.put("timestamp", System.currentTimeMillis());
                wsClient.send(json.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    // 获取百度Token
    private void fetchBaiduToken() {
        new Thread(() -> {
            try {
                String token = BaiduTokenFetcher.getAccessToken(APIKEY, SECRETKEY);
                accessToken = token;
                mMainHandler.post(() -> {
                    if (token != null) {
                        broadcastManager = new VoiceBroadcastManager(mAppContext, accessToken);
                        // 初始化成功回调
                        if (mInitCallback != null) {
                            mInitCallback.onInitSuccess();
                        }
                    } else {
                        // Token为空失败回调
                        if (mInitCallback != null) {
                            mInitCallback.onInitFail("百度Token获取为空，检查密钥/外网");
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                mMainHandler.post(() -> {
                    if (mInitCallback != null) {
                        mInitCallback.onInitFail("Token网络请求异常：" + e.getMessage());
                    }
                });
            }
        }).start();
    }

    // WebSocket初始化
    private void initWebSocket() {
        try {
            URI serverUri = new URI(SERVER_URI);
            wsClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {}

                @Override
                public void onMessage(String message) {
                    try {
                        JSONObject obj = new JSONObject(message);
                        String msgType = obj.optString("type", "");
                        if ("warning".equals(msgType)) {
                            int level = obj.getInt("risk_level");
                            String text = obj.getString("message");
                            String eventId = obj.optString("event_id", "");
                            if(text.equals(BaiduTTS.CACHE_SPEED_WARN)){
                                text = BaiduTTS.CACHE_SPEED_WARN;
                            }
                            if (broadcastManager != null) {
                                broadcastManager.onWarningReceived(level, text, eventId);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {}

                @Override
                public void onError(Exception ex) {}
            };
            wsClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // APP销毁释放资源
    public void release() {
        if (wsClient != null && wsClient.isOpen()) wsClient.close();
        if (broadcastManager != null) broadcastManager.destroy();
        sInstance = null;
    }

    public String getAccessToken() {
        return accessToken;
    }
}