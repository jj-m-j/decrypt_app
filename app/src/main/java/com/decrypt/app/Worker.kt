package com.decrypt.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Worker {

    /**
     * 源路径给目录就用目录：里面的文件名是随机的，不写进配置，
     * 扫一遍挑第一个能解开的。给的是文件就当成那一个文件。
     */
    fun run(srcInput: String, dstInput: String, backup: Boolean, log: (String) -> Unit): Boolean {
        log("请求 root 权限…")
        if (!RootShell.request()) {
            log("✗ 没拿到 root，先在 root 管理器里授权本应用")
            return false
        }
        log("✓ root 正常")

        val src = srcInput.trim().trimEnd('/')
        if (src.isEmpty()) {
            log("✗ 源路径为空")
            return false
        }

        val files = when (RootShell.kindOf(src)) {
            "FILE" -> {
                log("· 源路径是个文件，直接用")
                listOf(src)
            }

            "DIR" -> {
                val r = RootShell.sh("ls -1 ${RootShell.q(src)}")
                if (!r.ok) {
                    log("✗ 列目录失败：${r.text}")
                    return false
                }
                val names = r.text.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != "." && it != ".." }
                if (names.isEmpty()) {
                    log("✗ 目录是空的：$src")
                    return false
                }
                log("· 目录里 ${names.size} 个文件")
                names.map { "$src/$it" }
            }

            else -> {
                log("✗ 这个路径不存在：$src")
                dumpPath(src, log)
                return false
            }
        }

        var yaml: String? = null
        var used = ""

        for (f in files.take(30)) {
            val name = f.substringAfterLast('/')
            val raw = RootShell.read(f)
            if (!raw.ok) {
                log("  · $name 读不了：${raw.text.ifEmpty { "退出码 ${raw.exitCode}" }}")
                continue
            }
            val plain = ProfileCrypto.decrypt(raw.out)
            if (plain == null) {
                log("  · $name 不是能解开的加密 yaml，跳过")
                continue
            }
            yaml = plain
            used = name
            log("  ✓ $name 解开了")
            break
        }

        if (yaml == null) {
            log("✗ 里面没有能解开的加密 yaml")
            return false
        }

        val bytes = yaml.toByteArray(Charsets.UTF_8)
        log("· 明文 ${bytes.size} 字节，来自 $used")

        val dst = dstInput.trim()
        if (dst.isEmpty()) {
            log("✗ 目标路径为空")
            return false
        }

        val ok = RootShell.writeFile(dst, bytes, backup, log)
        log(if (ok) "✓ 完成" else "✗ 写完校验对不上，检查目标路径")
        return ok
    }

    /** 自检：把判案要用的证据一次性打出来 */
    fun diagnose(log: (String) -> Unit) {
        log("== root ==")
        if (!RootShell.request()) {
            log("✗ 没有可用的 su")
            return
        }
        log("su      ${RootShell.suPath}")
        log("id      ${RootShell.sh("id").text}")
        log("su -v   ${RootShell.sh("su -v 2>&1 || echo 无").text}")
        log("")

        log("== 各用户的 app 数据目录 ==")
        log(orNone(RootShell.sh("ls -d /data/user/*/com.nexgen.jisuwangluo 2>&1").text))
        log("")

        log("== profiles 目录 ==")
        log(orNone(RootShell.sh("ls -la /data/user/*/com.nexgen.jisuwangluo/files/profiles 2>&1 | head -30").text))
        log("")

        log("== 全盘找 profiles ==")
        log(
            orNone(
                RootShell.sh(
                    "find /data/user /data/data -maxdepth 5 -type d -name profiles -path '*jisuwangluo*' 2>/dev/null | head -10",
                ).text,
            ),
        )
        log("")

        log("== 目标目录 ==")
        log(orNone(RootShell.sh("ls -la /data/adb/box_bll/clash/proxies 2>&1 | head -20").text))
    }

    private fun orNone(s: String): String = if (s.isEmpty()) "（什么都没有）" else s

    /** 一层层往上敲，看是断在哪一级 */
    private fun dumpPath(path: String, log: (String) -> Unit) {
        log("· 从上层往下看：")
        var p = path
        while (p.length > 1) {
            val r = RootShell.sh("ls -ld ${RootShell.q(p)} 2>&1")
            val first = r.text.lineSequence().firstOrNull()?.trim().orEmpty()
            log("  ${if (r.ok) "✓" else "✗"} $first")
            p = p.substringBeforeLast('/', "")
        }
        log("· 时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    }
}
