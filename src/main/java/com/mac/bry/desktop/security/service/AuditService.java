package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.dto.UserAuditDto;
import com.mac.bry.desktop.security.model.AuditRevisionEntity;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.model.Role;
import java.util.Set;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    @PersistenceContext
    private final EntityManager entityManager;
    private final com.mac.bry.desktop.security.repository.AccessLogRepository accessLogRepository;
    private final com.mac.bry.desktop.security.repository.UserRepository userRepository;

    @Transactional
    public void logAccessEvent(String username, String action, String details) {
        log.info("Zdarzenie dostępu: {} - {} - {}", username, action, details);
        User user = userRepository.findByUsername(username).orElse(null);
        accessLogRepository.save(new com.mac.bry.desktop.security.model.AccessLog(username, user, action, details));
    }

    @Transactional(readOnly = true)
    public List<UserAuditDto> getEntityHistory(Class<?> entityClass, Long entityId, String entityName) {
        log.info("Pobieranie historii audytu dla {} ID: {}", entityClass.getSimpleName(), entityId);
        AuditReader auditReader = AuditReaderFactory.get(entityManager);
        
        List<Object[]> results = auditReader.createQuery()
                .forRevisionsOfEntity(entityClass, false, true)
                .add(AuditEntity.id().eq(entityId))
                .getResultList();
        
        List<UserAuditDto> auditHistory = new ArrayList<>();
        Object previousEntity = null;
        
        for (Object[] row : results) {
            AuditRevisionEntity revEntity = (AuditRevisionEntity) row[1];
            int revId = revEntity.getId();
            Object entityAtRev = auditReader.find(entityClass, entityId, revId);
            
            // Inicjalizacja ról jeśli to użytkownik
            if (entityAtRev instanceof User) {
                User u = (User) entityAtRev;
                if (u.getRoles() != null) u.getRoles().size();
            }

            LocalDateTime ts = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(revEntity.getTimestamp()), 
                    ZoneId.systemDefault()
            );

            if (previousEntity == null) {
                String val = "";
                if (entityAtRev instanceof User) val = ((User) entityAtRev).getUsername();
                else if (entityAtRev instanceof com.mac.bry.desktop.security.model.Department) val = ((com.mac.bry.desktop.security.model.Department) entityAtRev).getName();
                else if (entityAtRev instanceof com.mac.bry.desktop.security.model.Laboratory) val = ((com.mac.bry.desktop.security.model.Laboratory) entityAtRev).getName();
                else if (entityAtRev instanceof com.mac.bry.desktop.model.ThermoRecorder) val = ((com.mac.bry.desktop.model.ThermoRecorder) entityAtRev).getSerialNumber();
                else if (entityAtRev instanceof com.mac.bry.desktop.model.Calibration) val = ((com.mac.bry.desktop.model.Calibration) entityAtRev).getCertificateNumber();
                else if (entityAtRev instanceof com.mac.bry.desktop.model.CoolingDevice) val = ((com.mac.bry.desktop.model.CoolingDevice) entityAtRev).getName();
                else if (entityAtRev instanceof com.mac.bry.desktop.model.MaterialType) val = ((com.mac.bry.desktop.model.MaterialType) entityAtRev).getName();

                auditHistory.add(UserAuditDto.builder()
                        .revisionId(revEntity.getId())
                        .timestamp(ts)
                        .modifiedBy(revEntity.getModifiedBy())
                        .operationType("UTWORZENIE")
                        .fieldName(entityName)
                        .newValue(val)
                        .build());
            } else {
                if (entityAtRev instanceof User) {
                    compareFields(auditHistory, (User) previousEntity, (User) entityAtRev, revEntity.getId(), ts, revEntity.getModifiedBy());
                } else if (entityAtRev instanceof com.mac.bry.desktop.security.model.Department) {
                    compareDeptFields(auditHistory, (com.mac.bry.desktop.security.model.Department) previousEntity, (com.mac.bry.desktop.security.model.Department) entityAtRev, revEntity.getId(), ts, revEntity.getModifiedBy());
                } else if (entityAtRev instanceof com.mac.bry.desktop.security.model.Laboratory) {
                    compareLabFields(auditHistory, (com.mac.bry.desktop.security.model.Laboratory) previousEntity, (com.mac.bry.desktop.security.model.Laboratory) entityAtRev, revEntity.getId(), ts, revEntity.getModifiedBy());
                } else if (entityAtRev instanceof com.mac.bry.desktop.model.ThermoRecorder) {
                    compareRecorderFields(auditHistory, (com.mac.bry.desktop.model.ThermoRecorder) previousEntity, (com.mac.bry.desktop.model.ThermoRecorder) entityAtRev, revEntity.getId(), ts, revEntity.getModifiedBy());
                } else if (entityAtRev instanceof com.mac.bry.desktop.model.Calibration) {
                    compareCalibrationFields(auditHistory, (com.mac.bry.desktop.model.Calibration) previousEntity, (com.mac.bry.desktop.model.Calibration) entityAtRev, revEntity.getId(), ts, revEntity.getModifiedBy());
                } else if (entityAtRev instanceof com.mac.bry.desktop.model.CoolingDevice) {
                    compareCoolingDeviceFields(auditHistory, (com.mac.bry.desktop.model.CoolingDevice) previousEntity, (com.mac.bry.desktop.model.CoolingDevice) entityAtRev, revEntity.getId(), ts, revEntity.getModifiedBy());
                } else if (entityAtRev instanceof com.mac.bry.desktop.model.MaterialType) {
                    compareMaterialTypeFields(auditHistory, (com.mac.bry.desktop.model.MaterialType) previousEntity, (com.mac.bry.desktop.model.MaterialType) entityAtRev, revEntity.getId(), ts, revEntity.getModifiedBy());
                }
            }
            previousEntity = entityAtRev;
        }
        
        java.util.Collections.reverse(auditHistory);
        return auditHistory;
    }

    @Transactional(readOnly = true)
    public List<UserAuditDto> getUserHistory(Long userId) {
        return getEntityHistory(User.class, userId, "Konto");
    }

    private void compareFields(List<UserAuditDto> history, User oldU, User newU, int revId, LocalDateTime ts, String modBy) {
        if (!java.util.Objects.equals(oldU.getFirstName(), newU.getFirstName())) {
            addEntry(history, revId, ts, modBy, "Imię", oldU.getFirstName(), newU.getFirstName());
        }
        if (!java.util.Objects.equals(oldU.getLastName(), newU.getLastName())) {
            addEntry(history, revId, ts, modBy, "Nazwisko", oldU.getLastName(), newU.getLastName());
        }
        if (!java.util.Objects.equals(oldU.getEmail(), newU.getEmail())) {
            addEntry(history, revId, ts, modBy, "E-mail", oldU.getEmail(), newU.getEmail());
        }
        if (oldU.isEnabled() != newU.isEnabled()) {
            addEntry(history, revId, ts, modBy, "Aktywny", String.valueOf(oldU.isEnabled()), String.valueOf(newU.isEnabled()));
        }
        if (oldU.isLocked() != newU.isLocked()) {
            addEntry(history, revId, ts, modBy, "Zablokowany", String.valueOf(oldU.isLocked()), String.valueOf(newU.isLocked()));
        }
        if (oldU.isMustChangePassword() != newU.isMustChangePassword()) {
            addEntry(history, revId, ts, modBy, "Wymuszenie hasła", String.valueOf(oldU.isMustChangePassword()), String.valueOf(newU.isMustChangePassword()));
        }
        
        // Lokalizacja (zabezpieczona przed usuniętymi encjami)
        String oldDept = safeGetUnitName(oldU.getDepartment());
        String newDept = safeGetUnitName(newU.getDepartment());
        if (!java.util.Objects.equals(oldDept, newDept)) {
            addEntry(history, revId, ts, modBy, "Dział", oldDept, newDept);
        }

        String oldLab = safeGetUnitName(oldU.getLaboratory());
        String newLab = safeGetUnitName(newU.getLaboratory());
        if (!java.util.Objects.equals(oldLab, newLab)) {
            addEntry(history, revId, ts, modBy, "Pracownia", oldLab, newLab);
        }
        
        // Porównanie ról
        Set<String> oldRoles = oldU.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
        Set<String> newRoles = newU.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());
        
        log.info("DEBUG - Rewizja {}: Stare role: {}, Nowe role: {}", revId, oldRoles, newRoles);
        
        if (!oldRoles.equals(newRoles)) {
            addEntry(history, revId, ts, modBy, "Uprawnienia", oldRoles.toString(), newRoles.toString());
        }
    }

    private void compareDeptFields(List<UserAuditDto> history, com.mac.bry.desktop.security.model.Department oldD, com.mac.bry.desktop.security.model.Department newD, int revId, LocalDateTime ts, String modBy) {
        if (!java.util.Objects.equals(oldD.getName(), newD.getName())) {
            addEntry(history, revId, ts, modBy, "Nazwa", oldD.getName(), newD.getName());
        }
        if (!java.util.Objects.equals(oldD.getAbbreviation(), newD.getAbbreviation())) {
            addEntry(history, revId, ts, modBy, "Skrót", oldD.getAbbreviation(), newD.getAbbreviation());
        }
        if (!java.util.Objects.equals(oldD.getDescription(), newD.getDescription())) {
            addEntry(history, revId, ts, modBy, "Opis", oldD.getDescription(), newD.getDescription());
        }
    }

    private void compareLabFields(List<UserAuditDto> history, com.mac.bry.desktop.security.model.Laboratory oldL, com.mac.bry.desktop.security.model.Laboratory newL, int revId, LocalDateTime ts, String modBy) {
        if (!java.util.Objects.equals(oldL.getName(), newL.getName())) {
            addEntry(history, revId, ts, modBy, "Nazwa", oldL.getName(), newL.getName());
        }
        if (!java.util.Objects.equals(oldL.getAbbreviation(), newL.getAbbreviation())) {
            addEntry(history, revId, ts, modBy, "Skrót", oldL.getAbbreviation(), newL.getAbbreviation());
        }
        String oldD = oldL.getDepartment() != null ? oldL.getDepartment().getName() : "-";
        String newD = newL.getDepartment() != null ? newL.getDepartment().getName() : "-";
        if (!java.util.Objects.equals(oldD, newD)) {
            addEntry(history, revId, ts, modBy, "Dział nadrzędny", oldD, newD);
        }
    }

    private void compareRecorderFields(List<UserAuditDto> history, com.mac.bry.desktop.model.ThermoRecorder oldR, com.mac.bry.desktop.model.ThermoRecorder newR, int revId, LocalDateTime ts, String modBy) {
        if (!java.util.Objects.equals(oldR.getSerialNumber(), newR.getSerialNumber())) {
            addEntry(history, revId, ts, modBy, "Numer Seryjny", oldR.getSerialNumber(), newR.getSerialNumber());
        }
        if (!java.util.Objects.equals(oldR.getModel(), newR.getModel())) {
            addEntry(history, revId, ts, modBy, "Model", 
                oldR.getModel() != null ? oldR.getModel().getName() : "-", 
                newR.getModel() != null ? newR.getModel().getName() : "-");
        }
        if (oldR.getStatus() != newR.getStatus()) {
            addEntry(history, revId, ts, modBy, "Status", oldR.getStatus().getDisplayName(), newR.getStatus().getDisplayName());
        }
        if (!java.util.Objects.equals(oldR.getResolution(), newR.getResolution())) {
            addEntry(history, revId, ts, modBy, "Rozdzielczość", String.valueOf(oldR.getResolution()), String.valueOf(newR.getResolution()));
        }
        String oldDept = safeGetUnitName(oldR.getDepartment());
        String newDept = safeGetUnitName(newR.getDepartment());
        if (!java.util.Objects.equals(oldDept, newDept)) {
            addEntry(history, revId, ts, modBy, "Dział", oldDept, newDept);
        }
    }

    private void compareCalibrationFields(List<UserAuditDto> history, com.mac.bry.desktop.model.Calibration oldC, com.mac.bry.desktop.model.Calibration newC, int revId, LocalDateTime ts, String modBy) {
        if (!java.util.Objects.equals(oldC.getCertificateNumber(), newC.getCertificateNumber())) {
            addEntry(history, revId, ts, modBy, "Nr Świadectwa", oldC.getCertificateNumber(), newC.getCertificateNumber());
        }
        if (!java.util.Objects.equals(oldC.getCalibrationDate(), newC.getCalibrationDate())) {
            addEntry(history, revId, ts, modBy, "Data Wzorcowania", String.valueOf(oldC.getCalibrationDate()), String.valueOf(newC.getCalibrationDate()));
        }
        if (!java.util.Objects.equals(oldC.getValidUntil(), newC.getValidUntil())) {
            addEntry(history, revId, ts, modBy, "Ważne do", String.valueOf(oldC.getValidUntil()), String.valueOf(newC.getValidUntil()));
        }
        if (!java.util.Objects.equals(oldC.getCertificateFilePath(), newC.getCertificateFilePath())) {
            addEntry(history, revId, ts, modBy, "Ścieżka do skanu", oldC.getCertificateFilePath(), newC.getCertificateFilePath());
        }
    }

    private void compareCoolingDeviceFields(List<UserAuditDto> history, com.mac.bry.desktop.model.CoolingDevice oldD, com.mac.bry.desktop.model.CoolingDevice newD, int revId, LocalDateTime ts, String modBy) {
        if (!java.util.Objects.equals(oldD.getInventoryNumber(), newD.getInventoryNumber())) {
            addEntry(history, revId, ts, modBy, "Numer inwentarzowy", oldD.getInventoryNumber(), newD.getInventoryNumber());
        }
        if (!java.util.Objects.equals(oldD.getName(), newD.getName())) {
            addEntry(history, revId, ts, modBy, "Nazwa urządzenia", oldD.getName(), newD.getName());
        }
        String oldDept = safeGetUnitName(oldD.getDepartment());
        String newDept = safeGetUnitName(newD.getDepartment());
        if (!java.util.Objects.equals(oldDept, newDept)) {
            addEntry(history, revId, ts, modBy, "Dział", oldDept, newDept);
        }
        String oldLab = safeGetUnitName(oldD.getLaboratory());
        String newLab = safeGetUnitName(newD.getLaboratory());
        if (!java.util.Objects.equals(oldLab, newLab)) {
            addEntry(history, revId, ts, modBy, "Pracownia", oldLab, newLab);
        }

        // Porównanie komór chłodniczych (wielokomorowość)
        java.util.Map<Long, com.mac.bry.desktop.model.CoolingChamber> oldChambers = new java.util.HashMap<>();
        if (oldD.getChambers() != null) {
            for (com.mac.bry.desktop.model.CoolingChamber c : oldD.getChambers()) {
                if (c.getId() != null) oldChambers.put(c.getId(), c);
            }
        }

        java.util.Map<Long, com.mac.bry.desktop.model.CoolingChamber> newChambers = new java.util.HashMap<>();
        if (newD.getChambers() != null) {
            for (com.mac.bry.desktop.model.CoolingChamber c : newD.getChambers()) {
                if (c.getId() != null) newChambers.put(c.getId(), c);
            }
        }

        // Dodane lub zmodyfikowane komory
        for (com.mac.bry.desktop.model.CoolingChamber newC : newChambers.values()) {
            com.mac.bry.desktop.model.CoolingChamber oldC = oldChambers.get(newC.getId());
            if (oldC == null) {
                addEntry(history, revId, ts, modBy, "Komora: " + newC.getChamberName(), "-", "UTWORZONO (Typ: " + newC.getChamberType().getDisplayName() + ")");
            } else {
                if (!java.util.Objects.equals(oldC.getChamberName(), newC.getChamberName())) {
                    addEntry(history, revId, ts, modBy, "Komora (ID: " + newC.getId() + ") - Nazwa", oldC.getChamberName(), newC.getChamberName());
                }
                if (oldC.getChamberType() != newC.getChamberType()) {
                    addEntry(history, revId, ts, modBy, "Komora (" + newC.getChamberName() + ") - Typ", 
                        oldC.getChamberType() != null ? oldC.getChamberType().getDisplayName() : "-", 
                        newC.getChamberType() != null ? newC.getChamberType().getDisplayName() : "-");
                }
                String oldMat = oldC.getMaterialType() != null ? oldC.getMaterialType().getName() : "-";
                String newMat = newC.getMaterialType() != null ? newC.getMaterialType().getName() : "-";
                if (!java.util.Objects.equals(oldMat, newMat)) {
                    addEntry(history, revId, ts, modBy, "Komora (" + newC.getChamberName() + ") - Typ materiału", oldMat, newMat);
                }
                if (!java.util.Objects.equals(oldC.getMinOperatingTemp(), newC.getMinOperatingTemp())) {
                    addEntry(history, revId, ts, modBy, "Komora (" + newC.getChamberName() + ") - Temp Min", String.valueOf(oldC.getMinOperatingTemp()), String.valueOf(newC.getMinOperatingTemp()));
                }
                if (!java.util.Objects.equals(oldC.getMaxOperatingTemp(), newC.getMaxOperatingTemp())) {
                    addEntry(history, revId, ts, modBy, "Komora (" + newC.getChamberName() + ") - Temp Max", String.valueOf(oldC.getMaxOperatingTemp()), String.valueOf(newC.getMaxOperatingTemp()));
                }
                if (!java.util.Objects.equals(oldC.getVolume(), newC.getVolume())) {
                    addEntry(history, revId, ts, modBy, "Komora (" + newC.getChamberName() + ") - Pojemność (m³)", String.valueOf(oldC.getVolume()), String.valueOf(newC.getVolume()));
                }
            }
        }

        // Usunięte komory
        for (com.mac.bry.desktop.model.CoolingChamber oldC : oldChambers.values()) {
            if (!newChambers.containsKey(oldC.getId())) {
                addEntry(history, revId, ts, modBy, "Komora: " + oldC.getChamberName(), "ISTNIAŁA", "USUNIĘTO");
            }
        }
    }

    private void compareMaterialTypeFields(List<UserAuditDto> history, com.mac.bry.desktop.model.MaterialType oldM, com.mac.bry.desktop.model.MaterialType newM, int revId, LocalDateTime ts, String modBy) {
        if (!java.util.Objects.equals(oldM.getName(), newM.getName())) {
            addEntry(history, revId, ts, modBy, "Nazwa", oldM.getName(), newM.getName());
        }
        if (!java.util.Objects.equals(oldM.getDescription(), newM.getDescription())) {
            addEntry(history, revId, ts, modBy, "Opis", oldM.getDescription(), newM.getDescription());
        }
        if (!java.util.Objects.equals(oldM.getMinStorageTemp(), newM.getMinStorageTemp())) {
            addEntry(history, revId, ts, modBy, "Temp Min", String.valueOf(oldM.getMinStorageTemp()), String.valueOf(newM.getMinStorageTemp()));
        }
        if (!java.util.Objects.equals(oldM.getMaxStorageTemp(), newM.getMaxStorageTemp())) {
            addEntry(history, revId, ts, modBy, "Temp Max", String.valueOf(oldM.getMaxStorageTemp()), String.valueOf(newM.getMaxStorageTemp()));
        }
        if (!java.util.Objects.equals(oldM.getActivationEnergy(), newM.getActivationEnergy())) {
            addEntry(history, revId, ts, modBy, "Energia aktywacji Ea", String.valueOf(oldM.getActivationEnergy()), String.valueOf(newM.getActivationEnergy()));
        }
        if (!java.util.Objects.equals(oldM.getStandardSource(), newM.getStandardSource())) {
            addEntry(history, revId, ts, modBy, "Standard / Norma", oldM.getStandardSource(), oldM.getStandardSource());
        }
        if (!java.util.Objects.equals(oldM.getRequiresMapping(), newM.getRequiresMapping())) {
            addEntry(history, revId, ts, modBy, "Wymóg mapowania", String.valueOf(oldM.getRequiresMapping()), String.valueOf(newM.getRequiresMapping()));
        }
        if (!java.util.Objects.equals(oldM.getActive(), newM.getActive())) {
            addEntry(history, revId, ts, modBy, "Status aktywności", String.valueOf(oldM.getActive()), String.valueOf(newM.getActive()));
        }
    }

    private void addEntry(List<UserAuditDto> history, int revId, LocalDateTime ts, String modBy, String field, String oldV, String newV) {
        history.add(UserAuditDto.builder()
                .revisionId(revId)
                .timestamp(ts)
                .modifiedBy(modBy)
                .operationType("MODYFIKACJA")
                .fieldName(field)
                .oldValue(oldV)
                .newValue(newV)
                .build());
    }

    private String safeGetUnitName(Object unit) {
        if (unit == null) return "-";
        try {
            if (unit instanceof com.mac.bry.desktop.security.model.Department) {
                return ((com.mac.bry.desktop.security.model.Department) unit).getName();
            }
            if (unit instanceof com.mac.bry.desktop.security.model.Laboratory) {
                return ((com.mac.bry.desktop.security.model.Laboratory) unit).getName();
            }
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return "[USUNIĘTE]";
        }
        return "-";
    }
}
