use proptest::prelude::*;
use subsolve::language::Language;

proptest! {
    #[test]
    fn test_mapset_set_mapping_get_mapping_roundtrip(cipher in 0u8..26u8, plain in 0u8..26u8) {
        let mut ms = subsolve::mapset::MapSet::new(26);
        ms.set_mapping(cipher, plain);
        assert_eq!(ms.get_mapping(cipher), Some(plain));
    }

    #[test]
    fn test_mapset_symmetric_forbid(cipher in 0u8..26u8, plain in 0u8..26u8) {
        let mut ms = subsolve::mapset::MapSet::new(26);
        ms.forbid_mapping(cipher, plain);
        assert!(!ms.is_mapping_ok(cipher, plain));
        ms.forbid_mapping(cipher, plain);
        assert!(!ms.is_mapping_ok(cipher, plain));
    }

    #[test]
    fn test_mapset_new_is_empty(cipher in 0u8..26u8, plain in 0u8..26u8) {
        let ms = subsolve::mapset::MapSet::new(26);
        assert!(!ms.is_mapping_ok(cipher, plain));
    }

    #[test]
    fn test_mapset_full_set_is_all_ok(cipher in 0u8..26u8, plain in 0u8..26u8) {
        let mut ms = subsolve::mapset::MapSet::new(26);
        ms.set_full_set();
        assert!(ms.is_mapping_ok(cipher, plain));
    }

    #[test]
    fn test_word_make_pattern_len_matches_input(word in prop::collection::vec(0u8..=255u8, 1..=100)) {
        let pattern = subsolve::word::Word::make_pattern(&word);
        assert_eq!(pattern.len(), word.len());
    }

    #[test]
    fn test_word_make_pattern_indices_in_range(word in prop::collection::vec(0u8..=255u8, 1..=100)) {
        let pattern = subsolve::word::Word::make_pattern(&word);
        for (i, &p) in pattern.iter().enumerate() {
            assert!(p <= i as u8, "pattern index {} at position {} exceeds max possible {}", p, i, i);
        }
    }

    #[test]
    fn test_word_make_pattern_duplicate_bytes_have_same_index(
        word in prop::collection::vec(0u8..=10u8, 1..=50)
    ) {
        let pattern = subsolve::word::Word::make_pattern(&word);
        for i in 0..word.len() {
            for j in i + 1..word.len() {
                if word[i] == word[j] {
                    assert_eq!(pattern[i], pattern[j],
                        "same byte {} at positions {} and {} should have same pattern index",
                        word[i], i, j);
                }
            }
        }
    }

    #[test]
    fn test_language_char_byte_roundtrip_uppercase(c in prop::char::range('A', 'Z')) {
        let lang = subsolve::language::EnglishLanguage::new();
        let byte = lang.char_to_byte(c);
        assert!(byte < 26);
        let back = (byte + b'A') as char;
        assert_eq!(c, back, "char {} -> byte {} -> char {}", c, byte, back);
    }

    #[test]
    fn test_language_char_to_byte_space(c in prop::sample::select(vec![' ', '\t', '\n', '.', ',', '!', '?', '0', '9'])) {
        let lang = subsolve::language::EnglishLanguage::new();
        let byte = lang.char_to_byte(c);
        assert_eq!(byte, 26, "non-alpha '{}' should map to 26", c);
    }
}
