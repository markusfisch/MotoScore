package de.markusfisch.android.motoscore.widget;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.data.Database;

public class GraphView extends View {
	public ListView listView = null;

	private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final ArrayList<Float> sample = new ArrayList<>();

	private float dotRadius = 10;
	private float[] vertices;
	private int samples = 0;
	private float max = 0;
	private int itemHeight = 0;

	public GraphView(Context context) {
		super(context);
		init();
	}

	public GraphView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public void setCursor(Cursor cursor) {
		invalidate();

		if (cursor == null) {
			return;
		} else if (!cursor.moveToFirst()) {
			// cursor is closed by adapter
			return;
		}

		sample.clear();
		samples = 0;
		max = .5f;

		int idx = cursor.getColumnIndex(Database.RIDES_SCORE);
		do {
			float n = cursor.getFloat(idx);

			if (n > max) {
				max = n + n * .5f;
			}

			sample.add(n);
			++samples;
		} while (cursor.moveToNext());

		if (samples > 0) {
			vertices = new float[samples << 2];
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawColor(0x00000000);

		if (listView == null || samples < 1) {
			return;
		}

		View firstChild = listView.getChildAt(0);
		if (firstChild == null) {
			return;
		}

		float w = getWidth();
		float h = getHeight();
		float xf = w / max;

		if (itemHeight == 0) {
			itemHeight = firstChild.getMeasuredHeight() +
					listView.getDividerHeight();
		}

		int first = listView.getFirstVisiblePosition();
		int total = itemHeight * samples;
		float x = -1;
		float y = total - ((first * itemHeight) - firstChild.getTop());
		float lastY = 0;
		int v = 0;

		y -= itemHeight / 2f;
		int layer = canvas.saveLayerAlpha(0, 0, w, h, 0x22,
				Canvas.ALL_SAVE_FLAG);

		for (int n = samples; n-- > 0; ) {
			if (x > -1 && v % 4 == 0) {
				vertices[v++] = x;
				vertices[v++] = lastY;
			}

			x = xf * sample.get(n);

			canvas.drawCircle(x, y, dotRadius, fillPaint);

			vertices[v++] = x;
			vertices[v++] = y;
			lastY = y;

			y -= itemHeight;
		}

		if (v > 3) {
			canvas.drawLines(vertices, linePaint);
		}

		canvas.restoreToCount(layer);
	}

	private void init() {
		Context context = getContext();
		float dp = context.getResources().getDisplayMetrics().density;
		int color = ContextCompat.getColor(context, R.color.stats);

		dotRadius *= dp;

		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeWidth(4 * dp);
		linePaint.setColor(color);

		fillPaint.setStyle(Paint.Style.FILL);
		fillPaint.setColor(color);
	}
}
