package com.bakery.erp.ui

import androidx.compose.runtime.Composable

/**
 * Lectura de códigos de barras con la cámara.
 *
 * El código de barras se puede TECLEAR siempre —un lector USB o Bluetooth se comporta como
 * un teclado: escribe el número y manda Enter— así que esto es un atajo, no el mecanismo.
 * Por eso [isBarcodeScanSupported] existe: donde no hay cámara la pantalla esconde el botón
 * y sigue funcionando igual, en vez de ofrecer algo que no va a pasar nada al tocarlo.
 */
@Composable
expect fun isBarcodeScanSupported(): Boolean

/**
 * Abre el escáner y devuelve el código leído. [onScanned] no se llama si el usuario cancela
 * o si la lectura falla — un fallo no debe borrar lo que ya estaba escrito en el campo.
 *
 * Es una función y no un botón para que cada pantalla ponga el ícono donde le sirva: en el
 * campo de búsqueda de la compra va como trailing icon, en el formulario del insumo como
 * botón aparte.
 */
@Composable
expect fun rememberBarcodeScanner(onScanned: (String) -> Unit): () -> Unit
