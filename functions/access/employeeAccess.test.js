// Pruebas de las reglas puras del PIN (sin Firestore). `npm test` desde functions/.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { _internos: P } = require("./employeeAccess");

test("PIN válido: 4 a 6 números, ni repetidos ni en serie", () => {
  for (const ok of ["1357", "2580", "907153", "13579", "1243"]) assert.equal(P.motivoPinInvalido(ok), null, ok);
  for (const malo of ["123", "1234567", "12a4", "", null, 1357, "1111", "000000", "1234", "4321", "345678", "987654"]) {
    assert.notEqual(P.motivoPinInvalido(malo), null, String(malo));
  }
});

test("el hash lleva sal: el mismo PIN da hashes distintos y ambos coinciden", () => {
  const a = P.nuevoHash("2580");
  const b = P.nuevoHash("2580");
  assert.notEqual(a.hash, b.hash);
  assert.ok(P.pinCoincide("2580", a.sal, a.hash));
  assert.ok(P.pinCoincide("2580", b.sal, b.hash));
  assert.ok(!P.pinCoincide("2581", a.sal, a.hash));
  assert.ok(!P.pinCoincide("2580", a.sal, ""));
});

test("fallos cuentan, al quinto se bloquea y el acierto limpia", () => {
  const { sal, hash } = P.nuevoHash("2580");
  let estado = { sal, hash, fallos: 0, bloqueadoHasta: 0 };
  const t0 = 1_000_000;
  for (let i = 1; i < P.MAX_FALLOS; i++) {
    const r = P.evaluarIntento(estado, "0000", t0);
    assert.equal(r.ok, false);
    assert.equal(r.bloqueado, false);
    assert.equal(r.restantes, P.MAX_FALLOS - i);
    estado = { ...estado, fallos: r.fallos, bloqueadoHasta: r.bloqueadoHasta };
  }
  const bloqueo = P.evaluarIntento(estado, "0000", t0);
  assert.equal(bloqueo.bloqueado, true);
  assert.equal(bloqueo.bloqueadoHasta, t0 + P.BLOQUEO_MS);
  estado = { ...estado, fallos: bloqueo.fallos, bloqueadoHasta: bloqueo.bloqueadoHasta };

  // Bloqueado: ni el PIN correcto entra.
  assert.equal(P.evaluarIntento(estado, "2580", t0 + 1000).bloqueado, true);
  // Pasado el bloqueo, el correcto entra y deja todo en cero.
  const despues = P.evaluarIntento(estado, "2580", t0 + P.BLOQUEO_MS + 1);
  assert.deepEqual(despues, { ok: true, fallos: 0, bloqueadoHasta: 0 });
});
