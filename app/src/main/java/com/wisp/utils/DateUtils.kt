package com.wisp.utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {
    fun formatDate(date: Date): String = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(date)

    fun getRelativeTime(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "только что"
            minutes < 60 -> "$minutes ${plural(minutes, "минуту", "минуты", "минут")} назад"
            hours < 24 -> "$hours ${plural(hours, "час", "часа", "часов")} назад"
            days < 7 -> "$days ${plural(days, "день", "дня", "дней")} назад"
            else -> formatDate(date)
        }
    }

    private fun plural(n: Long, one: String, few: String, many: String): String {
        val mod10 = (n % 10).toInt()
        val mod100 = (n % 100).toInt()
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}