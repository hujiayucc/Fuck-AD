package com.hujiayucc.hook.hook.entity

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.hujiayucc.hook.hook.app.CoolApk
import com.hujiayucc.hook.hook.app.DSXYS
import com.hujiayucc.hook.hook.app.HuYa

enum class HookerList(val packageName: String, val hooker: YukiBaseHooker, val stop: Boolean) {
    /** 堆糖 */
    DuiTang("com.duitang.main", com.hujiayucc.hook.hook.app.DuiTang,true),
    /** 最右 */
    ZuiYou("cn.xiaochuankeji.tieba", com.hujiayucc.hook.hook.app.ZuiYou,false),
    /** 番茄小说 */
    DragonRead("com.dragon.read", com.hujiayucc.hook.hook.app.DragonRead,false),
    /** 喜马拉雅 */
    XiMaLaYa("com.ximalaya.ting.android", com.hujiayucc.hook.hook.app.XiMaLaYa,false),
    /** App分享 */
    AppShare("info.muge.appshare", com.hujiayucc.hook.hook.app.AppShare,false),
    /** 腾讯动漫 */
    QQAc("com.qq.ac.android", com.hujiayucc.hook.hook.app.QQAc,true),
    /** 小猿搜题 */
    XYST("com.fenbi.android.solar", com.hujiayucc.hook.hook.app.XYST,true),
    /** QQ阅读 */
    QQReader("com.qq.reader", com.hujiayucc.hook.hook.app.QQReader,true),
    /** 追书小说阅读 */
    ZSRead("com.zhuishuxiaoshuo.bbzz.app", com.hujiayucc.hook.hook.app.ZSRead,true),
    /** 米游社 */
    MiYoHyper("com.mihoyo.hyperion", com.hujiayucc.hook.hook.app.MiYoHyper,true),
    /** 古诗文网 */
    GSWW("org.gushiwen.gushiwen", com.hujiayucc.hook.hook.app.GSWW,false),
    /** 大师兄影视 */
    DSXYS("com.dogal.kenhuangzhe.geh.dashixiong", com.hujiayucc.hook.hook.app.DSXYS,true),
    /** 酷安 */
    COOLAPK("com.coolapk.market", CoolApk, false),
    /** 虎牙直播 */
    HUYA("com.duowan.kiwi", HuYa, false),;

    companion object {
        /** 通过包名获取对应Hooker */
        fun fromPackageName(packageName: String): HashMap<String, Any>? {
            return entries.find { it.packageName == packageName }?.let {
                val hooker = HashMap<String, Any>()
                hooker["hooker"] = it.hooker
                hooker["stop"] = it.stop
                return hooker
            }
        }
    }
}