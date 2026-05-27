package com.toshiba.modnet

enum class MaskEngine(
    val label: String,
) {
    AUTO("Compare"),
    YOLO("YOLOv8n"),
    IS_NET("IS-Net"),
}
