package ru.marslab.ide.ride.theme

import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Единый источник цветовых токенов UI.
 * Источник правды — настройки плагина (в дальнейшем можно привязать к системной теме IDE).
 */
 data class ThemeTokens(
     // Общие
     val bg: String,
     val textPrimary: String,
     val textSecondary: String,
     // Префиксы/метки
     val prefix: String,
     // Сообщения пользователя
     val userBg: String,
     val userBorder: String,
     // Кодовые блоки
     val codeBg: String,
     val codeText: String,
     val codeBorder: String,
 ) {
     fun toJcefMap(): Map<String, String> = mapOf(
         "bg" to bg,
         "textPrimary" to textPrimary,
         "textSecondary" to textSecondary,
         "prefix" to prefix,
         "userBg" to userBg,
         "userBorder" to userBorder,
         "codeBg" to codeBg,
         "codeText" to codeText,
         "codeBorder" to codeBorder,
     )

     companion object {
         fun fromSettings(settings: PluginSettings): ThemeTokens = ThemeTokens(
             // Пока используем фон код-блока как общий фон. Позже добавим отдельный параметр.
             bg = settings.chatCodeBackgroundColor,
             textPrimary = "#e6e6e6",
             textSecondary = "#9aa0a6",
             prefix = settings.chatPrefixColor,
             userBg = settings.chatUserBackgroundColor,
             userBorder = settings.chatUserBorderColor,
             codeBg = settings.chatCodeBackgroundColor,
             codeText = settings.chatCodeTextColor,
             codeBorder = settings.chatCodeBorderColor,
         )
     }
 }
