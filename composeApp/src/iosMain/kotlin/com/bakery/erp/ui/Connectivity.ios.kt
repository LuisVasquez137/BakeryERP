package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Pendiente: NWPathMonitor. Mientras tanto iOS nunca muestra el aviso. */
@Composable
actual fun rememberIsOnline(): State<Boolean> = remember { mutableStateOf(true) }
