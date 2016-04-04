package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.GridView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EmmaLaunchHelper extends BroadcastReceiver implements View.OnTouchListener {

    private float mDownY;
    private float mDownX;
    private float mLastY;
    private float mLocalMaxima;
    private boolean didLaunch;
    private float translation;
    private boolean tappedWhenScrollable = false;

    public static final float MIN_TRANSLATION_Y = -435;
    private static final float LAUNCH_THRESHOLD = 320;
    private static final float HESITATION_THRESHOLD = 20;
    private static final float EASING_FACTOR = 4;
    private static final float ASYMPTOTE = 1.1f;
    private volatile long eventFlagTime = 0L;
    private volatile boolean LaunchCommandSent = false;

    private float velocity;
    private float velocityInitialPosition;
    private long velocityInitialTime;

    private View emmaHeaderView, fadeView, dragView;
    private LauncherClings cling;
    private Interpolator easingInterpolator = new TanInverseInterpolator(ASYMPTOTE, EASING_FACTOR);
    private OnLaunchDragStatusChangedListener mOnLaunchDragStatusChangedListener;
    private float deltaY, deltaX;

    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public void setOnLaunchDragStatusChangedListener(OnLaunchDragStatusChangedListener onLaunchDragStatusChangedListener) {
        mOnLaunchDragStatusChangedListener = onLaunchDragStatusChangedListener;
    }

    public interface OnLaunchDragStatusChangedListener {
        void onDragStarted();
        void onDragging(float completion);
        void onDragEnded(boolean didLaunch);
    }

    public void bind(View header, View fadeView, View dragView) {
        if(header != null) {
            this.emmaHeaderView = header;
            this.fadeView = fadeView;
            this.dragView = dragView;
            emmaHeaderView.setTranslationY(MIN_TRANSLATION_Y);
        }
    }

    public void bindCling(LauncherClings cling){
        this.cling = cling;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        eventFlagTime = System.currentTimeMillis();
        if(emmaHeaderView != null) {
            if (!didLaunch) {
                if (event != null && event.getPointerCount() == 1) {
                    if (v instanceof GridView && ((GridView) v).canScrollList(-1)) {
                        tappedWhenScrollable = true;
                        mDownY = 0;
                        return false;
                    } else {
                        if (mDownY == 0) {
                            mDownY = event.getY();
                            mDownX = event.getX();
                            LaunchCommandSent = false;
                        }
                    }
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            mDownY = event.getY();
                            mDownX = event.getX();
                            break;

                        case MotionEvent.ACTION_MOVE:
                            if (tappedWhenScrollable) {
                                if (mOnLaunchDragStatusChangedListener != null) {
                                    mOnLaunchDragStatusChangedListener.onDragEnded(false);
                                    didLaunch = false;
                                }
                                return false;
                            }
                            float y = event.getY();
                            if (velocityInitialTime > 0 && y > velocityInitialPosition) {
                                float newVel = (y - velocityInitialPosition) / (System.currentTimeMillis() - velocityInitialTime);
                                if(newVel > velocity){
                                    velocity = newVel;
                                }
                            }
                            velocityInitialPosition = y;
                            velocityInitialTime = System.currentTimeMillis();
                            if (mLastY > y) {
                                if (mLocalMaxima < y) {
                                    mLocalMaxima = y;
                                }
                            } else {
                                mLocalMaxima = y;
                            }
                            mLastY = y;
                            deltaY = mDownY - event.getY();
                            deltaX = mDownX - event.getX();
                            if (deltaY > 0 || (-deltaY) < Math.abs(deltaX)) {
                                emmaHeaderView.setTranslationY(MIN_TRANSLATION_Y);
                                if (mOnLaunchDragStatusChangedListener != null) {
                                    mOnLaunchDragStatusChangedListener.onDragging(0);
                                }
                                return false;
                            } else {
                                translation = 2.0f * Math.abs(y - mDownY);
                                float maxTranslation = Math.abs(MIN_TRANSLATION_Y);
                                float factor = Math.abs((translation) / maxTranslation);
                                float interpolatedTranslation = easingInterpolator.getInterpolation(factor) * maxTranslation;
                                if (interpolatedTranslation - maxTranslation <= 0) {
                                    emmaHeaderView.setTranslationY(interpolatedTranslation - maxTranslation);
                                    if (mOnLaunchDragStatusChangedListener != null) {
                                        mOnLaunchDragStatusChangedListener.onDragging(interpolatedTranslation / maxTranslation);
                                    }
                                } else {
                                    emmaHeaderView.setTranslationY(0);
                                    if (mOnLaunchDragStatusChangedListener != null) {
                                        mOnLaunchDragStatusChangedListener.onDragging(1);
                                    }
                                }
                                executorService.schedule(worker,50, TimeUnit.MILLISECONDS);
                                return true;
                            }
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP:
                            if (mDownY != 0 && mDownY != 0) {
                                y = event.getY();
                                if (!tappedWhenScrollable && translation >= LAUNCH_THRESHOLD && (mLocalMaxima - y < HESITATION_THRESHOLD)) {
                                    launchEmma();
                                } else {
                                    resetAnimation();
                                }
                                tappedWhenScrollable = false;
                                mLocalMaxima = 0;
                                mDownY = 0;
                                deltaX = 0;
                                deltaY = 0;
                            }
                            break;
                    }
                }else{
                    switch (event.getAction()){
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP:    
                        case MotionEvent.ACTION_POINTER_UP:
                            if (mDownY != 0 && mDownY != 0) {
                                float y = event.getY();
                                if (!tappedWhenScrollable && translation >= LAUNCH_THRESHOLD && (mLocalMaxima - y < HESITATION_THRESHOLD)) {
                                    launchEmma();
                                } else {
                                    resetAnimation();
                                }
                                tappedWhenScrollable = false;
                                mLocalMaxima = 0;
                                mDownY = 0;
                                deltaX = 0;
                                deltaY = 0;
                            }
                            break;
                        default:
                            tappedWhenScrollable = false;
                            mLocalMaxima = 0;
                            mDownY = 0;
                            deltaX = 0;
                            deltaY = 0;
                    }
                }
            }
        }
        return false;
    }

    private void launchEmma() {
        LaunchCommandSent = true;
        if(emmaHeaderView != null) {
            translation = 0;
            tappedWhenScrollable = false;
            mLocalMaxima = 0;
            mDownY = 0;
            mDownX = 0;
            deltaX = 0;
            deltaY = 0;
            float animationDuration = 150;
            if (emmaHeaderView.getY() == 0) {
                animationDuration = 0;
            } else if (velocity != 0) {
                animationDuration = animationDuration / velocity;
            }
            emmaHeaderView.animate().setDuration((long) ((emmaHeaderView.getY() / MIN_TRANSLATION_Y) * animationDuration)).translationY(0).withEndAction(new Runnable() {
                @Override
                public void run() {
                    launchKeyboard(emmaHeaderView.getContext());
                    try {
                        Intent i = new Intent("com.mmce.hachi.search.MainActivity");
                        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        emmaHeaderView.getContext().startActivity(i);
                        if (mOnLaunchDragStatusChangedListener != null) {
                            mOnLaunchDragStatusChangedListener.onDragEnded(true);
                            didLaunch = true;
                        }
                    } catch (Exception e) {
                        resetAnimation();
                    }

                }
            });
            dragView.animate().setDuration((long) ((emmaHeaderView.getY() / MIN_TRANSLATION_Y) * animationDuration)).alpha(0f);
            if(cling != null) cling.dismissLongPressCling();
            fadeView.animate().setDuration((long) ((emmaHeaderView.getY() / MIN_TRANSLATION_Y) * animationDuration)).alpha(1f);
        }
    }

    public void onResume() {
        if(emmaHeaderView != null) {
            translation = 0;
            tappedWhenScrollable = false;
            mLocalMaxima = 0;
            mDownY = 0;
            mDownX = 0;
            deltaX = 0;
            deltaY = 0;
            didLaunch = false;
            emmaHeaderView.setTranslationY(MIN_TRANSLATION_Y);
            emmaHeaderView.setVisibility(View.VISIBLE);
            if (fadeView != null) {
                System.out.println("Resetting alpha here 1");
                fadeView.setAlpha(0f);
            }
            if (dragView != null) {
                dragView.setAlpha(1f);
            }
            if(cling != null && cling.getClingView() != null){
                if (cling.shouldShowFirstRunOrMigrationClings() && !cling.isClingDone()){
                    cling.getClingView().setAlpha(1f);
                }
            }
            hideKeyboard();
        }
    }

    private static final int RESET_ANIMATION_DURATION = 350;

    public void resetAnimation() {
        LaunchCommandSent = true;
        if(emmaHeaderView != null) {
            translation = 0;
            tappedWhenScrollable = false;
            mLocalMaxima = 0;
            mDownY = 0;
            mDownX = 0;
            deltaX = 0;
            deltaY = 0;
            emmaHeaderView.animate().setDuration(RESET_ANIMATION_DURATION).translationY(MIN_TRANSLATION_Y);
            dragView.animate().setDuration(RESET_ANIMATION_DURATION).alpha(1f);
            if(cling != null && cling.getClingView() != null){
                if (cling.shouldShowFirstRunOrMigrationClings() && !cling.isClingDone()){
                    cling.getClingView().animate().setDuration(RESET_ANIMATION_DURATION).alpha(1f);
                }
            }
            fadeView.animate().setDuration(RESET_ANIMATION_DURATION).alpha(0f);
            mOnLaunchDragStatusChangedListener.onDragEnded(false);
            didLaunch = false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(emmaHeaderView != null) {
            translation = 0;
            tappedWhenScrollable = false;
            mLocalMaxima = 0;
            mDownY = 0;
            mDownX = 0;
            deltaX = 0;
            deltaY = 0;
            didLaunch = false;
            if (emmaHeaderView != null) {
                emmaHeaderView.setTranslationY(MIN_TRANSLATION_Y);
                System.out.println("Launchcomplete Intent received and translation");
            } else {
                System.out.println("Launchcomplete Intent received and null");
            }
            if (fadeView != null) {
                System.out.println("Resetting alpha here 2");
                fadeView.setAlpha(0f);
            }
            if (dragView != null) {
                dragView.setAlpha(1f);
            }
            if(cling != null){
                cling.dismissLongPressCling();
            };
        }
    }

    private void launchKeyboard(Context context){
        if(emmaHeaderView != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.toggleSoftInputFromWindow(emmaHeaderView.getWindowToken(), InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void hideKeyboard(){
        if(emmaHeaderView != null){
            InputMethodManager imm = (InputMethodManager) emmaHeaderView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(emmaHeaderView.getWindowToken(), 0);
        }
    }

    private Runnable worker = new Runnable() {
        @Override
        public void run() {
            if(!LaunchCommandSent){
                if(System.currentTimeMillis() - eventFlagTime < 50){
                    return;
                }else{
                    if (!tappedWhenScrollable && translation >= LAUNCH_THRESHOLD && (mLocalMaxima - mLastY < HESITATION_THRESHOLD)) {
                        System.out.println("Emma Launcher Helper Here 1");
                        launchEmma();
                    } else {
                        System.out.println("Emma Launcher Helper Here 2");
                        resetAnimation();
                    }
                }
            }
        }
    };

}
