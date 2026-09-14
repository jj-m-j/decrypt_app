package com.decrypt.app

import java.util.concurrent.TimeUnit

class ShellResult(val exitCode: Int, val out: ByteArray) {
    val text: String get() = out.toString(Charsets.UTF_8).trim()
    val ok: Boolean get() = exitCode == 0
}

/**
 * 有些 root 管理器（KernelSU 就是）会让 su 继承调用者应用自己的 mount namespace，
 * 于是在这条 shell 里 /data/data 只剩下寥寥几个条目，别人看得见的数据目录我们看不见。
 * 这里备了几条路，开跑前挨个试，谁真能看见别人的数据就用谁。
 */
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

    private class Strategy(
        val name: String,
        /** 路径前面套这个前缀，靠 /proc/1/root 借 init 的挂载视图 */
        val prefix: String = "",
        /** 命令外面套一层 nsenter，进 init 的 mount namespace */
        val nsenter: Boolean = false,
        /** su -M，Magisk 系那套 mount master */
        val master: Boolean = false,
    )

    private val STRATEGIES = listOf(
        Strategy("su -c"),
        Strategy("su -c /proc/1/root", prefix = "/proc/1/root"),
        Strategy("su -c + nsenter", nsenter = true),
        Strategy("su -M -c", master = true),
    )

    var suPath: String? = null
        private set

    private var current = STRATEGIES[0]
    private var useNsenter = false
    private var useMaster = false
    private var prefix = ""

    /** 现在走的是哪条路，界面上会显示 */
    var modeName: String = "还没探测"
        private set

    /** 纯 shell 转义，不带路径前缀 */
    fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** 路径转义：带上当前策略需要的前缀 */
    fun q(path: String): String = shq(prefix + path)

    fun request(): Boolean {
        for (c in CANDIDATES) {
            suPath = c
            apply(STRATEGIES[0])
            val r = run(listOf(c, "-c", "id"))
            if (!(r.ok && r.text.contains("uid=0"))) continue
            pick()
            return true
        }
        suPath = null
        modeName = "没有可用的 su"
        return false
    }

    /** 开跑前把几条路都试一遍，挑第一条看得见别人数据的 */
    private fun pick() {
        for (s in STRATEGIES) {
            apply(s)
            if (countAppData() > 20) {
                modeName = s.name
                return
            }
        }
        apply(STRATEGIES[0])
        modeName = "su -c（受限，只看得见 ${countAppData()} 个数据目录）"
    }

    /** 自检用：把每条路的结果都报出来 */
    fun probe(log: (String) -> Unit) {
        for (s in STRATEGIES) {
            apply(s)
            val n = countAppData()
            log("  ${if (n > 20) "✓" else "✗"} ${s.name}  →  $n 个数据目录")
        }
        pick()
        log("  · 采用：$modeName")
    }

    private fun apply(s: Strategy) {
        current = s
        prefix = s.prefix
        useNsenter = s.nsenter
        useMaster = s.master
    }

    private fun countAppData(): Int =
        sh("ls -1 ${q("/data/data")} 2>/dev/null | wc -l").text.toIntOrNull() ?: 0

    fun sh(cmd: String, stdin: ByteArray? = null): ShellResult {
        val su = suPath ?: return ShellResult(-1, "没有可用的 su".toByteArray())
        val real = if (useNsenter) "nsenter -t 1 -m /system/bin/sh -c ${shq(cmd)}" else cmd
        val args = if (useMaster) listOf(su, "-M", "-c", real) else listOf(su, "-c", real)
        return run(args, stdin)
    }

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

    private fun run(args: List<String>, stdin: ByteArray? = null): ShellResult {
        return try {
            val p = ProcessBuilder(args).redirectErrorStream(true).start()
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
            ShellResult(-1, ((e.message ?: e.toString()) + "\n（${args.joinToString(" ")}）").toByteArray())
        }
    }
}
