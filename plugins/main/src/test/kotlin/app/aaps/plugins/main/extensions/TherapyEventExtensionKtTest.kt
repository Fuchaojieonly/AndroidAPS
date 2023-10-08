package app.aaps.plugins.main.extensions

import app.aaps.core.data.db.GlucoseUnit
import app.aaps.core.data.db.IDs
import app.aaps.core.data.db.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.main.extensions.isOlderThan
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TherapyEventExtensionKtTest : TestBaseWithProfile() {

    @Test
    fun isOlderThan() {
        val therapyEvent = TE(
            timestamp = now,
            isValid = true,
            type = TE.Type.ANNOUNCEMENT,
            note = "c",
            enteredBy = "dddd",
            glucose = 101.0,
            glucoseType = TE.MeterType.FINGER,
            glucoseUnit = GlucoseUnit.MGDL,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "b"
            )
        )
        Mockito.`when`(dateUtil.now()).thenReturn(now + T.mins(30).msecs())
        assertThat(therapyEvent.isOlderThan(1.0, dateUtil)).isFalse()
        Mockito.`when`(dateUtil.now()).thenReturn(now + T.hours(2).msecs())
        assertThat(therapyEvent.isOlderThan(1.0, dateUtil)).isTrue()
    }
}
