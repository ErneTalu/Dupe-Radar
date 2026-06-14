package com.duperadar.addon.dupedb;

import com.duperadar.addon.utils.DupeRadarUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;

public class DupeDBAuth {

    private static final String CLIENT_ID = "catr-dupedb-radar";
    private static final String BASE_URL = "https://dupedb.net/api";

    private static String accessToken = null;
    private static boolean authenticating = false;
    private static String cachedUsername = null;

    public static String getCachedUsername() { return cachedUsername; }
    public static boolean isAuthenticated() { return accessToken != null; }
    public static boolean isAuthenticating() { return authenticating; }
    public static String getAccessToken() { return accessToken; }
    private static Path getTokenFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("dupedb-token.json");
    }

    public static void fetchUserInfo() {
        if (accessToken == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                String response = DupeRadarUtils.httpGet(BASE_URL + "/oauth/userinfo", accessToken);
                cachedUsername = DupeRadarUtils.extractStr(response, "username");
            } catch (Exception e) {
                System.out.println("[DupeRadar] Failed to fetch user info: " + e.getMessage());
            }
        });
    }

    public static void loginAsync(Runnable onSuccess, Runnable onFail) {
        if (authenticating) return;
        authenticating = true;

        Path tokenFile = getTokenFile();

        // Loads cached token
        if (Files.exists(tokenFile)) {
            try {
                String saved = Files.readString(tokenFile);
                String token = DupeRadarUtils.extractStr(saved, "access_token");

                if (token != null && !token.isEmpty()) {
                    accessToken = token;
                    authenticating = false;
                    Minecraft.getInstance().execute(onSuccess); // On mt
                    return;
                }
            } catch (Exception ignored) {}
        }

        // PKCE + OAuth : TODO - More functions
        CompletableFuture.runAsync(() -> {
            ServerSocket server = null;
            try {
                byte[] verifierBytes = new byte[32];
                new SecureRandom().nextBytes(verifierBytes);

                String verifier = DupeRadarUtils.base64Url(verifierBytes);
                String challenge = DupeRadarUtils.base64Url(
                    MessageDigest.getInstance("SHA-256").digest(
                        verifier.getBytes(StandardCharsets.US_ASCII)
                    )
                );
                String state = DupeRadarUtils.base64Url(new SecureRandom().generateSeed(16));

                server = new ServerSocket(0);
                int port = server.getLocalPort();
                String redirectUri = "http://127.0.0.1:" + port + "/callback";

                String url = BASE_URL + "/oauth/authorize"
                    + "?response_type=code"
                    + "&client_id=" + CLIENT_ID
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&code_challenge=" + challenge
                    + "&code_challenge_method=S256"
                    + "&state=" + state;

                Util.getPlatform().openUri(url);

                server.setSoTimeout(60000);

                Socket socket = server.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line = reader.readLine();

                String code = null;
                if (line != null && line.startsWith("GET")) {
                    String path = line.split(" ")[1];
                    if (path.contains("?")) {
                        String query = path.substring(path.indexOf('?') + 1);
                        for (String param : query.split("&")) {
                            if (param.startsWith("code=")) {
                                code = URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                            }
                        }
                    }
                }

                PrintWriter out = new PrintWriter(socket.getOutputStream());
                out.println("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n"
                    + "<h1>Authenticated! Return to Minecraft.</h1>");
                out.flush();
                socket.close();

                if (code == null) throw new Exception("No code received");

                String body = "grant_type=authorization_code"
                    + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&client_id=" + CLIENT_ID
                    + "&code_verifier=" + verifier;

                HttpURLConnection conn = (HttpURLConnection) DupeRadarUtils.createURL(BASE_URL + "/oauth/token").openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

                String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                accessToken = DupeRadarUtils.extractStr(response, "access_token");

                if (accessToken == null || accessToken.isEmpty()) {
                    throw new Exception("No access_token in response");
                }

                Files.writeString(tokenFile, response);

                Minecraft.getInstance().execute(() -> {
                    authenticating = false;
                    onSuccess.run();
                });

            } catch (java.net.SocketTimeoutException e) {
                System.out.println("[DupeRadar] Login timed out");
                Minecraft.getInstance().execute(() -> {
                    authenticating = false;
                    onFail.run();
                });
            } catch (Exception e) {
                System.out.println("[DupeRadar] Login failed: " + e.getMessage());
                Minecraft.getInstance().execute(() -> {
                    authenticating = false;
                    onFail.run();
                });
            } finally {
                if (server != null) {
                    try { server.close(); } catch (Exception ignored) {}
                }
            }
        });
    }
    public static void logout() {
        accessToken = null;
        cachedUsername = null;
        authenticating = false;

        Path tokenFile = getTokenFile();
        try { Files.deleteIfExists(tokenFile); } catch (Exception ignored) {}
    }

}
