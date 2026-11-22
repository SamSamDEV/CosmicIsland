package fr.cosmicisland.admin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cosmicisland.Tr;
import fr.cosmicisland.admin.AdminNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.locale.Language;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fenetre d'administration (popup en jeu) : arbre de menus a gauche, contenu a droite (boutons, formulaires,
 * listes), bandeau de retour des commandes en bas. Toutes les actions sont des commandes /ccis envoyees au serveur,
 * qui verifie les permissions comme d'habitude.
 */
public class AdminScreen extends Screen {
    private static int offX = 0, offY = 0;

    private static final int TITLE_H = 22, LEFT_W = 118, STATUS_H = 16, PAD = 8;
    private static final int BG = 0xF0181C24, TITLE_BG = 0xFF262D3B, LEFT_BG = 0xFF131720, GOLD = 0xFFFFD966;
    private static final int TEXT = 0xFFE6E6E6, DIM = 0xFF9AA3B5;

    private final List<AdminModel.Cat> cats = AdminModel.load();
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, TextField> fields = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> modalHits = new ArrayList<>();
    private String pageId;
    private TextField focus;
    private int treeScroll, scroll, contentH, treeH, refresh = 40;
    private boolean dragging;
    private Modal modal;

    // geometrie courante (recalculee a chaque image)
    private int wx, wy, ww, wh, bodyTop, bodyBottom, cx1, cx2, cy1, cy2;

    public AdminScreen() {
        super(Component.literal("Cosmic Island"));
        if (!cats.isEmpty()) {
            AdminModel.Cat first = cats.get(0);
            expanded.add(first.id);
            if (!first.pages.isEmpty()) {
                pageId = first.pages.get(0).id;
            }
        }
    }

    // ------------------------------------------------------------------ types internes

    private record Hit(int x, int y, int w, int h, int clipTop, int clipBottom, Runnable action) {
        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipTop && my < clipBottom;
        }
    }

    private static final class TextField {
        String value = "";
        boolean numeric;
        Runnable onEnter;
    }

    private static final class Modal {
        boolean confirm;
        String cmd;
        TextField target;
        List<String> options = new ArrayList<>();
        TextField search = new TextField();
        int scroll;
    }

    // ------------------------------------------------------------------ outils

    private Minecraft mc() {
        return Minecraft.getInstance();
    }

    private JsonObject state() {
        return AdminClient.state;
    }

    private int level() {
        return state().has("level") ? state().get("level").getAsInt() : 0;
    }

    private String t(String key, Object... args) {
        return Tr.s("gui." + key, args);
    }

    private TextField field(String key, boolean numeric) {
        return fields.computeIfAbsent(key, k -> {
            TextField f = new TextField();
            f.numeric = numeric;
            return f;
        });
    }

    private List<String> options(AdminModel.Field f) {
        List<String> out = new ArrayList<>(f.values);
        switch (f.src) {
            case "bool" -> out.addAll(List.of("true", "false"));
            case "phases" -> out.addAll(List.of("pre_merge", "post_merge", "finale", "ended"));
            case "steps" -> out.addAll(List.of("camp", "reward", "immunity", "council"));
            case "roles" -> out.addAll(List.of("participant", "spectateur", "organisateur"));
            case "items" -> out.addAll(List.of("pot", "canteen", "rice", "shell", "lighter", "campfire"));
            case "colors" -> out.addAll(List.of("red", "blue", "green", "yellow", "gold", "aqua", "dark_red", "dark_green",
                    "dark_aqua", "dark_blue", "dark_purple", "light_purple", "white", "gray", "dark_gray", "black"));
            case "games" -> addAll(out, "games");
            case "langs" -> addAll(out, "langs");
            case "archives" -> addAll(out, "archives");
            case "teams" -> {
                for (JsonElement e : array("teams")) {
                    out.add(e.getAsJsonObject().get("id").getAsString());
                }
            }
            case "spawns" -> {
                for (JsonElement e : array("teams")) {
                    out.add(e.getAsJsonObject().get("id").getAsString());
                }
                out.addAll(List.of("conseil", "elimination", "jury"));
            }
            case "players" -> {
                for (JsonElement e : array("players")) {
                    out.add(e.getAsJsonObject().get("n").getAsString());
                }
            }
            default -> {
            }
        }
        return out;
    }

    private void addAll(List<String> out, String key) {
        for (JsonElement e : array(key)) {
            out.add(e.getAsString());
        }
    }

    private JsonArray array(String key) {
        return state().has(key) && state().get(key).isJsonArray() ? state().getAsJsonArray(key) : new JsonArray();
    }

    private AdminModel.Page currentPage() {
        for (AdminModel.Cat c : cats) {
            for (AdminModel.Page p : c.pages) {
                if (p.id.equals(pageId)) {
                    return p;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ commandes

    private void send(String cmd) {
        if (mc().player == null) {
            return;
        }
        String line = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        mc().player.connection.sendCommand(line);
        AdminClient.setFeedback(Component.literal(t("sent", "/" + line)).withStyle(ChatFormatting.GRAY));
        refresh = 8;
    }

    private void run(String cmd, boolean confirm, boolean admin) {
        if (admin && level() < 2) {
            AdminClient.setFeedback(Component.literal(t("err.admin")).withStyle(ChatFormatting.RED));
            return;
        }
        if (confirm) {
            Modal m = new Modal();
            m.confirm = true;
            m.cmd = cmd;
            modal = m;
            focus = null;
            return;
        }
        send(cmd);
    }

    private void runForm(AdminModel.Widget w, int index) {
        if (w.admin && level() < 2) {
            AdminClient.setFeedback(Component.literal(t("err.admin")).withStyle(ChatFormatting.RED));
            return;
        }
        String cmd = w.cmd;
        for (AdminModel.Field f : w.fields) {
            String v = field(pageId + "#" + index + "#" + f.name, false).value.trim();
            if (v.isEmpty()) {
                AdminClient.setFeedback(Component.literal(t("err.fill", t("field." + f.name))).withStyle(ChatFormatting.RED));
                return;
            }
            if (f.kind.equals("int") || f.kind.equals("dbl")) {
                try {
                    double d = Double.parseDouble(v);
                    if (d < f.min || d > f.max) {
                        AdminClient.setFeedback(Component.literal(t("err.fill", t("field." + f.name)
                                + " (" + (long) f.min + " - " + (long) f.max + ")")).withStyle(ChatFormatting.RED));
                        return;
                    }
                } catch (NumberFormatException e) {
                    AdminClient.setFeedback(Component.literal(t("err.fill", t("field." + f.name))).withStyle(ChatFormatting.RED));
                    return;
                }
            }
            if (!f.kind.equals("greedy") && v.contains(" ")) {
                v = v.replace(' ', '_');
            }
            cmd = cmd.replace("{" + f.name + "}", v);
        }
        run(cmd, w.confirm, false);
    }

    // ------------------------------------------------------------------ dessin

    private void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean hover, boolean danger, boolean primary, boolean disabled) {
        int fill = disabled ? 0xFF262A33 : danger ? (hover ? 0xFF8A3A3A : 0xFF6B2B2B)
                : primary ? (hover ? 0xFFB08A28 : 0xFF8A6D1F) : (hover ? 0xFF4A5878 : 0xFF38425A);
        box(g, x, y, w, h, fill, 0xFF0E1117);
        int color = disabled ? 0xFF666C78 : 0xFFFFFFFF;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    private void drawField(GuiGraphics g, TextField f, int x, int y, int w, String placeholder, boolean choice, boolean isFocus) {
        box(g, x, y, w, 16, 0xFF0E1117, isFocus ? GOLD : 0xFF3A4458);
        String shown = f.value;
        int maxW = w - (choice ? 16 : 8);
        if (shown.isEmpty() && !isFocus) {
            g.drawString(font, font.plainSubstrByWidth(placeholder, maxW), x + 4, y + 4, 0xFF6C7488, false);
        } else {
            while (shown.length() > 1 && font.width(shown) > maxW) {
                shown = shown.substring(1);
            }
            g.drawString(font, shown, x + 4, y + 4, TEXT, false);
            if (isFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = x + 4 + font.width(shown);
                g.fill(cx, y + 3, cx + 1, y + 13, 0xFFFFFFFF);
            }
        }
        if (choice) {
            g.drawString(font, "▾", x + w - 11, y + 4, DIM, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        hits.clear();
        modalHits.clear();
        renderBackground(g);

        ww = Math.min(width - 16, 470);
        wh = Math.min(height - 16, 310);
        offX = Math.max(-(width - ww) / 2, Math.min((width - ww) / 2, offX));
        offY = Math.max(-(height - wh) / 2, Math.min((height - wh) / 2, offY));
        wx = (width - ww) / 2 + offX;
        wy = (height - wh) / 2 + offY;
        bodyTop = wy + TITLE_H;
        bodyBottom = wy + wh - STATUS_H;

        box(g, wx, wy, ww, wh, BG, 0xFF05070B);
        // barre de titre
        g.fill(wx + 1, wy + 1, wx + ww - 1, wy + TITLE_H, TITLE_BG);
        g.fill(wx + 1, wy + TITLE_H, wx + ww - 1, wy + TITLE_H + 1, GOLD);
        g.drawString(font, t("title"), wx + 8, wy + 7, GOLD, true);
        String lvl = level() >= 2 ? t("level.2") : level() == 1 ? t("level.1") : "";
        g.drawString(font, lvl, wx + ww - 26 - font.width(lvl), wy + 7, DIM, false);
        boolean overClose = mx >= wx + ww - 20 && mx < wx + ww - 6 && my >= wy + 4 && my < wy + 18;
        button(g, wx + ww - 20, wy + 4, 14, 14, "x", overClose, true, false, false);
        hits.add(new Hit(wx + ww - 20, wy + 4, 14, 14, 0, height, this::onClose));

        renderTree(g, mx, my);
        renderContent(g, mx, my);

        // bandeau de retour
        g.fill(wx + 1, bodyBottom, wx + ww - 1, wy + wh - 1, 0xFF0E1117);
        if (!AdminClient.feedback.getString().isEmpty()) {
            FormattedCharSequence seq = Language.getInstance().getVisualOrder(font.substrByWidth(AdminClient.feedback, ww - 16));
            g.drawString(font, seq, wx + 8, bodyBottom + 4, 0xFFFFFFFF, false);
        }

        if (modal != null) {
            renderModal(g, mx, my);
        }
    }

    private void renderTree(GuiGraphics g, int mx, int my) {
        g.fill(wx + 1, bodyTop + 1, wx + LEFT_W, bodyBottom, LEFT_BG);
        g.enableScissor(wx + 1, bodyTop + 1, wx + LEFT_W, bodyBottom);
        int y = bodyTop + 4 - treeScroll;
        int top = bodyTop, bottom = bodyBottom;
        for (AdminModel.Cat c : cats) {
            boolean open = expanded.contains(c.id);
            boolean hover = mx >= wx + 1 && mx < wx + LEFT_W && my >= y && my < y + 15 && my >= top && my < bottom && modal == null;
            if (hover) {
                g.fill(wx + 2, y, wx + LEFT_W - 1, y + 15, 0xFF222938);
            }
            g.drawString(font, (open ? "▾ " : "▸ ") + t("cat." + c.id), wx + 6, y + 4, GOLD, false);
            final AdminModel.Cat cat = c;
            hits.add(new Hit(wx + 1, y, LEFT_W - 1, 15, top, bottom, () -> {
                if (!expanded.remove(cat.id)) {
                    expanded.add(cat.id);
                }
            }));
            y += 15;
            if (open) {
                for (AdminModel.Page p : c.pages) {
                    boolean sel = p.id.equals(pageId);
                    boolean ph = mx >= wx + 1 && mx < wx + LEFT_W && my >= y && my < y + 14 && my >= top && my < bottom && modal == null;
                    if (sel) {
                        g.fill(wx + 2, y, wx + LEFT_W - 1, y + 14, 0xFF2F3848);
                        g.fill(wx + 2, y, wx + 4, y + 14, GOLD);
                    } else if (ph) {
                        g.fill(wx + 2, y, wx + LEFT_W - 1, y + 14, 0xFF222938);
                    }
                    g.drawString(font, font.plainSubstrByWidth(t("page." + p.id), LEFT_W - 22), wx + 12, y + 3, sel ? 0xFFFFFFFF : 0xFFC4CAD6, false);
                    final AdminModel.Page page = p;
                    hits.add(new Hit(wx + 1, y, LEFT_W - 1, 14, top, bottom, () -> {
                        pageId = page.id;
                        scroll = 0;
                        focus = null;
                    }));
                    y += 14;
                }
            }
        }
        treeH = y + treeScroll - bodyTop + 4;
        g.disableScissor();
    }

    private void renderContent(GuiGraphics g, int mx, int my) {
        AdminModel.Page page = currentPage();
        cx1 = wx + LEFT_W + PAD;
        cx2 = wx + ww - PAD - 6;
        cy1 = bodyTop + 4;
        cy2 = bodyBottom - 2;
        int viewH = cy2 - cy1;
        scroll = Math.max(0, Math.min(Math.max(0, contentH - viewH), scroll));
        if (page == null) {
            return;
        }
        g.enableScissor(cx1 - 2, cy1, cx2 + 8, cy2);
        int y = cy1 + 2 - scroll;
        int w = cx2 - cx1;
        g.drawString(font, t("page." + page.id), cx1, y, GOLD, true);
        y += 12;
        g.fill(cx1, y, cx2, y + 1, 0xFF3A4458);
        y += 6;
        int index = 0;
        for (AdminModel.Widget wd : page.widgets) {
            y = renderWidget(g, wd, index++, y, w, mx, my);
        }
        contentH = y + scroll - cy1 + 6;
        g.disableScissor();
        // barre de defilement
        if (contentH > viewH) {
            int trackH = viewH;
            int thumbH = Math.max(14, trackH * viewH / contentH);
            int thumbY = cy1 + (trackH - thumbH) * scroll / Math.max(1, contentH - viewH);
            g.fill(cx2 + 3, cy1, cx2 + 6, cy2, 0xFF0E1117);
            g.fill(cx2 + 3, thumbY, cx2 + 6, thumbY + thumbH, 0xFF5A6786);
        }
    }

    private boolean hoverRect(int mx, int my, int x, int y, int w, int h) {
        return modal == null && mx >= x && mx < x + w && my >= y && my < y + h && my >= cy1 && my < cy2;
    }

    private int renderWidget(GuiGraphics g, AdminModel.Widget wd, int index, int y, int w, int mx, int my) {
        if (wd.type.equals("h")) {
            {
                y += 4;
                g.drawString(font, t(wd.key), cx1, y, 0xFFFFFFFF, false);
                g.fill(cx1, y + 10, cx1 + Math.min(w, font.width(t(wd.key)) + 20), y + 11, 0xFF6B5A22);
                return y + 16;
            }
        } else if (wd.type.equals("text")) {
            {
                for (FormattedCharSequence line : font.split(Component.literal(t(wd.key)), w)) {
                    g.drawString(font, line, cx1, y, DIM, false);
                    y += 10;
                }
                return y + 4;
            }
        } else if (wd.type.equals("info")) {
            {
                for (Component line : infoLines(wd.src)) {
                    for (FormattedCharSequence seq : font.split(line, w - 8)) {
                        g.drawString(font, seq, cx1 + 4, y, TEXT, false);
                        y += 10;
                    }
                }
                return y + 6;
            }
        } else if (wd.type.equals("btns")) {
            {
                int x = cx1;
                for (AdminModel.Btn b : wd.buttons) {
                    String label = t(b.key);
                    int bw = font.width(label) + 14;
                    if (x + bw > cx1 + w && x > cx1) {
                        x = cx1;
                        y += 20;
                    }
                    boolean disabled = b.admin && level() < 2;
                    button(g, x, y, bw, 16, label, hoverRect(mx, my, x, y, bw, 16), b.danger, false, disabled);
                    final AdminModel.Btn btn = b;
                    hits.add(new Hit(x, y, bw, 16, cy1, cy2, () -> run(btn.cmd, btn.confirm, btn.admin)));
                    x += bw + 4;
                }
                return y + 22;
            }
        } else if (wd.type.equals("form")) {
            {
                g.drawString(font, t(wd.key), cx1, y, 0xFFC4CAD6, false);
                y += 11;
                int x = cx1;
                for (AdminModel.Field f : wd.fields) {
                    boolean choice = f.kind.equals("choice");
                    int fw = choice ? 100 : f.kind.equals("int") || f.kind.equals("dbl") ? 54 : f.kind.equals("greedy") ? Math.min(190, w - 40) : 92;
                    if (x + fw > cx1 + w && x > cx1) {
                        x = cx1;
                        y += 20;
                    }
                    final TextField tf = field(pageId + "#" + index + "#" + f.name, f.kind.equals("int") || f.kind.equals("dbl"));
                    final int formIndex = index;
                    tf.onEnter = () -> runForm(wd, formIndex);
                    String placeholder = t("field." + f.name);
                    drawField(g, tf, x, y, fw, placeholder, choice, focus == tf);
                    final AdminModel.Field fd = f;
                    if (choice) {
                        hits.add(new Hit(x, y, fw, 16, cy1, cy2, () -> {
                            Modal m = new Modal();
                            m.target = tf;
                            m.options = options(fd);
                            modal = m;
                            focus = m.search;
                        }));
                    } else {
                        hits.add(new Hit(x, y, fw, 16, cy1, cy2, () -> focus = tf));
                    }
                    x += fw + 4;
                }
                if (x + 26 > cx1 + w && x > cx1) {
                    x = cx1;
                    y += 20;
                }
                boolean disabled = wd.admin && level() < 2;
                button(g, x, y, 26, 16, t("run"), hoverRect(mx, my, x, y, 26, 16), wd.danger, true, disabled);
                final int fi = index;
                hits.add(new Hit(x, y, 26, 16, cy1, cy2, () -> runForm(wd, fi)));
                return y + 24;
            }
        }
        return y;
    }

    // ------------------------------------------------------------------ infos dynamiques

    private List<Component> infoLines(String src) {
        List<Component> lines = new ArrayList<>();
        JsonObject st = state();
        switch (src) {
            case "season" -> {
                if (!st.has("season")) {
                    break;
                }
                JsonObject s = st.getAsJsonObject("season");
                lines.add(Component.literal(t("i.day", s.get("day").getAsInt())).withStyle(ChatFormatting.GOLD));
                lines.add(Component.literal(t("i.phase", s.get("phase").getAsString())));
                lines.add(Component.literal(t("i.step", s.get("step").getAsString())));
                lines.add(Component.literal(t("i.auto", yes(s.get("auto").getAsBoolean()))));
                lines.add(Component.literal(t("i.paused", yes(s.get("paused").getAsBoolean()))));
                lines.add(Component.literal(t("i.alive", s.get("alive").getAsInt(), s.get("start").getAsInt())));
                int mergeAt = s.get("mergeAt").getAsInt();
                if (mergeAt > 0) {
                    lines.add(Component.literal(t("i.mergeat", mergeAt, Math.max(0, mergeAt - 3))));
                }
                int finaleAt = s.get("finaleAt").getAsInt();
                if (finaleAt > 0) {
                    lines.add(Component.literal(t("i.finaleat", finaleAt)));
                }
            }
            case "finale" -> {
                if (!st.has("finale")) {
                    break;
                }
                JsonObject f = st.getAsJsonObject("finale");
                lines.add(Component.literal(t("i.fin.stage", f.get("stage").getAsString())).withStyle(ChatFormatting.GOLD));
                lines.add(Component.literal(t("i.fin.jury", f.get("jury").getAsInt(), f.get("votes").getAsInt())));
                List<String> names = new ArrayList<>();
                for (JsonElement e : f.getAsJsonArray("finalists")) {
                    names.add(e.getAsString());
                }
                lines.add(Component.literal(t("i.fin.finalists", names.isEmpty() ? "-" : String.join(" / ", names))));
            }
            case "teams" -> {
                if (st.has("merged") && st.get("merged").getAsBoolean()) {
                    lines.add(Component.literal(t("i.merged")).withStyle(ChatFormatting.GOLD));
                }
                for (JsonElement e : array("teams")) {
                    JsonObject o = e.getAsJsonObject();
                    ChatFormatting color = ChatFormatting.getByName(o.get("color").getAsString());
                    JsonArray members = o.getAsJsonArray("members");
                    lines.add(Component.literal("● " + t("i.team", o.get("name").getAsString(), members.size()))
                            .withStyle(color == null ? ChatFormatting.WHITE : color));
                    List<String> names = new ArrayList<>();
                    for (JsonElement m : members) {
                        names.add(m.getAsString());
                    }
                    if (!names.isEmpty()) {
                        lines.add(Component.literal("   " + String.join(", ", names)).withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            case "players" -> {
                JsonArray players = array("players");
                lines.add(Component.literal(t("i.count", players.size())).withStyle(ChatFormatting.GOLD));
                Map<String, String> teamNames = new HashMap<>();
                for (JsonElement e : array("teams")) {
                    teamNames.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject().get("name").getAsString());
                }
                for (JsonElement e : players) {
                    JsonObject p = e.getAsJsonObject();
                    String team = p.get("t").getAsString();
                    String role = p.get("r").getAsString();
                    boolean on = p.get("on").getAsBoolean();
                    String line = (on ? "● " : "○ ") + t("i.player", p.get("n").getAsString(), t("role." + role),
                            team.isEmpty() ? t("i.noteam") : teamNames.getOrDefault(team, team));
                    lines.add(Component.literal(line).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                }
            }
            default -> {
            }
        }
        return lines;
    }

    private String yes(boolean v) {
        return t(v ? "i.on" : "i.off");
    }

    // ------------------------------------------------------------------ fenetres modales

    private void renderModal(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, height, 0x88000000);
        if (modal.confirm) {
            int bw = 250, bh = 84, bx = (width - bw) / 2, by = (height - bh) / 2;
            box(g, bx, by, bw, bh, BG, GOLD);
            g.drawString(font, t("confirm.q"), bx + 10, by + 10, GOLD, false);
            int ly = by + 24;
            for (FormattedCharSequence line : font.split(Component.literal("/" + modal.cmd), bw - 20)) {
                g.drawString(font, line, bx + 10, ly, TEXT, false);
                ly += 10;
            }
            boolean hy = mx >= bx + 10 && mx < bx + 80 && my >= by + bh - 24 && my < by + bh - 8;
            boolean hn = mx >= bx + bw - 80 && mx < bx + bw - 10 && my >= by + bh - 24 && my < by + bh - 8;
            button(g, bx + 10, by + bh - 24, 70, 16, t("yes"), hy, false, true, false);
            button(g, bx + bw - 80, by + bh - 24, 70, 16, t("no"), hn, false, false, false);
            final String cmd = modal.cmd;
            modalHits.add(new Hit(bx + 10, by + bh - 24, 70, 16, 0, height, () -> {
                modal = null;
                send(cmd);
            }));
            modalHits.add(new Hit(bx + bw - 80, by + bh - 24, 70, 16, 0, height, () -> modal = null));
            return;
        }
        // liste de choix
        List<String> shown = new ArrayList<>();
        String q = modal.search.value.toLowerCase(Locale.ROOT);
        for (String o : modal.options) {
            if (q.isEmpty() || o.toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(o);
            }
        }
        int rows = Math.min(9, Math.max(1, shown.size()));
        int bw = 180, bh = 30 + rows * 13 + 6, bx = (width - bw) / 2, by = (height - bh) / 2;
        box(g, bx, by, bw, bh, BG, GOLD);
        drawField(g, modal.search, bx + 6, by + 6, bw - 12, t("search"), false, focus == modal.search);
        modal.scroll = Math.max(0, Math.min(Math.max(0, shown.size() - rows), modal.scroll));
        if (shown.isEmpty()) {
            g.drawString(font, t("empty"), bx + 10, by + 30, DIM, false);
        }
        for (int i = 0; i < rows && i + modal.scroll < shown.size(); i++) {
            String item = shown.get(i + modal.scroll);
            int iy = by + 26 + i * 13;
            boolean hv = mx >= bx + 6 && mx < bx + bw - 6 && my >= iy && my < iy + 13;
            if (hv) {
                g.fill(bx + 6, iy, bx + bw - 6, iy + 13, 0xFF2F3848);
            }
            g.drawString(font, font.plainSubstrByWidth(item, bw - 24), bx + 10, iy + 3, hv ? 0xFFFFFFFF : TEXT, false);
            final TextField target = modal.target;
            modalHits.add(new Hit(bx + 6, iy, bw - 12, 13, 0, height, () -> {
                target.value = item;
                modal = null;
                focus = null;
            }));
        }
        if (shown.size() > rows) {
            g.drawString(font, "↑↓", bx + bw - 22, by + bh - 12, DIM, false);
        }
        modalBounds = new int[]{bx, by, bw, bh};
    }

    private int[] modalBounds = new int[]{0, 0, 0, 0};

    // ------------------------------------------------------------------ entrees

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (modal != null) {
            for (int i = modalHits.size() - 1; i >= 0; i--) {
                if (modalHits.get(i).inside(mx, my)) {
                    modalHits.get(i).action().run();
                    return true;
                }
            }
            if (!modal.confirm) {
                int[] b = modalBounds;
                if (mx >= b[0] + 6 && mx < b[0] + b[2] - 6 && my >= b[1] + 6 && my < b[1] + 22) {
                    focus = modal.search;
                } else if (!(mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3])) {
                    modal = null;
                    focus = null;
                }
            }
            return true;
        }
        focus = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).inside(mx, my)) {
                hits.get(i).action().run();
                return true;
            }
        }
        if (mx >= wx && mx < wx + ww && my >= wy && my < wy + TITLE_H) {
            dragging = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && modal == null) {
            offX += (int) Math.round(dx);
            offY += (int) Math.round(dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) Math.round(-delta * 14);
        if (modal != null) {
            if (!modal.confirm) {
                modal.scroll += delta > 0 ? -1 : 1;
            }
            return true;
        }
        if (mx < wx + LEFT_W) {
            treeScroll = Math.max(0, Math.min(Math.max(0, treeH - (bodyBottom - bodyTop)), treeScroll + step));
        } else {
            scroll = Math.max(0, Math.min(Math.max(0, contentH - (cy2 - cy1)), scroll + step));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (modal != null) {
            if (key == 256) {
                modal = null;
                focus = null;
                return true;
            }
            if (modal.confirm && (key == 257 || key == 335)) {
                String cmd = modal.cmd;
                modal = null;
                send(cmd);
                return true;
            }
            if (!modal.confirm) {
                if (key == 259 && !modal.search.value.isEmpty()) {
                    modal.search.value = modal.search.value.substring(0, modal.search.value.length() - 1);
                    modal.scroll = 0;
                    return true;
                }
                if (key == 257 || key == 335) {
                    String q = modal.search.value.toLowerCase(Locale.ROOT);
                    for (String o : modal.options) {
                        if (q.isEmpty() || o.toLowerCase(Locale.ROOT).contains(q)) {
                            modal.target.value = o;
                            modal = null;
                            focus = null;
                            return true;
                        }
                    }
                }
            }
            return true;
        }
        if (focus != null) {
            if (key == 256) {
                focus = null;
                return true;
            }
            if (key == 259) {
                if (!focus.value.isEmpty()) {
                    focus.value = focus.value.substring(0, focus.value.length() - 1);
                }
                return true;
            }
            if (key == 257 || key == 335) {
                if (focus.onEnter != null) {
                    focus.onEnter.run();
                }
                return true;
            }
            if (Screen.isPaste(key)) {
                String clip = mc().keyboardHandler.getClipboard();
                if (clip != null) {
                    for (char c : clip.toCharArray()) {
                        accept(focus, c);
                    }
                }
                return true;
            }
            return true;
        }
        if (AdminKeys.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void accept(TextField f, char c) {
        if (c < 32 || c == 127 || f.value.length() >= 120) {
            return;
        }
        if (f.numeric && !(Character.isDigit(c) || c == '-' || c == '.')) {
            return;
        }
        f.value += c;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (modal != null && !modal.confirm) {
            accept(modal.search, c);
            modal.scroll = 0;
            return true;
        }
        if (focus != null) {
            accept(focus, c);
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public void tick() {
        if (--refresh <= 0) {
            AdminNetwork.CHANNEL.sendToServer(new AdminNetwork.RequestPacket(false));
            refresh = 40;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
