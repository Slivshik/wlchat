package org.thoughtcrime.securesms.components.recyclerview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Smooth Telegram-like item animator with slide + fade for add/remove/move animations.
 * Messages slide in from bottom with spring effect, slide out to right with fade.
 */
public class SmoothSlideItemAnimator extends DefaultItemAnimator {

  private static final int ADD_DURATION = 120;
  private static final int REMOVE_DURATION = 80;
  private static final int MOVE_DURATION = 120;

  public SmoothSlideItemAnimator() {
    setSupportsChangeAnimations(false);
    setAddDuration(ADD_DURATION);
    setRemoveDuration(REMOVE_DURATION);
    setMoveDuration(MOVE_DURATION);
  }

  @Override
  public boolean animateAdd(RecyclerView.ViewHolder holder) {
    View view = holder.itemView;

    // Start from slightly below, no fade
    view.setTranslationY(24f);
    view.setAlpha(1f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, "translationY", 24f, 0f)
    );
    set.setDuration(ADD_DURATION);
    set.setInterpolator(new FastOutSlowInInterpolator());
    set.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        view.setTranslationY(0f);
        view.setAlpha(1f);
        dispatchAddFinished(holder);
      }
    });
    set.start();
    return true;
  }

  @Override
  public boolean animateRemove(RecyclerView.ViewHolder holder) {
    View view = holder.itemView;

    // Quick slide to right without fade
    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, "translationX", 0f, view.getWidth() * 0.04f)
    );
    set.setDuration(REMOVE_DURATION);
    set.setInterpolator(new DecelerateInterpolator(2f));
    set.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        view.setTranslationX(0f);
        view.setAlpha(1f);
        dispatchRemoveFinished(holder);
      }
    });
    set.start();
    return true;
  }

  @Override
  public boolean animateMove(RecyclerView.ViewHolder holder, int fromX, int fromY, int toX, int toY) {
    View view = holder.itemView;
    int dx = toX - fromX;
    int dy = toY - fromY;

    AnimatorSet set = new AnimatorSet();
    if (dx != 0 && dy != 0) {
      set.playTogether(
          ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -dx, 0f),
          ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -dy, 0f)
      );
    } else if (dx != 0) {
      set.play(ObjectAnimator.ofFloat(view, View.TRANSLATION_X, -dx, 0f));
    } else if (dy != 0) {
      set.play(ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -dy, 0f));
    }
    set.setDuration(MOVE_DURATION);
    set.setInterpolator(new FastOutSlowInInterpolator());
    set.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        view.setTranslationX(0f);
        view.setTranslationY(0f);
      }
    });
    set.start();
    return true;
  }
}
