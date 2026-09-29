package com.autotennisclub.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.autotennisclub.app.ui.theme.BorderGray
import com.autotennisclub.app.ui.theme.Green
import com.autotennisclub.app.ui.theme.GreenPale
import com.autotennisclub.app.ui.theme.Navy
import com.autotennisclub.app.ui.theme.TextSecondary
import java.util.Locale

/** Customer languages (Figma Home: ES · CAT · EN). */
enum class AppLanguage(val tag: String, val label: String) {
    ES("es", "ES"),
    CA("ca", "CAT"),
    EN("en", "EN");

    companion object {
        /** Language each new customer starts with. */
        val DEFAULT = ES
    }
}

/** Renders [content] in [language], whatever the tablet's system language is. */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val configuration = remember(language, base) {
        Configuration(base).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    }
    val localized = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides configuration,
        content = content
    )
}

/**
 * A Dialog opens its own window, which resets LocalContext / LocalConfiguration to the
 * tablet's language. Re-provide the customer's language inside it.
 */
@Composable
fun LocalizedDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    Dialog(onDismissRequest = onDismissRequest) {
        CompositionLocalProvider(
            LocalContext provides context,
            LocalConfiguration provides configuration,
            content = content
        )
    }
}

@Composable
internal fun LanguagePicker(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppLanguage.entries.forEach { language ->
            val isSelected = language == selected
            OutlinedButton(
                onClick = { onSelect(language) },
                modifier = Modifier.width(88.dp).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Green else BorderGray),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isSelected) GreenPale else androidx.compose.ui.graphics.Color.Transparent
                )
            ) {
                Text(
                    language.label,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Navy else TextSecondary
                )
            }
        }
    }
}
