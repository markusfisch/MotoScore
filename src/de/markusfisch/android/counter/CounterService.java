package de.markusfisch.android.counter;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.os.Vibrator;

import java.util.Date;

public class CounterService
	extends Service
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

	public static final String STATE = "state";
	public static final String ACTION = "action";
	public static final String TIME = "time";

	public CounterDataSource dataSource;
	public CounterServiceListener listener = null;
	public int errors = 0;
	public float distance = 0;
	public boolean started = false;
	public Date rideStart = new Date();

	private final IBinder binder = new Binder();

	private Notifications notifications = null;

	private LocationManager locationManager = null;
	private Location lastLocation = null;
	private LocationRecorder locationRecorder = new LocationRecorder();

	private HeadsetReceiver headsetReceiver = null;
	private AudioManager audioManager = null;
	private ComponentName remoteControlReceiver = null;
	private long buttonDown = 0;
	private Vibrator vibrator;

	@Override
	public void onCreate()
	{
		dataSource = new CounterDataSource( getApplicationContext() );
		dataSource.open( new Runnable()
		{
			@Override
			public void run()
			{
				dataSource.queryAll();
			}
		} );

		notifications = new Notifications( getApplicationContext() );

		locationManager = (LocationManager)
			getSystemService( Context.LOCATION_SERVICE );

		audioManager = (AudioManager)
			getSystemService( Context.AUDIO_SERVICE );

		vibrator = (Vibrator)
			getSystemService( Context.VIBRATOR_SERVICE );

		if( (lastLocation = locationManager.getLastKnownLocation(
				LocationManager.GPS_PROVIDER )) == null &&
			(lastLocation = locationManager.getLastKnownLocation(
				LocationManager.NETWORK_PROVIDER )) == null )
			lastLocation = locationManager.getLastKnownLocation(
				LocationManager.PASSIVE_PROVIDER );

		headsetReceiver = new HeadsetReceiver();
		registerReceiver(
			headsetReceiver,
			new IntentFilter( Intent.ACTION_HEADSET_PLUG ) );

		registerMediaButton();
	}

	@Override
	public void onDestroy()
	{
		unregisterReceiver( headsetReceiver );

		unregisterMediaButton();
		stop();

		dataSource.close();
	}

	@Override
	public int onStartCommand( Intent intent, int flags, int startId )
	{
		if( intent != null )
			handleCommands( intent );

		return START_STICKY;
	}

	@Override
	public IBinder onBind( Intent intent )
	{
		return binder;
	}

	public void unregisterMediaButton()
	{
		if( remoteControlReceiver == null )
			return;

		audioManager.unregisterMediaButtonEventReceiver(
			remoteControlReceiver );

		remoteControlReceiver = null;
	}

	public void registerMediaButton()
	{
		if( audioManager == null ||
			!getSharedPreferences().getBoolean(
				CounterPreferenceActivity.USE_MEDIA_BUTTON,
				true ) )
			return;

		unregisterMediaButton();

		remoteControlReceiver = new ComponentName(
			getPackageName(),
			RemoteControlReceiver.class.getName() );

		audioManager.registerMediaButtonEventReceiver(
			remoteControlReceiver );
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

	public void stop()
	{
		if( !started )
			return;

		if( locationManager != null )
			locationManager.removeUpdates( locationRecorder );

		notifications.counting.hide();
		started = false;
	}

	public void start()
	{
		if( started )
			return;

		save();

		if( locationManager != null )
		{
			// don't permanently request position updates
			// because that would drain the battery
			int metersBetweenUpdates = 10;

			locationManager.requestLocationUpdates(
				LocationManager.PASSIVE_PROVIDER,
				1000,
				metersBetweenUpdates,
				locationRecorder );

			locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER,
				5000,
				metersBetweenUpdates,
				locationRecorder );

			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				5000,
				metersBetweenUpdates,
				locationRecorder );
		}

		if( getSharedPreferences().getBoolean(
				CounterPreferenceActivity.SHOW_NOTIFICATION,
				true ) )
			notifications.counting.show();

		started = true;
	}

	private void save()
	{
		dataSource.insert(
			rideStart,
			new Date(),
			errors,
			distance );

		rideStart = new Date();
		errors = 0;
		distance = 0;
	}

	private void handleCommands( Intent intent )
	{
		int state = intent.getIntExtra( STATE, -1 );

		if( state > -1 )
		{
			switch( state )
			{
				case 0:
					unregisterMediaButton();
					break;
				case 1:
					registerMediaButton();
					break;
			}
		}
		else
		{
			long time = intent.getLongExtra( TIME, 1 );

			switch( intent.getIntExtra( ACTION, -1 ) )
			{
				case android.view.KeyEvent.ACTION_DOWN:
					// there may come multiple ACTION_DOWNs
					// before there's a ACTION_UP but only
					// the very first one is interesting
					if( buttonDown == 0 )
						buttonDown = time;
					break;
				case android.view.KeyEvent.ACTION_UP:
					if( time-buttonDown < 900 )
					{
						count();
					}
					else
					{
						vibrator.vibrate( 3000 );

						if( started )
							stop();
						else
							start();
					}
					buttonDown = 0;
					break;
			}
		}
	}

	private SharedPreferences getSharedPreferences()
	{
		return getSharedPreferences(
			CounterPreferenceActivity.SHARED_PREFERENCES_NAME,
			0 );
	}

	private class LocationRecorder implements LocationListener
	{
		@Override
		public void onLocationChanged( Location location )
		{
			if( lastLocation != null &&
				lastLocation.getTime() != location.getTime() )
			{
				float d = location.distanceTo( lastLocation );

				// if location has no bearing and speed and its
				// accuracy is bigger than the last one then
				// consolidate positions because it's likely
				// the device isn't really moving at all
				if( !location.hasBearing() &&
					!location.hasSpeed() &&
					location.getAccuracy() > lastLocation.getAccuracy() )
					average( lastLocation, location );
				else
					distance += d;
			}

			if( location != null )
				lastLocation = location;
		}

		@Override
		public void onStatusChanged(
			String provider,
			int status,
			Bundle extras )
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

		private void average( Location a, Location b )
		{
			a.setLatitude( average(
				a.getLatitude(),
				b.getLatitude() ) );

			a.setLongitude( average(
				a.getLongitude(),
				b.getLongitude() ) );
		}

		private double average( double a, double b )
		{
			double diff = ((a-b)+360.0) % 360.0;

			if( diff > 180.0 )
				diff -= 360.0;

			double average = (b+diff/2.0) % 360.0;

			if( average < .0 )
				average += 360.0;

			return average;
		}
	}
}
