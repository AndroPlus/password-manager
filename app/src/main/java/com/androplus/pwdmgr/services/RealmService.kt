package com.androplus.pwdmgr.services

import android.util.Log
import com.androplus.pwdmgr.model.LoginModel
import com.androplus.pwdmgr.model.UserApplication
import com.androplus.pwdmgr.model.ExpenseCategory
import com.androplus.pwdmgr.model.ExpenseTransaction
import com.androplus.pwdmgr.model.TodoItem
import com.androplus.pwdmgr.model.ExpenseBudget
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mongodb.kbson.ObjectId

class RealmService private constructor() {

    private lateinit var realm: Realm


    init {
        val config = RealmConfiguration.Builder(
            schema = setOf(
                LoginModel::class,
                UserApplication::class,
                ExpenseCategory::class,
                ExpenseTransaction::class,
                TodoItem::class,
                ExpenseBudget::class
            )
        )
            .deleteRealmIfMigrationNeeded()
            .name("pwdDB")
            .encryptionKey(key!!)
            .build()

        try {
            realm = Realm.open(config)
            Log.d("RealmInit", "Realm initialized successfully.")
        } catch (e: Exception) {
            Log.e("RealmInit", "Error initializing Realm: ${e.message}")
        }
    }

    companion object {
        private var realmService: RealmService? = null
        var key:ByteArray? = null
        fun getInstance(key: ByteArray? = null): RealmService {
            this.key = key
            if (realmService == null) {
                realmService = RealmService()
            }
            return realmService!!
        }
    }

   suspend  fun createLogin(loginModel: LoginModel): Boolean {
        return try {
            realm.write {
                copyToRealm(loginModel)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

     fun getLoginData(): LoginModel? {
        return realm.query<LoginModel>("id == $0", "1").first().find()
    }

   suspend fun createApplication(userApplication: UserApplication): Boolean {
        return try {
            realm.write {
                copyToRealm(userApplication)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateApplication(userApp: UserApplication): Boolean {
        return try {
            realm.write {
                val userApplication = query<UserApplication>("_id == $0", userApp._id).find().first()
                userApplication.app_name = userApp.app_name
                userApplication.app_fields_json_array = userApp.app_fields_json_array
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun removeApplication(userApp: UserApplication): Boolean {
        return try {
            realm.write {
                val objectToDelete = query<UserApplication>("_id == $0", userApp._id).find().first()
               delete(objectToDelete)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

     fun getAllUserApplications(userApp: UserApplication? = UserApplication()): List<UserApplication> {
        val realmResults: RealmResults<UserApplication> = if (!userApp?.app_name.isNullOrEmpty()) {
            realm.query<UserApplication>("_id == $0", userApp?._id).find()
        } else {
            realm.query<UserApplication>().find()
        }
        return realmResults.toList()
    }

    // ==================== EXPENSE CATEGORY METHODS ====================

    suspend fun createCategory(category: ExpenseCategory): Boolean {
        return try {
            realm.write {
                copyToRealm(category)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteCategory(category: ExpenseCategory): Boolean {
        return try {
            realm.write {
                val objectToDelete = query<ExpenseCategory>("_id == $0", category._id).find().first()
                delete(objectToDelete)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAllCategories(): List<ExpenseCategory> {
        return realm.query<ExpenseCategory>().find().toList()
    }

    fun getCategoryById(id: String): ExpenseCategory? {
        return try {
            realm.query<ExpenseCategory>("_id == $0", ObjectId(id)).first().find()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Adds any default category the user doesn't have yet, matched by name, so
     * categories introduced in later versions reach existing installs too. Never
     * renames or removes the user's own categories.
     */
    suspend fun initializeDefaultCategories() {
        val existing = getAllCategories().mapTo(HashSet()) { it.name.lowercase() }
        val defaults = listOf(
            Triple("Food & Dining", "food", "#FF6B6B"),
            Triple("Groceries", "groceries", "#8BC34A"),
            Triple("Transportation", "transport", "#4ECDC4"),
            Triple("Bills & Utilities", "bills", "#FF9800"),
            Triple("Entertainment", "entertainment", "#95E1D3"),
            Triple("Housing", "housing", "#F38181"),
            Triple("Healthcare", "healthcare", "#AA96DA"),
            Triple("Education", "education", "#3F51B5"),
            Triple("Shopping", "shopping", "#FCBAD3"),
            Triple("Insurance & Savings", "savings", "#009688"),
            Triple("Cash", "cash", "#795548"),
            Triple("Income", "income", "#4CAF50"),
            Triple("Transfers", "transfers", "#9E9E9E"),
            Triple("Card Bill", "card", "#607D8B"),
            Triple("Other", "other", "#BDBDBD"),
        )
        var added = 0
        for ((name, icon, color) in defaults) {
            if (name.lowercase() in existing) continue
            createCategory(ExpenseCategory().apply {
                this.name = name
                this.icon = icon
                this.color = color
                isDefault = true
            })
            added++
        }
        Log.d("RealmService", "Default categories: added $added")
    }

    // ==================== EXPENSE TRANSACTION METHODS ====================

    suspend fun createExpense(expense: ExpenseTransaction): Boolean {
        return try {
            realm.write {
                copyToRealm(expense)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateExpense(expense: ExpenseTransaction): Boolean {
        return try {
            realm.write {
                val existingExpense = query<ExpenseTransaction>("_id == $0", expense._id).find().first()
                existingExpense.amount = expense.amount
                existingExpense.categoryId = expense.categoryId
                existingExpense.categoryName = expense.categoryName
                existingExpense.categoryColor = expense.categoryColor
                existingExpense.shortDescription = expense.shortDescription
                existingExpense.description = expense.description
                existingExpense.date = expense.date
                existingExpense.type = expense.type
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteExpense(expense: ExpenseTransaction): Boolean {
        return try {
            realm.write {
                val objectToDelete = query<ExpenseTransaction>("_id == $0", expense._id).find().first()
                delete(objectToDelete)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAllExpenses(): List<ExpenseTransaction> {
        return realm.query<ExpenseTransaction>()
            .find()
            .sortedByDescending { it.date }
    }

    /** Emits once now, then whenever any expense is added, changed or deleted. */
    fun expenseChanges(): Flow<Unit> =
        realm.query<ExpenseTransaction>().asFlow().map { }

    fun getExpensesByDateRange(startDate: Long, endDate: Long): List<ExpenseTransaction> {
        return realm.query<ExpenseTransaction>("date >= $0 AND date <= $1", startDate, endDate)
            .find()
            .sortedByDescending { it.date }
    }

    fun getExpensesByType(type: String): List<ExpenseTransaction> {
        return realm.query<ExpenseTransaction>("type == $0", type)
            .find()
            .sortedByDescending { it.date }
    }

    // ==================== BUDGET METHODS ====================
    suspend fun setBudget(budget: ExpenseBudget): Boolean {
        return try {
            realm.write {
                // Check if budget exists for category
                val existing = query<ExpenseBudget>("categoryId == $0", budget.categoryId).find().firstOrNull()
                if (existing != null) {
                    existing.amount = budget.amount
                    existing.period = budget.period
                } else {
                    copyToRealm(budget)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getBudgetForCategory(categoryId: String): ExpenseBudget? {
        return try {
            realm.query<ExpenseBudget>("categoryId == $0", categoryId).first().find()
        } catch (e: Exception) {
            null
        }
    }

    fun getAllBudgets(): List<ExpenseBudget> {
        return realm.query<ExpenseBudget>().find().toList()
    }

    // ==================== TODO METHODS ====================

    suspend fun createTodo(todo: TodoItem): Boolean {
        return try {
            realm.write {
                copyToRealm(todo)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateTodo(todo: TodoItem): Boolean {
        return try {
            realm.write {
                val existingTodo = query<TodoItem>("_id == $0", todo._id).find().first()
                existingTodo.title = todo.title
                existingTodo.description = todo.description
                existingTodo.isCompleted = todo.isCompleted
                existingTodo.priority = todo.priority
                existingTodo.category = todo.category
                existingTodo.dueDate = todo.dueDate
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun toggleTodoCompletion(todoId: ObjectId): Boolean {
        return try {
            realm.write {
                val existingTodo = query<TodoItem>("_id == $0", todoId).find().first()
                existingTodo.isCompleted = !existingTodo.isCompleted
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteTodo(todo: TodoItem): Boolean {
        return try {
            realm.write {
                val objectToDelete = query<TodoItem>("_id == $0", todo._id).find().first()
                delete(objectToDelete)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getAllTodos(): List<TodoItem> {
        return realm.query<TodoItem>()
            .find()
            .sortedByDescending { it.createdAt }
    }

    fun getTodoById(id: String): TodoItem? {
        return try {
            realm.query<TodoItem>("_id == $0", ObjectId(id)).first().find()
        } catch (e: Exception) {
            null
        }
    }
}
