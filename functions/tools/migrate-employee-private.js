/**
 * FASE 2 de "Ficha privada" (arquitectura/seguridad-accesos.md). Correr SOLO cuando todos los
 * equipos tengan la versión de la app que lee employees/{id}/private/*.
 *
 * Por cada colaborador:
 *   1. Copia a private/profile (DPI, NIT, estado civil, dirección, emergencia, notas) y a
 *      private/pay (forma de pago, banco, cuenta, salario, prestaciones) lo que todavía viva en
 *      la ficha pública, salvo que la parte privada ya sea igual o más reciente.
 *   2. Con --strip, borra esos campos de la ficha pública.
 *
 * Sin --apply no escribe nada: solo muestra cuántas fichas tocaría (sin imprimir los datos).
 *
 * Uso:
 *   node migrate-employee-private.js [--company=<id>]                 # ensayo
 *   node migrate-employee-private.js --apply                          # copia a private/
 *   node migrate-employee-private.js --apply --strip                  # copia y limpia la pública
 *
 * Hecho el 16 oct 2026 (fase 2). Después de --strip: la regla employeePrivateFields() ya rechaza
 * esos campos en la ficha pública. Respaldar antes con backup-employees.js.
 */
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');

initializeApp({ projectId: 'bakery-erp-saas' });
const db = getFirestore();
const arg = (k, d) => (process.argv.find((a) => a.startsWith(`--${k}=`)) || '').split('=')[1] || d;
const COMPANY = arg('company', 'tiO54slG1n9sk6k2ePCD');
const APPLY = process.argv.includes('--apply');
const STRIP = process.argv.includes('--strip');

const PARTS = {
  profile: ['dpi', 'nit', 'maritalStatus', 'address', 'emergencyContact', 'emergencyPhone', 'notes'],
  pay: ['paymentType', 'bank', 'bankAccount', 'baseSalary', 'monthlyBenefits'],
};
const millis = (v) => (v && typeof v.toMillis === 'function' ? v.toMillis() : Number(v) || 0);

(async () => {
  const employees = await db.collection(`companies/${COMPANY}/employees`).get();
  const counts = { fichas: employees.size, copiar: { profile: 0, pay: 0 }, limpiar: 0 };

  for (const emp of employees.docs) {
    const data = emp.data();
    const publicUpdated = millis(data.updatedAt);
    const strip = {};

    for (const [part, fields] of Object.entries(PARTS)) {
      const legacy = fields.filter((f) => data[f] !== undefined);
      if (!legacy.length) continue;
      legacy.forEach((f) => (strip[f] = FieldValue.delete()));

      const ref = emp.ref.collection('private').doc(part);
      const current = await ref.get();
      // La parte privada manda si es igual o más reciente (la escribió la app nueva).
      if (current.exists && millis(current.data().updatedAt) >= publicUpdated) continue;

      counts.copiar[part]++;
      if (APPLY) {
        const payload = { updatedAt: publicUpdated || Date.now() };
        fields.forEach((f) => (payload[f] = data[f] ?? (part === 'pay' && /Salary|Benefits/.test(f) ? 0 : '')));
        await ref.set(payload);
      }
    }

    if (Object.keys(strip).length) {
      counts.limpiar++;
      if (APPLY && STRIP) await emp.ref.update(strip);
    }
  }

  console.log(APPLY ? 'APLICADO' : 'ENSAYO (sin --apply no se escribió nada)');
  console.log(`Fichas: ${counts.fichas}`);
  console.log(`A copiar a private/profile: ${counts.copiar.profile} · a private/pay: ${counts.copiar.pay}`);
  console.log(`Fichas públicas con campos privados: ${counts.limpiar}${STRIP && APPLY ? ' (limpiadas)' : ''}`);
})().catch((e) => { console.error(e); process.exit(1); });
