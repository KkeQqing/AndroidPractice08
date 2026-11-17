package com.example.androidpractice08.IDVA.command;

import java.util.HashMap;
import java.util.Map;

public class CommandMapper {
    public static final String CMD_DESKTOP = "CMD_DESKTOP";                // 切桌面/主界面
    public static final String CMD_SWITCH_DRIVING = "CMD_SWITCH_DRIVING"; // 切换行车/驾驶模式
    public static final String CMD_PARKING_START = "CMD_PARKING_START";   // 启动自动泊车
    public static final String CMD_PARKING_CANCEL = "CMD_PARKING_CANCEL"; // 取消/停止泊车
    public static final String CMD_TAKE_OVER = "CMD_TAKE_OVER";           // 人工接管车辆

    // 简单关键词映射
    private static final Map<String, String> keywordMap = new HashMap<>();
    static {
        keywordMap.put("桌面", CMD_DESKTOP);
        keywordMap.put("主界面", CMD_DESKTOP);
        keywordMap.put("行车", CMD_SWITCH_DRIVING);
        keywordMap.put("驾驶", CMD_SWITCH_DRIVING);
        keywordMap.put("泊车", CMD_PARKING_START);
        keywordMap.put("停车", CMD_PARKING_START);
        keywordMap.put("开始泊车", CMD_PARKING_START);
        keywordMap.put("取消泊车", CMD_PARKING_CANCEL);
        keywordMap.put("停止泊车", CMD_PARKING_CANCEL);
        keywordMap.put("我已接管", CMD_TAKE_OVER);
        keywordMap.put("接管", CMD_TAKE_OVER);
    }

    public static String parse(String raw) {
        if (raw == null) return null;
        // 遍历关键词，遇到第一个匹配即返回
        for (Map.Entry<String, String> entry : keywordMap.entrySet()) {
            if (raw.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null; // 无效指令
    }

    public static boolean isEmergencyRelease(String command) {
        return CMD_TAKE_OVER.equals(command);
    }
}