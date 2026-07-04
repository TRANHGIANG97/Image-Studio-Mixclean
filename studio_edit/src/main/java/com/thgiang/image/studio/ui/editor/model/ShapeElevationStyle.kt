package com.thgiang.image.studio.ui.editor.model

/** Word-style 3D direction: raised (nổi lên) or inset (chìm). */
enum class ShapeElevationStyle {
    RAISED,
    INSET,
}

/** Whether 3-D depth applies to the shape geometry or text glyphs. */
enum class ElevationTarget {
    SHAPE,
    TEXT,
}
