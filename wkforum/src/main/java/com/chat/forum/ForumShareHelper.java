package com.chat.forum;

import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chat.base.endpoint.EndpointManager;
import com.chat.base.endpoint.EndpointSID;
import com.chat.base.endpoint.entity.ChatChooseContacts;
import com.chat.base.endpoint.entity.ChooseChatMenu;
import com.xinbida.wukongim.WKIM;
import com.xinbida.wukongim.entity.WKChannel;
import com.xinbida.wukongim.entity.WKSendOptions;
import com.xinbida.wukongim.msgmodel.WKTextContent;

/** Shares a forum link through Talkami's existing conversation picker. */
final class ForumShareHelper {
    private ForumShareHelper() {
    }

    static void sendToTalkami(@NonNull Context context, @Nullable String title,
                              @Nullable String summary, @NonNull String url) {
        String payload = buildPayload(title, summary, url);
        WKTextContent previewContent = new WKTextContent(payload);
        EndpointManager.getInstance().invoke(EndpointSID.showChooseChatView,
                new ChooseChatMenu(new ChatChooseContacts(channels -> {
                    if (channels == null || channels.isEmpty()) return;
                    int sent = 0;
                    for (WKChannel channel : channels) {
                        if (channel == null || TextUtils.isEmpty(channel.channelID)) continue;
                        WKSendOptions options = new WKSendOptions();
                        options.setting.receipt = channel.receipt;
                        WKIM.getInstance().getMsgManager().sendWithOptions(
                                new WKTextContent(payload), channel, options);
                        sent++;
                    }
                    if (sent > 0) {
                        Toast.makeText(context, R.string.forum_sent, Toast.LENGTH_SHORT).show();
                    }
                }), previewContent));
    }

    @NonNull
    private static String buildPayload(@Nullable String title, @Nullable String summary,
                                       @NonNull String url) {
        String cleanTitle = title == null ? "" : title.trim();
        String cleanSummary = summary == null ? "" : summary.replaceAll("\\s+", " ").trim();
        if (cleanSummary.length() > 100) cleanSummary = cleanSummary.substring(0, 100) + "…";
        StringBuilder out = new StringBuilder();
        if (!TextUtils.isEmpty(cleanTitle)) out.append(cleanTitle);
        if (!TextUtils.isEmpty(cleanSummary) && !TextUtils.equals(cleanSummary, cleanTitle)) {
            if (out.length() > 0) out.append('\n');
            out.append(cleanSummary);
        }
        if (out.length() > 0) out.append('\n');
        out.append(url);
        return out.toString();
    }
}
