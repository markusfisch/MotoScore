package de.markusfisch.android.motoscore;

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

import java.util.ArrayList;
import java.util.Date;

public class MotoScoreService
	extends Service
{
	public interface MotoScoreServiceListener
	{
		public void onMotoScoreUpdate();
	}

	public class Binder extends android.os.Binder
	{
		public MotoScoreService getService()
		{
			return MotoScoreService.this;
		}
	};

	public static final String COMMAND = "command";
	public static final String STATE = "state";
	public static final String ACTION = "action";
	public static final String TIME = "time";

	public static final int COMMAND_STATE = 0;
	public static final int COMMAND_ACTION = 1;
	public static final int COMMAND_CONFIGURATION = 2;

	public MotoScoreServiceListener listener = null;
	public Date rideStart = new Date();
	public float distance = 0;
	public int mistakes = 0;

	private static final int MILLISECONDS_BETWEEN_UPDATES = 30000;
	private static final int METERS_BETWEEN_UPDATES = 20;
	private static final int MINIMUM_ACCURACY = 100;

	private final IBinder binder = new Binder();

	private Notifications notifications;

	private LocationManager locationManager = null;
	private LocationRecorder locationRecorder = new LocationRecorder();
	private Location lastLocation = null;
	private long rideId = 0;

	private HeadsetReceiver headsetReceiver;
	private AudioManager audioManager;
	private ComponentName remoteControlReceiver = null;
	private long buttonDown = 0;

	private Vibrator vibrator;

	@Override
	public void onCreate()
	{
		final Context context = getApplicationContext();

		notifications = new Notifications( context );

		locationManager = (LocationManager)
			getSystemService( Context.LOCATION_SERVICE );

		headsetReceiver = new HeadsetReceiver();
		registerReceiver(
			headsetReceiver,
			new IntentFilter( Intent.ACTION_HEADSET_PLUG ) );

		audioManager = (AudioManager)
			getSystemService( Context.AUDIO_SERVICE );

		vibrator = (Vibrator)
			getSystemService( Context.VIBRATOR_SERVICE );
	}

	@Override
	public void onDestroy()
	{
		unregisterReceiver( headsetReceiver );

		stop();
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
				case COMMAND_CONFIGURATION:
					handleConfigurationCommand( intent );
					break;
			}

		return START_STICKY;
	}

	@Override
	public IBinder onBind( Intent intent )
	{
		return binder;
	}

	public boolean recording()
	{
		return rideId > 0;
	}

	public void start()
	{
		if( recording() )
			return;

		registerMediaButton();

		rideStart = new Date();
		mistakes = 0;
		distance = 0;

		if( !MotoScoreApplication.dataSource.ready() ||
			(rideId = MotoScoreApplication.dataSource.insertRide(
				rideStart )) < 1 )
		{
			Toast.makeText(
				getApplicationContext(),
				R.string.error_data_source,
				Toast.LENGTH_LONG ).show();

			return;
		}

		record( getLastKnownLocation() );

		if( locationManager != null )
			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				MILLISECONDS_BETWEEN_UPDATES,
				METERS_BETWEEN_UPDATES,
				locationRecorder );

		if( showNotification() )
			notifications.recording.show();

		if( listener != null )
			listener.onMotoScoreUpdate();
	}

	public void stop()
	{
		if( !recording() )
			return;

		unregisterMediaButton();

		if( locationManager != null )
			locationManager.removeUpdates( locationRecorder );

		notifications.recording.hide();

		MotoScoreApplication.dataSource.updateRide(
			rideId,
			new Date(),
			mistakes,
			distance );

		rideId = 0;

		if( listener != null )
			listener.onMotoScoreUpdate();
	}

	public void count()
	{
		synchronized( this )
		{
			++mistakes;

			if( listener != null )
				listener.onMotoScoreUpdate();

			vibrate( 1000 );
		}
	}

	private void vibrate( int milliseconds )
	{
		if( !hapticFeedback() )
			return;

		vibrator.vibrate( milliseconds );
	}

	private void handleStateCommand( Intent intent )
	{
		if( !recording() )
			return;

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

	public void registerMediaButton()
	{
		if( audioManager == null ||
			// don't register again
			remoteControlReceiver != null ||
			!useMediaButton() )
			return;

		unregisterMediaButton();

		remoteControlReceiver = new ComponentName(
			getPackageName(),
			RemoteControlReceiver.class.getName() );

		audioManager.registerMediaButtonEventReceiver(
			remoteControlReceiver );
	}

	public void unregisterMediaButton()
	{
		if( remoteControlReceiver == null )
			return;

		audioManager.unregisterMediaButtonEventReceiver(
			remoteControlReceiver );

		remoteControlReceiver = null;
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
					if( !recording() )
						start();

					count();
				}
				else
				{
					vibrate( 3000 );

					if( recording() )
						stop();
					else
						start();
				}
				buttonDown = 0;
				break;
		}
	}

	private void handleConfigurationCommand( Intent intent )
	{
		if( !recording() )
			return;

		if( useMediaButton() )
			registerMediaButton();
		else
			unregisterMediaButton();

		if( showNotification() )
			notifications.recording.show();
		else
			notifications.recording.hide();
	}

	private boolean useMediaButton()
	{
		return getSharedPreferences().getBoolean(
			MotoScorePreferenceActivity.USE_MEDIA_BUTTON,
			true );
	}

	private boolean showNotification()
	{
		return getSharedPreferences().getBoolean(
			MotoScorePreferenceActivity.SHOW_NOTIFICATION,
			true );
	}

	private boolean hapticFeedback()
	{
		return getSharedPreferences().getBoolean(
			MotoScorePreferenceActivity.HAPTIC_FEEDBACK,
			true );
	}

	private SharedPreferences getSharedPreferences()
	{
		return getSharedPreferences(
			MotoScorePreferenceActivity.SHARED_PREFERENCES_NAME,
			0 );
	}

	private Location getLastKnownLocation()
	{
		Location locations[] = {
			locationManager.getLastKnownLocation(
				LocationManager.GPS_PROVIDER ),
			locationManager.getLastKnownLocation(
				LocationManager.NETWORK_PROVIDER ),
			locationManager.getLastKnownLocation(
				LocationManager.PASSIVE_PROVIDER ) };
		long youngestTime = 0;
		Location youngest = null;

		for( int n = locations.length; n-- > 0; )
		{
			Location l = locations[n];

			if( l == null )
				continue;

			long t = l.getTime();

			if( t > youngestTime )
			{
				youngestTime = t;
				youngest = l;
			}
		}

		// use location only if
		if( youngest != null &&
			// it's not older than 10 minutes
			java.lang.System.currentTimeMillis()-youngestTime < 600000 &&
			// its accuracy is lower than MINIMUM_ACCURACY meters
			youngest.getAccuracy() < MINIMUM_ACCURACY )
			return youngest;

		return null;
	}

	private void record( Location location )
	{
		if( location == null ||
			location.getAccuracy() < MINIMUM_ACCURACY )
			return;

		if( recording() &&
			MotoScoreApplication.dataSource.ready() )
			MotoScoreApplication.dataSource.insertWaypoint(
				rideId,
				location.getTime(),
				location.getLatitude(),
				location.getLongitude(),
				location.getAccuracy(),
				location.getAltitude(),
				location.getBearing(),
				location.getSpeed() );

		if( lastLocation == null )
			lastLocation = location;
		else
		{
			distance += lastLocation.distanceTo( location );
			lastLocation = location;
		}
	}

	private class LocationRecorder implements LocationListener
	{
		@Override
		public void onLocationChanged( Location location )
		{
			record( location );
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
