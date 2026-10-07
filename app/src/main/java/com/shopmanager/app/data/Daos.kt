package com.shopmanager.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): Item?

    @Query("SELECT * FROM items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Item?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): Item?

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    /** Returns 1 when stock was reduced, 0 when there was not enough stock. */
    @Query("UPDATE items SET quantity = quantity - :qty, updatedAt = :now WHERE id = :id AND quantity >= :qty")
    suspend fun reduceStock(id: Long, qty: Int, now: Long): Int

    @Query("SELECT COUNT(*) FROM items WHERE quantity <= :threshold")
    fun observeLowStockCount(threshold: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM items")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM items ORDER BY category, name COLLATE NOCASE")
    suspend fun getAll(): List<Item>
}

@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Insert
    suspend fun insertLines(lines: List<SaleLine>)

    @Query("SELECT * FROM sales ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Sale>>

    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId")
    suspend fun linesFor(saleId: Long): List<SaleLine>

    @Query("SELECT COUNT(*) FROM sales WHERE createdAt >= :from")
    suspend fun countSince(from: Long): Int

    @Query(
        """SELECT COUNT(*) AS count, IFNULL(SUM(total), 0) AS total,
           IFNULL(SUM(upiAmount), 0) AS upi, IFNULL(SUM(cashAmount), 0) AS cash
           FROM sales WHERE createdAt >= :from"""
    )
    fun observeSummary(from: Long): Flow<DaySummary>

    // ---------- Reports (from inclusive, to exclusive) ----------

    @Query(
        """SELECT s.*, IFNULL((SELECT SUM(l.quantity) FROM sale_lines l WHERE l.saleId = s.id), 0) AS qty
           FROM sales s WHERE s.createdAt >= :from AND s.createdAt < :to ORDER BY s.createdAt"""
    )
    suspend fun salesBetween(from: Long, to: Long): List<SaleWithQty>

    @Query(
        """SELECT l.name AS name, SUM(l.quantity) AS qty, SUM(l.quantity * l.unitPrice) AS amount
           FROM sale_lines l JOIN sales s ON s.id = l.saleId
           WHERE s.createdAt >= :from AND s.createdAt < :to
           GROUP BY l.itemId, l.name ORDER BY qty DESC, amount DESC"""
    )
    suspend fun itemSalesBetween(from: Long, to: Long): List<ItemSales>
}

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insert(purchase: Purchase): Long

    @Delete
    suspend fun delete(purchase: Purchase)

    @Insert
    suspend fun insertPayment(payment: PurchasePayment): Long

    @Query(
        """SELECT p.*, IFNULL((SELECT SUM(pp.amount) FROM purchase_payments pp WHERE pp.purchaseId = p.id), 0) AS paid
           FROM purchases p ORDER BY p.dueDate"""
    )
    fun observeWithPaid(): Flow<List<PurchaseWithPaid>>

    @Query(
        """SELECT p.*, IFNULL((SELECT SUM(pp.amount) FROM purchase_payments pp WHERE pp.purchaseId = p.id), 0) AS paid
           FROM purchases p ORDER BY p.dueDate"""
    )
    suspend fun getAllWithPaid(): List<PurchaseWithPaid>

    @Query("SELECT * FROM purchase_payments WHERE paidOn >= :from AND paidOn < :to ORDER BY paidOn")
    suspend fun paymentsBetween(from: Long, to: Long): List<PurchasePayment>

    @Query("SELECT IFNULL(SUM(amount), 0) FROM purchase_payments WHERE purchaseId = :purchaseId")
    suspend fun paidFor(purchaseId: Long): Double

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getById(id: Long): Purchase?

    @Query("SELECT * FROM purchase_payments WHERE purchaseId = :purchaseId ORDER BY paidOn DESC, id DESC")
    fun observePayments(purchaseId: Long): Flow<List<PurchasePayment>>
}
