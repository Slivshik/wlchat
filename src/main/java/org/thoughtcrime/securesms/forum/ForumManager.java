package org.thoughtcrime.securesms.forum;

import android.util.Log;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import com.b44t.messenger.DcContext;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.util.WlChatMarker;

public class ForumManager {
    private static final String TAG = "ForumManager";
    private static final String KEY_TOPICS = "t";
    private static final String KEY_ID = "id";
    private static final String KEY_NAME = "n";
    private static final String KEY_EMOJI = "e";
    private static final String KEY_COLOR = "c";
    private static final String KEY_VERSION = "v";

    private final DcContext dcContext;
    private final Rpc rpc;

    public ForumManager(DcContext dcContext, Rpc rpc) {
        this.dcContext = dcContext;
        this.rpc = rpc;
    }

    private String getDescription(int chatId) {
        try {
            return rpc.getChatDescription(dcContext.getAccountId(), chatId);
        } catch (RpcException e) {
            Log.w(TAG, "getChatDescription failed", e);
            return null;
        }
    }

    private void setDescription(int chatId, String desc) throws RpcException {
        rpc.setChatDescription(dcContext.getAccountId(), chatId, desc);
    }

    public boolean isForum(int chatId) {
        String desc = getDescription(chatId);
        return desc != null && desc.startsWith("{") && desc.contains(KEY_TOPICS);
    }

    /** True if this chat's forum data was written by a newer WL Chat version using a data
     * format this build doesn't know how to parse - callers should show a "new content, update
     * WL Chat" placeholder instead of attempting to read or, worse, overwrite the data. */
    public boolean isUnsupportedVersion(int chatId) {
        String desc = getDescription(chatId);
        if (desc == null || !desc.startsWith("{")) return false;
        try {
            JSONObject json = new JSONObject(desc);
            return json.optInt(KEY_VERSION, 1) > WlChatMarker.SCHEMA_VERSION;
        } catch (JSONException e) {
            return false;
        }
    }

    public List<ForumTopic> getTopics(int chatId) {
        List<ForumTopic> topics = new ArrayList<>();
        String desc = getDescription(chatId);
        if (desc == null || desc.isEmpty()) return topics;
        if (isUnsupportedVersion(chatId)) return topics;

        try {
            JSONObject json = new JSONObject(desc);
            JSONArray arr = json.optJSONArray(KEY_TOPICS);
            if (arr == null) return topics;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.getJSONObject(i);
                topics.add(new ForumTopic(
                    t.getInt(KEY_ID),
                    t.getString(KEY_NAME),
                    t.optString(KEY_EMOJI, "\uD83D\uDCAC"),
                    t.optString(KEY_COLOR, "#2AABEE")
                ));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse forum topics", e);
        }
        return topics;
    }

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
            json.put(KEY_VERSION, WlChatMarker.SCHEMA_VERSION);
            setDescription(chatId, json.toString());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save forum topics", e);
            return false;
        }
    }

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

    public ForumTopic getTopic(int chatId, int topicId) {
        List<ForumTopic> topics = getTopics(chatId);
        for (ForumTopic t : topics) {
            if (t.getId() == topicId) return t;
        }
        return null;
    }

    public ForumTopic enableForum(int chatId) {
        if (isForum(chatId)) {
            List<ForumTopic> topics = getTopics(chatId);
            return topics.isEmpty() ? null : topics.get(0);
        }
        return createTopic(chatId, "General", "\uD83D\uDCAC", "#2AABEE");
    }

    public static int getMessageTopicId(String subject) {
        int id = ForumTopic.extractTopicId(subject);
        return id > 0 ? id : ForumTopic.DEFAULT_TOPIC_ID;
    }

    public static String makeTopicSubject(int topicId) {
        return ForumTopic.TOPIC_PREFIX + topicId;
    }
}
