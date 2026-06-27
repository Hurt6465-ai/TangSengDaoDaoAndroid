# wkfeed v6

独立发现动态插件。路线保持：Android 原生 Module + ViewPager2 Vertical + 内层多图 ViewPager2 + Media3 ExoPlayer + Glide + BottomSheet 评论 + 个人主页瀑布流。

v6 在 v5 基础上重点修复：

1. 修正 WKIMUtils 包名，和唐僧叨叨当前源码保持一致：com.chat.uikit.chat.manager.WKIMUtils。
2. 修正 ChatViewMenu 构造参数，传入 FragmentActivity，不再传普通 Context。
3. FeedRoute 支持非 Activity Context，自动加 FLAG_ACTIVITY_NEW_TASK。
4. FeedMedia.isVideo 补充 play_url_540p 判断，避免后端只返回 540p 时被误判成图片。
5. FeedMedia.playUrl 优先级调整：540p -> 480p -> 720p -> play_url，避免优先播放可能是原画的 play_url。
6. FeedBean stableId 从 32 位 hashCode 改成 64 位 rolling hash，降低 ViewPager2 stableId 冲突概率。
7. 个人主页瀑布流 gapStrategy 改为 GAP_HANDLING_NONE，减少刷新/分页时错位跳动。
8. FeedPlayerManager release 时取消当前 CacheWriter，避免关闭发现页后预加载线程继续跑。
9. 保留 v5 的视频 Mock、Media3 单播放器、轻量预缓存、发布按钮滑动隐藏、图片 WebP 约 100KB、视频 540p/最高 720p 策略。

接入：

settings.gradle:
include ':wkfeed'

app/build.gradle:
implementation project(path: ':wkfeed')

入口：
FeedRoute.openDiscover(context);
FeedRoute.newUserWaterfallFragment(uid);
