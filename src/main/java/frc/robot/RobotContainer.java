// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix6.SignalLogger;
import com.ctre.phoenix6.Utils;
import com.pathplanner.lib.auto.NamedCommands;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.GenericHID.RumbleType;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.generated.TunerConstants;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Shooter.ShootingSpeed.Speeds;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

public class RobotContainer {
  // Subsystems
  public final Shooter shooter = new Shooter();
  public final Indexer indexer = new Indexer();
  public final Intake intake = new Intake();
  // why is this instantiated statically???? whose idea was this
  // i feel like this will cause some sort of weird issue in the future
  public final CommandSwerveDrivetrain drivetrain = TunerConstants.DriveTrain;
  // Other stuff
  private static final boolean doSysID = false;
  private final SysIdRoutine routine = null; // drivetrain.sysId.routineToApply;

  public final Photon photon = new Photon();

  // Bindings
  private final CommandXboxController joystick = new CommandXboxController(0);
  private Shooter.ShootingSpeed plannedShootSpeed = Shooter.ShootingSpeed.AMP;
  // Other stuff
  private final Telemetry logger = new Telemetry(drivetrain.MaxSpeed);

  public RobotContainer() {
    configureDriveBindings();
    if (doSysID) {
      configureSysIDBindings(routine);
    } else {
      configureNonDriveBindings();
    }

    AutoCommands.bot = this;
    NamedCommands.registerCommands(
        Map.of(
            "shoot", AutoCommands.shoot(),
            "intake", AutoCommands.intake()));
    drivetrain.configurePathPlanner();

    if (Utils.isSimulation()) {
      drivetrain.seedFieldRelative(new Pose2d(new Translation2d(), Rotation2d.fromDegrees(90)));
    }
    drivetrain.registerTelemetry(logger::telemeterize);
  }

  private void configureDriveBindings() {
    drivetrain.setDefaultCommand( // Drivetrain will execute this command periodically
        drivetrain.applyRequest(
            () -> {
              double joystickY = -joystick.getLeftY();
              double joystickX = joystick.getLeftX();
              double rot = -joystick.getRightX();

              /*DoubleUnaryOperator transformThing = (val) -> // uses a polynomial to scale input
              // this also applies deadbands
              Math.abs(val) < 0.1
                  ? 0
                  : Math.copySign(Math.pow(val, 2), val) * 0.5 + val * 0.5;*/
              // : val;

              // applies deadbands, could potentially be used for polynomial input scaling later
              DoubleUnaryOperator transformThing = (val) -> MathUtil.applyDeadband(val, 0.10);

              double leftJoystickAngle = Math.atan2(joystickY, joystickX);
              double leftJoystickDist = Math.hypot(joystickX, joystickY);

              rot = transformThing.applyAsDouble(rot);
              leftJoystickDist = transformThing.applyAsDouble(leftJoystickDist);

              double forward = Math.sin(leftJoystickAngle) * leftJoystickDist;
              double left = -(Math.cos(leftJoystickAngle) * leftJoystickDist);

              return drivetrain
                  .drive
                  .withVelocityX( // Drive forward with negative Y (forward)
                      forward * CommandSwerveDrivetrain.MaxSpeed)
                  .withVelocityY( // Drive left with negative X (left)
                      left * CommandSwerveDrivetrain.MaxSpeed)
                  .withRotationalRate( // Drive counterclockwise with negative X (left)
                      rot * CommandSwerveDrivetrain.MaxAngularRate);
            }));
    // Back button: Recenter gyro
    joystick
        .back()
        .onTrue(drivetrain.runOnce(drivetrain::resetGyroToForwardFromOperatorPointOfView));
  }

  public Command intakeUntilNote() {
    return intake.intakeCmd().alongWith(indexer.softFeedCmd()).until(indexer.sensors.noteDetected);
  }

  public Command intakeUntilNoteWhileRumble() {
    return intakeUntilNote().alongWith(Commands.run(() ->
        {
          if (indexer.sensors.noteDetected.getAsBoolean()) { // make this better later
            joystick.getHID().setRumble(RumbleType.kLeftRumble, 1);
          }
        })).andThen(Commands.waitSeconds(0.2))
        .finallyDo(() -> joystick.getHID().setRumble(RumbleType.kLeftRumble, 1));
  }


  public Command shootyShoot(Supplier<Speeds> speedy) {
    return shooter
        .speedCmd(speedy)
        .alongWith(
            // we need to wait a bit otherwise atdesiredspeeds will return true
            // we really could fix this by checking the most recently set speeds and we
            // should
            // do this
            Commands.waitSeconds(0.1)
                .andThen(Commands.waitUntil(shooter::atDesiredSpeeds).andThen(indexer.feedCmd())));
  }

  private void configureNonDriveBindings() {
    // Driver bindings:
    // Start button: Eject
    joystick.start().whileTrue(indexer.ejectCmd().alongWith(intake.ejectCmd()));

    // Left bumper: Intake
    joystick.leftBumper().whileTrue(intakeUntilNoteWhileRumble());
    // Right bumper: Shoot
    joystick.rightBumper().whileTrue(shootyShoot(() -> plannedShootSpeed.speeds));

    // A AMP
    joystick
        .a()
        .onTrue(
            Commands.runOnce(
                () -> {
                  plannedShootSpeed = Shooter.ShootingSpeed.AMP;
                }));
    // B: Shooter
    joystick
        .b()
        .onTrue(
            Commands.runOnce(
                () -> {
                  plannedShootSpeed = Shooter.ShootingSpeed.SUBWOOFER;
                }));
  }

  public Command getAutonomousCommand() {
    // return drivetrain.getAutoPath("autopath");
    return Commands.print("No autonomous command configured");
  }

  private void configureSysIDBindings(SysIdRoutine routine) {
    /* Manually start logging with left bumper before running any tests,
     * and stop logging with right bumper after we're done with ALL tests.
     * This isn't necessary but is convenient to reduce the size of the hoot file */
    joystick.leftBumper().onTrue(Commands.runOnce(SignalLogger::start));
    joystick.rightBumper().onTrue(Commands.runOnce(SignalLogger::stop));

    /*
     * Joystick Y = quasistatic forward
     * Joystick A = quasistatic reverse
     * Joystick B = dynamic forward
     * Joystick X = dynamic reverse
     */
    joystick.y().whileTrue(routine.quasistatic(SysIdRoutine.Direction.kForward));
    joystick.a().whileTrue(routine.quasistatic(SysIdRoutine.Direction.kReverse));
    joystick.b().whileTrue(routine.dynamic(SysIdRoutine.Direction.kForward));
    joystick.x().whileTrue(routine.dynamic(SysIdRoutine.Direction.kReverse));
  }

  public void addPhotonPos() {
    var pose = photon.getEstimatedGlobalPose();

    if (pose.isPresent()) {
      var p = pose.get();
      var pose3d = p.estimatedPose;
      var pose2d = pose3d.toPose2d();
      if (Math.abs(pose3d.getZ()) > 0.2) {
        return;
      }
      if (pose3d.getY() < 0 || pose3d.getX() < 0) {
        return;
      }

      if (pose2d.getY() > 13 || pose2d.getX() > 13) {
        return;
      }

      double maxArea = 0;
      for (var target : p.targetsUsed) {
        maxArea = Math.max(target.getArea(), maxArea);
      }
      if (maxArea < 20000 /* pixels?*/) {
        return;
      }
      drivetrain.addVisionMeasurement(pose2d, p.timestampSeconds);
    }
  }
}
