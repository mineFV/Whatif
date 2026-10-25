# WhatIf

Petit mod Fabric de confort (QoL), côté client uniquement, pour Minecraft **26.2**.

## Fonctionnalités

- **Pseudo personnalisé** : affiche un autre pseudo pour toi seul (couleurs, dégradés, gras/italique/barré, ombre, symboles). Purement visuel : le serveur et les autres joueurs voient toujours ton vrai pseudo.
- **Dark Screen** : voile noir translucide sur l'écran de jeu (opacité réglable, option pour teinter aussi le HUD).
- **Musique en cours** : HUD avec le titre, l'artiste et la progression du morceau (YouTube Music, Spotify... tout lecteur MPRIS sous Linux). Nécessite `playerctl`.
- **Menu** : la commande `/whatif` ouvre le menu principal.

## Prérequis

- Minecraft 26.2
- Fabric Loader 0.19.3 ou plus
- Fabric API (`0.155.2+26.2`)
- Java 25
- Pour le HUD musique : Linux avec `playerctl` (`sudo pacman -S playerctl` sur Arch)

## Compiler

```
./gradlew build
```

Le mod compilé se trouve dans `build/libs/whatif-<version>.jar` (ignore le fichier `-sources`).

Pour lancer un client de test :

```
./gradlew runClient
```

## Configuration

Les réglages sont enregistrés dans `config/whatif.json` de l'instance Minecraft.
