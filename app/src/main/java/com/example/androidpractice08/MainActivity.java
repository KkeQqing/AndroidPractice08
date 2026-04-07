package com.example.androidpractice08;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.BitmapCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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