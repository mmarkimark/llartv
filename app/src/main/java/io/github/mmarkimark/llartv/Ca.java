package io.github.mmarkimark.llartv;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Every text LlarTV shows, in Catalan, whatever language the TV itself is set to. */
final class Ca {
    private Ca() {}

    static final Locale LOCALE = new Locale("ca", "ES");

    static final String INPUTS = "Entrades", WIFI = "Wi-Fi", SETTINGS = "Configuració";
    static final String OFFLINE = "Sense connexió", ETHERNET = "Ethernet", CONNECTED = "Connectat";
    static final String LIVE_TV = "TV en directe", NO_LIVE_TV = "Aquesta TV no té cap app de TV en directe";
    static final String CANT_OPEN = "No es pot obrir ", NOT_AVAILABLE = "No disponible", CANT_SWITCH_INPUT = "No es pot canviar d'entrada";
    static final String NEXT_MATCH = "PROPER PARTIT", LIVE_NOW = "EN DIRECTE", TODAY = "Avui", TOMORROW = "Demà";
    static final String HOME = "A casa", AWAY = "Fora", VS = "contra ";
    static final String WEATHER = "Temps", NEWS = "Notícies";

    // LlarTV settings
    static final String SETTINGS_TITLE = "Configuració de LlarTV";
    static final String SYSTEM_SETTINGS = "Configuració del sistema";
    static final String CLOCK = "Relotge";
    static final String CLOCK_SYSTEM = "Segueix el sistema";
    static final String CLOCK_24 = "24 h";
    static final String CLOCK_12 = "12 h";
    static final String WEATHER_HOME = "Temps a la pantalla";
    static final String WEATHER_SCREEN = "Temps al protector";
    static final String NEWS_HOME = "Notícies a la pantalla";
    static final String NEVER = "Mai";
    static final String MORNING_ONLY = "Només matí";
    static final String ALL_DAY = "Tot el dia";
    static final String CANT_SAVE = "No s'ha pogut desar";

    /** Same hour thresholds the English greeting used. */
    static String greeting(int hour) {
        return hour < 5 ? "Bona nit" : hour < 12 ? "Bon dia" : hour < 17 ? "Bona tarda" : hour < 22 ? "Bon vespre" : "Bona nit";
    }

    static String format(String pattern, Date d) { return new SimpleDateFormat(pattern, LOCALE).format(d); }

    /** "diumenge, 25 d’octubre" */
    static String date(Date d) { return format("EEEE, d MMMM", d); }

    static String countdown(long mins) {
        if (mins < 60) return "d'aquí a " + mins + " min";
        if (mins < 48 * 60) return "d'aquí a " + (mins / 60) + " h " + (mins % 60) + " min";
        return "d'aquí a " + (mins / (24 * 60)) + " dies";
    }

    static String kickedOff(long mins) { return "ha començat fa " + mins + " min"; }
}
