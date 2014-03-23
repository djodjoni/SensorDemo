package org.djodjo.test.sensortest;

import java.util.ArrayList;
import java.util.List;
import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Build;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

/**
 * Implements sensors emulation.
 */
public class SensorHelper {


	private static String TAG = SensorHelper.class.getSimpleName();

	private static boolean DEBUG = false;
	/**
	 * The target update time per sensor. Ignored if 0 or negative.
	 * Sensor updates that arrive faster than this delay are ignored.
	 * Ideally the emulator can be updated at up to 50 fps, however
	 * for average power devices something like 20 fps is more
	 * reasonable.
	 * Default value should match res/values/strings.xml > sensors_default_sample_rate.
	 */
	private long mUpdateTargetMs = 1000/20; // 20 fps in milliseconds
	/** Accumulates average update frequency. */
	private long mGlobalAvgUpdateMs = 0;

	/** Array containing monitored sensors. */
	private final List<MonitoredSensor> mSensors = new ArrayList<MonitoredSensor>();
	/** Sensor manager. */
	private SensorManager mSenMan;

	/*
	 * Messages exchanged with the UI.
	 */
	/** Lists UI handlers attached to this channel. */
	private final List<android.os.Handler> mUiHandlers = new ArrayList<android.os.Handler>();

	/**
	 * Sensor "enabled by emulator" state has changed. Parameter {@code obj} is
	 * the {@link MonitoredSensor}.
	 */
	public static final int SENSOR_STATE_CHANGED = 1;
	/**
	 * Sensor display value has changed. Parameter {@code obj} is the
	 * {@link MonitoredSensor}.
	 */
	public static final int SENSOR_DISPLAY_MODIFIED = 2;

	/**
	 * Constructs SensorHelper instance.
	 *
	 * @param context app context.
	 */
	public SensorHelper(Context context) {
		mSenMan = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		// Iterate through the available sensors, adding them to the array.
		List<Sensor> sensors = mSenMan.getSensorList(Sensor.TYPE_ALL);
		int cur_index = 0;
		for (int n = 0; n < sensors.size(); n++) {
			Sensor avail_sensor = sensors.get(n);

			// There can be multiple sensors of the same type. We need only one.
			if (!isSensorTypeAlreadyMonitored(avail_sensor.getType())) {
				// The first sensor we've got for the given type is not
				// necessarily the right one. So, use the default sensor
				// for the given type.
				Sensor def_sens = mSenMan.getDefaultSensor(avail_sensor.getType());
				MonitoredSensor to_add = new MonitoredSensor(def_sens);
				cur_index++;
				mSensors.add(to_add);
				//                if (DEBUG)
				//                    Log.d(TAG, String.format(
				//                            "Monitoring sensor #%02d: Name = '%s', Type = 0x%x",
				//                            cur_index, def_sens.getName(), def_sens.getType()));
			}
		}
	}

	/**
	 * Returns the list of sensors found on the device.
	 * @return A non-null possibly-empty list of sensors.
	 */
	public List<MonitoredSensor> getSensors() {
		return mSensors;
	}

	/**
	 * Set the target update delay throttling per-sensor, in milliseconds.
	 * <p/>
	 * For example setting it to 1000/50 means that updates for a <em>given</em> sensor
	 * faster than 50 fps is discarded.
	 *
	 * @param updateTargetMs 0 to disable throttling, otherwise a > 0 millisecond minimum
	 *   between sensor updates.
	 */
	public void setUpdateTargetMs(long updateTargetMs) {
		mUpdateTargetMs = updateTargetMs;
	}

	/**
	 * Returns the actual average time in milliseconds between same-sensor updates.
	 *
	 * @return The actual average time in milliseconds between same-sensor updates or 0.
	 */
	public long getActualUpdateMs() {
		return mGlobalAvgUpdateMs;
	}

	/**
	 * Handles 'enable' message.
	 *
	 * @param name friendly name of a sensor to enable, or "all" to
	 *            enable all sensors.
	 */
	public void onEnableSensor(String name) {
		if (name.contentEquals("all")) {
			// Enable all sensors.
			for (MonitoredSensor sensor : mSensors) {
				sensor.enableSensor();
			}
		} else {
			// Lookup sensor by friendly name.
			final MonitoredSensor sensor = getSensorByEFN(name);
			if (sensor != null) {
				sensor.enableSensor();
			}
		}
	}

	/**
	 * Handles 'disable' message.
	 *
	 * @param name friendly name of a sensor to disable, or "all" to
	 *            disable all sensors.
	 */
	public void onDisableSensor(String name) {
		if (name.contentEquals("all")) {
			// Disable all sensors.
			for (MonitoredSensor sensor : mSensors) {
				sensor.disableSensor();
			}
		} else {
			// Lookup sensor by friendly name.
			MonitoredSensor sensor = getSensorByEFN(name);
			if (sensor != null) {
				sensor.disableSensor();
			}
		}
	}

	/**
	 * Start listening to all monitored sensors.
	 */
	public void startSensors() {
		for (MonitoredSensor sensor : mSensors) {
			sensor.startListening();
		}
	}

	/**
	 * Stop listening to all monitored sensors.
	 */
	public void stopSensors() {
		for (MonitoredSensor sensor : mSensors) {
			sensor.stopListening();
		}
	}

	/***************************************************************************
	 * Internals
	 **************************************************************************/

	/**
	 * Checks if a sensor for the given type is already monitored.
	 *
	 * @param type Sensor type (one of the Sensor.TYPE_XXX constants)
	 * @return true if a sensor for the given type is already monitored, or
	 *         false if the sensor is not monitored.
	 */
	private boolean isSensorTypeAlreadyMonitored(int type) {
		for (MonitoredSensor sensor : mSensors) {
			if (sensor.getType() == type) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Looks up a monitored sensor by its friendly name.
	 *
	 * @param name friendly name to look up the monitored sensor for.
	 * @return Monitored sensor for the fiven name, or null if sensor was not
	 *         found.
	 */
	private MonitoredSensor getSensorByEFN(String name) {
		for (MonitoredSensor sensor : mSensors) {
			if (sensor.mFriendlyName.contentEquals(name)) {
				return sensor;
			}
		}
		return null;
	}

	/**
	 * Encapsulates a sensor that is being monitored. To monitor sensor changes
	 * each monitored sensor registers with sensor manager as a sensor listener.
	 * To control sensor monitoring from the UI, each monitored sensor has two
	 * UI controls associated with it: - A check box (named after sensor) that
	 * can be used to enable, or disable listening to the sensor changes. - A
	 * text view where current sensor value is displayed.
	 */
	public class MonitoredSensor {
		/** Sensor to monitor. */
		private final Sensor mSensor;
		/** The sensor name to display in the UI. */
		private String mUiName = "";
		/** Text view displaying the value of the sensor. */
		private String mValue = null;
		/** friendly name for the sensor. */
		private String mFriendlyName;
		/** Formats string to show in the TextView. */
		private String mTextFmt;
		/** Sensor values. */
		private float[] mValues = new float[3];
		/**
		 * Enabled state. This state is controlled by the emulator, that
		 * maintains its own list of sensors. So, if a sensor is missing, or is
		 * disabled in the emulator, it should be disabled in this application.
		 */
		private boolean mEnabledByApp = false;
		/** User-controlled enabled state. */
		private boolean mEnabledByUser = true;
		/** Sensor event listener for this sensor. */
		private final OurSensorEventListener mListener = new OurSensorEventListener();

		/**
		 * Constructs MonitoredSensor instance, and register the listeners.
		 *
		 * @param sensor Sensor to monitor.
		 */
		MonitoredSensor(Sensor sensor) {
			mSensor = sensor;
			mEnabledByUser = true;

			// Set appropriate sensor name depending on the type. Unfortunately,
			// we can't really use sensor.getName() here, since the value it
			// returns (although resembles the purpose) is a bit vaguer than it
			// should be. Also choose an appropriate format for the strings that
			// display sensor's value.
			switch (sensor.getType()) {
			case Sensor.TYPE_ACCELEROMETER:
				mUiName = "Accelerometer (m/s2)";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "acceleration";
				break;
			case 9: // Sensor.TYPE_GRAVITY is missing in API 7
				mUiName = "Gravity (m/s2)";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "gravity";
				break;
			case Sensor.TYPE_GYROSCOPE:
				mUiName = "Gyroscope (rad/s)";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "gyroscope";
				break;
			case Sensor.TYPE_LIGHT:
				mUiName = "Light (lux)";
				mTextFmt = "%.0f";
				mFriendlyName = "light";
				break;
			case 10: // Sensor.TYPE_LINEAR_ACCELERATION is missing in API 7
				mUiName = "Linear acceleration (m/s2)";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "linear-acceleration";
				break;
			case Sensor.TYPE_MAGNETIC_FIELD:
				mUiName = "Magnetic field (μT)";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "magnetic-field";
				break;
			case Sensor.TYPE_ORIENTATION:
				mUiName = "Orientation";
				mTextFmt = "%+03.0f %+03.0f %+03.0f";
				mFriendlyName = "orientation";
				break;
			case Sensor.TYPE_PRESSURE:
				mUiName = "Pressure (hPa)";
				mTextFmt = "%.0f";
				mFriendlyName = "pressure";
				break;
			case Sensor.TYPE_PROXIMITY:
				mUiName = "Proximity (cm)";
				mTextFmt = "%.0f";
				mFriendlyName = "proximity";
				break;
			case 11: // Sensor.TYPE_ROTATION_VECTOR is missing in API 7
				mUiName = "Rotation";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "rotation";
				break;
			case Sensor.TYPE_TEMPERATURE:
				mUiName = "Temperature (°C)";
				mTextFmt = "%.0f";
				mFriendlyName = "temperature";
				break;
			case Sensor.TYPE_RELATIVE_HUMIDITY:
				mUiName = "Relative Humidity (%)";
				mTextFmt = "%.0f";
				mFriendlyName = "relhumidity";
				break;
			case Sensor.TYPE_AMBIENT_TEMPERATURE:
				mUiName = "Ambient Temperature (°C)";
				mTextFmt = "%.0f";
				mFriendlyName = "ambtemperature";
				break;
				//android 4.3 api18
			case 14://Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED: 
				mUiName = "Magnetic field Uncalibrated (μT)";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "magnetic-field-uncalibrated";
				break;    
			case 15://Sensor.TYPE_GAME_ROTATION_VECTOR: 
				mUiName = "Game Rotation";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "game-rotation";
				break;    
			case 16://Sensor.TYPE_GYROSCOPE_UNCALIBRATED : 
				mUiName = "Gyroscope Uncalibrated (rad/s)";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "gyroscope-uncalibrated";
				break;
			case 17://Sensor.TYPE_SIGNIFICANT_MOTION : 
				mUiName = "Significant Motion Trigger";
				mTextFmt = "%.0f at %2$tT (nanosec:  %3$d)";
				mFriendlyName = "significant-motion";
				break;
				// API 19
			case 18://Sensor.TYPE_STEP_DETECTOR : 
				mUiName = "Foot Step Detector";
				mTextFmt = "%.0f";
				mFriendlyName = "step-detector";
				break;    
			case 19://TYPE_STEP_COUNTER : 
				mUiName = "Foot Step Counter";
				mTextFmt = "%.0f";
				mFriendlyName = "step-counter";
				break;    
			case 20://Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR : 
				mUiName = "Geomagnetic Rotation";
				mTextFmt = "%+.2f %+.2f %+.2f";
				mFriendlyName = "Geomagnetic-rotation";
				break;    

			default:
				mUiName = "<Unknown>";
				mTextFmt = "%.0f";
				mFriendlyName = "unknown";
				if (DEBUG) Loge("Unknown sensor type " + mSensor.getType() +
						" for sensor " + mSensor.getName());
				break;
			}
		}

		/**
		 * Get name for this sensor to display.
		 *
		 * @return Name for this sensor to display.
		 */
		public String getUiName() {
			return mUiName;
		}

		/**
		 * Get info for this sensor.
		 *
		 * @return Name for this sensor to display.
		 */
		public String getInfo() {
			String res = "";
			res += "Name:         " + mSensor.getName() + "\n";
			res += "Type:         " + mSensor.getType() + "\n";
			res += "Vendor:       " + mSensor.getVendor() + "\n";
			res += "Version:      " + mSensor.getVersion() + "\n";
			res += "Power: 		  " + mSensor.getPower() + " mA \n";
			res += "Resolution:   " + mSensor.getResolution() + "\n";
			res += "MinDelay: 	  " + mSensor.getMinDelay() + "\n";
			res += "MaximumRange: " + mSensor.getMaximumRange() + "\n";
			return res;
		}

		/**
		 * Gets current sensor value to display.
		 *
		 * @return Current sensor value to display.
		 */
		public String getValue() {
			if (mValue == null) {
				float[] values = mValues;
				if(mSensor.getType()==17) {
					mValue = String.format(mTextFmt, values[0], (long)values[1], (long)values[2]);
				} else {
					mValue = String.format(mTextFmt, values[0], values[1], values[2]);
				}
			}
			return mValue == null ? "??" : mValue;
		}

		/**
		 * Checks if monitoring of this this sensor has been enabled by
		 * emulator.
		 *
		 * @return true if monitoring of this this sensor has been enabled by
		 *         emulator, or false if emulator didn't enable this sensor.
		 */
		public boolean isEnabledByApp() {
			return mEnabledByApp;
		}

		/**
		 * Checks if monitoring of this this sensor has been enabled by user.
		 *
		 * @return true if monitoring of this this sensor has been enabled by
		 *         user, or false if user didn't enable this sensor.
		 */
		public boolean isEnabledByUser() {
			return mEnabledByUser;
		}

		/**
		 * Handles checked state change for the associated CheckBox. If check
		 * box is checked we will register sensor change listener. If it is
		 * unchecked, we will unregister sensor change listener.
		 */
		public void onCheckedChanged(boolean isChecked) {
			mEnabledByUser = isChecked;
			if (isChecked) {
				startListening();
			} else {
				stopListening();
			}
		}

		/**
		 * Gets sensor type.
		 *
		 * @return Sensor type as one of the Sensor.TYPE_XXX constants.
		 */
		private int getType() {
			return mSensor.getType();
		}

		/**
		 * Gets sensor's friendly name.
		 *
		 * @return Sensor's friendly name.
		 */
		private String getFriendlyName() {
			return mFriendlyName;
		}

		/**
		 * Starts monitoring the sensor.
		 * NOTE: This method is called from outside of the UI thread.
		 */
		@SuppressLint("NewApi")
		private void startListening() {
			if (mEnabledByApp && mEnabledByUser) {
				if (DEBUG) Log.d(TAG, "+++ Sensor " + getFriendlyName() + " is started.");
				if(mSensor.getType()==17 && Build.VERSION.SDK_INT > 17) {
					TriggerEventListener tgev = new TriggerEventListener() {

						@Override
						public void onTrigger(TriggerEvent event) {
							if (hasUiHandler()) {

								mValues[0] = event.values[0];
								mValues[1] = System.currentTimeMillis();
								mValues[2] = event.timestamp;

								mValue = null;

								Message msg = Message.obtain();
								msg.what = SENSOR_DISPLAY_MODIFIED;
								msg.obj = MonitoredSensor.this;
								notifyUiHandlers(msg);

								mSenMan.requestTriggerSensor(this, mSensor);
							}
						}
					};
					mSenMan.requestTriggerSensor(tgev, mSensor);
				} else {
					mSenMan.registerListener(mListener, mSensor, SensorManager.SENSOR_DELAY_FASTEST);
				}

			}
		}

		/**
		 * Stops monitoring the sensor.
		 * NOTE: This method is called from outside of the UI thread.
		 */
		private void stopListening() {
			if (DEBUG) Log.d(TAG, "--- Sensor " + getFriendlyName() + " is stopped.");
			if(mListener != null)
				mSenMan.unregisterListener(mListener);
		}

		/**
		 * Enables sensor events.
		 * NOTE: This method is called from outside of the UI thread.
		 */
		private void enableSensor() {
			if (DEBUG) Log.d(TAG, ">>> Sensor " + getFriendlyName() + " is enabled.");
			mEnabledByApp = true;
			mValue = null;

			Message msg = Message.obtain();
			msg.what = SENSOR_STATE_CHANGED;
			msg.obj = MonitoredSensor.this;
			notifyUiHandlers(msg);
		}

		/**
		 * Disables sensor events.
		 * NOTE: This method is called from outside of the UI thread.
		 */
		private void disableSensor() {
			if (DEBUG) Log.w(TAG, "<<< Sensor " + getFriendlyName() + " is disabled.");
			mEnabledByApp = false;
			mValue = "Disabled by emulator";

			Message msg = Message.obtain();
			msg.what = SENSOR_STATE_CHANGED;
			msg.obj = MonitoredSensor.this;
			notifyUiHandlers(msg);
		}


		private class OurSensorEventListener implements SensorEventListener {
			/** Last update's time-stamp in local thread millisecond time. */
			private long mLastUpdateTS = 0;
			/** Last display update time-stamp. */
			private long mLastDisplayTS = 0;

			/**
			 * Handles "sensor changed" event.
			 * This is an implementation of the SensorEventListener interface.
			 */
			@Override
			public void onSensorChanged(SensorEvent event) {
				long now = SystemClock.elapsedRealtime();

				long deltaMs = 0;
				if (mLastUpdateTS != 0) {
					deltaMs = now - mLastUpdateTS;
					if (mUpdateTargetMs > 0 && deltaMs < mUpdateTargetMs) {
						// New sample is arriving too fast. Discard it.
						return;
					}
				}

				// Format and post message for the emulator.
				float[] values = event.values;
				final int len = values.length;

				// Computes average update time for this sensor and average globally.
				if (mLastUpdateTS != 0) {
					if (mGlobalAvgUpdateMs != 0) {
						mGlobalAvgUpdateMs = (mGlobalAvgUpdateMs + deltaMs) / 2;
					} else {
						mGlobalAvgUpdateMs = deltaMs;
					}
				}
				mLastUpdateTS = now;

				// Update the UI for the sensor, with a static throttling of 10 fps max.
				if (hasUiHandler()) {
					if (mLastDisplayTS != 0) {
						long uiDeltaMs = now - mLastDisplayTS;
						if (uiDeltaMs < 1000 / 4 /* 4fps in ms */) {
							// Skip this UI update
							return;
						}
					}
					mLastDisplayTS = now;

					mValues[0] = values[0];
					if (len > 1) {
						mValues[1] = values[1];
						if (len > 2) {
							mValues[2] = values[2];
						}
					}
					mValue = null;

					Message msg = Message.obtain();
					msg.what = SENSOR_DISPLAY_MODIFIED;
					msg.obj = MonitoredSensor.this;
					notifyUiHandlers(msg);
				}

				if (DEBUG) {
					long now2 = SystemClock.elapsedRealtime();
					long processingTimeMs = now2 - now;
					Log.d(TAG, String.format("glob %d - local %d > target %d - processing %d -- %s",
							mGlobalAvgUpdateMs, deltaMs, mUpdateTargetMs, processingTimeMs,
							mSensor.getName()));
				}
			}

			/**
			 * Handles "sensor accuracy changed" event.
			 * This is an implementation of the SensorEventListener interface.
			 */
			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) {
			}
		}
	} // MonitoredSensor

	/***************************************************************************
	 * Logging wrappers
	 **************************************************************************/

	private void Loge(String log) {
		Log.e(TAG, log);
	}

	/**
	 * Indicates any UI handler is currently registered with the channel. If no UI
	 * is displaying the channel's state, maybe the channel can skip UI related tasks.
	 *
	 * @return True if there's at least one UI handler registered.
	 */
	public boolean hasUiHandler() {
		return !mUiHandlers.isEmpty();
	}

	/**
	 * Registers a new UI handler.
	 *
	 * @param uiHandler A non-null UI handler to register. Ignored if the UI
	 *            handler is null or already registered.
	 */
	public void addUiHandler(android.os.Handler uiHandler) {
		assert uiHandler != null;
		if (uiHandler != null) {
			if (!mUiHandlers.contains(uiHandler)) {
				mUiHandlers.add(uiHandler);
			}
		}
	}

	/**
	 * Unregisters an UI handler.
	 *
	 * @param uiHandler A non-null UI listener to unregister. Ignored if the
	 *            listener is null or already registered.
	 */
	public void removeUiHandler(android.os.Handler uiHandler) {
		assert uiHandler != null;
		mUiHandlers.remove(uiHandler);
	}

	/**
	 * Protected method to be used by handlers to send an event to all UI
	 * handlers.
	 *
	 * @param event An integer event code with no specific parameters. To be
	 *            defined by the handler itself.
	 */
	protected void notifyUiHandlers(int event) {
		for (android.os.Handler uiHandler : mUiHandlers) {
			uiHandler.sendEmptyMessage(event);
		}
	}

	/**
	 * Protected method to be used by handlers to send an event to all UI
	 * handlers.
	 *
	 * @param msg An event with parameters. To be defined by the handler itself.
	 */
	protected void notifyUiHandlers(Message msg) {
		for (android.os.Handler uiHandler : mUiHandlers) {
			uiHandler.sendMessage(msg);
		}
	}

}
