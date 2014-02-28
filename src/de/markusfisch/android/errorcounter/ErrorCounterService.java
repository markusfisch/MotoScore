package de.markusfisch.android.errorcounter;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;

public class ErrorCounterService
	extends Service
	implements
		SensorEventListener,
		LocationListener,
		Runnable
{
	public interface ErrorCounterServiceListener
	{
		public void update();
	}

	public class Binder extends android.os.Binder
	{
		public ErrorCounterService getService()
		{
			return ErrorCounterService.this;
		}
	};

	public int errors;
	public float distance;
	public ErrorCounterServiceListener listener = null;

	private final IBinder binder = new Binder();
	private LocationManager locationManager = null;
	private SensorManager sensorManager = null;
	private Sensor accelerationSensor = null;
	private boolean accelerationListening = false;
	private Location currentLocation = null;
	private Location lastLocation = null;
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
		locationManager = (LocationManager)
			getSystemService( Context.LOCATION_SERVICE );

		sensorManager = (SensorManager)
			getSystemService( Context.SENSOR_SERVICE );

		vibrator = (Vibrator)
			getSystemService( Context.VIBRATOR_SERVICE );

		if( (currentLocation = locationManager.getLastKnownLocation(
			LocationManager.NETWORK_PROVIDER )) == null )
			currentLocation = locationManager.getLastKnownLocation(
				LocationManager.GPS_PROVIDER );

		registerSensors();
		startThread();
	}

	@Override
	public void onDestroy()
	{
		stopThread();
		unregisterSensors();
	}

	@Override
	public int onStartCommand( Intent intent, int flags, int startId )
	{
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
				final float z = event.values[2];
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
				}

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
		currentLocation = location;
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
			if( lastLocation != null &&
				lastLocation.getTime() != currentLocation.getTime() )
			{
				float d = currentLocation.distanceTo(
					lastLocation );

				if( lastLocation.getAccuracy() < d &&
					currentLocation.getAccuracy() < d )
					distance += d;
			}

			if( currentLocation != null )
				lastLocation = new Location( currentLocation );

			if( tripleTap )
			{
				++errors;

				if( listener != null )
					listener.update();

				vibrator.vibrate( 1000 );
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

	public void reset()
	{
		errors = 0;
		distance = 0;
		lastLocation = null;
		hit = 0;
		firstTap = 0;
		secondTap = 0;
		tripleTap = false;
		blindFor = 0;
	}

	private void unregisterSensors()
	{
		if( sensorManager != null )
		{
			sensorManager.unregisterListener( this );
			accelerationListening = false;
		}

		if( locationManager != null )
		{
			locationManager.removeUpdates( this );
		}
	}

	private void registerSensors()
	{
		if( locationManager != null )
		{
			int minDistance = 10;

			locationManager.requestLocationUpdates(
				LocationManager.PASSIVE_PROVIDER,
				1000,
				minDistance,
				this );

			locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER,
				5000,
				minDistance,
				this );

			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				5000,
				minDistance,
				this );
		}

		if( sensorManager != null )
		{
			if( !accelerationListening &&
				(accelerationSensor != null ||
					(accelerationSensor = sensorManager.getDefaultSensor(
						Sensor.TYPE_LINEAR_ACCELERATION )) != null) )
				accelerationListening = sensorManager.registerListener(
					this,
					accelerationSensor,
					SensorManager.SENSOR_DELAY_GAME );
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
