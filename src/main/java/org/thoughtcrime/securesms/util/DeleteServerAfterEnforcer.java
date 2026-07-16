package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.b44t.messenger.DcContext;

/**
 * Delta Chat syncs certain configs - including delete_server_after - across a user's own devices
 * via self-addressed encrypted messages. If another device/client logged into the same account
 * has a different value, that sync can silently overwrite whatever was just set here, which is
 * why the setting can appear to "not save": it saves fine locally, but gets reverted again the
 * next time a sync from the other device is processed.
 *
 * <p>There's no way to opt a single config out of that sync, so instead this remembers what THIS
 * device wants ("desired"), independent of whatever the core's live value drifts to, and
 * re-applies it whenever given the chance (app start, settings screen resume). As long as this
 * device is opened at least occasionally, it keeps winning the tug-of-war against the other
 * device's stale value.
 */
public final class DeleteServerAfterEnforcer {
  private static final String KEY_DESIRED = "desired_delete_server_after";
  private static final int NOT_TRACKED = -1;

  private DeleteServerAfterEnforcer() {}

  /** Call whenever the user explicitly picks a delete_server_after value on this device,
   * including 0 (never) - that's a real choice too and should also be defended against drift. */
  public static void setDesired(Context context, int seconds) {
    prefs(context).edit().putInt(KEY_DESIRED, seconds).apply();
  }

  /** Re-applies the desired value to the given account if it has drifted away from it. No-op
   * if nothing has ever been explicitly set on this device. */
  public static void enforce(Context context, DcContext dcContext) {
    int desired = prefs(context).getInt(KEY_DESIRED, NOT_TRACKED);
    if (desired == NOT_TRACKED) return;
    if (dcContext.getConfigInt("delete_server_after") != desired) {
      dcContext.setConfigInt("delete_server_after", desired);
    }
  }

  private static SharedPreferences prefs(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context);
  }
}
