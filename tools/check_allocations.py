#!/usr/bin/env python3
"""Reject per-frame allocation on changed lines of hot methods (docs/DEVELOPMENT.md, "Particles and the allocation guard").

Hot methods are those named update*, render*, draw* and the ones listed in HOT_METHODS (helpers the scanner cannot
discover by name). The check runs on CHANGED lines against --base (default HEAD; origin/main includes committed PR
changes), so existing code needs no baseline; --all prints every hit in the tree, which is how the audit table in the
plan is regenerated. It is a net for new code: the runtime AllocationBudgetTest is what proves a path clean.

It does not follow calls, has no types (varargs and boxing are invisible), and flags a lambda in a hot method without
knowing whether it captures. Every ALLOWED entry states what the excluded code carries.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

import git_base

ROOT = Path(__file__).resolve().parents[1]
BASE = 'core/src/main/java/com/jlogicsoftware/kimeria/'
SOURCE_PATHS = ('core/src/main/java',)

HOT_NAME = re.compile(r'^(?:update|render|draw)')
# Helpers called from a hot method that the name rule cannot find: (file, method) -> why it is per-frame.
HOT_METHODS = {
    ('physics/CollideBody.java', 'overlapsSolidAt'): 'called from checkVerticalCollisions for every falling platform the body touches, every frame',
    ('physics/CollideBody.java', 'checkVerticalCollisions'): 'called from every actor update',
    ('effects/Torch.java', 'spawnCore'): 'called from update every 0.04 to 0.16 s',
    ('effects/Torch.java', 'spawnEmbers'): 'called from update every 0.15 to 0.35 s',
    ('effects/Torch.java', 'spawnSparkles'): 'called from update every 0.08 to 0.26 s',
    ('effects/Torch.java', 'spawnSmoke'): 'called from update every 0.3 to 0.8 s',
    ('effects/EnvironmentFx.java', 'emitGrit'): 'called from update while a grit burst is emitting',
    ('effects/EnvironmentFx.java', 'dustPuff'): 'called from Player.playerJump, which runs inside the player update',
    ('effects/EnvironmentFx.java', 'ceilingGrit'): 'called from Level.activateTrigger inside the player update',
    ('effects/LeafFx.java', 'spawn'): 'called from LeafFx.update whenever the room is below its leaf count (#473)',
    ('effects/LeafFx.java', 'avoided'): 'called from LeafFx.spawn, from update (#473)',
    ('entity/enemy/Beetle.java', 'crawl'): 'called from Beetle.updateEnemy every frame (#327)',
    ('entity/enemy/Beetle.java', 'notice'): 'called from Beetle.updateEnemy every frame of the notice',
    ('entity/enemy/Beetle.java', 'chase'): 'called from Beetle.updateEnemy every frame of the chase',
    ('entity/enemy/Beetle.java', 'windup'): 'called from Beetle.updateEnemy every frame of the wind-up',
    ('entity/enemy/Beetle.java', 'commit'): 'called from Beetle.chase when the hero touches the trigger box',
    ('entity/enemy/Beetle.java', 'burst'): 'called from Beetle.windup, Beetle.hitBySword and a stomp, inside the per-frame updates',
    ('entity/enemy/Beetle.java', 'die'): 'called from Beetle.collidedWithActor when a fireball or a stone hits it, inside their update',
    ('entity/enemy/Beetle.java', 'collidedWithActor'): 'called from Player, Fireball and Carryable updates every frame of contact',
    ('entity/enemy/Beetle.java', 'hears'): 'called from Beetle.crawl every frame',
    ('entity/enemy/Beetle.java', 'lostHero'): 'called from Beetle.chase every frame',
    ('entity/enemy/Beetle.java', 'touchesTrigger'): 'called from Beetle.chase every frame',
    ('entity/enemy/Beetle.java', 'animateCrawl'): 'called from Beetle.crawl and Beetle.chase every frame',
    ('entity/enemy/Beetle.java', 'frameIndex'): 'called from Beetle.render every frame',
    ('entity/enemy/Beetle.java', 'lie'): 'called from Beetle.update every frame the body lies (D101)',
    ('entity/enemy/Beetle.java', 'heroNear'): 'called from Beetle.lie every frame',
    ('entity/enemy/Beetle.java', 'push'): 'called from Beetle.lie when the hero comes near the body',
    ('entity/enemy/Beetle.java', 'stompedOn'): 'called from Beetle.lie every frame',
    ('entity/enemy/Beetle.java', 'poof'): 'called from Beetle.update, Beetle.crumble and Beetle.burnUp, inside the per-frame updates',
    ('entity/enemy/Beetle.java', 'fall'): 'called from Beetle.update every frame of the body',
    ('entity/enemy/RemainsSwing.java', 'update'): 'called from Beetle.lie and Spider.lie every frame (D101; matched by name too)',
    ('entity/enemy/Beetle.java', 'burn'): 'called from Beetle.update every frame the body burns',
    ('entity/enemy/Beetle.java', 'crumble'): 'called from the Player, Fireball and Carryable updates when a weapon reaches the body',
    ('entity/enemy/Beetle.java', 'burnUp'): 'called from the Fireball and Carryable updates when a fire reaches the body',
    ('entity/enemy/Beetle.java', 'bodyFade'): 'called from Beetle.renderBody every frame',
    ('entity/enemy/Beetle.java', 'renderBody'): 'called from Beetle.render every frame the body lies or burns',
    ('effects/BeetleFx.java', 'speck'): 'called from Beetle.windup 50 to 150 times a second (#327)',
    ('effects/BeetleFx.java', 'burst'): 'called from Beetle.burst, inside the per-frame updates',
    ('effects/BeetleFx.java', 'poof'): 'called from Beetle.poof, inside the per-frame updates',
    ('effects/RemainsFx.java', 'crumble'): 'called from Beetle.crumble and Spider.crumble, inside the per-frame updates (D101, #478)',
    ('effects/RemainsFx.java', 'burnGlow'): 'called from Beetle.burnUp and Spider.burnUp, inside the per-frame updates',
    ('effects/RemainsFx.java', 'burnFlame'): 'called from Beetle.burn and Spider.burn 70 times a second',
    ('effects/RemainsFx.java', 'burnOut'): 'called from Beetle.burn and Spider.burn at the end of the burn',
    ('entity/enemy/RemainsSwing.java', 'push'): 'called from Beetle.push and Spider.push when the hero comes near a body (D101)',
    ('effects/SpiderFx.java', 'silk'): 'called from Spider.windup 60 times a second (#328)',
    ('effects/SpiderFx.java', 'line'): 'called from Spider.leap once per 2 px flown',
    ('effects/SpiderFx.java', 'bite'): 'called from Spider.leap when it lands',
    ('effects/SpiderFx.java', 'poof'): 'called from Spider.poof, inside the per-frame updates',
    ('entity/enemy/Spider.java', 'crawl'): 'called from Spider.updateEnemy every frame (#328)',
    ('entity/enemy/Spider.java', 'notice'): 'called from Spider.updateEnemy every frame of the notice',
    ('entity/enemy/Spider.java', 'chase'): 'called from Spider.updateEnemy every frame of the chase',
    ('entity/enemy/Spider.java', 'inPounceBox'): 'called from Spider.chase every frame',
    ('entity/enemy/Spider.java', 'commit'): 'called from Spider.chase when the hero touches the pounce box',
    ('entity/enemy/Spider.java', 'reachableLanding'): 'called from Spider.commit every frame the hero stays in the pounce box while it cannot bite him (#497)',
    ('entity/enemy/Spider.java', 'floorAt'): 'called from Spider.reachableLanding, in a loop, from commit (#497)',
    ('entity/enemy/Spider.java', 'solidAt'): 'called from Spider.reachableLanding, in a loop, from commit (#497)',
    ('entity/enemy/Spider.java', 'clampToRange'): 'called from Spider.updateEnemy every frame (#497)',
    ('world/Level.java', 'spiderFloorAt'): 'called from Spider.floorAt, in a loop, from commit (#497)',
    ('world/Level.java', 'isLadderTop'): 'called from Level.spiderFloorAt (#497)',
    ('entity/enemy/Spider.java', 'windup'): 'called from Spider.updateEnemy every frame of the wind-up',
    ('entity/enemy/Spider.java', 'leap'): 'called from Spider.updateEnemy every frame of the leap',
    ('entity/enemy/Spider.java', 'bite'): 'called from Spider.updateEnemy every frame of the bite',
    ('entity/enemy/Spider.java', 'recover'): 'called from Spider.updateEnemy every frame of the recovery',
    ('entity/enemy/Spider.java', 'checkBite'): 'called from Spider.leap and Spider.bite every frame',
    ('entity/enemy/Spider.java', 'hears'): 'called from Spider.crawl every frame',
    ('entity/enemy/Spider.java', 'lostHero'): 'called from Spider.chase every frame',
    ('entity/enemy/Spider.java', 'animateCrawl'): 'called from Spider.crawl and Spider.chase every frame',
    ('entity/enemy/Spider.java', 'frameIndex'): 'called from Spider.render every frame',
    ('entity/enemy/Spider.java', 'fangBoxInto'): 'called from Spider.checkBite every frame of the leap and the bite',
    ('entity/enemy/Spider.java', 'pounceBoxInto'): 'called from Spider.inPounceBox every frame of the chase',
    ('entity/enemy/Spider.java', 'collidedWithActor'): 'called from Player, Fireball and Carryable updates every frame of contact',
    ('entity/enemy/Spider.java', 'die'): 'called from Spider.collidedWithActor and the sword, inside the per-frame updates',
    ('entity/enemy/Spider.java', 'lie'): 'called from Spider.update every frame the body lies (D101)',
    ('entity/enemy/Spider.java', 'heroNear'): 'called from Spider.lie every frame',
    ('entity/enemy/Spider.java', 'push'): 'called from Spider.lie when the hero comes near the body',
    ('entity/enemy/Spider.java', 'stompedOn'): 'called from Spider.lie every frame',
    ('entity/enemy/Spider.java', 'poof'): 'called from Spider.update, Spider.crumble and Spider.burnUp, inside the per-frame updates',
    ('entity/enemy/Spider.java', 'fall'): 'called from Spider.update every frame of the body',
    ('entity/enemy/Spider.java', 'burn'): 'called from Spider.update every frame the body burns',
    ('entity/enemy/Spider.java', 'crumble'): 'called from the Player, Fireball and Carryable updates when a weapon reaches the body',
    ('entity/enemy/Spider.java', 'burnUp'): 'called from the Fireball and Carryable updates when a fire reaches the body',
    ('entity/enemy/Spider.java', 'bodyFade'): 'called from Spider.renderBody every frame',
    ('entity/enemy/Spider.java', 'renderBody'): 'called from Spider.render every frame the body lies or burns',
    ('entity/enemy/Spider.java', 'heroPresent'): 'called from Spider.crawl, chase, recover, checkBite, lie and updateEnemy every frame',
    ('entity/enemy/Spider.java', 'clampToRange'): 'called from Spider.updateEnemy every frame',
    ('entity/enemy/Spider.java', 'centreX'): 'called from Spider.hears, lostHero, poof, push, burn and the body effects every frame',
    ('entity/enemy/Spider.java', 'floorY'): 'called from Spider.hears, lostHero, windup, land, poof, burn and the body effects every frame',
    ('entity/enemy/Spider.java', 'fangX'): 'called from Spider.windup, leap, land and checkBite every frame of the pounce',
    ('entity/enemy/Spider.java', 'warningRemaining'): 'a public read of the wind-up, read by tests each frame; kept allocation-free for any per-frame caller',
    ('entity/enemy/Spider.java', 'leapBodyInto'): 'called from Spider.leap every frame of the leap',
    ('entity/enemy/Spider.java', 'land'): 'called from Spider.leap when it lands or meets a solid',
    ('entity/enemy/Spider.java', 'travel'): 'called from Spider.crawl and Spider.chase every frame it moves along its route (#483, D103)',
    ('entity/enemy/Spider.java', 'aim'): 'called from Spider.chase every frame: the route point nearest the hero',
    ('entity/enemy/Spider.java', 'faceHero'): 'called from Spider.crawl and Spider.climbTo when it turns to the hero',
    ('entity/enemy/Spider.java', 'climbTo'): 'called from Spider.chase every frame it chases on a wall',
    ('entity/enemy/Spider.java', 'heroDropSide'): 'called from Spider.chase every frame',
    ('entity/enemy/Spider.java', 'atLip'): 'called from Spider.chase every frame the hero is in a drop box',
    ('entity/enemy/Spider.java', 'setUp'): 'called from Spider.chase when the drop winds up',
    ('entity/enemy/Spider.java', 'set'): 'called from Spider.updateEnemy every frame of the drop wind-up',
    ('entity/enemy/Spider.java', 'drop'): 'called from Spider.updateEnemy every frame of the drop',
    ('entity/enemy/Spider.java', 'dangle'): 'called from Spider.updateEnemy every frame of the bite at the thread end',
    ('entity/enemy/Spider.java', 'hang'): 'called from Spider.updateEnemy every frame it hangs',
    ('entity/enemy/Spider.java', 'reel'): 'called from Spider.updateEnemy every frame it reels up',
    ('entity/enemy/Spider.java', 'checkDropBite'): 'called from Spider.drop and Spider.dangle every frame',
    ('entity/enemy/Spider.java', 'hangFangsInto'): 'called from Spider.checkDropBite and fangBoxInto every frame of the drop',
    ('entity/enemy/Spider.java', 'isThreadPose'): 'called from Spider.updateEnemy, boundsInto and render every frame',
    ('entity/enemy/Spider.java', 'isWallPose'): 'called from Spider.updateEnemy, boundsInto and render every frame',
    ('entity/enemy/Spider.java', 'boundsInto'): 'the body box by pose, called by every combat and physics read of the spider every frame',
    ('entity/enemy/Spider.java', 'duSign'): 'called from Spider.travel, atEnd and crawl every frame',
    ('entity/enemy/Spider.java', 'coordinate'): 'called from Spider.atEnd every frame',
    ('entity/enemy/Spider.java', 'atEnd'): 'called from Spider.crawl, atLip and updateEnemy every frame',
    ('entity/enemy/Spider.java', 'hasNext'): 'called from Spider.crawl, travel and updateEnemy every frame',
    ('entity/enemy/Spider.java', 'enterLeg'): 'called from Spider.travel and updateEnemy when it rounds a corner, and from reel',
    ('entity/enemy/Spider.java', 'startWarning'): 'called from Spider.commit and setUp when a wind-up starts',
    ('entity/enemy/Spider.java', 'heroDistanceSquared'): 'called from Spider.hears and lostHero every frame',
    ('entity/enemy/Spider.java', 'centreY'): 'called from Spider.faceHero',
    ('entity/enemy/Spider.java', 'snap'): 'called from Spider.die when it is killed on its thread',
    ('entity/enemy/Spider.java', 'bob'): 'called from Spider.render every frame it hangs',
    ('entity/enemy/Spider.java', 'dropBoxInto'): 'called from Spider.heroDropSide every frame (Route.dropBoxInto)',
    ('effects/SpiderFx.java', 'silkDown'): 'called from Spider.set 60 times a second (#483, D103)',
    ('effects/SpiderFx.java', 'shed'): 'called from Spider.drop once per 6 px of thread paid out',
    ('effects/SpiderFx.java', 'snap'): 'called from Spider.snap when a spider is killed on its thread',
    ('entity/enemy/Beetle.java', 'heroPresent'): 'called from Beetle.crawl, chase and lie every frame',
    ('entity/enemy/Beetle.java', 'clampToRange'): 'called from Beetle.updateEnemy every frame',
    ('entity/enemy/Beetle.java', 'centreX'): 'called from Beetle.hears, lostHero, windup, burst, poof, push and burn every frame',
    ('entity/enemy/Beetle.java', 'floorY'): 'called from Beetle.hears, lostHero, windup, burst, poof and burn every frame',
    ('entity/enemy/Beetle.java', 'warningRemaining'): 'a public read of the wind-up, read by tests each frame; kept allocation-free for any per-frame caller',
    ('entity/enemy/Beetle.java', 'triggerBoxInto'): 'called from Beetle.touchesTrigger every frame of the chase',
    ('entity/enemy/Beetle.java', 'blastBoxInto'): 'called from Beetle.burst inside the per-frame update',
    ('effects/FireballFx.java', 'trail'): 'called from Fireball.update once per 2 px flown (#397)',
    ('effects/FireballFx.java', 'hit'): 'called from Fireball.update when the ball stops on terrain, a monster, a stump or root or a hive',
    ('effects/FireballFx.java', 'heavyHit'): 'called from Fireball.update when the ball stops on the boss',
    ('effects/FireballFx.java', 'fizzle'): 'called from Fireball.update when the ball burns out',
    ('effects/FireballFx.java', 'halo'): 'called from FireballFx.hit, heavyHit and fizzle, inside Fireball.update',
    ('effects/FireballFx.java', 'flame'): 'called from every FireballFx spawn helper, inside Fireball.update',
    ('effects/RingPool.java', 'start'): 'called from FireballFx.hit and heavyHit, inside Fireball.update, and from PickupSparksFx.burst (writes a fixed ring slot)',
    ('effects/FireballFx.java', 'sparkCone'): 'called from FireballFx.hit and heavyHit, inside Fireball.update',
    ('effects/FireballFx.java', 'drawBallHalo'): 'called from Fireball.render every frame a ball flies',
    ('world/Level.java', 'fireballFizzle'): 'called from Fireball.update when the ball stops on terrain or burns out',
    ('entity/items/Fireball.java', 'burst'): 'called from Fireball.update when the ball stops',
    ('effects/SporeCloud.java', 'burst'): 'called from FungusTrap.update when the swell ends (writes the cloud into the fixed pool)',
    ('effects/SporeCloud.java', 'spawn'): 'called from SporeCloud.burst, inside FungusTrap.update',
    ('entity/objects/HoneyFungus.java', 'burst'): 'called from FungusTrap.update when the swell ends (writes the spores into the fixed pool)',
    ('ui/FairyGuide.java', 'offscreenTarget'): 'called from FairyGuide.update every frame (D87: picks the off-screen mechanism)',
    ('ui/FairyGuide.java', 'nearestOffscreen'): 'called from FairyGuide.offscreenTarget every frame',
    ('ui/FairyGuide.java', 'sizeCue'): 'called from FairyGuide.update on the first frame after a pull (D87 update (#434))',
    ('ui/FairyGuide.java', 'setKey'): 'called from FairyGuide.offscreenTarget every frame (D87 update (#434))',
    ('ui/FairyGuide.java', 'angle'): 'called from FairyGuide.offscreenTarget and scanKeys (D87 update (#434))',
    ('ui/FairyGuide.java', 'keyLess'): 'called from FairyGuide.scanKeys (D87 update (#434))',
    ('ui/FairyGuide.java', 'nextAfterKey'): 'called from FairyGuide.offscreenTarget at each hop (D87 update (#434))',
    ('ui/FairyGuide.java', 'scanKeys'): 'called from FairyGuide.nextAfterKey (D87 update (#434))',
    ('ui/FairyGuide.java', 'edgePoint'): 'called from FairyGuide.update every frame an off-screen mechanism is marked',
    ('ui/FairyGuide.java', 'changedStrength'): 'called from FairyGuide.renderBehind and renderFront for every marked mechanism',
    ('entity/player/Player.java', 'collectLinkedTargets'): 'called from FairyGuide.update every frame (D87)',
    ('entity/player/Player.java', 'selectedInteraction'): 'called every frame a target is selected, by the fairy guide and the hint text (D87: now a reused instance)',
}
# (file, method, kind) -> what the excluded code carries and why fixing it would lose that.
# Existing hot-path allocations are NOT listed: the check only looks at changed lines, so touching one means fixing it.
ALLOWED = {
    ('world/Level.java', 'renderDebug', 'iterator'):
        'F1 collider/hitbox gizmos, drawn only while the debug flag is on: a measurement overlay, not a gameplay frame',
}

RULES = {
    'new': re.compile(r'\bnew\s+(?P<type>[\w.]+)(?:\s*<[^;()]*?>)?\s*[(\[]'),
    'string': re.compile(r'\bString\s*\.\s*(?:format|valueOf)\s*\(|"[ ]*"\s*\+|\+(?!=)\s*"[ ]*"|\+=\s*"[ ]*"'),
    'factory': re.compile(r'\.\s*stream\s*\(\s*\)|\b(?:List|Set|Map)\s*\.\s*of\s*\(|\bArrays\s*\.\s*asList\s*\(|\bCollections\s*\.\s*\w+\s*\('),
    'iterator': None,  # needs the file's declarations, see iterator_hits
    'lambda': None,    # needs the surrounding statement, see lambda_hits
}
ADVICE = {
    'new': 'no `new` per frame: reuse a field or a fixed array, pool with DefaultPool/PoolManager, or cache it',
    'string': 'String.format/valueOf/concatenation allocates: cache the text or change it only when its inputs change',
    'factory': 'this factory/stream call allocates: use a reused libGDX Array/FloatArray and an index loop',
    'iterator': 'for-each over a List/Set/Map creates an Iterator: use an index loop or a libGDX Array',
    'lambda': 'lambda in a hot method (it allocates when it captures): use a field or a named method reference cached once',
}
EXCEPTION_TYPE = re.compile(r'(?:Exception|Error|Throwable)$')
COLLECTION_TYPES = ('List|ArrayList|LinkedList|Set|HashSet|LinkedHashSet|TreeSet|SortedSet|Collection|Map|HashMap|'
                    'LinkedHashMap|TreeMap|Iterable|Queue|Deque|ArrayDeque')
DECLARATION = re.compile(r'\b(?:' + COLLECTION_TYPES + r')\s*<[^;=(){}]*>\s+(\w+)\s*[;=,)]')
FOR_EACH = re.compile(r'\bfor\s*\([^;()]*?:\s*(?:this\s*\.\s*)?(\w+)\s*(?P<call>\.\s*(?:values|keySet|entrySet)\s*\(\s*\))?\s*\)')
NON_CODE = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
METHOD = re.compile(
    r'(?:^|[;{}])\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:(?:public|private|protected|static|final|synchronized|abstract|default)\s+)*'
    r'(?:<[^>]+>\s+)?(?:[\w<>?\[\],.]+\s+)?(\w+)\s*\([^;{}]*\)\s*(?:throws\s+[\w.,\s]+)?\{')
NOT_METHODS = {'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'new', 'else', 'try', 'do'}


def code_only(text):
    """Blank comments and literals but keep the quotes of a string, so concatenation stays visible."""
    def blank(match):
        literal = match[0]
        if literal.startswith('"'):
            return '"' + ''.join('\n' if c == '\n' else ' ' for c in literal[1:-1]) + '"'
        return ''.join('\n' if c == '\n' else ' ' for c in literal)
    return NON_CODE.sub(blank, text)


def methods(code):
    found = []
    for match in METHOD.finditer(code):
        name = match[1]
        if name in NOT_METHODS:
            continue
        start = match.end() - 1
        depth = 1
        end = start + 1
        while end < len(code) and depth:
            depth += (code[end] == '{') - (code[end] == '}')
            end += 1
        found.append((start, end, name))
    return found


def owner_of(spans, position):
    inside = [(b - a, name) for a, b, name in spans if a <= position < b]
    return min(inside)[1] if inside else None


def is_hot(path, name):
    return name is not None and (HOT_NAME.match(name) or (path, name) in HOT_METHODS)


def lambda_hits(code):
    for match in re.finditer(r'->', code):
        segment = code[max(code.rfind(';', 0, match.start()), code.rfind('{', 0, match.start()), code.rfind('}', 0, match.start())) + 1:match.start()]
        # `case X ->` and `default ->` are switch arms, not lambdas.
        if re.match(r'\s*(?:case\b|default\b)', segment) and '->' not in segment:
            continue
        yield match.start()


def iterator_hits(code):
    names = set(DECLARATION.findall(code))
    for match in FOR_EACH.finditer(code):
        if match[1] in names:
            yield match.start()


def hits(path, source):
    """Every (line, method, kind, text) in a hot method, allowed or not."""
    code = code_only(source)
    spans = methods(code)
    found = []

    def add(position, kind):
        owner = owner_of(spans, position)
        if is_hot(path, owner):
            line = code.count('\n', 0, position) + 1
            found.append((line, owner, kind, source.splitlines()[line - 1].strip()))

    for kind, pattern in RULES.items():
        if pattern is not None:
            for match in pattern.finditer(code):
                if kind == 'new' and EXCEPTION_TYPE.search(match['type']):
                    continue
                add(match.start(), kind)
    for position in iterator_hits(code):
        add(position, 'iterator')
    for position in lambda_hits(code):
        add(position, 'lambda')
    return sorted(set(found))


def violations(path, source, added=None):
    """Hits on changed lines (all lines when `added` is None) that no ALLOWED entry covers."""
    issues = []
    for line, owner, kind, _ in hits(path, source):
        if added is not None and line not in added:
            continue
        if (path, owner, kind) in ALLOWED:
            continue
        issues.append(f'{path}:{line} ({owner}): {ADVICE[kind]}')
    return issues


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, encoding='utf-8', stderr=subprocess.PIPE)


def changed_lines(root, base):
    if base == git_base.AUTO:
        base = git_base.resolve(root, base, 'Allocation check')
    if base == 'HEAD':
        try:
            git(root, 'rev-parse', '--verify', 'HEAD')
        except subprocess.CalledProcessError:
            # A new repository has no legacy code: both staged and untracked files are new.
            return {path: None for path in git(root, 'ls-files', '--cached', '--others',
                '--exclude-standard', '--', *SOURCE_PATHS).splitlines()}
    else:
        base = git(root, 'merge-base', 'HEAD', base).strip()
    patch = git(root, 'diff', '--no-ext-diff', '--no-color', '--unified=0', base, '--', *SOURCE_PATHS)
    result = {}
    path = None
    line = 0
    for text in patch.splitlines():
        if text.startswith('+++ b/'):
            path = text[6:]
        elif text.startswith('@@'):
            line = int(re.search(r'\+(\d+)', text)[1])
        elif text.startswith('+') and path:
            result.setdefault(path, set()).add(line)
            line += 1
        elif text.startswith(' '):
            line += 1
    for path in git(root, 'ls-files', '--others', '--exclude-standard', '--', *SOURCE_PATHS).splitlines():
        result[path] = None
    return result


def relative(path):
    return path[len(BASE):] if path.startswith(BASE) else path


def check(root, base='HEAD'):
    issues = []
    for path, added in sorted(changed_lines(root, base).items()):
        file = root / path
        if path.endswith('.java') and file.is_file():
            issues.extend(violations(relative(path), file.read_text(encoding='utf-8'), added))
    return issues


def all_hits(root):
    directory = root / BASE
    if not directory.is_dir():
        raise ValueError(f'Java source directory is missing: {directory}')
    lines = []
    for file in sorted(directory.rglob('*.java')):
        path = file.relative_to(directory).as_posix()
        for line, owner, kind, text in hits(path, file.read_text(encoding='utf-8')):
            allowed = ALLOWED.get((path, owner, kind))
            note = f'  [allowed: {allowed}]' if allowed else ''
            lines.append(f'{path}:{line} ({owner}) {kind}: {text}{note}')
    return lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=Path, default=ROOT)
    parser.add_argument('--base', default=None,
                        help="'auto' (default; HEAD with --hook): merge-base with origin/main if it exists, else HEAD; or any ref")
    parser.add_argument('--all', action='store_true', help='Print every hit in the tree instead of checking changed lines')
    parser.add_argument('--hook', action='store_true')
    args = parser.parse_args()
    try:
        if args.hook:
            json.load(sys.stdin)
        if args.all:
            lines = all_hits(args.root.resolve())
            print('\n'.join(lines) + f'\n{len(lines)} hit(s) in hot methods.')
            return 0
        issues = check(args.root.resolve(), git_base.default_base(args.base, args.hook))
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'Allocation check could not run: {error}', file=sys.stderr)
        return 2
    if issues:
        print('Allocation in a per-frame method (update*, render*, draw*, HOT_METHODS); see AGENTS.md:\n' + '\n'.join(issues), file=sys.stderr)
        return 2
    print('{}' if args.hook else 'Allocation check passed for changed production code.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
