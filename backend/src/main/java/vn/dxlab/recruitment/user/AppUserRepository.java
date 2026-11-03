package vn.dxlab.recruitment.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByClerkUserId(String clerkUserId);

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndActiveTrue(Role role);

    List<AppUser> findAllByOrderByFullNameAsc();

    List<AppUser> findByRoleAndActiveTrueOrderByIdAsc(Role role);
}
