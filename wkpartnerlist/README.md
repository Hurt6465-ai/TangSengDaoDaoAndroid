# Talkami 独立列表语伴 Android 前端

## 主要功能

- 独立模块 `wkpartnerlist`，不修改 `wkpartnerbrowse` 内部代码。
- 轻玻璃顶栏、浅渐变背景、半实体圆角卡片。
- 72dp 圆形头像；头像左下角国旗；右上角真实在线绿点。
- 卡片右侧“打招呼”按钮，显示每日剩余额度。
- 首次没有本地缓存时显示 6 张同结构骨架卡片，使用轻微呼吸动画，不显示转圈 Loading。
- 当天有本地缓存时先秒开缓存，再静默同步服务端。
- 5 小时到期自动请求服务端轮换；使用 `ListAdapter + DiffUtil` 局部更新。
- 前台每 55 秒发送心跳；每 90 秒仅批量刷新可见卡片和后续约 10 人在线状态。
- 点击卡片打开现有语伴资料页；打招呼复用后端 `/v1/partners/greetings`。
- 中文、英文、缅甸语资源。

## 接入

在 Android 项目根目录执行：

```bash
unzip wkpartnerlist-frontend-final.zip
bash apply_partnerlist.sh /你的/TangSengDaoDaoAndroid-master
```

或手动：

1. 把 `wkpartnerlist/` 复制到项目根目录。
2. 在 `settings.gradle` 添加 `include ':wkpartnerlist'`。
3. 在 `app/build.gradle` 添加 `implementation project(path: ':wkpartnerlist')`。
4. 将底部语伴入口改为 Endpoint `peipe_open_partner_list`。

## 后端接口

- `GET /v1/partner-list/recommendations`
- `POST /v1/partner-list/online/batch`
- `POST /v1/partner-list/activity/heartbeat`
- `POST /v1/partners/greetings`

## 验证

```bash
./gradlew :wkpartnerlist:assembleDebug
./gradlew :app:assembleDebug
```

## 范围说明

本包是独立列表语伴页面与入口。Pending 关系中第 2、3 条聊天消息经 `/v1/message/send` 的公共聊天发送路由，属于 `wkuikit` 聊天层改造，不在列表页面模块内部重复实现。
