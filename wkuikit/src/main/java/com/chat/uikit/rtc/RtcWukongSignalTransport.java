package com.chat.uikit.rtc;

import android.text.TextUtils;

import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WuKong IM transport for WebRTC signaling.
 *
 * Key requirements:
 * 1) The peer must receive invite / offer / answer / ice through the normal online message flow.
 * 2) Signaling packets must not become chat bubbles, unread badges, or cache pollution.
 *
 * Implementation notes:
 * - Keep payload as WK_TEXT. Do not switch to WK_INSIDE_MSG here; some SDK/server combinations
 *   do not deliver inside messages through the listener used by the RTC module.
 * - header.noPersist = true: online signaling, do not store as normal chat history.
 * - header.redDot = false: do not increase unread/red-dot count.
 * - Reflection uses getDeclaredField across the inheritance chain. getField only sees public
 *   fields and can fail on SDK variants or obfuscation.
 */
public class RtcWukongSignalTransport implements RtcSignalTransport {
    private static final int SIGNAL_EXPIRE_SECONDS = 5 * 60;

    // key = className#fieldName
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Field FIELD_NOT_FOUND;

    static {
        Field placeholder = null;
        try {
            placeholder = RtcWukongSignalTransport.class.getDeclaredField("SIGNAL_EXPIRE_SECONDS");
        } catch (NoSuchFieldException ignored) {
        }
        FIELD_NOT_FOUND = placeholder;
    }

    @Override
    public void sendSignal(String peerUid, String payload) throws Exception {
        if (TextUtils.isEmpty(peerUid) || TextUtils.isEmpty(payload)) return;

        WKTextContent content = new WKTextContent(payload);
        WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
        WKSendOptions options = new WKSendOptions();

        applyRealtimeSignalOptions(options);
        // Some SDK versions copy no-persist/unread flags from content rather than options.
        // Mark both sides so RTC packets never become visible chat bubbles.
        markByReflection(content);
        WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
    }

    private void applyRealtimeSignalOptions(WKSendOptions options) {
        if (options == null) return;

        options.expire = SIGNAL_EXPIRE_SECONDS;

        try {
            if (options.header != null) {
                // Online delivery, no local/remote chat cache pollution.
                options.header.noPersist = true;
                // WKMsgHeader's real unread/red-dot switch.
                options.header.redDot = false;
                // Do not set syncOnce. Multi-device users should still be able to receive calls.
            }
        } catch (Exception ignored) {
        }

        try {
            if (options.setting != null) {
                options.setting.receipt = 0;
                options.setting.stream = 0;
            }
        } catch (Exception ignored) {
        }

        // Reflection fallback for older/newer SDK variants or non-public fields.
        markByReflection(options);
        markByReflection(getFieldValue(options, "header"));
        markByReflection(getFieldValue(options, "setting"));
    }

    private void markByReflection(Object object) {
        if (object == null) return;

        // Header-like fields.
        setFieldValue(object, "noPersist", true);
        setFieldValue(object, "no_persist", true);
        setFieldValue(object, "redDot", false);
        setFieldValue(object, "red_dot", false);

        // SDK variants may use different names for unread/red-dot behavior.
        setFieldValue(object, "noUnread", true);
        setFieldValue(object, "no_unread", true);
        setFieldValue(object, "showUnread", false);
        setFieldValue(object, "show_unread", false);
        setFieldValue(object, "unread", false);
        setFieldValue(object, "needRedDot", false);
        setFieldValue(object, "need_red_dot", false);
        setFieldValue(object, "persist", false);
        setFieldValue(object, "isPersist", false);
        setFieldValue(object, "is_persist", false);

        // Setting-like fields.
        setFieldValue(object, "receipt", 0);
        setFieldValue(object, "stream", 0);
        setFieldValue(object, "expire", SIGNAL_EXPIRE_SECONDS);

        // Do not set syncOnce=true here. It can make multi-device or background invite delivery unreliable.
    }

    private Object getFieldValue(Object object, String fieldName) {
        if (object == null || TextUtils.isEmpty(fieldName)) return null;
        try {
            Field field = findField(object.getClass(), fieldName);
            if (field == null || field == FIELD_NOT_FOUND) return null;
            field.setAccessible(true);
            return field.get(object);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void setFieldValue(Object object, String fieldName, Object value) {
        if (object == null || TextUtils.isEmpty(fieldName)) return;
        try {
            Field field = findField(object.getClass(), fieldName);
            if (field == null || field == FIELD_NOT_FOUND) return;

            field.setAccessible(true);
            Class<?> type = field.getType();

            if (type == boolean.class || type == Boolean.class) {
                if (value instanceof Boolean) {
                    field.set(object, value);
                } else if (value instanceof Number) {
                    field.set(object, ((Number) value).intValue() != 0);
                } else if (value instanceof String) {
                    field.set(object, "1".equals(value) || "true".equalsIgnoreCase((String) value));
                }
                return;
            }

            if (type == int.class || type == Integer.class) {
                if (value instanceof Number) {
                    field.set(object, ((Number) value).intValue());
                } else if (value instanceof Boolean) {
                    field.set(object, ((Boolean) value) ? 1 : 0);
                } else if (value instanceof String) {
                    try {
                        field.set(object, Integer.parseInt((String) value));
                    } catch (Exception ignored) {
                    }
                }
                return;
            }

            if (type == long.class || type == Long.class) {
                if (value instanceof Number) {
                    field.set(object, ((Number) value).longValue());
                } else if (value instanceof Boolean) {
                    field.set(object, ((Boolean) value) ? 1L : 0L);
                } else if (value instanceof String) {
                    try {
                        field.set(object, Long.parseLong((String) value));
                    } catch (Exception ignored) {
                    }
                }
                return;
            }

            if (type == String.class) {
                field.set(object, String.valueOf(value));
            }
        } catch (Exception ignored) {
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        if (clazz == null || TextUtils.isEmpty(fieldName)) return FIELD_NOT_FOUND;

        String key = clazz.getName() + "#" + fieldName;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                FIELD_CACHE.put(key, field);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Exception ignored) {
                break;
            }
        }

        FIELD_CACHE.put(key, FIELD_NOT_FOUND);
        return FIELD_NOT_FOUND;
    }
}
