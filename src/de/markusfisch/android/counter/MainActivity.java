package de.markusfisch.android.counter;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
	public static CounterService service = null;
	private TextView errorsTextView;
	private TextView distanceTextView;
	private TextView averageTextView;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		// start the service to keep it running without activities
		startService( new Intent(
			this,
			CounterService.class ) );

		setContentView( R.layout.activity_main );

		errorsTextView = (TextView)findViewById( R.id.errors );
		distanceTextView = (TextView)findViewById( R.id.distance );
		averageTextView = (TextView)findViewById( R.id.average );
	}

	@Override
	public void onResume()
	{
		super.onResume();

		// bind the service to be notified of new countings while visible
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

		// re-register media button in case some other app had registered
		if( service != null )
			service.registerMediaButton();

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
			case R.id.history:
				showHistory();
				return true;
			case R.id.preferences:
				showPreferences();
				return true;
		}

		return super.onOptionsItemSelected( item );
	}

	@Override
	public void onCount()
	{
		MainActivity.this.runOnUiThread( new Runnable()
		{
			public void run()
			{
				refresh();
			}
		} );
	}

	public void onReset( View v )
	{
		if( service.started )
		{
			service.stop();
			service.start();
		}
		else
			service.start();

		refresh();

		Toast.makeText(
			this,
			R.string.reset_successful,
			Toast.LENGTH_LONG ).show();
	}

	private void refresh()
	{
		if( errorsTextView == null ||
			distanceTextView == null )
			return;

		int errors = 0;
		int distance = 0;

		if( service != null )
		{
			errors = service.errors;
			distance = (int)Math.ceil( service.distance/1000 );
		}

		errorsTextView.setText(
			String.format( "%d", errors ) );
		distanceTextView.setText(
			String.format( "%d km", distance ) );
		averageTextView.setText(
			String.format( "%.1f",
				(float)errors/Math.max( 1, distance ) ) );
	}

	private void showHistory()
	{
		startActivity( new Intent(
			this,
			HistoryActivity.class ) );
	}

	private void showPreferences()
	{
		startActivity( new Intent(
			this,
			CounterPreferenceActivity.class ) );
	}
}
