package com.company.kanban.service;

import com.company.kanban.dto.LoginRequest; import com.company.kanban.entity.*; import com.company.kanban.repository.UserRepository;
import org.junit.jupiter.api.Test; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.server.ResponseStatusException;
import java.util.Optional; import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;

class AuthServiceStatusTest {
    @Test void disabledUserCannotAuthenticate(){UserRepository users=mock(UserRepository.class);PasswordEncoder encoder=mock(PasswordEncoder.class);JwtService jwt=mock(JwtService.class);User user=new User("Former Staff","off@test","hash",Role.STAFF,new Department("PPC"));user.setStatus(AccountStatus.DISABLED);when(users.findByEmailIgnoreCase("off@test")).thenReturn(Optional.of(user));AuthService service=new AuthService(users,encoder,jwt);assertEquals(401,assertThrows(ResponseStatusException.class,()->service.login(new LoginRequest("off@test","password"))).getStatusCode().value());verify(encoder,never()).matches(anyString(),anyString());}
    @Test void usernameCanAuthenticate(){UserRepository users=mock(UserRepository.class);PasswordEncoder encoder=mock(PasswordEncoder.class);JwtService jwt=mock(JwtService.class);User user=new User("Staff","afiq","hash",Role.STAFF,new Department("PPC"));when(users.findByEmailIgnoreCase("afiq")).thenReturn(Optional.of(user));when(encoder.matches("password1","hash")).thenReturn(true);when(jwt.generateToken(user)).thenReturn("token");AuthService service=new AuthService(users,encoder,jwt);assertEquals("token",service.login(new LoginRequest("afiq","password1")).token());}
}
