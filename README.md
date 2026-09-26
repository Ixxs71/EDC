# EDC — Essence + Dash cam

Application Android gratuite et libre (GPL-3.0-or-later), sans publicité, sans compte et sans pistage.
Version 0.1 — Android 8.0 et plus.

## Ce que fait l'app

**Essence.** Cherche les stations-service autour de votre position pour un carburant donné (GPLc, gazole,
SP95, SP98, E10, E85) et les classe par **coût réel** : prix affiché plus carburant brûlé pour le détour
aller-retour, réparti sur la quantité que vous comptez acheter. Signale les prix dont la mise à jour est
ancienne. Les distances par la route sont estimées sur le téléphone (ligne droite × 1,3) ou, si vous
indiquez l'adresse d'un serveur OSRM, calculées par ce serveur. Crée un fichier GPX que vous ouvrez avec
l'application de cartes de votre choix, ou affiche la liste et ouvre la navigation vers une station.

**Dash cam.** Enregistre la route avec la caméra arrière, sans son, en clips de durée fixe. Les clips les
plus anciens sont supprimés dès que le plafond d'espace choisi est atteint. L'écran affiche le temps
d'enregistrement avant écrasement pour vos réglages. Démarrage manuel, ou automatique à la connexion
d'Android Auto. Une liste intégrée montre les clips du plus récent au plus ancien.

## Installation

Téléchargez l'APK depuis la page « Releases » de ce dépôt et autorisez l'installation depuis cette source.
Les mises à jour peuvent être suivies avec [Obtainium](https://github.com/ImranR98/Obtainium).

## Permissions

| Permission | Pourquoi |
|---|---|
| Localisation (approximative) | Trouver les stations autour de vous. La position précise n'est pas demandée. |
| Caméra | Dash cam. Aucun micro n'est demandé. |
| Notifications | Statut de l'enregistrement, et notification « Dashcam désarmée » après un redémarrage ou une mise à jour. Demandée au démarrage de la dashcam ; sans elle, ces notifications ne s'affichent pas. |
| Réception du démarrage | Prévenir que la dashcam doit être relancée après un redémarrage ou une mise à jour. |
| Service de premier plan (caméra), Internet, état du réseau | Enregistrer avec l'écran éteint ; interroger les données ci-dessous. |

## Vie privée

- Pas de compte, de publicité, de statistiques ni de rapport de plantage envoyé.
- Les vidéos restent sur le téléphone. Aucun son n'est enregistré.
- La recherche de stations envoie votre position **arrondie à environ 110 m** à `data.economie.gouv.fr`
  (rayon autour de vous).
- Les distances par la route sont **estimées sur le téléphone** : rien n'est envoyé. Si vous saisissez
  l'adresse d'un serveur OSRM (le vôtre, par exemple), votre position arrondie et celle des stations lui
  sont envoyées. Aucun serveur n'est imposé : l'instance de démonstration du projet OSRM est réservée à un
  usage raisonnable non commercial et ne convient pas à une application diffusée.
- Le référentiel des enseignes est téléchargé sur `data.gouv.fr`, sans position.
- Votre position est obtenue par le service de localisation d'Android : aucune bibliothèque Google n'est
  embarquée. Selon votre téléphone et vos réglages, ce service peut s'appuyer sur des services Google.
- Un journal local (`events.log` : démarrages et arrêts de la dashcam, fournisseur de localisation utilisé)
  reste dans le stockage privé de l'app et n'est jamais envoyé. Il ne contient aucune position.

## Sources de données et licences

- Prix des carburants : DGCCRF, publiés sur `data.economie.gouv.fr` sous
  [Licence Ouverte v2.0 (Etalab)](https://www.etalab.gouv.fr/licence-ouverte-open-licence/).
- Enseignes des stations : « Référentiel des noms et enseignes de stations-service (enrichi par
  OpenStreetMap) », Chiffrex, sous licence ODbL — © Les contributeurs d'OpenStreetMap.
- Distances par la route, si vous utilisez un serveur [OSRM](https://project-osrm.org/) : données © les
  contributeurs d'OpenStreetMap (ODbL).
- Bibliothèques : AndroidX, Material Components, CameraX, kotlinx.coroutines (Apache-2.0).

## Compiler

JDK 17 ou plus, Android SDK (compileSdk 36).

```
./gradlew testDebugUnitTest   # tests unitaires
./gradlew assembleDebug
```

## Licence

GPL-3.0-or-later, voir [LICENSE](LICENSE). Chaque fichier source porte son identifiant SPDX.

## Soutenir le projet

EDC est gratuit. Si l'app vous est utile, vous pouvez laisser un pourboire via [GitHub Sponsors](https://github.com/sponsors/Ixxs71). Aucun paiement ne passe par l'app.

## Signaler un problème

Ouvrez un ticket sur ce dépôt en indiquant la version de l'app, le modèle de téléphone et la version d'Android.
