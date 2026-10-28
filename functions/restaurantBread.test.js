const test = require("node:test");
const assert = require("node:assert/strict");
const { breadNeedFor, breadDelta, dishIdsOf } = require("./restaurantBread");

const dishes = {
  chicken: { breadProductIds: ["pirujo"], breadProductNames: ["Pirujo con punta"], breadQuantities: [1] },
  club: { breadProductIds: ["molde", "pirujo"], breadProductNames: ["Pan molde", "Pirujo con punta"], breadQuantities: [3, 1] },
};
const line = (id, qty) => ({ productId: id, productName: id, quantity: qty });

test("cada platillo pide su pan por la cantidad ordenada", () => {
  const need = breadNeedFor([line("chicken", 2), line("club", 1), line("coca", 3)], dishes);
  assert.deepEqual(need, {
    pirujo: { qty: 3, name: "Pirujo con punta" },
    molde: { qty: 3, name: "Pan molde" },
  });
});

test("un platillo sin pan configurado no descuenta nada", () => {
  assert.deepEqual(breadNeedFor([line("coca", 2), { productId: null, quantity: 1 }], dishes), {});
});

test("un reenvío idéntico no vuelve a descontar", () => {
  const need = breadNeedFor([line("chicken", 2)], dishes);
  assert.deepEqual(breadDelta(need, need), {});
});

test("la cuenta corregida descuenta solo la diferencia", () => {
  const before = breadNeedFor([line("chicken", 2)], dishes);
  const after = breadNeedFor([line("chicken", 3)], dishes);
  assert.deepEqual(breadDelta(before, after), { pirujo: { qty: 1, name: "Pirujo con punta" } });
});

test("cancelada antes de recibirse: el pan vuelve", () => {
  const before = breadNeedFor([line("club", 1)], dishes);
  assert.deepEqual(breadDelta(before, {}), {
    molde: { qty: -3, name: "Pan molde" },
    pirujo: { qty: -1, name: "Pirujo con punta" },
  });
});

test("primer envío: todo es delta", () => {
  assert.deepEqual(breadDelta(undefined, { pirujo: { qty: 2, name: "P" } }), { pirujo: { qty: 2, name: "P" } });
});

test("ids de platillo sin repetir y sin vacíos", () => {
  assert.deepEqual(dishIdsOf([line("a", 1), line("a", 2), { productId: null }]), ["a"]);
});
