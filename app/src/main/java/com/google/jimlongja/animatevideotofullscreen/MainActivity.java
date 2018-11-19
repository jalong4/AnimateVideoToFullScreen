package com.google.jimlongja.animatevideotofullscreen;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.os.Bundle;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {


    private TextureView mVideoView;
    private TextView mFpsHUD;
    private Button mToggleFullScreen;
    private int mPosition = 0;
    private int mDuration = 0;

    private MediaPlayer mMediaPlayer;

    private MediaObserver mObserver = null;
    private SeekBar mProgress;
    private android.widget.RelativeLayout.LayoutParams mOriginalParams;
    private Boolean mIsFullScreen = false;
    private static final String POSITION_KEY = "Position";
    private static final String TAG = "AnimateVideoToFullScreen";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        String VIDEO_URL = "android.resource://" + getPackageName() + "/" + R.raw.big_buck_bunny_1080p_60fps_30s;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        callDisplayModeAPI();

        mFpsHUD = (TextView) findViewById(R.id.tvFpsHUD);
        mToggleFullScreen = (Button) findViewById(R.id.btnToggleFullScreen);
        mProgress = (SeekBar) findViewById(R.id.sbSeekBar);
        mProgress.setProgress(0);
        mVideoView = (TextureView) findViewById(R.id.tvVideoView);

        setupToggleFullScreenButton();

        if (mMediaPlayer == null) {
            try {
                mMediaPlayer = MediaPlayer.create(this, Uri.parse(VIDEO_URL));
            } catch (Exception e) {
                Log.e ("Error", e.getMessage());
            }
        }

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
//                mObserver.stop();
                mMediaPlayer.seekTo(0);
                mProgress.setProgress(0);
                Log.i(TAG, "Looping Video");
                mMediaPlayer.start();
            }
        });

        mObserver = new MediaObserver();
        new Thread(mObserver).start();
        
        setVideoViewToOriginalSize(false);
        mVideoView.requestFocus();
        mVideoView.setSurfaceTextureListener(this);

    }

    private void setupToggleFullScreenButton() {

        mToggleFullScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsFullScreen) {
                    setVideoViewToOriginalSize();
                } else {
                    setFullScreen();
                }
                mIsFullScreen = !mIsFullScreen;
                setToggleButtonText();
            }
        });

    }


    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(POSITION_KEY, mMediaPlayer.getCurrentPosition());
        mMediaPlayer.pause();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mPosition = savedInstanceState.getInt(POSITION_KEY);
        mMediaPlayer.seekTo(mPosition);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mMediaPlayer.setSurface(new Surface(surface));
        mDuration = mMediaPlayer.getDuration();
        mMediaPlayer.seekTo(mPosition);
        mMediaPlayer.start();
        mProgress.setMax(mDuration);
        mProgress.setProgress(mPosition);

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        double fps = fps();

        String fpsString = String.format("%.1f", fps);

        mFpsHUD.setText(fpsString);
        Log.i(TAG, "fps=" + fpsString);
    }


    private void resizeVideoView() {


        PropertyValuesHolder pvhLeft = PropertyValuesHolder.ofInt("left", 0, 1);
        PropertyValuesHolder pvhTop = PropertyValuesHolder.ofInt("top", 0, 1);
        PropertyValuesHolder pvhRight = PropertyValuesHolder.ofInt("right", 0, 1);
        PropertyValuesHolder pvhBottom = PropertyValuesHolder.ofInt("bottom", 0, 1);
        PropertyValuesHolder pvhRoundness = PropertyValuesHolder.ofFloat("roundness", 0, 1);


        final Animator collapseExpandAnim = ObjectAnimator.ofPropertyValuesHolder(mVideoView, pvhLeft, pvhTop,
                pvhRight, pvhBottom, pvhRoundness);
        collapseExpandAnim.setDuration(1000);
        collapseExpandAnim.setupStartValues();

        mVideoView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mVideoView.getViewTreeObserver().removeOnPreDrawListener(this);
                collapseExpandAnim.setupEndValues();
                collapseExpandAnim.start();
                return false;
            }
        });

    }

    private void setFullScreen() {
        DisplayMetrics metrics = new DisplayMetrics(); getWindowManager().getDefaultDisplay().getMetrics(metrics);
        android.widget.RelativeLayout.LayoutParams params = (android.widget.RelativeLayout.LayoutParams) mVideoView.getLayoutParams();
        params.width =  metrics.widthPixels;
        params.height = metrics.heightPixels;
        params.setMargins(0, 0, 0, 0);
        mVideoView.setLayoutParams(params);
        resizeVideoView();
    }

    private void setVideoViewToOriginalSize() {
        setVideoViewToOriginalSize(true);
    }

    private void setVideoViewToOriginalSize(Boolean animate) {

        DisplayMetrics metrics = new DisplayMetrics(); getWindowManager().getDefaultDisplay().getMetrics(metrics);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mVideoView.getLayoutParams();
        params.width =  (int) (320 * metrics.density);
        params.height = (int) (180 * metrics.density);
        params.setMargins(24, 24, 24, 24);

        mVideoView.setLayoutParams(params);

        if (animate) {
            resizeVideoView();
        }
    }

    private void setToggleButtonText() {
        mToggleFullScreen.setText(mIsFullScreen ? "Exit full screen" : "Full sceeen");
    }

    LinkedList<Long> times = new LinkedList<Long>(){{
        add(System.nanoTime());
    }};

    private final int MAX_SIZE = 100;
    private final double NANOS = 1000000000.0;

    /** Calculates and returns frames per second */
    private double fps() {
        long lastTime = System.nanoTime();
        double difference = (lastTime - times.getFirst()) / NANOS;
        times.addLast(lastTime);
        int size = times.size();
        if (size > MAX_SIZE) {
            times.removeFirst();
        }
        return difference > 0 ? times.size() / difference : 0.0;
    }

    public void callDisplayModeAPI() {
        Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
        Log.i(TAG, "Display.Mode API: width=" + Integer.toString(mode.getPhysicalWidth()) + " height="
                + Integer.toString(mode.getPhysicalHeight()) + " refresh Rate=" + Float
                .toString(mode.getRefreshRate()) + "\n");
    }

    private class MediaObserver implements Runnable {
        private AtomicBoolean stop = new AtomicBoolean(false);

        public void stop() {
            stop.set(true);
        }

        @Override
        public void run() {
            while (!stop.get()) {
                int pos = mMediaPlayer.getCurrentPosition();
                mProgress.setProgress(pos);
                try {
                    Thread.sleep(200);
                } catch (Exception e) {
                    Log.e ("Error", e.getMessage());
                }
            }
        }
    }
}
