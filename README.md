# Scala-BLAKE3

This is a pure Scala port of the BLAKE3 [reference implementation](https://github.com/BLAKE3-team/BLAKE3/blob/master/reference_impl/reference_impl.rs).

> [!NOTE]
> This is **not** an optimized implementation, if you're looking for something fast and stable to use in production, consider using the BLAKE3 implementations in [Bouncy Castle](https://www.bouncycastle.org/) or [Apache Commons Codec](https://commons.apache.org/proper/commons-codec/apidocs/org/apache/commons/codec/digest/Blake3.html).
>
> Another optimized Scala implementation exists at https://github.com/kcrypt/scala-blake3.

## Usage

```scala
val hasher = Blake3.Hasher()
hasher.update("scala".getBytes)
val hash = new Array[Byte](32)
hasher.finalize(hash)
```

## Development

You can use [`nix`](https://zero-to-nix.com/concepts/nix/) to launch a development shell, i.e.:

```shell
φ nix develop --command /bin/zsh
```

Alternatively, install [Scala](https://scala-lang.org) and [sbt](https://www.scala-sbt.org/), preferably using [Couriser](https://get-coursier.io/).

```shell
φ sbt test
...
Blake3TestVectors:
  + Input length 0 - all at once 0.013s
  + Input length 0 - one byte at a time 0.001s
  + Input length 1 - all at once 0.001s
  + Input length 1 - one byte at a time 0.001s
  + Input length 2 - all at once 0.001s
  + Input length 2 - one byte at a time 0.001s
  + Input length 3 - all at once 0.001s
  + Input length 3 - one byte at a time 0.001s
  + Input length 4 - all at once 0.001s
  + Input length 4 - one byte at a time 0.0s
  + Input length 5 - all at once 0.001s
  + Input length 5 - one byte at a time 0.001s
  + Input length 6 - all at once 0.0s
  + Input length 6 - one byte at a time 0.001s
  + Input length 7 - all at once 0.001s
  + Input length 7 - one byte at a time 0.0s
  + Input length 8 - all at once 0.001s
  + Input length 8 - one byte at a time 0.0s
  + Input length 63 - all at once 0.001s
  + Input length 63 - one byte at a time 0.0s
  + Input length 64 - all at once 0.001s
  + Input length 64 - one byte at a time 0.0s
  + Input length 65 - all at once 0.001s
  + Input length 65 - one byte at a time 0.0s
  + Input length 127 - all at once 0.0s
  + Input length 127 - one byte at a time 0.001s
  + Input length 128 - all at once 0.0s
  + Input length 128 - one byte at a time 0.001s
  + Input length 129 - all at once 0.0s
  + Input length 129 - one byte at a time 0.001s
  + Input length 1023 - all at once 0.0s
  + Input length 1023 - one byte at a time 0.001s
  + Input length 1024 - all at once 0.0s
  + Input length 1024 - one byte at a time 0.001s
  + Input length 1025 - all at once 0.001s
  + Input length 2048 - all at once 0.0s
  + Input length 2049 - all at once 0.001s
  + Input length 3072 - all at once 0.0s
  + Input length 3073 - all at once 0.001s
  + Input length 4096 - all at once 0.001s
  + Input length 4097 - all at once 0.0s
  + Input length 5120 - all at once 0.001s
  + Input length 5121 - all at once 0.001s
  + Input length 6144 - all at once 0.001s
  + Input length 6145 - all at once 0.0s
  + Input length 7168 - all at once 0.001s
  + Input length 7169 - all at once 0.001s
  + Input length 8192 - all at once 0.001s
  + Input length 8193 - all at once 0.001s
  + Input length 16384 - all at once 0.0s
  + Input length 31744 - all at once 0.002s
  + Input length 102400 - all at once 0.002s
  + Default output matches first 32 bytes of extended output 0.0s
  + Multiple updates with same total content produce the same result 0.001s
  + Different inputs produce different hashes 0.0s
  + Key must be exactly 32 bytes 0.001s
[info] Passed: Total 56, Failed 0, Errors 0, Passed 56
[success] Total time: 0 s, completed Mar 23, 2025, 10:30:36 PM
```

##  License

Licensed under either of

- Apache License, Version 2.0, ([LICENSE-APACHE](./LICENSE-APACHE) or https://www.apache.org/licenses/LICENSE-2.0)
- MIT license ([LICENSE-MIT](./LICENSE-MIT) or https://opensource.org/licenses/MIT)

at your option.

### Contribution

Unless you explicitly state otherwise, any contribution intentionally submitted for inclusion in the work by you, as defined in the Apache-2.0 license, shall be dual licensed as above, without any additional terms or conditions.
