package com.hujiayucc.hook.hook.entity

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType
import com.hujiayucc.hook.utils.Log

/** 禁言广告SDK Service */
object Service : YukiBaseHooker() {
    private val list = arrayListOf(
        "com.qq.e.comm.DownloadService",
        "com.kwad.sdk.api.proxy.VideoWallpaperService",
        "com.kwad.sdk.api.proxy.app.DownloadService",
        "com.kwad.sdk.api.proxy.app.FileDownloadService\$SeparateProcessService",
        "com.kwad.sdk.api.proxy.app.FileDownloadService\$SharedMainProcessService",
        "com.kwad.sdk.api.proxy.app.ServiceProxyRemote",
        "com.ss.android.socialbase.appdownloader.DownloadHandlerService",
        "com.ss.android.socialbase.appdownloader.RetryJobSchedulerService",
        "com.ss.android.socialbase.downloader.downloader.DownloadService",
        "com.ss.android.socialbase.downloader.downloader.IndependentProcessDownloadService",
        "com.ss.android.socialbase.downloader.downloader.SqlDownloadCacheService",
        "com.ss.android.socialbase.downloader.impls.DownloadHandleService",
        "com.ss.android.socialbase.downloader.notification.DownloadNotificationService"
    )

    override fun onHook() {
        for (clazz in list) {
            findClass(clazz).hook {
                injectMember {
                    allMembers(type = MembersType.ALL)
                    replaceTo(null)
                    Log.d("Hook Service: $clazz")
                }
            }.ignoredHookClassNotFoundFailure()
        }
    }
}