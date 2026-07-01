package org.thoughtcrime.securesms.relay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import chat.delta.rpc.types.TransportListEntry;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.R;

public class RelayListAdapter extends RecyclerView.Adapter<RelayListAdapter.RelayViewHolder> {

  private List<TransportListEntry> relays = new ArrayList<>();
  private final OnRelayClickListener listener;
  private String mainRelayAddr;

  public interface OnRelayClickListener {
    void onRelayClick(TransportListEntry relay);

    void onRelayLongClick(View view, TransportListEntry relay);

    void onRelayMenuClick(View view, TransportListEntry relay);
  }

  public RelayListAdapter(OnRelayClickListener listener) {
    this.listener = listener;
  }

  public String getMainRelay() {
    return mainRelayAddr;
  }

  public void setRelays(@Nullable List<TransportListEntry> relays, String mainRelayAddr) {
    this.relays = relays != null ? relays : new ArrayList<>();
    this.mainRelayAddr = mainRelayAddr;
    notifyDataSetChanged();
  }

  @NonNull
  @Override
  public RelayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.relay_list_item, parent, false);
    return new RelayViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull RelayViewHolder holder, int position) {
    TransportListEntry relay = relays.get(position);
    boolean isMain = relay.param.addr != null && relay.param.addr.equals(mainRelayAddr);
    holder.bind(relay, isMain, listener);
  }

  @Override
  public int getItemCount() {
    return relays.size();
  }

  public static class RelayViewHolder extends RecyclerView.ViewHolder {
    private final TextView emailText;
    private final TextView statusText;
    private final TextView serverInfoText;
    private final TextView labelsText;
    private final ImageView mainIndicator;
    private final View statusDot;
    private final ImageView moreOptions;

    public RelayViewHolder(@NonNull View itemView) {
      super(itemView);
      emailText = itemView.findViewById(R.id.email_text);
      statusText = itemView.findViewById(R.id.status_text);
      serverInfoText = itemView.findViewById(R.id.server_info_text);
      labelsText = itemView.findViewById(R.id.labels_text);
      mainIndicator = itemView.findViewById(R.id.main_indicator);
      statusDot = itemView.findViewById(R.id.status_dot);
      moreOptions = itemView.findViewById(R.id.more_options);
    }

    public void bind(TransportListEntry relay, boolean isMain, OnRelayClickListener listener) {
      Context context = itemView.getContext();

      // Email address
      emailText.setText(relay.param.addr);

      // Status indicator
      updateStatusIndicator(relay, isMain);

      // Server info
      updateServerInfo(relay);

      // Labels
      updateLabels(relay, isMain, context);

      // Main indicator
      mainIndicator.setVisibility(isMain ? View.VISIBLE : View.INVISIBLE);

      // Status dot color
      updateStatusDot(relay, context);

      // More options button
      moreOptions.setVisibility(View.VISIBLE);
      moreOptions.setOnClickListener(v -> {
        if (listener != null) {
          listener.onRelayMenuClick(v, relay);
        }
      });

      itemView.setOnClickListener(v -> {
        if (listener != null) {
          listener.onRelayClick(relay);
        }
      });

      itemView.setOnLongClickListener(v -> {
        if (listener != null) {
          listener.onRelayLongClick(v, relay);
        }
        return true;
      });
    }

    private void updateStatusIndicator(TransportListEntry relay, boolean isMain) {
      if (isMain) {
        statusText.setText(R.string.relay_status_connected);
        statusText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_500));
      } else if (relay.isUnpublished) {
        statusText.setText(R.string.relay_status_disconnected);
        statusText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_500));
      } else {
        statusText.setText(R.string.relay_status_connected);
        statusText.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_500));
      }
    }

    private void updateServerInfo(TransportListEntry relay) {
      String serverInfo = "";
      if (relay.param.imapServer != null && !relay.param.imapServer.isEmpty()) {
        serverInfo = relay.param.imapServer;
        if (relay.param.imapPort != null) {
          serverInfo += ":" + relay.param.imapPort;
        }
      } else if (relay.param.smtpServer != null && !relay.param.smtpServer.isEmpty()) {
        serverInfo = relay.param.smtpServer;
        if (relay.param.smtpPort != null) {
          serverInfo += ":" + relay.param.smtpPort;
        }
      }
      serverInfoText.setText(serverInfo);
      serverInfoText.setVisibility(serverInfo.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateLabels(TransportListEntry relay, boolean isMain, Context context) {
      List<String> labels = new ArrayList<>();
      if (isMain) {
        labels.add(context.getString(R.string.relay_used_for_sending));
      }
      if (relay.isUnpublished) {
        labels.add(context.getString(R.string.relay_hidden_from_contacts));
      }
      // Check if this is a managed provider
      if (relay.param.addr != null && isManagedProvider(relay.param.addr)) {
        labels.add(context.getString(R.string.managed_provider_auto_configured, getProviderDomain(relay.param.addr)));
      }

      if (!labels.isEmpty()) {
        labelsText.setText(String.join(" · ", labels));
        labelsText.setVisibility(View.VISIBLE);
      } else {
        labelsText.setVisibility(View.GONE);
      }
    }

    private boolean isManagedProvider(String email) {
      if (email == null || !email.contains("@")) return false;
      String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
      // Check for known managed providers
      return domain.equals("wlrus.lol");
    }

    private String getProviderDomain(String email) {
      if (email == null || !email.contains("@")) return "";
      return email.substring(email.indexOf("@") + 1);
    }

    private void updateStatusDot(TransportListEntry relay, Context context) {
      if (relay.isUnpublished) {
        statusDot.setBackgroundResource(R.drawable.status_dot_red);
        statusDot.setVisibility(View.VISIBLE);
      } else {
        statusDot.setBackgroundResource(R.drawable.status_dot_green);
        statusDot.setVisibility(View.VISIBLE);
      }
    }
  }
}
