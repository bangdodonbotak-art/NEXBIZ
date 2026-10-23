package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import id.nexbiz.pos.ui.navigation.NexbizNavHost
import id.nexbiz.pos.ui.screens.dashboard.DashboardViewModel
import id.nexbiz.pos.ui.screens.pos.PosViewModel
import id.nexbiz.pos.ui.theme.NexbizPosTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val posViewModel: PosViewModel = viewModel(
                factory = PosViewModel.provideFactory(context)
            )
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.provideFactory(context)
            )
            NexbizPosTheme {
                NexbizNavHost(
                    posViewModel = posViewModel,
                    dashboardViewModel = dashboardViewModel
                )
            }
        }
    }
}
