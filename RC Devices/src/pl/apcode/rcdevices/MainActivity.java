package pl.apcode.rcdevices;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;


import  pl.apcode.rcdevices.R;
import android.R.string;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity implements OnSeekBarChangeListener, OnCheckedChangeListener, SensorEventListener {

	private SeekBar bar,bar2,throttleBar;
	private TextView angleValueText;
	private TextView lowFilterPassValueText;
	private TextView azimuthValueText;
	private TextView throttleValueText; 
	
	private CheckBox linkWithAzimuthCheckBox;
	private CheckBox reverseCheckBox, breaksCheckBox;
	
	
	private String DEVICE_ADDRESS =  "";
	private int angleMin = 0;
	private int angleMax = 180;
	
	  // SPP UUID service
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final String TAG = "bluetooth1";
	
	  private BluetoothAdapter btAdapter = null;
	  private BluetoothSocket btSocket = null;
	  private OutputStream outStream = null;
	
	private SensorManager mSensorManager;
	private Sensor mCompass;
	
	private int azimuth = 0;
	private int angle = 90;
	private int refAzimuth = 0;
	private int refAngle = 90;
	private volatile float filterSensivity = 0.2f;
	protected float[] accelVals = null;
	
	private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
	      if(Build.VERSION.SDK_INT >= 10){
	          try {
	              final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
	          return (BluetoothSocket) m.invoke(device, MY_UUID);
	          } catch (Exception e) {
	        	  Log.e(TAG, "Could not create Insecure RFComm Connection",e);
	          }
	      }
	      return  device.createRfcommSocketToServiceRecord(MY_UUID);
	}
	  
	

	
	  private void checkBTState() {
	    // Check for Bluetooth support and then check to make sure it is turned on
	    // Emulator doesn't support Bluetooth and will return null
	    if(btAdapter==null) {
	      errorExit("Fatal Error", "Bluetooth not support");
	    } else {
	      if (btAdapter.isEnabled()) {
	        Log.d(TAG, "...Bluetooth ON...");
	      } else {
	        //Prompt user to turn on Bluetooth
	        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	        startActivityForResult(enableBtIntent, 1);
	      }
	    }
	  }
	  
	  private void errorExit(String title, String message){
	    Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
	    finish();
	  }
	  
	  private void sendData(String message) {
		  
		if(!isConnected() )
			return;
		
	    byte[] msgBuffer = message.getBytes();
	  
	    Log.d(TAG, "...Send data: " + message + "...");
	  
	    try {
	      outStream.write(msgBuffer);
	    } catch (IOException e) {
	      String msg = "In onResume() and an exception occurred during write: " + e.getMessage();
	      if (DEVICE_ADDRESS.equals("00:00:00:00:00:00"))
	        msg = msg + ".\n\nUpdate your server address from 00:00:00:00:00:00 to the correct address on line 35 in the java code";
	        msg = msg +  ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";
	        
	        errorExit("Fatal Error", msg);      
	    }
	  }

	  private boolean _isConnected = false;
	  
	  private boolean isConnected() {
		  return _isConnected;
	  }
	  
	  private void Connect()
	  {		    
		    // Set up a pointer to the remote node using it's address.
		    BluetoothDevice device = btAdapter.getRemoteDevice(DEVICE_ADDRESS);
		    
		    // Two things are needed to make a connection:
		    //   A MAC address, which we got above.
		    //   A Service ID or UUID.  In this case we are using the
		    //     UUID for SPP.
		    
		    try {
		        btSocket = createBluetoothSocket(device);
		    } catch (IOException e1) {
		        errorExit("Fatal Error", "In onResume() and socket create failed: " + e1.getMessage() + ".");
		    }
		        
		    // Discovery is resource intensive.  Make sure it isn't going on
		    // when you attempt to connect and pass your message.
		    btAdapter.cancelDiscovery();
		    
		    // Establish the connection.  This will block until it connects.
		    Log.d(TAG, "...Connecting...");
		    try {
		      btSocket.connect();
		      Log.d(TAG, "...Connection ok...");
		    } catch (IOException e) {
		      try {
		        btSocket.close();
		      } catch (IOException e2) {
		        errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
		      }
		    }
		      
		    // Create a data stream so we can talk to server.
		    Log.d(TAG, "...Create Socket...");
		  
		    try {
		      outStream = btSocket.getOutputStream();
		      _isConnected = true;
		    } catch (IOException e) {
		      errorExit("Fatal Error", "In onResume() and output stream creation failed:" + e.getMessage() + ".");
		    }	
		    
	  }
	  
	  
	  private void Disconnect() {
		  _isConnected = false;
		    Log.d(TAG, "...Disconnecting...");
		    
		    if (outStream != null) {
		      try {
		        outStream.flush();
		      } catch (IOException e) {
		        errorExit("Fatal Error", "In onPause() and failed to flush output stream: " + e.getMessage() + ".");
		      }
		    }
		  
		    try     {
		      btSocket.close();
		    } catch (IOException e2) {
		      errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
		    }
	  }
	  
		@Override
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.activity_main);
			
		    btAdapter = BluetoothAdapter.getDefaultAdapter();
		    checkBTState();
			
			
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
	protected void onStart() {
		// TODO Auto-generated method stub
		super.onStart();
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		DEVICE_ADDRESS = sharedPrefs.getString("bt-address", "");
		angleMin = Integer.parseInt(sharedPrefs.getString("servo-min", "0"));
		angleMax = Integer.parseInt(sharedPrefs.getString("servo-max", "180"));
		
		angleValueText.setText(Integer.toString(calculateAngle(500)));
		
		if(DEVICE_ADDRESS != "")
			Connect();
		mSensorManager.registerListener(this, mCompass, SensorManager.SENSOR_DELAY_GAME);
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		mSensorManager.unregisterListener(this);
		
		if(DEVICE_ADDRESS != "")
			Disconnect();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


	
	public int calculateAngle(int progValue) {
		return (angleMax - angleMin) * progValue / 1000 + angleMin;	
	}
	
	public int calculateThrottle(int progValue) {
		return (progValue * 255)/1000;
	}
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		
		if(seekBar.getId() == R.id.seekBar1) {
		angle = calculateAngle(progress);;
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
		// TODO Auto-generated method stub
		
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
				
				bar.setProgress(newAngle);
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
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

}
