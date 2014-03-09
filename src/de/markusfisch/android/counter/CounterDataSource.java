package de.markusfisch.android.counter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CounterDataSource
{
	public static final String TABLE = "countings";
	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_START = "start";
	public static final String COLUMN_STOP = "stop";
	public static final String COLUMN_MISTAKES = "mistakes";
	public static final String COLUMN_DISTANCE = "distance";

	public static final String COLUMN_DATE_AND_TIME = "date_and_time";
	public static final String COLUMN_MISTAKES_PER_KM = "mistakes_per_km";

	private SQLiteDatabase db = null;
	private OpenHelper helper;
	private Context context;

	public CounterDataSource( Context c )
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
		if( db != null )
			return;

		new Thread( new Runnable()
		{
			@Override
			public void run()
			{
				db = helper.getWritableDatabase();
			}
		} ).start();
	}

	public void close()
	{
		helper.close();
		db = null;
	}

	public Cursor queryAll()
	{
		if( db == null )
			return null;

		return db.rawQuery(
			"SELECT "+
				COLUMN_ID+","+
				COLUMN_START+","+
				COLUMN_STOP+","+
				COLUMN_MISTAKES+","+
				COLUMN_DISTANCE+","+
				" strftime( '%Y-%m-%d %H:%M:%S', "+COLUMN_START+
					" ) || "+
					" strftime( ' - %H:%M:%S', "+COLUMN_STOP+
					" ) AS "+COLUMN_DATE_AND_TIME+","+
				" cast( "+COLUMN_MISTAKES+" as float)/"+
					"max( round( "+COLUMN_DISTANCE+" ), 1 )"+
					" AS "+COLUMN_MISTAKES_PER_KM+
				" FROM "+TABLE+
				" WHERE julianday('now')-julianday("+COLUMN_START+") < 31"+
				" ORDER BY "+COLUMN_START+" DESC",
			null );
	}

	public long insert(
		Date start,
		Date stop,
		int mistakes,
		float distance )
	{
		if( db == null )
			return 0;

		return insert( db, start, stop, mistakes, distance );
	}

	public void remove( long id )
	{
		if( db == null )
			return;

		db.delete(
			TABLE,
			COLUMN_ID+"="+id,
			null );
	}

	private static long insert(
		SQLiteDatabase db,
		Date start,
		Date stop,
		int mistakes,
		float distance )
	{
		ContentValues cv = new ContentValues();
		cv.put( COLUMN_START, dateToString( start ) );
		cv.put( COLUMN_STOP, dateToString( stop ) );
		cv.put( COLUMN_MISTAKES, mistakes );
		cv.put( COLUMN_DISTANCE, distance );

		return db.insert( TABLE, null, cv );
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
			super( c, "countings.db", null, 1 );
		}

		@Override
		public void onCreate( SQLiteDatabase db )
		{
			db.execSQL(
				"CREATE TABLE "+TABLE+" ("+
					COLUMN_ID+" INTEGER PRIMARY KEY AUTOINCREMENT,"+
					COLUMN_START+" DATETIME,"+
					COLUMN_STOP+" DATETIME,"+
					COLUMN_MISTAKES+" INTEGER,"+
					COLUMN_DISTANCE+" FLOAT );" );
		}

		@Override
		public void onUpgrade(
			SQLiteDatabase db,
			int oldVersion,
			int newVersion )
		{
			db.execSQL( "DROP TABLE IF EXISTS "+TABLE );
			onCreate( db );
		}
	}
}
