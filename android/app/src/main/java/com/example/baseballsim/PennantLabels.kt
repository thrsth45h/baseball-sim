package com.example.baseballsim

import com.example.baseballsim.season.PennantSchedule
import com.example.baseballsim.season.Standing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class PennantClubLabel(val full: String, val short: String)

private val clubLabels = PennantSchedule.clubIds.zip(listOf(
    PennantClubLabel("読売ジャイアンツ", "巨人"),
    PennantClubLabel("阪神タイガース", "阪神"),
    PennantClubLabel("横浜DeNAベイスターズ", "DeNA"),
    PennantClubLabel("広島東洋カープ", "広島"),
    PennantClubLabel("東京ヤクルトスワローズ", "ヤクルト"),
    PennantClubLabel("中日ドラゴンズ", "中日"),
    PennantClubLabel("福岡ソフトバンクホークス", "ソフトバンク"),
    PennantClubLabel("北海道日本ハムファイターズ", "日本ハム"),
    PennantClubLabel("オリックス・バファローズ", "オリックス"),
    PennantClubLabel("東北楽天ゴールデンイーグルス", "楽天"),
    PennantClubLabel("千葉ロッテマリーンズ", "ロッテ"),
    PennantClubLabel("埼玉西武ライオンズ", "西武"),
)).toMap()

internal fun pennantClubLabel(id: String) = clubLabels.getValue(id)

private val dateFormat = DateTimeFormatter.ofPattern("uuuu年M月d日（E）", Locale.JAPANESE)

internal fun pennantLocalDate(year: Long, dayIndex: Int): LocalDate =
    LocalDate.of(Math.toIntExact(2025L + year), 3, 27).plusDays(dayIndex.toLong() - 2L)

internal fun pennantDate(date: LocalDate): String = date.format(dateFormat)

internal fun pennantDate(year: Long, dayIndex: Int): String = pennantDate(pennantLocalDate(year, dayIndex))

internal fun winPercentage(row: Standing): String {
    val ratio = row.totals.ratio ?: return "—"
    return String.format(Locale.ROOT, "%.3f", ratio.numerator.toDouble() / ratio.denominator).removePrefix("0")
}

internal fun gamesBehind(row: Standing, leader: Standing?): String {
    if (leader == null || row.rank == null || row.rank == 1) return "—"
    val halfGames = leader.totals.wins - row.totals.wins + row.totals.losses - leader.totals.losses
    return String.format(Locale.ROOT, "%.1f", halfGames / 2.0)
}
