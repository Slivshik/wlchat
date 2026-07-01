package org.thoughtcrime.securesms.components.recyclerview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Smooth item animator for chat list with Telegram-like animations.
 * Items slide in from left with fade, slide out to right with fade,
 * and move smoothly to their new positions.
 */
public class SmoothChatListAnimator extends DefaultItemAnimator {

  private static final int ADD_DURATION = 280;
  private static final int REMOVE_DURATION = 220;
  private static final int MOVE_DURATION = 250;
  private static final int CHANGE_DURATION = 200;

  public SmoothChatListAnimator() {
    setSupportsChangeAnimations(false);
    setAddDuration(ADD_DURATION);
    setRemoveDuration(REMOVE_DURATION);
    setMoveDuration(MOVE_DURATION);
    setChangeDuration(CHANGE_DURATION);
  }

  @Override
  public boolean animateAdd(RecyclerView.ViewHolder holder) {
    View view = holder.itemView;

    // Start state: slide in from left + fade in
    view.setTranslationX(-view.getWidth() * 0.3f);
    view.setAlpha(0f);
    view.setScaleX(0.95f);
    view.setScaleY(0.95f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, "translationX", -view.getWidth() * 0.3f, 0f),
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
        ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f),
        ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f)
    );
    set.setDuration(ADD_DURATION);
    set.setInterpolator(new OvershootInterpolator(1.1f));
    set.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        view.setTranslationX(0f);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        dispatchAddFinished(holder);
      }
    });
    set.start();
    return true;
  }

  @Override
  public boolean animateRemove(RecyclerView.ViewHolder holder) {
    View view = holder.itemView;

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, "translationX", 0f, view.getWidth() * 0.3f),
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f),
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f)
    );
    set.setDuration(REMOVE_DURATION);
    set.setInterpolator(new DecelerateInterpolator(2f));
    set.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        view.setTranslationX(0f);
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        dispatchRemoveFinished(holder);
      }
    });
    set.start();
    return true;
  }

  @Override
  public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
    View view = holder.itemView;

    int deltaX = toX - fromX;
    int deltaY = toY - fromY;

    if (deltaX != 0) {
      ObjectAnimator slideX = ObjectAnimator.ofFloat(view, "translationX", -deltaX, 0f);
      slideX.setDuration(MOVE_DURATION);
      slideX.setInterpolator(new DecelerateInterpolator(1.5f));
      slideX.start();
    }

    if (deltaY != 0) {
      ObjectAnimator slideY = ObjectAnimator.ofFloat(view, "translationY", -deltaY, 0f);
      slideY.setDuration(MOVE_DURATION);
      slideY.setInterpolator(new DecelerateInterpolator(1.5f));
      slideY.start();
    }

    return true;
  }

  @Override
  public boolean animateChange(RecyclerView.ViewHolder oldHolder, RecyclerView.ViewHolder newHolder, int fromLeft, int fromTop, int toLeft, int toTop) {
    if (oldHolder != newHolder) {
      // Cross-fade for item replacement
      View oldView = oldHolder.itemView;
      View newView = newHolder.itemView;

      newView.setAlpha(0f);
      newView.setTranslationX(oldView.getWidth() * 0.1f);

      AnimatorSet set = new AnimatorSet();
      set.playTogether(
          ObjectAnimator.ofFloat(oldView, "alpha", 1f, 0f),
          ObjectAnimator.ofFloat(newView, "alpha", 0f, 1f),
          ObjectAnimator.ofFloat(newView, "translationX", oldView.getWidth() * 0.1f, 0f)
      );
      set.setDuration(CHANGE_DURATION);
      set.setInterpolator(new DecelerateInterpolator(2f));
      set.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          oldView.setAlpha(1f);
          newView.setAlpha(1f);
          newView.setTranslationX(0f);
          dispatchChangeFinished(oldHolder, true);
          dispatchChangeFinished(newHolder, false);
        }
      });
      set.start();
      return true;
    }
    return false;
  }
}
