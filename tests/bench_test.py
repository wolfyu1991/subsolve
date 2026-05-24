"""Generate random substitution ciphers and verify subsolve solver accuracy."""

import subprocess
import random
import string
import time
import sys
import os
import argparse
import re
import math

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BINARY = os.path.join(PROJECT_ROOT, "target", "debug", "subsolve.exe")

SENTENCES = [
    # Short (3-6 words)
    "THE CAT SAT",
    "HELLO WORLD",
    "A QUICK BROWN FOX",
    "THIS IS A SECRET",
    "FIND THE KEY PLEASE",
    "GOOD MORNING EVERYONE",
    # Medium (7-12 words)
    "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG",
    "THIS IS A SECRET MESSAGE THAT NEEDS DECODING",
    "PLEASE FIND THE HIDDEN MEANING IN THIS TEXT",
    "EVERY GOOD BOY DESERVES FAVOR AND MORE FUN",
    "THE SUN RISES IN THE EAST AND SETS IN THE WEST",
    # Long (13+ words)
    "WHEN THE SUN ROSE OVER THE MOUNTAINS THE TRAVELERS BEGAN THEIR LONG JOURNEY THROUGH THE DENSE FOREST",
    "THE SECRET CODE WAS FINALLY BROKEN AFTER MONTHS OF CAREFUL ANALYSIS BY THE CRYPTOGRAPHY TEAM",
    "EVERY CIPHER HAS A SOLUTION IF YOU HAVE ENOUGH TIME AND THE RIGHT TOOLS TO UNLOCK ITS MYSTERIES",
    "THE ART OF CRYPTOGRAPHY HAS BEEN PRACTICED FOR THOUSANDS OF YEARS FROM ANCIENT ROME TO MODERN COMPUTERS",
]


def build_binary():
    """Ensure the binary is built."""
    if not os.path.exists(BINARY):
        print("Building subsolve (debug)...")
        result = subprocess.run(
            ["cargo", "build"],
            cwd=PROJECT_ROOT,
            capture_output=True, text=True
        )
        if result.returncode != 0:
            print("Build failed:", result.stderr)
            sys.exit(1)


def generate_cipher():
    """Return (encrypt_map, decrypt_map) as dicts."""
    letters = list(string.ascii_uppercase)
    shuffled = letters[:]
    random.shuffle(shuffled)
    enc = dict(zip(letters, shuffled))
    dec = {v: k for k, v in enc.items()}
    return enc, dec


def encrypt(plaintext, cipher_map):
    result = []
    for ch in plaintext.upper():
        result.append(cipher_map.get(ch, ch))
    return "".join(result)


def run_solver(ciphertext, trustspaces=True, timelimit=10):
    args = [BINARY, "--timelimit", str(timelimit)]
    if not trustspaces:
        args.extend(["--trustspaces", "false"])
    args.append(ciphertext)
    start = time.perf_counter()
    result = subprocess.run(
        args, capture_output=True, text=True, timeout=timelimit + 10
    )
    elapsed = time.perf_counter() - start
    return result, elapsed


def extract_solutions(stdout):
    """Parse solver output lines into (score, text) pairs."""
    solutions = []
    for line in stdout.strip().splitlines():
        line = line.strip()
        if not line:
            continue
        m = re.match(r"^(-?\d+(?:\.\d+)?)\s+(.*)", line)
        if m:
            score = float(m.group(1))
            text = m.group(2)
            solutions.append((score, text))
    return solutions


def normalized(text):
    """Remove spaces, uppercase for comparison."""
    return re.sub(r"[^A-Z]", "", text.upper())


def word_accuracy(found_text, expected_text):
    """Fraction of words that match exactly (case-insensitive)."""
    found_words = found_text.upper().split()
    expected_words = expected_text.upper().split()
    if len(found_words) != len(expected_words):
        return 0.0
    correct = sum(1 for a, b in zip(found_words, expected_words) if a == b)
    return correct / len(expected_words) if expected_words else 0.0


def char_accuracy(found_text, expected_text):
    """Fraction of characters (letters only) that match."""
    f = normalized(found_text)
    e = normalized(expected_text)
    if not e:
        return 1.0
    min_len = min(len(f), len(e))
    if min_len == 0:
        return 0.0
    correct = sum(1 for i in range(min_len) if f[i] == e[i])
    return correct / len(e)


def score_letter_accuracy(stdout, expected_text):
    """Check if any solution line has high word accuracy."""
    solutions = extract_solutions(stdout)
    if not solutions:
        return 0.0, ""
    best_word_acc = 0.0
    best_text = ""
    for score, text in solutions:
        wa = word_accuracy(text, expected_text)
        if wa > best_word_acc:
            best_word_acc = wa
            best_text = text
    return best_word_acc, best_text


def word_count(sentence):
    return len(sentence.split())

def accuracy_threshold(sentence_len_words, trustspaces):
    """Return (word_acc_threshold, char_acc_threshold)."""
    if sentence_len_words <= 4:
        return (0.3, 0.6) if trustspaces else (0.2, 0.4)
    elif sentence_len_words <= 8:
        return (0.6, 0.8) if trustspaces else (0.4, 0.6)
    else:
        return (0.8, 0.9) if trustspaces else (0.5, 0.7)

def run_tests(trustspaces, timelimit, trials_per_sentence, verbose):
    mode_name = "dictionary attack" if trustspaces else "genetic solver"
    print(f"\n  Mode: --trustspaces {'true' if trustspaces else 'false'} ({mode_name})")
    print(f"  Time limit: {timelimit}s, trials per sentence: {trials_per_sentence}")
    print()

    total = 0
    passed = 0
    total_time = 0.0
    short_total = 0
    short_passed = 0
    long_total = 0
    long_passed = 0
    results = []

    for sentence in SENTENCES:
        for trial in range(trials_per_sentence):
            enc_map, dec_map = generate_cipher()
            ciphertext = encrypt(sentence, enc_map)

            result, elapsed = run_solver(ciphertext, trustspaces, timelimit)
            total_time += elapsed
            total += 1

            word_acc, best_text = score_letter_accuracy(result.stdout, sentence)
            thr, _ = accuracy_threshold(word_count(sentence), trustspaces)
            ok = word_acc >= thr

            if ok:
                passed += 1

            nw = word_count(sentence)
            if nw <= 6:
                short_total += 1
                if ok:
                    short_passed += 1
            else:
                long_total += 1
                if ok:
                    long_passed += 1

            results.append((sentence, ciphertext, best_text, word_acc, elapsed, ok))

    print_results(results, passed, total, total_time,
                  short_passed, short_total, long_passed, long_total)
    return passed, total


def print_results(results, passed, total, total_time,
                  short_passed, short_total, long_passed, long_total):
    for sentence, ciphertext, best_text, word_acc, elapsed, ok in results:
        status = "PASS" if ok else "FAIL"
        short_ct = ciphertext[:60] + ("..." if len(ciphertext) > 60 else "")
        thr, _ = accuracy_threshold(word_count(sentence), True)
        print(
            f"  {status:4s}  [{elapsed:5.1f}s]  "
            f"acc={word_acc:.0%} (thr={thr:.0%})  "
            f"\"{short_ct}\""
        )
        if not ok and best_text:
            print(f"          expected: {sentence}")
            print(f"          got:      {best_text}")
    print()
    if short_total > 0:
        print(f"  Short (≤6 words): {short_passed}/{short_total} passed "
              f"({100.0 * short_passed / short_total:.1f}%)")
    if long_total > 0:
        print(f"  Long  (>6 words): {long_passed}/{long_total} passed "
              f"({100.0 * long_passed / long_total:.1f}%)")
    print(f"  Total: {passed}/{total} passed ({100.0 * passed / total:.1f}%), "
          f"time: {total_time:.1f}s")
    print()


def generate_rust_test_vectors(count=5):
    """Generate known-answer test vectors suitable for Rust integration tests."""
    print("// Auto-generated test vectors — paste into cli_tests.rs")
    for i in range(count):
        sentence = random.choice(SENTENCES)
        enc_map, dec_map = generate_cipher()
        ciphertext = encrypt(sentence, enc_map)
        snippet = sentence[:30].replace("'", "")
        print(f'    ("{ciphertext}", "{sentence}"),  // {snippet}...')


def main():
    parser = argparse.ArgumentParser(description="Benchmark subsolve solver accuracy")
    parser.add_argument("--trustspaces", type=str, default="both",
                        choices=["true", "false", "both"],
                        help="Which solver mode to test")
    parser.add_argument("--timelimit", type=int, default=10,
                        help="Time limit per solver run (seconds)")
    parser.add_argument("--trials", type=int, default=2,
                        help="Number of random cipher trials per sentence")
    parser.add_argument("--verbose", action="store_true",
                        help="Show detailed output")
    parser.add_argument("--generate-test-vectors", type=int, default=0, metavar="N",
                        help="Generate N Rust test vector pairs and exit")
    args = parser.parse_args()

    if args.generate_test_vectors > 0:
        build_binary()
        generate_rust_test_vectors(args.generate_test_vectors)
        return

    build_binary()

    print("=" * 60)
    print(f"  subsolve Accuracy Benchmark")
    print(f"  Sentences: {len(SENTENCES)}, trials/sentence: {args.trials}")
    print("=" * 60)

    total_passed = 0
    total_tests = 0

    for trustspaces in ([True, False] if args.trustspaces == "both"
                        else [args.trustspaces == "true"]):
        p, t = run_tests(trustspaces, args.timelimit, args.trials, args.verbose)
        total_passed += p
        total_tests += t

    print("=" * 60)
    pct = 100.0 * total_passed / total_tests if total_tests else 0
    print(f"  OVERALL: {total_passed}/{total_tests} passed ({pct:.1f}%)")
    print("=" * 60)

    if total_passed < total_tests:
        sys.exit(1)


if __name__ == "__main__":
    main()
