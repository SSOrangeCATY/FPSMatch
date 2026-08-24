package com.ptcrys.fpsmatch.common.minimap.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ptcrys.fpsmatch.core.minimap.editor.command.EditorOperation;
import com.ptcrys.fpsmatch.core.minimap.format.StrictJsonParser;
import com.ptcrys.fpsmatch.core.minimap.model.Sha256;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Decodes descriptors only after DraftStore has validated their canonical shape. */
final class DraftDescriptorParser {
    private DraftDescriptorParser() {
    }

    static List<EditorOperation> parse(byte[] bytes) {
        JsonObject root = StrictJsonParser.parse(bytes).getAsJsonObject();
        List<EditorOperation> operations = new ArrayList<>();
        for (JsonElement value : root.getAsJsonArray("operations")) {
            JsonObject operation = value.getAsJsonObject();
            String[] path = operation.get("path").getAsString().split("/");
            switch (operation.get("kind").getAsString()) {
                case "set_opacity" -> operations.add(new EditorOperation.SetOpacity(
                        path[1], path[3], operation.get("opacity").getAsDouble()
                ));
                case "set_visibility" -> operations.add(new EditorOperation.SetVisibility(
                        path[1], path[3], operation.get("visible").getAsBoolean()
                ));
                case "set_locked" -> operations.add(new EditorOperation.SetLocked(
                        path[1], path[3], operation.get("locked").getAsBoolean()
                ));
                case "put_tile" -> operations.add(new EditorOperation.PutTile(
                        path[1], path[3], coordinate(path[5], 0), coordinate(path[5], 1),
                        operation.has("oldHash")
                                ? Optional.of(Sha256.parse(operation.get("oldHash").getAsString()))
                                : Optional.empty(),
                        Sha256.parse(operation.get("newHash").getAsString())
                ));
                case "delete_tile" -> operations.add(new EditorOperation.DeleteTile(
                        path[1], path[3], coordinate(path[5], 0), coordinate(path[5], 1),
                        Sha256.parse(operation.get("oldHash").getAsString())
                ));
                default -> throw new IllegalArgumentException("Unsupported draft operation");
            }
        }
        return List.copyOf(operations);
    }

    private static int coordinate(String fileName, int index) {
        return Integer.parseInt(fileName.substring(0, fileName.length() - 4).split("_")[index]);
    }
}
