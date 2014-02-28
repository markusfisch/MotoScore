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
		if( Intent.ACTION_HEADSET_PLUG.equals( intent.getAction() ) )
		{
			sendState(
				context,
				intent.getIntExtra( "state", 0 ) );
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
}
