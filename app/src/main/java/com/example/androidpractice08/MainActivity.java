package com.example.androidpractice08;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.BitmapCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.androidpractice08.IDVA.VoiceHelper;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private ImageView ivAssetsCode,ivRawCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivAssetsCode = findViewById(R.id.iv_assets);
        ivRawCode = findViewById(R.id.iv_raw);

        loadImageFromAssets("g1.png",ivAssetsCode);
        loadImageFromRaw(R.raw.g2,ivRawCode);


        findViewById(R.id.btn_voice_global).setOnClickListener(v -> {
            VoiceHelper.getInstance().triggerVoiceWake(new VoiceHelper.VoiceResultCallback() {
                // 语音识别成功，拿到文字结果
                @Override
                public void onResult(String commandText) {
                    // 在这里写你的业务逻辑：显示文字、控制指令、页面跳转等
                }
                // 未说话/识别失败
                @Override
                public void onEmptyOrFail() {}
                // 语音还在初始化，不可使用
                @Override
                public void onNotReady(String msg) {
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadImageFromAssets(String fileName,ImageView imageView){
        try{
            InputStream inputStream = getAssets().open(fileName);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            imageView.setImageBitmap(bitmap);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private void loadImageFromRaw(int resId,ImageView imageView){
        InputStream inputStream = getResources().openRawResource(resId);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
        imageView.setImageBitmap(bitmap);
    }



}