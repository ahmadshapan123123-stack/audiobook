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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryRootsViewModel @Inject constructor(
    application: Application,
    private val repository: LibraryRootRepository,
    rootDao: LibraryRootDao,
    private val scanScheduler: ScanScheduler
) : AndroidViewModel(application) {
    val roots: StateFlow<List<LibraryRootEntity>> = rootDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addRoot(uri: Uri) {
        viewModelScope.launch {
            StorageAccess.persistReadWritePermission(getApplication<Application>().contentResolver, uri)
            val root = LibraryRootEntity(
                    uri = uri.toString(),
                    displayName = uri.lastPathSegment?.substringAfterLast(':') ?: uri.toString(),
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
}