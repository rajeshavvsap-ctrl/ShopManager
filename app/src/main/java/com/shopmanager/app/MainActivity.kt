package com.shopmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shopmanager.app.ui.home.HomeScreen
import com.shopmanager.app.ui.purchase.AddPurchaseScreen
import com.shopmanager.app.ui.purchase.PurchaseScreen
import com.shopmanager.app.ui.sales.SalesHistoryScreen
import com.shopmanager.app.ui.sales.SalesScreen
import com.shopmanager.app.ui.settings.SettingsScreen
import com.shopmanager.app.ui.stock.AddStockScreen
import com.shopmanager.app.ui.stock.StockScreen
import com.shopmanager.app.ui.theme.ShopTheme

object Routes {
    const val HOME = "home"
    const val SALES = "sales"
    const val SALES_HISTORY = "sales_history"
    const val STOCK = "stock"
    const val STOCK_ADD = "stock_add"
    const val PURCHASES = "purchases"
    const val PURCHASE_ADD = "purchase_add"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShopTheme {
                val nav = rememberNavController()
                val back: () -> Unit = { nav.popBackStack() }
                NavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(onOpen = { nav.navigate(it) }) }
                    composable(Routes.SALES) {
                        SalesScreen(onBack = back, onHistory = { nav.navigate(Routes.SALES_HISTORY) })
                    }
                    composable(Routes.SALES_HISTORY) { SalesHistoryScreen(onBack = back) }
                    composable(Routes.STOCK) {
                        StockScreen(onBack = back, onAddStock = { nav.navigate(Routes.STOCK_ADD) })
                    }
                    composable(Routes.STOCK_ADD) { AddStockScreen(onBack = back) }
                    composable(Routes.PURCHASES) {
                        PurchaseScreen(onBack = back, onAdd = { nav.navigate(Routes.PURCHASE_ADD) })
                    }
                    composable(Routes.PURCHASE_ADD) { AddPurchaseScreen(onBack = back) }
                    composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
                }
            }
        }
    }
}
