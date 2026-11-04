package com.davidadrianrg.atril.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.davidadrianrg.atril.core.auth.DeviceAuthError
import com.davidadrianrg.atril.core.auth.QrMatrix
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.ui.components.rememberWindowInfo
import com.davidadrianrg.atril.ui.viewmodel.QrLoginState

/** Always dark-on-white with a quiet zone, whatever the theme, so scanners can read it. */
@Composable
fun QrImage(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .semantics { contentDescription = tr("romm.qr.cd") },
    ) {
        val cell = size.minDimension / matrix.size
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y]) {
                drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** Landscape: QR on the left, status on the right. Portrait: stacked. Scrolls on short screens. */
@Composable
fun QrLoginDialog(
    state: QrLoginState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 640.dp),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(tr("romm.qr.title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                val landscape = rememberWindowInfo().isLandscape
                val qr: @Composable () -> Unit = {
                    if (state is QrLoginState.Waiting) QrImage(state.qr, Modifier.size(if (landscape) 176.dp else 220.dp))
                }
                val status: @Composable () -> Unit = {
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
                                Text(tr("romm.qr.waiting"), style = MaterialTheme.typography.bodyMedium)
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
                        is QrLoginState.Failed -> Text(qrErrorText(state.kind), color = MaterialTheme.colorScheme.error)
                    }
                }
                if (landscape) {
                    // Horizontal: QR a la izquierda, estado y acciones a la derecha.
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        qr()
                        Column(Modifier.weight(1f)) { status() }
                    }
                } else {
                    // Vertical: apilado; en fila el texto quedaba en ~50 dp.
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        qr()
                        if (state is QrLoginState.Waiting) Spacer(Modifier.height(16.dp))
                        Column(Modifier.fillMaxWidth()) { status() }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val retryable = state is QrLoginState.Denied || state is QrLoginState.Expired || state is QrLoginState.Failed
                    OutlinedButton(onClick = onClose) {
                        Text(if (state is QrLoginState.Success) tr("romm.qr.done") else tr("romm.qr.cancel"))
                    }
                    if (retryable) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onRetry) { Text(tr("romm.qr.retry")) }
                    }
                }
            }
        }
    }
}

private fun qrErrorText(kind: DeviceAuthError): String = when (kind) {
    DeviceAuthError.NETWORK -> tr("romm.qr.err.network")
    DeviceAuthError.INVALID_URL -> tr("romm.qr.err.url")
    DeviceAuthError.UNSUPPORTED -> tr("romm.qr.err.unsupported")
    DeviceAuthError.UNAUTHORIZED -> tr("romm.qr.err.unauthorized")
    DeviceAuthError.SERVER -> tr("romm.qr.err.server")
}
