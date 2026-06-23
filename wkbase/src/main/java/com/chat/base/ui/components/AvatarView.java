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

import com.chat.base.R;
import com.chat.base.config.WKApiConfig;
import com.chat.base.config.WKConfig;
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
    public ImageView flagIv;
    private static final float FLAG_WIDTH_RATIO = 0.42f;
    private static final float FLAG_HEIGHT_RATIO = 2f / 3f;
    private static final int FLAG_MIN_WIDTH_DP = 16;
    private static final int FLAG_MIN_HEIGHT_DP = 11;
    private static final String PROFILE_EXTRA_PREF = "front_profile_extra";
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
        flagIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
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
        onlineTv.setVisibility(GONE);
        hideFlag();
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
        hideFlag();
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

    private void applyFlagStyle() {
        if (flagIv == null) return;
        // 稳定版不用 TextView/emoji，直接用本地 PNG 资源，避免系统 emoji 字体和 GPU 合成造成发灰/半透明。
        // 这里保持纯国旗：无圆形底、无阴影、无 tint、无 alpha、按原始 3:2 比例 FIT_CENTER 显示，不裁剪。
        flagIv.setAlpha(1f);
        flagIv.setBackground(null);
        flagIv.setColorFilter(null);
        flagIv.setPadding(0, 0, 0, 0);
        flagIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
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

        FrameLayout.LayoutParams flagParams = (FrameLayout.LayoutParams) flagIv.getLayoutParams();
        int flagWidth = Math.max(FLAG_MIN_WIDTH_DP, Math.round(size * FLAG_WIDTH_RATIO));
        int flagHeight = Math.max(FLAG_MIN_HEIGHT_DP, Math.round(flagWidth * FLAG_HEIGHT_RATIO));
        flagParams.width = AndroidUtilities.dp(flagWidth);
        flagParams.height = AndroidUtilities.dp(flagHeight);
        flagParams.gravity = Gravity.BOTTOM | Gravity.START;
        // 叠在头像左下角，左/下轻微探出，和网页截图里的位置一致。
        flagParams.leftMargin = -AndroidUtilities.dp(Math.max(2f, size * 0.08f));
        flagParams.bottomMargin = -AndroidUtilities.dp(Math.max(1f, size * 0.04f));
        flagIv.setLayoutParams(flagParams);
        applyFlagStyle();

        int spotSize = Math.max(6, Math.round(size * 0.17f));
        FrameLayout.LayoutParams spotParams = (FrameLayout.LayoutParams) spotView.getLayoutParams();
        spotParams.width = AndroidUtilities.dp(spotSize);
        spotParams.height = AndroidUtilities.dp(spotSize);
        spotParams.gravity = Gravity.TOP | Gravity.END;
        spotParams.rightMargin = 0;
        spotParams.topMargin = 0;
        spotView.setLayoutParams(spotParams);
    }

    public void showAvatar(String channelID, byte channelType, String avatarCacheKey) {
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null && isTopicRoomChannel(channel)) {
            showTopicAvatar(channel);
            return;
        }
        if (isTopicRoomId(channelID, channelType)) {
            showDefaultAvatar(channelID, channelID);
            return;
        }
        prepareImageAvatar();
        if (channel != null) {
            updateFlagView(channel);
        } else {
            updateFlagByCountry(getLocalSavedCountry(channelID));
        }
        String url = getAvatarURL(channelID, channelType);
        GlideUtils.getInstance().showAvatarImg(getContext(), url, avatarCacheKey, imageView);
    }

    public void showAvatar(String channelID, byte channelType, boolean showOnlineStatus) {
        prepareImageAvatar();
        spotView.setVisibility(GONE);
        onlineTv.setVisibility(GONE);
        hideFlag();
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null) {
            showAvatar(channel, showOnlineStatus);
        } else if (isTopicRoomId(channelID, channelType)) {
            showDefaultAvatar(channelID, channelID);
        } else {
            updateFlagByCountry(getLocalSavedCountry(channelID));
            String url = getAvatarURL(channelID, channelType);
            GlideUtils.getInstance().showAvatarImg(getContext(), url, "", imageView);
        }
    }

    public void showAvatar(String channelID, byte channelType) {
        prepareImageAvatar();
        spotView.setVisibility(GONE);
        onlineTv.setVisibility(GONE);
        hideFlag();
        WKChannel channel = WKIM.getInstance().getChannelManager().getChannel(channelID, channelType);
        if (channel != null) {
            showAvatar(channel, false);
        } else if (isTopicRoomId(channelID, channelType)) {
            showDefaultAvatar(channelID, channelID);
        } else {
            updateFlagByCountry(getLocalSavedCountry(channelID));
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
        updateFlagView(channel);
        // 头像上只显示更小的在线绿点；离线的“最后在线时间”不再显示在头像右下角。
        if (showOnlineStatus && channel.online == 1) {
            spotView.setVisibility(VISIBLE);
        } else {
            spotView.setVisibility(GONE);
        }
        onlineTv.setText("");
        onlineTv.setVisibility(GONE);
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
        updateTopicFlagView(channel);
        spotView.setVisibility(GONE);
        onlineTv.setVisibility(GONE);
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
        flagIv.setImageResource(flagResId);
        applyFlagStyle();
        flagIv.setVisibility(VISIBLE);
        flagIv.bringToFront();
    }

    /**
     * 外部列表已有国籍/国旗字段时可直接调用，比如聊天室 member.flag/country_code。
     */
    public void showFlag(String countryOrFlag) {
        updateFlagByCountry(countryOrFlag);
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
            flagIv.setImageDrawable(null);
            flagIv.setVisibility(GONE);
        }
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

        if (isCountry(normalized, "mm", "mya", "myanmar", "burma", "burmese") || value.contains("缅甸") || value.contains("မြန်မာ")) return R.drawable.ic_flag_mm;
        if (isCountry(normalized, "cn", "chn", "china", "chinese", "prc") || value.contains("中国") || value.contains("中國") || value.contains("中文") || value.contains("တရုတ်")) return R.drawable.ic_flag_cn;
        if (isCountry(normalized, "th", "tha", "thailand", "thai") || value.contains("泰国") || value.contains("泰國") || value.contains("泰語") || value.contains("泰语") || value.contains("ထိုင်း")) return R.drawable.ic_flag_th;
        if (isCountry(normalized, "jp", "jpn", "japan", "japanese") || value.contains("日本") || value.contains("ဂျပန်")) return R.drawable.ic_flag_jp;
        if (isCountry(normalized, "kr", "kor", "korea", "southkorea", "republicofkorea", "korean") || value.contains("韩国") || value.contains("韓國") || value.contains("ကိုရီးယား")) return R.drawable.ic_flag_kr;
        if (isCountry(normalized, "vn", "vnm", "vietnam", "vietnamese") || value.contains("越南") || value.contains("ဗီယက်နမ်")) return R.drawable.ic_flag_vn;
        if (isCountry(normalized, "la", "lao", "laos") || value.contains("老挝") || value.contains("寮國") || value.contains("လာအို")) return R.drawable.ic_flag_la;
        if (isCountry(normalized, "kh", "khm", "cambodia", "khmer") || value.contains("柬埔寨") || value.contains("高棉") || value.contains("ကမ္ဘောဒီးယား") || value.contains("ခမာ")) return R.drawable.ic_flag_kh;
        if (isCountry(normalized, "my", "mys", "malaysia", "malay") || value.contains("马来西亚") || value.contains("馬來西亞") || value.contains("马来语") || value.contains("မလေး")) return R.drawable.ic_flag_my;
        if (isCountry(normalized, "sg", "sgp", "singapore") || value.contains("新加坡") || value.contains("စင်ကာပူ")) return R.drawable.ic_flag_sg;
        if (isCountry(normalized, "us", "usa", "unitedstates", "america", "american", "english") || value.contains("美国") || value.contains("美國") || value.contains("英语") || value.contains("အမေရိကန်") || value.contains("အင်္ဂလိပ်")) return R.drawable.ic_flag_us;
        if (isCountry(normalized, "other", "others") || value.contains("其他") || value.contains("အခြား")) return R.drawable.ic_flag_other;

        if (normalized.length() == 2) {
            int flagRes = countryCodeToFlagRes(normalized.toUpperCase(Locale.US));
            return flagRes == 0 ? R.drawable.ic_flag_other : flagRes;
        }
        return 0;
    }

    private boolean isCountry(String normalized, String... values) {
        if (TextUtils.isEmpty(normalized) || values == null) return false;
        for (String item : values) {
            if (!TextUtils.isEmpty(item) && normalized.equals(item.toLowerCase(Locale.US).replace(" ", ""))) return true;
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
            case "MM":
            case "MYA":
                return R.drawable.ic_flag_mm;
            case "CN":
            case "CHN":
                return R.drawable.ic_flag_cn;
            case "TH":
            case "THA":
                return R.drawable.ic_flag_th;
            case "JP":
            case "JPN":
                return R.drawable.ic_flag_jp;
            case "KR":
            case "KOR":
                return R.drawable.ic_flag_kr;
            case "VN":
            case "VNM":
                return R.drawable.ic_flag_vn;
            case "LA":
            case "LAO":
                return R.drawable.ic_flag_la;
            case "KH":
            case "KHM":
                return R.drawable.ic_flag_kh;
            case "MY":
            case "MYS":
                return R.drawable.ic_flag_my;
            case "SG":
            case "SGP":
                return R.drawable.ic_flag_sg;
            case "US":
            case "USA":
                return R.drawable.ic_flag_us;
            default:
                return 0;
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
