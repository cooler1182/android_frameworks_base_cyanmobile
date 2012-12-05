package com.android.systemui.statusbar.batteries;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ProgressBar;

public class CmBatteryNaviBarBottom extends ProgressBar implements Animatable, Runnable {

    private static final String TAG = CmBatteryNaviBarBottom.class.getSimpleName();

    // Total animation duration
    private static final int ANIM_DURATION = 5000; // 5 seconds

    // Duration between frames of charging animation
    private static final int FRAME_DURATION = ANIM_DURATION / 100;

    // Battery level to stop animation
    private static final int STOP_ANIMATION_LEVEL = 95;

    // Are we listening for actions?
    private boolean mAttached = false;

    // Should we show this?
    private boolean mShowCmBatteryNaviBar = false;

    private boolean mNVShow = false;

    // Current battery level
    private int mBatteryLevel = 0;

    // Current "step" of charging animation
    private int mChargingLevel = -1;

    // Are we charging?
    private boolean mBatteryCharging = false;

    private Handler mHandler = new Handler();

    private Interpolator mInterpolator = new DecelerateInterpolator();

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observer() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BATTERY), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVI_BUTTONS), false, this);
	    resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BATTERY_COLOR), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public CmBatteryNaviBarBottom(Context context) {
        this(context, null);
    }

    public CmBatteryNaviBarBottom(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CmBatteryNaviBarBottom(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        SettingsObserver observer = new SettingsObserver(mHandler);
        observer.observer();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mAttached = false;
            getContext().unregisterReceiver(mIntentReceiver);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                        BatteryManager.BATTERY_STATUS_UNKNOWN) == BatteryManager.BATTERY_STATUS_CHARGING;
                if (mBatteryCharging && mBatteryLevel < STOP_ANIMATION_LEVEL) {
                    start();
                } else {
                    stop();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stop();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mBatteryCharging && mBatteryLevel < STOP_ANIMATION_LEVEL) {
                    start();
                }
            }
        }
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mShowCmBatteryNaviBar = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0) == 7);
         mNVShow = (Settings.System.getInt(resolver,
                    Settings.System.NAVI_BUTTONS, 1) == 1);
        if (mShowCmBatteryNaviBar && mNVShow) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }

	Drawable d = getProgressDrawable();
        if (d instanceof LayerDrawable) {
            Drawable bar = ((LayerDrawable) d)
                    .findDrawableByLayerId(com.android.internal.R.id.progress);
            if (bar != null) {
                String color = Settings.System
                        .getString(resolver, Settings.System.STATUS_BAR_BATTERY_COLOR);
                Integer barColor = null;
                if (!TextUtils.isEmpty(color)) {
                    try {
                        barColor = Color.parseColor(color);
                    } catch (IllegalArgumentException e) {
                    }
                }
                if (barColor != null) {
                    bar.setColorFilter(barColor, PorterDuff.Mode.SRC);
                } else {
                    bar.clearColorFilter();
                }
                invalidate();
            }
        }

        if (mBatteryCharging && mBatteryLevel < STOP_ANIMATION_LEVEL) {
            start();
        } else {
            stop();
        }
    }

    @Override
    public void run() {
        mChargingLevel++;
        if (mChargingLevel > 100) {
            mChargingLevel = mBatteryLevel;
        }
        setProgress(mChargingLevel);
        long delay = (long) (FRAME_DURATION * mInterpolator
                .getInterpolation(100 / (float) mChargingLevel));
        mHandler.postDelayed(this, delay);
    }

    @Override
    public void start() {
        if (!isRunning()) {
            mHandler.removeCallbacks(this);
            mChargingLevel = mBatteryLevel;
            mHandler.post(this);
        }
    }

    @Override
    public void stop() {
        if (isRunning()) {
            mHandler.removeCallbacks(this);
            mChargingLevel = -1;
        }
        setProgress(mBatteryLevel);
    }

    @Override
    public boolean isRunning() {
        return mChargingLevel != -1;
    }

}
