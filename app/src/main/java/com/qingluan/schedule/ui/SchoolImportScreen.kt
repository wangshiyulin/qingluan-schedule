@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.qingluan.schedule.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qingluan.schedule.transfer.*

@Composable
fun SchoolImportScreen(onSchool: (String) -> Unit) {
    val context = LocalContext.current
    val schools = remember {
        runCatching {
                SchoolCatalog.decode(
                    context.assets.open("import/schools.json").bufferedReader().use {
                        it.readText()
                    }
                )
            }
            .getOrDefault(emptyList())
    }
    var address by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("undergraduate") }
    var choosing by remember { mutableStateOf<List<SchoolEntry>>(emptyList()) }
    val categories = listOf("undergraduate" to "本科/专科", "postgraduate" to "研究生", "general" to "通用")
    val groups =
        remember(query, schools, category) {
            SchoolCatalog.search(schools.filter { it.category == category }, query)
                .groupBy { it.name }
                .entries
                .toList()
        }
    Column(Modifier.fillMaxSize().testTag("school-import-page")) {
        PrimaryTabRow(
            selectedTabIndex = categories.indexOfFirst { it.first == category }.coerceAtLeast(0)
        ) {
            categories.forEach { (key, title) ->
                Tab(
                    selected = category == key,
                    onClick = { category = key },
                    text = { Text(title, maxLines = 1) },
                )
            }
        }
        if (category == "general") {
            AcademicAddressBar(
                address,
                {
                    address = it
                    error = null
                },
                {
                    val url = SchoolCatalog.normalizeAddress(address)
                    if (url != null) onSchool(url) else error = "请输入 HTTPS 教务网址"
                },
            )
            error?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text("搜索学校") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            key(category, query) {
                LazyColumn(
                    Modifier.weight(1f).testTag("school-list"),
                    contentPadding = PaddingValues(12.dp, 0.dp, 12.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (groups.isEmpty()) item { EmptyState("未找到学校，可在通用中输入教务网址") }
                    items(groups, key = { it.key }) { group ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    if (group.value.size == 1) onSchool(group.value.single().url)
                                    else choosing = group.value
                                },
                        ) {
                            Row(
                                Modifier.heightIn(min = 52.dp)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Language,
                                    null,
                                    Modifier.size(21.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    group.key,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosing.isNotEmpty())
        SelectionDialog(
            choosing.first().name,
            "",
            choosing.map { it.url to it.label.ifBlank { it.name } },
            { choosing = emptyList() },
        ) { url ->
            choosing = emptyList()
            onSchool(url)
        }
}
