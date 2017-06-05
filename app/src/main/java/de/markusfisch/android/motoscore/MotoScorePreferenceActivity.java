package de.markusfisch.android.motoscore;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;

public class MotoScorePreferenceActivity
	extends PreferenceActivity
	implements OnSharedPreferenceChangeListener
{
	private boolean dirty;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		getPreferenceManager().setSharedPreferencesName(
			MotoScorePreferences.SHARED_PREFERENCES_NAME );

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
			final Intent intent = new Intent(
				this,
				MotoScoreService.class );

			intent.putExtra(
				MotoScoreService.COMMAND,
				MotoScoreService.COMMAND_CONFIGURATION );

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
