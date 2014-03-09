package de.markusfisch.android.counter;

import android.app.Activity;
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
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

public class MainActivity
	extends Activity
	implements CounterService.CounterServiceListener
{
	private final ServiceConnection connection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(
			ComponentName className,
			IBinder binder )
		{
			service = ((CounterService.Binder)binder).getService();
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
	private CounterService service = null;
	private CounterAdapter adapter = null;
	private Handler handler = new Handler();
	private StatsView statsView;
	private ListView listView;
	private ImageButton startButton;
	private View counterView;
	private TextView dateTextView;
	private TextView mistakesTextView;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		// start the service to keep it running without activities
		startService( new Intent(
			this,
			CounterService.class ) );

		requestWindowFeature( Window.FEATURE_NO_TITLE );
		setContentView( R.layout.activity_main );

		statsView = (StatsView)findViewById( R.id.stats );
		listView = (ListView)findViewById( R.id.rides );
		startButton = (ImageButton)findViewById( R.id.start );
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

		statsView.listView = listView;
		registerForContextMenu( listView );
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
				CounterService.class ),
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
		inflater.inflate( R.menu.counter_options, menu );

		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item )
	{
		switch( item.getItemId() )
		{
			case R.id.preferences:
				startActivity( new Intent(
					this,
					CounterPreferenceActivity.class ) );
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
	public void onCounterUpdate()
	{
		setState();
		refresh();
	}

	public void onStart( View v )
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
		counterView.setVisibility( service.started ?
			View.VISIBLE :
			View.GONE );

		startButton.setImageResource( service.started ?
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
			CounterAdapter.getRideDate(
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
			adapter = new CounterAdapter( this, c );
			listView.setAdapter( adapter );
		}
		else
			adapter.changeCursor( c );

		statsView.setCursor( c );
	}
}
