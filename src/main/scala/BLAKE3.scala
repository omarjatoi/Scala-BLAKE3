/**
 * This is a port of the BLAKE3 reference implementation to Scala 3.
 * The original reference implementation is used for testing and as a readable
 * example of the algorithms involved.
 *
 * Example usage:
 *
 * ```scala
 * val hasher = Blake3.Hasher()
 * hasher.update("abc".getBytes)
 * hasher.update("def".getBytes)
 * val hash = new Array[Byte](32)
 * hasher.finalize(hash)
 * val extendedHash = new Array[Byte](500)
 * hasher.finalize(extendedHash)
 * assert(hash sameElements extendedHash.take(32))
 * ```
 */
object Blake3:
  // Constants
  val OutLen: Int = 32
  val KeyLen: Int = 32
  val BlockLen: Int = 64
  val ChunkLen: Int = 1024

  // Flags as opaque type aliases for more type safety
  opaque type Flag = Int
  object Flag:
    val ChunkStart: Flag = 1 << 0
    val ChunkEnd: Flag = 1 << 1
    val Parent: Flag = 1 << 2
    val Root: Flag = 1 << 3
    val KeyedHash: Flag = 1 << 4
    val DeriveKeyContext: Flag = 1 << 5
    val DeriveKeyMaterial: Flag = 1 << 6

    extension (f: Flag)
      def |(other: Flag): Flag = f | other

    given Conversion[Flag, Int] = identity

  export Flag._

  // IV constants
  val IV: IArray[Int] = IArray(
    0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
    0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19
  )

  // Message permutation indices
  val MsgPermutation: IArray[Int] = IArray(
    2, 6, 3, 10, 7, 0, 4, 13, 1, 11, 12, 5, 9, 14, 15, 8
  )

  /** The mixing function, G, which mixes either a column or a diagonal. */
  def g(state: Array[Int], a: Int, b: Int, c: Int, d: Int, mx: Int, my: Int): Unit =
    // Important: Use wrapping addition to match the original implementation
    state(a) = (state(a) + state(b)).&(0xFFFFFFFF) + mx
    state(d) = Integer.rotateRight(state(d) ^ state(a), 16)
    state(c) = (state(c) + state(d)).&(0xFFFFFFFF)
    state(b) = Integer.rotateRight(state(b) ^ state(c), 12)
    state(a) = (state(a) + state(b)).&(0xFFFFFFFF) + my
    state(d) = Integer.rotateRight(state(d) ^ state(a), 8)
    state(c) = (state(c) + state(d)).&(0xFFFFFFFF)
    state(b) = Integer.rotateRight(state(b) ^ state(c), 7)

  /** Applies one round of the BLAKE3 compression function. */
  def round(state: Array[Int], m: Array[Int]): Unit =
    // Mix the columns
    g(state, 0, 4, 8, 12, m(0), m(1))
    g(state, 1, 5, 9, 13, m(2), m(3))
    g(state, 2, 6, 10, 14, m(4), m(5))
    g(state, 3, 7, 11, 15, m(6), m(7))
    // Mix the diagonals
    g(state, 0, 5, 10, 15, m(8), m(9))
    g(state, 1, 6, 11, 12, m(10), m(11))
    g(state, 2, 7, 8, 13, m(12), m(13))
    g(state, 3, 4, 9, 14, m(14), m(15))

  /** Permutes the message words according to the permutation table. */
  def permute(m: Array[Int]): Unit =
    val permuted = Array.ofDim[Int](16)
    for i <- 0 until 16 do
      permuted(i) = m(MsgPermutation(i))
    Array.copy(permuted, 0, m, 0, 16)

  /**
   * Compresses a block of data using the BLAKE3 compression function.
   */
  def compress(
    chainingValue: Array[Int],
    blockWords: Array[Int],
    counter: Long,
    blockLen: Int,
    flags: Flag
  ): Array[Int] =
    val counterLow = counter.toInt
    val counterHigh = (counter >> 32).toInt
    val state = Array(
      chainingValue(0), chainingValue(1), chainingValue(2), chainingValue(3),
      chainingValue(4), chainingValue(5), chainingValue(6), chainingValue(7),
      IV(0), IV(1), IV(2), IV(3),
      counterLow, counterHigh, blockLen, flags
    )
    val block = blockWords.clone()

    round(state, block) // round 1
    permute(block)
    round(state, block) // round 2
    permute(block)
    round(state, block) // round 3
    permute(block)
    round(state, block) // round 4
    permute(block)
    round(state, block) // round 5
    permute(block)
    round(state, block) // round 6
    permute(block)
    round(state, block) // round 7

    for i <- 0 until 8 do
      state(i) ^= state(i + 8)
      state(i + 8) ^= chainingValue(i)

    state

  /** Gets the first 8 words from the compression output. */
  def first8Words(compressionOutput: Array[Int]): Array[Int] =
    compressionOutput.take(8)

  /** Converts a byte array to an array of 32-bit words in little-endian format. */
  def wordsFromLittleEndianBytes(bytes: Array[Byte], words: Array[Int]): Unit =
    // Handle full blocks of 4 bytes
    val fullBlocks = bytes.length / 4
    val safeBlocks = math.min(fullBlocks, words.length)

    for i <- 0 until safeBlocks do
      val idx = i * 4
      words(i) = ((bytes(idx) & 0xFF)
              | ((bytes(idx + 1) & 0xFF) << 8)
              | ((bytes(idx + 2) & 0xFF) << 16)
              | ((bytes(idx + 3) & 0xFF) << 24))

    // Handle potential partial block at the end
    if fullBlocks < words.length && bytes.length % 4 > 0 then
      var value = 0
      val startIdx = fullBlocks * 4
      for i <- 0 until (bytes.length % 4) do
        value |= (bytes(startIdx + i) & 0xFF) << (8 * i)
      words(fullBlocks) = value

  /**
   * Output represents the state just prior to producing either an 8-word
   * chaining value or any number of final output bytes.
   */
  case class Output(
    inputChainingValue: Array[Int],
    blockWords: Array[Int],
    counter: Long,
    blockLen: Int,
    flags: Flag
  ):
    /** Produces an 8-word chaining value. */
    def chainingValue(): Array[Int] =
      first8Words(compress(
        inputChainingValue,
        blockWords,
        counter,
        blockLen,
        flags
      ))

    /** Produces any number of output bytes for the root node. */
    def rootOutputBytes(outSlice: Array[Byte]): Unit =
      var outputBlockCounter = 0L
      var i = 0
      while i < outSlice.length do
        val blockSize = math.min(2 * OutLen, outSlice.length - i)
        val words = compress(
          inputChainingValue,
          blockWords,
          outputBlockCounter,
          blockLen,
          flags | Root
        )

        // Convert words to bytes (little-endian)
        for j <- 0 until math.min(16, (blockSize + 3) / 4) do
          val word = words(j)
          val startIdx = i + j * 4

          // Only write bytes that fit within the output slice
          if startIdx < outSlice.length then outSlice(startIdx) = (word & 0xFF).toByte
          if startIdx + 1 < outSlice.length then outSlice(startIdx + 1) = ((word >> 8) & 0xFF).toByte
          if startIdx + 2 < outSlice.length then outSlice(startIdx + 2) = ((word >> 16) & 0xFF).toByte
          if startIdx + 3 < outSlice.length then outSlice(startIdx + 3) = ((word >> 24) & 0xFF).toByte

        outputBlockCounter += 1
        i += blockSize

  /**
   * ChunkState manages the state for hashing one chunk of input data.
   */
  class ChunkState(
    var chainingValue: Array[Int],
    val chunkCounter: Long,
    var block: Array[Byte],
    var blockLen: Int,
    var blocksCompressed: Int,
    val flags: Flag
  ):
    /** Creates a new ChunkState with the given parameters. */
    def this(keyWords: Array[Int], chunkCounter: Long, flags: Flag) =
      this(
        keyWords.clone(),  // Clone to prevent inadvertent modification
        chunkCounter,
        Array.ofDim[Byte](BlockLen),
        0,
        0,
        flags
      )

    /** Returns the total number of bytes processed by this chunk state. */
    def len: Int = BlockLen * blocksCompressed + blockLen

    /** Returns the flag to use to indicate the start of a chunk. */
    def startFlag: Flag = if blocksCompressed == 0 then ChunkStart else 0

    /** Updates the chunk state with new input data. */
    def update(input: Array[Byte]): Unit =
      var inputIdx = 0
      while inputIdx < input.length do
        // If the block buffer is full, compress it and clear it.
        if blockLen == BlockLen then
          val blockWords = Array.ofDim[Int](16)
          wordsFromLittleEndianBytes(block, blockWords)
          chainingValue = first8Words(compress(
            chainingValue,
            blockWords,
            chunkCounter,
            BlockLen,
            flags | startFlag
          ))
          blocksCompressed += 1
          block = Array.ofDim[Byte](BlockLen)
          blockLen = 0

        // Copy input bytes into the block buffer
        val want = BlockLen - blockLen
        val take = math.min(want, input.length - inputIdx)
        System.arraycopy(input, inputIdx, block, blockLen, take)
        blockLen += take
        inputIdx += take

    /** Finalizes this chunk and returns the Output. */
    def output(): Output =
      val blockWords = Array.ofDim[Int](16)
      wordsFromLittleEndianBytes(block, blockWords)
      Output(
        chainingValue,
        blockWords,
        chunkCounter,
        blockLen,
        flags | startFlag | ChunkEnd
      )

  /** Creates an Output for a parent node in the Merkle tree. */
  def parentOutput(
    leftChildCv: Array[Int],
    rightChildCv: Array[Int],
    keyWords: Array[Int],
    flags: Flag
  ): Output =
    val blockWords = Array.ofDim[Int](16)
    System.arraycopy(leftChildCv, 0, blockWords, 0, 8)
    System.arraycopy(rightChildCv, 0, blockWords, 8, 8)
    Output(
      keyWords,
      blockWords,
      0, // Always 0 for parent nodes
      BlockLen, // Always BlockLen (64) for parent nodes
      Parent | flags
    )

  /** Computes the chaining value for a parent node in the Merkle tree. */
  def parentCv(
    leftChildCv: Array[Int],
    rightChildCv: Array[Int],
    keyWords: Array[Int],
    flags: Flag
  ): Array[Int] =
    parentOutput(leftChildCv, rightChildCv, keyWords, flags).chainingValue()

  /**
   * An incremental hasher that can accept any number of writes.
   */
  class Hasher private (
    var chunkState: ChunkState,
    val keyWords: Array[Int],
    var cvStack: Array[Array[Int]], // Space for subtree chaining values
    var cvStackLen: Int,
    val flags: Flag
  ):
    /** Creates a new Hasher with the given parameters. */
    private def this(keyWords: Array[Int], flags: Flag) =
      this(
        ChunkState(keyWords.clone(), 0, flags),
        keyWords.clone(),
        Array.fill(54)(Array.ofDim[Int](8)), // 2^54 * ChunkLen = 2^64
        0,
        flags
      )

    /** Pushes a chaining value onto the CV stack. */
    private def pushStack(cv: Array[Int]): Unit =
      cvStack(cvStackLen) = cv.clone()
      cvStackLen += 1

    /** Pops a chaining value from the CV stack. */
    private def popStack(): Array[Int] =
      cvStackLen -= 1
      cvStack(cvStackLen).clone()

    /** Adds a chunk chaining value to the Merkle tree. */
    private def addChunkChainingValue(newCv: Array[Int], totalChunks: Long): Unit =
      var cv = newCv.clone()
      var chunks = totalChunks

      // Merge subtrees
      while (chunks & 1) == 0 do
        cv = parentCv(popStack(), cv, keyWords, flags)
        chunks >>= 1

      pushStack(cv)

    /** Adds input to the hash state. This can be called any number of times. */
    def update(input: Array[Byte]): Unit =
      var idx = 0
      while idx < input.length do
        // If the current chunk is complete, finalize it
        if chunkState.len == ChunkLen then
          val chunkCv = chunkState.output().chainingValue()
          val totalChunks = chunkState.chunkCounter + 1
          addChunkChainingValue(chunkCv, totalChunks)
          chunkState = ChunkState(keyWords, totalChunks, flags)

        // Process more input
        val want = ChunkLen - chunkState.len
        val take = math.min(want, input.length - idx)
        chunkState.update(input.slice(idx, idx + take))
        idx += take

    /** Finalizes the hash and writes any number of output bytes. */
    def finalize(outSlice: Array[Byte]): Unit =
      // Compute the root node of the Merkle tree
      var output = chunkState.output()
      var parentNodesRemaining = cvStackLen

      while parentNodesRemaining > 0 do
        parentNodesRemaining -= 1
        output = parentOutput(
          cvStack(parentNodesRemaining),
          output.chainingValue(),
          keyWords,
          flags
        )

      output.rootOutputBytes(outSlice)

  /** Companion object for Hasher with factory methods */
  object Hasher:
    /** Construct a new Hasher for the regular hash function. */
    def apply(): Hasher = new Hasher(IV.toArray, 0)

    /** Construct a new Hasher for the keyed hash function. */
    def keyed(key: Array[Byte]): Hasher =
      require(key.length == KeyLen, s"Key must be $KeyLen bytes")
      val keyWords = Array.ofDim[Int](8)
      wordsFromLittleEndianBytes(key, keyWords)
      new Hasher(keyWords, KeyedHash)

    /** Construct a new Hasher for the key derivation function. */
    def deriveKey(context: String): Hasher =
      val contextHasher = new Hasher(IV.toArray, DeriveKeyContext)
      contextHasher.update(context.getBytes)
      val contextKey = Array.ofDim[Byte](KeyLen)
      contextHasher.finalize(contextKey)
      val contextKeyWords = Array.ofDim[Int](8)
      wordsFromLittleEndianBytes(contextKey, contextKeyWords)
      new Hasher(contextKeyWords, DeriveKeyMaterial)
