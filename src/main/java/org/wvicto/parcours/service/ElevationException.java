package org.wvicto.parcours.service;

/**
 * Erreur survenue lors de la récupération des altitudes auprès d'un service externe
 * (réseau coupé, service surchargé, réponse incohérente...). Son message est destiné
 * à être affiché à l'utilisateur.
 */
public class ElevationException extends Exception {

    private static final long serialVersionUID = 1L;

    public ElevationException(String message) {
        super(message);
    }

    public ElevationException(String message, Throwable cause) {
        super(message, cause);
    }
}