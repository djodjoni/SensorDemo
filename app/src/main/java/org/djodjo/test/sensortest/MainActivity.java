/*
 * Copyright (C) 2014 Kalin Maldzhanski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.djodjo.test.sensortest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.djodjo.test.sensortest.SensorHelper.MonitoredSensor;

import com.google.ads.AdView;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;


public class MainActivity extends Activity implements android.os.Handler.Callback {

    public static String TAG = MainActivity.class.getSimpleName();
    private static boolean DEBUG = true;

    private static final int MSG_UPDATE_ACTUAL_HZ = 0x31415;

    private TableLayout mTableLayout;
    private TextView mTextTargetHz;
    private TextView mTextActualHz;
    private SensorHelper mSensorHelper;

    private final Map<MonitoredSensor, DisplayInfo> mDisplayedSensors =
        new HashMap<SensorHelper.MonitoredSensor, MainActivity.DisplayInfo>();
    private final android.os.Handler mUiHandler = new android.os.Handler(this);
    private int mTargetSampleRate;
    private long mLastActualUpdateMs;
    
    private AdView adView;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sensors);
        mTableLayout = (TableLayout) findViewById(R.id.tableLayout);
        mTextTargetHz = (TextView) findViewById(R.id.textSampleRate);
        mTextActualHz = (TextView) findViewById(R.id.textActualRate);

        mTextTargetHz.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                updateSampleRate();
                return false;
            }
        });
        mTextTargetHz.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                updateSampleRate();
            }
        });
        createSensorUi();
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.d(TAG, "onResume");
        // BaseBindingActivity.onResume will bind to the service.
        super.onResume();
        //updateError();
       
        mSensorHelper.onEnableSensor("all");
        mSensorHelper.startSensors();
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.d(TAG, "onPause");
        // BaseBindingActivity.onResume will unbind from (but not stop) the service.
        super.onPause();
        mSensorHelper.stopSensors();
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        removeSensorUi();
        if (adView != null) {
          adView.destroy();
        }
        super.onDestroy();

    }

    private void createSensorUi() {
        final LayoutInflater inflater = getLayoutInflater();

        if (!mDisplayedSensors.isEmpty()) {
            removeSensorUi();
        }

        mSensorHelper = new SensorHelper(this);
        if (mSensorHelper != null) {
            mSensorHelper.addUiHandler(mUiHandler);
            mUiHandler.sendEmptyMessage(MSG_UPDATE_ACTUAL_HZ);

            assert mDisplayedSensors.isEmpty();
            List<MonitoredSensor> sensors = mSensorHelper.getSensors();
            for (MonitoredSensor sensor : sensors) {
                final TableRow row = (TableRow) inflater.inflate(R.layout.sensor_row,
                                                                 mTableLayout,
                                                                 false);
                mTableLayout.addView(row);
                mDisplayedSensors.put(sensor, new DisplayInfo(sensor, row));
            }
        }
    }

    private void removeSensorUi() {
        if (mSensorHelper != null) {
            mSensorHelper.removeUiHandler(mUiHandler);
            mSensorHelper = null;
        }
        mTableLayout.removeAllViews();
        for (DisplayInfo info : mDisplayedSensors.values()) {
            info.release();
        }
        mDisplayedSensors.clear();
    }

    private class DisplayInfo implements CompoundButton.OnCheckedChangeListener {
        private MonitoredSensor mSensor;
        private CheckBox mChk;
        private TextView mVal;
        private TextView mInfo;

        public DisplayInfo(MonitoredSensor sensor, TableRow row) {
            mSensor = sensor;

            // Initialize displayed checkbox for this sensor, and register
            // checked state listener for it.
            mChk = (CheckBox) row.findViewById(R.id.row_checkbox);
            mChk.setText(sensor.getUiName());
            mChk.setEnabled(sensor.isEnabledByApp());
            mChk.setChecked(sensor.isEnabledByUser());
            mChk.setOnCheckedChangeListener(this);

            // Initialize displayed text box for this sensor.
            mVal = (TextView) row.findViewById(R.id.row_textview);
            mVal.setText(sensor.getValue());
            
            mInfo = (TextView) row.findViewById(R.id.row_infoview);
            mInfo.setText(sensor.getInfo());
            
            final View imgInfo = row.findViewById(R.id.row_img_info);
            ((ImageView)imgInfo).getDrawable().setColorFilter(Color.GREEN | Color.GRAY, Mode.MULTIPLY);
            
            imgInfo.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					if(mInfo.getVisibility() == View.VISIBLE) {
						mInfo.setVisibility(View.GONE);
						((ImageView)imgInfo).getDrawable().setColorFilter(Color.GREEN | Color.GRAY, Mode.MULTIPLY);
					} else {
						mInfo.setVisibility(View.VISIBLE);
						((ImageView)imgInfo).getDrawable().setColorFilter(0, Mode.DST);
					}
				}
			});
        }

        /**
         * Handles checked state change for the associated CheckBox. If check
         * box is checked we will register sensor change listener. If it is
         * unchecked, we will unregister sensor change listener.
         */
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mSensor != null) {
                mSensor.onCheckedChanged(isChecked);
            }
        }

        public void release() {
            mChk = null;
            mVal = null;
            mSensor = null;

        }

        public void updateState() {
            if (mChk != null && mSensor != null) {
                mChk.setEnabled(mSensor.isEnabledByApp());
                mChk.setChecked(mSensor.isEnabledByUser());
            }
        }

        public void updateValue() {
            if (mVal != null && mSensor != null) {
                mVal.setText(mSensor.getValue());
            }
        }
    }

    /** Implementation of Handler.Callback */
    @Override
    public boolean handleMessage(Message msg) {
        DisplayInfo info = null;
        switch (msg.what) {
        case SensorHelper.SENSOR_STATE_CHANGED:
            info = mDisplayedSensors.get(msg.obj);
            if (info != null) {
                info.updateState();
            }
            break;
        case SensorHelper.SENSOR_DISPLAY_MODIFIED:
            info = mDisplayedSensors.get(msg.obj);
            if (info != null) {
                info.updateValue();
            }
            if (mSensorHelper != null) {
                // Update the "actual rate" field if the value has changed
                long ms = mSensorHelper.getActualUpdateMs();
                if (ms != mLastActualUpdateMs) {
                    mLastActualUpdateMs = ms;
                    String hz = mLastActualUpdateMs <= 0 ? "--" :
                                    Integer.toString((int) Math.ceil(1000. / ms));
                    mTextActualHz.setText(hz);
                }
            }
            break;
        case MSG_UPDATE_ACTUAL_HZ:
            if (mSensorHelper != null) {
                // Update the "actual rate" field if the value has changed
                long ms = mSensorHelper.getActualUpdateMs();
                if (ms != mLastActualUpdateMs) {
                    mLastActualUpdateMs = ms;
                    String hz = mLastActualUpdateMs <= 0 ? "--" :
                                    Integer.toString((int) Math.ceil(1000. / ms));
                    mTextActualHz.setText(hz);
                }
                mUiHandler.sendEmptyMessageDelayed(MSG_UPDATE_ACTUAL_HZ, 1000 /*1s*/);
            }
        }
        return true; // we consumed this message
    }
    
    




    private void updateSampleRate() {
        String str = mTextTargetHz.getText().toString();
        try {
            int hz = Integer.parseInt(str.trim());

            // Cap the value. 50 Hz is a reasonable max value for the emulator.
            if (hz <= 0 || hz > 50) {
                hz = 50;
            }

            if (hz != mTargetSampleRate) {
                mTargetSampleRate = hz;
                if (mSensorHelper != null) {
                    mSensorHelper.setUpdateTargetMs(hz <= 0 ? 0 : (int)(1000.0f / hz));
                }
            }
        } catch (Exception ignore) {}
    }
}

