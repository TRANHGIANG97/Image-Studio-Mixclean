package com.toshiba.modnet

enum class BackgroundMaskModel(
    val label: String,
    val assetName: String,
    val inputSize: Int? = null,
) {
    ML_KIT("ML Kit", "mlkit_subject_segmentation"),
    U2NETP("U2Netp FP16 320", "u2netp.onnx", 320),
}

enum class CoreMaskModel(
    val label: String,
) {
    ML_KIT("ML Kit"),
}

data class MaskModelResult(
    val mask: Mask,
    val confidencePercent: Int,
    val route: String,
    val elapsedMs: Long,
)

data class FusionLabResult(
    val title: String,
    val result: RemoveBgResult,
)

data class FusionLabCompareResult(
    val results: List<FusionLabResult>,
    val totalMs: Long,
)
