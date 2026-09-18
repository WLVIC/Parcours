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
-  **Afficher une carte interactive** (JXMapViewer + OpenStreetMap).


## 🚀 **Fonctionnalités en cours / futures**
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
