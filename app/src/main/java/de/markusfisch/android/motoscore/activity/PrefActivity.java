package de.markusfisch.android.motoscore.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.widget.Toast;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.export.DatabaseExporter;
import de.markusfisch.android.motoscore.preference.Preferences;
import de.markusfisch.android.motoscore.service.MotoScoreService;

public class PrefActivity extends AppCompatPreferenceActivity {
	private final OnSharedPreferenceChangeListener listener =
			new OnSharedPreferenceChangeListener() {
				@Override
				public void onSharedPreferenceChanged(
						SharedPreferences sharedPreferences,
						String key) {
					dirty = true;
				}
			};

	private boolean dirty;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		getPreferenceManager().setSharedPreferencesName(
				Preferences.SHARED_PREFERENCES_NAME);
		addPreferencesFromResource(R.xml.preferences);

		findPreference(Preferences.EXPORT_DATABASE).setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference preference) {
						Context context = PrefActivity.this;
						Toast.makeText(context,
								DatabaseExporter.exportDatabase(context),
								Toast.LENGTH_LONG).show();
						return true;
					}
				});
	}

	@Override
	protected void onResume() {
		super.onResume();
		dirty = false;
		getPreferenceScreen().getSharedPreferences()
				.registerOnSharedPreferenceChangeListener(listener);
	}

	@Override
	protected void onPause() {
		super.onPause();
		getPreferenceScreen().getSharedPreferences()
				.unregisterOnSharedPreferenceChangeListener(listener);

		if (dirty) {
			Intent intent = new Intent(this, MotoScoreService.class);
			intent.putExtra(MotoScoreService.COMMAND,
					MotoScoreService.COMMAND_CONFIGURATION);
			startService(intent);
		}
	}

	@Override
	protected boolean isValidFragment(String fragmentName) {
		return false;
	}
}
