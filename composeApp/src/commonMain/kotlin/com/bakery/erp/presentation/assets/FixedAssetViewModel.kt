package com.bakery.erp.presentation.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bakery.erp.domain.AssetHistoryRepository
import com.bakery.erp.domain.AssetImageUploader
import com.bakery.erp.domain.FixedAssetRepository
import com.bakery.erp.model.AssetEventType
import com.bakery.erp.model.AssetHistoryEntry
import com.bakery.erp.model.AssetStatus
import com.bakery.erp.model.FixedAsset
import com.bakery.erp.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock

data class FixedAssetUiState(
    val assets: List<FixedAsset> = emptyList(),
    val selectedAsset: FixedAsset? = null,
    val historyEntries: List<AssetHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isDetailLoading: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isEntrySaved: Boolean = false,
    val isEntryDeleted: Boolean = false
)

class FixedAssetViewModel(
    private val assetRepository: FixedAssetRepository,
    private val historyRepository: AssetHistoryRepository,
    private val imageUploader: AssetImageUploader
) : ViewModel() {

    private val _uiState = MutableStateFlow(FixedAssetUiState())
    val uiState: StateFlow<FixedAssetUiState> = _uiState.asStateFlow()

    fun loadAssets(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { assetRepository.getAssets(companyId) }
                .onSuccess { _uiState.value = _uiState.value.copy(assets = it, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun loadAsset(companyId: String, assetId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDetailLoading = true, error = null)
            runCatching { assetRepository.getAsset(companyId, assetId) }
                .onSuccess { asset ->
                    _uiState.value = _uiState.value.copy(
                        selectedAsset = asset,
                        isDetailLoading = false
                    )
                    if (asset != null) loadHistory(companyId, asset.id)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isDetailLoading = false, error = it.message) }
        }
    }

    fun loadHistory(companyId: String, assetId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isHistoryLoading = true)
            runCatching { historyRepository.getHistory(companyId, assetId) }
                .onSuccess { _uiState.value = _uiState.value.copy(historyEntries = it, isHistoryLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isHistoryLoading = false, error = it.message) }
        }
    }

    fun selectAsset(asset: FixedAsset?) {
        _uiState.value = _uiState.value.copy(
            selectedAsset = asset,
            historyEntries = if (asset == null) emptyList() else _uiState.value.historyEntries
        )
    }

    /**
     * Guarda el activo. Si es nuevo, además inserta automáticamente la entrada
     * de historial tipo PURCHASE con la fecha y costo de compra.
     */
    fun saveAsset(
        companyId: String,
        asset: FixedAsset,
        imageBytes: ByteArray?,
        currentUser: User?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            runCatching {
                val isNew = asset.id.isEmpty()
                val assetId = if (isNew) generateId() else asset.id
                var finalAsset = asset.copy(id = assetId)

                if (imageBytes != null) {
                    val url = imageUploader.upload(imageBytes, assetId)
                    if (url.isNotEmpty()) finalAsset = finalAsset.copy(imageUrl = url)
                }

                val saved = assetRepository.saveAsset(companyId, finalAsset)

                if (isNew && saved.purchaseDate > 0L && saved.purchaseCost > 0.0) {
                    val purchaseEntry = AssetHistoryEntry(
                        assetId = saved.id,
                        type = AssetEventType.PURCHASE,
                        date = saved.purchaseDate,
                        description = "Compra inicial" + (saved.supplierName?.let { " — $it" } ?: ""),
                        cost = saved.purchaseCost,
                        providerName = saved.supplierName,
                        invoiceNumber = saved.invoiceNumber,
                        createdBy = currentUser?.id ?: "",
                        createdByName = currentUser?.displayName ?: ""
                    )
                    historyRepository.addEntry(companyId, saved.id, purchaseEntry)
                    // Actualiza contadores
                    recalcAndPersistAssetCounters(assetRepository, historyRepository, companyId, saved.id)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
                loadAssets(companyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
            }
        }
    }

    fun deleteAsset(companyId: String, assetId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { assetRepository.deleteAsset(companyId, assetId) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isDeleted = true)
                    loadAssets(companyId)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /**
     * Guarda (crea o edita) una entrada de historial.
     * Si el evento implica cambios al activo (TRANSFER, DISPOSAL, SALE, MAINTENANCE con próxima fecha),
     * actualiza el activo en consecuencia.
     */
    fun saveHistoryEntry(
        companyId: String,
        assetId: String,
        entry: AssetHistoryEntry,
        currentUser: User?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            runCatching {
                val isNew = entry.id.isEmpty()
                val enriched = entry.copy(
                    assetId = assetId,
                    createdBy = if (entry.createdBy.isEmpty()) currentUser?.id ?: "" else entry.createdBy,
                    createdByName = if (entry.createdByName.isEmpty()) currentUser?.displayName ?: "" else entry.createdByName
                )
                if (isNew) historyRepository.addEntry(companyId, assetId, enriched)
                else historyRepository.updateEntry(companyId, assetId, enriched)

                // Side-effects sobre el activo
                applyAssetEntrySideEffects(assetRepository, companyId, assetId, enriched)

                // Recalcula contadores denormalizados
                recalcAndPersistAssetCounters(assetRepository, historyRepository, companyId, assetId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isEntrySaved = true)
                loadHistory(companyId, assetId)
                loadAsset(companyId, assetId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
            }
        }
    }

    fun deleteHistoryEntry(companyId: String, assetId: String, entryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            runCatching {
                historyRepository.deleteEntry(companyId, assetId, entryId)
                recalcAndPersistAssetCounters(assetRepository, historyRepository, companyId, assetId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isEntryDeleted = true)
                loadHistory(companyId, assetId)
                loadAsset(companyId, assetId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
            }
        }
    }

    fun clearSaved() { _uiState.value = _uiState.value.copy(isSaved = false) }
    fun clearDeleted() { _uiState.value = _uiState.value.copy(isDeleted = false) }
    fun clearEntrySaved() { _uiState.value = _uiState.value.copy(isEntrySaved = false) }
    fun clearEntryDeleted() { _uiState.value = _uiState.value.copy(isEntryDeleted = false) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    private fun generateId(): String {
        val hex = "0123456789abcdef"
        return Random.nextBytes(16).joinToString("") { b ->
            hex[(b.toInt() shr 4) and 0xF].toString() + hex[b.toInt() and 0xF]
        }
    }
}
