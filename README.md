# decrypt_app

小米风（Miuix / Compose）的安卓小工具：拿到 root 之后，把极速网络
`com.nexgen.jisuwangluo` 存在 `files/profiles/` 里的加密 yaml 解出来，
覆盖写入 `/data/adb/box_bll/clash/proxies/多宝专线.yaml`。

两个路径在 app 里都能改，会记在 SharedPreferences 里。

## 加密格式

```
FLCLASH_ENCRYPTED_V1:<base64(明文 XOR 重复密钥)>
密钥 = FlClash2024SecretKey!@#$%^&*()
```

这个密钥是工作区里那个爆破脚本（`解密/decrypt_flclash.py`）试出来的，
验证方式是把密文按周期 30 异或回去刚好等于明文，长度也完全一致。

源目录里的 yaml 文件名是随机生成的（像 `1789287773290.yaml`），所以配置里只填目录，
app 会扫目录里的文件逐个试，挑第一个能解开的。给的是文件路径也能直接用。
解不开或者不是这个格式的会被跳过并打进日志。

日志里每一行都会说明哪个文件试过、结果如何，「自检」按钮会把 su 版本、各用户下的
app 数据目录、profiles 目录内容、目标目录内容一次性打出来。

## 实现要点

- 全部文件读写都通过 `su -c` 交给 root shell 做，app 自己不去直接 open 那些路径。
- 读用 `cat`，写用 `cat > file` 从 stdin 塞字节，绕开转义和 base64 的坑。
- 写入前会 `stat` 出目标原来的 uid/gid/权限，写完再 `chown` + `chmod` 还原。
- 默认开启「写入前备份」，会先 `cp` 一份 `.bak`。
- su 路径挨个探测：`su`、`/system/bin/su`、`/data/adb/ksu/bin/su` 等。

## 构建

已配好 GitHub Actions（`.github/workflows/build.yml`），push 到 main 或者手动
`workflow_dispatch` 就会用 JDK 21 + Gradle 9.6.1 + AGP 9.3.1 打出 release APK，
在 Actions 页面的 Artifacts 里下载 `decrypt_app-release-apk`。

release 用 debug 签名，装得上，但别拿去上架。

本地构建：本仓库没有放 gradle wrapper jar，先 `gradle wrapper` 生成一下，
或者直接用系统 `gradle` 跑 `gradle :app:assembleRelease`。
需要 Android SDK platform 37 / build-tools 37.0.0。

## 技术栈

- Kotlin 2.4.10（AGP 9 自带 Kotlin，这里在根 buildscript 里把 KGP 抬到 2.4.10）
- Miuix `top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01`
- compileSdk / targetSdk 37，minSdk 26
