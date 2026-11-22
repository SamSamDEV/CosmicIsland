<p align="center">
  <img src="src/main/resources/logo.png" alt="Cosmic Island" width="256">
</p>

# 🌴 Cosmic Island

**Run a full Survivor / Koh-Lanta-style season inside Minecraft.**
**Vis une saison complète façon Koh-Lanta directement dans Minecraft.**

![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge 47.4.29](https://img.shields.io/badge/Forge-47.4.29%2B-orange)
![Java 17](https://img.shields.io/badge/Java-17-blue)
![Status](https://img.shields.io/badge/status-1.0.0--Alpha-red)
![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-lightgrey)

🇬🇧 [English](#-english) · 🇫🇷 [Français](#-français) · 💬 [Commands / Commandes](#-commands--commandes)

---

# 🇬🇧 English

## About

Cosmic Island turns a Minecraft server into a survival island: teams, camps, challenges, tribal councils, the merge and a grand finale. The mod runs the whole season, from the first night to the last jury vote, so hosts can focus on running the game instead of keeping score.

> ⚠️ **1.0.0-Alpha:** the **challenges (immunity and reward games) are not implemented yet.** Hosts run them manually for now. The season flow, council, merge and finale are playable.

## Features

**Season**
- Day and step cycle: camp → reward → immunity → council, shown on a HUD bar
- Phases: pre-merge, post-merge, finale, ended
- Season setup wizard that creates the teams and sets the merge and finale thresholds
- Balanced team draw, with a warning and suggested team sizes when teams can't be equal
- Season history, archives, backups and ranking

**Camp and survival**
- Team campfires with a boss bar and fuel to keep topped up
- Thirst system: canteen, cooking pot, rice and water to purify
- Modded food and fish integrated through item and tag rules (hydration, hunger, risk)
- Participant bracelet

**Council, merge and finale**
- Tribal council with anonymous voting, totem and vote reveal
- Manual team merge, decided by the hosts
- Three-stage finale:
  1. The last three players compete in the poles challenge.
  2. The winner picks one opponent for the final. The third player is eliminated and does **not** join the jury.
  3. The jury votes anonymously. It contains **only players eliminated after the merge**.
- Automatic alert when the jury isn't odd, with the recommended number of players

**Tools for hosts**
- In-game admin window (default key `K`)
- Full-season simulation with fake players, so you can test alone
- Data stored **per world**
- All player-facing text follows the chosen language (English and French included, extensible)

## Requirements

- Minecraft **1.20.1**
- Forge **47.4.29** or newer
- Java **17**
- Required on both client and server

## Installation

1. Install Forge 1.20.1 (47.4.29+).
2. Drop `Cosmic_Island_v<version>.jar` into the `mods` folder, on the server and on every client.
3. Start the game or the server.

## Quick start

1. Join your world as an operator and give yourself the organizer role: `/ccis role set <you> organisateur`
2. Open the admin window with `K` (or `/ccis admin`).
3. Prepare the season: `/ccis season setup <teams> <players>`
4. Draw the teams: `/ccis team draw all`
5. Set each team's base and play zone (see the spawn commands).
6. Start the season: `/ccis season start`
7. Move through days and steps, run council, merge, and finish with the finale.

**Test alone first:** `/ccis season simulate run` plays a complete season with fake players (`Test1`, `Test2`…). It refuses to run if a real participant is registered in the world. Use `/ccis season simulate stop` to clean up.

## The admin window

Press **K** (rebindable in the controls menu) or run `/ccis admin`. The window has a page tree on the left and the content on the right. Every action runs the matching command for you.

| Section | Pages |
|---|---|
| Season | overview, setup, testing, history |
| People | players, teams, ranking |
| Council | council, finale |
| Games | games, balance |
| Camp | campfires, spawns, survival, food, settings |

The older chest-based menu is still available with `/ccis admin chest`.

## Data and configuration

- **Per world** (`<world>/cosmicisland/`): players, teams, spawns, ranking, season, campfires, history, council logs, state, backups.
- **Global** (`config/cosmicisland/`): language, thirst, vocabulary, admins, council, chat, games and campfire settings.
- Data from older versions stored in `config/cosmicisland/` is **not** migrated. Copy the files by hand into the world's `cosmicisland` folder.

## Languages

Set the language with `/ccis language <lang>`. English and French are included. To add one, create `assets/cosmicisland/lang/<code>.json` with the same keys as `en_us.json`.

## Roadmap

- 🔲 Challenges (immunity and reward games)
- 🔲 Rewards and comfort items
- 🔲 Orientation system
- 🔲 Host announcements (titles, sounds, holograms)

## Roles and permissions

| Level | Who |
|---|---|
| Player | everyone |
| Organizer | game hosts, set with `/ccis role set <player> organisateur` |
| Admin | server operators |

---

# 🇫🇷 Français

## Présentation

Cosmic Island transforme ton serveur Minecraft en île de survie : équipes, camps, épreuves, conseils, fusion et finale. Le mod gère toute la saison, de la première nuit jusqu'au dernier vote du jury, pour que l'équipe d'animation se concentre sur le jeu plutôt que sur les scores.

> ⚠️ **1.0.0-Alpha :** les **épreuves (jeux d'immunité et de récompense) ne sont pas encore implémentées.** Les animateurs les gèrent à la main pour l'instant. Le déroulé de saison, le conseil, la fusion et la finale sont jouables.

## Fonctionnalités

**Saison**
- Cycle de jours et d'étapes : camp → récompense → immunité → conseil, affiché sur une barre HUD
- Phases : avant fusion, après fusion, finale, terminée
- Assistant de saison qui crée les équipes et règle la fusion et la finale
- Tirage au sort équilibré des équipes, avec avertissement et tailles conseillées quand elles ne peuvent pas être égales
- Historique, archives, sauvegardes et classement

**Camp et survie**
- Feux de camp d'équipe avec barre de boss et combustible à entretenir
- Système de soif : gourde, marmite, riz et eau à purifier
- Aliments et poissons modés intégrés par règles d'objet ou de tag (hydratation, faim, risque)
- Bracelet de participant

**Conseil, fusion et finale**
- Conseil avec vote anonyme, totem et dévoilement des votes
- Fusion des équipes manuelle, décidée par les animateurs
- Finale en trois temps :
  1. Les trois derniers jouent l'épreuve des poteaux.
  2. Le vainqueur choisit un adversaire pour la finale. Le troisième est éliminé et ne rejoint **pas** le jury.
  3. Le jury vote anonymement. Il est composé **uniquement des joueurs éliminés après la fusion**.
- Alerte automatique si le jury n'est pas impair, avec le nombre de joueurs conseillé

**Outils pour les animateurs**
- Fenêtre d'administration en jeu (touche `K` par défaut)
- Simulation de saison complète avec des joueurs fictifs, pour tester seul
- Données enregistrées **par monde**
- Tous les textes visibles suivent la langue choisie (français et anglais inclus, extensible)

## Prérequis

- Minecraft **1.20.1**
- Forge **47.4.29** ou plus récent
- Java **17**
- Requis côté client et côté serveur

## Installation

1. Installe Forge 1.20.1 (47.4.29+).
2. Dépose `Cosmic_Island_v<version>.jar` dans le dossier `mods`, sur le serveur et chez chaque joueur.
3. Lance le jeu ou le serveur.

## Démarrage rapide

1. Rejoins ton monde en opérateur et donne-toi le rôle d'organisateur : `/ccis role set <toi> organisateur`
2. Ouvre la fenêtre d'administration avec `K` (ou `/ccis admin`).
3. Prépare la saison : `/ccis season setup <équipes> <joueurs>`
4. Tire les équipes au sort : `/ccis team draw all`
5. Définis la base et la zone de jeu de chaque équipe (voir les commandes `spawn`).
6. Lance la saison : `/ccis season start`
7. Enchaîne les jours et les étapes, fais les conseils, la fusion, puis la finale.

**Teste d'abord seul :** `/ccis season simulate run` joue une saison complète avec des joueurs fictifs (`Test1`, `Test2`…). La commande refuse de se lancer si un vrai participant est enregistré dans le monde. Utilise `/ccis season simulate stop` pour nettoyer.

## La fenêtre d'administration

Appuie sur **K** (modifiable dans les contrôles) ou lance `/ccis admin`. L'arborescence des pages est à gauche, le contenu à droite. Chaque action exécute la commande correspondante pour toi.

| Section | Pages |
|---|---|
| Saison | vue d'ensemble, préparation, tests, historique |
| Personnes | joueurs, équipes, classement |
| Conseil | conseil, finale |
| Épreuves | épreuves, équilibrage |
| Camp | feux de camp, points de spawn, survie, nourriture, réglages |

L'ancien menu en coffre reste disponible avec `/ccis admin chest`.

## Données et configuration

- **Par monde** (`<monde>/cosmicisland/`) : joueurs, équipes, spawns, classement, saison, feux de camp, historique, journaux de conseil, état, sauvegardes.
- **Global** (`config/cosmicisland/`) : langue, soif, vocabulaire, admins, conseil, chat, épreuves et réglages des feux de camp.
- Les données des anciennes versions stockées dans `config/cosmicisland/` ne sont **pas** migrées. Copie les fichiers à la main dans le dossier `cosmicisland` du monde.

## Langues

Choisis la langue avec `/ccis language <langue>`. Le français et l'anglais sont inclus. Pour en ajouter une, crée `assets/cosmicisland/lang/<code>.json` avec les mêmes clés que `en_us.json`.

## Feuille de route

- 🔲 Épreuves (jeux d'immunité et de récompense)
- 🔲 Récompenses et confort
- 🔲 Système d'orientation
- 🔲 Annonces d'animateur (titres, sons, hologrammes)

## Rôles et permissions

| Niveau | Qui |
|---|---|
| Joueur | tout le monde |
| Organisateur | animateurs, défini avec `/ccis role set <joueur> organisateur` |
| Admin | opérateurs du serveur |

---

# 💬 Commands / Commandes

All commands start with `/ccis` (commands are in English only). · Toutes les commandes commencent par `/ccis` (en anglais uniquement).

**Level / Niveau :** 👤 Player / Joueur · 🎙️ Organizer / Organisateur · 🛡️ Admin

## General / Général

| Command | Level | EN | FR |
|---|---|---|---|
| `/ccis help` | 👤 | Shows help for your level | Affiche l'aide selon ton niveau |
| `/ccis info` | 👤 | Your role, staff level and team | Ton rôle, ton grade et ton équipe |
| `/ccis base` or `/base` | 👤 | Go back to your team's base (not during a challenge) | Retour à la base de ton équipe (impossible pendant une épreuve) |
| `/ccis history [page]` | 👤 | Season history (eliminations, bracelets, merge…) | Historique de la saison (éliminations, colliers, fusion…) |
| `/ccis history archive <name> [page]` | 👤 | Read an archived season | Relire une saison archivée |
| `/ccis ranking` | 👤 | Overall ranking of wins | Classement général des victoires |
| `/ccis ranking game <game>` | 👤 | Ranking of one game | Classement d'un jeu |
| `/ccis ranking player <name>` | 👤 | Wins of one player | Victoires d'un joueur |
| `/ccis giveup` | 👤 | Give up (asks for confirmation); you become a spectator | Abandonner (avec confirmation) : tu deviens spectateur |
| `/ccis vote` | 👤 | Reopen the council voting menu | Rouvrir le menu de vote du conseil |
| `/ccis finale vote` | 👤 | Open the jury voting menu (jurors only) | Ouvrir le menu de vote du jury (jurés uniquement) |
| `/ccis finale vote <player>` | 👤 | Cast a jury vote for a finalist | Voter pour un finaliste (jury) |
| `/ccis join` / `/ccis decline` | 👤 | Accept or decline a challenge invitation (chat buttons) | Accepter ou refuser une invitation à une épreuve (boutons du chat) |
| `/ccis thirst` | 👤 | Your current thirst | Ta soif actuelle |
| `/admin <message>` | 👤 | Write to the hosts (only private message allowed) | Écrire à l'organisation (seul message privé autorisé) |

## Season / Saison

| Command | Level | EN | FR |
|---|---|---|---|
| `/ccis season setup <teams 2-8> <players 4-200>` | 🎙️ | Creates missing teams and sets merge and finale thresholds | Crée les équipes manquantes et règle les seuils de fusion et de finale |
| `/ccis season start` | 🎙️ | Starts the season | Démarre la saison |
| `/ccis season pause` / `resume` / `end` | 🎙️ | Pauses, resumes or ends the season | Met en pause, reprend ou termine la saison |
| `/ccis season day <n>` / `day next` | 🎙️ | Sets the day, or moves to the next one | Choisit le jour, ou passe au suivant |
| `/ccis season auto <true\|false>` | 🎙️ | Automatic day progression on or off | Progression automatique des jours activée ou non |
| `/ccis season phase <phase>` | 🎙️ | Sets the phase (pre-merge, post-merge, finale…) | Choisit la phase (avant fusion, après fusion, finale…) |
| `/ccis season step <step>` / `step next` | 🎙️ | Sets the step (camp, reward, immunity, council) or moves on | Choisit l'étape (camp, récompense, immunité, conseil) ou avance |
| `/ccis season mergeat <players>` | 🎙️ | Players left that trigger the merge alert | Nombre de joueurs restants qui déclenche l'alerte de fusion |
| `/ccis season finaleat <players>` | 🎙️ | Players left that trigger the finale alert (default 3) | Nombre de joueurs restants qui déclenche l'alerte de finale (3 par défaut) |
| `/ccis season reset` / `reset confirm` | 🎙️ | Resets the season (asks for confirmation) | Réinitialise la saison (avec confirmation) |
| `/ccis season jury` | 🎙️ | Shows the current jury | Affiche le jury actuel |
| `/ccis season fake <count>` / `fake clear` | 🎙️ | Adds or removes fake participants | Ajoute ou retire des faux participants |
| `/ccis season simulate` | 🎙️ | Help for the simulation | Aide de la simulation |
| `/ccis season simulate run [players] [teams] [mergeat]` | 🎙️ | Plays a full season with fake players (defaults 12 / 2 / 10). ⚠️ resets the season | Joue une saison complète avec des joueurs fictifs (12 / 2 / 10 par défaut). ⚠️ réinitialise la saison |
| `/ccis season simulate stop` | 🎙️ | Cleans up the simulation | Nettoie la simulation |

## Teams and players / Équipes et joueurs

| Command | Level | EN | FR |
|---|---|---|---|
| `/ccis team list` | 🎙️ | Lists teams | Liste les équipes |
| `/ccis team add <team> <player>` / `remove <player>` | 🎙️ | Adds a player to a team, or removes one | Ajoute un joueur à une équipe, ou en retire un |
| `/ccis team draw [all]` | 🎙️ | Draws teams: spreads unassigned players (or everyone with `all`) evenly | Tirage au sort : répartit les joueurs sans équipe (ou tout le monde avec `all`) à parts égales |
| `/ccis team name <team> <text>` / `tag` / `color` | 🎙️ | Changes a team's name, tag or colour | Change le nom, le tag ou la couleur d'une équipe |
| `/ccis team create <id> <color>` | 🛡️ | Creates a team | Crée une équipe |
| `/ccis team delete <team>` | 🛡️ | Deletes a team (at least 2 must remain) | Supprime une équipe (il en faut au moins 2) |
| `/ccis team unmerge` | 🛡️ | Back to separate teams | Retour aux équipes séparées |
| `/ccis merge [name]` | 🎙️ | Manual merge: creates the single team, puts everyone in it, announces it | Fusion manuelle : crée l'équipe unique, y met tout le monde et l'annonce |
| `/ccis balance [team]` | 🎙️ | Roulette: randomly picks surplus players so teams are even (e.g. 5v5 instead of 6v5) | Roulette : tire au sort les joueurs en trop pour équilibrer les équipes (ex. 5v5 au lieu de 6v5) |
| `/ccis balance test [players]` | 🎙️ | Tests the roulette alone with fake players | Teste la roulette en solo avec des joueurs fictifs |
| `/ccis revive <player> [team]` | 🎙️ | Puts an eliminated or given-up player back in the game (pick the team in chat) | Remet un éliminé ou un abandon en jeu (choix de l'équipe dans le chat) |
| `/ccis role set <player> <role>` / `role get <player>` | 🛡️ | Sets or reads roles (participant, spectator, organizer) | Change ou lit les rôles (participant, spectateur, organisateur) |
| `/ccis chat global <on\|off>` | 🛡️ | ON: everyone reads everyone / OFF: team chat | ON : tout le monde se lit / OFF : chat par équipe |
| `/ccis reply <player> <message>` | 🎙️ | Replies privately to a player's `/admin` message | Répond en privé à un joueur |

## Council and finale / Conseil et finale

| Command | Level | EN | FR |
|---|---|---|---|
| `/ccis council start <team>` | 🎙️ | Starts a tribal council for a team | Lance un conseil pour une équipe |
| `/ccis council test <team> [count]` | 🎙️ | Tests a council with fake voters | Teste un conseil avec de faux votants |
| `/ccis council menu` / `status` | 🎙️ | Council menu and state | Menu et état du conseil |
| `/ccis council vote close` / `reveal` / `revote` / `cancel` | 🎙️ | Closes, reveals, re-runs or cancels the vote | Clôture, dévoile, refait ou annule le vote |
| `/ccis council immunity <player>` | 🎙️ | Gives or removes immunity | Donne ou retire l'immunité |
| `/ccis finale start` | 🎙️ | Starts the finale (last three players, poles) | Lance la finale (trois derniers, poteaux) |
| `/ccis finale winner <player>` | 🎙️ | Sets the poles winner | Désigne le vainqueur des poteaux |
| `/ccis finale pick <player>` | 🎙️ | The winner picks one opponent; the third is eliminated without joining the jury | Le vainqueur choisit son adversaire ; le troisième est éliminé sans rejoindre le jury |
| `/ccis finale vote start` | 🎙️ | Opens the jury vote | Ouvre le vote du jury |
| `/ccis finale reveal` | 🎙️ | Reveals the jury votes | Dévoile les votes du jury |
| `/ccis finale jury` / `status` / `reset` | 🎙️ | Shows the jury, the finale state, or resets the finale | Affiche le jury, l'état de la finale, ou la réinitialise |
| `/ccis finale simulate` / `simulate stop` | 🎙️ | Tests the finale with a fake jury and finalists | Teste la finale avec un faux jury et de faux finalistes |
| `/ccis bracelet yes\|no` | 👤 | Answers the bracelet prompt in chat | Répond à la question du bracelet dans le chat |

## Games / Épreuves

| Command | Level | EN | FR |
|---|---|---|---|
| `/ccis games` | 🎙️ | Lists challenges and their state | Liste les épreuves et leur état |
| `/ccis start <game>` | 🎙️ | Sends the challenge invitations | Envoie les invitations d'une épreuve |
| `/ccis freeze` / `unfreeze` | 🎙️ | Freezes or releases participants | Immobilise ou libère les participants |
| `/ccis go` | 🎙️ | Starts the countdown and the start | Lance le décompte et le départ |
| `/ccis stop [game]` | 🎙️ | Stops the current challenge | Arrête l'épreuve en cours |
| `/ccis enable <game>` / `disable <game>` | 🛡️ | Enables or disables a game | Active ou désactive un jeu |
| `/ccis poteaux zone pos1\|pos2\|info` | 🎙️ | Sets and shows the water zone of the poles | Définit et affiche la zone d'eau des poteaux |
| `/ccis poteaux check` | 🎙️ | Who is still on a pole? | Qui est encore sur un poteau ? |
| `/ccis ranking reset` / `ranking add` | 🛡️ | Resets or corrects the ranking | Remet à zéro ou corrige le classement |

## Camp and survival / Camp et survie

| Command | Level | EN | FR |
|---|---|---|---|
| `/ccis campfire status` | 🎙️ | State of the campfires | État des feux de camp |
| `/ccis campfire tp <team>` | 🎙️ | Teleports to a team's campfire | Se téléporte au feu d'une équipe |
| `/ccis campfire set <team>` | 🛡️ | Registers the campfire you are looking at | Définit le feu que tu regardes |
| `/ccis campfire remove <team>` / `fuel <team> <value>` | 🛡️ | Removes a campfire, or sets its fuel gauge | Retire un feu, ou règle sa jauge de combustible |
| `/ccis spawn set\|tp <team>` | 🛡️ | Play zone of a team | Zone de jeu d'une équipe |
| `/ccis spawn base set\|tp <team>` | 🛡️ | Base of a team | Base d'une équipe |
| `/ccis spawn respawn set\|tp <team>` | 🛡️ | Respawn point (on death) of a team | Point de réapparition (mort) d'une équipe |
| `/ccis spawn game <game> <team>` | 🛡️ | Team point specific to a game | Point d'équipe propre à un jeu |
| `/ccis spawn list` | 🛡️ | Lists all spawn points | Liste tous les points |
| `/ccis thirst set <players> <value>` | 🎙️ | Sets thirst (0–20) | Règle la soif (0–20) |
| `/ccis thirst on\|off` | 🛡️ | Enables or disables thirst | Active ou coupe la soif |
| `/ccis give <pot\|canteen\|rice\|shell\|lighter\|campfire> <players> [count]` | 🎙️ | Gives a survival item | Donne un objet de survie |
| `/ccis food info` | 🎙️ | Hydration rule of the item in your main hand | Règle d'hydratation de l'objet en main |
| `/ccis food list` | 🎙️ | Lists all item and tag rules | Liste toutes les règles d'objets et de tags |
| `/ccis food set <hydration> [saturation] [chance%]` | 🎙️ | Sets the rule of the held item (chance = risk of dehydration) | Règle l'objet en main (chance = risque de déshydratation) |
| `/ccis food remove` | 🎙️ | Removes the rule of the held item | Retire la règle de l'objet en main |
| `/ccis food tag <tag> <hydration> [saturation] [chance%]` | 🎙️ | Sets a rule for a whole item tag (e.g. `forge:raw_fishes`) | Règle un tag d'objets entier (ex. `forge:raw_fishes`) |
| `/ccis food tag remove <tag>` | 🎙️ | Removes a tag rule | Retire la règle d'un tag |

## Admin and tools / Administration et outils

| Command | Level | EN | FR |
|---|---|---|---|
| `/ccis admin` or `/ccis gui` | 🎙️ | Opens the admin window | Ouvre la fenêtre d'administration |
| `/ccis admin chest` | 🎙️ | Opens the old chest-based admin menu | Ouvre l'ancien menu admin en coffre |
| `/ccis say <message>` | 🎙️ | Announcement seen by everyone | Annonce visible par tout le monde |
| `/ccis placeholder <text>` | 🎙️ | Tests vocabulary placeholders | Teste les placeholders du vocabulaire |
| `/ccis language <lang>` | 🛡️ | Sets the mod language (`fr`, `en`…) | Change la langue du mod (`fr`, `en`…) |
| `/ccis reload` | 🛡️ | Reloads the config files | Recharge les fichiers de config |
| `/ccis history new <name>` | 🛡️ | Archives the current season and starts a new one | Archive la saison en cours et en commence une nouvelle |
| `/ccis history clear` / `clear confirm` | 🛡️ | Clears the current season log (asks for confirmation) | Efface le journal de la saison en cours (avec confirmation) |

---

<p align="center">
  <sub>Created for the Cosmic Island server · Créé pour le serveur Cosmic Island</sub><br>
  <sub>© All Rights Reserved</sub>
</p>
