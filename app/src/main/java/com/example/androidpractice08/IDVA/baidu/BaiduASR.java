package com.example.androidpractice08.IDVA.baidu;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;

import okhttp3.*;

/**
 * 百度短语音识别工具类（ASR）
 * 适配16000Hz 单声道 PCM音频，对接IDVA智能驾驶语音助手
 */
public class BaiduASR {
    private static final String TAG = "BaiduASRUtil";
    private static final String ASR_URL = "https://vop.baidu.com/server_api";
    // dev_pid=1537 普通话(纯中文识别)
    private static final int DEV_PID = 1537;
    // 建议改为设备唯一ID，此处先用默认值
    private static final String CUID = "idva_android_car_device";

    /**
     * 上传PCM音频，识别返回语音文字
     * @param pcmFile 16k单声道PCM音频文件
     * @param accessToken 百度鉴权Token
     * @return 识别成功返回文本，失败返回null
     */
    public static String recognize(File pcmFile, String accessToken) {
        // 前置合法性校验
        if (!pcmFile.exists() || !pcmFile.isFile() || accessToken == null || accessToken.isEmpty()) {
            Log.e(TAG, "音频文件不存在或AccessToken为空");
            return null;
        }

        FileInputStream fis = null;
        Response response = null;
        OkHttpClient client = new OkHttpClient();

        try {
            // 读取PCM文件二进制
            byte[] data = new byte[(int) pcmFile.length()];
            fis = new FileInputStream(pcmFile);
            int readTotal = fis.read(data);
            if (readTotal <= 0) {
                Log.e(TAG, "音频文件读取为空");
                return null;
            }
            String speechBase64 = Base64.getEncoder().encodeToString(data);

            // 组装请求JSON参数（百度官方必填字段）
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("format", "pcm");         // 音频格式
            jsonBody.put("rate", 16000);            // 采样率必须16000
            jsonBody.put("channel", 1);            // 单声道
            jsonBody.put("cuid", CUID);
            jsonBody.put("token", accessToken);
            jsonBody.put("len", data.length);      // 音频字节长度
            jsonBody.put("speech", speechBase64);  // base64音频
            jsonBody.put("dev_pid", DEV_PID);

            RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonBody.toString());
            Request request = new Request.Builder()
                    .url(ASR_URL)
                    .post(body)
                    .build();

            // 发起网络请求
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String respStr = response.body().string();
                Log.d(TAG, "ASR接口返回原始数据：" + respStr);
                JSONObject respJson = new JSONObject(respStr);

                // 校验错误码：0=成功，非0识别失败
                int errNo = respJson.optInt("err_no", -1);
                if (errNo != 0) {
                    Log.e(TAG, "识别失败 err_no:" + errNo + ",msg:" + respJson.optString("err_msg"));
                    return null;
                }

                // 安全读取识别结果数组
                JSONArray resultArr = respJson.optJSONArray("result");
                if (resultArr != null && resultArr.length() > 0) {
                    return resultArr.getString(0).trim();
                }
            }
            Log.e(TAG, "ASR接口请求失败，HTTP非200");
            return null;

        } catch (IOException e) {
            Log.e(TAG, "网络IO异常", e);
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "JSON解析异常，接口返回格式非法", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "语音识别整体异常", e);
            return null;
        } finally {
            // 强制关闭流，防止资源泄漏
            try {
                if (fis != null) fis.close();
            } catch (IOException e) {
                Log.e(TAG, "文件流关闭异常");
            }
            if (response != null) {
                response.close();
            }
        }
    }
}