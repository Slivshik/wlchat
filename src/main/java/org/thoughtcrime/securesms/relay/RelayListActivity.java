package org.thoughtcrime.securesms.relay;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;
import chat.delta.rpc.types.TransportListEntry;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEvent;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.List;
import org.thoughtcrime.securesms.BaseActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.connect.DcEventCenter;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.qr.QrActivity;
import org.thoughtcrime.securesms.qr.QrCodeHandler;
import org.thoughtcrime.securesms.util.ScreenLockUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

public class RelayListActivity extends BaseActionBarActivity
    implements RelayListAdapter.OnRelayClickListener, DcEventCenter.DcEventDelegate {

  private static final String TAG = "RelayListActivity";
  public static final String EXTRA_QR_DATA = "qr_data";

  private RelayListAdapter adapter;
  private Rpc rpc;
  private int accId;

  private String qrData = null;

  private ActivityResultLauncher<Intent> screenLockLauncher;
  private ActivityResultLauncher<Intent> qrScannerLauncher;

  private TransportListEntry contextMenuRelay = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_relay_list);

    qrScannerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                IntentResult scanResult =
                    IntentIntegrator.parseActivityResult(result.getResultCode(), result.getData());
                new QrCodeHandler(this).handleOnlyAddRelayQr(scanResult.getContents(), null);
              }
            });
    screenLockLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() != RESULT_OK) {
                finish();
                return;
              }
              if (qrData != null) {
                new QrCodeHandler(this).handleOnlyAddRelayQr(qrData, null);
                qrData = null;
              }
            });

    rpc = DcHelper.getRpc(this);
    accId = DcHelper.getContext(this).getAccountId();

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.email_relays);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    RecyclerView recyclerView = findViewById(R.id.relay_list);
    PulsingFloatingActionButton fabAdd = findViewById(R.id.fab_add_relay);

    ScrollView scrollView = findViewById(R.id.relay_scroll_view);
    ViewUtil.applyWindowInsets(scrollView);
    ViewUtil.applyWindowInsetsAsMargin(fabAdd);

    qrData = getIntent().getStringExtra(EXTRA_QR_DATA);
    if (qrData != null) {
      boolean result =
          ScreenLockUtil.applyScreenLock(
              this,
              getString(R.string.add_transport),
              getString(R.string.enter_system_secret_to_continue),
              screenLockLauncher);
      if (!result) {
        new QrCodeHandler(this).handleOnlyAddRelayQr(qrData, null);
      }
    }

    fabAdd.setOnClickListener(
        v -> {
          // Show options: scan QR or add manually
          new AlertDialog.Builder(this)
              .setTitle(R.string.relay_add_quick)
              .setItems(
                  new CharSequence[]{
                      getString(R.string.scan_qr_code),
                      getString(R.string.manual_account_setup_option),
                      getString(R.string.cancel)
                  },
                  (dialog, which) -> {
                    switch (which) {
                      case 0: // Scan QR
                        Intent intent =
                            new IntentIntegrator(this)
                                .setCaptureActivity(QrActivity.class)
                                .addExtra(QrActivity.EXTRA_SCAN_RELAY, true)
                                .createScanIntent();
                        qrScannerLauncher.launch(intent);
                        break;
                      case 1: // Add manually
                        Intent editIntent = new Intent(this, EditRelayActivity.class);
                        startActivity(editIntent);
                        break;
                    }
                  })
              .show();
        });

    LinearLayoutManager layoutManager = new LinearLayoutManager(this);
    DividerItemDecoration divider =
        new DividerItemDecoration(recyclerView.getContext(), layoutManager.getOrientation());
    recyclerView.addItemDecoration(divider);
    recyclerView.setLayoutManager(layoutManager);

    adapter = new RelayListAdapter(this);
    recyclerView.setAdapter(adapter);

    loadRelays();

    DcEventCenter eventCenter = DcHelper.getEventCenter(this);
    eventCenter.addObserver(DcContext.DC_EVENT_CONFIGURE_PROGRESS, this);
    eventCenter.addObserver(DcContext.DC_EVENT_TRANSPORTS_MODIFIED, this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DcHelper.getEventCenter(this).removeObservers(this);
  }

  private void loadRelays() {
    Util.runOnAnyBackgroundThread(
        () -> {
          String mainRelayAddr = "";
          try {
            mainRelayAddr = rpc.getConfig(accId, DcHelper.CONFIG_CONFIGURED_ADDRESS);
          } catch (RpcException e) {
            Log.e(TAG, "RPC.getConfig() failed", e);
          }
          String finalMainRelayAddr = mainRelayAddr;

          try {
            List<TransportListEntry> relays = rpc.listTransportsEx(accId);
            Util.runOnMain(() -> {
              adapter.setRelays(relays, finalMainRelayAddr);
              updateRelaySummary(relays, finalMainRelayAddr);
            });
          } catch (RpcException e) {
            Log.e(TAG, "RPC.listTransports() failed", e);
            Util.runOnMain(() -> {
              adapter.setRelays(null, finalMainRelayAddr);
              updateRelaySummary(null, finalMainRelayAddr);
            });
          }
        });
  }

  private void updateRelaySummary(List<TransportListEntry> relays, String mainRelayAddr) {
    TextView relayCountText = findViewById(R.id.relay_count_text);
    View relayCountIndicator = findViewById(R.id.relay_count_indicator);

    if (relays == null || relays.isEmpty()) {
      relayCountText.setText("No relays configured");
      relayCountIndicator.setBackgroundResource(R.drawable.status_dot_red);
    } else {
      int count = relays.size();
      int connectedCount = 0;
      for (TransportListEntry relay : relays) {
        if (!relay.isUnpublished) {
          connectedCount++;
        }
      }
      relayCountText.setText(count + " relay" + (count != 1 ? "s" : "") + " configured");
      relayCountIndicator.setBackgroundResource(
          connectedCount > 0 ? R.drawable.status_dot_green : R.drawable.status_dot_red);
    }
  }

  @Override
  public void onRelayClick(TransportListEntry relay) {
    if (relay.param.addr != null && !relay.param.addr.equals(adapter.getMainRelay())) {
      Util.runOnAnyBackgroundThread(
          () -> {
            try {
              rpc.setConfig(accId, DcHelper.CONFIG_CONFIGURED_ADDRESS, relay.param.addr);
              Util.runOnMain(() -> {
                Toast.makeText(this, getString(R.string.relay_switch_sent_from, relay.param.addr), Toast.LENGTH_SHORT).show();
              });
            } catch (RpcException e) {
              Log.e(TAG, "RPC.setConfig() failed", e);
            }
            loadRelays();
          });
    }
  }

  @Override
  public void onRelayLongClick(View view, TransportListEntry relay) {
    contextMenuRelay = relay;
    registerForContextMenu(view);
    openContextMenu(view);
    unregisterForContextMenu(view);
  }

  @Override
  public void onRelayMenuClick(View view, TransportListEntry relay) {
    contextMenuRelay = relay;
    showRelayOptions(relay);
  }

  private void showRelayOptions(TransportListEntry relay) {
    boolean isMain = relay.param.addr != null && relay.param.addr.equals(adapter.getMainRelay());

    String[] options;
    if (isMain) {
      options = new String[]{
          getString(R.string.edit_transport),
          getString(R.string.hide_from_contacts),
          getString(R.string.cancel)
      };
    } else {
      options = new String[]{
          getString(R.string.relay_switch_sent_from),
          getString(R.string.edit_transport),
          getString(R.string.hide_from_contacts),
          getString(R.string.remove_transport),
          getString(R.string.cancel)
      };
    }

    new AlertDialog.Builder(this)
        .setTitle(relay.param.addr)
        .setItems(options, (dialog, which) -> {
          int offset = 0;
          if (isMain) {
            switch (which) {
              case 0: // Edit
                onRelayEdit(relay);
                break;
              case 1: // Hide
                onRelayHide(relay);
                break;
            }
          } else {
            switch (which) {
              case 0: // Set as sending
                onRelayClick(relay);
                break;
              case 1: // Edit
                onRelayEdit(relay);
                break;
              case 2: // Hide
                onRelayHide(relay);
                break;
              case 3: // Remove
                onRelayDelete(relay);
                break;
            }
          }
        })
        .show();
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    getMenuInflater().inflate(R.menu.relay_item_context, menu);

    boolean nonNullAddr = contextMenuRelay != null && contextMenuRelay.param.addr != null;
    boolean isMain = nonNullAddr && contextMenuRelay.param.addr.equals(adapter.getMainRelay());

    Util.redMenuItem(menu, R.id.menu_delete_relay);
    menu.findItem(R.id.menu_delete_relay).setVisible(!isMain);
  }

  @Override
  public void onContextMenuClosed(android.view.Menu menu) {
    super.onContextMenuClosed(menu);
    contextMenuRelay = null;
  }

  @Override
  public boolean onContextItemSelected(@NonNull MenuItem item) {
    if (contextMenuRelay == null) return super.onContextItemSelected(item);

    int itemId = item.getItemId();
    if (itemId == R.id.menu_edit_relay) {
      onRelayEdit(contextMenuRelay);
      contextMenuRelay = null;
      return true;
    } else if (itemId == R.id.menu_delete_relay) {
      onRelayDelete(contextMenuRelay);
      contextMenuRelay = null;
      return true;
    }

    return super.onContextItemSelected(item);
  }

  private void onRelayEdit(TransportListEntry relay) {
    Intent intent = new Intent(this, EditRelayActivity.class);
    intent.putExtra(EditRelayActivity.EXTRA_ADDR, relay.param.addr);
    startActivity(intent);
  }

  private void onRelayHide(TransportListEntry relay) {
    try {
      rpc.setTransportUnpublished(accId, relay.param.addr, true);
      loadRelays();
      Toast.makeText(this, getString(R.string.relay_hidden_from_contacts), Toast.LENGTH_SHORT).show();
    } catch (RpcException e) {
      Log.e(TAG, "cannot unpublish relay: ", e);
    }
  }

  private void onRelayDelete(TransportListEntry relay) {
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(R.string.remove_transport)
            .setMessage(getString(R.string.confirm_remove_or_hide_transport_x, relay.param.addr))
            .setPositiveButton(
                R.string.remove_transport,
                (d, which) -> {
                  try {
                    rpc.deleteTransport(accId, relay.param.addr);
                    loadRelays();
                  } catch (RpcException e) {
                    Log.e(TAG, "RPC.deleteTransport() failed", e);
                  }
                })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(
                R.string.hide_from_contacts,
                (d, which) -> {
                  try {
                    rpc.setTransportUnpublished(accId, relay.param.addr, true);
                    loadRelays();
                  } catch (RpcException e) {
                    Log.e(TAG, "cannot unpublish relay: ", e);
                  }
                })
            .show();
    Util.redPositiveButton(dialog);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void handleEvent(@NonNull DcEvent event) {
    int eventId = event.getId();
    if (eventId == DcContext.DC_EVENT_CONFIGURE_PROGRESS) {
      if (event.getData1Int() == 1000) loadRelays();
    } else if (eventId == DcContext.DC_EVENT_TRANSPORTS_MODIFIED) {
      loadRelays();
    }
  }
}
