package com.thgiang.image.studio.ui.editor

import com.thgiang.image.core.util.processors.OpaqueContentBounds
import com.thgiang.image.studio.ui.editor.model.CropRatio
import com.thgiang.image.studio.ui.editor.model.EditorLayer
import com.thgiang.image.studio.ui.editor.model.EditorProduct
import com.thgiang.image.studio.ui.editor.model.EditorUserGroup
import com.thgiang.image.studio.ui.editor.model.EditorViewport
import com.thgiang.image.studio.ui.editor.model.LayerContentBounds
import com.thgiang.image.studio.ui.editor.model.LayerListItem
import com.thgiang.image.studio.ui.editor.model.LayerType
import com.thgiang.image.studio.ui.editor.model.OrientedLayerContentBounds
import com.thgiang.image.studio.ui.editor.model.SelectionState
import com.thgiang.image.studio.ui.editor.model.UserGroupBundle
import com.thgiang.image.studio.ui.editor.model.UserGroupMaps
import com.thgiang.image.studio.ui.editor.model.UserGroupOps
import com.thgiang.image.studio.ui.editor.model.UserGroupRole
import com.thgiang.image.studio.ui.editor.model.withOpaqueContentBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGroupOpsTest {

    @Test
    fun `consolidateMemberOrder places members adjacent at topmost index`() {
        val layers = listOf(
            EditorLayer(id = "a"),
            EditorLayer(id = "b"),
            EditorLayer(id = "c"),
            EditorLayer(id = "d"),
            EditorLayer(id = "e"),
        )

        val updated = UserGroupOps.consolidateMemberOrder(layers, setOf("b", "d", "e"))

        assertEquals(listOf("a", "c", "b", "d", "e"), updated.map { it.id })
    }

    @Test
    fun `applyContainerGroup tags members without removing them`() {
        val layers = listOf(
            EditorLayer(id = "a"),
            EditorLayer(id = "b"),
            EditorLayer(id = "c"),
        )
        val prep = UserGroupOps.prepareGroup(layers, setOf("a", "b"))!!

        val (updated, maps) = UserGroupOps.applyContainerGroup(layers, UserGroupMaps(), prep)

        assertEquals(3, updated.size)
        assertEquals(setOf("a", "b"), updated.filter { it.userGroupId == prep.groupId }.map { it.id }.toSet())
        assertNull(updated.find { it.id == "c" }?.userGroupId)
        assertEquals(listOf("a", "b"), maps.groups[prep.groupId]?.memberIds)
    }

    @Test
    fun `buildLayerListItems collapses container groups`() {
        val layers = listOf(
            EditorLayer(id = "a"),
            EditorLayer(id = "b", userGroupId = "g1"),
            EditorLayer(id = "c", userGroupId = "g1"),
            EditorLayer(id = "d"),
        )
        val maps = UserGroupMaps(
            groups = mapOf("g1" to EditorUserGroup("g1", listOf("b", "c"))),
        )

        val items = UserGroupOps.buildLayerListItems(layers, maps)

        assertEquals(3, items.size)
        assertTrue(items[1] is LayerListItem.Group)
        assertEquals(2, (items[1] as LayerListItem.Group).members.size)
        assertNull((items[1] as LayerListItem.Group).composite)
    }

    @Test
    fun `prepareGroup assigns shared userGroupId`() {
        val layers = listOf(EditorLayer(id = "a"), EditorLayer(id = "b"))

        val prep = UserGroupOps.prepareGroup(layers, setOf("a", "b"))!!

        assertEquals(setOf("a", "b"), prep.memberIds)
        assertEquals(listOf("a", "b"), prep.orderedMemberIds)
    }

    @Test
    fun `ungroup clears container membership`() {
        val prep = UserGroupOps.prepareGroup(
            listOf(EditorLayer(id = "a"), EditorLayer(id = "b")),
            setOf("a", "b"),
        )!!
        val (grouped, maps) = UserGroupOps.applyContainerGroup(
            listOf(EditorLayer(id = "a"), EditorLayer(id = "b")),
            UserGroupMaps(),
            prep,
        )

        val (updated, remaining) = UserGroupOps.ungroup(grouped, maps, setOf("a"))

        assertTrue(remaining.groups.isEmpty())
        assertTrue(updated.all { it.userGroupId == null })
    }

    @Test
    fun `ungroup restores legacy composite snapshots`() {
        val composite = EditorLayer(id = "c", userGroupId = "g1", userGroupRole = UserGroupRole.COMPOSITE)
        val layers = listOf(EditorLayer(id = "a"), composite)
        val maps = UserGroupMaps(
            bundles = mapOf(
                "g1" to UserGroupBundle(
                    groupId = "g1",
                    memberSnapshots = listOf(EditorLayer(id = "b"), EditorLayer(id = "d")),
                    compositeLayerId = "c",
                    insertIndex = 1,
                ),
            ),
        )

        val (updated, remaining) = UserGroupOps.ungroup(layers, maps, setOf("c"))

        assertTrue(remaining.bundles.isEmpty())
        assertEquals(listOf("a", "b", "d"), updated.map { it.id })
    }

    @Test
    fun `selectionMembers expands user group`() {
        val layers = listOf(
            EditorLayer(id = "a", userGroupId = "g1"),
            EditorLayer(id = "b", userGroupId = "g1"),
        )

        assertEquals(setOf("a", "b"), UserGroupOps.selectionMembers(layers, "a"))
    }

    @Test
    fun `toggle adds multiple layers when grouped`() {
        val layers = listOf(
            EditorLayer(id = "a"),
            EditorLayer(id = "b", userGroupId = "g1"),
            EditorLayer(id = "c", userGroupId = "g1"),
        )

        val (anchor, ids) = SelectionState.toggle(layers, setOf("a"), "a", "b")

        assertEquals("a", anchor)
        assertEquals(setOf("a", "b", "c"), ids)
    }

    @Test
    fun `canGroup requires at least two independent roots`() {
        val layers = listOf(EditorLayer(id = "a"), EditorLayer(id = "b"))
        assertTrue(UserGroupOps.canGroup(layers, setOf("a", "b")))
        assertFalse(UserGroupOps.canGroup(layers, setOf("a")))
    }

    @Test
    fun `canUngroup detects container groups`() {
        val prep = UserGroupOps.prepareGroup(
            listOf(EditorLayer(id = "a"), EditorLayer(id = "b")),
            setOf("a", "b"),
        )!!
        val (grouped, maps) = UserGroupOps.applyContainerGroup(
            listOf(EditorLayer(id = "a"), EditorLayer(id = "b")),
            UserGroupMaps(),
            prep,
        )

        assertTrue(UserGroupOps.canUngroup(grouped, setOf("a"), maps))
    }

    @Test
    fun `computeMemberContentBounds unions member boxes`() {
        val members = listOf(
            EditorLayer(
                id = "a",
                shapeWidthPx = 100f,
                shapeHeightPx = 80f,
                viewport = EditorViewport(offsetX = 0f, offsetY = 0f, scale = 1f),
            ),
            EditorLayer(
                id = "b",
                shapeWidthPx = 60f,
                shapeHeightPx = 40f,
                viewport = EditorViewport(offsetX = 100f, offsetY = 50f, scale = 1f),
            ),
        )

        val bounds = UserGroupOps.computeMemberContentBounds(members)!!

        assertEquals(40f, bounds.centerX, 0.01f)
        assertEquals(15f, bounds.centerY, 0.01f)
        assertEquals(180f, bounds.width, 0.01f)
        assertEquals(110f, bounds.height, 0.01f)
    }

    @Test
    fun `computeMemberOrientedContentBounds wraps all rotated corners`() {
        val layerA = EditorLayer(
            id = "a",
            type = LayerType.IMAGE,
            product = EditorProduct(
                foregroundUriString = "file:///sticker.png",
                baseWidth = 200,
                baseHeight = 200,
            ),
            shapeWidthPx = 200f,
            shapeHeightPx = 200f,
            cropRatio = CropRatio.ORIGINAL,
            viewport = EditorViewport(offsetX = -40f, offsetY = 20f, scale = 1f, rotation = 45f),
        ).withOpaqueContentBounds(
            OpaqueContentBounds(left = 80, top = 95, width = 40, height = 10),
        )
        val layerB = layerA.copy(
            id = "b",
            viewport = EditorViewport(offsetX = 40f, offsetY = 60f, scale = 1f, rotation = 45f),
        )

        val envelope = UserGroupOps.computeMemberOrientedContentBounds(listOf(layerA, layerB))!!

        assertEquals(0f, envelope.rotationDeg, 0.01f)
        for ((x, y) in com.thgiang.image.studio.ui.editor.model.layerFullRenderCorners(layerA) +
            com.thgiang.image.studio.ui.editor.model.layerFullRenderCorners(layerB)
        ) {
            assertTrue(x >= envelope.bounds.left - 0.5f)
            assertTrue(x <= envelope.bounds.left + envelope.bounds.width + 0.5f)
            assertTrue(y >= envelope.bounds.top - 0.5f)
            assertTrue(y <= envelope.bounds.top + envelope.bounds.height + 0.5f)
        }
    }

    @Test
    fun `duplicateUserGroup clones container members`() {
        val prep = UserGroupOps.prepareGroup(
            listOf(
                EditorLayer(id = "a", viewport = EditorViewport(offsetX = 10f, offsetY = 0f)),
                EditorLayer(id = "b", viewport = EditorViewport(offsetX = 20f, offsetY = 0f)),
            ),
            setOf("a", "b"),
        )!!
        val (grouped, maps) = UserGroupOps.applyContainerGroup(
            listOf(
                EditorLayer(id = "a", viewport = EditorViewport(offsetX = 10f, offsetY = 0f)),
                EditorLayer(id = "b", viewport = EditorViewport(offsetX = 20f, offsetY = 0f)),
            ),
            UserGroupMaps(),
            prep,
        )

        val (dupLayers, dupMaps, primaryId) = UserGroupOps.duplicateUserGroup(grouped, maps, prep.groupId)

        assertEquals(4, dupLayers.size)
        assertEquals(2, dupMaps.groups.size)
        assertTrue(primaryId != null)
        val newGroup = dupMaps.groups.values.first { it.id != prep.groupId }
        assertEquals(2, newGroup.memberIds.size)
    }

    @Test
    fun `buildCompositeLayer legacy helper still builds image layer`() {
        val oriented = OrientedLayerContentBounds(
            bounds = LayerContentBounds(centerX = 120f, centerY = 80f, width = 200f, height = 150f),
            rotationDeg = 0f,
        )

        val composite = UserGroupOps.buildCompositeLayer(
            groupId = "g1",
            compositeLayerId = "composite",
            compositeUri = "file:///tmp/group.png",
            orientedBounds = oriented,
        )

        assertEquals(200f, composite.shapeWidthPx, 0.01f)
        assertEquals(150f, composite.shapeHeightPx, 0.01f)
        assertEquals(UserGroupRole.COMPOSITE, composite.userGroupRole)
    }
}
