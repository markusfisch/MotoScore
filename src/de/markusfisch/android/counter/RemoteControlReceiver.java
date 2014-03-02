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
		final String action = intent.getAction();

		if( action.equals( Intent.ACTION_MEDIA_BUTTON ) )
		{
			final KeyEvent event = (KeyEvent)
				intent.getParcelableExtra( Intent.EXTRA_KEY_EVENT );

			sendAction(
				context,
				event.getAction(),
				event.getEventTime() );
		}
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
			CounterService.COMMAND,
			CounterService.COMMAND_ACTION );

		count.putExtra(
			CounterService.ACTION,
			action );

		count.putExtra(
			CounterService.TIME,
			time );

		context.startService( count );
	}
}
