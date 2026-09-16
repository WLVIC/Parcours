# 🚴 Parcours – Gestionnaire de trajets GPX

## 🎯 **But du projet**
Application Java/JavaFX pour **gérer et analyser mes trajets à vélo et randonnées pédestres** (format GPX).
Objectif : **Visualiser, filtrer, et analyser** mes parcours (distance, dénivelé, pentes, etc.).

---

## 📌 **Fonctionnalités actuelles**
- ✅ Charger **un ou plusieurs fichiers GPX** (via sélection multiple ou dossier).
- ✅ Visualiser les trajets (nom, date, distance, nombre de points).
- ✅ Manipuler les trajets (couper, supprimer début/fin).
- ✅ Calculer des statistiques :
  - Distance totale.
  - Dénivelé positif/négatif.
  - Pente maximale et moyenne.
- ✅ Filtrer les trajets par :
  - Proximité d’un point GPS (rayon configurable, **25m par défaut**).
  - Zone géographique (boîte englobante).
- ✅ **Mémorisation du dernier répertoire utilisé** (via `Preferences`).

## 🚀 **Fonctionnalités en cours / futures**
- [ ] **Afficher une carte interactive** (JXMapViewer + OpenStreetMap).
- [ ] **Sélection de points remarquables** (Maison, Boulangerie, etc.) via clics sur la carte.
- [ ] **Intégration de l’API IGN** (cartes officielles françaises, en parallèle de OpenStreetMap).
- [ ] **Sauvegarde des points remarquables** (fichier JSON).
- [ ] **Export des statistiques** (CSV/PDF).

---

## 🛠 **Technologies utilisées**
- **Langage** : Java 21
- **UI** : JavaFX 21 + FXML
- **Parsing GPX** : JAXB (DOM)
- **Carte** : JXMapViewer2 (OpenStreetMap)
- **Build** : Maven
- **Tests** : JUnit 5

---
## 📂 **Structure du projet**
Parcours/
├── pom.xml
├── README.md
├── src/main/java/org/wvicto/parcours/
│   ├── App.java
│   ├── model/
│   │   ├── Constants.java       # Constantes (ex: DEFAULT_RADIUS_KM = 0.025)
│   │   ├── PointGpx.java        # Point GPS (lat, lon, altitude, timestamp)
│   │   ├── Trajet.java          # Trajet + méthodes de manipulation
│   │   ├── GpxParser.java       # Parsing des fichiers GPX
│   │   ├── StatistiquesTrajet.java # Calculs (distance, pente, dénivelé)
│   │   └── FiltreTrajet.java    # Filtres géographiques
│   └── view/
│       ├── ParcoursController.java  # Contrôleur principal
│       ├── FiltreController.java    # Contrôleur de filtrage
│       └── CartePointsController.java # Contrôleur de la carte (JXMapViewer)
└── src/main/resources/org/wvicto/parcours/
├── Parcours.fxml
├── Filtre.fxml
└── CartePoints.fxml