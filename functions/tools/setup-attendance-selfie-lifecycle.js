/**
 * Regla de ciclo de vida del bucket: borra las selfies de asistencia a los 60 días.
 * Cloud Storage la aplica solo (una vez al día), sin función ni costo de lectura.
 *
 * Solo toca el prefijo attendanceSelfies/: el resto del bucket (documentos de
 * colaboradores, facturas, fotos de productos) no se ve afectado. Las reglas que ya
 * tenga el bucket se conservan; si esta ya existe no la duplica.
 *
 * Uso: node setup-attendance-selfie-lifecycle.js          (solo muestra las reglas)
 *      node setup-attendance-selfie-lifecycle.js --apply  (agrega la regla)
 *
 * Debe coincidir con ATTENDANCE_SELFIE_RETENTION_DAYS (model/AttendanceRecord.kt).
 */
const { initializeApp } = require('firebase-admin/app');
const { getStorage } = require('firebase-admin/storage');

const BUCKET = 'bakery-erp-saas.firebasestorage.app';
const PREFIX = 'attendanceSelfies/';
const AGE_DAYS = 60;

initializeApp({ projectId: 'bakery-erp-saas' });
const bucket = getStorage().bucket(BUCKET);
const APPLY = process.argv.includes('--apply');

const isOurs = (r) =>
  r.action && r.action.type === 'Delete' &&
  r.condition && (r.condition.matchesPrefix || []).includes(PREFIX);

(async () => {
  const [meta] = await bucket.getMetadata();
  const rules = (meta.lifecycle && meta.lifecycle.rule) || [];
  console.log(`Reglas actuales de ${BUCKET}:`, JSON.stringify(rules, null, 2));

  if (rules.some(isOurs)) {
    console.log(`Ya existe una regla de borrado para ${PREFIX}; no se cambia nada.`);
    return;
  }
  if (!APPLY) {
    console.log(`\nFalta la regla. Corre con --apply para borrar ${PREFIX} a los ${AGE_DAYS} días.`);
    return;
  }
  await bucket.addLifecycleRule({
    action: { type: 'Delete' },
    condition: { age: AGE_DAYS, matchesPrefix: [PREFIX] },
  }); // append por defecto: conserva las reglas existentes
  const [after] = await bucket.getMetadata();
  console.log('Reglas después:', JSON.stringify(after.lifecycle.rule, null, 2));
})().catch((e) => { console.error(e.message); process.exit(1); });
