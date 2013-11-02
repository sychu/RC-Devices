package pl.apcode.rcdevices.communication;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.UUID;

import pl.apcode.rcdevices.EventLogger;
import pl.apcode.rcdevices.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;



public class BluetoothLinkServiceHandler extends Handler {
	public enum HandlerMethod {InitConnection, StartConnection};
	
	public static final String tag = BluetoothLinkServiceHandler.class.getSimpleName();
	
	private static final UUID SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	public static final int NotificationID = 2001;
	private static final Object lock = new Object();	
	private static final int BTConnectDelay = 200; 
	private static final int BTRetryDelay = 1000; 
	
	private Context ctx;
	private SharedPreferences sharedPrefs;
	
	private NotificationManager mNotificationManager;
	private Resources mRes;
	
	private BluetoothAdapter btAdapter = null;		
	private BluetoothSocket btSocket = null;
	private String deviceAdderes = "";
	private OutputStream outStream = null;
	
	private boolean terminating = false;	
	private boolean isConnected = false;
	
	public static class MsgType 
	{ 
		public static final int CONNECT = 110;
		public static final int SEND = 130;
		public static final int RECEIVE = 140;
		public static final int INFO = 150;
	};
	  	
     
    
	 public BluetoothLinkServiceHandler(Looper looper, Context ctx) {
	      super(looper);
	      this.ctx = ctx;
	      mRes = ctx.getResources(); 
		  sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		  mNotificationManager = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
		  deviceAdderes = sharedPrefs.getString("bt-address", "");
	  }
	 
	 
	  @Override
	  public void handleMessage(Message msg) {
	
		  switch(msg.what) {
		  	case MsgType.CONNECT:
		  		initConnection();
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
	  
	  
	  public void initConnection() {
		  synchronized(lock) {		
			  	EventLogger.d(tag, "Handle CONNECT");
			  	//TODO for live control RC toy it is ok to remove old messages with connection... i think.
			  	cleanUpQueue();
				if(initBluetooth()) {
					if(!connect())
						postDelayed(new Runnable() {
							@Override
							public void run() {
								initConnection();
							}
						}  , BTRetryDelay);
				}
		  }
	  }
	  
	  
	  
	  private synchronized void cleanUpQueue() {
		  if(!terminating)
			  removeCallbacksAndMessages(null);
	  }
	  
	  
	  private void handleQuit() {
		  synchronized(lock) {
			  disconnect();
			  getLooper().quit();
			  EventLogger.i(tag, "Looper quit");
		  }
	  }
	  
	  
	 public synchronized void quitGently() {
			 terminating = true;
			 postAtFrontOfQueue(new Runnable() {			
				@Override
				public void run() {
					handleQuit();
				}
			});
	  }
	 
	 private void onServiceONLine() {
		 	isConnected = true;
			serviceNotify(
    				R.drawable.ic_stat_notify_btconnected, 
    				null, 
    				R.string.btlink_connected);
	 }
	 
	 private void onServiceOFFline() {
	    	isConnected = false;
			serviceNotify(
    				R.drawable.ic_stat_notify_btdisconnected, 
    				null, 
    				R.string.btlink_unabletoconnect);
	 }
	  
	 private synchronized void serviceNotify(int iconID, Integer tickerTextID, int contentTextID) {
		if(!terminating) {
		mNotificationManager.notify(
	    		NotificationID, 
	    		createNotification(
	    				iconID, 
	    				tickerTextID, 
	    				contentTextID));
		}
	 }
		
	public Notification createNotification(int iconID, Integer tickerTextID, int contentTextID) {
		Intent toLaunch = new Intent(ctx, pl.apcode.rcdevices.MainActivity.class);
		
        toLaunch.setAction("android.intent.action.MAIN");
        toLaunch.addCategory("android.intent.category.LAUNCHER");        
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        toLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        PendingIntent pi = PendingIntent.getActivity(ctx, 0,
        		toLaunch,  PendingIntent.FLAG_UPDATE_CURRENT);
                

        String tickerText;
        
        if(tickerTextID == null )
        	tickerText = null;
        else
        	tickerText=mRes.getString(tickerTextID);
        
        Notification noti = new Notification(
        		iconID,
        		tickerText, System.currentTimeMillis());
        
        noti.setLatestEventInfo(ctx, mRes.getText(R.string.app_name) , mRes.getText(contentTextID), pi);

        
        noti.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONLY_ALERT_ONCE;
        
        
        return noti;
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
			ctx.startActivity(enableBtIntent);
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
		  try
		  { 
			
			if(btSocket != null || outStream != null) {
				disconnect();
				Thread.sleep(BTConnectDelay);
			}
			
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
		    //btAdapter.cancelDiscovery();
		    
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
		    
		    Thread.sleep(BTConnectDelay);
		    
		  } catch(Exception gex) {
		    	EventLogger.e(tag, "Unable to connect device.", gex);
		    	return false;
		    }
		  
		  
		  onServiceONLine();
		  return true;
	  }
	  
	  
	  private void disconnect() {
	    	
		  if(outStream == null && btSocket == null)
			  return;
		  
		    onServiceOFFline();
		  
			if (outStream != null) {
			  try {
				  EventLogger.i(tag, "Disconnecting");
				  outStream.flush();
			  } catch (IOException e) {
				  EventLogger.e(tag, "Failed to flush output stream", e);
			  }
			  outStream = null;
			}
	
			if(btSocket != null) {
			    try     {
			      btSocket.close();
			    } catch (IOException e2) {
			      EventLogger.e(tag, "Failed to close socket." + e2.getMessage() + ".");
			    }
			    btSocket = null;
			}
			
	  }
		
	  

}