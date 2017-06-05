package de.markusfisch.android.motoscore;

import android.content.Context;
import android.content.SharedPreferences;

public class MotoScorePreferences
{
	public static final String SHARED_PREFERENCES_NAME = "MotoScoreSettings";
	public static final String USE_MEDIA_BUTTON = "use_media_button";
	public static final String HAPTIC_FEEDBACK = "haptic_feedback";
	public static final String SHOW_NOTIFICATION = "show_notification";
	public static final String SCORE = "score";
	public static final String NUMBER_OF_RIDES = "number_of_rides";
	public static final String SECONDS_BETWEEN_UPDATES = "seconds_between_updates";
	public static final String METERS_BETWEEN_UPDATES = "meters_between_updates";
	public static final String MINIMUM_ACCURACY = "minimum_accuracy";

	private SharedPreferences preferences;

	public MotoScorePreferences( Context context )
	{
		preferences = context.getSharedPreferences(
			SHARED_PREFERENCES_NAME,
			0 );
	}

	public int secondsBetweenUpdates()
	{
		return Integer.parseInt(
			preferences.getString(
				SECONDS_BETWEEN_UPDATES,
				"30" ) );
	}

	public int metersBetweenUpdates()
	{
		return Integer.parseInt(
			preferences.getString(
				METERS_BETWEEN_UPDATES,
				"20" ) );
	}

	public int minimumAccuracy()
	{
		return Integer.parseInt(
			preferences.getString(
				MINIMUM_ACCURACY,
				"100" ) );
	}

	public int score()
	{
		return Integer.parseInt(
			preferences.getString(
				SCORE,
				"1" ) );
	}

	public int numberOfRides()
	{
		return Integer.parseInt(
			preferences.getString(
				NUMBER_OF_RIDES,
				"100" ) );
	}

	public boolean useMediaButton()
	{
		return preferences.getBoolean(
			USE_MEDIA_BUTTON,
			true );
	}

	public boolean showNotification()
	{
		return preferences.getBoolean(
			SHOW_NOTIFICATION,
			true );
	}

	public boolean hapticFeedback()
	{
		return preferences.getBoolean(
			HAPTIC_FEEDBACK,
			true );
	}
}
