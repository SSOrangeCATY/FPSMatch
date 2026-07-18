package com.phasetranscrystal.fpsmatch.common.minimap.server;

public enum MinimapAction {
    OPEN_EDITOR,
    FETCH_SOURCE,
    REQUEST_WORLD_SNAPSHOT,
    INSPECT_DIRTY_SECTIONS,
    MUTATE_DRAFT,
    SAVE_DRAFT,
    UPLOAD,
    RESERVE_PUBLISH,
    COMMIT_PUBLISH,
    QUERY_PUBLISH_STATUS,
    FORCE_CLOSE_SESSION,
    DISCARD_DRAFT
}
