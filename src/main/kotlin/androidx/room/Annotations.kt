package androidx.room

// Anotações vazias: no PC os dados ficam num arquivo JSON, não no Room.
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(vararg val value: String)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Dao
annotation class Query(val value: String)
annotation class Insert(val onConflict: Int = 1)
annotation class Update
annotation class Delete
object OnConflictStrategy { const val REPLACE = 1 }
