package com.notes.easynotebook.ui

import android.app.Activity
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
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

    fun maybeShowRateDialog(activity: Activity, lifecycleOwner: LifecycleOwner) {

        lifecycleOwner.lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val state = prefs.state.first()
            if (!gate.shouldShow(state, now)) return@launch
            prefs.setLastShown(now)
            showDialog(activity, lifecycleOwner)
        }
    }

    private fun showDialog(activity: Activity, lifecycleOwner: LifecycleOwner) {
        val binding = DialogRateAppBinding.inflate(LayoutInflater.from(activity))

        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.color.transparent)
        }

        binding.btnRate.setOnClickListener {
            dialog.dismiss()

            lifecycleOwner.lifecycleScope.launch {
                prefs.markRated()
            }

            PlayStore.openAppListing(activity)
        }

        binding.btnLater.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnNoThanks.setOnClickListener {
            dialog.dismiss()
            lifecycleOwner.lifecycleScope.launch {
                prefs.setDoNotAskAgain(true)
            }
        }
        dialog.show()
    }
}