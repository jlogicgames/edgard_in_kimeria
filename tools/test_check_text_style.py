#!/usr/bin/env python3
"""Behavioral checks for the text style gate (tools/check_text_style.py)."""
import copy
import json
import unittest
import check_text_style as gate

DATA = {
    'languages': ['en', 'uk'], 'source': 'en',
    'voices': {'tale': {'keys': ['INTRO_STORY'], 'prefixes': ['HEALER_'], 'exclude': ['HEALER_HINT']},
               'hint': {'prefixes': ['HINT_'], 'maxSourceChars': 30, 'maxSentences': 2}},
    'register': {'controls': {'en': {'stems': ['jump'], 'words': ['key', 'stick']}, 'uk': {'stems': ['стрибк'], 'words': []}},
                 'lives': {'en': {'stems': [], 'words': ['live', 'lives']}, 'uk': {'stems': ['життя'], 'words': []}},
                 'boss': {'en': {'stems': [], 'words': ['boss']}, 'uk': {'stems': [], 'words': ['бос', 'боса']}}},
    'glossary': [{'concept': 'witch', 'forms': {'en': 'witch', 'uk': 'відьма'},
                  'stems': {'en': ['witch'], 'uk': ['відьм']}, 'avoid': {'uk': ['чаклунк']}}],
    'bindingTokens': ['A', 'J', 'Esc'],
    'allowlist': [],
}
DOC = 'Glossary: witch, відьма.'


def catalogs(en, uk):
    return {'en': en, 'uk': uk}


def findings(data, cats, corpus=('', ''), doc=DOC):
    remaining, stale = gate.run(data, cats, corpus, doc)
    return [(f[0], f[1], f[2], f[3]) for f in remaining], stale


class TextStyleGateTest(unittest.TestCase):
    def test_glossary_concept_must_reach_every_translation_and_avoided_forms_fail(self):
        cats = catalogs({'HEALER_A': 'Beware the witch.', 'HEALER_B': 'The witch smiles.'},
                        {'HEALER_A': 'Стережися відьми.', 'HEALER_B': 'Чаклунка всміхається.'})
        found, _ = findings(DATA, cats, ('Msg.HEALER_A Msg.HEALER_B', ''))
        self.assertIn(('T1', 'HEALER_B', 'uk', 'witch'), found)
        self.assertIn(('T1', 'HEALER_B', 'uk', 'чаклунк'), found)
        self.assertNotIn(('T1', 'HEALER_A', 'uk', 'witch'), found)

    def test_tale_voice_names_no_control_or_jargon_but_labels_and_hints_may(self):
        cats = catalogs({'HEALER_A': 'Jump over the boss.', 'HEALER_HINT': 'Jump', 'HINT_X': 'Jump',
                         'INTRO_STORY': 'Bossy clouds.'},
                        {'HEALER_A': 'Стрибком.', 'HEALER_HINT': 'Стрибок', 'HINT_X': 'Стрибок',
                         'INTRO_STORY': 'Хмари.'})
        found, _ = findings(DATA, cats, ('Msg.HEALER_A Msg.HEALER_HINT Msg.INTRO_STORY', '"HINT_X"'))
        self.assertIn(('T2', 'HEALER_A', 'en', 'controls'), found)
        self.assertIn(('T2', 'HEALER_A', 'en', 'boss'), found)
        self.assertIn(('T2', 'HEALER_A', 'uk', 'controls'), found)
        self.assertFalse([f for f in found if f[1] in ('HEALER_HINT', 'HINT_X')])
        # Whole-word terms do not fire inside a longer word ("Bossy").
        self.assertNotIn(('T2', 'INTRO_STORY', 'en', 'boss'), found)

    def test_register_stems_match_at_a_word_start_and_words_only_whole(self):
        # Positive: inflections of a stem, whole words, any case, both languages.
        for text in ('He jumps high.', 'JUMPING', 'Take the key.', 'a stick', 'Nine lives.', 'Страшний стрибком'):
            lang = 'uk' if 'стрибк' in text.lower() else 'en'
            self.assertTrue(any(gate.register_hits(text, g.get(lang, {})) for g in DATA['register'].values()), text)
        # Negative: the term inside a longer word, or a word of another language's list.
        for text in ('He is alive and lively.', 'A monkey', 'Sticky honey', 'Keyed? no: turnkey', 'Bossy clouds',
                     'Overjump', 'Life goes on'):
            self.assertFalse(any(gate.register_hits(text, g.get('en', {})) for g in DATA['register'].values()), text)
        self.assertFalse(any(gate.register_hits('jump boss key', g.get('uk', {})) for g in DATA['register'].values()))

    def test_allowlist_is_per_language_so_a_hit_left_in_one_language_still_fails(self):
        data = copy.deepcopy(DATA)
        data['allowlist'] = [{'check': 'T2', 'key': 'HEALER_A', 'lang': 'en', 'term': 'boss', 'issue': '#371', 'reason': 'r'}]
        cats = catalogs({'HEALER_A': 'The guardian waits.'}, {'HEALER_A': 'Бос чекає.'})
        found, stale = findings(data, cats, ('Msg.HEALER_A', ''))
        self.assertEqual([('T2', 'HEALER_A', 'uk', 'boss')], found)
        self.assertEqual(['en'], [e['lang'] for e in stale])

    def test_hint_budget_counts_characters_and_unterminated_sentences(self):
        cats = catalogs({'HINT_OK': 'Walk right.', 'HINT_LONG': 'x' * 31, 'HINT_THREE': 'Go. Run. Jump on'},
                        {'HINT_OK': 'Іди.', 'HINT_LONG': 'Іди.', 'HINT_THREE': 'Іди.'})
        found, _ = findings(DATA, cats, ('', '"HINT_OK" "HINT_LONG" "HINT_THREE"'))
        self.assertIn(('T3', 'HINT_LONG', 'en', 'length'), found)
        self.assertIn(('T3', 'HINT_THREE', 'en', 'length'), found)
        self.assertNotIn(('T3', 'HINT_OK', 'en', 'length'), found)

    def test_key_names_are_bindings_not_the_english_article(self):
        cats = catalogs({'HINT_A': 'A branch falls. Run on', 'HINT_B': 'Press J: jump', 'HINT_C': 'Esc + A'},
                        {'HINT_A': 'Гілка', 'HINT_B': 'Гілка', 'HINT_C': 'Гілка'})
        found, _ = findings(DATA, cats, ('', '"HINT_A" "HINT_B" "HINT_C"'))
        keys = {f[1] for f in found if f[0] == 'T4'}
        self.assertEqual({'HINT_B', 'HINT_C'}, keys)

    def test_unused_keys_need_a_msg_reference_or_a_map_value(self):
        cats = catalogs({'USED': 'a', 'MAPPED': 'b', 'DEAD': 'c', 'DEAD_TOO': 'd'},
                        {'USED': 'a', 'MAPPED': 'b', 'DEAD': 'c', 'DEAD_TOO': 'd'})
        found, _ = findings(DATA, cats, ('label(Msg.USED); // DEAD_TOO in a comment is no Msg read', 'value="MAPPED"'))
        self.assertEqual({'DEAD', 'DEAD_TOO'}, {f[1] for f in found if f[0] == 'T5'})

    def test_every_glossary_form_is_documented(self):
        found, _ = findings(DATA, catalogs({}, {}), doc='witch only')
        self.assertEqual([('T6', 'witch', 'uk', 'doc')], found)

    def test_allowlist_suppresses_exactly_and_stale_entries_fail(self):
        data = copy.deepcopy(DATA)
        data['allowlist'] = [
            {'check': 'T2', 'key': 'HEALER_A', 'lang': 'en', 'term': 'boss', 'issue': '#371', 'reason': 'r'},
            {'check': 'T2', 'key': 'HEALER_A', 'lang': 'uk', 'term': 'boss', 'issue': '#371', 'reason': 'r'},
            {'check': 'T2', 'key': 'HEALER_GONE', 'lang': 'en', 'term': 'boss', 'issue': '#371', 'reason': 'r'}]
        cats = catalogs({'HEALER_A': 'The boss waits. Jump'}, {'HEALER_A': 'Бос чекає.'})
        found, stale = findings(data, cats, ('Msg.HEALER_A', ''))
        self.assertEqual([('T2', 'HEALER_A', 'en', 'controls')], found)
        self.assertEqual(['HEALER_GONE'], [e['key'] for e in stale])

    def test_allowlist_entries_must_carry_issue_and_reason(self):
        data = copy.deepcopy(DATA)
        for issue in ('', 'P4', '#', '371'):
            data['allowlist'] = [{'check': 'T5', 'key': 'X', 'lang': '*', 'term': 'unused', 'issue': issue, 'reason': 'r'}]
            with self.assertRaises(ValueError, msg=issue):
                gate.run(data, catalogs({}, {}), ('', ''), DOC)

    def test_shipped_catalogs_pass_with_every_allowlist_entry_still_needed(self):
        data = json.loads(gate.DATA.read_text(encoding='utf-8'))
        remaining, stale = gate.run(data, gate.load_catalogs(data['languages']), gate.usage_corpus(data),
                                    gate.STYLE_DOC.read_text(encoding='utf-8'))
        self.assertEqual([], remaining)
        self.assertEqual([], stale)
        self.assertTrue(all(e['issue'] and e['reason'] for e in data['allowlist']))

    def test_main_exits_zero_on_the_repository(self):
        self.assertEqual(0, gate.main())


if __name__ == '__main__':
    unittest.main()
