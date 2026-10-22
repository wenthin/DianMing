package com.dianming.app.ui.import

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dianming.app.DianMingApp
import com.dianming.app.data.NewStudent
import com.dianming.app.data.Owner
import com.dianming.app.data.Repository
import com.dianming.app.spreadsheet.ColumnHeuristics
import com.dianming.app.spreadsheet.SheetTable
import com.dianming.app.spreadsheet.SpreadsheetReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val fileName: String = "",
    val table: SheetTable? = null,
    val nameColumn: Int = -1,
    val keptColumns: Set<Int> = emptySet(),
    val phoneColumn: Int = -1,
    val className: String = "",
    val owners: List<Owner> = emptyList(),
    val savedClassId: String? = null
) {
    val hasFile: Boolean get() = table != null

    val studentPreviewCount: Int
        get() {
            val t = table ?: return 0
            if (nameColumn < 0) return 0
            return (1 until t.rowCount).count { t.cell(it, nameColumn).isNotBlank() }
        }

    val canSave: Boolean
        get() = nameColumn >= 0 && className.isNotBlank() && studentPreviewCount > 0 && !saving
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: Repository = (app as DianMingApp).repository

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    fun loadUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, savedClassId = null) }
            try {
                val name = queryDisplayName(uri)
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                } ?: throw IllegalArgumentException("无法读取所选文件")
                val table = SpreadsheetReader.read(bytes, name)
                val nameCol = ColumnHeuristics.detectNameColumn(table)
                val phoneCol = ColumnHeuristics.detectPhoneColumn(table)
                val kept = (0 until table.columnCount)
                    .filter { it != nameCol && it != phoneCol }
                    .filter { table.columnValues(it).isNotEmpty() }
                    .toSet()
                val defaultClassName = name.substringBeforeLast('.', name).ifBlank { "新班级" }
                _state.update {
                    it.copy(
                        loading = false,
                        fileName = name,
                        table = table,
                        nameColumn = nameCol ?: -1,
                        phoneColumn = phoneCol ?: -1,
                        keptColumns = kept,
                        className = if (it.className.isBlank()) defaultClassName else it.className,
                        owners = if (it.owners.isEmpty()) listOf(Owner("", "")) else it.owners
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "读取表格失败，请确认文件格式是否正确"
                    )
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) ?: "" else ""
            }.orEmpty().ifBlank { uri.lastPathSegment ?: "表格" }
        } catch (_: Exception) {
            uri.lastPathSegment ?: "表格"
        }
    }

    fun setNameColumn(column: Int) = _state.update {
        it.copy(
            nameColumn = column,
            keptColumns = it.keptColumns - column,
            phoneColumn = if (it.phoneColumn == column) -1 else it.phoneColumn
        )
    }

    fun setPhoneColumn(column: Int) = _state.update {
        it.copy(
            phoneColumn = column,
            keptColumns = if (column >= 0) it.keptColumns - column else it.keptColumns
        )
    }

    fun toggleColumn(column: Int) = _state.update { s ->
        if (s.keptColumns.contains(column)) s.copy(keptColumns = s.keptColumns - column)
        else s.copy(keptColumns = s.keptColumns + column)
    }

    fun setClassName(name: String) = _state.update { it.copy(className = name) }

    fun addOwner() = _state.update { it.copy(owners = it.owners + Owner("", "")) }

    fun updateOwner(index: Int, name: String?, phone: String?) = _state.update { s ->
        s.copy(
            owners = s.owners.mapIndexed { i, o ->
                if (i != index) o else o.copy(name = name ?: o.name, phone = phone ?: o.phone)
            }
        )
    }

    fun removeOwner(index: Int) = _state.update { s ->
        s.copy(owners = s.owners.filterIndexed { i, _ -> i != index })
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun reset() {
        _state.value = ImportUiState()
    }

    fun save() {
        val s = _state.value
        val table = s.table ?: return
        if (s.nameColumn < 0 || s.className.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val students = (1 until table.rowCount).mapNotNull { row ->
                    val name = table.cell(row, s.nameColumn)
                    if (name.isBlank()) null
                    else NewStudent(
                        name = name,
                        phone = if (s.phoneColumn >= 0) {
                            table.cell(row, s.phoneColumn).takeIf { it.isNotBlank() }
                        } else null,
                        extras = s.keptColumns.mapNotNull { c ->
                            val v = table.cell(row, c)
                            if (v.isBlank()) null else table.headerOf(c) to v
                        }.toMap()
                    )
                }
                if (students.isEmpty()) {
                    _state.update { it.copy(saving = false, error = "没有解析到有效的学生姓名") }
                    return@launch
                }
                val owners = s.owners.filter { it.name.isNotBlank() || it.phone.isNotBlank() }
                val classId = repo.createClass(s.className.trim(), owners, students)
                _state.update { it.copy(saving = false, savedClassId = classId) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
            }
        }
    }

    companion object {
        val factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                ImportViewModel(app)
            }
        }
    }
}
