package app.virgil.core.database

import androidx.room.migration.Migration

/**
 * Migrações manuais. Regra do projeto: toda alteração de esquema incrementa a versão em
 * [VirgilDatabase], gera um novo JSON em core/database/schemas e ganha um teste de migração.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
