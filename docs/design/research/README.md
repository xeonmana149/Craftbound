# Foundation balance probes

These files document the 2 November 2026 design investigation. They are not registered NPC decks, production test tasks or changes to game balance.

- FoundationProbe: 600 games using the bundled catalogue and four baseline decks.
- FoundationProbeRuntime: 300 games using the saved runtime catalogue, with two Zombie Fishermen replacing River School's two Villagers.
- FoundationProbeCounter: 300 games using the bundled catalogue, replacing opposing Librarians with Lava Buckets against the baseline Fighter.

The paired result files contain raw program output. The proposal explains confounds and interpretations; do not compare the overall first-player percentage across suites as though they sample the same matchups.

The Java files are in package craftbound.game and require the project's compiled main classes, bundled resources and Minecraft/Fabric runtime dependencies on the classpath. The original runs used the existing WorkshopChecks classpath under JDK 25, compiled these sources to a separate scratch classes directory, then invoked their main classes. Runtime loading uses the local development catalogue path shown in its source; adjust that path when rerunning elsewhere. Reproduce against the same game build and catalogue content, or label the output as a new experiment. These captured sources do not pin all game dependencies or future Workshop changes.

Deck IDs/counts are also captured in ../foundation-npc-decks.proposal.json. Mulligan guidance there is a design suggestion and was not installed in MatchAI for these runs.
