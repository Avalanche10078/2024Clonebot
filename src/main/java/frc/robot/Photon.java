package frc.robot;

import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.util.Units;
import java.util.Optional;
import org.photonvision.EstimatedRobotPose;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonPoseEstimator;
import org.photonvision.PhotonPoseEstimator.PoseStrategy;

public class Photon {
  PhotonPoseEstimator photonPoseEstimator;

  public Photon() {
    var aprilTagFieldLayout = AprilTagFields.k2024Crescendo.loadAprilTagLayoutField();
    var cam = new PhotonCamera("Arducam_OV9281_USB_Camera");

    Transform3d robotToCam =
        new Transform3d(
            new Translation3d(
                Units.inchesToMeters(3.3125),
                0.0,
                Units.inchesToMeters(11.75)), //  approximation, fix later
            new Rotation3d(0, Math.toRadians(-30), 0));
    // Construct PhotonPoseEstimator
    photonPoseEstimator =
        new PhotonPoseEstimator(
            aprilTagFieldLayout, PoseStrategy.MULTI_TAG_PNP_ON_COPROCESSOR, cam, robotToCam);
  }

  public Optional<EstimatedRobotPose> getEstimatedGlobalPose() {
    return photonPoseEstimator.update();
  }
}
