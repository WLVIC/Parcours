package org.wvicto.parcours.service;

import org.wvicto.parcours.model.PointGpx;
import java.util.List;

/**
 * Interface pour les services d'élévation.
 * Permet de changer facilement d'API (Open-Elevation, IGN, etc.) sans modifier le reste du code.
 */
public interface ElevationService {

    /**
     * Récupère les altitudes pour une liste de points GPS.
     * @param points Liste de points pour lesquels récupérer l'altitude.
     * @return Liste des altitudes correspondantes (dans le même ordre que les points).
     * @throws Exception Si la requête échoue.
     */
    List<Double> getElevations(List<PointGpx> points) throws Exception;
}