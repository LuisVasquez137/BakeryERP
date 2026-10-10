# Precios y costos — cómo funciona hoy y propuesta (oct 2026)

> Documento de discusión. Cuando se decida, lo aprobado pasa a `distribucion.md` / `pos-ventas.md`.

---

## 1. Cómo funcionan los precios hoy

### 1.1 Precio al público
- Cada producto tiene **un solo precio de venta** (`salePrice`), el que se pone en la pestaña
  General del producto.
- **La caja (POS) siempre cobra ese precio**, o el precio del grupo en el caso del "pan de Q0.50".
- La caja **no aplica descuentos por lista**: cualquier cliente que llega al mostrador paga
  precio público.

### 1.2 Precio de planta a sucursal
Existen **listas de precios**. Al entrar por primera vez se crean dos:

| Lista | Tipo | Qué hace |
|---|---|---|
| **Cliente final** | RETAIL | Es el precio público. No hay nada que configurar. |
| **Sucursales** | WHOLESALE (mayorista) | Lleva un **porcentaje sobre el precio público** (ej. 0.80 = la sucursal paga el 80%). También acepta un **precio fijo por producto** que tiene prioridad sobre el porcentaje. |

- Al **empacar un despacho**, cada línea guarda el precio de ese momento. Con ese valor entra el
  pan al inventario de la tienda.
- La diferencia entre el precio público y lo que pagó la sucursal **es su margen**.
- Si un producto **no tiene precio** en la lista, el despacho **no se puede cerrar**.
- Cada sucursal puede tener su propia lista (en el formulario de la sucursal); si no tiene, usa
  "Sucursales".

### 1.3 Revendedores
- Ya existen como **clientes externos** (tipo "Revendedor").
- Cada uno puede tener **su propia lista de precios**.
- Hay una pantalla donde el revendedor hace su pedido y ve sus precios.
- **Lo que falta: en caja no se le puede cobrar a un revendedor a su precio.**

### 1.4 Costo real del producto
- **No se calcula.**
- El precio de planta a sucursal **no es el costo**: es un porcentaje del precio público que
  alguien definió.
- Hay un **presupuesto de gastos fijos mensuales**, pero solo se muestra en el Home y **no se
  reparte entre los productos**.

---

## 2. Propuesta de reestructura

### 2.1 Separar dos números que hoy se confunden

**Precio de transferencia** (lo que paga la sucursal a la planta)
- Recomendación: **dejarlo como porcentaje fijo del precio público**, como está ahora.
- Por qué no amarrarlo al costo real: el precio a las sucursales cambiaría cada mes con la luz,
  la harina o una planilla con horas extra. La encargada no entendería por qué su margen se
  mueve, y **el personal no puede aprender un precio que cambia**.

**Costo real** (lo que de verdad le cuesta a la planta)
- Se calcula aparte, **solo como dato para el dueño**.
- Sirve para ver **cuánto gana la planta** en cada pan (precio de transferencia menos costo
  real) y para revisar si los porcentajes están bien puestos.
- Si un pan sale con margen negativo, se ajusta su precio a mano.

### 2.2 La escalera de precios

Cada pan tiene tres precios (los porcentajes son solo de ejemplo):

| Lista | Quién compra | Ejemplo |
|---|---|---|
| Cliente final | Mostrador | 100% |
| Revendedores | Revendedor de pan | ~85% |
| Sucursales | Tienda propia | ~75% |

Para un pan de Q1.00 serían Q1.00 al público, Q0.85 al revendedor y Q0.75 a la sucursal.

### 2.3 El costo real, en dos capas

**Capa 1 — Materia prima**
- Sale de la receta multiplicada por el **precio promedio de compra** de cada insumo.
- Esto ya es posible porque ya se registran las compras, que es lo que sí se está usando hoy.

**Capa 2 — Gastos de la planta**
- Se suman los gastos del mes: planilla de panaderos, renta de la planta, luz, gas,
  depreciación del horno, etc.
- Se reparten **por kilo de masa producida**:
  `total de gastos del mes ÷ kilos de masa del mes = Q por kilo`.
- Cada pan carga según su **peso en crudo**, que ya está registrado en los lotes.
- Repartir por kilo es más justo que por unidad: un pan de 120 g no gasta lo mismo que uno de 40 g.

**Ejemplo:**
- Gastos de planta del mes: Q60,000
- Masa producida en el mes: 20,000 kg
- Tasa: Q3.00 por kg = Q0.003 por gramo
- Un pan de 60 g en crudo carga Q0.18 de gastos + su materia prima.

**Importante — qué NO entra al costo del pan:**
- La renta de la tienda, las cajeras y la luz de la sucursal **no entran al costo del pan**.
- Son gastos de la sucursal y se restan de **su** margen.
- Si se meten al costo de planta, se castiga dos veces a la tienda.

### 2.4 Revendedores en caja
- Un botón **"Venta a revendedor"** en la caja:
  1. La cajera elige al revendedor.
  2. La caja cambia a **su lista de precios**.
  3. Cobra **contado o crédito** (la cuenta de crédito ya existe en el sistema).
- El pan sale del inventario de la tienda igual que una venta normal.
- Sus pedidos anticipados pueden venir después; lo primero es poder cobrarles bien hoy.

---

## 3. Orden propuesto

| Paso | Qué | Código |
|---|---|---|
| 1 | **Definir los porcentajes y crear la lista "Revendedores"** | Casi nada. Es lo que se necesita para capacitar al personal. |
| 2 | **Venta a revendedor en caja** | Medio |
| 3 | **Costo de materia prima por producto** | Medio. Se calcula con lo que ya se registra. |
| 4 | **Gastos fijos mensuales de planta y tasa por kilo** | Grande. Da el costo completo y un reporte de margen por pan (planta contra sucursal). |

---

## 4. Preguntas para arrancar el paso 1

1. ~~¿Qué porcentaje pagan las sucursales?~~ **Ya no aplica:** se decidió que la planta entrega a
   costo (ver `propuesta-rentabilidad-por-sucursal.md`, sección 3). Falta definir el precio de
   los revendedores.
2. **Respondida:** los revendedores compran **en las tiendas** (incluida la tienda de la planta),
   no en la planta. La venta queda en la tienda donde compraron.
3. **Respondida:** pagan **de contado** casi siempre. El fiado es raro → la venta a revendedor
   arranca solo de contado; el crédito queda para después.
