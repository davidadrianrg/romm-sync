package es.davidrg.rommsync.core.library

/**
 * Vista mínima de un ROM para agrupar discos/variantes del mismo juego.
 * La reconstruyen tanto la app Android como el desktop a partir de sus DTOs.
 */
data class DiscEntry(
    val id: Int,
    val name: String,
    val platformSlug: String,
    val igdbId: Int?,
    val fileNames: List<String>,
)

/**
 * Grupo de entradas que representan el mismo juego físico.
 *
 * @param representativeId entrada que pinta la card (el superset con más ficheros).
 * @param downloadIds entradas a descargar para tener el juego completo (sin
 *   subconjuntos redundantes: si existe la entrada con todos los discos, las
 *   entradas de disco suelto no se descargan).
 * @param allIds todas las entradas del grupo (para mostrar discos/versiones).
 * @param discCount nº de discos distintos detectados.
 */
data class DiscGroup(
    val representativeId: Int,
    val downloadIds: List<Int>,
    val allIds: List<Int>,
    val discCount: Int,
)

/**
 * Agrupa entradas de RomM que son el mismo juego repartido en discos o
 * duplicado en varias entradas.
 *
 * Reglas (deliberadamente conservadoras, para no repetir el incidente
 * Golden Sun 1/2, que comparten igdb_id siendo juegos distintos):
 * - Solo se comparan entradas con la MISMA plataforma, el MISMO igdb_id (o
 *   mismo nombre normalizado si no hay igdb) y el MISMO nombre normalizado
 *   (sin marcas de disco).
 * - Dentro de ese cubo, dos entradas se relacionan si:
 *   (a) ambas llevan marca de disco en sus ficheros (Disc/Disk/CD N), o
 *   (b) el set de ficheros de una es subconjunto del de la otra (duplicados
 *       o superset: p. ej. un multi-disco completo junto a los discos sueltos).
 * - Entradas sin marca de disco y sin relación de subconjunto (p. ej. dos
 *   regiones distintas) NO se fusionan.
 */
object MultiDiscGrouper {

    private val DISC_REGEX = Regex(
        """\((?:disc|disk|cd)\s*([0-9]{1,2})[^)]*\)""",
        RegexOption.IGNORE_CASE,
    )
    private val DISC_IN_NAME = Regex(
        """[\s\-–—]*[\[(]\s*(?:disc|disk|cd)\s*[0-9]{1,2}[^\])]*[\])]""",
        RegexOption.IGNORE_CASE,
    )
    private val WHITESPACE = Regex("\\s+")
    private val TRAILING_SEPARATORS = Regex("""[\s\-–—:]+$""")
    private val REGION_TOKEN = Regex(
        """\((usa|europe|japan|world|germany|france|spain|italy|australia|korea|china|brazil|canada|netherlands|sweden)""",
        RegexOption.IGNORE_CASE,
    )

    private fun regionsOf(fileNames: List<String>): Set<String> =
        fileNames.mapNotNull { REGION_TOKEN.find(it)?.groupValues?.get(1)?.lowercase() }.toSet()

    /** Nº de disco extraído del nombre de fichero, o null si no lleva marca. */
    fun discNumber(fileName: String): Int? =
        DISC_REGEX.find(fileName)?.groupValues?.get(1)?.toIntOrNull()

    /** Nombre normalizado para comparar: sin marcas de disco, minúsculas, espacios simples. */
    fun normalizeName(name: String): String =
        DISC_IN_NAME.replace(name, "")
            .trim()
            .lowercase()
            .replace(WHITESPACE, " ")
            .replace(TRAILING_SEPARATORS, "")
            .trim()

    /** Agrupa la lista completa; devuelve un grupo por cada juego a mostrar. */
    fun group(entries: List<DiscEntry>): List<DiscGroup> {
        if (entries.isEmpty()) return emptyList()
        val buckets = entries.groupBy { e ->
            BucketKey(e.platformSlug.lowercase(), e.igdbId, normalizeName(e.name))
        }
        val indexOf = entries.withIndex().associate { (i, e) -> e.id to i }
        return buckets.values
            .flatMap { groupBucket(it) }
            .sortedBy { g -> indexOf[g.representativeId] ?: Int.MAX_VALUE }
    }

    private data class BucketKey(val platform: String, val igdbId: Int?, val name: String)

    private fun groupBucket(bucket: List<DiscEntry>): List<DiscGroup> {
        if (bucket.size == 1) return listOf(singleton(bucket[0]))

        val fileSets = bucket.map { it.fileNames.toSet() }
        val discs = bucket.map { e -> e.fileNames.mapNotNull(::discNumber).toSet() }
        val regions = bucket.map { regionsOf(it.fileNames) }

        val parent = IntArray(bucket.size) { it }
        fun find(x: Int): Int {
            var i = x
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        for (i in bucket.indices) {
            for (j in i + 1 until bucket.size) {
                val regionsCompatible = regions[i].isEmpty() || regions[j].isEmpty() || regions[i] == regions[j]
                val bothDiscs = discs[i].isNotEmpty() && discs[j].isNotEmpty() && regionsCompatible
                val subset = fileSets[i].isNotEmpty() && fileSets[j].isNotEmpty() &&
                    (fileSets[i].containsAll(fileSets[j]) || fileSets[j].containsAll(fileSets[i]))
                if (bothDiscs || subset) union(i, j)
            }
        }

        return bucket.indices.groupBy { find(it) }.values.map { idxs ->
            if (idxs.size == 1) return@map singleton(bucket[idxs[0]])
            val comp = idxs.map { bucket[it] }
            val compSets = comp.map { it.fileNames.toSet() }

            // Entradas que no son subconjunto estricto de otra (supersets).
            val maxima = comp.filterIndexed { idx, entry ->
                val mine = compSets[idx]
                compSets.withIndex().none { (otherIdx, other) ->
                    otherIdx != idx && other.size > mine.size && other.containsAll(mine)
                } && entry.fileNames.isNotEmpty()
            }.ifEmpty { comp }

            // Sin duplicados exactos repetidos.
            val seen = mutableSetOf<Set<String>>()
            val download = maxima.filter { seen.add(it.fileNames.toSet()) }

            val distinctDiscs = comp.flatMap { e -> e.fileNames.mapNotNull(::discNumber) }.distinct()
            DiscGroup(
                representativeId = (download.maxByOrNull { it.fileNames.size } ?: comp.first()).id,
                downloadIds = download.map { it.id },
                allIds = comp.map { it.id },
                discCount = distinctDiscs.size.takeIf { it > 0 } ?: download.size.coerceAtLeast(1),
            )
        }
    }

    private fun singleton(entry: DiscEntry): DiscGroup {
        val discs = entry.fileNames.mapNotNull(::discNumber).distinct()
        return DiscGroup(
            representativeId = entry.id,
            downloadIds = listOf(entry.id),
            allIds = listOf(entry.id),
            discCount = discs.size.takeIf { it > 0 } ?: 1,
        )
    }
}
