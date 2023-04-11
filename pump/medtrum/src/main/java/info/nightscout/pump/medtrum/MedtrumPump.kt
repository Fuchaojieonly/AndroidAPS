package info.nightscout.pump.medtrum

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.pump.medtrum.comm.enums.AlarmSetting
import info.nightscout.pump.medtrum.comm.enums.MedtrumPumpState
import info.nightscout.pump.medtrum.extension.toByteArray
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

@Singleton
class MedtrumPump @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val instantiator: Instantiator
) {

    // Pump state flow
    // TODO We might want to save this in SP, or at least get activated state from SP
    private val _pumpState = MutableStateFlow(MedtrumPumpState.NONE)
    val pumpStateFlow: StateFlow<MedtrumPumpState> = _pumpState

    var pumpState: MedtrumPumpState
        get() = _pumpState.value
        set(value) {
            _pumpState.value = value
        }

    // Prime progress as state flow
    private val _primeProgress = MutableStateFlow(0)
    val primeProgressFlow: StateFlow<Int> = _primeProgress

    var primeProgress: Int
        get() = _primeProgress.value
        set(value) {
            _primeProgress.value = value
        }

    // TODO: Save this in SP? This might be a bit tricky as we only know what we have set, not what the pump has set but the pump should not change it, addtionally we should track the active basal profile in pump e.g. Basal patern A, B etc
    var actualBasalProfile = byteArrayOf(0)
    var patchId = 0L
    var lastKnownSequenceNumber = 0
    var lastTimeReceivedFromPump = 0L // Time in seconds!
    var suspendTime = 0L // Time in seconds!
    var patchStartTime = 0L // Time in seconds!
    var patchAge = 0L // Time in seconds!

    var reservoir = 0.0

    var batteryVoltage_A = 0.0
    var batteryVoltage_B = 0.0

    var alarmFlags = 0
    var alarmParameter = 0

    // Last basal status update
    var lastBasalType = 0
    var lastBasalRate = 0.0
    var lastBasalSequence = 0
    var lastBasalPatchId = 0
    var lastBasalStartTime = 0L

    // Last stop status update
    var lastStopSequence = 0
    var lastStopPatchId = 0

    // TODO set these setting on init
    // User settings (desired values, to be set on pump)
    var desiredPatchExpiration = false
    var desiredAlarmSetting = AlarmSetting.LIGHT_VIBRATE_AND_BEEP.code
    var desiredHourlyMaxInsulin: Int = 40
    var desiredDailyMaxInsulin: Int = 180

    fun buildMedtrumProfileArray(nsProfile: Profile): ByteArray? {
        val list = nsProfile.getBasalValues()
        var basals = byteArrayOf()
        for (item in list) {
            val rate = round(item.value / 0.05).toInt()
            val time = item.timeAsSeconds / 60
            if (rate > 0xFFF || time > 0xFFF) {
                aapsLogger.error(LTag.PUMP, "buildMedtrumProfileArray: rate or time too large: $rate, $time")
                return null
            }
            basals += ((rate shl 12) + time).toByteArray(3)
            aapsLogger.debug(LTag.PUMP, "buildMedtrumProfileArray: value: ${item.value} time: ${item.timeAsSeconds}, converted: $rate, $time")
        }
        return (list.size).toByteArray(1) + basals
    }

    fun handleBasalStatusUpdate(basalType: Int, basalValue: Double, basalSequence: Int, basalPatchId: Int, basalStartTime: Long) {
        handleBasalStatusUpdate(basalType, basalValue, basalSequence, basalPatchId, basalStartTime, dateUtil.now())
    }

    fun handleBasalStatusUpdate(basalType: Int, basalRate: Double, basalSequence: Int, basalPatchId: Int, basalStartTime: Long, receivedTime: Long) {
        aapsLogger.debug(
            LTag.PUMP, "handleBasalStatusUpdate: basalType: $basalType basalValue: $basalRate basalSequence: $basalSequence basalPatchId: $basalPatchId basalStartTime: $basalStartTime " +
                "receivedTime: $receivedTime"
        )
        lastBasalType = basalType
        lastBasalRate = basalRate
        lastBasalSequence = basalSequence
        lastKnownSequenceNumber = basalSequence
        lastBasalPatchId = basalPatchId
        lastBasalStartTime = basalStartTime
        // TODO Handle history
    }

    fun handleStopStatusUpdate(stopSequence: Int, stopPatchId: Int) {
        aapsLogger.debug(LTag.PUMP, "handleStopStatusUpdate: stopSequence: $stopSequence stopPatchId: $stopPatchId")
        lastStopSequence = stopSequence
        lastKnownSequenceNumber = stopSequence
        lastStopPatchId = stopPatchId
        // TODO Handle history
    }
}
