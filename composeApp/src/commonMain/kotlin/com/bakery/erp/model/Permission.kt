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

    // Colaboradores
    MANAGE_EMPLOYEES;

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
        MANAGE_EMPLOYEES -> "Gestionar colaboradores"
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
        MANAGE_EMPLOYEES -> "Puede gestionar la información de los colaboradores"
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
        MANAGE_EMPLOYEES -> "Administración"
    }
}
