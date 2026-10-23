package com.servidor.dojutsu;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashSet;
import java.util.Set;

/** Datos persistentes por jugador (PersistentDataContainer, sobreviven reinicios). */
final class PlayerData {

    private final NamespacedKey kPoseidos;
    private final NamespacedKey kActivo;
    private final NamespacedKey kSeleccionado;
    private final NamespacedKey kEtapaMax;
    private final NamespacedKey kMarca;
    private final NamespacedKey kDesgaste;

    PlayerData(Plugin plugin) {
        kPoseidos = new NamespacedKey(plugin, "poseidos");
        kActivo = new NamespacedKey(plugin, "activo");
        kSeleccionado = new NamespacedKey(plugin, "seleccionado");
        kEtapaMax = new NamespacedKey(plugin, "etapa_max");
        kMarca = new NamespacedKey(plugin, "marca");
        kDesgaste = new NamespacedKey(plugin, "desgaste");
    }

    private PersistentDataContainer pdc(Player p) {
        return p.getPersistentDataContainer();
    }

    // ---- dojutsus ----

    Set<String> poseidos(Player p) {
        String raw = pdc(p).getOrDefault(kPoseidos, PersistentDataType.STRING, "");
        Set<String> out = new LinkedHashSet<>();
        for (String s : raw.split(",")) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    boolean posee(Player p, String id) {
        return poseidos(p).contains(id);
    }

    void dar(Player p, String id) {
        Set<String> s = poseidos(p);
        s.add(id);
        pdc(p).set(kPoseidos, PersistentDataType.STRING, String.join(",", s));
    }

    void quitar(Player p, String id) {
        Set<String> s = poseidos(p);
        s.remove(id);
        pdc(p).set(kPoseidos, PersistentDataType.STRING, String.join(",", s));
    }

    /** Dojutsu activo ahora mismo, o null. */
    String activo(Player p) {
        String s = pdc(p).getOrDefault(kActivo, PersistentDataType.STRING, "");
        return s.isEmpty() ? null : s;
    }

    void setActivo(Player p, String id) {
        if (id == null) pdc(p).remove(kActivo);
        else pdc(p).set(kActivo, PersistentDataType.STRING, id);
    }

    /** Dojutsu elegido para el atajo Shift+F. */
    String seleccionado(Player p) {
        String s = pdc(p).getOrDefault(kSeleccionado, PersistentDataType.STRING, "");
        return s.isEmpty() ? null : s;
    }

    void setSeleccionado(Player p, String id) {
        if (id == null) pdc(p).remove(kSeleccionado);
        else pdc(p).set(kSeleccionado, PersistentDataType.STRING, id);
    }

    // ---- marca ----

    /** Etapa más alta desbloqueada (0 = no tiene marca). */
    int etapaMax(Player p) {
        return pdc(p).getOrDefault(kEtapaMax, PersistentDataType.INTEGER, 0);
    }

    void setEtapaMax(Player p, int e) {
        pdc(p).set(kEtapaMax, PersistentDataType.INTEGER, e);
    }

    /** Etapa activa ahora mismo (0 = inactiva). */
    int marca(Player p) {
        return pdc(p).getOrDefault(kMarca, PersistentDataType.INTEGER, 0);
    }

    void setMarca(Player p, int e) {
        pdc(p).set(kMarca, PersistentDataType.INTEGER, e);
    }

    double desgaste(Player p) {
        return pdc(p).getOrDefault(kDesgaste, PersistentDataType.DOUBLE, 0.0);
    }

    void setDesgaste(Player p, double v) {
        pdc(p).set(kDesgaste, PersistentDataType.DOUBLE, Math.max(0.0, Math.min(100.0, v)));
    }
}
