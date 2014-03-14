package de.markusfisch.android.motoscore;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
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
	private float lineWidth = 4;
	private float dotRadius = 10;
	private final ArrayList<Float> sample = new ArrayList<Float>();
	private float vertices[];
	private int samples = 0;
	private float max = 0;
	private int itemHeight = 0;

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
		max = 2f;

		final int idx = cursor.getColumnIndex(
			MotoScoreDataSource.RIDES_MISTAKES_PER_KM );

		do
		{
			final float n = cursor.getFloat( idx );

			if( n > max )
				max = n+n*.5f;

			sample.add( n );
			++samples;

		} while( cursor.moveToNext() );

		if( samples > 0 )
			vertices = new float[samples << 2];
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

		final float w = getWidth();
		final float h = getHeight();
		final float xf = w/max;

		if( itemHeight == 0 )
			itemHeight =
				firstChild.getMeasuredHeight()+
				listView.getDividerHeight();

		final int first = listView.getFirstVisiblePosition();
		final int total = itemHeight*samples;
		float x = -1;
		float y = total-((first*itemHeight)-firstChild.getTop());
		float lastY = 0;
		int v = 0;

		y -= itemHeight/2f;

		int layer = canvas.saveLayerAlpha(
			0,
			0,
			w,
			h,
			0x22,
			Canvas.MATRIX_SAVE_FLAG |
			Canvas.CLIP_SAVE_FLAG |
			Canvas.HAS_ALPHA_LAYER_SAVE_FLAG |
			Canvas.FULL_COLOR_LAYER_SAVE_FLAG |
			Canvas.CLIP_TO_LAYER_SAVE_FLAG );

		for( int n = samples; n-- > 0; )
		{
			if( x > -1 &&
				v % 4 == 0 )
			{
				vertices[v++] = x;
				vertices[v++] = lastY;
			}

			x = xf*sample.get( n ).floatValue();

			canvas.drawCircle( x, y, dotRadius, fillPaint );

			vertices[v++] = x;
			vertices[v++] = y;
			lastY = y;

			y -= itemHeight;
		}

		if( v > 3 )
			canvas.drawLines( vertices, linePaint );

		canvas.restoreToCount( layer );
	}

	private void init()
	{
		final int color = 0xffffffff;
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
