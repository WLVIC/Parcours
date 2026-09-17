package org.wvicto.parcours.service;

import org.jxmapviewer.viewer.TileFactoryInfo;
import org.wvicto.parcours.util.SecureOSMTileFactoryInfo;

/**
 * Fournisseur de tuiles OpenStreetMap.
 * Implémente TileFactoryProvider pour être compatible avec CarteService.
 */
public class OpenStreetMapProvider implements TileFactoryProvider {
    @Override
    public TileFactoryInfo getTileFactoryInfo() {
        // ✅ Utilise ta classe SecureOSMTileFactoryInfo existante
        return new SecureOSMTileFactoryInfo();
    }
}