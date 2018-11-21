package com.google.jimlongja.animatevideotofullscreen;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PersistableBundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.os.Bundle;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {

    private TextureView mVideoView;
    private TextView mFpsHUD;
    private TextView mAvgFpsHUD;
    private TextView mDroppedFramesHUD;
    private TextView mCpuHUD;
    private TextView mGpuHUD;
    private TextView mMemHUD;
    private Button mToggleFullScreen;
    private int mPosition = 0;
    private int mDuration = 0;
    private long mDurationPlayed = 0;
    private long mFramesPlayed = 0;
    private long mFramesDropped = 0;
    private Double mTotalFsp = 0.0;


    private MediaPlayer mMediaPlayer;

    private MediaObserver mObserver = null;
    private SeekBar mProgress;
    private android.widget.RelativeLayout.LayoutParams mOriginalParams;
    private Boolean mIsFullScreen = false;
    private int mNumLoops = 0;

    private LinkedList<Double> mFpsList = new LinkedList<Double>(Arrays.asList(0.0));

    private static final int MAXLOOP = 10;
    private static final int MAX_SIZE = 1000;
    private static final double NANOS = 1000000000.0;
    private static final double MILLIS = 1000.0;
    private static final String POSITION_KEY = "Position";
    private static final String TAG = "AnimateVideoToFullScreen";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        String VIDEO_URL = "android.resource://" + getPackageName() + "/" + R.raw.big_buck_bunny_1080p_60fps_30s;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        callDisplayModeAPI();

        mFpsHUD = (TextView) findViewById(R.id.tvFpsHUD);
        mAvgFpsHUD = (TextView) findViewById(R.id.tvAvgFpsHUD);
        mDroppedFramesHUD = (TextView) findViewById(R.id.tvDroppedFramesHUD);
        mCpuHUD = (TextView) findViewById(R.id.tvCpuHUD);
        mGpuHUD = (TextView) findViewById(R.id.tvGpuHUD);
        mMemHUD = (TextView) findViewById(R.id.tvMemHUD);

        mToggleFullScreen = (Button) findViewById(R.id.btnToggleFullScreen);
        mProgress = (SeekBar) findViewById(R.id.sbSeekBar);
        mProgress.setProgress(0);

        mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mMediaPlayer.pause();
                    mMediaPlayer.seekTo(progress);
                    mPosition = progress;
                    mMediaPlayer.start();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

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

                mNumLoops ++;

                if (mNumLoops >= MAXLOOP) {
                    mObserver.stop();
                    Log.i(TAG, "Video Test complete");
                    return;
                }
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
        mMediaPlayer.start();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mMediaPlayer.setSurface(new Surface(surface));
        mDuration = mMediaPlayer.getDuration();
        mProgress.setMax(mDuration);
        mProgress.setProgress(mPosition);
        mMediaPlayer.seekTo(mPosition);
        mMediaPlayer.start();
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

        getMediaPlayerMetrics();
        double fps = (mDurationPlayed == 0) ? 0 : new Long(mFramesPlayed).doubleValue() / mDurationPlayed;

        double fpsFirst = mFpsList.getFirst();
        mTotalFsp += fps;
        mFpsList.addLast(new Double(fps));
        if (MAX_SIZE <= mFpsList.size()) {
            mFpsList.removeFirst();
            mTotalFsp -= fpsFirst;
        }

        double droppedPercentage = (mFramesPlayed == 0) ? 0 : (new Long(mFramesDropped).doubleValue() / mFramesPlayed) * 100.0;

        Double avg = mTotalFsp / mFpsList.size();
        mFpsHUD.setText(String.format("FPS\n%.1f", fps));
        mAvgFpsHUD.setText(String.format("Avg\n%.1f", avg));
        mDroppedFramesHUD.setText(String.format("Drop\n%.1f", droppedPercentage) + "%");

        Log.i(TAG, "fps=" + String.format("%.1f", fps) + ", avg fps=" + String.format("%.1f", avg));
    }


    private void resizeVideoView() {
        PropertyValuesHolder pvhLeft = PropertyValuesHolder.ofInt("left", 0, 1);
        PropertyValuesHolder pvhTop = PropertyValuesHolder.ofInt("top", 0, 1);
        PropertyValuesHolder pvhRight = PropertyValuesHolder.ofInt("right", 0, 1);
        PropertyValuesHolder pvhBottom = PropertyValuesHolder.ofInt("bottom", 0, 1);
        PropertyValuesHolder pvhRoundness = PropertyValuesHolder.ofFloat("roundness", 0, 1);



        final Animator collapseExpandAnim = ObjectAnimator.ofPropertyValuesHolder(mVideoView, pvhLeft, pvhTop,
                pvhRight, pvhBottom, pvhRoundness);
        collapseExpandAnim.setDuration(800);
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

        collapseExpandAnim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

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
        mProgress.setVisibility(View.INVISIBLE);
        setProgressBarToFullScreen();
        resizeVideoView();
    }

    private void setVideoViewToOriginalSize() {
        setVideoViewToOriginalSize(true);
    }

    private void setVideoViewToOriginalSize(boolean animate) {
        DisplayMetrics metrics = new DisplayMetrics(); getWindowManager().getDefaultDisplay().getMetrics(metrics);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mVideoView.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_LEFT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.width =  metrics.widthPixels / 5;
        params.height = metrics.heightPixels / 5;
        params.setMargins(24, 24, 24, 24);

        mVideoView.setLayoutParams(params);
        setProgressBarToOriginalSize(animate);

        if (animate) {
            resizeVideoView();
        }
    }

    private void setProgressBarToOriginalSize(boolean animate) {
        DisplayMetrics metrics = new DisplayMetrics(); getWindowManager().getDefaultDisplay().getMetrics(metrics);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mProgress.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_LEFT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.width =  (metrics.widthPixels / 5) + 48;


        if (animate) {
            mProgress.setVisibility(View.INVISIBLE);
        }
        mProgress.setLayoutParams(params);

    }

    private void setProgressBarToFullScreen() {
        DisplayMetrics metrics = new DisplayMetrics(); getWindowManager().getDefaultDisplay().getMetrics(metrics);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mProgress.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_LEFT);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.width =  metrics.widthPixels;
        params.setMargins(0, 0, 0, 0);
        mProgress.setLayoutParams(params);

    }

    private void setToggleButtonText() {
        mToggleFullScreen.setText(mIsFullScreen ? "Exit full screen" : "Full sceeen");
    }

    private void getMediaPlayerMetrics() {

        // validate a few MediaMetrics.
        PersistableBundle metrics = mMediaPlayer.getMetrics();
        if (metrics == null) {
            Log.d(TAG, "MediaPlayer.getMetrics() returned null metrics");
            return;
        }

        if (metrics.isEmpty()) {
            Log.d(TAG, "MediaPlayer.getMetrics() returned empty metrics");
            return;

        }

        int size = metrics.size();
        Set<String> keys = metrics.keySet();

        if (keys == null) {
            Log.d(TAG, "MediaMetricsSet returned no keys");
            return;
        }

        if (keys.size() != size) {
            Log.d(TAG, "MediaMetricsSet.keys().size() mismatch MediaMetricsSet.size()");
            return;
        }


        long frames = metrics.getLong(MediaPlayer.MetricsConstants.FRAMES, 0);
        long framesDropped = metrics.getLong(MediaPlayer.MetricsConstants.FRAMES_DROPPED, 0);
        int duration = mMediaPlayer.getCurrentPosition();

        Log.d(TAG, "frames: " + Long.toString(frames) + " dropped: " + Long.toString(framesDropped) + " duration: " + Long.toString(duration));


        mFramesPlayed = frames;
        mFramesDropped = framesDropped;
        mDurationPlayed = Math.round((mDuration / MILLIS) * mNumLoops) + Math.round(duration / MILLIS);
    }

    private void callDisplayModeAPI() {
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
