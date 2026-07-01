package com.chat.rtc;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
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
 * The old in-module call was more reliable because it did not over-own the busy state.
 * For delivery reliability, this transport now sends RTC packets through normal personal
 * messaging, but marks them silent/no-red-dot. ChatActivity hides packets by exact prefix.
 *
 * Do NOT force noPersist=true here. Some devices/server combinations drop online-only
 * packets when the callee is reconnecting or backgrounded, which makes the peer never ring.
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
        String myUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(myUid) && TextUtils.equals(myUid, peerUid)) {
            // Never send RTC packets to myself. This is the main cause of fake busy state.
            return;
        }

        WKTextContent content = new WKTextContent(payload);
        WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
        WKSendOptions options = new WKSendOptions();

        applyReliableSilentSignalOptions(options);
        // Some SDK versions copy unread/red-dot flags from content rather than options.
        markSilentOnly(content);
        WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
    }

    private void applyReliableSilentSignalOptions(WKSendOptions options) {
        if (options == null) return;

        options.expire = SIGNAL_EXPIRE_SECONDS;

        try {
            if (options.header != null) {
                // Reliable delivery: keep persist false/online-only OFF. The chat page hides the
                // payload by prefix, so a short-lived persisted signal is safer than a lost invite.
                options.header.noPersist = false;
                options.header.redDot = false;
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

        markReliableSilent(options);
        markReliableSilent(getFieldValue(options, "header"));
        markSilentOnly(getFieldValue(options, "setting"));
    }

    /** Marks packet as reliable/persistable but silent. */
    private void markReliableSilent(Object object) {
        if (object == null) return;
        setFieldValue(object, "noPersist", false);
        setFieldValue(object, "no_persist", false);
        setFieldValue(object, "persist", true);
        setFieldValue(object, "isPersist", true);
        setFieldValue(object, "is_persist", true);
        markSilentOnly(object);
    }

    /** Marks packet as no unread/no red dot without forcing noPersist. */
    private void markSilentOnly(Object object) {
        if (object == null) return;

        setFieldValue(object, "redDot", false);
        setFieldValue(object, "red_dot", false);
        setFieldValue(object, "noUnread", true);
        setFieldValue(object, "no_unread", true);
        setFieldValue(object, "showUnread", false);
        setFieldValue(object, "show_unread", false);
        setFieldValue(object, "unread", false);
        setFieldValue(object, "needRedDot", false);
        setFieldValue(object, "need_red_dot", false);

        setFieldValue(object, "receipt", 0);
        setFieldValue(object, "stream", 0);
        setFieldValue(object, "expire", SIGNAL_EXPIRE_SECONDS);
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
