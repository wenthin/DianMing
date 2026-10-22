# 点个名（DianMing）

[![构建](https://github.com/wenthin/DianMing/actions/workflows/android-release.yml/badge.svg)](https://github.com/wenthin/DianMing/actions/workflows/android-release.yml)
[![最新版本](https://img.shields.io/github/v/release/wenthin/DianMing?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC)](https://github.com/wenthin/DianMing/releases/latest)

一款用 **Kotlin + Jetpack Compose** 编写的安卓点名应用：导入班级名单表格、快捷点名、统计并追溯出勤数据。

## 直接下载

| 版本 | 下载 | 说明 |
| --- | --- | --- |
| v1.1.0 | [app-release.apk](https://github.com/wenthin/DianMing/releases/download/v1.1.0/app-release.apk) | 正式签名版，约 11 MB，需 Android 8.0 及以上 |

也可以在 [Releases 页面](https://github.com/wenthin/DianMing/releases) 查看历史版本与更新说明。

## 功能

| 模块 | 说明 |
| --- | --- |
| 首页 | 展示已创建的班级列表，右下角悬浮按钮（FAB）添加班级 |
| 导入班级 | 通过系统文件选择器选取本地表格（`.xlsx` / `.xls` / `.csv`），预览表格内容、指定**名字列**（必选）与**电话列**（可选）、勾选需要保留的其它列，并填写班级名称、负责人姓名与电话（可添加多位） |
| 班级详情 | 顶部班级信息卡片：班级名、人数、负责人（点击电话直接进入系统拨号）、"开始点名"按钮；下方为历史考勤记录列表与出勤概览 |
| 点名 | 按名单顺序逐个展示：姓名加粗放大（点击朗读），其余信息以标签形式展示，电话号一键拨号；底部三个并排按钮 —— 红色 ✕ 缺勤 / 蓝色 请假 / 绿色 ✓ 出勤，点完卡片先变色再向左滑走、下一位自动滑入；**右滑卡片可回退到上一位** |
| 断点续点 | 点名途中退出后，**10 分钟内**再点"开始点名"会变成"继续点名"，接着上次没点完的继续（从第一个没点到的人开始）；超时后自动变回"开始点名"，另开一次新的 |
| 考勤记录 | 每次点名生成一条考勤记录，记录时间与逐人状态；点击记录可进入详情修改 |

## 特性说明

- **零第三方表格库**：`.xlsx` 用 `ZipInputStream` + SAX 解析，`.xls` 先解 OLE2 复合文档再解析 BIFF8 记录，`.csv` 自动识别分隔符与 GBK / UTF-16 编码，全部为手写纯 Kotlin 实现。
- **零运行时权限**：文件选择走 SAF（`ActivityResultContracts.OpenDocument`），拨号走 `Intent.ACTION_DIAL`，朗读用系统 `TextToSpeech`，均无需申请权限。
- **朗读需要声明可见性**：Android 11 起默认开启包可见性限制，Manifest 中必须声明 `<queries><intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>`，否则应用看不到系统语音引擎，`TextToSpeech` 绑定失败、朗读会静默失效。
- **无注解处理器**：数据库使用原生 `SQLiteOpenHelper`，不引入 Room / KSP，构建稳定；界面刷新依靠 `Repository` 中的 `MutableStateFlow` tick 广播。
- **无 JSON 依赖**：附加信息的序列化使用自写的 `TinyStore`（`␟` 分隔的键值对），便于在 JVM 单元测试中直接验证。
- **点名动效**：点击状态按钮后，卡片先在**原地按状态变色**（前 1/3 动画时长，变色区域就是卡片本身，不是一整块矩形），再**统一向左滑走**（后 2/3），下一位从右侧滑入，全程带震动反馈；动画期间按钮置灰锁定，避免连点造成多点。撤销不再用按钮，改为**右滑卡片**回退上一位：滑动时露出"↩ 撤销上一次"提示，超过卡片宽度 1/4 松手即回退（没滑够自动弹回）。
- **断点续点**：点名记录是即时落库的，`开始点名` 时会先查找该班级最近一次**没点完**且**10 分钟内还有活动**的场次并接着点（场次按开始时间倒序，判据是"已点名人数 < 班级人数"且"距最后一次点名不超过 10 分钟"）；超过 10 分钟按钮会自动变回"开始点名"，新开一次场次。考勤详情页里另有"继续这次点名"，可以指定续哪一次。
- **后台返回保留界面**：Manifest 中 `MainActivity` 使用 `android:launchMode="singleTask"`。若用默认的 `standard`，在部分机型（如 MIUI / HyperOS）上每次从桌面点图标进入都会在同一任务里**新建一个 Activity 实例**，新实例自带一套全新的导航栈，结果就是"回到应用先看到原界面、很快又被新实例的首页顶掉"。改为 `singleTask` 后图标点击只复用已有实例，界面状态原样保留；即使进程被系统在后台回收，Compose Navigation 也能通过 `rememberSaveable` 从 `onSaveInstanceState` 恢复出原来的页面。

## 技术栈

| | |
| --- | --- |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.20 |
| Gradle | 8.13 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| JVM target | 17 |
| Compose BOM | 2025.09.00（material3 1.3.2） |
| Navigation | navigation-compose 2.9.8 |
| 测试 | JUnit 4 + kotlinx-coroutines-test |

## 构建

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug          # 产出 app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:testDebugUnitTest      # 运行单元测试
.\gradlew.bat :app:assembleRelease        # 产出 app\build\outputs\apk\release\app-release.apk（需先配置签名，见下）
```

### 签名配置（本地）

`app/build.gradle.kts` 会自动读取仓库根目录的 `keystore.properties`（已在 `.gitignore` 中排除，不会入库）：

```properties
storeFile=C:/Users/<用户名>/.android/dianming-release.jks
storePassword=<密钥库口令>
keyAlias=dianming
keyPassword=<密钥口令>
```

没有这个文件时 `assembleRelease` 仍可编译，只是产出未签名的包。

### 自动构建与发布（GitHub Actions）

`.github/workflows/android-release.yml` 定义了流水线：

- 推送到 `main`：自动跑单元测试 → 构建正式签名 APK → 作为构建产物（artifact）上传。
- 推送 `v*` 标签（如 `v1.0.0`）：在上述流程之外，自动创建 [Release](https://github.com/wenthin/DianMing/releases) 并附上 APK。

签名密钥库不放在仓库里，而是以 **Actions Secret** 的形式保存在仓库设置中：`KEYSTORE_BASE64`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。流水线会在构建时把它还原成临时密钥文件，签完即弃。

本地发一个新版本：

```powershell
git tag -a v1.1.0 -m "v1.1.0"
git push origin v1.1.0
```

## 目录结构

```
app/src/main/java/com/dianming/app/
├── MainActivity.kt / DianMingApp.kt     应用入口、Application
├── data/
│   ├── Models.kt        班级 / 学生 / 考勤记录等数据模型
│   ├── AppDb.kt         SQLiteOpenHelper 建表与读写
│   ├── Repository.kt    数据仓库 + 刷新通知
│   └── TinyStore.kt     键值序列化（附加信息）
├── spreadsheet/
│   ├── SpreadsheetReader.kt  统一入口，按扩展名分派
│   ├── SheetTable.kt         表格模型 + 列名/姓名/电话列启发式
│   ├── XlsxReader.kt         .xlsx（zip + SAX）
│   ├── XlsReader.kt          .xls（BIFF8）
│   ├── CompoundFile.kt       OLE2 复合文档解包
│   └── CsvReader.kt          .csv（分隔符 / 编码自动识别）
├── ui/
│   ├── home/      首页班级列表
│   ├── import/    导入与列配置
│   ├── detail/    班级详情、单次考勤详情
│   ├── rollcall/  点名界面
│   ├── common/    通用组件
│   ├── nav/       导航
│   └── theme/     主题与配色
└── util/          拨号、时间格式化、TTS 朗读
```

## 测试

`app/src/test/` 下共 29 个单元测试，覆盖：

- `.xlsx` 内容、日期列格式化、表头不在第一行的情况
- `.xls`（BIFF8）内容、日期列、数字列，以及 RK 数值解码（整数 / 负数 / 百分位 / 浮点）
- `.csv` 的 GBK 编码、引号转义、制表符分隔
- 姓名列 / 电话列的启发式识别
- `TinyStore` 序列化往返
- 「继续点名 / 开始点名」的判定规则（10 分钟窗口、点完不再续、多场次取最近可续的）

样本文件位于 `app/src/test/resources/`，由 `tools/gen_fixtures.py`（需要 Python 的 `openpyxl`、`xlwt`）生成：

```powershell
python tools\gen_fixtures.py
```
