package dev.jitesh.glassisle

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val r = RemoteInput.getResultsFromIntent(i)?.getCharSequence("reply")
        android.util.Log.i("GI_TEST", "notification action=" + i.action + " reply=" + r)
    }
}
