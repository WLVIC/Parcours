package org.wvicto.parcours.service;

/**
 * Une copie enrichie n'a pas pu être écrite (pas de fichier source, format non pris en
 * charge, fichier existant à ne pas remplacer, erreur disque...). Son message est destiné
 * à être affiché à l'utilisateur.
 */
public class EcritureGpxException extends Exception {

    private static final long serialVersionUID = 1L;

    public EcritureGpxException(String message) {
        super(message);
    }

    public EcritureGpxException(String message, Throwable cause) {
        super(message, cause);
    }
}