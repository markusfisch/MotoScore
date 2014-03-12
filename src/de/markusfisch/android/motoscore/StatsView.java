package de.markusfisch.android.motoscore;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;

public class StatsView
	extends View
{
	public ListView listView = null;

	private final Paint linePaint = new Paint( Paint.ANTI_ALIAS_FLAG );
	private final Paint fillPaint = new Paint( Paint.ANTI_ALIAS_FLAG );
	private final Path path = new Path();
	private float lineWidth = 4;
	private float dotRadius = 10;
	private final ArrayList<Integer> sample = new ArrayList<Integer>();
	private int samples = 0;
	private int max = 0;
	private int itemHeight = 0;
	private float xf = -1;

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
		max = 10;
		xf = -1;

		do
		{
			int n = cursor.getInt( cursor.getColumnIndex(
				MotoScoreDataSource.COLUMN_MISTAKES_PER_KM ) );

			if( n > max )
				max = n+n/2;

			sample.add( n );
			++samples;

		} while( cursor.moveToNext() );
	}

	@Override
	protected void onDraw( Canvas canvas )
	{
		canvas.drawColor( 0x00000000 );

		if( listView == null ||
			samples < 1 )
			return;

		final View firstChild = listView.getChildAt( 0 );

		if( firstChild == null )
			return;

		if( xf < 0 )
			xf = (float)canvas.getWidth()/max;

		if( itemHeight == 0 )
			itemHeight =
				firstChild.getMeasuredHeight()+
				listView.getDividerHeight();

		final int first = listView.getFirstVisiblePosition();
		final int total = itemHeight*samples;
		float y = total-((first*itemHeight)-firstChild.getTop());
		boolean move = true;

		path.reset();

		y -= itemHeight/2f;

		for( int n = samples; n-- > 0; )
		{
			final float x = xf*sample.get( n ).intValue();

			canvas.drawCircle( x, y, dotRadius, fillPaint );

			if( move )
			{
				move = false;
				path.moveTo( x, y );
			}
			else
				path.lineTo( x, y );

			y -= itemHeight;
		}

		canvas.drawPath( path, linePaint );
	}

	private void init()
	{
		final int color = 0xff68a4e7;
		final float dp = getContext()
			.getResources()
			.getDisplayMetrics()
			.density;

		dotRadius *= dp;

		linePaint.setStyle( Paint.Style.STROKE );
		linePaint.setStrokeWidth( lineWidth*dp );
		linePaint.setColor( color );

		fillPaint.setStyle( Paint.Style.FILL );
		fillPaint.setColor( color );
	}
}
