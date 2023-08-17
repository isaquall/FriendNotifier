package me.isaquall.friendnotifier;

import gearth.extensions.Extension;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HEntityType;
import gearth.extensions.parsers.HFriend;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtensionInfo(
        Title = "FriendNotifier",
        Description = "Know when your friends log on and off!",
        Version = "1.0",
        Author = "isaquall"
)
public class FriendNotifier extends Extension {

    private boolean receivedFragment = false;
    private boolean receivedUserObject = false;
    private boolean muteRoomJoins = false;
    private boolean muteFriendJoins = false;
    private final List<String> alreadyOnlineFriends = new ArrayList<>();
    private final Map<Integer, String> usersInRoom = new HashMap<>();
    private int userId;
    private int userIndex;

    public static void main(String[] args) {
        new FriendNotifier(args).run();
    }

    public FriendNotifier(String[] args) {
        super(args);
    }

    @Override
    public void initExtension() {
        intercept(HMessage.Direction.TOCLIENT, "FriendListFragment", this::onFriendListFragment);
        intercept(HMessage.Direction.TOCLIENT, "FriendListUpdate", this::onFriendListUpdate);
        intercept(HMessage.Direction.TOCLIENT, "Users", this::onUsers);
        intercept(HMessage.Direction.TOCLIENT, "UserRemove", this::onUserRemove);
        intercept(HMessage.Direction.TOCLIENT, "UserObject", this::onUserObject);
        intercept(HMessage.Direction.TOSERVER, "OpenFlatConnection", this::onOpenFlatConnection);
        intercept(HMessage.Direction.TOSERVER, "Chat", this::onChat);
    }

    private void onFriendListFragment(HMessage hmessage) {
        if (receivedFragment) return;
        receivedFragment = true;

        HFriend[] friends = HFriend.parseFromFragment(hmessage.getPacket());
        for (HFriend friend : friends) {
            if (friend.isOnline()) {
                alreadyOnlineFriends.add(friend.getName());
            }
        }
    }

    private void onFriendListUpdate(HMessage hMessage) {
        HFriend[] friends = HFriend.parseFromUpdate(hMessage.getPacket());
        for (HFriend friend : friends) {
            if (friend.isOnline()) {
                if (!alreadyOnlineFriends.contains(friend.getName())) {
                    if (!muteFriendJoins) {
                        sendToClient(new HPacket("{in:NotificationDialog}{s:\"furni_placement_error\"}{i:1}{s:\"message\"}{s:\"" + friend.getName() + " logged on.\"}"));
                    }
                }
            } else {
                if (!muteFriendJoins) {
                    sendToClient(new HPacket("{in:NotificationDialog}{s:\"furni_placement_error\"}{i:1}{s:\"message\"}{s:\"" + friend.getName() + " logged off.\"}"));
                }
                alreadyOnlineFriends.remove(friend.getName());
            }
        }
    }

    private void onUsers(HMessage hMessage) {
        for (HEntity entity : HEntity.parse(hMessage.getPacket())) {
            if (entity.getEntityType() == HEntityType.HABBO) {
                int index = entity.getIndex();
                if (!usersInRoom.containsKey(index)) {
                    if (entity.getId() == userId) {
                        userIndex = index;
                    }

                    usersInRoom.put(index, entity.getName());
                    if (!muteRoomJoins) {
                        sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, userIndex, entity.getName() + " entered the room.", 0, 30, 0, -1));
                    }
                }
            }
        }
    }

    private void onUserRemove(HMessage hMessage) {
        int index = Integer.parseInt(hMessage.getPacket().readString());
        if (!muteRoomJoins) {
            sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, userIndex, usersInRoom.get(index) + " left the room.", 0, 30, 0, -1));
        }
        usersInRoom.remove(index);
    }

    private void onUserObject(HMessage hMessage) {
        if (receivedUserObject) return;
        receivedUserObject = true;
        userId = hMessage.getPacket().readInteger();
    }

    private void onOpenFlatConnection(HMessage hMessage) {
        usersInRoom.clear();
    }

    private void onChat(HMessage hMessage) {
        String message = hMessage.getPacket().readString();
        switch (message) {
            case ":playerlist":
            case ":pl":
                hMessage.setBlocked(true);
                sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, userIndex, usersInRoom.size() + " Habbos in room: " + String.join(", ", usersInRoom.values()) + ".", 0, 30, 0, -1));
                break;
            case ":mutefriendjoins":
            case ":mf":
                hMessage.setBlocked(true);
                muteFriendJoins = !muteFriendJoins;
                if (muteFriendJoins) {
                    sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, userIndex, "Friend joins and leaves have been muted.", 0, 30, 0, -1));
                } else {
                    sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, userIndex, "Friend joins and leaves have been unmuted.", 0, 30, 0, -1));
                }
                break;
            case ":muteroomjoins":
            case ":mr":
                hMessage.setBlocked(true);
                muteRoomJoins = !muteRoomJoins;
                if (muteRoomJoins) {
                    sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, userIndex, "Room joins and leaves have been muted.", 0, 30, 0, -1));
                } else {
                    sendToClient(new HPacket("Whisper", HMessage.Direction.TOCLIENT, userIndex, "Room joins and leaves have been unmuted.", 0, 30, 0, -1));
                }
                break;
        }
    }
}
