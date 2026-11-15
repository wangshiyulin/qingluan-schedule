# 第三方组件与参考

本应用业务逻辑使用 Kotlin / Compose 实现；下列条目列出使用的组件与少量复用的配色常量。

界面选取 25 个 AndroidX Material 图标 Kotlin 向量源文件，保留文件中的 Google / AOSP 版权和 Apache-2.0 许可头；其余常用图标使用 material-icons-core。来源：androidx.compose.material:material-icons-extended-android:1.7.8 的官方 sources.jar。

运行依赖包括 Kotlin/Kotlinx、AndroidX（Compose、Material 3、Lifecycle、Room、DataStore 等），通常采用 Apache-2.0；具体声明以依赖各自随附许可为准。JUnit、Robolectric 等仅用于测试，不随发行 APK 运行。

功能与实现思路参考：
- Sleepy https://github.com/lingion/sleepy ：周期课程与单次课次拆分、通知重排和精确闹钟降级思路。仅阅读参考，未复制其 GPL 源码。
- 拾光课程表 https://github.com/ShiGuangSchedule/shiguangschedule ：独立作息表、多学期、小组件一致性。
- Lectro https://github.com/Pankaj-Meharchandani/Lectro ：本地课表与提醒、小组件的功能组织。
- 青鸾记账：用户授权参考其简洁设置与青绿色视觉风格，并适配其悬浮导航栏、关于页面与紧凑日期弹窗设计。

图标由用户提供，用于本项目。源码许可证的最终发布选择由用户决定；本次交付不自动授权第三方使用该图标。

Backdrop 2.0.1 / Shapes：Copyright 2025 Kyant，Apache License 2.0。用于底部导航的背景折射、色散、反光。

拾光课程表：Apache License 2.0。复用 ScheduleGridStyle.kt 中 DEFAULT_COLOR_MAPS 的 12 对浅色/深色课程配色常量；保留原值，增加本应用的边框和文字颜色。来源 https://github.com/ShiGuangSchedule/shiguangschedule 。

jsoup 1.24.1：Copyright Jonathan Hedley，MIT License。用于通用 HTML 课表解析。

ZXing core 3.5.4：Copyright ZXing authors，Apache License 2.0。用于课表二维码编解码。

玻璃效果实现思路参考用户提供的 https://juejin.cn/post/7514618352829448244 ，按钮采用 Android 原生 RuntimeShader；底栏使用 Backdrop。补充参考 https://forum.cocos.org/t/topic/171941 的折射与反光原理。

设置分组与 OLED 深色背景设计参考 Kazumi：https://github.com/Predidit/Kazumi 。仅参考布局、颜色语义和交互思路，本项目使用 Kotlin / Compose 独立实现，未移植其 Flutter 业务源码。

导入字段结构参考：拾光扩展仓库的正方适配（MIT，Copyright (c) 2025 星河欲转），https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/main/resources/zhengfang_jiaowu/zhengfang_01.js ；小爱课程表公开正方 DOM 解析示例 https://github.com/kqakqakqa/xiaoai-schedule-parser 。只参考网页字段结构，未复制其脚本；本地解析使用独立 Kotlin 实现。

导入架构与字段模型参考 WakeUp Kotlin 历史公开镜像：https://github.com/tKM9WsmQUaUgNttn3DGUsHkxG8/WakeupSchedule_Kotlin 。仅参考课程基础信息与上课时段分离、按课表和课程编号关联，以及正方网页字段位置；未复制其源码或资源。新增 AndroidX WebKit 1.18.0 用于文档开始时读取课表数据，Apache-2.0。

学校名称与 HTTPS 教务入口数据复用自拾光适配仓库（MIT，Copyright (c) 2025 星河欲转）：https://github.com/ShiGuangSchedule/shiguang_warehouse 。完整许可见 app/src/main/assets/licenses/shiguang-school-MIT.txt；只内置入口元数据，不下载或执行远程适配脚本。南京中医药大学入口依据用户截图及公开网址校验补充。
