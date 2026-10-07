package com.shopmanager.app.ui.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.PaymentMode
import com.shopmanager.app.data.PurchaseWithPaid
import com.shopmanager.app.data.ValidationException
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AmountField
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.ui.components.ConfirmDialog
import com.shopmanager.app.ui.components.DateField
import com.shopmanager.app.ui.components.MessageEffect
import com.shopmanager.app.ui.theme.Danger
import com.shopmanager.app.ui.theme.Success
import com.shopmanager.app.ui.theme.Warning
import com.shopmanager.app.util.DAY_MS
import com.shopmanager.app.util.asDate
import com.shopmanager.app.util.money
import com.shopmanager.app.util.startOfDay
import com.shopmanager.app.util.toAmountOrNull
import kotlinx.coroutines.launch

enum class DueStatus(val label: String, val color: Color) {
    OVERDUE("Overdue", Danger),
    DUE_SOON("Due soon", Warning),
    UPCOMING("Upcoming", Color(0xFF1F5A7A)),
    PAID("Paid", Success)
}

fun PurchaseWithPaid.status(today: Long): DueStatus = when {
    isPaid -> DueStatus.PAID
    purchase.dueDate < today -> DueStatus.OVERDUE
    purchase.dueDate <= today + 7 * DAY_MS -> DueStatus.DUE_SOON
    else -> DueStatus.UPCOMING
}

private enum class PFilter(val label: String) { PENDING("Pending"), OVERDUE("Overdue"), PAID("Paid"), ALL("All") }

@Composable
fun PurchaseScreen(onBack: () -> Unit, onAdd: () -> Unit) {
    val repo = LocalContext.current.shopApp.repository
    val all by remember { repo.observePurchases() }.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf(PFilter.PENDING) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar) { message = null }
    val today = remember { startOfDay(System.currentTimeMillis()) }

    val pendingTotal = all.sumOf { it.pending }
    val overdueTotal = all.filter { it.status(today) == DueStatus.OVERDUE }.sumOf { it.pending }
    val dueSoonTotal = all.filter { it.status(today) == DueStatus.DUE_SOON }.sumOf { it.pending }

    val shown = all.filter {
        when (filter) {
            PFilter.PENDING -> !it.isPaid
            PFilter.OVERDUE -> it.status(today) == DueStatus.OVERDUE
            PFilter.PAID -> it.isPaid
            PFilter.ALL -> true
        }
    }

    AppScaffold(
        title = "Purchase payments",
        onBack = onBack,
        snackbar = snackbar,
        fab = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add purchase") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Total pending to suppliers", style = MaterialTheme.typography.titleSmall)
                        Text(pendingTotal.money(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Overdue", style = MaterialTheme.typography.bodySmall)
                                Text(overdueTotal.money(), color = Danger, fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Due in 7 days", style = MaterialTheme.typography.bodySmall)
                                Text(dueSoonTotal.money(), color = Warning, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PFilter.entries) { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (all.isEmpty()) "No purchases yet. Tap \"Add purchase\" to record a supplier bill." else "Nothing here.",
                        Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(shown, key = { it.purchase.id }) { p ->
                PurchaseCard(p, p.status(today), today) { selectedId = p.purchase.id }
            }
        }
    }

    // Keep the details dialog in sync with live data (paid amount updates after a payment)
    selectedId?.let { id ->
        all.firstOrNull { it.purchase.id == id }?.let { p ->
            PurchaseDetailsDialog(p, today, onDismiss = { selectedId = null }, onMessage = { message = it })
        }
    }
}

@Composable
private fun PurchaseCard(p: PurchaseWithPaid, status: DueStatus, today: Long, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.purchase.supplierName, fontWeight = FontWeight.SemiBold)
                    Text(
                        (p.purchase.billNo?.let { "Bill $it  •  " } ?: "") + "Bought ${p.purchase.purchaseDate.asDate()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                StatusBadge(status)
            }
            Row(Modifier.padding(top = 8.dp)) {
                Amount("Total", p.purchase.totalAmount.money(), Modifier.weight(1f))
                Amount("Paid", p.paid.money(), Modifier.weight(1f))
                Amount("Pending", p.pending.money(), Modifier.weight(1f), if (p.isPaid) null else status.color)
            }
            if (!p.isPaid) {
                val days = ((p.purchase.dueDate - today) / DAY_MS).toInt()
                Text(
                    when {
                        days < 0 -> "Due ${p.purchase.dueDate.asDate()} • ${-days} days overdue"
                        days == 0 -> "Due today"
                        else -> "Pay by ${p.purchase.dueDate.asDate()} • in $days days"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = status.color,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun Amount(label: String, value: String, modifier: Modifier, color: Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusBadge(status: DueStatus) {
    Box(
        Modifier
            .background(status.color, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(status.label, color = Color.White, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun PurchaseDetailsDialog(p: PurchaseWithPaid, today: Long, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val repo = LocalContext.current.shopApp.repository
    val scope = rememberCoroutineScope()
    val payments by remember(p.purchase.id) { repo.observePayments(p.purchase.id) }.collectAsState(initial = emptyList())
    var paying by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.purchase.supplierName) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(p.status(today))
                p.purchase.billNo?.let { Text("Bill no: $it") }
                Text("Purchase date: ${p.purchase.purchaseDate.asDate()}")
                Text("Due date: ${p.purchase.dueDate.asDate()}")
                Text("Total: ${p.purchase.totalAmount.money()}")
                Text("Paid: ${p.paid.money()}")
                Text("Pending: ${p.pending.money()}", fontWeight = FontWeight.Bold)
                p.purchase.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Payments", style = MaterialTheme.typography.titleSmall)
                if (payments.isEmpty()) Text("No payments yet", style = MaterialTheme.typography.bodySmall)
                payments.forEach { pay ->
                    Row {
                        Text("${pay.paidOn.asDate()} • ${PaymentMode.of(pay.mode).label}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(pay.amount.money(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete purchase", color = Danger) }
            }
        },
        confirmButton = {
            if (!p.isPaid) Button(onClick = { paying = true }) { Text("Add payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (paying) {
        AddPaymentDialog(
            pending = p.pending,
            minDate = p.purchase.purchaseDate,
            onDismiss = { paying = false },
            onSave = { amount, mode, date, note ->
                scope.launch {
                    try {
                        repo.addPayment(p.purchase.id, amount, mode, date, note)
                        onMessage("Payment of ${amount.money()} saved")
                        paying = false
                    } catch (e: ValidationException) {
                        onMessage(e.message ?: "Could not save")
                    }
                }
            }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete purchase?",
            text = "This purchase and all its payments will be deleted.",
            onConfirm = {
                scope.launch {
                    repo.deletePurchase(p.purchase)
                    onMessage("Purchase deleted")
                    onDismiss()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun AddPaymentDialog(
    pending: Double,
    minDate: Long,
    onDismiss: () -> Unit,
    onSave: (Double, PaymentMode, Long, String?) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var date by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toAmountOrNull()
    val amountError = when {
        amountText.isBlank() -> null
        amount == null || amount <= 0 -> "Enter amount above zero"
        amount > pending + 0.001 -> "Cannot pay more than pending ${pending.money()}"
        else -> null
    }
    val dateError = when {
        date < startOfDay(minDate) -> "Payment date is before purchase date"
        date > startOfDay(System.currentTimeMillis()) -> "Payment date cannot be in the future"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add payment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pending: ${pending.money()}", fontWeight = FontWeight.SemiBold)
                AmountField(value = amountText, onValueChange = { amountText = it }, label = "Amount", error = amountError)
                TextButton(onClick = { amountText = String.format(java.util.Locale.US, "%.2f", pending) }) { Text("Pay full pending") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.BANK).forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }
                DateField(label = "Paid on", millis = date, onChange = { date = it }, error = dateError)
                OutlinedTextField(value = note, onValueChange = { note = it.take(80) }, label = { Text("Note (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = amount != null && amountError == null && dateError == null,
                onClick = { onSave(amount!!, mode, date, note) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
