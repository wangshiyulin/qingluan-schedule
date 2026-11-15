package com.qingluan.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One measured row keeps the address field and action aligned at every font scale. */
@Composable
internal fun AcademicAddressBar(
    address: String,
    onAddress: (String) -> Unit,
    onOpen: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            address,
            onAddress,
            label = { Text("教务网址") },
            singleLine = true,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("academic-address"),
        )
        Button(
            onClick = onOpen,
            enabled = enabled && address.isNotBlank(),
            modifier = Modifier.fillMaxHeight().widthIn(min = 80.dp).testTag("academic-open"),
        ) {
            Text("打开")
        }
    }
}
