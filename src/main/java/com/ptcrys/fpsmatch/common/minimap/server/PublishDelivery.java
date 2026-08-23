package com.ptcrys.fpsmatch.common.minimap.server;

import com.ptcrys.fpsmatch.core.minimap.model.MapKey;
import com.ptcrys.fpsmatch.core.minimap.storage.PublishOutcome;

public interface PublishDelivery {
    void respond(PublishOutcome outcome);

    void broadcastRevision(MapKey mapKey, long revision);
}
