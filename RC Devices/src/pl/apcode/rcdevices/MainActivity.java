package pl.apcode.rcdevices;

import  pl.apcode.rcdevices.R;
import pl.apcode.rcdevices.communication.BluetoothLinkService;
import pl.apcode.rcdevices.communication.BluetoothLinkService.LocalBinder;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
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

	
	private SeekBar servoAngleBar,lowPassFilterBar,throttleBar;
	private TextView angleValueText;
	private TextView lowFilterPassValueText;
	private TextView throttleValueText; 
	private TextView xRot, yRot, zRot;
	
	private Button breaksButon, setRotRef; 
	
	private int angleMin = 0;
	private int angleMax = 180;
	private int angleCenter = 90;
	private int throttleDeadZone = 20;
	
	private SensorManager mSensorManager;
    private Sensor mAccelerometer;

    private boolean mInitAccelerometer = true;
    private boolean mDriving = false;
    private double[] mAccVector = new double[3]; //gravity
    private double[] mAccAngle = new double[3];
    private double[] mAccAngleRef = new double[3];
    private double[] mAccAngleFiltered = new double[3];

	private volatile float filterSensivity = 0.15f;


	  
		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_main);			
			
			angleValueText = (TextView)findViewById(R.id.servoValueText);
			lowFilterPassValueText = (TextView)findViewById(R.id.lowPassFilterValueText);
			lowFilterPassValueText.setText(String.format("%.2f", filterSensivity ));
			throttleValueText = (TextView)findViewById(R.id.textViewThrottleValue);
			
			servoAngleBar = (SeekBar)findViewById(R.id.servoAngleBar);
			servoAngleBar.setOnSeekBarChangeListener(this);
			
			lowPassFilterBar = (SeekBar)findViewById(R.id.lowPassFilterBar);
			lowPassFilterBar.setOnSeekBarChangeListener(this);
			lowPassFilterBar.setProgress(  (int)(filterSensivity * 100) );
			
			throttleBar = (SeekBar)findViewById(R.id.throttleBar);
			throttleBar.setOnSeekBarChangeListener(this);
			
			
			breaksButon = (Button)findViewById(R.id.breaksButton);
			breaksButon.setOnTouchListener(this);
			
			setRotRef = (Button)findViewById(R.id.driveButton);
			setRotRef.setOnTouchListener(this);
						
		    mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
	        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
	        
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
		throttleDeadZone = Integer.parseInt(sharedPrefs.getString("throttle-dead-zone", "20"));
		
		angleCenter = calculateServoAngle(500);
		angleValueText.setText(Integer.toString(angleCenter));
		
		mInitAccelerometer = true;
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
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
	
    public int calculateServoProg(int  angleValue) {
        int prg=Math.round((angleValue-angleMin)*1000.0f/(angleMax - angleMin));
    	if(prg<0)
    		prg=0;
    	else if(prg>1000)
    		prg = 1000;
    	
    	return prg;
    }
    
	public int calculateServoAngle(int progValue) {
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
	
	public void updateMotor(int throttle) {
		
		throttleValueText.setText(Integer.toString(throttle));

		if(throttle<-1*throttleDeadZone)
			sendData("r#" + Integer.toString(Math.abs(throttle)) + ";");
		else if (throttle > throttleDeadZone)
			sendData("f#" + Integer.toString(throttle) + ";");
		else
			sendData("b;");
		
	}
	
	public void updateServo(int angle) {
		angleValueText.setText(Integer.toString(angle));
		sendData("s#" + Integer.toString(angle) + ";");	
	}
	
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		
		if(!mDriving) {
			if(seekBar.getId() == R.id.servoAngleBar)
				updateServo( calculateServoAngle(progress) );
			else if( seekBar.getId() == R.id.throttleBar)	
				updateMotor( calculateThrottle(progress) );	
		}
		
		if(seekBar.getId() == R.id.lowPassFilterBar) {
			filterSensivity = progress / 100.0f;
			lowFilterPassValueText.setText(String.format("%.2f", filterSensivity ));	
		}
			
		
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
		
		if(event.sensor == mAccelerometer) {
			float[] g = event.values;
			double g_len = Math.sqrt(g[0]*g[0] + g[1]*g[1] + g[2]*g[2]);
			
			mAccVector[0] = g[0]/g_len;
			mAccVector[1] = g[1]/g_len;
			mAccVector[2] = g[2]/g_len;
			
			calculateAngle(mAccVector, mAccAngle);
			
			if(mInitAccelerometer) {
				mInitAccelerometer = false;
				System.arraycopy(mAccAngle, 0, mAccAngleRef, 0, mAccAngle.length);
				System.arraycopy(mAccAngle, 0, mAccAngleFiltered, 0, mAccAngle.length);
			} else {
				lowPass(mAccAngle, mAccAngleFiltered);
			}
			
			double x = mAccAngleFiltered[0] - mAccAngleRef[0];
			double y = mAccAngleFiltered[1] - mAccAngleRef[1];
			double z = mAccAngleFiltered[2] - mAccAngleRef[2];
			
        	xRot.setText(String.format("%3.0f", x));
        	yRot.setText(String.format("%3.0f", y));
        	zRot.setText(String.format("%3.0f", z));
        	
        	int angle = angleCenter + (int)Math.round(x*1.5);
        	int throttle = (int)Math.round(y*10);
        	
        	if(mDriving) {
        		updateMotor(throttle);
        		updateServo(angle);
        		
        		throttleBar.setProgress(calculateThrottleProgres(throttle));
        		servoAngleBar.setProgress(calculateServoProg(angle));
        	}
			
		}
		
	}


	protected double[] lowPass( double[] input, double[] output ) {
	    
		if ( output == null ) {
			output = input.clone();
	    	return output;
	    	
		}

	    for ( int i=0; i<input.length; i++ )
	        output[i] = output[i] + filterSensivity * (input[i] - output[i]);        
	   
	    return output;
	}

	
	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

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

	private void calculateAngle(double[] normVector, double[] outAngle) {
		outAngle[0] = Math.toDegrees(Math.acos(normVector[0]));
		outAngle[1] = Math.toDegrees(Math.acos(normVector[1]));
		outAngle[2] = Math.toDegrees(Math.acos(normVector[2]));
	}
	
	private void breakVehicle() {
		mDriving=false;
		sendData("b;");
		throttleBar.setProgress(500);
		servoAngleBar.setProgress(500);
	}
	
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		
		if(v.getId() == R.id.breaksButton) {
			if(event.getAction() == MotionEvent.ACTION_DOWN)
				breakVehicle();
		} else if(v.getId() == R.id.driveButton) {
			if(event.getAction() == MotionEvent.ACTION_DOWN) {
				if(!mDriving) {
					mInitAccelerometer = true;
					mDriving = true;
				}
			}
			else if(event.getAction() == MotionEvent.ACTION_UP)
				breakVehicle();
			
		}
		return false;
	}

}
