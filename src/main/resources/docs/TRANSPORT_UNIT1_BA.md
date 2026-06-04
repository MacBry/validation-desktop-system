# Business Analysis (BA): Ewidencja Urządzeń i Tras Transportowych (Jednostka 1)

## 1. Cel Biznesowy i Kontekst GxP
Zgodnie z wymaganiami Dobrej Praktyki Dystrybucyjnej (DPD) oraz Standardowej Procedury Operacyjnej **SOP-CKiK-DZJ-WK-004**, transport składników krwi i próbek do badań musi odbywać się w kontrolowanych warunkach temperaturowych. 

Aby umożliwić cyfrową walidację i rewalidację tych procesów, system musi posiadać jednoznaczną ewidencję (słownik) zasobów biorących udział w transporcie:
1. **Urządzeń transportowych** (samochody z agregatem chłodniczym, mobilne zamrażarki, termotorby, pojemniki na suchy lód).
2. **Tras transportowych** (trasy rutynowe do szpitali/oddziałów oraz transporty wewnętrzne między budynkami RCKiK).

Prawidłowa ewidencja pozwala na automatyczne przypisywanie kryteriów akceptacji, weryfikację czasu trwania transportu oraz zapewnienie spójności danych (**Data Integrity**) wymaganej przez inspekcje GxP.

---

## 2. Wymagania Funkcjonalne

### 2.1. Ewidencja Urządzeń Transportowych (Transport Units)
System musi umożliwiać rejestrację i edycję urządzeń transportowych o następujących parametrach:
* **Nazwa urządzenia**: Czytelny identyfikator (np. *„Samochód Ford Transit chłodnia”*, *„Lodówka przenośna Waeco CFX”*).
* **Numer inwentarzowy / seryjny**: Unikalny kod rejestru środków trwałych (np. *DEV-PORT-001*).
* **Numer rejestracyjny**: Pole opcjonalne, wymagane dla pojazdów samochodowych.
* **Typ transportu**: Wybór ze słownika zamkniętego:
  * `CAR_CHAMBER` (Komora klimatyzowana w samochodzie)
  * `PORTABLE_ACTIVE` (Aktywna lodówka przenośna/zamrażarka)
  * `COOLER_BAG` (Termotorba pasywna)
  * `DRY_ICE_BOX` (Pojemnik styropianowy z suchym lodem)
* **Status**: Aktywny / Wycofany z użycia.

### 2.2. Ewidencja Tras Transportowych (Transport Routes)
Słownik tras transportowych musi zawierać:
* **Nazwa trasy**: (np. *„Trasa Północna – Szpital Wojewódzki”*, *„Magazyn Główny - Mroźnia Centralna”*).
* **Kod trasy**: Krótki unikalny identyfikator (np. *TR-NORTH-01*).
* **Punkt początkowy i końcowy**: (np. *RCKiK Budynek A -> Mroźnia Centralna*).
* **Szacowany czas przejazdu**: Wyrażony w minutach (służy do automatycznego porównania czasu trwania serii pomiarowej z planowanym czasem trasy).

### 2.3. Sesje Walidacji Transportu (Transport Validation Sessions)
Struktura reprezentująca pojedyncze badanie walidacyjne:
* Powiązanie z konkretnym urządzeniem transportowym.
* Powiązanie z wybraną trasą.
* Data i godzina przeprowadzenia walidacji.
* Nazwa operatora wykonującego badanie.
* Status sesji: W toku (*Draft*), Zaakceptowana (*Completed*), Odrzucona (*Rejected*).

---

## 3. Bezpieczeństwo i Audit Trail (GxP)
Każda zmiana parametrów urządzeń transportowych (np. zmiana nazwy, typu) oraz tras musi być logowana w rejestrze zdarzeń (**Audit Trail**) za pomocą mechanizmu wersjonowania. Pozwala to audytorom na odtworzenie historycznej konfiguracji pojazdu chłodniczego z momentu, gdy wykonywana była walidacja rok wcześniej.
