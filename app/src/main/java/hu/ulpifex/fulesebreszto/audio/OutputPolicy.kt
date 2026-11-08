package hu.ulpifex.fulesebreszto.audio

/** Platform-independent classification of an audio output (mirrors AudioDeviceInfo types). */
enum class OutputKind(val label: String) {
    WIRED_HEADPHONES("Vezetékes fejhallgató"),
    WIRED_HEADSET("Vezetékes headset"),
    USB_HEADSET("USB-C fejhallgató"),
    USB_DEVICE("USB-audioeszköz"),
    BLUETOOTH_A2DP("Bluetooth-fejhallgató (A2DP)"),
    BLE_HEADSET("Bluetooth LE Audio fejhallgató"),
    HEARING_AID("Hallókészülék"),
    BLE_SPEAKER("Bluetooth LE hangszóró"),
    BUILTIN_SPEAKER("Beépített hangszóró"),
    BUILTIN_EARPIECE("Beszédhangszóró"),
    OTHER("Egyéb kimenet");

    /** Always accepted: these device types are physically worn. */
    val isTrustedByType: Boolean
        get() = this == WIRED_HEADPHONES || this == WIRED_HEADSET || this == USB_HEADSET

    /** Accepted only after the user allowed this specific device (may be a speaker or car kit). */
    val needsApproval: Boolean
        get() = this == BLUETOOTH_A2DP || this == BLE_HEADSET || this == HEARING_AID || this == USB_DEVICE

    val isBluetooth: Boolean
        get() = this == BLUETOOTH_A2DP || this == BLE_HEADSET || this == HEARING_AID || this == BLE_SPEAKER

    /** Lower is preferred when several allowed outputs are connected. */
    val priority: Int
        get() = when (this) {
            WIRED_HEADPHONES, WIRED_HEADSET -> 0
            USB_HEADSET -> 1
            USB_DEVICE -> 2
            BLE_HEADSET -> 3
            BLUETOOTH_A2DP -> 4
            HEARING_AID -> 5
            else -> 99
        }
}

/**
 * A connected audio output. [id] is the runtime AudioDeviceInfo id, [key] a stable identity
 * used for the user's allow list (type family + address suffix + product name).
 */
data class OutputDevice(
    val id: Int,
    val kind: OutputKind,
    val name: String,
    val address: String = "",
) {
    val key: String get() = stableKey(kind, name, address)

    companion object {
        fun stableKey(kind: OutputKind, name: String, address: String): String {
            val family = when {
                kind.isBluetooth -> "bt"
                kind == OutputKind.USB_DEVICE || kind == OutputKind.USB_HEADSET -> "usb"
                else -> kind.name.lowercase()
            }
            // Bluetooth addresses may be partially anonymised ("XX:XX:XX:XX:AB:CD") for apps
            // without BLUETOOTH_CONNECT; the last two octets stay stable, so only they are used.
            val suffix = address.trim().takeLast(5).uppercase()
            return "$family|$suffix|${name.trim()}"
        }
    }
}

sealed interface RouteVerdict {
    data class Verified(val device: OutputDevice) : RouteVerdict
    data class Unverified(val reason: UnverifiedReason) : RouteVerdict
}

enum class UnverifiedReason(val message: String) {
    NO_ALLOWED_DEVICE("Nincs engedélyezett fejhallgató"),
    TARGET_DISCONNECTED("A fejhallgató lecsatlakozott"),
    NO_ROUTE_INFO("A rendszer még nem jelentette a tényleges kimenetet"),
    ROUTED_ELSEWHERE("A hang nem a fejhallgatóra került"),
    UNSAFE_OUTPUT_ACTIVE("A hang a telefon hangszóróján is megszólalhatna"),
    CALL_OR_OTHER_MODE("Hívás vagy kommunikációs mód aktív"),
    FOCUS_LOST("Másik alkalmazás vette át a hangot"),
}

/** Everything the policy needs to judge the current route, captured at one moment. */
data class RouteSnapshot(
    val connected: List<OutputDevice>,
    /** Devices the player reports it is routed to; empty when not yet known. */
    val routed: List<OutputDevice>,
    /** Devices the platform would use for media (API 33+); null when unavailable. */
    val strategy: List<OutputDevice>?,
    val audioModeNormal: Boolean = true,
    val focusLost: Boolean = false,
)

/**
 * Fail-closed policy: sound is allowed only when the current route can be shown to consist
 * exclusively of allowed headphone outputs. Any doubt means "stay muted and vibrate".
 */
class OutputPolicy(private val allowedKeys: Set<String>) {

    fun isAllowed(device: OutputDevice): Boolean =
        device.kind.isTrustedByType || (device.kind.needsApproval && device.key in allowedKeys)

    fun chooseTarget(connected: List<OutputDevice>): OutputDevice? =
        connected.filter(::isAllowed).minWithOrNull(compareBy<OutputDevice> { it.kind.priority }.thenBy { it.id })

    fun verify(target: OutputDevice, snapshot: RouteSnapshot): RouteVerdict {
        if (snapshot.connected.none { it.id == target.id }) {
            return RouteVerdict.Unverified(UnverifiedReason.TARGET_DISCONNECTED)
        }
        if (!snapshot.audioModeNormal) return RouteVerdict.Unverified(UnverifiedReason.CALL_OR_OTHER_MODE)
        if (snapshot.focusLost) return RouteVerdict.Unverified(UnverifiedReason.FOCUS_LOST)
        if (snapshot.routed.isEmpty()) return RouteVerdict.Unverified(UnverifiedReason.NO_ROUTE_INFO)
        if (snapshot.routed.none { it.id == target.id }) {
            return RouteVerdict.Unverified(UnverifiedReason.ROUTED_ELSEWHERE)
        }
        if (snapshot.routed.any { !isAllowed(it) }) {
            return RouteVerdict.Unverified(UnverifiedReason.UNSAFE_OUTPUT_ACTIVE)
        }
        val strategy = snapshot.strategy
        if (strategy != null && strategy.any { it.kind == OutputKind.BUILTIN_SPEAKER || it.kind == OutputKind.BUILTIN_EARPIECE }) {
            // Media as a whole would go to (or be duplicated on) the phone speaker.
            return RouteVerdict.Unverified(UnverifiedReason.UNSAFE_OUTPUT_ACTIVE)
        }
        return RouteVerdict.Verified(target)
    }
}

/** What the player has to do next, derived from a snapshot. */
sealed interface PathDecision {
    /** No allowed headphone connected: release the player, vibrate. */
    data object NoTarget : PathDecision

    /** A (different) allowed device is available: create a muted player bound to it. */
    data class NeedPlayer(val target: OutputDevice) : PathDecision

    /** Route proven: the player may be audible. */
    data class Audible(val target: OutputDevice) : PathDecision

    /** Player exists but the route is not proven: keep it muted. */
    data class StayMuted(val target: OutputDevice, val reason: UnverifiedReason) : PathDecision
}

class AudioPathEvaluator(private val policy: OutputPolicy) {

    fun decide(snapshot: RouteSnapshot, currentTargetId: Int?): PathDecision {
        val target = policy.chooseTarget(snapshot.connected) ?: return PathDecision.NoTarget
        if (currentTargetId != target.id) return PathDecision.NeedPlayer(target)
        return when (val verdict = policy.verify(target, snapshot)) {
            is RouteVerdict.Verified -> PathDecision.Audible(target)
            is RouteVerdict.Unverified -> PathDecision.StayMuted(target, verdict.reason)
        }
    }
}

/** Summary used by the main screen's status line. */
enum class HeadphoneStatusKind { CONNECTED, NONE, NEEDS_CHECK }

data class HeadphoneStatus(
    val kind: HeadphoneStatusKind,
    val deviceName: String? = null,
    val detail: String? = null,
)

object HeadphoneStatusResolver {
    fun resolve(connected: List<OutputDevice>, policy: OutputPolicy, mediaVolumePercent: Int?): HeadphoneStatus {
        val target = policy.chooseTarget(connected)
        if (target != null) {
            if (mediaVolumePercent != null && mediaVolumePercent == 0) {
                return HeadphoneStatus(
                    HeadphoneStatusKind.NEEDS_CHECK, target.name,
                    "A médiahangerő 0 – az ébresztés nem lesz hallható",
                )
            }
            return HeadphoneStatus(HeadphoneStatusKind.CONNECTED, target.name)
        }
        val pending = connected.firstOrNull { it.kind.needsApproval || it.kind == OutputKind.BLE_SPEAKER }
        if (pending != null) {
            return HeadphoneStatus(
                HeadphoneStatusKind.NEEDS_CHECK, pending.name,
                "„${pending.name}” nincs engedélyezve – a Beállításokban jelölheted meg",
            )
        }
        return HeadphoneStatus(HeadphoneStatusKind.NONE)
    }
}
