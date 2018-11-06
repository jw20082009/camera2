package com.eyedog.camera2;

import android.app.Activity;
import android.os.Bundle;
import com.eyedog.camera2.dev.CameraView;

public class CameraActivity extends Activity {

    CameraView mCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mCameraView = findViewById(R.id.camera_view);
        mCameraView.preSetBackCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraView.onPause();
    }
}
