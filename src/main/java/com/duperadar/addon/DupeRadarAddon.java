package com.duperadar.addon;

import com.duperadar.addon.gui.DupeRadarOverlay;

import autismclient.api.ApiVersion;
import autismclient.api.SimpleAddon;
import autismclient.api.module.SimpleModule;
import autismclient.gui.screen.AutismModuleScreen;
import autismclient.util.AutismOverlayManager;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

public final class DupeRadarAddon extends SimpleAddon {
    public static final String ID = "catr-dupe-radar";

    private SimpleModule module;

    public DupeRadarAddon() {
        super(ApiVersion.CURRENT, "com.duperadar.addon");
    }

    // TODO - Utils both etc...
    private void showOverlay() {
        Minecraft.getInstance().execute(() -> {
            DupeRadarOverlay overlay = DupeRadarOverlay.getInstance();
            AutismOverlayManager mgr = AutismOverlayManager.get();

            mgr.register(overlay);

            if (!overlay.isVisible()) {
                overlay.setVisible(true);
            }
            mgr.bringToFront(overlay);
        });
    }
    private void hideOverlay() {
        Minecraft.getInstance().execute(() -> {
            DupeRadarOverlay.getInstance().setVisible(false);
        });
    }

    @Override
    protected void initialize() {
        module = new SimpleModule("duperadar", "DupeDB", "Check server plugins against DupeDB.") // Just DupeDB rn
            .onEnabled(self -> showOverlay())
            .onDisabled(self -> hideOverlay());

        registerModule(module);

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> { // Couldn't find a public event for it
            DupeRadarOverlay overlay = DupeRadarOverlay.getInstance();
            boolean shouldShow = screen instanceof AutismModuleScreen && module.isEnabled();

            if (shouldShow) {
                showOverlay();
            } else if (overlay.isVisible()) {
                hideOverlay();
            }
        });

        // Clear on both disconnect & join
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            DupeRadarOverlay.getInstance().clearResults());

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            DupeRadarOverlay.getInstance().clearResults());
    }

}
