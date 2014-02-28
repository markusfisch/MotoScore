package de.markusfisch.android.counter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class CounterPreferenceActivity
	extends PreferenceActivity
	implements OnSharedPreferenceChangeListener
{
	public static final String SHARED_PREFERENCES_NAME = "CounterSettings";
	public static final String USE_MEDIA_BUTTON = "use_media_button";
	public static final String USE_KNOCK_DETECTION = "use_knock_detection";
	public static final String AUTO_DETECT_DRIVING = "auto_detect_driving";

	private boolean dirty;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		getPreferenceManager().setSharedPreferencesName(
			SHARED_PREFERENCES_NAME );

		addPreferencesFromResource( R.xml.preferences );
	}

	@Override
	public void onResume()
	{
		super.onResume();

		dirty = false;

		getPreferenceScreen()
			.getSharedPreferences()
			.registerOnSharedPreferenceChangeListener( this );
	}

	@Override
	public void onPause()
	{
		super.onPause();

		getPreferenceScreen()
			.getSharedPreferences()
			.unregisterOnSharedPreferenceChangeListener( this );

		if( dirty )
		{
			Toast.makeText(
				this,
				R.string.restart_service,
				Toast.LENGTH_SHORT ).show();

			Intent intent = new Intent(
				this,
				CounterService.class );

			stopService( intent );
			startService( intent );
		}
	}

	@Override
	public void onSharedPreferenceChanged(
		SharedPreferences sharedPreferences,
		String key )
	{
		dirty = true;
	}
}
