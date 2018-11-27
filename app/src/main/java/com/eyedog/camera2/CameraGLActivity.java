package com.eyedog.camera2;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.eyedog.camera2.dev.CameraGL.CameraGLView;

public class CameraGLActivity extends AppCompatActivity implements View.OnClickListener {
    CameraGLView mCameraView;
    Button mToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_gl);
        mCameraView = findViewById(R.id.camera_view);
        mToggle = findViewById(R.id.btn_toggle);
        mToggle.setOnClickListener(this);
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_toggle:
                mCameraView.toggle();
                break;
        }
    }
}
