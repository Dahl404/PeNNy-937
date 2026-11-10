package com.dahl404.penny937.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dahl404.penny937.data.Prefs
import com.dahl404.penny937.data.ProjectMeta
import com.dahl404.penny937.data.ProjectStore
import com.dahl404.penny937.editor.EditorModel
import com.dahl404.penny937.model.Frame
import com.dahl404.penny937.model.Ids
import com.dahl404.penny937.model.Layer
import com.dahl404.penny937.model.LayerImage
import com.dahl404.penny937.model.Project
import com.dahl404.penny937.model.ProjectFactory
import com.dahl404.penny937.model.ProjectOps
import com.dahl404.penny937.model.Stroke
import com.dahl404.penny937.model.Tool
import com.dahl404.penny937.ui.components.ChoiceChip
import com.dahl404.penny937.ui.components.ColorCircle
import com.dahl404.penny937.ui.components.PennyCard
import com.dahl404.penny937.ui.components.PennyIconButton
import com.dahl404.penny937.ui.components.PennyMascot
import com.dahl404.penny937.ui.components.PennySlider
import com.dahl404.penny937.ui.components.PennyTextField
import com.dahl404.penny937.ui.components.PennyToggle
import com.dahl404.penny937.ui.components.PrimaryButton
import com.dahl404.penny937.ui.components.SecondaryButton
import com.dahl404.penny937.ui.editor.BrushSheet
import com.dahl404.penny937.ui.editor.ColorSheet
import com.dahl404.penny937.ui.editor.EditorScreen
import com.dahl404.penny937.ui.editor.ExportSheet
import com.dahl404.penny937.ui.editor.FrameSheet
import com.dahl404.penny937.ui.editor.ProjectMenuSheet
import com.dahl404.penny937.ui.editor.SettingsSheet
import com.dahl404.penny937.ui.editor.LayersSheet
import com.dahl404.penny937.ui.icons.PennyIcons
import com.dahl404.penny937.ui.theme.LocalPenny
import com.dahl404.penny937.ui.theme.PennyTheme

@Composable
private fun previewModel(): EditorModel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        val project = ProjectFactory.create(
            name = "Moth study",
            width = 1080,
            height = 1920,
            fps = 12,
            background = ProjectFactory.backgrounds[0].second
        )
        EditorModel(
            context = context,
            store = ProjectStore(context),
            prefs = Prefs(context),
            initial = project,
            scope = scope
        )
    }
}

@Preview(name = "Home — populated", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun HomePopulatedPreview() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context) }
    val metas = remember {
        listOf(
            ProjectMeta(1, "Moth study", 1080, 1920, 12, 18, System.currentTimeMillis() - 240_000, 0xFFFBF9F4.toInt(), false),
            ProjectMeta(2, "Coffee steam", 1920, 1080, 24, 6, System.currentTimeMillis() - 8_400_000, 0xFFFFFFFF.toInt(), false),
            ProjectMeta(3, "Tiny dance", 1080, 1080, 15, 32, System.currentTimeMillis() - 93_600_000, 0xFFF0F0F2.toInt(), false)
        )
    }
    PennyTheme {
        HomeScreen(
            store = store,
            projects = metas,
            loading = false,
            onOpen = {},
            onCreate = { _, _, _, _, _ -> },
            onDuplicate = {},
            onDelete = {},
            onImportArchive = {}
        )
    }
}

@Preview(name = "Home — empty", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun HomeEmptyPreview() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context) }
    PennyTheme {
        HomeScreen(
            store = store,
            projects = emptyList(),
            loading = false,
            onOpen = {},
            onCreate = { _, _, _, _, _ -> },
            onDuplicate = {},
            onDelete = {},
            onImportArchive = {}
        )
    }
}

private fun stroke(tool: Tool, color: Int, width: Float, opacity: Float, vararg pts: FloatArray): Stroke {
    val flat = FloatArray(pts.size * 3)
    pts.forEachIndexed { i, p ->
        flat[i * 3] = p[0]
        flat[i * 3 + 1] = p[1]
        flat[i * 3 + 2] = p[2]
    }
    return Stroke(tool, color, width, opacity, flat)
}

private fun sampleProject(): Project {
    val now = System.currentTimeMillis()
    fun arc(cx: Float, cy: Float, r: Float, n: Int, wobble: Float = 0f): Array<FloatArray> =
        Array(n) { i ->
            val t = i / (n - 1f) * 6.2831855f
            floatArrayOf(
                cx + kotlin.math.cos(t) * r,
                cy + kotlin.math.sin(t) * r * 0.8f,
                0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(t * 1.4f)) + wobble
            )
        }
    fun wave(y: Float, amplitude: Float, offset: Float): Array<FloatArray> =
        Array(70) { i ->
            val t = i / 69f
            floatArrayOf(
                150f + t * 780f,
                y + kotlin.math.sin(t * 7f + offset) * amplitude,
                0.2f + 0.8f * kotlin.math.sin(t * Math.PI).toFloat()
            )
        }
    val frame1 = Frame(
        id = Ids.next(),
        layers = listOf(
            Layer(
                id = Ids.next(),
                name = "Line",
                strokes = listOf(
                    stroke(Tool.PEN, 0xFF1D1D1F.toInt(), 9f, 1f, *arc(540f, 780f, 280f, 150)),
                    stroke(Tool.PEN, 0xFF1D1D1F.toInt(), 7f, 1f, *wave(400f, 90f, 0.4f))
                )
            ),
            Layer(
                id = Ids.next(),
                name = "Color",
                opacity = 0.9f,
                strokes = listOf(
                    stroke(Tool.MARKER, 0xFFFF9500.toInt(), 120f, 0.5f, *wave(1100f, 60f, 2.2f))
                )
            )
        )
    )
    val frame2 = Frame(
        id = Ids.next(),
        layers = listOf(
            Layer(
                id = Ids.next(),
                name = "Line",
                strokes = listOf(
                    stroke(Tool.PEN, 0xFF1D1D1F.toInt(), 9f, 1f, *arc(565f, 760f, 280f, 150)),
                    stroke(Tool.PENCIL, 0xFF1D1D1F.toInt(), 26f, 1f, *wave(400f, 90f, 0.9f))
                )
            ),
            Layer(
                id = Ids.next(),
                name = "Hatch",
                strokes = listOf(
                    stroke(Tool.PEN, 0xFF007AFF.toInt(), 18f, 1f, *wave(1300f, 40f, 1.6f))
                )
            )
        )
    )
    val frame3 = Frame(
        id = Ids.next(),
        layers = listOf(
            Layer(
                id = Ids.next(),
                name = "Line",
                strokes = listOf(
                    stroke(Tool.PEN, 0xFF1D1D1F.toInt(), 9f, 1f, *arc(600f, 740f, 300f, 150))
                )
            )
        )
    )
    return Project(
        id = Ids.next(),
        name = "Moth study",
        width = 1080,
        height = 1920,
        fps = 12,
        background = 0xFFFBF9F4.toInt(),
        frames = listOf(frame1, frame2, frame3),
        createdAt = now,
        updatedAt = now
    )
}

@Preview(name = "Editor — sketched", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun EditorSketchedPreview() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember {
        EditorModel(
            context = context,
            store = ProjectStore(context),
            prefs = Prefs(context),
            initial = sampleProject(),
            scope = scope
        ).also { it.selectFrame(1) }
    }
    PennyTheme {
        EditorScreen(
            editor = model,
            onClose = {},
            onDuplicateProject = {},
            onDeleteProject = {}
        )
    }
}

@Preview(name = "Editor", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun EditorPreview() {
    val model = previewModel()
    PennyTheme {
        EditorScreen(
            editor = model,
            onClose = {},
            onDuplicateProject = {},
            onDeleteProject = {}
        )
    }
}

@Preview(name = "Color sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ColorSheetPreview() {
    val model = previewModel()
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            ColorSheet(
                visible = true,
                editor = model,
                onDismiss = {},
                onPickFromLayer = {}
            )
        }
    }
}

@Preview(name = "Color picker", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun EyedropperPreview() {
    val model = previewModel()
    remember(model) { model.beginEyedropper() }
    PennyTheme {
        EditorScreen(
            editor = model,
            onClose = {},
            onDuplicateProject = {},
            onDeleteProject = {}
        )
    }
}

@Preview(name = "Brush sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun BrushSheetPreview() {
    val model = previewModel()
    model.chooseTool(Tool.PENCIL)
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            BrushSheet(visible = true, editor = model, onDismiss = {})
        }
    }
}

@Preview(name = "Shape brush sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ShapeBrushSheetPreview() {
    val model = previewModel()
    model.chooseTool(Tool.SHAPE)
    model.chooseShape(com.dahl404.penny937.model.ShapeKind.CIRCLE)
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            BrushSheet(visible = true, editor = model, onDismiss = {})
        }
    }
}

@Preview(name = "Editor with grid and shape", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun EditorGridPreview() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember {
        EditorModel(
            context = context,
            store = ProjectStore(context),
            prefs = Prefs(context),
            initial = sampleProject(),
            scope = scope
        ).also {
            it.selectFrame(1)
            it.toggleGrid()
            it.updateOnionStrength(-0.55f)
            it.chooseTool(Tool.SHAPE)
            it.chooseShape(com.dahl404.penny937.model.ShapeKind.CIRCLE)
        }
    }
    PennyTheme {
        EditorScreen(model, {}, {}, {})
    }
}

@Composable
private fun referenceModel(enterPlacement: Boolean = false): EditorModel {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember {
        var project = ProjectFactory.create(
            name = "Reference study",
            width = 1080,
            height = 1920,
            fps = 12,
            background = ProjectFactory.backgrounds[0].second
        )
        project = ProjectOps.addReferenceLayer(
            project, 0,
            ProjectFactory.referenceLayer(
                "Reference",
                LayerImage(
                    file = "preview-ref.png",
                    sourceWidth = 1200,
                    sourceHeight = 900,
                    centerX = 540f,
                    centerY = 960f,
                    width = 800f,
                    height = 600f,
                    rotation = 8f
                )
            )
        )
        EditorModel(
            context = context,
            store = ProjectStore(context),
            prefs = Prefs(context),
            initial = project,
            scope = scope
        ).also { model ->
            model.selectLayer(0)
            if (enterPlacement) model.beginReposition(model.layer)
        }
    }
}

@Preview(name = "Layers sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun LayersSheetPreview() {
    val model = referenceModel()
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            LayersSheet(
                visible = true,
                editor = model,
                onImportReference = {},
                onImportVideo = {},
                onPositionReference = {},
                onShiftLayer = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "Reference placement", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun PlacementPreview() {
    val model = referenceModel(enterPlacement = true)
    PennyTheme {
        EditorScreen(model, {}, {}, {})
    }
}

@Preview(name = "New project", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun NewProjectSheetPreview() {
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            NewProjectSheet(visible = true, onDismiss = {}, onCreate = { _, _, _, _, _ -> })
        }
    }
}

@Preview(name = "Settings sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun SettingsSheetPreview() {
    val model = previewModel()
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            SettingsSheet(visible = true, editor = model, onDismiss = {})
        }
    }
}

@Preview(name = "Export sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ExportSheetPreview() {
    val model = previewModel()
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            ExportSheet(visible = true, editor = model, onDismiss = {})
        }
    }
}

@Preview(name = "Project menu", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ProjectMenuPreview() {
    val model = previewModel()
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            ProjectMenuSheet(
                visible = true,
                editor = model,
                onDismiss = {},
                onOpenSettings = {},
                onOpenExport = {},
                onDuplicateProject = {},
                onDeleteProject = {}
            )
        }
    }
}

@Preview(name = "Frame sheet", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun FrameSheetPreview() {
    val model = previewModel()
    PennyTheme {
        Box(Modifier.fillMaxSize()) {
            FrameSheet(visible = true, editor = model, onDismiss = {})
        }
    }
}

@Preview(name = "Dark home", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun DarkHomePreview() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context) }
    val metas = remember {
        listOf(
            ProjectMeta(1, "Moth study", 1080, 1920, 12, 18, System.currentTimeMillis() - 240_000, 0xFFFBF9F4.toInt(), false),
            ProjectMeta(2, "Coffee steam", 1920, 1080, 24, 6, System.currentTimeMillis() - 8_400_000, 0xFFFFFFFF.toInt(), false)
        )
    }
    PennyTheme(darkTheme = true) {
        HomeScreen(store, metas, false, {}, { _, _, _, _, _ -> }, {}, {}, {})
    }
}

@Preview(name = "Dark editor", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun DarkEditorPreview() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember {
        EditorModel(
            context = context,
            store = ProjectStore(context),
            prefs = Prefs(context),
            initial = sampleProject(),
            scope = scope
        ).also { it.selectFrame(1) }
    }
    PennyTheme(darkTheme = true) {
        EditorScreen(model, {}, {}, {})
    }
}

@Preview(name = "Components", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun ComponentsPreview() {
    PennyTheme {
        val palette = LocalPenny.current
        Column(
            Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PennyMascot(Modifier.size(120.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    PrimaryButton("New project", {}, icon = PennyIcons.Plus)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Duplicate", {}, icon = PennyIcons.Copy)
                }
            }
            PennyCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PennyTextField("", {}, "Untitled")
                    PennySlider(0.4f, {}, 0f..1f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("GIF", true, {})
                        ChoiceChip("Frames", false, {})
                        PennyToggle(true, {})
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PennyIconButton(PennyIcons.Pen, {}, background = palette.surface, contentDescription = null)
                PennyIconButton(PennyIcons.Pencil, {}, background = palette.surface, contentDescription = null)
                PennyIconButton(PennyIcons.Marker, {}, background = palette.surface, contentDescription = null)
                PennyIconButton(PennyIcons.Eraser, {}, background = palette.surface, contentDescription = null)
                ColorCircle(Color(0xFF0071E3), 30.dp)
            }
        }
    }
}
