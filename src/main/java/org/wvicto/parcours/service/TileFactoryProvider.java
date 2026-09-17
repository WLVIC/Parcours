package org.wvicto.parcours.service;

import org.jxmapviewer.viewer.TileFactoryInfo;

/**
 * Interface pour les fournisseurs de tuiles (OpenStreetMap, IGN, etc.).
 * Permet de changer dynamiquement le fournisseur de cartes.
 */
public interface TileFactoryProvider {
    /**
     * @return Une instance de TileFactoryInfo configurée pour le fournisseur.
     */
    TileFactoryInfo getTileFactoryInfo();
}
