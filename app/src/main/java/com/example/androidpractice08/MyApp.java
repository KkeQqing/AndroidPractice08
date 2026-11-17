package com.example.androidpractice08;

import android.app.Application;

import com.example.androidpractice08.IDVA.VoiceHelper;


public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // APP启动一次性初始化语音引擎、Token、WebSocket
        VoiceHelper.getInstance().init(this);
    }
}