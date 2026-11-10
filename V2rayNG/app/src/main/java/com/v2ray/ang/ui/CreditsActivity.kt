package com.v2ray.ang.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.util.Utils

class CreditsActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        CreditsScreen(onBackClick = { finish() })
    }
}

private data class CreditEntry(
    val name: String,
    val author: String,
    val license: String?,
    @StringRes val description: Int,
    val url: String,
)

private val credits = listOf(
    CreditEntry(
        name = "Xray-core",
        author = "XTLS",
        license = "MPL-2.0",
        description = R.string.credits_desc_xray,
        url = AppConfig.XRAY_URL,
    ),
    CreditEntry(
        name = "v2rayNG",
        author = "2dust",
        license = "GPL-3.0",
        description = R.string.credits_desc_v2rayng,
        url = AppConfig.V2RAYNG_URL,
    ),
    CreditEntry(
        name = "AndroidLibXrayLite",
        author = "2dust",
        license = "GPL-3.0",
        description = R.string.credits_desc_libxray,
        url = "${AppConfig.GITHUB_URL}/2dust/AndroidLibXrayLite",
    ),
    CreditEntry(
        name = "HEV Socks5 Tunnel",
        author = "heiher",
        license = "MIT",
        description = R.string.credits_desc_hev,
        url = "${AppConfig.GITHUB_URL}/heiher/hev-socks5-tunnel",
    ),
    CreditEntry(
        name = "net-merge",
        author = "Rohit Mahto",
        license = "MIT",
        description = R.string.credits_desc_netmerge,
        url = AppConfig.NET_MERGE_ENGINE_URL,
    ),
    CreditEntry(
        name = "netmerge-android",
        author = "ziyadshafeek",
        license = null,
        description = R.string.credits_desc_netmerge_android,
        url = AppConfig.NET_MERGE_URL,
    ),
    CreditEntry(
        name = "Shizzi",
        author = "carlelieser",
        license = null,
        description = R.string.credits_desc_shizzi,
        url = AppConfig.SHIZZI_URL,
    ),
    CreditEntry(
        name = "Shizuku",
        author = "RikkaApps",
        license = "Apache-2.0",
        description = R.string.credits_desc_shizuku,
        url = AppConfig.SHIZUKU_URL,
    ),
    CreditEntry(
        name = "VPN Hotspot",
        author = "Mygod and contributors",
        license = "Apache-2.0",
        description = R.string.credits_desc_vpnhotspot,
        url = AppConfig.VPNHOTSPOT_URL,
    ),
    CreditEntry(
        name = "HotspotXray",
        author = "shayanmahster",
        license = "Proprietary",
        description = R.string.credits_desc_hotspotxray,
        url = "${AppConfig.GITHUB_URL}/shayanmahster/HotspotXray",
    ),
    CreditEntry(
        name = "Jetpack Compose and Material 3",
        author = "Google / AOSP",
        license = "Apache-2.0",
        description = R.string.credits_desc_compose,
        url = "https://developer.android.com/jetpack/compose",
    ),
    CreditEntry(
        name = "INCY",
        author = "itdev",
        license = "Proprietary",
        description = R.string.credits_desc_incy,
        url = "https://incy.ac",
    ),
)

@Composable
private fun CreditsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.credits_title),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.credits_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            credits.forEach { entry ->
                CreditCard(entry = entry, onClick = { Utils.openUri(context, entry.url) })
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.credits_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun CreditCard(entry: CreditEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.credits_author, entry.author),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(entry.description),
                style = MaterialTheme.typography.bodySmall
            )
            if (entry.license != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.credits_license, entry.license),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
