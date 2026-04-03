package com.dynamsoft.scaniddocument;

import android.os.Handler;
import android.os.Looper;
import android.text.Spanned;
import android.transition.AutoTransition;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;

final class ScanUiAnimator {
	private static final String TAG = "ScanUiAnimator";
	private static final long DEFAULT_ANIMATION_DURATION_MS = 300L;
	private static final Handler MAIN = new Handler(Looper.getMainLooper());

	private ScanUiAnimator() {
	}

	private static void runOnMainThread(@Nullable Runnable action) {
		if (action == null) {
			return;
		}
		if (Looper.myLooper() == Looper.getMainLooper()) {
			action.run();
		} else {
			MAIN.post(action);
		}
	}

	static void clearPendingActions() {
		MAIN.removeCallbacksAndMessages(null);
	}

	static void runOnMainThreadDelayed(long delayMs, @Nullable Runnable action) {
		if (action == null) {
			return;
		}
		MAIN.postDelayed(action, Math.max(0L, delayMs));
	}

	static void showTip(ViewGroup parent, int tipIndex, long delayAfterAnimationEnd, @Nullable AnimationListener listener) {
		runOnMainThread(() -> {
			TransitionSet set = new TransitionSet()
					.addTransition(new ChangeBounds())
					.addListener(new SimpleTransitionListener() {
						@Override
						void onAnimationEnd() {
							if (listener == null) {
								return;
							}
							runOnMainThreadDelayed(delayAfterAnimationEnd, listener::onAnimationEnd);
						}
					})
					.setDuration(DEFAULT_ANIMATION_DURATION_MS);
			TransitionManager.beginDelayedTransition(parent, set);

			TextView tip1 = parent.findViewById(R.id.tv_tip1);
			tip1.setVisibility(View.VISIBLE);
			switch (tipIndex) {
				case 1:
					setTipText(tip1, R.string.tip1);
					break;
				case 20:
					setTipText(tip1, R.string.tip2);
					break;
				case 21:
					setTipText(tip1, R.string.tip2_1);
					break;
				case 22:
					setTipText(tip1, R.string.tip2_2);
					break;
				case 3:
					setTipText(tip1, R.string.tip3);
					break;
				case 4:
					setTipText(tip1, R.string.tip4);
					runOnMainThreadDelayed(1000, () -> showTip(parent, 41, 0, null));
					break;
				case 41:
					setTipText(tip1, R.string.tip4_1);
					runOnMainThreadDelayed(1000, () -> showTip(parent, 42, 0, null));
					break;
				case 42:
					setTipText(tip1, R.string.tip4_2);
					runOnMainThreadDelayed(1000, () -> showTip(parent, 43, 0, null));
					break;
				case 43:
					setTipText(tip1, R.string.tip4_3);
					break;
				case 5:
					setTipText(tip1, R.string.tip5);
					break;
				default:
					tip1.setVisibility(View.GONE);
			}
		});
	}

	private static void setTipText(TextView textView, int resId) {
		String rawText = textView.getContext().getString(resId);
		Spanned spanned = HtmlCompat.fromHtml(rawText, HtmlCompat.FROM_HTML_MODE_LEGACY);
		textView.setText(spanned);
	}

	static void showGuideTextZoneAnimate(ViewGroup parent, boolean returnWhite, @Nullable AnimationListener listener) {
		runOnMainThread(() -> {
			parent.findViewById(R.id.guide_frame_container)
					.setBackground(ContextCompat.getDrawable(parent.getContext(), R.drawable.rounded_green_border));

			runOnMainThreadDelayed(600, () -> {
				if (listener != null) {
					listener.onAnimationEnd();
				}
				View guideText = parent.findViewById(R.id.guide_frame_text);
				if (returnWhite) {
					parent.findViewById(R.id.guide_frame_container)
							.setBackground(ContextCompat.getDrawable(parent.getContext(), R.drawable.rounded_white_border));
				}
				if (guideText.getVisibility() != View.VISIBLE) {
					return;
				}

				TransitionSet visibilitySet = new TransitionSet()
						.addTransition(new AutoTransition())
						.setDuration(DEFAULT_ANIMATION_DURATION_MS);

				TransitionManager.beginDelayedTransition(parent, visibilitySet);
				guideText.setVisibility(View.GONE);
			});
		});
	}

	static Sequencer sequence() {
		return new Sequencer();
	}

	interface AnimationListener {
		void onAnimationEnd();
	}

	interface Step {
		void run(@NonNull Runnable next);
	}

	static final class Sequencer {
		private final java.util.ArrayDeque<Step> steps = new java.util.ArrayDeque<>();
		private Runnable onComplete;

		Sequencer then(@NonNull Step step) {
			steps.add(step);
			return this;
		}

		Sequencer then(@NonNull Runnable step) {
			steps.add(next -> {
				step.run();
				next.run();
			});
			return this;
		}

		Sequencer onComplete(@Nullable Runnable onComplete) {
			this.onComplete = onComplete;
			return this;
		}

		void start() {
			runOnMainThread(this::runNext);
		}

		private void runNext() {
			Step step = steps.poll();
			if (step == null) {
				if (onComplete != null) {
					onComplete.run();
				}
				return;
			}
			try {
				step.run(() -> runOnMainThread(this::runNext));
			} catch (Throwable t) {
				Log.e(TAG, "Sequencer step failed", t);
				if (onComplete != null) {
					onComplete.run();
				}
			}
		}
	}

	private abstract static class SimpleTransitionListener implements Transition.TransitionListener {
		private boolean done;

		abstract void onAnimationEnd();

		@Override
		public void onTransitionCancel(Transition transition) {
			if (done) {
				return;
			}
			done = true;
			onAnimationEnd();
		}

		@Override
		public void onTransitionEnd(Transition transition) {
			if (done) {
				return;
			}
			done = true;
			onAnimationEnd();
		}

		@Override
		public void onTransitionPause(Transition transition) {
		}

		@Override
		public void onTransitionResume(Transition transition) {
		}

		@Override
		public void onTransitionStart(Transition transition) {
		}
	}
}