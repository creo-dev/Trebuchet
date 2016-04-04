package com.android.launcher3;

import android.view.animation.Interpolator;

public class TanInverseInterpolator implements Interpolator {

	private float asymptote = 1f;
	private float easing = 1f;
	
	public TanInverseInterpolator(float asymptote, float easing) {
		this.asymptote = asymptote;
		this.easing = easing;
	}
	
    @Override
    public float getInterpolation(float input) {
        return asymptote * onPiByTwoScale(tanInverse(input));
    }

    private float onPiByTwoScale(float value) {
        return (float) (value / (Math.PI / 2));
    }

    private float tanInverse(float value) {
        return (float) Math.atan(easing * value);
    }
}
