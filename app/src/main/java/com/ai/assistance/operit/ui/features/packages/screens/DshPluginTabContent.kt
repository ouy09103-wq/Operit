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
 *
 * proot 环境自愈（参考 DSH-APP/DSHA 的 pnpm-env-fix.sh / heal-pnpm-shells.py）：
 *  - proot 的 link2symlink 把 Android 私有目录里无法创建的真硬链接模拟成
 *    .l2s 符号链，pnpm 默认硬链接铺包，临时文件一清理链就悬空（EPERM / ENOENT）；
 *    改写 package-import-method=copy 从源头绕开；
 *  - GitHub 来源插件 prepare 失败会留 .ignored_<name> 空壳，安装后自动换回。
 */

private const val DSH_DEFAULT_PROFILE = "web"
private const val DSH_STAGE_DIR_NAME = "dsh_stage"
private const val DSH_EXECUTOR_KEY = "dsh_manager"

/**
 * dsh 数据 home。官方默认 / DSHA 规范都是 ~/.dsh；autostart 拉起的服务不带
 * DSH_HOME 也读这里 —— 三方（CLI / 服务 / 手动命令）只有锚定同一棵树，
 * 安装的插件才会被服务加载。
 */
private const val DSH_HOME_DIR = "/root/.dsh"

/** dsh web 服务默认端口。 */
private const val DSH_DEFAULT_PORT = "3081"

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
        "else export DSH_HOME=\"$DSH_HOME_DIR\"; cd \"\$DSH_ROOT\"; " +
        body +
        "; fi"

/** 调用 dsh CLI，并把退出码以 [dsh-exit]N 形式回显。 */
private fun buildDshCliCall(args: String): String =
    "node \"\$DSH_ROOT/node_modules/@deepseek-ai/dsh/lib/bin.js\" $args 2>&1; " +
        "echo \"[dsh-exit]\$?\""

/**
 * pnpm 环境自愈（参照 DSHA 的 pnpm-env-fix.sh，含本机实测补强）。
 *
 * proot 的 link2symlink 把 Android 私有目录里无法创建的真硬链接模拟成 .l2s
 * 符号链；pnpm 默认用硬链接把包从 store 铺到 node_modules，临时文件一清理
 * 链就悬空，rename 报 EPERM，表现为各种莫名的安装失败。改写
 * package-import-method=copy 从源头绕开（proot 下硬链接本来就是模拟的，
 * 也没真省空间）；顺带清 store/tmp 失败残留与 /tmp 下的陈旧操作锁
 * （proot fake-root 下 getuid=0 与旧锁属主不符会报 ERR_PNPM_STORE_DIR_OPEN_OPERATION_LOCK）。
 * 全程幂等，只碰 pnpm 自己的配置与临时目录。
 */
private fun buildPnpmEnvHealCommand(): String =
    "NPMRC=/root/.npmrc; touch \"\$NPMRC\" 2>/dev/null; " +
        "if grep -q '^package-import-method=' \"\$NPMRC\" 2>/dev/null; then " +
        "grep -q '^package-import-method=copy' \"\$NPMRC\" || " +
        "sed -i 's|^package-import-method=.*|package-import-method=copy|' \"\$NPMRC\"; " +
        "else printf 'package-import-method=copy\\n' >> \"\$NPMRC\"; fi; " +
        "grep -q '^side-effects-cache=' \"\$NPMRC\" 2>/dev/null || " +
        "printf 'side-effects-cache=false\\n' >> \"\$NPMRC\"; " +
        "for S in /root/.local/share/pnpm/store/v* /root/.pnpm-store/v*; do " +
        "if [ -d \"\$S/tmp\" ]; then " +
        "find \"\$S/tmp\" -mindepth 1 -maxdepth 1 -exec rm -rf {} + 2>/dev/null; fi; " +
        "done; " +
        "rm -rf /tmp/pnpm-store-operation-locks-* 2>/dev/null; " +
        "echo '__DSH_PNPM_ENV_OK__'"

/**
 * 修 pnpm 装一半留下的 `.ignored_<name>` 空壳（参照 DSHA heal-pnpm-shells.py）。
 *
 * GitHub 来源的插件 prepare/build 失败时（pnpm 11+ 默认拒绝未知包的 build
 * script），pnpm 把已落地的完整目录 rename 成 .ignored_<name>，原位只留一个
 * 含 _pnpmPlaceholder 标记的 package.json 壳 —— dsh 加载到的是壳，插件形同
 * 不存在。仅在「原位确认是壳、且 .ignored_ 看起来完整」时换回，其余不碰。
 */
private fun buildIgnoredShellHealCommand(): String =
    "for NM in /root/.dsh/profiles/*/node_modules; do " +
        "[ -d \"\$NM\" ] || continue; " +
        "for IG in \"\$NM\"/.ignored_*; do " +
        "[ -d \"\$IG\" ] || continue; " +
        "[ -f \"\$IG/package.json\" ] || continue; " +
        "N=\${IG##*/}; N=\${N#.ignored_}; T=\"\$NM/\$N\"; " +
        "H=0; " +
        "if [ ! -e \"\$T\" ]; then H=1; " +
        "elif [ ! -f \"\$T/package.json\" ]; then H=1; " +
        "elif grep -q '_pnpmPlaceholder' \"\$T/package.json\" 2>/dev/null; then H=1; " +
        "else C=\$(ls -A \"\$T\" 2>/dev/null | wc -l); [ \"\$C\" -le 1 ] && H=1; fi; " +
        "if [ \"\$H\" = 1 ]; then rm -rf \"\$T\" 2>/dev/null; mv \"\$IG\" \"\$T\" 2>/dev/null; fi; " +
        "done; done; " +
        "echo '__DSH_SHELL_HEAL_OK__'"

/**
 * 重启 dsh web 服务：按进程 args 找旧 node 实例 kill 掉，再 setsid 脱离
 * 会话拉起。不依赖 curl / ps（本 rootfs 二者缺失），只用 /proc 扫描 +
 * kill + setsid + pidfile；跨应用（原版）的 node 因 uid 不同 kill 失败，
 * 不会误伤。端口从 3081 起用 node 探测自适应：与本机其它实例共存时
 * 自动顺延到空闲端口。
 */
private fun buildDshRestartCommand(): String =
    "KILLED=0; " +
        "for P in /proc/[0-9]*/cmdline; do " +
        "PID=\${P#/proc/}; PID=\${PID%/cmdline}; " +
        "FIRST=\$(tr '\\0' '\\n' < \"\$P\" 2>/dev/null | head -n 1); " +
        "case \"\$FIRST\" in */node|node) ;; *) continue;; esac; " +
        "tr '\\0' '\\n' < \"\$P\" 2>/dev/null | grep -q 'operit-dsh-runtime' || continue; " +
        "kill \"\$PID\" 2>/dev/null && KILLED=\$((KILLED + 1)); " +
        "done; " +
        "sleep 1; " +
        "PORT=$DSH_DEFAULT_PORT; " +
        "while [ \"\$PORT\" -lt 3100 ]; do " +
        "/usr/bin/node -e \"var s=require('net').createServer();" +
        "s.once('error',function(){process.exit(1)});" +
        "s.listen(\$PORT,'127.0.0.1',function(){s.close();process.exit(0)});\" 2>/dev/null && break; " +
        "PORT=\$((PORT + 1)); " +
        "done; " +
        "setsid /usr/bin/node \"\$DSH_ROOT/node_modules/@deepseek-ai/dsh/lib/bin.js\" " +
        "web --host 127.0.0.1 --port \"\$PORT\" " +
        "--trusted-host \"127.0.0.1:\$PORT\" --no-open " +
        ">> \"\$DSH_ROOT/dsh-web.log\" 2>&1 < /dev/null & " +
        "printf '%s\\n' \"\$!\" > \"\$DSH_ROOT/dsh-web.pid\"; " +
        "echo \"__DSH_RESTART_OK__=\$KILLED PORT=\$PORT\""

/** 读取指定 profile 的 package.json；runtime / profile 缺失时输出对应标记。 */
private fun buildProfileProbeCommand(profile: String): String =
    dshRootProbePrelude() +
        "if [ -z \"\$DSH_ROOT\" ]; then echo '__DSH_NORT__'; " +
        "else P=\"$DSH_HOME_DIR/profiles/${shellSingleQuote(profile)}/package.json\"; " +
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

    /** 执行一段在 runtime 守卫内运行的原始 shell 体（自愈 / 重启等）。 */
    fun runRawCommand(body: String, timeoutMs: Long, onDone: (Boolean, String) -> Unit) {
        scope.launch {
            busy = true
            val command = buildDshExec(body)
            val result =
                withContext(Dispatchers.IO) {
                    Terminal.getInstance(context)
                        .executeHiddenCommand(command, DSH_EXECUTOR_KEY, timeoutMs)
                }
            logText = result.output
            val ok = !result.output.contains("__DSH_NORT__")
            plugins = fetchPlugins(profile)
            busy = false
            onDone(ok, result.output)
        }
    }

    /** 安装后处理：修 `.ignored_` 空壳 → 重启服务，使插件立即生效。 */
    fun applyAndRestart(prefixMessage: String) {
        statusText = "正在应用并重启服务…"
        runRawCommand(
            buildIgnoredShellHealCommand() + "; " + buildDshRestartCommand(),
            120_000L
        ) { ok, out ->
            val port = Regex("PORT=(\\d+)").find(out)?.groupValues?.get(1)
            statusText =
                if (ok) {
                    "$prefixMessage（服务已重启${if (port != null) "，端口 $port" else ""}）"
                } else {
                    "$prefixMessage（服务重启失败，可手动重启）"
                }
            if (!ok) showLog = true
        }
    }

    fun runPluginCommand(args: String, timeoutMs: Long, onDone: (Boolean, String) -> Unit) {
        scope.launch {
            busy = true
            statusText = "正在执行：$args"
            // 执行前先做 pnpm 环境自愈（copy 导入模式），避免硬链接模拟链引发的安装失败
            val command = buildDshExec(buildPnpmEnvHealCommand() + "; " + buildDshCliCall(args))
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

    fun restartService() {
        runRawCommand(
            buildIgnoredShellHealCommand() + "; " + buildDshRestartCommand(),
            120_000L
        ) { ok, out ->
            val port = Regex("PORT=(\\d+)").find(out)?.groupValues?.get(1)
            statusText =
                if (ok) {
                    "服务已重启${if (port != null) "（端口 $port）" else ""}"
                } else {
                    "服务重启失败，请查看日志"
                }
            if (ok) {
                snackbarHost("dsh 服务已重启${if (port != null) "（端口 $port）" else ""}")
            } else {
                showLog = true
            }
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
            if (ok) {
                specInput = ""
                localName = null
                applyAndRestart("已安装：$spec")
                snackbarHost("已安装：$spec")
            } else {
                statusText = "安装失败，请查看日志"
                showLog = true
            }
        }
    }

    fun startUninstall(pluginName: String) {
        runPluginCommand(
            "plugin --profile ${shellSingleQuote(profile)} remove ${shellSingleQuote(pluginName)}",
            300_000L
        ) { ok, _ ->
            if (ok) {
                applyAndRestart("已卸载：$pluginName")
                snackbarHost("已卸载：$pluginName")
            } else {
                statusText = "卸载失败，请查看日志"
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
                    TextButton(
                        onClick = { restartService() },
                        enabled = !busy && !refreshing && runtimeRoot != null
                    ) {
                        Text("重启服务")
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