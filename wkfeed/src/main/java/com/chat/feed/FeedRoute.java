package com.chat.feed;

import android.content.Context;
import android.content.Intent;

import android.app.Activity;

import com.chat.feed.browse.FeedBrowseActivity;
import com.chat.feed.publish.FeedPublishActivity;
import com.chat.feed.profile.FeedWaterfallFragment;

public class FeedRoute {
    public static void openDiscover(Context context) {
        openBrowse(context, FeedBrowseActivity.MODE_DISCOVER, "", "", 0);
    }

    public static void openNearby(Context context) {
        openBrowse(context, FeedBrowseActivity.MODE_NEARBY, "", "", 0);
    }

    public static void openUserFeeds(Context context, String uid, int startPosition) {
        openBrowse(context, FeedBrowseActivity.MODE_PROFILE, uid, "", startPosition);
    }

    public static void openUserFeeds(Context context, String uid, String feedId, int startPosition) {
        openBrowse(context, FeedBrowseActivity.MODE_PROFILE, uid, feedId, startPosition);
    }

    private static void openBrowse(Context context, String mode, String uid, String feedId, int startPosition) {
        Intent intent = new Intent(context, FeedBrowseActivity.class);
        intent.putExtra(FeedBrowseActivity.EXTRA_MODE, mode);
        intent.putExtra(FeedBrowseActivity.EXTRA_UID, uid);
        intent.putExtra(FeedBrowseActivity.EXTRA_START_FEED_ID, feedId);
        intent.putExtra(FeedBrowseActivity.EXTRA_START_POSITION, startPosition);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void openPublish(Context context) {
        Intent intent = new Intent(context, FeedPublishActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static FeedWaterfallFragment newUserWaterfallFragment(String uid) {
        return FeedWaterfallFragment.newInstance(uid);
    }
}
