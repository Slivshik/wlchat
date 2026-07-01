package org.thoughtcrime.securesms.reactions;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import chat.delta.rpc.types.Reaction;
import chat.delta.rpc.types.Reactions;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ViewUtil;

public class ReactionsConversationView extends LinearLayout {

  // Normally 6dp, but we have 1dp left+right margin on the pills themselves
  private static final int OUTER_MARGIN = ViewUtil.dpToPx(4);
  private static final int MAX_AVATARS_SHOWN = 2;
  private static final int AVATAR_SIZE_DP = 12;

  private final List<Reaction> reactions = new ArrayList<>();
  private boolean isIncoming;

  public ReactionsConversationView(Context context) {
    super(context);
    init(null);
  }

  public ReactionsConversationView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init(attrs);
  }

  private void init(@Nullable AttributeSet attrs) {
    if (attrs != null) {
      TypedArray typedArray =
          getContext()
              .getTheme()
              .obtainStyledAttributes(attrs, R.styleable.ReactionsConversationView, 0, 0);
      isIncoming = typedArray.getInt(R.styleable.ReactionsConversationView_reaction_type, 0) == 2;
    }
  }

  public void clear() {
    this.reactions.clear();
    removeAllViews();
  }

  public void setReactions(Reactions reactionsData, GlideRequests glideRequests) {
    List<Reaction> reactionList =
        reactionsData != null ? reactionsData.reactions : new ArrayList<>();
    if (reactionList.equals(this.reactions)) {
      return;
    }

    clear();
    this.reactions.addAll(reactionList);

    Map<String, List<Integer>> emojiToContactIds =
        reactionsData != null ? buildEmojiToContactIds(reactionsData) : new HashMap<>();

    for (Reaction reaction : buildShortenedReactionsList(this.reactions)) {
      List<Integer> contactIds =
          reaction.emoji != null ? emojiToContactIds.get(reaction.emoji) : null;
      if (contactIds == null) contactIds = new ArrayList<>();
      View pill = buildPill(getContext(), this, reaction, contactIds, glideRequests);
      addView(pill);
    }

    if (isIncoming) {
      ViewUtil.setLeftMargin(this, OUTER_MARGIN);
    } else {
      ViewUtil.setRightMargin(this, OUTER_MARGIN);
    }
  }

  @SuppressWarnings("deprecation")
  private static @NonNull Map<String, List<Integer>> buildEmojiToContactIds(
      @NonNull Reactions reactionsData) {
    Map<String, List<Integer>> result = new HashMap<>();
    if (reactionsData.reactionsByContact == null) return result;

    for (Map.Entry<String, List<String>> entry : reactionsData.reactionsByContact.entrySet()) {
      int contactId;
      try {
        contactId = Integer.parseInt(entry.getKey());
      } catch (NumberFormatException e) {
        continue;
      }
      for (String emoji : entry.getValue()) {
        List<Integer> ids = result.get(emoji);
        if (ids == null) {
          ids = new ArrayList<>();
          result.put(emoji, ids);
        }
        ids.add(contactId);
      }
    }
    return result;
  }

  private static @NonNull List<Reaction> buildShortenedReactionsList(
      @NonNull List<Reaction> reactions) {
    if (reactions.size() > 3) {
      List<Reaction> shortened = new ArrayList<>(3);
      shortened.add(reactions.get(0));
      shortened.add(reactions.get(1));
      int count = 0;
      boolean isFromSelf = false;
      for (int index = 2; index < reactions.size(); index++) {
        count += reactions.get(index).count;
        isFromSelf = isFromSelf || reactions.get(index).isFromSelf;
      }
      Reaction reaction = new Reaction();
      reaction.emoji = null;
      reaction.count = count;
      reaction.isFromSelf = isFromSelf;
      shortened.add(reaction);

      return shortened;
    } else {
      return reactions;
    }
  }

  private static View buildPill(
      @NonNull Context context,
      @NonNull ViewGroup parent,
      @NonNull Reaction reaction,
      @NonNull List<Integer> contactIds,
      @Nullable GlideRequests glideRequests) {
    View root = LayoutInflater.from(context).inflate(R.layout.reactions_pill, parent, false);
    AppCompatTextView emojiView = root.findViewById(R.id.reactions_pill_emoji);
    TextView countView = root.findViewById(R.id.reactions_pill_count);
    View spacer = root.findViewById(R.id.reactions_pill_spacer);
    LinearLayout avatarsContainer = root.findViewById(R.id.reactions_pill_avatars);

    if (reaction.emoji != null) {
      emojiView.setText(reaction.emoji);

      if (reaction.count > 1) {
        countView.setText(String.valueOf(reaction.count));
      } else {
        countView.setVisibility(GONE);
        spacer.setVisibility(GONE);
      }
    } else {
      emojiView.setVisibility(GONE);
      spacer.setVisibility(GONE);
      countView.setText("+" + reaction.count);
    }

    if (reaction.isFromSelf) {
      root.setBackground(
          ContextCompat.getDrawable(context, R.drawable.reaction_pill_background_selected));
      countView.setTextColor(
          ContextCompat.getColor(context, R.color.reaction_pill_text_color_selected));
    } else {
      root.setBackground(ContextCompat.getDrawable(context, R.drawable.reaction_pill_background));
    }

    if (glideRequests != null && !contactIds.isEmpty()) {
      avatarsContainer.setVisibility(VISIBLE);
      int avatarSizePx = ViewUtil.dpToPx(AVATAR_SIZE_DP);
      int overlapPx = ViewUtil.dpToPx(2);
      int shown = Math.min(contactIds.size(), MAX_AVATARS_SHOWN);

      DcContext dcContext = DcHelper.getContext(context);

      for (int i = 0; i < shown; i++) {
        int contactId = contactIds.get(i);
        AvatarImageView avatar = new AvatarImageView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(avatarSizePx, avatarSizePx);
        if (i > 0) {
          lp.setMarginStart(-overlapPx);
        }
        avatar.setLayoutParams(lp);
        avatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);

        DcContact dcContact = dcContext.getContact(contactId);
        Recipient recipient = new Recipient(context, dcContact);
        avatar.setAvatar(glideRequests, recipient, false);

        avatarsContainer.addView(avatar);
      }

      if (contactIds.size() > MAX_AVATARS_SHOWN) {
        TextView overflow = new TextView(context);
        LinearLayout.LayoutParams overflowLp =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        overflowLp.setMarginStart(ViewUtil.dpToPx(2));
        overflow.setLayoutParams(overflowLp);
        overflow.setTextSize(9);
        overflow.setTextColor(
            ContextCompat.getColor(context, R.color.reaction_pill_text_color));
        overflow.setText("+" + (contactIds.size() - MAX_AVATARS_SHOWN));
        avatarsContainer.addView(overflow);
      }
    }

    return root;
  }
}
