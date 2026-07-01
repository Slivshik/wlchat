package org.thoughtcrime.securesms.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Utility class for smooth Telegram-like UI animations.
 * Provides reusable animation methods for common UI interactions.
 */
public class SmoothAnimator {

  private static final int FAST_DURATION = 150;
  private static final int NORMAL_DURATION = 250;
  private static final int SLOW_DURATION = 350;

  /**
   * Smooth scale + fade in animation (for button presses, menu items, etc.)
   */
  public static void scaleIn(View view, int duration) {
    view.setAlpha(0f);
    view.setScaleX(0.8f);
    view.setScaleY(0.8f);
    view.animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(duration)
        .setInterpolator(new OvershootInterpolator(1.2f))
        .start();
  }

  public static void scaleIn(View view) {
    scaleIn(view, NORMAL_DURATION);
  }

  /**
   * Smooth scale + fade out animation
   */
  public static void scaleOut(View view, int duration, AnimatorListenerAdapter listener) {
    view.animate()
        .alpha(0f)
        .scaleX(0.8f)
        .scaleY(0.8f)
        .setDuration(duration)
        .setInterpolator(new AccelerateDecelerateInterpolator())
        .setListener(listener)
        .start();
  }

  public static void scaleOut(View view) {
    scaleOut(view, FAST_DURATION, null);
  }

  /**
   * Smooth slide in from bottom with spring effect
   */
  public static void slideInFromBottom(View view, int duration) {
    view.setTranslationY(view.getHeight() + 100f);
    view.setAlpha(0f);
    view.animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(new OvershootInterpolator(1.1f))
        .start();
  }

  /**
   * Smooth slide out to bottom with fade
   */
  public static void slideOutToBottom(View view, int duration, AnimatorListenerAdapter listener) {
    view.animate()
        .translationY(view.getHeight() + 100f)
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(new DecelerateInterpolator(2f))
        .setListener(listener)
        .start();
  }

  /**
   * Smooth fade in
   */
  public static void fadeIn(View view, int duration) {
    view.setAlpha(0f);
    view.animate()
        .alpha(1f)
        .setDuration(duration)
        .setInterpolator(new DecelerateInterpolator(2f))
        .start();
  }

  public static void fadeIn(View view) {
    fadeIn(view, FAST_DURATION);
  }

  /**
   * Smooth fade out with optional listener
   */
  public static void fadeOut(View view, int duration, AnimatorListenerAdapter listener) {
    view.animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(new AccelerateDecelerateInterpolator())
        .setListener(listener)
        .start();
  }

  public static void fadeOut(View view) {
    fadeOut(view, FAST_DURATION, null);
  }

  /**
   * Smooth rotation animation (for expand/collapse icons)
   */
  public static void rotate(View view, float fromDegrees, float toDegrees, int duration) {
    ObjectAnimator rotate = ObjectAnimator.ofFloat(view, "rotation", fromDegrees, toDegrees);
    rotate.setDuration(duration);
    rotate.setInterpolator(new OvershootInterpolator(1.5f));
    rotate.start();
  }

  /**
   * Pulse animation (for attention-grabbing elements)
   */
  public static void pulse(View view) {
    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f),
        ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f)
    );
    set.setDuration(400);
    set.setInterpolator(new AccelerateDecelerateInterpolator());
    set.start();
  }

  /**
   * Shake animation (for error feedback)
   */
  public static void shake(View view) {
    ObjectAnimator shake = ObjectAnimator.ofFloat(view, "translationX",
        0, 15, -15, 10, -10, 5, -5, 0);
    shake.setDuration(500);
    shake.setInterpolator(new AccelerateDecelerateInterpolator());
    shake.start();
  }

  /**
   * Stagger animation for a group of views (e.g., menu items appearing one by one)
   */
  public static void staggerIn(View[] views, int durationPerItem, int staggerDelay) {
    for (int i = 0; i < views.length; i++) {
      View view = views[i];
      view.setAlpha(0f);
      view.setTranslationY(20f);
      view.animate()
          .alpha(1f)
          .translationY(0f)
          .setDuration(durationPerItem)
          .setStartDelay(staggerDelay * i)
          .setInterpolator(new OvershootInterpolator(1.1f))
          .start();
    }
  }

  /**
   * Smooth height expansion animation (for expanding content areas)
   */
  public static void expandHeight(View view, int targetHeight, int duration) {
    ValueAnimator animator = ValueAnimator.ofInt(view.getHeight(), targetHeight);
    animator.setDuration(duration);
    animator.setInterpolator(new DecelerateInterpolator(2f));
    animator.addUpdateListener(animation -> {
      int value = (int) animation.getAnimatedValue();
      android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
      params.height = value;
      view.setLayoutParams(params);
    });
    animator.start();
  }

  /**
   * Smooth height collapse animation
   */
  public static void collapseHeight(View view, int duration, AnimatorListenerAdapter listener) {
    int initialHeight = view.getHeight();
    ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
    animator.setDuration(duration);
    animator.setInterpolator(new DecelerateInterpolator(2f));
    animator.addUpdateListener(animation -> {
      int value = (int) animation.getAnimatedValue();
      android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
      params.height = value;
      view.setLayoutParams(params);
    });
    if (listener != null) {
      animator.addListener(listener);
    }
    animator.start();
  }
}
