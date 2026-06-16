# Analiza Biznesowa (BA) - Interpretacja Reguł Nelsona (Nelson Rules)

## 1. Cel Biznesowy
Moduł statystyczny w aplikacji VCC Validation Desktop wykorzystuje Karty Kontrolne Shewharta (X-Bar oraz S) wraz z testami stabilności procesu (tzw. Regułami Nelsona). Sam fakt wykrycia "naruszenia reguły 2" nie niesie za sobą wartości biznesowej, o ile użytkownik (Inżynier Walidacji, Audytor QA) nie wie, co to fizycznie oznacza w komorze klimatycznej. 

Celem funkcjonalności jest:
1. Przetłumaczenie suchych wyników statystycznych z algorytmów na język zrozumiały w świecie GxP.
2. Wsparcie inżyniera we wnioskowaniu, poprzez bezpośrednie podpowiedzi w GUI (tzw. "Diagnostyka Statystyczna").
3. Skrócenie czasu trwania diagnozy anomalii w raportach rewalidacyjnych.

## 2. Opis Zjawisk i Ich Interpretacje Biznesowe

Algorytmy analizują karty X-Bar i S, a w przypadku detekcji przypisują kod reguły. Zgodnie z wymaganiami zaprojektowano poniższe mapowanie:

### 2.1. Karta X-Bar (Średnie temperatur)
| Nr Reguły | Definicja Statystyczna | Interpretacja Biznesowa / GxP | Potencjalne Przyczyny w Komorze Chłodniczej |
|-----------|------------------------|--------------------------------|---------------------------------------------|
| **1** | 1 punkt poza granicami kontrolnymi (UCL/LCL) (ponad 3-sigma). | Zjawisko o charakterze nagłym (tzw. przyczyna specjalna). Wskazuje na jednorazowe, silne zaburzenie układu stabilnego. | Długotrwałe otwarcie drzwi komory, awaria agregatu (np. brak zasilania), włożenie dużej partii nieschłodzonego towaru. |
| **2** | 9 kolejnych punktów po tej samej stronie linii centralnej (CL). | Trwałe przesunięcie średniej procesu. Zmiana zachowania systemu o charakterze stałym i nielosowym. | Fizyczne rozkalibrowanie czujnika rejestratora, trwała zmiana nastawy termostatu, awaria grzałki defrostu. |
| **3** | 6 kolejnych punktów wykazujących stały trend (rosnący lub malejący). | Stały trend kierunkowy. Zwiastuje powolną, ale ciągłą zmianę parametru. | Powolne uchodzenie czynnika chłodniczego z układu, stopniowe narastanie szronu na parowniku, powolna degradacja sprężarki. |
| **4** | 14 kolejnych punktów naprzemiennie rosnących i malejących. | Niestabilność oscylacyjna (tzw. przeregulowanie). Zjawisko o charakterze sztucznie "mechanicznym". | Zbyt agresywne nastawy sterownika PID, co powoduje ciągłe wahania "załączenie chłodzenia" -> spadek poniżej nastawy -> "wyłączenie" -> wzrost powyżej nastawy. |

### 2.2. Karta S (Odchylenia standardowe)
| Nr Reguły | Definicja Statystyczna | Interpretacja Biznesowa / GxP | Potencjalne Przyczyny w Komorze Chłodniczej |
|-----------|------------------------|--------------------------------|---------------------------------------------|
| **1** | Odchylenie standardowe poza granicami (UCL). | Nagły skok zmienności (rozrzutu) w podgrupie. Proces w tym czasie staje się "rozstrojony". | Gwałtowne przepływy powietrza (np. wskutek odblokowania zablokowanego wcześniej wentylatora), wejście układu w intensywny cykl odszraniania (defrost). |

## 3. Wartość Dodana dla Klienta (Audyty GxP)
Inspektorzy z GIF (Główny Inspektorat Farmaceutyczny) lub audytorzy ISO bardzo często wymagają od inżynierów wytłumaczenia, dlaczego na danym wykresie widoczne są piki, a w tabeli widnieją odchylenia stabilności. Funkcjonalność ta automatycznie dostarcza inżynierom "gotowców" językowych do wykorzystania we wnioskach z rewalidacji, co znacząco zmniejsza ryzyko uwag audytowych.
