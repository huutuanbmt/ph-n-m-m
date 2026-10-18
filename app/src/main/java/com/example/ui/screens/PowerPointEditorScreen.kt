package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.PptSlide
import com.example.engine.SlideElement
import com.example.engine.SlideElementType
import com.example.engine.SlideTransitionType
import com.example.ui.OfficeViewModel
import com.example.ui.OpenEditorSession
import com.example.ui.theme.PowerPointOrange
import kotlin.math.roundToInt

@Composable
fun PowerPointEditorScreen(
    session: OpenEditorSession,
    viewModel: OfficeViewModel
) {
    val model = session.pptModel ?: return
    var activeSlideIndex by remember { mutableIntStateOf(0) }
    val safeSlideIdx = activeSlideIndex.coerceIn(0, (model.slides.size - 1).coerceAtLeast(0))
    val currentSlide = model.slides[safeSlideIdx]

    var selectedElementIdx by remember { mutableIntStateOf(0) }
    val safeElIdx = selectedElementIdx.coerceIn(0, (currentSlide.elements.size - 1).coerceAtLeast(0))
    val activeElement = currentSlide.elements.getOrNull(safeElIdx)

    var isFullScreenSlideshow by remember { mutableStateOf(false) }

    fun updateCurrentSlide(transform: (PptSlide) -> PptSlide) {
        val updatedSlides = model.slides.mapIndexed { idx, s ->
            if (idx == safeSlideIdx) transform(s) else s
        }
        viewModel.updatePptModel(session.document.id, model.copy(slides = updatedSlides))
    }

    fun updateActiveElement(transform: (SlideElement) -> SlideElement) {
        val updatedEls = currentSlide.elements.mapIndexed { idx, el ->
            if (idx == safeElIdx) transform(el) else el
        }
        updateCurrentSlide { it.copy(elements = updatedEls) }
    }

    if (isFullScreenSlideshow) {
        // Full-Screen Presentation Mode
        val slideBg = runCatching { Color(android.graphics.Color.parseColor("#${currentSlide.backgroundColorHex.trimStart('#')}")) }
            .getOrDefault(Color(0xFF0F2547))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(slideBg)
            ) {
                val canvasW = maxWidth
                val canvasH = maxHeight
                currentSlide.elements.sortedBy { it.zIndex }.forEach { el ->
                    val boxFill = runCatching { Color(android.graphics.Color.parseColor("#${el.fillColorHex.trimStart('#')}")) }
                        .getOrDefault(Color(0xFF1E3A8A))
                    val txtColor = runCatching { Color(android.graphics.Color.parseColor("#${el.textColorHex.trimStart('#')}")) }
                        .getOrDefault(Color.White)

                    Box(
                        modifier = Modifier
                            .offset(
                                x = canvasW * (el.xPercent / 100f),
                                y = canvasH * (el.yPercent / 100f)
                            )
                            .width(canvasW * (el.widthPercent / 100f))
                            .height(canvasH * (el.heightPercent / 100f))
                            .rotate(el.rotationDeg)
                            .clip(if (el.type == SlideElementType.SHAPE_CIRCLE) CircleShape else RoundedCornerShape(10.dp))
                            .background(boxFill)
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = el.text,
                            color = txtColor,
                            fontSize = el.fontSizePt.sp,
                            fontWeight = if (el.isBold) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Presentation Navigation Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { if (activeSlideIndex > 0) activeSlideIndex-- },
                    enabled = activeSlideIndex > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Slide trước", tint = Color.White)
                }
                Text(
                    text = "Slide ${safeSlideIdx + 1}/${model.slides.size} • Hiệu ứng: ${currentSlide.transition.label}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(
                    onClick = { if (activeSlideIndex < model.slides.size - 1) activeSlideIndex++ },
                    enabled = activeSlideIndex < model.slides.size - 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Slide tiếp", tint = Color.White)
                }
                Button(
                    onClick = { isFullScreenSlideshow = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PowerPointOrange)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Thoát trình chiếu")
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // PowerPoint Top Action & Object Insert Bar
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveActiveSession(session.document.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = PowerPointOrange),
                        modifier = Modifier.testTag("ppt_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Lưu", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (session.hasUnsavedChanges) "Lưu PPTX*" else "Đã lưu")
                    }
                    IconButton(
                        onClick = { viewModel.undoPpt(session.document.id) },
                        enabled = session.pptUndoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Hoàn tác")
                    }
                    Button(
                        onClick = { isFullScreenSlideshow = true },
                        modifier = Modifier.testTag("ppt_present_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Trình chiếu")
                    }
                    SlideElementType.entries.forEach { elType ->
                        AssistChip(
                            onClick = {
                                val newEl = SlideElement(
                                    id = "el_${System.currentTimeMillis()}",
                                    type = elType,
                                    text = "${elType.label} mới",
                                    xPercent = 14f,
                                    yPercent = 28f,
                                    widthPercent = 72f,
                                    heightPercent = 22f,
                                    zIndex = currentSlide.elements.size + 1,
                                    fillColorHex = if (elType == SlideElementType.SHAPE_CIRCLE) "B45309" else "1E3A8A"
                                )
                                updateCurrentSlide { it.copy(elements = it.elements + newEl) }
                                selectedElementIdx = currentSlide.elements.size
                            },
                            label = { Text("+ ${elType.label}") }
                        )
                    }
                    OutlinedButton(onClick = { viewModel.exportSessionToPdf(session.document.id) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xuất PDF")
                    }
                }

                // Transition & Background Theme Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Chuyển Slide:", style = MaterialTheme.typography.labelSmall)
                    SlideTransitionType.entries.forEach { tr ->
                        FilterChip(
                            selected = currentSlide.transition == tr,
                            onClick = { updateCurrentSlide { it.copy(transition = tr) } },
                            label = { Text(tr.label) }
                        )
                    }
                    AssistChip(
                        onClick = {
                            val backgrounds = listOf("0F2547", "1E293B", "14532D", "4C1D95", "7C2D12")
                            val nextBg = backgrounds[(backgrounds.indexOf(currentSlide.backgroundColorHex) + 1) % backgrounds.size]
                            updateCurrentSlide { it.copy(backgroundColorHex = nextBg) }
                        },
                        label = { Text("Đổi màu nền Slide") }
                    )
                }
            }
        }

        // Main Slide Canvas + Element Inspector
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val slideBg = runCatching { Color(android.graphics.Color.parseColor("#${currentSlide.backgroundColorHex.trimStart('#')}")) }
                .getOrDefault(Color(0xFF0F2547))

            // 16:9 Slide Interactive Preview Canvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(slideBg)
                    .border(2.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
            ) {
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()

                currentSlide.elements.sortedBy { it.zIndex }.forEachIndexed { idx, el ->
                    val isSelected = idx == safeElIdx
                    val boxFill = runCatching { Color(android.graphics.Color.parseColor("#${el.fillColorHex.trimStart('#')}")) }
                        .getOrDefault(Color(0xFF1E3A8A))
                    val txtColor = runCatching { Color(android.graphics.Color.parseColor("#${el.textColorHex.trimStart('#')}")) }
                        .getOrDefault(Color.White)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = ((el.xPercent / 100f) * wPx).roundToInt(),
                                    y = ((el.yPercent / 100f) * hPx).roundToInt()
                                )
                            }
                            .width(maxWidth * (el.widthPercent / 100f))
                            .height(maxHeight * (el.heightPercent / 100f))
                            .rotate(el.rotationDeg)
                            .clip(if (el.type == SlideElementType.SHAPE_CIRCLE) CircleShape else RoundedCornerShape(10.dp))
                            .background(boxFill)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.25f),
                                shape = if (el.type == SlideElementType.SHAPE_CIRCLE) CircleShape else RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedElementIdx = idx }
                            .padding(10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = el.text,
                            color = txtColor,
                            fontSize = (el.fontSizePt * 0.7f).sp,
                            fontWeight = if (el.isBold) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Active Element Property & Layering Controls
            activeElement?.let { el ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Đang chọn: ${el.type.label} (Lớp Z=${el.zIndex})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PowerPointOrange
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { updateActiveElement { it.copy(zIndex = it.zIndex + 1) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.FlipToFront, contentDescription = "Đưa lên trước")
                                }
                                IconButton(
                                    onClick = { updateActiveElement { it.copy(zIndex = (it.zIndex - 1).coerceAtLeast(1)) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.FlipToBack, contentDescription = "Đưa ra sau")
                                }
                                IconButton(
                                    onClick = { updateActiveElement { it.copy(rotationDeg = (it.rotationDeg + 15f) % 360f) } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Xoay 15°")
                                }
                                if (currentSlide.elements.size > 1) {
                                    IconButton(
                                        onClick = {
                                            val remaining = currentSlide.elements.filterIndexed { i, _ -> i != safeElIdx }
                                            updateCurrentSlide { it.copy(elements = remaining) }
                                            selectedElementIdx = 0
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa đối tượng", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = el.text,
                            onValueChange = { txt -> updateActiveElement { it.copy(text = txt) } },
                            label = { Text("Nội dung chữ trực tiếp trên đối tượng") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ppt_element_text_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vị trí X (${el.xPercent.toInt()}%)", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = el.xPercent,
                                    onValueChange = { v -> updateActiveElement { it.copy(xPercent = v) } },
                                    valueRange = 0f..75f
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vị trí Y (${el.yPercent.toInt()}%)", style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = el.yPercent,
                                    onValueChange = { v -> updateActiveElement { it.copy(yPercent = v) } },
                                    valueRange = 0f..75f
                                )
                            }
                        }
                    }
                }
            }

            // Speaker Notes Editor
            OutlinedTextField(
                value = currentSlide.speakerNotes,
                onValueChange = { notes -> updateCurrentSlide { it.copy(speakerNotes = notes) } },
                label = { Text("Ghi chú thuyết trình (Speaker Notes - Slide ${safeSlideIdx + 1})") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ppt_speaker_notes_input")
            )
        }

        // Bottom Slide Sorter Strip (Add, Duplicate, Reorder, Delete Slide)
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                model.slides.forEachIndexed { idx, slide ->
                    FilterChip(
                        selected = idx == safeSlideIdx,
                        onClick = {
                            activeSlideIndex = idx
                            selectedElementIdx = 0
                        },
                        label = { Text("Slide ${idx + 1}") },
                        modifier = Modifier.testTag("ppt_slide_tab_$idx")
                    )
                }
                AssistChip(
                    onClick = {
                        val newSlide = PptSlide(
                            id = "slide_${System.currentTimeMillis()}",
                            slideNumber = model.slides.size + 1,
                            speakerNotes = "Ghi chú cho Slide ${model.slides.size + 1}",
                            elements = listOf(
                                SlideElement(
                                    id = "el_title_${System.currentTimeMillis()}",
                                    text = "Tiêu đề Slide ${model.slides.size + 1}",
                                    isBold = true,
                                    fontSizePt = 24
                                )
                            )
                        )
                        viewModel.updatePptModel(session.document.id, model.copy(slides = model.slides + newSlide))
                        activeSlideIndex = model.slides.size
                    },
                    label = { Text("+ Thêm Slide") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("ppt_add_slide_chip")
                )
                IconButton(onClick = {
                    val dup = currentSlide.copy(
                        id = "slide_dup_${System.currentTimeMillis()}",
                        slideNumber = model.slides.size + 1
                    )
                    viewModel.updatePptModel(session.document.id, model.copy(slides = model.slides + dup))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Nhân bản Slide", modifier = Modifier.size(18.dp))
                }
                if (model.slides.size > 1) {
                    IconButton(onClick = {
                        val remaining = model.slides.filterIndexed { i, _ -> i != safeSlideIdx }
                        viewModel.updatePptModel(session.document.id, model.copy(slides = remaining))
                        activeSlideIndex = 0
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa Slide", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
