package com.decrypt.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun Screen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val prefs = remember { Prefs(context) }

    var srcDir by remember { mutableStateOf(prefs.srcDir) }
    var dstFile by remember { mutableStateOf(prefs.dstFile) }
    var backup by remember { mutableStateOf(prefs.backup) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("还没开始。") }

    LaunchedEffect(Unit) {
        rootOk = withContext(Dispatchers.IO) { RootShell.request() }
    }

    fun start() {
        prefs.srcDir = srcDir.trim()
        prefs.dstFile = dstFile.trim()
        prefs.backup = backup
        focus.clearFocus()
        busy = true
        log = ""

        scope.launch {
            val buf = StringBuilder()
            val ok = withContext(Dispatchers.IO) {
                Worker.run(srcDir, dstFile, backup) { line -> buf.append(line).append('\n') }
            }
            log = (if (ok) "全部成功\n\n" else "没做完\n\n") + buf.toString().trim()
            busy = false
        }
    }

    val keyboard = KeyboardOptions(imeAction = ImeAction.Done)
    val actions = KeyboardActions(onDone = { focus.clearFocus() })

    Scaffold(topBar = { SmallTopAppBar(title = "多宝专线解密") }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Text(
                    text = when (rootOk) {
                        null -> "正在检查 root…"
                        true -> "root 权限正常"
                        false -> "未获得 root 权限，请先在 root 管理器里授权"
                    },
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    TextField(
                        value = srcDir,
                        onValueChange = { srcDir = it },
                        label = "加密 yaml 所在目录",
                        useLabelAsPlaceholder = true,
                        enabled = !busy,
                        singleLine = true,
                        keyboardOptions = keyboard,
                        keyboardActions = actions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = dstFile,
                        onValueChange = { dstFile = it },
                        label = "要覆盖写入的文件",
                        useLabelAsPlaceholder = true,
                        enabled = !busy,
                        singleLine = true,
                        keyboardOptions = keyboard,
                        keyboardActions = actions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "写入前备份目标文件",
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = backup, onCheckedChange = { backup = it }, enabled = !busy)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = if (busy) "处理中…" else "解密并写入",
                    onClick = { start() },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth()) {
                Text(
                    text = log,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
