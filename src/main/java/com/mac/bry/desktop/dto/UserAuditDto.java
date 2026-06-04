package com.mac.bry.desktop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuditDto {
    private Integer revisionId;
    private LocalDateTime timestamp;
    private String modifiedBy;
    private String operationType; // ADD, MOD, DEL
    private String fieldName;
    private String oldValue;
    private String newValue;
}
