package com.unsafe

import com.urosjarc.dbmessiah.domain.Isolation
import com.urosjarc.dbmessiah.domain.Table
import com.urosjarc.dbmessiah.impl.postgresql.PgSchema
import com.urosjarc.dbmessiah.impl.postgresql.PgSerializer
import com.urosjarc.dbmessiah.impl.postgresql.PgService
import com.urosjarc.dbmessiah.serializers.BasicTS
import java.util.*
import kotlin.concurrent.thread

data class ProductTable(val id: UUID, val name: String, var quantity: Int)

val store_schema = PgSchema(
    name = "store",
    tables = listOf(
        Table(primaryKey = ProductTable::id)
    ),
)

val db = PgService(
    config = Properties().apply {
        this["jdbcUrl"] = "jdbc:postgresql://localhost:5432/public"
        this["username"] = "root"
        this["password"] = "root"
    },
    ser = PgSerializer(
        schemas = listOf(store_schema),
        globalSerializers = BasicTS.sqlite
    )
)

fun main() {

    val bookProduct = ProductTable(id = UUID.randomUUID(), name = "Book", quantity = 10)

    db.autocommit {
        it.schema.create(schema = store_schema)
        it.table.create<ProductTable>(throws = false)
        it.row.insert(row = bookProduct)
    }

    val buyerThread = thread(start = true, isDaemon = true, name = "Buyer thread") {
        println("START BUYER TRANSACTION")
        db.transaction(Isolation.READ_COMMITTED) {
            it.row.update(bookProduct)
            for (i in 0..5) {
                Thread.sleep(1000)
                println("Buyer is still in transaction customer transaction is waiting...")
            }
        }
        println("END BUYER TRANSACTION")
    }

    val customizerThread = thread(start = true, isDaemon = true, name = "Customer thread") {
        Thread.sleep(200)
        println("START CUSTOMER TRANSACTION ${bookProduct.id}")
        db.transaction(Isolation.READ_COMMITTED) {
            it.row.update(row = bookProduct)
        }
        println("END CUSTOMER TRANSACTION")

    }


    println("THREAD JOIN TO MAIN THREAD")
    customizerThread.join()
    buyerThread.join()

}
