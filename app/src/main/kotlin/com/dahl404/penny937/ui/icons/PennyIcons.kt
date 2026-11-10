package com.dahl404.penny937.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** A bespoke thin-line icon set so the app never looks like stock Material. */
object PennyIcons {

    private fun line(name: String, vararg paths: String, width: Float = 1.8f): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        for (p in paths) {
            builder.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    private fun filled(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        for (p in paths) {
            builder.addPath(
                pathData = addPathNodes(p),
                fill = SolidColor(Color.Black),
                stroke = null
            )
        }
        return builder.build()
    }

    val ChevronLeft = line("chevronLeft", "M15 18 L9 12 L15 6")
    val ChevronRight = line("chevronRight", "M9 18 L15 12 L9 6")
    val ChevronUp = line("chevronUp", "M6 15 L12 9 L18 15")
    val ChevronDown = line("chevronDown", "M6 9 L12 15 L18 9")
    val ArrowUp = line("arrowUp", "M12 19 V5", "M6 11 L12 5 L18 11")
    val ArrowDown = line("arrowDown", "M12 5 V19", "M6 13 L12 19 L18 13")

    val Undo = line(
        "undo",
        "M9 14.5 L4 9.5 L9 4.5",
        "M4 9.5 H14 A5.4 5.4 0 0 1 14 20.3 H10.5"
    )
    val Redo = line(
        "redo",
        "M15 14.5 L20 9.5 L15 4.5",
        "M20 9.5 H10 A5.4 5.4 0 0 0 10 20.3 H13.5"
    )

    val Play = filled("play", "M8 4.8 L19.2 12 L8 19.2 Z")
    val Pause = filled("pause", "M7 5 H10.4 V19 H7 Z", "M13.6 5 H17 V19 H13.6 Z")
    val Plus = line("plus", "M12 5 V19", "M5 12 H19")
    val Minus = line("minus", "M5 12 H19")
    val Close = line("close", "M6 6 L18 18", "M18 6 L6 18")
    val Check = line("check", "M4.5 12.5 L9.5 17.5 L19.5 6.5")

    val Copy = line(
        "copy",
        "M9 9 H19 A1 1 0 0 1 20 10 V20 A1 1 0 0 1 19 21 H9 A1 1 0 0 1 8 20 V10 A1 1 0 0 1 9 9 Z",
        "M16 9 V5 A1 1 0 0 0 15 4 H5 A1 1 0 0 0 4 5 V15 A1 1 0 0 0 5 16 H8"
    )
    val Trash = line(
        "trash",
        "M4 7 H20",
        "M9 7 V4.5 H15 V7",
        "M6.5 7 L7.5 20.5 H16.5 L17.5 7",
        "M10 11 V17",
        "M14 11 V17"
    )

    val Pen = line(
        "pen",
        "M7 4.5 H17 C17 10.8 14.8 17.2 12 21.5 C9.2 17.2 7 10.8 7 4.5 Z",
        "M7 4.5 H17",
        "M12 12.5 V19.5",
        "M12 10.5 A1.35 1.35 0 1 0 12 7.8 A1.35 1.35 0 1 0 12 10.5 Z"
    )
    val Pencil = line(
        "pencil",
        "M4 20 L5 15.4 L16.2 4.2 A2.6 2.6 0 0 1 19.8 7.8 L8.6 19 Z",
        "M4 20 L8.6 19",
        "M6.2 15.2 L8.8 17.8",
        "M15.2 5.2 L18.8 8.8"
    )
    val Marker = line(
        "marker",
        "M13.8 2.9 L21.1 10.2 L12.4 18.9 L5.1 11.6 Z",
        "M5.1 11.6 L3.2 19.2 A1.3 1.3 0 0 0 4.8 20.8 L12.4 18.9",
        "M10.5 8.6 L15.4 13.5",
        "M3 22 H12"
    )
    val Eyedropper = filled(
        "eyedropper",
        "M17.67 3c-.26 0-.51.1-.71.29l-3.12 3.12-1.93-1.91-1.41 1.41 1.42 1.42L3 16.25V21h4.75l8.92-8.92 1.42 1.42 1.41-1.41-1.92-1.92 3.12-3.12c.4-.4.4-1.03.01-1.42l-2.34-2.34c-.2-.19-.45-.29-.7-.29zM6.92 19L5 17.08l8.06-8.06 1.92 1.92L6.92 19z"
    )
    val Eraser = line(
        "eraser",
        "M13.2 4.8 A2.6 2.6 0 0 1 16.9 4.8 L20.4 8.3 A2.6 2.6 0 0 1 20.4 12 L12.4 20 H8.5 L3.6 15.1 A2.6 2.6 0 0 1 3.6 11.4 Z",
        "M7 10 L16 19",
        "M7.5 20.5 H20.5"
    )

    val Layers = line(
        "layers",
        "M12 3 L21 8 L12 13 L3 8 Z",
        "M3 12 L12 17 L21 12",
        "M3 16 L12 21 L21 16"
    )
    val Eye = line(
        "eye",
        "M12 5 C6.5 5 2.5 9.4 1.8 12 C2.5 14.6 6.5 19 12 19 C17.5 19 21.5 14.6 22.2 12 C21.5 9.4 17.5 5 12 5 Z",
        "M12 15 A3 3 0 1 0 12 9 A3 3 0 1 0 12 15 Z"
    )
    val EyeOff = line(
        "eyeOff",
        "M4.2 9.2 C3 10.2 2.2 11.3 1.8 12 C2.5 14.6 6.5 19 12 19 C13.7 19 15.2 18.5 16.5 17.8",
        "M9.6 5.4 C10.4 5.1 11.2 5 12 5 C17.5 5 21.5 9.4 22.2 12 C21.8 13.1 21 14.4 19.9 15.5",
        "M9.9 9.9 A3 3 0 0 0 14.1 14.1",
        "M4 4 L20 20"
    )
    val Onion = line(
        "onion",
        "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z",
        "M12 7.5 A4.5 4.5 0 1 0 12 16.5 A4.5 4.5 0 1 0 12 7.5 Z"
    )
    val Fit = line(
        "fit",
        "M4 9 V4 H9",
        "M15 4 H20 V9",
        "M20 15 V20 H15",
        "M9 20 H4 V15"
    )
    val Share = line(
        "share",
        "M12 16 V4",
        "M7.5 8.5 L12 4 L16.5 8.5",
        "M5 14 V19.5 H19 V14"
    )
    val Sliders = line(
        "sliders",
        "M3 7 H21", "M3 12 H21", "M3 17 H21",
        "M8 9 A2 2 0 1 0 8 5 A2 2 0 1 0 8 9 Z",
        "M15.5 14 A2 2 0 1 0 15.5 10 A2 2 0 1 0 15.5 14 Z",
        "M6 19 A2 2 0 1 0 6 15 A2 2 0 1 0 6 19 Z"
    )
    val SlidersFilled = filled(
        "slidersFilled",
        "M8 9 A2 2 0 1 0 8 5 A2 2 0 1 0 8 9 Z",
        "M15.5 14 A2 2 0 1 0 15.5 10 A2 2 0 1 0 15.5 14 Z",
        "M6 19 A2 2 0 1 0 6 15 A2 2 0 1 0 6 19 Z"
    )
    val More = filled(
        "more",
        "M6 12 A1.7 1.7 0 1 0 6 8.6 A1.7 1.7 0 1 0 6 12 Z",
        "M12 12 A1.7 1.7 0 1 0 12 8.6 A1.7 1.7 0 1 0 12 12 Z",
        "M18 12 A1.7 1.7 0 1 0 18 8.6 A1.7 1.7 0 1 0 18 12 Z"
    )
    val Frame = line(
        "frame",
        "M4 6 H15 A1 1 0 0 1 16 7 V18 A1 1 0 0 1 15 19 H4 A1 1 0 0 1 3 18 V7 A1 1 0 0 1 4 6 Z",
        "M20 6 V12",
        "M17 9 H23"
    )
    val Timeline = line(
        "timeline",
        "M3 7 H8 A0.8 0.8 0 0 1 8.8 7.8 V16.2 A0.8 0.8 0 0 1 8 17 H3 A0.8 0.8 0 0 1 2.2 16.2 V7.8 A0.8 0.8 0 0 1 3 7 Z",
        "M9.6 7 H14.6 A0.8 0.8 0 0 1 15.4 7.8 V16.2 A0.8 0.8 0 0 1 14.6 17 H9.6 A0.8 0.8 0 0 1 8.8 16.2 V7.8 A0.8 0.8 0 0 1 9.6 7 Z",
        "M16.2 7 H21.2 A0.8 0.8 0 0 1 22 7.8 V16.2 A0.8 0.8 0 0 1 21.2 17 H16.2 A0.8 0.8 0 0 1 15.4 16.2 V7.8 A0.8 0.8 0 0 1 16.2 7 Z"
    )
    val Clock = line(
        "clock",
        "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z",
        "M12 7.5 V12.2 L15.4 14"
    )
    val Square = line(
        "square",
        "M5 5 H19 A1 1 0 0 1 20 6 V18 A1 1 0 0 1 19 19 H5 A1 1 0 0 1 4 18 V6 A1 1 0 0 1 5 5 Z"
    )
    val Stack = line(
        "stack",
        "M12 3 H20 A1 1 0 0 1 21 4 V12",
        "M3 8 H15 A1 1 0 0 1 16 9 V21 H3 Z"
    )
    val Stylus = line(
        "stylus",
        "M14 4 L20 10 L11 19 L5 19 L5 13 Z",
        "M5 19 L10 14",
        "M12.5 5.5 L18.5 11.5"
    )
    val Grid = line(
        "grid",
        "M4 4 H20 V20 H4 Z",
        "M9.34 4 V20",
        "M14.66 4 V20",
        "M4 9.34 H20",
        "M4 14.66 H20"
    )
    val LineShape = line("lineShape", "M4.5 19.5 L19.5 4.5")
    val RectangleShape = line(
        "rectangleShape",
        "M4 7 H20 A1 1 0 0 1 21 8 V16 A1 1 0 0 1 20 17 H4 A1 1 0 0 1 3 16 V8 A1 1 0 0 1 4 7 Z"
    )
    val CircleShape = line(
        "circleShape",
        "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z"
    )
    val EllipseShape = line(
        "ellipseShape",
        "M12 5.5 A9 6.5 0 1 0 12 18.5 A9 6.5 0 1 0 12 5.5 Z"
    )
    val Image = line(
        "image",
        "M4 5 H20 A1 1 0 0 1 21 6 V18 A1 1 0 0 1 20 19 H4 A1 1 0 0 1 3 18 V6 A1 1 0 0 1 4 5 Z",
        "M3 16 L8.5 10.5 L13 15 L16.5 11.5 L21 16",
        "M15.5 9 A1.4 1.4 0 1 0 15.5 6.2 A1.4 1.4 0 1 0 15.5 9 Z"
    )
    val Video = line(
        "video",
        "M4 6 H20 A1 1 0 0 1 21 7 V17 A1 1 0 0 1 20 18 H4 A1 1 0 0 1 3 17 V7 A1 1 0 0 1 4 6 Z",
        "M10 9.4 L15.8 12 L10 14.6 Z"
    )
    val Lock = line(
        "lock",
        "M6 10 H18 A1 1 0 0 1 19 11 V20 A1 1 0 0 1 18 21 H6 A1 1 0 0 1 5 20 V11 A1 1 0 0 1 6 10 Z",
        "M8 10 V7 A4 4 0 0 1 16 7 V10",
        "M12 14 V17"
    )
    val LockOpen = line(
        "lockOpen",
        "M6 10 H18 A1 1 0 0 1 19 11 V20 A1 1 0 0 1 18 21 H6 A1 1 0 0 1 5 20 V11 A1 1 0 0 1 6 10 Z",
        "M8 10 V7 A4 4 0 0 1 16 7 V8",
        "M12 14 V17"
    )
    val Move = line(
        "move",
        "M12 3 V21",
        "M3 12 H21",
        "M9 6 L12 3 L15 6",
        "M9 18 L12 21 L15 18",
        "M6 9 L3 12 L6 15",
        "M18 9 L21 12 L18 15"
    )
    val Rotate = line(
        "rotate",
        "M19.5 12 A7.5 7.5 0 1 1 12 4.5",
        "M9 6.4 L12 4.5 L12.4 8.1"
    )
    val Morph = line(
        "morph",
        "M3.2 12 A3.4 3.4 0 1 1 10 12 A3.4 3.4 0 1 1 3.2 12 Z",
        "M14 8.6 H20 A1.4 1.4 0 0 1 21.4 10 V14 A1.4 1.4 0 0 1 20 15.4 H14 A1.4 1.4 0 0 1 12.6 14 V10 A1.4 1.4 0 0 1 14 8.6 Z",
        "M10.5 10.5 L12 12 L10.5 13.5"
    )
    val Info = line(
        "info",
        "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z",
        "M12 11 V16.5",
        "M12 8 V8.1"
    )
}
