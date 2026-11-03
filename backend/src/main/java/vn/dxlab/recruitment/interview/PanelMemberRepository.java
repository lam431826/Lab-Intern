package vn.dxlab.recruitment.interview;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelMemberRepository extends JpaRepository<PanelMember, Long> {

    boolean existsBySessionIdAndUserId(Long sessionId, Long userId);
}
