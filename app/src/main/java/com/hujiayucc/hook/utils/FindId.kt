package com.hujiayucc.hook.utils

/**
 * 枚举软件开屏跳过按钮ID
 *
 * @param packageName 软件包名
 * @param id 跳过控件ID
 * @param wait 等待延迟点击时长
 */
enum class FindId(val packageName: String, val id: Array<String>, val wait: Long) {
    TencentKG("com.tencent.karaoke", arrayOf("com.tencent.karaoke:id/m4i"),0),
    MiMarket("com.xiaomi.market", arrayOf("com.miui.systemAdSolution:id/view_skip_button"),0),
    MiShop("com.xiaomi.shop", arrayOf("com.xiaomi.shop:id/skip"),0),
    CoolApk("com.coolapk.market", arrayOf(
        "com.miui.systemAdSolution:id/view_skip_button",
        "android:id/button3",
        "com.coolapk.market:id/close_view"
    ),0),
    HuYa("com.duowan.kiwi", arrayOf("com.duowan.kiwi:id/skip_time"),0),
    MiYoHyper("com.mihoyo.hyperion", arrayOf("com.mihoyo.hyperion:id/mSplashBtJump"),0),
    APPShare("info.muge.appshare", arrayOf("info.muge.appshare:id/tv_jump"),0),
    IQiYi("com.qiyi.video", arrayOf("com.qiyi.video:id/id377a"),0),
    ZuiYou("cn.xiaochuankeji.tieba",
        arrayOf("com.byted.pangle:id/tt_interact_splash_click_bar_text"),0),
    ZhiHu("com.zhihu.android", arrayOf("com.zhihu.android:id/btn_skip"),0),
    XYST("com.fenbi.android.solar", arrayOf("com.fenbi.android.solar:id/btn_skip"),0),
    Dragon("com.dragon.read", arrayOf("com.dragon.read:id/dqt"),0),
    DragonFM("com.xs.fm", arrayOf("com.xs.fm:id/ck9"),0);

    companion object {
        /** 通过包名获取对应id */
        fun fromPackageName(packageName: String): HashMap<String, Any>? {
            return values().find { it.packageName == packageName }?.let {
                val findId: HashMap<String, Any> = HashMap()
                findId["id"] = it.id
                findId["wait"] = it.wait
                return findId
            }
        }
    }
}