package org.thoughtcrime.securesms.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import com.b44t.messenger.DcContext;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.BlockedContactsActivity;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.Util;

public class PrivacyPreferenceFragment extends ListSummaryPreferenceFragment {
  private static final String TAG = PrivacyPreferenceFragment.class.getSimpleName();
  private static final String PREF_AUTODEL_SERVER = "autodel_server";
  private static final String PREF_AUTODEL_MEDIA = "autodel_media";
  private static final String MANAGED_PROVIDER_DOMAIN = "wlrus.lol";
  // "delete_server_after" core config, in seconds (0 = never). The "1 = at once" sentinel
  // documented for this config does not stick on this core build (confirmed: toggling it on,
  // leaving and reopening this screen shows it reverted to off), so use the shortest interval
  // that's already proven to persist here - the same value "autodel_device" offers as its
  // "1 hour" option. Only ever deletes the server-side copy - the local copy on this device
  // is never touched by this config.
  private static final int DELETE_SERVER_AFTER_SOON = 3600;

  private CheckBoxPreference readReceiptsCheckbox;
  private CheckBoxPreference deleteSentCheckbox;

  private ListPreference autoDelDevice;
  private ListPreference autoDelServer;
  private ListPreference autoDelMedia;
  private PreferenceCategory managedStorageCategory;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    readReceiptsCheckbox = (CheckBoxPreference) this.findPreference("pref_read_receipts");
    readReceiptsCheckbox.setOnPreferenceChangeListener(new ReadReceiptToggleListener());

    deleteSentCheckbox = (CheckBoxPreference) this.findPreference("pref_delete_sent");
    deleteSentCheckbox.setOnPreferenceChangeListener(new DeleteSentToggleListener());
    // this is a WL Chat-specific feature; other builds of this codebase (Arcane Chat etc.)
    // keep using the plain, protocol-standard delete-server-after behavior instead.
    deleteSentCheckbox.setVisible(isWlChatBuild());

    this.findPreference("preference_category_blocked")
        .setOnPreferenceClickListener(new BlockedContactsClickListener());

    autoDelDevice = findPreference("autodel_device");
    autoDelDevice.setOnPreferenceChangeListener(new AutodelChangeListener());

    autoDelServer = findPreference(PREF_AUTODEL_SERVER);
    autoDelServer.setOnPreferenceChangeListener(new AutodelServerListener());

    autoDelMedia = findPreference(PREF_AUTODEL_MEDIA);
    autoDelMedia.setOnPreferenceChangeListener(new AutodelMediaListener());

    managedStorageCategory = findPreference("managed_storage_category");

    Preference screenSecurity = this.findPreference(Prefs.SCREEN_SECURITY_PREF);
    screenSecurity.setOnPreferenceChangeListener(new ScreenShotSecurityListener());
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_privacy);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity())
        .getSupportActionBar()
        .setTitle(R.string.pref_privacy);

    readReceiptsCheckbox.setChecked(0 != dcContext.getConfigInt("mdns_enabled"));
    deleteSentCheckbox.setChecked(
        dcContext.getConfigInt("delete_server_after") == DELETE_SERVER_AFTER_SOON);
    initAutodelFromCore();
    initManagedStorageVisibility();
    initAutodelServerFromCore();
    initAutodelMediaFromPrefs();
  }

  private void initAutodelFromCore() {
    String value = Integer.toString(dcContext.getConfigInt("delete_device_after"));
    autoDelDevice.setValue(value);
    updateListSummary(autoDelDevice, value);
  }

  private void initAutodelServerFromCore() {
    String value = Integer.toString(dcContext.getConfigInt("delete_server_after"));
    autoDelServer.setValue(value);
    updateListSummary(autoDelServer, value);
  }

  private void initAutodelMediaFromPrefs() {
    SharedPreferences prefs = requireContext().getSharedPreferences("autodel_media_prefs", Context.MODE_PRIVATE);
    String value = Integer.toString(prefs.getInt(PREF_AUTODEL_MEDIA, 0));
    autoDelMedia.setValue(value);
    updateListSummary(autoDelMedia, value);
  }

  private void initManagedStorageVisibility() {
    boolean isManagedProvider = isManagedProviderAccount();
    managedStorageCategory.setVisible(isManagedProvider);
  }

  private static boolean isWlChatBuild() {
    return BuildConfig.APPLICATION_ID.startsWith("chat.wl.");
  }

  private boolean isManagedProviderAccount() {
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

  public static CharSequence getSummary(Context context) {
    DcContext dcContext = DcHelper.getContext(context);
    final String onRes = context.getString(R.string.on);
    final String offRes = context.getString(R.string.off);
    String readReceiptState = dcContext.getConfigInt("mdns_enabled") != 0 ? onRes : offRes;
    return context.getString(R.string.pref_read_receipts) + " " + readReceiptState;
  }

  /** Check if the current account is a managed provider account. */
  public static boolean isManagedProvider(Context context) {
    DcContext dcContext = DcHelper.getContext(context);
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

  private class BlockedContactsClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(getActivity(), BlockedContactsActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class ReadReceiptToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (boolean) newValue;
      dcContext.setConfigInt("mdns_enabled", enabled ? 1 : 0);
      return true;
    }
  }

  private class AutodelChangeListener implements Preference.OnPreferenceChangeListener {
    private final String coreKey = "delete_device_after";

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      int timeout = Util.objectToInt(newValue);
      Context context = preference.getContext();
      if (timeout > 0) {
        int delCount = DcHelper.getContext(context).estimateDeletionCount(false, timeout);

        View gl = View.inflate(getActivity(), R.layout.dialog_with_checkbox, null);
        CheckBox confirmCheckbox = gl.findViewById(R.id.dialog_checkbox);
        TextView msg = gl.findViewById(R.id.dialog_message);

        msg.setText(
            String.format(
                context.getString(R.string.autodel_device_ask),
                delCount,
                getSelectedSummary(preference, newValue)));
        confirmCheckbox.setText(R.string.autodel_confirm);

        new AlertDialog.Builder(context)
            .setTitle(preference.getTitle())
            .setView(gl)
            .setPositiveButton(
                android.R.string.ok,
                (dialog, whichButton) -> {
                  if (confirmCheckbox.isChecked()) {
                    dcContext.setConfigInt(coreKey, timeout);
                    initAutodelFromCore();
                  } else {
                    onPreferenceChange(preference, newValue);
                  }
                })
            .setNegativeButton(
                android.R.string.cancel, (dialog, whichButton) -> initAutodelFromCore())
            .setCancelable(true)
            .setOnCancelListener(dialog -> initAutodelFromCore())
            .show();
      } else {
        updateListSummary(preference, newValue);
        dcContext.setConfigInt(coreKey, timeout);
      }
      return true;
    }
  }

  private class AutodelServerListener implements Preference.OnPreferenceChangeListener {
    private final String coreKey = "delete_server_after";

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      int timeout = Util.objectToInt(newValue);
      updateListSummary(preference, newValue);
      dcContext.setConfigInt(coreKey, timeout);

      if (timeout > 0) {
        Toast.makeText(
                getContext(),
                String.format(getString(R.string.autodel_messages_summary)),
                Toast.LENGTH_SHORT)
            .show();
      }
      return true;
    }
  }

  private class AutodelMediaListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      int timeout = Util.objectToInt(newValue);
      updateListSummary(preference, newValue);

      SharedPreferences prefs = requireContext().getSharedPreferences("autodel_media_prefs", Context.MODE_PRIVATE);
      prefs.edit().putInt(PREF_AUTODEL_MEDIA, timeout).apply();

      if (timeout > 0) {
        Toast.makeText(
                getContext(),
                String.format(getString(R.string.autodel_media_summary)),
                Toast.LENGTH_SHORT)
            .show();
      }
      return true;
    }
  }

  private class DeleteSentToggleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (boolean) newValue;
      // "delete_server_after" is the same core config the "Managed storage" section below
      // uses - it only ever removes the IMAP copy, the message stays in this device's local
      // database. Refresh that section too so the two stay in sync if both are visible.
      try {
        dcContext.setConfigInt(
            "delete_server_after", enabled ? DELETE_SERVER_AFTER_SOON : 0);
      } catch (Exception e) {
        Log.e(TAG, "failed to set delete_server_after", e);
      }
      // Belt-and-suspenders: make sure the switch reflects `enabled` even if something above
      // throws, instead of silently refusing to move.
      deleteSentCheckbox.setChecked(enabled);
      initAutodelServerFromCore();
      return true;
    }
  }

  private class ScreenShotSecurityListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (Boolean) newValue;
      Prefs.setScreenSecurityEnabled(getContext(), enabled);
      Toast.makeText(
              getContext(), R.string.pref_screen_security_please_restart_hint, Toast.LENGTH_LONG)
          .show();
      return true;
    }
  }
}
