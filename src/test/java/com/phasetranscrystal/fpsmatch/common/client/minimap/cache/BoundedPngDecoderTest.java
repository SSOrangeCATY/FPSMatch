package com.phasetranscrystal.fpsmatch.common.client.minimap.cache;

import com.phasetranscrystal.fpsmatch.core.minimap.format.CanonicalPngCodecV1;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedPngDecoderTest {
    @Test
    void decodesCanonicalPngWithinBudgetAndCancelsWorker() {
        byte[] rgba = new byte[4 * 4 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte) 0x11;
            rgba[i + 1] = (byte) 0x22;
            rgba[i + 2] = (byte) 0x33;
            rgba[i + 3] = (byte) 0xFF;
        }
        byte[] png = CanonicalPngCodecV1.encode(4, 4, rgba);
        BoundedPngDecoder decoder = new BoundedPngDecoder(64 * 1024, 16 * 1024);
        DecodedTile tile = decoder.decode(png);
        assertEquals(4, tile.width());
        assertEquals(4, tile.height());
        assertEquals(0x11, tile.rgba()[0] & 0xFF);
        tile.close();

        assertThrows(DecodeBudgetException.class, () ->
                decoder.decode(new byte[] {1, 2, 3, 4, 5}));
    }
}