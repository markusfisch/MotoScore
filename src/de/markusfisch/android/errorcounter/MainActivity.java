package de.markusfisch.android.errorcounter;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity
	extends Activity
	implements ErrorCounterService.ErrorCounterServiceListener
{
	private final ServiceConnection connection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(
			ComponentName className,
			IBinder binder )
		{
			service = ((ErrorCounterService.Binder)binder).getService();
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
	private ErrorCounterService service = null;
	private TextView errorsTextView;
	private TextView distanceTextView;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		// start & bind service
		{
			final Intent i = new Intent(
				this,
				ErrorCounterService.class );

			// start the service to keep it running without activities
			startService( i );

			// bind the service to get notified when it's up
			serviceBound = bindService(
				i,
				connection,
				Context.BIND_AUTO_CREATE );
		}

		if( !serviceBound )
		{
			Toast.makeText(
				this,
				R.string.error_service,
				Toast.LENGTH_LONG ).show();

			finish();
			return;
		}

		setContentView( R.layout.activity_main );

		errorsTextView = (TextView)findViewById( R.id.errors );
		distanceTextView = (TextView)findViewById( R.id.distance );
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if( serviceBound )
			unbindService( connection );
	}

	@Override
	public void onResume()
	{
		super.onResume();
		refresh();
	}

	@Override
	public void onPause()
	{
		super.onPause();
	}

	@Override
	public void update()
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
		service.reset();
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
		float distance = 0;

		if( service != null )
		{
			errors = service.errors;
			distance = service.distance/1000;
		}

		errorsTextView.setText(
			String.format( "%d", errors ) );
		distanceTextView.setText(
			String.format( "%.2f km", distance ) );
	}
}
