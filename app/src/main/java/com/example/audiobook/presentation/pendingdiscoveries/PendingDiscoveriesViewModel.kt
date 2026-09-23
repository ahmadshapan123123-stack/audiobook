package com.example.audiobook.presentation.pendingdiscoveries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.R
import com.example.audiobook.data.room.dao.LibraryRootDao
import com.example.audiobook.data.room.dao.PendingDiscoveryDao
import com.example.audiobook.domain.usecases.ApplyDiscoveryDecision
import com.example.audiobook.domain.usecases.DiscoveryDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PendingDiscoveryUiItem(
    val id: UUID,
    val folderPath: String,
    val detectedTitle: String,
    val authorName: String,
    val seriesName: String?
)

data class PendingDiscoveryGroup(
    val rootId: UUID,
    val rootName: String,
    val count: Int,
    val items: List<PendingDiscoveryUiItem>
)

@HiltViewModel
class PendingDiscoveriesViewModel @Inject constructor(
    private val pendingDiscoveryDao: PendingDiscoveryDao,
    private val libraryRootDao: LibraryRootDao,
    private val applyDecision: ApplyDiscoveryDecision
) : ViewModel() {

    private val _groups = MutableStateFlow<List<PendingDiscoveryGroup>>(emptyList())
    val groups: StateFlow<List<PendingDiscoveryGroup>> = _groups.asStateFlow()

    /** رسالة تغذية راجعة تُستهلك مرة واحدةً (Int = مرجع السلسلة، الخيط الثانوي للـplaceholder). */
    private val _message = MutableStateFlow<Pair<Int, String?>?>(null)
    val message: StateFlow<Pair<Int, String?>?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            pendingDiscoveryDao.observePending().collect { rows ->
                if (rows.isEmpty()) {
                    _groups.value = emptyList()
                    return@collect
                }
                val roots = libraryRootDao.getAll().associateBy { it.id }
                _groups.value = rows.groupBy { it.rootId }.map { (rootId, list) ->
                    PendingDiscoveryGroup(
                        rootId = rootId,
                        rootName = roots[rootId]?.displayName.orEmpty(),
                        count = list.size,
                        items = list.map {
                            PendingDiscoveryUiItem(it.id, it.folderPath, it.detectedTitle, it.authorName, it.seriesName)
                        }
                    )
                }
            }
        }
    }

    /** قرار على مجموعة كاملة (بوب-أب الفحص): يطبّق ثم يُغلق البوب-أب. */
    fun decideGroup(rootId: UUID, decision: DiscoveryDecision, targetName: String? = null) {
        viewModelScope.launch {
            applyDecision(rootId, decision, targetName)
            report(decision, targetName)
            DiscoveryNotifier.dismiss()
        }
    }

    /** قرار فردي من شاشة الإعدادات. */
    fun decideItem(id: UUID, decision: DiscoveryDecision, targetName: String? = null) {
        viewModelScope.launch {
            applyDecision.invokeForDiscovery(id, decision, targetName)
            report(decision, targetName)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun report(decision: DiscoveryDecision, targetName: String?) {
        _message.value = when (decision) {
            DiscoveryDecision.CREATE_BOOKS -> R.string.discovery_resolved_done to null
            DiscoveryDecision.MERGE_AS_ONE -> R.string.discovery_merged_done to null
            DiscoveryDecision.IGNORE_ALL -> R.string.discovery_ignored_done to null
            DiscoveryDecision.ASSIGN_SERIES -> R.string.discovery_assigned_series to targetName
            DiscoveryDecision.ASSIGN_AUTHOR -> R.string.discovery_assigned_author to targetName
        }
    }
}