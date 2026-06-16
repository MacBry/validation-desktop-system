package com.mac.bry.desktop.service.stats;

public class NelsonRulesInterpreter {

    /**
     * Zwraca merytoryczną interpretację biznesową (GxP) dla naruszenia na karcie X-Bar.
     * 
     * @param ruleNumber Numer naruszonej reguły Nelsona (1-4)
     * @return Interpretacja zjawiska fizycznego w komorze
     */
    public static String getXBarInterpretation(int ruleNumber) {
        switch (ruleNumber) {
            case 1:
                return "Zjawisko o charakterze nagłym (tzw. przyczyna specjalna). Może wskazywać na jednorazowe silne zaburzenie, np. długotrwałe otwarcie drzwi, nagłą awarię agregatu lub włożenie dużej partii nieschłodzonego towaru.";
            case 2:
                return "Trwałe przesunięcie średniej procesu. Sugeruje zmianę o charakterze stałym, np. fizyczne rozkalibrowanie czujnika, zmianę nastawy termostatu, czy też trwałe rozszczelnienie komory (np. uszkodzona uszczelka).";
            case 3:
                return "Stały trend kierunkowy w procesie. Może zwiastować postępującą degradację systemu, np. powolne uchodzenie czynnika chłodniczego, stopniowe narastanie szronu na parowniku lub starzenie się kompresora.";
            case 4:
                return "Niestabilność oscylacyjna (preregulowanie). Najczęściej wskazuje na zbyt agresywne nastawy sterownika PID układu chłodniczego, co powoduje ciągłe 'przeciąganie' temperatury raz w górę, raz w dół.";
            default:
                return "Niezdefiniowana anomalia procesu (wymaga analizy indywidualnej).";
        }
    }

    /**
     * Zwraca merytoryczną interpretację biznesową (GxP) dla naruszenia na karcie S (zmienność).
     * 
     * @param ruleNumber Numer naruszonej reguły dla karty S (zazwyczaj tylko 1 - przekroczenie limitów)
     * @return Interpretacja zjawiska fizycznego w komorze
     */
    public static String getSChartInterpretation(int ruleNumber) {
        if (ruleNumber == 1) {
            return "Nagły skok zmienności (rozrzutu) w podgrupie. Zwiększona zmienność na karcie S (odchyleń standardowych) często sugeruje pojawienie się gwałtownych przepływów powietrza (turbulencji) w danej strefie komory, np. wskutek zablokowania wentylatora lub załączenia cyklu odszraniania (defrost).";
        }
        return "Niezdefiniowana anomalia zmienności procesu.";
    }
}
