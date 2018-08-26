package de.markusfisch.android.motoscore.export;

import de.markusfisch.android.motoscore.app.MotoScoreApp;
import de.markusfisch.android.motoscore.data.Database;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class RideExporter {
	public interface ExportListener {
		void onExportStarted();
		void onExportFinished(String file);
	}

	private String name;
	private ExportListener listener;
	private String exportedFile = null;

	public RideExporter(long id, ExportListener listener) {
		this.listener = listener;
		exportStarted();
		new QueryRideAndWaypoints().execute(id);
	}

	private void export(Cursor cursor, String name) {
		if (cursor == null) {
			return;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			return;
		}

		try {
			File dir = new File(Environment
					.getExternalStorageDirectory()
					.getAbsolutePath() + "/MotoScore");

			dir.mkdirs();

			File file = new File(dir, name);
			FileOutputStream out = null;

			try {
				out = new FileOutputStream(file);

				String s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
						"<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n" +
						"<Placemark>\n" +
						"<name>Ride</name>\n" +
						"<description>Ride way points.</description>\n" +
						"<LineString>\n" +
						"<tessellate>1</tessellate>\n" +
						"<coordinates>";
				int latIdx = cursor.getColumnIndex(
						Database.WAYPOINTS_LATITUDE);
				int lngIdx = cursor.getColumnIndex(
						Database.WAYPOINTS_LONGITUDE);

				do {
					double lat = cursor.getDouble(latIdx);
					double lng = cursor.getDouble(lngIdx);
					s += lng + "," + lat + ",0\n";
				} while (cursor.moveToNext());

				s += "</coordinates>\n" +
						"</LineString>\n" +
						"</Placemark>\n" +
						"</kml>\n";

				byte bytes[] = s.getBytes("utf-8");
				out.write(bytes, 0, bytes.length);
				exportedFile = file.getAbsolutePath();
			} finally {
				if (out != null) {
					out.close();
				}
			}
		} catch (IOException e) {
			// ignore
		}

		cursor.close();
	}

	private void exportStarted() {
		exportedFile = null;
		if (listener != null) {
			listener.onExportStarted();
		}
	}

	private void exportFinished() {
		if (listener != null) {
			listener.onExportFinished(exportedFile);
		}
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has ended
	@SuppressLint("StaticFieldLeak")
	private class QueryRideAndWaypoints
			extends AsyncTask<Long, Void, String> {
		long id;

		@Override
		protected String doInBackground(Long... rideIds) {
			if (rideIds.length != 1) {
				return null;
			}
			return MotoScoreApp.db.queryRideDate(
					(id = rideIds[0].longValue()));
		}

		@Override
		protected void onProgressUpdate(Void... nothing) {
		}

		@Override
		protected void onPostExecute(String date) {
			if (date == null) {
				exportFinished();
				return;
			}

			name = "ride-" + date + ".kml";
			new QueryWaypoints().execute(id);
		}
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has ended
	@SuppressLint("StaticFieldLeak")
	private class QueryWaypoints extends AsyncTask<Long, Void, Cursor> {
		@Override
		protected Cursor doInBackground(Long... rideIds) {
			if (rideIds.length != 1) {
				return null;
			}
			return MotoScoreApp.db.queryWaypoints(
					rideIds[0].longValue());
		}

		@Override
		protected void onProgressUpdate(Void... nothing) {
		}

		@Override
		protected void onPostExecute(Cursor cursor) {
			export(cursor, name);
			exportFinished();
		}
	}
}
