wkdating 交友最终重构版
基于用户当前 `wkdating.zip` 重写，保留唐僧叨叨 Java/XML 架构，不引入 Compose、Flutter 或 Firebase。
已完成
首页与滑卡
CardStackView 已作为源码内置到模块，不依赖 JitPack，避免 CI 401/网络失败。
使用 `CardStackLayoutManager.getTopPosition()` 管理当前位置。
原生 `left_overlay / right_overlay / top_overlay`：左滑显示叉号、右滑显示红心、上滑显示星标。
三层卡片联动：`visibleCount=3`、`translationInterval=11`、`scaleInterval=0.95`、`maxDegree=17`。
DiffUtil 首次刷新，分页追加使用 `notifyItemRangeInserted`，不重置当前卡片。
首页四按钮：撤回、跳过、收藏、喜欢。
借鉴 Shuffle 维护本地 swipe history，支持连续撤回；撤回每天免费 3 次。
男喜欢 40 次/天，女喜欢 60 次/天；跳过不限次数。
超过判定线才触发一次短震动和低音量短音效，可在“我的交友”关闭；按钮使用 Overshoot 回弹。
正式构建接口失败不显示假用户；演示数据仅限 `BuildConfig.DEBUG`。
客户端再次排除 30 天未活跃用户，并执行双方异国恋意愿硬过滤。
附近页使用系统 LocationManager 获取一次经纬度并上报，不依赖 Google 地图 SDK。
页面拆分
`DatingHomeActivity`：推荐、附近、筛选、滑卡、分页和曝光。
`DatingProfileDetailActivity`：别人的完整资料，顶部照片、滚动白色资料卡、底部三按钮、屏蔽和举报。
`DatingMineActivity`：右上角纯人形图标进入；包含编辑资料、收藏、谁喜欢我、匹配、额度、交友开关、音效/触感开关。
`DatingEditProfileActivity`：只编辑自己的交友资料，不放收藏或“谁喜欢我”。
`DatingFavoritesActivity`：两列收藏卡片。
`DatingWhoLikesActivity`：会员占位页，不伪造喜欢你的用户。
`DatingMatchesActivity`：真实调用现有 matches 接口，点击进入唐僧聊天，长按解除匹配。
`DatingMatchDialog`：互相喜欢弹窗，立即聊天或继续匹配。
六张照片
最多 6 张，至少 2 张才能开启交友。
六个固定照片位；借鉴 DragRankSquare，第一张主图为大位、右侧与下方为小位；点击空位添加、点击已有图预览/删除、设为主图、长按拖动排序。
删除后后续照片自动前移，空位永远留在末尾。
复用唐僧 `common` 上传接口和 `WKUploader`。
上传前修正 EXIF，最长边限制 1440，压缩为 WebP；质量压缩仍超过目标时继续缩放，目标约 200KB。
收藏语义
收藏是“稍后看”，不触发匹配。
当前旧后端只认识 like/pass，因此客户端发送 `favorite`，旧后端会归一为 pass，不会误触发 match；同时本地保存收藏列表。
后端增加 `dating_favorites` 后，只需替换 `DatingFavoriteStore` 数据源和 favorite 接口，不需要重写 UI。
关键目录
```text
src/main/java/com/chat/dating/
  DatingHomeActivity.java
  DatingActionController.java
  DatingLocationHelper.java
  DatingProfileDetailActivity.java
  DatingMineActivity.java
  DatingEditProfileActivity.java
  DatingFavoritesActivity.java
  DatingWhoLikesActivity.java
  DatingMatchesActivity.java
  DatingMatchDialog.java
  DatingPhotoGridAdapter.java
  DatingPhotoUploadManager.java
  DatingPhotoCompressor.java
  DatingFavoriteStore.java

src/main/java/com/yuyakaido/android/cardstackview/
  CardStackView 源码（Apache-2.0，许可证见 THIRD_PARTY_CARDSTACKVIEW_LICENSE.txt）
```
主项目接入
根目录 `settings.gradle`：
```gradle
include ':wkdating'
```
`app/build.gradle`：
```gradle
implementation project(path: ':wkdating')
```
`TSApplication` 初始化：
```kotlin
WKDatingApplication.getInstance().init(this)
```
打开交友：
```java
EndpointManager.getInstance().invoke("dating_open", activity);
```
现有后端可直接使用
`GET /v1/dating/profile/me`
`POST /v1/dating/profile`
`POST /v1/dating/profile/copy_partner`
`POST /v1/dating/profile/enable`
`POST /v1/dating/location`
`GET /v1/dating/recommend`
`POST /v1/dating/swipes`
`POST /v1/dating/exposures`
`GET /v1/dating/matches`
`POST /v1/dating/matches/{match_id}/cancel`
`POST /v1/dating/block`
`POST /v1/dating/report`
后端仍需补齐
这些不能只靠 Android 客户端防绕过：
`dating_favorites` 收藏表、收藏列表、取消收藏接口。
`POST /v1/dating/swipes/undo`，每天免费撤回 3 次，只允许撤回尚未形成匹配的最后动作。
服务端额度：男喜欢 40、女喜欢 60；收藏额度按配置；跳过只做频率限制。
`who-liked-me` 接口与会员权限，免费用户只返回数量和模糊信息。
推荐接口服务端硬过滤双方异国恋意愿、30 天活跃度、严重举报和照片数量。
管理端：举报列表、删除照片、下架交友资料、封禁/恢复交友权限、查看多次举报用户。
推荐 session 曝光去重和真正 cursor 分页。
验证说明
已执行：
所有 XML 解析检查。
Java 语法解析检查。
ViewBinding 字段与布局 ID 对照检查。
drawable/layout 引用完整性检查。
非法 Android 资源文件名检查。
当前容器无法访问 `services.gradle.org` 下载 Gradle 8.13，因此未能完成完整 Gradle 编译；模块已移除 CardStackView 网络依赖，GitHub Actions 不再需要从 JitPack 下载该库。
