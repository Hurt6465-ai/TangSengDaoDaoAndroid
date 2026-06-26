package com.chat.base.ui.components;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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

    private static final float FLAG_SIZE_RATIO = 0.30f;
    private static final float FLAG_EDGE_INSET_RATIO = 0.00f;
    private static final float FLAG_CUTOUT_EXTRA_DP = 0.35f;
    private static final float ONLINE_SPOT_SIZE_RATIO = 0.15f;
    private static final float ONLINE_SPOT_INSET_RATIO = 0.05f;
    private static final float ONLINE_SPOT_CUTOUT_EXTRA_DP = 0.25f;
    private static final int ONLINE_SPOT_MIN_SIZE_DP = 5;
    private static final int FLAG_MIN_SIZE_DP = 11;
    private static final int FLAG_DEFAULT_SIZE_DP = 12;
    private static final String PROFILE_EXTRA_PREF = "front_profile_extra";

    // Last-online text is allowed, but it must not live forever.
    // Old cached lastOffline/lastSeen values previously kept rendering as MM-dd forever.
    private static final long LAST_ONLINE_MAX_DISPLAY_MS = 7L * 24L * 60L * 60L * 1000L;

    private static final Object COUNTRY_FETCH_LOCK = new Object();
    private static final int FETCHED_PERSONAL_COUNTRY_MAX_SIZE = 3000;
    private static final int FAILED_PERSONAL_COUNTRY_MAX_SIZE = 3000;
    private static final long COUNTRY_FETCH_FAIL_RETRY_MS = 60_000L;
    private static final Set<String> FETCHING_PERSONAL_COUNTRY_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Map<String, Boolean> FETCHED_PERSONAL_COUNTRY_KEYS = new LinkedHashMap<String, Boolean>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > FETCHED_PERSONAL_COUNTRY_MAX_SIZE;
        }
    };
    private static final Map<String, Long> FAILED_PERSONAL_COUNTRY_FETCH_TIME = new LinkedHashMap<String, Long>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > FAILED_PERSONAL_COUNTRY_MAX_SIZE;
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
    private final Paint embeddedCutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean drawingWithoutEmbeddedOverlays = false;
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
        embeddedCutoutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

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
        onlineTv.setSingleLine(true);
        onlineTv.setIncludeFontPadding(false);
        onlineTv.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        onlineTv.setBackgroundResource(R.drawable.online_bg);
        onlineTv.setVisibility(GONE);

        flagIv = new ImageView(getContext());
        flagIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        flagIv.setAdjustViewBounds(false);
        applyFlagStyle();
        flagIv.setVisibility(GONE);

        addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        addView(defaultAvatarTv, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        addView(flagIv, LayoutHelper.createFrame(FLAG_DEFAULT_SIZE_DP, FLAG_DEFAULT_SIZE_DP, Gravity.BOTTOM | Gravity.START, 0, 0, 0, 0));
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
        flagIv.setAlpha(1f);
        flagIv.setColorFilter(null);
        flagIv.setBackground(null);
        flagIv.setPadding(0, 0, 0, 0);
        flagIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        flagIv.bringToFront();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!shouldDrawEmbeddedFlag() && !shouldDrawEmbeddedOnlineSpot()) {
            super.dispatchDraw(canvas);
            return;
        }

        int saveCount = canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        drawingWithoutEmbeddedOverlays = true;
        super.dispatchDraw(canvas);
        drawingWithoutEmbeddedOverlays = false;

        if (shouldDrawEmbeddedFlag()) {
            drawCircleCutout(canvas, flagIv, FLAG_CUTOUT_EXTRA_DP);
        }
        if (shouldDrawEmbeddedOnlineSpot()) {
            drawCircleCutout(canvas, spotView, ONLINE_SPOT_CUTOUT_EXTRA_DP);
        }

        if (shouldDrawEmbeddedFlag()) {
            drawChild(canvas, flagIv, getDrawingTime());
        }
        if (shouldDrawEmbeddedOnlineSpot()) {
            drawChild(canvas, spotView, getDrawingTime());
        }
        canvas.restoreToCount(saveCount);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (drawingWithoutEmbeddedOverlays && (child == flagIv || child == spotView)) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private boolean shouldDrawEmbeddedFlag() {
        return flagIv != null
                && flagIv.getVisibility() == VISIBLE
                && currentFlagResId != 0
                && flagIv.getDrawable() != null
                && flagIv.getWidth() > 0
                && flagIv.getHeight() > 0
                && getWidth() > 0
                && getHeight() > 0;
    }

    private boolean shouldDrawEmbeddedOnlineSpot() {
        return spotView != null
                && spotView.getVisibility() == VISIBLE
                && spotView.getWidth() > 0
                && spotView.getHeight() > 0
                && getWidth() > 0
                && getHeight() > 0;
    }

    private void drawCircleCutout(Canvas canvas, View target, float extraDp) {
        float cx = target.getLeft() + target.getWidth() / 2f;
        float cy = target.getTop() + target.getHeight() / 2f;
        float radius = Math.max(target.getWidth(), target.getHeight()) / 2f + AndroidUtilities.dp(extraDp);
        canvas.drawCircle(cx, cy, radius, embeddedCutoutPaint);
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
        int flagSize = Math.max(FLAG_MIN_SIZE_DP, Math.round(size * FLAG_SIZE_RATIO));
        flagParams.width = AndroidUtilities.dp(flagSize);
        flagParams.height = AndroidUtilities.dp(flagSize);
        flagParams.gravity = Gravity.BOTTOM | Gravity.START;
        int flagEdgeInset = Math.round(size * FLAG_EDGE_INSET_RATIO);
        flagParams.leftMargin = AndroidUtilities.dp(flagEdgeInset);
        flagParams.bottomMargin = AndroidUtilities.dp(flagEdgeInset);
        flagIv.setLayoutParams(flagParams);
        applyFlagStyle();

        int spotSize = Math.max(ONLINE_SPOT_MIN_SIZE_DP, Math.round(size * ONLINE_SPOT_SIZE_RATIO));
        int spotInset = Math.max(1, Math.round(size * ONLINE_SPOT_INSET_RATIO));
        FrameLayout.LayoutParams spotParams = (FrameLayout.LayoutParams) spotView.getLayoutParams();
        spotParams.width = AndroidUtilities.dp(spotSize);
        spotParams.height = AndroidUtilities.dp(spotSize);
        spotParams.gravity = Gravity.TOP | Gravity.END;
        spotParams.rightMargin = AndroidUtilities.dp(spotInset);
        spotParams.topMargin = AndroidUtilities.dp(spotInset);
        spotView.setLayoutParams(spotParams);

        FrameLayout.LayoutParams onlineParams = (FrameLayout.LayoutParams) onlineTv.getLayoutParams();
        onlineParams.gravity = Gravity.BOTTOM | Gravity.END;
        onlineParams.rightMargin = 0;
        onlineParams.bottomMargin = 0;
        onlineTv.setLayoutParams(onlineParams);

        onlineTv.bringToFront();
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

        updateOnlineStatusView(channel, showOnlineStatus);
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
        if (currentFlagResId != flagResId || flagIv.getDrawable() == null) {
            flagIv.setImageResource(flagResId);
            currentFlagResId = flagResId;
            flagIv.invalidate();
        }
        if (flagIv.getVisibility() != VISIBLE) {
            flagIv.setVisibility(VISIBLE);
        }
        flagIv.bringToFront();
        invalidate();
    }

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
            invalidate();
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
        if (value.startsWith("馃實") || value.startsWith("馃寧") || value.startsWith("馃審")) {
            return R.drawable.ic_flag_other;
        }

        String normalized = value.toLowerCase(Locale.US)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .replace("/", "");

        if (isCountry(normalized, "cn", "chn", "china", "chinese", "prc") || value.contains("涓浗") || value.contains("涓湅") || value.contains("涓枃") || value.contains("醼愥�涐��愥��")) return R.drawable.ic_flag_cn;
        if (isCountry(normalized, "us", "usa", "unitedstates", "unitedstatesofamerica", "america", "american", "english") || value.contains("缇庡浗") || value.contains("缇庡湅") || value.contains("鑻辫") || value.contains("鑻辫獮") || value.contains("醼♂�欋�贬�涐���醼斸��") || value.contains("醼♂�勧�横�贯�傖�溼��曖��")) return R.drawable.ic_flag_us;
        if (isCountry(normalized, "jp", "jpn", "japan", "japanese") || value.contains("鏃ユ湰") || value.contains("醼傖�会�曖�斸��")) return R.drawable.ic_flag_jp;
        if (isCountry(normalized, "kr", "kor", "korea", "southkorea", "republicofkorea", "korean") || value.contains("闊╁浗") || value.contains("闊撳湅") || value.contains("醼�醼��涐��羔�氠���")) return R.drawable.ic_flag_kr;

        if (isCountry(normalized, "mm", "mya", "myanmar", "burma", "burmese") || value.contains("缂呯敻") || value.contains("绶敻") || value.contains("醼欋�坚�斸�横�欋��")) return R.drawable.ic_flag_mm;
        if (isCountry(normalized, "th", "tha", "thailand", "thai") || value.contains("娉板浗") || value.contains("娉板湅") || value.contains("娉拌獮") || value.contains("娉拌") || value.contains("醼戓���勧�横��")) return R.drawable.ic_flag_th;
        if (isCountry(normalized, "vn", "vnm", "vietnam", "vietnamese") || value.contains("瓒婂崡") || value.contains("醼椺��氠��醼横�斸�欋��")) return R.drawable.ic_flag_vn;
        if (isCountry(normalized, "la", "lao", "laos") || value.contains("鑰佹対") || value.contains("鑰佹捑") || value.contains("瀵湅") || value.contains("醼溼��♂���")) return R.drawable.ic_flag_la;
        if (isCountry(normalized, "kh", "khm", "cambodia", "khmer") || value.contains("鏌煍瀵�") || value.contains("楂樻") || value.contains("醼�醼欋�贯�樶�贬��掅��羔�氠���") || value.contains("醼佱�欋��")) return R.drawable.ic_flag_kh;
        if (isCountry(normalized, "my", "mys", "malaysia", "malay") || value.contains("椹潵瑗夸簹") || value.contains("棣締瑗夸簽") || value.contains("椹潵璇�") || value.contains("棣締瑾�") || value.contains("醼欋�溼�贬��")) return R.drawable.ic_flag_my;
        if (isCountry(normalized, "sg", "sgp", "singapore") || value.contains("鏂板姞鍧�") || value.contains("醼呩�勧�横��醼�曖��")) return R.drawable.ic_flag_sg;
        if (isCountry(normalized, "id", "idn", "indonesia", "indonesian") || value.contains("鍗板害灏艰タ浜�") || value.contains("鍗板凹")) return R.drawable.ic_flag_id;
        if (isCountry(normalized, "ph", "phl", "philippines", "filipino", "tagalog") || value.contains("鑿插緥瀹�") || value.contains("鑿插緥璩�") || value.contains("浠栧姞绂�")) return R.drawable.ic_flag_ph;
        if (isCountry(normalized, "bn", "brn", "brunei", "bruneian") || value.contains("鏂囪幈") || value.contains("姹惰悐")) return R.drawable.ic_flag_bn;

        if (isCountry(normalized, "gb", "gbr", "uk", "unitedkingdom", "greatbritain", "britain", "british", "england") || value.contains("鑻卞浗") || value.contains("鑻卞湅") || value.contains("涓嶅垪棰�")) return R.drawable.ic_flag_gb;
        if (isCountry(normalized, "fr", "fra", "france", "french") || value.contains("娉曞浗") || value.contains("娉曞湅") || value.contains("娉曡") || value.contains("娉曡獮")) return R.drawable.ic_flag_fr;
        if (isCountry(normalized, "de", "deu", "ger", "germany", "german", "deutschland") || value.contains("寰峰浗") || value.contains("寰峰湅") || value.contains("寰疯") || value.contains("寰疯獮")) return R.drawable.ic_flag_de;
        if (isCountry(normalized, "it", "ita", "italy", "italian") || value.contains("鎰忓ぇ鍒�") || value.contains("缇╁ぇ鍒�") || value.contains("鎰忚") || value.contains("鎰忚獮")) return R.drawable.ic_flag_it;
        if (isCountry(normalized, "es", "esp", "spain", "spanish") || value.contains("瑗跨彮鐗�") || value.contains("瑗胯") || value.contains("瑗胯獮")) return R.drawable.ic_flag_es;
        if (isCountry(normalized, "ru", "rus", "russia", "russian") || value.contains("淇勭綏鏂�") || value.contains("淇勭緟鏂�") || value.contains("淇勮") || value.contains("淇勮獮")) return R.drawable.ic_flag_ru;
        if (isCountry(normalized, "nl", "nld", "netherlands", "holland", "dutch") || value.contains("鑽峰叞") || value.contains("鑽疯槶")) return R.drawable.ic_flag_nl;
        if (isCountry(normalized, "ua", "ukr", "ukraine", "ukrainian") || value.contains("涔屽厠鍏�") || value.contains("鐑忓厠铇�")) return R.drawable.ic_flag_ua;
        if (isCountry(normalized, "tr", "tur", "turkey", "turkiye", "t眉rkiye", "turkish") || value.contains("鍦熻�冲叾")) return R.drawable.ic_flag_tr;
        if (isCountry(normalized, "pl", "pol", "poland", "polish") || value.contains("娉㈠叞") || value.contains("娉㈣槶")) return R.drawable.ic_flag_pl;
        if (isCountry(normalized, "gr", "grc", "greece", "greek") || value.contains("甯岃厞") || value.contains("甯岃嚇")) return R.drawable.ic_flag_gr;

        if (isCountry(normalized, "ae", "are", "uae", "unitedarabemirates", "emirates") || value.contains("闃胯仈閰�") || value.contains("闃胯伅閰�")) return R.drawable.ic_flag_ae;
        if (isCountry(normalized, "sa", "sau", "saudi", "saudiarabia", "arabia") || value.contains("娌欑壒")) return R.drawable.ic_flag_sa;
        if (isCountry(normalized, "qa", "qat", "qatar", "qatari") || value.contains("鍗″灏�") || value.contains("鍗″鐖�")) return R.drawable.ic_flag_qa;
        if (isCountry(normalized, "ir", "irn", "iran", "iranian", "persian") || value.contains("浼婃湕") || value.contains("娉㈡柉")) return R.drawable.ic_flag_ir;
        if (isCountry(normalized, "il", "isr", "israel", "israeli", "hebrew") || value.contains("浠ヨ壊鍒�") || value.contains("甯屼集鏉�") || value.contains("甯屼集渚�")) return R.drawable.ic_flag_il;
        if (isCountry(normalized, "kw", "kwt", "kuwait", "kuwaiti") || value.contains("绉戝▉鐗�")) return R.drawable.ic_flag_kw;
        if (isCountry(normalized, "eg", "egy", "egypt", "egyptian") || value.contains("鍩冨強")) return R.drawable.ic_flag_eg;
        if (isCountry(normalized, "jo", "jor", "jordan", "jordanian") || value.contains("绾︽棪") || value.contains("绱勬棪")) return R.drawable.ic_flag_jo;

        if (isCountry(normalized, "br", "bra", "brazil", "brazilian", "portuguese") || value.contains("宸磋タ") || value.contains("钁¤悇鐗欒") || value.contains("钁¤悇鐗欒獮")) return R.drawable.ic_flag_br;
        if (isCountry(normalized, "ar", "arg", "argentina", "argentine", "argentinian") || value.contains("闃挎牴寤�")) return R.drawable.ic_flag_ar;
        if (isCountry(normalized, "cl", "chl", "chile", "chilean") || value.contains("鏅哄埄")) return R.drawable.ic_flag_cl;
        if (isCountry(normalized, "pe", "per", "peru", "peruvian") || value.contains("绉橀瞾") || value.contains("绉橀")) return R.drawable.ic_flag_pe;
        if (isCountry(normalized, "co", "col", "colombia", "colombian") || value.contains("鍝ヤ鸡姣斾簹") || value.contains("鍝ュ�瘮浜�")) return R.drawable.ic_flag_co;
        if (isCountry(normalized, "ve", "ven", "venezuela", "venezuelan") || value.contains("濮斿唴鐟炴媺") || value.contains("濮斿収鐟炴媺")) return R.drawable.ic_flag_ve;
        if (isCountry(normalized, "mx", "mex", "mexico", "mexican") || value.contains("澧ㄨタ鍝�")) return R.drawable.ic_flag_mx;
        if (isCountry(normalized, "uy", "ury", "uruguay", "uruguayan") || value.contains("涔屾媺鍦�") || value.contains("鐑忔媺鍦�")) return R.drawable.ic_flag_uy;

        if (isCountry(normalized, "other", "others") || value.contains("鍏朵粬") || value.contains("鍏跺畠") || value.contains("醼♂�佱�坚���")) return R.drawable.ic_flag_other;

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
        long now = System.currentTimeMillis();
        synchronized (COUNTRY_FETCH_LOCK) {
            if (FETCHED_PERSONAL_COUNTRY_KEYS.containsKey(key)) return;
            Long lastFailedTime = FAILED_PERSONAL_COUNTRY_FETCH_TIME.get(key);
            if (lastFailedTime != null && now - lastFailedTime < COUNTRY_FETCH_FAIL_RETRY_MS) return;
            if (!FETCHING_PERSONAL_COUNTRY_KEYS.add(key)) return;
        }

        WKCommonModel.getInstance().getChannel(channelID, channelType, (code, msg, entity) -> {
            WKChannel refreshed = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
            String country = firstNotEmpty(getChannelCountry(refreshed), getEntityCountry(entity));
            int flagResId = countryToFlagRes(country);

            synchronized (COUNTRY_FETCH_LOCK) {
                FETCHING_PERSONAL_COUNTRY_KEYS.remove(key);
                if (flagResId != 0) {
                    FETCHED_PERSONAL_COUNTRY_KEYS.put(key, true);
                    FAILED_PERSONAL_COUNTRY_FETCH_TIME.remove(key);
                } else {
                    FAILED_PERSONAL_COUNTRY_FETCH_TIME.put(key, System.currentTimeMillis());
                }
            }

            if (flagResId == 0) return;
            post(() -> {
                if (TextUtils.equals(key, boundChannelKey)) {
                    updateFlagByCountry(country);
                }
            });
        });
    }

    private String getEntityCountry(Object entity) {
        if (entity == null) return "";
        String directCountry = firstNotEmpty(
                getObjectString(entity, "country_code"),
                getObjectString(entity, "countryCode"),
                getObjectString(entity, "country"),
                getObjectString(entity, "nationality_code"),
                getObjectString(entity, "nationality")
        );
        if (!TextUtils.isEmpty(directCountry)) return directCountry;

        Object localExtra = getObjectValue(entity, "localExtra");
        Object remoteExtra = getObjectValue(entity, "remoteExtraMap");
        return firstNotEmpty(
                getExtraStringFromObjectMap(localExtra, "country_code"),
                getExtraStringFromObjectMap(remoteExtra, "country_code"),
                getExtraStringFromObjectMap(localExtra, "countryCode"),
                getExtraStringFromObjectMap(remoteExtra, "countryCode"),
                getExtraStringFromObjectMap(localExtra, "country"),
                getExtraStringFromObjectMap(remoteExtra, "country"),
                getExtraStringFromObjectMap(localExtra, "nationality_code"),
                getExtraStringFromObjectMap(remoteExtra, "nationality_code"),
                getExtraStringFromObjectMap(localExtra, "nationality"),
                getExtraStringFromObjectMap(remoteExtra, "nationality")
        );
    }

    private String getObjectString(Object target, String name) {
        Object value = getObjectValue(target, name);
        if (value == null) return "";
        String str = String.valueOf(value);
        return "null".equalsIgnoreCase(str) ? "" : str;
    }

    private Object getObjectValue(Object target, String name) {
        if (target == null || TextUtils.isEmpty(name)) return null;
        try {
            java.lang.reflect.Field field = target.getClass().getField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
        }
        String suffix = name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
        try {
            java.lang.reflect.Method method = target.getClass().getMethod("get" + suffix);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String getExtraStringFromObjectMap(Object mapObj, String key) {
        if (!(mapObj instanceof Map) || TextUtils.isEmpty(key)) return "";
        Object value = ((Map<String, Object>) mapObj).get(key);
        if (value == null) return "";
        String str = String.valueOf(value);
        return "null".equalsIgnoreCase(str) ? "" : str;
    }

    private void updateOnlineStatusView(WKChannel channel, boolean showOnlineStatus) {
        resetStatusViews();
        if (!showOnlineStatus || channel == null) return;

        if (channel.online == 1) {
            spotView.setVisibility(VISIBLE);
            spotView.bringToFront();
            return;
        }

        String lastOnlineText = getLastOnlineText(channel);
        if (!TextUtils.isEmpty(lastOnlineText)) {
            onlineTv.setText(lastOnlineText);
            onlineTv.setVisibility(VISIBLE);
            onlineTv.bringToFront();
        }
    }

    private String getLastOnlineText(WKChannel channel) {
        Object raw = getFirstObjectValue(channel,
                "lastOffline",
                "lastOfflineTime",
                "lastOfflineAt",
                "lastSeen",
                "lastSeenTime",
                "lastSeenAt",
                "lastOnline",
                "lastOnlineTime",
                "lastOnlineAt",
                "lastActiveTime",
                "lastActiveAt");
        String text = formatOnlineTime(raw);
        if (!TextUtils.isEmpty(text)) return text;

        String extra = firstNotEmpty(
                getExtraString(channel.localExtra, "last_offline"),
                getExtraString(channel.remoteExtraMap, "last_offline"),
                getExtraString(channel.localExtra, "last_offline_time"),
                getExtraString(channel.remoteExtraMap, "last_offline_time"),
                getExtraString(channel.localExtra, "last_seen"),
                getExtraString(channel.remoteExtraMap, "last_seen"),
                getExtraString(channel.localExtra, "last_online"),
                getExtraString(channel.remoteExtraMap, "last_online"),
                getExtraString(channel.localExtra, "last_active_time"),
                getExtraString(channel.remoteExtraMap, "last_active_time"),
                getExtraString(channel.localExtra, "online_text"),
                getExtraString(channel.remoteExtraMap, "online_text")
        );
        return formatOnlineTime(extra);
    }

    private Object getFirstObjectValue(Object target, String... names) {
        if (target == null || names == null) return null;
        for (String name : names) {
            Object value = getObjectValue(target, name);
            if (value != null) return value;
        }
        return null;
    }

    private String formatOnlineTime(Object raw) {
        if (raw == null) return "";
        if (raw instanceof java.util.Date) {
            return formatOnlineTimestamp(((java.util.Date) raw).getTime());
        }
        if (raw instanceof Number) {
            return formatOnlineTimestamp(normalizeTimestamp(((Number) raw).longValue()));
        }

        String value = String.valueOf(raw).trim();
        if (TextUtils.isEmpty(value) || "null".equalsIgnoreCase(value) || "0".equals(value)) return "";

        long timestamp = parseTimestampFromString(value);
        if (timestamp > 0) {
            return formatOnlineTimestamp(timestamp);
        }

        // Do not display raw cached strings such as "鏄ㄥぉ", "3灏忔椂鍓�" or "online_text".
        // Those strings have no reliable timestamp, so a cached channel can make them appear forever.
        return "";
    }

    private long normalizeTimestamp(long timestamp) {
        if (timestamp <= 0) return 0;
        return timestamp < 100000000000L ? timestamp * 1000L : timestamp;
    }

    private long parseTimestampFromString(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        try {
            if (value.matches("^\\d{10,13}$")) {
                return normalizeTimestamp(Long.parseLong(value));
            }
        } catch (Exception ignored) {
        }

        String normalized = value.replace("T", " ").replace("Z", "").trim();
        String[] patterns = new String[]{
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(pattern, Locale.getDefault());
                java.util.Date date = format.parse(normalized);
                if (date != null) return date.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private String formatOnlineTimestamp(long timestampMs) {
        if (timestampMs <= 0) return "";
        long diff = System.currentTimeMillis() - timestampMs;
        if (diff < 0) diff = 0;

        // Important fix: do not show very old offline timestamps on the avatar forever.
        if (diff > LAST_ONLINE_MAX_DISPLAY_MS) {
            return "";
        }

        long minute = 60_000L;
        long hour = 60L * minute;
        long day = 24L * hour;

        if (diff < minute) return "鍒氬垰";
        if (diff < hour) return (diff / minute) + "鍒嗛挓鍓�";
        if (diff < day) return (diff / hour) + "灏忔椂鍓�";
        if (diff < 2L * day) return "鏄ㄥぉ";
        if (diff < 7L * day) return (diff / day) + "澶╁墠";
        return "";
    }

    public static void clearPersonalCountryFetchCache() {
        synchronized (COUNTRY_FETCH_LOCK) {
            FETCHING_PERSONAL_COUNTRY_KEYS.clear();
            FETCHED_PERSONAL_COUNTRY_KEYS.clear();
            FAILED_PERSONAL_COUNTRY_FETCH_TIME.clear();
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
