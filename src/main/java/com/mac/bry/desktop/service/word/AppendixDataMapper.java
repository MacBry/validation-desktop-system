package com.mac.bry.desktop.service.word;

import com.mac.bry.desktop.model.RevalidationSession;

import java.util.Map;

public interface AppendixDataMapper {
    /**
     * Zwraca mapę znaczników i odpowiadających im wartości do podmiany w dokumencie Word.
     */
    Map<String, String> prepareReplacements(RevalidationSession session);
}
