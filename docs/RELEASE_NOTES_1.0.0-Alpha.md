# Cosmic Island 1.0.0-Alpha
*Minecraft 1.20.1 · Forge 47.4.29+ · Java 17*

> ⚠️ **Alpha:** the **challenges (immunity and reward games) are not implemented yet.** Hosts run them manually for now. The season flow, council, merge and finale are playable.

## Added
**Season**
- Day and step cycle (camp, reward, immunity, council) with a HUD bar
- Phases: pre-merge, post-merge, finale, ended
- Season setup wizard: `/ccis season setup <teams> <players>`
- Balanced team draw, with a warning and suggested sizes when teams can't be equal
- Season history, archives, backups and ranking

**Camp and survival**
- Team campfires with a boss bar and fuel
- Thirst system: canteen, cooking pot, rice, water purification
- Modded food and fish integration through item and tag rules (`/ccis food`)
- Participant bracelet

**Council, merge and finale**
- Tribal council with anonymous voting, totem and vote reveal
- Manual team merge
- Three-stage finale: poles challenge, winner picks an opponent, anonymous jury vote
- The jury contains only players eliminated after the merge
- Jury parity alert with recommended player counts

**Host tools**
- In-game admin window (default key `K`, or `/ccis admin`)
- Full-season simulation with fake players: `/ccis season simulate run`
- Data stored per world
- English and French included, extensible

## Not yet available
- Challenges (immunity and reward games)
- Rewards and comfort items
- Orientation system
- Host announcements (titles, sounds, holograms)

## Notes
- Data from older versions stored in `config/cosmicisland/` is not migrated: copy it by hand into the world's `cosmicisland` folder.
- Install `Cosmic_Island_v1.0.0-Alpha.jar` on the server and on every client.
