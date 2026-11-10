package com.dahl404.penny937.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.imePadding
import com.dahl404.penny937.data.ProjectMeta
import com.dahl404.penny937.data.ProjectStore
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.ui.components.ColorCircle
import com.dahl404.penny937.ui.components.Hairline
import com.dahl404.penny937.ui.components.PennyIconButton
import com.dahl404.penny937.ui.components.PennyMascot
import com.dahl404.penny937.ui.components.PennySheet
import com.dahl404.penny937.ui.components.PennySlider
import com.dahl404.penny937.ui.components.PennyTextField
import com.dahl404.penny937.ui.components.PrimaryButton
import com.dahl404.penny937.ui.components.SecondaryButton
import com.dahl404.penny937.ui.components.SectionLabel
import com.dahl404.penny937.ui.editor.ConfirmDeleteSheet
import com.dahl404.penny937.ui.icons.PennyIcons
import com.dahl404.penny937.ui.theme.LocalPenny
import com.dahl404.penny937.ui.theme.LocalPennyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    store: ProjectStore,
    projects: List<ProjectMeta>,
    loading: Boolean,
    onOpen: (Long) -> Unit,
    onCreate: (String, Int, Int, Int, Int) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onImportArchive: (Uri) -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val pickArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportArchive(uri)
    }

    var showNew by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var actionVisible by remember { mutableStateOf(false) }
    var actionMeta by remember { mutableStateOf<ProjectMeta?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectMeta?>(null) }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 1100.dp)
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeHeader(onAbout = { showAbout = true })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    PrimaryButton(
                        text = if (projects.isEmpty()) "Start a project" else "New project",
                        onClick = { showNew = true },
                        icon = PennyIcons.Plus,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        text = "Import project (.zip)",
                        onClick = {
                            pickArchive.launch(
                                arrayOf("application/zip", "application/octet-stream")
                            )
                        },
                        icon = PennyIcons.Image,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Projects", style = type.title, color = palette.textPrimary)
                    Spacer(Modifier.weight(1f))
                    if (projects.isNotEmpty()) {
                        Text(
                            "${projects.size}",
                            style = type.mono,
                            color = palette.textTertiary
                        )
                    }
                }
            }
            if (projects.isEmpty() && !loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibrary()
                }
            }
            items(projects, key = { it.id }) { meta ->
                ProjectCard(
                    meta = meta,
                    store = store,
                    onClick = { onOpen(meta.id) },
                    onLongClick = {
                        actionMeta = meta
                        actionVisible = true
                    }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Hairline()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "PeNNy-937 — an offline sketchbook for people who draw frame by frame.",
                        style = type.caption,
                        color = palette.textTertiary,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        NewProjectSheet(
            visible = showNew,
            onDismiss = { showNew = false },
            onCreate = { name, w, h, fps, bg ->
                showNew = false
                onCreate(name, w, h, fps, bg)
            }
        )

        ProjectActionSheet(
            visible = actionVisible,
            meta = actionMeta,
            onDismiss = { actionVisible = false },
            onDuplicate = { onDuplicate(it); actionVisible = false },
            onDelete = { id ->
                deleteTarget = projects.firstOrNull { it.id == id }
                actionVisible = false
            }
        )

        ConfirmDeleteSheet(
            visible = deleteTarget != null,
            projectName = deleteTarget?.name ?: "",
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget?.let { onDelete(it.id) }
                deleteTarget = null
            }
        )

        AboutSheet(visible = showAbout, onDismiss = { showAbout = false })
    }
}

@Composable
private fun HomeHeader(onAbout: () -> Unit) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("PeNNy", style = type.hero, color = palette.textPrimary)
                Text(
                    "-937",
                    style = type.hero,
                    color = palette.accent,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "Frame-by-frame, by hand.",
                style = type.label,
                color = palette.textSecondary
            )
        }
        PennyIconButton(
            icon = PennyIcons.Info,
            onClick = onAbout,
            size = 38.dp,
            iconSize = 18.dp,
            tint = palette.textSecondary,
            background = palette.surface,
            contentDescription = "About PeNNy-937"
        )
    }
}

@Composable
private fun EmptyLibrary() {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PennyMascot(Modifier.size(148.dp))
        Spacer(Modifier.height(6.dp))
        Text("A blank page, waiting.", style = type.title, color = palette.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Every animation starts with a single line.",
            style = type.label,
            color = palette.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Tap Start a project above, or open the info button for a quick tour.",
            style = type.caption,
            color = palette.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    meta: ProjectMeta,
    store: ProjectStore,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(18.dp)

    val thumbnail by produceState<ImageBitmap?>(initialValue = null, meta.id, meta.updatedAt) {
        value = withContext(Dispatchers.IO) {
            store.loadThumbnail(meta.id)?.asImageBitmap()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .shadow(9.dp, shape, clip = false, ambientColor = palette.shadow, spotColor = palette.shadow)
                .clip(shape)
                .background(Color(meta.background))
                .border(0.6.dp, palette.hairline, shape)
        ) {
            val bmp = thumbnail
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        PennyIcons.Frame,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${meta.fps} fps",
                    style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            meta.name,
            style = type.headline,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${meta.frameCount} ${if (meta.frameCount == 1) "frame" else "frames"} · ${relativeTime(meta.updatedAt)}",
            style = type.caption,
            color = palette.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun BoxScope.NewProjectSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var widthText by remember { mutableStateOf("1080") }
    var heightText by remember { mutableStateOf("1920") }
    var fps by remember { mutableStateOf(12f) }
    var background by remember { mutableStateOf(ProjectFactory.backgrounds[0].second) }
    LaunchedEffect(visible) {
        if (visible) {
            name = ""
            widthText = "1080"
            heightText = "1920"
            fps = 12f
            background = ProjectFactory.backgrounds[0].second
        }
    }

    val width = widthText.toIntOrNull()
    val height = heightText.toIntOrNull()
    val validSize = width != null && height != null &&
        width in ProjectFactory.MIN_DIM..ProjectFactory.MAX_DIM &&
        height in ProjectFactory.MIN_DIM..ProjectFactory.MAX_DIM

    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "New project",
        subtitle = "Set the stage for your animation."
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            SectionLabel("NAME")
            Spacer(Modifier.height(7.dp))
            PennyTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                placeholder = "Untitled",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))

            SectionLabel("CANVAS")
            Spacer(Modifier.height(8.dp))
            val presets = ProjectFactory.sizePresets
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 2) {
                    SizePreset(
                        label = presets[i].first,
                        dims = "${presets[i].second} × ${presets[i].third}",
                        selected = widthText == presets[i].second.toString() &&
                            heightText == presets[i].third.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            widthText = presets[i].second.toString()
                            heightText = presets[i].third.toString()
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 2 until presets.size) {
                    SizePreset(
                        label = presets[i].first,
                        dims = "${presets[i].second} × ${presets[i].third}",
                        selected = widthText == presets[i].second.toString() &&
                            heightText == presets[i].third.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            widthText = presets[i].second.toString()
                            heightText = presets[i].third.toString()
                        }
                    )
                }
                if (presets.size < 4) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PennyTextField(
                    value = widthText,
                    onValueChange = { widthText = it.filter(Char::isDigit).take(4) },
                    placeholder = "W",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
                Text(
                    "×",
                    style = LocalPennyType.current.label,
                    color = LocalPenny.current.textTertiary,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                PennyTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter(Char::isDigit).take(4) },
                    placeholder = "H",
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                when {
                    !validSize -> "Each side needs to be between 32 and 4096 px"
                    (width ?: 0).toLong() * (height ?: 0) > 8_000_000L ->
                        "Large canvas — rendering will use a lot of memory."
                    else -> "Entered in pixels · 32 to 4096 on each side"
                },
                style = LocalPennyType.current.caption,
                color = if (validSize) LocalPenny.current.textTertiary else LocalPenny.current.danger
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("FRAME RATE")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${fps.roundToInt()}",
                    style = LocalPennyType.current.title,
                    color = LocalPenny.current.textPrimary
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "fps",
                    style = LocalPennyType.current.caption,
                    color = LocalPenny.current.textSecondary
                )
            }
            PennySlider(
                value = fps,
                onValueChange = { fps = it },
                valueRange = ProjectFactory.MIN_FPS.toFloat()..ProjectFactory.MAX_FPS.toFloat()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in listOf(8, 12, 24, 30, 60)) {
                    SmallChoice(
                        label = "$option",
                        selected = fps.roundToInt() == option,
                        onClick = { fps = option.toFloat() }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("PAPER")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((label, color) in ProjectFactory.backgrounds) {
                    ColorCircle(
                        color = Color(color),
                        size = 34.dp,
                        selected = background == color,
                        onClick = { background = color }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                text = "Create project",
                onClick = {
                    onCreate(
                        name.ifBlank { "Untitled" },
                        width ?: 1080,
                        height ?: 1920,
                        fps.roundToInt(),
                        background
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Plus,
                enabled = validSize
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Everything stays vector. You can change the canvas, frame rate and paper later without redrawing.",
                style = LocalPennyType.current.caption,
                color = LocalPenny.current.textTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun BoxScope.ProjectActionSheet(
    visible: Boolean,
    meta: ProjectMeta?,
    onDismiss: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = meta?.name,
        subtitle = meta?.let {
            "${it.width} × ${it.height} · ${it.frameCount} ${if (it.frameCount == 1) "frame" else "frames"} · ${it.fps} fps"
        }
    ) {
        if (meta != null) {
            SecondaryButton(
                text = "Duplicate",
                onClick = { onDuplicate(meta.id) },
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Copy
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Delete project",
                onClick = { onDelete(meta.id) },
                modifier = Modifier.fillMaxWidth(),
                icon = PennyIcons.Trash,
                tint = palette.danger
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Deleting removes the frames and the thumbnail from this device.",
                style = type.caption,
                color = palette.textTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BoxScope.AboutSheet(visible: Boolean, onDismiss: () -> Unit) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    PennySheet(
        visible = visible,
        onDismiss = onDismiss,
        title = "PeNNy-937",
        subtitle = "A small studio that fits in your hand."
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PennyMascot(Modifier.size(132.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Named after nothing in particular. Built to feel like a real pen.",
                style = type.body,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            TipRow("Stylus draws. Two fingers pan and zoom.")
            TipRow("Press the pen's side button to erase for that stroke.")
            TipRow("Slide the onion control left for a blue ghost of the last frame, right for a red ghost of the next. Centre is off.")
            TipRow("Drag the handle under a frame to rearrange the timeline; long-press a frame for its options.")
            TipRow("The sliders in the tool rail open brush and shape settings.")
            TipRow("Every frame has its own unlimited layers.")
            TipRow("Import a reference image from Layers, drag to place, pinch to scale and rotate. It locks itself and is never exported.")
            TipRow("Import a video from Layers: it samples at the project fps and fills a locked reference into each frame. Import another to continue after the clip.")
            TipRow("Open Color and tap Pick from Layer to sample ink from the selected layer. Drag for a live loupe, lift to keep, or cancel to revert.")
            TipRow("Line, rectangle, square, ellipse and circle are one drag away.")
            TipRow("Grid and thirds guides live in the canvas corner.")
            TipRow("Canvas size, frame rate and paper are yours to choose.")
            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Text(
                "Version 1.6 · Made for stylus and patience",
                style = type.caption,
                color = palette.textTertiary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Typeface: Inter · SIL Open Font License",
                style = type.caption,
                color = palette.textTertiary
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TipRow(text: String) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.accent)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = type.body, color = palette.textPrimary)
    }
}

@Composable
private fun SizePreset(
    label: String,
    dims: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) palette.accentSoft else palette.surfaceSunken)
            .border(
                if (selected) 1.2.dp else 0.6.dp,
                if (selected) palette.accent else palette.hairline,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(
            label,
            style = type.label.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) palette.accent else palette.textPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(dims, style = type.caption, color = palette.textSecondary)
    }
}

@Composable
private fun SmallChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPenny.current
    val type = LocalPennyType.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) palette.textPrimary else palette.surfaceSunken)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) palette.surface else palette.textSecondary
        )
    }
}

private fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val delta = (now - timestamp).coerceAtLeast(0)
    return when {
        delta < 90_000 -> "just now"
        delta < 3_600_000 -> "${delta / 60_000} min ago"
        delta < 86_400_000 -> "${delta / 3_600_000} h ago"
        delta < 7L * 86_400_000 -> "${delta / 86_400_000} d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
