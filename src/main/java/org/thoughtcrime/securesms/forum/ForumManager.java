package org.thoughtcrime.securesms.forum;

import android.util.Log;
import com.b44t.messenger.DcContext;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages forum topics stored in chat description.
 *
 * Data is compact — topics are serialized as a small JSON object
 * in the chat description field. This uses minimal email bandwidth.
 *
 * Format: {"t":[{"id":1,"n":"General","e":"💬","c":"#2AABEE"}]}
 * Total overhead: ~50-100 bytes per topic
 */
public class ForumManager {
    private static final String TAG = "ForumManager";
    private static final String KEY_TOPICS = "t";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "n";
    private static final String KEY_EMOJI = "e";
    private static final String KEY_COLOR = "c";

    private final DcContext dcContext;

    public ForumManager(DcContext dcContext) {
        this.dcContext = dcContext;
    }

    /**
     * Check if a chat has forum topics enabled.
     * A chat is a forum if its description contains topic JSON.
     */
    public boolean isForum(int chatId) {
        String desc = dcContext.getChat(chatId).getDescription();
        return desc != null && desc.startsWith("{") && desc.contains(KEY_TOPICS);
    }

    /**
     * Get all topics for a chat. Returns empty list if not a forum.
     */
    public List<ForumTopic> getTopics(int chatId) {
        List<ForumTopic> topics = new ArrayList<>();
        String desc = dcContext.getChat(chatId).getDescription();
        if (desc == null || desc.isEmpty()) return topics;

        try {
            JSONObject json = new JSONObject(desc);
            JSONArray arr = json.optJSONArray(KEY_TOPICS);
            if (arr == null) return topics;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.getJSONObject(i);
                topics.add(new ForumTopic(
                    t.getInt(KEY_ID),
                    t.getString(KEY_NAME),
                    t.optString(KEY_EMOJI, "💬"),
                    t.optString(KEY_COLOR, "#2AABEE")
                ));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse forum topics", e);
        }
        return topics;
    }

    /**
     * Save topics to chat description. Compact JSON format.
     */
    public boolean saveTopics(int chatId, List<ForumTopic> topics) {
        try {
            JSONObject json = new JSONObject();
            JSONArray arr = new JSONArray();
            for (ForumTopic topic : topics) {
                JSONObject t = new JSONObject();
                t.put(KEY_ID, topic.getId());
                t.put(KEY_NAME, topic.getName());
                t.put(KEY_EMOJI, topic.getEmoji());
                t.put(KEY_COLOR, topic.getColor());
                arr.put(t);
            }
            json.put(KEY_TOPICS, arr);
            String desc = json.toString();
            dcContext.setChatDescription(chatId, desc);
            return true;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save forum topics", e);
            return false;
        }
    }

    /**
     * Create a new topic. Auto-assigns next ID.
     */
    public ForumTopic createTopic(int chatId, String name, String emoji, String color) {
        List<ForumTopic> topics = getTopics(chatId);
        int nextId = 1;
        for (ForumTopic t : topics) {
            if (t.getId() >= nextId) nextId = t.getId() + 1;
        }
        ForumTopic topic = new ForumTopic(nextId, name, emoji, color);
        topics.add(topic);
        saveTopics(chatId, topics);
        return topic;
    }

    /**
     * Update an existing topic.
     */
    public boolean updateTopic(int chatId, ForumTopic updated) {
        List<ForumTopic> topics = getTopics(chatId);
        for (int i = 0; i < topics.size(); i++) {
            if (topics.get(i).getId() == updated.getId()) {
                topics.set(i, updated);
                return saveTopics(chatId, topics);
            }
        }
        return false;
    }

    /**
     * Delete a topic and its messages.
     */
    public boolean deleteTopic(int chatId, int topicId) {
        List<ForumTopic> topics = getTopics(chatId);
        for (int i = 0; i < topics.size(); i++) {
            if (topics.get(i).getId() == topicId) {
                topics.remove(i);
                return saveTopics(chatId, topics);
            }
        }
        return false;
    }

    /**
     * Get topic by ID.
     */
    public ForumTopic getTopic(int chatId, int topicId) {
        List<ForumTopic> topics = getTopics(chatId);
        for (ForumTopic t : topics) {
            if (t.getId() == topicId) return t;
        }
        return null;
    }

    /**
     * Enable forum mode on a chat by creating a default "General" topic.
     */
    public ForumTopic enableForum(int chatId) {
        if (isForum(chatId)) {
            List<ForumTopic> topics = getTopics(chatId);
            return topics.isEmpty() ? null : topics.get(0);
        }
        return createTopic(chatId, "General", "💬", "#2AABEE");
    }

    /**
     * Get the topic ID from a message's subject line.
     * Returns DEFAULT_TOPIC_ID if no topic tag.
     */
    public static int getMessageTopicId(String subject) {
        int id = ForumTopic.extractTopicId(subject);
        return id > 0 ? id : ForumTopic.DEFAULT_TOPIC_ID;
    }

    /**
     * Create a subject tag for a topic.
     */
    public static String makeTopicSubject(int topicId) {
        return ForumTopic.TOPIC_PREFIX + topicId;
    }
}
