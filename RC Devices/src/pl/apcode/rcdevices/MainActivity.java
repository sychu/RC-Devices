package pl.apcode.rcdevices;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;


import  pl.apcode.rcdevices.R;
import pl.apcode.rcdevices.communication.BluetoothLinkService;
import pl.apcode.rcdevices.communication.BluetoothLinkService.LocalBinder;
import android.R.string;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.BounceInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity implements OnSeekBarChangeListener, OnCheckedChangeListener, SensorEventListener, OnTouchListener {

    BluetoothLinkService mService;
    boolean mBound = false;

	
	private SeekBar bar,bar2,throttleBar;
	private TextView angleValueText;
	private TextView lowFilterPassValueText;
	private TextView throttleValueText; 
	private TextView xRot, yRot, zRot;
	
	private CheckBox useSensorsCheckBox;
	private Button breaksButon, setRotRef; 
	
	private int angleMin = 0;
	private int angleMax = 180;
	private int angleCenter = 90;
	
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mMagnetometer;

    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;
    
    private float[] mR = new float[9];
    private float[] mRef = null;
    private float[] mAngleChange = new float[3];
    private float[] mAngleChangeFiltered = null;

	private volatile float filterSensivity = 0.1f;
	protected float[] accelVals = null;

	  
		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_main);			
			
			angleValueText = (TextView)findViewById(R.id.servoValueText);
			lowFilterPassValueText = (TextView)findViewById(R.id.lowPassFilterValueText);
			lowFilterPassValueText.setText(String.format("%.2f", filterSensivity ));
			throttleValueText = (TextView)findViewById(R.id.textViewThrottleValue);
			
			bar = (SeekBar)findViewById(R.id.seekBar1);
			bar.setOnSeekBarChangeListener(this);
			
			bar2 = (SeekBar)findViewById(R.id.seekBar2);
			bar2.setOnSeekBarChangeListener(this);
			bar2.setProgress(  (int)(filterSensivity * 100) );
			
			throttleBar = (SeekBar)findViewById(R.id.seekBarMotor);
			throttleBar.setOnSeekBarChangeListener(this);
			
			useSensorsCheckBox = (CheckBox)findViewById(R.id.useSensorsCheckBox);
			useSensorsCheckBox.setOnCheckedChangeListener(this);
			
			breaksButon = (Button)findViewById(R.id.breaksButton);
			breaksButon.setOnTouchListener(this);
			
			setRotRef = (Button)findViewById(R.id.setRotRefButton);
			setRotRef.setOnTouchListener(this);
						
		    mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
	        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
	        
	        xRot = (TextView)findViewById(R.id.xTextView);
	        yRot = (TextView)findViewById(R.id.yTextView);
	        zRot = (TextView)findViewById(R.id.zTextView);
		}

		@Override
		protected void onActivityResult(int requestCode, int resultCode, Intent data) {
			// TODO Auto-generated method stub
			super.onActivityResult(requestCode, resultCode, data);
		}
		
	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		angleMin = Integer.parseInt(sharedPrefs.getString("servo-min", "0"));
		angleMax = Integer.parseInt(sharedPrefs.getString("servo-max", "180"));	
		angleCenter = calculateAngle(500);
		angleValueText.setText(Integer.toString(angleCenter));
		
        mLastAccelerometerSet = false;
        mLastMagnetometerSet = false;
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);

	}
	
	@Override
	protected void onStop() {
		super.onStop();
        mSensorManager.unregisterListener(this);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };


    public void sendData(String data) {
    	if(mBound)
    		mService.sendData(data);
    }
	
    public int calculateProg(int  angleValue) {
        int prg=Math.round((angleValue-angleMin)*1000.0f/(angleMax - angleMin));
    	if(prg<0)
    		prg=0;
    	else if(prg>1000)
    		prg = 1000;
    	
    	return prg;
    }
    
	public int calculateAngle(int progValue) {
		return Math.round((angleMax - angleMin) * progValue / 1000.0f + angleMin);	
	}
	
	public int calculateThrottle(int progValue) {
		return Math.round((progValue-500) * 255*2/1000.0f);
	}
	
	public int calculateThrottleProgres(int thr) {
		int prg =  Math.round(thr*1000/(255*2.0f) + 500);
    	if(prg<0)
    		prg=0;
    	else if(prg>1000)
    		prg = 1000;
    	
    	return prg;
	}
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		
		if(seekBar.getId() == R.id.seekBar1) {
		int angle = calculateAngle(progress);
		angleValueText.setText(Integer.toString(angle));
		sendData("s#" + Integer.toString(angle) + ";");
		}
		else if(seekBar.getId() == R.id.seekBar2) {
			
			filterSensivity = progress / 100.0f;
			lowFilterPassValueText.setText(String.format("%.2f", filterSensivity ));
			
			
		}else if( seekBar.getId() == R.id.seekBarMotor) {
			updateMotor();
		}
			
		
	}
	
	
	public void updateMotor() {
		int throttle = calculateThrottle(throttleBar.getProgress());
		throttleValueText.setText(Integer.toString(throttle));

		if(throttle<0)
			sendData("r#" + Integer.toString(Math.abs(throttle)) + ";");
		else
			sendData("f#" + Integer.toString(throttle) + ";");
		
	}
	

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		
        if (event.sensor == mAccelerometer) {
            System.arraycopy(event.values, 0, mLastAccelerometer, 0, event.values.length);
            mLastAccelerometerSet = true;
        } else if (event.sensor == mMagnetometer) {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0, event.values.length);
            mLastMagnetometerSet = true;
        }
        if (mLastAccelerometerSet && mLastMagnetometerSet) {
            if(SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnetometer)) {
            	if(mRef == null)
            		mRef = mR.clone();
            	
            	SensorManager.getAngleChange(mAngleChange, mR, mRef);
            	mAngleChangeFiltered = lowPass(mAngleChange, mAngleChangeFiltered);
            	//z,x,y
            	double x,y,z;
            	x = Math.toDegrees(mAngleChangeFiltered[1]);
            	y = Math.toDegrees(mAngleChangeFiltered[2]);
            	z = Math.toDegrees(mAngleChangeFiltered[0]);
            	
            	xRot.setText(String.format("%.1f", x) );
            	yRot.setText(String.format("%.1f", y) );
            	zRot.setText(String.format("%.1f", z));
            	
            	if(useSensorsCheckBox.isChecked()) {
            		bar.setProgress(calculateProg(angleCenter + (int)Math.round(y*1.5) ));
            		throttleBar.setProgress(calculateThrottleProgres((int)Math.round(x*11)));
            	}
            }
        }
	}


	protected float[] lowPass( float[] input, float[] output ) {
	    
		if ( output == null ) 
		{
			output = input.clone();
	    	return output;
	    	
		}

	    
	    
	    for ( int i=0; i<input.length; i++ ) {
	        output[i] = output[i] + filterSensivity * (input[i] - output[i]);        
	    }
	    

	    return output;
	}

	
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		// TODO Auto-generated method stub
		
		if(buttonView.getId() == R.id.useSensorsCheckBox) {
			if(isChecked) {
				System.arraycopy(mR, 0, mRef, 0, mR.length);
				mAngleChangeFiltered = null;
			}
		} else {
			updateMotor();
		}
		
	}
	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.action_settings:
	    	Intent configActivity = new Intent(this, Settings.class);
	    	startActivity(configActivity);
	        return true;
	    case R.id.connect:
	    	Intent istart = new Intent(this, BluetoothLinkService.class);
	    	startService(istart);
	    	bindService(istart, mConnection, BIND_AUTO_CREATE);
	    	return true;
	    case R.id.disconnect:
	    	Intent istop = new Intent(this, BluetoothLinkService.class);
	    	stopService(istop);
	    	if(mBound) {
	    		unbindService(mConnection);
	    		mBound = false;
	    	}
	    	return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		
		if(v.getId() == R.id.breaksButton) {
			if(event.getAction() == MotionEvent.ACTION_DOWN) {
				useSensorsCheckBox.setChecked(false);
				sendData("b;");
				throttleBar.setProgress(500);
				bar.setProgress(500);
				return false;
			}
		} else if(v.getId() == R.id.setRotRefButton) {
			
			System.arraycopy(mR, 0, mRef, 0, mR.length);
		}
		return false;
	}

}
