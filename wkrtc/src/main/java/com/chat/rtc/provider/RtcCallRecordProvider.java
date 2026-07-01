package com.chat.rtc.provider;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chat.base.msgitem.WKChatBaseProvider;
import com.chat.base.msgitem.WKChatIteMsgFromType;
import com.chat.base.msgitem.WKUIChatMsgItemEntity;
import com.chat.rtc.RtcConstants;
import com.chat.rtc.model.RtcCallRecordContent;

/** Simple visible call-record bubble. */
public class RtcCallRecordProvider extends WKChatBaseProvider {
    private static final int TV_ID = 0x76020301;

    @Override
    protected View getChatViewItem(ViewGroup parentView, WKChatIteMsgFromType from) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x14ffffff);
        bg.setCornerRadius(dp(14));
        root.setBackground(bg);

        TextView tv = new TextView(getContext());
        tv.setId(TV_ID);
        tv.setTextSize(14f);
        tv.setTextColor(0xff3f4755);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(false);
        root.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    @Override
    protected void setData(int adapterPosition, View parentView, WKUIChatMsgItemEntity uiChatMsgItemEntity, WKChatIteMsgFromType from) {
        TextView tv = parentView.findViewById(TV_ID);
        if (tv == null || uiChatMsgItemEntity == null || uiChatMsgItemEntity.wkMsg == null) return;
        String text = "通话记录";
        if (uiChatMsgItemEntity.wkMsg.baseContentMsgModel instanceof RtcCallRecordContent) {
            RtcCallRecordContent content = (RtcCallRecordContent) uiChatMsgItemEntity.wkMsg.baseContentMsgModel;
            String icon = RtcConstants.isVideo(content.callType) ? "📹 " : "📞 ";
            text = icon + content.getDisplayContent();
        } else if (uiChatMsgItemEntity.wkMsg.baseContentMsgModel != null) {
            text = uiChatMsgItemEntity.wkMsg.baseContentMsgModel.getDisplayContent();
        }
        tv.setText(text);
    }

    private int dp(float value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density + 0.5f);
    }
}
