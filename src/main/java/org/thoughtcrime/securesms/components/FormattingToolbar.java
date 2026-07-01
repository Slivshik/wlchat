package org.thoughtcrime.securesms.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import org.thoughtcrime.securesms.R;

/**
 * A floating formatting toolbar that appears above the compose text when text is selected.
 * Provides Bold, Italic, Underline, Strikethrough, and Code formatting buttons.
 * Each button wraps the selected text with the appropriate Telegram-style markdown delimiters.
 */
public class FormattingToolbar extends LinearLayout {

  private static final int ANIM_DURATION_SHOW = 180;
  private static final int ANIM_DURATION_HIDE = 140;
  private static final int STAGGER_DELAY = 16;

  private EditText targetEditText;
  private boolean isShowing = false;
  private boolean isAnimating = false;

  private ImageButton btnBold;
  private ImageButton btnItalic;
  private ImageButton btnUnderline;
  private ImageButton btnStrikethrough;
  private ImageButton btnCode;

  public FormattingToolbar(Context context) {
    super(context);
    init();
  }

  public FormattingToolbar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public FormattingToolbar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    setOrientation(HORIZONTAL);
    setGravity(android.view.Gravity.CENTER_VERTICAL);
    setClipChildren(false);
    setClipToPadding(false);
    setVisibility(GONE);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    btnBold = findViewById(R.id.format_bold);
    btnItalic = findViewById(R.id.format_italic);
    btnUnderline = findViewById(R.id.format_underline);
    btnStrikethrough = findViewById(R.id.format_strikethrough);
    btnCode = findViewById(R.id.format_code);

    btnBold.setOnClickListener(v -> applyFormat("*", "*"));
    btnItalic.setOnClickListener(v -> applyFormat("_", "_"));
    btnUnderline.setOnClickListener(v -> applyFormat("__", "__"));
    btnStrikethrough.setOnClickListener(v -> applyFormat("~", "~"));
    btnCode.setOnClickListener(v -> applyFormat("`", "`"));
  }

  public void attachToEditText(EditText editText) {
    this.targetEditText = editText;
  }

  // --- Formatting Logic ---

  private void applyFormat(String openDelim, String closeDelim) {
    if (targetEditText == null) return;

    Editable text = targetEditText.getText();
    if (text == null) return;

    int start = targetEditText.getSelectionStart();
    int end = targetEditText.getSelectionEnd();

    if (start < 0 || end <= start || end > text.length()) return;

    // Check if already wrapped — if so, unwrap
    if (isAlreadyFormatted(text, start, end, openDelim, closeDelim)) {
      removeFormat(text, start, end, openDelim, closeDelim);
    } else {
      addFormat(text, start, end, openDelim, closeDelim);
    }
  }

  private boolean isAlreadyFormatted(Editable text, int start, int end, String open, String close) {
    if (start < open.length()) return false;
    String before = text.subSequence(start - open.length(), start).toString();
    if (!before.equals(open)) return false;
    if (end + close.length() > text.length()) return false;
    String after = text.subSequence(end, end + close.length()).toString();
    return after.equals(close);
  }

  private void addFormat(Editable text, int start, int end, String open, String close) {
    // Insert close delimiter first (to not shift start index)
    text.insert(end, close);
    // Then insert open delimiter
    text.insert(start, open);

    // Select just the content (between delimiters)
    targetEditText.setSelection(start + open.length(), end + open.length());
  }

  private void removeFormat(Editable text, int start, int end, String open, String close) {
    int newStart = start - open.length();
    int newEnd = end + close.length();

    if (newStart < 0 || newEnd > text.length()) return;

    text.delete(newEnd - close.length(), newEnd); // remove close
    text.delete(newStart, newStart + open.length()); // remove open

    int selStart = Math.max(0, newStart);
    int selEnd = Math.max(selStart, newEnd - open.length() - close.length());
    targetEditText.setSelection(selStart, selEnd);
  }

  // --- Animation ---

  public void show() {
    if (isShowing || isAnimating) return;
    isShowing = true;
    isAnimating = true;

    setVisibility(VISIBLE);
    setAlpha(0f);
    setTranslationY(18f);
    setScaleX(0.96f);
    setScaleY(0.96f);

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(this, "alpha", 0f, 1f),
        ObjectAnimator.ofFloat(this, "translationY", 18f, 0f),
        ObjectAnimator.ofFloat(this, "scaleX", 0.96f, 1f),
        ObjectAnimator.ofFloat(this, "scaleY", 0.96f, 1f)
    );
    set.setDuration(ANIM_DURATION_SHOW);
    set.setInterpolator(new FastOutSlowInInterpolator());
    set.addListener(new AnimatorListenerAdapter() {
      @Override public void onAnimationEnd(Animator animation) {
        setScaleX(1f);
        setScaleY(1f);
        isAnimating = false;
      }
    });
    set.start();

    // Stagger button scale-in
    ImageButton[] btns = {btnBold, btnItalic, btnUnderline, btnStrikethrough, btnCode};
    for (int i = 0; i < btns.length; i++) {
      ImageButton btn = btns[i];
      if (btn != null) {
        btn.setAlpha(0f);
        btn.setScaleX(0.88f);
        btn.setScaleY(0.88f);
        btn.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(ANIM_DURATION_SHOW)
            .setStartDelay(STAGGER_DELAY * (i + 1))
            .setInterpolator(new FastOutSlowInInterpolator())
            .start();
      }
    }
  }

  public void hide() {
    if (!isShowing || isAnimating) return;
    isShowing = false;
    isAnimating = true;

    AnimatorSet set = new AnimatorSet();
    set.playTogether(
        ObjectAnimator.ofFloat(this, "alpha", 1f, 0f),
        ObjectAnimator.ofFloat(this, "translationY", 0f, 10f),
        ObjectAnimator.ofFloat(this, "scaleX", 1f, 0.98f),
        ObjectAnimator.ofFloat(this, "scaleY", 1f, 0.98f)
    );
    set.setDuration(ANIM_DURATION_HIDE);
    set.setInterpolator(new DecelerateInterpolator(1.5f));
    set.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        setVisibility(GONE);
        setTranslationY(0f);
        setScaleX(1f);
        setScaleY(1f);
        isAnimating = false;
      }
    });
    set.start();
  }

  public boolean isShowing() {
    return isShowing;
  }
}
