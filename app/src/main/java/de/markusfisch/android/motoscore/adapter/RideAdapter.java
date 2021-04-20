package de.markusfisch.android.motoscore.adapter;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import java.util.Locale;

import de.markusfisch.android.motoscore.R;
import de.markusfisch.android.motoscore.data.Database;

public class RideAdapter extends CursorAdapter {
	private final int dateIndex;
	private final int distanceIndex;
	private final int averageIndex;
	private final int mistakesIndex;
	private final int durationIndex;
	private final int scoreIndex;
	private final String km;
	private final String kmH;

	private int scoreType;

	public RideAdapter(Context context, Cursor cursor, int scoreType) {
		super(context, cursor, false);

		dateIndex = cursor.getColumnIndex(Database.RIDES_DATE_AND_TIME);
		distanceIndex = cursor.getColumnIndex(Database.RIDES_DISTANCE);
		averageIndex = cursor.getColumnIndex(Database.RIDES_AVERAGE);
		mistakesIndex = cursor.getColumnIndex(Database.RIDES_MISTAKES);
		durationIndex = cursor.getColumnIndex(Database.RIDES_DURATION);
		scoreIndex = cursor.getColumnIndex(Database.RIDES_SCORE);
		this.scoreType = scoreType;

		km = context.getString(R.string.km);
		kmH = context.getString(R.string.km_h);
	}

	public void setScoreType(int scoreType) {
		this.scoreType = scoreType;
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		return inflater.inflate(R.layout.row_ride, parent, false);
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor) {
		ViewHolder holder = getViewHolder(view);
		setData(holder, cursor);
	}

	private ViewHolder getViewHolder(View view) {
		ViewHolder holder = (ViewHolder) view.getTag();
		if (holder == null) {
			holder = new ViewHolder();
			holder.date = view.findViewById(R.id.date);
			holder.mistakes = view.findViewById(R.id.mistakes);
			holder.distance = view.findViewById(R.id.distance);
			holder.duration = view.findViewById(R.id.duration);
			holder.average = view.findViewById(R.id.average);
			holder.score = view.findViewById(R.id.score);
			view.setTag(holder);
		}
		return holder;
	}

	void setData(ViewHolder holder, Cursor cursor) {
		String date = cursor.getString(dateIndex);
		int distance = Math.round(
				cursor.getFloat(distanceIndex) / 1000f);
		float average = cursor.getFloat(averageIndex) * 3.6f;
		int mistakes = cursor.getInt(mistakesIndex);
		double duration = cursor.getDouble(durationIndex) * 24d;
		long minutes = Math.round((duration % 1d) * 60d) % 60;
		float score = cursor.getFloat(scoreIndex);

		holder.date.setText(date);
		holder.mistakes.setText(getMistakesText(mistakes));
		holder.distance.setText(getDistanceText(distance));
		holder.duration.setText(getDurationText(duration, minutes));
		holder.average.setText(getAverageText(average));
		holder.score.setText(getScoreText(score, minutes));
	}

	private static String getMistakesText(int mistakes) {
		return String.format(Locale.getDefault(), "%d", mistakes);
	}

	private String getDistanceText(int distance) {
		return String.format(Locale.getDefault(), "%d %s", distance, km);
	}

	private static String getDurationText(double duration, long minutes) {
		return String.format(Locale.getDefault(),
				"%02d:%02d", (int) Math.floor(duration), minutes);
	}

	private String getAverageText(float average) {
		return String.format(Locale.getDefault(), "%d %s",
				Math.round(average), kmH);
	}

	private String getScoreText(double score, long minutes) {
		switch (scoreType) {
			default:
			case Database.SCORE_TYPE_MISTAKES_PER_KM:
			case Database.SCORE_TYPE_MISTAKES_PER_HOUR:
				return String.format(Locale.getDefault(), "%.2f", score);
			case Database.SCORE_TYPE_MISTAKES_TOTAL:
				return getMistakesText((int) Math.round(score));
			case Database.SCORE_TYPE_DISTANCE_IN_KM:
				return getDistanceText((int) Math.round(score));
			case Database.SCORE_TYPE_DURATION_IN_HOURS:
				return getDurationText(score, minutes);
			case Database.SCORE_TYPE_AVERAGE_SPEED:
				return getAverageText(Math.round(score));
		}
	}
	}

	private static final class ViewHolder {
		private TextView date;
		private TextView mistakes;
		private TextView distance;
		private TextView duration;
		private TextView average;
		private TextView score;
	}
}
