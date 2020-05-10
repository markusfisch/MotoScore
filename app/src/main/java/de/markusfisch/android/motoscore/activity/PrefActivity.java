package de.markusfisch.android.motoscore.activity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.app.MotoScoreApp;
import de.markusfisch.android.motoscore.export.DatabaseExporter;
import de.markusfisch.android.motoscore.preference.Preferences;
import de.markusfisch.android.motoscore.service.MotoScoreService;

public class PrefActivity extends AppCompatPreferenceActivity {
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
					importDatabase(this, resultData.getData()) ?
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
						Context context = PrefActivity.this;
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

	private static boolean importDatabase(Context context, Uri uri) {
		if (uri == null) {
			return false;
		}
		ContentResolver cr = context.getContentResolver();
		if (cr == null) {
			return false;
		}
		final String fileName = "import.db";
		InputStream in = null;
		OutputStream out = null;
		try {
			in = cr.openInputStream(uri);
			if (in == null) {
				return false;
			}
			out = context.openFileOutput(fileName, Context.MODE_PRIVATE);
			byte[] buffer = new byte[4096];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
		} catch (IOException e) {
			return false;
		} finally {
			try {
				if (in != null) {
					in.close();
				}
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				// ignore, can't do anything about it
			}
		}
		boolean success = MotoScoreApp.db.importDatabase(context, fileName);
		context.deleteFile(fileName);
		return success;
	}
}
