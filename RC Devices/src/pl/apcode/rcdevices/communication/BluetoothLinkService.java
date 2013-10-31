package pl.apcode.rcdevices.communication;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ObjectOutputStream.PutField;
import java.lang.reflect.Method;
import java.util.UUID;

import pl.apcode.rcdevices.EventLogger;
import pl.apcode.rcdevices.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;


public class BluetoothLinkService extends Service {

	public static final String tag = BluetoothLinkService.class.getSimpleName();
	private static final UUID SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private static final int NotificationID = 2001;
	
	SharedPreferences sharedPrefs;
	
	private NotificationManager mNotificationManager;
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private Resources mRes;
	
	private BluetoothAdapter btAdapter = null;		
	private BluetoothSocket btSocket = null;
	private String deviceAdderes = "";
	private OutputStream outStream = null;
	

	public static class MsgType 
	{ 
		public static final int CONNECT = 110;
		public static final int SEND = 130;
		public static final int RECEIVE = 140;
		public static final int INFO = 150;
	}; 
	

	@Override
	public void onCreate() {
		Log.d(tag, "onCreate");
		
		HandlerThread thread = new HandlerThread("BluetoothLinkService",
	            Process.THREAD_PRIORITY_DEFAULT);
	    thread.start();
	    
	    mServiceLooper = thread.getLooper();
	    mServiceHandler = new ServiceHandler(mServiceLooper);
	    
	    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
	    deviceAdderes = sharedPrefs.getString("bt-address", "");
	    
	    
	    mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	    startForeground(
	    		NotificationID, 
	    		getForegroundNotification(
	    				R.drawable.tx_notification_disconnected, 
	    				null, 
	    				R.string.btlink_connecting)); 
	    
	    
		super.onCreate();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(tag, "onStartCommand");
		Message msg= mServiceHandler.obtainMessage(MsgType.CONNECT);
		msg.sendToTarget();
		return START_STICKY;
	}
	
	@Override
	public IBinder onBind(Intent arg0) {
		
		Log.d(tag, "onBind");
		return null;
	}
	
	
	@Override
	public void onDestroy() {
		Log.d(tag, "onDestroy");
		mServiceHandler.quit();
		disconnect();
		stopForeground(true);
		super.onDestroy();
	}
	
	
	private final class ServiceHandler extends Handler {
		
		private boolean terminating = false;
	    
		
		 public ServiceHandler(Looper looper) {
		      super(looper);
		      mRes = getResources(); 
		  }
		  
		  
		  private void handleConnect() {
		  		EventLogger.d(tag, "Handle CONNECT");
		  		removeMessages(MsgType.CONNECT);
		  		
				if(initBluetooth()) {
					if(connect()) {
						serviceNotify(
		    				R.drawable.tx_notification_connected, 
		    				null, 
		    				R.string.btlink_connected);
					} 
					else
					{
						serviceNotify(
		    				R.drawable.tx_notification_disconnected, 
		    				null, 
		    				R.string.btlink_unabletoconnect);
						
						reconnectDelayed();
					}
				}
		  }
		  
		  
		  private void reconnectDelayed() {
			  postDelayed(new Runnable() {
				public void run() {
					EventLogger.i(tag, "Reconnecting...");
					handleConnect();
				}
			}, 1000);
		  }
		  
		  public void quit() {
			 synchronized(this) { 	
				 terminating = true;
				 getLooper().quit();
			 }
		  }
		  
		private void serviceNotify(int iconID, Integer tickerTextID, int contentTextID) {
			synchronized (this) {
				if(!terminating) {
				mNotificationManager.notify(
			    		NotificationID, 
			    		getForegroundNotification(
			    				iconID, 
			    				tickerTextID, 
			    				contentTextID));
				}
			}
		}
		  
		  @Override
		  public void handleMessage(Message msg) {
		
			  switch(msg.what) {
			  	case MsgType.CONNECT:
			  		handleConnect();
			  		break;
			  	case MsgType.SEND:
			  		EventLogger.d(tag, "Handle SEND");
			  		break;
			  	case MsgType.RECEIVE:
			  		EventLogger.d(tag, "Handle RECEIVE");
			  		break;
			  	case MsgType.INFO:
			  		EventLogger.d(tag, "Handle INFO. " + String.valueOf(msg.obj) );
			  		break;
			  	default:
			  		EventLogger.w(tag, "Undefined handler for message: " + msg.what);
			  }
			  
		  }
	}
	
	private boolean _isConnected = false;
	public boolean IsConnected() {
		
		return _isConnected;
	}
	
	
	private boolean initBluetooth() {
		boolean result = false;
		
		EventLogger.d(tag, "initBluetooth");
		
		if(btAdapter == null)
			btAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if(btAdapter.isEnabled()) {
			EventLogger.d(tag,"Bluetooth is enabled");
			result = true;
		}
		else {
			EventLogger.d(tag,"Bluetooth is disabled. Requesting enable...");
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(enableBtIntent);
		}
		return result;
	}
	
	
	private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
	      if(Build.VERSION.SDK_INT >= 10){
	          try {
	              final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
	          return (BluetoothSocket) m.invoke(device, SSP_UUID);
	          } catch (Exception e) {
	        	  EventLogger.e(tag, "Could not create Insecure RFComm Connection. Fallback to secure RF Cnnection...",e);
	          }
	      }
	      return  device.createRfcommSocketToServiceRecord(SSP_UUID);
	}
	
	

	  private boolean connect()
	  {		    
		  disconnect();
		  try
		  {
		    // Set up a pointer to the remote node using it's address.
		    BluetoothDevice device = btAdapter.getRemoteDevice(deviceAdderes);
		    
		    // Two things are needed to make a connection:
		    //   A MAC address, which we got above.
		    //   A Service ID or UUID.  In this case we are using the
		    //     UUID for SPP.
		    
		    try {
		        btSocket = createBluetoothSocket(device);
		    } catch (IOException e1) {
		        EventLogger.e(tag, "Socket create failed!", e1 );
		        return false;
		    }
		        
		    // Discovery is resource intensive.  Make sure it isn't going on
		    // when you attempt to connect and pass your message.
		    btAdapter.cancelDiscovery();
		    
		    // Establish the connection.  This will block until it connects.
		    try {
		      btSocket.connect();
		      EventLogger.i(tag, "Socket connection OK.");
		    } catch (IOException e) {
		      try {
		    	  EventLogger.e(tag, "Socket connection FAIL!", e);
		    	  btSocket.close();
		        
		      } catch (IOException e2) {
		    	  EventLogger.e(tag, "Socket close FAIL!", e2);
		    	  return false;
		      }
		      return false;
		    }
		      
		    // Create a data stream so we can talk to server.		  
		    try {
		      outStream = btSocket.getOutputStream();
		      
		    } catch (IOException e) {
		    	EventLogger.e("Fatal Error", "In onResume() and output stream creation failed:" + e.getMessage() + ".");
		    }	
		  } catch(Exception gex) {
		    	EventLogger.e(tag, "Unable to connect device.", gex);
		    	return false;
		    }
		  
		  _isConnected = true;
		  return true;
	  }
	  
	  
	  private void disconnect() {
		  if(_isConnected) {
	    	EventLogger.i(tag, "Disconnecting");
		  
			_isConnected = false;	    
			  
			if (outStream != null) {
			  try {
			    outStream.flush();
			  } catch (IOException e) {
				  EventLogger.e(tag, "Failed to flush output stream", e);
			  }
			}
	
			if(btSocket != null) {
			    try     {
			      btSocket.close();
			    } catch (IOException e2) {
			      EventLogger.e(tag, "Failed to close socket." + e2.getMessage() + ".");
			    }
			}
		  }
	  }
	  
	
	Notification getForegroundNotification(int iconID, Integer tickerTextID, int contentTextID) {
		
		Intent toLaunch = new Intent(this, pl.apcode.rcdevices.MainActivity.class);
		
        toLaunch.setAction("android.intent.action.MAIN");
        toLaunch.addCategory("android.intent.category.LAUNCHER");        
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PendingIntent pi = PendingIntent.getActivity(this, 0,
        		toLaunch,  PendingIntent.FLAG_UPDATE_CURRENT);
                

        String tickerText;
        
        if(tickerTextID == null )
        	tickerText = null;
        else
        	tickerText=mRes.getString(tickerTextID);
        
        Notification noti = new Notification(
        		iconID,
        		tickerText, System.currentTimeMillis());
        
        noti.setLatestEventInfo(this, mRes.getText(R.string.app_name) , mRes.getText(contentTextID), pi);

        
        noti.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONLY_ALERT_ONCE;
        
        
        return noti;
	}
	

}
