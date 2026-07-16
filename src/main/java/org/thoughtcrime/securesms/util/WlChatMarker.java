package org.thoughtcrime.securesms.util;

import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import org.thoughtcrime.securesms.connect.DcHelper;

/**
 * Client-side-only "is this contact using WL Chat" heuristic.
 *
 * <p>Delta Chat's protocol has no field identifying which rebranded client sent a message, so WL
 * Chat quietly tags its own self-status with an invisible marker (zero-width characters, so it
 * never shows up in the status text the user sees/edits) that other WL Chat clients recognize.
 * This only works between WL Chat clients and is not cryptographically guaranteed - any
 * technically inclined user of another Delta-Chat-based client could copy the marker into their
 * own status.
 *
 * <p>The trailing number is a schema version for WL Chat's own custom data formats (e.g. the
 * forum-topics JSON stashed in a chat's description) - see {@link #getSchemaVersion}.
 */
public class WlChatMarker {
  private static final String MARKER_START = "\u200B\u2060WLC";
  private static final String MARKER_END = "\u2060\u200B";

  // Bump when WL-Chat-only data formats (forum topics JSON, etc.) change shape - clients seeing
  // a peer on a newer schema version than they understand can show a fallback placeholder
  // instead of trying to parse data they don't recognize.
  public static final int SCHEMA_VERSION = 1;

  private WlChatMarker() {}

  public static void ensureSelfMarker(DcContext dcContext) {
    String status = dcContext.getConfig(DcHelper.CONFIG_SELF_STATUS);
    if (status == null) status = "";
    String marked = strip(status) + MARKER_START + SCHEMA_VERSION + MARKER_END;
    if (!marked.equals(status)) {
      dcContext.setConfig(DcHelper.CONFIG_SELF_STATUS, marked);
    }
  }

  private static String strip(String status) {
    int start = status.indexOf(MARKER_START);
    if (start < 0) return status;
    int end = status.indexOf(MARKER_END, start);
    if (end < 0) return status.substring(0, start);
    return status.substring(0, start) + status.substring(end + MARKER_END.length());
  }

  public static boolean isWlChatUser(DcContact contact) {
    return contact != null && getSchemaVersion(contact.getStatus()) >= 0;
  }

  public static int getSchemaVersion(DcContact contact) {
    return contact == null ? -1 : getSchemaVersion(contact.getStatus());
  }

  public static int getSchemaVersion(String status) {
    if (status == null) return -1;
    int start = status.indexOf(MARKER_START);
    if (start < 0) return -1;
    int numStart = start + MARKER_START.length();
    int end = status.indexOf(MARKER_END, numStart);
    if (end < 0) return -1;
    try {
      return Integer.parseInt(status.substring(numStart, end).trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
