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
		public void onMistakeUpdate();
		public void onDataUpdate();
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

	public MotoScorePreferences preferences;
	public MotoScoreServiceListener listener = null;
	public Date rideStart = new Date();
	public float distance = 0;
	public float averageSpeed = 0;
	public int mistakes = 0;
	public int waypoints = 0;

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
	private int minimumAccuracy = 100;
	private float speeds = 0;

	private Vibrator vibrator;

	@Override
	public void onCreate()
	{
		final Context context = getApplicationContext();

		preferences = new MotoScorePreferences( context );
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

		// register media button to be able to start as soon
		// as the button is pressed
		registerMediaButton();
	}

	@Override
	public void onDestroy()
	{
		unregisterMediaButton();
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

	public void registerMediaButton()
	{
		unregisterMediaButton();

		if( audioManager == null ||
			!preferences.useMediaButton() )
			return;

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

	public boolean recording()
	{
		return rideId > 0;
	}

	public void start()
	{
		if( recording() )
			return;

		rideStart = new Date();
		distance = 0;
		averageSpeed = 0;
		speeds = 0;
		mistakes = 0;
		waypoints = 0;

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

		minimumAccuracy = preferences.minimumAccuracy();

		if( locationManager != null )
			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				preferences.secondsBetweenUpdates(),
				preferences.metersBetweenUpdates(),
				locationRecorder );

		if( preferences.showNotification() )
			notifications.recording.show();

		if( listener != null )
			listener.onDataUpdate();
	}

	public void stop()
	{
		if( !recording() )
			return;

		if( locationManager != null )
			locationManager.removeUpdates( locationRecorder );

		notifications.recording.hide();

		MotoScoreApplication.dataSource.updateRide(
			rideId,
			new Date(),
			mistakes,
			distance,
			averageSpeed );

		rideId = 0;

		if( listener != null )
			listener.onDataUpdate();
	}

	public void count()
	{
		synchronized( this )
		{
			++mistakes;

			if( listener != null )
				listener.onMistakeUpdate();

			vibrate( 1000 );
		}
	}

	private void vibrate( int milliseconds )
	{
		if( !preferences.hapticFeedback() )
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
					if( recording() )
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

		if( preferences.useMediaButton() )
			registerMediaButton();
		else
			unregisterMediaButton();

		if( preferences.showNotification() )
			notifications.recording.show();
		else
			notifications.recording.hide();
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
			youngest.getAccuracy() < minimumAccuracy )
			return youngest;

		return null;
	}

	private String uniqueDeviceId( Context context )
	{
		return android.provider.Settings.Secure.getString(
			context.getContentResolver(),
			android.provider.Settings.Secure.ANDROID_ID );
	}

	private void record( Location location )
	{
		if( location == null ||
			location.getAccuracy() > minimumAccuracy )
			return;

		if( recording() &&
			MotoScoreApplication.dataSource.ready() )
		{
			float meters = 0f;
			float speed = 0f;

			if( lastLocation != null )
				meters = lastLocation.distanceTo( location );

			if( location.hasSpeed() )
				speed = location.getSpeed();
			else if( lastLocation != null )
			{
				double seconds;

				if( android.os.Build.VERSION.SDK_INT >=
						android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 )
					seconds = (location.getElapsedRealtimeNanos()-
						lastLocation.getElapsedRealtimeNanos())/
						1000000000d;
				else
					seconds = (location.getTime()-
						lastLocation.getTime())/
						1000d;

				speed = (float)(meters/seconds);
			}

			MotoScoreApplication.dataSource.insertWaypoint(
				rideId,
				location.getTime(),
				location.getLatitude(),
				location.getLongitude(),
				location.getAccuracy(),
				location.getAltitude(),
				location.getBearing(),
				speed );

			++waypoints;

			if( lastLocation != null )
			{
				speeds += speed;
				averageSpeed = speeds/waypoints;
				distance += meters;
			}
		}

		lastLocation = location;
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
