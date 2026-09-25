package com.dsa.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.dsa.app.ui.SharedViewModel
import com.dsa.app.ui.WatchlistScreen
import com.dsa.app.ui.ChatScreen
import com.dsa.app.ui.SettingsScreen
import com.dsa.app.ui.theme.DsaTheme

enum class Tab(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    WATCH("自选", Icons.Filled.Star, Icons.Outlined.Star),
    CHAT("问股", Icons.Filled.Chat, Icons.Outlined.Chat),
    SETTINGS("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun App(vm: SharedViewModel) {
    var tab by remember { mutableStateOf(Tab.WATCH) }
    var detailCode by remember { mutableStateOf<String?>(null) }

    // 启动后延迟执行定时自动分析（避开首帧渲染高峰，内部已 try-catch）
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        vm.checkAndRunAutoAnalysis()
    }

    val currentTab = tab
    val detail = detailCode

    val darkTheme = when (vm.theme) {
        "dark" -> true
        "light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    DsaTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (detail == null) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = currentTab == t,
                                onClick = { tab = t },
                                icon = {
                                    Icon(
                                        if (currentTab == t) t.selectedIcon else t.unselectedIcon,
                                        contentDescription = t.label,
                                    )
                                },
                                label = { Text(t.label, fontSize = 11.sp) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            when {
                detail != null -> {
                    com.dsa.app.ui.DetailScreen(
                        vm = vm,
                        code = detail,
                        onClose = { detailCode = null },
                        modifier = Modifier.padding(padding),
                    )
                }
                currentTab == Tab.WATCH -> {
                    WatchlistScreen(
                        vm = vm,
                        onOpenDetail = { detailCode = it },
                        modifier = Modifier.padding(padding),
                    )
                }
                currentTab == Tab.CHAT -> {
                    ChatScreen(vm = vm, modifier = Modifier.padding(padding))
                }
                else -> {
                    SettingsScreen(vm = vm, modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
