package com.dianming.app.ui.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dianming.app.spreadsheet.SheetTable
import com.dianming.app.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val vm: ImportViewModel = viewModel(factory = ImportViewModel.factory)
    val state by vm.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.loadUri(uri) }

    fun openPicker() {
        picker.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "application/vnd.ms-excel.sheet.macroEnabled.12",
                "text/csv",
                "text/comma-separated-values",
                "text/plain",
                "application/octet-stream",
                "*/*"
            )
        )
    }

    LaunchedEffect(state.savedClassId) {
        state.savedClassId?.let(onSaved)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加班级") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            if (state.error != null) {
                ErrorBanner(message = state.error!!, onDismiss = { vm.clearError() })
            }

            if (state.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            val table = state.table
            if (table == null) {
                PickFileCard(
                    onPick = { openPicker() },
                    enabled = !state.loading
                )
            } else {
                FileInfoCard(
                    fileName = state.fileName,
                    rowCount = table.rowCount,
                    columnCount = table.columnCount,
                    onReplace = { openPicker() }
                )

                PreviewCard(table)

                ColumnConfigCard(
                    table = table,
                    nameColumn = state.nameColumn,
                    phoneColumn = state.phoneColumn,
                    keptColumns = state.keptColumns,
                    onNameColumn = { vm.setNameColumn(it) },
                    onPhoneColumn = { vm.setPhoneColumn(it) },
                    onToggleColumn = { vm.toggleColumn(it) }
                )

                ClassInfoCard(
                    className = state.className,
                    owners = state.owners,
                    previewCount = state.studentPreviewCount,
                    onClassName = { vm.setClassName(it) },
                    onAddOwner = { vm.addOwner() },
                    onOwnerChange = { index, name, phone -> vm.updateOwner(index, name, phone) },
                    onRemoveOwner = { vm.removeOwner(it) }
                )

                Button(
                    onClick = { vm.save() },
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("保存班级", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        }
    }
}

@Composable
private fun PickFileCard(onPick: () -> Unit, enabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.UploadFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Text("选择一个班级名单表格", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "支持 .xlsx / .xls / .csv 格式，表格中必须包含一列姓名",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onPick, enabled = enabled) {
                Icon(Icons.Rounded.TableChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择表格文件")
            }
        }
    }
}

@Composable
private fun FileInfoCard(
    fileName: String,
    rowCount: Int,
    columnCount: Int,
    onReplace: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "共 $rowCount 行 · $columnCount 列",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onReplace) { Text("更换") }
        }
    }
}

@Composable
private fun PreviewCard(table: SheetTable) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            SectionHeader(title = "表格预览", modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                val maxRows = minOf(6, table.rowCount)
                val maxCols = minOf(6, table.columnCount)
                for (c in 0 until maxCols) {
                    Column(
                        modifier = Modifier
                            .width(110.dp)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        ) {
                            Text(
                                text = table.headerOf(c),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        for (r in 0 until maxRows) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                            ) {
                                Text(
                                    text = table.cell(r, c),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnConfigCard(
    table: SheetTable,
    nameColumn: Int,
    phoneColumn: Int,
    keptColumns: Set<Int>,
    onNameColumn: (Int) -> Unit,
    onPhoneColumn: (Int) -> Unit,
    onToggleColumn: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "列设置")

            Text(
                text = "名字列（必选）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (c in 0 until table.columnCount) {
                    FilterChip(
                        selected = nameColumn == c,
                        onClick = { onNameColumn(c) },
                        label = { Text(table.headerOf(c)) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "电话列（可选，用于一键拨号）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = phoneColumn < 0,
                    onClick = { onPhoneColumn(-1) },
                    label = { Text("无") }
                )
                for (c in 0 until table.columnCount) {
                    FilterChip(
                        selected = phoneColumn == c,
                        onClick = { onPhoneColumn(c) },
                        label = { Text(table.headerOf(c)) },
                        enabled = c != nameColumn
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "其它列（取消勾选即可去掉）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            val optional = (0 until table.columnCount).filter { it != nameColumn && it != phoneColumn }
            if (optional.isEmpty()) {
                Text(
                    text = "没有其它可用的列",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                optional.forEach { c ->
                    val sample = table.columnValues(c).firstOrNull().orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = keptColumns.contains(c),
                            onCheckedChange = { onToggleColumn(c) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(table.headerOf(c), style = MaterialTheme.typography.bodyLarge)
                            if (sample.isNotBlank()) {
                                Text(
                                    text = "示例：$sample",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassInfoCard(
    className: String,
    owners: List<com.dianming.app.data.Owner>,
    previewCount: Int,
    onClassName: (String) -> Unit,
    onAddOwner: () -> Unit,
    onOwnerChange: (Int, String?, String?) -> Unit,
    onRemoveOwner: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "班级信息")

            OutlinedTextField(
                value = className,
                onValueChange = onClassName,
                label = { Text("班级名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "已识别 $previewCount 名学生",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("负责人", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = onAddOwner) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加")
                }
            }

            owners.forEachIndexed { index, owner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = owner.name,
                        onValueChange = { onOwnerChange(index, it, null) },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = owner.phone,
                        onValueChange = { onOwnerChange(index, null, it) },
                        label = { Text("电话") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    IconButton(onClick = { onRemoveOwner(index) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "删除负责人",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (owners.isEmpty()) {
                Text(
                    text = "还没有添加负责人，可稍后在班级详情页补充",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
