# Cuánto gana cada sucursal — propuesta (oct 2026)

> Documento de discusión. Complementa `propuesta-precios-y-costos.md`.

---

## 1. La idea central: el dinero puede seguir en un solo saco

Hoy todo el efectivo se junta y las facturas se pagan de la caja general. **Eso no hay que
cambiarlo.** Para saber cuánto gana cada sucursal no se necesita separar el dinero: se necesita
**anotar a quién le corresponde cada cosa**.

Cada venta, compra, gasto o pago tiene dos datos distintos:

| Dato | Pregunta | Ejemplo |
|---|---|---|
| **De dónde salió el dinero** | ¿Qué caja pagó? | Caja general |
| **A quién le corresponde** | ¿Para qué sucursal fue el gasto? | Pacajá |

La renta de Pacajá se puede pagar con la caja general, con dinero que vendió otra sucursal. No
importa: **se anota a Pacajá**. Así el saco común sigue igual y el sistema lleva la cuenta de
cada sucursal por separado.

> **Buena noticia:** el sistema ya guarda la sucursal en compras, gastos y colaboradores. La base
> existe; lo que falta es usarla con disciplina y armar el reporte.

---

## 2. Cada sucursal como si fuera un negocio aparte

Hay que imaginar que cada sucursal es un negocio pequeño que le compra el pan a la planta.

### 2.1 Tiendas (las 3 de venta)

```
  Ventas de la caja                       (mostrador + revendedores, que compran en la tienda)
− Pan recibido de las plantas             (a precio de transferencia, ya sale de los despachos)
− Productos de reventa                    (aguas, gaseosas, etc. comprados para esa tienda)
= MARGEN BRUTO
− Gastos propios de la tienda             (renta, cajeras, luz, agua, internet, bolsas, limpieza)
− Su parte del reparto                    (piloto + camión, por número de entregas)
= GANANCIA DE LA TIENDA
```

La **merma** (pan que no se vendió) ya va incluida: la tienda lo "compró" a la planta y no lo
cobró. Una tienda que pide de más se ve perdiendo, que es justo lo que se quiere detectar.

### 2.2 Plantas (pan y repostería)

```
  Lo que "le vendió" a las tiendas        (despachos a precio de transferencia)
− Materia prima                           (harina, azúcar, huevo... de las compras de la planta)
− Planilla de panaderos / reposteros
− Gastos propios de la planta             (renta, gas, luz, mantenimiento)
= GANANCIA DE LA PLANTA
```

**El reparto (sueldo del piloto + camión) NO va a la planta**: se reparte entre las tiendas que
reciben, por número de entregas (ver sección 7, pregunta 4).

### 2.3 Gastos generales (los que no son de nadie)

Hay gastos que no son de ninguna sucursal: el contador, el sueldo del dueño o de administración,
las licencias, el sistema, los intereses de un préstamo.

**Recomendación: NO repartirlos entre las sucursales.** Se dejan en un renglón aparte llamado
"Generales".

Por qué: si se reparten (por ejemplo, el contador entre 5), una tienda pequeña que sí gana con lo
suyo puede salir "perdiendo" solo por la parte que le tocó del contador. Y cerrarla no quita al
contador: el gasto se queda y se reparte entre las otras cuatro. Repartir los generales crea
perdedores falsos.

#### ¿Y entonces de dónde sale el dinero del contador y del sueldo del dueño?

**De la suma de lo que dejan todas las tiendas.** Lo que cada tienda "gana" en realidad es su
**aporte**: el dinero que le sobra después de pagar su pan y sus gastos, y que va al saco común
para cubrir los generales.

```
  Aporte Tienda planta      Q 18,000
+ Aporte Tienda A           Q 12,000
+ Aporte Tienda B           Q  9,000
+ Aporte Pacajá             Q -3,400     ← en vez de aportar, le quita al saco
+ Resultado de las plantas  Q    500     (diferencia contra el costo estándar)
= Total que entra al saco   Q 36,100
− Generales                 Q 25,000     (contador, sueldo del dueño, administración…)
= GANANCIA DE LA EMPRESA    Q 11,100
```

Leído así:
- **Cada tienda tiene un número claro**: cuánto aporta para pagar los generales.
- Pacajá no solo no aporta: las demás tiendas le están pagando la diferencia.
- Si los generales son Q25,000, **entre todas las tiendas tienen que aportar más de Q25,000**.
  Esa es la meta mensual del negocio, y se ve cuánto pone cada una.

Si alguna vez quieres ver "cuánto le tocaría" a cada tienda, se puede mostrar como dato
informativo (por ejemplo, en proporción a lo que vende), pero sin restarlo de su resultado.

#### El sueldo del dueño

Recomendación: **ponerte un sueldo fijo mensual** y anotarlo como gasto general, como si fueras
un empleado más (el administrador del negocio).

- Ese sueldo **sí es un gasto**: si no estuvieras, habría que pagarle a alguien para hacer tu
  trabajo. Si no lo anotas, el negocio se ve más rentable de lo que es.
- Lo que sacas **además** de tu sueldo, cuando sobra dinero, **no es gasto**: es **retiro de
  ganancias**. Se anota aparte y no afecta el resultado de nadie, porque es repartir lo que ya
  se ganó.

### 2.4 El reporte que se obtiene

| | Pan | Repostería | Tienda A | Tienda B | Pacajá | Generales | **Total** |
|---|---|---|---|---|---|---|---|
| Ganancia | +Q | +Q | +Q | +Q | **−Q** | −Q | **= lo que gana la empresa** |

**Regla de control:** la suma de todas las columnas tiene que dar lo mismo que la ganancia de la
empresa completa. Si no da, hay algo mal anotado.

---

## 3. El precio de planta a tienda: a COSTO, no inventado

> **Decisión (actualizada):** el dueño no quiere un porcentaje puesto a mano, porque se puede
> poner muy alto o muy bajo y el resultado de cada tienda dependería de ese número inventado.
> Esto **reemplaza** la recomendación del porcentaje fijo de `propuesta-precios-y-costos.md`.

### 3.1 La planta le entrega a las tiendas a su costo real

El precio de cada pan que sale de la planta es **lo que de verdad cuesta hacerlo**:

```
  Materia prima de la receta              (con el precio promedio de las compras)
+ Gastos de la planta por kilo de masa    (panaderos, renta, gas, luz… ÷ kilos del mes)
= COSTO DEL PAN = precio al que lo recibe la tienda
```

Consecuencias:
- **Nadie inventa el número.** Sale de las compras, las recetas y los gastos registrados.
- **La planta no gana ni pierde**: es un centro de costo. Su trabajo es producir barato, no vender.
- **Toda la ganancia aparece donde se vende el pan** (las tiendas y los revendedores), que es
  donde de verdad entra el dinero.
- Ahora sí tiene sentido mirar si una tienda **gana o pierde en números absolutos**, no solo
  compararla con las otras: si Pacajá sale en rojo, es que no alcanza a pagar el pan a lo que
  cuesta hacerlo más sus propios gastos.

### 3.2 El problema: el costo cambia cada mes

La luz, la harina y las horas extra cambian, y el costo real del mes **solo se conoce al
cerrar el mes**. No se puede esperar a fin de mes para ponerle precio a los despachos.

**Solución: costo estándar.**
1. Al empezar el mes, el sistema calcula el costo de cada pan con los datos del **mes anterior**
   (o el promedio de los últimos 3 meses, que es más estable).
2. Ese costo queda **fijo todo el mes**. Todos los despachos del mes usan ese mismo número.
3. Al cerrar el mes se compara lo que la planta gastó de verdad con lo que "cobró" a costo
   estándar. La diferencia se queda en la planta:
   - Gastó **más** de lo estándar → la planta sale en rojo ese mes: hubo desperdicio, horas extra
     o subió un insumo.
   - Gastó **menos** → la planta sale en verde: produjo más eficiente.

Así la planta sigue teniendo su número que vigilar (su eficiencia), pero sin que nadie haya
puesto un precio a ojo.

### 3.3 La tienda de la planta de pan

Se trata como **una sucursal más** que "le compra" a su propia planta al mismo costo estándar.
En el sistema son dos centros distintos: "Planta de pan" (produce) y "Tienda planta" (vende). Así
no se mezcla lo que se gana produciendo con lo que se gana vendiendo, y la tienda de la planta
se puede comparar de igual a igual con las otras tres.

### 3.4 Mientras no exista el cálculo de costo

El cálculo de costo (pasos 3 y 4 de `propuesta-precios-y-costos.md`) todavía no está hecho. Hasta
entonces los despachos siguen usando la lista "Sucursales" que ya existe. Esos números **no se
usan para decidir nada**; cuando el costo esté listo, la lista se reemplaza por el costo estándar.

---

## 4. Ejemplo con números (inventados)

**Pacajá, un mes:**

| Concepto | Monto |
|---|---|
| Ventas de caja | Q45,000 |
| Pan recibido de la planta (al 75%) | −Q36,000 |
| Reventa (aguas, gaseosas) | −Q2,000 |
| **Margen bruto** | **Q7,000** |
| Renta | −Q3,500 |
| 2 cajeras | −Q6,000 |
| Luz, agua, internet | −Q900 |
| **Ganancia de Pacajá** | **−Q3,400** |

Mirando de dónde viene el número:
- ¿Vende poco para lo que cuesta mantenerla?
- ¿Pide mucho pan y bota mucho? (si las ventas son Q45,000 al público y recibió pan que vale
  Q48,000 al público, Q3,000 se fueron a merma)
- ¿Tiene más cajeras de las que necesita por lo que vende?

Eso es lo que hoy no se puede ver porque todo cae al mismo saco.

---

## 5. Lo que hay que cambiar en la operación (no en el sistema)

Esto es lo que más pesa. El sistema no puede adivinar a quién le corresponde un gasto si nadie lo
anota.

1. **Toda compra y todo gasto se anota con su sucursal**, aunque se pague de la caja general.
   Una factura de luz de Pacajá → sucursal Pacajá. El contador → "Generales".
2. **Cada colaborador tiene su sucursal** en su ficha. El que trabaja en dos se pone en la
   que pasa más tiempo (no hace falta precisión de horas).
3. **Las tiendas usan la caja** para que las ventas queden registradas. Sin ventas registradas no
   hay reporte.
4. **El dinero que se recoge cada día se anota como "entregado a caja general"**, no
   desaparece. Así cuadra la caja de la tienda y se sabe cuánto recibió el saco común.
5. **Los despachos se siguen haciendo en el sistema**, que es lo que le pone el costo del pan a
   cada tienda.

---

## 6. Qué falta construir en el sistema

| # | Qué | Para qué |
|---|---|---|
| 1 | Opción **"Generales"** al anotar compras y gastos (además de las sucursales) | Separar lo que no es de nadie |
| 2 | **Gastos fijos mensuales por sucursal** (renta, internet) que se anotan solos cada mes | No depender de que alguien se acuerde |
| 3 | **Planilla cargada a la sucursal** de cada colaborador | El gasto más grande de cada tienda |
| 4 | **Reporte "Resultado por sucursal"** con la tabla de la sección 2.4, mes por mes | El objetivo final |
| 5 | Venta a revendedor en caja (de contado) | Que esas ventas entren a la tienda donde compraron, a su precio |

Orden sugerido: primero 1 (es pequeño y permite empezar a anotar bien desde ya), luego 4 con lo
que ya hay, y después 2, 3 y 5. **El reporte empieza a servir cuando las tiendas registran
ventas en caja**; antes de eso solo mostraría el lado de los costos.

---

## 7. Preguntas para afinar

1. ~~¿Las plantas también venden al público?~~ **Respondida:** la planta de pan sí tiene tienda →
   se trata como sucursal aparte (sección 3.3).
2. **Respondida:** la repostería **se despacha a todas las tiendas** igual que el pan → la planta
   de repostería es otro centro de costo con su propio costo estándar, mismo modelo.
3. **Respondida:** contador, sueldo del dueño, vehículo, publicidad y similares → **Generales**.
4. **Respondida:** el camión lleva pan y repostería y **solo reparte a las tiendas** (no vende a
   revendedores). **Decisión del dueño:** el sueldo del piloto (el que maneja) y los gastos del
   camión **se reparten entre las tiendas que reciben**, no se cargan a las plantas.
   Propuesta de regla: **por número de entregas del mes** (paradas de los repartos registrados):
   una entrega cuesta parecido lleve mucho o poco pan. La tienda de la planta no recibe reparto
   (está en la planta), así que no carga este gasto.
5. **Respondida:** **prueba de la caja esta semana en una sucursal.**

## 8. Prueba de la caja (semana del 12 oct 2026)

Lo que hay que tener listo antes de cobrar:
- Productos con precio público correcto (incluida la repostería) y categorías de caja armadas,
  incluido el grupo "pan de Q0.50".
- Caja(s) de la sucursal creadas con sus horarios de cuadre.
- Cajeras dadas de alta con su PIN.
- Los despachos a esa sucursal hechos en el sistema, para que haya inventario y el conteo de
  cierre cuadre.
- Que cuenten el pan todos los días: la venta del pan de grupo se reparte con el conteo de cierre.

**Ojo con los revendedores:** la caja hoy **no puede cobrar a precio de revendedor** (solo
existe el descuento de colaborador). Para la prueba conviene elegir una sucursal a la que casi
no lleguen revendedores, o construir "Venta a revendedor" esta misma semana.

---

## 9. Orden de implementación (todas las sucursales)

Se planea como si todas las tiendas fueran a usar la caja; no se sabe cuál empieza primero.

**Por qué este orden.** El costo de la planta necesita **un mes completo de gastos bien
anotados** para poder calcularse. Si se empieza por programar el cálculo, se termina y no hay
datos con qué correrlo. Por eso lo primero es empezar a anotar bien (barato y urgente), y
mientras esos datos se juntan se construye el resto.

### Paso 1 — Anotar cada gasto a su "dueño" (empezar YA) — HECHO 10 oct 2026, ver `caja-gastos.md` "CostCenter"
- El sistema ya separa **de dónde salió el dinero** (`paidFrom`: gaveta o caja general) de **la
  sucursal** (`branchId`). Falta:
  - Agregar **"Generales"** y **"Reparto"** como destinos, además de las sucursales.
  - Que compras y gastos **obliguen** a elegir destino (hoy puede quedar vacío).
  - Revisar que cada colaborador tenga su sucursal (panaderos → planta, piloto → Reparto,
    contador/administración → Generales).
- Operación: desde el día que salga, **toda factura se anota con su destino**.
- Resultado: a fin de mes ya hay datos reales para el paso 4.

### Paso 2 — Venta a revendedor en caja (antes de que cualquier tienda cobre) — HECHO 10 oct 2026, ver `pos-ventas.md` "Venta a revendedor"
- Botón "Venta a revendedor" en la caja: elegir revendedor → precios de su lista → contado.
- Lista "Revendedores" con sus precios.
- Va antes que el costo porque **cualquier tienda que empiece a cobrar lo necesita el primer
  día**, y los revendedores compran en todas.

### Paso 3 — Costo de materia prima por producto (pan y repostería) — HECHO (cálculo y pantalla) 10 oct 2026, ver `reportes.md` "Costo por producto"; falta la carga de datos de repostería
- Precio promedio de cada insumo, sacado de las compras.
- Receta × precio = costo de materia prima de cada masa → por pan, según su peso en crudo.
- Pantalla "Costo por producto" con alertas: productos sin receta, insumos sin precio.
- **Trabajo de datos**: cada producto (incluida la repostería) necesita receta y rendimiento
  cargados. Probablemente sea lo que más tiempo lleve, más que el código.
- Ya sirve solo: muestra cuánto de cada precio se va en materia prima.

### Paso 4 — Gastos fijos mensuales y costo estándar de planta — HECHO (código) 10 oct 2026, ver `reportes.md` "Costo estándar" y `caja-gastos.md` "Planta con tienda"; primer mes real = noviembre con datos de octubre
- **Agregado al plan:** la tienda de cada planta se separa también en gastos y personas (Planta /
  Tienda / Local compartido con % fijo de la tienda); si no, la cajera entraría al costo del pan.
- Gastos que se repiten cada mes (renta, internet, planilla) se anotan solos con su destino.
- Al cerrar el mes: gastos de cada planta ÷ kilos de masa producidos = tasa por kilo.
- **Costo estándar** de cada producto = materia prima + tasa × peso en crudo. Se congela para
  el mes siguiente.
- Los despachos dejan de usar la lista "Sucursales" y usan el costo estándar.

### Paso 5 — Reparto
- Sueldo del piloto + camión del mes ÷ entregas del mes → se carga a cada tienda según las
  entregas que recibió (sale de los repartos registrados).

### Paso 6 — Reporte "Resultado por sucursal"
- La tabla de la sección 2.4, mes por mes: aporte de cada tienda, resultado de cada planta
  contra su costo estándar, generales y ganancia total.
- Regla de control: la suma de las columnas = ganancia de la empresa.

### Decidido: la tienda de la planta de pan → opción A (separarla solo en el reporte)
En el sistema la planta de pan es **una sola sucursal** que produce y vende. Hay dos formas de
separarla:
- **A. Separarla en el reporte (recomendado).** Las ventas de su caja se cuentan como "Tienda
  planta" y su costo es lo vendido × costo estándar. **No cambia nada en la operación.**
- **B. Crearla como sucursal aparte** y "despacharle" el pan cada día desde la planta. Más
  exacto en inventario, pero agrega trabajo diario a la gente de la planta.
