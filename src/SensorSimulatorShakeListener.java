/*
 * Copyright 2011 Raymund Lew
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file was originally written by Matthew Wiggins and modified by Raymund
 * Lew on May 10, 2011. The source code was obtained from
 * http://android.hlidskialf.com/blog/code/android-shake-detection-listener.
 * Matthew Wiggins' original license notice is printed here as follows:
 *
 *   The following code was written by Matthew Wiggins
 *   and is released under the APACHE 2.0 license
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * SensorSimulatorShakeListener.java
 *
 * Uses the accelerometer to detect a "shake" motion.
 *
 * This class is implemented for testing purposes using the OpenIntents
 * SensorSimulator Android sensor simulator.
 */

package com.example.android.debuggermenu;

import org.openintents.sensorsimulator.hardware.Sensor;
import org.openintents.sensorsimulator.hardware.SensorEvent;
import org.openintents.sensorsimulator.hardware.SensorEventListener;
import org.openintents.sensorsimulator.hardware.SensorManagerSimulator;

import android.content.Context;
import android.hardware.SensorManager;

/**
 * A {@link org.openintents.sensorsimulator.hardware.SensorEventListener}
 * that recognizes "shake" motions.
 *
 * An interface is provided for actions to be done when a shake is recognized
 * through {@link SensorSimulatorShakeListener.OnShakeListener.
 * SensorSimulatorShakeListener.OnShakeListener}.
 *
 * The constants used to control how a shake is registered (such as the number
 * of shakes it takes to register a shake, or the amount of time the device
 * needs to be shaken to register a shake) can be set with setters.
 *
 * Uses the <a href="http://code.google.com/p/openintents/wiki/SensorSimulator">
 * OpenIntents</a> sensor simulator.
 *
 * Since the sensor simulator only allows one listener to be registered at a
 * time, it is pointless to allow multiple instances of this class to be
 * instantiated. For this reason, this class is a Singleton class. The instance
 * of this class can be retrieved using the {@link #getInstance() getInstance}
 * method.
 *
 * The screen orientation on the application using this listener should probably
 * also be locked (while the sensor is on at least). Shaking the phone a lot
 * will cause the screen orientation to flip.
 *
 * @author Matthew Wiggins, Raymund Lew
 * @see org.openintents.sensorsimulator.hardware.SensorEventListener
 * @see ShakeListener
 * @see <a href="http://code.google.com/p/openintents/wiki/SensorSimulator">
 *      OpenIntents SensorSimulator</a>
 */
public class SensorSimulatorShakeListener implements SensorEventListener {
    private int forceThreshold = 300;
    private int timeThreshold = 100;
    private int shakeTimeout = 500;
    private int shakeDuration = 700;
    private int joltCount = 3;

    private SensorManagerSimulator mSensorMgr = null;
    private float mLastX = -1.0f, mLastY = -1.0f, mLastZ = -1.0f;
    private long mLastTime;
    private OnShakeListener mShakeListener;
    private Context mContext;
    private int mJoltCount = 0;
    private long mLastShake;
    private long mLastForce;

    /* Singleton object here */
    private final static SensorSimulatorShakeListener INSTANCE = new SensorSimulatorShakeListener();

    /**
     * Interface used to set the actions to be performed when a shake is
     * registered.
     *
     * @author Matthew Wiggins
     *
     */
    public interface OnShakeListener {
        public void onShake();
    }

    /**
     * This is a singleton class, so the constructor is private.
     */
    private SensorSimulatorShakeListener() {
    }

    public static SensorSimulatorShakeListener getInstance() {
        return INSTANCE;
    }

    /**
     * Sets up the context from which to get the sensor service.
     */
    public void setContext(Context context) {
        mContext = context;
    }

    /**
     * @param forceThreshold
     *            the speed it takes for the device to register one "jolt."
     *            Because a "shake" has been defined in this context as a series
     *            of back and forth motions, a "jolt" will be defined here as
     *            one such back or forth motion. It is measured in meters per
     *            hundredth of a second, or in other words, m / s * 100. For
     *            instance, if the desired threshold is 4 m/s then set
     *            forceThreshold to be 400. The default value is 300.
     */
    public void setForceThreshold(int forceThreshold) {
        this.forceThreshold = forceThreshold;
    }

    /**
     * @param timeThreshold
     *            the minimum amount of time needed to register one jolt
     *            measured in milliseconds. The default value is 100.
     */
    public void setTimeThreshold(int timeThreshold) {
        this.timeThreshold = timeThreshold;
    }

    /**
     * @param shakeTimeout
     *            the amount of time needed to reset the jolt counter back to 0
     *            measured in milliseconds. The default value is 500.
     */
    public void setShakeTimeout(int shakeTimeout) {
        this.shakeTimeout = shakeTimeout;
    }

    /**
     * @param shakeDuration
     *            the total amount of time the device needs to be shaken in
     *            order to register a shake, measured in milliseconds. The
     *            default value is 700.
     */
    public void setShakeDuration(int shakeDuration) {
        this.shakeDuration = shakeDuration;
    }

    /**
     * @param joltCount
     *            the minimum number of jolts needed to register a shake. The
     *            default value is 3.
     */
    public void setJoltCount(int joltCount) {
        this.joltCount = joltCount;
    }

    /**
     * Call this method using a new instance of OnShakeListener to overload the
     * onShake method in the
     * {@link SensorSimulatorShakeListener.OnShakeListener.SensorSimulatorShakeListener.OnShakeListener}
     * interface.
     *
     * @param listener
     */
    public void setOnShakeListener(OnShakeListener listener) {
        mShakeListener = listener;
    }

    /**
     * Registers the listener to begin listening for shakes.
     *
     * This method should be used in onResume functions.
     */
    public void start() {
        /* Don't register if already registered! */
        if (mSensorMgr != null) {
            return;
        }
        if ((mSensorMgr = SensorManagerSimulator.getSystemService(mContext,
                Context.SENSOR_SERVICE)) == null) {
            throw new UnsupportedOperationException("Sensors not supported");
        }
        mSensorMgr.connectSimulator();
        if (!mSensorMgr.registerListener(this,
                mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME)) {
            mSensorMgr.unregisterListener(this);
            throw new UnsupportedOperationException(
                    "Accelerometer not supported");
        }
    }

    /**
     * Unregisters the listener to stop listening for shakes.
     *
     * This method should be used in onPause and onStop functions.
     */
    public void stop() {
        if (mSensorMgr != null) {
            mSensorMgr.unregisterListener(this);
            mSensorMgr.disconnectSimulator();
            mSensorMgr = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.type != Sensor.TYPE_ACCELEROMETER) {
            return;
        }
        long now = System.currentTimeMillis();

        if ((now - mLastForce) > shakeTimeout) {
            mJoltCount = 0;
        }

        if ((now - mLastTime) > timeThreshold) {
            long diff = now - mLastTime;
            float speed = Math.abs(event.values[0] + event.values[1]
                    + event.values[2] - mLastX - mLastY - mLastZ)
                    / diff * 10000;
            if (speed > forceThreshold) {
                if ((++mJoltCount >= joltCount)
                        && (now - mLastShake > shakeDuration)) {
                    mLastShake = now;
                    mJoltCount = 0;
                    if (mShakeListener != null) {
                        mShakeListener.onShake();
                    }
                }
                mLastForce = now;
            }
            mLastTime = now;
            mLastX = event.values[0];
            mLastY = event.values[1];
            mLastZ = event.values[2];
        }
    }

}
