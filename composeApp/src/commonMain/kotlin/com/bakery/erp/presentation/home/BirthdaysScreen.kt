package com.bakery.erp.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakery.erp.ui.AdaptiveContainer
import com.bakery.erp.ui.BrandTopAppBar
import com.bakery.erp.ui.theme.BrandColors
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Cumpleaños del año, mes por mes. Abre ya en el mes en curso: lo que se busca casi siempre
 * es "quién cumple ahora o pronto", y los meses que ya pasaron quedan arriba por si acaso.
 *
 * Usa los datos que el Home ya cargó (`HomeUiState.birthdays`); no hace otra lectura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaysScreen(homeViewModel: HomeViewModel, onNavigateBack: () -> Unit) {
    val state by homeViewModel.uiState.collectAsState()
    val birthdays = state.birthdays
    val currentMonth = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.monthNumber }

    Scaffold(
        containerColor = BrandColors.Ivory,
        topBar = {
            BrandTopAppBar(
                title = {
                    Column {
                        Text("Cumpleaños", color = Color.White, fontWeight = FontWeight.Bold)
                        if (birthdays != null && birthdays.year > 0) {
                            Text("${birthdays.year}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandColors.Primary)
            )
        }
    ) { padding ->
        AdaptiveContainer(modifier = Modifier.padding(padding)) {
            if (birthdays == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@AdaptiveContainer
            }
            val byMonth = remember(birthdays) { birthdays.allYear.groupBy { it.date.monthNumber } }
            val listState = rememberLazyListState()
            // Índice del encabezado del mes en curso: resumen (1) + por cada mes anterior,
            // su encabezado y sus filas (o la línea "nadie cumple").
            val currentIndex = remember(byMonth) {
                1 + (1 until currentMonth).sumOf { m -> 1 + maxOf(1, byMonth[m]?.size ?: 0) }
            }
            LaunchedEffect(currentIndex) { listState.scrollToItem(currentIndex) }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "summary") { YearSummary(birthdays) }
                (1..12).forEach { m ->
                    val people = byMonth[m].orEmpty()
                    item(key = "m_$m") { MonthHeader(m, people.size, isCurrent = m == currentMonth) }
                    if (people.isEmpty()) {
                        item(key = "e_$m") {
                            Text(
                                "Nadie cumple años en ${monthName(m)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandColors.MutedSlate,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                    } else {
                        items(people, key = { "p_${m}_${it.employeeId}" }) { p -> BirthdayRow(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearSummary(b: Birthdays) {
    val next = b.allYear.firstOrNull { it.daysFromToday >= 0 }
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(BrandColors.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Cake, null, tint = BrandColors.OnAccent)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when (b.allYear.size) {
                        0 -> "Todavía no hay fechas de nacimiento"
                        1 -> "1 persona del equipo"
                        else -> "${b.allYear.size} personas del equipo"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandColors.TextSlate
                )
                Text(
                    when {
                        b.allYear.isEmpty() -> "Llénalas en la ficha de cada colaborador y aparecerán aquí."
                        next == null -> "Ya pasaron todos los cumpleaños de este año."
                        next.daysFromToday == 0 -> "¡Hoy cumple años ${next.name}!"
                        next.daysFromToday == 1 -> "Mañana cumple años ${next.name}."
                        else -> "El próximo: ${next.name}, ${weekdayName(next.date.dayOfWeek)} ${next.date.dayOfMonth} de ${monthName(next.date.monthNumber)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandColors.MutedSlate
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(month: Int, count: Int, isCurrent: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            monthName(month).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (isCurrent) BrandColors.AccentDeep else BrandColors.Primary
        )
        if (count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) BrandColors.OnAccent else BrandColors.Primary,
                modifier = Modifier.clip(CircleShape)
                    .background(if (isCurrent) BrandColors.Accent else BrandColors.PrimaryContainer)
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
        if (isCurrent) {
            Text("este mes", style = MaterialTheme.typography.labelSmall, color = BrandColors.AccentDeep)
        }
        Box(Modifier.weight(1f).height(1.dp).background(BrandColors.ChartTrack))
    }
}
