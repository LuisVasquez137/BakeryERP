/**
 * Lo que se agregó a una cuenta de ComandaYa DESPUÉS de recibirse en caja.
 *
 * El cliente paga y al rato pide un postre: el mesero reabre la misma orden, la agrega y la
 * vuelve a poner "Pagado". La cuenta original ya está en una gaveta y no se toca (reescribirla
 * descuadraba esa caja), así que la diferencia entra como una cuenta ADICIONAL, pendiente de
 * cobrar, con solo lo nuevo. Sin esto, lo agregado nunca aparecía en BakeOS: ni se cobraba ni
 * se facturaba, y nadie se enteraba.
 *
 * Lógica pura, con tests en externalAddendum.test.js.
 */

const round2 = (n) => Math.round((Number(n) || 0) * 100) / 100;

/** Mismo producto al mismo precio = mismo renglón. El precio entra porque un extra cambia el precio. */
function itemKey(l) {
  return `${l.productId || l.productName}|${round2(l.unitPrice)}`;
}

/** Cantidades ya cobradas por renglón, a partir de las líneas de la cuenta. */
function accountedItemsFrom(lines) {
  const out = {};
  for (const l of lines) {
    const k = itemKey(l);
    const prev = out[k] || { qty: 0, name: l.productName, productId: l.productId || null, unitPrice: round2(l.unitPrice) };
    prev.qty += Number(l.quantity) || 0;
    out[k] = prev;
  }
  return out;
}

/**
 * La cuenta adicional, o null si no se agregó nada que cobrar.
 *
 * El monto manda sobre las líneas: `deltaTotal` es lo que el cliente debe de más, con los
 * descuentos y el domicilio ya aplicados por ComandaYa. Si las líneas nuevas suman más que eso
 * (hubo descuento), la diferencia va como descuento; si no hay líneas nuevas pero sí más monto
 * (cambió un precio o el domicilio), va un renglón de ajuste para que la factura cuadre.
 *
 * Si el total BAJÓ después de cobrar (se quitó algo) devuelve `{ refund }`: eso es devolverle
 * dinero al cliente, y se registra para revisarlo a mano, no se inventa un movimiento.
 */
function buildAddendum({ accountedTotal, accountedItems, newTotal, newLines, orderLabel }) {
  const deltaTotal = round2(newTotal - accountedTotal);
  if (deltaTotal < -0.005) return { refund: -deltaTotal };
  if (deltaTotal <= 0.005) return null;

  const nuevos = accountedItemsFrom(newLines);
  const lines = [];
  for (const [k, item] of Object.entries(nuevos)) {
    const extra = round2(item.qty - (accountedItems[k]?.qty || 0));
    if (extra <= 0) continue;
    lines.push({
      productId: item.productId,
      productName: item.name,
      quantity: extra,
      unitPrice: item.unitPrice,
      subtotal: round2(extra * item.unitPrice),
    });
  }
  let subtotal = round2(lines.reduce((a, l) => a + l.subtotal, 0));
  if (subtotal < deltaTotal - 0.005) {
    const ajuste = round2(deltaTotal - subtotal);
    lines.push({ productId: null, productName: `Ajuste ${orderLabel}`.trim(), quantity: 1, unitPrice: ajuste, subtotal: ajuste });
    subtotal = deltaTotal;
  }
  return { deltaTotal, lines, subtotal, discount: round2(Math.max(0, subtotal - deltaTotal)), items: nuevos };
}

module.exports = { buildAddendum, accountedItemsFrom, itemKey };
