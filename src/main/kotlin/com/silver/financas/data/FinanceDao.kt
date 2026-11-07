package com.silver.financas.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // ---- Fluxos observáveis
    @Query("SELECT * FROM accounts ORDER BY archived, name COLLATE NOCASE")
    fun accounts(): Flow<List<Account>>

    @Query("SELECT * FROM cards ORDER BY archived, name COLLATE NOCASE")
    fun cards(): Flow<List<CreditCard>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun txns(): Flow<List<Txn>>

    @Query("SELECT * FROM budgets")
    fun budgets(): Flow<List<Budget>>

    @Query("SELECT * FROM rules")
    fun rules(): Flow<List<CategoryRule>>

    // ---- Listas diretas (worker, backup)
    @Query("SELECT * FROM accounts") suspend fun accountsList(): List<Account>
    @Query("SELECT * FROM cards") suspend fun cardsList(): List<CreditCard>
    @Query("SELECT * FROM categories") suspend fun categoriesList(): List<Category>
    @Query("SELECT * FROM transactions") suspend fun txnsList(): List<Txn>
    @Query("SELECT * FROM budgets") suspend fun budgetsList(): List<Budget>
    @Query("SELECT * FROM rules") suspend fun rulesList(): List<CategoryRule>
    @Query("SELECT * FROM recurring_groups") suspend fun groupsList(): List<RecurringGroup>
    @Query("SELECT COUNT(*) FROM categories") suspend fun countCategories(): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id") suspend fun countTxnsForAccount(id: Long): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE cardId = :id") suspend fun countTxnsForCard(id: Long): Int
    @Query("SELECT * FROM transactions WHERE groupId = :groupId ORDER BY date") suspend fun txnsInGroup(groupId: String): List<Txn>
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun txn(id: Long): Txn?

    // ---- Escrita
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAccount(a: Account): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCard(c: CreditCard): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCategory(c: Category): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(b: Budget): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRule(r: CategoryRule)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGroup(g: RecurringGroup)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTxn(t: Txn): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTxns(list: List<Txn>)
    @Update suspend fun updateTxn(t: Txn)
    @Update suspend fun updateTxns(list: List<Txn>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAccounts(l: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCards(l: List<CreditCard>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(l: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBudgets(l: List<Budget>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRules(l: List<CategoryRule>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroups(l: List<RecurringGroup>)

    @Delete suspend fun deleteAccount(a: Account)
    @Delete suspend fun deleteCard(c: CreditCard)
    @Delete suspend fun deleteCategory(c: Category)
    @Delete suspend fun deleteBudget(b: Budget)
    @Delete suspend fun deleteTxn(t: Txn)

    @Query("DELETE FROM transactions WHERE groupId = :groupId AND date >= :fromDate")
    suspend fun deleteGroupFrom(groupId: String, fromDate: Long)

    @Query("DELETE FROM transactions WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearCategory(id: Long)

    @Query("UPDATE categories SET parentId = NULL WHERE parentId = :id")
    suspend fun clearParent(id: Long)

    @Query("DELETE FROM budgets WHERE categoryId = :id")
    suspend fun deleteBudgetsOf(id: Long)

    @Query("DELETE FROM rules WHERE categoryId = :id")
    suspend fun deleteRulesOf(id: Long)

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM cards") suspend fun clearCards()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM transactions") suspend fun clearTxns()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM recurring_groups") suspend fun clearGroups()
}
