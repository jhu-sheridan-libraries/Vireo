package org.tdl.vireo.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.tdl.vireo.model.Role;
import org.tdl.vireo.model.User;
import org.tdl.vireo.model.repo.ConfigurationRepo;
import org.tdl.vireo.model.repo.UserRepo;

import edu.tamu.weaver.auth.model.Credentials;

@ExtendWith(MockitoExtension.class)
public class VireoUserCredentialsServiceTest {

    private static final String NETID = "yzhu92";
    private static final String OLD_EMAIL = "yzhu92@jhmi.edu";
    private static final String NEW_EMAIL = "yzhu92@jhu.edu";
    private static final String FIRST_NAME = "Yining";
    private static final String LAST_NAME = "Zhu";

    @Mock
    private ConfigurationRepo configurationRepo;

    @Mock
    private UserRepo userRepo;

    @InjectMocks
    private VireoUserCredentialsService service;

    @BeforeEach
    public void setup() {
        // The userRepo field is inherited (protected) from UserCredentialsService<U, R>,
        // so @InjectMocks doesn't always wire it. Set it explicitly.
        ReflectionTestUtils.setField(service, "userRepo", userRepo);
        ReflectionTestUtils.setField(service, "admins", new String[] {});
        ReflectionTestUtils.setField(service, "useNetidAsIdentifier", false);

        // No DB-configured Shibboleth attribute overrides — use defaults.
        when(configurationRepo.getValueByNameAndType(anyString(), anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("Healthy login: primary lookup matches, fallback never runs")
    public void primaryLookupMatches_fallbackNotInvoked() {
        Credentials creds = credentialsWith(NETID, OLD_EMAIL, FIRST_NAME, LAST_NAME);
        User existing = existingUser(NETID, OLD_EMAIL, FIRST_NAME, LAST_NAME);
        when(userRepo.findByEmail(OLD_EMAIL)).thenReturn(existing);

        User result = service.updateUserByCredentials(creds);

        assertSame(existing, result);
        verify(userRepo).findByEmail(OLD_EMAIL);
        verify(userRepo, never()).findByNetid(anyString());
        verify(userRepo, never()).create(anyString(), anyString(), anyString(), any(Role.class));
    }

    @Test
    @DisplayName("VIR-51 fix: returning student, email changed, fallback by netid matches existing record")
    public void emailMisses_fallbackByNetidFindsUser_emailUpdated() {
        Credentials creds = credentialsWith(NETID, NEW_EMAIL, FIRST_NAME, LAST_NAME);
        User existing = existingUser(NETID, OLD_EMAIL, FIRST_NAME, LAST_NAME);

        when(userRepo.findByEmail(NEW_EMAIL)).thenReturn(null);
        when(userRepo.findByNetid(NETID)).thenReturn(existing);
        when(userRepo.save(existing)).thenReturn(existing);

        User result = service.updateUserByCredentials(creds);

        assertSame(existing, result);
        assertEquals(NEW_EMAIL, existing.getEmail());
        assertEquals(NEW_EMAIL, existing.getUsername());
        verify(userRepo).findByEmail(NEW_EMAIL);
        verify(userRepo).findByNetid(NETID);
        verify(userRepo, never()).create(anyString(), anyString(), anyString(), any(Role.class));
        verify(userRepo).save(existing);
    }

    @Test
    @DisplayName("Symmetric fallback: useNetidAsIdentifier=true, netid changed, fallback by email matches")
    public void netidMisses_fallbackByEmailFindsUser_netidUpdated() {
        ReflectionTestUtils.setField(service, "useNetidAsIdentifier", true);

        String newNetid = "yzhu92-new";
        Credentials creds = credentialsWith(newNetid, OLD_EMAIL, FIRST_NAME, LAST_NAME);
        User existing = existingUser(NETID, OLD_EMAIL, FIRST_NAME, LAST_NAME);

        when(userRepo.findByNetid(newNetid)).thenReturn(null);
        when(userRepo.findByEmail(OLD_EMAIL)).thenReturn(existing);
        when(userRepo.save(existing)).thenReturn(existing);

        User result = service.updateUserByCredentials(creds);

        assertSame(existing, result);
        assertEquals(newNetid, existing.getNetid());
        verify(userRepo).findByNetid(newNetid);
        verify(userRepo).findByEmail(OLD_EMAIL);
        verify(userRepo, never()).create(anyString(), anyString(), anyString(), any(Role.class));
        verify(userRepo).save(existing);
    }

    @Test
    @DisplayName("Truly new user: both lookups miss, create branch runs")
    public void bothLookupsMiss_createBranchRuns() {
        Credentials creds = credentialsWith(NETID, NEW_EMAIL, FIRST_NAME, LAST_NAME);
        User created = new User(NEW_EMAIL, FIRST_NAME, LAST_NAME, Role.ROLE_STUDENT);

        when(userRepo.findByEmail(NEW_EMAIL)).thenReturn(null);
        when(userRepo.findByNetid(NETID)).thenReturn(null);
        when(userRepo.create(NEW_EMAIL, FIRST_NAME, LAST_NAME, Role.ROLE_STUDENT)).thenReturn(created);
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = service.updateUserByCredentials(creds);

        assertNotNull(result);
        assertEquals(NETID, result.getNetid());
        verify(userRepo).findByEmail(NEW_EMAIL);
        verify(userRepo).findByNetid(NETID);
        verify(userRepo).create(NEW_EMAIL, FIRST_NAME, LAST_NAME, Role.ROLE_STUDENT);
        verify(userRepo).save(any(User.class));
    }

    @Test
    @DisplayName("Null-safety: fallback-matched user with null optional fields does not NPE")
    public void fallbackMatch_nullOptionalFieldsOnExistingUser_noNpe() {
        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put("netid", NETID);
        credentialMap.put("email", NEW_EMAIL);
        credentialMap.put("firstName", FIRST_NAME);
        credentialMap.put("lastName", LAST_NAME);
        credentialMap.put("middleName", "Q");
        credentialMap.put("orcid", "0000-0000-0000-0001");
        credentialMap.put("birthYear", "1990");

        Credentials creds = new Credentials();
        creds.setAllCredentials(credentialMap);

        // Existing user has null optional fields — locally-registered account, then matched by fallback.
        User existing = existingUser(NETID, OLD_EMAIL, FIRST_NAME, LAST_NAME);
        // middleName, orcid, birthYear left at null (default).

        when(userRepo.findByEmail(NEW_EMAIL)).thenReturn(null);
        when(userRepo.findByNetid(NETID)).thenReturn(existing);
        when(userRepo.save(existing)).thenReturn(existing);

        assertDoesNotThrow(() -> service.updateUserByCredentials(creds));

        assertEquals("Q", existing.getMiddleName());
        assertEquals("0000-0000-0000-0001", existing.getOrcid());
        assertEquals(Integer.valueOf(1990), existing.getBirthYear());
        verify(userRepo).save(existing);
    }

    @Test
    @DisplayName("Empty fallback identifier short-circuits: no repo call on the missing side")
    public void emptyShibNetid_fallbackShortCircuits() {
        // useNetidAsIdentifier=false (default). shibNetid is empty, so on email-miss the
        // fallback's StringUtils.isNotEmpty(shibNetid) check should prevent findByNetid.
        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put("netid", ""); // empty
        credentialMap.put("email", NEW_EMAIL);
        credentialMap.put("firstName", FIRST_NAME);
        credentialMap.put("lastName", LAST_NAME);

        Credentials creds = new Credentials();
        creds.setAllCredentials(credentialMap);

        when(userRepo.findByEmail(NEW_EMAIL)).thenReturn(null);
        when(userRepo.create(NEW_EMAIL, FIRST_NAME, LAST_NAME, Role.ROLE_STUDENT))
            .thenReturn(new User(NEW_EMAIL, FIRST_NAME, LAST_NAME, Role.ROLE_STUDENT));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateUserByCredentials(creds);

        verify(userRepo).findByEmail(NEW_EMAIL);
        verify(userRepo, never()).findByNetid(anyString());
        verify(userRepo).create(NEW_EMAIL, FIRST_NAME, LAST_NAME, Role.ROLE_STUDENT);
    }

    // --- helpers ---

    private Credentials credentialsWith(String netid, String email, String first, String last) {
        Map<String, String> map = new HashMap<>();
        map.put("netid", netid);
        map.put("email", email);
        map.put("firstName", first);
        map.put("lastName", last);
        Credentials c = new Credentials();
        c.setAllCredentials(map);
        return c;
    }

    private User existingUser(String netid, String email, String first, String last) {
        User u = new User(email, first, last, Role.ROLE_STUDENT);
        u.setNetid(netid);
        u.setUsername(email);
        return u;
    }
}
