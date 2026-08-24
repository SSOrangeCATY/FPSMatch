package com.ptcrys.fpsmatch.common.client.minimap.editor;

interface EditorTransportListener {
    EditorTransportListener NONE = new EditorTransportListener() { };

    default void detached() { }

    default void resuming() { }

    default void restored() { }
}
