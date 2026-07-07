# wklearning

独立学习插件模块。

本版结构：

- `LearningFragment`：原生学习首页，使用 `DrawerLayout` 做 Telegram 风格左侧边栏。
- 首页原生层使用 `ViewPager2` 横向切换：拼音 / 单词 / 口语 / 句型 / 语法 / 互动题。
- 每个栏目使用 `RecyclerView` 渲染学习卡片，后续可以把数据替换成本地 JSON 或远程 JSON。
- `WordFullscreenActivity`：背单词全屏页，使用竖向 `ViewPager2`；上/下滑切词，左滑不会，右滑会了，点击翻面。
- 侧边栏放原学习页内容：DeepSeek、886.best、千问、Qwen、学习书籍、语音朗读、高频生活场景、脚本管理。
- 广告暂时未接入。

背景图策略：

- 当前先用原生渐变海报兜底，避免远程失败导致空白。
- 正式运营建议：本地放默认背景图，远程 `home.json` 覆盖标题、价格、按钮文案和海报图 URL。

后续可以继续拆分：

- `LearningConfigRepository`：读取本地/远程 JSON。
- `LearningOfflineManager`：下载和校验离线包。
- `LearningTtsBridge`：接入 wkspeech 点读、朗读。
