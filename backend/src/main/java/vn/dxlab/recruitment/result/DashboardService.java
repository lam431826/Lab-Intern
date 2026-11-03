package vn.dxlab.recruitment.result;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.dxlab.recruitment.candidate.Candidate;
import vn.dxlab.recruitment.candidate.CandidateRepository;
import vn.dxlab.recruitment.candidate.ScreeningRules;
import vn.dxlab.recruitment.candidate.ScreeningStatus;
import vn.dxlab.recruitment.result.dto.DashboardDto;
import vn.dxlab.recruitment.result.dto.ResultRow;
import vn.dxlab.recruitment.result.dto.TrackResultDto;
import vn.dxlab.recruitment.scoring.ScoringService;
import vn.dxlab.recruitment.security.CurrentUser;
import vn.dxlab.recruitment.settings.SettingsService;
import vn.dxlab.recruitment.track.Track;
import vn.dxlab.recruitment.track.TrackRepository;
import vn.dxlab.recruitment.user.Role;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Tổng quan tiến độ tuyển dụng theo ngạch và việc cần làm của người đang đăng nhập. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ResultService results;
    private final ScoringService scoring;
    private final CandidateRepository candidates;
    private final DecisionRepository decisions;
    private final TrackRepository tracks;
    private final SettingsService settings;
    private final CurrentUser currentUser;

    public DashboardService(ResultService results, ScoringService scoring, CandidateRepository candidates,
                            DecisionRepository decisions, TrackRepository tracks, SettingsService settings,
                            CurrentUser currentUser) {
        this.results = results;
        this.scoring = scoring;
        this.candidates = candidates;
        this.decisions = decisions;
        this.tracks = tracks;
        this.settings = settings;
        this.currentUser = currentUser;
    }

    public DashboardDto get() {
        int sla = settings.current().getSlaHours();
        Instant now = Instant.now();
        List<Candidate> all = candidates.findAll();

        List<DashboardDto.TrackStats> stats = results.visibleTracks().stream().map(t -> {
            List<Candidate> mine = all.stream().filter(c -> c.getTrack().getId().equals(t.getId())).toList();
            TrackResultDto table = results.compute(t);
            List<ResultRow> rows = table.rows();
            return new DashboardDto.TrackStats(t.getId(), t.getCode(), t.getName(), t.getQuota(),
                    mine.size(),
                    mine.stream().filter(c -> c.getScreeningStatus() == ScreeningStatus.PENDING).count(),
                    mine.stream().filter(c -> ScreeningRules.overdue(c.getScreeningStatus(), c.getAssignedAt(), sla, now)).count(),
                    mine.stream().filter(c -> c.getScreeningStatus() == ScreeningStatus.PASSED).count(),
                    rows.stream().filter(r -> !r.interview().equals("NOT_SCHEDULED")).count(),
                    rows.stream().filter(r -> r.interview().equals("ATTENDED")).count(),
                    rows.stream().filter(r -> r.interview().equals("ABSENT")).count(),
                    rows.stream().filter(ResultRow::scoresComplete).count(),
                    rows.stream().filter(r -> r.decision() != null && r.decision().finalStatus() != FinalStatus.PENDING).count(),
                    rows.stream().filter(r -> r.decision() != null && r.decision().approved()).count(),
                    table.seatsTaken(),
                    rows.stream().filter(r -> r.decision() != null && r.decision().offerStatus() == OfferStatus.ACCEPTED).count());
        }).toList();

        var me = currentUser.get();
        long toScore = scoring.myQueue().stream().filter(q -> !q.sheetStatus().equals("SUBMITTED")).count();
        Set<Long> screeningTracks = switch (me.role()) {
            case ORGANIZER -> tracks.findAll().stream().map(Track::getId).collect(Collectors.toSet());
            case LEAD -> tracks.findByLeadId(me.id()).stream().map(Track::getId).collect(Collectors.toSet());
            default -> Set.of();
        };
        long screeningToDo = all.stream()
                .filter(c -> screeningTracks.contains(c.getTrack().getId()) && c.getScreeningStatus() == ScreeningStatus.PENDING)
                .count();
        long toApprove = me.role() != Role.LECTURER ? 0 : decisions.findAll().stream()
                .filter(d -> !d.isApproved() && d.getFinalStatus() != FinalStatus.PENDING).count();
        return new DashboardDto(stats, new DashboardDto.MyTasks(toScore, screeningToDo, toApprove));
    }
}
