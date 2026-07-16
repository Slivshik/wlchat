package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import java.lang.reflect.Field;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

public abstract class BaseActionBarActivity extends AppCompatActivity {

  private static final String TAG = "BaseActionBarActivity";
  protected DynamicTheme dynamicTheme = new DynamicTheme();
  private boolean skipDefaultExitTransition = false;

  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    onPreCreate();
    super.onCreate(savedInstanceState);

    // Apply smooth activity transitions
    overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);

    // Only enable Edge-to-Edge if it is well supported
    if (ViewUtil.isEdgeToEdgeSupported()) {
      // docs says to use: WindowCompat.enableEdgeToEdge(getWindow());
      // but it actually makes things worse, the next takes care of setting the 3-buttons navigation
      // bar background
      EdgeToEdge.enable(this);

      // force white text in status bar so it visible over background color
      WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
          .setAppearanceLightStatusBars(false);
    }
  }

  @Override
  protected void onPostCreate(@Nullable Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    // Apply adjustments to the toolbar for edge-to-edge display
    ViewUtil.adjustToolbarForE2E(this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    initializeScreenshotSecurity();
    dynamicTheme.onResume(this);
    // dynamicTheme.onResume() may finish() + restart this activity with its own
    // no-op transition (theme change); don't clobber that with the default exit one.
    if (isFinishing()) {
      skipDefaultExitTransition();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    // Mirror the slide-in-from-right / fade-out entrance with a matching
    // slide-out-to-right / fade-back-in exit, so screens that don't define
    // their own closing transition still animate symmetrically.
    if (isFinishing() && !skipDefaultExitTransition) {
      overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right);
    }
  }

  /**
   * Opts this activity instance out of the default slide-out exit transition, for callers that
   * finish with their own explicit transition (e.g. an intentional no-op transition to make a
   * follow-up activity feel like a continuation of this one).
   */
  protected void skipDefaultExitTransition() {
    skipDefaultExitTransition = true;
  }

  private void initializeScreenshotSecurity() {
    if (Prefs.isScreenSecurityEnabled(this)) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }

  /** Modified from: http://stackoverflow.com/a/13098824 */
  private void forceOverflowMenu() {
    try {
      ViewConfiguration config = ViewConfiguration.get(this);
      Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
      if (menuKeyField != null) {
        menuKeyField.setAccessible(true);
        menuKeyField.setBoolean(config, false);
      }
    } catch (IllegalAccessException e) {
      Log.w(TAG, "Failed to force overflow menu.");
    } catch (NoSuchFieldException e) {
      Log.w(TAG, "Failed to force overflow menu.");
    }
  }

  public void makeSearchMenuVisible(final Menu menu, final MenuItem searchItem) {
    for (int i = 0; i < menu.size(); ++i) {
      MenuItem item = menu.getItem(i);
      int id = item.getItemId();
      if (id == R.id.menu_search_up || id == R.id.menu_search_down) {
        item.setVisible(true);
      } else if (item != searchItem) {
        item.setVisible(false); // hide all other items
      }
    }
  }

  protected <T extends Fragment> T initFragment(@IdRes int target, @NonNull T fragment) {
    return initFragment(target, fragment, null);
  }

  protected <T extends Fragment> T initFragment(
      @IdRes int target, @NonNull T fragment, @Nullable Bundle extras) {
    Bundle args = new Bundle();

    if (extras != null) {
      args.putAll(extras);
    }

    fragment.setArguments(args);
    getSupportFragmentManager()
        .beginTransaction()
        .replace(target, fragment)
        .commitAllowingStateLoss();
    return fragment;
  }
}
