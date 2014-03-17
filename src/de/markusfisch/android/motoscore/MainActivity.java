package de.markusfisch.android.motoscore;

import android.support.v7.app.ActionBarActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.text.SimpleDateFormat;

public class MainActivity
	extends ActionBarActivity
	implements MotoScoreService.MotoScoreServiceListener
{
	private static final SimpleDateFormat startDateFormat =
		new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
	private static final SimpleDateFormat nowDateFormat =
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
	private StatsView statsView;
	private ListView listView;
	private View counterView;
	private TextView dateTextView;
	private TextView mistakesTextView;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		setContentView( R.layout.activity_main );

		getSupportActionBar().setHomeButtonEnabled( false );

		statsView = (StatsView)findViewById( R.id.stats );
		listView = (ListView)findViewById( R.id.rides );
		counterView = (View)findViewById( R.id.counter );
		dateTextView = (TextView)findViewById( R.id.date );
		mistakesTextView = (TextView)findViewById( R.id.mistakes );

		counterView.setOnClickListener(
			new View.OnClickListener()
			{
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
		statsView.listView = listView;

		// start the service to keep it running without activities
		startService( new Intent(
			this,
			MotoScoreService.class ) );
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
					R.drawable.ic_menu_stop :
					R.drawable.ic_menu_start );
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
	public void onMotoScoreUpdate()
	{
		setState();
		refresh();
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
				R.drawable.ic_menu_stop :
				R.drawable.ic_menu_start );
	}

	private void updateTime()
	{
		handler.removeCallbacks( updateTimeRunnable );

		if( service == null ||
			!service.recording() )
			return;

		dateTextView.setText( getRideDate(
			service.rideStart,
			new Date() ) );

		handler.postDelayed( updateTimeRunnable, 1000 );
	}

	private String getRideDate( Date start, Date stop )
	{
		return
			startDateFormat.format( start )+" - "+
			nowDateFormat.format( stop );
	}

	private void refresh()
	{
		query();

		if( service != null &&
			service.recording() )
		{
			updateTime();

			mistakesTextView.setText(
				String.format( "%d", service.mistakes ) );
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

		new QueryRides().execute( 30 );
	}

	private class QueryRides
		extends AsyncTask<Integer, Void, Cursor>
	{
		@Override
		protected Cursor doInBackground( Integer... limits )
		{
			if( limits.length != 1 )
				return null;

			return MotoScoreApplication
				.dataSource
				.queryRides( limits[0].intValue() );
		}

		@Override
		protected void onProgressUpdate( Void... nothing )
		{
		}

		@Override
		protected void onPostExecute( Cursor cursor )
		{
			if( cursor == null )
				return;

			if( adapter == null )
			{
				adapter = new MotoScoreAdapter(
					MainActivity.this,
					cursor );

				listView.setAdapter( adapter );
			}
			else
				adapter.changeCursor( cursor );

			statsView.setCursor( cursor );
		}
	}

	private class QueryNumberOfWaypoints
		extends AsyncTask<Long, Void, Integer>
	{
		private long rideId;

		@Override
		protected Integer doInBackground( Long... rideIds )
		{
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
