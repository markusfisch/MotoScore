package de.markusfisch.android.motoscore;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewTreeObserver;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

public class RideViewActivity
	extends ActionBarActivity
{
	private GoogleMap map;
	private long rideId;
	private Handler handler = new Handler();
	private Runnable retryAddRide = new Runnable()
	{
		@Override
		public void run()
		{
			addRide();
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

		addRide();
	}

	private void addRide()
	{
		handler.removeCallbacks( retryAddRide );

		if( !MotoScoreApplication.dataSource.ready() )
		{
			handler.postDelayed( retryAddRide, 100 );
			return;
		}

		new QueryWaypoints().execute( rideId );
	}

	private void addRide( Cursor cursor )
	{
		if( cursor == null ||
			!cursor.moveToFirst() )
			return;

		final PolylineOptions po =
			new PolylineOptions().geodesic( true );
		final int latIdx = cursor.getColumnIndex(
			MotoScoreDataSource.WAYPOINTS_LATITUDE );
		final int lngIdx = cursor.getColumnIndex(
			MotoScoreDataSource.WAYPOINTS_LONGITUDE );
		final LatLngBounds.Builder builder =
			new LatLngBounds.Builder();

		do
		{
			LatLng point = new LatLng(
				cursor.getDouble( latIdx ),
				cursor.getDouble( lngIdx ) );

			builder.include( point );
			po.add( point );

		} while( cursor.moveToNext() );

		map.addPolyline( po );

		final View mapView = findViewById( R.id.map );

		if( mapView == null )
			return;
		else if( mapView.getMeasuredWidth() > 0 &&
			mapView.getMeasuredHeight() > 0 )
			moveCamera( mapView, builder.build() );
		else
			mapView.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener()
				{
					@Override
					public void onGlobalLayout()
					{
						mapView
							.getViewTreeObserver()
							.removeGlobalOnLayoutListener( this );

						moveCamera( mapView, builder.build() );
					}
				} );
	}

	private void moveCamera( View mapView, LatLngBounds bounds )
	{
		map.moveCamera(
			CameraUpdateFactory.newLatLngBounds(
				bounds,
				// if width or height is 0, which should
				// never happen here, CameraUpdateFactory
				// throws a IllegalStateException nobody
				// wants
				Math.max( 240, mapView.getMeasuredWidth() ),
				Math.max( 240, mapView.getMeasuredHeight() ),
				16 ) );
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
			addRide( cursor );
		}
	}
}
