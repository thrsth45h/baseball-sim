package com.example.baseballsim

import com.example.baseballsim.season.PennantSchedule

private val observedClubNames = listOf(
    "読売ジャイアンツ", "阪神タイガース", "横浜DeNAベイスターズ",
    "広島東洋カープ", "東京ヤクルトスワローズ", "中日ドラゴンズ",
    "福岡ソフトバンクホークス", "北海道日本ハムファイターズ", "オリックス・バファローズ",
    "東北楽天ゴールデンイーグルス", "千葉ロッテマリーンズ", "埼玉西武ライオンズ",
)

internal fun expectedPennantClubName(id: String) = observedClubNames[PennantSchedule.clubIds.indexOf(id)]
