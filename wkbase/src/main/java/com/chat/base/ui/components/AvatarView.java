package com.chat.base.ui.components;

import android.content.Context;
import android.content.SharedPreferences;
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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.chat.base.R;
import com.chat.base.common.WKCommonModel;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
import com.chat.base.config.WKConstants;
import com.chat.base.glide.GlideRequestOptions;
import com.chat.base.glide.MyGlideUrlWithId;
import com.chat.base.utils.AndroidUtilities;
import com.chat.base.utils.LayoutHelper;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AvatarView extends FrameLayout {
    public ShapeableImageView imageView;
    public TextView defaultAvatarTv;
    public View spotView;
    public TextView onlineTv;
    public ImageView flagIv;

    private static final float FLAG_WIDTH_RATIO = 0.42f;
    private static final float FLAG_HEIGHT_RATIO = 2f / 3f;
    private static final float FLAG_LEFT_OVERHANG_RATIO = 0.08f;
    private static final int FLAG_MIN_WIDTH_DP = 16;
    private static final int FLAG_MIN_HEIGHT_DP = 11;
    private static final String PROFILE_EXTRA_PREF = "front_profile_extra";

    private static final Object COUNTRY_FETCH_LOCK = new Object();
    private static final int FETCHED_PERSONAL_COUNTRY_MAX_SIZE = 3000;
    private static final Set<String> FETCHING_PERSONAL_COUNTRY_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Map<String, Boolean> FETCHED_PERSONAL_COUNTRY_KEYS = new LinkedHashMap<String, Boolean>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > FETCHED_PERSONAL_COUNTRY_MAX_SIZE;
        }
    };
    private static final Map<String, Integer> FLAG_RES_CACHE = new ConcurrentHashMap<>();

    private String forcedFlagCountry = "";
    private volatile String boundChannelKey = "";
    private volatile String boundDisplayKey = "";
    private String lastAvatarLoadKey = "";
    private String defaultAvatarSeed = "";
    private String lastDefaultBgKey = "";
    private int currentFlagResId = 0;
    private boolean sizeInited = false;
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
        // 跟 Web 版 .cp-avatar-stack / .wkconv-avatar-flag-wrap 一样：
        // 头像自己裁圆，国旗作为左下角浮层，允许边缘探出一点。
        setClipChildren(false);
        setClipToPadding(false);

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
        // 不再把“最后在线时间”压在头像右下角，聊天页/列表需要时间时放到文字区域显示。
        onlineTv.setVisibility(GONE);

        flagIv = new ImageView(getContext());
        flagIv.setScaleType(ImageView.ScaleType.FIT_XY);
        flagIv.setAdjustViewBounds(false);
        applyFlagStyle();
        flagIv.setVisibility(GONE);

        addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        addView(defaultAvatarTv, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        addView(flagIv, LayoutHelper.createFrame(17, 11, Gravity.BOTTOM | Gravity.START, 0, 0, 0, 0));
        addView(spotView, LayoutHelper.createFrame(9, 9, Gravity.TOP | Gravity.END, 0, 0, 0, 0));
        addView(onlineTv, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END, 0, 0, 0, 0));
        setSize(40);
    }

    private void prepareImageAvatar() {
        imageView.setVisibility(VISIBLE);
        defaultAvatarTv.setVisibility(GONE);
    }

    private void resetStatusViews() {
        spotView.setVisibility(GONE);
        onlineTv.setText("");
        onlineTv.setVisibility(GONE);
    }

    private void clearImageRequest(Context context) {
        lastAvatarLoadKey = "";
        if (context == null || imageView == null) return;
        try {
            Glide.with(context).clear(imageView);
        } catch (Exception ignored) {
        }
    }

    public void showDefaultAvatar(String name) {
        showDefaultAvatar(name, name);
    }

    public void showDefaultAvatar(String name, String seed) {
        clearBoundKeys();
        clearImageRequest(getContext());
        forcedFlagCountry = "";
        setDefaultAvatarInternal(name, seed, false);
    }

    private void setDefaultAvatarInternal(String name, String seed, boolean keepCurrentFlag) {
        defaultAvatarSeed = TextUtils.isEmpty(seed) ? name : seed;
        String letter = getAvatarLetter(name);
        defaultAvatarTv.setText(letter);

        String bgKey = safeString(defaultAvatarSeed) + "_" + avatarSize + "_" + avatarCornerSize;
        if (!TextUtils.equals(lastDefaultBgKey, bgKey)) {
            defaultAvatarTv.setBackground(makeDefaultAvatarBg(defaultAvatarSeed));
            lastDefaultBgKey = bgKey;
        }

        defaultAvatarTv.setVisibility(VISIBLE);
        imageView.setVisibility(INVISIBLE);
        resetStatusViews();
        if (!keepCurrentFlag) {
            hideFlag();
        }
    }

    public void showAvatarUrl(String avatar, String avatarCacheKey, String fallbackName) {
        showAvatarUrl(avatar, avatarCacheKey, fallbackName, fallbackName);
    }

    public void showAvatarUrl(String avatar, String avatarCacheKey, String fallbackName, String fallbackSeed) {
        showAvatarUrlInternal(avatar, avatarCacheKey, fallbackName, fallbackSeed, false);
    }

    private void showAvatarUrlInternal(String avatar, String avatarCacheKey, String fallbackName, String fallbackSeed, boolean keepFlagOnFallback) {
        clearBoundKeys();
        resetStatusViews();
        if (!keepFlagOnFallback) {
            forcedFlagCountry = "";
        }

        if (TextUtils.isEmpty(avatar)) {
            clearImageRequest(getContext());
            setDefaultAvatarInternal(fallbackName, fallbackSeed, keepFlagOnFallback);
            return;
        }

        prepareImageAvatar();
        clearForcedFlagAndHide();
        String url = WKApiConfig.getShowUrl(avatar);
        String displayKey = bindStandaloneDisplayKey("url", url, avatarCacheKey, fallbackSeed);
        loadAvatarUrlWithFallback(url, avatarCacheKey, fallbackName, fallbackSeed, displayKey, keepFlagOnFallback);
    }

    private void loadAvatarUrlWithFallback(String url, String avatarCacheKey, String fallbackName, String fallbackSeed, String expectedDisplayKey, boolean keepFlagOnFallback) {
        if (TextUtils.isEmpty(url)) {
            clearImageRequest(getContext());
            setDefaultAvatarInternal(fallbackName, fallbackSeed, keepFlagOnFallback);
            return;
        }
        Context context = getContext();
        if (context == null) {
            setDefaultAvatarInternal(fallbackName, fallbackSeed, keepFlagOnFallback);
            return;
        }

        Object model = TextUtils.isEmpty(avatarCacheKey) ? url : new MyGlideUrlWithId(url, avatarCacheKey);
        String loadKey = expectedDisplayKey + "_url_" + url + "_" + safeString(avatarCacheKey);
        if (TextUtils.equals(lastAvatarLoadKey, loadKey)) {
            return;
        }
        lastAvatarLoadKey = loadKey;

        try {
            Glide.with(context)
                    .load(model)
                    .dontAnimate()
                    .apply(GlideRequestOptions.getInstance().normalRequestOption())
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            post(() -> {
                                if (!TextUtils.equals(expectedDisplayKey, boundDisplayKey)) return;
                                lastAvatarLoadKey = "";
                                setDefaultAvatarInternal(fallbackName, fallbackSeed, keepFlagOnFallback);
                            });
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            lastAvatarLoadKey = "";
            setDefaultAvatarInternal(fallbackName, fallbackSeed, keepFlagOnFallback);
        }
    }

    private String getAvatarLetter(String name) {
        if (TextUtils.isEmpty(name)) return "#";
        String trim = name.trim();
        if (TextUtils.isEmpty(trim)) return "#";
        int codePoint = trim.codePointAt(0);
        return new String(Character.toChars(codePoint)).toUpperCase(Locale.getDefault());
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

    private void applyFlagStyle() {
        if (flagIv == null) return;
        // 稳定版不用 TextView/emoji，直接用本地 PNG 资源，避免系统 emoji 字体和 GPU 合成造成发灰/半透明。
        // 这里保持纯国旗：无圆形底、无阴影、无 tint、无 alpha、按 3:2 View 尺寸直接显示，不走裁剪。
        flagIv.setAlpha(1f);
        flagIv.setBackground(null);
        flagIv.setColorFilter(null);
        flagIv.setPadding(0, 0, 0, 0);
        flagIv.setScaleType(ImageView.ScaleType.FIT_XY);
        flagIv.bringToFront();
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
        if (sizeInited && Float.compare(avatarSize, size) == 0 && Float.compare(avatarCornerSize, cornerSize) == 0) {
            return;
        }
        sizeInited = true;
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
            String bgKey = safeString(defaultAvatarSeed) + "_" + avatarSize + "_" + avatarCornerSize;
            defaultAvatarTv.setBackground(makeDefaultAvatarBg(defaultAvatarSeed));
            lastDefaultBgKey = bgKey;
        } else {
            lastDefaultBgKey = "";
        }

        FrameLayout.LayoutParams flagParams = (FrameLayout.LayoutParams) flagIv.getLayoutParams();
        int flagWidth = Math.max(FLAG_MIN_WIDTH_DP, Math.round(size * FLAG_WIDTH_RATIO));
        int flagHeight = Math.max(FLAG_MIN_HEIGHT_DP, Math.round(flagWidth * FLAG_HEIGHT_RATIO));
        flagParams.width = AndroidUtilities.dp(flagWidth);
        flagParams.height = AndroidUtilities.dp(flagHeight);
        flagParams.gravity = Gravity.BOTTOM | Gravity.START;
        // 国旗向左探出一点，比完全压在头像内部更接近 Web 版层叠效果。
        // 如果某个父容器仍然裁剪，需要在对应 item 父布局上额外设置 clipChildren=false / clipToPadding=false。
        int flagLeftOverhang = Math.max(1, Math.round(size * FLAG_LEFT_OVERHANG_RATIO));
        int flagBottomInset = Math.max(1, Math.round(size * 0.03f));
        flagParams.leftMargin = -AndroidUtilities.dp(flagLeftOverhang);
        flagParams.bottomMargin = AndroidUtilities.dp(flagBottomInset);
        flagIv.setLayoutParams(flagParams);
        applyFlagStyle();

        int spotSize = Math.max(6, Math.round(size * 0.15f));
        int spotInset = Math.max(2, Math.round(size * 0.06f));
        FrameLayout.LayoutParams spotParams = (FrameLayout.LayoutParams) spotView.getLayoutParams();
        spotParams.width = AndroidUtilities.dp(spotSize);
        spotParams.height = AndroidUtilities.dp(spotSize);
        spotParams.gravity = Gravity.TOP | Gravity.END;
        // 头像尺寸变大后，绿点不能贴到容器外沿，否则在 32dp/44dp 卡片头像里会看起来“跑出去”。
        // 这里按头像尺寸给一点内缩，保证绿点始终压在头像右上角里面。
        spotParams.rightMargin = AndroidUtilities.dp(spotInset);
        spotParams.topMargin = AndroidUtilities.dp(spotInset);
        spotView.setLayoutParams(spotParams);
        spotView.bringToFront();
        requestLayout();
    }

    public void showAvatar(String channelID, byte channelType, String avatarCacheKey) {
        bindChannelKey(channelID, channelType);
        resetStatusViews();

        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null && isTopicRoomChannel(channel)) {
            showTopicAvatar(channel);
            return;
        }
        if (isTopicRoomId(channelID, channelType)) {
            clearImageRequest(getContext());
            setDefaultAvatarInternal(channelID, channelID, false);
            return;
        }

        prepareImageAvatar();
        clearForcedFlagAndHide();
        String country = channel != null ? getChannelCountry(channel) : getLocalSavedCountry(channelID);
        updateFlagByCountry(country);
        if (TextUtils.isEmpty(country)) {
            tryFetchPersonalChannelCountry(channelID, channelType);
        }
        String url = getAvatarURL(channelID, channelType);
        showChannelAvatarImage(url, avatarCacheKey, channelID, channelID);
    }

    public void showAvatar(String channelID, byte channelType, boolean showOnlineStatus) {
        bindChannelKey(channelID, channelType);
        resetStatusViews();
        clearForcedFlagAndHide();

        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null) {
            showAvatar(channel, showOnlineStatus);
        } else if (isTopicRoomId(channelID, channelType)) {
            clearImageRequest(getContext());
            setDefaultAvatarInternal(channelID, channelID, false);
        } else {
            prepareImageAvatar();
            String country = getLocalSavedCountry(channelID);
            updateFlagByCountry(country);
            if (TextUtils.isEmpty(country)) {
                tryFetchPersonalChannelCountry(channelID, channelType);
            }
            String url = getAvatarURL(channelID, channelType);
            showChannelAvatarImage(url, "", channelID, channelID);
        }
    }

    public void showAvatar(String channelID, byte channelType) {
        bindChannelKey(channelID, channelType);
        resetStatusViews();
        clearForcedFlagAndHide();

        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null) {
            showAvatar(channel, false);
        } else if (isTopicRoomId(channelID, channelType)) {
            clearImageRequest(getContext());
            setDefaultAvatarInternal(channelID, channelID, false);
        } else {
            prepareImageAvatar();
            String country = getLocalSavedCountry(channelID);
            updateFlagByCountry(country);
            if (TextUtils.isEmpty(country)) {
                tryFetchPersonalChannelCountry(channelID, channelType);
            }
            String url = getAvatarURL(channelID, channelType);
            showChannelAvatarImage(url, "", channelID, channelID);
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

        bindChannelKey(channel.channelID, channel.channelType);
        prepareImageAvatar();
        resetStatusViews();
        clearForcedFlagAndHide();

        String avatarCacheKey = channel.avatarCacheKey;
        String url;
        if (!TextUtils.isEmpty(channel.avatar) && channel.avatar.contains("/")) {
            url = WKApiConfig.getShowUrl(channel.avatar);
        } else {
            url = getAvatarURL(channel.channelID, channel.channelType);
        }
        String fallbackName = firstNotEmpty(channel.channelRemark, channel.channelName, channel.channelID);
        showChannelAvatarImage(url, avatarCacheKey, fallbackName, channel.channelID);

        String country = getChannelCountry(channel);
        updateFlagByCountry(country);
        if (TextUtils.isEmpty(country)) {
            tryFetchPersonalChannelCountry(channel.channelID, channel.channelType);
        }

        // 头像上只显示更小的在线绿点；离线的“最后在线时间”不再显示在头像右下角。
        spotView.setVisibility(showOnlineStatus && channel.online == 1 ? VISIBLE : GONE);
        onlineTv.setText("");
        onlineTv.setVisibility(GONE);
    }

    public void showTopicAvatar(WKChannel channel) {
        if (channel == null) return;
        bindChannelKey(channel.channelID, channel.channelType);
        resetStatusViews();

        String showName = firstNotEmpty(channel.channelRemark, channel.channelName,
                getTopicExtraString(channel, "topic_title"), getTopicExtraString(channel, "creator_name"));
        String creatorUid = getTopicExtraString(channel, "creator_uid");
        String avatar = getTopicAvatar(channel, creatorUid);
        String avatarCacheKey = firstNotEmpty(getTopicExtraString(channel, "creator_avatar_cache_key"), channel.avatarCacheKey);
        if (TextUtils.isEmpty(avatar)) {
            clearImageRequest(getContext());
            setDefaultAvatarInternal(showName, firstNotEmpty(creatorUid, showName, channel.channelID), true);
        } else {
            showAvatarUrlInternal(avatar, avatarCacheKey, showName, firstNotEmpty(creatorUid, showName, channel.channelID), true);
        }
        updateTopicFlagView(channel);
        resetStatusViews();
    }

    private void showChannelAvatarImage(String url, String avatarCacheKey, String fallbackName, String fallbackSeed) {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(url)) {
            clearImageRequest(context);
            setDefaultAvatarInternal(fallbackName, fallbackSeed, true);
            return;
        }

        String expectedDisplayKey = boundDisplayKey;
        String loadKey = expectedDisplayKey + "_channel_" + safeString(url) + "_" + safeString(avatarCacheKey);
        if (TextUtils.equals(lastAvatarLoadKey, loadKey)) {
            return;
        }
        lastAvatarLoadKey = loadKey;

        Object model = TextUtils.isEmpty(avatarCacheKey) || isLocalFilePath(url)
                ? url
                : new MyGlideUrlWithId(url, avatarCacheKey);
        try {
            Glide.with(context)
                    .load(model)
                    .dontAnimate()
                    .apply(GlideRequestOptions.getInstance().normalRequestOption())
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            post(() -> {
                                if (!TextUtils.equals(expectedDisplayKey, boundDisplayKey)) return;
                                lastAvatarLoadKey = "";
                                setDefaultAvatarInternal(fallbackName, fallbackSeed, true);
                            });
                            return true;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            lastAvatarLoadKey = "";
            setDefaultAvatarInternal(fallbackName, fallbackSeed, true);
        }
    }

    private void updateFlagView(WKChannel channel) {
        updateFlagByCountry(getChannelCountry(channel));
    }

    private void updateFlagByCountry(String countryOrFlag) {
        int flagResId = countryToFlagRes(countryOrFlag);
        if (flagResId == 0) {
            hideFlag();
            return;
        }
        if (currentFlagResId != flagResId) {
            flagIv.setImageResource(flagResId);
            currentFlagResId = flagResId;
        }
        if (flagIv.getVisibility() != VISIBLE) {
            flagIv.setVisibility(VISIBLE);
        }
        flagIv.bringToFront();
    }

    /**
     * 外部列表已有国籍/国旗字段时可直接调用，比如聊天室 member.flag/country_code。
     */
    public void showFlag(String countryOrFlag) {
        forcedFlagCountry = countryOrFlag == null ? "" : countryOrFlag;
        updateFlagByCountry(forcedFlagCountry);
    }

    private void updateTopicFlagView(WKChannel channel) {
        String country = firstNotEmpty(
                getTopicExtraString(channel, "creator_country_code"),
                getTopicExtraString(channel, "creator_country"),
                getTopicExtraString(channel, "country_code"),
                getTopicExtraString(channel, "country"),
                getTopicExtraString(channel, "nationality_code"),
                getTopicExtraString(channel, "nationality")
        );
        updateFlagByCountry(country);
    }

    private void hideFlag() {
        if (flagIv != null) {
            if (currentFlagResId != 0) {
                flagIv.setImageDrawable(null);
                currentFlagResId = 0;
            }
            if (flagIv.getVisibility() != GONE) {
                flagIv.setVisibility(GONE);
            }
        }
    }

    private void clearForcedFlagAndHide() {
        forcedFlagCountry = "";
        hideFlag();
    }

    private String getChannelCountry(WKChannel channel) {
        if (channel == null) return "";
        String country = firstNotEmpty(
                getExtraString(channel.localExtra, "country_code"),
                getExtraString(channel.remoteExtraMap, "country_code"),
                getExtraString(channel.localExtra, "countryCode"),
                getExtraString(channel.remoteExtraMap, "countryCode"),
                getExtraString(channel.localExtra, "country"),
                getExtraString(channel.remoteExtraMap, "country"),
                getExtraString(channel.localExtra, "nationality_code"),
                getExtraString(channel.remoteExtraMap, "nationality_code"),
                getExtraString(channel.localExtra, "nationality"),
                getExtraString(channel.remoteExtraMap, "nationality")
        );

        // 兼容上一步资料页暂时保存在本地的国籍。
        // 只允许当前登录用户头像读本地兜底，避免好友头像全部显示成自己的国旗。
        if (TextUtils.isEmpty(country)) {
            country = getLocalSavedCountry(channel.channelID);
        }
        return country;
    }

    private String getLocalSavedCountry(String channelID) {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(channelID)) return "";
        String uid = WKConfig.getInstance().getUid();
        if (TextUtils.isEmpty(uid) || !channelID.equals(uid)) return "";
        SharedPreferences preferences = context.getSharedPreferences(PROFILE_EXTRA_PREF, Context.MODE_PRIVATE);
        String country = preferences.getString(uid + "_country", "");
        if (TextUtils.isEmpty(country)) {
            country = preferences.getString("current_country", "");
        }
        return country;
    }

    private int countryToFlagRes(String country) {
        if (TextUtils.isEmpty(country)) return 0;
        String cacheKey = country.trim().toLowerCase(Locale.US);
        if (TextUtils.isEmpty(cacheKey)) return 0;
        Integer cached = FLAG_RES_CACHE.get(cacheKey);
        if (cached != null) return cached;
        int resId = countryToFlagResUncached(country);
        FLAG_RES_CACHE.put(cacheKey, resId);
        return resId;
    }

    private int countryToFlagResUncached(String country) {
        if (TextUtils.isEmpty(country)) return 0;
        String value = country.trim();
        if (TextUtils.isEmpty(value)) return 0;

        String flagCode = extractFirstEmojiFlagCountryCode(value);
        if (!TextUtils.isEmpty(flagCode)) {
            return countryCodeToFlagRes(flagCode);
        }
        if (value.startsWith("🌍") || value.startsWith("🌎") || value.startsWith("🌏")) {
            return R.drawable.ic_flag_other;
        }

        String normalized = value.toLowerCase(Locale.US)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .replace("/", "");

        // 常用默认国家
        if (isCountry(normalized, "cn", "chn", "china", "chinese", "prc") || value.contains("中国") || value.contains("中國") || value.contains("中文") || value.contains("တရုတ်")) return R.drawable.ic_flag_cn;
        if (isCountry(normalized, "us", "usa", "unitedstates", "unitedstatesofamerica", "america", "american", "english") || value.contains("美国") || value.contains("美國") || value.contains("英语") || value.contains("英語") || value.contains("အမေရိကန်") || value.contains("အင်္ဂလိပ်")) return R.drawable.ic_flag_us;
        if (isCountry(normalized, "jp", "jpn", "japan", "japanese") || value.contains("日本") || value.contains("ဂျပန်")) return R.drawable.ic_flag_jp;
        if (isCountry(normalized, "kr", "kor", "korea", "southkorea", "republicofkorea", "korean") || value.contains("韩国") || value.contains("韓國") || value.contains("ကိုရီးယား")) return R.drawable.ic_flag_kr;

        // 东南亚
        if (isCountry(normalized, "mm", "mya", "myanmar", "burma", "burmese") || value.contains("缅甸") || value.contains("緬甸") || value.contains("မြန်မာ")) return R.drawable.ic_flag_mm;
        if (isCountry(normalized, "th", "tha", "thailand", "thai") || value.contains("泰国") || value.contains("泰國") || value.contains("泰語") || value.contains("泰语") || value.contains("ထိုင်း")) return R.drawable.ic_flag_th;
        if (isCountry(normalized, "vn", "vnm", "vietnam", "vietnamese") || value.contains("越南") || value.contains("ဗီယက်နမ်")) return R.drawable.ic_flag_vn;
        if (isCountry(normalized, "la", "lao", "laos") || value.contains("老挝") || value.contains("老撾") || value.contains("寮國") || value.contains("လာအို")) return R.drawable.ic_flag_la;
        if (isCountry(normalized, "kh", "khm", "cambodia", "khmer") || value.contains("柬埔寨") || value.contains("高棉") || value.contains("ကမ္ဘောဒီးယား") || value.contains("ခမာ")) return R.drawable.ic_flag_kh;
        if (isCountry(normalized, "my", "mys", "malaysia", "malay") || value.contains("马来西亚") || value.contains("馬來西亞") || value.contains("马来语") || value.contains("馬來語") || value.contains("မလေး")) return R.drawable.ic_flag_my;
        if (isCountry(normalized, "sg", "sgp", "singapore") || value.contains("新加坡") || value.contains("စင်ကာပူ")) return R.drawable.ic_flag_sg;
        if (isCountry(normalized, "id", "idn", "indonesia", "indonesian") || value.contains("印度尼西亚") || value.contains("印尼")) return R.drawable.ic_flag_id;
        if (isCountry(normalized, "ph", "phl", "philippines", "filipino", "tagalog") || value.contains("菲律宾") || value.contains("菲律賓") || value.contains("他加禄")) return R.drawable.ic_flag_ph;
        if (isCountry(normalized, "bn", "brn", "brunei", "bruneian") || value.contains("文莱") || value.contains("汶萊")) return R.drawable.ic_flag_bn;

        // 欧洲
        if (isCountry(normalized, "gb", "gbr", "uk", "unitedkingdom", "greatbritain", "britain", "british", "england") || value.contains("英国") || value.contains("英國") || value.contains("不列颠")) return R.drawable.ic_flag_gb;
        if (isCountry(normalized, "fr", "fra", "france", "french") || value.contains("法国") || value.contains("法國") || value.contains("法语") || value.contains("法語")) return R.drawable.ic_flag_fr;
        if (isCountry(normalized, "de", "deu", "ger", "germany", "german", "deutschland") || value.contains("德国") || value.contains("德國") || value.contains("德语") || value.contains("德語")) return R.drawable.ic_flag_de;
        if (isCountry(normalized, "it", "ita", "italy", "italian") || value.contains("意大利") || value.contains("義大利") || value.contains("意语") || value.contains("意語")) return R.drawable.ic_flag_it;
        if (isCountry(normalized, "es", "esp", "spain", "spanish") || value.contains("西班牙") || value.contains("西语") || value.contains("西語")) return R.drawable.ic_flag_es;
        if (isCountry(normalized, "ru", "rus", "russia", "russian") || value.contains("俄罗斯") || value.contains("俄羅斯") || value.contains("俄语") || value.contains("俄語")) return R.drawable.ic_flag_ru;
        if (isCountry(normalized, "nl", "nld", "netherlands", "holland", "dutch") || value.contains("荷兰") || value.contains("荷蘭")) return R.drawable.ic_flag_nl;
        if (isCountry(normalized, "ua", "ukr", "ukraine", "ukrainian") || value.contains("乌克兰") || value.contains("烏克蘭")) return R.drawable.ic_flag_ua;
        if (isCountry(normalized, "tr", "tur", "turkey", "turkiye", "türkiye", "turkish") || value.contains("土耳其")) return R.drawable.ic_flag_tr;
        if (isCountry(normalized, "pl", "pol", "poland", "polish") || value.contains("波兰") || value.contains("波蘭")) return R.drawable.ic_flag_pl;
        if (isCountry(normalized, "gr", "grc", "greece", "greek") || value.contains("希腊") || value.contains("希臘")) return R.drawable.ic_flag_gr;

        // 中东 / 北非常用
        if (isCountry(normalized, "ae", "are", "uae", "unitedarabemirates", "emirates") || value.contains("阿联酋") || value.contains("阿聯酋")) return R.drawable.ic_flag_ae;
        if (isCountry(normalized, "sa", "sau", "saudi", "saudiarabia", "arabia") || value.contains("沙特")) return R.drawable.ic_flag_sa;
        if (isCountry(normalized, "qa", "qat", "qatar", "qatari") || value.contains("卡塔尔") || value.contains("卡塔爾")) return R.drawable.ic_flag_qa;
        if (isCountry(normalized, "ir", "irn", "iran", "iranian", "persian") || value.contains("伊朗") || value.contains("波斯")) return R.drawable.ic_flag_ir;
        if (isCountry(normalized, "il", "isr", "israel", "israeli", "hebrew") || value.contains("以色列") || value.contains("希伯来") || value.contains("希伯來")) return R.drawable.ic_flag_il;
        if (isCountry(normalized, "kw", "kwt", "kuwait", "kuwaiti") || value.contains("科威特")) return R.drawable.ic_flag_kw;
        if (isCountry(normalized, "eg", "egy", "egypt", "egyptian") || value.contains("埃及")) return R.drawable.ic_flag_eg;
        if (isCountry(normalized, "jo", "jor", "jordan", "jordanian") || value.contains("约旦") || value.contains("約旦")) return R.drawable.ic_flag_jo;

        // 南美洲 / 拉美常用
        if (isCountry(normalized, "br", "bra", "brazil", "brazilian", "portuguese") || value.contains("巴西") || value.contains("葡萄牙语") || value.contains("葡萄牙語")) return R.drawable.ic_flag_br;
        if (isCountry(normalized, "ar", "arg", "argentina", "argentine", "argentinian") || value.contains("阿根廷")) return R.drawable.ic_flag_ar;
        if (isCountry(normalized, "cl", "chl", "chile", "chilean") || value.contains("智利")) return R.drawable.ic_flag_cl;
        if (isCountry(normalized, "pe", "per", "peru", "peruvian") || value.contains("秘鲁") || value.contains("秘魯")) return R.drawable.ic_flag_pe;
        if (isCountry(normalized, "co", "col", "colombia", "colombian") || value.contains("哥伦比亚") || value.contains("哥倫比亞")) return R.drawable.ic_flag_co;
        if (isCountry(normalized, "ve", "ven", "venezuela", "venezuelan") || value.contains("委内瑞拉") || value.contains("委內瑞拉")) return R.drawable.ic_flag_ve;
        if (isCountry(normalized, "mx", "mex", "mexico", "mexican") || value.contains("墨西哥")) return R.drawable.ic_flag_mx;
        if (isCountry(normalized, "uy", "ury", "uruguay", "uruguayan") || value.contains("乌拉圭") || value.contains("烏拉圭")) return R.drawable.ic_flag_uy;

        if (isCountry(normalized, "other", "others") || value.contains("其他") || value.contains("其它") || value.contains("အခြား")) return R.drawable.ic_flag_other;

        if (normalized.length() == 2 || normalized.length() == 3) {
            int flagRes = countryCodeToFlagRes(normalized.toUpperCase(Locale.US));
            return flagRes == 0 ? R.drawable.ic_flag_other : flagRes;
        }
        return 0;
    }

    private boolean isCountry(String normalized, String... values) {
        if (TextUtils.isEmpty(normalized) || values == null) return false;
        for (String item : values) {
            if (!TextUtils.isEmpty(item) && normalized.equals(item.toLowerCase(Locale.US).replace(" ", "").replace("-", "").replace("_", ""))) return true;
        }
        return false;
    }

    private String extractFirstEmojiFlagCountryCode(String value) {
        if (TextUtils.isEmpty(value)) return "";
        for (int offset = 0; offset < value.length(); ) {
            int first = value.codePointAt(offset);
            int nextOffset = offset + Character.charCount(first);
            if (nextOffset >= value.length()) break;
            int second = value.codePointAt(nextOffset);
            if (isRegionalIndicator(first) && isRegionalIndicator(second)) {
                char firstChar = (char) ('A' + first - 0x1F1E6);
                char secondChar = (char) ('A' + second - 0x1F1E6);
                return "" + firstChar + secondChar;
            }
            offset = nextOffset;
        }
        return "";
    }

    private boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    private int countryCodeToFlagRes(String code) {
        if (TextUtils.isEmpty(code)) return 0;
        String upper = code.trim().toUpperCase(Locale.US);
        switch (upper) {
            case "CN": case "CHN": return R.drawable.ic_flag_cn;
            case "US": case "USA": return R.drawable.ic_flag_us;
            case "JP": case "JPN": return R.drawable.ic_flag_jp;
            case "KR": case "KOR": return R.drawable.ic_flag_kr;
            case "MM": case "MYA": return R.drawable.ic_flag_mm;
            case "TH": case "THA": return R.drawable.ic_flag_th;
            case "VN": case "VNM": return R.drawable.ic_flag_vn;
            case "LA": case "LAO": return R.drawable.ic_flag_la;
            case "KH": case "KHM": return R.drawable.ic_flag_kh;
            case "MY": case "MYS": return R.drawable.ic_flag_my;
            case "SG": case "SGP": return R.drawable.ic_flag_sg;
            case "ID": case "IDN": return R.drawable.ic_flag_id;
            case "PH": case "PHL": return R.drawable.ic_flag_ph;
            case "BN": case "BRN": return R.drawable.ic_flag_bn;
            case "GB": case "GBR": case "UK": return R.drawable.ic_flag_gb;
            case "FR": case "FRA": return R.drawable.ic_flag_fr;
            case "DE": case "DEU": case "GER": return R.drawable.ic_flag_de;
            case "IT": case "ITA": return R.drawable.ic_flag_it;
            case "ES": case "ESP": return R.drawable.ic_flag_es;
            case "RU": case "RUS": return R.drawable.ic_flag_ru;
            case "NL": case "NLD": return R.drawable.ic_flag_nl;
            case "UA": case "UKR": return R.drawable.ic_flag_ua;
            case "TR": case "TUR": return R.drawable.ic_flag_tr;
            case "PL": case "POL": return R.drawable.ic_flag_pl;
            case "GR": case "GRC": return R.drawable.ic_flag_gr;
            case "AE": case "ARE": return R.drawable.ic_flag_ae;
            case "SA": case "SAU": return R.drawable.ic_flag_sa;
            case "QA": case "QAT": return R.drawable.ic_flag_qa;
            case "IR": case "IRN": return R.drawable.ic_flag_ir;
            case "IL": case "ISR": return R.drawable.ic_flag_il;
            case "KW": case "KWT": return R.drawable.ic_flag_kw;
            case "EG": case "EGY": return R.drawable.ic_flag_eg;
            case "JO": case "JOR": return R.drawable.ic_flag_jo;
            case "BR": case "BRA": return R.drawable.ic_flag_br;
            case "AR": case "ARG": return R.drawable.ic_flag_ar;
            case "CL": case "CHL": return R.drawable.ic_flag_cl;
            case "PE": case "PER": return R.drawable.ic_flag_pe;
            case "CO": case "COL": return R.drawable.ic_flag_co;
            case "VE": case "VEN": return R.drawable.ic_flag_ve;
            case "MX": case "MEX": return R.drawable.ic_flag_mx;
            case "UY": case "URY": return R.drawable.ic_flag_uy;
            default: return 0;
        }
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

    private void bindChannelKey(String channelID, byte channelType) {
        boundChannelKey = buildChannelKey(channelID, channelType);
        boundDisplayKey = "channel_" + boundChannelKey;
    }

    private String bindStandaloneDisplayKey(String type, String value, String avatarCacheKey, String fallbackSeed) {
        String displayKey = type + "_" + safeString(value) + "_" + safeString(avatarCacheKey) + "_" + safeString(fallbackSeed);
        boundDisplayKey = displayKey;
        return displayKey;
    }

    private void clearBoundKeys() {
        boundChannelKey = "";
        boundDisplayKey = "";
    }

    private String buildChannelKey(String channelID, byte channelType) {
        String uid = WKConfig.getInstance().getUid();
        return safeString(uid) + "_" + channelType + "_" + safeString(channelID);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private boolean isLocalFilePath(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return url.startsWith("/") || url.startsWith("file://");
    }

    private void tryFetchPersonalChannelCountry(String channelID, byte channelType) {
        if (TextUtils.isEmpty(channelID) || channelType != WKChannelType.PERSONAL) return;
        String uid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(uid) && channelID.equals(uid)) return;

        String key = buildChannelKey(channelID, channelType);
        synchronized (COUNTRY_FETCH_LOCK) {
            if (FETCHED_PERSONAL_COUNTRY_KEYS.containsKey(key)) return;
            if (!FETCHING_PERSONAL_COUNTRY_KEYS.add(key)) return;
        }

        WKCommonModel.getInstance().getChannel(channelID, channelType, (code, msg, entity) -> {
            WKChannel refreshed = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
            String country = getChannelCountry(refreshed);
            boolean success = entity != null || !TextUtils.isEmpty(country);

            synchronized (COUNTRY_FETCH_LOCK) {
                FETCHING_PERSONAL_COUNTRY_KEYS.remove(key);
                if (success) {
                    FETCHED_PERSONAL_COUNTRY_KEYS.put(key, true);
                }
            }

            if (TextUtils.isEmpty(country)) return;
            post(() -> {
                if (TextUtils.equals(key, boundChannelKey)) {
                    updateFlagByCountry(country);
                }
            });
        });
    }

    public static void clearPersonalCountryFetchCache() {
        synchronized (COUNTRY_FETCH_LOCK) {
            FETCHING_PERSONAL_COUNTRY_KEYS.clear();
            FETCHED_PERSONAL_COUNTRY_KEYS.clear();
        }
        FLAG_RES_CACHE.clear();
    }

    private String getAvatarURL(String channelID, byte channelType) {
        String filePath = WKConstants.avatarCacheDir + channelType + "_" + channelID;
        File file = new File(filePath);
        if (file.exists()) {
            return filePath;
        } else {
            return WKApiConfig.getShowAvatar(channelID, channelType);
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
