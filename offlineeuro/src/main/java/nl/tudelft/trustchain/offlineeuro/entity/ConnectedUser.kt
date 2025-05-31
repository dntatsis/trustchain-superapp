package nl.tudelft.trustchain.offlineeuro.entity

data class ConnectedUser(
    val id: Long,
    val name: String,
    val secretShare: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectedUser

        if (id != other.id) return false
        if (name != other.name) return false
        if (!secretShare.contentEquals(other.secretShare)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + secretShare.contentHashCode()
        return result
    }
}
