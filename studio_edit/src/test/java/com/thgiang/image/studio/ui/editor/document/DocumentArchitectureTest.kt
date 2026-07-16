package com.thgiang.image.studio.ui.editor.document

import com.thgiang.image.studio.ui.editor.document.adapter.EditorLayerBridge
import com.thgiang.image.studio.ui.editor.document.command.DocumentCommand
import com.thgiang.image.studio.ui.editor.document.command.DocumentReducer
import com.thgiang.image.studio.ui.editor.document.model.DocTextAlign
import com.thgiang.image.studio.ui.editor.document.model.DocTextTransform
import com.thgiang.image.studio.ui.editor.document.model.DocumentSnapshot
import com.thgiang.image.studio.ui.editor.document.model.Effect
import com.thgiang.image.studio.ui.editor.document.model.Fill
import com.thgiang.image.studio.ui.editor.document.model.FrameGeometry
import com.thgiang.image.studio.ui.editor.document.model.HeightConstraint
import com.thgiang.image.studio.ui.editor.document.model.LayoutConstraints
import com.thgiang.image.studio.ui.editor.document.model.NodeTransform
import com.thgiang.image.studio.ui.editor.document.model.SceneNode
import com.thgiang.image.studio.ui.editor.document.model.StrokeSpec
import com.thgiang.image.studio.ui.editor.document.model.StyleBag
import com.thgiang.image.studio.ui.editor.document.model.TextContent
import com.thgiang.image.studio.ui.editor.document.model.TextRun
import com.thgiang.image.studio.ui.editor.document.model.WidthConstraint
import com.thgiang.image.studio.ui.editor.document.rules.EffectCompatibilityMatrix
import com.thgiang.image.studio.ui.editor.document.rules.LayoutIntent
import com.thgiang.image.studio.ui.editor.document.rules.LayoutPolicy
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorViewport
import com.thgiang.image.studio.ui.editor.model.LayerGroupOps
import com.thgiang.image.studio.ui.editor.model.LayerGroupRole
import com.thgiang.image.studio.ui.editor.model.LayerGroupSync
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.ShapeType
import com.thgiang.image.studio.ui.editor.model.TextFormEffect
import com.thgiang.image.studio.ui.editor.model.TextFormPreset
import com.thgiang.image.studio.ui.editor.model.withPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentArchitectureTest {

    @Test
    fun layoutPolicy_styleOrCaseChange_preservesWidth_hugsHeight() {
        val layout = LayoutConstraints(
            width = WidthConstraint.Fixed,
            height = HeightConstraint.Fixed,
            boxWidthPx = 200f,
            boxHeightPx = 180f,
        )
        val next = LayoutPolicy.applyIntent(layout, LayoutIntent.StyleOrCaseChange)
        assertEquals(WidthConstraint.Fixed, next.width)
        assertEquals(HeightConstraint.Hug, next.height)
        assertEquals(200f, next.boxWidthPx)
    }

    @Test
    fun layoutPolicy_fontSizeChange_preservesBoth() {
        val layout = LayoutConstraints(
            width = WidthConstraint.Hug,
            height = HeightConstraint.Hug,
            boxWidthPx = 120f,
            boxHeightPx = 80f,
        )
        val next = LayoutPolicy.applyIntent(layout, LayoutIntent.FontSizeChange)
        assertEquals(WidthConstraint.Fixed, next.width)
        assertEquals(HeightConstraint.Fixed, next.height)
    }

    @Test
    fun effectMatrix_disablesElevationWhenTextFormActive() {
        val style = StyleBag(
            effects = listOf(
                Effect.DropShadow(intensity = 0.4f),
                Effect.Elevation3D(intensity = 0.5f, depthSizePx = 12f),
            ),
            textForm = TextFormEffect().withPreset(TextFormPreset.PATH_ARC_UP),
        )
        val result = EffectCompatibilityMatrix.resolve(style)
        assertTrue(result.disabled.isNotEmpty())
        assertFalse(result.style.effects.any { it is Effect.Elevation3D })
        assertTrue(result.style.effects.any { it is Effect.DropShadow })
        assertTrue(result.style.textForm.isActive)
    }

    @Test
    fun reducer_editText_updatesContentAndRequestsLayout() {
        val node = SceneNode.PureText(
            id = "t1",
            content = TextContent.fromPlain("Hello", textSizeSp = 24f),
            layout = LayoutConstraints(boxWidthPx = 200f, boxHeightPx = 60f),
            style = StyleBag(),
        )
        val snap = DocumentSnapshot(nodes = listOf(node))
        val result = DocumentReducer.reduce(
            snap,
            DocumentCommand.EditText("t1", "Hello world", LayoutIntent.EditText),
        )
        val updated = result.snapshot.node("t1") as SceneNode.PureText
        assertEquals("Hello world", updated.content.text)
        assertEquals(setOf("t1"), result.layoutNodeIds)
        assertEquals(LayoutIntent.EditText, result.layoutIntent)
    }

    @Test
    fun reducer_applyStyleTemplate_isAtomic() {
        val node = SceneNode.PureText(
            id = "t1",
            content = TextContent.fromPlain("Sale", textSizeSp = 32f),
            layout = LayoutConstraints(boxWidthPx = 200f, boxHeightPx = 60f),
            style = StyleBag(effects = listOf(Effect.Elevation3D(intensity = 0.8f))),
        )
        val newStyle = StyleBag(
            fills = listOf(Fill.Solid(0xFFE53935.toInt())),
            effects = listOf(Effect.DropShadow(intensity = 0.2f)),
            textForm = TextFormEffect(),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.ApplyStyleTemplate(nodeId = "t1", textStyle = newStyle),
        )
        val updated = result.snapshot.node("t1") as SceneNode.PureText
        assertEquals(1, updated.style.fills.size)
        assertTrue(updated.style.effects.any { it is Effect.DropShadow })
        assertFalse(updated.style.effects.any { it is Effect.Elevation3D })
        assertEquals(HeightConstraint.Hug, updated.layout.height)
    }

    @Test
    fun bridge_pureText_roundTrip_preservesTextAndSize() {
        val layer = EditorLayer(
            id = "L1",
            type = LayerType.TEXT,
            text = "Round trip",
            textSizeSp = 28f,
            textColorArgb = 0xFF112233.toInt(),
            shapeType = ShapeType.TEXT_ONLY,
            shapeWidthPx = 220f,
            shapeHeightPx = 70f,
            textAlign = "center",
            textTransform = "uppercase",
            viewport = EditorViewport(offsetX = 10f, offsetY = 20f, scale = 1f),
        )
        val doc = EditorLayerBridge.fromEditorLayers(
            layers = listOf(layer),
            templateWidth = 1080f,
            templateHeight = 1080f,
            selectedLayerId = "L1",
        )
        assertTrue(doc.nodes.single() is SceneNode.PureText)
        val back = EditorLayerBridge.toEditorLayers(doc, listOf(layer))
        assertEquals(1, back.size)
        assertEquals("Round trip", back[0].text)
        assertEquals(28f, back[0].textSizeSp)
        assertEquals(220f, back[0].shapeWidthPx, 0.01f)
        assertEquals(ShapeType.TEXT_ONLY, back[0].shapeType)
        assertEquals("uppercase", back[0].textTransform)
    }

    @Test
    fun bridge_textInShape_roundTrip_keepsSharedTransform() {
        val gid = "g1"
        val frame = EditorLayer(
            id = "F1",
            type = LayerType.SHAPE,
            groupId = gid,
            groupRole = LayerGroupRole.FRAME,
            shapeType = ShapeType.CARD,
            shapeWidthPx = 300f,
            shapeHeightPx = 120f,
            shapeColorArgb = 0xFF2196F3.toInt(),
            viewport = EditorViewport(offsetX = 5f, offsetY = 8f),
            text = "",
        )
        val label = EditorLayer(
            id = "T1",
            type = LayerType.TEXT,
            groupId = gid,
            groupRole = LayerGroupRole.LABEL,
            text = "Label",
            textSizeSp = 20f,
            shapeType = ShapeType.CARD,
            shapeWidthPx = 300f,
            shapeHeightPx = 120f,
            viewport = EditorViewport(offsetX = 5f, offsetY = 8f),
        )
        val doc = EditorLayerBridge.fromEditorLayers(
            listOf(frame, label), 1080f, 1080f, "T1",
        )
        assertTrue(doc.nodes.single() is SceneNode.TextInShape)
        val back = EditorLayerBridge.toEditorLayers(doc, listOf(frame, label))
        assertEquals(2, back.size)
        val f = back.first { it.groupRole == LayerGroupRole.FRAME }
        val t = back.first { it.groupRole == LayerGroupRole.LABEL }
        assertEquals(f.viewport.offsetX, t.viewport.offsetX)
        assertEquals(f.shapeWidthPx, t.shapeWidthPx)
        assertEquals(f.shapeHeightPx, t.shapeHeightPx)
        assertEquals("Label", t.text)
    }

    @Test
    fun layerGroupSync_appliesTransformToSibling() {
        val gid = "g2"
        val layers = listOf(
            EditorLayer(
                id = "F",
                type = LayerType.SHAPE,
                groupId = gid,
                groupRole = LayerGroupRole.FRAME,
                shapeWidthPx = 100f,
                shapeHeightPx = 50f,
                viewport = EditorViewport(),
            ),
            EditorLayer(
                id = "L",
                type = LayerType.TEXT,
                groupId = gid,
                groupRole = LayerGroupRole.LABEL,
                text = "x",
                shapeWidthPx = 100f,
                shapeHeightPx = 50f,
                viewport = EditorViewport(),
            ),
        )
        val next = LayerGroupSync.apply(layers, "L") { layer ->
            layer.copy(
                viewport = layer.viewport.withOffset(androidx.compose.ui.geometry.Offset(40f, 60f)),
                shapeWidthPx = 180f,
                shapeHeightPx = 90f,
            )
        }
        val f = next.first { it.id == "F" }
        val l = next.first { it.id == "L" }
        assertEquals(40f, f.viewport.offsetX)
        assertEquals(60f, f.viewport.offsetY)
        assertEquals(180f, f.shapeWidthPx)
        assertEquals(90f, f.shapeHeightPx)
        assertEquals(l.viewport.offsetX, f.viewport.offsetX)
    }

    @Test
    fun layerGroupOps_retargetForTool_switchesSiblings() {
        val gid = "g4"
        val layers = listOf(
            EditorLayer(
                id = "F",
                type = LayerType.SHAPE,
                groupId = gid,
                groupRole = LayerGroupRole.FRAME,
                shapeType = ShapeType.CARD,
            ),
            EditorLayer(
                id = "L",
                type = LayerType.TEXT,
                groupId = gid,
                groupRole = LayerGroupRole.LABEL,
                text = "x",
                shapeType = ShapeType.CARD,
            ),
        )
        assertEquals("F", LayerGroupOps.retargetForTool(layers, "L", preferFrame = true))
        assertEquals("L", LayerGroupOps.retargetForTool(layers, "F", preferFrame = false))
        assertEquals(
            "solo",
            LayerGroupOps.retargetForTool(
                listOf(EditorLayer(id = "solo", type = LayerType.SHAPE)),
                "solo",
                preferFrame = true,
            ),
        )
    }

    @Test
    fun layerGroupSync_shapeTypeChangeOnLabel_syncsFrame() {
        val gid = "g3"
        val layers = listOf(
            EditorLayer(
                id = "F",
                type = LayerType.SHAPE,
                groupId = gid,
                groupRole = LayerGroupRole.FRAME,
                shapeType = ShapeType.CARD,
                shapeWidthPx = 100f,
                shapeHeightPx = 50f,
            ),
            EditorLayer(
                id = "L",
                type = LayerType.TEXT,
                groupId = gid,
                groupRole = LayerGroupRole.LABEL,
                text = "x",
                shapeType = ShapeType.CARD,
                shapeWidthPx = 100f,
                shapeHeightPx = 50f,
            ),
        )
        val next = LayerGroupSync.apply(layers, "L") { layer ->
            layer.copy(shapeType = ShapeType.PILL, cornerRadiusX = 12f, cornerRadiusY = 12f)
        }
        val f = next.first { it.id == "F" }
        val l = next.first { it.id == "L" }
        assertEquals(ShapeType.PILL, f.shapeType)
        assertEquals(ShapeType.PILL, l.shapeType)
        assertEquals(12f, f.cornerRadiusX)
        assertEquals(12f, l.cornerRadiusX)
    }

    @Test
    fun reducer_setStyleBag_movesElevationBetweenFrameAndText() {
        val elev = Effect.Elevation3D(intensity = 0.6f, depthSizePx = 12f)
        val node = SceneNode.TextInShape(
            id = "g",
            frameId = "f",
            textId = "t",
            frameGeometry = FrameGeometry(shapeType = ShapeType.CARD),
            frameStyle = StyleBag(effects = listOf(elev)),
            textContent = TextContent(text = "Hi", runs = listOf(TextRun(text = "Hi", textSizeSp = 20f))),
            textStyle = StyleBag(),
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Hug,
                boxWidthPx = 200f,
                boxHeightPx = 80f,
            ),
        )
        var snap = DocumentSnapshot(nodes = listOf(node), templateWidth = 1080f, templateHeight = 1080f)
        snap = DocumentReducer.reduce(
            snap,
            DocumentCommand.SetStyleBag(
                nodeId = "g",
                style = StyleBag(),
                target = com.thgiang.image.studio.ui.editor.document.command.StyleTarget.FRAME,
                intent = LayoutIntent.FontSizeChange,
            ),
        ).snapshot
        snap = DocumentReducer.reduce(
            snap,
            DocumentCommand.SetStyleBag(
                nodeId = "g",
                style = StyleBag(effects = listOf(elev)),
                target = com.thgiang.image.studio.ui.editor.document.command.StyleTarget.TEXT,
                intent = LayoutIntent.FontSizeChange,
            ),
        ).snapshot
        val updated = snap.node("g") as SceneNode.TextInShape
        assertTrue(updated.frameStyle.elevation == null)
        assertNotNull(updated.textStyle.elevation)
        assertEquals(0.6f, updated.textStyle.elevation!!.intensity, 0.001f)
    }

    @Test
    fun reducer_setFrameGeometry_updatesTextInShape() {
        val node = SceneNode.TextInShape(
            id = "g",
            frameId = "f",
            textId = "t",
            frameGeometry = FrameGeometry(shapeType = ShapeType.CARD),
            frameStyle = StyleBag(fills = listOf(Fill.Solid(0xFF2196F3.toInt()))),
            textContent = TextContent(text = "Hi", runs = listOf(TextRun(text = "Hi", textSizeSp = 20f))),
            textStyle = StyleBag(),
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Hug,
                boxWidthPx = 200f,
                boxHeightPx = 80f,
            ),
        )
        val snap = DocumentSnapshot(nodes = listOf(node), templateWidth = 1080f, templateHeight = 1080f)
        val result = DocumentReducer.reduce(
            snap,
            DocumentCommand.SetFrameGeometry(
                nodeId = "g",
                geometry = FrameGeometry(
                    shapeType = ShapeType.PILL,
                    cornerRadiusX = 20f,
                    cornerRadiusY = 20f,
                ),
                frameStyle = StyleBag(
                    fills = listOf(Fill.Solid(0xFFE3F2FD.toInt())),
                    strokes = listOf(
                        StrokeSpec(colorArgb = 0xFF1565C0.toInt(), widthPx = 2f),
                    ),
                ),
            ),
        )
        val updated = result.snapshot.node("g") as SceneNode.TextInShape
        assertEquals(ShapeType.PILL, updated.frameGeometry.shapeType)
        assertEquals(20f, updated.frameGeometry.cornerRadiusX)
        assertEquals(1, updated.frameStyle.strokes.size)
    }

    @Test
    fun textContent_alignRoundTrip() {
        assertEquals(DocTextAlign.LEFT, DocTextAlign.fromLegacy("start"))
        assertEquals("center", DocTextAlign.CENTER.toLegacy())
        assertEquals(DocTextTransform.UPPERCASE, DocTextTransform.fromLegacy("uppercase"))
        assertEquals(null, DocTextTransform.NONE.toLegacy())
    }

    @Test
    fun reducer_setBold_preservesWidth_hugsHeight() {
        val node = SceneNode.PureText(
            id = "t1",
            content = TextContent.fromPlain("Hello", textSizeSp = 24f, fontWeight = "normal"),
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Fixed,
                boxWidthPx = 240f,
                boxHeightPx = 80f,
            ),
            style = StyleBag(),
        )
        val nextContent = node.content.copy(
            runs = listOf(node.content.primaryRun.copy(fontWeight = "bold", text = "Hello")),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.SetTextContent("t1", nextContent, LayoutIntent.StyleOrCaseChange),
        )
        val updated = result.snapshot.node("t1") as SceneNode.PureText
        assertEquals("bold", updated.content.primaryRun.fontWeight)
        assertEquals(240f, updated.layout.boxWidthPx)
        assertEquals(WidthConstraint.Fixed, updated.layout.width)
        assertEquals(HeightConstraint.Hug, updated.layout.height)
        assertEquals(LayoutIntent.StyleOrCaseChange, result.layoutIntent)
    }

    @Test
    fun reducer_setFill_preservesBox_viaFontSizeChangeIntent() {
        val node = SceneNode.PureText(
            id = "t1",
            content = TextContent.fromPlain("Hi"),
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Fixed,
                boxWidthPx = 200f,
                boxHeightPx = 60f,
            ),
            style = StyleBag(),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.SetStyleBag(
                nodeId = "t1",
                style = StyleBag(fills = listOf(Fill.Solid(0xFFE53935.toInt()))),
                intent = LayoutIntent.FontSizeChange,
            ),
        )
        val updated = result.snapshot.node("t1") as SceneNode.PureText
        assertEquals(200f, updated.layout.boxWidthPx)
        assertEquals(60f, updated.layout.boxHeightPx)
        assertEquals(WidthConstraint.Fixed, updated.layout.width)
        assertEquals(HeightConstraint.Fixed, updated.layout.height)
        assertTrue(updated.style.fills.isNotEmpty())
    }

    @Test
    fun reducer_applyTextForm_clearsElevation() {
        val node = SceneNode.PureText(
            id = "t1",
            content = TextContent.fromPlain("Arc"),
            layout = LayoutConstraints(boxWidthPx = 200f, boxHeightPx = 60f),
            style = StyleBag(effects = listOf(Effect.Elevation3D(intensity = 0.7f))),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.ApplyTextFormPreset("t1", TextFormPreset.PATH_ARC_UP),
        )
        val updated = result.snapshot.node("t1") as SceneNode.PureText
        assertTrue(updated.style.textForm.isActive)
        assertFalse(updated.style.effects.any { it is Effect.Elevation3D })
    }

    @Test
    fun reducer_bakeCornerScale_textInShape_scalesFontAndBox() {
        val node = SceneNode.TextInShape(
            id = "g",
            frameId = "f",
            textId = "t",
            frameGeometry = FrameGeometry(ShapeType.CARD),
            frameStyle = StyleBag(),
            textContent = TextContent.fromPlain("Hi", textSizeSp = 20f),
            textStyle = StyleBag(),
            layout = LayoutConstraints(
                boxWidthPx = 100f,
                boxHeightPx = 40f,
                transform = NodeTransform(scale = 2f),
            ),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.BakeCornerScale("g", 2f),
        )
        val updated = result.snapshot.node("g") as SceneNode.TextInShape
        assertEquals(40f, updated.textContent.primaryRun.textSizeSp)
        assertEquals(200f, updated.layout.boxWidthPx)
        assertEquals(80f, updated.layout.boxHeightPx)
        assertEquals(1f, updated.layout.transform.scale)
    }

    @Test
    fun reducer_bakeCornerScale_preservesMultiRunStyles() {
        val node = SceneNode.PureText(
            id = "t1",
            content = TextContent(
                text = "Hi!",
                runs = listOf(
                    TextRun(text = "Hi", textSizeSp = 10f, fontWeight = "bold"),
                    TextRun(text = "!", textSizeSp = 10f, fontWeight = "normal"),
                ),
            ),
            layout = LayoutConstraints(boxWidthPx = 100f, boxHeightPx = 40f),
            style = StyleBag(),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.BakeCornerScale("t1", 2f),
        )
        val updated = result.snapshot.node("t1") as SceneNode.PureText
        assertEquals(2, updated.content.runs.size)
        assertEquals("bold", updated.content.runs[0].fontWeight)
        assertEquals(20f, updated.content.runs[0].textSizeSp)
        assertEquals("!", updated.content.runs[1].text)
        assertEquals(20f, updated.content.runs[1].textSizeSp)
    }

    @Test
    fun reducer_resizeEdge_requestsHugHeight() {
        val node = SceneNode.PureText(
            id = "t1",
            content = TextContent.fromPlain("Wrap me"),
            layout = LayoutConstraints(
                width = WidthConstraint.Fixed,
                height = HeightConstraint.Fixed,
                boxWidthPx = 120f,
                boxHeightPx = 90f,
            ),
            style = StyleBag(),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.ResizeEdge("t1", widthPx = 220f),
        )
        val updated = result.snapshot.node("t1") as SceneNode.PureText
        assertEquals(220f, updated.layout.boxWidthPx)
        assertEquals(WidthConstraint.Fixed, updated.layout.width)
        assertEquals(HeightConstraint.Hug, updated.layout.height)
        assertEquals(LayoutIntent.ResizeEdgeWidth, result.layoutIntent)
    }

    @Test
    fun textInShape_setTransform_updatesSingleLayout() {
        val node = SceneNode.TextInShape(
            id = "g",
            frameId = "f",
            textId = "t",
            frameGeometry = FrameGeometry(ShapeType.CARD),
            frameStyle = StyleBag(fills = listOf(Fill.Solid(0xFF0000FF.toInt()))),
            textContent = TextContent.fromPlain("Hi"),
            textStyle = StyleBag(),
            layout = LayoutConstraints(
                boxWidthPx = 100f,
                boxHeightPx = 40f,
                transform = NodeTransform(),
            ),
        )
        val result = DocumentReducer.reduce(
            DocumentSnapshot(nodes = listOf(node)),
            DocumentCommand.SetTransform(
                nodeId = "g",
                transform = NodeTransform(offsetX = 15f, offsetY = 25f, rotation = 12f),
            ),
        )
        val updated = result.snapshot.node("g") as SceneNode.TextInShape
        assertEquals(15f, updated.layout.transform.offsetX)
        assertEquals(25f, updated.layout.transform.offsetY)
        assertEquals(12f, updated.layout.transform.rotation)
        assertNotNull(updated.frameId)
    }
}
