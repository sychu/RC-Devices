package pl.apcode.rcdevices.communication;

import java.util.UUID;

import pl.apcode.rcdevices.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;


public class BluetoothLinkService extends Service {

	public static final String tag = BluetoothLinkService.class.getSimpleName();
	
	private static final int NotificationID = 2001;
	private NotificationManager mNotificationManager;
			
	private static final UUID SSP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	public static class MsgType 
	{ 
		public static final int CONNECT = 101; 
		public static final int SEND = 102;
		public static final int RECEIVE = 103;
		public static final int INFO = 104;
	}; 
	
	
	
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private Resources mRes;
	private BluetoothAdapter btAdapter = null;
	
	private boolean initBluetooth() {
		boolean result = false;
		
		Log.d(tag, "initBluetooth");
		
		if(btAdapter == null)
			btAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if(btAdapter.isEnabled()) {
			Log.d(tag,"Bluetooth is enabled");
			result = true;
			
			mNotificationManager.notify(
	    		NotificationID, 
	    		getForegroundNotification(
	    				R.drawable.tx_notification_connected, 
	    				null, 
	    				R.string.btlink_connected)); 
		}
		else {
			Log.d(tag,"Bluetooth is disabled. Requesting enable...");
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(enableBtIntent);
			result = false;
		}
		return result;
	}
	
	
	private final class ServiceHandler extends Handler {
	  public ServiceHandler(Looper looper) {
	      super(looper);
	      mRes = getResources(); 
	  }
	  
	  
	  
	  @Override
	  public void handleMessage(Message msg) {
	
		  switch(msg.what) {
		  	case MsgType.CONNECT:
		  		Log.d(tag, "Handle CONNECT");
		  		initBluetooth();
		  		break;
		  	case MsgType.SEND:
		  		Log.d(tag, "Handle SEND");
		  		break;
		  	case MsgType.RECEIVE:
		  		Log.d(tag, "Handle RECEIVE");
		  		break;
		  	case MsgType.INFO:
		  		Log.d(tag, "Handle INFO");
		  		break;
		  	default:
		  		Log.w(tag, "Undefined handler for message: " + msg.what);
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
	
	
	@Override
	public void onCreate() {
		Log.d(tag, "onCreate");
		
		HandlerThread thread = new HandlerThread("ServiceStartArguments",
	            Process.THREAD_PRIORITY_DEFAULT);
	    thread.start();
	    
	    mServiceLooper = thread.getLooper();
	    mServiceHandler = new ServiceHandler(mServiceLooper);
	    
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
		stopForeground(true);
		mServiceLooper.quit();
		super.onDestroy();
	}

}
