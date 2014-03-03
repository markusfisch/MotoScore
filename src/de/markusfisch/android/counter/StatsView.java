package de.markusfisch.android.counter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

public class StatsView
	extends View
{
	private final Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
	private final Path path = new Path();
	private final ArrayList<Integer> sample = new ArrayList<Integer>();
	private int samples = 0;
	private int max = 0;

	public StatsView( Context context )
	{
		super( context );
		init();
	}

	public StatsView( Context context, AttributeSet attrs )
	{
		super( context, attrs );
		init();
	}

	public void setCursor( Cursor cursor )
	{
		if( cursor == null ||
			!cursor.moveToFirst() )
			return;

		sample.clear();
		samples = 0;
		max = 0;

		do
		{
			int n = cursor.getInt( cursor.getColumnIndex(
				CounterDataSource.COLUMN_ERRORS ) );

			if( n > max )
				max = n;

			sample.add( n );
			++samples;
		} while( cursor.moveToNext() );
	}

	@Override
	protected void onDraw( Canvas canvas )
	{
		final int width = canvas.getWidth();
		final int height = canvas.getHeight();
		final float yf = (float)height/samples;
		final float xf = (float)width/max;
		float y = height;

		canvas.drawColor( 0xfff3f3f3 );
		path.reset();
		path.moveTo( 0, y );

		for( int n = 0; n < samples; ++n )
		{
			path.lineTo(
				xf*sample.get( n ).intValue(),
				y );

			y -= yf;
		}

		canvas.drawPath( path, paint );
	}

	private void init()
	{
		paint.setStyle( Paint.Style.STROKE );
		paint.setStrokeWidth( 4 );
		paint.setColor( 0xffafdde9 );
	}
}
