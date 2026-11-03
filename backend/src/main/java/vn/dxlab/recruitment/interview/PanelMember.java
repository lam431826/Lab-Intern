package vn.dxlab.recruitment.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.dxlab.recruitment.common.BaseEntity;
import vn.dxlab.recruitment.user.AppUser;

/** Một thành viên hội đồng của ca. Chỉ người có tên ở đây mới chấm được ứng viên của ca. */
@Getter
@Setter
@Entity
@Table(name = "panel_members")
public class PanelMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "panel_role", nullable = false, length = 20)
    private PanelRole panelRole;
}
