package org.shizukuadb.install

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.shizukuadb.install.apk.ApkInfo
import org.shizukuadb.install.model.InstallState
import org.shizukuadb.install.model.ShizukuState
import org.shizukuadb.install.ui.AmoledTheme
import org.shizukuadb.install.viewmodel.InstallerViewModel

private const val APK_MIME = "application/vnd.android.package-archive"

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<InstallerViewModel>()

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::openApk) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            AmoledTheme {
                InstallerApp(
                    viewModel = viewModel,
                    onOpenApk = { openDocument.launch(arrayOf(APK_MIME)) },
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (intent.type == APK_MIME || uri.toString().endsWith(".apk", ignoreCase = true)) {
            if ((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Temporary grants are sufficient for the foreground installation flow.
                }
            }
            viewModel.openApk(uri)
        }
    }
}

@Composable
private fun InstallerApp(
    viewModel: InstallerViewModel,
    onOpenApk: () -> Unit,
    onFinish: () -> Unit
) {
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val showBack = installState !is InstallState.Idle

    BackHandler(enabled = showBack) { viewModel.resetToHome() }
    LaunchedEffect(Unit) { viewModel.refreshShizuku() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        when (val state = installState) {
            InstallState.Idle -> HomeScreen(
                shizukuState = shizukuState,
                message = message,
                onOpenApk = onOpenApk,
                onRefresh = viewModel::refreshShizuku,
                onRequestPermission = viewModel::requestShizukuPermission,
                onOpenShizuku = {
                    viewModel.openShizuku()?.let(context::startActivity)
                }
            )
            InstallState.LoadingApk -> LoadingScreen("Reading APK information")
            is InstallState.Ready -> ConfirmationScreen(
                apk = state.apk,
                shizukuState = shizukuState,
                onBack = viewModel::resetToHome,
                onInstall = viewModel::install,
                onRequestPermission = viewModel::requestShizukuPermission,
                onOpenShizuku = {
                    viewModel.openShizuku()?.let(context::startActivity)
                }
            )
            is InstallState.Installing -> InstallingScreen(state.apk)
            is InstallState.Success -> SuccessScreen(state.apk, state.message, onFinish)
            is InstallState.Failure -> FailureScreen(
                apk = state.apk,
                message = state.message,
                onRetry = viewModel::retry,
                onHome = viewModel::resetToHome
            )
        }
    }
}

@Composable
private fun HomeScreen(
    shizukuState: ShizukuState,
    message: String?,
    onOpenApk: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    AppScaffold {
        AppHeader()
        Spacer(Modifier.height(28.dp))
        StatusCard(shizukuState, onRefresh, onRequestPermission, onOpenShizuku)
        Spacer(Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101010))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(18.dp))
                Text("Open APK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Select an APK file as a fallback entry point.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onOpenApk,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Open APK")
                }
            }
        }
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            InlineMessage(message)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Tip: tap an APK in any file manager and choose Shizuku Installer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConfirmationScreen(
    apk: ApkInfo,
    shizukuState: ShizukuState,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    AppScaffold {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Install APK?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        StatusCard(shizukuState, null, onRequestPermission, onOpenShizuku)
        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101010))
        ) {
            Column(Modifier.padding(24.dp)) {
                Icon(Icons.Default.InstallMobile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    apk.fileName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!apk.applicationLabel.isNullOrBlank() && apk.applicationLabel != apk.fileName) {
                    Spacer(Modifier.height(4.dp))
                    Text(apk.applicationLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                InfoRow("Package", apk.packageName)
                InfoRow("Version", apk.versionName)
                InfoRow("Version code", apk.versionCode.toString())
                InfoRow("Size", apk.fileSizeLabel)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onInstall,
            enabled = shizukuState is ShizukuState.Connected,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.InstallMobile, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Install APK")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Cancel")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Installation starts only after you press Install APK.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusCard(
    state: ShizukuState,
    onRefresh: (() -> Unit)?,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    val connected = state is ShizukuState.Connected
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (connected) Color(0xFF10251B) else Color(0xFF211B12))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (connected) Icons.Default.CheckCircle else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (connected) Color(0xFF78E5A5) else Color(0xFFFFCF70),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.title, fontWeight = FontWeight.SemiBold)
                    Text(state.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
            if (!connected) {
                Spacer(Modifier.height(14.dp))
                when (state) {
                    ShizukuState.PermissionRequired -> OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("Check Shizuku Access")
                    }
                    ShizukuState.NotInstalled, ShizukuState.NotRunning -> OutlinedButton(onClick = onOpenShizuku, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Shizuku")
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.Top
    ) {
        item { Column { content() } }
    }
}

@Composable
private fun AppHeader() {
    Column {
        Text("Shizuku Installer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Install APKs with Shizuku", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LoadingScreen(label: String) {
    CenteredState {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(22.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun InstallingScreen(apk: ApkInfo) {
    CenteredState {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("Installing", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(apk.fileName, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Please wait...", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuccessScreen(apk: ApkInfo, message: String, onFinish: () -> Unit) {
    CenteredState {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF78E5A5), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(22.dp))
        Text("Installation successful", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(apk.fileName, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Done") }
    }
}

@Composable
private fun FailureScreen(apk: ApkInfo?, message: String, onRetry: () -> Unit, onHome: () -> Unit) {
    CenteredState {
        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(22.dp))
        Text("Installation failed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (apk != null) {
            Spacer(Modifier.height(8.dp))
            Text(apk.fileName, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try Again")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text("Back to Home") }
    }
}

@Composable
private fun InlineMessage(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF26191A)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, color = Color(0xFFFFDAD6))
        }
    }
}

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { content() }
    }
}
