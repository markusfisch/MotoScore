package de.markusfisch.android.motoscore;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

public class RideViewActivity
	extends ActionBarActivity
{
	private GoogleMap map;
	private long rideId;
	private Handler handler = new Handler();
	private Runnable retryPlot = new Runnable()
	{
		@Override
		public void run()
		{
			plot();
		}
	};

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		setContentView( R.layout.activity_ride_view );

		getSupportActionBar().setDisplayHomeAsUpEnabled( true );

		setUpMapIfNeeded();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		setUpMapIfNeeded();
	}

	private void setUpMapIfNeeded()
	{
		if( map != null )
			return;

		map = ((SupportMapFragment)getSupportFragmentManager()
			.findFragmentById( R.id.map )).getMap();

		Intent intent = getIntent();

		if( intent == null ||
			(rideId = intent.getLongExtra(
				MotoScoreDataSource.RIDES_ID,
				0 )) < 1 )
			return;

		plot();
	}

	private void plot()
	{
		handler.removeCallbacks( retryPlot );

		if( !MotoScoreApplication.dataSource.ready() )
		{
			handler.postDelayed( retryPlot, 100 );
			return;
		}

		new QueryWaypoints().execute( rideId );
	}

	private class QueryWaypoints extends AsyncTask<Long, Void, Cursor>
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
			if( cursor == null ||
				!cursor.moveToFirst() )
				return;

			PolylineOptions po =
				new PolylineOptions().geodesic( true );
			int latIdx = cursor.getColumnIndex(
				MotoScoreDataSource.WAYPOINTS_LATITUDE );
			int lngIdx = cursor.getColumnIndex(
				MotoScoreDataSource.WAYPOINTS_LONGITUDE );
			LatLng start = null;

			do
			{
				LatLng ll = new LatLng(
					cursor.getDouble( latIdx ),
					cursor.getDouble( lngIdx ) );

				if( start == null )
					start = ll;

				po.add( ll );

			} while( cursor.moveToNext() );

			map.addPolyline( po );

			map.moveCamera( CameraUpdateFactory.newLatLngZoom(
				start,
				18 ) );
		}
	}
}
