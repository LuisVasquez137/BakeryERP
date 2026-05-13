package com.bakery.erp.di

import com.bakery.erp.auth.GoogleAuthHandlerStub
import com.bakery.erp.data.FirebaseAuthRepository
import com.bakery.erp.data.FirebaseBranchRepository
import com.bakery.erp.data.FirebaseEmployeeRepository
import com.bakery.erp.data.FirebaseExpenseRepository
import com.bakery.erp.data.FirebaseFixedBudgetRepository
import com.bakery.erp.data.FirebaseIngredientRepository
import com.bakery.erp.data.FirebaseProductionRepository
import com.bakery.erp.data.FirebasePurchaseRepository
import com.bakery.erp.data.FirebaseRecipeRepository
import com.bakery.erp.data.FirebaseSupplierRepository
import com.bakery.erp.domain.AuthRepository
import com.bakery.erp.domain.BranchRepository
import com.bakery.erp.domain.EmployeeRepository
import com.bakery.erp.domain.ExpenseRepository
import com.bakery.erp.domain.FixedBudgetRepository
import com.bakery.erp.domain.GoogleAuthHandler
import com.bakery.erp.domain.IngredientRepository
import com.bakery.erp.domain.ProductionRepository
import com.bakery.erp.domain.PurchaseRepository
import com.bakery.erp.domain.RecipeRepository
import com.bakery.erp.domain.SupplierRepository
import com.bakery.erp.presentation.auth.AuthViewModel
import com.bakery.erp.presentation.branches.BranchViewModel
import com.bakery.erp.presentation.expenses.ExpenseViewModel
import com.bakery.erp.presentation.expenses.FixedBudgetViewModel
import com.bakery.erp.presentation.ingredients.IngredientViewModel
import com.bakery.erp.presentation.production.ProductionViewModel
import com.bakery.erp.presentation.purchases.PurchaseViewModel
import com.bakery.erp.presentation.products.ProductViewModel
import com.bakery.erp.presentation.recipes.RecipeViewModel
import com.bakery.erp.presentation.suppliers.SupplierViewModel
import com.bakery.erp.presentation.employees.EmployeeViewModel
import com.bakery.erp.presentation.users.UserViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { Firebase.auth }
    single { Firebase.firestore }
    single<AuthRepository> { FirebaseAuthRepository(get(), get()) }
    single<IngredientRepository> { FirebaseIngredientRepository(get()) }
    single<SupplierRepository> { FirebaseSupplierRepository(get()) }
    single<RecipeRepository> { FirebaseRecipeRepository(get()) }
    single<BranchRepository> { FirebaseBranchRepository(get()) }
    single<PurchaseRepository> { FirebasePurchaseRepository(get()) }
    single<ProductionRepository> { FirebaseProductionRepository(get()) }
    single<EmployeeRepository> { FirebaseEmployeeRepository(get()) }
    single<ExpenseRepository> { FirebaseExpenseRepository(get()) }
    single<FixedBudgetRepository> { FirebaseFixedBudgetRepository(get()) }
    // Stub por defecto para iOS y JS; Android lo sobreescribe con androidModule
    single<GoogleAuthHandler> { GoogleAuthHandlerStub() }
    viewModel { AuthViewModel(get(), get()) }
    viewModel { ProductViewModel(get()) }
    viewModel { IngredientViewModel(get(), get(), get()) }
    viewModel { PurchaseViewModel(get(), get(), get(), get()) }
    viewModel { ProductionViewModel(get(), get(), get(), get()) }
    viewModel { SupplierViewModel(get()) }
    viewModel { RecipeViewModel(get()) }
    viewModel { BranchViewModel(get()) }
    viewModel { ExpenseViewModel(get()) }
    viewModel { FixedBudgetViewModel(get()) }
    viewModel { EmployeeViewModel(get(), get()) }
    viewModel { UserViewModel(get()) }
}
