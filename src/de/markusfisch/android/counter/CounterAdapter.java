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

import java.text.SimpleDateFormat;
import java.util.Date;

public class CounterAdapter
	extends CursorAdapter
{
	public static final SimpleDateFormat startDateFormat =
		new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
	public static final SimpleDateFormat nowDateFormat =
		new SimpleDateFormat( "HH:mm:ss" );

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
			CounterDataSource.COLUMN_DATE_AND_TIME ) );
		final int distance =
			(int)Math.round( cursor.getFloat( cursor.getColumnIndex(
				CounterDataSource.COLUMN_DISTANCE ) )/1000f );
		final int mistakes = cursor.getInt( cursor.getColumnIndex(
			CounterDataSource.COLUMN_MISTAKES ) );
		final int ratio = cursor.getInt( cursor.getColumnIndex(
			CounterDataSource.COLUMN_MISTAKES_PER_KM ) );

		setTextView(
			(TextView)view.findViewById( R.id.date ),
			date );

		setTextView(
			(TextView)view.findViewById( R.id.details ),
			String.format(
				"%d %s / %d %s",
				mistakes,
				context.getString( R.string.mistakes ),
				distance,
				context.getString( R.string.km ) ) );

		setTextView(
			(TextView)view.findViewById( R.id.ratio ),
			String.format( "%d", ratio ) );
	}

	public static String getRideDate( Date start, Date stop )
	{
		return
			CounterAdapter.startDateFormat.format( start )+" - "+
			CounterAdapter.nowDateFormat.format( stop );
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
