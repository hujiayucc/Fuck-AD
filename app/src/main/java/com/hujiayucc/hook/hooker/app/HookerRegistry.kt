package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.hooker.util.Hooker

object HookerRegistry {
    private val appHookers = mapOf<String, List<Hooker>>(
        "info.muge.appshare" to listOf(AppShare),
        "com.baidu.netdisk" to listOf(BaiduPan),
        "com.banshenghuo.mobile" to listOf(BanShengHuo),
        "com.zuoyou.center" to listOf(BeiTongYouXiTing),
        "tv.danmaku.bili" to listOf(Bilibili),
        "com.yaxisvip.pubgtool.iueg" to listOf(CYC),
        "com.nowcasting.activity" to listOf(CaiYunTianQi),
        "com.coolapk.market" to listOf(CoolMarket),
        "com.douban.frodo" to listOf(DouBan),
        "com.ss.android.ugc.aweme" to listOf(DouYin),
        "com.dragon.read" to listOf(FQXS),
        "com.diershoubing.erbing" to listOf(ErBing),
        "com.yaerxing.fkst" to listOf(FengKuangShuaTi),
        "com.ganji.android.haoche_c" to listOf(GuaZiErShouChe),
        "com.phoenix.read" to listOf(HGMFDJ),
        "com.duowan.kiwi" to listOf(HuYa),
        "com.qiyi.video" to listOf(IQiYi),
        "com.jumang.jiongciyuan" to listOf(JiongCiYuan),
        "cn.kaiheila" to listOf(Kook),
        "com.huolala.mockgps" to listOf(MockGps),
        "com.kmxs.reader" to listOf(QiCat),
        "com.excelliance.dualaid" to listOf(ShuangKaiZhuShou),
        "cn.soulapp.android" to listOf(Soul),
        "com.baidu.tieba" to listOf(TieBa),
        "com.xiaobai.screen.record" to listOf(XiaoBaiRecord),
        "com.youdao.dict" to listOf(YouDaoDict)
    )

    fun create(packageName: String): List<Hooker> {
        return appHookers[packageName].orEmpty()
    }
}
