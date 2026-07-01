package org.thoughtcrime.securesms.forum;

/**
 * Lightweight forum topic model.
 * Topics are stored compactly in the chat description as JSON.
 * Messages are tagged with topic ID via the subject line.
 *
 * Data format in chat description:
 * {"t":[{"id":1,"n":"General","e":"💬","c":"#2AABEE"},{"id":2,"n":"Offtopic","e":"Random","c":"#4FAE4E"}]}
 *
 * Message tagging: subject line = "topic:1" for topic ID 1
 */
public class ForumTopic {
    public static final String TOPIC_PREFIX = "topic:";
    public static final int DEFAULT_TOPIC_ID = 1;

    private int id;
    private String name;
    private String emoji;
    private String color;

    public ForumTopic(int id, String name, String emoji, String color) {
        this.id = id;
        this.name = name;
        this.emoji = emoji != null ? emoji : "💬";
        this.color = color != null ? color : "#2AABEE";
    }

    public ForumTopic(int id, String name) {
        this(id, name, "💬", "#2AABEE");
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public String getColor() { return color; }

    public void setName(String name) { this.name = name; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public void setColor(String color) { this.color = color; }

    /**
     * Create a message subject tag for this topic.
     * Used with DcMsg.setSubject() to tag messages.
     */
    public String makeSubject() {
        return TOPIC_PREFIX + id;
    }

    /**
     * Extract topic ID from a message subject.
     * Returns -1 if the subject is not a topic tag.
     */
    public static int extractTopicId(String subject) {
        if (subject == null || !subject.startsWith(TOPIC_PREFIX)) return -1;
        try {
            return Integer.parseInt(subject.substring(TOPIC_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForumTopic)) return false;
        return id == ((ForumTopic) o).id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return emoji + " " + name + " (id=" + id + ")";
    }
}
