package com.shopmanager.app.ui.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shopmanager.app.data.Item
import com.shopmanager.app.data.PaymentMode
import com.shopmanager.app.data.Sale
import com.shopmanager.app.data.SaleRequestLine
import com.shopmanager.app.data.ShopRepository
import com.shopmanager.app.data.ValidationException
import com.shopmanager.app.util.round2
import com.shopmanager.app.util.toAmountOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CartLine(
    val key: String = UUID.randomUUID().toString(),
    /** null = manual item (not in stock, stock is not reduced) */
    val itemId: Long?,
    val name: String,
    val qty: Int,
    val unitPrice: Double,
    /** price from the stock master, to show when the user changed it */
    val listPrice: Double?,
    /** available stock when added; null for manual items */
    val stock: Int?
) {
    val amount: Double get() = (qty * unitPrice).round2()
}

class SalesViewModel(private val repo: ShopRepository) : ViewModel() {
    var query by mutableStateOf("")
    val cart = mutableStateListOf<CartLine>()
    var discountText by mutableStateOf("")
    var customerName by mutableStateOf("")
    var customerPhone by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var completedSale by mutableStateOf<Sale?>(null)
    var saving by mutableStateOf(false)
        private set

    val items: StateFlow<List<Item>> =
        repo.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subTotal: Double get() = cart.sumOf { it.amount }.round2()
    val discount: Double get() = discountText.toAmountOrNull() ?: 0.0
    val total: Double get() = (subTotal - discount).round2()

    val discountError: String?
        get() = when {
            discountText.isNotBlank() && discountText.toAmountOrNull() == null -> "Invalid amount"
            discount > subTotal -> "Discount cannot be more than sub total"
            else -> null
        }

    val phoneError: String?
        get() = if (customerPhone.isNotEmpty() && !Regex("^[6-9][0-9]{9}$").matches(customerPhone))
            "Enter a valid 10-digit mobile number" else null

    fun suggestions(all: List<Item>): List<Item> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return all.filter { it.name.contains(q, ignoreCase = true) || it.barcode == q }.take(8)
    }

    fun addItem(item: Item) {
        val latest = items.value.firstOrNull { it.id == item.id } ?: item
        val index = cart.indexOfFirst { it.itemId == latest.id }
        if (index >= 0) {
            changeQty(cart[index].key, +1)
        } else {
            if (latest.quantity <= 0) {
                message = "${latest.name} is out of stock"
                return
            }
            cart.add(
                CartLine(
                    itemId = latest.id, name = latest.name, qty = 1,
                    unitPrice = latest.salePrice, listPrice = latest.salePrice, stock = latest.quantity
                )
            )
        }
        query = ""
    }

    fun onBarcode(code: String) {
        viewModelScope.launch {
            val item = repo.findByBarcode(code)
            if (item == null) {
                query = code
                message = "No item with barcode $code. Add it in Stock or enter it manually."
            } else {
                addItem(item)
            }
        }
    }

    /** Returns an error message, or null when the manual item was added. */
    fun addManual(name: String, priceText: String, qtyText: String): String? {
        val price = priceText.toAmountOrNull()
        val qty = qtyText.toIntOrNull()
        return when {
            name.trim().length < 2 -> "Enter item name"
            price == null || price <= 0 -> "Enter a valid price"
            qty == null || qty <= 0 -> "Enter a valid quantity"
            qty > 999 -> "Quantity is too large"
            else -> {
                cart.add(CartLine(itemId = null, name = name.trim(), qty = qty, unitPrice = price.round2(), listPrice = null, stock = null))
                query = ""
                null
            }
        }
    }

    fun changeQty(key: String, delta: Int) {
        val i = cart.indexOfFirst { it.key == key }
        if (i < 0) return
        val line = cart[i]
        val newQty = line.qty + delta
        when {
            newQty <= 0 -> cart.removeAt(i)
            line.stock != null && newQty > line.stock -> message = "Only ${line.stock} in stock for ${line.name}"
            newQty > 999 -> message = "Quantity is too large"
            else -> cart[i] = line.copy(qty = newQty)
        }
    }

    fun updatePrice(key: String, price: Double) {
        val i = cart.indexOfFirst { it.key == key }
        if (i >= 0) cart[i] = cart[i].copy(unitPrice = price.round2())
    }

    fun remove(key: String) {
        cart.removeAll { it.key == key }
    }

    /** Checks before opening the payment dialog. */
    fun validateBeforePayment(): Boolean {
        val error = when {
            cart.isEmpty() -> "Add at least one item"
            cart.any { it.unitPrice <= 0 } -> "Every item needs a price above zero"
            discountError != null -> discountError
            total <= 0 -> "Total amount must be more than zero"
            phoneError != null -> phoneError
            else -> null
        }
        message = error
        return error == null
    }

    fun completeSale(mode: PaymentMode, upiAmount: Double, cashAmount: Double, upiRef: String?) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val sale = repo.completeSale(
                    lines = cart.map { SaleRequestLine(it.itemId, it.name, it.qty, it.unitPrice) },
                    discount = discount,
                    mode = mode,
                    upiAmount = upiAmount,
                    cashAmount = cashAmount,
                    upiRef = upiRef,
                    customerName = customerName,
                    customerPhone = customerPhone
                )
                completedSale = sale
                cart.clear()
                discountText = ""
                customerName = ""
                customerPhone = ""
                query = ""
            } catch (e: ValidationException) {
                message = e.message
            } catch (e: Exception) {
                message = "Could not save the sale: ${e.message}"
            } finally {
                saving = false
            }
        }
    }
}
