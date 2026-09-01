package sk.planx4.app.ble

import java.util.UUID

/**
 * UUIDs for the Leica DISTO Bluetooth LE "basic measurement" service. This is NOT from an
 * official Leica SDK (we don't have one) — it's the service reverse-engineered by the DISTO
 * hobbyist/community projects and cited in the "Plán X4" concept doc, section 03:
 *  - https://github.com/normanargiolas/disto-leica-bluetooth
 *  - https://github.com/seichter/d2relay
 *  - B4X forum: "BLE2 = Leica Disto and Bosch laser rangefinder"
 *
 * It's reported to work across several DISTO generations (D810, X3, ...) that share this
 * service, which is why we're starting with it for the X4 too — but it has NOT been
 * confirmed against a real X4 yet. The inclination/tilt characteristic in particular is
 * unknown; see [DistoBleManager] and the concept doc's nRF Connect walkthrough for how to
 * find it once you have the device in hand.
 */
object DistoUuids {
    val SERVICE_BASIC_MEASUREMENT: UUID = UUID.fromString("3ab10100-f831-4395-b29d-570977d5bf94")

    /** Notifies with a little-endian float32 distance in meters when the laser fires. */
    val CHAR_DISTANCE: UUID = UUID.fromString("3ab1010d-f831-4395-b29d-570977d5bf94")

    /**
     * Seen alongside [CHAR_DISTANCE] in community captures but not yet decoded for the X4.
     * [DistoBleManager] subscribes to every characteristic on the service (not just the known
     * ones) precisely so that, once you find which one changes when you tilt the device, you
     * can wire it up here without re-deriving the rest of the connection handling.
     */
    val CHAR_UNKNOWN_1: UUID = UUID.fromString("3ab10101-f831-4395-b29d-570977d5bf94")
    val CHAR_UNKNOWN_2: UUID = UUID.fromString("3ab10102-f831-4395-b29d-570977d5bf94")

    /** Standard BLE "Client Characteristic Configuration Descriptor" — used to enable notify. */
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
