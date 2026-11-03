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
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.common.BaseEntity;

import java.time.LocalTime;

/**
 * Slot phỏng vấn của một ứng viên trong một ca. Đổi lịch không sửa đè:
 * slot PV1 chuyển {@code active = false} và trỏ {@code replacedBy} sang slot PV2 mới.
 */
@Getter
@Setter
@Entity
@Table(name = "interview_slots")
public class InterviewSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_type", nullable = false, length = 5)
    private SlotType slotType = SlotType.PV1;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Attendance attendance = Attendance.PENDING;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private InterviewSlot replacedBy;
}
