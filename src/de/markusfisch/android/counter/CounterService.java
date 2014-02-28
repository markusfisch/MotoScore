package de.markusfisch.android.counter;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;

public class CounterService
	extends Service
	implements
		SensorEventListener,
		LocationListener,
		Runnable
{
	public interface CounterServiceListener
	{
		public void onCount();
	}

	public class Binder extends android.os.Binder
	{
		public CounterService getService()
		{
			return CounterService.this;
		}
	};

	public static final String COUNT = "count";

	public CounterServiceListener listener = null;
	public int errors = 0;
	public float distance = 0;
	public boolean driving = false;

	private class AccelerationEvent
	{
		public float z;
		public long time;
	};

	private final IBinder binder = new Binder();
	private final Handler countHandler = new Handler();
	private final Runnable countRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			count();
		}
	};
	private long lastCountAt = 0;

	private Notifications notifications = null;

	private LocationManager locationManager = null;
	private Location lastLocation = null;

	private AudioManager audioManager = null;
	private ComponentName remoteControlReceiver = null;

	private PowerManager powerManager = null;
	private PowerManager.WakeLock wakeLock = null;
	private SensorManager sensorManager = null;
	private Sensor accelerationSensor = null;
	private boolean accelerationListening = false;
	private long lastAccelerationEvent = 0;
	private int accelerationMark = 0;
	private int accelerationMax = 1000;
	private boolean accelerationHistoryComplete = false;
	private AccelerationEvent accelerationHistory[] =
		new AccelerationEvent[accelerationMax];

	private boolean running = false;
	private Thread thread = null;
	private long hit = 0;
	private long firstTap = 0;
	private long secondTap = 0;
	private boolean tripleTap = false;
	private Vibrator vibrator;
	private long blindFor = 0;

	@Override
	public void onCreate()
	{
		notifications = new Notifications( getApplicationContext() );

		locationManager = (LocationManager)
			getSystemService( Context.LOCATION_SERVICE );

		audioManager = (AudioManager)
			getSystemService( Context.AUDIO_SERVICE );

		powerManager = (PowerManager)
			getSystemService( Context.POWER_SERVICE );

		sensorManager = (SensorManager)
			getSystemService( Context.SENSOR_SERVICE );

		vibrator = (Vibrator)
			getSystemService( Context.VIBRATOR_SERVICE );

		if( (lastLocation = locationManager.getLastKnownLocation(
			LocationManager.NETWORK_PROVIDER )) == null )
			lastLocation = locationManager.getLastKnownLocation(
				LocationManager.GPS_PROVIDER );

		for( int n = accelerationMax; n-- > 0; )
			accelerationHistory[n] = new AccelerationEvent();

		registerSensors();
	}

	@Override
	public void onDestroy()
	{
		unregisterSensors();
	}

	@Override
	public int onStartCommand( Intent intent, int flags, int startId )
	{
		if( intent != null &&
			intent.getBooleanExtra( COUNT, false ) )
		{
			final long now = java.lang.System.currentTimeMillis();

			if( now-lastCountAt < 500 )
			{
				countHandler.removeCallbacks( countRunnable );

android.util.Log.d( "mf:dbg", "mf:dbg: double click!" );
				/*if( started )
					stop();
				else
					start();*/
			}
			else
				countHandler.postDelayed( countRunnable, 500 );

			lastCountAt = now;
		}

		return START_STICKY;
	}

	@Override
	public IBinder onBind( Intent intent )
	{
		return binder;
	}

	@Override
	public void onSensorChanged( SensorEvent event )
	{
		switch( event.sensor.getType() )
		{
			case Sensor.TYPE_LINEAR_ACCELERATION:
				accelerationEvent( event );
				break;
		}
	}

	@Override
	public void onAccuracyChanged( Sensor accelerationSensor, int accuracy )
	{
	}

	@Override
	public void onLocationChanged( Location location )
	{
		if( lastLocation != null &&
			lastLocation.getTime() != location.getTime() )
		{
			float d = location.distanceTo(
				lastLocation );

			if( lastLocation.getAccuracy() < d &&
				location.getAccuracy() < d )
				distance += d;
		}

		if( location != null )
			lastLocation = location;
	}

	@Override
	public void onStatusChanged( String provider, int status, Bundle extras )
	{
	}

	@Override
	public void onProviderEnabled( String provider )
	{
	}

	@Override
	public void onProviderDisabled( String provider )
	{
	}

	@Override
	public void run()
	{
		while( running )
		{
			if( tripleTap )
			{
				count();
				tripleTap = false;
			}

			try
			{
				thread.yield();
			}
			catch( Exception e )
			{
			}
		}
	}

	public void count()
	{
		synchronized( this )
		{
			++errors;

			if( listener != null )
				listener.onCount();

			vibrator.vibrate( 1000 );
		}
	}

	public void reset()
	{
		errors = 0;
		distance = 0;
		driving = false;

		hit = 0;
		firstTap = 0;
		secondTap = 0;
		tripleTap = false;
		blindFor = 0;
	}

	private void accelerationEvent( SensorEvent event )
	{
		/*final float z = event.values[2];
		final long t = event.timestamp;

		if( t < blindFor )
			break;

		if( z < -6 )
			hit = event.timestamp;
		else if( secondTap > 0 &&
			t-secondTap > 1000000000 )
		{
			firstTap = 0;
			secondTap = 0;
		}
		else if( firstTap > 0 &&
			t-firstTap > 1000000000 )
		{
			firstTap = 0;
			secondTap = 0;
		}

		if( hit > 0 &&
			t-hit > 100000000 )
		{
			if( firstTap == 0 )
				firstTap = hit;
			else if( secondTap == 0 )
				secondTap = hit;
			else
			{
				firstTap = 0;
				secondTap = 0;
				tripleTap = true;

				// some vibration motors actually point
				// in Z direction and will trigger
				// it all again so be blind for the
				// time of vibration
				blindFor = hit+1100000000l;
			}

			hit = 0;
		}*/

		final long t = event.timestamp;
		final float z = event.values[2];

		if( lastAccelerationEvent > 0 )
		{
			final AccelerationEvent ae =
				accelerationHistory[accelerationMark];

			ae.z = z;
			ae.time = t;

			if( ++accelerationMark >= accelerationMax )
			{
				accelerationMark = 0;
				accelerationHistoryComplete = true;
			}
		}

		lastAccelerationEvent = t;
	}

	private void unregisterSensors()
	{
		if( wakeLock != null )
		{
			wakeLock.release();
		}

		if( accelerationListening )
		{
			stopThread();
			sensorManager.unregisterListener( this );
			accelerationListening = false;
		}

		if( remoteControlReceiver != null )
		{
			audioManager.unregisterMediaButtonEventReceiver(
				remoteControlReceiver );
		}

		if( locationManager != null )
		{
			locationManager.removeUpdates( this );
		}
	}

	private void registerSensors()
	{
		SharedPreferences p = getSharedPreferences(
			CounterPreferenceActivity.SHARED_PREFERENCES_NAME,
			0 );

		if( locationManager != null )
		{
			// don't permanently request position updates
			// because that would drain the battery
			int metersBetweenUpdates = 10;

			locationManager.requestLocationUpdates(
				LocationManager.PASSIVE_PROVIDER,
				1000,
				metersBetweenUpdates,
				this );

			locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER,
				5000,
				metersBetweenUpdates,
				this );

			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				5000,
				metersBetweenUpdates,
				this );
		}

		if( audioManager != null &&
			p.getBoolean(
				CounterPreferenceActivity.USE_MEDIA_BUTTON,
				true ) )
		{
			remoteControlReceiver = new ComponentName(
				getPackageName(),
				RemoteControlReceiver.class.getName() );

			audioManager.registerMediaButtonEventReceiver(
				remoteControlReceiver );
		}

		if( sensorManager != null &&
			p.getBoolean(
				CounterPreferenceActivity.USE_KNOCK_DETECTION,
				true ) )
		{
			/*if( powerManager != null &&
				(wakeLock = powerManager.newWakeLock(
					PowerManager.SCREEN_DIM_WAKE_LOCK,
					"Counter" )) != null )
				wakeLock.acquire();*/

			if( !accelerationListening &&
				(accelerationSensor != null ||
					(accelerationSensor = sensorManager.getDefaultSensor(
						Sensor.TYPE_LINEAR_ACCELERATION )) != null) &&
				(accelerationListening = sensorManager.registerListener(
					this,
					accelerationSensor,
					SensorManager.SENSOR_DELAY_GAME )) )
				startThread();
		}
	}

	private void startThread()
	{
		if( running )
			return;

		running = true;

		thread = new Thread( this );
		thread.start();
	}

	private void stopThread()
	{
		if( !running )
			return;

		running = false;

		try
		{
			thread.join();
		}
		catch( Exception e )
		{
		}
	}
}
