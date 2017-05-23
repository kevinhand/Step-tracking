package com.example.steptracking;

import com.example.steptracking.R;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Vector;


import com.example.steptracking.TrackingService;
import com.example.steptracking.TrackingService.MyBinder;



public class StepTrackingActivity extends Activity {
   
	private TextView mStepCountView;
	private TextView mWalkingStateView;
	private TextView mHeadingView;
	private TextView mWifiView;
	private EditText mIPtext;
	
	private Intent mIntent = null;
	
	private TrackingService mTrackingService = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try{
        
	        setContentView(R.layout.activity_step_tracking);
	 
	       
	        mStepCountView = (TextView)findViewById(R.id.StepCountTextView);
	        mWalkingStateView = (TextView)findViewById(R.id.WalkingStateTextView);
	        mHeadingView = (TextView)findViewById(R.id.HeadingTextView);
	        mIPtext = (EditText)findViewById(R.id.IPtext);
	        mWifiView = (TextView)findViewById(R.id.wifiTextView);
	        
	        //mStepCountView.setText("Step:\n");
	        //mWalkingStateView.setText("Not Walking\n");
	        
	        
		
        }catch(Exception e)
        {
        	e.printStackTrace();
        }
    }
    
    
    @Override
    public void onStop() {
    	super.onStop();
//    	// unregister sensor listeners to prevent the activity from draining the device's battery.
//    	mSensorManager.unregisterListener(this);
//    	isRunning = false;
//    	// Flush the acceleration history to the log for detail analysis
//    	//writeAccLog();
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	
    	if(mTrackingService!=null)
    	{
    		mTrackingService.onDestroy();
    		this.getApplicationContext().unbindService(conn);
    	}
    	
    	
    	mTrackingService = null;
    	
//    	// unregister sensor listeners to prevent the activity from draining the device's battery.
//    	mSensorManager.unregisterListener(this);
//    	isRunning = false;
//    	// Flush the acceleration history to the log for detail analysis
//    	//writeAccLog();
    }
	
    @Override
    protected void onPause() {
        super.onPause();
//        // unregister sensor listeners to prevent the activity from draining the device's battery.
//        mSensorManager.unregisterListener(this);
    }
    
    @Override
    public void onResume() {
    	super.onResume();
//    	// restore the sensor listeners when user resumes the application.
//    	initListeners();
    }
        
    public void onResetStepButtonClicked(View v) throws InterruptedException
    {
    	if(mTrackingService == null)
    	{
	    	mIntent = new Intent(StepTrackingActivity.this, TrackingService.class);
			//startService(mIntent);
	        
	    	this.getApplicationContext().bindService(mIntent, conn, Context.BIND_AUTO_CREATE);
	    	
//    		Intent intent = new Intent();
//    		intent.setAction("com.example.steptracking.TrackingService");  
//            startService(intent);  
    	}else
    		mTrackingService.onResetStepButtonClicked();

    }
    
    private ServiceConnection conn = new ServiceConnection() {
        
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
        	mTrackingService.onDestroy();
        }
        
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO Auto-generated method stub
            MyBinder binder = (MyBinder)service;
            mTrackingService = binder.getService();
            mTrackingService.setElement(mStepCountView, mWalkingStateView, mHeadingView, mIPtext, mWifiView);
        }
    };
}

