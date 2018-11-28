package com.eyedog.camera2.recorder;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import com.eyedog.camera2.dev.CameraSurfaceView;
import java.io.File;

/**
 * created by jw200 at 2018/11/27 22:10
 **/
public class RecorderSurfaceView extends CameraSurfaceView
    implements MediaRecorder.OnErrorListener {
    private final String TAG = "RecorderSurfaceView";
    private MediaRecorder mMediaRecorder;
    private File mRecordFile;//录制的视频文件
    private boolean mIsRecording = false;

    public RecorderSurfaceView(Context context) {
        this(context, null);
    }

    public RecorderSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecorderSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RecorderSurfaceView(Context context, AttributeSet attrs, int defStyleAttr,
        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    private void initRecord() {
        try {
            if (mMediaRecorder == null) {
                mMediaRecorder = new MediaRecorder();
            } else {
                mMediaRecorder.reset();
            }
            cameraDev.getCameraDevice().unlock();
            mMediaRecorder.setCamera(cameraDev.getCameraDevice());
            mMediaRecorder.setOnErrorListener(this);
            mMediaRecorder.setPreviewDisplay(getHolder().getSurface());
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//视频源
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);//音频源
            mMediaRecorder.setAudioChannels(1);//单声道
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//视频输出格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//音频格式
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);//视频录制格式
            mMediaRecorder.setVideoSize(cameraDev.getPreviewWidth(),
                cameraDev.getPreviewHeight());//设置分辨率,320, 240微信小视频的像素一样
            //mMediaRecorder.setVideoFrameRate(17);// 设置每秒帧数 这个设置三星手机会出问题
            mMediaRecorder.setVideoEncodingBitRate(1 * 8 * 1024 * 1024);//清晰度
            mMediaRecorder.setOrientationHint(90);//输出旋转90度，保持竖屏录制
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            mRecordFile = new File(dir, System.currentTimeMillis() + ".mp4");
            //mMediaRecorder.setMaxDuration(Constant.MAXVEDIOTIME * 1000);
            mMediaRecorder.setOutputFile(mRecordFile.getAbsolutePath());
            mMediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
            cameraDev.stopCamera();
        }
    }

    public File getRecordFile() {
        return mRecordFile;
    }

    public void startRecord() {
        if (cameraDev.getCameraDevice() == null) {
            Log.i(TAG, "startRecord but camera not opened");
            return;
        }
        if (mIsRecording) {
            Log.i(TAG, "startRecord but is recording");
            return;
        }
        initRecord();
        if (null != mMediaRecorder) {
            mMediaRecorder.start();//开始录制
            mIsRecording = true;
        }
    }

    public void releaseRecord() {
        try {
            if (null != mMediaRecorder) {
                mMediaRecorder.setOnErrorListener(null);
                if (mIsRecording) mMediaRecorder.stop();
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mIsRecording = false;
                mRecordFile = null;
                mMediaRecorder = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.i(TAG, "onError,what:" + what + ";extra:" + extra);
    }
}
