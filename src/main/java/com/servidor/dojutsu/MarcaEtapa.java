package com.servidor.dojutsu;

import org.bukkit.potion.PotionEffect;

import java.util.List;

/** Una etapa de la marca (1, 2, ...). */
record MarcaEtapa(
        int numero,
        double desgastePorSegundo,
        double danoRecibido,
        boolean rayoVisual,
        String glifo,
        List<PotionEffect> efectos) {
}
