package vn.dxlab.recruitment.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizerBootstrapTest {

    private final AppUserRepository users = mock(AppUserRepository.class);

    @Test
    void createsFirstOrganizerOnce() {
        when(users.countByRoleAndActiveTrue(Role.ORGANIZER)).thenReturn(0L);
        when(users.findByEmail("boss@dxlab.vn")).thenReturn(Optional.empty());

        new OrganizerBootstrap(users, "  Boss@DXLab.vn ").run(null);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("boss@dxlab.vn");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ORGANIZER);
    }

    @Test
    void doesNothingWhenOrganizerExistsOrEmailBlank() {
        when(users.countByRoleAndActiveTrue(Role.ORGANIZER)).thenReturn(1L);
        new OrganizerBootstrap(users, "boss@dxlab.vn").run(null);
        new OrganizerBootstrap(users, "").run(null);
        verify(users, never()).save(any());
    }

    @Test
    void promotesExistingAccount() {
        AppUser member = new AppUser();
        member.setEmail("boss@dxlab.vn");
        member.setRole(Role.MEMBER);
        member.setActive(false);
        when(users.countByRoleAndActiveTrue(Role.ORGANIZER)).thenReturn(0L);
        when(users.findByEmail("boss@dxlab.vn")).thenReturn(Optional.of(member));

        new OrganizerBootstrap(users, "boss@dxlab.vn").run(null);

        assertThat(member.getRole()).isEqualTo(Role.ORGANIZER);
        assertThat(member.isActive()).isTrue();
    }
}
