# Reference inspection and original map interactions

Inspected 2026-11-17. This records what was actually accessible, not a claim that the reference games were executed or ported.

## The Orient Doomsday

[Repository](https://github.com/ZANReping/The-Orient-Doomsday) describes an *Age of History 3* mod. Its [map/Maps.json](https://github.com/ZANReping/The-Orient-Doomsday/blob/main/map/Maps.json) points to an EarthTDO map. The [.EarthTDO](https://github.com/ZANReping/The-Orient-Doomsday/tree/main/.EarthTDO) tree contains `provinces`, `cities`, `scenarios`, `data`, `background`, `wonders`, `formableCivs` and `suggestedCivilizations`, plus continent/geographical-region/configuration files. These are concrete map/province/scenario data structures.

This confirms that it is a map-based reference, but its data are meant for the Age of History engine. Inspection did not establish a reusable standalone Android engine or a license permitting its source/assets to be copied. No files from that mod were imported into Chronicle AI.

## Tianming

[Repository](https://github.com/Barbatos923/Tianming) exposed a short design README at inspection, about person-centered historical simulation: offices, territories, armies and money belong to characters, numerical rules produce events, and AI writes history from settled facts. README blob SHA: `9f090e5d2cc71e9c0a10fb6e82d0e7421dc15910`. No playable engine or working map module was available to validate, and no recognized repository license was exposed.

## Chronicle AI implementation direction

Keep the user's supplied map and implement original native connections: county/city selection opens real governance, buildings, officers and troops; country/city/territory selection resolves the current database owner; map army selection produces validated movement/mobilization/current-front commands; foreign selections open targeted diplomacy, intelligence and registered-ruler dialogue. Commands still require native preview/confirmation, resource checks, queueing and persistence. The WebView only sends validated stable selection IDs.

The developing campaign choices connect named commanders/envoys, real costs, supply, terrain, losses, persistent memories, diplomacy and history. A foreign ruler is an equal diplomatic contact, not the player's minister; conversation cannot make a country surrender or transfer territory.

Remaining depth includes visible moving units/trade, richer province jurisdiction and local finances, autonomous countries and multiple fronts. Map command controls are not a claim that those broader systems are finished. See [requirements](REQUIREMENTS.md), especially S02–S07 and M05–M07.

## Permission and private-use boundary

The player primarily intends personal use. That does not turn an unlicensed third-party repository or a commercial game's assets into distributable open-source material. As inspected on 2026-11-17, neither reference root advertises a reusable software license. The Orient Doomsday data, UI files, artwork and engine-specific resources must not be copied into the public Chronicle game repository, releases, or publicly downloadable CI artifacts without explicit rights verification.

- Barbatos923/Tianming: https://github.com/Barbatos923/Tianming — credited for person-centered historical simulation / historical narrative design concepts.
- ZANReping/The-Orient-Doomsday: https://github.com/ZANReping/The-Orient-Doomsday — credited for province/city/scenario/mod data organization concepts.
- Age of History 3 — credited as a strategic gameplay reference, not as an asset or source supplier.

Review and learn from mechanics, but write original Kotlin simulation code and use self-authored or appropriately licensed data and art. Each separately imported asset must have a provenance record (source URL, creator, license, changes, proof of redistribution right). Attribution alone does not substitute for a license. No third-party code or binary asset has been copied in this pass.

## Map-centered progression

Target game loop: select a province on the map -> inspect its city/county, population, resources, official, troops, corruption and roads -> preview a decree or troop movement -> validate conditions and costs -> queue the order -> advance continuous game days -> persist the authoritative result -> refresh visible map, date, province overlays and historical record. The city and province labels are scenario-dependent; stable IDs are not.

Recommended next engineering slices:
1. Display authoritative dates, actual clock state, quarterly progress and reasons for automatic pauses in both native UI and map.
2. Instrument the above with Android UI tests and release evidence.
3. Show regional resources, logistics and province ownership change visibly on map after a rule-engine result.
4. Add autonomous neighbors, multi-front conflict and route/supply economics only after existing bounded war and diplomacy loops are stable.
