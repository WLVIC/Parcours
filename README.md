# 🚴 Parcours – Gestionnaire de trajets GPX

## 🎯 But du projet
Application Java/JavaFX pour **gérer et analyser mes trajets à vélo et randonnées pédestres** (format GPX).
Objectif : **visualiser, filtrer et analyser** mes parcours (distance, dénivelé, pentes, itinéraires récurrents...).

---

## 📌 Fonctionnalités actuelles

### Interface
- Disposition en **panneaux redimensionnables** (`SplitPane`) : liste des trajets à gauche, carte en haut à droite, graphique en bas à droite.
- Barre de menu (`MenuBar`) : Fichier, Trajets, Routes.
- Sélectionner un trajet dans la liste le met en évidence sur la carte ; cliquer sur le tracé d'un trajet sur la carte le sélectionne dans la liste (synchronisation dans les deux sens).

### Trajets (`<trk>`)
- Charger **un ou plusieurs fichiers GPX** (sélection multiple ou dossier entier).
- Visualiser les trajets (nom, date, distance, nombre de points).
- Manipuler les trajets (couper, supprimer début/fin).
- Calculer des statistiques : distance totale, dénivelé positif/négatif, pente max et moyenne.
- **Mémorisation du dernier répertoire utilisé** (via `Preferences`).

### Filtrage
- Par proximité d'un point GPS (rayon configurable, 25 m par défaut).
- Par zone géographique (boîte englobante).
- Par **séquence de points remarquables** : sélectionne 2 points ou plus, dans l'ordre, pour retrouver les trajets qui les relient (ex. "Domicile → Travail") ou qui passent par une portion précise (ex. une côte définie par plusieurs points).

### Carte interactive
- Affichage OpenStreetMap (JXMapViewer2), intégrée en permanence dans la fenêtre principale.
- **Clic-droit** sur la carte pour créer un point remarquable (nom + catégorie libre).
- Affiche soit les points remarquables enregistrés, soit le tracé des trajets actuellement listés (le trajet sélectionné en évidence par-dessus les autres).

### Points remarquables (`<wpt>`)
Lieux définis manuellement (maison, travail, boulangerie...), enregistrés par clic-droit sur la carte et sauvegardés en JSON (`~/.parcours/points_remarquables.json`).

### Routes de référence (`<rte>`)
- **Génère une route** à partir d'un trajet enregistré désigné comme "typique" d'un itinéraire : le tracé est simplifié par l'algorithme de **Douglas-Peucker**, qui ne conserve que les points nécessaires pour représenter fidèlement sa forme.
- Les routes sont sauvegardées en JSON (`~/.parcours/routes.json`).
- **Classifie les trajets chargés** selon la route de référence qu'ils suivent (ou "Aucune correspondance"), avec un résumé par groupe.

---

## 🧭 Concepts clés (correspondance avec le format GPX)

| Concept GPX | Classe du projet | Rôle |
|---|---|---|
| `<trk>` | `Trajet` | Un enregistrement GPS réel, avec ses points de trace |
| `<rte>` | `Route` | Un itinéraire de référence, généré par simplification d'un `Trajet` |
| `<wpt>` | `PointRemarquable` | Un lieu défini manuellement (maison, boulangerie...) |

---

## 🚀 Fonctionnalités en cours / futures
- [ ] Graphique altitude/vitesse en fonction de la distance pour le trajet sélectionné (composant en place, pas encore alimenté en données).
- [ ] Renommage des trajets : manuel, et automatique (points remarquables reliés ou nom de route reconnue + date + "matin/midi/soir").
- [ ] Nommage des routes, utilisé ensuite pour le renommage automatique des trajets/fichiers (ex. `2026-09-15 - boulot direct`).
- [ ] Calcul du temps mis pour parcourir une route par chaque trajet qui y passe, date du "record", et fenêtre d'évolution du temps dans la durée.
- [ ] Menu contextuel sur la liste des trajets, pour y déplacer les actions actuellement en barre de boutons.
- [ ] Export/import GPX réel des `<rte>` et `<wpt>` (actuellement stockés en JSON interne, pas dans le fichier `.gpx` lui-même).
- [ ] Intégration de l'API IGN (cartes officielles françaises, en parallèle d'OpenStreetMap).
- [ ] Édition et suppression des points remarquables et des routes depuis l'interface (actuellement ajout seul).
- [ ] Export des statistiques (CSV/PDF).

---

## 🛠 Technologies utilisées
- **Langage** : Java 21
- **UI** : JavaFX 21 + FXML
- **Parsing GPX** : JAXB (DOM)
- **Carte** : JXMapViewer2 (OpenStreetMap)
- **Persistance JSON** : Gson
- **Build** : Maven
- **Tests** : JUnit 5

---

## 🧹 Dette technique connue
- `chargerDossier()` duplique la logique de `GpxService` au lieu de la réutiliser.
- `GpxParser` est rangé dans le package `model` alors qu'il s'agit d'un service de parsing.
- `PointRemarquable` duplique `latitude`/`longitude` de `PointGpx` par composition plutôt qu'une classe commune (ex. `Position`).