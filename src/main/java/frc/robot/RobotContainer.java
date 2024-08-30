// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.ctre.phoenix6.Utils;
import com.ctre.phoenix6.mechanisms.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.mechanisms.swerve.SwerveRequest;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import frc.robot.generated.TunerConstants;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.Indexer;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Shooter;

public class RobotContainer {
  private double MaxSpeed =
      TunerConstants.kSpeedAt12VoltsMps; // kSpeedAt12VoltsMps desired top speed
  private double MaxAngularRate =
      1.5 * Math.PI; // 3/4 of a rotation per second max angular velocity

  /* Setting up bindings for necessary control of the swerve drive platform */
  private final CommandXboxController joystick = new CommandXboxController(0); // My joystick
  private final CommandSwerveDrivetrain drivetrain = TunerConstants.DriveTrain; // My drivetrain

  private final SwerveRequest.FieldCentric drive =
      new SwerveRequest.FieldCentric()
          .withDeadband(MaxSpeed * 0.03)
          .withRotationalDeadband(MaxAngularRate * 0.05) // Add a 10% deadband
          .withDriveRequestType(DriveRequestType.OpenLoopVoltage); // I want field-centric
  // driving in open loop
  private final SwerveRequest.SwerveDriveBrake brake = new SwerveRequest.SwerveDriveBrake();
  private final SwerveRequest.PointWheelsAt point = new SwerveRequest.PointWheelsAt();

  private final Telemetry logger = new Telemetry(MaxSpeed);

  private final Shooter shooter = new Shooter();
  private final Indexer indexer = new Indexer();
  private final Intake intake = new Intake();

  private Shooter.ShootingSpeed plannedShootSpeed = Shooter.ShootingSpeed.AMP;

  private void configureBindings() {

    drivetrain.setDefaultCommand( // Drivetrain will execute this command periodically
        drivetrain.applyRequest(
            () -> {
              double velX = -joystick.getLeftY() * MaxSpeed;
              double velY = -joystick.getLeftX() * MaxSpeed;
              double rot = -joystick.getRightX() * MaxAngularRate;

              velX = Math.pow(velX, 3) * 0.5 + velX * 0.5;
              velY = Math.pow(velY, 3) * 0.5 + velY * 0.5;
              // velX = Math.pow(velX, 3) * 0.5 + velX * 0.5;

              return drive
                  .withVelocityX(velX) // Drive forward with negative Y (forward)
                  .withVelocityY(velY) // Drive left with negative X (left)
                  .withRotationalRate(rot); // Drive counterclockwise with negative X (left)
            }));

    // Driver bindings:
    // Start button: Eject
    joystick.start().whileTrue(indexer.ejectCmd().alongWith(intake.ejectCmd()));
    // Back button: Recenter gyro
    joystick
        .back()
        .onTrue(drivetrain.runOnce(drivetrain::resetGyroToForwardFromOperatorPointOfView));

    // Left bumper: Intake
    joystick
        .leftBumper()
        .whileTrue(
            intake
                .intakeCmd()
                .alongWith(indexer.softFeedCmd())
                .until(indexer.bothSensorsTriggered));
    // Right bumper: Shoot

    joystick
        .rightBumper()
        .whileTrue(
            shooter
                .speedCmd(plannedShootSpeed.speeds)
                .alongWith(
                    // we need to wait a bit otherwise atdesiredspeeds will return true
                    // we really could fix this by checking the most recently set speeds and we
                    // should
                    // do this
                    Commands.waitSeconds(0.1)
                        .andThen(
                            Commands.waitUntil(shooter::atDesiredSpeeds)
                                .andThen(indexer.feedCmd()))));

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

  public RobotContainer() {
    configureBindings();

    configureSysIDBindings();

    if (Utils.isSimulation()) {
      drivetrain.seedFieldRelative(new Pose2d(new Translation2d(), Rotation2d.fromDegrees(90)));
    }
    drivetrain.registerTelemetry(logger::telemeterize);
  }

  public Command getAutonomousCommand() {
    return Commands.print("No autonomous command configured");
  }

  private void configureSysIDBindings() {
    var m_mechanism = shooter;
    /* Manually start logging with left bumper before running any tests,
     * and stop logging with right bumper after we're done with ALL tests.
     * This isn't necessary but is convenient to reduce the size of the hoot file */
    // joystick.leftBumper().onTrue(Commands.runOnce(SignalLogger::start));
    // joystick.rightBumper().onTrue(Commands.runOnce(SignalLogger::stop));

    /*
     * Joystick Y = quasistatic forward
     * Joystick A = quasistatic reverse
     * Joystick B = dynamic forward
     * Joystick X = dynamic reverse
     */
    /*joystick.y().whileTrue(m_mechanism.sysIdQuasistatic(SysIdRoutine.Direction.kForward));
    joystick.a().whileTrue(m_mechanism.sysIdQuasistatic(SysIdRoutine.Direction.kReverse));
    joystick.b().whileTrue(m_mechanism.sysIdDynamic(SysIdRoutine.Direction.kForward));
    joystick.x().whileTrue(m_mechanism.sysIdDynamic(SysIdRoutine.Direction.kReverse));*/

  }
}
