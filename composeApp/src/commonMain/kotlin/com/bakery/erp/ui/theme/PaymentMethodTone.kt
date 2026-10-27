package com.bakery.erp.ui.theme

import com.bakery.erp.model.SalePaymentMethod

/**
 * Color fijo de cada forma de pago, el mismo en el historial, el detalle del cobro y los filtros:
 * así una tarjeta se distingue de un efectivo de un vistazo. Efectivo verde (dinero en la
 * gaveta), tarjeta azul, transferencia morado, PedidosYa naranja (su marca); lo demás neutro.
 */
fun paymentMethodTone(method: String): ChipTone = when (method) {
    SalePaymentMethod.CASH -> ChipTone.SUCCESS
    SalePaymentMethod.CARD -> ChipTone.INFO
    SalePaymentMethod.TRANSFER -> ChipTone.PROGRESS
    SalePaymentMethod.PEDIDOS_YA -> ChipTone.ATTENTION
    SalePaymentMethod.CUSTOMER_DEPOSIT -> ChipTone.BRAND
    else -> ChipTone.NEUTRAL
}
