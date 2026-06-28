package com.tacz.guns.resource.filter;

import com.google.gson.*;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;

public class RecipeFilter {
    private final List<IFilter<Identifier>> whitelist = new ArrayList<>();
    private final List<IFilter<Identifier>> blacklist = new ArrayList<>();

    public void merge(RecipeFilter other) {
        this.whitelist.addAll(other.whitelist);
        this.blacklist.addAll(other.blacklist);
    }

    public boolean contains(Identifier location) {
        boolean allowed = whitelist.isEmpty();
        for (IFilter<Identifier> filter : this.whitelist) {
            if (filter.test(location)) {
                allowed = true;
                break;
            }
        }
        for (IFilter<Identifier> filter : this.blacklist) {
            if (filter.test(location)) {
                allowed = false;
                break;
            }
        }
        return allowed;
    }

    public List<Identifier> filter(List<Identifier> input) {
        List<Identifier> output = new ArrayList<>();
        for (Identifier location : input) {
            if (contains(location)) {
                output.add(location);
            }
        }
        return output;
    }

    public <T> List<T> filter(List<T> input, Function<T, Identifier> getter) {
        List<T> output = new ArrayList<>();
        for (T entry : input) {
            if (contains(getter.apply(entry))) {
                output.add(entry);
            }
        }
        return output;
    }


    public static class Deserializer implements JsonDeserializer<RecipeFilter>, JsonSerializer<RecipeFilter> {
        @Override
        public RecipeFilter deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            RecipeFilter filter = new RecipeFilter();
            JsonArray wl = json.getAsJsonObject().getAsJsonArray("whitelist");
            if (wl != null) {
                loadFilters(wl, filter.whitelist);
            }
            JsonArray bl = json.getAsJsonObject().getAsJsonArray("blacklist");
            if (bl != null) {
                loadFilters(bl, filter.blacklist);
            }
            return filter;
        }

        private void loadFilters(JsonArray array, @NotNull List<IFilter<Identifier>> list) {
            LiteralFilter.Builder<Identifier> builder = new LiteralFilter.Builder<>();
            for (JsonElement element : array) {
                if (element.isJsonPrimitive()) {
                    String entry = element.getAsString();
                    if (entry.startsWith("^")) {
                        try {
                            list.add(new RegexFilter<>(entry));
                        } catch (PatternSyntaxException e) {
                            GunMod.LOGGER.error("Failed to parse regex filter: {}", entry, e);
                        }
                    } else {
                        Identifier rl = Identifier.tryParse(entry);
                        if (rl != null){
                            builder.add(rl);
                        }
                    }
                } else {
                    throw new JsonParseException("Invalid recipe filter entry: " + element);
                }
            }
            list.add(builder.build());
        }

        @Override
        public JsonElement serialize(RecipeFilter filter, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject json = new JsonObject();

            json.add("whitelist", context.serialize(toList(filter.whitelist)));
            json.add("blacklist", context.serialize(toList(filter.blacklist)));
            return json;
        }

        private List<String> toList(List<IFilter<Identifier>> filter) {
            List<String> list = new ArrayList<>();
            for (var f : filter) {
                if (f instanceof LiteralFilter<Identifier> literalFilter) {
                    list.addAll(literalFilter.getSet().stream().map(Identifier::toString).toList());
                } else if (f instanceof RegexFilter<Identifier> regexFilter) {
                    list.add(regexFilter.getPattern().pattern());
                }
            }
            return list;
        }
    }
}
