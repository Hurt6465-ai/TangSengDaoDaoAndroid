package com.chat.base.common;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.chat.base.R;
import com.chat.base.WKBaseApplication;
import com.chat.base.base.WKBaseModel;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.config.WKSharedPreferencesUtil;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.entity.AppModule;
import com.chat.base.entity.AppVersion;
import com.chat.base.entity.ChannelInfoEntity;
import com.chat.base.entity.WKAPPConfig;
import com.chat.base.entity.WKChannelState;
import com.chat.base.net.HttpResponseCode;
import com.chat.base.net.IRequestResultListener;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.DispatchQueuePool;
import com.chat.base.utils.WKDeviceUtils;
import com.chat.base.utils.WKReader;
import com.chat.base.utils.WKToastUtils;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelExtras;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 4/21/21 6:23 PM
 */
public class WKCommonModel extends WKBaseModel {
    private final DispatchQueuePool dispatchQueuePool = new DispatchQueuePool(3);

    private WKCommonModel() {
    }

    private static class CommonModelBinder {
        final static WKCommonModel model = new WKCommonModel();
    }

    public static WKCommonModel getInstance() {
        return CommonModelBinder.model;
    }

    public void getAppNewVersion(boolean isShowToast, final IAppNewVersion iAppNewVersion) {
        String v = WKDeviceUtils.getInstance().getVersionName(WKBaseApplication.getInstance().getContext());
        request(createService(WKCommonService.class).getAppNewVersion(v), new IRequestResultListener<AppVersion>() {
            @Override
            public void onSuccess(AppVersion result) {
                if ((result == null || TextUtils.isEmpty(result.download_url)) && isShowToast) {
                    WKToastUtils.getInstance().showToastNormal(WKBaseApplication.getInstance().getContext().getString(R.string.is_new_version));
                } else {
                    iAppNewVersion.onNewVersion(result);
                }
            }

            @Override
            public void onFail(int code, String msg) {

            }
        });
    }

    public interface IAppNewVersion {
        void onNewVersion(AppVersion version);
    }

    public interface IAppConfig {
        void onResult(int code, String msg, WKAPPConfig wkappConfig);
    }

    public void getAppConfig(IAppConfig iAppConfig) {
        request(createService(WKCommonService.class).getAppConfig(), new IRequestResultListener<>() {
            @Override
            public void onSuccess(WKAPPConfig result) {
                WKConfig.getInstance().saveAppConfig(result);
                if (iAppConfig != null) {
                    iAppConfig.onResult(HttpResponseCode.success, "", result);
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iAppConfig != null) {
                    iAppConfig.onResult(code, msg, null);
                }
            }
        });
    }

    public void getChannelState(String channelID, byte channelType, final IChannelState iChannelState) {
        request(createService(WKCommonService.class).getChannelState(channelID, channelType), new IRequestResultListener<WKChannelState>() {
            @Override
            public void onSuccess(WKChannelState result) {
                iChannelState.onResult(result);
            }

            @Override
            public void onFail(int code, String msg) {
                iChannelState.onResult(null);
            }
        });
    }

    public interface IChannelState {
        void onResult(WKChannelState channelState);
    }


    public void getChannel(String channelID, byte channelType, IGetChannel iGetChannel) {
        dispatchQueuePool.execute(() -> request(createService(WKCommonService.class).getChannel(channelID, channelType), new IRequestResultListener<ChannelInfoEntity>() {
            @Override
            public void onSuccess(ChannelInfoEntity result) {
                saveChannel(result);
                if (iGetChannel != null) {
                    AndroidUtilities.runOnUIThread(() -> iGetChannel.onResult(HttpResponseCode.success, "", result));
                }
            }

            @Override
            public void onFail(int code, String msg) {
                if (iGetChannel != null) {
                    AndroidUtilities.runOnUIThread(() -> iGetChannel.onResult(code, msg, null));

                }
            }
        }));

    }

    private void saveChannel(ChannelInfoEntity entity) {
        if (entity == null || entity.channel == null) return;

        WKChannel localChannel = WKIM.getInstance().getChannelManager()
                .getChannel(entity.channel.channel_id, entity.channel.channel_type);
        HashMap<String, Object> remoteExtraMap = entity.extra == null ? new HashMap<>() : new HashMap<>(entity.extra);
        HashMap<String, Object> hashMap = new HashMap<>();
        if (localChannel != null && localChannel.localExtra != null) {
            hashMap.putAll(localChannel.localExtra);
        }

        WKChannel wkChannel = new WKChannel(entity.channel.channel_id, entity.channel.channel_type);
        boolean isTopicRoom = isTopicRoomEntity(entity, remoteExtraMap);
        boolean isRefreshContacts = false;
        if (localChannel != null && !TextUtils.isEmpty(localChannel.channelID)) {
            if (localChannel.follow != entity.follow || localChannel.status != entity.status) {
                isRefreshContacts = true;
            }
        }

        String localAvatar = localChannel == null ? "" : localChannel.avatar;
        String localAvatarCacheKey = localChannel == null ? "" : localChannel.avatarCacheKey;
        String localChannelName = localChannel == null ? "" : localChannel.channelName;

        if (isTopicRoom) {
            hashMap.put("topic_room", 1);
            putIfNotEmpty(hashMap, "topic_title", firstNotEmpty(
                    getExtraString(remoteExtraMap, "topic_title"),
                    entity.name,
                    getExtraString(hashMap, "topic_title"),
                    localChannelName));
            putIfNotEmpty(hashMap, "creator_uid", firstNotEmpty(
                    getExtraString(remoteExtraMap, "creator_uid"),
                    getExtraString(hashMap, "creator_uid")));
            putIfNotEmpty(hashMap, "creator_name", firstNotEmpty(
                    getExtraString(remoteExtraMap, "creator_name"),
                    getExtraString(hashMap, "creator_name")));
            putIfNotEmpty(hashMap, "creator_avatar", firstNotEmpty(
                    cleanTopicGroupAvatar(getExtraString(remoteExtraMap, "creator_avatar"), entity.channel.channel_id),
                    cleanTopicGroupAvatar(entity.logo, entity.channel.channel_id),
                    cleanTopicGroupAvatar(getExtraString(hashMap, "creator_avatar"), entity.channel.channel_id),
                    cleanTopicGroupAvatar(localAvatar, entity.channel.channel_id)));
            putIfNotEmpty(hashMap, "creator_avatar_cache_key", firstNotEmpty(
                    getExtraString(remoteExtraMap, "creator_avatar_cache_key"),
                    getExtraString(hashMap, "creator_avatar_cache_key")));
        }

        wkChannel.channelName = firstNotEmpty(
                entity.name,
                getExtraString(hashMap, "topic_title"),
                getExtraString(remoteExtraMap, "topic_title"),
                localChannelName);

        String creatorAvatar = firstNotEmpty(
                getExtraString(hashMap, "creator_avatar"),
                cleanTopicGroupAvatar(getExtraString(remoteExtraMap, "creator_avatar"), entity.channel.channel_id));
        String creatorAvatarCacheKey = firstNotEmpty(
                getExtraString(remoteExtraMap, "creator_avatar_cache_key"),
                getExtraString(hashMap, "creator_avatar_cache_key"));
        if (isTopicRoom) {
            String topicAvatar = firstNotEmpty(
                    creatorAvatar,
                    cleanTopicGroupAvatar(entity.logo, entity.channel.channel_id),
                    cleanTopicGroupAvatar(localAvatar, entity.channel.channel_id));
            String creatorUID = getExtraString(hashMap, "creator_uid");
            if (TextUtils.isEmpty(topicAvatar) && !TextUtils.isEmpty(creatorUID)) {
                topicAvatar = "users/" + creatorUID + "/avatar";
                hashMap.put("creator_avatar", topicAvatar);
            }
            wkChannel.avatar = topicAvatar;
            wkChannel.avatarCacheKey = firstNotEmpty(creatorAvatarCacheKey, localAvatarCacheKey);
        } else {
            wkChannel.avatar = entity.logo;
            wkChannel.avatarCacheKey = localAvatarCacheKey;
        }

        wkChannel.channelRemark = entity.remark;
        wkChannel.status = entity.status;
        wkChannel.online = entity.online;
        wkChannel.lastOffline = entity.last_offline;
        wkChannel.receipt = entity.receipt;
        wkChannel.robot = entity.robot;
        wkChannel.category = entity.category;
        if (isTopicRoom && TextUtils.isEmpty(wkChannel.category)) {
            wkChannel.category = "topic_room";
        }
        wkChannel.top = entity.stick;
        wkChannel.mute = entity.mute;
        wkChannel.showNick = entity.show_nick;
        wkChannel.follow = entity.follow;
        wkChannel.save = entity.save;
        wkChannel.forbidden = entity.forbidden;
        wkChannel.invite = entity.invite;
        wkChannel.flame = entity.flame;
        wkChannel.flameSecond = entity.flame_second;
        wkChannel.deviceFlag = entity.device_flag;
        if (entity.parent_channel != null) {
            wkChannel.parentChannelID = entity.parent_channel.channel_id;
            wkChannel.parentChannelType = entity.parent_channel.channel_type;
        }
        wkChannel.remoteExtraMap = remoteExtraMap;
        hashMap.put(WKChannelExtras.beDeleted, entity.be_deleted);
        hashMap.put(WKChannelExtras.beBlacklist, entity.be_blacklist);
        hashMap.put(WKChannelExtras.notice, entity.notice);
        wkChannel.localExtra = hashMap;
        WKIM.getInstance().getChannelManager().saveOrUpdateChannel(wkChannel);
        if (isRefreshContacts) {
            EndpointManager.getInstance().invoke(WKConstants.refreshContacts, null);
        }
    }

    private String cleanTopicGroupAvatar(String avatar, String channelID) {
        if (isTopicGroupAvatar(avatar, channelID)) return "";
        return avatar;
    }

    private boolean isTopicGroupAvatar(String avatar, String channelID) {
        if (TextUtils.isEmpty(avatar)) return false;
        String lower = avatar.toLowerCase(java.util.Locale.US);
        if (lower.contains("groups/topic_") && lower.contains("/avatar")) return true;
        return !TextUtils.isEmpty(channelID)
                && channelID.startsWith("topic_")
                && lower.contains("groups/" + channelID.toLowerCase(java.util.Locale.US) + "/avatar");
    }

    private boolean isTopicRoomEntity(ChannelInfoEntity entity, HashMap<String, Object> extra) {
        if (entity == null || entity.channel == null) return false;
        if (entity.channel.channel_type == 2 && !TextUtils.isEmpty(entity.channel.channel_id) && entity.channel.channel_id.startsWith("topic_")) return true;
        if ("topic_room".equals(entity.category)) return true;
        Object value = extra == null ? null : extra.get("topic_room");
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        return value != null && ("1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)));
    }

    private String getExtraString(java.util.Map<String, Object> map, String key) {
        if (map == null || TextUtils.isEmpty(key)) return "";
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNotEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private void putIfNotEmpty(HashMap<String, Object> map, String key, String value) {
        if (map != null && !TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
            map.put(key, value);
        }
    }

    public interface IGetChannel {
        void onResult(int code, String msg, ChannelInfoEntity entity);
    }

    public void getAppModule(@NotNull final IAppModule iAppModule) {
        request(createService(WKCommonService.class).getAppModule(), new IRequestResultListener<List<AppModule>>() {
            @Override
            public void onSuccess(List<AppModule> result) {
                String text = WKSharedPreferencesUtil.getInstance().getSPWithUID("app_module");
                List<AppModule> localSavedAppModule = new ArrayList<>();
                if (!TextUtils.isEmpty(text)) {
                    localSavedAppModule = JSON.parseArray(text, AppModule.class);
                }
                List<AppModule> tempList = new ArrayList<>();
                if (WKReader.isNotEmpty(result)) {
                    for (AppModule item : result) {
                        AppModule m = new AppModule();
                        m.setName(item.getName());
                        m.setDesc(item.getDesc());
                        m.setSid(item.getSid());
                        m.setStatus(item.getStatus());
                        if (item.getStatus() == 2) {
                            m.setChecked(true);
                        } else if (item.getStatus() == 0) {
                            m.setChecked(false);
                        } else {
                            if (WKReader.isNotEmpty(localSavedAppModule)) {
                                for (AppModule temp : localSavedAppModule) {
                                    if (temp.getSid().equals(item.getSid())) {
                                        m.setChecked(temp.getChecked());
                                    }
                                }
                            } else {
                                m.setChecked(false);
                            }
                        }
                        tempList.add(m);
                    }
                }
                String json = JSON.toJSONString(tempList);
                WKSharedPreferencesUtil.getInstance().putSPWithUID("app_module", json);

                iAppModule.onResult(HttpResponseCode.success, "", tempList);
            }

            @Override
            public void onFail(int code, String msg) {
                iAppModule.onResult(code, msg, null);
            }
        });
    }

    public interface IAppModule {
        void onResult(int code, String msg, List<AppModule> list);
    }
}
