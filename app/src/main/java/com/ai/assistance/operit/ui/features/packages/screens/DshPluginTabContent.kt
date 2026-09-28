package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.system.Terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DSH 插件管理页（包管理界面第 5 个标签页）。
 *
 * 与 ToolPkg 安装链路完全独立：这里驱动的是 proot 内 dsh runtime 的 profile
 * 依赖树（pnpm 语义），支持三种安装来源：
 *  - npm 包名（如 dsh-purge）
 *  - tar.gz 链接（如 GitHub archive 直链）
 *  - 本地 tar.gz 文件（复制到应用外部目录后以 file: 协议安装）
 *
 * 所有命令通过 Terminal.executeHiddenCommand 投递到 proot Ubuntu 环境执行。
 */

private const val DSH_DEFAULT_PROFILE = "web"
private const val DSH_STAGE_DIR_NAME = "dsh_stage"
private const val DSH_EXECUTOR_KEY = "dsh_manager"

/** 单引号包裹参数，避免 spec / profile 中的特殊字符破坏命令。 */
private fun shellSingleQuote(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

/** 在 proot 内探测 dsh runtime 根目录，结果存入 shell 变量 DSH_ROOT。 */
private fun dshRootProbePrelude(): String =
    "DSH_ROOT=''; " +
        "for D in /root/operit-dsh-runtime /root/sidebar_deepseek_harness; do " +
        "if [ -f \"\$D/node_modules/@deepseek-ai/dsh/lib/bin.js\" ]; then DSH_ROOT=\"\$D\"; break; fi; " +
        "done; "

/** 包一层 runtime 守卫再执行 body；runtime 缺失时输出 __DSH_NORT__ 标记。 */
private fun buildDshExec(body: String): String =
    dshRootProbePrelude() +
        "if [ -z \"\$DSH_ROOT\" ]; then echo '__DSH_NORT__'; " +
        "else export DSH_HOME=\"\$DSH_ROOT/dsh-home\"; cd \"\$DSH_ROOT\"; " +
        body +
        "; fi"

/** 调用 dsh CLI，并把退出码以 [dsh-exit]N 形式回显。 */
private fun buildDshCliCall(args: String): String =
    "node \"\$DSH_ROOT/node_modules/@deepseek-ai/dsh/lib/bin.js\" $args 2>&1; " +
        "echo \"[dsh-exit]\$?\""

/** 读取指定 profile 的 package.json；runtime / profile 缺失时输出对应标记。 */
private fun buildProfileProbeCommand(profile: String): String =
    dshRootProbePrelude() +
        "if [ -z \"\$DSH_ROOT\" ]; then echo '__DSH_NORT__'; " +
        "else P=\$DSH_ROOT/dsh-home/profiles/${shellSingleQuote(profile)}/package.json; " +
        "echo \"__DSH_ROOT__\$DSH_ROOT\"; " +
        "if [ -f \"\$P\" ]; then cat \"\$P\"; else echo '__DSH_NOPROFILE__'; fi; fi"

/** 从 profile package.json 文本中解析已挂载的插件 bundle（过滤 dsh 核心包）。 */
private fun parseBundles(raw: String): List<String> {
    val idx = raw.indexOf("\"bundles\"")
    if (idx < 0) return emptyList()
    val tail = raw.substring(idx)
    val close = tail.indexOf(']')
    val segment = if (close >= 0) tail.substring(0, close) else tail
    return Regex("\"([^\"]+)\"")
        .findAll(segment)
        .map { it.groupValues[1] }
        .filter { it != "bundles" && !it.startsWith("@deepseek-ai/") }
        .toList()
}

private enum class DshInstallSource {
    URL,
    NPM,
    LOCAL_FILE
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex("_display_name")
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    }

private fun sanitizeFileName(raw: String?): String {
    val cleaned = (raw ?: "").replace(Regex("[^A-Za-z0-9._-]"), "_")
    return cleaned.ifBlank { "dsh-plugin.tar.gz" }
}

/** 把应用外部目录路径转换成 proot 内可见路径。 */
private fun toProotVisiblePath(file: File): String {
    val path = file.absolutePath
    return if (path.startsWith("/storage/emulated/0")) {
        "/sdcard" + path.removePrefix("/storage/emulated/0")
    } else {
        path
    }
}

/** 把用户选择的本地 tar.gz 复制到应用外部目录，返回落盘后的文件。 */
private suspend fun stageLocalPlugin(context: Context, uri: Uri): File? =
    withContext(Dispatchers.IO) {
        try {
            val stageDir = context.getExternalFilesDir(DSH_STAGE_DIR_NAME)
            if (stageDir == null) return@withContext null
            if (!stageDir.exists() && !stageDir.mkdirs()) return@withContext null
            stageDir.listFiles()?.forEach { it.delete() }
            val fileName = sanitizeFileName(queryDisplayName(context, uri))
            val dest = File(stageDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            dest
        } catch (e: Exception) {
            null
        }
    }

@Composable
fun DshPluginTabContent(
    searchQuery: String,
    snackbarHost: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf(DSH_DEFAULT_PROFILE) }
    var runtimeRoot by remember { mutableStateOf<String?>(null) }
    var probeDone by remember { mutableStateOf(false) }
    var plugins by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf(DshInstallSource.URL) }
    var specInput by remember { mutableStateOf("") }
    var localName by remember { mutableStateOf<String?>(null) }
    var logText by remember { mutableStateOf("") }
    var showLog by remember { mutableStateOf(false) }

    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val staged = stageLocalPlugin(context, uri)
                if (staged == null) {
                    snackbarHost("读取本地文件失败")
                    return@launch
                }
                localName = staged.name
                specInput = "file:" + toProotVisiblePath(staged)
                statusText = "已选择：${staged.name}"
            }
        }

    suspend fun fetchPlugins(targetProfile: String): List<String> {
        val result =
            withContext(Dispatchers.IO) {
                Terminal.getInstance(context)
                    .executeHiddenCommand(
                        buildProfileProbeCommand(targetProfile),
                        DSH_EXECUTOR_KEY,
                        60_000L
                    )
            }
        val output = result.output
        if (output.contains("__DSH_NORT__")) {
            runtimeRoot = null
            return emptyList()
        }
        runtimeRoot =
            Regex("__DSH_ROOT__(.+)")
                .find(output)
                ?.groupValues
                ?.get(1)
                ?.trim()
        return parseBundles(output)
    }

    suspend fun refresh(targetProfile: String) {
        refreshing = true
        try {
            plugins = fetchPlugins(targetProfile)
            probeDone = true
        } catch (e: Exception) {
            statusText = "探测失败：${e.message ?: "未知错误"}"
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(profile) {
        delay(400)
        refresh(profile)
    }

    fun runPluginCommand(args: String, timeoutMs: Long, onDone: (Boolean, String) -> Unit) {
        scope.launch {
            busy = true
            statusText = "正在执行：$args"
            val command = buildDshExec(buildDshCliCall(args))
            val result =
                withContext(Dispatchers.IO) {
                    Terminal.getInstance(context)
                        .executeHiddenCommand(command, DSH_EXECUTOR_KEY, timeoutMs)
                }
            logText = result.output
            val ok = result.output.contains("[dsh-exit]0")
            plugins = fetchPlugins(profile)
            busy = false
            onDone(ok, result.output)
        }
    }

    fun startInstall() {
        val spec = specInput.trim()
        if (profile.isBlank()) {
            snackbarHost("请填写 profile")
            return
        }
        if (spec.isBlank()) {
            snackbarHost("请填写插件来源")
            return
        }
        runPluginCommand(
            "plugin --profile ${shellSingleQuote(profile)} add ${shellSingleQuote(spec)}",
            600_000L
        ) { ok, _ ->
            statusText =
                if (ok) "安装完成：$spec" else "安装失败，请查看日志"
            if (ok) {
                specInput = ""
                localName = null
                snackbarHost("已安装：$spec（重启 dsh 服务后生效）")
            } else {
                showLog = true
            }
        }
    }

    fun startUninstall(pluginName: String) {
        runPluginCommand(
            "plugin --profile ${shellSingleQuote(profile)} remove ${shellSingleQuote(pluginName)}",
            300_000L
        ) { ok, _ ->
            statusText = if (ok) "已卸载：$pluginName" else "卸载失败，请查看日志"
            if (ok) {
                snackbarHost("已卸载：$pluginName（重启 dsh 服务后生效）")
            } else {
                showLog = true
            }
        }
    }

    val visiblePlugins =
        remember(plugins, searchQuery) {
            val query = searchQuery.trim()
            if (query.isEmpty()) {
                plugins
            } else {
                plugins.filter { it.contains(query, ignoreCase = true) }
            }
        }

    val emptyText =
        when {
            probeDone && runtimeRoot == null -> "未检测到 DSH runtime"
            plugins.isEmpty() -> "该 profile 下暂无 dsh 插件"
            else -> "没有匹配的插件"
        }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部状态条：runtime 探测结果 + 刷新
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text =
                            when {
                                runtimeRoot != null -> "runtime: $runtimeRoot"
                                probeDone -> "runtime: 未检测到"
                                else -> "runtime: 检测中…"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            when {
                                runtimeRoot != null -> MaterialTheme.colorScheme.onSurfaceVariant
                                probeDone -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { scope.launch { refresh(profile) } },
                            enabled = !busy
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (probeDone && runtimeRoot == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "未找到 dsh runtime（需先安装 operit-dsh-runtime 或 sidebar_deepseek_harness）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // profile 选择
        OutlinedTextField(
            value = profile,
            onValueChange = { profile = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            enabled = !busy,
            singleLine = true,
            label = { Text("profile") },
            placeholder = { Text(DSH_DEFAULT_PROFILE) }
        )

        // 已安装插件列表
        if (visiblePlugins.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visiblePlugins) { name ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "profile: $profile",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { startUninstall(name) },
                                enabled = !busy
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "卸载 $name",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 安装区
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DshInstallSource.entries.forEach { src ->
                        val label =
                            when (src) {
                                DshInstallSource.URL -> "链接"
                                DshInstallSource.NPM -> "包名"
                                DshInstallSource.LOCAL_FILE -> "本地文件"
                            }
                        OutlinedButton(onClick = { source = src }, enabled = !busy) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (source == src) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = specInput,
                    onValueChange = { specInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    singleLine = true,
                    label = {
                        Text(
                            when (source) {
                                DshInstallSource.URL -> "tar.gz 链接"
                                DshInstallSource.NPM -> "npm 包名"
                                DshInstallSource.LOCAL_FILE -> "本地文件（file: 路径）"
                            }
                        )
                    },
                    placeholder = {
                        Text(
                            text =
                                when (source) {
                                    DshInstallSource.URL ->
                                        "https://github.com/owner/repo/archive/refs/heads/master.tar.gz"
                                    DshInstallSource.NPM -> "dsh-purge"
                                    DshInstallSource.LOCAL_FILE -> "请先选择文件"
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                if (source == DshInstallSource.LOCAL_FILE) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { filePicker.launch("*/*") }, enabled = !busy) {
                            Text("选择文件")
                        }
                        Text(
                            text = localName ?: "未选择",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { startInstall() },
                        enabled =
                            !busy &&
                                !refreshing &&
                                runtimeRoot != null &&
                                specInput.isNotBlank()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("安装")
                    }
                    if (logText.isNotBlank()) {
                        TextButton(onClick = { showLog = true }, enabled = !busy) {
                            Text("日志")
                        }
                    }
                }
            }
        }

        statusText?.let { text ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("dsh 执行日志") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logText.ifBlank { "(无输出)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLog = false }) { Text("关闭") }
            }
        )
    }
}
