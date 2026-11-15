<h1 align="center">青鸾课程表</h1>

<p align="center">
  <img src="https://schedule.qingluanx.com/assets/brand.svg" width="200" height="200" alt="青鸾课程表图标" />
</p>

<p align="center">查看今日课程，安排每一周。</p>

<p align="center"><strong>本地存储 · 无需注册 · 轻量易用</strong></p>

<p align="center">
  <a href="https://github.com/wangshiyulin/qingluan-schedule/releases/latest"><img src="https://img.shields.io/github/v/release/wangshiyulin/qingluan-schedule?label=Release&amp;color=379D98" alt="最新版本" /></a>
  <img src="https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&amp;logoColor=white" alt="Android 15 及以上" />
</p>

<p align="center">
  <a href="https://github.com/wangshiyulin/qingluan-schedule/releases/latest">下载应用</a> ·
  <a href="https://schedule.qingluanx.com">官网</a> ·
  <a href="https://github.com/wangshiyulin/qingluan-schedule/releases">更新日志</a> ·
  <a href="https://github.com/wangshiyulin/qingluan-schedule/issues">问题反馈</a>
</p>

## 简介

青鸾课程表是一款 Android 课程表应用，支持今日课程、周课表、多课表管理、教务导入、临时调整和备份恢复。课表保存在自己的设备上，无需注册应用账号。

你可以设置独立作息、调整授课周次，为课程添加教师、教室和备注，也可以自定义配色、字号和课表样式。

## 界面预览

<table>
  <tr>
    <td align="center"><img src="https://schedule.qingluanx.com/assets/previews/today.webp" width="240" alt="今日课程页面" /><br />今日</td>
    <td align="center"><img src="https://schedule.qingluanx.com/assets/previews/week.webp" width="240" alt="周课表页面" /><br />课表</td>
    <td align="center"><img src="https://schedule.qingluanx.com/assets/previews/settings.webp" width="240" alt="设置页面" /><br />设置</td>
  </tr>
</table>

## 功能特性

- **今日与周课表**：查看日期、教学周和今日课程，滑动切换周次；已结束的今日课程淡化并显示删除线。
- **多课表管理**：创建、编辑和切换不同学期的课表，分别设置开学日期、总周数、每周起始日和绑定作息。
- **课程编辑**：点击课表区域添加课程，支持教师、教室、备注、多时段、单双周及不连续周；连续课程合并显示。
- **作息管理**：创建独立作息表，自定义每节课的起止时间，按课时和休息时长批量设置。
- **临时调整与调休**：支持调课、停课、补课和撤销调整；节假日可批量停课，调休可采用另一天的常规课表。
- **课程冲突**：检查重叠课次，查看冲突列表并跳转到对应周；导入前也会提示时间冲突。
- **教务导入**：本科/专科、研究生、通用三个入口，支持学校搜索和手动网址，可选择自动识别、网页表格或页面数据；导入前核对课程、周次、作息和缺失字段。
- **导入与导出**：支持原生 JSON、CSV / TSV、Excel（XLSX）、HTML、ICS、分享口令和二维码；可将课表导出为 PNG 或 PDF。
- **数据与备份**：完整 JSON / ZIP 备份、本地自动备份、历史恢复和撤销最近修改。
- **课程提醒**：设置提前时间，检查通知权限、后台运行状态和下一次提醒，发送测试通知。
- **个性化课表**：调整节次高度、课程字号、间距、圆角、透明度和对齐方式；拖动分隔条调整实时预览区域。
- **操作体验**：透明液态玻璃底栏、页面转场、预测返回、可选触觉反馈和自选启动页面；支持浅色、深色及系统动态配色。

## 下载

支持 **Android 15 及以上**。

前往 [GitHub Releases](https://github.com/wangshiyulin/qingluan-schedule/releases/latest) 下载 `.apk` 安装包。也可以通过应用内的 **设置 → 关于 → 检查更新** 打开发布页面。

## 数据与备份

课表默认在本机保存，不会由应用自动上传。教务导入需要打开学校网站，网页解析在本机完成。

<details>
<summary>备份与使用说明</summary>

- 换设备、卸载或清除应用数据前，请导出完整备份，并保存到应用之外的位置。
- 本地自动备份可保留 3、5 或 10 份，保存在应用私有目录，卸载应用会一并删除。
- CSV、XLSX 和 HTML 适合交换周期课程；保留临时调整与节假日调休请选择原生 JSON 或 ICS。完整 JSON / ZIP 备份同时保留全部本地课表和设置。
- 完整备份恢复会替换本地数据，请先核对预览。
- 学校目录包含 214 所学校、230 个 HTTPS 入口。目录提供导航，不代表所有学校均已逐一完成兼容性验证。
- 教务导入前请打开完整学期课表，导入时核对课程总数、教师、教室、周次和作息。网站未加载的数据无法凭空补齐，旧课表缺失字段需重新导入。
- 不同教务系统的页面结构、跨域框架或验证码可能影响识别，遇到问题可改用 Excel / HTML 等文件导入。不支持任意截图 OCR 或其他软件的私有云端口令。
- 课程提醒需要通知权限，系统省电策略可能影响送达；可在课程提醒页面检查状态并发送测试通知。

</details>

## 反馈与参与

欢迎通过 [Issues](https://github.com/wangshiyulin/qingluan-schedule/issues) 提交问题和建议。反馈问题时，请附上应用版本、系统版本和复现步骤；提供课表样本前请遮挡账号等个人信息。

- 官网：[schedule.qingluanx.com](https://schedule.qingluanx.com)
- 作者博客：[qingluanx.com](https://qingluanx.com)
- 更新记录：[GitHub Releases](https://github.com/wangshiyulin/qingluan-schedule/releases)
- 开源组件说明：[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
