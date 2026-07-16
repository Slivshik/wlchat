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
 * Smooth item animator for chat list with Telegram-like animations.
 * Items slide in from left with fade, slide out to right with fade,
 * and move smoothly to their new positions.
 *
 * <p>Every animation started here is tracked per-holder so a new animation on the same holder
 * cancels and properly finishes the previous one instead of fighting it, and
 * {@link #dispatchMoveFinished} is always called so RecyclerView's internal bookkeeping doesn't
 * get stuck (which otherwise shows up as items nudged out of place after several quick layout
 * passes, e.g. rows re-sorting quickly).
 */
public class SmoothChatListAnimator extends DefaultItemAnimator {

  private static final int ADD_DURATION = 190;
  private static final int REMOVE_DURATION = 150;
  private static final int MOVE_DURATION = 180;
  private static final int CHANGE_DURATION = 160;

  private final Map<RecyclerView.ViewHolder, Animator> runningAnimations = new HashMap<>();

  public SmoothChatListAnimator() {
    setSupportsChangeAnimations(false);
    setAddDuration(ADD_DURATION);
    setRemoveDuration(REMOVE_DURATION);
    setMoveDuration(MOVE_DURATION);
    setChangeDuration(CHANGE_DURATION);
  }

  @Override
  public boolean animateAdd(RecyclerView.ViewHolder holder) {
    cancelRunning(holder);
    View view = holder.itemView;

    view.setTranslationY(15f);
    view.setAlpha(0f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 15f, 0f),
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

    AnimatorSet set = new AnimatorSet();
    set.playTogether(ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f));
    set.setDuration(REMOVE_DURATION);
    set.setInterpolator(new DecelerateInterpolator(2f));
    set.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            runningAnimations.remove(holder);
            view.setAlpha(1f);
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
    int deltaX = toX - fromX;
    int deltaY = toY - fromY;

    if (deltaX == 0 && deltaY == 0) {
      dispatchMoveFinished(holder);
      return false;
    }

    cancelRunning(holder);

    AnimatorSet set = new AnimatorSet();
    if (deltaX != 0 && deltaY != 0) {
      set.playTogether(
          ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -deltaX, 0f),
          ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -deltaY, 0f));
    } else if (deltaX != 0) {
      set.play(ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -deltaX, 0f));
    } else {
      set.play(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -deltaY, 0f));
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

  @Override
  public boolean animateChange(
      RecyclerView.ViewHolder oldHolder,
      RecyclerView.ViewHolder newHolder,
      int fromLeft,
      int fromTop,
      int toLeft,
      int toTop) {
    if (oldHolder == newHolder) {
      dispatchChangeFinished(oldHolder, true);
      return false;
    }

    cancelRunning(oldHolder);
    cancelRunning(newHolder);

    View oldView = oldHolder.itemView;
    View newView = newHolder.itemView;

    newView.setAlpha(0f);
    newView.setTranslationY(10f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(oldView, View.ALPHA, 1f, 0f),
        ObjectAnimator.ofFloat(newView, View.ALPHA, 0f, 1f),
        ObjectAnimator.ofFloat(newView, View.TRANSLATION_Y, 10f, 0f));
    set.setDuration(CHANGE_DURATION);
    set.setInterpolator(new FastOutSlowInInterpolator());
    set.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            runningAnimations.remove(oldHolder);
            runningAnimations.remove(newHolder);
            oldView.setAlpha(1f);
            newView.setAlpha(1f);
            newView.setTranslationX(0f);
            newView.setTranslationY(0f);
            dispatchChangeFinished(oldHolder, true);
            dispatchChangeFinished(newHolder, false);
          }
        });
    runningAnimations.put(oldHolder, set);
    runningAnimations.put(newHolder, set);
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
