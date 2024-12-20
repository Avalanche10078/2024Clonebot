package frc.robot.generated

import com.ctre.phoenix6.configs.*
import com.ctre.phoenix6.mechanisms.swerve.SwerveDrivetrainConstants
import com.ctre.phoenix6.mechanisms.swerve.SwerveModule.ClosedLoopOutputType
import com.ctre.phoenix6.mechanisms.swerve.SwerveModuleConstants
import com.ctre.phoenix6.mechanisms.swerve.SwerveModuleConstants.SteerFeedbackType
import com.ctre.phoenix6.mechanisms.swerve.SwerveModuleConstantsFactory
import edu.wpi.first.math.util.Units
import frc.robot.enableOutreachModeConst
import frc.robot.subsystems.CommandSwerveDrivetrain

// Generated by the Tuner X Swerve Project Generator
// https://v6.docs.ctr-electronics.com/en/stable/docs/tuner/tuner-swerve/index.html
object TunerConstants {
    // The steer motor uses any SwerveModule.SteerRequestType control request with the
    // output type specified by SwerveModuleConstants.SteerMotorClosedLoopOutput
    private val steerGains: Slot0Configs =
        Slot0Configs().withKP(100.0).withKI(0.0).withKD(0.2).withKS(0.0).withKV(1.5).withKA(0.0)

    // When using closed-loop control, the drive motor uses the control
    // output type specified by SwerveModuleConstants.DriveMotorClosedLoopOutput
    private val driveGains: Slot0Configs = Slot0Configs()
        .withKP(0.33)
        .withKI(0.0)
        .withKD(0.0)
        .withKS(0.24336)
        .withKV(0.11356)
        .withKA(0.010205) // reanalyze later

    // rotation kA:
    // The closed-loop output type to use for the steer motors;
    // This affects the PID/FF gains for the steer motors
    private val steerClosedLoopOutput = ClosedLoopOutputType.Voltage

    // The closed-loop output type to use for the drive motors;
    // This affects the PID/FF gains for the drive motors
    private val driveClosedLoopOutput = ClosedLoopOutputType.Voltage

    // The stator current at which the wheels start to slip;
    // This needs to be tuned to your individual robot
    // this was generated with the amazing "random guess" strategy
    private val kSlipCurrentA = if (enableOutreachModeConst) 20.0 else 60.0

    // Initial configs for the drive and steer motors and the CANcoder; these cannot be null.
    // Some configs will be overwritten; check the `with*InitialConfigs()` API documentation.
    private val driveInitialConfigs = TalonFXConfiguration()
    private val steerInitialConfigs: TalonFXConfiguration = TalonFXConfiguration()
        .withCurrentLimits(
            CurrentLimitsConfigs()
                .withStatorCurrentLimit(if (enableOutreachModeConst) 10.0 else 60.0) // Swerve azimuth does not require much torque output, so we can set a relatively
                // low stator current limit to help avoid brownouts without impacting performance.
                .withStatorCurrentLimitEnable(true)
        )
    private val cancoderInitialConfigs = CANcoderConfiguration()

    // Configs for the Pigeon 2; leave this null to skip applying Pigeon 2 configs
    private val pigeonConfigs: Pigeon2Configuration? = null

    // Every 1 rotation of the azimuth results in kCoupleRatio drive motor turns;
    // This may need to be tuned to your individual robot
    private const val kCoupleRatio = 3.5714285714285716 // 50.0/14.0

    private const val kDriveGearRatio = (50.0 / 14.0) * (16.0 / 28.0) * (45.0 / 15.0)
    private const val kSteerGearRatio = 150.0 / 7.0
    const val kWheelRadiusInches = 1.94 // Best estimate... TODO needs characterization

    private const val kInvertLeftSide = false
    private const val kInvertRightSide = true

    private const val kCANbusName = "canivoreBus"
    private const val kPigeonId = 1

    // These are only used for simulation
    private const val kSteerInertia = 0.00001
    private const val kDriveInertia = 0.001

    // Simulated voltage necessary to overcome friction
    private const val kSteerFrictionVoltage = 0.25
    private const val kDriveFrictionVoltage = 0.25

    // Theoretical free speed (m/s) at 12v applied output
    const val kSpeedAt12VoltsMps: Double = ((6080.0 / 60.0) // falcon 500 foc rotations per second
            * (1 / kDriveGearRatio) // wheel rotations per second
            * (kWheelRadiusInches * 2 * Math.PI)
            * 0.0254) // meters per second

    private val DrivetrainConstants: SwerveDrivetrainConstants = SwerveDrivetrainConstants()
        .withCANbusName(kCANbusName)
        .withPigeon2Id(kPigeonId)
        .withPigeon2Configs(pigeonConfigs)

    private val ConstantCreator: SwerveModuleConstantsFactory = SwerveModuleConstantsFactory()
        .withDriveMotorGearRatio(kDriveGearRatio)
        .withSteerMotorGearRatio(kSteerGearRatio)
        .withWheelRadius(kWheelRadiusInches)
        .withSlipCurrent(kSlipCurrentA)
        .withSteerMotorGains(steerGains)
        .withDriveMotorGains(driveGains)
        .withSteerMotorClosedLoopOutput(steerClosedLoopOutput)
        .withDriveMotorClosedLoopOutput(driveClosedLoopOutput)
        .withSpeedAt12VoltsMps(kSpeedAt12VoltsMps)
        .withSteerInertia(kSteerInertia)
        .withDriveInertia(kDriveInertia)
        .withSteerFrictionVoltage(kSteerFrictionVoltage)
        .withDriveFrictionVoltage(kDriveFrictionVoltage)
        .withFeedbackSource(SteerFeedbackType.FusedCANcoder)
        .withCouplingGearRatio(kCoupleRatio)
        .withDriveMotorInitialConfigs(driveInitialConfigs)
        .withSteerMotorInitialConfigs(steerInitialConfigs)
        .withCANcoderInitialConfigs(cancoderInitialConfigs)

    // Front Left
    private val FrontLeft: SwerveModuleConstants = ConstantCreator.createModuleConstants(
        12,
        9,
        3,
        -0.460205078125,
        Units.inchesToMeters(7.875),
        Units.inchesToMeters(11.375),
        kInvertLeftSide
    ).withSteerMotorInverted(true)

    // Front Right
    private val FrontRight: SwerveModuleConstants = ConstantCreator.createModuleConstants(
        10,
        13,
        5,
        -0.234619140625,
        Units.inchesToMeters(7.875),
        Units.inchesToMeters(-11.375),
        kInvertRightSide
    ).withSteerMotorInverted(true)

    // Back Left
    private val BackLeft: SwerveModuleConstants = ConstantCreator.createModuleConstants(
        6,
        7,
        2,
        -0.15234375,
        Units.inchesToMeters(-7.875),
        Units.inchesToMeters(11.375),
        kInvertLeftSide
    ).withSteerMotorInverted(true)

    // Back Right
    private val BackRight: SwerveModuleConstants = ConstantCreator.createModuleConstants(
        8,
        11,
        4,
        0.149658203125,
        Units.inchesToMeters(-7.875),
        Units.inchesToMeters(-11.375),
        kInvertRightSide
    ).withSteerMotorInverted(true)

    fun createDrivetrain(): CommandSwerveDrivetrain {
        return CommandSwerveDrivetrain(
            DrivetrainConstants,
            FrontLeft,
            FrontRight,
            BackLeft,
            BackRight
        )
    }
}
