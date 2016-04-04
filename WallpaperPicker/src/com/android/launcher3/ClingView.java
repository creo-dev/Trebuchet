package com.android.launcher3;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Created by farhan on 23/1/16.
 */
public class ClingView extends FrameLayout {

    private Launcher mLauncher;
    private View clingTextView;
    private LauncherClings launcherClings;
    private boolean hack;

    public ClingView(Context context) {
        super(context);
    }

    public ClingView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClingView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void bindLauncherandTextView(Launcher launcher, View textView, LauncherClings clings){
        mLauncher = launcher;
        clingTextView = textView;
        launcherClings = clings;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if(mLauncher != null) {
            mLauncher.passEventToEmmaLauncher(this, ev);
        }
        if(ev.getRawX() > 1000 && ev.getRawY() > 725 && ev.getRawX() < 1345 && ev.getRawY() < 860) {
            clingTextView.onTouchEvent(ev);
            if(ev.getAction()==MotionEvent.ACTION_UP || ev.getAction()==MotionEvent.ACTION_POINTER_UP){
                if(hack) {
                    launcherClings.dismissLongPressCling();
                }
            }else if((ev.getAction()==MotionEvent.ACTION_DOWN || ev.getAction()==MotionEvent.ACTION_POINTER_DOWN)){
                hack = true;
            }
        }else{
            hack = false;
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return onInterceptTouchEvent(ev);
    }
}