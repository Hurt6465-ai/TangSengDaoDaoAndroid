package com.chat.uikit.user.service;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chat.base.base.WKBaseModel;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.ICommonListener;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.net.entity.CommonResponse;
import com.chat.base.net.ud.WKUploader;
import com.chat.base.utils.WKTimeUtils;
import com.chat.uikit.enity.BlacklistUser;
import com.chat.uikit.enity.Device;
import com.chat.uikit.enity.FriendOnline;
import com.chat.uikit.enity.MailListEntity;
import com.chat.uikit.enity.OnlineUser;
import com.chat.uikit.enity.OnlineUserAndDevice;
import com.chat.uikit.enity.UserInfo;
import com.chat.uikit.enity.UserQr;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelMember;
import com.xinbida.wukongim.entity.WKChannelType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2020-06-30 12:37
 * 用户
 */
public class UserModel extends WKBaseModel {
    private UserModel() {
    }

    private static class UserModelBinder {
        static final UserModel userModel = new UserModel();
    }

    public static UserModel getInstance() {
        return UserModelBinder.userModel;
    }

    public void updateUserInfo(String key, String value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(UserService.class).updateUserInfo(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null) iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });
    }

    public void updateUserSetting(String key, int value, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(key, value);
        request(createService(UserService.class).setting(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null) iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });
    }

    public void updateUserRemark(String uid, String remark, final ICommonListener iCommonListener) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("uid", uid);
        jsonObject.put("remark", remark);
        request(createService(UserService.class).updateFriendRemark(jsonObject), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null) iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });
    }

    public void deleteUser(String uid, final ICommonListener iCommonListener) {
        request(createService(UserService.class).deleteFriend(uid), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null) iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });
    }

    public void addBlackList(String uid, final ICommonListener iCommonListener) {
        request(createService(UserService.class).addBlackList(uid), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                WKCommonModel.getInstance().getChannel(uid, WKChannelType.PERSONAL, null);
                if (iCommonListener != null) iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });
    }

    public void removeBlackList(String uid, final ICommonListener iCommonListener) {
        request(createService(UserService.class).removeBlackList(uid), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                WKCommonModel.getInstance().getChannel(uid, WKChannelType.PERSONAL, null);
                if (iCommonListener != null) iCommonListener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) iCommonListener.onResult(code, msg);
            }
        });
    }


    public void blacklists(final IBlacklistListener listener) {
        request(createService(UserService.class).blacklists(), new IRequestResultListener<List<BlacklistUser>>() {
            @Override
            public void onSuccess(List<BlacklistUser> result) {
                if (listener != null) listener.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) listener.onResult(code, msg, null);
            }
        });
    }

    public interface IBlacklistListener {
        void onResult(int code, String msg, List<BlacklistUser> list);
    }

    public void sendDestroyCode(final ICommonListener listener) {
        request(createService(UserService.class).sendDestroyCode(), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (listener != null) listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) listener.onResult(code, msg);
            }
        });
    }

    public void destroyAccount(String code, final ICommonListener listener) {
        request(createService(UserService.class).destroyAccount(code), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (listener != null) listener.onResult(result.status, result.msg);
            }

            @Override
            public void onFail(int code, String msg) {
                if (listener != null) listener.onResult(code, msg);
            }
        });
    }


    public void uploadAvatar(String filePath, final IUploadBack iUploadBack) {
        String url = WKApiConfig.baseUrl + "users/" + WKConfig.getInstance().getUid() + "/avatar?uuid=" + WKTimeUtils.getInstance().getCurrentMills();
        WKUploader.getInstance().upload(url, filePath, new WKUploader.IUploadBack() {
            @Override
            public void onSuccess(String url) {
                if (iUploadBack != null) iUploadBack.onResult(HttpResponseCode.success);
            }

            @Override
            public void onError() {
                if (iUploadBack != null) iUploadBack.onResult(HttpResponseCode.error);
            }
        });
    }

    public interface IUploadBack {
        void onResult(int code);
    }

    public void getOnlineUsers(List<String> uids, @NonNull final IOnlineUser iOnlineUser) {
        JSONArray jsonArray = new JSONArray();
        if (uids != null) {
            jsonArray.addAll(uids);
        }
        request(createService(UserService.class).getOnlineUsers(jsonArray), new IRequestResultListener<List<OnlineUser>>() {
            @Override
            public void onSuccess(List<OnlineUser> result) {
                iOnlineUser.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                iOnlineUser.onResult(code, msg, null);
            }
        });
    }

    public interface IOnlineUser {
        void onResult(int code, String msg, List<OnlineUser> list);
    }

    public void getOnlineUsers() {
        request(createService(UserService.class).onlineUsers(), new IRequestResultListener<OnlineUserAndDevice>() {
            @Override
            public void onSuccess(OnlineUserAndDevice result) {
                if (result == null) {
                    return;
                }

                int pcOnline = 0;
                int muteOfApp = 0;
                if (result.pc != null) {
                    pcOnline = result.pc.online;
                    muteOfApp = result.pc.mute_of_app;
                }
                String currentUid = WKConfig.getInstance().getUid();
                WKSharedPreferencesUtil.getInstance().putInt(currentUid + "_pc_online", pcOnline);
                WKSharedPreferencesUtil.getInstance().putInt(currentUid + "_mute_of_app", muteOfApp);

                Map<String, FriendOnline> remoteByUid = new HashMap<>();
                if (result.friends != null) {
                    for (FriendOnline friend : result.friends) {
                        if (friend == null || TextUtils.isEmpty(friend.uid)) {
                            continue;
                        }
                        remoteByUid.put(friend.uid, friend);
                    }
                }

                List<WKChannel> localChannels = WKIM.getInstance()
                        .getChannelManager()
                        .getWithFollowAndStatus(WKChannelType.PERSONAL, 1, 1);
                List<WKChannel> changedChannels = new ArrayList<>();
                Set<String> handledUids = new HashSet<>();

                if (localChannels != null) {
                    for (WKChannel channel : localChannels) {
                        if (channel == null || TextUtils.isEmpty(channel.channelID)) {
                            continue;
                        }

                        FriendOnline remote = remoteByUid.get(channel.channelID);
                        int newOnline = remote == null ? 0 : remote.online;
                        long newLastOffline = remote == null ? channel.lastOffline : remote.last_offline;

                        if (channel.online != newOnline
                                || (remote != null && channel.lastOffline != newLastOffline)) {
                            channel.online = newOnline;
                            if (remote != null) {
                                channel.lastOffline = newLastOffline;
                            }
                            changedChannels.add(channel);
                        }
                        handledUids.add(channel.channelID);
                    }
                }

                for (Map.Entry<String, FriendOnline> entry : remoteByUid.entrySet()) {
                    if (handledUids.contains(entry.getKey())) {
                        continue;
                    }
                    WKChannel channel = WKIM.getInstance()
                            .getChannelManager()
                            .getChannel(entry.getKey(), WKChannelType.PERSONAL);
                    if (channel == null) {
                        continue;
                    }

                    FriendOnline remote = entry.getValue();
                    if (channel.online != remote.online || channel.lastOffline != remote.last_offline) {
                        channel.online = remote.online;
                        channel.lastOffline = remote.last_offline;
                        changedChannels.add(channel);
                    }
                }

                if (!changedChannels.isEmpty()) {
                    WKIM.getInstance().getChannelManager().saveOrUpdateChannels(changedChannels);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                // 网络失败时保留本地状态，避免短暂断网导致所有联系人瞬间离线。
            }
        });
    }


    public void userQr(final IUserQr iUserQr) {
        request(createService(UserService.class).userQr(), new IRequestResultListener<UserQr>() {
            @Override
            public void onSuccess(UserQr result) {
                if (iUserQr != null) iUserQr.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iUserQr != null) iUserQr.onResult(code, msg, null);
            }
        });
    }

    public interface IUserQr {
        void onResult(int code, String msg, UserQr userQr);
    }

    public void uploadContacts(List<MailListEntity> list, final ICommonListener iCommonListener) {
        JSONArray jsonArray = new JSONArray();
        if (list != null) {
            for (MailListEntity entity : list) {
                if (entity == null) {
                    continue;
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", entity.name);
                jsonObject.put("zone", entity.zone);
                jsonObject.put("phone", entity.phone);
                jsonArray.add(jsonObject);
            }
        }
        request(createService(UserService.class).uploadContacts(jsonArray), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null) {
                    iCommonListener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) {
                    iCommonListener.onResult(code, msg);
                }
            }
        });
    }

    public void getContacts(final IGetContacts iGetContacts) {
        request(createService(UserService.class).getContacts(), new IRequestResultListener<List<MailListEntity>>() {
            @Override
            public void onSuccess(List<MailListEntity> result) {
                if (iGetContacts != null) iGetContacts.onResult(HttpResponseCode.success, "", result);
            }

            @Override
            public void onFail(int code, String msg) {
                if (iGetContacts != null) iGetContacts.onResult(code, msg, null);
            }
        });
    }

    public interface IGetContacts {
        void onResult(int code, String msg, List<MailListEntity> list);
    }

    public interface IUserInfo {
        void onResult(int code, String msg, UserInfo userInfo);
    }

    public void getUserInfo(String uid, String groupNo, IUserInfo iUserInfo) {
        request(createService(UserService.class).getUserInfo(uid, groupNo), new IRequestResultListener<>() {
            @Override
            public void onSuccess(UserInfo result) {
                if (result != null && result.group_member != null) {
                    WKChannelMember member = new WKChannelMember();
                    member.memberUID = result.group_member.uid;
                    member.memberRemark = result.group_member.remark;
                    member.memberName = result.group_member.name;
                    member.channelID = result.group_member.group_no;
                    member.channelType = WKChannelType.GROUP;
                    member.isDeleted = result.group_member.is_deleted;
                    member.version = result.group_member.version;
                    member.role = result.group_member.role;
                    member.status = result.group_member.status;
                    member.memberInviteUID = result.group_member.invite_uid;
                    member.robot = result.group_member.robot;
                    member.forbiddenExpirationTime = result.group_member.forbidden_expir_time;
                    if (member.robot == 1 && !TextUtils.isEmpty(result.group_member.username)) {
                        member.memberName = result.group_member.username;
                    }
                    member.updatedAt = result.group_member.updated_at;
                    member.createdAt = result.group_member.created_at;
                    WKIM.getInstance().getChannelMembersManager().save(member);
                }
                if (iUserInfo != null) {
                    iUserInfo.onResult(HttpResponseCode.success, "", result);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iUserInfo != null) {
                    iUserInfo.onResult(code, msg, null);
                }
            }
        });
    }

    public void quit(ICommonListener iCommonListener) {
        request(createService(UserService.class).quit(), new IRequestResultListener<CommonResponse>() {
            @Override
            public void onSuccess(CommonResponse result) {
                if (iCommonListener != null) {
                    iCommonListener.onResult(HttpResponseCode.success, "");
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iCommonListener != null) {
                    iCommonListener.onResult(code, msg);
                }
            }
        });
    }

    public void device() {
        String deviceId = WKConstants.getDeviceID();
        request(createService(UserService.class).device(deviceId), new IRequestResultListener<>() {
            @Override
            public void onSuccess(Device result) {
                if (result != null) {
                    WKIM.getInstance().setDeviceId(String.valueOf(result.id));
                }
            }

            @Override
            public void onFail(int code, String msg) {
            }
        });
    }
}
