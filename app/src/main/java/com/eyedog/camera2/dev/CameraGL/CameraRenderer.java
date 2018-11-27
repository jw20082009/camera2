package com.eyedog.camera2.dev.CameraGL;

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import com.eyedog.camera2.dev.SurfaceTextureCallback;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * created by jw200 at 2018/11/27 13:45
 **/
public class CameraRenderer implements GLSurfaceView.Renderer {
    private final String TAG = "CameraRenderer";
    private FullFrameRect mFullScreen;
    private int mTextureId;
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;
    private SurfaceTexture mSurfaceTexture;
    private SurfaceTextureCallback mTextureCallback;
    private final float[] mSTMatrix = new float[16];

    public CameraRenderer() {
        mIncomingSizeUpdated = false;
    }

    public void setCameraPreviewSize(int width, int height) {
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
    }

    public void setSurfaceTextureCallback(SurfaceTextureCallback callback) {
        this.mTextureCallback = callback;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mFullScreen = new FullFrameRect(
            new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId = mFullScreen.createTextureObject();
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        if (mTextureCallback != null) {
            mTextureCallback.onAvailable(mSurfaceTexture);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

    }

    @Override
    public void onDrawFrame(GL10 gl) {
        mSurfaceTexture.updateTexImage();
        if (mIncomingSizeUpdated) {
            mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
            mIncomingSizeUpdated = false;
        }
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        //MatrixUtils.logFloatArr(mSTMatrix);
        mFullScreen.drawFrame(mTextureId, mSTMatrix /**MatrixUtils.getOriginalMatrix()*/);
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }
}
