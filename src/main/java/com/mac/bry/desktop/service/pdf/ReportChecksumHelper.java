package com.mac.bry.desktop.service.pdf;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Slf4j
public class ReportChecksumHelper {

    public static String calculateSha256Checksum(RevalidationSession session, List<RevalidationSession.GridPosition> activePositions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();

            // Powiązanie z komorą i urządzeniem nadrzędnym
            sb.append(session.getCoolingDevice().getInventoryNumber())
              .append(session.getCoolingChamber().getChamberName());

            // Odczyt zsynchronizowanej macierzy pomiarów
            int pointCount = session.getAssignedPositions().values().iterator().next().getSeries().getMeasurements().size();
            for (int i = 0; i < pointCount; i++) {
                sb.append(i + 1);
                for (RevalidationSession.GridPosition pos : activePositions) {
                    RevalidationSession.PositionData d = session.getAssignedPositions().get(pos);
                    ThermoMeasurementPoint pt = d.getSeries().getMeasurements().get(i);
                    sb.append(pt.getTimestampLocal().toString())
                      .append(pt.getRawCelsius());
                }
            }

            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            log.error("Błąd podczas obliczania sumy kontrolnej integralności sesji", e);
            return "INTEGRITY_ERROR";
        }
    }
}
