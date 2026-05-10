package com.abizer_r.quickedit.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.abizer_r.quickedit.R

@Composable
fun ConfirmExitDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_exit_title)) },
        text = { Text(stringResource(R.string.confirm_exit_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
