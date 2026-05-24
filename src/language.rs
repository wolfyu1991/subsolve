use std::sync::OnceLock;

use crate::mapset::MapSet;
use crate::ngram4::Ngram4;
use crate::resources;

static TETRAGRAMS: OnceLock<Ngram4> = OnceLock::new();
static TETRAGRAMS_NS: OnceLock<Ngram4> = OnceLock::new();

pub trait Language: Send + Sync {
    fn letter_count(&self) -> usize;
    fn char_to_byte(&self, c: char) -> u8;
    fn byte_to_char(&self, b: u8) -> char;

    fn string_to_bytes(&self, s: &str) -> Vec<u8> {
        let mut bytes = Vec::new();
        for c in s.chars() {
            let b = self.char_to_byte(c);
            if b < self.letter_count() as u8 {
                bytes.push(b);
            }
        }
        bytes
    }

    fn bytes_to_string(&self, bytes: &[u8]) -> String {
        let mut out = String::with_capacity(bytes.len());
        for &b in bytes {
            out.push(self.byte_to_char(b));
        }
        out
    }

    fn cipher_text_to_words(&self, ciphertext: &str) -> Vec<crate::word::Word> {
        let mut words = Vec::new();
        for token in ciphertext.split(|c: char| c.is_whitespace() || "-.,;/+()\\?".contains(c)) {
            if token.is_empty() {
                continue;
            }
            let cleaned: String = token
                .chars()
                .filter(|&c| {
                    let b = self.char_to_byte(c);
                    b < self.letter_count() as u8
                })
                .collect();
            let bs = self.string_to_bytes(&cleaned);
            if bs.is_empty() {
                continue;
            }
            if token.starts_with('^') {
                continue;
            }
            words.push(crate::word::Word::new(cleaned, bs));
        }
        words
    }

    fn tetragrams(&self) -> &Ngram4 {
        TETRAGRAMS
            .get_or_init(|| Ngram4::from_raw(resources::tetragrams_raw()))
    }

    fn tetragrams_ns(&self) -> &Ngram4 {
        TETRAGRAMS_NS
            .get_or_init(|| Ngram4::from_raw(resources::tetragrams_ns_raw()))
    }

    fn compute_candidate_score(&self, candidate: &[u8]) -> f64 {
        let ngrams = self.tetragrams();
        let mut ma = 27usize;
        let mut mb = 27usize;
        let mut mc = 26usize;
        let mut score = 0.0f64;
        for &md in candidate {
            score += ngrams.get_log_prob(ma as u8, mb as u8, mc as u8, md);
            ma = mb;
            mb = mc;
            mc = md as usize;
        }
        score += ngrams.get_log_prob(ma as u8, mb as u8, mc as u8, 26u8);
        score
    }

    fn compute_score_mapset(&self, cipher_bytes: &[u8], mapset: &MapSet) -> f64 {
        let mut map = [0i32; 28];
        for (i, m) in map.iter_mut().enumerate().take(26) {
            *m = match mapset.get_mapping(i as u8) {
                Some(v) => v as i32,
                None => -1,
            };
        }
        map[26] = 26;
        map[27] = 27;
        self.compute_score_map(cipher_bytes, &map)
    }

    fn compute_score_map(&self, cipher_bytes: &[u8], map: &[i32]) -> f64 {
        let ngrams = self.tetragrams();
        let len = cipher_bytes.len();
        let c0 = if len > 2 { cipher_bytes[len - 2] as usize } else { 27 };
        let c1 = if len > 1 { cipher_bytes[len - 1] as usize } else { 27 };
        let c2 = 26usize;
        let mut m0 = map[c0];
        if m0 < 0 { m0 = 27; }
        let mut m1 = map[c1];
        if m1 < 0 { m1 = 27; }
        let mut m2 = map[c2];
        if m2 < 0 { m2 = 27; }
        let mut score = 0.0f64;
        for &c3 in cipher_bytes {
            let mut m3 = map[c3 as usize];
            if m3 < 0 {
                m3 = 27;
                score += -20.0;
            }
            score += ngrams.get_log_prob(m0 as u8, m1 as u8, m2 as u8, m3 as u8);
            m0 = m1;
            m1 = m2;
            m2 = m3;
        }
        score
    }

    fn cipher_text_to_cipher_bytes(&self, text: &str) -> Vec<u8> {
        let mut bytes = Vec::new();
        let mut last_was_space = false;
        for c in text.chars() {
            let b = self.char_to_byte(c);
            if b == self.letter_count() as u8 && last_was_space {
                continue;
            }
            last_was_space = b == self.letter_count() as u8;
            bytes.push(b);
        }
        bytes
    }

    fn translate_string(
        &self,
        ciphertext: &str,
        map: &MapSet,
        spaces: Option<&[i32]>,
    ) -> String {
        let mut out = String::new();
        let mut spacepos = 0;
        let mut space_pending = false;
        for c in ciphertext.chars() {
            if spaces.is_some() && c == ' ' {
                continue;
            }
            let cb = self.char_to_byte(c);
            if cb < self.letter_count() as u8 {
                if space_pending {
                    out.push(' ');
                    space_pending = false;
                }
                if let Some(m) = map.get_mapping(cb) {
                    if c.is_lowercase() {
                        out.push((b'a' + m) as char);
                    } else {
                        out.push((b'A' + m) as char);
                    }
                } else {
                    out.push('~');
                }
                if let Some(sp) = spaces {
                    if sp[spacepos] > 0 {
                        space_pending = true;
                    }
                }
                spacepos += 1;
            } else {
                out.push(c);
            }
        }
        out
    }
}

pub struct EnglishLanguage;

impl Default for EnglishLanguage {
    fn default() -> Self {
        Self::new()
    }
}

impl EnglishLanguage {
    pub fn new() -> Self {
        EnglishLanguage
    }
}

impl Language for EnglishLanguage {
    fn letter_count(&self) -> usize {
        26
    }

    fn char_to_byte(&self, c: char) -> u8 {
        if c == ' ' {
            return 26;
        }
        let u = c.to_ascii_uppercase();
        if !u.is_ascii_uppercase() {
            return 26;
        }
        u as u8 - b'A'
    }

    fn byte_to_char(&self, b: u8) -> char {
        match b {
            255 => '~',
            26 => ' ',
            v if v < 26 => (b'A' + v) as char,
            _ => '?',
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_char_to_byte() {
        let lang = EnglishLanguage::new();
        assert_eq!(lang.char_to_byte('A'), 0);
        assert_eq!(lang.char_to_byte('Z'), 25);
        assert_eq!(lang.char_to_byte(' '), 26);
        assert_eq!(lang.char_to_byte('~'), 26);
        assert_eq!(lang.char_to_byte('a'), 0);
        assert_eq!(lang.char_to_byte('z'), 25);
        assert_eq!(lang.char_to_byte('1'), 26);
    }

    #[test]
    fn test_byte_to_char() {
        let lang = EnglishLanguage::new();
        assert_eq!(lang.byte_to_char(0), 'A');
        assert_eq!(lang.byte_to_char(25), 'Z');
        assert_eq!(lang.byte_to_char(26), ' ');
        assert_eq!(lang.byte_to_char(255), '~');
    }

    #[test]
    fn test_string_to_bytes() {
        let lang = EnglishLanguage::new();
        assert_eq!(lang.string_to_bytes("ABC"), vec![0, 1, 2]);
        assert_eq!(lang.string_to_bytes("hello"), vec![7, 4, 11, 11, 14]);
        assert_eq!(lang.string_to_bytes(""), vec![]);
        assert_eq!(lang.string_to_bytes("HELLO WORLD"), vec![7, 4, 11, 11, 14, 22, 14, 17, 11, 3]);
    }

    #[test]
    fn test_bytes_to_string() {
        let lang = EnglishLanguage::new();
        assert_eq!(lang.bytes_to_string(&[0, 1, 2]), "ABC");
        assert_eq!(lang.bytes_to_string(&[7, 4, 11, 11, 14]), "HELLO");
    }

    #[test]
    fn test_cipher_text_to_words() {
        let lang = EnglishLanguage::new();
        let words = lang.cipher_text_to_words("HELLO WORLD");
        assert_eq!(words.len(), 2);
        assert_eq!(words[0].text, "HELLO");
        assert_eq!(words[1].text, "WORLD");

        // words starting with ^ should be skipped
        let words = lang.cipher_text_to_words("HELLO ^SKIP");
        assert_eq!(words.len(), 1);
        assert_eq!(words[0].text, "HELLO");
    }

    #[test]
    fn test_cipher_text_to_cipher_bytes() {
        let lang = EnglishLanguage::new();
        // Each letter becomes 0-25, space becomes 26
        let result = lang.cipher_text_to_cipher_bytes("A B");
        assert_eq!(result, vec![0, 26, 1]);

        // Consecutive spaces should collapse
        let result = lang.cipher_text_to_cipher_bytes("A  B");
        assert_eq!(result, vec![0, 26, 1]);
    }

    #[test]
    fn test_translate_string_simple() {
        let lang = EnglishLanguage::new();
        let mut map = MapSet::new(26);
        map.set_full_set();
        map.set_mapping(0, 1); // A→B
        let result = lang.translate_string("A", &map, None);
        assert_eq!(result, "B");
    }

    #[test]
    fn test_translate_string_unmapped() {
        let lang = EnglishLanguage::new();
        let mut map = MapSet::new(26);
        map.set_empty_set();
        let result = lang.translate_string("A", &map, None);
        assert_eq!(result, "~");
    }

    #[test]
    fn test_translate_string_preserves_case() {
        let lang = EnglishLanguage::new();
        let mut map = MapSet::new(26);
        map.set_full_set();
        map.set_mapping(0, 2); // A→C
        assert_eq!(lang.translate_string("a", &map, None), "c");
        assert_eq!(lang.translate_string("A", &map, None), "C");
    }

    #[test]
    fn test_translate_string_with_spaces() {
        let lang = EnglishLanguage::new();
        let mut map = MapSet::new(26);
        map.set_full_set();
        map.set_mapping(0, 1); // A→B
        map.set_mapping(1, 0); // B→A
        let spaces = vec![0, 1];
        let result = lang.translate_string("AB", &map, Some(&spaces));
        assert_eq!(result, "BA");
    }
}
