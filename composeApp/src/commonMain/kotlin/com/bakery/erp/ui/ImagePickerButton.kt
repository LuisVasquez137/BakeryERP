package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ImagePickerButton(
    currentImageUrl: String?,
    selectedBytes: ByteArray?,
    onImageSelected: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Ofrece además tomar la foto con la cámara. Solo tiene efecto en Android; en web y en
     * escritorio no hay cámara que abrir y el botón simplemente no aparece.
     *
     * Va apagado por defecto para no cambiarles el comportamiento a las pantallas que ya
     * usaban este componente (recetas, productos, activos, pedidos especiales): ahí la foto
     * sale del catálogo o de la galería, no del momento.
     */
    allowCamera: Boolean = false
)
