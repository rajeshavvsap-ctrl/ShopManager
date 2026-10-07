package com.shopmanager.app.reports

import com.shopmanager.app.data.Category
import com.shopmanager.app.data.PaymentMode
import com.shopmanager.app.data.ShopRepository
import com.shopmanager.app.util.DAY_MS
import com.shopmanager.app.util.asDate
import com.shopmanager.app.util.startOfDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReportColumn(val title: String, val weight: Float, val alignEnd: Boolean = false)

/** A printable/exportable table. Numbers are plain ("1250.00") so they work as numbers in Excel. */
data class Report(
    val title: String,
    val period: String,
    val summary: List<Pair<String, String>>,
    val columns: List<ReportColumn>,
    val rows: List<List<String>>,
    val totals: List<String>? = null,
    val fileBase: String
)

enum class ReportType(val title: String, val description: String, val usesPeriod: Boolean) {
    SALES_SUMMARY("Sales summary", "Bills, quantity sold, UPI / cash – day-wise or month-wise", true),
    ITEM_SALES("Item-wise sales", "How many pieces of each item were sold", true),
    BILLS("Bill-wise sales", "Every bill with customer, payment mode and amount", true),
    STOCK("Stock statement", "Current stock remaining for every item, with value", false),
    PAYABLES("Supplier payables", "Purchases, paid and pending amounts per supplier bill", true),
    SUPPLIER_PAYMENTS("Supplier payments made", "Payments made to suppliers in the period", true)
}

enum class PeriodType(val label: String) { TODAY("Today"), MONTH("This month"), YEAR("This year"), CUSTOM("Custom") }

/** from inclusive, to exclusive (local time). */
data class Period(val from: Long, val to: Long, val label: String) {
    val days: Int get() = ((to - from + DAY_MS / 2) / DAY_MS).toInt()

    companion object {
        fun of(type: PeriodType, customFrom: Long, customTo: Long): Period {
            val now = System.currentTimeMillis()
            val today = startOfDay(now)
            val cal = Calendar.getInstance().apply { timeInMillis = today }
            return when (type) {
                PeriodType.TODAY -> Period(today, today + DAY_MS, "Today, ${today.asDate()}")
                PeriodType.MONTH -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val from = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    Period(from, cal.timeInMillis, SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(from)))
                }
                PeriodType.YEAR -> {
                    cal.set(Calendar.DAY_OF_YEAR, 1)
                    val from = cal.timeInMillis
                    cal.add(Calendar.YEAR, 1)
                    Period(from, cal.timeInMillis, "Year ${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(from))}")
                }
                PeriodType.CUSTOM -> {
                    val f = startOfDay(minOf(customFrom, customTo))
                    val t = startOfDay(maxOf(customFrom, customTo))
                    Period(f, t + DAY_MS, "${f.asDate()} to ${t.asDate()}")
                }
            }
        }
    }
}

private fun n(v: Double) = String.format(Locale.US, "%.2f", v)
private fun stamp(ms: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

class ReportBuilder(private val repo: ShopRepository) {

    suspend fun build(type: ReportType, p: Period): Report = when (type) {
        ReportType.SALES_SUMMARY -> salesSummary(p)
        ReportType.ITEM_SALES -> itemSales(p)
        ReportType.BILLS -> bills(p)
        ReportType.STOCK -> stock()
        ReportType.PAYABLES -> payables(p)
        ReportType.SUPPLIER_PAYMENTS -> supplierPayments(p)
    }

    private fun fileName(base: String, p: Period?) =
        if (p == null) "${base}_${stamp(System.currentTimeMillis())}" else "${base}_${stamp(p.from)}-${stamp(p.to - 1)}"

    private suspend fun salesSummary(p: Period): Report {
        val sales = repo.salesBetween(p.from, p.to)
        val byMonth = p.days > 62
        val keyFmt = SimpleDateFormat(if (byMonth) "MMM yyyy" else "dd MMM yyyy", Locale.getDefault())
        val groups = sales.groupBy { keyFmt.format(Date(it.sale.createdAt)) } // sales are sorted by time
        val rows = groups.map { (key, list) ->
            listOf(
                key,
                list.size.toString(),
                list.sumOf { it.qty }.toString(),
                n(list.sumOf { it.sale.discount }),
                n(list.sumOf { it.sale.upiAmount }),
                n(list.sumOf { it.sale.cashAmount }),
                n(list.sumOf { it.sale.total })
            )
        }
        val total = sales.sumOf { it.sale.total }
        val qty = sales.sumOf { it.qty }
        return Report(
            title = "Sales Summary",
            period = p.label,
            summary = listOf(
                "Total sales" to n(total),
                "Bills" to sales.size.toString(),
                "Pieces sold" to qty.toString(),
                "UPI" to n(sales.sumOf { it.sale.upiAmount }),
                "Cash" to n(sales.sumOf { it.sale.cashAmount }),
                "Discount given" to n(sales.sumOf { it.sale.discount }),
                "Average bill" to n(if (sales.isEmpty()) 0.0 else total / sales.size)
            ),
            columns = listOf(
                ReportColumn(if (byMonth) "Month" else "Date", 2.2f),
                ReportColumn("Bills", 1f, true),
                ReportColumn("Qty", 1f, true),
                ReportColumn("Discount", 1.5f, true),
                ReportColumn("UPI", 1.6f, true),
                ReportColumn("Cash", 1.6f, true),
                ReportColumn("Total", 1.8f, true)
            ),
            rows = rows,
            totals = listOf(
                "TOTAL", sales.size.toString(), qty.toString(), n(sales.sumOf { it.sale.discount }),
                n(sales.sumOf { it.sale.upiAmount }), n(sales.sumOf { it.sale.cashAmount }), n(total)
            ),
            fileBase = fileName("Sales_Summary", p)
        )
    }

    private suspend fun itemSales(p: Period): Report {
        val list = repo.itemSalesBetween(p.from, p.to)
        val stockByName = repo.allItems().associateBy { it.name.lowercase() }
        val rows = list.mapIndexed { i, it ->
            listOf(
                (i + 1).toString(),
                it.name,
                it.qty.toString(),
                n(it.amount),
                stockByName[it.name.lowercase()]?.quantity?.toString() ?: "-"
            )
        }
        return Report(
            title = "Item-wise Sales",
            period = p.label,
            summary = listOf(
                "Different items sold" to list.size.toString(),
                "Pieces sold" to list.sumOf { it.qty }.toString(),
                "Amount (before bill discount)" to n(list.sumOf { it.amount })
            ),
            columns = listOf(
                ReportColumn("#", 0.6f, true),
                ReportColumn("Item", 4f),
                ReportColumn("Qty sold", 1.3f, true),
                ReportColumn("Amount", 1.8f, true),
                ReportColumn("Stock left", 1.4f, true)
            ),
            rows = rows,
            totals = listOf("", "TOTAL", list.sumOf { it.qty }.toString(), n(list.sumOf { it.amount }), ""),
            fileBase = fileName("Item_Sales", p)
        )
    }

    private suspend fun bills(p: Period): Report {
        val sales = repo.salesBetween(p.from, p.to)
        val fmt = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
        val rows = sales.map {
            listOf(
                it.sale.billNo,
                fmt.format(Date(it.sale.createdAt)),
                it.sale.customerName ?: "",
                PaymentMode.of(it.sale.paymentMode).label,
                it.qty.toString(),
                n(it.sale.discount),
                n(it.sale.total)
            )
        }
        return Report(
            title = "Bill-wise Sales",
            period = p.label,
            summary = listOf("Bills" to sales.size.toString(), "Total sales" to n(sales.sumOf { it.sale.total })),
            columns = listOf(
                ReportColumn("Bill no", 2f),
                ReportColumn("Date & time", 2.1f),
                ReportColumn("Customer", 2f),
                ReportColumn("Paid by", 1.4f),
                ReportColumn("Qty", 0.8f, true),
                ReportColumn("Disc.", 1.1f, true),
                ReportColumn("Total", 1.5f, true)
            ),
            rows = rows,
            totals = listOf(
                "TOTAL", "", "", "", sales.sumOf { it.qty }.toString(),
                n(sales.sumOf { it.sale.discount }), n(sales.sumOf { it.sale.total })
            ),
            fileBase = fileName("Bills", p)
        )
    }

    private suspend fun stock(): Report {
        val items = repo.allItems()
        fun value(cost: Double, sale: Double, q: Int) = q * (if (cost > 0) cost else sale)
        val rows = items.map {
            listOf(
                it.name,
                Category.of(it.category).label,
                it.barcode ?: "",
                it.quantity.toString(),
                n(it.salePrice),
                n(it.costPrice),
                n(value(it.costPrice, it.salePrice, it.quantity))
            )
        }
        val totalQty = items.sumOf { it.quantity }
        val totalValue = items.sumOf { value(it.costPrice, it.salePrice, it.quantity) }
        val saleValue = items.sumOf { it.quantity * it.salePrice }
        return Report(
            title = "Stock Statement",
            period = "As on ${System.currentTimeMillis().asDate()}",
            summary = listOf(
                "Items" to items.size.toString(),
                "Pieces in stock" to totalQty.toString(),
                "Stock value (cost)" to n(totalValue),
                "Stock value (selling price)" to n(saleValue),
                "Low stock items (≤ ${ShopRepository.LOW_STOCK})" to items.count { it.quantity <= ShopRepository.LOW_STOCK }.toString(),
                "Cloth pieces" to items.filter { it.category == Category.CLOTH.name }.sumOf { it.quantity }.toString(),
                "Jewellery pieces" to items.filter { it.category == Category.JEWELLERY.name }.sumOf { it.quantity }.toString()
            ),
            columns = listOf(
                ReportColumn("Item", 3.2f),
                ReportColumn("Category", 1.8f),
                ReportColumn("Barcode", 2f),
                ReportColumn("Qty", 0.9f, true),
                ReportColumn("Sale price", 1.4f, true),
                ReportColumn("Cost", 1.3f, true),
                ReportColumn("Value", 1.6f, true)
            ),
            rows = rows,
            totals = listOf("TOTAL", "", "", totalQty.toString(), "", "", n(totalValue)),
            fileBase = fileName("Stock", null)
        )
    }

    private suspend fun payables(p: Period): Report {
        val today = startOfDay(System.currentTimeMillis())
        val list = repo.allPurchasesWithPaid()
            .filter { !it.isPaid || it.purchase.purchaseDate in p.from until p.to }
            .sortedWith(compareBy({ it.isPaid }, { it.purchase.dueDate }))
        val rows = list.map {
            val status = when {
                it.isPaid -> "Paid"
                it.purchase.dueDate < today -> "Overdue"
                else -> "Pending"
            }
            listOf(
                it.purchase.supplierName,
                it.purchase.billNo ?: "",
                it.purchase.purchaseDate.asDate(),
                it.purchase.dueDate.asDate(),
                n(it.purchase.totalAmount),
                n(it.paid),
                n(it.pending),
                status
            )
        }
        return Report(
            title = "Supplier Payables",
            period = "${p.label} (plus all unpaid bills)",
            summary = listOf(
                "Total pending" to n(list.sumOf { it.pending }),
                "Overdue" to n(list.filter { !it.isPaid && it.purchase.dueDate < today }.sumOf { it.pending }),
                "Bills listed" to list.size.toString()
            ),
            columns = listOf(
                ReportColumn("Supplier", 2.4f),
                ReportColumn("Bill no", 1.3f),
                ReportColumn("Bought", 1.6f),
                ReportColumn("Due", 1.6f),
                ReportColumn("Total", 1.5f, true),
                ReportColumn("Paid", 1.5f, true),
                ReportColumn("Pending", 1.5f, true),
                ReportColumn("Status", 1.2f)
            ),
            rows = rows,
            totals = listOf(
                "TOTAL", "", "", "", n(list.sumOf { it.purchase.totalAmount }),
                n(list.sumOf { it.paid }), n(list.sumOf { it.pending }), ""
            ),
            fileBase = fileName("Supplier_Payables", p)
        )
    }

    private suspend fun supplierPayments(p: Period): Report {
        val payments = repo.supplierPaymentsBetween(p.from, p.to)
        val purchases = repo.allPurchasesWithPaid().associateBy { it.purchase.id }
        val rows = payments.map {
            val pur = purchases[it.purchaseId]?.purchase
            listOf(
                it.paidOn.asDate(),
                pur?.supplierName ?: "",
                pur?.billNo ?: "",
                PaymentMode.of(it.mode).label,
                it.note ?: "",
                n(it.amount)
            )
        }
        return Report(
            title = "Supplier Payments Made",
            period = p.label,
            summary = listOf(
                "Payments" to payments.size.toString(),
                "Total paid" to n(payments.sumOf { it.amount })
            ),
            columns = listOf(
                ReportColumn("Date", 1.6f),
                ReportColumn("Supplier", 2.6f),
                ReportColumn("Bill no", 1.4f),
                ReportColumn("Mode", 1.4f),
                ReportColumn("Note", 2.2f),
                ReportColumn("Amount", 1.6f, true)
            ),
            rows = rows,
            totals = listOf("TOTAL", "", "", "", "", n(payments.sumOf { it.amount })),
            fileBase = fileName("Supplier_Payments", p)
        )
    }
}
