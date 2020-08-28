package de.markusfisch.android.motoscore.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.adapter.RideAdapter;
import de.markusfisch.android.motoscore.app.MotoScoreApp;
import de.markusfisch.android.motoscore.data.Database;
import de.markusfisch.android.motoscore.io.RideExporter;
import de.markusfisch.android.motoscore.service.MotoScoreService;
import de.markusfisch.android.motoscore.widget.RideListView;

public class MainActivity extends AppCompatActivity {
	private static final int REQUEST_PERMISSIONS = 1;

	private final RideExporter.ExportListener exportListener = new RideExporter.ExportListener() {
		@Override
		public void onExportStarted() {
			showProgress();
		}

		@Override
		public void onExportFinished(String fileName) {
			hideProgress();
			String message;
			if (fileName == null) {
				message = getString(R.string.error_ride_export_failed);
			} else {
				message = String.format(Locale.getDefault(),
						getString(R.string.ride_exported_to),
						fileName);
			}
			Toast.makeText(MainActivity.this, message,
					Toast.LENGTH_LONG).show();
		}
	};
	private final MotoScoreService.ServiceListener serviceListener = new MotoScoreService.ServiceListener() {
		@Override
		public void onMistakeUpdate() {
			updateMistakes();
		}

		@Override
		public void onDataUpdate() {
			setState();
			update();
		}
	};
	private final ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className,
				IBinder binder) {
			service = ((MotoScoreService.Binder) binder).getService();
			service.listener = serviceListener;

			// (re-)register media button because another app
			// may have registered itself in the meantime
			service.registerMediaButton();

			setState();
			update();
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			// onServiceDisconnected() gets called when the service
			// is killed only!
			disconnectFromService();
		}
	};
	private final Runnable updateRunnable = new Runnable() {
		@Override
		public void run() {
			update();
		}
	};
	private final Runnable updateTimeRunnable = new Runnable() {
		@Override
		public void run() {
			updateTime();
		}
	};
	private final Handler handler = new Handler();

	private boolean serviceBound = false;
	private MotoScoreService service = null;
	private RideAdapter adapter = null;
	private RideListView listView;
	private Parcelable listViewState;
	private View progressCircle;
	private View counterView;
	private TextView dateTextView;
	private TextView distanceTextView;
	private TextView mistakesTextView;
	private View showMoreView;
	private FloatingActionButton fab;
	private int totalRides = 0;
	private int listLength = MotoScoreApp.preferences.numberOfRides();

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_main);
		requestPermissions();

		fab = (FloatingActionButton) findViewById(R.id.start);
		listView = (RideListView) findViewById(R.id.rides);
		progressCircle = findViewById(R.id.progress);
		counterView = findViewById(R.id.counter);
		dateTextView = (TextView) findViewById(R.id.date);
		distanceTextView = (TextView) findViewById(R.id.distance);
		mistakesTextView = (TextView) findViewById(R.id.mistakes);

		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startStop();
			}
		});

		counterView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (service != null) {
					service.count();
				}
			}
		});

		listView.setEmptyView(findViewById(R.id.no_rides));
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id) {
				queryNumberOfWaypointsAsync(id);
			}
		});

		registerForContextMenu(listView);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		// close cursor
		if (adapter != null) {
			adapter.changeCursor(null);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		update();
	}

	@Override
	protected void onPause() {
		super.onPause();
		listViewState = listView.onSaveInstanceState();
	}

	@Override
	protected void onStart() {
		super.onStart();

		// bind the service to be notified of new countings
		// while visible
		serviceBound = bindService(
				new Intent(this, MotoScoreService.class),
				connection,
				Context.BIND_AUTO_CREATE);
		if (!serviceBound) {
			Toast.makeText(this, R.string.error_service,
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (serviceBound) {
			unbindService(connection);
			serviceBound = false;
			disconnectFromService();
		}
	}

	private void disconnectFromService() {
		if (service != null) {
			service.listener = null;
			service = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_options, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.preferences:
				startActivity(new Intent(this, PreferenceActivity.class));
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(
			ContextMenu menu,
			View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		getMenuInflater().inflate(R.menu.ride_options, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info =
				(AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

		switch (item.getItemId()) {
			case R.id.export_ride:
				RideExporter.exportAsync(this, info.id, exportListener);
				return true;
			case R.id.remove_ride:
				MotoScoreApp.db.removeRide(info.id);
				update();
				return true;
		}

		return false;
	}

	private void requestPermissions() {
		ArrayList<String> permissions = new ArrayList<>();
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
			permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
		}
		permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
		permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
		requestPermissions(this, permissions, REQUEST_PERMISSIONS);
	}

	private static void requestPermissions(
			Activity activity,
			List<String> permissions,
			int requestCode) {
		List<String> missing = new ArrayList<>();
		for (String permission : permissions) {
			if (ContextCompat.checkSelfPermission(activity, permission) !=
					PackageManager.PERMISSION_GRANTED) {
				missing.add(permission);
			}
		}
		if (missing.size() < 1) {
			return;
		}
		ActivityCompat.requestPermissions(
				activity,
				missing.toArray(new String[0]),
				requestCode);
	}

	public void startStop() {
		if (service == null) {
			return;
		}
		if (service.isRecording()) {
			service.stop();
		} else {
			startService(new Intent(this, MotoScoreService.class));
		}
	}

	private void setState() {
		if (service == null) {
			return;
		}
		boolean isRecording = service.isRecording();
		counterView.setVisibility(isRecording ?
				View.VISIBLE :
				View.GONE);
		fab.setImageResource(isRecording ?
				R.drawable.ic_action_stop :
				R.drawable.ic_action_start);
	}

	private void updateMistakes() {
		if (service == null || !service.isRecording()) {
			return;
		}
		mistakesTextView.setText(String.format(Locale.getDefault(),
				"%d", service.mistakes));
	}

	private void updateTime() {
		handler.removeCallbacks(updateTimeRunnable);
		if (service == null || !service.isRecording()) {
			return;
		}

		dateTextView.setText(getRideTime(service.rideStart, new Date()));
		distanceTextView.setText(service.waypoints > 0 ?
				String.format(Locale.getDefault(), "%.1f %s",
						service.distance / 1000, getString(R.string.km)) :
				getString(R.string.awaiting_gps_fix));

		handler.postDelayed(updateTimeRunnable, 1000);
	}

	private static String getRideTime(Date start, Date stop) {
		// create a new object because Locale.getDefault() may have
		// changed while the app is running
		SimpleDateFormat timeFormat = new SimpleDateFormat(
				"HH:mm:ss", Locale.getDefault());
		return timeFormat.format(start) + " - " + timeFormat.format(stop);
	}

	private void update() {
		handler.removeCallbacks(updateRunnable);
		if (service == null || !MotoScoreApp.db.isOpen()) {
			handler.postDelayed(updateRunnable, 500);
			return;
		}
		if (service.isRecording()) {
			updateTime();
			updateMistakes();
		}
		queryTotalAndRidesAsync();
	}

	private void showProgress() {
		progressCircle.post(new Runnable() {
			@Override
			public void run() {
				progressCircle.setVisibility(View.VISIBLE);
			}
		});
	}

	private void hideProgress() {
		progressCircle.setVisibility(View.GONE);
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has ended
	@SuppressLint("StaticFieldLeak")
	private void queryTotalAndRidesAsync() {
		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected Integer doInBackground(Void... nothing) {
				showProgress();
				return MotoScoreApp.db.queryNumberOfRides();
			}

			@Override
			protected void onPostExecute(Integer count) {
				hideProgress();
				if (count == null) {
					return;
				}
				totalRides = count;
				queryRidesAsync();
			}
		}.execute();
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has ended
	@SuppressLint("StaticFieldLeak")
	private void queryRidesAsync() {
		new AsyncTask<Void, Void, Cursor>() {
			@Override
			protected Cursor doInBackground(Void... nothing) {
				showProgress();
				return MotoScoreApp.db.queryRides(listLength,
						MotoScoreApp.preferences.score());
			}

			@Override
			protected void onPostExecute(Cursor cursor) {
				hideProgress();
				if (cursor == null) {
					return;
				}
				updateAdapter(cursor);
			}
		}.execute();
	}

	@SuppressLint("InflateParams")
	private void updateAdapter(Cursor cursor) {
		if (adapter == null) {
			showMoreView = MainActivity.this.getLayoutInflater().inflate(
					R.layout.show_more,
					null);
			showMoreView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					listLength += 100;
					update();
				}
			});

			// it's required to call addFooterView()
			// BEFORE setting the adapter (fixed in KitKat)
			listView.addFooterView(showMoreView);

			adapter = new RideAdapter(MainActivity.this, cursor);
			listView.setAdapter(adapter);

			if (totalRides < listLength) {
				listView.removeFooterView(showMoreView);
			}

			if (listViewState != null) {
				listView.onRestoreInstanceState(listViewState);
				listViewState = null;
			}
		} else {
			listView.removeFooterView(showMoreView);
			adapter.changeCursor(cursor);

			if (totalRides > listLength) {
				listView.addFooterView(showMoreView);
			}
		}
		listView.updateGraph(cursor);
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has ended
	@SuppressLint("StaticFieldLeak")
	private void queryNumberOfWaypointsAsync(final long rideId) {
		new AsyncTask<Void, Void, Integer>() {
			@Override
			protected Integer doInBackground(Void... nothings) {
				showProgress();
				return MotoScoreApp.db.queryWaypointsCount(rideId);
			}

			@Override
			protected void onPostExecute(Integer count) {
				hideProgress();
				if (count == null || count < 1) {
					Toast.makeText(MainActivity.this, R.string.no_waypoints,
							Toast.LENGTH_LONG).show();
				} else {
					Intent intent = new Intent(MainActivity.this,
							RideViewActivity.class);
					intent.putExtra(Database.RIDES_ID, rideId);
					startActivity(intent);
				}
			}
		}.execute();
	}
}
