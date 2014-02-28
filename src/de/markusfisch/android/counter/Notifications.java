package de.markusfisch.android.counter;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;

public class Notifications
{
	public Counting counting;

	private static final int NOTIFY_COUNTING = 1;

	private NotificationManager notificationManager;

	public class Counting extends AbstractNotification
	{
		public Counting( Context context, int id )
		{
			super( id );

			Resources r = context.getResources();

			notification = getNotification(
				context,
				R.drawable.notify,
				r.getString( R.string.app_name ),
				r.getString( R.string.counting ),
				getDefaultIntent( context ) );

			notification.flags |= Notification.FLAG_ONGOING_EVENT;
		}
	}

	public Notifications( Context context )
	{
		notificationManager = (NotificationManager)
			context.getSystemService( Context.NOTIFICATION_SERVICE );

		counting = new Counting(
			context,
			NOTIFY_COUNTING );
	}

	private abstract class AbstractNotification
	{
		protected Notification notification;
		private int id;

		public AbstractNotification( int id )
		{
			this.id = id;
		}

		public void show()
		{
			notificationManager.notify( id, notification );
		}

		public void hide()
		{
			notificationManager.cancel( id );
		}
	}

	private static Notification getNotification(
		Context context,
		int icon,
		String title,
		String text,
		Intent intent )
	{
		Notification notification =
			new NotificationCompat.Builder( context )
				.setSmallIcon( icon )
				.setContentTitle( title )
				.setContentText( text )
				.build();

		notification.contentIntent = PendingIntent.getActivity(
			context,
			0,
			intent,
			0 );

		return notification;
	}

	private static Intent getDefaultIntent( Context context )
	{
		Intent intent = new Intent( context, MainActivity.class );

		intent.addFlags(
			Intent.FLAG_ACTIVITY_NEW_TASK |
			Intent.FLAG_ACTIVITY_SINGLE_TOP );

		return intent;
	}
}
