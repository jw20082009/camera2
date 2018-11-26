package com.eyedog.camera2;

import android.app.Activity;
import android.hardware.camera2.CameraAccessException;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.eyedog.camera2.dev.Camera2View;

public class Camera2Activity extends Activity implements View.OnClickListener {

    Camera2View mCameraView;
    Button mBtnCapture, mPicturePreset;
    EditText etPictureWidth, etPictureHeight;
    TextView tvPictureSizes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);
        mCameraView = findViewById(R.id.camera_view);
        mCameraView.preSetBackCamera();
        etPictureWidth = findViewById(R.id.et_width);
        etPictureHeight = findViewById(R.id.et_height);
        tvPictureSizes = findViewById(R.id.tv_picture_sizes);
        mBtnCapture = findViewById(R.id.btn_capture);
        mBtnCapture.setOnClickListener(this);
        mPicturePreset = findViewById(R.id.btn_preset_picture);
        mPicturePreset.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCameraView.onResume();
        try {
            tvPictureSizes.setText(getSizeListStr(mCameraView.getPictureSizes()));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCameraView.onPause();
    }

    private String getSizeListStr(Size[] sizes) {
        if (sizes != null && sizes.length > 0) {
            StringBuilder stringBuilder = new StringBuilder("pictureSizes:");
            for (int i = 0; i < sizes.length; i++) {
                Size size = sizes[i];
                if (i != 0) {
                    stringBuilder.append(",");
                    stringBuilder.append(size.getWidth() + "*" + size.getHeight());
                } else {
                    stringBuilder.append(size.getWidth() + "*" + size.getHeight());
                }
            }
            return stringBuilder.toString();
        }
        return "";
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_preset_picture:
                int width = 0, height = 0;
                CharSequence sequenceW = etPictureWidth.getText();
                CharSequence sequenceH = etPictureHeight.getText();
                if (sequenceW != null && !TextUtils.isEmpty(sequenceW.toString())) {
                    width = Integer.parseInt(sequenceW.toString());
                }
                if (sequenceH != null && !TextUtils.isEmpty(sequenceH.toString())) {
                    height = Integer.parseInt(sequenceH.toString());
                }
                if (width > 0 && height > 0) {
                    mCameraView.setPictureSize(width, height);
                }
                break;
            case R.id.btn_capture:
                mCameraView.capture();
                break;
        }
    }
}
