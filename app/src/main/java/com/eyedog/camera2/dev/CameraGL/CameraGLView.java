package com.eyedog.camera2.dev.CameraGL;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import com.eyedog.camera2.dev.CameraDev;
import com.eyedog.camera2.dev.CameraStateCallback;
import com.eyedog.camera2.dev.SurfaceTextureCallback;

/**
 * created by jw200 at 2018/11/27 13:40
 **/
public class CameraGLView extends GLSurfaceView implements SurfaceTexture.OnFrameAvailableListener {

    CameraRenderer mRenderer;
    CameraDev mCameraDev;

    public CameraGLView(Context context) {
        this(context, null);
    }

    public CameraGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        mRenderer = new CameraRenderer();
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mCameraDev = new CameraDev();
    }

    public void toggle(){
        mCameraDev.toggle(mStateCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraDev.tryOpenCamera(mStateCallback,
            android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    @Override
    public void onPause() {
        super.onPause();
        mCameraDev.stopCamera();
    }

    CameraStateCallback mStateCallback = new CameraStateCallback() {
        @Override
        public void onOpened(int cameraId) {
            SurfaceTexture st = mRenderer.getSurfaceTexture();
            if (st != null) {
                mCameraDev.startPreview(st);
                st.setOnFrameAvailableListener(CameraGLView.this);
            } else {
                mRenderer.setSurfaceTextureCallback(new SurfaceTextureCallback() {
                    @Override
                    public void onAvailable(SurfaceTexture st) {
                        mCameraDev.startPreview(st);
                        mRenderer.setSurfaceTextureCallback(null);
                        st.setOnFrameAvailableListener(CameraGLView.this);
                    }
                });
            }
            mRenderer.setCameraPreviewSize(mCameraDev.getPreviewWidth(),
                mCameraDev.getPreviewHeight());
        }
    };

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        requestRender();
    }
}
