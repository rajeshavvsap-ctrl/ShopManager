package com.shopmanager.app.ui.sales

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.PaymentMode
import com.shopmanager.app.ui.components.AmountField
import com.shopmanager.app.ui.theme.Success
import com.shopmanager.app.util.money
import com.shopmanager.app.util.qrBitmap
import com.shopmanager.app.util.round2
import com.shopmanager.app.util.toAmountOrNull
import com.shopmanager.app.util.upiUri

@Composable
fun PaymentDialog(
    total: Double,
    upiId: String,
    storeName: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (mode: PaymentMode, upi: Double, cash: Double, upiRef: String?) -> Unit
) {
    var mode by remember { mutableStateOf(PaymentMode.UPI) }
    var cashReceived by remember { mutableStateOf("") }
    var upiPart by remember { mutableStateOf("") }
    var upiRef by remember { mutableStateOf("") }

    val received = cashReceived.toAmountOrNull()
    val upiSplit = upiPart.toAmountOrNull()

    // Amount that must be collected via UPI (for QR) and validation per mode
    val upiDue = when (mode) {
        PaymentMode.UPI -> total
        PaymentMode.SPLIT -> upiSplit ?: 0.0
        else -> 0.0
    }
    val error: String? = when (mode) {
        PaymentMode.CASH -> when {
            cashReceived.isBlank() -> null // optional: blank means exact cash
            received == null -> "Invalid amount"
            received < total -> "Cash received is less than the bill"
            else -> null
        }
        PaymentMode.SPLIT -> when {
            upiSplit == null -> "Enter UPI amount"
            upiSplit <= 0 || upiSplit >= total -> "UPI amount must be between ₹0 and the total"
            else -> null
        }
        else -> null
    }
    val canConfirm = error == null && !saving && !(mode == PaymentMode.SPLIT && upiSplit == null)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Payment  •  ${total.money()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PaymentMode.UPI, PaymentMode.CASH, PaymentMode.SPLIT).forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }

                if (mode == PaymentMode.CASH) {
                    AmountField(
                        value = cashReceived, onValueChange = { cashReceived = it },
                        label = "Cash received (optional)", error = error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (received != null && received >= total) {
                        Text(
                            "Return change: ${(received - total).round2().money()}",
                            color = Success, fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (mode == PaymentMode.SPLIT) {
                    AmountField(
                        value = upiPart, onValueChange = { upiPart = it },
                        label = "UPI amount", error = if (upiPart.isBlank()) null else error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (upiSplit != null && error == null) {
                        Text("Collect cash: ${(total - upiSplit).round2().money()}", fontWeight = FontWeight.Bold)
                    }
                }

                if (mode != PaymentMode.CASH && upiDue > 0) {
                    if (upiId.isNotBlank()) {
                        val qr = remember(upiDue, upiId) {
                            qrBitmap(upiUri(upiId, storeName, upiDue, "Bill payment")).asImageBitmap()
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(bitmap = qr, contentDescription = "UPI QR code", modifier = Modifier.size(200.dp))
                            Text("Scan to pay ${upiDue.money()} to $upiId", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            "Tip: add your UPI ID in Settings to show a payment QR code here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = upiRef, onValueChange = { upiRef = it.take(30) },
                        label = { Text("UPI reference / UTR (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = canConfirm, onClick = {
                when (mode) {
                    PaymentMode.UPI -> onConfirm(mode, total, 0.0, upiRef)
                    PaymentMode.CASH -> onConfirm(mode, 0.0, total, null)
                    else -> {
                        val u = (upiSplit ?: 0.0).round2()
                        onConfirm(mode, u, (total - u).round2(), upiRef)
                    }
                }
            }) { Text(if (saving) "Saving…" else "Payment received") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } }
    )
}
