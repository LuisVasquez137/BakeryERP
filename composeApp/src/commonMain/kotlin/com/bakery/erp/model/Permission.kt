package com.bakery.erp.model

enum class Permission {
    // Catálogo
    VIEW_PRODUCTS, EDIT_PRODUCTS,
    VIEW_INGREDIENTS, EDIT_INGREDIENTS,
    VIEW_RECIPES, EDIT_RECIPES,
    VIEW_SUPPLIERS, EDIT_SUPPLIERS,

    // Gastos
    VIEW_EXPENSES,
    ADD_VARIABLE_EXPENSE,
    MANAGE_FIXED_BUDGET,

    // Compras
    RECEIVE_STOCK,

    // Administración
    VIEW_ALL_BRANCHES,
    MANAGE_BRANCHES,
    MANAGE_USERS,

    // Producción
    VIEW_PRODUCTION,
    SEND_PRODUCTION_ORDER,
    MANAGE_PRODUCTION_LOTS,
    MANAGE_PRODUCTION_SHIFTS,

    // Preparaciones
    VIEW_PREPARATIONS,
    EDIT_PREPARATIONS,
    MANAGE_PREPARATION_BATCHES,

    // Colaboradores
    MANAGE_EMPLOYEES,

    // Activos Fijos
    VIEW_ASSETS, EDIT_ASSETS, MANAGE_ASSETS, ADD_ASSET_HISTORY,

    // Precios
    MANAGE_PRICE_LISTS,

    // Distribución
    DISPATCH_PACK,
    MANAGE_DELIVERY_RUNS,
    DRIVE_DELIVERY,
    RECEIVE_DISPATCH,
    MANAGE_FINISHED_STOCK,
    VIEW_DISPATCH_VALUE,

    // Ventas (POS)
    OPERATE_POS,
    VIEW_SALES,
    MANAGE_POS_CONFIG,
    VIEW_SALES_VALUE,
    VOID_SALE,
    MANAGE_DAILY_CASH_CLOSE,
    SPOT_CHECK_CASH,

    // Conteo de pan (cierre de día)
    COUNT_FINISHED_STOCK,
    VIEW_BREAD_COUNTS,

    // Reportes
    VIEW_OPERATIONS_REPORT,
    VIEW_SALES_REPORT,
    VIEW_PRODUCTION_REPORT,
    VIEW_FINANCIAL_REPORT,
    VIEW_INVENTORY_REPORT,

    // Pedidos de Clientes
    VIEW_CUSTOMER_ORDERS,
    CREATE_RESELLER_ORDER,
    CREATE_RETAIL_SPECIAL_ORDER,
    CREATE_CAKE_ORDER,
    MANAGE_CUSTOMER_ORDERS,
    MANAGE_EXTERNAL_CUSTOMERS,
    COLLECT_ORDER_PAYMENT;

    val label: String get() = when (this) {
        VIEW_PRODUCTS -> "Ver productos"
        EDIT_PRODUCTS -> "Editar productos"
        VIEW_INGREDIENTS -> "Ver ingredientes"
        EDIT_INGREDIENTS -> "Editar ingredientes"
        VIEW_RECIPES -> "Ver recetas"
        EDIT_RECIPES -> "Editar recetas"
        VIEW_SUPPLIERS -> "Ver proveedores"
        EDIT_SUPPLIERS -> "Editar proveedores"
        VIEW_EXPENSES -> "Ver gastos"
        ADD_VARIABLE_EXPENSE -> "Ingresar gastos variables"
        MANAGE_FIXED_BUDGET -> "Gestionar presupuesto fijo"
        RECEIVE_STOCK -> "Ingresar materia prima"
        VIEW_ALL_BRANCHES -> "Ver todas las sucursales"
        MANAGE_BRANCHES -> "Gestionar sucursales"
        MANAGE_USERS -> "Gestionar usuarios"
        VIEW_PRODUCTION -> "Ver producción"
        SEND_PRODUCTION_ORDER -> "Enviar pedidos de producción"
        MANAGE_PRODUCTION_LOTS -> "Gestionar lotes de producción"
        MANAGE_PRODUCTION_SHIFTS -> "Configurar turnos de producción"
        VIEW_PREPARATIONS -> "Ver preparaciones"
        EDIT_PREPARATIONS -> "Editar preparaciones"
        MANAGE_PREPARATION_BATCHES -> "Producir lotes de preparación"
        MANAGE_EMPLOYEES -> "Gestionar colaboradores"
        VIEW_ASSETS -> "Ver activos fijos"
        EDIT_ASSETS -> "Crear y editar activos"
        MANAGE_ASSETS -> "Eliminar, dar de baja y transferir"
        ADD_ASSET_HISTORY -> "Registrar mantenimientos y reparaciones"
        MANAGE_PRICE_LISTS -> "Gestionar listas de precios"
        DISPATCH_PACK -> "Empacar despachos en planta"
        MANAGE_DELIVERY_RUNS -> "Gestionar hojas de reparto"
        DRIVE_DELIVERY -> "Repartidor"
        RECEIVE_DISPATCH -> "Recibir despachos en sucursal"
        MANAGE_FINISHED_STOCK -> "Ajustar inventario de producto terminado"
        VIEW_DISPATCH_VALUE -> "Ver valor monetario en despachos"
        OPERATE_POS -> "Operar punto de venta"
        VIEW_SALES -> "Ver historial de ventas"
        MANAGE_POS_CONFIG -> "Configurar cajas y grupos rápidos"
        VIEW_SALES_VALUE -> "Ver montos en historial de ventas"
        VOID_SALE -> "Anular ventas"
        MANAGE_DAILY_CASH_CLOSE -> "Cerrar el día por sucursal"
        SPOT_CHECK_CASH -> "Cuadre sorpresa de caja"
        COUNT_FINISHED_STOCK -> "Conteo de pan al cierre"
        VIEW_BREAD_COUNTS -> "Ver conteos de pan"
        VIEW_OPERATIONS_REPORT -> "Ver dashboard operativo"
        VIEW_SALES_REPORT -> "Ver reporte de ventas"
        VIEW_PRODUCTION_REPORT -> "Ver reporte de producción"
        VIEW_FINANCIAL_REPORT -> "Ver reporte financiero"
        VIEW_INVENTORY_REPORT -> "Ver reporte de inventario"
        VIEW_CUSTOMER_ORDERS -> "Ver pedidos de clientes"
        CREATE_RESELLER_ORDER -> "Crear pedido de revendedor"
        CREATE_RETAIL_SPECIAL_ORDER -> "Crear encargo de cliente"
        CREATE_CAKE_ORDER -> "Crear encargo de pastel"
        MANAGE_CUSTOMER_ORDERS -> "Confirmar y gestionar pedidos de clientes"
        MANAGE_EXTERNAL_CUSTOMERS -> "Gestionar clientes externos y revendedores"
        COLLECT_ORDER_PAYMENT -> "Cobrar anticipos y saldos de pedidos"
    }

    val description: String get() = when (this) {
        VIEW_PRODUCTS -> "Puede explorar el catálogo de productos"
        EDIT_PRODUCTS -> "Puede crear y editar productos"
        VIEW_INGREDIENTS -> "Puede explorar el catálogo de ingredientes y ver el stock"
        EDIT_INGREDIENTS -> "Puede crear y editar ingredientes"
        VIEW_RECIPES -> "Puede ver las recetas y sus ingredientes"
        EDIT_RECIPES -> "Puede crear y editar recetas"
        VIEW_SUPPLIERS -> "Puede ver el listado de proveedores"
        EDIT_SUPPLIERS -> "Puede crear y editar proveedores"
        VIEW_EXPENSES -> "Puede ver los gastos variables y presupuesto fijo"
        ADD_VARIABLE_EXPENSE -> "Puede registrar gastos variables"
        MANAGE_FIXED_BUDGET -> "Puede configurar y editar el presupuesto fijo"
        RECEIVE_STOCK -> "Puede registrar compras e ingreso de materia prima"
        VIEW_ALL_BRANCHES -> "Puede ver todas las sucursales de la empresa"
        MANAGE_BRANCHES -> "Puede crear, editar y eliminar sucursales"
        MANAGE_USERS -> "Puede gestionar usuarios y asignar permisos"
        VIEW_PRODUCTION -> "Puede ver pedidos y lotes de producción"
        SEND_PRODUCTION_ORDER -> "Puede crear pedidos de producción para las plantas"
        MANAGE_PRODUCTION_LOTS -> "Puede crear, ejecutar y completar lotes de producción"
        MANAGE_PRODUCTION_SHIFTS -> "Puede configurar los turnos de producción por planta"
        VIEW_PREPARATIONS -> "Puede ver las preparaciones (concha, rellenos, etc.)"
        EDIT_PREPARATIONS -> "Puede crear y editar preparaciones"
        MANAGE_PREPARATION_BATCHES -> "Puede producir lotes de preparación y descontar inventario"
        MANAGE_EMPLOYEES -> "Puede gestionar la información de los colaboradores"
        VIEW_ASSETS -> "Puede ver el inventario de activos fijos de la empresa"
        EDIT_ASSETS -> "Puede crear y editar activos fijos"
        MANAGE_ASSETS -> "Puede eliminar, dar de baja, vender y transferir activos"
        ADD_ASSET_HISTORY -> "Puede registrar mantenimientos, reparaciones y demás eventos en el historial de un activo"
        MANAGE_PRICE_LISTS -> "Puede crear, editar y eliminar listas de precios y definir precios por canal"
        DISPATCH_PACK -> "Puede armar y cerrar despachos en planta (contar y empacar producto)"
        MANAGE_DELIVERY_RUNS -> "Puede crear hojas de reparto y asignar repartidor y vehículo"
        DRIVE_DELIVERY -> "Puede ver y completar las hojas de reparto que le sean asignadas"
        RECEIVE_DISPATCH -> "Puede recibir y verificar despachos en sucursal de venta"
        MANAGE_FINISHED_STOCK -> "Puede registrar mermas y ajustes al inventario de producto terminado en sucursal"
        VIEW_DISPATCH_VALUE -> "Puede ver el precio unitario y el total monetario de las notas de despacho"
        OPERATE_POS -> "Puede abrir caja, registrar ventas y cerrar la sesión de caja al final del turno"
        VIEW_SALES -> "Puede consultar el historial de ventas y los detalles de cada transacción"
        MANAGE_POS_CONFIG -> "Puede configurar cajas registradoras y grupos rápidos de venta por precio"
        VIEW_SALES_VALUE -> "Puede ver los montos monetarios en el historial de ventas (sin este permiso solo ve el conteo)"
        VOID_SALE -> "Puede anular ventas ya cobradas (Fase 2 — revierte stock y caja)"
        MANAGE_DAILY_CASH_CLOSE -> "Puede hacer el cierre consolidado del día por sucursal: define cuánto se deja para mañana y cuánto se lleva el owner"
        SPOT_CHECK_CASH -> "Puede hacer cuadres sorpresa a cualquier caja abierta sin cerrarla (auditoría en caliente para owners y supervisores)"
        COUNT_FINISHED_STOCK -> "Puede contar físicamente el pan al cierre del día y aplicar mermas automáticas por las diferencias"
        VIEW_BREAD_COUNTS -> "Puede consultar el historial de conteos de pan y sus mermas"
        VIEW_OPERATIONS_REPORT -> "Puede ver el dashboard del Home con KPIs operativos en vivo y banner de alertas accionables"
        VIEW_SALES_REPORT -> "Puede consultar el reporte formal de ventas: ingresos por día, método de pago, cajero, producto y hora"
        VIEW_PRODUCTION_REPORT -> "Puede consultar el reporte formal de producción: planeado vs producido, eficiencia, mermas en tránsito y discrepancias"
        VIEW_FINANCIAL_REPORT -> "Puede consultar el estado financiero del mes: ingresos, gastos, planilla, utilidad operativa y margen"
        VIEW_INVENTORY_REPORT -> "Puede consultar el reporte de inventario: rotación, mermas valoradas y movimientos por sucursal"
        VIEW_CUSTOMER_ORDERS -> "Puede ver el listado y detalle de pedidos de clientes (revendedores, encargos especiales, pasteles)"
        CREATE_RESELLER_ORDER -> "Puede crear pedidos para tiendas revendedoras con lista mayorista"
        CREATE_RETAIL_SPECIAL_ORDER -> "Puede crear encargos especiales de cliente final (panes especiales, cantidades grandes)"
        CREATE_CAKE_ORDER -> "Puede crear encargos de pastel con especificaciones de sabor, relleno, decoración y anticipo"
        MANAGE_CUSTOMER_ORDERS -> "Puede confirmar, editar y cancelar pedidos de clientes; consolidarlos en lotes de producción"
        MANAGE_EXTERNAL_CUSTOMERS -> "Puede crear y editar la agenda de clientes externos y revendedores"
        COLLECT_ORDER_PAYMENT -> "Puede registrar anticipos, depósitos y cobros del saldo de un pedido (en POS o directo)"
    }

    val group: String get() = when (this) {
        VIEW_PRODUCTS, EDIT_PRODUCTS,
        VIEW_RECIPES, EDIT_RECIPES -> "Catálogo"
        VIEW_INGREDIENTS, EDIT_INGREDIENTS,
        VIEW_SUPPLIERS, EDIT_SUPPLIERS -> "Insumos"
        VIEW_EXPENSES, ADD_VARIABLE_EXPENSE,
        MANAGE_FIXED_BUDGET -> "Gastos"
        RECEIVE_STOCK -> "Compras"
        VIEW_ALL_BRANCHES, MANAGE_BRANCHES,
        MANAGE_USERS -> "Administración"
        VIEW_PRODUCTION, SEND_PRODUCTION_ORDER,
        MANAGE_PRODUCTION_LOTS, MANAGE_PRODUCTION_SHIFTS -> "Producción"
        VIEW_PREPARATIONS, EDIT_PREPARATIONS,
        MANAGE_PREPARATION_BATCHES -> "Preparaciones"
        MANAGE_EMPLOYEES -> "Administración"
        VIEW_ASSETS, EDIT_ASSETS,
        MANAGE_ASSETS, ADD_ASSET_HISTORY -> "Activos Fijos"
        MANAGE_PRICE_LISTS -> "Catálogo"
        DISPATCH_PACK, MANAGE_DELIVERY_RUNS,
        DRIVE_DELIVERY, RECEIVE_DISPATCH,
        MANAGE_FINISHED_STOCK, VIEW_DISPATCH_VALUE -> "Distribución"
        OPERATE_POS, VIEW_SALES, MANAGE_POS_CONFIG,
        VIEW_SALES_VALUE, VOID_SALE,
        MANAGE_DAILY_CASH_CLOSE, SPOT_CHECK_CASH -> "Ventas (POS)"
        COUNT_FINISHED_STOCK, VIEW_BREAD_COUNTS -> "Distribución"
        VIEW_OPERATIONS_REPORT, VIEW_SALES_REPORT,
        VIEW_PRODUCTION_REPORT, VIEW_FINANCIAL_REPORT,
        VIEW_INVENTORY_REPORT -> "Reportes"
        VIEW_CUSTOMER_ORDERS, CREATE_RESELLER_ORDER,
        CREATE_RETAIL_SPECIAL_ORDER, CREATE_CAKE_ORDER,
        MANAGE_CUSTOMER_ORDERS, MANAGE_EXTERNAL_CUSTOMERS,
        COLLECT_ORDER_PAYMENT -> "Pedidos de Clientes"
    }
}
