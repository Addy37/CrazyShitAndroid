package com.webapp.crazyshit;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.view.OrientationEventListener;

/** Listens for deliberate portrait/landscape movement while an eligible media view is active. */
final class SensorMediaOrientationListener implements SensorEventListener {
    enum Position { PORTRAIT, LANDSCAPE }

    interface Callback {
        void onPositionChanged(Position position);
    }

    private static final int PORTRAIT_EDGE_DEGREES = 30;
    private static final int LANDSCAPE_HALF_RANGE_DEGREES = 25;
    private static final long STABLE_DELAY_MS = 350L;
    private static final float FLAT_GRAVITY_Z = SensorManager.GRAVITY_EARTH * 0.75f;
    private static final float GRAVITY_FILTER = 0.8f;

    private final Activity activity;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final OrientationEventListener orientationListener;

    private boolean enabled;
    private boolean hasGravity;
    private float gravityZ;
    private int lastDegrees = OrientationEventListener.ORIENTATION_UNKNOWN;
    private Position stablePosition;
    private Position pendingPosition;

    private final Runnable dispatchPending = () -> {
        if (!enabled || pendingPosition == null) return;
        Position current = classify(lastDegrees);
        if (isFlat() || current != pendingPosition) {
            clearPending();
            return;
        }
        stablePosition = pendingPosition;
        pendingPosition = null;
        callback.onPositionChanged(stablePosition);
    };

    SensorMediaOrientationListener(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null
                ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        orientationListener = new OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
            @Override
            public void onOrientationChanged(int orientation) {
                lastDegrees = orientation;
                evaluate();
            }
        };
    }

    void enable() {
        if (enabled || !PhoneOrientationPolicy.isPhoneSized(activity)
                || !orientationListener.canDetectOrientation()) return;
        enabled = true;
        stablePosition = null;
        pendingPosition = null;
        lastDegrees = OrientationEventListener.ORIENTATION_UNKNOWN;
        hasGravity = accelerometer == null;
        gravityZ = 0f;
        if (accelerometer != null && sensorManager != null) {
            hasGravity = !sensorManager.registerListener(
                    this,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_UI
            );
        }
        orientationListener.enable();
    }

    void disable() {
        if (!enabled) return;
        enabled = false;
        orientationListener.disable();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        handler.removeCallbacks(dispatchPending);
        pendingPosition = null;
        stablePosition = null;
        lastDegrees = OrientationEventListener.ORIENTATION_UNKNOWN;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!enabled || event == null || event.values.length < 3) return;
        float nextZ = event.values[2];
        gravityZ = hasGravity
                ? GRAVITY_FILTER * gravityZ + (1f - GRAVITY_FILTER) * nextZ
                : nextZ;
        hasGravity = true;
        evaluate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void evaluate() {
        if (!enabled || !hasGravity || isFlat()) {
            clearPending();
            return;
        }
        Position position = classify(lastDegrees);
        if (position == null || position == stablePosition) {
            clearPending();
            return;
        }
        if (position == pendingPosition) return;
        pendingPosition = position;
        handler.removeCallbacks(dispatchPending);
        handler.postDelayed(dispatchPending, STABLE_DELAY_MS);
    }

    private void clearPending() {
        pendingPosition = null;
        handler.removeCallbacks(dispatchPending);
    }

    private boolean isFlat() {
        return hasGravity && Math.abs(gravityZ) >= FLAT_GRAVITY_Z;
    }

    private Position classify(int degrees) {
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return null;
        if (degrees <= PORTRAIT_EDGE_DEGREES || degrees >= 360 - PORTRAIT_EDGE_DEGREES) {
            return Position.PORTRAIT;
        }
        if (Math.abs(degrees - 90) <= LANDSCAPE_HALF_RANGE_DEGREES
                || Math.abs(degrees - 270) <= LANDSCAPE_HALF_RANGE_DEGREES) {
            return Position.LANDSCAPE;
        }
        return null;
    }
}
