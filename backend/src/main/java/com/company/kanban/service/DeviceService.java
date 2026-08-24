package com.company.kanban.service;

import com.company.kanban.dto.DeviceDtos.*;
import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class DeviceService {
    static final int CODE_MINUTES = 10;
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final UserDeviceRepository devices;
    private final DeviceRegistrationCodeRepository codes;
    private final NotificationRepository notifications;
    private final SecureRandom random;
    private final Clock clock;

    @Autowired
    public DeviceService(UserDeviceRepository devices, DeviceRegistrationCodeRepository codes,
                         NotificationRepository notifications) {
        this(devices, codes, notifications, new SecureRandom(), Clock.systemDefaultZone());
    }

    DeviceService(UserDeviceRepository devices, DeviceRegistrationCodeRepository codes,
                  NotificationRepository notifications, SecureRandom random, Clock clock) {
        this.devices = devices; this.codes = codes; this.notifications = notifications;
        this.random = random; this.clock = clock;
    }

    @Transactional
    public RegistrationCodeResponse createRegistrationCode(User user) {
        requireActive(user);
        String code = generateCode();
        LocalDateTime expiresAt = now().plusMinutes(CODE_MINUTES);
        codes.save(new DeviceRegistrationCode(user, hash(normalizeCode(code)), expiresAt));
        return new RegistrationCodeResponse(code, expiresAt);
    }

    @Transactional
    public ExchangeResponse exchange(String suppliedCode, String deviceName) {
        String normalized = normalizeCode(suppliedCode);
        DeviceRegistrationCode code = codes.findByCodeHash(hash(normalized))
                .orElseThrow(() -> unauthorized("Invalid registration code"));
        LocalDateTime now = now();
        if (code.getUsedAt() != null || !code.getExpiresAt().isAfter(now))
            throw unauthorized("Registration code is expired or already used");
        requireActive(code.getUser());

        byte[] identifierBytes = new byte[12];
        byte[] secretBytes = new byte[32];
        random.nextBytes(identifierBytes);
        random.nextBytes(secretBytes);
        String identifier = Base64.getUrlEncoder().withoutPadding().encodeToString(identifierBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String token = identifier + "." + secret;
        UserDevice device = new UserDevice(code.getUser(), cleanName(deviceName), identifier, hash(token), now);
        code.use(now);
        codes.save(code);
        devices.save(device);
        long cursor = notifications.findMaxIdByRecipientId(code.getUser().getId()).orElse(0L);
        return new ExchangeResponse(token, cursor, code.getUser().getId(), code.getUser().getName());
    }

    @Transactional
    public UserDevice authenticate(String token) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{40,}$")) throw unauthorized("Invalid device credential");
        String identifier = token.substring(0, token.indexOf('.'));
        UserDevice device = devices.findByTokenIdentifier(identifier).orElseThrow(() -> unauthorized("Invalid device credential"));
        if (!device.isActive() || !MessageDigest.isEqual(device.getDeviceTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII))) throw unauthorized("Device revoked or invalid");
        requireActive(device.getUser());
        device.touch(now());
        return devices.save(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list(User user) {
        return devices.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::response).toList();
    }

    @Transactional
    public void revoke(Long id, User user) {
        UserDevice device = devices.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        device.revoke(now());
        devices.save(device);
    }

    @Transactional
    public void revokeAuthenticated(UserDevice device) {
        device.revoke(now());
        devices.save(device);
    }

    private DeviceResponse response(UserDevice d) {
        return new DeviceResponse(d.getId(), d.getDeviceName(), d.getCreatedAt(), d.getLastUsedAt(), d.getRevokedAt(), d.isActive());
    }
    private void requireActive(User user) { if (user == null || user.getStatus() != AccountStatus.ACTIVE) throw unauthorized("Account is not active"); }
    private ResponseStatusException unauthorized(String message) { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private String cleanName(String name) { String clean = name == null ? "" : name.trim(); return clean.isEmpty() ? "Windows PC" : clean.substring(0, Math.min(160, clean.length())); }
    private String normalizeCode(String code) { return code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(); }
    private String generateCode() {
        StringBuilder raw = new StringBuilder(12);
        for (int i = 0; i < 12; i++) raw.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8);
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

}
