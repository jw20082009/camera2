package com.eyedog.camera2.dev;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * created by jw200 at 2018/11/27 21:07
 **/
public class CameraSurfaceView extends SurfaceView
    implements SurfaceHolder.Callback {

    CameraDev cameraDev;
    boolean isCameraOpened = false, isPreviewing = false, isSurfaceCreated = false;

    public CameraSurfaceView(Context context) {
        this(context, null);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CameraSurfaceView(Context context, AttributeSet attrs, int defStyleAttr,
        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        cameraDev = new CameraDev();
        getHolder().addCallback(this);
    }

    public void onResume() {
        cameraDev.tryOpenCamera(mStateCallback, Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public void onPause() {
        cameraDev.stopCamera();
    }

    CameraStateCallback mStateCallback = new CameraStateCallback() {
        @Override
        public void onOpened(int cameraId) {
            isCameraOpened = true;
            startPreview();
        }
    };

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isSurfaceCreated = true;
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        cameraDev.stopCamera();
        isCameraOpened = false;
    }

    private void startPreview() {
        if (isCameraOpened && isSurfaceCreated && !isPreviewing) {
            cameraDev.startPreview(getHolder());
            isPreviewing = true;
        }
    }
}
