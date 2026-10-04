package io.github.lookie14.runcash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lookie14.runcash.ui.calendar.CalendarRoute
import io.github.lookie14.runcash.ui.health.HealthGate
import io.github.lookie14.runcash.ui.home.HomeRoute
import io.github.lookie14.runcash.ui.theme.RunCashColors

private val Tabs = listOf("오늘", "달력")

/** 하단 큰 글씨 탭 2개: 오늘 / 달력. */
@Composable
fun RunCashApp() {
    HealthGate { RunCashContent() }
}

@Composable
private fun RunCashContent() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(modifier = Modifier.background(Color.White)) {
                HorizontalDivider(color = RunCashColors.Track)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    Tabs.forEachIndexed { index, label ->
                        val selected = index == selectedTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.Tab,
                                    onClick = { selectedTab = index },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontSize = 26.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (selected) RunCashColors.Forest else RunCashColors.Muted,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        // 각 화면은 자기 Scaffold로 상태바 여백을 처리하므로 하단 탭이 차지한 만큼만 아래를 비운다.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            when (selectedTab) {
                0 -> HomeRoute()
                else -> CalendarRoute()
            }
        }
    }
}
