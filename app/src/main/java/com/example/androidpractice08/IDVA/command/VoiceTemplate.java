package com.example.androidpractice08.IDVA.command;


import com.example.androidpractice08.IDVA.baidu.TtsConfig;

public class VoiceTemplate {
    public static TtsConfig getConfig(int riskLevel, String rawMessage) {
        TtsConfig config = new TtsConfig();
        switch (riskLevel) {
            case 1: // 提示级
                config.speed = "5";
                config.pitch = "5";
                config.volume = "8";
                config.person = "0";
                config.text = rawMessage;
                break;
            case 2: // 警示级
                config.speed = "4";
                config.pitch = "4";
                config.volume = "10";
                config.person = "0";
                config.text = rawMessage;
                break;
            case 3: // 接管级
                config.speed = "3";
                config.pitch = "3";
                config.volume = "15";
                config.person = "1";       // 男声
                config.text = "请注意，" + rawMessage + "，请立即接管车辆！";
                break;
            default:
                config.text = rawMessage;
        }
        return config;
    }
}