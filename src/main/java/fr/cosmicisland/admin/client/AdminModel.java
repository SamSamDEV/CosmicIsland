package fr.cosmicisland.admin.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cosmicisland.CosmicIsland;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Arbre des menus de la fenetre d'administration, lu dans assets/cosmicisland/admin_ui.json. */
public final class AdminModel {
    public static final class Field {
        public String name, kind, src;
        public List<String> values = new ArrayList<>();
        public double min = Double.NEGATIVE_INFINITY, max = Double.POSITIVE_INFINITY;
    }

    public static final class Btn {
        public String key, cmd;
        public boolean confirm, danger, admin;
    }

    public static final class Widget {
        public String type, key, cmd, src;
        public boolean confirm, danger, admin;
        public List<Btn> buttons = new ArrayList<>();
        public List<Field> fields = new ArrayList<>();
    }

    public static final class Page {
        public String id;
        public List<Widget> widgets = new ArrayList<>();
    }

    public static final class Cat {
        public String id;
        public List<Page> pages = new ArrayList<>();
    }

    public static List<Cat> load() {
        List<Cat> cats = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager()
                    .getResource(new ResourceLocation(CosmicIsland.MODID, "admin_ui.json"));
            if (res.isEmpty()) {
                return cats;
            }
            try (InputStreamReader in = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(in).getAsJsonObject();
                for (JsonElement ce : root.getAsJsonArray("tree")) {
                    JsonObject co = ce.getAsJsonObject();
                    Cat cat = new Cat();
                    cat.id = co.get("id").getAsString();
                    for (JsonElement pe : co.getAsJsonArray("pages")) {
                        JsonObject po = pe.getAsJsonObject();
                        Page page = new Page();
                        page.id = po.get("id").getAsString();
                        for (JsonElement we : po.getAsJsonArray("w")) {
                            page.widgets.add(widget(we.getAsJsonObject()));
                        }
                        cat.pages.add(page);
                    }
                    cats.add(cat);
                }
            }
        } catch (Exception e) {
            CosmicIsland.LOGGER.error("admin_ui.json illisible", e);
        }
        return cats;
    }

    private static boolean flag(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsInt() != 0;
    }

    private static Widget widget(JsonObject o) {
        Widget w = new Widget();
        w.type = o.get("t").getAsString();
        w.key = o.has("l") ? o.get("l").getAsString() : "";
        w.cmd = o.has("c") ? o.get("c").getAsString() : "";
        w.src = o.has("src") ? o.get("src").getAsString() : "";
        w.confirm = flag(o, "x");
        w.danger = flag(o, "d");
        w.admin = flag(o, "a");
        if (o.has("b")) {
            for (JsonElement be : o.getAsJsonArray("b")) {
                JsonObject bo = be.getAsJsonObject();
                Btn b = new Btn();
                b.key = bo.get("l").getAsString();
                b.cmd = bo.get("c").getAsString();
                b.confirm = flag(bo, "x");
                b.danger = flag(bo, "d");
                b.admin = flag(bo, "a");
                w.buttons.add(b);
            }
        }
        if (o.has("f")) {
            for (JsonElement fe : o.getAsJsonArray("f")) {
                JsonObject fo = fe.getAsJsonObject();
                Field f = new Field();
                f.name = fo.get("n").getAsString();
                f.kind = fo.get("k").getAsString();
                f.src = fo.has("src") ? fo.get("src").getAsString() : "";
                if (fo.has("v")) {
                    JsonArray arr = fo.getAsJsonArray("v");
                    for (JsonElement v : arr) {
                        f.values.add(v.getAsString());
                    }
                }
                if (fo.has("min")) {
                    f.min = fo.get("min").getAsDouble();
                }
                if (fo.has("max")) {
                    f.max = fo.get("max").getAsDouble();
                }
                w.fields.add(f);
            }
        }
        return w;
    }
}
