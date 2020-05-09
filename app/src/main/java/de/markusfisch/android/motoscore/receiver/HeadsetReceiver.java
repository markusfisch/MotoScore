package de.markusfisch.android.motoscore.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.markusfisch.android.motoscore.service.MotoScoreService;

public class HeadsetReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
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
