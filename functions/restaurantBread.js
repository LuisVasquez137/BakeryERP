/**
 * Pan que llevan los platillos del restaurante (oct 2026).
 *
 * El chicken sandwich lleva un pirujo con punta: la cocina lo toma del mostrador y, sin esto,
 * el conteo de cierre lo ve como faltante (merma o, en el pan por grupo, venta sin dinero).
 * En BakeOS se configura una vez qué pan lleva cada platillo de ComandaYa
 * (`companies/{c}/restaurantDishes/{externalItemId}`), y al llegar la cuenta se descuenta del
 * inventario de tienda de la sucursal como `CONSUMO_RESTAURANTE`, a costo.
 *
 * Se vincula por el id del platillo en ComandaYa, no por el nombre: renombrar "Chicken
 * sandwich" no debe romper el vínculo.
 *
 * Corregir = escribir la diferencia: la venta guarda lo que ya descontó (`appliedBread`) y un
 * reenvío o una cancelación aplican solo el delta.
 */

const EPS = 1e-9;

/**
 * Pan que piden estas líneas: { productId: { qty, name } }.
 *
 * [dishes]: externalItemId → doc de `restaurantDishes` (con `breadProductIds`,
 * `breadProductNames`, `breadQuantities` en arreglos paralelos). Un platillo sin pan
 * configurado no aporta nada.
 */
function breadNeedFor(lines, dishes) {
  const need = {};
  for (const l of lines) {
    const dish = l.productId ? dishes[l.productId] : null;
    if (!dish) continue;
    const ids = dish.breadProductIds || [];
    const names = dish.breadProductNames || [];
    const qtys = dish.breadQuantities || [];
    ids.forEach((pid, i) => {
      const q = (Number(qtys[i]) || 0) * (Number(l.quantity) || 0);
      if (!pid || q <= EPS) return;
      const prev = need[pid] || { qty: 0, name: names[i] || "" };
      prev.qty += q;
      need[pid] = prev;
    });
  }
  return need;
}

/**
 * Lo que hay que descontar ahora: { productId: { qty, name } } con qty > 0 = sale más pan,
 * qty < 0 = vuelve pan (la cuenta se corrigió o se canceló antes de recibirse en caja).
 */
function breadDelta(previous, next) {
  const out = {};
  const ids = new Set([...Object.keys(previous || {}), ...Object.keys(next || {})]);
  for (const pid of ids) {
    const before = (previous && previous[pid] && previous[pid].qty) || 0;
    const after = (next && next[pid] && next[pid].qty) || 0;
    const d = after - before;
    if (Math.abs(d) <= EPS) continue;
    const name = (next && next[pid] && next[pid].name) || (previous && previous[pid] && previous[pid].name) || "";
    out[pid] = { qty: d, name };
  }
  return out;
}

/** Ids de platillo distintos de las líneas (para leer su configuración de una vez). */
function dishIdsOf(lines) {
  return [...new Set(lines.map((l) => l.productId).filter(Boolean))];
}

module.exports = { breadNeedFor, breadDelta, dishIdsOf };
