use std::fmt;

const LOG_TABLE_256: [u8; 256] = [
    0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,
    4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
    5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
    5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
    6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
    6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
    6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
    6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
];

#[derive(Clone, Debug)]
pub struct MapSet {
    pub set: Vec<u64>,
    pub size: usize,
}

impl MapSet {
    pub fn new(size: usize) -> Self {
        MapSet {
            set: vec![0u64; size],
            size,
        }
    }

    pub fn new_from_map(map: &[i32]) -> Self {
        let size = map.len();
        let mut set = vec![0u64; size];
        for (i, &m) in map.iter().enumerate() {
            if m >= 0 {
                set[i] = 1u64 << (m as u64);
            }
        }
        MapSet { set, size }
    }

    pub fn copy_from(&self) -> Self {
        MapSet {
            set: self.set.clone(),
            size: self.size,
        }
    }

    pub fn set_full_set(&mut self) {
        let full = (1u64 << (self.size + 1)) - 1;
        for v in self.set.iter_mut() {
            *v = full;
        }
    }

    pub fn set_empty_set(&mut self) {
        for v in self.set.iter_mut() {
            *v = 0;
        }
    }

    pub fn forbid_mapping(&mut self, a: u8, b: u8) {
        self.set[a as usize] &= !(1u64 << b);
    }

    pub fn set_mapping(&mut self, a: u8, b: u8) {
        let mask = !(1u64 << b);
        for v in self.set.iter_mut() {
            *v &= mask;
        }
        self.set[a as usize] = 1u64 << b;
    }

    pub fn set_mappings(&mut self, a: &[u8], b: &[u8]) {
        let mut mask = 0u64;
        for &bv in b {
            mask |= 1u64 << bv;
        }
        let not_mask = !mask;
        for v in self.set.iter_mut() {
            *v &= not_mask;
        }
        for i in 0..a.len() {
            self.set[a[i] as usize] = 1u64 << b[i];
        }
    }

    pub fn enable_mappings(&mut self, a: &[u8], b: &[u8]) {
        for i in 0..a.len() {
            self.set[a[i] as usize] |= 1u64 << b[i];
        }
    }

    pub fn is_uniquely_mapped(&self, a: u8) -> bool {
        let v = self.set[a as usize];
        v != 0 && (v & (v - 1)) == 0
    }

    pub fn get_mapping(&self, a: u8) -> Option<u8> {
        let v = self.set[a as usize];
        if v == 0 || (v & (v - 1)) != 0 {
            return None;
        }
        Some(Self::one_hot_log2(v) as u8)
    }

    pub fn is_mapping_ok(&self, a: u8, b: u8) -> bool {
        (self.set[a as usize] & (1u64 << b)) != 0
    }

    pub fn is_mappings_ok(&self, a: &[u8], b: &[u8]) -> bool {
        for i in 0..a.len() {
            if (self.set[a[i] as usize] & (1u64 << b[i])) == 0 {
                return false;
            }
        }
        true
    }

    pub fn intersect_with(&mut self, other: &MapSet) {
        for i in 0..self.size {
            if other.set[i] == 0 {
                continue;
            }
            let old = self.set[i];
            self.set[i] &= other.set[i];
            if self.set[i] == old {
                continue;
            }
            if self.set[i] != 0 && (self.set[i] & (self.set[i] - 1)) == 0 {
                let v = self.set[i];
                let not_v = !v;
                for j in 0..self.size {
                    self.set[j] &= not_v;
                }
                self.set[i] = v;
            }
        }
    }

    pub fn self_reduce(&mut self) {
        let mut seen_pairs = vec![0u64; self.size];
        for i in 0..self.size {
            let b0 = self.set[i];
            let b1 = b0 & b0.wrapping_sub(1);
            let b2 = b1 & b1.wrapping_sub(1);
            if b2 != 0 {
                continue;
            }
            let mut bidx0 = Self::one_hot_log2(b1);
            let mut bidx1 = Self::one_hot_log2(b0 ^ b1);
            if bidx0 > bidx1 {
                std::mem::swap(&mut bidx0, &mut bidx1);
            }
            let mask_bit = 1u64 << bidx1;
            if (seen_pairs[bidx0] & mask_bit) != 0 {
                let not_b0 = !b0;
                for j in 0..self.size {
                    self.set[j] &= not_b0;
                }
                self.set[i] = b0;
            }
            seen_pairs[bidx0] |= mask_bit;
        }
    }

    pub fn make_map(&self) -> Vec<i32> {
        let mut m = vec![0i32; self.size];
        for (i, mv) in m.iter_mut().enumerate() {
            *mv = match self.get_mapping(i as u8) {
                Some(v) => v as i32,
                None => -1,
            };
        }
        m
    }

    pub fn set_to(&mut self, other: &MapSet) {
        self.set.copy_from_slice(&other.set);
    }

    pub fn randomly_permute(&mut self, allow_identity: bool) {
        use rand::Rng;
        let mut rng = rand::thread_rng();
        let mut plain: Vec<u8> = (0..self.size as u8).collect();
        loop {
            for i in (1..self.size).rev() {
                let j = rng.gen_range(0..=i);
                plain.swap(i, j);
            }
            if !allow_identity {
                let ok = plain.iter().enumerate().take(self.size).any(|(i, &v)| v != i as u8);
                if ok {
                    break;
                }
            } else {
                break;
            }
        }
        for (i, &p) in plain.iter().enumerate().take(self.size) {
            self.set[i] = 1u64 << p;
        }
    }

    pub fn has_mappings(&self, c: u8) -> bool {
        self.set[c as usize] != 0
    }

    pub fn one_hot_log2(v: u64) -> usize {
        let v = v as u32;
        let tt = (v >> 16) as usize;
        if tt != 0 {
            let t = (v >> 24) as usize;
            if t != 0 {
                return 24 + LOG_TABLE_256[t] as usize;
            }
            return 16 + LOG_TABLE_256[tt & 0xFF] as usize;
        }
        let t = (v >> 8) as usize;
        if t != 0 {
            return 8 + LOG_TABLE_256[t] as usize;
        }
        LOG_TABLE_256[v as usize] as usize
    }
}

impl fmt::Display for MapSet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        for i in 0..self.size {
            write!(f, "{}: ", (i as u8 + b'A') as char)?;
            for j in 0..self.size {
                if (self.set[i] & (1u64 << j)) != 0 {
                    write!(f, "{}", (j as u8 + b'A') as char)?;
                } else {
                    write!(f, " ")?;
                }
            }
            writeln!(f)?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_new_creates_zeroed_sets() {
        let ms = MapSet::new(26);
        assert_eq!(ms.size, 26);
        assert_eq!(ms.set.len(), 26);
        for &v in &ms.set {
            assert_eq!(v, 0);
        }
    }

    #[test]
    fn test_new_from_map_identity() {
        let map: Vec<i32> = (0..26).collect();
        let ms = MapSet::new_from_map(&map);
        for i in 0..26 {
            assert_eq!(ms.set[i], 1u64 << i);
        }
    }

    #[test]
    fn test_new_from_map_negative_value() {
        let map = vec![-1, 0, 1];
        let ms = MapSet::new_from_map(&map);
        assert_eq!(ms.set[0], 0);
        assert_eq!(ms.set[1], 1);
        assert_eq!(ms.set[2], 2);
    }

    #[test]
    fn test_copy_from_independent() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 42;
        let copy = ms.copy_from();
        assert_eq!(copy.set[0], 42);
        ms.set[0] = 99;
        assert_eq!(copy.set[0], 42);
        assert_eq!(copy.size, 5);
    }

    #[test]
    fn test_set_full_set() {
        let mut ms = MapSet::new(26);
        ms.set_full_set();
        let expected = (1u64 << 27) - 1;
        for &v in &ms.set {
            assert_eq!(v, expected);
        }
    }

    #[test]
    fn test_set_empty_set_after_full() {
        let mut ms = MapSet::new(5);
        ms.set_full_set();
        ms.set_empty_set();
        for &v in &ms.set {
            assert_eq!(v, 0);
        }
    }

    #[test]
    fn test_forbid_mapping_clears_bit() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b111;
        ms.forbid_mapping(0, 1);
        assert_eq!(ms.set[0], 0b101);
    }

    #[test]
    fn test_forbid_mapping_noop_if_not_set() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b101;
        ms.forbid_mapping(0, 1);
        assert_eq!(ms.set[0], 0b101);
    }

    #[test]
    fn test_set_mapping_sets_and_clears_others() {
        let mut ms = MapSet::new(5);
        ms.set_full_set();
        ms.set_mapping(2, 3);
        for i in 0..5 {
            if i == 2 {
                assert_eq!(ms.set[i], 1u64 << 3, "row {} should have only bit 3", i);
            } else {
                assert_eq!(ms.set[i] & (1u64 << 3), 0, "row {} should not have bit 3", i);
            }
        }
    }

    #[test]
    fn test_is_uniquely_mapped() {
        let mut ms = MapSet::new(5);
        assert!(!ms.is_uniquely_mapped(0));
        ms.set[0] = 0b1010;
        assert!(!ms.is_uniquely_mapped(0));
        ms.set[0] = 1u64 << 3;
        assert!(ms.is_uniquely_mapped(0));
    }

    #[test]
    fn test_get_mapping_none_if_empty() {
        let ms = MapSet::new(5);
        assert_eq!(ms.get_mapping(0), None);
    }

    #[test]
    fn test_get_mapping_none_if_not_unique() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 0b101;
        assert_eq!(ms.get_mapping(0), None);
    }

    #[test]
    fn test_get_mapping_returns_value() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 1u64 << 4;
        assert_eq!(ms.get_mapping(0), Some(4));
    }

    #[test]
    fn test_is_mapping_ok() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 0b101;
        assert!(ms.is_mapping_ok(0, 0));
        assert!(ms.is_mapping_ok(0, 2));
        assert!(!ms.is_mapping_ok(0, 1));
    }

    #[test]
    fn test_is_mappings_ok_all_pass() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 1u64 << 1;
        ms.set[1] = 1u64 << 3;
        assert!(ms.is_mappings_ok(&[0, 1], &[1, 3]));
    }

    #[test]
    fn test_is_mappings_ok_one_fails() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 1u64 << 1;
        ms.set[1] = 1u64 << 3;
        assert!(!ms.is_mappings_ok(&[0, 1], &[1, 4]));
    }

    #[test]
    fn test_is_mappings_ok_empty() {
        let ms = MapSet::new(5);
        assert!(ms.is_mappings_ok(&[], &[]));
    }

    #[test]
    fn test_set_mappings_batch() {
        let mut ms = MapSet::new(26);
        ms.set_full_set();
        ms.set_mappings(&[0, 1, 2], &[5, 10, 15]);
        assert_eq!(ms.set[0], 1u64 << 5);
        assert_eq!(ms.set[1], 1u64 << 10);
        assert_eq!(ms.set[2], 1u64 << 15);
        for i in 3..26 {
            assert_eq!(ms.set[i] & (1u64 << 5), 0, "bit 5 should be cleared from row {}", i);
            assert_eq!(ms.set[i] & (1u64 << 10), 0, "bit 10 should be cleared from row {}", i);
            assert_eq!(ms.set[i] & (1u64 << 15), 0, "bit 15 should be cleared from row {}", i);
        }
    }

    #[test]
    fn test_enable_mappings_adds_bits() {
        let mut ms = MapSet::new(3);
        ms.enable_mappings(&[0, 1], &[0, 1]);
        assert_eq!(ms.set[0], 1);
        assert_eq!(ms.set[1], 2);
        assert_eq!(ms.set[2], 0);
        ms.enable_mappings(&[0], &[2]);
        assert_eq!(ms.set[0], 5);
    }

    #[test]
    fn test_make_map_returns_mappings() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 1u64 << 2;
        ms.set[1] = 1u64 << 4;
        ms.set[3] = 1u64 << 0;
        let m = ms.make_map();
        assert_eq!(m, vec![2, 4, -1, 0, -1]);
    }

    #[test]
    fn test_has_mappings() {
        let mut ms = MapSet::new(3);
        assert!(!ms.has_mappings(0));
        ms.set[0] = 1;
        assert!(ms.has_mappings(0));
    }

    #[test]
    fn test_has_mappings_after_forbid() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 1;
        ms.forbid_mapping(0, 0);
        assert!(!ms.has_mappings(0));
    }

    #[test]
    fn test_intersect_with_skips_zero_rows() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b111;
        let other = MapSet::new(3);
        ms.intersect_with(&other);
        assert_eq!(ms.set[0], 0b111);
    }

    #[test]
    fn test_intersect_with_simple_and() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b111;
        ms.set[1] = 0b110;
        let mut other = MapSet::new(3);
        other.set[0] = 0b101;
        other.set[1] = 0b011;
        ms.intersect_with(&other);
        assert_eq!(ms.set[0], 0b101);
        assert_eq!(ms.set[1], 0b010);
    }

    #[test]
    fn test_intersect_with_propagates_unique() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b101;
        ms.set[1] = 0b101;
        ms.set[2] = 0b110;
        let mut other = MapSet::new(3);
        other.set[0] = 0b100;
        other.set[1] = 0b111;
        other.set[2] = 0b111;
        ms.intersect_with(&other);
        assert_eq!(ms.set[0], 0b100);
        assert_eq!(ms.set[1], 0b001);
        assert_eq!(ms.set[2], 0b010);
    }

    #[test]
    fn test_intersect_with_no_propagation_if_unchanged() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b001;
        ms.set[1] = 0b111;
        let mut other = MapSet::new(3);
        other.set[0] = 0b001;
        other.set[1] = 0b110;
        ms.intersect_with(&other);
        assert_eq!(ms.set[0], 0b001);
        assert_eq!(ms.set[1], 0b110);
    }

    #[test]
    fn test_self_reduce_eliminates_pair_from_subsequent_rows() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 0b11;
        ms.set[1] = 0b11;
        ms.set[2] = 0b111;
        ms.set[3] = 0b1111;
        ms.self_reduce();
        // first occurrence (row 0) gets cleared, only row 1 (current) is restored
        assert_eq!(ms.set[0], 0);
        assert_eq!(ms.set[1], 0b11);
        assert_eq!(ms.set[2], 0b100);
        assert_eq!(ms.set[3], 0b1100);
    }

    #[test]
    fn test_self_reduce_skips_rows_with_more_than_two_bits() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b111;
        ms.set[1] = 0b111;
        ms.self_reduce();
        assert_eq!(ms.set[0], 0b111);
        assert_eq!(ms.set[1], 0b111);
    }

    #[test]
    fn test_self_reduce_noop_with_single_pair() {
        let mut ms = MapSet::new(3);
        ms.set[0] = 0b11;
        ms.set[2] = 0b100;
        ms.self_reduce();
        assert_eq!(ms.set[0], 0b11);
        assert_eq!(ms.set[2], 0b100);
    }

    #[test]
    fn test_self_reduce_multiple_independent_pairs() {
        let mut ms = MapSet::new(6);
        ms.set[0] = 0b11;   // pair {0,1}
        ms.set[1] = 0b11;   // dupe (clears all, restores i=1)
        ms.set[2] = 0b1100; // pair {2,3}
        ms.set[3] = 0b1100; // dupe (clears all, restores i=3)
        ms.set[4] = 0b111111;
        ms.self_reduce();
        // first occurrences (rows 0,2) cleared; only current rows (1,3) restored
        assert_eq!(ms.set[0], 0);
        assert_eq!(ms.set[1], 0b11);
        assert_eq!(ms.set[2], 0);
        assert_eq!(ms.set[3], 0b1100);
        assert_eq!(ms.set[4], 0b110000);
    }

    #[test]
    fn test_self_reduce_clears_earlier_rows_too() {
        let mut ms = MapSet::new(4);
        ms.set[2] = 0b11;  // earlier row has pair {0,1}
        ms.set[1] = 0b100; // unique, should keep bit
        ms.set[3] = 0b11;  // duplicate pair with row 2
        ms.self_reduce();
        assert_eq!(ms.set[1], 0b100, "row 1 should retain its unique mapping");
        // row 2 is cleared because only the current (row 3) gets restored
        assert_eq!(ms.set[3], 0b11, "row 3 should keep its pair");
        // bit 0 and 1 should be cleared from rows 0 and 2
        assert_eq!(ms.set[0], 0, "row 0 should have bits 0,1 cleared");
        assert_eq!(ms.set[2], 0, "row 2 should have bits 0,1 cleared");
    }

    #[test]
    fn test_one_hot_log2_small_values() {
        assert_eq!(MapSet::one_hot_log2(1u64 << 0), 0);
        assert_eq!(MapSet::one_hot_log2(1u64 << 1), 1);
        assert_eq!(MapSet::one_hot_log2(1u64 << 5), 5);
        assert_eq!(MapSet::one_hot_log2(1u64 << 10), 10);
        assert_eq!(MapSet::one_hot_log2(1u64 << 25), 25);
    }

    #[test]
    fn test_one_hot_log2_ignores_upper_32_bits() {
        let v = (1u64 << 20) | (1u64 << 40);
        assert_eq!(MapSet::one_hot_log2(v), 20);
    }

    #[test]
    fn test_one_hot_log2_boundary_values() {
        for i in 0..26 {
            assert_eq!(MapSet::one_hot_log2(1u64 << i), i, "log2(1<<{})", i);
        }
    }

    #[test]
    fn test_set_mapping_then_get_mapping_roundtrip() {
        let mut ms = MapSet::new(26);
        ms.set_mapping(15, 20);
        assert_eq!(ms.get_mapping(15), Some(20));
    }

    #[test]
    fn test_forbid_mapping_effect_on_is_mapping_ok() {
        let mut ms = MapSet::new(5);
        ms.set_full_set();
        assert!(ms.is_mapping_ok(3, 2));
        ms.forbid_mapping(3, 2);
        assert!(!ms.is_mapping_ok(3, 2));
    }

    #[test]
    fn test_clone_derive() {
        let mut ms = MapSet::new(5);
        ms.set[0] = 42;
        let cloned = ms.clone();
        assert_eq!(cloned.set[0], 42);
        assert_eq!(cloned.size, 5);
    }
}
