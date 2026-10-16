package com.bakery.erp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.bakery.erp.ui.theme.BrandColors

/**
 * La barra superior de toda la app. Misma firma que `TopAppBar` de Material para que
 * cambiarla sea solo cambiar el nombre.
 *
 * Si la pantalla pide el azul de marca, se pinta degradada: del azul oscuro a la izquierda
 * (donde va el título) a un azul más vivo a la derecha, con un brillo celeste leve en la
 * esquina. Los tonos se quedaron oscuros a propósito: el título y los iconos son blancos y
 * un celeste claro los dejaría sin contraste. Cualquier otro color (la barra verde del
 * kiosko, las blancas) se respeta tal cual.
 *
 * El degradado vive aquí y solo aquí: para ajustar el tono de todas las pantallas se toca
 * [BrandHeaderBrush].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors()
) {
    if (colors.containerColor != BrandColors.Primary) {
        TopAppBar(title = title, modifier = modifier, navigationIcon = navigationIcon, actions = actions, colors = colors)
        return
    }
    Box(
        modifier
            .background(BrandHeaderBrush)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(BrandColors.ChartSky.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.height * 2.2f
                    ),
                    radius = size.height * 2.2f,
                    center = Offset(size.width, 0f)
                )
            }
    ) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor
            )
        )
    }
}

/** Degradado del encabezado: azul oscuro → azul de marca → azul vivo (blanco encima ≥ 5:1). */
val BrandHeaderBrush: Brush = Brush.horizontalGradient(
    listOf(BrandColors.PrimaryDark, BrandColors.Primary, BrandColors.HeaderBright)
)
