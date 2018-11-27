package com.eyedog.camera2;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import com.eyedog.camera2.dev.CameraSurfaceView;

public class CameraSurfaceActivity extends AppCompatActivity{
    CameraSurfaceView cameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_surface);
        cameraView = findViewById(R.id.camera_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.onPause();
    }
}
