package com.decrypt.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Worker {

    /**
     * 全程两次 shell 调用：一次把源目录里的文件端回来，一次把明文写出去。
     * 解密、挑文件、备份决策、结果校验都在 app 里做。
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

        log("读取源目录…")
        var entries = RootShell.readAll(src)
        if (entries.isEmpty()) {
            log("✗ 这条 su 看不到：$src")
            log("· 换几条路再试：")
            RootShell.probe(log)
            entries = RootShell.readAll(src)
        }
        if (entries.isEmpty()) {
            log("✗ 都看不见。上面那几行发我")
            return false
        }
        log("· 拿到 ${entries.size} 个文件")

        var yaml: String? = null
        var used = ""

        for ((path, raw) in entries.take(30)) {
            val name = path.substringAfterLast('/')
            val plain = ProfileCrypto.decrypt(raw)
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

        log("写入目标…")
        val ok = RootShell.writeFile(dst, bytes, backup, log)
        log(if (ok) "✓ 完成" else "✗ 写完校验对不上，检查目标路径")
        return ok
    }

    /** 自检：看几条路各自能看见多少数据目录，不做全盘扫描 */
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

        log("== 解密 ==")
        log("纯 Kotlin：Base64 解码 + 循环 XOR，不调用任何外部脚本")
        log("")
        log("· 时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    }

    private fun orNone(s: String): String = if (s.isEmpty()) "（什么都没有）" else s
}
