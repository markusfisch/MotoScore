package de.markusfisch.android.motoscore;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MotoScoreAdapter
	extends CursorAdapter
{
	private LayoutInflater inflater = null;

	public MotoScoreAdapter( Context context, Cursor cursor )
	{
		super( context, cursor, false );
		init( context );
	}

	@Override
	public View newView(
		Context context,
		Cursor cursor,
		ViewGroup parent )
	{
		LayoutInflater inflater = LayoutInflater.from(
			parent.getContext() );

		return inflater.inflate(
			R.layout.row_ride,
			parent,
			false );
	}

	@Override
	public void bindView( View view, Context context, Cursor cursor )
	{
		final long id = cursor.getLong( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_ID ) );
		final String date = cursor.getString( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_DATE_AND_TIME ) );
		final int distance =
			(int)Math.round( cursor.getFloat( cursor.getColumnIndex(
				MotoScoreDataSource.RIDES_DISTANCE ) )/1000f );
		final int mistakes = cursor.getInt( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_MISTAKES ) );
		final float score = cursor.getFloat( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_SCORE ) );

		setText(
			(TextView)view.findViewById( R.id.date ),
			date );

		setText(
			(TextView)view.findViewById( R.id.details ),
			String.format(
				"%d %s / %d %s",
				mistakes,
				context.getString( R.string.mistakes ),
				distance,
				context.getString( R.string.km ) ) );

		setText(
			(TextView)view.findViewById( R.id.score ),
			String.format( "%.2f", score ) );
	}

	private void setText(
		TextView tv,
		String text )
	{
		if( tv == null )
			return;

		tv.setText( text );
	}

	private void init( Context context )
	{
		inflater = LayoutInflater.from( context );
	}
}
