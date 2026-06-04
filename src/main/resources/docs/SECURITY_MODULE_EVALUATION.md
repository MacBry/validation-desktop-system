# Raport Oceny Technicznej: Moduł Zarządzania Użytkownikami
**System**: Validation Desktop System
**Kontekst**: Jednoinstancyjna aplikacja medyczna w sieci izolowanej (RCKiK Poznań)

## 1. Architektura i Fundamenty Techniczne
Moduł został zbudowany w oparciu o stos technologiczny **Spring Boot + JavaFX + Hibernate Envers**. Jest to wybór optymalny dla środowisk typu RCKiK z następujących powodów:
*   **Izolacja**: Aplikacja desktopowa nie wymaga wystawiania portów na zewnątrz (brak serwera WWW dostępnego publicznie).
*   **Jednoinstancyjność**: Wykorzystanie lokalnej lub sieciowej bazy danych (MySQL/MariaDB) zapewnia wysoką wydajność przy relatywnie niskich kosztach utrzymania infrastruktury.
*   **Flyway**: Automatyczne migracje bazy danych gwarantują spójność schematu na wszystkich stanowiskach.

## 2. Bezpieczeństwo (Security)
Zaimplementowane mechanizmy spełniają wysokie standardy bezpieczeństwa danych medycznych:
*   **Ochrona Brute-force**: Progresywne opóźnienie logowania oraz twarda blokada konta po 5 próbach skutecznie chronią przed atakami słownikowymi wewnątrz sieci.
*   **Polityka Haseł**: Restrykcyjne wymagania (8+ znaków, znaki specjalne, cyfry) oraz wymuszanie zmiany hasła (Password Aging/Must Change) są zgodne z zaleceniami GIODO/RODO.
*   **Zasada najmniejszych uprawnień**: Precyzyjne sterowanie rolami (`ROLE_USER`, `ROLE_QA`, `ROLE_SUPER_ADMIN`) pozwala na ścisłą kontrolę dostępu do funkcji walidacyjnych.

## 3. Zgodność z 21 CFR Part 11 (Audit Trail)
To najsilniejszy punkt modułu w kontekście walidacji systemów komputerowych:
*   **Niezaprzeczalność**: Dzięki `UserRevisionListener`, każda zmiana ma przypisanego autora (username) i znacznik czasu.
*   **Kompletność**: Audyt obejmuje nie tylko dane profilowe, ale również krytyczne zmiany uprawnień (relacje Many-to-Many), co jest często pomijane w prostych systemach.
*   **Odtwarzalność**: Mechanizm baseline (V8) pozwala na śledzenie historii nawet dla danych wprowadzonych na początku cyklu życia systemu.

## 4. Analiza pod kątem RCKiK Poznań (Środowisko Izolowane)
### Mocne strony:
1.  **Brak zależności chmurowych**: System może działać całkowicie offline. Jedynym punktem styku może być serwer SMTP (do powiadomień).
2.  **Niskie wymagania infrastrukturalne**: Nie wymaga klastrów Kubernetes ani skomplikowanych serwerów aplikacji.
3.  **Łatwa walidacja**: Przejrzysta struktura bazy danych i dokumentacja techniczna ułatwiają proces IQ/OQ/PQ.

### Obszary do rozważenia / Ryzyka:
1.  **SMTP**: Obecnie skonfigurowany jest serwis Brevo (Cloud). W izolowanej sieci RCKiK konieczne będzie przełączenie na wewnętrzny serwer pocztowy (np. MS Exchange lub lokalny Postfix).
2.  **Backup**: W systemie jednoinstancyjnym kluczowe jest zapewnienie automatycznego backupu bazy danych na poziomie serwera (poza aplikacją).
3.  **Logowanie domenowe**: Docelowo warto rozważyć integrację z Active Directory (LDAP/SSO), aby użytkownicy RCKiK nie musieli pamiętać osobnych haseł do aplikacji.

## 5. Werdykt
**Moduł nadaje się do rozwijania i wdrożenia w środowisku RCKiK Poznań.** 

Posiada solidne fundamenty bezpieczeństwa i audytu, które są kluczowe w sektorze medycznym. Implementacja jest czysta, dobrze udokumentowana i pokryta testami (34 scenariusze), co minimalizuje ryzyko błędów regresji przy dalszym rozwoju.

> [!TIP]
> **Rekomendacja**: Następnym krokiem rozwojowym powinna być implementacja podpisu elektronicznego (Electronic Signature) pod raportami walidacyjnymi, wykorzystująca te same poświadczenia, które zostały zaimplementowane w tym module.
