package de.markusfisch.android.motoscore;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.text.SimpleDateFormat;

public class MainActivity
	extends ActionBarActivity
	implements
		MotoScoreService.MotoScoreServiceListener,
		RideExporter.ExportListener
{
	private static final SimpleDateFormat timeFormat =
		new SimpleDateFormat( "HH:mm:ss" );
	private final ServiceConnection connection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(
			ComponentName className,
			IBinder binder )
		{
			service = ((MotoScoreService.Binder)binder).getService();
			service.listener = MainActivity.this;

			// (re-)register media button because another app
			// may have registered itself in the meantime
			service.registerMediaButton();

			listLength = service.preferences.numberOfRides();

			setState();
			refresh();
		}

		@Override
		public void onServiceDisconnected( ComponentName className )
		{
			service.listener = null;
			service = null;
		}
	};
	private final Runnable queryRetryRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			query();
		}
	};
	private final Runnable updateTimeRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			updateTime();
		}
	};
	private boolean serviceBound = false;
	private MotoScoreService service = null;
	private MotoScoreAdapter adapter = null;
	private Handler handler = new Handler();
	private MenuItem startMenuItem;
	private GraphView graphView;
	private ListView listView;
	private LinearLayout progressCircle;
	private View counterView;
	private TextView dateTextView;
	private TextView distanceTextView;
	private TextView mistakesTextView;
	private View showMoreView;
	private int totalRides = 0;
	private int listLength = 100;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		setContentView( R.layout.activity_main );

		getSupportActionBar().setHomeButtonEnabled( false );

		graphView = (GraphView)findViewById( R.id.stats );
		listView = (ListView)findViewById( R.id.rides );
		progressCircle = (LinearLayout)findViewById( R.id.progress );
		counterView = (View)findViewById( R.id.counter );
		dateTextView = (TextView)findViewById( R.id.date );
		distanceTextView = (TextView)findViewById( R.id.distance );
		mistakesTextView = (TextView)findViewById( R.id.mistakes );

		counterView.setOnClickListener(
			new View.OnClickListener()
			{
				@Override
				public void onClick( View v )
				{
					if( service != null )
						service.count();
				}
			} );

		listView.setEmptyView( findViewById( R.id.no_rides ) );
		listView.setOnItemClickListener(
			new AdapterView.OnItemClickListener()
			{
				@Override
				public void onItemClick(
					AdapterView<?> parent,
					View view,
					int position,
					long id )
				{
					new QueryNumberOfWaypoints().execute( id );
				}
			} );

		registerForContextMenu( listView );
		graphView.listView = listView;

		// start the service to keep it running without activities
		startService( new Intent(
			this,
			MotoScoreService.class ) );
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		// close cursor
		if( adapter != null )
			adapter.changeCursor( null );
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// bind the service to be notified of new countings
		// while visible
		if( !(serviceBound = bindService(
			new Intent(
				this,
				MotoScoreService.class ),
			connection,
			Context.BIND_AUTO_CREATE )) )
			Toast.makeText(
				this,
				R.string.error_service,
				Toast.LENGTH_LONG ).show();

		refresh();
	}

	@Override
	public void onPause()
	{
		super.onPause();

		if( serviceBound )
			unbindService( connection );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu )
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate( R.menu.main_activity_options, menu );

		if( (startMenuItem = menu.findItem( R.id.start )) != null )
			setState();

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		switch( item.getItemId() )
		{
			case R.id.start:
				startStop();

				item.setIcon( service.recording() ?
					R.drawable.ic_action_stop :
					R.drawable.ic_action_start );
				return true;
			case R.id.preferences:
				startActivity( new Intent(
					this,
					MotoScorePreferenceActivity.class ) );
				return true;
		}

		return super.onOptionsItemSelected( item );
	}

	@Override
	public void onCreateContextMenu(
		ContextMenu menu,
		View v,
		ContextMenuInfo menuInfo )
	{
		super.onCreateContextMenu( menu, v, menuInfo );

		MenuInflater inflater = getMenuInflater();
		inflater.inflate( R.menu.ride_options, menu );
	}

	@Override
	public boolean onContextItemSelected( MenuItem item )
	{
		AdapterView.AdapterContextMenuInfo info =
			(AdapterView.AdapterContextMenuInfo)item.getMenuInfo();

		switch( item.getItemId() )
		{
			case R.id.export_ride:
				new RideExporter(
					info.id,
					this );
				return true;
			case R.id.remove_ride:
				MotoScoreApplication
					.dataSource
					.removeRide( info.id );

				refresh();
				return true;
		}

		return false;
	}

	@Override
	public void onMistakeUpdate()
	{
		updateMistakes();
	}

	@Override
	public void onDataUpdate()
	{
		setState();
		refresh();
	}

	@Override
	public void onExportStarted()
	{
		showProgressCircle();
	}

	@Override
	public void onExportFinished( String file )
	{
		hideProgressCircle();

		String message;

		if( file == null )
			message = getString( R.string.error_ride_export_failed );
		else
			message = String.format(
				getString( R.string.ride_exported_to ),
				file );

		Toast.makeText(
			MainActivity.this,
			message,
			Toast.LENGTH_LONG ).show();
	}

	public void startStop()
	{
		if( service == null )
			return;

		if( service.recording() )
			service.stop();
		else
			service.start();

		setState();
		refresh();
	}

	private void setState()
	{
		if( service == null )
			return;

		if( counterView != null )
			counterView.setVisibility( service.recording() ?
				View.VISIBLE :
				View.GONE );

		if( startMenuItem != null )
			startMenuItem.setIcon( service.recording() ?
				R.drawable.ic_action_stop :
				R.drawable.ic_action_start );
	}

	private void updateMistakes()
	{
		if( service == null ||
			!service.recording() )
			return;

		mistakesTextView.setText(
			String.format( "%d", service.mistakes ) );
	}

	private void updateTime()
	{
		handler.removeCallbacks( updateTimeRunnable );

		if( service == null ||
			!service.recording() )
			return;

		dateTextView.setText( getRideTime(
			service.rideStart,
			new Date() ) );

		distanceTextView.setText( service.waypoints > 0 ?
			String.format( "%.1f %s",
				service.distance/1000,
				getString( R.string.km ) ) :
			getString( R.string.awaiting_gps_fix ) );

		handler.postDelayed( updateTimeRunnable, 1000 );
	}

	private static String getRideTime( Date start, Date stop )
	{
		return
			timeFormat.format( start )+" - "+
			timeFormat.format( stop );
	}

	private void refresh()
	{
		query();

		if( service != null &&
			service.recording() )
		{
			updateTime();
			updateMistakes();
		}
	}

	private void query()
	{
		handler.removeCallbacks( queryRetryRunnable );

		if( service == null ||
			!MotoScoreApplication.dataSource.ready() )
		{
			handler.postDelayed( queryRetryRunnable, 500 );
			return;
		}

		new QueryTotalAndRides().execute();
	}

	private void showProgressCircle()
	{
		progressCircle.post( new Runnable()
		{
			@Override
			public void run()
			{
				progressCircle.setVisibility( View.VISIBLE );
			}
		} );
	}

	private void hideProgressCircle()
	{
		progressCircle.setVisibility( View.GONE );
	}

	private class QueryTotalAndRides
		extends AsyncTask<Void, Void, Integer>
	{
		@Override
		protected Integer doInBackground( Void... nothing )
		{
			showProgressCircle();

			return MotoScoreApplication
				.dataSource
				.queryNumberOfRides();
		}

		@Override
		protected void onProgressUpdate( Void... nothing )
		{
		}

		@Override
		protected void onPostExecute( Integer count )
		{
			hideProgressCircle();

			if( count == null )
				return;

			totalRides = count.intValue();

			new QueryRides().execute();
		}
	}

	private class QueryRides
		extends AsyncTask<Void, Void, Cursor>
	{
		@Override
		protected Cursor doInBackground( Void... nothing )
		{
			showProgressCircle();

			return MotoScoreApplication
				.dataSource
				.queryRides(
					listLength,
					service.preferences.score() );
		}

		@Override
		protected void onProgressUpdate( Void... nothing )
		{
		}

		@Override
		protected void onPostExecute( Cursor cursor )
		{
			hideProgressCircle();

			if( cursor == null )
				return;

			if( adapter == null )
			{
				showMoreView =
					MainActivity.this.getLayoutInflater().inflate(
						R.layout.show_more,
						null );

				showMoreView.setOnClickListener(
					new View.OnClickListener()
					{
						@Override
						public void onClick( View v )
						{
							listLength += 100;
							query();
						}
					} );

				// it's required to call addFooterView()
				// BEFORE setting the adapter (fixed in KitKat)
				listView.addFooterView( showMoreView );

				adapter = new MotoScoreAdapter(
					MainActivity.this,
					cursor );

				listView.setAdapter( adapter );

				if( totalRides < listLength )
					listView.removeFooterView( showMoreView );
			}
			else
			{
				listView.removeFooterView( showMoreView );

				adapter.changeCursor( cursor );

				if( totalRides > listLength )
					listView.addFooterView( showMoreView );
			}

			graphView.setCursor( cursor );
		}
	}

	private class QueryNumberOfWaypoints
		extends AsyncTask<Long, Void, Integer>
	{
		private long rideId;

		@Override
		protected Integer doInBackground( Long... rideIds )
		{
			showProgressCircle();

			if( rideIds.length != 1 )
				return null;

			rideId = rideIds[0].longValue();

			return MotoScoreApplication
				.dataSource
				.queryWaypointsCount( rideId );
		}

		@Override
		protected void onProgressUpdate( Void... nothing )
		{
		}

		@Override
		protected void onPostExecute( Integer count )
		{
			hideProgressCircle();

			if( count == null ||
				rideId < 1 )
				return;

			if( count.intValue() < 1 )
			{
				Toast.makeText(
					MainActivity.this,
					R.string.no_waypoints,
					Toast.LENGTH_LONG ).show();
			}
			else
			{
				Intent intent = new Intent(
					MainActivity.this,
					RideViewActivity.class );

				intent.putExtra(
					MotoScoreDataSource.RIDES_ID,
					rideId );

				startActivity( intent );
			}
		}
	}
}
