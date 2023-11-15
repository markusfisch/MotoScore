package de.markusfisch.android.motoscore.activity;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.concurrent.Executors;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.app.MotoScoreApp;
import de.markusfisch.android.motoscore.data.Database;

public class RideViewActivity extends AppCompatActivity {
	private GoogleMap map;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		setContentView(R.layout.activity_ride_view);
		setUpMapIfNeeded();
	}

	@Override
	protected void onResume() {
		super.onResume();
		setUpMapIfNeeded();
	}

	private void setUpMapIfNeeded() {
		if (map != null) {
			return;
		}

		final long rideId;
		Intent intent = getIntent();
		if (intent != null) {
			rideId = intent.getLongExtra(Database.RIDES_ID, 0);
		} else {
			rideId = 0;
		}

		SupportMapFragment mapFragment = ((SupportMapFragment) getSupportFragmentManager()
				.findFragmentById(R.id.map));
		if (mapFragment == null) {
			return;
		}
		mapFragment.getMapAsync(googleMap -> {
			map = googleMap;
			if (rideId > 0) {
				addRideAsync(rideId);
			}
		});
	}

	private void addRideAsync(final long rideId) {
		Handler handler = new Handler(Looper.getMainLooper());
		Executors.newSingleThreadExecutor().execute(() -> {
			Cursor cursor = MotoScoreApp.db.queryWaypoints(rideId);
			handler.post(() -> {
				addRide(cursor);
			});
		});
	}

	private void addRide(Cursor cursor) {
		if (cursor == null || !cursor.moveToFirst()) {
			return;
		}

		PolylineOptions po = new PolylineOptions().geodesic(true);
		int latIdx = cursor.getColumnIndex(Database.WAYPOINTS_LATITUDE);
		int lngIdx = cursor.getColumnIndex(Database.WAYPOINTS_LONGITUDE);
		final LatLngBounds.Builder builder = new LatLngBounds.Builder();

		do {
			LatLng point = new LatLng(
					cursor.getDouble(latIdx),
					cursor.getDouble(lngIdx));
			builder.include(point);
			po.add(point);
		} while (cursor.moveToNext());

		cursor.close();
		map.addPolyline(po);

		final View mapView = findViewById(R.id.map);
		if (mapView.getMeasuredWidth() > 0 &&
				mapView.getMeasuredHeight() > 0) {
			moveCamera(mapView, builder.build());
		} else {
			mapView.getViewTreeObserver().addOnGlobalLayoutListener(
					new ViewTreeObserver.OnGlobalLayoutListener() {
						@Override
						public void onGlobalLayout() {
							mapView.getViewTreeObserver()
									.removeGlobalOnLayoutListener(this);
							moveCamera(mapView, builder.build());
						}
					});
		}
	}

	private void moveCamera(View mapView, LatLngBounds bounds) {
		map.moveCamera(CameraUpdateFactory.newLatLngBounds(
				bounds,
				// If width or height is 0, which should never happen here,
				// CameraUpdateFactory throws an IllegalStateException
				// nobody wants.
				Math.max(240, mapView.getMeasuredWidth()),
				Math.max(240, mapView.getMeasuredHeight()),
				16));
	}
}
