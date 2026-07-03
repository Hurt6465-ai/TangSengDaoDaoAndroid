package com.chat.rtc;

import android.text.TextUtils;

import com.chat.base.config.WKConfig;
import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.WKSendMsgMenu;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKChannelType;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WuKong IM transport for WebRTC signaling.
 *
 * INVITE is allowed to be persisted with a short expire window so the callee can still
 * receive the call during short reconnect/background sync. All other SDP/ICE/control packets
 * remain online-only and no-red-dot to avoid polluting chat history and unread counts.
 */
public class RtcWukongSignalTransport implements RtcSignalTransport {
    private static final int SIGNAL_EXPIRE_SECONDS = 5 * 60;

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
        String loginUid = WKConfig.getInstance().getUid();
        if (!TextUtils.isEmpty(loginUid) && TextUtils.equals(peerUid, loginUid)) {
            throw new IllegalArgumentException("RTC signal target is self: " + peerUid);
        }

        boolean durableInvite = isInviteSignal(payload);
        // Use normal text content for signaling transport compatibility. ChatActivity hides
        // messages by the strict __cp_harmony_rtc__: prefix, so it will not render as a bubble.
        // Custom content types are more likely to be missed if registration is late or cache decode fails.
        WKTextContent content = new WKTextContent(payload);
        WKChannel channel = new WKChannel(peerUid, WKChannelType.PERSONAL);
        WKSendOptions options = new WKSendOptions();

        applySignalOptions(options, durableInvite);
        markByReflection(content, !durableInvite);
        // Follow the same send hook path as normal chat messages. Some host logic is attached
        // to EndpointSID.sendMessage; bypassing it can make signaling behave differently from
        // regular text messages on certain builds.
        try { EndpointManager.getInstance().invokes(EndpointSID.sendMessage, new WKSendMsgMenu(channel, options)); } catch (Exception ignored) {}
        WKIM.getInstance().getMsgManager().sendWithOptions(content, channel, options);
    }

    private boolean isInviteSignal(String payload) {
        try {
            String text = payload;
            if (text.startsWith(RtcConstants.SIGNAL_PREFIX)) {
                text = text.substring(RtcConstants.SIGNAL_PREFIX.length());
            }
            JSONObject object = new JSONObject(text);
            return RtcSignalCompat.INVITE.equals(object.optString("type"));
        } catch (Exception ignored) {
            return payload.contains("\"type\":\"invite\"") || payload.contains("\"type\" : \"invite\"");
        }
    }

    private void applySignalOptions(WKSendOptions options, boolean durableInvite) {
        if (options == null) return;

        options.expire = SIGNAL_EXPIRE_SECONDS;

        try {
            if (options.header != null) {
                options.header.noPersist = !durableInvite;
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

        markByReflection(options, !durableInvite);
        markByReflection(getFieldValue(options, "header"), !durableInvite);
        markByReflection(getFieldValue(options, "setting"), !durableInvite);
    }

    private void markByReflection(Object object, boolean noPersist) {
        if (object == null) return;

        setFieldValue(object, "noPersist", noPersist);
        setFieldValue(object, "no_persist", noPersist);
        setFieldValue(object, "redDot", false);
        setFieldValue(object, "red_dot", false);

        setFieldValue(object, "noUnread", true);
        setFieldValue(object, "no_unread", true);
        setFieldValue(object, "showUnread", false);
        setFieldValue(object, "show_unread", false);
        setFieldValue(object, "unread", false);
        setFieldValue(object, "needRedDot", false);
        setFieldValue(object, "need_red_dot", false);

        if (noPersist) {
            setFieldValue(object, "persist", false);
            setFieldValue(object, "isPersist", false);
            setFieldValue(object, "is_persist", false);
        }

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

    /** Avoid importing model just for one string in this low-level transport. */
    private static final class RtcSignalCompat {
        private static final String INVITE = "invite";
    }
}
