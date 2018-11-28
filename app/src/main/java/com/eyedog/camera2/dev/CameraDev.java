package com.eyedog.camera2.dev;

import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.view.SurfaceHolder;
import com.eyedog.camera2.Constant;
import com.eyedog.camera2.utils.CameraSizeUtils;
import java.io.IOException;
import java.util.List;

/**
 * created by jw200 at 2018/11/27 14:09
 **/
public class CameraDev {

    int mCameraID = 1;
    int mFacing = 0;
    boolean mIsPreviewing = false;
    Camera mCameraDevice;
    Camera.Parameters mParams;
    private int mPreviewWidth;
    private int mPreviewHeight;

    private int mPictureWidth = 1000;
    private int mPictureHeight = 1000;

    private int mPresetPreviewWidth = Constant.PREVIEW_WIDTH;
    private int mPresetPreviewHeight = Constant.PREVIEW_HEIGHT;

    public int getPreviewWidth() {
        return mPreviewWidth;
    }

    public int getPreviewHeight() {
        return mPreviewHeight;
    }

    public void toggle(CameraStateCallback callback) {
        stopCamera();
        tryOpenCamera(callback,
            mFacing == Camera.CameraInfo.CAMERA_FACING_BACK ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public Camera getCameraDevice() {
        return mCameraDevice;
    }

    public synchronized boolean tryOpenCamera(CameraStateCallback callback, int facing) {
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
                int numberOfCameras = Camera.getNumberOfCameras();
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == facing) {
                        mCameraID = i;
                        mFacing = facing;
                    }
                }
            }
            stopPreview();
            if (mCameraDevice != null) {
                mCameraDevice.release();
                mCameraDevice = null;
            }
            if (mCameraID >= 0) {
                mCameraDevice = Camera.open(mCameraID);
            } else {
                mCameraDevice = Camera.open();
                mFacing = Camera.CameraInfo.CAMERA_FACING_BACK; //default: back facing
            }
            android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(mCameraID, info);
            int rotation = info.orientation;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (360 - info.orientation) % 360;
            } else {
                rotation = info.orientation % 360;
            }
            mCameraDevice.setDisplayOrientation(rotation);
        } catch (Exception e) {
            e.printStackTrace();
            mCameraDevice = null;
            return false;
        }
        if (mCameraDevice != null) {
            try {
                initCamera();
                if (callback != null) {
                    callback.onOpened(mCameraID);
                }
            } catch (Exception e) {
                mCameraDevice.release();
                mCameraDevice = null;
                return false;
            }
            return true;
        }
        return false;
    }

    private synchronized void initCamera() {
        if (mCameraDevice == null) {
            return;
        }
        mParams = mCameraDevice.getParameters();
        mParams.setPictureFormat(PixelFormat.JPEG);
        List<Camera.Size> picSizes = mParams.getSupportedPictureSizes();
        Camera.Size picSz =
            CameraSizeUtils.getLargeSize(picSizes, mPictureWidth, mPictureHeight, false);
        List<Camera.Size> prevSizes = mParams.getSupportedPreviewSizes();
        Camera.Size prevSz =
            CameraSizeUtils.getLargeSize(prevSizes, mPresetPreviewWidth, mPresetPreviewHeight,
                true);
        List<Integer> frameRates = mParams.getSupportedPreviewFrameRates();
        int fpsMax = 0;
        for (Integer n : frameRates) {
            if (fpsMax < n) {
                fpsMax = n;
            }
        }
        mParams.setPreviewSize(prevSz.width, prevSz.height);
        mParams.setPictureSize(picSz.width, picSz.height);
        List<String> focusModes = mParams.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        int previewRate = fpsMax;
        mParams.setPreviewFrameRate(previewRate); //设置相机预览帧率
        try {
            mCameraDevice.setParameters(mParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mParams = mCameraDevice.getParameters();
        Camera.Size szPic = mParams.getPictureSize();
        Camera.Size szPrev = mParams.getPreviewSize();
        mPreviewWidth = szPrev.width;
        mPreviewHeight = szPrev.height;
        mPictureWidth = szPic.width;
        mPictureHeight = szPic.height;
    }

    public synchronized void stopCamera() {
        if (mIsPreviewing) {
            mIsPreviewing = false;
            if (mCameraDevice != null) {
                stopPreview();
                mCameraDevice.setPreviewCallback(null);
                mCameraDevice.release();
                mCameraDevice = null;
            }
        }
    }

    public synchronized void startPreview(SurfaceTexture texture) {
        if (mIsPreviewing) {
            return;
        }
        if (mCameraDevice != null) {
            try {
                mCameraDevice.setPreviewTexture(texture);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCameraDevice.startPreview();
            mIsPreviewing = true;
        }
    }

    public synchronized void startPreview(SurfaceHolder holder) {
        if (mIsPreviewing) {
            return;
        }
        if (mCameraDevice != null) {
            try {
                mCameraDevice.setPreviewDisplay(holder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCameraDevice.startPreview();
            mIsPreviewing = true;
        }
    }

    public synchronized void stopPreview() {
        if (mIsPreviewing && mCameraDevice != null) {
            mCameraDevice.stopPreview();
            mIsPreviewing = false;
        }
    }
}
