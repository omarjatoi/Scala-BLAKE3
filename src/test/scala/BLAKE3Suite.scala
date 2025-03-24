import java.io.{File, FileInputStream}
import scala.io.Source
import scala.util.Using

class Blake3TestVectors extends munit.FunSuite {
  import Blake3._
  import upickle.default._

  // Constants from the original Rust code
  val BlockLen: Int = 64
  val ChunkLen: Int = 1024
  val OutputLen: Int = 2 * BlockLen + 3 // 131 bytes

  // Define the case classes for the test vectors JSON
  case class Case(
      input_len: Int,
      hash: String,
      keyed_hash: String,
      derive_key: String
  )

  case class Cases(
      _comment: String,
      key: String,
      context_string: String,
      cases: Seq[Case]
  )

  // JSON reader for the test vectors
  implicit val caseRW: ReadWriter[Case] = macroRW
  implicit val casesRW: ReadWriter[Cases] = macroRW

  // Load the test vectors from the JSON file
  def loadTestVectors(): Cases = {
    val filePath = "src/test/resources/test_vectors.json"
    val source = Source.fromFile(filePath)
    try {
      read[Cases](source.mkString)
    } finally {
      source.close()
    }
  }

  // Helper to convert a hex string to a byte array
  def hexToBytes(hex: String): Array[Byte] = {
    hex.grouped(2).map(h => Integer.parseInt(h, 16).toByte).toArray
  }

  // Helper to convert a byte array to a hex string
  def bytesToHex(bytes: Array[Byte]): String = {
    bytes.map(b => f"${b & 0xff}%02x").mkString
  }

  // Create a test input pattern as used in the original implementation
  def paintTestInput(buf: Array[Byte]): Unit = {
    for (i <- buf.indices) {
      buf(i) = (i % 251).toByte
    }
  }

  // Test the reference implementation with all input at once
  def testAllAtOnce(
      key: Array[Byte],
      input: Array[Byte],
      expectedHash: Array[Byte],
      expectedKeyedHash: Array[Byte],
      expectedDeriveKey: Array[Byte],
      testName: String
  ): Unit = {
    test(s"$testName - all at once") {
      // Regular hash
      val out = Array.ofDim[Byte](expectedHash.length)
      val hasher = Hasher()
      hasher.update(input)
      hasher.finalize(out)
      assertEquals(bytesToHex(out), bytesToHex(expectedHash))

      // Keyed hash
      val keyedOut = Array.ofDim[Byte](expectedKeyedHash.length)
      val keyedHasher = Hasher.keyed(key)
      keyedHasher.update(input)
      keyedHasher.finalize(keyedOut)
      assertEquals(bytesToHex(keyedOut), bytesToHex(expectedKeyedHash))

      // Derive key
      val deriveKeyOut = Array.ofDim[Byte](expectedDeriveKey.length)
      val deriveKeyHasher = Hasher.deriveKey(testVectors.context_string)
      deriveKeyHasher.update(input)
      deriveKeyHasher.finalize(deriveKeyOut)
      assertEquals(bytesToHex(deriveKeyOut), bytesToHex(expectedDeriveKey))
    }
  }

  // Test the reference implementation with input one byte at a time
  def testOneByteAtATime(
      key: Array[Byte],
      input: Array[Byte],
      expectedHash: Array[Byte],
      expectedKeyedHash: Array[Byte],
      expectedDeriveKey: Array[Byte],
      testName: String
  ): Unit = {
    test(s"$testName - one byte at a time") {
      // Regular hash
      val out = Array.ofDim[Byte](expectedHash.length)
      val hasher = Hasher()
      for (b <- input) {
        hasher.update(Array(b))
      }
      hasher.finalize(out)
      assertEquals(bytesToHex(out), bytesToHex(expectedHash))

      // Keyed hash
      val keyedOut = Array.ofDim[Byte](expectedKeyedHash.length)
      val keyedHasher = Hasher.keyed(key)
      for (b <- input) {
        keyedHasher.update(Array(b))
      }
      keyedHasher.finalize(keyedOut)
      assertEquals(bytesToHex(keyedOut), bytesToHex(expectedKeyedHash))

      // Derive key
      val deriveKeyOut = Array.ofDim[Byte](expectedDeriveKey.length)
      val deriveKeyHasher = Hasher.deriveKey(testVectors.context_string)
      for (b <- input) {
        deriveKeyHasher.update(Array(b))
      }
      deriveKeyHasher.finalize(deriveKeyOut)
      assertEquals(bytesToHex(deriveKeyOut), bytesToHex(expectedDeriveKey))
    }
  }

  // Load the test vectors
  val testVectors = loadTestVectors()
  val key = testVectors.key.getBytes()

  // Generate test cases for each length in the test vectors
  for (testCase <- testVectors.cases) {
    val input = Array.ofDim[Byte](testCase.input_len)
    paintTestInput(input)

    val expectedHash = hexToBytes(testCase.hash)
    val expectedKeyedHash = hexToBytes(testCase.keyed_hash)
    val expectedDeriveKey = hexToBytes(testCase.derive_key)

    testAllAtOnce(
      key,
      input,
      expectedHash,
      expectedKeyedHash,
      expectedDeriveKey,
      s"Input length ${testCase.input_len}"
    )

    // Only run one-byte-at-a-time tests for smaller inputs to avoid excessive test time
    if (testCase.input_len <= 1024) {
      testOneByteAtATime(
        key,
        input,
        expectedHash,
        expectedKeyedHash,
        expectedDeriveKey,
        s"Input length ${testCase.input_len}"
      )
    }
  }

  // Test that default output (32 bytes) matches first 32 bytes of extended output
  test("Default output matches first 32 bytes of extended output") {
    val input = Array.ofDim[Byte](100)
    paintTestInput(input)

    // Regular hash
    val hasher1 = Hasher()
    hasher1.update(input)
    val hash32 = Array.ofDim[Byte](32)
    hasher1.finalize(hash32)

    val hasher2 = Hasher()
    hasher2.update(input)
    val hashExtended = Array.ofDim[Byte](64)
    hasher2.finalize(hashExtended)

    assertEquals(bytesToHex(hash32), bytesToHex(hashExtended.take(32)))

    // Keyed hash
    val keyedHasher1 = Hasher.keyed(key)
    keyedHasher1.update(input)
    val keyedHash32 = Array.ofDim[Byte](32)
    keyedHasher1.finalize(keyedHash32)

    val keyedHasher2 = Hasher.keyed(key)
    keyedHasher2.update(input)
    val keyedHashExtended = Array.ofDim[Byte](64)
    keyedHasher2.finalize(keyedHashExtended)

    assertEquals(
      bytesToHex(keyedHash32),
      bytesToHex(keyedHashExtended.take(32))
    )

    // Derive key
    val deriveKeyHasher1 = Hasher.deriveKey(testVectors.context_string)
    deriveKeyHasher1.update(input)
    val deriveKeyHash32 = Array.ofDim[Byte](32)
    deriveKeyHasher1.finalize(deriveKeyHash32)

    val deriveKeyHasher2 = Hasher.deriveKey(testVectors.context_string)
    deriveKeyHasher2.update(input)
    val deriveKeyHashExtended = Array.ofDim[Byte](64)
    deriveKeyHasher2.finalize(deriveKeyHashExtended)

    assertEquals(
      bytesToHex(deriveKeyHash32),
      bytesToHex(deriveKeyHashExtended.take(32))
    )
  }

  // Test multiple updates with the same total content produce the same result
  test("Multiple updates with same total content produce the same result") {
    val input = Array.ofDim[Byte](1000)
    paintTestInput(input)

    // Single update
    val hasher1 = Hasher()
    hasher1.update(input)
    val hash1 = Array.ofDim[Byte](32)
    hasher1.finalize(hash1)

    // Multiple updates
    val hasher2 = Hasher()
    val chunkSize = 100
    for (i <- 0 until input.length by chunkSize) {
      val end = math.min(i + chunkSize, input.length)
      hasher2.update(input.slice(i, end))
    }
    val hash2 = Array.ofDim[Byte](32)
    hasher2.finalize(hash2)

    assertEquals(bytesToHex(hash1), bytesToHex(hash2))
  }

  // Test that different inputs produce different hashes
  test("Different inputs produce different hashes") {
    val input1 = Array.fill[Byte](100)(1)
    val input2 = Array.fill[Byte](100)(2)

    val hasher1 = Hasher()
    hasher1.update(input1)
    val hash1 = Array.ofDim[Byte](32)
    hasher1.finalize(hash1)

    val hasher2 = Hasher()
    hasher2.update(input2)
    val hash2 = Array.ofDim[Byte](32)
    hasher2.finalize(hash2)

    assertNotEquals(bytesToHex(hash1), bytesToHex(hash2))
  }

  // Test key size validation
  test("Key must be exactly 32 bytes") {
    val thrown = intercept[IllegalArgumentException] {
      Hasher.keyed(Array.fill[Byte](31)(0))
    }
    assert(thrown.getMessage.contains("must be 32 bytes"))
  }
}
