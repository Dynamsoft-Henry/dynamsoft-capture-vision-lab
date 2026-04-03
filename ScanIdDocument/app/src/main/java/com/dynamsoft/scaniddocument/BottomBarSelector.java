package com.dynamsoft.scaniddocument;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BottomBarSelector extends ConstraintLayout {
	public static final String KEY_ID = "ID";
	public static final String KEY_BOTH = "BOTH";
	public static final String KEY_PASSPORT = "PASSPORT";

	public interface OnSelectedItemChangedListener {
		void onSelectedItemChanged(@NonNull String key);
	}

	private static final int CENTER_INDEX = 1;

	private final List<String> keys = new ArrayList<>(3);
	private final Map<String, String> labels = new HashMap<>(3);
	private final List<OnSelectedItemChangedListener> listeners = new ArrayList<>();

	private LinearLayout tabContainer;
	private TextView[] tabs;
	private boolean isAnimating;

	public BottomBarSelector(@NonNull Context context) {
		super(context);
		init(context);
	}

	public BottomBarSelector(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public BottomBarSelector(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		LayoutInflater.from(context).inflate(R.layout.bottombar, this, true);

		tabContainer = findViewById(R.id.tabContainer);
		TextView tabId = findViewById(R.id.tab_id);
		TextView tabBoth = findViewById(R.id.tab_both);
		TextView tabPassport = findViewById(R.id.tab_passport);
		tabs = new TextView[]{tabId, tabBoth, tabPassport};

		labels.put(KEY_ID, tabId.getText() == null ? "ID" : tabId.getText().toString());
		labels.put(KEY_BOTH, tabBoth.getText() == null ? "Both" : tabBoth.getText().toString());
		labels.put(KEY_PASSPORT, tabPassport.getText() == null ? "Passport" : tabPassport.getText().toString());

		keys.clear();
		keys.add(KEY_ID);
		keys.add(KEY_BOTH);
		keys.add(KEY_PASSPORT);

		for (int index = 0; index < tabs.length; index++) {
			final int tabIndex = index;
			tabs[index].setOnClickListener(v -> {
				if (isAnimating || !isEnabled()) {
					return;
				}
				if (tabIndex == CENTER_INDEX) {
					notifySelectedChanged();
					return;
				}
				if (tabIndex == 0) {
					animateOneStepRight();
				} else if (tabIndex == 2) {
					animateOneStepLeft();
				}
			});
		}

		post(() -> {
			syncTextsFromKeys();
			applySelectedStyle();
		});
	}

	public void selectItem(@NonNull String key) {
		if (isAnimating || tabs == null || tabs.length != 3 || keys.size() != 3) {
			return;
		}
		if (key.equals(keys.get(CENTER_INDEX))) {
			applySelectedStyle();
			notifySelectedChanged();
			return;
		}
		int guard = 0;
		while (!key.equals(keys.get(CENTER_INDEX)) && guard++ < 4) {
			if (key.equals(keys.get(0))) {
				rotateRightKeys();
			} else if (key.equals(keys.get(2))) {
				rotateLeftKeys();
			} else {
				break;
			}
		}
		syncTextsFromKeys();
		applySelectedStyle();
		notifySelectedChanged();
	}

	public String getSelectedKey() {
		if (keys.size() != 3) {
			return "";
		}
		String key = keys.get(CENTER_INDEX);
		return key == null ? "" : key;
	}

	public void addOnSelectedItemChangedListener(OnSelectedItemChangedListener listener) {
		if (listener != null && !listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public void removeOnSelectedItemChangedListener(OnSelectedItemChangedListener listener) {
		listeners.remove(listener);
	}

	private void animateOneStepLeft() {
		isAnimating = true;
		final float step = tabContainer.getWidth() / 3f;
		tabContainer.animate()
				.translationX(-step)
				.setDuration(200)
				.setInterpolator(new AccelerateDecelerateInterpolator())
				.withEndAction(() -> {
					rotateLeftKeys();
					tabContainer.setTranslationX(0f);
					syncTextsFromKeys();
					applySelectedStyle();
					notifySelectedChanged();
					isAnimating = false;
				})
				.start();
	}

	private void animateOneStepRight() {
		isAnimating = true;
		final float step = tabContainer.getWidth() / 3f;
		tabContainer.animate()
				.translationX(step)
				.setDuration(200)
				.setInterpolator(new AccelerateDecelerateInterpolator())
				.withEndAction(() -> {
					rotateRightKeys();
					tabContainer.setTranslationX(0f);
					syncTextsFromKeys();
					applySelectedStyle();
					notifySelectedChanged();
					isAnimating = false;
				})
				.start();
	}

	private void rotateLeftKeys() {
		String first = keys.get(0);
		keys.set(0, keys.get(1));
		keys.set(1, keys.get(2));
		keys.set(2, first);
	}

	private void rotateRightKeys() {
		String last = keys.get(2);
		keys.set(2, keys.get(1));
		keys.set(1, keys.get(0));
		keys.set(0, last);
	}

	private void syncTextsFromKeys() {
		for (int index = 0; index < 3; index++) {
			String key = keys.get(index);
			String label = labels.get(key);
			tabs[index].setText(label == null ? key : label);
		}
	}

	private void applySelectedStyle() {
		for (int index = 0; index < 3; index++) {
			if (index == CENTER_INDEX) {
				tabs[index].setTextColor(0xFFFFFFFF);
				tabs[index].setTypeface(null, Typeface.BOLD);
				tabs[index].setAlpha(1f);
			} else {
				tabs[index].setTextColor(0xFFFFFFFF);
				tabs[index].setTypeface(null, Typeface.NORMAL);
				tabs[index].setAlpha(0.8f);
			}
		}
	}

	private void notifySelectedChanged() {
		String selectedKey = getSelectedKey();
		for (OnSelectedItemChangedListener listener : listeners) {
			listener.onSelectedItemChanged(selectedKey);
		}
	}
}