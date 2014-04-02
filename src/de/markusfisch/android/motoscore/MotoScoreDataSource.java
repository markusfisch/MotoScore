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
	public static final int SCORE_MISTAKES_DISTANCE = 1;
	public static final int SCORE_DISTANCE_MISTAKES = 2;
	public static final int SCORE_MISTAKES = 3;
	public static final int SCORE_DISTANCE = 4;

	public static final String RIDES = "rides";
	public static final String RIDES_ID = "_id";
	public static final String RIDES_START = "start";
	public static final String RIDES_STOP = "stop";
	public static final String RIDES_MISTAKES = "mistakes";
	public static final String RIDES_DISTANCE = "distance";
	public static final String RIDES_DURATION = "duration";
	public static final String RIDES_AVERAGE = "average";

	public static final String RIDES_DATE_AND_TIME = "date_and_time";
	public static final String RIDES_SCORE = "score";

	public static final String WAYPOINTS = "waypoints";
	public static final String WAYPOINTS_ID = "_id";
	public static final String WAYPOINTS_RIDE = "ride";
	public static final String WAYPOINTS_LONGITUDE = "longitude";
	public static final String WAYPOINTS_LATITUDE = "latitude";
	public static final String WAYPOINTS_TIME = "time";
	public static final String WAYPOINTS_ACCURACY = "accuracy";
	public static final String WAYPOINTS_ALTITUDE = "altitude";
	public static final String WAYPOINTS_BEARING = "bearing";
	public static final String WAYPOINTS_SPEED = "speed";

	private SQLiteDatabase db = null;
	private OpenHelper helper;

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
				recalculateAverages( db );
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

	public String queryRideDate( long id )
	{
		if( !ready() )
			return null;

		Cursor cursor = db.rawQuery(
			"SELECT "+
				" strftime( '%Y%m%d%H%M%S', "+RIDES_START+" )"+
				" FROM "+RIDES+
				" WHERE "+RIDES_ID+" = "+id,
			null );

		if( cursor == null ||
			!cursor.moveToFirst() )
			return null;

		String date = cursor.getString( 0 );

		cursor.close();

		return date;
	}

	public int queryNumberOfRides()
	{
		if( !ready() )
			return -1;

		Cursor cursor = db.rawQuery(
			"SELECT "+
				" COUNT(*)"+
				" FROM "+RIDES+
				" WHERE "+RIDES_STOP+" IS NOT NULL",
			null );

		if( cursor == null ||
			!cursor.moveToFirst() )
			return -1;

		int n = cursor.getInt( 0 );

		cursor.close();

		return n;
	}

	public Cursor queryRides( int limit, int score )
	{
		if( !ready() )
			return null;

		String scoreExpression;

		switch( score )
		{
			default:
			case 1:
				scoreExpression =
					"cast( "+RIDES_MISTAKES+" as float )/"+
					"max( "+RIDES_DISTANCE+"/1000, 1 )";
				break;
			case 2:
				scoreExpression =
					"cast( "+RIDES_MISTAKES+" as float )/"+
					"round( (julianday( "+RIDES_STOP+" )-julianday( "+
						RIDES_START+" ))*1440 )";
				break;
			case 3:
				scoreExpression = RIDES_MISTAKES;
				break;
			case 4:
				scoreExpression = RIDES_DISTANCE;
				break;
			case 5:
				scoreExpression =
					"(julianday( "+RIDES_STOP+
						" )-julianday( "+RIDES_START+" ))*24";
				break;
			case 6:
				scoreExpression = RIDES_AVERAGE+"*3.6";
				break;
		}

		return db.rawQuery(
			"SELECT "+
				RIDES_ID+","+
				RIDES_START+","+
				RIDES_MISTAKES+","+
				RIDES_DISTANCE+","+
				RIDES_AVERAGE+","+
				" strftime( '%Y-%m-%d %H:%M', "+RIDES_START+
					" ) || "+
					" strftime( ' - %H:%M', "+RIDES_STOP+
					" ) AS "+RIDES_DATE_AND_TIME+","+
				" julianday( "+RIDES_STOP+
					" )-julianday( "+RIDES_START+
					" ) AS "+RIDES_DURATION+","+
				" "+scoreExpression+" AS "+RIDES_SCORE+
				" FROM "+RIDES+
				" WHERE "+RIDES_STOP+" IS NOT NULL"+
				" ORDER BY "+RIDES_START+" DESC"+
				" LIMIT "+limit,
			null );
	}

	public long insertRide( Date start )
	{
		if( !ready() )
			return 0;

		return insertRide( db, start );
	}

	public long updateRide(
		long rideId,
		Date stop,
		int mistakes,
		float distance,
		float averageSpeed )
	{
		if( db == null )
			return 0;

		return updateRide(
			db,
			rideId,
			stop,
			mistakes,
			distance,
			averageSpeed );
	}

	public void removeRide( long id )
	{
		if( !ready() )
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
		Date start )
	{
		ContentValues cv = new ContentValues();

		cv.put( RIDES_START, dateToString( start ) );
		cv.put( RIDES_MISTAKES, 0 );
		cv.put( RIDES_DISTANCE, 0f );

		return db.insert( RIDES, null, cv );
	}

	private static long updateRide(
		SQLiteDatabase db,
		long rideId,
		Date stop,
		int mistakes,
		float distance,
		float averageSpeed )
	{
		ContentValues cv = new ContentValues();

		cv.put( RIDES_STOP, dateToString( stop ) );
		cv.put( RIDES_MISTAKES, mistakes );
		cv.put( RIDES_DISTANCE, distance );
		cv.put( RIDES_AVERAGE, averageSpeed );

		return db.update(
			RIDES,
			cv,
			RIDES_ID+"="+rideId,
			null );
	}

	public Cursor queryWaypoints( long rideId )
	{
		if( !ready() )
			return null;

		return db.rawQuery(
			"SELECT "+
				WAYPOINTS_ID+","+
				WAYPOINTS_TIME+","+
				WAYPOINTS_LONGITUDE+","+
				WAYPOINTS_LATITUDE+","+
				WAYPOINTS_ACCURACY+","+
				WAYPOINTS_ALTITUDE+","+
				WAYPOINTS_BEARING+","+
				WAYPOINTS_SPEED+
				" FROM "+WAYPOINTS+
				" WHERE "+WAYPOINTS_RIDE+" = "+rideId+
				" ORDER BY "+WAYPOINTS_TIME,
			null );
	}

	public int queryWaypointsCount( long rideId )
	{
		if( !ready() )
			return -1;

		Cursor cursor = db.rawQuery(
			"SELECT COUNT(*)"+
				" FROM "+WAYPOINTS+
				" WHERE "+WAYPOINTS_RIDE+" = "+rideId,
			null );

		if( cursor == null ||
			!cursor.moveToFirst() )
			return -1;

		int n = cursor.getInt( 0 );

		cursor.close();

		return n;
	}

	public long insertWaypoint(
		long rideId,
		long time,
		double latitude,
		double longitude,
		float accuracy,
		double altitude,
		float bearing,
		float speed )
	{
		if( !ready() )
			return 0;

		return insertWaypoint(
			db,
			rideId,
			time,
			latitude,
			longitude,
			accuracy,
			altitude,
			bearing,
			speed );
	}

	public void removeWaypoint( long id )
	{
		if( !ready() )
			return;

		db.delete(
			WAYPOINTS,
			WAYPOINTS_ID+"="+id,
			null );
	}

	private static long insertWaypoint(
		SQLiteDatabase db,
		long rideId,
		long time,
		double latitude,
		double longitude,
		float accuracy,
		double altitude,
		float bearing,
		float speed )
	{
		ContentValues cv = new ContentValues();

		cv.put( WAYPOINTS_RIDE, rideId );
		cv.put( WAYPOINTS_TIME, time );
		cv.put( WAYPOINTS_LATITUDE, latitude );
		cv.put( WAYPOINTS_LONGITUDE, longitude );
		cv.put( WAYPOINTS_ACCURACY, accuracy );
		cv.put( WAYPOINTS_ALTITUDE, altitude );
		cv.put( WAYPOINTS_BEARING, bearing );
		cv.put( WAYPOINTS_SPEED, speed );

		return db.insert( WAYPOINTS, null, cv );
	}

	private static String dateToString( Date date )
	{
		return new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss" ).format(
			date );
	}

	private static void recalculateAverages( SQLiteDatabase db )
	{
		Cursor cursor = db.rawQuery(
			"SELECT "+
				RIDES_ID+","+
				" (SELECT SUM(speed)"+
					" FROM "+WAYPOINTS+
					" WHERE "+WAYPOINTS_RIDE+" = r."+RIDES_ID+")/"+
				" (SELECT COUNT(*)"+
					" FROM "+WAYPOINTS+
					" WHERE "+WAYPOINTS_RIDE+" = r."+RIDES_ID+")"+
				" FROM "+RIDES+" AS r",
			null );

		if( cursor == null ||
			!cursor.moveToFirst() )
			return;

		do
		{
			db.execSQL(
				"UPDATE "+RIDES+
					" SET "+RIDES_AVERAGE+" = "+
						cursor.getInt( 1 )+
					" WHERE "+RIDES_ID+" = "+
						cursor.getInt( 0 ) );

		} while( cursor.moveToNext() );

		cursor.close();
	}

	private class OpenHelper extends SQLiteOpenHelper
	{
		public OpenHelper( Context c )
		{
			super( c, "MotoScore.db", null, 2 );
		}

		@Override
		public void onCreate( SQLiteDatabase db )
		{
			db.execSQL( "DROP TABLE IF EXISTS "+RIDES );
			db.execSQL(
				"CREATE TABLE "+RIDES+" ("+
					RIDES_ID+" INTEGER PRIMARY KEY AUTOINCREMENT,"+
					RIDES_START+" DATETIME,"+
					RIDES_STOP+" DATETIME,"+
					RIDES_MISTAKES+" INTEGER,"+
					RIDES_DISTANCE+" FLOAT,"+
					RIDES_AVERAGE+" FLOAT );" );

			db.execSQL( "DROP TABLE IF EXISTS "+WAYPOINTS );
			db.execSQL(
				"CREATE TABLE "+WAYPOINTS+" ("+
					WAYPOINTS_ID+" INTEGER PRIMARY KEY AUTOINCREMENT,"+
					WAYPOINTS_RIDE+" INTEGER,"+
					WAYPOINTS_TIME+" TIMESTAMP,"+
					WAYPOINTS_LONGITUDE+" FLOAT,"+
					WAYPOINTS_LATITUDE+" FLOAT,"+
					WAYPOINTS_ACCURACY+" FLOAT,"+
					WAYPOINTS_ALTITUDE+" FLOAT,"+
					WAYPOINTS_BEARING+" FLOAT,"+
					WAYPOINTS_SPEED+" FLOAT );" );
		}

		@Override
		public void onUpgrade(
			SQLiteDatabase db,
			int oldVersion,
			int newVersion )
		{
			switch( oldVersion )
			{
				default:
					onCreate( db );
					break;
				case 1:
					updateToVersion2( db );
					break;
			}
		}

		private void updateToVersion2( SQLiteDatabase db )
		{
			db.execSQL(
				"ALTER TABLE "+RIDES+
					" ADD COLUMN "+RIDES_AVERAGE+" FLOAT" );

			recalculateAverages( db );
		}
	}
}
