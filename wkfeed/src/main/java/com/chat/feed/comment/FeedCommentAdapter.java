package com.chat.feed.comment;

import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.chat.base.config.WKApiConfig;
import com.chat.base.ui.components.AvatarView;
import com.chat.feed.R;
import com.chat.feed.model.CommentBean;
import com.chat.uikit.view.CircleProgress;
import com.chat.uikit.view.WKPlayVoiceUtils;
import com.chat.uikit.view.WaveformView;
import com.xinbida.wukongim.entity.WKChannelType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedCommentAdapter extends RecyclerView.Adapter<FeedCommentAdapter.VH> {
    public interface CommentActionListener {
        void onReplyClick(CommentBean item, int position);
        void onLoadReplies(CommentBean item, int position);
        void onRetryLocal(CommentBean item, int position);
    }

    private final ArrayList<CommentBean> list = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private CommentActionListener actionListener;

    public FeedCommentAdapter() {
        setHasStableIds(true);
    }

    public void setActionListener(CommentActionListener listener) {
        this.actionListener = listener;
    }

    public void submitList(List<CommentBean> newList) {
        ArrayList<CommentBean> next = flatten(newList);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new FeedCommentDiffCallback(list, next));
        list.clear();
        list.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    public void appendList(List<CommentBean> moreList) {
        ArrayList<CommentBean> next = flatten(moreList);
        if (next.isEmpty()) return;
        int start = list.size();
        list.addAll(next);
        notifyItemRangeInserted(start, next.size());
    }

    private ArrayList<CommentBean> flatten(List<CommentBean> source) {
        ArrayList<CommentBean> out = new ArrayList<>();
        if (source == null) return out;
        for (CommentBean item : source) {
            if (item == null) continue;
            item.item_type = CommentBean.TYPE_COMMENT;
            out.add(item);
            List<CommentBean> children = item.safeChildren();
            for (CommentBean child : children) {
                if (child == null) continue;
                child.parent_id = item.comment_id;
                child.item_type = CommentBean.TYPE_REPLY;
                out.add(child);
            }
        }
        return out;
    }

    public void addFirst(CommentBean bean) {
        if (bean == null) return;
        list.add(0, bean);
        notifyItemInserted(0);
    }

    public void markLocalFailed(String commentId) {
        int index = indexOf(commentId);
        if (index < 0) return;
        CommentBean bean = list.get(index);
        bean.local_sending = false;
        bean.local_failed = true;
        notifyItemChanged(index);
    }

    public void markLocalSent(String commentId) {
        int index = indexOf(commentId);
        if (index < 0) return;
        CommentBean bean = list.get(index);
        bean.local_sending = false;
        bean.local_failed = false;
        notifyItemChanged(index);
    }

    public void markLocalSending(String commentId) {
        int index = indexOf(commentId);
        if (index < 0) return;
        CommentBean bean = list.get(index);
        bean.local_sending = true;
        bean.local_failed = false;
        notifyItemChanged(index);
    }

    public void updateLocalContent(String commentId, String content) {
        int index = indexOf(commentId);
        if (index < 0) return;
        list.get(index).content = content;
        notifyItemChanged(index);
    }

    private int indexOf(String commentId) {
        if (commentId == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (commentId.equals(list.get(i).comment_id)) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feed_comment, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CommentBean item = list.get(position);
        boolean reply = item.isReply();
        int start = dp(holder.itemView, reply ? 62 : 15);
        holder.root.setPadding(start, holder.root.getPaddingTop(), holder.root.getPaddingRight(), holder.root.getPaddingBottom());
        holder.avatar.setSize(reply ? 28 : 38);
        bindCommentAvatar(holder.avatar, item);
        holder.nameTv.setText(item.name == null ? "" : item.name);
        holder.timeTv.setText(item.created_at > 0 ? sdf.format(new Date(normalizeTime(item.created_at))) : "");
        holder.likeTv.setText(item.like_count > 0 ? String.valueOf(item.like_count) : "");

        VoicePayload voice = VoicePayload.parse(item.content);
        if (voice != null) {
            holder.contentTv.setVisibility(View.GONE);
            holder.voiceLayout.setVisibility(View.VISIBLE);
            bindVoice(holder, item, voice);
        } else {
            holder.voiceLayout.setVisibility(View.GONE);
            holder.contentTv.setVisibility(View.VISIBLE);
            holder.contentTv.setText(item.content == null ? "" : item.content);
        }

        if (item.local_sending) {
            holder.stateTv.setVisibility(View.VISIBLE);
            holder.stateTv.setText(R.string.feed_comment_sending);
            holder.stateTv.setTextColor(0xFF8A8F98);
        } else if (item.local_failed) {
            holder.stateTv.setVisibility(View.VISIBLE);
            holder.stateTv.setText(R.string.feed_comment_failed);
            holder.stateTv.setTextColor(0xFFEF4444);
        } else {
            holder.stateTv.setVisibility(View.GONE);
        }

        int missingReplyCount = Math.max(0, item.reply_count - item.safeChildren().size());
        holder.expandTv.setVisibility(!reply && missingReplyCount > 0 ? View.VISIBLE : View.GONE);
        holder.expandTv.setText(holder.itemView.getResources().getString(R.string.feed_comment_expand_replies, missingReplyCount));
        holder.expandTv.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onLoadReplies(item, holder.getBindingAdapterPosition());
        });
        holder.replyTv.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onReplyClick(item, holder.getBindingAdapterPosition());
        });
        holder.stateTv.setOnClickListener(v -> {
            if (item.local_failed && actionListener != null) actionListener.onRetryLocal(item, holder.getBindingAdapterPosition());
        });
    }

    private void bindVoice(@NonNull VH holder, CommentBean item, VoicePayload voice) {
        String key = item.comment_id == null ? String.valueOf(item.stableId()) : item.comment_id;
        holder.voicePlayBtn.setBindId(key);
        holder.voiceTimeTv.setText(formatVoiceTime(voice.durationSec));
        holder.voiceWaveform.setBind(key);
        holder.voiceWaveform.setWaveform(voice.waveformBytes());
        if (TextUtils.equals(WKPlayVoiceUtils.getInstance().getPlayKey(), key) && WKPlayVoiceUtils.getInstance().isPlaying()) {
            holder.voicePlayBtn.setPause();
        } else {
            holder.voicePlayBtn.setPlay();
            holder.voiceWaveform.setProgress(0f);
        }
        holder.voiceLayout.setOnClickListener(v -> {
            String path = voice.playPath();
            if (TextUtils.isEmpty(path)) return;
            attachVoicePlayListener(holder, key);
            if (WKPlayVoiceUtils.getInstance().isPlaying() && TextUtils.equals(WKPlayVoiceUtils.getInstance().getPlayKey(), key)) {
                WKPlayVoiceUtils.getInstance().onPause();
                holder.voicePlayBtn.setPlay();
                holder.voiceWaveform.setProgress(0f);
                return;
            }
            WKPlayVoiceUtils.getInstance().playVoice(path, key);
            holder.voicePlayBtn.setPause();
        });
        if (TextUtils.equals(WKPlayVoiceUtils.getInstance().getPlayKey(), key)) {
            attachVoicePlayListener(holder, key);
        }
    }

    private void attachVoicePlayListener(@NonNull VH holder, String key) {
        WKPlayVoiceUtils.getInstance().setPlayListener(new WKPlayVoiceUtils.IPlayListener() {
            @Override
            public void onCompletion(String playKey) {
                if (TextUtils.equals(playKey, key)) {
                    holder.voiceWaveform.setProgress(0f);
                    holder.voicePlayBtn.setPlay();
                }
            }

            @Override
            public void onProgress(String playKey, float progress) {
                if (TextUtils.equals(playKey, key)) {
                    holder.voiceWaveform.setProgress(progress);
                    holder.voicePlayBtn.setPause();
                }
            }

            @Override
            public void onStop(String playKey) {
                if (TextUtils.equals(playKey, key)) {
                    holder.voiceWaveform.setProgress(0f);
                    holder.voicePlayBtn.setPlay();
                }
            }
        });
    }

    @Override
    public long getItemId(int position) {
        return list.get(position).stableId();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    private void bindCommentAvatar(AvatarView avatar, CommentBean item) {
        if (avatar == null || item == null) return;
        try {
            if (!TextUtils.isEmpty(item.uid)) {
                avatar.showAvatar(item.uid, WKChannelType.PERSONAL, item.avatar_cache_key);
            } else if (!TextUtils.isEmpty(item.avatar)) {
                avatar.showAvatarUrl(item.avatar, item.avatar_cache_key, item.name, item.uid);
            } else {
                avatar.showDefaultAvatar(item.name, item.uid);
            }
            if (!TextUtils.isEmpty(item.country_code)) {
                avatar.showFlag(item.country_code);
            }
        } catch (Throwable ignored) {
            avatar.showDefaultAvatar(item.name, item.uid);
        }
    }

    private long normalizeTime(long time) {
        return time < 100000000000L ? time * 1000L : time;
    }

    private String formatVoiceTime(int sec) {
        if (sec <= 0) sec = 1;
        int m = sec / 60;
        int s = sec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", m, s);
    }

    private int dp(View view, int value) {
        return (int) (view.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    static class VoicePayload {
        final boolean local;
        final String path;
        final int durationSec;
        final String waveform;

        VoicePayload(boolean local, String path, int durationSec, String waveform) {
            this.local = local;
            this.path = path;
            this.durationSec = durationSec;
            this.waveform = waveform;
        }

        static VoicePayload parse(String content) {
            if (TextUtils.isEmpty(content)) return null;
            boolean local = content.startsWith("voice_local:");
            boolean remote = content.startsWith("voice:");
            if (!local && !remote) return null;
            String body = content.substring(local ? "voice_local:".length() : "voice:".length());
            String[] parts = body.split("\\|", -1);
            String path = parts.length > 0 ? parts[0] : "";
            int duration = 1;
            if (parts.length > 1) {
                try { duration = Math.max(1, Integer.parseInt(parts[1])); } catch (Exception ignored) {}
            }
            String waveform = parts.length > 2 ? parts[2] : "";
            return new VoicePayload(local, path, duration, waveform);
        }

        String playPath() {
            if (TextUtils.isEmpty(path)) return "";
            if (local || path.startsWith("/") || path.startsWith("file://") || path.startsWith("http://") || path.startsWith("https://")) {
                return path;
            }
            return WKApiConfig.getShowUrl(path);
        }

        byte[] waveformBytes() {
            if (!TextUtils.isEmpty(waveform)) {
                try { return Base64.decode(waveform, Base64.NO_WRAP); } catch (Exception ignored) {}
            }
            return new byte[]{6, 10, 14, 18, 12, 9, 16, 22, 13, 7, 15, 20, 11, 8, 17, 12, 9, 6};
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        View root;
        AvatarView avatar;
        TextView nameTv;
        TextView contentTv;
        LinearLayout voiceLayout;
        CircleProgress voicePlayBtn;
        WaveformView voiceWaveform;
        TextView voiceTimeTv;
        TextView timeTv;
        TextView replyTv;
        TextView stateTv;
        TextView expandTv;
        TextView likeTv;
        VH(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.commentRoot);
            avatar = itemView.findViewById(R.id.commentAvatar);
            nameTv = itemView.findViewById(R.id.commentNameTv);
            contentTv = itemView.findViewById(R.id.commentContentTv);
            voiceLayout = itemView.findViewById(R.id.commentVoiceLayout);
            voicePlayBtn = itemView.findViewById(R.id.commentVoicePlayBtn);
            voiceWaveform = itemView.findViewById(R.id.commentVoiceWaveform);
            voiceTimeTv = itemView.findViewById(R.id.commentVoiceTimeTv);
            timeTv = itemView.findViewById(R.id.commentTimeTv);
            replyTv = itemView.findViewById(R.id.commentReplyTv);
            stateTv = itemView.findViewById(R.id.commentStateTv);
            expandTv = itemView.findViewById(R.id.commentExpandTv);
            likeTv = itemView.findViewById(R.id.commentLikeTv);
        }
    }
}
