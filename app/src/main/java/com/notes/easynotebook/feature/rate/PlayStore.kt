package com.notes.easynotebook.feature.rate

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

object PlayStore {

    fun openAppListing(context: Context): Boolean {
        val appId = context.packageName
        val marketUri = "market://details?id=$appId".toUri()
        val webUri = "https://play.google.com/store/apps/details?id=$appId".toUri()

        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, marketUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}