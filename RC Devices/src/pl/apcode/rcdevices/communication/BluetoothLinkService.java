package pl.apcode.rcdevices.communication;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;


public class BluetoothLinkService extends Service {

	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	
	
	private final class ServiceHandler extends Handler {
	  public ServiceHandler(Looper looper) {
	      super(looper);
	  }
	  
	  @Override
	  public void handleMessage(Message msg) {
	
		  
	      long endTime = System.currentTimeMillis() + 5*1000;
	      while (System.currentTimeMillis() < endTime) {
	          synchronized (this) {
	              try {
	                  wait(endTime - System.currentTimeMillis());
	              } catch (Exception e) {
	              }
	          }
	      }
	   }
	}

	
	
	
	
	@Override
	public void onCreate() {
	    HandlerThread thread = new HandlerThread("ServiceStartArguments",
	            Process.THREAD_PRIORITY_DEFAULT);
	    thread.start();
	    
	    // Get the HandlerThread's Looper and use it for our Handler 
	    mServiceLooper = thread.getLooper();
	    mServiceHandler = new ServiceHandler(mServiceLooper);

		super.onCreate();
	}
	
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

}
