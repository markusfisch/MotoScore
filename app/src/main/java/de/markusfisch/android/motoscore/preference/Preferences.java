package de.markusfisch.android.motoscore.preference;

import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {
	public static final String SHARED_PREFERENCES_NAME = "MotoScoreSettings";

	private static final String DISCLOSURE_SHOWN = "disclosure_shown";
	private static final String USE_MEDIA_BUTTON = "use_media_button";
	private static final String HAPTIC_FEEDBACK = "haptic_feedback";
	private static final String SCORE = "score";
	private static final String SECONDS_BETWEEN_UPDATES = "seconds_between_updates";
	private static final String METERS_BETWEEN_UPDATES = "meters_between_updates";
	private static final String MINIMUM_ACCURACY = "minimum_accuracy";

	private SharedPreferences preferences;

	public void init(Context context) {
		preferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
				0);
	}

	public int secondsBetweenUpdates() {
		return Integer.parseInt(preferences.getString(SECONDS_BETWEEN_UPDATES,
				"30"));
	}

	public int metersBetweenUpdates() {
		return Integer.parseInt(preferences.getString(METERS_BETWEEN_UPDATES,
				"20"));
	}

	public int minimumAccuracy() {
		return Integer.parseInt(preferences.getString(MINIMUM_ACCURACY,
				"100"));
	}

	public int score() {
		return Integer.parseInt(preferences.getString(SCORE, "1"));
	}

	public boolean disclosureShown() {
		return preferences.getBoolean(DISCLOSURE_SHOWN, false);
	}

	public void setDisclosureShown() {
		SharedPreferences.Editor editor = preferences.edit();
		editor.putBoolean(DISCLOSURE_SHOWN, true);
		editor.apply();
	}

	public boolean useMediaButton() {
		return preferences.getBoolean(USE_MEDIA_BUTTON, true);
	}

	public boolean hapticFeedback() {
		return preferences.getBoolean(HAPTIC_FEEDBACK, true);
	}
}
