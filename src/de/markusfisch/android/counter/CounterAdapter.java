package de.markusfisch.android.counter;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CounterAdapter
	extends CursorAdapter
{
	private LayoutInflater inflater = null;
	private int layout = 0;

	public CounterAdapter( Context context, Cursor cursor )
	{
		super( context, cursor, false );
		init( context );
	}

	public CounterAdapter(
		Context context,
		Cursor cursor,
		int layout )
	{
		super( context, cursor, false );
		init( context );

		this.layout = layout;
	}

	@Override
	public View newView(
		Context context,
		Cursor cursor,
		ViewGroup parent )
	{
		LayoutInflater i = LayoutInflater.from(
			parent.getContext() );

		return i.inflate(
			layout > 0 ? layout : R.layout.row_ride,
			parent,
			false );
	}

	@Override
	public void bindView( View view, Context context, Cursor cursor )
	{
		final long id = cursor.getLong( cursor.getColumnIndex(
			CounterDataSource.COLUMN_ID ) );
		final String date = cursor.getString( cursor.getColumnIndex(
			CounterDataSource.COLUMN_START ) );
		final int distance = Math.max(
			1,
			(int)Math.ceil( cursor.getFloat( cursor.getColumnIndex(
				CounterDataSource.COLUMN_DISTANCE ) )/1000f ) );
		final int errors = cursor.getInt( cursor.getColumnIndex(
			CounterDataSource.COLUMN_ERRORS ) );

		setTextView(
			(TextView)view.findViewById( R.id.date ),
			date );

		setTextView(
			(TextView)view.findViewById( R.id.distance ),
			String.format( "%d km", distance ) );

		setTextView(
			(TextView)view.findViewById( R.id.errors ),
			String.format( "%d", errors ) );

		/*setGauge(
			view.findViewById( R.id.background ),
			(float)errors/distance );*/
	}

	private void setTextView( TextView tv, String text )
	{
		if( tv != null )
			tv.setText( text );
	}

	private void init( Context context )
	{
		inflater = LayoutInflater.from( context );
	}
}
