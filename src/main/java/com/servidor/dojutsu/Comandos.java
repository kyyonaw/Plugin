package com.servidor.dojutsu;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Comandos implements CommandExecutor, TabCompleter {

    private final DojutsuPlugin plugin;

    Comandos(DojutsuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("dojutsu")) return dojutsu(s, a);
        return marca(s, a);
    }

    // ------------------------------------------------------------------ /dojutsu

    private boolean dojutsu(CommandSender s, String[] a) {
        PlayerData datos = plugin.datos();

        if (a.length >= 1) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "recargar" -> {
                    if (!admin(s)) return true;
                    plugin.cargarConfig();
                    plugin.msg(s, "§aConfiguración recargada.");
                    return true;
                }
                case "dar", "quitar" -> {
                    if (!admin(s)) return true;
                    if (a.length < 3) {
                        plugin.msg(s, "§cUso: /dojutsu " + sub + " <jugador> <id>");
                        return true;
                    }
                    Player objetivo = Bukkit.getPlayerExact(a[1]);
                    Dojutsu d = plugin.dojutsu(a[2]);
                    if (objetivo == null || d == null) {
                        plugin.msg(s, "§cJugador o dojutsu no encontrado.");
                        return true;
                    }
                    if (sub.equals("dar")) {
                        datos.dar(objetivo, d.id());
                        if (datos.seleccionado(objetivo) == null) datos.setSeleccionado(objetivo, d.id());
                        plugin.msg(objetivo, "§aHas despertado el §c" + d.nombre() + "§a.");
                    } else {
                        if (d.id().equals(datos.activo(objetivo))) plugin.desactivarDojutsu(objetivo, false);
                        datos.quitar(objetivo, d.id());
                        plugin.msg(objetivo, "§7Has perdido el " + d.nombre() + ".");
                    }
                    plugin.msg(s, "§aListo.");
                    return true;
                }
                default -> { }
            }
        }

        if (!(s instanceof Player p)) {
            plugin.msg(s, "§cSolo jugadores.");
            return true;
        }
        if (!p.hasPermission("dojutsu.usar")) {
            plugin.msg(p, "§cSin permiso.");
            return true;
        }

        if (a.length == 0) {
            if (datos.activo(p) != null) {
                plugin.desactivarDojutsu(p, false);
                return true;
            }
            Dojutsu d = plugin.dojutsu(datos.seleccionado(p));
            if (d == null) {
                plugin.msg(p, "§7Uso: /dojutsu <id> §8| §7Tus dojutsus: §f" + String.join(", ", datos.poseidos(p)));
            } else {
                plugin.activarDojutsu(p, d);
            }
            return true;
        }

        if (a[0].equalsIgnoreCase("lista")) {
            plugin.msg(p, "§7Tus dojutsus: §f" + (datos.poseidos(p).isEmpty() ? "ninguno" : String.join(", ", datos.poseidos(p))));
            return true;
        }

        Dojutsu d = plugin.dojutsu(a[0]);
        if (d == null) {
            plugin.msg(p, "§cDojutsu desconocido. Disponibles: §f" + String.join(", ", plugin.idsDojutsu()));
            return true;
        }
        if (d.id().equals(datos.activo(p))) {
            plugin.desactivarDojutsu(p, false);
        } else {
            plugin.activarDojutsu(p, d);
        }
        return true;
    }

    // ------------------------------------------------------------------ /marca

    private boolean marca(CommandSender s, String[] a) {
        PlayerData datos = plugin.datos();

        if (a.length >= 1) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "dar" -> {
                    if (!admin(s)) return true;
                    if (a.length < 3) {
                        plugin.msg(s, "§cUso: /marca dar <jugador> <etapa>");
                        return true;
                    }
                    Player objetivo = Bukkit.getPlayerExact(a[1]);
                    Integer etapa = entero(a[2]);
                    if (objetivo == null || etapa == null || etapa < 1 || etapa > plugin.etapasTotales()) {
                        plugin.msg(s, "§cJugador o etapa inválidos (1-" + plugin.etapasTotales() + ").");
                        return true;
                    }
                    datos.setEtapaMax(objetivo, etapa);
                    plugin.msg(objetivo, "§5La marca despierta en ti §7(etapa máxima " + etapa + ").");
                    plugin.msg(s, "§aListo.");
                    return true;
                }
                case "quitar" -> {
                    if (!admin(s)) return true;
                    if (a.length < 2) {
                        plugin.msg(s, "§cUso: /marca quitar <jugador>");
                        return true;
                    }
                    Player objetivo = Bukkit.getPlayerExact(a[1]);
                    if (objetivo == null) {
                        plugin.msg(s, "§cJugador no encontrado.");
                        return true;
                    }
                    plugin.desactivarMarca(objetivo);
                    datos.setEtapaMax(objetivo, 0);
                    datos.setDesgaste(objetivo, 0);
                    plugin.msg(s, "§aListo.");
                    return true;
                }
                case "desgaste" -> {
                    if (!admin(s)) return true;
                    if (a.length < 3) {
                        plugin.msg(s, "§cUso: /marca desgaste <jugador> <0-100>");
                        return true;
                    }
                    Player objetivo = Bukkit.getPlayerExact(a[1]);
                    Integer v = entero(a[2]);
                    if (objetivo == null || v == null) {
                        plugin.msg(s, "§cJugador o valor inválido.");
                        return true;
                    }
                    datos.setDesgaste(objetivo, v);
                    plugin.msg(s, "§aListo.");
                    return true;
                }
                default -> { }
            }
        }

        if (!(s instanceof Player p)) {
            plugin.msg(s, "§cSolo jugadores.");
            return true;
        }
        if (!p.hasPermission("dojutsu.usar")) {
            plugin.msg(p, "§cSin permiso.");
            return true;
        }

        if (a.length >= 1 && a[0].equalsIgnoreCase("estado")) {
            plugin.msg(p, "§7Etapa máxima: §f" + datos.etapaMax(p)
                    + " §8| §7activa: §f" + datos.marca(p)
                    + " §8| §7desgaste: §f" + Math.round(datos.desgaste(p)) + "%");
            return true;
        }

        if (a.length >= 1) {
            Integer etapa = entero(a[0]);
            if (etapa == null) {
                plugin.msg(p, "§7Uso: /marca [etapa|estado]");
                return true;
            }
            plugin.activarMarca(p, etapa);
            return true;
        }

        if (datos.marca(p) > 0) {
            plugin.desactivarMarca(p);
        } else {
            plugin.activarMarca(p, datos.etapaMax(p));
        }
        return true;
    }

    // ------------------------------------------------------------------ utilidades

    private boolean admin(CommandSender s) {
        if (s.hasPermission("dojutsu.admin")) return true;
        plugin.msg(s, "§cSin permiso.");
        return false;
    }

    private static Integer entero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        boolean esDoj = cmd.getName().equalsIgnoreCase("dojutsu");
        if (a.length == 1) {
            if (esDoj) {
                out.addAll(plugin.idsDojutsu());
                out.add("lista");
                if (s.hasPermission("dojutsu.admin")) out.addAll(List.of("dar", "quitar", "recargar"));
            } else {
                out.add("estado");
                for (int i = 1; i <= plugin.etapasTotales(); i++) out.add(String.valueOf(i));
                if (s.hasPermission("dojutsu.admin")) out.addAll(List.of("dar", "quitar", "desgaste"));
            }
        } else if (a.length == 2 && s.hasPermission("dojutsu.admin")) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (List.of("dar", "quitar", "desgaste").contains(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        } else if (a.length == 3 && esDoj && s.hasPermission("dojutsu.admin")) {
            out.addAll(plugin.idsDojutsu());
        }
        String actual = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(actual));
        return out;
    }
}
