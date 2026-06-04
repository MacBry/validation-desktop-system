package com.mac.bry.desktop.security.audit;

import com.mac.bry.desktop.security.model.AuditRevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        AuditRevisionEntity auditEntity = (AuditRevisionEntity) revisionEntity;
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            auditEntity.setModifiedBy(auth.getName());
        } else {
            auditEntity.setModifiedBy("SYSTEM / REGISTRATION");
        }
    }
}
