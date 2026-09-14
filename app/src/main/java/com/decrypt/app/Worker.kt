package com.decrypt.app

object Worker {

    /** 整个流程：拿 root → 在源目录里找最新的加密 yaml → 解密 → 覆盖写入目标文件 */
    fun run(srcDir: String, dstFile: String, backup: Boolean, log: (String) -> Unit): Boolean {
        log("请求 root 权限…")
        if (!RootShell.request()) {
            log("没拿到 root，确认设备已 root 并且授权给了本应用")
            return false
        }
        log("root 正常")

        val src = srcDir.trim().trimEnd('/')
        if (src.isEmpty()) {
            log("源目录为空")
            return false
        }

        val listing = RootShell.sh("ls -1t '$src' 2>&1")
        val names = listing.text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(40)
            .toList()

        if (!listing.ok || names.isEmpty()) {
            log("读不到 $src：${listing.text.trim()}")
            return false
        }
        log("目录里 ${names.size} 个文件，按时间从新到旧试解密…")

        var yaml: String? = null
        var used = ""
        for (name in names) {
            val raw = RootShell.sh("cat '$src/$name'")
            if (!raw.ok || raw.out.isEmpty()) continue
            val plain = ProfileCrypto.decrypt(raw.out)
            if (plain != null) {
                yaml = plain
                used = name
                break
            }
        }

        if (yaml == null) {
            log("目录里没有可解密的 yaml")
            return false
        }
        val bytes = yaml.toByteArray(Charsets.UTF_8)
        log("已解密 $used（${bytes.size} 字节）")

        val dst = dstFile.trim()
        if (dst.isEmpty()) {
            log("目标路径为空")
            return false
        }

        val ok = RootShell.writeFile(dst, bytes, backup, log)
        log(if (ok) "搞定" else "写入后校验对不上，检查一下目标路径")
        return ok
    }
}
