package com.dahl404.penny937.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import com.dahl404.penny937.model.ShapeKind

fun shapeIcon(kind: ShapeKind): ImageVector = when (kind) {
    ShapeKind.LINE -> PennyIcons.LineShape
    ShapeKind.RECTANGLE -> PennyIcons.RectangleShape
    ShapeKind.SQUARE -> PennyIcons.Square
    ShapeKind.ELLIPSE -> PennyIcons.EllipseShape
    ShapeKind.CIRCLE -> PennyIcons.CircleShape
}
