package com.example.androidpractice08.IDVA.baidu;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class BaiduTokenFetcher {
    private static final String TOKEN_URL =
            "https://aip.baidubce.com/oauth/2.0/token";

    /**
     * 获取百度AI AccessToken
     * @param apiKey 应用API Key
     * @param secretKey 应用Secret Key
     * @return access_token 失败返回null
     */
    public static String getAccessToken(String apiKey, String secretKey) {
        OkHttpClient client = new OkHttpClient();
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", apiKey)
                .add("client_secret", secretKey)
                .build();
        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build();

        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String jsonStr = response.body().string();
                // 打印原始返回，排错专用
                android.util.Log.d("TokenFetcher", "接口返回原始数据：" + jsonStr);
                JSONObject obj = new JSONObject(jsonStr);
                return obj.getString("access_token");
            }
        } catch (IOException e) {
            android.util.Log.e("TokenFetcher", "网络请求异常", e);
        } catch (JSONException e) {
            android.util.Log.e("TokenFetcher", "JSON解析异常，返回格式非法", e);
        } finally {
            // 必须关闭response，防止OkHttp连接泄漏
            if (response != null) {
                response.close();
            }
        }
        return null;
    }
}