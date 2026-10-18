package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FontRecordEntity
import com.example.data.local.OfficeDocType
import com.example.ui.OfficeViewModel
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.PdfCrimson
import com.example.ui.theme.PowerPointOrange
import com.example.ui.theme.WordBlue
import com.example.ui.theme.resolveComposeFontFamily
import kotlinx.coroutines.launch

@Composable
fun TemplatesLibraryScreen(
    viewModel: OfficeViewModel,
    onRequestImportTemplateSaf: () -> Unit
) {
    val templates by viewModel.templates.collectAsState()
    var selectedCategory by remember { mutableStateOf("Tất cả") }
    val categories = remember(templates) {
        listOf("Tất cả") + templates.map { it.category }.distinct()
    }

    val filtered = remember(templates, selectedCategory) {
        if (selectedCategory == "Tất cả") templates
        else templates.filter { it.category == selectedCategory }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kho Mẫu Tài Liệu Chuyên Nghiệp",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "11+ danh mục chuẩn hành chính, tài chính, học thuật & trình chiếu",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onRequestImportTemplateSaf,
                        modifier = Modifier.testTag("import_template_saf_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nhập mẫu từ máy")
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.templateId }) { tpl ->
                val docType = OfficeDocType.fromCode(tpl.docType)
                val badgeColor = when (docType) {
                    OfficeDocType.WORD -> WordBlue
                    OfficeDocType.EXCEL -> ExcelGreen
                    OfficeDocType.POWERPOINT -> PowerPointOrange
                    OfficeDocType.PDF -> PdfCrimson
                }
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("template_card_${tpl.templateId}"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = badgeColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${tpl.category} • .${tpl.extension.uppercase()}",
                                    color = badgeColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "${tpl.defaultFont} • ${tpl.pageSetupInfo}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = tpl.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tpl.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.createFromTemplateAndOpen(tpl) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sử dụng mẫu này")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FontManagerScreen(
    viewModel: OfficeViewModel,
    onRequestImportFontSaf: () -> Unit
) {
    val fonts by viewModel.fonts.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var samplePreviewText by remember {
        mutableStateOf("Cộng hòa Xã hội Chủ nghĩa Việt Nam — Tiếng Việt chuẩn Unicode: ă â đ ê ô ơ ư ắ ầ ể ộ ự ỹ 0123456789")
    }

    val filteredFonts = remember(fonts, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) fonts
        else fonts.filter {
            it.familyName.lowercase().contains(q) ||
                it.officeTargetName.lowercase().contains(q) ||
                it.substitutionFor.lowercase().contains(q)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hệ thống Quản lý Font & Chống lỗi Tiếng Việt",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Đối chiếu font thực tế trong DOCX/XLSX/PPTX, tự động ánh xạ kích thước chuẩn và bảo toàn font gốc.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = onRequestImportFontSaf,
                            modifier = Modifier.testTag("import_font_ttf_otf_button")
                        ) {
                            Icon(Icons.Default.FontDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nhập TTF/OTF")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Giữ nguyên thông tin Font gốc trong file nguồn khi chỉ thay font để hiển thị",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Không tự động thay đổi <w:rFonts> trong DOCX nếu người dùng chưa chủ động đổi font.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = prefs.preserveOriginalFontsOnSave,
                            onCheckedChange = {
                                scope.launch { viewModel.prefsRepo.setPreserveOriginalFonts(it) }
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = samplePreviewText,
                onValueChange = { samplePreviewText = it },
                label = { Text("Văn bản xem trước trực tiếp (Kiểm tra dấu Tiếng Việt)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Tìm font theo tên (Times New Roman, Calibri, Arial, Tinos, Be Vietnam Pro...)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("font_search_input")
            )
        }

        items(filteredFonts, key = { it.fontId }) { font ->
            FontCardItem(
                font = font,
                previewText = samplePreviewText,
                onToggleFav = { scope.launch { viewModel.repository.toggleFontFavorite(font) } },
                onSetDefaultWord = {
                    scope.launch {
                        viewModel.repository.setDefaultFontForApp(font, OfficeDocType.WORD)
                        viewModel.showBanner("Đã đặt '${font.officeTargetName}' làm font mặc định cho Word")
                    }
                },
                onSetDefaultExcel = {
                    scope.launch {
                        viewModel.repository.setDefaultFontForApp(font, OfficeDocType.EXCEL)
                        viewModel.showBanner("Đã đặt '${font.officeTargetName}' làm font mặc định cho Excel")
                    }
                },
                onSetDefaultPpt = {
                    scope.launch {
                        viewModel.repository.setDefaultFontForApp(font, OfficeDocType.POWERPOINT)
                        viewModel.showBanner("Đã đặt '${font.officeTargetName}' làm font mặc định cho PowerPoint")
                    }
                }
            )
        }
    }
}

@Composable
private fun FontCardItem(
    font: FontRecordEntity,
    previewText: String,
    onToggleFav: () -> Unit,
    onSetDefaultWord: () -> Unit,
    onSetDefaultExcel: () -> Unit,
    onSetDefaultPpt: () -> Unit
) {
    val composeFont = resolveComposeFontFamily(font.officeTargetName)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("font_card_${font.fontId}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = font.familyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ánh xạ chuẩn Office: ${font.officeTargetName} • Thay thế cho: ${font.substitutionFor}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WordBlue
                    )
                }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        imageVector = if (font.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Yêu thích font",
                        tint = if (font.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = previewText,
                    fontFamily = composeFont,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (font.supportsVietnamese) ExcelGreen else PowerPointOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (font.supportsVietnamese) "Đạt chuẩn Tiếng Việt Unicode • ${font.licenseNote}"
                    else "Thiếu một số ký tự dấu Tiếng Việt",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = font.defaultForWord,
                    onClick = onSetDefaultWord,
                    label = { Text(if (font.defaultForWord) "✓ Mặc định Word" else "Đặt mặc định Word") }
                )
                FilterChip(
                    selected = font.defaultForExcel,
                    onClick = onSetDefaultExcel,
                    label = { Text(if (font.defaultForExcel) "✓ Mặc định Excel" else "Đặt mặc định Excel") }
                )
                FilterChip(
                    selected = font.defaultForPpt,
                    onClick = onSetDefaultPpt,
                    label = { Text(if (font.defaultForPpt) "✓ Mặc định PPT" else "Đặt mặc định PPT") }
                )
            }
        }
    }
}
