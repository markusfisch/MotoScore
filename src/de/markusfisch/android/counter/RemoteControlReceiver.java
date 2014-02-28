package de.markusfisch.android.counter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class RemoteControlReceiver
	extends BroadcastReceiver
{
	@Override
	public void onReceive( Context context, Intent intent )
	{
		if( Intent.ACTION_MEDIA_BUTTON.equals( intent.getAction() ) )
		{
			final KeyEvent event = (KeyEvent)
				intent.getParcelableExtra( Intent.EXTRA_KEY_EVENT );

			sendAction(
				context,
				event.getAction(),
				event.getEventTime() );
		}
	}

	private void sendState(
		final Context context,
		final int state )
	{
		final Intent count = new Intent(
			context,
			CounterService.class );

		count.putExtra(
			CounterService.STATE,
			state );

		context.startService( count );
	}

	private void sendAction(
		final Context context,
		final int action,
		final long time )
	{
		final Intent count = new Intent(
			context,
			CounterService.class );

		count.putExtra(
			CounterService.ACTION,
			action );

		count.putExtra(
			CounterService.TIME,
			time );

		context.startService( count );
	}
}
