const test = require("node:test");
const assert = require("node:assert/strict");
const { buildAddendum, accountedItemsFrom } = require("./externalAddendum");

const line = (name, qty, price) => ({ productId: name, productName: name, quantity: qty, unitPrice: price, subtotal: qty * price });
const cobrada = [line("Pollo", 2, 45), line("Coca", 2, 10)];

test("sin cambios no hay adicional (un reenvío idéntico no crea nada)", () => {
  assert.equal(buildAddendum({
    accountedTotal: 110, accountedItems: accountedItemsFrom(cobrada), newTotal: 110, newLines: cobrada, orderLabel: "orden 47",
  }), null);
});

test("el postre pedido después de pagar sale solo, sin repetir lo ya cobrado", () => {
  const nuevas = [...cobrada, line("Flan", 1, 18)];
  const a = buildAddendum({ accountedTotal: 110, accountedItems: accountedItemsFrom(cobrada), newTotal: 128, newLines: nuevas, orderLabel: "orden 47" });
  assert.equal(a.deltaTotal, 18);
  assert.deepEqual(a.lines.map((l) => [l.productName, l.quantity, l.subtotal]), [["Flan", 1, 18]]);
  assert.equal(a.discount, 0);
});

test("una unidad más del mismo plato entra como la diferencia de cantidad", () => {
  const nuevas = [line("Pollo", 3, 45), line("Coca", 2, 10)];
  const a = buildAddendum({ accountedTotal: 110, accountedItems: accountedItemsFrom(cobrada), newTotal: 155, newLines: nuevas, orderLabel: "" });
  assert.deepEqual(a.lines.map((l) => [l.productName, l.quantity]), [["Pollo", 1]]);
});

test("si hubo descuento, las líneas nuevas lo llevan y el total manda", () => {
  const nuevas = [...cobrada, line("Flan", 1, 20)];
  const a = buildAddendum({ accountedTotal: 110, accountedItems: accountedItemsFrom(cobrada), newTotal: 128, newLines: nuevas, orderLabel: "" });
  assert.equal(a.deltaTotal, 18);
  assert.equal(a.subtotal, 20);
  assert.equal(a.discount, 2);
});

test("más monto sin líneas nuevas (domicilio) va como ajuste para que la factura cuadre", () => {
  const a = buildAddendum({ accountedTotal: 110, accountedItems: accountedItemsFrom(cobrada), newTotal: 125, newLines: cobrada, orderLabel: "orden 47" });
  assert.deepEqual(a.lines.map((l) => [l.productName, l.subtotal]), [["Ajuste orden 47", 15]]);
  assert.equal(a.subtotal, 15);
});

test("si el total bajó después de cobrar es una devolución, no un adicional", () => {
  const a = buildAddendum({ accountedTotal: 110, accountedItems: accountedItemsFrom(cobrada), newTotal: 90, newLines: [line("Pollo", 2, 45)], orderLabel: "" });
  assert.deepEqual(a, { refund: 20 });
});
