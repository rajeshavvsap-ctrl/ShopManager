package com.shopmanager.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Category(val label: String) {
    CLOTH("Cloth"),
    JEWELLERY("Antique Jewellery");

    companion object {
        fun of(name: String): Category = entries.firstOrNull { it.name == name } ?: CLOTH
    }
}

enum class PaymentMode(val label: String) {
    UPI("UPI"),
    CASH("Cash"),
    SPLIT("UPI + Cash"),
    BANK("Bank transfer");

    companion object {
        fun of(name: String): PaymentMode = entries.firstOrNull { it.name == name } ?: CASH
    }
}

/** A stock item. Barcode is optional but unique when present (SQLite allows many NULLs). */
@Entity(
    tableName = "items",
    indices = [Index(value = ["barcode"], unique = true), Index(value = ["name"])]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val category: String = Category.CLOTH.name,
    val salePrice: Double,
    val costPrice: Double = 0.0,
    val quantity: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billNo: String,
    val createdAt: Long = System.currentTimeMillis(),
    val customerName: String? = null,
    val customerPhone: String? = null,
    val subTotal: Double,
    val discount: Double,
    val total: Double,
    val paymentMode: String,
    val upiAmount: Double,
    val cashAmount: Double,
    val upiRef: String? = null
)

@Entity(
    tableName = "sale_lines",
    foreignKeys = [ForeignKey(
        entity = Sale::class,
        parentColumns = ["id"],
        childColumns = ["saleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["saleId"])]
)
data class SaleLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    /** null = manually typed item that is not in stock */
    val itemId: Long?,
    val name: String,
    val quantity: Int,
    val unitPrice: Double
)

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierName: String,
    val billNo: String? = null,
    val purchaseDate: Long,
    val dueDate: Long,
    val totalAmount: Double,
    val notes: String? = null
)

@Entity(
    tableName = "purchase_payments",
    foreignKeys = [ForeignKey(
        entity = Purchase::class,
        parentColumns = ["id"],
        childColumns = ["purchaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["purchaseId"])]
)
data class PurchasePayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val paidOn: Long,
    val amount: Double,
    val mode: String,
    val note: String? = null
)

data class PurchaseWithPaid(
    @Embedded val purchase: Purchase,
    val paid: Double
) {
    val pending: Double get() = (purchase.totalAmount - paid).coerceAtLeast(0.0)
    val isPaid: Boolean get() = pending < 0.01
}

data class DaySummary(
    val count: Int,
    val total: Double,
    val upi: Double,
    val cash: Double
)

/** One line of a sale being saved. itemId == null for manual items. */
data class SaleRequestLine(
    val itemId: Long?,
    val name: String,
    val quantity: Int,
    val unitPrice: Double
)
