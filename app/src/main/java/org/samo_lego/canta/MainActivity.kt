package org.samo_lego.canta

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.samo_lego.canta.extension.getInfoForPackage
import org.samo_lego.canta.ui.CantaApp
import org.samo_lego.canta.ui.theme.CantaTheme
import org.samo_lego.canta.util.ShizukuPackageInstallerUtils
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
const val APP_NAME = "Canta"
const val packageName = "org.samo_lego.canta"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CantaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        CantaApp(
                            launchShizuku = {
                                // Open shizuku app
                                val launchIntent =
                                    packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
                                startActivity(launchIntent)
                            },
                            uninstallApp = { uninstallApp(it) },
                            reinstallApp = { reinstallApp(it) },
                        )
                        ADBTerminal()
                    }
                }
            }
        }
    }

    @Composable
    fun ADBTerminal() {
        var command by remember { mutableStateOf("") }
        var output by remember { mutableStateOf("") }

        Column(modifier = Modifier.padding(16.dp)) {
            TextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Enter ADB Command") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { executeADBCommand(command) { result -> output = result } }) {
                Text("Execute")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = output)
        }
    }

    private fun executeADBCommand(command: String, callback: (String) -> Unit) {
        try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                output.append(line).append("\n")
                line = reader.readLine()
            }
            process.waitFor()
            callback(output.toString())
        } catch (e: Exception) {
            e.printStackTrace()
            callback("Error executing command: ${e.message}")
        }
    }

    private fun uninstallApp(packageName: String): Boolean {
        val broadcastIntent = Intent("org.samo_lego.canta.UNINSTALL_RESULT_ACTION")
        val intent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val packageInstaller = getPackageInstaller()
        val packageInfo = packageManager.getInfoForPackage(packageName)

        val isSystem = (packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        Log.i(APP_NAME, "Uninstalling '$packageName' [system: $isSystem]")

        // 0x00000004 = PackageManager.DELETE_SYSTEM_APP
        // 0x00000002 = PackageManager.DELETE_ALL_USERS
        val flags = if (isSystem) 0x00000004 else 0x00000002

        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                PackageInstaller::class.java.getDeclaredMethod(
                    "uninstall",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    PendingIntent::class.java
                ).invoke(packageInstaller, packageName, flags, intent)
            } else {
                HiddenApiBypass.invoke(
                    PackageInstaller::class.java,
                    packageInstaller,
                    "uninstall",
                    packageName,
                    flags,
                    intent.intentSender
                )
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun reinstallApp(packageName: String): Boolean {
        val installReason = PackageManager.INSTALL_REASON_UNKNOWN
        val broadcastIntent = Intent("org.samo_lego.canta.INSTALL_RESULT_ACTION")
        val intent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PackageManager.INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS
        val installFlags = 0x00400000

        return try {
            HiddenApiBypass.invoke(
                PackageInstaller::class.java,
                ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller(),
                "installExistingPackage",
                packageName,
                installFlags,
                installReason,
                intent.intentSender,
                0,
                null
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getPackageInstaller(): PackageInstaller {
        val iPackageInstaller = ShizukuPackageInstallerUtils.getPrivilegedPackageInstaller()
        val root = Shizuku.getUid() == 0
        val userId = if (root) android.os.Process.myUserHandle().hashCode() else 0

        // The reason for use "com.android.shell" as installer package under adb is that
        // getMySessions will check installer package's owner
        return ShizukuPackageInstallerUtils.createPackageInstaller(
            iPackageInstaller, "com.android.shell", userId, this
        )
    }
}
