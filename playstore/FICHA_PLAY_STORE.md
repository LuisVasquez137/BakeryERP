# Ficha de Google Play — BakeOS

Todo listo para copiar/pegar en Play Console → Presencia en la tienda → Ficha principal.
Idioma de la ficha: **Español (es-419 / Latinoamérica)**.

---

## 1. Datos básicos

| Campo | Valor |
|---|---|
| Nombre de la app (máx. 30) | `BakeOS — Gestión de Panadería` (29 ✓) |
| Alternativa corta | `BakeOS: ERP para Panaderías` (27 ✓) |
| Desarrollador | Luis Fernando Vásquez |
| Categoría de la app | Empresas (Business) |
| Etiquetas | Productividad, Inventario, Punto de venta |
| Correo de contacto | luisvasquez1307@gmail.com |
| Sitio web (opcional) | https://luisvasquez137.github.io/BakeryERP/ |
| Política de privacidad | https://luisvasquez137.github.io/BakeryERP/privacy-policy.html |

---

## 2. Descripción breve (máx. 80 caracteres)

Elige una. El contador está al final de cada línea.

```
ERP para panaderías: recetas, producción, inventario, despacho y reportes.   (74 ✓)
```
Alternativas:
```
Gestiona tu panadería: recetas, producción, stock por sucursal y reparto.    (73 ✓)
Controla recetas, producción, inventario y ventas de tu panadería artesanal. (76 ✓)
```

---

## 3. Descripción completa (máx. 4000 caracteres)

```
BakeOS es el sistema operativo de tu panadería: una sola app para gestionar todo el negocio, desde la receta hasta la venta en mostrador.

Pensado para panaderías artesanales con una o varias sucursales, BakeOS conecta la producción de la planta con el inventario y las ventas de cada tienda, para que sepas en todo momento qué se produjo, qué se despachó y qué se vendió.

▸ RECETAS COMO LAS PIENSA EL PANADERO
Crea recetas en porcentaje del panadero o por peso real. Soporta varias harinas base, pasos con foto y los productos que salen de cada masa. Las esponjas y preparaciones se gestionan aparte y se declaran al momento de producir.

▸ PRODUCCIÓN POR LOTE Y TURNO
Las sucursales envían pedidos de producción a la planta. El supervisor los consolida en lotes por turno y fecha. El panadero abre su "Lista de Producción", decide la esponja por producto, ve la receta ya escalada a la cantidad pedida y, al terminar, descuenta los insumos automáticamente (Kardex).

▸ INVENTARIO POR SUCURSAL
Stock de insumos y de producto terminado independiente por cada sucursal, con costo promedio ponderado. Cada compra, producción, venta o merma queda registrada en el Kardex.

▸ COMPRAS Y PROVEEDORES
Registra compras por sucursal con sus formatos de presentación; el stock y el costo se actualizan solos. Administra tus proveedores en un solo lugar.

▸ DESPACHO Y REPARTO
Al cerrar un lote se generan las notas de despacho automáticamente. Empaque, salida a reparto, acuse de llegada y recepción con conteo final (faltantes, daños o sobrantes). Agrupa despachos en hojas de reparto por repartidor y vehículo.

▸ LISTAS DE PRECIOS
Precios al detalle y mayoreo con factor por lista o precio fijo por producto. Cada despacho guarda el precio histórico para auditoría.

▸ PUNTO DE VENTA Y CAJA
Ventas en mostrador, apertura y cierre de caja con arqueo.

▸ ACTIVOS FIJOS
Inventario valorado de maquinaria, mobiliario y vehículos, con depreciación, historial y avisos de mantenimiento.

▸ EMPLEADOS
Perfiles, documentos, historial y reloj de turno biométrico en modo tablet.

▸ REPORTES Y ALERTAS
Panel de inicio con datos vivos: ventas de los últimos días, alertas de stock bajo, mantenimientos próximos, despachos sin acuse y más. Reportes de ventas, producción y financiero.

▸ MULTI-EMPRESA Y PERMISOS
Cada negocio gestiona sus sucursales y usuarios con roles y permisos detallados (dueño, administrador, encargado, panadero, repartidor, cajero).

BakeOS funciona en la nube con Firebase: tus datos se sincronizan entre dispositivos en tiempo real.

Diseñado junto a panaderos reales para que refleje cómo se trabaja de verdad en una panadería.
```

---

## 4. Recursos gráficos (carpeta /playstore)

| Recurso | Tamaño | Archivo | Estado |
|---|---|---|---|
| Ícono de la app | 512×512 PNG | `app_icon_512x512.png` | ✅ Listo |
| Gráfico de funciones (Feature) | 1024×500 PNG | `feature_graphic_1024x500.png` | ✅ Listo |
| Capturas de teléfono | mín. 2 (recom. 4–8), 9:16 | `screenshots/` | ⏳ Falta tomar |

### Cómo generar las capturas enmarcadas
1. Abre la app en el emulador o tu teléfono.
2. Toma capturas de las mejores pantallas (Receta, Lista de Producción, Inventario, Despacho, Reportes, Home).
3. Guárdalas en `playstore/raw_screenshots/` nombradas así: `01 - Recetas del panadero.png`, `02 - Producción por lote.png`, etc.
4. Ejecuta: `python playstore/frame_screenshots.py`
5. Sube los archivos de `playstore/screenshots/` a Play Console.

Títulos sugeridos para las capturas:
- Recetas en porcentaje del panadero
- Producción por lote y turno
- Inventario por sucursal en tiempo real
- Despacho y reparto controlados
- Reportes y alertas en tu inicio
- Listas de precios al detalle y mayoreo

---

## 5. Clasificación de contenido
- Cuestionario: app de tipo **Empresas / Productividad**, sin contenido sensible.
- Resultado esperado: **Para todos / PEGI 3**.

## 6. Seguridad de los datos (Data safety)
Declarar lo que recolecta la app:
- **Datos personales:** nombre, correo (autenticación) — recolectados, cifrados en tránsito.
- **Datos del negocio:** recetas, inventario, ventas (almacenados en Firebase del usuario).
- No se comparten con terceros para publicidad.
- El usuario puede solicitar eliminación: https://luisvasquez137.github.io/BakeryERP/account-deletion.html

## 7. Público objetivo
- Edad objetivo: 18+ (herramienta de negocio).
- No dirigida a niños.
```
