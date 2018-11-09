package de.markusfisch.android.motoscore.activity;

import de.markusfisch.android.motoscore.adapter.RideAdapter;
import de.markusfisch.android.motoscore.app.MotoScoreApp;
import de.markusfisch.android.motoscore.data.Database;
import de.markusfisch.android.motoscore.export.RideExporter;
import de.markusfisch.android.motoscore.service.MotoScoreService;
import de.markusfisch.android.motoscore.widget.GraphView;
import de.markusfisch.android.motoscore.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class MainActivity extends AppCompatActivity {
	private static final int REQUEST_PERMISSIONS = 1;
	private static final SimpleDateFormat timeFormat = new SimpleDateFormat(
			"HH:mm:ss", Locale.getDefault());

	private final RideExporter.ExportListener exportListener =
			new RideExporter.ExportListener() {
		@Override
		public void onExportStarted() {
			showProgress();
		}

		@Override
		public void onExportFinished(String file) {
			hideProgress();
			String message;
			if (file == null) {
				message = getString(R.string.error_ride_export_failed);
			} else {
				message = String.format(Locale.getDefault(),
						getString(R.string.ride_exported_to),
						file);
			}
			Toast.makeText(MainActivity.this, message,
					Toast.LENGTH_LONG).show();
		}
	};
	private final MotoScoreService.ServiceListener serviceListener =
			new MotoScoreService.ServiceListener() {
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
			service.listener = null;
			service = null;
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
	private GraphView graphView;
	private ListView listView;
	private LinearLayout progressCircle;
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
		graphView = (GraphView) findViewById(R.id.stats);
		listView = (ListView) findViewById(R.id.rides);
		progressCircle = (LinearLayout) findViewById(R.id.progress);
		counterView = (View) findViewById(R.id.counter);
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
				new QueryNumberOfWaypoints().execute(id);
			}
		});

		registerForContextMenu(listView);
		graphView.listView = listView;
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

		update();
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (serviceBound) {
			unbindService(connection);
			serviceBound = false;
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
				startActivity(new Intent(this, PrefActivity.class));
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
				new RideExporter(info.id, exportListener);
				return true;
			case R.id.remove_ride:
				MotoScoreApp.db.removeRide(info.id);
				update();
				return true;
		}

		return false;
	}

	private void requestPermissions() {
		requestPermissions(
				this,
				new String[]{
						android.Manifest.permission.ACCESS_FINE_LOCATION,
						android.Manifest.permission.WRITE_EXTERNAL_STORAGE
				},
				new Runnable[]{
						null,
						null
				},
				REQUEST_PERMISSIONS);
	}

	private static void requestPermissions(
			Activity activity,
			String permissions[],
			Runnable runnables[],
			int requestCode) {
		List<String> missing = new ArrayList<>();
		int i = 0;
		for (String permission : permissions) {
			Runnable runnable = i < runnables.length ? runnables[i] : null;
			++i;
			if (ContextCompat.checkSelfPermission(activity, permission) !=
					PackageManager.PERMISSION_GRANTED) {
				missing.add(permission);
			} else if (runnable != null) {
				runnable.run();
			}
		}

		if (missing.size() < 1) {
			return;
		}

		ActivityCompat.requestPermissions(
				activity,
				missing.toArray(new String[missing.size()]),
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
		new QueryTotalAndRides().execute();
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
	private class QueryTotalAndRides extends AsyncTask<Void, Void, Integer> {
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
			totalRides = count.intValue();
			new QueryRides().execute();
		}
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has ended
	@SuppressLint("StaticFieldLeak")
	private class QueryRides extends AsyncTask<Void, Void, Cursor> {
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
			} else {
				listView.removeFooterView(showMoreView);
				adapter.changeCursor(cursor);

				if (totalRides > listLength) {
					listView.addFooterView(showMoreView);
				}
			}

			graphView.setCursor(cursor);
		}
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has ended
	@SuppressLint("StaticFieldLeak")
	private class QueryNumberOfWaypoints
			extends AsyncTask<Long, Void, Integer> {
		private long rideId;

		@Override
		protected Integer doInBackground(Long... rideIds) {
			showProgress();
			if (rideIds.length != 1) {
				return null;
			}
			rideId = rideIds[0].longValue();
			return MotoScoreApp.db.queryWaypointsCount(rideId);
		}

		@Override
		protected void onPostExecute(Integer count) {
			hideProgress();
			if (count == null || rideId < 1) {
				return;
			}
			if (count.intValue() < 1) {
				Toast.makeText(MainActivity.this, R.string.no_waypoints,
						Toast.LENGTH_LONG).show();
			} else {
				Intent intent = new Intent(MainActivity.this,
						RideViewActivity.class);
				intent.putExtra(Database.RIDES_ID, rideId);
				startActivity(intent);
			}
		}
	}
}
