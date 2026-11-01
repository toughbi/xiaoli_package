# 日迹账本：正式签名 APK 与 GitHub 发布流程

本文档适用于当前项目：

```text
E:\code\xiaoli_package
```

无需安装 Android Studio。JDK、Android SDK、Gradle 和依赖缓存已经位于项目内的 `.android-build` 目录。

---

## 一、版本信息

发布前先打开 `android-app/app/build.gradle`，确认：

```gradle
defaultConfig {
    applicationId "com.daytrace.ledger"
    minSdk 26
    targetSdk 35
    versionCode 34
    versionName "1.16.1"
}
```

以后每次正式发布：

- `versionCode` 必须增加 1，例如 `34 → 35`
- `versionName` 按发布版本修改，例如 `1.16.1 → 1.16.2`
- `versionCode` 不能重复或变小

---

## 二、在 VSCode 中准备环境

使用 VSCode 打开整个项目根目录：

```text
E:\code\xiaoli_package
```

项目中的 `.vscode/settings.json` 会自动配置：

```text
JAVA_HOME
ANDROID_HOME
ANDROID_SDK_ROOT
ANDROID_USER_HOME
GRADLE_USER_HOME
TEMP / TMP
```

如果当前终端是在配置加入前打开的，请关闭终端，再打开一个新的 PowerShell 终端。

也可以按：

```text
Ctrl+Shift+P
```

执行：

```text
Developer: Reload Window
```

验证 Java：

```powershell
java -version
```

应显示：

```text
openjdk version "17.0.2"
```

如果当前终端没有自动加载环境，手动执行：

```powershell
$env:JAVA_HOME = "E:\code\xiaoli_package\.android-build\jdk\jdk-17.0.2"
$env:ANDROID_HOME = "E:\code\xiaoli_package\.android-build\sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = "E:\code\xiaoli_package\.android-build\gradle-home"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

java -version
```

---

## 三、创建正式签名证书

正式签名证书只需要创建一次。

建议把证书保存在项目目录之外：

```powershell
New-Item -ItemType Directory -Path "E:\code\xiaoli_package_keys" -Force
```

创建证书：

```powershell
& "E:\code\xiaoli_package\.android-build\jdk\jdk-17.0.2\bin\keytool.exe" `
  -genkeypair -v `
  -keystore "E:\code\xiaoli_package_keys\daytrace-release.jks" `
  -alias daytrace `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000
```

终端会要求设置：

- 签名库密码
- 密钥密码
- 姓名
- 组织名称
- 城市、省份和国家代码

证书和密码必须长期保存。以后发布新版本必须使用同一个证书，否则 Android 会提示签名不一致，用户无法覆盖安装。

不要提交或上传：

```text
daytrace-release.jks
*.keystore
keystore.properties
```

---

## 四、构建未签名 Release APK

在 VSCode 终端执行：

```powershell
cd E:\code\xiaoli_package
powershell -ExecutionPolicy Bypass -File .\build-android.ps1 -Tasks :app:assembleRelease
```

构建成功后，未签名文件位于：

```text
E:\code\xiaoli_package\android-app\app\build\outputs\apk\release\app-release-unsigned.apk
```

如果文件名称不同，查看：

```powershell
Get-ChildItem "E:\code\xiaoli_package\android-app\app\build\outputs\apk\release"
```

---

## 五、对齐 APK

Android 正式 APK 必须先进行 ZIP 对齐，再进行签名。

设置路径：

```powershell
$root = "E:\code\xiaoli_package"

$buildTools = (
    Get-ChildItem "$root\.android-build\sdk\build-tools" -Directory |
    Sort-Object Name -Descending |
    Select-Object -First 1
).FullName

$unsigned = "$root\android-app\app\build\outputs\apk\release\app-release-unsigned.apk"
$aligned  = "$root\daytrace-v1.16.1-aligned.apk"
$signed   = "$root\daytrace-v1.16.1-release.apk"
```

检查路径：

```powershell
$buildTools
$unsigned
```

执行对齐：

```powershell
& "$buildTools\zipalign.exe" -p -f 4 $unsigned $aligned
```

---

## 六、生成正式签名 APK

执行：

```powershell
& "$buildTools\apksigner.bat" sign `
  --ks "E:\code\xiaoli_package_keys\daytrace-release.jks" `
  --ks-key-alias daytrace `
  --v1-signing-enabled true `
  --v2-signing-enabled true `
  --out $signed `
  $aligned
```

终端会要求输入：

1. 签名库密码
2. 证书密钥密码

最终用于发布的文件：

```text
E:\code\xiaoli_package\daytrace-v1.16.1-release.apk
```

以下文件只是中间产物，不要发布：

```text
daytrace-v1.16.1-aligned.apk
app-release-unsigned.apk
*.idsig
```

---

## 七、验证正式签名

执行：

```powershell
& "$buildTools\apksigner.bat" verify `
  --verbose `
  --print-certs `
  $signed
```

确认输出包含：

```text
Verified using v2 scheme (APK Signature Scheme v2): true
```

查看版本号：

```powershell
& "$buildTools\aapt2.exe" dump badging $signed |
    Select-Object -First 1
```

确认：

```text
versionCode
versionName
```

与 `android-app/app/build.gradle` 一致。

生成 SHA256：

```powershell
Get-FileHash $signed -Algorithm SHA256
```

把输出的 SHA256 写入 GitHub Release 说明。

---

## 八、准备 Git 仓库

当前项目应忽略以下内容：

```gitignore
.android-build/
android-app/build/
android-app/app/build/
android-app/.gradle/
android-app/local.properties
.vscode/settings.json
*.apk
*.aab
*.idsig
*.jks
*.keystore
keystore.properties
release/
```

检查暂存区：

```powershell
cd E:\code\xiaoli_package
git status
```

确保暂存区中没有：

- `.android-build/`
- `*.jks`
- 正式签名 APK
- 调试 APK
- `.idsig`
- API Key
- 本机专属 `.vscode/settings.json`

首次创建 Git 仓库：

```powershell
git init
git add .
git commit -m "Initial release: Daytrace ledger v1.16.1"
git branch -M main
```

如果 Git 提示用户名或邮箱未配置：

```powershell
git config --global user.name "你的名字"
git config --global user.email "你的GitHub邮箱"
```

---

## 九、创建 GitHub 仓库并推送源码

在 GitHub 网页创建空仓库：

1. 打开 GitHub。
2. 点击 `New repository`。
3. 填写仓库名称，例如 `daytrace-ledger`。
4. 不要勾选自动创建 README、`.gitignore` 或 License。
5. 创建仓库。

添加远程地址：

```powershell
cd E:\code\xiaoli_package
git remote add origin https://github.com/你的用户名/daytrace-ledger.git
```

如果已经存在 `origin`，修改地址：

```powershell
git remote set-url origin https://github.com/你的用户名/daytrace-ledger.git
```

推送：

```powershell
git push -u origin main
```

首次推送会要求 GitHub 登录：

- 优先使用浏览器登录或 Git Credential Manager
- 如果要求密码，请使用 GitHub Personal Access Token
- 不要使用 GitHub 账户登录密码

---

## 十、创建 GitHub Release 并上传 APK

源码推送成功后，打开 GitHub 仓库：

1. 点击右侧 `Releases`。
2. 点击 `Draft a new release`。
3. `Choose a tag` 填写：

```text
v1.16.1
```

4. Release 标题填写：

```text
日迹账本 v1.16.1
```

5. 将 `RELEASE_NOTES_v1.16.1.md` 的内容复制到说明框。

6. 上传：

```text
daytrace-v1.16.1-release.apk
```

7. 点击：

```text
Publish release
```

GitHub 仓库保存源码，GitHub Releases 保存 APK。

不要把 APK 提交进 Git 源码。

---

## 十一、发布前检查清单

- [ ] `versionCode` 已增加
- [ ] `versionName` 已修改
- [ ] Release 构建成功
- [ ] APK 已使用 `zipalign` 对齐
- [ ] APK 已使用正式证书签名
- [ ] `apksigner verify` 验证成功
- [ ] SHA256 已生成
- [ ] 源码中没有 API Key
- [ ] 源码中没有 `.jks`
- [ ] 源码中没有 APK 和 `.idsig`
- [ ] GitHub Release 只上传正式签名 APK
- [ ] Release 说明包含版本、隐私和 SHA256

---

## 十二、以后发布新版本

1. 修改 `android-app/app/build.gradle`
2. 增加 `versionCode`
3. 修改 `versionName`
4. 重新构建 Release APK
5. 使用同一个 `daytrace-release.jks` 签名
6. 验证签名并生成 SHA256
7. 提交源码并推送
8. 创建新的 Git 标签，例如 `v1.16.2`
9. 上传新的正式签名 APK 到 GitHub Releases

不要重新生成签名证书，否则老用户无法直接升级。
