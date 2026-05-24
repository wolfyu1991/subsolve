use std::cmp::Ordering;

use crate::word;
use crate::dict::Dict;
use crate::language::{EnglishLanguage, Language};
use crate::mapset::MapSet;
use crate::word::Word;

pub struct Puzzle {
    pub raw_cipher_text: String,
    pub cipher_text: String,
    pub cipher_bytes: Vec<u8>,
    pub num_ciphertext_letters: usize,
    pub num_unique_ciphertext_letters: usize,
    pub trust_spaces: bool,
    pub clues: String,
    pub initial_map: MapSet,
    pub histogram: Vec<i32>,
    pub historder: Vec<u8>,
    pub words: Vec<Word>,
    pub lang: Box<dyn Language>,
}

impl Puzzle {
    pub fn new(
        mut dict: Dict,
        ctext: &str,
        clues: &str,
        trust_spaces: bool,
        allow_identity: bool,
    ) -> Self {
        let lang: Box<dyn Language> = Box::new(EnglishLanguage::new());
        let letter_count = lang.letter_count();
        let raw_cipher_text = ctext.to_string();

        let despace_clues = Self::despace_string(clues.trim());
        let cipher_text = if trust_spaces {
            Self::only_alpha_space_characters(&Self::despace_string(ctext.trim()))
        } else {
            Self::only_alpha_characters(&Self::despace_string(ctext.trim()))
        };
        let cipher_bytes = lang.cipher_text_to_cipher_bytes(&cipher_text);

        let mut initial_map = MapSet::new(letter_count);
        initial_map.set_full_set();
        if !allow_identity {
            for i in 0..letter_count {
                initial_map.forbid_mapping(i as u8, i as u8);
            }
        }

        let mut puzzle = Puzzle {
            raw_cipher_text,
            cipher_text,
            cipher_bytes,
            num_ciphertext_letters: 0,
            num_unique_ciphertext_letters: 0,
            trust_spaces,
            clues: despace_clues.clone(),
            initial_map,
            histogram: vec![],
            historder: vec![],
            words: vec![],
            lang,
        };

        puzzle.handle_clues(&despace_clues);

        puzzle.num_ciphertext_letters = puzzle
            .cipher_bytes
            .iter()
            .filter(|&&b| b < letter_count as u8)
            .count();

        let mut present = vec![false; letter_count];
        for &b in &puzzle.cipher_bytes {
            if b < letter_count as u8 {
                present[b as usize] = true;
            }
        }
        puzzle.num_unique_ciphertext_letters = present.iter().filter(|&&b| b).count();

        let rawwords = puzzle
            .lang
            .cipher_text_to_words(&puzzle.raw_cipher_text);
        let mut dedup_words: Vec<Word> = Vec::new();
        for w in rawwords {
            let exists = dedup_words
                .iter()
                .any(|uw| w.compare_word(uw) == Ordering::Equal);
            if !exists {
                dedup_words.push(w);
            }
        }

        for w in &mut dedup_words {
            let results = dict.lookup(&w.pattern);
            if results.is_empty() {
                w.enabled = false;
                w.candidates = Some(Vec::new());
            } else {
                w.candidates = Some(word::to_candidates(results, &*puzzle.lang));
            }
        }

        let mut histogram = vec![0i32; letter_count];
        for w in &dedup_words {
            for &b in &w.word {
                histogram[b as usize] += 1;
            }
        }

        let mut hists: Vec<(i32, u8)> = histogram
            .iter()
            .enumerate()
            .map(|(i, &c)| (c, i as u8))
            .collect();
        hists.sort_by_key(|b| std::cmp::Reverse(b.0));
        let historder: Vec<u8> = hists.iter().map(|&(_, l)| l).collect();

        puzzle.histogram = histogram;
        puzzle.historder = historder;
        puzzle.words = dedup_words;

        puzzle
    }

    pub fn despace_string(s: &str) -> String {
        if s.is_empty() {
            return s.to_string();
        }
        let spacecount = s.chars().filter(|&c| c == ' ').count();
        let frac = spacecount as f64 / s.len() as f64;
        if frac < 0.4 {
            return s.to_string();
        }
        if s.len() > 8 {
            let bytes = s.as_bytes();
            let mut spacedouble = 0i32;
            for i in 0..s.len() - 2 {
                if bytes[i] == b' ' && bytes[i + 2] == b' ' {
                    spacedouble += 1;
                }
            }
            let frac2 = spacedouble as f64 / (s.len() - 2) as f64;
            if frac2 < 0.4 {
                return s.to_string();
            }
        }
        let mut sb = String::new();
        let mut spacerun = 0i32;
        for c in s.chars() {
            if c == ' ' {
                spacerun += 1;
            } else {
                if spacerun > 1 {
                    sb.push(' ');
                }
                sb.push(c);
                spacerun = 0;
            }
        }
        sb
    }

    pub fn handle_clues(&mut self, clues: &str) {
        self.clues = clues.to_string();
        for tok in clues.split(|c: char| c.is_whitespace() || c == ',') {
            if tok.is_empty() {
                continue;
            }
            if let Some(pos) = tok.find("!=") {
                let key = &tok[..pos];
                let value = &tok[pos + 2..];
                if key.len() == 1 {
                    let k = self.lang.char_to_byte(key.chars().next().unwrap());
                    for vc in value.chars() {
                        let v = self.lang.char_to_byte(vc);
                        self.initial_map.forbid_mapping(k, v);
                    }
                } else if key.len() == value.len() {
                    for (kc, vc) in key.chars().zip(value.chars()) {
                        let k = self.lang.char_to_byte(kc);
                        let v = self.lang.char_to_byte(vc);
                        self.initial_map.forbid_mapping(k, v);
                    }
                }
            } else if let Some(pos) = tok.find('=') {
                let key = &tok[..pos];
                let value = &tok[pos + 1..];
                if key.len() != value.len() {
                    continue;
                }
                for (kc, vc) in key.chars().zip(value.chars()) {
                    let k = self.lang.char_to_byte(kc);
                    let v = self.lang.char_to_byte(vc);
                    self.initial_map.set_mapping(k, v);
                }
            }
        }
    }

    pub fn scramble(&mut self, allow_identity: bool) {
        let mut permuted_map = MapSet::new(self.lang.letter_count());
        permuted_map.randomly_permute(allow_identity);
        self.cipher_text =
            self.lang
                .translate_string(&self.raw_cipher_text, &permuted_map, None);

        let toks: Vec<&str> = self
            .clues
            .split(|c: char| c.is_whitespace() || c == ',')
            .collect();
        let mut new_clues = String::new();
        for tok in toks.iter() {
            if tok.is_empty() {
                continue;
            }
            let parts: Vec<&str> = tok.splitn(2, '=').collect();
            if parts.len() != 2 {
                continue;
            }
            if !new_clues.is_empty() {
                new_clues.push(' ');
            }
            new_clues.push_str(&self.lang.translate_string(parts[0], &permuted_map, None));
            new_clues.push('=');
            new_clues.push_str(parts[1]);
        }
        self.clues = new_clues;

        self.initial_map = MapSet::new(self.lang.letter_count());
        self.initial_map.set_full_set();
        let clues_copy = self.clues.clone();
        self.handle_clues(&clues_copy);
    }

    fn only_alpha_characters(s: &str) -> String {
        s.chars().filter(|c| c.is_alphabetic()).collect()
    }

    fn only_alpha_space_characters(s: &str) -> String {
        let mut sb = String::new();
        let mut have_space = false;
        for c in s.chars() {
            if c.is_alphabetic() {
                if have_space {
                    sb.push(' ');
                }
                sb.push(c);
                have_space = false;
            } else if c == ' '
                || c == '.'
                || c == '-'
                || c == ':'
                || c == ';'
                || c == '?'
                || c == '!'
                || c == '('
                || c == ')'
            {
                have_space = true;
            }
        }
        sb
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::dict::Dict;

    #[test]
    fn test_despace_string_low_space_ratio() {
        assert_eq!(Puzzle::despace_string("HELLO"), "HELLO");
        assert_eq!(Puzzle::despace_string("HELLO WORLD"), "HELLO WORLD");
    }

    #[test]
    fn test_despace_string_high_space_ratio_short() {
        let result = Puzzle::despace_string("A  B");
        assert_eq!(result, "A B");
    }

    #[test]
    fn test_despace_string_single_spaces_removed() {
        let result = Puzzle::despace_string("A B C D E F G H I J");
        assert_eq!(result, "ABCDEFGHIJ");
    }

    #[test]
    fn test_despace_string_double_spaces_preserved_need_triple() {
        let result = Puzzle::despace_string("A   B   C   D");
        assert_eq!(result, "A B C D");
    }

    #[test]
    fn test_despace_string_trailing_spaces_low_ratio() {
        assert_eq!(Puzzle::despace_string("HELLO  "), "HELLO  ");
    }

    #[test]
    fn test_despace_string_leading_spaces_low_ratio() {
        assert_eq!(Puzzle::despace_string("  HELLO"), "  HELLO");
    }

    #[test]
    fn test_despace_string_empty() {
        assert_eq!(Puzzle::despace_string(""), "");
    }

    #[test]
    fn test_despace_string_long_triple_spaces() {
        let result = Puzzle::despace_string("A   B   C   D   E   F   G   H   I   J");
        assert_eq!(result, "A B C D E F G H I J");
    }

    #[test]
    fn test_only_alpha_characters() {
        let result = Puzzle::only_alpha_characters("HELLO, WORLD!");
        assert_eq!(result, "HELLOWORLD");
    }

    #[test]
    fn test_only_alpha_characters_numbers() {
        let result = Puzzle::only_alpha_characters("ABC123DEF");
        assert_eq!(result, "ABCDEF");
    }

    #[test]
    fn test_only_alpha_space_characters() {
        let result = Puzzle::only_alpha_space_characters("HELLO, WORLD!");
        assert_eq!(result, "HELLO WORLD");
    }

    #[test]
    fn test_only_alpha_space_characters_multiple_separators() {
        let result = Puzzle::only_alpha_space_characters("A.B-C:D;E?F!G(H)I");
        assert_eq!(result, "A B C D E F G H I");
    }

    #[test]
    fn test_handle_clues_equals() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "A=B C=D", true, true);

        assert!(puzzle.initial_map.is_mapping_ok(0, 1));
        assert!(!puzzle.initial_map.is_mapping_ok(0, 0));
        assert!(puzzle.initial_map.is_mapping_ok(2, 3));
        assert!(!puzzle.initial_map.is_mapping_ok(2, 2));
    }

    #[test]
    fn test_handle_clues_not_equals_single() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "A!=BC", true, true);

        assert!(!puzzle.initial_map.is_mapping_ok(0, 1));
        assert!(!puzzle.initial_map.is_mapping_ok(0, 2));
        assert!(puzzle.initial_map.is_mapping_ok(0, 3));
    }

    #[test]
    fn test_handle_clues_not_equals_pairwise() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "AB!=CD", true, true);

        assert!(!puzzle.initial_map.is_mapping_ok(0, 2));
        assert!(!puzzle.initial_map.is_mapping_ok(1, 3));
        assert!(puzzle.initial_map.is_mapping_ok(0, 1));
        assert!(puzzle.initial_map.is_mapping_ok(0, 3));
    }

    #[test]
    fn test_handle_clues_empty() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        for i in 0..26 {
            for j in 0..26 {
                assert!(puzzle.initial_map.is_mapping_ok(i as u8, j as u8));
            }
        }
    }

    #[test]
    fn test_puzzle_words_hello_world() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO WORLD", "", true, true);

        assert_eq!(puzzle.words.len(), 2);
        assert_eq!(puzzle.words[0].text, "HELLO");
        assert_eq!(puzzle.words[1].text, "WORLD");
    }

    #[test]
    fn test_puzzle_words_dedup() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO HELLO", "", true, true);

        assert_eq!(puzzle.words.len(), 1);
        assert_eq!(puzzle.words[0].text, "HELLO");
    }

    #[test]
    fn test_puzzle_cipher_bytes_trust_spaces() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO WORLD", "", true, true);

        let expected = vec![7, 4, 11, 11, 14, 26, 22, 14, 17, 11, 3];
        assert_eq!(puzzle.cipher_bytes, expected);
    }

    #[test]
    fn test_puzzle_cipher_bytes_no_trust_spaces() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO WORLD", "", false, true);

        let expected = vec![7, 4, 11, 11, 14, 22, 14, 17, 11, 3];
        assert_eq!(puzzle.cipher_bytes, expected);
    }

    #[test]
    fn test_puzzle_num_ciphertext_letters() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO WORLD", "", true, true);

        assert_eq!(puzzle.num_ciphertext_letters, 10);
    }

    #[test]
    fn test_puzzle_num_unique_ciphertext_letters() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        assert_eq!(puzzle.num_unique_ciphertext_letters, 4);
    }

    #[test]
    fn test_puzzle_histogram() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        assert_eq!(puzzle.histogram[7], 1);
        assert_eq!(puzzle.histogram[4], 1);
        assert_eq!(puzzle.histogram[11], 2);
        assert_eq!(puzzle.histogram[14], 1);
    }

    #[test]
    fn test_puzzle_historder_most_frequent_first() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        assert!(!puzzle.historder.is_empty());
        let mut seen = vec![false; 26];
        for &letter in &puzzle.historder {
            seen[letter as usize] = true;
        }
        for i in 0..26 {
            assert!(seen[i], "letter {} not in historder", i);
        }
    }

    #[test]
    fn test_puzzle_handle_clues_not_equals_with_comma() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "A!=B,C!=D", true, true);

        assert!(!puzzle.initial_map.is_mapping_ok(0, 1));
        assert!(!puzzle.initial_map.is_mapping_ok(2, 3));
    }

    #[test]
    fn test_puzzle_scramble_does_not_panic() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let mut puzzle = Puzzle::new(dict, "HELLO", "A=B", true, true);

        let old_text = puzzle.cipher_text.clone();
        puzzle.scramble(false);
        assert_ne!(puzzle.cipher_text, old_text);
    }

    #[test]
    fn test_puzzle_allow_identity_disabled() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, false);

        for i in 0..26 {
            assert!(!puzzle.initial_map.is_mapping_ok(i as u8, i as u8),
                "identity mapping {}->{} should be forbidden", i, i);
        }
    }

    #[test]
    fn test_puzzle_allow_identity_enabled() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "HELLO", "", true, true);

        for i in 0..26 {
            assert!(puzzle.initial_map.is_mapping_ok(i as u8, i as u8),
                "identity mapping {}->{} should be allowed", i, i);
        }
    }

    #[test]
    fn test_puzzle_disabled_word_not_in_dict() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "", true, true);

        assert!(!puzzle.words.is_empty());
        for w in &puzzle.words {
            assert!(!w.enabled, "word should be disabled");
        }
    }

    #[test]
    fn test_puzzle_no_words_for_empty_text() {
        let dict = Dict::from_static(crate::resources::dictionary()).unwrap();
        let puzzle = Puzzle::new(dict, "", "", true, true);
        assert!(puzzle.words.is_empty());
    }

    #[test]
    fn test_only_alpha_characters_empty() {
        let result = Puzzle::only_alpha_characters("");
        assert_eq!(result, "");
    }

    #[test]
    fn test_only_alpha_characters_only_special() {
        let result = Puzzle::only_alpha_characters("123!@#");
        assert_eq!(result, "");
    }

    #[test]
    fn test_only_alpha_space_characters_empty() {
        let result = Puzzle::only_alpha_space_characters("");
        assert_eq!(result, "");
    }

    #[test]
    fn test_only_alpha_space_characters_only_punct() {
        let result = Puzzle::only_alpha_space_characters("!?.,;:-()");
        assert_eq!(result, "");
    }

    #[test]
    fn test_only_alpha_space_characters_consecutive_separators() {
        let result = Puzzle::only_alpha_space_characters("A..B..C");
        assert_eq!(result, "A B C");
    }
}
