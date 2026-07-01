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
 * Smooth Telegram-like item animator with slide + fade for add/remove/move animations.
 * Messages slide in from bottom with spring effect, slide out to right with fade.
 */
public class SmoothSlideItemAnimator extends DefaultItemAnimator {

  private static final int ADD_DURATION = 250;
  private static final int REMOVE_DURATION = 200;
  private static final int MOVE_DURATION = 220;

  public SmoothSlideItemAnimator() {
    setSupportsChangeAnimations(false);
    setAddDuration(ADD_DURATION);
    setRemoveDuration(REMOVE_DURATION);
    setMoveDuration(MOVE_DURATION);
  }

  @Override
  public boolean animateAdd(RecyclerView.ViewHolder holder) {
    View view = holder.itemView;

    // Start from slightly below and transparent
    view.setTranslationY(60f);
    view.setAlpha(0f);
    view.setScaleX(0.97f);
    view.setScaleY(0.97f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, "translationY", 60f, 0f),
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
        ObjectAnimator.ofFloat(view, "scaleX", 0.97f, 1f),
        ObjectAnimator.ofFloat(view, "scaleY", 0.97f, 1f)
    );
    set.setDuration(ADD_DURATION);
    set.setInterpolator(new OvershootInterpolator(1.15f));
    set.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        view.setTranslationY(0f);
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
        ObjectAnimator.ofFloat(view, "translationX", 0f, view.getWidth() * 0.25f),
        ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f),
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f)
    );
    set.setDuration(REMOVE_DURATION);
    set.setInterpolator(new DecelerateInterpolator(2.5f));
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
    set.setInterpolator(new DecelerateInterpolator(1.8f));
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
