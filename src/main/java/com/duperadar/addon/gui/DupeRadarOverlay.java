package com.duperadar.addon.gui;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismOverlayBase;
import com.duperadar.addon.utils.DupeRadarUtils;
import com.duperadar.addon.utils.DupeRadarUtils.ExploitEntry;
import com.duperadar.addon.dupedb.DupeDBAuth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DupeRadarOverlay extends AutismOverlayBase {

    private static DupeRadarOverlay instance;

    // GUI
    private static final int PANEL_W = 260;
    private static final int PANEL_H = 240;
    private static final int PANEL_BG = 0xE818181B;
    private static final int BORDER = 0xFF332428;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int SUCCESS = 0xFF35D873;
    private static final int ERROR = 0xFFFF5B5B;
    private static final int WORKING = 0xFF35D873;
    private static final int PATCHED = 0xFFFFA94D;
    private static final int VERIFIED = 0xFF6AA9FF;
    private static final int HOVER_BG = 0x33FFFFFF;
    private static final int CLICK_FLASH = 0x66FFFFFF;
    private static final int DRAG_BAR_H = 24;
    private static final int ROW_H = 14;
    private static final long FLASH_DURATION_MS = 150;

    private enum ScanState { IDLE, SCANNING, ERROR, DONE }
    private enum RowKind { PLUGIN, EXPLOIT }

    // Not working but keepin for later
    private static final Set<String> CLIENT_MOD_BLACKLIST = Set.of(
        "fabric",
        "fabric menu api v1",
        "fabricloader",
        "fabric api",
        "fabric language kotlin",
        "minecraft",
        "java",
        "viaversion",
        "viafabric",
        "viafabricplus",
        "viabackwards",
        "vialegacy",
        "viaproxy"
    );

    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final Map<String, List<ExploitEntry>> matchedPlugins = new LinkedHashMap<>();
    private String expandedPlugin = null;

    private String statusMsg = "";
    private ScanState scanState = ScanState.IDLE;

    private boolean dragging = false;
    private double dragOffX, dragOffY;

    private boolean needsRebuild = true;
    private int scrollOffset = 0;

    // Flash effect
    private RowKind flashKind = null;
    private String flashKey = null;
    private long flashStart = 0;

    public static DupeRadarOverlay getInstance() {
        if (instance == null) instance = new DupeRadarOverlay();
        return instance;
    }

    private DupeRadarOverlay() {
        super("duperadar-overlay", PANEL_W, PANEL_H);
        panelX = 100;
        panelY = 100;
        panelWidth = PANEL_W;
        panelHeight = PANEL_H;
    }

    // I hate these
    @Override
    public void setVisible(boolean v) {
        super.setVisible(v);
        if (v) markDirty();
        if (!v) dragging = false;
    }

    public void markDirty() {
        needsRebuild = true;
    }

    public void clearResults() {
        matchedPlugins.clear();
        expandedPlugin = null;
        statusMsg = "";
        scanState = ScanState.IDLE;
        scrollOffset = 0;
        markDirty();
    }

    private void rebuildIfNeeded() {
        if (!needsRebuild) return;
        needsRebuild = false;
        buttons.clear();

        boolean loggedIn = DupeDBAuth.isAuthenticated();

        buttons.add(CompactOverlayButton.create(panelX + 8, panelY + 8, 50, 16,
                Component.literal("Close"),
                b -> setVisible(false))
            .setVariant(CompactOverlayButton.Variant.SECONDARY));

        if (loggedIn) {
            buttons.add(CompactOverlayButton.create(panelX + panelWidth - 62, panelY + 8, 54, 16,
                    Component.literal("Logout"),
                    b -> { logout(); markDirty(); })
                .setVariant(CompactOverlayButton.Variant.DANGER));

            buttons.add(CompactOverlayButton.create(panelX + 8, panelY + panelHeight - 26, panelWidth - 16, 16,
                    Component.literal("Scan"),
                    b -> scan())
                .setVariant(CompactOverlayButton.Variant.PRIMARY));
        } else {
            buttons.add(CompactOverlayButton.create(panelX + panelWidth - 62, panelY + 8, 54, 16,
                    Component.literal(DupeDBAuth.isAuthenticating() ? "..." : "Login"),
                    b -> { login(); markDirty(); })
                .setVariant(CompactOverlayButton.Variant.SUCCESS));
        }
    }

    private boolean hitPanel(int x, int y) {
        return x >= panelX && x < panelX + panelWidth
            && y >= panelY && y < panelY + panelHeight;
    }

    // Rendering & gui
    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        rebuildIfNeeded();

        int mx = (int) mouseX;
        int my = (int) mouseY;
        Font font = Minecraft.getInstance().font;

        UiRenderer.frame(graphics, UiBounds.of(panelX, panelY, panelWidth, panelHeight), PANEL_BG, BORDER);

        drawText(graphics, font, "DupeDB Radar", panelX + panelWidth / 2, panelY + 12, TEXT, true);

        if (DupeDBAuth.isAuthenticated()) {
            String name = DupeDBAuth.getCachedUsername();
            if (name != null) {
                drawText(graphics, font, "● " + name, panelX + panelWidth - 62, panelY + 28, SUCCESS, false, 54);
            }
        }

        if (DupeDBAuth.isAuthenticated() && !statusMsg.isEmpty()) {
            int color = scanState == ScanState.ERROR ? ERROR
                : scanState == ScanState.DONE ? SUCCESS
                : MUTED;
            drawText(graphics, font, statusMsg, panelX + panelWidth / 2, panelY + 32, color, true);
        }

        drawText(graphics, font, "Matched Plugins", panelX + 8, panelY + 50, TEXT, false);
        UiRenderer.rect(graphics, UiBounds.of(panelX + 8, panelY + 62, panelWidth - 16, 1), BORDER);

        int listTop = panelY + 68;
        int listBottom = panelY + panelHeight - 32;
        int y = listTop - scrollOffset;

        if (matchedPlugins.isEmpty()) {
            String msg;
            if (!DupeDBAuth.isAuthenticated()) msg = "Login first.";
            else if (scanState == ScanState.SCANNING) msg = "";
            else if (scanState == ScanState.DONE) msg = "No matches found.";
            else msg = "Press Scan to start.";
            if (!msg.isEmpty()) drawText(graphics, font, msg, panelX + 8, listTop, MUTED, false);
        } else {
            int rowX = panelX + 4;
            int rowW = panelWidth - 8;

            for (Map.Entry<String, List<ExploitEntry>> entry : matchedPlugins.entrySet()) {
                String pluginName = entry.getKey();
                List<ExploitEntry> exploits = entry.getValue();
                boolean expanded = pluginName.equals(expandedPlugin);

                if (y + ROW_H > listTop && y < listBottom) {
                    int bg = computeRowBg(RowKind.PLUGIN, pluginName, mx, my, rowX, y, rowW);
                    if (bg != 0) {
                        UiRenderer.rect(graphics, UiBounds.of(rowX, y - 1, rowW, ROW_H), bg);
                    }

                    String arrow = expanded ? "▼" : "▶";
                    drawText(graphics, font, arrow + " " + pluginName, panelX + 8, y, SUCCESS, false, panelWidth - 50);
                    String count = "(" + exploits.size() + ")";
                    int cw = font.width(count);
                    drawText(graphics, font, count, panelX + panelWidth - 10 - cw, y, MUTED, false);
                }
                y += ROW_H;

                if (expanded) {
                    for (ExploitEntry ex : exploits) {
                        if (y + ROW_H > listTop && y < listBottom) {
                            int bg = computeRowBg(RowKind.EXPLOIT, ex.id, mx, my, rowX, y, rowW);
                            if (bg != 0) {
                                UiRenderer.rect(graphics, UiBounds.of(rowX, y - 1, rowW, ROW_H), bg);
                            }

                            int statusColor = "working".equalsIgnoreCase(ex.status) ? WORKING
                                : "patched".equalsIgnoreCase(ex.status) ? PATCHED
                                : VERIFIED;
                            String statusLabel = DupeRadarUtils.capitalize(ex.status);
                            int slw = font.width(statusLabel);
                            drawText(graphics, font, "  • " + ex.name, panelX + 12, y, TEXT, false, panelWidth - 30 - slw);
                            drawText(graphics, font, statusLabel, panelX + panelWidth - 10 - slw, y, statusColor, false);
                        }
                        y += ROW_H;
                    }
                }
            }
        }

        for (CompactOverlayButton button : buttons) {
            CompactOverlayButton.renderStyled(graphics, font, button, mx, my);
        }
    }

    private int computeRowBg(RowKind kind, String key, int mx, int my, int rx, int ry, int rw) {
        boolean hover = mx >= rx && mx < rx + rw && my >= ry - 1 && my < ry - 1 + ROW_H;
        boolean flashing = kind == flashKind && key.equals(flashKey)
            && (System.currentTimeMillis() - flashStart) < FLASH_DURATION_MS;
        if (flashing) return CLICK_FLASH;
        if (hover) return HOVER_BG;
        return 0;
    }

    private void triggerFlash(RowKind kind, String key) {
        flashKind = kind;
        flashKey = key;
        flashStart = System.currentTimeMillis();
    }

    // Overlay interface things
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        if (!hitPanel((int) mx, (int) my)) return false;

        int imx = (int) mx;
        int imy = (int) my;

        for (CompactOverlayButton btn : buttons) {
            if (CompactOverlayButton.fireIfHit(btn, imx, imy, button)) return true;
        }

        int listTop = panelY + 68;
        int listBottom = panelY + panelHeight - 32;
        if (button == 0 && imy >= listTop && imy < listBottom && imx >= panelX + 4 && imx < panelX + panelWidth - 4) {
            int y = listTop - scrollOffset;
            for (Map.Entry<String, List<ExploitEntry>> entry : matchedPlugins.entrySet()) {
                String pluginName = entry.getKey();

                if (imy >= y && imy < y + ROW_H) {
                    triggerFlash(RowKind.PLUGIN, pluginName);
                    expandedPlugin = pluginName.equals(expandedPlugin) ? null : pluginName;
                    return true;
                }
                y += ROW_H;

                if (pluginName.equals(expandedPlugin)) {
                    for (ExploitEntry ex : entry.getValue()) {
                        if (imy >= y && imy < y + ROW_H) {
                            triggerFlash(RowKind.EXPLOIT, ex.id);
                            Util.getPlatform().openUri("https://dupedb.net/exploit/" + ex.id);
                            return true;
                        }
                        y += ROW_H;
                    }
                }
            }
        }

        if (button == 0 && imy >= panelY && imy <= panelY + DRAG_BAR_H) {
            dragging = true;
            dragOffX = imx - panelX;
            dragOffY = imy - panelY;
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            panelX = (int) (mx - dragOffX);
            panelY = (int) (my - dragOffY);
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible) return false;
        if (!hitPanel((int) mx, (int) my)) return false;
        scrollOffset = Math.max(0, scrollOffset - (int) (amount * ROW_H * 2));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) { return false; }

    @Override
    public boolean charTyped(char c, int mods) { return false; }

    // DupeRadar
    private void scan() {
        List<String> rawPlugins = DupeRadarUtils.getServerPlugins();
        List<String> serverPlugins = new ArrayList<>();
        for (String p : rawPlugins) {
            if (!CLIENT_MOD_BLACKLIST.contains(p.toLowerCase().trim())) {
                serverPlugins.add(p);
            }
        }

        if (!DupeDBAuth.isAuthenticated()) {
            statusMsg = "Login first.";
            scanState = ScanState.ERROR;
            markDirty();
            return;
        }

        if (serverPlugins.isEmpty()) {
            statusMsg = "Scan server plugins first!";
            scanState = ScanState.ERROR;
            matchedPlugins.clear();
            expandedPlugin = null;
            openPluginScanner();
            markDirty();
            return;
        }

        statusMsg = "Scanning...";
        scanState = ScanState.SCANNING;
        matchedPlugins.clear();
        expandedPlugin = null;
        scrollOffset = 0;
        markDirty();

        final List<String> serverPluginsFinal = serverPlugins;

        CompletableFuture.runAsync(() -> {
            try {
                String token = DupeDBAuth.getAccessToken();
                String resp = DupeRadarUtils.httpGet("https://dupedb.net/api/plugins", token);
                List<String> dbPlugins = DupeRadarUtils.parsePluginNames(resp);
                List<String> matchedNames = DupeRadarUtils.matchPlugins(serverPluginsFinal, dbPlugins);

                if (matchedNames.isEmpty()) {
                    Minecraft.getInstance().execute(() -> {
                        statusMsg = "No matches found.";
                        scanState = ScanState.DONE;
                        markDirty();
                    });
                    return;
                }

                Map<String, List<ExploitEntry>> results =
                    DupeRadarUtils.fetchExploitsForPlugins(matchedNames, token);

                Minecraft.getInstance().execute(() -> {
                    matchedPlugins.clear();
                    matchedPlugins.putAll(results);
                    statusMsg = results.isEmpty()
                        ? "No exploits for matched plugins."
                        : results.size() + " plugin match(es)!";
                    scanState = ScanState.DONE;
                    markDirty();
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    statusMsg = "Error: " + e.getMessage();
                    scanState = ScanState.ERROR;
                    markDirty();
                });
            }
        });
    }
    private void login() {
        if (DupeDBAuth.isAuthenticating()) return;

        statusMsg = "Opening browser...";
        scanState = ScanState.IDLE;
        markDirty();
        DupeDBAuth.loginAsync(
            () -> { statusMsg = "Logged in!"; scanState = ScanState.DONE; DupeDBAuth.fetchUserInfo(); markDirty(); },
            () -> { statusMsg = "Login failed."; scanState = ScanState.ERROR; markDirty(); }
        );
    }
    private void logout() {
        DupeDBAuth.logout();

        statusMsg = "";
        scanState = ScanState.IDLE;
        matchedPlugins.clear();
        expandedPlugin = null;
    }

    private void openPluginScanner() {
        try {
            autismclient.modules.AutismModule global = autismclient.modules.AutismModule.get();
            if (global == null) return;
            autismclient.util.AutismServerInfoOverlay overlay = global.getServerDataOverlay();
            if (overlay == null) return;
            autismclient.util.AutismOverlayManager.get().register(overlay);
            overlay.openPluginsTab();
            autismclient.util.AutismOverlayManager.get().bringToFront(overlay);
            overlay.setVisible(true);
        } catch (Exception ignored) {}
    }

    private void drawText(GuiGraphicsExtractor g, Font f, String text, int x, int y, int color, boolean center) {
        drawText(g, f, text, x, y, color, center, Integer.MAX_VALUE);
    }
    private void drawText(GuiGraphicsExtractor g, Font f, String text, int x, int y, int color, boolean center, int maxW) {
        CompactTheme theme = new CompactTheme();
        if (center) {
            int w = f.width(text);
            UiText.draw(g, f, text, theme.fontFor(UiTone.BODY), color, x - w / 2, y, false);
        } else {
            UiText.drawEllipsized(g, f, text, theme.fontFor(UiTone.BODY), color, x, y, Math.max(1, maxW), false);
        }
    }
}
