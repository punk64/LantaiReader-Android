package com.lantai.reader.common

object StrEn {
    val map: MutableMap<String, String> = HashMap()

    val templates: MutableMap<String, String> = HashMap()

    fun put(zh: String, en: String) {
        map[zh] = en
    }

    fun tpl(zh: String, en: String) {
        templates[zh] = en
    }

    private fun load() {
        put("刚刚", "just now")
        tpl("{n} 分钟前", "{n} min ago")
        tpl("{n} 小时前", "{n} h ago")
        put("昨天", "yesterday")
        tpl("{n} 天前", "{n} d ago")
        put("已读完", "Finished")
        put("未开始", "Not started")
        tpl("读到 {n}%", "{n}% read")
        tpl("{n} 分钟", "{n} min")
        tpl("{n} 小时", "{n} h")
        tpl("{n} 小时 {n} 分", "{n} h {n} min")
        tpl("{n} 分", "{n} min")
        tpl("{n} 时", "{n} h")

        put("未连接", "Not connected")
        put("局域网", "LAN")
        put("公网", "Internet")
        put("在线", "Online")
        put("离线", "Offline")
        put("离线模式", "Offline mode")
        put("本机读者", "Local reader")
        put("连接中…", "Connecting…")
        put("登录", "Sign in")
        put("服务器地址", "Server address")
        put("账号", "Account")
        put("密码", "Password")
        put("登录失败", "Sign-in failed")
        put("网络不可用", "Network unavailable")
        put("离线使用", "Use offline")
        put("仅看已下载内容", "Downloaded items only")

        put("首页", "Home")
        put("书库", "Library")
        put("统计", "Stats")
        put("我的", "Me")
        put("全部", "All")
        put("本地", "Local")
        put("云库", "Cloud")
        put("来源", "Source")
        put("显示", "View")
        put("按系列", "By series")
        put("全部书籍", "All books")
        put("格式", "Format")
        put("阅读", "Read")
        put("排序", "Sort")
        put("筛选", "Filter")
        put("更多", "More")
        put("收起", "Less")
        put("重置", "Reset")
        put("重置筛选", "Reset filters")
        put("正在加载…", "Loading…")
        put("加载失败", "Load failed")
        put("重试", "Retry")
        put("暂无内容", "Nothing here")
        put("没有符合条件的书", "No matching books")
        put("放宽筛选，或导入本地文件", "Loosen filters, or import local files")
        put("还没有下载到本机的云库书", "No cloud books downloaded yet")

        put("切换在线", "Go online")
        put("切换离线", "Go offline")
        put("返回登录页", "Back to sign-in")
        put("退出登录", "Sign out")
        put("离线不可用 · 需连接服务器", "Unavailable offline · needs a server")
        put("离线模式 · 仅本机内容", "Offline · local content only")
        put("在线模式", "Online mode")
        put("会话已失效，请从登录页重新登录", "Session expired; sign in again from the sign-in page")
        put("离线模式不提供在线内容", "Online content is unavailable in offline mode")
        put("离线模式不提供统计", "Stats are unavailable in offline mode")
    }

    init {
        load()
    }
}
