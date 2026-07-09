package com.obsidianvoice.notes.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Persists the user's chosen Obsidian vault (sync) folder, stored as a
 * Storage Access Framework tree URI with persisted read/write permission.
 */
class SettingsStore(private val context: Context) {

    val vaultTreeUri: Flow<String?> =
        context.dataStore.data.map { it[VAULT_TREE_URI] }

    suspend fun setVaultTreeUri(uri: String) {
        context.dataStore.edit { it[VAULT_TREE_URI] = uri }
    }

    suspend fun currentVaultTreeUri(): String? =
        context.dataStore.data.first()[VAULT_TREE_URI]

    private companion object {
        val VAULT_TREE_URI = stringPreferencesKey("vault_tree_uri")
    }
}
