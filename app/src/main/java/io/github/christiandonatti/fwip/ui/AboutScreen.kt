package io.github.christiandonatti.fwip.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.christiandonatti.fwip.R
import io.github.christiandonatti.fwip.str
import io.github.christiandonatti.fwip.ui.theme.FwipColors

private const val REPO = "https://github.com/ChristianDonatti/fwip"
private const val GPL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** Version, license, credits and language: everything a free-software app owes its users. */
@Composable
fun AboutScreen(gutter: Dp, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircleIconButton(Icons.Filled.ArrowBack, str(R.string.back), onBack, size = 44.dp)
            Spacer(Modifier.height(4.dp))
            FwipLogo(size = 30)
            Text(str(R.string.version, version), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            Text(str(R.string.about_tagline), style = MaterialTheme.typography.bodyLarge, color = FwipColors.TextSoft)
            Text(str(R.string.made_by), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextMuted)
            PillButton(str(R.string.source_code), { open(REPO) }, style = PillStyle.Ghost, height = 48.dp)

            Section(str(R.string.language)) {
                Text(str(R.string.language_hint), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PillButton(
                        str(R.string.choose_language),
                        {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                )
                            }
                        },
                        style = PillStyle.Ghost,
                        height = 48.dp,
                    )
                }
            }

            Section(str(R.string.license)) {
                Text(str(R.string.license_text), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
                PillButton(str(R.string.read_license), { open(GPL) }, style = PillStyle.Ghost, height = 48.dp)
            }

            Section(str(R.string.credits)) {
                Text(str(R.string.credit_model), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
                Text(str(R.string.credit_fonts), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
                Text(str(R.string.credit_libs), style = MaterialTheme.typography.bodyMedium, color = FwipColors.TextSoft)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FwipColors.Surface, androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
