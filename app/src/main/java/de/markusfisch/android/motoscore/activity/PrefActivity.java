package de.markusfisch.android.motoscore.activity;

import de.markusfisch.android.motoscore.data.Database;
import de.markusfisch.android.motoscore.preference.Preferences;
import de.markusfisch.android.motoscore.service.MotoScoreService;
import de.markusfisch.android.motoscore.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.Preference;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

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
				exportDatabase();
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

	private void exportDatabase() {
		try {
			File sd = Environment.getExternalStorageDirectory();
			File data = Environment.getDataDirectory();

			if (sd.canWrite()) {
				String dbName = Database.FILE_NAME;
				String backupDBPath = dbName;
				String currentDBPath = "//data//" + getPackageName() +
						"//databases//" + dbName;
				File currentDB = new File(data, currentDBPath);
				File backupDB = new File(sd, backupDBPath);

				if (currentDB.exists()) {
					FileChannel src = new FileInputStream(
							currentDB).getChannel();
					FileChannel dst = new FileOutputStream(
							backupDB).getChannel();
					dst.transferFrom(src, 0, src.size());
					src.close();
					dst.close();

					Toast.makeText(this, R.string.successfully_exported,
							Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(this, R.string.error_cant_find_db,
							Toast.LENGTH_SHORT).show();
				}
			} else {
				Toast.makeText(this, R.string.error_storage_not_writeable,
						Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e) {
			Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}
}
