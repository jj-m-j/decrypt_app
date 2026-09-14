package com.decrypt.app

import java.util.concurrent.TimeUnit

class ShellResult(val exitCode: Int, val out: ByteArray) {
    val text: String get() = out.toString(Charsets.UTF_8).trim()
    val ok: Boolean get() = exitCode == 0
}

/** 所有文件操作都丢给 su 去做，app 自己不去碰那些路径。 */
object RootShell {

    private const val TIMEOUT_MS = 90_000L

    private val CANDIDATES = listOf(
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
    )

    var suPath: String? = null
        private set

    /** 单引号里的路径丢给 shell 之前要先补一下反斜杠，不然带引号的路径直接崩 */
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 挨个试 su，能跑出 uid=0 才算数 */
    fun request(): Boolean {
        for (c in CANDIDATES) {
            suPath = c
            val r = run("id")
            if (r.ok && r.text.contains("uid=0")) return true
        }
        suPath = null
        return false
    }

    fun sh(cmd: String, stdin: ByteArray? = null): ShellResult {
        val su = suPath ?: return ShellResult(-1, "没有可用的 su".toByteArray())
        return run(cmd, stdin, su)
    }

    /** 读文件；顺便把「路径其实是目录」这类错误原样带出来 */
    fun read(path: String): ShellResult = sh("cat ${q(path)}")

    /** DIR / FILE / NONE */
    fun kindOf(path: String): String {
        val p = q(path)
        return sh("if [ -d $p ]; then echo DIR; elif [ -f $p ]; then echo FILE; else echo NONE; fi")
            .text.lines().last().trim()
    }

    /** 直接写字节：用 cat 从 stdin 接管，省掉转义、base64 这些麻烦 */
    fun writeFile(path: String, bytes: ByteArray, backup: Boolean, log: (String) -> Unit): Boolean {
        if (path.isEmpty() || !path.contains('/')) {
            log("✗ 目标路径不合法")
            return false
        }
        val dir = path.substringBeforeLast('/')
        val f = q(path)

        val meta = sh("stat -c '%u:%g:%a' $f 2>/dev/null").text

        if (backup && meta.isNotEmpty()) {
            if (sh("cp -f $f $f.bak").ok) log("· 已备份 → $path.bak")
        }

        sh("mkdir -p ${q(dir)}")

        val w = sh("cat > $f", bytes)
        if (!w.ok) {
            log("✗ 写入失败：${w.text.ifEmpty { "退出码 ${w.exitCode}" }}")
            return false
        }

        val parts = meta.split(':')
        if (parts.size == 3) {
            sh("chown ${parts[0]}:${parts[1]} $f; chmod ${parts[2]} $f")
        } else {
            sh("chmod 644 $f")
        }

        val size = sh("wc -c < $f").text.toLongOrNull()
        log("· 已写入 $path  ($size 字节)")
        return size == bytes.size.toLong()
    }

    private fun run(cmd: String, stdin: ByteArray? = null, su: String = suPath ?: "su"): ShellResult {
        return try {
            val p = ProcessBuilder(su, "-c", cmd).redirectErrorStream(true).start()
            if (stdin != null) {
                p.outputStream.use { it.write(stdin) }
            } else {
                p.outputStream.close()
            }
            // 先把输出读完再 waitFor，否则大文件会把管道堵死
            val out = p.inputStream.readBytes()
            if (!p.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                p.destroy()
                ShellResult(-1, "命令超时".toByteArray())
            } else {
                ShellResult(p.exitValue(), out)
            }
        } catch (e: Exception) {
            ShellResult(-1, ((e.message ?: e.toString()) + "\n（" + cmd + "）").toByteArray())
        }
    }
}
