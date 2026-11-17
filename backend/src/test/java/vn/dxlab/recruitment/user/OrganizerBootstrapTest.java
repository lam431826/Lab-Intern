package vn.dxlab.recruitment.user;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import vn.dxlab.recruitment.clerk.ClerkInvitationClient;
import vn.dxlab.recruitment.common.error.BusinessException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizerBootstrapTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final ClerkInvitationClient invitations = mock(ClerkInvitationClient.class);

    private OrganizerBootstrap bootstrap(String email) {
        return new OrganizerBootstrap(users, invitations, "http://localhost:5173", email);
    }

    @Test
    void createsFirstOrganizerOnce() {
        when(users.countByRoleAndActiveTrue(Role.ORGANIZER)).thenReturn(0L);
        when(users.findByEmail("boss@dxlab.vn")).thenReturn(Optional.empty());

        bootstrap("  Boss@DXLab.vn ").run(null);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("boss@dxlab.vn");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ORGANIZER);
        // Clerk Restricted chỉ cho người được mời đăng ký
        verify(invitations).invite("boss@dxlab.vn", "http://localhost:5173");
    }

    @Test
    void inviteFailureDoesNotStopStartup() {
        when(users.countByRoleAndActiveTrue(Role.ORGANIZER)).thenReturn(0L);
        when(users.findByEmail("boss@dxlab.vn")).thenReturn(Optional.empty());
        doThrow(new BusinessException("CLERK_NOT_CONFIGURED", "x")).when(invitations).invite(any(), any());

        bootstrap("boss@dxlab.vn").run(null);

        verify(users).save(any());
    }

    @Test
    void doesNothingWhenOrganizerExistsOrEmailBlank() {
        when(users.countByRoleAndActiveTrue(Role.ORGANIZER)).thenReturn(1L);
        bootstrap("boss@dxlab.vn").run(null);
        bootstrap("").run(null);
        verify(users, never()).save(any());
        verify(invitations, never()).invite(any(), any());
    }

    @Test
    void promotesExistingAccount() {
        AppUser member = new AppUser();
        member.setEmail("boss@dxlab.vn");
        member.setRole(Role.MEMBER);
        member.setActive(false);
        when(users.countByRoleAndActiveTrue(Role.ORGANIZER)).thenReturn(0L);
        when(users.findByEmail("boss@dxlab.vn")).thenReturn(Optional.of(member));

        bootstrap("boss@dxlab.vn").run(null);

        assertThat(member.getRole()).isEqualTo(Role.ORGANIZER);
        assertThat(member.isActive()).isTrue();
    }
}
