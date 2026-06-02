package dev.blocktracker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class VeyraFreecam {
    private static boolean enabled;
    private static boolean cWasDown;
    private static Vec3 position;
    private static Vec3 frozenPlayerPosition;
    private static CameraType previousCameraType;
    private static boolean forward;
    private static boolean backward;
    private static boolean left;
    private static boolean right;
    private static boolean up;
    private static boolean down;
    private static boolean fast;

    private VeyraFreecam() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static Vec3 position() {
        return position;
    }

    public static void tick(Minecraft client, boolean cDown) {
        if (client.player == null || client.getWindow() == null) {
            disable(client);
            cWasDown = false;
            return;
        }

        if (cDown && !cWasDown && client.screen == null) {
            if (enabled) {
                disable(client);
            } else {
                enable(client);
            }
        }
        cWasDown = cDown;

        if (!enabled) {
            return;
        }

        readFreecamKeys(client);
        freezePlayer(client);
        unbindMovementKeys(client);
    }

    public static void frame(Minecraft client, float deltaTicks) {
        if (!enabled || client.player == null) {
            return;
        }

        if (position == null) {
            position = client.player.getEyePosition();
        }

        double speedPerTick = fast ? 0.82D : 0.38D;
        double speed = speedPerTick * Math.max(0.0F, Math.min(deltaTicks, 3.0F));
        double yaw = Math.toRadians(client.player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        double dx = 0.0D;
        double dy = 0.0D;
        double dz = 0.0D;

        if (forward) {
            dx += forwardX;
            dz += forwardZ;
        }
        if (backward) {
            dx -= forwardX;
            dz -= forwardZ;
        }
        if (right) {
            dx += rightX;
            dz += rightZ;
        }
        if (left) {
            dx -= rightX;
            dz -= rightZ;
        }
        if (up) {
            dy += 1.0D;
        }
        if (down) {
            dy -= 1.0D;
        }

        double length = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        if (length > 0.0D) {
            position = position.add((dx / length) * speed, (dy / length) * speed, (dz / length) * speed);
        }
    }

    private static void enable(Minecraft client) {
        enabled = true;
        position = client.player == null ? null : client.player.getEyePosition();
        frozenPlayerPosition = client.player == null ? null : client.player.position();
        previousCameraType = client.options.getCameraType();
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void disable(Minecraft client) {
        if (enabled && previousCameraType != null) {
            client.options.setCameraType(previousCameraType);
        }
        enabled = false;
        position = null;
        frozenPlayerPosition = null;
        previousCameraType = null;
        forward = false;
        backward = false;
        left = false;
        right = false;
        up = false;
        down = false;
        fast = false;
    }

    private static void readFreecamKeys(Minecraft client) {
        var window = client.getWindow();
        forward = InputConstants.isKeyDown(window, InputConstants.KEY_W);
        backward = InputConstants.isKeyDown(window, InputConstants.KEY_S);
        left = InputConstants.isKeyDown(window, InputConstants.KEY_A);
        right = InputConstants.isKeyDown(window, InputConstants.KEY_D);
        up = InputConstants.isKeyDown(window, InputConstants.KEY_SPACE);
        down = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        fast = InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);
    }

    private static void unbindMovementKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keySprint.setDown(false);
    }

    private static void freezePlayer(Minecraft client) {
        if (client.player == null) {
            return;
        }

        client.player.input.keyPresses = Input.EMPTY;
        client.player.setDeltaMovement(Vec3.ZERO);
        if (frozenPlayerPosition != null) {
            client.player.setPos(frozenPlayerPosition);
        }
    }
}
