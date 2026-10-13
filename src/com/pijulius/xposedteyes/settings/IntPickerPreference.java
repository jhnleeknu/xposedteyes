package com.pijulius.xposedteyes.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.pijulius.xposedteyes.R;

/**
 * Integer setting with a bounded picker and one-tap presets. Values are shown in
 * display units (e.g. seconds) and stored multiplied by {@code scale} (e.g. ms),
 * always clamped to the declared range so nothing out of range is ever saved.
 */
public class IntPickerPreference extends DialogPreference {
	private final int min;
	private final int max;
	private final int scale;
	private final int[] presets;
	private final String unit;
	private int defaultStored;
	private NumberPicker picker;

	public IntPickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.IntPickerPreference);
		min = a.getInt(R.styleable.IntPickerPreference_minValue, 0);
		max = a.getInt(R.styleable.IntPickerPreference_maxValue, 100);
		scale = Math.max(1, a.getInt(R.styleable.IntPickerPreference_scale, 1));
		presets = parsePresets(a.getString(R.styleable.IntPickerPreference_presets));
		String u = a.getString(R.styleable.IntPickerPreference_unitLabel);
		unit = u == null ? "" : u;
		a.recycle();
		defaultStored = min * scale;
		setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);
	}

	private static int[] parsePresets(String s) {
		if (s == null || s.trim().isEmpty())
			return new int[0];
		String[] parts = s.split(",");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++)
			out[i] = Integer.parseInt(parts[i].trim());
		return out;
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInt(index, min * scale);
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
		// The default is only displayed; it is written when the user confirms a value.
		defaultStored = clampStored(defaultValue instanceof Integer ? (Integer)defaultValue : min * scale);
		updateSummary();
	}

	public int getStoredValue() {
		try {
			return clampStored(getPersistedInt(defaultStored));
		} catch (ClassCastException e) {
			return defaultStored;
		}
	}

	private int clampStored(int stored) {
		return Math.max(min * scale, Math.min(max * scale, stored));
	}

	public void updateSummary() {
		setSummary(getContext().getString(R.string.int_value_range,
			getStoredValue() / scale, unit, min, max));
	}

	@Override
	protected View onCreateDialogView() {
		Context c = getContext();
		LinearLayout root = new LinearLayout(c);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = (int)(16 * c.getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad, pad, pad);

		TextView range = new TextView(c);
		range.setText(c.getString(R.string.int_range_hint, min, max, unit));
		root.addView(range);

		picker = new NumberPicker(c);
		picker.setMinValue(min);
		picker.setMaxValue(max);
		picker.setWrapSelectorWheel(false);
		picker.setValue(Math.max(min, Math.min(max, getStoredValue() / scale)));
		root.addView(picker, new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		if (presets.length > 0) {
			LinearLayout row = new LinearLayout(c);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (final int p : presets) {
				if (p < min || p > max)
					continue;
				Button b = new Button(c);
				b.setText(p + unit);
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						picker.setValue(p);
					}
				});
				row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			}
			root.addView(row);
		}
		return root;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (!positiveResult || picker == null)
			return;
		picker.clearFocus();
		int stored = clampStored(picker.getValue() * scale);
		if (callChangeListener(stored)) {
			persistInt(stored);
			updateSummary();
		}
	}
}
