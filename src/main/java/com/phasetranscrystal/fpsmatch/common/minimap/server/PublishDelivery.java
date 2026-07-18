package com.phasetranscrystal.fpsmatch.common.minimap.server;

import com.phasetranscrystal.fpsmatch.core.minimap.model.MapKey;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.PublishOutcome;

public interface PublishDelivery {
    void respond(PublishOutcome outcome);

    void broadcastRevision(MapKey mapKey, long revision);
}
