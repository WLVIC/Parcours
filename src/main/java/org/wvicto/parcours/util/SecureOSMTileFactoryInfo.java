package org.wvicto.parcours.util;

import org.jxmapviewer.OSMTileFactoryInfo;

/**
 * Étend OSMTileFactoryInfo pour forcer HTTPS (OpenStreetMap exige HTTPS depuis 2017).
 * Garde tous les autres paramètres (zoom, axes, etc.) inchangés.
 */
public class SecureOSMTileFactoryInfo extends OSMTileFactoryInfo {
    @Override
    public String getTileUrl(int x, int y, int zoom) {
        // ✅ Remplace "http://" par "https://" dans l'URL générée par OSMTileFactoryInfo
        return super.getTileUrl(x, y, zoom).replace("http://", "https://");
    }
}