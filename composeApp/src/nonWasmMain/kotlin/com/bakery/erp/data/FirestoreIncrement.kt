package com.bakery.erp.data

import dev.gitlive.firebase.firestore.FieldValue

/**
 * `FieldValue.increment` con decimales. GitLive 2.1.0 solo lo ofrece para `Int`, y el
 * inventario y el dinero son `Double`.
 *
 * Por qué importa: una suma en el servidor no lee el saldo, así que dos cajas sin internet que
 * suben sus ventas en cualquier orden dejan el MISMO resultado. Un leer-modificar-escribir
 * hecho en el equipo pisaría el descuento de la otra caja (ver `arquitectura/pos-ventas.md`,
 * "Cobro sin internet").
 */
expect fun incrementBy(value: Double): FieldValue
