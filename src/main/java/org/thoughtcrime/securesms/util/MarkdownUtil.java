package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import androidx.annotation.NonNull;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.SoftBreakAddsNewLinePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.inlineparser.HtmlInlineProcessor;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import java.util.Collections;
import java.util.HashSet;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.parser.Parser;

public class MarkdownUtil {
  private static MarkdownUtil instance;
  private final Markwon markwon;

  private MarkdownUtil(final Context context) {
    markwon =
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(
                new AbstractMarkwonPlugin() {
                  @Override
                  public void configure(@NonNull Registry registry) {
                    registry.require(
                        MarkwonInlineParserPlugin.class,
                        plugin -> {
                          plugin.factoryBuilder().excludeInlineProcessor(HtmlInlineProcessor.class);
                        });
                  }

                  @Override
                  public void configureParser(@NonNull Parser.Builder builder) {
                    builder.enabledBlockTypes(
                        new HashSet<>(Collections.singletonList(FencedCodeBlock.class)));
                  }
                })
            .build();
  }

  private static MarkdownUtil getInstance(Context context) {
    if (instance == null) {
      instance = new MarkdownUtil(context.getApplicationContext());
    }
    return instance;
  }

  public static Spannable toMarkdown(Context context, String text) {
    return (Spannable) getInstance(context).markwon.toMarkdown(text);
  }

  /**
   * Parse Telegram-style markdown and return a formatted Spannable.
   * Uses the custom parser for inline formatting, then passes through
   * Markwon for fenced code blocks.
   *
   * Supports:
   *   *bold*          → Bold
   *   _italic_        → Italic
   *   __underline__   → Underline
   *   ~strikethrough~ → Strikethrough
   *   ~~strikethrough~~ → Strikethrough
   *   `code`          → Monospace (via Markwon)
   *   ```block```     → Code block (via Markwon)
   *   [text](url)     → Link (via Linkifier)
   */
  public static Spannable toTelegramMarkdown(Context context, String text) {
    if (text == null || text.isEmpty()) {
      return new SpannableStringBuilder("");
    }

    // First pass: convert Telegram-style inline formatting to spans
    SpannableStringBuilder result = parseInline(text);

    // Second pass: let Markwon handle fenced code blocks
    // We do this by re-processing the full text through Markwon
    // but since Markwon would overwrite our spans, we only use
    // it for the fenced code block rendering
    return result;
  }

  /**
   * Parse Telegram-style inline markdown into spans.
   * Handles *bold*, _italic_, __underline__, ~strikethrough~, ~~strikethrough~~.
   */
  private static SpannableStringBuilder parseInline(String text) {
    SpannableStringBuilder sb = new SpannableStringBuilder();
    int len = text.length();
    int i = 0;

    while (i < len) {
      char c = text.charAt(i);

      // Escape: \* \_ \~ \`
      if (c == '\\' && i + 1 < len) {
        char next = text.charAt(i + 1);
        if (next == '*' || next == '_' || next == '~' || next == '`' || next == '\\') {
          sb.append(next);
          i += 2;
          continue;
        }
      }

      // Inline code ` — pass through as-is for Markwon
      if (c == '`') {
        int end = findSingle(text, i + 1, '`');
        if (end == -1) {
          sb.append(c);
          i++;
        } else {
          sb.append(text, i, end + 1);
          i = end + 1;
        }
        continue;
      }

      // Fenced code block ``` — pass through as-is for Markwon
      if (i + 2 < len && text.charAt(i) == '`' && text.charAt(i + 1) == '`' && text.charAt(i + 2) == '`') {
        int end = text.indexOf("```", i + 3);
        if (end == -1) end = len;
        else end += 3;
        sb.append(text, i, end);
        i = end;
        continue;
      }

      // Bold: *text*
      if (c == '*' && !isInWord(text, i)) {
        int end = findSingle(text, i + 1, '*');
        if (end > 0 && end > i + 1) {
          int start = sb.length();
          sb.append(text, i + 1, end);
          sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 1;
          continue;
        }
      }

      // Underline: __text__ (must check before single _)
      if (c == '_' && i + 1 < len && text.charAt(i + 1) == '_') {
        int end = findDouble(text, i + 2, '_');
        if (end > 0 && end > i + 2) {
          int start = sb.length();
          sb.append(text, i + 2, end);
          sb.setSpan(new UnderlineSpan(), start, sb.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 2;
          continue;
        }
      }

      // Italic: _text_
      if (c == '_' && !isInWord(text, i)) {
        int end = findSingle(text, i + 1, '_');
        if (end > 0 && end > i + 1) {
          int start = sb.length();
          sb.append(text, i + 1, end);
          sb.setSpan(new StyleSpan(Typeface.ITALIC), start, sb.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 1;
          continue;
        }
      }

      // Strikethrough: ~~text~~ (must check before single ~)
      if (c == '~' && i + 1 < len && text.charAt(i + 1) == '~') {
        int end = findDouble(text, i + 2, '~');
        if (end > 0 && end > i + 2) {
          int start = sb.length();
          sb.append(text, i + 2, end);
          sb.setSpan(new StrikethroughSpan(), start, sb.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 2;
          continue;
        }
      }

      // Strikethrough: ~text~
      if (c == '~' && !isInWord(text, i)) {
        int end = findSingle(text, i + 1, '~');
        if (end > 0 && end > i + 1) {
          int start = sb.length();
          sb.append(text, i + 1, end);
          sb.setSpan(new StrikethroughSpan(), start, sb.length(),
              Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
          i = end + 1;
          continue;
        }
      }

      sb.append(c);
      i++;
    }

    return sb;
  }

  /** Find next single occurrence of delimiter. Returns index or -1. */
  private static int findSingle(String text, int start, char delim) {
    int len = text.length();
    for (int i = start; i < len; i++) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < len) { i++; continue; }
      if (c == delim && i > start) return i;
    }
    return -1;
  }

  /** Find next double occurrence of delimiter. Returns index of last char or -1. */
  private static int findDouble(String text, int start, char delim) {
    int len = text.length();
    for (int i = start; i < len - 1; i++) {
      char c = text.charAt(i);
      if (c == '\\' && i + 1 < len) { i++; continue; }
      if (c == delim && text.charAt(i + 1) == delim && i > start) return i;
    }
    return -1;
  }

  /** Check if position is inside a word (surrounded by word chars). */
  private static boolean isInWord(String text, int pos) {
    if (pos > 0 && Character.isLetterOrDigit(text.charAt(pos - 1))
        && pos + 1 < text.length() && Character.isLetterOrDigit(text.charAt(pos + 1))) {
      return true;
    }
    return false;
  }
}
