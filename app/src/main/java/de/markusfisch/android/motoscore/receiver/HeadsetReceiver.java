package de.markusfisch.android.motoscore.receiver;

import de.markusfisch.android.motoscore.service.MotoScoreService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class HeadsetReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
			sendState(context, intent.getIntExtra("state", 0));
		}
	}

	private void sendState(Context context, int state) {
		Intent count = new Intent(context, MotoScoreService.class);
		count.putExtra(MotoScoreService.COMMAND,
				MotoScoreService.COMMAND_STATE);
		count.putExtra(MotoScoreService.STATE, state);
		context.startService(count);
	}
}
