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
	public MotoScoreAdapter( Context context, Cursor cursor )
	{
		super( context, cursor, false );
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
		final float average = cursor.getFloat( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_AVERAGE ) )*3.6f;
		final int mistakes = cursor.getInt( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_MISTAKES ) );
		final double duration = cursor.getDouble( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_DURATION ) )*24d;
		final long minutes = Math.round( (duration % 1d)*60d ) % 60;
		final float score = cursor.getFloat( cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_SCORE ) );

		setText(
			(TextView)view.findViewById( R.id.date ),
			date );

		setText(
			(TextView)view.findViewById( R.id.mistakes ),
			String.format(
				"%d",
				mistakes ) );

		setText(
			(TextView)view.findViewById( R.id.distance ),
			String.format(
				"%d %s",
				distance,
				context.getString( R.string.km ) ) );

		setText(
			(TextView)view.findViewById( R.id.duration ),
			String.format(
				"%02d:%02d",
				Math.round( duration ),
				minutes ) );

		setText(
			(TextView)view.findViewById( R.id.average ),
			String.format(
				"%d %s",
				Math.round( average ),
				context.getString( R.string.km_h ) ) );

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
}
