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

### Graphique altitude/vitesse/pente
- Profil du trajet sélectionné : **altitude** (m, axe gauche, en bleu) toujours affichée, et un second axe (droite, rouge) basculable entre **vitesse** (km/h) et **pente** (%) via deux boutons radio.
- Les deux courbes sont superposées (deux `LineChart` empilés, l'un transparent) plutôt qu'un seul graphique à deux séries, pour leur donner chacune leur propre échelle.
- **Lissage par "signal d'abord, dérivée ensuite"**, plutôt que l'inverse : vitesse et pente sont toutes deux des dérivées (distance/temps, altitude/distance), qui amplifient mécaniquement le bruit du signal d'origine avant même qu'on ait la chance de le lisser.
  - **Pente** : l'altitude brute est lissée par **médiane glissante** (robuste à un point, ou une courte série de points, d'altitude ponctuellement faux — fréquent avec le GPS, dont la précision verticale se dégrade nettement selon le masquage du ciel à un endroit donné), puis la pente est calculée à partir de l'altitude déjà lissée.
  - **Vitesse** : calculée sur une fenêtre de temps large (distance parcourue entre deux points espacés / temps réellement écoulé entre eux) plutôt que moyennée après coup à partir de vitesses instantanées déjà bruitées par des intervalles très courts entre points consécutifs.
- Se met à jour automatiquement à chaque changement de sélection dans la liste des trajets.

### Points remarquables (`<wpt>`)
Lieux définis manuellement (maison, travail, boulangerie...), enregistrés par clic-droit sur la carte et sauvegardés en JSON (`~/.parcours/points_remarquables.json`).

### Routes de référence (`<rte>`)
- **Génère une route** à partir d'un trajet enregistré désigné comme "typique" d'un itinéraire : le tracé est simplifié par l'algorithme de **Douglas-Peucker**, qui ne conserve que les points nécessaires pour représenter fidèlement sa forme.
- **Création manuelle** par clics successifs sur la carte (mode dédié, aperçu numéroté en direct), pour les cas où aucun trajet enregistré ne représente bien l'itinéraire.
- Les routes sont sauvegardées en JSON (`~/.parcours/routes.json`).
- **Affichage sur la carte** : sélectionne une ou plusieurs routes pour voir leur tracé et chacun de leurs waypoints (utile pour juger de la précision d'une simplification).
- **Classifie les trajets chargés** selon la route de référence qu'ils suivent (ou "Aucune correspondance"), avec un résumé par groupe.
- **Vue d'ensemble** : les trajets regroupés par route dans une fenêtre dépliable, avec un groupe "Aucune correspondance" à part — permet de repérer des trajets similaires non encore associés et de désigner directement l'un d'eux comme nouvelle route typique, avec rafraîchissement immédiat du classement.
- **Analyse de performance** : pour une route donnée, calcule le temps de parcours (premier → dernier point de correspondance) de chaque trajet correspondant, affiche le nombre de trajets trouvés, le record, et leur évolution dans le temps (nuage de points, un graphique par date).
- **Gestion** : fenêtre listant les routes enregistrées, avec suppression (multi-sélection possible).

---

## 🧭 Concepts clés (correspondance avec le format GPX)

| Concept GPX | Classe du projet | Rôle |
|---|---|---|
| `<trk>` | `Trajet` | Un enregistrement GPS réel, avec ses points de trace |
| `<rte>` | `Route` | Un itinéraire de référence, généré par simplification d'un `Trajet` |
| `<wpt>` | `PointRemarquable` | Un lieu défini manuellement (maison, boulangerie...) |

---

## 🚀 Fonctionnalités futures
- [ ] Renommage des trajets : manuel, et automatique (points remarquables reliés ou nom de route reconnue + date + "matin/midi/soir").
- [ ] Nommage des routes partageant un même départ/arrivée comme variantes (ex. "Maison-Travail - Via Seine - Var 1") — convention de nommage envisagée plutôt qu'une hiérarchie stockée, à valider.
- [ ] Menu contextuel sur la carte pour la création de route ("commencer route", "ajouter point", "terminer route" au clic-droit), à la place des entrées du menu général.
- [ ] Menu contextuel sur la liste des trajets, pour y déplacer les actions actuellement en barre de boutons/menu.
- [ ] Gestion des points remarquables (liste, suppression) — actuellement ajout seul, contrairement aux routes qui ont déjà leur fenêtre de gestion.
- [ ] Enrichir un fichier GPX d'un commentaire indiquant la route suivie : décidé que `Trajet` retiendra son fichier source et que l'écriture se fera dans une copie (jamais en écrasant l'original) ; reste à définir l'organisation des copies (dossier "trajets" avec sous-dossiers par mois envisagé, pas encore tranché).
- [ ] Export/import GPX réel des `<rte>` et `<wpt>` (actuellement stockés en JSON interne, pas dans le fichier `.gpx` lui-même).
- [ ] Intégration de l'API IGN (cartes officielles françaises, en parallèle d'OpenStreetMap).
- [ ] Édition des routes (renommer, ajouter/retirer un waypoint) — seule la suppression existe pour l'instant.
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

## 🐞 Dette fonctionnelle connue
