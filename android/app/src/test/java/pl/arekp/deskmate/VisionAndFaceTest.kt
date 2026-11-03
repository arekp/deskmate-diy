package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.ai.LookTool
import pl.arekp.deskmate.ui.Emotion
import pl.arekp.deskmate.ui.FaceStyle
import pl.arekp.deskmate.vision.FaceObservation
import pl.arekp.deskmate.vision.LabelsPl
import pl.arekp.deskmate.vision.SceneLabel
import pl.arekp.deskmate.vision.SceneObservation

class VisionAndFaceTest {

    @Test
    fun labelsAreTranslatedOrKept() {
        assertEquals("kubek", LabelsPl.translate("Cup"))
        assertEquals("telefon", LabelsPl.translate("Mobile phone"))
        assertEquals("Himalayan", LabelsPl.translate("Himalayan"))
    }

    @Test
    fun lookTool_describesFaceSceneAndText() {
        val d = LookTool.describe(
            FaceObservation(present = true, smiling = true, attentive = true, size = 0.3f),
            SceneObservation(labels = listOf(SceneLabel("kubek", 0.82f)), handRaised = true),
            "DeskMate",
        )
        assertTrue(d, d.contains("Twarz: tak, uśmiechnięta, patrzy w kamerę, blisko."))
        assertTrue(d, d.contains("kubek 82%"))
        assertTrue(d, d.contains("podniesioną rękę"))
        assertTrue(d, d.contains("„DeskMate”"))
        val empty = LookTool.describe(FaceObservation(present = false), SceneObservation(), null)
        assertTrue(empty.startsWith("Twarz: nie widzę."))
        assertFalse(empty.contains("Tekst"))
    }

    @Test
    fun everyEmotionHasSaneStyle() {
        for (e in Emotion.entries) {
            val s = FaceStyle.of(e)
            assertTrue("$e open", s.eye.open in 0.1f..1.5f)
            assertTrue("$e lid", s.eye.lidCover in 0f..0.6f && s.eye.bottomCover in 0f..0.7f)
            assertTrue("$e curve", s.mouth.curve in -1f..1f)
            assertTrue("$e color", s.color ushr 24 == 0xFFL) // nieprzezroczysty
        }
        // emocje rozróżnialne: smutek i złość mają przeciwne nachylenie powiek
        assertTrue(FaceStyle.of(Emotion.SAD).eye.lidAngle > 0 && FaceStyle.of(Emotion.ANGRY).eye.lidAngle < 0)
        assertTrue(FaceStyle.of(Emotion.HAPPY).mouth.curve > 0 && FaceStyle.of(Emotion.SAD).mouth.curve < 0)
    }

    @Test
    fun modelCanPickNewEmotionsButNotListening() {
        val names = Emotion.forModel.map { it.name.lowercase() }
        assertTrue(names.containsAll(listOf("excited", "love", "confused", "angry")))
        assertFalse("listening" in names)
    }
}
