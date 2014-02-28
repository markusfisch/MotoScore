package de.markusfisch.android.counter;

import android.app.ListActivity;
import android.content.Context;
import android.os.Bundle;
import android.widget.SimpleCursorAdapter;

public class HistoryActivity
	extends ListActivity
{
	private CounterDataSource dataSource;

	@Override
	public void onCreate( Bundle state )
	{
		super.onCreate( state );

		setContentView( R.layout.activity_history );

		dataSource = new CounterDataSource( this );
		dataSource.open();

		setListAdapter( new SimpleCursorAdapter(
			this,
			R.layout.row_history,
			dataSource.queryAll(),
			new String[]{
				CounterDataSource.COLUMN_START,
				CounterDataSource.COLUMN_ERRORS,
				CounterDataSource.COLUMN_DISTANCE },
			new int[]{
				R.id.row_start,
				R.id.row_errors,
				R.id.row_distance } ) );
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		dataSource.close();
	}
}
