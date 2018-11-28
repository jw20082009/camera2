package com.eyedog.camera2;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.eyedog.camera2.recorder.RecorderSurfaceView;

public class CameraSurfaceActivity extends AppCompatActivity implements View.OnClickListener {
    RecorderSurfaceView cameraView;
    Button recordButton, toggleButton;
    TextView recordTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_surface);
        cameraView = findViewById(R.id.camera_view);
        recordButton = findViewById(R.id.btn_record);
        recordButton.setOnClickListener(this);
        recordTv = findViewById(R.id.tv_record_file);
        toggleButton = findViewById(R.id.btn_toggle);
        toggleButton.setOnClickListener(this);
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
            case R.id.btn_record:
                if (cameraView.isRecording()) {
                    ((Button) v).setText("record");
                    recordTv.setText("");
                    cameraView.releaseRecord();
                } else {
                    ((Button) v).setText("stop");
                    cameraView.startRecord();
                    recordTv.setText(cameraView.getRecordFile().getAbsolutePath());
                }
                break;
            case R.id.btn_toggle:
                cameraView.toggle();
                break;
        }
    }
}
