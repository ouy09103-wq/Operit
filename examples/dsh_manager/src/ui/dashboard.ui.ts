/**
 * DSH 插件管理器 —— Compose DSL 控制面板
 *
 * 运行在 `ui` 上下文。所有动作通过 ctx.callTool('dsh_plugin_manager:<tool>', params)
 * 转发到 subpackage；subpackage 才是真正落地终端命令的地方。
 */
const SUBPACKAGE_ID = "dsh_manager";

interface Cell<T> { value: T; set: (v: T) => void; }

function cell<T>(ctx: any, key: string, initial: T): Cell<T> {
  const pair = ctx.useState(key, initial) as [T, (v: T) => void];
  return { value: pair[0], set: pair[1] };
}

const TEXT = {
  zh: {
    title: "DSH 插件管理器",
    status: "1. 运行时状态",
    runtime: "2. 安装 / 更新 runtime",
    service: "3. Web 服务",
    plugin: "4. 插件管理",
    refreshStatus: "刷新状态",
    ensureRuntime: "安装 / 更新",
    version: "版本（latest 或 0.1.7-rc.2）",
    port: "端口",
    start: "启动",
    stop: "停止",
    restart: "重启",
    profile: "profile",
    spec: "插件来源（npm 名 / github:owner/repo / file:./path）",
    keyword: "搜索关键词",
    list: "列表",
    find: "搜索",
    add: "安装",
    remove: "卸载",
    doctor: "自检",
    log: "输出",
    clear: "清空输出",
    empty: "尚无输出。",
    running: "执行中…",
  },
  en: {
    title: "DSH Plugin Manager",
    status: "1. Runtime status",
    runtime: "2. Install / update runtime",
    service: "3. Web service",
    plugin: "4. Plugin management",
    refreshStatus: "Refresh status",
    ensureRuntime: "Install / update",
    version: "Version (latest or 0.1.7-rc.2)",
    port: "Port",
    start: "Start",
    stop: "Stop",
    restart: "Restart",
    profile: "Profile",
    spec: "Plugin source (npm name / github:owner/repo / file:./path)",
    keyword: "Search keyword",
    list: "List",
    find: "Search",
    add: "Install",
    remove: "Remove",
    doctor: "Doctor",
    log: "Output",
    clear: "Clear output",
    empty: "No output yet.",
    running: "Running…",
  },
};

function resolveText() {
  const locale = String((typeof getLang === "function" ? getLang() : "") || "").toLowerCase();
  return locale.startsWith("zh") ? TEXT.zh : TEXT.en;
}

function toText(result: any): string {
  if (result === null || result === undefined) return "(empty)";
  if (typeof result === "string") return result;
  try {
    return JSON.stringify(result, null, 2);
  } catch (e) {
    return String(result);
  }
}

export default function Screen(ctx: any) {
  const t = resolveText();

  const spec = cell<string>(ctx, "dsh_spec", "");
  const profile = cell<string>(ctx, "dsh_profile", "web");
  const port = cell<string>(ctx, "dsh_port", "3081");
  const version = cell<string>(ctx, "dsh_version", "latest");
  const keyword = cell<string>(ctx, "dsh_keyword", "dsh-plugin");
  const busy = cell<boolean>(ctx, "dsh_busy", false);
  const log = cell<string>(ctx, "dsh_log", "");

  async function run(tool: string, params: Record<string, unknown>, label: string) {
    if (busy.value) return;
    busy.set(true);
    log.set(`▶ ${label} …`);
    try {
      const result = await ctx.callTool(`${SUBPACKAGE_ID}:${tool}`, params);
      const payload = (result && (result as any).message) ? (result as any).message : toText(result);
      log.set(`▶ ${label}\n${payload}\n\n${toText(result)}`);
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      log.set(`▶ ${label}\n✗ ${detail}`);
    } finally {
      busy.set(false);
    }
  }

  const fieldProps = (label: string, c: Cell<string>, extra: Record<string, unknown> = {}) => ({
    label,
    value: c.value,
    onValueChange: (v: string) => c.set(v),
    singleLine: true,
    modifier: ctx.Modifier.fillMaxWidth().toJSON(),
    ...extra,
  });

  const actionRow = (children: any[]) =>
    ctx.UI.Row({ spacing: 8, fillMaxWidth: true }, children);

  const btn = (label: string, on: () => void) =>
    ctx.UI.Button({ text: label, onClick: on, enabled: !busy.value });

  const header = (text: string) =>
    ctx.UI.Text({ text, style: "titleMedium", color: "primary", fontWeight: "medium" });

  const statusCard = ctx.UI.Card({ fillMaxWidth: true, content: [] });
  void statusCard;

  const children: any[] = [
    header(t.status),
    ctx.UI.Row({ spacing: 8, fillMaxWidth: true }, [
      btn(t.refreshStatus, () => run("dsh_runtime_status", { port }, t.refreshStatus)),
    ]),

    header(t.runtime),
    ctx.UI.TextField(fieldProps(t.version, version)),
    actionRow([btn(t.ensureRuntime, () => run("dsh_ensure_runtime", { version: version.value }, t.ensureRuntime))]),

    header(t.service),
    ctx.UI.TextField(fieldProps(t.port, port)),
    actionRow([
      btn(t.start, () => run("dsh_service_start", { port: port.value }, t.start)),
      btn(t.stop, () => run("dsh_service_stop", {}, t.stop)),
      btn(t.restart, () => run("dsh_service_restart", { port: port.value }, t.restart)),
    ]),

    header(t.plugin),
    ctx.UI.TextField(fieldProps(t.profile, profile)),
    ctx.UI.TextField(fieldProps(t.spec, spec)),
    actionRow([
      btn(t.list, () => run("dsh_plugin_list", { profile: profile.value }, t.list)),
      btn(t.remove, () => run("dsh_plugin_remove", { name: spec.value, profile: profile.value }, t.remove)),
      btn(t.doctor, () => run("dsh_plugin_doctor", { name: spec.value, profile: profile.value }, t.doctor)),
    ]),
    actionRow([btn(t.add, () => run("dsh_plugin_add", { spec: spec.value, profile: profile.value }, t.add))]),

    ctx.UI.TextField(fieldProps(t.keyword, keyword)),
    actionRow([
      btn(t.find, () => run("dsh_plugin_find", { keyword: keyword.value }, t.find)),
      btn(t.clear, () => log.set("")),
    ]),

    header(t.log),
    busy.value
      ? ctx.UI.Row({ verticalAlignment: "center" }, [
          ctx.UI.CircularProgressIndicator({ width: 20, height: 20, strokeWidth: 2 }),
          ctx.UI.Spacer({ width: 10 }),
          ctx.UI.Text({ text: t.running, style: "bodyMedium", color: "onSurfaceVariant" }),
        ])
      : null,
    log.value
      ? ctx.UI.Text({ text: log.value, style: "bodySmall", fontFamily: "monospace" })
      : ctx.UI.Text({ text: t.empty, style: "bodyMedium", color: "onSurfaceVariant" }),
  ];

  return ctx.UI.LazyColumn(
    {
      spacing: 12,
      padding: 16,
      fillMaxSize: true,
      topBarTitle: t.title,
    },
    children.filter(Boolean)
  );
}