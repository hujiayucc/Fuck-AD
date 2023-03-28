package com.hujiayucc.hook.utils

/**
 * 枚举软件开屏跳过按钮ID
 *
 * @param packageName 软件包名
 * @param id 跳过控件ID
 * @param wait 等待延迟点击时长
 */
enum class FindId(val packageName: String, val id: String, val wait: Long) {
    TencentKG("com.tencent.karaoke","com.tencent.karaoke:id/m4i",0),
    MiMarket("com.xiaomi.market","com.miui.systemAdSolution:id/view_skip_button",0),
    MiShop("com.xiaomi.shop","com.xiaomi.shop:id/skip",0),
    CoolApk("com.coolapk.market","com.miui.systemAdSolution:id/view_skip_button",0),
    HuYa("com.duowan.kiwi","com.duowan.kiwi:id/skip_time",0),
    MiYoHyper("com.mihoyo.hyperion","com.mihoyo.hyperion:id/mSplashBtJump",0),
    APPShare("info.muge.appshare","info.muge.appshare:id/tv_jump",0),
    IQiYi("com.qiyi.video","com.qiyi.video:id/id377a",0),
    ZuiYou("cn.xiaochuankeji.tieba","com.byted.pangle:id/tt_interact_splash_click_bar_text",0),
    ZhiHu("com.zhihu.android","com.zhihu.android:id/btn_skip",0),
    XYST("com.fenbi.android.solar","com.fenbi.android.solar:id/btn_skip",0),
    BiLiBiLi("tv.danmaku.bili","tv.danmaku.bili:id/count_down",0);

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