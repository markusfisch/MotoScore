package de.markusfisch.android.motoscore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MotoScoreDataSource
{
	public static final String RIDES = "rides";
	public static final String RIDES_ID = "_id";
	public static final String RIDES_START = "start";
	public static final String RIDES_STOP = "stop";
	public static final String RIDES_MISTAKES = "mistakes";
	public static final String RIDES_DISTANCE = "distance";

	public static final String RIDES_DATE_AND_TIME = "date_and_time";
	public static final String RIDES_MISTAKES_PER_KM = "mistakes_per_km";

	public static final String WAYPOINTS = "waypoints";
	public static final String WAYPOINTS_ID = "_id";
	public static final String WAYPOINTS_RIDE = "ride";
	public static final String WAYPOINTS_LONGITUDE = "longitude";
	public static final String WAYPOINTS_LATITUDE = "latitude";
	public static final String WAYPOINTS_TIME = "time";

	private SQLiteDatabase db = null;
	private OpenHelper helper;
	private Context context;

	private boolean opening = false;
	private Handler handler = new Handler();
	private Runnable retryCloseRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			close();
		}
	};

	public MotoScoreDataSource( Context c )
	{
		helper = new OpenHelper( c );
		context = c;
	}

	public boolean ready()
	{
		return db != null;
	}

	public void open() throws SQLException
	{
		if( ready() )
			return;

		opening = true;

		new Thread( new Runnable()
		{
			@Override
			public void run()
			{
				db = helper.getWritableDatabase();
				opening = false;
			}
		} ).start();
	}

	public void close()
	{
		if( !ready() )
		{
			handler.removeCallbacks( retryCloseRunnable );

			if( opening )
				handler.postDelayed(
					retryCloseRunnable,
					100 );

			return;
		}

		helper.close();
		db = null;
	}

	public Cursor queryRides( int days )
	{
		if( db == null )
			return null;

		return db.rawQuery(
			"SELECT "+
				RIDES_ID+","+
				RIDES_START+","+
				RIDES_STOP+","+
				RIDES_MISTAKES+","+
				RIDES_DISTANCE+","+
				" strftime( '%Y-%m-%d %H:%M', "+RIDES_START+
					" ) || "+
					" strftime( ' - %H:%M', "+RIDES_STOP+
					" ) AS "+RIDES_DATE_AND_TIME+","+
				" cast( "+RIDES_MISTAKES+" as float )/"+
					"max( "+RIDES_DISTANCE+"/1000, 1 )"+
					" AS "+RIDES_MISTAKES_PER_KM+
				" FROM "+RIDES+
				" WHERE julianday( 'now' )-julianday( "+
					RIDES_START+" ) < "+days+
				" ORDER BY "+RIDES_START+" DESC",
			null );
	}

	public long insertRide(
		Date start,
		Date stop,
		int mistakes,
		float distance )
	{
		if( db == null )
			return 0;

		return insertRide( db, start, stop, mistakes, distance );
	}

	public void removeRide( long id )
	{
		if( db == null )
			return;

		db.delete(
			WAYPOINTS,
			WAYPOINTS_RIDE+"="+id,
			null );

		db.delete(
			RIDES,
			RIDES_ID+"="+id,
			null );
	}

	private static long insertRide(
		SQLiteDatabase db,
		Date start,
		Date stop,
		int mistakes,
		float distance )
	{
		ContentValues cv = new ContentValues();

		cv.put( RIDES_START, dateToString( start ) );
		cv.put( RIDES_STOP, dateToString( stop ) );
		cv.put( RIDES_MISTAKES, mistakes );
		cv.put( RIDES_DISTANCE, distance );

		return db.insert( RIDES, null, cv );
	}

	public Cursor queryWaypoints( long ride )
	{
		if( db == null )
			return null;

		return db.rawQuery(
			"SELECT "+
				WAYPOINTS_ID+","+
				WAYPOINTS_LONGITUDE+","+
				WAYPOINTS_LATITUDE+
				" FROM "+WAYPOINTS+
				" WHERE "+WAYPOINTS_RIDE+" = "+ride+
				" ORDER BY "+WAYPOINTS_TIME,
			null );
	}

	public long insertWaypoint(
		long rideId,
		double latitude,
		double longitude,
		long time )
	{
		if( db == null )
			return 0;

		return insertWaypoint( db, rideId, latitude, longitude, time );
	}

	public void removeWaypoint( long id )
	{
		if( db == null )
			return;

		db.delete(
			WAYPOINTS,
			WAYPOINTS_ID+"="+id,
			null );
	}

	private static long insertWaypoint(
		SQLiteDatabase db,
		long rideId,
		double latitude,
		double longitude,
		long time )
	{
		ContentValues cv = new ContentValues();

		cv.put( WAYPOINTS_RIDE, rideId );
		cv.put( WAYPOINTS_LATITUDE, latitude );
		cv.put( WAYPOINTS_LONGITUDE, longitude );
		cv.put( WAYPOINTS_TIME, time );

		return db.insert( WAYPOINTS, null, cv );
	}

	private static String dateToString( Date date )
	{
		return new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss" ).format(
			date );
	}

	private class OpenHelper extends SQLiteOpenHelper
	{
		public OpenHelper( Context c )
		{
			super( c, "MotoScore.db", null, 1 );
		}

		@Override
		public void onCreate( SQLiteDatabase db )
		{
			db.execSQL(
				"CREATE TABLE "+RIDES+" ("+
					RIDES_ID+" INTEGER PRIMARY KEY AUTOINCREMENT,"+
					RIDES_START+" DATETIME,"+
					RIDES_STOP+" DATETIME,"+
					RIDES_MISTAKES+" INTEGER,"+
					RIDES_DISTANCE+" FLOAT );" );

			db.execSQL(
				"CREATE TABLE "+WAYPOINTS+" ("+
					WAYPOINTS_ID+" INTEGER PRIMARY KEY AUTOINCREMENT,"+
					WAYPOINTS_RIDE+" INTEGER,"+
					WAYPOINTS_LONGITUDE+" FLOAT,"+
					WAYPOINTS_LATITUDE+" FLOAT,"+
					WAYPOINTS_TIME+" TIMESTAMP );" );
		}

		@Override
		public void onUpgrade(
			SQLiteDatabase db,
			int oldVersion,
			int newVersion )
		{
			db.execSQL( "DROP TABLE IF EXISTS "+RIDES );
			db.execSQL( "DROP TABLE IF EXISTS "+WAYPOINTS );

			onCreate( db );
		}
	}
}
