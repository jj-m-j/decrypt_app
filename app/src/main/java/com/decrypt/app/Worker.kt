package com.decrypt.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Worker {

    private const val MAGIC = "FLCLASH_ENCRYPTED_V1"

    /**
     * 源路径给目录就用目录：里面的文件名是随机的，不写进配置，
     * 扫一遍挑第一个能解开的。给的是文件就当成那一个文件。
     * 万一这条 su 看不见它，就直接按文件内容全设备找。
     */
    fun run(srcInput: String, dstInput: String, backup: Boolean, log: (String) -> Unit): Boolean {
        log("请求 root 权限…")
        if (!RootShell.request()) {
            log("✗ 没拿到 root，先在 root 管理器里授权本应用")
            return false
        }
        log("✓ root 正常   ${RootShell.mode}")

        val src = srcInput.trim().trimEnd('/')
        var files: List<String> = emptyList()

        when (RootShell.kindOf(src)) {
            "FILE" -> {
                log("· 源路径是个文件，直接用")
                files = listOf(src)
            }

            "DIR" -> {
                val r = RootShell.sh("ls -1 ${RootShell.q(src)}")
                val names = r.text.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != "." && it != ".." }
                if (names.isEmpty()) {
                    log("✗ 目录是空的或者列不出来：$src")
                    log(namespaceHint(log))
                    return false
                }
                log("· 目录里 ${names.size} 个文件")
                files = names.map { "$src/$it" }
            }

            else -> {
                log("✗ 这条 su 看不到：$src")
                val hit = hunt(log)
                if (hit == null) {
                    log(namespaceHint(log))
                    return false
                }
                files = listOf(hit)
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

    /** 不认包名，只认内容：谁开头是 FLCLASH_ENCRYPTED_V1 就是它 */
    private fun hunt(log: (String) -> Unit): String? {
        log("· 不认包名，按文件内容全设备找…")

        val dirs = RootShell.sh("ls -d /data/user/*/*/files/profiles 2>/dev/null | head -20").text
        log(if (dirs.isEmpty()) "  · 没看到任何 profiles 目录" else "  · 现有 profiles 目录：\n$dirs")

        val cmds = listOf(
            "grep -rl $MAGIC /data/user/*/*/files/profiles 2>/dev/null | head -5",
            "grep -l $MAGIC /data/user/*/*/files/* 2>/dev/null | head -5",
            "timeout 20 grep -rl $MAGIC --include='*.yaml' --include='*.yml' --include='*.txt' /data/data 2>/dev/null | head -5",
            "grep -rl $MAGIC /data/local/tmp /data/adb /data/media/0/Download 2>/dev/null | head -5",
        )
        for (c in cmds) {
            val hit = RootShell.sh(c, null).text.lines()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && it.startsWith("/") }
            if (hit != null) {
                log("  ✓ 找到 $hit")
                log("  · 把它填进上面的源目录，下次就不用再找了")
                return hit
            }
        }

        log("  ✗ 整台机器都没搜到这种文件")
        return null
    }

    private fun namespaceHint(log: (String) -> Unit): String = buildString {
        append("· /data/data 里有 ")
        append(RootShell.sh("ls -1 /data/data 2>/dev/null | wc -l").text)
        append(" 个目录，/data/user 里有 ")
        append(RootShell.sh("ls -1 /data/user 2>/dev/null | wc -l").text)
        append(" 个")
    }

    /** 自检：把判案要用的证据一次性打出来 */
    fun diagnose(log: (String) -> Unit) {
        log("== root ==")
        if (!RootShell.request()) {
            log("✗ 没有可用的 su")
            return
        }
        log("方式    ${RootShell.mode}")
        log("su -v   ${RootShell.sh("su -v 2>&1 || echo 无").text}")
        log("id      ${RootShell.sh("id").text}")
        log("")

        log("== 看得见什么 ==")
        log("/data/data 条目数   ${RootShell.sh("ls -1 /data/data 2>/dev/null | wc -l").text}")
        log("/data/user 条目数   ${RootShell.sh("ls -1 /data/user 2>/dev/null | wc -l").text}")
        log("readlink /data/user/0   ${RootShell.sh("readlink /data/user/0 2>&1").text}")
        log("ls /data/user  →")
        log(orNone(RootShell.sh("ls -la /data/user 2>&1").text))
        log("")

        log("== 包名 ==")
        log("· pm 里像它的：")
        log(
            orNone(
                RootShell.sh("pm list packages 2>/dev/null | grep -iE 'nexgen|jisu|wangluo|speed' | head -20").text,
            ),
        )
        log("· /data/data 里像它的：")
        log(
            orNone(
                RootShell.sh("ls -1 /data/data 2>/dev/null | grep -iE 'nexgen|jisu|wangluo|speed' | head -20").text,
            ),
        )
        log("· 全设备 profiles 目录：")
        log(orNone(RootShell.sh("ls -d /data/user/*/*/files/profiles 2>/dev/null | head -30").text))
        log("")

        log("== 按内容找加密 yaml ==")
        log(
            orNone(
                RootShell.sh(
                    "timeout 20 grep -rl $MAGIC --include='*.yaml' --include='*.yml' --include='*.txt' " +
                        "/data/user/*/*/files/profiles /data/data 2>/dev/null | head -10",
                ).text,
            ),
        )
        log("")

        log("== 目标目录 ==")
        log(orNone(RootShell.sh("ls -la /data/adb/box_bll/clash/proxies 2>&1 | head -20").text))
        log("")
        log("· 时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    }

    private fun orNone(s: String): String = if (s.isEmpty()) "（什么都没有）" else s
}
