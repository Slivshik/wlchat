package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.b44t.messenger.DcContext;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.thoughtcrime.securesms.connect.DcHelper;

/**
 * Manages automatic cleanup of old media files for managed provider accounts (@wlrus.lol).
 *
 * Runs on app startup to delete media files older than the configured threshold.
 * This helps stay within the 15 MB per-user storage limit on managed providers.
 */
public class MediaCleanupManager {
  private static final String TAG = "MediaCleanupManager";
  private static final String PREFS_NAME = "autodel_media_prefs";
  private static final String KEY_AUTODEL_MEDIA = "autodel_media";
  private static final String MANAGED_PROVIDER_DOMAIN = "wlrus.lol";

  private static final ExecutorService executor = Executors.newSingleThreadExecutor();
  private static volatile boolean running = false;

  /**
   * Run media cleanup if conditions are met:
   * - Account is a managed provider (@wlrus.lol)
   * - Media auto-delete is enabled (> 0)
   * - Not already running
   */
  public static void runIfneeded(Context context) {
    if (running) return;

    try {
      DcContext dcContext = DcHelper.getContext(context);
      if (!isManagedProvider(dcContext)) return;

      SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
      int mediaDays = prefs.getInt(KEY_AUTODEL_MEDIA, 0);
      if (mediaDays <= 0) return;

      running = true;
      executor.execute(() -> {
        try {
          cleanupOldMedia(context, dcContext, mediaDays);
        } catch (Exception e) {
          Log.w(TAG, "Media cleanup failed", e);
        } finally {
          running = false;
        }
      });
    } catch (Exception e) {
      Log.w(TAG, "Failed to check media cleanup", e);
      running = false;
    }
  }

  /**
   * Force run media cleanup (for manual trigger from settings).
   */
  public static void forceCleanup(Context context, int mediaDays) {
    if (running || mediaDays <= 0) return;

    running = true;
    executor.execute(() -> {
      try {
        DcContext dcContext = DcHelper.getContext(context);
        cleanupOldMedia(context, dcContext, mediaDays);
      } catch (Exception e) {
        Log.w(TAG, "Media cleanup failed", e);
      } finally {
        running = false;
      }
    });
  }

  private static void cleanupOldMedia(Context context, DcContext dcContext, int mediaDays) {
    long cutoffMs = System.currentTimeMillis() - (mediaDays * 24L * 60 * 60 * 1000);
    long deletedBytes = 0;
    int deletedCount = 0;

    // Get the media directory
    String blobDirPath = dcContext.getBlobdir();
    if (blobDirPath == null) return;
    File mediaDir = new File(blobDirPath);
    if (!mediaDir.exists()) return;

    Log.i(TAG, "Starting media cleanup, days=" + mediaDays + ", dir=" + mediaDir.getAbsolutePath());

    // Recursively scan and delete old files
    deletedBytes = scanAndDelete(mediaDir, cutoffMs);
    deletedCount = (int) (deletedBytes > 0 ? deletedBytes / 1024 : 0); // approximate

    if (deletedBytes > 0) {
      Log.i(TAG, "Media cleanup complete: freed ~" + (deletedBytes / 1024) + " KB");
    }
  }

  private static long scanAndDelete(File dir, long cutoffMs) {
    long freedBytes = 0;
    File[] files = dir.listFiles();
    if (files == null) return 0;

    for (File file : files) {
      if (file.isDirectory()) {
        freedBytes += scanAndDelete(file, cutoffMs);
        // Delete empty directories
        if (file.exists() && file.list() != null && file.list().length == 0) {
          file.delete();
        }
      } else {
        if (file.lastModified() < cutoffMs) {
          long size = file.length();
          if (file.delete()) {
            freedBytes += size;
          }
        }
      }
    }
    return freedBytes;
  }

  private static boolean isManagedProvider(DcContext dcContext) {
    try {
      String addr = dcContext.getConfig("addr");
      if (addr != null) {
        addr = addr.toLowerCase();
        return addr.endsWith("@" + MANAGED_PROVIDER_DOMAIN)
            || addr.contains(MANAGED_PROVIDER_DOMAIN);
      }
    } catch (Exception e) {
      // ignore
    }
    return false;
  }
}
