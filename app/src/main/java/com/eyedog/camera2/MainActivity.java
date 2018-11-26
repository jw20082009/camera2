package com.eyedog.camera2;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final String TAG = "MainActivity";
    Button mBtnCamera2, mBtnCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mBtnCamera2 = findViewById(R.id.btn_camera2);
        mBtnCamera2.setOnClickListener(this);
        mBtnCamera = findViewById(R.id.btn_camera);
        mBtnCamera.setOnClickListener(this);
        File fileDir = getFilesDir();
        File createFile = new File(fileDir, "test");
        if (!createFile.exists()) {
            createFile.mkdir();
        }
        File txt = new File(createFile, "test.txt");
        Log.i(TAG, "filepath:" + txt.getAbsolutePath());
        FileWriter fileWritter = null;
        try {
            txt.createNewFile();
            fileWritter = new FileWriter(txt, false);
            fileWritter.write("test");
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fileWritter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_camera2:
                startActivity(new Intent(this, Camera2Activity.class));
                break;
            case R.id.btn_camera:
                startActivity(new Intent(this, CameraActivity.class));
                break;
        }
    }
}
