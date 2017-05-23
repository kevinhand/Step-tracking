package com.example.steptracking;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Vector;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class TrackingService extends Service implements SensorEventListener{

	private TextView mStepCountView;
	private TextView mWalkingStateView;
	private TextView mHeadingView = null;
	private EditText mIPtext;
	private TextView mWifiView;
	
	private SensorManager mSensorManager = null;
	private WifiManager mWifi = null;
	BroadcastReceiver mWifiReceiver = null;
	IntentFilter mWifiIntent= null;
//	public  TelephonyManager tel = null;
	
	private float[] mRotationMatrix = new float[16];
	private float[] mLinearVector = new float[4];
	private float[] mWorldAcce = new float[4];
	private float[] mInverseRotationMatrix = new float[16];
	private float[] mRotationVector = new float[4];
	//For testing
	private float[] mAcc = new float[4];
	private float[] mMag = new float[4];
	
	//Environment
	private double mTemp;
	private double mLight;
	private double mHumidity;
	
	//Phone orientation
	private float[] orientVals = new float[3];
	private float angle = 0;
	
	private String serverIP = "172.26.191.55";
//	private DataOutputStream logWriter;
//	private DataOutputStream logWriter2;
	
	private boolean isSendDataToServer  = false; //True: send data to server , False: only logging data
	private boolean isUserWifi2LogFP = true; //True: use android wifi to log FP , false: us IW to log
	private boolean isCollectIWResult = false; //True to collect the IW result by continuosly reading file
	
	private Vector<Fingerprint> mCollectedIWFPs = new Vector<Fingerprint>();
	
	//Step Detection Thresholds and Variables
	double upperThresh = 2.5;
	double lowerThresh = -1.5;
	int maxDistThresh = 150;
	int minDistThresh = 15;
	int currentStepCount = 0;
	int minStepDistThresh = 15;
	int maxStepDistThresh = 150;
	int maxStillDistThresh = 600;
	float lastUpperPeak = -1;
	float lastLowerPeak = -1;
	long lastUpperPeakIndex = -1;
	long lastLowerPeakIndex = -1;
	long lastStepIndex = -1;
	long sampleCount = 0;
	int logcounter=0;
	
	
	//Control Variables
	boolean isStepUpdate = false;
	boolean isRunning = true;
	boolean isWalking = false;
	
	//Vector to hold all angles calculated, each step may occupy multiple entries in the vector 
	Vector<headingTuple> accVector = new Vector<headingTuple>();
	int accVectorIndex = 0;
	
	boolean orientationPositive = true; //True positive, False negative
	
	Process mProcess = null;
	
	long lastStepTime = 0;
	
	//Angle smoothing
	float azimuth = 0;
	float lastAzimuth = 361;
	float l = 0.5f;
	
	//Sea level
	float mAltitude = -1;
	
	public class Fingerprint
	{
		String mac;
		Integer rssi;
		long collectedTime;
		
		Fingerprint(String m, int r)
		{
			mac = m;
			rssi = r;
			collectedTime = 0;
		}
		
		Fingerprint(String m, int r, long t)
		{
			mac = m;
			rssi = r;
			collectedTime = t;
		}
	}
	
	
	public void setElement(TextView t1, TextView t2, TextView t3, EditText e1, TextView t4)
	{
		mStepCountView = t1;
		mWalkingStateView = t2;
		mHeadingView = t3;
		mIPtext = e1;
		mWifiView = t4;
		
		mStepCountView.setText("Step: "+ currentStepCount + "\n");
		mWalkingStateView.setText("Not Walking\n");
	}
	
	@Override
	public void onCreate() {
	
		 // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
//        tel = (TelephonyManager)this.getSystemService(Context.TELEPHONY_SERVICE);
        initListeners();
        
        if(isUserWifi2LogFP)
        {
	        // get wifi manager
	        mWifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
	        mWifiIntent = new IntentFilter();
	        mWifiIntent.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
	        mWifiReceiver = new BroadcastReceiver(){
	            public void onReceive(Context c, Intent i){
	              List<ScanResult> l = mWifi.getScanResults();
	              mWifiView.setText("");
	              for (ScanResult r : l) {
	                 // do smth with results
	            	  mWifiView.append(r.BSSID + ":"+ r.level+"\n");
	              }
	              
	              mWifi.startScan();
	            }
	        };
	        this.registerReceiver(mWifiReceiver, mWifiIntent );
	        mWifi.startScan();
        }
        
        for(int i=0; i<4; i++)
    	{
    		mAcc[i] = 0;
    		mMag[i] = 0;
    		mRotationVector[i] = 0;
    	}

//		try {		
//	    	String path = Environment.getExternalStorageDirectory().getAbsolutePath();
//	    	File folder= new File(path+"/StepLog/");
//	    	if (!folder.exists()) {
//	    		folder.mkdirs();
//	    	}
//	    	logcounter++;
//	    	File f = new File(path+"/StepLog/Log"+logcounter+".txt");
//	    	File f2 = new File(path+"/StepLog/wLog"+logcounter+".txt");
//	    	while(f.exists()){
//	    		logcounter++;
//	    		f = new File(path+"/StepLog/Log"+logcounter+".txt");
//	    		f2 = new File(path+"/StepLog/wLog"+logcounter+".txt");
//	    	}	    	
//			f.createNewFile();
//			f2.createNewFile();
//	    	FileOutputStream fos = new FileOutputStream(f);
//	    	FileOutputStream fos2 = new FileOutputStream(f2);
//			logWriter = new DataOutputStream(fos);
//			logWriter2 = new DataOutputStream(fos2);
//			//WriteCurrentTime
			

			if(!isUserWifi2LogFP)
			{
//				new Thread() {
//					public void run()
//					{
//						try{
//							String path = Environment.getExternalStorageDirectory().getAbsolutePath();
//			            	String[] commands = {
//			    		            "sh /data/iw/iw.sh "+path+"/StepLog/Log_"+logcounter+".txt",		   
//			    		    };
//							Runtime rt = Runtime.getRuntime();
//							mProcess = rt.exec("su");
//							execCommands(mProcess, commands);
//						}catch(Exception e){e.printStackTrace();}
//					}
//	            }.start();
				
	            if(isCollectIWResult)
	            {
		           // Thread.sleep(200);
		            
//		            new Thread() {
//						public void run()
//						{
//							try{
//								collectIWResult();
//							}catch(Exception e){
//								e.printStackTrace();
//							}
//						}
//		            }.start();
	            }
	            
	            lastStepTime = System.currentTimeMillis();
			}
			//Start the network task to send the data to the server for result visualization
            new NetworkTask().execute(null , null, null);
		    
//		} catch (Exception e) {
//			e.printStackTrace();
//		} 
        
	}
	
	private void collectIWResult() throws IOException
	{
		String path = Environment.getExternalStorageDirectory().getAbsolutePath();
		File f = new File(path+"/StepLog/Log_"+logcounter+".txt");
		if(!f.exists())
			f.createNewFile();
		FileInputStream fis = new FileInputStream(f);
		DataInputStream dis = new DataInputStream(fis);
		
		String line ="";
		long currentTime = 0;
		String currentMac = "";
		
		while(isRunning)
		{
			try{
				line=dis.readLine();
				
				if(line==null)
				{
					Thread.sleep(500);
					continue;
				}
				if(line.startsWith("1"))
				{
					currentTime = Long.parseLong(line);
					Thread.sleep(50);
				}else if(line.startsWith("BSS "))
				{
					currentMac = line.substring(4,21);
				}else if(line.trim().startsWith("signal:"))
				{
					String[] tokens = line.trim().split(" ");
					double currentSignal = Double.parseDouble(tokens[1]);
					
					synchronized(mCollectedIWFPs){
						mCollectedIWFPs.add(new Fingerprint(currentMac, (int)currentSignal, currentTime));
					}
				}
			
			}catch(Exception e){}
		}
		
	}
	

	public Boolean execCommands(Process process, String... command ) {
	    try {
	        DataOutputStream os = new DataOutputStream(process.getOutputStream());

	        for(int i = 0; i < command.length; i++) {
	            os.writeBytes(command[i] + "\n");
	            os.flush();
	        }
	        os.writeBytes("exit\n");
	        os.flush();
	        process.waitFor();
	    } catch (IOException e) {
	        return false;
	    } catch (InterruptedException e) {
	        return false;
	    }
	    return true; 
	    
	    
	}
	
	@Override
    public void onDestroy() {
    	super.onDestroy();
    	// unregister sensor listeners to prevent the activity from draining the device's battery.
    	mSensorManager.unregisterListener(this);
    	//this.unregisterReceiver(mWifiReceiver);
    	isRunning = false;
    	
    	//Stop the wifi collecting script
    	if(!isUserWifi2LogFP)
    		killProcessByName("sh");
    	
    	//writeLog();
    }
	
	public void killProcessByName(String killProcessName) {
		try {
			Process p = Runtime.getRuntime().exec("ps");
			p.waitFor();
			StringBuffer sb = new StringBuffer();
			InputStreamReader isr = new InputStreamReader(p.getInputStream());
			int ch;
			char[] buf = new char[1024];
			while ((ch = isr.read(buf)) != -1) {
				sb.append(buf, 0, ch);
			}

			Vector<Integer> pids = new Vector<Integer>();
			String[] processLinesAr = sb.toString().split("\n");
			for (String line : processLinesAr) {
				String[] comps = line.split("[\\s]+");
				if (comps.length != 9)
					continue;

				int pid = Integer.parseInt(comps[1]);
				String packageName = comps[8];
				if (packageName.equals(killProcessName))
					pids.add(pid);
			}

			Runtime rt = Runtime.getRuntime();
			Process process = rt.exec("su");
			String[] commands = new String[pids.size()];
			for (int i = 0; i < commands.length; i++) {
				commands[i] = "kill -9 " + pids.get(i);
			}
			execCommands(process, commands);
		} catch (Exception e) {
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		IBinder result = null;
	    if (null == result) {
	        result = new MyBinder();
	    }
	    return result;
	}
	
	public class MyBinder extends Binder{
	    
	    public TrackingService getService(){
	        return TrackingService.this;
	    }
	}
	
    // This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
    public void initListeners(){

        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_FASTEST);
        
        mSensorManager.registerListener(this,
                mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
                SensorManager.SENSOR_DELAY_FASTEST);
        
//        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_PRESSURE);
//        if(sensors.size() > 0) {
//          mSensorManager.registerListener(this, sensors.get(0), SensorManager.SENSOR_DELAY_FASTEST);
//        }
        
        //Rotation Matrix Testing
//        mSensorManager.registerListener(this,
//                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
//                SensorManager.SENSOR_DELAY_FASTEST);
//        
//        mSensorManager.registerListener(this,
//                mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE),
//                SensorManager.SENSOR_DELAY_FASTEST);
        
//        mSensorManager.registerListener(this,
//                mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
//                SensorManager.SENSOR_DELAY_FASTEST);
//        
//        mSensorManager.registerListener(this,
//                mSensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY),
//                SensorManager.SENSOR_DELAY_FASTEST);
//        Log.i("before", "before"); //INFO 
        
    }

 
    
	@Override
	public void onSensorChanged(SensorEvent event) {
		
//		if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
//			 return;
		
		switch(event.sensor.getType()) {
		
		case Sensor.TYPE_ROTATION_VECTOR:
	    	//Calculate new rotation matrix
	    	SensorManager.getRotationMatrixFromVector(mRotationMatrix , event.values);
	    	
	    	mRotationVector[0] = event.values[0];
	    	mRotationVector[1] = event.values[1];
	    	mRotationVector[2] = event.values[2];
	    	
	    	//mHeadingView.setText((int)(event.values[0]*100) + " " + (int)(event.values[1]*100) + " " + (int)(event.values[2]*100));
	    	SensorManager.getOrientation(mRotationMatrix, orientVals);
	    	angle = (float) normalizeAngle(orientVals[0]);
			azimuth = (float) Math.toDegrees(angle);
			//lowPassFilter();
			
			angle=(float) Math.toRadians(azimuth);
	    	break;
	    
		case Sensor.TYPE_ORIENTATION:
			float pitch = event.values[1];
			if( -140 < pitch && pitch < 30)
				orientationPositive = true;
			else
				orientationPositive = false;
			break;
	    	
	    case Sensor.TYPE_LINEAR_ACCELERATION:
	    	//Update rotation matrix, inverted version
	    	mLinearVector[0] = event.values[0];
	    	mLinearVector[1] = event.values[1];
	    	mLinearVector[2] = event.values[2];

	    	//SensorManager.getRotationMatrix(mRotationMatrix,null,mAcc,mMag);
	    	android.opengl.Matrix.invertM(mInverseRotationMatrix,0,mRotationMatrix,0);
	    	android.opengl.Matrix.multiplyMV(mWorldAcce,0,mInverseRotationMatrix,0,mLinearVector,0);
	    		  	    	
	    	//Update walking state and step count
	    	updateStep();
	    	//Add current heading angle to the accVector
	    	updateAngleHistory();
	    	
	    	break;	 
	    	
//	    case Sensor.TYPE_MAGNETIC_FIELD:
//	    	mMag[0] = event.values[0];
//	    	mMag[1] = event.values[1];
//	    	mMag[2] = event.values[2];
//	    	break;
//	    	
//	    case Sensor.TYPE_PRESSURE:
//	    	mAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]);
//	    	break;
//	    	
//	    case Sensor.TYPE_LIGHT:
//	    	mLight = event.values[0];
//	    	break;
//	    	
//	    case Sensor.TYPE_AMBIENT_TEMPERATURE:
//	    	mTemp = event.values[0]; 
//	    	break;
//	    	
//	    case Sensor.TYPE_RELATIVE_HUMIDITY:
//	    	mHumidity = event.values[0];
//	    	break;
	    }
		
		
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public void onResetStepButtonClicked() throws InterruptedException
    {
    	//Stop the network task
    	isRunning = false;
    	
    	try {Thread.sleep(200);} catch(InterruptedException e) {}
    	//Get the server ip address from the edit text
    	serverIP = mIPtext.getText().toString();
    	//Reset steps
    	currentStepCount = 0;
		lastLowerPeakIndex = -1;
		lastUpperPeakIndex = -1;
		lastStepIndex = -1;
		mStepCountView.setText("Step: "+ currentStepCount + "\n");
		//Restart the network task to inform the server the start of the new session
    	isRunning = true;
    	new NetworkTask().execute(null , null, null);
    }
	
	public void updateStep()
	{
		//Increase current sample count
		sampleCount++;
		
		//If the user is standing still for too much time, reset the walking state
		if(sampleCount - lastStepIndex > maxStillDistThresh)
		{
			isWalking = false;
			mWalkingStateView.setText("Not Walking\n");
			//mHeadingView.setText("\n");
		}
		
		//Detect steps based on zAcc 
		if(mWorldAcce[2]>upperThresh)
		{
			lastUpperPeak = mWorldAcce[2];
			lastUpperPeakIndex = sampleCount;
			
			if(lastLowerPeakIndex!=-1 && lastUpperPeakIndex-lastLowerPeakIndex<maxDistThresh 
				&& lastUpperPeakIndex-lastLowerPeakIndex>minDistThresh && sampleCount - lastStepIndex > minStepDistThresh)
			{
				//In the walking state, new step detected
				if(isWalking)
				{
					isStepUpdate = true;
					currentStepCount++;
					lastStepIndex = sampleCount;
					//Reset last lower peak for future steps
					lastLowerPeakIndex = -1;
					
					mWalkingStateView.setText("Walking\n");
					mStepCountView.setText("Step: "+ currentStepCount + "\n");
				}else
				{
					//Not in the walking state, transit to the walking state if one candidate step detected
					if(sampleCount-lastStepIndex<maxStepDistThresh)
					{
						isWalking = true;
					}
					lastStepIndex = sampleCount;
				}
			}
		}else if(mWorldAcce[2]<lowerThresh)
		{
			if(mWorldAcce[2]<lastLowerPeak || sampleCount-lastLowerPeakIndex > maxDistThresh)
			{
				lastLowerPeak = mWorldAcce[2];
				lastLowerPeakIndex = sampleCount;
			}
		}	
	}	

	//Add new heading angle to the vector for further heading analysis
	public void updateAngleHistory()
	{
		if(isWalking && Math.sqrt((mWorldAcce[1]*mWorldAcce[1])+(mWorldAcce[0]*mWorldAcce[0]))>0.8)
		{
	    	double angle = 0;
	    	String directionTag = "";
	    	
	    	if(mWorldAcce[0]==0 && mWorldAcce[1]<0)
	    	{
	    		angle = 270;
	    		directionTag = "南";
	    	}else if(mWorldAcce[0]==0 && mWorldAcce[1]>0)
	    	{
	    		angle = 90;
	    		directionTag = "北";
	    	}else if(mWorldAcce[0]<0 && mWorldAcce[1]==0)
	    	{
	    		angle = 180;
	    		directionTag = "西";
	    	}else if(mWorldAcce[0]>0 && mWorldAcce[1]==0)
	    	{
	    		directionTag = "东";
	    	}else if(mWorldAcce[0]<0 && mWorldAcce[1]<0)
	    	{
	    		angle = Math.atan(mWorldAcce[1]/mWorldAcce[0]);
	    		angle *= 57.32;
	    		angle = (int)(angle+180);
	    		directionTag = "西南";
	    	}else if(mWorldAcce[0]<0 && mWorldAcce[1]>0)
	    	{
	    		angle = Math.atan(-mWorldAcce[1]/mWorldAcce[0]);
	    		angle *= 57.32;
	    		angle = (int)(180-angle);
	    		directionTag = "西北";
	    	}else if(mWorldAcce[0]>0 && mWorldAcce[1]<0)
	    	{
	    		angle = Math.atan(-mWorldAcce[1]/mWorldAcce[0]);
	    		angle *= 57.32;
	    		angle = (int)(360-angle);
	    		directionTag = "东南";;
	    	}else if(mWorldAcce[0]>0 && mWorldAcce[1]>0)
	    	{
	    		angle = Math.atan(mWorldAcce[1]/mWorldAcce[0]);
	    		angle *= 57.32;
	    		angle = (int)angle;
	    		directionTag = "东北";
	    	}
	    	
			accVector.add(new headingTuple(mWorldAcce[0],mWorldAcce[1],mWorldAcce[2],(int)angle,directionTag,currentStepCount ));
		}
	}
	
	//Find out the heading axis and angles for the current step
	public int findHeadingAngle(Vector<headingTuple> accVector)
	{
		int mergeThresh = 25;
		int maxDegree =-1;
		int maxCount = -1;
		int maxPositive = 0;
		int maxNegative = 0;
		float maxPosAcc = 0;
		float maxNegAcc = 0;
		boolean maxPosFlag = false;
		
		for(int i=0; i<180; i++)
		{
			int Count=0;
			int positive = 0;
			int negative = 0;
			float posAcc = 0;
			float negAcc = 0;
			
			double tempPosMax = -1;
			double tempNegMax = -1;
			
			for(int j=accVectorIndex; j<accVector.size(); j++)
			{
				if( Math.abs(accVector.get(j).angle-i) <mergeThresh)
				{
					Count++;
					positive++;
					double len = Math.sqrt(accVector.get(j).horizentalAcc*accVector.get(j).horizentalAcc + accVector.get(j).verticalAcc*accVector.get(j).verticalAcc);
					posAcc += len;
					if(len>tempPosMax)
						tempPosMax = len;
				}else if(Math.abs(accVector.get(j).angle-i-180)<mergeThresh)
				{
					Count++;
					negative++;
					double len = Math.sqrt(accVector.get(j).horizentalAcc*accVector.get(j).horizentalAcc + accVector.get(j).verticalAcc*accVector.get(j).verticalAcc);
					negAcc += len;
					if(len>tempNegMax)
						tempNegMax = len;
				}
			}
			
			if(Count > maxCount)
			{
				maxCount = Count;
				maxDegree = i;
				maxPositive = positive;
				maxNegative = negative;
				maxPosAcc = posAcc;
				maxNegAcc = negAcc;
				if(tempPosMax>tempNegMax)
					maxPosFlag = true;
				else
					maxPosFlag = false;
			}
		}
		
		if( (orientationPositive && maxNegAcc>maxPosAcc) || (!orientationPositive && maxNegAcc<maxPosAcc))
			return maxDegree;
		else 
			return  (maxDegree+180);
	}
	
	public float normalizeAngle(float angle){
		angle=(float) (angle%(2*Math.PI));
		return (float) (angle<0?angle+2*Math.PI:angle);
	}
	
	
	protected void lowPassFilter() {
		
		if(lastAzimuth == 361)
		{
			lastAzimuth=azimuth;
			return;
		}
		
		// lowpass filter
		float dazimuth = azimuth -lastAzimuth;

//		// if the angle changes more than 180°, we want to change direction and follow the shorter angle
		if (dazimuth > 180) {
			// change to range -180 to 0
			dazimuth = (float) (dazimuth - 360f);
		} else if (dazimuth < -180) {
			// change to range 0 to 180
			dazimuth = (float) (360f + dazimuth);
		}
		// lowpass filter
		azimuth = lastAzimuth+ dazimuth*l;
		
		azimuth%=360;
		
		if(azimuth<0){
			azimuth+=360;
		}
		
		lastAzimuth=azimuth;

	}

    
    public void writeLog() 
    {
    	int step = -1;
    	for(int i=0 ; i<accVector.size(); i++)
    	{
    		headingTuple tuple = accVector.get(i);
//    		try {
//				logWriter.write((tuple.step + "	" +  tuple.horizentalAcc + "	" + tuple.verticalAcc + "	ZAcc"+ tuple.zAcc + "	" + tuple.angle + "	" + tuple.direction + "\n").getBytes());
				
//				if(step!=tuple.step)
//				{
//					step = tuple.step;
//					for (ScanResult r : tuple.fingerPrint) {
//						logWriter.write((r.BSSID + ":"+ r.level+"\n").getBytes());
//		            }
//					logWriter.write("=================================================================\n".getBytes());
//				}
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
    	}
    }
    
    //Data structure to hold each heading input
	public class headingTuple
	{
		float horizentalAcc;
		float verticalAcc;
		float zAcc;
		int step;
		int angle;
		String direction;
		List<ScanResult> fingerPrint;
		
		headingTuple(float x, float y, float z, int a, String tag, int stepCount)
		{
			horizentalAcc = x;
			verticalAcc = y;
			zAcc = z;
			step = stepCount;
			angle = a;
			direction = tag;
			//fingerPrint = r;
		}	
	}
	

	private Vector<Fingerprint> avgFP4AllPoints(Vector<Fingerprint> fps)
	{
		HashMap<String, Integer> sumMap = new HashMap<String, Integer>();
		HashMap<String, Integer> countMap = new HashMap<String, Integer>();
		for(Fingerprint fp : fps)
		{
			if(!sumMap.containsKey(fp.mac))
			{
				sumMap.put(fp.mac, fp.rssi);
				countMap.put(fp.mac, 1);
			}else
			{
				int sum = sumMap.get(fp.mac);
				int c = countMap.get(fp.mac);
				sumMap.remove(fp.mac);
				countMap.remove(fp.mac);
				sumMap.put(fp.mac, fp.rssi + sum);
				countMap.put(fp.mac, c +1);
			}
		}
		
		Vector<Fingerprint> avgs = new Vector<Fingerprint>();
		Set<String> keyset = sumMap.keySet();
		for(String key : keyset)
		{
			int avg = sumMap.get(key)/countMap.get(key);
			avgs.add(new Fingerprint(key,avg));
		}
		return avgs;
	}
	
	//Task to send heading angle of each step to the server for visualisation 
	private class NetworkTask extends AsyncTask<String, Void, String> {
        protected String doInBackground(String... url) {
            try {
            	PrintWriter out = null;
            	if(isSendDataToServer)
            	{
            		try{
		            	String hostname = serverIP;
		            	Socket theSocket = new Socket(hostname, 8888);
		    		    BufferedReader networkIn = new BufferedReader(new InputStreamReader(theSocket.getInputStream()));
		    		    out = new PrintWriter(theSocket.getOutputStream());
            		}catch(Exception e){
            			e.printStackTrace();
            		}
            	}
            	
    		    while(true)//isRunning)
    		    {
//  		    	if(isStepUpdate)
//    		    	{
    		    		String fingerPrintString = "";
    		    		if(isUserWifi2LogFP)
    		    		{
	    		    		//Send the current heading angle and fingerprint to the server
	    		    		List<ScanResult> result = mWifi.getScanResults();
	    		    		for(ScanResult r : result)
	    		    		{
	    		    			/*fingerPrintString += r.BSSID + "," + r.level + " ";*/
	    		    			fingerPrintString += r.BSSID + " " + r.level + " ";
	    		    		}
    		    		}else
    		    		{
    		    			if(isCollectIWResult)
    		    			{
    		    				Vector<Fingerprint> fps = null;
    		    				synchronized(mCollectedIWFPs){
    		    					fps = avgFP4AllPoints(mCollectedIWFPs);
    		    					mCollectedIWFPs.clear();
    		    				}
    		    				for(Fingerprint p : fps)
    	    		    		{
    	    		    			fingerPrintString += p.mac+ "," + p.rssi + " ";
    	    		    		}
    		    			}
    		    		}
    		    		
//method one
/*    		    		String cellularString ="";    		    			
    		    		List<CellInfo>  cellinfogsmlist = tel.getAllCellInfo();
    		    		if(cellinfogsmlist !=null){
	    		    		for(int i = 0;i<cellinfogsmlist.size();i++){
	    		    			cellularString += ((CellInfoGsm)cellinfogsmlist.get(i)).getCellIdentity().getCid()+","+((CellInfoGsm)cellinfogsmlist.get(i)).getCellSignalStrength().getDbm()+" ";
	    		    		}	  		    			
    		    		}		
*/
//    		    		int MagValue = (int) Math.sqrt( mMag[0]*mMag[0]+mMag[1]*mMag[1]+mMag[2]*mMag[2]);
    		    		String headingString = lastStepTime + "#" + System.currentTimeMillis() + "#" + findHeadingAngle(accVector)+"#";
//    		    		String headingString = lastStepTime + "#" + System.currentTimeMillis() + "#" + findHeadingAngle(accVector)+"#"+MagValue+"#";
    		    		//String headingString = MagValue+"#";
    		    		  		    		
/*    		    		String headingString = lastStepTime + " " + System.currentTimeMillis() + " " +(int)((450-azimuth)%360) +" "+mAltitude+" "+MagValue+" "+mLight+" "+mHumidity+" "+mTemp;//正东0度，海拔
*/    		    		
    		    		lastStepTime = System.currentTimeMillis();
    		    		
    		    		String sendString = headingString+fingerPrintString+"\n";
//    		    		logWriter.write((sendString).getBytes());
    		    		
    		    		   		    	   		    		
//    		    		logWriter2.write((cellularString+"\n").getBytes());
/*    		    		logWriter2.write((fingerPrintString+"\n").getBytes());
*//*    		    		logWriter.write(headingString.getBytes());*/
    		    		
    		    		if(isSendDataToServer)
    		    		{
    		    			try{
	    		    			out.println(sendString);
	    		    			out.flush();
    		    			}catch(Exception e){}
    		    		}
    		    		
    		    		
    		    		//Mark all the angles to 'used'
    		    		accVectorIndex = accVector.size();
//    		    		isStepUpdate = false;
    		    		Thread.sleep(1500);
//   	    		    
    		    	}
//    		    }
    		   // networkIn.close();
    		  //  out.close();
    		  //  theSocket.close();
                
            } catch (Exception e) {
                e.printStackTrace();
            } 
            return null;
        }    	
    }
	

}
