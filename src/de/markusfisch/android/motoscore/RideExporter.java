package de.markusfisch.android.motoscore;

import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RideExporter
{
	public interface ExportListener
	{
		public void onExportStarted();
		public void onExportFinished();
	}

	private String name;
	private ExportListener listener;

	public RideExporter( long id, ExportListener listener )
	{
		this.listener = listener;

		exportStarted();
		new QueryRideAndWaypoints().execute( id );
	}

	private void export( Cursor cursor, String name )
	{
		if( cursor == null ||
			!cursor.moveToFirst() )
			return;

		try
		{
			final File dir = new File(
				Environment
					.getExternalStorageDirectory()
					.getAbsolutePath()+"/MotoScore" );

			dir.mkdirs();

			final File file = new File( dir, name );

			FileOutputStream out = null;

			try
			{
				out = new FileOutputStream( file );

				String s =
					"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
					"<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n"+
					"<Placemark>\n"+
					"<name>Ride</name>\n"+
					"<description>Ride way points.</description>\n"+
					"<LineString>\n"+
					"<tessellate>1</tessellate>\n"+
					"<coordinates>";

				final int latIdx = cursor.getColumnIndex(
					MotoScoreDataSource.WAYPOINTS_LATITUDE );
				final int lngIdx = cursor.getColumnIndex(
					MotoScoreDataSource.WAYPOINTS_LONGITUDE );

				do
				{
					double lat = cursor.getDouble( latIdx );
					double lng = cursor.getDouble( lngIdx );

					s += lng+","+lat+",0\n";

				} while( cursor.moveToNext() );

				s +=
					"</coordinates>\n"+
					"</LineString>\n"+
					"</Placemark>\n"+
					"</kml>\n";

				byte bytes[] = s.getBytes();

				out.write( bytes, 0, bytes.length );
			}
			finally
			{
				if( out != null )
					out.close();
			}
		}
		catch( Exception e )
		{
		}
	}

	private void exportStarted()
	{
		if( listener != null )
			listener.onExportStarted();
	}

	private void exportFinished()
	{
		if( listener != null )
			listener.onExportFinished();
	}

	private class QueryRideAndWaypoints
		extends AsyncTask<Long, Void, String>
	{
		long id;

		@Override
		protected String doInBackground( Long... rideIds )
		{
			if( rideIds.length != 1 )
				return null;

			return MotoScoreApplication.dataSource.queryRideDate(
				(id = rideIds[0].longValue()) );
		}

		@Override
		protected void onProgressUpdate( Void... nothing )
		{
		}

		@Override
		protected void onPostExecute( String date )
		{
			if( date == null )
			{
				exportFinished();
				return;
			}

			name = "ride-"+date+".kml";

			new QueryWaypoints().execute( id );
		}
	}

	private class QueryWaypoints
		extends AsyncTask<Long, Void, Cursor>
	{
		@Override
		protected Cursor doInBackground( Long... rideIds )
		{
			if( rideIds.length != 1 )
				return null;

			return MotoScoreApplication.dataSource.queryWaypoints(
				rideIds[0].longValue() );
		}

		@Override
		protected void onProgressUpdate( Void... nothing )
		{
		}

		@Override
		protected void onPostExecute( Cursor cursor )
		{
			export( cursor, name );

			if( cursor != null )
				cursor.close();

			exportFinished();
		}
	}
}
