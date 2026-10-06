package dev.pocketdeck.adapter.recognition

import dev.pocketdeck.domain.card.CardId
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.sqrt

data class CardMatch(
    val cardId: CardId,
    /** Normalised cross-correlation of the illustration thumbnails, from -1 to 1. */
    val score: Float,
)

/**
 * The fingerprints of every known card (`card_index.bin`, written by `tools/card_index`). Thumbnails are stored
 * mean-centred and unit-length, so a dot product is the correlation and ignores brightness and contrast.
 */
class CardIndex private constructor(
    val parameters: FingerprintParameters,
    private val ids: List<CardId>,
    private val thumbnails: List<ByteArray>,
    private val vectors: List<FloatArray>,
) {
    val size: Int get() = ids.size

    fun thumbnailOf(cardId: CardId): ByteArray =
        thumbnails[
            ids.indexOf(cardId).also {
                require(it >= 0) { "Unknown $cardId" }
            },
        ]

    /** The [count] most similar cards, best first. A flat (featureless) thumbnail correlates with nothing. */
    fun nearest(
        fingerprint: ByteArray,
        count: Int,
    ): List<CardMatch> {
        val query = normalise(fingerprint)
        return vectors.indices
            .map { CardMatch(ids[it], dot(query, vectors[it])) }
            .sortedByDescending { it.score }
            .take(count)
    }

    private fun dot(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    companion object {
        private const val MAGIC = "PDIX"
        private const val SUPPORTED_VERSION = 1
        private const val BYTE_MASK = 0xFF

        fun read(input: InputStream): CardIndex {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(MAGIC.length).also(data::readFully).decodeToString()
            require(magic == MAGIC) { "Not a card index (magic '$magic')" }
            val version = data.readUnsignedShort()
            require(version == SUPPORTED_VERSION) { "Unsupported card index version $version" }
            val parameters =
                FingerprintParameters(
                    width = data.readUnsignedShort(),
                    height = data.readUnsignedShort(),
                    x0 = data.readFloat(),
                    y0 = data.readFloat(),
                    x1 = data.readFloat(),
                    y1 = data.readFloat(),
                )
            val count = data.readInt()
            val ids = ArrayList<CardId>(count)
            val thumbnails = ArrayList<ByteArray>(count)
            repeat(count) {
                ids += CardId(ByteArray(data.readUnsignedShort()).also(data::readFully).decodeToString())
                thumbnails += ByteArray(parameters.width * parameters.height).also(data::readFully)
            }
            return CardIndex(parameters, ids, thumbnails, thumbnails.map(::normalise))
        }

        private fun normalise(thumbnail: ByteArray): FloatArray {
            val values = FloatArray(thumbnail.size) { (thumbnail[it].toInt() and BYTE_MASK).toFloat() }
            val mean = values.average().toFloat()
            var squares = 0f
            for (i in values.indices) {
                values[i] -= mean
                squares += values[i] * values[i]
            }
            val length = sqrt(squares)
            if (length == 0f) return FloatArray(values.size)
            for (i in values.indices) values[i] /= length
            return values
        }
    }
}
