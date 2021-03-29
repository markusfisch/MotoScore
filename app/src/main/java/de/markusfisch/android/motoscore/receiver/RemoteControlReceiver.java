package de.markusfisch.android.motoscore.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import de.markusfisch.android.motoscore.service.MotoScoreService;

public class RemoteControlReceiver extends BroadcastReceiver {
	public static KeyEvent getKeyEvent(Intent intent) {
		return Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
				? (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
				: null;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		KeyEvent event = getKeyEvent(intent);
		if (event != null) {
			sendAction(context, event.getAction(), event.getEventTime());
		}
	}

	private static void sendAction(Context context, int action, long time) {
		Intent count = new Intent(context, MotoScoreService.class);
		count.putExtra(MotoScoreService.COMMAND,
				MotoScoreService.COMMAND_ACTION);
		count.putExtra(MotoScoreService.ACTION, action);
		count.putExtra(MotoScoreService.TIME, time);
		context.startService(count);
	}
}
