package de.markusfisch.android.motoscore.app;

import android.app.Application;

import de.markusfisch.android.motoscore.data.Database;
import de.markusfisch.android.motoscore.preference.Preferences;

public class MotoScoreApp extends Application {
	public static final Database db = new Database();
	public static final Preferences preferences = new Preferences();

	@Override
	public void onCreate() {
		super.onCreate();
		db.open(this);
		preferences.init(this);
	}
}

