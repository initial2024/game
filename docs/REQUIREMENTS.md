# Complete requirements register

[Project](../README.md) · [简体中文](REQUIREMENTS.zh-CN.md) · [Developer handoff](HANDOFF.md)

Updated 2026-11-17 (Asia/Singapore). Consolidates all historical user requests, not a declaration of completion. Baseline: `feature/character-chat-hf-search`, `f10fb00c3c1a9cf06a36791b80c0a06d270bf438`, APK 0.7.0/code 11.

Status: **Implemented** = baseline with evidence; **Partial** = usable first version with remaining scope; **Open** = undelivered; **Device** = physical validation outstanding. Priorities: **P0** immediate, **P1** next gameplay, **P2** expansion, **V** acceptance. Baseline completion does not imply all edge cases or physical hardware passed.

## Binding decisions and superseded requests

- One original game in `initial2024/game`; never modify DSH, Local Dream, Orbit Mail or other projects. Native Kotlin/Compose, fixed package `cn.chronicle.shanhe`, ARM64, stable signature, no committed private keys, no deleted player data.
- Deterministic rules + optional local AI + persistence. Rules settle first; AI cannot alter trusted resources, evidence, entities, identity, life/death or history. Strict whitelisted structured commands and legal/resource checks precede all state changes.
- **Continuous time supersedes the initial turn design.** Pause and selectable speed are required; quarterly accounting is internal, not a player turn or conversation cooldown.
- No paid API, mandatory model download, conversation fee or artificial chat wait. Installed models work offline. No automatic third-party upload of player saves/chats.
- Latest supplied `chronicle_world_map_v3_2.html`, upgraded to the current protocol, supersedes the earlier map baselines. Preserve appearance, features and provenance; no reduced replacement.
- English GitHub description and primary README, Chinese entry point. Chinese game UI has not been requested to become English.
- *Age of History* is the actual strategy game. Tianming and The Orient Doomsday are design references; neither exposed a recognized license at inspection. No unlicensed source/assets import.
- Nonsexual political coercion can have resistance, hostility and petitions; do not erase consequences. Pregnancy remains a separate opt-in, restricted to eligible willing adults. Varied-age/gender nonsexual encounters and kinship must respect role/age restrictions.
- Development, tests, CI and development APK delivery are authorized. No unconfirmed commercial launch, fabricated inference results or emulator-as-phone claims.

## World, setup and time

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| W01 | Custom country/ruler/era/children names, difficulty, seed and strategy; in-game changes preserve old history. | Implemented baseline | P1 |
| W02 | Pure ancient and early-industrial settings with genuinely distinct institutions, production, buildings and warfare; deepen both beyond text reskin. | Partial | P1 |
| W03 | Superpower/medium/small/random scale and peaceful/balanced/warlike climate; corruption and politics remain in all modes. | Implemented baseline | V |
| W04 | Continuous pause/1×/4×/16×/64×, background stop, safe resume, major-event pause, high-speed boundary correctness; clearer pause preferences. | Implemented baseline; preferences partial | P1 |
| W05 | Optional tutorial and first-year protection; guards sized/named for country/scenario; no unlimited protection resets. | Implemented baseline | V |
| W06 | Four starting regions, three neighbors, factions and rich local hierarchy; expand autonomous multi-country dynamics. | Partial | P1 |

## Economy, administration, buildings and terrain

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| E01 | Treasury, annual income/expense, debt, population, morale, stability, administration, corruption, agriculture, commerce, industry, science, infrastructure, armies/diplomacy: explicit units/bounds and causal ledgers. | Implemented baseline | V |
| E02 | Taxes have social/commercial tradeoffs; building/research/war/population consume resources; deficits use debt/cuts/crises. No overflow, duplicate settlement or free infinite growth. | Implemented; extended balance needed | V |
| E03 | Palaces and urban buildings, workshops/factories, farms, mines, roads, academies, canals: costs, materials, terrain, time, upkeep and refunds. | Implemented baseline; depth partial | P1 |
| E04 | Distinct local resources, depleting mines/regenerating forests, terrain-driven yield, transport, building suitability and war. Map reference terrain is not surveyed real elevation/mineral data. | Implemented; balance partial | P1 |
| E05 | Selectable counties/prefectures, multiple officers and policies, independent local population/budgets/security/morale/corruption rather than only national allocation. | Partial | P1 |
| E06 | Standing orders, four-quarter outlook, reserve funds, top risks and rescue options. Extend recurring-budget forecast to transparent full cash flow and contingent costs. | Partial | P1 |
| E07 | Complex natural-language plans: fractions, bank/private capital, multiple steps, partial success, failure/delay, era/administrative checks and uncertainty. | Basic parser/AI plans implemented; financing open | P2 |
| E08 | Limited parallel tasks with reservation/revalidation/cancellation; acting ministers manage authorized national affairs while ruler travels. | Queue implemented; full delegation open | P1 |

## Characters, offices and family

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| C01 | Name/role/age/faction/ability/loyalty/views/relationships/objectives/memories; coherent aging, conflicts, appointment/death. Consolidate all life-state cleanup to prevent resurrection. | Partial | P1 |
| C02 | Every eligible registered living person, including magistrates, troops, commanders, family and foreign rulers, shares real model conversation; access respects custody/study/location/identity. | Implemented baseline; wider tests needed | P0 |
| C03 | Chat affects trust/awe/loyalty/suspicion/hostility via rules, resists repeated-message farming; proposed decrees require preview/confirmation. Per-reigning-ruler relations remain to add. | Partial | P1 |
| C04 | Examinations, recruit/select/appoint controls, capability/office eligibility, local/central officials, salaries/arrears with loyalty/morale costs. Full historical bureaucracy remains. | Partial | P1 |
| C05 | Domestic/foreign study; academy and teacher levels affect training; real duration/fees/unavailable duties/return. Improve balance and teaching depth. | Implemented baseline; depth partial | P1 |
| C06 | Consort selection, customizable child names, separate optional adult/willing/healthy pregnancy eligibility, gestation, birth, care and education costs. | Implemented baseline | V |
| C07 | Genealogy, per-ruler relationships, succession disputes/factions/regency, natural lifespan/recovery and coherent former ruler records. | Initial heir/transfer/regency implemented; full system partial | P1 |
| C08 | Varied-age/gender/ability/motive encounters, friendship/recruitment/kinship/adult political marriage, plausible persisted identity reactions. | Implemented baseline; autonomous lives partial | P1 |
| C09 | Forced nonsexual political acts create resistance, hostile memories, petitions and legitimacy costs; redress/compensation does not erase history or instantly reset trust. | Implemented baseline; more chains partial | P1 |

## Travel, corruption and high-quality cases

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| I01 | Public/incognito/secret travel, destination, companions, disguise, purpose, stay and acting minister; real time/cost/exposure while country runs. | Implemented baseline; delegation partial | P1 |
| I02 | Markets/inns/offices/farms/workshops/mines/camps, talk/inquiry/inspection/local policy/affairs/contextual events; multi-activity stays and return visits. | Partial | P1 |
| I03 | NPC-visible identity filtering; saying “I am emperor” grants no automatic authority. Hidden identity blocks official powers; authenticated reveal has real consequences. | Implemented baseline | V |
| I04 | Persistent truths, participants, anomalies, interests/evidence; no-evidence/suspect/partial/full/false-evidence distinctions. No auto-guilt or one-click universal cleanup. | Implemented baseline | V |
| I05 | Audit/covert inquiry/questioning/inspectors/hearing/replacement/performance reform/institution reform; complete grassroots/local/military/central corruption accounts. | Basic means implemented; four-domain depth partial | P1 |
| I06 | Testable public/disguised/remote-inspector routes with different cost, information, risk, travel/return and follow-up. | Implemented baseline | V |
| I07 | Many high-quality cases: current 64 authored premises need distinct chains, motives, twists, ambiguity, contextual random triggers and linked consequences. Seed variants are not new authored stories. | Partial | P1 |
| I08 | Evidence board with rumor/testimony/ledger/physical distinctions/links, confrontation, false-evidence counterplay, protection and coercion effects; dead witness cannot give new live testimony. | Rule evidence implemented; board/edge cases partial | P1 |
| I09 | Diverse royal petitions: travelers/joint complaints/military/commercial/inn/false complaints/appeals against ruler. Respect player's actual rank and visible identity. | Implemented baseline; more chains partial | P1 |
| I10 | Revisit sees replacements, reforms, construction, rewarded NPCs and lasting resentment; important events/history/memory persist. | Partial | P1 |

## Armies, diplomacy, organizations and historical strategy

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| S01 | County/prefecture/provincial/central forces, commanders, recruit/train/appoint/pay/mobilize/supply, morale and actual troop counts. | Implemented baseline | V |
| S02 | Terrain/roads/canals/resources/supply affect march and battle; deeper encirclement, city damage/recovery and campaign movement. | Partial | P1 |
| S03 | Rapid assault/siege/diplomacy choices, advance warnings, multiple fronts, casualties, peace terms and autonomous foreign governments. | Single-front strategic war and border transfer implemented; rest open | P1 |
| S04 | Age-of-History-inspired strategy: territory ownership changes actual administration, economy, supply and diplomacy, not merely map color. | Partial | P1 |
| S05 | Create inspector/intelligence/assassin/guard organizations; spy/counterspy and fictional in-game funded missions/bounties, risk/time/outcome/political cost. | Implemented baseline; networks partial | P1 |
| S06 | Attacks on player and foreign rulers, warnings/guards/mitigation/novice protection/succession; registered adult fictional targets, uncertain results and coherent refunds. | Implemented baseline; broad life-cycle tests needed | P1 |
| S07 | Conspiracies with motive, participants, targets, funding, preparation, knowledge boundaries, credible evidence, faction alliances and countermeasures. | Partial | P2 |

## Narrative, guidance, pacing and long-term goals

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| N01 | Optional Chengjiang guide/current goal, staged systems: investigate→judge→build→face pressure→inherit; free-play opt-out. | Implemented baseline; playtest pacing | P1 |
| N02 | Important-event pause, quarterly finance/risk report and actionable choices, warning before crises and meaningful rescues. | Partial | P1 |
| N03 | Industry/prosperity/balanced-power/unification routes with milestones/endings; family/study/exams feed main loop. | Initial milestones implemented; endings/depth partial | P1 |
| N04 | ≥30 causal event templates with conditions/cooldowns/results/follow-ups/history; expand linked causal chains rather than random text. | 33 original plus case/sovereign events; chains partial | P1 |
| N05 | Separate trusted state/log/character memory/auxiliary AI summaries; persist narration and bounded relevant summaries, never overwrite facts. | State/log/memories implemented; persistent AI summaries open | P1 |
| N06 | Historian's chapter record, varying endings/biographies based only on established facts. | Rule chronicles implemented; AI persistence partial | P2 |
| N07 | Once-per-game quarter rewind, consumed privilege persists across slot copies/imports, abandoned timeline isolated, no duplication exploit. | Open | P2 |

## Local AI and model management

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| A01 | On send, automatically load installed selected/default GGUF if enabled, then generate; no repeated manual loading or silent download. Visible nonblocking verification/loading/generation. | Code implemented in 2544aed; final device verification pending | P0 |
| A02 | Explicit model/template provenance, persistent failure reason, model-center access if missing, cancellation during loading/inference without save damage or double CONVERSE. | Error retention fixed in 2544aed; final device verification pending | P0 |
| A03 | Native llama.cpp, replaceable inference adapter, actual licensed Chinese model narration/intent understanding, no fixed fake AI. | Actual inference implemented; adapter partial | P1 |
| A04 | Broad HF search/GGUF browsing/fixed revision/hash/license/installation, local import, threads/context, unload/delete and measured device recommendations. | Implemented; measured recommendations partial | P1 |
| A05 | Real mirror URLs, progress/pause/retry/resume/foreground service, SHA+GGUF checks, ModelScope/HF Mirror/original source. | Implemented; HF Mirror full-file and vivo background tests outstanding | V |
| A06 | Validate more licensed 0.5B–1.7B quantized models: Chinese quality, ARM64, license, memory/load/speed/long-run stability; larger models optional. | One 0.5B Q4 emulator-verified; broader validation open | V |
| A07 | Strict JSON schema/equivalent, whitelist/entity/resource checks, bounded retry, uncertainty and playable fallback; AI no direct DB/evidence/life/history powers. | Implemented baseline | V |
| A08 | GBNF JSON, KV-prefix reuse, correct prompt templates/context budgets; no unsupported claims that Q4_0 or larger is always better. | Templates/budgets implemented; GBNF/KV open | P2 |
| A09 | Thermal adaptation, memory-pressure unload/output caps, thread/context caps, ARM dotprod/i8mm dispatch and genuine phone metrics. | Memory/caps implemented; thermal/dispatch open; phone Device | P2/V |

## Map, compact UI and presentation

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| M01 | Supplied rotation/zoom/search/details/themes/labels/relations/scenario sandbox preserved; AssetLoader/CSP/restricted bridge/offline local content, no authority to write game state. | Implemented baseline | V |
| M02 | DB snapshots/versioned deltas drive names/factions/borders/hierarchy/city lifecycle/routes/geography, isolated saves/stable clicks. More geography and world behavior remain. | Protocol implemented; gameplay depth partial | P1 |
| M03 | Save→map country/city→stable city ID→native rule update→refresh→process restart consistency. | Browser/Android evidence implemented | V |
| M04 | Detailed selectable provinces/prefectures/counties and native city facts/buildings/officers/troops; keep current 8/14/171 hierarchy and expand useful details. | Implemented baseline; depth partial | P1 |
| M05 | Tax/security/morale overlays, moving armies/trade, terrain/border detail preserving supplied map identity. | Open | P1 |
| M06 | Static canvas/Path2D cache, DPR cap/on-demand draw/pause/destroy/recover, malformed data/migration/out-of-order/isolation checks, leak profiling. | Lifecycle/protocol implemented; cache/profiling open | P2/V |
| U01 | Four native tabs, bounded pages/folded categories/selectors, retained scroll, font scaling, light/dark/eye-care themes; no critical overlap at 390×844. | Implemented baseline; sweep new screens | V |
| U02 | Original attractive icon/design, ambience/click sounds/haptics, readable subset font, ink transitions, accessible seal/reject interaction. | Icon/themes implemented; audio/font/seal open | P2 |
| U03 | Responsive/cancellable AI, background saving, optional useful local notifications, no unwanted reminders. | Implemented baseline; physical lifecycle Device | V |

## Architecture, saves, testing and delivery

| ID | Requirement and remaining acceptance | Status | Priority |
|---|---|---|---|
| T01 | UI/model-independent rules, seeded deterministic replay, revision/CAS atomic commit, traceable actions and no duplicate click settlement. | Implemented baseline | V |
| T02 | Separate economy/characters/events/diplomacy/AI/memory/persistence/UI/models; replaceable inference adapter and smaller orchestration boundaries. | Pure core/engines implemented; memory/adapter partial | P2 |
| T03 | Autosave/manual slots, version/checksum/atomic migration/restart/isolation; streaming large exports beyond current 8MiB/10k decisions/2k chats without deleting records. | Baseline implemented; scalable export open | P1 |
| T04 | ≥1,000 internal periods no invalid state/deadlock; identical state/seed/actions replay; money/population/age/office/death invariants. | Baseline core tests implemented; expand new systems | V |
| T05 | Hundreds of seeds × ten years × scales/eras/climates/strategies, declared interventions; measure insolvency/rebellion/exploits/survival/economic distributions. | Open | V |
| T06 | Damaged model refused; inference cancel/failure/restart safe; offline rules/downloaded model; isolated saves/chats; long-text UI/cancel. | Baseline implemented; auto-load/fault matrix needed | V |
| T07 | WebView security, legacy migration, delta-order rejection, bad geometry, restart and leak checks. | Protocol/restart implemented; leak profiling partial | V |
| T08 | R8/resource shrink, no dev suffix/DebugProbes, 16KiB native alignment, JNI callbacks/signature, own measured baseline profile and memory/power sessions. | Release size/alignment/signature implemented; profiling open | V |
| T09 | Actions real APKs/tests/source/Draft PR/downloads/hashes/features/limitations; distinguish failed jobs, browsers, emulators and phones. | Repeat existing delivery discipline each release | V |
| T10 | Actual vivo Android16 ARM64 install/update/UI/inference/memory/thermal/background/offline/long-play acceptance. | Device: no physical hardware available | V |
| T11 | English About/README, Chinese entry, full portable backlog, branch/build/test/evidence/next-step handoff, kept current. | README/backlog/handoff published; About API403 remains | P0 |

## Completion gates

1. Finish A01/A02 first, including optimized actual-GGUF verification from an unloaded process session.
2. Continue P1 vertical playable loops then P2 depth; keep producing executable builds rather than only documents.
3. Mark completion only with source paths and reproducible evidence; record limitations. Seed combinations are not separately authored stories.
4. Revalidate royal travel/investigation/decision/return/follow-up, new strategic loops and real model chat. Separate browser, emulator and hardware results.
5. Missing physical hardware/private signing key or unresolved model license blocks its own acceptance; preserve runnable work and continue unrelated authorized tasks.
