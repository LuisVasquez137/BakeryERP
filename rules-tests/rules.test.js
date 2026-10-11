/**
 * Pruebas de firestore.rules y storage.rules. Corren contra el emulador:
 *   cd rules-tests && npm test
 *
 * Cada flujo real de la app (FirebaseAuthRepository, aprobar usuario, revendedor,
 * regenerar código) tiene una prueba que DEBE pasar; cada hueco que existía en las
 * reglas publicadas antes de oct 2026 tiene una prueba que DEBE fallar.
 */
const { test, before, after, beforeEach, describe } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment, assertSucceeds, assertFails,
} = require('@firebase/rules-unit-testing');
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs, query, where,
} = require('firebase/firestore');

let env;
const root = path.join(__dirname, '..');

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-bakeos',
    firestore: { rules: fs.readFileSync(path.join(root, 'firestore.rules'), 'utf8'), host: '127.0.0.1', port: 8085 },
    storage: { rules: fs.readFileSync(path.join(root, 'storage.rules'), 'utf8'), host: '127.0.0.1', port: 9199 },
  });
});
after(async () => { await env.cleanup(); });

// ── Datos de partida ───────────────────────────────────────────────────────

const profile = (over) => ({
  orgId: 'orgA', email: 'x@x.com', displayName: 'X', role: 'EMPLOYEE',
  branchIds: [], permissions: [], isActive: true, isApproved: true, photoUrl: null, ...over,
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const users = {
      // El OWNER original no trae isApproved (doc viejo).
      ownerA: { orgId: 'orgA', email: 'owner@a.com', displayName: 'Dueño', role: 'OWNER', branchIds: [], permissions: [], isActive: true, photoUrl: null },
      adminA: profile({ role: 'ADMIN', email: 'admin@a.com' }),
      managerUsersA: profile({ permissions: ['MANAGE_USERS'] }),
      encargadaA: profile({ permissions: ['OPERATE_POS', 'MANAGE_EXTERNAL_CUSTOMERS'], branchIds: ['b1'] }),
      cajeraA: profile({ permissions: ['OPERATE_POS'], branchIds: ['b1'] }),
      pendingA: profile({ isApproved: false, isActive: false }),
      inactiveA: profile({ isActive: false }),
      resellerPendingA: profile({ role: 'RESELLER', isApproved: false }),
      ownerB: profile({ orgId: 'orgB', role: 'OWNER', email: 'owner@b.com' }),
      googleNoOrg: profile({ orgId: '', role: 'OWNER', email: 'g@x.com' }),
    };
    for (const [id, data] of Object.entries(users)) await setDoc(doc(db, 'users', id), data);
    await setDoc(doc(db, 'organizations/orgA'), { name: 'Sabropan', ownerEmail: 'owner@a.com', inviteCode: 'AAAAAA' });
    await setDoc(doc(db, 'organizations/orgB'), { name: 'Otra', ownerEmail: 'owner@b.com', inviteCode: 'BBBBBB' });
    await setDoc(doc(db, 'inviteCodes/AAAAAA'), { orgId: 'orgA', orgName: 'Sabropan' });
    await setDoc(doc(db, 'inviteCodes/BBBBBB'), { orgId: 'orgB', orgName: 'Otra' });
    await setDoc(doc(db, 'companies/orgA/sales/s1'), { total: 10 });
    await setDoc(doc(db, 'companies/orgA/branches/b1'), { name: 'Bombero' });
    await setDoc(doc(db, 'companies/orgB/sales/s1'), { total: 99 });
  });
});

const as = (uid, email) => env.authenticatedContext(uid, { email: email || `${uid}@test.com` }).firestore();
const stranger = () => as('stranger', 'stranger@evil.com');

// ── Flujos de la app que tienen que seguir funcionando ─────────────────────

describe('flujos de la app', () => {
  test('registro de empresa nueva: perfil OWNER, organización y código', async () => {
    const db = as('nuevo', 'nuevo@x.com');
    await assertSucceeds(setDoc(doc(db, 'users/nuevo'), profile({ orgId: 'orgNueva', role: 'OWNER', email: 'Nuevo@x.com' })));
    await assertSucceeds(setDoc(doc(db, 'organizations/orgNueva'), { name: 'P', ownerEmail: 'Nuevo@X.com', subscriptionPlan: 'free', inviteCode: 'NNNNNN' }));
    await assertSucceeds(setDoc(doc(db, 'inviteCodes/NNNNNN'), { orgId: 'orgNueva', orgName: 'P' }));
    await assertSucceeds(setDoc(doc(db, 'companies/orgNueva/products/p1'), { name: 'Pan' }));
  });

  test('Google primera vez: perfil sin empresa y luego crea la suya', async () => {
    const db = as('g1', 'g1@x.com');
    await assertSucceeds(setDoc(doc(db, 'users/g1'), profile({ orgId: '', role: 'OWNER', email: 'g1@x.com' })));
    await assertSucceeds(setDoc(doc(db, 'users/g1'), profile({ orgId: 'orgG', role: 'OWNER', email: 'g1@x.com' })));
    await assertSucceeds(setDoc(doc(db, 'organizations/orgG'), { name: 'G', ownerEmail: 'g1@x.com' }));
  });

  test('Google sin empresa se une con código y queda pendiente', async () => {
    const db = as('googleNoOrg', 'g@x.com');
    await assertSucceeds(getDoc(doc(db, 'inviteCodes/AAAAAA')));
    await assertSucceeds(setDoc(doc(db, 'users/googleNoOrg'), profile({ isApproved: false, isActive: false, email: 'g@x.com' })));
  });

  test('unirse con código (correo y contraseña) queda pendiente', async () => {
    const db = as('j1');
    await assertSucceeds(getDoc(doc(db, 'inviteCodes/AAAAAA')));
    await assertSucceeds(setDoc(doc(db, 'users/j1'), profile({ isApproved: false, isActive: false })));
  });

  test('pendiente lee su perfil y el nombre de su empresa', async () => {
    const db = as('pendingA');
    await assertSucceeds(getDoc(doc(db, 'users/pendingA')));
    await assertSucceeds(getDoc(doc(db, 'organizations/orgA')));
  });

  test('OWNER y ADMIN aprueban y asignan permisos (set del doc completo)', async () => {
    const approved = profile({ isApproved: true, isActive: true, permissions: ['OPERATE_POS'], branchIds: ['b1'] });
    await assertSucceeds(setDoc(doc(as('ownerA'), 'users/pendingA'), approved));
    await assertSucceeds(setDoc(doc(as('adminA'), 'users/cajeraA'), profile({ permissions: ['OPERATE_POS', 'VOID_SALE'] })));
  });

  test('OWNER puede nombrar a otro OWNER (caso sindel)', async () => {
    await assertSucceeds(updateDoc(doc(as('ownerA'), 'users/adminA'), { role: 'OWNER' }));
  });

  test('cada quien actualiza su foto y su nombre', async () => {
    await assertSucceeds(updateDoc(doc(as('cajeraA'), 'users/cajeraA'), { photoUrl: 'https://x', displayName: 'Ana' }));
  });

  test('personal lista a los usuarios de su empresa', async () => {
    const db = as('cajeraA');
    await assertSucceeds(getDocs(query(collection(db, 'users'), where('orgId', '==', 'orgA'))));
  });

  test('personal aprobado lee y escribe datos de su empresa', async () => {
    const db = as('cajeraA');
    await assertSucceeds(getDoc(doc(db, 'companies/orgA/sales/s1')));
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/sales/s2'), { branchId: 'b1', total: 5 }));
  });

  test('revendedor: registro, escoger sucursal y completar ficha', async () => {
    const db = as('r1');
    await assertSucceeds(setDoc(doc(db, 'users/r1'), profile({ role: 'RESELLER', isApproved: false })));
    await assertSucceeds(getDoc(doc(db, 'organizations/orgA')));
    await assertSucceeds(getDocs(collection(db, 'companies/orgA/branches')));
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/externalCustomers/c1'), { linkedUserId: 'r1', active: false, registrationStatus: 'PENDING_APPROVAL' }));
  });

  test('encargada con MANAGE_EXTERNAL_CUSTOMERS aprueba al revendedor', async () => {
    await assertSucceeds(updateDoc(doc(as('encargadaA'), 'users/resellerPendingA'), { isApproved: true }));
  });

  test('regenerar código de invitación', async () => {
    const db = as('ownerA', 'owner@a.com');
    await assertSucceeds(updateDoc(doc(db, 'organizations/orgA'), { inviteCode: 'CCCCCC' }));
    await assertSucceeds(setDoc(doc(db, 'inviteCodes/CCCCCC'), { orgId: 'orgA', orgName: 'Sabropan' }));
    await assertSucceeds(deleteDoc(doc(db, 'inviteCodes/AAAAAA')));
  });

  test('OWNER cambia ajustes de la empresa', async () => {
    await assertSucceeds(updateDoc(doc(as('ownerA'), 'organizations/orgA'), { usesResalePurchases: true }));
  });
});

// ── Huecos que existían y tienen que quedar cerrados ───────────────────────

describe('ataques', () => {
  test('empleado no se puede subir a OWNER ni darse permisos', async () => {
    const db = as('cajeraA');
    await assertFails(updateDoc(doc(db, 'users/cajeraA'), { role: 'OWNER' }));
    await assertFails(updateDoc(doc(db, 'users/cajeraA'), { permissions: ['MANAGE_USERS'] }));
    await assertFails(updateDoc(doc(db, 'users/cajeraA'), { branchIds: [] }));
  });

  test('nadie se cambia de empresa', async () => {
    await assertFails(updateDoc(doc(as('cajeraA'), 'users/cajeraA'), { orgId: 'orgB' }));
  });

  test('pendiente no se aprueba solo', async () => {
    await assertFails(updateDoc(doc(as('pendingA'), 'users/pendingA'), { isApproved: true, isActive: true }));
  });

  test('extraño no puede crearse un perfil dentro de una empresa existente', async () => {
    const db = stranger();
    await assertFails(setDoc(doc(db, 'users/stranger'), profile({ role: 'OWNER' })));
    await assertFails(setDoc(doc(db, 'users/stranger'), profile({ isApproved: true })));
    await assertFails(setDoc(doc(db, 'users/stranger'), profile({ isApproved: false, permissions: ['OPERATE_POS'] })));
    await assertFails(setDoc(doc(db, 'users/stranger'), profile({ role: 'ADMIN', isApproved: false })));
  });

  test('Google sin empresa no se mete como OWNER a una existente', async () => {
    await assertFails(setDoc(doc(as('googleNoOrg'), 'users/googleNoOrg'), profile({ role: 'OWNER', email: 'g@x.com' })));
  });

  test('no se puede listar empresas ni códigos', async () => {
    const db = stranger();
    await assertFails(getDocs(collection(db, 'organizations')));
    await assertFails(getDocs(collection(db, 'inviteCodes')));
    await assertFails(getDoc(doc(db, 'organizations/orgA')));
  });

  test('extraño no lee ni escribe datos de una empresa', async () => {
    const db = stranger();
    await assertFails(getDoc(doc(db, 'companies/orgA/sales/s1')));
    await assertFails(setDoc(doc(db, 'companies/orgA/sales/x'), { total: 1 }));
  });

  test('pendiente e inactivo no ven datos de la empresa ni la lista de personal', async () => {
    await assertFails(getDoc(doc(as('pendingA'), 'companies/orgA/sales/s1')));
    await assertFails(getDoc(doc(as('inactiveA'), 'companies/orgA/sales/s1')));
    await assertFails(getDocs(query(collection(as('pendingA'), 'users'), where('orgId', '==', 'orgA'))));
  });

  test('una empresa no ve a la otra', async () => {
    await assertFails(getDoc(doc(as('cajeraA'), 'companies/orgB/sales/s1')));
    await assertFails(setDoc(doc(as('ownerB'), 'users/cajeraA'), profile({ role: 'OWNER' })));
  });

  test('empleado no edita a otros usuarios (ni con MANAGE_USERS: solo OWNER/ADMIN)', async () => {
    await assertFails(updateDoc(doc(as('cajeraA'), 'users/pendingA'), { isApproved: true }));
    await assertFails(updateDoc(doc(as('managerUsersA'), 'users/pendingA'), { isApproved: true }));
  });

  test('ADMIN no crea OWNERs ni toca al OWNER', async () => {
    await assertFails(updateDoc(doc(as('adminA'), 'users/cajeraA'), { role: 'OWNER' }));
    await assertFails(updateDoc(doc(as('adminA'), 'users/adminA'), { role: 'OWNER' }));
    await assertFails(updateDoc(doc(as('adminA'), 'users/ownerA'), { permissions: [] }));
  });

  test('encargada solo aprueba revendedores, no empleados, y solo isApproved', async () => {
    await assertFails(updateDoc(doc(as('encargadaA'), 'users/pendingA'), { isApproved: true }));
    await assertFails(updateDoc(doc(as('encargadaA'), 'users/resellerPendingA'), { isApproved: true, role: 'OWNER' }));
  });

  test('revendedor pendiente no ve ventas ni crea fichas ajenas', async () => {
    const db = as('resellerPendingA');
    await assertFails(getDoc(doc(db, 'companies/orgA/sales/s1')));
    await assertFails(setDoc(doc(db, 'companies/orgA/externalCustomers/c9'), { linkedUserId: 'otro', active: false }));
    await assertFails(setDoc(doc(db, 'companies/orgA/externalCustomers/c9'), { linkedUserId: 'resellerPendingA', active: true }));
  });

  test('no se borran perfiles ni se crea un código para otra empresa', async () => {
    await assertFails(deleteDoc(doc(as('ownerA'), 'users/cajeraA')));
    await assertFails(setDoc(doc(as('ownerA'), 'inviteCodes/ZZZZZZ'), { orgId: 'orgB' }));
    await assertFails(setDoc(doc(as('cajeraA'), 'inviteCodes/ZZZZZZ'), { orgId: 'orgA' }));
  });

  test('ADMIN no cambia el correo dueño de la empresa', async () => {
    await assertFails(updateDoc(doc(as('adminA'), 'organizations/orgA'), { ownerEmail: 'admin@a.com' }));
  });

  test('empleado no cambia ajustes de la empresa', async () => {
    await assertFails(updateDoc(doc(as('cajeraA'), 'organizations/orgA'), { usesResalePurchases: true }));
  });
});

// ── Colaboradores: permisos y PIN ──────────────────────────────────────────

describe('colaboradores', () => {
  const ficha = { name: 'Ana', status: 'ACTIVE', branchId: 'b1' };
  const seed = (data) => env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), 'companies/orgA/employees/e1'), data));

  test('el formulario crea y edita la ficha sin tocar acceso', async () => {
    const db = as('encargadaA');
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/employees/e2'), ficha));
    await seed({ ...ficha, permissions: ['OPERATE_POS'], pinStatus: 'SET', pinUpdatedAt: 1 });
    await assertSucceeds(updateDoc(doc(db, 'companies/orgA/employees/e1'), { name: 'Ana María', phone: '5555' }));
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/employees/e1/documents/d1'), { url: 'x' }));
    await assertSucceeds(getDoc(doc(as('cajeraA'), 'companies/orgA/employees/e1')));
  });

  test('OWNER y ADMIN asignan permisos al colaborador', async () => {
    await seed(ficha);
    await assertSucceeds(updateDoc(doc(as('ownerA'), 'companies/orgA/employees/e1'), { permissions: ['OPERATE_POS'] }));
    await assertSucceeds(updateDoc(doc(as('adminA'), 'companies/orgA/employees/e1'), { permissions: ['OPERATE_POS', 'VOID_SALE'] }));
    await assertSucceeds(setDoc(doc(as('ownerA'), 'companies/orgA/employees/e3'), { ...ficha, permissions: ['OPERATE_POS'] }));
  });

  test('nadie más se da permisos ni toca el estado del PIN', async () => {
    await seed({ ...ficha, pinStatus: 'TEMPORARY', pinUpdatedAt: 1 });
    const enc = as('encargadaA');
    await assertFails(updateDoc(doc(enc, 'companies/orgA/employees/e1'), { permissions: ['VIEW_FINANCIAL_REPORT'] }));
    await assertFails(setDoc(doc(enc, 'companies/orgA/employees/e9'), { ...ficha, permissions: ['VOID_SALE'] }));
    await assertFails(updateDoc(doc(enc, 'companies/orgA/employees/e1'), { pinStatus: 'SET' }));
    await assertFails(updateDoc(doc(as('ownerA'), 'companies/orgA/employees/e1'), { pinStatus: 'SET' }));
    await assertFails(setDoc(doc(enc, 'companies/orgA/employees/e9'), { ...ficha, pinStatus: 'SET' }));
  });

  test('el hash del PIN no lo lee nadie, ni el dueño', async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), 'employeePins/orgA__e1'), { hash: 'x', sal: 'y' }));
    await assertFails(getDoc(doc(as('ownerA'), 'employeePins/orgA__e1')));
    await assertFails(setDoc(doc(as('ownerA'), 'employeePins/orgA__e1'), { hash: 'z' }));
  });

  test('cuenta de equipo: la marca el OWNER, nadie se la pone o quita solo ni nace con ella', async () => {
    await assertSucceeds(updateDoc(doc(as('ownerA'), 'users/cajeraA'), { isDeviceAccount: true }));
    await assertFails(updateDoc(doc(as('encargadaA'), 'users/encargadaA'), { isDeviceAccount: true }));
    await assertFails(setDoc(doc(as('j2'), 'users/j2'), profile({ isApproved: false, isActive: false, isDeviceAccount: true })));
    await assertSucceeds(setDoc(doc(as('j3'), 'users/j3'), profile({ isApproved: false, isActive: false, isDeviceAccount: false })));
  });
});

// ── Fase 4: historial, configuración y sucursal ────────────────────────────

describe('fase 4', () => {
  const seed = (path, data) => env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), path), data));

  test('el historial de dinero no se borra, ni el dueño', async () => {
    await seed('companies/orgA/cashSessions/cs1', { branchId: 'b1' });
    await seed('companies/orgA/cashSessions/cs1/movements/m1', { amount: 5 });
    await seed('companies/orgA/sales/s1/lines/l1', { qty: 1 });
    await seed('companies/orgA/ingredients/i1/movements/k1', { qty: 1 });
    await seed('companies/orgA/salesDaily/d1', { total: 1 });
    for (const who of ['ownerA', 'cajeraA']) {
      const db = as(who);
      await assertFails(deleteDoc(doc(db, 'companies/orgA/sales/s1')));
      await assertFails(deleteDoc(doc(db, 'companies/orgA/sales/s1/lines/l1')));
      await assertFails(deleteDoc(doc(db, 'companies/orgA/cashSessions/cs1')));
      await assertFails(deleteDoc(doc(db, 'companies/orgA/cashSessions/cs1/movements/m1')));
      await assertFails(deleteDoc(doc(db, 'companies/orgA/ingredients/i1/movements/k1')));
      await assertFails(deleteDoc(doc(db, 'companies/orgA/salesDaily/d1')));
    }
  });

  test('revendedores: la lista de precios es del que maneja precios', async () => {
    await seed('companies/orgA/externalCustomers/r1', { type: 'RESELLER', name: 'Don Beto', priceListId: 'L1', registrationStatus: 'ACTIVE', active: true });
    await seed('companies/orgA/externalCustomers/k1', { type: 'RETAIL_RECURRING', name: 'Doña Marta', priceListId: '', active: true });
    const enc = as('encargadaA');
    const r1 = doc(enc, 'companies/orgA/externalCustomers/r1');
    // Corregir el teléfono de un revendedor sí; su precio, su estado o borrarlo no.
    await assertSucceeds(setDoc(r1, { type: 'RESELLER', name: 'Don Beto', phone: '5555', priceListId: 'L1', registrationStatus: 'ACTIVE', active: true }));
    await assertFails(setDoc(r1, { type: 'RESELLER', name: 'Don Beto', priceListId: 'L2', registrationStatus: 'ACTIVE', active: true }));
    await assertFails(setDoc(r1, { type: 'RESELLER', name: 'Don Beto', priceListId: 'L1', registrationStatus: 'ACTIVE', active: false }));
    await assertFails(deleteDoc(r1));
    // Dar de alta un revendedor o convertir un cliente en revendedor, no.
    await assertFails(setDoc(doc(enc, 'companies/orgA/externalCustomers/r2'), { type: 'RESELLER', name: 'X', priceListId: 'L1', active: true }));
    await assertFails(setDoc(doc(enc, 'companies/orgA/externalCustomers/k1'), { type: 'RESELLER', name: 'Doña Marta', priceListId: 'L1', active: true }));
    // Clientes recurrentes: libres.
    await assertSucceeds(setDoc(doc(enc, 'companies/orgA/externalCustomers/k2'), { type: 'RETAIL_RECURRING', name: 'Oficina' }));
    await assertSucceeds(deleteDoc(doc(enc, 'companies/orgA/externalCustomers/k1')));
    // Sin el permiso de clientes, nada.
    await assertFails(setDoc(doc(as('cajeraA'), 'companies/orgA/externalCustomers/k3'), { type: 'RETAIL_RECURRING', name: 'Y' }));
    // El dueño, todo.
    const own = as('ownerA');
    await assertSucceeds(setDoc(doc(own, 'companies/orgA/externalCustomers/r3'), { type: 'RESELLER', name: 'Z', priceListId: 'L1', active: true }));
    await assertSucceeds(deleteDoc(doc(own, 'companies/orgA/externalCustomers/r1')));
  });

  test('lo que la app sí borra se sigue pudiendo borrar', async () => {
    await seed('companies/orgA/cashSessions/cs1/openingDenominations/q100', { n: 1 });
    await seed('companies/orgA/dailyCashCloses/dc1/depositDenominations/q100', { n: 1 });
    await seed('companies/orgA/creditMovements/cm1', { amount: 1 });
    await seed('companies/orgA/notifications/n1', { t: 1 });
    await seed('companies/orgA/productionOrders/po1/lines/l1', { q: 1 });
    const db = as('cajeraA');
    await assertSucceeds(deleteDoc(doc(db, 'companies/orgA/cashSessions/cs1/openingDenominations/q100')));
    await assertSucceeds(deleteDoc(doc(db, 'companies/orgA/dailyCashCloses/dc1/depositDenominations/q100')));
    await assertSucceeds(deleteDoc(doc(db, 'companies/orgA/creditMovements/cm1')));
    await assertSucceeds(deleteDoc(doc(db, 'companies/orgA/notifications/n1')));
    await assertSucceeds(deleteDoc(doc(db, 'companies/orgA/productionOrders/po1/lines/l1')));
  });

  test('el historial se sigue escribiendo y corrigiendo', async () => {
    const db = as('cajeraA');
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/sales/s2'), { branchId: 'b1', total: 5 }));
    await assertSucceeds(updateDoc(doc(db, 'companies/orgA/sales/s1'), { status: 'VOIDED' }));
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/sales/s2/lines/l1'), { qty: 1 }));
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/cashSessions/cs9'), { branchId: 'b1' }));
    await assertSucceeds(setDoc(doc(db, 'companies/orgA/cashSessions/cs9/movements/m1'), { amount: 5 }));
  });

  test('ventas y cajas nuevas solo en las sucursales de la cuenta', async () => {
    await assertFails(setDoc(doc(as('cajeraA'), 'companies/orgA/sales/x1'), { branchId: 'b2', total: 5 }));
    await assertFails(setDoc(doc(as('cajeraA'), 'companies/orgA/cashSessions/x1'), { branchId: 'b2' }));
    await assertFails(setDoc(doc(as('cajeraA'), 'companies/orgA/sales/x2'), { total: 5 }));
    await assertSucceeds(setDoc(doc(as('ownerA'), 'companies/orgA/sales/x3'), { branchId: 'b2', total: 5 }));
    // Cuenta sin sucursales asignadas = todas (canAccessBranch de la app).
    await assertSucceeds(setDoc(doc(as('managerUsersA'), 'companies/orgA/sales/x4'), { branchId: 'b2', total: 5 }));
  });

  test('configuración solo con su permiso', async () => {
    const caj = as('cajeraA');
    await assertFails(setDoc(doc(caj, 'companies/orgA/cashRegisters/r1'), { name: 'Caja 1' }));
    await assertFails(setDoc(doc(caj, 'companies/orgA/saleQuickGroups/g1'), { name: 'Pan Q0.50' }));
    await assertFails(setDoc(doc(caj, 'companies/orgA/priceLists/l1'), { name: 'X' }));
    await assertFails(setDoc(doc(caj, 'companies/orgA/standardCosts/2026-11_p1'), { unitCost: 1 }));
    await assertFails(setDoc(doc(caj, 'companies/orgA/standardCostPeriods/2026-11'), { period: '2026-11' }));
    await assertFails(setDoc(doc(caj, 'companies/orgA/fixedBudget/f1'), { amount: 1 }));
    await assertFails(setDoc(doc(caj, 'companies/orgA/jobPositions/j1'), { title: 'X' }));
    await assertSucceeds(getDoc(doc(caj, 'companies/orgA/cashRegisters/r1')));
    const own = as('ownerA');
    await assertSucceeds(setDoc(doc(own, 'companies/orgA/cashRegisters/r1'), { name: 'Caja 1' }));
    await assertSucceeds(setDoc(doc(own, 'companies/orgA/priceLists/l1'), { name: 'X' }));
    await assertSucceeds(setDoc(doc(own, 'companies/orgA/standardCosts/2026-11_p1'), { unitCost: 1 }));
    await assertSucceeds(deleteDoc(doc(own, 'companies/orgA/cashRegisters/r1')));
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'users/posAdmin'),
      profile({ permissions: ['MANAGE_POS_CONFIG'], branchIds: ['b1'] })));
    await assertSucceeds(setDoc(doc(as('posAdmin'), 'companies/orgA/saleQuickGroups/g1'), { name: 'Pan Q0.50' }));
  });

  test('perfiles de facturación: solo la función', async () => {
    await assertFails(setDoc(doc(as('ownerA'), 'companies/orgA/felProfiles/p1'), { nit: '123' }));
    await assertSucceeds(getDoc(doc(as('ownerA'), 'companies/orgA/felProfiles/p1')));
  });
});

// ── Storage ────────────────────────────────────────────────────────────────

describe('storage', () => {
  const put = (uid, p) => env.authenticatedContext(uid).storage().ref(p).putString('hola');
  const read = (uid, p) => env.authenticatedContext(uid).storage().ref(p).getMetadata();

  test('personal sube y lee documentos de su empresa', async () => {
    await assertSucceeds(put('cajeraA', 'companies/orgA/employees/e1/dpi.pdf'));
    await assertSucceeds(read('cajeraA', 'companies/orgA/employees/e1/dpi.pdf'));
    await assertSucceeds(put('cajeraA', 'purchaseInvoices/orgA/p1.jpg'));
    await assertSucceeds(put('cajeraA', 'products/p1.jpg'));
    await assertSucceeds(put('cajeraA', 'recipes/r1/steps/s1.jpg'));
  });

  test('extraño, pendiente y otra empresa no tocan archivos', async () => {
    await assertSucceeds(put('cajeraA', 'companies/orgA/employees/e1/dpi.pdf'));
    await assertFails(read('stranger', 'companies/orgA/employees/e1/dpi.pdf'));
    await assertFails(read('ownerB', 'companies/orgA/employees/e1/dpi.pdf'));
    await assertFails(read('pendingA', 'companies/orgA/employees/e1/dpi.pdf'));
    await assertFails(put('stranger', 'products/p1.jpg'));
    await assertFails(put('stranger', 'otra/ruta.jpg'));
  });

  test('selfies de asistencia: solo la empresa dueña', async () => {
    await assertSucceeds(put('cajeraA', 'attendanceSelfies/orgA/e1/r1_in.jpg'));
    await assertSucceeds(read('cajeraA', 'attendanceSelfies/orgA/e1/r1_in.jpg'));
    await assertFails(read('ownerB', 'attendanceSelfies/orgA/e1/r1_in.jpg'));
    await assertFails(put('ownerB', 'attendanceSelfies/orgA/e1/r2_in.jpg'));
    await assertFails(put('stranger', 'attendanceSelfies/orgA/e1/r3_in.jpg'));
  });
});
