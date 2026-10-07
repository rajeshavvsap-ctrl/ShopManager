package com.shopmanager.app.ui.reports

import android.content.ActivityNotFoundException
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shopmanager.app.reports.ExportFormat
import com.shopmanager.app.reports.Period
import com.shopmanager.app.reports.PeriodType
import com.shopmanager.app.reports.Report
import com.shopmanager.app.reports.ReportBuilder
import com.shopmanager.app.reports.ReportExporter
import com.shopmanager.app.reports.ReportType
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.ui.components.DateField
import com.shopmanager.app.util.DAY_MS
import com.shopmanager.app.util.startOfDay
import com.shopmanager.app.util.toAmountOrNull
import com.shopmanager.app.util.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.shopApp
    val builder = remember { ReportBuilder(app.repository) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val today = remember { startOfDay(System.currentTimeMillis()) }
    var periodType by rememberSaveable { mutableStateOf(PeriodType.TODAY) }
    var customFrom by rememberSaveable { mutableStateOf(today - 6 * DAY_MS) }
    var customTo by rememberSaveable { mutableStateOf(today) }
    val period = Period.of(periodType, customFrom, customTo)
    val customError = if (periodType == PeriodType.CUSTOM && customFrom > customTo) "From date is after To date" else null

    var overview by remember { mutableStateOf<Report?>(null) }
    var preview by remember { mutableStateOf<Report?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Quick numbers for the selected period
    LaunchedEffect(period) {
        overview = withContext(Dispatchers.IO) { builder.build(ReportType.SALES_SUMMARY, period) }
    }

    fun withReport(type: ReportType, action: suspend (Report) -> Unit) {
        if (busy) return
        if (customError != null) {
            scope.launch { snackbar.showSnackbar(customError) }
            return
        }
        busy = true
        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) { builder.build(type, period) }
                action(report)
            } catch (e: Exception) {
                snackbar.showSnackbar("Could not create report: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    fun download(type: ReportType, format: ExportFormat) = withReport(type) { report ->
        val name = "${report.fileBase}.${format.ext}"
        val saved = withContext(Dispatchers.IO) {
            ReportExporter.saveToDownloads(context, name, format, ReportExporter.bytes(report, format, app.settings.storeName))
        }
        val result = snackbar.showSnackbar(
            message = "Saved to ${saved.location}",
            actionLabel = "Open",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            try {
                context.startActivity(ReportExporter.openIntent(saved))
            } catch (e: ActivityNotFoundException) {
                snackbar.showSnackbar("No app found to open ${format.label} files")
            }
        }
    }

    fun share(type: ReportType, format: ExportFormat) = withReport(type) { report ->
        val name = "${report.fileBase}.${format.ext}"
        val intent = withContext(Dispatchers.IO) {
            ReportExporter.shareIntent(
                context, name, format, ReportExporter.bytes(report, format, app.settings.storeName),
                "${report.title} – ${report.period}"
            )
        }
        context.startActivity(intent)
    }

    AppScaffold(title = "Reports", onBack = onBack, snackbar = snackbar) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PeriodType.entries) { t ->
                        FilterChip(selected = periodType == t, onClick = { periodType = t }, label = { Text(t.label) })
                    }
                }
            }
            if (periodType == PeriodType.CUSTOM) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField("From", customFrom, { customFrom = it }, Modifier.weight(1f), customError)
                        DateField("To", customTo, { customTo = it }, Modifier.weight(1f))
                    }
                }
            }
            item { OverviewCard(period.label, overview) }
            items(ReportType.entries) { type ->
                ReportCard(
                    type = type,
                    busy = busy,
                    onView = { withReport(type) { preview = it } },
                    onPdf = { download(type, ExportFormat.PDF) },
                    onExcel = { download(type, ExportFormat.EXCEL) },
                    onShare = { share(type, ExportFormat.PDF) }
                )
            }
            item {
                Text(
                    "Downloads are saved in the phone's Downloads/ShopManager folder. Excel files are CSV and open in Excel or Google Sheets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    preview?.let { ReportPreviewDialog(it) { preview = null } }
}

@Composable
private fun OverviewCard(label: String, report: Report?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            val s = report?.summary?.toMap().orEmpty()
            Text(
                (s["Total sales"]?.toAmountOrNull() ?: 0.0).money(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text("${s["Bills"] ?: "0"} bills  •  ${s["Pieces sold"] ?: "0"} pieces sold")
            Text(
                "UPI ${(s["UPI"]?.toAmountOrNull() ?: 0.0).money()}  •  Cash ${(s["Cash"]?.toAmountOrNull() ?: 0.0).money()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReportCard(
    type: ReportType,
    busy: Boolean,
    onView: () -> Unit,
    onPdf: () -> Unit,
    onExcel: () -> Unit,
    onShare: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(type.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                type.description + if (!type.usesPeriod) " (period not used)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = onView, enabled = !busy) { IconLabel(Icons.Default.Visibility, "View") }
                OutlinedButton(onClick = onPdf, enabled = !busy) { IconLabel(Icons.Default.Download, "PDF") }
                OutlinedButton(onClick = onExcel, enabled = !busy) { IconLabel(Icons.Default.TableChart, "Excel") }
                OutlinedButton(onClick = onShare, enabled = !busy) { IconLabel(Icons.Default.Share, "Share") }
            }
        }
    }
}

@Composable
private fun IconLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Icon(icon, contentDescription = null)
    Text(" $label")
}

@Composable
private fun ReportPreviewDialog(report: Report, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(report.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(report.period, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                report.summary.forEach { (k, v) ->
                    Row {
                        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(v, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (report.rows.isEmpty()) {
                    Text("No data for this period.")
                } else {
                    // Scrollable table
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        TableRow(report, report.columns.map { it.title }, bold = true)
                        report.rows.take(300).forEach { TableRow(report, it) }
                        report.totals?.let { TableRow(report, it, bold = true) }
                        if (report.rows.size > 300) {
                            Text("Showing first 300 rows. Download to see all ${report.rows.size}.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TableRow(report: Report, values: List<String>, bold: Boolean = false) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        values.forEachIndexed { i, v ->
            val col = report.columns.getOrNull(i)
            Text(
                v,
                modifier = Modifier.width(((col?.weight ?: 1f) * 44).dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (col?.alignEnd == true) TextAlign.End else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
