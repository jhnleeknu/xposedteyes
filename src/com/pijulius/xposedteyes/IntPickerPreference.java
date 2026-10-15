package com.pijulius.xposedteyes;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import com.pijulius.xposedteyes.settings.Keys;
import com.pijulius.xposedteyes.settings.StartupSettings;

/**
 * Integer setting picked from its validated range (StartupSettings.RANGES,
 * looked up by key) with optional one-tap presets. Values outside the range
 * cannot be entered.
 */
@SuppressWarnings("deprecation")
public class IntPickerPreference extends DialogPreference {
	private int value;
	private NumberPicker picker;

	public IntPickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setPositiveButtonText(android.R.string.ok);
		setNegativeButtonText(android.R.string.cancel);
	}

	private StartupSettings.Range range() {
		StartupSettings.Range r = StartupSettings.RANGES.get(getKey());
		if (r == null)
			throw new IllegalStateException("No range for " + getKey());
		return r;
	}

	private int[] presets() {
		if (Keys.STARTUP_DELAY_S.equals(getKey()) || Keys.BT_LAUNCH_DELAY_S.equals(getKey()))
			return StartupSettings.DELAY_PRESETS_S;
		return new int[0];
	}

	@Override
	protected View onCreateDialogView() {
		Context c = getContext();
		StartupSettings.Range r = range();

		LinearLayout root = new LinearLayout(c);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setGravity(Gravity.CENTER_HORIZONTAL);
		int pad = (int)(16 * c.getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad, pad, pad);

		picker = new NumberPicker(c);
		picker.setMinValue(r.min);
		picker.setMaxValue(r.max);
		picker.setValue(Math.max(r.min, Math.min(r.max, value)));
		picker.setWrapSelectorWheel(false);

		int[] presets = presets();
		if (presets.length > 0) {
			LinearLayout row = new LinearLayout(c);
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (final int p : presets) {
				Button b = new Button(c);
				b.setText(c.getString(R.string.seconds_short, p));
				b.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						picker.setValue(p);
					}
				});
				row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
			}
			root.addView(row, new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		}

		root.addView(picker);
		return root;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (!positiveResult || picker == null)
			return;
		picker.clearFocus();
		int v = picker.getValue();
		if (range().contains(v) && callChangeListener(v))
			setValue(v);
	}

	public void setValue(int v) {
		value = v;
		persistInt(v);
		notifyChanged();
	}

	public int getValue() {
		return value;
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInt(index, 0);
	}

	@Override
	protected void onSetInitialValue(boolean restore, Object defaultValue) {
		int def = defaultValue instanceof Integer ? (Integer)defaultValue : range().def;
		if (!restore) {
			value = def;
			return;
		}
		try {
			value = getPersistedInt(def);
		} catch (ClassCastException e) {
			value = def;
		}
	}
}
