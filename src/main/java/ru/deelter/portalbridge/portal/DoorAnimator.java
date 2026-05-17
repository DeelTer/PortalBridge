package ru.deelter.portalbridge.portal;

import org.bukkit.block.BlockFace;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

public final class DoorAnimator {

    private static final float NEAR = 0.09375f;
    private static final float FAR  = 1f - NEAR;

    private DoorAnimator() {}

    public static Transformation buildTransform(BlockFace facing, boolean leftHinge, boolean open) {
        float angle = open ? (leftHinge ? 90f : -90f) : 0f;
        return buildTransform(facing, leftHinge, angle);
    }

    public static Transformation buildTransform(BlockFace facing, boolean leftHinge, float angleDeg) {
        Vector3f hinge = hingePoint(facing, leftHinge);
        Quaternionf rot = new Quaternionf().rotateY((float) Math.toRadians(angleDeg));
        Vector3f rotHinge = rot.transform(new Vector3f(hinge));
        Vector3f translation = new Vector3f(hinge).sub(rotHinge);
        translation.y += 0.001f;
        return new Transformation(translation, rot, new Vector3f(1f), new Quaternionf());
    }

    private static Vector3f hingePoint(@NonNull BlockFace facing, boolean leftHinge) {
        return switch (facing) {
            case NORTH -> leftHinge ? new Vector3f(NEAR, 0f, FAR)  : new Vector3f(FAR,  0f, FAR);
            case SOUTH -> leftHinge ? new Vector3f(FAR,  0f, NEAR) : new Vector3f(NEAR, 0f, NEAR);
            case EAST  -> leftHinge ? new Vector3f(NEAR, 0f, NEAR) : new Vector3f(NEAR, 0f, FAR);
            case WEST  -> leftHinge ? new Vector3f(FAR,  0f, FAR)  : new Vector3f(FAR,  0f, NEAR);
            default    -> new Vector3f(0f, 0f, 0f);
        };
    }
}
