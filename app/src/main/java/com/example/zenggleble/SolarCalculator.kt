package com.example.zenggeble

import kotlin.math.*

object SolarCalculator {
    const val LAT = 25.60
    const val LON = -100.27
    const val TZ_HORAS = -6

    fun minutosSol(diaDelAnio: Int, amanecer: Boolean): Int {
        val r = Math.PI / 180.0
        val lngHour = LON / 15.0
        val t = diaDelAnio + (((if (amanecer) 6.0 else 18.0) - lngHour) / 24.0)
        val m = (0.9856 * t) - 3.289
        var l = m + 1.916 * sin(m * r) + 0.020 * sin(2 * m * r) + 282.634
        l = (l % 360.0 + 360.0) % 360.0

        var ra = atan(0.91764 * tan(l * r)) / r
        ra = (ra % 360.0 + 360.0) % 360.0
        ra += (floor(l / 90.0) * 90.0) - (floor(ra / 90.0) * 90.0)
        ra /= 15.0

        val sinDec = 0.39782 * sin(l * r)
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(90.833 * r) - sinDec * sin(LAT * r)) / (cosDec * cos(LAT * r))
        if (cosH > 1.0 || cosH < -1.0) return -1

        var h = acos(cosH) / r
        if (amanecer) h = 360.0 - h
        h /= 15.0

        val tVal = h + ra - (0.06571 * t) - 6.622
        val ut = (tVal - lngHour + 48.0) % 24.0
        val local = (ut + TZ_HORAS + 48.0) % 24.0
        return (local * 60.0).toInt()
    }
}