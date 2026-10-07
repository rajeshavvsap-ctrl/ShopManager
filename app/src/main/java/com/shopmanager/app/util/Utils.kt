package com.shopmanager.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToLong

// ---------------- Formatting ----------------

private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

fun Double.money(): String = inr.format(this)
fun Double.round2(): Double = (this * 100).roundToLong() / 100.0

fun Long.asDate(): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(this))
fun Long.asDateTime(): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(this))

/** Parses a user-typed amount like "1,250.50". Returns null when not a valid number. */
fun String.toAmountOrNull(): Double? =
    trim().replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() }

/** Keeps only digits and one dot, max 2 decimals – for amount text fields. */
fun String.filterAmount(): String {
    val cleaned = filter { it.isDigit() || it == '.' }
    val dot = cleaned.indexOf('.')
    if (dot < 0) return cleaned.take(9)
    val whole = cleaned.substring(0, dot).take(9)
    val frac = cleaned.substring(dot + 1).replace(".", "").take(2)
    return "$whole.$frac"
}

fun String.filterDigits(max: Int): String = filter { it.isDigit() }.take(max)

// ---------------- Dates ----------------

fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

const val DAY_MS = 24L * 60 * 60 * 1000

/** Material date picker works in UTC midnight; convert to/from local midnight. */
fun utcPickerToLocal(utcMs: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

fun localToUtcPicker(localMs: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMs }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

// ---------------- Barcode scanner ----------------

/** Opens Google's barcode scanner (Play services). No camera permission is required. */
fun scanBarcode(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_ITF, Barcode.FORMAT_QR_CODE
        )
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val value = barcode.rawValue?.trim().orEmpty()
            if (value.isNotEmpty()) onResult(value) else onError("Could not read barcode")
        }
        .addOnFailureListener { onError("Scanner not available: ${it.message ?: "try again"}") }
}

// ---------------- UPI ----------------

private val UPI_ID_REGEX = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9]{1,63}$")
fun isValidUpiId(id: String) = UPI_ID_REGEX.matches(id.trim())

fun upiUri(upiId: String, payeeName: String, amount: Double, note: String): String =
    "upi://pay?pa=${Uri.encode(upiId.trim())}&pn=${Uri.encode(payeeName.ifBlank { "Store" })}" +
        "&am=${String.format(Locale.US, "%.2f", amount)}&cu=INR&tn=${Uri.encode(note)}"

fun qrBitmap(content: String, size: Int = 600): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

// ---------------- Settings ----------------

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val storeNameState = mutableStateOf(prefs.getString("storeName", "").orEmpty())
    private val upiIdState = mutableStateOf(prefs.getString("upiId", "").orEmpty())

    /** Backed by Compose state so screens update as soon as settings are saved. */
    var storeName: String
        get() = storeNameState.value
        set(v) {
            storeNameState.value = v.trim()
            prefs.edit().putString("storeName", v.trim()).apply()
        }

    var upiId: String
        get() = upiIdState.value
        set(v) {
            upiIdState.value = v.trim()
            prefs.edit().putString("upiId", v.trim()).apply()
        }
}
