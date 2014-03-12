package de.markusfisch.android.motoscore;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.widget.Toast;

public class MotoScorePreferenceActivity
	extends PreferenceActivity
	implements OnSharedPreferenceChangeListener
{
	public static final String SHARED_PREFERENCES_NAME = "MotoScoreSettings";
	public static final String USE_MEDIA_BUTTON = "use_media_button";
	public static final String HAPTIC_FEEDBACK = "haptic_feedback";
	public static final String SHOW_NOTIFICATION = "show_notification";

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
				MotoScoreService.class );

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
