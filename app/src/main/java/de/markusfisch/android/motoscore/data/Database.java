package de.markusfisch.android.motoscore.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.markusfisch.android.motoscore.R;

public class Database {
	public static final String FILE_NAME = "MotoScore.db";

	public static final int SCORE_TYPE_MISTAKES_PER_KM = 1;
	public static final int SCORE_TYPE_MISTAKES_PER_HOUR = 2;
	public static final int SCORE_TYPE_MISTAKES_TOTAL = 3;
	public static final int SCORE_TYPE_DISTANCE_IN_KM = 4;
	public static final int SCORE_TYPE_DURATION_IN_HOURS = 5;
	public static final int SCORE_TYPE_AVERAGE_SPEED = 6;

	public static final String RIDES = "rides";
	public static final String RIDES_ID = "_id";
	public static final String RIDES_START = "start";
	public static final String RIDES_STOP = "stop";
	public static final String RIDES_MISTAKES = "mistakes";
	public static final String RIDES_DISTANCE = "distance";
	public static final String RIDES_AVERAGE = "average";
	public static final String RIDES_DURATION = "duration";
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

	public boolean importDatabase(Context context, String fileName) {
		SQLiteDatabase edb = null;
		try {
			edb = new ImportHelper(new ExternalDatabaseContext(context),
					fileName).getReadableDatabase();
			db.beginTransaction();
			if (addRidesTable(db, edb)) {
				db.setTransactionSuccessful();
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			return false;
		} finally {
			if (db.inTransaction()) {
				db.endTransaction();
			}
			if (edb != null) {
				edb.close();
			}
		}
	}

	public boolean open(Context context) {
		try {
			db = new OpenHelper(context).getWritableDatabase();
			return true;
		} catch (SQLException e) {
			Toast.makeText(context, R.string.error_database,
					Toast.LENGTH_LONG).show();
			return false;
		}
	}

	public boolean isOpen() {
		return db != null;
	}

	public String queryRideDate(long id) {
		Cursor cursor = db.rawQuery(
				"SELECT " +
						" strftime( '%Y%m%d%H%M%S', " + RIDES_START + " )" +
						" FROM " + RIDES +
						" WHERE " + RIDES_ID + " = ?",
				new String[]{String.valueOf(id)});

		if (cursor == null) {
			return null;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			return null;
		}

		String date = cursor.getString(0);
		cursor.close();
		return date;
	}

	public Cursor queryRides(int scoreType) {
		String scoreExpression;

		switch (scoreType) {
			default:
			case SCORE_TYPE_MISTAKES_PER_KM:
				scoreExpression = "cast(" + RIDES_MISTAKES + " as float) / " +
						"max(" + RIDES_DISTANCE + " / 1000, 1)";
				break;
			case SCORE_TYPE_MISTAKES_PER_HOUR:
				scoreExpression = "cast(" + RIDES_MISTAKES + " as float) / " +
						"round((julianday(" + RIDES_STOP + ") - julianday(" +
						RIDES_START + ")) * 1440)";
				break;
			case SCORE_TYPE_MISTAKES_TOTAL:
				scoreExpression = RIDES_MISTAKES;
				break;
			case SCORE_TYPE_DISTANCE_IN_KM:
				scoreExpression = "round(cast(" + RIDES_DISTANCE +
						" as float) / 1000)";
				break;
			case SCORE_TYPE_DURATION_IN_HOURS:
				scoreExpression = "(julianday(" + RIDES_STOP +
						") - julianday(" + RIDES_START + ")) * 24";
				break;
			case SCORE_TYPE_AVERAGE_SPEED:
				scoreExpression = RIDES_AVERAGE + " * 3.6";
				break;
		}

		return db.rawQuery(
				"SELECT " +
						RIDES_ID + "," +
						RIDES_START + "," +
						RIDES_STOP + "," +
						RIDES_MISTAKES + "," +
						RIDES_DISTANCE + "," +
						RIDES_AVERAGE + "," +
						"julianday(" + RIDES_STOP + ") - julianday(" +
						RIDES_START + ") AS " + RIDES_DURATION + "," +
						scoreExpression + " AS " + RIDES_SCORE +
						" FROM " + RIDES +
						" WHERE " + RIDES_STOP + " IS NOT NULL" +
						" ORDER BY " + RIDES_START + " DESC",
				null);
	}

	public long insertRide(Date start) {
		return insertRide(db, start);
	}

	public long updateRide(
			long rideId,
			Date stop,
			int mistakes,
			float distance,
			float averageSpeed) {
		return updateRide(db, rideId, stop, mistakes, distance, averageSpeed);
	}

	public void removeRide(long id) {
		String[] values = new String[]{String.valueOf(id)};
		db.delete(WAYPOINTS, WAYPOINTS_RIDE + "= ?", values);
		db.delete(RIDES, RIDES_ID + "= ?", values);
	}

	private static long insertRide(SQLiteDatabase db, Date start) {
		return insertRide(db, dateToString(start));
	}

	private static long insertRide(SQLiteDatabase db, String start) {
		ContentValues cv = new ContentValues();
		cv.put(RIDES_START, start);
		cv.put(RIDES_MISTAKES, 0);
		cv.put(RIDES_DISTANCE, 0f);
		return db.insert(RIDES, null, cv);
	}

	private static long updateRide(
			SQLiteDatabase db,
			long rideId,
			Date stop,
			int mistakes,
			float distance,
			float averageSpeed) {
		return updateRide(db, rideId, dateToString(stop), mistakes, distance,
				averageSpeed);
	}

	private static long updateRide(
			SQLiteDatabase db,
			long rideId,
			String stop,
			int mistakes,
			float distance,
			float averageSpeed) {
		ContentValues cv = new ContentValues();
		cv.put(RIDES_STOP, stop);
		cv.put(RIDES_MISTAKES, mistakes);
		cv.put(RIDES_DISTANCE, distance);
		cv.put(RIDES_AVERAGE, averageSpeed);
		return db.update(RIDES, cv, RIDES_ID + "= ?",
				new String[]{String.valueOf(rideId)});
	}

	public Cursor queryWaypoints(long rideId) {
		return db.rawQuery(
				"SELECT " +
						WAYPOINTS_ID + "," +
						WAYPOINTS_TIME + "," +
						WAYPOINTS_LONGITUDE + "," +
						WAYPOINTS_LATITUDE + "," +
						WAYPOINTS_ACCURACY + "," +
						WAYPOINTS_ALTITUDE + "," +
						WAYPOINTS_BEARING + "," +
						WAYPOINTS_SPEED +
						" FROM " + WAYPOINTS +
						" WHERE " + WAYPOINTS_RIDE + " = ?" +
						" ORDER BY " + WAYPOINTS_TIME,
				new String[]{String.valueOf(rideId)});
	}

	public int queryWaypointsCount(long rideId) {
		Cursor cursor = db.rawQuery(
				"SELECT COUNT(*)" +
						" FROM " + WAYPOINTS +
						" WHERE " + WAYPOINTS_RIDE + " = ?",
				new String[]{String.valueOf(rideId)});

		if (cursor == null) {
			return -1;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			return -1;
		}

		int n = cursor.getInt(0);
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
			float speed) {
		return insertWaypoint(db, rideId, time, latitude, longitude,
				accuracy, altitude, bearing, speed);
	}

	public void removeWaypoint(long id) {
		db.delete(WAYPOINTS, WAYPOINTS_ID + "=" + id, null);
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
			float speed) {
		ContentValues cv = new ContentValues();
		cv.put(WAYPOINTS_RIDE, rideId);
		cv.put(WAYPOINTS_TIME, time);
		cv.put(WAYPOINTS_LATITUDE, latitude);
		cv.put(WAYPOINTS_LONGITUDE, longitude);
		cv.put(WAYPOINTS_ACCURACY, accuracy);
		cv.put(WAYPOINTS_ALTITUDE, altitude);
		cv.put(WAYPOINTS_BEARING, bearing);
		cv.put(WAYPOINTS_SPEED, speed);
		return db.insert(WAYPOINTS, null, cv);
	}

	private static String dateToString(Date date) {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
				Locale.getDefault()).format(date);
	}

	private static void recalculateAverages(SQLiteDatabase db) {
		Cursor cursor = db.rawQuery(
				"SELECT " +
						RIDES_ID + "," +
						" (SELECT SUM(speed)" +
						" FROM " + WAYPOINTS +
						" WHERE " + WAYPOINTS_RIDE + " = r." +
						RIDES_ID + ") / " +
						" (SELECT COUNT(*)" +
						" FROM " + WAYPOINTS +
						" WHERE " + WAYPOINTS_RIDE + " = r." +
						RIDES_ID + ")" +
						" FROM " + RIDES + " AS r",
				null);

		if (cursor == null) {
			return;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			return;
		}

		do {
			db.execSQL("UPDATE " + RIDES +
					" SET " + RIDES_AVERAGE + " = " +
					cursor.getInt(1) +
					" WHERE " + RIDES_ID + " = " +
					cursor.getInt(0));
		} while (cursor.moveToNext());

		cursor.close();
	}

	private static class OpenHelper extends SQLiteOpenHelper {
		private OpenHelper(Context context) {
			super(context, FILE_NAME, null, 2);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("DROP TABLE IF EXISTS " + RIDES);
			db.execSQL("CREATE TABLE " + RIDES + " (" +
					RIDES_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
					RIDES_START + " DATETIME," +
					RIDES_STOP + " DATETIME," +
					RIDES_MISTAKES + " INTEGER," +
					RIDES_DISTANCE + " FLOAT," +
					RIDES_AVERAGE + " FLOAT);");

			db.execSQL("DROP TABLE IF EXISTS " + WAYPOINTS);
			db.execSQL("CREATE TABLE " + WAYPOINTS + " (" +
					WAYPOINTS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
					WAYPOINTS_RIDE + " INTEGER," +
					WAYPOINTS_TIME + " TIMESTAMP," +
					WAYPOINTS_LONGITUDE + " FLOAT," +
					WAYPOINTS_LATITUDE + " FLOAT," +
					WAYPOINTS_ACCURACY + " FLOAT," +
					WAYPOINTS_ALTITUDE + " FLOAT," +
					WAYPOINTS_BEARING + " FLOAT," +
					WAYPOINTS_SPEED + " FLOAT);");
		}

		@Override
		public void onDowngrade(
				SQLiteDatabase db,
				int oldVersion,
				int newVersion) {
			// Do nothing, but without that method, a downgrade
			// would cause an exception.
		}

		@Override
		public void onUpgrade(
				SQLiteDatabase db,
				int oldVersion,
				int newVersion) {
			if (oldVersion < 2) {
				addRidesAverageColumn(db);
			}
		}

		private void addRidesAverageColumn(SQLiteDatabase db) {
			db.execSQL("ALTER TABLE " + RIDES +
					" ADD COLUMN " + RIDES_AVERAGE + " FLOAT");
			recalculateAverages(db);
		}
	}

	private static boolean addRidesTable(
			SQLiteDatabase dst,
			SQLiteDatabase src) {
		Cursor cursor = src.rawQuery(
				"SELECT *" +
						" FROM " + RIDES +
						" ORDER BY " + RIDES_ID,
				null);
		if (cursor == null) {
			return false;
		}
		int idIndex = cursor.getColumnIndex(RIDES_ID);
		int startIndex = cursor.getColumnIndex(RIDES_START);
		int stopIndex = cursor.getColumnIndex(RIDES_STOP);
		int mistakesIndex = cursor.getColumnIndex(RIDES_MISTAKES);
		int distanceIndex = cursor.getColumnIndex(RIDES_DISTANCE);
		int averageIndex = cursor.getColumnIndex(RIDES_AVERAGE);
		boolean success = true;
		if (cursor.moveToFirst()) {
			do {
				String startDate = cursor.getString(startIndex);
				String stopDate = cursor.getString(stopIndex);
				if (startDate == null || stopDate == null ||
						rideExists(dst, startDate, stopDate)) {
					continue;
				}
				long rideId = insertRide(dst, startDate);
				if (rideId < 1) {
					success = false;
					break;
				}
				updateRide(dst,
						rideId,
						stopDate,
						cursor.getInt(mistakesIndex),
						cursor.getFloat(distanceIndex),
						averageIndex < 0 ? 0f : cursor.getFloat(averageIndex));
				if (!addWaypointsTable(dst, src, rideId,
						cursor.getLong(idIndex))) {
					success = false;
					break;
				}
			} while (cursor.moveToNext());
		}
		cursor.close();
		if (success && averageIndex < 0) {
			recalculateAverages(dst);
		}
		return success;
	}

	private static boolean rideExists(
			SQLiteDatabase db,
			String startDate,
			String stopDate) {
		Cursor cursor = db.rawQuery(
				"SELECT " + RIDES_ID +
						" FROM " + RIDES +
						" WHERE " + RIDES_START + " = ?" +
						" AND " + RIDES_STOP + " = ?",
				new String[]{startDate, stopDate});
		if (cursor == null) {
			return false;
		}
		boolean exists = cursor.moveToFirst() && cursor.getCount() > 0;
		cursor.close();
		return exists;
	}

	private static boolean addWaypointsTable(
			SQLiteDatabase dst,
			SQLiteDatabase src,
			long dstRideId,
			long srdRideId) {
		Cursor cursor = src.rawQuery(
				"SELECT *" +
						" FROM " + WAYPOINTS +
						" WHERE " + WAYPOINTS_RIDE + " = ?" +
						" ORDER BY " + WAYPOINTS_ID,
				new String[]{String.valueOf(srdRideId)});
		if (cursor == null) {
			return false;
		}
		int lngIndex = cursor.getColumnIndex(WAYPOINTS_LONGITUDE);
		int latIndex = cursor.getColumnIndex(WAYPOINTS_LATITUDE);
		int timeIndex = cursor.getColumnIndex(WAYPOINTS_TIME);
		int accuracyIndex = cursor.getColumnIndex(WAYPOINTS_ACCURACY);
		int altitudeIndex = cursor.getColumnIndex(WAYPOINTS_ALTITUDE);
		int bearingIndex = cursor.getColumnIndex(WAYPOINTS_BEARING);
		int speedIndex = cursor.getColumnIndex(WAYPOINTS_SPEED);
		boolean success = cursor.moveToFirst();
		if (success) {
			do {
				if (insertWaypoint(dst, dstRideId,
						cursor.getLong(timeIndex),
						cursor.getDouble(latIndex),
						cursor.getDouble(lngIndex),
						cursor.getFloat(accuracyIndex),
						cursor.getDouble(altitudeIndex),
						cursor.getFloat(bearingIndex),
						cursor.getFloat(speedIndex)) < 1L) {
					success = false;
					break;
				}
			} while (cursor.moveToNext());
		}
		cursor.close();
		return success;
	}

	private static class ImportHelper extends SQLiteOpenHelper {
		private ImportHelper(Context context, String path) {
			super(context, path, null, 1);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			// Do nothing.
		}

		@Override
		public void onDowngrade(
				SQLiteDatabase db,
				int oldVersion,
				int newVersion) {
			// Do nothing, but without that method we cannot open
			// different versions.
		}

		@Override
		public void onUpgrade(
				SQLiteDatabase db,
				int oldVersion,
				int newVersion) {
			// Do nothing, but without that method we cannot open
			// different versions.
		}
	}

	// Somehow it's required to use this ContextWrapper to access the
	// tables in an external database; without this, the database will
	// only contain the table "android_metadata".
	public static class ExternalDatabaseContext extends ContextWrapper {
		public ExternalDatabaseContext(Context base) {
			super(base);
		}

		@Override
		public File getDatabasePath(String name) {
			return new File(getFilesDir(), name);
		}

		@Override
		public SQLiteDatabase openOrCreateDatabase(String name, int mode,
				SQLiteDatabase.CursorFactory factory,
				DatabaseErrorHandler errorHandler) {
			return openOrCreateDatabase(name, mode, factory);
		}

		@Override
		public SQLiteDatabase openOrCreateDatabase(String name, int mode,
				SQLiteDatabase.CursorFactory factory) {
			return SQLiteDatabase.openOrCreateDatabase(
					getDatabasePath(name), null);
		}
	}
}
