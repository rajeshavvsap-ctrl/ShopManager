package com.shopmanager.app.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.Category
import com.shopmanager.app.data.Item
import com.shopmanager.app.data.PaymentMode
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AmountField
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.ui.components.MessageEffect
import com.shopmanager.app.ui.theme.Danger
import com.shopmanager.app.ui.theme.Success
import com.shopmanager.app.util.filterDigits
import com.shopmanager.app.util.money
import com.shopmanager.app.util.scanBarcode
import com.shopmanager.app.util.toAmountOrNull

@Composable
fun SalesScreen(onBack: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val app = context.shopApp
    val vm: SalesViewModel = viewModel { SalesViewModel(app.repository) }
    val allItems by vm.items.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.message, snackbar) { vm.message = null }

    var showManual by remember { mutableStateOf(false) }
    var editLine by remember { mutableStateOf<CartLine?>(null) }
    var showPayment by remember { mutableStateOf(false) }

    AppScaffold(
        title = "Sales",
        onBack = onBack,
        snackbar = snackbar,
        actions = { IconButton(onClick = onHistory) { Icon(Icons.Default.History, contentDescription = "Sales history") } },
        bottomBar = {
            Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${vm.cart.sumOf { it.qty }} items", style = MaterialTheme.typography.bodySmall)
                        Text(vm.total.money(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { if (vm.validateBeforePayment()) showPayment = true },
                        enabled = vm.cart.isNotEmpty()
                    ) { Text("Payment") }
                }
            }
        }
    ) { padding ->
        val suggestions = vm.suggestions(allItems)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it.take(60) },
                    label = { Text("Search item name or barcode") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            scanBarcode(context, onResult = vm::onBarcode, onError = { vm.message = it })
                        }) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(suggestions, key = { "s" + it.id }) { item -> SuggestionRow(item) { vm.addItem(item) } }
            item {
                if (vm.query.isNotBlank() && suggestions.isEmpty()) {
                    Text("Item not found in stock.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showManual = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (vm.query.isNotBlank() && suggestions.isEmpty()) "Enter \"${vm.query.trim()}\" manually" else "Enter item manually")
                }
                HorizontalDivider()
            }

            if (vm.cart.isEmpty()) {
                item {
                    Text(
                        "Cart is empty. Search, scan or type an item to start a bill.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(vm.cart, key = { it.key }) { line ->
                CartRow(
                    line = line,
                    onMinus = { vm.changeQty(line.key, -1) },
                    onPlus = { vm.changeQty(line.key, +1) },
                    onEditPrice = { editLine = line },
                    onDelete = { vm.remove(line.key) }
                )
            }

            if (vm.cart.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("Sub total", Modifier.weight(1f))
                            Text(vm.subTotal.money())
                        }
                        AmountField(
                            value = vm.discountText, onValueChange = { vm.discountText = it },
                            label = "Discount (optional)", error = vm.discountError,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vm.customerName, onValueChange = { vm.customerName = it.take(40) },
                            label = { Text("Customer name (optional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vm.customerPhone, onValueChange = { vm.customerPhone = it.filterDigits(10) },
                            label = { Text("Customer mobile (optional)") }, singleLine = true,
                            isError = vm.phoneError != null,
                            supportingText = vm.phoneError?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showManual) {
        ManualItemDialog(
            initialName = vm.query.trim(),
            onDismiss = { showManual = false },
            onAdd = { name, price, qty ->
                val error = vm.addManual(name, price, qty)
                if (error == null) showManual = false
                error
            }
        )
    }

    editLine?.let { line ->
        EditPriceDialog(line = line, onDismiss = { editLine = null }) { price ->
            vm.updatePrice(line.key, price)
            editLine = null
        }
    }

    if (showPayment) {
        PaymentDialog(
            total = vm.total,
            upiId = app.settings.upiId,
            storeName = app.settings.storeName,
            saving = vm.saving,
            onDismiss = { showPayment = false },
            onConfirm = { mode, upi, cash, ref ->
                vm.completeSale(mode, upi, cash, ref)
            }
        )
    }
    // Close the payment dialog once the sale is saved and show the success dialog
    LaunchedEffect(vm.completedSale) { if (vm.completedSale != null) showPayment = false }
    vm.completedSale?.let { sale ->
        AlertDialog(
            onDismissRequest = { vm.completedSale = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(48.dp)) },
            title = { Text("Sale completed") },
            text = {
                Column {
                    Text("Bill: ${sale.billNo}")
                    Text("Amount: ${sale.total.money()}", fontWeight = FontWeight.Bold)
                    Text("Paid by: " + PaymentMode.of(sale.paymentMode).label)
                    Text("Stock has been updated.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { Button(onClick = { vm.completedSale = null }) { Text("New bill") } }
        )
    }
}

@Composable
private fun SuggestionRow(item: Item, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                Text(
                    Category.of(item.category).label + (item.barcode?.let { "  •  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.salePrice.money(), fontWeight = FontWeight.Bold)
                Text(
                    if (item.quantity > 0) "Stock: ${item.quantity}" else "Out of stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.quantity > 0) MaterialTheme.colorScheme.onSecondaryContainer else Danger
                )
            }
        }
    }
}

@Composable
private fun CartRow(line: CartLine, onMinus: () -> Unit, onPlus: () -> Unit, onEditPrice: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, fontWeight = FontWeight.SemiBold)
                    if (line.itemId == null) {
                        Text("Manual item (not in stock)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Danger) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onEditPrice),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(line.unitPrice.money())
                    if (line.listPrice != null && line.listPrice != line.unitPrice) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            line.listPrice.money(),
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Edit price", modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp))
                }
                IconButton(onClick = onMinus) { Icon(Icons.Default.Remove, contentDescription = "Less") }
                Text("${line.qty}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onPlus) { Icon(Icons.Default.Add, contentDescription = "More") }
                Text(
                    line.amount.money(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(96.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ManualItemDialog(initialName: String, onDismiss: () -> Unit, onAdd: (String, String, String) -> String?) {
    var name by remember { mutableStateOf(initialName) }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter item manually") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Item name") }, singleLine = true)
                AmountField(value = price, onValueChange = { price = it }, label = "Price")
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it.filterDigits(3) }, label = { Text("Quantity") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Manual items are billed but do not change stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { Button(onClick = { error = onAdd(name, price, qty) }) { Text("Add to bill") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditPriceDialog(line: CartLine, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(line.unitPrice.toString().removeSuffix(".0")) }
    val value = text.toAmountOrNull()
    val error = if (value == null || value <= 0) "Enter a price above zero" else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit price") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.name, fontWeight = FontWeight.SemiBold)
                line.listPrice?.let { Text("Stock price: ${it.money()}", style = MaterialTheme.typography.bodySmall) }
                AmountField(value = text, onValueChange = { text = it }, label = "Price per piece", error = if (text.isBlank()) null else error)
            }
        },
        confirmButton = { Button(enabled = error == null, onClick = { value?.let(onSave) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (line.listPrice != null) TextButton(onClick = { onSave(line.listPrice) }) { Text("Reset") }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
