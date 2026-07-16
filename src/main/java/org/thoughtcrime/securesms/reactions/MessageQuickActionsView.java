package org.thoughtcrime.securesms.reactions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.b44t.messenger.DcMsg;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.ViewUtil;

/**
 * Small floating card with the most common per-message actions (reply/forward/copy/delete),
 * shown right below the reaction bar on long-press, alongside the full multi-select action mode
 * (checkboxes + top action bar), which is started at the same time.
 */
public class MessageQuickActionsView extends LinearLayout {

  private View replyRow;
  private View forwardRow;
  private View copyRow;
  private View deleteRow;
  private boolean initialized;

  public MessageQuickActionsView(Context context) {
    super(context);
  }

  public MessageQuickActionsView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  private void init() {
    if (initialized) return;
    initialized = true;
    replyRow = findViewById(R.id.qa_reply);
    forwardRow = findViewById(R.id.qa_forward);
    copyRow = findViewById(R.id.qa_copy);
    deleteRow = findViewById(R.id.qa_delete);
  }

  /** @param topY final vertical position, already decided by the caller together with
   * AddReactionView's - this view only clamps it to its own screen bounds as a safety net,
   * it does not decide on its own whether to flip above the message (the caller already
   * accounted for both views' combined height when it does that). */
  public void show(DcMsg message, View anchorForXPosition, int topY, Listener listener) {
    init();

    replyRow.setOnClickListener(
        v -> {
          listener.onReply();
          hide();
        });
    forwardRow.setOnClickListener(
        v -> {
          listener.onForward();
          hide();
        });
    copyRow.setOnClickListener(
        v -> {
          listener.onCopy();
          hide();
        });
    deleteRow.setOnClickListener(
        v -> {
          listener.onDelete();
          hide();
        });

    int x = (int) anchorForXPosition.getX();
    if (message.isOutgoing()) {
      x += anchorForXPosition.getWidth() - getWidth();
    }

    // keep a small gap from the screen edge instead of letting the card touch it.
    int edgeMargin = (int) (getResources().getDisplayMetrics().density * 10);

    View parent = (View) getParent();
    if (parent != null) {
      int maxLeft = Math.max(edgeMargin, parent.getWidth() - getWidth() - edgeMargin);
      x = Math.min(Math.max(x, edgeMargin), maxLeft);

      int maxTop = Math.max(0, parent.getHeight() - getHeight());
      topY = Math.min(Math.max(topY, 0), maxTop);
    } else {
      x = Math.max(x, edgeMargin);
    }

    ViewUtil.setLeftMargin(this, x);
    ViewUtil.setTopMargin(this, topY);

    setPivotX(message.isOutgoing() ? getWidth() : 0f);
    setPivotY(0f);
    animate().cancel();
    setScaleX(0.7f);
    setScaleY(0.7f);
    setAlpha(0f);
    setVisibility(View.VISIBLE);
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .alpha(1f)
        .setStartDelay(30)
        .setDuration(200)
        .setInterpolator(new OvershootInterpolator(1.1f))
        .start();
  }

  public void hide() {
    if (getVisibility() != View.VISIBLE) {
      return;
    }
    animate().cancel();
    animate()
        .scaleX(0.7f)
        .scaleY(0.7f)
        .alpha(0f)
        .setStartDelay(0)
        .setDuration(120)
        .setInterpolator(new FastOutSlowInInterpolator())
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                setVisibility(View.GONE);
                setScaleX(1f);
                setScaleY(1f);
                setAlpha(1f);
              }
            })
        .start();
  }

  public void move(int dy) {
    if (getVisibility() == View.VISIBLE) {
      ViewUtil.setTopMargin(this, (int) this.getY() - dy);
    }
  }

  public interface Listener {
    void onReply();

    void onForward();

    void onCopy();

    void onDelete();
  }
}
