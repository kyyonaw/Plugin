package com.servidor.dojutsu;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DojutsuPlugin extends JavaPlugin implements Listener {

    /** Fuente del resource pack (assets/dojutsu/font/ojos.json). */
    static final String FONT = "dojutsu:ojos";

    private final Map<String, Dojutsu> dojutsus = new LinkedHashMap<>();
    private final Map<Integer, MarcaEtapa> etapas = new HashMap<>();
    private final Map<String, Long> enfriamientos = new HashMap<>();
    private final Map<UUID, Long> inicioActivo = new HashMap<>();
    private final Set<UUID> enCinematica = new HashSet<>();

    private PlayerData datos;
    private String prefijo = "";
    private String glifoVineta = "";
    private Sound sonidoMarca;
    private Particle particulaMarca;
    private int cinematicaMarca = 40;
    private double recuperacion = 0.15;
    private double bloqueoDesgaste = 90;
    private int penalizacionSeg = 20;
    private boolean resetearAlMorir = true;
    private int tickContador = 0;

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    public void onEnable() {
        saveDefaultConfig();
        datos = new PlayerData(this);
        cargarConfig();

        Comandos comandos = new Comandos(this);
        getCommand("dojutsu").setExecutor(comandos);
        getCommand("dojutsu").setTabCompleter(comandos);
        getCommand("marca").setExecutor(comandos);
        getCommand("marca").setTabCompleter(comandos);
        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                tickContador++;
                boolean segundo = tickContador % 2 == 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        tick(p, segundo);
                    } catch (Exception ex) {
                        getLogger().warning("Error en tick de " + p.getName() + ": " + ex);
                    }
                }
            }
        }.runTaskTimer(this, 10L, 10L);
    }

    void cargarConfig() {
        reloadConfig();
        dojutsus.clear();
        etapas.clear();

        prefijo = getConfig().getString("prefijo", "§8[§cDojutsu§8] §7");
        resetearAlMorir = getConfig().getBoolean("resetear-desgaste-al-morir", true);

        ConfigurationSection ds = getConfig().getConfigurationSection("dojutsus");
        if (ds != null) {
            for (String id : ds.getKeys(false)) {
                ConfigurationSection c = ds.getConfigurationSection(id);
                if (c == null) continue;
                dojutsus.put(id, new Dojutsu(
                        id,
                        c.getString("nombre", id),
                        c.getString("glifo-grande", ""),
                        c.getString("glifo-icono", ""),
                        c.getInt("cinematica-ticks", 30),
                        sonido(c.getString("sonido")),
                        particula(c.getString("particula")),
                        c.getInt("duracion-max", 0),
                        c.getInt("enfriamiento", 0),
                        c.getDouble("dano-recibido", 1.0),
                        c.getDouble("resaltar-radio", 0),
                        parseEfectos(c.getStringList("efectos"))));
            }
        }

        ConfigurationSection m = getConfig().getConfigurationSection("marca");
        if (m != null) {
            glifoVineta = m.getString("vineta", "");
            sonidoMarca = sonido(m.getString("sonido"));
            particulaMarca = particula(m.getString("particula"));
            cinematicaMarca = m.getInt("cinematica-ticks", 40);
            recuperacion = m.getDouble("recuperacion-por-segundo", 0.15);
            bloqueoDesgaste = m.getDouble("bloqueo-desgaste", 90);
            penalizacionSeg = m.getInt("penalizacion-segundos", 20);
            ConfigurationSection es = m.getConfigurationSection("etapas");
            if (es != null) {
                for (String k : es.getKeys(false)) {
                    int n;
                    try {
                        n = Integer.parseInt(k);
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    ConfigurationSection c = es.getConfigurationSection(k);
                    if (c == null) continue;
                    etapas.put(n, new MarcaEtapa(
                            n,
                            c.getDouble("desgaste-por-segundo", 0.5),
                            c.getDouble("dano-recibido", 1.0),
                            c.getBoolean("rayo-visual", false),
                            m.getString("glifo-etapa." + k, ""),
                            parseEfectos(c.getStringList("efectos"))));
                }
            }
        }
    }

    // ------------------------------------------------------------------ utilidades de config

    private static Sound sonido(String nombre) {
        if (nombre == null) return null;
        try {
            return Sound.valueOf(nombre.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Particle particula(String nombre) {
        if (nombre == null) return null;
        try {
            return Particle.valueOf(nombre.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static PotionEffectType tipoEfecto(String nombre) {
        String n = nombre.trim().toUpperCase();
        switch (n) {
            case "STRENGTH": n = "INCREASE_DAMAGE"; break;
            case "SLOWNESS": n = "SLOW"; break;
            case "RESISTANCE": n = "DAMAGE_RESISTANCE"; break;
            case "HASTE": n = "FAST_DIGGING"; break;
            case "MINING_FATIGUE": n = "SLOW_DIGGING"; break;
            case "JUMP_BOOST": n = "JUMP"; break;
            case "NAUSEA": n = "CONFUSION"; break;
            case "INSTANT_HEALTH": n = "HEAL"; break;
            default: break;
        }
        return PotionEffectType.getByName(n);
    }

    private List<PotionEffect> parseEfectos(List<String> lista) {
        List<PotionEffect> out = new ArrayList<>();
        for (String s : lista) {
            String[] partes = s.split(":");
            PotionEffectType t = tipoEfecto(partes[0]);
            if (t == null) {
                getLogger().warning("Efecto desconocido en config: " + s);
                continue;
            }
            int amp = 0;
            if (partes.length > 1) {
                try {
                    amp = Integer.parseInt(partes[1].trim());
                } catch (NumberFormatException ignored) {
                    // se queda en 0
                }
            }
            out.add(new PotionEffect(t, 60, amp, true, false, false));
        }
        return out;
    }

    // ------------------------------------------------------------------ acceso para Comandos

    PlayerData datos() {
        return datos;
    }

    Collection<String> idsDojutsu() {
        return dojutsus.keySet();
    }

    Dojutsu dojutsu(String id) {
        return id == null ? null : dojutsus.get(id.toLowerCase());
    }

    int etapasTotales() {
        return etapas.size();
    }

    void msg(CommandSender s, String texto) {
        s.sendMessage(prefijo + texto);
    }

    // ------------------------------------------------------------------ dojutsus

    boolean activarDojutsu(Player p, Dojutsu d) {
        UUID u = p.getUniqueId();
        if (enCinematica.contains(u)) return false;
        if (!datos.posee(p, d.id()) && !p.hasPermission("dojutsu.admin")) {
            msg(p, "§cNo posees el " + d.nombre() + ".");
            return false;
        }
        long hasta = enfriamientos.getOrDefault(u + ":" + d.id(), 0L);
        long ahora = System.currentTimeMillis();
        if (hasta > ahora) {
            msg(p, "§cTus ojos aún descansan: " + ((hasta - ahora) / 1000 + 1) + "s.");
            return false;
        }
        String actual = datos.activo(p);
        if (actual != null) {
            if (actual.equals(d.id())) return false;
            desactivarDojutsu(p, false);
        }

        datos.setActivo(p, d.id());
        datos.setSeleccionado(p, d.id());
        inicioActivo.put(u, ahora);

        iniciarCinematica(p, d.cinematicaTicks(), d.glifoGrande(), d.sonido(), d.particula());
        msg(p, "§c" + d.nombre() + " §7activado.");
        return true;
    }

    void desactivarDojutsu(Player p, boolean agotado) {
        String id = datos.activo(p);
        if (id == null) return;
        Dojutsu d = dojutsus.get(id);
        datos.setActivo(p, null);
        inicioActivo.remove(p.getUniqueId());
        if (d == null) return;
        for (PotionEffect e : d.efectos()) {
            p.removePotionEffect(e.getType());
        }
        long cd = d.enfriamiento() * 1000L;
        if (cd > 0) {
            enfriamientos.put(p.getUniqueId() + ":" + id, System.currentTimeMillis() + cd);
        }
        msg(p, agotado ? "§cTus ojos se agotaron: §7" + d.nombre() + " desactivado." : "§7" + d.nombre() + " desactivado.");
    }

    // ------------------------------------------------------------------ marca

    boolean activarMarca(Player p, int etapa) {
        UUID u = p.getUniqueId();
        if (enCinematica.contains(u)) return false;
        int max = datos.etapaMax(p);
        if (max < 1) {
            msg(p, "§cNo tienes la marca.");
            return false;
        }
        if (etapa > max) etapa = max;
        MarcaEtapa nueva = etapas.get(etapa);
        if (nueva == null) {
            msg(p, "§cEsa etapa no existe.");
            return false;
        }
        if (datos.desgaste(p) >= bloqueoDesgaste) {
            msg(p, "§cEl desgaste es demasiado alto (" + Math.round(datos.desgaste(p)) + "%).");
            return false;
        }
        int actual = datos.marca(p);
        if (actual == etapa) return false;
        if (actual > 0) quitarEfectosMarca(p, actual);

        datos.setMarca(p, etapa);
        iniciarCinematica(p, cinematicaMarca, glifoVineta, sonidoMarca, particulaMarca);
        if (nueva.rayoVisual()) {
            p.getWorld().strikeLightningEffect(p.getLocation());
        }
        msg(p, "§5Marca §7etapa " + etapa + " activada.");
        return true;
    }

    void desactivarMarca(Player p) {
        int actual = datos.marca(p);
        if (actual == 0) return;
        quitarEfectosMarca(p, actual);
        datos.setMarca(p, 0);
        msg(p, "§7Marca desactivada.");
    }

    private void quitarEfectosMarca(Player p, int etapa) {
        MarcaEtapa e = etapas.get(etapa);
        if (e == null) return;
        for (PotionEffect pe : e.efectos()) {
            p.removePotionEffect(pe.getType());
        }
    }

    // ------------------------------------------------------------------ cinemática

    /** Congela y acerca la cámara (lentitud = zoom), muestra el overlay del glifo y reproduce sonido/partículas. */
    private void iniciarCinematica(Player p, int ticks, String glifo, Sound sonido, Particle particula) {
        final UUID u = p.getUniqueId();
        enCinematica.add(u);

        PotionEffectType lento = PotionEffectType.getByName("SLOW");
        PotionEffectType salto = PotionEffectType.getByName("JUMP");
        if (lento != null) p.addPotionEffect(new PotionEffect(lento, ticks, 6, true, false, false));
        if (salto != null) p.addPotionEffect(new PotionEffect(salto, ticks, 128, true, false, false));

        if (sonido != null) p.getWorld().playSound(p.getLocation(), sonido, 1.0f, 1.0f);
        if (particula != null) {
            Location ojos = p.getEyeLocation();
            p.getWorld().spawnParticle(particula, ojos, 40, 0.4, 0.4, 0.4, 0.02);
        }
        if (glifo != null && !glifo.isEmpty()) {
            int entrada = Math.min(6, ticks / 4);
            int salida = Math.min(10, ticks / 3);
            int medio = Math.max(1, ticks - entrada - salida);
            overlay(p, glifo, entrada, medio, salida);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                enCinematica.remove(u);
            }
        }.runTaskLater(this, ticks);
    }

    /** Muestra un glifo del resource pack a pantalla casi completa usando /title con fuente propia. */
    private void overlay(Player p, String glifo, int entrada, int medio, int salida) {
        String objetivo = p.getUniqueId().toString();
        String json = "{\"text\":\"" + glifo.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"font\":\"" + FONT + "\"}";
        comando("title " + objetivo + " times " + entrada + " " + medio + " " + salida);
        comando("title " + objetivo + " subtitle {\"text\":\"\"}");
        comando("title " + objetivo + " title " + json);
    }

    private void comando(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    // ------------------------------------------------------------------ bucle principal

    private void tick(Player p, boolean segundo) {
        UUID u = p.getUniqueId();
        if (enCinematica.contains(u)) return;

        // --- dojutsu activo
        String id = datos.activo(p);
        if (id != null) {
            Dojutsu d = dojutsus.get(id);
            if (d == null) {
                datos.setActivo(p, null);
            } else {
                long ahora = System.currentTimeMillis();
                long activoSeg = (ahora - inicioActivo.computeIfAbsent(u, k -> ahora)) / 1000;
                if (d.duracionMax() > 0 && activoSeg >= d.duracionMax()) {
                    desactivarDojutsu(p, true);
                } else {
                    for (PotionEffect e : d.efectos()) p.addPotionEffect(e);
                    if (segundo && d.resaltarRadio() > 0) resaltarCercanos(p, d.resaltarRadio());
                }
            }
        }

        // --- marca
        int etapa = datos.marca(p);
        double desgaste = datos.desgaste(p);
        if (etapa > 0) {
            MarcaEtapa e = etapas.get(etapa);
            if (e == null) {
                datos.setMarca(p, 0);
            } else {
                for (PotionEffect pe : e.efectos()) p.addPotionEffect(pe);
                if (segundo) {
                    desgaste += e.desgastePorSegundo();
                    if (desgaste >= 100) {
                        datos.setDesgaste(p, 100);
                        agotamiento(p);
                    } else {
                        datos.setDesgaste(p, desgaste);
                    }
                }
            }
        } else if (segundo && desgaste > 0) {
            datos.setDesgaste(p, desgaste - recuperacion);
        }

        hud(p);
    }

    private void resaltarCercanos(Player p, double radio) {
        PotionEffectType brillo = PotionEffectType.getByName("GLOWING");
        if (brillo == null) return;
        for (Entity en : p.getNearbyEntities(radio, radio, radio)) {
            if (en instanceof LivingEntity le && !(en instanceof ArmorStand)) {
                le.addPotionEffect(new PotionEffect(brillo, 30, 0, true, false, false));
            }
        }
    }

    private void agotamiento(Player p) {
        desactivarMarca(p);
        int ticks = penalizacionSeg * 20;
        String[] castigos = {"WEAKNESS", "SLOW", "DARKNESS"};
        for (String n : castigos) {
            PotionEffectType t = PotionEffectType.getByName(n);
            if (t != null) p.addPotionEffect(new PotionEffect(t, ticks, 1, true, false, true));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.0f, 0.6f);
        msg(p, "§4La marca consumió tu cuerpo. §7Estás agotado " + penalizacionSeg + "s.");
    }

    // ------------------------------------------------------------------ HUD

    private TextComponent glifo(String g) {
        TextComponent t = new TextComponent(g);
        t.setFont(FONT);
        return t;
    }

    private void hud(Player p) {
        String id = datos.activo(p);
        int etapa = datos.marca(p);
        int max = datos.etapaMax(p);
        double desgaste = datos.desgaste(p);
        boolean mostrarMarca = max > 0 && (etapa > 0 || desgaste >= 0.5);
        if (id == null && !mostrarMarca) return;

        TextComponent raiz = new TextComponent("");
        Dojutsu d = id == null ? null : dojutsus.get(id);
        if (d != null) {
            raiz.addExtra(glifo(d.glifoIcono()));
            for (BaseComponent b : TextComponent.fromLegacyText(" §f" + d.nombre())) raiz.addExtra(b);
        }
        if (mostrarMarca) {
            if (d != null) {
                for (BaseComponent b : TextComponent.fromLegacyText("   §8| ")) raiz.addExtra(b);
            }
            MarcaEtapa e = etapas.get(etapa > 0 ? etapa : 1);
            for (BaseComponent b : TextComponent.fromLegacyText("§5Marca ")) raiz.addExtra(b);
            if (e != null) raiz.addExtra(glifo(e.glifo()));
            String estado = etapa > 0 ? "§detapa " + etapa : "§7inactiva";
            String color = desgaste >= 75 ? "§c" : desgaste >= 40 ? "§6" : "§a";
            for (BaseComponent b : TextComponent.fromLegacyText(
                    " §7• " + estado + " §7| desgaste " + color + Math.round(desgaste) + "%")) {
                raiz.addExtra(b);
            }
        }
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, raiz);
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alRecibirDano(EntityDamageEvent ev) {
        if (!(ev.getEntity() instanceof Player p)) return;
        double mult = 1.0;
        String id = datos.activo(p);
        if (id != null) {
            Dojutsu d = dojutsus.get(id);
            if (d != null) mult *= d.danoRecibido();
        }
        int etapa = datos.marca(p);
        if (etapa > 0) {
            MarcaEtapa e = etapas.get(etapa);
            if (e != null) mult *= e.danoRecibido();
        }
        if (mult != 1.0) ev.setDamage(ev.getDamage() * mult);
    }

    /** Shift + F: alterna el dojutsu seleccionado. */
    @EventHandler(ignoreCancelled = true)
    public void alCambiarManos(PlayerSwapHandItemsEvent ev) {
        Player p = ev.getPlayer();
        if (!p.isSneaking() || !p.hasPermission("dojutsu.usar")) return;
        if (datos.activo(p) != null) {
            ev.setCancelled(true);
            desactivarDojutsu(p, false);
            return;
        }
        String sel = datos.seleccionado(p);
        Dojutsu d = dojutsu(sel);
        if (d == null) {
            Set<String> mios = datos.poseidos(p);
            if (!mios.isEmpty()) d = dojutsu(mios.iterator().next());
        }
        if (d != null) {
            ev.setCancelled(true);
            activarDojutsu(p, d);
        }
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent ev) {
        limpiar(ev.getPlayer(), false);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent ev) {
        limpiar(ev.getPlayer(), false);
        enCinematica.remove(ev.getPlayer().getUniqueId());
    }

    @EventHandler
    public void alMorir(PlayerDeathEvent ev) {
        Player p = ev.getEntity();
        limpiar(p, false);
        if (resetearAlMorir) datos.setDesgaste(p, 0);
    }

    /** Deja al jugador sin dojutsu ni marca activos (el desgaste se conserva). */
    private void limpiar(Player p, boolean avisar) {
        String id = datos.activo(p);
        if (id != null) {
            Dojutsu d = dojutsus.get(id);
            if (d != null) for (PotionEffect e : d.efectos()) p.removePotionEffect(e.getType());
            datos.setActivo(p, null);
            inicioActivo.remove(p.getUniqueId());
        }
        int etapa = datos.marca(p);
        if (etapa > 0) {
            quitarEfectosMarca(p, etapa);
            datos.setMarca(p, 0);
        }
        if (avisar) msg(p, "§7Estados reiniciados.");
    }
}
