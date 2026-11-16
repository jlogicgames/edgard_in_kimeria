#!/usr/bin/env python3
"""Text style gate for the player-facing catalogs (docs/TEXT_STYLE.md, data in tools/text_style.json).

T1 glossary: a concept named in the English source is named in every translation; avoided forms never appear.
T2 tale register: narration and spoken healer/Edgard lines name no control and no game jargon (per-language
   word lists; stems match at a word start, words as whole words).
T3 zone message budget: English HINT_* source within the character and sentence budget.
T4 no key names in zone messages: no binding token used as a key (followed by +, : or /).
T5 unused keys: every catalog key is read by code (Msg.KEY) or authored in a map ("KEY").
T6 glossary documented: every glossary form appears in docs/TEXT_STYLE.md.

Pixel limits (lines at the real font size) stay in LocalizationLayoutTest and HealerDialogueTest.
An allowlist entry suppresses exactly one finding (one key, one language, one term) and must name its
rewrite issue as #NNN; an entry that
matches nothing fails, so the allowlist empties as the rewrite lands.
"""
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'tools' / 'text_style.json'
STYLE_DOC = ROOT / 'docs' / 'TEXT_STYLE.md'
SENTENCE_END = re.compile(r'[.!?](?=\s|$)')


def load_catalogs(languages, root=ROOT):
    return {lang: json.loads((root / 'assets' / 'localization' / f'{lang}.json').read_text(encoding='utf-8'))
            for lang in languages}


def is_voice(key, voice):
    if key in voice.get('exclude', ()):
        return False
    return key in voice.get('keys', ()) or any(key.startswith(p) for p in voice.get('prefixes', ()))


def word_pattern(word):
    return re.compile(r'(?<!\w)' + re.escape(word) + r'(?!\w)', re.IGNORECASE)


def stem_pattern(stem):
    return re.compile(r'(?<!\w)' + re.escape(stem), re.IGNORECASE)


def register_hits(text, terms):
    """Matched terms of one register group in one language: stems at a word start, words as whole words."""
    hits = [s for s in terms.get('stems', ()) if stem_pattern(s).search(text)]
    hits += [w for w in terms.get('words', ()) if word_pattern(w).search(text)]
    return hits


def check_glossary(catalogs, data):
    source, findings = data['source'], []
    for entry in data['glossary']:
        concept, stems = entry['concept'], entry['stems']
        for key, text in catalogs[source].items():
            if not any(s in text.lower() for s in stems[source]):
                continue
            for lang in data['languages']:
                if lang != source and not any(s in catalogs[lang].get(key, '').lower() for s in stems[lang]):
                    findings.append(('T1', key, lang, concept, f'names "{concept}" in {source} but not in {lang}'))
        for lang, avoided in entry.get('avoid', {}).items():
            for key, text in catalogs[lang].items():
                for form in avoided:
                    if form in text.lower():
                        findings.append(('T1', key, lang, form, f'uses avoided form "{form}" for "{concept}"'))
    return findings


def check_register(catalogs, data):
    findings, tale = [], data['voices']['tale']
    for lang in data['languages']:
        for key, text in catalogs[lang].items():
            if not is_voice(key, tale):
                continue
            for name, group in data['register'].items():
                hits = register_hits(text, group.get(lang, {}))
                if hits:
                    findings.append(('T2', key, lang, name, f'tale voice names {name}: {", ".join(hits)}'))
    return findings


def check_hint_budget(catalogs, data):
    hint, source, findings = data['voices']['hint'], data['source'], []
    for key, text in catalogs[source].items():
        if not is_voice(key, hint):
            continue
        stripped = text.strip()
        sentences = len(SENTENCE_END.findall(stripped)) + (0 if SENTENCE_END.search(stripped[-1:]) else 1)
        if len(text) > hint['maxSourceChars'] or sentences > hint['maxSentences']:
            findings.append(('T3', key, source, 'length',
                             f'{len(text)} chars, {sentences} sentences (budget {hint["maxSourceChars"]}, '
                             f'{hint["maxSentences"]})'))
    return findings


def binding_pattern(tokens):
    alternatives = '|'.join(re.escape(t) for t in sorted(tokens, key=len, reverse=True))
    return re.compile(r'(?<!\w)(?:' + alternatives + r')(?!\w)(?=\s*[+:/])')


def check_key_names(catalogs, data):
    hint, pattern, findings = data['voices']['hint'], binding_pattern(data['bindingTokens']), []
    for lang in data['languages']:
        for key, text in catalogs[lang].items():
            match = is_voice(key, hint) and pattern.search(text)
            if match:
                findings.append(('T4', key, lang, 'binding', f'names the key "{match[0]}"'))
    return findings


def usage_corpus(data, root=ROOT):
    usage, catalog_class = data['usage'], (root / data['usage']['catalogClass']).resolve()
    code, maps = [], []
    for folder in usage['sources']:
        for path in sorted((root / folder).rglob('*.java')) if (root / folder).is_dir() else ():
            if path.resolve() != catalog_class:
                code.append(path.read_text(encoding='utf-8'))
    for folder in usage['maps']:
        for path in sorted((root / folder).rglob('*.tmx')) if (root / folder).is_dir() else ():
            maps.append(path.read_text(encoding='utf-8'))
    return '\n'.join(code), '\n'.join(maps)


def check_unused(catalogs, data, corpus):
    code, maps = corpus
    return [('T5', key, '*', 'unused', 'read by no code and authored in no map')
            for key in catalogs[data['source']]
            if not re.search(r'\bMsg\.' + re.escape(key) + r'\b', code) and f'"{key}"' not in maps]


def check_documented(data, doc_text):
    return [('T6', entry['concept'], lang, 'doc', f'form "{form}" is missing from docs/TEXT_STYLE.md')
            for entry in data['glossary'] for lang, form in entry['forms'].items() if form not in doc_text]


def allowed(finding, entry):
    check, key, lang, term, _ = finding
    return entry['check'] == check and entry['key'] == key and entry['lang'] == lang and entry['term'] == term


def apply_allowlist(findings, allowlist):
    for entry in allowlist:
        if not re.fullmatch(r'#\d+', entry.get('issue', '')) or not entry.get('reason'):
            raise ValueError(f'allowlist entry needs a GitHub issue (#NNN) and a reason: {entry}')
    remaining = [f for f in findings if not any(allowed(f, e) for e in allowlist)]
    stale = [e for e in allowlist if not any(allowed(f, e) for f in findings)]
    return remaining, stale


def run(data, catalogs, corpus, doc_text):
    findings = (check_glossary(catalogs, data) + check_register(catalogs, data) + check_hint_budget(catalogs, data)
                + check_key_names(catalogs, data) + check_unused(catalogs, data, corpus)
                + check_documented(data, doc_text))
    return apply_allowlist(findings, data.get('allowlist', []))


def main():
    data = json.loads(DATA.read_text(encoding='utf-8'))
    remaining, stale = run(data, load_catalogs(data['languages']), usage_corpus(data),
                           STYLE_DOC.read_text(encoding='utf-8'))
    for check, key, lang, _, message in remaining:
        print(f'{check} {key} [{lang}]: {message}')
    for entry in stale:
        print(f'stale allowlist entry (matches nothing, remove it): {entry["check"]} {entry["key"]} '
              f'[{entry["lang"]}] {entry["term"]} ({entry["issue"]})')
    if remaining or stale:
        print('Text style check failed; see docs/TEXT_STYLE.md.', file=sys.stderr)
        return 1
    print(f'Text style check passed ({len(data.get("allowlist", []))} allowlisted findings tracked).')
    return 0


if __name__ == '__main__':
    sys.exit(main())
