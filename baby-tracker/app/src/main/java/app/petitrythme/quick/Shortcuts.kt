package app.petitrythme.quick

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.petitrythme.data.EventType

/**
 * Raccourcis affichés par un appui long sur l'icône de l'appli :
 * Pipi, Caca, Biberon, Tétée.
 */
object Shortcuts {
    private const val VERSION = 2

    fun installOnce(context: Context) {
        val prefs = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) == VERSION) return
        val list = listOf(
            build(context, "pee", "Pipi", "Noter un pipi", "💧", QuickEntryActivity.quick(context, "PEE")),
            build(context, "poo", "Caca", "Noter un caca", "💩", QuickEntryActivity.quick(context, "POO")),
            build(context, "bottle", "Biberon", "Noter un biberon", "🍼", QuickEntryActivity.intent(context, EventType.BOTTLE)),
            build(context, "nurse", "Tétée", "Lancer une tétée", "🤱", QuickEntryActivity.quick(context, QuickEntryActivity.QUICK_NURSE)),
        ).mapIndexed { i, b -> b.setRank(i).build() }
        // Peut échouer si le lanceur limite les mises à jour : on réessaiera au prochain démarrage.
        val ok = runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }.getOrDefault(false)
        if (ok) prefs.edit().putInt("version", VERSION).apply()
    }

    private fun build(context: Context, id: String, short: String, long: String, emoji: String, intent: android.content.Intent) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(emojiIcon(emoji))
            .setIntent(intent)

    /** Icône adaptative : l'emoji centré sur un fond rose pâle. */
    private fun emojiIcon(emoji: String): IconCompat {
        val size = 216
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFCE3EA.toInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.36f
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(emoji, size / 2f, y, p)
        return IconCompat.createWithAdaptiveBitmap(bmp)
    }
}
