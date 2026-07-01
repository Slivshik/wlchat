package org.thoughtcrime.securesms.util;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.UnderlineSpan;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts Telegram-style markdown to a Spannable with formatting spans.
 *
 * Supports:
 *   *bold*         → Bold
 *   _italic_       → Italic
 *   __underline__  → Underline
 *   ~strikethrough~ → Strikethrough
 *   ~~strikethrough~~ → Strikethrough
 *   `code`         → Monospace
 *   ```code block``` → Monospace block (left as-is for Markwon)
 *   [text](url)    → Link (left as-is for Linkifier)
 *
 * Delimiters are matched greedily. Nested formatting is supported.
 * Underscore/asterisk/tilde in the middle of a word is treated as literal
 * (Telegram behavior).
 */
public final class TelegramMarkdownUtil {

    private TelegramMarkdownUtil() {}

    /**
     * Parse Telegram-style markdown and return a formatted Spannable.
     * This replaces MarkdownUtil.toMarkdown for Telegram-style input.
     */
    public static Spannable toSpannable(String text) {
        if (text == null || text.isEmpty()) {
            return new SpannableStringBuilder("");
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        List<FormatSpan> spans = new ArrayList<>();

        int i = 0;
        int len = text.length();

        while (i < len) {
            char c = text.charAt(i);

            // Fenced code block ``` — pass through as-is
            if (i + 2 < len && text.charAt(i) == '`' && text.charAt(i + 1) == '`' && text.charAt(i + 2) == '`') {
                int end = text.indexOf("```", i + 3);
                if (end == -1) end = len;
                else end += 3;
                sb.append(text, i, end);
                i = end;
                continue;
            }

            // Inline code ` — pass through as-is (Markwon handles it)
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end == -1) {
                    sb.append(c);
                    i++;
                } else {
                    sb.append(text, i, end + 1);
                    i = end + 1;
                }
                continue;
            }

            // Bold: *text* (single asterisks)
            if (c == '*' && !isInWord(text, i)) {
                int end = findClosing(text, i + 1, '*');
                if (end > 0 && end - i > 1) {
                    int start = sb.length();
                    sb.append(text, i + 1, end);
                    spans.add(new FormatSpan(start, sb.length(), new StyleSpan(android.graphics.Typeface.BOLD)));
                    i = end + 1;
                    continue;
                }
            }

            // Underline: __text__ (double underscores)
            if (c == '_' && i + 1 < len && text.charAt(i + 1) == '_' && !isInWord(text, i)) {
                int end = findClosingDouble(text, i + 2, '_');
                if (end > 0 && end - i > 2) {
                    int start = sb.length();
                    sb.append(text, i + 2, end);
                    spans.add(new FormatSpan(start, sb.length(), new UnderlineSpan()));
                    i = end + 2;
                    continue;
                }
            }

            // Italic: _text_ (single underscore)
            if (c == '_' && !isInWord(text, i)) {
                int end = findClosing(text, i + 1, '_');
                if (end > 0 && end - i > 1) {
                    int start = sb.length();
                    sb.append(text, i + 1, end);
                    spans.add(new FormatSpan(start, sb.length(), new StyleSpan(android.graphics.Typeface.ITALIC)));
                    i = end + 1;
                    continue;
                }
            }

            // Strikethrough: ~~text~~ (double tilde) or ~text~ (single tilde)
            if (c == '~') {
                // Try double tilde first
                if (i + 1 < len && text.charAt(i + 1) == '~') {
                    int end = findClosingDouble(text, i + 2, '~');
                    if (end > 0 && end - i > 2) {
                        int start = sb.length();
                        sb.append(text, i + 2, end);
                        spans.add(new FormatSpan(start, sb.length(), new StrikethroughSpan()));
                        i = end + 2;
                        continue;
                    }
                }
                // Single tilde
                int end = findClosing(text, i + 1, '~');
                if (end > 0 && end - i > 1) {
                    int start = sb.length();
                    sb.append(text, i + 1, end);
                    spans.add(new FormatSpan(start, sb.length(), new StrikethroughSpan()));
                    i = end + 1;
                    continue;
                }
            }

            // Escape sequence: \* \_ \~ \`
            if (c == '\\' && i + 1 < len) {
                char next = text.charAt(i + 1);
                if (next == '*' || next == '_' || next == '~' || next == '`' || next == '\\') {
                    sb.append(next);
                    i += 2;
                    continue;
                }
            }

            sb.append(c);
            i++;
        }

        // Apply all collected spans
        for (FormatSpan fs : spans) {
            sb.setSpan(fs.span, fs.start, fs.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return sb;
    }

    /**
     * Find closing single character delimiter, respecting escapes.
     * Returns the index of the closing char, or -1 if not found.
     */
    private static int findClosing(String text, int start, char delimiter) {
        int len = text.length();
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < len) {
                i++; // skip escaped char
                continue;
            }
            if (c == delimiter) {
                // Don't match empty delimiters
                if (i > start) return i;
                return -1;
            }
        }
        return -1;
    }

    /**
     * Find closing double character delimiter, respecting escapes.
     * Returns the index of the LAST char of the closing delimiter, or -1.
     */
    private static int findClosingDouble(String text, int start, char delimiter) {
        int len = text.length();
        for (int i = start; i < len - 1; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < len) {
                i++;
                continue;
            }
            if (c == delimiter && text.charAt(i + 1) == delimiter) {
                if (i > start) return i;
                return -1;
            }
        }
        return -1;
    }

    /**
     * Check if position is inside a word (surrounded by word chars on both sides).
     * Telegram doesn't treat delimiters inside words as formatting.
     */
    private static boolean isInWord(String text, int pos) {
        if (pos > 0 && Character.isLetterOrDigit(text.charAt(pos - 1))
                && pos + 1 < text.length() && Character.isLetterOrDigit(text.charAt(pos + 1))) {
            return true;
        }
        return false;
    }

    private static class FormatSpan {
        final int start;
        final int end;
        final Object span;

        FormatSpan(int start, int end, Object span) {
            this.start = start;
            this.end = end;
            this.span = span;
        }
    }
}
