package vn.dxlab.recruitment.clerk;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.dxlab.recruitment.common.error.ApiError;

/**
 * Nhận webhook từ Clerk. Không cần đăng nhập, nhưng mọi gói tin phải có chữ ký Svix hợp lệ.
 * Đọc body dạng chuỗi thô vì chữ ký được tính trên đúng từng byte Clerk gửi.
 */
@RestController
@RequestMapping("/api/webhooks/clerk")
public class ClerkWebhookController {

    private final SvixSignatureVerifier verifier;
    private final ClerkWebhookService service;
    private final JsonMapper json;

    public ClerkWebhookController(@Lazy SvixSignatureVerifier verifier, ClerkWebhookService service, JsonMapper json) {
        this.verifier = verifier;
        this.service = service;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<?> receive(@RequestHeader(name = "svix-id", required = false) String svixId,
                                     @RequestHeader(name = "svix-timestamp", required = false) String svixTimestamp,
                                     @RequestHeader(name = "svix-signature", required = false) String svixSignature,
                                     @RequestBody String body) {
        if (!verifier.verify(svixId, svixTimestamp, svixSignature, body)) {
            return ResponseEntity.badRequest().body(new ApiError("INVALID_SIGNATURE", "Chữ ký webhook không hợp lệ"));
        }
        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", "Nội dung webhook không phải JSON"));
        }
        service.handle(svixId, event);
        return ResponseEntity.noContent().build();
    }
}
