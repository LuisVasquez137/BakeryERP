package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** No-op para wasmJs (sin Firebase en este target). */
@Composable
actual fun rememberIsOnline(): State<Boolean> = remember { mutableStateOf(true) }
