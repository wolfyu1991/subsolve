use std::cmp::Ordering;
use std::collections::HashMap;
use std::io::{self, Cursor, Read, Seek, SeekFrom};

use crate::word::Word;

#[derive(Clone)]
pub struct Dict {
    data: Vec<u8>,
    low_off: u64,
    #[cfg(test)]
    properties: HashMap<String, String>,
    pattern_cache: HashMap<Vec<u8>, Vec<Vec<u8>>>,
}

impl Dict {
    pub fn from_file(path: &str) -> io::Result<Self> {
        let data = std::fs::read(path)?;
        Self::from_bytes(data)
    }

    pub fn from_static(data: &'static [u8]) -> io::Result<Self> {
        Self::from_bytes(data.to_vec())
    }

    fn from_bytes(data: Vec<u8>) -> io::Result<Self> {
        let mut cursor = Cursor::new(data.as_slice());

        let mut buf32 = [0u8; 4];
        cursor.read_exact(&mut buf32)?;
        let magic = i32::from_be_bytes(buf32);
        if magic != 233573869 {
            return Err(io::Error::new(io::ErrorKind::InvalidData, "Not a dictionary file"));
        }

        cursor.read_exact(&mut buf32)?;
        let nprops = i32::from_be_bytes(buf32);

        #[cfg(test)]
        let mut properties = HashMap::new();

        for _ in 0..nprops {
            let key = read_utf(&mut cursor)?;
            let value = read_utf(&mut cursor)?;
            #[cfg(test)]
            properties.insert(key, value);
            #[cfg(not(test))]
            let _ = (key, value);
        }

        let low_off = cursor.position();

        Ok(Dict {
            data,
            low_off,
            #[cfg(test)]
            properties,
            pattern_cache: HashMap::new(),
        })
    }

    #[cfg(test)]
    pub fn get_property(&self, key: &str) -> Option<&str> {
        self.properties.get(key).map(|s| s.as_str())
    }

    pub fn lookup(&mut self, pattern: &[u8]) -> Vec<Vec<u8>> {
        if let Some(cached) = self.pattern_cache.get(pattern) {
            return cached.clone();
        }
        let matches = self.lookup_inner(pattern);
        self.pattern_cache.insert(pattern.to_vec(), matches.clone());
        matches
    }

    fn lookup_inner(&mut self, pattern: &[u8]) -> Vec<Vec<u8>> {
        let mut cursor = Cursor::new(self.data.as_slice());
        let filesize = self.data.len() as u64;

        let mut low = self.low_off;
        let mut high = filesize - 1;
        let mut lastpos = -1i64;
        let mut matches: Vec<Vec<u8>> = Vec::new();

        loop {
            let pos = (low + high) / 2;
            if pos as i64 == lastpos {
                return matches;
            }
            lastpos = pos as i64;

            if cursor.seek(SeekFrom::Start(pos)).is_err() {
                return matches;
            }

            let mut byte_buf = [0u8; 1];
            loop {
                match cursor.read(&mut byte_buf) {
                    Ok(0) => {
                        high = pos;
                        break;
                    }
                    Err(_) => {
                        high = pos;
                        break;
                    }
                    Ok(_) => {
                        if byte_buf[0] == 0xFF {
                            break;
                        }
                    }
                }
            }

            if byte_buf[0] != 0xFF {
                continue;
            }

            if cursor.read(&mut byte_buf).is_err() {
                return matches;
            }
            let word_len = byte_buf[0] as usize;

            let mut nwords_buf = [0u8; 2];
            if cursor.read_exact(&mut nwords_buf).is_err() {
                return matches;
            }
            let n_words = u16::from_be_bytes(nwords_buf);

            let mut this_word = vec![0u8; word_len];
            if cursor.read_exact(&mut this_word).is_err() {
                return matches;
            }

            let this_pattern = Word::make_pattern(&this_word);
            let cmp = Word::compare_arrays(&this_pattern, pattern);

            if cmp != Ordering::Equal && !matches.is_empty() {
                return matches;
            }

            match cmp {
                Ordering::Less => {
                    low = pos;
                }
                Ordering::Equal => {
                    matches.push(this_word);
                    for _ in 0..(n_words - 1) {
                        let mut word = vec![0u8; word_len];
                        if cursor.read_exact(&mut word).is_err() {
                            break;
                        }
                        matches.push(word);
                    }
                    return matches;
                }
                Ordering::Greater => {
                    high = pos;
                }
            }
        }
    }

}

fn read_utf<R: Read>(reader: &mut R) -> io::Result<String> {
    let mut len_buf = [0u8; 2];
    reader.read_exact(&mut len_buf)?;
    let byte_len = u16::from_be_bytes(len_buf) as usize;
    let mut bytes = vec![0u8; byte_len];
    reader.read_exact(&mut bytes)?;
    String::from_utf8(bytes).map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::language::EnglishLanguage;
    use crate::language::Language;
    use crate::resources;

    #[test]
    fn test_dict_open() {
        let dict = Dict::from_static(resources::dictionary()).unwrap();
        assert_eq!(dict.get_property("langclass"), Some("decrypto.EnglishLanguage"));
    }

    #[test]
    fn test_dict_lookup_hello() {
        let mut dict = Dict::from_static(resources::dictionary()).unwrap();
        let lang = EnglishLanguage::new();
        let hello_bytes = lang.string_to_bytes("HELLO");
        let pattern = Word::make_pattern(&hello_bytes);
        let results = dict.lookup(&pattern);
        assert!(!results.is_empty(), "HELLO should be in dictionary");
        assert!(results.contains(&hello_bytes), "results should contain HELLO: {:?}", results);
    }

    #[test]
    fn test_dict_lookup_nonexistent() {
        let mut dict = Dict::from_static(resources::dictionary()).unwrap();
        let pattern = vec![0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];
        let results = dict.lookup(&pattern);
        assert!(results.is_empty());
    }

    #[test]
    fn test_dict_caching() {
        let mut dict = Dict::from_static(resources::dictionary()).unwrap();
        let pattern = vec![0, 1, 2];
        let r1 = dict.lookup(&pattern);
        let r2 = dict.lookup(&pattern);
        assert_eq!(r1.len(), r2.len());
    }
}
