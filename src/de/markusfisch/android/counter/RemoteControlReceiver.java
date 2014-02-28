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
		if( !Intent.ACTION_MEDIA_BUTTON.equals( intent.getAction() ) )
			return;

		final KeyEvent event = (KeyEvent)
			intent.getParcelableExtra( Intent.EXTRA_KEY_EVENT );

		switch( event.getAction() )
		{
			case KeyEvent.ACTION_UP:
				final Intent count = new Intent(
					context,
					CounterService.class );

				count.putExtra( CounterService.COUNT, true );
				context.startService( count );
				break;
		}
	}
}
