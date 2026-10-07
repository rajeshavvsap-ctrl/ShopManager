package com.shopmanager.app.ui.stock

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.Category
import com.shopmanager.app.data.Item
import com.shopmanager.app.data.ShopRepository
import com.shopmanager.app.data.ValidationException
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AmountField
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.ui.components.ConfirmDialog
import com.shopmanager.app.ui.components.MessageEffect
import com.shopmanager.app.ui.theme.Danger
import com.shopmanager.app.ui.theme.Success
import com.shopmanager.app.util.filterDigits
import com.shopmanager.app.util.money
import com.shopmanager.app.util.round2
import com.shopmanager.app.util.scanBarcode
import com.shopmanager.app.util.toAmountOrNull
import kotlinx.coroutines.launch

private enum class StockFilter(val label: String) { ALL("All"), CLOTH("Cloth"), JEWELLERY("Jewellery"), LOW("Low stock") }

@Composable
fun StockScreen(onBack: () -> Unit, onAddStock: () -> Unit) {
    val context = LocalContext.current
    val repo = context.shopApp.repository
    val items by remember { repo.observeItems() }.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(StockFilter.ALL) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar) { message = null }

    val shown = items.filter { item ->
        val q = query.trim()
        (q.isEmpty() || item.name.contains(q, true) || item.barcode == q) &&
            when (filter) {
                StockFilter.ALL -> true
                StockFilter.CLOTH -> item.category == Category.CLOTH.name
                StockFilter.JEWELLERY -> item.category == Category.JEWELLERY.name
                StockFilter.LOW -> item.quantity <= ShopRepository.LOW_STOCK
            }
    }
    val totalQty = shown.sumOf { it.quantity }
    val stockValue = shown.sumOf { it.quantity * (if (it.costPrice > 0) it.costPrice else it.salePrice) }

    AppScaffold(
        title = "Stock",
        onBack = onBack,
        snackbar = snackbar,
        fab = {
            ExtendedFloatingActionButton(
                onClick = onAddStock,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add stock") }
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
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    label = { Text("Search name or barcode") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            scanBarcode(context, onResult = { code ->
                                query = code
                                if (items.none { it.barcode == code }) message = "Barcode $code is not in stock yet"
                            }, onError = { message = it })
                        }) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StockFilter.entries) { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            item {
                Text(
                    "${shown.size} items  •  $totalQty pcs  •  value ${stockValue.money()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (items.isEmpty()) "No stock yet. Tap \"Add stock\" to scan or enter items." else "No items match.",
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(shown, key = { it.id }) { item -> StockRow(item) { editing = item } }
        }
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editing = null },
            onMessage = { message = it }
        )
    }
}

@Composable
private fun StockRow(item: Item, onClick: () -> Unit) {
    val low = item.quantity <= ShopRepository.LOW_STOCK
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.SemiBold)
                Text(
                    Category.of(item.category).label + (item.barcode?.let { "  •  $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(item.salePrice.money(), style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                Modifier
                    .background(if (low) Danger else Success, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("${item.quantity}", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EditItemDialog(item: Item, onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val repo = LocalContext.current.shopApp.repository
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(item.name) }
    var barcode by remember { mutableStateOf(item.barcode.orEmpty()) }
    var category by remember { mutableStateOf(Category.of(item.category)) }
    var sale by remember { mutableStateOf(item.salePrice.toString().removeSuffix(".0")) }
    var cost by remember { mutableStateOf(if (item.costPrice > 0) item.costPrice.toString().removeSuffix(".0") else "") }
    var qty by remember { mutableStateOf(item.quantity.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit item") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Item name") }, singleLine = true)
                OutlinedTextField(value = barcode, onValueChange = { barcode = it.trim().take(40) }, label = { Text("Barcode") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                AmountField(value = sale, onValueChange = { sale = it }, label = "Selling price")
                AmountField(value = cost, onValueChange = { cost = it }, label = "Cost price")
                OutlinedTextField(
                    value = qty, onValueChange = { qty = it.filterDigits(6) }, label = { Text("Quantity in stock") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Use this to correct stock after a count") }
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = sale.toAmountOrNull()
                val c = if (cost.isBlank()) 0.0 else cost.toAmountOrNull()
                val q = qty.toIntOrNull()
                error = when {
                    name.trim().length < 2 -> "Enter item name"
                    s == null || s <= 0 -> "Enter selling price"
                    c == null || c < 0 -> "Invalid cost price"
                    q == null || q < 0 -> "Enter quantity (0 or more)"
                    else -> null
                }
                if (error == null) {
                    scope.launch {
                        try {
                            repo.updateItem(
                                item.copy(
                                    name = name, barcode = barcode, category = category.name,
                                    salePrice = s!!.round2(), costPrice = c!!.round2(), quantity = q!!
                                )
                            )
                            onMessage("Item updated")
                            onDismiss()
                        } catch (e: ValidationException) {
                            error = e.message
                        }
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Danger) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete item?",
            text = "\"${item.name}\" will be removed from stock. Past bills are not affected.",
            onConfirm = {
                scope.launch {
                    repo.deleteItem(item)
                    onMessage("Item deleted")
                    onDismiss()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
