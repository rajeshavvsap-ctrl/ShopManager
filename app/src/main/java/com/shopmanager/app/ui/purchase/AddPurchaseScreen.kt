package com.shopmanager.app.ui.purchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.PaymentMode
import com.shopmanager.app.data.Purchase
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AmountField
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.ui.components.DateField
import com.shopmanager.app.ui.components.MessageEffect
import com.shopmanager.app.util.DAY_MS
import com.shopmanager.app.util.money
import com.shopmanager.app.util.round2
import com.shopmanager.app.util.startOfDay
import com.shopmanager.app.util.toAmountOrNull
import kotlinx.coroutines.launch

@Composable
fun AddPurchaseScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.shopApp.repository
    val scope = rememberCoroutineScope()
    val today = remember { startOfDay(System.currentTimeMillis()) }

    var supplier by rememberSaveable { mutableStateOf("") }
    var billNo by rememberSaveable { mutableStateOf("") }
    var purchaseDate by rememberSaveable { mutableStateOf(today) }
    var dueDate by rememberSaveable { mutableStateOf(today + 30 * DAY_MS) }
    var totalText by rememberSaveable { mutableStateOf("") }
    var paidText by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PaymentMode.CASH) }
    var notes by rememberSaveable { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar) { message = null }

    val total = totalText.toAmountOrNull()
    val paid = if (paidText.isBlank()) 0.0 else paidText.toAmountOrNull()

    val supplierError = if (supplier.trim().length < 2) "Enter supplier name" else null
    val totalError = if (total == null || total <= 0) "Enter bill amount" else null
    val paidError = when {
        paid == null || paid < 0 -> "Invalid amount"
        total != null && paid > total -> "Paid amount cannot be more than bill amount"
        else -> null
    }
    val purchaseDateError = if (purchaseDate > today) "Purchase date cannot be in the future" else null
    val dueDateError = if (dueDate < purchaseDate) "Due date must be on or after purchase date" else null

    AppScaffold(title = "Add purchase", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = supplier, onValueChange = { supplier = it.take(60) },
                label = { Text("Supplier name") }, singleLine = true,
                isError = showErrors && supplierError != null,
                supportingText = if (showErrors) supplierError?.let { { Text(it) } } else null,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = billNo, onValueChange = { billNo = it.take(30) },
                label = { Text("Supplier bill / invoice no. (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DateField("Purchase date", purchaseDate, { purchaseDate = it }, Modifier.fillMaxWidth(), purchaseDateError)
            DateField("Payment due date", dueDate, { dueDate = it }, Modifier.fillMaxWidth(), dueDateError)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 15, 30, 45, 60).forEach { d ->
                    AssistChip(onClick = { dueDate = purchaseDate + d * DAY_MS }, label = { Text("${d}d") })
                }
            }
            AmountField(
                value = totalText, onValueChange = { totalText = it }, label = "Bill amount",
                error = if (showErrors) totalError else null, modifier = Modifier.fillMaxWidth()
            )
            AmountField(
                value = paidText, onValueChange = { paidText = it }, label = "Paid now (optional)",
                error = paidError.takeIf { paidText.isNotBlank() },
                supporting = if (total != null && paid != null && paidError == null) "Pending after save: ${(total - paid).round2().money()}" else null,
                modifier = Modifier.fillMaxWidth()
            )
            if ((paid ?: 0.0) > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK).forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }
            }
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(200) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Tip: add the received items to Stock separately (Stock → Add stock).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    showErrors = true
                    val firstError = listOf(supplierError, totalError, paidError, purchaseDateError, dueDateError).firstOrNull { it != null }
                    if (firstError != null) {
                        message = firstError
                        return@Button
                    }
                    saving = true
                    scope.launch {
                        try {
                            repo.addPurchase(
                                Purchase(
                                    supplierName = supplier,
                                    billNo = billNo.trim().ifEmpty { null },
                                    purchaseDate = purchaseDate,
                                    dueDate = dueDate,
                                    totalAmount = total!!.round2(),
                                    notes = notes.trim().ifEmpty { null }
                                ),
                                paidNow = paid ?: 0.0,
                                mode = mode
                            )
                            onBack()
                        } catch (e: Exception) {
                            message = "Could not save: ${e.message}"
                            saving = false
                        }
                    }
                }
            ) { Text("Save purchase") }
        }
    }
}
