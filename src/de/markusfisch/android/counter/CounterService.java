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

import java.util.ArrayList;
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
	public boolean started = false;
	public Date rideStart = null;
	public int errors = 0;
	public float distance = 0;

	private static final int MILLISECONDS_BETWEEN_UPDATES = 30000;
	private static final int NANOSECONDS_BETWEEN_UPDATES = MILLISECONDS_BETWEEN_UPDATES*1000000;
	private static final int METERS_BETWEEN_UPDATES = 20;
	private static final int MINIMUM_ACCURACY = 100;

	private final IBinder binder = new Binder();

	private Notifications notifications;

	private LocationManager locationManager = null;
	private LocationRecorder locationRecorder = new LocationRecorder();
	private long lastLocationUpdate;
	private float lastLocationAccuracy;
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

		dataSource = new CounterDataSource( context );
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
		errors = 0;
		distance = 0;
		wayPoints.clear();

		// only use last known location if it's fresh
		{
			final long fresh = 1000000000*60;
			final long now = android.os.SystemClock.elapsedRealtimeNanos();
			Location location = null;

			if( (
					(location = locationManager.getLastKnownLocation(
						LocationManager.GPS_PROVIDER )) == null ||
					now-location.getElapsedRealtimeNanos() > fresh
				) &&
				(
					(location = locationManager.getLastKnownLocation(
						LocationManager.NETWORK_PROVIDER )) == null ||
					now-location.getElapsedRealtimeNanos() > fresh
				) &&
				(
					(location = locationManager.getLastKnownLocation(
						LocationManager.PASSIVE_PROVIDER )) == null ||
					now-location.getElapsedRealtimeNanos() > fresh
				) )
			{
				lastLocationUpdate = now;
				lastLocationAccuracy = MINIMUM_ACCURACY;
			}
			else
			{
				wayPoints.add( location );

				lastLocationUpdate = location.getElapsedRealtimeNanos();
				lastLocationAccuracy = location.getAccuracy();
			}
		}

		if( locationManager != null )
		{
			locationManager.requestLocationUpdates(
				LocationManager.GPS_PROVIDER,
				MILLISECONDS_BETWEEN_UPDATES,
				METERS_BETWEEN_UPDATES,
				locationRecorder );

			locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER,
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
			listener.onCounterUpdate();

// DEBUG
try
{
	java.io.File sdCard = android.os.Environment.getExternalStorageDirectory();
	java.io.File dir = new java.io.File( sdCard.getAbsolutePath()+"/motocounter" );
	dir.mkdirs();
	java.io.File file = new java.io.File( dir, "ride.kml" );
	java.io.FileOutputStream out = null;

	try
	{
		out = new java.io.FileOutputStream( file );

		String s =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
			"<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n"+
			"<Placemark>\n"+
			"<name>Ride</name>\n"+
			"<description>Ride way points.</description>\n"+
			"<LineString>\n"+
			"<tessellate>1</tessellate>\n"+
			"<coordinates>";

		for( int n = 0, l = wayPoints.size(); n < l; ++n )
		{
			Location loc = wayPoints.get( n );

			s += loc.getLongitude()+","+loc.getLatitude()+",0\n";
		}

		s +=
			"</coordinates>\n"+
			"</LineString>\n"+
			"</Placemark>\n"+
			"</kml>\n";

		byte bytes[] = s.getBytes();

		out.write( bytes, 0, bytes.length );
	}
	finally
	{
		if( out != null )
			out.close();
	}
}
catch( Exception e )
{
	Toast.makeText(
		getApplicationContext(),
		"Cannot write to SD card!",
		Toast.LENGTH_LONG ).show();
}
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

	private void vibrate( int milliseconds )
	{
		if( !getSharedPreferences().getBoolean(
				CounterPreferenceActivity.HAPTIC_FEEDBACK,
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
			errors,
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
			final float accuracy = location.getAccuracy();

			if( lastLocationUpdate+NANOSECONDS_BETWEEN_UPDATES <=
				location.getElapsedRealtimeNanos() )
			{
				wayPoints.add( location );

				lastLocationAccuracy = MINIMUM_ACCURACY;
				lastLocationUpdate = location.getElapsedRealtimeNanos();
			}
			else if(
				accuracy < lastLocationAccuracy ||
				wayPoints.size() == 0 )
			{
				wayPoints.add( location );
				lastLocationAccuracy = accuracy;
			}
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
