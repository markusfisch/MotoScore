package de.markusfisch.android.counter;

import android.app.ListActivity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.widget.SimpleCursorAdapter;

public class HistoryActivity
	extends ListActivity
{
	private Handler handler = new Handler();

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		setContentView( R.layout.activity_history );

		new Thread( new Runnable()
		{
			public void run()
			{
				final Cursor cursor;

				if( MainActivity.service == null )
					return;

				synchronized( MainActivity.service )
				{
					cursor = MainActivity.service.dataSource.queryAll();
				}

				handler.post( new Runnable()
				{
					public void run()
					{
						setListAdapter( new SimpleCursorAdapter(
							HistoryActivity.this,
							R.layout.row_history,
							cursor,
							new String[]{
								CounterDataSource.COLUMN_START,
								CounterDataSource.COLUMN_ERRORS,
								CounterDataSource.COLUMN_DISTANCE },
							new int[]{
								R.id.row_start,
								R.id.row_errors,
								R.id.row_distance } ) );
					}
				} );
			}
		} ).start();
	}
}
