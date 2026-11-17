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
