package com.notes.easynotebook.ui

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.notes.easynotebook.R
import com.notes.easynotebook.core.storage.RatePrefs
import com.notes.easynotebook.databinding.DialogRateAppBinding
import com.notes.easynotebook.feature.rate.PlayStore
import com.notes.easynotebook.feature.rate.RateGate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RatePrompter(
    private val prefs: RatePrefs,
    private val gate: RateGate = RateGate()
) {
    private var dialog: AlertDialog? = null
    private var isCheckingOrShowing = false

    /**
     * notesCount is required to avoid prompting brand new users.
     */
    fun maybeShowRateDialog(
        activity: Activity,
        lifecycleOwner: LifecycleOwner,
        notesCount: Int
    ) {
        if (isCheckingOrShowing || activity.isFinishing || activity.isDestroyed) return
        isCheckingOrShowing = true
        lifecycleOwner.lifecycleScope.launch {
            val now = System.currentTimeMillis()

            // NEW: track sessions + first open timestamp
            prefs.onSessionStart(now)

            val state = prefs.state.first()
            if (!gate.shouldShow(state, now, notesCount) ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                activity.isFinishing || activity.isDestroyed
            ) {
                isCheckingOrShowing = false
                return@launch
            }

            // Mark shown immediately so it won't re-open if lifecycle triggers again
            prefs.setLastShown(now)

            showDialog(activity, lifecycleOwner)
        }
    }

    private fun showDialog(activity: Activity, lifecycleOwner: LifecycleOwner) {
        val binding = DialogRateAppBinding.inflate(LayoutInflater.from(activity))

        dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setCancelable(true) // рекомендовано: юзер може закрити
            .create()

        val currentDialog = dialog ?: return
        currentDialog.setOnShowListener {
            currentDialog.window?.setBackgroundDrawableResource(R.color.transparent)
        }
        currentDialog.setOnDismissListener {
            dialog = null
            isCheckingOrShowing = false
        }

        binding.btnRate.setOnClickListener {
            currentDialog.dismiss()

            lifecycleOwner.lifecycleScope.launch {
                prefs.markRated()
            }

            PlayStore.openAppListing(activity)
        }

        binding.btnLater.setOnClickListener {
            currentDialog.dismiss()
            // lastShownAt вже записаний при показі → повтор через 14 днів
        }

        binding.btnNoThanks.setOnClickListener {
            currentDialog.dismiss()
            lifecycleOwner.lifecycleScope.launch {
                prefs.setDoNotAskAgain(true)
            }
        }

        currentDialog.show()
    }
}
