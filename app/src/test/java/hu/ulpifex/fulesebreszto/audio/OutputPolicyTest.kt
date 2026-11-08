package hu.ulpifex.fulesebreszto.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputPolicyTest {

    private val speaker = OutputDevice(1, OutputKind.BUILTIN_SPEAKER, "Telefon")
    private val earpiece = OutputDevice(2, OutputKind.BUILTIN_EARPIECE, "Telefon")
    private val wired = OutputDevice(10, OutputKind.WIRED_HEADPHONES, "Fejhallgató")
    private val usbHeadset = OutputDevice(11, OutputKind.USB_HEADSET, "USB-C fülhallgató")
    private val usbDac = OutputDevice(12, OutputKind.USB_DEVICE, "USB DAC")
    private val buds = OutputDevice(20, OutputKind.BLUETOOTH_A2DP, "Galaxy Buds", "XX:XX:XX:XX:12:AB")
    private val btSpeaker = OutputDevice(21, OutputKind.BLUETOOTH_A2DP, "JBL Flip", "XX:XX:XX:XX:77:01")
    private val leSpeaker = OutputDevice(22, OutputKind.BLE_SPEAKER, "LE hangszóró")
    private val leBuds = OutputDevice(23, OutputKind.BLE_HEADSET, "LE fülhallgató", "11:22:33:44:55:66")

    private val noneAllowed = OutputPolicy(emptySet())
    private val budsAllowed = OutputPolicy(setOf(buds.key))

    private fun snapshot(
        connected: List<OutputDevice>,
        routed: List<OutputDevice>,
        strategy: List<OutputDevice>? = null,
        modeNormal: Boolean = true,
        focusLost: Boolean = false,
    ) = RouteSnapshot(connected, routed, strategy, modeNormal, focusLost)

    @Test
    fun `wired and usb-c headphones are trusted by type`() {
        assertTrue(noneAllowed.isAllowed(wired))
        assertTrue(noneAllowed.isAllowed(usbHeadset))
        assertTrue(noneAllowed.isAllowed(OutputDevice(13, OutputKind.WIRED_HEADSET, "Headset")))
    }

    @Test
    fun `bluetooth and usb audio devices need explicit approval`() {
        assertFalse(noneAllowed.isAllowed(buds))
        assertFalse(noneAllowed.isAllowed(usbDac))
        assertFalse(noneAllowed.isAllowed(leBuds))
        assertTrue(budsAllowed.isAllowed(buds))
        assertFalse("approving one BT device must not approve another", budsAllowed.isAllowed(btSpeaker))
    }

    @Test
    fun `speakers are never allowed`() {
        val everything = OutputPolicy(setOf(speaker.key, earpiece.key, leSpeaker.key))
        assertFalse(everything.isAllowed(speaker))
        assertFalse(everything.isAllowed(earpiece))
        assertFalse(everything.isAllowed(leSpeaker))
    }

    @Test
    fun `wired headphones are preferred over bluetooth`() {
        assertEquals(wired, budsAllowed.chooseTarget(listOf(speaker, buds, wired)))
        assertEquals(buds, budsAllowed.chooseTarget(listOf(speaker, buds)))
        assertNull(noneAllowed.chooseTarget(listOf(speaker, buds, btSpeaker)))
    }

    @Test
    fun `anonymised bluetooth address keeps the same identity`() {
        val full = OutputDevice(30, OutputKind.BLUETOOTH_A2DP, "Galaxy Buds", "A1:B2:C3:D4:12:AB")
        assertEquals(buds.key, full.key)
        val le = OutputDevice(31, OutputKind.BLE_HEADSET, "Galaxy Buds", "XX:XX:XX:XX:12:ab")
        assertEquals(buds.key, le.key)
    }

    @Test
    fun `route is verified only when the player is routed exclusively to the target`() {
        val ok = noneAllowed.verify(wired, snapshot(listOf(speaker, wired), routed = listOf(wired), strategy = listOf(wired)))
        assertTrue(ok is RouteVerdict.Verified)
    }

    @Test
    fun `missing route information keeps the alarm silent`() {
        val v = noneAllowed.verify(wired, snapshot(listOf(speaker, wired), routed = emptyList()))
        assertEquals(RouteVerdict.Unverified(UnverifiedReason.NO_ROUTE_INFO), v)
    }

    @Test
    fun `preferred device ignored by the platform is detected`() {
        val v = noneAllowed.verify(wired, snapshot(listOf(speaker, wired), routed = listOf(speaker)))
        assertEquals(RouteVerdict.Unverified(UnverifiedReason.ROUTED_ELSEWHERE), v)
    }

    @Test
    fun `duplicated output including the speaker is rejected`() {
        val dual = noneAllowed.verify(wired, snapshot(listOf(speaker, wired), routed = listOf(wired, speaker)))
        assertEquals(RouteVerdict.Unverified(UnverifiedReason.UNSAFE_OUTPUT_ACTIVE), dual)

        val strategy = budsAllowed.verify(buds, snapshot(listOf(speaker, buds), routed = listOf(buds), strategy = listOf(buds, speaker)))
        assertEquals(RouteVerdict.Unverified(UnverifiedReason.UNSAFE_OUTPUT_ACTIVE), strategy)

        val unapprovedSecond = budsAllowed.verify(buds, snapshot(listOf(buds, btSpeaker), routed = listOf(buds, btSpeaker)))
        assertEquals(RouteVerdict.Unverified(UnverifiedReason.UNSAFE_OUTPUT_ACTIVE), unapprovedSecond)
    }

    @Test
    fun `calls, focus loss and disconnection keep it silent`() {
        assertEquals(
            RouteVerdict.Unverified(UnverifiedReason.CALL_OR_OTHER_MODE),
            noneAllowed.verify(wired, snapshot(listOf(wired), listOf(wired), modeNormal = false)),
        )
        assertEquals(
            RouteVerdict.Unverified(UnverifiedReason.FOCUS_LOST),
            noneAllowed.verify(wired, snapshot(listOf(wired), listOf(wired), focusLost = true)),
        )
        assertEquals(
            RouteVerdict.Unverified(UnverifiedReason.TARGET_DISCONNECTED),
            noneAllowed.verify(wired, snapshot(listOf(speaker), listOf(wired))),
        )
    }

    @Test
    fun `audio path transitions - connect, verify, unplug, reconnect`() {
        val evaluator = AudioPathEvaluator(budsAllowed)

        // 1. Only the phone speaker: nothing may play.
        assertEquals(PathDecision.NoTarget, evaluator.decide(snapshot(listOf(speaker), emptyList()), currentTargetId = null))

        // 2. Wired headphones plugged in: create a muted player for them.
        assertEquals(PathDecision.NeedPlayer(wired), evaluator.decide(snapshot(listOf(speaker, wired), emptyList()), null))

        // 3. Player exists, route not yet reported: stay muted.
        assertEquals(
            PathDecision.StayMuted(wired, UnverifiedReason.NO_ROUTE_INFO),
            evaluator.decide(snapshot(listOf(speaker, wired), emptyList()), wired.id),
        )

        // 4. Route reported on the headphones: audible.
        assertEquals(PathDecision.Audible(wired), evaluator.decide(snapshot(listOf(speaker, wired), listOf(wired)), wired.id))

        // 5. Platform falls back to the speaker before the removal callback: muted.
        assertEquals(
            PathDecision.StayMuted(wired, UnverifiedReason.ROUTED_ELSEWHERE),
            evaluator.decide(snapshot(listOf(speaker, wired), listOf(speaker)), wired.id),
        )

        // 6. Headphones unplugged: no target at all.
        assertEquals(PathDecision.NoTarget, evaluator.decide(snapshot(listOf(speaker), listOf(speaker)), wired.id))

        // 7. Allowed Bluetooth buds connect: new muted player for them.
        assertEquals(PathDecision.NeedPlayer(buds), evaluator.decide(snapshot(listOf(speaker, buds), emptyList()), null))

        // 8. Wired plugged in while BT plays: switch to the preferred wired target.
        assertEquals(PathDecision.NeedPlayer(wired), evaluator.decide(snapshot(listOf(speaker, buds, wired), listOf(buds)), buds.id))
    }

    @Test
    fun `unapproved bluetooth speaker never becomes a target`() {
        val evaluator = AudioPathEvaluator(noneAllowed)
        assertEquals(PathDecision.NoTarget, evaluator.decide(snapshot(listOf(speaker, btSpeaker), listOf(btSpeaker)), null))
    }

    @Test
    fun `main screen status line`() {
        assertEquals(HeadphoneStatusKind.CONNECTED, HeadphoneStatusResolver.resolve(listOf(speaker, wired), noneAllowed, 50).kind)
        assertEquals(HeadphoneStatusKind.NONE, HeadphoneStatusResolver.resolve(listOf(speaker), noneAllowed, 50).kind)
        val pending = HeadphoneStatusResolver.resolve(listOf(speaker, buds), noneAllowed, 50)
        assertEquals(HeadphoneStatusKind.NEEDS_CHECK, pending.kind)
        assertEquals("Galaxy Buds", pending.deviceName)
        assertEquals(HeadphoneStatusKind.NEEDS_CHECK, HeadphoneStatusResolver.resolve(listOf(wired), noneAllowed, 0).kind)
    }
}
