package de.markusfisch.android.counter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CounterDataSource
{
	public static final String TABLE = "countings";
	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_START = "start";
	public static final String COLUMN_STOP = "stop";
	public static final String COLUMN_ERRORS = "errors";
	public static final String COLUMN_DISTANCE = "distance";

	private SQLiteDatabase db = null;
	private OpenHelper helper;
	private Context context;
	private Handler handler = new Handler();

	public CounterDataSource( Context c )
	{
		helper = new OpenHelper( c );
		context = c;
	}

	public void open( final Runnable runnable ) throws SQLException
	{
		if( db != null )
			return;

		new Thread( new Runnable()
		{
			@Override
			public void run()
			{
				db = helper.getWritableDatabase();

				handler.post( runnable );
			}
		} ).start();
	}

	public void close()
	{
		if( db == null )
			return;

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
				COLUMN_ERRORS+","+
				COLUMN_DISTANCE+
				" FROM "+TABLE+
				" ORDER BY "+COLUMN_START+" DESC",
			null );
	}

	public static long insert(
		SQLiteDatabase db,
		Date start,
		Date stop,
		int errors,
		float distance )
	{
		if( db == null )
			return 0;

		ContentValues cv = new ContentValues();
		cv.put( COLUMN_START, dateToString( start ) );
		cv.put( COLUMN_STOP, dateToString( stop ) );
		cv.put( COLUMN_ERRORS, errors );
		cv.put( COLUMN_DISTANCE, distance );

		return db.insert( TABLE, null, cv );
	}

	public long insert(
		Date start,
		Date stop,
		int errors,
		float distance )
	{
		return insert( db, start, stop, errors, distance );
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
					COLUMN_ERRORS+" INTEGER,"+
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
