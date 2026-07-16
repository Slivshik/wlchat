package com.b44t.messenger;

import org.thoughtcrime.securesms.util.Util;

public class DcChat {

  public static final int DC_CHAT_TYPE_UNDEFINED = 0;
  public static final int DC_CHAT_TYPE_SINGLE = 100;
  public static final int DC_CHAT_TYPE_GROUP = 120;
  public static final int DC_CHAT_TYPE_MAILINGLIST = 140;
  public static final int DC_CHAT_TYPE_OUT_BROADCAST = 160;
  public static final int DC_CHAT_TYPE_IN_BROADCAST = 165;

  public static final int DC_CHAT_NO_CHAT = 0;
  public static final int DC_CHAT_ID_ARCHIVED_LINK = 6;
  public static final int DC_CHAT_ID_ALLDONE_HINT = 7;
  public static final int DC_CHAT_ID_LAST_SPECIAL = 9;

  public static final int DC_CHAT_VISIBILITY_NORMAL = 0;
  public static final int DC_CHAT_VISIBILITY_ARCHIVED = 1;
  public static final int DC_CHAT_VISIBILITY_PINNED = 2;

  private int accountId;

  public DcChat(int accountId, long chatCPtr) {
    this.accountId = accountId;
    this.chatCPtr = chatCPtr;
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    unrefChatCPtr();
    chatCPtr = 0;
  }

  public int getAccountId() {
    return accountId;
  }

  public native int getId();

  public native int getType();

  public native int getVisibility();

  public native String getName();

  public native String getMailinglistAddr();

  public native String getProfileImage();

  public native int getColor();

  public native boolean isEncrypted();

  public native boolean isUnpromoted();

  public native boolean isSelfTalk();

  public native boolean isDeviceTalk();

  public native boolean canSend();

  public native boolean isSendingLocations();

  public native boolean isMuted();

  public native boolean isContactRequest();

  // aliases and higher-level tools

  public boolean isMultiUser() {
    int type = getType();
    return type != DC_CHAT_TYPE_SINGLE;
  }

  public boolean shallLeaveBeforeDelete(DcContext dcContext) {
    if (isInBroadcast()) {
      final int[] members = dcContext.getChatContacts(getId());
      return Util.contains(members, DcContact.DC_CONTACT_ID_SELF);
    } else if (isMultiUser() && isEncrypted() && canSend() && !isOutBroadcast()) {
      return true;
    }
    return false;
  }

  public boolean isMailingList() {
    return getType() == DC_CHAT_TYPE_MAILINGLIST;
  }

  /**
   * Best-effort "is self the group's owner" check, used to gate group-management UI (editing
   * name/photo, removing members, group-wide settings) to whoever created the group.
   *
   * <p>Delta Chat's protocol has no admin/owner/role concept at all - every member is equal - so
   * this is a client-side-only heuristic: the oldest message in the chat is normally the "member
   * added" system message generated when the group was created, so whoever sent it is treated as
   * the creator. It is not cryptographically guaranteed and can be wrong for old chats whose full
   * history wasn't synced to this device, or if that message was deleted.
   */
  public boolean isOwnedBySelf(DcContext dcContext) {
    if (!canSend()) return false;
    int[] msgIds = dcContext.getChatMsgs(getId(), 0, 0);
    if (msgIds.length == 0) return true;
    DcMsg oldest = dcContext.getMsg(msgIds[0]);
    return oldest.getFromId() == DcContact.DC_CONTACT_ID_SELF;
  }

  public boolean isInBroadcast() {
    return getType() == DC_CHAT_TYPE_IN_BROADCAST;
  }

  public boolean isOutBroadcast() {
    return getType() == DC_CHAT_TYPE_OUT_BROADCAST;
  }

  // working with raw c-data

  private long chatCPtr; // CAVE: the name is referenced in the JNI

  private native void unrefChatCPtr();

  public long getChatCPtr() {
    return chatCPtr;
  }
}
