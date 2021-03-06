package com.eyedog.camera2;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.eyedog.camera2.dev.CameraView;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    CameraView cameraView;
    Button mBtnToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        cameraView = findViewById(R.id.camera_view);
        mBtnToggle = findViewById(R.id.btn_toggle);
        mBtnToggle.setOnClickListener(this);
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_toggle:
                cameraView.toggle();
                break;
        }
    }
}

