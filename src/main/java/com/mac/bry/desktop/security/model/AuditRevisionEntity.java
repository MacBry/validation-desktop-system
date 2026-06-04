package com.mac.bry.desktop.security.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import com.mac.bry.desktop.security.audit.UserRevisionListener;

@Entity
@Table(name = "revinfo")
@RevisionEntity(UserRevisionListener.class)
@Getter
@Setter
public class AuditRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "rev")
    private int id;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;

    @Column(name = "modified_by")
    private String modifiedBy;
}
