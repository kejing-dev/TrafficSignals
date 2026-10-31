package io.embodit.trafficsignals.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.embodit.trafficsignals.platform.TsLog

/**
 * Debug builds only (action `io.embodit.trafficsignals.DEBUG`), see [DebugCommands]:
 *
 *   adb -s emulator-5554 shell "am broadcast -a io.embodit.trafficsignals.DEBUG -p io.embodit.trafficsignals --es cmd 'q straight,EAST,STRAIGHT'"
 *
 * Extras: `cmd` = command name plus its argument text (or `--es s ARG`).
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra("cmd") ?: return
        val parts = raw.trim().split(Regex("\\s+"), limit = 2)
        val s = intent.getStringExtra("s") ?: parts.getOrNull(1)
        TsLog.i("CMD recv '${parts[0]}' s='$s'")
        DebugCommands.run(parts[0], s)
    }
}
