package vn.dxlab.recruitment.clerk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Webhook Clerk đã xử lý; Clerk (Svix) có thể gửi lại cùng một sự kiện nhiều lần. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "processed_webhooks")
public class ProcessedWebhook {

    @Id
    @Column(name = "svix_id", length = 100)
    private String svixId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedWebhook(String svixId, String eventType) {
        this.svixId = svixId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }
}
