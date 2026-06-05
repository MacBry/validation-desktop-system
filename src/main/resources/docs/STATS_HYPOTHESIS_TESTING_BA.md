# Analiza Biznesowa (BA) - Testowanie Hipotez Statystycznych

## 1. Cel Biznesowy
Umożliwienie użytkownikowi (np. QA Managerowi, Walidatorowi) naukowego potwierdzenia założeń dotyczących stref temperaturowych. Testowanie hipotez zastępuje subiektywną ocenę danych wykresowych obiektywnym dowodem matematycznym o określonym poziomie istotności ($\alpha = 0.05$).

## 2. Kontekst Walidacyjny
W procesach walidacji komór magazynowych i chłodniczych często pojawiają się pytania:
*   *Czy temperatura na najwyższej półce jest statystycznie wyższa niż na najniższej?* (ANOVA / t-Student).
*   *Czy po wymianie uszczelki drzwi stabilność temperatury uległa poprawie?* (Test F / Test t-Studenta dla prób zależnych).
*   *Czy dwa czujniki umieszczone blisko siebie wskazują równoważne wartości?* (Test TOST).

## 3. Wymagania Funkcjonalne

### REQ-01: Test Równoważności TOST (Two One-Sided Tests)
Tradycyjny test t-Studenta sprawdza jedynie, czy średnie różnią się od siebie. Brak statystycznie istotnej różnicy nie oznacza równoważności. W walidacji wymagany jest **test TOST**:
*   Definiuje się przedział tolerancji (equivalence bounds, np. $[-0.5^\circ\text{C}, +0.5^\circ\text{C}]$).
*   Test sprawdza dwie hipotezy jednostronne: czy różnica średnich jest większa niż dolny limit oraz czy jest mniejsza niż górny limit.
*   Jeśli obie hipotezy są odrzucone z poziomem istotności $\alpha = 0.05$, system stwierdza **statystyczną równoważność** stref.

### REQ-02: Porównanie Wielu Sensorów (ANOVA / Kruskal-Wallis)
*   Użytkownik wybiera grupę czujników (np. wszystkie czujniki poziomu górnego vs. dolnego).
*   System automatycznie sprawdza normalność rozkładu (Shapiro-Wilk) oraz jednorodność wariancji (Test Levene'a/Bartletta).
*   Jeśli warunki są spełnione, wykonywany jest test **ANOVA**. W przeciwnym wypadku system automatycznie przełącza się na nieparametryczny test **Kruskala-Wallisa**.
*   Wynik prezentowany jest jako wartość p (p-value). Wartość $p < 0.05$ oznacza, że przynajmniej jedna strefa różni się w sposób istotny statystycznie od pozostałych.

### REQ-03: Test Jednorodności Wariancji (F-test)
*   Porównanie wariancji dwóch czujników (np. czujnik referencyjny vs. czujnik przy drzwiach).
*   Weryfikacja, czy wahania temperatury w strefie krytycznej są istotnie większe niż w strefie stabilnej.

## 4. Prezentacja Wyników (UX)
Raport z testów hipotez musi zawierać:
*   Jasne podsumowanie dla audytora: **"POTWIERDZONO RÓWNOWAŻNOŚĆ"** lub **"WYKRYTO STATYSTYCZNIE ISTOTNĄ RÓŻNICĘ"** (zamiast surowych definicji statystycznych).
*   Przedziały ufności (Confidence Intervals) zwizualizowane graficznie.
