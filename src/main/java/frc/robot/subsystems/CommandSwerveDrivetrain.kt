package frc.robot.subsystems

import com.ctre.phoenix6.StatusCode
import com.ctre.phoenix6.Utils
import com.ctre.phoenix6.mechanisms.swerve.*
import com.ctre.phoenix6.mechanisms.swerve.SwerveRequest.*
import com.ctre.phoenix6.mechanisms.swerve.utility.PhoenixPIDController
import com.pathplanner.lib.auto.AutoBuilder
import com.pathplanner.lib.config.ModuleConfig
import com.pathplanner.lib.config.PIDConstants
import com.pathplanner.lib.config.RobotConfig
import com.pathplanner.lib.controllers.PPHolonomicDriveController
import edu.wpi.first.math.geometry.Pose2d
import edu.wpi.first.math.geometry.Rotation2d
import edu.wpi.first.math.geometry.Translation2d
import edu.wpi.first.math.kinematics.ChassisSpeeds
import edu.wpi.first.math.kinematics.SwerveDriveKinematics
import edu.wpi.first.math.system.plant.DCMotor
import edu.wpi.first.wpilibj.DriverStation
import edu.wpi.first.wpilibj.Notifier
import edu.wpi.first.wpilibj.RobotController
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import edu.wpi.first.wpilibj2.command.Command
import edu.wpi.first.wpilibj2.command.Subsystem
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine
import frc.robot.generated.TunerConstants
import frc.robot.sysIdGenerateRoutine
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt


private fun getAngleToPoint(robotPose: Pose2d, targetPoint: Translation2d) =
    (targetPoint - robotPose.translation).let { Rotation2d(it.x, it.y) }

/**
 * Class that extends the Phoenix SwerveDrivetrain class and implements subsystem so it can be used
 * in command-based projects easily.
 */
class CommandSwerveDrivetrain(
    driveTrainConstants: SwerveDrivetrainConstants,
    vararg modules: SwerveModuleConstants
) : SwerveDrivetrain(driveTrainConstants, *modules), Subsystem {
    val teleopDriveRequest: FieldCentricButBetter =
        FieldCentricButBetter() // NOTE: stricter deadbands implemented in controls
            .withDeadband(MAX_SPEED * 0.001)
            .withRotationalDeadband(MAX_ANGLE_RATE * 0.001)
            .withDriveRequestType(SwerveModule.DriveRequestType.OpenLoopVoltage) as FieldCentricButBetter

    /* Blue alliance sees forward as 0 degrees (toward red alliance wall) */
    private val blueAlliancePerspectiveRotation: Rotation2d = Rotation2d.fromDegrees(0.0)

    /* Red alliance sees forward as 180 degrees (toward blue alliance wall) */
    private val redAlliancePerspectiveRotation: Rotation2d = Rotation2d.fromDegrees(180.0)

    /* Keep track if we've ever applied the operator perspective before or not */
    private var hasAppliedOperatorPerspective = false

    fun applyRequest(requestSupplier: Supplier<SwerveRequest?>): Command {
        return run { setControl(requestSupplier.get()) }
    }

    private var lastSimTime = 0.0
    private fun startSimThread() {
        lastSimTime = Utils.getCurrentTimeSeconds()

        /* Run simulation at a faster rate so PID gains behave more reasonably */
        val simNotifier =
            Notifier {
                val currentTime = Utils.getCurrentTimeSeconds()
                val deltaTime = currentTime - lastSimTime
                lastSimTime = currentTime

                /* use the measured time delta, get battery voltage from WPILib */
                updateSimState(deltaTime, RobotController.getBatteryVoltage())
            }
        simNotifier.startPeriodic(SIM_LOOP_PERIOD)
    }

    init {
        if (Utils.isSimulation()) {
            println("Starting swerve drive simulation thread")
            startSimThread()
        }
    }

    override fun periodic() {
        /* Periodically try to apply the operator perspective */
        /* If we haven't applied the operator perspective before, then we should apply it regardless of DS state */
        /* This allows us to correct the perspective in case the robot code restarts mid-match */
        /* Otherwise, only check and apply the operator perspective if the DS is disabled */
        /* This ensures driving behavior doesn't change until an explicit disable event occurs during testing*/
        if (!hasAppliedOperatorPerspective || DriverStation.isDisabled()) {
            DriverStation.getAlliance()
                .ifPresent { allianceColor: DriverStation.Alliance ->
                    setOperatorPerspectiveForward(
                        if (allianceColor == DriverStation.Alliance.Red)
                            redAlliancePerspectiveRotation
                        else blueAlliancePerspectiveRotation
                    )
                    hasAppliedOperatorPerspective = true
                }
        }

        SmartDashboard.putString("DT speaker location", speakerLocation.toString())
        //SmartDashboard.putString("DT speaker rotation", angleToSpeaker.toString())
        SmartDashboard.putNumber("DT dist speaker", distanceToSpeaker)
        SmartDashboard.putString("DT robot translation", pose().translation.toString())
        SmartDashboard.putString("DT robot rotation", pose().rotation.toString())
        SmartDashboard.putBoolean("DT good pointing", goodPointingToSpeaker)
        SmartDashboard.putBoolean("DT not moving", notActivelyMoving)

    }

    /**
     * So basically, the other gyro reset method always assumed the robot is facing towards red
     * alliance wall This method assumes the robot is facing away from the operator regardless of
     * alliance TODO: figure out how this interacts with mid-match rio restarts?
     */
    fun resetGyroToForwardFromOperatorPointOfView() {
        val currentLocation = state.Pose
        val newLocation =
            Pose2d(
                currentLocation.translation,
                if (DriverStation.getAlliance()
                        .orElse(DriverStation.Alliance.Blue) == DriverStation.Alliance.Blue
                )
                    Rotation2d.fromDegrees(0.0)
                else Rotation2d.fromDegrees(180.0)
            )
        seedFieldRelative(newLocation)
    }

    private val autonomousRequest: ApplyChassisSpeeds = ApplyChassisSpeeds()
    private fun runAutoSpeeds(speeds: ChassisSpeeds) =
        setControl(autonomousRequest.withSpeeds(speeds))

    private fun pose() = this.state.Pose

    fun configurePathPlanner() {
        var driveBaseRadius = 0.0
        for (moduleLocation in m_moduleLocations) {
            driveBaseRadius = max(driveBaseRadius, moduleLocation.getNorm())
        }

        AutoBuilder.configure(
            ::pose,  // Supplier of current robot pose
            ::seedFieldRelative,  // Consumer for seeding pose against auto
            ::currentRobotChassisSpeeds,
            ::runAutoSpeeds,  // Consumer of ChassisSpeeds to drive the robot
            PPHolonomicDriveController(
                PIDConstants(5.0, 0.0, 0.0),  // TODO tune
                PIDConstants(5.0, 0.0, 0.0)
            ),
            RobotConfig(
                50.73, 4.0,
                ModuleConfig(
                    TunerConstants.kWheelRadiusInches,
                    MAX_SPEED,
                    1.0,
                    DCMotor.getFalcon500Foc(1),
                    60.0,
                    4
                ), 11.375 * 2 // todo fix
            ),
            {
                DriverStation.getAlliance()
                    .orElse(DriverStation.Alliance.Blue) == DriverStation.Alliance.Red
            },  // Assume the path needs to be flipped for Red vs Blue, this is normally the case
            this
        )
    }

    private val currentRobotChassisSpeeds
        get() = m_kinematics.toChassisSpeeds(*state.ModuleStates)
    var align = false

    // this desaturates wheel speeds, unlike the ctre api
    class FieldCentricButBetter : FieldCentric() {
        val headingController = PhoenixPIDController(4.0, 0.0, 0.0)


        init {
            headingController.enableContinuousInput(-Math.PI, Math.PI)
        }

        override fun apply(
            parameters: SwerveControlRequestParameters,
            vararg modulesToApply: SwerveModule
        ): StatusCode {
            var toApplyX = VelocityX
            var toApplyY = VelocityY
            if (ForwardReference == SwerveRequest.ForwardReference.OperatorPerspective) {
                /* If we're operator perspective, modify the X/Y translation by the angle */
                var tmp = Translation2d(toApplyX, toApplyY)
                tmp = tmp.rotateBy(parameters.operatorForwardDirection)
                toApplyX = tmp.x
                toApplyY = tmp.y
            }
            var toApplyOmega = RotationalRate
            if (sqrt(toApplyX * toApplyX + toApplyY * toApplyY) < Deadband) {
                toApplyX = 0.0
                toApplyY = 0.0
            }
            if (abs(toApplyOmega) < RotationalDeadband) {
                toApplyOmega = 0.0
            }
            // actually
            // TODO this is kinda bad and we should fix this stuff
            if (abs(toApplyOmega) < 0.05 && staticDrivetrainLol != null && staticDrivetrainLol!!.weShouldBePointingAt != null) {
                val weShouldBePointingAt = staticDrivetrainLol!!.weShouldBePointingAt!!
                val angleToPointAt = getAngleToPoint(parameters.currentPose, weShouldBePointingAt)

                val rotationRate = headingController.calculate(
                    parameters.currentPose.rotation.radians,
                    angleToPointAt.radians, parameters.timestamp
                )
                toApplyOmega = rotationRate
            } else if (abs(toApplyOmega) < 6.3 && staticDrivetrainLol != null && staticDrivetrainLol!!.align == true) {
                var myCurrentAngleDegrees = parameters.currentPose.rotation.degrees
                while (myCurrentAngleDegrees > 360) { myCurrentAngleDegrees -= 360.0 }
                while (myCurrentAngleDegrees < 0) { myCurrentAngleDegrees += 360.0 }

                var plus45 = myCurrentAngleDegrees.plus(45.0)
                var div90 = floor(plus45.div(90.0))
                var times90 = div90.times(90)

                val rotationRate = headingController.calculate(
                    parameters.currentPose.rotation.radians,
                    Math.toRadians(times90), parameters.timestamp
                )
                toApplyOmega = rotationRate
            }



            val speeds = ChassisSpeeds.discretize(
                ChassisSpeeds.fromFieldRelativeSpeeds(
                    toApplyX, toApplyY, toApplyOmega, parameters.currentPose.getRotation()
                ), parameters.updatePeriod
            )
            //println("speeds")
            val states = parameters.kinematics.toSwerveModuleStates(speeds, CenterOfRotation)
            // this is the line that ctre forgot (hopefully it's correct):
            SwerveDriveKinematics.desaturateWheelSpeeds(
                states, speeds, MAX_SPEED, MAX_SPEED, MAX_ANGLE_RATE
            )

            modulesToApply.forEachIndexed { index, module ->
                module.apply(states[index], DriveRequestType, SteerRequestType)
                //println("test i " + index + " " + states[index].toString())
            }
            return StatusCode.OK
        }
    }

    /* Use one of these sysidroutines for your particular test */
    private val sysIdTranslation: SysIdRoutine = sysIdGenerateRoutine(
        this@CommandSwerveDrivetrain,
        4.0
    ) { volts -> setControl(SysIdSwerveTranslation().withVolts(volts)) }

    private val sysIdRotation: SysIdRoutine = sysIdGenerateRoutine(
        this@CommandSwerveDrivetrain,
        4.0
    ) { volts -> setControl(SysIdSwerveRotation().withVolts(volts)) }

    private val sysIdSteer: SysIdRoutine = sysIdGenerateRoutine(
        this@CommandSwerveDrivetrain,
        7.0
    ) { volts -> setControl(SysIdSwerveSteerGains().withVolts(volts)) }

    /* Change this to the sysid routine you want to test */
    val routineToApply: SysIdRoutine = sysIdTranslation

    val modulePoses: List<Pose2d>
        /**
         * Get the Pose2d of each swerve module based on kinematics and current robot pose. The returned
         * array order matches the kinematics module order. NOTE: Only used for simulation for the
         * photonvision debug field
         */
        get() {
            return m_moduleLocations.map { Pose2d(it, Rotation2d.fromDegrees(0.0)) }
        }

    companion object {
        // TODO: re-implement brake and pointat requests into controls
        // Stuff for controls:
        const val MAX_SPEED: Double =
            TunerConstants.kSpeedAt12VoltsMps // kSpeedAt12VoltsMps desired top speed
        const val MAX_ANGLE_RATE: Double = 2.5 * Math.PI // teleop
        private const val SIM_LOOP_PERIOD = 0.005 // 5 ms

        // TODO make better
        var staticDrivetrainLol: CommandSwerveDrivetrain? = null
    }

    init {
        staticDrivetrainLol = this;
    }


    val speakerLocation
        get() = DriverStation.getAlliance().orElse(DriverStation.Alliance.Blue)
            .let {
                if (it == DriverStation.Alliance.Blue) Translation2d(
                    0.0,
                    5.548
                ) else Translation2d(16.579, 5.548)
            }

    val distanceToSpeaker
        get() = pose().translation.getDistance(speakerLocation)

    var weShouldBePointingAt: Translation2d? = null

    // TODO make this better lol
    val goodPointingToSpeaker
        get() = true
    //    get() = (angleToSpeaker - pose().rotation).radians.absoluteValue < 0.1

    val notActivelyMoving
        get() = this.state.speeds.let {
            hypot(it.vxMetersPerSecond, it.vyMetersPerSecond) < 0.1
        }


}
