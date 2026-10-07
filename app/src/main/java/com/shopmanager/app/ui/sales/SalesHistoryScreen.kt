package com.shopmanager.app.ui.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.DaySummary
import com.shopmanager.app.data.PaymentMode
import com.shopmanager.app.data.Sale
import com.shopmanager.app.data.SaleLine
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.util.asDateTime
import com.shopmanager.app.util.money

@Composable
fun SalesHistoryScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.shopApp.repository
    val sales by remember { repo.observeRecentSales() }.collectAsState(initial = emptyList())
    val today by remember { repo.observeTodaySummary() }.collectAsState(initial = DaySummary(0, 0.0, 0.0, 0.0))
    var expanded by remember { mutableStateOf<Long?>(null) }

    AppScaffold(title = "Sales history", onBack = onBack) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Today", style = MaterialTheme.typography.titleMedium)
                        Text(today.total.money(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("${today.count} bills  •  UPI ${today.upi.money()}  •  Cash ${today.cash.money()}")
                    }
                }
            }
            if (sales.isEmpty()) item { Text("No sales yet.", Modifier.padding(16.dp)) }
            items(sales, key = { it.id }) { sale ->
                SaleCard(sale, expanded == sale.id) { expanded = if (expanded == sale.id) null else sale.id }
            }
        }
    }
}

@Composable
private fun SaleCard(sale: Sale, isExpanded: Boolean, onClick: () -> Unit) {
    val repo = LocalContext.current.shopApp.repository
    var lines by remember { mutableStateOf<List<SaleLine>>(emptyList()) }
    LaunchedEffect(isExpanded) { if (isExpanded && lines.isEmpty()) lines = repo.saleLines(sale.id) }

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(sale.billNo, fontWeight = FontWeight.SemiBold)
                    Text(sale.createdAt.asDateTime(), style = MaterialTheme.typography.bodySmall)
                    sale.customerName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(sale.total.money(), fontWeight = FontWeight.Bold)
                    Text(PaymentMode.of(sale.paymentMode).label, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isExpanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                lines.forEach { l ->
                    Row {
                        Text("${l.name} × ${l.quantity}", Modifier.weight(1f))
                        Text((l.unitPrice * l.quantity).money())
                    }
                }
                if (sale.discount > 0) Row { Text("Discount", Modifier.weight(1f)); Text("- " + sale.discount.money()) }
                if (sale.paymentMode == PaymentMode.SPLIT.name) {
                    Text("UPI ${sale.upiAmount.money()}  +  Cash ${sale.cashAmount.money()}", style = MaterialTheme.typography.bodySmall)
                }
                sale.upiRef?.let { Text("UPI ref: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
