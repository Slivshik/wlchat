package org.thoughtcrime.securesms.forum;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.DcContext;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;

/**
 * Telegram-style forum topics list.
 * Shows topics as a list with emoji, name, and message count.
 * Tapping a topic opens the conversation filtered to that topic.
 *
 * Data is stored compactly in chat description — minimal bandwidth.
 */
public class ForumActivity extends PassphraseRequiredActionBarActivity {

    private static final String EXTRA_CHAT_ID = "chat_id";

    private int chatId;
    private DcContext dcContext;
    private ForumManager forumManager;
    private RecyclerView recyclerView;
    private TopicAdapter adapter;
    private List<ForumTopic> topics = new ArrayList<>();
    private View unsupportedContentView;
    private View addTopicButton;

    public static void start(Context context, int chatId) {
        Intent intent = new Intent(context, ForumActivity.class);
        intent.putExtra(EXTRA_CHAT_ID, chatId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState, boolean ready) {
        super.onCreate(savedInstanceState, ready);
        setContentView(R.layout.forum_activity);

        chatId = getIntent().getIntExtra(EXTRA_CHAT_ID, 0);
        dcContext = DcHelper.getContext(this);
        forumManager = new ForumManager(dcContext, DcHelper.getRpc(this));

        setupToolbar();
        setupViews();
        loadTopics();
    }

    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.forum_topics);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }

    private void setupViews() {
        recyclerView = findViewById(R.id.forum_topics_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TopicAdapter();
        recyclerView.setAdapter(adapter);
        unsupportedContentView = findViewById(R.id.forum_unsupported_content);

        // Add topic button
        addTopicButton = findViewById(R.id.forum_add_topic);
        if (addTopicButton != null) {
            addTopicButton.setOnClickListener(v -> showCreateTopicDialog());
        }
    }

    private void loadTopics() {
        if (forumManager.isUnsupportedVersion(chatId)) {
            // This chat's forum data was written by a newer WL Chat version in a format this
            // build can't parse - show a placeholder instead of silently dropping/overwriting it.
            recyclerView.setVisibility(View.GONE);
            if (unsupportedContentView != null) unsupportedContentView.setVisibility(View.VISIBLE);
            if (addTopicButton != null) addTopicButton.setVisibility(View.GONE);
            return;
        }

        topics.clear();
        if (forumManager.isForum(chatId)) {
            topics.addAll(forumManager.getTopics(chatId));
        }
        if (topics.isEmpty()) {
            // Auto-create General topic
            ForumTopic general = forumManager.enableForum(chatId);
            if (general != null) {
                topics.add(general);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showCreateTopicDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_topic, null);
        EditText nameInput = dialogView.findViewById(R.id.topic_name_input);

        new AlertDialog.Builder(this)
            .setTitle(R.string.create_topic)
            .setView(dialogView)
            .setPositiveButton(R.string.create, (d, w) -> {
                String name = nameInput.getText().toString().trim();
                if (!name.isEmpty()) {
                    ForumTopic topic = forumManager.createTopic(chatId, name, "💬", "#2AABEE");
                    topics.add(topic);
                    adapter.notifyItemInserted(topics.size() - 1);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showEditTopicDialog(ForumTopic topic) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_topic, null);
        EditText nameInput = dialogView.findViewById(R.id.topic_name_input);
        nameInput.setText(topic.getName());

        new AlertDialog.Builder(this)
            .setTitle(R.string.edit_topic)
            .setView(dialogView)
            .setPositiveButton(R.string.save, (d, w) -> {
                String name = nameInput.getText().toString().trim();
                if (!name.isEmpty()) {
                    topic.setName(name);
                    forumManager.updateTopic(chatId, topic);
                    adapter.notifyDataSetChanged();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openTopic(ForumTopic topic) {
        // Open conversation with topic filter
        Intent intent = new Intent(this, org.thoughtcrime.securesms.ConversationActivity.class);
        intent.putExtra("chat_id", chatId);
        intent.putExtra("forum_topic_id", topic.getId());
        intent.putExtra("forum_topic_name", topic.getName());
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // ─── Adapter ──────────────────────────────────────────────────────

    private class TopicAdapter extends RecyclerView.Adapter<TopicViewHolder> {

        @Override
        public TopicViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.forum_topic_item, parent, false);
            return new TopicViewHolder(view);
        }

        @Override
        public void onBindViewHolder(TopicViewHolder holder, int position) {
            ForumTopic topic = topics.get(position);
            holder.bind(topic);
        }

        @Override
        public int getItemCount() {
            return topics.size();
        }
    }

    // ─── ViewHolder ───────────────────────────────────────────────────

    private class TopicViewHolder extends RecyclerView.ViewHolder {
        private final View itemView;
        private final TextView emojiIcon;
        private final TextView topicName;
        private final View colorBar;
        private final TextView messageCount;

        TopicViewHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;
            this.emojiIcon = itemView.findViewById(R.id.topic_emoji);
            this.topicName = itemView.findViewById(R.id.topic_name);
            this.colorBar = itemView.findViewById(R.id.topic_color_bar);
            this.messageCount = itemView.findViewById(R.id.topic_message_count);
        }

        void bind(ForumTopic topic) {
            topicName.setText(topic.getName());

            // Set emoji
            emojiIcon.setText(topic.getEmoji());

            // Set color bar
            try {
                GradientDrawable colorDot = new GradientDrawable();
                colorDot.setShape(GradientDrawable.OVAL);
                colorDot.setColor(Color.parseColor(topic.getColor()));
                colorDot.setSize(12, 12);
                colorBar.setBackground(colorDot);
            } catch (Exception e) {
                colorBar.setVisibility(View.GONE);
            }

            // Count messages in this topic
            int count = countMessagesForTopic(topic.getId());
            if (count > 0) {
                messageCount.setText(String.valueOf(count));
                messageCount.setVisibility(View.VISIBLE);
            } else {
                messageCount.setVisibility(View.GONE);
            }

            // Click to open topic
            itemView.setOnClickListener(v -> openTopic(topic));

            // Long press to edit
            itemView.setOnLongClickListener(v -> {
                showEditTopicDialog(topic);
                return true;
            });
        }

        private int countMessagesForTopic(int topicId) {
            // Count messages with this topic's subject tag
            String subject = ForumManager.makeTopicSubject(topicId);
            int[] msgIds = dcContext.getChatMsgs(chatId, 0, 0);
            int count = 0;
            for (int msgId : msgIds) {
                com.b44t.messenger.DcMsg msg = dcContext.getMsg(msgId);
                if (msg != null && subject.equals(msg.getSubject())) {
                    count++;
                }
            }
            return count;
        }
    }
}
