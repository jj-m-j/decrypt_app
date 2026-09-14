package com.decrypt.app

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var srcDir: String
        get() = sp.getString(KEY_SRC, DEFAULT_SRC) ?: DEFAULT_SRC
        set(v) = sp.edit().putString(KEY_SRC, v).apply()

    var dstFile: String
        get() = sp.getString(KEY_DST, DEFAULT_DST) ?: DEFAULT_DST
        set(v) = sp.edit().putString(KEY_DST, v).apply()

    var backup: Boolean
        get() = sp.getBoolean(KEY_BAK, true)
        set(v) = sp.edit().putBoolean(KEY_BAK, v).apply()

    companion object {
        const val DEFAULT_SRC = "/data/user/0/com.nexgen.jisuwangluo/files/profiles"
        const val DEFAULT_DST = "/data/adb/box_bll/clash/proxies/多宝专线.yaml"

        private const val KEY_SRC = "src_dir"
        private const val KEY_DST = "dst_file"
        private const val KEY_BAK = "backup"
    }
}
