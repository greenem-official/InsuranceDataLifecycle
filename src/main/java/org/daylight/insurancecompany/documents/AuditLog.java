package org.daylight.insurancecompany.documents;

import jakarta.persistence.Id;
import lombok.*;
import org.daylight.insurancecompany.dto.AuditAction;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    private String id;

    private Instant timestamp;

    private Long userId;

    private AuditAction action;

    private Map<String, Object> details;
}
