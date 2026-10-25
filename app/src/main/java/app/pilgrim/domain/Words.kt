package app.pilgrim.domain

import java.time.LocalDate

/** Russian or English, side by side where the words are used. */
fun t(ru: Boolean, r: String, e: String) = if (ru) r else e

object Words {
    private val roman = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
    private val monthsRu = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val monthsEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    fun roman(n: Int): String {
        if (n <= 0) return ""
        val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var x = n
        return buildString { for (i in v.indices) while (x >= v[i]) { append(s[i]); x -= v[i] } }
    }

    /** The date on a stamp: "25 · X · 2026". */
    fun stampDate(d: LocalDate) = "${d.dayOfMonth} · ${roman[d.monthValue - 1]} · ${d.year}"

    /** "25 октября 2026" / "25 October 2026". */
    fun date(d: LocalDate, ru: Boolean, year: Boolean = true) =
        if (ru) "${d.dayOfMonth} ${monthsRu[d.monthValue - 1]}${if (year) " ${d.year}" else ""}"
        else "${d.dayOfMonth} ${monthsEn[d.monthValue - 1]}${if (year) " ${d.year}" else ""}"

    fun plural(n: Long, one: String, few: String, many: String): String {
        val a = n % 100
        val b = n % 10
        return when {
            a in 11..14 -> many
            b == 1L -> one
            b in 2..4 -> few
            else -> many
        }
    }

    /** "1 ч 20 мин" / "1 h 20 min". */
    fun duration(seconds: Long, ru: Boolean): String {
        val m = seconds / 60
        val h = m / 60
        return when {
            h == 0L -> "$m ${t(ru, "мин", "min")}"
            m % 60 == 0L -> "$h ${t(ru, "ч", "h")}"
            else -> "$h ${t(ru, "ч", "h")} ${m % 60} ${t(ru, "мин", "min")}"
        }
    }

    fun times(n: Int, ru: Boolean) = if (ru) "$n ${plural(n.toLong(), "раз", "раза", "раз")}" else if (n == 1) "once" else "$n times"

    fun days(n: Int, ru: Boolean) = if (ru) "$n ${plural(n.toLong(), "день", "дня", "дней")}" else if (n == 1) "1 day" else "$n days"

    fun stamps(n: Int, ru: Boolean) = if (ru) "$n ${plural(n.toLong(), "печать", "печати", "печатей")}" else if (n == 1) "1 stamp" else "$n stamps"

    fun places(n: Int, ru: Boolean) = if (ru) "$n ${plural(n.toLong(), "место", "места", "мест")}" else if (n == 1) "1 place" else "$n places"

    /** "в 5-й раз" / "for the 5th time". */
    fun nth(n: Int, ru: Boolean): String = if (ru) "$n-й" else when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    /** One word, as the fields keep it: no spaces, a sensible length. */
    fun word(s: String) = s.trim().split(Regex("\\s+")).firstOrNull().orEmpty().take(24)
}
