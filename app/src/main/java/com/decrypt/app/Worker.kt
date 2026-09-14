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
        log("✓ root 正常   ${RootShell.modeName}")

        val src = srcInput.trim().trimEnd('/')
        if (src.isEmpty()) {
            log("✗ 源目录为空")
            return false
        }

        if (RootShell.kindOf(src) == "NONE") {
            log("✗ 当前这条路看不见：$src")
            log("· 换几条路再试：")
            RootShell.probe(log)
            if (RootShell.kindOf(src) == "NONE") {
                log("✗ 都不行。上面这几行发我，一眼就能看出是哪儿的毛病")
                return false
            }
            log("· 换路之后看见了")
        }

        var files: List<String> = emptyList()
        when (RootShell.kindOf(src)) {
            "FILE" -> {
                log("· 源路径是个文件，直接用")
                files = listOf(src)
            }

            else -> {
                val r = RootShell.sh("ls -1 ${RootShell.q(src)}")
                val names = r.text.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != "." && it != ".." }
                if (names.isEmpty()) {
                    log("✗ 目录是空的或者列不出来")
                    return false
                }
                log("· 目录里 ${names.size} 个文件")
                files = names.map { "$src/$it" }
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

    /** 自检：只看两条路能不能对上，不做全盘扫描 */
    fun diagnose(srcDir: String, dstFile: String, log: (String) -> Unit) {
        log("== root ==")
        if (!RootShell.request()) {
            log("✗ 没有可用的 su")
            return
        }
        log("su      ${RootShell.suPath}")
        log("su -v   ${RootShell.sh("su -v 2>&1 || echo 无").text}")
        log("id      ${RootShell.sh("id").text}")
        log("")

        log("== 几条路各能看见多少数据目录 ==")
        RootShell.probe(log)
        log("")

        val src = srcDir.trim().trimEnd('/')
        log("== 源目录 ==")
        log("$src  →  ${RootShell.kindOf(src)}")
        log(orNone(RootShell.sh("ls -la ${RootShell.q(src)} 2>&1 | head -20").text))
        log("")

        log("== 目标 ==")
        log(orNone(RootShell.sh("ls -l ${RootShell.q(dstFile.trim())} 2>&1").text))
        log("")
        log(orNone(RootShell.sh("ls -la ${RootShell.q(dstFile.trim().substringBeforeLast('/'))} 2>&1 | head -20").text))
        log("")
        log("· 时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    }

    private fun orNone(s: String): String = if (s.isEmpty()) "（什么都没有）" else s
}
