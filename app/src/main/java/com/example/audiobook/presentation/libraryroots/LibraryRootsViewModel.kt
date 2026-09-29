package com.example.audiobook.presentation.libraryroots

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.localfilesystem.StorageAccess
import com.example.audiobook.data.repository.LibraryRootRepository
import com.example.audiobook.data.room.dao.LibraryRootDao
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.background.scanworker.ScanScheduler
import com.example.audiobook.domain.usecases.LibraryManagement
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryRootsViewModel @Inject constructor(
    application: Application,
    private val repository: LibraryRootRepository,
    rootDao: LibraryRootDao,
    private val scanScheduler: ScanScheduler,
    private val libraryManagement: LibraryManagement
) : AndroidViewModel(application) {
    val roots: StateFlow<List<LibraryRootEntity>> = rootDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // بعد إعادة التثبيت/مسح البيانات يختفي إذن SAF المُخزَّن رغم بقاء الجذر في قاعدة
    // البيانات — قائمة الجذور التي فُقد إذنها تُحسب دفاعيًّا (runCatching) حتى لا
    // تتعطل الواجهة في أي بيئة اختبار بدون Context حقيقي.
    val accessRevokedRoots: StateFlow<Set<UUID>> = rootDao.observeAll()
        .map { stored ->
            stored.filter { root -> root.isEnabled && !hasPersistedAccess(root.uri) }.map { it.id }.toSet()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private fun hasPersistedAccess(uriString: String): Boolean = runCatching {
        val uri = Uri.parse(uriString)
        StorageAccess.hasPersistedPermission(getApplication<Application>().contentResolver, uri)
    }.getOrDefault(true)

    fun addRoot(uri: Uri) {
        viewModelScope.launch {
            StorageAccess.persistReadWritePermission(getApplication<Application>().contentResolver, uri)
            val root = LibraryRootEntity(
                    uri = uri.toString(),
                    displayName = StorageAccess.displayNameOf(uri),
                    isPriority = roots.value.none { it.isPriority },
                    isEnabled = true,
                    lastScanAt = null,
                    scanStatus = ScanStatus.IDLE
                )
            repository.insert(root)
            if (root.isPriority) scanScheduler.schedulePriorityScan(root)
            else scanScheduler.scheduleBackgroundScans()
        }
    }

    fun setPriority(root: LibraryRootEntity, selected: Boolean) {
        viewModelScope.launch {
            if (selected) repository.clearPriorityExcept(root.id)
            repository.setPriority(root.id, selected)
            if (selected) scanScheduler.schedulePriorityScan(root)
            else scanScheduler.scheduleBackgroundScans()
        }
    }

    fun setEnabled(root: LibraryRootEntity, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(root.id, enabled)
            if (enabled) refresh(root)
        }
    }

    fun refresh(root: LibraryRootEntity) {
        if (root.isPriority) scanScheduler.schedulePriorityScan(root)
        else viewModelScope.launch { scanScheduler.scheduleBackgroundScans() }
    }

    /**
     * إعادة منح إذن SAF لجذر فُقد إذنه (بعد إعادة التثبيت): يختار المستخدم المجلد
     * مجددًا من منتقي SAF، يُحدَّث uri الجذر ثم يُعاد الفحص فورًا.
     */
    fun reGrantAccess(root: LibraryRootEntity, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                StorageAccess.persistReadWritePermission(getApplication<Application>().contentResolver, uri)
            }
            repository.update(root.copy(uri = uri.toString()))
            refresh(root)
        }
    }

    // ── STAGE 6B: حذف/إعادة تسمية الجذور ──

    private val _deleteResult = MutableStateFlow<LibraryManagement.RootDeleteResult?>(null)
    val deleteResult: StateFlow<LibraryManagement.RootDeleteResult?> = _deleteResult.asStateFlow()

    private val _busyRoot = MutableStateFlow<UUID?>(null)
    val busyRoot: StateFlow<UUID?> = _busyRoot.asStateFlow()

    /** حذف الجذر مع تتابع صريح (كتب/إصدارات/ملفات/فصول/تقدم/اكتشافات). */
    fun deleteRoot(root: LibraryRootEntity) {
        if (_busyRoot.value != null) return
        viewModelScope.launch {
            _busyRoot.value = root.id
            try {
                _deleteResult.value = libraryManagement.deleteLibraryRoot(root.id)
            } finally {
                _busyRoot.value = null
            }
        }
    }

    fun consumeDeleteResult() {
        _deleteResult.value = null
    }

    /** إعادة تسمية عرضية. @return false إذا كان الاسم فارغًا. */
    fun renameRoot(root: LibraryRootEntity, newName: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onDone(libraryManagement.renameLibraryRoot(root.id, newName))
        }
    }

    /**
     * تحرير موحّد للجذر: الاسم + الأولوية + التفعيل في عملية واحدة.
     * يحدّث صف الجذر فقط — لا يمسّ الكتب/الإصدارات. @return false للاسم
     * الفارغ أو الجذر المفقود.
     */
    fun editRoot(rootId: UUID, newName: String, priority: Boolean, enabled: Boolean, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) {
                onDone(false)
                return@launch
            }
            val root = repository.getById(rootId) ?: run {
                onDone(false)
                return@launch
            }
            repository.update(root.copy(displayName = trimmed, isPriority = priority, isEnabled = enabled))
            if (priority) {
                repository.clearPriorityExcept(rootId)
                scanScheduler.schedulePriorityScan(root.copy(displayName = trimmed))
            } else {
                scanScheduler.scheduleBackgroundScans()
            }
            onDone(true)
        }
    }
}