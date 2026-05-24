use std::sync::OnceLock;

pub fn dictionary() -> &'static [u8] {
    include_bytes!(concat!(env!("OUT_DIR"), "/dict.bin")).as_slice()
}

fn tetragram_from_bytes(bytes: &[u8]) -> Vec<f32> {
    assert_eq!(bytes.len() % 4, 0, "tetragram size not multiple of 4");
    bytes
        .chunks_exact(4)
        .map(|c| f32::from_ne_bytes([c[0], c[1], c[2], c[3]]))
        .collect()
}

pub fn tetragrams_raw() -> &'static [f32] {
    static DATA: OnceLock<Vec<f32>> = OnceLock::new();
    DATA.get_or_init(|| {
        tetragram_from_bytes(
            include_bytes!(concat!(env!("OUT_DIR"), "/tetra.raw")),
        )
    });
    DATA.get().unwrap()
}

pub fn tetragrams_ns_raw() -> &'static [f32] {
    static DATA: OnceLock<Vec<f32>> = OnceLock::new();
    DATA.get_or_init(|| {
        tetragram_from_bytes(
            include_bytes!(concat!(env!("OUT_DIR"), "/tetra_ns.raw")),
        )
    });
    DATA.get().unwrap()
}

pub fn gramfreq_ws() -> &'static [u8] {
    include_bytes!(concat!(env!("OUT_DIR"), "/gramfreq_ws.stb")).as_slice()
}

pub fn gramfreq_ns() -> &'static [u8] {
    include_bytes!(concat!(env!("OUT_DIR"), "/gramfreq_ns.stb")).as_slice()
}
