package dev.axiom.launcher

import android.content.Intent
import android.net.Uri

/**
 * AXIOM never installs/uninstalls packages itself. Android's Package Installer
 * shows the system confirmation and enforces device/profile policy.
 */
internal fun canRequestUninstall(app: InstalledApp, launcherPackage: String): Boolean =
    app.packageName.isNotBlank() &&
    app.packageName != launcherPackage &&
    !app.system

internal fun uninstallConfirmationIntent(app: InstalledApp): Intent =
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Safe fallback for protected/system apps or locked profiles. */
internal fun applicationDetailsIntent(app: InstalledApp): Intent =
    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", app.packageName, null))
