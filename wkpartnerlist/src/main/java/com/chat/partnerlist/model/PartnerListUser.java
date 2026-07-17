package com.chat.partnerlist.model;

import android.text.TextUtils;

import com.chat.partnerlist.PartnerListTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class PartnerListUser {
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Shanghai");

    public String uid;
    public String id;
    public String name;
    public String username;
    public String avatar;
    public int sex;
    public String birthday;
    public String intro;
    public String country_code;
    public String country;
    public List<String> native_languages;
    public List<String> learning_languages;
    public List<String> tags;
    public String profile_cover;
    public List<String> profile_images;
    public String vercode;
    public int online;
    public int last_offline;
    public long last_active_at;

    // 推荐接口兼容字段。created_at 是唐僧用户接口常用字段；其余字段兼容不同后端命名。
    public String created_at;
    public String joined_at;
    public String registered_at;
    public String join_time;
    public long created_at_ts;
    public long joined_at_ts;
    public int is_new;
    public long profile_version;

    public String stableId() {
        if (!TextUtils.isEmpty(uid)) return uid;
        return id == null ? "" : id;
    }

    public String displayName() {
        if (!TextUtils.isEmpty(name)) return name;
        if (!TextUtils.isEmpty(username)) return username;
        return stableId();
    }

    public String displayAvatar() {
        String key = stableId();
        if (!TextUtils.isEmpty(key)) return "users/" + key + "/avatar";
        return avatar == null ? "" : avatar.trim();
    }

    public int age() {
        if (TextUtils.isEmpty(birthday) || birthday.length() < 4) return 0;
        try {
            int year = Integer.parseInt(birthday.substring(0, 4));
            int current = Calendar.getInstance().get(Calendar.YEAR);
            int age = current - year;
            return age > 0 && age < 120 ? age : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 账号注册/加入时间在指定天数内才算“新加入”。
     * 优先使用明确时间戳；旧后端未返回时间时，才兼容使用 is_new。
     */
    public boolean joinedWithinDays(long serverTime, int days) {
        if (days <= 0) return false;
        long now = PartnerListTime.normalizeMillis(serverTime);
        if (now <= 0) now = System.currentTimeMillis();

        long joined = firstPositive(
                PartnerListTime.normalizeMillis(joined_at_ts),
                parseFlexibleTime(joined_at),
                PartnerListTime.normalizeMillis(created_at_ts),
                parseFlexibleTime(created_at),
                parseFlexibleTime(registered_at),
                parseFlexibleTime(join_time)
        );

        if (joined > 0) {
            long diff = now - joined;
            // 允许服务端与本地几分钟的时钟误差，但不把明显的未来时间判为新人。
            if (diff < -10L * 60L * 1000L) return false;
            return Math.max(0L, diff) < days * DAY_MS;
        }
        return is_new == 1;
    }

    public PartnerListUser copy() {
        PartnerListUser out = new PartnerListUser();
        out.uid = uid;
        out.id = id;
        out.name = name;
        out.username = username;
        out.avatar = avatar;
        out.sex = sex;
        out.birthday = birthday;
        out.intro = intro;
        out.country_code = country_code;
        out.country = country;
        out.native_languages = native_languages == null ? new ArrayList<>() : new ArrayList<>(native_languages);
        out.learning_languages = learning_languages == null ? new ArrayList<>() : new ArrayList<>(learning_languages);
        out.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        out.profile_cover = profile_cover;
        out.profile_images = profile_images == null ? new ArrayList<>() : new ArrayList<>(profile_images);
        out.vercode = vercode;
        out.online = online;
        out.last_offline = last_offline;
        out.last_active_at = last_active_at;
        out.created_at = created_at;
        out.joined_at = joined_at;
        out.registered_at = registered_at;
        out.join_time = join_time;
        out.created_at_ts = created_at_ts;
        out.joined_at_ts = joined_at_ts;
        out.is_new = is_new;
        out.profile_version = profile_version;
        return out;
    }

    public List<String> nativeLanguages() {
        return native_languages == null ? new ArrayList<>() : native_languages;
    }

    public List<String> learningLanguages() {
        return learning_languages == null ? new ArrayList<>() : learning_languages;
    }

    public List<String> tags() {
        return tags == null ? new ArrayList<>() : tags;
    }

    private static long firstPositive(long... values) {
        if (values == null) return 0L;
        for (long value : values) if (value > 0) return value;
        return 0L;
    }

    private static long parseFlexibleTime(String value) {
        if (TextUtils.isEmpty(value)) return 0L;
        String text = value.trim();
        if (TextUtils.isEmpty(text)) return 0L;

        if (text.matches("^-?\\d+$")) {
            try {
                return PartnerListTime.normalizeMillis(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        String normalized = text.replace('/', '-');
        DateTimeFormatter[] dateTimeFormats = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        };
        for (DateTimeFormatter formatter : dateTimeFormats) {
            try {
                return LocalDateTime.parse(normalized, formatter)
                        .atZone(FALLBACK_ZONE).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(FALLBACK_ZONE).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }
}
