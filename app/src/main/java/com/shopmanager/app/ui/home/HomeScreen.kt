package com.shopmanager.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shopmanager.app.Routes
import com.shopmanager.app.data.DaySummary
import com.shopmanager.app.data.ShopRepository
import com.shopmanager.app.reports.Period
import com.shopmanager.app.reports.PeriodType
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.ui.theme.Danger
import com.shopmanager.app.ui.theme.Gold
import com.shopmanager.app.ui.theme.Maroon
import com.shopmanager.app.util.money
import com.shopmanager.app.util.startOfDay

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val app = LocalContext.current.shopApp
    val repo = app.repository
    val summary by remember { repo.observeTodaySummary() }.collectAsState(initial = DaySummary(0, 0.0, 0.0, 0.0))
    val itemCount by remember { repo.observeItemCount() }.collectAsState(initial = 0)
    val lowStock by remember { repo.observeLowStockCount() }.collectAsState(initial = 0)
    val purchases by remember { repo.observePurchases() }.collectAsState(initial = emptyList())
    val today = remember { startOfDay(System.currentTimeMillis()) }
    val pending = purchases.sumOf { it.pending }
    val overdue = purchases.count { !it.isPaid && it.purchase.dueDate < today }
    val storeName = app.settings.storeName.ifBlank { "Shop Manager" }
    val monthStart = remember { Period.of(PeriodType.MONTH, 0, 0).from }
    val month by remember { repo.observeSummarySince(monthStart) }.collectAsState(initial = DaySummary(0, 0.0, 0.0, 0.0))

    AppScaffold(
        title = storeName,
        actions = {
            IconButton(onClick = { onOpen(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cloth & Antique Jewellery", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Tile(
                title = "Sales",
                icon = Icons.Default.PointOfSale,
                color = Maroon,
                line1 = "Today: ${summary.total.money()}  (${summary.count} bills)",
                line2 = "UPI ${summary.upi.money()}  •  Cash ${summary.cash.money()}",
                onClick = { onOpen(Routes.SALES) }
            )
            Tile(
                title = "Stock",
                icon = Icons.Default.Inventory2,
                color = Gold,
                line1 = "$itemCount items",
                line2 = if (lowStock > 0) "$lowStock items low on stock (≤ ${ShopRepository.LOW_STOCK})" else "Stock levels OK",
                line2Color = if (lowStock > 0) Danger else null,
                onClick = { onOpen(Routes.STOCK) }
            )
            Tile(
                title = "Purchase Payments",
                icon = Icons.Default.Payments,
                color = Color(0xFF1F5A7A),
                line1 = "Pending: ${pending.money()}",
                line2 = if (overdue > 0) "$overdue bills overdue" else "No overdue bills",
                line2Color = if (overdue > 0) Danger else null,
                onClick = { onOpen(Routes.PURCHASES) }
            )
            Tile(
                title = "Reports",
                icon = Icons.Default.Assessment,
                color = Color(0xFF2E5E3A),
                line1 = "This month: ${month.total.money()}  (${month.count} bills)",
                line2 = "Sales, stock & supplier statements – PDF / Excel",
                onClick = { onOpen(Routes.REPORTS) }
            )
        }
    }
}

@Composable
private fun Tile(
    title: String,
    icon: ImageVector,
    color: Color,
    line1: String,
    line2: String,
    onClick: () -> Unit,
    line2Color: Color? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(line1, color = Color.White)
                Text(
                    line2,
                    color = if (line2Color != null) Color(0xFFFFE0E0) else Color.White.copy(alpha = 0.85f),
                    fontWeight = if (line2Color != null) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
