package com.shopmanager.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.shopmanager.app.util.round2
import com.shopmanager.app.util.startOfDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Thrown when a rule is broken; the message is shown to the user as-is. */
class ValidationException(message: String) : Exception(message)

sealed interface StockResult {
    val item: Item

    data class Created(override val item: Item) : StockResult
    data class Increased(override val item: Item, val oldQty: Int) : StockResult
}

class ShopRepository(private val db: AppDatabase) {
    private val itemDao = db.itemDao()
    private val saleDao = db.saleDao()
    private val purchaseDao = db.purchaseDao()

    // ---------------- Items / stock ----------------

    fun observeItems() = itemDao.observeAll()
    fun observeItemCount() = itemDao.observeCount()
    fun observeLowStockCount(threshold: Int = LOW_STOCK) = itemDao.observeLowStockCount(threshold)

    suspend fun findByBarcode(code: String): Item? =
        code.trim().takeIf { it.isNotEmpty() }?.let { itemDao.findByBarcode(it) }

    suspend fun findByName(name: String): Item? =
        name.trim().takeIf { it.isNotEmpty() }?.let { itemDao.findByName(it) }

    /**
     * Adds stock. If the barcode (or, without barcode, the name) already exists the
     * quantity is added to that item; otherwise a new item is created.
     */
    suspend fun addStock(
        barcode: String?,
        name: String,
        category: Category,
        salePrice: Double,
        costPrice: Double,
        qty: Int
    ): StockResult = db.withTransaction {
        val code = barcode?.trim()?.ifEmpty { null }
        val cleanName = name.trim()
        val now = System.currentTimeMillis()

        val byCode = code?.let { itemDao.findByBarcode(it) }
        if (byCode != null) {
            val clash = itemDao.findByName(cleanName)
            if (clash != null && clash.id != byCode.id) {
                throw ValidationException("Name \"$cleanName\" is already used by another item")
            }
            val updated = byCode.copy(
                name = cleanName,
                category = category.name,
                salePrice = salePrice,
                costPrice = costPrice,
                quantity = byCode.quantity + qty,
                updatedAt = now
            )
            itemDao.update(updated)
            return@withTransaction StockResult.Increased(updated, byCode.quantity)
        }

        val byName = itemDao.findByName(cleanName)
        if (byName != null) {
            if (code != null && byName.barcode != null && byName.barcode != code) {
                throw ValidationException(
                    "\"${byName.name}\" already exists with barcode ${byName.barcode}. Use a different name."
                )
            }
            val updated = byName.copy(
                barcode = byName.barcode ?: code,
                category = category.name,
                salePrice = salePrice,
                costPrice = costPrice,
                quantity = byName.quantity + qty,
                updatedAt = now
            )
            itemDao.update(updated)
            return@withTransaction StockResult.Increased(updated, byName.quantity)
        }

        val item = Item(
            name = cleanName,
            barcode = code,
            category = category.name,
            salePrice = salePrice,
            costPrice = costPrice,
            quantity = qty,
            updatedAt = now
        )
        val id = itemDao.insert(item)
        StockResult.Created(item.copy(id = id))
    }

    suspend fun updateItem(item: Item) {
        val clean = item.copy(
            name = item.name.trim(),
            barcode = item.barcode?.trim()?.ifEmpty { null },
            updatedAt = System.currentTimeMillis()
        )
        val clash = itemDao.findByName(clean.name)
        if (clash != null && clash.id != clean.id) {
            throw ValidationException("Name \"${clean.name}\" is already used by another item")
        }
        try {
            itemDao.update(clean)
        } catch (e: SQLiteConstraintException) {
            throw ValidationException("Barcode ${clean.barcode} is already used by another item")
        }
    }

    suspend fun deleteItem(item: Item) = itemDao.delete(item)

    // ---------------- Sales ----------------

    fun observeRecentSales(limit: Int = 200) = saleDao.observeRecent(limit)
    fun observeTodaySummary() = saleDao.observeSummary(startOfDay(System.currentTimeMillis()))
    fun observeSummarySince(from: Long) = saleDao.observeSummary(from)
    suspend fun saleLines(saleId: Long) = saleDao.linesFor(saleId)

    /** Saves the bill and reduces stock in one transaction. Nothing is saved if any item is short. */
    suspend fun completeSale(
        lines: List<SaleRequestLine>,
        discount: Double,
        mode: PaymentMode,
        upiAmount: Double,
        cashAmount: Double,
        upiRef: String?,
        customerName: String?,
        customerPhone: String?
    ): Sale = db.withTransaction {
        if (lines.isEmpty()) throw ValidationException("Add at least one item")
        val now = System.currentTimeMillis()

        for (line in lines) {
            val id = line.itemId ?: continue
            if (itemDao.reduceStock(id, line.quantity, now) == 0) {
                val available = itemDao.getById(id)?.quantity ?: 0
                throw ValidationException("Not enough stock for ${line.name} (available: $available)")
            }
        }

        val subTotal = lines.sumOf { it.quantity * it.unitPrice }.round2()
        val todayCount = saleDao.countSince(startOfDay(now))
        val billNo = "INV-" + SimpleDateFormat("yyMMdd", Locale.US).format(Date(now)) +
            "-" + (todayCount + 1).toString().padStart(3, '0')

        val sale = Sale(
            billNo = billNo,
            createdAt = now,
            customerName = customerName?.trim()?.ifEmpty { null },
            customerPhone = customerPhone?.trim()?.ifEmpty { null },
            subTotal = subTotal,
            discount = discount.round2(),
            total = (subTotal - discount).round2(),
            paymentMode = mode.name,
            upiAmount = upiAmount.round2(),
            cashAmount = cashAmount.round2(),
            upiRef = upiRef?.trim()?.ifEmpty { null }
        )
        val saleId = saleDao.insertSale(sale)
        saleDao.insertLines(lines.map {
            SaleLine(saleId = saleId, itemId = it.itemId, name = it.name, quantity = it.quantity, unitPrice = it.unitPrice)
        })
        sale.copy(id = saleId)
    }

    // ---------------- Purchases / payables ----------------

    fun observePurchases() = purchaseDao.observeWithPaid()
    fun observePayments(purchaseId: Long) = purchaseDao.observePayments(purchaseId)

    suspend fun addPurchase(purchase: Purchase, paidNow: Double, mode: PaymentMode) = db.withTransaction {
        val id = purchaseDao.insert(purchase.copy(supplierName = purchase.supplierName.trim()))
        if (paidNow > 0) {
            purchaseDao.insertPayment(
                PurchasePayment(purchaseId = id, paidOn = purchase.purchaseDate, amount = paidNow.round2(), mode = mode.name, note = "Paid at purchase")
            )
        }
        id
    }

    suspend fun addPayment(purchaseId: Long, amount: Double, mode: PaymentMode, paidOn: Long, note: String?) =
        db.withTransaction {
            val purchase = purchaseDao.getById(purchaseId) ?: throw ValidationException("Purchase not found")
            val pending = (purchase.totalAmount - purchaseDao.paidFor(purchaseId)).round2()
            if (amount > pending + 0.001) {
                throw ValidationException("Amount is more than pending balance")
            }
            purchaseDao.insertPayment(
                PurchasePayment(purchaseId = purchaseId, paidOn = paidOn, amount = amount.round2(), mode = mode.name, note = note?.trim()?.ifEmpty { null })
            )
        }

    suspend fun deletePurchase(purchase: Purchase) = purchaseDao.delete(purchase)

    // ---------------- Reports ----------------

    suspend fun salesBetween(from: Long, to: Long) = saleDao.salesBetween(from, to)
    suspend fun itemSalesBetween(from: Long, to: Long) = saleDao.itemSalesBetween(from, to)
    suspend fun allItems() = itemDao.getAll()
    suspend fun allPurchasesWithPaid() = purchaseDao.getAllWithPaid()
    suspend fun supplierPaymentsBetween(from: Long, to: Long) = purchaseDao.paymentsBetween(from, to)
    suspend fun purchaseById(id: Long) = purchaseDao.getById(id)

    companion object {
        const val LOW_STOCK = 2
    }
}
