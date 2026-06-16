package com.mac.bry.desktop.service.word;

import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ValidationPlanNumber;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;

import java.util.List;
import java.util.Map;

public abstract class AbstractAppendixDataMapper implements AppendixDataMapper {

    protected ValidationPlanNumberRepository validationPlanNumberRepository;

    public AbstractAppendixDataMapper(ValidationPlanNumberRepository validationPlanNumberRepository) {
        this.validationPlanNumberRepository = validationPlanNumberRepository;
    }

    protected void populateRpwPlaceholders(Map<String, String> replacements, RevalidationSession session) {
        String nrRpw = "—";
        String skrotPracowni = "—";
        String rokRpw = "—";

        if (session.getCoolingDevice() != null) {
            CoolingDevice device = session.getCoolingDevice();
            if (device.getLaboratory() != null && device.getLaboratory().getAbbreviation() != null) {
                skrotPracowni = device.getLaboratory().getAbbreviation();
            } else if (device.getDepartment() != null && device.getDepartment().getAbbreviation() != null) {
                skrotPracowni = device.getDepartment().getAbbreviation();
            }

            if (validationPlanNumberRepository != null) {
                List<ValidationPlanNumber> planNumbers = validationPlanNumberRepository.findByCoolingDeviceOrderByYearDesc(device);
                if (planNumbers != null && !planNumbers.isEmpty()) {
                    ValidationPlanNumber activePlan = planNumbers.get(0);
                    if (activePlan.getPlanNumber() != null) {
                        nrRpw = String.valueOf(activePlan.getPlanNumber());
                    }
                    if (activePlan.getYear() != null) {
                        rokRpw = String.valueOf(activePlan.getYear());
                    }
                    if (activePlan.getCoolingDevice() != null) {
                        CoolingDevice activeDevice = activePlan.getCoolingDevice();
                        if (activeDevice.getLaboratory() != null && activeDevice.getLaboratory().getAbbreviation() != null) {
                            skrotPracowni = activeDevice.getLaboratory().getAbbreviation();
                        } else if (activeDevice.getDepartment() != null && activeDevice.getDepartment().getAbbreviation() != null) {
                            skrotPracowni = activeDevice.getDepartment().getAbbreviation();
                        }
                    }
                }
            }
        }

        if (skrotPracowni == null || skrotPracowni.trim().isEmpty()) {
            skrotPracowni = "—";
        }

        replacements.put("$NrRPW$", nrRpw);
        replacements.put("$skrotPracowni$", skrotPracowni);
        replacements.put("$rokRPW$", rokRpw);
    }
}
