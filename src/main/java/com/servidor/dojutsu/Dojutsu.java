package com.servidor.dojutsu;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffect;

import java.util.List;

/** Definición de un dojutsu leída desde config.yml. */
record Dojutsu(
        String id,
        String nombre,
        String glifoGrande,
        String glifoIcono,
        int cinematicaTicks,
        Sound sonido,
        Particle particula,
        int duracionMax,
        int enfriamiento,
        double danoRecibido,
        double resaltarRadio,
        List<PotionEffect> efectos) {
}
