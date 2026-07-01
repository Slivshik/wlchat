package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ResUtil;

public class EmojiToggle extends AppCompatImageButton {

  private Drawable emojiToggle;
  //  private Drawable stickerToggle;

  private Drawable mediaToggle;
  private Drawable imeToggle;

  public EmojiToggle(Context context) {
    super(context);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  public EmojiToggle(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    initialize();
  }

  public void setToMedia() {
    setImageDrawable(mediaToggle);
  }

  public void setToIme() {
    setImageDrawable(imeToggle);
  }

  /**
   * Smoothly toggle between emoji and keyboard with rotation animation
   */
  public void toggleSmoothly(boolean showEmoji) {
    // Scale out
    animate()
        .scaleX(0f)
        .scaleY(0f)
        .setDuration(100)
        .withEndAction(() -> {
          if (showEmoji) {
            setToMedia();
          } else {
            setToIme();
          }
          // Scale in
          animate()
              .scaleX(1f)
              .scaleY(1f)
              .setDuration(150)
              .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
              .start();
        })
        .start();
  }

  private void initialize() {
    this.emojiToggle = ResUtil.getDrawable(getContext(), R.attr.conversation_emoji_toggle);
    //    this.stickerToggle = ResUtil.getDrawable(getContext(),
    // R.attr.conversation_sticker_toggle);
    this.imeToggle = ResUtil.getDrawable(getContext(), R.attr.conversation_keyboard_toggle);
    this.mediaToggle = emojiToggle;

    setToMedia();
  }

  public void setStickerMode(boolean stickerMode) {
    this.mediaToggle = /*stickerMode ? stickerToggle :*/ emojiToggle;

    if (getDrawable() != imeToggle) {
      setToMedia();
    }
  }

  public boolean isStickerMode() {
    // return this.mediaToggle == stickerToggle;
    return false;
  }
}
