/// <reference types="@mapeditor/tiled-api" />
// Keeps object names in sync with the links drawn through the Trigger `target` property.
//
//   Trigger  ->  "Lever [12 → 13]"          (own id → target id)
//   target   ->  "Torch [13] ← 12"          (own id ← ids of the triggers pointing at it)
//
// Runs on every save, inside Tiled only (the game and its dev mode are not involved).
// The game links by the `target` property and ignores these names; they are for reading the map
// (View > Show Object Names). A Trigger without `target` is a legacy name link: it and its
// partner are left alone so the old name still matches, and a warning is logged instead.

const MECHANISMS = ["Actionable", "Escalator", "Ladder"];

function classOf(object) {
    return object.className !== undefined ? object.className : object.type;
}

function targetIdOf(trigger) {
    const value = trigger.property("target");
    if (value === undefined || value === null) return 0;
    return typeof value === "number" ? value : (value.id || 0);
}

function kindOf(mechanism) {
    const cls = classOf(mechanism);
    if (cls !== "Actionable") return cls;
    const kind = mechanism.property("type");
    return kind ? String(kind) : cls;
}

function objectLayer(map, name) {
    return map.layers.filter(l => l.isObjectLayer && l.name === name);
}

function warn(message) {
    (tiled.warn || tiled.log)("[links] " + message);
}

function relabel(map) {
    const spawn = objectLayer(map, "SpawnPoints").flatMap(l => l.objects);
    const collisions = objectLayer(map, "Collisions").flatMap(l => l.objects);
    const byId = {};
    spawn.forEach(o => { byId[o.id] = o; });
    const incoming = {};

    spawn.filter(o => classOf(o) === "Trigger").forEach(trigger => {
        const id = targetIdOf(trigger);
        if (!id) {
            warn("Trigger [" + trigger.id + "] '" + trigger.name + "' has no target (legacy name link?)");
            return;
        }
        const target = byId[id];
        if (!target || MECHANISMS.indexOf(classOf(target)) < 0) {
            const inCollisions = collisions.some(o => o.id === id);
            warn("Trigger [" + trigger.id + "] target " + id + (inCollisions
                ? " is in the Collisions layer; move it to SpawnPoints" : " is not a mechanism in SpawnPoints"));
            trigger.name = "Lever [" + trigger.id + " → ✗" + id + "]";
            return;
        }
        trigger.name = "Lever [" + trigger.id + " → " + id + "]";
        (incoming[id] = incoming[id] || []).push(trigger.id);
    });

    Object.keys(incoming).forEach(id => {
        const mechanism = byId[id];
        mechanism.name = kindOf(mechanism) + " [" + id + "] ← " + incoming[id].join(", ");
    });
}

tiled.assetAboutToBeSaved.connect(asset => {
    if (asset.isTileMap) relabel(asset);
});
