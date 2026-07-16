package org.thoughtcrime.securesms.components.recyclerview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Smooth Telegram-like item animator with slide + fade for add/remove/move animations.
 * Messages slide in from bottom with spring effect, slide out to right with fade.
 *
 * <p>Every animation started here is tracked per-holder so it can be cancelled/finished exactly
 * once, and {@link #dispatchMoveFinished} is always called - without it, RecyclerView's internal
 * animation bookkeeping never clears for move operations, and a stale animator left over from a
 * previous binding can keep nudging a since-recycled (rebound to different content) itemView,
 * which shows up as messages jumping to the wrong spot / overlapping, most noticeably when the
 * list is repeatedly resized in quick succession (e.g. the keyboard opening/closing).
 */
public class SmoothSlideItemAnimator extends DefaultItemAnimator {

  private static final int ADD_DURATION = 180;
  private static final int REMOVE_DURATION = 140;
  private static final int MOVE_DURATION = 180;

  private final Map<RecyclerView.ViewHolder, Animator> runningAnimations = new HashMap<>();

  public SmoothSlideItemAnimator() {
    setSupportsChangeAnimations(false);
    setAddDuration(ADD_DURATION);
    setRemoveDuration(REMOVE_DURATION);
    setMoveDuration(MOVE_DURATION);
  }

  @Override
  public boolean animateAdd(RecyclerView.ViewHolder holder) {
    cancelRunning(holder);
    View view = holder.itemView;

    view.setTranslationY(20f);
    view.setAlpha(0f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 20f, 0f),
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f));
    set.setDuration(ADD_DURATION);
    set.setInterpolator(new DecelerateInterpolator(2f));
    set.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            runningAnimations.remove(holder);
            view.setTranslationY(0f);
            view.setAlpha(1f);
            dispatchAddFinished(holder);
          }
        });
    runningAnimations.put(holder, set);
    set.start();
    return true;
  }

  @Override
  public boolean animateRemove(RecyclerView.ViewHolder holder) {
    cancelRunning(holder);
    View view = holder.itemView;

    view.setTranslationY(0f);
    view.setAlpha(1f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, 20f),
        ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f));
    set.setDuration(REMOVE_DURATION);
    set.setInterpolator(new FastOutSlowInInterpolator());
    set.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            runningAnimations.remove(holder);
            view.setAlpha(1f);
            view.setTranslationY(0f);
            dispatchRemoveFinished(holder);
          }
        });
    runningAnimations.put(holder, set);
    set.start();
    return true;
  }

  @Override
  public boolean animateMove(
      RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
    View view = holder.itemView;
    int dx = toX - fromX;
    int dy = toY - fromY;

    if (dx == 0 && dy == 0) {
      dispatchMoveFinished(holder);
      return false;
    }

    cancelRunning(holder);

    AnimatorSet set = new AnimatorSet();
    if (dx != 0 && dy != 0) {
      set.playTogether(
          ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -dx, 0f),
          ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -dy, 0f));
    } else if (dx != 0) {
      set.play(ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -dx, 0f));
    } else {
      set.play(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -dy, 0f));
    }
    set.setDuration(MOVE_DURATION);
    set.setInterpolator(new FastOutSlowInInterpolator());
    set.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            runningAnimations.remove(holder);
            view.setTranslationX(0f);
            view.setTranslationY(0f);
            dispatchMoveFinished(holder);
          }
        });
    runningAnimations.put(holder, set);
    set.start();
    return true;
  }

  private void cancelRunning(RecyclerView.ViewHolder holder) {
    Animator previous = runningAnimations.remove(holder);
    if (previous != null) {
      previous.cancel();
    }
  }

  @Override
  public void endAnimation(@NonNull RecyclerView.ViewHolder item) {
    Animator anim = runningAnimations.remove(item);
    if (anim != null) {
      anim.cancel();
    }
    View view = item.itemView;
    view.setTranslationX(0f);
    view.setTranslationY(0f);
    view.setAlpha(1f);
    super.endAnimation(item);
  }

  @Override
  public void endAnimations() {
    for (Animator anim : new ArrayList<>(runningAnimations.values())) {
      anim.cancel();
    }
    runningAnimations.clear();
    super.endAnimations();
  }

  @Override
  public boolean isRunning() {
    return !runningAnimations.isEmpty() || super.isRunning();
  }
}
