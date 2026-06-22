package com.chat.base.ui.components;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.chat.base.R;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConstants;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.chat.base.glide.GlideRequestOptions;
import com.chat.base.glide.GlideUtils;
import com.chat.base.glide.MyGlideUrlWithId;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.chat.base.utils.WKTimeUtils;
import com.google.android.material.imageview.ShapeableImageView;

import com.google.android.material.shape.CornerFamily;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;

import java.io.File;
import java.util.Locale;

public class AvatarView extends FrameLayout {
    public ShapeableImageView imageView;
    public TextView defaultAvatarTv;
    public View spotView;
    public TextView onlineTv;
    private float avatarSize = 40f;
    private float avatarCornerSize = 20f;

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        imageView = new ShapeableImageView(getContext());
//        imageView.setStrokeColorResource(R.color.borderColor);
//        imageView.setStrokeWidth(AndroidUtilities.dp(1));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setPadding(AndroidUtilities.dp(0.1f), AndroidUtilities.dp(0.1f), AndroidUtilities.dp(0.1f), AndroidUtilities.dp(0.1f));
        imageView.setImageResource(R.drawable.default_view_bg);

        spotView = new View(getContext());
        spotView.setBackgroundResource(R.drawable.online_spot);
        spotView.setVisibility(GONE);

        defaultAvatarTv = new TextView(getContext());
        defaultAvatarTv.setTextSize(20f);
        defaultAvatarTv.setTextColor(0xffffffff);
        defaultAvatarTv.setBackgroundResource(R.drawable.shape_rand);
        defaultAvatarTv.setTypeface(Typeface.DEFAULT_BOLD);
        defaultAvatarTv.setVisibility(GONE);
        defaultAvatarTv.setGravity(Gravity.CENTER);

        onlineTv = new TextView(getContext());
        onlineTv.setTextColor(0xff02F507);
        onlineTv.setTextSize(9f);
        onlineTv.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        onlineTv.setBackgroundResource(R.drawable.online_bg);
        onlineTv.setVisibility(INVISIBLE);
        addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        addView(onlineTv, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END, 0, 0, 0, 0));
        addView(spotView, LayoutHelper.createFrame(15, 15, Gravity.BOTTOM | Gravity.END, 0, 0, 0, 0));
        addView(defaultAvatarTv, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        setSize(40);
    }

    private void prepareImageAvatar() {
        imageView.setVisibility(VISIBLE);
        defaultAvatarTv.setVisibility(GONE);
    }

    public void showDefaultAvatar(String name) {
        showDefaultAvatar(name, name);
    }

    public void showDefaultAvatar(String name, String seed) {
        String letter = getAvatarLetter(name);
        defaultAvatarTv.setText(letter);
        defaultAvatarTv.setBackground(makeDefaultAvatarBg(TextUtils.isEmpty(seed) ? name : seed));
        defaultAvatarTv.setVisibility(VISIBLE);
        imageView.setVisibility(INVISIBLE);
        spotView.setVisibility(GONE);
        onlineTv.setVisibility(INVISIBLE);
    }

    public void showAvatarUrl(String avatar, String avatarCacheKey, String fallbackName) {
        showAvatarUrl(avatar, avatarCacheKey, fallbackName, fallbackName);
    }

    public void showAvatarUrl(String avatar, String avatarCacheKey, String fallbackName, String fallbackSeed) {
        if (TextUtils.isEmpty(avatar)) {
            showDefaultAvatar(fallbackName, fallbackSeed);
            return;
        }
        prepareImageAvatar();
        String url = WKApiConfig.getShowUrl(avatar);
        loadAvatarUrlWithFallback(url, avatarCacheKey, fallbackName, fallbackSeed);
    }

    private void loadAvatarUrlWithFallback(String url, String avatarCacheKey, String fallbackName, String fallbackSeed) {
        if (TextUtils.isEmpty(url)) {
            showDefaultAvatar(fallbackName, fallbackSeed);
            return;
        }
        Context context = getContext();
        if (context == null) {
            showDefaultAvatar(fallbackName, fallbackSeed);
            return;
        }
        Object model = TextUtils.isEmpty(avatarCacheKey) ? url : new MyGlideUrlWithId(url, avatarCacheKey);
        try {
            Glide.with(context)
                    .load(model)
                    .dontAnimate()
                    .apply(GlideRequestOptions.getInstance().normalRequestOption())
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            post(() -> showDefaultAvatar(fallbackName, fallbackSeed));
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            showDefaultAvatar(fallbackName, fallbackSeed);
        }
    }

    private String getAvatarLetter(String name) {
        if (TextUtils.isEmpty(name)) return "#";
        String trim = name.trim();
        if (TextUtils.isEmpty(trim)) return "#";
        return trim.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    private GradientDrawable makeDefaultAvatarBg(String seed) {
        int[] colors = new int[]{
                0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFF059669,
                0xFFEA580C, 0xFF0891B2, 0xFF4F46E5, 0xFFDC2626
        };
        int index = TextUtils.isEmpty(seed) ? 0 : (seed.hashCode() & 0x7fffffff) % colors.length;
        GradientDrawable drawable = new GradientDrawable();
        if (avatarCornerSize >= avatarSize / 2f - 0.5f) {
            drawable.setShape(GradientDrawable.OVAL);
        } else {
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(AndroidUtilities.dp(avatarCornerSize));
        }
        drawable.setColor(colors[index]);
        drawable.setStroke(AndroidUtilities.dp(1), 0x66FFFFFF);
        return drawable;
    }

    public void setStrokeWidth(float width) {
        imageView.setStrokeWidth(AndroidUtilities.dp(width));
    }

    public void setStrokeColor(int colorResource) {
        imageView.setStrokeColorResource(colorResource);
    }

    public void setSize(float size) {
        setSize(size, size / 2f);
    }

    public void setSize(float size, float cornerSize) {
        avatarSize = size;
        avatarCornerSize = cornerSize;
        imageView.getLayoutParams().width = AndroidUtilities.dp(size);
        imageView.getLayoutParams().height = AndroidUtilities.dp(size);
        imageView.setShapeAppearanceModel(imageView.getShapeAppearanceModel()
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, AndroidUtilities.dp(cornerSize))
                .build());

        defaultAvatarTv.getLayoutParams().height = AndroidUtilities.dp(size);
        defaultAvatarTv.getLayoutParams().width = AndroidUtilities.dp(size);
        defaultAvatarTv.setTextSize(size * 0.38f);
        if (defaultAvatarTv.getVisibility() == VISIBLE) {
            defaultAvatarTv.setBackground(makeDefaultAvatarBg(defaultAvatarTv.getText() == null ? "" : defaultAvatarTv.getText().toString()));
        }

    }

    public void showAvatar(String channelID, byte channelType, String avatarCacheKey) {
        if (isTopicRoomId(channelID, channelType)) {
            showDefaultAvatar(channelID, channelID);
            return;
        }
        prepareImageAvatar();
        String url = getAvatarURL(channelID, channelType);
        GlideUtils.getInstance().showAvatarImg(getContext(), url, avatarCacheKey, imageView);
    }

    public void showAvatar(String channelID, byte channelType, boolean showOnlineStatus) {
        prepareImageAvatar();
        spotView.setVisibility(GONE);
        onlineTv.setVisibility(INVISIBLE);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null) {
            showAvatar(channel, showOnlineStatus);
        } else if (isTopicRoomId(channelID, channelType)) {
            showDefaultAvatar(channelID, channelID);
        } else {
            String url = getAvatarURL(channelID, channelType);
            GlideUtils.getInstance().showAvatarImg(getContext(), url, "", imageView);
        }
    }

    public void showAvatar(String channelID, byte channelType) {
        prepareImageAvatar();
        spotView.setVisibility(GONE);
        onlineTv.setVisibility(INVISIBLE);
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null) {
            showAvatar(channel, false);
        } else if (isTopicRoomId(channelID, channelType)) {
            showDefaultAvatar(channelID, channelID);
        } else {
            String url = getAvatarURL(channelID, channelType);
            GlideUtils.getInstance().showAvatarImg(getContext(), url, "", imageView);
        }
    }

    public void showAvatar(WKChannel channel) {
        showAvatar(channel, false);
    }

    public void showAvatar(WKChannel channel, boolean showOnlineStatus) {
        if (channel == null) return;
        if (isTopicRoomChannel(channel)) {
            showTopicAvatar(channel);
            return;
        }
        prepareImageAvatar();
        String avatarCacheKey = channel.avatarCacheKey;
        String url;
        if (!TextUtils.isEmpty(channel.avatar) && channel.avatar.contains("/")) {
            url = WKApiConfig.getShowUrl(channel.avatar);
        } else {
            url = getAvatarURL(channel.channelID, channel.channelType);
        }
        GlideUtils.getInstance().showAvatarImg(imageView.getContext(), url, avatarCacheKey, imageView);
        if (showOnlineStatus) {
            if (channel.online == 1) {
                spotView.setVisibility(VISIBLE);
                onlineTv.setVisibility(INVISIBLE);
            } else {
                spotView.setVisibility(GONE);
                String showTime = WKTimeUtils.getInstance().getOnlineTime(channel.lastOffline);
                if (TextUtils.isEmpty(showTime)) {
                    onlineTv.setVisibility(INVISIBLE);
                } else {
                    onlineTv.setVisibility(VISIBLE);
                    onlineTv.setText(showTime);
                }
            }
        } else {
            spotView.setVisibility(GONE);
            onlineTv.setVisibility(INVISIBLE);
        }
    }

    public void showTopicAvatar(WKChannel channel) {
        if (channel == null) return;
        String showName = firstNotEmpty(channel.channelRemark, channel.channelName,
                getTopicExtraString(channel, "topic_title"), getTopicExtraString(channel, "creator_name"));
        String creatorUid = getTopicExtraString(channel, "creator_uid");
        String avatar = getTopicAvatar(channel, creatorUid);
        String avatarCacheKey = firstNotEmpty(getTopicExtraString(channel, "creator_avatar_cache_key"), channel.avatarCacheKey);
        if (TextUtils.isEmpty(avatar)) {
            showDefaultAvatar(showName, firstNotEmpty(creatorUid, showName, channel.channelID));
        } else {
            showAvatarUrl(avatar, avatarCacheKey, showName, firstNotEmpty(creatorUid, showName, channel.channelID));
        }
        spotView.setVisibility(GONE);
        onlineTv.setVisibility(INVISIBLE);
    }

    private String getTopicAvatar(WKChannel channel, String creatorUid) {
        if (channel == null) return "";
        String creatorAvatar = firstNotEmpty(getTopicExtraString(channel, "creator_avatar"));
        String channelAvatar = channel.avatar;
        if (isTopicGroupAvatar(channelAvatar, channel.channelID)) {
            channelAvatar = "";
        }
        if (isTopicGroupAvatar(creatorAvatar, channel.channelID)) {
            creatorAvatar = "";
        }
        String avatar = firstNotEmpty(creatorAvatar, channelAvatar);
        if (TextUtils.isEmpty(avatar) && !TextUtils.isEmpty(creatorUid)) {
            avatar = "users/" + creatorUid + "/avatar";
        }
        return avatar;
    }

    private boolean isTopicGroupAvatar(String avatar, String channelID) {
        if (TextUtils.isEmpty(avatar)) return false;
        String lower = avatar.toLowerCase(Locale.US);
        if (lower.contains("groups/topic_") && lower.contains("/avatar")) return true;
        return !TextUtils.isEmpty(channelID)
                && channelID.startsWith("topic_")
                && lower.contains("groups/" + channelID.toLowerCase(Locale.US) + "/avatar");
    }

    private boolean isTopicRoomId(String channelID, byte channelType) {
        return channelType == 2 && !TextUtils.isEmpty(channelID) && channelID.startsWith("topic_");
    }

    private boolean isTopicRoomChannel(WKChannel channel) {
        if (channel == null) return false;
        if (channel.channelType == 2 && !TextUtils.isEmpty(channel.channelID) && channel.channelID.startsWith("topic_")) return true;
        if ("topic_room".equals(channel.category)) return true;
        return hasTopicRoomFlag(channel.remoteExtraMap) || hasTopicRoomFlag(channel.localExtra);
    }

    private boolean hasTopicRoomFlag(java.util.Map<String, Object> map) {
        if (map == null) return false;
        Object value = map.get("topic_room");
        if (value instanceof Number) return ((Number) value).intValue() == 1;
        return value != null && ("1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value)));
    }

    private String getTopicExtraString(WKChannel channel, String key) {
        if (channel == null || TextUtils.isEmpty(key)) return "";
        String value = getExtraString(channel.localExtra, key);
        if (TextUtils.isEmpty(value)) value = getExtraString(channel.remoteExtraMap, key);
        return value;
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

    private String getAvatarURL(String channelID, byte channelType) {
        String filePath = WKConstants.avatarCacheDir + channelType + "_" + channelID;
        File file = new File(filePath);
        if (file.exists()) {
            return filePath;
        } else {
            String url = WKApiConfig.getShowAvatar(channelID, channelType);
            return url;
        }
    }

    public static void clearCache(String channelID, byte channelType) {
        String filePath = WKConstants.avatarCacheDir + channelType + "_" + channelID;
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
    }
}
