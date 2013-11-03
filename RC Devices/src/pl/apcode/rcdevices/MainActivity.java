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
import android.view.animation.BounceInterpolator;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity implements OnSeekBarChangeListener, OnCheckedChangeListener, SensorEventListener {

    BluetoothLinkService mService;
    boolean mBound = false;

	
	private SeekBar bar,bar2,throttleBar;
	private TextView angleValueText;
	private TextView lowFilterPassValueText;
	private TextView azimuthValueText;
	private TextView throttleValueText; 
	
	private CheckBox linkWithAzimuthCheckBox;
	private CheckBox reverseCheckBox, breaksCheckBox;
	
	private int angleMin = 0;
	private int angleMax = 180;
	
	private SensorManager mSensorManager;
	private Sensor mCompass;
	
	private int azimuth = 0;
	private int angle = 90;
	private int refAzimuth = 0;
	private int refAngle = 90;
	private volatile float filterSensivity = 0.2f;
	protected float[] accelVals = null;
	

	  

	  
		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_main);			
			
			angleValueText = (TextView)findViewById(R.id.servoValueText);
			azimuthValueText = (TextView)findViewById(R.id.azimuthValueText);
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
			
			linkWithAzimuthCheckBox = (CheckBox)findViewById(R.id.checkBox1);
			linkWithAzimuthCheckBox.setOnCheckedChangeListener(this);
			
			reverseCheckBox = (CheckBox)findViewById(R.id.checkBoxReverse);
			reverseCheckBox.setOnCheckedChangeListener(this);
			
			breaksCheckBox = (CheckBox)findViewById(R.id.checkBoxBreak);
			breaksCheckBox.setOnCheckedChangeListener(this);
			
		    mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		    mCompass = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
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
		angleValueText.setText(Integer.toString(calculateAngle(500)));
		
		mSensorManager.registerListener(this, mCompass, SensorManager.SENSOR_DELAY_GAME);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
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
        double prg=(angleValue-angleMin)*1000.0/(angleMax - angleMin);
    	if(prg<0)
    		prg=0;
    	else if(prg>1000)
    		prg = 1000;
    	
    	return (int) (prg+0.5);
    }
    
	public int calculateAngle(int progValue) {
		return (int)((angleMax - angleMin) * progValue / 1000.0 + angleMin + 0.5);	
	}
	
	public int calculateThrottle(int progValue) {
		return (progValue * 255)/1000;
	}
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		
		if(seekBar.getId() == R.id.seekBar1) {
		angle = calculateAngle(progress);
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
		if(!breaksCheckBox.isChecked()) {
			if(reverseCheckBox.isChecked())
				sendData("r#" + Integer.toString(throttle) + ";");
			else
				sendData("f#" + Integer.toString(throttle) + ";");
		} else
			sendData("b;");
		
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
		
		if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
			accelVals = lowPass( event.values, accelVals );
		
			azimuth = Math.round(accelVals[0]);
			
			azimuthValueText.setText(Integer.toString(azimuth));
			if(linkWithAzimuthCheckBox.isChecked()) {
				int newAngle = azimuth - refAzimuth + refAngle;
				
				if(newAngle < 0)
					newAngle = newAngle + 360;
				else if(newAngle > 360)
					newAngle = newAngle - 360;
				
				if(newAngle > 270)
					newAngle = 0;
				else if(newAngle > 180)
					newAngle = 180;
				
				bar.setProgress(calculateProg(newAngle));
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
		
		if(buttonView.getId() == R.id.checkBox1) {
			if(isChecked) {
				refAngle = angle;
				refAzimuth = azimuth;
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

}
