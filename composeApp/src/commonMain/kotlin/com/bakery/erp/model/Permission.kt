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
    ADD_GENERAL_EXPENSE,
    MANAGE_FIXED_BUDGET,

    // Compras
    RECEIVE_STOCK,
    MANAGE_PURCHASE_PAYMENTS,
    EDIT_PURCHASES,
    DELETE_PURCHASES,

    // Administración
    VIEW_ALL_BRANCHES,
    MANAGE_BRANCHES,
    MANAGE_USERS,

    // Producción
    VIEW_PRODUCTION,
    SEND_PRODUCTION_ORDER,
    MANAGE_PRODUCTION_LOTS,
    MANAGE_PRODUCTION_SHIFTS,
    /**
     * Sacar materia prima de bodega para el trabajo de planta (ver [PlantIssue]).
     *
     * Permiso propio y no reuso de EDIT_INGREDIENTS ni RECEIVE_STOCK a propósito: el
     * punto entero del módulo es que el panadero anote su saco de harina sin poder
     * además editar el catálogo ni ajustar el stock a mano.
     */
    ISSUE_PLANT_MATERIAL,

    // Preparaciones
    VIEW_PREPARATIONS,
    EDIT_PREPARATIONS,
    MANAGE_PREPARATION_BATCHES,

    // Colaboradores
    MANAGE_EMPLOYEES,
    /**
     * Ver y editar cuánto gana cada quien (salario, nómina, rangos de los puestos). Aparte de
     * MANAGE_EMPLOYEES a propósito: la encargada registra a la gente de su sucursal sin
     * enterarse del sueldo de nadie.
     */
    VIEW_EMPLOYEE_SALARY,
    /**
     * Una por pestaña de la ficha del colaborador. MANAGE_EMPLOYEES solo da la lista, el
     * Perfil y el alta/edición; lo demás se reparte para que no todos vean todo (sus
     * papeles, sus faltas, lo que debe).
     */
    VIEW_EMPLOYEE_ATTENDANCE,
    VIEW_EMPLOYEE_DOCUMENTS,
    VIEW_EMPLOYEE_HISTORY,
    VIEW_EMPLOYEE_CREDIT,
    /** Ver la pestaña Acceso (PIN y permisos). Cambiar algo ahí sigue siendo solo OWNER/ADMIN. */
    VIEW_EMPLOYEE_ACCESS,

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
    /** Bitácora de cajas: aperturas, cierres y entradas al cierre fuera de horario. */
    VIEW_CASH_AUDIT,
    /** Retirar efectivo de la gaveta al cerrar el turno. */
    WITHDRAW_AT_CLOSE,

    // Conteo de pan (cierre de día)
    COUNT_FINISHED_STOCK,
    VIEW_BREAD_COUNTS,

    // Reportes
    VIEW_OPERATIONS_REPORT,
    VIEW_SALES_REPORT,
    VIEW_PRODUCTION_REPORT,
    VIEW_FINANCIAL_REPORT,
    VIEW_INVENTORY_REPORT,
    VIEW_BRANCH_SUPPLY_REPORT,

    // Pedidos de Clientes
    VIEW_CUSTOMER_ORDERS,
    CREATE_RESELLER_ORDER,
    CREATE_RETAIL_SPECIAL_ORDER,
    CREATE_CAKE_ORDER,
    MANAGE_CUSTOMER_ORDERS,
    MANAGE_EXTERNAL_CUSTOMERS,
    COLLECT_ORDER_PAYMENT,

    // Créditos
    SELL_ON_CREDIT,
    COLLECT_CREDIT_PAYMENT,
    GIVE_EMPLOYEE_ADVANCE,
    MANAGE_CREDIT_ACCOUNTS;

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
        ADD_VARIABLE_EXPENSE -> "Anotar gastos de su caja"
        ADD_GENERAL_EXPENSE -> "Anotar gastos de caja general"
        MANAGE_FIXED_BUDGET -> "Gestionar presupuesto fijo"
        RECEIVE_STOCK -> "Ingresar materia prima"
        MANAGE_PURCHASE_PAYMENTS -> "Registrar pagos a proveedores"
        EDIT_PURCHASES -> "Editar compras registradas"
        DELETE_PURCHASES -> "Eliminar compras"
        VIEW_ALL_BRANCHES -> "Ver todas las sucursales"
        MANAGE_BRANCHES -> "Gestionar sucursales"
        MANAGE_USERS -> "Gestionar usuarios"
        VIEW_PRODUCTION -> "Ver producción"
        SEND_PRODUCTION_ORDER -> "Enviar pedidos de producción"
        MANAGE_PRODUCTION_LOTS -> "Gestionar lotes de producción"
        MANAGE_PRODUCTION_SHIFTS -> "Configurar turnos de producción"
        ISSUE_PLANT_MATERIAL -> "Anotar material de planta"
        VIEW_PREPARATIONS -> "Ver preparaciones"
        EDIT_PREPARATIONS -> "Editar preparaciones"
        MANAGE_PREPARATION_BATCHES -> "Producir lotes de preparación"
        MANAGE_EMPLOYEES -> "Gestionar colaboradores"
        VIEW_EMPLOYEE_SALARY -> "Ver y editar salarios"
        VIEW_EMPLOYEE_ATTENDANCE -> "Ver asistencia de colaboradores"
        VIEW_EMPLOYEE_DOCUMENTS -> "Ver y adjuntar documentos de colaboradores"
        VIEW_EMPLOYEE_HISTORY -> "Ver historial de colaboradores"
        VIEW_EMPLOYEE_CREDIT -> "Ver créditos de colaboradores"
        VIEW_EMPLOYEE_ACCESS -> "Ver acceso (PIN) de colaboradores"
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
        VIEW_CASH_AUDIT -> "Bitácora de cajas"
        WITHDRAW_AT_CLOSE -> "Retirar efectivo al cerrar caja"
        COUNT_FINISHED_STOCK -> "Conteo de pan al cierre"
        VIEW_BREAD_COUNTS -> "Ver conteos de pan"
        VIEW_OPERATIONS_REPORT -> "Ver dashboard operativo"
        VIEW_SALES_REPORT -> "Ver reporte de ventas"
        VIEW_PRODUCTION_REPORT -> "Ver reporte de producción"
        VIEW_FINANCIAL_REPORT -> "Ver reporte financiero"
        VIEW_INVENTORY_REPORT -> "Ver reporte de inventario"
        VIEW_BRANCH_SUPPLY_REPORT -> "Ver pedido vs. recibido"
        VIEW_CUSTOMER_ORDERS -> "Ver pedidos de clientes"
        CREATE_RESELLER_ORDER -> "Crear pedido de revendedor"
        CREATE_RETAIL_SPECIAL_ORDER -> "Crear encargo de cliente"
        CREATE_CAKE_ORDER -> "Crear encargo de pastel"
        MANAGE_CUSTOMER_ORDERS -> "Confirmar y gestionar pedidos de clientes"
        MANAGE_EXTERNAL_CUSTOMERS -> "Gestionar clientes externos y revendedores"
        COLLECT_ORDER_PAYMENT -> "Cobrar anticipos y saldos de pedidos"
        SELL_ON_CREDIT -> "Vender a crédito"
        COLLECT_CREDIT_PAYMENT -> "Cobrar créditos en caja"
        GIVE_EMPLOYEE_ADVANCE -> "Dar adelantos a colaboradores"
        MANAGE_CREDIT_ACCOUNTS -> "Administrar cuentas de crédito"
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
        ADD_VARIABLE_EXPENSE -> "Puede anotar lo que se paga desde la caja que está operando: sale de su gaveta y le tiene que faltar al cuadrar"
        ADD_GENERAL_EXPENSE -> "Puede anotar gastos que NO salen de ninguna gaveta — caja general o del bolsillo del dueño. Es el acceso directo de Operaciones y el de Gastos"
        MANAGE_FIXED_BUDGET -> "Puede configurar y editar el presupuesto fijo"
        RECEIVE_STOCK -> "Puede registrar compras e ingreso de materia prima"
        MANAGE_PURCHASE_PAYMENTS -> "Puede abonar y saldar facturas de proveedor"
        EDIT_PURCHASES -> "Puede corregir una compra ya registrada; el inventario se ajusta por diferencia"
        DELETE_PURCHASES -> "Puede eliminar una compra y revertir su ingreso al inventario"
        VIEW_ALL_BRANCHES -> "Puede ver todas las sucursales de la empresa"
        MANAGE_BRANCHES -> "Puede crear, editar y eliminar sucursales"
        MANAGE_USERS -> "Puede gestionar usuarios y asignar permisos"
        VIEW_PRODUCTION -> "Puede ver pedidos y lotes de producción"
        SEND_PRODUCTION_ORDER -> "Puede crear pedidos de producción para las plantas"
        MANAGE_PRODUCTION_LOTS -> "Puede crear, ejecutar y completar lotes de producción"
        MANAGE_PRODUCTION_SHIFTS -> "Puede configurar los turnos de producción por planta"
        ISSUE_PLANT_MATERIAL -> "Puede anotar el material que se usa en planta fuera de las recetas (harina para enharinar, manteca para engrasar) y descontarlo del inventario con un motivo"
        VIEW_PREPARATIONS -> "Puede ver las preparaciones (concha, rellenos, etc.)"
        EDIT_PREPARATIONS -> "Puede crear y editar preparaciones"
        MANAGE_PREPARATION_BATCHES -> "Puede producir lotes de preparación y descontar inventario"
        MANAGE_EMPLOYEES -> "Puede gestionar la información de los colaboradores"
        VIEW_EMPLOYEE_SALARY -> "Puede ver y editar el salario de los colaboradores, su nómina y los rangos de salario de los puestos"
        VIEW_EMPLOYEE_ATTENDANCE -> "Puede ver la pestaña Asistencia en la ficha del colaborador"
        VIEW_EMPLOYEE_DOCUMENTS -> "Puede ver, adjuntar y borrar los documentos del colaborador (DPI, antecedentes, contrato…)"
        VIEW_EMPLOYEE_HISTORY -> "Puede ver y agregar eventos en el historial del colaborador"
        VIEW_EMPLOYEE_CREDIT -> "Puede ver la cuenta de crédito del colaborador (fiados y adelantos)"
        VIEW_EMPLOYEE_ACCESS -> "Puede ver la pestaña Acceso del colaborador; cambiar el PIN o permisos sigue siendo del dueño o administrador"
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
        VIEW_CASH_AUDIT -> "Puede ver la bitácora de cajas por sucursal y recibe el aviso cuando alguien entra al cierre fuera del horario de esa caja"
        WITHDRAW_AT_CLOSE -> "Puede sacar efectivo de la gaveta al cerrar el turno; lo que queda pasa al siguiente turno o al día siguiente"
        COUNT_FINISHED_STOCK -> "Puede contar físicamente el pan al cierre del día y aplicar mermas automáticas por las diferencias"
        VIEW_BREAD_COUNTS -> "Puede consultar el historial de conteos de pan y sus mermas"
        VIEW_OPERATIONS_REPORT -> "Puede ver el dashboard del Home con KPIs operativos en vivo y banner de alertas accionables"
        VIEW_SALES_REPORT -> "Puede consultar el reporte formal de ventas: ingresos por día, método de pago, cajero, producto y hora"
        VIEW_PRODUCTION_REPORT -> "Puede consultar el reporte formal de producción: planeado vs producido, eficiencia, mermas en tránsito y discrepancias"
        VIEW_FINANCIAL_REPORT -> "Puede consultar el estado financiero del mes: ingresos, gastos, planilla, utilidad operativa y margen"
        VIEW_INVENTORY_REPORT -> "Puede consultar el reporte de inventario: rotación, mermas valoradas y movimientos por sucursal"
        VIEW_BRANCH_SUPPLY_REPORT -> "Puede ver, para sus sucursales, cuánto pidieron, cuánto les mandó la planta en cada turno, cuánto recibieron y cuánto vendieron"
        VIEW_CUSTOMER_ORDERS -> "Puede ver el listado y detalle de pedidos de clientes (revendedores, encargos especiales, pasteles)"
        CREATE_RESELLER_ORDER -> "Puede crear pedidos para tiendas revendedoras con lista mayorista"
        CREATE_RETAIL_SPECIAL_ORDER -> "Puede crear encargos especiales de cliente final (panes especiales, cantidades grandes)"
        CREATE_CAKE_ORDER -> "Puede crear encargos de pastel con especificaciones de sabor, relleno, decoración y anticipo"
        MANAGE_CUSTOMER_ORDERS -> "Puede confirmar, editar y cancelar pedidos de clientes; consolidarlos en lotes de producción"
        MANAGE_EXTERNAL_CUSTOMERS -> "Puede crear y editar la agenda de clientes externos y revendedores"
        COLLECT_ORDER_PAYMENT -> "Puede registrar anticipos, depósitos y cobros del saldo de un pedido (en POS o directo)"
        SELL_ON_CREDIT -> "Puede cargar una venta del POS a la cuenta de un cliente, institución o colaborador"
        COLLECT_CREDIT_PAYMENT -> "Puede recibir en su caja el pago de una cuenta de crédito"
        GIVE_EMPLOYEE_ADVANCE -> "Puede entregar un adelanto de salario desde su caja; se carga a la cuenta del colaborador"
        MANAGE_CREDIT_ACCOUNTS -> "Puede crear cuentas de crédito, autorizar personas, registrar pagos y ajustes"
    }

    val group: String get() = when (this) {
        VIEW_PRODUCTS, EDIT_PRODUCTS,
        VIEW_RECIPES, EDIT_RECIPES -> "Catálogo"
        VIEW_INGREDIENTS, EDIT_INGREDIENTS,
        VIEW_SUPPLIERS, EDIT_SUPPLIERS -> "Insumos"
        VIEW_EXPENSES, ADD_VARIABLE_EXPENSE, ADD_GENERAL_EXPENSE,
        MANAGE_FIXED_BUDGET -> "Gastos"
        RECEIVE_STOCK -> "Compras"
        MANAGE_PURCHASE_PAYMENTS -> "Compras"
        EDIT_PURCHASES -> "Compras"
        DELETE_PURCHASES -> "Compras"
        VIEW_ALL_BRANCHES, MANAGE_BRANCHES,
        MANAGE_USERS -> "Administración"
        VIEW_PRODUCTION, SEND_PRODUCTION_ORDER,
        MANAGE_PRODUCTION_LOTS, MANAGE_PRODUCTION_SHIFTS,
        ISSUE_PLANT_MATERIAL -> "Producción"
        VIEW_PREPARATIONS, EDIT_PREPARATIONS,
        MANAGE_PREPARATION_BATCHES -> "Preparaciones"
        MANAGE_EMPLOYEES, VIEW_EMPLOYEE_SALARY, VIEW_EMPLOYEE_ATTENDANCE, VIEW_EMPLOYEE_DOCUMENTS,
        VIEW_EMPLOYEE_HISTORY, VIEW_EMPLOYEE_CREDIT, VIEW_EMPLOYEE_ACCESS -> "Administración"
        VIEW_ASSETS, EDIT_ASSETS,
        MANAGE_ASSETS, ADD_ASSET_HISTORY -> "Activos Fijos"
        MANAGE_PRICE_LISTS -> "Catálogo"
        DISPATCH_PACK, MANAGE_DELIVERY_RUNS,
        DRIVE_DELIVERY, RECEIVE_DISPATCH,
        MANAGE_FINISHED_STOCK, VIEW_DISPATCH_VALUE -> "Distribución"
        OPERATE_POS, VIEW_SALES, MANAGE_POS_CONFIG,
        VIEW_SALES_VALUE, VOID_SALE,
        MANAGE_DAILY_CASH_CLOSE, SPOT_CHECK_CASH, VIEW_CASH_AUDIT, WITHDRAW_AT_CLOSE -> "Ventas (POS)"
        COUNT_FINISHED_STOCK, VIEW_BREAD_COUNTS -> "Distribución"
        VIEW_OPERATIONS_REPORT, VIEW_SALES_REPORT,
        VIEW_PRODUCTION_REPORT, VIEW_FINANCIAL_REPORT,
        VIEW_INVENTORY_REPORT, VIEW_BRANCH_SUPPLY_REPORT -> "Reportes"
        VIEW_CUSTOMER_ORDERS, CREATE_RESELLER_ORDER,
        CREATE_RETAIL_SPECIAL_ORDER, CREATE_CAKE_ORDER,
        MANAGE_CUSTOMER_ORDERS, MANAGE_EXTERNAL_CUSTOMERS,
        COLLECT_ORDER_PAYMENT -> "Pedidos de Clientes"
        SELL_ON_CREDIT, COLLECT_CREDIT_PAYMENT,
        GIVE_EMPLOYEE_ADVANCE, MANAGE_CREDIT_ACCOUNTS -> "Créditos"
    }

    /**
     * Permiso **sensible**: deja ver dinero a alguien que solo necesita contar pan.
     *
     * Se queda fuera del "Habilitar todos" del grupo — hay que marcarlo uno por uno,
     * a propósito. Vive en el mismo grupo que empacar y recibir porque ahí es donde
     * se usa, y por eso habilitar "Distribución" completo se lo llevaba puesto: la
     * encargada quedaba viendo el valor de cada nota sin que nadie lo hubiera
     * decidido. Quitar el permiso de su grupo lo escondería de donde se busca; lo
     * que corresponde es que el atajo no lo incluya.
     */
    val isSensitive: Boolean get() =
        this == VIEW_DISPATCH_VALUE || this == VIEW_SALES_VALUE || this == VIEW_EMPLOYEE_SALARY
}
