package de.markusfisch.android.motoscore.io;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executors;

import de.markusfisch.android.motoscore.app.MotoScoreApp;
import de.markusfisch.android.motoscore.data.Database;

public class RideExporter {
	public interface ExportListener {
		void onExportStarted();

		void onExportFinished(String fileName);
	}

	public static void exportAsync(Context context, long id,
			ExportListener listener) {
		if (listener != null) {
			listener.onExportStarted();
		}
		exportRideAndWayPointsAsync(context, id, listener);
	}

	private static void exportRideAndWayPointsAsync(
			final Context context,
			final long id,
			final ExportListener listener) {
		Handler handler = new Handler(Looper.getMainLooper());
		Executors.newSingleThreadExecutor().execute(() -> {
			String date = MotoScoreApp.db.queryRideDate(id);
			handler.post(() -> {
				if (date == null) {
					finishExport(listener, null);
					return;
				}
				queryWaypointsAsync(context, id, "ride-" + date + ".kml",
						listener);
			});
		});
	}

	private static void queryWaypointsAsync(
			final Context context,
			final long id,
			final String fileName,
			final ExportListener listener) {
		Handler handler = new Handler(Looper.getMainLooper());
		Executors.newSingleThreadExecutor().execute(() -> {
			Cursor cursor = MotoScoreApp.db.queryWaypoints(id);
			handler.post(() -> {
				finishExport(listener,
						export(context, cursor, fileName) ? fileName : null);
			});
		});
	}

	private static void finishExport(
			ExportListener listener,
			String fileName) {
		if (listener != null) {
			listener.onExportFinished(fileName);
		}
	}

	private static boolean export(
			Context context,
			Cursor cursor,
			String fileName) {
		if (cursor == null) {
			return false;
		} else if (!cursor.moveToFirst()) {
			cursor.close();
			return false;
		}
		OutputStream out = null;
		try {
			out = ExternalFile.openExternalOutputStream(context, fileName,
					"application/vnd.google-earth.kml+xml");

			StringBuilder sb = new StringBuilder();
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
					"<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n" +
					"<Placemark>\n" +
					"<name>Ride</name>\n" +
					"<description>Ride way points.</description>\n" +
					"<LineString>\n" +
					"<tessellate>1</tessellate>\n" +
					"<coordinates>");
			int latIdx = cursor.getColumnIndex(
					Database.WAYPOINTS_LATITUDE);
			int lngIdx = cursor.getColumnIndex(
					Database.WAYPOINTS_LONGITUDE);

			do {
				double lat = cursor.getDouble(latIdx);
				double lng = cursor.getDouble(lngIdx);
				sb.append(lng).append(",").append(lat).append(",0\n");
			} while (cursor.moveToNext());

			sb.append("</coordinates>\n" +
					"</LineString>\n" +
					"</Placemark>\n" +
					"</kml>\n");

			out.write(sb.toString().getBytes("utf-8"));
			return true;
		} catch (IOException e) {
			return false;
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				// Ignore, can't do anything about it.
			}
			cursor.close();
		}
	}
}
