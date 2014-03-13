package de.markusfisch.android.motoscore;

import android.support.v7.app.ActionBarActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
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

public class MainActivity
	extends ActionBarActivity
	implements MotoScoreService.MotoScoreServiceListener
{
	private final ServiceConnection connection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(
			ComponentName className,
			IBinder binder )
		{
			service = ((MotoScoreService.Binder)binder).getService();
			service.listener = MainActivity.this;

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
		statsView.listView = listView;
		registerForContextMenu( listView );

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
				start();

				item.setIcon( service.started ?
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
				service.dataSource.remove( info.id );
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

	public void start()
	{
		if( service.started )
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
			counterView.setVisibility( service.started ?
				View.VISIBLE :
				View.GONE );

		if( startMenuItem != null )
			startMenuItem.setIcon( service.started ?
				R.drawable.ic_menu_stop :
				R.drawable.ic_menu_start );
	}

	private void updateTime()
	{
		handler.removeCallbacks( updateTimeRunnable );

		if( service == null ||
			!service.started )
			return;

		dateTextView.setText(
			MotoScoreAdapter.getRideDate(
				service.rideStart,
				new Date() ) );

		handler.postDelayed( updateTimeRunnable, 1000 );
	}

	private void refresh()
	{
		query();

		if( service != null &&
			service.started )
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
			!service.dataSource.ready() )
		{
			handler.postDelayed( queryRetryRunnable, 500 );
			return;
		}

		Cursor c = service.dataSource.queryAll();

		if( adapter == null )
		{
			adapter = new MotoScoreAdapter( this, c );
			listView.setAdapter( adapter );
		}
		else
			adapter.changeCursor( c );

		statsView.setCursor( c );
	}
}
