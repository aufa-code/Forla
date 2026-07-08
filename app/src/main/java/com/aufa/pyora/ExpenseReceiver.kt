package com.aufa.pyora

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.aufa.pyora.data.AppDatabase
import com.aufa.pyora.data.MoneyTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpenseReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_LOG = "com.aufa.pyora.LOG_EXPENSE"
        const val EXTRA_VISIT_ID = "visit_id"
        const val EXTRA_PLACE_ID = "place_id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val KEY_REPLY = "key_reply"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LOG) return
        val visitId = intent.getLongExtra(EXTRA_VISIT_ID, -1L)
        val placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1L).let { if (it < 0) null else it }
        var amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, visitId.toInt())

        // Kalau dari "Isi nominal" (inline reply), amount == -1 → baca teksnya
        if (amount < 0.0) {
            val remote = RemoteInput.getResultsFromIntent(intent)
            val text = remote?.getCharSequence(KEY_REPLY)?.toString()?.filter { it.isDigit() }
            amount = text?.toDoubleOrNull() ?: 0.0
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        val finalAmount = amount
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = appContext.getSharedPreferences("pyora", Context.MODE_PRIVATE)
                val set = prefs.getStringSet("confirmed_visits", emptySet())?.toMutableSet() ?: mutableSetOf()
                set.add(visitId.toString())
                prefs.edit().putStringSet("confirmed_visits", set).apply()

                if (finalAmount > 0.0 && placeId != null) {
                    AppDatabase.getInstance(appContext).transactionDao().insert(
                        MoneyTransaction(
                            amount = finalAmount,
                            type = "expense",
                            category = null,
                            placeId = placeId,
                            timestamp = System.currentTimeMillis(),
                            note = "Dari notifikasi",
                            source = "auto"
                        )
                    )
                }
                NotificationManagerCompat.from(appContext).cancel(notifId)
            } finally {
                pending.finish()
            }
        }
    }
}