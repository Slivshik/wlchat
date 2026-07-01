package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.DcChat;
import com.b44t.messenger.DcChatlist;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.thoughtcrime.securesms.connect.DcEventCenter;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.qr.QrShowActivity;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import chat.delta.rpc.Rpc;
import chat.delta.rpc.RpcException;

public class ProfileFragment extends Fragment
    implements ProfileAdapter.ItemClickListener, DcEventCenter.DcEventDelegate {

  private static final String TAG = "ProfileFragment";
  public static final String CHAT_ID_EXTRA = "chat_id";
  public static final String CONTACT_ID_EXTRA = "contact_id";

  private ActivityResultLauncher<Intent> pickContactLauncher;
  private ProfileAdapter adapter;
  private ActionMode actionMode;
  private final ActionModeCallback actionModeCallback = new ActionModeCallback();

  private DcContext dcContext;
  protected int chatId;
  private int contactId;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    chatId = getArguments() != null ? getArguments().getInt(CHAT_ID_EXTRA, -1) : -1;
    contactId = getArguments().getInt(CONTACT_ID_EXTRA, -1);
    dcContext = DcHelper.getContext(requireContext());
    pickContactLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              Intent data = result.getData();
              Log.i(
                  TAG,
                  "Received result from activity, resultCode="
                      + result.getResultCode()
                      + ", data="
                      + data);
              if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                List<Integer> selected =
                    data.getIntegerArrayListExtra(ContactMultiSelectionActivity.CONTACTS_EXTRA);
                List<Integer> deselected =
                    data.getIntegerArrayListExtra(
                        ContactMultiSelectionActivity.DESELECTED_CONTACTS_EXTRA);
                Util.runOnAnyBackgroundThread(
                    () -> {
                      if (deselected != null) { // Remove members that were deselected
                        Log.i(TAG, deselected.size() + " members removed");
                        int[] members = dcContext.getChatContacts(chatId);
                        for (int contactId : deselected) {
                          for (int memberId : members) {
                            if (memberId == contactId) {
                              dcContext.removeContactFromChat(chatId, memberId);
                              break;
                            }
                          }
                        }
                      }

                      if (selected != null) { // Add new members
                        Log.i(TAG, selected.size() + " members added");
                        for (Integer contactId : selected) {
                          if (contactId != null) {
                            dcContext.addContactToChat(chatId, contactId);
                          }
                        }
                      }
                    });
              }
            });
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.profile_fragment, container, false);
    adapter = new ProfileAdapter(this, GlideApp.with(this), this);

    RecyclerView list = ViewUtil.findById(view, R.id.recycler_view);

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(list);

    list.setAdapter(adapter);
    list.setLayoutManager(
        new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));

    update();

    DcEventCenter eventCenter = DcHelper.getEventCenter(requireContext());
    eventCenter.addObserver(DcContext.DC_EVENT_CHAT_MODIFIED, this);
    eventCenter.addObserver(DcContext.DC_EVENT_CONTACTS_CHANGED, this);
    eventCenter.addObserver(DcContext.DC_EVENT_MSGS_CHANGED, this);
    eventCenter.addObserver(DcContext.DC_EVENT_INCOMING_MSG, this);
    return view;
  }

  @Override
  public void onDestroyView() {
    DcHelper.getEventCenter(requireContext()).removeObservers(this);
    super.onDestroyView();
  }

  @Override
  public void handleEvent(@NonNull DcEvent event) {
    update();
  }

  private void update() {
    int[] memberList = null;
    DcChatlist sharedChats = null;

    DcChat dcChat = null;
    DcContact dcContact = null;
    if (contactId > 0) {
      dcContact = dcContext.getContact(contactId);
    }
    if (chatId > 0) {
      dcChat = dcContext.getChat(chatId);
    }

    if (dcChat != null && dcChat.isMultiUser()) {
      memberList = dcContext.getChatContacts(chatId);
    } else if (contactId > 0 && contactId != DcContact.DC_CONTACT_ID_SELF) {
      sharedChats = dcContext.getChatlist(0, null, contactId);
    }

    adapter.changeData(memberList, dcContact, sharedChats, dcChat);
  }

  // handle events
  // =========================================================================

  @Override
  public void onSettingsClicked(int settingsId) {
    switch (settingsId) {
      case ProfileAdapter.ITEM_ALL_MEDIA_BUTTON:
        if (chatId > 0) {
          Intent intent = new Intent(getActivity(), AllMediaActivity.class);
          intent.putExtra(AllMediaActivity.CHAT_ID_EXTRA, chatId);
          startActivity(intent);
        }
        break;
      case ProfileAdapter.ITEM_SEND_MESSAGE_BUTTON:
        onSendMessage();
        break;
      case ProfileAdapter.ITEM_INTRODUCED_BY:
        onVerifiedByClicked();
        break;
      case ProfileAdapter.ITEM_GROUP_SETTING_MUTE:
        onMuteChat();
        break;
      case ProfileAdapter.ITEM_GROUP_SETTING_EPHEMERAL:
        onEphemeralMessages();
        break;
      case ProfileAdapter.ITEM_GROUP_SETTING_CHANNEL_TYPE:
        onChannelType();
        break;
      case ProfileAdapter.ITEM_GROUP_SETTING_BANNED:
        onBannedMembers();
        break;
    }
  }

  @Override
  public void onStatusLongClicked(boolean isMultiUser) {
    Context context = requireContext();
    new AlertDialog.Builder(context)
        .setTitle(isMultiUser ? R.string.chat_description : R.string.pref_default_status_label)
        .setItems(
            new CharSequence[] {context.getString(R.string.menu_copy_to_clipboard)},
            (dialogInterface, i) -> {
              Util.writeTextToClipboard(context, adapter.getStatusText());
              Toast.makeText(
                      context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT)
                  .show();
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void onMuteChat() {
    DcChat dcChat = dcContext.getChat(chatId);
    if (dcChat.isMuted()) {
      // Unmute
      try {
        Rpc rpc = DcHelper.getRpc(requireContext());
        chat.delta.rpc.types.MuteDuration duration = new chat.delta.rpc.types.MuteDuration.NotMuted();
        rpc.setChatMuteDuration(dcContext.getAccountId(), chatId, duration);
        Toast.makeText(requireContext(), "Chat unmuted", Toast.LENGTH_SHORT).show();
      } catch (RpcException e) {
        Toast.makeText(requireContext(), "Failed to unmute: " + e.getMessage(), Toast.LENGTH_SHORT).show();
      }
    } else {
      // Show mute options
      new AlertDialog.Builder(requireContext())
          .setTitle(R.string.mute)
          .setItems(
              new CharSequence[]{
                  getString(R.string.mute_for_one_hour),
                  getString(R.string.mute_for_one_day),
                  getString(R.string.mute_forever)
              },
              (dialog, which) -> {
                try {
                  Rpc rpc = DcHelper.getRpc(requireContext());
                  chat.delta.rpc.types.MuteDuration duration;
                  switch (which) {
                    case 0: { // 1 hour
                      chat.delta.rpc.types.MuteDuration.Until u = new chat.delta.rpc.types.MuteDuration.Until();
                      u.duration = 3600;
                      duration = u;
                      break;
                    }
                    case 1: { // 1 day
                      chat.delta.rpc.types.MuteDuration.Until u = new chat.delta.rpc.types.MuteDuration.Until();
                      u.duration = 86400;
                      duration = u;
                      break;
                    }
                    default: // Forever
                      duration = new chat.delta.rpc.types.MuteDuration.Forever();
                      break;
                  }
                  rpc.setChatMuteDuration(dcContext.getAccountId(), chatId, duration);
                  Toast.makeText(requireContext(), "Chat muted", Toast.LENGTH_SHORT).show();
                } catch (RpcException e) {
                  Toast.makeText(requireContext(), "Failed to mute: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
              })
          .setNegativeButton(R.string.cancel, null)
          .show();
    }
  }

  private void onEphemeralMessages() {
    DcChat dcChat = dcContext.getChat(chatId);
    int currentTimer = dcContext.getChatEphemeralTimer(chatId);
    String[] options = {
        getString(R.string.ephemeral_duration_off),
        getString(R.string.ephemeral_duration_1_hour),
        getString(R.string.ephemeral_duration_1_day),
        getString(R.string.ephemeral_duration_1_week)
    };
    int[] timers = {0, 3600, 86400, 604800};

    int checkedItem = 0;
    for (int i = 0; i < timers.length; i++) {
      if (timers[i] == currentTimer) {
        checkedItem = i;
        break;
      }
    }

    new AlertDialog.Builder(requireContext())
        .setTitle(R.string.pref_ephemeral_messages)
        .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
          try {
            Rpc rpc = DcHelper.getRpc(requireContext());
            rpc.setChatEphemeralTimer(dcContext.getAccountId(), chatId, timers[which]);
            Toast.makeText(requireContext(), "Ephemeral messages updated", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
          } catch (RpcException e) {
            Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
          }
        })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void onChannelType() {
    DcChat dcChat = dcContext.getChat(chatId);
    int chatType = dcChat.getType();

    String[] options;
    if (chatType == DcChat.DC_CHAT_TYPE_OUT_BROADCAST || chatType == DcChat.DC_CHAT_TYPE_IN_BROADCAST) {
      options = new String[]{
          getString(R.string.channel_type_public),
          getString(R.string.channel_type_private)
      };
    } else {
      options = new String[]{
          getString(R.string.channel_type_private),
          getString(R.string.channel_type_public)
      };
    }

    new AlertDialog.Builder(requireContext())
        .setTitle(R.string.channel_type)
        .setItems(options, (dialog, which) -> {
          if (which == 0) {
            Toast.makeText(requireContext(), "Current channel type is already active", Toast.LENGTH_SHORT).show();
          } else {
            Toast.makeText(requireContext(), "Channel type can be changed by recreating the group", Toast.LENGTH_LONG).show();
          }
        })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void onBannedMembers() {
    try {
      Rpc rpc = DcHelper.getRpc(requireContext());
      List<chat.delta.rpc.types.Contact> blocked = rpc.getBlockedContacts(dcContext.getAccountId());
      if (blocked.isEmpty()) {
        Toast.makeText(requireContext(), "No banned members", Toast.LENGTH_SHORT).show();
        return;
      }

      String[] names = new String[blocked.size()];
      for (int i = 0; i < blocked.size(); i++) {
        names[i] = blocked.get(i).name + " (" + blocked.get(i).address + ")";
      }

      new AlertDialog.Builder(requireContext())
          .setTitle(R.string.banned_members)
          .setItems(names, (dialog, which) -> {
            chat.delta.rpc.types.Contact contact = blocked.get(which);
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.unban_member)
                .setMessage(getString(R.string.confirm_unban, contact.name))
                .setPositiveButton(R.string.ok, (d, w) -> {
                  try {
                    rpc.unblockContact(dcContext.getAccountId(), contact.id);
                    Toast.makeText(requireContext(), "Member unbanned", Toast.LENGTH_SHORT).show();
                  } catch (RpcException e) {
                    Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                  }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
          })
          .setNegativeButton(R.string.cancel, null)
          .show();
    } catch (RpcException e) {
      Toast.makeText(requireContext(), "Failed to load banned members: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  @Override
  public void onMemberLongClicked(int contactId) {
    if (contactId > DcContact.DC_CONTACT_ID_LAST_SPECIAL
        || contactId == DcContact.DC_CONTACT_ID_SELF) {
      if (contactId == DcContact.DC_CONTACT_ID_SELF) {
        // Can't ban/remove yourself
        return;
      }

      DcChat dcChat = dcContext.getChat(chatId);
      if (!dcChat.canSend()) {
        return;
      }

      DcContact dcContact = dcContext.getContact(contactId);
      String memberName = dcContact.getDisplayName();

      String[] options;
      if (contactId == DcContact.DC_CONTACT_ID_SELF) {
        return;
      } else {
        options = new String[]{
            getString(R.string.remove_member),
            getString(R.string.ban_member),
            getString(R.string.cancel)
        };
      }

      new AlertDialog.Builder(requireContext())
          .setTitle(memberName)
          .setItems(options, (dialog, which) -> {
            if (which == 0) {
              // Remove from group
              new AlertDialog.Builder(requireContext())
                  .setTitle(R.string.remove_member)
                  .setMessage(getString(R.string.confirm_remove, memberName))
                  .setPositiveButton(R.string.ok, (d, w) -> {
                    try {
                      Rpc rpc = DcHelper.getRpc(requireContext());
                      rpc.removeContactFromChat(dcContext.getAccountId(), chatId, contactId);
                      Toast.makeText(requireContext(), "Member removed", Toast.LENGTH_SHORT).show();
                    } catch (RpcException e) {
                      Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                  })
                  .setNegativeButton(R.string.cancel, null)
                  .show();
            } else if (which == 1) {
              // Ban (block + remove)
              new AlertDialog.Builder(requireContext())
                  .setTitle(R.string.ban_member)
                  .setMessage(getString(R.string.confirm_ban, memberName))
                  .setPositiveButton(R.string.ok, (d, w) -> {
                    try {
                      Rpc rpc = DcHelper.getRpc(requireContext());
                      rpc.removeContactFromChat(dcContext.getAccountId(), chatId, contactId);
                      rpc.blockContact(dcContext.getAccountId(), contactId);
                      Toast.makeText(requireContext(), "Member banned", Toast.LENGTH_SHORT).show();
                    } catch (RpcException e) {
                      Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                  })
                  .setNegativeButton(R.string.cancel, null)
                  .show();
            }
          })
          .show();
    }
  }

  @Override
  public void onMemberClicked(int contactId) {
    if (actionMode != null) {
      if (contactId > DcContact.DC_CONTACT_ID_LAST_SPECIAL
          || contactId == DcContact.DC_CONTACT_ID_SELF) {
        adapter.toggleMemberSelection(contactId);
        if (adapter.getSelectedMembersCount() == 0) {
          actionMode.finish();
          actionMode = null;
        } else {
          actionMode.setTitle(String.valueOf(adapter.getSelectedMembersCount()));
        }
      }
    } else if (contactId == DcContact.DC_CONTACT_ID_ADD_MEMBER) {
      onAddMember();
    } else if (contactId == DcContact.DC_CONTACT_ID_QR_INVITE) {
      onQrInvite();
    } else if (contactId > DcContact.DC_CONTACT_ID_LAST_SPECIAL) {
      Intent intent = new Intent(getContext(), ProfileActivity.class);
      intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, contactId);
      startActivity(intent);
    }
  }

  @Override
  public void onAvatarClicked() {
    ProfileActivity activity = (ProfileActivity) getActivity();
    activity.onEnlargeAvatar();
  }

  public void onAddMember() {
    DcChat dcChat = dcContext.getChat(chatId);
    Intent intent = new Intent(getContext(), ContactMultiSelectionActivity.class);
    ArrayList<Integer> preselectedContacts = new ArrayList<>();
    for (int memberId : dcContext.getChatContacts(chatId)) {
      preselectedContacts.add(memberId);
    }
    intent.putExtra(ContactSelectionListFragment.PRESELECTED_CONTACTS, preselectedContacts);
    pickContactLauncher.launch(intent);
  }

  public void onQrInvite() {
    Intent qrIntent = new Intent(getContext(), QrShowActivity.class);
    qrIntent.putExtra(QrShowActivity.CHAT_ID, chatId);
    startActivity(qrIntent);
  }

  @Override
  public void onSharedChatClicked(int chatId) {
    Intent intent = new Intent(getContext(), ConversationActivity.class);
    intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
    requireContext().startActivity(intent);
    requireActivity().finish();
  }

  private void onVerifiedByClicked() {
    DcContact dcContact = dcContext.getContact(contactId);
    int verifierId = dcContact.getVerifierId();
    if (verifierId != 0 && verifierId != DcContact.DC_CONTACT_ID_SELF) {
      Intent intent = new Intent(getContext(), ProfileActivity.class);
      intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, verifierId);
      startActivity(intent);
    }
  }

  private void onSendMessage() {
    DcContact dcContact = dcContext.getContact(contactId);
    int chatId = dcContext.createChatByContactId(dcContact.getId());
    if (chatId != 0) {
      Intent intent = new Intent(getActivity(), ConversationActivity.class);
      intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
      requireActivity().startActivity(intent);
      requireActivity().finish();
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      mode.getMenuInflater().inflate(R.menu.profile_context, menu);
      menu.findItem(R.id.delete).setVisible(true);
      menu.findItem(R.id.details).setVisible(false);
      menu.findItem(R.id.show_in_chat).setVisible(false);
      menu.findItem(R.id.save).setVisible(false);
      menu.findItem(R.id.share).setVisible(false);
      menu.findItem(R.id.menu_resend).setVisible(false);
      menu.findItem(R.id.menu_select_all).setVisible(false);
      mode.setTitle("1");

      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
      return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {
      if (menuItem.getItemId() == R.id.delete) {
        final Collection<Integer> toDelIds = adapter.getSelectedMembers();
        StringBuilder readableToDelList = new StringBuilder();
        for (Integer toDelId : toDelIds) {
          if (readableToDelList.length() > 0) {
            readableToDelList.append(", ");
          }
          readableToDelList.append(dcContext.getContact(toDelId).getDisplayName());
        }
        DcChat dcChat = dcContext.getChat(chatId);
        AlertDialog dialog =
            new AlertDialog.Builder(requireContext())
                .setPositiveButton(
                    R.string.remove_desktop,
                    (d, which) -> {
                      for (Integer toDelId : toDelIds) {
                        dcContext.removeContactFromChat(chatId, toDelId);
                      }
                      mode.finish();
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .setMessage(
                    getString(
                        dcChat.isOutBroadcast()
                            ? R.string.ask_remove_from_channel
                            : R.string.ask_remove_members,
                        readableToDelList))
                .show();
        Util.redPositiveButton(dialog);
        return true;
      }
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      actionMode = null;
      adapter.clearSelection();
    }
  }
}
