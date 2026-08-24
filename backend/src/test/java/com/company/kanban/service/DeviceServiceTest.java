package com.company.kanban.service;

import com.company.kanban.entity.*;
import com.company.kanban.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceServiceTest {
    private final UserDeviceRepository devices = mock(UserDeviceRepository.class);
    private final DeviceRegistrationCodeRepository codes = mock(DeviceRegistrationCodeRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-24T02:00:00Z"), ZoneOffset.UTC);
    private final DeviceService service = new DeviceService(devices, codes, notifications, new SecureRandom(), clock);
    private final User user = activeUser(7L);

    @Test void registrationCodeCreationStoresOnlyHashAndTenMinuteExpiry() {
        var result = service.createRegistrationCode(user);
        assertTrue(result.code().matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertEquals(LocalDateTime.of(2026, 8, 24, 2, 10), result.expiresAt());
        verify(codes).save(argThat(saved -> !saved.getCodeHash().contains(result.code())
                && saved.getCodeHash().equals(DeviceService.hash(result.code().replace("-", "")))));
    }

    @Test void expiredRegistrationCodeIsRejected() {
        var code = new DeviceRegistrationCode(user, DeviceService.hash("AAAABBBBCCCC"), LocalDateTime.of(2026, 8, 24, 1, 59));
        when(codes.findByCodeHash(DeviceService.hash("AAAABBBBCCCC"))).thenReturn(Optional.of(code));
        assertUnauthorized(() -> service.exchange("AAAA-BBBB-CCCC", "PC"));
        verifyNoInteractions(devices);
    }

    @Test void exchangeIsOneTimeAndReturnsHashedDeviceToken() {
        var code = new DeviceRegistrationCode(user, DeviceService.hash("AAAABBBBCCCC"), LocalDateTime.of(2026, 8, 24, 2, 5));
        when(codes.findByCodeHash(any())).thenReturn(Optional.of(code));
        when(devices.save(any())).thenAnswer(invocation -> {
            UserDevice device = invocation.getArgument(0); if (device.getId() == null) ReflectionTestUtils.setField(device, "id", 55L); return device;
        });
        when(notifications.findMaxIdByRecipientId(7L)).thenReturn(Optional.of(91L));

        var result = service.exchange("AAAA-BBBB-CCCC", "Production PC");
        assertTrue(result.deviceToken().matches("[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{43}"));
        assertEquals(91L, result.notificationCursor());
        verify(devices, atLeastOnce()).save(argThat(device -> device.getDeviceTokenHash().equals(DeviceService.hash(result.deviceToken()))
                && !device.getDeviceTokenHash().contains(result.deviceToken())));
        assertNotNull(code.getUsedAt());
        assertUnauthorized(() -> service.exchange("AAAA-BBBB-CCCC", "Production PC"));
    }

    @Test void invalidCodeIsRejected() {
        when(codes.findByCodeHash(any())).thenReturn(Optional.empty());
        assertUnauthorized(() -> service.exchange("WRONG-CODE", "PC"));
    }

    @Test void activeDeviceAuthenticatesAndUpdatesLastUsed() {
        String token = "abcdefghijklmnop." + "a".repeat(43);
        UserDevice device = device(user, 4L, token);
        when(devices.findByTokenIdentifier("abcdefghijklmnop")).thenReturn(Optional.of(device));
        when(devices.save(device)).thenReturn(device);
        assertSame(device, service.authenticate(token));
        assertEquals(LocalDateTime.of(2026, 8, 24, 2, 0), device.getLastUsedAt());
    }

    @Test void revokedDeviceIsRejected() {
        String token = "abcdefghijklmnop." + "a".repeat(43);
        UserDevice device = device(user, 4L, token); device.revoke(LocalDateTime.now(clock));
        when(devices.findByTokenIdentifier("abcdefghijklmnop")).thenReturn(Optional.of(device));
        assertUnauthorized(() -> service.authenticate(token));
    }

    @Test void disabledAndPendingUsersAreRejected() {
        for (AccountStatus status : List.of(AccountStatus.DISABLED, AccountStatus.PENDING_ACTIVATION)) {
            User inactive = activeUser(8L); inactive.setStatus(status);
            String token = "ponmlkjihgfedcba." + "b".repeat(43); UserDevice device = device(inactive, 8L, token);
            when(devices.findByTokenIdentifier("ponmlkjihgfedcba")).thenReturn(Optional.of(device));
            assertUnauthorized(() -> service.authenticate(token));
        }
    }

    @Test void userListsOnlyOwnDevicesAndCanRevokeOwn() {
        UserDevice own = device(user, 4L, "abcdefghijklmnop." + "a".repeat(43));
        when(devices.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(own));
        when(devices.findByIdAndUserId(4L, 7L)).thenReturn(Optional.of(own));
        assertEquals(List.of(4L), service.list(user).stream().map(item -> item.id()).toList());
        service.revoke(4L, user);
        assertFalse(own.isActive()); verify(devices).save(own);
    }

    @Test void userCannotRevokeAnotherUsersDevice() {
        when(devices.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());
        var ex = assertThrows(ResponseStatusException.class, () -> service.revoke(99L, user));
        assertEquals(404, ex.getStatusCode().value());
    }

    private static User activeUser(long id) {
        Department department = new Department("PPC"); ReflectionTestUtils.setField(department, "id", 1L);
        User result = new User("Bob", "bob@test", "x", Role.STAFF, department); ReflectionTestUtils.setField(result, "id", id); return result;
    }
    private static UserDevice device(User user, long id, String token) {
        UserDevice result = new UserDevice(user, "PC", token.substring(0, token.indexOf('.')), DeviceService.hash(token), LocalDateTime.of(2026, 8, 20, 1, 0));
        ReflectionTestUtils.setField(result, "id", id); return result;
    }
    private static void assertUnauthorized(Runnable action) {
        var ex = assertThrows(ResponseStatusException.class, action::run); assertEquals(401, ex.getStatusCode().value());
    }
}
