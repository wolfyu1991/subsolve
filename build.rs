use std::io::Read;
use std::path::PathBuf;

fn main() {
    let out = PathBuf::from(std::env::var("OUT_DIR").unwrap());

    process_tetragram("java-legacy/english4.sta", out.join("tetra.raw"));
    process_tetragram("java-legacy/english4ns.sta", out.join("tetra_ns.raw"));

    std::fs::copy("java-legacy/english-standard.dat", out.join("dict.bin")).unwrap();
    std::fs::copy("java-legacy/gramfreq5.stb", out.join("gramfreq_ws.stb")).unwrap();
    std::fs::copy("java-legacy/gramfreq5ns.stb", out.join("gramfreq_ns.stb")).unwrap();

    for path in [
        "java-legacy/english-standard.dat",
        "java-legacy/english4.sta",
        "java-legacy/english4ns.sta",
        "java-legacy/gramfreq5.stb",
        "java-legacy/gramfreq5ns.stb",
    ] {
        println!("cargo:rerun-if-changed={}", path);
    }
}

fn process_tetragram(src: &str, dst: PathBuf) {
    let file = std::fs::File::open(src).unwrap();
    let mut decoder = flate2::read::GzDecoder::new(file);

    let mut header = [0u8; 8];
    decoder.read_exact(&mut header).unwrap();

    let mut be_bytes = Vec::new();
    decoder.read_to_end(&mut be_bytes).unwrap();

    let n = be_bytes.len() / 4;
    let mut le_bytes = Vec::with_capacity(be_bytes.len());
    for i in 0..n {
        let off = i * 4;
        let val = f32::from_be_bytes(be_bytes[off..off + 4].try_into().unwrap());
        le_bytes.extend_from_slice(&val.to_le_bytes());
    }

    std::fs::write(&dst, &le_bytes).unwrap();
}
