package de.markusfisch.android.counter;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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

			// re-register media button in case some other app
			// stepped in between
			service.registerMediaButton();

			setState();
			query();
			refresh();
		}

		@Override
		public void onServiceDisconnected( ComponentName className )
		{
			service.listener = null;
			service = null;
		}
	};
	private boolean serviceBound = false;
	private CounterService service = null;
	private CounterAdapter adapter = null;
	private Handler handler = new Handler();
	private ListView listView;
	private ImageButton startButton;
	private View counterView;
	private TextView dateTextView;
	private TextView errorsTextView;
	private TextView distanceTextView;

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

		listView = (ListView)findViewById( R.id.rides );
		startButton = (ImageButton)findViewById( R.id.start );
		counterView = (View)findViewById( R.id.counter );
		dateTextView = (TextView)findViewById( R.id.date );
		errorsTextView = (TextView)findViewById( R.id.errors );
		distanceTextView = (TextView)findViewById( R.id.distance );

		registerForContextMenu( listView );
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// bind the service to be notified of new countings
		// while visible
		serviceBound = bindService(
			new Intent(
				this,
				CounterService.class ),
			connection,
			Context.BIND_AUTO_CREATE );

		if( !serviceBound )
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
	public void onCount()
	{
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
			R.drawable.ic_stop :
			R.drawable.ic_start );
	}

	private void query()
	{
		if( !service.dataSource.ready() )
			handler.postDelayed( new Runnable()
			{
				@Override
				public void run()
				{
					query();
				}
			}, 500 );

		adapter = new CounterAdapter(
			this,
			service.dataSource.queryAll() );

		listView.setAdapter( adapter );
	}

	private void refresh()
	{
		if( adapter != null )
			adapter.changeCursor(
				service.dataSource.queryAll() );

		if( service != null &&
			service.started )
		{
			dateTextView.setText(
				service.rideStart.toString() );
			errorsTextView.setText(
				String.format( "%d", service.errors ) );
			distanceTextView.setText(
				String.format( "%d km",
					(int)Math.ceil( service.distance/1000 ) ) );
		}
	}
}
