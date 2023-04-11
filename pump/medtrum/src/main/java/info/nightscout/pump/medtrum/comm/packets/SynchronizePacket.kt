package info.nightscout.pump.medtrum.comm.packets

import dagger.android.HasAndroidInjector
import info.nightscout.pump.medtrum.MedtrumPump
import info.nightscout.pump.medtrum.comm.enums.CommandType.SYNCHRONIZE
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.pump.medtrum.extension.toInt
import info.nightscout.rx.logging.LTag
import javax.inject.Inject

class SynchronizePacket(injector: HasAndroidInjector) : MedtrumPacket(injector) {

    @Inject lateinit var medtrumPump: MedtrumPump

    companion object {

        private const val RESP_STATE_START = 6
        private const val RESP_STATE_END = RESP_STATE_START + 1
        private const val RESP_FIELDS_START = 7
        private const val RESP_FIELDS_END = RESP_FIELDS_START + 2
        private const val RESP_SYNC_DATA_START = 9

        private const val MASK_SUSPEND = 0x01
        private const val MASK_NORMAL_BOLUS = 0x02
        private const val MASK_EXTENDED_BOLUS = 0x04
    }

    init {
        opCode = SYNCHRONIZE.code
        expectedMinRespLength = RESP_SYNC_DATA_START + 1
    }

    override fun handleResponse(data: ByteArray): Boolean {
        val success = super.handleResponse(data)
        if (success) {
            var state = MedtrumPumpState.fromByte(data[RESP_STATE_START])

            medtrumPump.pumpState = state

            var fieldMask = data.copyOfRange(RESP_FIELDS_START, RESP_FIELDS_END).toInt()
            var syncData = data.copyOfRange(RESP_SYNC_DATA_START, data.size)
            var offset = 0

            if (fieldMask != 0) {
                aapsLogger.debug(LTag.PUMPCOMM, "SynchronizePacket: fieldMask: $fieldMask")
            }

            // Remove bolus fields from fieldMask if fields are present (we sync bolus trough other commands)
            if (fieldMask and MASK_SUSPEND != 0) {
                offset += 4 // If field is present, skip 4 bytes
            }
            if (fieldMask and MASK_NORMAL_BOLUS != 0) {
                aapsLogger.warn(LTag.PUMPCOMM, "SynchronizePacket: Normal bolus present removing from fieldMask")
                fieldMask = fieldMask and MASK_NORMAL_BOLUS.inv()
                syncData = syncData.copyOfRange(0, offset) + syncData.copyOfRange(offset + 3, syncData.size)
            }
            if (fieldMask and MASK_EXTENDED_BOLUS != 0) {
                aapsLogger.warn(LTag.PUMPCOMM, "SynchronizePacket: Extended bolus present removing from fieldMask")
                fieldMask = fieldMask and MASK_EXTENDED_BOLUS.inv()
                syncData = syncData.copyOfRange(0, offset) + syncData.copyOfRange(offset + 3, syncData.size)
            }

            // Let the notification packet handle the rest of the sync data
            NotificationPacket(injector).handleMaskedMessage(fieldMask.toByteArray(2) + syncData)
        }

        return success
    }
}
