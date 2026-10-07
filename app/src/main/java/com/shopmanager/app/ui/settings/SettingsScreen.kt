package com.shopmanager.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shopmanager.app.shopApp
import com.shopmanager.app.ui.components.AppScaffold
import com.shopmanager.app.util.isValidUpiId

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = LocalContext.current.shopApp.settings
    var storeName by remember { mutableStateOf(settings.storeName) }
    var upiId by remember { mutableStateOf(settings.upiId) }
    val upiError = if (upiId.isNotBlank() && !isValidUpiId(upiId)) "Enter a valid UPI ID, e.g. shopname@okaxis" else null

    AppScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = storeName, onValueChange = { storeName = it.take(40) },
                label = { Text("Store name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = upiId, onValueChange = { upiId = it.trim().take(60) },
                label = { Text("Store UPI ID (for payment QR)") }, singleLine = true,
                isError = upiError != null,
                supportingText = { Text(upiError ?: "Customers scan the QR on the payment screen to pay this UPI ID") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = upiError == null,
                onClick = {
                    settings.storeName = storeName
                    settings.upiId = upiId
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
            Text(
                "All data is stored on this phone. Uninstalling the app deletes it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
