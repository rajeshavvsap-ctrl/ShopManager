package com.shopmanager.app.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shopmanager.app.data.Category
import com.shopmanager.app.data.Item
import com.shopmanager.app.data.ShopRepository
import com.shopmanager.app.data.StockResult
import com.shopmanager.app.data.ValidationException
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AmountField
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.ui.components.MessageEffect
import com.shopmanager.app.ui.components.onFocusLost
import com.shopmanager.app.ui.theme.Warning
import com.shopmanager.app.util.filterDigits
import com.shopmanager.app.util.money
import com.shopmanager.app.util.scanBarcode
import com.shopmanager.app.util.toAmountOrNull
import kotlinx.coroutines.launch

class AddStockViewModel(private val repo: ShopRepository) : ViewModel() {
    var barcode by mutableStateOf("")
    var name by mutableStateOf("")
    var category by mutableStateOf(Category.CLOTH)
    var salePrice by mutableStateOf("")
    var costPrice by mutableStateOf("")
    var qty by mutableStateOf("1")

    /** Existing item matched by barcode or name – stock will be added to it. */
    var existing by mutableStateOf<Item?>(null)
        private set
    var showErrors by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set

    val nameError get() = if (name.trim().length < 2) "Enter item name" else null
    val qtyError
        get() = qty.toIntOrNull().let {
            when {
                it == null || it <= 0 -> "Enter quantity (1 or more)"
                it > 100000 -> "Quantity is too large"
                else -> null
            }
        }
    val salePriceError get() = salePrice.toAmountOrNull().let { if (it == null || it <= 0) "Enter selling price" else null }
    val costPriceError get() = if (costPrice.isNotBlank() && costPrice.toAmountOrNull() == null) "Invalid amount" else null
    val priceWarning: String?
        get() {
            val s = salePrice.toAmountOrNull() ?: return null
            val c = costPrice.toAmountOrNull() ?: return null
            return if (c > s) "Selling price is lower than cost price" else null
        }

    /** Look up the barcode (scanned or typed) and prefill the form if the item exists. */
    private var lastLookup = ""

    fun lookupBarcode(code: String) {
        barcode = code.trim()
        if (barcode == lastLookup) return
        lastLookup = barcode
        if (barcode.isEmpty()) {
            existing = null
            return
        }
        viewModelScope.launch {
            val item = repo.findByBarcode(barcode)
            existing = item
            if (item != null) prefill(item) else message = "New barcode – enter item details"
        }
    }

    /** When typing a name with no barcode, check if it is already in stock. */
    fun lookupName() {
        if (barcode.isNotBlank() || name.isBlank()) return
        viewModelScope.launch {
            val item = repo.findByName(name)
            existing = item
            if (item != null) prefill(item)
        }
    }

    private fun prefill(item: Item) {
        name = item.name
        category = Category.of(item.category)
        salePrice = item.salePrice.toPlain()
        costPrice = if (item.costPrice > 0) item.costPrice.toPlain() else ""
        qty = "1"
    }

    fun onNameChange(value: String) {
        name = value.take(60)
        if (barcode.isBlank() && existing != null && !existing!!.name.equals(name.trim(), ignoreCase = true)) existing = null
    }

    fun save(keepAdding: Boolean, onDone: () -> Unit) {
        showErrors = true
        if (listOf(nameError, qtyError, salePriceError, costPriceError).any { it != null } || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val result = repo.addStock(
                    barcode = barcode,
                    name = name,
                    category = category,
                    salePrice = salePrice.toAmountOrNull()!!,
                    costPrice = costPrice.toAmountOrNull() ?: 0.0,
                    qty = qty.toInt()
                )
                message = when (result) {
                    is StockResult.Created -> "Added ${result.item.name} (qty ${result.item.quantity})"
                    is StockResult.Increased -> "${result.item.name}: stock ${result.oldQty} → ${result.item.quantity}"
                }
                if (keepAdding) reset() else onDone()
            } catch (e: ValidationException) {
                message = e.message
            } catch (e: Exception) {
                message = "Could not save: ${e.message}"
            } finally {
                saving = false
            }
        }
    }

    private fun reset() {
        barcode = ""; name = ""; salePrice = ""; costPrice = ""; qty = "1"; lastLookup = ""
        existing = null; showErrors = false
    }
}

private fun Double.toPlain(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

@Composable
fun AddStockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.shopApp.repository
    val vm: AddStockViewModel = viewModel { AddStockViewModel(repo) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm.message, snackbar) { vm.message = null }
    val err = vm.showErrors

    AppScaffold(title = "Add stock", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = vm.barcode,
                onValueChange = { vm.barcode = it.trim().take(40) },
                label = { Text("Barcode (scan or type, optional)") },
                trailingIcon = {
                    IconButton(onClick = {
                        scanBarcode(context, onResult = vm::lookupBarcode, onError = { vm.message = it })
                    }) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.lookupBarcode(vm.barcode) }),
                supportingText = { Text("Press search on keyboard to look up a typed barcode") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusLost { vm.lookupBarcode(vm.barcode) }
            )

            vm.existing?.let { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Existing item found", fontWeight = FontWeight.Bold)
                        Text("${item.name}  •  current stock ${item.quantity}  •  ${item.salePrice.money()}")
                        Text("Quantity entered below will be ADDED to current stock.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = vm.name,
                onValueChange = vm::onNameChange,
                label = { Text("Item name") },
                singleLine = true,
                isError = err && vm.nameError != null,
                supportingText = if (err) vm.nameError?.let { { Text(it) } } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusLost { vm.lookupName() }
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(selected = vm.category == c, onClick = { vm.category = c }, label = { Text(c.label) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(
                    value = vm.salePrice, onValueChange = { vm.salePrice = it }, label = "Selling price",
                    error = if (err) vm.salePriceError else null, modifier = Modifier.weight(1f)
                )
                AmountField(
                    value = vm.costPrice, onValueChange = { vm.costPrice = it }, label = "Cost price",
                    error = if (err) vm.costPriceError else null, modifier = Modifier.weight(1f)
                )
            }
            vm.priceWarning?.let { Text(it, color = Warning, style = MaterialTheme.typography.bodySmall) }

            OutlinedTextField(
                value = vm.qty,
                onValueChange = { vm.qty = it.filterDigits(6) },
                label = { Text(if (vm.existing != null) "Quantity to add" else "Quantity") },
                singleLine = true,
                isError = err && vm.qtyError != null,
                supportingText = if (err) vm.qtyError?.let { { Text(it) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.save(keepAdding = true, onDone = onBack) }, enabled = !vm.saving, modifier = Modifier.weight(1f)) {
                    Text("Save & add next")
                }
                Button(onClick = { vm.save(keepAdding = false, onDone = onBack) }, enabled = !vm.saving, modifier = Modifier.weight(1f)) {
                    Text("Save")
                }
            }
        }
    }
}
