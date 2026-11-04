package com.davidadrianrg.atril.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidadrianrg.atril.core.auth.DeviceAuthError
import com.davidadrianrg.atril.core.auth.QrLoginState
import com.davidadrianrg.atril.core.auth.QrMatrix
import com.davidadrianrg.atril.core.i18n.tr

/** Black-on-white with a quiet zone, whatever the theme, so phone cameras can read it. */
@Composable
internal fun QrImage(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp)) {
        val cell = size.minDimension / matrix.size
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

@Composable
internal fun QrLoginDialog(state: QrLoginState, onRetry: () -> Unit, onClose: () -> Unit) {
    val retryable = state is QrLoginState.Denied || state is QrLoginState.Expired || state is QrLoginState.Failed
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("romm.qr.title")) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state is QrLoginState.Waiting) QrImage(state.qr, Modifier.size(220.dp))
                Column(Modifier.width(300.dp)) {
                    when (state) {
                        is QrLoginState.Waiting -> {
                            Text(tr("romm.qr.scan"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                tr("romm.qr.code", state.code.userCode),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(tr("romm.qr.waiting"))
                            }
                        }
                        QrLoginState.Idle, QrLoginState.Starting -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(tr("romm.qr.starting"))
                        }
                        QrLoginState.Success -> Text(tr("romm.qr.success"), color = MaterialTheme.colorScheme.secondary)
                        QrLoginState.Denied -> Text(tr("romm.qr.denied"), color = MaterialTheme.colorScheme.error)
                        QrLoginState.Expired -> Text(tr("romm.qr.expired"), color = MaterialTheme.colorScheme.error)
                        is QrLoginState.Failed -> Text(errorText(state.kind), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (retryable) Button(onClick = onRetry) { Text(tr("romm.qr.retry")) }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose) {
                Text(if (state is QrLoginState.Success) tr("romm.qr.done") else tr("romm.qr.cancel"))
            }
        },
    )
}

private fun errorText(kind: DeviceAuthError): String = when (kind) {
    DeviceAuthError.NETWORK -> tr("romm.qr.err.network")
    DeviceAuthError.INVALID_URL -> tr("romm.qr.err.url")
    DeviceAuthError.UNSUPPORTED -> tr("romm.qr.err.unsupported")
    DeviceAuthError.UNAUTHORIZED -> tr("romm.qr.err.unauthorized")
    DeviceAuthError.SERVER -> tr("romm.qr.err.server")
}
