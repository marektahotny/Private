package sk.planx4.app.ble

import java.util.UUID

/** A single value pushed by the DISTO over BLE. `characteristicUuid` lets the UI (or you,
 *  while hunting for the inclination characteristic) see raw notifications we don't
 *  otherwise interpret yet. */
sealed class DistoMeasurement {
    data class Distance(val meters: Double) : DistoMeasurement()

    /** Placeholder for once the inclination characteristic is known — not wired up yet. */
    data class Inclination(val degrees: Double) : DistoMeasurement()

    /** A notification from a characteristic we don't decode yet — surfaced raw so it can be
     *  inspected (e.g. logged) while reverse-engineering the tilt data with the physical X4. */
    data class RawNotification(val characteristicUuid: UUID, val bytes: ByteArray) : DistoMeasurement() {
        override fun equals(other: Any?): Boolean =
            other is RawNotification && characteristicUuid == other.characteristicUuid && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = 31 * characteristicUuid.hashCode() + bytes.contentHashCode()
    }
}

enum class DistoConnectionState { DISCONNECTED, SCANNING, CONNECTING, DISCOVERING_SERVICES, READY }
