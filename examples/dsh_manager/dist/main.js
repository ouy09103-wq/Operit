"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
/**
 * DSH 插件管理器（Operit 分支版）
 *
 * 这是 ToolPkg 包级入口，运行在 `main` 上下文。
 * 只做注册声明；真正的工具逻辑在 packages/dsh_plugin_manager.js（subpackage），
 * 界面在 ui/dashboard.ui.ts（Compose DSL）。
 *
 * 关键点：
 * - 不依赖 Operit 市场的 com.operit.deepseek_harness 侧边栏包；
 * - runtime 自建于 /root/operit-dsh-runtime，与用户既有 sidebar 完全隔离。
 */
const dashboard_ui_js_1 = __importDefault(require("./ui/dashboard.ui.js"));
const TOOLPKG_ID = "com.operit.dsh_plugin_manager";
const UI_MODULE_ID = "dsh_setup";
const ROUTE_ID = "dsh_dashboard";
const ROUTE = `toolpkg:${TOOLPKG_ID}:ui:${ROUTE_ID}`;
const TITLE = { zh: "DSH 插件管理器", en: "DSH Plugin Manager" };
function registerToolPkg() {
    ToolPkg.registerToolboxUiModule({
        id: UI_MODULE_ID,
        runtime: "compose_dsl",
        screen: dashboard_ui_js_1.default,
        params: {},
        title: TITLE,
    });
    ToolPkg.registerUiRoute({
        id: ROUTE_ID,
        route: ROUTE,
        runtime: "compose_dsl",
        screen: dashboard_ui_js_1.default,
        title: TITLE,
    });
    ToolPkg.registerNavigationEntry({
        id: "dsh_dashboard_toolbox",
        route: ROUTE,
        surface: "toolbox",
        title: TITLE,
        icon: "extension",
        order: 60,
    });
    return true;
}
