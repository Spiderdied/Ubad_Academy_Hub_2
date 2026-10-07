package com.ubad.academy.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ubad.academy.R
import com.ubad.academy.data.cloud.CloudSyncStatus
import com.ubad.academy.data.cloud.CloudUser
import java.text.DateFormat
import java.util.Date

/**
 * Settings → Google account and cloud sync.
 *
 * Mirrors what the web app puts in its settings screen, with the same strings
 * and the same three choices. Two things it does differently, both deliberate:
 *
 *  - When the build has no Google **web** client id (`googleWebClientId` is
 *    blank), sign-in cannot work at all. Rather than showing a button that fails
 *    on tap, it says so plainly and points at how to configure it. Silent
 *    breakage here would be far worse than an honest message.
 *  - Sync failures are always surfaced with a message. The web only toasts some
 *    of them, which makes "why did my data stop syncing" unanswerable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudSettingsSection(
    modifier: Modifier = Modifier,
    user: CloudUser?,
    status: CloudSyncStatus,
    signInAvailable: Boolean,
    configured: Boolean,
    busy: Boolean,
    onSignIn: (Activity) -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit,
    onDownload: () -> Unit,
    onMerge: () -> Unit,
    onDismissChoice: () -> Unit,
) {
    val context = LocalContext.current

    SetGroup(
        icon = if (user == null) Icons.Outlined.CloudOff else Icons.Outlined.Cloud,
        title = stringResource(R.string.set_cloudSync),
        modifier = modifier,
        desc = if (user == null) {
            stringResource(R.string.set_cloudSyncOff)
        } else {
            stringResource(R.string.set_cloudSyncOn)
        },
    ) {
        if (!configured) {
            // The build has no Firebase client config; say so instead of failing later.
            Text(
                stringResource(R.string.set_cloudNotConfigured),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SetGroup
        }

        if (user == null) {
            if (!signInAvailable) {
                Text(
                    stringResource(R.string.set_cloudNoClientId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@SetGroup
            }
            Button(
                onClick = { (context as? Activity)?.let(onSignIn) },
                enabled = !busy && context is Activity,
            ) {
                Icon(Icons.Outlined.CloudUpload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.set_googleSignIn))
            }
            return@SetGroup
        }

        // ── signed in ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.set_googleSignedIn, user.label),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (user.email.isNotBlank()) {
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val detail = when {
                status.busy -> stringResource(R.string.set_syncing)
                status.error != null -> status.error
                status.lastSyncAt > 0 -> {
                    val when_ = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(status.lastSyncAt))
                    "${stringResource(R.string.set_synced)} · $when_ · ${status.fileCount} ${stringResource(R.string.set_syncedFiles)}"
                }
                else -> stringResource(R.string.set_cloudSyncOn)
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSyncNow, enabled = !busy && !status.busy) {
                if (status.busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.set_syncNow))
            }
            OutlinedButton(onClick = onDownload, enabled = !busy && !status.busy) {
                Icon(Icons.Outlined.CloudDone, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.set_downloadCloud))
            }
            OutlinedButton(onClick = onSignOut, enabled = !busy && !status.busy) {
                Icon(Icons.Outlined.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.set_googleSignOut))
            }
        }
    }

    // First sync with data on both sides: never decide silently (see the web's
    // `firstSyncChoice`). Upload / Restore / Merge, and the answer is remembered
    // per account.
    status.pendingRemote?.let { remote ->
        if (!status.needsFirstChoice) return@let
        FirstSyncDialog(
            fileCount = remote.files.size,
            onUpload = { onSyncNow() },
            onRestore = { onDownload() },
            onMerge = { onMerge() },
            onDismiss = onDismissChoice,
        )
    }
}

@Composable
private fun FirstSyncDialog(
    fileCount: Int,
    onUpload: () -> Unit,
    onRestore: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_firstTitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.sync_firstBody))
                Text(
                    "$fileCount ${stringResource(R.string.set_syncedFiles)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChoiceRow(
                    label = stringResource(R.string.sync_upload),
                    desc = stringResource(R.string.sync_uploadDesc),
                    onClick = onUpload,
                )
                ChoiceRow(
                    label = stringResource(R.string.sync_restore),
                    desc = stringResource(R.string.sync_restoreDesc),
                    onClick = onRestore,
                )
                ChoiceRow(
                    label = stringResource(R.string.sync_merge),
                    desc = stringResource(R.string.sync_mergeDesc),
                    onClick = onMerge,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, desc: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
