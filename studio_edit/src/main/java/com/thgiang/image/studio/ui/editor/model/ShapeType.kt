package com.thgiang.image.studio.ui.editor.model

enum class LayerType : java.io.Serializable {
    IMAGE,
    /** Khung — decorative shape geometry (Phase 2). */
    SHAPE,
    /** Nhãn — text content (Phase 2). */
    TEXT,
    /** @deprecated Legacy combined layer; normalized to SHAPE/TEXT or group on load. */
    SHAPE_TEXT,
    SHADOW_REGION,
}

enum class ShapeType : java.io.Serializable {
    /** Fully rounded ends (stadium shape) */
    PILL,
    /** Standard rectangle with slightly rounded corners */
    CARD,
    /** Teardrop / speech bubble — 3 rounded corners + 1 sharp pointer at bottom-left */
    TEARDROP,
    /** Perfect circle */
    CIRCLE,
    /** 5-pointed star */
    STAR,
    /** Flat-topped regular hexagon */
    HEXAGON,
    /** Equilateral triangle pointing up */
    TRIANGLE,
    /** Horizontal line (stroke only) */
    LINE,
    /** Rhombus / diamond */
    DIAMOND,
    /** Arrow from Fabric path */
    ARROW,
    /** Arbitrary SVG path */
    PATH,
    /** Polygon from exported point list */
    POLYGON,
    /** Slanted quadrilateral / banner */
    PARALLELOGRAM,
    /** Text only — no visible shape background or stroke */
    TEXT_ONLY,
}
