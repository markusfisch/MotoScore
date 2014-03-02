package de.markusfisch.android.counter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class HeadsetReceiver
	extends BroadcastReceiver
{
	@Override
	public void onReceive( Context context, Intent intent )
	{
		final String action = intent.getAction();

		if( action.equals( Intent.ACTION_HEADSET_PLUG ) )
			sendState(
				context,
				intent.getIntExtra( "state", 0 ) );
	}

	private void sendState(
		final Context context,
		final int state )
	{
		final Intent count = new Intent(
			context,
			CounterService.class );

		count.putExtra(
			CounterService.COMMAND,
			CounterService.COMMAND_STATE );

		count.putExtra(
			CounterService.STATE,
			state );

		context.startService( count );
	}
}
