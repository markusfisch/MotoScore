package de.markusfisch.android.motoscore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;

public class BackgroundView
	extends View
{
	private final Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
	private int width = -1;
	private int height = -1;
	private Stop stops[];

	public BackgroundView( Context context )
	{
		super( context );
		init();
	}

	public BackgroundView( Context context, AttributeSet attrs )
	{
		super( context, attrs );
		init();
	}

	@Override
	protected void onDraw( Canvas canvas )
	{
		if( width < 0 &&
			(width = canvas.getWidth()) < 1 )
			return;

		if( height < 0 &&
			(height = canvas.getHeight()) < 1 )
			return;

		final Calendar cal = Calendar.getInstance();
		final int minuteOfDay =
			cal.get( Calendar.HOUR_OF_DAY )*60+
			cal.get( Calendar.MINUTE );
		final int f = Math.min( minuteOfDay, 1440-minuteOfDay );
		int n = stops.length;
		final int colors[] = new int[n];

		while( n-- > 0 )
		{
			final Stop s = stops[n];

			colors[n] = Color.rgb(
				(int)Math.round(
					s.base[0]+s.range[0]*f ) % 255,
				(int)Math.round(
					s.base[1]+s.range[1]*f ) % 255,
				(int)Math.round(
					s.base[2]+s.range[2]*f ) % 255 );
		}

		paint.setShader( new LinearGradient(
			0,
			0,
			0,
			height,
			colors,
			null,
			Shader.TileMode.CLAMP ) );

		canvas.drawRect(
			0f,
			0f,
			width,
			height,
			paint );
	}

	private void init()
	{
		paint.setStyle( Paint.Style.FILL );

		final int colors[] = { 0x5fabff, 0x2a7ad3 };
		int n = colors.length;

		stops = new Stop[n];

		while( n-- > 0 )
		{
			final Stop s = new Stop();
			final int
				c = colors[n],
				r = Color.red( c ),
				g = Color.green( c ),
				b = Color.blue( c );

			s.base[0] = r*.7f;
			s.base[1] = g*.7f;
			s.base[2] = b*.7f;

			s.range[0] = r*.3f/720f;
			s.range[1] = g*.3f/720f;
			s.range[2] = b*.3f/720f;

			stops[n] = s;
		}
	}

	private class Stop
	{
		public float base[] = new float[3];
		public float range[] = new float[3];
	}
}
