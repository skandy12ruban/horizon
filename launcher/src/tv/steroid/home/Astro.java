package tv.steroid.home;

/**
 * Where the sun and moon are, and how much of the moon is lit, for a place and time.
 * Port of the formulas in SunCalc by Vladimir Agafonkin (BSD-2-Clause), accurate to well
 * under a degree, which is plenty for a picture of the sky.
 */
final class Astro {
    static final double RAD = Math.PI / 180, DAY_MS = 86400000.0, J1970 = 2440588, J2000 = 2451545,
        E = RAD * 23.4397;

    /** Result for one body. Azimuth is measured from south, positive towards the west (radians). */
    static final class Pos { double azimuth, altitude, distance, parallactic; }

    /** Moon phase: lit fraction 0..1, phase 0 new → 0.5 full → 1, and the bright limb's angle (radians). */
    static final class Light { double fraction, phase, angle; }

    static double days(long ms) { return ms / DAY_MS - 0.5 + J1970 - J2000; }
    static double ra(double l, double b) { return Math.atan2(Math.sin(l) * Math.cos(E) - Math.tan(b) * Math.sin(E), Math.cos(l)); }
    static double dec(double l, double b) { return Math.asin(Math.sin(b) * Math.cos(E) + Math.cos(b) * Math.sin(E) * Math.sin(l)); }
    static double azimuth(double h, double phi, double d) { return Math.atan2(Math.sin(h), Math.cos(h) * Math.sin(phi) - Math.tan(d) * Math.cos(phi)); }
    static double altitude(double h, double phi, double d) { return Math.asin(Math.sin(phi) * Math.sin(d) + Math.cos(phi) * Math.cos(d) * Math.cos(h)); }
    static double sidereal(double d, double lw) { return RAD * (280.16 + 360.9856235 * d) - lw; }
    static double refraction(double h) { if (h < 0) h = 0; return 0.0002967 / Math.tan(h + 0.00312536 / (h + 0.08901179)); }

    /** {declination, right ascension} of the sun. */
    static double[] sunCoords(double d) {
        double m = RAD * (357.5291 + 0.98560028 * d);
        double c = RAD * (1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m));
        double l = m + c + RAD * 102.9372 + Math.PI;
        return new double[]{dec(l, 0), ra(l, 0)};
    }

    /** {declination, right ascension, distance km} of the moon. */
    static double[] moonCoords(double d) {
        double L = RAD * (218.316 + 13.176396 * d), M = RAD * (134.963 + 13.064993 * d), F = RAD * (93.272 + 13.229350 * d);
        double l = L + RAD * 6.289 * Math.sin(M), b = RAD * 5.128 * Math.sin(F), dist = 385001 - 20905 * Math.cos(M);
        return new double[]{dec(l, b), ra(l, b), dist};
    }

    /** The sun's ecliptic longitude in radians, 0..2π (0 = March equinox, π/2 = June solstice…). */
    static double sunLongitude(long ms) {
        double d = days(ms), m = RAD * (357.5291 + 0.98560028 * d);
        double c = RAD * (1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m));
        double l = (m + c + RAD * 102.9372 + Math.PI) % (2 * Math.PI);
        return l < 0 ? l + 2 * Math.PI : l;
    }

    static Pos sun(long ms, double lat, double lng) {
        double lw = RAD * -lng, phi = RAD * lat, d = days(ms);
        double[] c = sunCoords(d);
        double h = sidereal(d, lw) - c[1];
        Pos p = new Pos();
        p.azimuth = azimuth(h, phi, c[0]);
        p.altitude = altitude(h, phi, c[0]);
        return p;
    }

    static Pos moon(long ms, double lat, double lng) {
        double lw = RAD * -lng, phi = RAD * lat, d = days(ms);
        double[] c = moonCoords(d);
        double h = sidereal(d, lw) - c[1];
        Pos p = new Pos();
        p.azimuth = azimuth(h, phi, c[0]);
        double alt = altitude(h, phi, c[0]);
        p.altitude = alt + refraction(alt);
        p.distance = c[2];
        p.parallactic = Math.atan2(Math.sin(h), Math.tan(phi) * Math.cos(c[0]) - Math.sin(c[0]) * Math.cos(h));
        return p;
    }

    static Light moonLight(long ms) {
        double d = days(ms), sdist = 149598000;
        double[] s = sunCoords(d), m = moonCoords(d);
        double phi = Math.acos(Math.sin(s[0]) * Math.sin(m[0]) + Math.cos(s[0]) * Math.cos(m[0]) * Math.cos(s[1] - m[1]));
        double inc = Math.atan2(sdist * Math.sin(phi), m[2] - sdist * Math.cos(phi));
        double angle = Math.atan2(Math.cos(s[0]) * Math.sin(s[1] - m[1]),
            Math.sin(s[0]) * Math.cos(m[0]) - Math.cos(s[0]) * Math.sin(m[0]) * Math.cos(s[1] - m[1]));
        Light l = new Light();
        l.fraction = (1 + Math.cos(inc)) / 2;
        l.phase = 0.5 + 0.5 * inc * (angle < 0 ? -1 : 1) / Math.PI;
        l.angle = angle;
        return l;
    }
}
