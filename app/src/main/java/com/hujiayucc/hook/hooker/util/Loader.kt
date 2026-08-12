package com.hujiayucc.hook.hooker.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.hujiayucc.hook.ModuleMain
import com.hujiayucc.hook.author.Author
import com.hujiayucc.hook.author.JwtUtils.isLogin
import com.hujiayucc.hook.author.JwtUtils.readExpiresAtEpochSeconds
import com.hujiayucc.hook.author.JwtUtils.trustedNowEpochSeconds
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
        val nowEpochSeconds = prefs.trustedNowEpochSeconds() ?: Long.MAX_VALUE
        authSnapshot?.let { cached ->
            if (cached.sameAuthData(snapshot)) {
                if (AuthCachePolicy.canReuse(true, cached.authorized, cached.expiresAtEpochSeconds, nowEpochSeconds)) {
                    return false
                }
                if (!cached.authorized) return true
            }
        }
        return synchronized(this) {
            val synchronizedNow = prefs.trustedNowEpochSeconds() ?: Long.MAX_VALUE
            authSnapshot?.let { cached ->
                if (cached.sameAuthData(snapshot)) {
                    if (AuthCachePolicy.canReuse(true, cached.authorized, cached.expiresAtEpochSeconds, synchronizedNow)) {
                        return@synchronized false
                    }
                    if (!cached.authorized) return@synchronized true
                }
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
        val entitlement: String,
        val expiresAtEpochSeconds: Long,
        val authorized: Boolean
    ) {
        fun sameAuthData(other: AuthSnapshot): Boolean {
            return email == other.email &&
                token == other.token &&
                pubKey == other.pubKey &&
                deviceName == other.deviceName &&
                deviceId == other.deviceId &&
                entitlement == other.entitlement &&
                expiresAtEpochSeconds == other.expiresAtEpochSeconds
        }

        companion object {
            fun from(prefs: SharedPreferences): AuthSnapshot {
                return AuthSnapshot(
                    email = prefs.getString("email", "").orEmpty(),
                    token = prefs.getString("token", "").orEmpty(),
                    pubKey = prefs.getString("pubKey", "").orEmpty(),
                    deviceName = prefs.getString("name", "").orEmpty(),
                    deviceId = prefs.getString("id", "").orEmpty(),
                    entitlement = prefs.getString("entitlement", "").orEmpty(),
                    expiresAtEpochSeconds = readExpiresAtEpochSeconds(prefs.getString("token", "").orEmpty()),
                    authorized = false
                )
            }
        }
    }
}