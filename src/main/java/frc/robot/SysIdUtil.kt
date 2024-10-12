package frc.robot

import com.ctre.phoenix6.SignalLogger
import com.ctre.phoenix6.controls.VoltageOut
import com.ctre.phoenix6.hardware.TalonFX
import edu.wpi.first.units.Measure
import edu.wpi.first.units.Units
import edu.wpi.first.units.Voltage
import edu.wpi.first.wpilibj.sysid.SysIdRoutineLog
import edu.wpi.first.wpilibj2.command.Subsystem
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Mechanism
import java.util.function.Consumer

/** Helper methods for generating sysid routines to prevent repetitive code  */
fun sysIdGenerateRoutine(
    subsystem: Subsystem,
    volts: Double,
    drive: Consumer<Measure<Voltage>>
): SysIdRoutine {
    return SysIdRoutine(
        SysIdRoutine.Config(
            null, Units.Volts.of(volts), null
        ) { state: SysIdRoutineLog.State ->
            SignalLogger.writeString("state", state.toString())
        },
        Mechanism(drive, null, subsystem)
    )
}


fun sysIdSingleMotor(subsystem: Subsystem, motor: TalonFX): SysIdRoutine {
    return sysIdGenerateRoutine(subsystem, 6.0) { volts: Measure<Voltage> ->
        motor.setControl(
            VoltageOut(0.0).apply {
                Output = volts.`in`(Units.Volts)
                EnableFOC = true
                UpdateFreqHz = 0.0
            }
        )
    }
}