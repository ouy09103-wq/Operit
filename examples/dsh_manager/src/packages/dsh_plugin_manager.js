/* METADATA
{
    "name": "dsh_manager",
    "display_name": { "zh": "DSH 插件管理器（Operit 分支版）", "en": "DSH Plugin Manager (Operit Branch)" },
    "description": {
        "zh": "在 Operit 内原生管理 dsh 插件：自建独立 DSH runtime（不依赖 Operit 市场的 DeepSeek Harness 侧边栏包），启停 Web 服务，搜索/安装/卸载 dsh 插件，并做安装后自检。",
        "en": "Manage dsh plugins natively inside Operit: self-hosted DSH runtime (independent of the market DeepSeek Harness sidebar package), service control, plugin search/install/remove, plus post-install verification."
    },
    "category": "System",
    "enabledByDefault": false,
    "tools": [
        {
            "name": "dsh_runtime_status",
            "description": { "zh": "检查 node/npm/pnpm、自建 DSH runtime、Web 服务与日志状态。", "en": "Report node/npm/pnpm, self-hosted DSH runtime, web service and log status." },
            "parameters": [
                { "name": "port", "description": { "zh": "可选：Web 端口，默认 3081", "en": "Optional: web port, default 3081" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_ensure_runtime",
            "description": { "zh": "在 /root/operit-dsh-runtime 安装或更新 @deepseek-ai/dsh（含 node-pty 编译依赖与 pnpm）。", "en": "Install or update @deepseek-ai/dsh under /root/operit-dsh-runtime (includes node-pty build deps and pnpm)." },
            "parameters": [
                { "name": "version", "description": { "zh": "可选：版本或 latest，默认 latest", "en": "Optional: version or latest, default latest" }, "type": "string", "required": false },
                { "name": "timeoutMs", "description": { "zh": "可选：超时毫秒，默认 900000", "en": "Optional: timeout ms, default 900000" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_service_start",
            "description": { "zh": "启动自建 DSH Web 服务（仅回环地址，写 PID 文件后台常驻）。", "en": "Start the self-hosted DSH web service (loopback only, PID-file detached)." },
            "parameters": [
                { "name": "port", "description": { "zh": "可选：Web 端口，默认 3081", "en": "Optional: web port, default 3081" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_service_stop",
            "description": { "zh": "按 PID 文件停止自建 DSH Web 服务。", "en": "Stop the self-hosted DSH web service via PID file." },
            "parameters": []
        },
        {
            "name": "dsh_service_restart",
            "description": { "zh": "重启自建 DSH Web 服务（插件安装后生效）。", "en": "Restart the self-hosted DSH web service (apply plugin changes)." },
            "parameters": [
                { "name": "port", "description": { "zh": "可选：Web 端口，默认 3081", "en": "Optional: web port, default 3081" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_service_autostart",
            "description": { "zh": "部署幂等自启：挂 shell 启动钩子，新会话自动补活服务并纠正 pidfile（proot 无 systemd/cron 场景）。", "en": "Deploy idempotent autostart: hook into shell startup so new sessions self-heal the service and fix the pidfile (for proot without systemd/cron)." },
            "parameters": [
                { "name": "port", "description": { "zh": "可选：Web 端口，默认 3081", "en": "Optional: web port, default 3081" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_plugin_list",
            "description": { "zh": "列出指定 profile 已安装的 dsh 插件（profile 不存在会自动初始化）。", "en": "List installed dsh plugins for a profile (auto-initializes missing profile)." },
            "parameters": [
                { "name": "profile", "description": { "zh": "可选：profile 名，默认 web", "en": "Optional: profile name, default web" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_plugin_add",
            "description": { "zh": "安装 dsh 插件：npm 包名 / github:owner/repo / file:./path，完成后自动自检。", "en": "Install a dsh plugin: npm name / github:owner/repo / file:./path; auto-verifies after install." },
            "parameters": [
                { "name": "spec", "description": { "zh": "插件来源（必填）", "en": "Plugin spec (required)" }, "type": "string", "required": true },
                { "name": "profile", "description": { "zh": "可选：profile 名，默认 web", "en": "Optional: profile name, default web" }, "type": "string", "required": false },
                { "name": "timeoutMs", "description": { "zh": "可选：超时毫秒，默认 300000", "en": "Optional: timeout ms, default 300000" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_plugin_remove",
            "description": { "zh": "卸载 dsh 插件。", "en": "Remove a dsh plugin." },
            "parameters": [
                { "name": "name", "description": { "zh": "插件包名（必填）", "en": "Plugin package name (required)" }, "type": "string", "required": true },
                { "name": "profile", "description": { "zh": "可选：profile 名，默认 web", "en": "Optional: profile name, default web" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_plugin_doctor",
            "description": { "zh": "安装后自检：核对 profile 的 dsh.profile.bundles 是否已挂载指定插件，并回显 profile 目录结构。", "en": "Post-install doctor: check whether the plugin is mounted in the profile's dsh.profile.bundles and dump profile layout." },
            "parameters": [
                { "name": "name", "description": { "zh": "可选：插件包名，留空则只回显 profile 状态", "en": "Optional: plugin package name; leave empty to dump profile state only" }, "type": "string", "required": false },
                { "name": "profile", "description": { "zh": "可选：profile 名，默认 web", "en": "Optional: profile name, default web" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "dsh_plugin_find",
            "description": { "zh": "在 npm registry 搜索 dsh 插件候选。", "en": "Search npm registry for dsh plugin candidates." },
            "parameters": [
                { "name": "keyword", "description": { "zh": "关键词，默认 dsh-plugin", "en": "Keyword, default dsh-plugin" }, "type": "string", "required": false },
                { "name": "limit", "description": { "zh": "可选：返回条数，默认 15", "en": "Optional: result count, default 15" }, "type": "string", "required": false }
            ]
        }
    ]
}*/
/* 说明：
 * - 本包是 Operit 侧的 Sandbox Package，直接跑在 Operit 的 Linux 终端环境里，
 *   不读取、不依赖 Operit 市场那个 com.operit.deepseek_harness 侧边栏包。
 * - runtime 自建于 /root/operit-dsh-runtime，DSH_HOME 隔离在该目录下的 dsh-home，
 *   与任何已存在的 ~/sidebar_deepseek_harness 完全互不干扰。
 * - dsh plugin --profile <name> ls/add/remove 是 pnpm 透传子命令；profile 不存在时
 *   会由 dsh 自动初始化，无需先手动建 profile。
 */
const DshPluginManager = (function () {
    const RUNTIME_DIR = "/root/operit-dsh-runtime";
    const DSH_HOME_DIR = `${RUNTIME_DIR}/dsh-home`;
    const DSH_BIN = `${RUNTIME_DIR}/node_modules/.bin/dsh`;
    const WEB_LOG = `${RUNTIME_DIR}/dsh-web.log`;
    const INSTALL_LOG = `${RUNTIME_DIR}/install.log`;
    const PID_FILE = `${RUNTIME_DIR}/dsh-web.pid`;
    const AUTOSTART_FILE = `${RUNTIME_DIR}/dsh-autostart.sh`;
    const AUTOSTART_HOOK = "/etc/profile.d/99-dsh-autostart.sh";
    const SESSION_NAME = "operit_dsh_plugin_mgr";
    const DEFAULT_PROFILE = "web";
    const DEFAULT_PORT = 3081;
    const REGISTRY = "https://registry.npmmirror.com/";
    const MIN_TIMEOUT_MS = 3000;

    function q(value) {
        return "'" + String(value).replace(/'/g, "'\\''") + "'";
    }

    function norm(value) {
        return value === null || value === undefined ? "" : String(value);
    }

    function normalizeTimeout(value, fallback) {
        const base = fallback || 60000;
        const parsed = parseInt(value, 10);
        if (isNaN(parsed)) return base;
        return Math.max(MIN_TIMEOUT_MS, parsed);
    }

    async function run(command, timeoutMs) {
        const timeout = normalizeTimeout(timeoutMs);
        const session = await Tools.System.terminal.create(SESSION_NAME);
        const sessionId = session.sessionId;
        const begin = "__DSHPM_BEGIN__";
        const end = "__DSHPM_END__";
        // 用哨兵包裹，只取标记之间的内容：终端会话里可能残留上一次的输入/提示符/echo，
        // 直接取全量输出会让 JSON 解析被前导噪音污染。
        const wrapped = `printf '%s\\n' '${begin}'; ${command}; printf '%s\\n' '${end}'`;
        const result = await Tools.System.terminal.exec(sessionId, wrapped, timeout);
        let output = norm(result && result.output);
        const b = output.indexOf(begin);
        const e = output.indexOf(end);
        if (b >= 0 && e > b) {
            output = output.slice(b + begin.length, e).replace(/^\s*\n/, "").replace(/\s+$/, "");
        }
        return {
            command,
            sessionId,
            exitCode: typeof (result && result.exitCode) === "number" ? result.exitCode : -1,
            timedOut: !!(result && result.timedOut === true),
            output,
        };
    }

    function envPrelude() {
        return [
            `export DSH_HOME=${q(DSH_HOME_DIR)}`,
            `export npm_config_registry=${q(REGISTRY)}`,
            `export NPM_CONFIG_REGISTRY=${q(REGISTRY)}`,
        ].join("; ");
    }

    // profile 状态读 profiles/<p>/package.json：
    // - dsh.profile.bundles   = 真正作为 profile 层被激活的 bundle
    // - dependencies          = 已装入但未声明 dsh.bundle 的普通依赖（不会激活为层）
    // 判定插件是否安装必须两处都看，否则会漏判「只进 dependencies」的插件。
    async function readProfile(profile) {
        const pkgPath = `${DSH_HOME_DIR}/profiles/${profile}/package.json`;
        const cmd = `test -f ${q(pkgPath)} && cat ${q(pkgPath)} || echo "__NO_PROFILE__"`;
        const result = await run(cmd, 15000);
        if (result.output.indexOf("__NO_PROFILE__") >= 0) {
            return { exists: false, bundles: [], dependencies: [], raw: "" };
        }
        // 终端会话可能残留提示符/引号/echo，输出不保证是纯 JSON。
        // 从第一个 { 截到最后一个 } 再解析，避免前导噪音导致整体解析失败。
        const raw = result.output;
        const start = raw.indexOf("{");
        const end = raw.lastIndexOf("}");
        const jsonText = (start >= 0 && end > start) ? raw.slice(start, end + 1) : "";
        let bundles = [];
        let dependencies = [];
        let parsed = null;
        try {
            parsed = JSON.parse(jsonText);
            const p = parsed && parsed.dsh && parsed.dsh.profile;
            if (p && Array.isArray(p.bundles)) bundles = p.bundles;
            const deps = parsed && parsed.dependencies;
            if (deps && typeof deps === "object") dependencies = Object.keys(deps);
        } catch (e) { /* 解析失败则保持空数组，raw 里会回显原始内容 */ }
        return { exists: true, bundles, dependencies, raw: jsonText || raw };
    }

    // 包名归一：去掉 scope 前缀再取 @ 之前的部分（兼容 @scope/name@1.2.3 / file: 写法）
    function shortNameOf(name) {
        return String(name || "").replace(/^@[^/]+\//, "").split("@")[0];
    }

    // 生成一段 shell 片段：扫描 /proc 找到匹配「dsh web --port <port>」的进程 PID。
    // 不信任 pidfile（start 会写新 PID，pidfile 可能残留已死号）。
    // 排除 sidebar_deepseek_harness（默认也占 3081），避免误伤用户既有侧边栏。
    function findPidCmd(port) {
        return `for p in /proc/[0-9]*; do c=$(tr '\\0' ' ' < $p/cmdline 2>/dev/null); ` +
            `case "$c" in *dsh*web*--port\\ ${port}*) case "$c" in *sidebar_deepseek_harness*) ;; *) basename $p; break;; esac;; esac; done`;
    }

    async function runDsh(args, timeoutMs) {
        const command = [
            `mkdir -p ${q(DSH_HOME_DIR)}`,
            `cd ${q(RUNTIME_DIR)}`,
            envPrelude(),
            `${q(DSH_BIN)} ${args} 2>&1`,
        ].join("; ");
        return await run(command, timeoutMs || 180000);
    }

    async function dsh_runtime_status(params) {
        const port = parseInt(params && params.port, 10) || DEFAULT_PORT;
        const command = [
            `echo "=== node ==="`,
            `which node >/dev/null 2>&1 && node -v 2>&1 || echo "node-missing"`,
            `which npm >/dev/null 2>&1 && npm -v 2>&1 || echo "npm-missing"`,
            `which pnpm >/dev/null 2>&1 && pnpm -v 2>&1 || echo "pnpm-missing"`,
            `echo "=== runtime ==="`,
            `test -d ${q(RUNTIME_DIR)} && echo "runtime-dir=present" || echo "runtime-dir=missing"`,
            `test -x ${q(DSH_BIN)} && ${q(DSH_BIN)} --version 2>&1 | head -n 1 || echo "dsh-missing"`,
            `echo "=== service ==="`,
            `test -f ${q(PID_FILE)} && echo "pid-file=$(cat ${q(PID_FILE)})" || echo "pid-file=absent"`,
            // 判活不再只看 pidfile（dsh_service_start 会写新 PID，pidfile 可能存已死 PID 造成假阴性）。
            // 先按 cmdline 扫描真实存活进程；命中即以真身为准并回写 pidfile（自愈同步）。
            `LIVE=$(${findPidCmd(port)})`,
            `if [ -n "$LIVE" ]; then echo "service=running"; echo "$LIVE" > ${q(PID_FILE)}; echo "service-pid=$LIVE"; else echo "service=not-running"; fi`,
            `kill -0 "$(cat ${q(PID_FILE)} 2>/dev/null)" 2>/dev/null && echo "pidfile-alive=yes" || echo "pidfile-alive=no"`,
            `curl -s -o /dev/null -w 'http=%{http_code}\\n' --max-time 5 http://127.0.0.1:${port}/ 2>&1 || echo "http-unreachable"`,
            `echo "=== dsh-home ==="`,
            `test -d ${q(DSH_HOME_DIR)} && ls ${q(DSH_HOME_DIR)} || echo "dsh-home=missing"`,
            `test -d ${q(DSH_HOME_DIR)}/profiles && ls ${q(DSH_HOME_DIR)}/profiles || echo "profiles=none"`,
            `echo "=== log tail ==="`,
            `test -f ${q(WEB_LOG)} && tail -n 5 ${q(WEB_LOG)} || echo "no-log"`,
        ].join("; ");
        const result = await run(command, 40000);
        return {
            success: result.exitCode === 0,
            message: "DSH runtime 状态查询完成",
            data: { port, exitCode: result.exitCode, timedOut: result.timedOut, output: result.output },
        };
    }

    async function dsh_ensure_runtime(params) {
        const version = norm(params && params.version).trim() || "latest";
        const spec = `@deepseek-ai/dsh@${version}`;
        const command = [
            `mkdir -p ${q(RUNTIME_DIR)} ${q(DSH_HOME_DIR)}`,
            `cd ${q(RUNTIME_DIR)}`,
            `[ -f package.json ] || npm init -y >/dev/null 2>&1`,
            envPrelude(),
            `if ! which cc >/dev/null 2>&1 && ! which gcc >/dev/null 2>&1; then apt-get update -y >/dev/null 2>&1; apt-get install -y --no-install-recommends build-essential >/dev/null 2>&1; fi`,
            `if ! which pnpm >/dev/null 2>&1; then npm install -g pnpm >/dev/null 2>&1; fi`,
            `npm install --omit=dev --no-fund --no-audit ${q(spec)} > ${q(INSTALL_LOG)} 2>&1`,
            `echo "INSTALL_EXIT=$?"`,
            `tail -n 12 ${q(INSTALL_LOG)}`,
            // npm 11 默认拦截依赖的 install/postinstall 脚本（node-pty 等）。
            // node-pty 走预编译 pty.node 即可用，但 spawn-helper 的可执行位需要手动补。
            `if [ -f node_modules/@deepseek-ai/dsh-subprocess-local/scripts/ensure-spawn-helper.mjs ]; then node node_modules/@deepseek-ai/dsh-subprocess-local/scripts/ensure-spawn-helper.mjs 2>&1 | tail -3; fi`,
            `test -x ${q(DSH_BIN)} && echo "RUNTIME_OK" || echo "RUNTIME_MISSING"`,
            `test -x ${q(DSH_BIN)} && ${q(DSH_BIN)} --version 2>&1 | head -n 1 || true`,
        ].join("; ");
        const result = await run(command, params && params.timeoutMs ? params.timeoutMs : 900000);
        const ok = result.output.indexOf("RUNTIME_OK") >= 0;
        return {
            success: ok,
            message: ok ? `DSH runtime 就绪：${spec}` : `DSH runtime 安装未完成（exit=${result.exitCode}）`,
            data: { spec, runtimeDir: RUNTIME_DIR, exitCode: result.exitCode, timedOut: result.timedOut, output: result.output },
        };
    }

    async function dsh_service_start(params) {
        const port = parseInt(params && params.port, 10) || DEFAULT_PORT;
        // 幂等：先扫真实进程。已活着就直接复用并同步 pidfile，绝不重启——避免每次调用
        // 都 kill 再起（旧实现会打断正在跑的服务，且写新 PID 造成 pidfile 漂移）。
        const command = [
            `mkdir -p ${q(RUNTIME_DIR)} ${q(DSH_HOME_DIR)}`,
            `cd ${q(RUNTIME_DIR)}`,
            envPrelude(),
            `LIVE=$(${findPidCmd(port)})`,
            // 端口有响应（含 401）也视为在跑：即使 /proc 扫描漏判（cmdline 被截断等），
            // 也绝不对活着的服务执行 kill→重启。
            `PORTUP=0; curl -s -o /dev/null --max-time 3 http://127.0.0.1:${port}/ 2>/dev/null && PORTUP=1`,
            `if [ -n "$LIVE" ] || [ "$PORTUP" = "1" ]; then [ -n "$LIVE" ] && echo "$LIVE" > ${q(PID_FILE)}; echo "already-running port-up=$PORTUP pid=$LIVE"; ` +
                `echo "--- http ---"; curl -s -o /dev/null -w 'http=%{http_code}\\n' --max-time 5 http://127.0.0.1:${port}/ 2>&1 || true; ` +
                `echo "--- log tail ---"; tail -n 3 ${q(WEB_LOG)} 2>/dev/null || true; exit 0; fi`,
            `if [ -f ${q(PID_FILE)} ]; then kill "$(cat ${q(PID_FILE)})" >/dev/null 2>&1 || true; rm -f ${q(PID_FILE)}; fi`,
            // 兜底清掉按端口匹配的残留（含 pidfile 丢失/错位场景），并等待端口真正释放，
            // 否则紧接着的拉起会撞 EADDRINUSE（node 直接退出，表现为启动失败）。
            `for p in /proc/[0-9]*; do c=$(tr '\\0' ' ' < $p/cmdline 2>/dev/null); case "$c" in *dsh*web*--port\\ ${port}*) case "$c" in *sidebar_deepseek_harness*) ;; *) kill "$(basename $p)" >/dev/null 2>&1 || true;; esac;; esac; done`,
            `for i in 1 2 3 4 5 6 7 8 9 10; do curl -s -o /dev/null --max-time 1 http://127.0.0.1:${port}/ 2>/dev/null || break; sleep 1; done`,
            `setsid -f ${q(DSH_BIN)} web --host 127.0.0.1 --port ${port} --trusted-host 127.0.0.1:${port} --no-open > ${q(WEB_LOG)} 2>&1 < /dev/null`,
            `for i in 1 2 3 4 5 6 7 8 9 10 11 12; do curl -s -o /dev/null --max-time 2 http://127.0.0.1:${port}/ && break; sleep 2; done`,
            `REALPID=$(${findPidCmd(port)})`,
            `if [ -n "$REALPID" ]; then echo "$REALPID" > ${q(PID_FILE)}; echo "pid=$REALPID"; else echo "pid=not-found"; fi`,
            `echo "--- log tail ---"`,
            `tail -n 8 ${q(WEB_LOG)}`,
            `echo "--- http ---"`,
            `curl -s -o /dev/null -w 'http=%{http_code}\\n' --max-time 8 http://127.0.0.1:${port}/ 2>&1 || true`,
        ].join("; ");
        const result = await run(command, 90000);
        const ok = result.output.indexOf("http=303") >= 0 || result.output.indexOf("http=200") >= 0 || result.output.indexOf("http=401") >= 0;
        const reused = result.output.indexOf("already-running") >= 0;
        return {
            success: ok,
            message: ok
                ? (reused ? "DSH Web 服务已在运行（幂等复用，未重启）" : "DSH Web 服务已启动（401=需 token，303=带 token 可进）")
                : "DSH Web 服务启动流程已执行，但端口未响应（检查日志）",
            data: { port, reused, exitCode: result.exitCode, timedOut: result.timedOut, output: result.output, logPath: WEB_LOG, pidFile: PID_FILE },
        };
    }

    async function dsh_service_stop() {
        // 目标进程的 cmdline 里 runtime 目录可能是相对写法，不能拿它当特征；
        // 用「dsh + web」匹配，并排除用户既有的 sidebar_deepseek_harness。
        const match = `*dsh*web*`;
        const exclude = `*sidebar_deepseek_harness*`;
        const command = [
            `if [ -f ${q(PID_FILE)} ]; then PID="$(cat ${q(PID_FILE)})"; kill "$PID" >/dev/null 2>&1 || true; sleep 1; kill -9 "$PID" >/dev/null 2>&1 || true; rm -f ${q(PID_FILE)}; echo "stopped pid=$PID"; else echo "no-pid-file"; fi`,
            // 兜底：PID 文件丢失/错位时，按 cmdline 清掉本管理器拉起的 dsh web 残留进程
            `for p in /proc/[0-9]*; do c=$(tr '\\0' ' ' < $p/cmdline 2>/dev/null); case "$c" in ${match}) case "$c" in ${exclude}) ;; *) kill "$(basename $p)" >/dev/null 2>&1 || true;; esac;; esac; done; sleep 1`,
            `REMAIN=""; for p in /proc/[0-9]*; do c=$(tr '\\0' ' ' < $p/cmdline 2>/dev/null); case "$c" in ${match}) case "$c" in ${exclude}) ;; *) REMAIN="$REMAIN $(basename $p)";; esac;; esac; done; test -z "$REMAIN" && echo "no-residual-process" || echo "residual:$REMAIN"`,
        ].join("; ");
        const result = await run(command, 25000);
        return {
            success: result.output.indexOf("no-residual-process") >= 0,
            message: result.output.indexOf("no-residual-process") >= 0 ? "DSH Web 服务已停止" : "已发送停止信号，仍有进程残留（见输出）",
            data: { output: result.output },
        };
    }

    async function dsh_service_restart(params) {
        await dsh_service_stop();
        return await dsh_service_start(params);
    }

    // 部署幂等自启：写 dsh-autostart.sh 并挂到 shell 启动钩子（/etc/profile.d + ~/.bashrc）。
    // proot 无 systemd/cron，唯一可靠触发点是 shell 启动；脚本自身幂等（活着同步 pidfile，
    // 死了才拉起），因此任何新会话一执行终端命令即自愈，无需手动干预。
    async function dsh_service_autostart(params) {
        const port = parseInt(params && params.port, 10) || DEFAULT_PORT;
        const script = [
            `#!/bin/sh`,
            `# dsh 幂等自启（由 dsh_manager 部署）。设计约束：`,
            `#  1) 会被 /etc/profile.d/*.sh 与 ~/.bashrc 以 \`. script\` source，绝不能 exit——`,
            `#     exit 会杀掉 login shell，使 LOGIN_SUCCESSFUL/TERMINAL_READY 握手失败，`,
            `#     终端报 "Session initialization timeout"。故全部用 return||exit 收尾。`,
            `#  2) 本 rootfs 缺 awk/ss/nohup，只用 curl / kill / flock。`,
            `#  3) node 启动需数秒，探活太早会误判 -> flock 串行 + pidfile 存活 + 就绪等待，防重复拉起。`,
            `RT=${q(RUNTIME_DIR)}`,
            `PIDF="$RT/dsh-web.pid"`,
            `LOG="$RT/dsh-web.log"`,
            `LOCK="$RT/.dsh-autostart.lock"`,
            `PORT=${port}`,
            `BIN="$RT/node_modules/@deepseek-ai/dsh/lib/bin.js"`,
            `NODE=/usr/bin/node`,
            `[ -x "$NODE" ] || NODE=$(command -v node 2>/dev/null)`,
            `URL="http://127.0.0.1:$PORT/"`,
            `probe() { curl -s -o /dev/null --max-time 2 "$URL" 2>/dev/null; }`,
            `dshpid() { for p in /proc/[0-9]*; do c=$(tr '\\0' ' ' < $p/cmdline 2>/dev/null); ` +
                `case "$c" in *dsh*web*--port\\ \${PORT}*) case "$c" in *sidebar_deepseek_harness*) ;; *) basename $p; break;; esac;; esac; done; }`,
            // 子 shell 隔离：不污染 login shell 的变量与 fd。
            `(`,
            `    exec 9>"$LOCK" 2>/dev/null`,
            `    flock -n 9 2>/dev/null || exit 0`,
            `    probe && exit 0`,
            `    if [ -f "$PIDF" ]; then`,
            `        pp=$(cat "$PIDF" 2>/dev/null)`,
            `        if [ -n "$pp" ] && kill -0 "$pp" 2>/dev/null; then`,
            `            i=0; while [ "$i" -lt 10 ]; do probe && exit 0; sleep 1; i=$((i + 1)); done`,
            `            exit 0`,
            `        fi`,
            `    fi`,
            `    lp=$(dshpid); if [ -n "$lp" ]; then printf '%s\\n' "$lp" > "$PIDF"; exit 0; fi`,
            `    if [ -x "$NODE" ] && [ -f "$BIN" ]; then`,
            // 关键：setsid 不加 -f，让 $! 就是 node 的 PID（-f 会强制 fork，$! 变成短命包装进程，
            // 甚至在前台执行时为空）。仍以 dshpid 扫描结果为准回填，双保险防 pidfile 漂移/空值。
            `        setsid "$NODE" "$BIN" web --host 127.0.0.1 --port "$PORT" --trusted-host "127.0.0.1:$PORT" --no-open >> "$LOG" 2>&1 < /dev/null &`,
            `        np=$!`,
            `        i=0; while [ "$i" -lt 15 ]; do probe && break; sleep 1; i=$((i + 1)); done`,
            `        rp=$(dshpid); [ -n "$rp" ] && np="$rp"`,
            `        printf '%s\\n' "$np" > "$PIDF"`,
            `    fi`,
            `) 2>/dev/null`,
            `return 0 2>/dev/null || true`,
        ].join("\n");
        const command = [
            `mkdir -p ${q(RUNTIME_DIR)}`,
            // 用 q() 转义整段脚本（含换行，单引号字符串可跨行）直接落盘，
            // 不依赖 Buffer/base64（QuickJS runtime 不一定提供 Buffer）。
            `printf '%s' ${q(script)} > ${q(AUTOSTART_FILE)}`,
            `chmod +x ${q(AUTOSTART_FILE)}`,
            `ln -sf ${q(AUTOSTART_FILE)} ${q(AUTOSTART_HOOK)}`,
            `grep -q 'dsh-autostart' /root/.bashrc 2>/dev/null || printf '\\n# dsh autostart\\n[ -x ${AUTOSTART_FILE} ] && . ${AUTOSTART_FILE}\\n' >> /root/.bashrc`,
            `echo "--- script ---"; head -n 3 ${q(AUTOSTART_FILE)}`,
            `echo "--- hook ---"; ls -l ${q(AUTOSTART_HOOK)}`,
            `echo "--- verify ---"; ${q(AUTOSTART_FILE)} && echo "AUTOSTART_OK" || echo "AUTOSTART_FAIL"`,
            `test -f ${q(PID_FILE)} && echo "pid-file=$(cat ${q(PID_FILE)})" || echo "pid-file=absent"`,
        ].join("; ");
        const result = await run(command, 30000);
        const ok = result.output.indexOf("AUTOSTART_OK") >= 0;
        return {
            success: ok,
            message: ok
                ? "已部署 dsh 幂等自启（/etc/profile.d + ~/.bashrc 钩子，新会话自动补活）"
                : "自启部署流程已执行，但自检未通过（见输出）",
            data: {
                port,
                script: AUTOSTART_FILE,
                hook: AUTOSTART_HOOK,
                bashrc: "/root/.bashrc",
                exitCode: result.exitCode,
                output: result.output,
            },
        };
    }

    async function dsh_plugin_list(params) {
        const profile = norm(params && params.profile).trim() || DEFAULT_PROFILE;
        const result = await runDsh(`plugin --profile ${q(profile)} ls`, 120000);
        const profileInfo = await readProfile(profile);
        return {
            success: result.exitCode === 0,
            message: `已查询 profile「${profile}」的插件列表`,
            data: {
                profile,
                exitCode: result.exitCode,
                activeBundles: profileInfo.bundles,
                dependencies: profileInfo.dependencies,
                profileExists: profileInfo.exists,
                output: result.output,
            },
        };
    }
    async function dsh_plugin_add(params) {
        const spec = norm(params && params.spec).trim();
        if (!spec) throw new Error("spec 不能为空（npm 包名 / github:owner/repo / file:./path）");
        const profile = norm(params && params.profile).trim() || DEFAULT_PROFILE;
        const result = await runDsh(
            `plugin --profile ${q(profile)} add ${q(spec)}`,
            params && params.timeoutMs ? params.timeoutMs : 300000
        );
        const profileInfo = await readProfile(profile);
        const short = shortNameOf(spec);
        const inBundles = profileInfo.bundles.some((b) => String(b).indexOf(short) >= 0);
        const inDeps = profileInfo.dependencies.some((d) => d === short || String(d).indexOf(short) >= 0);
        const installed = inBundles || inDeps;
        let note = "";
        if (result.exitCode === 0) {
            if (inBundles) note = "已作为 profile 层激活";
            else if (inDeps) note = "已装入 dependencies，但插件未声明 dsh.bundle，未激活为 profile 层（需插件自带 dsh.bundle 才生效）";
            else note = "命令成功但未能从 profile 清单确认，请跑 dsh_plugin_doctor";
        }
        return {
            success: result.exitCode === 0,
            message: result.exitCode === 0
                ? `插件安装完成：${spec}（${note}，建议随后 dsh_service_restart）`
                : `插件安装失败（exit=${result.exitCode}）：${spec}`,
            data: {
                spec,
                profile,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                profileExists: profileInfo.exists,
                installedInDependencies: inDeps,
                activatedAsBundle: inBundles,
                installed,
                activeBundles: profileInfo.bundles,
                dependencies: profileInfo.dependencies,
                output: result.output,
            },
        };
    }

    async function dsh_plugin_remove(params) {
        const name = norm(params && params.name).trim();
        if (!name) throw new Error("name 不能为空");
        const profile = norm(params && params.profile).trim() || DEFAULT_PROFILE;
        const result = await runDsh(`plugin --profile ${q(profile)} remove ${q(name)}`, 180000);
        const profileInfo = await readProfile(profile);
        const short = shortNameOf(name);
        const stillThere = profileInfo.dependencies.some((d) => d === short || String(d).indexOf(short) >= 0)
            || profileInfo.bundles.some((b) => String(b).indexOf(short) >= 0);
        return {
            success: result.exitCode === 0 && !stillThere,
            message: result.exitCode !== 0
                ? `卸载失败（exit=${result.exitCode}）：${name}`
                : (stillThere ? `命令已执行，但「${name}」仍留在 profile 清单中` : `插件已卸载：${name}`),
            data: {
                name,
                profile,
                exitCode: result.exitCode,
                stillPresent: stillThere,
                activeBundles: profileInfo.bundles,
                dependencies: profileInfo.dependencies,
                output: result.output,
            },
        };
    }

    async function dsh_plugin_doctor(params) {
        const profile = norm(params && params.profile).trim() || DEFAULT_PROFILE;
        const name = norm(params && params.name).trim();
        const profileInfo = await readProfile(profile);
        const list = await runDsh(`plugin --profile ${q(profile)} ls`, 120000);
        const layout = await run(
            `echo "=== profiles ==="; ls ${q(DSH_HOME_DIR)}/profiles 2>&1; ` +
            `echo "=== profile dir ==="; ls ${q(DSH_HOME_DIR)}/profiles/${q(profile)} 2>&1; ` +
            `echo "=== node_modules top ==="; ls ${q(DSH_HOME_DIR)}/profiles/${q(profile)}/node_modules 2>&1 | head -30`,
            30000
        );
        // 安装判定：bundles（已激活层）或 dependencies（已装入未激活）任一命中即算「已安装」
        const short = name ? shortNameOf(name) : "";
        const inBundles = short ? profileInfo.bundles.some((b) => String(b).indexOf(short) >= 0) : null;
        const inDeps = short ? profileInfo.dependencies.some((d) => String(d).indexOf(short) >= 0) : null;
        const installed = short ? (inBundles || inDeps) : null;
        let verdict;
        if (!name) {
            verdict = `profile「${profile}」激活层 ${profileInfo.bundles.length} 个，依赖 ${profileInfo.dependencies.length} 个`;
        } else if (inBundles) {
            verdict = `插件「${name}」已作为 profile 层激活于「${profile}」`;
        } else if (inDeps) {
            verdict = `插件「${name}」已装入「${profile}」的 dependencies，但未声明 dsh.bundle，未激活为层`;
        } else {
            verdict = `插件「${name}」未在 profile「${profile}」的清单中找到`;
        }
        return {
            success: true,
            message: verdict,
            data: {
                profile,
                query: name || null,
                installed,
                activatedAsBundle: inBundles,
                installedInDependencies: inDeps,
                activeBundles: profileInfo.bundles,
                dependencies: profileInfo.dependencies,
                profileExists: profileInfo.exists,
                profilePackageJson: profileInfo.raw ? profileInfo.raw.slice(0, 2000) : "",
                listOutput: list.output,
                layout: layout.output,
            },
        };
    }

    async function dsh_plugin_find(params) {
        const keyword = norm(params && params.keyword).trim() || "dsh-plugin";
        const limit = Math.min(Math.max(parseInt(params && params.limit, 10) || 15, 1), 50);
        const url = `${REGISTRY}-/v1/search?text=${encodeURIComponent(keyword)}&size=${limit}`;
        // registry 偶发 connection reset，做两次退避重试，避免一次抖动就判失败
        let lastError = null;
        let parsed = null;
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                const client = OkHttp.newClient();
                const request = client.newRequest()
                    .url(url)
                    .method("GET")
                    .headers({ "User-Agent": "Operit-DSH-Plugin-Manager/1.0" });
                const response = await request.build().execute();
                if (!response.isSuccessful()) throw new Error(`registry HTTP ${response.statusCode}`);
                parsed = JSON.parse(response.content);
                lastError = null;
                break;
            } catch (error) {
                lastError = error;
                if (attempt < 2) {
                    await Tools.System.sleep(1000 * (attempt + 1));
                }
            }
        }
        if (lastError) throw lastError;
        const objects = Array.isArray(parsed && parsed.objects) ? parsed.objects : [];
        const rows = objects.map((item) => {
            const pkg = (item && item.package) || {};
            const links = pkg.links || {};
            return {
                name: pkg.name || "",
                version: pkg.version || "",
                description: pkg.description || "",
                updated: pkg.date || "",
                repository: links.repository || links.npm || "",
            };
        }).filter((row) => row.name);
        const formatted = rows.map((row, index) => `${index + 1}. ${row.name}@${row.version}\n   ${row.description}\n   ${row.repository}`).join("\n\n");
        return {
            success: true,
            message: `找到 ${rows.length} 个与「${keyword}」相关的候选`,
            data: { keyword, count: rows.length, results: rows, formatted: formatted || "无结果" },
        };
    }

    async function wrap(func, params, failMessage) {
        try {
            const result = await func(params || {});
            complete(result);
        } catch (error) {
            const detail = error && error.message ? error.message : String(error);
            console.error(`[dsh_plugin_manager] ${failMessage}: ${detail}`);
            complete({ success: false, message: `${failMessage}: ${detail}` });
        }
    }

    return {
        dsh_runtime_status: (params) => wrap(dsh_runtime_status, params, "状态查询失败"),
        dsh_ensure_runtime: (params) => wrap(dsh_ensure_runtime, params, "runtime 安装失败"),
        dsh_service_start: (params) => wrap(dsh_service_start, params, "服务启动失败"),
        dsh_service_stop: (params) => wrap(dsh_service_stop, params, "服务停止失败"),
        dsh_service_restart: (params) => wrap(dsh_service_restart, params, "服务重启失败"),
        dsh_service_autostart: (params) => wrap(dsh_service_autostart, params, "自启部署失败"),
        dsh_plugin_list: (params) => wrap(dsh_plugin_list, params, "插件列表查询失败"),
        dsh_plugin_add: (params) => wrap(dsh_plugin_add, params, "插件安装失败"),
        dsh_plugin_remove: (params) => wrap(dsh_plugin_remove, params, "插件卸载失败"),
        dsh_plugin_doctor: (params) => wrap(dsh_plugin_doctor, params, "插件自检失败"),
        dsh_plugin_find: (params) => wrap(dsh_plugin_find, params, "插件搜索失败"),
    };
})();

exports.dsh_runtime_status = DshPluginManager.dsh_runtime_status;
exports.dsh_ensure_runtime = DshPluginManager.dsh_ensure_runtime;
exports.dsh_service_start = DshPluginManager.dsh_service_start;
exports.dsh_service_stop = DshPluginManager.dsh_service_stop;
exports.dsh_service_restart = DshPluginManager.dsh_service_restart;
exports.dsh_service_autostart = DshPluginManager.dsh_service_autostart;
exports.dsh_plugin_list = DshPluginManager.dsh_plugin_list;
exports.dsh_plugin_add = DshPluginManager.dsh_plugin_add;
exports.dsh_plugin_remove = DshPluginManager.dsh_plugin_remove;
exports.dsh_plugin_doctor = DshPluginManager.dsh_plugin_doctor;
exports.dsh_plugin_find = DshPluginManager.dsh_plugin_find;