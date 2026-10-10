package com.bakery.erp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bakery.erp.model.SelfieCheck

/** Lo que la cámara le va diciendo a la pantalla para que muestre la instrucción. */
enum class SelfieCameraStatus {
    STARTING,
    NO_PERMISSION,
    NO_CAMERA,
    /** No hay cara dentro del círculo (o está muy lejos). */
    LOOK_AT_CAMERA,
    MULTIPLE_FACES,
    /** Hay una cara: falta el parpadeo. */
    BLINK,
    CAPTURED
}

/** `false` donde el kiosko no tiene cámara propia (web, iOS por ahora): se marca sin foto. */
expect val isSelfieCameraAvailable: Boolean

/**
 * Vista previa de la cámara frontal que se dispara sola: espera una sola cara, pide un
 * parpadeo y entrega un JPEG pequeño recortado a la cara. Si no hay parpadeo en unos
 * segundos entrega la foto igual con [SelfieCheck.FACE]. Nunca bloquea: la pantalla ofrece
 * "Marcar sin foto" si esto no llega a [onCaptured].
 *
 * El llamador da el tamaño y la forma (círculo) con [modifier].
 */
@Composable
expect fun SelfieCamera(
    modifier: Modifier,
    onStatus: (SelfieCameraStatus) -> Unit,
    onCaptured: (jpeg: ByteArray, check: SelfieCheck) -> Unit
)
