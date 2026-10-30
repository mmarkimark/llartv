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
    static final String CLOCK = "Rellotge";
    static final String CLOCK_SYSTEM = "Segueix el sistema";
    static final String CLOCK_24 = "24 h";
    static final String CLOCK_12 = "12 h";
    static final String WEATHER_HOME = "Temps a la pantalla";
    static final String WEATHER_SCREEN = "Temps al protector";
    static final String NEWS_HOME = "Notícies a la pantalla";
    static final String NEWS_SOURCE = "Font de notícies";
    static final String NEWS_SOURCE_AUTO = "Automàtic";
    static final String NEWS_SOURCE_3CAT = "3Cat";
    static final String NEWS_SOURCE_MUNDO = "BBC News Mundo";
    static final String NEWS_SOURCE_WORLD = "BBC News World";
    static final String NEWS_SOURCE_CUSTOM = "Personalitzada";
    static final String MUSIC_SCREEN = "Música al protector";
    static final String WALLPAPER = "Fons de pantalla";
    static final String WALLPAPER_BING = "Fotos de Bing";
    static final String WALLPAPER_PLAIN = "Fons llis";
    static final String WALLPAPER_CUSTOM = "Imatge pròpia";
    static final String WALLPAPER_CUSTOM_TIP = "La imatge pròpia té prioritat: canvia wallpaper amb scripts/config.sh per usar Bing o un fons llis";
    static final String PHOTO_CHANGE = "Canvi de foto";
    static final String PHOTO_FIXED = "Fixa (la més nova)";
    static final String PHOTO_HOURLY = "Cada hora";
    static final String NIGHT_MODE = "Mode nit";
    static final String NIGHT_OFF = "Desactivat";
    static final String ON = "Sí";
    static final String OFF = "No";
    /** The menu labels, in the same order as NewsSource.OPTIONS (a test pins the pairing). */
    static final String[] NEWS_SOURCE_LABELS = {NEWS_SOURCE_AUTO, NEWS_SOURCE_3CAT, NEWS_SOURCE_MUNDO, NEWS_SOURCE_WORLD};
    static final String NEWS_CUSTOM_TIP = "El feed personalitzat té prioritat: elimina news_rss de home.conf per usar un predefinit.";
    static final String NEVER = "Mai";
    static final String MORNING_ONLY = "Només matí";
    static final String ALL_DAY = "Tot el dia";
    static final String CANT_SAVE = "No s'ha pogut desar";

    /** The Catalan forms: "bon dia" until 14:00, then "bon vespre"; "bona nit" in the small hours. */
    static String greeting(int hour) {
        return hour < 5 ? "Bona nit" : hour < 14 ? "Bon dia" : hour < 22 ? "Bon vespre" : "Bona nit";
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
