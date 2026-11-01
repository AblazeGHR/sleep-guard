package com.areyouasleep.util

import android.content.Context
import android.content.Intent

/** 第二天想接着听，一键跳回那个 App */
object AppLauncher {

    fun open(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
