package gg.vape.module.utility;

import gg.vape.Vape;
import gg.vape.event.EventHandler;
import gg.vape.event.impl.EventPreTick;
import gg.vape.module.UtilityMod;
import gg.vape.module.utility.lunarunlocker.LunarUnlockUtil;
import gg.vape.notification.NotificationType;
import gg.vape.value.BooleanValue;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;

/**
 * LunarUnlocker - Unlocks all Lunar Client cosmetics client-side
 * Ported from LunarUnlocker-1.0.meowtils by meowtils team
 */
public class LunarUnlocker extends UtilityMod {
    private final BooleanValue autoUnlock;
    private boolean hasUnlocked = false;
    private boolean notificationShown = false;

    public LunarUnlocker() {
        super("LunarUnlocker", "Unlocks all Lunar Client cosmetics client-side");

        this.autoUnlock = new BooleanValue(this, "Auto Unlock", "Automatically unlock when joining a world", true);
        this.addValue(this.autoUnlock);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.hasUnlocked = false;
        this.notificationShown = false;

        // Check if Lunar Client is available
        if (!LunarUnlockUtil.isAvailable()) {
            Vape.INSTANCE.getNotificationManager().show(
                "§cLunar Client not detected",
                "LunarUnlocker requires Lunar Client to be running",
                NotificationType.ALERT,
                3000L
            );
            this.setEnabled(false);
            return;
        }

        Vape.INSTANCE.getNotificationManager().show(
            "§aLunarUnlocker enabled",
            "Lunar Client detected. " + (this.autoUnlock.getValue() ? "Will auto-unlock in world." : "Use the unlock button."),
            NotificationType.INFO,
            2000L
        );
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.hasUnlocked = false;
        this.notificationShown = false;
    }

    @EventHandler
    public void onTick(EventPreTick event) {
        if (!this.isEnabled()) {
            return;
        }

        // Auto unlock when in world
        if (this.autoUnlock.getValue() && !this.hasUnlocked) {
            EntityPlayerSP player = event.getThePlayer();
            if (player != null && player.isNotNull()) {
                this.performUnlock();
            }
        }
    }

    /**
     * Manually trigger unlock operation
     */
    public void performUnlock() {
        EntityPlayerSP player = Minecraft.thePlayer();
        if (player == null || !player.isNotNull()) {
            Vape.INSTANCE.getNotificationManager().show(
                "§cCannot unlock",
                "Join a world first",
                NotificationType.WARNING,
                2000L
            );
            return;
        }

        // Check if Lunar Client is still available
        if (!LunarUnlockUtil.isAvailable()) {
            Vape.INSTANCE.getNotificationManager().show(
                "§cLunar Client not detected",
                "Make sure Lunar Client is running",
                NotificationType.ALERT,
                3000L
            );
            return;
        }

        // Perform unlock
        LunarUnlockUtil.UnlockResult result = LunarUnlockUtil.unlockAll();

        if (result.isSuccess()) {
            this.hasUnlocked = true;
            Vape.INSTANCE.getNotificationManager().show(
                "§aUnlock successful",
                result.getMessage(),
                NotificationType.INFO,
                4000L
            );
        } else {
            String message = result.getMessage();
            if (message == null || message.isEmpty()) {
                message = "Unknown error occurred";
            }
            Vape.INSTANCE.getNotificationManager().show(
                "§cUnlock failed",
                message,
                NotificationType.ALERT,
                3000L
            );
        }
    }

    @Override
    public String getSimpleSuffix() {
        if (!LunarUnlockUtil.isAvailable()) {
            return "§cNo Lunar";
        }
        if (this.hasUnlocked) {
            return "§aUnlocked";
        }
        return this.autoUnlock.getValue() ? "§eAuto" : "§7Manual";
    }
}
