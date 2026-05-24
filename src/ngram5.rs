use std::collections::HashMap;
use std::io::Read;

use flate2::read::GzDecoder;

const MAGIC: i32 = -1_438_373_319;

pub struct Sampler<T> {
    proportions: Vec<f64>,
    objs: Vec<T>,
    total_proportion: f64,
}

impl<T> Sampler<T> {
    pub fn new(proportions: Vec<f64>, objs: Vec<T>) -> Self {
        let total_proportion: f64 = proportions.iter().sum();
        Sampler {
            proportions,
            objs,
            total_proportion,
        }
    }

    pub fn sample(&self, v: f64) -> &T {
        let mut v = v * self.total_proportion;
        for i in 0..self.proportions.len() {
            v -= self.proportions[i];
            if v < 0.0 {
                return &self.objs[i];
            }
        }
        &self.objs[0]
    }

    #[cfg(test)]
    pub fn items(&self) -> &[T] {
        &self.objs
    }
}

pub fn make_pattern(s: &str) -> String {
    let mut map = [0u8; 256];
    let mut next = b'A';
    let mut out = String::with_capacity(s.len());
    for &b in s.as_bytes() {
        if b == b' ' {
            out.push(' ');
            continue;
        }
        if map[b as usize] == 0 {
            map[b as usize] = next;
            next += 1;
        }
        out.push(map[b as usize] as char);
    }
    out
}

pub fn read_ngram5_from_bytes(data: &[u8]) -> Result<HashMap<String, Sampler<String>>, std::io::Error> {
    let decoder = GzDecoder::new(data);
    let mut reader = std::io::BufReader::new(decoder);
    read_ngram5_inner(&mut reader)
}

fn read_ngram5_inner<R: Read>(reader: &mut R) -> Result<HashMap<String, Sampler<String>>, std::io::Error> {
    let mut buf4 = [0u8; 4];
    reader.read_exact(&mut buf4)?;
    let magic = i32::from_be_bytes(buf4);
    if magic != MAGIC {
        return Err(std::io::Error::new(std::io::ErrorKind::InvalidData,
            format!("bad ngram5 magic: got {magic:#x}, expected {MAGIC:#x}")));
    }

    reader.read_exact(&mut buf4)?;
    let order = i32::from_be_bytes(buf4) as usize;

    reader.read_exact(&mut buf4)?;
    let npatterns = i32::from_be_bytes(buf4) as usize;

    let mut map = HashMap::with_capacity(npatterns);
    let mut buf8 = [0u8; 8];

    for _ in 0..npatterns {
        reader.read_exact(&mut buf4)?;
        let ngrams = i32::from_be_bytes(buf4) as usize;

        let mut proportions = Vec::with_capacity(ngrams);
        let mut strings = Vec::with_capacity(ngrams);

        for _ in 0..ngrams {
            let mut text = vec![0u8; order];
            reader.read_exact(&mut text)?;
            let s = String::from_utf8(text)
                .map_err(|e| std::io::Error::new(std::io::ErrorKind::InvalidData, e))?;
            assert_eq!(s.len(), order);

            reader.read_exact(&mut buf8)?;
            let count = f64::from_be_bytes(buf8);

            strings.push(s);
            proportions.push(count);
        }

        let pat = make_pattern(&strings[0]);
        map.insert(pat, Sampler::new(proportions, strings));
    }

    Ok(map)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::resources;

    #[test]
    fn test_sampler() {
        let s = Sampler::new(vec![1.0, 2.0, 3.0], vec!["a", "b", "c"]);
        assert_eq!(*s.sample(0.0), "a");
        assert_eq!(*s.sample(1.0 / 6.0 - 1e-12), "a");
        assert_eq!(*s.sample(1.0 / 6.0), "b");
        assert_eq!(*s.sample(0.5), "c");
        assert_eq!(*s.sample(0.99), "c");
    }

    #[test]
    fn test_make_pattern() {
        assert_eq!(make_pattern("AAAAA"), "AAAAA");
        assert_eq!(make_pattern("HELLO"), "ABCCD");
        assert_eq!(make_pattern("HELLO "), "ABCCD ");
        assert_eq!(make_pattern("AAA BB"), "AAA BB");
        assert_eq!(make_pattern("AABAA"), "AABAA");
        assert_eq!(make_pattern("abcde"), "ABCDE");
        assert_eq!(make_pattern("a b c"), "A B C");
        assert_eq!(make_pattern("THAT"), "ABCA");
    }

    fn check_data(map: &HashMap<String, Sampler<String>>, order: usize) {
        assert!(!map.is_empty(), "map should be non-empty");
        for (pat, sampler) in map {
            for c in pat.chars() {
                assert!(
                    c.is_ascii_uppercase() || c == ' ',
                    "pattern {pat:?} has invalid char {c:?}"
                );
            }
            for s in sampler.items() {
                assert_eq!(s.len(), order, "sample {s:?} has wrong length");
            }
        }
    }

    #[test]
    fn test_gramfreq5() {
        let map = read_ngram5_from_bytes(resources::gramfreq_ws()).expect("failed to read gramfreq5.stb");
        check_data(&map, 5);
    }

    #[test]
    fn test_gramfreq5ns() {
        let map = read_ngram5_from_bytes(resources::gramfreq_ns()).expect("failed to read gramfreq5ns.stb");
        check_data(&map, 5);
    }
}
