package de.markusfisch.android.counter;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.widget.Toast;

import java.util.Date;

public class CounterService
	extends Service
{
	public interface CounterServiceListener
	{
		public void onCounterUpdate();
	}

	public class Binder extends android.os.Binder
	{
		public CounterService getService()
		{
			return CounterService.this;
		}
	};

	public static final String COMMAND = "command";
	public static final String STATE = "state";
	public static final String ACTION = "action";
	public static final String TIME = "time";

	public static final int COMMAND_STATE = 0;
	public static final int COMMAND_ACTION = 1;

	public CounterDataSource dataSource = null;
	public CounterServiceListener listener = null;
	public int errors = 0;
	public float distance = 0;
	public boolean started = false;
	public Date rideStart = new Date();

	private static final int MILLISECONDS_BETWEEN_UPDATES = 10000;
	private static final int METERS_BETWEEN_UPDATES = 20;
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
	private long lastLocationUpdate;

	@Override
	public void onCreate()
	{
		dataSource = new CounterDataSource( getApplicationContext() );
		dataSource.open();

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
			switch( intent.getIntExtra( COMMAND, -1 ) )
			{
				case COMMAND_STATE:
					handleStateCommand( intent );
					break;
				case COMMAND_ACTION:
					handleActionCommand( intent );
					break;
			}

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
				listener.onCounterUpdate();

			vibrate( 1000 );
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

		save();

		if( listener != null )
			listener.onCounterUpdate();
	}

	public void start()
	{
		if( started )
			return;

		rideStart = new Date();
		errors = 0;
		distance = 0;

		if( locationManager != null )
		{
			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				MILLISECONDS_BETWEEN_UPDATES,
				METERS_BETWEEN_UPDATES,
				locationRecorder );
		}

		if( getSharedPreferences().getBoolean(
				CounterPreferenceActivity.SHOW_NOTIFICATION,
				true ) )
			notifications.counting.show();

		started = true;

		if( listener != null )
			listener.onCounterUpdate();
	}

	private void save()
	{
		if( !dataSource.ready() )
		{
			Toast.makeText(
				getApplicationContext(),
				R.string.error_data_source,
				Toast.LENGTH_LONG ).show();

			return;
		}

		dataSource.insert(
			rideStart,
			new Date(),
			errors,
			distance );
	}

	private void vibrate( int milliseconds )
	{
		if( !getSharedPreferences().getBoolean(
				CounterPreferenceActivity.HAPTIC_FEEDBACK,
				true ) )
			return;

		vibrator.vibrate( milliseconds );
	}

	private void handleStateCommand( Intent intent )
	{
		switch( intent.getIntExtra( STATE, -1 ) )
		{
			case 0:
				unregisterMediaButton();
				break;
			case 1:
				registerMediaButton();
				break;
		}
	}

	private void handleActionCommand( Intent intent )
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
					if( !started )
						start();

					count();
				}
				else
				{
					vibrate( 3000 );

					if( started )
						stop();
					else
						start();
				}
				buttonDown = 0;
				break;
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
			if( location.getAccuracy() < METERS_BETWEEN_UPDATES &&
				lastLocationUpdate+MILLISECONDS_BETWEEN_UPDATES <=
					location.getTime() )
			{
				if( lastLocation != null )
				{
					float d = lastLocation.distanceTo( location );

					distance += d;
				}

				lastLocation = location;
			}

			lastLocationUpdate = location.getTime();
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
	}
}
