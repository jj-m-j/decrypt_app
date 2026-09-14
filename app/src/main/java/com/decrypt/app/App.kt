package com.decrypt.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    val scroll = rememberScrollState()

    var srcDir by remember { mutableStateOf(prefs.srcDir) }
    var dstFile by remember { mutableStateOf(prefs.dstFile) }
    var backup by remember { mutableStateOf(prefs.backup) }
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }

    val logLines = remember { mutableStateListOf<String>() }
    val logChannel = remember { Channel<String>(Channel.UNLIMITED) }

    LaunchedEffect(Unit) {
        for (line in logChannel) logLines.add(line)
    }
    LaunchedEffect(logLines.size) {
        withFrameNanos { }
        scroll.animateScrollTo(scroll.maxValue)
    }
    LaunchedEffect(Unit) {
        rootOk = withContext(Dispatchers.IO) { RootShell.request() }
    }

    /** 两个动作都走这条通道，日志一边跑一边往下面滚 */
    fun launchLog(action: ((String) -> Unit) -> Unit) {
        focus.clearFocus()
        busy = true
        logLines.clear()
        scope.launch {
            withContext(Dispatchers.IO) { action { line -> logChannel.trySend(line) } }
            busy = false
        }
    }

    fun savePaths() {
        prefs.srcDir = srcDir.trim()
        prefs.dstFile = dstFile.trim()
        prefs.backup = backup
    }

    val keyboard = KeyboardOptions(imeAction = ImeAction.Done)
    val imeDone = KeyboardActions(onDone = { focus.clearFocus() })

    Scaffold(topBar = { SmallTopAppBar(title = "多宝专线解密") }) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(inner),
        ) {
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (rootOk == null) {
                        CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = when (rootOk) {
                            null -> "正在检查 root…"
                            true -> "root 权限正常"
                            false -> "没有 root，先去 root 管理器授权"
                        },
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SmallTitle("路径")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    TextField(
                        value = srcDir,
                        onValueChange = { srcDir = it },
                        label = "加密 yaml 所在目录",
                        enabled = !busy,
                        singleLine = true,
                        keyboardOptions = keyboard,
                        keyboardActions = imeDone,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = dstFile,
                        onValueChange = { dstFile = it },
                        label = "要覆盖写入的文件",
                        enabled = !busy,
                        singleLine = true,
                        keyboardOptions = keyboard,
                        keyboardActions = imeDone,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = "恢复默认路径",
                            onClick = {
                                srcDir = Prefs.DEFAULT_SRC
                                dstFile = Prefs.DEFAULT_DST
                                savePaths()
                                focus.clearFocus()
                            },
                            enabled = !busy,
                        )
                    }
                }
            }

            SmallTitle("选项")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
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

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = if (busy) "处理中…" else "解密并写入",
                    onClick = {
                        savePaths()
                        launchLog { Worker.run(srcDir, dstFile, backup, it) }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = "自检",
                    onClick = { launchLog { Worker.diagnose(it) } },
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                )
            }

            SmallTitle("日志")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = if (logLines.isEmpty()) {
                            "还没跑过。点「自检」先看看 root 和路径情况。"
                        } else {
                            logLines.joinToString("\n")
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
