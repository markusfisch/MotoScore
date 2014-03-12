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

	public MotoScoreDataSource dataSource = null;
	public MotoScoreServiceListener listener = null;
	public boolean started = false;
	public Date rideStart = null;
	public int mistakes = 0;
	public float distance = 0;

	private static final int MILLISECONDS_BETWEEN_UPDATES = 30000;
	private static final int METERS_BETWEEN_UPDATES = 20;

	private final IBinder binder = new Binder();

	private Notifications notifications;

	private LocationManager locationManager = null;
	private LocationRecorder locationRecorder = new LocationRecorder();
	private ArrayList<Location> wayPoints = new ArrayList<Location>();

	private HeadsetReceiver headsetReceiver;
	private AudioManager audioManager;
	private ComponentName remoteControlReceiver = null;
	private long buttonDown = 0;

	private Vibrator vibrator;

	@Override
	public void onCreate()
	{
		final Context context = getApplicationContext();

		dataSource = new MotoScoreDataSource( context );
		dataSource.open();

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

	public void start()
	{
		if( started )
			return;

		rideStart = new Date();
		mistakes = 0;
		distance = 0;
		wayPoints.clear();

		// get last location
		{
			Location location;

			if( (
					(location = locationManager.getLastKnownLocation(
						LocationManager.GPS_PROVIDER )) != null ||
					(location = locationManager.getLastKnownLocation(
						LocationManager.NETWORK_PROVIDER )) != null ||
					(location = locationManager.getLastKnownLocation(
						LocationManager.PASSIVE_PROVIDER )) != null
				) &&
				// but only use it if's fresh
				java.lang.System.currentTimeMillis()-
					location.getTime() < 60000 )
				wayPoints.add( location );
		}

		if( locationManager != null )
			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				MILLISECONDS_BETWEEN_UPDATES,
				METERS_BETWEEN_UPDATES,
				locationRecorder );

		if( getSharedPreferences().getBoolean(
				MotoScorePreferenceActivity.SHOW_NOTIFICATION,
				true ) )
			notifications.counting.show();

		started = true;

		if( listener != null )
			listener.onMotoScoreUpdate();
	}

	public void stop()
	{
		if( !started )
			return;

		if( locationManager != null )
			locationManager.removeUpdates( locationRecorder );

		notifications.counting.hide();
		started = false;

		distance = calculateDistance();
		save();

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
		if( !getSharedPreferences().getBoolean(
				MotoScorePreferenceActivity.HAPTIC_FEEDBACK,
				true ) )
			return;

		vibrator.vibrate( milliseconds );
	}

	private float calculateDistance()
	{
		int size = wayPoints.size();

		if( size < 2 )
			return 0;

		float d = 0;
		Location last = wayPoints.get( 0 );

		for( int n = 1; n < size; ++n )
		{
			final Location location = wayPoints.get( n );

			d += last.distanceTo( location );
			last = location;
		}

		return d;
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
			mistakes,
			distance );
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
				MotoScorePreferenceActivity.USE_MEDIA_BUTTON,
				true ) )
			return;

		unregisterMediaButton();

		remoteControlReceiver = new ComponentName(
			getPackageName(),
			RemoteControlReceiver.class.getName() );

		audioManager.registerMediaButtonEventReceiver(
			remoteControlReceiver );
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
			MotoScorePreferenceActivity.SHARED_PREFERENCES_NAME,
			0 );
	}

	private class LocationRecorder implements LocationListener
	{
		@Override
		public void onLocationChanged( Location location )
		{
			wayPoints.add( location );
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
