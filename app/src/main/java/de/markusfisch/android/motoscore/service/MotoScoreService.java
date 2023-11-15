package de.markusfisch.android.motoscore.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import androidx.annotation.NonNull;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.Date;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.app.MotoScoreApp;
import de.markusfisch.android.motoscore.notification.Notifications;
import de.markusfisch.android.motoscore.receiver.HeadsetReceiver;
import de.markusfisch.android.motoscore.receiver.RemoteControlReceiver;

public class MotoScoreService extends Service {
	public interface ServiceListener {
		void onMistakeUpdate();

		void onDataUpdate();
	}

	public class Binder extends android.os.Binder {
		public MotoScoreService getService() {
			return MotoScoreService.this;
		}
	}

	public static final String COMMAND = "command";
	public static final String STATE = "state";
	public static final String ACTION = "action";
	public static final String TIME = "time";

	public static final int COMMAND_STATE = 0;
	public static final int COMMAND_ACTION = 1;
	public static final int COMMAND_CONFIGURATION = 2;

	public ServiceListener listener = null;
	public Date rideStart = new Date();
	public float distance = 0;
	public float averageSpeed = 0;
	public int mistakes = 0;
	public int waypoints = 0;

	private final IBinder binder = new Binder();
	private final LocationRecorder locationRecorder = new LocationRecorder();

	private Notifications notifications;
	private LocationManager locationManager;
	private Location lastLocation;
	private long rideId = 0;

	private HeadsetReceiver headsetReceiver;
	private AudioManager audioManager;
	private Vibrator vibrator;
	private ComponentName remoteControlReceiver;
	private MediaSession mediaSession;
	private long buttonDown = 0;
	private int minimumAccuracy = 100;
	private float speeds = 0;

	@Override
	public void onCreate() {
		notifications = new Notifications(this);
		locationManager = (LocationManager)
				getSystemService(Context.LOCATION_SERVICE);

		headsetReceiver = new HeadsetReceiver();
		registerReceiver(headsetReceiver,
				new IntentFilter(Intent.ACTION_HEADSET_PLUG));

		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

		// Register media button to be able to start as soon
		// as the button is pressed.
		registerMediaButton();
	}

	@Override
	public void onDestroy() {
		unregisterMediaButton();
		unregisterReceiver(headsetReceiver);
		stop();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			switch (intent.getIntExtra(COMMAND, -1)) {
				case -1:
					start();
					break;
				case COMMAND_STATE:
					handleStateCommand(intent);
					break;
				case COMMAND_ACTION:
					handleActionCommand(intent);
					break;
				case COMMAND_CONFIGURATION:
					handleConfigurationCommand();
					break;
			}
		}
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	public void registerMediaButton() {
		unregisterMediaButton();

		if (!MotoScoreApp.preferences.useMediaButton()) {
			return;
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			if (audioManager != null) {
				remoteControlReceiver = new ComponentName(getPackageName(),
						RemoteControlReceiver.class.getName());
				audioManager.registerMediaButtonEventReceiver(
						remoteControlReceiver);
			}
		} else {
			mediaSession = new MediaSession(this, "ride");
			mediaSession.setCallback(new MediaSession.Callback() {
				@Override
				public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
					KeyEvent event = RemoteControlReceiver.getKeyEvent(
							mediaButtonIntent);
					if (event != null) {
						handleActionCommand(event.getAction(),
								event.getEventTime());
					}
					return super.onMediaButtonEvent(mediaButtonIntent);
				}
			});
			mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
					MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
			mediaSession.setPlaybackState(new PlaybackState.Builder()
					.setActions(
							PlaybackState.ACTION_PLAY |
									PlaybackState.ACTION_PLAY_PAUSE |
									PlaybackState.ACTION_PAUSE |
									PlaybackState.ACTION_SKIP_TO_NEXT |
									PlaybackState.ACTION_SKIP_TO_PREVIOUS)
					.setState(PlaybackState.STATE_STOPPED,
							PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0)
					.build());
			mediaSession.setActive(true);
			// On Android O and better an app is required to play some
			// sound in order to get media button events.
			playSound(this, R.raw.silent_sound);
		}
	}

	public void unregisterMediaButton() {
		if (remoteControlReceiver != null) {
			audioManager.unregisterMediaButtonEventReceiver(
					remoteControlReceiver);
			remoteControlReceiver = null;
		} else if (mediaSession != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				mediaSession.release();
			}
			mediaSession = null;
		}
	}

	public boolean isRecording() {
		return rideId > 0;
	}

	public void start() {
		if (isRecording()) {
			return;
		}

		reset();

		if (!MotoScoreApp.db.isOpen() ||
				(rideId = MotoScoreApp.db.insertRide(rideStart)) < 1) {
			Toast.makeText(getApplicationContext(),
					R.string.error_data_source,
					Toast.LENGTH_LONG).show();
			return;
		}

		try {
			record(getLastKnownLocation());
		} catch (SecurityException e) {
			// User has denied access to location updates.
			cancelRide();
			return;
		}

		minimumAccuracy = MotoScoreApp.preferences.minimumAccuracy();

		if (locationManager != null) {
			try {
				locationManager.requestLocationUpdates(
						LocationManager.GPS_PROVIDER,
						MotoScoreApp.preferences.secondsBetweenUpdates(),
						MotoScoreApp.preferences.metersBetweenUpdates(),
						locationRecorder);
			} catch (SecurityException e) {
				// User has denied access to location updates.
				cancelRide();
				return;
			}
		}

		startForeground(1, notifications.recording.getNotification());

		if (listener != null) {
			listener.onDataUpdate();
		}
	}

	private void cancelRide() {
		MotoScoreApp.db.removeRide(rideId);
		rideId = 0;
		Toast.makeText(getApplicationContext(),
				R.string.error_insufficient_permissions,
				Toast.LENGTH_LONG).show();
	}

	public void stop() {
		if (!isRecording()) {
			return;
		}

		if (locationManager != null) {
			locationManager.removeUpdates(locationRecorder);
		}

		MotoScoreApp.db.updateRide(
				rideId,
				new Date(),
				mistakes,
				distance,
				averageSpeed);
		rideId = 0;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			stopForeground(Service.STOP_FOREGROUND_REMOVE);
		} else {
			stopForeground(true);
		}

		if (listener != null) {
			listener.onDataUpdate();
		}

		stopSelf();
	}

	public synchronized void count() {
		++mistakes;
		if (listener != null) {
			listener.onMistakeUpdate();
		}
		vibrate(1000);
	}

	private void reset() {
		rideStart = new Date();
		distance = 0;
		averageSpeed = 0;
		speeds = 0;
		mistakes = 0;
		waypoints = 0;
	}

	private void vibrate(int milliseconds) {
		if (MotoScoreApp.preferences.hapticFeedback()) {
			vibrator.vibrate(milliseconds);
		}
	}

	private void handleStateCommand(Intent intent) {
		if (!isRecording()) {
			return;
		}
		switch (intent.getIntExtra(STATE, -1)) {
			case 0:
				unregisterMediaButton();
				break;
			case 1:
				registerMediaButton();
				break;
		}
	}

	private void handleActionCommand(Intent intent) {
		handleActionCommand(intent.getIntExtra(ACTION, -1),
				intent.getLongExtra(TIME, 1));
	}

	private void handleActionCommand(int action, long time) {
		switch (action) {
			case KeyEvent.ACTION_DOWN:
				// There may come multiple ACTION_DOWNs
				// before there's a ACTION_UP but only
				// the very first one is interesting.
				if (buttonDown == 0) {
					buttonDown = time;
				}
				break;
			case KeyEvent.ACTION_UP:
				if (time - buttonDown < 900) {
					if (isRecording()) {
						count();
					}
				} else {
					vibrate(3000);
					if (isRecording()) {
						stop();
					} else {
						start();
					}
				}
				buttonDown = 0;
				break;
		}
	}

	private void handleConfigurationCommand() {
		if (!isRecording()) {
			return;
		}

		if (MotoScoreApp.preferences.useMediaButton()) {
			registerMediaButton();
		} else {
			unregisterMediaButton();
		}
	}

	private Location getLastKnownLocation() throws SecurityException {
		Location[] locations = {
				locationManager.getLastKnownLocation(
						LocationManager.GPS_PROVIDER),
				locationManager.getLastKnownLocation(
						LocationManager.NETWORK_PROVIDER),
				locationManager.getLastKnownLocation(
						LocationManager.PASSIVE_PROVIDER)
		};
		long youngestTime = 0;
		Location youngest = null;

		for (int n = locations.length; n-- > 0; ) {
			Location l = locations[n];

			if (l == null) {
				continue;
			}

			long t = l.getTime();

			if (t > youngestTime) {
				youngestTime = t;
				youngest = l;
			}
		}

		// Use location only if
		if (youngest != null &&
				// it's not older than 10 minutes and
				java.lang.System.currentTimeMillis() - youngestTime < 600000 &&
				// its accuracy is lower than MINIMUM_ACCURACY meters.
				youngest.getAccuracy() < minimumAccuracy) {
			return youngest;
		}

		return null;
	}

	private void record(Location location) {
		if (location == null || location.getAccuracy() > minimumAccuracy) {
			return;
		}

		if (isRecording() && MotoScoreApp.db.isOpen()) {
			float meters = 0f;
			float speed = 0f;

			if (lastLocation != null) {
				meters = lastLocation.distanceTo(location);
			}

			if (location.hasSpeed()) {
				speed = location.getSpeed();
			} else if (lastLocation != null) {
				double seconds;

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
					seconds = (location.getElapsedRealtimeNanos() -
							lastLocation.getElapsedRealtimeNanos()) /
							1000000000d;
				} else {
					seconds = (location.getTime() -
							lastLocation.getTime()) /
							1000d;
				}

				speed = (float) (meters / seconds);
			}

			MotoScoreApp.db.insertWaypoint(
					rideId,
					location.getTime(),
					location.getLatitude(),
					location.getLongitude(),
					location.getAccuracy(),
					location.getAltitude(),
					location.getBearing(),
					speed);

			++waypoints;

			if (lastLocation != null) {
				speeds += speed;
				averageSpeed = speeds / waypoints;
				distance += meters;
			}
		}

		lastLocation = location;
	}

	private class LocationRecorder implements LocationListener {
		@Override
		public void onLocationChanged(@NonNull Location location) {
			record(location);
		}

		@Override
		public void onStatusChanged(
				String provider,
				int status,
				Bundle extras) {
		}

		@Override
		public void onProviderEnabled(@NonNull String provider) {
		}

		@Override
		public void onProviderDisabled(@NonNull String provider) {
		}
	}

	private static void playSound(Context context, int resId) {
		MediaPlayer mediaPlayer = MediaPlayer.create(context, resId);
		mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mediaPlayer) {
				mediaPlayer.release();
			}
		});
		mediaPlayer.start();
	}
}
