package de.markusfisch.android.motoscore;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class MotoScoreApplication extends Application
{
	public static MotoScoreDataSource dataSource;

	@Override
	public void onCreate()
	{
		super.onCreate();

		dataSource = new MotoScoreDataSource( getApplicationContext() );
		dataSource.open();

		// since apps just get killed by the system, it's kind of
		// pointless to try dataSource.close() anywhere
	}
}

