package de.markusfisch.android.motoscore.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.widget.Toast;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.io.DatabaseExporter;
import de.markusfisch.android.motoscore.io.DatabaseImporter;
import de.markusfisch.android.motoscore.preference.Preferences;
import de.markusfisch.android.motoscore.service.MotoScoreService;

public class PreferenceActivity extends AppCompatPreferenceActivity {
	private static final int PICK_FILE_RESULT_CODE = 1;

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
	protected void onActivityResult(int requestCode, int resultCode,
			Intent resultData) {
		if (requestCode == PICK_FILE_RESULT_CODE &&
				resultCode == RESULT_OK && resultData != null) {
			Toast.makeText(this,
					DatabaseImporter.importDatabase(this, resultData.getData()) ?
							R.string.successfully_imported :
							R.string.no_rides,
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		getPreferenceManager().setSharedPreferencesName(
				Preferences.SHARED_PREFERENCES_NAME);
		addPreferencesFromResource(R.xml.preferences);

		findPreference("export_db").setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference preference) {
						Context context = PreferenceActivity.this;
						Toast.makeText(context,
								DatabaseExporter.exportDatabase(context),
								Toast.LENGTH_LONG).show();
						return true;
					}
				});

		findPreference("import_db").setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference preference) {
						Intent chooseFile = new Intent(
								Intent.ACTION_GET_CONTENT);
						// in theory, it should be "application/x-sqlite3"
						// or the newer "application/vnd.sqlite3" but
						// only "application/octet-stream" works
						chooseFile.setType("application/octet-stream");
						startActivityForResult(
								Intent.createChooser(
										chooseFile,
										getString(R.string.import_db)),
								PICK_FILE_RESULT_CODE);
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
