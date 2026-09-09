# 卡片保险箱 CardVault

纯自用的安卓银行卡管理工具。录入卡号、银行、持卡人等信息，首页按银行品牌色展示卡片墙，支持指纹解锁、卡号默认掩码、加密备份导出导入。不上架、不做云同步。

## 功能

| 功能 | 说明 |
|---|---|
| 卡片墙首页 | 按银行品牌色渲染卡面，只显示卡号后四位；拼音 A-Z 排序、信用卡优先；类型筛选 + 银行名/卡号搜索 |
| 录入 / 编辑 | 银行名、备注名、卡号、持卡人、类型（储蓄/信用）、卡组织（自动识别或手选）、有效期、额度、预留手机、备注 |
| 卡号校验 | Luhn 实时校验；卡组织 BIN 自动识别（银联 / Visa / MC / Amex / JCB）；已录卡号查重拦截 |
| 详情查看 | 默认掩码，点按展开，一键复制 |
| 批量操作 | 长按进入多选，批量删除 |
| 指纹解锁 | 生物识别进 App，切后台自动上锁（可开关） |
| 防截屏 | FLAG_SECURE，release 默认开启，设置页可临时关闭 |
| 加密备份 | 自设密码（PBKDF2 + AES-GCM）导出 `.cvbak`，换机导入合并 |

## 技术栈

Kotlin + Jetpack Compose + Material 3。刻意零重依赖：无 Room、无 SQLCipher、无 Navigation（页面切换用 sealed class 状态机）。

| 项 | 版本 |
|---|---|
| AGP / Kotlin / Gradle | 9.3.2 / 2.2.10 / 9.5.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 (Android 8.0) |

## 安全设计（改动前必读）

1. **数据模型无 CVV / 查询密码 / 支付密码字段**，永远不要加。
2. **存储 = Keystore 密钥 + AES-256-GCM 加密整个 JSON 文件**，不用数据库；数据量小，依赖少风险小。
3. **备份用独立的密码派生密钥（PBKDF2 21 万轮）**，不复用 Keystore 密钥——它不可导出，换机后解不开。
4. **备份密码无法找回**；卸载 App / 清数据 = 卡库永久丢失（Keystore 密钥一并失效），换机前务必先导出。
5. 卡号默认只显后四位；`FLAG_SECURE` 防截屏；`allowBackup=false`；切后台立即上锁。

## 构建与运行

```bash
# 环境要求：JDK 17+（Gradle JDK 用 Android Studio 自带 jbr 即可）
./gradlew assembleDebug      # debug 包
./gradlew assembleRelease    # 正式签名包
```

**克隆后必做**：本仓库不含签名信息。在项目根目录创建 `local.properties`（已 gitignore），补上：

```properties
sdk.dir=<你的 Android SDK 路径>
RELEASE_STORE_FILE=<keystore 绝对路径>
RELEASE_STORE_PASSWORD=<store 密码>
RELEASE_KEY_ALIAS=<key 别名>
RELEASE_KEY_PASSWORD=<key 密码>
```

缺后四项时 release 构建会直接报错提示；debug 构建不需要它们。

⚠️ 工程必须放在**全英文路径**下，AGP 拒绝非 ASCII 路径。证书 + 密码丢失 = 无法覆盖安装已发布的包。

## 代码结构

```
app/src/main/java/com/example/cardvault/
├── MainActivity.kt          入口：生物识别、防截屏、导航状态机、批量删除调度
├── model/BankCard.kt        数据模型 + Luhn 校验 + BIN 识别 + 掩码 + 排序
├── data/VaultCrypto.kt      Keystore 密钥、AES-GCM 封箱、备份密码派生
├── data/VaultRepository.kt  卡库读写（加密 JSON，可选字段容错）
└── ui/
    ├── CardFace.kt          卡面组件（品牌色、类型徽章、长按多选）
    ├── CardListScreen.kt    首页卡片墙（筛选/搜索/条件条/多选模式）
    ├── CardDetailScreen.kt  详情页
    ├── CardEditScreen.kt    录入/编辑表单（查重、卡组织下拉、额度）
    ├── SettingsScreen.kt    设置（分组卡片式）、备份导出导入
    └── theme/               主题（浅灰底 + 蓝强调）与银行品牌配色
```

## 版本规则

三位式 `x.y.z`：第三位 = bug 修复，第二位 = 新功能，第一位 = 重大变更。当前 v1.2.0。

## TODO

- 拍照识别卡号（ML Kit，需相机权限）
- 信用卡还款日提醒
- 自动锁超时宽限（切后台 N 秒内回来免验证）
