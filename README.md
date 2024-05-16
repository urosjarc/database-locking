# Database locking demonstration

Here is demonstrated how you can achieve
total database locking...

## Usecase example

Buyer updates whole `Product table` in the
transaction with lowest possible isolation level `read commited`.
**Buyer forgets to close transaction!**

You have then customer that tries to **buy product** 
from the same table... Customer can **not perform the action**
and the customer thread hangs since the row is locked because of buyer transaction.

## Code example (hanging customer thread)

```kotlin
 val bookProduct = ProductTable(id = UUID.randomUUID(), name = "Book", quantity = 10)

/**
 * SEED
 */
db.autocommit {
    it.schema.create(schema = store_schema)
    it.table.create<ProductTable>()
    it.row.insert(row = bookProduct)
}

/**
 * BUYER THREAD
 */
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

/**
 * CUSTOMER THREAD
 */
val customizerThread = thread(start = true, isDaemon = true, name = "Customer thread") {
    Thread.sleep(200)
    println("START CUSTOMER TRANSACTION ${bookProduct.id}")
    db.transaction(Isolation.READ_COMMITTED) { it.row.update(row = bookProduct) }
    println("END CUSTOMER TRANSACTION")

}


println("THREAD JOIN TO MAIN THREAD")
customizerThread.join()
buyerThread.join()
```
### LOGS

```text
                    Driver.kt:46  | INFO  | Prepare query: CREATE SCHEMA IF NOT EXISTS "store"
                     Driver.kt:46  | INFO  | Prepare query: CREATE TABLE IF NOT EXISTS "store"."ProductTable" ("id" CHARACTER(36) PRIMARY KEY, "name" VARCHAR(100) NOT NULL, "quantity" INTEGER NOT NULL)
                     Driver.kt:46  | INFO  | Prepare query: INSERT INTO "store"."ProductTable" ("id", "name", "quantity") VALUES ('e0b62e55-85fd-4002-98e0-b37dfb9bcf10', 'Book', 10)
                     
START BUYER TRANSACTION
THREAD JOIN TO MAIN THREAD

             ConnectionPool.kt:123 | INFO  | Transaction type: 2
                     Driver.kt:46  | INFO  | Prepare query: UPDATE "store"."ProductTable" SET "name" = 'Book', "quantity" = 10 WHERE "store"."ProductTable"."id" = 'e0b62e55-85fd-4002-98e0-b37dfb9bcf10'
                     
START CUSTOMER TRANSACTION

             ConnectionPool.kt:123 | INFO  | Transaction type: 2
                     Driver.kt:46  | INFO  | Prepare query: UPDATE "store"."ProductTable" SET "name" = 'Book', "quantity" = 10 WHERE "store"."ProductTable"."id" = 'e0b62e55-85fd-4002-98e0-b37dfb9bcf10'
                     
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...

END BUYER TRANSACTION

                 PoolBase.java:240 | DEBUG | HikariPool-1 - Reset (autoCommit) on connection org.postgresql.jdbc.PgConnection@9d420430
                 
END CUSTOMER TRANSACTION

                 PoolBase.java:240 | DEBUG | HikariPool-1 - Reset (autoCommit) on connection org.postgresql.jdbc.PgConnection@2d03d710
```

## SOLUTION

The solution for such cases is to set timeout for the transaction session like so...

```text
SET idle_in_transaction_session_timeout=1000;
```

If buyer transaction takes to long to execute... after 1 second database will release locks and
other transaction can resume with execution.

### Logs

We can see from the database logs that transaction is terminating after 1 second.
```text
postgresql_1  | 2024-05-16 05:43:15.300 UTC [655] FATAL:  terminating connection due to idle-in-transaction timeout
```

Our application is now without infinite locks...

```text

                     Driver.kt:46  | INFO  | Prepare query: CREATE SCHEMA IF NOT EXISTS "store"
                     Driver.kt:46  | INFO  | Prepare query: CREATE TABLE IF NOT EXISTS "store"."ProductTable" ("id" CHARACTER(36) PRIMARY KEY, "name" VARCHAR(100) NOT NULL, "quantity" INTEGER NOT NULL)
                     Driver.kt:46  | INFO  | Prepare query: INSERT INTO "store"."ProductTable" ("id", "name", "quantity") VALUES ('16c22957-b398-40e3-9050-601167460386', 'Book', 10)
                     Driver.kt:46  | INFO  | Prepare query:  SET idle_in_transaction_session_timeout=500; 
                     
START BUYER TRANSACTION
THREAD JOIN TO MAIN THREAD

             ConnectionPool.kt:123 | INFO  | Transaction type: 2
                     Driver.kt:46  | INFO  | Prepare query: UPDATE "store"."ProductTable" SET "name" = 'Book', "quantity" = 10 WHERE "store"."ProductTable"."id" = '16c22957-b398-40e3-9050-601167460386'
               
START CUSTOMER TRANSACTION 16c22957-b398-40e3-9050-601167460386

             ConnectionPool.kt:123 | INFO  | Transaction type: 2
                     Driver.kt:46  | INFO  | Prepare query: UPDATE "store"."ProductTable" SET "name" = 'Book', "quantity" = 10 WHERE "store"."ProductTable"."id" = '16c22957-b398-40e3-9050-601167460386'
                     
END CUSTOMER TRANSACTION

Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
Buyer is still in transaction customer transaction is waiting...
```
