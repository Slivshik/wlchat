package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

/**
 * Whether a broadcast channel's join QR/link is currently withdrawn ("private") or shareable
 * ("public", the default). Delta Chat's protocol has no such concept - a valid join QR always
 * lets anyone who has it join instantly - so "private" here means the owner has withdrawn the
 * link (old copies of it stop working, see DcContext.setConfigFromQr in ProfileFragment) rather
 * than a request/approval system, which isn't possible without core support. This is local-only
 * bookkeeping so the UI can show the right label; it is not synced anywhere.
 */
public final class ChannelPrivacyPrefs {
  private static final String KEY_PREFIX = "channel_private_";

  private ChannelPrivacyPrefs() {}

  public static boolean isPrivate(Context context, int chatId) {
    return prefs(context).getBoolean(KEY_PREFIX + chatId, false);
  }

  public static void setPrivate(Context context, int chatId, boolean isPrivate) {
    prefs(context).edit().putBoolean(KEY_PREFIX + chatId, isPrivate).apply();
  }

  private static SharedPreferences prefs(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context);
  }
}
