package com.hujiayucc.hook.hooker.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.hujiayucc.hook.ModuleMain
import com.hujiayucc.hook.author.Author
import com.hujiayucc.hook.author.JwtUtils.isLogin
import io.github.libxposed.api.XposedModuleInterface

object Loader: Hooker() {
    @Volatile
    private var authSnapshot: AuthSnapshot? = null

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        Activity::class.java.method("onCreate")
            .hook {
                before {
                    val activity = instance<Activity>()
                    if (shouldCheckAuth(activity, ModuleMain.prefs)) {
                        Author(activity, true, ModuleMain.prefs)
                    }
                }
            }
    }

    private fun shouldCheckAuth(context: Context, prefs: SharedPreferences): Boolean {
        val snapshot = AuthSnapshot.from(prefs)
        authSnapshot?.let { cached ->
            if (cached.sameAuthData(snapshot)) return !cached.authorized
        }
        return synchronized(this) {
            authSnapshot?.let { cached ->
                if (cached.sameAuthData(snapshot)) return@synchronized !cached.authorized
            }
            Author(context, false, prefs)
            val refreshed = AuthSnapshot.from(prefs).copy(authorized = prefs.isLogin())
            authSnapshot = refreshed
            !refreshed.authorized
        }
    }

    private data class AuthSnapshot(
        val email: String,
        val token: String,
        val pubKey: String,
        val deviceName: String,
        val deviceId: String,
        val authorized: Boolean
    ) {
        fun sameAuthData(other: AuthSnapshot): Boolean {
            return email == other.email &&
                token == other.token &&
                pubKey == other.pubKey &&
                deviceName == other.deviceName &&
                deviceId == other.deviceId
        }

        companion object {
            fun from(prefs: SharedPreferences): AuthSnapshot {
                return AuthSnapshot(
                    email = prefs.getString("email", "").orEmpty(),
                    token = prefs.getString("token", "").orEmpty(),
                    pubKey = prefs.getString("pubKey", "").orEmpty(),
                    deviceName = prefs.getString("name", "").orEmpty(),
                    deviceId = prefs.getString("id", "").orEmpty(),
                    authorized = false
                )
            }
        }
    }
}